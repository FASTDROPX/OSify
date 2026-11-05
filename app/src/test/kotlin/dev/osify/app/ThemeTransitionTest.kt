package dev.osify.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.osify.app.data.AppPreferences
import dev.osify.app.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="en-rUS-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeTransitionTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var view: View
    private fun shot(name: String) {compose.runOnIdle {
        val root=view.rootView;val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap));val file=File("build/screenshots/033/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }}
    @Test fun animatedPaletteAndAmoledAlsoCoverDynamicColor() {
        var prefs by mutableStateOf(AppPreferences(language="ru",theme="light",motion="full",dynamicColor=false))
        var background=Color.Unspecified
        compose.mainClock.autoAdvance=false
        compose.setContent {
            view=LocalView.current
            OsifyTheme(prefs) {
                val palette=MaterialTheme.colorScheme
                SideEffect {background=palette.background}
                Surface(Modifier.fillMaxSize()) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("Оформление",style=MaterialTheme.typography.headlineLarge)
                    Card {Column(Modifier.padding(20.dp)) {Text("AMOLED");Text("Настоящий чёрный фон")}}
                    Button(onClick={}) {Text("Продолжить")}
                    NavigationReflow("settings") {}
                }}
            }
        }
        compose.mainClock.advanceTimeBy(100);shot("theme-light")
        val initial=background
        compose.runOnIdle {prefs=prefs.copy(theme="dark")}
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        compose.mainClock.advanceTimeBy(160);shot("theme-middle")
        val middle=background
        assertNotEquals(initial,middle)
        compose.mainClock.advanceTimeBy(800);shot("theme-dark")
        assertNotEquals(middle,background)
        compose.runOnIdle {prefs=prefs.copy(amoled=true)}
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800);shot("theme-amoled")
        assertEquals(Color.Black,background)
        compose.runOnIdle {prefs=prefs.copy(dynamicColor=true)}
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800);shot("theme-dynamic-amoled")
        assertEquals(Color.Black,background)
        compose.runOnIdle {prefs=prefs.copy(theme="light",motion="off")}
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        assertNotEquals(Color.Black,background)
        compose.mainClock.autoAdvance=true
    }
}
