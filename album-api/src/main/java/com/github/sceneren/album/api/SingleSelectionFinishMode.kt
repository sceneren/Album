package com.github.sceneren.album.api

/** 单选时点击媒体后的完成策略。 */
enum class SingleSelectionFinishMode {
    /** 点击确认返回 */
    EXPLICIT_CONFIRM,
    /** 点击媒体直接返回 */
    IMMEDIATE,
}
