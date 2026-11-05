package dev.osify.app.engine

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.system.Os
import androidx.core.content.FileProvider
import dev.osify.app.BuildConfig
import dev.osify.app.data.VirtualMachine
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.time.Instant

/** Private per-machine diagnostics. Never records keyboard or clipboard contents. */
object VmLogs {
    const val LIMIT=1024*1024
    fun folder(context: Context,id: String): File=folder(context.filesDir,id)
    internal fun folder(filesDir: File,id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        val root=File(filesDir,"vms").canonicalFile
        return File(root,id).canonicalFile.also {require(it.parentFile==root);it.mkdirs()}
    }
    internal fun tail(file: File,limit: Int=LIMIT): String {
        if(!file.isFile) return "(empty)"
        return RandomAccessFile(file,"r").use {f->
            val offset=(f.length()-limit).coerceAtLeast(0);f.seek(offset)
            val bytes=ByteArray((f.length()-offset).toInt());f.readFully(bytes)
            (if(offset>0) "[last $limit bytes]\n" else "")+bytes.toString(Charsets.UTF_8)
        }
    }
    @Synchronized fun event(context: Context,id: String,text: String) {
        val file=File(folder(context,id),"runtime.log")
        if(file.length()>LIMIT) file.writeText(tail(file,LIMIT/2))
        file.appendText("${Instant.now()} $text\n")
    }
    fun prepare(context: Context,id: String) {
        val dir=folder(context,id)
        listOf("qemu","serial").forEach {name->
            val file=File(dir,"$name.log")
            if(file.isFile) {File(dir,"$name.previous.log").writeText(tail(file));file.delete()}
        }
        event(context,id,"=== New QEMU session ===")
    }
    /** Called only in the disposable :qemu process. Captures native stdout/stderr. */
    fun capture(context: Context,id: String) {
        val pipe=Os.pipe()
        val file=File(folder(context,id),"qemu.log")
        Thread({
            FileInputStream(pipe[0]).use {input->
                val chunk=ByteArray(8192)
                while(true) {
                    val count=input.read(chunk);if(count<0) break
                    if(file.length()>LIMIT) file.writeText(tail(file,LIMIT/2))
                    file.appendBytes(chunk.copyOf(count))
                }
            }
        },"qemu-log-writer").apply {isDaemon=true;start()}
        Os.dup2(pipe[1],1);Os.dup2(pipe[1],2);Os.close(pipe[1])
    }
    internal fun redact(text: String,vm: VirtualMachine,privateRoot: String): String {
        var result=text.replace(privateRoot,"<app>")
        listOf(vm.password,vm.token).filter {it.isNotEmpty()}.forEach {result=result.replace(it,"[redacted]")}
        return result.replace(Regex("(?i)(password|token)([\\s\"':=]+)[^\\s,;\"}]+"),"$1$2[redacted]")
            .replace(Regex("http://(?:10\\.0\\.2\\.2|127\\.0\\.0\\.1):[0-9]+/[a-f0-9]{32,64}/"),"http://<guest-tools>/[redacted]/")
    }
    fun report(context: Context,vm: VirtualMachine): String {
        val dir=folder(context,vm.id)
        val text=buildString {
            appendLine("OSify ${BuildConfig.VERSION_NAME} · VM diagnostics · ${Instant.now()}")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}; ABI ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Machine: ${vm.name}; ${vm.guest}; state=${vm.state}")
            appendLine("RAM=${vm.ramMiB} MiB; CPU=${vm.cores}; disk=${vm.diskGiB} GiB; ${vm.firmware}")
            appendLine("QEMU 5.1; hardware=${runCatching {dev.osify.core.VmHardware.encode(dev.osify.core.VmHardware.decode(vm.hardwareJson))}.getOrDefault("invalid")}")
            appendLine("Network uses Android user-mode NAT unless disabled or isolated. Guest driver/DHCP/DNS must work inside the guest.")
            appendLine("Status: ${vm.log}")
            appendLine("Guest journal is included only if the guest writes it to serial /dev/ttyS0. VGA screen text is not captured automatically.")
            listOf("runtime.log","qemu.log","serial.log","qemu.previous.log","serial.previous.log").forEach {name->
                appendLine("\n=== $name ===");appendLine(tail(File(dir,name)))
            }
        }
        return redact(text,vm,context.filesDir.absolutePath)
    }
    fun snapshot(context: Context,report: String): File {
        val dir=File(context.cacheDir,"vm-log-exports").also {it.mkdirs()}
        dir.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach {it.delete()}
        return File.createTempFile("OSify-", ".txt",dir).also {it.writeText(report)}
    }
    fun shareIntent(context: Context,file: File): Intent {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.logs",file)
        return Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri)
            .apply {clipData=ClipData.newRawUri("OSify diagnostics",uri)}.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    const val LINUX_DIAGNOSTICS="{ journalctl -b -u systemd-resolved -u systemd-udevd --no-pager -n 200; dmesg | tail -n 120; ip addr; ip route; cat /etc/resolv.conf; } > /dev/ttyS0 2>&1"
}
