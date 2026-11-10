package com.github.sceneren.album.video.media3.internal

import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.sceneren.album.video.AlbumVideoPlaybackState
import com.github.sceneren.album.video.AlbumVideoPlayer

/**
 * Main-looper adapter. Snapshots read Media3's in-memory state only; the picker owns progress
 * polling, so no timer, provider query or decoder operation is performed by [snapshot].
 */
@OptIn(UnstableApi::class)
internal class Media3AlbumVideoPlayer(
    private val playerView: PlayerView,
    initialPlayer: Player,
) : AlbumVideoPlayer {
    private var player: Player? = initialPlayer
    private var changedListener: (() -> Unit)? = null
    private var hasRenderedFrame = false
    private var released = false
    private var releasedState = AlbumVideoPlaybackState()

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            if (!released) hasRenderedFrame = true
        }

        override fun onEvents(player: Player, events: Player.Events) {
            // Includes first-frame, errors, timeline/seekability, seek, playback and audio events.
            // Media3 batches these on the application looper after individual callbacks, so a
            // snapshot taken by the UI sees a consistent state (including hasRenderedFrame).
            notifyChanged()
        }
    }

    init {
        checkMainThread()
        check(initialPlayer.applicationLooper == Looper.getMainLooper())
        initialPlayer.repeatMode = Player.REPEAT_MODE_OFF
        initialPlayer.addListener(listener)
        playerView.player = initialPlayer
    }

    override val view: View
        get() = playerView

    override var onChanged: (() -> Unit)?
        get() = changedListener
        set(value) {
            checkMainThread()
            // A late UI assignment must not retain the host after release.
            changedListener = if (released) null else value
        }

    override fun load(uri: Uri, positionMs: Long, playWhenReady: Boolean, muted: Boolean) {
        val player = activePlayer() ?: return
        hasRenderedFrame = false
        player.playWhenReady = false
        // Apply mute before enabling playback, including when reusing this adapter for a load.
        applyMuted(player, muted)
        player.setMediaItem(MediaItem.fromUri(uri), positionMs.coerceAtLeast(0))
        player.playWhenReady = playWhenReady
        player.prepare()
        notifyChanged()
    }

    override fun snapshot(): AlbumVideoPlaybackState {
        val player = activePlayer() ?: return releasedState
        val durationMs = player.duration.coerceAtLeast(0)
        val positionMs = player.currentPosition.coerceAtLeast(0).let { position ->
            if (durationMs > 0) position.coerceAtMost(durationMs) else position
        }
        return AlbumVideoPlaybackState(
            positionMs = positionMs,
            durationMs = durationMs,
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            ended = player.playbackState == Player.STATE_ENDED,
            failed = player.playerError != null,
            seekable = player.isCurrentMediaItemSeekable,
            hasRenderedFrame = hasRenderedFrame,
        )
    }

    override fun setPlaying(playing: Boolean) {
        val player = activePlayer() ?: return
        // Do not seek on STATE_ENDED: keep the final frame until the UI explicitly replays.
        player.playWhenReady = playing
        notifyChanged()
    }

    override fun setMuted(muted: Boolean) {
        val player = activePlayer() ?: return
        applyMuted(player, muted)
        notifyChanged()
    }

    override fun seekTo(positionMs: Long) {
        val player = activePlayer() ?: return
        if (!player.isCurrentMediaItemSeekable) return
        val durationMs = player.duration.coerceAtLeast(0)
        val targetMs = positionMs.coerceAtLeast(0).let { position ->
            if (durationMs > 0) position.coerceAtMost(durationMs) else position
        }
        player.seekTo(targetMs)
        notifyChanged()
    }

    override fun retry() {
        val player = activePlayer() ?: return
        if (player.playerError == null) return
        // prepare() retains the current MediaItem, position and requested play/mute values.
        // Do not force playback or reset hasRenderedFrame while the previous frame is visible.
        player.prepare()
        notifyChanged()
    }

    override fun release() {
        checkMainThread()
        if (released) return
        val player = this.player ?: return
        releasedState = snapshot().copy(playWhenReady = false, isPlaying = false)
        released = true
        changedListener = null
        this.player = null
        try {
            player.removeListener(listener)
        } finally {
            try {
                // Its parent belongs to the picker (including AndroidView in Compose), so do not
                // remove the view from its parent; detach its TextureView/listeners instead.
                playerView.player = null
            } finally {
                player.release()
            }
        }
    }

    private fun applyMuted(player: Player, muted: Boolean) {
        // Silence before abandoning focus; enable focus handling before making audio audible.
        // Keep the existing MEDIA/MOVIE attributes, so this never changes device volume or usage.
        if (muted) player.volume = 0f
        if (player is ExoPlayer) {
            player.setAudioAttributes(player.audioAttributes, !muted)
        }
        if (!muted) player.volume = 1f
    }

    private fun activePlayer(): Player? {
        checkMainThread()
        return player
    }

    private fun notifyChanged() {
        if (!released) changedListener?.invoke()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Album video players must be accessed on the main thread."
        }
    }
}
