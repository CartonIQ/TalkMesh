package com.cartoniq.talkmesh.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.KeyPair
import javax.crypto.spec.SecretKeySpec
import java.util.UUID

/**
 * Real phone-to-phone mesh on top of Google Nearby Connections (P2P_CLUSTER).
 * Every packet is flooded to neighbours with a hop limit (TTL) and de-duplicated by id.
 * Broadcast packets (room chat, stories, board) are also "carried": replayed to phones that connect later.
 * NOTE: direct messages are relayed through other phones in readable form (links are encrypted per hop,
 * not end-to-end). Tell your users.
 */
class MeshEngine(private val app: Context, private val prefs: Prefs) {
    companion object {
        const val SERVICE = "com.cartoniq.talkmesh"
        const val TTL0 = 4
        const val DAY = 24L * 60 * 60 * 1000
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(app)
    private val store = Store(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var disco: Job? = null

    val myId: String = prefs.userId.value.ifEmpty { UUID.randomUUID().toString().take(6).also { prefs.userId.set(it) } }

    var appVisible = true
    private val keyPair: KeyPair = loadKeys()
    private val rooms = mutableStateListOf<Room>()
    private val roomKeys = HashMap<String, SecretKeySpec>()
    private val pendingKeys = HashMap<String, String>()
    val keyChanged = mutableStateListOf<String>()

    var running by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var sendProgress by mutableStateOf(0f); private set
    var game by mutableStateOf<Ttt?>(null); private set
    var discoUri by mutableStateOf<String?>(null); private set
    var discoName by mutableStateOf(""); private set
    var discoHost by mutableStateOf(""); private set
    var discoPlaying by mutableStateOf(false); private set
    var vibe by mutableStateOf(prefs.vibe.value); private set

    private val endpoints = mutableStateMapOf<String, String>()
    val peers = mutableStateMapOf<String, PeerInfo>()
    val threads = mutableStateMapOf<String, SnapshotStateList<Msg>>()
    val stories = mutableStateListOf<Story>()
    val posts = mutableStateListOf<Post>()
    val hiddenPosts = mutableStateListOf<String>()
    val sos = mutableStateListOf<SosAlert>()
    val inbox = mutableStateListOf<Incoming>()
    val carried: Int get() = recent.size

    private val seen = LinkedHashSet<String>()
    private val recent = ArrayDeque<String>()
    private val pendingNames = HashMap<String, String>()
    private val fileMeta = HashMap<Long, JSONObject>()
    private val filePayloads = HashMap<Long, Payload>()
    private val outgoing = HashSet<Long>()
    private var player: MediaPlayer? = null

    init {
        store.load().forEach { (k, v) -> threads[k] = mutableStateListOf<Msg>().also { it.addAll(v) } }
        loadRooms()
    }

    private fun loadKeys(): KeyPair = try {
        if (prefs.privKey.value.isEmpty()) error("no key yet")
        Crypto.restore(prefs.pubKey.value, prefs.privKey.value)
    } catch (e: Exception) {
        Crypto.newKeyPair().also { prefs.pubKey.set(Crypto.pubB64(it)); prefs.privKey.set(Crypto.privB64(it)) }
    }

    private fun now() = System.currentTimeMillis()
    fun displayName(): String = if (prefs.ghost.value) "ghost" else prefs.name.value.ifBlank { "you" }
    private fun localEp() = "${displayName()}#$myId"
    private fun idOf(ep: String) = ep.substringAfterLast('#')
    private fun nameOf(ep: String) = ep.substringBeforeLast('#')

    // ------------------------------------------------------------ lifecycle
    fun start() {
        if (running) return
        running = true
        error = null
        client.startAdvertising(
            localEp(), SERVICE, connCb, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener { e -> fail(e, ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) }
        disco = scope.launch {
            while (isActive) {
                client.startDiscovery(SERVICE, discCb, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
                    .addOnFailureListener { e -> fail(e, ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) }
                delay(if (prefs.battery.value) 25_000L else 120_000L)
                client.stopDiscovery()
                if (prefs.battery.value) delay(60_000L)
            }
        }
        loop = scope.launch {
            while (isActive) {
                sendPresence()
                stories.removeAll { now() - it.ts > DAY }
                delay(20_000L)
            }
        }
    }

    fun stop() {
        running = false
        loop?.cancel(); disco?.cancel()
        client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints()
        endpoints.clear()
        player?.release(); player = null
    }

    fun destroy() { stop(); scope.cancel() }

    /** Restart after a setting that changes our public name (ghost mode, rename). */
    fun restart() { if (running) { stop(); start() } }

    private fun fail(e: Exception, ignore: Int) {
        if ((e as? ApiException)?.statusCode == ignore) return
        error = "turn on Bluetooth, Wi-Fi and location, then try again"
    }

    // ------------------------------------------------------------ connections
    private val discCb = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            // only the side with the smaller id initiates, so both phones don't connect twice
            if (myId < idOf(info.endpointName) && !endpoints.containsKey(id)) {
                client.requestConnection(localEp(), id, connCb)
            }
        }
        override fun onEndpointLost(id: String) {}
    }

    private val connCb = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            pendingNames[id] = info.endpointName
            client.acceptConnection(id, payloadCb)
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (!result.status.isSuccess) return
            val ep = pendingNames[id] ?: return
            endpoints[id] = ep
            peers[idOf(ep)] = PeerInfo(idOf(ep), nameOf(ep), "", 1, 0, now(), pinOf(idOf(ep)))
            sendPresence()
            recent.toList().forEach { sendRaw(it, only = listOf(id)) }   // carry mode
        }
        override fun onDisconnected(id: String) {
            endpoints.remove(id)?.let { peers.remove(idOf(it)) }
        }
    }

