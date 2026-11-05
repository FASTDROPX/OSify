@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.osify.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.osify.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.res.Resources
import java.util.WeakHashMap

internal enum class OsIdentity { WINDOWS, WINDOWS10, UBUNTU, ALPINE, LINUX, ALMA, ANTIX, ARCHCRAFT, ARCHLINUX, ARTIXLINUX, ATHENAOS, BODHI, CACHYOS, CHIMERALINUX, DEBIAN, DEVUAN, EDUBUNTU, FEDORA, FREEDOS, GARUDA, GENTOO, GHOSTBSD, KUBUNTU, LUBUNTU, NIXOS, UBUNTU_BUDGIE, UBUNTU_CINNAMON, UBUNTU_KYLIN, UBUNTU_MATE, UBUNTU_SERVER, UBUNTU_STUDIO, UBUNTU_UNITY, WINDOWS7, WINDOWSXP, XUBUNTU }
private val InternetIdentities=mapOf(
    "almalinux" to OsIdentity.ALMA,
    "antix" to OsIdentity.ANTIX,
    "archcraft" to OsIdentity.ARCHCRAFT,
    "arch linux" to OsIdentity.ARCHLINUX,
    "artix linux" to OsIdentity.ARTIXLINUX,
    "athena os" to OsIdentity.ATHENAOS,
    "bodhi" to OsIdentity.BODHI,
    "cachyos" to OsIdentity.CACHYOS,
    "chimera linux" to OsIdentity.CHIMERALINUX,
    "debian" to OsIdentity.DEBIAN,
    "devuan" to OsIdentity.DEVUAN,
    "edubuntu" to OsIdentity.EDUBUNTU,
    "fedora" to OsIdentity.FEDORA,
    "freedos" to OsIdentity.FREEDOS,
    "garuda linux" to OsIdentity.GARUDA,
    "gentoo" to OsIdentity.GENTOO,
    "ghostbsd" to OsIdentity.GHOSTBSD,
    "kubuntu" to OsIdentity.KUBUNTU,
    "lubuntu" to OsIdentity.LUBUNTU,
    "nixos" to OsIdentity.NIXOS,
    "ubuntu budgie" to OsIdentity.UBUNTU_BUDGIE,
    "ubuntu cinnamon" to OsIdentity.UBUNTU_CINNAMON,
    "ubuntu kylin" to OsIdentity.UBUNTU_KYLIN,
    "ubuntu mate" to OsIdentity.UBUNTU_MATE,
    "ubuntu server" to OsIdentity.UBUNTU_SERVER,
    "ubuntu studio" to OsIdentity.UBUNTU_STUDIO,
    "ubuntu unity" to OsIdentity.UBUNTU_UNITY,
    "windows 7" to OsIdentity.WINDOWS7,
    "windows xp" to OsIdentity.WINDOWSXP,
    "xubuntu" to OsIdentity.XUBUNTU,
)
private val OsIdentity.internetKey: String? get()=when(this) {
    OsIdentity.ALMA->"alma"
    OsIdentity.ANTIX->"antix"
    OsIdentity.ARCHCRAFT->"archcraft"
    OsIdentity.ARCHLINUX->"archlinux"
    OsIdentity.ARTIXLINUX->"artixlinux"
    OsIdentity.ATHENAOS->"athenaos"
    OsIdentity.BODHI->"bodhi"
    OsIdentity.CACHYOS->"cachyos"
    OsIdentity.CHIMERALINUX->"chimeralinux"
    OsIdentity.DEBIAN->"debian"
    OsIdentity.DEVUAN->"devuan"
    OsIdentity.EDUBUNTU->"edubuntu"
    OsIdentity.FEDORA->"fedora"
    OsIdentity.FREEDOS->"freedos"
    OsIdentity.GARUDA->"garuda"
    OsIdentity.GENTOO->"gentoo"
    OsIdentity.GHOSTBSD->"ghostbsd"
    OsIdentity.KUBUNTU->"kubuntu"
    OsIdentity.LUBUNTU->"lubuntu"
    OsIdentity.NIXOS->"nixos"
    OsIdentity.UBUNTU_BUDGIE->"ubuntu_budgie"
    OsIdentity.UBUNTU_CINNAMON->"ubuntu_cinnamon"
    OsIdentity.UBUNTU_KYLIN->"ubuntu_kylin"
    OsIdentity.UBUNTU_MATE->"ubuntu_mate"
    OsIdentity.UBUNTU_SERVER->"ubuntu_server"
    OsIdentity.UBUNTU_STUDIO->"ubuntu_studio"
    OsIdentity.UBUNTU_UNITY->"ubuntu_unity"
    OsIdentity.WINDOWS7->"windows7"
    OsIdentity.WINDOWSXP->"windowsxp"
    OsIdentity.XUBUNTU->"xubuntu"
    else->null
}
internal fun osIdentity(name: String,guest: String=""): OsIdentity {
    InternetIdentities.entries.sortedByDescending {it.key.length}.firstOrNull {name.filter {it.isLetterOrDigit()}.contains(it.key.filter {it.isLetterOrDigit()},true)}?.let {return it.value}
    return when {
        name.contains("windows 10",true) || name.contains("tiny10",true) || name.contains("tiny 10",true)->OsIdentity.WINDOWS10
        name.contains("ubuntu",true)->OsIdentity.UBUNTU
        name.contains("alpine",true)->OsIdentity.ALPINE
        guest=="WINDOWS" || name.contains("windows",true) || name.contains("tiny",true)->OsIdentity.WINDOWS
        else->OsIdentity.LINUX
    }
}
internal val TileInk=Color(0xFFF2F5FC)
internal val TileMuted=Color(0xFFC6D5E6)
private val JpegCovers=setOf("antix","archlinux","archcraft","edubuntu","fedora","gentoo","kubuntu","ubuntu_budgie","ubuntu_cinnamon","ubuntu_mate","xubuntu","ghostbsd","debian","ubuntu_kylin","windowsxp","nixos")
private object OsArtCache {
    // Do not carry native bitmap handles between different Resources lifetimes.
    private val images=WeakHashMap<Resources,MutableMap<Int,ImageBitmap>>()
    @Synchronized fun get(resources: Resources,id: Int)=images[resources]?.get(id)
    @Synchronized fun put(resources: Resources,id: Int,bitmap: ImageBitmap)=images.getOrPut(resources) {mutableMapOf()}.getOrPut(id) {bitmap}
}

