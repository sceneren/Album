package com.github.sceneren.album.ui.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaPermissionRequestFactory
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerIntentCodec
import com.github.sceneren.album.api.AlbumPickerSessionSnapshot
import com.github.sceneren.album.api.MediaAccessStatus
import com.github.sceneren.album.api.PhotoPickResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 基于 XML 布局的 View 全屏相册选择页。 */
class AlbumPickerActivity : ComponentActivity() {
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
    private lateinit var permissionButton: Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = intent.getIntExtra(AlbumPickerExtras.THEME, 0)
        if (theme != 0) setTheme(theme)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        config = AlbumPickerIntentCodec.readConfig(intent)
        appearance = AlbumPickerExtras.readAppearance(intent)
        imageLoader = AlbumUi.requireImageLoader()
        api = AlbumApi.create(this)
        client = api.createPickerClient(this)
        val sessionId = requireNotNull(intent.getStringExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID))
        session = client.openSession(config, sessionId)

        photoPicker = api.registerPhotoPicker(this, config.mediaFilter, config.maxSelectionCount) { result ->
            showMessage(
                when (result) {
                is PhotoPickResult.Selected -> getString(R.string.auv_added_count, result.media.size)
                PhotoPickResult.Cancelled -> getString(R.string.auv_add_cancelled)
                is PhotoPickResult.Failed -> getString(R.string.auv_add_failed, result.reason.name)
                },
            )
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

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    override fun onDestroy() {
        previewDialog?.dismiss()
        previewDialog = null
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
        permissionButton = findViewById(R.id.auv_picker_permission)

        findViewById<ImageButton>(R.id.auv_picker_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        titleAction.setOnClickListener { showDirectories() }
        directoryScrim.setOnClickListener { hideDirectoryPanel() }
        cancelButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        previewButton.setOnClickListener { showPreview() }
        doneAction.setOnClickListener { confirmSelection() }
        permissionButton.setOnClickListener { requestMediaPermission() }

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
            ::onMediaPreview,
            ::toggleMedia,
        )
        mediaAdapter = GalleryAdapter(
            appearance,
            gridMetrics,
            imageLoader,
            ::onMediaPreview,
            ::toggleMedia,
        )
        grid.adapter = ConcatAdapter(actionAdapter, cameraAdapter, mediaAdapter)
        directoryAdapter = DirectoryAdapter(imageLoader, ::selectDirectory)
        directoryList.layoutManager = LinearLayoutManager(this)
        directoryList.adapter = directoryAdapter
        directoryList.itemAnimator = null
        directoryList.clipToOutline = true
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
        permissionButton.visibility = if (
            shouldShowPermissionUpgradeButton(
                isAllowedByHost = config.showPermissionUpgrade,
                accessStatus = accessStatus,
            )
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        permissionButton.text = when (accessStatus) {
            MediaAccessStatus.PARTIAL -> getString(R.string.auv_partial_permission)
            MediaAccessStatus.DENIED -> getString(R.string.auv_denied_permission)
            MediaAccessStatus.FULL -> ""
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
                val updatedDirectories = api.getMediaDirectories(config.mediaFilter)
                    .getOrNull()
                    .orEmpty()
                if (accessStatus == MediaAccessStatus.FULL) {
                    directories = updatedDirectories
                    renderTitle()
                    directoryAdapter.submit(directories, session.bucketId)
                }
            }
        }
    }

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

    private fun renderTitle() {
        val directory = selectedTitleDirectory(
            accessStatus = accessStatus,
            bucketId = session.bucketId,
            directories = directories,
        )
        title.text = directory?.bucketName?.takeIf(String::isNotBlank)
            ?: directory?.let { getString(R.string.auv_unnamed_directory, it.mediaCount) }
            ?: getString(R.string.auv_title)
    }

    private fun toggleMedia(media: AlbumMedia) {
        lifecycleScope.launch {
            client.toggleSelection(session.sessionId, media).onSuccess { updated ->
                renderSession(updated)
                maybeAutoConfirm()
            }.onFailure {
                showMessage(it.message ?: getString(R.string.auv_selection_failed))
            }
        }
    }

    private fun maybeAutoConfirm() {
        if (
            config.maxSelectionCount == 1 &&
            config.singleSelectionFinishMode == com.github.sceneren.album.api.SingleSelectionFinishMode.IMMEDIATE &&
            session.selectedItems.size == 1
        ) {
            confirmSelection()
        }
    }

    private fun confirmSelection() {
        if (session.selectedItems.isEmpty()) {
            showMessage(getString(R.string.auv_select_first))
            return
        }
        doneAction.isEnabled = false
        lifecycleScope.launch {
            client.confirm(session.sessionId).onSuccess { result ->
                setResult(Activity.RESULT_OK, AlbumPickerIntentCodec.putResult(Intent(), result))
                finish()
            }.onFailure { failure ->
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

    private fun showMessage(message: CharSequence) {
        messageToast?.cancel()
        messageToast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).also(Toast::show)
    }

    private fun requestMediaPermission() {
        permissionLauncher.launch(AlbumMediaPermissionRequestFactory.create(config.mediaFilter))
    }

    private fun photoPickerLaunch() {
        photoPicker?.launch()
    }

    private fun cameraMediaType() = when (config.mediaFilter) {
        AlbumMediaFilter.IMAGES -> AlbumMediaType.IMAGE
        AlbumMediaFilter.VIDEOS -> AlbumMediaType.VIDEO
        AlbumMediaFilter.IMAGES_AND_VIDEOS -> if (
            config.camera.mixedMediaCaptureType == AlbumCameraCaptureType.PHOTO
        ) {
            AlbumMediaType.IMAGE
        } else {
            AlbumMediaType.VIDEO
        }
    }

    private fun actions(): List<Action> = buildList {
        if (config.camera.enabled) add(Action.CAMERA)
        if (accessStatus != MediaAccessStatus.FULL) add(Action.ADD)
    }

    private fun renderActions() {
        if (::actionAdapter.isInitialized) actionAdapter.submit(actions())
    }

    private fun onMediaPreview(media: AlbumMedia) {
        val loadedFeedItems = mediaAdapter.snapshot().items
        val initialItems = (cameraAdapter.currentItems() + loadedFeedItems).distinctBy { it.uri }
        openPreview(
            items = initialItems,
            initialIndex = initialItems.indexOfFirst { it.uri == media.uri }.coerceAtLeast(0),
            nextOffset = loadedFeedItems.size,
        )
    }

    private fun showPreview() {
        if (session.selectedItems.isEmpty()) return
        openPreview(session.selectedItems, initialIndex = 0, nextOffset = null)
    }

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

    private fun showDirectoryPanel() {
        directoryAdapter.submit(directories, session.bucketId)
        val contentHeight = directories.size * resources.getDimensionPixelSize(
            R.dimen.auv_directory_row_height,
        )
        directoryList.updateLayoutParams<ViewGroup.LayoutParams> {
            height = minOf(
                contentHeight,
                resources.getDimensionPixelSize(R.dimen.auv_directory_max_height),
                (root.height - directoryList.top).coerceAtLeast(0),
            )
        }
        directoryScrim.isVisible = true
        directoryList.isVisible = directories.isNotEmpty()
        titleArrow.animate().rotation(180f).setDuration(DIRECTORY_ARROW_DURATION_MILLIS).start()
    }

    private fun hideDirectoryPanel() {
        directoryScrim.isVisible = false
        directoryList.isVisible = false
        titleArrow.animate().rotation(0f).setDuration(DIRECTORY_ARROW_DURATION_MILLIS).start()
    }

    private fun isDirectoryPanelVisible(): Boolean = directoryScrim.isVisible

    private class DirectoryAdapter(
        private val imageLoader: AlbumImageLoader,
        private val onClick: (Long) -> Unit,
    ) : RecyclerView.Adapter<DirectoryHolder>() {
        private var items: List<AlbumDirectory> = emptyList()
        private var selectedBucketId: Long = AlbumDirectory.ALL_BUCKET_ID

        fun submit(value: List<AlbumDirectory>, selectedBucketId: Long) {
            if (items == value && this.selectedBucketId == selectedBucketId) return
            val previousItems = items
            val previousSelectedBucketId = this.selectedBucketId
            val diff = DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = previousItems.size

                    override fun getNewListSize(): Int = value.size

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                        previousItems[oldItemPosition].bucketId == value[newItemPosition].bucketId

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean {
                        val oldItem = previousItems[oldItemPosition]
                        val newItem = value[newItemPosition]
                        return oldItem == newItem &&
                            (oldItem.bucketId == previousSelectedBucketId) ==
                            (newItem.bucketId == selectedBucketId)
                    }
                },
            )
            items = value
            this.selectedBucketId = selectedBucketId
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectoryHolder =
            DirectoryHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.auv_item_album_directory,
                    parent,
                    false,
                ),
                imageLoader,
                onClick,
            )

        override fun onBindViewHolder(holder: DirectoryHolder, position: Int) {
            val directory = items[position]
            holder.bind(directory, directory.bucketId == selectedBucketId)
        }

        override fun onViewRecycled(holder: DirectoryHolder) {
            holder.clear()
        }

        override fun getItemCount(): Int = items.size
    }

    private class DirectoryHolder(
        itemView: View,
        private val imageLoader: AlbumImageLoader,
        private val onClick: (Long) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.auv_directory_cover)
        private val label: TextView = itemView.findViewById(R.id.auv_directory_label)

        fun bind(directory: AlbumDirectory, selected: Boolean) {
            clear()
            val name = if (directory.bucketId == AlbumDirectory.ALL_BUCKET_ID) {
                itemView.context.getString(R.string.auv_all_media)
            } else {
                directory.bucketName?.takeIf(String::isNotBlank)
                    ?: itemView.context.getString(R.string.auv_unnamed_directory_name)
            }
            label.text = itemView.context.getString(
                R.string.auv_directory_label,
                name,
                directory.mediaCount,
            )
            itemView.setBackgroundColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (selected) {
                        R.color.auv_directory_selected
                    } else {
                        R.color.auv_directory_panel
                    },
                ),
            )
            imageLoader.load(cover, directory.toCoverMedia(), AlbumImageTarget.GRID_THUMBNAIL)
            itemView.setOnClickListener { onClick(directory.bucketId) }
        }

        fun clear() {
            imageLoader.clear(cover)
            label.text = null
            itemView.setOnClickListener(null)
        }
    }

    private enum class Action { CAMERA, ADD }

    /** XML 单元格的功能入口适配器。 */
    private class ActionAdapter(
        private val appearance: AlbumPickerAppearance,
        private val gridMetrics: GridMetrics,
        private val onClick: (Action) -> Unit,
    ) : RecyclerView.Adapter<ActionHolder>() {
        private var items: List<Action> = emptyList()

        fun submit(value: List<Action>) {
            if (items == value) return
            val previousItems = items
            val diff = DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = previousItems.size

                    override fun getNewListSize(): Int = value.size

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                        previousItems[oldItemPosition] == value[newItemPosition]

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = areItemsTheSame(oldItemPosition, newItemPosition)
                },
            )
            items = value
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionHolder =
            ActionHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.auv_item_album_action, parent, false),
                appearance,
                parent.gridCellSize(gridMetrics),
            )

        override fun onBindViewHolder(holder: ActionHolder, position: Int) {
            holder.bind(items[position], onClick)
        }

        override fun getItemCount(): Int = items.size
    }

    private class ActionHolder(
        itemView: View,
        private val appearance: AlbumPickerAppearance,
        cellSize: Int,
    ) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.auv_action_icon)
        private val label: TextView = itemView.findViewById(R.id.auv_action_label)

        init {
            itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cellSize,
            )
        }

        fun bind(action: Action, onClick: (Action) -> Unit) {
            val context = itemView.context
            label.text = when (action) {
                Action.CAMERA -> context.getString(R.string.auv_capture)
                Action.ADD -> context.getString(R.string.auv_add_more)
            }
            val primary = appearance.primaryTextColor ?: context.color(R.color.auv_primary)
            label.setTextColor(primary)
            val customIcon = when (action) {
                Action.CAMERA -> appearance.cameraIconRes
                Action.ADD -> appearance.addIconRes
            }
            icon.setImageResource(
                customIcon ?: when (action) {
                    Action.CAMERA -> R.drawable.auv_ic_album_camera
                    Action.ADD -> R.drawable.auv_ic_album_add
                },
            )
            if (customIcon == null) icon.setColorFilter(primary) else icon.clearColorFilter()
            itemView.setOnClickListener { onClick(action) }
        }
    }

    private class CameraAdapter(
        private val appearance: AlbumPickerAppearance,
        private val gridMetrics: GridMetrics,
        private val imageLoader: AlbumImageLoader,
        private val onPreview: (AlbumMedia) -> Unit,
        private val onToggle: (AlbumMedia) -> Unit,
    ) : RecyclerView.Adapter<MediaHolder>() {
        private var items: List<AlbumMedia> = emptyList()
        private var selectedUris: Set<android.net.Uri> = emptySet()

        fun submit(value: List<AlbumMedia>, selectedUris: Set<android.net.Uri>) {
            if (items == value && this.selectedUris == selectedUris) return
            val previousItems = items
            val previousSelectedUris = this.selectedUris
            val diff = DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = previousItems.size

                    override fun getNewListSize(): Int = value.size

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                        previousItems[oldItemPosition].uri == value[newItemPosition].uri

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean {
                        val oldItem = previousItems[oldItemPosition]
                        val newItem = value[newItemPosition]
                        return oldItem == newItem &&
                            (oldItem.uri in previousSelectedUris) ==
                            (newItem.uri in selectedUris)
                    }
                },
            )
            items = value
            this.selectedUris = selectedUris
            diff.dispatchUpdatesTo(this)
        }

        fun currentItems(): List<AlbumMedia> = items

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder =
            MediaHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.auv_item_album_media, parent, false),
                appearance,
                imageLoader,
                parent.gridCellSize(gridMetrics),
            )

        override fun onBindViewHolder(holder: MediaHolder, position: Int) {
            holder.bind(items[position], selectedUris, onPreview, onToggle)
        }

        override fun onViewRecycled(holder: MediaHolder) {
            holder.clear()
        }

        override fun getItemCount(): Int = items.size
    }

    private class GalleryAdapter(
        private val appearance: AlbumPickerAppearance,
        private val gridMetrics: GridMetrics,
        private val imageLoader: AlbumImageLoader,
        private val onPreview: (AlbumMedia) -> Unit,
        private val onToggle: (AlbumMedia) -> Unit,
    ) : PagingDataAdapter<AlbumMedia, MediaHolder>(DIFF) {
        private var selectedUris: Set<android.net.Uri> = emptySet()

        fun updateSelection(value: Set<android.net.Uri>) {
            val changedUris = (selectedUris - value) + (value - selectedUris)
            if (changedUris.isEmpty()) return
            selectedUris = value
            for (index in 0 until itemCount) {
                if (peek(index)?.uri in changedUris) notifyItemChanged(index)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder =
            MediaHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.auv_item_album_media, parent, false),
                appearance,
                imageLoader,
                parent.gridCellSize(gridMetrics),
            )

        override fun onBindViewHolder(holder: MediaHolder, position: Int) {
            val item = getItem(position)
            if (item != null) {
                holder.bind(item, selectedUris, onPreview, onToggle)
            } else {
                holder.clear()
            }
        }

        override fun onViewRecycled(holder: MediaHolder) {
            holder.clear()
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<AlbumMedia>() {
                override fun areItemsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia): Boolean =
                    oldItem.uri == newItem.uri

                override fun areContentsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia): Boolean =
                    oldItem == newItem
            }
        }
    }

    private class MediaHolder(
        itemView: View,
        private val appearance: AlbumPickerAppearance,
        private val imageLoader: AlbumImageLoader,
        cellSize: Int,
    ) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.auv_media_image)
        private val check: ImageView = itemView.findViewById(R.id.auv_media_check)
        private var boundMedia: AlbumMedia? = null

        init {
            itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cellSize,
            )
        }

        fun bind(
            media: AlbumMedia,
            selected: Set<android.net.Uri>,
            onPreview: (AlbumMedia) -> Unit,
            onToggle: (AlbumMedia) -> Unit,
        ) {
            if (boundMedia != media) {
                imageLoader.clear(image)
                imageLoader.load(image, media, AlbumImageTarget.GRID_THUMBNAIL)
                boundMedia = media
            }
            val checked = media.uri in selected
            val checkIcon = if (checked) {
                appearance.checkedIconRes ?: R.drawable.auv_ic_album_checked
            } else {
                appearance.uncheckedIconRes ?: R.drawable.auv_ic_album_unchecked
            }
//            check.text = ""
//            check.setCompoundDrawablesWithIntrinsicBounds(checkIcon, 0, 0, 0)
            check.setImageResource(checkIcon)
            check.setBackgroundColor(appearance.scrimColor ?: Color.TRANSPARENT)
            check.setOnClickListener { onToggle(media) }
            itemView.setOnClickListener { onPreview(media) }
        }

        fun clear() {
            boundMedia = null
            imageLoader.clear(image)
            check.setImageDrawable(null)
            check.setBackgroundColor(Color.TRANSPARENT)
            check.setOnClickListener(null)
            itemView.setOnClickListener(null)
        }
    }

    private fun color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt().coerceAtLeast(0)
}

