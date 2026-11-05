package dev.osify.app.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import dev.osify.app.OsifyApplication
import dev.osify.app.data.*
import dev.osify.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/** External protocol adapter; no Termux/PRoot source or native binary is bundled. */
class TermuxEngine(private val context: Context,private val dao: OsifyDao,private val preferences: Preferences,scope: CoroutineScope): GuestEngine {
    override val kind=EngineKind.PROOT
    override val status: StateFlow<EngineReport> = dao.sessionFlow().map { row ->
        EngineReport(row?.state?.let { SessionState.valueOf(it) }?:SessionState.IDLE,"External Termux adapter · device validation pending",row?.log.orEmpty())
    }.stateIn(scope,SharingStarted.Eagerly,EngineReport(SessionState.IDLE,"External engine · untested"))
    fun installed(): Boolean = try { context.packageManager.getPackageInfo("com.termux",0);true } catch(_: PackageManager.NameNotFoundException) { false }
    fun permissionGranted()=ContextCompat.checkSelfPermission(context,"com.termux.permission.RUN_COMMAND")==PackageManager.PERMISSION_GRANTED
    override fun capabilities()=EngineCapabilities(installed() && permissionGranted(),true,false,false,"Experimental external PRoot adapter. Not validated on an Android device. PRoot shares the host kernel. Sessions run in Termux, not in OSify.")
    override suspend fun install(): Result<Unit> = send("install",arrayOf("install","alpine"),true)
    override suspend fun launch(): Result<Unit> = send("launch",arrayOf("login","alpine"),false)
    private suspend fun send(operation: String,arguments: Array<String>,background: Boolean): Result<Unit> = runCatching {
        check(preferences.flow.first().externalEngineConsent) { "Read and enable the external engine disclosure first" }
        check(installed() && permissionGranted()) { "Install Termux and grant its optional command permission" }
        val prior=dao.session()?:EngineSession()
        val next=SessionMachine.next(SessionState.valueOf(prior.state),operation)
        val token=UUID.randomUUID().toString()
        val callback=Intent(context,TermuxResultReceiver::class.java).setAction("dev.osify.TERMUX_RESULT").setData(Uri.parse("osify://result/$token"))
        val result=PendingIntent.getBroadcast(context,token.hashCode(),callback,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        dao.session(prior.copy(state=next.name,token=token,operation=operation,log="Request sent to Termux. No successful boot is implied. Awaiting exit callback."))
        try {
            val request=Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/proot-distro")
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arguments)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND",background)
                .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","OSify · Alpine Linux")
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",result)
            check(context.startService(request)!=null) { "Termux command service unavailable" }
            if(!background) context.packageManager.getLaunchIntentForPackage("com.termux")?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } catch(e: Exception) { result.cancel();dao.session(prior.copy(state="FAILED",log=e.message.orEmpty()));throw e }
    }
    override suspend fun stop(force: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("Session is owned by Termux. Exit the guest shell there; OSify cannot safely stop external sessions."))
    suspend fun markUnknown() {
        val row=dao.session()?:return
        dao.session(row.copy(state="UNKNOWN",token="",operation="",log="Callback lost or session status unknown. Check Termux before trying again; no live RAM state can be restored by OSify."))
    }
    suspend fun resetTracking() {
        dao.session(EngineSession(log="Tracking reset by user. No external process was stopped."))
    }
}

class TermuxResultReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val token=intent.data?.lastPathSegment?:return
        val result=intent.getBundleExtra("result")?:return
        val pending=goAsync();val app=context.applicationContext as OsifyApplication
        app.scope.launch {
            try {
                val dao=app.database.dao();val row=dao.session()?:return@launch
                if(row.token!=token || row.operation !in setOf("install","launch")) return@launch
                val internalError=result.getInt("err",-1)
                val code=result.getInt("exitCode",Int.MIN_VALUE)
                val success=internalError == -1 && code == 0
                val newState=if(success) SessionMachine.next(SessionState.valueOf(row.state),if(row.operation=="install") "installed" else "exit") else SessionState.FAILED
                val log="Termux callback: exit=$code, err=$internalError\n"+listOf(result.getString("errmsg"),result.getString("stdout"),result.getString("stderr")).filterNotNull().joinToString("\n").takeLast(16000)
                dao.finishSession(token,newState.name,log)
            } finally { pending.finish() }
        }
    }
}

class UnavailableQemuEngine: GuestEngine {
    override val kind=EngineKind.QEMU
    override val status: StateFlow<EngineReport> = MutableStateFlow(EngineReport(SessionState.UNAVAILABLE,"QEMU binary, firmware and display backend are not packaged or validated."))
    override fun capabilities()=EngineCapabilities(false,false,false,false,status.value.message)
    private fun unavailable()=Result.failure<Unit>(UnsupportedOperationException(status.value.message))
    override suspend fun install()=unavailable()
    override suspend fun launch()=unavailable()
    override suspend fun stop(force: Boolean)=unavailable()
}
