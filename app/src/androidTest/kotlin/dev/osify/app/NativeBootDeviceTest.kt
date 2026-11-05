package dev.osify.app

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.osify.app.data.ImageRecord
import dev.osify.app.data.VirtualMachine
import dev.osify.core.RfbClient
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class NativeBootDeviceTest {
    @Test fun bootAlpineWithoutTermuxAndRestart()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OsifyApplication
        app.startupRecovery.join()
        val fixture=File(app.getExternalFilesDir(null),"boot-test.iso")
        check(fixture.isFile) {"Push the official Alpine virt ISO to external files/boot-test.iso"}
        val dir=File(app.filesDir,"images").also {it.mkdirs()}
        val file=File(dir,"native-boot.image");fixture.copyTo(file,overwrite=true)
        val dao=app.database.dao()
        val image=ImageRecord("native-boot","Alpine native boot.iso","3.24.3","X86_64","alpine-virt","",file.length(),"cdc1bc0efc64d8d611bf3733f54868172a545888dfd726f518bf279bcb0ff1af",bytes=file.length(),state="COMPLETE",imported=true,fileName=file.name)
        dao.putImage(image)
        val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
        val machine=VirtualMachine("native-boot-test","Alpine native boot","LINUX",image.id,512,1,2,port,"Test1234")
        dao.virtualMachine(machine)
        var client: RfbClient?=null
        try {
            app.vmEngine.launch(machine,true)
            withTimeout(75000) {while(dao.virtualMachine(machine.id)?.state=="REQUESTED") delay(200)}
            assertEquals(dao.virtualMachine(machine.id)?.log,"DISPLAY_AVAILABLE",dao.virtualMachine(machine.id)?.state)
            val monitor=Any();var bitmap: Bitmap?=null;var pixels=0L
            client=RfbClient(port,machine.password,object: RfbClient.Listener {
                override fun resize(width: Int,height: Int) {synchronized(monitor) {bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)}}
                override fun pixels(x: Int,y: Int,width: Int,height: Int,argb: IntArray) {synchronized(monitor) {bitmap?.setPixels(argb,0,width,x,y,width,height);pixels+=argb.size}}
                override fun status(message: String) {android.util.Log.i("NativeBootTest",message)}
            }).also {it.start()}
            withTimeout(20000) {while(pixels==0L) delay(200)}
            val serial=File(app.filesDir,"vms/${machine.id}/serial.log")
            withTimeout(90000) {while(!serial.exists() || !serial.readText().contains("localhost login:")) delay(500)}
            assertFalse(serial.readText().contains("Kernel panic"))
            File(app.filesDir,"visuals/native-alpine-serial.txt").also {it.parentFile!!.mkdirs();it.writeText(serial.readText())}
            fun type(text: String) {text.forEach {char->val key=if(char=='\n') 0xff0d else char.code;client!!.key(key,true);client!!.key(key,false);Thread.sleep(60)}}
            type("root\n");delay(1200);type("uname -a\n");delay(1000)
            val output=File(app.filesDir,"visuals/native-alpine-boot.png").also {it.parentFile!!.mkdirs()}
            synchronized(monitor) {output.outputStream().use {bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it)}}
            assertTrue(pixels>640*480)
            app.vmEngine.control(dao.virtualMachine(machine.id)!!,"pause")
            assertEquals("PAUSED",dao.virtualMachine(machine.id)?.state)
            app.vmEngine.control(dao.virtualMachine(machine.id)!!,"resume")
            assertEquals("DISPLAY_AVAILABLE",dao.virtualMachine(machine.id)?.state)
            client.close();client=null
            app.vmEngine.control(dao.virtualMachine(machine.id)!!,"force")
            withTimeout(15000) {while(dao.virtualMachine(machine.id)?.state!="STOPPED") delay(200)}
            delay(1000)
            val uefi=dao.virtualMachine(machine.id)!!.copy(firmware="UEFI")
            dao.virtualMachine(uefi)
            app.vmEngine.launch(uefi,true)
            withTimeout(75000) {while(dao.virtualMachine(machine.id)?.state=="REQUESTED") delay(200)}
            assertEquals(dao.virtualMachine(machine.id)?.log,"DISPLAY_AVAILABLE",dao.virtualMachine(machine.id)?.state)
            pixels=0
            client=RfbClient(port,machine.password,object: RfbClient.Listener {
                override fun resize(width: Int,height: Int) {synchronized(monitor) {bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)}}
                override fun pixels(x: Int,y: Int,width: Int,height: Int,argb: IntArray) {synchronized(monitor) {bitmap?.setPixels(argb,0,width,x,y,width,height);pixels+=argb.size}}
                override fun status(message: String) {android.util.Log.i("NativeBootTest",message)}
            }).also {it.start()}
            withTimeout(20000) {while(pixels==0L) delay(200)}
            withTimeout(120000) {while(!serial.exists() || !serial.readText().contains("localhost login:")) delay(500)}
            synchronized(monitor) {File(app.filesDir,"visuals/native-uefi-boot.png").outputStream().use {bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it)}}
            File(app.filesDir,"visuals/native-uefi-serial.txt").writeText(serial.readText())
        } finally {
            client?.close()
            runCatching {app.vmEngine.control(dao.virtualMachine(machine.id)!!,"force")}
            withTimeoutOrNull(10000) {while(dao.virtualMachine(machine.id)?.state!="STOPPED") delay(200)}
            dao.virtualMachine(machine.copy(state="STOPPED"))
            if(InstrumentationRegistry.getArguments().getString("keepFixture")!="true") {app.vmEngine.remove(machine);dao.deleteImage(image.id);file.delete()}
        }
    }
}
