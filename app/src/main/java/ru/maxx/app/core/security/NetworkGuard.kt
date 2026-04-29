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
        "api.ipify.org","checkip.amazonaws.com","ifconfig.me","ifconfig.co",
        "ip.mail.ru","ipinfo.io","icanhazip.com","ipecho.net",
        "myexternalip.com","ip4.seeip.org","api64.ipify.org",
        "main.telegram.org","mmg.whatsapp.net","mtalk.google.com",
        "gstatic.com","gosuslugi.ru","liveinternet.ru",
        "analytics.google.com","firebase.googleapis.com",
        "firebaselogging.googleapis.com","firebaseinstallations.googleapis.com",
        "crashlytics.com","sentry.io","amplitude.com","mixpanel.com",
        "appmetrica.yandex.com","mc.yandex.ru",
        "d2verb.ai","api.d2verb.ai","dumate.ru","ai.max.ru",
        "calls.okcdn.ru","st.max.ru","web.max.ru","max.ru",
        "vk.com","vk.ru","ok.ru","mail.ru","mycdn.me","userapi.com","vk-apps.com",
        "backapi.rustore.ru","wl.liarts.ru","rustore.ru",
        "legal.max.ru","sferum.ru"
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
