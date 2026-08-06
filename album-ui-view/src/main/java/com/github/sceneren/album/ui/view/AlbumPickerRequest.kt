package com.github.sceneren.album.ui.view

import androidx.annotation.StyleRes
import com.github.sceneren.album.api.AlbumPickerConfig

/** View 相册选择器的一次启动请求。 */
data class AlbumPickerRequest(
    val config: AlbumPickerConfig,
    @StyleRes val themeResId: Int = 0,
    val appearance: AlbumPickerAppearance = AlbumPickerAppearance(),
)
