package com.student.smartstudy

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.IOException

// ---------------------------------------------------------------- storage

enum class PyStore(val label: String, val hint: String) {
    Internal("Internal storage", "Private to this app. Always available."),
    Shared("Phone storage", "App folder on shared storage, visible in a file manager under Android/data. Other apps may be able to change files here, so keep only code you trust."),
    Sd("SD card", "App folder on the SD card. Needs a card to be inserted. Other apps may be able to change files here, so keep only code you trust.")
}

object PyStorage {
    private const val PREF = "pylab_prefs"

    /** The base directory for a storage choice, or null when it is not available right now. */
    fun baseFor(ctx: Context, kind: PyStore): File? = when (kind) {
        PyStore.Internal -> ctx.filesDir
        PyStore.Shared -> ctx.getExternalFilesDirs(null).filterNotNull()
            .firstOrNull { !Environment.isExternalStorageRemovable(it) }
        PyStore.Sd -> ctx.getExternalFilesDirs(null).filterNotNull()
            .firstOrNull { Environment.isExternalStorageRemovable(it) }
    }

    fun kind(ctx: Context): PyStore {
        val name = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("store", PyStore.Internal.name)
        val k = PyStore.values().firstOrNull { it.name == name } ?: PyStore.Internal
        return if (baseFor(ctx, k) != null) k else PyStore.Internal
    }

    fun root(ctx: Context, kind: PyStore = kind(ctx)): File {
        val base = baseFor(ctx, kind) ?: ctx.filesDir
        return File(base, "PyLab").also { it.mkdirs() }
    }

    fun libs(root: File) = File(root, "libs").also { it.mkdirs() }
    fun scripts(root: File) = File(root, "scripts").also { it.mkdirs() }
    fun work(root: File) = File(root, "work").also { it.mkdirs() }

    /** Switches storage. Existing files are copied across (never overwritten) so nothing is lost. */
    fun switchTo(ctx: Context, to: PyStore): String {
        val from = kind(ctx)
        if (from == to) return "Already using ${to.label}."
        if (baseFor(ctx, to) == null) return "${to.label} is not available."
        return try {
            val src = root(ctx, from)
            val dst = root(ctx, to)
            if (src.exists()) src.copyRecursively(dst, overwrite = false)
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("store", to.name).apply()
            "Now using ${to.label}. Your files were copied across."
        } catch (e: Exception) {
            "Could not switch: ${e.message}"
        }
    }
}

// ---------------------------------------------------------------- libraries

data class LibItem(val name: String, val isDir: Boolean, val bytes: Long)

object PyLibs {
    private val archives = listOf(".zip", ".whl", ".egg")

    fun list(libs: File): List<LibItem> =
        (libs.listFiles() ?: emptyArray()).sortedBy { it.name.lowercase() }
            .map { LibItem(it.name, it.isDirectory, if (it.isDirectory) it.walkTopDown().filter { f -> f.isFile }.sumOf { f -> f.length() } else it.length()) }

    /** Entries for sys.path: the libs folder plus each .zip/.whl/.egg inside it. */
    fun sysPath(libs: File): List<String> =
        listOf(libs.absolutePath) + (libs.listFiles() ?: emptyArray())
            .filter { f -> f.isFile && archives.any { f.name.lowercase().endsWith(it) } }
            .map { it.absolutePath }

    fun delete(libs: File, name: String): Boolean {
        val f = File(libs, name)
        return f.canonicalPath.startsWith(libs.canonicalPath + File.separator) && f.deleteRecursively()
    }

    private const val MAX_BYTES = 150L * 1024 * 1024
    private val skip = setOf(".git", "__pycache__", ".DS_Store", ".idea")

    private fun safe(name: String): String = name.replace('/', '_').replace('\\', '_').trim().ifEmpty { "unnamed" }.let { if (it == "." || it == "..") "_" else it }

