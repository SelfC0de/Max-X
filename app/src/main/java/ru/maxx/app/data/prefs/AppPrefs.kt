package ru.maxx.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appStore by preferencesDataStore("app_settings")

class AppPrefs(private val ctx: Context) {
    private object K {
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric")
        val PASS_LOCK_ENABLED  = booleanPreferencesKey("pass_lock")
        val PROXY_ENABLED      = booleanPreferencesKey("proxy_enabled")
        val PROXY_HOST         = stringPreferencesKey("proxy_host")
        val PROXY_PORT         = intPreferencesKey("proxy_port")
        val PROXY_USER         = stringPreferencesKey("proxy_user")
        val PROXY_PASS         = stringPreferencesKey("proxy_pass")
        val NOTIF_PERSONAL     = booleanPreferencesKey("notif_personal")
        val NOTIF_GROUPS       = booleanPreferencesKey("notif_groups")
        val NOTIF_CHANNELS     = booleanPreferencesKey("notif_channels")
        val NOTIF_SOUND        = booleanPreferencesKey("notif_sound")
        val NOTIF_VIBRO        = booleanPreferencesKey("notif_vibro")
        val SPOOF_ENABLED      = booleanPreferencesKey("spoof_enabled")
        val BYPASS_ENABLED     = booleanPreferencesKey("bypass_enabled")
        val PHONE_VISIBILITY   = stringPreferencesKey("phone_visibility")
        val ONLINE_VISIBILITY  = stringPreferencesKey("online_visibility")
        val AVATAR_VISIBILITY  = stringPreferencesKey("avatar_visibility")
        val FORWARD_LINK       = booleanPreferencesKey("forward_link")
        val FONT_SIZE          = stringPreferencesKey("font_size")
    }

    val biometricEnabled: Flow<Boolean>  = ctx.appStore.data.map { it[K.BIOMETRIC_ENABLED] ?: false }
    val passLockEnabled: Flow<Boolean>   = ctx.appStore.data.map { it[K.PASS_LOCK_ENABLED] ?: false }
    val proxyEnabled: Flow<Boolean>      = ctx.appStore.data.map { it[K.PROXY_ENABLED] ?: false }
    val proxyHost: Flow<String>          = ctx.appStore.data.map { it[K.PROXY_HOST] ?: "" }
    val proxyPort: Flow<Int>             = ctx.appStore.data.map { it[K.PROXY_PORT] ?: 1080 }
    val proxyUser: Flow<String>          = ctx.appStore.data.map { it[K.PROXY_USER] ?: "" }
    val proxyPass: Flow<String>          = ctx.appStore.data.map { it[K.PROXY_PASS] ?: "" }
    val notifPersonal: Flow<Boolean>     = ctx.appStore.data.map { it[K.NOTIF_PERSONAL] ?: true }
    val notifGroups: Flow<Boolean>       = ctx.appStore.data.map { it[K.NOTIF_GROUPS] ?: true }
    val notifChannels: Flow<Boolean>     = ctx.appStore.data.map { it[K.NOTIF_CHANNELS] ?: false }
    val notifSound: Flow<Boolean>        = ctx.appStore.data.map { it[K.NOTIF_SOUND] ?: true }
    val notifVibro: Flow<Boolean>        = ctx.appStore.data.map { it[K.NOTIF_VIBRO] ?: true }
    val spoofEnabled: Flow<Boolean>      = ctx.appStore.data.map { it[K.SPOOF_ENABLED] ?: true }
    val bypassEnabled: Flow<Boolean>     = ctx.appStore.data.map { it[K.BYPASS_ENABLED] ?: false }
    val phoneVisibility: Flow<String>    = ctx.appStore.data.map { it[K.PHONE_VISIBILITY] ?: "nobody" }
    val onlineVisibility: Flow<String>   = ctx.appStore.data.map { it[K.ONLINE_VISIBILITY] ?: "everyone" }
    val avatarVisibility: Flow<String>   = ctx.appStore.data.map { it[K.AVATAR_VISIBILITY] ?: "contacts" }
    val forwardLink: Flow<Boolean>       = ctx.appStore.data.map { it[K.FORWARD_LINK] ?: false }
    val fontSize: Flow<String>           = ctx.appStore.data.map { it[K.FONT_SIZE] ?: "medium" }

    suspend fun set(key: Preferences.Key<Boolean>, v: Boolean) = ctx.appStore.edit { it[key] = v }
    suspend fun set(key: Preferences.Key<String>, v: String)  = ctx.appStore.edit { it[key] = v }
    suspend fun set(key: Preferences.Key<Int>, v: Int)        = ctx.appStore.edit { it[key] = v }

    suspend fun setBiometric(v: Boolean) = ctx.appStore.edit { it[K.BIOMETRIC_ENABLED] = v }
    suspend fun setPassLock(v: Boolean)  = ctx.appStore.edit { it[K.PASS_LOCK_ENABLED] = v }
    suspend fun setProxyEnabled(v: Boolean) = ctx.appStore.edit { it[K.PROXY_ENABLED] = v }
    suspend fun setProxyHost(v: String)  = ctx.appStore.edit { it[K.PROXY_HOST] = v }
    suspend fun setProxyPort(v: Int)     = ctx.appStore.edit { it[K.PROXY_PORT] = v }
    suspend fun setProxyUser(v: String)  = ctx.appStore.edit { it[K.PROXY_USER] = v }
    suspend fun setProxyPass(v: String)  = ctx.appStore.edit { it[K.PROXY_PASS] = v }
    suspend fun setNotifPersonal(v: Boolean) = ctx.appStore.edit { it[K.NOTIF_PERSONAL] = v }
    suspend fun setNotifGroups(v: Boolean)   = ctx.appStore.edit { it[K.NOTIF_GROUPS] = v }
    suspend fun setNotifChannels(v: Boolean) = ctx.appStore.edit { it[K.NOTIF_CHANNELS] = v }
    suspend fun setNotifSound(v: Boolean)    = ctx.appStore.edit { it[K.NOTIF_SOUND] = v }
    suspend fun setNotifVibro(v: Boolean)    = ctx.appStore.edit { it[K.NOTIF_VIBRO] = v }
    suspend fun setSpoofEnabled(v: Boolean)  = ctx.appStore.edit { it[K.SPOOF_ENABLED] = v }
    suspend fun setBypassEnabled(v: Boolean) = ctx.appStore.edit { it[K.BYPASS_ENABLED] = v }
    suspend fun setPhoneVisibility(v: String)  = ctx.appStore.edit { it[K.PHONE_VISIBILITY] = v }
    suspend fun setOnlineVisibility(v: String) = ctx.appStore.edit { it[K.ONLINE_VISIBILITY] = v }
    suspend fun setAvatarVisibility(v: String) = ctx.appStore.edit { it[K.AVATAR_VISIBILITY] = v }
    suspend fun setForwardLink(v: Boolean)  = ctx.appStore.edit { it[K.FORWARD_LINK] = v }
    suspend fun setFontSize(v: String)       = ctx.appStore.edit { it[K.FONT_SIZE] = v }
}
