package com.student.smartstudy

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.delay

object CEngine {
    private var loaded = false

    fun load(): Boolean {
        if (!loaded) {
            loaded = try {
                System.loadLibrary("clab")
                true
            } catch (e: Throwable) {
                false
            }
        }
        return loaded
    }

    /** mode: 1 = Turbo C (whole-program checker + Turbo C style messages), 0 = plain interpreter */
    external fun start(src: ByteArray, dir: String, timeoutMs: Int, mode: Int): Long
    /** [flags, output bytes...]; flags: 1 = waiting for input, 2 = finished. */
    external fun poll(handle: Long, waitMs: Int): ByteArray
    external fun write(handle: Long, data: ByteArray)
    external fun closeInput(handle: Long)
    external fun kill(handle: Long)
    external fun finish(handle: Long): String
}

/**
 * One run of a C program. Output is streamed to [onText] as it appears, [onWaiting] tells the screen when
 * the program is waiting for a line to be typed, and [onDone] gets the final status line.
 * send / eof / stop may be called from the UI thread at any time.
 */
class CRun(
    private val ctx: Context,
    private val code: String,
    private val timeoutMs: Int,
    private val turboC: Boolean,
    private val onText: (String) -> Unit,
    private val onWaiting: (Boolean) -> Unit,
    private val onDone: (String) -> Unit
) {
    private val lock = Any()
    private var handle = 0L

    fun start() {
        Thread(null, Runnable { runLoop() }, "clab-run", 64L * 1024 * 1024).start()
    }

    private fun runLoop() {
        try {
            if (!CEngine.load()) {
                onDone("C engine is not available on this device")
                return
            }
            val dir = File(ctx.cacheDir, "clab")
            dir.mkdirs()
            val h = synchronized(lock) {
                val v = CEngine.start(code.toByteArray(Charsets.UTF_8), dir.absolutePath, timeoutMs, if (turboC) 1 else 0)
                handle = v
                v
            }
            if (h == 0L) {
                onDone("could not start program")
                return
            }
            val decoder = Utf8Chunker()
            var wasWaiting = false
            while (true) {
                val r = CEngine.poll(h, 60)
                val flags = if (r.isNotEmpty()) r[0].toInt() else 2
                if (r.size > 1) onText(decoder.feed(r.copyOfRange(1, r.size)))
                val w = (flags and 1) != 0
                if (w != wasWaiting) { wasWaiting = w; onWaiting(w) }
                if ((flags and 2) != 0) break
            }
            val status = synchronized(lock) {
                val st = CEngine.finish(h)
                handle = 0L
                st
            }
            onWaiting(false)
            onDone(status)
        } catch (e: Throwable) {
            onWaiting(false)
            onDone("Could not run: " + (e.message ?: "error"))
        }
    }

    fun send(line: String) {
        synchronized(lock) { if (handle != 0L) CEngine.write(handle, (line + "\n").toByteArray(Charsets.UTF_8)) }
    }

    fun eof() {
        synchronized(lock) { if (handle != 0L) CEngine.closeInput(handle) }
    }

    fun stop() {
        synchronized(lock) { if (handle != 0L) CEngine.kill(handle) }
    }
}

private val cKeywords = setOf(
    "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
    "extern", "float", "for", "goto", "if", "int", "long", "register", "return", "short", "signed",
    "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
    "near", "far", "huge", "cdecl", "pascal", "interrupt"
)

private val EditorBg = Color(0xFF1E1E2E)
private val EditorFg = Color(0xFFE6E6F0)

