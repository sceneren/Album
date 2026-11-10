package com.github.sceneren.photopicker

import android.os.SystemClock
import android.widget.FrameLayout
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.sceneren.album.video.AlbumVideoPlaybackState
import com.github.sceneren.album.video.AlbumVideoPlayer
import com.github.sceneren.album.video.media3.Media3AlbumVideoPlayerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real codec, TextureView and content-provider validation using a bounded test-only fixture. */
@RunWith(AndroidJUnit4::class)
class VideoPlaybackIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun contentVideoRendersPausesSeeksEndsAndReleases() {
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "photo_picker").apply { mkdirs() }
        val fixture = File(directory, "album-video-integration-${java.util.UUID.randomUUID()}.mp4")
        instrumentation.context.assets.open("media3_sample.mp4").use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.album.api.fileprovider", fixture)
        var player: AlbumVideoPlayer? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val container = FrameLayout(activity)
                    activity.setContentView(container)
                    player = Media3AlbumVideoPlayerFactory().create(activity).also {
                        container.addView(it.view, FrameLayout.LayoutParams(-1, -1))
                        it.load(uri, 0, playWhenReady = true, muted = true)
                    }
                }
                val active = checkNotNull(player)
                awaitState(active) { it.hasRenderedFrame && it.isPlaying && it.positionMs > 100 }
                instrumentation.runOnMainSync { active.setPlaying(false) }
                val paused = state(active)
                assertFalse(paused.playWhenReady)
                assertTrue(paused.durationMs > 0)
                assertTrue(paused.seekable)
                SystemClock.sleep(200)
                assertTrue(kotlin.math.abs(state(active).positionMs - paused.positionMs) < 150)

                instrumentation.runOnMainSync { active.seekTo(paused.durationMs / 2) }
                awaitState(active) { it.positionMs >= paused.durationMs / 3 && !it.playWhenReady }
                instrumentation.runOnMainSync {
                    active.seekTo((paused.durationMs - 250).coerceAtLeast(0))
                    active.setPlaying(true)
                }
                awaitState(active) { it.ended }
                assertTrue(state(active).hasRenderedFrame)
                instrumentation.runOnMainSync {
                    val parent = active.view.parent
                    active.release()
                    active.release()
                    assertSame(parent, active.view.parent)
                    assertFalse(active.snapshot().isPlaying)
                    assertFalse(active.snapshot().playWhenReady)
                    assertEquals(null, active.onChanged)
                }
                player = null
            }
        } finally {
            player?.let { active -> instrumentation.runOnMainSync { active.release() } }
            // Delete only this known app-owned fixture, never any user/provider media.
            check(fixture.canonicalFile.parentFile == directory.canonicalFile)
            fixture.delete()
        }
    }

    private fun state(player: AlbumVideoPlayer): AlbumVideoPlaybackState {
        var value = AlbumVideoPlaybackState()
        instrumentation.runOnMainSync { value = player.snapshot() }
        return value
    }

    private fun awaitState(player: AlbumVideoPlayer, predicate: (AlbumVideoPlaybackState) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        do {
            val current = state(player)
            assertFalse("Playback failed: $current", current.failed)
            if (predicate(current)) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Playback state timed out: ${state(player)}")
    }
}
