package com.github.sceneren.album.ui.compose

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import com.github.sceneren.album.api.AlbumCompressionConfig
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerResult
import com.github.sceneren.album.api.SingleSelectionFinishMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraResultLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingCameraShowsToastAndCanBeRetried() {
        val attempts = AtomicInteger()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                options: ActivityOptionsCompat?) {
                attempts.incrementAndGet()
                throw ActivityNotFoundException("No camera handler")
            }
        }
        show(registry, {})
        repeat(2) { attempt ->
            compose.onNodeWithText("拍摄").performClick()
            compose.waitUntil(5_000) { attempts.get() == attempt + 1 &&
                ShadowToast.getTextOfLatestToast() == "没有可用相机" }
            compose.waitForIdle()
        }
        assertEquals(2, attempts.get())
    }

    @Test fun closingPickerWhileMediaRefreshIsWaitingDoesNotReadTheRemovedSession() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        val provider = BlockingMediaProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        var cancellations = 0
        val pickerState = AlbumPickerState()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                options: ActivityOptionsCompat?) {
                error("No external activity is needed for this test")
            }
        }
        try {
            show(registry, {}, pickerState, { cancellations++ })
            compose.waitUntil(5_000) { provider.entered.count == 0L }
            compose.runOnIdle { pickerState.cancel() }
            compose.waitUntil(5_000) { cancellations == 1 &&
                context.getSharedPreferences("album_picker_sessions", 0).all.isEmpty() }
            // Confirmation and cancellation both remove the session. Resume the old refresh while
            // leaving the finished host composed: it must not read the removed session.
            provider.release.countDown()
            compose.waitUntil(5_000) { provider.returned.count == 0L }
            compose.waitForIdle()
            assertEquals(1, cancellations)
        } finally {
            provider.release.countDown()
        }
    }

    private fun show(
        registry: ActivityResultRegistry,
        onResult: (AlbumPickerResult) -> Unit,
        state: AlbumPickerState? = null,
        onCancel: () -> Unit = {},
    ) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme {
                    AlbumPickerHost(
                        config = AlbumPickerConfig(
                            AlbumMediaFilter.IMAGES, 1,
                            singleSelectionFinishMode = SingleSelectionFinishMode.IMMEDIATE,
                            compression = AlbumCompressionConfig(enabled = false),
                        ),
                        onResult = onResult, onCancel = onCancel, modifier = Modifier, state = state,
                        appearance = AlbumPickerAppearance(), imageLoader = { _, _ -> ColorPainter(Color.Gray) },
                        animation = AlbumPickerAnimation.None,
                    )
                }
            }
        }
    }

    private class BlockingMediaProvider : ContentProvider() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            entered.countDown()
            assertTrue("Test must release the provider", release.await(10, TimeUnit.SECONDS))
            returned.countDown()
            return MatrixCursor(projection ?: arrayOf("_id"))
        }
        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
