package com.student.smartstudy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MathInk = Color(0xFF2B2B6B)
private val MathBg = Color(0xFFEEF0FF)
private val SCHEMA = Regex("^[A-Z_][A-Za-z_0-9]*\\(.*\\)$")

private fun isMathLine(s: String): Boolean {
    if (s.startsWith("• ")) return false
    if (s.none { it.isLetter() }) return true
    if (SCHEMA.matches(s.trim())) return true
    val words = s.split(" ").count { w -> w.length >= 3 && w.all { it.isLetter() } }
    return s.any { it in "=∈∉⊆⊊∪∩×→⇒≤≥≠{}[]|⌈⌊△∅" } && words < 4
}

private fun kindStyle(k: String): Pair<String, Color> = when (k) {
    "qa" -> "?" to Color(0xFF3B9BE0)
    "gl" -> "Aa" to Color(0xFF7C6CF0)
    "sum" -> "Σ" to Color(0xFFE0A800)
    "cs" -> "</>" to Color(0xFF2E9E6B)
    "ex" -> "Ex" to Color(0xFFF2894E)
    "warn" -> "!" to Color(0xFFD64545)
    "fin" -> "✓" to Color(0xFF2E9E6B)
    else -> "" to Accent
}

@Composable
fun Lines(lines: List<String>, tint: Color = MathBg) {
    val groups = remember(lines) {
        val g = mutableListOf<Pair<Int, MutableList<String>>>()
        lines.forEach { l ->
            val kind = if (l.startsWith("\u0001")) 2 else if (isMathLine(l)) 1 else 0
            val t = if (kind == 2) l.substring(1) else l
            if (g.isNotEmpty() && g.last().first == kind) g.last().second.add(t) else g.add(kind to mutableListOf(t))
        }
        g
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { (kind, ls) ->
            when (kind) {
                2 -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0xFF23233A)) {
                    Text(
                        ls.joinToString("\n"), Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 19.sp, color = Color(0xFFE6E6FF), softWrap = false
                    )
                }
                1 -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = tint) {
                    Text(
                        ls.joinToString("\n"), Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, color = MathInk, softWrap = false
                    )
                }
                else -> Text(ls.joinToString("\n"), fontSize = 15.sp, lineHeight = 22.sp, color = Ink)
            }
        }
    }
}

@Composable
fun QaCard(n: String, q: String, a: String) {
    var open by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable(enabled = a.isNotEmpty()) { open = !open }, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = Accent.copy(alpha = 0.14f)) {
                    Text("Q$n", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(q, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 22.sp, color = Ink)
            }
            if (open) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Good) {
                Column(Modifier.padding(12.dp)) {
                    Text("Answer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D4F))
                    Text(a, fontSize = 14.sp, lineHeight = 21.sp, color = Ink)
                }
            } else if (a.isNotEmpty()) Text("Tap to reveal answer", color = Accent, fontSize = 13.sp)
            else Text("Self-check: answer in your own words", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun BlockView(b: Blk) {
    when (b) {
        is BP -> Text(b.x, fontSize = 15.sp, lineHeight = 23.sp, color = Ink)
        is BH -> Text(b.x, Modifier.padding(top = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        is BUl -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            b.x.forEach {
                Row {
                    Text("•", Modifier.width(18.dp), color = Accent, fontWeight = FontWeight.Bold)
                    Text(it, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 22.sp, color = Ink)
                }
            }
        }
        is BOl -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.x.forEachIndexed { n, t ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Text("${n + 1}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(t, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 22.sp, color = Ink)
                }
            }
        }
        is BLn -> Lines(b.x)
        is BCode -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF23233A)) {
            Text(
                b.x.joinToString("\n"), Modifier.padding(14.dp).horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFFE6E6FF), softWrap = false
            )
        }
        is BEx -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF4E6)) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(Color(0xFFF2894E)))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(b.h, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45A12), fontSize = 14.sp)
                    Lines(b.x, Color(0xFFFFE6C8))
                }
            }
        }
        is BNote -> {
            val (bg, fg, _) = when (b.k) {
                "warn" -> Triple(Color(0xFFFDECEC), Color(0xFFC62828), "")
                "key" -> Triple(Color(0xFFEEE9FF), Accent, "")
                else -> Triple(Color(0xFFE8F3FD), Color(0xFF1565C0), "")
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = bg) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(b.h, fontWeight = FontWeight.SemiBold, color = fg, fontSize = 14.sp)
                    Lines(b.x, Color.White.copy(alpha = 0.7f))
                }
            }
        }
        is BKv -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                b.x.forEachIndexed { n, (l, r) ->
                    if (n > 0) HorizontalDivider(color = Color(0xFFEDEDF2))
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        if (l.isNotEmpty()) Text(l, Modifier.weight(0.4f), fontWeight = FontWeight.Medium, color = Accent, fontSize = 14.sp)
                        Text(
                            r, Modifier.weight(if (l.isEmpty()) 1f else 0.6f), fontSize = 14.sp, lineHeight = 20.sp, color = Ink,
                            fontFamily = if (isMathLine(r)) FontFamily.Monospace else FontFamily.Default
                        )
                    }
                }
            }
        }
        is BGl -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.x.forEach { (t, d) ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Column(Modifier.padding(14.dp)) {
                        Text(t, fontWeight = FontWeight.SemiBold, color = Accent, fontSize = 14.sp)
                        Text(d, fontSize = 14.sp, lineHeight = 20.sp, color = Ink)
                    }
                }
            }
        }
        is BQa -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            b.x.forEachIndexed { n, (q, a) ->
                val m = Regex("^(\\d+)\\. ").find(q)
                QaCard(m?.groupValues?.get(1) ?: "${n + 1}", q.removePrefix(m?.value ?: ""), a)
            }
        }
    }
}

