package dev.osify.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="en-rUS-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingUiTest {
    @get:Rule val compose=createComposeRule()
    private val device=DeviceSnapshot(DeviceProfile(Architecture.ARM64,8,8L*1024*1024*1024,4L*1024*1024*1024,20L*1024*1024*1024,35),4L*1024*1024*1024,false,80,0,"Test graphics")
    @Test fun allFivePagesWorkWithMotionOff() {
        var finished=false
        compose.setContent { OsifyTheme(AppPreferences(language="en",motion="off",dynamicColor=false)) { Onboarding(AppPreferences(haptics=false),device,{finished=true}) } }
        repeat(4) { page ->
            compose.onNodeWithTag("onboarding-step-$page").assertExists()
            compose.onNodeWithTag("onboarding-next").performClick()
        }
        compose.runOnIdle {assertTrue(finished)}
    }
    @Test fun skippingIsImmediateAndRussianIsLocalized() {
        var finished=false
        compose.setContent { OsifyTheme(AppPreferences(language="ru",motion="off",dynamicColor=false)) { Onboarding(AppPreferences(),device,{finished=true}) } }
        compose.onNodeWithText("Пропустить").assertExists().performClick()
        compose.runOnIdle {assertTrue(finished)}
    }
    @Test fun animatedFinishAndBackRemainUsable() {
        var finished=false
        lateinit var view: android.view.View
        compose.setContent {
            view=androidx.compose.ui.platform.LocalView.current
            OsifyTheme(AppPreferences(language="ru",motion="full",dynamicColor=false,theme="dark")) {
                Onboarding(AppPreferences(haptics=false),device,{finished=true})
            }
        }
        fun capture(name: String) {
            compose.runOnIdle {
                val root=view.rootView
                val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                val file=java.io.File("build/screenshots/$name.png");file.parentFile!!.mkdirs()
                file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        capture("onboarding-first")
        repeat(3) {compose.onNodeWithTag("onboarding-next").performClick()}
        compose.onNodeWithText("Начать").assertExists()
        capture("onboarding-finish")
        compose.onNodeWithTag("onboarding-back").performClick()
        compose.onNodeWithTag("onboarding-step-2").assertExists()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.runOnIdle {assertTrue(finished)}
    }
    @Test fun presetValidationBlocksInvalidSave() {
        var saved: Preset?=null
        compose.setContent { OsifyTheme(AppPreferences(language="en",motion="off",dynamicColor=false)) { PresetEditor(Preset(),device,{}, {saved=it}) } }
        compose.onNodeWithTag("preset-name").performTextClearance()
        compose.onNodeWithTag("preset-save").performClick()
        compose.runOnIdle {assertEquals(null,saved)}
        compose.onNodeWithTag("preset-error").assertExists()
        compose.onNodeWithTag("preset-name").performScrollTo().performTextInput("My Alpine")
        compose.onNodeWithTag("preset-save").performClick()
        compose.runOnIdle {assertEquals("My Alpine",saved?.name)}
    }
}
