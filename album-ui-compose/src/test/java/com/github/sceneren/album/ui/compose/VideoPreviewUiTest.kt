package com.github.sceneren.album.ui.compose

import android.net.Uri
import android.graphics.Rect
import android.graphics.drawable.LayerDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerSessionSnapshot
import com.github.sceneren.album.video.AlbumVideoPlayerFactory
import com.github.sceneren.album.ui.compose.internal.video.VideoPreviewSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoPreviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val video = media(1, AlbumMediaType.VIDEO)
    private val image = media(2, AlbumMediaType.IMAGE)
    private val selection = AlbumPickerSessionSnapshot(
        sessionId = "video-test",
        selectedItems = emptyList(),
        cameraItems = emptyList(),
        selectedUris = emptySet(),
        bucketId = AlbumDirectory.ALL_BUCKET_ID,
        previewUri = null,
        hasPendingCamera = false,
    )

    @After fun resetFactory() { AlbumUi.setVideoPlayerFactory(null) }

    @Test fun optionalFactoryCanBeRegisteredAndCleared() {
        AlbumUi.setVideoPlayerFactory(null)
        assertNull(AlbumUi.videoPlayerFactoryOrNull())
        val factory = AlbumVideoPlayerFactory { FakeComposeVideoPlayer() }
        AlbumUi.setVideoPlayerFactory(factory)
        assertSame(factory, AlbumUi.videoPlayerFactoryOrNull())
        AlbumUi.setVideoPlayerFactory(null)
        assertNull(AlbumUi.videoPlayerFactoryOrNull())
    }

    @Test fun absentFactoryShowsOnlyStaticCoverAndOriginalChrome() {
        val targets = mutableListOf<AlbumImageTarget>()
        var toggleCount = 0
        compose.setContent {
            MaterialTheme {
                AlbumPreviewScreen(
                    embedded = true,
                    systemBarStyle = null,
                    state = preview(listOf(video)),
                    session = selection,
                    appearance = AlbumPickerAppearance(),
                    imageLoader = { _, target -> targets += target; ColorPainter(Color.DarkGray) },
                    videoPlayerFactory = null,
                    onToggle = { toggleCount++ },
                    onLoadMore = {},
                    onClose = {},
                    onConfirm = {},
                )
            }
        }
        compose.onNodeWithText("1/1").assertExists()
        compose.onNodeWithContentDescription("切换选择状态").performClick()
        compose.onNodeWithContentDescription("暂停视频").assertDoesNotExist()
        compose.onNodeWithContentDescription("取消静音").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(targets.contains(AlbumImageTarget.VIDEO_COVER))
            assertEquals(1, toggleCount)
        }
    }

    @Test fun customEngineGetsCapsuleControlsAndBackReleasesIt() {
        val showing = mutableStateOf(true)
        val players = mutableListOf<FakeComposeVideoPlayer>()
        val factory = AlbumVideoPlayerFactory { FakeComposeVideoPlayer().also(players::add) }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    if (showing.value) {
                        AlbumPreviewScreen(
                            embedded = true,
                            systemBarStyle = null,
                            state = preview(listOf(video)),
                            session = selection,
                            appearance = AlbumPickerAppearance(),
                            imageLoader = { _, _ -> ColorPainter(Color.DarkGray) },
                            videoPlayerFactory = factory,
                            onToggle = {},
                            onLoadMore = {},
                            onClose = { showing.value = false },
                            onConfirm = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("暂停视频").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("播放视频").assertExists()
        compose.onNodeWithContentDescription("取消静音").performClick()
        compose.onNodeWithContentDescription("静音").assertExists()
        compose.onNodeWithContentDescription("切换选择状态").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, players.size)
            assertFalse(players.single().muteEnabled)
            assertFalse(players.single().state.playWhenReady)
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertTrue(players.single().released) }
    }

    @Test fun prefetchedVideosDoNotPlayAndImagePageNeverCreatesAnEngine() {
        val players = mutableListOf<FakeComposeVideoPlayer>()
        val factory = AlbumVideoPlayerFactory {
            assertTrue(players.all { it.released })
            FakeComposeVideoPlayer().also(players::add)
        }
        compose.setContent {
            MaterialTheme {
                AlbumPreviewScreen(
                    embedded = true,
                    systemBarStyle = null,
                    state = preview(listOf(video, image, media(3, AlbumMediaType.VIDEO))),
                    session = selection,
                    appearance = AlbumPickerAppearance(),
                    imageLoader = { _, _ -> ColorPainter(Color.DarkGray) },
                    videoPlayerFactory = factory,
                    onToggle = {},
                    onLoadMore = {},
                    onClose = {},
                    onConfirm = {},
                )
            }
        }
        compose.runOnIdle { assertEquals(1, players.size) }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithText("2/3").assertExists()
        compose.runOnIdle {
            assertTrue(players.first().released)
            assertEquals(1, players.size)
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("暂停视频").assertExists()
        compose.runOnIdle { assertEquals(2, players.size) }
    }

    @Test fun playbackProgressUpdatesKeepNativeControlAndTrackInTheSamePlace() {
        val player = FakeComposeVideoPlayer()
        val session = VideoPreviewSession { player }
        lateinit var root: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            MaterialTheme { AlbumVideoControls(session) }
        }
        compose.runOnIdle {
            session.setForeground(true)
            session.show(video.uri)
        }
        compose.waitForIdle()
        lateinit var originalSeek: SeekBar
        val originalPosition = IntArray(2)
        val originalSize = Rect()
        val originalTrack = Rect()
        compose.runOnIdle {
            originalSeek = checkNotNull(findSeek(root))
            originalSeek.getLocationOnScreen(originalPosition)
            originalSize.set(0, 0, originalSeek.width, originalSeek.height)
            originalTrack.set((originalSeek.progressDrawable as LayerDrawable)
                .findDrawableByLayerId(android.R.id.background).bounds)
        }
        for (position in listOf(0L, 1L, 7_500L, 15_000L, 22_500L, 29_999L, 30_000L, 0L)) {
            compose.runOnIdle { player.emit(player.state.copy(positionMs = position)) }
            compose.waitForIdle()
            compose.runOnIdle {
                val seek = checkNotNull(findSeek(root))
                val location = IntArray(2).also(seek::getLocationOnScreen)
                val track = (seek.progressDrawable as LayerDrawable)
                    .findDrawableByLayerId(android.R.id.background).bounds
                val thumb = Rect(seek.thumb.bounds).apply { offset(-seek.thumbOffset, 0) }
                assertSame(originalSeek, seek)
                assertEquals(originalPosition.toList(), location.toList())
                assertEquals(originalSize, Rect(0, 0, seek.width, seek.height))
                assertEquals(originalTrack, track)
                assertTrue("thumb outside track at $position", track.contains(thumb))
                assertEquals((position.toDouble() / player.state.durationMs * seek.max).toInt(), seek.progress)
            }
        }
        compose.runOnIdle { session.close() }
    }

    @Test fun replacingPlaybackSessionRebindsNativeSeekListeners() {
        val firstPlayer = FakeComposeVideoPlayer()
        val secondPlayer = FakeComposeVideoPlayer()
        val first = VideoPreviewSession { firstPlayer }
        val second = VideoPreviewSession { secondPlayer }
        val active = mutableStateOf(first)
        lateinit var root: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            MaterialTheme { AlbumVideoControls(active.value) }
        }
        compose.runOnIdle {
            first.setForeground(true)
            first.show(video.uri)
        }
        compose.waitForIdle()
        var oldSeek: SeekBar? = null
        compose.runOnIdle {
            oldSeek = checkNotNull(findSeek(root))
            first.close()
            second.setForeground(true)
            second.show(video.uri)
            active.value = second
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val newSeek = checkNotNull(findSeek(root))
            assertFalse(oldSeek === newSeek)
            assertEquals(newSeek.resources.getDimensionPixelSize(R.dimen.auc_video_progress_thumb_width), newSeek.thumb.intrinsicWidth)
            newSeek.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals(0, firstPlayer.seeks)
            assertTrue(secondPlayer.seeks > 0)
            second.close()
        }
    }

    private fun findSeek(view: View): SeekBar? {
        if (view is SeekBar) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findSeek(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun preview(items: List<AlbumMedia>) = PreviewState(
        id = 1, items = items, initialIndex = 0, nextOffset = null, endReached = true,
    )

    private fun media(id: Int, type: AlbumMediaType) = AlbumMedia(
        uri = Uri.parse("content://media/external/file/$id"),
        mediaType = type,
        displayName = "preview-$id",
        mimeType = null,
        sizeBytes = null,
        dateAddedEpochSeconds = null,
        dateModifiedEpochSeconds = null,
        width = null,
        height = null,
        durationMillis = null,
        bucketId = null,
        bucketName = null,
        selectedAtEpochMillis = null,
        source = AlbumMediaSource.MEDIA_STORE,
    )
}
