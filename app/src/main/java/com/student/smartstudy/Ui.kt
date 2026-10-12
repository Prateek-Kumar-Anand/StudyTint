package com.student.smartstudy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

val Accent = Color(0xFF5B5BD6)
val Bg = Color(0xFFF4F4F6)
val Ink = Color(0xFF1E1E24)
val Good = Color(0xFFD7F5E0)
val Bad = Color(0xFFFBDADA)

enum class Mode { Study, Practise }

sealed interface Screen {
    object Welcome : Screen
    object Home : Screen
    object Score : Screen
    object Stars : Screen
    data class Pick(val m: Mode) : Screen
    data class Notes(val s: Subject, val i: Int, val prev: Screen) : Screen
    data class RichNotes(val s: Subject, val i: Int, val prev: Screen) : Screen
    data class Topic(val s: Subject, val i: Int, val idx: Int, val prev: Screen) : Screen
    data class Quiz(val s: Subject, val i: Int, val prev: Screen) : Screen
}

@Composable
fun App(p: Progress) {
    var screen by remember { mutableStateOf<Screen>(Screen.Welcome) }
    fun parent(s: Screen): Screen = when (s) {
        is Screen.Notes -> s.prev
        is Screen.RichNotes -> s.prev
        is Screen.Topic -> Screen.RichNotes(s.s, s.i, s.prev)
        is Screen.Quiz -> s.prev
        else -> Screen.Home
    }
    BackHandler(screen != Screen.Welcome && screen != Screen.Home) { screen = parent(screen) }
    Box(Modifier.fillMaxSize().background(if (screen == Screen.Welcome) Color(0xFF0B0B0B) else Bg).statusBarsPadding().navigationBarsPadding()) {
        when (val s = screen) {
            Screen.Welcome -> Welcome { screen = Screen.Home }
            Screen.Home -> Home(p) { screen = it }
            Screen.Score -> ScoreScreen(p) { screen = Screen.Home }
            Screen.Stars -> StarsScreen(p, { screen = Screen.Home }) { sub, i -> screen = if (rich["${sub.id}:$i"] != null) Screen.RichNotes(sub, i, Screen.Stars) else Screen.Notes(sub, i, Screen.Stars) }
            is Screen.Pick -> PickScreen(s.m, p, { screen = Screen.Home }) { sub, i ->
                screen = if (s.m == Mode.Study) (if (rich["${sub.id}:$i"] != null) Screen.RichNotes(sub, i, s) else Screen.Notes(sub, i, s)) else Screen.Quiz(sub, i, s)
            }
            is Screen.Notes -> NotesScreen(s.s, s.i, p) { screen = s.prev }
            is Screen.RichNotes -> RichHome(s.s, s.i, p, { screen = s.prev }) { screen = Screen.Topic(s.s, s.i, it, s.prev) }
            is Screen.Topic -> TopicScreen(s.s, s.i, s.idx, p, { screen = Screen.RichNotes(s.s, s.i, s.prev) }) { screen = Screen.Topic(s.s, s.i, it, s.prev) }
            is Screen.Quiz -> QuizScreen(s.s, s.i, p) { screen = s.prev }
        }
    }
}

@Composable
fun Orb(size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(
            Brush.linearGradient(listOf(Color(0xFFFF8A4C), Color(0xFFC2185B), Color(0xFF7C4DFF)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(size / 6)) {
            repeat(2) {
                Box(Modifier.size(size / 11, size / 6).clip(RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp)).background(Color.White))
            }
        }
    }
}

@Composable
fun Welcome(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Image(
            painterResource(R.drawable.logo_full), "StudyTint logo",
            Modifier.fillMaxWidth(0.88f), contentScale = ContentScale.FillWidth
        )
        Spacer(Modifier.height(12.dp))
        Text("Your smart study companion", color = Color(0xFFB4B4BE), fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text("Python  •  Maths  •  Exploration in AI  •  DBMS", color = Color(0xFF8A8A94), textAlign = TextAlign.Center, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onStart, Modifier.fillMaxWidth().height(60.dp), shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
        ) { Text("Get started  →", fontSize = 17.sp) }
    }
}

