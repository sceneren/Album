package com.github.sceneren.album.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AlbumMediaChangeTrackerTest {
    @Test fun failedCheckPreservesBaselineAndNextSuccessfulCheckStillDetectsChange() = runTest {
        var token = "before"
        var failed = false
        val tracker = AlbumMediaChangeTracker {
            check(!failed)
            AlbumMediaChangeTracker.Snapshot(token)
        }
        assertFalse(tracker.hasChanges().getOrThrow())
        failed = true
        token = "after"
        assertTrue(tracker.hasChanges().isFailure)
        failed = false
        assertTrue(tracker.hasChanges().getOrThrow())
        assertFalse(tracker.hasChanges().getOrThrow())
    }

    @Test fun cancellationIsNotConvertedToASuccessfulCheck() = runTest {
        val tracker = AlbumMediaChangeTracker { throw CancellationException("screen closed") }
        var cancelled = false
        try { tracker.hasChanges() } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }
}
