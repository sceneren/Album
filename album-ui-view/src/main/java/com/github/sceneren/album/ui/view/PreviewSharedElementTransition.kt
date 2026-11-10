package com.github.sceneren.album.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.sceneren.album.api.AlbumMedia
import kotlin.math.roundToInt

/** Retains only the displayed thumbnail and screen bounds, never a recycled cell or its Activity. */
internal data class PreviewTransitionOrigin(val bounds: RectF, val drawable: Drawable) {
    companion object {
        fun capture(image: ImageView): PreviewTransitionOrigin? {
            val drawable = image.drawable ?: return null
            if (!image.isAttachedToWindow || image.width <= 0 || image.height <= 0) return null
            val position = IntArray(2)
            image.getLocationOnScreen(position)
            val bounds = Rect(position[0], position[1], position[0] + image.width, position[1] + image.height)
            val visible = Rect()
            // A partially hidden cell has no complete shared element to animate.
            if (!image.getLocalVisibleRect(visible) || visible != Rect(0, 0, image.width, image.height)) return null
            return PreviewTransitionOrigin(
                RectF(bounds),
                drawable.constantState?.newDrawable(image.resources)?.mutate() ?: return null,
            )
        }
    }
}

/** Expands the existing thumbnail from the cell's crop into a fitted preview, without copying pixels. */
internal class PreviewSharedElementTransition(
    private val root: ViewGroup,
    private val pager: ViewPager2,
    private val chrome: List<View>,
    private val origin: PreviewTransitionOrigin,
    private val media: AlbumMedia,
    private val backgroundColor: Int,
) {
    private val background = backgroundColor.toDrawable()
    private val image = ImageView(root.context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setImageDrawable(origin.drawable)
    }
    private var animator: ValueAnimator? = null
    private var finished = false

    fun prepare() {
        root.background = background
        background.alpha = 0
        pager.alpha = 0f
        pager.isUserInputEnabled = false
        chrome.forEach { it.alpha = 0f }
    }

    fun start() {
        if (finished || animator != null) return
        if (pager.width == 0 || pager.height == 0) {
            finish()
            return
        }
        val rootPosition = IntArray(2).also(root::getLocationOnScreen)
        val pagerPosition = IntArray(2).also(pager::getLocationOnScreen)
        val from = RectF(origin.bounds).apply { offset(-rootPosition[0].toFloat(), -rootPosition[1].toFloat()) }
        val viewport = RectF(
            (pagerPosition[0] - rootPosition[0]).toFloat(),
            (pagerPosition[1] - rootPosition[1]).toFloat(),
            (pagerPosition[0] - rootPosition[0] + pager.width).toFloat(),
            (pagerPosition[1] - rootPosition[1] + pager.height).toFloat(),
        )
        // A loaded preview may already have replaced the provider's square thumbnail.
        val previewImage = (pager.getChildAt(0) as? RecyclerView)
            ?.findViewHolderForAdapterPosition(pager.currentItem)?.itemView
            ?.findViewById<ImageView>(R.id.auv_preview_zoom_image)?.drawable
        val targetDrawable = previewImage?.takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }
            ?: origin.drawable
        val hasDimensions = (media.width ?: 0) > 0 && (media.height ?: 0) > 0
        val width = if (hasDimensions) media.width!! else targetDrawable.intrinsicWidth
        val height = if (hasDimensions) media.height!! else targetDrawable.intrinsicHeight
        val target = fittedPreviewBounds(viewport, width, height)
        root.overlay.add(image)
        fun render(fraction: Float) {
            fun edge(start: Float, end: Float) = (start + (end - start) * fraction).roundToInt()
            image.layout(edge(from.left, target.left), edge(from.top, target.top),
                edge(from.right, target.right), edge(from.bottom, target.bottom))
            background.alpha = (255 * fraction).roundToInt()
            chrome.forEach { it.alpha = fraction }
        }
        render(0f)
        val activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { render(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { finish() }
            })
        }
        animator = activeAnimator
        activeAnimator.start()
    }

    fun cancel() {
        animator?.cancel()
        finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        animator?.removeAllUpdateListeners()
        animator?.removeAllListeners()
        animator = null
        root.overlay.remove(image)
        image.setImageDrawable(null)
        background.alpha = 255
        pager.alpha = 1f
        pager.isUserInputEnabled = true
        chrome.forEach { it.alpha = 1f }
    }
}

internal fun fittedPreviewBounds(viewport: RectF, width: Int, height: Int): RectF {
    if (width <= 0 || height <= 0) return RectF(viewport)
    val scale = minOf(viewport.width() / width, viewport.height() / height)
    val halfWidth = width * scale / 2
    val halfHeight = height * scale / 2
    return RectF(viewport.centerX() - halfWidth, viewport.centerY() - halfHeight,
        viewport.centerX() + halfWidth, viewport.centerY() + halfHeight)
}
