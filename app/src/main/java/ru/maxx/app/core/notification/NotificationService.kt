package ru.maxx.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import ru.maxx.app.R
import ru.maxx.app.data.model.Message

object NotificationService {

    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_SILENT   = "silent"
    const val KEY_REPLY        = "key_reply"
    const val EXTRA_CHAT_ID    = "chat_id"
    const val EXTRA_NOTIF_ID   = "notif_id"
    const val ACTION_REPLY     = "ru.maxx.app.REPLY"
    const val ACTION_MARK_READ = "ru.maxx.app.MARK_READ"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Входящие сообщения" })
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_SILENT, "Без звука", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Уведомления без звука" })
    }

    fun showMessageNotification(
        ctx: Context,
        chatId: Long,
        chatTitle: String,
        senderName: String,
        text: String,
        messages: List<Pair<String, String>> = emptyList(), // (sender, text) история
        isMuted: Boolean = false
    ) {
        val notifId = chatId.hashCode()

        // Быстрый ответ
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Ответить...")
            .build()

        val replyPendingIntent = PendingIntent.getBroadcast(
            ctx, notifId,
            Intent(ACTION_REPLY).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_NOTIF_ID, notifId)
                setPackage(ctx.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val markReadIntent = PendingIntent.getBroadcast(
            ctx, notifId + 1,
            Intent(ACTION_MARK_READ).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_NOTIF_ID, notifId)
                setPackage(ctx.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification, "Ответить", replyPendingIntent
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()

        // Messaging style (iOS/Android лучшие практики)
        val me = Person.Builder().setName("Вы").setKey("me").build()
        val sender = Person.Builder().setName(senderName).setKey(senderName).build()

        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if (messages.size > 1) chatTitle else null)
            .setGroupConversation(messages.size > 1)

        // Добавляем историю сообщений (до 5)
        messages.takeLast(5).forEach { (s, t) ->
            val p = if (s == "me") me else Person.Builder().setName(s).setKey(s).build()
            messagingStyle.addMessage(NotificationCompat.MessagingStyle.Message(t, System.currentTimeMillis(), p))
        }
        // Текущее
        messagingStyle.addMessage(NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), sender))

        val notif = NotificationCompat.Builder(ctx, if (isMuted) CHANNEL_SILENT else CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF8CBF26.toInt())
            .setStyle(messagingStyle)
            .setPriority(if (isMuted) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .addAction(replyAction)
            .addAction(NotificationCompat.Action(0, "Прочитано", markReadIntent))
            .setGroup("maxx_messages_$chatId")
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify(notifId, notif)
        }
    }

    fun cancelNotification(ctx: Context, chatId: Long) {
        NotificationManagerCompat.from(ctx).cancel(chatId.hashCode())
    }
}
