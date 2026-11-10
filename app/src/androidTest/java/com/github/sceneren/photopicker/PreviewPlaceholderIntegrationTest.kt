package com.github.sceneren.photopicker

import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.ui.view.AlbumImageLoader
import com.github.sceneren.album.ui.view.AlbumImageTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic slow-original checks against the host's actual painter implementation. */
@RunWith(AndroidJUnit4::class)
class PreviewPlaceholderIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val item = media(1)
    private lateinit var loader: ImageLoader
    private lateinit var viewOutput: ImageView
    private lateinit var viewLoader: AlbumImageLoader

    @After fun closeLoader() { if (::loader.isInitialized) loader.shutdown() }

    @Test fun cachedThumbnailIsVisibleBeforeOriginalThenReplaced() {
        val release = CompletableDeferred<Unit>()
        loader = createTestLoader(release)
        seed(item, android.graphics.Color.GREEN)
        show(mutableStateOf(item))
        awaitColor(Color.Green)
        release.complete(Unit)
        awaitColor(Color.Red)
    }

    @Test fun evictedThumbnailLoadsSmallFallbackWithoutWaitingForOriginal() {
        val release = CompletableDeferred<Unit>()
        loader = createTestLoader(release)
        show(mutableStateOf(item))
        awaitColor(Color.Green)
        release.complete(Unit)
        awaitColor(Color.Red)
    }

    @Test fun failedOriginalKeepsThumbnailVisible() {
        val release = CompletableDeferred<Unit>()
        loader = createTestLoader(release, fail = true)
        seed(item, android.graphics.Color.GREEN)
        show(mutableStateOf(item))
        awaitColor(Color.Green)
        release.complete(Unit)
        compose.waitForIdle()
        awaitColor(Color.Green)
    }

    @Test fun uncachedThumbnailSurvivesOriginalFailure() {
        val release = CompletableDeferred<Unit>()
        loader = createTestLoader(release, fail = true)
        show(mutableStateOf(item))
        awaitColor(Color.Green)
        release.complete(Unit)
        compose.waitForIdle()
        awaitColor(Color.Green)
    }

    @Test fun switchingMediaNeverShowsPreviousThumbnailOrLateOriginal() {
        val release = CompletableDeferred<Unit>()
        loader = createTestLoader(release)
        val current = mutableStateOf(item)
        val next = media(2)
        seed(item, android.graphics.Color.GREEN)
        seed(next, android.graphics.Color.BLUE)
        show(current)
        awaitColor(Color.Green)
        compose.runOnIdle { current.value = next }
        awaitColor(Color.Blue)
        release.complete(Unit)
        awaitColor(Color.Red)
    }

    @Test fun viewOriginalDoesNotWaitForSlowMissingThumbnail() {
        val releaseThumbnail = CompletableDeferred<Unit>()
        loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                val isThumbnail = chain.request.memoryCacheKey == chain.request.placeholderMemoryCacheKey?.key
                if (isThumbnail) releaseThumbnail.await()
                val color = if (isThumbnail) android.graphics.Color.GREEN else android.graphics.Color.RED
                SuccessResult(bitmap(color, 256).asImage(), chain.request)
            })
        }.build()
        showView()
        compose.runOnIdle { viewLoader.load(viewOutput, item, AlbumImageTarget.PREVIEW_IMAGE) }
        awaitColor(Color.Red)
        // A late thumbnail must not overwrite the already displayed original.
        releaseThumbnail.complete(Unit)
        compose.waitForIdle()
        awaitColor(Color.Red)
    }

    @Test fun viewUncachedThumbnailIsShownWhileOriginalLoadsThenPromoted() {
        val release = CompletableDeferred<Unit>()
        loader = createTestLoader(release)
        showView()
        compose.runOnIdle { viewLoader.load(viewOutput, item, AlbumImageTarget.PREVIEW_IMAGE) }
        awaitColor(Color.Green)
        release.complete(Unit)
        awaitColor(Color.Red)
    }

    @Test fun viewRebindAndClearCancelBothPendingRequests() {
        val releaseFirst = CompletableDeferred<Unit>()
        loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                if (chain.request.data.toString() == item.uri.toString()) releaseFirst.await()
                SuccessResult(bitmap(android.graphics.Color.RED, 256).asImage(), chain.request)
            })
        }.build()
        showView()
        compose.runOnIdle { viewLoader.load(viewOutput, item, AlbumImageTarget.PREVIEW_IMAGE) }
        val next = media(2)
        seed(next, android.graphics.Color.BLUE)
        compose.runOnIdle { viewLoader.load(viewOutput, next, AlbumImageTarget.PREVIEW_IMAGE) }
        awaitColor(Color.Red)
        compose.runOnIdle { viewLoader.clear(viewOutput) }
        awaitColor(Color.Black)
        releaseFirst.complete(Unit)
        compose.waitForIdle()
        awaitColor(Color.Black)
    }

    @Test fun viewRebindAndReattachStillCancelThumbnailsOnLifecycleStop() {
        val thumbnailStarts = AtomicInteger()
        val thumbnailCancels = AtomicInteger()
        loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                if (chain.request.memoryCacheKey == chain.request.placeholderMemoryCacheKey?.key) {
                    thumbnailStarts.incrementAndGet()
                    try { awaitCancellation() } finally { thumbnailCancels.incrementAndGet() }
                } else {
                    awaitCancellation()
                }
            })
        }.build()
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        showView()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.RESUMED
            viewOutput.setViewTreeLifecycleOwner(owner)
            viewLoader.load(viewOutput, item, AlbumImageTarget.PREVIEW_IMAGE)
        }
        compose.waitUntil(5_000) { thumbnailStarts.get() >= 1 }
        compose.runOnIdle { viewLoader.load(viewOutput, media(2), AlbumImageTarget.PREVIEW_IMAGE) }
        compose.waitUntil(5_000) { thumbnailStarts.get() >= 2 }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(5_000) { thumbnailCancels.get() == thumbnailStarts.get() }
        val beforeRestart = thumbnailStarts.get()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(5_000) { thumbnailStarts.get() > beforeRestart }
        val beforeAttach = thumbnailStarts.get()
        compose.runOnIdle {
            val parent = viewOutput.parent as ViewGroup
            val index = parent.indexOfChild(viewOutput)
            val params = viewOutput.layoutParams
            parent.removeView(viewOutput)
            parent.addView(viewOutput, index, params)
        }
        compose.waitUntil(5_000) { thumbnailStarts.get() > beforeAttach }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(5_000) { thumbnailCancels.get() == thumbnailStarts.get() }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.DESTROYED }
    }

    @Test fun composePreviewReplacesSquareThumbnailWithCompleteRectangle() {
        withRectangleFixture { rectangle ->
            loader = ImageLoader.Builder(context).build()
            seed(rectangle, android.graphics.Color.GREEN)
            show(mutableStateOf(rectangle))
            awaitColor(Color.Red)
            assertRectangleEdges()
        }
    }

    @Test fun viewPreviewReplacesSquareThumbnailWithCompleteRectangle() {
        withRectangleFixture { rectangle ->
            loader = ImageLoader.Builder(context).build()
            seed(rectangle, android.graphics.Color.GREEN)
            showView()
            compose.runOnIdle { viewLoader.loadPreview(viewOutput, rectangle) }
            awaitColor(Color.Red)
            assertRectangleEdges()
        }
    }

    private fun assertRectangleEdges() {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val width = minOf(pixels.width.toFloat(), pixels.height * 2f)
        val left = (pixels.width - width) / 2
        assertEquals(Color.Green, pixels[(left + width * 0.05f).toInt(), pixels.height / 2])
        assertEquals(Color.Blue, pixels[(left + width * 0.95f).toInt(), pixels.height / 2])
    }

    private fun withRectangleFixture(block: (AlbumMedia) -> Unit) {
        val directory = File(context.getExternalFilesDir(null), "photo_picker").apply { mkdirs() }
        val fixture = File(directory, "album-preview-${java.util.UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
            val canvas = android.graphics.Canvas(this)
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.GREEN }
            canvas.drawRect(0f, 0f, 32f, 160f, paint)
            paint.color = android.graphics.Color.BLUE
            canvas.drawRect(288f, 0f, 320f, 160f, paint)
        }
        try {
            fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.album.api.fileprovider", fixture)
            block(item.copy(uri = uri, width = 320, height = 160, mimeType = "image/png", sizeBytes = fixture.length()))
        } finally {
            bitmap.recycle()
            check(fixture.canonicalFile.parentFile == directory.canonicalFile)
            fixture.delete()
        }
    }

    private fun showView() {
        viewLoader = createAlbumViewImageLoader(loader)
        compose.setContent {
            AndroidView(
                factory = { context -> ImageView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    viewOutput = this
                } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun createTestLoader(release: CompletableDeferred<Unit>, fail: Boolean = false): ImageLoader =
        ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                if (chain.request.memoryCacheKey == chain.request.placeholderMemoryCacheKey?.key) {
                    SuccessResult(bitmap(android.graphics.Color.GREEN, 16).asImage(), chain.request)
                } else {
                    release.await()
                    if (fail) ErrorResult(null, chain.request, IllegalStateException("Original unavailable"))
                    else SuccessResult(bitmap(android.graphics.Color.RED, 256).asImage(), chain.request)
                }
            })
        }.build()

    private fun seed(media: AlbumMedia, color: Int) {
        loader.memoryCache!![MemoryCache.Key(thumbnailCacheKey(media))] = MemoryCache.Value(bitmap(color, 16).asImage())
    }

    private fun show(current: androidx.compose.runtime.State<AlbumMedia>) {
        compose.setContent {
            Image(
                painter = rememberAlbumPainter(context, current.value, false, loader, 128, 512),
                contentDescription = null, modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun awaitColor(expected: Color) {
        compose.waitUntil(5_000) {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2] == expected
        }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(expected, pixels[pixels.width / 2, pixels.height / 2])
    }

    private fun bitmap(color: Int, edge: Int): Bitmap =
        Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun media(id: Int) = AlbumMedia(
        uri = Uri.parse("content://preview-placeholder/$id"), mediaType = AlbumMediaType.IMAGE,
        displayName = "image-$id", mimeType = "image/jpeg", sizeBytes = null,
        dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null,
        width = 800, height = 600, durationMillis = null, bucketId = null,
        bucketName = null, selectedAtEpochMillis = null, source = AlbumMediaSource.PHOTO_PICKER,
    )
}
