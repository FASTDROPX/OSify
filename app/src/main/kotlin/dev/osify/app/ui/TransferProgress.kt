package dev.osify.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable fun TransferProgress(progress: Float?,active: Boolean=true,modifier: Modifier=Modifier,previewPhase: Float?=null) {
    val motion=LocalMotion.current
    val value by animateFloatAsState((progress?:0f).coerceIn(0f,1f),tween(if(motion=="off") 0 else 350),label="download bytes")
    val phaseState=if(previewPhase!=null) rememberUpdatedState(previewPhase) else if(active && motion=="full") {
        rememberInfiniteTransition(label="download wave").animateFloat(0f,1f,infiniteRepeatable(tween(1800,easing=LinearEasing)),label="wave phase")
    } else rememberUpdatedState(0f)
    val color=MaterialTheme.colorScheme.primary
    val track=MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(20.dp).semantics {progressBarRangeInfo=progress?.let {ProgressBarRangeInfo(it.coerceIn(0f,1f),0f..1f)}?:ProgressBarRangeInfo.Indeterminate}) {
        val phase=phaseState.value
        val stroke=3.dp.toPx();val mid=size.height/2
        if(progress!=null && value<1f) drawLine(track,Offset((size.width*value+6.dp.toPx()).coerceAtMost(size.width),mid),Offset(size.width-stroke/2,mid),stroke,StrokeCap.Round)
        val start=if(progress==null) (size.width+size.width*.3f)*phase-size.width*.3f else 0f
        val end=if(progress==null) start+size.width*.3f else size.width*value
        if(end<=0) return@Canvas
        val path=Path();val a=if(motion=="full" && active) 3.dp.toPx() else 0f
        val from=start.coerceAtLeast(0f);val to=end.coerceAtMost(size.width)
        var x=from
        while(x<=to) {
            val envelope=sin(PI*((x-from)/(to-from).coerceAtLeast(1f))).toFloat()
            val y=mid+sin((x/28.dp.toPx()-phase)*2*PI).toFloat()*a*envelope
            if(x==from) path.moveTo(x,y) else path.lineTo(x,y)
            x+=1.5.dp.toPx()
        }
        drawPath(path,color,style=Stroke(stroke,cap=StrokeCap.Round))
    }
}
