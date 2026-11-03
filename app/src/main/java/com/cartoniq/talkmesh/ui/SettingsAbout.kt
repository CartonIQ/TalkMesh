package com.cartoniq.talkmesh.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartoniq.talkmesh.data.Prefs

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(50.dp).height(28.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) Ink else Color.Transparent)
            .border(2.dp, Ink, RoundedCornerShape(14.dp))
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) { Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) Lime else Ink)) }
}

@Composable
private fun SRow(label: String, onClick: (() -> Unit)? = null, right: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .drawBehind {
                drawLine(Ink.copy(alpha = .12f), Offset(24.dp.toPx(), size.height), Offset(size.width - 24.dp.toPx(), size.height), 2.dp.toPx())
            }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        T(label, 17, FontWeight.Bold)
        right()
    }
}

@Composable
fun SettingsScreen(prefs: Prefs, onClose: () -> Unit, onAbout: () -> Unit, onBlocked: () -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    var edit by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var vibe by remember { mutableStateOf(false) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(mesh.exportJson().toByteArray()) }
                ctx.toast("backup saved")
            } catch (e: Exception) { ctx.toast("could not save backup") }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (e: Exception) { null }
            if (text != null && mesh.importJson(text)) { applyTheme(prefs.theme.value); mesh.restart(); ctx.toast("backup restored") }
            else ctx.toast("that file is not a TalkMesh backup")
        }
    }

    Screen(Paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header("settings", "make it yours") { Pill("done", Lime, Ink, onClose) }
            Row(
                Modifier.padding(24.dp).fillMaxWidth().chunky(Pink, 28.dp).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(prefs.name.value, Ink)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    T(prefs.name.value, 18, FontWeight.ExtraBold)
                    T("shown to people nearby", 13)
                }
                Pill("edit", Ink, Lime) { draft = prefs.name.value; edit = true }
            }
            SRow("theme", {
                val next = ThemePacks[(ThemePacks.indexOf(prefs.theme.value) + 1) % ThemePacks.size]
                prefs.theme.set(next); applyTheme(next)
            }) { T("${prefs.theme.value} ›", 15) }
            SRow("text size", {
                val l = listOf("small", "normal", "large")
                prefs.textSize.set(l[(l.indexOf(prefs.textSize.value) + 1) % l.size])
            }) { T("${prefs.textSize.value} ›", 15) }
            SRow("vibe", { vibe = true }) { T(if (mesh.vibe.isEmpty()) "none ›" else "${mesh.vibe} ›", 15) }
            SRow("vibrate on new message") { Toggle(prefs.notifications.value) { prefs.notifications.set(it) } }
            SRow("keep mesh on in background") {
                Toggle(prefs.background.value) { prefs.background.set(it); if (it) ctx.startMeshService() else ctx.stopMeshService() }
            }
            SRow("ghost mode (hide my name)") { Toggle(prefs.ghost.value) { prefs.ghost.set(it); mesh.restart() } }
            SRow("blocked users", onBlocked) { T("${mesh.blockedList().size} ›", 15) }
            SRow("battery saver") { Toggle(prefs.battery.value) { prefs.battery.set(it) } }
            SRow("storage", { ctx.cacheDir.deleteRecursively(); ctx.toast("cache cleared") }) { T("clear cache ›", 15) }
            SRow("backup chats & settings", { backup.launch("talkmesh-backup.json") }) { T("›", 15) }
            SRow("restore backup", { restore.launch(arrayOf("application/json")) }) { T("›", 15) }
            SRow("about & help", onAbout) { T("›", 15) }
        }
    }
    if (vibe) VibeDialog { vibe = false }
    if (edit) AlertDialog(
        onDismissRequest = { edit = false },
        confirmButton = {
            TextButton(onClick = {
                if (draft.isNotBlank()) { prefs.name.set(draft.trim()); mesh.restart() }
                edit = false
            }) { Text("save") }
        },
        dismissButton = { TextButton(onClick = { edit = false }) { Text("cancel") } },
        title = { Text("your name") },
        text = { OutlinedTextField(value = draft, onValueChange = { draft = it.take(20).replace('#', ' ') }, singleLine = true) }
    )
}

@Composable
fun AboutScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mesh = LocalMesh.current
    val ver = remember { ctx.versionName() }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val rows: List<Pair<String, () -> Unit>> = listOf(
        "About this app" to {
            info = "About TalkMesh" to "TalkMesh v$ver\nChat with nearby people with no internet.\nBuilt by CartonIQ, a solo studio working from a phone.\n$SITE_URL"
        },
        "Privacy Policy" to { ctx.openUrl(PRIVACY_URL) },
        "Terms & Conditions" to {
            info = "Terms & Conditions" to "TalkMesh is provided as is, without warranty. Do not rely on it as your only emergency system. " +
                "You are responsible for the content you send. Be kind to the people around you.\n\n(Replace with your final legal text before publishing.)"
        },
        "Licenses & Copyright" to {
            info = "Licenses & Copyright" to "© CartonIQ. All rights reserved.\n\nOpen source used: Kotlin, AndroidX and Jetpack Compose (Apache License 2.0)."
        },
        "Data collection information" to {
            info = "Data collection" to "TalkMesh has no accounts and no servers. Your name, chats and settings stay on this device. Messages you send are shared with phones in range and relayed through them. " +
                "If ads are enabled, Google AdMob may collect device identifiers as described in the Privacy Policy."
        },
        "Delete My Data" to { confirmDelete = true },
        "Contact Support" to { ctx.sendMail("TalkMesh support") },
        "Rate this app" to { ctx.openUrl(ctx.storeUrl()) },
        "Share this app" to { ctx.shareApp() },
        "Check for Updates" to { ctx.toast("You are on v$ver"); ctx.openUrl(ctx.storeUrl()) },
        "Report a Bug" to { ctx.sendMail("TalkMesh bug report", "Describe the problem:\n\n\n---\n${ctx.deviceInfo()}") },
        "Acknowledgements" to {
            info = "Acknowledgements" to "Thank you to every tester and friend who tried TalkMesh early, and to the open source community."
        },
    )

    Screen(Paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    T("about", 52, FontWeight.Black, spacing = -3f, lineHeight = 56)
                    T("talkmesh v$ver · by cartoniq", 14)
                }
                Pill("done", Lime, Ink, onBack)
            }
            Row(Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sticker("offline-first", Lime, 0f)
                Sticker("no trackers", Pink, 0f)
            }
            rows.forEachIndexed { i, (label, action) ->
                SRow(label, action) {
                    T("›", 16, FontWeight.Bold)
                }
            }
            T("built on a phone, with grit · cartoniq", 12, FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
        }
    }
    info?.let { (t, b) ->
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton(onClick = { info = null }) { Text("ok") } },
            title = { Text(t) }, text = { Text(b) }
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        confirmButton = {
            TextButton(onClick = {
                prefs.wipe(); mesh.wipeData(); applyTheme("riso"); ctx.cacheDir.deleteRecursively(); confirmDelete = false; ctx.toast("your data was deleted")
            }) { Text("delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("cancel") } },
        title = { Text("Delete my data?") },
        text = { Text("This removes your name, settings, chats and cached files from this phone. Copies already sent to other phones cannot be deleted from here.") }
    )
}
