package com.github.sceneren.album.api.internal.picker

import android.net.Uri

internal fun interface UriAccessChecker {
    fun canRead(uri: Uri): Boolean
}
