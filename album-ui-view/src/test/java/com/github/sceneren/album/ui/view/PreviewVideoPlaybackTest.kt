package com.github.sceneren.album.ui.view

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.ui.view.internal.video.PreviewVideoOutput
import com.github.sceneren.album.ui.view.internal.video.PreviewVideoPlayback
import com.github.sceneren.album.ui.view.internal.video.PreviewVideoUiState
import com.github.sceneren.album.video.AlbumVideoPlaybackState
import com.github.sceneren.album.video.AlbumVideoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the actual lifecycle/control owner using only the public, engine-independent API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewVideoPlaybackTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val first = Uri.parse("content://media/external/video/media/1")
    private val second = Uri.parse("content://media/external/video/media/2")
    private val players = mutableListOf<FakeVideoPlayer>()
    private var ui = PreviewVideoUiState()
    private val playback = PreviewVideoPlayback(
        playerFactory = { FakeVideoPlayer(context).also { players += it } },
        onStateChanged = { ui = it },
    )
    private val output = RecordingOutput()

    @Test
    fun onlyResumedSettledAttachedVideoCreatesAPlayerAndStartsMuted() {
        playback.select(first)
        playback.setOutput(output)
        playback.setResumed(true)
        assertTrue(players.isEmpty())

        playback.setSettled(true)

        assertEquals(1, players.size)
        assertSame(players.single(), output.player)
        assertEquals(Load(first, 0, true, true), players.single().loads.single())
        assertTrue(ui.showPause)
        assertTrue(ui.muted)
        assertTrue(ui.active)
        playback.setResumed(true)
        playback.setSettled(true)
        playback.setOutput(output)
        assertEquals(1, players.size)
    }

    @Test
    fun dragReleasesBeforeNextPageAndRestoresPerUriPositionPauseAndGlobalMute() {
        activate()
        val original = players.single()
        original.update(original.state.copy(positionMs = 4_200))
        playback.togglePlay()
        playback.toggleMuted()
        assertFalse(original.state.playWhenReady)
        assertFalse(original.mutedValue)

        playback.setSettled(false)
        assertTrue(original.released)
        assertNull(original.onChanged)
        assertNull(output.player)
        playback.select(second)
        playback.setOutput(output)
        assertEquals(1, players.size)
        playback.setSettled(true)
        val next = players.last()
        assertEquals(Load(second, 0, true, false), next.loads.single())

        playback.setSettled(false)
        playback.select(first)
        playback.setOutput(output)
        playback.setSettled(true)

        assertTrue(next.released)
        assertEquals(Load(first, 4_200, false, false), players.last().loads.single())
        assertEquals(1, players.count { !it.released })
    }

    @Test
    fun backgroundAndDetachedPageReleaseWhileResumeRecreatesAndCloseIsTerminal() {
        activate()
        val firstPlayer = players.single()
        firstPlayer.update(firstPlayer.state.copy(positionMs = 3_000))
        val staleCallback = firstPlayer.onChanged

        playback.setResumed(false)
        assertTrue(firstPlayer.released)
        assertFalse(ui.active)
        staleCallback?.invoke()
        assertFalse(ui.active)
        playback.setResumed(true)
        assertEquals(Load(first, 3_000, true, true), players.last().loads.single())

        val secondPlayer = players.last()
        playback.setOutput(null)
        assertTrue(secondPlayer.released)
        assertFalse(ui.active)
        playback.setOutput(output)
        val thirdPlayer = players.last()
        playback.close()
        playback.close()
        playback.setResumed(true)
        playback.setSettled(true)
        playback.select(second)
        playback.setOutput(output)

        assertTrue(thirdPlayer.released)
        assertEquals(3, players.size)
        assertFalse(ui.active)
    }

    @Test
    fun completedVideoKeepsEndFrameAndExplicitPlayRewindsWithoutLooping() {
        activate()
        val active = players.single()
        active.update(active.state.copy(positionMs = 10_000, ended = true, isPlaying = false))

        assertTrue(ui.ended)
        assertFalse(ui.showPause)
        assertTrue(active.seeks.isEmpty())
        assertFalse(active.released)
        playback.togglePlay()

        assertEquals(listOf(0L), active.seeks)
        assertTrue(active.state.playWhenReady)
        assertFalse(ui.ended)
        assertTrue(ui.showPause)
    }

    @Test
    fun completedVideoReentryRendersLastFramePausedAndCanReplay() {
        activate()
        players.single().let { it.update(it.state.copy(positionMs = 10_000, ended = true)) }
        playback.setResumed(false)
        playback.setResumed(true)

        val recreated = players.last()
        assertEquals(Load(first, 9_999, false, true), recreated.loads.single())
        assertTrue(ui.ended)
        assertFalse(ui.showPause)
        playback.togglePlay()
        assertEquals(listOf(0L), recreated.seeks)
        assertTrue(ui.showPause)
    }

    @Test
    fun failedPlaybackShowsRetryAndRetryResumesAtCurrentPosition() {
        activate()
        val active = players.single()
        active.update(active.state.copy(positionMs = 1_234, failed = true, isPlaying = false))

        assertTrue(ui.failed)
        assertFalse(ui.showPause)
        assertFalse(output.hasFrame)
        playback.togglePlay()

        assertEquals(1, active.retries)
        assertEquals(1_234L, active.state.positionMs)
        assertFalse(ui.failed)
        assertTrue(active.state.playWhenReady)
    }

    @Test
    fun delayedRetryDoesNotLatchStaleFailureAfterTheHealthyEvent() {
        activate()
        val active = players.single()
        active.update(active.state.copy(positionMs = 1_234, failed = true, isPlaying = false))
        active.delayRetryCompletion = true

        playback.togglePlay()

        assertEquals(1, active.retries)
        assertTrue(ui.failed) // The engine still reports the old failure until its next event.
        active.update(active.state.copy(failed = false, isPlaying = true))
        assertFalse(ui.failed)
        assertTrue(ui.showPause)
        assertTrue(output.hasFrame)
    }

    @Test
    fun restoredFailureIsRetainedUntilRetryButDoesNotLatchItsDelayedOldSnapshot() {
        activate()
        players.single().let { it.update(it.state.copy(failed = true, isPlaying = false)) }
        playback.setResumed(false)
        playback.setResumed(true)
        val recreated = players.last()
        // A fresh load may be healthy, but it must not silently discard the saved failure intent.
        assertFalse(recreated.state.failed)
        assertTrue(ui.failed)
        assertFalse(recreated.state.playWhenReady)
        recreated.update(recreated.state.copy(failed = true))
        recreated.delayRetryCompletion = true

        playback.togglePlay()
        assertTrue(ui.failed)
        recreated.update(recreated.state.copy(failed = false, isPlaying = true))

        assertFalse(ui.failed)
        assertTrue(ui.showPause)
        assertEquals(1, recreated.retries)
    }

    @Test
    fun delayedReplaySeekDoesNotLatchEndedAfterTheHealthyEvent() {
        activate()
        val active = players.single()
        active.update(active.state.copy(positionMs = 10_000, ended = true, isPlaying = false))
        active.delaySeekCompletion = true

        playback.togglePlay()

        assertEquals(listOf(0L), active.seeks)
        assertTrue(ui.ended)
        active.update(active.state.copy(positionMs = 0, ended = false, isPlaying = true))
        assertFalse(ui.ended)
        assertTrue(ui.showPause)
    }

    @Test
    fun seekingRestoredEndClearsItAfterDelayedSeekAndRetainsPauseIntent() {
        activate()
        players.single().let { it.update(it.state.copy(positionMs = 10_000, ended = true, isPlaying = false)) }
        playback.setResumed(false)
        playback.setResumed(true)
        val recreated = players.last()
        assertFalse(recreated.state.ended)
        assertTrue(ui.ended)
        recreated.update(recreated.state.copy(ended = true))
        recreated.delaySeekCompletion = true

        playback.seekToFraction(0.5f)
        assertTrue(ui.ended)
        recreated.update(recreated.state.copy(positionMs = 5_000, ended = false))

        assertEquals(listOf(5_000L), recreated.seeks)
        assertFalse(ui.ended)
        assertFalse(ui.showPause)
        assertFalse(recreated.state.playWhenReady)
    }

    @Test
    fun scrubSeeksOnReleaseAndRetainsUserPauseIntent() {
        activate()
        val active = players.single()
        playback.togglePlay()
        playback.beginScrub()
        assertFalse(active.state.playWhenReady)
        assertTrue(active.seeks.isEmpty())

        playback.endScrub(0.65f)

        assertEquals(listOf(6_500L), active.seeks)
        assertFalse(active.state.playWhenReady)
        assertFalse(ui.showPause)
        playback.togglePlay()
        playback.beginScrub()
        assertFalse(active.state.playWhenReady)
        playback.endScrub(0.25f)
        assertTrue(active.state.playWhenReady)
        assertEquals(2_500L, active.state.positionMs)
    }

    @Test
    fun releaseDuringScrubDoesNotConvertAutomaticPauseIntoUserPause() {
        activate()
        playback.beginScrub()
        playback.setResumed(false)
        playback.setResumed(true)

        assertEquals(Load(first, 0, true, true), players.last().loads.single())
        playback.endScrub(0.5f)
        assertTrue(players.last().state.playWhenReady)
    }

    @Test
    fun unknownDurationOrNonSeekableMediaNeverSeek() {
        activate()
        val active = players.single()
        active.update(active.state.copy(durationMs = 0, seekable = false))
        playback.beginScrub()
        playback.seekToFraction(0.5f)
        playback.endScrub(0.5f)

        assertTrue(active.seeks.isEmpty())
        assertFalse(ui.seekable)
        active.update(active.state.copy(ended = true))
        playback.togglePlay()
        assertEquals(2, active.loads.size)
        assertEquals(Load(first, 0, true, true), active.loads.last())
        assertTrue(active.seeks.isEmpty())
    }

    @Test
    fun selectingImageDropsVideoOutputAndKeepsNoPlayer() {
        activate()
        playback.select(null)

        assertTrue(players.single().released)
        assertNull(output.player)
        assertFalse(ui.active)
        playback.setResumed(false)
        playback.setResumed(true)
        playback.setSettled(false)
        playback.setSettled(true)
        assertEquals(1, players.size)
    }

    private fun activate() {
        playback.select(first)
        playback.setOutput(output)
        playback.setSettled(true)
        playback.setResumed(true)
    }

    private class RecordingOutput : PreviewVideoOutput {
        var player: AlbumVideoPlayer? = null
        var hasFrame = false
        override fun attach(player: AlbumVideoPlayer?) { this.player = player }
        override fun showFrame(hasRenderedFrame: Boolean) { hasFrame = hasRenderedFrame }
    }
}

