package dev.osify.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.osify.app.data.AppPreferences
import dev.osify.app.data.VirtualMachine
import dev.osify.app.engine.GuestConsoleScreen
import dev.osify.app.ui.OsifyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="ru-rRU-w360dp-h825dp",application=TestOsifyApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsoleLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun checkLabels(scale: Float) {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                OsifyTheme(AppPreferences(language="ru",theme="dark",motion="off")) {
                    GuestConsoleScreen(VirtualMachine("layout","Alpine Linux","LINUX",port=1),{})
                }
            }
        }
        for(tag in listOf("console-keyboard","console-text","console-pointer","console-keys")) {
            compose.onNodeWithTag("$tag-label",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();assertTrue(action(layouts))
                val result=layouts.single()
                // Paragraph container width may differ by a floating-point epsilon. Check the
                // actual visible text and last glyph instead of that virtual container width.
                assertEquals(result.layoutInput.text.length,result.getLineEnd(0,visibleEnd=true),"$tag must show its full label")
                assertTrue(result.getLineRight(0)<=result.size.width+.5f,"$tag text extends beyond its bounds at $scale")
                assertFalse(result.didOverflowHeight,"$tag text height is clipped at $scale")
            }
        }
    }
    @Test fun labelsFitPhoneAtNormalFont()=checkLabels(1f)
    @Test fun labelsFitPhoneAtLargeFont()=checkLabels(1.35f)
}
