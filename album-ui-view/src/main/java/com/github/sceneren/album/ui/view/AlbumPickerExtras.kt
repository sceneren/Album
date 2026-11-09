package com.github.sceneren.album.ui.view

import android.content.Intent

/** 负责 `AlbumPickerExtras` 相关的数据与行为。 */
internal object AlbumPickerExtras {
    /** 表示 `THEME` 对应的数据。 */
    const val THEME = "album_view.theme"
    /** 表示 `PREFIX` 对应的数据。 */
    private const val PREFIX = "album_view.appearance."
    /** 表示 `ANIMATION_PREFIX` 对应的数据。 */
    private const val ANIMATION_PREFIX = "album_view.animation."

    /** 执行 `putAppearance` 方法定义的处理。 */
    fun putAppearance(intent: Intent, appearance: AlbumPickerAppearance) {
        intent.putOptionalInt(PREFIX + "toolbar", appearance.toolbarColor)
        intent.putOptionalInt(PREFIX + "bottom", appearance.bottomBarColor)
        intent.putOptionalInt(PREFIX + "preview", appearance.previewBackgroundColor)
        intent.putOptionalInt(PREFIX + "accent", appearance.accentColor)
        intent.putOptionalInt(PREFIX + "primary", appearance.primaryTextColor)
        intent.putOptionalInt(PREFIX + "secondary", appearance.secondaryTextColor)
        intent.putOptionalInt(PREFIX + "scrim", appearance.scrimColor)
        intent.putOptionalInt(PREFIX + "back", appearance.backIconRes)
        intent.putOptionalInt(PREFIX + "camera", appearance.cameraIconRes)
        intent.putOptionalInt(PREFIX + "add", appearance.addIconRes)
        intent.putOptionalInt(PREFIX + "checked", appearance.checkedIconRes)
        intent.putOptionalInt(PREFIX + "unchecked", appearance.uncheckedIconRes)
        intent.putOptionalInt(PREFIX + "folder", appearance.folderIconRes)
        intent.putOptionalInt(PREFIX + "done", appearance.doneIconRes)
        intent.putOptionalInt(PREFIX + "video", appearance.videoIconRes)
        intent.putExtra(PREFIX + "grid_item_spacing_dp", appearance.gridItemSpacingDp)
        intent.putExtra(PREFIX + "grid_span_count", appearance.gridSpanCount)
    }

    private fun Intent.putOptionalInt(key: String, value: Int?) {
        if (value != null && value != 0) {
            putExtra(key, value)
        } else if (value == 0 && !key.endsWith("back") && !key.endsWith("camera") &&
            !key.endsWith("add") && !key.endsWith("checked") && !key.endsWith("unchecked") &&
            !key.endsWith("folder") && !key.endsWith("done") && !key.endsWith("video")
        ) {
            putExtra(key, value)
        } else {
            removeExtra(key)
        }
    }

    /** 获取 `readAppearance` 所需的数据。 */
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

    /** 执行 `putAnimation` 方法定义的处理。 */
    fun putAnimation(intent: Intent, animation: AlbumPickerAnimation?) {
        intent.putExtra(ANIMATION_PREFIX + "enabled", animation != null)
        if (animation == null) return
        intent.putExtra(ANIMATION_PREFIX + "open_enter", animation.openEnterResId)
        intent.putExtra(ANIMATION_PREFIX + "open_exit", animation.openExitResId)
        intent.putExtra(ANIMATION_PREFIX + "close_enter", animation.closeEnterResId)
        intent.putExtra(ANIMATION_PREFIX + "close_exit", animation.closeExitResId)
    }

    /** 获取 `readAnimation` 所需的数据。 */
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
