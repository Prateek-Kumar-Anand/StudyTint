package com.student.smartstudy

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption for everything the app saves about the user. GCM also authenticates: changed, cut off
 * or swapped data is rejected instead of being read.
 *
 * Plain JVM code (no Android classes), so it can be tested on a computer.
 *
 * Every piece of data carries a "label" (for example "pref:progress:p_name") that is authenticated with it, so a
 * saved value cannot be moved into another place.
 */
internal object CryptoBox {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val CHUNK = 64 * 1024
    private const val FILE_ID_BYTES = 16
    private val BLOB = "STB1".toByteArray(Charsets.US_ASCII)
    private val STREAM = "STS1".toByteArray(Charsets.US_ASCII)

    private class Sealed(val iv: ByteArray, val body: ByteArray)

    private fun encrypt(key: SecretKey, plain: ByteArray, length: Int, aad: ByteArray): Sealed {
        val c = Cipher.getInstance(ALGORITHM)
        // No IV is passed in: the provider makes a fresh random one (the Android Keystore insists on this).
        c.init(Cipher.ENCRYPT_MODE, key)
        c.updateAAD(aad)
        val body = c.doFinal(plain, 0, length)
        val iv = c.iv
        if (iv == null || iv.size != IV_BYTES) throw GeneralSecurityException("Unexpected IV")
        return Sealed(iv, body)
    }

    private fun decrypt(key: SecretKey, iv: ByteArray, data: ByteArray, offset: Int, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance(ALGORITHM)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, iv))
        c.updateAAD(aad)
        return c.doFinal(data, offset, data.size - offset)
    }

    // ------------------------------------------------------------------ small values: one block

    fun seal(key: SecretKey, plain: ByteArray, label: String): ByteArray {
        val s = encrypt(key, plain, plain.size, label.toByteArray(Charsets.UTF_8))
        return BLOB + s.iv + s.body
    }

    fun open(key: SecretKey, sealed: ByteArray, label: String): ByteArray {
        val head = BLOB.size + IV_BYTES
        if (sealed.size < head + TAG_BYTES || !sealed.copyOfRange(0, BLOB.size).contentEquals(BLOB)) {
            throw GeneralSecurityException("Not an encrypted value")
        }
        val iv = sealed.copyOfRange(BLOB.size, head)
        return decrypt(key, iv, sealed, head, label.toByteArray(Charsets.UTF_8))
    }

    // ------------------------------------------------------------------ large files: chunks, little memory

    private fun chunkAad(label: String, fileId: ByteArray, index: Long, last: Boolean): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeUTF(label)
            write(fileId)
            writeLong(index)
            writeBoolean(last)
        }
        return bytes.toByteArray()
    }

    private fun fill(input: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    fun sealStream(key: SecretKey, input: InputStream, output: OutputStream, label: String) {
        val out = DataOutputStream(output)
        val fileId = ByteArray(FILE_ID_BYTES).also { SecureRandom().nextBytes(it) }
        out.write(STREAM)
        out.write(fileId)
        var current = ByteArray(CHUNK)
        var next = ByteArray(CHUNK)
        var size = fill(input, current)
        var index = 0L
        while (true) {
            val nextSize = if (size < CHUNK) 0 else fill(input, next)
            val last = nextSize == 0
            val s = encrypt(key, current, size, chunkAad(label, fileId, index, last))
            out.writeBoolean(last)
            out.writeInt(s.body.size)
            out.write(s.iv)
            out.write(s.body)
            if (last) break
            val spare = current
            current = next
            next = spare
            size = nextSize
            index++
        }
        out.flush()
    }

    /** Throws if the data was changed or is incomplete; the caller must then discard what was written. */
    fun openStream(key: SecretKey, input: InputStream, output: OutputStream, label: String) {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(STREAM.size).also { data.readFully(it) }
            if (!magic.contentEquals(STREAM)) throw GeneralSecurityException("Not an encrypted file")
            val fileId = ByteArray(FILE_ID_BYTES).also { data.readFully(it) }
            var index = 0L
            while (true) {
                val last = data.readBoolean()
                val length = data.readInt()
                if (length < TAG_BYTES || length > CHUNK + TAG_BYTES) throw GeneralSecurityException("Damaged file")
                val iv = ByteArray(IV_BYTES).also { data.readFully(it) }
                val body = ByteArray(length).also { data.readFully(it) }
                output.write(decrypt(key, iv, body, 0, chunkAad(label, fileId, index, last)))
                if (last) break
                index++
            }
        } catch (e: EOFException) {
            throw GeneralSecurityException("The encrypted file is cut off")
        }
        if (data.read() != -1) throw GeneralSecurityException("Unexpected data after the end of the file")
        output.flush()
    }
}
