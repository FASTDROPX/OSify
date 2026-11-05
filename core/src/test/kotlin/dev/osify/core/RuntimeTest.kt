package dev.osify.core

import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.*
import java.net.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*

class RuntimeTest {
    @Test fun loopbackBridgeServesOnlyItsCapability() {
        val directory=kotlin.io.path.createTempDirectory("osify-bridge").toFile()
        try {
            val file=File(directory,"iso").apply {writeBytes(ByteArray(65536) {(it%241).toByte()})}
            LocalImageBridge().use {bridge->
                val url=bridge.publish(file)
                assertEquals("127.0.0.1",URI(url).host)
                assertContentEquals(file.readBytes(),URL(url).openStream().use {it.readBytes()})
                val bad=URL(url.substringBeforeLast('/')+"/../etc/passwd").openConnection() as HttpURLConnection
                assertEquals(404,bad.responseCode)
                bad.disconnect()
            }
        } finally {directory.deleteRecursively()}
    }
    @Test fun vmCommandsKeepHostileValuesOutOfShellAndValidateBounds() {
        val script=VmCommands.launch("my-vm","http://127.0.0.1:12345/abcdef","0".repeat(64),1024,2,32,5911,"Abc12345")
        assertTrue(script.contains("-accel tcg,thread=multi"))
        assertTrue(script.contains("password-secret=vncpass"))
        assertTrue(script.contains("sha256sum --check"))
        assertEquals("'a'\"'\"'b'",VmCommands.quote("a'b"))
        assertFails {VmCommands.launch("../bad","http://127.0.0.1/x","0".repeat(64),1024,2,32,5911,"Abc12345")}
        assertFails {VmCommands.launch("vm","https://example.com/x","0".repeat(64),1024,2,32,5911,"Abc12345")}
        assertFails {VmCommands.control("vm","; rm -rf anything")}
        assertFails {VmCommands.quote("bad\u0000value")}
    }
    @Test fun generatedCommandsLaunchAndControlRealQemu() {
        assumeTrue("Requires explicit host toolchain and guest ISO",System.getenv("OSIFY_QEMU_SCRIPT_TEST")=="1")
        val tools=File(System.getenv("OSIFY_QEMU_ROOT"))
        val bios=File(System.getenv("OSIFY_QEMU_BIOS_DIR"))
        val firmware=File(System.getenv("OSIFY_QEMU_EFI_DIR"))
        val iso=File(System.getenv("OSIFY_QEMU_ISO"))
        val root=kotlin.io.path.createTempDirectory("osify-real-commands").toFile()
        val bin=File(root,"bin").apply {mkdirs()}
        File(root,"share").mkdirs()
        File(root,"share/qemu").mkdirs()
        listOf("edk2-x86_64-code.fd","edk2-i386-vars.fd").forEach {File(firmware,it).copyTo(File(root,"share/qemu/$it"))}
        File(bin,"qemu-system-x86_64").apply {writeText("#!/bin/bash\nexec "+VmCommands.quote(File(tools,"usr/bin/qemu-system-x86_64").path)+" -L "+VmCommands.quote(bios.path)+" \"\$@\"\n");setExecutable(true)}
        listOf("qemu-img","socat","swtpm").forEach {name->java.nio.file.Files.createSymbolicLink(File(bin,name).toPath(),File(tools,"usr/bin/$name").toPath())}
        fun execute(script: String): String {
            val process=ProcessBuilder("bash","-c",script).redirectErrorStream(true).apply {environment().apply {
                put("PREFIX",root.path);put("PATH",bin.path+":"+get("PATH"));put("LD_LIBRARY_PATH",File(tools,"usr/lib/x86_64-linux-gnu").path+":"+File(tools,"usr/lib/x86_64-linux-gnu/swtpm").path)
            }}.start()
            val log=process.inputStream.bufferedReader().readText()
            assertTrue(process.waitFor(30,TimeUnit.SECONDS),log)
            assertEquals(0,process.exitValue(),log)
            return log
        }
        val evidence=StringBuilder()
        try {
            val sha=java.security.MessageDigest.getInstance("SHA-256").let {digest->iso.inputStream().use {input->val buffer=ByteArray(65536);while(true) {val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};digest.digest().joinToString("") {"%02x".format(it)}}
            LocalImageBridge().use {bridge->
                for(mode in listOf("BIOS","UEFI")) {
                    val id="verified-${mode.lowercase()}"
                    val port=ServerSocket(0).use {it.localPort}
                    evidence.appendLine("$mode launch: "+execute(VmCommands.launch(id,bridge.publish(iso),sha,256,1,2,port,"Osify123",mode)))
                    for(action in listOf("pause","resume","force")) evidence.appendLine("$mode $action: "+execute(VmCommands.control(id,action)))
                    evidence.appendLine("$mode disk-only launch: "+execute(VmCommands.launch(id,"http://127.0.0.1:1/unused",sha,256,1,2,port,"Osify123",mode,false)))
                    execute(VmCommands.control(id,"force"))
                }
            }
        } finally {
            File("build/evidence/generated-qemu-commands.log").apply {parentFile.mkdirs();writeText(evidence.toString())}
            root.deleteRecursively()
        }
    }
    @Test fun genuineQemuFramebufferIntegration() {
        assumeTrue("Enable only with a real, password-protected local QEMU",System.getenv("OSIFY_QEMU_TEST")=="1")
        val latch=CountDownLatch(1);val lock=Any();var bitmap: BufferedImage?=null;var failure: String?=null
        val windows=System.getenv("OSIFY_WINDOWS_TEST")=="1"
        val client=RfbClient(if(windows) 5912 else 5911,if(windows) "OsifyWin" else "OsifyVnc",object: RfbClient.Listener {
            override fun resize(width: Int,height: Int) {synchronized(lock) {bitmap=BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB)}}
            override fun pixels(x: Int,y: Int,width: Int,height: Int,argb: IntArray) {synchronized(lock) {bitmap!!.setRGB(x,y,width,height,argb,0,width)};latch.countDown()}
            override fun status(message: String) {if(message!="Framebuffer connected") failure=message}
        })
        client.start()
        try {
            assertTrue(latch.await(15,TimeUnit.SECONDS),failure?:"No framebuffer received")
            if(windows) {
                repeat(3) {client.key(0xff0d,true);client.key(0xff0d,false);Thread.sleep(500)}
                Thread.sleep(5000)
            } else {
                client.key(0xffe3,true);client.key(0xffe9,true);client.key(0xffbe,true);client.key(0xffbe,false);client.key(0xffe9,false);client.key(0xffe3,false)
                client.text("\n")
                Thread.sleep(300)
                client.text("uname -a\n")
                Thread.sleep(1000)
            }
            synchronized(lock) {
                val frame=bitmap!!
                assertTrue(frame.width>100 && frame.height>100)
                val file=File(if(windows) "build/evidence/qemu-windows-framebuffer.png" else "build/evidence/qemu-linux-framebuffer.png");file.parentFile.mkdirs();ImageIO.write(frame,"PNG",file)
            }
            assertNull(failure)
        } finally {client.close()}
    }
}
