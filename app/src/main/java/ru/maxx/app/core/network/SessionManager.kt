package ru.maxx.app.core.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.*
import ru.maxx.app.core.protocol.MaxProtocol
import ru.maxx.app.core.spoofing.SpoofingManager
import java.util.UUID
import kotlin.random.Random

class SessionManager(
    private val socket: MaxSocket,
    private val spoofing: SpoofingManager,
    private val prefs: ru.maxx.app.data.prefs.AuthPrefs
) {
    private val TAG = "SessionManager"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    sealed class AuthState {
        object Unknown : AuthState()
        object Unauthenticated : AuthState()
        data class PhoneVerification(val token: String) : AuthState()
        data class PasswordRequired(val trackId: String, val hint: String?) : AuthState()
        object Authenticated : AuthState()
        data class Error(val msg: String) : AuthState()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            socket.state.collect { state ->
                when (state) {
                    MaxSocket.State.Connected -> sendHandshake()
                    MaxSocket.State.Disconnected -> { /* reconnect handled by socket */ }
                    else -> {}
                }
            }
        }
        scope.launch {
            socket.packets.collect { pkt -> handlePacket(pkt) }
        }
    }

    private suspend fun sendHandshake() {
        val profile = spoofing.getOrGenerate()
        val mtId = prefs.getMtInstanceId() ?: UUID.randomUUID().toString().also { prefs.setMtInstanceId(it) }
        val csId = prefs.getClientSessionId().takeIf { it != 0 } ?: Random.nextInt(1, 100).also { prefs.setClientSessionId(it) }
        val payload = mapOf(
            "mt_instanceid"   to mtId,
            "clientSessionId" to csId,
            "deviceId"        to profile.deviceId,
            "userAgent"       to spoofing.toHandshakePayload(profile)
        )
        Log.i(TAG, ">>> HANDSHAKE deviceId=${profile.deviceId} mtId=$mtId csId=$csId")
        Log.d(TAG, "    userAgent=${spoofing.toHandshakePayload(profile)}")
        socket.send(MaxProtocol.Op.HANDSHAKE, payload)
    }

    // requestOtp/verifyOtp: отправляем пакет, ответ приходит через handlePacket → _authState
    // НЕ используем sendAndAwait чтобы избежать двойной обработки одного пакета
    suspend fun requestOtp(phone: String): Boolean {
        val state = socket.state.value
        Log.i(TAG, "requestOtp: socket.state=$state")

        if (state != MaxSocket.State.Connected && state != MaxSocket.State.Authorized) {
            Log.i(TAG, "Not connected, connecting...")
            socket.connect()
        }

        // Ждём пока handshake завершится (authState станет Unauthenticated или Authenticated)
        // Таймаут 10 секунд
        val ready = withTimeoutOrNull(10_000L) {
            authState.first { it is AuthState.Unauthenticated || it is AuthState.Authenticated || it is AuthState.Error }
        }

        if (ready == null) {
            Log.e(TAG, "requestOtp: timeout waiting for handshake")
            _authState.value = AuthState.Error("Не удалось подключиться к серверу")
            return false
        }
        if (ready is AuthState.Error) {
            Log.e(TAG, "requestOtp: handshake error: ${ready.msg}")
            return false
        }

        Log.i(TAG, ">>> AUTH_PHONE phone=$phone (handshake OK, state=$ready)")
        socket.send(MaxProtocol.Op.AUTH_PHONE, mapOf("phone" to phone, "type" to "START_AUTH"))
        return true
    }

    suspend fun verifyOtp(token: String, code: String): Boolean {
        socket.send(MaxProtocol.Op.AUTH_VERIFY, mapOf(
            "verifyCode" to code, "token" to token, "authTokenType" to "CHECK_CODE"
        ))
        return true
    }

    suspend fun sendPassword(trackId: String, password: String): Boolean {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.AUTH_PASSWORD,
            mapOf("trackId" to trackId, "password" to password))
        if (pkt?.cmd == MaxProtocol.CMD_OK) return true
        if (pkt?.cmd == MaxProtocol.CMD_ERROR) {
            _authState.value = AuthState.Error(pkt.payload["message"]?.toString() ?: "Неверный пароль")
        }
        return false
    }

    private fun handlePacket(pkt: MaxProtocol.Packet) {
        // ── Полный лог каждого пакета для диагностики ──────────────────────
        Log.d(TAG, "<<< PKT opcode=${pkt.opcode} cmd=${pkt.cmd} seq=${pkt.seq}")
        Log.d(TAG, "    payload=${pkt.payload}")

        when (pkt.opcode) {
            MaxProtocol.Op.HANDSHAKE -> {
                Log.i(TAG, "HANDSHAKE response: cmd=${pkt.cmd} payload=${pkt.payload}")
                if (pkt.cmd == MaxProtocol.CMD_OK) {
                    val token = prefs.getToken()
                    if (token != null) {
                        scope.launch { authenticate(token) }
                    } else {
                        Log.i(TAG, "HANDSHAKE OK — no token, going Unauthenticated")
                        _authState.value = AuthState.Unauthenticated
                    }
                } else {
                    // Сервер отклонил handshake
                    val errMsg = pkt.payload["message"]?.toString()
                        ?: pkt.payload["error"]?.toString()
                        ?: "Handshake rejected cmd=${pkt.cmd}"
                    Log.e(TAG, "HANDSHAKE ERROR: $errMsg")
                    _authState.value = AuthState.Error("Ошибка подключения: $errMsg")
                }
            }
            MaxProtocol.Op.AUTH_PHONE -> {
                Log.i(TAG, "AUTH_PHONE response: cmd=${pkt.cmd} payload=${pkt.payload}")
                when (pkt.cmd) {
                    MaxProtocol.CMD_OK -> {
                        val token = pkt.payload["token"]?.toString()
                        if (token != null) {
                            Log.i(TAG, "AUTH_PHONE OK: token=$token")
                            _authState.value = AuthState.PhoneVerification(token)
                        } else {
                            Log.e(TAG, "AUTH_PHONE OK but no token in payload!")
                            _authState.value = AuthState.Error("Нет токена в ответе сервера")
                        }
                    }
                    MaxProtocol.CMD_ERROR -> {
                        val errMsg = pkt.payload["message"]?.toString()
                            ?: pkt.payload["error"]?.toString()
                            ?: pkt.payload["reason"]?.toString()
                            ?: "Неизвестная ошибка (code=${pkt.payload["code"]})"
                        val errCode = pkt.payload["code"]?.toString() ?: ""
                        Log.e(TAG, "AUTH_PHONE ERROR code=$errCode msg=$errMsg full=${pkt.payload}")
                        _authState.value = AuthState.Error("$errMsg (код: $errCode)")
                    }
                    else -> {
                        Log.w(TAG, "AUTH_PHONE unknown cmd=${pkt.cmd} payload=${pkt.payload}")
                        _authState.value = AuthState.Error("Неожиданный ответ сервера: cmd=${pkt.cmd}")
                    }
                }
            }
            MaxProtocol.Op.AUTH_VERIFY, MaxProtocol.Op.AUTH_CHATS -> {
                Log.i(TAG, "AUTH_VERIFY/CHATS opcode=${pkt.opcode} cmd=${pkt.cmd} payload=${pkt.payload}")
                if (pkt.cmd == MaxProtocol.CMD_OK) {
                    // Токен может быть в разных местах payload:
                    // 1. payload["token"] — прямой
                    // 2. payload["tokenAttrs"]["LOGIN"]["token"] — вложенный (реальный формат сервера)
                    val tok: String? = pkt.payload["token"] as? String
                        ?: run {
                            val tokenAttrs = pkt.payload["tokenAttrs"] as? Map<*, *>
                            val loginBlock = tokenAttrs?.get("LOGIN") as? Map<*, *>
                            loginBlock?.get("token") as? String
                        }

                    // userId из profile.contact.id
                    val userId: String? = (pkt.payload["userId"] ?: pkt.payload["id"])?.toString()
                        ?: run {
                            val profile = pkt.payload["profile"] as? Map<*, *>
                            val contact = profile?.get("contact") as? Map<*, *>
                            contact?.get("id")?.toString()
                        }

                    Log.i(TAG, "AUTH_VERIFY: extracted tok=${tok?.take(20)}... userId=$userId")

                    if (tok != null) {
                        prefs.setToken(tok)
                        prefs.setUserId(userId)
                        socket.markAuthorized()
                        Log.i(TAG, "AUTH_VERIFY: SUCCESS — token saved, userId=$userId")
                        _authState.value = AuthState.Authenticated
                    } else if (pkt.payload["passwordChallenge"] != null) {
                        val ch = pkt.payload["passwordChallenge"] as? Map<*, *>
                        _authState.value = AuthState.PasswordRequired(
                            trackId = ch?.get("trackId")?.toString() ?: "",
                            hint    = ch?.get("hint")?.toString()
                        )
                    } else {
                        Log.e(TAG, "AUTH_VERIFY: no token found in payload! keys=${pkt.payload.keys}")
                        _authState.value = AuthState.Error("Не удалось получить токен авторизации")
                    }
                } else if (pkt.cmd == MaxProtocol.CMD_ERROR) {
                    val errMsg = pkt.payload["message"]?.toString() ?: "Ошибка верификации"
                    Log.e(TAG, "AUTH_VERIFY ERROR: $errMsg full=${pkt.payload}")
                    _authState.value = AuthState.Error(errMsg)
                }
            }
        }
    }

    private suspend fun authenticate(token: String) {
        socket.send(MaxProtocol.Op.AUTH_CHATS, mapOf(
            "token" to token,
            "limit" to 50,
            "offset" to 0
        ))
    }

    fun cancel() {
        scope.cancel()
    }

    fun logout() {
        prefs.clearAuth()
        _authState.value = AuthState.Unauthenticated
    }
}
