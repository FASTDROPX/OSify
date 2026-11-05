package dev.osify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.osify.app.AppViewModel

@Composable internal fun DashboardHero(catalog: ()->Unit,launcher: ()->Unit) {
    Surface(shape=RoundedCornerShape(28.dp),color=Color(0xFF102849),contentColor=TileInk) {
        Box {
            OsArtwork(OsIdentity.WINDOWS,Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xFF102849),Color(0xE6102849),Color(0x35102849)))))
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OsLogo(OsIdentity.WINDOWS,Modifier.size(36.dp));OsLogo(OsIdentity.UBUNTU,Modifier.size(36.dp));OsLogo(OsIdentity.ALPINE,Modifier.size(36.dp))}
                Text(tr("An OS for your next idea","Система для ваших идей"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth())
                Text(tr("Choose an image and launch your OS.","Выберите образ и запустите свою ОС."),style=MaterialTheme.typography.bodySmall,color=TileMuted,modifier=Modifier.fillMaxWidth(.9f))
                Button(onClick=catalog,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Icon(Icons.Default.GridView,null);Spacer(Modifier.width(8.dp));Text(tr("Choose an OS","Выбрать ОС"));Spacer(Modifier.weight(1f));Icon(Icons.AutoMirrored.Filled.ArrowForward,null)}
            }
        }
    }
}

@Composable internal fun HomeMachineSummary(vm: AppViewModel,open: ()->Unit) {
    val machines by vm.virtualMachines.collectAsStateWithLifecycle()
    if(machines.isNotEmpty()) {
        Surface(onClick=open,shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                val first=machines.first()
                OsLogo(osIdentity(first.name,first.guest),Modifier.size(48.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(tr("My machines","Мои машины"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if(machines.size==1) first.name else tr("${machines.size} saved machines","Сохранено машин: ${machines.size}"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)}
                Icon(Icons.Default.ChevronRight,null)
            }
        }
    }
}

@Composable internal fun ImageLibraryEmpty(catalog: ()->Unit) {
    Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {OsLogo(OsIdentity.WINDOWS,Modifier.size(48.dp));OsLogo(OsIdentity.UBUNTU,Modifier.size(48.dp));OsLogo(OsIdentity.ALPINE,Modifier.size(48.dp))}
            Text(tr("Your first system starts here","Здесь будет ваша первая система"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(tr("Download an official image from the catalog. Its progress and file will appear here.","Скачайте официальный образ из каталога. Здесь появятся прогресс загрузки и готовый файл."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick=catalog,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Icon(Icons.Default.GridView,null);Spacer(Modifier.width(8.dp));Text(tr("Open catalog","Открыть каталог"))}
        }
    }
}
