package dev.osify.core

import java.io.*
import java.net.*
import java.util.concurrent.*
import org.junit.Test
import kotlin.test.*

/** Validates actual bytes written over an authenticated RFB connection. */
class RfbInputTest {
    @Test fun multilineUnicodePasteIsOrderedAndReleasesFlushBeforeClose() {
        val events=CopyOnWriteArrayList<Pair<Int,Boolean>>()
        val pointer=CopyOnWriteArrayList<Int>()
        val connected=CountDownLatch(1);val finished=CountDownLatch(1)
        val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))
        var problem: Throwable?=null
        val thread=Thread {
            try {server.accept().use {socket->
                socket.soTimeout=10000
                val input=DataInputStream(socket.getInputStream());val out=DataOutputStream(socket.getOutputStream())
                out.write("RFB 003.008\n".toByteArray());out.flush();input.readFully(ByteArray(12))
                out.write(byteArrayOf(1,2));out.flush();assertEquals(2,input.readUnsignedByte())
                val challenge=ByteArray(16) {it.toByte()};out.write(challenge);out.flush()
                val answer=ByteArray(16);input.readFully(answer);assertContentEquals(RfbClient.authenticate("Test1234",challenge),answer)
                out.writeInt(0);out.flush();input.readByte()
                out.writeShort(640);out.writeShort(480);out.write(ByteArray(16));out.writeInt(0);out.flush()
                while(true) {
                    when(input.readUnsignedByte()) {
                        0->input.readFully(ByteArray(19))
                        2->{input.readByte();val count=input.readUnsignedShort();input.readFully(ByteArray(count*4))}
                        3->input.readFully(ByteArray(9))
                        4->{val down=input.readUnsignedByte()!=0;input.readShort();events+=input.readInt() to down}
                        5->{pointer+=input.readUnsignedByte();input.readShort();input.readShort()}
                        else->error("Unexpected RFB message")
                    }
                }
            }} catch(_: EOFException) {} catch(e: Throwable) {problem=e}
            finally {finished.countDown()}
        }.apply {isDaemon=true;start()}
        val client=RfbClient(server.localPort,"Test1234",object: RfbClient.Listener {
            override fun resize(width: Int,height: Int) {}
            override fun pixels(x: Int,y: Int,width: Int,height: Int,argb: IntArray) {}
            override fun status(message: String) {if(message=="Framebuffer connected") connected.countDown()}
        })
        try {
            client.start();assertTrue(connected.await(5,TimeUnit.SECONDS))
            client.text("a\r\nЖ😀\t")
            client.key(0xffe3,true);client.key(99,true);client.key(99,false);client.key(0xffe3,false)
            client.pointer(200,100,1);client.pointer(200,100,0)
            client.closeAfterInput()
            assertTrue(finished.await(5,TimeUnit.SECONDS))
            problem?.let {throw it}
            val expected=listOf(97,0xff0d,0x01000416,0x0101f600,0xff09).flatMap {listOf(it to true,it to false)}+
                listOf(0xffe3 to true,99 to true,99 to false,0xffe3 to false)
            assertEquals(expected,events.toList())
            assertEquals(listOf(1,0),pointer.toList())
        } finally {client.close();server.close();thread.join(1000)}
    }
}
