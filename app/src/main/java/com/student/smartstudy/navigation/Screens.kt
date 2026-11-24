package com.student.smartstudy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Mode { Study, Practise }

sealed interface Screen {
    object Welcome : Screen
    object Home : Screen
    object Score : Screen
    object CLab : Screen
    object PyLab : Screen
    object DbLab : Screen
    data class Setup(val edit: Boolean) : Screen
    data class Pick(val m: Mode) : Screen
    data class Notes(val s: Subject, val i: Int, val prev: Screen) : Screen
    data class RichNotes(val s: Subject, val i: Int, val prev: Screen) : Screen
    data class Topic(val s: Subject, val i: Int, val idx: Int, val prev: Screen) : Screen
    data class Quiz(val s: Subject, val i: Int, val prev: Screen) : Screen
}
