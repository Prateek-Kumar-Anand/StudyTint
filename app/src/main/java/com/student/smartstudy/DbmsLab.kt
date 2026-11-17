package com.student.smartstudy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SqlBg = Color(0xFF1E1E2E)
private val SqlFg = Color(0xFFE6E6F0)
val DbColor = Color(0xFF2E86DE)

// ------------------------------------------------------------------------------------------ SQL words

private val sqlKeywords = listOf(
    "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL", "IS", "IN", "LIKE", "GLOB", "BETWEEN", "EXISTS", "DISTINCT", "ALL",
    "AS", "ON", "USING", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "NATURAL", "GROUP", "BY", "HAVING",
    "ORDER", "ASC", "DESC", "LIMIT", "OFFSET", "UNION", "INTERSECT", "EXCEPT", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
    "DELETE", "CREATE", "TABLE", "VIEW", "INDEX", "TRIGGER", "DROP", "ALTER", "ADD", "COLUMN", "RENAME", "TO", "PRIMARY",
    "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK", "DEFAULT", "CONSTRAINT", "AUTOINCREMENT", "IF", "TEMP", "TEMPORARY",
    "BEGIN", "END", "COMMIT", "ROLLBACK", "TRANSACTION", "SAVEPOINT", "RELEASE", "CASE", "WHEN", "THEN", "ELSE", "CAST",
    "COLLATE", "WITH", "RECURSIVE", "OVER", "PARTITION", "WINDOW", "ROWS", "RANGE", "UNBOUNDED", "PRECEDING", "FOLLOWING",
    "CURRENT", "ROW", "PRAGMA", "EXPLAIN", "QUERY", "PLAN", "VACUUM", "ANALYZE", "BEFORE", "AFTER", "INSTEAD", "OF", "FOR",
    "EACH", "RAISE", "ABORT", "FAIL", "IGNORE", "REPLACE", "CONFLICT", "DO", "NOTHING", "RETURNING", "TRUE", "FALSE",
    "SHOW", "TABLES", "DESCRIBE", "TRUNCATE", "VIRTUAL", "WITHOUT", "ROWID", "GENERATED", "ALWAYS", "STORED", "FILTER",
    "NULLS", "FIRST", "LAST", "ESCAPE", "ISNULL", "NOTNULL", "MATCH"
)
private val sqlKeywordSet = sqlKeywords.toHashSet()
private val sqlTypes = setOf(
    "INTEGER", "INT", "TEXT", "REAL", "BLOB", "NUMERIC", "VARCHAR", "CHAR", "FLOAT", "DOUBLE", "DECIMAL", "DATE", "DATETIME",
    "BOOLEAN", "TIMESTAMP", "BIGINT", "SMALLINT", "TINYINT"
)
private val sqlFunctions = listOf(
    "COUNT", "SUM", "AVG", "MIN", "MAX", "TOTAL", "ABS", "ROUND", "LENGTH", "LOWER", "UPPER", "SUBSTR", "SUBSTRING", "TRIM",
    "LTRIM", "RTRIM", "COALESCE", "IFNULL", "NULLIF", "IIF", "DATE", "TIME", "DATETIME", "STRFTIME", "JULIANDAY", "RANDOM",
    "TYPEOF", "INSTR", "GROUP_CONCAT", "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE", "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE",
    "PRINTF", "HEX", "QUOTE", "REPLACE", "CAST", "LIKE", "JSON_EXTRACT", "MAX", "CHANGES", "LAST_INSERT_ROWID"
).toHashSet()

private class SqlHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        if (s.length > 40_000) return TransformedText(text, OffsetMapping.Identity)   // colouring a huge text on every key press would make typing lag
        val b = AnnotatedString.Builder(s)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '-' && i + 1 < s.length && s[i + 1] == '-') {
                var j = i
                while (j < s.length && s[j] != '\n') j++
                b.addStyle(SpanStyle(color = Color(0xFF7F8C9B)), i, j)
                i = j
            } else if (c == '/' && i + 1 < s.length && s[i + 1] == '*') {
                var j = s.indexOf("*/", i + 2)
                j = if (j < 0) s.length else j + 2
                b.addStyle(SpanStyle(color = Color(0xFF7F8C9B)), i, j)
                i = j
            } else if (c == '\'') {
                var j = i + 1
                while (j < s.length) {
                    if (s[j] == '\'') {
                        if (j + 1 < s.length && s[j + 1] == '\'') { j += 2; continue }
                        j++
                        break
                    }
                    j++
                }
                b.addStyle(SpanStyle(color = Color(0xFFC3E88D)), i, minOf(j, s.length))
                i = minOf(j, s.length)
            } else if (c == '"' || c == '`') {
                var j = i + 1
                while (j < s.length && s[j] != c && s[j] != '\n') j++
                j = minOf(j + 1, s.length)
                b.addStyle(SpanStyle(color = Color(0xFFFFCB6B)), i, j)
                i = j
            } else if (c.isDigit()) {
                var j = i
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '.')) j++
                b.addStyle(SpanStyle(color = Color(0xFFF78C6C)), i, j)
                i = j
            } else if (c.isLetter() || c == '_') {
                var j = i
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                val w = s.substring(i, j).uppercase()
                var k = j
                while (k < s.length && s[k] == ' ') k++
                val call = k < s.length && s[k] == '('
                when {
                    call && w in sqlFunctions -> b.addStyle(SpanStyle(color = Color(0xFF89DDFF)), i, j)
                    w in sqlKeywordSet -> b.addStyle(SpanStyle(color = Color(0xFF82AAFF), fontWeight = FontWeight.Bold), i, j)
                    w in sqlTypes -> b.addStyle(SpanStyle(color = Color(0xFFFFCB6B)), i, j)
                }
                i = j
            } else {
                i++
            }
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** Word before the cursor and what could complete it: table and column names first, then SQL keywords. */
private class Suggestion(val start: Int, val end: Int, val items: List<String>)

