package com.cartoniq.talkmesh.ui

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartoniq.talkmesh.data.Prefs
import com.cartoniq.talkmesh.data.Recorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- CHATS
@Composable
fun ChatsScreen(onTab: (Tab) -> Unit, openChat: (String) -> Unit, openOv: (Ov) -> Unit, onEnable: () -> Unit) {
    val mesh = LocalMesh.current
    var vibeDialog by remember { mutableStateOf(false) }
    var storyDialog by remember { mutableStateOf(false) }
    var roomDialog by remember { mutableStateOf(false) }
    val alive = mesh.alive()
    val keys = (listOf("nearby") + mesh.roomList().map { "room:${it.id}" } + alive.map { it.id } +
        mesh.threads.keys.filter { k -> k != "nearby" && !k.startsWith("room:") && alive.none { it.id == k } && !mesh.isBlocked(k) }).distinct()
    val storyOwners = mesh.stories.filter { !mesh.isBlocked(it.from) && System.currentTimeMillis() - it.ts < 24L * 3600_000 }
        .map { it.name }.distinct()
    val cardColors = listOf(Pink, Lime, Lilac, Peach)

    Screen(Paper) {
        Halftone(Blue, Modifier.align(Alignment.TopEnd).size(190.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
            Header("squad", "${alive.size} nearby" + if (mesh.running) "" else " · mesh off")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("fun", Lime, Ink) { openOv(Ov.FUN) }
                Pill("+ room", Lilac, Ink) { roomDialog = true }
                Pill(if (mesh.vibe.isEmpty()) "set vibe" else "vibe: ${mesh.vibe}", Pink, Ink) { vibeDialog = true }
                Pill("settings", Ink, Lime) { openOv(Ov.SETTINGS) }
            }
            if (!mesh.running || mesh.error != null) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth().chunky(Lime, 22.dp).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    T(mesh.error ?: "mesh is off, tap to find nearby phones", 14, FontWeight.Bold, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Pill("turn on", Ink, Lime, onEnable)
                }
            }
            Row(
                Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.clickable { storyDialog = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).border(3.dp, Ink, CircleShape).padding(5.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                        T("+", 26, FontWeight.Black)
                    }
                    T("story", 12, FontWeight.Bold)
                }
                storyOwners.forEachIndexed { i, n ->
                    Column(Modifier.clickable { openOv(Ov.STORIES) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).border(3.dp, if (i % 2 == 0) Orange else Blue, CircleShape).padding(5.dp)) {
                            Avatar(n, Color(0xFFE3D6BC), 50)
                        }
                        T(n.take(8), 12, FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                keys.forEachIndexed { i, key ->
                    val last = mesh.thread(key).lastOrNull()
                    val peer = mesh.peers[key]
                    val isRoom = key.startsWith("room:")
                    val title = if (key == "nearby") "Nearby room" else if (isRoom) mesh.roomName(key) ?: "room" else peer?.name ?: last?.name ?: "unknown"
                    val sub = last?.text ?: when {
                        key == "nearby" -> "everyone in range sees this room"
                        isRoom -> "private room · encrypted"
                        peer != null && peer.vibe.isNotEmpty() -> peer.vibe
                        peer != null -> "${peer.hops} hop${if (peer.hops == 1) "" else "s"} away · say hi"
                        else -> ""
                    }
                    Row(
                        Modifier.fillMaxWidth().rotate(if (i % 2 == 0) -1f else 1f).chunky(cardColors[i % 4], 28.dp)
                            .clickable { openChat(key) }.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(if (key == "nearby" || isRoom) "#" else title, Ink)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            T(title, 18, FontWeight.ExtraBold)
                            T(sub, 14)
                        }
                        if (peer != null) T("${peer.hops}h", 12, FontWeight.Bold)
                    }
                }
            }
        }
        Dock(Tab.CHATS, false, Paper, onTab)
    }
    if (vibeDialog) VibeDialog { vibeDialog = false }
    if (storyDialog) StoryComposer { storyDialog = false }
    if (roomDialog) RoomDialog { roomDialog = false }
}

