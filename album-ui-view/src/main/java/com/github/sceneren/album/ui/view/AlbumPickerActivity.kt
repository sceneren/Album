package com.github.sceneren.album.ui.view

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 基于 XML 布局的 View 全屏相册选择页。 */
class AlbumPickerActivity : ComponentActivity() {
    private var activityAnimation: AlbumPickerAnimation? = AlbumPickerAnimation()
    private lateinit var config: com.github.sceneren.album.api.AlbumPickerConfig
    private lateinit var appearance: AlbumPickerAppearance
    private lateinit var api: AlbumApi
    private lateinit var client: com.github.sceneren.album.api.AlbumPickerClient
    private lateinit var imageLoader: AlbumImageLoader
    private lateinit var session: AlbumPickerSessionSnapshot

    private lateinit var root: View
    private lateinit var toolbar: View
    private lateinit var bottomBar: View
    private lateinit var grid: RecyclerView
    private lateinit var titleAction: View
    private lateinit var title: TextView
    private lateinit var titleArrow: ImageView
    private lateinit var directoryScrim: View
    private lateinit var directoryList: RecyclerView
    private lateinit var cancelButton: TextView
    private lateinit var previewButton: Button
    private lateinit var doneAction: LinearLayout
    private lateinit var selectedCount: TextView
    private lateinit var doneText: TextView
    private lateinit var permissionAction: View
    private lateinit var permissionText: TextView
    private lateinit var actionAdapter: ActionAdapter
    private lateinit var cameraAdapter: CameraAdapter
    private lateinit var mediaAdapter: GalleryAdapter
    private lateinit var directoryAdapter: DirectoryAdapter

