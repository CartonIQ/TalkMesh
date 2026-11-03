package com.cartoniq.talkmesh

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.cartoniq.talkmesh.data.MeshHolder
import com.cartoniq.talkmesh.data.Notifier
import com.cartoniq.talkmesh.ui.hasMeshPermissions

/** Keeps the mesh running (and relaying for other phones) while the app is closed. */
class MeshService : Service() {
    companion object { const val ACTION_STOP = "com.cartoniq.talkmesh.STOP" }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MeshHolder.prefs(this).background.set(false)
            MeshHolder.engine(this).stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        Notifier.ensureChannels(this)
        val n = Notifier.serviceNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notifier.SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(Notifier.SERVICE_ID, n)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (hasMeshPermissions()) MeshHolder.engine(this).start()
        return START_STICKY
    }
}
