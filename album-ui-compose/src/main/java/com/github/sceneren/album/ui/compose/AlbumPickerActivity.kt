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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.panpf.zoomimage.ZoomImage
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
import kotlinx.coroutines.Job

/** Compose 全屏相册选择页。 */
class AlbumPickerActivity : ComponentActivity() {
    private lateinit var config: com.github.sceneren.album.api.AlbumPickerConfig
    private lateinit var appearance: AlbumPickerAppearance
    private lateinit var api: AlbumApi
    private lateinit var client: com.github.sceneren.album.api.AlbumPickerClient
    private lateinit var imageLoader: AlbumImageLoader
    private var session by mutableStateOf<AlbumPickerSessionSnapshot?>(null)
    private var accessStatus by mutableStateOf(MediaAccessStatus.DENIED)
    private var feed by mutableStateOf<com.github.sceneren.album.api.AlbumMediaFeed?>(null)
    private var directories by mutableStateOf<List<AlbumDirectory>>(emptyList())
    private var message by mutableStateOf("")
    private var preview by mutableStateOf<PreviewState?>(null)
    private var previewLoadJob: Job? = null

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
        val theme = intent.getIntExtra(AlbumPickerExtras.THEME, 0)
        if (theme != 0) setTheme(theme)
        super.onCreate(savedInstanceState)
        config = AlbumPickerIntentCodec.readConfig(intent)
        appearance = AlbumPickerExtras.readAppearance(intent)
        imageLoader = AlbumUi.requireImageLoader()
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
                if (accessStatus != MediaAccessStatus.FULL) refreshContent()
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
            AlbumPickerScreen(
                config = config,
                appearance = appearance,
                session = currentSession(),
                accessStatus = accessStatus,
                directories = directories,
                feed = feed,
                message = message,
                preview = preview,
                imageLoader = imageLoader,
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
                onGridPreview = ::showGridPreview,
                onSelectedPreview = ::showSelectedPreview,
                onPreviewLoadMore = ::loadMorePreview,
                onClosePreview = ::closePreview,
                onConfirm = ::confirmSelection,
            )
        }
        refreshContent()
    }

    private var cameraLauncher: com.github.sceneren.album.api.AlbumCameraLauncher? = null

    override fun onDestroy() {
        previewLoadJob?.cancel()
        super.onDestroy()
    }

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

    private fun showGridPreview(media: AlbumMedia, loadedFeedItems: List<AlbumMedia>) {
        val cameraItems = if (accessStatus == MediaAccessStatus.FULL) {
            currentSession().cameraItems
        } else {
            emptyList()
        }
        val items = (cameraItems + loadedFeedItems).distinctBy { it.uri }
        if (items.isEmpty()) return
        previewLoadJob?.cancel()
        preview = PreviewState(
            id = System.nanoTime(),
            items = items,
            initialIndex = items.indexOfFirst { it.uri == media.uri }.coerceAtLeast(0),
            nextOffset = loadedFeedItems.size,
        )
    }

    private fun showSelectedPreview() {
        val items = currentSession().selectedItems
        if (items.isEmpty()) return
        previewLoadJob?.cancel()
        preview = PreviewState(
            id = System.nanoTime(),
            items = items,
            initialIndex = 0,
            nextOffset = null,
            endReached = true,
        )
    }

    private fun loadMorePreview() {
        val current = preview ?: return
        val offset = current.nextOffset ?: return
        if (current.loading || current.endReached) return
        preview = current.copy(loading = true)
        previewLoadJob = lifecycleScope.launch {
            api.loadMediaPage(
                mediaFilter = config.mediaFilter,
                bucketId = currentSession().bucketId,
                offset = offset,
                limit = PREVIEW_PAGE_SIZE,
            ).onSuccess { page ->
                val latest = preview?.takeIf { it.id == current.id } ?: return@onSuccess
                val knownUris = latest.items.mapTo(hashSetOf()) { it.uri }
                val additions = page.filter { knownUris.add(it.uri) }
                preview = latest.copy(
                    items = latest.items + additions,
                    nextOffset = offset + page.size,
                    loading = false,
                    endReached = page.size < PREVIEW_PAGE_SIZE,
                )
            }.onFailure { failure ->
                val latest = preview?.takeIf { it.id == current.id } ?: return@onFailure
                preview = latest.copy(loading = false, endReached = true)
                message = failure.message ?: getString(R.string.auc_preview_load_failed)
            }
        }
    }

    private fun closePreview() {
        previewLoadJob?.cancel()
        previewLoadJob = null
        preview = null
    }

    private fun cameraMediaType() = when (config.mediaFilter) {
        AlbumMediaFilter.IMAGES -> AlbumMediaType.IMAGE
        AlbumMediaFilter.VIDEOS -> AlbumMediaType.VIDEO
        AlbumMediaFilter.IMAGES_AND_VIDEOS -> if (
            config.camera.mixedMediaCaptureType == AlbumCameraCaptureType.PHOTO
        ) AlbumMediaType.IMAGE else AlbumMediaType.VIDEO
    }
}

private data class PreviewState(
    val id: Long,
    val items: List<AlbumMedia>,
    val initialIndex: Int,
    val nextOffset: Int?,
    val loading: Boolean = false,
    val endReached: Boolean = false,
)

