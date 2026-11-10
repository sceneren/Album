package com.github.sceneren.album.video

import android.content.Context
import android.net.Uri
import android.view.View

/**
 * Creates a fresh playback instance for one visible preview. Register this factory with the
 * View or Compose `AlbumUi`; omitting it keeps video previews as static covers.
 *
 * The factory must not retain the supplied Activity [Context] after its player is released.
 * Implementations do not need Media3, Compose, or an image-loading library.
 */
fun interface AlbumVideoPlayerFactory {
    /** Called on the main thread, only for the current foreground video page. */
    fun create(context: Context): AlbumVideoPlayer
}

/**
 * A replaceable video engine and its rendering surface. All calls and [onChanged] notifications
 * run on the main thread. Loading and decoding must be asynchronous; [snapshot] must be cheap.
 *
 * The picker owns controls and lifecycle: it attaches [view], calls [load], and releases the
 * player when the page leaves the foreground. Each instance handles one media URI. Do not add
 * your own transport controls to [view] unless intentionally replacing the picker presentation.
 */
interface AlbumVideoPlayer {
    /** The engine's aspect-fit rendering View, with no parent when returned from the factory. */
    val view: View

    /** One listener for state changes, errors and the first rendered frame; null detaches it. */
    var onChanged: (() -> Unit)?

    /** Loads a content URI asynchronously, restoring position and requested play/mute intent. */
    fun load(uri: Uri, positionMs: Long, playWhenReady: Boolean, muted: Boolean)

    /** Returns current state without querying a provider or doing file/decoder work. */
    fun snapshot(): AlbumVideoPlaybackState

    /** Updates requested playback, including while buffering; false must stop audible playback. */
    fun setPlaying(playing: Boolean)

    /** Mutes only this preview, without modifying the device volume. */
    fun setMuted(muted: Boolean)

    /** Seeks to a position in milliseconds. The picker calls this only for seekable media. */
    fun seekTo(positionMs: Long)

    /** Retries a failed load; the picker separately sets requested playback to true. */
    fun retry()

    /** Releases surfaces, listeners and decoders. Must be idempotent and emit no later callbacks. */
    fun release()
}

/** Immutable, engine-independent playback state. Unknown duration is represented by zero. */
data class AlbumVideoPlaybackState(
    /** Current position, in milliseconds. */
    val positionMs: Long = 0,
    /** Positive duration, or zero while unknown. */
    val durationMs: Long = 0,
    /** User-requested playback; differs from [isPlaying] while buffering or suppressed. */
    val playWhenReady: Boolean = false,
    /** Whether playback is currently advancing. */
    val isPlaying: Boolean = false,
    /** The end was reached; remain on the final frame until an explicit seek/replay. */
    val ended: Boolean = false,
    /** Playback failed; [AlbumVideoPlayer.retry] may recover it. */
    val failed: Boolean = false,
    /** Whether seeking within this media is supported. */
    val seekable: Boolean = false,
    /** The rendering View has displayed a frame, allowing the picker to hide the cover. */
    val hasRenderedFrame: Boolean = false,
)
