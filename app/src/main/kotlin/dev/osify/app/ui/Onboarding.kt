package dev.osify.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.osify.app.data.AppPreferences
import dev.osify.app.device.DeviceSnapshot
import kotlinx.coroutines.launch

private val WelcomeEase=CubicBezierEasing(.16f,1f,.3f,1f)
@Composable fun Onboarding(preferences: AppPreferences,device: DeviceSnapshot,onFinish: () -> Unit,onBatterySettings: () -> Unit={}) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    val view=LocalView.current
    val textRise=with(androidx.compose.ui.platform.LocalDensity.current) {24.dp.roundToPx()}
    val motion=LocalMotion.current
    val duration=LocalMotionMillis.current
    val scope=rememberCoroutineScope()
    val exit=remember { Animatable(0f) }
    val green=remember { Animatable(0f) }
    val confetti=remember { Animatable(1f) }
    val scroll=rememberScrollState()
    val arrival=remember { Animatable(if(motion=="off") 1f else 0f) }
    LaunchedEffect(motion) {arrival.snapTo(if(motion=="off") 1f else 0f);arrival.animateTo(1f,tween(if(motion=="full") 650 else if(motion=="off") 0 else 100,easing=MotionTokens.settle))}
    val progress by animateFloatAsState((step+1)/4f,tween(duration,easing=WelcomeEase),label="welcome progress")
    LaunchedEffect(step,motion) {
        scroll.scrollTo(0);green.snapTo(0f);confetti.snapTo(1f)
        if(step==3) {
            green.animateTo(1f,tween(if(motion=="full") 800 else duration,easing=WelcomeEase))
            if(preferences.haptics && !leaving) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if(motion=="full") {confetti.snapTo(0f);confetti.animateTo(1f,tween(2600,easing=LinearEasing))}
        }
    }
    fun leave() {
        if(leaving) return
        leaving=true
        if(preferences.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if(motion=="off") onFinish() else scope.launch {
            exit.animateTo(1f,tween(if(motion=="full") 540 else 100,easing=LinearEasing));onFinish()
        }
    }
    fun Modifier.depart(index: Int): Modifier {
        val t=((exit.value-index*.045f)/(1f-index*.045f)).coerceIn(0f,1f)
        return this.offset { androidx.compose.ui.unit.IntOffset(0,if(motion=="full") (-view.height.coerceAtLeast(1000)*t*t*1.15f).toInt() else 0) }.graphicsLayer {
            compositingStrategy=CompositingStrategy.ModulateAlpha
            clip=false;alpha=1f-t*t
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            // Blur the viewport once. Child layers remain unbounded while travelling across it.
            Box(Modifier.fillMaxSize().graphicsLayer {
                if(Build.VERSION.SDK_INT>=31 && motion=="full" && exit.value>0f && exit.value<1f) {
                    renderEffect=android.graphics.RenderEffect.createBlurEffect(.1f,1f+exit.value*9f,android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                }
            }) {
                Column(Modifier.safeDrawingPadding().fillMaxSize().padding(horizontal=24.dp,vertical=12.dp).offset { androidx.compose.ui.unit.IntOffset(0,if(motion=="full") ((1-arrival.value)*36.dp.toPx()).toInt() else 0) }.graphicsLayer {alpha=arrival.value;clip=false;compositingStrategy=CompositingStrategy.ModulateAlpha}) {
                    Row(Modifier.depart(0),verticalAlignment=Alignment.CenterVertically) {
                        OrbitMark(40.dp)
                        Text("OSify",fontFamily=WordmarkFont,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=10.dp).weight(1f))
                        TextButton(onClick={leave()},enabled=!leaving,modifier=Modifier.testTag("onboarding-skip")) {Text(tr("Skip","Пропустить"))}
                    }
                    Row(Modifier.fillMaxWidth().padding(top=22.dp).depart(1),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        repeat(4) {i->
                            val fill by animateFloatAsState(if(i<=step) 1f else 0f,tween(duration),label="welcome segment")
                            LinearProgressIndicator(progress={if(step==3 && i==3) green.value else fill},modifier=Modifier.weight(1f).height(5.dp),color=if(step==3) Color(0xFF83D6B4) else MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.outlineVariant,gapSize=0.dp,drawStopIndicator={})
                        }
                    }
                    // Freeze scroll position without its viewport clip during the exit flight.
                    val scrolling=if(!leaving) Modifier.verticalScroll(scroll) else Modifier.layout {measurable,c->
                        val p=measurable.measure(c.copy(maxHeight=Constraints.Infinity,minHeight=0))
                        layout(p.width,c.maxHeight) {p.place(0,-scroll.value)}
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().then(scrolling).depart(2)) {
                        PageMorphCarousel(step,onStep={if(!leaving) step=it},modifier=Modifier)
                        Spacer(Modifier.height(12.dp))
                        AnimatedContent(step,transitionSpec={(fadeIn(tween(duration,delayMillis=duration/3))+slideInVertically(tween(duration,easing=MotionTokens.settle)) {if(motion=="full") textRise else 0}) togetherWith (fadeOut(tween(duration/3))+slideOutVertically(tween(duration/3)) {if(motion=="full") -textRise/2 else 0}) using SizeTransform(clip=false)},label="welcome copy") {current->
                            Column(Modifier.fillMaxWidth().testTag("onboarding-step-$current"),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                                Text(when(current) {
                                    0->tr("Start with\nan image","Начнём\nс образа ОС")
                                    1->tr("Keep your\nOS collection","Образы\nпод рукой")
                                    2->tr("Your phone’s\nresources","Возможности\nтелефона")

                                    else->tr("You’re\nready","Можно\nначинать")
                                },fontFamily=HeadingFont,fontSize=27.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold,modifier=Modifier)
                                Text(when(current) {
                                    0->tr("Choose an image in the catalog. Download it here or add a file from your phone.","Выберите образ в каталоге. Скачайте его здесь или добавьте файл с телефона.")
                                    1->tr("Pause and resume downloads. Your files stay in the library. Start ISO-based machines from Launcher after setting up Termux.","Загрузку можно поставить на паузу и продолжить позже. Файлы останутся в коллекции. После подготовки Termux запускайте машины из ISO в Launcher.")
                                    2->tr("${device.profile.cores} CPU cores\n${formatBytes(device.profile.totalRam)} RAM\n${formatBytes(device.profile.freeDisk)} storage available", "Ядер процессора: ${device.profile.cores}\nПамять: ${formatBytes(device.profile.totalRam)}\nСвободное место: ${formatBytes(device.profile.freeDisk)}")

                                    else->tr("Open the catalog and choose your first image. You can revisit this introduction in Settings.","Откройте каталог и выберите первый образ. Это знакомство можно повторить в настройках.")
                                },style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                    Column(Modifier.fillMaxWidth().depart(4)) {
                        if(step==3) {
                            Button(onClick={leave()},enabled=!leaving,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("onboarding-next"),colors=ButtonDefaults.buttonColors(containerColor=BrandBlue,contentColor=Color(0xFF092D49),disabledContainerColor=BrandBlue,disabledContentColor=Color(0xFF092D49))) {Text(tr("Start","Начать"),style=MaterialTheme.typography.titleMedium)}
                            TextButton(onClick={step=(step-1).coerceAtLeast(0)},enabled=!leaving,modifier=Modifier.align(Alignment.CenterHorizontally).testTag("onboarding-back")) {Text(tr("Back","Назад"))}
                        } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            if(step>0) TextButton(onClick={step=(step-1).coerceAtLeast(0)},enabled=!leaving,modifier=Modifier.testTag("onboarding-back")) {Text(tr("Back","Назад"))}
                            Spacer(Modifier.weight(1f))
                            Button(onClick={step=(step+1).coerceAtMost(3)},enabled=!leaving,modifier=Modifier.heightIn(min=52.dp).testTag("onboarding-next")) {Text(tr("Continue","Продолжить"))}
                        }
                    }
                }
            }
            if(confetti.value<1f && motion=="full" && !leaving) ConfettiBurst(confetti.value)
        }
    }
}

fun formatBytes(bytes: Long): String {
    if(bytes<0) return "—"
    val gib=1024.0*1024*1024;val mib=1024.0*1024
    return when { bytes>=gib->String.format(java.util.Locale.getDefault(),"%.1f GiB",bytes/gib);bytes>=mib->String.format(java.util.Locale.getDefault(),"%.1f MiB",bytes/mib);else->"${bytes/1024} KiB" }
}

@Composable private fun PageMorphCarousel(step: Int,onStep: (Int)->Unit,modifier: Modifier=Modifier) {
    val duration=if(LocalMotion.current=="off") 0 else if(LocalMotion.current=="reduced") 100 else 720
    var drag by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxWidth().height(250.dp).testTag("page-morph-carousel").pointerInput(step) {
        detectHorizontalDragGestures(onDragStart={drag=0f},onDragEnd={if(kotlin.math.abs(drag)>45.dp.toPx()) onStep((step+if(drag<0) 1 else -1).coerceIn(0,3))}) {change,delta->change.consume();drag+=delta}
    }) {
        val cardWidth=maxWidth*.76f
        repeat(4) {i->
            val pos=i-step
            val offset by animateDpAsState(when(pos) {0->maxWidth*.06f;1->maxWidth*.86f;else->if(pos<0) -maxWidth else maxWidth*1.8f},tween(duration,easing=MotionTokens.settle),label="page position")
            val scale by animateFloatAsState(if(pos==0) 1f else .71f,tween(duration,easing=MotionTokens.settle),label="page size")
            val alpha by animateFloatAsState(when(pos) {0->1f;1->.38f;else->0f},tween(duration),label="page opacity")
            val large by animateDpAsState(if(pos==0) 44.dp else 30.dp,tween(duration,easing=MotionTokens.settle),label="page large radius")
            val small by animateDpAsState(if(pos==0) 16.dp else 30.dp,tween(duration,easing=MotionTokens.settle),label="page small radius")
            val color by animateColorAsState(if(pos==0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,tween(duration),label="page color")
            val ink by animateColorAsState(if(pos==0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,tween(duration),label="page ink")
            val textReveal by animateFloatAsState(if(pos==0) 1f else 0f,tween(duration,easing=MotionTokens.settle),label="page label arrival")
            Surface(modifier=Modifier.offset(x=offset,y=18.dp).width(cardWidth).height(218.dp).graphicsLayer {scaleX=scale;scaleY=scale;this.alpha=alpha;rotationZ=if(pos==1) 7f*(1-scale)/.29f else 0f},shape=RoundedCornerShape(large,small,large,small),color=color) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    Text("0${i+1}",fontFamily=NumberFont,fontWeight=FontWeight.Bold,fontSize=80.sp,color=ink.copy(alpha=.8f),modifier=Modifier.offset { androidx.compose.ui.unit.IntOffset(0,((1-textReveal)*24.dp.toPx()).toInt()) })
                    Text(when(i) {0->tr("Welcome","Приветствие");1->tr("Choose an OS","Выбор ОС");2->tr("Resources","Ресурсы");else->tr("Start","Запуск")},style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=ink,modifier=Modifier.offset { androidx.compose.ui.unit.IntOffset(0,((1-textReveal)*24.dp.toPx()).toInt()) }.graphicsLayer {this.alpha=.4f+.6f*textReveal})
                }
            }
        }
    }
}
