package dev.osify.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.osify.app.ui.OsifyApp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="en-rUS-w412dp-h915dp",application=TestOsifyApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrimaryFlowUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun launch(motion: String="off"): AppViewModel {
        val app=ApplicationProvider.getApplicationContext<OsifyApplication>()
        runBlocking {app.preferences.boolean("onboarded",true);app.preferences.string("language","en");app.preferences.string("motion",motion);app.preferences.string("theme","dark");app.preferences.boolean("dynamic",false)}
        val vm=AppViewModel(app)
        compose.setContent { OsifyApp(vm) }
        compose.waitUntil(15000) {compose.onAllNodesWithTag("nav-home").fetchSemanticsNodes().isNotEmpty()}
        return vm
    }
    @Test fun homeCatalogSearchAndHonestImageDetails() {
        launch()
        compose.onNodeWithTag("memory-card").assertExists()
        screenshot("dashboard")
        compose.onNodeWithTag("nav-catalog").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("catalog-alpine-virt").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("catalog-search").performTextInput("no-such-os")
        compose.onNodeWithText("No matching images. Try another search.").assertExists()
        compose.onNodeWithTag("catalog-search").performTextClearance()
        compose.onNodeWithTag("catalog-search").performTextInput("Windows")
        compose.onNodeWithTag("catalog-windows-10").assertExists()
        compose.onNodeWithTag("catalog-windows-11").assertExists()
        compose.onNodeWithTag("catalog-alpine-minirootfs").assertDoesNotExist()
        compose.onNodeWithTag("catalog-search").performTextClearance()
        screenshot("catalog")
        compose.onNodeWithTag("page-viewport").performTouchInput {swipe(start=center,end=center.copy(y=center.y-100f),durationMillis=1000)}
        screenshot("033-catalog-scroll-fade")
        compose.onNodeWithTag("catalog-alpine-virt").performScrollTo().performClick()
        compose.onNodeWithTag("image-download").assertExists()
        compose.onNodeWithTag("catalog-detail").assertExists()
    }
    @Test fun libraryPresetAndSettingsNavigation() {
        launch()
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithTag("import-image").assertExists()
        compose.onNodeWithTag("nav-launcher").performClick()
        compose.onNodeWithText("Built-in QEMU").assertExists()
        compose.onNodeWithTag("new-vm").performClick()
        compose.onNodeWithText("First, add an ISO image").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-title").assertExists()
        screenshot("settings")
        compose.onNodeWithTag("settings-appearance").performClick()
        compose.onNodeWithTag("settings-motion").performScrollTo()
        compose.onNodeWithTag("page-viewport").performTouchInput {swipeUp()}
        screenshot("033-settings-scroll-fade")
        compose.onNodeWithTag("settings-language").performScrollTo().performClick()
        compose.onNodeWithText("Русский").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Оформление").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun russianLauncherWithSavedMachineAndEditor() {
        val app=ApplicationProvider.getApplicationContext<OsifyApplication>()
        runBlocking {
            app.startupRecovery.join()
            app.database.dao().putImage(dev.osify.app.data.ImageRecord("tiny-iso","tiny11.iso","local","UNKNOWN","import","",1,"0".repeat(64),state="COMPLETE",imported=true,fileName="preview-only.iso"))
            app.database.dao().virtualMachine(dev.osify.app.data.VirtualMachine("visual-vm","Tiny 11","WINDOWS","tiny-iso",firmware="UEFI"))
        }
        launch()
        runBlocking {app.preferences.string("language","ru")}
        compose.onNodeWithTag("nav-launcher").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("vm-visual-vm").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(5000) {compose.onAllNodesWithTag("os-art-WINDOWS",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        screenshot("041-launcher-top-ru")
        compose.onNodeWithTag("vm-visual-vm").performScrollTo()
        screenshot("040-launcher-machine-ru")
        compose.onNodeWithText("Параметры").performClick()
        compose.onNodeWithText("Установленного диска").performClick()
        compose.onNodeWithTag("vm-edit-visual-vm").performClick()
        compose.onNodeWithTag("vm-ram").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(768f)}
        compose.onNodeWithTag("vm-cores").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(3f)}
        compose.onNodeWithTag("vm-ram-value",useUnmergedTree=true).assertTextEquals("768 MB")
        compose.onNodeWithTag("vm-edit-save").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("vm-edit-save").fetchSemanticsNodes().isEmpty()}
        val configured=runBlocking {app.database.dao().virtualMachine("visual-vm")}!!
        assertEquals(768,configured.ramMiB);assertEquals(3,configured.cores)
        compose.onNodeWithTag("vm-menu-visual-vm").performClick()
        compose.onNodeWithText("Журнал движка").performClick()
        compose.onNodeWithTag("logs-close").performClick()
        compose.onNodeWithTag("new-vm").performScrollTo().performClick()
        compose.onNodeWithTag("vm-name").assertExists()
        screenshot("040-create-vm-ru",true)
        compose.onNodeWithText("Отмена").performClick()
    }
    @Test fun illustratedCatalogFiltersAndUbuntuSource() {
        val app=ApplicationProvider.getApplicationContext<OsifyApplication>()
        launch()
        runBlocking {app.preferences.string("language","ru")}
        compose.onNodeWithTag("nav-catalog").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("os-art-WINDOWS",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("os-art-UBUNTU",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        screenshot("041-catalog-top-ru")
        compose.onNodeWithTag("filter-linux").performClick()
        compose.onNodeWithTag("catalog-windows-11").assertDoesNotExist()
        compose.onNodeWithTag("catalog-ubuntu-desktop").assertExists()
        compose.onNodeWithTag("filter-iso").performClick()
        compose.onNodeWithTag("catalog-alpine-minirootfs").assertDoesNotExist()
        screenshot("041-catalog-linux-iso-ru")
        compose.onNodeWithTag("catalog-ubuntu-desktop").performClick()
        compose.onNodeWithTag("image-download").assertExists()
        screenshot("041-ubuntu-details-ru")
    }
    @Test fun pageTransitionsAndSettingsReflow() {
        launch("full")
        compose.mainClock.autoAdvance=false
        val headerBounds=compose.onNodeWithTag("app-header").fetchSemanticsNode().boundsInRoot
        for(destination in listOf("catalog","launcher","library","settings","home","settings")) {
            compose.onNodeWithTag("nav-$destination").performClick()
            compose.mainClock.advanceTimeBy(80);screenshot("v11-$destination-enter")
            kotlin.test.assertEquals(headerBounds,compose.onNodeWithTag("app-header").fetchSemanticsNode().boundsInRoot)
            compose.mainClock.advanceTimeBy(200);screenshot("v11-$destination-middle")
            kotlin.test.assertEquals(headerBounds,compose.onNodeWithTag("app-header").fetchSemanticsNode().boundsInRoot)
            compose.mainClock.advanceTimeBy(900);screenshot("v11-$destination-ready")
        }
        compose.onNodeWithTag("settings-appearance").performClick()
        compose.mainClock.advanceTimeBy(150);screenshot("v11-appearance-enter")
        compose.mainClock.advanceTimeBy(900);screenshot("v11-appearance-ready")
        compose.onNodeWithTag("settings-back").performClick()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithTag("settings-title").assertExists()
        compose.mainClock.autoAdvance=true
    }
    @Test fun cardMorphHasIntermediateGeometryAndReversesToItsSource() {
        launch("full")
        compose.onNodeWithTag("nav-catalog").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("catalog-windows-11").fetchSemanticsNodes().isNotEmpty()}
        val source=compose.onNodeWithTag("catalog-windows-11").fetchSemanticsNode().boundsInRoot
        val header=compose.onNodeWithTag("app-header").fetchSemanticsNode().boundsInRoot
        val viewport=compose.onNodeWithTag("page-viewport").fetchSemanticsNode().boundsInRoot
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("catalog-windows-11").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        val middle=compose.onNodeWithTag("catalog-detail-hero").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(middle.width>source.width)
        kotlin.test.assertTrue(middle.width<viewport.width)
        kotlin.test.assertEquals(header,compose.onNodeWithTag("app-header").fetchSemanticsNode().boundsInRoot)
        fun paragraph(tag: String): Triple<androidx.compose.ui.unit.TextUnit,Int,List<Int>> {
            var snapshot: Triple<androidx.compose.ui.unit.TextUnit,Int,List<Int>>?=null
            compose.onNodeWithTag(tag,useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {action->
                val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(results)
                val r=results.single();snapshot=Triple(r.layoutInput.style.fontSize,r.size.width,(0 until r.lineCount).map {r.getLineEnd(it)})
            }
            return snapshot!!
        }
        val compactParagraph=paragraph("catalog-description-compact")
        val fullParagraph=paragraph("catalog-description-expanded")
        screenshot("042-morph-160ms")
        compose.mainClock.advanceTimeBy(280)
        assertEquals(compactParagraph,paragraph("catalog-description-compact"))
        assertEquals(fullParagraph,paragraph("catalog-description-expanded"))
        screenshot("042-morph-440ms")
        compose.mainClock.advanceTimeBy(700)
        val expanded=compose.onNodeWithTag("catalog-detail-hero").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertEquals(viewport.width,expanded.width)
        kotlin.test.assertEquals(viewport.top,expanded.top)
        screenshot("042-morph-expanded")
        compose.onNodeWithTag("catalog-detail-back").performClick()
        compose.mainClock.advanceTimeBy(160)
        val returning=compose.onNodeWithTag("catalog-detail-hero").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(returning.width<expanded.width && returning.width>source.width)
        assertEquals(fullParagraph,paragraph("catalog-description-expanded"))
        screenshot("045-close-160ms")
        compose.mainClock.advanceTimeBy(280)
        assertEquals(compactParagraph,paragraph("catalog-description-compact"))
        screenshot("045-close-440ms")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("catalog-detail").assertDoesNotExist()
        kotlin.test.assertEquals(source,compose.onNodeWithTag("catalog-windows-11").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance=true
    }
    private fun screenshot(name: String,dialog: Boolean=false) {
        val file=File("build/screenshots/$name.png");file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=if(dialog) android.view.inspector.WindowInspector.getGlobalWindowViews().last() else compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
}
