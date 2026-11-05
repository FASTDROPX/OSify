package dev.osify.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val BrandBlue=Color(0xFF91C8FF)
@Composable fun OrbitMark(size: Dp=48.dp,animated: Boolean=false) {
    Canvas(Modifier.size(size).semantics { contentDescription="OSify" }) {
        val s=this.size.minDimension
        drawRoundRect(BrandBlue,cornerRadius=CornerRadius(s*.28f))
        for(y in listOf(.32f,.55f)) {
            val path=Path().apply { moveTo(s*.28f,s*y);lineTo(s*.5f,s*(y+.19f));lineTo(s*.72f,s*y) }
            drawPath(path,Color.White,style=Stroke(s*.085f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}
