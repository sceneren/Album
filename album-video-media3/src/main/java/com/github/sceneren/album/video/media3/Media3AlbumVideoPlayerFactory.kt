package com.github.sceneren.album.video.media3

import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.sceneren.album.video.AlbumVideoPlayer
import com.github.sceneren.album.video.AlbumVideoPlayerFactory
import com.github.sceneren.album.video.media3.internal.Media3AlbumVideoPlayer

/**
 * Optional Media3 playback engine for either Album picker UI.
 *
 * The host registers an instance with each UI's `AlbumUi.setVideoPlayerFactory`. Each [create]
 * call owns a new ExoPlayer and a parentless, aspect-fit TextureView inside a control-free
 * PlayerView. Picker controls, foreground lifecycle, play intent and mute preference stay with
 * the UI; no Media3 types are required in the UI modules.
 *
 * Players use application context, request media audio focus only for unmuted playback, and
 * pause on audio-becoming-noisy events. Muted autoplay does not interrupt audio from other apps.
 * Media3 opens the supplied content URI asynchronously without copying media. The host
 * remains responsible for URI read access and for calling [AlbumVideoPlayer.release] on the main
 * thread when a preview leaves the foreground. This factory holds no Context or player instances.
 */
@OptIn(UnstableApi::class)
class Media3AlbumVideoPlayerFactory : AlbumVideoPlayerFactory {
    /** Creates an independent main-thread player without attaching its view to a parent. */
    override fun create(context: Context): AlbumVideoPlayer {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Album video players must be created on the main thread."
        }
        val applicationContext = context.applicationContext
        val playerView = LayoutInflater.from(applicationContext)
            .inflate(R.layout.amv_player_view, null, false) as PlayerView
        val player = ExoPlayer.Builder(applicationContext)
            .setLooper(Looper.getMainLooper())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        return try {
            Media3AlbumVideoPlayer(playerView, player)
        } catch (failure: RuntimeException) {
            playerView.player = null
            player.release()
            throw failure
        }
    }
}
