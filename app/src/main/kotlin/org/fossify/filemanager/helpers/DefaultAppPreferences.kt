package org.fossify.filemanager.helpers

import android.content.SharedPreferences
import androidx.core.content.edit

class DefaultAppPreferences(private val prefs: SharedPreferences, private val legacyBuiltInAudio: Boolean) {
    fun get(feature: DefaultAppFeature): String = DefaultAppFeature.savedChoice(
        feature, prefs.getString(feature.preferenceKey, null), legacyBuiltInAudio
    )

    fun set(feature: DefaultAppFeature, target: String) {
        // An explicit empty value also overrides the legacy music player preference.
        prefs.edit { putString(feature.preferenceKey, target) }
    }

    fun reset() {
        prefs.edit { DefaultAppFeature.entries.forEach { putString(it.preferenceKey, "") } }
    }
}
