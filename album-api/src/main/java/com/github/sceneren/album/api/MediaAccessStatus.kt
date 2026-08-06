package com.github.sceneren.album.api

/** 表示宿主针对指定过滤条件实际拥有的设备媒体库访问状态。 */
enum class MediaAccessStatus {
    FULL,
    PARTIAL,
    DENIED,
}
