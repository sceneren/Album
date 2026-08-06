package com.github.sceneren.album.api.internal.session

import org.json.JSONObject

internal fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

internal fun JSONObject.optNullableLong(key: String): Long? =
    if (isNull(key)) null else optLong(key)

internal fun JSONObject.optNullableInt(key: String): Int? =
    if (isNull(key)) null else optInt(key)

internal fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else getString(key)