private fun suggest(v: TextFieldValue, names: List<String>): Suggestion? {
    if (!v.selection.collapsed) return null
    val tx = v.text
    val end = v.selection.start
    if (end < 0 || end > tx.length) return null
    var st = end
    while (st > 0 && (tx[st - 1].isLetterOrDigit() || tx[st - 1] == '_')) st--
    if (end - st < 2) return null
    val w = tx.substring(st, end)
    val out = ArrayList<String>()
    for (n in names) if (n.startsWith(w, ignoreCase = true) && !n.equals(w, ignoreCase = true) && n !in out) out.add(n)
    for (k in sqlKeywords) if (k.startsWith(w, ignoreCase = true) && !k.equals(w, ignoreCase = true) && k !in out) out.add(k)
    return if (out.isEmpty()) null else Suggestion(st, end, out.take(14))
}

private val snippets = linkedMapOf(
    "CREATE TABLE" to "CREATE TABLE my_table (\n    id INTEGER PRIMARY KEY,\n    name TEXT NOT NULL,\n    marks INTEGER CHECK (marks >= 0)\n);\n",
    "INSERT" to "INSERT INTO my_table (id, name, marks)\nVALUES (1, 'Aarav', 85),\n       (2, 'Meera', 91);\n",
    "SELECT" to "SELECT *\nFROM my_table\nWHERE marks >= 50\nORDER BY name;\n",
    "UPDATE" to "UPDATE my_table\nSET marks = marks + 5\nWHERE id = 1;\n",
    "DELETE" to "DELETE FROM my_table\nWHERE id = 2;\n",
    "JOIN" to "SELECT a.name, b.title\nFROM table_a AS a\nJOIN table_b AS b ON a.id = b.a_id;\n",
    "GROUP BY" to "SELECT department, COUNT(*) AS total, AVG(marks) AS average\nFROM my_table\nGROUP BY department\nHAVING COUNT(*) >= 2\nORDER BY total DESC;\n",
    "Subquery" to "SELECT name\nFROM my_table\nWHERE marks > (SELECT AVG(marks) FROM my_table);\n",
    "ALTER" to "ALTER TABLE my_table ADD COLUMN email TEXT;\n",
    "VIEW" to "CREATE VIEW toppers AS\nSELECT name, marks\nFROM my_table\nWHERE marks >= 80;\n",
    "INDEX" to "CREATE INDEX idx_marks ON my_table (marks);\n\nEXPLAIN QUERY PLAN\nSELECT * FROM my_table WHERE marks = 85;\n",
    "TRIGGER" to "CREATE TRIGGER log_delete AFTER DELETE ON my_table\nBEGIN\n    INSERT INTO audit_log (note) VALUES ('deleted ' || OLD.name);\nEND;\n",
    "Transaction" to "BEGIN;\nUPDATE Account SET balance = balance - 500 WHERE account_id = 1;\nUPDATE Account SET balance = balance + 500 WHERE account_id = 2;\nCOMMIT;   -- or ROLLBACK;\n",
    "Window" to "SELECT name, marks,\n       RANK() OVER (ORDER BY marks DESC) AS position\nFROM my_table;\n",
    "CTE" to "WITH high AS (\n    SELECT * FROM my_table WHERE marks >= 80\n)\nSELECT COUNT(*) FROM high;\n",
    "Pragma" to "PRAGMA table_info(my_table);\n"
)

private val keywordChips = listOf(
    "SELECT", "FROM", "WHERE", "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM", "JOIN", "ON", "GROUP BY", "ORDER BY",
    "HAVING", "AND", "OR", "NOT", "NULL", "LIKE", "IN", "DISTINCT", "COUNT(", "AS", "LIMIT", "CREATE TABLE"
)
private val symbolChips = listOf(",", "(", ")", "'", ";", "*", "=", "<", ">", "%", "_", ".", "||", "!=")

