package ru.maxx.app

import android.app.Application
import ru.maxx.app.core.notification.NotificationService
import ru.maxx.app.di.AppContainer

class MaxXApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        NotificationService.createChannels(this)  // FIX: регистрируем каналы при старте
    }

    companion object {
        // Безопасный доступ из non-Activity контекста (только после Application.onCreate)
        private var _instance: MaxXApp? = null
        val instance: MaxXApp get() = checkNotNull(_instance) { "MaxXApp not initialized" }
    }

    init { _instance = this }
}
