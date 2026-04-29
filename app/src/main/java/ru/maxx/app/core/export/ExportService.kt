package ru.maxx.app.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.maxx.app.data.model.Message
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class ExportFormat { object TXT : ExportFormat(); object HTML : ExportFormat(); object JSON : ExportFormat() }
sealed class ExportResult { data class Success(val file: File) : ExportResult(); data class Error(val msg: String) : ExportResult() }

class ExportService(private val ctx: Context) {

    suspend fun exportChat(
        messages: List<Message>,
        chatTitle: String,
        myUserId: Long,
        format: ExportFormat = ExportFormat.TXT
    ): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safeTitle = chatTitle.replace(Regex("[^а-яА-ЯёЁa-zA-Z0-9_]"), "_").take(32)
            val ext = when (format) { is ExportFormat.TXT -> "txt"; is ExportFormat.HTML -> "html"; is ExportFormat.JSON -> "json" }
            val file = File(ctx.cacheDir, "export_${safeTitle}_$timestamp.$ext")

            val content = when (format) {
                ExportFormat.TXT  -> buildTxt(messages, chatTitle, myUserId)
                ExportFormat.HTML -> buildHtml(messages, chatTitle, myUserId)
                ExportFormat.JSON -> buildJson(messages, chatTitle)
            }
            file.writeText(content, Charsets.UTF_8)
            ExportResult.Success(file)
        }.getOrElse { ExportResult.Error(it.message ?: "Ошибка экспорта") }
    }

    fun share(file: File, format: ExportFormat) {
        val mime = when (format) {
            ExportFormat.TXT  -> "text/plain"
            ExportFormat.HTML -> "text/html"
            ExportFormat.JSON -> "application/json"
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Экспорт переписки").also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    private fun buildTxt(msgs: List<Message>, title: String, myId: Long): String = buildString {
        val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))
        appendLine("=== $title ===")
        appendLine("Экспорт: ${dateFmt.format(Date())}")
        appendLine("Сообщений: ${msgs.size}")
        appendLine()
        msgs.forEach { m ->
            val who = if (m.senderId == myId) "Вы" else m.senderName.ifEmpty { "ID:${m.senderId}" }
            val time = if (m.time > 0) dateFmt.format(Date(m.time)) else ""
            appendLine("[$time] $who:")
            if (m.text.isNotEmpty()) appendLine("  ${m.text}")
            m.attachments.forEach { a -> appendLine("  [${a.type.name}] ${a.name ?: a.url}") }
            appendLine()
        }
    }

    private fun buildHtml(msgs: List<Message>, title: String, myId: Long): String = buildString {
        val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))
        appendLine("<!DOCTYPE html><html lang='ru'><head><meta charset='UTF-8'>")
        appendLine("<title>${escHtml(title)}</title><style>")
        appendLine("body{font-family:Arial,sans-serif;background:#0a0a10;color:#e8e8f0;max-width:800px;margin:0 auto;padding:20px}")
        appendLine("h1{color:#8cbf26}.msg{margin:8px 0;padding:10px 14px;border-radius:12px;max-width:70%}")
        appendLine(".mine{background:#1a2e0d;color:#c8e8a0;margin-left:auto}.theirs{background:#161622}")
        appendLine(".meta{font-size:11px;opacity:0.6;margin-bottom:4px}.wrap{display:flex;flex-direction:column}")
        appendLine("</style></head><body>")
        appendLine("<h1>${escHtml(title)}</h1>")
        appendLine("<p style='color:#666'>Экспорт: ${dateFmt.format(Date())} · ${msgs.size} сообщений</p>")
        appendLine("<div class='wrap'>")
        msgs.forEach { m ->
            val isMine = m.senderId == myId
            val who = if (isMine) "Вы" else m.senderName.ifEmpty { "ID:${m.senderId}" }
            val time = if (m.time > 0) dateFmt.format(Date(m.time)) else ""
            appendLine("<div class='msg ${if (isMine) "mine" else "theirs"}'>")
            appendLine("<div class='meta'>${escHtml(who)} · $time${if (m.edited) " · ред." else ""}</div>")
            if (m.text.isNotEmpty()) appendLine("<div>${escHtml(m.text)}</div>")
            m.attachments.forEach { a -> appendLine("<div style='color:#8cbf26'>[${a.type.name}] ${escHtml(a.name ?: a.url)}</div>") }
            appendLine("</div>")
        }
        appendLine("</div></body></html>")
    }

    private fun buildJson(msgs: List<Message>, title: String): String {
        val sb = StringBuilder()
        val titleEsc = title.replace("\\", "\\\\").replace("\"", "\\\""); sb.append("{\"chat\":\"$titleEsc\",")
        sb.append(""exportedAt":${System.currentTimeMillis()},")
        sb.append(""messages":[")
        msgs.forEachIndexed { i, m ->
            if (i > 0) sb.append(",")
            val textEsc = m.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); sb.append("{\"id\":${m.id},\"sender\":${m.senderId},\"text\":\"$textEsc\",\"time\":${m.time},\"edited\":${m.edited}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun escHtml(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
