package dev.osify.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale

/** Passed to the window recomposer so Material and custom animations share the preference. */
class AppMotionScale: MotionDurationScale {
    override var scaleFactor by mutableFloatStateOf(1f)
    fun update(preference: String,systemScale: Float) {
        scaleFactor=when(preference) { "off"->0f;"reduced"->minOf(systemScale,.2f);else->systemScale }.coerceAtLeast(0f)
    }
}
