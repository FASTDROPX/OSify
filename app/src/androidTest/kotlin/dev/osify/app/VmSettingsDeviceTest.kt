package dev.osify.app
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.osify.app.data.VirtualMachine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VmSettingsDeviceTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private fun shot(app: OsifyApplication,name: String) {
  val b=instrumentation.uiAutomation.takeScreenshot()
  File(app.filesDir,"visuals/$name.png").also {it.parentFile!!.mkdirs();it.outputStream().use {out->b.compress(Bitmap.CompressFormat.PNG,100,out)}};b.recycle()
 }
 private fun waitNode(predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo {
  val end=android.os.SystemClock.uptimeMillis()+12000
  while(android.os.SystemClock.uptimeMillis()<end) {
   val q=java.util.ArrayDeque<AccessibilityNodeInfo>();instrumentation.uiAutomation.rootInActiveWindow?.let {q.add(it)}
   while(q.isNotEmpty()) {val n=q.removeFirst();if(predicate(n)) return n;for(i in 0 until n.childCount) n.getChild(i)?.let {q.add(it)}}
   android.os.SystemClock.sleep(200)
  }
  error("Android document/chooser control missing")
 }
 @Test fun editSlidersPreserveDiskAndSaveAndShareTxt() {
  val app=compose.activity.application as OsifyApplication
  val vm=VirtualMachine("settings-device","Settings test","LINUX",imageId="console-device",ramMiB=512,cores=1,diskGiB=2)
  runBlocking {app.startupRecovery.join();app.preferences.boolean("onboarded",true);app.preferences.string("language","ru");app.preferences.string("theme","dark");app.preferences.string("motion","off");app.database.dao().virtualMachine(vm)}
  val disk=File(app.filesDir,"vms/${vm.id}/disk.raw").also {it.parentFile!!.mkdirs();it.writeText("guest-disk-sentinel")}
  compose.waitUntil(15000) {compose.onAllNodesWithTag("nav-launcher").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("nav-launcher").performClick()
  compose.onNodeWithTag("vm-menu-${vm.id}").performScrollTo().performClick()
  compose.onNodeWithText("Изменить конфигурацию").performClick()
  compose.onNodeWithTag("vm-ram").performSemanticsAction(SemanticsActions.SetProgress) {it(768f)}
  compose.onNodeWithTag("vm-cores").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) {it(3f)}
  compose.onNodeWithTag("vm-ram-value",true).assertTextEquals("768 MB");compose.onNodeWithTag("vm-cores-value",true).assertTextEquals("3")
  shot(app,"machine-edit-sliders")
  compose.onNodeWithTag("vm-edit-save").performClick()
  compose.waitUntil(10000) {compose.onAllNodesWithTag("vm-edit-save").fetchSemanticsNodes().isEmpty()}
  val saved=runBlocking {app.database.dao().virtualMachine(vm.id)}!!
  assertEquals(768,saved.ramMiB);assertEquals(3,saved.cores);assertEquals(vm.imageId,saved.imageId);assertEquals("guest-disk-sentinel",disk.readText())
  compose.onNodeWithTag("vm-menu-${vm.id}").performScrollTo().performClick();compose.onNodeWithText("Журнал движка").performClick()
  compose.waitUntil(10000) {compose.onAllNodesWithText("OSify 0.4.5",substring=true).fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("logs-save").performClick()
  val filename="OSify-device-log-${System.currentTimeMillis()}.txt"
  waitNode {it.isEditable && it.className?.toString()?.contains("EditText")==true}.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,filename)})
  shot(app,"logs-save-android")
  waitNode {it.isClickable && it.isEnabled && (it.viewIdResourceName?.endsWith("button1")==true || it.viewIdResourceName?.endsWith("action_menu_save")==true || it.text?.toString()?.uppercase() in setOf("SAVE","СОХРАНИТЬ"))}.performAction(AccessibilityNodeInfo.ACTION_CLICK)
  compose.waitUntil(15000) {compose.onAllNodesWithTag("logs-share").fetchSemanticsNodes().isNotEmpty()}
  android.os.SystemClock.sleep(1000)
  val txt=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat /sdcard/Download/$filename")).bufferedReader().use {it.readText()}
  assertTrue("Saved TXT must contain configured resources",txt.contains("OSify 0.4.5") && txt.contains("RAM=768 MiB; CPU=3"))
  File(app.filesDir,"visuals/saf-export.txt").writeText(txt)
  compose.onNodeWithTag("logs-share").performClick()
  waitNode {it.packageName?.toString() !in setOf("dev.osify.app","com.google.android.inputmethod.latin")}
  android.os.SystemClock.sleep(800);shot(app,"logs-share-android")
  instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
  compose.onNodeWithTag("logs-close").performClick()
  runBlocking {app.vmEngine.remove(saved)}
 }
}
