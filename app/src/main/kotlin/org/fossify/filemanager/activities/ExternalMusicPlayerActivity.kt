package org.fossify.filemanager.activities

import android.os.Bundle
import androidx.activity.addCallback

/** External playback returns to its caller instead of navigating into the file manager. */
class ExternalMusicPlayerActivity : MusicPlayerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