/** Small bundled images decode off the UI thread; subsequent visits reuse the bitmap. */
@Composable internal fun OsArtwork(identity: OsIdentity,modifier: Modifier=Modifier) {
    val resources=LocalContext.current.resources
    val resource=when(identity) {OsIdentity.WINDOWS,OsIdentity.WINDOWS10->R.drawable.os_windows;OsIdentity.UBUNTU->R.drawable.os_ubuntu;else->R.drawable.os_alpine}
    val asset=if(identity==OsIdentity.ALPINE) "branding/alpine-cover.webp" else if(identity==OsIdentity.WINDOWS10) "branding/windows10-cover.webp" else identity.internetKey?.let {"branding/$it-cover.${if(it in JpegCovers) "jpg" else "webp"}"}
    val cacheKey=asset?.hashCode()?:resource
    val bitmap by produceState(OsArtCache.get(resources,cacheKey),resources,cacheKey) {
        value=withContext(Dispatchers.IO) {OsArtCache.get(resources,cacheKey)?:OsArtCache.put(resources,cacheKey,(if(asset!=null) resources.assets.open(asset).use {BitmapFactory.decodeStream(it)} else BitmapFactory.decodeResource(resources,resource)).asImageBitmap())}
    }
    val alpha by animateFloatAsState(if(bitmap!=null) 1f else 0f,tween(if(LocalMotion.current=="off") 0 else 160),label="OS artwork")
    bitmap?.let {Image(it,null,modifier.testTag("os-art-${identity.name}"),alpha=alpha,contentScale=ContentScale.Crop)}
}

/** Render the supplied originals through a clean, consistently sized optical viewport.
 * Cropping/clip paths remove source padding and printed checkerboards without redrawing the marks. */
