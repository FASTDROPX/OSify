package dev.osify.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versioned, typed hardware only. Private paths, QMP and VNC stay application-owned. */
@Serializable data class VmHardware(
 val machine: String="pc",val cpu: String="auto",val tcg: String="single",val tsc: String="auto",
 val hpet: Boolean=false,val acpi: Boolean=true,val rtcBase: String="utc",val rtcClock: String="vm",
 val vga: String="std",val network: String="auto",val mac: String="",val restrictNetwork: Boolean=false,
 val diskBus: String="ide",val diskCache: String="writeback",val discard: String="ignore",
 val usb: Boolean=true,val pointer: String="tablet",val usbKeyboard: Boolean=false,
 val boot: String="auto",val bootMenu: Boolean=true,val cdFirst: Boolean=true,val noReboot: Boolean=false,
 val serial: Boolean=true,val balloon: Boolean=false,val tbMiB: Int=32,val architecture: String="auto"
) {
 fun validate() {
  require(architecture in setOf("auto","X86","X86_64")) {"The built-in emulator supports x86 and x64 guests, not ARM64"}
  require(machine in setOf("pc","q35")) {"Unknown chipset"}
  require(cpu in setOf("auto","qemu64","qemu32","core2duo","Nehalem","Haswell","max")) {"Unknown CPU model"}
  require(tcg in setOf("single","multi") && tsc in setOf("auto","on","off")) {"Invalid CPU options"}
  require(rtcBase in setOf("utc","localtime") && rtcClock in setOf("vm","host")) {"Invalid clock"}
  require(vga in setOf("std","cirrus","vmware","virtio")) {"Unknown display adapter"}
  require(network in setOf("auto","none","rtl8139","e1000","pcnet","virtio-net-pci")) {"Unknown network adapter"}
  require(mac.isEmpty() || mac.matches(Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) && mac.take(2).toInt(16) and 1==0) {"MAC must be a unicast address, for example 52:54:00:12:34:56"}
  require(diskBus in setOf("ide","virtio") && diskCache in setOf("writeback","writethrough","none") && discard in setOf("ignore","unmap")) {"Invalid storage options"}
  require(pointer in setOf("tablet","mouse","none") && boot in setOf("auto","iso","disk")) {"Invalid boot or USB option"}
  require(tbMiB in setOf(16,32,64,128,256)) {"Invalid translation cache"}
 }
 fun nic(guest: String)=if(network=="auto") if(guest=="LINUX") "virtio-net-pci" else "rtl8139" else network
 fun cpuValue(firmware: String): String=(if(cpu=="auto") {if(architecture=="X86") "qemu32" else "qemu64"} else cpu)+when(tsc) {"on"->",+tsc";"off"->",-tsc";else->if(firmware=="BIOS") ",-tsc" else ""}
 fun useIso(requested: Boolean)=when(boot) {"iso"->true;"disk"->false;else->requested}
 fun arguments(guest: String,firmware: String,disk: String,format: String,iso: String?,serialPath: String): List<String> {
  validate()
  val a=mutableListOf("-machine",machine,"-accel","tcg,thread=$tcg,tb-size=$tbMiB","-rtc","base=$rtcBase,clock=$rtcClock","-cpu",cpuValue(firmware),"-vga",vga,
   "-drive","file=$disk,format=$format,if=none,id=osify-disk,cache=$diskCache,discard=$discard,aio=threads",
   "-device",(if(diskBus=="virtio") "virtio-blk-pci" else "ide-hd,bus=ide.0,unit=0")+",drive=osify-disk,bootindex=${if(iso!=null && cdFirst) 2 else 1}")
  if(!hpet) a+="-no-hpet"
  if(!acpi) a+="-no-acpi"
  if(noReboot) a+="-no-reboot"
  a+=listOf("-serial",if(serial) "file:$serialPath" else "none")
  val nic=nic(guest)
  if(nic=="none") a+=listOf("-nic","none") else a+=listOf("-netdev","user,id=net0,restrict=${if(restrictNetwork) "on" else "off"}","-device","$nic,netdev=net0"+(if(mac.isNotEmpty()) ",mac=$mac" else ""))
  if(usb) {a+="-usb";if(pointer!="none") a+=listOf("-device","usb-$pointer");if(usbKeyboard) a+=listOf("-device","usb-kbd")}
  if(balloon) a+=listOf("-device","virtio-balloon-pci")
  if(iso!=null) a+=listOf("-drive","file=$iso,format=raw,if=none,media=cdrom,readonly=on,id=osify-install","-device","ide-cd,bus=ide.1,unit=0,drive=osify-install,bootindex=${if(cdFirst) 1 else 2}")
  a+=listOf("-boot","menu=${if(bootMenu) "on" else "off"},strict=on")
  return a
 }
 companion object {
  private val json=Json {encodeDefaults=true;ignoreUnknownKeys=false}
  fun decode(value: String): VmHardware=if(value.isBlank()) VmHardware() else json.decodeFromString<VmHardware>(value).also {it.validate()}
  fun encode(value: VmHardware): String {value.validate();return json.encodeToString(value)}
 }
}
