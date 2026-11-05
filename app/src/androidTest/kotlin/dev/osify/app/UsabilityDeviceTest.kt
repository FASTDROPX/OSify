package dev.osify.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.osify.app.data.VirtualMachine
import dev.osify.app.engine.VmLogs
import dev.osify.core.VmHardware
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UsabilityDeviceTest {
    companion object {
        @BeforeClass @JvmStatic fun setup()=runBlocking {
            val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OsifyApplication
            app.startupRecovery.join();app.preferences.boolean("onboarded",true);app.preferences.string("language","ru");app.preferences.string("motion","off");app.preferences.string("theme","dark")
            app.database.dao().virtualMachine(VirtualMachine("ui-tools-test","UI tools fixture","LINUX",hardwareJson=VmHardware.encode(VmHardware(architecture="X86_64"))))
            File(VmLogs.folder(app,"ui-tools-test"),"disk.raw").writeBytes(ByteArray(1024*1024).also {it[0]=1})
        }
    }
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag:String) {compose.waitUntil(12000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun shot(name:String) {
        android.os.SystemClock.sleep(500)
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.filesDir,"visuals/0412-$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    private fun openTools() {compose.onNodeWithTag("vm-menu-ui-tools-test").performScrollTo().performClick();compose.onNodeWithText("Файлы и драйверы").performClick();waitTag("guest-add-files")}
    @Test fun favoritesSearchCloneAndToolsInBothOrientations() {
        val app=compose.activity.application as OsifyApplication
        try {
            waitTag("nav-catalog");compose.onNodeWithTag("nav-catalog").performClick();waitTag("filter-favorites")
            compose.onNodeWithTag("catalog-search").performTextReplacement("Windows XP")
            compose.onNodeWithTag("catalog-search").performImeAction()
            waitTag("favorite-windows-xp-source");compose.onNodeWithTag("favorite-windows-xp-source").performScrollTo().performClick()
            compose.waitUntil(5000) {runBlocking {"windows-xp-source" in app.preferences.flow.first().favorites}}
            compose.onNodeWithTag("filter-favorites").performScrollTo().performClick();shot("favorites")
            compose.onNodeWithTag("nav-launcher").performClick();waitTag("launcher-search")
            compose.onNodeWithTag("launcher-search").performTextReplacement("UI tools fixture");shot("launcher-search")
            openTools();shot("files-portrait")
            compose.onNodeWithTag("guest-tools-tab-1",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();Assert.assertTrue(action(layouts));Assert.assertEquals(layouts.single().layoutInput.text.length,layouts.single().getLineEnd(0,visibleEnd=true))
            }
            compose.onNodeWithText("Драйверы",substring=false).performClick();waitTag("guest-driver-download");shot("drivers-portrait")
            compose.onNodeWithText("Экран",substring=false).performClick();shot("display-portrait")
            compose.onNodeWithContentDescription("Закрыть").performClick()
            compose.onNodeWithTag("vm-menu-ui-tools-test").performScrollTo().performClick();compose.onNodeWithText("Клонировать машину").performClick();shot("clone")
            compose.onNodeWithText("Создать копию").performClick()
            compose.waitUntil(10000) {runBlocking {app.database.dao().virtualMachines().first().any {it.name=="UI tools fixture · copy"}}}
            compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            android.os.SystemClock.sleep(1500);waitTag("launcher-search")
            compose.onNodeWithTag("launcher-search").performTextReplacement("UI tools fixture")
            openTools();shot("files-landscape")
            compose.onNodeWithText("Драйверы",substring=false).performClick();waitTag("guest-driver-download");compose.onNodeWithTag("guest-driver-download").performScrollTo();shot("drivers-landscape")
            compose.onNodeWithContentDescription("Закрыть").performClick()
        } finally {
            runBlocking {
                app.database.dao().virtualMachines().first().filter {it.name.startsWith("UI tools fixture") && it.state=="STOPPED"}.forEach {app.vmEngine.remove(it)}
                if("windows-xp-source" in app.preferences.flow.first().favorites)app.preferences.favorite("windows-xp-source")
                app.preferences.string("motion","full")
            }
            compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
