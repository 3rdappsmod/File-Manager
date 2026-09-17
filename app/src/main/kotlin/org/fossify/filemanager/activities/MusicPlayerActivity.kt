package org.fossify.filemanager.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityMusicPlayerBinding
import org.fossify.filemanager.services.MusicPlayerService
import java.util.Locale
import java.util.concurrent.TimeUnit

class MusicPlayerActivity : SimpleActivity(), MusicPlayerService.PlaybackListener {
    companion object {
        const val EXTRA_PLAYLIST = "extra_playlist"
        const val EXTRA_START_INDEX = "extra_start_index"
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
            musicService?.listener = this@MusicPlayerActivity
            isBound = true
            startRequestedPlaylist()
            updateTrackInfo()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupViews()

        val serviceIntent = Intent(this, MusicPlayerService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.musicPlayerAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.musicPlayerHolder)
        progressHandler.post(progressUpdater)
    }

    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(progressUpdater)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            musicService?.listener = null
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

    private fun startRequestedPlaylist() {
        val playlist = intent.getStringArrayListExtra(EXTRA_PLAYLIST)
        if (playlist.isNullOrEmpty()) {
            return
        }

        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        musicService?.startPlaylist(playlist, startIndex)
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
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMillis) % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
