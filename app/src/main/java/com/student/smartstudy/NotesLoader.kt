package com.student.smartstudy

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

sealed interface Blk
@Immutable
data class BP(val x: String) : Blk
@Immutable
data class BH(val x: String) : Blk
@Immutable
data class BUl(val x: List<String>) : Blk
@Immutable
data class BOl(val x: List<String>) : Blk
@Immutable
data class BLn(val x: List<String>) : Blk
@Immutable
data class BCode(val x: List<String>) : Blk
@Immutable
data class BEx(val h: String, val x: List<String>) : Blk
@Immutable
data class BNote(val k: String, val h: String, val x: List<String>) : Blk
@Immutable
data class BKv(val x: List<Pair<String, String>>) : Blk
@Immutable
data class BGl(val x: List<Pair<String, String>>) : Blk
@Immutable
data class BQa(val x: List<Pair<String, String>>) : Blk

@Immutable
data class Sec(val n: Int, val t: String, val k: String, val s: String, val b: List<Blk>)
@Immutable
data class Rich(val title: String, val intro: String, val sec: List<Sec>)

/** The detailed notes. Filled in by [loadRich] in the background; being Compose state, screens update the moment it is ready. */
var rich: Map<String, Rich> by mutableStateOf(emptyMap())
    private set

private fun strs(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
private fun pairs(a: JSONArray) = (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } }

private fun blk(o: JSONObject): Blk = when (o.getString("t")) {
    "p" -> BP(o.getString("x"))
    "h" -> BH(o.getString("x"))
    "ul" -> BUl(strs(o.getJSONArray("x")))
    "ol" -> BOl(strs(o.getJSONArray("x")))
    "ln" -> BLn(strs(o.getJSONArray("x")))
    "code" -> BCode(strs(o.getJSONArray("x")))
    "ex" -> BEx(o.getString("h"), strs(o.getJSONArray("x")))
    "note" -> BNote(o.getString("k"), o.getString("h"), strs(o.getJSONArray("x")))
    "kv" -> BKv(pairs(o.getJSONArray("x")))
    "gl" -> BGl(pairs(o.getJSONArray("x")))
    "qa" -> BQa(pairs(o.getJSONArray("x")))
    else -> BP(o.optString("x"))
}

private val NOTE_FILES = listOf(
    "ma:0" to "maths_unit1_notes.json",
    "ma:1" to "maths_unit2_notes.json",
    "ma:2" to "maths_unit3_notes.json",
    "ma:3" to "maths_unit4_notes.json",
    "db:0" to "dbms_unit1_notes.json",
    "db:1" to "dbms_unit2_notes.json",
    "db:2" to "dbms_unit3_notes.json",
    "db:3" to "dbms_unit4_notes.json",
    "ai:0" to "ai_unit1_notes.json",
    "ai:1" to "ai_unit2_notes.json",
    "ai:2" to "ai_unit3_notes.json",
    "ai:3" to "ai_unit4_notes.json",
    "py:0" to "python_unit1_notes.json",
    "py:1" to "python_unit2_notes.json",
    "py:2" to "python_unit3_notes.json",
    "py:3" to "python_unit4_notes.json",
    "c:0" to "c_unit1_notes.json",
    "c:1" to "c_unit2_notes.json",
    "c:2" to "c_unit3_notes.json",
    "c:3" to "c_unit4_notes.json"
)

/** Parses the notes on a background thread so the app opens straight away (about 2.7 MB of JSON). */
fun loadRich(ctx: Context) {
    val app = ctx.applicationContext
    Thread(null, { loadRichNow(app) }, "load-notes").start()
}

private fun loadRichNow(ctx: Context) {
    val m = mutableMapOf<String, Rich>()
    for ((key, file) in NOTE_FILES) {
        try {
            val o = JSONObject(ctx.assets.open(file).bufferedReader().use { it.readText() })
            val sa = o.getJSONArray("sec")
            val secs = (0 until sa.length()).map { i ->
                val s = sa.getJSONObject(i)
                val ba = s.getJSONArray("b")
                Sec(s.getInt("n"), s.getString("t"), s.getString("k"), s.getString("s"), (0 until ba.length()).map { j -> blk(ba.getJSONObject(j)) })
            }
            m[key] = Rich(o.getString("title"), o.getString("intro"), secs)
        } catch (e: Exception) {
            Log.e("SmartStudy", "Notes failed to load: $file", e)
        }
    }
    rich = m
}
