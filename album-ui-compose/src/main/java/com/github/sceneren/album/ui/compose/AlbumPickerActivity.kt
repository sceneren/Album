package com.github.sceneren.album.ui.compose

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.DimenRes
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
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
    private var preview by mutableStateOf<PreviewState?>(null)
    private var isConfirming by mutableStateOf(false)
    private var previewLoadJob: Job? = null
    private var messageToast: Toast? = null
    private var isPhotoPickerInFlight = false

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
            isPhotoPickerInFlight = false
            showMessage(
                when (result) {
                    is PhotoPickResult.Selected -> getString(
                        R.string.auc_added_count,
                        result.media.size,
                    )
                    PhotoPickResult.Cancelled -> getString(R.string.auc_add_cancelled)
                    is PhotoPickResult.Failed -> getString(
                        R.string.auc_add_failed,
                        result.reason.name,
                    )
                },
            )
            if (result is PhotoPickResult.Selected) refreshContent()
        }
        cameraLauncher = client.registerCamera(this, currentSession().sessionId) { result ->
            result.onSuccess { updated ->
                renderSession(updated)
                if (accessStatus != MediaAccessStatus.FULL) refreshContent()
                maybeAutoConfirm()
            }.onFailure { failure ->
                showMessage(failure.message ?: getString(R.string.auc_camera_failed))
            }
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
                preview = preview,
                imageLoader = imageLoader,
                onBack = { onBackPressedDispatcher.onBackPressed() },
                onDirectory = { bucketId ->
                    if (shouldUpdateDirectory(currentSession().bucketId, bucketId)) {
                        lifecycleScope.launch {
                            client.setBucket(currentSession().sessionId, bucketId).onSuccess { updated ->
                                renderSession(updated)
                                refreshContent()
                            }
                        }
                    }
                },
                onRequestPermission = {
                    permissionLauncher.launch(AlbumMediaPermissionRequestFactory.create(config.mediaFilter))
                },
                onAddMore = ::launchPhotoPicker,
                onCamera = { cameraLauncher?.launch(cameraMediaType()) },
                onToggle = ::toggleMedia,
                onGridPreview = ::showGridPreview,
                onSelectedPreview = ::showSelectedPreview,
                onPreviewLoadMore = ::loadMorePreview,
                onClosePreview = ::closePreview,
                onConfirm = ::confirmSelection,
                isConfirming = isConfirming,
            )
        }
    }

    private var cameraLauncher: com.github.sceneren.album.api.AlbumCameraLauncher? = null

    override fun onResume() {
        super.onResume()
        if (!isPhotoPickerInFlight) refreshContent()
    }

    override fun onDestroy() {
        previewLoadJob?.cancel()
        messageToast?.cancel()
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

    private fun showMessage(message: CharSequence) {
        messageToast?.cancel()
        messageToast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).also(Toast::show)
    }

    private fun launchPhotoPicker() {
        val launcher = photoPicker ?: return
        isPhotoPickerInFlight = true
        try {
            launcher.launch()
        } catch (failure: RuntimeException) {
            isPhotoPickerInFlight = false
            throw failure
        }
    }

    private fun toggleMedia(media: AlbumMedia) {
        if (
            media.uri !in currentSession().selectedUris &&
            currentSession().selectedItems.size >= config.maxSelectionCount
        ) {
            showSelectionLimitMessage()
            return
        }
        lifecycleScope.launch {
            client.toggleSelection(currentSession().sessionId, media).onSuccess { updated ->
                renderSession(updated)
                maybeAutoConfirm()
            }.onFailure { failure ->
                showMessage(failure.message ?: getString(R.string.auc_selection_failed))
            }
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
        if (isConfirming) return
        if (currentSession().selectedItems.isEmpty()) {
            showMessage(getString(R.string.auc_select_first))
            return
        }
        isConfirming = true
        lifecycleScope.launch {
            client.confirm(currentSession().sessionId).onSuccess { result ->
                setResult(Activity.RESULT_OK, AlbumPickerIntentCodec.putResult(Intent(), result))
                finish()
            }.onFailure { failure ->
                isConfirming = false
                showMessage(
                    getString(
                        R.string.auc_process_failed,
                        failure.message ?: getString(R.string.auc_retry),
                    ),
                )
            }
        }
    }

    private fun showSelectionLimitMessage() {
        val message = when (config.mediaFilter) {
            AlbumMediaFilter.IMAGES -> R.string.auc_selection_limit_images
            AlbumMediaFilter.VIDEOS -> R.string.auc_selection_limit_videos
            AlbumMediaFilter.IMAGES_AND_VIDEOS -> R.string.auc_selection_limit_files
        }
        showMessage(getString(message, config.maxSelectionCount))
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
                showMessage(failure.message ?: getString(R.string.auc_preview_load_failed))
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

internal fun selectedTitleDirectory(
    accessStatus: MediaAccessStatus,
    bucketId: Long,
    directories: List<AlbumDirectory>,
): AlbumDirectory? {
    if (accessStatus != MediaAccessStatus.FULL || bucketId == AlbumDirectory.ALL_BUCKET_ID) {
        return null
    }
    return directories.firstOrNull { it.bucketId == bucketId }
}

internal fun shouldUpdateDirectory(currentBucketId: Long, targetBucketId: Long): Boolean =
    currentBucketId != targetBucketId

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
    isConfirming: Boolean,
) {
    val toolbar = appearance.toolbarColor?.toColor() ?: colorResource(R.color.auc_toolbar)
    val bottom = appearance.bottomBarColor?.toColor() ?: colorResource(R.color.auc_bottom)
    val accent = appearance.accentColor?.toColor() ?: colorResource(R.color.auc_accent)
    val primary = appearance.primaryTextColor?.toColor() ?: colorResource(R.color.auc_primary)
    val titleDirectory = selectedTitleDirectory(accessStatus, session.bucketId, directories)
    val directoryName = titleDirectory?.bucketName
    val title = when {
        titleDirectory == null -> stringResource(R.string.auc_title)
        !directoryName.isNullOrBlank() -> directoryName
        else -> stringResource(R.string.auc_unnamed_directory, titleDirectory.mediaCount)
    }
    var directoryMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = directoryMenu) { directoryMenu = false }
    LaunchedEffect(accessStatus) {
        if (accessStatus != MediaAccessStatus.FULL) directoryMenu = false
    }
    val pagingItems: LazyPagingItems<AlbumMedia>? = feed?.pagingData?.collectAsLazyPagingItems()
    val selectionLimitReached = session.selectedItems.size >= config.maxSelectionCount

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(toolbar)
                        .statusBarsPadding()
                        .height(dimensionResource(R.dimen.auc_toolbar_height))
                        .padding(
                            horizontal = dimensionResource(R.dimen.auc_toolbar_padding_horizontal),
                            vertical = dimensionResource(R.dimen.auc_toolbar_padding_vertical),
                        ),
                ) {
                    IconButton(
                        onClick = onBack,
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = dimensionResource(
                                    R.dimen.auc_toolbar_title_margin_horizontal,
                                ),
                            )
                            .align(Alignment.Center),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimensionResource(R.dimen.auc_toolbar_back_size))
                                .clickable(enabled = accessStatus == MediaAccessStatus.FULL) {
                                    directoryMenu = !directoryMenu
                                },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                color = primary,
                                textAlign = TextAlign.Center,
                                fontSize = dimensionSp(R.dimen.auc_toolbar_title_text_size),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (accessStatus == MediaAccessStatus.FULL) {
                                Icon(
                                    painter = painterResource(
                                        appearance.folderIconRes
                                            ?: R.drawable.auc_ic_album_expand_more,
                                    ),
                                    contentDescription = null,
                                    tint = if (appearance.folderIconRes == null) {
                                        primary
                                    } else {
                                        Color.Unspecified
                                    },
                                    modifier = Modifier
                                        .padding(
                                            start = dimensionResource(
                                                R.dimen.auc_toolbar_title_arrow_margin_start,
                                            ),
                                        )
                                        .size(
                                            dimensionResource(
                                                R.dimen.auc_toolbar_title_arrow_size,
                                            ),
                                        )
                                        .graphicsLayer {
                                            rotationZ = if (directoryMenu) 180f else 0f
                                        },
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .height(dimensionResource(R.dimen.auc_toolbar_back_size))
                            .clickable(onClick = onBack)
                            .padding(
                                start = dimensionResource(
                                    R.dimen.auc_toolbar_cancel_padding_start,
                                ),
                                end = dimensionResource(
                                    R.dimen.auc_toolbar_cancel_padding_end,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.auc_cancel),
                            color = primary,
                            fontSize = dimensionSp(R.dimen.auc_toolbar_cancel_text_size),
                        )
                    }
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
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimensionResource(R.dimen.auc_permission_height))
                                .padding(dimensionResource(R.dimen.auc_permission_inset)),
                            shape = RoundedCornerShape(
                                dimensionResource(R.dimen.auc_permission_corner_radius),
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(R.color.auc_permission_background),
                                contentColor = colorResource(R.color.auc_permission_text),
                            ),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = if (accessStatus == MediaAccessStatus.PARTIAL) {
                                    stringResource(R.string.auc_partial_permission)
                                } else {
                                    stringResource(R.string.auc_denied_permission)
                                },
                                fontSize = dimensionSp(R.dimen.auc_permission_text_size),
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(dimensionResource(R.dimen.auc_bottom_height))
                            .padding(
                                top = dimensionResource(R.dimen.auc_toolbar_padding_vertical),
                                bottom = dimensionResource(R.dimen.auc_toolbar_padding_vertical),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onSelectedPreview,
                            modifier = Modifier
                                .height(dimensionResource(R.dimen.auc_bottom_button_height))
                                .defaultMinSize(
                                    minWidth = dimensionResource(
                                        R.dimen.auc_bottom_min_button_width,
                                    ),
                                ),
                            shape = RectangleShape,
                            contentPadding = PaddingValues(
                                horizontal = dimensionResource(
                                    R.dimen.auc_bottom_padding_horizontal,
                                ),
                            ),
                        ) {
                            Text(
                                text = if (session.selectedItems.isEmpty()) {
                                    stringResource(R.string.auc_preview)
                                } else {
                                    stringResource(
                                        R.string.auc_preview_count,
                                        session.selectedItems.size,
                                    )
                                },
                                color = primary,
                                fontSize = dimensionSp(R.dimen.auc_bottom_done_text_size),
                            )
                        }
                        Spacer(Modifier.weight(1f))
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
                columns = GridCells.Fixed(appearance.gridSpanCount),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(appearance.gridItemSpacingDp.dp),
                horizontalArrangement = Arrangement.spacedBy(appearance.gridItemSpacingDp.dp),
            ) {
                if (config.camera.enabled) {
                    item(key = "action_camera") {
                        ActionTile(
                            label = stringResource(R.string.auc_capture),
                            customIconRes = appearance.cameraIconRes,
                            defaultIconRes = R.drawable.auc_ic_album_camera,
                            appearance = appearance,
                            onClick = onCamera,
                        )
                    }
                }
                if (accessStatus != MediaAccessStatus.FULL) {
                    item(key = "action_add") {
                        ActionTile(
                            label = stringResource(R.string.auc_add_more),
                            customIconRes = appearance.addIconRes,
                            defaultIconRes = R.drawable.auc_ic_album_add,
                            appearance = appearance,
                            onClick = onAddMore,
                        )
                    }
                }
                if (accessStatus == MediaAccessStatus.FULL) {
                    items(session.cameraItems, key = { "camera:${it.uri}" }) { item ->
                        MediaTile(
                            item,
                            item.uri in session.selectedUris,
                            selectionLimitReached && item.uri !in session.selectedUris,
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
                        key = pagingItems.itemKey { it.uri.toString() },
                        contentType = pagingItems.itemContentType { "media" },
                    ) { index ->
                        pagingItems[index]?.let { item ->
                            MediaTile(
                                item,
                                item.uri in session.selectedUris,
                                selectionLimitReached && item.uri !in session.selectedUris,
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
                                .height(dimensionResource(R.dimen.auc_loading_box_height)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = accent)
                        }
                    }
                }
            }
        }

        val directoryVisible = directoryMenu && accessStatus == MediaAccessStatus.FULL
        val directoryPanelMaxHeight = maxHeight * DIRECTORY_PANEL_MAX_HEIGHT_RATIO
        val directoryPanelTransition = updateTransition(
            targetState = directoryVisible,
            label = "directoryPanel",
        )
        val directoryPanelAlpha by directoryPanelTransition.animateFloat(
            transitionSpec = { tween(DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS) },
            label = "directoryPanelAlpha",
        ) { visible ->
            if (visible) 1f else 0f
        }
        val directoryPanelScaleY by directoryPanelTransition.animateFloat(
            transitionSpec = { tween(DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS) },
            label = "directoryPanelScaleY",
        ) { visible ->
            if (visible) 1f else 0f
        }
        AnimatedVisibility(
            visible = directoryVisible,
            enter = fadeIn(animationSpec = tween(DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS)),
            exit = fadeOut(animationSpec = tween(DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = dimensionResource(R.dimen.auc_toolbar_height))
                    .background(colorResource(R.color.auc_directory_scrim))
                    .clickable { directoryMenu = false },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = dimensionResource(R.dimen.auc_toolbar_height)),
        ) {
            if (directoryPanelTransition.currentState || directoryPanelTransition.targetState) {
                DirectoryPanel(
                    directories = directories,
                    selectedBucketId = session.bucketId,
                    imageLoader = imageLoader,
                    maxHeight = directoryPanelMaxHeight,
                    onDirectory = { bucketId ->
                        directoryMenu = false
                        if (shouldUpdateDirectory(session.bucketId, bucketId)) {
                            onDirectory(bucketId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = directoryPanelAlpha
                            scaleY = directoryPanelScaleY
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        },
                )
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
        if (isConfirming) ProcessingOverlay()
    }
}

@Composable
private fun DirectoryPanel(
    directories: List<AlbumDirectory>,
    selectedBucketId: Long,
    imageLoader: AlbumImageLoader,
    maxHeight: Dp,
    onDirectory: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(
        bottomStart = dimensionResource(R.dimen.auc_directory_corner_radius),
        bottomEnd = dimensionResource(R.dimen.auc_directory_corner_radius),
    )
    LazyColumn(
        modifier = modifier
            .heightIn(max = maxHeight)
            .background(colorResource(R.color.auc_directory_panel), panelShape),
    ) {
        lazyListItems(
            items = directories,
            key = AlbumDirectory::bucketId,
        ) { directory ->
            DirectoryRow(
                directory = directory,
                selected = directory.bucketId == selectedBucketId,
                imageLoader = imageLoader,
                onClick = { onDirectory(directory.bucketId) },
            )
        }
    }
}

@Composable
private fun DirectoryRow(
    directory: AlbumDirectory,
    selected: Boolean,
    imageLoader: AlbumImageLoader,
    onClick: () -> Unit,
) {
    val name = if (directory.bucketId == AlbumDirectory.ALL_BUCKET_ID) {
        stringResource(R.string.auc_all_media)
    } else {
        directory.bucketName?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.auc_unnamed_directory_name)
    }
    val label = stringResource(R.string.auc_directory_label, name, directory.mediaCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.auc_directory_row_height))
            .background(
                color = if (selected) {
                    colorResource(R.color.auc_directory_selected)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(
                    dimensionResource(R.dimen.auc_directory_corner_radius),
                ),
            )
            .clickable(onClick = onClick)
            .padding(dimensionResource(R.dimen.auc_directory_row_padding)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = imageLoader.painter(
                directory.toCoverMedia(),
                AlbumImageTarget.GRID_THUMBNAIL,
            ),
            contentDescription = null,
            modifier = Modifier.size(dimensionResource(R.dimen.auc_directory_cover_size)),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = label,
            color = colorResource(R.color.auc_directory_text),
            fontSize = dimensionSp(R.dimen.auc_directory_text_size),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = dimensionResource(R.dimen.auc_directory_text_margin_start)),
        )
    }
}