@Composable internal fun OsLogo(identity: OsIdentity,modifier: Modifier=Modifier) {
    identity.internetKey?.let {key->
        val resources=LocalContext.current.resources
        val bitmap=remember(resources,key) {resources.assets.open("branding/$key-logo.png").use {BitmapFactory.decodeStream(it).asImageBitmap()}}
        val crop=remember(bitmap) {
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.asAndroidBitmap().getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            var left=bitmap.width;var top=bitmap.height;var right=0;var bottom=0
            pixels.forEachIndexed {index,pixel->if((pixel ushr 24)>30) {val x=index%bitmap.width;val y=index/bitmap.width;left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y)}}
            intArrayOf(left,top,right-left+1,bottom-top+1)
        }
        androidx.compose.foundation.Canvas(modifier.testTag("os-logo-${identity.name}")) {
            val h=size.height*40f/48f;val w=h*crop[2]/crop[3]
            drawImage(bitmap,srcOffset=IntOffset(crop[0],crop[1]),srcSize=IntSize(crop[2],crop[3]),dstOffset=IntOffset(((size.width-w)/2).toInt(),((size.height-h)/2).toInt()),dstSize=IntSize(w.toInt(),h.toInt()))
        }
        return
    }
    if(identity==OsIdentity.LINUX) {
        Surface(modifier,color=Color.Transparent) {
            Icon(Icons.Default.Terminal,null,Modifier.padding(4.dp).fillMaxSize(),tint=MaterialTheme.colorScheme.primary)
        };return
    }
    val resource=when(identity) {
        OsIdentity.WINDOWS->R.drawable.brand_windows11
        OsIdentity.WINDOWS10->R.drawable.brand_windows10
        OsIdentity.UBUNTU->R.drawable.brand_ubuntu
        else->R.drawable.brand_alpine
    }
    val resources=LocalContext.current.resources
    val bitmap=remember(resources,resource) {BitmapFactory.decodeResource(resources,resource).asImageBitmap()}
    Surface(modifier,color=Color.Transparent) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val bounds=when(identity) {
                OsIdentity.WINDOWS->intArrayOf(137,136,227,227)
                OsIdentity.WINDOWS10->intArrayOf(52,44,352,351)
                OsIdentity.UBUNTU->intArrayOf(33,33,334,334)
                else->intArrayOf(0,0,bitmap.width,bitmap.height)
            }
            val ratio=bounds[2].toFloat()/bounds[3]
            val h=size.height*(40f/48f)
            val w=h*ratio
            val left=(size.width-w)/2;val top=(size.height-h)/2
            val mask=Path().apply {
                when(identity) {
                    OsIdentity.WINDOWS->{
                        val gap=w*.047f;val half=(w-gap)/2
                        addRect(androidx.compose.ui.geometry.Rect(left,top,left+half,top+half))
                        addRect(androidx.compose.ui.geometry.Rect(left+half+gap,top,left+w,top+half))
                        addRect(androidx.compose.ui.geometry.Rect(left,top+half+gap,left+half,top+h))
                        addRect(androidx.compose.ui.geometry.Rect(left+half+gap,top+half+gap,left+w,top+h))
                    }
                    OsIdentity.WINDOWS10->{
                        fun panel(x1: Float,y1: Float,x2: Float,y2: Float,x3: Float,y3: Float,x4: Float,y4: Float) {
                            moveTo(left+x1*w,top+y1*h);lineTo(left+x2*w,top+y2*h);lineTo(left+x3*w,top+y3*h);lineTo(left+x4*w,top+y4*h);close()
                        }
                        panel(.003f,.145f,.423f,.084f,.423f,.486f,.003f,.486f)
                        panel(.444f,.081f,.997f,.003f,.997f,.486f,.444f,.486f)
                        panel(.003f,.510f,.423f,.510f,.423f,.920f,.003f,.855f)
                        panel(.444f,.510f,.997f,.510f,.997f,.997f,.444f,.923f)
                    }
                    OsIdentity.UBUNTU->addOval(androidx.compose.ui.geometry.Rect(left,top,left+w,top+h))
                    else->addRect(androidx.compose.ui.geometry.Rect(left,top,left+w,top+h))
                }
            }
            val outer=Path().apply {
                if(identity==OsIdentity.WINDOWS) addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(left,top,left+w,top+h),androidx.compose.ui.geometry.CornerRadius(w*.064f)))
                else addRect(androidx.compose.ui.geometry.Rect(left,top,left+w,top+h))
            }
            clipPath(outer) {clipPath(mask) {
                drawImage(bitmap,srcOffset=IntOffset(bounds[0],bounds[1]),srcSize=IntSize(bounds[2],bounds[3]),dstOffset=IntOffset(left.toInt(),top.toInt()),dstSize=IntSize(w.toInt(),h.toInt()))
            }}
        }
    }
}

