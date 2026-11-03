package com.cartoniq.talkmesh.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cartoniq.talkmesh.ui.applyTheme

/** One persisted value (Boolean, Int or String) that Compose can observe. */
class Pref<T : Any>(private val sp: SharedPreferences, private val key: String, private val default: T) {
    @Suppress("UNCHECKED_CAST")
    var value: T by mutableStateOf(
        when (default) {
            is Boolean -> sp.getBoolean(key, default) as T
            is Int -> sp.getInt(key, default) as T
            else -> (sp.getString(key, default as String) ?: default) as T
        }
    )
        private set

    fun set(v: T) {
        value = v
        sp.edit().apply {
            when (v) {
                is Boolean -> putBoolean(key, v)
                is Int -> putInt(key, v)
                else -> putString(key, v as String)
            }
        }.apply()
    }

    fun reset() = set(default)
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("talkmesh", Context.MODE_PRIVATE)
    val onboarded = Pref(sp, "onboarded", false)
    val userId = Pref(sp, "userId", "")
    val name = Pref(sp, "name", "you")
    val vibe = Pref(sp, "vibe", "")
    val notifications = Pref(sp, "notifications", true)
    val ghost = Pref(sp, "ghost", false)
    val battery = Pref(sp, "battery", true)
    val theme = Pref(sp, "theme", "riso")
    val textSize = Pref(sp, "textSize", "normal")
    val blocked = Pref(sp, "blocked", "")      // "id|name;id|name"
    val quests = Pref(sp, "quests", "")        // "hi,story,..."
    val background = Pref(sp, "background", false)
    val pubKey = Pref(sp, "pubKey", "")
    val privKey = Pref(sp, "privKey", "")
    val pins = Pref(sp, "pins", "")            // "id|key;id|key" first-seen public keys
    val rooms = Pref(sp, "rooms", "")          // "name|CODE;name|CODE"
    val sent = Pref(sp, "sent", 0)
    val relayed = Pref(sp, "relayed", 0)
    val bonus = Pref(sp, "bonus", 0)

    init { applyTheme(theme.value) }

    fun wipe() {
        sp.edit().clear().apply()
        listOf(name, vibe, theme, textSize, blocked, quests, pubKey, privKey, pins, rooms).forEach { it.reset() }
        listOf(notifications, ghost, battery, onboarded, background).forEach { it.reset() }
        listOf(sent, relayed, bonus).forEach { it.reset() }
        applyTheme("riso")
    }
}
