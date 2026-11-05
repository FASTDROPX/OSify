package dev.osify.app

import androidx.test.core.app.ApplicationProvider
import dev.osify.app.data.VirtualMachine
import dev.osify.app.engine.VmLogs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=TestOsifyApplication::class)
class VmConfigurationAndLogsTest {
    @Test fun editPreservesGuestDiskAndIdentityAndRejectsRunningMachine()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<TestOsifyApplication>()
        app.startupRecovery.join()
        val dao=app.database.dao()
        val vm=VirtualMachine("edit-test","Arch Linux","LINUX",imageId="original-image",ramMiB=1024,cores=3,diskGiB=32,password="vnc-secret")
        dao.virtualMachine(vm)
        val disk=File(VmLogs.folder(app,vm.id),"disk.raw").also {it.writeBytes(byteArrayOf(1,3,7,11))}
        app.vmEngine.configure(vm.id,"Arch desktop",256,2,"UEFI")
        val saved=dao.virtualMachine(vm.id)!!
        assertEquals(vm.copy(name="Arch desktop",ramMiB=256,cores=2,firmware="UEFI"),saved)
        assertContentEquals(byteArrayOf(1,3,7,11),disk.readBytes())
        val hardware=dev.osify.core.VmHardware(network="e1000",tcg="multi",boot="disk")
        app.vmEngine.configure(vm.id,saved.name,256,2,"UEFI",hardware)
        val advanced=dao.virtualMachine(vm.id)!!
        assertEquals(hardware,dev.osify.core.VmHardware.decode(advanced.hardwareJson))
        assertEquals(vm.imageId,advanced.imageId);assertEquals(vm.password,advanced.password)
        assertContentEquals(byteArrayOf(1,3,7,11),disk.readBytes())
        dao.virtualMachine(advanced.copy(state="DISPLAY_AVAILABLE",token="active"))
        assertFailsWith<IllegalStateException> {app.vmEngine.configure(vm.id,"Bad edit",256,1,"BIOS")}
        assertEquals("Arch desktop",dao.virtualMachine(vm.id)!!.name)
        assertContentEquals(byteArrayOf(1,3,7,11),disk.readBytes())
    }
    @Test fun reportRedactsSecretsAndExportsOnlyScopedTxt() {
        val app=ApplicationProvider.getApplicationContext<TestOsifyApplication>()
        val vm=VirtualMachine("log-test","Arch Linux","LINUX",password="private-secret",token="operation-secret",log="password=other-secret token: other-token")
        File(VmLogs.folder(app,vm.id),"serial.log").writeText("guest error\nprivate-secret operation-secret ${app.filesDir}/disk.raw http://10.0.2.2:12345/${"b".repeat(32)}/upload")
        val report=VmLogs.report(app,vm)
        listOf("private-secret","operation-secret","other-secret","other-token",app.filesDir.absolutePath).forEach {assertFalse(report.contains(it),it)}
        assertTrue(report.contains("guest error"));assertTrue(report.contains("RAM=1024"))
        assertFalse(report.contains("b".repeat(32)))
        val file=VmLogs.snapshot(app,report)
        // Android FileProvider is verified with the real ContentResolver in GuestConsoleDeviceTest.
        assertEquals(report,file.readText())
        assertEquals("vm-log-exports",file.parentFile!!.name)
    }
    @Test fun logPathsRejectTraversalAndPreviousSessionIsBounded() {
        val app=ApplicationProvider.getApplicationContext<TestOsifyApplication>()
        listOf("../outside","/absolute","id/child","..","a\\b").forEach {id->assertFailsWith<IllegalArgumentException> {VmLogs.folder(app,id)}}
        val dir=VmLogs.folder(app,"rotation-test")
        File(dir,"serial.log").writeText("a".repeat(VmLogs.LIMIT+1000)+"END")
        VmLogs.prepare(app,"rotation-test")
        val previous=File(dir,"serial.previous.log")
        assertTrue(previous.length()<VmLogs.LIMIT+100);assertTrue(previous.readText().endsWith("END"))
        assertFalse(File(dir,"serial.log").exists())
        assertTrue(File(dir,"runtime.log").readText().contains("New QEMU session"))
    }
}
