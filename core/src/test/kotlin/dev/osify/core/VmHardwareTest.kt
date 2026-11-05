package dev.osify.core

import java.io.File
import org.junit.Test
import kotlin.test.*

class VmHardwareTest {
 @Test fun defaultsKeepCompatibleHardwareAndPersistentCdBoot() {
  val h=VmHardware.decode("")
  val a=h.arguments("LINUX","BIOS","disk.raw","raw","install.iso","serial.log")
  assertTrue(a.contains("qemu64,-tsc"));assertTrue(a.contains("virtio-net-pci,netdev=net0"));assertTrue(a.contains("menu=on,strict=on"));assertTrue(a.contains("ide-cd,bus=ide.1,unit=0,drive=osify-install,bootindex=1"));assertTrue(a.contains("ide-hd,bus=ide.0,unit=0,drive=osify-disk,bootindex=2"));assertFalse(a.any {it.contains("once=")})
  assertEquals(h,VmHardware.decode(VmHardware.encode(h)))
 }
 @Test fun advancedOptionsReachTheEngineAndCannotInjectArguments() {
  val h=VmHardware(machine="q35",cpu="Haswell",tcg="multi",tsc="on",hpet=true,acpi=false,rtcBase="localtime",rtcClock="host",vga="virtio",network="e1000",mac="52:54:00:12:34:56",restrictNetwork=true,diskBus="virtio",diskCache="writethrough",discard="unmap",usb=true,pointer="mouse",usbKeyboard=true,boot="disk",bootMenu=false,noReboot=true,serial=false,balloon=true,tbMiB=64)
  assertEquals(h,VmHardware.decode(VmHardware.encode(h)))
  val a=h.arguments("WINDOWS","UEFI","disk.raw","raw",null,"serial")
  listOf("q35","tcg,thread=multi,tb-size=64","Haswell,+tsc","base=localtime,clock=host","virtio","-no-acpi","-no-reboot","usb-mouse","usb-kbd","virtio-balloon-pci","menu=off,strict=on","user,id=net0,restrict=on","e1000,netdev=net0,mac=52:54:00:12:34:56").forEach {assertTrue(it in a,it)}
  assertFalse("-no-hpet" in a);assertFalse(h.useIso(true))
  assertFails {h.copy(cpu="max,-smp,300").validate()};assertFails {h.copy(mac="01:00:00:00:00:00").validate()};assertFails {h.copy(diskBus="ide,file=/outside").validate()}
 }
 @Test fun disabledDevicesAreActuallyAbsent() {
  val a=VmHardware(network="none",usb=false,serial=false).arguments("LINUX","BIOS","disk","raw",null,"serial")
  assertEquals("none",a[a.indexOf("-nic")+1]);assertFalse("-usb" in a);assertFalse("usb-tablet" in a);assertEquals("none",a[a.indexOf("-serial")+1])
 }
}
