package org.fossify.filemanager.activities

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.util.UUID
import org.fossify.filemanager.R
import org.fossify.filemanager.helpers.isExternalAudioUri

/** The public entry point accepts only the supplied URI, never internal path/playlist extras. */
class OpenAudioActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data?.normalizeScheme()
        if (intent.action != Intent.ACTION_VIEW || uri == null || !isExternalAudioUri(uri.toString())) {
            Toast.makeText(this, R.string.playback_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            startActivity(Intent(this, MusicPlayerActivity::class.java).apply {
                if (uri.scheme == "content") {
                    setDataAndType(uri, intent.type)
                    clipData = ClipData.newRawUri("audio", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // File URIs remain private extras to avoid re-exposing them through Android intents.
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MusicPlayerActivity.EXTRA_PATH, uri.toString())
                putExtra(MusicPlayerActivity.EXTRA_REQUEST_ID, UUID.randomUUID().toString())
            })
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.playback_error, Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
