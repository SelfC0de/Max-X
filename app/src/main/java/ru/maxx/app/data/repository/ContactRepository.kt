package ru.maxx.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import ru.maxx.app.core.network.MaxSocket
import ru.maxx.app.core.protocol.MaxProtocol
import ru.maxx.app.data.model.Contact
import ru.maxx.app.data.prefs.AuthPrefs

class ContactRepository(private val socket: MaxSocket, private val auth: AuthPrefs) {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    suspend fun loadContacts(): List<Contact> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.CONTACTS_LOAD, mapOf("limit" to 500))
            ?: return emptyList()
        val list = withContext(Dispatchers.Default) { (pkt.payload["contacts"] as? List<*>)?.mapNotNull { parseContact(it) } ?: emptyList() }
        _contacts.value = list
        return list
    }

    suspend fun searchContact(query: String): List<Contact> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.CONTACT_FIND, mapOf("query" to query))
            ?: return emptyList()
        return (pkt.payload["contacts"] as? List<*>)?.mapNotNull { parseContact(it) } ?: emptyList()
    }

    fun subscribePresence(userIds: List<Long>) {
        if (userIds.isEmpty()) return
        socket.scope.launch {
            socket.send(MaxProtocol.Op.PRESENCE_SUBSCRIBE, mapOf("userIds" to userIds, "subscribe" to true))
        }
    }

    fun observePresence(): Flow<Pair<Long, Boolean>> = socket.packets
        .filter { it.opcode == MaxProtocol.Op.CONTACTS_PRESENCE }
        .mapNotNull { pkt ->
            val uid = pkt.payload["userId"] as? Long ?: return@mapNotNull null
            val online = pkt.payload["online"] as? Boolean ?: false
            uid to online
        }

    @Suppress("UNCHECKED_CAST")
    private fun parseContact(raw: Any?): Contact? {
        val m = raw as? Map<String, Any?> ?: return null
        val id = m["id"] as? Long ?: return null
        return Contact(
            id = id,
            firstName = m["firstName"]?.toString() ?: "",
            lastName = m["lastName"]?.toString() ?: "",
            phone = m["phone"]?.toString(),
            avatarUrl = m["avatarUrl"]?.toString(),
            online = m["online"] as? Boolean ?: false,
            lastSeen = m["lastSeen"] as? Long ?: 0L,
            chatId = m["chatId"] as? Long,
        )
    }

    suspend fun findByPhones(phones: List<String>): List<Contact> {
        val pkt = socket.sendAndAwait(
            ru.maxx.app.core.protocol.MaxProtocol.Op.CONTACT_FIND,
            mapOf("phones" to phones)
        ) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val raw = (pkt.payload["contacts"] as? List<Map<String, Any?>>) ?: return emptyList()
        return raw.mapNotNull { parseContact(it) }
    }
}
