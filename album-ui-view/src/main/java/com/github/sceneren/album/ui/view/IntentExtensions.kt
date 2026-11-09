package com.github.sceneren.album.ui.view

import android.content.Intent

/** 执行 `intOrNull` 方法定义的处理。 */
internal fun Intent.intOrNull(key: String, zeroIsNull: Boolean = false): Int? {
    if (!hasExtra(key)) return null
    val value = getIntExtra(key, 0)
    return if (zeroIsNull && value == 0) null else value
}
