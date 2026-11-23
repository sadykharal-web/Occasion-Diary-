package com.example.drive

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.ActivityCategory
import com.example.data.ActivityRecord
import com.example.data.FamilyProfile
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveBackupPayload(
    val categories: List<ActivityCategory>,
    val families: List<FamilyProfile>,
    val activities: List<ActivityRecord>,
    val exportedAtMillis: Long
)

sealed interface DriveAuthState {
    data class Authorized(val accessToken: String) : DriveAuthState
    data class NeedsUserConsent(val pendingIntent: PendingIntent) : DriveAuthState
    data class Error(val message: String) : DriveAuthState
}

data class DriveSyncStatus(
    val isSyncing: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val lastSyncMillis: Long = 0L,
    val lastSyncedFileName: String? = null,
    val statusMessage: String? = null
)

object GoogleDriveSyncManager {
    private const val TAG = "GoogleDriveSync"
    private const val BACKUP_FILE_NAME = "Neondra_Roznamcha_Backup.json"
    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PREFS_NAME = "drive_sync_prefs"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun isAutoSyncEnabled(context: Context, userId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_sync_$userId", true)
    }

    fun setAutoSyncEnabled(context: Context, userId: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_sync_$userId", enabled)
            .apply()
    }

    fun getLastSyncMillis(context: Context, userId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("last_sync_$userId", 0L)
    }

    private fun saveLastSyncMillis(context: Context, userId: String, millis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_sync_$userId", millis)
            .apply()
    }

    suspend fun requestDriveAuthorization(context: Context): DriveAuthState {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()
            if (result.hasResolution()) {
                val pending = result.pendingIntent
                if (pending != null) {
                    DriveAuthState.NeedsUserConsent(pending)
                } else {
                    DriveAuthState.Error("Google Drive اجازت کا ڈائیلاگ دستیاب نہیں ہو سکا۔")
                }
            } else {
                val token = result.accessToken
                if (!token.isNullOrBlank()) {
                    DriveAuthState.Authorized(token)
                } else {
                    DriveAuthState.Error("Google Drive رسائی ٹوکن موصول نہیں ہوا۔")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Drive authorization failed", e)
            DriveAuthState.Error(e.localizedMessage ?: "Google Drive اجازت ناکام ہو گئی۔")
        }
    }

    suspend fun tryGetSilentToken(context: Context): String? {
        return when (val state = requestDriveAuthorization(context)) {
            is DriveAuthState.Authorized -> state.accessToken
            else -> null
        }
    }

    fun extractTokenFromConsentResult(activity: Activity, data: Intent?): String? {
        return try {
            val result = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
            result.accessToken
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting token from consent result", e)
            null
        }
    }

    suspend fun uploadBackupToDrive(
        context: Context,
        userId: String,
        userEmail: String?,
        accessToken: String,
        categories: List<ActivityCategory>,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val nowMillis = System.currentTimeMillis()
            val backupJson = serializeBackupJson(
                userEmail = userEmail,
                exportedAtMillis = nowMillis,
                categories = categories,
                families = families,
                activities = activities
            )

            val existingFileId = findBackupFileId(accessToken)
            val metadataJson = JSONObject().apply {
                put("name", BACKUP_FILE_NAME)
                put("mimeType", "application/json")
                put("description", "Neondra Roznamcha auto backup for ${userEmail ?: userId}")
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    okhttp3.Headers.headersOf("Content-Type", "application/json; charset=UTF-8"),
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addPart(
                    okhttp3.Headers.headersOf("Content-Type", "application/json; charset=UTF-8"),
                    backupJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .build()

            val url = if (existingFileId != null) {
                "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart"
            } else {
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")

            if (existingFileId != null) {
                reqBuilder.patch(multipartBody)
            } else {
                reqBuilder.post(multipartBody)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Drive upload failed: ${response.code} $bodyString")
                    return@withContext Result.failure(
                        IllegalStateException("Google Drive اپلوڈ ناکام (${response.code})")
                    )
                }
            }

            saveLastSyncMillis(context, userId, nowMillis)
            Result.success(nowMillis)
        } catch (e: Exception) {
            Log.e(TAG, "Drive backup error", e)
            Result.failure(e)
        }
    }

    suspend fun downloadBackupFromDrive(accessToken: String): Result<DriveBackupPayload> =
        withContext(Dispatchers.IO) {
            try {
                val fileId = findBackupFileId(accessToken)
                    ?: return@withContext Result.failure(
                        IllegalStateException("آپ کے Google Drive میں ابھی کوئی بیک اپ فائل موجود نہیں۔")
                    )

                val request = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Google Drive ڈاؤنلوڈ ناکام (${response.code})")
                        )
                    }
                    Result.success(parseBackupJson(raw))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Drive restore error", e)
                Result.failure(e)
            }
        }

