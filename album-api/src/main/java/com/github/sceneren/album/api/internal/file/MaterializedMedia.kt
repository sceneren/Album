package com.github.sceneren.album.api.internal.file

import android.net.Uri

/** App-owned file produced from a selected content URI. */
internal data class MaterializedMedia(
    val originalUri: Uri,
    val originalFilePath: String,
    val filePath: String,
    val sizeBytes: Long,
)
