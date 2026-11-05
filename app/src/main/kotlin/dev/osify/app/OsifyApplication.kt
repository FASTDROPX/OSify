package dev.osify.app

import android.app.Application
import androidx.room.Room
import dev.osify.app.data.*
import dev.osify.app.device.DeviceProfiler
import dev.osify.app.engine.TermuxEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

open class OsifyApplication: Application(), androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration
        get()=androidx.work.Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val imageBridge=dev.osify.core.LocalImageBridge()
    val guestTools by lazy {dev.osify.app.engine.GuestTools(this)}
    val vmEngine by lazy { dev.osify.app.engine.NativeQemuEngine(this) }
    val frameStats=dev.osify.app.device.UiFrameStats()
    val motionScale=AppMotionScale()
    protected open val multiProcessDatabase: Boolean = true
    val database by lazy { Room.databaseBuilder(this,OsifyDatabase::class.java,"osify.db").addMigrations(VM_MIGRATION,dev.osify.app.data.HARDWARE_MIGRATION).apply {if(multiProcessDatabase) enableMultiInstanceInvalidation()}.build() }
    val preferences by lazy { Preferences(this) }
    val profiler by lazy { DeviceProfiler(this) }
    val catalog by lazy { CatalogRepository(this,database.dao()) }
    val termux by lazy { TermuxEngine(this,database.dao(),preferences,scope) }
    lateinit var startupRecovery: kotlinx.coroutines.Job
        private set
    override fun onCreate() {
        super.onCreate()
        startupRecovery=scope.launch {
            val processName=if(android.os.Build.VERSION.SDK_INT>=28) Application.getProcessName() else getSystemService(android.app.ActivityManager::class.java).runningAppProcesses?.firstOrNull {it.pid==android.os.Process.myPid()}?.processName.orEmpty()
            if(processName.endsWith(":qemu")) return@launch
            val running=getSystemService(android.app.ActivityManager::class.java).getRunningServices(50).any {it.service.className=="dev.osify.app.engine.QemuService"}
            val dao=database.dao()
            (dao.virtualMachines().first()+listOfNotNull(dao.virtualMachine("runtime"))).filter {!running && it.state in setOf("REQUESTED","DISPLAY_AVAILABLE","PAUSED","SHUTDOWN_REQUESTED")}.forEach {
                dao.virtualMachine(it.copy(state="STOPPED",token="",operation="",log="The previous engine process is no longer running."))
            }
            database.dao().images().first().filter { it.imported && it.state!="COMPLETE" && it.state!="FAILED" }.forEach {
                database.dao().state(it.id,"FAILED","Import was interrupted. Choose the original file again; no completed image was claimed.")
            }
        }
    }
}
