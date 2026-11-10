package com.github.sceneren.album.api.internal.mediastore

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.internal.picker.PersistableGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidMediaChangeObserverTest {
    @Test
    fun batchesNotificationsAndUnregistersWhenScreenStops() = runTest {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val grants = object : PersistableGrantManager {
            override fun persistedReadUris() = setOf(Uri.parse("content://documents/photo/1"))
            override fun takeRead(uri: Uri) = Unit
            override fun releaseRead(uri: Uri) = Unit
        }
        var changes = 0
        val job = launch { AndroidMediaChangeObserver(resolver, grants).changes().collect { changes++ } }
        runCurrent()
        val mediaRoot = Uri.parse("content://media")
        val documentRoot = Uri.parse("content://documents")
        val shadow = org.robolectric.Shadows.shadowOf(resolver)
        assertEquals(1, shadow.getContentObservers(mediaRoot).size)
        assertEquals(1, shadow.getContentObservers(documentRoot).size)

        repeat(10) { shadow.getContentObservers(mediaRoot).single().onChange(false) }
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertEquals(0, changes)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, changes)

        job.cancel()
        runCurrent()
        assertTrue(shadow.getContentObservers(mediaRoot).isEmpty())
        assertTrue(shadow.getContentObservers(documentRoot).isEmpty())
    }
}
