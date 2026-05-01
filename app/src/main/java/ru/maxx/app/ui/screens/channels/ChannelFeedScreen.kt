package ru.maxx.app.ui.screens.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.maxx.app.data.model.AttachType
import ru.maxx.app.data.model.Message
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.MaxXTopBar
import ru.maxx.app.ui.theme.*

class ChannelFeedViewModel(private val container: AppContainer) : ViewModel() {
    private val _posts    = MutableStateFlow<List<Message>>(emptyList())
    private val _loading  = MutableStateFlow(false)
    private val _hasMore  = MutableStateFlow(true)
    private val _title    = MutableStateFlow("")
    private val _subtitle = MutableStateFlow("")
    private val _avatar   = MutableStateFlow("")

    val posts:    StateFlow<List<Message>>  = _posts.asStateFlow()
    val loading:  StateFlow<Boolean>        = _loading.asStateFlow()
    val hasMore:  StateFlow<Boolean>        = _hasMore.asStateFlow()
    val title:    StateFlow<String>         = _title.asStateFlow()
    val subtitle: StateFlow<String>         = _subtitle.asStateFlow()
    val avatar:   StateFlow<String>         = _avatar.asStateFlow()

    fun init(chatId: Long, chatTitle: String) {
        _title.value = chatTitle
        loadMore(chatId)
    }

    fun loadMore(chatId: Long) {
        if (_loading.value || !_hasMore.value) return
        viewModelScope.launch {
            _loading.value = true
            val before = _posts.value.lastOrNull()?.id ?: 0L
            val batch  = container.msgRepo.loadMessages(chatId, before)
            if (batch.isEmpty()) {
                _hasMore.value = false
            } else {
                _posts.value = _posts.value + batch
            }
            _loading.value = false
        }
    }

    fun join(chatId: Long) = viewModelScope.launch {
        container.msgRepo.enterChannel(chatId)
    }
}

@Composable
fun ChannelFeedScreen(
    container: AppContainer,
    chatId: Long,
    chatTitle: String,
    avatarUrl: String = "",
    onBack: () -> Unit
) {
    val vm = remember { ChannelFeedViewModel(container) }

    LaunchedEffect(chatId) { vm.init(chatId, chatTitle) }

    val posts   by vm.posts.collectAsState()
    val loading by vm.loading.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val listState = rememberLazyListState()

    // Подгрузка при достижении конца
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= posts.size - 3 && !loading && hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore(chatId)
    }

    Scaffold(
        containerColor = BgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Spacer(modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars)
                    .background(BgSecondary)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .background(BgSecondary).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, null, tint = TextPrimary)
                    }
                    // Аватарка канала
                    Box(Modifier.size(38.dp).clip(CircleShape).background(AccentDark), Alignment.Center) {
                        if (avatarUrl.isNotEmpty()) {
                            AsyncImage(model = avatarUrl, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape))
                        } else {
                            Text(chatTitle.take(2).uppercase(), color = Accent,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(chatTitle, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    // Кнопка поиска
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Search, null, tint = TextMuted)
                    }
                }
            }
        },
        bottomBar = {
            // Кнопка подписаться снизу
            Surface(color = BgSecondary, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = { vm.join(chatId) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                    ) {
                        Text("Подписаться", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    ) { pad ->
        if (posts.isEmpty() && loading) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text("Нет записей", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    ChannelPost(post = post)
                    HorizontalDivider(color = Border.copy(alpha = 0.4f), thickness = 0.5.dp)
                }
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Accent, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelPost(post: Message) {
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().background(BgPrimary).padding(horizontal = 12.dp, vertical = 12.dp)) {

        // Медиа вложения (картинки, видео, gif)
        if (post.attachments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                post.attachments.forEach { attach ->
                    when (attach.type) {
                        AttachType.IMAGE -> {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(attach.url).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                        AttachType.VIDEO -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(10.dp)).background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (attach.thumbnailUrl != null) {
                                    AsyncImage(model = attach.thumbnailUrl, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                // Оверлей плеера
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                                Box(Modifier.size(52.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)), Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White,
                                        modifier = Modifier.size(32.dp))
                                }
                                if (attach.duration != null) {
                                    Text(
                                        formatDuration(attach.duration),
                                        fontSize = 11.sp, color = Color.White,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                    )
                                }
                            }
                        }
                        AttachType.AUDIO -> {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)).background(BgCard)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(AccentDark),
                                    Alignment.Center) {
                                    Icon(Icons.Outlined.MusicNote, null, tint = Accent, modifier = Modifier.size(18.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(attach.name ?: "Аудио", style = MaterialTheme.typography.bodySmall)
                                    if (attach.duration != null) {
                                        Text(formatDuration(attach.duration), fontSize = 10.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                        AttachType.FILE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)).background(BgCard)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Outlined.InsertDriveFile, null, tint = Accent,
                                    modifier = Modifier.size(28.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(attach.name ?: "Файл", style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (attach.size != null) {
                                        Text(formatSize(attach.size), fontSize = 10.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
            if (post.text.isNotEmpty()) Spacer(Modifier.height(8.dp))
        }

        // Текст поста с поддержкой ссылок
        if (post.text.isNotEmpty()) {
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // Реакции и мета
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Реакции
            if (post.reactions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.reactions.take(6).forEach { (emoji, count) ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgCard)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 14.sp)
                            Text("$count", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // Просмотры и время
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (post.viewCount != null && post.viewCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Visibility, null, tint = TextHint, modifier = Modifier.size(13.dp))
                        Text(formatCount(post.viewCount), fontSize = 11.sp, color = TextHint)
                    }
                }
                if (post.time > 0) {
                    Text(formatPostTime(post.time), fontSize = 11.sp, color = TextHint)
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60; val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024))
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fK".format(n / 1_000.0)
    else           -> "$n"
}

private fun formatPostTime(ms: Long): String {
    val now  = System.currentTimeMillis()
    val diff = now - ms
    val cal  = java.util.Calendar.getInstance().also { it.timeInMillis = ms }
    return when {
        diff < 3_600_000 -> "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        else -> {
            val months = arrayOf("янв","фев","мар","апр","май","июн","июл","авг","сен","окт","ноя","дек")
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${months[cal.get(java.util.Calendar.MONTH)]}"
        }
    }
}
