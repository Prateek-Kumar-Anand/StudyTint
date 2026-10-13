package com.student.smartstudy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun pct(a: Int, b: Int) = if (b == 0) 0 else a * 100 / b
private fun accColor(a: Int) = when { a >= 75 -> Color(0xFF2E9E6B); a >= 50 -> Color(0xFFF2894E); else -> Color(0xFFD64545) }

private fun readOf(s: Subject, i: Int, p: Progress): Pair<Int, Int> {
    val r = rich["${s.id}:$i"]
    return if (r != null) r.sec.indices.count { "${s.id}:$i:$it" in p.read } to r.sec.size
    else (if ("${s.id}:$i" in p.done) 1 else 0) to 1
}

private fun topicLabel(key: String): String {
    val h = key.substringBefore('#')
    val sn = subjects.firstOrNull { it.id == h.substringBefore(':') }?.name ?: h
    return "$sn · U${(h.substringAfter(':').toIntOrNull() ?: 0) + 1} · ${key.substringAfter('#')}"
}

@Composable
fun Donut(frac: Float, dia: Dp, color: Color, label: String, sub: String) {
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 14.dp.toPx()
            val box = Size(this.size.width - w, this.size.height - w)
            drawArc(color = Color(0xFFE9E9F2), startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(w / 2, w / 2), size = box, style = Stroke(w))
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false, topLeft = Offset(w / 2, w / 2), size = box, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(sub, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun StatTile(value: String, label: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = color)
            Text(label, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun MiniBar(label: String, frac: Float, right: String, color: Color) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
            Text(right, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, color = color, trackColor = Color(0xFFE9E9F2), modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
    }
}

@Composable
fun ScoreScreen(p: Progress, listState: LazyListState, back: () -> Unit) {
    val units = subjects.flatMap { s -> s.units.indices.map { s to it } }
    val ans = units.sumOf { (s, i) -> p.stats["${s.id}:$i"]?.first ?: 0 }
    val cor = units.sumOf { (s, i) -> p.stats["${s.id}:$i"]?.second ?: 0 }
    val quizzes = units.sumOf { (s, i) -> p.stats["${s.id}:$i@"]?.first ?: 0 }
    val acc = pct(cor, ans)
    val readTotal = units.sumOf { (s, i) -> readOf(s, i, p).second }
    val readDone = units.sumOf { (s, i) -> readOf(s, i, p).first }
    val unitsStarted = units.count { (s, i) -> readOf(s, i, p).first > 0 }
    val topics = p.stats.entries.filter { '#' in it.key && it.value.first >= 3 }.map { Triple(it.key, it.value.first, it.value.second) }
    val strong = topics.filter { pct(it.third, it.second) >= 70 }.sortedByDescending { pct(it.third, it.second) }.take(3)
    val weak = topics.filter { pct(it.third, it.second) < 70 }.sortedBy { pct(it.third, it.second) }.take(3)

    Column(Modifier.fillMaxSize()) {
        TopBar("Score & Analysis", back)
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color.White, Color(0xFFEDEBFF)))).padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(acc / 100f, 130.dp, accColor(acc), if (ans == 0) "—" else "$acc%", "accuracy")
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (ans == 0) "No quiz taken yet" else "$cor of $ans correct", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
                            MiniBar("Reading", readDone / readTotal.toFloat(), "$readDone / $readTotal", Accent)
                            Text("$unitsStarted of ${units.size} units started", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("$cor", "Correct", Color(0xFF2E9E6B), Modifier.weight(1f))
                    StatTile("${ans - cor}", "Wrong", Color(0xFFD64545), Modifier.weight(1f))
                    StatTile("$quizzes", "Quizzes done", Accent, Modifier.weight(1f))
                }
            }
            if (strong.isNotEmpty() || weak.isNotEmpty() || ans > 0) item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Insights", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink)
                        if (topics.isEmpty()) Text("Answer at least 3 questions in a topic to unlock strongest and weakest topics.", fontSize = 13.sp, color = Color.Gray)
                        if (weak.isNotEmpty()) {
                            Text("Needs work", Modifier.padding(top = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD64545))
                            weak.forEach { Text("• ${topicLabel(it.first)} — ${pct(it.third, it.second)}% (${it.third}/${it.second})", fontSize = 13.sp, color = Ink) }
                        }
                        if (strong.isNotEmpty()) {
                            Text("Strongest", Modifier.padding(top = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2E9E6B))
                            strong.forEach { Text("• ${topicLabel(it.first)} — ${pct(it.third, it.second)}% (${it.third}/${it.second})", fontSize = 13.sp, color = Ink) }
                        }
                    }
                }
            }
            item { Text("By subject", Modifier.padding(top = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink) }
            items(subjects) { s ->
                val sAns = s.units.indices.sumOf { p.stats["${s.id}:$it"]?.first ?: 0 }
                val sCor = s.units.indices.sumOf { p.stats["${s.id}:$it"]?.second ?: 0 }
                val rd = s.units.indices.sumOf { readOf(s, it, p).first }
                val rt = s.units.indices.sumOf { readOf(s, it, p).second }
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                SubjectBadge(s)
                                Spacer(Modifier.width(10.dp))
                                Text(s.name, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink)
                            }
                            Text(if (sAns == 0) "—" else "${pct(sCor, sAns)}%", color = if (sAns == 0) Color.Gray else accColor(pct(sCor, sAns)), fontWeight = FontWeight.Medium)
                        }
                        MiniBar("Reading", rd / rt.toFloat(), "$rd / $rt", s.color)
                        MiniBar("Accuracy", pct(sCor, sAns) / 100f, if (sAns == 0) "no answers" else "$sCor / $sAns correct", if (sAns == 0) Color.LightGray else accColor(pct(sCor, sAns)))
                        HorizontalDivider(color = Color(0xFFEDEDF2))
                        s.units.indices.forEach { i ->
                            val (r, t) = readOf(s, i, p)
                            val (ua, uc) = p.stats["${s.id}:$i"] ?: (0 to 0)
                            val best = p.scores["${s.id}:$i"]
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(s.units[i].title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                                Text(
                                    "Read: " + (if (t > 1) "$r/$t topics" else if (r > 0) "done" else "not yet") +
                                        "   ·   Practice: " + (if (ua == 0) "none yet" else "$uc/$ua (${pct(uc, ua)}%)") +
                                        (if (best != null) "   ·   Best $best%" else ""),
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
