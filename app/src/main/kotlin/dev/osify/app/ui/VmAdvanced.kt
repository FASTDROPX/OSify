@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.osify.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.osify.core.VmHardware

@Composable internal fun VmAdvanced(h: VmHardware,onChange: (VmHardware)->Unit) {
 val auto=tr("Auto","Авто")
 Text(tr("Hardware options supported by the embedded PC engine. Changes apply after stopping and restarting the machine.","Параметры оборудования встроенного PC-движка. Изменения применяются после остановки и нового запуска."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 AdvancedGroup(tr("CPU & timers","CPU и таймеры")) {
  HardwareChoice(tr("Guest architecture","Архитектура гостя"),h.architecture,listOf("auto","X86","X86_64"),"advanced-architecture",mapOf("auto" to auto,"X86" to "x86 (32-bit)","X86_64" to "x64 (64-bit)")) {onChange(h.copy(architecture=it))}
  HardwareChoice(tr("Chipset","Чипсет"),h.machine,listOf("pc","q35"),"advanced-machine") {onChange(h.copy(machine=it))}
  HardwareChoice(tr("CPU model","Модель CPU"),h.cpu,listOf("auto","qemu64","qemu32","core2duo","Nehalem","Haswell","max"),"advanced-cpu",mapOf("auto" to auto)) {onChange(h.copy(cpu=it))}
  HardwareChoice(tr("TCG threads","Потоки TCG"),h.tcg,listOf("single","multi"),"advanced-tcg",mapOf("single" to tr("Single","Один поток"),"multi" to tr("Multi","Несколько потоков"))) {onChange(h.copy(tcg=it))}
  HardwareChoice(tr("Translation cache","Кеш трансляции"),h.tbMiB.toString(),listOf("16","32","64","128","256"),"advanced-cache") {onChange(h.copy(tbMiB=it.toInt()))}
  Text(tr("Cache size is in MiB, in addition to guest RAM. Multi-thread TCG may be faster but depends on the guest.","Кеш в MiB выделяется сверх RAM машины. TCG multi может ускорить работу; результат зависит от ОС."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  HardwareChoice("TSC",h.tsc,listOf("auto","on","off"),"advanced-tsc",mapOf("auto" to auto)) {onChange(h.copy(tsc=it))}
  HardwareToggle("HPET",h.hpet) {onChange(h.copy(hpet=it))}
  HardwareToggle("ACPI",h.acpi) {onChange(h.copy(acpi=it))}
  HardwareChoice(tr("Clock base","Часовой пояс RTC"),h.rtcBase,listOf("utc","localtime"),"advanced-rtc-base") {onChange(h.copy(rtcBase=it))}
  HardwareChoice(tr("Clock source","Источник RTC"),h.rtcClock,listOf("vm","host"),"advanced-rtc-clock") {onChange(h.copy(rtcClock=it))}
 }
 AdvancedGroup(tr("Boot","Загрузка")) {
  HardwareChoice(tr("Boot device","Источник загрузки"),h.boot,listOf("auto","iso","disk"),"advanced-boot",mapOf("auto" to tr("Use launch choice","Как при запуске"),"iso" to "ISO","disk" to tr("Installed disk","Установленный диск"))) {onChange(h.copy(boot=it))}
  HardwareToggle(tr("CD first","Сначала CD / ISO"),h.cdFirst) {onChange(h.copy(cdFirst=it))}
  HardwareToggle(tr("Firmware boot menu","Меню загрузки прошивки"),h.bootMenu) {onChange(h.copy(bootMenu=it))}
  HardwareToggle(tr("Exit instead of reboot","Выход вместо перезагрузки"),h.noReboot) {onChange(h.copy(noReboot=it))}
  Text(tr("A Windows installer may ask you to press a key to boot from CD. OSify does not press installer keys automatically.","Установщик Windows может попросить нажать клавишу для загрузки с CD. OSify не нажимает кнопки установщика автоматически."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
 AdvancedGroup(tr("Storage","Диск")) {
  HardwareChoice(tr("Disk controller","Контроллер диска"),h.diskBus,listOf("ide","virtio"),"advanced-disk") {onChange(h.copy(diskBus=it))}
  HardwareChoice(tr("Write cache","Кеш записи"),h.diskCache,listOf("writeback","writethrough","none"),"advanced-disk-cache") {onChange(h.copy(diskCache=it))}
  HardwareChoice("TRIM / discard",h.discard,listOf("ignore","unmap"),"advanced-discard") {onChange(h.copy(discard=it))}
  Text(tr("Changing the controller can require guest drivers. Disk contents and capacity are preserved.","Смена контроллера может потребовать драйверов в гостевой ОС. Данные и размер диска сохраняются."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
 AdvancedGroup(tr("Display","Графика")) {
  HardwareChoice(tr("Video adapter","Видеоадаптер"),h.vga,listOf("std","cirrus","vmware","virtio"),"advanced-vga") {onChange(h.copy(vga=it))}
  Text(tr("Software emulation, without 3D acceleration. Display resolution is selected inside the guest.","Программная эмуляция без 3D-ускорения. Разрешение экрана выбирается внутри гостевой ОС."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
 AdvancedGroup(tr("Network","Сеть")) {
  HardwareChoice(tr("Network adapter","Сетевой адаптер"),h.network,listOf("auto","none","rtl8139","e1000","pcnet","virtio-net-pci"),"advanced-network",mapOf("auto" to auto,"none" to tr("Disabled","Отключён"))) {onChange(h.copy(network=it))}
  HardwareToggle(tr("Isolate from Internet","Изолировать от интернета"),h.restrictNetwork) {onChange(h.copy(restrictNetwork=it))}
  OutlinedTextField(h.mac,{onChange(h.copy(mac=it.trim().take(17)))},singleLine=true,label={Text(tr("MAC address (optional)","MAC-адрес (необязательно)"))},placeholder={Text("52:54:00:12:34:56")},modifier=Modifier.fillMaxWidth().testTag("advanced-mac"))
  Text(tr("NAT uses the phone connection. The guest still needs its network driver, DHCP and DNS.","NAT использует подключение телефона. В гостевой ОС должны работать сетевой драйвер, DHCP и DNS."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
 AdvancedGroup(tr("Input & diagnostics","Ввод и диагностика")) {
  HardwareToggle("USB",h.usb) {onChange(h.copy(usb=it))}
  HardwareChoice(tr("USB pointer","USB-указатель"),h.pointer,listOf("tablet","mouse","none"),"advanced-pointer") {onChange(h.copy(pointer=it))}
  HardwareToggle(tr("USB keyboard","USB-клавиатура"),h.usbKeyboard) {onChange(h.copy(usbKeyboard=it))}
  HardwareToggle(tr("Serial log","Последовательный порт и журнал"),h.serial) {onChange(h.copy(serial=it))}
  HardwareToggle(tr("VirtIO balloon device","Устройство VirtIO balloon"),h.balloon) {onChange(h.copy(balloon=it))}
 }
 Text(tr("KVM, phone GPU passthrough, audio output are not implemented in this engine integration.","KVM, проброс GPU телефона, вывод звука в этой интеграции движка пока не реализованы."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 OutlinedButton(onClick={onChange(VmHardware())},modifier=Modifier.fillMaxWidth().testTag("advanced-reset")) {Icon(Icons.Default.Restore,null);Spacer(Modifier.width(8.dp));Text(tr("Reset hardware defaults","Сбросить параметры оборудования"))}
}
@Composable private fun AdvancedGroup(title: String,content: @Composable ColumnScope.()->Unit) {
 Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(22.dp)) {
  Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);content()}
 }
}
@Composable private fun HardwareToggle(label: String,value: Boolean,change: (Boolean)->Unit) {
 Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Switch(value,change)}
}
@Composable private fun HardwareChoice(label: String,value: String,choices: List<String>,tag: String,names: Map<String,String> = emptyMap(),change: (String)->Unit) {
 var expanded by remember {mutableStateOf(false)}
 Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
  Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Box {
   OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().testTag(tag),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {Text(names[value]?:value,Modifier.weight(1f),maxLines=1,softWrap=false);Icon(Icons.Default.ExpandMore,null)}
   DropdownMenu(expanded,{expanded=false}) {choices.forEach {key->DropdownMenuItem(text={Text(names[key]?:key)},onClick={change(key);expanded=false},trailingIcon={if(key==value) Icon(Icons.Default.Check,null)})}}
  }
 }
}
