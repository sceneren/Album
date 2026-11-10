package com.github.sceneren.album.ui.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

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
}
