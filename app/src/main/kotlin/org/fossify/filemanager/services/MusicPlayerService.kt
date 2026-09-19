package org.fossify.filemanager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.Looper
import android.os.IBinder
import android.util.Log
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
import org.fossify.filemanager.helpers.PlaybackState as PlayerState
import org.fossify.filemanager.helpers.isVisibleAudio
import org.fossify.filemanager.helpers.StorageEvents
import org.fossify.filemanager.helpers.isWithinStorage
import org.fossify.filemanager.helpers.AudioFocus
import org.fossify.filemanager.helpers.PlaybackRequest
import org.fossify.filemanager.helpers.AudioStorage
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
    private val audioFocus by lazy { AudioFocus(this, ::pause) }
    private val storageEvents by lazy {
        StorageEvents(this, {}, { root ->
            if (getCurrentPath().isWithinStorage(root)) {
                requests.invalidate()
                playbackFailed(IOException("Audio storage was removed"))
            }
        })
    }
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playlist = ArrayList<String>()
    private var currentIndex = 0
    private val playerState = PlayerState()
    private val isPrepared get() = playerState.isPrepared
    private val listeners = mutableSetOf<PlaybackListener>()
    private val requests = PlaybackRequest()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addListener(listener: PlaybackListener) {
        listeners.add(listener)
        listener.onTrackChanged(getCurrentPath(), isPlaying())
    }

    fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }
    var repeatMode = MUSIC_PLAYER_REPEAT_MODE_ONCE

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        repeatMode = config.musicPlayerRepeatMode
        createNotificationChannel()
        setupMediaSession()
        storageEvents.start()
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
        requests.invalidate()
        storageEvents.close()
        listeners.clear()
        audioFocus.release()
        releaseMediaPlayer()
        mediaSession?.release()
        super.onDestroy()
    }

    fun openPath(path: String, requestId: String) {
        if (!requests.accept(requestId)) return
        if (path == getCurrentPath() && isPrepared) return
        releaseMediaPlayer()
        playlist = arrayListOf(path)
        currentIndex = 0
        playerState.prepare()
        onTrackChangedUpdated(path, false)
        val generation = requests.generation
        AudioStorage(applicationContext).loadPlaylist(path) { result ->
            mainHandler.post {
                if (generation == requests.generation) {
                    result.onSuccess { paths ->
                        if (paths.isEmpty()) {
                            playbackFailed(SecurityException("No visible audio files"))
                        } else {
                            val autoPlay = playerState.playWhenReady
                            playlist = ArrayList(paths)
                            currentIndex = playlist.indexOf(path).coerceAtLeast(0)
                            playCurrent(autoPlay)
                        }
                    }.onFailure { playbackFailed(IOException("Cannot load audio folder", it)) }
                }
            }
        }
    }

    fun togglePlayPause() {
        if (isPlaying() || playerState.playWhenReady) pause() else play()
    }

    fun play() {
        if (isPlaying()) return
        playerState.requestPlay()
        if (playerState.status == PlayerState.Status.PREPARING) return
        if (!isPrepared) {
            playCurrent()
            return
        }
        try {
            // Target 35+ requires a visible activity or foreground service before requesting focus.
            startForegroundWithNotification()
            if (!audioFocus.acquire()) {
                pause()
                return
            }
            mediaPlayer?.start()
            playerState.started()
            onPlaybackStateUpdated()
        } catch (error: IllegalStateException) {
            playbackFailed(error)
        } catch (error: SecurityException) {
            playbackFailed(error)
        }
    }

    fun pause() {
        if (isPlaying()) mediaPlayer?.pause()
        playerState.pause()
        audioFocus.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
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
            seekTo(0)
        }
    }

    fun seekTo(positionMs: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(positionMs.coerceIn(0, getDuration()))
            updateMediaSessionPlaybackState(isPlaying())
        }
    }

    fun getCurrentPath() = playlist.getOrNull(currentIndex) ?: ""

    fun isPlaying() = playerState.status == PlayerState.Status.PLAYING

    fun isPlaylistEmpty() = playlist.isEmpty()

    fun getDuration() = if (isPrepared) mediaPlayer?.duration ?: 0 else 0

    fun getCurrentPosition() = if (isPrepared) mediaPlayer?.currentPosition ?: 0 else 0

    private fun playCurrent(autoPlay: Boolean = true) {
        val path = playlist.getOrNull(currentIndex) ?: return
        releaseMediaPlayer()
        if (!isVisibleAudio(path, config.shouldShowHidden())) {
            playbackFailed(SecurityException("Hidden audio is no longer visible"))
            return
        }
        playerState.prepare()
        if (!autoPlay) playerState.pause()
        onTrackChangedUpdated(path, false)
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.apply {
                setAudioAttributes(AudioFocus.attributes())
                setWakeMode(this@MusicPlayerService, PowerManager.PARTIAL_WAKE_LOCK)
                AudioStorage(this@MusicPlayerService).setDataSource(this, path)
                setOnPreparedListener {
                    if (mediaPlayer === it) {
                        playerState.ready()
                        updateMediaSessionMetadata(path)
                        if (playerState.playWhenReady) play() else onPlaybackStateUpdated()
                    }
                }
                setOnCompletionListener {
                    if (mediaPlayer === it) onTrackCompleted()
                }
                setOnErrorListener { failedPlayer, what, extra ->
                    if (mediaPlayer === failedPlayer) {
                        playbackFailed(IOException("MediaPlayer error: $what/$extra"))
                    }
                    true
                }
                prepareAsync()
            }
        } catch (error: IOException) {
            playbackFailed(error)
        } catch (error: IllegalArgumentException) {
            playbackFailed(error)
        } catch (error: IllegalStateException) {
            playbackFailed(error)
        } catch (error: SecurityException) {
            playbackFailed(error)
        }
    }

    private fun playbackFailed(error: Exception) {
        Log.w("MusicPlayerService", "Audio playback failed", error)
        releaseMediaPlayer()
        playerState.stop(failed = true)
        audioFocus.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        onPlaybackStateUpdated()
        listeners.toList().forEach { it.onError() }
    }

    private fun onTrackCompleted() {
        when (repeatMode) {
            MUSIC_PLAYER_REPEAT_MODE_REPEAT_ONE -> replayCurrent()
            MUSIC_PLAYER_REPEAT_MODE_SEQUENTIAL -> playNext()
            else -> pauseAtStart()
        }
    }

    private fun replayCurrent() {
        playerState.pause()
        seekTo(0)
        play()
    }

    private fun pauseAtStart() {
        // Completion already stopped the native player. Do not pause an unprepared player.
        if (isPrepared && mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        playerState.pause()
        seekTo(0)
        audioFocus.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        onPlaybackStateUpdated()
    }

    private fun stopPlayback() {
        requests.invalidate()
        audioFocus.release()
        releaseMediaPlayer()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        onPlaybackStateUpdated()
        stopSelf()
    }

    private fun releaseMediaPlayer() {
        playerState.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun onTrackChangedUpdated(path: String, isPlaying: Boolean) {
        listeners.toList().forEach { it.onTrackChanged(path, isPlaying) }
        updateMediaSessionMetadata(path)
        updateMediaSessionPlaybackState(isPlaying)
        updateNotification()
    }

    private fun onPlaybackStateUpdated() {
        val playing = isPlaying()
        listeners.toList().forEach { it.onPlaybackStateChanged(playing) }
        updateMediaSessionPlaybackState(playing)
        updateNotification()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "MusicPlayerService").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onSkipToNext() = playNext()
                override fun onSkipToPrevious() = playPrevious()
                override fun onStop() = stopPlayback()
                override fun onSeekTo(pos: Long) = seekTo(pos.coerceIn(0L, getDuration().toLong()).toInt())
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
        val state = when (playerState.status) {
            PlayerState.Status.STOPPED -> PlaybackState.STATE_STOPPED
            PlayerState.Status.PREPARING -> PlaybackState.STATE_BUFFERING
            PlayerState.Status.PAUSED -> PlaybackState.STATE_PAUSED
            PlayerState.Status.PLAYING -> PlaybackState.STATE_PLAYING
            PlayerState.Status.ERROR -> PlaybackState.STATE_ERROR
        }
        val actions = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO

        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, getCurrentPosition().toLong(), if (isPlaying) 1f else 0f)
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
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MusicPlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
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
