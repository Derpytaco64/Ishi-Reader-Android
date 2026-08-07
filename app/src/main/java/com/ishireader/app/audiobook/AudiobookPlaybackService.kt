@file:OptIn(UnstableApi::class)

package com.ishireader.app.audiobook

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Hosts the single ExoPlayer + MediaSession for audiobook playback. Being a MediaSessionService
 * (rather than driving ExoPlayer straight from AudiobookPlayerActivity) is what gets lock-screen/
 * notification transport controls and background playback for free -- Media3's own
 * DefaultMediaNotificationProvider builds and keeps that notification in sync with the session's
 * state without any of that having to be hand-rolled here, and playback keeps running (as a
 * foreground service, see the manifest's mediaPlayback foregroundServiceType) if the user leaves
 * AudiobookPlayerActivity, the same as the website's MediaSession-backed background playback.
 */
class AudiobookPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Stopping playback (rather than just pausing) when the user swipes the app away from
     *  recents matches typical audiobook-app behavior -- MediaSessionService's own default
     *  onTaskRemoved keeps a *paused* session's service alive indefinitely otherwise. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession
        if (session != null && (!session.player.playWhenReady || session.player.mediaItemCount == 0)) {
            session.player.stop()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
