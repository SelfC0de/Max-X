package ru.maxx.app.core.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.maxx.app.core.protocol.MaxProtocol
import ru.maxx.app.core.security.NetworkGuard
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocketFactory

class MaxSocket {
    private val TAG = "MaxSocket"
    private val HOSTS = listOf("api.oneme.ru" to 443, "ws-api.oneme.ru" to 443, "ws-api.oneme.ru" to 8443)
    private val PING_INTERVAL_MS = 25_000L
    private val RECONNECT_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)

    sealed class State {
        object Disconnected : State()
        object Connecting : State()
        object Connected : State()
        object Authorized : State()
        data class Error(val msg: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _packets = MutableSharedFlow<MaxProtocol.Packet>(extraBufferCapacity = 512)
    val packets: SharedFlow<MaxProtocol.Packet> = _packets.asSharedFlow()

    @Volatile private var sslSocket: javax.net.ssl.SSLSocket? = null
    private val seq = AtomicInteger(0)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<MaxProtocol.Packet>>()
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectAttempt = 0

    suspend fun connect(): Boolean {
        if (_state.value == State.Connected || _state.value == State.Authorized) return true
        _state.value = State.Connecting
        for ((host, port) in HOSTS) {
            try {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val raw = NetworkGuard.guardedSocketFactory.createSocket(host, port)
                val ssl = factory.createSocket(raw, host, port, true) as javax.net.ssl.SSLSocket
                ssl.soTimeout = 40_000
                ssl.startHandshake()
                sslSocket = ssl
                _state.value = State.Connected
                reconnectAttempt = 0
                startRead()
                startPing()
                Log.i(TAG, "Connected $host:$port")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed $host:$port: ${e.message}")
            }
        }
        _state.value = State.Error("Нет подключения к серверу")
        scheduleReconnect()
        return false
    }

    suspend fun send(opcode: Int, payload: Map<String, Any?>): Int {
        // Блокируем шпионские опкоды MAX:
        // opcode=5   HOST_REACHABILITY — VPN-статус, IP, хосты
        // opcode=1   ANALYTICS_EVENT  — аналитика
        // opcode=60-64 CALL_* — звонки (нет функционала + KWS прослушка)
        if (opcode == 5 || opcode == 1 || opcode in 60..64) {
            android.util.Log.w("MaxSocket", "BLOCKED spy opcode=$opcode payload=${payload.keys}")
            return -1
        }
        val s = (seq.incrementAndGet() and 0xFF)
        val bytes = MaxProtocol.pack(s, opcode, payload)
        withContext(Dispatchers.IO) {
            try {
                sslSocket?.outputStream?.let { out -> out.write(bytes); out.flush() }
                    ?: throw IOException("Socket not connected")
            } catch (e: IOException) {
                Log.e(TAG, "Send error op=$opcode: ${e.message}")
                handleDisconnect()
            }
        }
        return s
    }

    suspend fun sendAndAwait(opcode: Int, payload: Map<String, Any?>, timeoutMs: Long = 12_000L): MaxProtocol.Packet? {
        val s = send(opcode, payload)
        val def = CompletableDeferred<MaxProtocol.Packet>()
        // Если слот занят — старый запрос истёк, отменяем его
        pending.put(s, def)?.cancel()
        pending[s] = def
        return try {
            withTimeout(timeoutMs) { def.await() }
        } catch (_: TimeoutCancellationException) {
            pending.remove(s)
            Log.w(TAG, "Timeout op=$opcode seq=$s")
            null
        }
    }

    // Ожидание пакета по opcode без seq (push от сервера)
    suspend fun awaitOpcode(opcode: Int, timeoutMs: Long = 15_000L): MaxProtocol.Packet? =
        withTimeoutOrNull(timeoutMs) {
            packets.first { it.opcode == opcode && (it.cmd == MaxProtocol.CMD_OK || it.cmd == MaxProtocol.CMD_ERROR) }
        }

    private fun startRead() {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val inStream = sslSocket?.inputStream ?: return@launch
            val buf = ByteArray(32_768)
            // Используем ByteArrayOutputStream вместо += ByteArray (убираем O(n²))
            val accumulator = java.io.ByteArrayOutputStream(65_536)
            while (isActive) {
                try {
                    val n = inStream.read(buf)
                    if (n < 0) { handleDisconnect(); break }
                    accumulator.write(buf, 0, n)
                    val (packets, remaining) = MaxProtocol.tryUnpackMany(accumulator.toByteArray())
                    accumulator.reset()
                    if (remaining.isNotEmpty()) accumulator.write(remaining)
                    packets.forEach { pkt ->
                        pending.remove(pkt.seq)?.complete(pkt)
                        _packets.tryEmit(pkt)
                    }
                } catch (e: IOException) {
                    if (isActive) { Log.e(TAG, "Read error: ${e.message}"); handleDisconnect() }
                    break
                }
            }
        }
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                if (_state.value == State.Authorized) {
                    send(MaxProtocol.Op.PING, mapOf("interactive" to false))
                }
            }
        }
    }

    private fun handleDisconnect() {
        if (_state.value == State.Disconnected) return
        closeSocket()
        _state.value = State.Disconnected
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        scope.launch {
            val delay = RECONNECT_DELAYS_MS.getOrElse(reconnectAttempt) { 60_000L }
            reconnectAttempt++
            Log.i(TAG, "Reconnect in ${delay}ms (attempt $reconnectAttempt)")
            delay(delay)
            // Не переподключаемся если явно disconnect() был вызван
            if (_state.value != State.Disconnected) connect()
        }
    }

    private fun closeSocket() {
        pingJob?.cancel()
        readJob?.cancel()
        val snapshot = pending.values.toList()  // FIX: snapshot перед итерацией
        pending.clear()
        snapshot.forEach { it.cancel() }
        runCatching { sslSocket?.close() }
        sslSocket = null
    }

    fun disconnect() {
        scope.coroutineContext.cancelChildren()  // отменяем reconnect timer
        reconnectAttempt = 0
        closeSocket()
        _state.value = State.Disconnected
    }

    fun markAuthorized() { _state.value = State.Authorized }
}
