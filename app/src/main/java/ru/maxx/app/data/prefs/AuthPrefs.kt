package ru.maxx.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * AuthPrefs: все операции через SharedPreferences.
 * tokenFlow реализован через OnSharedPreferenceChangeListener + callbackFlow —
 * гарантирует что Flow обновляется при изменении через SP.
 */
class AuthPrefs(private val ctx: Context) {

    private val sp: SharedPreferences =
        ctx.getSharedPreferences("auth_sp", Context.MODE_PRIVATE)

    private object K {
        const val TOKEN      = "token"
        const val SPOOF_DONE = "spoof_setup_done"
        const val USER_ID    = "user_id"
        const val MT_INST    = "mt_instance_id"
        const val CLIENT_SID = "client_session_id"
    }

    fun getToken(): String?      = sp.getString(K.TOKEN, null)
    fun getUserId(): String?     = sp.getString(K.USER_ID, null)
    fun getMtInstanceId(): String? = sp.getString(K.MT_INST, null)
    fun getClientSessionId(): Int  = sp.getInt(K.CLIENT_SID, 0)

    fun setToken(v: String)        { sp.edit { putString(K.TOKEN, v) } }
    fun setUserId(v: String?)      { sp.edit { if (v != null) putString(K.USER_ID, v) else remove(K.USER_ID) } }
    fun setMtInstanceId(v: String) { sp.edit { putString(K.MT_INST, v) } }
    fun setClientSessionId(v: Int) { sp.edit { putInt(K.CLIENT_SID, v) } }
    fun isSpoofSetupDone(): Boolean = sp.getBoolean(K.SPOOF_DONE, false)
    fun markSpoofSetupDone() = sp.edit { putBoolean(K.SPOOF_DONE, true) }

    fun clearAuth()                { sp.edit { remove(K.TOKEN); remove(K.USER_ID) } }

    // Flow обновляется при каждом setToken/clearAuth
    val tokenFlow: Flow<String?> = callbackFlow {
        trySend(sp.getString(K.TOKEN, null))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == K.TOKEN) trySend(sp.getString(K.TOKEN, null))
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()
}
