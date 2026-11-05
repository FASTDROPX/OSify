package dev.osify.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable fun ScrollFade(scroll: ScrollState,modifier: Modifier=Modifier,minimumOpacity: Float=0f) {
    val color=MaterialTheme.colorScheme.surface
    Spacer(modifier.fillMaxWidth().height(28.dp).drawWithCache {
        val gradient=Brush.verticalGradient(listOf(color,color.copy(alpha=0f)))
        onDrawBehind {
            val opacity=(scroll.value/24.dp.toPx()).coerceIn(minimumOpacity,1f)
            if(opacity>0f) drawRect(gradient,alpha=opacity)
        }
    })
}
