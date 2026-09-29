package org.fossify.filemanager.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** Wait for initial focus, but cancel playback on interruptions or headphone disconnection. */
class AudioFocus(private val context: Context, private val pause: () -> Unit, private val resume: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val state = AudioFocusState()
    private var registered = false
    private var request: AudioFocusRequest? = null
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    fun acquire(): AudioFocusState.Result {
        if (request != null) return state.result
        val token = state.begin()
        val nextRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes())
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener({ change ->
                if (state.isCurrent(token)) {
                    if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        if (state.gain(token)) resume()
                    } else {
                        pause()
                    }
                }
            }, handler)
            .build()
        request = nextRequest
        val result = when (manager.requestAudioFocus(nextRequest)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> AudioFocusState.Result.GRANTED
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> AudioFocusState.Result.DELAYED
            else -> AudioFocusState.Result.FAILED
        }
        state.complete(token, result)
        if (result == AudioFocusState.Result.FAILED) {
            release()
        } else if (!registered) {
            ContextCompat.registerReceiver(
                context, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registered = true
        }
        return result
    }

    fun release() {
        state.cancel()
        val previous = request
        request = null
        previous?.let { manager.abandonAudioFocusRequest(it) }
        if (registered) {
            context.unregisterReceiver(noisyReceiver)
            registered = false
        }
    }

    companion object {
        fun attributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
