package org.fossify.filemanager.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.MusicPlayerActivity
import org.fossify.filemanager.services.MusicPlayerService

class PlayerNotification(private val service: Service) {
    private var foreground = false
    private val manager = service.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, service.getString(R.string.music_player), NotificationManager.IMPORTANCE_LOW
        ))
    }

    fun start(path: String, playing: Boolean, token: MediaSession.Token?) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(service, NOTIFICATION_ID, build(path, playing, token), type)
        foreground = true
    }

    fun update(path: String, playing: Boolean, token: MediaSession.Token?, preparing: Boolean) {
        // Retain an existing foreground session between tracks; background promotion may be forbidden.
        if (playing || (preparing && foreground)) {
            manager.notify(NOTIFICATION_ID, build(path, playing || preparing, token))
        } else {
            remove()
        }
    }

    fun remove() {
        foreground = false
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun build(path: String, playing: Boolean, token: MediaSession.Token?): Notification {
        val intent = Intent(service, MusicPlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            service, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = MediaStyle().setShowActionsInCompactView(0, 1, 2)
        token?.let { style.setMediaSession(MediaSessionCompat.Token.fromToken(it)) }
        val playPauseIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(path.getFilenameFromPath())
            .setContentText(service.getString(R.string.music_player))
            .setContentIntent(contentIntent)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, service.getString(R.string.previous_track),
                actionIntent(MusicPlayerService.ACTION_PREVIOUS))
            .addAction(playPauseIcon, service.getString(R.string.play_pause),
                actionIntent(MusicPlayerService.ACTION_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, service.getString(R.string.next_track),
                actionIntent(MusicPlayerService.ACTION_NEXT))
            .setStyle(style)
            .build()
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(service, MusicPlayerService::class.java).setAction(action)
        return PendingIntent.getService(
            service, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL_ID = "music_player_channel"
        private const val NOTIFICATION_ID = 1000
    }
}