    private fun copyDoc(ctx: Context, doc: DocumentFile, dest: File, total: LongArray) {
        if (doc.isDirectory) {
            if ((doc.name ?: "") in skip) return
            dest.mkdirs()
            for (child in doc.listFiles()) copyDoc(ctx, child, File(dest, safe(child.name ?: "unnamed")), total)
        } else {
            ctx.contentResolver.openInputStream(doc.uri)?.use { input ->
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total[0] += n.toLong()
                        if (total[0] > MAX_BYTES) throw IOException("That is larger than 150 MB")
                        out.write(buf, 0, n)
                    }
                }
            }
        }
    }

    /** Copies a library folder (or a folder of libraries) from phone or SD card storage. */
    fun importTree(ctx: Context, tree: Uri, libs: File): String {
        val doc = DocumentFile.fromTreeUri(ctx, tree) ?: return "Could not open that folder."
        val name = safe(doc.name ?: "library")
        val total = LongArray(1)
        return try {
            if (doc.findFile("__init__.py") != null) {
                copyDoc(ctx, doc, File(libs, name), total)
                "Added library \"$name\"."
            } else {
                val kids = doc.listFiles()
                for (k in kids) copyDoc(ctx, k, File(libs, safe(k.name ?: "unnamed")), total)
                "Added ${kids.size} item${if (kids.size == 1) "" else "s"} from \"$name\"."
            }
        } catch (e: Exception) {
            "Import stopped: ${e.message}"
        }
    }

    /** Copies single .py / .zip / .whl / .egg files. */
    fun importFiles(ctx: Context, uris: List<Uri>, libs: File): String {
        var ok = 0
        val rejected = mutableListOf<String>()
        val total = LongArray(1)
        for (u in uris) {
            val doc = DocumentFile.fromSingleUri(ctx, u)
            val name = safe(doc?.name ?: continue)
            val lower = name.lowercase()
            if (!(lower.endsWith(".py") || archives.any { lower.endsWith(it) })) { rejected += name; continue }
            try {
                ctx.contentResolver.openInputStream(u)?.use { input ->
                    File(libs, name).outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total[0] += n.toLong()
                            if (total[0] > MAX_BYTES) throw IOException("That is larger than 150 MB")
                            out.write(buf, 0, n)
                        }
                    }
                }
                ok++
            } catch (e: Exception) {
                return "Import stopped: ${e.message}"
            }
        }
        return buildString {
            append("Added $ok file${if (ok == 1) "" else "s"}.")
            if (rejected.isNotEmpty()) append(" Skipped (only .py, .zip, .whl and .egg work): ${rejected.joinToString()}.")
        }
    }
}

// ---------------------------------------------------------------- engine

object PyEngine {
    @Synchronized fun ensure(ctx: Context) {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx.applicationContext))
    }

    private fun runner() = Python.getInstance().getModule("pylab_runner")

    fun version(ctx: Context): String = try {
        ensure(ctx)
        runner().callAttr("version").toString()
    } catch (e: Throwable) { "" }

    /**
     * Runs the program and blocks until it ends, returning the status line. Output is streamed to [onText] while it
     * runs and [onWaiting] says whether it is waiting for a line to be typed (send / eof answer it).
     */
    fun run(
        ctx: Context, code: String, root: File, timeoutSec: Int,
        onText: (String) -> Unit, onWaiting: (Boolean) -> Unit
    ): String = try {
        ensure(ctx)
        val mod = runner()
        val paths = PyLibs.sysPath(PyStorage.libs(root)).joinToString("\n")
        val work = PyStorage.work(root).absolutePath
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val poller = Thread {
            var wasWaiting = false
            while (!done.get()) {
                try { Thread.sleep(40) } catch (e: InterruptedException) { }
                try {
                    val r = mod.callAttr("poll").asList()
                    val text = r[0].toString()
                    if (text.isNotEmpty()) onText(text)
                    val w = r[1].toBoolean()
                    if (w != wasWaiting) { wasWaiting = w; onWaiting(w) }
                } catch (e: Throwable) { }
            }
        }
        poller.start()
        val r = try {
            mod.callAttr("run", code, work, paths, timeoutSec).asList()
        } finally {
            done.set(true)
            try { poller.join(1000) } catch (e: InterruptedException) { }
        }
        val rest = r[0].toString()
        if (rest.isNotEmpty()) onText(rest)
        onWaiting(false)
        r[1].toString()
    } catch (e: Throwable) {
        onWaiting(false)
        "Python could not start: ${e.message}"
    }

    fun send(line: String) {
        try { runner().callAttr("send", line) } catch (e: Throwable) { }
    }

    fun eof() {
        try { runner().callAttr("close_input") } catch (e: Throwable) { }
    }

    fun stop() {
        try { runner().callAttr("stop") } catch (e: Throwable) { }
    }
}

