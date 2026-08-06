package com.github.sceneren.album.ui.compose

import android.content.Intent

internal object AlbumPickerExtras {
    const val THEME = "album_compose.theme"
    private const val PREFIX = "album_compose.appearance."
    private const val ANIMATION_PREFIX = "album_compose.animation."

    fun putAppearance(intent: Intent, appearance: AlbumPickerAppearance) {
        intent.putExtra(PREFIX + "toolbar", appearance.toolbarColor ?: Int.MIN_VALUE)
        intent.putExtra(PREFIX + "bottom", appearance.bottomBarColor ?: Int.MIN_VALUE)
        intent.putExtra(PREFIX + "preview", appearance.previewBackgroundColor ?: Int.MIN_VALUE)
        intent.putExtra(PREFIX + "accent", appearance.accentColor ?: Int.MIN_VALUE)
        intent.putExtra(PREFIX + "primary", appearance.primaryTextColor ?: Int.MIN_VALUE)
        intent.putExtra(PREFIX + "secondary", appearance.secondaryTextColor ?: Int.MIN_VALUE)
        intent.putExtra(PREFIX + "scrim", appearance.scrimColor ?: Int.MIN_VALUE)
        intent.putExtra(PREFIX + "back", appearance.backIconRes ?: 0)
        intent.putExtra(PREFIX + "camera", appearance.cameraIconRes ?: 0)
        intent.putExtra(PREFIX + "add", appearance.addIconRes ?: 0)
        intent.putExtra(PREFIX + "checked", appearance.checkedIconRes ?: 0)
        intent.putExtra(PREFIX + "unchecked", appearance.uncheckedIconRes ?: 0)
        intent.putExtra(PREFIX + "folder", appearance.folderIconRes ?: 0)
        intent.putExtra(PREFIX + "done", appearance.doneIconRes ?: 0)
        intent.putExtra(PREFIX + "video", appearance.videoIconRes ?: 0)
        intent.putExtra(PREFIX + "grid_item_spacing_dp", appearance.gridItemSpacingDp)
        intent.putExtra(PREFIX + "grid_span_count", appearance.gridSpanCount)
    }

    fun readAppearance(intent: Intent) = AlbumPickerAppearance(
        toolbarColor = intent.intOrNull(PREFIX + "toolbar"),
        bottomBarColor = intent.intOrNull(PREFIX + "bottom"),
        previewBackgroundColor = intent.intOrNull(PREFIX + "preview"),
        accentColor = intent.intOrNull(PREFIX + "accent"),
        primaryTextColor = intent.intOrNull(PREFIX + "primary"),
        secondaryTextColor = intent.intOrNull(PREFIX + "secondary"),
        scrimColor = intent.intOrNull(PREFIX + "scrim"),
        backIconRes = intent.intOrNull(PREFIX + "back", true),
        cameraIconRes = intent.intOrNull(PREFIX + "camera", true),
        addIconRes = intent.intOrNull(PREFIX + "add", true),
        checkedIconRes = intent.intOrNull(PREFIX + "checked", true),
        uncheckedIconRes = intent.intOrNull(PREFIX + "unchecked", true),
        folderIconRes = intent.intOrNull(PREFIX + "folder", true),
        doneIconRes = intent.intOrNull(PREFIX + "done", true),
        videoIconRes = intent.intOrNull(PREFIX + "video", true),
        gridItemSpacingDp = intent.getIntExtra(PREFIX + "grid_item_spacing_dp", 1),
        gridSpanCount = intent.getIntExtra(PREFIX + "grid_span_count", 4),
    )

    fun putAnimation(intent: Intent, animation: AlbumPickerAnimation?) {
        intent.putExtra(ANIMATION_PREFIX + "enabled", animation != null)
        if (animation == null) return
        intent.putExtra(ANIMATION_PREFIX + "open_enter", animation.openEnterResId)
        intent.putExtra(ANIMATION_PREFIX + "open_exit", animation.openExitResId)
        intent.putExtra(ANIMATION_PREFIX + "close_enter", animation.closeEnterResId)
        intent.putExtra(ANIMATION_PREFIX + "close_exit", animation.closeExitResId)
    }

    fun readAnimation(intent: Intent): AlbumPickerAnimation? {
        if (!intent.getBooleanExtra(ANIMATION_PREFIX + "enabled", true)) return null
        val defaults = AlbumPickerAnimation()
        return AlbumPickerAnimation(
            openEnterResId = intent.getIntExtra(
                ANIMATION_PREFIX + "open_enter",
                defaults.openEnterResId,
            ),
            openExitResId = intent.getIntExtra(
                ANIMATION_PREFIX + "open_exit",
                defaults.openExitResId,
            ),
            closeEnterResId = intent.getIntExtra(
                ANIMATION_PREFIX + "close_enter",
                defaults.closeEnterResId,
            ),
            closeExitResId = intent.getIntExtra(
                ANIMATION_PREFIX + "close_exit",
                defaults.closeExitResId,
            ),
        )
    }
}