    private val payloadCb = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, p: Payload) {
            when (p.type) {
                Payload.Type.BYTES -> p.asBytes()?.let { handle(String(it, Charsets.UTF_8), id) }
                Payload.Type.FILE -> filePayloads[p.id] = p
                else -> {}
            }
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {
            if (u.payloadId in outgoing && u.totalBytes > 0) sendProgress = u.bytesTransferred.toFloat() / u.totalBytes
            if (u.status == PayloadTransferUpdate.Status.SUCCESS) {
                if (u.payloadId in outgoing) { outgoing.remove(u.payloadId); sendProgress = 1f } else finishFile(u.payloadId)
            }
        }
    }

    // ------------------------------------------------------------ packets
    private fun sendRaw(raw: String, only: List<String>? = null, except: String? = null) {
        val targets = (only ?: endpoints.keys.toList()).filter { it != except && endpoints.containsKey(it) }
        if (targets.isEmpty()) return
        client.sendPayload(targets, Payload.fromBytes(raw.toByteArray(Charsets.UTF_8)))
    }

    private fun broadcast(type: String, to: String, body: String, ttl: Int = TTL0): String {
        val pid = UUID.randomUUID().toString().take(12)
        val raw = JSONObject().put("i", pid).put("t", type).put("f", myId).put("n", displayName())
            .put("to", to).put("ttl", ttl).put("b", body).put("ts", now()).toString()
        markSeen(pid)
        if (to == "*" && (type == "c" || type == "s" || type == "b" || type == "m")) {
            recent.addLast(raw); while (recent.size > 150) recent.removeFirst()
        }
        sendRaw(raw)
        return pid
    }

    private fun markSeen(id: String): Boolean {
        if (!seen.add(id)) return false
        if (seen.size > 1500) { val iter = seen.iterator(); repeat(300) { if (iter.hasNext()) { iter.next(); iter.remove() } } }
        return true
    }

    private fun handle(raw: String, fromEp: String) {
        val j = try { JSONObject(raw) } catch (e: Exception) { return }
        val pid = j.optString("i"); if (pid.isEmpty() || !markSeen(pid)) return
        val from = j.optString("f"); if (from.isEmpty() || from == myId || isBlocked(from)) return
        val to = j.optString("to", "*")
        val ttl = j.optInt("ttl", 1)
        val type = j.optString("t")
        if (to == "*" || to == myId) deliver(type, pid, from, j.optString("n"), j.optString("b"), j.optLong("ts", now()), TTL0 - ttl + 1, to == myId)
        if (ttl > 1 && to != myId && type != "fm") {
            j.put("ttl", ttl - 1)
            val out = j.toString()
            if (to == "*" && (type == "c" || type == "s" || type == "b" || type == "m")) { recent.addLast(out); while (recent.size > 150) recent.removeFirst() }
            sendRaw(out, except = fromEp)
            prefs.relayed.set(prefs.relayed.value + 1)
        }
    }

    private fun deliver(type: String, pid: String, from: String, name: String, body: String, ts: Long, hops: Int, direct: Boolean) {
        val b = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        when (type) {
            "h" -> {
                val newKey = b.optString("k")
                val pinned = pinOf(from)
                if (newKey.isNotEmpty() && pinned.isEmpty()) setPin(from, newKey)
                else if (newKey.isNotEmpty() && pinned != newKey) { pendingKeys[from] = newKey; if (from !in keyChanged) keyChanged.add(from) }
                val key = pinned.ifEmpty { newKey }
                peers[from] = PeerInfo(from, name, b.optString("v"), if (peers[from]?.hops == 1) 1 else hops, b.optInt("p"), now(), key)
                if (alive().size >= 3) quest("meet3")
            }
            "c" -> {
                if (direct) {
                    val k = peers[from]?.key.orEmpty()
                    val sk = if (k.isEmpty()) null else try { Crypto.shared(keyPair, k) } catch (e: Exception) { null }
                    val plain = sk?.let { Crypto.decrypt(it, b.optString("e")) }
                    add(from, Msg(pid, from, name, plain ?: "[encrypted message, could not decrypt]", ts, false))
                    buzz(name, "new message")
                } else {
                    add("nearby", Msg(pid, from, name, b.optString("x"), ts, false))
                    buzz("Nearby room", "$name: ${b.optString("x")}".take(80))
                }
            }
            "m" -> {
                val rid = b.optString("r")
                val rk = roomKeys[rid] ?: return
                val inner = Crypto.decrypt(rk, b.optString("e")) ?: return
                val j = try { JSONObject(inner) } catch (e: Exception) { return }
                add("room:$rid", Msg(pid, from, j.optString("n", name), j.optString("x"), ts, false))
                buzz(roomName("room:$rid") ?: "private room", "new message")
            }
            "s" -> { stories.add(Story(pid, from, name, b.optString("x"), b.optInt("c"), ts)); stories.removeAll { now() - it.ts > DAY } }
            "b" -> posts.add(0, Post(pid, from, if (b.optBoolean("a")) "anon" else name, b.optString("x"), b.optBoolean("a"), ts))
            "sos" -> { sos.add(SosAlert(pid, name, if (b.has("la")) b.optDouble("la") else null, if (b.has("lo")) b.optDouble("lo") else null, ts)); buzz("SOS from $name", "open TalkMesh to see the location") }
            "g" -> onGame(from, name, b)
            "dp" -> onDisco(b)
            "fm" -> fileMeta[b.optLong("pid")] = b.put("from", name)
        }
    }

    private fun buzz(title: String = "TalkMesh", text: String = "new activity") {
        if (!prefs.notifications.value) return
        if (!appVisible) { Notifier.post(app, title, text); return }
        try {
            (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    private fun sendPresence() {
        val body = JSONObject().put("v", vibe).put("p", points()).put("k", Crypto.pubB64(keyPair)).toString()
        broadcast("h", "*", body)
    }

    // ------------------------------------------------------------ people
    fun alive(): List<PeerInfo> = peers.values.filter { now() - it.lastSeen < 70_000 }.sortedBy { it.hops }
    fun directPeers(): List<PeerInfo> = alive().filter { p -> endpoints.values.any { idOf(it) == p.id } }
    private fun endpointOf(peerId: String) = endpoints.entries.firstOrNull { idOf(it.value) == peerId }?.key

    fun updateVibe(v: String) { vibe = v; prefs.vibe.set(v); sendPresence() }

    // blocking
    fun isBlocked(id: String) = prefs.blocked.value.split(';').any { it.startsWith("$id|") }
    fun blockedList(): List<Pair<String, String>> =
        prefs.blocked.value.split(';').filter { it.contains('|') }.map { it.substringBefore('|') to it.substringAfter('|') }
    fun block(id: String, name: String) {
        if (!isBlocked(id)) prefs.blocked.set((blockedList().map { "${it.first}|${it.second}" } + "$id|${name.replace(';', ' ').replace('|', ' ')}").joinToString(";"))
        threads.remove(id); peers.remove(id)
    }
    fun unblock(id: String) = prefs.blocked.set(blockedList().filter { it.first != id }.joinToString(";") { "${it.first}|${it.second}" })

    // ------------------------------------------------------------ chat
    private fun add(key: String, m: Msg) {
        val l = threads.getOrPut(key) { mutableStateListOf() }
        l.add(m)
        persist()
    }

    private fun persist() {
        val snap = threads.mapValues { it.value.toList() }
        scope.launch(Dispatchers.IO) { store.save(snap) }
    }

    fun thread(key: String): List<Msg> = threads[key] ?: emptyList()

    /** Returns false if a direct message cannot be encrypted yet (their key has not arrived). */
    fun sendChat(to: String, text: String): Boolean {
        val t = text.trim(); if (t.isEmpty()) return true
        val body = if (to == "*") {
            JSONObject().put("x", t).toString()
        } else {
            if (to in keyChanged) return false
            val k = peers[to]?.key.orEmpty()
            if (k.isEmpty()) return false
            val sk = try { Crypto.shared(keyPair, k) } catch (e: Exception) { return false }
            JSONObject().put("e", Crypto.encrypt(sk, t)).toString()
        }
        val pid = broadcast("c", to, body)
        add(if (to == "*") "nearby" else to, Msg(pid, myId, displayName(), t, now(), true))
        prefs.sent.set(prefs.sent.value + 1); quest("hi")
        return true
    }

    // ------------------------------------------------------------ key pinning (trust on first use)
    private fun pinOf(id: String): String =
        prefs.pins.value.split(';').firstOrNull { it.startsWith("$id|") }?.substringAfter('|').orEmpty()

    private fun setPin(id: String, key: String) {
        val rest = prefs.pins.value.split(';').filter { it.contains('|') && !it.startsWith("$id|") }
        prefs.pins.set((rest + "$id|$key").joinToString(";"))
    }

    /** The user accepts that this person's key legitimately changed (for example a reinstall). */
    fun trustNewKey(id: String) {
        val k = pendingKeys.remove(id) ?: return
        setPin(id, k)
        peers[id]?.let { peers[id] = it.copy(key = k) }
        keyChanged.remove(id)
    }

    // ------------------------------------------------------------ private rooms (encrypted)
    private fun loadRooms() {
        rooms.clear(); roomKeys.clear()
        prefs.rooms.value.split(';').filter { it.contains('|') }.forEach {
            val k = Crypto.roomKey(it.substringAfter('|'))
            val id = Crypto.roomId(k)
            roomKeys[id] = k
            rooms.add(Room(it.substringBefore('|'), it.substringAfter('|'), id))
        }
    }

    private fun saveRooms() = prefs.rooms.set(rooms.joinToString(";") { "${it.name.replace(';', ' ').replace('|', ' ')}|${it.code}" })

    fun roomList(): List<Room> = rooms
    fun roomName(key: String): String? = rooms.firstOrNull { "room:${it.id}" == key }?.name
    fun roomCode(key: String): String? = rooms.firstOrNull { "room:${it.id}" == key }?.code

    fun createRoom(name: String): String { val code = Crypto.newCode(); joinRoom(name, code); return code }

    fun joinRoom(name: String, code: String): Boolean {
        val c = code.trim().uppercase()
        if (c.length < 8) return false
        val k = Crypto.roomKey(c)
        val id = Crypto.roomId(k)
        if (rooms.any { it.id == id }) return true
        roomKeys[id] = k
        rooms.add(Room(name.trim().ifEmpty { "room" }.take(24), c, id))
        saveRooms()
        return true
    }

    fun leaveRoom(id: String) {
        rooms.removeAll { it.id == id }; roomKeys.remove(id); threads.remove("room:$id")
        saveRooms(); persist()
    }

    fun sendRoom(id: String, text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        val k = roomKeys[id] ?: return
        val inner = JSONObject().put("x", t).put("n", displayName()).toString()
        val pid = broadcast("m", "*", JSONObject().put("r", id).put("e", Crypto.encrypt(k, inner)).toString())
        add("room:$id", Msg(pid, myId, displayName(), t, now(), true))
        prefs.sent.set(prefs.sent.value + 1); quest("hi")
    }

    // ------------------------------------------------------------ stories, board, sos
    fun postStory(text: String, color: Int) {
        val t = text.trim(); if (t.isEmpty()) return
        val pid = broadcast("s", "*", JSONObject().put("x", t).put("c", color).toString())
        stories.add(Story(pid, myId, displayName(), t, color, now()))
        prefs.sent.set(prefs.sent.value + 2); quest("story")
    }

    fun postBoard(text: String, anon: Boolean) {
        val t = text.trim(); if (t.isEmpty()) return
        val pid = broadcast("b", "*", JSONObject().put("x", t).put("a", anon).toString())
        posts.add(0, Post(pid, myId, if (anon) "anon" else displayName(), t, anon, now()))
        quest("board")
    }

    fun hidePost(id: String) { hiddenPosts.add(id) }

    fun sendSos(lat: Double?, lon: Double?) {
        val b = JSONObject()
        if (lat != null && lon != null) b.put("la", lat).put("lo", lon)
        broadcast("sos", "*", b.toString())
    }

    // ------------------------------------------------------------ quests and points
    fun points(): Int = prefs.sent.value * 2 + prefs.relayed.value + prefs.bonus.value
    fun questDone(id: String) = prefs.quests.value.split(',').contains(id)
    fun quest(id: String) {
        val done = prefs.quests.value.split(',').filter { it.isNotEmpty() }
        if (id in done) return
        prefs.quests.set((done + id).joinToString(","))
        prefs.bonus.set(prefs.bonus.value + 20)
    }

    // ------------------------------------------------------------ tic-tac-toe
    private fun gp(op: String, to: String, c: Int = -1) =
        broadcast("g", to, JSONObject().put("op", op).put("c", c).toString(), ttl = TTL0)

    fun gameInvite(p: PeerInfo) {
        game = Ttt(p.id, p.name, "---------", 'X', 'X', "")
        gp("inv", p.id); quest("game")
    }
    fun gameMove(i: Int) {
        val g = game ?: return
        if (g.over.isNotEmpty() || g.turn != g.mark || g.cells[i] != '-') return
        gp("mv", g.oppId, i)
        game = applyMove(g, i, g.mark)
    }
    fun gameRematch() { val g = game ?: return; game = Ttt(g.oppId, g.oppName, "---------", g.mark, 'X', ""); gp("rs", g.oppId) }
    fun gameQuit() { val g = game ?: return; gp("qt", g.oppId); game = null }

    private fun applyMove(g: Ttt, i: Int, m: Char): Ttt {
        val cells = g.cells.substring(0, i) + m + g.cells.substring(i + 1)
        val lines = listOf(intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8), intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8), intArrayOf(0, 4, 8), intArrayOf(2, 4, 6))
        val won = lines.any { l -> cells[l[0]] == m && cells[l[1]] == m && cells[l[2]] == m }
        val over = if (won) m.toString() else if (cells.none { it == '-' }) "draw" else ""
        return g.copy(cells = cells, turn = if (m == 'X') 'O' else 'X', over = over)
    }

    private fun onGame(from: String, name: String, b: JSONObject) {
        when (b.optString("op")) {
            "inv" -> if (game == null) { game = Ttt(from, name, "---------", 'O', 'X', ""); quest("game") }
            "mv" -> game?.let { g -> val i = b.optInt("c"); if (g.oppId == from && i in 0..8 && g.cells[i] == '-' && g.over.isEmpty()) game = applyMove(g, i, if (g.mark == 'X') 'O' else 'X') }
            "rs" -> game?.let { g -> if (g.oppId == from) game = Ttt(g.oppId, g.oppName, "---------", g.mark, 'X', "") }
            "qt" -> if (game?.oppId == from) game = null
        }
    }

    // ------------------------------------------------------------ files, voice, silent disco
    /** Sends a file to direct neighbours only. Returns false if nobody can receive it. */
    fun sendFile(targets: List<String>, open: () -> ParcelFileDescriptor?, name: String, kind: String): Boolean {
        val eps = targets.mapNotNull { endpointOf(it) }
        if (eps.isEmpty()) return false
        sendProgress = 0f
        eps.forEach { ep ->
            val pfd = open() ?: return@forEach
            val payload = Payload.fromFile(pfd)
            outgoing.add(payload.id)
            val meta = JSONObject().put("i", UUID.randomUUID().toString().take(12)).put("t", "fm").put("f", myId).put("n", displayName())
                .put("to", "*").put("ttl", 1).put("ts", now())
                .put("b", JSONObject().put("pid", payload.id).put("name", name).put("kind", kind).toString())
            sendRaw(meta.toString(), only = listOf(ep))
            client.sendPayload(ep, payload)
        }
        prefs.sent.set(prefs.sent.value + 3)
        if (kind == "file") quest("file")
        return true
    }

    private fun finishFile(pid: Long) {
        val p = filePayloads.remove(pid) ?: return
        val meta = fileMeta.remove(pid)
        val f = p.asFile()
        @Suppress("DEPRECATION")
        val uri = (f?.asUri() ?: f?.asJavaFile()?.let { Uri.fromFile(it) })?.toString() ?: return
        val name = meta?.optString("name").takeUnless { it.isNullOrEmpty() } ?: "file"
        val who = meta?.optString("from").orEmpty().ifEmpty { "someone" }
        when (meta?.optString("kind")) {
            "voice" -> {
                val fromId = peers.values.firstOrNull { it.name == who }?.id ?: "nearby"
                add(fromId, Msg("v$pid", fromId, who, "voice note", now(), false, uri)); buzz()
            }
            "disco" -> { discoUri = uri; discoName = name; discoHost = who; discoPlaying = false; player?.release(); player = null }
            else -> { inbox.add(0, Incoming(name, "file", uri, who, now())); buzz() }
        }
    }

    fun addVoiceLocal(peerId: String, uri: String) = add(peerId, Msg("v${now()}", myId, displayName(), "voice note", now(), true, uri))

    fun discoShare(open: () -> ParcelFileDescriptor?, uri: String, name: String): Boolean {
        discoUri = uri; discoName = name; discoHost = displayName(); discoPlaying = false
        player?.release(); player = null
        return sendFile(directPeers().map { it.id }, open, name, "disco")
    }

    private fun ensurePlayer(): MediaPlayer? {
        val u = discoUri ?: return null
        if (player == null) player = try { MediaPlayer.create(app, Uri.parse(u)) } catch (e: Exception) { null }
        return player
    }

    fun discoPlay(play: Boolean) {
        applyDisco(play)
        broadcast("dp", "*", JSONObject().put("op", if (play) "play" else "pause").toString(), ttl = 2)
    }

    private fun applyDisco(play: Boolean) {
        val mp = ensurePlayer() ?: return
        try { if (play) mp.start() else mp.pause() } catch (e: Exception) { }
        discoPlaying = play
    }

    private fun onDisco(b: JSONObject) { applyDisco(b.optString("op") == "play") }

    // ------------------------------------------------------------ backup and delete
    fun exportJson(): String = JSONObject().put("v", 1).put("name", prefs.name.value).put("vibe", prefs.vibe.value)
        .put("theme", prefs.theme.value).put("textSize", prefs.textSize.value).put("blocked", prefs.blocked.value)
        .put("quests", prefs.quests.value).put("bonus", prefs.bonus.value).put("rooms", prefs.rooms.value)
        .put("threads", threadsToJson(threads.mapValues { it.value.toList() })).toString()

    fun importJson(s: String): Boolean = try {
        val o = JSONObject(s)
        prefs.name.set(o.optString("name", prefs.name.value))
        prefs.vibe.set(o.optString("vibe", ""))
        prefs.theme.set(o.optString("theme", "riso"))
        prefs.textSize.set(o.optString("textSize", "normal"))
        prefs.blocked.set(o.optString("blocked", ""))
        prefs.quests.set(o.optString("quests", ""))
        prefs.bonus.set(o.optInt("bonus", 0))
        prefs.rooms.set(o.optString("rooms", ""))
        loadRooms()
        threads.clear()
        threadsFromJson(o.getJSONObject("threads")).forEach { (k, v) -> threads[k] = mutableStateListOf<Msg>().also { it.addAll(v) } }
        persist()
        true
    } catch (e: Exception) { false }

    /** Delete My Data: everything stored by this app on this phone. */
    fun wipeData() {
        threads.clear(); stories.clear(); posts.clear(); inbox.clear(); sos.clear(); recent.clear(); hiddenPosts.clear()
        store.clear(); loadRooms(); keyChanged.clear(); pendingKeys.clear()
    }
}
