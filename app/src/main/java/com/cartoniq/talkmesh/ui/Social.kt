package com.cartoniq.talkmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun storyColor(i: Int): Color = listOf(Blue, Pink, Orange, Lime, Lilac)[Math.floorMod(i, 5)]
fun storyFg(bg: Color): Color = if (bg == Blue) Paper else Ink

@Composable
fun VibeDialog(onClose: () -> Unit) {
    val mesh = LocalMesh.current
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("close") } },
        title = { Text("your vibe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("studying", "free", "need chai", "busy", "chilling", "clear").forEach { v ->
                    Pill(v, if (v == mesh.vibe) Lime else Pink, Ink) { mesh.updateVibe(if (v == "clear") "" else v); onClose() }
                }
            }
        }
    )
}

@Composable
fun StoryComposer(onClose: () -> Unit) {
    val mesh = LocalMesh.current
    var text by remember { mutableStateOf("") }
    var c by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = { mesh.postStory(text, c); onClose() }) { Text("post") } },
        dismissButton = { TextButton(onClick = onClose) { Text("cancel") } },
        title = { Text("new story") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it.take(120) }, label = { Text("what's up?") })
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(5) { i ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(storyColor(i)).border(if (i == c) 3.dp else 1.dp, Ink, CircleShape).clickable { c = i })
                    }
                }
                T("vanishes in 24 hours and is seen by phones in range", 12, modifier = Modifier.padding(top = 10.dp))
            }
        }
    )
}

