package dev.osify.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against an actual installed APK on a connected emulator/device. */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun firstLaunchAndPrimaryNavigation() {
        compose.waitUntil(15000) {compose.onAllNodesWithTag("onboarding-skip").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("nav-home").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithTag("onboarding-skip").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("onboarding-skip").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("nav-home").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("memory-card").assertExists()
        compose.onNodeWithTag("nav-catalog").performClick()
        compose.onNodeWithTag("catalog-search").assertExists()
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithTag("import-image").assertExists()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("settings-title").assertExists()
    }
}
