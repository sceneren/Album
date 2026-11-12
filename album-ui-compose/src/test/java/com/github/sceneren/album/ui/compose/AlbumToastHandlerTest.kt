package com.github.sceneren.album.ui.compose

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Looper
import android.text.SpannableString
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPickerConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumToastHandlerTest {
    @get:Rule val compose = createComposeRule()
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
    fun fullPickerSwitchesBetweenDefaultToastAndHostHandler() {
        verifyPickerToastRouting(embedded = false)
    }

    @Test
    fun embeddedPickerSwitchesBetweenDefaultToastAndHostHandler() {
        verifyPickerToastRouting(embedded = true)
    }

    private fun verifyPickerToastRouting(embedded: Boolean) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    throw ActivityNotFoundException("No camera handler")
                }
            }
        }
        val config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, 9)
        val imageLoader = AlbumImageLoader { _, _ -> ColorPainter(Color.Gray) }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme {
                    if (embedded) {
                        AlbumPickerContent(
                            config = config,
                            state = rememberAlbumPickerState(),
                            onResult = {},
                            onCancel = {},
                            imageLoader = imageLoader,
                        )
                    } else {
                        AlbumPicker(
                            config = config,
                            onResult = {},
                            onCancel = {},
                            imageLoader = imageLoader,
                            animation = AlbumPickerAnimation.None,
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("拍摄").performClick()
        compose.waitUntil(5_000) { ShadowToast.shownToastCount() == 1 }
        val defaultToast = ShadowToast.getLatestToast()
        assertEquals("没有可用相机", ShadowToast.getTextOfLatestToast())
        assertEquals(Toast.LENGTH_SHORT, defaultToast.duration)

        val messages = mutableListOf<CharSequence>()
        compose.runOnIdle {
            AlbumUi.setToastHandler { message ->
                assertSame(Looper.getMainLooper(), Looper.myLooper())
                messages += message
            }
        }
        compose.onNodeWithText("拍摄").performClick()
        compose.waitUntil(5_000) { messages.size == 1 }
        assertEquals("没有可用相机", messages.single().toString())
        assertEquals(1, ShadowToast.shownToastCount())
        assertTrue(shadowOf(defaultToast).isCancelled)

        compose.runOnIdle { AlbumUi.setToastHandler(null) }
        compose.onNodeWithText("拍摄").performClick()
        compose.waitUntil(5_000) { ShadowToast.shownToastCount() == 2 }
        assertEquals("没有可用相机", ShadowToast.getTextOfLatestToast())
        assertEquals(1, messages.size)
    }
}
