package com.dietaryops.manager.util

import android.content.Context
import android.content.SharedPreferences
import com.dietaryops.manager.data.SettingsManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object RemoteConfig {

    private const val PREFS_NAME = "dietary_remote_config"
    private const val KEY_SCRIPT_URL = "script_url"
    private const val KEY_RECIPE_URL = "recipe_url"
    private const val KEY_TEMP_LOG_URL = "temp_log_url"
    private const val KEY_ACTIVE_WEEK = "active_week"
    private const val KEY_STAFF_ROSTER_JSON = "staff_roster_json"

    private const val DEFAULT_RECIPE_URL =
        "https://docs.google.com/spreadsheets/d/16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY/edit#gid=0"
    private const val DEFAULT_TEMP_LOG_URL =
        "https://docs.google.com/spreadsheets/d/16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY/edit#gid=0"
    private const val DEFAULT_ACTIVE_WEEK = "Week 5"
    private const val DEFAULT_STAFF_ROSTER =
        """[{"name":"Terry Little Jr.","id":"TL01","role":"RECEIVER"},{"name":"Andy Tygart","id":"AT01","role":"MANAGER"}]"""

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getScriptUrl(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_SCRIPT_URL, SettingsManager.DEFAULT_WEB_APP_URL)
            ?.ifBlank { SettingsManager.DEFAULT_WEB_APP_URL }
            ?: SettingsManager.DEFAULT_WEB_APP_URL
    }

    fun getRecipeUrl(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_RECIPE_URL, DEFAULT_RECIPE_URL)
            ?.ifBlank { DEFAULT_RECIPE_URL }
            ?: DEFAULT_RECIPE_URL
    }

    fun getTempLogUrl(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_TEMP_LOG_URL, DEFAULT_TEMP_LOG_URL)
            ?.ifBlank { DEFAULT_TEMP_LOG_URL }
            ?: DEFAULT_TEMP_LOG_URL
    }

    fun getActiveWeek(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_ACTIVE_WEEK, DEFAULT_ACTIVE_WEEK)
            ?.ifBlank { DEFAULT_ACTIVE_WEEK }
            ?: DEFAULT_ACTIVE_WEEK
    }

    fun getStaffRosterJson(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_STAFF_ROSTER_JSON, DEFAULT_STAFF_ROSTER)
            ?.ifBlank { DEFAULT_STAFF_ROSTER }
            ?: DEFAULT_STAFF_ROSTER
    }

    suspend fun syncConfig(context: Context, targetUrl: String = getScriptUrl(context)) =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = targetUrl.ifBlank { SettingsManager.DEFAULT_WEB_APP_URL }
                val configUrl =
                    if (cleanUrl.contains("?")) "$cleanUrl&action=get_remote_config" else "$cleanUrl?action=get_remote_config"

                val request = Request.Builder().url(configUrl).build()
                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@withContext
                    val jsonObj = JsonParser.parseString(bodyStr).asJsonObject

                    val editor = getPrefs(context).edit()
                    if (jsonObj.has("script_url")) editor.putString(
                        KEY_SCRIPT_URL,
                        jsonObj.get("script_url").asString
                    )
                    if (jsonObj.has("recipe_url")) editor.putString(
                        KEY_RECIPE_URL,
                        jsonObj.get("recipe_url").asString
                    )
                    if (jsonObj.has("temp_log_url")) editor.putString(
                        KEY_TEMP_LOG_URL,
                        jsonObj.get("temp_log_url").asString
                    )
                    if (jsonObj.has("active_week")) editor.putString(
                        KEY_ACTIVE_WEEK,
                        jsonObj.get("active_week").asString
                    )
                    if (jsonObj.has("staff_roster_json")) editor.putString(
                        KEY_STAFF_ROSTER_JSON,
                        jsonObj.get("staff_roster_json").asString
                    )
                    editor.apply()
                }
            } catch (_: Exception) {
                // Offline dock fallback: retains cached/default values seamlessly
            }
        }
}
