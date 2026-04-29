package ru.maxx.app.ui.screens.auth

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.launch
import ru.maxx.app.core.spoofing.SpoofProfile
import ru.maxx.app.core.spoofing.SpoofingManager
import ru.maxx.app.ui.theme.*

// Пресеты устройств (аналог device_presets.dart из Komet)
private data class DevicePreset(
    val deviceName: String,
    val osVersion: String,
    val screen: String,
    val arch: String = "arm64-v8a",
    val buildNumber: Int = 6686,
    val appVersion: String = "26.14.1"
)

private val DEVICE_PRESETS = listOf(
    DevicePreset("Samsung Galaxy S24 Ultra", "Android 14", "xxhdpi 480dpi 1440x3088"),
    DevicePreset("Samsung Galaxy S23 Ultra", "Android 14", "xxhdpi 480dpi 1440x3088"),
    DevicePreset("Samsung Galaxy S22",       "Android 13", "xxhdpi 480dpi 1080x2340"),
    DevicePreset("Xiaomi 14 Ultra",          "Android 14", "xxhdpi 480dpi 1440x3200"),
    DevicePreset("Xiaomi 13 Pro",            "Android 13", "xxhdpi 480dpi 1440x3200"),
    DevicePreset("Google Pixel 8 Pro",       "Android 14", "xxhdpi 480dpi 1344x2992"),
    DevicePreset("Google Pixel 7",           "Android 13", "xxhdpi 420dpi 1080x2400"),
    DevicePreset("OnePlus 12",               "Android 14", "xxhdpi 480dpi 1440x3168"),
    DevicePreset("OnePlus 11",               "Android 13", "xxhdpi 450dpi 1440x3216"),
    DevicePreset("HONOR Magic6 Pro",         "Android 14", "xxhdpi 480dpi 1312x2848"),
    DevicePreset("Realme GT5 Pro",           "Android 14", "xxhdpi 480dpi 1440x3168"),
    DevicePreset("OPPO Find X7 Ultra",       "Android 14", "xxhdpi 480dpi 1440x3168"),
    DevicePreset("Vivo X100 Pro",            "Android 14", "xxhdpi 480dpi 1260x2800"),
)

private fun genId(): String = ByteArray(8)
    .also { Random.Default.nextBytes(it) }
    .joinToString("") { "%02x".format(it) }

