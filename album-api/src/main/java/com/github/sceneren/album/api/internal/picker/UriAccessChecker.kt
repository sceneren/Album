package com.github.sceneren.album.api.internal.picker

import android.net.Uri

/** 定义 `UriAccessChecker` 的能力边界。 */
internal fun interface UriAccessChecker {
    /** 判断 `canRead` 条件是否成立。 */
    fun canRead(uri: Uri): Boolean
}
