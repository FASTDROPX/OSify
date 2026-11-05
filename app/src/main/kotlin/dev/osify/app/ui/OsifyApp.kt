@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.osify.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.osify.app.AppViewModel
import dev.osify.app.data.*
import dev.osify.app.device.DeviceSnapshot
import dev.osify.core.*
import java.text.DateFormat
import java.util.Date

@Composable fun OsifyApp(vm: AppViewModel) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val prefs=preferences
    if(prefs==null) { MaterialTheme { Surface(Modifier.fillMaxSize()) { Box(contentAlignment=Alignment.Center) { CircularProgressIndicator() } } };return }
    OsifyTheme(prefs) {
        val device=remember(vm) {vm.device.value}
        val context=LocalContext.current
        val openBattery: ()->Unit={ runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }.onFailure { vm.message.value=it.message } }
        if(!prefs.onboarded) Onboarding(prefs,device,{vm.boolean("onboarded",true)},openBattery)
        else MainShell(vm,prefs,device,openBattery)
    }
}

@Composable private fun MainShell(vm: AppViewModel,prefs: AppPreferences,device: DeviceSnapshot,openBattery: ()->Unit) {
    val tr=translator()
    val context=LocalContext.current
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val busy by vm.catalogBusy.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val release by vm.releaseReport.collectAsStateWithLifecycle()
    val snackbar=remember { SnackbarHostState() }
    var deviceDetails by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf("home") }
    val screenScroll=remember(screen) { androidx.compose.foundation.ScrollState(0) }
    var selected by remember { mutableStateOf<CatalogImage?>(null) }
    var download by remember { mutableStateOf<CatalogImage?>(null) }
    var imageDetails by remember { mutableStateOf<ImageRecord?>(null) }
    var presetEditor by remember { mutableStateOf<Preset?>(null) }
    var editPresetId by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    var showEngine by rememberSaveable { mutableStateOf(false) }
    var consentDialog by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var exportContent by rememberSaveable { mutableStateOf("") }
    var pendingImage by rememberSaveable { mutableStateOf<String?>(null) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) vm.run { context.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(exportContent) } }
    }
    val exportLog=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if(uri!=null) vm.run { context.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(exportContent) } }
    }
    val importImage=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) { vm.importImage(uri);screen="library" } }
    val importPreset=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) vm.run {
            val text=context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader -> val b=CharArray(32769);var n=0;while(n<b.size) {val count=reader.read(b,n,b.size-n);if(count<0)break;n+=count};require(n<=32768) {"Preset exceeds 32 KiB"};String(b,0,n) }
            vm.importPreset(text)
        }
    }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingImage?.let { id->(catalog?.images.orEmpty()+listOf(OfficialWindows.windows11,OfficialUbuntu.desktop)).find { image->image.id==id }?.let(vm::queue) };pendingImage=null
    }
    val termuxPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* Actual permission is rechecked before every engine request. */ }
    val openLink: (String)->Unit={ url->runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { vm.message.value=it.message } }
    val openTermux: ()->Unit={
        val intent=context.packageManager.getLaunchIntentForPackage("com.termux")
        if(intent==null) openLink("https://f-droid.org/en/packages/com.termux/") else context.startActivity(intent)
    }
    val openAppSettings: ()->Unit={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}
    val launchImport: ()->Unit={importImage.launch(arrayOf("*/*"))}
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it);vm.message.value=null } }
    BackHandler(enabled=screen!="home") { screen="home" }
    val destinations=listOf("home","catalog","launcher","library","settings")
    val labels=listOf(tr("Overview","Обзор"),tr("Explore","Каталог"),"Launcher",tr("Library","Библиотека"),tr("Settings","Настройки"))
    val icons=listOf(Icons.Default.SpaceDashboard,Icons.Default.TravelExplore,Icons.Default.PlayArrow,Icons.Default.FolderOpen,Icons.Default.Tune)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=840.dp
        Scaffold(snackbarHost={SnackbarHost(snackbar)},bottomBar={
            if(!wide) NavigationReflow(screen) {screen=it}
        }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if(wide) NavigationRail {
                    OrbitMark(56.dp)
                    Spacer(Modifier.height(28.dp))
                    destinations.forEachIndexed { i,key -> NavigationRailItem(selected=screen==key,onClick={screen=key},icon={Icon(icons[i],null)},label={Text(labels[i],style=MaterialTheme.typography.labelSmall)},modifier=Modifier.testTag("nav-$key")) }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp).testTag("app-header"),verticalAlignment=Alignment.CenterVertically) {
                        OrbitMark(36.dp)
                        Text("OSify",fontFamily=WordmarkFont,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=8.dp).weight(1f))
                        IconButton(onClick={screen="settings"},modifier=Modifier.testTag("open-settings")) {Icon(Icons.Default.Tune,tr("Settings","Настройки"))}
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().testTag("page-viewport")) {
                    if(screen=="catalog") CatalogExperience(vm,catalog,busy,{vm.queue(it)},{screen="launcher"},launchImport,openLink,{id->confirm=tr("Discard partial data and start again?","Удалить частичные данные и скачать заново?") to {vm.restart(id)}})
                    else {
                    AnimatedPageColumn(screen,Modifier.fillMaxSize().verticalScroll(screenScroll).widthIn(max=1100.dp).padding(horizontal=20.dp,vertical=12.dp),spacing=if(screen=="launcher" || screen=="catalog") 10.dp else 14.dp) {
                    when(screen) {
                        "home"->{
                            Arrival(0) {PageTitle(tr("Your systems","Ваши системы"),tr("Choose, download, launch","Выберите, скачайте, запустите"))}
                            Arrival(1) {DashboardHero({screen="catalog"},{screen="launcher"})}
                            Arrival(2) {HomeTransferStats(vm) {screen="library"}}
                            Arrival(3) {HomeMachineSummary(vm) {screen="launcher"}}
                            Text(tr("Your phone","Ваш телефон"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            LiveMemoryCard(vm)
                            TextButton(onClick={deviceDetails=!deviceDetails}) {Text(tr("Phone details","Сведения о телефоне"))}
                            androidx.compose.animation.AnimatedVisibility(deviceDetails) {LiveDeviceCard(vm)}
                            OutlinedButton(onClick=vm::releaseResources,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Default.Memory,null);Spacer(Modifier.width(8.dp));Text(tr("Release OSify resources","Освободить ресурсы OSify")) }
                            Text(tr("Closes idle OSify resources. Other apps are not affected.","Закрывает неиспользуемые ресурсы OSify. Другие приложения продолжат работать."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            release?.let { (before,after)->
                                InfoCard(tr("Memory after cleanup","Память после очистки"),tr("No OSify-owned idle guests were running. Available RAM: ${formatBytes(before)} before → ${formatBytes(after)} after. Other Android activity can change this value; no performance gain is claimed.","Собственных неактивных гостей OSify нет. Доступная память: ${formatBytes(before)} до → ${formatBytes(after)} после. Значение меняется из-за работы Android; рост производительности не обещается."),Icons.Default.Info)
                                TextButton(onClick=openAppSettings) { Text(tr("Android app settings","Настройки приложения в Android")) }
                            }
                        }
                        "catalog"->Unit
                        "library"->{
                            val images by vm.images.collectAsStateWithLifecycle()
                            PageTitle(tr("Your images","Ваши образы"),tr("Download here. Launch from Launcher.","Здесь — файлы. Запуск систем — в Launcher."))
                            FilledTonalButton(onClick=launchImport,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("import-image")) { Icon(Icons.Default.FileOpen,null);Spacer(Modifier.width(8.dp));Text(tr("Import an image","Импортировать образ")) }
                            if(images.isEmpty()) {
                                ImageLibraryEmpty {screen="catalog"}
                                SupportingInfo(Icons.Default.FolderOpen,tr("Already have an image?","Образ уже на телефоне?"),tr("Use Import above to add an ISO, IMG, QCOW2 or tar.gz file.","Нажмите «Импортировать образ», чтобы добавить ISO, IMG, QCOW2 или tar.gz."))
                            }
                            images.forEach { row->
                                ImageCard(row,{imageDetails=row},{vm.pause(row.id)},{vm.resume(row.id)},
                                    {confirm=tr("Discard partial data and download the entire image again? This can use several gigabytes.","Удалить частичные данные и скачать весь образ заново? Это может потребовать несколько гигабайт трафика.") to {vm.restart(row.id)}},
                                    {confirm=tr("Delete this image and its partial data from OSify?", "Удалить образ и его частичные данные из OSify?") to {vm.delete(row.id)}})
                            }
                        }
                        "launcher"->LauncherScreen(vm,prefs,device,openTermux,{if(!vm.app.termux.installed()) openTermux() else if(!prefs.externalEngineConsent) consentDialog=true else if(!vm.app.termux.permissionGranted()) termuxPermission.launch("com.termux.permission.RUN_COMMAND") else vm.prepareRuntime()},{showEngine=true},{screen="catalog"})
                        "settings"->SettingsScreen(prefs,vm,openBattery,openAppSettings,
                            {confirm=tr("Reset app preferences? Saved images and presets are kept.","Сбросить настройки приложения? Образы и профили сохранятся.") to vm::resetSettings},
                            {if(it) consentDialog=true else vm.boolean("termux",false)},
                            {screen="launcher"},{exportContent=vm.diagnostics();info=tr("Diagnostics include transfer errors and the Termux terminal transcript. Review this in the engine panel before sharing. No device identifiers are collected.","Диагностика включает ошибки загрузок и текст терминала Termux. Перед отправкой просмотрите журнал в панели движка. Идентификаторы устройства не собираются.")},
                            {info=tr("OSify ${dev.osify.app.BuildConfig.VERSION_NAME}\n\nQEMU 5.1 is included, based on Limbo 6.0.1. No Termux or root required. One x64 guest runs in a private Android service process. Disks and images stay in OSify storage.\n\nCatalog: Quickget public JSON API plus official Alpine, Ubuntu and Microsoft sources. No Windows installation images are bundled. XP, 7 and Tiny ISO downloads are linked from Internet Archive.\n\nComponent licenses: OSify Apache-2.0; QEMU GPL-2.0; Limbo GPL-2.0-or-later; Quickget data GPL-3.0. Firmware and libraries retain their licenses. Real OS artwork: project repositories, Quickemu icons, DistroWatch and Wikimedia. Sources, attribution and corresponding native sources accompany this APK.","OSify ${dev.osify.app.BuildConfig.VERSION_NAME}\n\nВстроен QEMU 5.1 на основе Limbo 6.0.1. Termux и root не нужны. Одна гостевая ОС x64 работает в отдельном процессе Android. Диски и образы хранятся в OSify.\n\nКаталог: открытый JSON API Quickget и официальные источники Alpine, Ubuntu и Microsoft. Установочные образы Windows не включены. ISO XP, 7 и Tiny доступны по прямым ссылкам Internet Archive.\n\nЛицензии компонентов: OSify Apache-2.0; QEMU GPL-2.0; Limbo GPL-2.0-or-later; данные Quickget GPL-3.0. Прошивки и библиотеки сохраняют собственные лицензии. Настоящие изображения ОС: репозитории проектов, Quickemu icons, DistroWatch и Wikimedia. Источники, сведения об авторах и соответствующие нативные исходники прилагаются к APK.")})
                    }
                    Spacer(Modifier.height(12.dp))
                    }
                    ScrollFade(screenScroll,Modifier.align(Alignment.TopCenter))
                    }
                    }
                }
            }
        }
    }
    download?.let { image ->
        val metered=context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        AlertDialog(onDismissRequest={download=null},title={Text(tr("Download image?","Скачать образ?"))},text={Text("${image.name} ${image.version} / ${formatBytes(image.bytes)}\n\n"+
            (if(prefs.wifiOnly) tr("Waits for an unmetered network (usually Wi-Fi).","Ожидает сеть без тарификации (обычно Wi-Fi).") else if(metered && prefs.meteredWarning) tr("Your network is metered. Mobile data charges may apply.","Сеть тарифицируется. Возможна оплата мобильного трафика.") else tr("Uses your current network.","Используется текущая сеть."))+"\n\n"+if(OfficialWindows.isEvaluation(image)) tr("A local SHA-256 is saved. No publisher checksum is available. Android may interrupt long transfers; downloaded data is kept when possible.","Сохраним локальный SHA-256. Эталонный хеш издателя недоступен. Android может прервать долгую загрузку; скачанные данные по возможности сохранятся.") else tr("The source checksum is checked before completion. Notifications are optional. Android may interrupt long transfers; partial data is kept when possible.","Перед завершением проверяется контрольная сумма источника. Уведомления необязательны. Android может прервать долгую загрузку; частичные данные по возможности сохраняются."))},
            confirmButton={TextButton(onClick={
                download=null
                if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {pendingImage=image.id;notifications.launch(Manifest.permission.POST_NOTIFICATIONS)} else {vm.queue(image)}
            },modifier=Modifier.testTag("confirm-download")) {Text(tr("Download","Скачать"))}},dismissButton={TextButton(onClick={download=null}) {Text(tr("Cancel","Отмена"))}})
    }
    imageDetails?.let { row ->
        val clipboard=LocalClipboardManager.current
        AlertDialog(onDismissRequest={imageDetails=null},title={Text(row.title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(transferLabel(row.state)+" / "+formatBytes(row.bytes))
            Text(if(row.imported || row.flavor.startsWith("windows")) tr("User-provided, untrusted image. The local SHA-256 identifies this file; no publisher authenticity has been established.","Пользовательский недоверенный образ. Локальная SHA-256 идентифицирует файл, но не подтверждает его подлинность.") else tr("SHA-256 is verified against the official manifest only after the state is Complete.","SHA-256 сверяется с официальным манифестом только при переходе в состояние «Готово»."))
            Text("${ImageChecksums.label(row.sha256)}\n${ImageChecksums.value(row.sha256)}",style=MaterialTheme.typography.bodySmall)
            if(row.sha256.isNotBlank()) TextButton(onClick={clipboard.setText(AnnotatedString(row.sha256))}) {Text(tr("Copy checksum","Копировать хеш"))}
            Text(tr("Create an ISO-based x64 machine in Launcher. QEMU runs in Termux and its screen opens in OSify. Imported architecture and bootability are not verified.","Создайте машину x64 из ISO в Launcher. QEMU работает в Termux, экран открывается в OSify. Архитектура и загрузочность импортированного образа не проверены."))
            if(row.error.isNotBlank() && row.state in setOf("FAILED","RESTART_REQUIRED")) Text(row.error,color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(onClick={imageDetails=null}) {Text(tr("Close","Закрыть"))}})
    }
    presetEditor?.let { preset -> PresetEditor(preset,device,{presetEditor=null}) { p -> if(editPresetId==null) vm.savePreset(p) else vm.savePreset(p,editPresetId!!);presetEditor=null } }
    confirm?.let { (text,action)->AlertDialog(onDismissRequest={confirm=null},title={Text(tr("Please confirm","Подтвердите действие"))},text={Text(text)},confirmButton={TextButton(onClick={confirm=null;action()}) {Text(tr("Confirm","Подтвердить"))}},dismissButton={TextButton(onClick={confirm=null}) {Text(tr("Cancel","Отмена"))}}) }
    if(consentDialog) AlertDialog(onDismissRequest={consentDialog=false},title={Text(tr("Allow Termux integration?","Разрешить интеграцию с Termux?"))},text={Text(tr("OSify can prepare packages, copy your selected ISO into Termux and run its own Linux or QEMU environments there. Guest disks stay in Termux. This integration is experimental and has not been validated on your phone. Install and initialize Termux separately, then allow external commands.","OSify сможет подготовить пакеты, скопировать выбранный ISO в Termux и запускать там свои среды Linux и QEMU. Гостевые диски останутся в Termux. Интеграция экспериментальная и пока не проверена на вашем телефоне. Установите и откройте Termux отдельно, затем разрешите внешние команды."))},confirmButton={TextButton(onClick={vm.boolean("termux",true);consentDialog=false}) {Text(tr("Enable integration","Включить интеграцию"))}},dismissButton={TextButton(onClick={consentDialog=false}) {Text(tr("Cancel","Отмена"))}})
    if(showEngine) EngineDialog(prefs,engine,vm.app.termux.installed(),vm.app.termux.permissionGranted(),{showEngine=false},openTermux,{openLink("https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent")},
        {if(prefs.externalEngineConsent) termuxPermission.launch("com.termux.permission.RUN_COMMAND") else consentDialog=true},
        {confirm=tr("Install Alpine inside Termux? Termux downloads and verifies its own packages and images. Its network and storage use are managed separately from OSify's download preferences.","Установить Alpine внутри Termux? Termux скачивает и проверяет собственные пакеты и образы. Его использование сети и хранилища не регулируется настройками загрузок OSify.") to vm::installLinux},vm::launchLinux,
        {confirm=tr("Reset tracking? This does not stop any guest. Check and exit the session in Termux first to avoid duplicate sessions.","Сбросить учёт сеанса? Гостевая ОС не будет остановлена. Сначала проверьте сеанс в Termux и выйдите из него, чтобы не создавать дубликаты.") to vm::resetEngine},vm::markEngineUnknown)
    info?.let { text -> AlertDialog(onDismissRequest={info=null},title={Text("OSify")},text={Text(text,modifier=Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton(onClick={info=null}) {Text(tr("Close","Закрыть"))}},dismissButton={if(exportContent.startsWith("OSify ${dev.osify.app.BuildConfig.VERSION_NAME}")) TextButton(onClick={info=null;exportLog.launch("osify-diagnostics.txt")}) {Text(tr("Export log","Сохранить журнал"))}}) }
}

@Composable private fun PageTitle(title: String,subtitle: String) { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge)} }
@Composable private fun SectionTitle(text: String) {Text(text,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)}
@Composable private fun BadgeText(text: String) {Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(30.dp)) {Text(text,Modifier.padding(horizontal=12.dp,vertical=7.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSecondaryContainer)} }
@Composable private fun InfoCard(title: String,text: String,icon: ImageVector) {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}} }
@Composable private fun StatCard(label: String,value: String,icon: ImageVector,modifier: Modifier) {
    Surface(modifier,shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
            Column {Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}

@Composable fun MemoryCard(device: DeviceSnapshot) {
    val d=device.profile
    val fraction=if(d.totalRam>0) (device.used.toFloat()/d.totalRam).coerceIn(0f,1f) else 0f
    var details by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("memory-card"),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal=18.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Memory","Память"),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                IconButton(onClick={details=!details},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Info,tr("Memory details","О показателях памяти"),modifier=Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(d.totalRam>0) formatBytes(d.availableRam) else "—",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                Text(if(d.totalRam>0) tr("available of ${formatBytes(d.totalRam)}","свободно из ${formatBytes(d.totalRam)}") else tr("Measurement unavailable","Нет данных"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=4.dp).weight(1f))
            }
            MemoryWaveProgress(fraction,active=d.totalRam>0)
            Text(if(d.totalRam>0) tr("${(fraction*100).toInt()}% used, including Android cache","Занято ${(fraction*100).toInt()}%, включая кэш Android") else tr("Android has not supplied memory data","Android не передал показатели памяти"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedVisibility(details) {Text(tr("Total minus available is an estimate. Useful Android cache counts as used memory. Updated every 2.5 seconds.","Занятая память — разница между общей и доступной. В неё входит полезный кэш Android. Обновляется каждые 2,5 секунды."),style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@Composable private fun DeviceCard(device: DeviceSnapshot) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(tr("This phone","Этот телефон"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {BadgeText(device.profile.architecture.name);BadgeText(tr("${device.profile.cores} cores","Ядер: ${device.profile.cores}"));BadgeText("Android API ${device.profile.api}")}
            Text(tr("Storage available: ${formatBytes(device.profile.freeDisk)}","Свободно для приложения: ${formatBytes(device.profile.freeDisk)}"))
            Text(device.graphics,style=MaterialTheme.typography.bodySmall)
            val battery=if(device.battery in 0..100) "${device.battery}%" else tr("unknown","неизвестна")
            val thermal=when(device.thermal) {0->tr("normal","норма");1,2->tr("warm","повышен");3,4->tr("high","высокий");5,6->tr("critical","критический");else->tr("unknown","неизвестен")}
            Text(tr("Battery: $battery / thermal status: $thermal\nKVM: not verified","Батарея: $battery / тепловой статус: $thermal\nKVM: не подтверждён"),style=MaterialTheme.typography.bodySmall)
            if(device.lowMemory) Text(tr("Android reports low memory. Close OSify-owned guests before starting more work.","Android сообщает о нехватке памяти. Закройте собственных гостей OSify перед новой задачей."),color=MaterialTheme.colorScheme.error)
        }
    }
}

@Composable internal fun ImageCard(row: ImageRecord,details: ()->Unit,pause: ()->Unit,resume: ()->Unit,restart: ()->Unit,delete: ()->Unit) {
    var menu by remember {mutableStateOf(false)}
    val identity=osIdentity(row.title)
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(100.dp)) {
                OsArtwork(identity,Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xF0102840),androidx.compose.ui.graphics.Color(0x70102840)))))
                Row(Modifier.fillMaxSize().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    OsLogo(identity,Modifier.size(48.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(row.title,fontWeight=FontWeight.Bold,color=TileInk,style=MaterialTheme.typography.titleSmall);Text(if(row.imported) tr("Your file","Ваш файл") else row.version,color=TileMuted,style=MaterialTheme.typography.labelSmall)}
                    Box {
                        IconButton(onClick={menu=true},modifier=Modifier.testTag("image-menu-${row.id}")) {Icon(Icons.Default.MoreVert,tr("Image actions","Действия с образом"),tint=TileInk)}
                        DropdownMenu(menu,{menu=false}) {
                            DropdownMenuItem(text={Text(tr("Details","Сведения"))},onClick={menu=false;details()})
                            if(!row.imported && row.state in setOf("PAUSED","FAILED","RESTART_REQUIRED")) DropdownMenuItem(text={Text(tr("Start over…","Скачать заново…"))},onClick={menu=false;restart()})
                            if(row.state !in setOf("RUNNING","VERIFYING","QUEUED")) DropdownMenuItem(text={Text(tr("Delete…","Удалить…"))},onClick={menu=false;delete()})
                        }
                    }
                }
            }
            Column(Modifier.padding(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                AnimatedContent(row.state=="COMPLETE",transitionSpec={(fadeIn()+expandVertically()) togetherWith (fadeOut()+shrinkVertically())},label="image ready") {ready->
                    if(!ready) DownloadFill(row.state,if(row.expectedBytes>0) row.bytes.toFloat()/row.expectedBytes else 0f,{when(row.state) {"RUNNING","QUEUED"->pause();"PAUSED","FAILED"->resume();"RESTART_REQUIRED"->restart();else->details()}},Modifier.fillMaxWidth(),bytes=row.bytes,totalBytes=row.expectedBytes)
                    else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                        Text(tr("Ready to use","Готов к запуску"),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                        Text(formatBytes(row.bytes),fontFamily=NumberFont,style=MaterialTheme.typography.labelMedium)
                    }
                }
                if(row.error.isNotBlank() && row.state in setOf("FAILED","RESTART_REQUIRED")) Text(row.error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                TextButton(onClick=details) {Icon(Icons.Default.Info,null,Modifier.size(17.dp));Spacer(Modifier.width(7.dp));Text(tr("File details","Сведения о файле"))}
            }
        }
    }
}

@Composable fun transferLabel(state: String): String=when(state) {
    "QUEUED"->tr("Queued","В очереди")
    "RUNNING"->tr("Downloading","Загрузка");"PAUSED"->tr("Paused","Пауза")
    "VERIFYING"->tr("Verifying file","Проверка файла");"COMPLETE"->tr("Complete","Готово")
    "RESTART_REQUIRED"->tr("Fresh download requires your confirmation","Новая загрузка требует подтверждения")
    "FAILED"->tr("Failed","Ошибка")
    else->state
}
@Composable private fun flavorLabel(flavor: String)=when(flavor) {"alpine-minirootfs"->tr("Minimal Linux filesystem","Минимальная среда Linux");"alpine-virt"->tr("Virtual ISO","ISO для виртуальных машин");else->tr("Standard ISO","Стандартный ISO")}
@Composable fun sessionLabel(state: SessionState)=when(state) {
    SessionState.IDLE->tr("No external session requested","Внешний сеанс не запрошен")
    SessionState.INSTALL_REQUESTED->tr("Install requested / awaiting Termux result","Установка запрошена / ожидается ответ Termux")
    SessionState.INSTALLED_EXTERNAL->tr("Termux reported installation complete","Termux сообщил о завершении установки")
    SessionState.LAUNCH_REQUESTED->tr("Launch requested / running state unverified","Запуск запрошен / работа не подтверждена")
    SessionState.RETURNED->tr("Termux reported the guest shell exited","Termux сообщил о выходе из гостевой оболочки")
    SessionState.FAILED->tr("External request failed","Ошибка внешнего запроса")
    else->tr("Session status unknown / unavailable","Состояние сеанса неизвестно / недоступно")
}

@Composable private fun Arrival(index: Int,content: @Composable ()->Unit) {content()}

@Composable private fun LiveMemoryCard(vm: AppViewModel) {
    val device by vm.device.collectAsStateWithLifecycle()
    MemoryCard(device)
}
@Composable private fun LiveDeviceCard(vm: AppViewModel) {
    val device by vm.device.collectAsStateWithLifecycle()
    DeviceCard(device)
}

@Composable private fun HomeTransferStats(vm: AppViewModel,open: ()->Unit) {
    val images by vm.images.collectAsStateWithLifecycle()
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
        StatCard(tr("Saved images","В коллекции"),images.count {it.state=="COMPLETE"}.toString(),Icons.Default.Layers,Modifier.weight(1f).clickable(onClick=open))
        StatCard(tr("Downloading","Загружаются"),images.count {it.state in setOf("RUNNING","QUEUED","VERIFYING")}.toString(),Icons.Default.CloudDownload,Modifier.weight(1f).clickable(onClick=open))
    }
}
