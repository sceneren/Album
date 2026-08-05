package com.github.sceneren.album.ui.compose

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
        enableEdgeToEdge(
            statusBarStyle = systemBarStyle(appearance.toolbarColor ?: DEFAULT_TOOLBAR_COLOR),
            navigationBarStyle = systemBarStyle(appearance.bottomBarColor ?: DEFAULT_BOTTOM_BAR_COLOR),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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
                if (preview != null) {
                    closePreview()
                    return
                }
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
    }

    private var cameraLauncher: com.github.sceneren.album.api.AlbumCameraLauncher? = null

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

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
private val DEFAULT_TOOLBAR_COLOR = 0xFF303136.toInt()
private val DEFAULT_BOTTOM_BAR_COLOR = 0xFF303136.toInt()

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
    val toolbar = (appearance.toolbarColor ?: DEFAULT_TOOLBAR_COLOR).toColor()
    val bottom = (appearance.bottomBarColor ?: DEFAULT_BOTTOM_BAR_COLOR).toColor()
    val accent = (appearance.accentColor ?: 0xFF00C853.toInt()).toColor()
    val primary = (appearance.primaryTextColor ?: 0xFFFFFFFF.toInt()).toColor()
    val secondary = (appearance.secondaryTextColor ?: 0xFFD3D3D3.toInt()).toColor()
    var directoryMenu by remember { mutableStateOf(false) }
    val pagingItems: LazyPagingItems<AlbumMedia>? = feed?.pagingData?.collectAsLazyPagingItems()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(toolbar)
                        .statusBarsPadding()
                        .height(56.dp),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { directoryMenu = true },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "相机胶卷",
                                color = primary,
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
                        DropdownMenu(
                            expanded = directoryMenu,
                            onDismissRequest = { directoryMenu = false },
                        ) {
                            if (accessStatus == MediaAccessStatus.FULL) {
                                DropdownMenuItem(
                                    text = { Text("全部媒体") },
                                    onClick = {
                                        directoryMenu = false
                                        onDirectory(Long.MIN_VALUE)
                                    },
                                )
                                directories.forEach { directory ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                directory.bucketName
                                                    ?: "未命名 (${directory.mediaCount})",
                                            )
                                        },
                                        onClick = {
                                            directoryMenu = false
                                            onDirectory(directory.bucketId)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = onBack) { Text("取消", color = primary) }
                }
            },
            bottomBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(bottom)
                        .navigationBarsPadding(),
                ) {
                    if (accessStatus != MediaAccessStatus.FULL && config.showPermissionUpgrade) {
                        Button(onClick = onRequestPermission, Modifier.fillMaxWidth()) {
                            Text(
                                if (accessStatus == MediaAccessStatus.PARTIAL) {
                                    "当前只能访问部分媒体，申请全部访问"
                                } else {
                                    "申请相册权限"
                                },
                            )
                        }
                    }
                    if (message.isNotBlank()) {
                        Text(
                            message,
                            color = secondary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onSelectedPreview) {
                            Text(
                                text = if (session.selectedItems.isEmpty()) {
                                    "预览"
                                } else {
                                    "预览(${session.selectedItems.size})"
                                },
                                color = primary,
                            )
                        }
                        Text(
                            "已选 ${session.selectedItems.size}",
                            color = secondary,
                            modifier = Modifier.weight(1f),
                        )
                        SelectionFinishAction(
                            selectedCount = session.selectedItems.size,
                            appearance = appearance,
                            onConfirm = onConfirm,
                        )
                    }
                }
            },
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (config.camera.enabled) {
                    item(key = "action_camera") {
                        ActionTile("拍摄", appearance.cameraIconRes, onCamera)
                    }
                }
                if (accessStatus != MediaAccessStatus.FULL) {
                    item(key = "action_add") {
                        ActionTile("添加更多", appearance.addIconRes, onAddMore)
                    }
                }
                if (accessStatus == MediaAccessStatus.FULL) {
                    items(session.cameraItems, key = { "camera:${it.uri}" }) { item ->
                        MediaTile(
                            item,
                            item.uri in session.selectedUris,
                            appearance,
                            imageLoader,
                            onPreview = { selected ->
                                onGridPreview(
                                    selected,
                                    pagingItems?.itemSnapshotList?.items.orEmpty(),
                                )
                            },
                            onToggle = onToggle,
                        )
                    }
                }
                if (pagingItems != null) {
                    items(
                        count = pagingItems.itemCount,
                        key = { index ->
                            pagingItems[index]?.uri?.toString() ?: "placeholder:$index"
                        },
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
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = accent)
                        }
                    }
                }
            }
        }

        preview?.let { state ->
            key(state.id) {
                AlbumPreviewScreen(
                    state = state,
                    session = session,
                    appearance = appearance,
                    imageLoader = imageLoader,
                    onToggle = onToggle,
                    onLoadMore = onPreviewLoadMore,
                    onClose = onClosePreview,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun AlbumPreviewScreen(
    state: PreviewState,
    session: AlbumPickerSessionSnapshot,
    appearance: AlbumPickerAppearance,
    imageLoader: AlbumImageLoader,
    onToggle: (AlbumMedia) -> Unit,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    val toolbar = (appearance.toolbarColor ?: DEFAULT_TOOLBAR_COLOR).toColor()
    val bottom = (appearance.bottomBarColor ?: DEFAULT_BOTTOM_BAR_COLOR).toColor()
    val previewBackground = appearance.previewBackgroundColor?.toColor() ?: Color.Black
    val primary = (appearance.primaryTextColor ?: 0xFFFFFFFF.toInt()).toColor()
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

    val currentMedia = state.items.getOrNull(pagerState.currentPage)
    val currentSelected = currentMedia?.uri in session.selectedUris
    Column(Modifier
        .fillMaxSize()
        .background(previewBackground)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(toolbar)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    painter = painterResource(
                        appearance.backIconRes ?: R.drawable.auc_ic_album_back,
                    ),
                    contentDescription = stringResource(R.string.auc_back),
                    tint = Color.Unspecified,
                )
            }
            Text(
                text = stringResource(
                    R.string.auc_preview_position,
                    pagerState.currentPage + 1,
                    state.items.size,
                ),
                color = primary,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(
                onClick = { currentMedia?.let(onToggle) },
                enabled = currentMedia != null,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    painter = painterResource(
                        if (currentSelected) {
                            appearance.checkedIconRes ?: R.drawable.auc_ic_album_checked
                        } else {
                            appearance.uncheckedIconRes ?: R.drawable.auc_ic_album_unchecked
                        },
                    ),
                    contentDescription = stringResource(R.string.auc_toggle_selection),
                    tint = Color.Unspecified,
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
                        modifier = Modifier
                            .size(64.dp)
                            .clickable {
                                // 视频预览仅显示封面，点击图标不在选择器内播放。
                            },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bottom)
                .navigationBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionFinishAction(
                selectedCount = session.selectedItems.size,
                appearance = appearance,
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
private fun SelectionFinishAction(
    selectedCount: Int,
    appearance: AlbumPickerAppearance,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = (appearance.accentColor ?: 0xFF00C853.toInt()).toColor()
    val primary = (appearance.primaryTextColor ?: 0xFFFFFFFF.toInt()).toColor()
    val secondary = (appearance.secondaryTextColor ?: 0xFFD3D3D3.toInt()).toColor()
    if (selectedCount == 0) {
        Text(
            text = stringResource(R.string.auc_please_select),
            color = secondary,
            fontSize = 14.sp,
            modifier = modifier.padding(horizontal = 8.dp),
        )
        return
    }

    Row(
        modifier = modifier
            .clickable(onClick = onConfirm)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        appearance.doneIconRes?.let { iconRes ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .height(18.dp)
                    .wrapContentHeight()
                    .widthIn(min = 18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .background(accent, CircleShape)
                .height(18.dp)
                .wrapContentHeight()
                .widthIn(min = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = selectedCount.toString(),
                color = primary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.auc_done),
            color = accent,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ActionTile(label: String, iconRes: Int?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(88.dp)
            .background(Color.DarkGray)
            .clickable(onClick = onClick),
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
    Box(Modifier
        .size(88.dp)
        .clickable { onPreview(media) }) {
        Image(
            painter = imageLoader.painter(media, AlbumImageTarget.GRID_THUMBNAIL),
            contentDescription = media.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
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

private fun systemBarStyle(color: Int): SystemBarStyle =
    if (ColorUtils.calculateLuminance(color) > LIGHT_COLOR_LUMINANCE) {
        SystemBarStyle.light(
            scrim = color,
            darkScrim = ColorUtils.blendARGB(color, android.graphics.Color.BLACK, 0.6f),
        )
    } else {
        SystemBarStyle.dark(color)
    }

private const val LIGHT_COLOR_LUMINANCE = 0.5
