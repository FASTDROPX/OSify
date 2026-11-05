package dev.osify.app

import android.content.*
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.osify.app.data.ImageRecord
import dev.osify.app.data.VirtualMachine
import dev.osify.app.engine.GuestConsoleActivity
import dev.osify.app.engine.FramebufferView
import dev.osify.app.engine.VmLogs
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.*

/** Tests the Material controls against a real, newly booted Alpine guest. */
@RunWith(AndroidJUnit4::class)
class GuestConsoleDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun shot(app: OsifyApplication,name: String) {
        compose.waitForIdle();android.os.SystemClock.sleep(400)
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(app.filesDir,"visuals/$name.png").also {it.parentFile!!.mkdirs();it.outputStream().use {out->bitmap.compress(Bitmap.CompressFormat.PNG,100,out)}}
        bitmap.recycle()
    }
    private fun frame(view: View): FramebufferView? {
        if(view is FramebufferView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) frame(view.getChildAt(i))?.let {return it}
        return null
    }
    @Test fun clipboardMouseKeysAndImmersiveModeWorkInActualGuest()=runBlocking {
        val app=instrumentation.targetContext.applicationContext as OsifyApplication
        app.startupRecovery.join()
        app.preferences.string("language","ru");app.preferences.string("theme","dark");app.preferences.string("motion","off")
        app.getSharedPreferences("guest-controls",Context.MODE_PRIVATE).edit().putBoolean("pointer",false).commit()
        val source=File(app.getExternalFilesDir(null),"boot-test.iso")
        check(source.isFile) {"Push official Alpine virt ISO to files/boot-test.iso"}
        val file=File(app.filesDir,"images/console-device.image").also {it.parentFile!!.mkdirs();source.copyTo(it,overwrite=true)}
        val dao=app.database.dao()
        val image=ImageRecord("console-device","Alpine console test.iso","3.24.3","X86_64","alpine-virt","",file.length(),"cdc1bc0efc64d8d611bf3733f54868172a545888dfd726f518bf279bcb0ff1af",bytes=file.length(),state="COMPLETE",imported=true,fileName=file.name)
        dao.putImage(image)
        val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
        val vm=VirtualMachine("console-device","Alpine Linux","LINUX",image.id,512,1,2,port,"UiTest44")
        dao.virtualMachine(vm)
        var scenario: ActivityScenario<GuestConsoleActivity>?=null
        try {
            app.vmEngine.launch(vm,true)
            withTimeout(75000) {while(dao.virtualMachine(vm.id)?.state=="REQUESTED") delay(200)}
            assertEquals("DISPLAY_AVAILABLE",dao.virtualMachine(vm.id)?.state)
            val serial=File(app.filesDir,"vms/${vm.id}/serial.log")
            withTimeout(120000) {while(!serial.exists() || !serial.readText().contains("localhost login:")) delay(500)}
            scenario=ActivityScenario.launch(Intent(app,GuestConsoleActivity::class.java).putExtra("vm",vm.id))
            compose.waitUntil(15000) {compose.onAllNodesWithTag("console-text").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("console-text").assertIsEnabled()
            for(tag in listOf("console-keyboard","console-text","console-pointer","console-keys")) {
                compose.onNodeWithTag("$tag-label",useUnmergedTree=true).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) {action->
                    val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    assertTrue(action(layouts))
                    val result=layouts.single()
                    assertEquals("$tag must show full label",result.layoutInput.text.length,result.getLineEnd(0,visibleEnd=true))
                    assertTrue("$tag must fit its bounds",result.getLineRight(0)<=result.size.width+.5f)
                    assertFalse("$tag height must not clip",result.didOverflowHeight)
                }
            }
            shot(app,"console-dark")
            fun insert(text: String,saveShot: Boolean=false) {
                compose.onNodeWithTag("console-text").performClick()
                compose.onNodeWithTag("console-text-input").performTextReplacement(text)
                compose.onNodeWithTag("console-read-clipboard-label",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {action->
                    val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();assertTrue(action(layouts))
                    assertEquals(layouts.single().layoutInput.text.length,layouts.single().getLineEnd(0,visibleEnd=true))
                }
                if(saveShot) shot(app,"console-text-keyboard")
                compose.onNodeWithTag("console-paste").performScrollTo().performClick()
                compose.waitUntil(5000) {compose.onAllNodesWithTag("console-sheet").fetchSemanticsNodes().isEmpty()}
            }
            insert("root\n");delay(1500)
            insert("echo osify-paste-ok > /dev/ttyS0\n",true)
            withTimeout(10000) {while(!serial.readText().contains("osify-paste-ok")) delay(200)}
            scenario.onActivity {activity->
                val clip=activity.getSystemService(ClipboardManager::class.java)
                assertEquals("echo osify-paste-ok > /dev/ttyS0\n",clip.primaryClip!!.getItemAt(0).text.toString())
                clip.setPrimaryClip(ClipData.newPlainText("Test","uname -a"))
            }
            compose.onNodeWithTag("console-text").performClick()
            compose.onNodeWithTag("console-read-clipboard").performScrollTo().performClick()
            compose.onNodeWithTag("console-text-input").assertTextContains("uname -a")
            compose.onNodeWithTag("console-paste").performScrollTo().performClick()
            delay(3200)
            compose.onNodeWithTag("console-keys").performClick()
            compose.onNodeWithTag("key-Enter").performScrollTo().performClick()
            compose.onNodeWithTag("console-sheet-close").performClick()
            compose.onNodeWithTag("console-keyboard").performClick()
            delay(800)
            compose.onNodeWithTag("console-keyboard").assertIsSelected()
            instrumentation.sendStringSync("echo osify-direct-ok > /dev/ttyS0")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
            withTimeout(10000) {while(!serial.readText().contains("osify-direct-ok")) delay(200)}
            delay(6000)
            shot(app,"console-direct-keyboard")
            compose.onNodeWithTag("console-keyboard").performClick()
            compose.onNodeWithTag("console-keys").performClick()
            compose.onNodeWithTag("key-Ctrl").performClick()
            compose.onNodeWithTag("key-Ctrl").assertIsSelected()
            compose.onNodeWithTag("key-Ctrl").performClick()
            shot(app,"console-keys")
            compose.onNodeWithTag("console-sheet-close").performClick()
            compose.onNodeWithTag("console-pointer").performClick()
            compose.onNodeWithTag("mouse-drag").performClick()
            compose.onNodeWithTag("mouse-drag").assertIsSelected()
            compose.onNodeWithTag("mouse-drag").performClick()
            val events=java.util.concurrent.CopyOnWriteArrayList<Int>()
            scenario.onActivity {activity->frame(activity.window.decorView)!!.inputObserver={_,_,mask->events+=mask}}
            compose.onNodeWithTag("guest-display").performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(100f,-60f),400)}
            compose.onNodeWithTag("mouse-right").performClick()
            assertTrue(events.contains(4));assertEquals(0,events.last())
            compose.onNodeWithTag("mouse-drag").assertIsNotSelected()
            shot(app,"console-pointer")
            compose.onNodeWithTag("console-fullscreen").performClick()
            compose.onNodeWithTag("console-header").assertDoesNotExist()
            compose.onNodeWithTag("console-dock").assertIsDisplayed()
            compose.onNodeWithTag("console-restore").assertIsDisplayed()
            android.os.SystemClock.sleep(800)
            scenario.onActivity {activity->assertFalse(ViewCompat.getRootWindowInsets(activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.statusBars()))}
            shot(app,"console-fullscreen")
            compose.onNodeWithTag("console-restore").performClick()
            compose.waitUntil(5000) {compose.onAllNodesWithTag("console-header").fetchSemanticsNodes().isNotEmpty()}
            app.preferences.string("theme","light")
            compose.waitForIdle();shot(app,"console-light")
            scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            delay(1200);compose.waitForIdle();shot(app,"console-landscape")
            val dock=compose.onNodeWithTag("console-dock").fetchSemanticsNode().boundsInRoot
            val viewport=compose.onNodeWithTag("console-viewport").fetchSemanticsNode().boundsInRoot
            assertTrue("Landscape dock must be vertical on the right",dock.left>=viewport.right && dock.height>dock.width)
            assertTrue("Landscape guest must have sufficient width",viewport.width>dock.width*3)
            compose.onNodeWithTag("console-fullscreen").performClick()
            shot(app,"console-landscape-fullscreen")
            compose.onNodeWithTag("console-restore").performClick()
            scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            delay(1000)
            // Real NAT + guest RTL8139 driver. DHCP runs inside Alpine, then DNS.
            insert("ip link set eth0 up\n")
            delay(1000)
            insert("udhcpc -i eth0 -q -n -t 3 -T 1 > /dev/ttyS0 2>&1\n")
            delay(10000)
            insert("timeout 12 nslookup example.com > /dev/ttyS0 2>&1\n")
            delay(14000)
            insert("echo osify-network-done > /dev/ttyS0\n")
            withTimeout(10000) {while(!serial.readText().contains("osify-network-done")) delay(200)}
            File(app.filesDir,"visuals/network-diagnostics.txt").writeText(serial.readText())
            assertTrue("NAT DHCP must assign a guest address",serial.readText().contains("10.0.2.15"))
            assertTrue("Guest DNS must resolve example.com",serial.readText().contains("Name:") && serial.readText().contains("example.com"))
            compose.onNodeWithTag("console-menu").performClick()
            compose.onNodeWithText("Логи машины · TXT").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("OSify 0.4.5",substring=true).fetchSemanticsNodes().isNotEmpty()}
            shot(app,"console-logs")
            val report=VmLogs.report(app,dao.virtualMachine(vm.id)!!)
            assertTrue(report.contains("osify-paste-ok"));assertFalse(report.contains(vm.password))
            val exported=VmLogs.snapshot(app,report)
            val share=VmLogs.shareIntent(app,exported)
            val uri=share.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
            assertEquals(report,app.contentResolver.openInputStream(uri)!!.bufferedReader().use {it.readText()})
            assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            File(app.filesDir,"visuals/console-report.txt").writeText(report)
            compose.onNodeWithTag("logs-close").performClick()
            compose.onNodeWithTag("console-back").performClick()
            shot(app,"console-exit")
            compose.onNodeWithTag("console-pip").performClick()
            delay(2200)
            var isPip=false;scenario.onActivity {isPip=it.isInPictureInPictureMode}
            assertTrue("Activity must enter actual Android PiP",isPip)
            assertEquals("DISPLAY_AVAILABLE",dao.virtualMachine(vm.id)?.state)
            shot(app,"console-pip")
            // Reopening the singleTask activity must expand the same live framebuffer.
            app.startActivity(Intent(app,GuestConsoleActivity::class.java).putExtra("vm",vm.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(1800)
            scenario.onActivity {assertFalse(it.isInPictureInPictureMode)}
            compose.waitUntil(10000) {compose.onAllNodesWithTag("console-back").fetchSemanticsNodes().isNotEmpty()}
            insert("echo osify-pip-restored > /dev/ttyS0\n".replace("\\n","\n"))
            withTimeout(10000) {while(!serial.readText().contains("osify-pip-restored")) delay(200)}
            compose.onNodeWithTag("console-back").performClick()
            compose.onNodeWithTag("console-stop").performClick()
            withTimeout(15000) {while(dao.virtualMachine(vm.id)?.state!="STOPPED") delay(200)}
            assertEquals("STOPPED",dao.virtualMachine(vm.id)?.state)
            File(app.filesDir,"visuals/console-serial.txt").writeText(serial.readText())
        } catch(e: Throwable) {
            runCatching {File(app.filesDir,"visuals/console-serial.txt").writeText(File(app.filesDir,"vms/${vm.id}/serial.log").readText())}
            runCatching {shot(app,"console-failure")}
            throw e
        } finally {
            scenario?.close()
            runCatching {app.vmEngine.control(dao.virtualMachine(vm.id)!!,"force")}
            withTimeoutOrNull(15000) {while(dao.virtualMachine(vm.id)?.state!="STOPPED") delay(200)}
            app.preferences.string("theme","dark");app.preferences.string("motion","full")
            dao.virtualMachine(vm.copy(state="STOPPED"))
            if(InstrumentationRegistry.getArguments().getString("keepFixture")!="true") {app.vmEngine.remove(vm);dao.deleteImage(image.id);file.delete()}
        }
    }
}
