package dev.osify.app.engine

import android.net.Uri
import android.provider.OpenableColumns
import dev.osify.app.OsifyApplication
import dev.osify.app.data.VirtualMachine
import dev.osify.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID

/** Private staged media; the running service owns the bridge, UI owns SAF import/export. */
class GuestTools(private val app: OsifyApplication) {
    private val mutex=Mutex()
    fun folder(id: String)=File(VmLogs.folder(app,id),"exchange").also {it.mkdirs()}
    fun inbox(id: String)=File(folder(id),"inbox").also {it.mkdirs()}
    fun outbox(id: String)=File(folder(id),"outbox").also {it.mkdirs()}
    private fun json(file: File)=runCatching {JSONObject(file.readText())}.getOrDefault(JSONObject())
    private fun atomic(file: File,data: String) {
        val temp=File(file.parentFile,file.name+".tmp")
        try {temp.writeText(data);java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)} finally {temp.delete()}
    }
    @Synchronized private fun endpoint(id: String): JSONObject {
        val file=File(folder(id),"bridge.json");val old=json(file)
        if(old.optString("key").matches(Regex("[a-f0-9]{32}")) && old.optInt("port") in 1024..65535)return old
        return JSONObject().put("key",UUID.randomUUID().toString().replace("-","")).put("port",ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}).also {atomic(file,it.toString())}
    }
    fun url(id: String): String {val e=endpoint(id);return "http://10.0.2.2:${e.getInt("port")}/${e.getString("key")}/"}
    fun startBridge(id: String): GuestBridge {
        val e=endpoint(id)
        val bridge=runCatching {GuestBridge(folder(id),e.getString("key"),e.getInt("port"))}.getOrElse {GuestBridge(folder(id),e.getString("key"))}
        e.put("port",bridge.port);atomic(File(folder(id),"bridge.json"),e.toString());return bridge
    }
    fun display(id: String)=json(File(folder(id),"display.json"))
    @Synchronized fun desiredDisplay(id: String,width: Int,height: Int,enabled: Boolean?=null) {
        val file=File(folder(id),"display.json");val current=json(file)
        val scale=minOf(1.0,1600.0/maxOf(width,height).coerceAtLeast(1))
        val w=((width*scale).toInt()/8*8).coerceIn(480,1920);val h=((height*scale).toInt()/8*8).coerceIn(320,1920)
        atomic(file,JSONObject().put("width",w).put("height",h).put("enabled",enabled?:current.optBoolean("enabled",true)).toString())
    }
    fun agentStatus(id: String): String {
        val f=File(folder(id),"agent-status.txt")
        if(!f.isFile || f.length()>4096)return ""
        return runCatching {
            val parts=f.readText().split('\n',limit=2)
            if(System.currentTimeMillis()-parts[0].toLong()>10000) "" else JSONObject(parts[1]).let {"${it.optInt("width")} × ${it.optInt("height")} · ${it.optString("message").take(140)}"}
        }.getOrDefault("")
    }
    suspend fun importFiles(vm: VirtualMachine,uris: List<Uri>)=mutex.withLock {
        check(uris.size in 1..100)
        val dir=inbox(vm.id)
        for(uri in uris) {
            currentCoroutineContext().ensureActive()
            var name="file"
            app.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())name=it.getString(0)?:name}
            val target=File(dir,UUID.randomUUID().toString().take(8)+"-"+GuestBridge.safeName(name).take(51))
            val temp=File(dir,target.name+".part")
            try {
                var bytes=0L
                app.contentResolver.openInputStream(uri)!!.use {input->temp.outputStream().use {output->
                    val b=ByteArray(65536)
                    while(true) {
                        currentCoroutineContext().ensureActive();val n=input.read(b);if(n<0)break;bytes+=n
                        require(bytes<=GuestBridge.MAX_UPLOAD && dir.usableSpace>n+16L*1024*1024) {"Файл больше 128 МБ или недостаточно места."}
                        output.write(b,0,n)
                    }
                }}
                require(dir.listFiles().orEmpty().count {it.isFile && !it.name.endsWith(".part")}<200) {"На диске обмена слишком много файлов."}
                require(dir.listFiles().orEmpty().sumOf {it.length()}<512L*1024*1024) {"Освободите место на диске обмена (до 512 МБ)."}
                check(temp.renameTo(target))
            } finally {temp.delete()}
        }
        rebuild(vm.id)
        app.vmEngine.changeMedium(vm.id,"osify-share",File(folder(vm.id),"phone.iso"))
    }
    fun rebuild(id: String): File {
        val dir=folder(id);val tools=File(dir,"tools").also {it.mkdirs()};val base=url(id)
        for(name in listOf("OSify-Display.ps1","OSify-Display.py")) {
            val text=app.assets.open("guest-tools/$name").bufferedReader().use {it.readText()}.replace("__OSIFY_URL__",base)
            File(tools,name).writeText(text)
        }
        File(tools,"OSify-Install-Windows.cmd").writeText("@powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"%~dp0OSify-Display.ps1\" -Install\r\n")
        File(tools,"OSify-Files.url").writeText("[InternetShortcut]\r\nURL=$base\r\n")
        File(tools,"OSify-Readme.txt").writeText("""
OSify PHONE disc / Диск Телефон

Files from Android are on this CD (read-only).
Файлы с телефона находятся на этом диске, только для чтения.

Send files back through the guest browser / Передача файлов обратно:
$base
The VM must be running with NAT networking. Save received files in OSify on Android.
Машина должна работать с NAT-сетью. Сохраните принятый файл через OSify на телефоне.

Optional automatic resolution / Автоподстройка разрешения:
Windows 7+ with PowerShell: copy all OSify-* files to a writable folder, run OSify-Install-Windows.cmd.
Windows 7+: скопируйте OSify-* в доступную для записи папку, запустите OSify-Install-Windows.cmd.
Linux X11 with Python3 and xrandr: python3 OSify-Display.py --install
Helper follows the visible phone viewport; modes still depend on guest video drivers.
Помощник применяет поддерживаемые драйвером режимы. Wayland и XP не поддерживаются этим помощником.
Uninstall: remove OSify Display.cmd from Windows Startup, or ~/.config/autostart/osify-display.desktop on Linux.
Удаление: уберите OSify Display.cmd из автозагрузки Windows или osify-display.desktop из автозагрузки Linux.
""".trimIndent())
        val files=inbox(id).listFiles().orEmpty().filter {it.isFile && !it.name.endsWith(".part")}+tools.listFiles().orEmpty().filter {it.isFile}
        val target=File(dir,"phone.iso");val temp=File(dir,"phone.iso.tmp")
        try {ExchangeIso.write(files,temp);check(temp.renameTo(target))} finally {temp.delete()}
        return target
    }
    fun selectedDriver(id: String)=json(File(folder(id),"media.json")).optString("driver")
    suspend fun driverFile(id: String,driverId: String=selectedDriver(id)): File? {
        val image=app.database.dao().image(driverId)?:return null
        if(image.state!="COMPLETE")return null
        val root=File(app.filesDir,"images").canonicalFile;val file=File(root,image.fileName).canonicalFile
        require(file.parentFile==root && file.isFile && file.length()==image.bytes) {"Driver ISO unavailable"}
        require(BootMediaProbe.inspect(file).kind in setOf("iso","udf")) {"Choose a real driver ISO"}
        return file
    }
    suspend fun selectDriver(vm: VirtualMachine,id: String)=mutex.withLock {
        val driver=if(id.isEmpty())null else driverFile(vm.id,id)?:error("Дождитесь завершения загрузки ISO.")
        app.vmEngine.changeMedium(vm.id,"osify-drivers",driver)
        atomic(File(folder(vm.id),"media.json"),JSONObject().put("driver",id).toString())
    }
    suspend fun deleteSent(vm: VirtualMachine,file: File)=mutex.withLock {
        require(file.canonicalFile.parentFile==inbox(vm.id).canonicalFile);check(file.delete())
        rebuild(vm.id);app.vmEngine.changeMedium(vm.id,"osify-share",File(folder(vm.id),"phone.iso"))
    }
    suspend fun deleteReceived(id: String,file: File)=mutex.withLock {
        require(file.canonicalFile.parentFile==outbox(id).canonicalFile);check(file.delete())
    }
}
