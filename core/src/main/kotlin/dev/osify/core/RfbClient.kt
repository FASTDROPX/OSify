package dev.osify.core

import java.io.*
import java.net.*
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Bounded RFB 3.8 client for a password-protected, loopback-only QEMU framebuffer. */
class RfbClient(private val port: Int,private val password: String,private val listener: Listener): Closeable {
    interface Listener {
        fun resize(width: Int,height: Int)
        fun pixels(x: Int,y: Int,width: Int,height: Int,argb: IntArray)
        fun status(message: String)
    }
    private var socket: Socket?=null
    @Volatile private var output: DataOutputStream?=null
    @Volatile private var closed=false
    private val writer=Executors.newSingleThreadExecutor {r->Thread(r,"osify-vnc-write").apply {isDaemon=true}}
    fun start() {Thread({runCatching {read()}.onFailure {if(!closed) listener.status(it.message?:"Console disconnected")};close()},"osify-vnc-read").apply {isDaemon=true}.start()}
    private fun dimensions(w: Int,h: Int) {require(w in 1..4096 && h in 1..4096 && w.toLong()*h<=8_388_608) {"Unsupported framebuffer size"}}
    private fun read() {
        val s=Socket();socket=s;s.connect(InetSocketAddress("127.0.0.1",port),15000);s.soTimeout=15000;s.tcpNoDelay=true
        val input=DataInputStream(BufferedInputStream(s.getInputStream(),65536));val out=DataOutputStream(s.getOutputStream())
        val version=ByteArray(12);input.readFully(version);require(String(version,Charsets.US_ASCII).startsWith("RFB 003.")) {"Not an RFB server"}
        out.write("RFB 003.008\n".toByteArray());out.flush()
        val count=input.readUnsignedByte();require(count in 1..32) {"VNC server rejected connection"}
        val methods=ByteArray(count);input.readFully(methods);require(methods.contains(2.toByte())) {"Password authentication required"}
        out.writeByte(2);out.flush();val challenge=ByteArray(16);input.readFully(challenge)
        out.write(authenticate(password,challenge));out.flush();check(input.readInt()==0) {"VNC authentication failed"}
        out.writeByte(1);out.flush()
        var width=input.readUnsignedShort();var height=input.readUnsignedShort();dimensions(width,height)
        input.skipNBytesCompat(16);val nameLength=input.readInt();require(nameLength in 0..4096);input.skipNBytesCompat(nameLength)
        listener.resize(width,height);s.soTimeout=0
        // 32-bit little endian true colour: B,G,R,padding.
        synchronized(out) {
            out.write(byteArrayOf(0,0,0,0,32,24,0,1,0,(-1).toByte(),0,(-1).toByte(),0,(-1).toByte(),16,8,0,0,0,0))
            out.writeByte(2);out.writeByte(0);out.writeShort(3);listOf(0,-223,-224).forEach(out::writeInt);out.flush()
        }
        output=out
        listener.status("Framebuffer connected")
        request(false,width,height)
        while(!closed) {
            when(input.readUnsignedByte()) {
                0->{
                    var resized=false
                    input.readByte();val rectangles=input.readUnsignedShort();require(rectangles<=4096)
                    for(rect in 0 until rectangles) {
                        val x=input.readUnsignedShort();val y=input.readUnsignedShort();val w=input.readUnsignedShort();val h=input.readUnsignedShort();val encoding=input.readInt()
                        if(encoding==-224) break
                        if(encoding==-223) {dimensions(w,h);width=w;height=h;listener.resize(w,h);resized=true;continue}
                        require(x+w<=width && y+h<=height) {"Invalid framebuffer rectangle"}
                        when(encoding) {
                            0->{val bytes=ByteArray(w*h*4);input.readFully(bytes);val colors=IntArray(w*h) {i->val b=i*4;0xff000000.toInt() or ((bytes[b+2].toInt() and 255) shl 16) or ((bytes[b+1].toInt() and 255) shl 8) or (bytes[b].toInt() and 255)};listener.pixels(x,y,w,h,colors)}
                            1->throw IOException("Unexpected CopyRect; reconnect console")
                            else->throw IOException("Unsupported framebuffer encoding $encoding")
                        }
                    }
                    request(!resized,width,height)
                }
                2->Unit
                3->{input.skipNBytesCompat(3);val length=input.readInt();require(length in 0..1_048_576);input.skipNBytesCompat(length)}
                else->throw IOException("Unsupported framebuffer message")
            }
        }
    }
    private fun request(incremental: Boolean,w: Int,h: Int) {output?.let {out->synchronized(out) {out.writeByte(3);out.writeByte(if(incremental) 1 else 0);out.writeShort(0);out.writeShort(0);out.writeShort(w);out.writeShort(h);out.flush()}}}
    fun pointer(x: Int,y: Int,mask: Int)=send {writeByte(5);writeByte(mask);writeShort(x.coerceIn(0,4095));writeShort(y.coerceIn(0,4095))}
    fun key(symbol: Int,down: Boolean)=send {writeByte(4);writeByte(if(down) 1 else 0);writeShort(0);writeInt(symbol)}
    /** Ordered, paced paste: legacy PS/2 queues lose keys when a whole paragraph arrives at once. */
    fun text(text: String) {
        require(text.length<=65536) {"Text exceeds 65536 characters"}
        val normalized=text.replace("\r\n","\n").replace('\r','\n')
        if(closed)return
        runCatching {writer.execute {runCatching {normalized.codePoints().forEach {symbol->
              if(closed)throw EOFException()
            val key=when(symbol) {10->0xff0d;9->0xff09;8->0xff08;else->if(symbol<=255) symbol else 0x01000000 or symbol}
              output?.let {out->synchronized(out) {out.writeByte(4);out.writeByte(1);out.writeShort(0);out.writeInt(key);out.flush()}}
              Thread.sleep(20)
              output?.let {out->synchronized(out) {out.writeByte(4);out.writeByte(0);out.writeShort(0);out.writeInt(key);out.flush()}}
              Thread.sleep(20)
        }}.onFailure {close()}}}
    }
    /** Flush key/button releases before disconnecting, without blocking the UI thread. */
    fun closeAfterInput() {runCatching {writer.execute {close()}}.onFailure {close()}}
    private fun send(block: DataOutputStream.()->Unit) {if(closed)return;runCatching {writer.execute {runCatching {output?.let {synchronized(it) {it.block();it.flush()}}}.onFailure {close()}}}}
    override fun close() {closed=true;runCatching {socket?.close()};writer.shutdownNow()}
    companion object {
        fun authenticate(password: String,challenge: ByteArray): ByteArray {
            require(challenge.size==16)
            val bytes=password.toByteArray(Charsets.ISO_8859_1).copyOf(8)
            val key=bytes.map {Integer.reverse(it.toInt() and 255).ushr(24).toByte()}.toByteArray()
            return Cipher.getInstance("DES/ECB/NoPadding").apply {init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"DES"))}.doFinal(challenge)
        }
    }
}
private fun DataInputStream.skipNBytesCompat(count: Int) {var remaining=count;while(remaining>0) {val n=skipBytes(remaining);if(n<=0) {readByte();remaining--} else remaining-=n}}
