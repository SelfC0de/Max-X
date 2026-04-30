package ru.maxx.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppPrefs(private val ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // Приватность
    var hideTypingStatus: Boolean
        get() = sp.getBoolean("hide_typing", false)
        set(v) = sp.edit { putBoolean("hide_typing", v) }

    var autoMarkRead: Boolean
        get() = sp.getBoolean("auto_mark_read", true)
        set(v) = sp.edit { putBoolean("auto_mark_read", v) }

    // Прокси
    var proxyEnabled: Boolean
        get() = sp.getBoolean("proxy_enabled", false)
        set(v) = sp.edit { putBoolean("proxy_enabled", v) }

    var proxyHost: String
        get() = sp.getString("proxy_host", "") ?: ""
        set(v) = sp.edit { putString("proxy_host", v) }

    var proxyPort: Int
        get() = sp.getInt("proxy_port", 1080)
        set(v) = sp.edit { putInt("proxy_port", v) }

    var proxyUser: String
        get() = sp.getString("proxy_user", "") ?: ""
        set(v) = sp.edit { putString("proxy_user", v) }

    var proxyPass: String
        get() = sp.getString("proxy_pass", "") ?: ""
        set(v) = sp.edit { putString("proxy_pass", v) }

    // Множественные аккаунты

    // Безопасность
    var biometricEnabled: Boolean
        get() = sp.getBoolean("biometric_enabled", false)
        set(v) = sp.edit { putBoolean("biometric_enabled", v) }

    var passLockEnabled: Boolean
        get() = sp.getBoolean("pass_lock_enabled", false)
        set(v) = sp.edit { putBoolean("pass_lock_enabled", v) }

    // Уведомления
    var notifPersonal: Boolean
        get() = sp.getBoolean("notif_personal", true)
        set(v) = sp.edit { putBoolean("notif_personal", v) }

    var notifGroups: Boolean
        get() = sp.getBoolean("notif_groups", true)
        set(v) = sp.edit { putBoolean("notif_groups", v) }

    var notifChannels: Boolean
        get() = sp.getBoolean("notif_channels", false)
        set(v) = sp.edit { putBoolean("notif_channels", v) }

    var notifSound: Boolean
        get() = sp.getBoolean("notif_sound", true)
        set(v) = sp.edit { putBoolean("notif_sound", v) }

    var notifVibro: Boolean
        get() = sp.getBoolean("notif_vibro", true)
        set(v) = sp.edit { putBoolean("notif_vibro", v) }

    // Спуфинг
    var spoofEnabled: Boolean
        get() = sp.getBoolean("spoof_enabled", true)
        set(v) = sp.edit { putBoolean("spoof_enabled", v) }

    // Приватность
    var phoneVisibility: String
        get() = sp.getString("phone_vis", "everyone") ?: "everyone"
        set(v) = sp.edit { putString("phone_vis", v) }

    var onlineVisibility: String
        get() = sp.getString("online_vis", "everyone") ?: "everyone"
        set(v) = sp.edit { putString("online_vis", v) }

    fun getAccounts(): List<SavedAccount> {
        val raw = sp.getString("accounts", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("|").mapNotNull {
            val parts = it.split(";;")
            if (parts.size >= 3) SavedAccount(parts[0], parts[1], parts[2]) else null
        }
    }

    fun saveAccount(account: SavedAccount) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.userId == account.userId }
        accounts.add(account)
        sp.edit { putString("accounts", accounts.joinToString("|") { "${it.userId};;${it.phone};;${it.token}" }) }
    }

    fun removeAccount(userId: String) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.userId == userId }
        sp.edit { putString("accounts", accounts.joinToString("|") { "${it.userId};;${it.phone};;${it.token}" }) }
    }
}

data class SavedAccount(val userId: String, val phone: String, val token: String)
