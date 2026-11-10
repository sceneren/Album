package com.github.sceneren.album.ui.view.internal.video

import android.net.Uri
import com.github.sceneren.album.video.AlbumVideoPlaybackState
import com.github.sceneren.album.video.AlbumVideoPlayer

/** A page output is attached only while it is the settled, visible video. */
internal interface PreviewVideoOutput {
    fun attach(player: AlbumVideoPlayer?)
    fun showFrame(hasRenderedFrame: Boolean)
}

internal data class PreviewVideoUiState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val showPause: Boolean = false,
    val muted: Boolean = true,
    val ended: Boolean = false,
    val failed: Boolean = false,
    val seekable: Boolean = false,
    val active: Boolean = false,
    val isPlaying: Boolean = false,
)

/**
 * Owns at most one player for this preview. No player survives a page drag, detached page,
 * background transition or dismissal; per-URI position and user intent survive those releases.
 */
internal class PreviewVideoPlayback(
    private val playerFactory: () -> AlbumVideoPlayer,
    private val onStateChanged: (PreviewVideoUiState) -> Unit,
) {
    private data class SavedPlayback(
        var positionMs: Long = 0,
        var durationMs: Long = 0,
        var playWhenReady: Boolean = true,
        var ended: Boolean = false,
        var failed: Boolean = false,
    )

    private val saved = mutableMapOf<Uri, SavedPlayback>()
    private var selectedUri: Uri? = null
    private var output: PreviewVideoOutput? = null
    private var resumed = false
    private var settled = false
    private var closed = false
    private var muted = true
    private var player: AlbumVideoPlayer? = null
    // Preserve only state restored into a fresh player, not transient old engine snapshots.
    private var restoredFailed = false
    private var restoredEnded = false
    private var scrubbing = false
    private var resumeAfterScrub = false

    fun select(uri: Uri?) {
        if (closed || selectedUri == uri) return
        releasePlayer()
        output = null
        selectedUri = uri
        publish()
    }

    fun setOutput(value: PreviewVideoOutput?) {
        if (closed || output === value) return
        releasePlayer()
        output = value
        reconcile()
    }

    fun setResumed(value: Boolean) {
        if (closed || resumed == value) return
        resumed = value
        reconcile()
    }

    fun setSettled(value: Boolean) {
        if (closed || settled == value) return
        settled = value
        reconcile()
    }

    fun togglePlay() {
        val active = player ?: return
        val state = currentSaved() ?: return
        val snapshot = active.snapshot()
        if (state.failed || snapshot.failed) {
            restoredFailed = false
            restoredEnded = false
            state.failed = false
            state.ended = false
            state.playWhenReady = true
            active.retry()
            active.setPlaying(true)
        } else if (state.ended || snapshot.ended) {
            restoredEnded = false
            state.ended = false
            state.positionMs = 0
            state.playWhenReady = true
            if (snapshot.seekable) {
                active.seekTo(0)
                active.setPlaying(true)
            } else {
                selectedUri?.let { active.load(it, 0, true, muted) }
            }
        } else {
            state.playWhenReady = !snapshot.playWhenReady
            active.setPlaying(state.playWhenReady)
        }
        refresh()
    }

    fun toggleMuted() {
        if (closed) return
        muted = !muted
        player?.setMuted(muted)
        publish()
    }

    fun beginScrub() {
        val active = player ?: return
        if (scrubbing || !active.snapshot().seekable) return
        resumeAfterScrub = currentSaved()?.playWhenReady == true &&
            currentSaved()?.ended == false && currentSaved()?.failed == false
        scrubbing = true
        active.setPlaying(false)
        refresh()
    }

    fun seekToFraction(fraction: Float) {
        val active = player ?: return
        val state = currentSaved() ?: return
        val snapshot = active.snapshot()
        if (!snapshot.seekable || snapshot.durationMs <= 0) return
        val position = (snapshot.durationMs * fraction.coerceIn(0f, 1f)).toLong()
        restoredEnded = false
        state.ended = false
        state.positionMs = position
        active.seekTo(position)
        refresh()
    }

    fun endScrub(fraction: Float) {
        if (!scrubbing) return
        seekToFraction(fraction)
        scrubbing = false
        player?.setPlaying(resumeAfterScrub)
        currentSaved()?.playWhenReady = resumeAfterScrub
        refresh()
    }

    /** Called by player events, and at a bounded interval only while playback advances. */
    fun refresh() {
        val active = player ?: return
        remember(active.snapshot())
        publish()
    }

    fun close() {
        if (closed) return
        releasePlayer()
        closed = true
        output = null
        selectedUri = null
        saved.clear()
        publish()
    }

    private fun reconcile() {
        val uri = selectedUri
        if (!resumed || !settled || uri == null || output == null) {
            releasePlayer()
            publish()
            return
        }
        if (player == null) {
            val state = saved.getOrPut(uri, ::SavedPlayback)
            val created = playerFactory()
            restoredFailed = state.failed
            restoredEnded = state.ended
            player = created
            output?.attach(created)
            // A previously completed video renders its last frame without automatically replaying.
            val startPosition = if (state.ended && state.durationMs > 0) {
                (state.durationMs - 1).coerceAtLeast(0)
            } else {
                state.positionMs
            }
            created.load(
                uri,
                startPosition,
                state.playWhenReady && !state.ended && !state.failed,
                muted,
            )
            // Capture notifications only after initial intent has been applied. Custom engines
            // may synchronously report default state during load or surface attachment.
            created.onChanged = { if (player === created) refresh() }
        }
        refresh()
    }

    private fun releasePlayer() {
        val active = player ?: return
        remember(active.snapshot())
        player = null
        active.onChanged = null
        // Clear the surface before releasing codecs, and never count this pause as user intent.
        active.setPlaying(false)
        output?.attach(null)
        output?.showFrame(false)
        active.release()
        scrubbing = false
    }

    private fun remember(snapshot: AlbumVideoPlaybackState) {
        val state = currentSaved() ?: return
        state.positionMs = snapshot.positionMs.coerceAtLeast(0)
        if (snapshot.durationMs > 0) state.durationMs = snapshot.durationMs
        state.ended = restoredEnded || snapshot.ended
        state.failed = restoredFailed || snapshot.failed
        if (!scrubbing) {
            state.playWhenReady = snapshot.playWhenReady && !state.ended && !state.failed
        }
        output?.showFrame(snapshot.hasRenderedFrame && !state.failed)
    }

    private fun currentSaved(): SavedPlayback? =
        selectedUri?.let { saved.getOrPut(it, ::SavedPlayback) }

    private fun publish() {
        val state = currentSaved()
        val snapshot = player?.snapshot()
        onStateChanged(
            PreviewVideoUiState(
                positionMs = state?.positionMs ?: 0,
                durationMs = state?.durationMs ?: 0,
                showPause = state != null && state.playWhenReady && !state.ended && !state.failed,
                muted = muted,
                ended = state?.ended == true,
                failed = state?.failed == true,
                seekable = snapshot?.seekable == true && snapshot.durationMs > 0,
                active = player != null,
                isPlaying = snapshot?.isPlaying == true,
            ),
        )
    }
}
