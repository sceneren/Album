package com.github.sceneren.album.api.internal.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-wide session transactions, shared by every client using the same preferences. */
internal object AlbumSessionLocks {
    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val entries = mutableMapOf<String, Entry>()

    private fun retain(key: String): Entry = synchronized(entries) {
        entries.getOrPut(key) { Entry() }.also { it.users++ }
    }

    private fun release(key: String, entry: Entry) = synchronized(entries) {
        if (--entry.users == 0) entries.remove(key)
    }

    suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        val entry = retain(key)
        return try {
            entry.mutex.withLock { block() }
        } finally {
            release(key, entry)
        }
    }

    /** Cleanup must never wait for, or remove, a session with an in-flight transaction. */
    fun ifIdle(key: String, block: () -> Unit) {
        val entry = retain(key)
        try {
            if (entry.mutex.tryLock()) {
                try { block() } finally { entry.mutex.unlock() }
            }
        } finally {
            release(key, entry)
        }
    }
}
