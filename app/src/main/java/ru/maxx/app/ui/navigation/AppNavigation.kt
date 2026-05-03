package ru.maxx.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.*
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.screens.auth.PhoneScreen
import ru.maxx.app.ui.screens.auth.SpoofSetupScreen
import ru.maxx.app.ui.screens.auth.OtpScreen
import ru.maxx.app.ui.screens.chat.ChatScreen
import ru.maxx.app.ui.screens.MainScreen

sealed class Route(val path: String) {
    object SpoofSetup : Route("spoof_setup")
    object Phone     : Route("phone")
    object Otp       : Route("otp/{token}/{phone}") { fun go(t: String, p: String) = "otp/$t/${java.net.URLEncoder.encode(p, "UTF-8")}" }
    object Chats     : Route("chats")
    object Chat      : Route("chat/{chatId}/{title}") {
        fun go(id: Long, title: String) = "chat/$id/${java.net.URLEncoder.encode(title, "UTF-8")}"
    }
    object Contacts  : Route("contacts")
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
        composable(Route.SpoofSetup.path) {
            SpoofSetupScreen(
                spoofing = container.spoofing,
                onApplied = { nav.navigate(Route.Phone.path) { popUpTo(Route.SpoofSetup.path) { inclusive = true } } }
            )
        }
        composable(Route.Phone.path) {
            PhoneScreen(container = container,
                onOtpRequested = { token, phone -> nav.navigate(Route.Otp.go(token, phone)) },
                onAuthorized = { nav.navigate(Route.Chats.path) { popUpTo(0) { inclusive = true } } })
        }
        composable(Route.Otp.path, listOf(
                navArgument("token") { type = NavType.StringType },
                navArgument("phone") { type = NavType.StringType; defaultValue = "" }
            )) { back ->
            OtpScreen(container = container,
                token = back.arguments?.getString("token") ?: "",
                phone = java.net.URLDecoder.decode(back.arguments?.getString("phone") ?: "", "UTF-8"),
                onAuthorized = { nav.navigate(Route.Chats.path) { popUpTo(0) { inclusive = true } } })
        }
        composable(Route.Chats.path) {
            MainScreen(
                container   = container,
                onChatClick = { id, t -> nav.navigate(Route.Chat.go(id, t)) },
                onLogout    = {
                    container.session.logout()
                    nav.navigate(Route.Phone.path) { popUpTo(0) { inclusive = true } }
                }
            )
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
    }
}
