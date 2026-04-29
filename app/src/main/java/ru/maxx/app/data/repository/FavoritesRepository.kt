package ru.maxx.app.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.maxx.app.data.model.Message

/**
 * Избранные сообщения — хранятся локально в SharedPreferences.
 * Сохраняется полный Message объект.
 */
private const val MAX_SAVED = 500

class FavoritesRepository(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val json  = Json { ignoreUnknownKeys = true }

    private val _favorites = MutableStateFlow<List<SavedMessage>>(emptyList())
    val favorites: StateFlow<List<SavedMessage>> = _favorites.asStateFlow()

    data class SavedMessage(
        val savedAt: Long,
        val message: Message,
        val chatTitle: String
    )

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    init { scope.launch { load() } }

    private fun load() {
        val raw = prefs.getString("saved", null) ?: return
        _favorites.value = runCatching {
            json.decodeFromString<List<SerializedSaved>>(raw)
                .map { SavedMessage(it.savedAt, it.message, it.chatTitle) }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val snapshot = _favorites.value.toList()
        scope.launch {
            val serialized = snapshot.map { SerializedSaved(it.savedAt, it.message, it.chatTitle) }
            prefs.edit().putString("saved", json.encodeToString(serialized)).apply()
        }
    }

    fun saveMessage(msg: Message, chatTitle: String) {
        if (_favorites.value.any { it.message.id == msg.id }) return
        val updated = listOf(SavedMessage(System.currentTimeMillis(), msg, chatTitle)) + _favorites.value
        _favorites.value = if (updated.size > MAX_SAVED) updated.take(MAX_SAVED) else updated
        save()
    }

    fun removeSaved(messageId: Long) {
        _favorites.value = _favorites.value.filter { it.message.id != messageId }
        save()
    }

    fun isSaved(messageId: Long) = _favorites.value.any { it.message.id == messageId }

    @kotlinx.serialization.Serializable
    private data class SerializedSaved(val savedAt: Long, val message: Message, val chatTitle: String)
}
