package ru.maxx.app.core.protocol

import net.jpountz.lz4.LZ4Factory
import org.msgpack.core.MessagePack
import org.msgpack.value.Value
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MaxProtocol {
    const val VER = 10
    const val CMD_REQUEST: Short = 0x0000
    const val CMD_OK: Short = 256.toShort()
    const val CMD_ERROR: Short = 768.toShort()

    object Op {
        const val PING = 1; const val PONG = 2; const val HANDSHAKE = 6
        const val AUTH_PHONE = 17; const val AUTH_VERIFY = 18; const val AUTH_CHATS = 19
        const val PRIVACY = 22; const val MEDIA_UPLOAD = 25; const val SEARCH_CHANNELS = 32
        const val CONTACTS_LOAD = 35; const val CONTACT_FIND = 46; const val CONTACTS_PRESENCE = 54
        const val MESSAGES_LOAD = 55; const val JOIN_GROUP = 57; const val GROUP_MEMBERS = 59
        const val SEND_MESSAGE = 64; const val EDIT_MESSAGE = 65; const val DELETE_MESSAGE = 66
        const val READ_MESSAGES = 67; const val PRESENCE_SUBSCRIBE = 75; const val TYPING = 77
        const val MESSAGES_HISTORY = 79; const val MEDIA_LIST = 80; const val MEDIA_STICKERS = 83
        const val ENTER_CHANNEL = 89; const val SESSIONS_LIST = 96; const val SESSIONS_TERMINATE = 97
        const val AUTH_PASSWORD = 115; const val SET_PASSWORD = 116
        const val COMPLAINTS_GET = 162; const val FOLDERS_CREATE = 274; const val FOLDERS_UPDATE = 276
        const val REACTIONS_SEND = 178; const val REACTIONS_LIST = 179
        // HOST_REACHABILITY (5) НАМЕРЕННО ОТСУТСТВУЕТ
    }

    data class Packet(val ver: Int, val cmd: Short, val seq: Int, val opcode: Int, val payload: Map<String, Any?>)

    // Decompressor создаётся per-call (LZ4SafeDecompressor не thread-safe для одного экземпляра)
    private val lz4Factory by lazy { LZ4Factory.fastestInstance() }
    private val DECOMPRESS_MAX = 4 * 1024 * 1024 // 4MB

    fun pack(seq: Int, opcode: Int, payload: Map<String, Any?>): ByteArray {
        val body = msgpackSerialize(payload)
        return ByteBuffer.allocate(10 + body.size).order(ByteOrder.BIG_ENDIAN)
            .put(VER.toByte())
            .putShort(CMD_REQUEST)
            .put(seq.toByte())
            .putShort(opcode.toShort())
            .putInt(body.size)
            .put(body)
            .array()
    }

    fun tryUnpackMany(buffer: ByteArray): Pair<List<Packet>, ByteArray> {
        val packets = mutableListOf<Packet>()
        var pos = 0
        while (pos + 10 <= buffer.size) {
            val buf = ByteBuffer.wrap(buffer, pos, buffer.size - pos).order(ByteOrder.BIG_ENDIAN)
            val ver   = buf.get().toInt() and 0xFF
            val cmd   = buf.short
            val seq   = buf.get().toInt() and 0xFF
            val op    = buf.short.toInt() and 0xFFFF
            val lenRaw = buf.int
            val comp  = (lenRaw ushr 24) and 0xFF
            val pLen  = lenRaw and 0xFFFFFF
            if (pos + 10 + pLen > buffer.size) break
            val bodyBytes = buffer.copyOfRange(pos + 10, pos + 10 + pLen)
            val decompressed = if (comp != 0) decompress(bodyBytes) ?: bodyBytes else bodyBytes
            msgpackDeserialize(decompressed)?.let { packets += Packet(ver, cmd, seq, op, it) }
            pos += 10 + pLen
        }
        return packets to buffer.copyOfRange(pos, buffer.size)
    }

    private fun decompress(src: ByteArray): ByteArray? = runCatching {
        val out = ByteArray(DECOMPRESS_MAX)
        val len = lz4Factory.safeDecompressor().decompress(src, 0, src.size, out, 0)
        out.copyOf(len)
    }.getOrNull()

    private fun msgpackDeserialize(data: ByteArray): Map<String, Any?>? = runCatching {
        val v = MessagePack.newDefaultUnpacker(data).unpackValue()
        valueToMap(v)
    }.getOrNull()

    private fun valueToMap(v: Value): Map<String, Any?> {
        if (!v.isMapValue) return emptyMap()
        return v.asMapValue().map().entries.associate { (k, vv) -> k.toString() to valueToAny(vv) }
    }

    private fun valueToAny(v: Value): Any? = when {
        v.isNilValue     -> null
        v.isBooleanValue -> v.asBooleanValue().boolean
        v.isIntegerValue -> v.asIntegerValue().toLong()
        v.isFloatValue   -> v.asFloatValue().toDouble()
        v.isStringValue  -> v.asStringValue().asString()
        v.isBinaryValue  -> v.asBinaryValue().asByteArray()
        v.isArrayValue   -> v.asArrayValue().list().map { valueToAny(it) }
        v.isMapValue     -> valueToMap(v)
        else -> v.toString()
    }

    fun msgpackSerialize(map: Map<String, Any?>): ByteArray {
        val out = ByteArrayOutputStream(256)
        val p = MessagePack.newDefaultPacker(out)
        p.packMapHeader(map.size)
        map.forEach { (k, v) -> p.packString(k); packVal(p, v) }
        p.close()
        return out.toByteArray()
    }

    private fun packVal(p: org.msgpack.core.MessagePacker, v: Any?) {
        when (v) {
            null        -> p.packNil()
            is Boolean  -> p.packBoolean(v)
            is Int      -> p.packInt(v)
            is Long     -> p.packLong(v)
            is Float    -> p.packFloat(v)
            is Double   -> p.packDouble(v)
            is String   -> p.packString(v)
            is ByteArray -> { p.packBinaryHeader(v.size); p.writePayload(v) }
            is List<*>  -> { p.packArrayHeader(v.size); v.forEach { packVal(p, it) } }
            is Map<*,*> -> {
                @Suppress("UNCHECKED_CAST") val m = v as Map<String, Any?>
                p.packMapHeader(m.size); m.forEach { (k, vv) -> p.packString(k); packVal(p, vv) }
            }
            else -> p.packString(v.toString())
        }
    }
}
