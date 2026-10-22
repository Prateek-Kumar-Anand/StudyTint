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
    object CLab : Screen
    object PyLab : Screen
    data class Setup(val edit: Boolean) : Screen
    data class Pick(val m: Mode) : Screen
    data class Notes(val s: Subject, val i: Int, val prev: Screen) : Screen
    data class RichNotes(val s: Subject, val i: Int, val prev: Screen) : Screen
    data class Topic(val s: Subject, val i: Int, val idx: Int, val prev: Screen) : Screen
    data class Quiz(val s: Subject, val i: Int, val prev: Screen) : Screen
}

private fun depth(s: Screen): Int = when (s) {
    Screen.Welcome -> 0
    Screen.Home -> 1
    is Screen.Setup -> if (s.edit) 2 else 1
    Screen.Score, Screen.Stars, Screen.CLab, Screen.PyLab, is Screen.Pick -> 2
    is Screen.Notes, is Screen.RichNotes, is Screen.Quiz -> 3
    is Screen.Topic -> 4
}

private fun order(s: Screen): Int = if (s is Screen.Topic) s.idx else 0

// `using` is a member of AnimatedContentTransitionScope, so this must be an extension on it.
private fun AnimatedContentTransitionScope<Screen>.navTransition(from: Screen, to: Screen): ContentTransform {
    val fwd = depth(to) > depth(from) || (depth(to) == depth(from) && order(to) >= order(from))
    val d = if (fwd) 1 else -1
    val spec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
    return (slideInHorizontally(spec) { it * d / 8 } + fadeIn(tween(260, delayMillis = 30))) togetherWith
        (slideOutHorizontally(spec) { -it * d / 8 } + fadeOut(tween(140))) using SizeTransform(clip = false)
}

