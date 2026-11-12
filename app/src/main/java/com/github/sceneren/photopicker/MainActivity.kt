package com.github.sceneren.photopicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.github.sceneren.album.ui.compose.AlbumPickerContent
import com.github.sceneren.album.ui.compose.rememberAlbumPickerState
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumCameraConfig
import com.github.sceneren.album.api.AlbumCompressionConfig
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerResult
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.SingleSelectionFinishMode
import com.github.sceneren.album.ui.compose.AlbumPicker
import com.github.sceneren.photopicker.ui.theme.AlbumTheme
import com.github.sceneren.album.ui.view.AlbumPickerAppearance as ViewPickerAppearance
import com.github.sceneren.album.ui.view.AlbumPickerContract as ViewPickerContract
import com.github.sceneren.album.ui.view.AlbumPickerRequest as ViewPickerRequest

/** 仅用于演示两个可复用 UI 模块，媒体查询和选择状态均由模块负责。 */
class MainActivity : ComponentActivity() {
    private var lastResult by mutableStateOf<AlbumPickerResult?>(null)
    private val albumApi by lazy { AlbumApi.create(applicationContext) }

    private fun showResult(result: AlbumPickerResult?) {
        val previous = lastResult
        lastResult = result
        // The demo only displays the current result. Its previous result is no longer consumed.
        if (previous != null && previous != result) lifecycleScope.launch {
            albumApi.releaseGeneratedMedia(applicationContext, previous)
        }
    }

    private val viewPicker = registerForActivityResult(ViewPickerContract()) { result ->
        showResult(result)
    }

    /** 处理 `onCreate` 回调。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selectedFilter by rememberSaveable { mutableStateOf(AlbumMediaFilter.IMAGES) }
            var compressionEnabled by rememberSaveable { mutableStateOf(true) }
            var requestPermissionEnable by rememberSaveable { mutableStateOf(true) }
            var cameraEnable by rememberSaveable { mutableStateOf(true) }
            var showComposePicker by rememberSaveable { mutableStateOf(false) }
            var showEmbeddedPicker by rememberSaveable { mutableStateOf(false) }
            val pickerConfig = buildRequest(
                mediaFilter = selectedFilter,
                compressionEnabled = compressionEnabled,
                showPermissionUpgrade = requestPermissionEnable,
                cameraEnabled = cameraEnable,
            )
            AlbumTheme {
                Scaffold { paddingValues ->
                    if (showEmbeddedPicker) {
                        EmbeddedPickerDemo(
                            config = pickerConfig.copy(
                                maxSelectionCount = 9,
                                singleSelectionFinishMode = SingleSelectionFinishMode.EXPLICIT_CONFIRM,
                            ),
                            onResult = { showResult(it); showEmbeddedPicker = false },
                            onCancel = { showEmbeddedPicker = false },
                            modifier = Modifier.padding(paddingValues),
                        )
                    } else DemoScreen(
                        filter = selectedFilter,
                        compressionEnabled = compressionEnabled,
                        requestPermissionEnable = requestPermissionEnable,
                        cameraEnable = cameraEnable,
                        result = lastResult,
                        onFilter = { selectedFilter = it },
                        onCompression = { compressionEnabled = it },
                        onRequestPermission = { requestPermissionEnable = it },
                        onCamera = { cameraEnable = it },
                        onOpenView = {
                            viewPicker.launch(
                                ViewPickerRequest(
                                    config = pickerConfig,
                                    appearance = ViewPickerAppearance(
                                        gridItemSpacingDp = 1,
                                        gridSpanCount = 4,
                                    ),
                                ),
                            )
                        },
                        onOpenCompose = { showComposePicker = true },
                        onOpenEmbedded = { showEmbeddedPicker = true },
                        modifier = Modifier.padding(paddingValues),
                    )
                }
                if (showComposePicker) {
                    AlbumPicker(
                        config = pickerConfig,
                        onResult = { result ->
                            showResult(result)
                            showComposePicker = false
                        },
                        onCancel = { showComposePicker = false },
                    )
                }
            }
        }
    }

}

/** 创建或准备 `buildRequest` 对应的对象。 */
private fun buildRequest(
    mediaFilter: AlbumMediaFilter,
    compressionEnabled: Boolean,
    showPermissionUpgrade: Boolean,
    cameraEnabled: Boolean,
): AlbumPickerConfig = AlbumPickerConfig(
    mediaFilter = mediaFilter,
    maxSelectionCount = 1,
    singleSelectionFinishMode = SingleSelectionFinishMode.IMMEDIATE,
    camera = AlbumCameraConfig(
        enabled = cameraEnabled,
        mixedMediaCaptureType = AlbumCameraCaptureType.PHOTO,
    ),
    compression = AlbumCompressionConfig(enabled = compressionEnabled),
    showPermissionUpgrade = showPermissionUpgrade,
)

@Composable
/** 执行 `DemoScreen` 方法定义的处理。 */
internal fun DemoScreen(
    filter: AlbumMediaFilter,
    compressionEnabled: Boolean,
    requestPermissionEnable: Boolean,
    cameraEnable: Boolean,
    result: AlbumPickerResult?,
    onFilter: (AlbumMediaFilter) -> Unit,
    onCompression: (Boolean) -> Unit,
    onRequestPermission: (Boolean) -> Unit,
    onCamera: (Boolean) -> Unit,
    onOpenView: () -> Unit,
    onOpenCompose: () -> Unit,
    onOpenEmbedded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.demo_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(R.string.demo_description))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlbumMediaFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
        FilterChip(
            selected = compressionEnabled,
            onClick = { onCompression(!compressionEnabled) },
            label = { Text(stringResource(R.string.enable_compression)) },
        )

        FilterChip(
            selected = requestPermissionEnable,
            onClick = { onRequestPermission(!requestPermissionEnable) },
            label = { Text(stringResource(R.string.enable_permission_request)) },
        )

        FilterChip(
            selected = cameraEnable,
            onClick = { onCamera(!cameraEnable) },
            label = { Text(stringResource(R.string.enable_camera)) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenView) { Text(stringResource(R.string.open_view_picker)) }
            Button(onClick = onOpenCompose) { Text(stringResource(R.string.open_compose_picker)) }
        }
        Button(onClick = onOpenEmbedded) { Text(stringResource(R.string.open_embedded_picker)) }
        Spacer(Modifier.height(8.dp))
        result?.let { pickerResult ->
            Text(
                text = stringResource(R.string.result_count, pickerResult.items.size),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(pickerResult.items) { item ->
                    Text(
                        text = stringResource(
                            R.string.result_item_format,
                            item.mediaType.name,
                            item.resultUri.toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** 宿主提供标题及操作栏，内容组件只占 Column 分配的区域。 */
@Composable
private fun EmbeddedPickerDemo(
    config: AlbumPickerConfig,
    onResult: (AlbumPickerResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberAlbumPickerState()
    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.embedded_picker_title),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        AlbumPickerContent(
            config = config,
            state = state,
            onResult = onResult,
            onCancel = onCancel,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = state::cancel, enabled = !state.isProcessing) {
                Text(stringResource(R.string.embedded_picker_cancel))
            }
            Button(onClick = state::preview, enabled = state.canConfirm) {
                Text(stringResource(R.string.embedded_picker_preview))
            }
            Button(onClick = state::confirm, enabled = state.canConfirm) {
                Text(stringResource(R.string.embedded_picker_confirm, state.selectedItems.size))
            }
        }
    }
}
