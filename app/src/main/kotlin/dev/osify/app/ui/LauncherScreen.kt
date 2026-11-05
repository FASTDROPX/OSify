@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.osify.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.osify.app.AppViewModel
import dev.osify.app.data.*
import dev.osify.app.device.DeviceSnapshot
import dev.osify.app.engine.GuestConsoleActivity
import dev.osify.app.engine.VmLogsDialog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import dev.osify.core.SessionState
import dev.osify.core.VmHardware

@Composable fun LauncherScreen(vm: AppViewModel,prefs: AppPreferences,device: DeviceSnapshot,openTermux: ()->Unit,prepare: ()->Unit,linux: ()->Unit,catalog: ()->Unit) {
    val machines by vm.virtualMachines.collectAsStateWithLifecycle()
    val images by vm.images.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    val launcherFocus=LocalFocusManager.current
    var search by rememberSaveable {mutableStateOf("")}
    var media by remember {mutableStateOf<VirtualMachine?>(null)}
    var clone by remember {mutableStateOf<VirtualMachine?>(null)}
    var cloning by remember {mutableStateOf(false)}
    var cloneProgress by remember {mutableFloatStateOf(0f)}
    var cloneJob by remember {mutableStateOf<Job?>(null)}
    var create by remember {mutableStateOf(false)}
    var log by remember {mutableStateOf<VirtualMachine?>(null)}
    var editing by remember {mutableStateOf<VirtualMachine?>(null)}
    var remove by remember {mutableStateOf<VirtualMachine?>(null)}
    Text("Launcher",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
    Text(tr("Your systems, right on your phone.","Ваши системы — прямо на телефоне."),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
    QemuStatusStrip(machines,onLog={log=it})
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(tr("My systems","Мои системы"),Modifier.weight(1f),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
        TileBadge(machines.size.toString(),color=MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick={create=true},modifier=Modifier.testTag("new-vm")) {Icon(Icons.Default.Add,tr("Create virtual machine","Создать виртуальную машину"))}
    }
    if(machines.isNotEmpty()) OutlinedTextField(search,{search=it},singleLine=true,label={Text(tr("Find a machine","Найти машину"))},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}) {Icon(Icons.Default.Close,tr("Clear search","Очистить поиск"))}},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={launcherFocus.clearFocus()}),modifier=Modifier.fillMaxWidth().testTag("launcher-search"),shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
    val shownMachines=machines.filter {search.isBlank() || (it.name+" "+it.guest+" "+images.firstOrNull {image->image.id==it.imageId}?.title.orEmpty()).contains(search,true)}
    if(machines.isNotEmpty() && shownMachines.isEmpty()) Text(tr("No matching machines","Машины не найдены"),style=MaterialTheme.typography.bodyMedium)
    if(machines.isEmpty()) OutlinedCard(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OsLogo(OsIdentity.WINDOWS,Modifier.size(48.dp));OsLogo(OsIdentity.UBUNTU,Modifier.size(48.dp))}
            Text(tr("Add your first system","Добавьте первую систему"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text(tr("Download an ISO or import a disk, then create a machine with +.","Скачайте ISO или добавьте диск, затем создайте машину кнопкой +."),style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick=catalog) {Icon(Icons.Default.GridView,null,Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text(tr("Choose an OS","Выбрать ОС"))}
        }
    }
    shownMachines.forEach {machine->key(machine.id) {
        var bootIso by rememberSaveable(machine.id) {mutableStateOf(true)}
        var menu by remember {mutableStateOf(false)}
        var options by remember {mutableStateOf(false)}
        var openAfterLaunch by remember {mutableStateOf(false)}
        var stopping by remember {mutableStateOf(false)}
        val active=machine.state in setOf("DISPLAY_AVAILABLE","PAUSED","SHUTDOWN_REQUESTED")
        val pending=machine.state=="REQUESTED"
        val identity=osIdentity(machine.name+" "+images.firstOrNull {it.id==machine.imageId}?.title.orEmpty(),machine.guest)
        LaunchedEffect(machine.state,openAfterLaunch) {
            if(openAfterLaunch && machine.state=="DISPLAY_AVAILABLE") {
                kotlinx.coroutines.delay(550);openAfterLaunch=false
                context.startActivity(Intent(context,GuestConsoleActivity::class.java).putExtra("vm",machine.id))
            }
            if(machine.state=="FAILED") openAfterLaunch=false
        }
        OsMachineTile(identity,machine.name,
            if(machine.guest=="WINDOWS") tr("Windows on your phone","Windows на вашем телефоне") else tr("A complete virtual machine","Полноценная виртуальная машина"),
            when(machine.state) {"REQUESTED"->tr("Starting","Запуск");"DISPLAY_AVAILABLE"->tr("Running","Работает");"PAUSED"->tr("Paused","Пауза");"SHUTDOWN_REQUESTED"->tr("Shutting down","Завершение");"FAILED"->tr("Launch failed","Ошибка запуска");else->tr("Ready","Готова к запуску")},
            if(active) Color(0xFF8DD8AB) else MaterialTheme.colorScheme.onSurfaceVariant,listOf(runCatching {VmHardware.decode(machine.hardwareJson).architecture}.getOrDefault("auto").let {if(it=="X86") "x86" else if(it=="X86_64") "x64" else "CPU auto"},"${machine.ramMiB} MB",machine.firmware),
            primary=tr("Start","Запустить"),enabled=!pending && !stopping,onPrimary={
                if(active) {stopping=true;scope.launch {runCatching {withContext(Dispatchers.IO) {vm.app.vmEngine.control(machine,"force");withTimeout(15000) {vm.dao.virtualMachines().first {rows->rows.none {it.id==machine.id && it.state !in setOf("STOPPED","FAILED")}}}};delay(500)}.onFailure {vm.message.value=it.message};stopping=false}} else {openAfterLaunch=true;if(machine.state=="PAUSED") vm.controlVm(machine,"resume") else vm.launchVm(machine,bootIso)}
            },running=(active || pending) && !stopping,
            secondary=if(active) tr("Screen","Экран") else tr("Options","Параметры"),onSecondary={if(active) context.startActivity(Intent(context,GuestConsoleActivity::class.java).putExtra("vm",machine.id)) else options=true},tag="vm-${machine.id}") {
                Box {
                    IconButton(onClick={menu=true},modifier=Modifier.size(30.dp).testTag("vm-menu-${machine.id}")) {Icon(Icons.Default.MoreVert,tr("Machine actions","Действия с машиной"),Modifier.size(20.dp))}
                    DropdownMenu(menu,{menu=false}) {
                        if(active) {
                            DropdownMenuItem(text={Text(if(machine.state=="PAUSED") tr("Resume","Продолжить") else tr("Pause","Пауза"))},onClick={menu=false;vm.controlVm(machine,if(machine.state=="PAUSED") "resume" else "pause")})
                            DropdownMenuItem(text={Text(tr("Shut down guest","Завершить работу гостя"))},onClick={menu=false;vm.controlVm(machine,"shutdown")})
                        }
                        if(!active && !pending) DropdownMenuItem(text={Text(tr("Edit configuration","Изменить конфигурацию"))},onClick={menu=false;editing=machine})
                        if(pending || machine.state=="FAILED") DropdownMenuItem(text={Text(tr("Reset engine","Сбросить движок"))},onClick={menu=false;vm.resetPendingVm(machine.id)})
                        DropdownMenuItem(text={Text(tr("Files & drivers","Файлы и драйверы"))},leadingIcon={Icon(Icons.Default.FolderOpen,null)},onClick={menu=false;media=machine})
                        if(machine.state=="STOPPED") DropdownMenuItem(text={Text(tr("Clone machine","Клонировать машину"))},leadingIcon={Icon(Icons.Default.ContentCopy,null)},onClick={menu=false;clone=machine})
                        DropdownMenuItem(text={Text(tr("Runtime log","Журнал движка"))},onClick={menu=false;log=machine})
                        if(machine.state=="STOPPED") DropdownMenuItem(text={Text(tr("Remove machine","Удалить машину"))},onClick={menu=false;remove=machine})
                    }
                }
            }
        if(options) AlertDialog(onDismissRequest={options=false},title={Text(machine.name)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {OsLogo(identity,Modifier.size(48.dp));Column {Text("${machine.ramMiB} MB RAM",fontWeight=FontWeight.Bold);Text("${machine.cores} CPU · "+runCatching {VmHardware.decode(machine.hardwareJson).architecture}.getOrDefault("auto").let {if(it=="X86") "x86" else if(it=="X86_64") "x64" else "CPU auto"},style=MaterialTheme.typography.bodySmall)}}
            Text("${machine.diskGiB} GB · ${machine.firmware}")
            Text(tr("Boot from","Загрузка с"),fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(bootIso,{bootIso=true},label={Text("ISO")})
                FilterChip(!bootIso,{bootIso=false},label={Text(tr("Installed disk","Установленного диска"))})
            }
            Text(tr("Use the ISO to install, then start from the disk. Files stay in OSify.","Для установки используйте ISO, затем запускайте с диска. Файлы хранятся в OSify."),style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick={options=false;editing=machine},modifier=Modifier.testTag("vm-edit-${machine.id}")) {Text(tr("Edit","Изменить"))}},dismissButton={TextButton(onClick={options=false}) {Text(tr("Done","Готово"))}})
    }}
    Text(tr("One machine at a time. x64 emulation on ARM can be slow; resources depend on the guest OS.","Одна машина за раз. Эмуляция x64 на ARM может быть медленной; ресурсы зависят от гостевой ОС."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(create) VmEditor(images.filter {it.state=="COMPLETE" && it.architecture!="ARM64" && it.flavor !in setOf("alpine-minirootfs","windows-drivers") && !it.title.endsWith(".tar.gz",true)},device,prefs.profile,{create=false},{image,name,guest,ram,cores,disk,firmware->vm.createVm(image,name,guest,ram,cores,disk,firmware);create=false},{create=false;catalog()})
    media?.let {GuestToolsDialog(vm.app,it,onDismiss={media=null})}
    clone?.let {source->AlertDialog(onDismissRequest={if(!cloning) clone=null},title={Text(tr("Clone machine","Клонировать машину"))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(source.name);Text(tr("Copies the disk and settings. The source must remain stopped.","Копия диска и настроек. Исходная машина должна оставаться остановленной."),style=MaterialTheme.typography.bodySmall);if(cloning) LinearProgressIndicator(progress={cloneProgress},modifier=Modifier.fillMaxWidth())}},confirmButton={TextButton(enabled=!cloning,onClick={cloning=true;cloneJob=scope.launch {try {withContext(Dispatchers.IO) {vm.app.vmEngine.clone(source.id) {cloneProgress=it}};clone=null} catch(e: CancellationException) {clone=null} catch(e: Exception) {vm.message.value=e.message} finally {cloning=false}}}) {Text(tr("Create copy","Создать копию"))}},dismissButton={TextButton(onClick={if(cloning) cloneJob?.cancel() else clone=null}) {Text(tr("Cancel","Отмена"))}})}
    log?.let {VmLogsDialog(it,onDismiss={log=null})}
    editing?.let {machine->VmConfigurationEditor(machine,device,onDismiss={editing=null},save={name,ram,cores,firmware->vm.app.vmEngine.configure(machine.id,name,ram,cores,firmware)},saveHardware={name,ram,cores,firmware,hardware->vm.app.vmEngine.configure(machine.id,name,ram,cores,firmware,hardware)})}
    remove?.let {machine->AlertDialog(onDismissRequest={remove=null},title={Text(tr("Delete machine?","Удалить машину?"))},text={Text(tr("Its virtual disk will be deleted. The original ISO stays in Downloads.","Виртуальный диск будет удалён. Исходный ISO останется в «Загрузках»."))},confirmButton={TextButton(onClick={vm.removeVm(machine);remove=null}) {Text(tr("Delete","Удалить"))}},dismissButton={TextButton(onClick={remove=null}) {Text(tr("Cancel","Отмена"))}})}
}

private fun suggestedGuest(image: ImageRecord?)=if(image?.let {it.flavor.startsWith("windows") || it.title.contains("windows",true) || it.title.contains("tiny",true)}==true) "WINDOWS" else "LINUX"
internal fun recommendedFirmware(image: ImageRecord?,guest: String): String=if(guest!="WINDOWS" || Regex("(?i)(windows[ _-]*xp|winxp|\\bxp\\b|windows[ _-]*7|win7|windows[ _-]*vista)").containsMatchIn(image?.title.orEmpty()+" "+image?.flavor.orEmpty())) "BIOS" else "UEFI"
private fun suggestedName(image: ImageRecord?)=image?.title?.replace(Regex("(?i)\\.iso$"),"")?.take(80)?:"My OS"
private fun suggestedDisk(image: ImageRecord?,guest: String)=if(guest=="WINDOWS") "64" else if(image?.flavor=="ubuntu-desktop" || image?.title?.contains("ubuntu",true)==true) "32" else "8"

@Composable private fun VmEditor(images: List<ImageRecord>,device: DeviceSnapshot,profile: String,dismiss: ()->Unit,save: (ImageRecord,String,String,Int,Int,Int,String)->Unit,catalog: ()->Unit) {
    if(images.isEmpty()) {
        AlertDialog(onDismissRequest=dismiss,title={Text(tr("Create a system","Создайте свою систему"))},text={Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {OsLogo(OsIdentity.WINDOWS,Modifier.size(48.dp));OsLogo(OsIdentity.UBUNTU,Modifier.size(48.dp))}
            Text(tr("First, add an ISO image","Сначала добавьте образ ISO"),fontWeight=FontWeight.Bold)
            Text(tr("Download one from the catalog or import your own file in Downloads. Then choose its memory and disk size.","Скачайте образ в каталоге или импортируйте свой файл в «Загрузках». Затем выберите память и размер диска."))
        }},confirmButton={TextButton(onClick=catalog) {Text(tr("Choose an ISO","Выбрать ISO"))}},dismissButton={TextButton(onClick=dismiss) {Text(tr("Cancel","Отмена"))}})
        return
    }

    var selected by remember {mutableStateOf(images.firstOrNull())}
    var selectedArch by remember(selected?.id) {mutableStateOf(selected?.architecture?.takeIf {it in setOf("X86","X86_64")}.orEmpty())}
    val memoryLimit=memoryLimit(device)
    var name by remember {mutableStateOf(suggestedName(images.firstOrNull()))};var guest by remember {mutableStateOf(suggestedGuest(images.firstOrNull()))}
    var ram by remember {mutableStateOf(minOf(if(profile=="COMFORT") 1536 else 1024,memoryLimit).toString())};var cores by remember {mutableStateOf(minOf(if(profile=="SPEED") 4 else 2,maxOf(1,device.profile.cores/2)).toString())};var disk by remember {mutableStateOf(suggestedDisk(images.firstOrNull(),guest))}
    var firmware by remember {mutableStateOf(recommendedFirmware(selected,guest))}
    LaunchedEffect(selected?.id) {if(dev.osify.core.BootPlan.xp(selected?.title.orEmpty())) {ram=minOf(512,memoryLimit).toString();cores="1";firmware="BIOS"}}
    var error by remember {mutableStateOf<String?>(null)}
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(16.dp).imePadding(),shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(tr("Create a system","Создайте свою систему"),style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(images.isEmpty()) {Text(tr("First, add an ISO image","Сначала добавьте образ ISO"),fontWeight=FontWeight.Bold);Text(tr("Download one from the catalog or import a file in Downloads.","Скачайте его в каталоге или импортируйте файл в «Загрузках»."),style=MaterialTheme.typography.bodySmall);Button(onClick=catalog) {Text(tr("Choose an ISO","Выбрать ISO"))}}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("X86","X86_64").forEach {a->FilterChip(selectedArch==a,{selectedArch=a},label={Text(if(a=="X86") "x86 · 32 bit" else "x64 · 64 bit")})}}
                    if(selectedArch.isBlank()) Text(tr("Choose the guest architecture","Выберите архитектуру образа"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    if(images.isNotEmpty()) Text(tr("Installation image","Установочный образ"),fontWeight=FontWeight.Bold)
                    images.forEach {image->Row(verticalAlignment=Alignment.CenterVertically) {OsLogo(osIdentity(image.title),Modifier.size(48.dp));RadioButton(selected?.id==image.id,{if(name==suggestedName(selected)) name=suggestedName(image);selected=image;guest=suggestedGuest(image);firmware=recommendedFirmware(selected,guest);disk=suggestedDisk(image,guest)});Text(image.title)}}
                    OutlinedTextField(name,{name=it},label={Text(tr("Name","Название"))},modifier=Modifier.fillMaxWidth().testTag("vm-name"))
                    Row {listOf("WINDOWS","LINUX").forEach {key->FilterChip(selected=guest==key,onClick={guest=key;firmware=recommendedFirmware(selected,key);disk=suggestedDisk(selected,key)},label={Text(key)})}}
                    Row {listOf("BIOS","UEFI").forEach {key->FilterChip(selected=firmware==key,onClick={firmware=key},label={Text(if(key=="UEFI") "UEFI" else "BIOS")})}}
                    ResourceControls(ram.toIntOrNull()?:256,cores.toIntOrNull()?:1,memoryLimit,{ram=it.toString()},{cores=it.toString()})
                    OutlinedTextField(disk,{disk=it},label={Text(tr("Virtual disk, GiB (2–128)","Виртуальный диск, GiB (2–128)"))},modifier=Modifier.fillMaxWidth())
                    Text(tr("The virtual disk grows as data is written. BIOS and UEFI are available. Windows 11 hardware requirements still apply.","Виртуальный диск занимает место по мере записи. Доступны BIOS и UEFI. Требования Windows 11 к оборудованию сохраняются."),style=MaterialTheme.typography.bodySmall)
                    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                }
                Row {TextButton(onClick=dismiss) {Text(tr("Cancel","Отмена"))};Button(onClick={
                    val image=selected;val r=ram.toIntOrNull();val c=cores.toIntOrNull();val d=disk.toIntOrNull()
                    if(selectedArch.isBlank() || image==null || name.isBlank() || r !in 256..memoryLimit || c !in 1..4 || d !in 2..128) error=if(java.util.Locale.getDefault().language=="ru") "Проверьте образ, название и числовые поля" else "Check the ISO, name and numeric fields"
                    else save(image.copy(architecture=selectedArch),name,guest,r!!,c!!,d!!,firmware)
                },modifier=Modifier.testTag("vm-save")) {Text(tr("Create","Создать"))}}
            }
        }
    }
}


