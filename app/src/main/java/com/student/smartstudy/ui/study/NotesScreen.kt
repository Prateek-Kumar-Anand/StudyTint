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
fun NotesScreen(s: Subject, i: Int, p: Progress, back: () -> Unit) {
    val u = s.units[i]
    val key = "${s.id}:$i"
    val ok = key in p.done
    Column(Modifier.fillMaxSize()) {
        TopBar(s.name, back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Medium,
                        color = Ink)
                }
            }
            items(u.notes) { n ->
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White,
                    border = BorderStroke(1.dp, GoldLine)) {
                    Text(n, Modifier.fillMaxWidth().padding(16.dp), fontSize = 15.sp, color = Ink)
                }
            }
        }
        Button(
            { p.toggle(key) }, Modifier.fillMaxWidth().padding(20.dp).height(56.dp), shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = if (ok) GoldSoft else Accent,
                contentColor = if (ok) Accent else Color.White)
        ) { Text(if (ok) "Completed ✓ (tap to undo)" else "Mark as completed") }
    }
}