// ---------------------------------------------------------------- RADAR
@Composable
fun RadarScreen(onTab: (Tab) -> Unit, openChat: (String) -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    val alive = mesh.alive()
    var selId by remember { mutableStateOf<String?>(null) }
    val p = alive.firstOrNull { it.id == selId } ?: alive.firstOrNull()
    var talking by remember { mutableStateOf(false) }
    val rec = remember { Recorder(ctx) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "a"
    )
    Screen(Ink, lightBg = false) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
            Header("radar", if (alive.isEmpty()) "nobody in range yet" else "rings show hops, not meters", Paper)
            Box(Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally).size(320.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val r = size.width / 2f
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 16f))
                    listOf(1f, .66f, .33f).forEach {
                        drawCircle(Color(0xFF555555), r * it, c, style = Stroke(2.dp.toPx(), pathEffect = dash))
                    }
                    drawArc(Lime.copy(alpha = .28f), sweep - 40f, 40f, true, Offset.Zero, Size(size.width, size.height))
                    val a = Math.toRadians(sweep.toDouble())
                    drawLine(Lime, c, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 3.dp.toPx())
                    drawCircle(Lime, 16.dp.toPx(), c); drawCircle(Ink, 5.dp.toPx(), c)
                }
                alive.forEachIndexed { i, pe ->
                    // The radio gives distance in hops but no direction, so the angle is a stable per-person spot.
                    val a = Math.toRadians((abs(pe.id.hashCode()) % 360).toDouble())
                    val d = when (pe.hops) { 1 -> .4f; 2 -> .62f; 3 -> .8f; else -> .95f }
                    val x = 160f + cos(a).toFloat() * d * 150f
                    val y = 160f + sin(a).toFloat() * d * 150f
                    val pc = listOf(Pink, Orange, Lilac, Lime)[i % 4]
                    Row(
                        Modifier.offset(x = (x - 14f).dp, y = (y - 14f).dp).clickable { selId = pe.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(pc).border(if (pe.id == p?.id) 3.dp else 0.dp, Lime, CircleShape))
                        Box(Modifier.padding(start = 4.dp).clip(CircleShape).background(Paper).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            T(pe.name.take(10), 13, FontWeight.ExtraBold)
                        }
                    }
                }
            }
            if (p != null) {
                Row(
                    Modifier.padding(horizontal = 24.dp).padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Paper).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(p.name, Pink)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        T(p.name, 18, FontWeight.ExtraBold)
                        T("${p.hops} hop${if (p.hops == 1) "" else "s"}" + if (p.vibe.isNotEmpty()) " · ${p.vibe}" else "", 14)
                    }
                    Pill("chat", Ink, Lime) { openChat(p.id) }
                }
                Box(
                    Modifier.padding(horizontal = 24.dp, vertical = 14.dp).fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape)
                        .background(if (talking) Orange else Lime)
                        .pointerInput(p.id) {
                            detectTapGestures(onPress = {
                                if (!ctx.has(Manifest.permission.RECORD_AUDIO)) {
                                    mic.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (rec.start()) {
                                    talking = true
                                    tryAwaitRelease()
                                    talking = false
                                    val f = rec.stop()
                                    if (f != null) {
                                        val ok = mesh.sendFile(listOf(p.id), { ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) }, f.name, "voice")
                                        if (ok) mesh.addVoiceLocal(p.id, Uri.fromFile(f).toString())
                                        else ctx.toast("walkie reaches direct neighbors only (1 hop)")
                                    }
                                } else ctx.toast("microphone is busy")
                            })
                        },
                    contentAlignment = Alignment.Center
                ) { T(if (talking) "recording… let go to send" else "hold to talk", 16, FontWeight.ExtraBold) }
            } else {
                T("Ask a friend to open TalkMesh with Bluetooth, Wi-Fi and location on.", 15, FontWeight.Medium, Paper, Modifier.padding(24.dp))
            }
        }
        Dock(Tab.RADAR, false, Ink, onTab)
    }
}

