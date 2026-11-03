package com.cartoniq.talkmesh.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cartoniq.talkmesh.data.playUri

fun ago(ts: Long): String {
    val m = (System.currentTimeMillis() - ts) / 60_000
    return when { m < 1 -> "now"; m < 60 -> "${m}m"; m < 1440 -> "${m / 60}h"; else -> "${m / 1440}d" }
}

private fun Context.fileInfo(uri: Uri): Pair<String, String> {
    var name = "file"; var size = ""
    contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = c.getColumnIndex(OpenableColumns.SIZE)
            if (ni >= 0) name = c.getString(ni) ?: "file"
            if (si >= 0 && !c.isNull(si)) size = "%.1f MB".format(c.getLong(si) / 1048576.0)
        }
    }
    return name to size
}

private fun Context.openReceived(uri: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { toast("Saved on your phone in Downloads/Nearby") }
}

// ---------------------------------------------------------------- FUN HUB
@Composable
fun FunScreen(onTab: (Tab) -> Unit, openOv: (Ov) -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    Screen(Paper) {
        Halftone(Orange, Modifier.align(Alignment.TopEnd).size(190.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
            Header("fun", "games, quests & more")
            Column(Modifier.padding(24.dp).fillMaxWidth().hardShadow(32.dp, Blue, 5.dp).clip(RoundedCornerShape(32.dp)).background(Ink).padding(22.dp)) {
                T("silent disco", 34, FontWeight.Black, Lime, spacing = -1f)
                T(
                    if (mesh.discoUri != null) "now: ${mesh.discoName} · host ${mesh.discoHost}" else "one song, every nearby phone",
                    14, color = Paper, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                Pill("open", Lime, Ink) { openOv(Ov.DISCO) }
            }
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FunTile("mesh games", "tic-tac-toe", Lime, Modifier.weight(1f)) { openOv(Ov.GAMES) }
                    FunTile("squad quests", "earn points", Pink, Modifier.weight(1f)) { openOv(Ov.QUESTS) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FunTile("walkie", "hold to talk on radar", Lilac, Modifier.weight(1f)) { onTab(Tab.RADAR) }
                    FunTile("mesh points", "${mesh.points()} pts", Peach, Modifier.weight(1f)) { openOv(Ov.QUESTS) }
                }
                FunTile("mesh drop", "send files to nearby phones", Paper, Modifier.fillMaxWidth()) { openOv(Ov.DROP) }
                FunTile("local board", "notes and confessions in range", Paper, Modifier.fillMaxWidth()) { openOv(Ov.BOARD) }
                FunTile("carry mode", "${mesh.carried} messages stored for others", Lime, Modifier.fillMaxWidth()) {
                    ctx.toast("Your phone hands stored posts to phones that join later")
                }
            }
        }
        Dock(null, false, Paper, onTab)
    }
}

@Composable
private fun FunTile(title: String, sub: String, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 84.dp).chunky(bg, 26.dp).clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) { Column { T(title, 18, FontWeight.ExtraBold); T(sub, 13) } }
}

// ---------------------------------------------------------------- STORIES
@Composable
fun StoriesScreen(onClose: () -> Unit) {
    val mesh = LocalMesh.current
    val list = mesh.stories.filter { !mesh.isBlocked(it.from) && System.currentTimeMillis() - it.ts < 24L * 3600_000 }.sortedByDescending { it.ts }
    var idx by remember { mutableIntStateOf(0) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(idx, list.size) {
        if (list.isEmpty()) return@LaunchedEffect
        p.snapTo(0f)
        p.animateTo(1f, tween(5000, easing = LinearEasing))
        if (idx < list.lastIndex) idx++ else onClose()
    }
    Screen(Ink, lightBg = false) {
        if (list.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T("no stories yet", 28, FontWeight.Black, Paper)
                T("stories vanish after 24 hours", 15, color = Muted, modifier = Modifier.padding(vertical = 12.dp))
                Pill("back", Lime, Ink, onClose)
            }
        } else {
            val s = list[idx.coerceIn(0, list.lastIndex)]
            val bg = storyColor(s.color)
            Box(Modifier.fillMaxSize().clickable { if (idx < list.lastIndex) idx++ else onClose() })
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                list.take(8).forEachIndexed { i, _ ->
                    val f = if (i < idx) 1f else if (i == idx) p.value else 0f
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Paper.copy(alpha = .3f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Paper))
                    }
                }
            }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Avatar(s.name, Pink, 44)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    T(s.name, 16, FontWeight.ExtraBold, Paper)
                    T("${ago(s.ts)} ago · vanishes in 24h", 13, color = Muted)
                }
                if (s.from != mesh.myId) Pill("block", Paper, Ink) { mesh.block(s.from, s.name); onClose() }
                Spacer(Modifier.width(8.dp))
                Pill("close", Lime, Ink, onClose)
            }
            Box(Modifier.align(Alignment.Center).padding(horizontal = 30.dp).fillMaxWidth().height(420.dp).rotate(-2f).clip(RoundedCornerShape(40.dp)).background(bg)) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-40).dp).size(230.dp).clip(CircleShape).background(Color.Black.copy(alpha = .12f)))
                T(s.text, 40, FontWeight.Black, storyFg(bg), Modifier.align(Alignment.CenterStart).padding(28.dp), lineHeight = 44, spacing = -1f)
            }
        }
    }
}

