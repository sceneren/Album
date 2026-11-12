package com.github.sceneren.album.ui.compose

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DimenRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumCameraPermissionRequestFactory
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFeed
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaPermissionRequestFactory
import com.github.sceneren.album.api.AlbumMediaSpecialFormat
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerResult
import com.github.sceneren.album.api.AlbumPickerSessionSnapshot
import com.github.sceneren.album.api.MediaAccessStatus
import com.github.sceneren.album.api.PhotoPickResult
import com.github.sceneren.album.api.SingleSelectionFinishMode
import com.github.sceneren.album.video.AlbumVideoPlayerFactory
import com.github.sceneren.album.ui.compose.internal.video.VideoPreviewSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import androidx.compose.foundation.lazy.items as lazyListItems

/**
 * Compose 相册选择器打开和关闭时使用的动画。
 *
 * [enterTransition] 和 [exitTransition] 可使用 Compose Animation 提供的任意过渡动画，
 * 包括通过 `+` 组合的滑动、淡入淡出和缩放动画。
 */
data class AlbumPickerAnimation(
    /** 选择器加入组合时播放的动画。 */
    val enterTransition: EnterTransition = defaultPickerEnterTransition(),
    /** 选择器退出组合前播放的动画。 */
    val exitTransition: ExitTransition = defaultPickerExitTransition(),
) {
    /** 提供默认动画和无动画预置配置。 */
    companion object {
        /** 默认的底部滑入和滑出动画。 */
        val Default: AlbumPickerAnimation = AlbumPickerAnimation()

        /** 不播放打开和关闭动画。 */
        val None: AlbumPickerAnimation = AlbumPickerAnimation(
            enterTransition = EnterTransition.None,
            exitTransition = ExitTransition.None,
        )
    }
}

/**
 * 完整的 Compose 相册页面，包含标题栏、内容区、底部确认栏及页面转场。
 *
 * 宿主负责导航，并应在退出动画完成后的 [onResult] 或 [onCancel] 回调中移除此组件。
 * [animation] 为 null 或 [AlbumPickerAnimation.None] 时不播放打开和关闭动画。
 * [imageLoader] 为 null 时，使用通过 [AlbumUi] 配置的进程级图片加载器。
 * [videoPlayerFactory] 默认使用 [AlbumUi] 的可选配置；显式传 null 时只显示静态视频封面。
 * [systemBarStyle] 控制系统栏颜色和图标明暗；null 表示完全由宿主管理。
 * 需要嵌入已有页面时使用 [AlbumPickerContent]。
 */
