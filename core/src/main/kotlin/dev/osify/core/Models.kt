package dev.osify.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable enum class Architecture { ARM64, X86_64, X86, UNKNOWN }
@Serializable enum class EngineKind { PROOT, QEMU }
@Serializable enum class Profile { SPEED, BALANCED, COMFORT }
@Serializable enum class TransferState { QUEUED, RUNNING, PAUSED, VERIFYING, COMPLETE, FAILED, RESTART_REQUIRED, CANCELLED }
@Serializable enum class SessionState { UNAVAILABLE, IDLE, INSTALL_REQUESTED, INSTALLED_EXTERNAL, LAUNCH_REQUESTED, RETURNED, FAILED, UNKNOWN }

object TransferMachine {
    fun allows(from: TransferState, to: TransferState): Boolean = to in when(from) {
        TransferState.QUEUED -> setOf(TransferState.RUNNING, TransferState.PAUSED, TransferState.CANCELLED, TransferState.FAILED)
        TransferState.RUNNING -> setOf(TransferState.PAUSED, TransferState.VERIFYING, TransferState.FAILED, TransferState.CANCELLED, TransferState.RESTART_REQUIRED)
        TransferState.VERIFYING -> setOf(TransferState.COMPLETE, TransferState.FAILED, TransferState.PAUSED, TransferState.CANCELLED)
        TransferState.PAUSED, TransferState.FAILED -> setOf(TransferState.QUEUED, TransferState.CANCELLED)
        TransferState.RESTART_REQUIRED -> setOf(TransferState.QUEUED, TransferState.CANCELLED)
        TransferState.COMPLETE, TransferState.CANCELLED -> emptySet()
    }
}

object SessionMachine {
    fun next(state: SessionState, action: String): SessionState = when(action) {
        "install" -> { require(state in setOf(SessionState.IDLE, SessionState.RETURNED, SessionState.FAILED, SessionState.UNKNOWN)); SessionState.INSTALL_REQUESTED }
        "installed" -> { require(state == SessionState.INSTALL_REQUESTED); SessionState.INSTALLED_EXTERNAL }
        "launch" -> { require(state in setOf(SessionState.INSTALLED_EXTERNAL, SessionState.RETURNED)); SessionState.LAUNCH_REQUESTED }
        "exit" -> { require(state == SessionState.LAUNCH_REQUESTED); SessionState.RETURNED }
        "failure" -> SessionState.FAILED
        "lost" -> SessionState.UNKNOWN
        else -> error("Unknown lifecycle event")
    }
}

data class DeviceProfile(val architecture: Architecture, val cores: Int, val totalRam: Long, val availableRam: Long, val freeDisk: Long, val api: Int)
data class Recommendation(val supported: Boolean, val virtualCores: Int, val ramMiB: Int?, val reasons: List<String>)

object Tuner {
    fun recommend(device: DeviceProfile, architecture: Architecture, engine: EngineKind, profile: Profile): Recommendation {
        val reasons = mutableListOf<String>()
        val native = architecture == device.architecture && architecture != Architecture.UNKNOWN
        if (engine == EngineKind.PROOT) {
            reasons += "PRoot shares Android's kernel; it does not reserve VM memory."
            if (!native) reasons += "This PRoot adapter requires a matching host architecture."
        } else {
            reasons += "QEMU is not packaged or validated in this build."
            if (!native) reasons += "Cross-architecture emulation can be extremely slow."
        }
        if (device.availableRam < 768L * 1024 * 1024) reasons += "Low available host memory."
        if (device.freeDisk < 512L * 1024 * 1024) reasons += "Less than 512 MiB storage available."
        val budget = minOf(device.availableRam / 3, device.totalRam / 4) / (1024 * 1024)
        val ram = if(engine == EngineKind.QEMU) budget.coerceAtMost(4096).toInt().takeIf { it>=64 } else null
        val cores = minOf(if (profile == Profile.SPEED) 4 else 2, maxOf(1, device.cores / 2))
        return Recommendation(native && engine == EngineKind.PROOT && device.freeDisk >= 512L*1024*1024 && device.availableRam >= 768L*1024*1024, cores, ram, reasons)
    }
}

@Serializable data class Preset(
    val schemaVersion: Int = 1, val name: String = "Balanced", val architecture: Architecture = Architecture.ARM64,
    val engine: EngineKind = EngineKind.PROOT, val profile: Profile = Profile.BALANCED,
    val cores: Int = 2, val ramMiB: Int = 512, val diskGiB: Int = 4,
    val diskFormat: String = "qcow2", val bootOrder: String = "disk", val firmware: String = "default",
    val width: Int = 1280, val height: Int = 720, val graphics: String = "software",
    val input: String = "tablet", val audio: Boolean = false, val network: Boolean = false,
    val advancedFlags: List<String> = emptyList()
)

