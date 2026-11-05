package dev.osify.core

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Explicit opt-in integration check; not part of the deterministic unit test suite. */
object OfficialNetworkSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val directory=File("core/build/official-network-smoke").also {it.mkdirs()}
        val report=StringBuilder("Official HTTPS integration smoke, ${java.time.Instant.now()}\nHost JVM transport; this is not an Android guest boot test.\n")
        for(arch in listOf(Architecture.ARM64,Architecture.X86_64)) {
            val code=if(arch==Architecture.ARM64) "aarch64" else "x86_64"
            val endpoint="https://${TrustedUrls.HOST}/alpine/latest-stable/releases/$code/latest-releases.yaml"
            TrustedUrls.check(endpoint)
            val c=URL(endpoint).openConnection() as HttpURLConnection
            val manifest=try {c.connectTimeout=15000;c.readTimeout=20000;c.instanceFollowRedirects=false;check(c.responseCode==200);c.inputStream.bufferedReader().use {it.readText()}} finally {c.disconnect()}
            val images=AlpineManifest.parse(manifest,arch)
            val image=images.single {it.flavor=="alpine-minirootfs"}
            val file=ResumableDownloader().download(image,directory,"smoke-$code")
            report.appendLine("${image.name} ${image.version} $arch: ${images.size} manifest entries, ${file.length()} bytes, verified SHA-256 ${ResumableDownloader.hash(file)}")
        }
        File(directory,"report.txt").writeText(report.toString());print(report)
    }
}
