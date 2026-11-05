package dev.osify.app

import androidx.test.core.app.ApplicationProvider
import dev.osify.app.data.*
import dev.osify.app.engine.*
import dev.osify.core.VmHardware
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=TestOsifyApplication::class)
class GuestToolsSafetyTest {
    @Test fun cloneCopiesDiskVarsAndConfigWithIndependentSecretsAndCancellationCleanup()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<TestOsifyApplication>();app.startupRecovery.join()
        val dao=app.database.dao()
        val h=VmHardware(architecture="X86_64",network="e1000",mac="52:54:00:12:34:56")
        val vm=VirtualMachine("clone-source","Source","WINDOWS",password="original",firmware="UEFI",hardwareJson=VmHardware.encode(h))
        dao.virtualMachine(vm)
        val dir=VmLogs.folder(app,vm.id)
        val disk=File(dir,"disk.raw");RandomAccessFile(disk,"rw").use {it.setLength(3L*1048576);it.write(byteArrayOf(1,2,3));it.seek(2L*1048576);it.write(7)}
        File(dir,"OVMF_VARS.fd").writeText("persistent vars")
        File(dir,"exchange").mkdirs();File(dir,"exchange/bridge.json").writeText("private capability")
        val before=dao.virtualMachines().first().map {it.id}.toSet()
        app.vmEngine.clone(vm.id)
        val copy=dao.virtualMachines().first().single {it.id !in before}
        val destination=VmLogs.folder(app,copy.id)
        assertContentEquals(disk.readBytes(),File(destination,"disk.raw").readBytes())
        assertEquals("persistent vars",File(destination,"OVMF_VARS.fd").readText())
        assertFalse(File(destination,"exchange/bridge.json").exists())
        assertNotEquals(vm.password,copy.password);assertNotEquals(vm.id,copy.id)
        assertEquals(h.copy(mac=""),VmHardware.decode(copy.hardwareJson))
        File(destination,"disk.raw").writeBytes(byteArrayOf(9));assertEquals(3L*1048576,disk.length())
        val folders=File(app.filesDir,"vms").listFiles()!!.map {it.name}.toSet()
        assertFailsWith<CancellationException> {app.vmEngine.clone(vm.id) {throw CancellationException("cancelled")}}
        assertEquals(folders,File(app.filesDir,"vms").listFiles()!!.map {it.name}.toSet())
        assertEquals(before+copy.id,dao.virtualMachines().first().map {it.id}.toSet())
        dao.virtualMachine(vm.copy(state="DISPLAY_AVAILABLE"))
        assertFailsWith<IllegalStateException> {app.vmEngine.clone(vm.id)}
        assertEquals(1,disk.readBytes()[0].toInt())
    }
    @Test fun installedDiskBootDoesNotRequireDeletedInstallerAndAmbiguityIsRejected()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<TestOsifyApplication>();app.startupRecovery.join()
        val vm=VirtualMachine("disk-without-iso","Installed XP","WINDOWS",imageId="removed",hardwareJson=VmHardware.encode(VmHardware(architecture="X86",boot="disk",cpu="qemu32")))
        val dir=VmLogs.folder(app,vm.id)
        val disk=File(dir,"disk.raw").apply {writeBytes(ByteArray(4096).also {it[0]=0xEB.toByte();it[510]=0x55;it[511]=0xAA.toByte()})}
        val boot=resolveBoot(app,vm,true)
        assertNull(boot.iso);assertEquals(disk,boot.disk);assertEquals("X86",boot.architecture)
        File(dir,"import.img").writeBytes(disk.readBytes())
        assertFailsWith<IllegalStateException> {resolveBoot(app,vm,false)}
        File(dir,"import.img").delete();disk.writeBytes(ByteArray(4096))
        assertFailsWith<IllegalArgumentException> {resolveBoot(app,vm,false)}
        Unit
    }
    @Test fun favoritesPersistAndDisplayOrientationKeepsBoundedActualDesiredDimensions()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<TestOsifyApplication>()
        app.preferences.favorite("windows-xp")
        assertTrue("windows-xp" in Preferences(app).flow.first().favorites)
        app.preferences.favorite("windows-xp")
        assertFalse("windows-xp" in app.preferences.flow.first().favorites)
        app.guestTools.desiredDisplay("orientation-test",720,1400)
        val portrait=app.guestTools.display("orientation-test");assertTrue(portrait.getInt("height")>portrait.getInt("width"))
        app.guestTools.desiredDisplay("orientation-test",1600,720)
        val landscape=app.guestTools.display("orientation-test");assertTrue(landscape.getInt("width")>landscape.getInt("height"))
        app.guestTools.desiredDisplay("orientation-test",1600,720,false)
        assertFalse(app.guestTools.display("orientation-test").getBoolean("enabled"))
    }
}
