package com.student.smartstudy

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

sealed interface Blk
data class BP(val x: String) : Blk
data class BH(val x: String) : Blk
data class BUl(val x: List<String>) : Blk
data class BOl(val x: List<String>) : Blk
data class BLn(val x: List<String>) : Blk
data class BCode(val x: List<String>) : Blk
data class BEx(val h: String, val x: List<String>) : Blk
data class BNote(val k: String, val h: String, val x: List<String>) : Blk
data class BKv(val x: List<Pair<String, String>>) : Blk
data class BGl(val x: List<Pair<String, String>>) : Blk
data class BQa(val x: List<Pair<String, String>>) : Blk

data class Sec(val n: Int, val t: String, val k: String, val s: String, val b: List<Blk>)
data class Rich(val title: String, val intro: String, val sec: List<Sec>)

var rich: Map<String, Rich> = emptyMap()
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

fun loadRich(ctx: Context) {
    val m = mutableMapOf<String, Rich>()
    for ((key, file) in listOf("ma:0" to "rich_ma_0.json", "ma:1" to "rich_ma_1.json", "ma:2" to "rich_ma_2.json", "ma:3" to "rich_ma_3.json", "db:0" to "rich_db_0.json", "db:1" to "rich_db_1.json", "db:2" to "rich_db_2.json", "db:3" to "rich_db_3.json", "ai:0" to "rich_ai_0.json", "ai:1" to "rich_ai_1.json", "ai:2" to "rich_ai_2.json", "ai:3" to "rich_ai_3.json")) {
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