private const val DIRECTORY_PANEL_MAX_HEIGHT_RATIO = 0.6f
private const val DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS = 200

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
    val toolbar = appearance.toolbarColor?.toColor() ?: colorResource(R.color.auc_toolbar)
    val bottom = appearance.bottomBarColor?.toColor() ?: colorResource(R.color.auc_bottom)
    val previewBackground = appearance.previewBackgroundColor?.toColor()
        ?: colorResource(android.R.color.black)
    val primary = appearance.primaryTextColor?.toColor() ?: colorResource(R.color.auc_primary)
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
                .height(dimensionResource(R.dimen.auc_toolbar_height))
                .padding(
                    horizontal = dimensionResource(R.dimen.auc_toolbar_padding_horizontal),
                    vertical = dimensionResource(R.dimen.auc_toolbar_padding_vertical),
                ),
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
                fontSize = dimensionSp(R.dimen.auc_preview_page_counter_text_size),
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
                    modifier = Modifier.size(
                        dimensionResource(R.dimen.auc_preview_selection_icon_size),
                    ),
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
                            .size(dimensionResource(R.dimen.auc_preview_video_play_size))
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
                .height(dimensionResource(R.dimen.auc_bottom_height))
                .padding(
                    start = dimensionResource(R.dimen.auc_toolbar_padding_horizontal),
                    top = dimensionResource(R.dimen.auc_toolbar_padding_vertical),
                    end = dimensionResource(R.dimen.auc_toolbar_padding_horizontal),
                    bottom = dimensionResource(R.dimen.auc_toolbar_padding_vertical),
                ),
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
    val accent = appearance.accentColor?.toColor() ?: colorResource(R.color.auc_accent)
    val primary = appearance.primaryTextColor?.toColor() ?: colorResource(R.color.auc_primary)
    val secondary = appearance.secondaryTextColor?.toColor() ?: colorResource(R.color.auc_secondary)
    val hasSelection = selectedCount > 0

    Row(
        modifier = modifier
            .height(dimensionResource(R.dimen.auc_bottom_button_height))
            .clickable(enabled = hasSelection, onClick = onConfirm)
            .padding(
                start = dimensionResource(R.dimen.auc_preview_done_action_padding_start),
                end = dimensionResource(R.dimen.auc_preview_done_action_padding_end),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasSelection) {
            Box(
                modifier = Modifier
                    .background(accent, CircleShape)
                    .height(dimensionResource(R.dimen.auc_preview_selected_count_size))
                    .widthIn(min = dimensionResource(R.dimen.auc_preview_selected_count_size)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = selectedCount.toString(),
                    color = primary,
                    fontSize = dimensionSp(R.dimen.auc_preview_count_text_size),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (hasSelection && appearance.doneIconRes != null) {
            Spacer(Modifier.width(dimensionResource(R.dimen.auc_preview_done_icon_gap)))
            Icon(
                painter = painterResource(appearance.doneIconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .height(dimensionResource(R.dimen.auc_preview_selected_count_size))
                    .wrapContentHeight()
                    .widthIn(min = dimensionResource(R.dimen.auc_preview_selected_count_size)),
            )
        }
        Spacer(
            Modifier.width(
                dimensionResource(R.dimen.auc_preview_done_text_margin_start),
            ),
        )
        Text(
            text = if (hasSelection) {
                stringResource(R.string.auc_done)
            } else {
                stringResource(R.string.auc_please_select)
            },
            color = if (hasSelection) accent else secondary,
            fontSize = dimensionSp(R.dimen.auc_bottom_done_text_size),
        )
    }
}

@Composable
private fun ActionTile(
    label: String,
    customIconRes: Int?,
    defaultIconRes: Int,
    appearance: AlbumPickerAppearance,
    onClick: () -> Unit,
) {
    val primary = appearance.primaryTextColor?.toColor() ?: colorResource(R.color.auc_primary)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(colorResource(R.color.auc_action))
            .clickable(onClick = onClick)
            .padding(dimensionResource(R.dimen.auc_action_padding)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(customIconRes ?: defaultIconRes),
            contentDescription = null,
            tint = if (customIconRes == null) primary else Color.Unspecified,
            modifier = Modifier.size(dimensionResource(R.dimen.auc_action_icon_size)),
        )
        Spacer(Modifier.height(dimensionResource(R.dimen.auc_action_label_margin_top)))
        Text(
            text = label,
            color = primary,
            fontSize = dimensionSp(R.dimen.auc_action_label_text_size),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MediaTile(
    media: AlbumMedia,
    selected: Boolean,
    selectionBlocked: Boolean,
    appearance: AlbumPickerAppearance,
    imageLoader: AlbumImageLoader,
    onPreview: (AlbumMedia) -> Unit,
    onToggle: (AlbumMedia) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.Black)
            .clickable(enabled = !selectionBlocked) { onPreview(media) },
    ) {
        MediaThumbnail(media, imageLoader)
        if (selected || selectionBlocked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (selected) {
                            appearance.scrimColor?.toColor()
                                ?: colorResource(R.color.auc_media_selected_scrim)
                        } else {
                            colorResource(R.color.auc_media_blocked_scrim)
                        },
                    ),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(dimensionResource(R.dimen.auc_media_check_size))
                .padding(dimensionResource(R.dimen.auc_media_check_margin))
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

@Composable
private fun MediaThumbnail(
    media: AlbumMedia,
    imageLoader: AlbumImageLoader,
) {
    Image(
        painter = imageLoader.painter(media, AlbumImageTarget.GRID_THUMBNAIL),
        contentDescription = media.displayName,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun ProcessingOverlay() {
    BackHandler { }
    val interactionSource = remember { MutableInteractionSource() }
    val spinnerTransition = rememberInfiniteTransition(label = "processingSpinner")
    val spinnerRotation by spinnerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PROCESSING_SPINNER_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        ),
        label = "processingSpinnerRotation",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.auc_processing_screen_scrim))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(R.dimen.auc_processing_dialog_size))
                .background(
                    colorResource(R.color.auc_processing_dialog_background),
                    RoundedCornerShape(
                        dimensionResource(R.dimen.auc_processing_dialog_corner_radius),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.auc_progress_spinner),
                contentDescription = stringResource(R.string.auc_processing),
                modifier = Modifier
                    .size(dimensionResource(R.dimen.auc_processing_indicator_size))
                    .rotate(spinnerRotation),
            )
        }
    }
}

/** Converts an XML sp resource to Compose sp without applying the font scale twice. */
@Composable
private fun dimensionSp(@DimenRes resourceId: Int): TextUnit {
    val dimension = dimensionResource(resourceId)
    return (dimension.value / LocalDensity.current.fontScale).sp
}

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
private const val PROCESSING_SPINNER_DURATION_MILLIS = 1_000
