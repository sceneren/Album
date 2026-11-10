package com.github.sceneren.album.api.internal.mediastore

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import com.github.sceneren.album.api.internal.picker.PersistableGrantManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce

/** 一个界面只注册一次媒体监听；合并批量增删通知，取消收集时释放系统观察者。 */
internal class AndroidMediaChangeObserver(
    private val resolver: ContentResolver,
    private val grantManager: PersistableGrantManager,
) {
    @OptIn(FlowPreview::class)
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        val authorities = buildSet {
            add(MediaStore.AUTHORITY)
            grantManager.persistedReadUris().forEach { uri -> uri.authority?.let(::add) }
        }
        try {
            authorities.forEach { authority ->
                try {
                    resolver.registerContentObserver(Uri.parse("content://$authority"), true, observer)
                } catch (_: SecurityException) {
                    // 部分提供方不允许监听根 URI；前台恢复仍会校验当前页。
                }
            }
            awaitClose()
        } finally {
            resolver.unregisterContentObserver(observer)
        }
    }.conflate().debounce(300L)
}
