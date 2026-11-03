package com.cartoniq.talkmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF121212)
val Muted = Color(0xFFB8B2A5)

// Palette is observable so the theme pack can be switched live in Settings.
var Paper by mutableStateOf(Color(0xFFF4ECDD))
var Blue by mutableStateOf(Color(0xFF2B3CFF))
var Orange by mutableStateOf(Color(0xFFFF6A2B))
var Pink by mutableStateOf(Color(0xFFFFB3DA))
var Lime by mutableStateOf(Color(0xFFF4FF52))
var Lilac by mutableStateOf(Color(0xFFBFC8FF))
var Peach by mutableStateOf(Color(0xFFFFC9A8))

val ThemePacks = listOf("riso", "sunset", "mint")

fun applyTheme(name: String) {
    when (name) {
        "sunset" -> { Paper = Color(0xFFFFEDD8); Blue = Color(0xFFC81D3A); Orange = Color(0xFFFF8C1A); Pink = Color(0xFFFFB5A7); Lime = Color(0xFFFFE156); Lilac = Color(0xFFE8B4F5); Peach = Color(0xFFFFD6A5) }
        "mint" -> { Paper = Color(0xFFE6F4EA); Blue = Color(0xFF0B6E4F); Orange = Color(0xFFFF7043); Pink = Color(0xFFFFB3C7); Lime = Color(0xFFC7F464); Lilac = Color(0xFFB8E0F6); Peach = Color(0xFFFFD9B3) }
        else -> { Paper = Color(0xFFF4ECDD); Blue = Color(0xFF2B3CFF); Orange = Color(0xFFFF6A2B); Pink = Color(0xFFFFB3DA); Lime = Color(0xFFF4FF52); Lilac = Color(0xFFBFC8FF); Peach = Color(0xFFFFC9A8) }
    }
}

@Composable
fun TalkMeshTheme(textScale: Float = 1f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * textScale)) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Lime, background = Paper, surface = Paper,
            onSurface = Ink, onBackground = Ink
        ),
        content = content
    )
    }
}

/** Hard offset "riso" shadow drawn behind the element. */
fun Modifier.hardShadow(radius: Dp, color: Color = Ink, offset: Dp = 4.dp): Modifier = drawBehind {
    val o = offset.toPx()
    drawRoundRect(color, topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(radius.toPx()))
}

/** Chunky sticker card: fill + 2dp ink border + optional hard shadow. */
fun Modifier.chunky(bg: Color, radius: Dp = 24.dp, shadow: Boolean = true): Modifier {
    val s = RoundedCornerShape(radius)
    val base = if (shadow) this.hardShadow(radius) else this
    return base.background(bg, s).border(2.dp, Ink, s)
}
