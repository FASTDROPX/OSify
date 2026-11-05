package dev.osify.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*

/** Runs the actual installed application, including intermediate card geometry, on an emulator. */
@RunWith(AndroidJUnit4::class)
class CatalogDeviceTest {
    companion object {
        @org.junit.BeforeClass @JvmStatic fun stopHomeWaveBeforeActivity() {
            val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OsifyApplication
            runBlocking {app.preferences.string("motion","off")}
        }
    }
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun prepare(motion: String="full",theme: String="dark") {
        val app=compose.activity.application as OsifyApplication
        runBlocking {
            app.preferences.boolean("onboarded",true)
            app.preferences.string("language","ru")
            app.preferences.string("motion","off")
            app.preferences.string("theme",theme)
            app.preferences.boolean("dynamic",false)
        }
        compose.waitUntil(15000) {compose.onAllNodesWithTag("nav-catalog").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav-catalog").performClick()
        runBlocking {app.preferences.string("motion",motion)}
        compose.waitUntil(15000) {compose.onAllNodesWithTag("catalog-windows-11").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
    }
    private fun shot(name: String) {
        android.os.SystemClock.sleep(1000)
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output=File(compose.activity.filesDir,"visuals/$name.png").apply {parentFile!!.mkdirs()}
        output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun expandReverseFiltersAndFixedHeader() {
        prepare()
        shot("catalog-dark")
        val header=compose.onNodeWithTag("app-header").fetchSemanticsNode().boundsInRoot
        val card=compose.onNodeWithTag("catalog-windows-11").fetchSemanticsNode().boundsInRoot
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun tap(x: Float,y: Float): Long {
            val now=android.os.SystemClock.uptimeMillis()
            listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP).forEach {action->
                val event=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,x,y,0).apply {source=android.view.InputDevice.SOURCE_TOUCHSCREEN}
                automation.injectInputEvent(event,true);event.recycle()
            }
            return now
        }
        fun frameAt(start: Long,delay: Long,name: String) {
            val remaining=delay-(android.os.SystemClock.uptimeMillis()-start)
            if(remaining>0) android.os.SystemClock.sleep(remaining)
            val bitmap=automation.takeScreenshot()
            File(compose.activity.filesDir,"visuals/$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        val openedAt=tap(card.left+card.width*.55f,card.top+card.height*.3f)
        frameAt(openedAt,160,"expand-160ms");frameAt(openedAt,440,"expand-440ms")
        compose.waitUntil(5000) {val h=compose.onNodeWithTag("catalog-detail-hero").fetchSemanticsNode().boundsInRoot;h.width>=compose.onNodeWithTag("page-viewport").fetchSemanticsNode().boundsInRoot.width-1}
        shot("windows-detail-dark")
        val hero=compose.onNodeWithTag("catalog-detail-hero").fetchSemanticsNode().boundsInRoot
        val viewport=compose.onNodeWithTag("page-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(hero.width-viewport.width)<2f)
        assertTrue(abs(hero.top-viewport.top)<2f)
        assertEquals(header,compose.onNodeWithTag("app-header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("image-download").assertIsDisplayed()
        val back=compose.onNodeWithTag("catalog-detail-back").fetchSemanticsNode().boundsInRoot
        val closedAt=tap(back.center.x,back.center.y)
        frameAt(closedAt,160,"close-160ms");frameAt(closedAt,440,"close-440ms")
        compose.waitUntil(5000) {compose.onAllNodesWithTag("catalog-detail").fetchSemanticsNodes().isEmpty()}
        val returned=compose.onNodeWithTag("catalog-windows-11").fetchSemanticsNode().boundsInRoot
        assertEquals(card,returned)
        compose.onNodeWithTag("filter-linux").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("catalog-windows-11").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("filter-iso").performClick()
        compose.onNodeWithTag("catalog-alpine-minirootfs").assertDoesNotExist()
        shot("catalog-linux-iso")
        compose.onNodeWithTag("catalog-ubuntu-desktop").performClick()
        compose.waitUntil(5000) {
            val h=compose.onNodeWithTag("catalog-detail-hero").fetchSemanticsNode().boundsInRoot
            val v=compose.onNodeWithTag("page-viewport").fetchSemanticsNode().boundsInRoot
            abs(h.width-v.width)<2f && abs(h.top-v.top)<2f
        }
        shot("ubuntu-detail")
        compose.onNodeWithTag("catalog-detail").performTouchInput {swipeUp()}
        compose.onNodeWithTag("catalog-detail-back").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("catalog-detail").fetchSemanticsNodes().isEmpty()}
    }
    @Test fun motionOffLightThemeAndPrimaryScreens() {
        prepare("off","light")
        shot("catalog-light")
        compose.onNodeWithTag("catalog-windows-11").performClick()
        compose.onNodeWithTag("image-download").assertIsDisplayed()
        shot("windows-detail-light")
        compose.onNodeWithTag("catalog-detail-back").performClick()
        compose.onNodeWithTag("nav-home").performClick()
        shot("home-light")
        compose.onNodeWithTag("nav-launcher").performClick()
        shot("launcher-light")
        compose.onNodeWithTag("new-vm").performClick()
        compose.onNodeWithText("Создайте свою систему").assertExists()
        shot("create-machine-light")
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("nav-library").performClick()
        shot("library-light")
        compose.onNodeWithTag("nav-settings").performClick()
        shot("settings-light")
        compose.onNodeWithTag("settings-appearance").performClick()
        shot("appearance-light")
    }
    @Test fun largeTypeAndLauncherLogos() {
        prepare("off","dark")
        compose.onNodeWithTag("catalog-search").performTextInput("Windows 10")
        compose.onNodeWithTag("catalog-windows-10").performScrollTo().performClick()
        compose.onNodeWithTag("image-download").performScrollTo().assertIsDisplayed()
        shot("windows10-large-type")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.waitUntil(5000) {compose.onAllNodesWithTag("catalog-detail").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("catalog-search").performTextClearance()
        compose.onNodeWithTag("catalog-windows-11").performClick()
        compose.onNodeWithTag("image-download").performScrollTo().assertIsDisplayed()
        shot("windows11-large-type")
        compose.onNodeWithTag("nav-launcher").performClick()
        val app=compose.activity.application as OsifyApplication
        val examples=listOf("win11" to "Windows 11", "win10" to "Windows 10", "ubuntu" to "Ubuntu", "alpine" to "Alpine Linux")
        // UI fixtures only: these machines are never booted or represented as running guests.
        try {
            runBlocking {
                app.startupRecovery.join()
                examples.forEach {(id,title)->app.database.dao().virtualMachine(dev.osify.app.data.VirtualMachine("ui-preview-$id",title,if(id.startsWith("win")) "WINDOWS" else "LINUX"))}
            }
            examples.forEach {(id,_)->
                compose.waitUntil(5000) {compose.onAllNodesWithTag("vm-ui-preview-$id").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("vm-ui-preview-$id").performScrollTo()
                shot("launcher-$id-large-type")
            }
            compose.onNodeWithTag("nav-library").performClick()
            shot("library-large-type")
            compose.onNodeWithTag("nav-settings").performClick()
            shot("settings-large-type")
        } finally {runBlocking {examples.forEach {(id,_)->app.database.dao().deleteStoppedVm("ui-preview-$id")}}}
    }

    @Test fun realCatalogBrandingAndWindowsEditions() {
        prepare("off","dark")
        for((query,logo) in listOf("Windows XP" to "WINDOWSXP","Windows 7" to "WINDOWS7","Bodhi" to "BODHI","CachyOS" to "CACHYOS","Chimera" to "CHIMERALINUX")) {
            compose.onNodeWithTag("catalog-search").performTextClearance()
            compose.onNodeWithTag("catalog-search").performTextInput(query)
            compose.onNodeWithTag("catalog-search").performImeAction()
            compose.onNodeWithTag("os-logo-$logo",useUnmergedTree=true).assertExists()
            shot("brand-${logo.lowercase()}")
        }
        compose.onNodeWithTag("catalog-search").performTextClearance()
        compose.onNodeWithTag("catalog-search").performTextInput("Tiny")
        compose.onNodeWithTag("catalog-search").performImeAction()
        compose.onNodeWithText("Tiny11").assertExists()
        compose.onNodeWithText("Tiny10").assertExists()
        compose.onNodeWithText("Лучший выбор").assertExists()
        shot("tiny-windows-gold")
    }

    @Test fun tileStartsDownloadAndDetailCounterStaysInsideButton() {
        prepare("off","dark")
        val app=compose.activity.application as OsifyApplication
        var row: dev.osify.app.data.ImageRecord?=null
        try {
            compose.onNodeWithTag("catalog-search").performTextInput("Alpine")
            val action=hasTestTag("download-fill") and hasAnyAncestor(hasTestTag("catalog-alpine-virt"))
            compose.onNode(action,useUnmergedTree=true).performClick()
            compose.onNodeWithTag("confirm-download").assertDoesNotExist()
            compose.waitUntil(20000) {
                row=runBlocking {app.database.dao().images().first().firstOrNull {it.flavor=="alpine-virt" && !it.imported}}
                row!=null
            }
            compose.waitUntil(60000) {
                row=runBlocking {app.database.dao().image(row!!.id)}
                row!!.bytes>=1_000_000 || row!!.state=="FAILED"
            }
            assertTrue(row!!.error,row!!.bytes>=1_000_000)
            compose.onNode(action,useUnmergedTree=true).performClick()
            compose.waitUntil(10000) {runBlocking {app.database.dao().image(row!!.id)?.state=="PAUSED"}}
            compose.onNodeWithTag("catalog-alpine-virt").performClick()
            compose.onNodeWithTag("download-byte-counter",useUnmergedTree=true).assertIsDisplayed()
            compose.onNodeWithText("Приостановлено / частичные данные сохранены").assertDoesNotExist()
            shot("download-counter-paused")
            compose.onNodeWithTag("catalog-detail-back").performClick()
            shot("compact-download-paused")
        } finally {
            row?.let {saved->runBlocking {
                app.database.dao().pauseIfActive(saved.id)
                dev.osify.app.data.ImageRepository(app,app.database.dao(),app.preferences).delete(saved.id)
            }}
        }
    }

}
