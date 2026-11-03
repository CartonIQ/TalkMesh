package com.cartoniq.talkmesh.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Saves chat history on this phone only (filesDir/threads.json). */
class Store(ctx: Context) {
    private val f = File(ctx.filesDir, "threads.json")

    @Synchronized
    fun load(): Map<String, List<Msg>> = try { threadsFromJson(JSONObject(f.readText())) } catch (e: Exception) { emptyMap() }

    @Synchronized
    fun save(m: Map<String, List<Msg>>) { try { f.writeText(threadsToJson(m).toString()) } catch (e: Exception) { } }

    @Synchronized
    fun clear() { f.delete() }
}
