package com.github.sceneren.photopicker

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/** Exercise the actual host loader, not a separate ImageLoader with a fixed test size. */
class PickerBackgroundTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun thumbnailsRemainVisibleAfterBackgroundAndBackStillWorks() {
        compose.onNodeWithText("打开 Compose 相册").performClick()
        compose.waitUntil(15_000) { gridHasImageContent() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(15_000) { gridHasImageContent() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
        )
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("打开 Compose 相册"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Touch input (rather than semantic performClick) catches invisible input blockers.
        compose.onNodeWithText("打开 Compose 相册").performTouchInput { click() }
        compose.waitUntil(15_000) { gridHasImageContent() }
    }

    @Test fun rapidScrollAwayAndBackKeepsThumbnailsVisible() {
        compose.onNodeWithText("打开 Compose 相册").performClick()
        compose.waitUntil(15_000) { gridHasImageContent() }
        repeat(3) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "input swipe 540 1800 540 500 150",
            ).close()
        }
        compose.waitForIdle()
        repeat(3) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "input swipe 540 500 540 1800 150",
            ).close()
        }
        compose.waitUntil(5_000) { gridHasImageContent() }
    }

    private fun gridHasImageContent(): Boolean = runCatching {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val colors = HashSet<androidx.compose.ui.graphics.Color>()
        // Sample the middle grid, excluding title/footer text and the two action tiles.
        for (y in pixels.height / 3 until pixels.height * 2 / 3 step 12) {
            for (x in 0 until pixels.width step 12) colors += pixels[x, y]
        }
        colors.size > 100
    }.getOrDefault(false)
}
