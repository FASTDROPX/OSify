package dev.osify.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import dev.osify.app.R
import dev.osify.app.data.ImageRecord
import dev.osify.core.CatalogImage
import kotlin.math.pow

val BodyFont=FontFamily(Font(R.font.comfortaa),Font(R.font.comfortaa_bold,FontWeight.Bold))
val HeadingFont=FontFamily(Font(R.font.unbounded))
val NumberFont=FontFamily(Font(R.font.outfit),Font(R.font.outfit_bold,FontWeight.Bold))
val WordmarkFont=FontFamily(Font(R.font.syne))
object MotionTokens {
    val settle=CubicBezierEasing(.2f,.8f,.15f,1f)
    const val morph=650
    const val confirmation=180
}
@Composable fun NavigationReflow(selected: String,onSelect: (String)->Unit) {
    val keys=listOf("home","catalog","launcher","library","settings")
    val labels=listOf(tr("Home","Главная"),tr("Catalog","Каталог"),"Launcher",tr("Downloads","Загрузки"),tr("Settings","Настройки"))
    val icons=listOf(Icons.Default.Home,Icons.Default.GridView,Icons.Default.PlayArrow,Icons.Default.Download,Icons.Default.Tune)
    val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
    val index=keys.indexOf(selected)
    val duration=if(LocalMotion.current=="off") 0 else if(LocalMotion.current=="reduced") 100 else MotionTokens.morph
    val topRight by animateDpAsState(if(index==4) 16.dp else 31.dp,tween(duration),label="navigation upper edge")
    val bottomLeft by animateDpAsState(if(index==0) 16.dp else 31.dp,tween(duration),label="navigation lower edge")
    Surface(Modifier.padding(horizontal=12.dp,vertical=8.dp).fillMaxWidth().testTag("navigation-reflow"),shape=RoundedCornerShape(31.dp,topRight,31.dp,bottomLeft),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(7.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
            keys.forEachIndexed { i,key->
                val weight by animateFloatAsState(if(selected==key) 2.4f*fontScale.coerceIn(1f,1.5f) else 1f,tween(duration,easing=MotionTokens.settle),label="navigation space")
                val large by animateDpAsState(if(selected==key) 35.dp else 24.dp,tween(duration),label="active large corner")
                val small by animateDpAsState(if(selected==key) 12.dp else 24.dp,tween(duration),label="active small corner")
                Surface(onClick={onSelect(key)},modifier=Modifier.weight(weight).heightIn(min=48.dp).testTag("nav-$key"),shape=RoundedCornerShape(large,small,large,small),color=if(selected==key) MaterialTheme.colorScheme.primary else Color.Transparent,contentColor=if(selected==key) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Row(Modifier.padding(horizontal=4.dp,vertical=12.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        Icon(icons[i],labels[i],modifier=Modifier.size(22.dp))
                        AnimatedVisibility(selected==key,enter=fadeIn(tween(duration))+expandHorizontally(tween(duration)),exit=fadeOut(tween(duration/3))+shrinkHorizontally(tween(duration))) {
                            Text(labels[i],modifier=Modifier.padding(start=4.dp,end=4.dp),fontFamily=BodyFont,fontWeight=FontWeight.Bold,fontSize=11.sp,maxLines=1)
                        }
                    }
                }
            }
        }
    }
}
@Composable fun DownloadSurface(state: String,progress: Float,onClick: ()->Unit,modifier: Modifier=Modifier) {
    DownloadFill(state,progress,onClick,modifier.fillMaxWidth())
}

/** V11 / 06 Download Fill: real progress fills the complete button surface. */
@Composable fun DownloadFill(state: String,progress: Float,onClick: ()->Unit,modifier: Modifier=Modifier,compact: Boolean=false,idleLabel: String?=null,expansion: Float=if(compact) 0f else 1f,bytes: Long?=null,totalBytes: Long=0) {
    val duration=if(LocalMotion.current=="off") 0 else 280
    val fill by animateFloatAsState(if(state=="COMPLETE") 1f else progress.takeIf {it.isFinite()}?.coerceIn(0f,1f)?:0f,tween(duration,easing=LinearEasing),label="download fill")
    val e=expansion.coerceIn(0f,1f)
    val complete by animateFloatAsState(if(state=="COMPLETE") 1f else 0f,tween(duration),label="download ready shape")
    val counterVisible=bytes!=null && state!="IDLE" && state!="COMPLETE"
    val counter by animateFloatAsState(if(counterVisible) 1f else 0f,tween(duration),label="download counter space")
    val counterAlpha=((e-.65f)/.35f).coerceIn(0f,1f)*counter
    val large=18.dp+12.dp*e+10.dp*complete
    val small=18.dp-8.dp*e+30.dp*complete
    val icon=when(state) {"COMPLETE"->Icons.Default.Check;"RUNNING","QUEUED"->Icons.Default.Pause;"PAUSED"->Icons.Default.PlayArrow;"FAILED","RESTART_REQUIRED"->Icons.Default.Refresh;"VERIFYING"->Icons.Default.HourglassTop;else->Icons.Default.Download}
    val label=when(state) {
        "COMPLETE"->tr("Ready","Готово");"RUNNING","QUEUED"->if(e<.5f) tr("Pause","Пауза") else tr("Downloading","Загрузка");"PAUSED"->tr("Resume","Продолжить")
        "FAILED","RESTART_REQUIRED"->tr("Retry","Повторить");"VERIFYING"->tr("Checking","Проверка");else->idleLabel?:tr("Download","Скачать")
    }
    val colors=MaterialTheme.colorScheme
    val background by animateColorAsState(if(state=="IDLE") colors.primaryContainer else colors.surfaceContainerHighest,tween(duration),label="download background")
    val ink by animateColorAsState(if(fill>.52f) colors.onPrimary else if(state=="IDLE") colors.onPrimaryContainer else colors.onSurface,tween(duration),label="download ink")
    Surface(onClick=onClick,enabled=state!="VERIFYING",modifier=modifier.height(44.dp+(24.dp+16.dp*counter)*e).testTag("download-fill"),shape=RoundedCornerShape(large,small,large,small),color=background,contentColor=ink) {
        Box(contentAlignment=Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {if(fill>0f) drawRect(colors.primary,size=Size(size.width*fill,size.height))}
            Row(Modifier.offset(y=-8.dp*counterAlpha).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp+5.dp*e)) {
                AnimatedContent(icon,transitionSpec={ (slideInVertically(tween(duration)) {-it/2}+fadeIn(tween(duration))) togetherWith (slideOutVertically(tween(duration)) {it/2}+fadeOut(tween(duration))) },label="download icon") {glyph->Icon(glyph,null,Modifier.size(17.dp+7.dp*e))}
                AnimatedContent(label,transitionSpec={ (slideInVertically(tween(duration,easing=MotionTokens.settle)) {-it}+fadeIn(tween(duration))) togetherWith (slideOutVertically(tween(duration)) {it}+fadeOut(tween(duration))) },label="download label") {text->
                    Text(text,fontWeight=FontWeight.Bold,fontSize=(11+5*e).sp,lineHeight=(16+8*e).sp,maxLines=1,softWrap=false)
                }
                if(e>.8f && state in setOf("RUNNING","PAUSED")) AnimatedContent((progress.coerceIn(0f,1f)*100).toInt(),transitionSpec={fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))},label="download percent") {value->Text("$value%",fontFamily=BodyFont,fontWeight=FontWeight.Bold,fontSize=14.sp,maxLines=1)}
            }
            if(counterVisible) Box(Modifier.align(Alignment.BottomStart).padding(start=20.dp,bottom=9.dp).graphicsLayer {alpha=counterAlpha}) {DownloadCounter(bytes?:0,totalBytes,duration)}
        }
    }
}
@Composable private fun DownloadCounter(bytes: Long,total: Long,duration: Int) {
    val text="${bytes/1_000_000} MB / "+if(total>=1_000_000_000) String.format(java.util.Locale.US,"%.1f GB",total/1_000_000_000.0) else if(total>0) "${total/1_000_000} MB" else "— MB"
    val entrance=remember {Animatable(if(duration==0) 1f else 0f)}
    LaunchedEffect(Unit) {entrance.animateTo(1f,tween(if(duration==0) 0 else 700,easing=LinearEasing))}
    Row(Modifier.testTag("download-byte-counter")) {
        text.forEachIndexed {index,char->
            val t=if(duration==0) 1f else ((entrance.value*700-index*18)/340).coerceIn(0f,1f)
            val eased=MotionTokens.settle.transform(t)
            AnimatedContent(char,modifier=Modifier.graphicsLayer {alpha=.6f*eased;translationY=(1-eased)*18.dp.toPx()},transitionSpec={ (slideInVertically(tween(duration,easing=MotionTokens.settle)) {it}+fadeIn(tween(duration))) togetherWith (slideOutVertically(tween(duration)) {-it}+fadeOut(tween(duration))) },label="download digit $index") {c->Text(c.toString(),fontFamily=NumberFont,fontWeight=FontWeight.Bold,fontSize=13.sp,lineHeight=16.sp)}
        }
    }
}
@Composable fun DesktopIllustration(image: CatalogImage) {
    val windows=image.flavor.startsWith("windows")
    val bg=if(windows) Color(0xFF153F63) else Color(0xFF214A5B)
    Surface(shape=RoundedCornerShape(20.dp),color=bg,modifier=Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
        Box(Modifier.padding(12.dp)) {
            Canvas(Modifier.matchParentSize()) {
                if(windows) {
                    val s=size.height*.38f;val x=size.width*.65f;val y=size.height*.18f
                    repeat(4) {i->drawRect(BrandBlue.copy(alpha=.6f),Offset(x+(i%2)*(s/2+3),y+(i/2)*(s/2+3)),Size(s/2,s/2))}
                } else {
                    val p=Path().apply {moveTo(size.width*.5f,size.height*.08f);lineTo(size.width*.88f,size.height*.8f);lineTo(size.width*.2f,size.height*.8f);close()};drawPath(p,Color(0xFF8EB9CA))
                }
                drawRoundRect(Color(0xFF111C27),Offset(size.width*.04f,size.height*.18f),Size(size.width*.52f,size.height*.6f),CornerRadius(8.dp.toPx()))
                drawRoundRect(Color(0xFF314459),Offset(size.width*.04f,size.height*.18f),Size(size.width*.52f,size.height*.09f),CornerRadius(8.dp.toPx()))
                repeat(4) {drawLine(Color(0xFF8EB5C9),Offset(size.width*.08f,size.height*(.36f+it*.08f)),Offset(size.width*(.24f+it%2*.15f),size.height*(.36f+it*.08f)),2.dp.toPx())}
                drawRoundRect(Color(0xCC142333),Offset(0f,size.height*.9f),Size(size.width,size.height*.1f),CornerRadius(4.dp.toPx()))
            }
            if(windows) Text("Windows",fontFamily=NumberFont,color=Color.White,style=MaterialTheme.typography.labelSmall) else Icon(androidx.compose.ui.res.painterResource(R.drawable.alpine_logo),"Alpine Linux",tint=Color.White,modifier=Modifier.width(94.dp).height(23.dp).align(Alignment.TopStart))
            Text(tr("Interface illustration","Иллюстрация интерфейса"),color=Color.White.copy(alpha=.8f),fontSize=9.sp,modifier=Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable fun MorphLaunchAction(pending: Boolean,onClick: ()->Unit) {
    val t by animateFloatAsState(if(pending) 1f else 0f,tween(if(LocalMotion.current=="off") 0 else 650,easing=MotionTokens.settle),label="launch icon contour")
    val corner by animateDpAsState(if(pending) 20.dp else 30.dp,label="launch shape")
    Button(onClick=onClick,shape=RoundedCornerShape(corner),modifier=Modifier.heightIn(min=56.dp)) {
        val iconColor=LocalContentColor.current
        Canvas(Modifier.size(22.dp)) {
            val a=listOf(Offset(.25f,.1f),Offset(.75f,.5f),Offset(.25f,.9f),Offset(.25f,.7f),Offset(.25f,.5f),Offset(.25f,.3f))
            val b=listOf(Offset(.2f,.15f),Offset(.8f,.15f),Offset(.8f,.85f),Offset(.6f,.85f),Offset(.2f,.85f),Offset(.2f,.5f))
            val p=Path();a.zip(b).forEachIndexed {i,(x,y)->val q=Offset((x.x+(y.x-x.x)*t)*size.width,(x.y+(y.y-x.y)*t)*size.height);if(i==0)p.moveTo(q.x,q.y)else p.lineTo(q.x,q.y)};p.close();drawPath(p,iconColor)
        }
        Spacer(Modifier.width(10.dp));Text(if(pending) tr("View status","Открыть статус") else tr("Set up Linux","Подготовить Linux"))
    }
}

@Composable internal fun LaunchToggle(running: Boolean,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true) {
    val duration=if(LocalMotion.current=="off") 0 else if(LocalMotion.current=="reduced") 100 else 500
    val exponentialOut=Easing {if(it==1f) 1f else 1f-2f.pow(-10f*it)}
    val t by animateFloatAsState(if(running) 1f else 0f,tween(duration,easing=if(running) exponentialOut else FastOutSlowInEasing),label="launch to stop")
    val colors=MaterialTheme.colorScheme
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=46.dp).testTag("launch-toggle"),shape=RoundedCornerShape(24.dp,12.dp,24.dp,12.dp),color=Color.Transparent,contentColor=colors.onPrimary) {
        // Composite both fills first, then let Surface clip once. No bright underlay at the edge.
        Box(Modifier.graphicsLayer {compositingStrategy=CompositingStrategy.Offscreen},contentAlignment=Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {drawRect(colors.primary);drawRect(Color(0xFF172B40),size=Size(size.width*t,size.height))}
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Canvas(Modifier.size(18.dp)) {
                    val a=listOf(Offset(.2f,.1f),Offset(.85f,.5f),Offset(.2f,.9f),Offset(.2f,.5f))
                    val b=listOf(Offset(.2f,.2f),Offset(.8f,.2f),Offset(.8f,.8f),Offset(.2f,.8f))
                    val path=Path()
                    a.zip(b).forEachIndexed {i,(x,y)->val q=Offset((x.x+(y.x-x.x)*t)*size.width,(x.y+(y.y-x.y)*t)*size.height);if(i==0) path.moveTo(q.x,q.y) else path.lineTo(q.x,q.y)}
                    path.close();drawPath(path,lerp(colors.onPrimary,Color(0xFFE0EEFF),t))
                }
                AnimatedContent(running,modifier=Modifier.weight(1f),contentAlignment=Alignment.Center,transitionSpec={(slideInVertically(tween(duration,easing=FastOutSlowInEasing)) {-it}+fadeIn(tween(duration))) togetherWith (slideOutVertically(tween(duration,easing=FastOutSlowInEasing)) {it}+fadeOut(tween(duration)))},label="launch stop label") {active->SingleLineLabel(if(active) tr("Stop","Остановить") else tr("Start","Запустить"),Modifier.fillMaxWidth().testTag("launch-toggle-label"),style=MaterialTheme.typography.labelLarge.copy(fontFamily=BodyFont,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=if(active) Color(0xFFE0EEFF) else colors.onPrimary))}
            }
        }
    }
}
