package com.github.sceneren.album.ui.compose

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.github.panpf.zoomimage.ZoomImage
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaType

internal const val PREVIEW_TRANSITION_MILLIS = 300
private val previewBoundsTransform = BoundsTransform { _, _ -> tween(PREVIEW_TRANSITION_MILLIS) }

/** A single overlay contains both the retained grid and the preview, including embedded pickers. */
@Composable
internal fun AlbumPreviewLayout(
    modifier: Modifier,
    content: @Composable BoxWithConstraintsScope.(SharedTransitionScope) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val constraintsScope = this
        SharedTransitionLayout(Modifier.fillMaxSize()) { content(constraintsScope, this) }
    }
}

@Composable
internal fun Modifier.sharedAlbumThumbnail(
    media: AlbumMedia,
    scope: SharedTransitionScope?,
    visible: Boolean,
): Modifier = if (scope != null && media.mediaType == AlbumMediaType.IMAGE) {
    with(scope) {
        sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(media.uri),
            visible = visible,
            boundsTransform = previewBoundsTransform,
        )
    }
} else this

/** Animate a cropped thumbnail into the fitted image bounds, keeping the zoom viewport full size. */
@Composable
internal fun AlbumPreviewImage(
    media: AlbumMedia,
    painter: Painter,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ZoomImage(
            painter = painter,
            contentDescription = media.displayName,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = if (sharedTransitionScope?.isTransitionActive == true) 0f else 1f
            },
            contentScale = ContentScale.Fit,
            scrollBar = null,
        )
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            val intrinsic = painter.intrinsicSize
            val ratio = when {
                intrinsic.width > 0 && intrinsic.height > 0 && intrinsic.width != intrinsic.height ->
                    intrinsic.width / intrinsic.height
                (media.width ?: 0) > 0 && (media.height ?: 0) > 0 ->
                    media.width!!.toFloat() / media.height!!
                else -> 1f
            }
            val imageWidth = minOf(maxWidth, maxHeight * ratio)
            val imageHeight = imageWidth / ratio
            with(sharedTransitionScope) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(imageWidth, imageHeight)
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(media.uri),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = previewBoundsTransform,
                        )
                        .graphicsLayer { alpha = if (isTransitionActive) 1f else 0f },
                )
            }
        }
    }
}