private fun quoteIfNeeded(n: String) = if (Regex("[A-Za-z_][A-Za-z0-9_]*").matches(n)) n else "\"" + n.replace("\"", "\"\"") + "\""

private fun insertTemplate(t: DbTable): String {
    val cols = t.columns
    val names = cols.joinToString(", ") { quoteIfNeeded(it.name) }
    val vals = cols.joinToString(", ") {
        val ty = it.type.uppercase()
        if (ty.contains("INT") || ty.contains("REAL") || ty.contains("FLOA") || ty.contains("DOUB") || ty.contains("NUM") || ty.contains("DEC")) "0" else "''"
    }
    return "INSERT INTO ${quoteIfNeeded(t.name)} ($names)\nVALUES ($vals);\n"
}

private fun fmtMs(ms: Double): String = when {
    ms < 0.05 -> "<0.1 ms"
    ms < 1000 -> String.format(java.util.Locale.US, "%.1f ms", ms)
    else -> String.format(java.util.Locale.US, "%.2f s", ms / 1000)
}

private fun oneLine(sql: String): String = sql.replace(Regex("\\s+"), " ").trim().let { if (it.length > 150) it.take(150) + "…" else it }

private fun toTsv(r: DbResult): String = buildString {
    append(r.columns.joinToString("\t")).append('\n')
    for (row in r.rows) append(r.columns.indices.joinToString("\t") { (row.getOrNull(it) ?: "NULL").replace("\t", " ").replace("\n", " ") }).append('\n')
}

private fun isNumber(v: String) = v.isNotEmpty() && v.length < 24 && v.toDoubleOrNull() != null

// ------------------------------------------------------------------------------------------ screen

