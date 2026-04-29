package ru.maxx.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.*
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.screens.auth.PhoneScreen
import ru.maxx.app.ui.screens.auth.OtpScreen
import ru.maxx.app.ui.screens.chats.ChatsScreen
import ru.maxx.app.ui.screens.chat.ChatScreen
import ru.maxx.app.ui.screens.contacts.ContactsScreen
import ru.maxx.app.ui.screens.channels.ChannelsScreen
import ru.maxx.app.ui.screens.profile.ProfileScreen
import ru.maxx.app.ui.screens.settings.SettingsScreen
import ru.maxx.app.ui.screens.favorites.FavoritesScreen

sealed class Route(val path: String) {
    object Phone     : Route("phone")
    object Otp       : Route("otp/{token}") { fun go(t: String) = "otp/$t" }
    object Chats     : Route("chats")
    object Chat      : Route("chat/{chatId}/{title}") {
        fun go(id: Long, title: String) = "chat/$id/${java.net.URLEncoder.encode(title, "UTF-8")}"
    }
    object Contacts  : Route("contacts")
    object Channels  : Route("channels")
    object Profile   : Route("profile")
    object Settings  : Route("settings")
    object Favorites : Route("favorites")
}

@Composable
fun AppNavGraph(
    nav: NavHostController,
    startDest: String,
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val slideIn  = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))
    val slideOut = slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(180))
    val popIn    = slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(220))
    val popOut   = slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(180))

    NavHost(nav, startDest, modifier = modifier,
        enterTransition = { slideIn }, exitTransition = { slideOut },
        popEnterTransition = { popIn }, popExitTransition = { popOut }
    ) {
        composable(Route.Phone.path) {
            PhoneScreen(container = container,
                onOtpRequested = { nav.navigate(Route.Otp.go(it)) })
        }
        composable(Route.Otp.path, listOf(navArgument("token") { type = NavType.StringType })) { back ->
            OtpScreen(container = container,
                token = back.arguments?.getString("token") ?: "",
                onAuthorized = { nav.navigate(Route.Chats.path) { popUpTo(0) { inclusive = true } } })
        }
        composable(Route.Chats.path) {
            ChatsScreen(container = container,
                onChatClick      = { id, t -> nav.navigate(Route.Chat.go(id, t)) },
                onContactsClick  = { nav.navigate(Route.Contacts.path) },
                onChannelsClick  = { nav.navigate(Route.Channels.path) },
                onProfileClick   = { nav.navigate(Route.Profile.path) },
                onFavoritesClick = { nav.navigate(Route.Favorites.path) })
        }
        composable(Route.Chat.path, listOf(
            navArgument("chatId") { type = NavType.LongType },
            navArgument("title")  { type = NavType.StringType }
        )) { back ->
            ChatScreen(container = container,
                chatId = back.arguments?.getLong("chatId") ?: 0L,
                title  = java.net.URLDecoder.decode(back.arguments?.getString("title") ?: "", "UTF-8"),
                onBack = { nav.popBackStack() })
        }
        composable(Route.Contacts.path) {
            ContactsScreen(container = container,
                onBack = { nav.popBackStack() },
                onContactClick = { id, t -> nav.navigate(Route.Chat.go(id, t)) })
        }
        composable(Route.Channels.path) {
            ChannelsScreen(container = container,
                onBack = { nav.popBackStack() },
                onChannelClick = { id, t -> nav.navigate(Route.Chat.go(id, t)) })
        }
        composable(Route.Profile.path) {
            ProfileScreen(container = container,
                onBack          = { nav.popBackStack() },
                onSettingsClick = { nav.navigate(Route.Settings.path) },
                onFavoritesClick = { nav.navigate(Route.Favorites.path) },
                onLogout = {
                    container.session.logout()
                    nav.navigate(Route.Phone.path) { popUpTo(0) { inclusive = true } }
                })
        }
        composable(Route.Settings.path) {
            SettingsScreen(container = container, onBack = { nav.popBackStack() })
        }
        composable(Route.Favorites.path) {
            FavoritesScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}
