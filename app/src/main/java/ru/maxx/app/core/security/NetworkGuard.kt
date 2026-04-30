package ru.maxx.app.core.security

import android.util.Log
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import java.net.InetAddress
import java.net.Socket

object NetworkGuard {
    private const val TAG = "NetworkGuard"

    val ALLOWED: Set<String> = setOf(
        "api.oneme.ru", "ws-api.oneme.ru", "i.oneme.ru"
    )

    private val BLOCKED: Set<String> = setOf(
        // ── IP-детекция ────────────────────────────────────────────────────────────
        "api.ipify.org","checkip.amazonaws.com","ifconfig.me","ifconfig.co",
        "ip.mail.ru","ipinfo.io","icanhazip.com","ipecho.net",
        "myexternalip.com","ip4.seeip.org","api64.ipify.org",
        "ipapi.co","ip-api.com","wtfismyip.com","httpbin.org",
        "ident.me","wgetip.com","whatismyip.akamai.com",

        // ── VPN/цензура детект (ТСПУ) ─────────────────────────────────────────────
        "main.telegram.org","telegram.org","t.me",
        "mtalk.google.com","gstatic.com","gosuslugi.ru","liveinternet.ru",
        "connectivitycheck.gstatic.com","connectivitycheck.android.com",

        // ── Мессенджеры-конкуренты ────────────────────────────────────────────────
        "mmg.whatsapp.net","whatsapp.com","wa.me",
        "instagram.com","graph.instagram.com","cdninstagram.com",
        "web.telegram.org","telega.io",
        "wechat.com","weixin.qq.com",
        "viber.com","viber.net",
        "signal.org",
        "line.me","line-scdn.net",
        "discord.com","discordapp.com","discordcdn.com",
        "skype.com","skype.net",

        // ── OpenAI / AI сервисы ───────────────────────────────────────────────────
        "api.openai.com","openai.com","chat.openai.com","chatgpt.com",
        "oaidalleapiprodscus.blob.core.windows.net",
        "anthropic.com","claude.ai",
        "bard.google.com","gemini.google.com",
        "copilot.microsoft.com","bing.com",
        "d2verb.ai","api.d2verb.ai","dumate.ru","ai.max.ru",
        "gigachat.ru","giga.chat",
        "yandexgpt.ru","300.ya.ru",

        // ── Аналитика / телеметрия ────────────────────────────────────────────────
        "analytics.google.com","firebase.googleapis.com",
        "firebaselogging.googleapis.com","firebaseinstallations.googleapis.com",
        "firebaseremoteconfig.googleapis.com","fcm.googleapis.com",
        "crashlytics.com","sentry.io","amplitude.com","mixpanel.com",
        "appmetrica.yandex.com","mc.yandex.ru","metrika.yandex.ru",
        "adjust.com","appsflyer.com","branch.io","kochava.com",
        "flurry.com","countly.com","segment.io","heap.io",
        "newrelic.com","datadog.com","instabug.com",
        "bugsnag.com","rollbar.com","raygun.io",
        "hotjar.com","fullstory.com","logrocket.com",

        // ── VK/OK/Rutube экосистема ───────────────────────────────────────────────
        "vk.com","vk.ru","ok.ru","mail.ru","mycdn.me","userapi.com","vk-apps.com",
        "rutube.ru","vkvideo.ru","imgsmail.ru","vkontakte.ru",
        "backapi.rustore.ru","wl.liarts.ru","rustore.ru",
        "sferum.ru","st.max.ru","web.max.ru","max.ru","legal.max.ru",

        // ── Реклама ───────────────────────────────────────────────────────────────
        "doubleclick.net","googlesyndication.com","googletagmanager.com",
        "googletagservices.com","adservice.google.com",
        "yandex.ru","an.yandex.ru","awaps.yandex.net",
        "pagead2.googlesyndication.com","adnxs.com","criteo.com",
        "moatads.com","scorecardresearch.com","quantserve.com",
        "taboola.com","outbrain.com",

        // ── Социальные сети ───────────────────────────────────────────────────────
        "facebook.com","fbcdn.net","connect.facebook.net",
        "twitter.com","twimg.com","t.co",
        "linkedin.com","licdn.com",
        "tiktok.com","tiktokcdn.com","bytedance.com",
        "youtube.com","youtu.be",
        "pinterest.com",
        "snapchat.com",
        "reddit.com","redd.it",

        // ── Прочее ────────────────────────────────────────────────────────────────
        "calls.okcdn.ru","zoom.us","teams.microsoft.com"
    )

    // FIX: правильная проверка поддомена — h.endsWith(".$it"), не h.endsWith(".")
    fun isAllowed(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (ALLOWED.any { h == it || h.endsWith(".$it") }) return true
        if (BLOCKED.any { h == it || h.endsWith(".$it") }) {
            Log.w(TAG, "BLOCKED: $h")   // FIX: переменная h, не пустая строка
            return false
        }
        Log.w(TAG, "UNKNOWN blocked: $h")
        return false
    }

    val hostnameVerifier = HostnameVerifier { hostname: String, _: SSLSession ->
        isAllowed(hostname)
    }

    val guardedSocketFactory = object : SocketFactory() {
        private val d = getDefault()
        private fun assertAllowed(host: String) {
            if (!isAllowed(host)) throw SecurityException("NetworkGuard: blocked $host")  // FIX: $host
        }
        override fun createSocket(): Socket = d.createSocket()
        override fun createSocket(host: String, port: Int): Socket {
            assertAllowed(host); return d.createSocket(host, port)
        }
        override fun createSocket(host: String, port: Int, la: InetAddress, lp: Int): Socket {
            assertAllowed(host); return d.createSocket(host, port, la, lp)
        }
        override fun createSocket(h: InetAddress, p: Int): Socket = d.createSocket(h, p)
        override fun createSocket(a: InetAddress, p: Int, la: InetAddress, lp: Int): Socket =
            d.createSocket(a, p, la, lp)
    }
}
