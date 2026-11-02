package org.fossify.filemanager

import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.FossifyApp
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.helpers.LocalDateFormats

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        val preferences = config
        val dateFormat = LocalDateFormats.normalize(preferences.dateFormat)
        if (preferences.dateFormat != dateFormat) preferences.dateFormat = dateFormat
        Reprint.initialize(this)
    }
}
