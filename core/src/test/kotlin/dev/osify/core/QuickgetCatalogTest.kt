package dev.osify.core

import java.io.File
import kotlin.test.*

class QuickgetCatalogTest {
    @Test fun actualSnapshotHasDistinctVerifiedBootableImages() {
        val images=QuickgetCatalog.parse(File("../app/src/main/assets/quickget_data.json").readText(),Architecture.X86_64)
        assertTrue(images.size>=25)
        assertEquals(images.size,images.distinctBy {it.id}.size)
        assertTrue(images.any {it.name=="Debian"})
        assertTrue(images.any {it.name=="CachyOS"})
        assertTrue(images.none {it.name=="Alpine Linux"})
        images.forEach {image->
            if(image.flavor !in setOf("quickget-artixlinux","quickget-gentoo")) {assertTrue(image.sha256.matches(Regex("[a-f0-9]{64}")));TrustedUrls.check(image.url)} else assertTrue(image.url.startsWith("https://"))
            assertFalse(image.url.endsWith(".xz"))
        }
    }
    @Test fun catalogRejectsUntrustedAndUnverifiedSources() {
        val data="""[{"name":"test","pretty_name":"Test","releases":[{"iso":[{"web":{"url":"https://127.0.0.1/private.iso","checksum":"${"a".repeat(64)}"}}]}]}]"""
        assertTrue(QuickgetCatalog.parse(data,Architecture.X86_64).isEmpty())
        assertFails {QuickgetCatalog.parse("x".repeat(2*1024*1024+1),Architecture.X86_64)}
    }
}