// ---------------------------------------------------------------- MESH DROP
@Composable
fun DropScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    val direct = mesh.directPeers()
    var target by remember { mutableStateOf<String?>(null) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { val (n, s) = ctx.fileInfo(uri); fileUri = uri; fileName = n; fileSize = s; sending = false }
    }
    val tgt = direct.firstOrNull { it.id == target } ?: direct.firstOrNull()
    Screen(Paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
            Header("drop", "files go to direct neighbors") { Pill("done", Lime, Ink, onBack) }
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (direct.isEmpty()) T("No direct neighbors yet. Stand near a friend with TalkMesh open.", 15)
                direct.forEach { pe ->
                    val on = pe.id == tgt?.id
                    Row(
                        Modifier.fillMaxWidth().then(if (on) Modifier.chunky(Pink, 24.dp) else Modifier.chunky(Color.Transparent, 24.dp, shadow = false))
                            .clickable { target = pe.id }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(pe.name, Ink, 42)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { T(pe.name, 16, FontWeight.ExtraBold); T("direct neighbor", 13) }
                        if (on) T("selected", 13, FontWeight.ExtraBold)
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth().chunky(Lime, 28.dp).clickable { picker.launch("*/*") }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp, 56.dp).clip(RoundedCornerShape(10.dp)).background(Ink), contentAlignment = Alignment.Center) { T("file", 13, FontWeight.Black, Lime) }
                Column(Modifier.padding(start = 14.dp)) {
                    T(fileName ?: "tap to choose a file", 17, FontWeight.ExtraBold)
                    T(if (fileSize.isEmpty()) "no ads, no limits" else "$fileSize · no ads, no limits", 13)
                }
            }
            if (sending) {
                Column(Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink).padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        T("sending to ${tgt?.name ?: "…"}", 15, FontWeight.ExtraBold, Paper)
                        T("${(mesh.sendProgress * 100).toInt()}%", 15, FontWeight.ExtraBold, Lime)
                    }
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(12.dp).clip(CircleShape).background(Color(0xFF3A3A3A))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(mesh.sendProgress).background(Lime))
                    }
                }
            }
            if (mesh.inbox.isNotEmpty()) {
                T("received", 20, FontWeight.Black, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
                mesh.inbox.take(10).forEach { inc ->
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 5.dp).fillMaxWidth().chunky(Lilac, 22.dp).clickable { ctx.openReceived(inc.uri) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { T(inc.name, 16, FontWeight.ExtraBold); T("from ${inc.fromName} · ${ago(inc.ts)}", 13) }
                        T("open", 14, FontWeight.ExtraBold)
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth().height(60.dp).hardShadow(30.dp, Blue, 5.dp)
                .clip(RoundedCornerShape(30.dp)).background(Ink)
                .clickable {
                    val u = fileUri; val t = tgt
                    when {
                        u == null -> ctx.toast("choose a file first")
                        t == null -> ctx.toast("no direct neighbor to send to")
                        else -> sending = mesh.sendFile(listOf(t.id), { ctx.contentResolver.openFileDescriptor(u, "r") }, fileName ?: "file", "file")
                    }
                },
            contentAlignment = Alignment.Center
        ) { T("send", 18, FontWeight.Black, Lime) }
    }
}

