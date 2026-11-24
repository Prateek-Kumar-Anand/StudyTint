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
fun QuizScreen(s: Subject, i: Int, p: Progress, back: () -> Unit) {
    val unitQs = remember(s.id, i) { quiz[s.id].orEmpty().filter { it.unit == i } }
    val topics = remember(s.id, i) { unitQs.map { it.topic }.filter { it.isNotEmpty() }.distinct() }
    var mode by remember(s.id, i) { mutableStateOf<String?>(if (topics.isEmpty()) "" else null) }
    val qs = when (val m = mode) { null -> emptyList(); "" -> unitQs; else ->
        unitQs.filter { it.topic == m } }
    var idx by remember(s.id, i) { mutableStateOf(0) }
    var sel by remember(s.id, i) { mutableStateOf(-1) }
    var right by remember(s.id, i) { mutableStateOf(0) }
    fun start(m: String?) { mode = m; idx = 0; sel = -1; right = 0 }
    LaunchedEffect(idx, mode) {
        if (mode != null && qs.isNotEmpty() && idx >= qs.size) {
            p.attempt("${s.id}:$i")
            if (mode == "") p.save("${s.id}:$i", right * 100 / qs.size)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(s.units[i].title.substringAfter(": "), { if (mode != null && topics.isNotEmpty()) start(null)
            else back() })
        if (unitQs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center) { Text("No questions yet.", color = Color.Gray) }
        } else if (mode == null) {
            LazyColumn(contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(Modifier.fillMaxWidth().tap(RoundedCornerShape(20.dp)) { start("") },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Accent)) {
                        Text("All questions in order · ${unitQs.size}", Modifier.padding(18.dp),
                            color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                }
                item { Text("Topics", Modifier.padding(top = 6.dp), fontSize = 16.sp,
                    fontWeight = FontWeight.Medium, color = Ink) }
                items(topics) { t ->
                    Card(Modifier.fillMaxWidth().tap(RoundedCornerShape(18.dp)) { start(t) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, GoldLine)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t, Modifier.weight(1f), color = Ink)
                            Text("${unitQs.count { it.topic == t }}", color = Accent,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else if (idx >= qs.size) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Orb(120.dp)
                Spacer(Modifier.height(20.dp))
                Text(if (qs.first().a == -1) "Confident on $right of ${qs.size}"
                    else "You scored $right/${qs.size}", fontSize = 24.sp, fontWeight = FontWeight.Medium,
                    color = Ink)
                Text("${right * 100 / qs.size}%", fontSize = 18.sp, color = Accent)
                Spacer(Modifier.height(24.dp))
                Button({ start(mode) }, Modifier.fillMaxWidth().height(54.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Try again") }
                if (topics.isNotEmpty()) TextButton({ start(null) }) { Text("Choose another topic",
                    color = Accent) }
                TextButton(back) { Text("Done", color = Accent) }
            }
        } else {
            val q = qs[idx]
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Question ${idx + 1} of ${qs.size}" + if (q.topic.isNotEmpty()) "  ·  ${q.topic}"
                        else "", color = Color.Gray, fontSize = 13.sp)
                    Text(q.q, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium,
                        color = Ink)
                    if (q.a == -1) Text("Think of your answer first, then rate yourself.", color = Color.Gray,
                        fontSize = 13.sp)
                    q.o.forEachIndexed { n, label ->
                        val c = when {
                            sel == -1 -> Color.White
                            q.a == -1 -> if (n == sel) (if (n == 0) Good else Bad) else Color.White
                            n == q.a -> Good
                            n == sel -> Bad
                            else -> Color.White
                        }
                        val cc by animateColorAsState(c, tween(250), label = "opt")
                        Surface(
                            Modifier.fillMaxWidth().clickable(enabled = sel == -1) { if (sel == -1) { sel = n;
                                val ok = if (q.a == -1) n == 0 else n == q.a; if (ok) right++;
                                p.record("${s.id}:$i", q.topic, ok) } },
                            shape = RoundedCornerShape(16.dp), color = cc,
                                border = BorderStroke(1.dp, GoldLine)
                        ) { Text(label, Modifier.padding(16.dp), color = Ink) }
                    }
                    AnimatedVisibility(visible = sel != -1 && q.sol.isNotEmpty(),
                        enter = fadeIn(tween(250)) + expandVertically(tween(250))) {
                        Surface(shape = RoundedCornerShape(16.dp), color = GoldSoft,
                            border = BorderStroke(1.dp, GoldLine)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("Explanation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = Accent)
                                Text(q.sol, fontSize = 14.sp, lineHeight = 20.sp, color = Ink)
                            }
                        }
                    }
                }
                if (sel != -1) Button({ idx++; sel = -1 }, Modifier.fillMaxWidth().padding(20.dp)
                    .height(54.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text(if (idx == qs.size - 1) "Finish" else "Next") }
            }
        }
    }
}
