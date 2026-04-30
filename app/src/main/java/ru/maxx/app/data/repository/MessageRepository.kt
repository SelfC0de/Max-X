package ru.maxx.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import ru.maxx.app.core.network.MaxSocket
import ru.maxx.app.core.protocol.MaxProtocol
import ru.maxx.app.data.model.Message
import ru.maxx.app.data.model.TypingEvent
import ru.maxx.app.data.prefs.AuthPrefs

class MessageRepository(private val socket: MaxSocket, private val auth: AuthPrefs) {

    suspend fun loadMessages(chatId: Long, limit: Int = 50, beforeId: Long? = null): List<Message> {
        val payload = mutableMapOf<String, Any?>("chatId" to chatId, "count" to limit)
        beforeId?.let { payload["beforeId"] = it }
        val pkt = socket.sendAndAwait(MaxProtocol.Op.MESSAGES_LOAD, payload) ?: return emptyList()
        return withContext(Dispatchers.Default) { (pkt.payload["messages"] as? List<*>)?.mapNotNull { parseMsg(it, chatId) } ?: emptyList() }
    }

    suspend fun sendMessage(chatId: Long, text: String, replyToId: Long? = null): Boolean {
        val payload = mutableMapOf<String, Any?>(
            "chatId" to chatId, "text" to text,
            "cid" to System.currentTimeMillis()
        )
        replyToId?.let { payload["replyMessageId"] = it }
        val pkt = socket.sendAndAwait(MaxProtocol.Op.SEND_MESSAGE, payload)
        return pkt?.cmd == MaxProtocol.CMD_OK
    }

    suspend fun editMessage(chatId: Long, messageId: Long, text: String): Boolean {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.EDIT_MESSAGE, mapOf(
            "chatId" to chatId, "messageId" to messageId, "text" to text
        ))
        return pkt?.cmd == MaxProtocol.CMD_OK
    }

    suspend fun deleteMessage(chatId: Long, messageId: Long, forAll: Boolean): Boolean {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.DELETE_MESSAGE, mapOf(
            "chatId" to chatId, "messageIds" to listOf(messageId), "deleteForAll" to forAll
        ))
        return pkt?.cmd == MaxProtocol.CMD_OK
    }

    suspend fun markRead(chatId: Long, messageId: Long) {
        socket.send(MaxProtocol.Op.READ_MESSAGES, mapOf("chatId" to chatId, "messageId" to messageId))
    }

    suspend fun sendTyping(chatId: Long, isTyping: Boolean) {
        socket.send(MaxProtocol.Op.TYPING, mapOf("chatId" to chatId, "typing" to isTyping))
    }

    suspend fun sendReaction(chatId: Long, messageId: Long, emoji: String): Boolean {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.REACTIONS_SEND, mapOf(
            "chatId" to chatId, "messageId" to messageId, "emoji" to emoji
        ))
        return pkt?.cmd == MaxProtocol.CMD_OK
    }

    fun observeNewMessages(chatId: Long): Flow<Message> = socket.packets
        .filter { it.opcode == MaxProtocol.Op.SEND_MESSAGE && it.cmd == MaxProtocol.CMD_OK }
        .mapNotNull { pkt ->
            val msgData = pkt.payload["message"] as? Map<String, Any?> ?: return@mapNotNull null
            val cId = (pkt.payload["chatId"] as? Long) ?: return@mapNotNull null
            if (cId != chatId) return@mapNotNull null
            parseMsg(msgData, chatId)
        }

    fun observeTyping(chatId: Long): Flow<TypingEvent> = socket.packets
        .filter { it.opcode == MaxProtocol.Op.TYPING }
        .mapNotNull { pkt ->
            val cId = pkt.payload["chatId"] as? Long ?: return@mapNotNull null
            if (cId != chatId) return@mapNotNull null
            val uid = pkt.payload["userId"] as? Long ?: return@mapNotNull null
            TypingEvent(chatId, uid, pkt.payload["name"]?.toString() ?: "")
        }

    @Suppress("UNCHECKED_CAST")
    private fun parseMsg(raw: Any?, chatId: Long): Message? {
        val m = raw as? Map<String, Any?> ?: return null
        return Message(
            id = m["id"] as? Long ?: return null,
            chatId = chatId,
            senderId = m["sender"] as? Long ?: 0L,
            text = m["text"]?.toString() ?: "",
            time = m["time"] as? Long ?: 0L,
            edited = m["edited"] as? Boolean ?: false,
            replyToId = m["replyMessageId"] as? Long,
            senderName = buildString {
                append(m["senderFirstName"]?.toString() ?: "")
                val last = m["senderLastName"]?.toString() ?: ""
                if (last.isNotEmpty()) append(" $last")
            }.trim()
        )
    }

    suspend fun markUnread(chatId: Long): Boolean {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.READ_MESSAGES, mapOf(
            "chatId" to chatId, "messageId" to 0L, "unread" to true
        ))
        return pkt?.cmd == MaxProtocol.CMD_OK
    }

    suspend fun searchMessages(chatId: Long, query: String): List<Message> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.MESSAGES_LOAD, mapOf(
            "chatId" to chatId, "count" to 50, "searchQuery" to query
        )) ?: return emptyList()
        return (pkt.payload["messages"] as? List<*>)?.mapNotNull { parseMsg(it, chatId) } ?: emptyList()
    }

    suspend fun forwardMessage(fromChatId: Long, messageId: Long, toChatId: Long): Boolean {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.SEND_MESSAGE, mapOf(
            "chatId" to toChatId,
            "forwardChatId" to fromChatId,
            "forwardMessageId" to messageId,
            "cid" to System.currentTimeMillis()
        ))
        return pkt?.cmd == MaxProtocol.CMD_OK
    }

    suspend fun setAutoDelete(chatId: Long, seconds: Int): Boolean {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.EDIT_MESSAGE, mapOf(
            "chatId" to chatId, "autoDeleteDuration" to seconds
        ))
        return pkt?.cmd == MaxProtocol.CMD_OK
    }

    suspend fun loadStickers(): List<Map<String, Any?>> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.MEDIA_STICKERS, emptyMap<String, Any?>())
            ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (pkt.payload["packs"] as? List<Map<String, Any?>>) ?: emptyList()
    }

    suspend fun joinGroup(inviteLink: String): Boolean {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.JOIN_GROUP, mapOf("link" to inviteLink))
        return pkt?.cmd == MaxProtocol.CMD_OK
    }

    suspend fun getGroupMembers(chatId: Long): List<Map<String, Any?>> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.GROUP_MEMBERS, mapOf("chatId" to chatId))
            ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (pkt.payload["members"] as? List<Map<String, Any?>>) ?: emptyList()
    }

    suspend fun searchChannels(query: String): List<Map<String, Any?>> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.SEARCH_CHANNELS, mapOf("query" to query))
            ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (pkt.payload["channels"] as? List<Map<String, Any?>>) ?: emptyList()
    }

    suspend fun loadMediaList(chatId: Long, type: String): List<Map<String, Any?>> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.MEDIA_LIST,
            mapOf("chatId" to chatId, "type" to type, "count" to 50)
        ) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (pkt.payload["media"] as? List<Map<String, Any?>>) ?: emptyList()
    }
}
