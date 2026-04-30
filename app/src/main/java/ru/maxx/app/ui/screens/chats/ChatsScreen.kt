package ru.maxx.app.ui.screens.chats

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.maxx.app.data.model.Chat
import ru.maxx.app.data.model.ChatFolder
import ru.maxx.app.data.model.ChatType
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.*
import ru.maxx.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ChatsViewModel(private val container: AppContainer) : ViewModel() {

    private val _chats   = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    val folders: StateFlow<List<ChatFolder>> = _folders.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val myUserId: Long = container.authPrefs.getUserId()?.toLongOrNull() ?: 0L

    init {
        loadChats()
        observeUpdates()
        observeFolders()
    }

    fun loadChats() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { container.chatRepo.loadChats() }
                .onSuccess { _chats.value = it.sortedByDescending { c -> c.lastMessage?.time ?: 0L } }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    private fun observeUpdates() {
        viewModelScope.launch {
            container.chatRepo.observeUpdates().collect { updated ->
                _chats.update { list ->
                    list.map { if (it.id == updated.id) updated else it }
                        .sortedByDescending { it.lastMessage?.time ?: 0L }
                }
            }
        }
    }

    private fun observeFolders() {
        viewModelScope.launch {
            container.foldersRepo.folders.collect { _folders.value = it }
        }
    }

    fun filteredChats(folder: ChatFolder?): List<Chat> =
        container.foldersRepo.filterChats(_chats.value, folder)

    fun clearError() { _error.value = null }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun ChatsScreen(
    container: AppContainer,
    onChatClick: (Long, String) -> Unit,
    onContactsClick: () -> Unit,
    onChannelsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onFavoritesClick: () -> Unit = {}
) {
    val vm = remember { ChatsViewModel(container) }
    val chats   by vm.chats.collectAsState()
    val folders by vm.folders.collectAsState()
    val loading by vm.loading.collectAsState()
    val error   by vm.error.collectAsState()

    var selectedFolder by remember { mutableStateOf<ChatFolder?>(null) }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Показываем ошибки
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short); vm.clearError() }
    }

    // Применяем папку + поиск
    // folders в зависимостях чтобы derivedStateOf пересчитался при смене папок
    val displayChats by remember(chats, selectedFolder, search, folders) {
        derivedStateOf {
            vm.filteredChats(selectedFolder)
                .filter { search.isEmpty() || it.title.contains(search, ignoreCase = true) }
        }
    }

    Scaffold(
        containerColor = BgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) { MaxXSnackbar(it.visuals.message) } },
        topBar = {
            Column {
                AnimatedContent(
                    targetState = showSearch,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                    label = "topbar"
                ) { inSearch ->
                    if (inSearch) {
                        SearchBar(
                            query = search,
                            onQuery = { search = it },
                            onClose = { showSearch = false; search = "" }
                        )
                    } else {
                        MaxXTopBar("Max-X", actions = {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Default.Search, null, tint = Accent)
                            }
                            IconButton(onClick = onFavoritesClick) {
                                Icon(Icons.Default.Bookmark, null, tint = TextMuted)
                            }

                        })
                    }
                }
                // Папки-фильтры
                if (!showSearch) {
                    FolderTabs(
                        folders = folders,
                        selected = selectedFolder,
                        onSelect = { selectedFolder = if (selectedFolder?.id == it?.id) null else it }
                    )
                }
                HorizontalDivider(color = Border, thickness = 0.5.dp)
            }
        },

    ) { pad ->
        if (loading && chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(displayChats, key = { it.id }) { chat ->
                    ChatRow(
                        chat = chat,
                        myUserId = vm.myUserId,
                        onClick = { onChatClick(chat.id, chat.title) }
                    )
                    HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 72.dp))
                }

                if (displayChats.isEmpty() && !loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = TextHint, modifier = Modifier.size(52.dp))
                                Text(
                                    if (search.isNotEmpty()) "Ничего не найдено"
                                    else if (selectedFolder != null) "В этой папке пусто"
                                    else "Нет чатов",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Folder tabs ─────────────────────────────────────────────────────────────

@Composable
private fun FolderTabs(
    folders: List<ChatFolder>,
    selected: ChatFolder?,
    onSelect: (ChatFolder?) -> Unit
) {
    // Статические табы всегда показываются
    data class StaticTab(val id: String, val label: String, val icon: ImageVector)
    val staticTabs = listOf(
        StaticTab("all",      "Все",           Icons.Default.AllInbox),
        StaticTab("personal", "Личные",        Icons.Outlined.Person),
        StaticTab("groups",   "Группы",        Icons.Outlined.Group),
        StaticTab("channels", "Каналы",        Icons.Outlined.Campaign),
        StaticTab("unread",   "Непрочитанные", Icons.Default.MarkChatUnread),
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth().background(BgSecondary).height(34.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(staticTabs, key = { it.id }) { tab ->
            val isSelected = when (tab.id) {
                "all"      -> selected == null
                else       -> selected?.icon == tab.id || selected?.title?.lowercase() == tab.label.lowercase()
            }
            FolderChip(
                label    = tab.label,
                icon     = tab.icon,
                selected = isSelected
            ) {
                if (tab.id == "all") {
                    onSelect(null)
                } else {
                    val match = folders.find { f ->
                        f.icon == tab.id || f.title.lowercase() == tab.label.lowercase()
                    }
                    onSelect(if (isSelected) null else (match ?: ChatFolder(
                            id = tab.id,
                            title = tab.label,
                            icon = tab.id,
                            filters = when (tab.id) {
                                "personal"  -> ru.maxx.app.data.model.FolderFilter(includeGroups = false, includeChannels = false)
                                "groups"    -> ru.maxx.app.data.model.FolderFilter(includePersonal = false, includeChannels = false)
                                "channels"  -> ru.maxx.app.data.model.FolderFilter(includePersonal = false, includeGroups = false)
                                "unread"    -> ru.maxx.app.data.model.FolderFilter(includeUnread = true)
                                else        -> ru.maxx.app.data.model.FolderFilter()
                            }
                        )))
                }
            }
        }
        // Пользовательские папки с сервера (если есть сверх стандартных)
        val standardTitles = setOf("все", "личные", "группы", "каналы", "непрочитанные")
        items(folders.filter { it.title.lowercase() !in standardTitles }, key = { it.id }) { folder ->
            FolderChip(
                label    = folder.title,
                icon     = folderIcon(folder.icon),
                selected = selected?.id == folder.id
            ) { onSelect(folder) }
        }
    }
}

@Composable
private fun FolderChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentDark else BgTertiary)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = if (selected) Accent else TextMuted, modifier = Modifier.size(11.dp))
        Text(label, fontSize = 10.sp, color = if (selected) Accent else TextMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun folderIcon(name: String): ImageVector = when (name) {
    "person"       -> Icons.Outlined.Person
    "group"        -> Icons.Outlined.Group
    "campaign"     -> Icons.Outlined.Campaign
    "mark_unread"  -> Icons.Default.MarkChatUnread
    "bookmark"     -> Icons.Default.Bookmark
    else           -> Icons.Default.Folder
}

// ─── Search bar ──────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    Column {
        Spacer(modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .background(BgSecondary)
        )
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).background(BgSecondary).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = Accent) }
        OutlinedTextField(
            value = query, onValueChange = onQuery,
            placeholder = { Text("Поиск чатов...", color = TextHint, fontSize = 13.sp) },
            modifier = Modifier.weight(1f).height(42.dp),
            shape = RoundedCornerShape(10.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                focusedBorderColor = Accent, unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
            )
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Clear, null, tint = TextMuted) }
        }
    }
    }
}