// ---------------------------------------------------------------- SAFETY
@Composable
fun SafetyScreen(prefs: Prefs, onTab: (Tab) -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var sent by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var siren by remember { mutableStateOf(false) }
    var aid by remember { mutableStateOf(false) }
    val loc = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (!ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)) loc.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    LaunchedEffect(torch) { setTorch(ctx, torch) }
    DisposableEffect(Unit) { onDispose { setTorch(ctx, false) } }
    LaunchedEffect(siren) {
        if (siren) {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            try {
                while (true) { tg.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 900); delay(1000) }
            } finally { tg.release() }
        }
    }

    Screen(Orange) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
            Header("sos", "needs a phone in range, not signal")
            Box(Modifier.fillMaxWidth().height(290.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(280.dp)) {
                    drawCircle(Ink.copy(alpha = .25f), 140.dp.toPx(), style = Stroke(3.dp.toPx()))
                    drawCircle(Ink.copy(alpha = .5f), 115.dp.toPx(), style = Stroke(3.dp.toPx()))
                }
                Box(
                    Modifier.size(200.dp).clip(CircleShape).background(Ink).pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            val job = scope.launch {
                                progress.animateTo(1f, tween(3000, easing = LinearEasing))
                                sent = true
                                val l = ctx.lastLocation()
                                mesh.sendSos(l?.latitude, l?.longitude)
                                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                v?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                            }
                            tryAwaitRelease()
                            job.cancel()
                            scope.launch { progress.snapTo(0f) }
                        })
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 5.dp.toPx()
                        drawArc(
                            Lime, -90f, 360f * progress.value, false, Offset(inset, inset),
                            Size(size.width - inset * 2, size.height - inset * 2),
                            style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T("SOS", 56, FontWeight.Black, Paper)
                        T(if (sent) "sent" else "hold 3s", 14, FontWeight.Bold, Lime)
                    }
                }
            }
            val n = mesh.alive().size
            T(
                if (sent) "sent to $n nearby phone${if (n == 1) "" else "s"} with your last known location"
                else "sends your last known location to every phone in range",
                16, FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 24.dp),
                align = androidx.compose.ui.text.style.TextAlign.Center
            )
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Tile("flashlight", if (torch) "tap to turn off" else "tap to light", Lime, torch, Modifier.weight(1f)) { torch = !torch }
                    Tile("siren", if (siren) "tap to stop" else "loud alert", Pink, siren, Modifier.weight(1f)) { siren = !siren }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Tile("first aid", "offline guide", Paper, false, Modifier.weight(1f)) { aid = true }
                    Tile("battery saver", if (prefs.battery.value) "on" else "off", Lilac, prefs.battery.value, Modifier.weight(1f)) {
                        prefs.battery.set(!prefs.battery.value)
                    }
                }
            }
        }
        Dock(Tab.SAFETY, true, Orange, onTab)
    }
    if (aid) AlertDialog(
        onDismissRequest = { aid = false },
        confirmButton = { TextButton(onClick = { aid = false }) { Text("ok") } },
        title = { Text("first aid basics") },
        text = {
            Text(
                "1. Check the area is safe first.\n2. Call your local emergency number if you can.\n" +
                    "3. Press firmly on heavy bleeding with a clean cloth.\n4. Keep the person warm, calm and still.\n" +
                    "5. Do not move someone with a possible neck or back injury.\n\nThis is a quick reminder, not medical advice. " +
                    "TalkMesh SOS only reaches phones within mesh range."
            )
        }
    )
}

@Composable
private fun Tile(title: String, sub: String, bg: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(84.dp).chunky(if (on) Lime else bg, 24.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) { Column { T(title, 17, FontWeight.ExtraBold); T(sub, 13) } }
}