object Presets {
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    fun validate(p: Preset): Preset {
        require(p.schemaVersion == 1) { "Unsupported preset schema" }
        require(p.name.isNotBlank() && p.name.length <= 80 && p.name.none { it.isISOControl() }) { "Invalid name" }
        require(p.architecture != Architecture.UNKNOWN) { "Choose a supported architecture" }
        require(p.cores in 1..16 && p.ramMiB in 64..16384 && p.diskGiB in 1..512) { "Resource values out of bounds" }
        require(p.width in 320..3840 && p.height in 240..2160) { "Invalid resolution" }
        require(p.diskFormat in setOf("raw", "qcow2") && p.bootOrder in setOf("disk", "cdrom") && p.firmware in setOf("default", "uefi", "bios"))
        require(p.graphics in setOf("software", "virtio") && p.input in setOf("tablet", "mouse"))
        // No arbitrary switches are accepted until a native adapter defines an explicit safe grammar.
        require(p.advancedFlags.isEmpty()) { "Advanced engine flags are unavailable in this build" }
        return p
    }
    fun encode(p: Preset): String = json.encodeToString(validate(p))
    fun decode(s: String): Preset { require(s.toByteArray().size <= 32768); return validate(json.decodeFromString<Preset>(s)) }
    fun warnings(p: Preset, device: DeviceProfile): List<String> = buildList {
        if(p.engine == EngineKind.QEMU) add("Legacy JSON presets do not configure Launcher VMs; create a machine in Launcher.")
        if(p.engine == EngineKind.PROOT) add("VM cores, RAM, disk, display and firmware fields are not applied to PRoot.")
        if(p.architecture != device.architecture) add("Guest architecture differs from this device.")
        if(p.ramMiB.toLong()*1024*1024 > device.availableRam/2) add("VM memory exceeds half the currently available memory.")
    }
}

@Serializable data class CatalogImage(
    val id: String, val name: String, val version: String, val architecture: Architecture, val flavor: String,
    val url: String, val bytes: Long, val sha256: String, val license: String = "Mixed open-source; see Alpine package licenses",
    val source: String = "https://alpinelinux.org/downloads/", val summary: String = ""
)

object TrustedUrls {
    const val HOST = "dl-cdn.alpinelinux.org"
    val IMAGE_HOSTS=setOf("fedorapeople.org",HOST,OfficialWindows.HOST,OfficialUbuntu.HOST,"cdimage.debian.org","cdimage.ubuntu.com","channels.nixos.org","releases.nixos.org","distfiles.gentoo.org","download.fedoraproject.org","download.freebsd.org","download.ghostbsd.org","files.devuan.org","github.com","release-assets.githubusercontent.com","iso.builds.garudalinux.org","mirror.cachyos.org","mirror.rackspace.com","mirrors.ocf.berkeley.edu","repo.almalinux.org","archive.archlinux.org","dl.fedoraproject.org","repo.chimera-linux.org","sourceforge.net","downloads.sourceforge.net","www.ibiblio.org","archive.org","artix.arkhost.com","altushost-swe.dl.sourceforge.net","archive.archlinux.org","artix.arkhost.com","cdimage.ubuntu.com","distfiles.gentoo.org","dl.fedoraproject.org","dn720903.ca.archive.org","dn721005.ca.archive.org","dn760107.eu.archive.org","dn790003.ca.archive.org","download.ghostbsd.org","files.devuan.org","iso.builds.garudalinux.org","master.dl.sourceforge.net","mirror.cachyos.org","release-assets.githubusercontent.com","releases.nixos.org","releases.ubuntu.com","repo.almalinux.org","repo.chimera-linux.org","saimei.ftp.acc.umu.se","sf-eu-introserv-3.dl.sourceforge.net","www.ibiblio.org")
    fun check(url: String, allowedHosts: Set<String> = IMAGE_HOSTS): URI {
        val u = URI(url)
        require(u.scheme == "https" && (u.host in allowedHosts || (allowedHosts===IMAGE_HOSTS && (u.host?.endsWith(".dl.sourceforge.net")==true || u.host?.endsWith(".archive.org")==true))) && u.userInfo == null && u.fragment == null && (u.port == -1 || u.port == 443)) { "Untrusted image URL" }
        require(!u.rawPath.replace("%20", "").contains("%", true) && u.path.split('/').none { it == ".." || it == "." } && !u.path.contains('\\')) { "Unsafe URL path" }
        return u
    }
}

/** A deliberately narrow parser for the official release list, not a general YAML interpreter. */
object AlpineManifest {
    fun parse(text: String, architecture: Architecture): List<CatalogImage> {
        require(text.toByteArray().size <= 256*1024) { "Manifest too large" }
        val arch = when(architecture) { Architecture.ARM64 -> "aarch64"; Architecture.X86_64 -> "x86_64"; else -> error("Unsupported architecture") }
        val records = mutableListOf<MutableMap<String,String>>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if(line=="-" || line.startsWith("- ")) records += mutableMapOf<String,String>()
            val clean = line.removePrefix("- ")
            if(':' in clean && records.isNotEmpty()) {
                val key = clean.substringBefore(':').trim()
                if(key in setOf("arch","version","flavor","file","size","sha256")) {
                    require(key !in records.last()) { "Duplicate manifest field" }
                    records.last()[key] = clean.substringAfter(':').trim().trim('"','\'')
                }
            }
        }
        return records.filter { it["flavor"] in setOf("alpine-minirootfs", "alpine-standard", "alpine-virt") && it["arch"] == arch }.map { m ->
            val file = requireNotNull(m["file"])
            val version = requireNotNull(m["version"])
            val flavor = requireNotNull(m["flavor"])
            require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
            val suffix = if(flavor == "alpine-minirootfs") "tar.gz" else "iso"
            require(file == "$flavor-$version-$arch.$suffix") { "Unexpected release filename" }
            val hash = requireNotNull(m["sha256"]).lowercase()
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "Missing SHA-256" }
            val bytes = requireNotNull(m["size"]).toLong()
            require(bytes in 1..(20L*1024*1024*1024))
            val url = "https://${TrustedUrls.HOST}/alpine/v${version.substringBeforeLast('.')}/releases/$arch/$file"
            TrustedUrls.check(url)
            CatalogImage("$flavor-$version-$arch", "Alpine Linux", version, architecture, flavor, url, bytes, hash)
        }.also { require(it.isNotEmpty()) { "No supported verified releases" } }
    }
}
