@file:OptIn(ExperimentalFoundationApi::class)
package com.cartoniq.talkmesh.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val load = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        load.animateTo(1f, tween(2000, easing = LinearEasing))
        onDone()
    }
    Screen(Paper) {
        Halftone(Blue, Modifier.align(Alignment.TopEnd).size(200.dp))
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Blue, w * .30f, Offset(w * .38f, h * .22f), blendMode = BlendMode.Multiply)
            drawCircle(Pink, w * .30f, Offset(w * .62f, h * .28f), blendMode = BlendMode.Multiply)
            drawCircle(Lime, w * .26f, Offset(w * .48f, h * .38f), blendMode = BlendMode.Multiply)
            val pts = listOf(Offset(.23f, .15f), Offset(.64f, .12f), Offset(.79f, .27f), Offset(.46f, .31f), Offset(.28f, .40f))
                .map { Offset(it.x * w, it.y * h) }
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 3 to 4, 0 to 4).forEach { (a, b) ->
                drawLine(Ink, pts[a], pts[b], 3.dp.toPx())
            }
            pts.forEach { drawCircle(Ink, 11.dp.toPx(), it); drawCircle(Paper, 4.dp.toPx(), it) }
        }
        Sticker("no wifi. still vibing.", Lime, 6f, Modifier.align(Alignment.TopEnd).padding(24.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 26.dp, bottom = 110.dp)) {
            T("talk\nmesh", 68, FontWeight.Black, spacing = -4f, lineHeight = 64)
            T("chat with zero signal.", 18, modifier = Modifier.padding(top = 12.dp))
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 54.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(5) { i ->
                Box(
                    Modifier.width(36.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (load.value > i / 5f) Ink else Color.Transparent)
                        .border(2.dp, Ink, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

private val titles = listOf("no wifi?\nno problem.", "you talk.\nthey hear it.", "your squad\nhas your back.")
private val descs = listOf(
    "Phones link up through Bluetooth and relay your messages hop by hop.",
    "Hold to talk and send a voice note to a friend nearby.",
    "One hold sends your location to every device around you."
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val bgs = listOf(Blue, Orange, Pink)
    val fgs = listOf(Paper, Ink, Ink)
    val shadows = listOf(Lime, Paper, Blue)
    val page = pager.currentPage
    val bg by animateColorAsState(bgs[page], label = "bg")
    val fg = fgs[page]

    Screen(bg, lightBg = page != 0) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { p -> OnbPage(p, fgs[p]) }
        if (page < 2) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).heightIn(min = 48.dp).clickable { onDone() }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) { T("skip", 16, FontWeight.Bold, fg) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp, vertical = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.width(36.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(fg.copy(alpha = if (i == page) 1f else .3f))
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().height(60.dp).hardShadow(30.dp, shadows[page], 5.dp)
                    .clip(RoundedCornerShape(30.dp)).background(Ink)
                    .clickable { if (page < 2) scope.launch { pager.animateScrollToPage(page + 1) } else onDone() },
                contentAlignment = Alignment.Center
            ) { T(if (page < 2) "next →" else "let's go", 18, FontWeight.Black, Lime) }
        }
    }
}

@Composable
private fun OnbPage(page: Int, fg: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        T("0${page + 1} / 03", 15, FontWeight.Black, fg, Modifier.padding(top = 16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                0 -> Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val r = size.minDimension / 2f
                    drawCircle(Paper, r * .95f, c, style = Stroke(3.dp.toPx()))
                    drawCircle(Paper, r * .60f, c, style = Stroke(3.dp.toPx()))
                    val ends = listOf(Offset(-.7f, -.5f), Offset(.7f, -.35f), Offset(-.55f, .6f), Offset(.65f, .55f))
                        .map { Offset(c.x + it.x * r, c.y + it.y * r) }
                    ends.forEach { drawLine(Paper, c, it, 3.dp.toPx()) }
                    ends.forEach { drawCircle(Lime, 16.dp.toPx(), it); drawCircle(Ink, 16.dp.toPx(), it, style = Stroke(3.dp.toPx())) }
                    drawCircle(Pink, 40.dp.toPx(), c); drawCircle(Ink, 40.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    drawCircle(Ink, 12.dp.toPx(), c)
                }
                1 -> Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 20.dp).size(180.dp).clip(CircleShape).background(Lime))
                    Column(Modifier.align(Alignment.TopStart).padding(top = 60.dp).rotate(-6f).width(230.dp).chunky(Paper, 32.dp).padding(horizontal = 24.dp, vertical = 20.dp)) {
                        T("hey!", 40, FontWeight.Black); T("you", 14, FontWeight.Bold)
                    }
                    Column(Modifier.align(Alignment.BottomEnd).padding(bottom = 30.dp).rotate(5f).width(230.dp).chunky(Ink, 32.dp, shadow = false).padding(horizontal = 24.dp, vertical = 20.dp)) {
                        T("got it!", 40, FontWeight.Black, Lime); T("them", 14, FontWeight.Bold, Lime)
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = center
                        val r = size.minDimension / 2f
                        drawCircle(Ink, r * .95f, c, style = Stroke(3.dp.toPx()))
                        drawCircle(Ink, r * .65f, c, style = Stroke(3.dp.toPx()))
                        listOf(Offset(-.8f, -.4f), Offset(.8f, .3f), Offset(-.45f, .8f)).forEach {
                            val p = Offset(c.x + it.x * r, c.y + it.y * r)
                            drawCircle(Lime, 11.dp.toPx(), p); drawCircle(Ink, 11.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                        }
                    }
                    Box(Modifier.size(140.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                        T("SOS", 40, FontWeight.Black, Paper)
                    }
                }
            }
        }
        T(titles[page], 40, FontWeight.Black, fg, lineHeight = 42, spacing = -1.5f)
        T(descs[page], 17, FontWeight.Medium, fg, Modifier.padding(top = 12.dp, bottom = 180.dp))
    }
}