    private var cameraLauncher: com.github.sceneren.album.api.AlbumCameraLauncher? = null
    private var photoPicker: com.github.sceneren.album.api.AlbumPhotoPickerLauncher? = null
    private var feedJob: Job? = null
    private var directoryJob: Job? = null
    private var messageToast: Toast? = null
    private var previewDialog: AlbumPreviewDialog? = null
    private var processingDialog: Dialog? = null
    private var processingSpinnerAnimator: ObjectAnimator? = null
    private var isConfirming = false
    private var accessStatus: MediaAccessStatus = MediaAccessStatus.DENIED
    private var directories: List<AlbumDirectory> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        lifecycleScope.launch {
            api.syncPartialSelections(config.mediaFilter)
            refreshContent()
        }
    }

    /** 处理 `onCreate` 回调。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = intent.getIntExtra(AlbumPickerExtras.THEME, 0)
        if (theme != 0) setTheme(theme)
        super.onCreate(savedInstanceState)
        activityAnimation = AlbumPickerExtras.readAnimation(intent)
        applyActivityTransitions(activityAnimation)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        config = AlbumPickerIntentCodec.readConfig(intent)
        appearance = AlbumPickerExtras.readAppearance(intent)
        imageLoader = AlbumUi.requireImageLoader()
        api = AlbumApi.create(this)
        client = api.createPickerClient(this)
        val sessionId = requireNotNull(intent.getStringExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID))
        session = client.openSession(config, sessionId)

        photoPicker = api.registerPhotoPicker(this, config.mediaFilter, maxSelectionCount = null) {
            refreshContent()
        }
        cameraLauncher = client.registerCamera(this, session.sessionId) { result ->
            result.onSuccess { updated ->
                renderSession(updated)
                if (accessStatus != MediaAccessStatus.FULL) refreshContent()
                maybeAutoConfirm()
            }.onFailure { failure ->
                showMessage(failure.message ?: getString(R.string.auv_camera_failed))
            }
        }

        bindViews()
        applyAppearance()
        applySystemBarInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            /** 执行 `handleOnBackPressed` 方法定义的处理。 */
            override fun handleOnBackPressed() {
                if (isDirectoryPanelVisible()) {
                    hideDirectoryPanel()
                    return
                }
                lifecycleScope.launch {
                    client.cancel(session.sessionId)
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        })
    }

    /** 处理 `onResume` 回调。 */
    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    /** 执行 `finish` 方法定义的处理。 */
    override fun finish() {
        super.finish()
        applyLegacyCloseTransition(activityAnimation)
    }

    /** 处理 `onDestroy` 回调。 */
    override fun onDestroy() {
        previewDialog?.dismiss()
        previewDialog = null
        hideProcessingDialog()
        feedJob?.cancel()
        directoryJob?.cancel()
        messageToast?.cancel()
        super.onDestroy()
    }

    /** 所有页面控件均由 auv_activity_album_picker.xml 声明。 */
    private fun bindViews() {
        setContentView(R.layout.auv_activity_album_picker)
        root = findViewById(R.id.auv_picker_root)
        toolbar = findViewById(R.id.auv_picker_toolbar)
        bottomBar = findViewById(R.id.auv_picker_bottom)
        grid = findViewById(R.id.auv_picker_grid)
        titleAction = findViewById(R.id.auv_picker_title_action)
        title = findViewById(R.id.auv_picker_title)
        titleArrow = findViewById(R.id.auv_picker_title_arrow)
        directoryScrim = findViewById(R.id.auv_picker_directory_scrim)
        directoryList = findViewById(R.id.auv_picker_directories)
        cancelButton = findViewById(R.id.auv_picker_cancel)
        previewButton = findViewById(R.id.auv_picker_preview)
        doneAction = findViewById(R.id.auv_picker_done_action)
        selectedCount = findViewById(R.id.auv_picker_selected_count)
        doneText = findViewById(R.id.auv_picker_done_text)
        permissionAction = findViewById(R.id.auv_picker_permission)
        permissionText = findViewById(R.id.auv_picker_permission_text)

        findViewById<ImageButton>(R.id.auv_picker_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        titleAction.setOnClickListener { showDirectories() }
        directoryScrim.setOnClickListener { hideDirectoryPanel() }
        cancelButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        previewButton.setOnClickListener { showPreview() }
        doneAction.setOnClickListener { confirmSelection() }
        permissionAction.setOnClickListener { requestMediaPermission() }

        val gridMetrics = GridMetrics(
            spanCount = appearance.gridSpanCount,
            spacingPx = dpToPx(appearance.gridItemSpacingDp),
        )
        grid.layoutManager = GridLayoutManager(this, gridMetrics.spanCount)
        grid.addItemDecoration(GridSpacingItemDecoration(gridMetrics))
        grid.itemAnimator = null
        actionAdapter = ActionAdapter(appearance, gridMetrics) { action ->
            when (action) {
                Action.CAMERA -> cameraLauncher?.launch(cameraMediaType())
                Action.ADD -> photoPickerLaunch()
            }
        }
        cameraAdapter = CameraAdapter(
            appearance,
            gridMetrics,
            imageLoader,
            config.maxSelectionCount,
            ::onMediaPreview,
            ::toggleMedia,
        )
        mediaAdapter = GalleryAdapter(
            appearance,
            gridMetrics,
            imageLoader,
            config.maxSelectionCount,
            ::onMediaPreview,
            ::toggleMedia,
        )
        grid.adapter = ConcatAdapter(actionAdapter, cameraAdapter, mediaAdapter)
        directoryAdapter = DirectoryAdapter(imageLoader, ::selectDirectory)
        directoryList.layoutManager = LinearLayoutManager(this)
        directoryList.adapter = directoryAdapter
        directoryList.itemAnimator = null
    }

    /** 将可配置外观应用到 XML 中已声明的控件，不创建新的界面层级。 */
    private fun applyAppearance() {
        val toolbarColor = appearance.toolbarColor ?: color(R.color.auv_toolbar)
        val bottomColor = appearance.bottomBarColor ?: color(R.color.auv_bottom)
        val primaryColor = appearance.primaryTextColor ?: color(R.color.auv_primary)
        val accentColor = appearance.accentColor ?: color(R.color.auv_accent)
        root.setBackgroundColor(toolbarColor)
        toolbar.setBackgroundColor(toolbarColor)
        bottomBar.setBackgroundColor(bottomColor)
        title.setTextColor(primaryColor)
        cancelButton.setTextColor(primaryColor)
        previewButton.setTextColor(primaryColor)
        doneText.setTextColor(color(R.color.auv_secondary))
        selectedCount.setTextColor(primaryColor)
        selectedCount.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor)
        }
        doneText.compoundDrawablePadding = dpToPx(4)
        appearance.backIconRes?.let { findViewById<ImageButton>(R.id.auv_picker_back).setImageResource(it) }
        val customFolderIcon = appearance.folderIconRes
        titleArrow.setImageResource(customFolderIcon ?: R.drawable.auv_ic_album_expand_more)
        if (customFolderIcon == null) {
            titleArrow.setColorFilter(primaryColor)
        } else {
            titleArrow.clearColorFilter()
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = ColorUtils.calculateLuminance(toolbarColor) > LIGHT_COLOR_LUMINANCE
            isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(bottomColor) > LIGHT_COLOR_LUMINANCE
        }
    }

    /**
     * 工具栏和底栏的背景绘制到系统栏区域，实际可点击内容避开状态栏、刘海和导航栏。
     */
    private fun applySystemBarInsets() {
        val toolbarHeight = toolbar.layoutParams.height
        val toolbarPaddingLeft = toolbar.paddingLeft
        val toolbarPaddingTop = toolbar.paddingTop
        val toolbarPaddingRight = toolbar.paddingRight
        val toolbarPaddingBottom = toolbar.paddingBottom
        val bottomHeight = bottomBar.layoutParams.height
        val bottomPaddingLeft = bottomBar.paddingLeft
        val bottomPaddingTop = bottomBar.paddingTop
        val bottomPaddingRight = bottomBar.paddingRight
        val bottomPaddingBottom = bottomBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            toolbar.updateLayoutParams<ViewGroup.LayoutParams> {
                height = toolbarHeight + safeInsets.top
            }
            toolbar.updatePadding(
                left = toolbarPaddingLeft + safeInsets.left,
                top = toolbarPaddingTop + safeInsets.top,
                right = toolbarPaddingRight + safeInsets.right,
                bottom = toolbarPaddingBottom,
            )
            bottomBar.updateLayoutParams<ViewGroup.LayoutParams> {
                height = bottomHeight + safeInsets.bottom
            }
            bottomBar.updatePadding(
                left = bottomPaddingLeft + safeInsets.left,
                top = bottomPaddingTop,
                right = bottomPaddingRight + safeInsets.right,
                bottom = bottomPaddingBottom + safeInsets.bottom,
            )
            grid.updatePadding(left = safeInsets.left, right = safeInsets.right)
            directoryScrim.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = toolbarHeight + safeInsets.top
            }
            directoryList.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = toolbarHeight + safeInsets.top
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** 更新 `refreshContent` 对应的状态。 */
    private fun refreshContent() {
        accessStatus = api.getMediaAccessStatus(config.mediaFilter)
        val canBrowseDirectories = accessStatus == MediaAccessStatus.FULL
        titleArrow.visibility = if (canBrowseDirectories) View.VISIBLE else View.GONE
        titleAction.isEnabled = canBrowseDirectories
        if (!canBrowseDirectories) {
            directoryJob?.cancel()
            directoryJob = null
            directories = emptyList()
            hideDirectoryPanel()
        }
        renderTitle()
        renderActions()
        permissionAction.visibility = if (shouldShowPermissionUpgradeButton(
                isAllowedByHost = config.showPermissionUpgrade,
                accessStatus = accessStatus,
            )
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val applicationName = applicationInfo.loadLabel(packageManager).toString().ifBlank { packageName }
        permissionText.text = when (accessStatus) {
            MediaAccessStatus.FULL -> ""
            else -> getString(
                R.string.auv_denied_permission,
                applicationName,
            )
        }
        val feed = api.getMediaFeed(
            mediaFilter = config.mediaFilter,
            bucketId = session.bucketId,
        )
        feedJob?.cancel()
        feedJob = lifecycleScope.launch {
            feed.pagingData.collectLatest { data: PagingData<AlbumMedia> ->
                mediaAdapter.submitData(data)
            }
        }
        lifecycleScope.launch {
            renderSession(client.snapshot(session.sessionId))
        }
        if (canBrowseDirectories) {
            directoryJob?.cancel()
            directoryJob = lifecycleScope.launch {
                val updatedDirectories = api.getMediaDirectories(config.mediaFilter).getOrNull().orEmpty()
                if (accessStatus == MediaAccessStatus.FULL) {
                    directories = updatedDirectories
                    renderTitle()
                    directoryAdapter.submit(directories, session.bucketId)
                }
            }
        }
    }

    /** 执行 `renderSession` 方法定义的处理。 */
    private fun renderSession(updated: AlbumPickerSessionSnapshot) {
        session = updated
        renderTitle()
        directoryAdapter.submit(directories, updated.bucketId)
        cameraAdapter.submit(
            value = if (accessStatus == MediaAccessStatus.FULL) updated.cameraItems else emptyList(),
            selectedUris = updated.selectedUris,
        )
        mediaAdapter.updateSelection(updated.selectedUris)
        previewDialog?.updateSelection(updated.selectedUris)
        previewButton.text = if (updated.selectedItems.isEmpty()) {
            getString(R.string.auv_preview)
        } else {
            getString(R.string.auv_preview_count, updated.selectedItems.size)
        }
        val hasSelection = updated.selectedItems.isNotEmpty()
        selectedCount.visibility = if (hasSelection) View.VISIBLE else View.GONE
        selectedCount.text = updated.selectedItems.size.toString()
        doneText.text = getString(
            if (hasSelection) R.string.auv_done else R.string.auv_please_select,
        )
        doneText.setTextColor(
            if (hasSelection) {
                appearance.accentColor ?: color(R.color.auv_accent)
            } else {
                appearance.secondaryTextColor ?: color(R.color.auv_secondary)
            },
        )
        doneText.setCompoundDrawablesWithIntrinsicBounds(
            if (hasSelection) appearance.doneIconRes ?: 0 else 0,
            0,
            0,
            0,
        )
        doneAction.isEnabled = hasSelection
    }

    /** 执行 `renderTitle` 方法定义的处理。 */
    private fun renderTitle() {
        val directory = selectedTitleDirectory(
            accessStatus = accessStatus,
            bucketId = session.bucketId,
            directories = directories,
        )
        title.text = directory?.bucketName?.takeIf(String::isNotBlank) ?: directory?.let { getString(R.string.auv_unnamed_directory, it.mediaCount) } ?: getString(R.string.auv_title)
    }

    /** 更新 `toggleMedia` 对应的状态。 */
    private fun toggleMedia(media: AlbumMedia) {
        if (media.uri !in session.selectedUris && session.selectedItems.size >= config.maxSelectionCount) {
            showSelectionLimitMessage()
            return
        }
        lifecycleScope.launch {
            client.toggleSelection(session.sessionId, media).onSuccess { updated ->
                renderSession(updated)
                maybeAutoConfirm()
            }.onFailure {
                showMessage(it.message ?: getString(R.string.auv_selection_failed))
            }
        }
    }

    /** 执行 `maybeAutoConfirm` 方法定义的处理。 */
    private fun maybeAutoConfirm() {
        if (config.maxSelectionCount == 1 && config.singleSelectionFinishMode == com.github.sceneren.album.api.SingleSelectionFinishMode.IMMEDIATE && session.selectedItems.size == 1) {
            confirmSelection()
        }
    }

    /** 执行 `confirmSelection` 方法定义的处理。 */
    private fun confirmSelection() {
        if (isConfirming) return
        if (session.selectedItems.isEmpty()) {
            showMessage(getString(R.string.auv_select_first))
            return
        }
        isConfirming = true
        doneAction.isEnabled = false
        showProcessingDialog()
        lifecycleScope.launch {
            client.confirm(session.sessionId).onSuccess { result ->
                setResult(Activity.RESULT_OK, AlbumPickerIntentCodec.putResult(Intent(), result))
                finish()
            }.onFailure { failure ->
                isConfirming = false
                hideProcessingDialog()
                doneAction.isEnabled = true
                showMessage(
                    getString(
                        R.string.auv_process_failed,
                        failure.message ?: getString(R.string.auv_retry),
                    ),
                )
            }
        }
    }

    /** 执行 `showProcessingDialog` 方法定义的处理。 */
    private fun showProcessingDialog() {
        processingSpinnerAnimator?.cancel()
        processingDialog = Dialog(this, R.style.auv_theme_album_picker_processing).apply {
            setContentView(R.layout.auv_dialog_album_processing)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            show()
            window?.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            processingSpinnerAnimator = ObjectAnimator.ofFloat(
                findViewById<ImageView>(R.id.auv_processing_spinner),
                View.ROTATION,
                0f,
                360f,
            ).apply {
                duration = PROCESSING_SPINNER_DURATION_MILLIS
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    /** 执行 `hideProcessingDialog` 方法定义的处理。 */
    private fun hideProcessingDialog() {
        processingSpinnerAnimator?.cancel()
        processingSpinnerAnimator = null
        processingDialog?.dismiss()
        processingDialog = null
    }

    /** 执行 `showSelectionLimitMessage` 方法定义的处理。 */
    private fun showSelectionLimitMessage() {
        val message = when (config.mediaFilter) {
            AlbumMediaFilter.IMAGES -> R.string.auv_selection_limit_images
            AlbumMediaFilter.VIDEOS -> R.string.auv_selection_limit_videos
            AlbumMediaFilter.IMAGES_AND_VIDEOS -> R.string.auv_selection_limit_files
        }
        showMessage(getString(message, config.maxSelectionCount))
    }

    /** 执行 `showMessage` 方法定义的处理。 */
    private fun showMessage(message: CharSequence) {
        messageToast?.cancel()
        messageToast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).also(Toast::show)
    }

    /** 执行 `requestMediaPermission` 方法定义的处理。 */
    private fun requestMediaPermission() {
        permissionLauncher.launch(AlbumMediaPermissionRequestFactory.create(config.mediaFilter))
    }

    /** 执行 `photoPickerLaunch` 方法定义的处理。 */
    private fun photoPickerLaunch() {
        photoPicker?.launch()
    }

    /** 执行 `cameraMediaType` 方法定义的处理。 */
    private fun cameraMediaType() = when (config.mediaFilter) {
        AlbumMediaFilter.IMAGES -> AlbumMediaType.IMAGE
        AlbumMediaFilter.VIDEOS -> AlbumMediaType.VIDEO
        AlbumMediaFilter.IMAGES_AND_VIDEOS -> if (config.camera.mixedMediaCaptureType == AlbumCameraCaptureType.PHOTO) {
            AlbumMediaType.IMAGE
        } else {
            AlbumMediaType.VIDEO
        }
    }

    /** 执行 `actions` 方法定义的处理。 */
    private fun actions(): List<Action> = buildList {
        if (config.camera.enabled) add(Action.CAMERA)
        if (accessStatus != MediaAccessStatus.FULL) add(Action.ADD)
    }

    /** 执行 `renderActions` 方法定义的处理。 */
    private fun renderActions() {
        if (::actionAdapter.isInitialized) actionAdapter.submit(actions())
    }

    /** 处理 `onMediaPreview` 回调。 */
    private fun onMediaPreview(media: AlbumMedia) {
        val loadedFeedItems = mediaAdapter.snapshot().items
        val initialItems = (cameraAdapter.currentItems() + loadedFeedItems).distinctBy { it.uri }
        openPreview(
            items = initialItems,
            initialIndex = initialItems.indexOfFirst { it.uri == media.uri }.coerceAtLeast(0),
            nextOffset = loadedFeedItems.size,
        )
    }

    /** 执行 `showPreview` 方法定义的处理。 */
    private fun showPreview() {
        if (session.selectedItems.isEmpty()) return
        openPreview(session.selectedItems, initialIndex = 0, nextOffset = null)
    }

    /** 执行 `openPreview` 方法定义的处理。 */
    private fun openPreview(
        items: List<AlbumMedia>,
        initialIndex: Int,
        nextOffset: Int?,
    ) {
        if (items.isEmpty()) return
        previewDialog?.dismiss()
        previewDialog = AlbumPreviewDialog(
            activity = this,
            appearance = appearance,
            imageLoader = imageLoader,
            scope = lifecycleScope,
            initialItems = items,
            initialIndex = initialIndex,
            initialSelectedUris = session.selectedUris,
            nextOffset = nextOffset,
            onToggleSelection = ::toggleMedia,
            onConfirm = ::confirmSelection,
            onDismiss = { dismissed ->
                if (previewDialog === dismissed) previewDialog = null
            },
            loadMore = { offset, limit ->
                api.loadMediaPage(
                    mediaFilter = config.mediaFilter,
                    bucketId = session.bucketId,
                    offset = offset,
                    limit = limit,
                )
            },
        ).also(AlbumPreviewDialog::show)
    }

    /** 执行 `showDirectories` 方法定义的处理。 */
    private fun showDirectories() {
        if (accessStatus != MediaAccessStatus.FULL) return
        if (isDirectoryPanelVisible()) {
            hideDirectoryPanel()
            return
        }
        if (directories.isNotEmpty()) {
            showDirectoryPanel()
            return
        }
        directoryJob?.cancel()
        directoryJob = lifecycleScope.launch {
            directories = api.getMediaDirectories(config.mediaFilter).getOrNull().orEmpty()
            if (accessStatus == MediaAccessStatus.FULL) {
                renderTitle()
                showDirectoryPanel()
            }
        }
    }

    /** 执行 `selectDirectory` 方法定义的处理。 */
    private fun selectDirectory(bucketId: Long) {
        hideDirectoryPanel()
        if (!shouldUpdateDirectory(session.bucketId, bucketId)) return
        lifecycleScope.launch {
            client.setBucket(session.sessionId, bucketId).onSuccess { updated ->
                renderSession(updated)
                refreshContent()
            }
        }
    }

    /** 执行 `showDirectoryPanel` 方法定义的处理。 */
    private fun showDirectoryPanel() {
        directoryAdapter.submit(directories, session.bucketId)
        val contentHeight = directories.size * resources.getDimensionPixelSize(
            R.dimen.auv_directory_row_height,
        )
        val windowMaxHeight = (root.height * DIRECTORY_PANEL_MAX_HEIGHT_RATIO).roundToInt()
        val panelHeight = minOf(
            contentHeight,
            windowMaxHeight,
            (root.height - directoryList.top).coerceAtLeast(0),
        )
        directoryList.updateLayoutParams<ViewGroup.LayoutParams> {
            height = panelHeight
        }
        cancelDirectoryPanelAnimations()
        directoryScrim.isVisible = true
        directoryList.isVisible = directories.isNotEmpty()
        directoryScrim.alpha = 0f
        directoryList.alpha = 0f
        directoryList.pivotY = 0f
        directoryList.scaleY = 0f
        directoryScrim.animate().alpha(1f).setDuration(DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS).start()
        if (directoryList.isVisible) {
            directoryList.animate().alpha(1f).scaleY(1f).setDuration(DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS).start()
        } else {
            directoryList.alpha = 1f
            directoryList.scaleY = 1f
        }
        titleArrow.animate().rotation(180f).setDuration(DIRECTORY_ARROW_DURATION_MILLIS).start()
    }

    /** 执行 `hideDirectoryPanel` 方法定义的处理。 */
    private fun hideDirectoryPanel() {
        cancelDirectoryPanelAnimations()
        if (!directoryScrim.isVisible && !directoryList.isVisible) {
            titleArrow.animate().rotation(0f).setDuration(DIRECTORY_ARROW_DURATION_MILLIS).start()
            return
        }
        directoryScrim.animate().alpha(0f).setDuration(DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS).withEndAction {
                directoryScrim.isVisible = false
                directoryScrim.alpha = 1f
            }.start()
        if (directoryList.isVisible) {
            directoryList.animate().alpha(0f).scaleY(0f).setDuration(DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS).withEndAction {
                    directoryList.isVisible = false
                    directoryList.alpha = 1f
                    directoryList.scaleY = 1f
                }.start()
        }
        titleArrow.animate().rotation(0f).setDuration(DIRECTORY_ARROW_DURATION_MILLIS).start()
    }

    /** 判断 `cancelDirectoryPanelAnimations` 条件是否成立。 */
    private fun cancelDirectoryPanelAnimations() {
        directoryScrim.animate().cancel()
        directoryList.animate().cancel()
    }

    /** 判断 `isDirectoryPanelVisible` 条件是否成立。 */
    private fun isDirectoryPanelVisible(): Boolean = directoryScrim.isVisible

    /** 执行 `color` 方法定义的处理。 */
    private fun color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)

    /** 执行 `dpToPx` 方法定义的处理。 */
    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).roundToInt().coerceAtLeast(0)
}

