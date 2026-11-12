package com.github.sceneren.album.api.internal.mediastore

import android.content.Context
import android.database.ContentObserver
import androidx.core.net.toUri
import android.provider.MediaStore
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/** One application-lifetime observer, rather than one leaked observer per picker/client. */
internal class MediaStoreChangeMonitor private constructor(context: Context) {
    val revision = AtomicLong()
    val observing: Boolean = try {
        context.contentResolver.registerContentObserver(
            "content://${MediaStore.AUTHORITY}".toUri(), true,
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) { revision.incrementAndGet() }
            },
        )
        true
    } catch (_: RuntimeException) {
        false
    }

    companion object {
        private val monitors = WeakHashMap<Context, MediaStoreChangeMonitor>()

        /** Called from the data source's IO dispatcher, never from UI construction. */
        fun get(context: Context): MediaStoreChangeMonitor = synchronized(monitors) {
            monitors.getOrPut(context.applicationContext) { MediaStoreChangeMonitor(context.applicationContext) }
        }
    }
}
