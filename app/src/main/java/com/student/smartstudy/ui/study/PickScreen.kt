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
fun PickScreen(m: Mode, p: Progress, back: () -> Unit, listState: LazyListState, open: (Subject, Int) ->
    Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(if (m == Mode.Study) "Study" else "Practise", back)
        LazyColumn(state = listState, contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            subjects.forEach { s ->
                item(key = s.id) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SubjectBadge(s)
                        Spacer(Modifier.width(10.dp))
                        Text(s.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
                    }
                }
                items(s.units.indices.toList(), key = { "${s.id}:$it" }) { i ->
                    val key = "${s.id}:$i"
                    val tag = if (m == Mode.Study) (if (key in p.done) "✓" else "›")
                    else p.scores[key]?.let { "$it%" } ?: "›"
                    Card(
                        Modifier.fillMaxWidth().tap(RoundedCornerShape(20.dp)) { open(s, i) },
                            shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (m == Mode.Study &&
                            rich["${s.id}:$i"] != null) GoldSoft else Color.White),
                        border = BorderStroke(1.dp, GoldLine)
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.units[i].title, fontSize = 16.sp, color = Ink)
                                val r = if (m == Mode.Study) rich["${s.id}:$i"] else null
                                if (r != null) Text("Detailed notes · ${r.sec.size} topics", fontSize = 12.sp,
                                    color = Accent)
                            }
                            Text(tag, fontSize = 18.sp, color = Accent)
                        }
                    }
                }
            }
        }
    }
}
