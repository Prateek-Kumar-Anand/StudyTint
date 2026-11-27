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

private fun depth(s: Screen): Int = when (s) {
    Screen.Welcome -> 0
    Screen.Home -> 1
    is Screen.Setup -> if (s.edit) 2 else 1
    Screen.Score, Screen.CLab, Screen.PyLab, Screen.DbLab, is Screen.Pick -> 2
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
fun App(p: Progress) {
    // No welcome screen: open straight on Home, or on profile set-up the very first time (it has "Skip for
    // now").
    var screen by remember { mutableStateOf<Screen>(if (p.profile.isComplete || p.profileSkipped) Screen.Home
        else Screen.Setup(false)) }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    fun st(k: String): LazyListState = listStates.getOrPut(k) { LazyListState() }
    fun parent(s: Screen): Screen = when (s) {
        is Screen.Notes -> s.prev
        is Screen.RichNotes -> s.prev
        is Screen.Topic -> Screen.RichNotes(s.s, s.i, s.prev)
        is Screen.Quiz -> s.prev
        is Screen.Setup -> Screen.Home
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
            confirmButton = { TextButton(onClick = { Updates.markSeen(appCtx);
                whatsNew = null }) { Text("Got it") } }
        )
    }
    var locked by remember { mutableStateOf(Vault.problem) }
    if (locked) {
        AlertDialog(
            onDismissRequest = { locked = false },
            title = { Text("Saved data is locked") },
            text = { Text("Some saved data could not be unlocked, so changes to it will not be saved. Nothing has been deleted. Restart the phone and open the app again. If this message stays, clearing the app's storage in Android settings starts fresh, but erases your saved data.", fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { locked = false }) { Text("OK") } }
        )
    }
    val bg by animateColorAsState(if (screen == Screen.Welcome) Color(0xFF0B0B0B) else Bg, tween(400),
        label = "bg")
    Box(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        AnimatedContent(targetState = screen, transitionSpec = { navTransition(initialState, targetState) },
            label = "nav") { s ->
        when (s) {
            Screen.Welcome -> Welcome { screen = if (p.profile.isComplete || p.profileSkipped) Screen.Home
                else Screen.Setup(false) }
            is Screen.Setup -> SetupScreen(p, s.edit, { screen = Screen.Home }) { screen = Screen.Home }
            Screen.Home -> Home(p, st("home")) { screen = it }
            Screen.Score -> ScoreScreen(p, st("score")) { screen = Screen.Home }
            is Screen.Pick -> PickScreen(s.m, p, { screen = Screen.Home }, st("pick:${s.m}")) { sub, i ->
                screen = if (s.m == Mode.Study) (if (rich["${sub.id}:$i"] != null) Screen.RichNotes(sub, i,
                    s) else Screen.Notes(sub, i, s)) else Screen.Quiz(sub, i, s)
            }
            is Screen.Notes -> NotesScreen(s.s, s.i, p) { screen = s.prev }
            is Screen.RichNotes -> RichHome(s.s, s.i, p, { screen = s.prev },
                st("rich:${s.s.id}:${s.i}")) { screen = Screen.Topic(s.s, s.i, it, s.prev) }
            is Screen.Topic -> TopicScreen(s.s, s.i, s.idx, p,
                { screen = Screen.RichNotes(s.s, s.i, s.prev) }) { screen = Screen.Topic(s.s, s.i, it,
                s.prev) }
            is Screen.Quiz -> QuizScreen(s.s, s.i, p) { screen = s.prev }
            Screen.CLab -> CLabScreen { screen = Screen.Home }
            Screen.PyLab -> PyLabScreen { screen = Screen.Home }
            Screen.DbLab -> DbLabScreen { screen = Screen.Home }
        }
        }
    }
}