private class CHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        if (s.length > 40_000) return TransformedText(text, OffsetMapping.Identity)   // colouring a huge text on every key press would make typing lag
        val b = AnnotatedString.Builder(s)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                var j = i
                while (j < s.length && s[j] != '\n') j++
                b.addStyle(SpanStyle(color = Color(0xFF7F8C9B)), i, j)
                i = j
            } else if (c == '/' && i + 1 < s.length && s[i + 1] == '*') {
                var j = s.indexOf("*/", i + 2)
                j = if (j < 0) s.length else j + 2
                b.addStyle(SpanStyle(color = Color(0xFF7F8C9B)), i, j)
                i = j
            } else if (c == '#') {
                var j = i
                while (j < s.length && s[j] != '\n') j++
                b.addStyle(SpanStyle(color = Color(0xFFC792EA)), i, j)
                i = j
            } else if (c == '"' || c == '\'') {
                var j = i + 1
                while (j < s.length && s[j] != c && s[j] != '\n') {
                    if (s[j] == '\\') j++
                    j++
                }
                j = minOf(j + 1, s.length)
                b.addStyle(SpanStyle(color = Color(0xFFC3E88D)), i, j)
                i = j
            } else if (c.isDigit()) {
                var j = i
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '.')) j++
                b.addStyle(SpanStyle(color = Color(0xFFF78C6C)), i, j)
                i = j
            } else if (c.isLetter() || c == '_') {
                var j = i
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                if (s.substring(i, j) in cKeywords) {
                    b.addStyle(SpanStyle(color = Color(0xFF82AAFF), fontWeight = FontWeight.Bold), i, j)
                }
                i = j
            } else {
                i++
            }
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private val samples = linkedMapOf(
    "Hello" to """#include <stdio.h>
#include <conio.h>

void main()
{
    clrscr();
    printf("Hello, World!\n");
}
""",
    "Input" to """#include <stdio.h>
#include <conio.h>

void main()
{
    int a, b;
    clrscr();
    printf("Enter two numbers: ");
    scanf("%d %d", &a, &b);
    printf("Sum = %d\n", a + b);
}
""",
    "Loop" to """#include <stdio.h>

int main()
{
    int i, j, n = 5;
    for (i = 1; i <= n; i++) {
        for (j = 1; j <= i; j++) printf("* ");
        printf("\n");
    }
    return 0;
}
""",
    "Array" to """#include <stdio.h>

int main()
{
    int a[6] = {42, 7, 19, 3, 25, 11};
    int n = 6, i, j, t;
    for (i = 0; i < n - 1; i++)
        for (j = 0; j < n - i - 1; j++)
            if (a[j] > a[j + 1]) {
                t = a[j]; a[j] = a[j + 1]; a[j + 1] = t;
            }
    for (i = 0; i < n; i++) printf("%d ", a[i]);
    printf("\n");
    return 0;
}
""",
    "Struct" to """#include <stdio.h>
#include <string.h>

struct Student {
    char name[30];
    int marks;
};

int main()
{
    struct Student s;
    strcpy(s.name, "Asha");
    s.marks = 91;
    printf("%s scored %d\n", s.name, s.marks);
    return 0;
}
""",
    "Pointer" to """#include <stdio.h>
#include <stdlib.h>

void swap(int *a, int *b)
{
    int t = *a; *a = *b; *b = t;
}

int main()
{
    int x = 10, y = 20, i;
    int *p;
    swap(&x, &y);
    printf("x=%d y=%d\n", x, y);
    p = (int *)malloc(3 * sizeof(int));
    for (i = 0; i < 3; i++) p[i] = i * i;
    printf("%d %d %d\n", p[0], p[1], p[2]);
    free(p);
    return 0;
}
""",
    "Recursion" to """#include <stdio.h>

int fact(int n)
{
    return n <= 1 ? 1 : n * fact(n - 1);
}

int fib(int n)
{
    if (n < 2) return n;
    return fib(n - 1) + fib(n - 2);
}

int main()
{
    int i;
    printf("5! = %d\n", fact(5));
    for (i = 0; i < 10; i++) printf("%d ", fib(i));
    printf("\n");
    return 0;
}
""",
    "TC errors" to """#include <stdio.h>

int add(int a, int b)
{
    return a + b;
}

void main()
{
    int x = 5, y;
    float f;
    y = add(x);
    z = 10;
    printf("%d", y)
    for (int i = 0; i < 3; i++)
        printf("%d", i);
    undefined_fn(3);
}
"""
)

private val symbols = listOf("{", "}", "(", ")", "[", "]", ";", "\"", "'", "#", "<", ">", "=", "&", "*", "+", "-", "/", "%", "!", "|", ",", "\\n", "    ")

