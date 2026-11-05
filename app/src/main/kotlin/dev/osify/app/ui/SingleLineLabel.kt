package dev.osify.app.ui
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** Fit action captions at measurement time; a label never loses its final glyph. */
@Composable internal fun SingleLineLabel(text: String,modifier: Modifier=Modifier,style: TextStyle=MaterialTheme.typography.labelLarge,minSp: Float=9f,maxSp: Float=12f) {
    BasicText(text,modifier,style=style.copy(color=if(style.color==androidx.compose.ui.graphics.Color.Unspecified) LocalContentColor.current else style.color),maxLines=1,softWrap=false,
        autoSize=TextAutoSize.StepBased(minFontSize=minSp.sp,maxFontSize=maxSp.sp,stepSize=.25.sp))
}
