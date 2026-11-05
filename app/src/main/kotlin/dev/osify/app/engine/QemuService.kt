package dev.osify.app.engine

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.max2idea.android.limbo.jni.VMExecutor
import dev.osify.app.*
import dev.osify.core.VmHardware
import dev.osify.app.data.VirtualMachine
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** QEMU owns process globals; dispose this service process after every guest exit. */
class QemuService: Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val app get()=application as OsifyApplication
    private var current: VirtualMachine?=null
    private var token=""
    private var started=false
    private var stopping=false
    private var bridge: dev.osify.core.GuestBridge?=null
    private val qmp get()=File(filesDir,"qemu-qmp.sock")
    override fun onBind(intent: Intent?)=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("guest","OSify · QEMU",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,QemuService::class.java).setAction("shutdown"),PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"guest").setSmallIcon(R.drawable.ic_download).setContentTitle("OSify · QEMU").setContentText("Virtual machine").setOngoing(true).setContentIntent(open).addAction(0,"Stop",stop).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(915,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(915,notification)
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="shutdown") {scope.launch {runCatching {QemuControl(qmp).use {it.execute("system_powerdown")}}};return START_NOT_STICKY}
        if(started) return START_NOT_STICKY
        val id=intent?.getStringExtra("vm")?:run {stopSelf();return START_NOT_STICKY}
        started=true;token=intent.getStringExtra("token").orEmpty()
        scope.launch {startGuest(id,intent.getBooleanExtra("bootIso",true))}
        return START_NOT_STICKY
    }
    private suspend fun startGuest(id: String,bootIso: Boolean) {
        val dao=app.database.dao()
        try {
            val vm=dao.virtualMachine(id)?:error("Machine removed")
            check(vm.token==token)
            current=vm
            VmLogs.prepare(this,id)
            VmLogs.capture(this,id)
            VmLogs.event(this,id,"Starting ${vm.firmware}: ${vm.ramMiB} MiB, ${vm.cores} CPU; requestedISO=$bootIso")
            val boot=resolveBoot(app,vm,bootIso,prepareDisk=true)
            val firmware=File(filesDir,"qemu-5.1").also {it.mkdirs()}
            fun copyAssets(source: String,target: File) {
                val children=assets.list(source).orEmpty()
                if(children.isEmpty()) {val temp=File(target.parentFile,target.name+".tmp");try {assets.open(source).use {input->temp.outputStream().use {input.copyTo(it)}};check(temp.renameTo(target))} finally {temp.delete()}}
                else {target.mkdirs();children.forEach {copyAssets("$source/$it",File(target,it))}}
            }
            copyAssets("qemu",firmware)
            val folder=File(File(filesDir,"vms"),vm.id).also {it.mkdirs()}
            bridge=app.guestTools.startBridge(vm.id)
            val share=app.guestTools.rebuild(vm.id)
            val drivers=app.guestTools.driverFile(vm.id)
            qmp.delete()
            android.system.Os.setenv("TMPDIR",cacheDir.absolutePath,true)
            val hardware=boot.hardware
            val args=mutableListOf("qemu-system-x86_64","-L",firmware.absolutePath,"-m",vm.ramMiB.toString(),"-smp",vm.cores.toString(),"-display","none","-vnc","127.0.0.1:${vm.port-5900},password=on","-qmp","unix:${qmp.absolutePath},server=on,wait=off","-monitor","none","-parallel","none")
            args+=hardware.arguments(vm.guest,vm.firmware,boot.disk.absolutePath,boot.format,boot.iso?.absolutePath,File(folder,"serial.log").absolutePath)
            fun extraCd(id: String,file: File?,bus: String,unit: Int) {
                args+=listOf("-drive","if=none,media=cdrom,readonly=on,id=$id"+(file?.let {",file=${it.absolutePath},format=raw"}?:""),"-device","ide-cd,drive=$id,bus=$bus,unit=$unit")
            }
            extraCd("osify-share",share,if(hardware.machine=="q35") "ide.2" else "ide.0",if(hardware.machine=="q35") 0 else 1)
            extraCd("osify-drivers",drivers,if(hardware.machine=="q35") "ide.3" else "ide.1",if(hardware.machine=="q35") 0 else 1)
            VmLogs.event(this,id,"Hardware: ${VmHardware.encode(hardware)}")
            if(vm.firmware=="UEFI") {
                val code=File(firmware,"OVMF_CODE.fd")
                check(code.isFile) {"UEFI firmware is missing. Choose BIOS for this image."}
                val vars=File(folder,"OVMF_VARS.fd").also {if(!it.exists()) File(firmware,"OVMF_VARS.fd").copyTo(it)}
                args.addAll(listOf("-drive","if=pflash,format=raw,readonly=on,file=${code.absolutePath}","-drive","if=pflash,format=raw,file=${vars.absolutePath}"))
            }
            VmLogs.event(this,id,"VM id=${vm.id}; guestCPU=${boot.architecture}; binary=libqemu-system-x86_64.so; acceleration=TCG software")
            VmLogs.event(this,id,"Disk=${boot.disk.absolutePath}; format=${boot.format}; size=${boot.disk.length()}; ISO=${boot.iso?.absolutePath}; ISOsize=${boot.iso?.length()}; media=${boot.media}")
            VmLogs.event(this,id,"QEMU args: "+args.joinToString(" "))
            val readiness=scope.launch {
                try {
                    withTimeout(60000) {
                        while(!qmp.exists()) delay(100)
                        var ready=false
                        while(!ready) {
                            ready=runCatching {QemuControl(qmp).use {control->
                                control.execute("set_password",JSONObject().put("protocol","vnc").put("password",vm.password))
                                control.execute("query-status")
                            };true}.getOrDefault(false)
                            if(!ready) delay(150)
                        }
                    }
                    val latest=dao.virtualMachine(id)
                    if(latest?.token==token) dao.virtualMachine(latest.copy(state="DISPLAY_AVAILABLE",operation="native-running",log="Embedded QEMU 5.1 · VNC and QMP ready"))
                } catch(e: Exception) {if(e !is CancellationException) {dao.finishVm(id,token,"FAILED","QEMU did not start: ${e.message}");runCatching {QemuControl(qmp).use {it.execute("quit")}}}}
            }
            val result=VMExecutor().start(filesDir.absolutePath,firmware.absolutePath,"libqemu-system-x86_64.so","${applicationInfo.nativeLibraryDir}/libqemu-system-x86_64.so",0,args.toTypedArray())
            VmLogs.event(this,id,"QEMU exited: $result")
            readiness.cancelAndJoin()
            val latest=dao.virtualMachine(id)
            if(latest?.token==token) dao.virtualMachine(latest.copy(state=if(result!="VM shutdown") "FAILED" else "STOPPED",operation="native-stopped",token="",log=result))
        } catch(e: Throwable) {
            runCatching {VmLogs.event(this,id,"Failure: ${e.stackTraceToString()}")}
            dao.finishVm(id,token,"FAILED",e.message?:e.javaClass.simpleName)
        } finally {
            stopping=true
            bridge?.close();bridge=null
            qmp.delete()
            delay(250) // Let Room invalidate the main process before this process is disposed.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            delay(200)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    override fun onDestroy() {
        if(!stopping) {
            runBlocking(Dispatchers.IO) {current?.let {vm->app.database.dao().finishVm(vm.id,token,"STOPPED","Android stopped the QEMU service")}}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onDestroy()
    }
}