@Composable
fun CLabScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("clab_prefs", Context.MODE_PRIVATE) }
    var code by remember { mutableStateOf(TextFieldValue(prefs.getString("code", samples["Hello"]) ?: "")) }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var turboC by remember { mutableStateOf(prefs.getBoolean("turbo_c", true)) }
    var running by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    val outLock = remember { Any() }
    val current = remember { arrayOfNulls<CRun>(1) }
    val scroll = rememberScrollState()
    val highlight = remember { CHighlight() }
    val mono = FontFamily.Monospace

    // leaving the screen stops a program that is still running
    DisposableEffect(Unit) { onDispose { current[0]?.stop() } }
    // keep the prompt in view when the program asks for input
    LaunchedEffect(waiting) {
        if (waiting) {
            delay(150)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    fun appendOut(t: String) {
        synchronized(outLock) { output += t }
    }

    fun setCode(v: TextFieldValue) {
        code = v
        prefs.edit().putString("code", v.text).apply()
    }

    fun insert(t: String) {
        val sel = code.selection
        val text = code.text.substring(0, sel.min) + t + code.text.substring(sel.max)
        setCode(TextFieldValue(text, TextRange(sel.min + t.length)))
    }

    fun runCode() {
        if (running) return
        running = true
        waiting = false
        output = ""
        status = "Running…"
        val src = code.text
        prefs.edit().putString("code", src).apply()
        val run = CRun(
            ctx.applicationContext, src, 10000, turboC,
            onText = { appendOut(it) },
            onWaiting = { waiting = it },
            onDone = { st ->
                val out = synchronized(outLock) { output }
                val errs = if (turboC)
                    Regex("(?m)^(Error NONAME00\\.C \\d+:|Linker Error:)").findAll(out).count()
                else Regex("(?m)^\\S+:\\d+:\\d+: error:").findAll(out).count()
                val warns = if (turboC)
                    Regex("(?m)^Warning NONAME00\\.C \\d+:").findAll(out).count()
                else Regex("(?m)^\\S+:\\d+:\\d+: warning:").findAll(out).count()
                val abnormal = turboC && out.contains("Abnormal program termination")
                status = when {
                    errs > 0 -> (if (turboC) "compile failed: " else "build failed: ") + "$errs error${if (errs > 1) "s" else ""}" + (if (warns > 0) ", $warns warning${if (warns > 1) "s" else ""}" else "")
                    abnormal -> "Abnormal program termination"
                    warns > 0 && st.endsWith("code 0") -> st + " ($warns warning${if (warns > 1) "s" else ""})"
                    else -> st
                }
                current[0] = null
                waiting = false
                running = false
            }
        )
        current[0] = run
        run.start()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("C Lab", back)
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to "Turbo C", false to "gcc style").forEach { (tc, label) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (turboC == tc) Color(0xFF2FA37A) else Color.White,
                        modifier = Modifier.clickable(enabled = !running) {
                            turboC = tc
                            prefs.edit().putBoolean("turbo_c", tc).apply()
                        }
                    ) {
                        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp,
                            color = if (turboC == tc) Color.White else Ink, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                samples.forEach { (name, src) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = Color.White,
                        modifier = Modifier.clickable { setCode(TextFieldValue(src, TextRange(src.length))) }
                    ) {
                        Text(name, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, color = Ink)
                    }
                }
            }

            BasicTextField(
                value = code,
                onValueChange = { setCode(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp)
                    .background(EditorBg, RoundedCornerShape(16.dp)).padding(14.dp),
                textStyle = TextStyle(fontFamily = mono, fontSize = 14.sp, color = EditorFg, lineHeight = 20.sp),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = highlight,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false)
            )

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                symbols.forEach { sym ->
                    Surface(
                        shape = RoundedCornerShape(8.dp), color = Color(0xFFE9E9FA),
                        modifier = Modifier.clickable { insert(if (sym == "\\n") "\\n" else sym) }
                    ) {
                        Text(if (sym.isBlank()) "Tab" else sym, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontFamily = mono, fontSize = 14.sp, color = Ink)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { runCode() }, enabled = !running,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FA37A))
                ) { Text(if (running) "Running…" else "▶  Run", fontSize = 16.sp) }
                OutlinedButton(
                    onClick = { current[0]?.stop() }, enabled = running,
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Stop") }
                Button(
                    onClick = { output = ""; status = "" }, enabled = !running,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
                ) { Text("Clear") }
            }

            ConsoleBox(
                output = remember(output) { colorDiagnostics(output) },
                status = status,
                statusOk = status.endsWith("code 0") || status.contains("code 0 (") || status == "Running…",
                running = running,
                waiting = waiting,
                onSubmit = { line ->
                    appendOut(line + "\n")        // echo what was typed, like a terminal
                    current[0]?.send(line)
                },
                onEof = { current[0]?.eof() }
            )
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

private val diagError = Regex("^\\S+:\\d+:\\d+: error:.*")
private val diagWarn = Regex("^\\S+:\\d+:\\d+: warning:.*")
private val diagCaret = Regex("^\\s*\\|\\s*\\^.*")
private val tcError = Regex("^(Error NONAME00\\.C \\d+:|Linker Error:).*")
private val tcWarn = Regex("^Warning NONAME00\\.C \\d+:.*")
private val tcHead = Regex("^(Compiling NONAME00\\.C:|Linking NONAME00\\.EXE:|\\s+Errors: \\d+\\s+Warnings: \\d+.*)$")
private val tcAbort = Regex("^(Abnormal program termination|Divide error|Floating point error:.*|Stack overflow!|Null pointer assignment|scanf : floating point formats not linked|C Lab could not.*)$")

private fun mayBeDiagnostic(l: String): Boolean =
    l.length < 400 && (l.contains("rror") || l.contains("arning") || l.startsWith("Compiling") || l.startsWith("Linking") ||
        l.startsWith("Abnormal") || l.startsWith("Divide") || l.startsWith("Floating") || l.startsWith("Stack") ||
        l.startsWith("Null") || l.startsWith("scanf") || l.startsWith("C Lab") || l.trimStart().startsWith("|") || l.trimStart().startsWith("Errors"))

private fun colorDiagnostics(out: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val lines = out.split("\n")
        lines.forEachIndexed { i, l ->
            // most lines are ordinary program output: skip the seven regular expressions for them
            val style = if (!mayBeDiagnostic(l)) null else when {
                tcError.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                tcWarn.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFD166), fontWeight = FontWeight.Bold)
                tcHead.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFF8AB4F8), fontWeight = FontWeight.Bold)
                tcAbort.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                diagError.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                diagWarn.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFD166), fontWeight = FontWeight.Bold)
                diagCaret.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFF7EE0A8), fontWeight = FontWeight.Bold)
                else -> null
            }
            if (style != null) withStyle(style) { append(l) } else append(l)
            if (i < lines.lastIndex) append("\n")
        }
    }
