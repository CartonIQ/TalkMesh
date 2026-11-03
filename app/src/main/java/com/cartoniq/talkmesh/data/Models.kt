package com.cartoniq.talkmesh.data

import org.json.JSONArray
import org.json.JSONObject

data class Msg(val id: String, val from: String, val name: String, val text: String, val ts: Long, val mine: Boolean, val uri: String? = null)
data class PeerInfo(val id: String, val name: String, val vibe: String, val hops: Int, val points: Int, val lastSeen: Long, val key: String = "")
data class Room(val name: String, val code: String, val id: String)
data class Story(val id: String, val from: String, val name: String, val text: String, val color: Int, val ts: Long)
data class Post(val id: String, val from: String, val name: String, val text: String, val anon: Boolean, val ts: Long)
data class SosAlert(val id: String, val name: String, val lat: Double?, val lon: Double?, val ts: Long)
data class Incoming(val name: String, val kind: String, val uri: String, val fromName: String, val ts: Long)
data class Ttt(val oppId: String, val oppName: String, val cells: String, val mark: Char, val turn: Char, val over: String)

fun Msg.toJson(): JSONObject = JSONObject().put("id", id).put("f", from).put("n", name).put("x", text)
    .put("ts", ts).put("m", mine).put("u", uri ?: "")

fun JSONObject.toMsg() = Msg(getString("id"), getString("f"), getString("n"), getString("x"), getLong("ts"), getBoolean("m"), optString("u").ifEmpty { null })

fun threadsToJson(m: Map<String, List<Msg>>): JSONObject {
    val o = JSONObject()
    m.forEach { (k, v) -> o.put(k, JSONArray().also { a -> v.takeLast(300).forEach { a.put(it.toJson()) } }) }
    return o
}

fun threadsFromJson(o: JSONObject): Map<String, List<Msg>> =
    o.keys().asSequence().associateWith { k ->
        val a = o.getJSONArray(k)
        List(a.length()) { i -> a.getJSONObject(i).toMsg() }
    }
