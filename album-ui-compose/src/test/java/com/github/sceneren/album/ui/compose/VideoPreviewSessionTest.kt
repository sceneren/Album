package com.github.sceneren.album.ui.compose

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.ui.compose.internal.video.VideoPreviewSession
import com.github.sceneren.album.video.AlbumVideoPlaybackState
import com.github.sceneren.album.video.AlbumVideoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoPreviewSessionTest {
    private val uri = Uri.parse("content://media/external/video/media/1")
    private val other = Uri.parse("content://media/external/video/media/2")
    private val created = mutableListOf<FakeComposeVideoPlayer>()
    private val session = VideoPreviewSession {
        assertTrue(created.all { it.released })
        FakeComposeVideoPlayer().also(created::add)
    }

    @Test fun createsOnlyInForegroundAndDoesNotRecreateOnRecomposition() {
        session.show(uri)
        assertTrue(created.isEmpty())
        session.setForeground(true)
        repeat(5) { session.show(uri); session.setForeground(true) }
        assertEquals(1, created.size)
        assertTrue(created.single().state.playWhenReady)
        assertTrue(created.single().muteEnabled)
        session.close()
        assertTrue(created.single().released)
    }

    @Test fun dragAndImagePageReleaseBeforeNextVideoAndRestorePositionAndPause() {
        session.setForeground(true)
        session.show(uri)
        val first = created.single()
        first.emit(first.state.copy(positionMs = 12_345))
        session.togglePlayback()
        session.toggleMute()
        session.show(null) // A pager drag or image cannot keep a decoder alive.
        assertTrue(first.released)
        assertNull(session.player)
        session.show(other)
        assertEquals(other, created.last().loadedUri)
        assertFalse(created.last().muteEnabled)
        session.show(uri)
        assertEquals(12_345L, created.last().state.positionMs)
        assertFalse(created.last().state.playWhenReady)
        session.close()
    }

    @Test fun backgroundReleasePreservesIntentAndOldCallbacksCannotCorruptNewPlayer() {
        session.setForeground(true)
        session.show(uri)
        val first = created.single()
        val lateCallback = first.onChanged
        first.emit(first.state.copy(positionMs = 678))
        session.setForeground(false)
        assertTrue(first.released)
        assertNull(first.onChanged)
        session.setForeground(true)
        assertEquals(678L, session.playback.positionMs)
        first.state = first.state.copy(positionMs = 99_999)
        lateCallback?.invoke()
        assertEquals(678L, session.playback.positionMs)
        assertTrue(session.playback.playWhenReady)
        session.close()
    }

    @Test fun completedVideoStaysStoppedAcrossRecreationAndReplaysFromStart() {
        session.setForeground(true)
        session.show(uri)
        val first = created.single()
        first.emit(first.state.copy(positionMs = 30_000, durationMs = 30_000, ended = true, isPlaying = false))
        assertTrue(session.playback.ended)
        session.setForeground(false)
        session.setForeground(true)
        val resumed = created.last()
        assertFalse(resumed.state.playWhenReady)
        assertEquals(29_999L, resumed.state.positionMs)
        assertTrue(session.playback.ended)
        session.togglePlayback()
        assertEquals(0L, resumed.state.positionMs)
        assertTrue(resumed.state.playWhenReady)
        assertFalse(session.playback.ended)
        session.close()
    }

    @Test fun nonSeekableReplayReloadsInsteadOfCallingUnsupportedSeek() {
        session.setForeground(true)
        session.show(uri)
        val player = created.single()
        player.emit(player.state.copy(seekable = false, ended = true, isPlaying = false))
        session.togglePlayback()
        assertEquals(2, player.loads)
        assertEquals(0L, player.state.positionMs)
        assertEquals(0, player.seeks)
        assertTrue(player.state.playWhenReady)
        session.close()
    }

    @Test fun errorsRemainVisibleUntilExplicitRetryAndCloseIsIdempotent() {
        session.setForeground(true)
        session.show(uri)
        val player = created.single()
        player.emit(player.state.copy(failed = true, isPlaying = false))
        assertTrue(session.playback.failed)
        session.togglePlayback()
        assertEquals(1, player.retries)
        assertFalse(session.playback.failed)
        assertTrue(player.state.playWhenReady)
        session.close()
        session.close()
        session.show(other)
        session.setForeground(true)
        assertEquals(1, player.releases)
        assertEquals(1, created.size)
    }

    @Test fun asynchronousRetryAndReplayCanClearOldTerminalSnapshots() {
        session.setForeground(true)
        session.show(uri)
        val player = created.single()
        player.completeCommandsImmediately = false
        player.emit(player.state.copy(failed = true))
        session.togglePlayback()
        assertTrue(session.playback.failed) // The old state can remain until a decoder event.
        player.emit(player.state.copy(failed = false))
        assertFalse(session.playback.failed)
        player.emit(player.state.copy(ended = true))
        session.togglePlayback()
        assertTrue(session.playback.ended)
        player.emit(player.state.copy(ended = false, positionMs = 0))
        assertFalse(session.playback.ended)
        assertTrue(session.playback.playWhenReady)
        session.close()
    }

    @Test fun seekingClampsAndPreservesPausedIntentAndInterruptedDragResumes() {
        session.setForeground(true)
        session.show(uri)
        val player = created.single()
        session.togglePlayback()
        session.beginScrub()
        session.endScrub(2f)
        assertEquals(player.state.durationMs, player.state.positionMs)
        assertFalse(player.state.playWhenReady)
        session.seekToFraction(-1f)
        assertEquals(0L, player.state.positionMs)
        session.togglePlayback()
        session.beginScrub()
        assertFalse(player.state.playWhenReady)
        session.setForeground(false)
        session.setForeground(true)
        assertTrue(created.last().state.playWhenReady)
        session.close()
    }
}

internal class FakeComposeVideoPlayer : AlbumVideoPlayer {
    override val view = View(ApplicationProvider.getApplicationContext<Context>())
    override var onChanged: (() -> Unit)? = null
    var state = AlbumVideoPlaybackState()
    var loadedUri: Uri? = null
    var muteEnabled = true
    var released = false
    var releases = 0
    var loads = 0
    var retries = 0
    var seeks = 0
    var completeCommandsImmediately = true

    override fun load(uri: Uri, positionMs: Long, playWhenReady: Boolean, muted: Boolean) {
        loadedUri = uri
        this.muteEnabled = muted
        loads++
        state = AlbumVideoPlaybackState(
            positionMs = positionMs,
            durationMs = 30_000,
            playWhenReady = playWhenReady,
            // UI tests should not need clocks/timers to observe controls.
            isPlaying = false,
            seekable = true,
            hasRenderedFrame = true,
        )
        onChanged?.invoke()
    }
    override fun snapshot() = state
    override fun setPlaying(playing: Boolean) = emit(state.copy(playWhenReady = playing))
    override fun setMuted(muted: Boolean) { this.muteEnabled = muted }
    override fun seekTo(positionMs: Long) {
        check(state.seekable)
        seeks++
        if (completeCommandsImmediately) emit(state.copy(positionMs = positionMs, ended = false))
    }
    override fun retry() {
        retries++
        if (completeCommandsImmediately) emit(state.copy(failed = false))
    }
    override fun release() { released = true; releases++; onChanged = null }
    fun emit(value: AlbumVideoPlaybackState) { state = value; onChanged?.invoke() }
}
