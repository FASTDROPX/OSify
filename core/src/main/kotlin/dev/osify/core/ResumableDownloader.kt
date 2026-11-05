package dev.osify.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class RestartRequired(message: String): IOException(message)
class TransferPaused: IOException("Transfer paused; verified partial data retained")
class IntegrityFailure(message: String): IOException(message)
@Serializable data class Checkpoint(val bytes: Long, val sha256: String, val etag: String?, val lastModified: String?)
data class TransferProgress(val bytes: Long, val total: Long)

/** Blocking transport, run on IO. Never overwrites a partial response on a failed resume. */
class ResumableDownloader(private val validateUrl: (String) -> Unit = { TrustedUrls.check(it); Unit }) {
    private val json = Json { encodeDefaults = true }
    fun download(
        image: CatalogImage, directory: File, id: String,
        shouldContinue: () -> Boolean = { true },
        progress: (TransferProgress) -> Unit = {},
        verifying: () -> Unit = {}
    ): File {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,100}")))
        require(image.bytes > 0 && ImageChecksums.valid(image))
        validateUrl(image.url)
        directory.mkdirs()
        val partial = File(directory, "$id.part")
        val sidecar = File(directory, "$id.checkpoint")
        val completed = File(directory, "$id.image")
        var checkpoint: Checkpoint? = null
        if(partial.exists() && partial.length() > 0) {
            checkpoint = try { json.decodeFromString<Checkpoint>(sidecar.readText()) } catch(e: Exception) { throw RestartRequired("Interrupted partial data has no valid checkpoint. Confirm a fresh download.") }
            require(checkpoint.bytes >= 0 && checkpoint.bytes <= image.bytes)
            if(partial.length() < checkpoint.bytes || hash(partial, checkpoint.bytes) != checkpoint.sha256) throw RestartRequired("Partial file integrity check failed. Confirm a fresh download.")
            RandomAccessFile(partial, "rw").use { it.setLength(checkpoint.bytes) }
        }
        val offset = partial.takeIf { it.exists() }?.length() ?: 0L
        if(directory.usableSpace < (image.bytes-offset) + 32L*1024*1024) throw IOException("Not enough storage; keep at least 32 MiB free.")
        if(offset == image.bytes) return finish(partial, completed, sidecar, image, verifying,shouldContinue)
        val strongEtag=checkpoint?.etag?.takeIf { it.startsWith('"') && it.endsWith('"') && it.length in 2..202 && it.none(Char::isISOControl) }
        val modifiedValidator=checkpoint?.lastModified?.takeIf { runCatching {java.time.ZonedDateTime.parse(it,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)}.isSuccess }
        if(offset > 0 && strongEtag==null && modifiedValidator==null) throw RestartRequired("Server supplied no reliable resume validator. Confirm a fresh download.")
        var connection: HttpURLConnection? = null
        var etag = checkpoint?.etag
        var modified = checkpoint?.lastModified
        var received = offset
        var bodyStarted = false
        try {
            if(!shouldContinue()) throw TransferPaused()
            var target = image.url
            for(redirect in 0..5) {
                validateUrl(target)
                val c = URL(target).openConnection() as HttpURLConnection
                connection = c
                c.connectTimeout = 20_000; c.readTimeout = 30_000; c.instanceFollowRedirects = false
                c.setRequestProperty("Accept-Encoding", "identity")
                c.setRequestProperty("User-Agent", "OSify/0.1")
                if(offset > 0) {
                    c.setRequestProperty("Range", "bytes=$offset-")
                    c.setRequestProperty("If-Range", strongEtag ?: modifiedValidator)
                }
                val code = c.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    if(redirect == 5) throw IOException("Too many redirects")
                    target = URL(URL(target), c.getHeaderField("Location") ?: throw IOException("Missing redirect location")).toString()
                    c.disconnect(); continue
                }
                if(offset > 0 && code == 200) throw RestartRequired("Server changed the image or cannot resume. Partial data kept; confirm a fresh download.")
                if(code !in setOf(200,206)) throw IOException("HTTP $code; partial data retained")
                if(code == 206) {
                    val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(c.getHeaderField("Content-Range") ?: "") ?: throw RestartRequired("Invalid Content-Range")
                    val (start,end,total) = range.destructured
                    if(start.toLong() != offset || total.toLong() != image.bytes || end.toLong() != image.bytes-1) throw RestartRequired("Unexpected resume range")
                } else if(offset != 0L) throw RestartRequired("Resume unsupported")
                val length = c.contentLengthLong
                if(length >= 0 && length != image.bytes-offset) throw IntegrityFailure("Server size differs from the trusted manifest")
                val responseEtag = c.getHeaderField("ETag")
                val responseModified = c.getHeaderField("Last-Modified")
                if(offset > 0 && ((strongEtag != null && strongEtag != responseEtag) || (strongEtag==null && modifiedValidator != responseModified))) throw RestartRequired("Resume validator changed")
                etag = responseEtag; modified = responseModified
                val digest = MessageDigest.getInstance("SHA-256")
                if(offset > 0) partial.inputStream().use { input -> val b=ByteArray(65536); while(true) { val n=input.read(b); if(n<0) break; digest.update(b,0,n) } }
                var savedAt = offset
                bodyStarted = true
                c.inputStream.use { input ->
                    java.io.FileOutputStream(partial, offset > 0).use { output ->
                        val buffer = ByteArray(65536)
                        while(true) {
                            if(!shouldContinue()) throw TransferPaused()
                            val n = input.read(buffer); if(n < 0) break
                            if(received+n > image.bytes) throw IntegrityFailure("Response exceeds expected size")
                            output.write(buffer,0,n); digest.update(buffer,0,n); received += n
                            progress(TransferProgress(received,image.bytes))
                            if(received-savedAt >= 8L*1024*1024) {
                                output.flush()
                                val current = try { (digest.clone() as MessageDigest).digest().hex() } catch(_: CloneNotSupportedException) { null }
                                if(current != null) { save(sidecar,Checkpoint(received,current,etag,modified)); savedAt=received }
                            }
                        }
                    }
                }
                if(received != image.bytes) throw IOException("Incomplete response; partial data retained")
                return finish(partial,completed,sidecar,image,verifying,shouldContinue)
            }
            throw IOException("Redirect failed")
        } catch(e: Exception) {
            if(bodyStarted && partial.exists()) save(sidecar, Checkpoint(partial.length(),hash(partial),etag,modified))
            throw e
        } finally { connection?.disconnect() }
    }
    private fun finish(part: File, final: File, sidecar: File, image: CatalogImage, verifying: () -> Unit,shouldContinue: ()->Boolean): File {
        if(!shouldContinue()) throw TransferPaused()
        verifying()
        if(part.length() != image.bytes || !ImageChecksums.matches(part,image)) throw IntegrityFailure("Image checksum mismatch. Image is quarantined and cannot be used; restart explicitly.")
        if(!shouldContinue()) throw TransferPaused()
        Files.move(part.toPath(),final.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        sidecar.delete()
        return final
    }
    private fun save(file: File, checkpoint: Checkpoint) {
        val tmp=File(file.parentFile,file.name+".tmp")
        tmp.writeText(json.encodeToString(checkpoint))
        Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    companion object {
        fun hash(file: File, limit: Long = file.length(), algorithm: String = "SHA-256"): String {
            val digest=MessageDigest.getInstance(algorithm)
            file.inputStream().use { input ->
                var remaining=limit; val buffer=ByteArray(65536)
                while(remaining>0) { val n=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt()); if(n<0) throw IntegrityFailure("Truncated file"); digest.update(buffer,0,n); remaining-=n }
            }
            return digest.digest().hex()
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    }
}
