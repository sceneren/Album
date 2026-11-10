package com.github.sceneren.album.ui.view

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.viewpager2.widget.ViewPager2
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.ui.view.internal.video.PreviewVideoControls
import com.github.sceneren.album.ui.view.internal.video.PreviewVideoPlayback
import com.github.sceneren.album.video.AlbumVideoPlayerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** 使用 XML、ViewPager2 和 ZoomImageView 实现的全屏媒体预览。 */
internal class AlbumPreviewDialog(
    private val activity: ComponentActivity,
    private val appearance: AlbumPickerAppearance,
    imageLoader: AlbumImageLoader,
    private val scope: CoroutineScope,
    initialItems: List<AlbumMedia>,
    private val initialIndex: Int,
    initialSelectedUris: Set<Uri>,
    private var nextOffset: Int?,
    private val onToggleSelection: (AlbumMedia) -> Unit,
    private val onConfirm: () -> Unit,
    private val onDismiss: (AlbumPreviewDialog) -> Unit,
    private val loadMore: suspend (offset: Int, limit: Int) -> Result<List<AlbumMedia>>,
    private val videoPlayerFactory: AlbumVideoPlayerFactory? = AlbumUi.videoPlayerFactoryOrNull(),
    private val transitionOrigin: PreviewTransitionOrigin? = null,
) {
    private val dialog = Dialog(activity, R.style.auv_theme_album_picker_preview)
    private val videoPlayback = videoPlayerFactory?.let { factory ->
        PreviewVideoPlayback({ factory.create(activity) }) { state ->
            if (::videoControls.isInitialized) videoControls.render(state)
        }
    }
    private val adapter = PreviewAdapter(
        appearance,
        imageLoader,
        initialItems,
        videoPlaybackEnabled = videoPlayerFactory != null,
        onVideoOutputChanged = { videoPlayback?.setOutput(it) },
    )
    private lateinit var root: View
    private lateinit var toolbar: View
    private lateinit var bottomBar: View
    private lateinit var pager: ViewPager2
    private lateinit var pageCounter: TextView
    private lateinit var selectionButton: ImageButton
    private lateinit var doneAction: LinearLayout
    private lateinit var selectedCount: TextView
    private lateinit var doneText: TextView
    private lateinit var videoBack: ImageButton
    private lateinit var videoControls: PreviewVideoControls
    private var selectedUris: Set<Uri> = initialSelectedUris.toSet()
    private var isShowing = false
    private var closed = false
    private var sharedElementTransition: PreviewSharedElementTransition? = null
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> videoPlayback?.setResumed(isShowing)
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> videoPlayback?.setResumed(false)
            Lifecycle.Event.ON_DESTROY -> dismiss()
            else -> Unit
        }
    }
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            currentPosition = position
            updateChrome()
            updateVideoPage()
            maybeLoadMore(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            videoPlayback?.setSettled(state == ViewPager2.SCROLL_STATE_IDLE)
        }
    }
    private var currentPosition: Int = 0
    private var loadJob: Job? = null
    private var endReached = nextOffset == null

    init {
        dialog.setContentView(R.layout.auv_dialog_album_preview)
        bindViews()
        applyAppearance()
        applySystemBarInsets()

        pager.adapter = adapter
        pager.registerOnPageChangeCallback(pageChangeCallback)
        dialog.setOnDismissListener {
            closePlayback()
            loadJob?.cancel()
            pager.unregisterOnPageChangeCallback(pageChangeCallback)
            pager.adapter = null
            onDismiss(this)
        }
    }

    @Suppress("DEPRECATION")
    /** 执行 `show` 方法定义的处理。 */
    fun show() {
        if (closed) return
        transitionOrigin?.let { origin ->
            sharedElementTransition = PreviewSharedElementTransition(
                root = root as ViewGroup,
                pager = pager,
                chrome = listOf(toolbar, bottomBar),
                origin = origin,
                media = adapter.itemAt(initialIndex.coerceIn(0, adapter.itemCount - 1)) ?: return@let,
                backgroundColor = previewBackgroundColor(),
            ).also { it.prepare() }
        }
        dialog.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        dialog.show()
        isShowing = true
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(
                (if (sharedElementTransition == null) previewBackgroundColor() else Color.TRANSPARENT).toDrawable(),
            )
            // The image's own bounds animation owns this transition.
            setWindowAnimations(0)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(this, root).apply {
                isAppearanceLightStatusBars =
                    ColorUtils.calculateLuminance(toolbarColor()) > LIGHT_COLOR_LUMINANCE
                isAppearanceLightNavigationBars =
                    ColorUtils.calculateLuminance(bottomBarColor()) > LIGHT_COLOR_LUMINANCE
            }
        }
        ViewCompat.requestApplyInsets(root)

        val targetIndex = initialIndex.coerceIn(0, (adapter.itemCount - 1).coerceAtLeast(0))
        currentPosition = targetIndex
        pager.setCurrentItem(targetIndex, false)
        updateChrome()
        updateVideoPage()
        videoPlayback?.setSettled(pager.scrollState == ViewPager2.SCROLL_STATE_IDLE)
        activity.lifecycle.addObserver(lifecycleObserver)
        videoPlayback?.setResumed(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        pager.post { if (isShowing) maybeLoadMore(targetIndex) }
        root.doOnPreDraw {
            if (isShowing) sharedElementTransition?.start()
        }
    }

    /** 执行 `dismiss` 方法定义的处理。 */
    fun dismiss() {
        closePlayback()
        dialog.dismiss()
    }

    private fun closePlayback() {
        if (closed) return
        closed = true
        isShowing = false
        activity.lifecycle.removeObserver(lifecycleObserver)
        sharedElementTransition?.cancel()
        sharedElementTransition = null
        videoPlayback?.close()
        if (::videoControls.isInitialized) videoControls.clear()
    }

    private fun updateVideoPage() {
        val video = adapter.itemAt(currentPosition)?.takeIf { it.mediaType == AlbumMediaType.VIDEO }
        videoPlayback?.select(video?.uri)
        adapter.selectPosition(currentPosition)
    }

    /** 更新 `updateSelection` 对应的状态。 */
    fun updateSelection(value: Set<Uri>) {
        selectedUris = value.toSet()
        updateChrome()
    }

    /** 执行 `bindViews` 方法定义的处理。 */
    private fun bindViews() {
        root = dialog.findViewById(R.id.auv_preview_root)
        toolbar = dialog.findViewById(R.id.auv_preview_toolbar)
        bottomBar = dialog.findViewById(R.id.auv_preview_bottom)
        pager = dialog.findViewById(R.id.auv_preview_pager)
        pageCounter = dialog.findViewById(R.id.auv_preview_page_counter)
        selectionButton = dialog.findViewById(R.id.auv_preview_selection)
        doneAction = dialog.findViewById(R.id.auv_preview_done_action)
        selectedCount = dialog.findViewById(R.id.auv_preview_selected_count)
        doneText = dialog.findViewById(R.id.auv_preview_done_text)
        videoBack = dialog.findViewById(R.id.auv_preview_video_back)
        videoPlayback?.let { videoControls = PreviewVideoControls(root, it) }

        dialog.findViewById<ImageButton>(R.id.auv_preview_back).setOnClickListener { dismiss() }
        videoBack.setOnClickListener { dismiss() }
        selectionButton.setOnClickListener {
            adapter.itemAt(currentPosition)?.let(onToggleSelection)
        }
        doneAction.setOnClickListener {
            if (selectedUris.isNotEmpty()) {
                dismiss()
                onConfirm()
            }
        }
    }

    /** 执行 `applyAppearance` 方法定义的处理。 */
    private fun applyAppearance() {
        val previewColor = previewBackgroundColor()
        val primaryColor = appearance.primaryTextColor
            ?: activity.getColorCompat(R.color.auv_primary)
        val accentColor = appearance.accentColor
            ?: activity.getColorCompat(R.color.auv_accent)

        root.setBackgroundColor(previewColor)
        pager.setBackgroundColor(previewColor)
        toolbar.setBackgroundColor(toolbarColor())
        bottomBar.setBackgroundColor(bottomBarColor())
        pageCounter.setTextColor(primaryColor)
        selectedCount.setTextColor(primaryColor)
        selectedCount.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor)
        }
        doneText.compoundDrawablePadding = activity.dpToPx(4)
        val backIcon = appearance.backIconRes ?: R.drawable.auv_ic_album_back
        dialog.findViewById<ImageButton>(R.id.auv_preview_back).setImageResource(backIcon)
        videoBack.setImageResource(backIcon)
    }

    /** 执行 `applySystemBarInsets` 方法定义的处理。 */
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
            applyVideoInsets(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom)
            insets
        }
    }

    private fun applyVideoInsets(left: Int, top: Int, right: Int, bottom: Int) {
        val margin = activity.resources.getDimensionPixelSize(R.dimen.auv_video_controls_margin)
        val errorMargin = activity.resources.getDimensionPixelSize(R.dimen.auv_video_error_bottom_margin)
        val isRtl = ViewCompat.getLayoutDirection(root) == ViewCompat.LAYOUT_DIRECTION_RTL
        val startInset = if (isRtl) right else left
        val endInset = if (isRtl) left else right
        videoBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = activity.dpToPx(8) + startInset
            topMargin = activity.dpToPx(4) + top
        }
        root.findViewById<View>(R.id.auv_preview_video_controls)
            .updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = margin + left
                rightMargin = margin + right
                marginStart = margin + startInset
                marginEnd = margin + endInset
                bottomMargin = margin + bottom
            }
        root.findViewById<View>(R.id.auv_preview_video_error)
            .updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = margin + left
                rightMargin = margin + right
                marginStart = margin + startInset
                marginEnd = margin + endInset
                bottomMargin = errorMargin + bottom
            }
    }

    /** 更新 `updateChrome` 对应的状态。 */
    internal fun updateChrome() {
        if (adapter.itemCount == 0) return
        currentPosition = currentPosition.coerceIn(0, adapter.itemCount - 1)
        pageCounter.text = activity.getString(
            R.string.auv_preview_position,
            currentPosition + 1,
            adapter.itemCount,
        )

        val currentMedia = adapter.itemAt(currentPosition)
        val playableVideo = videoPlayerFactory != null && currentMedia?.mediaType == AlbumMediaType.VIDEO
        toolbar.visibility = if (playableVideo) View.GONE else View.VISIBLE
        bottomBar.visibility = if (playableVideo) View.GONE else View.VISIBLE
        videoBack.visibility = if (playableVideo) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.auv_preview_video_controls).visibility =
            if (playableVideo) View.VISIBLE else View.GONE
        if (!playableVideo) root.findViewById<View>(R.id.auv_preview_video_error).visibility = View.GONE
        dialog.window?.let { window ->
            WindowCompat.getInsetsController(window, root).apply {
                isAppearanceLightStatusBars = !playableVideo &&
                    ColorUtils.calculateLuminance(toolbarColor()) > LIGHT_COLOR_LUMINANCE
                isAppearanceLightNavigationBars = !playableVideo &&
                    ColorUtils.calculateLuminance(bottomBarColor()) > LIGHT_COLOR_LUMINANCE
            }
        }
        val isSelected = currentMedia?.uri in selectedUris
        selectionButton.setImageResource(
            if (isSelected) {
                appearance.checkedIconRes ?: R.drawable.auv_ic_album_checked
            } else {
                appearance.uncheckedIconRes ?: R.drawable.auv_ic_album_unchecked
            },
        )
        selectionButton.isSelected = isSelected

        val hasSelection = selectedUris.isNotEmpty()
        selectedCount.visibility = if (hasSelection) View.VISIBLE else View.GONE
        selectedCount.text = String.format(Locale.getDefault(), "%d", selectedUris.size)
        doneText.text = activity.getString(
            if (hasSelection) R.string.auv_done else R.string.auv_please_select,
        )
        doneText.setTextColor(
            if (hasSelection) {
                appearance.accentColor ?: activity.getColorCompat(R.color.auv_accent)
            } else {
                appearance.secondaryTextColor ?: activity.getColorCompat(R.color.auv_secondary)
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

    /** 执行 `maybeLoadMore` 方法定义的处理。 */
    internal fun maybeLoadMore(position: Int) {
        val offset = nextOffset ?: return
        if (endReached || loadJob?.isActive == true) return
        if (position < adapter.itemCount - PREVIEW_PREFETCH_DISTANCE) return

        loadJob = scope.launch {
            loadMore(offset, PREVIEW_PAGE_SIZE)
                .onSuccess { page ->
                    nextOffset = offset + page.size
                    if (page.size < PREVIEW_PAGE_SIZE) endReached = true
                    adapter.append(page)
                    updateChrome()
                }
                .onFailure { failure ->
                    Toast.makeText(
                        activity.applicationContext,
                        failure.message ?: activity.getString(R.string.auv_preview_load_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }

    /** 将当前对象转换为 `toolbarColor` 对应的结果。 */
    private fun toolbarColor(): Int =
        appearance.toolbarColor ?: activity.getColorCompat(R.color.auv_toolbar)

    /** 执行 `bottomBarColor` 方法定义的处理。 */
    private fun bottomBarColor(): Int =
        appearance.bottomBarColor ?: activity.getColorCompat(R.color.auv_bottom)

    /** 执行 `previewBackgroundColor` 方法定义的处理。 */
    private fun previewBackgroundColor(): Int =
        appearance.previewBackgroundColor ?: activity.getColorCompat(android.R.color.black)

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `PREVIEW_PAGE_SIZE` 对应的数据。 */
        const val PREVIEW_PAGE_SIZE = 30
        /** 表示 `PREVIEW_PREFETCH_DISTANCE` 对应的数据。 */
        const val PREVIEW_PREFETCH_DISTANCE = 3
        /** 表示 `LIGHT_COLOR_LUMINANCE` 对应的数据。 */
        const val LIGHT_COLOR_LUMINANCE = 0.5
    }
}
