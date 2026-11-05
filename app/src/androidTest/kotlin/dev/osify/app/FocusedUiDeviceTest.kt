package dev.osify.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Focused checks only: no downloads or guest boots. */
@RunWith(AndroidJUnit4::class)
class FocusedUiDeviceTest {
    companion object {
        @org.junit.BeforeClass @JvmStatic fun setup() {
            val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OsifyApplication
            runBlocking {
                app.preferences.boolean("onboarded",true)
                app.preferences.string("language","ru");app.preferences.string("motion","off")
                app.preferences.string("theme","dark");app.preferences.string("catalog_sort","curated")
            }
        }
    }
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) {compose.waitUntil(12000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun shot(name: String) {
        compose.waitForIdle();android.os.SystemClock.sleep(400)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.filesDir,"visuals/0410-$name.png").apply {parentFile!!.mkdirs()}.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun sortingPersistsAndQemuStripIsCompact() {
        val app=compose.activity.application as OsifyApplication
        waitTag("nav-catalog");compose.onNodeWithTag("nav-catalog").performClick();waitTag("catalog-sort")
        shot("catalog")
        compose.onNodeWithTag("filter-windows").performClick()
        compose.onNodeWithTag("catalog-sort").performClick();waitTag("sort-name_asc");shot("sort")
        compose.onNodeWithTag("sort-name_asc").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("catalog-sort-sheet").fetchSemanticsNodes().isEmpty()}
        assertEquals("name_asc",runBlocking {app.preferences.flow.first().catalogSort})
        compose.waitForIdle()
        val tags=listOf("catalog-windows-tiny10-source","catalog-windows-tiny11-source","catalog-windows-10","catalog-windows-11","catalog-windows-7-source","catalog-windows-xp-source")
        val actual=compose.onAllNodes(SemanticsMatcher("OS cards") {it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) in tags}).fetchSemanticsNodes().map {it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]}
        assertEquals(tags,actual)
        compose.onNodeWithTag("nav-launcher").performClick();waitTag("qemu-status")
        val density=compose.activity.resources.displayMetrics.density
        assertTrue(compose.onNodeWithTag("qemu-status").fetchSemanticsNode().boundsInRoot.height/density<=64f)
        shot("qemu-compact")
        compose.onNodeWithTag("qemu-status-toggle").performClick();waitTag("qemu-recheck");shot("qemu-details")
        compose.onNodeWithTag("nav-catalog").performClick();waitTag("catalog-sort")
        compose.onNodeWithTag("catalog-sort").performClick();waitTag("sort-name_asc");compose.onNodeWithTag("sort-name_asc").assertIsSelected()
        compose.onNodeWithTag("sort-curated").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("catalog-sort-sheet").fetchSemanticsNodes().isEmpty()}
        runBlocking {app.preferences.string("theme","light")}
        shot("catalog-light")
        compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        android.os.SystemClock.sleep(1200);waitTag("catalog-sort")
        compose.onNodeWithTag("catalog-sort").performScrollTo().performClick();waitTag("sort-name_asc");shot("sort-landscape")
        compose.onNodeWithTag("sort-size_desc").performScrollTo().performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("catalog-sort-sheet").fetchSemanticsNodes().isEmpty()}
        assertEquals("size_desc",runBlocking {app.preferences.flow.first().catalogSort})
        runBlocking {app.preferences.string("catalog_sort","curated");app.preferences.string("theme","dark");app.preferences.string("motion","full")}
        compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
