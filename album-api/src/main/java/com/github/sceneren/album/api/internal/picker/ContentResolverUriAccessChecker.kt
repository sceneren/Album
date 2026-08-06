package com.github.sceneren.album.api.internal.picker

import android.content.ContentResolver
import android.net.Uri

internal class ContentResolverUriAccessChecker(
    private val resolver: ContentResolver,
) : UriAccessChecker {
    override fun canRead(uri: Uri): Boolean = runCatching {
        resolver.openAssetFileDescriptor(uri, READ_MODE)?.use { true } ?: false
    }.getOrDefault(false)

    private companion object {
        const val READ_MODE = "r"
    }
}
