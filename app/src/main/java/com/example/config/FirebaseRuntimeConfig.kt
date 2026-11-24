package com.example.config

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.json.JSONObject

data class ParsedGoogleServicesConfig(
    val projectId: String,
    val projectNumber: String?,
    val storageBucket: String?,
    val applicationId: String,
    val apiKey: String,
    val webClientId: String?
)

object FirebaseRuntimeConfig {
    private const val TAG = "FirebaseRuntimeConfig"
    private const val PREFS_NAME = "occasion_diary_firebase_runtime"
    private const val KEY_RAW_JSON = "raw_google_services_json"
    private const val KEY_WEB_CLIENT_ID = "runtime_web_client_id"

    fun ensureFirebaseInitialized(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            return true
        }
        val defaultInitialized = runCatching {
            FirebaseApp.initializeApp(appContext) != null
        }.getOrDefault(false)
        if (defaultInitialized && FirebaseApp.getApps(appContext).isNotEmpty()) {
            return true
        }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(KEY_RAW_JSON, null)?.takeIf { it.isNotBlank() } ?: return false
        val parsed = parseGoogleServicesJson(savedJson, appContext.packageName).getOrNull() ?: return false
        return initializeWithParsedConfig(appContext, parsed).isSuccess
    }

    fun getRuntimeWebClientId(context: Context): String? {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_WEB_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
        if (cached != null) return cached

        val savedJson = prefs.getString(KEY_RAW_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        val parsed = parseGoogleServicesJson(savedJson, appContext.packageName).getOrNull() ?: return null
        if (!parsed.webClientId.isNullOrBlank()) {
            prefs.edit().putString(KEY_WEB_CLIENT_ID, parsed.webClientId).apply()
        }
        return parsed.webClientId
    }

    fun importGoogleServicesJson(context: Context, rawJson: String): Result<String> {
        val appContext = context.applicationContext ?: context
        val parsed = parseGoogleServicesJson(rawJson, appContext.packageName).getOrElse { err ->
            return Result.failure(err)
        }
        val initResult = initializeWithParsedConfig(appContext, parsed)
        if (initResult.isFailure) {
            return Result.failure(initResult.exceptionOrNull() ?: IllegalStateException("Firebase initialization failed"))
        }

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RAW_JSON, rawJson.trim())
            .putString(KEY_WEB_CLIENT_ID, parsed.webClientId ?: "")
            .commit()

        return Result.success(parsed.projectId)
    }

    fun readJsonTextFromUri(context: Context, uri: Uri): Result<String> {
        return runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Unable to open the selected file.")
            require(content.isNotBlank()) { "The selected file is empty." }
            content
        }
    }

    fun parseGoogleServicesJson(
        rawJson: String,
        targetPackageName: String
    ): Result<ParsedGoogleServicesConfig> {
        return runCatching {
            val trimmed = rawJson.trim()
            require(trimmed.isNotEmpty()) { "Please provide google-services.json content." }
            val root = JSONObject(trimmed)
            val projectInfo = root.optJSONObject("project_info")
                ?: error("Invalid google-services.json: missing project_info.")
            val projectId = projectInfo.optString("project_id").trim()
            require(projectId.isNotEmpty()) { "Invalid google-services.json: missing project_id." }

            val projectNumber = projectInfo.optString("project_number").trim().ifEmpty { null }
            val storageBucket = projectInfo.optString("storage_bucket").trim().ifEmpty { null }

            val clients = root.optJSONArray("client")
                ?: error("Invalid google-services.json: missing client array.")
            require(clients.length() > 0) { "Invalid google-services.json: client array is empty." }

            var matchedClient: JSONObject? = null
            for (i in 0 until clients.length()) {
                val candidate = clients.optJSONObject(i) ?: continue
                val pkg = candidate.optJSONObject("client_info")
                    ?.optJSONObject("android_client_info")
                    ?.optString("package_name")
                    ?.trim()
                if (pkg == targetPackageName) {
                    matchedClient = candidate
                    break
                }
            }
            val selectedClient = matchedClient ?: clients.optJSONObject(0)
                ?: error("Invalid google-services.json: unable to read Android client entry.")

            val appId = selectedClient.optJSONObject("client_info")
                ?.optString("mobilesdk_app_id")
                ?.trim()
                .orEmpty()
            require(appId.isNotEmpty()) { "Invalid google-services.json: missing mobilesdk_app_id." }

            val apiKeys = selectedClient.optJSONArray("api_key")
            val apiKey = if (apiKeys != null && apiKeys.length() > 0) {
                apiKeys.optJSONObject(0)?.optString("current_key")?.trim().orEmpty()
            } else {
                ""
            }
            require(apiKey.isNotEmpty()) { "Invalid google-services.json: missing api_key.current_key." }

            var webClientId: String? = null
            val oauthClients = selectedClient.optJSONArray("oauth_client")
            if (oauthClients != null) {
                for (i in 0 until oauthClients.length()) {
                    val oauth = oauthClients.optJSONObject(i) ?: continue
                    if (oauth.optInt("client_type") == 3) {
                        val id = oauth.optString("client_id").trim()
                        if (id.isNotEmpty()) {
                            webClientId = id
                            break
                        }
                    }
                }
            }
            if (webClientId == null) {
                val otherClients = selectedClient.optJSONObject("services")
                    ?.optJSONObject("appinvite_service")
                    ?.optJSONArray("other_platform_oauth_client")
                if (otherClients != null) {
                    for (i in 0 until otherClients.length()) {
                        val oauth = otherClients.optJSONObject(i) ?: continue
                        if (oauth.optInt("client_type") == 3) {
                            val id = oauth.optString("client_id").trim()
                            if (id.isNotEmpty()) {
                                webClientId = id
                                break
                            }
                        }
                    }
                }
            }

            ParsedGoogleServicesConfig(
                projectId = projectId,
                projectNumber = projectNumber,
                storageBucket = storageBucket,
                applicationId = appId,
                apiKey = apiKey,
                webClientId = webClientId
            )
        }
    }

    private fun initializeWithParsedConfig(
        appContext: Context,
        config: ParsedGoogleServicesConfig
    ): Result<FirebaseApp> {
        return runCatching {
            val existing = FirebaseApp.getApps(appContext).firstOrNull()
            if (existing != null) return@runCatching existing

            val optionsBuilder = FirebaseOptions.Builder()
                .setProjectId(config.projectId)
                .setApplicationId(config.applicationId)
                .setApiKey(config.apiKey)
            if (!config.storageBucket.isNullOrBlank()) {
                optionsBuilder.setStorageBucket(config.storageBucket)
            }
            if (!config.projectNumber.isNullOrBlank()) {
                optionsBuilder.setGcmSenderId(config.projectNumber)
            }
            FirebaseApp.initializeApp(appContext, optionsBuilder.build())
        }.onFailure { err ->
            Log.e(TAG, "Failed to initialize FirebaseApp from runtime config", err)
        }
    }
}
