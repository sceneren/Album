package com.github.sceneren.album.ui.view

import android.content.Intent

internal fun Intent.intOrNull(key: String, zeroIsNull: Boolean = false): Int? {
    val value = getIntExtra(key, Int.MIN_VALUE)
    return if (value == Int.MIN_VALUE || (zeroIsNull && value == 0)) null else value
}