@Composable
fun Chip(t: String) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f)) {
        Text(t, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun RichHome(s: Subject, i: Int, p: Progress, back: () -> Unit, listState: LazyListState, open: (Int) -> Unit) {
    val r = rich["${s.id}:$i"] ?: return
    val key = "${s.id}:$i"
    val read = r.sec.indices.count { "$key:$it" in p.read }
    val mins = (r.sec.sumOf { sec -> sec.b.sumOf { blockWords(it) } } / 180).coerceAtLeast(1)
    Column(Modifier.fillMaxSize()) {
        TopBar(s.name, back)
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF5B5BD6), Color(0xFF9B59D0), Color(0xFFE0568B))))
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("UNIT ${i + 1}  ·  DETAILED NOTES", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.weight(1f))
                                    Text(if (key in p.stars) "★" else "☆", Modifier.clickable { p.star(key) }, color = Color.White, fontSize = 22.sp)
                                }
                                Text(r.title, color = Color.White, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium)
                                Text(r.intro, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("${r.sec.size} topics")
                            Chip("~$mins min read")
                        }
                        LinearProgressIndicator(
                            progress = { read / r.sec.size.toFloat() }, color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        )
                        Text("$read of ${r.sec.size} topics read", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
            }
            item { Text("Topics", Modifier.padding(top = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink) }
            itemsIndexed(r.sec) { idx, sec ->
                val (glyph, tint) = kindStyle(sec.k)
                Card(
                    Modifier.fillMaxWidth().clickable { open(idx) }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(if (glyph.isEmpty()) "${sec.n}" else glyph, color = tint, fontWeight = FontWeight.SemiBold, fontSize = if (glyph.length > 1) 13.sp else 16.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sec.t, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
                            if (sec.s.isNotEmpty()) Text(sec.s, fontSize = 12.5.sp, color = Color.Gray, maxLines = 2, lineHeight = 17.sp)
                        }
                        Text(if ("$key:$idx" in p.read) "✓" else "›", color = Accent, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

private fun blockWords(b: Blk): Int = when (b) {
    is BP -> b.x.split(" ").size
    is BH -> 2
    is BUl -> b.x.sumOf { it.split(" ").size }
    is BOl -> b.x.sumOf { it.split(" ").size }
    is BLn -> b.x.sumOf { it.split(" ").size }
    is BCode -> b.x.sumOf { it.split(" ").size }
    is BEx -> b.x.sumOf { it.split(" ").size }
    is BNote -> b.x.sumOf { it.split(" ").size }
    is BKv -> b.x.sumOf { it.first.split(" ").size + it.second.split(" ").size }
    is BGl -> b.x.sumOf { it.first.split(" ").size + it.second.split(" ").size }
    is BQa -> b.x.sumOf { it.first.split(" ").size }
}

@Composable
fun TopicScreen(s: Subject, i: Int, idx: Int, p: Progress, back: () -> Unit, go: (Int) -> Unit) {
    val r = rich["${s.id}:$i"] ?: return
    val sec = r.sec[idx]
    val n = r.sec.size
    val scroll = rememberScrollState()
    LaunchedEffect(idx) { p.markRead("${s.id}:$i:$idx"); scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Unit ${i + 1}", back)
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("TOPIC ${idx + 1} OF $n", color = Accent, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
            Text(sec.t, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium, color = Ink)
            key(idx) { sec.b.forEach { BlockView(it) } }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ go(idx - 1) }, Modifier.weight(1f).height(50.dp), enabled = idx > 0, shape = CircleShape) { Text("‹ Previous", color = Accent) }
            Button(
                { if (idx < n - 1) go(idx + 1) else back() }, Modifier.weight(1f).height(50.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text(if (idx < n - 1) "Next ›" else "Done ✓") }
        }
    }
}
