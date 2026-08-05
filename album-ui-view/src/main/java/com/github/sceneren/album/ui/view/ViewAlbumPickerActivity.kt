package com.github.sceneren.album.ui.view

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import coil3.load
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

/** 基于 XML 布局的 View 全屏相册选择页。 */
class ViewAlbumPickerActivity : ComponentActivity() {
    private lateinit var config: com.github.sceneren.album.api.AlbumPickerConfig
    private lateinit var appearance: ViewAlbumPickerAppearance
    private lateinit var api: AlbumApi
    private lateinit var client: com.github.sceneren.album.api.AlbumPickerClient
    private lateinit var session: AlbumPickerSessionSnapshot

    private lateinit var root: View
    private lateinit var toolbar: View
    private lateinit var bottomBar: View
    private lateinit var grid: RecyclerView
    private lateinit var title: TextView
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
        val theme = intent.getIntExtra(ViewAlbumPickerExtras.THEME, 0)
        if (theme != 0) setTheme(theme)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        config = AlbumPickerIntentCodec.readConfig(intent)
        appearance = ViewAlbumPickerExtras.readAppearance(intent)
        api = AlbumApi.create(this)
        client = api.createPickerClient(this)
        val sessionId = requireNotNull(intent.getStringExtra(AlbumPickerIntentCodec.EXTRA_SESSION_ID))
        session = client.openSession(config, sessionId)

