package ro.ocsike.toyotaclima

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Criptare AES-GCM cu o cheie ținută în Android Keystore.
 * Cheia nu poate fi extrasă din telefon; textul criptat e salvat ca "v1:<iv>:<date>".
 */
object Secure {
    private const val ALIAS = "toyclimate_secrets"
    private const val PREFIX = "v1:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return PREFIX + b64.encodeToString(cipher.iv) + ":" + b64.encodeToString(data)
    }

    /** null dacă textul nu e în formatul nostru sau cheia s-a pierdut (ex. telefon resetat). */
    fun decrypt(stored: String): String? = try {
        if (!stored.startsWith(PREFIX)) {
            null
        } else {
            val parts = stored.removePrefix(PREFIX).split(':')
            val b64 = Base64.getDecoder()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b64.decode(parts[0])))
            String(cipher.doFinal(b64.decode(parts[1])), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}