@Composable
fun OptionCard(emoji: String, title: String, sub: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Ink)
            Text(sub, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun Home(p: Progress, go: (Screen) -> Unit) {
    val total = subjects.sumOf { it.units.size }
    val done = p.done.size
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Hi, Student", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text("What shall we do today?", color = Color.Gray)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Orb(96.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("$done of $total units done", fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { done / total.toFloat() }, color = Accent, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard("📖", "Study", "Read unit notes", Color(0xFF7C6CF0), Modifier.weight(1f)) { go(Screen.Pick(Mode.Study)) }
                OptionCard("✏️", "Practise", "Quiz yourself", Color(0xFFF2894E), Modifier.weight(1f)) { go(Screen.Pick(Mode.Practise)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard("🏆", "Score", "Your results", Color(0xFF3B9BE0), Modifier.weight(1f)) { go(Screen.Score) }
                OptionCard("⭐", "Highlighted", "Starred topics", Color(0xFFE0A800), Modifier.weight(1f)) { go(Screen.Stars) }
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(42.dp).clickable(onClick = onBack)) {
            Box(contentAlignment = Alignment.Center) { Text("←", fontSize = 20.sp) }
        }
        Text(title, Modifier.weight(1f).padding(end = 42.dp), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
    }
}

@Composable
fun PickScreen(m: Mode, p: Progress, back: () -> Unit, open: (Subject, Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(if (m == Mode.Study) "Study" else "Practise", back)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            subjects.forEach { s ->
                item(key = s.id) {
                    Text("${s.emoji}  ${s.name}", Modifier.padding(top = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = s.color)
                }
                items(s.units.indices.toList(), key = { "${s.id}:$it" }) { i ->
                    val key = "${s.id}:$i"
                    val tag = if (m == Mode.Study) (if (key in p.done) "✅" else "›")
                    else p.scores[key]?.let { "$it%" } ?: "›"
                    Card(
                        Modifier.fillMaxWidth().clickable { open(s, i) }, shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (m == Mode.Study && rich["${s.id}:$i"] != null) Color(0xFFEDEBFF) else Color.White)
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.units[i].title, fontSize = 16.sp, color = Ink)
                                val r = if (m == Mode.Study) rich["${s.id}:$i"] else null
                                if (r != null) Text("✨ Detailed notes · ${r.sec.size} topics", fontSize = 12.sp, color = Accent)
                            }
                            Text(tag, fontSize = 18.sp, color = Accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotesScreen(s: Subject, i: Int, p: Progress, back: () -> Unit) {
    val u = s.units[i]
    val key = "${s.id}:$i"
    val ok = key in p.done
    Column(Modifier.fillMaxSize()) {
        TopBar(s.name, back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Text(if (key in p.stars) "⭐" else "☆", Modifier.clickable { p.star(key) }.padding(8.dp), fontSize = 26.sp)
                }
            }
            items(u.notes) { n ->
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Text(n, Modifier.fillMaxWidth().padding(16.dp), fontSize = 15.sp, color = Ink)
                }
            }
        }
        Button(
            { p.toggle(key) }, Modifier.fillMaxWidth().padding(20.dp).height(56.dp), shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = if (ok) Color.White else Accent, contentColor = if (ok) Accent else Color.White)
        ) { Text(if (ok) "Completed ✓ (tap to undo)" else "Mark as completed") }
    }
}

@Composable
fun QuizScreen(s: Subject, i: Int, p: Progress, back: () -> Unit) {
    val unitQs = remember(s.id, i) { quiz[s.id].orEmpty().filter { it.unit == i } }
    val topics = remember(s.id, i) { unitQs.map { it.topic }.filter { it.isNotEmpty() }.distinct() }
    var mode by remember(s.id, i) { mutableStateOf<String?>(if (topics.isEmpty()) "" else null) }
    val qs = when (val m = mode) { null -> emptyList(); "" -> unitQs; else -> unitQs.filter { it.topic == m } }
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
        TopBar(s.units[i].title.substringAfter(": "), { if (mode != null && topics.isNotEmpty()) start(null) else back() })
        if (unitQs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("No questions yet.", color = Color.Gray) }
        } else if (mode == null) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(Modifier.fillMaxWidth().clickable { start("") }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Accent)) {
                        Text("All questions in order · ${unitQs.size}", Modifier.padding(18.dp), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                }
                item { Text("Topics", Modifier.padding(top = 6.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink) }
                items(topics) { t ->
                    Card(Modifier.fillMaxWidth().clickable { start(t) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t, Modifier.weight(1f), color = Ink)
                            Text("${unitQs.count { it.topic == t }}", color = Accent, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else if (idx >= qs.size) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Orb(120.dp)
                Spacer(Modifier.height(20.dp))
                Text("You scored $right/${qs.size}", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text("${right * 100 / qs.size}%", fontSize = 18.sp, color = Accent)
                Spacer(Modifier.height(24.dp))
                Button({ start(mode) }, Modifier.fillMaxWidth().height(54.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Try again") }
                if (topics.isNotEmpty()) TextButton({ start(null) }) { Text("Choose another topic", color = Accent) }
                TextButton(back) { Text("Done", color = Accent) }
            }
        } else {
            val q = qs[idx]
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Question ${idx + 1} of ${qs.size}" + if (q.topic.isNotEmpty()) "  ·  ${q.topic}" else "", color = Color.Gray, fontSize = 13.sp)
                    Text(q.q, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink)
                    q.o.forEachIndexed { n, label ->
                        val c = when {
                            sel == -1 -> Color.White
                            n == q.a -> Good
                            n == sel -> Bad
                            else -> Color.White
                        }
                        Surface(
                            Modifier.fillMaxWidth().clickable(enabled = sel == -1) { if (sel == -1) { sel = n; if (n == q.a) right++; p.record("${s.id}:$i", q.topic, n == q.a) } },
                            shape = RoundedCornerShape(16.dp), color = c
                        ) { Text(label, Modifier.padding(16.dp), color = Ink) }
                    }
                    if (sel != -1 && q.sol.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE9E9FA)) {
                            Text("💡 ${q.sol}", Modifier.fillMaxWidth().padding(14.dp), fontSize = 14.sp, color = Ink)
                        }
                    }
                }
                if (sel != -1) Button({ idx++; sel = -1 }, Modifier.fillMaxWidth().padding(20.dp).height(54.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text(if (idx == qs.size - 1) "Finish" else "Next") }
            }
        }
    }
}

@Composable
fun StarsScreen(p: Progress, back: () -> Unit, open: (Subject, Int) -> Unit) {
    val list = subjects.flatMap { s -> s.units.indices.filter { "${s.id}:$it" in p.stars }.map { s to it } }
    Column(Modifier.fillMaxSize()) {
        TopBar("Highlighted topics", back)
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Tap ☆ on any unit while studying to highlight it here.", color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list) { (s, i) ->
                Card(
                    Modifier.fillMaxWidth().clickable { open(s, i) }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(s.name, color = s.color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("⭐ ${s.units[i].title}", fontSize = 16.sp, color = Ink)
                    }
                }
            }
        }
    }
}