// ---------------------------------------------------------------- GAMES
@Composable
fun GamesScreen(onBack: () -> Unit) {
    val mesh = LocalMesh.current
    val g = mesh.game
    Screen(Paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header("games", "tic-tac-toe with a friend") { Pill("done", Lime, Ink, onBack) }
            if (g == null) {
                val peers = mesh.alive()
                if (peers.isEmpty()) T("Nobody in range yet.", 16, modifier = Modifier.padding(24.dp))
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    peers.forEach { p ->
                        Row(Modifier.fillMaxWidth().chunky(Lime, 24.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name, Ink, 42)
                            T(p.name, 17, FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            Pill("challenge", Ink, Lime) { mesh.gameInvite(p) }
                        }
                    }
                }
            } else {
                val st = when {
                    g.over == "draw" -> "draw!"
                    g.over.isNotEmpty() -> if (g.over[0] == g.mark) "you win!" else "${g.oppName} wins"
                    g.turn == g.mark -> "your turn (${g.mark})"
                    else -> "waiting for ${g.oppName}"
                }
                T(st, 28, FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
                Column(Modifier.align(Alignment.CenterHorizontally)) {
                    repeat(3) { r ->
                        Row {
                            repeat(3) { c ->
                                val i = r * 3 + c
                                val cell = g.cells[i]
                                Box(
                                    Modifier.size(100.dp).padding(5.dp).chunky(when (cell) { 'X' -> Pink; 'O' -> Lime; else -> Paper }, 20.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).clickable { mesh.gameMove(i) },
                                    contentAlignment = Alignment.Center
                                ) { if (cell != '-') T(cell.toString(), 44, FontWeight.Black) }
                            }
                        }
                    }
                }
                Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (g.over.isNotEmpty()) Pill("rematch", Lime, Ink) { mesh.gameRematch() }
                    Pill("quit", Ink, Lime) { mesh.gameQuit() }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- QUESTS + POINTS
@Composable
fun QuestsScreen(onBack: () -> Unit) {
    val mesh = LocalMesh.current
    val quests = listOf(
        "hi" to "say hi in a chat", "meet3" to "meet 3 people in range", "story" to "post a vanish story",
        "board" to "post on the local board", "game" to "play tic-tac-toe", "file" to "send a file with Mesh Drop"
    )
    val board = (listOf(Triple("you", mesh.points(), true)) + mesh.alive().map { Triple(it.name, it.points, false) }).sortedByDescending { it.second }
    Screen(Paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header("quests", "earn points, climb the squad") { Pill("done", Lime, Ink, onBack) }
            Column(Modifier.padding(24.dp).fillMaxWidth().chunky(Ink, 28.dp).padding(22.dp)) {
                T("${mesh.points()} pts", 48, FontWeight.Black, Lime)
                T("+2 per message, +1 per message you relay, +20 per quest", 13, color = Paper)
            }
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quests.forEach { (id, label) ->
                    val done = mesh.questDone(id)
                    Row(Modifier.fillMaxWidth().chunky(if (done) Lime else Paper, 22.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(if (done) "✓" else "○", 20, FontWeight.Black)
                        T(label, 16, FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        T("+20", 14, FontWeight.ExtraBold)
                    }
                }
            }
            T("leaderboard", 24, FontWeight.Black, modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 10.dp))
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                board.forEachIndexed { i, (n, p, me) ->
                    Row(Modifier.fillMaxWidth().chunky(if (me) Pink else Paper, 20.dp, shadow = false).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("#${i + 1}", 16, FontWeight.Black)
                        T(n, 16, FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        T("$p", 16, FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- BOARD + CONFESSIONS
@Composable
fun BoardScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    var text by remember { mutableStateOf("") }
    var anon by remember { mutableStateOf(false) }
    val posts = mesh.posts.filter { it.id !in mesh.hiddenPosts && !mesh.isBlocked(it.from) }
    Screen(Paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header("board", "notes and confessions in range") { Pill("done", Lime, Ink, onBack) }
            Column(Modifier.padding(24.dp).fillMaxWidth().chunky(Lime, 26.dp).padding(16.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(240) }, label = { Text("write something kind") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Toggle(anon) { anon = it }
                    T("confession (anonymous)", 14, FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Pill("post", Ink, Lime) { if (text.isNotBlank()) { mesh.postBoard(text, anon); text = "" } }
                }
            }
            T("Anonymous posts still use a hidden id, so you can report or block them. Be kind.", 12, modifier = Modifier.padding(horizontal = 24.dp))
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (posts.isEmpty()) T("Nothing posted yet.", 15)
                posts.forEach { p ->
                    Column(Modifier.fillMaxWidth().chunky(if (p.anon) Pink else Paper, 24.dp).padding(16.dp)) {
                        T("${p.name} · ${ago(p.ts)}", 13, FontWeight.ExtraBold)
                        T(p.text, 17, FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))
                        if (p.from != mesh.myId) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("report", Ink, Lime) {
                                mesh.hidePost(p.id)
                                ctx.sendMail("TalkMesh report", "Post id: ${p.id}\nAuthor id: ${p.from}\nText: ${p.text}")
                            }
                            Pill("block", Paper, Ink) { mesh.block(p.from, p.name) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- BLOCKED
@Composable
fun BlockedScreen(onBack: () -> Unit) {
    val mesh = LocalMesh.current
    val list = mesh.blockedList()
    Screen(Paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header("blocked", "you won't see these people") { Pill("done", Lime, Ink, onBack) }
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (list.isEmpty()) T("No blocked users.", 16)
                list.forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().chunky(Paper, 22.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name, Ink, 42)
                        T(name, 17, FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Pill("unblock", Lime, Ink) { mesh.unblock(id) }
                    }
                }
            }
        }
    }
}

@Composable
fun RoomDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var created by remember { mutableStateOf<Pair<String, String>?>(null) }
    val c0 = created
    if (c0 != null) {
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = { TextButton(onClick = { ctx.shareText("Join my TalkMesh room \"${c0.first}\" with code ${c0.second}"); onClose() }) { Text("share code") } },
            dismissButton = { TextButton(onClick = onClose) { Text("close") } },
            title = { Text("room created") },
            text = { Text("Code: ${c0.second}\n\nOnly people with this code can read the room. Share it only with people you trust.") }
        )
    } else {
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = {
                TextButton(onClick = {
                    when {
                        name.isBlank() -> ctx.toast("give the room a name")
                        code.isBlank() -> created = name.trim() to mesh.createRoom(name)
                        mesh.joinRoom(name, code) -> onClose()
                        else -> ctx.toast("that code is too short")
                    }
                }) { Text(if (code.isBlank()) "create" else "join") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("cancel") } },
            title = { Text("private room") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("room name") }, singleLine = true)
                    OutlinedTextField(value = code, onValueChange = { code = it.take(16) }, label = { Text("code (empty = create new)") }, singleLine = true)
                }
            }
        )
    }
}
