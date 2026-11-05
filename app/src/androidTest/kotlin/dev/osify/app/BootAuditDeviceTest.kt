package dev.osify.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.osify.app.data.*
import dev.osify.app.engine.*
import dev.osify.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.InetAddress
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class BootAuditDeviceTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OsifyApplication
    private suspend fun start(vm: VirtualMachine): RfbClient {
        app.startActivity(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));delay(1500)
        app.vmEngine.launch(vm,true)
        withTimeout(75000) {while(app.database.dao().virtualMachine(vm.id)?.state=="REQUESTED")delay(200)}
        val row=app.database.dao().virtualMachine(vm.id)!!
        assertEquals(row.log,"DISPLAY_AVAILABLE",row.state)
        return RfbClient(vm.port,vm.password,object:RfbClient.Listener {
            override fun resize(width:Int,height:Int) {synchronized(this@BootAuditDeviceTest) {bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)}}
            override fun pixels(x:Int,y:Int,width:Int,height:Int,argb:IntArray) {synchronized(this@BootAuditDeviceTest) {bitmap?.setPixels(argb,0,width,x,y,width,height)}}
            override fun status(message:String) {android.util.Log.i("BootAudit",message)}
        }).also {it.start()}
    }
    private var bitmap:Bitmap?=null
    private fun capture(name:String) {synchronized(this) {val file=File(app.filesDir,"visuals/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {assertNotNull(bitmap);bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    private fun type(client:RfbClient,text:String) {client.text(text);Thread.sleep(250)}
    private suspend fun stop(vm:VirtualMachine) {
        app.database.dao().virtualMachine(vm.id)?.let {row->if(row.state in setOf("REQUESTED","DISPLAY_AVAILABLE","PAUSED","SHUTDOWN_REQUESTED"))app.vmEngine.control(row,"force")}
        withTimeout(15000) {while(app.database.dao().virtualMachine(vm.id)?.state !in setOf("STOPPED","FAILED"))delay(200)};delay(1000)
    }
    private suspend fun fixture(id:String,title:String,fileName:String,arch:String,guest:String,h:VmHardware):VirtualMachine {
        app.startupRecovery.join()
        val source=File(app.getExternalFilesDir(null),fileName);check(source.isFile)
        val target=File(app.filesDir,"images/$id.image");target.parentFile!!.mkdirs();source.copyTo(target,true)
        app.database.dao().putImage(ImageRecord(id,title,"audit",arch,"audit","",target.length(),"",bytes=target.length(),state="COMPLETE",imported=true,fileName=target.name))
        val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
        val uefi=InstrumentationRegistry.getArguments().getString("uefi")=="true" && guest=="LINUX"
        return VirtualMachine(id,title,guest,id,512,1,2,port,"Audit123",firmware=if(uefi) "UEFI" else "BIOS",hardwareJson=VmHardware.encode(if(uefi)h.copy(machine="q35",cpu="Nehalem",tsc="on") else h)).also {app.database.dao().virtualMachine(it)}
    }
    @Test fun actualXpInstallerAndIndependentClone()=runBlocking {
        val vm=fixture("audit-xp","Windows XP audit","xp-test.iso","X86","WINDOWS",BootPlan.defaults("Windows XP","X86"))
        var client:RfbClient?=null
        try {
            client=start(vm)
            delay(12000);capture("audit-xp-12s")
            delay(30000);capture("audit-xp-42s")
            delay(30000);capture("audit-xp-72s")
            val blocks=withContext(Dispatchers.IO) {QemuControl(File(app.filesDir,"qemu-qmp.sock")).use {it.execute("query-block")}}
            File(app.filesDir,"visuals/audit-xp-blocks.json").writeText(blocks.toString(2))
            assertTrue(blocks.toString().contains("osify-install"));assertTrue(blocks.toString().contains("osify-share"));assertTrue(blocks.toString().contains("osify-drivers"))
        } finally {client?.close();stop(vm)}
    }
    @Test fun actualAlpineExchangeMountAndNatReturn()=runBlocking {
        val vm=fixture("audit-linux","Alpine exchange audit","boot-test.iso","X86_64","LINUX",VmHardware(architecture="X86_64"))
        File(app.guestTools.inbox(vm.id),"hot-update.txt").delete()
        app.guestTools.outbox(vm.id).listFiles().orEmpty().forEach {it.delete()}
        File(app.guestTools.inbox(vm.id),"test-unicode.txt").writeText("OSify phone to guest ✓")
        var client:RfbClient?=null
        try {
            client=start(vm)
            val serial=File(VmLogs.folder(app,vm.id),"serial.log")
            withTimeout(100000) {while(!serial.isFile || !serial.readText().contains("localhost login:"))delay(500)}
            type(client,"root\n");delay(1500)
            capture("audit-linux-login")
            type(client,"echo osify-command-ready > /dev/ttyS0\n")
            withTimeout(10000) {while(!serial.readText().contains("osify-command-ready"))delay(200)}
            // Probe all CD slots rather than assume Linux's optical enumeration order.
            type(client,"mkdir -p /mnt/phone; for d in /dev/sr0 /dev/sr1 /dev/sr2; do mount \$d /mnt/phone; test -f /mnt/phone/test-unicode.txt && break; umount /mnt/phone; done; cat /mnt/phone/test-unicode.txt > /dev/ttyS0\n");delay(1800)
            withTimeout(30000) {while(!serial.readText().contains("OSify phone to guest"))delay(200)}
            type(client,"ip link set eth0 up; udhcpc -i eth0 -q -n -t 3 -T 1 > /dev/ttyS0 2>&1; echo guest-return > /tmp/return.txt\n");delay(4000)
            withTimeout(30000) {while(!serial.readText().contains("lease of 10.0.2.15"))delay(200)}
            val url=app.guestTools.url(vm.id)
            type(client,"wget -O- --post-file=/tmp/return.txt --header='X-Filename: return.txt' '${url}upload' > /dev/ttyS0 2>&1\n");delay(2500)
            withTimeout(30000) {while(app.guestTools.outbox(vm.id).listFiles().orEmpty().none {it.readText()=="guest-return\n"})delay(200)}
            assertTrue(serial.readText().contains("OSify phone to guest"))
            File(app.guestTools.inbox(vm.id),"hot-update.txt").writeText("hot mount success")
            val updated=app.guestTools.rebuild(vm.id);app.vmEngine.changeMedium(vm.id,"osify-share",updated)
            type(client,"umount /mnt/phone; mount \$d /mnt/phone; cat /mnt/phone/hot-update.txt > /dev/ttyS0\n");delay(1800)
            withTimeout(20000) {while(!serial.readText().contains("hot mount success"))delay(200)}
            val driverCopy=File(app.filesDir,"images/audit-driver.image");updated.copyTo(driverCopy,true)
            app.database.dao().putImage(ImageRecord("audit-driver","Driver test.iso","test","UNKNOWN","windows-drivers","",driverCopy.length(),"",bytes=driverCopy.length(),state="COMPLETE",imported=true,fileName=driverCopy.name))
            app.guestTools.selectDriver(app.database.dao().virtualMachine(vm.id)!!,"audit-driver")
            val blocks=withContext(Dispatchers.IO) {QemuControl(File(app.filesDir,"qemu-qmp.sock")).use {it.execute("query-block")}}
            assertTrue(blocks.toString().contains("audit-driver.image"))
            app.guestTools.selectDriver(app.database.dao().virtualMachine(vm.id)!!,"")
            app.database.dao().deleteImage("audit-driver");driverCopy.delete()
            capture("audit-linux-exchange")
            File(app.filesDir,"visuals/audit-linux-serial.txt").writeText(serial.readText())
        } finally {runCatching {capture("audit-linux-final")};client?.close();stop(vm)}
    }
    @Test fun cleanupOwnAuditFixtures()=runBlocking {
        app.startupRecovery.join();val dao=app.database.dao()
        for(id in listOf("audit-xp","audit-linux")) {
            val row=dao.virtualMachine(id)
            if(row!=null) {check(row.name in setOf("Windows XP audit","Alpine exchange audit") && row.state=="STOPPED");app.vmEngine.remove(row)}
            dao.image(id)?.let {image->check(image.flavor=="audit");dao.deleteImage(id);File(app.filesDir,"images/$id.image").delete()}
        }
        dao.image("audit-driver")?.let {dao.deleteImage(it.id);File(app.filesDir,"images/audit-driver.image").delete()}
        File(app.getExternalFilesDir(null),"xp-test.iso").takeIf {it.length()==617754624L}?.delete()
        Unit
    }
}