@Composable
fun DbLabScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val prefs = remember { ctx.getSharedPreferences("dbms_prefs", Context.MODE_PRIVATE) }
    val clipboard = LocalClipboardManager.current
    val mono = FontFamily.Monospace
    val highlight = remember { SqlHighlight() }
    val scroll = rememberScrollState()

    var dbName by remember { mutableStateOf(prefs.getString("db", null)?.takeIf { DbStore.validName(it) } ?: "my_database") }
    var code by remember { mutableStateOf(TextFieldValue(prefs.getString("sql", "") ?: "")) }
    var tab by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var lastRun by remember { mutableStateOf<DbRun?>(null) }
    var schema by remember { mutableStateOf<DbSchema?>(null) }
    var schemaLoading by remember { mutableStateOf(true) }
    var inTx by remember { mutableStateOf(false) }
    var stopOnError by remember { mutableStateOf(prefs.getBoolean("stop_on_error", true)) }
    var history by remember { mutableStateOf(DbStore.history(ctx)) }
    var showDbDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var resultsY by remember { mutableStateOf(0) }

    DisposableEffect(Unit) { onDispose { DbEngine.stop() } }

    fun setCode(v: TextFieldValue) {
        code = v
        prefs.edit().putString("sql", v.text).apply()
    }

    fun insertText(t: String) {
        val s = code.text
        val a = code.selection.min.coerceIn(0, s.length)
        val b = code.selection.max.coerceIn(0, s.length)
        setCode(TextFieldValue(s.substring(0, a) + t + s.substring(b), TextRange(a + t.length)))
    }

    fun insertSnippet(t: String) {
        val s = code.text
        val a = code.selection.min.coerceIn(0, s.length)
        val pre = if (s.isBlank()) "" else if (a > 0 && s[a - 1] != '\n') "\n\n" else "\n"
        if (s.isBlank()) setCode(TextFieldValue(t, TextRange(t.length))) else insertText(pre + t)
    }

    fun refreshSchema() {
        val name = dbName
        val path = DbStore.path(appCtx, name)
        Thread {
            val s = DbEngine.schema(appCtx, path)
            if (name == dbName) {
                schema = s
                if (s != null) inTx = s.inTx
                schemaLoading = false
            }
        }.start()
    }

    LaunchedEffect(dbName, refreshKey) {
        schemaLoading = true
        schema = null
        refreshSchema()
    }

    LaunchedEffect(lastRun) {
        if (lastRun != null) {
            delay(80)
            scroll.animateScrollTo(resultsY)
        }
    }

    fun execute(text: String, lineOffset: Int, save: Boolean) {
        if (running) return
        val name = dbName
        val path = DbStore.path(appCtx, name)
        running = true
        lastRun = null
        tab = 0
        if (save) history = DbStore.addHistory(appCtx, text)
        prefs.edit().putString("sql", code.text).apply()
        val stop = stopOnError
        Thread {
            val r = DbEngine.run(appCtx, path, text, 20, stop, lineOffset)
            val s = DbEngine.schema(appCtx, path)
            if (name == dbName) {
                if (s != null) schema = s
                inTx = r.inTx
                lastRun = r
            }
            running = false
        }.start()
    }

    fun runEditor() {
        val sel = code.selection
        val s = code.text
        val useSel = !sel.collapsed && s.substring(sel.min.coerceIn(0, s.length), sel.max.coerceIn(0, s.length)).isNotBlank()
        val text = if (useSel) s.substring(sel.min.coerceIn(0, s.length), sel.max.coerceIn(0, s.length)) else s
        if (text.isBlank()) {
            toastOnMain(appCtx, "Type a query first")
            return
        }
        val offset = if (useSel) s.substring(0, sel.min.coerceIn(0, s.length)).count { it == '\n' } else 0
        execute(text, offset, true)
    }

    fun runText(sql: String) {
        setCode(TextFieldValue(sql, TextRange(sql.length)))
        execute(sql, 0, true)
    }

    fun switchTo(n: String) {
        if (n == dbName) return
        val old = DbStore.path(appCtx, dbName)
        Thread { DbEngine.close(old) }.start()
        dbName = n
        prefs.edit().putString("db", n).apply()
        inTx = false
        lastRun = null
        expanded = emptySet()
    }

    fun createDb(rawName: String, template: String) {
        val n = rawName.trim()
        if (!DbStore.validName(n)) {
            toastOnMain(appCtx, "Use letters, numbers, spaces, _ or - (up to 30 characters)")
            return
        }
        if (DbStore.list(appCtx).any { it.equals(n, ignoreCase = true) }) {
            toastOnMain(appCtx, "A database called $n already exists")
            return
        }
        Thread {
            val path = DbStore.path(appCtx, n)
            val asset = DbStore.templates[template] ?: ""
            if (asset.isNotEmpty()) {
                val r = DbEngine.run(appCtx, path, DbStore.readAsset(appCtx, asset), 30, true, 0)
                val bad = r.results.firstOrNull { it.kind == "error" }
                if (bad != null) {
                    DbStore.deleteFiles(appCtx, n)
                    toastOnMain(appCtx, "Could not create it: " + bad.message)
                    return@Thread
                }
            } else {
                DbEngine.schema(appCtx, path)   // opening the file creates it
            }
            switchTo(n)
            toastOnMain(appCtx, "Database $n is ready")
        }.start()
    }

    fun deleteDb(n: String) {
        Thread {
            DbStore.deleteFiles(appCtx, n)
            if (n == dbName) {
                val rest = DbStore.list(appCtx)
                dbName = rest.firstOrNull() ?: "my_database"
                prefs.edit().putString("db", dbName).apply()
                inTx = false
                lastRun = null
                refreshKey++
            }
            toastOnMain(appCtx, "Deleted $n")
        }.start()
    }

    fun resetDb() {
        val n = dbName
        Thread {
            DbStore.deleteFiles(appCtx, n)
            inTx = false
            lastRun = null
            refreshKey++
            toastOnMain(appCtx, "$n is empty again")
        }.start()
    }

    fun shareDb() {
        val n = dbName
        val path = DbStore.path(appCtx, n)
        Thread {
            val d = DbEngine.dump(appCtx, path)
            when {
                d == null -> toastOnMain(appCtx, "Could not read the database")
                d.length > 400_000 -> toastOnMain(appCtx, "This database is too large to share as text")
                else -> Handler(Looper.getMainLooper()).post {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "$n.sql").putExtra(Intent.EXTRA_TEXT, d)
                    try { ctx.startActivity(Intent.createChooser(send, "Share $n as SQL")) } catch (e: Exception) { }
                }
            }
        }.start()
    }

    fun importUri(uri: Uri) {
        Thread {
            try {
                // read at most 40 MB so a huge file cannot use up the phone's memory
                val limit = 40_000_000
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (out.size() <= limit) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                if (bytes == null) {
                    toastOnMain(appCtx, "Could not open that file")
                    return@Thread
                }
                if (bytes.size > limit) {
                    toastOnMain(appCtx, "That file is too large (the limit is 40 MB)")
                    return@Thread
                }
                val display = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "imported.db"
                val isDb = bytes.size >= 16 && String(bytes, 0, 15, Charsets.ISO_8859_1) == "SQLite format 3"
                if (isDb) {
                    val n = DbStore.uniqueName(appCtx, DbStore.cleanName(display.substringBeforeLast('.')))
                    java.io.File(DbStore.path(appCtx, n)).writeBytes(bytes)
                    switchTo(n)
                    toastOnMain(appCtx, "Imported database $n")
                } else {
                    val text = bytes.toString(Charsets.UTF_8)
                    setCode(TextFieldValue(text, TextRange(text.length)))
                    tab = 0
                    toastOnMain(appCtx, "Loaded $display into the editor. Press Run to execute it.")
                }
            } catch (e: Exception) {
                toastOnMain(appCtx, "Could not read that file")
            }
        }.start()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) importUri(uri) }

    // ------------------------------------------------------------------------- layout

    Column(Modifier.fillMaxSize()) {
        TopBar("DBMS Lab", back)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.weight(1f, fill = false).clickable { showDbDialog = true }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("▣", color = DbColor, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(dbName, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("  ▾", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.weight(1f))
            Box {
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(38.dp).clickable { menuOpen = true }) {
                    Box(contentAlignment = Alignment.Center) { Text("⋮", fontSize = 20.sp, color = Ink) }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Databases…") }, onClick = { menuOpen = false; showDbDialog = true })
                    DropdownMenuItem(
                        text = { Text((if (stopOnError) "✓  " else "     ") + "Stop at the first error") },
                        onClick = {
                            menuOpen = false
                            stopOnError = !stopOnError
                            prefs.edit().putBoolean("stop_on_error", stopOnError).apply()
                        }
                    )
                    DropdownMenuItem(text = { Text("Open a .sql or .db file") }, onClick = { menuOpen = false; picker.launch("*/*") })
                    DropdownMenuItem(text = { Text("Share database as SQL") }, onClick = { menuOpen = false; shareDb() })
                    DropdownMenuItem(text = { Text("Clear the editor") }, onClick = { menuOpen = false; setCode(TextFieldValue("")) })
                    DropdownMenuItem(
                        text = { Text("Empty this database…", color = Color(0xFFD64545)) },
                        onClick = { menuOpen = false; confirm = "Delete every table and row in “$dbName”? This cannot be undone." to { resetDb() } }
                    )
                }
            }
        }

        if (inTx) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFFFEFD2)) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("● Transaction open. Changes are not saved yet.", Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFF8A5200))
                    TextButton(onClick = { execute("COMMIT;", 0, false) }, enabled = !running) { Text("Commit", fontSize = 13.sp) }
                    TextButton(onClick = { execute("ROLLBACK;", 0, false) }, enabled = !running) { Text("Rollback", fontSize = 13.sp, color = Color(0xFFD64545)) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Query", "Tables" + (schema?.let { " (${it.tables.size})" } ?: ""), "History" + if (history.isEmpty()) "" else " (${history.size})")
                .forEachIndexed { i, label ->
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = if (tab == i) DbColor else Color.White,
                        modifier = Modifier.weight(1f).clickable { tab = i }
                    ) {
                        Text(label, Modifier.fillMaxWidth().padding(vertical = 9.dp), textAlign = TextAlign.Center, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, color = if (tab == i) Color.White else Ink)
                    }
                }
        }

        when (tab) {
            0 -> {
                val names = remember(schema) {
                    val l = ArrayList<String>()
                    schema?.tables?.forEach { t -> l.add(t.name); t.columns.forEach { c -> l.add(c.name) } }
                    l.distinct()
                }
                val sg = suggest(code, names)
                val selected = !code.selection.collapsed && code.text.isNotBlank()
                Column(
                    Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        snippets.forEach { (label, text) ->
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.clickable { insertSnippet(text) }) {
                                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, color = Ink)
                            }
                        }
                    }

                    BasicTextField(
                        value = code,
                        onValueChange = { setCode(it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp).background(SqlBg, RoundedCornerShape(16.dp)).padding(14.dp),
                        textStyle = TextStyle(fontFamily = mono, fontSize = 14.sp, color = SqlFg, lineHeight = 20.sp),
                        cursorBrush = SolidColor(Color.White),
                        visualTransformation = highlight,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                        decorationBox = { inner ->
                            Box {
                                if (code.text.isEmpty()) {
                                    Text("Write SQL here, for example:\nSELECT * FROM Student;", fontFamily = mono, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF6B6B78))
                                }
                                inner()
                            }
                        }
                    )

                    if (sg != null) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            sg.items.forEach { cand ->
                                val isKw = cand in sqlKeywordSet
                                Surface(
                                    shape = RoundedCornerShape(8.dp), color = if (isKw) Color(0xFFE9E9FA) else Color(0xFFD9ECFF),
                                    modifier = Modifier.clickable {
                                        val tx = code.text
                                        val put = cand + (if (isKw) " " else "")
                                        setCode(TextFieldValue(tx.substring(0, sg.start) + put + tx.substring(sg.end), TextRange(sg.start + put.length)))
                                    }
                                ) { Text(cand, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontFamily = mono, fontSize = 13.sp, color = Ink) }
                            }
                        }
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            keywordChips.forEach { kw ->
                                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE9E9FA), modifier = Modifier.clickable { insertText(if (kw.endsWith("(")) kw else "$kw ") }) {
                                    Text(kw, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontFamily = mono, fontSize = 12.sp, color = Ink)
                                }
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        symbolChips.forEach { sym ->
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE9E9FA), modifier = Modifier.clickable { insertText(sym) }) {
                                Text(sym, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontFamily = mono, fontSize = 14.sp, color = Ink)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { runEditor() }, enabled = !running, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DbColor)
                        ) { Text(if (running) "Running…" else if (selected) "▶  Run selection" else "▶  Run", fontSize = 16.sp) }
                        OutlinedButton(onClick = { DbEngine.stop() }, enabled = running, shape = RoundedCornerShape(16.dp)) { Text("Stop") }
                        Button(
                            onClick = { setCode(TextFieldValue("")); lastRun = null }, enabled = !running, shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
                        ) { Text("Clear") }
                    }

                    Column(Modifier.onGloballyPositioned { resultsY = it.positionInParent().y.toInt() }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (running) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = DbColor, trackColor = Color(0xFFD9ECFF))
                            Text("Running…", fontSize = 12.sp, color = Color.Gray)
                        }
                        val r = lastRun
                        if (r != null) {
                            ResultsView(r) { text ->
                                clipboard.setText(AnnotatedString(text))
                                toastOnMain(appCtx, "Copied")
                            }
                        } else if (!running) {
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Ready", fontWeight = FontWeight.Medium, color = Ink)
                                    Text("• Write one or many statements, ending each with ;", fontSize = 13.sp, color = Color.Gray)
                                    Text("• Select part of the text to run only that part", fontSize = 13.sp, color = Color.Gray)
                                    Text("• Tap the database name to open a sample college, company, library or bank database", fontSize = 13.sp, color = Color.Gray)
                                    Text("• SHOW TABLES, DESC table and BEGIN / COMMIT / ROLLBACK all work", fontSize = 13.sp, color = Color.Gray)
                                    val v = schema?.version ?: ""
                                    if (v.isNotEmpty()) Text("Engine: SQLite $v", fontSize = 12.sp, color = Color(0xFF9A9AA8))
                                    else if (schemaLoading) Text("Starting the SQL engine…", fontSize = 12.sp, color = Color(0xFF9A9AA8))
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            1 -> {
                val sc = schema
                if (sc == null) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(if (schemaLoading) "Loading tables…" else "Could not read the database.", color = Color.Gray)
                    }
                } else if (sc.tables.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("No tables in $dbName yet", fontWeight = FontWeight.Medium, color = Ink)
                        Text("Create one in the Query tab, or open a ready-made sample.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showDbDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = DbColor)) { Text("Sample databases") }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(sc.tables, key = { it.name }) { t ->
                            val open = t.name in expanded
                            val q = quoteIfNeeded(t.name)
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable { expanded = if (open) expanded - t.name else expanded + t.name }.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(if (t.isView) "◈" else "▦", color = DbColor, fontSize = 16.sp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(t.name, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink)
                                            Text(
                                                (if (t.isView) "view" else "table") + " · ${t.columns.size} columns" + (t.rows?.let { " · $it rows" } ?: ""),
                                                fontSize = 12.sp, color = Color.Gray
                                            )
                                        }
                                        Text(if (open) "▴" else "▾", color = Color.Gray)
                                    }
                                    if (open) {
                                        HorizontalDivider(color = Color(0xFFEDEDF2))
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            t.columns.forEach { c ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(c.name, Modifier.weight(1f), fontFamily = mono, fontSize = 13.sp, fontWeight = if (c.pk > 0) FontWeight.Bold else FontWeight.Normal, color = Ink)
                                                    val flags = buildList {
                                                        if (c.type.isNotBlank()) add(c.type)
                                                        if (c.pk > 0) add("PK")
                                                        if (c.notNull && c.pk == 0) add("NOT NULL")
                                                        if (t.fks.any { it.from == c.name }) add("FK")
                                                        if (c.default != null) add("DEFAULT ${c.default}")
                                                    }
                                                    Text(flags.joinToString("  "), fontSize = 11.sp, color = Color(0xFF6B6B78))
                                                }
                                            }
                                            t.fks.forEach { f -> Text("↳ ${f.from} → ${f.table}(${f.to})", fontSize = 12.sp, color = DbColor, fontFamily = mono) }
                                            if (t.indexes.isNotEmpty()) Text("Indexes: " + t.indexes.joinToString(), fontSize = 12.sp, color = Color.Gray)
                                            if (t.triggers.isNotEmpty()) Text("Triggers: " + t.triggers.joinToString(), fontSize = 12.sp, color = Color.Gray)
                                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                val chipColor = Color(0xFFE9E9FA)
                                                Surface(shape = RoundedCornerShape(10.dp), color = DbColor, modifier = Modifier.clickable(enabled = !running) { runText("SELECT *\nFROM $q;") }) {
                                                    Text("SELECT *", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontSize = 13.sp)
                                                }
                                                Surface(shape = RoundedCornerShape(10.dp), color = chipColor, modifier = Modifier.clickable(enabled = !running) { runText("DESC $q;") }) {
                                                    Text("DESC", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontSize = 13.sp)
                                                }
                                                Surface(shape = RoundedCornerShape(10.dp), color = chipColor, modifier = Modifier.clickable(enabled = !running) { runText("SHOW CREATE TABLE $q;") }) {
                                                    Text("CREATE SQL", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontSize = 13.sp)
                                                }
                                                if (!t.isView) Surface(shape = RoundedCornerShape(10.dp), color = chipColor, modifier = Modifier.clickable { setCode(TextFieldValue(insertTemplate(t))); tab = 0 }) {
                                                    Text("INSERT template", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontSize = 13.sp)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp), color = Color(0xFFFBDADA),
                                                    modifier = Modifier.clickable(enabled = !running) {
                                                        confirm = "Drop ${if (t.isView) "view" else "table"} “${t.name}”?" + (t.rows?.let { if (!t.isView && it > 0) " Its $it rows are deleted." else "" } ?: "") to {
                                                            execute("DROP ${if (t.isView) "VIEW" else "TABLE"} IF EXISTS $q;", 0, false)
                                                        }
                                                    }
                                                ) { Text("DROP", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFFB02A2A), fontSize = 13.sp) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("SQLite ${sc.version}", Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFF9A9AA8))
                        }
                    }
                }
            }

            else -> {
                if (history.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Queries you run are saved here.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Last ${history.size} runs", Modifier.weight(1f), fontSize = 13.sp, color = Color.Gray)
                                TextButton(onClick = { confirm = "Clear the whole history?" to { DbStore.saveHistory(appCtx, emptyList()); history = emptyList() } }) { Text("Clear all", fontSize = 13.sp) }
                            }
                        }
                        items(history, key = { it }) { sql ->
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(sql.lines().take(5).joinToString("\n").let { if (sql.lines().size > 5) "$it\n…" else it }, fontFamily = mono, fontSize = 12.sp, lineHeight = 17.sp, color = Ink)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { setCode(TextFieldValue(sql, TextRange(sql.length))); tab = 0 }) { Text("Edit", fontSize = 13.sp) }
                                        TextButton(onClick = { runText(sql) }, enabled = !running) { Text("Run again", fontSize = 13.sp) }
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { history = history.filter { it != sql }; DbStore.saveHistory(appCtx, history) }) { Text("Remove", fontSize = 13.sp, color = Color(0xFFD64545)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------- dialogs

    val pending = confirm
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(pending.first) },
            confirmButton = { TextButton(onClick = { confirm = null; pending.second() }) { Text("Yes", color = Color(0xFFD64545)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }

    if (showDbDialog) {
        var newName by remember { mutableStateOf("") }
        var tpl by remember { mutableStateOf("College") }
        val dbs = remember(showDbDialog, dbName, refreshKey) { DbStore.list(appCtx) }
        val all = if (dbName in dbs) dbs else (dbs + dbName).sortedBy { it.lowercase() }
        AlertDialog(
            onDismissRequest = { showDbDialog = false },
            title = { Text("Databases") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    all.forEach { n ->
                        Row(Modifier.fillMaxWidth().clickable {
                            showDbDialog = false
                            if (n != dbName) {
                                if (inTx) confirm = "Switching now rolls back the open transaction. Continue?" to { switchTo(n) } else switchTo(n)
                            }
                        }.background(if (n == dbName) Color(0xFFE8F1FD) else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (n == dbName) "●" else "○", color = DbColor)
                            Spacer(Modifier.width(10.dp))
                            Text(n, Modifier.weight(1f), fontWeight = if (n == dbName) FontWeight.Medium else FontWeight.Normal, color = Ink)
                            Text("Delete", Modifier.clickable {
                                showDbDialog = false
                                confirm = "Delete database “$n” and everything in it?" to { deleteDb(n) }
                            }.padding(6.dp), fontSize = 12.sp, color = Color(0xFFD64545))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFEDEDF2))
                    Text("New database", fontWeight = FontWeight.Medium, color = Ink)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DbStore.templates.keys.forEach { t ->
                            Surface(
                                shape = RoundedCornerShape(16.dp), color = if (tpl == t) DbColor else Color(0xFFEFEFF6),
                                modifier = Modifier.clickable {
                                    if (newName.isBlank() || newName == tpl.lowercase() || newName == "new_database") newName = if (t == "Empty") "new_database" else t.lowercase()
                                    tpl = t
                                }
                            ) { Text(t, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 13.sp, color = if (tpl == t) Color.White else Ink) }
                        }
                    }
                    Text(DbStore.templateInfo[tpl] ?: "", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it.replace("\n", "").take(30) },
                        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val n = if (newName.isBlank()) (if (tpl == "Empty") "new_database" else tpl.lowercase()) else newName
                            showDbDialog = false
                            createDb(n, tpl)
                        },
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = DbColor)
                    ) { Text("Create and open") }
                }
            },
            confirmButton = { TextButton(onClick = { showDbDialog = false }) { Text("Close") } }
        )
    }
}

