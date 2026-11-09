package com.github.sceneren.photopicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.platform.app.InstrumentationRegistry
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumPickerConfig
import com.github.sceneren.album.ui.compose.AlbumPicker
import com.github.sceneren.album.ui.compose.AlbumPickerAnimation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real touch regression: cancelling a picker must release the host screen. */
class PickerDismissalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backAfterOpeningReleasesHostTouchInput() = verifyDismissal(AlbumPickerAnimation.None)

    @Test fun animatedBackReleasesHostTouchInput() = verifyDismissal(AlbumPickerAnimation.Default)

    private fun verifyDismissal(animation: AlbumPickerAnimation) {
        val showing = mutableStateOf(true)
        var cancellations = 0
        var hostClicks = 0
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Button(onClick = { hostClicks++ }) { Text("Host action") }
                if (showing.value) {
                    AlbumPicker(
                        config = AlbumPickerConfig(AlbumMediaFilter.IMAGES, 100),
                        onResult = {},
                        onCancel = { cancellations++; showing.value = false },
                        animation = animation,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
        )
        compose.waitUntil(5_000) { !showing.value }
        compose.onNodeWithText("Host action").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, cancellations)
            assertEquals(1, hostClicks)
        }
    }
}
