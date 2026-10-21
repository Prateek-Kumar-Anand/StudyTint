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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

object CEngine {
    private var loaded = false

    private fun load(): Boolean {
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

    external fun run(src: ByteArray, input: ByteArray, dir: String, timeoutMs: Int): ByteArray

    /** Returns (program output, status line). */
    fun execute(ctx: Context, code: String, input: String, timeoutMs: Int = 10000): Pair<String, String> {
        if (!load()) return Pair("", "C engine is not available on this device")
        val dir = File(ctx.cacheDir, "clab")
        dir.mkdirs()
        val raw = run(code.toByteArray(Charsets.UTF_8), input.toByteArray(Charsets.UTF_8), dir.absolutePath, timeoutMs)
        val cut = raw.lastIndexOf(0.toByte())
        return if (cut < 0) {
            Pair(String(raw, Charsets.UTF_8), "")
        } else {
            Pair(String(raw, 0, cut, Charsets.UTF_8), String(raw, cut + 1, raw.size - cut - 1, Charsets.UTF_8))
        }
    }
}

private val cKeywords = setOf(
    "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
    "extern", "float", "for", "goto", "if", "int", "long", "register", "return", "short", "signed",
    "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while"
)

private val EditorBg = Color(0xFF1E1E2E)
private val EditorFg = Color(0xFFE6E6F0)

private class CHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
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

int main() {
    printf("Hello, World!\n");
    return 0;
}
""",
    "Input" to """#include <stdio.h>

int main() {
    int a, b;
    printf("Enter two numbers: ");
    scanf("%d %d", &a, &b);
    printf("Sum = %d\n", a + b);
    return 0;
}
""",
    "Loop" to """#include <stdio.h>

int main() {
    int n = 5;
    for (int i = 1; i <= n; i++) {
        for (int j = 1; j <= i; j++) printf("* ");
        printf("\n");
    }
    return 0;
}
""",
    "Array" to """#include <stdio.h>

int main() {
    int a[6] = {42, 7, 19, 3, 25, 11};
    int n = 6;
    for (int i = 0; i < n - 1; i++)
        for (int j = 0; j < n - i - 1; j++)
            if (a[j] > a[j + 1]) {
                int t = a[j]; a[j] = a[j + 1]; a[j + 1] = t;
            }
    for (int i = 0; i < n; i++) printf("%d ", a[i]);
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

int main() {
    struct Student s;
    strcpy(s.name, "Asha");
    s.marks = 91;
    printf("%s scored %d\n", s.name, s.marks);
    return 0;
}
""",
    "Pointer" to """#include <stdio.h>
#include <stdlib.h>

void swap(int *a, int *b) {
    int t = *a; *a = *b; *b = t;
}

int main() {
    int x = 10, y = 20;
    swap(&x, &y);
    printf("x=%d y=%d\n", x, y);
    int *p = (int *)malloc(3 * sizeof(int));
    for (int i = 0; i < 3; i++) p[i] = i * i;
    printf("%d %d %d\n", p[0], p[1], p[2]);
    free(p);
    return 0;
}
""",
    "Recursion" to """#include <stdio.h>

int fact(int n) {
    return n <= 1 ? 1 : n * fact(n - 1);
}

int fib(int n) {
    if (n < 2) return n;
    return fib(n - 1) + fib(n - 2);
}

int main() {
    printf("5! = %d\n", fact(5));
    for (int i = 0; i < 10; i++) printf("%d ", fib(i));
    printf("\n");
    return 0;
}
"""
)

private val symbols = listOf("{", "}", "(", ")", "[", "]", ";", "\"", "'", "#", "<", ">", "=", "&", "*", "+", "-", "/", "%", "!", "|", ",", "\\n", "    ")

@Composable
fun CLabScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("clab_prefs", Context.MODE_PRIVATE) }
    var code by remember { mutableStateOf(TextFieldValue(prefs.getString("code", samples["Hello"]) ?: "")) }
    var input by remember { mutableStateOf(prefs.getString("input", "") ?: "") }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val highlight = remember { CHighlight() }
    val mono = FontFamily.Monospace

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
        output = ""
        status = "Running…"
        val src = code.text
        val stdin = input
        prefs.edit().putString("code", src).putString("input", stdin).apply()
        Thread(null, Runnable {
            val res = try {
                CEngine.execute(ctx.applicationContext, src, stdin)
            } catch (e: Throwable) {
                Pair("", "Could not run: " + (e.message ?: "error"))
            }
            output = res.first
            val errs = Regex("(?m)^\\S+:\\d+:\\d+: error:").findAll(res.first).count()
            val warns = Regex("(?m)^\\S+:\\d+:\\d+: warning:").findAll(res.first).count()
            status = when {
                errs > 0 -> "build failed: $errs error${if (errs > 1) "s" else ""}" + (if (warns > 0) ", $warns warning${if (warns > 1) "s" else ""}" else "")
                warns > 0 && res.second.endsWith("code 0") -> res.second + " ($warns warning${if (warns > 1) "s" else ""})"
                else -> res.second
            }
            running = false
        }, "clab-run", 64L * 1024 * 1024).start()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("C Lab", back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            Text("Input for scanf (optional)", fontSize = 13.sp, color = Color.Gray)
            BasicTextField(
                value = input,
                onValueChange = { input = it; prefs.edit().putString("input", it).apply() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp),
                textStyle = TextStyle(fontFamily = mono, fontSize = 14.sp, color = Ink)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { runCode() }, enabled = !running,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FA37A))
                ) { Text(if (running) "Running…" else "▶  Run", fontSize = 16.sp) }
                Button(
                    onClick = { output = ""; status = "" },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
                ) { Text("Clear") }
            }

            Column(
                Modifier.fillMaxWidth().heightIn(min = 120.dp)
                    .background(Color(0xFF111118), RoundedCornerShape(16.dp)).padding(14.dp)
            ) {
                Text("Output", fontSize = 12.sp, color = Color(0xFF8A8A94))
                Spacer(Modifier.padding(top = 6.dp))
                SelectionContainer {
                    Text(remember(output) { colorDiagnostics(output) }, fontFamily = mono, fontSize = 13.sp, color = Color(0xFFE6E6F0), lineHeight = 19.sp)
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 8.dp))
                    val ok = (status.endsWith("code 0") || status.contains("code 0 (") || status == "Running…")
                    Text(status, fontFamily = mono, fontSize = 12.sp, color = if (ok) Color(0xFF7EE0A8) else Color(0xFFFFB86C))
                }
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

private val diagError = Regex("^\\S+:\\d+:\\d+: error:.*")
private val diagWarn = Regex("^\\S+:\\d+:\\d+: warning:.*")
private val diagCaret = Regex("^\\s*\\|\\s*\\^.*")

private fun colorDiagnostics(out: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val lines = out.split("\n")
        lines.forEachIndexed { i, l ->
            val style = when {
                diagError.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                diagWarn.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFD166), fontWeight = FontWeight.Bold)
                diagCaret.matches(l) -> androidx.compose.ui.text.SpanStyle(color = Color(0xFF7EE0A8), fontWeight = FontWeight.Bold)
                else -> null
            }
            if (style != null) withStyle(style) { append(l) } else append(l)
            if (i < lines.lastIndex) append("\n")
        }
    }
