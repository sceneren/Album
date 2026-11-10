package com.github.sceneren.album.ui.view

import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.viewpager2.widget.ViewPager2
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class PreviewSharedElementTransitionTest {
    @Test
    fun rectangularImagesFitInsideOffsetViewportWithoutCropping() {
        val viewport = RectF(20f, 80f, 1020f, 1680f)
        assertEquals(RectF(20f, 630f, 1020f, 1130f), fittedPreviewBounds(viewport, 1200, 600))
        assertEquals(RectF(120f, 80f, 920f, 1680f), fittedPreviewBounds(viewport, 600, 1200))
        assertEquals(RectF(512f, 80f, 528f, 1680f), fittedPreviewBounds(viewport, 100, 10000))
    }

    @Test
    fun cancellingBeforeFirstFrameRestoresPagerAndChrome() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val context = controller.get()
            val root = FrameLayout(context)
            val pager = ViewPager2(context)
            val toolbar = View(context)
            root.addView(pager)
            root.addView(toolbar)
            val media = AlbumMedia(
                uri = Uri.parse("content://transition/1"), mediaType = AlbumMediaType.IMAGE,
                displayName = null, mimeType = "image/jpeg", sizeBytes = null,
                dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null,
                width = 1200, height = 600, durationMillis = null, bucketId = null,
                bucketName = null, selectedAtEpochMillis = null, source = AlbumMediaSource.MEDIA_STORE,
            )
            val transition = PreviewSharedElementTransition(root, pager, listOf(toolbar),
                PreviewTransitionOrigin(RectF(10f, 20f, 110f, 120f), ColorDrawable()), media, android.graphics.Color.BLACK)
            transition.prepare()
            assertEquals(0f, pager.alpha)
            assertFalse(pager.isUserInputEnabled)
            transition.cancel()
            transition.start()
            transition.cancel()
            assertEquals(1f, pager.alpha)
            assertEquals(1f, toolbar.alpha)
            assertTrue(pager.isUserInputEnabled)
            assertEquals(255, root.background.alpha)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
