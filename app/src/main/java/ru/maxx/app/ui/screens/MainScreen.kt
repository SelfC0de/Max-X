package ru.maxx.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.screens.chats.ChatsScreen
import ru.maxx.app.ui.screens.contacts.ContactsScreen
import ru.maxx.app.ui.screens.profile.ProfileScreen
import ru.maxx.app.ui.screens.settings.SettingsScreen
import ru.maxx.app.ui.screens.auth.SpoofSetupScreen
import ru.maxx.app.ui.screens.favorites.FavoritesScreen
import ru.maxx.app.ui.theme.*

@Composable
fun MainScreen(
    container: AppContainer,
    onChatClick: (Long, String) -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab  by remember { mutableIntStateOf(0) }
    var showSettings    by remember { mutableStateOf(false) }
    var showFavorites   by remember { mutableStateOf(false) }
    var showSpoofSetup  by remember { mutableStateOf(false) }

    if (showSpoofSetup) {
        SpoofSetupScreen(
            spoofing  = container.spoofing,
            onApplied = { showSpoofSetup = false },
            onBack    = { showSpoofSetup = false; showSettings = true }
        )
        return
    }
    if (showSettings) {
        SettingsScreen(container = container, onBack = { showSettings = false }, onSpoofSetup = { showSettings = false; showSpoofSetup = true })
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
            MaxXBottomBar(
                selected = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    ) { pad ->
        AnimatedContent(
            targetState    = selectedTab,
            modifier       = Modifier.fillMaxSize().padding(pad),
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(180)) togetherWith
                    slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(180))
                } else {
                    slideInHorizontally(tween(220)) { -it / 5 } + fadeIn(tween(180)) togetherWith
                    slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(180))
                }
            },
            label = "tab_content"
        ) { tab ->
            when (tab) {
                0 -> ChatsScreen(
                    container        = container,
                    onChatClick      = onChatClick,
                    onContactsClick  = { selectedTab = 1 },
                    onProfileClick   = { selectedTab = 3 },
                    onFavoritesClick = { showFavorites = true }
                )
                1 -> ContactsScreen(
                    container      = container,
                    onBack         = { selectedTab = 0 },
                    onContactClick = onChatClick
                )
                2 -> ProfileScreen(
                    container        = container,
                    onBack           = { selectedTab = 0 },
                    onSettingsClick  = { showSettings = true },
                    onFavoritesClick = { showFavorites = true },
                    onLogout         = onLogout
                )
                else -> {}
            }
        }
    }
}

// Таббар точно как в оригинале MAX
@Composable
private fun MaxXBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        HorizontalDivider(color = Border, thickness = 0.5.dp)
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            containerColor = BgSecondary,
            tonalElevation = 0.dp
        ) {
            // Чаты
            NavBarItem(
                icon     = Icons.Filled.Chat,
                iconOff  = Icons.Outlined.ChatBubbleOutline,
                label    = "Чаты",
                selected = selected == 0,
                onClick  = { onSelect(0) }
            )
            // Контакты
            NavBarItem(
                icon     = Icons.Filled.People,
                iconOff  = Icons.Outlined.PeopleOutline,
                label    = "Контакты",
                selected = selected == 1,
                onClick  = { onSelect(1) }
            )
            // Каналы
            NavBarItem(
                icon     = Icons.Filled.Campaign,
                iconOff  = Icons.Outlined.Campaign,
                label    = "Каналы",
                selected = selected == 2,
                onClick  = { onSelect(2) }
            )
            // Профиль
            NavBarItem(
                icon     = Icons.Filled.AccountCircle,
                iconOff  = Icons.Outlined.AccountCircle,
                label    = "Профиль",
                selected = selected == 3,
                onClick  = { onSelect(3) }
            )
        }
    }
}

@Composable
private fun RowScope.NavBarItem(
    icon: ImageVector,
    iconOff: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick  = onClick,
        icon = {
            Icon(
                imageVector = if (selected) icon else iconOff,
                contentDescription = label,
                modifier = Modifier.size(26.dp)
            )
        },
        label = {
            Text(
                text       = label,
                fontSize   = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor   = Accent,
            selectedTextColor   = Accent,
            unselectedIconColor = TextMuted,
            unselectedTextColor = TextMuted,
            indicatorColor      = AccentDark
        )
    )
}
