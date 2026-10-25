package com.student.smartstudy

import android.content.Context

/**
 * Update support.
 *
 * To ship a new version:
 *  1. Edit version.properties (raise VERSION_CODE, set VERSION_NAME).
 *  2. Add a Release to [changelog] below (newest first) so users see "What's new".
 *  3. If saved data changes shape, raise [DATA_VERSION] and add a step in [migrate].
 *  4. Push (or tag vX.Y.Z to get a GitHub Release). The new APK installs over the old one and keeps all data.
 */
object Updates {
    /** Bump when the format of saved data changes, then add a step in [migrate]. */
    const val DATA_VERSION = 2

    data class Release(val name: String, val notes: List<String>)

    val changelog = listOf(
        Release(
            "1.3.0",
            listOf(
                "Python Lab: new \"Get online\" tab. Search PyPI or pick a popular library and save it, with the libraries it needs, straight to internal storage, phone storage or the SD card",
                "Downloads are checked against PyPI's checksum, and only libraries that can run on a phone (pure Python) are offered"
            )
        ),
        Release(
            "1.2.0",
            listOf(
                "C Lab and Python Lab: type input right in the output box, like a terminal",
                "C Lab: programs run without #include lines (printf, scanf, sqrt, bool and more are added for you)",
                "C Lab: main() without a return type or return statement now works",
                "Profile: add, change or remove a profile photo; it is saved on your device"
            )
        ),
        Release(
            "1.1.0",
            listOf(
                "New Python Lab: write and run Python with real error messages",
                "Choose where Python files live: internal storage, phone storage or SD card",
                "Import your own libraries from a folder, .py, .zip or .whl file",
                "C Lab now shows compiler-style errors and input warnings",
                "Smoother scrolling and nicer cards"
            )
        )
    )

    private const val PREF = "app_meta"

    fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    /** Runs once at start-up. Upgrades saved data step by step, never deleting anything. */
    fun migrate(ctx: Context) {
        val meta = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        var v = meta.getInt("data_version", 1)
        while (v < DATA_VERSION) {
            when (v) {
                1 -> { /* 1 -> 2: nothing to convert; Python Lab keeps its own preferences. */ }
                // 2 -> { migrate from version 2 to 3 here }
            }
            v++
        }
        meta.edit().putInt("data_version", DATA_VERSION).apply()
    }

    /** The release notes to show once after an update, or null. */
    fun pendingRelease(ctx: Context): Release? {
        val meta = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val current = versionName(ctx)
        val seen = meta.getString("seen_version", null)
        if (seen == current) return null
        val existingUser = ctx.getSharedPreferences("progress", Context.MODE_PRIVATE).contains("p_name")
        if (seen == null && !existingUser) {
            meta.edit().putString("seen_version", current).apply()
            return null
        }
        return changelog.firstOrNull { it.name == current }
    }

    fun markSeen(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("seen_version", versionName(ctx)).apply()
    }
}
