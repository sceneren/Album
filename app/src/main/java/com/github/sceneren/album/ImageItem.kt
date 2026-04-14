package com.github.sceneren.album

import android.net.Uri

/**
 * 图片信息数据类
 *
 * 封装从 MediaStore 查询到的单张图片的完整元数据信息。
 * uri 是基于 content:// 协议的资源定位符，可直接用于 Glide / Coil 等图片加载框架，
 * 无需额外的文件路径转换，在 Android 10+ 的分区存储下也能正常工作。
 *
 * @property id           MediaStore 中的唯一标识
 * @property uri          图片的 content:// URI，兼容所有 Android 版本
 * @property displayName  图片的显示名称（含扩展名，如 photo.jpg）
 * @property size         图片文件大小，单位为字节
 * @property dateAdded    图片添加到设备的时间戳（秒级）
 * @property dateModified 图片最后修改的时间戳（秒级）
 * @property mimeType     图片的 MIME 类型，例如 image/jpeg、image/png
 * @property width        图片的宽度（像素）
 * @property height       图片的高度（像素）
 * @property bucketId     图片所属目录的唯一标识
 * @property bucketName   图片所属目录的显示名称
 */
data class ImageItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucketName: String
)