// ---------------------------------------------------------------- editor colours

private val PyBg = Color(0xFF1E1E2E)
private val PyFg = Color(0xFFE6E6F0)

private val pyKeywords = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except",
    "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass",
    "raise", "return", "try", "while", "with", "yield", "match", "case"
)
private val pyConstants = setOf("True", "False", "None", "self")
private val pyBuiltins = setOf(
    "print", "input", "len", "range", "int", "float", "str", "list", "dict", "set", "tuple", "bool", "open", "sum",
    "min", "max", "abs", "sorted", "enumerate", "zip", "map", "filter", "type", "isinstance", "round"
)

private class PyHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        if (s.length > 40_000) return TransformedText(text, OffsetMapping.Identity)   // colouring a huge text on every key press would make typing lag
        val b = AnnotatedString.Builder(s)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '#') {
                var j = i
                while (j < s.length && s[j] != '\n') j++
                b.addStyle(SpanStyle(color = Color(0xFF7F8C9B)), i, j)
                i = j
            } else if (c == '"' || c == '\'') {
                val triple = s.startsWith("$c$c$c", i)
                var j: Int
                if (triple) {
                    j = s.indexOf("$c$c$c", i + 3)
                    j = if (j < 0) s.length else j + 3
                } else {
                    j = i + 1
                    while (j < s.length && s[j] != c && s[j] != '\n') {
                        if (s[j] == '\\') j++
                        j++
                    }
                    j = minOf(j + 1, s.length)
                }
                b.addStyle(SpanStyle(color = Color(0xFFC3E88D)), i, j)
                i = j
            } else if (c == '@') {
                var j = i + 1
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_' || s[j] == '.')) j++
                b.addStyle(SpanStyle(color = Color(0xFFC792EA)), i, j)
                i = j
            } else if (c.isDigit()) {
                var j = i
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '.' || s[j] == '_')) j++
                b.addStyle(SpanStyle(color = Color(0xFFF78C6C)), i, j)
                i = j
            } else if (c.isLetter() || c == '_') {
                var j = i
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                val w = s.substring(i, j)
                when (w) {
                    in pyKeywords -> b.addStyle(SpanStyle(color = Color(0xFF82AAFF), fontWeight = FontWeight.Bold), i, j)
                    in pyConstants -> b.addStyle(SpanStyle(color = Color(0xFFFF9CAC)), i, j)
                    in pyBuiltins -> b.addStyle(SpanStyle(color = Color(0xFF89DDFF)), i, j)
                }
                i = j
            } else {
                i++
            }
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private val tbHeader = Regex("^Traceback \\(most recent call last\\):.*")
private val tbFile = Regex("^\\s*File \".*\", line \\d+.*")
private val tbCaret = Regex("^\\s*[\\^~]+\\s*$")
private val tbError = Regex("^[A-Za-z_][\\w.]*(Error|Exception|Warning|Interrupt|Exit)\\b.*")

