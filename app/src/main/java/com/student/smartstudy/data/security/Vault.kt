package com.student.smartstudy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * The app's encryption keys.
 *
 *  - A master key is created inside the Android Keystore. It cannot be read or copied out of the phone (on most
 *    phones it sits in secure hardware).
 *  - A random data key does the actual encrypting. Only its form wrapped by the master key is saved.
 *
 * So the app's files are useless when copied to another device. If the keys cannot be used, nothing is
 * regenerated or overwritten: the saved data stays as it is and [problem] is set.
 */
internal object Vault {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "studytint.master.v1"
    private const val LABEL = "data-key"
    private const val KEY_BYTES = 32

    @Volatile var problem = false
        private set
    @Volatile private var cached: SecretKey? = null

    fun reportProblem() {
        problem = true
    }

    @Synchronized fun key(ctx: Context): SecretKey {
        cached?.let { return it }
        try {
            val file = File(ctx.applicationContext.filesDir, "vault/data.key")
            if (!file.exists()) create(file)
            // Always read it back from disk: nothing is encrypted with a key that could not be recovered later.
            val master = existingMaster() ?: throw IllegalStateException("The master key is missing")
            val raw = CryptoBox.open(master, file.readBytes(), LABEL)
            check(raw.size == KEY_BYTES) { "Damaged data key" }
            return SecretKeySpec(raw, "AES").also { cached = it }
        } catch (e: Exception) {
            problem = true
            throw e
        }
    }

    private fun create(file: File) {
        val master = existingMaster() ?: newMaster()
        val raw = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = CryptoBox.seal(master, raw, LABEL)
        SecureFiles.writeAtomic(file) { it.write(wrapped) }
        raw.fill(0)
    }

    private fun existingMaster(): SecretKey? {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return store.getKey(MASTER_ALIAS, null) as SecretKey?
    }

    private fun newMaster(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BYTES * 8)
                .build()
        )
        return generator.generateKey()
    }
}
