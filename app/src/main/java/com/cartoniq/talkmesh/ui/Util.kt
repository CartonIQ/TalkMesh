package com.cartoniq.talkmesh.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import androidx.core.content.ContextCompat as CC
import com.cartoniq.talkmesh.MeshService
import android.os.Build
import android.widget.Toast

const val SUPPORT_EMAIL = "cartoniq.business@gmail.com"
const val PRIVACY_URL = "https://cartoniq.github.io/privacy/"
const val SITE_URL = "https://cartoniq.runs-at.dev"

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast("No app found to open this link")
    }
}

fun Context.storeUrl() = "https://play.google.com/store/apps/details?id=$packageName"

fun Context.sendMail(subject: String, body: String = "") {
    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try { startActivity(i) } catch (e: Exception) { toast("No email app found") }
}

fun Context.shareApp() {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Chat with nearby friends with zero internet: TalkMesh ${storeUrl()}")
    }
    startActivity(Intent.createChooser(i, "Share TalkMesh"))
}

fun Context.versionName(): String = try {
    packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
} catch (e: Exception) { "1.0.0" }

fun Context.deviceInfo() = "TalkMesh ${versionName()} / ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}"

fun setTorch(ctx: Context, on: Boolean) {
    try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        cm.setTorchMode(id, on)
    } catch (e: Exception) { /* no flash or camera busy */ }
}

/** Runtime permissions Nearby Connections needs on this Android version. */
fun meshPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.NEARBY_WIFI_DEVICES
    )
    Build.VERSION.SDK_INT >= 31 -> arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION
    )
    else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

fun Context.has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
fun Context.hasMeshPermissions() = meshPermissions().all { has(it) }

/** Last known location from GPS/network, or null. Needs ACCESS_FINE_LOCATION. */
fun Context.lastLocation(): Location? {
    if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) return null
    return try {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null } }
            .maxByOrNull { it.time }
    } catch (e: Exception) { null }
}

fun Context.startMeshService() {
    try { CC.startForegroundService(this, Intent(this, MeshService::class.java)) } catch (e: Exception) { toast("could not start background mode") }
}

fun Context.stopMeshService() { stopService(Intent(this, MeshService::class.java)) }

fun Context.shareText(text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
