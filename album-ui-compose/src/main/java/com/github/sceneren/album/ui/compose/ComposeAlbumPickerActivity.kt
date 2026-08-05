package com.github.sceneren.album.ui.compose

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaPermissionRequestFactory
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerIntentCodec
import com.github.sceneren.album.api.AlbumPickerSessionSnapshot
import com.github.sceneren.album.api.MediaAccessStatus
import com.github.sceneren.album.api.PhotoPickResult
import kotlinx.coroutines.launch

/** Compose 全屏相册选择页。 */
class ComposeAlbumPickerActivity : ComponentActivity() {
    private lateinit var config: com.github.sceneren.album.api.AlbumPickerConfig
    private lateinit var appearance: ComposeAlbumPickerAppearance
    private lateinit var api: AlbumApi
    private lateinit var client: com.github.sceneren.album.api.AlbumPickerClient
    private var session by mutableStateOf<AlbumPickerSessionSnapshot?>(null)
    private var accessStatus by mutableStateOf(MediaAccessStatus.DENIED)
    private var feed by mutableStateOf<com.github.sceneren.album.api.AlbumMediaFeed?>(null)
    private var directories by mutableStateOf<List<AlbumDirectory>>(emptyList())
    private var message by mutableStateOf("")
    private var preview by mutableStateOf<AlbumMedia?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        lifecycleScope.launch {
            api.syncPartialSelections(config.mediaFilter)
            refreshContent()
        }
    }

    private var photoPicker: com.github.sceneren.album.api.AlbumPhotoPickerLauncher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = intent.getIntExtra(ComposeAlbumPickerExtras.THEME, 0)
        if (theme != 0) setTheme(theme)
        super.onCreate(savedInstanceState)
        config = AlbumPickerIntentCodec.readConfig(intent)
        appearance = ComposeAlbumPickerExtras.readAppearance(intent)
        api = AlbumApi.create(this)
        client = api.createPickerClient(this)
        val sessionId = requireNotNull(intent.getStringExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID))
        session = client.openSession(config, sessionId)
        photoPicker = api.registerPhotoPicker(this, config.mediaFilter, config.maxSelectionCount) { result ->
            message = when (result) {
                is PhotoPickResult.Selected -> "已添加 ${result.media.size} 项，可在网格中手动选择"
                PhotoPickResult.Cancelled -> "已取消添加"
                is PhotoPickResult.Failed -> "添加失败：${result.reason.name}"
            }
            refreshContent()
        }
        cameraLauncher = client.registerCamera(this, currentSession().sessionId) { result ->
            result.onSuccess { updated ->
                renderSession(updated)
                maybeAutoConfirm()
            }.onFailure { message = it.message ?: "拍摄失败" }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                lifecycleScope.launch {
                    client.cancel(currentSession().sessionId)
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        })
        setContent {
            ComposeAlbumPickerScreen(
                config = config,
                appearance = appearance,
                session = currentSession(),
                accessStatus = accessStatus,
                directories = directories,
                feed = feed,
                message = message,
                preview = preview,
                onBack = { onBackPressedDispatcher.onBackPressed() },
                onDirectory = { bucketId ->
                    lifecycleScope.launch {
                        client.setBucket(currentSession().sessionId, bucketId)
                        refreshContent()
                    }
                },
                onRequestPermission = {
                    permissionLauncher.launch(AlbumMediaPermissionRequestFactory.create(config.mediaFilter))
                },
                onAddMore = { photoPicker?.launch() },
                onCamera = { cameraLauncher?.launch(cameraMediaType()) },
                onToggle = ::toggleMedia,
                onPreview = { preview = it },
                onClosePreview = { preview = null },
                onConfirm = ::confirmSelection,
            )
        }
        refreshContent()
    }

    private var cameraLauncher: com.github.sceneren.album.api.AlbumCameraLauncher? = null

    private fun refreshContent() {
        accessStatus = api.getMediaAccessStatus(config.mediaFilter)
        lifecycleScope.launch {
            directories = if (accessStatus == MediaAccessStatus.FULL) {
                api.getMediaDirectories(config.mediaFilter).getOrNull().orEmpty()
            } else {
                emptyList()
            }
            val current = currentSession()
            feed = api.getMediaFeed(config.mediaFilter, current.bucketId)
            renderSession(client.snapshot(current.sessionId))
        }
    }

    private fun renderSession(updated: AlbumPickerSessionSnapshot) {
        session = updated
    }

    private fun currentSession(): AlbumPickerSessionSnapshot =
        requireNotNull(session) { "相册选择会话尚未初始化" }

    private fun toggleMedia(media: AlbumMedia) {
        lifecycleScope.launch {
            client.toggleSelection(currentSession().sessionId, media).onSuccess { updated ->
                renderSession(updated)
                maybeAutoConfirm()
            }.onFailure { message = it.message ?: "选择失败" }
        }
    }

    private fun maybeAutoConfirm() {
        if (
            config.maxSelectionCount == 1 &&
            config.singleSelectionFinishMode == com.github.sceneren.album.api.SingleSelectionFinishMode.IMMEDIATE &&
            currentSession().selectedItems.size == 1
        ) confirmSelection()
    }

    private fun confirmSelection() {
        if (currentSession().selectedItems.isEmpty()) {
            message = "请先选择媒体"
            return
        }
        lifecycleScope.launch {
            client.confirm(currentSession().sessionId).onSuccess { result ->
                setResult(Activity.RESULT_OK, AlbumPickerIntentCodec.putResult(Intent(), result))
                finish()
            }.onFailure { message = "处理失败：${it.message ?: "请重试"}" }
        }
    }

    private fun cameraMediaType() = when (config.mediaFilter) {
        AlbumMediaFilter.IMAGES -> AlbumMediaType.IMAGE
        AlbumMediaFilter.VIDEOS -> AlbumMediaType.VIDEO
        AlbumMediaFilter.IMAGES_AND_VIDEOS -> if (
            config.camera.mixedMediaCaptureType == AlbumCameraCaptureType.PHOTO
        ) AlbumMediaType.IMAGE else AlbumMediaType.VIDEO
    }
}

