package ru.maxx.app.core.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * E2E шифрование через Android Keystore + X25519 (эмуляция через RSA-OAEP) + AES-256-GCM.
 *
 * Схема:
 * 1. Каждый пользователь генерирует RSA-2048 keypair в Keystore.
 * 2. Публичный ключ публикуется (через отдельный API вызов).
 * 3. При отправке: генерируем AES-256 session key, шифруем им сообщение (GCM),
 *    шифруем session key публичным ключом получателя (RSA-OAEP).
 * 4. При получении: расшифровываем session key приватным ключом, им расшифровываем сообщение.
 */
class E2ECrypto(private val ctx: Context) {

    @Volatile private var cachedKeyPair: KeyPair? = null

    companion object {
        private const val KEYSTORE   = "AndroidKeyStore"
        private const val KEY_ALIAS  = "maxx_e2e_key_v1"
        private const val RSA_ALGO   = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val AES_ALGO   = "AES/GCM/NoPadding"
        private const val GCM_TAG    = 128
        private const val GCM_IV_LEN = 12
    }

    data class EncryptedPayload(
        val ciphertext: String,   // Base64 зашифрованного текста
        val iv: String,           // Base64 IV для GCM
        val encryptedKey: String, // Base64 AES ключа, зашифрованного RSA
        val version: Int = 1
    )

    // Генерация или загрузка RSA keypair из Keystore (KeyStore.load кешируется)
    fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }
        val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            return KeyPair(entry.certificate.publicKey, entry.privateKey).also { cachedKeyPair = it }
        }
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setKeySize(2048)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            setDigests(KeyProperties.DIGEST_SHA256)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(false) // StrongBox опционально
            }
            setUserAuthenticationRequired(false) // не требуем биометрию для шифрования
        }.build()
        kpg.initialize(spec)
        return kpg.generateKeyPair().also { cachedKeyPair = it }
    }

    // Экспорт публичного ключа в Base64 для передачи собеседнику
    fun exportPublicKey(): String {
        val kp = getOrCreateKeyPair()
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    // Шифрование сообщения публичным ключом получателя
    fun encrypt(plaintext: String, recipientPublicKeyB64: String): EncryptedPayload {
        val recipientPubKeyBytes = Base64.decode(recipientPublicKeyB64, Base64.NO_WRAP)
        val recipientPubKey: PublicKey = java.security.KeyFactory
            .getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            .generatePublic(java.security.spec.X509EncodedKeySpec(recipientPubKeyBytes))

        // Генерируем ephemeral AES-256 ключ
        val aesKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        // Шифруем сообщение AES-GCM
        val iv = ByteArray(GCM_IV_LEN).also { java.security.SecureRandom().nextBytes(it) }
        val aesCipher = Cipher.getInstance(AES_ALGO)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG, iv))
        val ciphertext = aesCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Шифруем AES ключ публичным RSA ключом получателя
        val rsaCipher = Cipher.getInstance(RSA_ALGO)
        rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPubKey)
        val encryptedKey = rsaCipher.doFinal(aesKey.encoded)

        return EncryptedPayload(
            ciphertext   = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iv           = Base64.encodeToString(iv, Base64.NO_WRAP),
            encryptedKey = Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
        )
    }

    // Расшифровка своим приватным ключом
    fun decrypt(payload: EncryptedPayload): String {
        val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Приватный ключ не найден")

        // Расшифровываем AES ключ
        val encKeyBytes = Base64.decode(payload.encryptedKey, Base64.NO_WRAP)
        val rsaCipher = Cipher.getInstance(RSA_ALGO)
        rsaCipher.init(Cipher.DECRYPT_MODE, entry.privateKey)
        val aesKeyBytes = rsaCipher.doFinal(encKeyBytes)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        // Расшифровываем сообщение
        val iv = Base64.decode(payload.iv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.ciphertext, Base64.NO_WRAP)
        val aesCipher = Cipher.getInstance(AES_ALGO)
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG, iv))
        return String(aesCipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // Удаление ключей (при выходе)
    fun deleteKeys() {
        cachedKeyPair = null
        runCatching {
            KeyStore.getInstance(KEYSTORE).also { it.load(null) }.deleteEntry(KEY_ALIAS)
        }
    }
}