        photoPicker = api.registerPhotoPicker(this, config.mediaFilter, config.maxSelectionCount) { result ->
            showMessage(
                when (result) {
                is PhotoPickResult.Selected -> getString(R.string.album_view_added_count, result.media.size)
                PhotoPickResult.Cancelled -> getString(R.string.album_view_add_cancelled)
                is PhotoPickResult.Failed -> getString(R.string.album_view_add_failed, result.reason.name)
                },
            )
            refreshContent()
        }
        cameraLauncher = client.registerCamera(this, session.sessionId) { result ->
            result.onSuccess { updated ->
                renderSession(updated)
                maybeAutoConfirm()
            }.onFailure { failure ->
                showMessage(failure.message ?: getString(R.string.album_view_camera_failed))
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

    /** 所有页面控件均由 activity_view_album_picker.xml 声明。 */
    private fun bindViews() {
        setContentView(R.layout.activity_view_album_picker)
        root = findViewById(R.id.album_picker_root)
        toolbar = findViewById(R.id.album_picker_toolbar)
        bottomBar = findViewById(R.id.album_picker_bottom)
        grid = findViewById(R.id.album_picker_grid)
        title = findViewById(R.id.album_picker_title)
        cancelButton = findViewById(R.id.album_picker_cancel)
        previewButton = findViewById(R.id.album_picker_preview)
        doneButton = findViewById(R.id.album_picker_done)
        permissionButton = findViewById(R.id.album_picker_permission)

        findViewById<ImageButton>(R.id.album_picker_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        title.setOnClickListener { showDirectories() }
        cancelButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        previewButton.setOnClickListener { showPreview() }
        doneButton.setOnClickListener { confirmSelection() }
        permissionButton.setOnClickListener { requestMediaPermission() }

        grid.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
        grid.itemAnimator = null
        actionAdapter = ActionAdapter(appearance) { action ->
            when (action) {
                Action.CAMERA -> cameraLauncher?.launch(cameraMediaType())
                Action.ADD -> photoPickerLaunch()
            }
        }
        cameraAdapter = CameraAdapter(appearance, ::onMediaPreview, ::toggleMedia)
        mediaAdapter = GalleryAdapter(appearance, ::onMediaPreview, ::toggleMedia)
        grid.adapter = ConcatAdapter(actionAdapter, cameraAdapter, mediaAdapter)
    }

    /** 将可配置外观应用到 XML 中已声明的控件，不创建新的界面层级。 */
    private fun applyAppearance() {
        val toolbarColor = appearance.toolbarColor ?: color(R.color.album_view_toolbar)
        val bottomColor = appearance.bottomBarColor ?: color(R.color.album_view_bottom)
        val primaryColor = appearance.primaryTextColor ?: color(R.color.album_view_primary)
        val accentColor = appearance.accentColor ?: color(R.color.album_view_accent)
        root.setBackgroundColor(toolbarColor)
        toolbar.setBackgroundColor(toolbarColor)
        bottomBar.setBackgroundColor(bottomColor)
        title.setTextColor(primaryColor)
        cancelButton.setTextColor(primaryColor)
        previewButton.setTextColor(primaryColor)
        doneButton.setTextColor(accentColor)
        appearance.backIconRes?.let { findViewById<ImageButton>(R.id.album_picker_back).setImageResource(it) }
        appearance.folderIconRes?.let { title.setCompoundDrawablesWithIntrinsicBounds(0, 0, it, 0) }
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
            accessStatus != MediaAccessStatus.FULL && config.showPermissionUpgrade
        ) View.VISIBLE else View.GONE
        permissionButton.text = when (accessStatus) {
            MediaAccessStatus.PARTIAL -> getString(R.string.album_view_partial_permission)
            MediaAccessStatus.DENIED -> getString(R.string.album_view_denied_permission)
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
        cameraAdapter.submit(updated.cameraItems)
        mediaAdapter.selectedUris = updated.selectedUris
        mediaAdapter.notifyDataSetChanged()
        previewButton.text = if (updated.selectedItems.isEmpty()) {
            getString(R.string.album_view_preview)
        } else {
            getString(R.string.album_view_preview_count, updated.selectedItems.size)
        }
        doneButton.text = if (updated.selectedItems.isEmpty()) {
            getString(R.string.album_view_please_select)
        } else {
            getString(R.string.album_view_done_count, updated.selectedItems.size)
        }
    }

    private fun toggleMedia(media: AlbumMedia) {
        lifecycleScope.launch {
            client.toggleSelection(session.sessionId, media).onSuccess { updated ->
                renderSession(updated)
                maybeAutoConfirm()
            }.onFailure {
                showMessage(it.message ?: getString(R.string.album_view_selection_failed))
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
            showMessage(getString(R.string.album_view_select_first))
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
                        R.string.album_view_process_failed,
                        failure.message ?: getString(R.string.album_view_retry),
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
        Dialog(this, R.style.Theme_AlbumUiView_Preview).apply {
            setContentView(R.layout.dialog_album_preview)
            findViewById<ImageView>(R.id.album_preview_image).apply {
                setBackgroundColor(appearance.previewBackgroundColor ?: color(android.R.color.black))
                load(media.uri)
            }
            show()
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(appearance.previewBackgroundColor ?: color(android.R.color.black)))
            }
        }
    }

    private fun showPreview() {
        session.selectedItems.firstOrNull()?.let(::onMediaPreview)
    }

    private fun showDirectories() {
        if (accessStatus != MediaAccessStatus.FULL) return
        lifecycleScope.launch {
            val popup = PopupMenu(this@ViewAlbumPickerActivity, title)
            popup.menu.add(R.string.album_view_all_media).setOnMenuItemClickListener {
                lifecycleScope.launch {
                    client.setBucket(session.sessionId, Long.MIN_VALUE)
                    refreshContent()
                }
                true
            }
            api.getMediaDirectories(config.mediaFilter).getOrNull().orEmpty().forEach { directory ->
                popup.menu.add(
                    directory.bucketName ?: getString(
                        R.string.album_view_unnamed_directory,
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
        private val appearance: ViewAlbumPickerAppearance,
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
                LayoutInflater.from(parent.context).inflate(R.layout.item_album_action, parent, false),
                appearance,
                parent.context.gridCellSize(),
            )

        override fun onBindViewHolder(holder: ActionHolder, position: Int) {
            holder.bind(items[position], onClick)
        }

        override fun getItemCount(): Int = items.size
    }

    private class ActionHolder(
        itemView: View,
        private val appearance: ViewAlbumPickerAppearance,
        cellSize: Int,
    ) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.album_action_icon)
        private val label: TextView = itemView.findViewById(R.id.album_action_label)

        init {
            itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cellSize,
            )
        }

        fun bind(action: Action, onClick: (Action) -> Unit) {
            val context = itemView.context
            label.text = when (action) {
                Action.CAMERA -> context.getString(R.string.album_view_capture)
                Action.ADD -> context.getString(R.string.album_view_add_more)
            }
            val primary = appearance.primaryTextColor ?: context.color(R.color.album_view_primary)
            label.setTextColor(primary)
            val customIcon = when (action) {
                Action.CAMERA -> appearance.cameraIconRes
                Action.ADD -> appearance.addIconRes
            }
            icon.setImageResource(
                customIcon ?: when (action) {
                    Action.CAMERA -> R.drawable.ic_album_camera
                    Action.ADD -> R.drawable.ic_album_add
                },
            )
            if (customIcon == null) icon.setColorFilter(primary) else icon.clearColorFilter()
            itemView.setOnClickListener { onClick(action) }
        }
    }

    private class CameraAdapter(
        private val appearance: ViewAlbumPickerAppearance,
        private val onPreview: (AlbumMedia) -> Unit,
        private val onToggle: (AlbumMedia) -> Unit,
    ) : RecyclerView.Adapter<MediaHolder>() {
        private var items: List<AlbumMedia> = emptyList()
        var selectedUris: Set<android.net.Uri> = emptySet()

        fun submit(value: List<AlbumMedia>) {
            items = value
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder =
            MediaHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_album_media, parent, false),
                appearance,
                parent.context.gridCellSize(),
            )

        override fun onBindViewHolder(holder: MediaHolder, position: Int) {
            holder.bind(items[position], selectedUris, onPreview, onToggle)
        }

        override fun getItemCount(): Int = items.size
    }

    private class GalleryAdapter(
        private val appearance: ViewAlbumPickerAppearance,
        private val onPreview: (AlbumMedia) -> Unit,
        private val onToggle: (AlbumMedia) -> Unit,
    ) : PagingDataAdapter<AlbumMedia, MediaHolder>(DIFF) {
        var selectedUris: Set<android.net.Uri> = emptySet()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder =
            MediaHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_album_media, parent, false),
                appearance,
                parent.context.gridCellSize(),
            )

        override fun onBindViewHolder(holder: MediaHolder, position: Int) {
            getItem(position)?.let { holder.bind(it, selectedUris, onPreview, onToggle) }
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
        private val appearance: ViewAlbumPickerAppearance,
        cellSize: Int,
    ) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.album_media_image)
        private val check: TextView = itemView.findViewById(R.id.album_media_check)

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
            image.load(media.uri)
            val checked = media.uri in selected
            val checkIcon = if (checked) appearance.checkedIconRes else appearance.uncheckedIconRes
            if (checkIcon != null) {
                check.text = ""
                check.setCompoundDrawablesWithIntrinsicBounds(checkIcon, 0, 0, 0)
            } else {
                check.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                check.text = if (checked) "✓" else "○"
            }
            check.setBackgroundColor(appearance.scrimColor ?: itemView.context.color(android.R.color.transparent))
            check.setOnClickListener { onToggle(media) }
            itemView.setOnClickListener { onPreview(media) }
        }
    }

    private fun color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)
}

private const val GRID_SPAN_COUNT = 4
private const val LIGHT_COLOR_LUMINANCE = 0.5

private fun Context.gridCellSize(): Int = resources.displayMetrics.widthPixels / GRID_SPAN_COUNT

private fun Context.color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)