// ─── Chat row ─────────────────────────────────────────────────────────────────

@Composable
fun ChatRow(chat: Chat, myUserId: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(BgPrimary).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            AvatarView(
                initials = chat.initials, size = 50,
                bgColor = when (chat.type) { ChatType.PERSONAL -> AccentDark; ChatType.GROUP -> BlueDark; ChatType.CHANNEL -> PurpleDark },
                textColor = when (chat.type) { ChatType.PERSONAL -> Accent; ChatType.GROUP -> Blue; ChatType.CHANNEL -> Purple }
            )
            if (chat.type == ChatType.PERSONAL) {
                OnlineDot(Modifier.align(Alignment.BottomEnd).offset((-1).dp, (-1).dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(Modifier.weight(1f, false), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (chat.isMuted) Icon(Icons.Default.NotificationsOff, null, tint = TextHint, modifier = Modifier.size(13.dp))
                    Text(chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatChatTime(chat.lastMessage?.time ?: 0), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                val lastText = remember(chat.lastMessage?.id, myUserId) { buildLastText(chat, myUserId) }
                Text(lastText, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (chat.unreadCount > 0) UnreadBadge(chat.unreadCount, Modifier.padding(start = 4.dp))
            }
        }
    }
}

private fun buildLastText(chat: Chat, myUserId: Long): String {
    val msg = chat.lastMessage ?: return ""
    return when {
        msg.senderId == myUserId -> "Вы: ${msg.text}"
        chat.type != ChatType.PERSONAL && msg.senderName.isNotEmpty() -> "${msg.senderName}: ${msg.text}"
        else -> msg.text
    }
}

@Composable
fun MaxXSnackbar(message: String) {
    Surface(
        shape  = RoundedCornerShape(10.dp),
        color  = BgCard,
        tonalElevation = 4.dp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text     = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style    = MaterialTheme.typography.bodySmall,
            color    = TextPrimary
        )
    }
}

fun formatChatTime(timeMs: Long): String {
    if (timeMs == 0L) return ""
    val now  = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().also { it.timeInMillis = timeMs }
    return when {
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR) &&
        now.get(java.util.Calendar.YEAR)        == then.get(java.util.Calendar.YEAR) ->
            String.format("%02d:%02d", then.get(java.util.Calendar.HOUR_OF_DAY), then.get(java.util.Calendar.MINUTE))
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) -> {
            val months = arrayOf("янв","фев","мар","апр","май","июн","июл","авг","сен","окт","ноя","дек")
            "${then.get(java.util.Calendar.DAY_OF_MONTH)} ${months[then.get(java.util.Calendar.MONTH)]}"
        }
        else -> "${then.get(java.util.Calendar.DAY_OF_MONTH)}.${String.format("%02d",then.get(java.util.Calendar.MONTH)+1)}.${then.get(java.util.Calendar.YEAR)%100}"
    }
}
