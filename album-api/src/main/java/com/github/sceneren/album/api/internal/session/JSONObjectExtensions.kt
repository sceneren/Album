package com.github.sceneren.album.api.internal.session

import org.json.JSONObject

/** 执行 `putNullable` 方法定义的处理。 */
internal fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

/** 执行 `optNullableLong` 方法定义的处理。 */
internal fun JSONObject.optNullableLong(key: String): Long? =
    if (isNull(key)) null else optLong(key)

/** 执行 `optNullableInt` 方法定义的处理。 */
internal fun JSONObject.optNullableInt(key: String): Int? =
    if (isNull(key)) null else optInt(key)

/** 执行 `optNullableString` 方法定义的处理。 */
internal fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else getString(key)
