package com.example.mymeetings.data.local

import android.content.Context
import java.security.SecureRandom
import java.util.Base64

object DatabaseKeyProvider {
    private const val PREFS_NAME = "secure_db_prefs"
    private const val KEY_PASSPHRASE = "db_passphrase"

    /**
     * Obtains the unique database passphrase, generating a random 256-bit key
     * and storing it inside private, sandboxed SharedPreferences if it does not exist.
     */
    fun getOrGeneratePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var encoded = prefs.getString(KEY_PASSPHRASE, null)
        if (encoded == null) {
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            encoded = Base64.getEncoder().encodeToString(key)
            prefs.edit().putString(KEY_PASSPHRASE, encoded).apply()
        }
        return Base64.getDecoder().decode(encoded)
    }
}
