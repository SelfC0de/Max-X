package ru.maxx.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.*
import ru.maxx.app.MaxXApp

class QuickReplyReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val chatId  = intent.getLongExtra(NotificationService.EXTRA_CHAT_ID, -1L)
        val notifId = intent.getIntExtra(NotificationService.EXTRA_NOTIF_ID, -1)
        if (chatId == -1L) return

        when (intent.action) {
            NotificationService.ACTION_REPLY -> {
                val bundle    = RemoteInput.getResultsFromIntent(intent) ?: return
                val replyText = bundle.getCharSequence(NotificationService.KEY_REPLY)
                    ?.toString()?.trim()
                if (replyText.isNullOrEmpty()) return

                val container = (ctx.applicationContext as? MaxXApp)?.container ?: return

                // FIX: goAsync() удерживает BroadcastReceiver живым до завершения корутины
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        val ok = container.msgRepo.sendMessage(chatId, replyText)
                        if (ok) NotificationManagerCompat.from(ctx).cancel(notifId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            NotificationService.ACTION_MARK_READ -> {
                NotificationManagerCompat.from(ctx).cancel(notifId)
            }
        }
    }
}
