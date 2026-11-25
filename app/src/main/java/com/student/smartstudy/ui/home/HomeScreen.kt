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

@Composable
fun Home(p: Progress, listState: LazyListState, go: (Screen) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "greeting") {
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).fillMaxHeight().clip(CircleShape).background(Gold))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(vertical = 2.dp)) {
                    Text("Hi, ${p.profile.name.trim().substringBefore(' ').ifBlank { "Student" }}",
                        fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("What shall we do today?", color = Color(0xFF8A7B52), fontSize = 13.sp)
                }
            }
        }
        item(key = "profile") {
            if (p.profile.isComplete) ProfileCard(p.profile, p.photo) { go(Screen.Setup(true)) }
            else ProfilePromptCard { go(Screen.Setup(true)) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard("Study", "Read unit notes", Color(0xFFD4A017),
                    Modifier.weight(1f)) { go(Screen.Pick(Mode.Study)) }
                OptionCard("Practise", "Quiz yourself", Color(0xFFE07B39),
                    Modifier.weight(1f)) { go(Screen.Pick(Mode.Practise)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard("Score", "Your results", Color(0xFFB5651D),
                    Modifier.weight(1f)) { go(Screen.Score) }
                OptionCard("C Lab", "Write & run C code", Color(0xFFC9A227),
                    Modifier.weight(1f)) { go(Screen.CLab) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard("Python Lab", "Write & run Python", Color(0xFFE8A93B),
                    Modifier.weight(1f)) { go(Screen.PyLab) }
                OptionCard("DBMS Lab", "Write & run SQL", DbColor, Modifier.weight(1f)) { go(Screen.DbLab) }
            }
        }
        item(key = "activity") { ActivityBox(p.activity) }
        item(key = "version") {
            val vctx = androidx.compose.ui.platform.LocalContext.current
            Text("StudyTint version ${Updates.versionName(vctx)}", Modifier.fillMaxWidth().padding(top = 4.dp),
                color = Color(0xFFB09A5A), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}
