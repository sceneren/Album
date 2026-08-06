package com.github.sceneren.album.ui.view

import android.content.Intent

internal object AlbumPickerExtras {
    const val THEME = "album_view.theme"
    private const val PREFIX = "album_view.appearance."

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
        backIconRes = intent.intOrNull(PREFIX + "back", zeroIsNull = true),
        cameraIconRes = intent.intOrNull(PREFIX + "camera", zeroIsNull = true),
        addIconRes = intent.intOrNull(PREFIX + "add", zeroIsNull = true),
        checkedIconRes = intent.intOrNull(PREFIX + "checked", zeroIsNull = true),
        uncheckedIconRes = intent.intOrNull(PREFIX + "unchecked", zeroIsNull = true),
        folderIconRes = intent.intOrNull(PREFIX + "folder", zeroIsNull = true),
        doneIconRes = intent.intOrNull(PREFIX + "done", zeroIsNull = true),
        videoIconRes = intent.intOrNull(PREFIX + "video", zeroIsNull = true),
        gridItemSpacingDp = intent.getIntExtra(PREFIX + "grid_item_spacing_dp", 1),
        gridSpanCount = intent.getIntExtra(PREFIX + "grid_span_count", 4),
    )
}