private fun colorPython(out: String): AnnotatedString = buildAnnotatedString {
    val lines = out.split("\n")
    lines.forEachIndexed { i, l ->
        val style = when {
            tbHeader.matches(l) -> SpanStyle(color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
            tbError.matches(l) -> SpanStyle(color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
            tbFile.matches(l) -> SpanStyle(color = Color(0xFF82AAFF))
            tbCaret.matches(l) -> SpanStyle(color = Color(0xFF7EE0A8), fontWeight = FontWeight.Bold)
            else -> null
        }
        if (style != null) withStyle(style) { append(l) } else append(l)
        if (i < lines.lastIndex) append("\n")
    }
}

// ---------------------------------------------------------------- samples

private val pySamples = linkedMapOf(
    "Hello" to "print(\"Hello from Python!\")\nname = \"StudyTint\"\nprint(f\"Welcome to {name}\")\n",
    "Input & maths" to "a = int(input(\"First number: \"))\nb = int(input(\"Second number: \"))\nprint(\"Sum      =\", a + b)\nprint(\"Product  =\", a * b)\nprint(\"Division =\", a / b)\n",
    "Loops" to "for i in range(1, 6):\n    print(i, \"squared is\", i * i)\n\ntotal = 0\nn = 1\nwhile n <= 10:\n    total += n\n    n += 1\nprint(\"Sum of 1..10 =\", total)\n",
    "Lists & dicts" to "marks = {\"Asha\": 82, \"Ravi\": 74, \"Mina\": 91}\nfor student, score in sorted(marks.items(), key=lambda kv: -kv[1]):\n    print(f\"{student:<6} {score}\")\n\nsquares = [n * n for n in range(1, 8)]\nprint(squares, sum(squares))\n",
    "Classes" to "class Account:\n    def __init__(self, owner, balance=0):\n        self.owner = owner\n        self.balance = balance\n\n    def deposit(self, amount):\n        self.balance += amount\n        return self.balance\n\nacc = Account(\"Riya\", 100)\nprint(acc.deposit(50))\nprint(acc.owner, acc.balance)\n",
    "Files" to "# Files are saved in the Python Lab working folder.\nwith open(\"notes.txt\", \"w\") as f:\n    f.write(\"Saved from StudyTint\\n\")\n\nwith open(\"notes.txt\") as f:\n    print(f.read())\n\nimport os\nprint(os.listdir(\".\"))\n",
    "Error demo" to "def divide(a, b):\n    return a / b\n\nprint(divide(10, 2))\nprint(divide(1, 0))\n"
)

private val pySymbols = listOf(":", "    ", "(", ")", "[", "]", "{", "}", "\"", "'", "=", "#", "_", ".", ",", "+", "-", "*", "/", "%", "<", ">", "!")

// ---------------------------------------------------------------- screen

@Composable
fun PyLabScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pylab_prefs", Context.MODE_PRIVATE) }
    var code by remember { mutableStateOf(TextFieldValue(prefs.getString("code", pySamples["Hello"]) ?: "")) }
    var timeout by remember { mutableStateOf(prefs.getInt("timeout", 15)) }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    val outLock = remember { Any() }
    val scroll = rememberScrollState()
    var panel by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var onlineQuery by remember { mutableStateOf("") }
    var onlineHits by remember { mutableStateOf<List<PyOnline.Hit>?>(null) }   // null = show the popular list
    var onlineBusy by remember { mutableStateOf("") }                           // "", "search" or the library being saved
    var onlineMsg by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var showSave by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var pyVersion by remember { mutableStateOf("") }
    val highlight = remember { PyHighlight() }
    val mono = FontFamily.Monospace

    // refresh is read so these re-evaluate after storage / library / script changes
    val kind = remember(refresh) { PyStorage.kind(ctx) }
    val root = remember(refresh) { PyStorage.root(ctx) }
    val libList = remember(refresh) { PyLibs.list(PyStorage.libs(root)) }
    val onlineInstalled = remember(refresh) { PyOnline.installedNames(PyStorage.libs(root)) }
    val scriptList = remember(refresh) { (PyStorage.scripts(root).listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".py") }.sortedBy { it.name.lowercase() } }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        pyVersion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PyEngine.version(ctx) }
    }

    // leaving the screen stops a program that is still running
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (running) PyEngine.stop() } }
    // keep the prompt in view when the program asks for input
    androidx.compose.runtime.LaunchedEffect(waiting) {
        if (waiting) {
            kotlinx.coroutines.delay(150)
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

    fun runAsync(work: () -> String) {
        Thread {
            val msg = work()
            note = msg
            refresh++
        }.start()
    }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { note = "Copying…"; runAsync { PyLibs.importTree(ctx, uri, PyStorage.libs(PyStorage.root(ctx))) } }
    }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { note = "Copying…"; runAsync { PyLibs.importFiles(ctx, uris, PyStorage.libs(PyStorage.root(ctx))) } }
    }

    fun doOnlineSearch() {
        val q = onlineQuery.trim()
        if (q.isEmpty()) { onlineHits = null; onlineMsg = ""; return }
        if (onlineBusy.isNotEmpty()) return
        onlineBusy = "search"
        onlineMsg = "Searching…"
        Thread {
            try {
                val found = PyOnline.search(q)
                onlineHits = found
                onlineMsg = if (found.isEmpty()) "Nothing found for \"$q\". Try the exact library name." else ""
            } catch (e: java.net.UnknownHostException) {
                onlineMsg = "No internet connection."
            } catch (e: java.net.ConnectException) {
                onlineMsg = "No internet connection."
            } catch (e: Exception) {
                onlineMsg = "Search failed: ${e.message ?: "network error"}"
            }
            onlineBusy = ""
        }.start()
    }

    fun doOnlineInstall(name: String) {
        if (onlineBusy.isNotEmpty()) return
        onlineBusy = name
        onlineMsg = "Looking up $name…"
        val libs = PyStorage.libs(root)
        Thread {
            val res = PyOnline.install(libs, name) { onlineMsg = it }
            onlineMsg = res.message()
            onlineBusy = ""
            refresh++
        }.start()
    }

    fun runCode() {
        if (running) return
        running = true
        waiting = false
        output = ""
        status = "Running…"
        val src = code.text
        val limit = timeout
        val r = root
        prefs.edit().putString("code", src).putInt("timeout", limit).apply()
        Thread(null, Runnable {
            val st = PyEngine.run(ctx.applicationContext, src, r, limit, { appendOut(it) }, { waiting = it })
            status = st
            waiting = false
            running = false
        }, "pylab-run", 32L * 1024 * 1024).start()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Python Lab", back)
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                (if (pyVersion.isNotEmpty()) "Python $pyVersion" else "Python") + "  ·  files in ${kind.label}",
                fontSize = 12.sp, color = Color.Gray
            )

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pySamples.forEach { (name, src) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = Color.White,
                        modifier = Modifier.tap(RoundedCornerShape(20.dp)) { setCode(TextFieldValue(src, TextRange(src.length))) }
                    ) {
                        Text(name, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, color = Ink)
                    }
                }
            }

            BasicTextField(
                value = code,
                onValueChange = { setCode(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp)
                    .background(PyBg, RoundedCornerShape(16.dp)).padding(14.dp),
                textStyle = TextStyle(fontFamily = mono, fontSize = 14.sp, color = PyFg, lineHeight = 20.sp),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = highlight,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false)
            )

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pySymbols.forEach { sym ->
                    Surface(
                        shape = RoundedCornerShape(8.dp), color = Color(0xFFE9E9FA),
                        modifier = Modifier.tap(RoundedCornerShape(8.dp)) { insert(sym) }
                    ) {
                        Text(if (sym.isBlank()) "Tab" else sym, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontFamily = mono, fontSize = 14.sp, color = Ink)
                    }
                }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Time limit", Modifier.padding(top = 8.dp, end = 4.dp), fontSize = 13.sp, color = Color.Gray)
                listOf(5, 15, 30, 60).forEach { sec ->
                    val on = timeout == sec
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = if (on) Accent else Color.White,
                        modifier = Modifier.tap(RoundedCornerShape(16.dp)) { timeout = sec; prefs.edit().putInt("timeout", sec).apply() }
                    ) {
                        Text("${sec}s", Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 13.sp, color = if (on) Color.White else Ink)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { runCode() }, enabled = !running,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3776AB))
                ) { Text(if (running) "Running…" else "▶  Run", fontSize = 16.sp) }
                OutlinedButton(
                    onClick = { PyEngine.stop() }, enabled = running,
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Stop") }
                Button(
                    onClick = { output = ""; status = "" }, enabled = !running,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
                ) { Text("Clear") }
            }

            ConsoleBox(
                output = remember(output) { colorPython(output) },
                status = status,
                statusOk = status.endsWith("code 0") || status == "Running…",
                running = running,
                waiting = waiting,
                onSubmit = { line ->
                    appendOut(line + "\n")        // echo what was typed, like a terminal
                    PyEngine.send(line)
                },
                onEof = { PyEngine.eof() }
            )

            // ---- storage, libraries, scripts
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("storage" to "Storage", "libs" to "Libraries", "online" to "Get online", "scripts" to "My scripts").forEach { (id, label) ->
                    val on = panel == id
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = if (on) Accent else Color.White,
                        modifier = Modifier.tap(RoundedCornerShape(20.dp)) { panel = if (on) "" else id }
                    ) {
                        Text(label, Modifier.padding(horizontal = 16.dp, vertical = 9.dp), fontSize = 14.sp, color = if (on) Color.White else Ink)
                    }
                }
            }

            if (note.isNotEmpty()) Text(note, fontSize = 13.sp, color = Accent)

            when (panel) {
                "storage" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Where Python Lab keeps its scripts, libraries and working files.", fontSize = 13.sp, color = Color.Gray)
                    PyStore.values().forEach { k ->
                        val base = PyStorage.baseFor(ctx, k)
                        val enabled = base != null
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                RadioButton(selected = kind == k, enabled = enabled, onClick = {
                                    note = "Switching…"
                                    runAsync { PyStorage.switchTo(ctx, k) }
                                })
                                Column(Modifier.padding(start = 4.dp)) {
                                    Text(k.label, fontWeight = FontWeight.Medium, color = if (enabled) Ink else Color.Gray)
                                    Text(if (enabled) k.hint else "Not detected on this device.", fontSize = 12.sp, color = Color.Gray)
                                    if (enabled && kind == k) Text(File(base!!, "PyLab").absolutePath, fontSize = 11.sp, fontFamily = mono, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                "libs" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Import pure-Python libraries from your phone or SD card. They are copied into Python Lab and become importable straight away.",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { treeLauncher.launch(null) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Add folder") }
                        Button(
                            onClick = { filesLauncher.launch(arrayOf("*/*")) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Add files") }
                    }
                    TextButton(onClick = { panel = "online" }) { Text("Or get libraries from the internet →") }
                    Text("A folder with __init__.py is added as one package; any other folder adds everything inside it. Files can be .py, .zip, .whl or .egg. Libraries with compiled code (.so) cannot run.", fontSize = 12.sp, color = Color.Gray)
                    if (libList.isEmpty()) Text("No libraries yet.", fontSize = 13.sp, color = Ink)
                    libList.forEach { item ->
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(item.name + if (item.isDir) "/" else "", fontFamily = mono, fontSize = 14.sp, color = Ink)
                                    Text(formatSize(item.bytes), fontSize = 12.sp, color = Color.Gray)
                                }
                                TextButton(onClick = { PyLibs.delete(PyStorage.libs(root), item.name); refresh++ }) { Text("Remove") }
                            }
                        }
                    }
                }

                "online" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Download libraries from the internet (PyPI) straight into Python Lab, together with the libraries they need. " +
                            "Only pure-Python libraries can run on a phone: ones built on compiled code (NumPy, pandas, Pillow, SciPy…) cannot.",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    Text("Save to", fontSize = 12.sp, color = Color.Gray)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PyStore.values().forEach { k ->
                            val avail = PyStorage.baseFor(ctx, k) != null
                            val on = kind == k
                            Surface(
                                shape = RoundedCornerShape(16.dp), color = if (on) Accent else Color.White,
                                modifier = Modifier.tap(RoundedCornerShape(16.dp)) {
                                    if (avail && !on && onlineBusy.isEmpty()) {
                                        note = "Switching…"
                                        runAsync { PyStorage.switchTo(ctx, k) }
                                    }
                                }
                            ) {
                                Text(
                                    k.label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp,
                                    color = if (on) Color.White else if (avail) Ink else Color.Gray
                                )
                            }
                        }
                    }
                    Text(PyStorage.libs(root).absolutePath, fontFamily = mono, fontSize = 11.sp, color = Color.Gray)

                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicTextField(
                            value = onlineQuery,
                            onValueChange = { onlineQuery = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f).background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp),
                            textStyle = TextStyle(fontFamily = mono, fontSize = 14.sp, color = Ink),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doOnlineSearch() }),
                            decorationBox = { inner ->
                                if (onlineQuery.isEmpty()) Text("Search PyPI, e.g. requests", fontSize = 14.sp, color = Color.Gray)
                                inner()
                            }
                        )
                        Button(
                            onClick = { doOnlineSearch() }, enabled = onlineBusy.isEmpty(),
                            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Search") }
                    }
                    if (onlineBusy.isNotEmpty()) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (onlineMsg.isNotEmpty()) Text(onlineMsg, fontSize = 13.sp, color = Accent)

                    val shown = onlineHits ?: PyOnline.popular()
                    Text(if (onlineHits == null) "Popular pure-Python libraries" else "Results", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                    shown.forEach { h ->
                        val have = PyOnline.normalize(h.name) in onlineInstalled
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(
                                        h.name + (if (h.version.isNotEmpty()) "  ${h.version}" else "") + (if (have) "  ✓" else ""),
                                        fontFamily = mono, fontSize = 14.sp, color = Ink
                                    )
                                    if (h.summary.isNotEmpty()) Text(h.summary, fontSize = 12.sp, color = Color.Gray)
                                }
                                if (onlineBusy == h.name) {
                                    Text("Saving…", fontSize = 13.sp, color = Accent, modifier = Modifier.padding(end = 12.dp))
                                } else {
                                    TextButton(onClick = { doOnlineInstall(h.name) }, enabled = onlineBusy.isEmpty()) {
                                        Text(if (have) "Update" else "Install")
                                    }
                                }
                            }
                        }
                    }
                    if (onlineHits != null) {
                        TextButton(onClick = { onlineHits = null; onlineQuery = ""; onlineMsg = "" }) { Text("Back to popular") }
                    }
                    Text(
                        "Saved libraries appear in the Libraries tab, where you can remove them. They are checked against PyPI's checksum before they are kept.",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }

                "scripts" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { saveName = ""; showSave = true }, shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Save current code") }
                    if (scriptList.isEmpty()) Text("No saved scripts yet.", fontSize = 13.sp, color = Ink)
                    scriptList.forEach { f ->
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(f.name, Modifier.weight(1f).padding(vertical = 12.dp), fontFamily = mono, fontSize = 14.sp, color = Ink)
                                TextButton(onClick = {
                                    val t = try { f.readText() } catch (e: Exception) { "" }
                                    setCode(TextFieldValue(t, TextRange(t.length)))
                                }) { Text("Open") }
                                TextButton(onClick = { f.delete(); refresh++ }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save script") },
            text = {
                BasicTextField(
                    value = saveName, onValueChange = { saveName = it },
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F1F6), RoundedCornerShape(12.dp)).padding(12.dp),
                    textStyle = TextStyle(fontFamily = mono, fontSize = 15.sp, color = Ink), singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = saveName.trim().replace(Regex("[^A-Za-z0-9_\\- ]"), "_").ifEmpty { "script" }
                    val file = File(PyStorage.scripts(root), if (clean.endsWith(".py")) clean else "$clean.py")
                    note = try { file.writeText(code.text); "Saved ${file.name}." } catch (e: Exception) { "Could not save: ${e.message}" }
                    showSave = false
                    refresh++
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } }
        )
    }
}

private fun formatSize(b: Long): String = when {
    b >= 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
    b >= 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "$b B"
}
