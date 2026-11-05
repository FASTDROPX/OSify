package dev.osify.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Small RAM wave. Frequency is continuous in the interpolated measurement, not discrete steps. */
@Composable fun MemoryWaveProgress(progress: Float,active: Boolean=true,modifier: Modifier=Modifier,previewPhase: Float?=null) {
    val motion=LocalMotion.current
    val target=progress.takeIf {it.isFinite()}?.coerceIn(0f,1f)?:0f
    val displayed by animateFloatAsState(target,tween(if(motion=="off") 0 else 1100,easing=LinearEasing),label="memory wave fill and frequency")
    val phaseState=if(previewPhase!=null) rememberUpdatedState(previewPhase) else if(active && motion=="full") {
        rememberInfiniteTransition(label="memory wave").animateFloat(0f,1f,infiniteRepeatable(tween(2600,easing=LinearEasing)),label="memory wave travel")
    } else rememberUpdatedState(0f)
    val ink=MaterialTheme.colorScheme.primary
    val track=MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(10.dp).testTag("memory-wave").semantics {progressBarRangeInfo=ProgressBarRangeInfo(target,0f..1f)}) {
        val phase=phaseState.value
        val stroke=3.dp.toPx()
        val start=stroke/2
        val stop=(size.width-stroke/2).coerceAtLeast(start)
        val width=stop-start
        val end=start+width*displayed
        val y=size.height/2
        val tail=end+6.dp.toPx()
        if(tail<stop) drawLine(track,Offset(tail,y),Offset(stop,y),stroke,StrokeCap.Round)
        if(displayed<=0f) return@Canvas
        val amplitude=if(motion=="off") 0f else .9.dp.toPx()
        val frequency=2f+6f*displayed
        val path=Path().apply {moveTo(start,y)}
        var x=start
        while(x<end) {
            val envelope=minOf((x-start)/10.dp.toPx(),(end-x)/10.dp.toPx(),1f).coerceAtLeast(0f)
            path.lineTo(x,y+sin(2*PI*((x-start)/width.coerceAtLeast(1f)*frequency-phase)).toFloat()*amplitude*envelope)
            x+=.75.dp.toPx()
        }
        path.lineTo(end,y)
        drawPath(path,ink,style=Stroke(stroke,cap=StrokeCap.Round))
    }
}
