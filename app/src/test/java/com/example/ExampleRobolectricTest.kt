package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.FirebaseRuntimeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Occasion Diary", appName)
    }

    @Test
    fun `MainActivity launches safely even when Firebase is uninitialized`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
    }

    @Test
    fun `parseGoogleServicesJson extracts FirebaseOptions and web client id`() {
        val sampleJson = """
            {
              "project_info": {
                "project_number": "1234567890",
                "project_id": "demo-occasion-project",
                "storage_bucket": "demo-occasion-project.appspot.com"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:1234567890:android:abcdef123456",
                    "android_client_info": {
                      "package_name": "com.aistudio.neondraledger.kxvrpq"
                    }
                  },
                  "oauth_client": [
                    {
                      "client_id": "1234567890-webclient.example.invalid",
                      "client_type": 3
                    }
                  ],
                  "api_key": [
                    {
                      "current_key": "dummy_test_api_key_value"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = FirebaseRuntimeConfig.parseGoogleServicesJson(
            rawJson = sampleJson,
            targetPackageName = "com.aistudio.neondraledger.kxvrpq"
        )
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("demo-occasion-project", parsed.projectId)
        assertEquals("1:1234567890:android:abcdef123456", parsed.applicationId)
        assertEquals("dummy_test_api_key_value", parsed.apiKey)
        assertEquals("1234567890-webclient.example.invalid", parsed.webClientId)
    }
}
