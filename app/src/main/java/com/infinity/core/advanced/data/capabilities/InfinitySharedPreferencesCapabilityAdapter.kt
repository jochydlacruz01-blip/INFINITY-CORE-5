package com.infinity.core.advanced.data.capabilities

import android.content.Context
import android.content.SharedPreferences
import com.infinity.core.advanced.domain.capabilities.DefaultInfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityId
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.InfinityApplicationLauncherCapability
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMonitoringCapability
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationCapability
import com.infinity.core.advanced.domain.capabilities.InfinityThemeCapability
import com.infinity.core.advanced.domain.capabilities.InfinityUserPreferencesCapability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

private const val DESKTOP_PREFERENCES = "infinity_desktop"
private const val ADVANCED_PREFERENCES = "infinity_advanced_preferences"
private const val THEME_KEY = "theme_choice"
private const val MAX_PREFERENCE_VALUE_LENGTH = 4096

private val supportedThemeIds = setOf("midnight", "ocean", "violet", "emerald", "sunset")
private val forbiddenPreferenceKeyFragments = setOf(
    "password", "passwd", "secret", "token", "credential", "authorization", "private_key"
)

internal fun infinityNormalizeAdvancedThemeId(themeId: String): String =
    themeId.trim().lowercase().takeIf { it in supportedThemeIds } ?: "midnight"

internal fun infinityIsSafeAdvancedPreferenceKey(key: String): Boolean {
    val normalized = key.trim().lowercase()
    return normalized.isNotEmpty() &&
        normalized.length <= 80 &&
        normalized.none { it.isWhitespace() } &&
        forbiddenPreferenceKeyFragments.none(normalized::contains)
}

/**
 * Bridges ADVANCED capability contracts to the preferences already owned by
 * the desktop. Theme changes therefore update the existing theme source instead
 * of creating a parallel theme store.
 */
internal class InfinitySharedPreferencesCapabilityAdapter(
    private val desktopPreferences: SharedPreferences,
    private val advancedPreferences: SharedPreferences
) : InfinityThemeCapability, InfinityUserPreferencesCapability {

    override val selectedThemeId: Flow<String> =
        observePreference(desktopPreferences, THEME_KEY, "midnight")
            .map(::infinityNormalizeAdvancedThemeId)

    override suspend fun selectTheme(themeId: String) {
        val normalized = infinityNormalizeAdvancedThemeId(themeId)
        require(themeId.trim().lowercase() in supportedThemeIds) {
            "Unsupported INFINITY theme: $themeId"
        }
        check(desktopPreferences.edit().putString(THEME_KEY, normalized).commit()) {
            "Unable to persist the selected INFINITY theme"
        }
    }

    override fun observeString(key: String, defaultValue: String): Flow<String> {
        require(infinityIsSafeAdvancedPreferenceKey(key)) { "Unsafe preference key" }
        require(defaultValue.length <= MAX_PREFERENCE_VALUE_LENGTH) { "Default value is too long" }
        return observePreference(advancedPreferences, key.trim(), defaultValue)
    }

    override suspend fun putString(key: String, value: String) {
        require(infinityIsSafeAdvancedPreferenceKey(key)) { "Unsafe preference key" }
        require(value.length <= MAX_PREFERENCE_VALUE_LENGTH) { "Preference value is too long" }
        check(advancedPreferences.edit().putString(key.trim(), value).commit()) {
            "Unable to persist INFINITY preference"
        }
    }

    private fun observePreference(
        preferences: SharedPreferences,
        key: String,
        defaultValue: String
    ): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, changedKey ->
            if (changedKey == key) {
                trySend(source.getString(key, defaultValue) ?: defaultValue)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(preferences.getString(key, defaultValue) ?: defaultValue)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/** Application composition root for the concrete ADVANCED capability adapters. */
internal fun createInfinityAdvancedCapabilityRegistry(
    context: Context
): InfinityCapabilityRegistry {
    val appContext = context.applicationContext
    val preferencesAdapter = InfinitySharedPreferencesCapabilityAdapter(
        desktopPreferences = appContext.getSharedPreferences(DESKTOP_PREFERENCES, Context.MODE_PRIVATE),
        advancedPreferences = appContext.getSharedPreferences(ADVANCED_PREFERENCES, Context.MODE_PRIVATE)
    )
    val deviceMonitoringAdapter = InfinityAndroidDeviceMonitoringAdapter(appContext)
    val applicationLauncherAdapter = InfinityAndroidApplicationLauncherAdapter(appContext)
    return DefaultInfinityCapabilityRegistry(
        mapOf(
            InfinityCapabilityId.NOTIFICATIONS to InfinityNotificationCapabilityAdapter(),
            InfinityCapabilityId.DEVICE_MONITORING to deviceMonitoringAdapter,
            InfinityCapabilityId.APPLICATION_LAUNCHER to applicationLauncherAdapter,
            InfinityCapabilityId.THEME to preferencesAdapter,
            InfinityCapabilityId.USER_PREFERENCES to preferencesAdapter
        )
    )
}
