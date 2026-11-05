@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.osify.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.osify.app.AppViewModel
import dev.osify.app.data.CatalogResult
import dev.osify.app.data.ImageRecord
import dev.osify.core.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val WindowsExtras=SupplementalDownloads.windows
private fun sourceOnly(image: CatalogImage)=image.sha256.isBlank() && !OfficialWindows.isEvaluation(image)
private fun bestChoice(image: CatalogImage)=image.id=="tiny11-source"
private val ChoiceGold=Color(0xFFE8BF63)
private fun description(image: CatalogImage,ru: Boolean)=when(image.flavor) {
    "windows-11-eval"->if(ru) "Windows для работы, игр и знакомых приложений" else "Windows for work, play and familiar apps"
    "windows-10-source"->if(ru) "Классическая Windows" else "Classic Windows"
    "windows-xp-source"->if(ru) "Для старых программ и игр" else "Classic apps and games"
    "windows-7-source"->if(ru) "Знакомый рабочий стол" else "The familiar desktop"
    "windows-tiny10-source"->if(ru) "Облегчённая Windows 10 от NTDEV" else "NTDEV's lightweight Windows 10"
    "windows-tiny11-source"->if(ru) "Облегчённая Windows 11 от NTDEV" else "NTDEV's lightweight Windows 11"
    "ubuntu-desktop"->if(ru) "Полноценный рабочий стол Linux" else "A complete Linux desktop"
    "alpine-virt"->if(ru) "Лёгкий Linux для виртуальной машины" else "Lightweight Linux for a virtual machine"
    "alpine-standard"->if(ru) "Установочный образ Linux" else "A Linux installation image"
    else->if(ru) "${image.version} · Образ для виртуальной машины" else image.summary.ifBlank {"${image.version} · Virtual machine image"}.take(90)
}
private fun imageBadges(image: CatalogImage)=listOf(if(image.id=="windows-xp-source") "x86" else if(image.architecture==Architecture.X86_64) "x64" else "ARM64",if(image.flavor=="alpine-minirootfs") "rootfs" else "ISO")+if(image.bytes>0) listOf(formatBytes(image.bytes)) else emptyList()
private fun tileTag(image: CatalogImage)=when(image.flavor) {"windows-11-eval"->"catalog-windows-11";"windows-10-source"->"catalog-windows-10";else->"catalog-${image.flavor}"}

