package com.github.sceneren.album.ui.view

import android.app.Activity
import android.app.Dialog
import android.animation.ObjectAnimator
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumCameraConfig
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerIntentCodec
import com.github.sceneren.album.api.AlbumPhotoPickerLauncher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowToast
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PickerResumeCheckTest {
    @Test fun resultDeliveredAfterResumeCannotSuppressNextForegroundChangeCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, 9, camera = AlbumCameraConfig(enabled = false))
        val session = AlbumApi.create(context).createPickerClient(context).openSession(config)
        val intent = AlbumPickerIntentCodec.putConfig(Intent(context, AlbumPickerActivity::class.java), config)
            .putExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID, session.sessionId)
        val previousLoader = ReflectionHelpers.getStaticField<AlbumImageLoader?>(AlbumUi::class.java, "configuredImageLoader")
        AlbumUi.setImageLoader(AlbumImageLoader { _, _, _ -> })
        val controller = Robolectric.buildActivity(AlbumPickerActivity::class.java, intent)
        try {
            val activity = controller.create().get()
            // Observe the actual lifecycle/result check requests without running unrelated provider IO.
            activity.lifecycleScope.cancel()
            controller.start().resume()
            val requests = ReflectionHelpers.getField<Channel<Unit>>(activity, "refreshRequests")
            assertTrue(requests.tryReceive().isSuccess)
            val picker = ReflectionHelpers.getField<AlbumPhotoPickerLauncher>(activity, "photoPicker")
            picker.launch()
            val request = shadowOf(activity).nextStartedActivityForResult
            activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_CANCELED, Intent())
            assertNull(ReflectionHelpers.getField<Dialog?>(activity, "processingDialog"))
            val deadline = System.nanoTime() + 5_000_000_000L
            var delivered = false
            while (!delivered && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                delivered = requests.tryReceive().isSuccess
                if (!delivered) Thread.sleep(5)
            }
            assertTrue("Asynchronous picker result must arrive after the activity is already resumed", delivered)
            controller.pause().stop().start().resume()
            assertTrue("Returning from the gallery must always check for deletions", requests.tryReceive().isSuccess)
        } finally {
            controller.pause().stop().destroy()
            ReflectionHelpers.setStaticField(AlbumUi::class.java, "configuredImageLoader", previousLoader)
        }
    }

    @Test fun selectedPhotoShowsDialogUntilTheRefreshFinishes() {
        verifyResultLoading(failResult = false)
    }

    @Test fun failedPhotoResultClosesTheLoadingDialog() {
        verifyResultLoading(failResult = true)
    }

    private fun verifyResultLoading(failResult: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, 9, camera = AlbumCameraConfig(enabled = false))
        val session = AlbumApi.create(context).createPickerClient(context).openSession(config)
        val intent = AlbumPickerIntentCodec.putConfig(Intent(context, AlbumPickerActivity::class.java), config)
            .putExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID, session.sessionId)
        val provider = BlockingPickerProvider(failResult)
        ShadowContentResolver.registerProviderInternal("picker-loading", provider)
        val previousLoader = ReflectionHelpers.getStaticField<AlbumImageLoader?>(AlbumUi::class.java, "configuredImageLoader")
        AlbumUi.setImageLoader(AlbumImageLoader { _, _, _ -> })
        val controller = Robolectric.buildActivity(AlbumPickerActivity::class.java, intent)
        try {
            val activity = controller.create().start().resume().get()
            pumpUntil { ReflectionHelpers.getField<Boolean>(activity, "isSessionReady") }
            ReflectionHelpers.getField<AlbumPhotoPickerLauncher>(activity, "photoPicker").launch()
            val request = shadowOf(activity).nextStartedActivityForResult
            activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://picker-loading/${UUID.randomUUID()}")))
            pumpUntil { provider.entered.count == 0L }
            val dialog = ReflectionHelpers.getField<Dialog?>(activity, "processingDialog")
            val animator = ReflectionHelpers.getField<ObjectAnimator?>(activity, "processingSpinnerAnimator")
            assertTrue(dialog?.isShowing == true)
            assertNotNull(animator)
            assertNotNull(dialog!!.findViewById<android.widget.ImageView>(R.id.auv_processing_spinner))

            provider.release.countDown()
            if (!failResult) {
                pumpUntil { provider.refreshEntered.count == 0L }
                assertTrue("The dialog must cover the post-result media check", dialog.isShowing)
                assertTrue(ReflectionHelpers.getField<Boolean>(activity, "isProcessingPhotoPickerResult"))
                provider.refreshRelease.countDown()
            }
            pumpUntil { !ReflectionHelpers.getField<Boolean>(activity, "isProcessingPhotoPickerResult") }
            assertNull(ReflectionHelpers.getField<Dialog?>(activity, "processingDialog"))
            assertNull(ReflectionHelpers.getField<ObjectAnimator?>(activity, "processingSpinnerAnimator"))
            assertFalse(dialog.isShowing)
            if (failResult) assertEquals(activity.getString(R.string.auv_selection_failed), ShadowToast.getTextOfLatestToast())
        } finally {
            provider.release.countDown()
            provider.refreshRelease.countDown()
            controller.pause().stop().destroy()
            ReflectionHelpers.setStaticField(AlbumUi::class.java, "configuredImageLoader", previousLoader)
        }
    }

    @Test fun destroyingThePickerDismissesTheDialogWhileResultIoIsBlocked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, 9, camera = AlbumCameraConfig(enabled = false))
        val session = AlbumApi.create(context).createPickerClient(context).openSession(config)
        val intent = AlbumPickerIntentCodec.putConfig(Intent(context, AlbumPickerActivity::class.java), config)
            .putExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID, session.sessionId)
        val provider = BlockingPickerProvider(true)
        ShadowContentResolver.registerProviderInternal("picker-loading", provider)
        val previousLoader = ReflectionHelpers.getStaticField<AlbumImageLoader?>(AlbumUi::class.java, "configuredImageLoader")
        AlbumUi.setImageLoader(AlbumImageLoader { _, _, _ -> })
        val controller = Robolectric.buildActivity(AlbumPickerActivity::class.java, intent)
        var destroyed = false
        try {
            val activity = controller.create().start().resume().get()
            pumpUntil { ReflectionHelpers.getField<Boolean>(activity, "isSessionReady") }
            ReflectionHelpers.getField<AlbumPhotoPickerLauncher>(activity, "photoPicker").launch()
            val request = shadowOf(activity).nextStartedActivityForResult
            activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://picker-loading/${UUID.randomUUID()}")))
            pumpUntil { provider.entered.count == 0L }
            val animator = ReflectionHelpers.getField<ObjectAnimator>(activity, "processingSpinnerAnimator")
            assertNotNull(animator)
            controller.pause().stop().destroy()
            destroyed = true
            assertNull(ReflectionHelpers.getField<Dialog?>(activity, "processingDialog"))
            assertNull(ReflectionHelpers.getField<ObjectAnimator?>(activity, "processingSpinnerAnimator"))
        } finally {
            provider.release.countDown()
            provider.refreshRelease.countDown()
            if (!destroyed) controller.pause().stop().destroy()
            ReflectionHelpers.setStaticField(AlbumUi::class.java, "configuredImageLoader", previousLoader)
        }
    }

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("The asynchronous picker operation did not finish", condition())
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
            val columns = projection ?: arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            return MatrixCursor(columns).apply {
                addRow(columns.map<String, Any?> { column -> when (column) {
                    MediaStore.MediaColumns.DISPLAY_NAME -> "picked.jpg"
                    MediaStore.MediaColumns.MIME_TYPE -> "image/jpeg"
                    else -> 100L
                } }.toTypedArray())
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