private const val LIGHT_COLOR_LUMINANCE = 0.5
private const val DIRECTORY_ARROW_DURATION_MILLIS = 150L

internal fun shouldShowPermissionUpgradeButton(
    isAllowedByHost: Boolean,
    accessStatus: MediaAccessStatus,
): Boolean = isAllowedByHost && accessStatus != MediaAccessStatus.FULL

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

private fun AlbumDirectory.toCoverMedia() = AlbumMedia(
    uri = coverUri,
    mediaType = coverMediaType,
    displayName = bucketName,
    mimeType = null,
    sizeBytes = null,
    dateAddedEpochSeconds = null,
    dateModifiedEpochSeconds = null,
    width = null,
    height = null,
    durationMillis = null,
    bucketId = bucketId,
    bucketName = bucketName,
    selectedAtEpochMillis = null,
    source = AlbumMediaSource.MEDIA_STORE,
)

private data class GridMetrics(
    val spanCount: Int,
    val spacingPx: Int,
)

/** 仅在 item 之间增加间距，不给 RecyclerView 外边缘增加额外留白。 */
private class GridSpacingItemDecoration(
    private val metrics: GridMetrics,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.set(0, 0, 0, 0)
        if (metrics.spacingPx == 0) return

        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % metrics.spanCount
        outRect.left = ((column.toLong() * metrics.spacingPx) / metrics.spanCount).toInt()
        val nextColumnOffset = (((column + 1L) * metrics.spacingPx) / metrics.spanCount).toInt()
        outRect.right = metrics.spacingPx - nextColumnOffset
        if (position >= metrics.spanCount) outRect.top = metrics.spacingPx
    }
}

private fun View.gridCellSize(metrics: GridMetrics): Int {
    val gridWidth = measuredWidth.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
    val contentWidth = gridWidth.toLong() - paddingLeft - paddingRight
    val totalSpacing = metrics.spacingPx.toLong() * (metrics.spanCount - 1)
    return ((contentWidth - totalSpacing) / metrics.spanCount)
        .coerceAtLeast(1L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private fun Context.color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)
