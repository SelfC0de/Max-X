package ru.maxx.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import ru.maxx.app.core.network.MaxSocket
import ru.maxx.app.core.protocol.MaxProtocol
import ru.maxx.app.data.model.Chat
import ru.maxx.app.data.model.ChatType
import ru.maxx.app.data.model.Message
import ru.maxx.app.data.prefs.AuthPrefs

class ChatRepository(private val socket: MaxSocket, private val auth: AuthPrefs) {

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    suspend fun loadChats(): List<Chat> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.AUTH_CHATS, mapOf("limit" to 50, "offset" to 0))
            ?: return emptyList()
        val list = withContext(Dispatchers.Default) { (pkt.payload["chats"] as? List<*>)?.mapNotNull { parseChat(it) } ?: emptyList() }
        _chats.value = list
        return list
    }

    fun observeUpdates(): Flow<Chat> = socket.packets
        .filter { it.opcode == 64 && it.cmd == MaxProtocol.CMD_OK }
        .mapNotNull { pkt ->
            val chatId = pkt.payload["chatId"] as? Long ?: return@mapNotNull null
            val chatData = pkt.payload["chat"] as? Map<*, *> ?: return@mapNotNull null
            parseChat(chatData)
        }

    @Suppress("UNCHECKED_CAST")
    private fun parseChat(raw: Any?): Chat? {
        val m = raw as? Map<String, Any?> ?: return null
        val id = (m["id"] as? Long) ?: return null
        val typeStr = m["type"]?.toString() ?: "CHAT"
        val type = when (typeStr.uppercase()) {
            "CHANNEL" -> ChatType.CHANNEL
            "GROUP", "CHAT" -> if ((m["ownerContact"] != null || (m["memberCount"] as? Long ?: 0L) > 2L)) ChatType.GROUP else ChatType.PERSONAL
            else -> ChatType.PERSONAL
        }
        val lastMsg = (m["lastMessage"] as? Map<String, Any?>)?.let { parseMessage(it, id) }
        return Chat(
            id = id,
            type = type,
            title = buildChatTitle(m),
            avatarUrl = m["avatarUrl"]?.toString(),
            lastMessage = lastMsg,
            unreadCount = (m["newMessages"] as? Long)?.toInt() ?: 0,
            memberCount = (m["memberCount"] as? Long)?.toInt() ?: 0,
            isPinned = m["isPinned"] as? Boolean ?: false,
            isMuted = m["isMuted"] as? Boolean ?: false,
        )
    }

    private fun buildChatTitle(m: Map<String, Any?>): String {
        val name = m["name"]?.toString() ?: m["title"]?.toString()
        if (!name.isNullOrBlank()) return name
        val contact = m["contact"] as? Map<*, *>
        if (contact != null) {
            val first = contact["firstName"]?.toString() ?: ""
            val last = contact["lastName"]?.toString() ?: ""
            return "$first $last".trim()
        }
        return "Чат"
    }

    @Suppress("UNCHECKED_CAST")
    fun parseMessage(m: Map<String, Any?>, chatId: Long = 0): Message {
        return Message(
            id = (m["id"] as? Long) ?: 0L,
            chatId = chatId,
            senderId = (m["sender"] as? Long) ?: 0L,
            text = m["text"]?.toString() ?: "",
            time = (m["time"] as? Long) ?: 0L,
            edited = m["edited"] as? Boolean ?: false,
            replyToId = m["replyMessageId"] as? Long,
        )
    }
}
