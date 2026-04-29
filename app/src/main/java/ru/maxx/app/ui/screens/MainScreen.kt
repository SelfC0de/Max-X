package ru.maxx.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.screens.chats.ChatsScreen
import ru.maxx.app.ui.screens.contacts.ContactsScreen
import ru.maxx.app.ui.screens.channels.ChannelsScreen
import ru.maxx.app.ui.screens.profile.ProfileScreen
import ru.maxx.app.ui.screens.settings.SettingsScreen
import ru.maxx.app.ui.screens.favorites.FavoritesScreen
import ru.maxx.app.ui.theme.*

@Composable
fun MainScreen(
    container: AppContainer,
    onChatClick: (Long, String) -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings  by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(container = container, onBack = { showSettings = false })
        return
    }
    if (showFavorites) {
        FavoritesScreen(container = container, onBack = { showFavorites = false })
        return
    }

    Scaffold(
        containerColor = BgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MainBottomBar(selected = selectedTab, onSelect = { selectedTab = it })
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (selectedTab) {
                0 -> ChatsScreen(
                    container        = container,
                    onChatClick      = onChatClick,
                    onContactsClick  = { selectedTab = 1 },
                    onChannelsClick  = { selectedTab = 2 },
                    onProfileClick   = { selectedTab = 3 },
                    onFavoritesClick = { showFavorites = true }
                )
                1 -> ContactsScreen(
                    container       = container,
                    onBack          = { selectedTab = 0 },
                    onContactClick  = onChatClick
                )
                2 -> ChannelsScreen(
                    container      = container,
                    onBack         = { selectedTab = 0 },
                    onChannelClick = onChatClick
                )
                3 -> ProfileScreen(
                    container        = container,
                    onBack           = { selectedTab = 0 },
                    onSettingsClick  = { showSettings = true },
                    onFavoritesClick = { showFavorites = true },
                    onLogout         = onLogout
                )
            }
        }
    }
}

@Composable
private fun MainBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        HorizontalDivider(color = Border, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(BgSecondary)
                .navigationBarsPadding()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MainNavItem(Icons.Filled.Chat, Icons.Outlined.ChatBubbleOutline, "Чаты",       selected == 0) { onSelect(0) }
            MainNavItem(Icons.Filled.People, Icons.Outlined.PeopleOutline,   "Контакты",   selected == 1) { onSelect(1) }
            // FAB в центре
            FloatingActionButton(
                onClick = {},
                modifier      = Modifier.size(52.dp),
                containerColor = Accent,
                contentColor   = BgSecondary,
                elevation      = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(26.dp)) }
            MainNavItem(Icons.Filled.Campaign, Icons.Outlined.Campaign,          "Каналы", selected == 2) { onSelect(2) }
            MainNavItem(Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle, "Профиль", selected == 3) { onSelect(3) }
        }
    }
}

@Composable
private fun MainNavItem(
    iconOn: androidx.compose.ui.graphics.vector.ImageVector,
    iconOff: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = if (selected) iconOn else iconOff,
            contentDescription = label,
            tint     = if (selected) Accent else TextMuted,
            modifier = Modifier.size(26.dp)
        )
        Text(
            text       = label,
            fontSize   = 10.sp,
            color      = if (selected) Accent else TextMuted,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}
