package dev.osify.core

import java.io.*
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.Semaphore
import java.util.concurrent.CopyOnWriteArraySet

/** Loopback-only capability URL; never exposes directory listings or accepts filesystem paths. */
class LocalImageBridge: Closeable {
    private val slots=Semaphore(2)
    private val clients=CopyOnWriteArraySet<Socket>()
    private var server: ServerSocket?=null
    private var secret=""
    private var image: File?=null
    @Synchronized fun publish(file: File): String {
        close();require(file.isFile && file.length()>0)
        secret=ByteArray(24).also {SecureRandom().nextBytes(it)}.joinToString("") {"%02x".format(it)}
        image=file;val socket=ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));server=socket
        Thread({while(!socket.isClosed) {try {val client=socket.accept();if(!slots.tryAcquire()) {client.close();continue};clients.add(client);Thread({try {serve(client)} finally {clients.remove(client);slots.release()}},"osify-image-client").apply {isDaemon=true}.start()} catch(_: IOException) {break}}},"osify-image-bridge").apply {isDaemon=true}.start()
        return "http://127.0.0.1:${socket.localPort}/$secret"
    }
    private fun serve(client: Socket) {
        client.use {s->
            runCatching {
                s.soTimeout=15000
                val reader=BufferedInputStream(s.getInputStream())
                val request=reader.headerLine(1024)?:return
                var headers=0
                while(true) {val line=reader.headerLine(1024)?:return;if(line.isEmpty()) break;headers+=line.length;require(headers<8192)}
                val expected="GET /$secret HTTP/1.1"
                val f=image
                val out=s.getOutputStream()
                if(request!=expected || f==null) {out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray());return}
                out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${f.length()}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
                f.inputStream().use {it.copyTo(out,262144)};out.flush()
            }
        }
    }
    @Synchronized override fun close() {runCatching {server?.close()};server=null;clients.forEach {runCatching {it.close()}};clients.clear();image=null;secret=""}
}

private fun InputStream.headerLine(limit: Int): String? {
    val line=ByteArrayOutputStream()
    while(line.size()<limit) {val value=read();if(value<0)return null;if(value==10)return line.toString("US-ASCII").removeSuffix("\r");line.write(value)}
    throw IOException("HTTP header too long")
}
