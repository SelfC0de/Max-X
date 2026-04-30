package ru.maxx.app.ui.screens.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.MaxXTopBar
import ru.maxx.app.ui.theme.*

@Composable
fun MediaGalleryScreen(
    container: AppContainer,
    chatId: Long,
    chatTitle: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mediaItems by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Медиа", "Файлы", "Аудио")

    LaunchedEffect(chatId, selectedTab) {
        loading = true
        val pkt = container.socket.sendAndAwait(
            ru.maxx.app.core.protocol.MaxProtocol.Op.MEDIA_LIST,
            mapOf("chatId" to chatId, "type" to when(selectedTab) {
                0 -> "PHOTO_VIDEO"; 1 -> "FILE"; else -> "AUDIO"
            }, "count" to 50)
        )
        @Suppress("UNCHECKED_CAST")
        mediaItems = (pkt?.payload?.get("media") as? List<Map<String, Any?>>) ?: emptyList()
        loading = false
    }

    Scaffold(
        containerColor = BgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MaxXTopBar("Медиа · $chatTitle", onBack = onBack)
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BgSecondary,
                contentColor = Accent,
                indicator = { tabs ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabs[selectedTab]),
                        color = Accent
                    )
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, fontSize = 13.sp, color = if (selectedTab == i) Accent else TextMuted) }
                    )
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (mediaItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Нет медиафайлов", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            } else if (selectedTab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mediaItems) { item ->
                        val url = item["previewUrl"]?.toString() ?: item["url"]?.toString() ?: ""
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).fillMaxWidth()
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mediaItems) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)).background(BgCard)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (selectedTab == 1) Icons.Outlined.InsertDriveFile else Icons.Outlined.AudioFile,
                                null, tint = Accent, modifier = Modifier.size(28.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item["name"]?.toString() ?: "Файл", style = MaterialTheme.typography.bodyMedium)
                                Text(item["size"]?.toString() ?: "", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