@Composable
private fun ComposeAlbumPickerScreen(
    config: com.github.sceneren.album.api.AlbumPickerConfig,
    appearance: ComposeAlbumPickerAppearance,
    session: AlbumPickerSessionSnapshot,
    accessStatus: MediaAccessStatus,
    directories: List<AlbumDirectory>,
    feed: com.github.sceneren.album.api.AlbumMediaFeed?,
    message: String,
    preview: AlbumMedia?,
    onBack: () -> Unit,
    onDirectory: (Long) -> Unit,
    onRequestPermission: () -> Unit,
    onAddMore: () -> Unit,
    onCamera: () -> Unit,
    onToggle: (AlbumMedia) -> Unit,
    onPreview: (AlbumMedia) -> Unit,
    onClosePreview: () -> Unit,
    onConfirm: () -> Unit,
) {
    val toolbar = (appearance.toolbarColor ?: 0xFF303136.toInt()).toColor()
    val bottom = (appearance.bottomBarColor ?: 0xFF303136.toInt()).toColor()
    val accent = (appearance.accentColor ?: 0xFF00C853.toInt()).toColor()
    var directoryMenu by remember { mutableStateOf(false) }
    val pagingItems: LazyPagingItems<AlbumMedia>? = feed?.pagingData?.collectAsLazyPagingItems()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(toolbar).padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = MaterialTheme.typography.headlineMedium.fontSize) }
                Box(Modifier.weight(1f)) {
                    Text(
                        text = "相机胶卷⌄",
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().clickable { directoryMenu = true },
                        textAlign = TextAlign.Center,
                    )
                    DropdownMenu(expanded = directoryMenu, onDismissRequest = { directoryMenu = false }) {
                        if (accessStatus == MediaAccessStatus.FULL) {
                            DropdownMenuItem(
                                text = { Text("全部媒体") },
                                onClick = { directoryMenu = false; onDirectory(Long.MIN_VALUE) },
                            )
                            directories.forEach { directory ->
                                DropdownMenuItem(
                                    text = { Text(directory.bucketName ?: "未命名 (${directory.mediaCount})") },
                                    onClick = { directoryMenu = false; onDirectory(directory.bucketId) },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onBack) { Text("取消", color = Color.White) }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(bottom)) {
                if (accessStatus != MediaAccessStatus.FULL && config.showPermissionUpgrade) {
                    Button(onClick = onRequestPermission, Modifier.fillMaxWidth()) {
                        Text(if (accessStatus == MediaAccessStatus.PARTIAL) "当前只能访问部分媒体，申请全部访问" else "申请相册权限")
                    }
                }
                if (message.isNotBlank()) Text(message, color = Color.LightGray, modifier = Modifier.padding(horizontal = 12.dp))
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { session.selectedItems.firstOrNull()?.let(onPreview) }) {
                        Text(
                            text = if (session.selectedItems.isEmpty()) "预览" else "预览(${session.selectedItems.size})",
                            color = Color.White,
                        )
                    }
                    Text("已选 ${session.selectedItems.size}", color = Color.LightGray, modifier = Modifier.weight(1f))
                    Button(onClick = onConfirm) { Text("完成(${session.selectedItems.size})", color = accent) }
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (config.camera.enabled) {
                item(key = "action_camera") { ActionTile("拍摄", appearance.cameraIconRes, onCamera) }
            }
            if (accessStatus != MediaAccessStatus.FULL) {
                item(key = "action_add") { ActionTile("添加更多", appearance.addIconRes, onAddMore) }
            }
            items(session.cameraItems, key = { "camera:${it.uri}" }) { item ->
                MediaTile(item, item.uri in session.selectedUris, appearance, onPreview, onToggle)
            }
            if (pagingItems != null) {
                items(
                    count = pagingItems.itemCount,
                    key = { index -> pagingItems[index]?.uri?.toString() ?: "placeholder:$index" },
                ) { index ->
                    pagingItems[index]?.let { item ->
                        MediaTile(item, item.uri in session.selectedUris, appearance, onPreview, onToggle)
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                }
            }
        }
    }

    preview?.let { media ->
        Dialog(onDismissRequest = onClosePreview, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(appearance.previewBackgroundColor?.toColor() ?: Color.Black)) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.displayName,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
                Button(onClick = onClosePreview, Modifier.fillMaxWidth()) { Text("返回") }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, iconRes: Int?, onClick: () -> Unit) {
    Box(
        Modifier.size(88.dp).background(Color.DarkGray).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (iconRes != null) Icon(painterResource(iconRes), contentDescription = label, tint = Color.White)
        else Text(label, color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MediaTile(
    media: AlbumMedia,
    selected: Boolean,
    appearance: ComposeAlbumPickerAppearance,
    onPreview: (AlbumMedia) -> Unit,
    onToggle: (AlbumMedia) -> Unit,
) {
    Box(Modifier.size(88.dp).clickable { onPreview(media) }) {
        AsyncImage(
            model = media.uri,
            contentDescription = media.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp)
                .background(Color.Black.copy(alpha = 0.5f)).clickable { onToggle(media) },
            contentAlignment = Alignment.Center,
        ) {
            val icon = if (selected) appearance.checkedIconRes else appearance.uncheckedIconRes
            if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = Color.White)
            else Text(if (selected) "✓" else "○", color = Color.White)
        }
    }
}

private fun Int.toColor() = Color(this)
