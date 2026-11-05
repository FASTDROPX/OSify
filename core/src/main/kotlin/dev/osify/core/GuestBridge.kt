package dev.osify.core

import java.io.*
import java.net.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Semaphore

/** Per-VM loopback-only capability server, reachable from QEMU user networking as 10.0.2.2. */
class GuestBridge(private val folder: File,private val key: String,port: Int=0): Closeable {
    private val server=ServerSocket(port,4,InetAddress.getByName("127.0.0.1"))
    private val clients=CopyOnWriteArraySet<Socket>();private val slots=Semaphore(4)
    val port get()=server.localPort
    val url get()="http://10.0.2.2:$port/$key/"
    init {
        require(key.matches(Regex("[a-f0-9]{32,64}")))
        File(folder,"inbox").mkdirs();File(folder,"outbox").mkdirs()
        Thread({while(!server.isClosed) {try {
            val client=server.accept()
            if(!slots.tryAcquire()) {client.close();continue}
            clients.add(client)
            Thread({try {serve(client)} finally {clients.remove(client);slots.release()}},"guest-tools-request").apply {isDaemon=true}.start()
        } catch(_: IOException) {break}}},"guest-tools").apply {isDaemon=true}.start()
    }
    private fun line(input: InputStream,limit: Int=4096): String {
        val b=ByteArrayOutputStream()
        while(b.size()<limit) {val c=input.read();if(c<0)throw EOFException();if(c==10)return b.toString("UTF-8").removeSuffix("\r");b.write(c)}
        throw IOException("Header too long")
    }
    private fun escape(s: String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    private fun serve(socket: Socket)=socket.use {s->
        s.soTimeout=30000
        val input=BufferedInputStream(s.getInputStream());val out=s.getOutputStream()
        fun response(code: String,type: String,bytes: ByteArray) {out.write("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n".toByteArray());out.write(bytes);out.flush()}
        try {
            val request=line(input).split(' ');require(request.size==3)
            val headers=mutableMapOf<String,String>();var total=0
            while(true) {val h=line(input);if(h.isEmpty())break;total+=h.length;require(total<16384);headers[h.substringBefore(':').lowercase()]=h.substringAfter(':',"").trim()}
            val prefix="/$key/";val path=request[1]
            if(!path.startsWith(prefix)) {response("404 Not Found","text/plain",byteArrayOf());return@use}
            val endpoint=path.removePrefix(prefix)
            if(request[0]=="GET" && endpoint=="display") {
                val f=File(folder,"display.json");val data=if(f.isFile && f.length()<4096) f.readBytes() else "{\"enabled\":false}".toByteArray()
                response("200 OK","application/json",data);return@use
            }
            if(request[0]=="POST" && endpoint=="status") {
                val n=headers["content-length"]?.toIntOrNull()?:0;require(n in 1..2048)
                val b=ByteArray(n);DataInputStream(input).readFully(b)
                val temp=File(folder,"agent-status.tmp");temp.writeText(System.currentTimeMillis().toString()+"\n"+b.toString(Charsets.UTF_8));temp.renameTo(File(folder,"agent-status.txt"))
                response("200 OK","text/plain","ok".toByteArray());return@use
            }
            if(request[0]=="POST" && endpoint=="upload") {
                val size=headers["content-length"]?.toLongOrNull()?:0
                require(size in 1..MAX_UPLOAD && folder.usableSpace>size+16L*1024*1024)
                require(File(folder,"outbox").listFiles().orEmpty().sumOf {it.length()}+size<=512L*1024*1024) {"Remove received files in OSify before uploading more"}
                val remaining=object: InputStream() {
                    var left=size
                    override fun read(): Int {if(left<=0)return -1;val v=input.read();if(v<0)throw EOFException();left--;return v}
                    override fun read(b: ByteArray,off: Int,len: Int): Int {if(left<=0)return -1;val n=input.read(b,off,minOf(len.toLong(),left).toInt());if(n<0)throw EOFException();left-=n;return n}
                }
                val multipart=headers["content-type"].orEmpty().startsWith("multipart/form-data")
                val rawName: String;var boundary: ByteArray?=null
                if(multipart) {
                    val token=headers["content-type"].orEmpty().substringAfter("boundary=","").trim().trim('"');require(token.length in 1..160)
                    require(line(remaining)=="--$token")
                    val disposition=line(remaining);rawName=Regex("filename=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1)?:"guest-file"
                    var count=0;while(line(remaining).isNotEmpty())require(count++<16)
                    boundary=("\r\n--$token").toByteArray()
                } else rawName=URLDecoder.decode(headers["x-filename"]?:"guest-file","UTF-8")
                val name=safeName(rawName)
                val target=File(File(folder,"outbox"),UUID.randomUUID().toString().take(8)+"-"+name)
                val temp=File(target.parentFile,target.name+".part")
                try {
                    temp.outputStream().use {file->
                        if(boundary==null) remaining.copyTo(file,65536) else {
                            var pending=byteArrayOf();var found=false;val chunk=ByteArray(65536)
                            while(true) {
                                val n=remaining.read(chunk);if(n<0)break
                                val all=pending+chunk.copyOf(n);val end=indexOf(all,boundary)
                                if(end>=0) {file.write(all,0,end);found=true;break}
                                val write=(all.size-boundary.size).coerceAtLeast(0);file.write(all,0,write);pending=all.copyOfRange(write,all.size)
                            }
                            require(found) {"Incomplete upload"}
                        }
                    }
                    require(temp.renameTo(target))
                } finally {temp.delete()}
                response("200 OK","text/html; charset=utf-8","<meta charset=utf-8><p>Saved to OSify / Файл передан на телефон.</p><a href='./'>Back / Назад</a>".toByteArray());return@use
            }
            if(request[0]=="GET" && endpoint.startsWith("file/")) {
                val name=URLDecoder.decode(endpoint.removePrefix("file/"),"UTF-8");require(name==safeName(name))
                val file=File(File(folder,"inbox"),name).canonicalFile
                require(file.parentFile==File(folder,"inbox").canonicalFile && file.isFile)
                out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment\r\nContent-Length: ${file.length()}\r\nConnection: close\r\n\r\n".toByteArray());file.inputStream().use {it.copyTo(out,65536)};out.flush();return@use
            }
            if(request[0]!="GET" || endpoint.isNotEmpty()) {response("404 Not Found","text/plain",byteArrayOf());return@use}
            val links=File(folder,"inbox").listFiles().orEmpty().filter {it.isFile && !it.name.startsWith('.')}.joinToString("") {"<li><a href='file/${URLEncoder.encode(it.name,"UTF-8")}'>${escape(it.name)}</a></li>"}
            val page="""<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>OSify files</title><style>body{font:16px sans-serif;background:#101418;color:#e0e8f0;max-width:720px;margin:24px auto;padding:16px}a{color:#91c8ff}input,button{padding:12px;margin:8px 0}</style><h1>OSify · Files / Файлы</h1><h2>To phone / На телефон</h2><form action='upload' method='post' enctype='multipart/form-data'><input type='file' name='file' required><br><button type='submit'>Send / Передать</button></form><p>Max 128 MB. Save received files in OSify on your phone.<br>До 128 МБ. Сохраните принятые файлы через OSify на телефоне.</p><h2>From phone / С телефона</h2><ul>$links</ul>"""
            response("200 OK","text/html; charset=utf-8",page.toByteArray())
        } catch(_: Exception) {runCatching {response("400 Bad Request","text/plain","Request failed / Не удалось передать файл".toByteArray())}}
    }
    override fun close() {runCatching {server.close()};clients.forEach {runCatching {it.close()}};clients.clear()}
    companion object {
        const val MAX_UPLOAD=128L*1024*1024
        fun safeName(name: String)=name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\p{Cntrl}<>:\"|?*]"),"_").trim().trim('.').take(60).ifEmpty {"file"}
        private fun indexOf(data: ByteArray,pattern: ByteArray): Int {for(i in 0..data.size-pattern.size) {var j=0;while(j<pattern.size && data[i+j]==pattern[j])j++;if(j==pattern.size)return i};return -1}
    }
}
