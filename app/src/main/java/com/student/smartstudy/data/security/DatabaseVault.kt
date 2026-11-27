package com.student.smartstudy

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import javax.crypto.SecretKey

/**
 * Keeps the DBMS Lab databases encrypted whenever they are not in use.
 *
 *  - at rest:  `name.db.enc`
 *  - in use:   `name.db` (SQLite needs a readable file); [seal] turns it back into the encrypted form.
 *
 * When both exist, `name.db` is the newer one (the app was closed before it could be sealed).
 * Nothing readable is deleted before the encrypted copy has been decrypted again and compared.
 *
 * [settle] must close every connection to the file and fold journals back into it; it returns false when that
 * is not possible yet (for example an open transaction). Plain JVM code.
 */
internal class DatabaseVault(private val key: () -> SecretKey, private val settle: (File) -> Boolean) {
    private val sideFiles = listOf("-journal", "-wal", "-shm")

    private fun sealedFile(db: File) = File(db.path + ".enc")
    private fun openingFile(db: File) = File(db.path + ".opening")

    /** Database names (without extension) in [dir], whether sealed or in use. */
    fun names(dir: File): List<String> =
        (dir.listFiles() ?: emptyArray()).filter { it.isFile }.map { it.name.removeSuffix(".enc") }
            .filter { it.endsWith(".db") }.map { it.removeSuffix(".db") }.distinct().sortedBy { it.lowercase() }

    /**
     * Runs [block] with the readable database in place. Throws, without running it, when the database exists but
     * cannot be decrypted, so SQLite never creates an empty file over it.
     */
    @Synchronized fun <T> use(db: File, sealAfterwards: () -> Boolean = { false }, block: () -> T): T {
        unlock(db)
        try {
            return block()
        } finally {
            if (sealAfterwards()) seal(db)
        }
    }

    private fun unlock(db: File) {
        val enc = sealedFile(db)
        if (db.exists() || !enc.exists()) return
        val tmp = openingFile(db)
        try {
            enc.inputStream().buffered().use { input ->
                tmp.outputStream().use { CryptoBox.openStream(key(), input, it, LABEL) }
            }
            sideFiles.forEach { File(db.path + it).delete() }    // left over from before it was sealed
            if (!tmp.renameTo(db)) throw IOException("Could not open the database")
        } finally {
            tmp.delete()
        }
    }

    /** Encrypts a database that is in use. Returns false (keeping the readable file) when it has to wait. */
    @Synchronized fun seal(db: File): Boolean {
        openingFile(db).delete()
        if (!db.exists()) return true
        return try {
            if (!settle(db)) return false
            val wal = File(db.path + "-wal")
            if (wal.length() > 0) return false
            encrypt(sealedFile(db)) { db.inputStream() }
            if (!db.delete()) return false
            sideFiles.forEach { File(db.path + it).delete() }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sealAll(dir: File) {
        names(dir).forEach { seal(File(dir, "$it.db")) }
    }

    /** Saves an imported database straight into the encrypted form. */
    @Synchronized fun importBytes(db: File, bytes: ByteArray) {
        encrypt(sealedFile(db)) { bytes.inputStream() }
    }

    @Synchronized fun delete(db: File, close: () -> Unit) {
        close()
        sealedFile(db).delete()
        db.delete()
        openingFile(db).delete()
        sideFiles.forEach { File(db.path + it).delete() }
    }

    private fun encrypt(target: File, source: () -> java.io.InputStream) {
        val secret = key()
        val tmp = File(target.path + ".new")
        try {
            val plainHash = MessageDigest.getInstance("SHA-256")
            SecureFiles.writeAtomic(tmp) { out ->
                DigestInputStream(source().buffered(), plainHash).use { CryptoBox.sealStream(secret, it, out, LABEL) }
            }
            val checkHash = MessageDigest.getInstance("SHA-256")
            val discard = object : OutputStream() {
                override fun write(b: Int) {}
                override fun write(b: ByteArray, off: Int, len: Int) {}
            }
            tmp.inputStream().buffered().use {
                CryptoBox.openStream(secret, it, DigestOutputStream(discard, checkHash), LABEL)
            }
            if (!plainHash.digest().contentEquals(checkHash.digest())) throw IOException("Encryption check failed")
            SecureFiles.replace(tmp, target)
        } finally {
            tmp.delete()
        }
    }

    private companion object {
        const val LABEL = "database"
    }
}