@Composable internal fun TileBadge(text: String,icon: ImageVector?=null,color: Color=TileMuted) {
    Surface(shape=RoundedCornerShape(8.dp),color=color.copy(alpha=.09f),contentColor=color) {
        Row(Modifier.padding(horizontal=7.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            icon?.let {Icon(it,null,Modifier.size(12.dp))}
            AnimatedContent(text,transitionSpec={fadeIn(tween(220)) togetherWith fadeOut(tween(160))},label="badge content") {value->Text(value,fontFamily=BodyFont,fontWeight=FontWeight.SemiBold,fontSize=11.sp,lineHeight=14.sp)}
        }
    }
}

@Composable internal fun OsCatalogTile(identity: OsIdentity,title: String,description: String,badges: List<String>,action: String,tag: String,onClick: ()->Unit) {
    val base=when(identity) {OsIdentity.WINDOWS,OsIdentity.WINDOWS10->Color(0xFF102849);OsIdentity.UBUNTU->Color(0xFF3A152E);else->Color(0xFF122F43)}
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(22.dp),color=base,border=BorderStroke(1.dp,Color.White.copy(alpha=.12f)),contentColor=TileInk) {
        Box {
            OsArtwork(identity,Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(base,base.copy(alpha=.94f),base.copy(alpha=.16f)))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent,base.copy(alpha=.5f)))))
            Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                    OsLogo(identity,Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(end=35.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(title,fontWeight=FontWeight.Bold,fontSize=19.sp,lineHeight=23.sp)
                        Text(description,color=TileMuted,fontSize=12.sp,lineHeight=16.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {badges.forEachIndexed {i,text->TileBadge(text,if(i==0) Icons.Default.Memory else Icons.Default.Album)}}
                    Button(onClick=onClick,shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(horizontal=13.dp,vertical=8.dp),modifier=Modifier.heightIn(min=40.dp)) {
                        Icon(Icons.Default.Download,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text(action,fontWeight=FontWeight.Bold,fontSize=12.sp)
                    }
                }
            }
        }
    }
}

