package org.fossify.filemanager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.MusicPlayerActivity
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.helpers.MUSIC_PLAYER_REPEAT_MODE_ONCE
import org.fossify.filemanager.helpers.MUSIC_PLAYER_REPEAT_MODE_REPEAT_ONE
import org.fossify.filemanager.helpers.MUSIC_PLAYER_REPEAT_MODE_SEQUENTIAL
import java.io.IOException

class MusicPlayerService : Service() {
    companion object {
        const val ACTION_PLAY_PAUSE = "org.fossify.filemanager.action.PLAY_PAUSE"
        const val ACTION_NEXT = "org.fossify.filemanager.action.NEXT"
        const val ACTION_PREVIOUS = "org.fossify.filemanager.action.PREVIOUS"
        const val ACTION_STOP = "org.fossify.filemanager.action.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "music_player_channel"
        private const val NOTIFICATION_ID = 1000
    }

    interface PlaybackListener {
        fun onTrackChanged(path: String, isPlaying: Boolean)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onError()
    }

    inner class MusicPlayerBinder : Binder() {
        fun getService() = this@MusicPlayerService
    }

    private val binder = MusicPlayerBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playlist = ArrayList<String>()
    private var currentIndex = 0
    private var isPrepared = false
    var listener: PlaybackListener? = null
    var repeatMode = MUSIC_PLAYER_REPEAT_MODE_ONCE

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        repeatMode = config.musicPlayerRepeatMode
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseMediaPlayer()
        mediaSession?.release()
        super.onDestroy()
    }

    fun startPlaylist(paths: ArrayList<String>, startIndex: Int) {
        if (paths.isEmpty()) {
            return
        }

        playlist = paths
        currentIndex = startIndex.coerceIn(0, paths.size - 1)
        playCurrent()
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (!isPrepared) {
            return
        }

        if (player.isPlaying) {
            player.pause()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } else {
            player.start()
            startForegroundWithNotification()
        }

        onPlaybackStateUpdated()
    }

    fun playNext() {
        if (currentIndex < playlist.size - 1) {
            currentIndex++
            playCurrent()
        } else {
            pauseAtStart()
        }
    }

    fun playPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            playCurrent()
        } else {
            mediaPlayer?.seekTo(0)
        }
    }

    fun seekTo(positionMs: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(positionMs)
        }
    }

    fun getCurrentPath() = playlist.getOrNull(currentIndex) ?: ""

    fun isPlaying() = mediaPlayer?.isPlaying == true

    fun isPlaylistEmpty() = playlist.isEmpty()

    fun getDuration() = if (isPrepared) mediaPlayer?.duration ?: 0 else 0

    fun getCurrentPosition() = if (isPrepared) mediaPlayer?.currentPosition ?: 0 else 0

    private fun playCurrent() {
        val path = playlist.getOrNull(currentIndex) ?: return
        releaseMediaPlayer()
        isPrepared = false
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(path)
                setOnPreparedListener {
                    isPrepared = true
                    it.start()
                    startForegroundWithNotification()
                    onTrackChangedUpdated(path, true)
                }
                setOnCompletionListener {
                    onTrackCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    isPrepared = false
                    listener?.onError()
                    true
                }
                prepareAsync()
            }
        } catch (e: IOException) {
            listener?.onError()
            return
        } catch (e: IllegalArgumentException) {
            listener?.onError()
            return
        }
    }

    private fun onTrackCompleted() {
        when (repeatMode) {
            MUSIC_PLAYER_REPEAT_MODE_REPEAT_ONE -> replayCurrent()
            MUSIC_PLAYER_REPEAT_MODE_SEQUENTIAL -> playNext()
            else -> pauseAtStart()
        }
    }

    private fun replayCurrent() {
        mediaPlayer?.apply {
            seekTo(0)
            start()
        }
        onPlaybackStateUpdated()
    }

    // used when there is nothing left to auto-advance to (once mode, or end of playlist):
    // rewind to the beginning and pause, instead of tearing the player down, so the same
    // track can be replayed from this screen without needing to reopen it
    private fun pauseAtStart() {
        mediaPlayer?.apply {
            if (isPlaying) {
                pause()
            }
            seekTo(0)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        onPlaybackStateUpdated()
    }

    private fun stopPlayback() {
        releaseMediaPlayer()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseMediaPlayer() {
        isPrepared = false
        mediaPlayer?.apply {
            try {
                if (isPlaying) {
                    stop()
                }
            } catch (ignored: IllegalStateException) {
            }
            release()
        }
        mediaPlayer = null
    }

    private fun onTrackChangedUpdated(path: String, isPlaying: Boolean) {
        listener?.onTrackChanged(path, isPlaying)
        updateMediaSessionMetadata(path)
        updateMediaSessionPlaybackState(isPlaying)
        updateNotification()
    }

    private fun onPlaybackStateUpdated() {
        val playing = isPlaying()
        listener?.onPlaybackStateChanged(playing)
        updateMediaSessionPlaybackState(playing)
        updateNotification()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "MusicPlayerService").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = togglePlayPause()
                override fun onPause() = togglePlayPause()
                override fun onSkipToNext() = playNext()
                override fun onSkipToPrevious() = playPrevious()
                override fun onStop() = stopPlayback()
            })
            isActive = true
        }
    }

    private fun updateMediaSessionMetadata(path: String) {
        mediaSession?.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, path.getFilenameFromPath())
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, getDuration().toLong())
                .build()
        )
    }

    private fun updateMediaSessionPlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP

        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, getCurrentPosition().toLong(), 1f)
                .build()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.music_player),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun updateNotification() {
        if (!isPlaying()) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MusicPlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getCurrentPath().getFilenameFromPath())
            .setContentText(getString(R.string.music_player))
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying())
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.previous_track), getActionPendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseIcon, getString(R.string.play_pause), getActionPendingIntent(ACTION_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, getString(R.string.next_track), getActionPendingIntent(ACTION_NEXT))
            .setStyle(buildMediaStyle())
            .build()
    }

    private fun buildMediaStyle(): MediaStyle {
        val style = MediaStyle().setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { style.setMediaSession(MediaSessionCompat.Token.fromToken(it)) }
        return style
    }

    private fun getActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
