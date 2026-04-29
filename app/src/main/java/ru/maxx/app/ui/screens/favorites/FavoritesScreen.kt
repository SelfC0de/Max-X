package ru.maxx.app.ui.screens.favorites

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxx.app.data.repository.FavoritesRepository
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.MaxXTopBar
import ru.maxx.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FavoritesScreen(container: AppContainer, onBack: () -> Unit) {
    val repo = remember { container.favoritesRepo }
    val favorites by repo.favorites.collectAsState()
    var search by remember { mutableStateOf("") }

    val filtered = remember(favorites, search) {
        if (search.isEmpty()) favorites
        else favorites.filter { it.message.text.contains(search, ignoreCase = true) || it.chatTitle.contains(search, ignoreCase = true) }
    }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            MaxXTopBar("Избранное", onBack = onBack, actions = {
                IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = Accent) }
            })
        }
    ) { pad ->
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BookmarkBorder, null, tint = TextHint, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Нет сохранённых сообщений", style = MaterialTheme.typography.bodyMedium)
                    Text("Зажмите сообщение → «Сохранить»", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(filtered, key = { it.message.id }) { saved ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { -it / 4 }) {
                        SavedMessageRow(
                            saved = saved,
                            onDelete = { repo.removeSaved(saved.message.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMessageRow(saved: FavoritesRepository.SavedMessage, onDelete: () -> Unit) {
    var showDelete by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { showDelete = !showDelete }
            .background(BgPrimary).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(AccentDark),
            Alignment.Center
        ) { Icon(Icons.Default.Bookmark, null, tint = Accent, modifier = Modifier.size(20.dp)) }

        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(saved.chatTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                Text(timeFmt.format(Date(saved.savedAt)), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(3.dp))
            Text(saved.message.text.ifEmpty { "[медиа]" }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }

    AnimatedVisibility(visible = showDelete) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 68.dp, vertical = 0.dp)) {
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Удалить из сохранённых", color = Red, fontSize = 12.sp)
            }
        }
    }

    HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 66.dp))
}
