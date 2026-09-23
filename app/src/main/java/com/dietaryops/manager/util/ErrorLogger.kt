package com.dietaryops.manager.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.dietaryops.manager.data.SettingsManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object ErrorLogger {
    private const val TAG = "DietaryOpsErrorLogger"

    fun logError(
        context: Context,
        settingsManager: SettingsManager,
        errorTag: String,
        exception: Throwable?,
        customMessage: String = ""
    ) {
        val errorMessage = exception?.localizedMessage ?: customMessage.ifBlank { "Unknown error" }
        val stackTrace = exception?.stackTraceToString() ?: ""
        val staff = "${settingsManager.staffName} (${settingsManager.employeeId})"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        Log.e(TAG, "[$errorTag] $errorMessage\n$stackTrace")

        // Firebase Analytics exception event logging
        try {
            val analytics = FirebaseAnalytics.getInstance(context)
            val bundle = Bundle().apply {
                putString("error_tag", errorTag)
                putString("staff", staff)
                putString("message", errorMessage.take(100))
                putString("device", deviceModel)
            }
            analytics.logEvent("app_error_logged", bundle)
        } catch (_: Exception) {
            // Ignore Analytics fallback
        }

        // Save error locally in SharedPreferences for in-app viewer
        val prefs = context.getSharedPreferences("error_logs", Context.MODE_PRIVATE)
        val timestamp = System.currentTimeMillis()
        val formattedLog = "[$timestamp] [$errorTag] $staff | $deviceModel | $errorMessage"
        val existing = prefs.getString("last_errors", "") ?: ""
        val updated = "$formattedLog\n$existing".take(3000)
        prefs.edit().putString("last_errors", updated).apply()

        // Asynchronously report error to Cloud Firestore & Google Sheets
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Report to Firestore /error_logs
                val firestore = FirebaseFirestore.getInstance()
                val logData = mapOf(
                    "timestamp" to timestamp,
                    "tag" to errorTag,
                    "staff" to staff,
                    "companyCode" to settingsManager.companyCode,
                    "department" to settingsManager.department,
                    "device" to deviceModel,
                    "message" to errorMessage,
                    "stackTrace" to stackTrace.take(1000)
                )
                firestore.collection("companies")
                    .document(settingsManager.companyCode)
                    .collection("error_logs")
                    .add(logData)
            } catch (_: Exception) {
                // Ignore secondary logging failure
            }

            try {
                // 2. Report to Google Sheets Webhook
                val webhook = settingsManager.webAppUrl
                if (webhook.isNotBlank()) {
                    val url = URL(webhook)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000

                    val json = JSONObject().apply {
                        put("action", "log_error")
                        put("tag", errorTag)
                        put("staff", staff)
                        put("device", deviceModel)
                        put("message", errorMessage)
                        put("timestamp", timestamp)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(json.toString())
                    writer.flush()
                    writer.close()
                    conn.responseCode
                }
            } catch (_: Exception) {
                // Ignore secondary logging failure
            }
        }
    }

    fun getRecentErrorLogs(context: Context): String {
        val prefs = context.getSharedPreferences("error_logs", Context.MODE_PRIVATE)
        return prefs.getString("last_errors", "No error logs recorded.") ?: "No error logs recorded."
    }

    fun clearErrorLogs(context: Context) {
        val prefs = context.getSharedPreferences("error_logs", Context.MODE_PRIVATE)
        prefs.edit().remove("last_errors").apply()
    }
}
