package ru.maxx.app.data.repository

import android.content.Context
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.maxx.app.core.crypto.E2ECrypto
import ru.maxx.app.core.network.MaxSocket
import ru.maxx.app.core.protocol.MaxProtocol

/**
 * E2ERepository: обмен публичными ключами, шифрование/расшифровка сообщений.
 *
 * Протокол обмена ключами использует opcode 22 (PRIVACY) с расширенными полями.
 * Зашифрованные сообщения передаются как обычный текст с префиксом "__e2e__"
 * и JSON-телом EncryptedPayload — сервер не видит контент.
 */
class E2ERepository(
    private val socket: MaxSocket,
    private val crypto: E2ECrypto,
    private val ctx: Context
) {
    private val keyCache = ConcurrentHashMap<Long, String>() // userId -> publicKey B64 (thread-safe)

    // Инициализация: публикуем свой публичный ключ
    suspend fun publishPublicKey() {
        val pubKey = crypto.exportPublicKey()
        socket.send(MaxProtocol.Op.PRIVACY, mapOf(
            "e2ePublicKey" to pubKey,
            "e2eVersion"   to 1
        ))
    }

    // Получение публичного ключа собеседника
    suspend fun getRecipientPublicKey(userId: Long): String? {
        keyCache[userId]?.let { return it }
        val pkt = socket.sendAndAwait(MaxProtocol.Op.CONTACT_FIND, mapOf(
            "userId" to userId, "requestE2EKey" to true
        )) ?: return null
        val key = pkt.payload["e2ePublicKey"]?.toString() ?: return null
        keyCache[userId] = key
        return key
    }

    // Шифрование текста для отправки
    suspend fun encryptForUser(userId: Long, plaintext: String): String? {
        val recipientKey = getRecipientPublicKey(userId) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val payload = crypto.encrypt(plaintext, recipientKey)
                "__e2e__{"c":"${payload.ciphertext}","i":"${payload.iv}","k":"${payload.encryptedKey}"}"
            }.getOrNull()
        }
    }

    // Расшифровка полученного сообщения
    suspend fun decryptMessage(encryptedText: String): String? {
        if (!encryptedText.startsWith("__e2e__")) return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val json = encryptedText.removePrefix("__e2e__")
                val obj = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
                val payload = E2ECrypto.EncryptedPayload(
                    ciphertext   = obj["c"]!!.jsonPrimitive.content,
                    iv           = obj["i"]!!.jsonPrimitive.content,
                    encryptedKey = obj["k"]!!.jsonPrimitive.content
                )
                crypto.decrypt(payload)
            }.getOrNull()
        }
    }

    fun isE2EMessage(text: String) = text.startsWith("__e2e__")
}
