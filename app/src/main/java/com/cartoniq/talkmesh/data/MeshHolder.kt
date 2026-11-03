package com.cartoniq.talkmesh.data

import android.content.Context

/** One Prefs + one MeshEngine for the whole process, shared by the UI and the background service. */
object MeshHolder {
    @Volatile private var p: Prefs? = null
    @Volatile private var e: MeshEngine? = null

    fun prefs(ctx: Context): Prefs = p ?: synchronized(this) { p ?: Prefs(ctx.applicationContext).also { p = it } }
    fun engine(ctx: Context): MeshEngine = e ?: synchronized(this) { e ?: MeshEngine(ctx.applicationContext, prefs(ctx)).also { e = it } }
}
