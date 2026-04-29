package ru.maxx.app.ui.screens.chats

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
                            IconButton(onClick = {}) {
                                Icon(Icons.Default.Edit, null, tint = TextMuted)
                            }
                        })
                    }
                }
                // Папки-фильтры
                if (!showSearch && folders.isNotEmpty()) {
                    FolderTabs(
                        folders = folders,
                        selected = selectedFolder,
                        onSelect = { selectedFolder = if (selectedFolder?.id == it?.id) null else it }
                    )
                }
                HorizontalDivider(color = Border, thickness = 0.5.dp)
            }
        },
        bottomBar = {
            BottomNavBar(
                selected = 0,
                onChats = {}, onContacts = onContactsClick,
                onChannels = onChannelsClick, onProfile = onProfileClick,
                onFab = { /* TODO: new chat picker */ }
            )
        }
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
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(BgSecondary).height(38.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Все" таб
        item {
            FolderChip(label = "Все", icon = Icons.Default.AllInbox, selected = selected == null) { onSelect(null) }
        }
        items(folders, key = { it.id }) { folder ->
            FolderChip(
                label = folder.title,
                icon  = folderIcon(folder.icon),
                selected = selected?.id == folder.id
            ) { onSelect(folder) }
        }
    }
}

@Composable
private fun FolderChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) AccentDark else BgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = if (selected) Accent else TextMuted, modifier = Modifier.size(13.dp))
        Text(label, fontSize = 11.sp, color = if (selected) Accent else TextMuted, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
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

// ─── Bottom nav ───────────────────────────────────────────────────────────────

@Composable
fun BottomNavBar(
    selected: Int,
    onChats: () -> Unit, onContacts: () -> Unit,
    onChannels: () -> Unit, onProfile: () -> Unit,
    onFab: () -> Unit
) {
    Column {
        HorizontalDivider(color = Border, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp).background(BgSecondary)
                .navigationBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavItem(Icons.Filled.Chat, Icons.Outlined.ChatBubbleOutline, "Чаты", selected == 0, onChats)
            NavItem(Icons.Filled.People, Icons.Outlined.PeopleOutline, "Контакты", selected == 1, onContacts)
            FloatingActionButton(
                onClick = onFab, modifier = Modifier.size(46.dp),
                containerColor = Accent, contentColor = BgSecondary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(22.dp)) }
            NavItem(Icons.Filled.Campaign, Icons.Outlined.Campaign, "Каналы", selected == 2, onChannels)
            NavItem(Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle, "Профиль", selected == 3, onProfile)
        }
    }
}

@Composable
private fun NavItem(iconOn: ImageVector, iconOff: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.width(58.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Icon(if (selected) iconOn else iconOff, label, tint = if (selected) Accent else TextMuted, modifier = Modifier.size(23.dp))
            Text(label, fontSize = 9.sp, color = if (selected) Accent else TextMuted)
        }
    }
}

private val timeFormatter = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }
private val dateFormatter = ThreadLocal.withInitial { SimpleDateFormat("d MMM", Locale("ru")) }

private fun formatChatTime(ts: Long): String {
    if (ts == 0L) return ""
    return if (System.currentTimeMillis() - ts < 86_400_000L) timeFormatter.get()!!.format(Date(ts))
    else dateFormatter.get()!!.format(Date(ts))
}

// Snackbar компонент (используется и в ChatScreen)
@Composable
fun MaxXSnackbar(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp)).background(BgCard).padding(horizontal = 14.dp, vertical = 12.dp)
    ) { Text(message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium) }
}
