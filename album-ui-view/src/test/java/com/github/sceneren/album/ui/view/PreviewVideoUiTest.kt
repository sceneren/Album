package com.github.sceneren.album.ui.view

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.ui.view.internal.video.PreviewVideoOutput
import com.github.sceneren.album.video.AlbumVideoPlayerFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class PreviewVideoUiTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val video = media(1, AlbumMediaType.VIDEO)
    private val image = media(2, AlbumMediaType.IMAGE)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun failedPreviewAppendStopsSilentlyAndKeepsLoadedImages() = runTest {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var attempts = 0
        val preview = AlbumPreviewDialog(
            activity = controller.get(), appearance = AlbumPickerAppearance(),
            imageLoader = AlbumImageLoader { _, _, _ -> }, scope = this,
            initialItems = listOf(image), initialIndex = 0, initialSelectedUris = emptySet(), nextOffset = 1,
            onToggleSelection = {}, onConfirm = {}, onDismiss = {},
            loadMore = { _, _ -> attempts++; Result.failure(IllegalStateException("Provider unavailable")) },
        )
        try {
            org.robolectric.shadows.ShadowToast.reset()
            preview.show()
            preview.maybeLoadMore(0)
            runCurrent()
            preview.maybeLoadMore(0)
            runCurrent()
            assertEquals(1, attempts)
            assertNull(org.robolectric.shadows.ShadowToast.getLatestToast())
            val pager = dialogRoot().findViewById<ViewPager2>(R.id.auv_preview_pager)
            assertEquals(1, pager.adapter!!.itemCount)
        } finally {
            preview.dismiss()
            controller.pause().stop().destroy()
        }
    }

    @After
    fun resetFactory() {
        AlbumUi.setVideoPlayerFactory(null)
    }

    @Test
    fun factoryRegistrationIsOptionalAndCanBeReset() {
        AlbumUi.setVideoPlayerFactory(null)
        assertNull(AlbumUi.videoPlayerFactoryOrNull())
        val factory = AlbumVideoPlayerFactory { FakeVideoPlayer(it) }
        AlbumUi.setVideoPlayerFactory(factory)
        assertSame(factory, AlbumUi.videoPlayerFactoryOrNull())
        AlbumUi.setVideoPlayerFactory(null)
        assertNull(AlbumUi.videoPlayerFactoryOrNull())
    }

    @Test
    fun missingFactoryKeepsStaticVideoCoverAndOriginalChromeWithoutControls() {
        AlbumUi.setVideoPlayerFactory(null)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val preview = preview(activity, listOf(video))
        preview.show()
        val root = dialogRoot()
        assertEquals(View.GONE, root.findViewById<View>(R.id.auv_preview_video_controls).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.auv_preview_video_back).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.auv_preview_toolbar).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.auv_preview_bottom).visibility)

        val loadedTargets = mutableListOf<AlbumImageTarget>()
        val holder = holder(AlbumImageLoader { _, _, target -> loadedTargets += target })
        holder.bind(video)
        assertEquals(listOf(AlbumImageTarget.VIDEO_COVER), loadedTargets)
        assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.auv_preview_video_cover).visibility)
        val staticIcon = holder.itemView.findViewById<View>(R.id.auv_preview_video_static_play)
        assertEquals(View.VISIBLE, staticIcon.visibility)
        assertFalse(staticIcon.isClickable)
        assertEquals(0, holder.itemView.findViewById<ViewGroup>(R.id.auv_preview_video_surface).childCount)
        preview.dismiss()
        controller.pause().stop().destroy()
    }

    @Test
    fun configuredFactoryUsesFloatingBackAndCapsuleAndIsCapturedAtPreviewCreation() {
        val players = mutableListOf<FakeVideoPlayer>()
        AlbumUi.setVideoPlayerFactory(AlbumVideoPlayerFactory { FakeVideoPlayer(it).also(players::add) })
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val preview = preview(activity, listOf(video))
        // This change affects only future previews, not the one already constructed.
        AlbumUi.setVideoPlayerFactory(null)
        preview.show()
        val root = dialogRoot()
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.auv_preview_video_controls).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.auv_preview_video_back).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.auv_preview_toolbar).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.auv_preview_bottom).visibility)

        val pager = root.findViewById<ViewPager2>(R.id.auv_preview_pager)
        val adapter = pager.adapter as PreviewAdapter
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        assertEquals(1, players.size)
        val active = players.single()
        assertSame(active.view, holder.itemView.findViewById<ViewGroup>(R.id.auv_preview_video_surface).getChildAt(0))
        controller.pause()
        assertTrue(active.released)
        assertEquals(0, holder.itemView.findViewById<ViewGroup>(R.id.auv_preview_video_surface).childCount)
        controller.resume()
        assertEquals(2, players.size)
        preview.dismiss()
        assertTrue(players.last().released)
        adapter.onViewDetachedFromWindow(holder)
        controller.pause().stop().destroy()
    }

    @Test
    fun imagePageKeepsItsOriginalLoaderAndNeverCreatesVideoPlayer() {
        var creations = 0
        AlbumUi.setVideoPlayerFactory(AlbumVideoPlayerFactory { creations++; FakeVideoPlayer(it) })
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val preview = preview(controller.get(), listOf(image))
        preview.show()
        val root = dialogRoot()
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.auv_preview_toolbar).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.auv_preview_bottom).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.auv_preview_video_controls).visibility)
        assertEquals(0, creations)

        val loadedTargets = mutableListOf<AlbumImageTarget>()
        val holder = holder(AlbumImageLoader { _, _, target -> loadedTargets += target }, enabled = true)
        holder.bind(image)
        assertEquals(listOf(AlbumImageTarget.PREVIEW_IMAGE), loadedTargets)
        assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.auv_preview_zoom_image).visibility)
        assertEquals(View.GONE, holder.itemView.findViewById<View>(R.id.auv_preview_video_cover).visibility)
        preview.dismiss()
        controller.pause().stop().destroy()
    }

    @Test
    fun adjacentPreloadedHoldersNeverBecomePlaybackOutputAndRecycleDetaches() {
        val outputs = mutableListOf<PreviewVideoOutput?>()
        val adapter = PreviewAdapter(
            AlbumPickerAppearance(),
            AlbumImageLoader { _, _, _ -> },
            listOf(video, media(3, AlbumMediaType.VIDEO)),
            videoPlaybackEnabled = true,
            onVideoOutputChanged = { outputs += it },
        )
        val parent = FrameLayout(context)
        val first = adapter.onCreateViewHolder(parent, 0)
        val adjacent = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(first, 0)
        adapter.onBindViewHolder(adjacent, 1)
        adapter.selectPosition(0)
        adapter.onViewAttachedToWindow(adjacent)
        assertTrue(outputs.isEmpty())
        adapter.onViewAttachedToWindow(first)
        assertEquals(listOf(first), outputs)
        adapter.onViewRecycled(first)
        assertNull(outputs.last())
        adapter.selectPosition(1)
        assertSame(adjacent, outputs.last())
    }

    @Test
    fun capsuleUsesExactResourceGeometryAndSafetyInsetsDoNotAccumulate() {
        AlbumUi.setVideoPlayerFactory(AlbumVideoPlayerFactory { FakeVideoPlayer(it) })
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val preview = preview(controller.get(), listOf(video))
        preview.show()
        val root = dialogRoot()
        val controls = root.findViewById<View>(R.id.auv_preview_video_controls)
        val play = root.findViewById<ImageButton>(R.id.auv_preview_video_play)
        val mute = root.findViewById<ImageButton>(R.id.auv_preview_video_mute)
        val seek = root.findViewById<SeekBar>(R.id.auv_preview_video_progress)
        assertEquals(dp(48), controls.layoutParams.height)
        assertEquals(dp(48), play.layoutParams.width)
        assertEquals(dp(48), mute.layoutParams.width)
        assertEquals(dp(12), play.paddingLeft)
        assertEquals(dp(12), mute.paddingLeft)
        assertEquals(dp(18), seek.thumb.intrinsicWidth)
        assertEquals(dp(6), seek.thumb.intrinsicHeight)
        assertEquals(dp(6), seek.progressDrawable.intrinsicHeight)
        assertEquals(Color.parseColor("#D9222222"), context.getColor(R.color.auv_video_controls_background))
        assertEquals(Color.parseColor("#FFAAAAAA"), context.getColor(R.color.auv_video_controls_foreground))
        assertEquals(Color.parseColor("#FF4D4D4D"), context.getColor(R.color.auv_video_progress_unplayed))
        val safeInsets = Insets.of(11, 23, 17, 31)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), safeInsets)
            .build()
        repeat(2) { ViewCompat.dispatchApplyWindowInsets(root, insets) }
        val margins = controls.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(dp(24) + 11, margins.leftMargin)
        assertEquals(dp(24) + 17, margins.rightMargin)
        assertEquals(dp(24) + 31, margins.bottomMargin)
        preview.dismiss()
        controller.pause().stop().destroy()
    }

    @Test
    fun seekGestureDisallowsParentInterceptionFromTouchDownToRelease() {
        val root = LayoutInflater.from(context).inflate(R.layout.auv_dialog_album_preview, null)
        val playback = com.github.sceneren.album.ui.view.internal.video.PreviewVideoPlayback(
            { FakeVideoPlayer(context) },
            {},
        )
        com.github.sceneren.album.ui.view.internal.video.PreviewVideoControls(root, playback)
        val seek = root.findViewById<SeekBar>(R.id.auv_preview_video_progress)
        val controls = seek.parent as ViewGroup
        controls.removeView(seek)
        val parent = InterceptionParent(context)
        parent.addView(seek)
        seek.layout(0, 0, dp(200), dp(48))
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 20f, 20f, 0)
        try {
            seek.dispatchTouchEvent(down)
            assertTrue(parent.disallowed)
            seek.dispatchTouchEvent(up)
            assertFalse(parent.disallowed)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun preview(activity: ComponentActivity, items: List<AlbumMedia>) = AlbumPreviewDialog(
        activity = activity,
        appearance = AlbumPickerAppearance(),
        imageLoader = AlbumImageLoader { _, _, _ -> },
        scope = TestScope(),
        initialItems = items,
        initialIndex = 0,
        initialSelectedUris = emptySet(),
        nextOffset = null,
        onToggleSelection = {},
        onConfirm = {},
        onDismiss = {},
        loadMore = { _, _ -> Result.success(emptyList()) },
    )

    private fun dialogRoot(): View {
        return ShadowDialog.getLatestDialog().findViewById(R.id.auv_preview_root)
    }

    private fun holder(loader: AlbumImageLoader, enabled: Boolean = false) = PreviewHolder(
        LayoutInflater.from(context).inflate(R.layout.auv_item_album_preview_page, FrameLayout(context), false),
        AlbumPickerAppearance(),
        loader,
        enabled,
    )

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    private fun media(id: Int, type: AlbumMediaType) = AlbumMedia(
        uri = Uri.parse("content://media/external/file/$id"),
        mediaType = type,
        displayName = null,
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

    private class InterceptionParent(context: Context) : FrameLayout(context) {
        var disallowed = false
        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            disallowed = disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }
}
