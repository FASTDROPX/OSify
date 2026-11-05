package dev.osify.core

import kotlin.test.*
import java.io.File

class DomainTest {
    private val host=DeviceProfile(Architecture.ARM64,8,8L*1024*1024*1024,3L*1024*1024*1024,10L*1024*1024*1024,36)
    @Test fun prootDoesNotReserveVmMemory() {
        val recommendation=Tuner.recommend(host,Architecture.ARM64,EngineKind.PROOT,Profile.BALANCED)
        assertTrue(recommendation.supported); assertNull(recommendation.ramMiB); assertEquals(2,recommendation.virtualCores)
    }
    @Test fun incompatibleArchitectureAndPressureAreRejected() {
        assertFalse(Tuner.recommend(host,Architecture.X86_64,EngineKind.PROOT,Profile.SPEED).supported)
        assertFalse(Tuner.recommend(host.copy(availableRam=128L*1024*1024),Architecture.ARM64,EngineKind.PROOT,Profile.SPEED).supported)
        assertFalse(Tuner.recommend(host.copy(freeDisk=1),Architecture.ARM64,EngineKind.PROOT,Profile.SPEED).supported)
        assertFalse(Tuner.recommend(host,Architecture.ARM64,EngineKind.QEMU,Profile.SPEED).supported)
    }
    @Test fun presetsRoundTripAndRejectUnknownSchemaAndExecutableFlags() {
        val p=Preset(name="Моя Linux")
        assertEquals(p,Presets.decode(Presets.encode(p)))
        assertFails { Presets.decode("""{"schemaVersion":2}""") }
        assertFails { Presets.decode("""{"name":"x","unexpected":true}""") }
        assertFails { Presets.validate(p.copy(advancedFlags=listOf("-drive", "file=/data/private"))) }
        assertFails { Presets.validate(p.copy(name="\nname")) }
        assertFails { Presets.validate(p.copy(cores=0)) }
        assertFails { Presets.validate(p.copy(ramMiB=Int.MAX_VALUE)) }
    }
    @Test fun truthfulLifecycleNeverClaimsBootFromDispatch() {
        val install=SessionMachine.next(SessionState.IDLE,"install")
        assertEquals(SessionState.INSTALL_REQUESTED,install)
        val installed=SessionMachine.next(install,"installed")
        assertEquals(SessionState.LAUNCH_REQUESTED,SessionMachine.next(installed,"launch"))
        assertFails { SessionMachine.next(SessionState.IDLE,"exit") }
        assertEquals(SessionState.UNKNOWN,SessionMachine.next(SessionState.LAUNCH_REQUESTED,"lost"))
    }
    @Test fun terminalDownloadStatesCannotRestartSilently() {
        assertTrue(TransferMachine.allows(TransferState.RUNNING,TransferState.RESTART_REQUIRED))
        assertTrue(TransferMachine.allows(TransferState.VERIFYING,TransferState.COMPLETE))
        assertFalse(TransferMachine.allows(TransferState.RUNNING,TransferState.COMPLETE))
        assertFalse(TransferMachine.allows(TransferState.COMPLETE,TransferState.QUEUED))
        assertFalse(TransferMachine.allows(TransferState.CANCELLED,TransferState.RUNNING))
    }
    private fun manifest(file: String="alpine-minirootfs-3.22.2-aarch64.tar.gz",sha: String="a".repeat(64))="""
        ---
        - title: Mini root filesystem
          arch: aarch64
          version: 3.22.2
          flavor: alpine-minirootfs
          file: $file
          size: 3000000
          sha256: $sha
    """.trimIndent()
    @Test fun officialManifestHasStrictNamesAndHashes() {
        val image=AlpineManifest.parse(manifest(),Architecture.ARM64).single()
        assertEquals("https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.2-aarch64.tar.gz",image.url)
        assertFails { AlpineManifest.parse(manifest("../../evil"),Architecture.ARM64) }
        assertFails { AlpineManifest.parse(manifest(sha="missing"),Architecture.ARM64) }
        assertFails { AlpineManifest.parse(manifest()+"\n  size: 5",Architecture.ARM64) }
        assertFails { AlpineManifest.parse("x".repeat(262145),Architecture.ARM64) }
    }
    @Test fun trustedUrlsRejectSpoofingAndTraversal() {
        listOf("http://dl-cdn.alpinelinux.org/x","https://dl-cdn.alpinelinux.org.evil/x","https://evil@dl-cdn.alpinelinux.org/x","https://dl-cdn.alpinelinux.org/../x","https://dl-cdn.alpinelinux.org/%2e%2e/x","https://dl-cdn.alpinelinux.org:444/x").forEach { assertFails(it) { TrustedUrls.check(it) } }
    }
    @Test fun parsesActualOfficialSnapshotIncludingBareListMarkers() {
        for(arch in listOf(Architecture.ARM64,Architecture.X86_64)) {
            val name=if(arch==Architecture.ARM64) "aarch64" else "x86_64"
            val text=File("../app/src/main/assets/releases-$name.yaml").readText()
            val entries=AlpineManifest.parse(text,arch)
            assertEquals(setOf("alpine-minirootfs","alpine-standard","alpine-virt"),entries.map {it.flavor}.toSet())
            entries.forEach { assertEquals(arch,it.architecture);assertTrue(it.bytes>0);TrustedUrls.check(it.url) }
        }
    }
}
