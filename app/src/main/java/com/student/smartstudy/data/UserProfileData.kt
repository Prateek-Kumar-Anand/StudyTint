package com.student.smartstudy

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap

data class Profile(val name: String = "", val father: String = "", val lurn: String = "",
    val roll: String = "", val course: String = "") {
    val isComplete: Boolean
        get() = name.isNotBlank() && father.isNotBlank() && lurn.isNotBlank() && roll.isNotBlank() &&
            course.isNotBlank()
}