internal data class Load(val uri: Uri, val positionMs: Long, val playing: Boolean, val muted: Boolean)

/** A host-supplied fake: no Media3 types, codec, service or media provider is involved. */
internal class FakeVideoPlayer(context: Context) : AlbumVideoPlayer {
    override val view = View(context)
    override var onChanged: (() -> Unit)? = null
    var state = AlbumVideoPlaybackState()
    var mutedValue = true
    var released = false
    val loads = mutableListOf<Load>()
    val seeks = mutableListOf<Long>()
    var retries = 0
    var delayRetryCompletion = false
    var delaySeekCompletion = false

    override fun load(uri: Uri, positionMs: Long, playWhenReady: Boolean, muted: Boolean) {
        loads += Load(uri, positionMs, playWhenReady, muted)
        mutedValue = muted
        update(
            AlbumVideoPlaybackState(
                positionMs = positionMs,
                durationMs = 10_000,
                playWhenReady = playWhenReady,
                isPlaying = playWhenReady,
                seekable = true,
                hasRenderedFrame = true,
            ),
        )
    }

    override fun snapshot() = state
    override fun setPlaying(playing: Boolean) {
        update(state.copy(playWhenReady = playing, isPlaying = playing && !state.ended && !state.failed))
    }
    override fun setMuted(muted: Boolean) { mutedValue = muted }
    override fun seekTo(positionMs: Long) {
        seeks += positionMs
        if (!delaySeekCompletion) update(state.copy(positionMs = positionMs, ended = false))
    }
    override fun retry() {
        retries++
        if (!delayRetryCompletion) update(state.copy(failed = false))
    }
    override fun release() {
        onChanged = null
        released = true
    }
    fun update(value: AlbumVideoPlaybackState) {
        state = value
        onChanged?.invoke()
    }
}
