package com.student.smartstudy

import android.content.Context
import java.io.File

/**
 * Encrypts anything still readable on disk: data from older versions, or a database that was open when the app
 * was closed. Safe to run at every start; it does nothing when everything is already encrypted.
 */
internal fun encryptExistingData(ctx: Context) {
    val app = ctx.applicationContext
    try { Vault.key(app) } catch (e: Exception) { return }       // nothing is touched without working keys
    SecureStore.NAMES.forEach { SecureStore.of(app, it).encryptExisting() }
    ProfilePhoto.encryptExisting(app)
    PyStore.values().forEach { kind ->
        val base = PyStorage.baseFor(app, kind) ?: return@forEach
        ScriptVault.encryptAll(File(base, "PyLab/scripts")) { Vault.key(app) }
    }
    Thread { DbEngine.sealAll(app) }.start()
}
