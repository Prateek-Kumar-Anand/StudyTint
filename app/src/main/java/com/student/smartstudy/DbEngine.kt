package com.student.smartstudy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Plain classes on purpose (not data classes): Compose compares them by identity, which is cheap even for 1000 rows.

class DbResult(
    val kind: String,                 // "rows", "ok" or "error"
    val sql: String,
    val line: Int,
    val ms: Double,
    val message: String,
    val hint: String,
    val code: String,
    val columns: List<String>,
    val rows: List<List<String?>>,
    val total: Int,
    val truncated: Boolean
)

class DbRun(val results: List<DbResult>, val inTx: Boolean, val skipped: Int, val timedOut: Boolean)

class DbColumn(val name: String, val type: String, val notNull: Boolean, val pk: Int, val default: String?)
class DbFk(val from: String, val table: String, val to: String)
class DbTable(
    val name: String, val isView: Boolean, val sql: String, val columns: List<DbColumn>, val fks: List<DbFk>,
    val rows: Int?, val indexes: List<String>, val triggers: List<String>
)
class DbSchema(val tables: List<DbTable>, val inTx: Boolean, val version: String)

/** Bridge to dbms_engine.py (SQLite through Python's sqlite3, the same Python the Python Lab uses). */
object DbEngine {
    private fun mod() = Python.getInstance().getModule("dbms_engine")

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    private fun errorRun(msg: String) = DbRun(
        listOf(DbResult("error", "", 0, 0.0, msg, "", "ENGINE", emptyList(), emptyList(), 0, false)), false, 0, false
    )

    fun run(ctx: Context, path: String, sql: String, timeoutSec: Int, stopOnError: Boolean, lineOffset: Int): DbRun = try {
        PyEngine.ensure(ctx)
        val json = mod().callAttr("run", path, sql, timeoutSec, 1000, stopOnError, lineOffset).toString()
        val o = JSONObject(json)
        val arr = o.getJSONArray("results")
        val results = (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            val cols = strings(r.optJSONArray("columns"))
            val rowsJson = r.optJSONArray("rows")
            val rows = if (rowsJson == null) emptyList() else (0 until rowsJson.length()).map { ri ->
                val row = rowsJson.getJSONArray(ri)
                (0 until row.length()).map { ci -> if (row.isNull(ci)) null else row.getString(ci) }
            }
            DbResult(
                kind = r.getString("kind"), sql = r.optString("sql", ""), line = r.optInt("line", 0), ms = r.optDouble("ms", 0.0),
                message = r.optString("message", ""), hint = r.optString("hint", ""), code = r.optString("code", ""),
                columns = cols, rows = rows, total = r.optInt("total", rows.size), truncated = r.optBoolean("truncated", false)
            )
        }
        DbRun(results, o.optBoolean("in_tx", false), o.optInt("skipped", 0), o.optBoolean("timed_out", false))
    } catch (e: Throwable) {
        errorRun("The SQL engine could not run: " + (e.message ?: e.toString()))
    }

    fun schema(ctx: Context, path: String): DbSchema? = try {
        PyEngine.ensure(ctx)
        val o = JSONObject(mod().callAttr("schema", path).toString())
        val arr = o.getJSONArray("tables")
        val tables = (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            val cj = t.getJSONArray("columns")
            val cols = (0 until cj.length()).map { k ->
                val c = cj.getJSONObject(k)
                DbColumn(c.getString("name"), c.optString("type", ""), c.optBoolean("notnull", false), c.optInt("pk", 0),
                    if (c.isNull("default")) null else c.getString("default"))
            }
            val fj = t.getJSONArray("fks")
            val fks = (0 until fj.length()).map { k ->
                val f = fj.getJSONObject(k)
                DbFk(f.optString("from", ""), f.optString("table", ""), if (f.isNull("to")) "" else f.getString("to"))
            }
            DbTable(
                t.getString("name"), t.getString("type") == "view", t.optString("sql", ""), cols, fks,
                if (t.isNull("rows")) null else t.getInt("rows"), strings(t.optJSONArray("indexes")), strings(t.optJSONArray("triggers"))
            )
        }
        DbSchema(tables, o.optBoolean("in_tx", false), o.optString("version", ""))
    } catch (e: Throwable) {
        null
    }

    fun stop() {
        try { mod().callAttr("stop") } catch (e: Throwable) { }
    }

    fun close(path: String) {
        try { mod().callAttr("close_db", path) } catch (e: Throwable) { }
    }

    fun dump(ctx: Context, path: String): String? = try {
        PyEngine.ensure(ctx)
        mod().callAttr("dump", path).toString()
    } catch (e: Throwable) { null }
}

/** Where the lab's databases live (app-private storage) plus the query history. */
object DbStore {
    private const val PREF = "dbms_prefs"
    const val MAX_HISTORY = 60

    val templates = linkedMapOf(
        "Empty" to "",
        "College" to "dbms/college.sql",
        "Company" to "dbms/company.sql",
        "Library" to "dbms/library.sql",
        "Bank" to "dbms/bank.sql"
    )
    val templateInfo = mapOf(
        "Empty" to "Start from nothing",
        "College" to "Student · Course · Enrolment",
        "Company" to "Employee · Department · Project",
        "Library" to "Author · Book · Member · Loan",
        "Bank" to "Account · Txn with a trigger"
    )

    fun dir(ctx: Context): File = File(ctx.filesDir, "dbms").also { it.mkdirs() }
    fun path(ctx: Context, name: String): String = File(dir(ctx), "$name.db").absolutePath

    fun list(ctx: Context): List<String> =
        (dir(ctx).listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".db") }
            .map { it.name.removeSuffix(".db") }.sortedBy { it.lowercase() }

    fun validName(n: String): Boolean = Regex("^[A-Za-z0-9_][A-Za-z0-9_ \\-]{0,29}$").matches(n)

    fun cleanName(raw: String): String {
        val s = raw.trim().replace(Regex("[^A-Za-z0-9_ \\-]"), "_").trim()
        val t = if (s.isEmpty() || !(s[0].isLetterOrDigit() || s[0] == '_')) "db_$s" else s
        return t.take(30).trim().ifEmpty { "database" }
    }

    fun uniqueName(ctx: Context, base: String): String {
        val have = list(ctx).map { it.lowercase() }.toSet()
        if (base.lowercase() !in have) return base
        var i = 2
        while ("${base.take(26)}_$i".lowercase() in have) i++
        return "${base.take(26)}_$i"
    }

    fun deleteFiles(ctx: Context, name: String) {
        val p = path(ctx, name)
        DbEngine.close(p)
        for (suffix in listOf("", "-journal", "-wal", "-shm")) File(p + suffix).delete()
    }

    fun readAsset(ctx: Context, asset: String): String =
        ctx.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }

    // ------------------------------------------------------------- history (saved in SharedPreferences)

    fun history(ctx: Context): List<String> = try {
        val a = JSONArray(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("history", "[]") ?: "[]")
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun saveHistory(ctx: Context, list: List<String>) {
        val a = JSONArray()
        list.take(MAX_HISTORY).forEach { a.put(it) }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("history", a.toString()).apply()
    }

    /** Puts a query on top of the history (an identical earlier entry moves up instead of repeating). */
    fun addHistory(ctx: Context, sql: String): List<String> {
        val t = sql.trim()
        if (t.isEmpty()) return history(ctx)
        val list = listOf(t) + history(ctx).filter { it != t }
        saveHistory(ctx, list)
        return list.take(MAX_HISTORY)
    }
}

fun toastOnMain(ctx: Context, msg: String) {
    Handler(Looper.getMainLooper()).post { Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show() }
}
