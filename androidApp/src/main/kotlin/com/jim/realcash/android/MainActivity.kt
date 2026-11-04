package com.jim.realcash.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.jim.realcash.android.debug.androidDebugEntries
import com.jim.realcash.ui.RealCashApp

/** Hosts the shared Compose UI, edge to edge, with the Android-only debug screens in 설정 → 디버그. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = realCash
        setContent {
            val view = LocalView.current
            val haptics = remember(view) { AndroidHaptics(view) }
            val debugEntries = remember { androidDebugEntries() }
            RealCashApp(app.data, haptics, platformDebugEntries = debugEntries)
        }
    }
}