// ------------------------------------------------------------------------------------------ results

@Composable
private fun ResultsView(dbRun: DbRun, onCopy: (String) -> Unit) {
    val bad = dbRun.results.count { it.kind == "error" }
    val total = dbRun.results.sumOf { it.ms }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val summary = buildString {
            append("${dbRun.results.size} statement${if (dbRun.results.size == 1) "" else "s"}")
            if (bad > 0) append(" · $bad error${if (bad == 1) "" else "s"}")
            append(" · ").append(fmtMs(total))
        }
        Text(summary, fontSize = 12.sp, color = if (bad > 0) Color(0xFFB02A2A) else Color.Gray, fontWeight = FontWeight.Medium)
        dbRun.results.forEachIndexed { i, r ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.sql.isNotBlank()) {
                    Text("${i + 1}.  " + oneLine(r.sql), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF6B6B78), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                when (r.kind) {
                    "rows" -> RowsTable(r, onCopy)
                    "ok" -> Surface(shape = RoundedCornerShape(14.dp), color = Good) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("✔  " + r.message, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFF1F6B45))
                            Text(fmtMs(r.ms), fontSize = 11.sp, color = Color(0xFF4C8C6A))
                            if (r.hint.isNotBlank()) Text("⚠  " + r.hint, fontSize = 13.sp, color = Color(0xFF8A5200))
                        }
                    }
                    else -> Surface(shape = RoundedCornerShape(14.dp), color = Bad) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "✖  Error" + (if (r.code.isNotBlank()) " · ${r.code}" else "") + (if (r.line > 0) " · line ${r.line}" else ""),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFB02A2A)
                            )
                            SelectionContainer { Text(r.message, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Ink) }
                            if (r.hint.isNotBlank()) Text("💡  " + r.hint, fontSize = 13.sp, color = Color(0xFF1F4E8C))
                        }
                    }
                }
            }
        }
        if (dbRun.skipped > 0) {
            Text(
                "${dbRun.skipped} more statement${if (dbRun.skipped == 1) " was" else "s were"} not run because of the error. " +
                    "You can change this with “Stop at the first error” in the ⋮ menu.",
                fontSize = 12.sp, color = Color.Gray
            )
        }
    }
}