@Composable internal fun RuntimeSetupTile(pending: Boolean,failed: Boolean,onClick: ()->Unit) {
    Surface(modifier=Modifier.fillMaxWidth().testTag("runtime-setup-tile"),shape=RoundedCornerShape(24.dp),color=Color(0xFF173E67),border=BorderStroke(1.dp,Color(0xFF466586)),contentColor=TileInk) {
        Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF244B7C),Color(0xFF10294B),Color(0xFF106DA5))))) {
            Box(Modifier.align(Alignment.CenterEnd).offset(x=26.dp,y=18.dp).size(150.dp).graphicsLayer {rotationZ=14f}.background(Brush.linearGradient(listOf(Color(0xFF85CCFF),Color(0xFF1266B1))),RoundedCornerShape(45.dp)))
            Surface(Modifier.align(Alignment.CenterEnd).padding(end=12.dp).size(104.dp,87.dp).graphicsLayer {rotationZ=12f},shape=RoundedCornerShape(16.dp),color=Color(0xFF0A1C30),border=BorderStroke(5.dp,Color(0xFF759DBB)),shadowElevation=12.dp) {Box(contentAlignment=Alignment.Center) {Icon(Icons.Default.Terminal,null,Modifier.size(48.dp),tint=Color(0xFFCFEBFF))}}
            Column(Modifier.fillMaxWidth(.69f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {Icon(Icons.Default.Bolt,null,Modifier.size(16.dp));Text(tr("Quick start","Быстрый старт"),fontWeight=FontWeight.SemiBold,fontSize=11.sp)}
                Text(if(pending) tr("Preparing engines","Готовим движки") else if(failed) tr("Check Termux","Проверьте Termux") else tr("Prepare Termux","Подготовить Termux"),fontWeight=FontWeight.Bold,fontSize=18.sp,lineHeight=23.sp)
                Text(if(pending) tr("Termux is installing packages","Termux устанавливает пакеты") else tr("Set up Linux and QEMU once","Один раз настройте Linux и QEMU"),color=Color(0xFFCCDFF2),fontSize=12.sp,lineHeight=16.sp)
                Button(onClick=onClick,contentPadding=PaddingValues(horizontal=14.dp,vertical=8.dp),modifier=Modifier.testTag("launcher-setup")) {Icon(if(pending) Icons.Default.Info else Icons.Default.OpenInNew,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(tr("Setup","Подготовка"),fontWeight=FontWeight.Bold,fontSize=12.sp)}
            }
        }
    }
}

@Composable internal fun OsMachineTile(identity: OsIdentity,title: String,description: String,status: String,statusColor: Color,badges: List<String>,primary: String,primaryIcon: ImageVector=Icons.Default.PlayArrow,enabled: Boolean=true,onPrimary: ()->Unit,secondary: String,onSecondary: ()->Unit,tag: String,running: Boolean?=null,menu: @Composable ()->Unit) {
    Surface(Modifier.fillMaxWidth().animateContentSize(tween(if(LocalMotion.current=="off") 0 else 300,easing=FastOutSlowInEasing)).testTag(tag),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f))) {
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF18354C))) {
                    OsArtwork(identity,Modifier.matchParentSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x55122234),Color.Transparent,Color(0x66122234)))))
                    OsLogo(identity,Modifier.align(Alignment.TopCenter).padding(top=18.dp).size(48.dp))
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {Text(title,Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("$tag-title"),fontWeight=FontWeight.Bold,fontSize=18.sp,lineHeight=23.sp,maxLines=1,softWrap=false);menu()}
                    Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,lineHeight=16.sp)
                    AnimatedContent(status to statusColor,transitionSpec={fadeIn(tween(220)) togetherWith fadeOut(tween(180))},label="machine state") {(label,ink)->TileBadge(label,Icons.Default.Circle,ink)}
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {badges.forEach {TileBadge(it,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                }
            }
            val primaryButton: @Composable (Modifier)->Unit={modifier->
                if(running!=null) LaunchToggle(running,onPrimary,modifier,enabled) else Button(onClick=onPrimary,enabled=enabled,modifier=modifier.heightIn(min=43.dp),contentPadding=PaddingValues(horizontal=9.dp,vertical=9.dp)) {Icon(primaryIcon,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));SingleLineLabel(primary,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge.copy(fontWeight=FontWeight.Bold))}
            }
            val secondaryButton: @Composable (Modifier)->Unit={modifier->
                OutlinedButton(onClick=onSecondary,modifier=modifier.heightIn(min=43.dp),contentPadding=PaddingValues(horizontal=9.dp,vertical=9.dp)) {Icon(Icons.Default.Tune,null,Modifier.size(16.dp));Spacer(Modifier.width(5.dp));AnimatedContent(secondary,modifier=Modifier.weight(1f),transitionSpec={fadeIn(tween(220)) togetherWith fadeOut(tween(180))},label="machine secondary") {label->SingleLineLabel(label,Modifier.fillMaxWidth().testTag("$tag-secondary-label"),style=MaterialTheme.typography.labelLarge.copy(fontFamily=BodyFont,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center))}}
            }
            Row(Modifier.fillMaxWidth().testTag("$tag-actions"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {primaryButton(Modifier.weight(1f));secondaryButton(Modifier.weight(1f))}
        }
    }
}