@Composable
fun AlbumPicker(
    config: AlbumPickerConfig,
    onResult: (AlbumPickerResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    appearance: AlbumPickerAppearance = AlbumPickerAppearance(),
    imageLoader: AlbumImageLoader? = null,
    animation: AlbumPickerAnimation? = AlbumPickerAnimation.Default,
    systemBarStyle: AlbumPickerSystemBarStyle? = AlbumPickerSystemBarStyle(),
    videoPlayerFactory: AlbumVideoPlayerFactory? = AlbumUi.videoPlayerFactoryOrNull(),
) {
    key(config) {
        AlbumPickerHost(
            config = config,
            onResult = onResult,
            onCancel = onCancel,
            modifier = modifier,
            appearance = appearance,
            imageLoader = imageLoader,
            videoPlayerFactory = videoPlayerFactory,
            animation = animation,
            systemBarStyle = systemBarStyle,
        )
    }
}

/** 两种展示模式共用同一选择、权限、拍摄及结果处理流程。 */
@Composable
internal fun AlbumPickerHost(
    config: AlbumPickerConfig,
    onResult: (AlbumPickerResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
    appearance: AlbumPickerAppearance,
    imageLoader: AlbumImageLoader?,
    animation: AlbumPickerAnimation?,
    embedded: Boolean = false,
    state: AlbumPickerState? = null,
    systemBarStyle: AlbumPickerSystemBarStyle? = null,
    videoPlayerFactory: AlbumVideoPlayerFactory? = null,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val toolbarColor = appearance.toolbarColor ?: colorResource(R.color.auc_toolbar).toArgb()
    val bottomBarColor = appearance.bottomBarColor ?: colorResource(R.color.auc_bottom).toArgb()
    if (!embedded && systemBarStyle != null) {
        AlbumPickerSystemBars(
            statusBarColor = systemBarStyle.statusBarColor ?: toolbarColor,
            navigationBarColor = systemBarStyle.navigationBarColor ?: bottomBarColor,
            darkStatusBarIcons = systemBarStyle.darkStatusBarIcons,
            darkNavigationBarIcons = systemBarStyle.darkNavigationBarIcons,
            navigationBarContrastEnforced = systemBarStyle.navigationBarContrastEnforced,
        )
    }
    val areMediaPermissionsDeclared = remember(applicationContext, config.mediaFilter) {
        AlbumMediaPermissionRequestFactory.areDeclaredInManifest(
            context = applicationContext,
            filter = config.mediaFilter,
        )
    }
    val api = remember(applicationContext) { AlbumApi.create(applicationContext) }
    val mediaChangeTracker = remember(api, config.mediaFilter) {
        api.createMediaChangeTracker(config.mediaFilter)
    }
    val client = remember(api, applicationContext) { api.createPickerClient(applicationContext) }
    val sessionId = rememberSaveable(config) { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val resolvedImageLoader = remember(imageLoader) {
        imageLoader ?: AlbumUi.requireImageLoader()
    }
    val visibilityState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    var session by remember(api, client, config, sessionId) {
        mutableStateOf(client.openSession(config, sessionId))
    }
    var accessStatus by remember { mutableStateOf(MediaAccessStatus.DENIED) }
    var feed by remember { mutableStateOf<AlbumMediaFeed?>(null) }
    val pagingItems = feed?.pagingData?.collectAsLazyPagingItems()
    // The refresh consumer outlives recompositions, including the first null feed.
    val currentPagingItems by rememberUpdatedState(pagingItems)
    var activeFeedSignature by remember { mutableStateOf<ComposeFeedSignature?>(null) }
    var directories by remember { mutableStateOf<List<AlbumDirectory>>(emptyList()) }
    var preview by remember { mutableStateOf<PreviewState?>(null) }
    var isConfirming by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var pendingCompletion by remember { mutableStateOf<PickerCompletion?>(null) }
    val refreshRequests = remember(config, sessionId) {
        Channel<Unit>(Channel.CONFLATED).apply { trySend(Unit) }
    }
    var isExternalLauncherInFlight by remember { mutableStateOf(false) }
    var isCameraPermissionRequestInFlight by remember { mutableStateOf(false) }
    var pendingCameraMediaType by rememberSaveable { mutableStateOf<AlbumMediaType?>(null) }
    var previewLoadJob by remember { mutableStateOf<Job?>(null) }
    var messageToast by remember { mutableStateOf<Toast?>(null) }

    /** 执行 `showMessage` 方法定义的处理。 */
    fun showMessage(message: CharSequence) {
        messageToast?.cancel()
        messageToast = AlbumUi.showToast(context, message)
    }

    /** 更新 `refreshContentNow` 对应的状态。 */
    suspend fun refreshContentNow() {
        if (isConfirming || isCancelling || isFinished) return
        val mediaChanged = mediaChangeTracker.hasChanges().getOrDefault(false)
        // Confirmation/cancellation can remove the session while the provider check is suspended.
        if (isConfirming || isCancelling || isFinished) return
        val previousAccessStatus = accessStatus
        val latestAccessStatus = api.getMediaAccessStatus(config.mediaFilter)
        val accessChanged = previousAccessStatus != latestAccessStatus
        accessStatus = latestAccessStatus
        val current = client.snapshot(session.sessionId)
        session = current
        val targetSignature = ComposeFeedSignature(
            mediaFilter = config.mediaFilter,
            bucketId = current.bucketId,
            accessStatus = latestAccessStatus,
        )
        val shouldReplaceFeed = feed == null || activeFeedSignature != targetSignature
        if (shouldReplaceFeed) {
            // A directory/permission change needs a new route, not a refresh of the old source.
            activeFeedSignature = targetSignature
            feed = api.getMediaFeed(config.mediaFilter, current.bucketId)
        } else if (mediaChanged) {
            // Keep the presenter and its snapshot while the new generation loads. Replacing
            // the Flow here clears every visible cell on return from the system picker.
            currentPagingItems?.refresh()
        }
        directories = if (latestAccessStatus == MediaAccessStatus.FULL) {
            if (mediaChanged || shouldReplaceFeed || accessChanged) {
                api.getMediaDirectories(config.mediaFilter).getOrNull().orEmpty()
            } else {
                directories
            }
        } else {
            emptyList()
        }
    }

    /** 更新 `refreshContent` 对应的状态。 */
    fun refreshContent() {
        refreshRequests.trySend(Unit)
    }

    /** 执行 `confirmSelection` 方法定义的处理。 */
    fun confirmSelection() {
        if (isConfirming || isCancelling || isFinished) return
        if (session.selectedItems.isEmpty()) {
            showMessage(applicationContext.getString(R.string.auc_select_first))
            return
        }
        isConfirming = true
        scope.launch {
            client.confirm(session.sessionId).onSuccess { result ->
                pendingCompletion = PickerCompletion.Result(result)
                isFinished = true
                visibilityState.targetState = false
            }.onFailure { failure ->
                isConfirming = false
                showMessage(
                    applicationContext.getString(
                        R.string.auc_process_failed,
                        failure.message ?: applicationContext.getString(R.string.auc_retry),
                    ),
                )
                refreshContent()
            }
        }
    }

    /** 执行 `handlePhotoPickerResult` 方法定义的处理。 */
    fun handlePhotoPickerResult(uris: List<Uri>) {
        scope.launch {
            try {
                val result = api.processPhotoPickerResult(
                    uris = uris,
                    mediaFilter = config.mediaFilter,
                    maxSelectionCount = null,
                )
                if (result is PhotoPickResult.Failed) {
                    showMessage(applicationContext.getString(R.string.auc_selection_failed))
                }
            } finally {
                isExternalLauncherInFlight = false
                refreshContent()
            }
        }
    }

    val photoPickerRequest = remember(config.mediaFilter) {
        PickVisualMediaRequest.Builder()
            .setMediaType(config.mediaFilter.toVisualMediaType())
            .build()
    }
    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        handlePhotoPickerResult(uris)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        scope.launch {
            try {
                api.syncPartialSelections(config.mediaFilter)
                refreshContent()
            } finally {
                isExternalLauncherInFlight = false
            }
        }
    }
    val photoCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        scope.launch {
            try {
                client.completeCamera(session.sessionId, success)
                    .onSuccess { updated ->
                        session = updated
                        if (accessStatus != MediaAccessStatus.FULL) {
                            refreshContent()
                        }
                        maybeAutoConfirm(config, updated, ::confirmSelection)
                    }
                    .onFailure { failure ->
                        showMessage(
                            failure.message
                                ?: applicationContext.getString(R.string.auc_camera_failed),
                        )
                    }
            } finally {
                isExternalLauncherInFlight = false
            }
        }
    }
    val videoCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { success ->
        scope.launch {
            try {
                client.completeCamera(session.sessionId, success)
                    .onSuccess { updated ->
                        session = updated
                        if (accessStatus != MediaAccessStatus.FULL) {
                            refreshContent()
                        }
                        maybeAutoConfirm(config, updated, ::confirmSelection)
                    }
                    .onFailure { failure ->
                        showMessage(
                            failure.message
                                ?: applicationContext.getString(R.string.auc_camera_failed),
                        )
                    }
            } finally {
                isExternalLauncherInFlight = false
            }
        }
    }

    /** 在无需请求权限或已获授权后准备输出文件并启动系统相机。 */
    fun launchPreparedCamera(mediaType: AlbumMediaType) {
        scope.launch {
            client.prepareCamera(session.sessionId, mediaType).onSuccess { capture ->
                isExternalLauncherInFlight = true
                try {
                    if (mediaType == AlbumMediaType.IMAGE) {
                        photoCameraLauncher.launch(capture.uri)
                    } else {
                        videoCameraLauncher.launch(capture.uri)
                    }
                } catch (failure: RuntimeException) {
                    isExternalLauncherInFlight = false
                    client.completeCamera(session.sessionId, success = false).onSuccess { session = it }
                    showMessage(
                        if (failure is ActivityNotFoundException) {
                            applicationContext.getString(R.string.auc_camera_unavailable)
                        } else {
                            failure.message ?: applicationContext.getString(R.string.auc_camera_failed)
                        },
                    )
                }
            }.onFailure { failure ->
                showMessage(
                    failure.message ?: applicationContext.getString(R.string.auc_camera_failed),
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val mediaType = pendingCameraMediaType
        pendingCameraMediaType = null
        isCameraPermissionRequestInFlight = false
        if (isGranted && mediaType != null) launchPreparedCamera(mediaType)
    }

    /** 执行 `launchPhotoPicker` 方法定义的处理。 */
    fun launchPhotoPicker() {
        isExternalLauncherInFlight = true
        try {
            multiplePhotoPickerLauncher.launch(photoPickerRequest)
        } catch (failure: RuntimeException) {
            isExternalLauncherInFlight = false
            throw failure
        }
    }

    /** 执行 `launchCamera` 方法定义的处理。 */
    fun launchCamera() {
        if (isCameraPermissionRequestInFlight) return
        val mediaType = config.cameraMediaType()
        if (AlbumCameraPermissionRequestFactory.shouldRequest(applicationContext)) {
            pendingCameraMediaType = mediaType
            isCameraPermissionRequestInFlight = true
            try {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (failure: RuntimeException) {
                pendingCameraMediaType = null
                isCameraPermissionRequestInFlight = false
                throw failure
            }
            return
        }
        launchPreparedCamera(mediaType)
    }

    /** 执行 `showSelectionLimitMessage` 方法定义的处理。 */
    fun showSelectionLimitMessage() {
        val message = when (config.mediaFilter) {
            AlbumMediaFilter.IMAGES -> R.string.auc_selection_limit_images
            AlbumMediaFilter.VIDEOS -> R.string.auc_selection_limit_videos
            AlbumMediaFilter.IMAGES_AND_VIDEOS -> R.string.auc_selection_limit_files
        }
        showMessage(applicationContext.getString(message, config.maxSelectionCount))
    }

    /** 更新 `toggleMedia` 对应的状态。 */
    fun toggleMedia(media: AlbumMedia) {
        if (
            media.uri !in session.selectedUris &&
            session.selectedItems.size >= config.maxSelectionCount
        ) {
            showSelectionLimitMessage()
            return
        }
        scope.launch {
            client.toggleSelection(session.sessionId, media).onSuccess { updated ->
                session = updated
                maybeAutoConfirm(config, updated, ::confirmSelection)
            }.onFailure { failure ->
                showMessage(
                    failure.message ?: applicationContext.getString(R.string.auc_selection_failed),
                )
            }
        }
    }

    /** 执行 `showGridPreview` 方法定义的处理。 */
    fun showGridPreview(media: AlbumMedia, loadedFeedItems: List<AlbumMedia>) {
        val cameraItems = if (accessStatus == MediaAccessStatus.FULL) {
            session.cameraItems
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

    /** 执行 `showSelectedPreview` 方法定义的处理。 */
    fun showSelectedPreview() {
        val items = session.selectedItems
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

    /** 获取 `loadMorePreview` 所需的数据。 */
    fun loadMorePreview() {
        val current = preview ?: return
        val offset = current.nextOffset ?: return
        if (current.loading || current.endReached) return
        preview = current.copy(loading = true)
        previewLoadJob = scope.launch {
            api.loadMediaPage(
                mediaFilter = config.mediaFilter,
                bucketId = session.bucketId,
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
            }.onFailure {
                val latest = preview?.takeIf { it.id == current.id } ?: return@onFailure
                preview = latest.copy(loading = false, nextOffset = null, endReached = true)
            }
        }
    }

    /** 执行 `closePreview` 方法定义的处理。 */
    fun closePreview() {
        previewLoadJob?.cancel()
        previewLoadJob = null
        preview = null
    }

    /** 判断 `cancelPicker` 条件是否成立。 */
    fun cancelPicker() {
        if (isCancelling || isConfirming || isFinished) return
        if (preview != null) {
            closePreview()
            return
        }
        isCancelling = true
        // Navigation must not queue behind provider image IO. Close immediately; finish
        // private session cleanup independently even when the host removes this composition.
        pendingCompletion = PickerCompletion.Cancelled
        isFinished = true
        visibilityState.targetState = false
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { client.cancel(session.sessionId) }
            }
        }
    }

    DisposableEffect(lifecycleOwner, config, sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_RESUME &&
                !isExternalLauncherInFlight &&
                !isCameraPermissionRequestInFlight &&
                !isFinished
            ) {
                refreshContent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(api, lifecycleOwner, config, sessionId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            api.observeMediaChanges().collect {
                if (!isExternalLauncherInFlight && !isCameraPermissionRequestInFlight && !isFinished) {
                    refreshContent()
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            previewLoadJob?.cancel()
            messageToast?.cancel()
        }
    }
    val stateOwner = remember { Any() }
    DisposableEffect(state, stateOwner) {
        state?.attach(stateOwner)
        onDispose { state?.detach(stateOwner) }
    }
    SideEffect {
        state?.update(
            token = stateOwner,
            selected = session.selectedItems,
            processing = isConfirming || isCancelling || isFinished,
            callbacks = AlbumPickerState.Actions(
                confirm = ::confirmSelection,
                preview = ::showSelectedPreview,
                cancel = ::cancelPicker,
                refresh = ::refreshContent,
            ),
        )
    }
    val occupiesInput = !isFinished || visibilityState.currentState ||
        visibilityState.targetState || !visibilityState.isIdle
    BackHandler(enabled = occupiesInput && (!embedded || preview != null), onBack = ::cancelPicker)
    LaunchedEffect(refreshRequests) {
        for (request in refreshRequests) {
            if (!isFinished) {
                refreshContentNow()
            }
        }
    }
    // Do not let a child transition (e.g. a loading spinner) indefinitely delay host dismissal.
    LaunchedEffect(isFinished) {
        if (isFinished) {
            withTimeoutOrNull(1_000L) {
                snapshotFlow { !visibilityState.currentState && visibilityState.isIdle }.first { it }
            }
            when (val completion = pendingCompletion) {
                is PickerCompletion.Result -> currentOnResult(completion.value)
                PickerCompletion.Cancelled -> currentOnCancel()
                null -> Unit
            }
        }
    }

    Box(modifier.clipToBounds()) {
        if (occupiesInput) InteractionBlocker(blockBack = false)
        AnimatedVisibility(
            visibleState = visibilityState,
            modifier = Modifier.fillMaxSize(),
            enter = animation?.enterTransition ?: EnterTransition.None,
            exit = animation?.exitTransition ?: ExitTransition.None,
        ) {
            Box(Modifier.fillMaxSize()) {
                AlbumPickerScreen(
                    embedded = embedded,
                    systemBarStyle = systemBarStyle,
                    modifier = Modifier.fillMaxSize(),
                    config = config,
                    appearance = appearance,
                    session = session,
                    accessStatus = accessStatus,
                    areMediaPermissionsDeclared = areMediaPermissionsDeclared,
                    directories = directories,
                    pagingItems = pagingItems,
                    preview = preview,
                    imageLoader = resolvedImageLoader,
                    videoPlayerFactory = videoPlayerFactory,
                    onBack = ::cancelPicker,
                    onDirectory = { bucketId ->
                        if (shouldUpdateDirectory(session.bucketId, bucketId)) {
                            scope.launch {
                                client.setBucket(session.sessionId, bucketId).onSuccess { updated ->
                                    session = updated
                                    refreshContent()
                                }
                            }
                        }
                    },
                    onRequestPermission = {
                        isExternalLauncherInFlight = true
                        try {
                            permissionLauncher.launch(
                                AlbumMediaPermissionRequestFactory.create(config.mediaFilter),
                            )
                        } catch (failure: RuntimeException) {
                            isExternalLauncherInFlight = false
                            throw failure
                        }
                    },
                    onAddMore = ::launchPhotoPicker,
                    onCamera = ::launchCamera,
                    onToggle = ::toggleMedia,
                    onGridPreview = ::showGridPreview,
                    onSelectedPreview = ::showSelectedPreview,
                    onPreviewLoadMore = ::loadMorePreview,
                    onClosePreview = ::closePreview,
                    onConfirm = ::confirmSelection,
                    isConfirming = isConfirming,
                )
                if (isCancelling || isFinished) InteractionBlocker(blockBack = !embedded)
            }
        }
    }
}

private data class ComposeFeedSignature(
    val mediaFilter: AlbumMediaFilter,
    val bucketId: Long,
    val accessStatus: MediaAccessStatus,
)

private sealed interface PickerCompletion {
    data class Result(val value: AlbumPickerResult) : PickerCompletion

    data object Cancelled : PickerCompletion
}

/** 将当前对象转换为 `toVisualMediaType` 对应的结果。 */
private fun AlbumMediaFilter.toVisualMediaType() = when (this) {
    AlbumMediaFilter.IMAGES -> ActivityResultContracts.PickVisualMedia.ImageOnly
    AlbumMediaFilter.VIDEOS -> ActivityResultContracts.PickVisualMedia.VideoOnly
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
}

/** 执行 `cameraMediaType` 方法定义的处理。 */
internal fun AlbumPickerConfig.cameraMediaType() = when (mediaFilter) {
    AlbumMediaFilter.IMAGES -> AlbumMediaType.IMAGE
    AlbumMediaFilter.VIDEOS -> AlbumMediaType.VIDEO
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> if (
        camera.mixedMediaCaptureType == AlbumCameraCaptureType.PHOTO
    ) AlbumMediaType.IMAGE else AlbumMediaType.VIDEO
}

/** 执行 `maybeAutoConfirm` 方法定义的处理。 */
private fun maybeAutoConfirm(
    config: AlbumPickerConfig,
    session: AlbumPickerSessionSnapshot,
    onConfirm: () -> Unit,
) {
    if (shouldAutoConfirm(config, session)) {
        onConfirm()
    }
}

/** 判断 `shouldAutoConfirm` 条件是否成立。 */
internal fun shouldAutoConfirm(
    config: AlbumPickerConfig,
    session: AlbumPickerSessionSnapshot,
): Boolean =
    (
            config.maxSelectionCount == 1 &&
                    config.singleSelectionFinishMode == SingleSelectionFinishMode.IMMEDIATE &&
                    session.selectedItems.size == 1
            )

/** Compose 选择器与 View 选择器使用相同的打开和关闭动画时长。 */
private const val PICKER_ANIMATION_DURATION_MILLIS = 300

/** 创建与 View 选择器一致的默认底部滑入动画。 */
private fun defaultPickerEnterTransition(): EnterTransition = slideInVertically(
    animationSpec = tween(
        durationMillis = PICKER_ANIMATION_DURATION_MILLIS,
        easing = FastOutSlowInEasing,
    ),
    initialOffsetY = { fullHeight -> fullHeight },
)

/** 创建与 View 选择器一致的默认底部滑出动画。 */
private fun defaultPickerExitTransition(): ExitTransition = slideOutVertically(
    animationSpec = tween(
        durationMillis = PICKER_ANIMATION_DURATION_MILLIS,
        easing = FastOutSlowInEasing,
    ),
    targetOffsetY = { fullHeight -> fullHeight },
)

/** 执行 `selectedTitleDirectory` 方法定义的处理。 */
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

/** 判断 `shouldUpdateDirectory` 条件是否成立。 */
internal fun shouldUpdateDirectory(currentBucketId: Long, targetBucketId: Long): Boolean =
    currentBucketId != targetBucketId

/** 判断申请完整媒体权限入口是否应该显示。 */
internal fun shouldShowPermissionUpgradeButton(
    isAllowedByHost: Boolean,
    arePermissionsDeclared: Boolean,
    accessStatus: MediaAccessStatus,
): Boolean = isAllowedByHost &&
    arePermissionsDeclared &&
    accessStatus != MediaAccessStatus.FULL

/** 只有尚无可展示内容时才遮盖首刷；后台刷新保留已有网格。 */
internal fun isMediaGridInitialLoading(
    refreshLoadState: LoadState?,
    presentedItemCount: Int,
): Boolean = presentedItemCount == 0 &&
    (refreshLoadState == null || refreshLoadState is LoadState.Loading)

/** 表示 `PREVIEW_PAGE_SIZE` 对应的数据。 */
private const val PREVIEW_PAGE_SIZE = 30
/** 表示 `PREVIEW_PREFETCH_DISTANCE` 对应的数据。 */
private const val PREVIEW_PREFETCH_DISTANCE = 3

@Composable
/** 执行 `AlbumPickerScreen` 方法定义的处理。 */
internal fun AlbumPickerScreen(
    embedded: Boolean,
    systemBarStyle: AlbumPickerSystemBarStyle?,
    modifier: Modifier,
    config: AlbumPickerConfig,
    appearance: AlbumPickerAppearance,
    session: AlbumPickerSessionSnapshot,
    accessStatus: MediaAccessStatus,
    areMediaPermissionsDeclared: Boolean,
    directories: List<AlbumDirectory>,
    pagingItems: LazyPagingItems<AlbumMedia>?,
    preview: PreviewState?,
    imageLoader: AlbumImageLoader,
    videoPlayerFactory: AlbumVideoPlayerFactory?,
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
    val applicationContext = LocalContext.current.applicationContext
    val applicationName = remember(applicationContext) {
        applicationContext.applicationInfo
            .loadLabel(applicationContext.packageManager)
            .toString()
            .ifBlank { applicationContext.packageName }
    }
    val toolbar = appearance.toolbarColor?.toColor() ?: colorResource(R.color.auc_toolbar)
    val bottom = appearance.bottomBarColor?.toColor() ?: colorResource(R.color.auc_bottom)
    val primary = appearance.primaryTextColor?.toColor() ?: colorResource(R.color.auc_primary)
    val titleDirectory = selectedTitleDirectory(accessStatus, session.bucketId, directories)
    val directoryName = titleDirectory?.bucketName
    val title = when {
        titleDirectory == null -> stringResource(R.string.auc_title)
        !directoryName.isNullOrBlank() -> directoryName
        else -> stringResource(R.string.auc_unnamed_directory, titleDirectory.mediaCount)
    }
    var directoryMenu by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = directoryMenu) { directoryMenu = false }
    LaunchedEffect(accessStatus) {
        if (accessStatus != MediaAccessStatus.FULL) directoryMenu = false
    }
    val gridState = key(accessStatus, session.bucketId) { rememberLazyGridState() }
    val selectionLimitReached = session.selectedItems.size >= config.maxSelectionCount
    val isInitialGridLoading = isMediaGridInitialLoading(
        refreshLoadState = pagingItems?.loadState?.refresh,
        presentedItemCount = (pagingItems?.itemCount ?: 0) +
            if (accessStatus == MediaAccessStatus.FULL) session.cameraItems.size else 0,
    )

    AlbumPreviewLayout(modifier) { sharedTransitionScope ->
        Scaffold(
            containerColor = Color.Black,
            contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (embedded) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .height(dimensionResource(R.dimen.auc_toolbar_height))
                            .background(toolbar),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { directoryMenu = !directoryMenu },
                            enabled = accessStatus == MediaAccessStatus.FULL,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                title,
                                modifier = Modifier.weight(1f, fill = false),
                                color = primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (accessStatus == MediaAccessStatus.FULL) {
                                Icon(
                                    painter = painterResource(
                                        appearance.folderIconRes ?: R.drawable.auc_ic_album_expand_more,
                                    ),
                                    contentDescription = null,
                                    tint = if (appearance.folderIconRes == null) primary else Color.Unspecified,
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
                        if (shouldShowPermissionUpgradeButton(
                                config.showPermissionUpgrade, areMediaPermissionsDeclared, accessStatus,
                            )) {
                            TextButton(onClick = onRequestPermission) {
                                Text(stringResource(R.string.auc_manage_access), color = primary)
                            }
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(systemBarStyle?.statusBarColor?.toColor() ?: toolbar)
                            .then(if (embedded) Modifier else Modifier.statusBarsPadding())
                            .background(toolbar)
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
                }
            },
            bottomBar = {
                if (!embedded) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(systemBarStyle?.navigationBarColor?.toColor() ?: bottom)
                            .then(if (embedded) Modifier else Modifier.navigationBarsPadding())
                            .background(bottom),
                    ) {
                        if (shouldShowPermissionUpgradeButton(
                                isAllowedByHost = config.showPermissionUpgrade,
                                arePermissionsDeclared = areMediaPermissionsDeclared,
                                accessStatus = accessStatus,
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dimensionResource(R.dimen.auc_permission_height))
                                    .background(colorResource(R.color.auc_permission_background))
                                    .clickable(onClick = onRequestPermission)
                                    .padding(
                                        horizontal = dimensionResource(
                                            R.dimen.auc_permission_padding_horizontal,
                                        ),
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.auc_ic_permission_info),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(
                                        dimensionResource(R.dimen.auc_permission_info_size),
                                    ),
                                )
                                Spacer(
                                    Modifier.width(
                                        dimensionResource(R.dimen.auc_permission_text_margin_horizontal),
                                    ),
                                )
                                Text(
                                    text = stringResource(
                                        config.mediaFilter.permissionUpgradeMessageRes(),
                                        applicationName,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    color = colorResource(R.color.auc_permission_text),
                                    fontSize = dimensionSp(R.dimen.auc_permission_text_size),
                                    lineHeight = dimensionSp(R.dimen.auc_permission_text_line_height),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(
                                    Modifier.width(
                                        dimensionResource(R.dimen.auc_permission_text_margin_horizontal),
                                    ),
                                )
                                Icon(
                                    painter = painterResource(R.drawable.auc_ic_permission_chevron),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(
                                        dimensionResource(R.dimen.auc_permission_chevron_size),
                                    ),
                                )
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(dimensionResource(R.dimen.auc_bottom_height))
                                .padding(vertical = dimensionResource(R.dimen.auc_toolbar_padding_vertical)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 预览按钮
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
                            // 完成按钮
                            SelectionFinishAction(
                                selectedCount = session.selectedItems.size,
                                appearance = appearance,
                                onConfirm = onConfirm,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            // The grid remains in composition across refreshes, including empty results.
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(appearance.gridSpanCount),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(appearance.gridItemSpacingDp.dp),
                    horizontalArrangement = Arrangement.spacedBy(appearance.gridItemSpacingDp.dp),
                ) {
                    if (config.camera.enabled) {
                        item(key = "action_camera", contentType = "action") {
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
                        item(key = "action_add", contentType = "action") {
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
                        items(session.cameraItems, key = { "camera:${it.uri}" }, contentType = { "media" }) { item ->
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
                                sharedTransitionScope = sharedTransitionScope,
                                previewVisible = preview != null,
                            )
                        }
                    }
                    if (pagingItems?.loadState?.refresh is LoadState.Error ||
                        pagingItems?.loadState?.append is LoadState.Error) {
                        item(key = "load_error", contentType = "error", span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(onClick = { pagingItems.retry() }) {
                                Text(stringResource(R.string.auc_load_failed_retry))
                            }
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
                                    sharedTransitionScope = sharedTransitionScope,
                                    previewVisible = preview != null,
                                )
                            }
                        }
                    }
                }
                if (isInitialGridLoading) {
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black)
                            .clickable(interactionSource = interactionSource, indication = null, onClick = {})
                            .clearAndSetSemantics { },
                        contentAlignment = Alignment.Center,
                    ) {
                        ProgressSpinner()
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
                    .then(if (embedded) Modifier else Modifier.statusBarsPadding())
                    .padding(top = dimensionResource(R.dimen.auc_toolbar_height))
                    .background(colorResource(R.color.auc_directory_scrim))
                    .clickable { directoryMenu = false },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (embedded) Modifier else Modifier.statusBarsPadding())
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

        AnimatedContent(
            targetState = preview,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it?.id },
            transitionSpec = {
                fadeIn(tween(PREVIEW_TRANSITION_MILLIS)) togetherWith
                    fadeOut(tween(PREVIEW_TRANSITION_MILLIS))
            },
            label = "albumPreview",
        ) { state ->
            if (state != null) {
                AlbumPreviewScreen(
                    embedded = embedded,
                    systemBarStyle = systemBarStyle,
                    state = state,
                    session = session,
                    appearance = appearance,
                    imageLoader = imageLoader,
                    videoPlayerFactory = videoPlayerFactory,
                    onToggle = onToggle,
                    onLoadMore = onPreviewLoadMore,
                    onClose = onClosePreview,
                    onConfirm = onConfirm,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            }
        }
        if (isConfirming) ProcessingOverlay(blockBack = !embedded)
    }
}

@Composable
/** 执行 `DirectoryPanel` 方法定义的处理。 */
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
/** 执行 `DirectoryRow` 方法定义的处理。 */
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

/** 表示 `DIRECTORY_PANEL_MAX_HEIGHT_RATIO` 对应的数据。 */
private const val DIRECTORY_PANEL_MAX_HEIGHT_RATIO = 0.6f
/** 表示 `DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS` 对应的数据。 */
private const val DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS = 200

@Composable
/** 执行 `AlbumPreviewScreen` 方法定义的处理。 */
internal fun AlbumPreviewScreen(
    embedded: Boolean,
    systemBarStyle: AlbumPickerSystemBarStyle?,
    state: PreviewState,
    session: AlbumPickerSessionSnapshot,
    appearance: AlbumPickerAppearance,
    imageLoader: AlbumImageLoader,
    videoPlayerFactory: AlbumVideoPlayerFactory?,
    onToggle: (AlbumMedia) -> Unit,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
    val videoMode = videoPlayerFactory != null && currentMedia?.mediaType == AlbumMediaType.VIDEO
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoSession = remember(videoPlayerFactory, context) {
        videoPlayerFactory?.let { factory -> VideoPreviewSession { factory.create(context) } }
    }
    DisposableEffect(videoSession, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            videoSession?.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        videoSession?.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            videoSession?.setForeground(false)
        }
    }
    DisposableEffect(videoSession) {
        onDispose { videoSession?.close() }
    }
    val previewActive = animatedVisibilityScope == null ||
        animatedVisibilityScope.transition.targetState == EnterExitState.Visible
    val playableUri = currentMedia?.takeIf {
        it.mediaType == AlbumMediaType.VIDEO && !pagerState.isScrollInProgress && previewActive &&
            sharedTransitionScope?.isTransitionActive != true
    }?.uri
    SideEffect { videoSession?.show(playableUri) }
    if (videoMode && !embedded && systemBarStyle != null) {
        AlbumPickerSystemBars(
            statusBarColor = previewBackground.toArgb(),
            navigationBarColor = previewBackground.toArgb(),
            darkStatusBarIcons = false,
            darkNavigationBarIcons = false,
        )
    }
    Box(Modifier.fillMaxSize().background(previewBackground)) {
        Column(Modifier.fillMaxSize()) {
            if (!videoMode) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(systemBarStyle?.statusBarColor?.toColor() ?: toolbar)
                        .then(if (embedded) Modifier else Modifier.statusBarsPadding())
                        .background(toolbar)
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
                    AlbumPreviewImage(
                        media = media,
                        painter = imageLoader.previewPainter(media),
                        sharedTransitionScope = sharedTransitionScope.takeIf { index == pagerState.currentPage },
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                } else {
                    val active = videoSession?.takeIf { it.currentUri == media.uri }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AlbumVideoPreview(
                            media = media,
                            imageLoader = imageLoader,
                            session = active,
                        )
                        if (videoPlayerFactory == null) {
                            Icon(
                                painter = painterResource(appearance.videoIconRes ?: R.drawable.auc_ic_album_play),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(dimensionResource(R.dimen.auc_preview_video_play_size)),
                            )
                        }
                    }
                }
            }
            if (!embedded && !videoMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(systemBarStyle?.navigationBarColor?.toColor() ?: bottom)
                        .then(if (embedded) Modifier else Modifier.navigationBarsPadding())
                        .background(bottom)
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
        if (videoMode && videoSession != null) {
            Box(
                Modifier.fillMaxSize()
                    .then(if (embedded) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)),
            ) {
                IconButton(
                    onClick = {
                        videoSession.close()
                        onClose()
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(
                            start = dimensionResource(R.dimen.auc_toolbar_padding_horizontal),
                            top = dimensionResource(R.dimen.auc_video_back_margin_top),
                        )
                        .size(dimensionResource(R.dimen.auc_toolbar_back_size)),
                ) {
                    Icon(
                        painterResource(appearance.backIconRes ?: R.drawable.auc_ic_album_back),
                        contentDescription = stringResource(R.string.auc_back),
                        tint = Color.Unspecified,
                    )
                }
                AlbumVideoControls(
                    session = videoSession,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(dimensionResource(R.dimen.auc_video_controls_margin)),
                )
            }
        }
    }
}

/**
 * 确认完成按钮。
 * @param selectedCount 已选媒体数量
 * @param appearance 配置
 * @param onConfirm 确认完成回调
 * @param modifier 修饰符
 */
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
                    .height(dimensionResource(R.dimen.auc_preview_selected_count_size))
                    .widthIn(min = dimensionResource(R.dimen.auc_preview_selected_count_size))
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedCount.toString(),
                    color = primary,
                    fontSize = dimensionSp(R.dimen.auc_preview_count_text_size),
                    lineHeight = dimensionSp(R.dimen.auc_preview_count_text_size),
                    maxLines = 1,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
            }

        }

        Spacer(
            Modifier.width(
                dimensionResource(R.dimen.auc_preview_done_text_margin_start),
            ),
        )

        if (hasSelection && appearance.doneIconRes != null) {
            Icon(
                painter = painterResource(appearance.doneIconRes),
                contentDescription = null,
                tint = Color.Unspecified,
            )
            Spacer(
                Modifier.width(
                    dimensionResource(R.dimen.auc_preview_done_icon_gap),
                ),
            )
        }

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
/** 执行 `ActionTile` 方法定义的处理。 */
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
/** 执行 `MediaTile` 方法定义的处理。 */
private fun MediaTile(
    media: AlbumMedia,
    selected: Boolean,
    selectionBlocked: Boolean,
    appearance: AlbumPickerAppearance,
    imageLoader: AlbumImageLoader,
    onPreview: (AlbumMedia) -> Unit,
    onToggle: (AlbumMedia) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    previewVisible: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.Black)
            .clickable(enabled = !selectionBlocked) { onPreview(media) },
    ) {
        MediaThumbnail(media, imageLoader, sharedTransitionScope, previewVisible)
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
        MediaInfo(media)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(dimensionResource(R.dimen.auc_media_check_size))
                .clickable { onToggle(media) }
                .padding(dimensionResource(R.dimen.auc_media_check_margin)),
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
/** Displays duration or an image-format badge using only metadata already on [media]. */
private fun BoxScope.MediaInfo(media: AlbumMedia) {
    val labelShape = RoundedCornerShape(
        dimensionResource(R.dimen.auc_media_label_corner_radius),
    )
    val durationLabel = remember(media.mediaType, media.durationMillis) {
        media.videoDurationLabel()
    }
    durationLabel?.let { duration ->
        val durationDescription = stringResource(R.string.auc_video_duration, duration)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(dimensionResource(R.dimen.auc_media_label_margin))
                .height(dimensionResource(R.dimen.auc_media_label_height))
                .clearAndSetSemantics { contentDescription = durationDescription },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.auc_ic_album_video),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(
                    dimensionResource(R.dimen.auc_media_video_icon_size),
                ),
            )
            Spacer(Modifier.width(dimensionResource(R.dimen.auc_media_video_icon_gap)))
            Text(
                text = duration,
                color = colorResource(R.color.auc_primary),
                fontSize = dimensionSp(R.dimen.auc_media_label_text_size),
                maxLines = 1,
            )
        }
    }

    val gridBadge = remember(
        media.mediaType,
        media.specialFormat,
        media.mimeType,
        media.displayName,
        media.width,
        media.height,
    ) {
        media.gridBadge()
    }
    val badge = when (gridBadge) {
        MediaGridBadge.GIF -> stringResource(R.string.auc_media_badge_gif)
        MediaGridBadge.LONG_IMAGE -> stringResource(R.string.auc_media_badge_long_image)
        MediaGridBadge.LIVE_PHOTO -> stringResource(R.string.auc_media_badge_live_photo)
        null -> null
    }
    badge?.let { label ->
        Text(
            text = label,
            color = colorResource(R.color.auc_primary),
            fontSize = dimensionSp(R.dimen.auc_media_label_text_size),
            lineHeight = dimensionSp(R.dimen.auc_media_label_text_size),
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(dimensionResource(R.dimen.auc_media_label_margin))
                .wrapContentSize()
                .background(colorResource(R.color.auc_media_badge_background), labelShape)
                .padding(
                    horizontal = dimensionResource(R.dimen.auc_media_label_padding_horizontal),
                    vertical = dimensionResource(R.dimen.auc_media_label_padding_vertical),
                ),
        )
    }
}

@Composable
/** 执行 `MediaThumbnail` 方法定义的处理。 */
private fun MediaThumbnail(
    media: AlbumMedia,
    imageLoader: AlbumImageLoader,
    sharedTransitionScope: SharedTransitionScope? = null,
    previewVisible: Boolean = false,
) {
    Image(
        painter = imageLoader.painter(media, AlbumImageTarget.GRID_THUMBNAIL),
        contentDescription = media.displayName,
        modifier = Modifier.fillMaxSize().sharedAlbumThumbnail(media, sharedTransitionScope, !previewVisible),
        contentScale = ContentScale.Crop,
    )
}

/** Labels supported by a media tile in the picker grid. */
internal enum class MediaGridBadge {
    GIF,
    LONG_IMAGE,
    LIVE_PHOTO,
}

/** Returns the highest-priority badge for an image tile. */
internal fun AlbumMedia.gridBadge(): MediaGridBadge? {
    if (mediaType != AlbumMediaType.IMAGE) return null
    if (specialFormat == AlbumMediaSpecialFormat.MOTION_PHOTO) {
        return MediaGridBadge.LIVE_PHOTO
    }
    if (
        specialFormat == AlbumMediaSpecialFormat.GIF ||
        mimeType.equals("image/gif", ignoreCase = true) ||
        displayName?.endsWith(".gif", ignoreCase = true) == true
    ) {
        return MediaGridBadge.GIF
    }
    val imageWidth = width?.takeIf { it > 0 } ?: return null
    val imageHeight = height?.takeIf { it > 0 } ?: return null
    return if (imageHeight.toLong() >= imageWidth.toLong() * LONG_IMAGE_RATIO) {
        MediaGridBadge.LONG_IMAGE
    } else {
        null
    }
}

/** Formats video duration as `mm:ss`, or `h:mm:ss` for long videos. */
internal fun AlbumMedia.videoDurationLabel(): String? {
    if (mediaType != AlbumMediaType.VIDEO) return null
    val totalSeconds = durationMillis?.takeIf { it >= 0L }?.div(MILLIS_PER_SECOND)
        ?: return UNKNOWN_VIDEO_DURATION
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

private const val LONG_IMAGE_RATIO = 3L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val UNKNOWN_VIDEO_DURATION = "--:--"

@Composable
/** 执行 `ProcessingOverlay` 方法定义的处理。 */
private fun ProcessingOverlay(blockBack: Boolean) {
    BackHandler(enabled = blockBack) { }
    val interactionSource = remember { MutableInteractionSource() }
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
            ProgressSpinner(contentDescription = stringResource(R.string.auc_processing))
        }
    }
}

/** 列表加载与文件处理共用的图片旋转指示器。 */
@Composable
private fun ProgressSpinner(contentDescription: String? = null) {
    val spinnerTransition = rememberInfiniteTransition(label = "progressSpinner")
    val spinnerRotation by spinnerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PROCESSING_SPINNER_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        ),
        label = "progressSpinnerRotation",
    )
    Image(
        painter = painterResource(R.drawable.auc_progress_spinner),
        contentDescription = contentDescription,
        modifier = Modifier
            .size(dimensionResource(R.dimen.auc_processing_indicator_size))
            .graphicsLayer { rotationZ = spinnerRotation },
    )
}

/** 将 XML 的 sp 资源转换为 Compose 的 sp 值，避免重复应用字体缩放。 */
@Composable
private fun dimensionSp(@DimenRes resourceId: Int): TextUnit {
    val dimension = dimensionResource(resourceId)
    return (dimension.value / LocalDensity.current.fontScale).sp
}

/** 表示 `PROCESSING_SPINNER_DURATION_MILLIS` 对应的数据。 */
private const val PROCESSING_SPINNER_DURATION_MILLIS = 1_000

@Composable
/** 退出流程开始后拦截输入，避免动画期间重复触发选择器操作。 */
private fun InteractionBlocker(blockBack: Boolean = true) {
    if (blockBack) BackHandler { }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .clearAndSetSemantics { },
    )
}
