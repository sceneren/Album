package com.github.sceneren.album

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.video.VideoFrameDecoder
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPhotoPickerLauncher
import com.github.sceneren.album.ui.theme.AlbumTheme

class MainActivity : ComponentActivity() {
    private lateinit var albumApi: AlbumApi
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var imagePicker: AlbumPhotoPickerLauncher
    private lateinit var videoPicker: AlbumPhotoPickerLauncher
    private lateinit var mixedPicker: AlbumPhotoPickerLauncher

    private val viewModel: AlbumViewModel by viewModels {
        AlbumViewModel.Factory(AlbumApiDataClient(albumApi))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        albumApi = AlbumApi.create(applicationContext)
        val hostViewModel = viewModel

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            hostViewModel.refresh()
        }
        imagePicker = registerPicker(AlbumMediaFilter.IMAGES)
        videoPicker = registerPicker(AlbumMediaFilter.VIDEOS)
        mixedPicker = registerPicker(AlbumMediaFilter.IMAGES_AND_VIDEOS)

        val imageLoader = createImageLoader()
        enableEdgeToEdge()
        setContent {
            AlbumTheme {
                val state by hostViewModel.uiState.collectAsState()
                val media = hostViewModel.mediaPagingData.collectAsLazyPagingItems()
                AlbumScreen(
                    state = state,
                    media = media,
                    onFilterChanged = hostViewModel::setMediaFilter,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            MediaPermissionRequestFactory.create(
                                filter = hostViewModel.uiState.value.mediaFilter,
                                sdkInt = Build.VERSION.SDK_INT,
                            ),
                        )
                    },
                    onOpenPicker = {
                        launcherFor(hostViewModel.uiState.value.mediaFilter).launch()
                    },
                    onDirectorySelected = hostViewModel::selectDirectory,
                    onRetry = media::retry,
                    imageLoader = imageLoader,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun registerPicker(
        filter: AlbumMediaFilter,
    ): AlbumPhotoPickerLauncher = albumApi.registerPhotoPicker(
        activity = this,
        mediaFilter = filter,
        maxSelectionCount = null,
        onResult = viewModel::onPhotoPickResult,
    )

    private fun launcherFor(
        filter: AlbumMediaFilter,
    ): AlbumPhotoPickerLauncher = when (filter) {
        AlbumMediaFilter.IMAGES -> imagePicker
        AlbumMediaFilter.VIDEOS -> videoPicker
        AlbumMediaFilter.IMAGES_AND_VIDEOS -> mixedPicker
    }

    private fun createImageLoader(): ImageLoader = ImageLoader.Builder(applicationContext)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            add(VideoFrameDecoder.Factory())
        }
        .build()
}
