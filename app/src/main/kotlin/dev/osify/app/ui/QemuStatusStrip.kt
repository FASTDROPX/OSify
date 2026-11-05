package dev.osify.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.osify.app.OsifyApplication
import dev.osify.app.data.VirtualMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class QemuPackage { CHECKING, READY, SETUP, ERROR }
private data class QemuCheck(val state: QemuPackage,val detail: String="")

/** Read-only package check. Never loads QEMU into the UI process or starts a guest. */
private fun checkQemu(context: Context): QemuCheck {
    if(Build.SUPPORTED_64_BIT_ABIS.none {it=="arm64-v8a" || it=="x86_64"}) return QemuCheck(QemuPackage.ERROR,"ABI")
    val libraries=listOf("qemu-system-x86_64","osifyvm","limbo","glib-2.0","pixman-1","SDL2","compat-limbo","compat-musl","compat-SDL2-addons","compat-SDL2-ext")
    return try {
        for(name in libraries) {
            val file=File(context.applicationInfo.nativeLibraryDir,"lib$name.so")
            if(!file.isFile) return QemuCheck(QemuPackage.SETUP,file.name)
            val header=ByteArray(5)
            val count=file.inputStream().use {it.read(header)}
            if(count!=5 || !header.copyOfRange(0,4).contentEquals(byteArrayOf(127,69,76,70)) || header[4]!=2.toByte()) return QemuCheck(QemuPackage.ERROR,file.name)
        }
        for(name in listOf("bios.bin","bios-256k.bin","vgabios-stdvga.bin","OVMF_CODE.fd","OVMF_VARS.fd")) {
            val present=runCatching {context.assets.open("qemu/$name").use {it.read()!=-1}}.getOrDefault(false)
            if(!present) return QemuCheck(QemuPackage.SETUP,name)
        }
        QemuCheck(QemuPackage.READY)
    } catch(_: Exception) {QemuCheck(QemuPackage.ERROR,"I/O")}
}

@Composable internal fun QemuStatusStrip(machines: List<VirtualMachine>,onLog: (VirtualMachine)->Unit) {
    val context=LocalContext.current
    var revision by remember {mutableIntStateOf(0)}
    var expanded by remember {mutableStateOf(false)}
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {revision++}
    val check by produceState(QemuCheck(QemuPackage.CHECKING),revision) {
        value=QemuCheck(QemuPackage.CHECKING)
        value=withContext(Dispatchers.IO) {
            runCatching {(context.applicationContext as OsifyApplication).vmEngine.prepare()}
            checkQemu(context)
        }
    }
    val active=machines.firstOrNull {it.state in setOf("REQUESTED","DISPLAY_AVAILABLE","PAUSED","SHUTDOWN_REQUESTED")}
    val failed=if(active==null) machines.firstOrNull {it.state=="FAILED"} else null
    val state=if(check.state==QemuPackage.READY) active?.state?:if(failed!=null) "FAILED" else "READY" else check.state.name
    val label=when(state) {
        "READY"->tr("Ready","Готов")
        "REQUESTED"->tr("Starting","Запуск")
        "DISPLAY_AVAILABLE"->tr("Running","Работает")
        "PAUSED"->tr("Paused","Пауза")
        "SHUTDOWN_REQUESTED"->tr("Stopping","Остановка")
        "FAILED"->tr("Launch failed","Ошибка запуска")
        "SETUP"->tr("Setup required","Нужна настройка")
        "ERROR"->tr("Component error","Ошибка компонентов")
        else->tr("Checking…","Проверка…")
    }
    val duration=if(LocalMotion.current=="off") 0 else 200
    val tint=if(state in setOf("FAILED","ERROR")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.fillMaxWidth().testTag("qemu-status")) {
        Column {
            Surface(onClick={expanded=!expanded},color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.testTag("qemu-status-toggle")) {
                Row(Modifier.fillMaxWidth().heightIn(min=52.dp).padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Memory,null,Modifier.size(22.dp),tint=tint)
                    Text("QEMU",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
                    AnimatedContent(label,modifier=Modifier.weight(1f),transitionSpec={fadeIn(tween(duration))+slideInVertically(tween(duration)) {it/3} togetherWith fadeOut(tween(duration))},label="QEMU status") {
                        Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if(state in setOf("FAILED","ERROR","SETUP")) Icons.Default.ErrorOutline else if(state=="CHECKING") Icons.Default.HourglassEmpty else Icons.Default.CheckCircle,null,Modifier.size(18.dp),tint=tint)
                    Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,tr("Details","Подробнее"),Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(expanded,enter=expandVertically(tween(duration))+fadeIn(tween(duration)),exit=shrinkVertically(tween(duration))+fadeOut(tween(duration))) {
                Column(Modifier.fillMaxWidth().padding(start=14.dp,end=14.dp,bottom=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(when {
                        check.state==QemuPackage.CHECKING->tr("Checking bundled engine and firmware.","Проверяем встроенный движок и прошивки.")
                        check.detail=="ABI"->tr("This APK needs an ARM64 or x86_64 device.","Этому APK нужно устройство ARM64 или x86_64.")
                        check.state!=QemuPackage.READY->tr("A bundled component is missing or unreadable. Reinstall the complete APK. Component: ","Компонент отсутствует или не читается. Установите полный APK заново. Компонент: ")+check.detail
                        active!=null->active.name
                        failed!=null->failed.name+" · "+tr("Open its runtime log for details.","Подробности — в журнале машины.")
                        else->tr("Built-in engine, BIOS and UEFI are present. Guest compatibility depends on the image and machine settings.","Встроенный движок, BIOS и UEFI на месте. Совместимость гостевой ОС зависит от образа и настроек машины.")
                    },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick={revision++},enabled=check.state!=QemuPackage.CHECKING,modifier=Modifier.testTag("qemu-recheck")) {Icon(Icons.Default.Refresh,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(tr("Check","Проверить"))}
                        (active?:failed)?.let {machine->TextButton(onClick={onLog(machine)}) {Icon(Icons.Default.ReceiptLong,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(tr("Log","Журнал"))}}
                    }
                }
            }
        }
    }
}
