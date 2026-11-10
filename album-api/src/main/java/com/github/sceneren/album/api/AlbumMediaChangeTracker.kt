package com.github.sceneren.album.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 某个媒体过滤条件的变化基线。首次检查建立基线，后续仅在数据实际变化时返回 true。
 * 一个列表持有一个实例；检查失败保留旧基线，调用方应保留当前列表。
 */
class AlbumMediaChangeTracker internal constructor(
    private val readSnapshot: suspend () -> Snapshot,
) {
    private val mutex = Mutex()
    private var previous: Any? = null

    /** 在 IO 上比较元数据；仅在元数据不可用时探测 URI 可读性，不读取或解码媒体内容，也不清空分页。 */
    suspend fun hasChanges(): Result<Boolean> = try {
        mutex.withLock {
            val current = readSnapshot()
            val changed = previous != null && (previous != current.signature || current.cachedMediaChanged)
            previous = current.signature
            Result.success(changed)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    internal data class Snapshot(val signature: Any, val cachedMediaChanged: Boolean = false)
}
