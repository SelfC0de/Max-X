package ru.maxx.app.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import ru.maxx.app.core.network.MaxSocket
import ru.maxx.app.core.protocol.MaxProtocol
import ru.maxx.app.data.prefs.AuthPrefs
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

sealed class UploadResult {
    data class Progress(val percent: Int) : UploadResult()
    data class Success(val url: String, val fileId: String) : UploadResult()
    data class Failure(val error: String) : UploadResult()
}

private const val MAX_FILE_BYTES = 50 * 1024 * 1024  // 50 MB лимит

class MediaRepository(
    private val socket: MaxSocket,
    private val auth: AuthPrefs,
    private val ctx: Context
) {
    fun uploadFile(uri: Uri): Flow<UploadResult> = flow {
        emit(UploadResult.Progress(0))
        try {
            val resolver = ctx.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"

            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { stream ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var n: Int
                    while (stream.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        if (out.size() > MAX_FILE_BYTES)
                            throw Exception("Файл превышает 50 МБ")
                    }
                    out.toByteArray()
                } ?: throw Exception("Не удалось прочитать файл")
            }

            emit(UploadResult.Progress(20))

            val pkt = socket.sendAndAwait(MaxProtocol.Op.MEDIA_UPLOAD, mapOf(
                "mimeType" to mimeType, "size" to bytes.size.toLong()
            )) ?: throw Exception("Нет ответа сервера")

            if (pkt.cmd != MaxProtocol.CMD_OK)
                throw Exception("Сервер отклонил загрузку (${pkt.cmd})")

            val uploadUrl = pkt.payload["uploadUrl"]?.toString()
                ?: throw Exception("Нет URL для загрузки")
            val fileId = pkt.payload["fileId"]?.toString() ?: ""

            emit(UploadResult.Progress(40))

            // emit() нельзя внутри withContext — возвращаем результат наружу
            val httpCode = withContext(Dispatchers.IO) {
                var conn: HttpsURLConnection? = null
                try {
                    conn = URL(uploadUrl).openConnection() as HttpsURLConnection
                    conn.apply {
                        doOutput = true
                        requestMethod = "PUT"
                        setRequestProperty("Content-Type", mimeType)
                        setFixedLengthStreamingMode(bytes.size)
                        connectTimeout = 30_000
                        readTimeout    = 60_000
                    }
                    conn.outputStream.use { it.write(bytes) }
                    conn.responseCode
                } finally {
                    conn?.disconnect()
                }
            }
            if (httpCode !in 200..299) throw Exception("HTTP $httpCode")
            emit(UploadResult.Progress(100))
            emit(UploadResult.Success(uploadUrl, fileId))
        } catch (e: Exception) {
            emit(UploadResult.Failure(e.message ?: "Ошибка загрузки"))
        }
    }

    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        var conn: HttpsURLConnection? = null
        runCatching {
            conn = URL(url).openConnection() as HttpsURLConnection
            conn!!.apply { connectTimeout = 15_000; readTimeout = 30_000 }
            conn!!.inputStream.use { it.readBytes() }
        }.also {
            conn?.disconnect()  // FIX: disconnect в finally
        }.getOrNull()
    }
}
