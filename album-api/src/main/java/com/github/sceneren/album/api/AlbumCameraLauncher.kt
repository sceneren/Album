package com.github.sceneren.album.api

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** 相机 Activity Result 的统一包装。 */
class AlbumCameraLauncher internal constructor(
    private val activity: ComponentActivity,
    private val client: AlbumPickerClient,
    private val sessionId: String,
    private val photoLauncher: ActivityResultLauncher<Uri>,
    private val videoLauncher: ActivityResultLauncher<Uri>,
    private val onResult: (Result<AlbumPickerSessionSnapshot>) -> Unit,
) {
    /** 注册后可多次调用；每次调用都会先把待写入 URI 持久化。 */
    fun launch(mediaType: AlbumMediaType) {
        activity.lifecycleScope.launch {
            val captureType = when (mediaType) {
                AlbumMediaType.IMAGE -> AlbumMediaType.IMAGE
                AlbumMediaType.VIDEO -> AlbumMediaType.VIDEO
            }
            val prepared = client.prepareCamera(sessionId, captureType)
            prepared.fold(
                onSuccess = { capture ->
                    try {
                        if (captureType == AlbumMediaType.IMAGE) {
                            photoLauncher.launch(capture.uri)
                        } else {
                            videoLauncher.launch(capture.uri)
                        }
                    } catch (failure: RuntimeException) {
                        client.completeCamera(sessionId, success = false)
                        onResult(Result.failure(failure))
                    }
                },
                onFailure = { failure -> onResult(Result.failure(failure)) },
            )
        }
    }
}
