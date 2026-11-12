package com.github.sceneren.album.ui.compose

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
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
import java.util.UUID
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

    @Test fun photoPickerReturnShowsSpinnerThroughMetadataAndRefresh() {
        verifyPhotoPickerLoading(embedded = false, failResult = false)
    }

    @Test fun embeddedPhotoPickerFailureClosesSpinnerAndRestoresHostState() {
        verifyPhotoPickerLoading(embedded = true, failResult = true)
    }

    private fun verifyPhotoPickerLoading(embedded: Boolean, failResult: Boolean) {
        val provider = BlockingPickerProvider(failResult)
        ShadowContentResolver.registerProviderInternal("compose-picker-loading", provider)
        val pickerState = AlbumPickerState()
        var pickerRequestCode = -1
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                options: ActivityOptionsCompat?) {
                check(contract is ActivityResultContracts.PickMultipleVisualMedia)
                pickerRequestCode = requestCode
            }
        }
        try {
            show(registry, {}, pickerState, embedded = embedded)
            compose.onNodeWithContentDescription("正在处理所选文件").assertDoesNotExist()
            compose.onNodeWithText("添加更多").performClick()
            compose.waitUntil(5_000) { pickerRequestCode != -1 }
            compose.runOnIdle {
                assertTrue(registry.dispatchResult(pickerRequestCode, Activity.RESULT_OK,
                    Intent().setData(Uri.parse("content://compose-picker-loading/${UUID.randomUUID()}"))))
            }
            try {
                compose.waitUntil(5_000) { provider.entered.count == 0L && pickerState.isProcessing }
            } catch (failure: Throwable) {
                throw AssertionError("entered=${provider.entered.count}, processing=${pickerState.isProcessing}, " +
                    "toast=${ShadowToast.getTextOfLatestToast()}", failure)
            }
            compose.onNodeWithContentDescription("正在处理所选文件").assertIsDisplayed()
            provider.release.countDown()
            if (!failResult) {
                compose.waitUntil(5_000) { provider.refreshEntered.count == 0L }
                compose.onNodeWithContentDescription("正在处理所选文件").assertIsDisplayed()
                assertTrue(pickerState.isProcessing)
                provider.refreshRelease.countDown()
            }
            compose.waitUntil(5_000) { !pickerState.isProcessing }
            compose.onNodeWithContentDescription("正在处理所选文件").assertDoesNotExist()
            if (failResult) assertEquals(RuntimeEnvironment.getApplication().getString(R.string.auc_selection_failed),
                ShadowToast.getTextOfLatestToast())
        } finally {
            provider.release.countDown()
            provider.refreshRelease.countDown()
        }
    }

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
            try {
                compose.waitUntil(5_000) { attempts.get() == attempt + 1 &&
                    ShadowToast.getTextOfLatestToast() == "没有可用相机" }
            } catch (failure: Throwable) {
                throw AssertionError("Attempt=$attempt, launches=${attempts.get()}, toast=${ShadowToast.getTextOfLatestToast()}", failure)
            }
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

    @Test fun cameraActionRemainsUsableWhileTheInitialMediaQueryIsBlocked() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        val provider = BlockingMediaProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val attempts = AtomicInteger()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                options: ActivityOptionsCompat?) {
                attempts.incrementAndGet()
                throw ActivityNotFoundException("No camera handler")
            }
        }
        try {
            show(registry, {})
            compose.waitUntil(5_000) { provider.entered.count == 0L }
            compose.onNodeWithText("拍摄").performClick()
            compose.waitUntil(5_000) { attempts.get() == 1 && ShadowToast.getTextOfLatestToast() == "没有可用相机" }
            assertEquals(1L, provider.release.count)
        } finally {
            provider.release.countDown()
        }
    }

    private fun show(
        registry: ActivityResultRegistry,
        onResult: (AlbumPickerResult) -> Unit,
        state: AlbumPickerState? = null,
        onCancel: () -> Unit = {},
        embedded: Boolean = false,
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
                        onResult = onResult, onCancel = onCancel, modifier = Modifier.fillMaxSize(), state = state,
                        embedded = embedded,
                        appearance = AlbumPickerAppearance(), imageLoader = { _, _ -> ColorPainter(Color.Gray) },
                        animation = AlbumPickerAnimation.None,
                    )
                }
            }
        }
    }

    private class BlockingPickerProvider(private val failResult: Boolean) : ContentProvider() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val refreshEntered = CountDownLatch(1)
        val refreshRelease = CountDownLatch(1)
        private val queries = AtomicInteger()
        override fun onCreate() = true
        override fun getType(uri: Uri): String {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            return if (failResult) "application/octet-stream" else "image/jpeg"
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            if (queries.incrementAndGet() > 1) {
                refreshEntered.countDown()
                check(refreshRelease.await(10, TimeUnit.SECONDS))
            }
            val columns = projection ?: arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            return MatrixCursor(columns).apply {
                addRow(columns.map<String, Any?> { column -> when (column) {
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME -> "picked.jpg"
                    android.provider.MediaStore.MediaColumns.MIME_TYPE -> "image/jpeg"
                    else -> 100L
                } }.toTypedArray())
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
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
