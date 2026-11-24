package com.example.drive

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.ActivityCategory
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LocalBackupPayload(
    val categories: List<ActivityCategory>,
    val families: List<FamilyProfile>,
    val activities: List<ActivityRecord>,
    val events: List<EventEntry> = emptyList(),
    val exportedAtMillis: Long
)

data class LocalBackupStatus(
    val isBusy: Boolean = false,
    val lastBackupMillis: Long = 0L,
    val lastFileName: String? = null,
    val statusMessage: String? = null
)

object LocalBackupManager {
    private const val TAG = "LocalBackupManager"
    const val DEFAULT_BACKUP_FILE_NAME = "Life_Line_Backup.json"
    private const val PREFS_NAME = "occasion_diary_local_backup"
    private const val KEY_LAST_BACKUP = "last_backup_millis"

    fun getLastBackupMillis(context: Context): Long {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_BACKUP, 0L)
    }

    private fun saveLastBackupMillis(context: Context, millis: Long) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKUP, millis)
            .apply()
    }

    suspend fun exportBackupToUri(
        context: Context,
        uri: Uri,
        categories: List<ActivityCategory>,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val nowMillis = System.currentTimeMillis()
            val json = serializeBackupJson(
                exportedAtMillis = nowMillis,
                categories = categories,
                families = families,
                activities = activities,
                events = events
            )
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open backup destination.")
            saveLastBackupMillis(context, nowMillis)
            Result.success(nowMillis)
        } catch (e: Exception) {
            Log.e(TAG, "Offline backup export failed", e)
            Result.failure(e)
        }
    }

    suspend fun importBackupFromUri(
        context: Context,
        uri: Uri
    ): Result<LocalBackupPayload> = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Could not read selected backup file.")
            require(raw.isNotBlank()) { "Selected backup file is empty." }
            Result.success(parseBackupJson(raw))
        } catch (e: Exception) {
            Log.e(TAG, "Offline backup import failed", e)
            Result.failure(e)
        }
    }

    fun serializeBackupJson(
        exportedAtMillis: Long,
        categories: List<ActivityCategory>,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): String {
        val root = JSONObject()
        root.put("app", "LifeLine")
        root.put("version", 3)
        root.put("mode", "offline")
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

        val eventsArray = JSONArray()
        events.forEach { evt ->
            eventsArray.put(
                JSONObject().apply {
                    put("id", evt.id)
                    put("title", evt.title)
                    put("description", evt.description)
                    put("eventDateMillis", evt.eventDateMillis)
                    put("category", evt.category)
                    put("location", evt.location)
                    if (!evt.familyDocId.isNullOrBlank()) put("familyDocId", evt.familyDocId)
                    put("familyName", evt.familyName)
                }
            )
        }
        root.put("events", eventsArray)

        return root.toString(2)
    }

    fun parseBackupJson(raw: String): LocalBackupPayload {
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

        val events = buildList {
            val arr = root.optJSONArray("events") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    EventEntry(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        eventDateMillis = obj.optLong("eventDateMillis", System.currentTimeMillis()),
                        category = obj.optString("category", "Wedding"),
                        location = obj.optString("location"),
                        familyDocId = obj.optString("familyDocId").takeIf { it.isNotBlank() },
                        familyName = obj.optString("familyName")
                    )
                )
            }
        }

        return LocalBackupPayload(
            categories = categories,
            families = families,
            activities = activities,
            events = events,
            exportedAtMillis = exportedAt
        )
    }
}
