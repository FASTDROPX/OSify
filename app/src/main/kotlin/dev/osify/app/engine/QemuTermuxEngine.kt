package dev.osify.app.engine

import android.app.*
import android.content.*
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.osify.app.*
import dev.osify.app.data.*
import dev.osify.core.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.UUID

class QemuTermuxEngine(private val app: OsifyApplication) {
    private val requestLock=Mutex()
    private val dao get()=app.database.dao()
    private fun checkAccess() {
        check(app.termux.installed()) {"Install and open Termux first"}
        check(app.termux.permissionGranted()) {"Grant Termux command permission in Launcher"}
    }
    suspend fun prepare()=requestLock.withLock {
        app.startupRecovery.join()
        check(dao.virtualMachine("runtime")?.state!="REQUESTED") {"Preparation already in progress"}
        checkAccess()
        send(VirtualMachine("runtime","Runtime","RUNTIME"),"prepare",VmCommands.prepare)
    }
    suspend fun launch(vm: VirtualMachine,bootIso: Boolean)=requestLock.withLock {
        app.startupRecovery.join()
        check(dao.virtualMachine(vm.id)?.state!="REQUESTED") {"A command is already pending"}
        checkAccess()
        check(app.preferences.flow.first().externalEngineConsent) {"Enable Termux integration first"}
        check(dao.virtualMachines().first().none {it.state=="REQUESTED" && it.operation=="launch"}) {"Wait for the other ISO transfer to finish"}
        val image=if(bootIso) dao.image(vm.imageId)?:error("ISO file is missing") else null
        if(image!=null) check(image.state=="COMPLETE" && image.sha256.matches(Regex("[a-f0-9]{64}"))) {"Import or finish downloading the ISO first"}
        val root=File(app.filesDir,"images").canonicalFile
        val file=image?.let {File(root,it.fileName).canonicalFile}
        if(file!=null) check(file.parentFile==root && file.isFile) {"Image is unavailable"}
        val url=if(file!=null) app.imageBridge.publish(file) else "http://127.0.0.1:1/unused"
        if(file!=null) ContextCompat.startForegroundService(app,Intent(app,ImageBridgeService::class.java))
        try {send(vm,"launch",VmCommands.launch(vm.id,url,image?.sha256?:"0".repeat(64),vm.ramMiB,vm.cores,vm.diskGiB,vm.port,vm.password,vm.firmware,bootIso))}
        catch(e: Exception) {app.imageBridge.close();app.stopService(Intent(app,ImageBridgeService::class.java));throw e}
    }
    suspend fun control(vm: VirtualMachine,action: String)=requestLock.withLock {app.startupRecovery.join();checkAccess();check(dao.virtualMachine(vm.id)?.state!="REQUESTED") {"A command is already pending"};send(vm,action,VmCommands.control(vm.id,action))}
    suspend fun resetPending(id: String)=requestLock.withLock {
        val vm=dao.virtualMachine(id)?:return@withLock
        check(vm.state=="REQUESTED")
        dao.finishVm(id,vm.token,"UNKNOWN","Tracking reset. Check Termux; this does not stop any process.")
        if(vm.operation=="launch") {app.imageBridge.close();app.stopService(Intent(app,ImageBridgeService::class.java))}
    }
    suspend fun remove(vm: VirtualMachine) {check(dao.deleteStoppedVm(vm.id)==1) {"Stop the machine before removing it"}}
    private suspend fun send(vm: VirtualMachine,operation: String,script: String) {
        check(app.preferences.flow.first().externalEngineConsent) {"Enable Termux integration first"}
        val token=UUID.randomUUID().toString()
        val callback=Intent(app,VmResultReceiver::class.java).setData(Uri.parse("osify://vm/${vm.id}/$token"))
        val pending=PendingIntent.getBroadcast(app,token.hashCode(),callback,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        dao.virtualMachine(vm.copy(state="REQUESTED",operation=operation,token=token,log="Request sent to Termux. Waiting for its result."))
        try {
            val intent=Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/bash")
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",script))
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
                .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","OSify / ${vm.name}")
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",pending)
            check(app.startService(intent)!=null) {"Termux command service unavailable"}
        } catch(e: Exception) {pending.cancel();dao.finishVm(vm.id,token,"FAILED",e.message.orEmpty());throw e}
    }
    suspend fun create(image: ImageRecord,name: String,guest: String,ram: Int,cores: Int,disk: Int,firmware: String) {
        app.startupRecovery.join()
        require(name.isNotBlank() && name.length<=80)
        require(firmware in setOf("BIOS","UEFI"))
        val physical=app.profiler.snapshot().profile.totalRam
        require(physical<=0 || ram.toLong()*1048576<=physical/2) {"VM RAM must not exceed half of physical memory"}
        require(guest in setOf("LINUX","WINDOWS"))
        require(ram in 256..4096 && cores in 1..4 && disk in 2..128)
        if(image!=null) check(image.state=="COMPLETE")
        val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
        val random=SecureRandom();val alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val password=(1..8).map {alphabet[random.nextInt(alphabet.length)]}.joinToString("")
        dao.virtualMachine(VirtualMachine(UUID.randomUUID().toString(),name,guest,image.id,ram,cores,disk,port,password,firmware=firmware))
    }
}

class VmResultReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val segments=intent.data?.pathSegments?:return
        if(segments.size!=2) return
        val (id,token)=segments
        val result=intent.getBundleExtra("result")?:return
        val pending=goAsync();val app=context.applicationContext as OsifyApplication
        app.scope.launch {
            try {
                val dao=app.database.dao();val vm=dao.virtualMachine(id)?:return@launch
                if(vm.token!=token) return@launch
                val code=result.getInt("exitCode",Int.MIN_VALUE);val err=result.getInt("err",-1)
                val success=code==0 && err==-1
                val state=if(!success) "FAILED" else when(vm.operation) {"prepare"->"PREPARED";"launch","resume"->"DISPLAY_AVAILABLE";"pause"->"PAUSED";"force"->"STOPPED";"shutdown"->"SHUTDOWN_REQUESTED";else->"UNKNOWN"}
                val log="Termux result: exit=$code, err=$err\n"+listOf(result.getString("errmsg"),result.getString("stdout"),result.getString("stderr")).filterNotNull().joinToString("\n").takeLast(12000)
                dao.finishVm(id,token,state,log)
                if(vm.operation=="launch") {app.imageBridge.close();app.stopService(Intent(app,ImageBridgeService::class.java))}
            } finally {pending.finish()}
        }
    }
}

class ImageBridgeService: Service() {
    override fun onBind(intent: Intent?)=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("iso-copy","OSify ISO transfer",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val cancel=PendingIntent.getService(this,1,Intent(this,ImageBridgeService::class.java).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"iso-copy").setSmallIcon(R.drawable.ic_download).setContentTitle("OSify").setContentText(getString(R.string.runtime_preparing)).setContentIntent(open).setOngoing(true).addAction(0,getString(R.string.runtime_cancel_transfer),cancel).build()
        if(android.os.Build.VERSION.SDK_INT>=29) startForeground(914,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(914,notification)
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {if(intent?.action=="cancel") stopSelf();return START_NOT_STICKY}
    override fun onTimeout(startId: Int,fgsType: Int) {stopSelf()}
    override fun onDestroy() {(application as OsifyApplication).imageBridge.close();super.onDestroy()}
}