private const val PREVIEW_PAGE_SIZE = 30
private const val PREVIEW_PREFETCH_DISTANCE = 3

@Composable
private fun AlbumPickerScreen(
    config: com.github.sceneren.album.api.AlbumPickerConfig,
    appearance: AlbumPickerAppearance,
    session: AlbumPickerSessionSnapshot,
    accessStatus: MediaAccessStatus,
    directories: List<AlbumDirectory>,
    feed: com.github.sceneren.album.api.AlbumMediaFeed?,
    message: String,
    preview: PreviewState?,
    imageLoader: AlbumImageLoader,
    onBack: () -> Unit,
    onDirectory: (Long) -> Unit,
    onRequestPermission: () -> Unit,
    onAddMore: () -> Unit,
    onCamera: () -> Unit,
    onToggle: (AlbumMedia) -> Unit,
    onGridPreview: (AlbumMedia, List<AlbumMedia>) -> Unit,
    onSelectedPreview: () -> Unit,
    onPreviewLoadMore: () -> Unit,
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
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(
                            appearance.backIconRes ?: R.drawable.auc_ic_album_back,
                        ),
                        contentDescription = stringResource(R.string.auc_back),
                        tint = Color.Unspecified,
                    )
                }
                Box(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { directoryMenu = true },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "相机胶卷",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Icon(
                            painter = painterResource(
                                appearance.folderIconRes ?: R.drawable.auc_ic_album_expand_more,
                            ),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
                    TextButton(onClick = onSelectedPreview) {
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
            if (accessStatus == MediaAccessStatus.FULL) {
                items(session.cameraItems, key = { "camera:${it.uri}" }) { item ->
                    MediaTile(
                        item,
                        item.uri in session.selectedUris,
                        appearance,
                        imageLoader,
                        onPreview = { selected ->
                            onGridPreview(selected, pagingItems?.itemSnapshotList?.items.orEmpty())
                        },
                        onToggle = onToggle,
                    )
                }
            }
            if (pagingItems != null) {
                items(
                    count = pagingItems.itemCount,
                    key = { index -> pagingItems[index]?.uri?.toString() ?: "placeholder:$index" },
                ) { index ->
                    pagingItems[index]?.let { item ->
                        MediaTile(
                            item,
                            item.uri in session.selectedUris,
                            appearance,
                            imageLoader,
                            onPreview = { selected ->
                                onGridPreview(selected, pagingItems.itemSnapshotList.items)
                            },
                            onToggle = onToggle,
                        )
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

    preview?.let { state ->
        key(state.id) {
            AlbumPreviewDialog(
                state = state,
                appearance = appearance,
                imageLoader = imageLoader,
                onLoadMore = onPreviewLoadMore,
                onClose = onClosePreview,
            )
        }
    }
}

@Composable
private fun AlbumPreviewDialog(
    state: PreviewState,
    appearance: AlbumPickerAppearance,
    imageLoader: AlbumImageLoader,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = state.initialIndex.coerceIn(0, (state.items.size - 1).coerceAtLeast(0)),
        pageCount = { state.items.size },
    )
    LaunchedEffect(pagerState.currentPage, state.items.size, state.loading, state.endReached) {
        if (
            !state.loading &&
            !state.endReached &&
            pagerState.currentPage >= state.items.size - PREVIEW_PREFETCH_DISTANCE
        ) {
            onLoadMore()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(appearance.previewBackgroundColor?.toColor() ?: Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
                key = { index -> state.items[index].uri.toString() },
            ) { index ->
                val media = state.items[index]
                if (media.mediaType == AlbumMediaType.IMAGE) {
                    ZoomImage(
                        painter = imageLoader.painter(
                            media,
                            AlbumImageTarget.PREVIEW_IMAGE,
                        ),
                        contentDescription = media.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = imageLoader.painter(
                                media,
                                AlbumImageTarget.VIDEO_COVER,
                            ),
                            contentDescription = media.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        Icon(
                            painter = painterResource(
                                appearance.videoIconRes ?: R.drawable.auc_ic_album_play,
                            ),
                            contentDescription = stringResource(R.string.auc_video_play),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(64.dp).clickable {
                                // 视频预览仅显示封面，点击图标不在选择器内播放。
                            },
                        )
                    }
                }
            }
            Button(onClick = onClose, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auc_back))
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
    appearance: AlbumPickerAppearance,
    imageLoader: AlbumImageLoader,
    onPreview: (AlbumMedia) -> Unit,
    onToggle: (AlbumMedia) -> Unit,
) {
    Box(Modifier.size(88.dp).clickable { onPreview(media) }) {
        Image(
            painter = imageLoader.painter(media, AlbumImageTarget.GRID_THUMBNAIL),
            contentDescription = media.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp)
                .background(appearance.scrimColor?.toColor() ?: Color.Transparent)
                .clickable { onToggle(media) },
            contentAlignment = Alignment.Center,
        ) {
            val icon = if (selected) {
                appearance.checkedIconRes ?: R.drawable.auc_ic_album_checked
            } else {
                appearance.uncheckedIconRes ?: R.drawable.auc_ic_album_unchecked
            }
            Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified)
        }
    }
}

private fun Int.toColor() = Color(this)
