package com.github.sceneren.album.api.internal.file

import android.net.Uri

/** 描述由所选内容 URI 生成、归应用所有的文件。 */
internal data class MaterializedMedia(
    /** 原始媒体内容 URI。 */
    val originalUri: Uri,
    /** 复制后原始文件的绝对路径。 */
    val originalFilePath: String,
    /** 文件的绝对路径。 */
    val filePath: String,
    /** 文件大小，单位为字节。 */
    val sizeBytes: Long,
    /** Whether an existing materialized file was reused. */
    val reused: Boolean,
)

/** Metadata needed to identify and materialize one selected media item. */
internal data class MaterializationRequest(
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val generationModified: Long? = null,
    val dateModifiedEpochSeconds: Long? = null,
)
