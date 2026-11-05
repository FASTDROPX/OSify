package dev.osify.app.engine

import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import androidx.core.content.ContextCompat
import dev.osify.app.OsifyApplication
import dev.osify.core.VmHardware
import dev.osify.app.data.ImageRecord
import dev.osify.app.data.VirtualMachine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.UUID

/** The packaged QEMU runs in a disposable, private Android service process. */
class NativeQemuEngine(private val app: OsifyApplication) {
    private val lock=Mutex()
    private val dao get()=app.database.dao()
    val available get()=File(app.applicationInfo.nativeLibraryDir,"libqemu-system-x86_64.so").isFile
    suspend fun prepare() {check(available) {"This APK has no QEMU for this device ABI"}}
    suspend fun launch(requested: VirtualMachine,bootIso: Boolean)=lock.withLock {
        app.startupRecovery.join()
        val vm=dao.virtualMachine(requested.id)?:error("Machine removed")
        check(available) {"This APK supports ARM64 and x86_64 devices"}
        check(dao.virtualMachines().first().none {it.state in setOf("REQUESTED","DISPLAY_AVAILABLE","PAUSED","SHUTDOWN_REQUESTED")}) {"Stop the running machine first"}
        withContext(Dispatchers.IO) {resolveBoot(app,vm,bootIso)}
        val token=UUID.randomUUID().toString()
        dao.virtualMachine(vm.copy(state="REQUESTED",operation="native-launch",token=token,log="Starting embedded QEMU"))
        try {
            ContextCompat.startForegroundService(app,Intent(app,QemuService::class.java).putExtra("vm",vm.id).putExtra("bootIso",bootIso).putExtra("token",token))
            app.scope.launch {
                var observed=false;var misses=0
                while(true) {
                    delay(1000)
                    val row=dao.virtualMachine(vm.id)?:return@launch
                    if(row.token!=token || row.state !in setOf("REQUESTED","DISPLAY_AVAILABLE","PAUSED","SHUTDOWN_REQUESTED"))return@launch
                    val running=app.getSystemService(android.app.ActivityManager::class.java).getRunningServices(50).any {it.service.className==QemuService::class.java.name}
                    if(running) {observed=true;misses=0} else misses++
                    if(misses>=(if(observed) 3 else 20)) {
                        val nativeLog=VmLogs.tail(File(VmLogs.folder(app,vm.id),"qemu.log"),4096)
                        dao.finishVm(vm.id,token,"FAILED","QEMU process exited. "+nativeLog.takeLast(2000));return@launch
                    }
                }
            }
        } catch(e: Exception) {dao.finishVm(vm.id,token,"FAILED",e.message.orEmpty());throw e}
    }
    suspend fun control(vm: VirtualMachine,action: String)=lock.withLock {
        check(action in setOf("pause","resume","shutdown","force"))
        val latest=dao.virtualMachine(vm.id)?:error("Machine removed")
        check(latest.token==vm.token && latest.state in setOf("REQUESTED","DISPLAY_AVAILABLE","PAUSED","SHUTDOWN_REQUESTED")) {"This machine is no longer running"}
        VmLogs.event(app,vm.id,"Control: $action")
        val command=when(action) {"pause"->"stop";"resume"->"cont";"shutdown"->"system_powerdown";else->"quit"}
        withContext(Dispatchers.IO) {QemuControl(File(app.filesDir,"qemu-qmp.sock")).use {it.execute(command)}}
        if(action!="force") dao.virtualMachine(latest.copy(state=when(action) {"pause"->"PAUSED";"resume"->"DISPLAY_AVAILABLE";else->"SHUTDOWN_REQUESTED"},operation="native-$action"))
    }
    suspend fun changeMedium(id: String,device: String,file: File?)=lock.withLock {
        require(device in setOf("osify-share","osify-drivers"))
        val vm=dao.virtualMachine(id)?:error("Machine removed")
        if(vm.state in setOf("STOPPED","FAILED"))return@withLock
        check(vm.state in setOf("DISPLAY_AVAILABLE","PAUSED")) {"Дождитесь завершения запуска машины."}
        withContext(Dispatchers.IO) {QemuControl(File(app.filesDir,"qemu-qmp.sock")).use {qmp->
            // QEMU 5.1 accepts force on eject, not on blockdev-change-medium.
            qmp.execute("eject",JSONObject().put("device",device).put("force",true))
            if(file!=null) qmp.execute("blockdev-change-medium",JSONObject().put("device",device).put("filename",file.absolutePath).put("format","raw"))
        }}
    }
    suspend fun resetPending(id: String) {
        val row=dao.virtualMachine(id)?:return
        runCatching {control(row,"force")}.getOrElse {dao.virtualMachine(row.copy(state="STOPPED",token="",log="Engine is not running"))}
    }
    suspend fun remove(vm: VirtualMachine)=lock.withLock {
        check(dao.deleteStoppedVm(vm.id)==1) {"Stop the machine before removing it"}
        val root=File(app.filesDir,"vms").canonicalFile
        val folder=File(root,vm.id).canonicalFile
        require(folder.parentFile==root)
        folder.deleteRecursively()
    }
    suspend fun configure(id: String,name: String,ram: Int,cores: Int,firmware: String,hardware: VmHardware?=null)=lock.withLock {
        app.startupRecovery.join()
        require(name.isNotBlank() && name.length<=80)
        validateResources(ram,cores)
        require(firmware in setOf("BIOS","UEFI"))
        hardware?.validate()
        val changed=if(hardware==null) dao.configureVm(id,name.trim(),ram,cores,firmware) else dao.configureHardware(id,name.trim(),ram,cores,firmware,VmHardware.encode(hardware))
        check(changed==1) {"Stop the machine before changing its hardware"}
        VmLogs.event(app,id,"Configuration saved: RAM=$ram MiB, CPU=$cores, firmware=$firmware")
    }
    suspend fun clone(id: String,progress: (Float)->Unit={})=lock.withLock {
        app.startupRecovery.join()
        val source=dao.virtualMachine(id)?:error("Machine removed")
        check(source.state=="STOPPED") {"Остановите машину перед клонированием."}
        val original=VmLogs.folder(app,id)
        val disks=listOf("disk.raw","import.qcow2","import.img").map {File(original,it)}.filter {it.isFile}
        check(disks.size==1) {"Для копирования нужен один созданный виртуальный диск."}
        val files=disks+listOf(File(original,"OVMF_VARS.fd")).filter {it.isFile}
        val newId=UUID.randomUUID().toString();val target=VmLogs.folder(app,newId)
        val total=files.sumOf {it.length()}.coerceAtLeast(1);var copied=0L
        var committed=false
        try {
            for(file in files) RandomAccessFile(file,"r").use {input->RandomAccessFile(File(target,file.name),"rw").use {output->
                output.setLength(input.length())
                val block=ByteArray(1048576);val zeros=ByteArray(block.size)
                while(input.filePointer<input.length()) {
                    currentCoroutineContext().ensureActive()
                    val offset=input.filePointer;val n=input.read(block);if(n<0)break
                    if(n<block.size) java.util.Arrays.fill(block,n,block.size,0.toByte())
                    if(!block.contentEquals(zeros)) {check(target.usableSpace>n+16L*1024*1024) {"Недостаточно памяти для копии диска."};output.seek(offset);output.write(block,0,n)}
                    copied+=n;progress((copied.toDouble()/total).toFloat())
                }
                output.fd.sync()
            }}
            val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
            val password=UUID.randomUUID().toString().replace("-","").take(8)
            val h=VmHardware.decode(source.hardwareJson).copy(mac="")
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {dao.virtualMachine(source.copy(id=newId,name=(source.name+" · copy").take(80),port=port,password=password,state="STOPPED",token="",operation="",log="Cloned disk and configuration",hardwareJson=VmHardware.encode(h)));committed=true}
        } catch(e: Throwable) {if(!committed)target.deleteRecursively();throw e}
    }
    private fun validateResources(ram: Int,cores: Int) {
        require(ram in 256..4096 && cores in 1..4)
        val physical=app.profiler.snapshot().profile.totalRam
        require(physical<=0 || ram.toLong()*1048576<=physical/2) {"Choose less RAM for this phone"}
    }
    suspend fun create(image: ImageRecord,name: String,guest: String,ram: Int,cores: Int,disk: Int,firmware: String) {
        app.startupRecovery.join()
        require(name.isNotBlank() && name.length<=80 && guest in setOf("LINUX","WINDOWS"))
        require(ram in 256..4096 && cores in 1..4 && disk in 2..128 && firmware in setOf("BIOS","UEFI"))
        validateResources(ram,cores)
        check(image.state=="COMPLETE")
        require(image.architecture!="ARM64") {"Choose an x64 guest image for the built-in PC emulator"}
        val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
        val alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random=SecureRandom()
        val password=(1..8).map {alphabet[random.nextInt(alphabet.length)]}.joinToString("")
        val arch=image.architecture
        require(arch in setOf("X86","X86_64")) {"Выберите архитектуру x86 или x64 при создании машины."}
        dao.virtualMachine(VirtualMachine(UUID.randomUUID().toString(),name,guest,image.id,ram,cores,disk,port,password,firmware=firmware,hardwareJson=VmHardware.encode(dev.osify.core.BootPlan.defaults(name+" "+image.title,arch))))
    }
}

internal class QemuControl(file: File): AutoCloseable {
    private val socket=LocalSocket()
    private val input: java.io.BufferedReader
    private val output: java.io.BufferedWriter
    init {
        socket.connect(LocalSocketAddress(file.absolutePath,LocalSocketAddress.Namespace.FILESYSTEM))
        socket.soTimeout=5000
        input=socket.inputStream.bufferedReader();output=socket.outputStream.bufferedWriter()
        check(JSONObject(input.readLine()?:error("QMP closed")).has("QMP"))
        execute("qmp_capabilities")
    }
    fun execute(command: String,arguments: JSONObject?=null): JSONObject {
        val request=JSONObject().put("execute",command).put("id","osify")
        if(arguments!=null) request.put("arguments",arguments)
        output.write(request.toString());output.newLine();output.flush()
        repeat(100) {
            val response=JSONObject(input.readLine()?:error("QEMU disconnected"))
            if(response.optString("id")=="osify") {check(!response.has("error")) {response.optJSONObject("error")?.optString("desc").orEmpty()};return response}
        }
        error("QEMU did not answer")
    }
    override fun close() {socket.close()}
}
