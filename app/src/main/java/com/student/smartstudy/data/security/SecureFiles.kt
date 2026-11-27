package com.student.smartstudy

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.crypto.SecretKey

/** Safe file writing and whole-file encryption. Plain JVM code. */
internal object SecureFiles {
    /** Writes to a temporary file, forces it to disk, then swaps it in, so a crash never leaves half a file. */
    fun writeAtomic(target: File, write: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        try {
            FileOutputStream(tmp).use {
                write(it)
                it.flush()
                it.fd.sync()
            }
            replace(tmp, target)
        } finally {
            tmp.delete()
        }
    }

    /** Moves [from] over [to]. SD cards may refuse to rename onto an existing file, so the old one is set aside first. */
    fun replace(from: File, to: File) {
        if (from.renameTo(to)) return
        val old = File(to.path + ".old")
        old.delete()
        if (to.exists() && !to.renameTo(old)) throw IOException("Could not replace ${to.name}")
        if (!from.renameTo(to)) {
            old.renameTo(to)
            throw IOException("Could not save ${to.name}")
        }
        old.delete()
    }

    fun write(file: File, plain: ByteArray, key: SecretKey, label: String) {
        val sealed = CryptoBox.seal(key, plain, label)
        writeAtomic(file) { it.write(sealed) }
    }

    fun read(file: File, key: SecretKey, label: String): ByteArray = CryptoBox.open(key, file.readBytes(), label)

    /** Encrypts [plain] into [sealed]. The readable original is deleted only after the copy reads back correctly. */
    fun encryptInPlace(plain: File, sealed: File, key: SecretKey, label: String) {
        val bytes = plain.readBytes()
        write(sealed, bytes, key, label)
        if (!read(sealed, key, label).contentEquals(bytes)) throw IOException("Could not verify ${sealed.name}")
        plain.delete()
    }
}
