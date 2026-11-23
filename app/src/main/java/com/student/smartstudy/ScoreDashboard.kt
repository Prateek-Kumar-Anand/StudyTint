package com.student.smartstudy

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

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
            drawArc(color = Color(0xFFF0E5BF), startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(w / 2, w / 2), size = box, style = Stroke(w))
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
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, GoldLine)) {
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
        LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, color = color, trackColor = Color(0xFFF0E5BF), modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Score & Analysis dashboard: soft white cards, a progress bar with a marker, an activity dot grid,
// a ranked topic list with progress rings and a petal "heat" chart of accuracy per subject.
// ---------------------------------------------------------------------------------------------------------------------

private val Tile = Color(0xFFF7F5EE)
private val GoodGreen = Color(0xFF2E9E6B)
private val NoData = Color(0xFFCDBF93)
private const val GRID_WEEKS = 15

/** yyyyMMdd for the day [daysAgo] days before today (java.util.Calendar works on every supported Android version). */
fun dayKey(daysAgo: Int): String {
    val c = java.util.Calendar.getInstance()
    c.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
    return String.format(java.util.Locale.US, "%04d%02d%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

private fun frac(a: Int, b: Int) = if (b == 0) 0f else (a / b.toFloat()).coerceIn(0f, 1f)
private fun levelOf(p: Int) = when { p >= 85 -> "Expert"; p >= 60 -> "Intermediate"; else -> "Beginner" }
private fun shortName(s: Subject) = if (s.name.length > 8) s.mark else s.name

private data class TopicStat(val subject: Subject?, val unit: Int, val topic: String, val answered: Int, val correct: Int) {
    val score: Int get() = pct(correct, answered)
}

private fun parseTopic(key: String, v: Pair<Int, Int>): TopicStat {
    val head = key.substringBefore('#')
    val sub = subjects.firstOrNull { it.id == head.substringBefore(':') }
    return TopicStat(sub, head.substringAfter(':').toIntOrNull() ?: 0, key.substringAfter('#'), v.first, v.second)
}

/** One count per cell of the activity grid, oldest column first, 7 rows per column (Sunday on top). -1 marks a day in the future. */
private fun activityCells(activity: Map<String, Int>): List<Int> {
    val todayRow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1
    val out = ArrayList<Int>(GRID_WEEKS * 7)
    for (c in 0 until GRID_WEEKS) for (r in 0 until 7) {
        val ago = (GRID_WEEKS - 1 - c) * 7 + (todayRow - r)
        out.add(if (ago < 0) -1 else activity[dayKey(ago)] ?: 0)
    }
    return out
}

@Composable
private fun DashCard(title: String, dot: Color, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, GoldLine)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink)
                trailing()
            }
            content()
        }
    }
}

@Composable
private fun Ring(fraction: Float, dia: Dp, color: Color, label: String) {
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 4.dp.toPx()
            val box = Size(this.size.width - w, this.size.height - w)
            drawArc(color = Color(0xFFF0E5BF), startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(w / 2, w / 2), size = box, style = Stroke(w))
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = Offset(w / 2, w / 2), size = box, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Ink)
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Tile).padding(3.dp)) {
        options.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.clip(CircleShape).background(if (on) Color.White else Color.Transparent).clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) { Text(t, fontSize = 13.sp, color = if (on) Ink else Color.Gray, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
        }
    }
}

@Composable
private fun SmallTile(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Tile).padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

