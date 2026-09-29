package org.fossify.filemanager.activities

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import androidx.lifecycle.Lifecycle
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityMusicPlayerBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.helpers.isExternalAudioUri
import org.fossify.filemanager.helpers.MUSIC_PLAYER_REPEAT_MODE_ONCE
import org.fossify.filemanager.helpers.MUSIC_PLAYER_REPEAT_MODE_REPEAT_ONE
import org.fossify.filemanager.helpers.MUSIC_PLAYER_REPEAT_MODE_SEQUENTIAL
import org.fossify.filemanager.services.MusicPlayerService

// Lifecycle, service and view callbacks are kept together to make subscription ownership explicit.
@Suppress("TooManyFunctions")
class MusicPlayerActivity : SimpleActivity(), MusicPlayerService.PlaybackListener {
    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_REQUEST_ID = "extra_request_id"
        private const val PROGRESS_UPDATE_DELAY = 500L
    }

    private val binding by viewBinding(ActivityMusicPlayerBinding::inflate)
    private var musicService: MusicPlayerService? = null
    private var isBound = false
    private var isDraggingSeekBar = false
    private val progressHandler = Handler(Looper.getMainLooper())

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, PROGRESS_UPDATE_DELAY)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicPlayerService.MusicPlayerBinder).getService()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                musicService?.addListener(this@MusicPlayerActivity)
            }
            startRequestedPlaylist()
            updateTrackInfo()
            updateRepeatModeIcon(musicService?.repeatMode ?: MUSIC_PLAYER_REPEAT_MODE_ONCE)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupViews()
        setupEdgeToEdge(padBottomSystem = listOf(binding.musicPlayerHolder))

        val serviceIntent = Intent(this, MusicPlayerService::class.java)
        forwardAudioGrant(serviceIntent)
        startService(serviceIntent)
        isBound = bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val serviceIntent = Intent(this, MusicPlayerService::class.java)
        forwardAudioGrant(serviceIntent)
        startService(serviceIntent)
        startRequestedPlaylist()
    }

    override fun onStart() {
        super.onStart()
        musicService?.addListener(this)
        updateTrackInfo()
        updateRepeatModeIcon(musicService?.repeatMode ?: config.musicPlayerRepeatMode)
    }

    override fun onStop() {
        musicService?.removeListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.musicPlayerAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.musicPlayerHolder)
        val tint = ColorStateList.valueOf(getProperTextColor())
        binding.apply {
            listOf(musicPlayerPlayPause, musicPlayerPrevious, musicPlayerNext, musicPlayerRepeatMode,
                musicPlayerAlbumArt).forEach { it.imageTintList = tint }
        }
        progressHandler.post(progressUpdater)
    }

    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(progressUpdater)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            musicService?.removeListener(this)
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupViews() {
        binding.apply {
            musicPlayerPlayPause.setOnClickListener {
                musicService?.togglePlayPause()
            }

            musicPlayerNext.setOnClickListener {
                musicService?.playNext()
                updateTrackInfo()
            }

            musicPlayerPrevious.setOnClickListener {
                musicService?.playPrevious()
                updateTrackInfo()
            }

            musicPlayerRepeatMode.setOnClickListener {
                cycleRepeatMode()
            }

            musicPlayerSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        musicPlayerCurrentTime.text = formatDuration(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isDraggingSeekBar = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isDraggingSeekBar = false
                    musicService?.seekTo(seekBar?.progress ?: 0)
                }
            })
        }
    }

    private fun forwardAudioGrant(serviceIntent: Intent) {
        val uri = intent.data ?: return
        if (!isExternalAudioUri(uri.toString())) return
        // A service grant survives closing the player activity for background playback/repeat.
        serviceIntent.data = uri
        serviceIntent.clipData = ClipData.newRawUri("audio", uri)
        serviceIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun startRequestedPlaylist() {
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        musicService?.openPath(path, requestId)
    }

    private fun updateProgress() {
        val service = musicService ?: return
        if (isDraggingSeekBar) {
            return
        }

        binding.apply {
            val duration = service.getDuration()
            musicPlayerSeekbar.max = duration
            musicPlayerSeekbar.progress = service.getCurrentPosition()
            musicPlayerTotalTime.text = formatDuration(duration)
            musicPlayerCurrentTime.text = formatDuration(service.getCurrentPosition())
        }
    }

    private fun updateTrackInfo() {
        val path = musicService?.getCurrentPath()
        if (path.isNullOrEmpty()) {
            return
        }

        binding.musicPlayerTitle.text = path.getFilenameFromPath()
        updatePlayPauseIcon(musicService?.isPlaying() == true)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val iconId = if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
        binding.musicPlayerPlayPause.setImageResource(iconId)
    }

    private fun cycleRepeatMode() {
        val currentMode = musicService?.repeatMode ?: MUSIC_PLAYER_REPEAT_MODE_ONCE
        val nextMode = when (currentMode) {
            MUSIC_PLAYER_REPEAT_MODE_ONCE -> MUSIC_PLAYER_REPEAT_MODE_REPEAT_ONE
            MUSIC_PLAYER_REPEAT_MODE_REPEAT_ONE -> MUSIC_PLAYER_REPEAT_MODE_SEQUENTIAL
            else -> MUSIC_PLAYER_REPEAT_MODE_ONCE
        }

        musicService?.repeatMode = nextMode
        config.musicPlayerRepeatMode = nextMode
        updateRepeatModeIcon(nextMode)
        toast(repeatModeDescription(nextMode))
    }

    private fun updateRepeatModeIcon(mode: Int) {
        val iconId = when (mode) {
            MUSIC_PLAYER_REPEAT_MODE_REPEAT_ONE -> R.drawable.ic_repeat_mode_repeat_one_vector
            MUSIC_PLAYER_REPEAT_MODE_SEQUENTIAL -> R.drawable.ic_repeat_mode_sequential_vector
            else -> R.drawable.ic_repeat_mode_once_vector
        }

        binding.musicPlayerRepeatMode.apply {
            setImageResource(iconId)
            contentDescription = getString(repeatModeDescription(mode))
        }
    }

    private fun repeatModeDescription(mode: Int) = when (mode) {
        MUSIC_PLAYER_REPEAT_MODE_REPEAT_ONE -> R.string.repeat_mode_repeat_one
        MUSIC_PLAYER_REPEAT_MODE_SEQUENTIAL -> R.string.repeat_mode_sequential
        else -> R.string.repeat_mode_once
    }

    override fun onTrackChanged(path: String, isPlaying: Boolean) {
        runOnUiThread {
            binding.musicPlayerTitle.text = path.getFilenameFromPath()
            updatePlayPauseIcon(isPlaying)
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            updatePlayPauseIcon(isPlaying)
        }
    }

    override fun onError() {
        runOnUiThread {
            toast(R.string.playback_error)
        }
    }

    private fun formatDuration(millis: Int): String {
        val safeMillis = millis.coerceAtLeast(0).toLong()
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMillis) % TimeUnit.MINUTES.toSeconds(1)
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
