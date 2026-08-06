package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.net.Uri

/** 负责 `ContentResolverUriAccessChecker` 相关的数据与行为。 */
internal class ContentResolverUriAccessChecker(
    private val resolver: ContentResolver,
) : UriAccessChecker {
    /** 判断 `canRead` 条件是否成立。 */
    override fun canRead(uri: Uri): Boolean = runCatching {
        resolver.openAssetFileDescriptor(uri, READ_MODE)?.use { true } ?: false
    }.getOrDefault(false)

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `READ_MODE` 对应的数据。 */
        const val READ_MODE = "r"
    }
}