/** 表示 `LIGHT_COLOR_LUMINANCE` 对应的数据。 */
private const val LIGHT_COLOR_LUMINANCE = 0.5

/** 表示 `DIRECTORY_ARROW_DURATION_MILLIS` 对应的数据。 */
private const val DIRECTORY_ARROW_DURATION_MILLIS = 150L

/** 表示 `DIRECTORY_PANEL_MAX_HEIGHT_RATIO` 对应的数据。 */
private const val DIRECTORY_PANEL_MAX_HEIGHT_RATIO = 0.6f

/** 表示 `DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS` 对应的数据。 */
private const val DIRECTORY_PANEL_ANIMATION_DURATION_MILLIS = 200L

/** 表示 `PROCESSING_SPINNER_DURATION_MILLIS` 对应的数据。 */
private const val PROCESSING_SPINNER_DURATION_MILLIS = 1_000L

/** 判断 `shouldShowPermissionUpgradeButton` 条件是否成立。 */
internal fun shouldShowPermissionUpgradeButton(
    isAllowedByHost: Boolean,
    accessStatus: MediaAccessStatus,
): Boolean = isAllowedByHost && accessStatus != MediaAccessStatus.FULL

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
internal fun shouldUpdateDirectory(currentBucketId: Long, targetBucketId: Long): Boolean = currentBucketId != targetBucketId
