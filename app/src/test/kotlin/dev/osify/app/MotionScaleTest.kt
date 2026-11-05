package dev.osify.app

import org.junit.Test
import kotlin.test.assertEquals

class MotionScaleTest {
    @Test fun appAndSystemMotionPreferencesAreCombined() {
        val scale=AppMotionScale()
        scale.update("full",0f);assertEquals(0f,scale.scaleFactor)
        scale.update("off",1f);assertEquals(0f,scale.scaleFactor)
        scale.update("reduced",2f);assertEquals(.2f,scale.scaleFactor)
        scale.update("full",2f);assertEquals(2f,scale.scaleFactor)
    }
}
