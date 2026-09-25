package com.dietaryops.manager

import android.util.Log
import com.dietaryops.manager.data.SettingsManager
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"

    // Key Constants
    const val KEY_WELCOME_MESSAGE = "welcome_message"
    const val KEY_ENABLE_NEW_FEATURE = "enable_new_feature"
    const val KEY_ADMIN_PIN = "admin_pin"
    const val KEY_SHEET_WEBHOOK_URL = "sheet_webhook_url"
    const val KEY_MIN_REQUIRED_VERSION = "min_required_version"
    const val KEY_BANNER_MESSAGE = "banner_message"

    // Default Configuration Map
    val DEFAULT_VALUES: Map<String, Any> = mapOf(
        KEY_WELCOME_MESSAGE to "Welcome to Dietary Ops Manager!",
        KEY_ENABLE_NEW_FEATURE to true,
        KEY_ADMIN_PIN to "1234",
        KEY_SHEET_WEBHOOK_URL to SettingsManager.DEFAULT_WEB_APP_URL,
        KEY_MIN_REQUIRED_VERSION to "1.0.0",
        KEY_BANNER_MESSAGE to ""
    )

    private val lazyRemoteConfig = lazy {
        try {
            val instance = FirebaseRemoteConfig.getInstance()
            val fetchInterval = if (BuildConfig.DEBUG) 30L else 3600L
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(fetchInterval)
                .build()
            instance.setConfigSettingsAsync(configSettings)
            instance.setDefaultsAsync(DEFAULT_VALUES)
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseRemoteConfig", e)
            null
        }
    }

    val remoteConfig: FirebaseRemoteConfig?
        get() = lazyRemoteConfig.value

    // StateFlow Parameters
    private val _welcomeMessage = MutableStateFlow(DEFAULT_VALUES[KEY_WELCOME_MESSAGE] as String)
    val welcomeMessage: StateFlow<String> = _welcomeMessage.asStateFlow()

    private val _isFeatureEnabled = MutableStateFlow(DEFAULT_VALUES[KEY_ENABLE_NEW_FEATURE] as Boolean)
    val isFeatureEnabled: StateFlow<Boolean> = _isFeatureEnabled.asStateFlow()

    private val _adminPin = MutableStateFlow(DEFAULT_VALUES[KEY_ADMIN_PIN] as String)
    val adminPin: StateFlow<String> = _adminPin.asStateFlow()

    private val _sheetWebhookUrl = MutableStateFlow(DEFAULT_VALUES[KEY_SHEET_WEBHOOK_URL] as String)
    val sheetWebhookUrl: StateFlow<String> = _sheetWebhookUrl.asStateFlow()

    private val _minRequiredVersion = MutableStateFlow(DEFAULT_VALUES[KEY_MIN_REQUIRED_VERSION] as String)
    val minRequiredVersion: StateFlow<String> = _minRequiredVersion.asStateFlow()

    private val _bannerMessage = MutableStateFlow(DEFAULT_VALUES[KEY_BANNER_MESSAGE] as String)
    val bannerMessage: StateFlow<String> = _bannerMessage.asStateFlow()

    private val _isFetchSuccessful = MutableStateFlow(false)
    val isFetchSuccessful: StateFlow<Boolean> = _isFetchSuccessful.asStateFlow()

    fun initialize() {
        val config = remoteConfig
        if (config != null) {
            updateStateFlows()
        }
    }

    fun fetchAndActivate(onComplete: ((Boolean) -> Unit)? = null) {
        val config = remoteConfig
        if (config == null) {
            Log.w(TAG, "RemoteConfig instance is null, skipping fetch")
            _isFetchSuccessful.value = false
            onComplete?.invoke(false)
            return
        }

        config.fetchAndActivate()
            .addOnCompleteListener { task ->
                val successful = task.isSuccessful
                _isFetchSuccessful.value = successful
                if (successful) {
                    Log.d(TAG, "Remote config fetched and activated successfully")
                    updateStateFlows()
                } else {
                    Log.w(TAG, "Remote config fetch failed", task.exception)
                }
                onComplete?.invoke(successful)
            }
    }

    private fun updateStateFlows() {
        val config = remoteConfig ?: return
        _welcomeMessage.value = config.getString(KEY_WELCOME_MESSAGE).ifBlank { DEFAULT_VALUES[KEY_WELCOME_MESSAGE] as String }
        _isFeatureEnabled.value = config.getBoolean(KEY_ENABLE_NEW_FEATURE)
        _adminPin.value = config.getString(KEY_ADMIN_PIN).ifBlank { DEFAULT_VALUES[KEY_ADMIN_PIN] as String }
        _sheetWebhookUrl.value = config.getString(KEY_SHEET_WEBHOOK_URL).ifBlank { DEFAULT_VALUES[KEY_SHEET_WEBHOOK_URL] as String }
        _minRequiredVersion.value = config.getString(KEY_MIN_REQUIRED_VERSION).ifBlank { DEFAULT_VALUES[KEY_MIN_REQUIRED_VERSION] as String }
        _bannerMessage.value = config.getString(KEY_BANNER_MESSAGE)
    }
}
