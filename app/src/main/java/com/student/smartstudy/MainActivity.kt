package com.student.smartstudy

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light bars with dark icons, so the clock, battery and notification icons stay visible.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        Updates.migrate(applicationContext)
        loadQuestionBanks(applicationContext)
        loadRich(applicationContext)
        setContent { MaterialTheme(colorScheme = GoldColorScheme) { App(remember { Progress(applicationContext) }) } }
    }

    override fun onStart() {
        super.onStart()
        DbEngine.foreground = true
    }

    override fun onStop() {
        DbEngine.foreground = false
        val app = applicationContext
        Thread { DbEngine.sealAll(app) }.start()
        super.onStop()
    }
}
