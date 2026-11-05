package dev.osify.app.ui

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Animates every direct page element, including headers, actions and live status cards. */
@Composable fun AnimatedPageColumn(pageKey: Any,modifier: Modifier=Modifier,spacing: Dp=14.dp,content: @Composable ()->Unit) {
    val motion=LocalMotion.current
    val reveal=remember { Animatable(if(motion=="off") 1f else 0f) }
    LaunchedEffect(pageKey,motion) {
        reveal.snapTo(if(motion=="off") 1f else 0f)
        reveal.animateTo(1f,tween(if(motion=="full") 820 else if(motion=="off") 0 else 100,easing=LinearEasing))
    }
    Layout(content=content,modifier=modifier) {measurables,constraints->
        val children=measurables.map {it.measure(constraints.copy(minHeight=0,maxHeight=Constraints.Infinity))}.filter {it.height>0}
        val gap=spacing.roundToPx()
        val width=constraints.constrainWidth(children.maxOfOrNull {it.width}?:0)
        val height=constraints.constrainHeight(children.sumOf {it.height}+gap*(children.size-1).coerceAtLeast(0))
        layout(width,height) {
            var y=0
            children.forEachIndexed {index,p->
                val delay=if(motion=="full") index.coerceAtMost(8)*28f else 0f
                val progress=reveal.value
                val t=if(motion=="off") 1f else ((progress*820-delay)/(820-delay)).coerceIn(0f,1f)
                val eased=MotionTokens.settle.transform(t)
                val offset=if(motion=="full") ((1-eased)*42.dp.toPx()).toInt() else 0
                p.placeRelativeWithLayer(0,y+offset) {
                    alpha=if(motion=="full") eased else progress
                    clip=false
                    compositingStrategy=CompositingStrategy.ModulateAlpha
                }
                y+=p.height+gap
            }
        }
    }
}
