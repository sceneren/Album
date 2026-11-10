package com.github.sceneren.album.video.media3.internal

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.video.AlbumVideoPlaybackState
import com.github.sceneren.album.video.media3.R
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Deterministic Media3 event coverage without opening providers or invoking real codecs. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class Media3AlbumVideoPlayerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uri = Uri.parse("content://media/external/video/media/42")
    private lateinit var backend: RecordingPlayer
    private lateinit var playerView: PlayerView
    private lateinit var adapter: Media3AlbumVideoPlayer

    @Before
    fun createAdapter() {
        backend = RecordingPlayer()
        playerView = LayoutInflater.from(context)
            .inflate(R.layout.amv_player_view, null, false) as PlayerView
        adapter = Media3AlbumVideoPlayer(playerView, backend.player)
        backend.operations.clear()
    }

    @After
    fun releaseAdapter() {
        adapter.release()
    }

    @Test
    fun loadPreservesContentUriPositionAndIntentWithMuteBeforePlayback() {
        var callbackLooper: Looper? = null
        adapter.onChanged = { callbackLooper = Looper.myLooper() }

        adapter.load(uri, 1_234, playWhenReady = true, muted = true)

        assertEquals(uri, backend.mediaItem?.localConfiguration?.uri)
        assertEquals(1_234, adapter.snapshot().positionMs)
        assertTrue(adapter.snapshot().playWhenReady)
        assertFalse(adapter.snapshot().isPlaying)
        assertFalse(adapter.snapshot().hasRenderedFrame)
        assertEquals(0f, backend.volume, 0f)
        assertEquals(1, backend.prepareCount)
        assertEquals(
            listOf("play:false", "volume:0.0", "media", "play:true", "prepare"),
            backend.operations,
        )
        assertSame(Looper.getMainLooper(), callbackLooper)
    }

    @Test
    fun negativeRestoredPositionAndUnknownDurationAreNormalizedWithoutIo() {
        backend.durationMs = C.TIME_UNSET
        adapter.load(uri, -50, playWhenReady = false, muted = false)
        val operationCount = backend.operations.size

        repeat(20) {
            assertEquals(AlbumVideoPlaybackState(), adapter.snapshot())
        }

        assertEquals(0, backend.positionMs)
        assertEquals(1f, backend.volume, 0f)
        assertEquals(operationCount, backend.operations.size)
        assertEquals(1, backend.prepareCount)
    }

    @Test
    fun batchedEventsReportFirstFramePlaybackTimelineAndEndWithoutAutoReplay() {
        val observedStates = mutableListOf<AlbumVideoPlaybackState>()
        adapter.onChanged = { observedStates += adapter.snapshot() }
        adapter.load(uri, 0, playWhenReady = true, muted = true)
        backend.durationMs = 4_000
        backend.seekable = true
        backend.playbackState = Player.STATE_READY
        backend.listeners.toList().forEach(Player.Listener::onRenderedFirstFrame)

        backend.emit(Player.EVENT_RENDERED_FIRST_FRAME, Player.EVENT_PLAYBACK_STATE_CHANGED)

        val ready = observedStates.last()
        assertTrue(ready.isPlaying)
        assertTrue(ready.playWhenReady)
        assertTrue(ready.hasRenderedFrame)
        assertTrue(ready.seekable)
        assertEquals(4_000, ready.durationMs)

        backend.positionMs = 4_000
        backend.playbackState = Player.STATE_ENDED
        backend.emit(Player.EVENT_PLAYBACK_STATE_CHANGED)
        adapter.setPlaying(false)
        adapter.setPlaying(true)

        assertTrue(adapter.snapshot().ended)
        assertTrue(adapter.snapshot().hasRenderedFrame)
        assertFalse(adapter.snapshot().isPlaying)
        assertEquals(4_000, adapter.snapshot().positionMs)
        assertTrue(backend.seekPositions.isEmpty())
    }

    @Test
    fun newLoadClearsFirstFrameAndRestoresItsOwnPositionAndMuteIntent() {
        backend.listeners.toList().forEach(Player.Listener::onRenderedFirstFrame)
        backend.emit(Player.EVENT_RENDERED_FIRST_FRAME)
        assertTrue(adapter.snapshot().hasRenderedFrame)

        adapter.load(uri, 2_000, playWhenReady = false, muted = false)

        assertFalse(adapter.snapshot().hasRenderedFrame)
        assertFalse(adapter.snapshot().playWhenReady)
        assertEquals(2_000, adapter.snapshot().positionMs)
        assertEquals(1f, backend.volume, 0f)
    }

    @Test
    fun bufferingPauseAndSystemAudioPauseRemainVisibleInSnapshot() {
        adapter.load(uri, 0, playWhenReady = true, muted = false)
        backend.playbackState = Player.STATE_BUFFERING
        assertTrue(adapter.snapshot().playWhenReady)
        assertFalse(adapter.snapshot().isPlaying)

        adapter.setPlaying(false)
        backend.playbackState = Player.STATE_READY
        assertFalse(adapter.snapshot().playWhenReady)
        assertFalse(adapter.snapshot().isPlaying)
        adapter.setPlaying(true)
        assertTrue(adapter.snapshot().isPlaying)

        var changes = 0
        adapter.onChanged = { changes++ }
        backend.playWhenReady = false
        backend.emit(Player.EVENT_PLAY_WHEN_READY_CHANGED)

        assertEquals(1, changes)
        assertFalse(adapter.snapshot().playWhenReady)
        assertFalse(adapter.snapshot().isPlaying)
    }

    @Test
    fun seekIgnoresUnseekableAndClampsKnownDurationWithoutResuming() {
        adapter.load(uri, 0, playWhenReady = false, muted = true)
        adapter.seekTo(50)
        assertTrue(backend.seekPositions.isEmpty())
        backend.seekable = true
        backend.durationMs = 1_000

        adapter.seekTo(-100)
        adapter.seekTo(5_000)

        assertEquals(listOf(0L, 1_000L), backend.seekPositions)
        assertEquals(1_000, adapter.snapshot().positionMs)
        assertFalse(adapter.snapshot().playWhenReady)

        backend.durationMs = C.TIME_UNSET
        adapter.seekTo(8_000)
        assertEquals(8_000, adapter.snapshot().positionMs)
        assertEquals(0, adapter.snapshot().durationMs)
    }

    @Test
    fun errorAndRetryRetainUriPositionMuteAndPausedIntent() {
        adapter.load(uri, 750, playWhenReady = false, muted = true)
        backend.error = PlaybackException(
            "unreadable provider",
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        backend.playbackState = Player.STATE_IDLE
        var changes = 0
        adapter.onChanged = { changes++ }
        backend.emit(Player.EVENT_PLAYER_ERROR)

        assertTrue(adapter.snapshot().failed)
        assertEquals(1, changes)

        adapter.retry()

        assertEquals(2, backend.prepareCount)
        assertEquals(uri, backend.mediaItem?.localConfiguration?.uri)
        assertEquals(750, adapter.snapshot().positionMs)
        assertFalse(adapter.snapshot().failed)
        assertFalse(adapter.snapshot().playWhenReady)
        assertEquals(0f, backend.volume, 0f)
        adapter.retry()
        assertEquals(2, backend.prepareCount)
    }

    @Test
    fun releaseDetachesSurfaceBeforeDecoderOnceAndIgnoresAllLateCallbacks() {
        val parent = FrameLayout(context).apply { addView(playerView) }
        adapter.load(uri, 123, playWhenReady = true, muted = true)
        backend.playbackState = Player.STATE_READY
        val lateListeners = backend.listeners.toList()
        var changes = 0
        adapter.onChanged = { changes++ }
        backend.operations.clear()

        adapter.release()
        adapter.release()
        backend.emit(Player.EVENT_PLAYBACK_STATE_CHANGED, listeners = lateListeners)
        lateListeners.forEach(Player.Listener::onRenderedFirstFrame)
        adapter.onChanged = { changes++ }
        adapter.setPlaying(true)
        adapter.setMuted(false)
        adapter.seekTo(500)
        adapter.retry()
        adapter.load(uri, 0, playWhenReady = true, muted = false)

        assertEquals(1, backend.releaseCount)
        assertTrue(backend.listeners.isEmpty())
        assertNull(playerView.player)
        assertSame(parent, playerView.parent)
        assertTrue(backend.operations.indexOf("detach") < backend.operations.indexOf("release"))
        assertEquals(0, changes)
        assertNull(adapter.onChanged)
        assertFalse(adapter.snapshot().hasRenderedFrame)
        assertFalse(adapter.snapshot().playWhenReady)
        assertFalse(adapter.snapshot().isPlaying)
        assertEquals(123, adapter.snapshot().positionMs)
    }

    @Test
    fun mutedAutoplayDisablesFocusAndUnmutedLoadEnablesItBeforePlayback() {
        useExoPlayerBackend()

        adapter.load(uri, 0, playWhenReady = true, muted = true)

        assertEquals(
            listOf("play:false", "volume:0.0", "focus:false", "media", "play:true", "prepare"),
            backend.operations,
        )
        assertEquals(listOf(false), backend.focusChanges)
        assertTrue(adapter.snapshot().playWhenReady)

        backend.operations.clear()
        adapter.load(uri, 10, playWhenReady = true, muted = false)

        assertEquals(
            listOf("play:false", "focus:true", "volume:1.0", "media", "play:true", "prepare"),
            backend.operations,
        )
        assertEquals(listOf(false, true), backend.focusChanges)
        assertSame(backend.initialAudioAttributes, backend.audioAttributes)
        assertEquals(C.USAGE_MEDIA, backend.audioAttributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, backend.audioAttributes.contentType)
    }

    @Test
    fun muteSilencesBeforeAbandoningFocusAndUnmuteEnablesFocusBeforeAudio() {
        useExoPlayerBackend()
        adapter.load(uri, 0, playWhenReady = true, muted = true)
        backend.operations.clear()

        adapter.setMuted(false)
        adapter.setMuted(true)

        assertEquals(
            listOf("focus:true", "volume:1.0", "volume:0.0", "focus:false"),
            backend.operations,
        )
        assertEquals(listOf(false, true, false), backend.focusChanges)
        assertTrue(adapter.snapshot().playWhenReady)
        assertEquals(0f, backend.volume, 0f)
        assertSame(backend.initialAudioAttributes, backend.audioAttributes)

        adapter.release()
        backend.operations.clear()
        adapter.setMuted(false)
        assertTrue(backend.operations.isEmpty())
    }

    private fun useExoPlayerBackend() {
        adapter.release()
        backend = RecordingPlayer(exoPlayer = true)
        adapter = Media3AlbumVideoPlayer(playerView, backend.player)
        backend.operations.clear()
    }

    /**
     * A strict proxy for the small Player surface consumed by this adapter and its rendering view.
     * Unexpected Player calls fail rather than silently providing a misleading default.
     */
    private class RecordingPlayer(exoPlayer: Boolean = false) : InvocationHandler {
        val listeners = mutableListOf<Player.Listener>()
        val operations = mutableListOf<String>()
        val seekPositions = mutableListOf<Long>()
        var mediaItem: MediaItem? = null
        var positionMs = 0L
        var durationMs = C.TIME_UNSET
        var playWhenReady = false
        var volume = 1f
        var playbackState = Player.STATE_IDLE
        var seekable = false
        var error: PlaybackException? = null
        var repeatMode = Player.REPEAT_MODE_ALL
        var prepareCount = 0
        var releaseCount = 0
        val focusChanges = mutableListOf<Boolean>()
        val initialAudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        var audioAttributes = initialAudioAttributes
        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(if (exoPlayer) ExoPlayer::class.java else Player::class.java),
            this,
        ) as Player

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val arguments = args.orEmpty()
            return when (method.name) {
                "equals" -> proxy === arguments[0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RecordingPlayer"
                "getApplicationLooper" -> Looper.getMainLooper()
                "getPlaybackState" -> playbackState
                "getPlayWhenReady" -> playWhenReady
                "getVolume" -> volume
                "getAudioAttributes" -> audioAttributes
                "setAudioAttributes" -> {
                    audioAttributes = arguments[0] as AudioAttributes
                    val handleAudioFocus = arguments[1] as Boolean
                    focusChanges += handleAudioFocus
                    operations += "focus:$handleAudioFocus"
                    null
                }
                "getCurrentPosition" -> positionMs
                "getDuration" -> durationMs
                "getPlayerError" -> error
                "getRepeatMode" -> repeatMode
                "isCurrentMediaItemSeekable" -> seekable
                "isPlayingAd" -> false
                "isPlaying" -> playWhenReady && playbackState == Player.STATE_READY
                "getVideoSize" -> VideoSize.UNKNOWN
                "setImageOutput" -> null
                "isCommandAvailable" -> arguments[0] == Player.COMMAND_SET_VIDEO_SURFACE
                "setRepeatMode" -> {
                    repeatMode = arguments[0] as Int
                    null
                }
                "addListener" -> {
                    listeners += arguments[0] as Player.Listener
                    null
                }
                "removeListener" -> {
                    listeners -= arguments[0] as Player.Listener
                    null
                }
                "setVideoTextureView" -> {
                    operations += "attach"
                    null
                }
                "clearVideoTextureView" -> {
                    operations += "detach"
                    null
                }
                "setPlayWhenReady" -> {
                    playWhenReady = arguments[0] as Boolean
                    operations += "play:$playWhenReady"
                    null
                }
                "setVolume" -> {
                    volume = arguments[0] as Float
                    operations += "volume:$volume"
                    null
                }
                "setMediaItem" -> {
                    mediaItem = arguments[0] as MediaItem
                    positionMs = arguments[1] as Long
                    error = null
                    playbackState = Player.STATE_IDLE
                    operations += "media"
                    null
                }
                "prepare" -> {
                    prepareCount++
                    error = null
                    playbackState = Player.STATE_BUFFERING
                    operations += "prepare"
                    null
                }
                "seekTo" -> {
                    positionMs = arguments[0] as Long
                    seekPositions += positionMs
                    operations += "seek:$positionMs"
                    null
                }
                "release" -> {
                    releaseCount++
                    operations += "release"
                    null
                }
                else -> error("Unexpected Player call: ${method.name}")
            }
        }

        fun emit(vararg events: Int, listeners: List<Player.Listener> = this.listeners.toList()) {
            val flags = FlagSet.Builder().addAll(*events).build()
            listeners.forEach { it.onEvents(player, Player.Events(flags)) }
        }
    }
}