/** A measured card becomes the detail page. The fixed app header is outside this viewport. */
@Composable fun CatalogExperience(vm: AppViewModel,catalog: CatalogResult?,busy: Boolean,onDownload: (CatalogImage)->Unit,onUse: ()->Unit,onImport: ()->Unit,openLink: (String)->Unit,onRestart: (String)->Unit) {
    val russian=LocalRussian.current
    val motion=LocalMotion.current
    val scope=rememberCoroutineScope()
    val density=LocalDensity.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun stopTyping() {focus.clearFocus();keyboard?.hide()}
    val images by vm.images.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val order=CatalogOrder.from(prefs?.catalogSort?:"curated")
    var displayedOrder by remember {mutableStateOf(order)}
    val sortAlpha=remember {Animatable(1f)}
    LaunchedEffect(order) {
        if(order!=displayedOrder) {
            sortAlpha.animateTo(0f,tween(if(motion=="off") 0 else 90))
            displayedOrder=order
            sortAlpha.animateTo(1f,tween(if(motion=="off") 0 else 180))
        }
    }
    var favoritesOnly by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    val favorites=prefs?.favorites.orEmpty()
    var query by remember {mutableStateOf("")}
    var family by remember {mutableStateOf("all")}
    var format by remember {mutableStateOf("all")}
    var advanced by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<CatalogImage?>(null)}
    var selectedIndex by remember {mutableIntStateOf(0)}
    var origin by remember {mutableStateOf(Rect.Zero)}
    var actionOrigin by remember {mutableStateOf(Rect.Zero)}
    val buttonBounds=remember {mutableMapOf<String,Rect>()}
    var viewportOrigin by remember {mutableStateOf(Offset.Zero)}
    val bounds=remember {mutableMapOf<String,Rect>()}
    val reveal=remember {Animatable(0f)}
    val p=reveal.value
    val listScroll=rememberScrollState()
    val detailScroll=rememberScrollState()
    val all=(listOf(OfficialWindows.windows11)+WindowsExtras+listOf(OfficialUbuntu.desktop)+catalog?.images.orEmpty().filter {it.flavor !in setOf("alpine-minirootfs","alpine-standard")}).distinctBy {it.url}
    val ordered=orderCatalog(all,displayedOrder,if(russian) java.util.Locale.forLanguageTag("ru") else java.util.Locale.ENGLISH)
    val shown=all.filter {image->
        val windows=image.flavor.startsWith("windows")
        val minimal=image.flavor in setOf("alpine-minirootfs","alpine-virt")
        val text="${image.name} ${image.version} ${image.flavor} "+if(windows) "Microsoft Виндовс Винда" else "Linux Линукс Альпайн Ubuntu Убунту"
        (!favoritesOnly || image.flavor in favorites) && (query.isBlank() || text.contains(query,true)) && (family=="all" || (family=="windows")==windows) && (format=="all" || (format=="minimal" && minimal) || (format=="iso" && image.flavor!="alpine-minirootfs"))
    }
    var closing by remember {mutableStateOf(false)}
    val morphDuration=if(motion=="off") 0 else if(motion=="reduced") 120 else 760
    fun close() {
        if(closing) return
        closing=true
        scope.launch {
            try {
                detailScroll.stopScroll(MutatePriority.PreventUserInput)
                if(detailScroll.value>0) detailScroll.animateScrollTo(0,tween(if(motion=="off") 0 else 240,easing=FastOutSlowInEasing))
                // Exact time-reversed easing of entry, with the same geometry and duration.
                reveal.animateTo(0f,tween(morphDuration,easing=Easing {1f-FastOutSlowInEasing.transform(1f-it)}))
                selected=null
            } finally {closing=false}
        }
    }
    BackHandler(selected!=null) {close()}
    LaunchedEffect(selected?.id) {if(selected!=null) reveal.animateTo(1f,tween(morphDuration,easing=FastOutSlowInEasing))}
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)).onGloballyPositioned {viewportOrigin=it.boundsInRoot().topLeft}.testTag("catalog-experience")) {
        val viewportWidth=constraints.maxWidth.toFloat()
        AnimatedPageColumn("catalog",Modifier.fillMaxSize().verticalScroll(listScroll,enabled=selected==null).padding(horizontal=20.dp,vertical=12.dp),spacing=12.dp) {
            Column(Modifier.graphicsLayer {alpha=(1-p*4).coerceIn(0f,1f)},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(tr("Choose an OS","Выберите ОС"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(tr("Linux and Windows for your next machine","Linux и Windows для ваших машин"),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                OutlinedTextField(query,{query=it},placeholder={Text(tr("Search operating systems","Поиск операционных систем"),style=MaterialTheme.typography.bodyMedium)},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth().testTag("catalog-search"),shape=RoundedCornerShape(20.dp),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={stopTyping()}))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    FilterChip(family=="all" && format=="all" && !favoritesOnly,{stopTyping();family="all";format="all";favoritesOnly=false},label={Text(tr("All","Все"))},modifier=Modifier.testTag("filter-all"))
                    FilterChip(family=="linux",{stopTyping();family=if(family=="linux") "all" else "linux"},label={Text("Linux")},modifier=Modifier.testTag("filter-linux"))
                    FilterChip(family=="windows",{stopTyping();family=if(family=="windows") "all" else "windows"},label={Text("Windows")},modifier=Modifier.testTag("filter-windows"))
                    FilterChip(format=="minimal",{format=if(format=="minimal") "all" else "minimal"},label={Text(tr("Minimal","Минимальные"))},modifier=Modifier.testTag("filter-minimal"))
                    FilterChip(favoritesOnly,{favoritesOnly=!favoritesOnly},leadingIcon={Icon(Icons.Default.Star,null,Modifier.size(16.dp))},label={Text(tr("Favorites","Избранное"))},modifier=Modifier.testTag("filter-favorites"))
                    FilterChip(format=="iso",{format=if(format=="iso") "all" else "iso"},label={Text("ISO")},modifier=Modifier.testTag("filter-iso"))
                }
                CatalogSortMenu(order,shown.size) {stopTyping();vm.string("catalog_sort",it.key)}
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ordered.forEachIndexed {index,image->key(image.id) {
              AnimatedVisibility(image in shown,enter=fadeIn(tween(400))+slideInHorizontally(tween(600,easing=MotionTokens.settle)) {-it}+expandVertically(tween(600,easing=MotionTokens.settle)),exit=fadeOut(tween(250))+slideOutHorizontally(tween(450,easing=MotionTokens.settle)) {-it}+shrinkVertically(tween(550,easing=MotionTokens.settle))) {
                val enter=remember {Animatable(if(motion=="off") 1f else 0f)}
                LaunchedEffect(Unit) {enter.animateTo(1f,tween(if(motion=="off") 0 else 650,delayMillis=if(motion=="full") minOf(index,8)*55 else 0,easing=MotionTokens.settle))}
                val delay=(index-selectedIndex).coerceAtLeast(0).coerceAtMost(8)*.065f
                val exit=((p-delay)/(1-delay)).coerceIn(0f,1f)
                val travel=MotionTokens.settle.transform(exit)
                Box(Modifier.fillMaxWidth().onGloballyPositioned {bounds[image.id]=it.boundsInRoot()}.graphicsLayer {
                    alpha=if(selected?.id==image.id) 0f else (1f-travel)*enter.value*sortAlpha.value
                    translationX=if(motion=="full") -viewportWidth*(travel+(1-enter.value)*.3f) else 0f
                }) {
                    val row=images.firstOrNull {it.url==image.url && !it.imported && it.state!="CANCELLED"}
                    CatalogPreview(image,row,image.flavor in favorites,{vm.toggleFavorite(image.flavor)},onAction={
                        stopTyping()
                        when(row?.state) {
                            "COMPLETE"->onUse();"RUNNING","QUEUED"->vm.pause(row.id);"PAUSED","FAILED"->vm.resume(row.id);"RESTART_REQUIRED"->onRestart(row.id)
                            else->if(sourceOnly(image)) onImport() else onDownload(image)
                        }
                    },onActionBounds={buttonBounds[image.id]=it}) {
                        stopTyping()
                        bounds[image.id]?.let {r->origin=r.translate(-viewportOrigin)}
                        buttonBounds[image.id]?.let {r->actionOrigin=r.translate(-viewportOrigin)}
                        selectedIndex=index;selected=image
                    }
                }
              }
            }}
            if(shown.isEmpty()) Text(tr("No matching images. Try another search.","Ничего не найдено. Попробуйте другой запрос."))
            Column(Modifier.graphicsLayer {alpha=1-p},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={advanced=!advanced},modifier=Modifier.weight(1f)) {Text(tr("Sources and architecture","Источники и архитектура"))}
                    IconButton(onClick={vm.refreshCatalog(true)},enabled=!busy,modifier=Modifier.testTag("catalog-refresh")) {Icon(Icons.Default.Refresh,tr("Refresh catalog","Обновить каталог"))}
                }
                AnimatedVisibility(advanced) { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(Architecture.ARM64,Architecture.X86_64).forEach {arch->FilterChip(catalog?.images?.firstOrNull()?.architecture==arch,{vm.refreshCatalog(false,arch)},label={Text(arch.name)})}}
                    Text(tr("Quickget open catalog + official Alpine, Ubuntu and Microsoft sources.","Открытый каталог Quickget + официальные источники Alpine, Ubuntu и Microsoft."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    catalog?.let {Text(if(it.bundled) tr("Bundled snapshot: 2 Nov 2026","Снимок каталога: 2 ноября 2026") else tr("Checked: ","Проверен: ")+DateFormat.getDateTimeInstance().format(Date(it.fetchedAt)),style=MaterialTheme.typography.labelSmall)}
                }}
            }
            Spacer(Modifier.height(12.dp))
        }
        selected?.let {image->
            val row=images.firstOrNull {it.url==image.url && !it.imported && it.state!="CANCELLED"}
            ExpandedCatalog(image,row,p,origin,actionOrigin,maxHeight,detailScroll,close={close()},action={
                when(row?.state) {
                    "COMPLETE"->onUse();"RUNNING","QUEUED"->vm.pause(row.id);"PAUSED","FAILED"->vm.resume(row.id);"RESTART_REQUIRED"->onRestart(row.id)
                    else->if(sourceOnly(image)) onImport() else onDownload(image)
                }
            },onImport=onImport,openLink=openLink)
        }
        ScrollFade(if(selected==null) listScroll else detailScroll,Modifier.align(Alignment.TopCenter),minimumOpacity=p)
    }
}

