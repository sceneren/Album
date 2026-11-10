package com.github.sceneren.album.ui.compose

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewSharedElementUiTest {
    @get:Rule val compose = createComposeRule()
    private var preview by mutableStateOf(false)
    private lateinit var sharedScope: SharedTransitionScope
    private val media = AlbumMedia(
        uri = Uri.parse("content://shared-preview/1"), mediaType = AlbumMediaType.IMAGE,
        displayName = "rectangle", mimeType = "image/png", sizeBytes = null,
        dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null,
        width = 1200, height = 600, durationMillis = null, bucketId = null,
        bucketName = null, selectedAtEpochMillis = null, source = AlbumMediaSource.MEDIA_STORE,
    )
    private val secondMedia = media.copy(uri = Uri.parse("content://shared-preview/2"))
    private var currentMedia by mutableStateOf(media)

    @Test
    fun imageMatchesGridOnEntryAndReturnsOnExit() {
        show()
        compose.runOnIdle { preview = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertTrue(sharedScope.isTransitionActive) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertFalse(sharedScope.isTransitionActive) }
        compose.onNodeWithContentDescription("rectangle").assertExists()
        compose.runOnIdle { preview = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertTrue(sharedScope.isTransitionActive) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertFalse(sharedScope.isTransitionActive) }
        compose.onNodeWithContentDescription("rectangle").assertDoesNotExist()
    }

    @Test
    fun closingDuringEntryDoesNotLeaveAnActiveTransition() {
        show()
        compose.runOnIdle { preview = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { preview = false }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertFalse(sharedScope.isTransitionActive) }
        compose.onNodeWithContentDescription("rectangle").assertDoesNotExist()
    }

    @Test
    fun pagingDoesNotStartAnotherTransitionFromTheCoveredGrid() {
        show()
        compose.runOnIdle { preview = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { currentMedia = secondMedia }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertFalse(sharedScope.isTransitionActive) }
    }

    private fun show() {
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        val painter = BitmapPainter(bitmap.asImageBitmap())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                val scope = this
                SideEffect { sharedScope = scope }
                Image(painter, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(100.dp).sharedAlbumThumbnail(media, scope, !preview))
                Image(painter, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.offset(x = 120.dp).size(100.dp)
                        .sharedAlbumThumbnail(secondMedia, scope, !preview))
                AnimatedContent(preview, transitionSpec = {
                    fadeIn(tween(PREVIEW_TRANSITION_MILLIS)) togetherWith fadeOut(tween(PREVIEW_TRANSITION_MILLIS))
                }) { visible ->
                    if (visible) key(currentMedia.uri) {
                        AlbumPreviewImage(currentMedia, painter, scope, this@AnimatedContent)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }
}
