package dev.osify.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.osify.app.OsifyApplication
import dev.osify.app.data.*
import dev.osify.core.DriverDownloads
import kotlinx.coroutines.*
import java.io.File

@Composable fun GuestToolsDialog(app: OsifyApplication,machine: VirtualMachine,onDismiss: ()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val tools=app.guestTools
    val compact=LocalConfiguration.current.screenHeightDp<480
    val images by app.database.dao().images().collectAsStateWithLifecycle(emptyList())
    var tab by remember {mutableIntStateOf(0)};var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf("")}
    var sent by remember {mutableStateOf(emptyList<File>())};var received by remember {mutableStateOf(emptyList<File>())}
    var agent by remember {mutableStateOf("")};var url by remember {mutableStateOf("")};var selectedDriver by remember {mutableStateOf("")}
    var autoResize by remember {mutableStateOf(true)};var export by remember {mutableStateOf<File?>(null)}
    var remove by remember {mutableStateOf<File?>(null)}
    var removeReceived by remember {mutableStateOf<File?>(null)}
    val repository=remember {ImageRepository(app,app.database.dao(),app.preferences)}
    fun work(action: suspend ()->Unit) {scope.launch {busy=true;error="";try {withContext(Dispatchers.IO) {action()}} catch(e: CancellationException) {throw e} catch(e: Exception) {error=e.message.orEmpty()} finally {busy=false}}}
    LaunchedEffect(machine.id) {
        while(true) {
            withContext(Dispatchers.IO) {
                sent=tools.inbox(machine.id).listFiles().orEmpty().filter {it.isFile && !it.name.endsWith(".part")}.sortedBy {it.name}
                received=tools.outbox(machine.id).listFiles().orEmpty().filter {it.isFile && !it.name.endsWith(".part")}.sortedBy {it.name}
                agent=tools.agentStatus(machine.id);url=tools.url(machine.id);selectedDriver=tools.selectedDriver(machine.id)
                autoResize=tools.display(machine.id).optBoolean("enabled",true)
            };delay(1500)
        }
    }
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {uris->if(uris.isNotEmpty())work {tools.importFiles(machine,uris)}}
    val importDriver=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)work {repository.import(uri)}}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->
        val file=export
        if(uri!=null && file!=null)work {app.contentResolver.openOutputStream(uri,"w")!!.use {out->file.inputStream().use {it.copyTo(out,65536)}}}
    }
    Dialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(if(compact) .98f else .9f).padding(if(compact) 8.dp else 12.dp).imePadding(),shape=RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(if(compact) 12.dp else 18.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 6.dp else 10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text(tr("Guest tools","Инструменты ОС"),style=if(compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge);if(!compact)Text(machine.name,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
                    IconButton(onClick=onDismiss,enabled=!busy) {Icon(Icons.Default.Close,tr("Close","Закрыть"))}
                }
                PrimaryTabRow(selectedTabIndex=tab) {listOf(tr("Files","Файлы"),tr("Drivers","Драйверы"),tr("Display","Экран")).forEachIndexed {i,title->Tab(tab==i,{tab=i},text={SingleLineLabel(title,Modifier.fillMaxWidth().testTag("guest-tools-tab-$i"),style=MaterialTheme.typography.labelLarge.copy(textAlign=androidx.compose.ui.text.style.TextAlign.Center),minSp=10f,maxSp=13f)})}}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("guest-tools-content"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    when(tab) {
                        0->{
                            Text(tr("Phone → OS","Телефон → ОС"),style=MaterialTheme.typography.titleMedium)
                            Text(tr("Files appear on the read-only PHONE CD. You can also download them from the local page in the guest browser.","Файлы появятся на CD «PHONE» только для чтения. Их также можно скачать с локальной страницы в браузере гостевой ОС."),style=MaterialTheme.typography.bodySmall)
                            Button(onClick={pick.launch(arrayOf("*/*"))},enabled=!busy && machine.state!="REQUESTED",modifier=Modifier.fillMaxWidth().testTag("guest-add-files")) {Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text(tr("Add files","Добавить файлы"))}
                            sent.forEach {file->Row(verticalAlignment=Alignment.CenterVertically) {Text(file.name.substringAfter('-'),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis);IconButton(onClick={remove=file},enabled=!busy) {Icon(Icons.Default.DeleteOutline,tr("Remove from exchange","Убрать с диска обмена"))}}}
                            HorizontalDivider()
                            Text(tr("OS → phone","ОС → телефон"),style=MaterialTheme.typography.titleMedium)
                            Text(tr("Open OSify-Files.url on the PHONE CD, or copy this address into the guest browser. Send a file, then save it below. NAT networking must be enabled. Limit: 128 MB per file.","Откройте OSify-Files.url на диске PHONE или вставьте адрес в браузер гостя. Передайте файл и сохраните его ниже. Нужна работающая NAT-сеть. До 128 МБ на файл."),style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OSify guest files",url))},enabled=url.isNotEmpty(),modifier=Modifier.fillMaxWidth().testTag("guest-copy-files-url")) {Icon(Icons.Default.ContentCopy,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(tr("Copy guest address","Скопировать адрес для гостя"),style=MaterialTheme.typography.labelMedium)}
                            if(received.isEmpty()) Text(tr("Received files will appear here","Здесь появятся принятые файлы"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            received.forEach {file->Row(verticalAlignment=Alignment.CenterVertically) {
                                OutlinedButton(onClick={export=file;save.launch(file.name.substringAfter('-'))},enabled=!busy,modifier=Modifier.weight(1f)) {Icon(Icons.Default.SaveAlt,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(file.name.substringAfter('-'),Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)}
                                IconButton(onClick={removeReceived=file},enabled=!busy) {Icon(Icons.Default.DeleteOutline,tr("Delete received copy","Удалить принятую копию"))}
                            }}
                        }
                        1->{
                            val driver=images.firstOrNull {it.url==DriverDownloads.virtio.url}
                            Text("VirtIO-Win · 0.1.302",style=MaterialTheme.typography.titleMedium)
                            Text(tr("Official VirtIO drivers ISO for supported modern Windows versions. It does not install drivers automatically. XP uses the compatible IDE / PS/2 profile; this current ISO is not an XP driver pack.","Официальный ISO VirtIO для поддерживаемых современных версий Windows. Драйверы устанавливаются внутри гостевой ОС. XP использует совместимый профиль IDE / PS/2; этот современный ISO не является сборником драйверов XP."),style=MaterialTheme.typography.bodySmall)
                            DownloadFill(driver?.state?:"IDLE",if(driver!=null && driver.expectedBytes>0) driver.bytes.toFloat()/driver.expectedBytes else 0f,{
                                work {when(driver?.state) {"COMPLETE"->tools.selectDriver(machine,driver.id);"RUNNING","QUEUED","VERIFYING"->repository.pause(driver.id);"PAUSED","FAILED"->repository.resume(driver.id);"RESTART_REQUIRED"->repository.restart(driver.id);else->repository.queue(DriverDownloads.virtio)}}
                            },Modifier.fillMaxWidth().testTag("guest-driver-download"),idleLabel=tr("Download drivers · 837 MB","Скачать драйверы · 837 МБ"))
                            Text(tr("Publisher HTTPS source. A local SHA-256 is recorded after download; no publisher ISO SHA-256 is supplied.","Загрузка по HTTPS с сервера проекта. После скачивания сохраняется локальный SHA-256; контрольная сумма ISO от издателя не предоставлена."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(driver?.state=="COMPLETE") Button(onClick={work {tools.selectDriver(machine,driver!!.id)}},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("guest-attach-driver")) {Text(tr("Attach driver CD","Подключить CD драйверов"))}
                            OutlinedButton(onClick={importDriver.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text(tr("Import your driver ISO","Добавить свой ISO драйверов"))}
                            images.filter {it.state=="COMPLETE" && (it.flavor=="windows-drivers" || it.imported && it.title.endsWith(".iso",true))}.forEach {image->
                                FilterChip(selectedDriver==image.id,{work {tools.selectDriver(machine,image.id)}},label={Text(image.title,maxLines=2,overflow=TextOverflow.Ellipsis)},leadingIcon={if(selectedDriver==image.id)Icon(Icons.Default.Check,null,Modifier.size(18.dp))},enabled=!busy)
                            }
                            if(selectedDriver.isNotEmpty()) TextButton(onClick={work {tools.selectDriver(machine,"")}},enabled=!busy) {Text(tr("Eject driver CD","Извлечь CD драйверов"))}
                            Text(tr("In Windows, open the driver CD or select its folder in Device Manager. Installing drivers does not switch your existing disk controller.","В Windows откройте CD драйверов или укажите его папку в диспетчере устройств. Установка драйверов не меняет контроллер установленного диска."),style=MaterialTheme.typography.bodySmall)
                        }
                        2->{
                            Text(tr("Dynamic guest resolution","Динамическое разрешение ОС"),style=MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment=Alignment.CenterVertically) {Text(tr("Follow phone viewport","Подстраивать под экран телефона"),Modifier.weight(1f));Switch(autoResize,{value->autoResize=value;work {val d=tools.display(machine.id);tools.desiredDisplay(machine.id,d.optInt("width",1024),d.optInt("height",768),value)}},enabled=!busy)}
                            Text(if(agent.isEmpty()) tr("OSify Display helper is not connected","Помощник OSify Display не подключён") else agent,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tr("The picture fits the phone on rotation without a helper. To change resolution inside the OS, install the optional helper from the PHONE CD: Windows 7+ with PowerShell or Linux X11 with Python3/xrandr. The guest video driver must support the requested mode; XP and Wayland are not supported by this helper.","Изображение вписывается в экран при повороте без помощника. Для изменения разрешения внутри ОС установите помощник с диска PHONE: Windows 7+ с PowerShell или Linux X11 с Python3/xrandr. Режим должен поддерживаться видеодрайвером; этот помощник не поддерживает XP и Wayland."),style=MaterialTheme.typography.bodySmall)
                            Text("Windows: OSify-Install-Windows.cmd\nLinux: python3 OSify-Display.py --install",style=MaterialTheme.typography.bodySmall)
                            Text(tr("Tools communicate only through the private local VM connection. See OSify-Readme.txt on the CD for installation and removal.","Помощник работает через приватное локальное соединение с машиной. Инструкция по установке и удалению — OSify-Readme.txt на диске."),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
    remove?.let {file->AlertDialog(onDismissRequest={remove=null},title={Text(tr("Remove exchange copy?","Убрать копию с диска обмена?"))},text={Text(file.name)},confirmButton={TextButton(onClick={remove=null;work {tools.deleteSent(machine,file)}}) {Text(tr("Remove","Убрать"))}},dismissButton={TextButton(onClick={remove=null}) {Text(tr("Cancel","Отмена"))}})}
    removeReceived?.let {file->AlertDialog(onDismissRequest={removeReceived=null},title={Text(tr("Delete received copy?","Удалить принятую копию?"))},text={Text(file.name.substringAfter('-'))},confirmButton={TextButton(onClick={removeReceived=null;work {tools.deleteReceived(machine.id,file)}}) {Text(tr("Delete","Удалить"))}},dismissButton={TextButton(onClick={removeReceived=null}) {Text(tr("Cancel","Отмена"))}})}
}
