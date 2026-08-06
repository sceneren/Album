package com.github.sceneren.album.ui.view

import android.content.Intent

/** 执行 `intOrNull` 方法定义的处理。 */
internal fun Intent.intOrNull(key: String, zeroIsNull: Boolean = false): Int? {
    val value = getIntExtra(key, Int.MIN_VALUE)
    return if (value == Int.MIN_VALUE || (zeroIsNull && value == 0)) null else value
}