@Composable
fun SubjectBadge(s: Subject) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(s.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(s.mark, color = s.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun App(p: Progress) {
    var screen by remember { mutableStateOf<Screen>(Screen.Welcome) }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    fun st(k: String): LazyListState = listStates.getOrPut(k) { LazyListState() }
    fun parent(s: Screen): Screen = when (s) {
        is Screen.Notes -> s.prev
        is Screen.RichNotes -> s.prev
        is Screen.Topic -> Screen.RichNotes(s.s, s.i, s.prev)
        is Screen.Quiz -> s.prev
        is Screen.Setup -> if (s.edit) Screen.Home else Screen.Welcome
        else -> Screen.Home
    }
    BackHandler(screen != Screen.Welcome && screen != Screen.Home) { screen = parent(screen) }
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    var whatsNew by remember { mutableStateOf(Updates.pendingRelease(appCtx)) }
    val notes = whatsNew
    if (notes != null && screen == Screen.Home) {
        AlertDialog(
            onDismissRequest = { Updates.markSeen(appCtx); whatsNew = null },
            title = { Text("What's new in ${notes.name}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { notes.notes.forEach { Text("•  $it", fontSize = 14.sp) } } },
            confirmButton = { TextButton(onClick = { Updates.markSeen(appCtx); whatsNew = null }) { Text("Got it") } }
        )
    }
    val bg by animateColorAsState(if (screen == Screen.Welcome) Color(0xFF0B0B0B) else Bg, tween(400), label = "bg")
    Box(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        AnimatedContent(targetState = screen, transitionSpec = { navTransition(initialState, targetState) }, label = "nav") { s ->
        when (s) {
            Screen.Welcome -> Welcome { screen = if (p.profile.isComplete) Screen.Home else Screen.Setup(false) }
            is Screen.Setup -> SetupScreen(p, s.edit, { screen = Screen.Home }) { screen = Screen.Home }
            Screen.Home -> Home(p, st("home")) { screen = it }
            Screen.Score -> ScoreScreen(p, st("score")) { screen = Screen.Home }
            Screen.Stars -> StarsScreen(p, { screen = Screen.Home }, st("stars")) { sub, i -> screen = if (rich["${sub.id}:$i"] != null) Screen.RichNotes(sub, i, Screen.Stars) else Screen.Notes(sub, i, Screen.Stars) }
            is Screen.Pick -> PickScreen(s.m, p, { screen = Screen.Home }, st("pick:${s.m}")) { sub, i ->
                screen = if (s.m == Mode.Study) (if (rich["${sub.id}:$i"] != null) Screen.RichNotes(sub, i, s) else Screen.Notes(sub, i, s)) else Screen.Quiz(sub, i, s)
            }
            is Screen.Notes -> NotesScreen(s.s, s.i, p) { screen = s.prev }
            is Screen.RichNotes -> RichHome(s.s, s.i, p, { screen = s.prev }, st("rich:${s.s.id}:${s.i}")) { screen = Screen.Topic(s.s, s.i, it, s.prev) }
            is Screen.Topic -> TopicScreen(s.s, s.i, s.idx, p, { screen = Screen.RichNotes(s.s, s.i, s.prev) }) { screen = Screen.Topic(s.s, s.i, it, s.prev) }
            is Screen.Quiz -> QuizScreen(s.s, s.i, p) { screen = s.prev }
            Screen.CLab -> CLabScreen { screen = Screen.Home }
            Screen.PyLab -> PyLabScreen { screen = Screen.Home }
        }
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
        Text("Python  •  Maths  •  C  •  Exploration in AI  •  DBMS", color = Color(0xFF8A8A94), textAlign = TextAlign.Center, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onStart, Modifier.fillMaxWidth().height(60.dp), shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
        ) { Text("Get started  →", fontSize = 17.sp) }
    }
}

@Composable
fun OptionCard(title: String, sub: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.tap(RoundedCornerShape(22.dp), onClick), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.width(28.dp).height(4.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.height(18.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Ink)
            Text(sub, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun Home(p: Progress, listState: LazyListState, go: (Screen) -> Unit) {
    val total = subjects.sumOf { it.units.size }
    val done = p.done.size
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "greeting") {
            Text("Hi, ${p.profile.name.trim().substringBefore(' ').ifBlank { "Student" }}", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text("What shall we do today?", color = Color.Gray)
        }
        item(key = "profile") { ProfileCard(p.profile, p.photo) { go(Screen.Setup(true)) } }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Orb(96.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("$done of $total units done", fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        val hp by animateFloatAsState(done / total.toFloat(), tween(450), label = "hp")
                        LinearProgressIndicator(progress = { hp }, color = Accent, trackColor = Color(0xFFE9E9F2), modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard("Study", "Read unit notes", Color(0xFF7C6CF0), Modifier.weight(1f)) { go(Screen.Pick(Mode.Study)) }
                OptionCard("Practise", "Quiz yourself", Color(0xFFF2894E), Modifier.weight(1f)) { go(Screen.Pick(Mode.Practise)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard("Score", "Your results", Color(0xFF3B9BE0), Modifier.weight(1f)) { go(Screen.Score) }
                OptionCard("Highlighted", "Starred topics", Color(0xFFE0A800), Modifier.weight(1f)) { go(Screen.Stars) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard("C Lab", "Write & run C code", Color(0xFF2FA37A), Modifier.weight(1f)) { go(Screen.CLab) }
                OptionCard("Python Lab", "Write & run Python", Color(0xFF3776AB), Modifier.weight(1f)) { go(Screen.PyLab) }
            }
        }
        item(key = "version") {
            val vctx = androidx.compose.ui.platform.LocalContext.current
            Text("StudyTint v${Updates.versionName(vctx)}", Modifier.fillMaxWidth().padding(top = 4.dp), color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(42.dp).tap(CircleShape, onBack)) {
            Box(contentAlignment = Alignment.Center) { Text("←", fontSize = 20.sp) }
        }
        Text(title, Modifier.weight(1f).padding(end = 42.dp), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
    }
}

@Composable
fun PickScreen(m: Mode, p: Progress, back: () -> Unit, listState: LazyListState, open: (Subject, Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(if (m == Mode.Study) "Study" else "Practise", back)
        LazyColumn(state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Modifier.fillMaxWidth().tap(RoundedCornerShape(20.dp)) { open(s, i) }, shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (m == Mode.Study && rich["${s.id}:$i"] != null) Color(0xFFEDEBFF) else Color.White)
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.units[i].title, fontSize = 16.sp, color = Ink)
                                val r = if (m == Mode.Study) rich["${s.id}:$i"] else null
                                if (r != null) Text("Detailed notes · ${r.sec.size} topics", fontSize = 12.sp, color = Accent)
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
                    Text(if (key in p.stars) "★" else "☆", Modifier.clickable { p.star(key) }.padding(8.dp), fontSize = 26.sp)
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
                    Card(Modifier.fillMaxWidth().tap(RoundedCornerShape(20.dp)) { start("") }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Accent)) {
                        Text("All questions in order · ${unitQs.size}", Modifier.padding(18.dp), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                }
                item { Text("Topics", Modifier.padding(top = 6.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink) }
                items(topics) { t ->
                    Card(Modifier.fillMaxWidth().tap(RoundedCornerShape(18.dp)) { start(t) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                Text(if (qs.first().a == -1) "Confident on $right of ${qs.size}" else "You scored $right/${qs.size}", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
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
                    Text(q.q, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = Ink)
                    if (q.a == -1) Text("Think of your answer first, then rate yourself.", color = Color.Gray, fontSize = 13.sp)
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
                            Modifier.fillMaxWidth().clickable(enabled = sel == -1) { if (sel == -1) { sel = n; val ok = if (q.a == -1) n == 0 else n == q.a; if (ok) right++; p.record("${s.id}:$i", q.topic, ok) } },
                            shape = RoundedCornerShape(16.dp), color = cc
                        ) { Text(label, Modifier.padding(16.dp), color = Ink) }
                    }
                    AnimatedVisibility(visible = sel != -1 && q.sol.isNotEmpty(), enter = fadeIn(tween(250)) + expandVertically(tween(250))) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE9E9FA)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("Explanation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                                Text(q.sol, fontSize = 14.sp, lineHeight = 20.sp, color = Ink)
                            }
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
fun StarsScreen(p: Progress, back: () -> Unit, listState: LazyListState, open: (Subject, Int) -> Unit) {
    val list = subjects.flatMap { s -> s.units.indices.filter { "${s.id}:$it" in p.stars }.map { s to it } }
    Column(Modifier.fillMaxSize()) {
        TopBar("Highlighted topics", back)
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Tap ☆ on any unit while studying to highlight it here.", color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else LazyColumn(state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { (s, i) -> "${s.id}:$i" }) { (s, i) ->
                Card(
                    Modifier.fillMaxWidth().tap(RoundedCornerShape(20.dp)) { open(s, i) }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(s.name, color = s.color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(s.units[i].title, fontSize = 16.sp, color = Ink)
                    }
                }
            }
        }
    }
}
