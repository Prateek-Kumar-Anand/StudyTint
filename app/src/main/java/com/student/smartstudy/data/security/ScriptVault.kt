package com.student.smartstudy

import java.io.File
import javax.crypto.SecretKey

/**
 * Saved Python scripts. On disk they are `name.py.enc` (encrypted), including on phone storage and the SD card,
 * where other apps may be able to reach the files. Screens only see `name.py`. Plain JVM code.
 */
internal object ScriptVault {
    private const val SUFFIX = ".enc"
    private const val LABEL = "script"

    private fun sealed(dir: File, name: String) = File(dir, name + SUFFIX)

    fun list(dir: File): List<String> =
        (dir.listFiles() ?: emptyArray()).filter { it.isFile }.map { it.name.removeSuffix(SUFFIX) }
            .filter { it.endsWith(".py") }.distinct().sortedBy { it.lowercase() }

    fun read(dir: File, name: String, key: () -> SecretKey): String {
        val plain = File(dir, name)
        // A readable file with this name is newer: it was put there after the last save.
        val bytes = if (plain.isFile) plain.readBytes() else SecureFiles.read(sealed(dir, name), key(), LABEL)
        return bytes.toString(Charsets.UTF_8)
    }

    fun save(dir: File, name: String, text: String, key: () -> SecretKey) {
        SecureFiles.write(sealed(dir, name), text.toByteArray(Charsets.UTF_8), key(), LABEL)
        File(dir, name).delete()
    }

    fun delete(dir: File, name: String) {
        File(dir, name).delete()
        sealed(dir, name).delete()
    }

    /** Encrypts readable scripts left by older versions. An existing, different script is never overwritten. */
    fun encryptAll(dir: File, key: () -> SecretKey) {
        val plain = (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".py") }
        for (f in plain) {
            try {
                var name = f.name
                var n = 2
                while (sealed(dir, name).exists() && !sameText(sealed(dir, name), f, key())) {
                    name = "${f.name.removeSuffix(".py")}_$n.py"
                    n++
                }
                SecureFiles.encryptInPlace(f, sealed(dir, name), key(), LABEL)
            } catch (e: Exception) {
                // leave this file as it is; it is tried again at the next start
            }
        }
    }

    private fun sameText(enc: File, plain: File, key: SecretKey): Boolean = try {
        SecureFiles.read(enc, key, LABEL).contentEquals(plain.readBytes())
    } catch (e: Exception) { false }
}
