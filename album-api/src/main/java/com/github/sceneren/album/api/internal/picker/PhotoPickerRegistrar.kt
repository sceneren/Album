package com.github.sceneren.album.api.internal.picker

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPhotoPickerLauncher
import com.github.sceneren.album.api.PhotoPickResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PhotoPickerRegistrar(
    private val processor: PhotoPickerResultProcessor,
    private val applicationScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PickerRegistrar {
    override fun register(
        activity: ComponentActivity,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
        onResult: (PhotoPickResult) -> Unit,
    ): AlbumPhotoPickerLauncher {
        val lifecycle = activity.lifecycle
        check(lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "Photo Picker cannot be registered for a destroyed Activity"
        }
        check(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            "Photo Picker must be registered before the Activity is STARTED"
        }

        val pickerContract = PhotoPickerContractFactory.create(maxSelectionCount)
        val request = mediaFilter.toPickerRequest()
        val launchPicker: () -> Unit = when (pickerContract) {
            PickerContract.Single -> {
                val launcher = activity.registerForActivityResult(
                    ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    processResult(
                        activity = activity,
                        uris = uri?.let(::listOf).orEmpty(),
                        mediaFilter = mediaFilter,
                        maxSelectionCount = maxSelectionCount,
                        onResult = onResult,
                    )
                }
                ({ launcher.launch(request) })
            }

            PickerContract.MultipleDefault -> {
                val launcher = activity.registerForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(),
                ) { uris ->
                    processResult(activity, uris, mediaFilter, maxSelectionCount, onResult)
                }
                ({ launcher.launch(request) })
            }

            is PickerContract.Multiple -> {
                val launcher = activity.registerForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(pickerContract.maxItems),
                ) { uris ->
                    processResult(activity, uris, mediaFilter, maxSelectionCount, onResult)
                }
                ({ launcher.launch(request) })
            }
        }

        return object : AlbumPhotoPickerLauncher {
            override val mediaFilter: AlbumMediaFilter = mediaFilter

            override fun launch() = launchPicker()
        }
    }

    private fun processResult(
        activity: ComponentActivity,
        uris: List<android.net.Uri>,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
        onResult: (PhotoPickResult) -> Unit,
    ) {
        applicationScope.launch {
            val result = processor.process(uris, mediaFilter, maxSelectionCount)
            withContext(mainDispatcher) {
                if (activity.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                    onResult(result)
                }
            }
        }
    }
}
