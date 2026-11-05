package dev.osify.core

import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.*

class BootAndExchangeTest {
    @Test fun requiredGuestConfigurationMatrixUsesSupportedArchitectureAndBootDevices() {
        for((name,arch,firmware,guest) in listOf(
            listOf("Windows XP","X86","BIOS","WINDOWS"),listOf("Windows 7","X86_64","BIOS","WINDOWS"),
            listOf("Windows 10","X86_64","UEFI","WINDOWS"),listOf("Windows 11","X86_64","UEFI","WINDOWS"),
            listOf("Linux","X86_64","BIOS","LINUX"))) {
            val h=BootPlan.defaults(name,arch);BootPlan.validate(h,firmware,arch,null)
            val a=h.arguments(guest,firmware,"disk.raw","raw","install.iso","serial")
            assertTrue(a.any {it.contains("osify-install,bootindex=1")});assertTrue(a.any {it.contains("osify-disk,bootindex=2")})
            assertFalse(a.any {it.contains("order=") || it.contains("once=")})
            if(name=="Windows 11")assertEquals("Nehalem",h.cpu)
        }
        assertFails {BootPlan.architecture(VmHardware(),"ARM64",null)}
    }
    private fun fixture(efi: Boolean,cpu: Int): File {
        val bytes=ByteArray(32*2048)
        fun le(at: Int,n: Int) {ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(at,n)}
        fun desc(sector: Int,type: Int) {bytes[sector*2048]=type.toByte();"CD001".toByteArray().copyInto(bytes,sector*2048+1);bytes[sector*2048+6]=1}
        desc(16,0);"EL TORITO SPECIFICATION".toByteArray().copyInto(bytes,16*2048+7);le(16*2048+71,20)
        desc(17,1);desc(18,255)
        fun record(at: Int,name: ByteArray,extent: Int,size: Int,dir: Boolean): Int {
            val n=33+name.size+(if(name.size%2==0)1 else 0);bytes[at]=n.toByte();le(at+2,extent);le(at+10,size);bytes[at+25]=if(dir)2 else 0;bytes[at+32]=name.size.toByte();name.copyInto(bytes,at+33);return n
        }
        record(17*2048+156,byteArrayOf(0),21,2048,true)
        val name=when(cpu) {0xaa64->"BOOTAA64.EFI;1";0x8664->"BOOTX64.EFI;1";else->"BOOTIA32.EFI;1"}
        record(21*2048,name.toByteArray(),22,512,false)
        val m=22*2048;bytes[m]=77;bytes[m+1]=90;le(m+60,64);bytes[m+64]=80;bytes[m+65]=69;bytes[m+68]=(cpu and 255).toByte();bytes[m+69]=(cpu ushr 8).toByte()
        val c=20*2048;bytes[c]=1;bytes[c+1]=if(efi)0xef.toByte() else 0;bytes[c+30]=0x55;bytes[c+31]=0xaa.toByte()
        val sum=(0 until 32 step 2).sumOf {(bytes[c+it].toInt() and 255) or ((bytes[c+it+1].toInt() and 255) shl 8)}
        val negative=(-sum) and 65535;bytes[c+28]=negative.toByte();bytes[c+29]=(negative ushr 8).toByte()
        bytes[c+32]=0x88.toByte();le(c+40,22)
        return File.createTempFile("osify-fixture-",".iso").also {it.writeBytes(bytes);it.deleteOnExit()}
    }
    @Test fun realUserXpReadOnlyProbe() {
        val path=System.getenv("OSIFY_TEST_XP_ISO");assumeTrue(path!=null)
        val file=File(path!!);assumeTrue(file.isFile)
        val before=file.lastModified();val media=BootMediaProbe.inspect(file)
        println("REAL_XP_PROBE=$media")
        assertEquals("iso",media.kind);assertTrue(media.bios);assertEquals("X86",media.architecture)
        assertEquals(617754624L,file.length());assertEquals(before,file.lastModified())
    }
    @Test fun rejectsArm64OnlyEvenWhenCatalogSaysX64AndChecksFirmware() {
        val arm=BootMediaProbe.inspect(fixture(true,0xaa64))
        assertEquals(setOf("ARM64"),arm.architectures);assertTrue(arm.efi);assertFalse(arm.bios)
        assertFailsWith<IllegalArgumentException> {BootPlan.architecture(VmHardware(),"X86_64",arm)}
        val x86=BootMediaProbe.inspect(fixture(false,0x14c));assertEquals("X86",x86.architecture)
        assertEquals("X86",BootPlan.architecture(VmHardware(),"X86_64",x86))
        assertFails {BootPlan.validate(VmHardware(),"UEFI","X86",x86)}
        val x64=BootMediaProbe.inspect(fixture(true,0x8664));BootPlan.validate(VmHardware(),"UEFI","X86_64",x64)
        assertFails {BootPlan.validate(VmHardware(),"BIOS","X86_64",x64)}
        assertFails {BootPlan.architecture(VmHardware(),"UNKNOWN",null)}
    }
    @Test fun rejectsTruncatedStructuresAndInvalidQcowAndPreservesXpDevices() {
        val iso=fixture(false,0x14c);java.io.RandomAccessFile(iso,"rw").use {it.setLength(21*2048)}
        assertFails {BootMediaProbe.inspect(iso)}
        val disk=File.createTempFile("bad-qcow-",".qcow2").apply {writeBytes(byteArrayOf(81,70,73,-5)+ByteArray(68));deleteOnExit()}
        assertFails {BootMediaProbe.inspect(disk)}
        val h=BootPlan.defaults("Windows XP","X86");assertEquals("pc",h.machine);assertFalse(h.usb);assertEquals("cirrus",h.vga)
        val a=h.arguments("WINDOWS","BIOS","disk.raw","raw","xp.iso","serial")
        assertTrue("qemu32,+tsc" in a);assertFalse(a.any {it.contains("virtio")});assertFalse(a.any {it.contains("order=")})
    }
    @Test fun actualHttpMultipartRoundtripAndCapabilityIsolation() {
        val root=kotlin.io.path.createTempDirectory("guest-tools").toFile()
        GuestBridge(root,"a".repeat(32)).use {bridge->
            val base="http://127.0.0.1:${bridge.port}/";val key="a".repeat(32)
            val wrong=URL(base+"wrong/").openConnection() as HttpURLConnection;assertEquals(404,wrong.responseCode);wrong.disconnect()
            val file=File(root,"inbox/test.txt").apply {writeText("phone-to-guest")}
            assertEquals(file.readText(),URL(base+"$key/file/test.txt").readText())
            val boundary="OSify-boundary";val payload=ByteArray(150000) {(it%251).toByte()}
            val body=("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../../return.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray()+payload+("\r\n--$boundary--\r\n").toByteArray()
            val post=URL(base+"$key/upload").openConnection() as HttpURLConnection;post.requestMethod="POST";post.doOutput=true;post.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary");post.setFixedLengthStreamingMode(body.size);post.outputStream.use {it.write(body)}
            assertEquals(200,post.responseCode);post.disconnect()
            val received=File(root,"outbox").listFiles()!!.single();assertContentEquals(payload,received.readBytes());assertEquals(File(root,"outbox").canonicalFile,received.canonicalFile.parentFile)
        }
        root.deleteRecursively()
    }
    @Test fun independentIsoReaderArtifactAndNoUpscaleOfData() {
        val root=kotlin.io.path.createTempDirectory("joliet").toFile()
        val file=File(root,"Тест файла.txt").apply {writeText("unicode payload\n")}
        val iso=File(root,"phone.iso");ExchangeIso.write(listOf(file),iso)
        assertEquals("iso",BootMediaProbe.inspect(iso).kind)
        System.getenv("OSIFY_TEST_ARTIFACTS")?.let {dir->File(dir).mkdirs();iso.copyTo(File(dir,"unicode-phone.iso"),overwrite=true)}
        root.deleteRecursively()
    }
}
