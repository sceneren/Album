package com.github.sceneren.album.video.media3

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.TextureView
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.video.AlbumVideoPlaybackState
import com.github.sceneren.album.video.AlbumVideoPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercises the actual factory, view resources and Media3 configuration without loading media. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class Media3AlbumVideoPlayerFactoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val activePlayers = mutableListOf<AlbumVideoPlayer>()

    @After
    fun releasePlayers() {
        activePlayers.forEach(AlbumVideoPlayer::release)
    }

    @Test
    fun factoryCreatesIndependentMainThreadPlayers() {
        val factory = Media3AlbumVideoPlayerFactory()
        val first = create(factory)
        val second = create(factory)
        val firstMedia3 = requireNotNull((first.view as PlayerView).player)
        val secondMedia3 = requireNotNull((second.view as PlayerView).player)

        assertNotSame(first, second)
        assertNotSame(first.view, second.view)
        assertNotSame(firstMedia3, secondMedia3)
        assertSame(Looper.getMainLooper(), firstMedia3.applicationLooper)
        assertSame(Looper.getMainLooper(), secondMedia3.applicationLooper)
        assertEquals(Player.REPEAT_MODE_OFF, firstMedia3.repeatMode)
        assertEquals(C.USAGE_MEDIA, firstMedia3.audioAttributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, firstMedia3.audioAttributes.contentType)
        assertEquals(AlbumVideoPlaybackState(), first.snapshot())

        first.setMuted(true)
        first.release()

        assertEquals(1f, secondMedia3.volume, 0f)
        assertEquals(AlbumVideoPlaybackState(), second.snapshot())
        assertSame(secondMedia3, (second.view as PlayerView).player)
    }

    @Test
    fun renderingViewIsParentlessAspectFitTextureWithoutAnyTransportUi() {
        val player = create()
        val view = player.view as PlayerView

        assertNull(view.parent)
        assertTrue(view.videoSurfaceView is TextureView)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, view.resizeMode)
        assertFalse(view.useController)
        assertFalse(view.isClickable)
        assertFalse(view.isFocusable)
        assertNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_controller))
        assertNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_controller_placeholder))
        assertNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_error_message))
        assertNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_buffering))
        assertNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_artwork))
    }

    @Test
    fun activityContextIsNotRetainedByRenderingView() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val player = Media3AlbumVideoPlayerFactory().create(controller.get())
        activePlayers += player
        try {
            assertSame(context, player.view.context)
            player.release()
            assertNull((player.view as PlayerView).player)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun releasedPlayerAcceptsRepeatedCleanupWithoutCallbacksOrResurrection() {
        val player = create()
        var callbacks = 0
        player.onChanged = { callbacks++ }

        player.release()
        player.release()
        player.onChanged = { callbacks++ }
        player.load(Uri.parse("content://media/external/video/media/1"), 0, true, false)
        player.setPlaying(true)
        player.setMuted(false)
        player.seekTo(1_000)
        player.retry()

        assertEquals(0, callbacks)
        assertNull(player.onChanged)
        assertNull((player.view as PlayerView).player)
        assertEquals(AlbumVideoPlaybackState(), player.snapshot())
    }

    private fun create(
        factory: Media3AlbumVideoPlayerFactory = Media3AlbumVideoPlayerFactory(),
    ): AlbumVideoPlayer = factory.create(context).also(activePlayers::add)
}
