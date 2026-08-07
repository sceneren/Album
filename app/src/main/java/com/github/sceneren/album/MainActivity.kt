package com.github.sceneren.album

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumCameraConfig
import com.github.sceneren.album.api.AlbumCompressionConfig
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerResult
import com.github.sceneren.album.api.SingleSelectionFinishMode
import com.github.sceneren.album.ui.compose.AlbumPicker
import com.github.sceneren.album.ui.theme.AlbumTheme
import com.github.sceneren.album.ui.view.AlbumPickerAppearance as ViewPickerAppearance
import com.github.sceneren.album.ui.view.AlbumPickerContract as ViewPickerContract
import com.github.sceneren.album.ui.view.AlbumPickerRequest as ViewPickerRequest

/** 仅用于演示两个可复用 UI 模块，媒体查询和选择状态均由模块负责。 */
class MainActivity : ComponentActivity() {
    private var lastResult by mutableStateOf<AlbumPickerResult?>(null)

    private val viewPicker = registerForActivityResult(ViewPickerContract()) { result ->
        lastResult = result
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
            val pickerConfig = buildRequest(
                mediaFilter = selectedFilter,
                compressionEnabled = compressionEnabled,
                showPermissionUpgrade = requestPermissionEnable,
                cameraEnabled = cameraEnable,
            )
            AlbumTheme {
                Scaffold { paddingValues ->
                    DemoScreen(
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
                        modifier = Modifier.padding(paddingValues),
                    )
                }
                if (showComposePicker) {
                    AlbumPicker(
                        config = pickerConfig,
                        onResult = { result ->
                            lastResult = result
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
    maxSelectionCount = 9,
    singleSelectionFinishMode = SingleSelectionFinishMode.EXPLICIT_CONFIRM,
    camera = AlbumCameraConfig(
        enabled = cameraEnabled,
        mixedMediaCaptureType = AlbumCameraCaptureType.PHOTO,
    ),
    compression = AlbumCompressionConfig(enabled = compressionEnabled),
    showPermissionUpgrade = showPermissionUpgrade,
)

@Composable
/** 执行 `DemoScreen` 方法定义的处理。 */
private fun DemoScreen(
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Album UI 演示", style = MaterialTheme.typography.headlineSmall)
        Text("相册页面由 album-ui-view / album-ui-compose 提供，app 只负责启动和接收结果。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlbumMediaFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(option.label()) },
                )
            }
        }
        FilterChip(
            selected = compressionEnabled,
            onClick = { onCompression(!compressionEnabled) },
            label = { Text("启用图片压缩（100KB 以下跳过）") },
        )

        FilterChip(
            selected = requestPermissionEnable,
            onClick = { onRequestPermission(!requestPermissionEnable) },
            label = { Text("启用内部权限申请") },
        )

        FilterChip(
            selected = cameraEnable,
            onClick = { onCamera(!cameraEnable) },
            label = { Text("启用相机") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenView) { Text("打开 View 相册") }
            Button(onClick = onOpenCompose) { Text("打开 Compose 相册") }
        }
        Spacer(Modifier.height(8.dp))
        result?.let { pickerResult ->
            Text("已返回 ${pickerResult.items.size} 项", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(pickerResult.items) { item ->
                    Text("${item.mediaType}: ${item.filePath}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
