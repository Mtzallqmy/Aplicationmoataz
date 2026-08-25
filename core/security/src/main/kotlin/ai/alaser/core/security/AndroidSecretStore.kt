package ai.alaser.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("alaser_encrypted_secrets", Context.MODE_PRIVATE)

    fun put(id: String, value: String) {
        require(id.isNotBlank()) { "A secret identifier is required." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = encode(cipher.iv) + ":" + encode(ciphertext)
        check(preferences.edit().putString(id, encoded).commit()) { "The encrypted secret could not be saved." }
    }

    fun get(id: String): String? {
        val payload = preferences.getString(id, null) ?: return null
        val parts = payload.split(':', limit = 2)
        require(parts.size == 2) { "The encrypted secret payload is invalid." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(parts[0])))
        return cipher.doFinal(decode(parts[1])).toString(Charsets.UTF_8)
    }

    fun delete(id: String) {
        preferences.edit().remove(id).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val KEY_ALIAS = "ai.alaser.secrets.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
