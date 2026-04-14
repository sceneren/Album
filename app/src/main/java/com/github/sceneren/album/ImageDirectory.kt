package com.github.sceneren.album

import android.net.Uri

/**
 * 图片目录数据类
 *
 * 表示设备中一个图片目录（相册）的摘要信息，
 * 包括目录名称、封面图片以及该目录下图片的总数。
 *
 * 当 bucketId 等于 ALL_BUCKET_ID 时，表示"全部图片"虚拟目录，
 * 此时传入 getImagesByDirectory 会查询设备上的所有图片。
 *
 * @property bucketId   目录的唯一标识，ALL_BUCKET_ID 代表全部图片
 * @property bucketName 目录的显示名称（如 Camera、Screenshots）
 * @property coverUri   该目录中最新一张图片的 content:// URI，可作为封面展示
 * @property imageCount 该目录下的图片总数
 */
data class ImageDirectory(
    val bucketId: Long,
    val bucketName: String,
    val coverUri: Uri,
    val imageCount: Int
) {
    companion object {
        /**
         * "全部图片"虚拟目录的哨兵 bucketId
         *
         * MediaStore 的 BUCKET_ID 是由目录路径哈希而来的正数，
         * 因此使用 Long.MIN_VALUE 作为哨兵值，不会与真实目录冲突。
         */
        const val ALL_BUCKET_ID = Long.MIN_VALUE
        const val ALL_BUCKET_NAME = "全部图片"
    }
}