// ---------------------------------------------------------------- SILENT DISCO
@Composable
fun DiscoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "r"
    )
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = ctx.fileInfo(uri).first
            val sentToAny = mesh.discoShare({ ctx.contentResolver.openFileDescriptor(uri, "r") }, uri.toString(), name)
            if (!sentToAny) ctx.toast("no direct neighbors yet, the song is ready on this phone")
        }
    }
    val has = mesh.discoUri != null
    Screen(Blue, lightBg = false) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Header("disco", "silent disco", Paper) { Pill("done", Lime, Ink, onBack) }
            Canvas(Modifier.padding(top = 28.dp).size(260.dp).rotate(if (mesh.discoPlaying) spin else 0f)) {
                val c = center
                val r = size.width / 2f
                drawCircle(Ink, r, c)
                listOf(.85f, .70f, .55f).forEach { drawCircle(Color(0xFF333333), r * it, c, style = Stroke(2.dp.toPx())) }
                drawCircle(Pink, r * .32f, c)
                drawCircle(Ink, 8.dp.toPx(), c)
            }
            T(if (has) mesh.discoName.take(28) else "no song yet", 26, FontWeight.Black, Paper, Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp))
            T(if (has) "host: ${mesh.discoHost}" else "pick a song to share with phones near you", 15, color = Paper, modifier = Modifier.padding(horizontal = 24.dp))
            Box(
                Modifier.padding(top = 20.dp).size(76.dp).clip(CircleShape).background(if (has) Lime else Lime.copy(alpha = .4f))
                    .clickable { if (has) mesh.discoPlay(!mesh.discoPlaying) else ctx.toast("pick a song first") },
                contentAlignment = Alignment.Center
            ) { T(if (mesh.discoPlaying) "II" else "▶", 26, FontWeight.Black, Ink) }
            Row(Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    mesh.directPeers().take(4).forEachIndexed { i, pe ->
                        Box(Modifier.offset(x = (-12 * i).dp)) { Avatar(pe.name, listOf(Pink, Lime, Orange, Lilac)[i % 4], 48) }
                    }
                }
                Pill("pick song", Lime, Ink) { pick.launch("audio/*") }
                Pill("invite", Paper, Ink) { ctx.shareApp() }
            }
            T("Everyone near you gets the song, then play and pause stay roughly in sync (small delay is normal).", 13, color = Paper, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

// ---------------------------------------------------------------- CHAT THREAD
@Composable
fun ChatThread(key: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    val msgs = mesh.thread(key)
    var text by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) state.animateScrollToItem(msgs.lastIndex) }
    val peer = mesh.peers[key]
    val isRoom = key.startsWith("room:")
    val title = if (key == "nearby") "Nearby room" else if (isRoom) mesh.roomName(key) ?: "room" else peer?.name ?: msgs.lastOrNull()?.name ?: "chat"
    Screen(Paper) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("‹ back", Lime, Ink, onBack)
                Spacer(Modifier.width(12.dp))
                Avatar(if (key == "nearby" || isRoom) "#" else title, Pink, 44)
                T(title, 20, FontWeight.Black, modifier = Modifier.padding(start = 10.dp).weight(1f))
                if (isRoom) {
                    Pill("invite", Ink, Lime) { ctx.shareText("Join my TalkMesh room \"$title\" with code ${mesh.roomCode(key)}") }
                    Spacer(Modifier.width(6.dp))
                    Pill("leave", Paper, Ink) { mesh.leaveRoom(key.removePrefix("room:")); onBack() }
                } else if (key != "nearby") Pill("block", Ink, Lime) { mesh.block(key, title); onBack() }
            }
            if (key == "nearby") T("everyone in range can read this room", 12, color = Color(0xFF6B655A), modifier = Modifier.padding(horizontal = 20.dp))
            else if (isRoom) T("private room: only people with the code can read it", 12, color = Color(0xFF6B655A), modifier = Modifier.padding(horizontal = 20.dp))
            else T("end-to-end encrypted · keys are trusted on first use", 12, color = Color(0xFF6B655A), modifier = Modifier.padding(horizontal = 20.dp))
            if (key in mesh.keyChanged) Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().chunky(Orange, 18.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                T("this person's security key changed. Only trust it if you know why (reinstall, new phone).", 13, FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill("trust", Ink, Lime) { mesh.trustNewKey(key) }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), state = state, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(msgs) { m ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(Modifier.widthIn(max = 280.dp).chunky(if (m.mine) Lime else Paper, 22.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
                            if (!m.mine && (key == "nearby" || isRoom)) T(m.name, 12, FontWeight.ExtraBold)
                            if (m.uri != null) Box(Modifier.clip(CircleShape).background(Ink).clickable { playUri(ctx, m.uri) }.padding(horizontal = 16.dp, vertical = 8.dp)) { T("▶ voice note", 15, FontWeight.ExtraBold, Lime) }
                            else T(m.text, 16, FontWeight.Medium)
                        }
                    }
                }
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(CircleShape).border(2.dp, Ink, CircleShape).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(500) },
                        textStyle = TextStyle(fontSize = 16.sp, color = Ink), singleLine = true,
                        decorationBox = { inner -> Box { if (text.isEmpty()) T("type a message", 16, color = Color(0xFF6B655A)); inner() } }
                    )
                }
                Spacer(Modifier.width(10.dp))
                Pill("send", Ink, Lime) {
                    if (text.isNotBlank()) {
                        when {
                            isRoom -> { mesh.sendRoom(key.removePrefix("room:"), text); text = "" }
                            mesh.sendChat(if (key == "nearby") "*" else key, text) -> text = ""
                            else -> ctx.toast("waiting for their security key, try again in a few seconds")
                        }
                    }
                }
            }
        }
    }
}
