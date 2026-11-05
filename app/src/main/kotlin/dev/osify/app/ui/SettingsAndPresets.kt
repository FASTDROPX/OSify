@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.osify.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.osify.app.AppViewModel
import dev.osify.app.data.AppPreferences
import dev.osify.app.device.DeviceSnapshot
import dev.osify.core.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.osify.app.data.VirtualMachine

@Composable fun SettingsScreen(
    prefs: AppPreferences,vm: AppViewModel,openBattery: ()->Unit,openAppSettings: ()->Unit,reset: ()->Unit,
    consent: (Boolean)->Unit,engine: ()->Unit,diagnostics: ()->Unit,about: ()->Unit
) {
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<SettingChoice?>(null) }
    val text=translator()
    val machines by vm.virtualMachines.collectAsStateWithLifecycle()
    var advancedMachine by remember {mutableStateOf<VirtualMachine?>(null)}
    androidx.activity.compose.BackHandler(section!=null) {section=null}
    fun choose(title: String,choices: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
        picker=SettingChoice(title,choices,selected,onSelect)
    }
    val language=when(prefs.language) {"ru"->"Русский";"en"->"English";else->tr("System","Системный")}
    val theme=when(prefs.theme) {"dark"->tr("Dark","Тёмная");"light"->tr("Light","Светлая");else->tr("System","Системная")}
    val motion=when(prefs.motion) {"off"->tr("Off","Выключены");"reduced"->tr("Reduced","Уменьшенные");else->tr("Full","Полные")}
    advancedMachine?.let {machine->VmConfigurationEditor(machine,vm.device.collectAsStateWithLifecycle().value,onDismiss={advancedMachine=null},save={name,ram,cores,firmware->vm.app.vmEngine.configure(machine.id,name,ram,cores,firmware)},saveHardware={name,ram,cores,firmware,hardware->vm.app.vmEngine.configure(machine.id,name,ram,cores,firmware,hardware)},initialAdvanced=true)}
    AnimatedPageColumn(section ?: "settings-root",Modifier.fillMaxWidth()) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        if(section!=null) IconButton(onClick={section=null},modifier=Modifier.testTag("settings-back")) {Icon(Icons.AutoMirrored.Filled.ArrowBack,tr("Back","Назад"))}
        Text(when(section) {"advanced"->"Advanced";"appearance"->tr("Appearance","Оформление");"downloads"->tr("Downloads","Загрузки");"system"->tr("System","Система");"linux"->tr("Machines","Машины");else->tr("Settings","Настройки")},style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("settings-title"))
    }
    when(section) {
        null->{
            Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    OrbitMark(44.dp)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(tr("Make OSify yours","OSify по-вашему"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text("$theme  •  $language",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer)}
                }
            }
            SettingsList {
                SettingsLink(Icons.Default.Palette,tr("Appearance","Оформление"),tr("Theme, language and motion","Тема, язык и анимации"),"settings-appearance") {section="appearance"}
                SettingsLink(Icons.Default.Download,tr("Downloads","Загрузки"),tr("Network and file storage","Сеть и хранение файлов"),"settings-downloads") {section="downloads"}
                SettingsLink(Icons.Default.NotificationsNone,tr("System","Система"),tr("Notifications and battery","Уведомления и батарея"),"settings-system") {section="system"}
                SettingsLink(Icons.Default.SettingsSuggest,"Advanced",tr("Virtual machine hardware","Оборудование виртуальных машин"),"settings-advanced") {section="advanced"}
                SettingsLink(Icons.Default.Memory,tr("Machines","Машины"),tr("Built-in QEMU and resource profiles","Встроенный QEMU и профили ресурсов"),"settings-linux") {section="linux"}
            }
            Text(tr("Help","Помощь"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=12.dp,top=8.dp))
            SettingsList {
                SettingsLink(Icons.Default.Replay,tr("Replay introduction","Повторить знакомство"),tag="replay-onboarding") {vm.boolean("onboarded",false)}
                SettingsLink(Icons.Default.Description,tr("Diagnostics","Диагностика"),tr("Save a report","Сохранить отчёт"),onClick=diagnostics)
                SettingsLink(Icons.Default.Info,tr("About OSify","Об OSify"),"${dev.osify.app.BuildConfig.VERSION_NAME}",onClick=about)
            }
            TextButton(onClick=reset,modifier=Modifier.testTag("reset-settings")) {Text(tr("Reset preferences","Сбросить настройки"),color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        "advanced"->{
            SettingsNote(tr("Select a stopped machine to edit its hardware. Each machine keeps its own settings.","Выберите остановленную машину. У каждой машины свои параметры оборудования."))
            if(machines.isEmpty()) SettingsNote(tr("Create a machine in Launcher first.","Сначала создайте машину в Launcher."))
            SettingsList {machines.forEach {machine->
                val stopped=machine.state in setOf("STOPPED","FAILED")
                SettingsLink(Icons.Default.Computer,machine.name,if(stopped) "${machine.ramMiB} MB · ${machine.cores} CPU · ${machine.firmware}" else tr("Stop this machine to configure it","Для настройки остановите машину"),"advanced-vm-${machine.id}") {if(stopped) advancedMachine=machine}
            }}
        }
        "appearance"->{
            Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    OrbitMark(44.dp)
                    Column {Text(tr("OSify blue","Голубой OSify"),fontWeight=FontWeight.Bold);Text(tr("The palette follows the icon","Палитра в цвет значка"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
            SettingsList {
                SettingsLink(Icons.Default.Language,tr("Language","Язык"),language,"settings-language") {
                    choose(text("Language","Язык"),listOf("system" to text("System","Системный"),"en" to "English","ru" to "Русский"),prefs.language) {vm.string("language",it)}
                }
                SettingsLink(Icons.Default.Contrast,tr("Theme","Тема"),theme,"settings-theme") {
                    choose(text("Theme","Тема"),listOf("system" to text("System","Системная"),"light" to text("Light","Светлая"),"dark" to text("Dark","Тёмная")),prefs.theme) {vm.string("theme",it)}
                }
                SettingsToggle(tr("AMOLED black","AMOLED"),tr("Black in dark themes, including wallpaper colors","Чёрный фон тёмной темы, включая цвета обоев"),prefs.amoled) {vm.boolean("amoled",it)}
                SettingsToggle(tr("Wallpaper colors","Цвета обоев"),tr("Replace the blue palette","Заменяют голубую палитру"),prefs.dynamicColor) {vm.boolean("dynamic",it)}
            }
            SettingsList {
                SettingsLink(Icons.Default.Animation,tr("Motion","Анимации"),motion,"settings-motion") {
                    choose(text("Motion","Анимации"),listOf("full" to text("Full","Полные"),"reduced" to text("Reduced","Уменьшенные"),"off" to text("Off","Выключены")),prefs.motion) {vm.string("motion",it)}
                }
                SettingsToggle(tr("Vibration","Вибрация"),tr("A short response to actions","Короткий отклик на действия"),prefs.haptics) {vm.boolean("haptics",it)}
            }
            SettingsNote(tr("Android’s animation and vibration settings also apply.","Системные настройки анимации и вибрации тоже учитываются."))
        }
        "downloads"->{
            SettingsList {
                SettingsToggle(tr("Unmetered networks","Сети без тарификации"),tr("Usually Wi-Fi","Обычно это Wi-Fi"),prefs.wifiOnly) {vm.boolean("wifi",it)}
                SettingsToggle(tr("Data warning","Предупреждать о трафике"),tr("Before using a metered network","Перед загрузкой по платной сети"),prefs.meteredWarning) {vm.boolean("metered",it)}
                SettingsLink(Icons.Default.Download,tr("At the same time","Одновременно"),prefs.concurrent.toString()) {
                    choose(text("Concurrent downloads","Одновременные загрузки"),listOf("1" to "1","2" to "2","3" to "3"),prefs.concurrent.toString()) {vm.concurrency(it.toInt())}
                }
            }
            SettingsNote(tr("Network preferences apply when a download starts or resumes. Files are stored inside OSify and removed when the app is uninstalled.","Выбор сети применяется при старте или продолжении загрузки. Файлы хранятся внутри OSify и удаляются вместе с приложением."))
            SettingsNote(tr("Android may pause long transfers. Resume keeps downloaded data when the server supports it; restarting from zero requires confirmation.","Android может прервать долгую загрузку. При продолжении скачанные данные сохраняются, если сервер это поддерживает. Загрузка с нуля требует подтверждения."))
        }
        "system"->{
            SettingsList {
                SettingsLink(Icons.Default.NotificationsNone,tr("Notifications and permissions","Уведомления и разрешения"),tr("Open Android settings","Открыть настройки Android"),onClick=openAppSettings)
                SettingsLink(Icons.Default.BatteryStd,tr("Battery","Батарея"),tr("Background restrictions","Ограничения фоновой работы"),onClick=openBattery)
            }
            SettingsNote(tr("Enable notifications in Android settings to see background transfers. Battery restrictions may interrupt background work; changing them is optional.","Уведомления о фоновых загрузках можно включить в настройках Android. Ограничения батареи могут прерывать работу в фоне; менять их необязательно."))
        }
        "linux"->{
            SettingsList {
                SettingsLink(Icons.Default.Tune,tr("Default profile","Профиль по умолчанию"),when(prefs.profile) {"SPEED"->tr("Speed","Скорость");"COMFORT"->tr("Comfort","Комфорт");else->tr("Balanced","Баланс")}) {
                    choose(text("Default profile","Профиль по умолчанию"),listOf("SPEED" to text("Speed","Скорость"),"BALANCED" to text("Balanced","Баланс"),"COMFORT" to text("Comfort","Комфорт")),prefs.profile) {vm.string("profile",it)}
                }
                SettingsLink(Icons.Default.Info,tr("QEMU and open-source licenses","QEMU и открытые лицензии"),onClick=about)
            }
            SettingsNote(tr("QEMU is included in OSify. Machine disks are stored in the app. One x64 guest can run at a time.","QEMU встроен в OSify. Диски машин хранятся в приложении. Одновременно запускается одна гостевая система x64."))
        }
    }
    }
    picker?.let { choice->
        AlertDialog(onDismissRequest={picker=null},title={Text(choice.title)},text={
            Column {choice.options.forEach { (key,label)->
                Row(Modifier.fillMaxWidth().clickable {choice.select(key);picker=null}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=choice.selected==key,onClick={choice.select(key);picker=null})
                    Text(label,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(start=8.dp))
                }
            }}
        },confirmButton={TextButton(onClick={picker=null}) {Text(tr("Cancel","Отмена"))}})
    }
}
private data class SettingChoice(val title: String,val options: List<Pair<String,String>>,val selected: String,val select: (String)->Unit)
@Composable private fun SettingsList(content: @Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) {Column(content=content)}
}
@Composable private fun SettingsLink(icon: ImageVector,title: String,subtitle: String="",tag: String="",onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).testTag(tag).padding(horizontal=18.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Surface(shape=RoundedCornerShape(13.dp),color=MaterialTheme.colorScheme.secondaryContainer) {Icon(icon,null,modifier=Modifier.padding(10.dp).size(22.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            if(subtitle.isNotEmpty()) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight,null,modifier=Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SettingsToggle(title: String,description: String,checked: Boolean,onChecked: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(title,style=MaterialTheme.typography.titleSmall);Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(checked,onChecked,modifier=Modifier.semanticsDescription(title))
    }
}
@Composable private fun SettingsNote(text: String) {Text(text,modifier=Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun ChoiceRow(label: String,choices: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {choices.forEach { (key,title)->FilterChip(selected=key==selected,onClick={onSelect(key)},label={Text(title)},modifier=Modifier.heightIn(min=48.dp))}}
    }
}
@Composable private fun SettingSwitch(title: String,description: String,checked: Boolean,onChecked: (Boolean)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.titleSmall);Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(checked,onChecked,modifier=Modifier.semanticsDescription(title))
    }
}
private fun Modifier.semanticsDescription(label: String): Modifier = this.then(Modifier.semantics { contentDescription=label })

@Composable fun PresetEditor(initial: Preset,device: DeviceSnapshot,onDismiss: ()->Unit,onSave: (Preset)->Unit) {
    val tr=translator()
    var draft by remember(initial) { mutableStateOf(initial) }
    var cores by remember { mutableStateOf(initial.cores.toString()) };var ram by remember { mutableStateOf(initial.ramMiB.toString()) }
    var disk by remember { mutableStateOf(initial.diskGiB.toString()) };var width by remember { mutableStateOf(initial.width.toString()) };var height by remember { mutableStateOf(initial.height.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
      Surface(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(16.dp),shape=RoundedCornerShape(28.dp)) {
       Column {
        Text(tr("Guest preset","Профиль гостевой ОС"),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp).testTag("preset-editor"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(tr("Expert configuration / schema v1. This does not start a guest.","Экспертная конфигурация / схема v1. Гостевая ОС не запускается."),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(draft.name,{draft=draft.copy(name=it)},label={Text(tr("Name","Название"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("preset-name"))
            ChoiceRow(tr("Architecture","Архитектура"),listOf("ARM64" to "ARM64","X86_64" to "X86_64"),draft.architecture.name) {draft=draft.copy(architecture=Architecture.valueOf(it))}
            ChoiceRow(tr("Engine","Движок"),listOf("PROOT" to "PRoot","QEMU" to "QEMU"),draft.engine.name) {draft=draft.copy(engine=EngineKind.valueOf(it))}
            Text(if(draft.engine==EngineKind.PROOT) tr("External adapter only. PRoot shares the host kernel. VM CPU/RAM/disk/firmware/display fields below are stored but not applied.","Только внешний адаптер. PRoot использует ядро устройства. Поля CPU/ОЗУ/диска/прошивки/экрана ниже сохраняются, но не применяются.") else tr("This legacy JSON preset does not configure Launcher VMs. Create a machine in Launcher.","Этот старый JSON-профиль не настраивает VM Launcher. Создайте машину в Launcher."),color=MaterialTheme.colorScheme.primary)
            ChoiceRow(tr("Profile","Профиль"),listOf("SPEED" to tr("Speed","Скорость"),"BALANCED" to tr("Balanced","Баланс"),"COMFORT" to tr("Comfort","Комфорт")),draft.profile.name) {
                val profile=Profile.valueOf(it);val recommendation=Tuner.recommend(device.profile,draft.architecture,draft.engine,profile)
                draft=draft.copy(profile=profile);cores=recommendation.virtualCores.toString();recommendation.ramMiB?.let { value->ram=value.toString() }
            }
            Text(tr("Conservative recommendation: at most half of host CPU cores, up to 2 for Balanced/Comfort or 4 for Speed. VM memory (future QEMU) uses at most one third of available RAM. Hardware virtualization is not assumed.","Консервативная рекомендация: не более половины ядер устройства, до 2 для баланса/комфорта или 4 для скорости. ОЗУ будущей QEMU — не более трети доступной памяти. Аппаратная виртуализация не предполагается."),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={advanced=!advanced}) {Text(tr("Advanced saved configuration","Расширенная сохранённая конфигурация"))}
            if(advanced) {
                Text(tr("These legacy values can be archived and exported; they do not configure Launcher or Termux.","Эти старые параметры можно сохранить и экспортировать; на Launcher и Termux они не влияют."),style=MaterialTheme.typography.bodySmall)
                Text("${draft.architecture} / ${draft.engine} / ${draft.ramMiB} MiB / ${draft.cores} CPU",fontFamily=NumberFont)
            }
            if(draft.architecture!=device.profile.architecture) Text(tr("Warning: architecture differs from this device.","Внимание: архитектура отличается от устройства."),color=MaterialTheme.colorScheme.error)
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("preset-error"))}
        }
        Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.End) {
         TextButton(onClick=onDismiss) {Text(tr("Cancel","Отмена"))}
         TextButton(onClick={
        runCatching {Presets.validate(draft.copy(cores=cores.toInt(),ramMiB=ram.toInt(),diskGiB=disk.toInt(),width=width.toInt(),height=height.toInt()))}
            .onSuccess(onSave).onFailure {error=tr("Check the numeric fields and configuration: ","Проверьте числовые поля и конфигурацию: ")+it.message}
         },modifier=Modifier.testTag("preset-save")) {Text(tr("Save preset","Сохранить профиль"))}
        }
       }
      }
    }
}
@Composable private fun NumberField(label: String,value: String,onChange: (String)->Unit,tag: String) {
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag(tag))
}

@Composable fun EngineDialog(
    prefs: AppPreferences,report: EngineReport,installed: Boolean,permission: Boolean,onDismiss: ()->Unit,openTermux: ()->Unit,guide: ()->Unit,
    requestPermission: ()->Unit,install: ()->Unit,launch: ()->Unit,reset: ()->Unit,unknown: ()->Unit
) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("External Linux engine","Внешний движок Linux"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(tr("EXPERIMENTAL / NOT DEVICE-TESTED","ЭКСПЕРИМЕНТАЛЬНО / НЕ ПРОВЕРЕНО НА УСТРОЙСТВЕ"),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
            Text(sessionLabel(report.state),style=MaterialTheme.typography.titleMedium)
            Text(tr("Termux owns the installation, display, keyboard and process lifetime. OSify cannot verify a live boot, embed this terminal, safely shut it down or force-stop it. Leave OSify freely; doing so does not stop Termux.","Установкой, экраном, клавиатурой и процессом управляет Termux. OSify не подтверждает текущую загрузку, не встраивает терминал и не может безопасно либо принудительно остановить его. Выход из OSify не останавливает Termux."))
            Text(tr("PRoot is a Linux userland sharing Android's kernel, not a VM or security sandbox. It cannot boot Windows or its own Linux kernel. Root/Shizuku does not automatically grant KVM.","PRoot — пользовательская среда Linux с ядром Android, а не виртуальная машина или защитная песочница. Он не загружает Windows или собственное ядро Linux. Root/Shizuku не дают KVM автоматически."),style=MaterialTheme.typography.bodySmall)
            Text(tr("Setup (advanced)","Подготовка (для опытных пользователей)"),fontWeight=FontWeight.Bold)
            Text(tr("1. Install a trusted F-Droid or GitHub Termux build (use a consistent signing source).\n2. In Termux, install proot-distro and set allow-external-apps=true in ~/.termux/termux.properties. See the official guide.\n3. Enable the integration in OSify Settings, then grant the optional command permission.\n4. Request Alpine installation. Wait for the actual exit callback before launching.","1. Установите доверенную сборку Termux из F-Droid или GitHub (не смешивайте источники подписи).\n2. В Termux установите proot-distro и задайте allow-external-apps=true в ~/.termux/termux.properties. См. официальное руководство.\n3. Включите интеграцию в настройках OSify, затем выдайте необязательное разрешение команд.\n4. Запросите установку Alpine. Перед запуском дождитесь фактического ответа о завершении."))
            Text(tr("Termux installed: $installed / permission: $permission / consent: ${prefs.externalEngineConsent}","Termux установлен: $installed / разрешение: $permission / согласие: ${prefs.externalEngineConsent}"),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=openTermux) {Text(if(installed) tr("Open Termux","Открыть Termux") else tr("Get Termux from F-Droid","Termux в F-Droid"))}
            TextButton(onClick=guide) {Text(tr("Official integration guide","Официальное руководство интеграции"))}
            if(!permission) OutlinedButton(onClick=requestPermission,enabled=installed) {Text(tr("Grant command access","Разрешить выполнение команд"))}
            val pending=report.state in setOf(SessionState.INSTALL_REQUESTED,SessionState.LAUNCH_REQUESTED)
            Button(onClick=install,enabled=installed && permission && prefs.externalEngineConsent && !pending && report.state !in setOf(SessionState.INSTALLED_EXTERNAL,SessionState.RETURNED)) {Text(tr("Request Alpine install","Запросить установку Alpine"))}
            Button(onClick=launch,enabled=installed && permission && prefs.externalEngineConsent && report.state in setOf(SessionState.INSTALLED_EXTERNAL,SessionState.RETURNED)) {Text(tr("Request terminal launch","Запросить запуск терминала"))}
            if(pending) TextButton(onClick=unknown) {Text(tr("Callback missing? Mark status unknown","Нет ответа? Отметить статус неизвестным"))}
            Text(tr("To shut down: exit the guest shell in Termux. Force-stopping Termux may corrupt guest files. OSify cannot restore live guest RAM after interruption.","Для завершения выйдите из гостевой оболочки в Termux. Принудительная остановка Termux может повредить файлы гостя. OSify не восстанавливает живую память гостя после прерывания."),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=reset) {Text(tr("Reset tracking only…","Сбросить только учёт сеанса…"))}
            if(report.log.isNotBlank()) {Text(tr("Session log (may contain private terminal output)","Журнал сеанса (может содержать личные данные терминала)"),fontWeight=FontWeight.Bold);Text(report.log,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text(tr("Close","Закрыть"))}})
}
