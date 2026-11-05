package dev.osify.app.data

import android.content.Context
import dev.osify.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class CatalogResult(val images: List<CatalogImage>,val fetchedAt: Long,val bundled: Boolean)
class CatalogRepository(private val context: Context,private val dao: OsifyDao) {
    private val json=Json { encodeDefaults=true }
    suspend fun load(arch: Architecture, refresh: Boolean=false): CatalogResult = withContext(Dispatchers.IO) {
        val code=if(arch==Architecture.X86_64) "x86_64" else "aarch64"
        val selected=if(arch==Architecture.UNKNOWN) Architecture.ARM64 else arch
        val cached=dao.cache("quickget-v3-${selected.name}")
        if(!refresh && cached!=null) return@withContext CatalogResult(json.decodeFromString(cached.json),cached.fetchedAt,false)
        if(!refresh) return@withContext CatalogResult(AlpineManifest.parse(context.assets.open("releases-$code.yaml").bufferedReader().use { it.readText() },selected).filter {it.flavor=="alpine-virt"}+QuickgetCatalog.parse(context.assets.open("quickget_data.json").bufferedReader().use {it.readText()},selected),0,true)
        val url="https://${TrustedUrls.HOST}/alpine/latest-stable/releases/$code/latest-releases.yaml"
        TrustedUrls.check(url)
        val c=URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout=15000; c.readTimeout=20000; c.instanceFollowRedirects=false
            check(c.responseCode==200) { "Catalog HTTP ${c.responseCode}" }
            val data=c.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(output.size()<=256*1024) { val n=input.read(buffer,0,minOf(buffer.size,256*1024+1-output.size())); if(n<0) break; output.write(buffer,0,n) }
                output.toByteArray()
            }
            require(data.size<=256*1024) { "Manifest too large" }
            val alpine=AlpineManifest.parse(data.toString(Charsets.UTF_8),selected).filter {it.flavor=="alpine-virt"}
            var target=QuickgetCatalog.ENDPOINT
            var api: String?=null
            for(redirect in 0..5) {
                TrustedUrls.check(target)
                val request=URL(target).openConnection() as HttpURLConnection
                try {
                    request.connectTimeout=15000;request.readTimeout=20000;request.instanceFollowRedirects=false
                    if(request.responseCode in setOf(301,302,303,307,308)) {target=URL(URL(target),request.getHeaderField("Location")?:error("Missing API redirect")).toString();continue}
                    check(request.responseCode==200) {"Catalog API HTTP ${request.responseCode}"}
                    val data=request.inputStream.use {input->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(output.size()<=2*1024*1024) {val n=input.read(buffer,0,minOf(buffer.size,2*1024*1024+1-output.size()));if(n<0) break;output.write(buffer,0,n)};output.toByteArray()}
                    require(data.size<=2*1024*1024)
                    api=data.toString(Charsets.UTF_8);break
                } finally {request.disconnect()}
            }
            val images=alpine+QuickgetCatalog.parse(api?:error("Catalog API redirects exceeded"),selected)
            val time=System.currentTimeMillis()
            dao.cache(CatalogCache("quickget-v3-${selected.name}",json.encodeToString(images),time))
            CatalogResult(images,time,false)
        } finally { c.disconnect() }
    }
}
