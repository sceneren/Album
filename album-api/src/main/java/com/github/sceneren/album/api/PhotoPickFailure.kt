package com.github.sceneren.album.api

/** 定义验证或持久化选择器结果时对外报告的稳定失败类型。 */
enum class PhotoPickFailure {
    SELECTION_LIMIT_EXCEEDED,
    MEDIA_TYPE_NOT_ALLOWED,
    PERSISTABLE_PERMISSION_FAILED,
    METADATA_READ_FAILED,
    DATABASE_WRITE_FAILED,
}
