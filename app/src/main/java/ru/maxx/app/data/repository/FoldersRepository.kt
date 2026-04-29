package ru.maxx.app.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.maxx.app.core.network.MaxSocket
import ru.maxx.app.core.protocol.MaxProtocol
import ru.maxx.app.data.model.Chat
import ru.maxx.app.data.model.ChatFolder
import ru.maxx.app.data.model.ChatType
import ru.maxx.app.data.model.FolderFilter
import java.util.UUID

class FoldersRepository(
    private val socket: MaxSocket,
    private val ctx: Context
) {
    private val prefs = ctx.getSharedPreferences("folders", Context.MODE_PRIVATE)
    private val json  = Json { ignoreUnknownKeys = true }

    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    val folders: Flow<List<ChatFolder>> = _folders.asStateFlow()

    private val ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    init { ioScope.launch { loadLocal() } }

    private fun loadLocal() {
        val raw = prefs.getString("folders_list", null) ?: run {
            // Дефолтные папки
            _folders.value = defaultFolders()
            return
        }
        _folders.value = runCatching { json.decodeFromString<List<ChatFolder>>(raw) }
            .getOrDefault(defaultFolders())
    }

    private fun saveLocal() {
        val snapshot = _folders.value.toList()
        ioScope.launch {
            prefs.edit().putString("folders_list", json.encodeToString(snapshot)).apply()
        }
    }

    fun filterChats(chats: List<Chat>, folder: ChatFolder?): List<Chat> {
        if (folder == null) return chats   // "Все" — без фильтра
        val f = folder.filters
        return chats.filter { chat ->
            when {
                folder.chatIds.isNotEmpty() && chat.id in folder.chatIds -> true
                f.customChatIds.isNotEmpty() && chat.id in f.customChatIds -> true
                else -> {
                    val typeOk = when (chat.type) {
                        ChatType.PERSONAL -> f.includePersonal
                        ChatType.GROUP    -> f.includeGroups
                        ChatType.CHANNEL  -> f.includeChannels
                    }
                    val mutedOk = if (!f.includeMuted) !chat.isMuted else true
                    val unreadOk = if (f.includeUnread) chat.unreadCount > 0 else true
                    typeOk && mutedOk && unreadOk
                }
            }
        }
    }

    suspend fun createFolder(title: String, filter: FolderFilter): ChatFolder {
        val folder = ChatFolder(
            id       = UUID.randomUUID().toString(),
            title    = title,
            filters  = filter,
            sortOrder = _folders.value.size
        )
        _folders.value = _folders.value + folder
        saveLocal()  // async IO
        // Синхронизируем с сервером
        socket.send(MaxProtocol.Op.FOLDERS_CREATE, mapOf(
            "id"      to folder.id,
            "title"   to folder.title,
            "include" to filter.customChatIds,
            "filters" to listOf<Any>()
        ))
        return folder
    }

    suspend fun updateFolder(folder: ChatFolder) {
        _folders.value = _folders.value.map { if (it.id == folder.id) folder else it }
        saveLocal()
        socket.send(MaxProtocol.Op.FOLDERS_UPDATE, mapOf(
            "id"    to folder.id,
            "title" to folder.title
        ))
    }

    fun deleteFolder(folderId: String) {
        _folders.value = _folders.value.filter { it.id != folderId }
        saveLocal()
    }

    private fun defaultFolders() = listOf(
        ChatFolder("personal", "Личные",    "person",    filters = FolderFilter(includePersonal = true, includeGroups = false, includeChannels = false)),
        ChatFolder("groups",   "Группы",    "group",     filters = FolderFilter(includePersonal = false, includeGroups = true, includeChannels = false)),
        ChatFolder("channels", "Каналы",    "campaign",  filters = FolderFilter(includePersonal = false, includeGroups = false, includeChannels = true)),
        ChatFolder("unread",   "Непрочит.", "mark_unread", filters = FolderFilter(includeUnread = true)),
    )
}
