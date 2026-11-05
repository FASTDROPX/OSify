@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.osify.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.osify.core.CatalogImage
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.launch

internal enum class CatalogOrder(val key: String,val en: String,val ru: String,val icon: ImageVector) {
    CURATED("curated","Catalog order","Порядок каталога",Icons.Default.GridView),
    NAME_ASC("name_asc","Name · A–Z","Название · А–Я",Icons.Default.SortByAlpha),
    NAME_DESC("name_desc","Name · Z–A","Название · Я–А",Icons.Default.SortByAlpha),
    SIZE_ASC("size_asc","Size · smallest first","Размер · по возрастанию",Icons.Default.ArrowUpward),
    SIZE_DESC("size_desc","Size · largest first","Размер · по убыванию",Icons.Default.ArrowDownward);
    companion object { fun from(key: String)=entries.firstOrNull {it.key==key}?:CURATED }
}

/** Stable ordering: unknown sizes are last in either direction; the source order breaks ties. */
internal fun orderCatalog(images: List<CatalogImage>,order: CatalogOrder,locale: Locale): List<CatalogImage> {
    if(order==CatalogOrder.CURATED) return images
    val collator=Collator.getInstance(locale).apply {strength=Collator.SECONDARY}
    return images.sortedWith {a,b->when(order) {
        CatalogOrder.NAME_ASC->collator.compare(a.name,b.name)
        CatalogOrder.NAME_DESC->collator.compare(b.name,a.name)
        else->when {
            a.bytes<=0 && b.bytes<=0->0
            a.bytes<=0->1
            b.bytes<=0->-1
            order==CatalogOrder.SIZE_ASC->a.bytes.compareTo(b.bytes)
            else->b.bytes.compareTo(a.bytes)
        }
    }}
}

@Composable internal fun CatalogSortMenu(order: CatalogOrder,count: Int,onSelect: (CatalogOrder)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val scope=rememberCoroutineScope()
    val duration=if(LocalMotion.current=="off") 0 else 180
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(tr("$count systems","Систем: $count"),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={expanded=true},contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),modifier=Modifier.testTag("catalog-sort")) {
            Icon(Icons.Default.Sort,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp))
            AnimatedContent(order,transitionSpec={fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))},label="sort label") {value->
                Text(tr(value.en,value.ru),style=MaterialTheme.typography.labelMedium,maxLines=1)
            }
            Spacer(Modifier.width(4.dp));Icon(Icons.Default.ExpandMore,null,Modifier.size(18.dp))
        }
    }
    if(expanded) ModalBottomSheet(onDismissRequest={expanded=false},sheetState=sheet,modifier=Modifier.testTag("catalog-sort-sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(tr("Sort systems","Сортировка систем"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(tr("Unknown download sizes appear last.","Образы с неизвестным размером — в конце."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=8.dp))
            Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                CatalogOrder.entries.forEach {item->
                    val selected=item==order
                    val color by animateColorAsState(if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,tween(duration),label="sort selection")
                    Surface(color=color,shape=RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().testTag("sort-${item.key}").selectable(selected,role=Role.RadioButton,onClick={
                            onSelect(item)
                            scope.launch {sheet.hide();expanded=false}
                        }).padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Icon(item.icon,null,Modifier.size(22.dp))
                            Text(tr(item.en,item.ru),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                            AnimatedVisibility(selected,enter=fadeIn(tween(duration)),exit=fadeOut(tween(duration))) {Icon(Icons.Default.Check,null,Modifier.size(20.dp))}
                        }
                    }
                }
            }
        }
    }
}