@Composable
fun SpoofSetupScreen(
    spoofing: SpoofingManager,
    onApplied: () -> Unit
) {
    val context = LocalContext.current

    // Поля
    var deviceName   by remember { mutableStateOf("") }
    var osVersion    by remember { mutableStateOf("") }
    var screen       by remember { mutableStateOf("") }
    var timezone     by remember { mutableStateOf("Europe/Moscow") }
    var locale       by remember { mutableStateOf("ru") }
    var deviceId     by remember { mutableStateOf(genId()) }
    var appVersion   by remember { mutableStateOf("26.14.1") }
    var buildNumber  by remember { mutableStateOf("6686") }
    var arch         by remember { mutableStateOf("arm64-v8a") }

    var showRestartDialog by remember { mutableStateOf(false) }

    // При открытии — сразу генерируем случайный пресет
    LaunchedEffect(Unit) {
        val preset = DEVICE_PRESETS.random()
        deviceName  = preset.deviceName
        osVersion   = preset.osVersion
        screen      = preset.screen
        arch        = preset.arch
        appVersion  = preset.appVersion
        buildNumber = preset.buildNumber.toString()
        deviceId    = genId()
    }

    fun randomize() {
        val preset = DEVICE_PRESETS.random()
        deviceName  = preset.deviceName
        osVersion   = preset.osVersion
        screen      = preset.screen
        arch        = preset.arch
        appVersion  = preset.appVersion
        buildNumber = preset.buildNumber.toString()
        deviceId    = genId()
    }

    suspend fun save() {
        val profile = SpoofProfile(
            deviceId    = deviceId,
            deviceName  = deviceName,
            osVersion   = osVersion,
            appVersion  = appVersion,
            screen      = screen,
            timezone    = timezone,
            locale      = locale,
            arch        = arch,
            buildNumber = buildNumber.toIntOrNull() ?: 6686
        )
        spoofing.saveWithSetupDone(profile)
    }

    // Диалог перезапуска
    if (showRestartDialog) {
        AlertDialog(
            containerColor = BgCard,
            title = { Text("Применить и перезапустить?", style = MaterialTheme.typography.titleMedium) },
            text  = { Text("Настройки спуфинга сохранены. Для применения требуется перезапуск приложения.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    restartApp(context)
                }) { Text("Перезапустить", color = Accent, fontWeight = FontWeight.Medium) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false; onApplied() }) {
                    Text("Пропустить", color = TextMuted)
                }
            }
        )
    }

    Scaffold(
        containerColor = BgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .background(BgSecondary).statusBarsPadding().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Security, null, tint = Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Настройки устройства", style = MaterialTheme.typography.titleSmall)
                        Text("Установите один раз перед входом", fontSize = 11.sp, color = TextMuted)
                    }
                }
                HorizontalDivider(color = Border, thickness = 0.5.dp)
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().background(BgSecondary)
                        .navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Кнопка "Сгенерировать"
                    OutlinedButton(
                        onClick = { randomize() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                    ) {
                        Icon(Icons.Outlined.Autorenew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Генерировать", fontSize = 13.sp)
                    }
                    // Кнопка "Применить"
                    val scope = rememberCoroutineScope()
                    Button(
                        onClick = {
                            scope.launch {
                                save()
                                showRestartDialog = true
                            }
                        },
                        modifier = Modifier.weight(1.4f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BgPrimary)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Применить", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad)
                .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Инфо баннер
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(AccentDark)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Info, null, tint = Accent, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Text(
                    "Эти данные передаются серверу как идентификатор вашего устройства. " +
                    "Используйте настройки для каждой новой сессии.",
                    fontSize = 12.sp, color = Accent, lineHeight = 17.sp
                )
            }

            // Блок: Устройство
            SpoofCard(title = "Устройство") {
                SpoofField("Название устройства", Icons.Outlined.PhoneAndroid, deviceName) { deviceName = it }
                SpoofField("Версия ОС", Icons.Outlined.Android, osVersion) { osVersion = it }
                SpoofField("Экран", Icons.Outlined.AspectRatio, screen) { screen = it }

                // Архитектура — dropdown
                SpoofDropdown(
                    label = "Архитектура процессора",
                    selected = arch,
                    options = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"),
                    onSelect = { arch = it }
                )
            }

            // Блок: Приложение
            SpoofCard(title = "Приложение") {
                SpoofField("Версия MAX", Icons.Outlined.Apps, appVersion) { appVersion = it }
                SpoofField("Build Number", Icons.Outlined.Tag, buildNumber,
                    keyboard = KeyboardType.Number) { buildNumber = it }
            }

            // Блок: Идентификаторы
            SpoofCard(title = "Идентификаторы") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        SpoofField("Device ID", Icons.Outlined.Fingerprint, deviceId) { deviceId = it }
                    }
                    IconButton(onClick = { deviceId = genId() }) {
                        Icon(Icons.Outlined.Refresh, null, tint = Accent)
                    }
                }
            }

            // Блок: Локализация
            SpoofCard(title = "Локализация") {
                SpoofField("Часовой пояс", Icons.Outlined.Schedule, timezone) { timezone = it }
                SpoofField("Локаль", Icons.Outlined.Language, locale) { locale = it }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SpoofCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(BgCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp))
        content()
    }
}

@Composable
private fun SpoofField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = BgSecondary, unfocusedContainerColor = BgSecondary,
            focusedBorderColor      = Accent,       unfocusedBorderColor    = Border,
            focusedTextColor        = TextPrimary,  unfocusedTextColor      = TextPrimary,
            cursorColor             = Accent,
            focusedLabelColor       = Accent,       unfocusedLabelColor     = TextHint
        )
    )
}

@Composable
private fun SpoofDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Outlined.Memory, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    null, tint = TextMuted,
                    modifier = Modifier.clickable { expanded = true }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = BgSecondary, unfocusedContainerColor = BgSecondary,
                focusedBorderColor      = Accent,       unfocusedBorderColor    = Border,
                focusedTextColor        = TextPrimary,  unfocusedTextColor      = TextPrimary,
                focusedLabelColor       = Accent,       unfocusedLabelColor     = TextHint
            )
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = BgCard
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 13.sp, color = if (opt == selected) Accent else TextPrimary) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

private fun restartApp(context: Context) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.clearApplicationUserData()
}