@Composable
private fun Petal(s: Subject, answered: Int, correct: Int, modifier: Modifier) {
    val heat = if (answered == 0) NoData else accColor(pct(correct, answered))
    Box(modifier.size(112.dp).clip(RoundedCornerShape(44.dp)).background(Color(0xFFFFF9E8)), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(heat.copy(alpha = if (answered == 0) 0.25f else 0.6f), Color.Transparent))))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (answered == 0) "—" else "${pct(correct, answered)}%", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(shortName(s), fontSize = 12.sp, color = Color(0xFF8A7B52), maxLines = 1)
        }
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
    val readPct = pct(readDone, readTotal)
    val unitsStarted = units.count { (s, i) -> readOf(s, i, p).first > 0 }
    val topics = p.stats.entries.filter { '#' in it.key && it.value.first >= 3 }.map { parseTopic(it.key, it.value) }
    val perSubject = subjects.map { s ->
        val a = s.units.indices.sumOf { p.stats["${s.id}:$it"]?.first ?: 0 }
        val c = s.units.indices.sumOf { p.stats["${s.id}:$it"]?.second ?: 0 }
        Triple(s, a, c)
    }
    val weakest = perSubject.filter { it.second > 0 }.minByOrNull { pct(it.third, it.second) }
    var showStrong by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Score & Analysis", back)
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ---- 1. Study progress ------------------------------------------------------------------------------------
            item {
                DashCard("Study Progress", Accent, trailing = {
                    Surface(shape = CircleShape, color = Ink) {
                        Text(if (ans == 0) "No quiz yet" else "Accuracy $acc%", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("$readDone", fontSize = 42.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Text("note sections read", fontSize = 13.sp, color = Color.Gray)
                        }
                        Text(
                            buildString { append("$readPct%"); append(" of all notes") }, fontSize = 13.sp, color = GoodGreen,
                            fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    val f = frac(readDone, readTotal)
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Tile)) {
                        Box(
                            Modifier.fillMaxWidth(f.coerceAtLeast(0.03f)).fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(Color(0xFFF8DE7E), Color(0xFFE2AA2C))))
                        )
                        Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.03f)).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                            Box(Modifier.width(3.dp).fillMaxHeight(0.8f).clip(RoundedCornerShape(2.dp)).background(Ink))
                        }
                    }
                    Row {
                        Text("0", Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                        Text("$unitsStarted of ${units.size} units started", fontSize = 12.sp, color = Color.Gray)
                        Text("$readTotal", Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }

                }
            }

            // ---- 2. Subjects ------------------------------------------------------------------------------------------
            item {
                DashCard("Subjects", Color(0xFFE07B39)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Tile).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        perSubject.forEachIndexed { n, (s, a, c) ->
                            val rd = s.units.indices.sumOf { readOf(s, it, p).first }
                            val rt = s.units.indices.sumOf { readOf(s, it, p).second }
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SubjectBadge(s)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$rd of $rt sections read", fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(if (a == 0) "—" else "${pct(c, a)}%", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (a == 0) Color.Gray else accColor(pct(c, a)))
                            }
                            if (n < perSubject.size - 1) HorizontalDivider(color = Color(0xFFEFE3BC))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallTile("$readPct%", "Notes read", Accent, Modifier.weight(1f))
                        SmallTile(if (ans == 0) "—" else "$acc%", "Accuracy", if (ans == 0) Color.Gray else accColor(acc), Modifier.weight(1f))
                        SmallTile("$quizzes", "Quizzes", Ink, Modifier.weight(1f))
                    }
                    Text(if (ans == 0) "No answers yet" else "$cor correct and ${ans - cor} wrong out of $ans answers", fontSize = 12.sp, color = Color.Gray)
                }
            }

            // ---- 3. Top topics ----------------------------------------------------------------------------------------
            item {
                val list = if (showStrong) topics.filter { it.score >= 70 }.sortedByDescending { it.score }.take(5)
                else topics.filter { it.score < 70 }.sortedBy { it.score }.take(5)
                DashCard("Top Topics", GoodGreen, trailing = { Segmented(listOf("Strongest", "Needs work"), if (showStrong) 0 else 1) { showStrong = it == 0 } }) {
                    if (list.isEmpty()) {
                        Text(
                            if (topics.isEmpty()) "Answer at least 3 questions in a topic to unlock this list." else if (showStrong) "No topic is at 70% or above yet. Keep practising." else "Nothing needs work right now. Well done.",
                            fontSize = 13.sp, color = Color.Gray
                        )
                    }
                    list.forEach { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val col = t.subject?.color ?: Accent
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                Text(t.subject?.mark ?: "?", color = col, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.topic, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Box(Modifier.clip(CircleShape).background(Tile).padding(horizontal = 10.dp, vertical = 3.dp)) {
                                        Text(levelOf(t.score), fontSize = 11.sp, color = Ink)
                                    }
                                    Text("  ${t.subject?.let { shortName(it) } ?: ""} · U${t.unit + 1}", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                }
                            }
                            Ring(t.score / 100f, 44.dp, accColor(t.score), "${t.score}%")
                        }
                    }
                }
            }

            // ---- 4. Subject trends (petal heat chart) -----------------------------------------------------------------
            item {
                DashCard("Subject Trends", Color(0xFFB5651D)) {
                    Box(Modifier.fillMaxWidth().height(296.dp), contentAlignment = Alignment.Center) {
                        perSubject.forEachIndexed { k, (s, a, c) ->
                            val ang = Math.toRadians(-90.0 + 72.0 * k)
                            Petal(s, a, c, Modifier.align(Alignment.Center).offset(x = (88 * cos(ang)).dp, y = (88 * sin(ang)).dp))
                        }
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Text("♥", fontSize = 15.sp, color = Ink)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tile).padding(14.dp), verticalAlignment = Alignment.Top) {
                        Text("Insight: ", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text(
                            when {
                                ans == 0 -> "Take a quiz in any subject to see how each one is going."
                                weakest != null && pct(weakest.third, weakest.second) < 70 -> "Revise ${weakest.first.name} next. Its accuracy is ${pct(weakest.third, weakest.second)}%."
                                readPct < 50 -> "Your accuracy is good. Read more notes to cover every unit."
                                else -> "Great balance. Try harder practice questions to keep the pace."
                            },
                            fontSize = 13.sp, color = Color(0xFF5A4A1E), modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ---- 5. Unit details --------------------------------------------------------------------------------------
            item {
                DashCard("Unit Details", Accent) {
                    subjects.forEachIndexed { n, s ->
                        val sAns = s.units.indices.sumOf { p.stats["${s.id}:$it"]?.first ?: 0 }
                        val sCor = s.units.indices.sumOf { p.stats["${s.id}:$it"]?.second ?: 0 }
                        val rd = s.units.indices.sumOf { readOf(s, it, p).first }
                        val rt = s.units.indices.sumOf { readOf(s, it, p).second }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SubjectBadge(s)
                                Spacer(Modifier.width(10.dp))
                                Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink)
                                Text(if (sAns == 0) "—" else "${pct(sCor, sAns)}%", color = if (sAns == 0) Color.Gray else accColor(pct(sCor, sAns)), fontWeight = FontWeight.Medium)
                            }
                            MiniBar("Reading", frac(rd, rt), "$rd / $rt", s.color)
                            MiniBar("Accuracy", pct(sCor, sAns) / 100f, if (sAns == 0) "no answers" else "$sCor / $sAns correct", if (sAns == 0) Color.LightGray else accColor(pct(sCor, sAns)))
                            s.units.indices.forEach { i ->
                                val (r, t) = readOf(s, i, p)
                                val (ua, uc) = p.stats["${s.id}:$i"] ?: (0 to 0)
                                val best = p.scores["${s.id}:$i"]
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tile).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                        if (n < subjects.size - 1) HorizontalDivider(color = Color(0xFFEFE3BC))
                    }
                }
            }
        }
    }
}