@Composable
private fun RowsTable(r: DbResult, onCopy: (String) -> Unit) {
    val mono = FontFamily.Monospace
    var shown by remember(r) { mutableStateOf(50) }
    val widths = remember(r) {
        r.columns.indices.map { c ->
            var m = r.columns[c].length
            for (row in r.rows.take(200)) {
                val l = (row.getOrNull(c) ?: "NULL").length
                if (l > m) m = l
            }
            (m.coerceIn(4, 28) * 8 + 24).dp
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${r.total} row${if (r.total == 1) "" else "s"} · ${r.columns.size} column${if (r.columns.size == 1) "" else "s"} · ${fmtMs(r.ms)}",
                    Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink
                )
                TextButton(onClick = { onCopy(toTsv(r)) }) { Text("Copy", fontSize = 12.sp) }
            }
            if (r.message.isNotBlank()) Text(r.message, Modifier.padding(horizontal = 12.dp, vertical = 2.dp), fontSize = 12.sp, color = Color(0xFF8A5200))
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row(Modifier.background(Color(0xFFEDEBFF))) {
                        Text("#", Modifier.width(40.dp).padding(horizontal = 8.dp, vertical = 8.dp), fontFamily = mono, fontSize = 12.sp, color = Color.Gray)
                        r.columns.forEachIndexed { i, c ->
                            Text(c, Modifier.width(widths[i]).padding(horizontal = 8.dp, vertical = 8.dp), fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                        }
                    }
                    r.rows.take(shown).forEachIndexed { ri, row ->
                        Row(Modifier.background(if (ri % 2 == 0) Color.White else Color(0xFFF6F6FB))) {
                            Text("${ri + 1}", Modifier.width(40.dp).padding(horizontal = 8.dp, vertical = 7.dp), fontFamily = mono, fontSize = 11.sp, color = Color(0xFF9A9AA8))
                            r.columns.indices.forEach { ci ->
                                val v = row.getOrNull(ci)
                                Text(
                                    v ?: "NULL", Modifier.width(widths[ci]).padding(horizontal = 8.dp, vertical = 7.dp),
                                    fontFamily = mono, fontSize = 12.sp, color = if (v == null) Color(0xFF9A9AA8) else Ink,
                                    fontStyle = if (v == null) FontStyle.Italic else FontStyle.Normal,
                                    textAlign = if (v != null && isNumber(v)) TextAlign.End else TextAlign.Start,
                                    maxLines = 6, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (r.rows.isEmpty()) Text("No rows", Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFF9A9AA8), fontStyle = FontStyle.Italic)
            if (r.rows.size > shown) TextButton(onClick = { shown += 50 }) { Text("Show 50 more  (${r.rows.size - shown} left)", fontSize = 13.sp) }
            if (r.truncated) Text("Only the first ${r.rows.size} of ${r.total} rows are loaded. Use LIMIT or WHERE to narrow the query.", Modifier.padding(12.dp), fontSize = 12.sp, color = Color.Gray)
        }
    }
}
