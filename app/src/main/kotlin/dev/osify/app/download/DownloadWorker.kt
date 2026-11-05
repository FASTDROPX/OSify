package dev.osify.app.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import dev.osify.app.MainActivity
import dev.osify.app.OsifyApplication
import dev.osify.app.R
import dev.osify.app.data.ImageTransactions
import dev.osify.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File

class DownloadWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    private val app=context.applicationContext as OsifyApplication
    private val dao=app.database.dao()
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id=inputData.getString("image") ?: return@withContext Result.failure()
        val prefs=app.preferences.flow.first()
        ImageTransactions.mutex(id).withLock { DownloadSlots.withPermit(prefs.concurrent) { transfer(id) } }
    }
    private suspend fun transfer(id: String): Result {
        var row=dao.image(id) ?: return Result.success()
        if(row.imported || row.state !in setOf("QUEUED","RUNNING","VERIFYING")) return Result.success()
        dao.state(id,"RUNNING")
        return try {
            setForeground(notification(id,row.title))
            if(row.expectedBytes<=0) {
                val size=ImageMetadata.size(row.url)
                val latest=dao.image(id)?:return Result.success()
                if(latest.state!="RUNNING") return Result.success()
                row=latest.copy(expectedBytes=size)
                dao.putImage(row)
            }
            val image=CatalogImage(id,row.title,row.version,Architecture.valueOf(row.architecture),row.flavor,row.url,row.expectedBytes,row.sha256)
            val dir=File(app.filesDir,"images").also { it.mkdirs() }
            val final=File(dir,"$id.image")
            // Recover death between atomic rename and Room commit.
            if(final.exists() && final.length()==row.expectedBytes && ImageChecksums.matches(final,image)) {
                dao.putImage(row.copy(sha256=ResumableDownloader.hash(final),bytes=row.expectedBytes,state="COMPLETE",fileName=final.name,error=""))
                return Result.success()
            }
            if(final.exists()) throw RestartRequired("Recovered completed file is corrupt. Confirm a fresh download; no automatic restart was performed.")
            var lastStateCheck=0L; var keepGoing=true; var lastProgress=0L
            val result=ResumableDownloader().download(image,dir,id,
                shouldContinue={
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(now-lastStateCheck>300) { lastStateCheck=now; keepGoing=runBlocking { dao.image(id)?.state in setOf("RUNNING","VERIFYING") } }
                    !isStopped && keepGoing
                },progress={ p ->
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(now-lastProgress>500) { lastProgress=now;runBlocking { dao.progress(id,p.bytes) } }
                },verifying={ runBlocking { dao.state(id,"VERIFYING") } })
            dao.putImage(row.copy(sha256=ResumableDownloader.hash(result),bytes=result.length(),state="COMPLETE",fileName=result.name,error=""))
            Result.success()
        } catch(e: TransferPaused) {
            if(dao.image(id)?.state in setOf("RUNNING","VERIFYING")) dao.state(id,"PAUSED")
            Result.success()
        } catch(e: RestartRequired) {
            dao.state(id,"RESTART_REQUIRED",e.message.orEmpty()); Result.success()
        } catch(e: Exception) {
            val current=dao.image(id)?.state
            if(current !in setOf("CANCELLED","PAUSED","QUEUED")) dao.state(id,"FAILED",e.message?.take(500)?:e.javaClass.simpleName)
            Result.success()
        }
    }
    private fun notification(id: String,title: String): ForegroundInfo {
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("images",applicationContext.getString(R.string.download_channel),NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause=PendingIntent.getBroadcast(applicationContext,id.hashCode(),Intent(applicationContext,PauseTransferReceiver::class.java).putExtra("image",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(applicationContext,"images").setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title).setContentText(applicationContext.getString(R.string.download_active)).setContentIntent(open)
            .setOngoing(true).setProgress(0,0,true).addAction(0,applicationContext.getString(R.string.cancel),pause).build()
        return if(android.os.Build.VERSION.SDK_INT>=29) ForegroundInfo(id.hashCode() and 0x7fffffff,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode() and 0x7fffffff,notification)
    }
}

class PauseTransferReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val id=intent.getStringExtra("image")?:return
        val pending=goAsync(); val app=context.applicationContext as OsifyApplication
        app.scope.launch { try { app.database.dao().pauseIfActive(id) } finally { pending.finish() } }
    }
}
