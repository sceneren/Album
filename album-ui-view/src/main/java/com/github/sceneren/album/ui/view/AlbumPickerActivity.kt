package com.github.sceneren.album.ui.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
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
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaPermissionRequestFactory
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
    private lateinit var cancelButton: TextView
    private lateinit var previewButton: Button
    private lateinit var doneButton: Button
    private lateinit var permissionButton: Button
    private lateinit var actionAdapter: ActionAdapter
    private lateinit var cameraAdapter: CameraAdapter
    private lateinit var mediaAdapter: GalleryAdapter

    private var cameraLauncher: com.github.sceneren.album.api.AlbumCameraLauncher? = null
    private var photoPicker: com.github.sceneren.album.api.AlbumPhotoPickerLauncher? = null
    private var feedJob: Job? = null
    private var messageToast: Toast? = null
    private var accessStatus: MediaAccessStatus = MediaAccessStatus.DENIED

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
                lifecycleScope.launch {
                    client.cancel(session.sessionId)
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        })
        refreshContent()
    }

    override fun onDestroy() {
        feedJob?.cancel()
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
        cancelButton = findViewById(R.id.auv_picker_cancel)
        previewButton = findViewById(R.id.auv_picker_preview)
        doneButton = findViewById(R.id.auv_picker_done)
        permissionButton = findViewById(R.id.auv_picker_permission)

        findViewById<ImageButton>(R.id.auv_picker_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        titleAction.setOnClickListener { showDirectories() }
        cancelButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        previewButton.setOnClickListener { showPreview() }
        doneButton.setOnClickListener { confirmSelection() }
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
        doneButton.setTextColor(accentColor)
        appearance.backIconRes?.let { findViewById<ImageButton>(R.id.auv_picker_back).setImageResource(it) }
        val customFolderIcon = appearance.folderIconRes
        titleArrow.setImageResource(customFolderIcon ?: R.drawable.auv_ic_album_expand_more)
        if (customFolderIcon == null) {
            titleArrow.setColorFilter(primaryColor)
        } else {
            titleArrow.clearColorFilter()
        }
        appearance.doneIconRes?.let { doneButton.setCompoundDrawablesWithIntrinsicBounds(it, 0, 0, 0) }
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
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun refreshContent() {
        accessStatus = api.getMediaAccessStatus(config.mediaFilter)
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
    }

    private fun renderSession(updated: AlbumPickerSessionSnapshot) {
        session = updated
        cameraAdapter.selectedUris = updated.selectedUris
        cameraAdapter.submit(
            if (accessStatus == MediaAccessStatus.FULL) updated.cameraItems else emptyList(),
        )
        mediaAdapter.selectedUris = updated.selectedUris
        mediaAdapter.notifyDataSetChanged()
        previewButton.text = if (updated.selectedItems.isEmpty()) {
            getString(R.string.auv_preview)
        } else {
            getString(R.string.auv_preview_count, updated.selectedItems.size)
        }
        doneButton.text = if (updated.selectedItems.isEmpty()) {
            getString(R.string.auv_please_select)
        } else {
            getString(R.string.auv_done_count, updated.selectedItems.size)
        }
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
        doneButton.isEnabled = false
        lifecycleScope.launch {
            client.confirm(session.sessionId).onSuccess { result ->
                setResult(Activity.RESULT_OK, AlbumPickerIntentCodec.putResult(Intent(), result))
                finish()
            }.onFailure { failure ->
                doneButton.isEnabled = true
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
        AlbumPreviewDialog(
            activity = this,
            appearance = appearance,
            imageLoader = imageLoader,
            scope = lifecycleScope,
            initialItems = items,
            initialIndex = initialIndex,
            nextOffset = nextOffset,
            loadMore = { offset, limit ->
                api.loadMediaPage(
                    mediaFilter = config.mediaFilter,
                    bucketId = session.bucketId,
                    offset = offset,
                    limit = limit,
                )
            },
        )
    }

    private fun showDirectories() {
        if (accessStatus != MediaAccessStatus.FULL) return
        lifecycleScope.launch {
            val popup = PopupMenu(this@AlbumPickerActivity, title)
            popup.menu.add(R.string.auv_all_media).setOnMenuItemClickListener {
                lifecycleScope.launch {
                    client.setBucket(session.sessionId, Long.MIN_VALUE)
                    refreshContent()
                }
                true
            }
            api.getMediaDirectories(config.mediaFilter).getOrNull().orEmpty().forEach { directory ->
                popup.menu.add(
                    directory.bucketName ?: getString(
                        R.string.auv_unnamed_directory,
                        directory.mediaCount,
                    ),
                ).setOnMenuItemClickListener {
                    lifecycleScope.launch {
                        client.setBucket(session.sessionId, directory.bucketId)
                        refreshContent()
                    }
                    true
                }
            }
            popup.show()
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
            items = value
            notifyDataSetChanged()
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
        var selectedUris: Set<android.net.Uri> = emptySet()

        fun submit(value: List<AlbumMedia>) {
            items = value
            notifyDataSetChanged()
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
        var selectedUris: Set<android.net.Uri> = emptySet()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder =
            MediaHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.auv_item_album_media, parent, false),
                appearance,
                imageLoader,
                parent.gridCellSize(gridMetrics),
            )

        override fun onBindViewHolder(holder: MediaHolder, position: Int) {
            getItem(position)?.let { holder.bind(it, selectedUris, onPreview, onToggle) }
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
            imageLoader.clear(image)
            imageLoader.load(image, media, AlbumImageTarget.GRID_THUMBNAIL)
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
            imageLoader.clear(image)
            check.setOnClickListener(null)
            itemView.setOnClickListener(null)
        }
    }

    private fun color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt().coerceAtLeast(0)
}

private const val LIGHT_COLOR_LUMINANCE = 0.5

internal fun shouldShowPermissionUpgradeButton(
    isAllowedByHost: Boolean,
    accessStatus: MediaAccessStatus,
): Boolean = isAllowedByHost && accessStatus != MediaAccessStatus.FULL

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
