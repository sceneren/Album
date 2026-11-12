package com.github.sceneren.album.ui.compose

import androidx.annotation.MainThread

/** 宿主自定义提示的显示方式和样式，通过 [AlbumUi.setToastHandler] 注册。 */
fun interface AlbumToastHandler {
    /** 在主线程显示完整的 [message]，库不会同时显示默认 Toast。 */
    @MainThread
    fun showToast(message: CharSequence)
}
