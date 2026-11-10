package com.github.sceneren.album.ui.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ScaleDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual native drawable coordinates, not just intrinsic resource dimensions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class VideoProgressGeometryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun progressUpdatesKeepTrackFixedAndThumbInsideItsBackground() {
        val seek = LayoutInflater.from(context).inflate(R.layout.auc_video_progress, null) as SeekBar
        for (width in listOf(120, 300, 600)) {
            seek.measure(exact(width), exact(48))
            seek.layout(0, 0, seek.measuredWidth, seek.measuredHeight)
            val initialBounds = Rect(seek.left, seek.top, seek.right, seek.bottom)
            val layers = seek.progressDrawable as LayerDrawable
            val track = layers.findDrawableByLayerId(android.R.id.background)
            val fill = layers.findDrawableByLayerId(android.R.id.progress)
            val initialTrack = Rect(track.bounds)
            for (progress in listOf(0, 1, 100, 2500, 5000, 7500, 9900, 9999, 10000, 0)) {
                seek.progress = progress
                // Track and thumb share the padding translation; thumb also subtracts its offset.
                val thumb = Rect(seek.thumb.bounds).apply { offset(-seek.thumbOffset, 0) }
                assertEquals(initialBounds, Rect(seek.left, seek.top, seek.right, seek.bottom))
                assertEquals(initialTrack, track.bounds)
                assertTrue("fill must remain inside track at $progress", track.bounds.contains(fill.bounds))
                assertTrue(
                    "thumb must remain inside track at $progress: track=${track.bounds}, thumb=$thumb",
                    track.bounds.contains(thumb),
                )
                val filledShape = checkNotNull((fill as ScaleDrawable).drawable).bounds
                assertTrue("scaled fill outside background at $progress", track.bounds.contains(filledShape))
                assertTrue("fill must cover the thumb through its right edge at $progress", filledShape.right >= thumb.right)
                assertTrue("fill end must align with the thumb at $progress", filledShape.right - thumb.right <= 1)
                assertEquals(track.bounds.centerY(), thumb.centerY())
                if (progress == 0) assertEquals(track.bounds.left, thumb.left)
                if (progress == seek.max) assertEquals(track.bounds.right, thumb.right)
            }
        }
    }

    @Test
    @Config(sdk = [34])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun lowProgressRendersAsOneContinuousCapsuleWithoutASeparateStartingDot() {
        val seek = LayoutInflater.from(context).inflate(R.layout.auc_video_progress, null) as SeekBar
        seek.measure(exact(300), exact(48))
        seek.layout(0, 0, seek.measuredWidth, seek.measuredHeight)
        val foreground = context.getColor(R.color.auc_video_controls_foreground)
        for (progress in listOf(0, 1, 100, 250, 500, 1000, 2500, 5000, 9999, 10000)) {
            seek.progress = progress
            val bitmap = Bitmap.createBitmap(seek.width, seek.height, Bitmap.Config.ARGB_8888)
            try {
                seek.draw(Canvas(bitmap))
                assertEquals("native rendering must contain the foreground capsule", foreground,
                    bitmap.getPixel(seek.paddingLeft + seek.thumb.bounds.centerX(), seek.paddingTop + seek.thumb.bounds.centerY()))
                for (y in 0 until bitmap.height) {
                    val filled = (0 until bitmap.width).filter { x ->
                        val pixel = bitmap.getPixel(x, y)
                        Color.alpha(pixel) == 255 && Color.red(pixel) == Color.red(foreground)
                    }
                    if (filled.isEmpty()) continue
                    for (x in filled.first()..filled.last()) {
                        assertEquals("Disconnected fill at progress=$progress pixel=($x,$y)", foreground, bitmap.getPixel(x, y))
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
