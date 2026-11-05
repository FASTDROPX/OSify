package dev.osify.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

private data class Paper(val side: Int,val delay: Float,val vx: Float,val vy: Float,val spin: Float,val phase: Float,val width: Float,val height: Float,val color: Int)
/** Analytic ballistic motion: independent of frame rate, deterministic for visual review. */
@Composable fun ConfettiBurst(progress: Float,modifier: Modifier=Modifier) {
    val papers=remember {
        val random=Random(8417)
        List(64) {i->Paper(if(i%2==0) 1 else -1,random.nextFloat()*.28f,110+random.nextFloat()*230,-610-random.nextFloat()*230,
            (random.nextFloat()-.5f)*620,random.nextFloat()*6.28f,7+random.nextFloat()*6,6+random.nextFloat()*5,i%4)}
    }
    Canvas(modifier.fillMaxSize()) {
        val colors=listOf(BrandBlue,Color(0xFFDCEEFF),Color(0xFF55A4E9),Color(0xFF89D6BA))
        val elapsed=progress*2.6f
        papers.forEach {p->
            val t=elapsed-p.delay
            if(t<0f || t>2.6f) return@forEach
            val drag=(1-exp(-1.1f*t))/1.1f
            val x=(if(p.side==1) -8.dp.toPx() else size.width+8.dp.toPx())+p.side*p.vx.dp.toPx()*drag+sin(t*7+p.phase)*9.dp.toPx()*t
            val y=size.height-16.dp.toPx()+p.vy.dp.toPx()*t+(.5f*650).dp.toPx()*t*t
            val alpha=((2.6f-t)/.45f).coerceIn(0f,1f)
            val center=Offset(x,y)
            rotate(p.spin*t+p.phase*57,center) {
                scale(.3f+.7f*abs(cos(t*6+p.phase)),1f,center) {
                    drawRoundRect(colors[p.color].copy(alpha=alpha),Offset(x-p.width.dp.toPx()/2,y-p.height.dp.toPx()/2),Size(p.width.dp.toPx(),p.height.dp.toPx()),CornerRadius(1.5.dp.toPx()))
                }
            }
        }
    }
}
