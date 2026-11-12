package com.github.sceneren.album.ui.view

import android.content.Context
import android.os.Looper
import android.text.SpannableString
import android.widget.Toast
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumToastHandlerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        AlbumUi.setToastHandler(null)
        ShadowToast.reset()
    }

    @After
    fun tearDown() {
        AlbumUi.setToastHandler(null)
        ShadowToast.reset()
    }

    @Test
    fun absentHandlerShowsDefaultShortToast() {
        val toast = AlbumUi.showToast(context, "请选择照片")

        assertSame(toast, ShadowToast.getLatestToast())
        assertEquals("请选择照片", ShadowToast.getTextOfLatestToast())
        assertEquals(Toast.LENGTH_SHORT, toast!!.duration)
    }

    @Test
    fun customHandlerReceivesOriginalTextWithoutDefaultToast() {
        val message = SpannableString("最多选择 9 张图片")
        var calls = 0
        AlbumUi.setToastHandler { callbackMessage ->
            assertSame(message, callbackMessage)
            assertSame(Looper.getMainLooper(), Looper.myLooper())
            calls++
        }

        assertNull(AlbumUi.showToast(context, message))
        assertEquals(1, calls)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun clearingHandlerRestoresDefaultToast() {
        var calls = 0
        AlbumUi.setToastHandler { calls++ }
        AlbumUi.showToast(context, "自定义提示")

        AlbumUi.setToastHandler(null)
        AlbumUi.showToast(context, "默认提示")

        assertEquals(1, calls)
        assertEquals(1, ShadowToast.shownToastCount())
        assertEquals("默认提示", ShadowToast.getTextOfLatestToast())
    }
}
