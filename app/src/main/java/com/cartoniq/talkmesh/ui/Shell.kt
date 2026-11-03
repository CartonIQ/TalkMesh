package com.cartoniq.talkmesh.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartoniq.talkmesh.data.MeshEngine
import com.cartoniq.talkmesh.data.MeshHolder
import com.cartoniq.talkmesh.data.Prefs

enum class Stage { SPLASH, ONBOARD, MAIN }
enum class Tab { CHATS, RADAR, SAFETY }
enum class Ov { FUN, STORIES, DROP, DISCO, SETTINGS, ABOUT, CHAT, GAMES, QUESTS, BOARD, BLOCKED }

val LocalMesh = staticCompositionLocalOf<MeshEngine> { error("MeshEngine not provided") }

@Composable
fun Root() {
    val ctx = LocalContext.current
    val prefs = remember { MeshHolder.prefs(ctx) }
    val mesh = remember { MeshHolder.engine(ctx) }
    val scale = when (prefs.textSize.value) { "small" -> .9f; "large" -> 1.15f; else -> 1f }
    TalkMeshTheme(scale) {
        CompositionLocalProvider(LocalMesh provides mesh) {
            var stage by rememberSaveable { mutableStateOf(Stage.SPLASH) }
            when (stage) {
                Stage.SPLASH -> SplashScreen { stage = if (prefs.onboarded.value) Stage.MAIN else Stage.ONBOARD }
                Stage.ONBOARD -> OnboardingScreen { prefs.onboarded.set(true); stage = Stage.MAIN }
                Stage.MAIN -> MainShell(prefs)
            }
        }
    }
}

@Composable
fun MainShell(prefs: Prefs) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    var tab by rememberSaveable { mutableStateOf(Tab.CHATS) }
    var ov by rememberSaveable { mutableStateOf<Ov?>(null) }
    var chatWith by rememberSaveable { mutableStateOf("nearby") }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotif = {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !ctx.has(android.Manifest.permission.POST_NOTIFICATIONS))
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val begin = {
        mesh.start()
        if (prefs.background.value) ctx.startMeshService()
        askNotif()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ctx.hasMeshPermissions()) begin() else ctx.toast("TalkMesh needs these permissions to find nearby phones")
    }
    val enable = { if (ctx.hasMeshPermissions()) begin() else permLauncher.launch(meshPermissions()) }
    LaunchedEffect(Unit) { enable() }

    BackHandler(enabled = ov != null || tab != Tab.CHATS) {
        when {
            ov == Ov.ABOUT || ov == Ov.BLOCKED -> ov = Ov.SETTINGS
            ov in listOf(Ov.DROP, Ov.DISCO, Ov.GAMES, Ov.QUESTS, Ov.BOARD) -> ov = Ov.FUN
            ov != null -> ov = null
            else -> tab = Tab.CHATS
        }
    }
    val go: (Tab) -> Unit = { tab = it; ov = null }

    when (ov) {
        Ov.FUN -> FunScreen(go) { ov = it }
        Ov.STORIES -> StoriesScreen { ov = null }
        Ov.DROP -> DropScreen { ov = Ov.FUN }
        Ov.DISCO -> DiscoScreen { ov = Ov.FUN }
        Ov.GAMES -> GamesScreen { ov = Ov.FUN }
        Ov.QUESTS -> QuestsScreen { ov = Ov.FUN }
        Ov.BOARD -> BoardScreen { ov = Ov.FUN }
        Ov.SETTINGS -> SettingsScreen(prefs, { ov = null }, { ov = Ov.ABOUT }, { ov = Ov.BLOCKED })
        Ov.BLOCKED -> BlockedScreen { ov = Ov.SETTINGS }
        Ov.ABOUT -> AboutScreen(prefs) { ov = Ov.SETTINGS }
        Ov.CHAT -> ChatThread(chatWith) { ov = null }
        null -> when (tab) {
            Tab.CHATS -> ChatsScreen(go, { chatWith = it; ov = Ov.CHAT }, { ov = it }, enable)
            Tab.RADAR -> RadarScreen(go) { chatWith = it; ov = Ov.CHAT }
            Tab.SAFETY -> SafetyScreen(prefs, go)
        }
    }

    // Incoming SOS from a nearby phone
    var dismissed by remember { mutableStateOf(setOf<String>()) }
    val alert = mesh.sos.lastOrNull { System.currentTimeMillis() - it.ts < 10 * 60_000L && it.id !in dismissed }
    if (alert != null) AlertDialog(
        onDismissRequest = { dismissed = dismissed + alert.id },
        title = { Text("SOS from ${alert.name}") },
        text = { Text(if (alert.lat != null) "They sent their location. Open the map and go help, or call local emergency services." else "They need help but location was not available.") },
        confirmButton = {
            if (alert.lat != null) TextButton(onClick = {
                ctx.openUrl("geo:${alert.lat},${alert.lon}?q=${alert.lat},${alert.lon}"); dismissed = dismissed + alert.id
            }) { Text("open map") }
        },
        dismissButton = { TextButton(onClick = { dismissed = dismissed + alert.id }) { Text("dismiss") } }
    )
}

/** Floating dock with a raised lime Radar orb in the middle. */
@Composable
fun BoxScope.Dock(active: Tab?, inkDock: Boolean, screenBg: Color, onTab: (Tab) -> Unit) {
    val bg = if (inkDock) Ink else Paper
    val fg = if (inkDock) Paper else Ink
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 26.dp).height(98.dp)
    ) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(68.dp)
                .clip(RoundedCornerShape(34.dp)).background(bg).border(2.dp, Ink, RoundedCornerShape(34.dp))
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DockItem("chats", 0, active == Tab.CHATS, fg) { onTab(Tab.CHATS) }
            Spacer(Modifier.width(84.dp))
            DockItem("safety", 2, active == Tab.SAFETY, fg) { onTab(Tab.SAFETY) }
        }
        Column(
            Modifier.align(Alignment.TopCenter).size(84.dp).clip(CircleShape).background(Lime)
                .border(5.dp, screenBg, CircleShape).clickable { onTab(Tab.RADAR) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon24(1, Ink, 28)
            T("radar", 11, FontWeight.ExtraBold, Ink)
        }
    }
}

@Composable
private fun DockItem(label: String, kind: Int, on: Boolean, fg: Color, onClick: () -> Unit) {
    val c = fg.copy(alpha = if (on) 1f else .55f)
    Column(Modifier.width(64.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon24(kind, c)
        T(label, 11, FontWeight.Bold, c)
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Orange else Color.Transparent))
    }
}