@Composable private fun CatalogPreview(image: CatalogImage,row: ImageRecord?,favorite: Boolean,onFavorite: ()->Unit,onAction: ()->Unit,onActionBounds: (Rect)->Unit,select: ()->Unit) {
    val identity=osIdentity(image.name)
    val base=baseColor(identity)
    Surface(onClick=select,modifier=Modifier.fillMaxWidth().heightIn(min=156.dp).testTag(tileTag(image)),shape=RoundedCornerShape(22.dp),color=base,contentColor=TileInk,border=if(bestChoice(image)) androidx.compose.foundation.BorderStroke(1.5.dp,ChoiceGold) else null) {
        Box {
            HeroArtwork(identity,base,Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().heightIn(min=if(bestChoice(image)) 178.dp else 156.dp).padding(15.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(11.dp),verticalAlignment=Alignment.Top) {
                    OsLogo(identity,Modifier.size(48.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        if(bestChoice(image)) ChoiceLabel()
                        Text(image.name,fontWeight=FontWeight.Bold,fontSize=19.sp,lineHeight=24.sp)
                        Text(description(image,LocalRussian.current),color=TileMuted,fontSize=12.sp,lineHeight=17.sp,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    IconToggleButton(checked=favorite,onCheckedChange={onFavorite()},modifier=Modifier.size(34.dp).testTag("favorite-${image.flavor}")) {
                        Icon(if(favorite) Icons.Default.Star else Icons.Default.StarBorder,tr("Favorite","В избранное"),Modifier.size(22.dp),tint=TileInk)
                    }
                }
                Spacer(Modifier.weight(1f).heightIn(min=12.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {imageBadges(image).take(2).forEach {TileBadge(it)}}
                    DownloadFill(row?.state?:"IDLE",if(row!=null && row.expectedBytes>0) row.bytes.toFloat()/row.expectedBytes else 0f,onAction,Modifier.width(148.dp).onGloballyPositioned {onActionBounds(it.boundsInRoot())},compact=true,idleLabel=if(sourceOnly(image)) tr("Import","Добавить") else tr("Download","Скачать"))
                }
            }
        }
    }
}

private fun baseColor(identity: OsIdentity)=when(identity) {OsIdentity.WINDOWS,OsIdentity.WINDOWS10->Color(0xFF102849);OsIdentity.UBUNTU->Color(0xFF35152E);else->Color(0xFF122F43)}
@Composable private fun HeroArtwork(identity: OsIdentity,base: Color,modifier: Modifier=Modifier,expanded: Float=0f) {
    Box(modifier.background(base)) {
        OsArtwork(identity,Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(base.copy(alpha=.95f*(1-expanded)),base.copy(alpha=.65f*(1-expanded)),Color.Transparent))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(base.copy(alpha=.08f),Color.Transparent,base.copy(alpha=.96f)))))
    }
}

@Composable private fun ExpandedCatalog(image: CatalogImage,row: ImageRecord?,fraction: Float,origin: Rect,actionOrigin: Rect,viewportHeight: Dp,detailScroll: androidx.compose.foundation.ScrollState,close: ()->Unit,action: ()->Unit,onImport: ()->Unit,openLink: (String)->Unit) {
    val density=LocalDensity.current
    val p=fraction.coerceIn(0f,1f)
    val identity=osIdentity(image.name)
    val base=baseColor(identity)
    var recommendationHeight by remember {mutableStateOf(120.dp)}
    var supportHeight by remember {mutableStateOf(260.dp)}
    fun blend(a: Float,b: Float)=a+(b-a)*p
    fun dp(px: Float)=with(density) {px.toDp()}
    val top=dp(blend(origin.top,0f))
    val left=dp(blend(origin.left,0f))
    val compactHeight=dp(origin.height).coerceAtLeast(150.dp)
    val heroHeight=if(density.fontScale>1.2f) 310.dp else 278.dp
    val artworkHeight=compactHeight+(heroHeight-compactHeight)*p
    val buttonTop=heroHeight+64.dp+recommendationHeight+16.dp
    val movingButtonY=dp(blend(actionOrigin.top,with(density){buttonTop.toPx()}))
    // Reveal supporting content only after the travelling action has cleared its bounds.
    val clearance=((movingButtonY-(heroHeight+64.dp+recommendationHeight)).value/16f).coerceIn(0f,1f)
    val detailsAlpha=((p-.35f)/.65f).coerceIn(0f,1f)*clearance
    BoxWithConstraints(Modifier.fillMaxSize().testTag("catalog-detail")) {
        val targetWidth=maxWidth
        val surfaceWidth=dp(origin.width)+(targetWidth-dp(origin.width))*p
        val buttonWidth=dp(actionOrigin.width)+(targetWidth-40.dp-dp(actionOrigin.width))*p
        val contentHeight=maxOf(viewportHeight,buttonTop+100.dp+supportHeight+24.dp)
        Box(Modifier.fillMaxSize().verticalScroll(detailScroll,enabled=p>.99f)) {
            Box(Modifier.fillMaxWidth().height(contentHeight)) {
                Box(Modifier.fillMaxWidth().height(contentHeight).graphicsLayer {alpha=p}.background(MaterialTheme.colorScheme.background))
                Box(Modifier.offset(left,top).width(surfaceWidth).height(artworkHeight).clip(RoundedCornerShape(22.dp*(1-p))).testTag("catalog-detail-hero")) {
                    HeroArtwork(identity,base,Modifier.matchParentSize(),p)
                    Row(Modifier.offset(x=15.dp+5.dp*p,y=(15.dp+(heroHeight-94.dp-15.dp)*p)),horizontalArrangement=Arrangement.spacedBy(11.dp),verticalAlignment=Alignment.Top) {
                        OsLogo(identity,Modifier.size(48.dp))
                        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            if(bestChoice(image)) Box(Modifier.graphicsLayer {alpha=((p-.7f)/.3f).coerceIn(0f,1f)}) {ChoiceLabel()}
                            Box(Modifier.height(with(density) {(24+8*p).sp.toDp()})) {
                                Text(image.name,color=TileInk,fontSize=27.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold,maxLines=1,softWrap=false,
                                    modifier=Modifier.width((targetWidth-99.dp).coerceAtLeast(1.dp)).graphicsLayer {
                                        val scale=(19+8*p)/27f;scaleX=scale;scaleY=scale;transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f)
                                    })
                            }
                            // Two endpoint layouts keep their width and typography for the entire morph.
                            // They cross-fade only near their endpoint; no visible paragraph is reflowed.
                            Box {
                                val compactAlpha=(1-p/.15f).coerceIn(0f,1f)
                                val expandedAlpha=((p-.85f)/.15f).coerceIn(0f,1f)
                                Text(description(image,LocalRussian.current),modifier=Modifier.width((dp(origin.width)-89.dp).coerceAtLeast(1.dp)).graphicsLayer {alpha=compactAlpha}.testTag("catalog-description-compact"),color=TileMuted,fontSize=12.sp,lineHeight=17.sp,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(description(image,LocalRussian.current),modifier=Modifier.width((targetWidth-99.dp).coerceAtLeast(1.dp)).graphicsLayer {alpha=expandedAlpha}.testTag("catalog-description-expanded"),color=TileMuted,fontSize=12.sp,lineHeight=17.sp,maxLines=3)
                            }
                        }
                    }
                }
                Box(Modifier.offset(x=dp(blend(origin.left+with(density){15.dp.toPx()},with(density){20.dp.toPx()})),y=dp(blend(origin.bottom-with(density){(23.dp+14.sp.toDp()).toPx()},with(density){(heroHeight+16.dp).toPx()})))) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {imageBadges(image).forEachIndexed {index,badge->
                        Box(Modifier.graphicsLayer {alpha=if(index<2) 1f else ((p-.35f)/.65f).coerceIn(0f,1f)}) {TileBadge(badge,color=if(p>.5f) MaterialTheme.colorScheme.onSurfaceVariant else TileMuted)}
                    }}
                }
                Column(Modifier.offset(y=heroHeight+64.dp).padding(horizontal=20.dp).fillMaxWidth().onSizeChanged {recommendationHeight=with(density){it.height.toDp()}}.graphicsLayer {alpha=detailsAlpha;translationY=(1-detailsAlpha)*24.dp.toPx()},verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(tr("Before you start","Для запуска"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
                    val windows=image.flavor.startsWith("windows")
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Requirement(Icons.Default.Memory,"RAM",when {image.id=="windows-xp-source"->"256 MB";image.id=="windows-7-source"->"1 GB";image.id.startsWith("tiny")->"2 GB";windows || image.flavor=="ubuntu-desktop"->"4 GB";else->"1 GB"},Modifier.weight(1f))
                        Requirement(Icons.Default.Storage,tr("Disk","Диск"),when {image.id=="windows-xp-source"->"4 GB";image.id=="windows-7-source" || image.id.startsWith("tiny")->"16 GB";windows->"64 GB";image.flavor=="ubuntu-desktop"->"32 GB";else->"8 GB"},Modifier.weight(1f))
                        Requirement(Icons.Default.Terminal,tr("Engine","Среда"),if(image.flavor=="alpine-minirootfs") "Termux" else "QEMU",Modifier.weight(1f))
                    }
                }
                val buttonX=dp(blend(actionOrigin.left,with(density){20.dp.toPx()}))
                val buttonY=movingButtonY
                DownloadFill(row?.state?:"IDLE",if(row!=null && row.expectedBytes>0) row.bytes.toFloat()/row.expectedBytes else 0f,action,Modifier.offset(buttonX,buttonY).width(buttonWidth).testTag("image-download"),expansion=p,bytes=row?.bytes,totalBytes=row?.expectedBytes?:image.bytes,idleLabel=if(sourceOnly(image)) tr("Add ISO","Добавить ISO") else tr("Download","Скачать"))
                Column(Modifier.offset(y=buttonTop+100.dp).fillMaxWidth().padding(horizontal=20.dp).onSizeChanged {supportHeight=with(density){it.height.toDp()}}.graphicsLayer {alpha=detailsAlpha;translationY=(1-detailsAlpha)*32.dp.toPx()},verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    if(row?.state=="COMPLETE") FilledTonalButton(onClick=action,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Icon(Icons.Default.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(tr("Continue in Launcher","Продолжить в Launcher"))}
                    row?.error?.takeIf {it.isNotBlank() && row.state in setOf("FAILED","RESTART_REQUIRED")}?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                    OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(18.dp)) {Icon(Icons.Default.FileOpen,null);Spacer(Modifier.width(8.dp));Text(tr("Or import your ISO","Или добавьте свой ISO"))}
                    val windows=image.flavor.startsWith("windows")
                    val info=when {
                        sourceOnly(image)->tr("Add your own ISO. The publisher link is in the source details below.","Добавьте свой ISO. Ссылка на сайт автора находится в сведениях об источнике.")
                        image.flavor.startsWith("quickget-")->tr("Download from the distribution mirror. Its published checksum is checked before use.","Загрузка с зеркала дистрибутива. Перед использованием проверим опубликованную контрольную сумму.")
                        OfficialWindows.isEvaluation(image)->tr("Official Microsoft evaluation, 90 days. A local checksum identifies the file; publisher verification is unavailable.","Официальная ознакомительная версия Microsoft на 90 дней. Сохраняется локальный хеш файла; эталонный хеш издателя недоступен.")
                        windows->tr("Archived installation ISO. Downloaded directly and checked against the pinned archive checksum. A Windows license is required.","Архивный установочный ISO. Прямая загрузка с проверкой сохранённой контрольной суммы архива. Нужна лицензия Windows.")
                        else->tr("Downloaded from the official source. The SHA-256 is checked before the file is ready.","Загрузка из официального источника. Перед завершением проверим SHA-256 файла.")
                    }
                    SupportingInfo(Icons.Default.VerifiedUser,tr("About this image","Об этом образе"),info)
                    SupportingInfo(Icons.Default.Info,tr("What happens next","Что дальше"),if(image.flavor=="alpine-minirootfs") tr("This is a filesystem archive, not a bootable ISO. Use the separate Alpine environment in Termux.","Это архив файловой системы, а не загрузочный ISO. Для Alpine используйте отдельную среду в Termux.") else tr("Create a machine in Launcher and select this ISO. An x64 guest on an ARM phone can be slow. Requirements depend on the OS.","Создайте машину в Launcher и выберите ISO. Эмуляция x64 на ARM-телефоне может быть медленной. Требования зависят от ОС."))
                    var technical by remember {mutableStateOf(false)}
                    TextButton(onClick={technical=!technical},modifier=Modifier.testTag("image-technical")) {Text(tr("Source and technical details","Источник и технические сведения"));Icon(if(technical) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)}
                    AnimatedVisibility(technical) { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("${image.version}\n${image.architecture}\n${image.url}"+(if(image.sha256.isNotEmpty()) "\n\n${ImageChecksums.label(image.sha256)}\n${ImageChecksums.value(image.sha256)}" else ""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={openLink(image.source)}) {Text(tr("Image source","Источник образа"))}
                    }}
                }
            }
        }
                IconButton(onClick=close,modifier=Modifier.offset(12.dp,top+12.dp).graphicsLayer {alpha=((p-.3f)/.7f).coerceIn(0f,1f)}.size(44.dp).clip(RoundedCornerShape(16.dp)).background(base.copy(alpha=.75f)).testTag("catalog-detail-back")) {Icon(Icons.AutoMirrored.Filled.ArrowBack,tr("Back to catalog","Вернуться в каталог"),tint=Color.White)}
    }
}

@Composable private fun Requirement(icon: androidx.compose.ui.graphics.vector.ImageVector,label: String,value: String,modifier: Modifier) {
    Surface(modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(icon,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.primary)
            Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,fontSize=12.sp,lineHeight=17.sp,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable internal fun SupportingInfo(icon: androidx.compose.ui.graphics.vector.ImageVector,title: String,text: String) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,Modifier.padding(top=2.dp).size(21.dp),tint=MaterialTheme.colorScheme.primary)
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold);Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable private fun ChoiceLabel() {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.Star,null,Modifier.size(13.dp),tint=ChoiceGold)
        Text(tr("Best choice","Лучший выбор"),color=ChoiceGold,fontWeight=FontWeight.Bold,fontSize=10.sp,lineHeight=14.sp)
    }
}
