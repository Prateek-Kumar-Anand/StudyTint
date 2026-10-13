package com.student.smartstudy

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*

class Progress(ctx: Context) {
    private val sp = ctx.getSharedPreferences("progress", Context.MODE_PRIVATE)
    var done by mutableStateOf(sp.getStringSet("done", emptySet())!!.toSet())
        private set
    var stars by mutableStateOf(sp.getStringSet("stars", emptySet())!!.toSet())
        private set
    var read by mutableStateOf(sp.getStringSet("read", emptySet())!!.toSet())
        private set
    var stats by mutableStateOf(
        sp.getStringSet("stats", emptySet())!!.mapNotNull { e ->
            val k = e.lastIndexOf('=')
            if (k < 0) null else e.substring(0, k) to e.substring(k + 1).split(",").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        }.toMap()
    )
        private set
    var scores by mutableStateOf(
        sp.getStringSet("scores2", emptySet())!!.mapNotNull { e ->
            e.split("=").let { if (it.size == 2) it[0] to (it[1].toIntOrNull() ?: 0) else null }
        }.toMap()
    )
        private set

    fun toggle(key: String) {
        done = if (key in done) done - key else done + key
        sp.edit().putStringSet("done", done).apply()
    }
    fun star(key: String) {
        stars = if (key in stars) stars - key else stars + key
        sp.edit().putStringSet("stars", stars).apply()
    }
    private fun persist() {
        sp.edit().putStringSet("stats", stats.map { "${it.key}=${it.value.first},${it.value.second}" }.toSet()).apply()
    }
    fun record(key: String, topic: String, ok: Boolean) {
        val add = if (ok) 1 else 0
        fun bump(k: String) { val c = stats[k] ?: (0 to 0); stats = stats + (k to (c.first + 1 to c.second + add)) }
        bump(key)
        if (topic.isNotEmpty()) bump("$key#$topic")
        persist()
    }
    fun attempt(key: String) {
        val c = stats["$key@"] ?: (0 to 0)
        stats = stats + ("$key@" to (c.first + 1 to 0))
        persist()
    }
    fun markRead(key: String) {
        if (key !in read) {
            read = read + key
            sp.edit().putStringSet("read", read).apply()
        }
    }
    fun save(key: String, value: Int) {
        if (value > (scores[key] ?: -1)) {
            scores = scores + (key to value)
            sp.edit().putStringSet("scores2", scores.map { "${it.key}=${it.value}" }.toSet()).apply()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadMathBank(applicationContext)
        loadRich(applicationContext)
        setContent { MaterialTheme { App(remember { Progress(applicationContext) }) } }
    }
}
