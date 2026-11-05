package dev.osify.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.osify.app.data.VirtualMachine
import dev.osify.app.engine.*
import dev.osify.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket

@RunWith(AndroidJUnit4::class)
class AdvancedSmokeDeviceTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun shot(app: OsifyApplication,name: String) {
  val b=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(app.filesDir,"visuals/$name.png").also {it.parentFile!!.mkdirs();it.outputStream().use {out->b.compress(Bitmap.CompressFormat.PNG,100,out)}};b.recycle()
 }
 @Test fun editDiagnoseLaunchAndStop()=runBlocking {
  val app=compose.activity.application as OsifyApplication;app.startupRecovery.join()
  app.preferences.boolean("onboarded",true);app.preferences.string("language","ru");app.preferences.string("theme","dark");app.preferences.string("motion","off")
  val dao=app.database.dao();val image=dao.image("console-device")!!
  val machine=VirtualMachine("advanced046","Advanced test","LINUX",image.id,512,1,2,ServerSocket(0).use {it.localPort},"Smoke046")
  dao.virtualMachine(machine)
  try {
   compose.waitUntil(12000) {compose.onAllNodesWithTag("nav-launcher").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithTag("nav-launcher").performClick().also {android.os.SystemClock.sleep(350)}
   compose.onNodeWithTag("vm-menu-${machine.id}").performScrollTo().performClick().also {android.os.SystemClock.sleep(350)};compose.onNodeWithText("Изменить конфигурацию").performClick().also {android.os.SystemClock.sleep(350)}
   compose.onNodeWithTag("vm-advanced-tab").performClick().also {android.os.SystemClock.sleep(350)};compose.waitForIdle();shot(app,"046-advanced-cpu")
   compose.onNodeWithTag("advanced-tcg").performScrollTo().performClick().also {android.os.SystemClock.sleep(350)};compose.onNodeWithText("Несколько потоков").performClick().also {android.os.SystemClock.sleep(350)}
   compose.onNodeWithTag("advanced-boot").performScrollTo().performClick().also {android.os.SystemClock.sleep(350)};compose.onNodeWithText("Установленный диск").performClick().also {android.os.SystemClock.sleep(350)}
   compose.onNodeWithTag("vm-edit-save").performClick().also {android.os.SystemClock.sleep(350)}
   compose.waitUntil(10000) {compose.onAllNodesWithTag("vm-edit-save").fetchSemanticsNodes().isEmpty()}
   var saved=dao.virtualMachine(machine.id)!!;val h=VmHardware.decode(saved.hardwareJson)
   assertEquals("multi",h.tcg);assertEquals("disk",h.boot)
   app.vmEngine.configure(machine.id,saved.name,512,1,"BIOS",h.copy(boot="iso",network="e1000"));saved=dao.virtualMachine(machine.id)!!
   app.vmEngine.launch(saved,true)
   withTimeout(30000) {while(dao.virtualMachine(machine.id)?.state=="REQUESTED") delay(150)}
   assertEquals("DISPLAY_AVAILABLE",dao.virtualMachine(machine.id)?.state)
   app.vmEngine.control(dao.virtualMachine(machine.id)!!,"force")
   withTimeout(10000) {while(dao.virtualMachine(machine.id)?.state!="STOPPED") delay(150)}
  } finally {
   val latest=dao.virtualMachine(machine.id)!!
   if(latest.state !in setOf("STOPPED","FAILED")) runCatching {app.vmEngine.control(latest,"force")}
   withTimeoutOrNull(10000) {while(dao.virtualMachine(machine.id)?.state !in setOf("STOPPED","FAILED")) delay(150)}
   dao.virtualMachine(machine.copy(state="STOPPED"));app.vmEngine.remove(machine);app.preferences.string("motion","full")
  }
 }
}
