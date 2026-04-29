package ru.maxx.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: Long = 0, val firstName: String = "", val lastName: String = "",
    val phone: String = "", val avatarUrl: String? = null,
    val online: Boolean = false, val lastSeen: Long = 0, val about: String = ""
) {
    val displayName get() = "$firstName $lastName".trim().ifEmpty { phone }
    val initials get() = buildString {
        firstName.firstOrNull()?.let { append(it) }
        lastName.firstOrNull()?.let { append(it) }
        if (isEmpty()) displayName.firstOrNull()?.let { append(it) }
    }.uppercase()
}

@Serializable
data class Contact(
    val id: Long = 0, val firstName: String = "", val lastName: String = "",
    val phone: String? = null, val avatarUrl: String? = null,
    val online: Boolean = false, val lastSeen: Long = 0, val chatId: Long? = null,
    val isBlocked: Boolean = false
) {
    val displayName get() = "$firstName $lastName".trim().ifEmpty { phone ?: "" }
    val initials get() = buildString {
        firstName.firstOrNull()?.let { append(it) }
        lastName.firstOrNull()?.let { append(it) }
        if (isEmpty()) displayName.firstOrNull()?.let { append(it) }
    }.uppercase()
}

@Serializable
data class Chat(
    val id: Long = 0, val type: ChatType = ChatType.PERSONAL,
    val title: String = "", val avatarUrl: String? = null,
    val lastMessage: Message? = null, val unreadCount: Int = 0,
    val ownerId: Long? = null, val participantIds: List<Long> = emptyList(),
    val isPinned: Boolean = false, val isMuted: Boolean = false,
    val memberCount: Int = 0
) {
    val initials get() = title.split(" ").take(2)
        .mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
}

enum class ChatType { PERSONAL, GROUP, CHANNEL }

@Serializable
data class Message(
    val id: Long = 0, val chatId: Long = 0, val senderId: Long = 0,
    val text: String = "", val time: Long = 0,
    val edited: Boolean = false, val replyToId: Long? = null,
    val replyTo: Message? = null,
    val attachments: List<Attachment> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    val readBy: List<Long> = emptyList(),
    val isForwarded: Boolean = false, val forwardedFrom: String? = null,
    val senderName: String = ""
)

@Serializable
data class Attachment(
    val type: AttachType, val url: String, val name: String? = null,
    val size: Long? = null, val width: Int? = null, val height: Int? = null,
    val duration: Int? = null, val thumbnailUrl: String? = null
)

enum class AttachType { IMAGE, VIDEO, AUDIO, FILE, VOICE, STICKER }

@Serializable
data class Reaction(
    val emoji: String, val count: Int, val myReaction: Boolean = false
)

@Serializable
data class Session(
    val id: Long, val deviceName: String, val platform: String,
    val lastActive: Long, val isCurrent: Boolean = false, val ip: String = ""
)

data class TypingEvent(val chatId: Long, val userId: Long, val name: String)
