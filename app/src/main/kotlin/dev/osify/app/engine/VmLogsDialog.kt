package dev.osify.app.engine

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.osify.app.OsifyApplication
import dev.osify.app.data.VirtualMachine
import dev.osify.app.ui.tr
import kotlinx.coroutines.*

@Composable fun VmLogsDialog(machine: VirtualMachine,onDismiss: ()->Unit,onDiagnosticCommand: ((String)->Unit)?=null) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var report by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    suspend fun refresh() {
        val app=context.applicationContext as OsifyApplication
        report=withContext(Dispatchers.IO) {VmLogs.report(context,app.database.dao().virtualMachine(machine.id)?:machine)}
    }
    fun perform(action: suspend ()->Unit) {scope.launch {busy=true;runCatching {action()}.onFailure {error=it.message.orEmpty()};busy=false}}
    LaunchedEffect(machine.id) {perform {refresh()}}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {uri->
        if(uri!=null) perform {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(report.toByteArray(Charsets.UTF_8))}?:error("Cannot open the document")}}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(12.dp),shape=RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(tr("Machine logs","Логи машины"),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick=onDismiss,modifier=Modifier.testTag("logs-close")) {Icon(Icons.Default.Close,tr("Close","Закрыть"))}
                }
                Text(tr("QEMU, app events and guest serial output. Refresh after reproducing a problem.","QEMU, события приложения и вывод гостя в serial. Обновите после появления ошибки."),style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    FilledTonalIconButton(onClick={perform {refresh()}},enabled=!busy,modifier=Modifier.testTag("logs-refresh")) {Icon(Icons.Default.Refresh,tr("Refresh logs","Обновить логи"))}
                    FilledTonalIconButton(onClick={save.launch("OSify-${machine.id.take(8)}.txt")},enabled=!busy && report.isNotEmpty(),modifier=Modifier.testTag("logs-save")) {Icon(Icons.Default.SaveAlt,tr("Save TXT","Сохранить TXT"))}
                    FilledTonalIconButton(onClick={perform {
                        val file=withContext(Dispatchers.IO) {VmLogs.snapshot(context,report)}
                        context.startActivity(Intent.createChooser(VmLogs.shareIntent(context,file),null))
                    }},enabled=!busy && report.isNotEmpty(),modifier=Modifier.testTag("logs-share")) {Icon(Icons.Default.Share,tr("Share TXT","Отправить TXT"))}
                    FilledTonalIconButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("OSify logs",report))},enabled=!busy && report.isNotEmpty()) {Icon(Icons.Default.ContentCopy,tr("Copy logs","Скопировать логи"))}
                }
                if(machine.guest=="LINUX") OutlinedButton(onClick={
                    if(onDiagnosticCommand!=null) onDiagnosticCommand(VmLogs.LINUX_DIAGNOSTICS)
                    else context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Linux diagnostics",VmLogs.LINUX_DIAGNOSTICS))
                },modifier=Modifier.fillMaxWidth().testTag("logs-command")) {Text(if(onDiagnosticCommand!=null) tr("Prepare guest diagnostics","Собрать журнал Linux") else tr("Copy Linux diagnostics command","Копировать команду Linux"))}
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
                Surface(Modifier.weight(1f).fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
                    SelectionContainer {Text(report,modifier=Modifier.padding(12.dp).verticalScroll(rememberScrollState()).testTag("logs-content"),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
                }
                Text(tr("Linux command requires a guest shell; review it and press Enter in the guest. It sends journal + network state to serial.","Команда требует консоль Linux: проверьте её и нажмите Enter в госте. Она пишет журнал и состояние сети в serial."),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
