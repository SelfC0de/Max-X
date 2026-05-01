package ru.maxx.app.ui.screens.channels

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.maxx.app.data.model.Chat
import ru.maxx.app.data.model.ChatType
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.*
import ru.maxx.app.ui.theme.*

class ChannelsViewModel(container: AppContainer) : ViewModel() {
    private val _channels      = MutableStateFlow<List<Chat>>(emptyList())
    private val _recommended   = MutableStateFlow<List<Map<String, Any?>>>(emptyList())
    private val _loading       = MutableStateFlow(false)
    private val _searchLoading = MutableStateFlow(false)
    private val _searchResults = MutableStateFlow<List<Map<String, Any?>>>(emptyList())

    val channels:      StateFlow<List<Chat>>              = _channels.asStateFlow()
    val recommended:   StateFlow<List<Map<String, Any?>>> = _recommended.asStateFlow()
    val loading:       StateFlow<Boolean>                 = _loading.asStateFlow()
    val searchLoading: StateFlow<Boolean>                 = _searchLoading.asStateFlow()
    val searchResults: StateFlow<List<Map<String, Any?>>> = _searchResults.asStateFlow()

    private val container = container

    init {
        viewModelScope.launch {
            _loading.value = true
            val all = container.chatRepo.loadChats()
            _channels.value = all.filter { it.type == ChatType.CHANNEL }
            _loading.value = false
        }
        loadRecommended()
    }

    private fun loadRecommended() = viewModelScope.launch {
        val results = container.msgRepo.searchChannelsWithOffset("")
        _recommended.value = results
        android.util.Log.d("Channels", "Recommended: ${results.size} items, keys=${results.firstOrNull()?.keys}")
    }

    fun search(query: String) = viewModelScope.launch {
        if (query.length < 2) { _searchResults.value = emptyList(); return@launch }
        _searchLoading.value = true
        _searchResults.value = container.msgRepo.searchChannelsWithOffset(query)
        android.util.Log.d("Channels", "Search '$query': ${_searchResults.value.size} results")
        _searchLoading.value = false
    }

    fun joinChannel(channelId: Long) = viewModelScope.launch {
        container.msgRepo.enterChannel(channelId)
        val all = container.chatRepo.loadChats()
        _channels.value = all.filter { it.type == ChatType.CHANNEL }
    }

    fun leaveChannel(chatId: Long) = viewModelScope.launch {
        container.session.terminateSession(chatId.toString()) // используем как leave
    }
}

@Composable
fun ChannelsScreen(container: AppContainer, onBack: () -> Unit, onChannelClick: (Long, String) -> Unit) {
    val vm            = remember { ChannelsViewModel(container) }
    val channels      by vm.channels.collectAsState()
    val recommended   by vm.recommended.collectAsState()
    val loading       by vm.loading.collectAsState()
    val searchLoading by vm.searchLoading.collectAsState()
    val searchResults by vm.searchResults.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) { vm.search(searchQuery) }

    Scaffold(
        containerColor = BgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MaxXTopBar("Каналы", actions = {
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                    Icon(if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                        null, tint = if (showSearch) Accent else TextMuted)
                }
            })
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            // Search bar
            AnimatedVisibility(visible = showSearch, enter = fadeIn(androidx.compose.animation.core.tween(200)), exit = fadeOut(androidx.compose.animation.core.tween(200))) {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск каналов...", color = TextHint, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                    )
                )
            }

            if (loading || searchLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Accent) }
                return@Scaffold
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {

                // Результаты поиска
                if (showSearch && searchQuery.length >= 2) {
                    if (searchResults.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                            Text("Каналы не найдены", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                        }}
                    } else {
                        item { SectionHeader("Результаты поиска") }
                        items(searchResults) { ch ->
                            SearchChannelRow(ch, onJoin = {
                                val id = ch["id"]?.toString()?.toLongOrNull() ?: 0L
                                vm.joinChannel(id)
                            })
                        }
                    }
                } else {
                    // Мои каналы
                    if (channels.isNotEmpty()) {
                        item { SectionHeader("Мои каналы") }
                        items(channels, key = { it.id }) { ch ->
                            ChannelRow(ch, onLeave = { vm.leaveChannel(ch.id) }, onClick = { onChannelClick(ch.id, ch.title) })
                        }
                    }

                    // Рекомендованные
                    if (recommended.isNotEmpty()) {
                        item { SectionHeader("Рекомендованные") }
                        items(recommended) { ch ->
                            SearchChannelRow(ch, onJoin = {
                                val id = ch["id"]?.toString()?.toLongOrNull() ?: 0L
                                vm.joinChannel(id)
                            })
                        }
                    }

                    if (channels.isEmpty() && recommended.isEmpty()) {
                        item {
                            Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Campaign, null, tint = TextHint, modifier = Modifier.size(48.dp))
                                    Text("Каналы не найдены", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                                    Text("Используйте поиск", color = TextHint, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ChannelRow(ch: Chat, onLeave: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(AccentDark), Alignment.Center) {
            Text(ch.title.take(2).uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(ch.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Нажмите чтобы открыть", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        TextButton(onClick = onLeave, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Покинуть", fontSize = 11.sp, color = Red)
        }
    }
}

@Composable
private fun SearchChannelRow(ch: Map<String, Any?>, onJoin: () -> Unit) {
    val title     = ch["title"]?.toString() ?: ch["name"]?.toString() ?: "Канал"
    val desc      = ch["description"]?.toString() ?: ch["about"]?.toString() ?: ""
    val members   = ch["membersCount"]?.toString() ?: ch["subscribers"]?.toString()
        ?: ch["memberCount"]?.toString() ?: ""
    val avatarUrl = ch["photoUrl"]?.toString() ?: ch["avatarUrl"]?.toString()
        ?: ch["photo"]?.toString() ?: ""

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(BgCard), Alignment.Center) {
            if (avatarUrl.isNotEmpty()) {
                coil.compose.AsyncImage(
                    model = avatarUrl, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(CircleShape)
                )
            } else {
                Text(title.take(2).uppercase(), color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (desc.isNotEmpty()) Text(desc, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (members.isNotEmpty()) Text("$members подписчиков", style = MaterialTheme.typography.labelSmall, color = TextHint)
        }
        OutlinedButton(
            onClick = onJoin,
            modifier = Modifier.height(32.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
            border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
        ) { Text("Вступить", fontSize = 11.sp) }
    }
}
