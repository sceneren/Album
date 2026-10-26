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

/**
 * 识别并复制一项所选媒体所需的元数据。
 * @property uri 原始内容地址。
 * @property displayName 原始显示名称。
 * @property mimeType 原始 MIME。
 * @property sizeBytes 提供方声明的字节数。
 * @property generationModified MediaStore 世代版本。
 * @property dateModifiedEpochSeconds 最后修改秒数。
 * @property maxBytes 复制前及复制期间执行的字节上限；null 表示不限制。
 */
internal data class MaterializationRequest(
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val generationModified: Long? = null,
    val dateModifiedEpochSeconds: Long? = null,
    val maxBytes: Long? = null,
)
