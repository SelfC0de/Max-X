package ru.maxx.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatFolder(
    val id: String,          // UUID
    val title: String,
    val icon: String = "folder",
    val chatIds: List<Long> = emptyList(),
    val filters: FolderFilter = FolderFilter(),
    val sortOrder: Int = 0
)

@Serializable
data class FolderFilter(
    val includePersonal: Boolean  = true,
    val includeGroups: Boolean    = true,
    val includeChannels: Boolean  = true,
    val includeUnread: Boolean    = false,   // только непрочитанные
    val includeMuted: Boolean     = true,
    val customChatIds: List<Long> = emptyList()
)
