package dev.osify.app.engine

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.osify.app.ui.BodyFont
import dev.osify.app.ui.LocalMotionMillis
import dev.osify.app.ui.SingleLineLabel
import dev.osify.app.ui.tr

/** A keyboard-sized dock, not a modal: the guest stays visible and accepts touch input. */
@Composable internal fun CompactGuestKeysPanel(
    landscape: Boolean,held: Set<Int>,onModifier: (Int)->Unit,onKey: (Int)->Unit,
    onCtrlAltDel: ()->Unit,onClose: ()->Unit,modifier: Modifier=Modifier
) {
    var group by rememberSaveable {mutableIntStateOf(0)}
    val colors=MaterialTheme.colorScheme
    val motion=LocalMotionMillis.current
    val heightLimit=(LocalConfiguration.current.screenHeightDp.dp*.45f).coerceAtMost(260.dp)
    val groups=listOf(tr("Input","Ввод"),tr("Navigation","Навигация"),"F1–F12")
    val deleteDescription=tr("Send Ctrl + Alt + Delete","Отправить Ctrl + Alt + Delete")
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        Surface(modifier=modifier.fillMaxWidth().heightIn(max=heightLimit).testTag("console-keys-panel"),shape=RoundedCornerShape(topStart=18.dp,topEnd=18.dp),color=colors.surfaceContainer,tonalElevation=2.dp) {
            BoxWithConstraints(Modifier.fillMaxWidth().then(if(landscape) Modifier.navigationBarsPadding() else Modifier)) {
                val wide=landscape && maxWidth>=600.dp
                @Composable fun Groups(modifier: Modifier) {
                    Row(modifier,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        groups.forEachIndexed {index,label->
                            Surface(onClick={group=index},color=if(group==index) colors.primaryContainer else colors.surfaceContainerHigh,contentColor=if(group==index) colors.onPrimaryContainer else colors.onSurfaceVariant,shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f).height(40.dp).testTag("key-group-$index").semantics {selected=group==index}) {
                                Box(Modifier.padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
                                    SingleLineLabel(label,style=MaterialTheme.typography.labelLarge.copy(fontFamily=BodyFont,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center))
                                }
                            }
                        }
                    }
                }
                @Composable fun Modifiers(modifier: Modifier) {
                    Row(modifier,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf("Ctrl" to 0xffe3,"Alt" to 0xffe9,"Shift" to 0xffe1,"Win" to 0xffeb).forEach {(label,key)->
                            CompactKey(label,{onModifier(key)},Modifier.weight(1f).testTag("key-$label"),selected=key in held)
                        }
                        CompactKey("Ctrl+Alt+Del",onCtrlAltDel,Modifier.weight(1.8f).testTag("key-ctrl-alt-del").semantics {contentDescription=deleteDescription})
                    }
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Groups(Modifier.weight(1f))
                        if(wide) Modifiers(Modifier.weight(1.35f))
                        IconButton(onClick=onClose,modifier=Modifier.size(40.dp).testTag("console-sheet-close")) {Icon(Icons.Default.Close,tr("Close keys","Закрыть клавиши"),Modifier.size(20.dp))}
                    }
                    if(!wide) Modifiers(Modifier.fillMaxWidth())
                    AnimatedContent(group,transitionSpec={fadeIn(tween(if(motion==0) 0 else 140)) togetherWith fadeOut(tween(if(motion==0) 0 else 90))},label="guest key bank") {bank->
                        val keys=when(bank) {
                            1->listOf("Home" to 0xff50,"End" to 0xff57,"PgUp" to 0xff55,"PgDn" to 0xff56,"←" to 0xff51,"↑" to 0xff52,"↓" to 0xff54,"→" to 0xff53)
                            2->(1..12).map {"F$it" to 0xffbd+it}
                            else->listOf("Esc" to 0xff1b,"Tab" to 0xff09,"Enter" to 0xff0d,"⌫" to 0xff08,"Ins" to 0xff63,"Del" to 0xffff,"C" to 99,"V" to 118)
                        }
                        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            keys.chunked(if(bank==2) 6 else 4).forEach {row->
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                    row.forEach {(label,key)->CompactKey(label,{onKey(key)},Modifier.weight(1f).testTag("key-$label"))}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun CompactKey(label: String,onClick: ()->Unit,modifier: Modifier=Modifier,selected: Boolean=false) {
    val colors=MaterialTheme.colorScheme
    Surface(onClick=onClick,modifier=modifier.height(40.dp).semantics {this.selected=selected},shape=RoundedCornerShape(10.dp),color=if(selected) colors.primary else colors.surfaceContainerHigh,contentColor=if(selected) colors.onPrimary else colors.onSurface) {
        Box(Modifier.padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
            SingleLineLabel(label,style=MaterialTheme.typography.labelLarge.copy(fontFamily=BodyFont,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center))
        }
    }
}