/** The "Daily activity" dot grid (last 15 weeks). Shown at the bottom of Home. */
@Composable
fun ActivityBox(activity: Map<String, Int>, modifier: Modifier = Modifier) {
    val cells = remember(activity) { activityCells(activity) }
    val activeDays = cells.count { it > 0 }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Tile).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFE6D9A8)))
            Text("  no activity", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFD4A017)))
            Text("  active", fontSize = 12.sp, color = Color.Gray)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(92.dp)) {
                Text("Daily activity", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink, lineHeight = 24.sp)
                Text("$activeDays active days", Modifier.padding(top = 4.dp), fontSize = 11.sp, color = Color.Gray)
            }
            Canvas(Modifier.weight(1f).height(98.dp)) {
                val cell = minOf(this.size.width / GRID_WEEKS, this.size.height / 7f)
                val left = this.size.width - cell * GRID_WEEKS
                cells.forEachIndexed { idx, n ->
                    if (n >= 0) {
                        val col = idx / 7
                        val row = idx % 7
                        val color = when { n == 0 -> Color(0xFFE6D9A8); n < 4 -> Color(0xFFF0C94F); else -> Color(0xFFC98A0A) }
                        drawCircle(color, radius = cell * (if (n == 0) 0.14f else 0.24f), center = Offset(left + cell * (col + 0.5f), cell * (row + 0.5f)))
                    }
                }
            }
        }
    }
}
