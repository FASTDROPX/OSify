package dev.osify.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DownloaderTest {
    private val content=ByteArray(160000) { (it%251).toByte() }
    private val hash=MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val directory=Files.createTempDirectory("osify-download-test").toFile()
    private val server=MockWebServer()
    private val downloader=ResumableDownloader { val uri=java.net.URI(it);require(uri.host==server.url("/").host && uri.port==server.port) }
    private fun spec()=CatalogImage("test","Test OS","1.0.0",Architecture.ARM64,"rootfs",server.url("/image").toString(),content.size.toLong(),hash)
    private fun response(bytes: ByteArray=content)=MockResponse().setHeader("ETag","\"version1\"").setBody(Buffer().write(bytes))
    @AfterTest fun cleanup() { server.shutdown();directory.deleteRecursively() }
    @Test fun fullDownloadIsVerifiedAndAtomicallyCompleted() {
        server.enqueue(response())
        var verifying=false
        val file=downloader.download(spec(),directory,"one",verifying={verifying=true})
        assertTrue(verifying);assertContentEquals(content,file.readBytes());assertFalse(directory.resolve("one.part").exists())
    }
    private fun pause(): Long {
        server.enqueue(response())
        var continueDownload=true
        assertFailsWith<TransferPaused> { downloader.download(spec(),directory,"one",{continueDownload},{if(it.bytes>32000) continueDownload=false}) }
        return directory.resolve("one.part").length()
    }
    @Test fun pauseAndRangeResumeValidateHeadersAndData() {
        val offset=pause()
        assertTrue(offset in 1 until content.size.toLong())
        server.enqueue(response(content.copyOfRange(offset.toInt(),content.size)).setResponseCode(206).setHeader("Content-Range","bytes $offset-${content.size-1}/${content.size}"))
        val final=downloader.download(spec(),directory,"one")
        server.takeRequest(1,TimeUnit.SECONDS)
        val resumed=server.takeRequest(1,TimeUnit.SECONDS)!!
        assertEquals("bytes=$offset-",resumed.getHeader("Range"));assertEquals("\"version1\"",resumed.getHeader("If-Range"))
        assertContentEquals(content,final.readBytes())
    }
    @Test fun ignoredRangeNeverDiscardsPartialData() {
        val offset=pause();server.enqueue(response())
        assertFailsWith<RestartRequired> { downloader.download(spec(),directory,"one") }
        assertEquals(offset,directory.resolve("one.part").length())
    }
    @Test fun changedEtagNeverAppendsUnrelatedBytes() {
        val offset=pause()
        server.enqueue(response(content.copyOfRange(offset.toInt(),content.size)).setResponseCode(206).setHeader("Content-Range","bytes $offset-${content.size-1}/${content.size}").setHeader("ETag","\"version2\""))
        assertFailsWith<RestartRequired> { downloader.download(spec(),directory,"one") }
        assertEquals(offset,directory.resolve("one.part").length())
    }
    @Test fun corruptPartialIsRejectedBeforeNetwork() {
        pause(); directory.resolve("one.part").writeBytes(byteArrayOf(5,6))
        assertFailsWith<RestartRequired> { downloader.download(spec(),directory,"one") }
        assertEquals(1,server.requestCount)
    }
    @Test fun uncheckpointedCrashTailIsDiscardedWithoutDiscardingVerifiedPrefix() {
        val offset=pause();directory.resolve("one.part").appendBytes(byteArrayOf(1,2,3))
        server.enqueue(response(content.copyOfRange(offset.toInt(),content.size)).setResponseCode(206).setHeader("Content-Range","bytes $offset-${content.size-1}/${content.size}"))
        assertContentEquals(content,downloader.download(spec(),directory,"one").readBytes())
    }
    @Test fun wrongFinalHashNeverCreatesUsableImage() {
        server.enqueue(response())
        assertFailsWith<IntegrityFailure> { downloader.download(spec().copy(sha256="0".repeat(64)),directory,"one") }
        assertFalse(directory.resolve("one.image").exists())
    }
    @Test fun invalidRangeIsRejected() {
        val offset=pause()
        server.enqueue(response(content.copyOfRange(offset.toInt(),content.size)).setResponseCode(206).setHeader("Content-Range","bytes 0-${content.size-1}/${content.size}"))
        assertFailsWith<RestartRequired> { downloader.download(spec(),directory,"one") }
    }
    @Test fun untrustedRedirectDoesNotOpenSecondHost() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","https://example.invalid/image"))
        assertFails { downloader.download(spec(),directory,"one") }
        assertFalse(directory.resolve("one.image").exists())
    }
    @Test fun weakEtagWithoutLastModifiedRequiresExplicitRestart() {
        server.enqueue(response().setHeader("ETag","W/\"weak\""))
        var keepGoing=true
        assertFailsWith<TransferPaused> {downloader.download(spec(),directory,"one",{keepGoing},{if(it.bytes>0)keepGoing=false})}
        assertFailsWith<RestartRequired> {downloader.download(spec(),directory,"one")}
        assertEquals(1,server.requestCount)
    }
    @Test fun weakEtagWithLastModifiedUsesDateAndValidatesIt() {
        val date="Mon, 02 Nov 2026 12:00:00 GMT"
        server.enqueue(response().setHeader("ETag","W/\"weak\"").setHeader("Last-Modified",date))
        var keepGoing=true
        assertFailsWith<TransferPaused> {downloader.download(spec(),directory,"one",{keepGoing},{if(it.bytes>0)keepGoing=false})}
        val offset=directory.resolve("one.part").length()
        server.enqueue(response(content.copyOfRange(offset.toInt(),content.size)).setResponseCode(206).setHeader("Content-Range","bytes $offset-${content.size-1}/${content.size}").setHeader("ETag","W/\"weak2\"").setHeader("Last-Modified",date))
        assertContentEquals(content,downloader.download(spec(),directory,"one").readBytes())
        server.takeRequest(1,TimeUnit.SECONDS)
        assertEquals(date,server.takeRequest(1,TimeUnit.SECONDS)!!.getHeader("If-Range"))
    }
    @Test fun pauseDuringVerificationDoesNotPublishCompletedImage() {
        server.enqueue(response())
        var continueWork=true
        assertFailsWith<TransferPaused> {downloader.download(spec(),directory,"one",{continueWork},verifying={continueWork=false})}
        assertFalse(directory.resolve("one.image").exists())
        assertEquals(content.size.toLong(),directory.resolve("one.part").length())
    }
}
