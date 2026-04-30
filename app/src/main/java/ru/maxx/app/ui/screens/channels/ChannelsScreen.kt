package ru.maxx.app.ui.screens.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.maxx.app.data.model.Chat
import ru.maxx.app.data.model.ChatType
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.*
import ru.maxx.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class ChannelsViewModel(container: AppContainer) : ViewModel() {
    private val _channels = MutableStateFlow<List<Chat>>(emptyList())
    val channels: StateFlow<List<Chat>> = _channels.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            _loading.value = true
            val all = container.chatRepo.loadChats()
            _channels.value = all.filter { it.type == ChatType.CHANNEL }
            _loading.value = false
        }
    }
}

@Composable
@Composable
fun ChannelSearchBar(query: String, onQuery: (String) -> Unit) {
    // интегрирован в ChannelsScreen
}

@Composable
fun ChannelsScreen(container: AppContainer, onBack: () -> Unit, onChannelClick: (Long, String) -> Unit) {
    val vm = remember { ChannelsViewModel(container) }
    val channels by vm.channels.collectAsState()
    val loading by vm.loading.collectAsState()

    Scaffold(containerColor = BgPrimary, topBar = { MaxXTopBar("Каналы") }) { pad ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator(color = Accent) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(channels, key = { it.id }) { ch ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onChannelClick(ch.id, ch.title) }
                            .background(BgPrimary).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AvatarView(ch.initials, size = 48, bgColor = PurpleDark, textColor = Purple)
                        Column(Modifier.weight(1f)) {
                            Text(ch.title, style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (ch.memberCount > 0) Text("${ch.memberCount} подписчиков", style = MaterialTheme.typography.bodySmall)
                                if (ch.lastMessage != null) {
                                    Text("·", style = MaterialTheme.typography.bodySmall, color = TextHint)
                                    Text(ch.lastMessage.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (ch.unreadCount > 0) UnreadBadge(ch.unreadCount)
                    }
                    HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 70.dp))
                }
            }
        }
    }
}
