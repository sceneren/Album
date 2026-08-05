package com.github.sceneren.album.ui.view

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.AlbumPickerIntentCodec
import com.github.sceneren.album.api.AlbumPickerResult

/** View 相册选择器的一次启动请求。 */
data class ViewAlbumPickerRequest(
    val config: AlbumPickerConfig,
    @StyleRes val themeResId: Int = 0,
    val appearance: ViewAlbumPickerAppearance = ViewAlbumPickerAppearance(),
)

/** View 实现的颜色和图标覆盖项；未设置的值从 Theme 属性读取。 */
data class ViewAlbumPickerAppearance(
    @ColorInt val toolbarColor: Int? = null,
    @ColorInt val bottomBarColor: Int? = null,
    @ColorInt val previewBackgroundColor: Int? = null,
    @ColorInt val accentColor: Int? = null,
    @ColorInt val primaryTextColor: Int? = null,
    @ColorInt val secondaryTextColor: Int? = null,
    @ColorInt val scrimColor: Int? = null,
    @DrawableRes val backIconRes: Int? = null,
    @DrawableRes val cameraIconRes: Int? = null,
    @DrawableRes val addIconRes: Int? = null,
    @DrawableRes val checkedIconRes: Int? = null,
    @DrawableRes val uncheckedIconRes: Int? = null,
    @DrawableRes val folderIconRes: Int? = null,
    @DrawableRes val doneIconRes: Int? = null,
    @DrawableRes val videoIconRes: Int? = null,
)

/** 以全屏 View Activity 打开相册选择器。 */
class ViewAlbumPickerContract : ActivityResultContract<ViewAlbumPickerRequest, AlbumPickerResult?>() {
    override fun createIntent(context: Context, input: ViewAlbumPickerRequest): Intent =
        Intent(context, ViewAlbumPickerActivity::class.java)
            .also { intent ->
                AlbumPickerIntentCodec.putConfig(intent, input.config)
                intent.putExtra(ViewAlbumPickerExtras.THEME, input.themeResId)
                ViewAlbumPickerExtras.putAppearance(intent, input.appearance)
                intent.putExtra(
                    AlbumPickerIntentCodec.EXTRA_SESSION_ID,
                    AlbumPickerIntentCodec.newSessionId(),
                )
            }

    override fun parseResult(resultCode: Int, intent: Intent?): AlbumPickerResult? =
        if (resultCode == android.app.Activity.RESULT_OK && intent != null) {
            AlbumPickerIntentCodec.readResult(intent)
        } else {
            null
        }
}

internal object ViewAlbumPickerExtras {
    const val THEME = "album_view.theme"
    private const val PREFIX = "album_view.appearance."

    fun putAppearance(intent: Intent, appearance: ViewAlbumPickerAppearance) {
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
    }

    fun readAppearance(intent: Intent) = ViewAlbumPickerAppearance(
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
    )

    private fun Intent.intOrNull(key: String, zeroIsNull: Boolean = false): Int? {
        val value = getIntExtra(key, Int.MIN_VALUE)
        return if (value == Int.MIN_VALUE || (zeroIsNull && value == 0)) null else value
    }
}
