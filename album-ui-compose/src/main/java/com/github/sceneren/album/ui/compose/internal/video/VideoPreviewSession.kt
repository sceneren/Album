package com.github.sceneren.album.ui.compose.internal.video

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.sceneren.album.video.AlbumVideoPlaybackState
import com.github.sceneren.album.video.AlbumVideoPlayer

/** Owns at most one foreground engine; saved entries contain no player or View references. */
internal class VideoPreviewSession(
    private val createPlayer: () -> AlbumVideoPlayer,
) {
    var player: AlbumVideoPlayer? by mutableStateOf(null)
        private set
    var playback: AlbumVideoPlaybackState by mutableStateOf(AlbumVideoPlaybackState())
        private set
    var muted: Boolean by mutableStateOf(true)
        private set

    private val saved = mutableMapOf<Uri, PlaybackSnapshot>()
    var currentUri: Uri? by mutableStateOf(null)
        private set
    private var foreground = false
    private var scrubbing = false
    private var resumeAfterScrub = false
    private var closed = false
    private var restoredFailure = false
    private var restoredEnd = false

    /** Null during a pager drag, so prefetched pages can never start a decoder. */
    fun show(value: Uri?) {
        if (closed) return
        if (currentUri != value) {
            releasePlayer()
            currentUri = value
            playback = AlbumVideoPlaybackState()
        }
        ensurePlayer()
    }

    fun setForeground(value: Boolean) {
        if (closed) return
        foreground = value
        if (value) ensurePlayer() else releasePlayer()
    }

    fun togglePlayback() {
        val current = player ?: return
        val snapshot = current.snapshot()
        val state = currentSaved() ?: return
        when {
            state.failed || snapshot.failed -> {
                restoredFailure = false
                restoredEnd = false
                state.failed = false
                state.ended = false
                current.retry()
                current.setPlaying(true)
            }
            state.ended || snapshot.ended -> {
                restoredEnd = false
                state.ended = false
                if (snapshot.seekable) {
                    current.seekTo(0)
                    current.setPlaying(true)
                } else {
                    current.load(checkNotNull(currentUri), 0, true, muted)
                }
            }
            else -> current.setPlaying(!snapshot.playWhenReady)
        }
        refresh()
    }

    fun toggleMute() {
        if (closed) return
        muted = !muted
        player?.setMuted(muted)
    }

    fun beginScrub() {
        val current = player ?: return
        if (scrubbing || !playback.seekable) return
        resumeAfterScrub = playback.playWhenReady && !playback.ended && !playback.failed
        scrubbing = true
        current.setPlaying(false)
        refresh()
    }

    fun seekToFraction(fraction: Float) {
        val current = player ?: return
        val snapshot = current.snapshot()
        if (!snapshot.seekable || snapshot.durationMs <= 0) return
        restoredEnd = false
        currentSaved()?.ended = false
        current.seekTo((snapshot.durationMs * fraction.coerceIn(0f, 1f)).toLong())
        refresh()
    }

    fun endScrub(fraction: Float) {
        if (!scrubbing) return
        seekToFraction(fraction)
        scrubbing = false
        player?.setPlaying(resumeAfterScrub)
        refresh()
    }

    /** Events and a progress-only UI coroutine call this; snapshot never performs media IO. */
    fun refresh() {
        val current = player ?: return
        remember(current.snapshot())
    }

    fun close() {
        if (closed) return
        releasePlayer()
        closed = true
        currentUri = null
        saved.clear()
    }

    private fun ensurePlayer() {
        val currentUri = currentUri ?: return
        if (!foreground || player != null) return
        val state = saved.getOrPut(currentUri, ::PlaybackSnapshot)
        val created = createPlayer()
        restoredFailure = state.failed
        restoredEnd = state.ended
        player = created
        // Recreate the final frame without silently looping an already completed preview.
        val position = if (state.ended && state.durationMillis > 0) {
            (state.durationMillis - 1).coerceAtLeast(0)
        } else {
            state.positionMillis
        }
        created.load(
            currentUri,
            position,
            state.playWhenReady && !state.ended && !state.failed,
            muted,
        )
        created.onChanged = { if (player === created) refresh() }
        refresh()
    }

    private fun releasePlayer() {
        val current = player ?: return
        remember(current.snapshot())
        player = null
        current.onChanged = null
        current.setPlaying(false)
        current.release()
        scrubbing = false
        playback = playback.copy(isPlaying = false, hasRenderedFrame = false)
    }

    private fun remember(snapshot: AlbumVideoPlaybackState) {
        val state = currentSaved() ?: return
        state.positionMillis = snapshot.positionMs.coerceAtLeast(0)
        if (snapshot.durationMs > 0) state.durationMillis = snapshot.durationMs
        // Preserve restored terminal state until an explicit retry/replay, but do not latch
        // transient old snapshots from engines whose retry/seek completes asynchronously.
        state.ended = restoredEnd || snapshot.ended
        state.failed = restoredFailure || snapshot.failed
        state.playWhenReady = if (scrubbing) {
            resumeAfterScrub
        } else {
            snapshot.playWhenReady && !state.ended && !state.failed
        }
        playback = snapshot.copy(
            ended = state.ended,
            failed = state.failed,
            playWhenReady = state.playWhenReady,
            durationMs = state.durationMillis,
        )
    }

    private fun currentSaved(): PlaybackSnapshot? = currentUri?.let { saved.getOrPut(it, ::PlaybackSnapshot) }
}

private data class PlaybackSnapshot(
    var positionMillis: Long = 0,
    var durationMillis: Long = 0,
    var playWhenReady: Boolean = true,
    var ended: Boolean = false,
    var failed: Boolean = false,
)