private fun memoryLimit(device: DeviceSnapshot): Int=(if(device.profile.totalRam>0) minOf(4096,(device.profile.totalRam/2/1048576).toInt()) else 4096).coerceAtLeast(256)/128*128

@Composable private fun ResourceControls(ram: Int,cores: Int,limit: Int,onRam: (Int)->Unit,onCores: (Int)->Unit) {
    val colors=MaterialTheme.colorScheme
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    Surface(color=colors.surfaceContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            ResourceSlider(Icons.Default.Memory,tr("Memory","Оперативная память"),"$ram MB",ram.toFloat(),256f,limit.coerceAtLeast(384).toFloat(),((limit-256)/128-1).coerceAtLeast(0),"vm-ram",enabled=limit>256) {focus.clearFocus();keyboard?.hide();onRam((it/128).roundToInt()*128)}
            Text(tr("Up to half of the phone RAM; the rest stays available for Android.","До половины памяти телефона — остальное остаётся Android."),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            HorizontalDivider()
            ResourceSlider(Icons.Default.DeveloperBoard,tr("CPU cores","Ядра процессора"),cores.toString(),cores.toFloat(),1f,4f,2,"vm-cores") {focus.clearFocus();keyboard?.hide();onCores(it.roundToInt())}
            Text(tr("More cores do not always make software emulation faster.","Больше ядер не всегда ускоряет программную эмуляцию."),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        }
    }
}

@Composable private fun ResourceSlider(icon: androidx.compose.ui.graphics.vector.ImageVector,title: String,valueText: String,value: Float,min: Float,max: Float,steps: Int,tag: String,enabled: Boolean=true,onChange: (Float)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
        Text(title,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {Text(valueText,Modifier.padding(horizontal=12.dp,vertical=8.dp).testTag("$tag-value"),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)}
    }
    Slider(value=value.coerceIn(min,max),onValueChange=onChange,valueRange=min..max,steps=steps,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag(tag))
}

@Composable internal fun VmConfigurationEditor(machine: VirtualMachine,device: DeviceSnapshot,onDismiss: ()->Unit,save: suspend (String,Int,Int,String)->Unit,saveHardware: (suspend (String,Int,Int,String,VmHardware)->Unit)?=null,initialAdvanced: Boolean=false) {
    var name by remember(machine.id) {mutableStateOf(machine.name)}
    val limit=memoryLimit(device)
    var ram by remember(machine.id) {mutableIntStateOf(machine.ramMiB.coerceAtMost(limit))}
    var cores by remember(machine.id) {mutableIntStateOf(machine.cores)}
    var firmware by remember(machine.id) {mutableStateOf(machine.firmware)}
    var hardware by remember(machine.id) {mutableStateOf(runCatching {VmHardware.decode(machine.hardwareJson)}.getOrDefault(VmHardware()))}
    var advanced by remember(machine.id) {mutableStateOf(initialAdvanced)}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    Dialog(onDismissRequest={if(!busy) onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(12.dp).imePadding(),shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(tr("Machine configuration","Конфигурация машины"),style=MaterialTheme.typography.titleLarge)
                PrimaryTabRow(selectedTabIndex=if(advanced) 1 else 0) {
                    Tab(!advanced,{advanced=false},text={Text(tr("Basic","Основное"))},modifier=Modifier.testTag("vm-basic-tab"))
                    Tab(advanced,{advanced=true},text={Text("Advanced")},modifier=Modifier.testTag("vm-advanced-tab"))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    if(advanced) VmAdvanced(hardware) {hardware=it} else {
                    Text(tr("Changes apply at the next start. The installed system and disk are preserved.","Изменения применятся при следующем запуске. Установленная система и диск сохраняются."),style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(name,{name=it.take(80)},label={Text(tr("Name","Название"))},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("vm-edit-name"))
                    ResourceControls(ram,cores,limit,{ram=it},{cores=it})
                    Text(tr("Firmware","Прошивка"),style=MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("BIOS","UEFI").forEach {f->FilterChip(firmware==f,{firmware=f},label={Text(f)},enabled=!busy)}}
                    Text(tr("Keep the firmware used when installing the guest; changing it can prevent booting.","Оставьте прошивку, с которой устанавливали ОС: её смена может помешать загрузке."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr("Disk · ${machine.diskGiB} GB","Диск · ${machine.diskGiB} GB"),fontWeight=FontWeight.Bold)
                    Text(tr("Disk capacity and the source image stay fixed to protect the installed system.","Размер диска и исходный образ зафиксированы, чтобы сохранить установленную систему."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("vm-edit-error"))
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss,enabled=!busy) {Text(tr("Cancel","Отмена"))}
                    Button(onClick={scope.launch {busy=true;runCatching {withContext(Dispatchers.IO) {hardware.validate();if(saveHardware!=null) saveHardware(name,ram,cores,firmware,hardware) else save(name,ram,cores,firmware)}}.onSuccess {onDismiss()}.onFailure {error=it.message.orEmpty()};busy=false}},enabled=!busy && name.isNotBlank(),modifier=Modifier.testTag("vm-edit-save")) {Text(tr("Save","Сохранить"))}
                }
            }
        }
    }
}
