package com.cartoniq.talkmesh.data

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

/** Push-to-talk recorder. Needs RECORD_AUDIO permission granted before start(). */
class Recorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean = try {
        val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setMaxDuration(15_000)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        rec = r; file = f; startedAt = System.currentTimeMillis()
        true
    } catch (e: Exception) { rec?.release(); rec = null; false }

    /** Returns the recorded file, or null if too short or failed. */
    fun stop(): File? {
        val r = rec ?: return null
        rec = null
        val long = System.currentTimeMillis() - startedAt > 600
        try { r.stop() } catch (e: Exception) { r.release(); file?.delete(); return null }
        r.release()
        if (!long) { file?.delete(); return null }
        return file
    }
}

fun playUri(ctx: Context, uri: String) {
    try {
        MediaPlayer().apply {
            setDataSource(ctx, Uri.parse(uri))
            setOnPreparedListener { it.start() }
            setOnCompletionListener { it.release() }
            setOnErrorListener { mp, _, _ -> mp.release(); true }
            prepareAsync()
        }
    } catch (e: Exception) { /* unplayable file */ }
}
