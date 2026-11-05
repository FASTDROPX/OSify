package dev.osify.core

import kotlinx.serialization.json.*
import java.net.URI

/** Public Quickget JSON API. Only direct, uncompressed ISO sources with SHA-256 are admitted. */
object QuickgetCatalog {
    const val ENDPOINT="https://github.com/quickemu-project/quickget_configs/releases/download/daily/quickget_data.json"
    private val json=Json {ignoreUnknownKeys=true}
    // Each admitted system has its real artwork bundled for offline display.
    private val branded=setOf("alma","antix","archcraft","archlinux","artixlinux","athenaos","bodhi","cachyos","chimeralinux","debian","devuan","edubuntu","fedora","freedos","garuda","gentoo","ghostbsd","kubuntu","lubuntu","nixos","ubuntu-budgie","ubuntu-cinnamon","ubuntu-kylin","ubuntu-mate","ubuntu-server","ubuntu-studio","ubuntu-unity","xubuntu")
    fun parse(text: String,architecture: Architecture): List<CatalogImage> {
        require(text.toByteArray().size<=2*1024*1024)
        val arch=if(architecture==Architecture.ARM64) "aarch64" else "x86_64"
        return json.parseToJsonElement(text).jsonArray.mapNotNull distro@ {entry->
            runCatching {
                val os=entry.jsonObject
                val key=os.getValue("name").jsonPrimitive.content
                if(key !in branded) return@distro null
                SupplementalDownloads.linux.firstOrNull {it.flavor=="quickget-$key" && it.architecture==architecture}?.let {return@distro it}
                val releases=os.getValue("releases").jsonArray.map {it.jsonObject}.filter {(it["arch"]?.jsonPrimitive?.content?:"x86_64")==arch}
                val candidates=releases.flatMap {release->release["iso"]?.jsonArray.orEmpty().mapNotNull source@ {source->
                    val web=source.jsonObject["web"]?.jsonObject?:return@source null
                    val url=web["url"]?.jsonPrimitive?.content?:return@source null
                    val checksum=web["checksum"]?.jsonPrimitive?.content?.lowercase().orEmpty()
                    val path=URI(url).path
                    if(!checksum.matches(Regex("[a-f0-9]{64}")) || web.containsKey("archive_format") || !(path.endsWith(".iso",true) || (URI(url).host=="sourceforge.net" && path.endsWith(".iso/download",true)))) return@source null
                    if(runCatching {TrustedUrls.check(url)}.isFailure) return@source null
                    Triple(release,url,checksum)
                }}
                val (release,originalUrl,hash)=candidates.firstOrNull()?:return@distro null
                val url=when(key) {
                    "archlinux"->originalUrl.replace("https://mirror.rackspace.com/archlinux/iso/","https://archive.archlinux.org/iso/")
                    "fedora"->originalUrl.replace("https://download.fedoraproject.org/","https://dl.fedoraproject.org/")
                    else->originalUrl
                }
                val homepage=os["homepage"]?.jsonPrimitive?.content?.takeIf {it.startsWith("https://")}?:url
                val name=os.getValue("pretty_name").jsonPrimitive.content.take(80)
                val version=listOfNotNull(release["release"]?.jsonPrimitive?.content,release["edition"]?.jsonPrimitive?.content).joinToString(" · ").take(80)
                CatalogImage("quickget-$key-$arch",name,version,architecture,"quickget-$key",url,0,hash,
                    license="See distribution license and Quickget catalog (GPL-3.0)",source=homepage,
                    summary=os["description"]?.jsonPrimitive?.content.orEmpty().take(500))
            }.getOrNull()
        }.distinctBy {it.id}
    }
}

/** Resolve the length without downloading the body. Redirects follow the same image URL policy. */
object ImageMetadata {
    fun size(url: String): Long {
        for(method in listOf("HEAD","GET")) {
            var target=url
            for(redirect in 0..5) {
                TrustedUrls.check(target)
                val c=java.net.URL(target).openConnection() as java.net.HttpURLConnection
                try {
                    c.connectTimeout=15000;c.readTimeout=15000;c.instanceFollowRedirects=false;c.requestMethod=method
                    c.setRequestProperty("Accept-Encoding","identity")
                    if(method=="GET") c.setRequestProperty("Range","bytes=0-0")
                    val code=c.responseCode
                    if(code in setOf(301,302,303,307,308)) {target=java.net.URL(java.net.URL(target),c.getHeaderField("Location")?:error("Missing redirect")).toString()}
                    else {
                        val bytes=if(code==206) c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?:0 else if(code==200) c.contentLengthLong else 0
                        if(bytes in 1..(64L*1024*1024*1024)) return bytes
                        break
                    }
                } finally {c.disconnect()}
            }
        }
        error("The source did not provide the image size. Open its official site or try again later.")
    }
}
