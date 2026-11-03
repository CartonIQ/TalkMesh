package com.cartoniq.talkmesh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cartoniq.talkmesh.data.MeshHolder
import com.cartoniq.talkmesh.ui.Root

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root() }
    }

    override fun onStart() { super.onStart(); MeshHolder.engine(this).appVisible = true }
    override fun onStop() { super.onStop(); MeshHolder.engine(this).appVisible = false }

    override fun onDestroy() {
        super.onDestroy()
        // Without background mode the mesh stops when the app is closed to save battery.
        if (isFinishing && !MeshHolder.prefs(this).background.value) MeshHolder.engine(this).stop()
    }
}
