package ru.maxx.app.di

import android.content.Context
import ru.maxx.app.core.crypto.E2ECrypto
import ru.maxx.app.core.export.ExportService
import ru.maxx.app.core.network.MaxSocket
import ru.maxx.app.core.network.SessionManager
import ru.maxx.app.core.spoofing.SpoofingManager
import ru.maxx.app.data.prefs.AppPrefs
import ru.maxx.app.data.prefs.AuthPrefs
import ru.maxx.app.data.repository.*

class AppContainer(val ctx: Context) {
    val authPrefs    by lazy { AuthPrefs(ctx) }
    val appPrefs     by lazy { AppPrefs(ctx) }
    val spoofing     by lazy { SpoofingManager(ctx) }
    val socket       by lazy { MaxSocket() }
    val session      by lazy { SessionManager(socket, spoofing, authPrefs) }
    val chatRepo     by lazy { ChatRepository(socket, authPrefs) }
    val contactRepo  by lazy { ContactRepository(socket, authPrefs) }
    val msgRepo      by lazy { MessageRepository(socket, authPrefs) }
    val mediaRepo    by lazy { MediaRepository(socket, authPrefs, ctx) }
    val e2eCrypto    by lazy { E2ECrypto(ctx) }
    val e2eRepo      by lazy { E2ERepository(socket, e2eCrypto, ctx) }
    val foldersRepo  by lazy { FoldersRepository(socket, ctx) }
    val favoritesRepo  by lazy { FavoritesRepository(ctx) }
    val exportService by lazy { ExportService(ctx) }
}
