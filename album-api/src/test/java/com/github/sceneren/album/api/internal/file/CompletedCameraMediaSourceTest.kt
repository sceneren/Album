package com.github.sceneren.album.api.internal.file

import java.io.File
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPickerClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletedCameraMediaSourceTest {
    @Test fun onlyRequestedCameraFilesAreProbedAndUnchangedVersionsAreReused() {
        val context = RuntimeEnvironment.getApplication()
        val root = File(context.cacheDir, "camera-page")
        val directory = File(root, "camera").apply { mkdirs() }
        List(200) { File(directory, "$it.jpg").apply { writeText("camera-$it") } }
        var probes = 0
        val source = CompletedCameraMediaSource(context, root) { _, _ ->
            probes++
            AlbumPickerClient.ProbedCameraMetadata(640, 480)
        }
        val initial = source.list(AlbumMediaFilter.IMAGES)
        assertEquals(200, initial.size)
        assertEquals(0, probes)
        val page = initial.take(20).map(source::readMetadata)
        assertEquals(20, probes)
        assertTrue(page.all { it.width == 640 })
        source.list(AlbumMediaFilter.IMAGES).take(20).forEach(source::readMetadata)
        assertEquals(20, probes)
        val changedFile = File(directory, initial.first().displayName!!)
        changedFile.appendText("edit")
        source.readMetadata(initial.first())
        assertEquals(21, probes)
        initial.drop(20).take(10).forEach(source::readMetadata)
        assertEquals(31, probes)
    }
}
