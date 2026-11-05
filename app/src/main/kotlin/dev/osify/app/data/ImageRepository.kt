package dev.osify.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.*
import dev.osify.core.*
import dev.osify.app.download.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

class ImageRepository(private val context: Context,private val dao: OsifyDao,private val preferences: Preferences) {
    private val work=WorkManager.getInstance(context)
    private val directory=File(context.filesDir,"images").also { it.mkdirs() }
    suspend fun recoverFinishedWork() {
        dao.images().first().filter { !it.imported && it.state in setOf("QUEUED","RUNNING","VERIFYING") }.forEach { row ->
            val info=work.getWorkInfosForUniqueWork("image-${row.id}").get()
            if(info.isEmpty() || info.all { it.state.isFinished }) dao.pauseIfActive(row.id,"Android interrupted this transfer. Tap Resume; existing partial data will be checked first.")
        }
    }
    suspend fun queue(image: CatalogImage) {
        TrustedUrls.check(image.url)
        require(directory.usableSpace>image.bytes+32L*1024*1024) { "Not enough storage" }
        val existing=dao.images().first().any { it.url==image.url && it.state !in setOf("CANCELLED","FAILED") }
        require(!existing) { "Image is already in your library" }
        val id=UUID.randomUUID().toString()
        dao.putImage(ImageRecord(id,image.name,image.version,image.architecture.name,image.flavor,image.url,image.bytes,image.sha256))
        enqueue(id)
    }
    private suspend fun enqueue(id: String) {
        val prefs=preferences.flow.first()
        val request=OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("image" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if(prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR,30,java.util.concurrent.TimeUnit.SECONDS).addTag("image-$id").build()
        work.enqueueUniqueWork("image-$id",ExistingWorkPolicy.APPEND_OR_REPLACE,request)
    }
    suspend fun pause(id: String) { dao.pauseIfActive(id) }
    suspend fun resume(id: String) = ImageTransactions.mutex(id).withLock {
        val row=dao.image(id)?:return
        require(row.state in setOf("PAUSED","FAILED"))
        dao.state(id,"QUEUED");enqueue(id)
    }
    suspend fun restart(id: String) = ImageTransactions.mutex(id).withLock {
        val row=dao.image(id)?:return
        require(row.state in setOf("RESTART_REQUIRED","FAILED","PAUSED"))
        File(directory,"$id.part").delete();File(directory,"$id.checkpoint").delete();File(directory,"$id.checkpoint.tmp").delete();File(directory,"$id.image").delete()
        dao.progress(id,0);dao.state(id,"QUEUED");enqueue(id)
    }
    suspend fun delete(id: String) = ImageTransactions.mutex(id).withLock {
        require(dao.virtualMachines().first().none {it.imageId==id}) {"This ISO belongs to a virtual machine. Keep it while that machine is in Launcher."}

        val row=dao.image(id)?:return
        require(row.state !in setOf("RUNNING","VERIFYING","QUEUED")) { "Pause the transfer before deleting it" }
        work.cancelUniqueWork("image-$id").result.get()
        directory.listFiles()?.filter { it.name.startsWith("$id.") }?.forEach { it.delete() }
        dao.deleteImage(id)
    }
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        // Finish reconciliation before creating a new import that it could mistake for old work.
        (context.applicationContext as? dev.osify.app.OsifyApplication)?.startupRecovery?.join()
        val resolver=context.contentResolver
        var title="Imported image";var advertised=-1L
        resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst()) {
                title=cursor.getString(0)?.filter { !it.isISOControl() }?.take(100)?:title
                if(!cursor.isNull(1)) advertised=cursor.getLong(1)
            }
        }
        require(title.lowercase().let { it.endsWith(".iso") || it.endsWith(".img") || it.endsWith(".qcow2") || it.endsWith(".tar.gz") }) { "Choose an ISO, IMG, QCOW2 or tar.gz image" }
        if(advertised>0) require(directory.usableSpace>advertised+32L*1024*1024) { "Not enough storage" }
        val id=UUID.randomUUID().toString();val temp=File(directory,"$id.import");val final=File(directory,"$id.image")
        val row=ImageRecord(id,title,"User supplied","UNKNOWN","import","",advertised.coerceAtLeast(0),"",state="QUEUED",imported=true)
        dao.putImage(row)
        try {
            val digest=MessageDigest.getInstance("SHA-256");var bytes=0L
            resolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { output ->
                val buffer=ByteArray(65536)
                while(true) {
                    val n=input.read(buffer);if(n<0) break
                    if(bytes%(8L*1024*1024)<65536) require(directory.usableSpace>32L*1024*1024) { "Storage is nearly full" }
                    output.write(buffer,0,n);digest.update(buffer,0,n);bytes+=n
                    if(bytes%(1024*1024)<65536) dao.progress(id,bytes)
                }
            } }
            require(bytes>0 && (advertised<0 || bytes==advertised)) { "Empty or incomplete import" }
            Files.move(temp.toPath(),final.toPath(),StandardCopyOption.ATOMIC_MOVE)
            val detected=runCatching {BootMediaProbe.inspect(final).architecture}.getOrDefault("UNKNOWN")
            dao.putImage(row.copy(architecture=detected,expectedBytes=bytes,bytes=bytes,sha256=digest.digest().joinToString("") { "%02x".format(it) },state="COMPLETE",fileName=final.name))
        } catch(e: Exception) { temp.delete();dao.state(id,"FAILED",e.message.orEmpty());throw e }
    }
}
