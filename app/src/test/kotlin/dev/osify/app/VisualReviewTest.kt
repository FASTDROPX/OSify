package dev.osify.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.osify.app.data.AppPreferences
import dev.osify.app.device.DeviceSnapshot
import dev.osify.app.ui.*
import dev.osify.core.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="en-rUS-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualReviewTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var view: View
    private val device=DeviceSnapshot(DeviceProfile(Architecture.ARM64,8,8L*1024*1024*1024,3L*1024*1024*1024,20L*1024*1024*1024,34),5L*1024*1024*1024,false,72,0,"OpenGL ES 3.2")
    private fun capture(name: String) {compose.runOnIdle {
        val root=view.rootView
        val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap));val file=File("build/screenshots/v3/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }}
    @Test fun onboardingAndExitFrames() {
        var finished=false
        compose.mainClock.autoAdvance=false
        compose.setContent {view=LocalView.current;OsifyTheme(AppPreferences(language="ru",motion="full",theme="dark",dynamicColor=false)) {Onboarding(AppPreferences(haptics=false),device,{finished=true})}}
        compose.mainClock.advanceTimeBy(700);capture("welcome-00")
        compose.onNodeWithTag("onboarding-next").performClick()
        for(i in 1..4) {compose.mainClock.advanceTimeBy(180);capture("page-morph-0$i")}
        compose.onNodeWithTag("onboarding-next").performClick();compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("onboarding-next").performClick();compose.mainClock.advanceTimeBy(850)
        for(i in 0..9) {compose.mainClock.advanceTimeBy(200);capture("confetti-${i.toString().padStart(2,'0')}")}
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("onboarding-next").performClick()
        for(i in 0..6) {compose.mainClock.advanceTimeBy(70);capture("exit-0$i")}
        compose.mainClock.advanceTimeBy(200);compose.runOnIdle {assertTrue(finished)}
    }
    @Test fun controlsLightAndDark() {
        var selected by mutableStateOf("home")
        var state by mutableStateOf("IDLE")
        var dark by mutableStateOf(true)
        compose.setContent {
            view=LocalView.current
            OsifyTheme(AppPreferences(language="ru",motion="off",theme=if(dark) "dark" else "light",dynamicColor=false)) {
                Surface(Modifier.fillMaxSize()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    MemoryCard(device)
                    DownloadSurface(state,.42f,{})
                    CompositionLocalProvider(LocalMotion provides "full") {TransferProgress(.58f,previewPhase=.4f)}
                    NavigationReflow(selected) {selected=it}
                }}
            }
        }
        capture("controls-idle")
        compose.runOnIdle {state="RUNNING";selected="catalog"};capture("controls-running")
        compose.runOnIdle {state="PAUSED";selected="library"};capture("controls-paused")
        compose.runOnIdle {state="FAILED"};capture("controls-error")
        compose.runOnIdle {state="COMPLETE"};capture("controls-ready")
        compose.runOnIdle {dark=false};capture("controls-light")
        compose.runOnIdle {selected="settings"};capture("controls-settings")
    }
    @Test fun largeRussianTextAndReflow() {
        compose.setContent {
            view=LocalView.current
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.35f)) {
                OsifyTheme(AppPreferences(language="ru",motion="off",theme="light",dynamicColor=false)) {
                    Surface(Modifier.fillMaxSize()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        MemoryCard(device)
                        NavigationReflow("settings") {}
                        DownloadSurface("COMPLETE",1f,{})
                    }}
                }
            }
        }
        capture("large-text-light")
        compose.onNodeWithText("Настройки").assertExists()
        compose.onNodeWithText("Готово").assertExists()
    }

    @Test fun ramWaveSmoothFillAndFrequency() {
        var fraction by mutableFloatStateOf(.2f)
        compose.mainClock.autoAdvance=false
        compose.setContent {
            view=LocalView.current
            OsifyTheme(AppPreferences(language="ru",motion="full",theme="dark",dynamicColor=false)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp)) {
                        val used=(device.profile.totalRam*fraction).toLong()
                        MemoryCard(device.copy(profile=device.profile.copy(availableRam=device.profile.totalRam-used),used=used))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300);capture("ram-wave-low")
        compose.runOnIdle {fraction=.7f}
        for(i in 0..11) {compose.mainClock.advanceTimeBy(100);capture("ram-wave-fill-${i.toString().padStart(2,'0')}")}
        compose.runOnIdle {fraction=.95f}
        compose.mainClock.advanceTimeBy(1300);capture("ram-wave-high")
        compose.onNodeWithTag("memory-wave").assertExists()
        compose.mainClock.autoAdvance=true
    }

    @Test fun illustratedTilesWithLargeRussianFontDark()=renderIllustratedTiles(true)
    @Test fun illustratedTilesWithLargeRussianFontLight()=renderIllustratedTiles(false)
    private fun renderIllustratedTiles(dark: Boolean) {
        compose.setContent {
            view=LocalView.current
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.35f)) {
                key(dark) { OsifyTheme(AppPreferences(language="ru",motion="off",theme=if(dark) "dark" else "light",dynamicColor=false)) {
                    Surface(Modifier.fillMaxSize()) {Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        OsCatalogTile(OsIdentity.WINDOWS,"Windows 11","Официальная ознакомительная версия",listOf("x64","ISO"),"Скачать","large-os",{})
                        OsMachineTile(OsIdentity.UBUNTU,"Ubuntu 24.04","Linux в виртуальной машине","Остановлена",MaterialTheme.colorScheme.onSurfaceVariant,listOf("x64","1024 MiB","BIOS"),"Запустить",onPrimary={},secondary="Параметры",onSecondary={},tag="large-vm",menu={})
                        NavigationReflow("launcher") {}
                    }}
                }}
            }
        }
        compose.waitUntil(5000) {compose.onAllNodesWithTag("os-art-WINDOWS",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        capture(if(dark) "041-tiles-large-dark" else "041-tiles-large-light")
        compose.onNodeWithText("Параметры").assertIsDisplayed()
    }

    @Test fun providedLogosHaveEqualVisibleHeightsAndTransparentCorners() {
        val marks=listOf(OsIdentity.WINDOWS,OsIdentity.WINDOWS10,OsIdentity.UBUNTU,OsIdentity.ALPINE)
        val background=Color(0xFF101418)
        compose.setContent {
            view=LocalView.current
            OsifyTheme(AppPreferences(language="ru",motion="off",theme="dark")) {
                Surface(Modifier.fillMaxSize(),color=background) {
                    Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        marks.forEach {mark->Box(Modifier.size(48.dp).then(Modifier.testTag("mark-${mark.name}"))) {OsLogo(mark,Modifier.fillMaxSize())}}
                    }
                }
            }
        }
        lateinit var rendered: Bitmap
        val origin=IntArray(2)
        compose.runOnIdle {
            val root=view.rootView
            rendered=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(Canvas(rendered))
            val rootOrigin=IntArray(2);root.getLocationInWindow(rootOrigin);view.getLocationInWindow(origin)
            origin[0]-=rootOrigin[0];origin[1]-=rootOrigin[1]
        }
        val heights=marks.map {mark->
            val bounds=compose.onNodeWithTag("mark-${mark.name}").fetchSemanticsNode().boundsInRoot
            val bitmap=Bitmap.createBitmap(rendered,bounds.left.toInt()+origin[0],bounds.top.toInt()+origin[1],bounds.width.toInt(),bounds.height.toInt())
            val bg=background.toArgb()
            kotlin.test.assertEquals(bg,bitmap.getPixel(0,0),"No background behind $mark")
            kotlin.test.assertEquals(bg,bitmap.getPixel(bitmap.width-1,bitmap.height-1),"Transparent corner for $mark")
            var first=bitmap.height;var last=-1
            for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) {
                val value=bitmap.getPixel(x,y)
                val difference=kotlin.math.abs(android.graphics.Color.red(value)-android.graphics.Color.red(bg))+kotlin.math.abs(android.graphics.Color.green(value)-android.graphics.Color.green(bg))+kotlin.math.abs(android.graphics.Color.blue(value)-android.graphics.Color.blue(bg))
                if(difference>60) {first=minOf(first,y);last=maxOf(last,y)}
            }
            kotlin.test.assertTrue(last>=first,"Visible pixels for $mark")
            last-first+1
        }
        kotlin.test.assertTrue(heights.max()-heights.min()<=2,"Visible logo heights: $heights")
        File("build/screenshots/042-logo-visible-heights.txt").apply {parentFile.mkdirs();writeText(marks.zip(heights).joinToString("\n") {"${it.first}: ${it.second} px"})}
        val first=compose.onNodeWithTag("mark-${marks.first().name}").fetchSemanticsNode().boundsInRoot
        val last=compose.onNodeWithTag("mark-${marks.last().name}").fetchSemanticsNode().boundsInRoot
        val gallery=Bitmap.createBitmap(rendered,first.left.toInt()+origin[0]-8,first.top.toInt()+origin[1]-8,(last.right-first.left).toInt()+16,first.height.toInt()+16)
        File("build/screenshots/v3/042-logo-size-comparison.png").apply {parentFile.mkdirs();outputStream().use {gallery.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }

}
