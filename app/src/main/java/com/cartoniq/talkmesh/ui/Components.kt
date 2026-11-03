package com.cartoniq.talkmesh.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Short text helper. */
@Composable
fun T(
    text: String,
    size: Int = 16,
    weight: FontWeight = FontWeight.Medium,
    color: Color = Ink,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    lineHeight: Int = 0,
    spacing: Float = 0f,
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight,
        letterSpacing = spacing.sp, textAlign = align,
        lineHeight = if (lineHeight > 0) lineHeight.sp else TextUnit.Unspecified
    )
}

/** Full-screen background that handles edge-to-edge insets and status bar icon color. */
@Composable
fun Screen(bg: Color, lightBg: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = lightBg
    }
    Box(Modifier.fillMaxSize().background(bg)) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), content = content)
    }
}

@Composable
fun Avatar(letter: String, bg: Color, size: Int = 48) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        T(letter.take(1).uppercase(), (size / 2.3f).toInt(), FontWeight.Black, if (bg == Ink) Paper else Ink)
    }
}

@Composable
fun Pill(text: String, bg: Color = Lime, fg: Color = Ink, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) { T(text, 15, FontWeight.ExtraBold, fg) }
}

@Composable
fun Sticker(text: String, bg: Color = Lime, rot: Float = -4f, modifier: Modifier = Modifier) {
    Box(modifier.rotate(rot).chunky(bg, 14.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
        T(text, 14, FontWeight.ExtraBold)
    }
}

@Composable
fun Header(title: String, sub: String, color: Color = Ink, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            T(title, 56, FontWeight.Black, color, spacing = -3f, lineHeight = 60)
            T(sub, 15, FontWeight.Medium, color)
        }
        right()
    }
}

/** kind: 0 chat, 1 radar, 2 safety. Drawn on a 24x24 grid. */
@Composable
fun Icon24(kind: Int, color: Color, px: Int = 24) {
    Canvas(Modifier.size(px.dp)) {
        scale(this.size.width / 24f, Offset.Zero) {
            val st = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (kind) {
                0 -> drawPath(Path().apply {
                    moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 16f); lineTo(9f, 16f); lineTo(4f, 20f); close()
                }, color, style = st)
                1 -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = st)
                    drawCircle(color, 4f, Offset(12f, 12f), style = st)
                    drawLine(color, Offset(12f, 12f), Offset(18f, 6f), 2.2f, StrokeCap.Round)
                }
                else -> {
                    drawPath(Path().apply { moveTo(12f, 3f); lineTo(21f, 19f); lineTo(3f, 19f); close() }, color, style = st)
                    drawLine(color, Offset(12f, 10f), Offset(12f, 14f), 2.2f, StrokeCap.Round)
                    drawCircle(color, 1.3f, Offset(12f, 17f))
                }
            }
        }
    }
}

@Composable
fun Halftone(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 12.dp.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) { drawCircle(color, 2.dp.toPx(), Offset(x, y)); x += step }
            y += step
        }
    }
}
