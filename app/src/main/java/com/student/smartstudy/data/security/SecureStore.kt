package com.student.smartstudy

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.SecretKey

/**
 * Encrypted replacement for SharedPreferences. A value for `key` is saved as `enc:key`, encrypted.
 *
 * A plain `key` entry can only come from an older version of the app; it is read as it is and encrypted by
 * [encryptExisting]. A value that cannot be decrypted is never overwritten.
 */
internal class SecureStore(
    private val prefs: SharedPreferences, private val name: String, private val key: () -> SecretKey
) {
    private val unreadable = HashSet<String>()

    private fun label(k: String) = "pref:$name:$k"

    @Synchronized private fun read(k: String): Any? {
        if (prefs.contains(k)) return prefs.all[k]
        val saved = prefs.getString(PREFIX + k, null) ?: return null
        return try {
            decode(CryptoBox.open(key(), Base64.decode(saved, Base64.NO_WRAP), label(k)))
        } catch (e: Exception) {
            unreadable += k
            Vault.reportProblem()
            null
        }
    }

    fun contains(k: String): Boolean = prefs.contains(k) || prefs.contains(PREFIX + k)
    fun getString(k: String, def: String?): String? = read(k) as? String ?: def
    fun getInt(k: String, def: Int): Int = read(k) as? Int ?: def
    fun getBoolean(k: String, def: Boolean): Boolean = read(k) as? Boolean ?: def
    fun getStringSet(k: String, def: Set<String>): Set<String> =
        (read(k) as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: def

    fun edit() = Editor()

    inner class Editor {
        private val changes = LinkedHashMap<String, Any>()

        fun putString(k: String, v: String) = apply { changes[k] = v }
        fun putInt(k: String, v: Int) = apply { changes[k] = v }
        fun putBoolean(k: String, v: Boolean) = apply { changes[k] = v }
        fun putStringSet(k: String, v: Set<String>) = apply { changes[k] = v.toSet() }

        fun apply() {
            synchronized(this@SecureStore) {
                val editor = prefs.edit()
                for ((k, v) in changes) {
                    if (k in unreadable) continue
                    try {
                        editor.putString(PREFIX + k, sealed(k, v)).remove(k)
                    } catch (e: Exception) {
                        Vault.reportProblem()       // keys unavailable: keep what is saved, never write it in the clear
                    }
                }
                editor.apply()
            }
        }
    }

    /** Encrypts everything an older version saved in the clear, in one atomic write. */
    @Synchronized fun encryptExisting() {
        val plain = prefs.all.filterKeys { !it.startsWith(PREFIX) }
        if (plain.isEmpty()) return
        try {
            val editor = prefs.edit()
            for ((k, v) in plain) {
                if (v == null) continue
                editor.putString(PREFIX + k, sealed(k, v)).remove(k)
            }
            editor.commit()
        } catch (e: Exception) {
            Vault.reportProblem()
        }
    }

    private fun sealed(k: String, v: Any): String =
        Base64.encodeToString(CryptoBox.seal(key(), encode(v), label(k)), Base64.NO_WRAP)

    private fun encode(v: Any): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        fun text(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            out.writeInt(b.size)
            out.write(b)
        }
        when (v) {
            is String -> { out.writeByte('s'.code); text(v) }
            is Int -> { out.writeByte('i'.code); out.writeInt(v) }
            is Boolean -> { out.writeByte('b'.code); out.writeBoolean(v) }
            is Set<*> -> { out.writeByte('l'.code); out.writeInt(v.size); v.forEach { text(it as String) } }
            else -> throw IllegalArgumentException("Unsupported value")
        }
        return bytes.toByteArray()
    }

    private fun decode(bytes: ByteArray): Any {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        fun text(): String = ByteArray(input.readInt()).also { input.readFully(it) }.toString(Charsets.UTF_8)
        return when (input.readByte().toInt().toChar()) {
            's' -> text()
            'i' -> input.readInt()
            'b' -> input.readBoolean()
            'l' -> List(input.readInt()) { text() }.toSet()
            else -> throw IllegalStateException("Unknown value type")
        }
    }

    companion object {
        private const val PREFIX = "enc:"
        private val stores = HashMap<String, SecureStore>()

        /** The preference files that hold anything the user typed or did. */
        val NAMES = listOf("progress", "pylab_prefs", "clab_prefs", "dbms_prefs")

        @Synchronized fun of(ctx: Context, name: String): SecureStore {
            val app = ctx.applicationContext
            return stores.getOrPut(name) {
                SecureStore(app.getSharedPreferences(name, Context.MODE_PRIVATE), name) { Vault.key(app) }
            }
        }
    }
}