    private fun findBackupFileId(accessToken: String): String? {
        val query = "name = '$BACKUP_FILE_NAME' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&spaces=drive&fields=files(id,name,modifiedTime)&pageSize=5"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive فہرست چیک ناکام (${response.code})")
            }
            val json = JSONObject(raw)
            val files = json.optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
        }
    }

    private fun serializeBackupJson(
        userEmail: String?,
        exportedAtMillis: Long,
        categories: List<ActivityCategory>,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>
    ): String {
        val root = JSONObject()
        root.put("app", "NeondraRoznamcha")
        root.put("version", 2)
        root.put("userEmail", userEmail ?: "")
        root.put("exportedAtMillis", exportedAtMillis)

        val catsArray = JSONArray()
        categories.forEach { cat ->
            catsArray.put(
                JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("colorHex", cat.colorHex)
                    put("iconKey", cat.iconKey)
                }
            )
        }
        root.put("categories", catsArray)

        val famsArray = JSONArray()
        families.forEach { fam ->
            famsArray.put(
                JSONObject().apply {
                    put("id", fam.id)
                    put("familyId", fam.familyId)
                    put("name", fam.name)
                    put("fatherName", fam.fatherName)
                    put("address", fam.address)
                    if (!fam.phone.isNullOrBlank()) put("phone", fam.phone)
                }
            )
        }
        root.put("families", famsArray)

        val actsArray = JSONArray()
        activities.forEach { act ->
            actsArray.put(
                JSONObject().apply {
                    put("id", act.id)
                    if (!act.familyDocId.isNullOrBlank()) put("familyDocId", act.familyDocId)
                    put("familyId", act.familyId)
                    put("name", act.name)
                    put("fatherName", act.fatherName)
                    put("address", act.address)
                    put("eventType", act.eventType)
                    put("eventDetail", act.eventDetail)
                    put("eventDateMillis", act.eventDateMillis)
                    put("direction", act.direction)
                    put("homeItems", act.homeItems)
                    put("neondraAmount", act.neondraAmount)
                    put("reminderEnabled", act.reminderEnabled)
                    put("reminderAtMillis", act.reminderAtMillis)
                    put("reminderMinutesBefore", act.reminderMinutesBefore)
                    put("calendarSynced", act.calendarSynced)
                }
            )
        }
        root.put("activities", actsArray)

        return root.toString(2)
    }

    private fun parseBackupJson(raw: String): DriveBackupPayload {
        val root = JSONObject(raw)
        val exportedAt = root.optLong("exportedAtMillis", System.currentTimeMillis())

        val categories = buildList {
            val arr = root.optJSONArray("categories") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    ActivityCategory(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        colorHex = obj.optString("colorHex", "#0F5C4D"),
                        iconKey = obj.optString("iconKey", "event")
                    )
                )
            }
        }

        val families = buildList {
            val arr = root.optJSONArray("families") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    FamilyProfile(
                        id = obj.optString("id"),
                        familyId = obj.optString("familyId"),
                        name = obj.optString("name"),
                        fatherName = obj.optString("fatherName"),
                        address = obj.optString("address"),
                        phone = obj.optString("phone").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        val activities = buildList {
            val arr = root.optJSONArray("activities") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    ActivityRecord(
                        id = obj.optString("id"),
                        familyDocId = obj.optString("familyDocId").takeIf { it.isNotBlank() },
                        familyId = obj.optString("familyId"),
                        name = obj.optString("name"),
                        fatherName = obj.optString("fatherName"),
                        address = obj.optString("address"),
                        eventType = obj.optString("eventType"),
                        eventDetail = obj.optString("eventDetail"),
                        eventDateMillis = obj.optLong("eventDateMillis", System.currentTimeMillis()),
                        direction = obj.optString("direction", "OUT"),
                        homeItems = obj.optString("homeItems"),
                        neondraAmount = obj.optLong("neondraAmount", 0L),
                        reminderEnabled = obj.optBoolean("reminderEnabled", false),
                        reminderAtMillis = obj.optLong("reminderAtMillis", 0L),
                        reminderMinutesBefore = obj.optLong("reminderMinutesBefore", 0L),
                        calendarSynced = obj.optBoolean("calendarSynced", false)
                    )
                )
            }
        }

        return DriveBackupPayload(
            categories = categories,
            families = families,
            activities = activities,
            exportedAtMillis = exportedAt
        )
    }
}
