package ru.maxx.app.core.spoofing

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlin.random.Random

private val Context.spoofStore by preferencesDataStore("spoof")

data class SpoofProfile(
    val deviceId: String, val deviceName: String, val osVersion: String,
    val appVersion: String, val screen: String, val timezone: String,
    val locale: String, val arch: String, val buildNumber: Int
)

class SpoofingManager(private val ctx: Context) {

    private val PRESETS = listOf(
        SpoofProfile("", "Samsung Galaxy S24 Ultra", "Android 14", "26.14.1", "xxhdpi 480dpi 1440x3088", "Europe/Moscow", "ru", "arm64-v8a", 6686),
        SpoofProfile("", "Samsung Galaxy S23 Ultra", "Android 14", "26.14.1", "xxhdpi 480dpi 1440x3088", "Europe/Moscow", "ru", "arm64-v8a", 6686),
        SpoofProfile("", "Xiaomi 14 Ultra",          "Android 14", "26.14.1", "xxhdpi 480dpi 1440x3200", "Europe/Moscow", "ru", "arm64-v8a", 6686),
        SpoofProfile("", "Google Pixel 8 Pro",        "Android 14", "26.14.1", "xxhdpi 480dpi 1344x2992", "Europe/Moscow", "ru", "arm64-v8a", 6686),
        SpoofProfile("", "OnePlus 12",                "Android 14", "26.14.1", "xxhdpi 480dpi 1440x3168", "Europe/Moscow", "ru", "arm64-v8a", 6686),
        SpoofProfile("", "HONOR Magic6 Pro",          "Android 14", "26.14.1", "xxhdpi 480dpi 1312x2848", "Europe/Moscow", "ru", "arm64-v8a", 6686),
    )

    private object K {
        val ENABLED = booleanPreferencesKey("enabled")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val OS_VER = stringPreferencesKey("os_ver")
        val APP_VER = stringPreferencesKey("app_ver")
        val SCREEN = stringPreferencesKey("screen")
        val TZ = stringPreferencesKey("tz")
        val LOCALE = stringPreferencesKey("locale")
        val ARCH = stringPreferencesKey("arch")
        val BUILD = intPreferencesKey("build")
    }

    suspend fun getOrGenerate(): SpoofProfile {
        val p = ctx.spoofStore.data.first()
        return if (p[K.ENABLED] == true) {
            // deviceId всегда генерируется заново при каждом подключении
            // чтобы не использовать deviceId другого пользователя
            val freshId = genId()
            ctx.spoofStore.edit { it[K.DEVICE_ID] = freshId }
            SpoofProfile(
                freshId, p[K.DEVICE_NAME] ?: PRESETS[0].deviceName,
                p[K.OS_VER] ?: PRESETS[0].osVersion, p[K.APP_VER] ?: PRESETS[0].appVersion,
                p[K.SCREEN] ?: PRESETS[0].screen, p[K.TZ] ?: PRESETS[0].timezone,
                p[K.LOCALE] ?: "ru", p[K.ARCH] ?: "arm64-v8a", p[K.BUILD] ?: 6686
            )
        } else generate()
    }

    suspend fun generate(): SpoofProfile {
        val preset = PRESETS[Random.nextInt(PRESETS.size)].copy(deviceId = genId())
        ctx.spoofStore.edit { p ->
            p[K.ENABLED] = true; p[K.DEVICE_ID] = preset.deviceId
            p[K.DEVICE_NAME] = preset.deviceName; p[K.OS_VER] = preset.osVersion
            p[K.APP_VER] = preset.appVersion; p[K.SCREEN] = preset.screen
            p[K.TZ] = preset.timezone; p[K.LOCALE] = preset.locale
            p[K.ARCH] = preset.arch; p[K.BUILD] = preset.buildNumber
        }
        return preset
    }

    fun isSetupDone(): Boolean = false  // проверка через DataStore — async, не используем здесь

    suspend fun saveWithSetupDone(profile: SpoofProfile) {
        ctx.spoofStore.edit { p ->
            p[K.SETUP_DONE] = true
            p[K.DEVICE_ID] = profile.deviceId; p[K.DEVICE_NAME] = profile.deviceName
            p[K.OS_VER] = profile.osVersion; p[K.APP_VER] = profile.appVersion
            p[K.SCREEN] = profile.screen; p[K.TZ] = profile.timezone
            p[K.LOCALE] = profile.locale; p[K.ARCH] = profile.arch
            p[K.BUILD] = profile.buildNumber; p[K.ENABLED] = true
        }
    }

    suspend fun save(profile: SpoofProfile) {
        ctx.spoofStore.edit { p ->
            p[K.ENABLED] = true; p[K.DEVICE_ID] = profile.deviceId
            p[K.DEVICE_NAME] = profile.deviceName; p[K.OS_VER] = profile.osVersion
            p[K.APP_VER] = profile.appVersion; p[K.SCREEN] = profile.screen
            p[K.TZ] = profile.timezone; p[K.LOCALE] = profile.locale
            p[K.ARCH] = profile.arch; p[K.BUILD] = profile.buildNumber
        }
    }

    fun toHandshakePayload(p: SpoofProfile): Map<String, Any?> = mapOf(
        "deviceType" to "ANDROID", "locale" to p.locale, "deviceLocale" to p.locale,
        "osVersion" to p.osVersion, "deviceName" to p.deviceName,
        "appVersion" to p.appVersion, "screen" to p.screen,
        "timezone" to p.timezone, "pushDeviceType" to "GCM",
        "arch" to p.arch, "buildNumber" to p.buildNumber
    )

    fun genId(): String = ByteArray(8).also { Random.nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
}
