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
        object CodeExpired : AuthState()   // код устарел — нужно запросить снова
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
        // Отправляем — handlePacket обработает ответ opcode=115
        socket.send(MaxProtocol.Op.AUTH_PASSWORD,
            mapOf("trackId" to trackId, "password" to password))
        return true
    }

    private fun handlePacket(pkt: MaxProtocol.Packet) {
        // Игнорируем шпионские и call пакеты
        if (pkt.opcode == 5 || pkt.opcode == 1 || pkt.opcode in 60..64) {
            Log.w(TAG, "Ignored spy packet opcode=${pkt.opcode}")
            return
        }
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
            MaxProtocol.Op.AUTH_PASSWORD -> {
                Log.i(TAG, "AUTH_PASSWORD opcode=${pkt.opcode} cmd=${pkt.cmd}")
                if (pkt.cmd == MaxProtocol.CMD_OK) {
                    @Suppress("UNCHECKED_CAST")
                    val tokenAttrs = pkt.payload["tokenAttrs"] as? Map<*, *>
                    val loginToken = (tokenAttrs?.get("LOGIN") as? Map<*, *>)?.get("token")?.toString()
                    if (!loginToken.isNullOrBlank()) {
                        prefs.setToken(loginToken)
                        Log.i(TAG, "AUTH_PASSWORD: SUCCESS — token saved, server will send AUTH_CHATS")
                        // Сервер сам пришлёт opcode=19 — не вызываем authenticate()
                    }
                } else if (pkt.cmd == MaxProtocol.CMD_ERROR) {
                    val msg = pkt.payload["localizedMessage"]?.toString()
                        ?: pkt.payload["message"]?.toString()
                        ?: "Неверный пароль"
                    Log.e(TAG, "AUTH_PASSWORD ERROR: $msg")
                    _authState.value = AuthState.Error(msg)
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
                        // Сохраняем имя и телефон из profile.contact
                        val contact = (pkt.payload["profile"] as? Map<*, *>)?.get("contact") as? Map<*, *>
                        val names   = contact?.get("names") as? List<*>
                        val nameMap = names?.firstOrNull() as? Map<*, *>
                        val firstName = nameMap?.get("firstName")?.toString() ?: ""
                        val lastName  = nameMap?.get("lastName")?.toString() ?: ""
                        val fullName  = listOf(firstName, lastName).filter { it.isNotBlank() && it != "Deleted" }.joinToString(" ")
                        if (fullName.isNotBlank()) prefs.setUserName(fullName)
                        val phone = contact?.get("phone")?.toString()
                        if (!phone.isNullOrBlank()) prefs.setUserPhone("+$phone".replace("++", "+"))
                        socket.markAuthorized()
                        Log.i(TAG, "AUTH_VERIFY: SUCCESS — token saved, userId=$userId, name=$fullName, loading chats...")
                        // Загружаем чаты сразу с новым токеном (не через prefs — он может не успеть)
                        scope.launch { authenticate(tok) }
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
                    val errMsg = pkt.payload["message"]?.toString()
                        ?: pkt.payload["error"]?.toString()
                        ?: pkt.payload["reason"]?.toString()
                        ?: "Ошибка верификации"
                    val errCode = pkt.payload["code"]?.toString() ?: ""
                    Log.e(TAG, "AUTH_VERIFY ERROR code=$errCode msg=$errMsg full=${pkt.payload}")
                    // proto.state = сервер уже обработал AUTH_CHATS и шлёт дубль — игнорируем
                    // proto.state — ошибка дубля AUTH_CHATS, всегда игнорируем
                    if (errCode == "proto.state" || errMsg.contains("proto.state") || errMsg.contains("NEW session")) {
                        Log.w(TAG, "Ignoring proto.state error")
                        return
                    }
                    // Код устарел / неверен — предлагаем запросить снова
                    val isExpired = errMsg.contains("устар", ignoreCase = true)
                        || errMsg.contains("expired", ignoreCase = true)
                        || errMsg.contains("invalid", ignoreCase = true)
                        || errCode == "VERIFY_CODE_EXPIRED"
                        || errCode == "VERIFY_CODE_INVALID"
                    if (isExpired) {
                        _authState.value = AuthState.CodeExpired
                    } else {
                        _authState.value = AuthState.Error(errMsg)
                    }
                }
            }
        }
    }

    suspend fun loadSessions(): List<Map<String, Any?>> {
        val pkt = socket.sendAndAwait(MaxProtocol.Op.SESSIONS_LIST, mapOf("limit" to 50))
            ?: return emptyList()
        Log.d(TAG, "SESSIONS payload keys: ${pkt.payload.keys}")
        if (pkt.cmd == MaxProtocol.CMD_OK) {
            @Suppress("UNCHECKED_CAST")
            val list = (pkt.payload["sessions"] as? List<Map<String, Any?>>)
                ?: (pkt.payload["devices"] as? List<Map<String, Any?>>)
                ?: emptyList()
            if (list.isNotEmpty()) Log.d(TAG, "SESSIONS first item keys: ${list.first().keys}")
            return list
        }
        return emptyList()
    }

    suspend fun terminateSession(sessionId: String) {
        socket.send(MaxProtocol.Op.SESSIONS_TERMINATE, mapOf("sessionId" to sessionId))
    }

    suspend fun authenticateWithToken(token: String) {
        if (socket.state !is MaxSocket.State.Connected) {
            socket.connect()
        }
        socket.markAuthorized()
        _authState.value = AuthState.Authenticated
        scope.launch { authenticate(token) }
    }

    private suspend fun authenticate(token: String) {
        // sendAndAwait перехватывает ответ по seq — handlePacket его не получит
        socket.sendAndAwait(MaxProtocol.Op.AUTH_CHATS, mapOf(
            "token" to token,
            "limit" to 50,
            "offset" to 0
        ), timeoutMs = 15_000L)
        // Ответ обрабатывается в handlePacket только если пришёл вне sendAndAwait
        // sendAndAwait забирает пакет раньше
    }

    fun cancel() {
        scope.cancel()
    }

    fun logout() {
        prefs.clearAuth()
        _authState.value = AuthState.Unauthenticated
    }
}
