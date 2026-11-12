package com.github.sceneren.album.api

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumCameraLauncherTest {
    @Test fun unavailablePhotoCameraReportsFailureAndClearsPendingCapture() = verifyFailure(AlbumMediaType.IMAGE)

    @Test fun unavailableVideoCameraReportsFailureAndClearsPendingCapture() = verifyFailure(AlbumMediaType.VIDEO)

    private fun verifyFailure(type: AlbumMediaType) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val client = AlbumApi.create(activity).createPickerClient(activity)
        val session = client.openSession(AlbumPickerConfig(AlbumMediaFilter.IMAGES_AND_VIDEOS, 1))
        val failure = ActivityNotFoundException("No camera handler")
        val result = AtomicReference<Result<AlbumPickerSessionSnapshot>?>()
        val launcher = AlbumCameraLauncher(
            activity, client, session.sessionId,
            FailingLauncher(ActivityResultContracts.TakePicture(), failure),
            FailingLauncher(ActivityResultContracts.CaptureVideo(), failure),
        ) { result.set(it) }
        try {
            launcher.launch(type)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (result.get() == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertSame(failure, requireNotNull(result.get()).exceptionOrNull())
            assertFalse(client.snapshot(session.sessionId).hasPendingCamera)
            // A failed launch must leave the session ready for the next camera attempt.
            runBlocking { client.prepareCamera(session.sessionId, type).getOrThrow() }
        } finally {
            runBlocking { client.cancel(session.sessionId) }
            controller.pause().stop().destroy()
        }
    }

    private class FailingLauncher(
        override val contract: ActivityResultContract<Uri, Boolean>,
        private val failure: RuntimeException,
    ) : ActivityResultLauncher<Uri>() {
        override fun launch(input: Uri, options: ActivityOptionsCompat?) { throw failure }
        override fun unregister() = Unit
    }
}
