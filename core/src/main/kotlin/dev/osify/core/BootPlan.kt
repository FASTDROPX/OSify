package dev.osify.core

object BootPlan {
    fun architecture(h: VmHardware,metadata: String,probe: BootMedia?): String {
        require(probe?.architectures!=setOf("ARM64")) {"Образ содержит только ARM64-загрузчик. Нужен ISO x86/x64: ARM64 QEMU в этом APK отсутствует."}
        val detected=probe?.architectures.orEmpty().filter {it in setOf("X86","X86_64")}
        val selected=if(h.architecture!="auto") h.architecture else when {
            detected.size==1->detected.first()
            metadata in setOf("X86","X86_64")->metadata
            "X86_64" in detected->"X86_64"
            else->error("Архитектура образа не определена. Выберите x86 или x64 в параметрах машины → Advanced.")
        }
        require(metadata!="ARM64" && (detected.isEmpty() || selected in detected)) {"Архитектура машины $selected не соответствует загрузчикам образа: $detected"}
        return selected
    }
    fun validate(h: VmHardware,firmware: String,architecture: String,media: BootMedia?) {
        h.validate()
        require(firmware in setOf("BIOS","UEFI"))
        require(!(architecture=="X86" && firmware=="UEFI")) {"32-битной машине нужен BIOS: прошивки UEFI IA32 в этом APK нет."}
        if(media?.kind=="iso" && (media.bios || media.efi)) {
            require(firmware!="BIOS" || media.bios) {"В ISO нет Legacy-загрузки. Выберите UEFI для новой машины."}
            require(firmware!="UEFI" || media.efi) {"В ISO нет EFI-загрузки. Для XP и старых установщиков выберите BIOS."}
        }
        require(!(architecture=="X86_64" && h.cpu=="qemu32")) {"Для x64-образа выберите CPU auto или qemu64."}
    }
    fun xp(name: String)=Regex("(?i)(windows[ _-]*xp|winxp|\\bxp\\b)").containsMatchIn(name)
    fun defaults(name: String,architecture: String)=when {
        xp(name)->VmHardware(architecture=if(architecture=="X86_64") "X86_64" else "X86",cpu=if(architecture=="X86_64") "qemu64" else "qemu32",vga="cirrus",usb=false,pointer="none",tsc="on",rtcBase="localtime")
        architecture=="X86_64" && Regex("(?i)(windows[ _-]*11|tiny[ _-]*11)").containsMatchIn(name)->VmHardware(architecture=architecture,cpu="Nehalem",tsc="on")
        else->VmHardware(architecture=architecture)
    }
}
