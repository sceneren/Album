package com.github.sceneren.album.api.internal.session

import android.content.Context
import android.net.Uri
import com.github.sceneren.album.api.AlbumCameraCaptureType
import com.github.sceneren.album.api.AlbumCameraConfig
import com.github.sceneren.album.api.AlbumCompressionConfig
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.api.SingleSelectionFinishMode
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** 负责 `AlbumPickerSessionStore` 管理数据的持久化读写。 */
internal class AlbumPickerSessionStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /** 创建或准备 `create` 对应的对象。 */
    fun create(
        config: AlbumPickerConfig,
        sessionId: String = UUID.randomUUID().toString(),
    ): AlbumPickerSessionState = AlbumPickerSessionState(sessionId, config).also(::save)

    /** 执行 `save` 方法定义的处理。 */
    fun save(state: AlbumPickerSessionState) {
        preferences.edit()
            .putString(state.sessionId, encode(state))
            .commit()
    }

    /** 获取 `load` 所需的数据。 */
    fun load(sessionId: String): AlbumPickerSessionState? =
        preferences.getString(sessionId, null)?.let { decode(sessionId, it) }

    /** 清理 `remove` 对应的数据或资源。 */
    fun remove(sessionId: String) {
        preferences.edit().remove(sessionId).commit()
    }

    /** 执行 `encode` 方法定义的处理。 */
    private fun encode(state: AlbumPickerSessionState): String = JSONObject().apply {
        put("config", encodeConfig(state.config))
        put("selected", encodeItems(state.selected))
        put("cameraItems", encodeItems(state.cameraItems))
        state.pendingCamera?.let { put("pendingCamera", encodePending(it)) }
            ?: put("pendingCamera", JSONObject.NULL)
        put("bucketId", state.bucketId)
        state.previewUri?.let { put("previewUri", it.toString()) }
            ?: put("previewUri", JSONObject.NULL)
    }.toString()

    /** 执行 `decode` 方法定义的处理。 */
    private fun decode(sessionId: String, value: String): AlbumPickerSessionState {
        val json = JSONObject(value)
        val configJson = json.getJSONObject("config")
        return AlbumPickerSessionState(
            sessionId = sessionId,
            config = decodeConfig(configJson),
            selected = decodeItems(json.getJSONArray("selected")),
            cameraItems = decodeItems(json.getJSONArray("cameraItems")),
            pendingCamera = json.optJSONObject("pendingCamera")?.let(::decodePending),
            bucketId = json.optLong("bucketId", Long.MIN_VALUE),
            previewUri = json.optNullableString("previewUri")?.let(Uri::parse),
        )
    }

    /** 执行 `encodeConfig` 方法定义的处理。 */
    private fun encodeConfig(config: AlbumPickerConfig) = JSONObject().apply {
        put("filter", config.mediaFilter.name)
        put("max", config.maxSelectionCount)
        put("singleFinish", config.singleSelectionFinishMode.name)
        put("cameraEnabled", config.camera.enabled)
        put("mixedCapture", config.camera.mixedMediaCaptureType.name)
        put("compressionEnabled", config.compression.enabled)
        put("skipKb", config.compression.skipAtOrBelowKb)
        put("showPermissionUpgrade", config.showPermissionUpgrade)
    }

    /** 执行 `decodeConfig` 方法定义的处理。 */
    private fun decodeConfig(json: JSONObject) = AlbumPickerConfig(
        mediaFilter = AlbumMediaFilter.valueOf(json.getString("filter")),
        maxSelectionCount = json.getInt("max"),
        singleSelectionFinishMode = SingleSelectionFinishMode.valueOf(json.getString("singleFinish")),
        camera = AlbumCameraConfig(
            enabled = json.getBoolean("cameraEnabled"),
            mixedMediaCaptureType = AlbumCameraCaptureType.valueOf(json.getString("mixedCapture")),
        ),
        compression = AlbumCompressionConfig(
            enabled = json.getBoolean("compressionEnabled"),
            skipAtOrBelowKb = json.getLong("skipKb"),
        ),
        showPermissionUpgrade = json.getBoolean("showPermissionUpgrade"),
    )

    /** 执行 `encodeItems` 方法定义的处理。 */
    private fun encodeItems(items: List<AlbumPickerSelection>) = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("uri", item.uri.toString())
                put("type", item.mediaType.name)
                putNullable("displayName", item.displayName)
                putNullable("mimeType", item.mimeType)
                putNullable("size", item.sizeBytes)
                putNullable("width", item.width)
                putNullable("height", item.height)
                putNullable("duration", item.durationMillis)
                put("source", item.source.name)
                putNullable("filePath", item.filePath)
            })
        }
    }

    /** 执行 `decodeItems` 方法定义的处理。 */
    private fun decodeItems(array: JSONArray): List<AlbumPickerSelection> = buildList {
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            add(
                AlbumPickerSelection(
                    uri = Uri.parse(item.getString("uri")),
                    mediaType = AlbumMediaType.valueOf(item.getString("type")),
                    displayName = item.optNullableString("displayName"),
                    mimeType = item.optNullableString("mimeType"),
                    sizeBytes = item.optNullableLong("size"),
                    width = item.optNullableInt("width"),
                    height = item.optNullableInt("height"),
                    durationMillis = item.optNullableLong("duration"),
                    source = AlbumPickerItemSource.valueOf(item.getString("source")),
                    filePath = item.optNullableString("filePath"),
                ),
            )
        }
    }

    /** 执行 `encodePending` 方法定义的处理。 */
    private fun encodePending(pending: AlbumPendingCameraCapture) = JSONObject().apply {
        put("uri", pending.uri.toString())
        put("filePath", pending.filePath)
        put("type", pending.mediaType.name)
    }

    /** 执行 `decodePending` 方法定义的处理。 */
    private fun decodePending(json: JSONObject) = AlbumPendingCameraCapture(
        uri = Uri.parse(json.getString("uri")),
        filePath = json.getString("filePath"),
        mediaType = AlbumMediaType.valueOf(json.getString("type")),
    )

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `PREFERENCES_NAME` 对应的数据。 */
        const val PREFERENCES_NAME = "album_picker_sessions"
    }
}
