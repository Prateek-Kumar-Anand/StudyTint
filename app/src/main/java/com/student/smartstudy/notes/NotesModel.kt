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

/** A real table: optional caption [c], header cells [h] (may be empty) and body rows [r]. */
@Immutable
data class BTb(val c: String, val h: List<String>, val r: List<List<String>>) : Blk

@Immutable
data class Sec(val n: Int, val t: String, val k: String, val s: String, val b: List<Blk>)

@Immutable
data class Rich(val title: String, val intro: String, val sec: List<Sec>)
