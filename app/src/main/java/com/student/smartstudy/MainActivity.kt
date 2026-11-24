package com.student.smartstudy

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Updates.migrate(applicationContext)
        loadQuestionBanks(applicationContext)
        loadRich(applicationContext)
        setContent { MaterialTheme(colorScheme = GoldColorScheme) { App(remember { Progress(applicationContext) }) } }
    }
}
