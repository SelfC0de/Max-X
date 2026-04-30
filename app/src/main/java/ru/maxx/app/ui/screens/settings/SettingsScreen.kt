package ru.maxx.app.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.maxx.app.data.prefs.AppPrefs
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.*
import androidx.compose.material.icons.outlined.Check
import ru.maxx.app.ui.theme.*

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onSpoofSetup: () -> Unit = {}) {
    var biometric  by remember { mutableStateOf(container.appPrefs.biometricEnabled) }
    var passLock   by remember { mutableStateOf(container.appPrefs.passLockEnabled) }
    var phoneVis   by remember { mutableStateOf(container.appPrefs.phoneVisibility) }
    var onlineVis  by remember { mutableStateOf(container.appPrefs.onlineVisibility) }
    var proxyEn    by remember { mutableStateOf(container.appPrefs.proxyEnabled) }
    var proxyHost  by remember { mutableStateOf(container.appPrefs.proxyHost) }
    var proxyPort  by remember { mutableStateOf(container.appPrefs.proxyPort.toString()) }
    var notifV     by remember { mutableStateOf(container.appPrefs.notifVibro) }


    Scaffold(containerColor = BgPrimary, topBar = { MaxXTopBar("Настройки", onBack = onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {

            // Приватность
            var showPhonePicker  by remember { mutableStateOf(false) }
            var showOnlinePicker by remember { mutableStateOf(false) }

            if (showPhonePicker) {
                VisibilityDialog(
                    title   = "Кто видит номер телефона",
                    current = phoneVis,
                    onSelect = { container.appPrefs.phoneVisibility = it; phoneVis = it; showPhonePicker = false },
                    onDismiss = { showPhonePicker = false }
                )
            }
            if (showOnlinePicker) {
                VisibilityDialog(
                    title   = "Кто видит статус «в сети»",
                    current = onlineVis,
                    onSelect = { container.appPrefs.onlineVisibility = it; onlineVis = it; showOnlinePicker = false },
                    onDismiss = { showOnlinePicker = false }
                )
            }

            ExpandableCard(
                title = "Приватность",
                icon = Icons.Filled.Shield, iconBg = AccentDark, iconColor = Accent
            ) {
                SettingsToggle("Номер телефона", visLabel(phoneVis)) { showPhonePicker = true }
                SettingsToggle("Статус «в сети»", visLabel(onlineVis)) { showOnlinePicker = true }
                SettingsSwitch("Ссылка при пересылке", false) { }
                SettingsSwitch(
                    "Скрыть статус «печатает»",
                    container.appPrefs.hideTypingStatus
                ) { container.appPrefs.hideTypingStatus = it }
                SettingsSwitch(
                    "Автоматически отмечать прочитанным",
                    container.appPrefs.autoMarkRead
                ) { container.appPrefs.autoMarkRead = it }
            }

            Spacer(Modifier.height(8.dp))

            // Безопасность
            ExpandableCard("Безопасность", Icons.Filled.Lock, PurpleDark, Purple) {
                SettingsSwitchRow("Пароль входа", "Дополнительная защита", passLock) { container.appPrefs.passLockEnabled = it }
                SettingsSwitchRow("Биометрия", "Отпечаток / Face ID", biometric) { container.appPrefs.biometricEnabled = it }
                SettingsToggle("Активные сессии", "2 устройства") {}
            }

            Spacer(Modifier.height(8.dp))

            // Сеть и прокси
            ExpandableCard("Сеть и прокси", Icons.Outlined.Wifi, BlueDark, Blue) {
                SettingsSwitchRow("SOCKS5 прокси", if (proxyEn) "$proxyHost:$proxyPort" else "Выключен", proxyEn) { container.appPrefs.proxyEnabled = it }
                if (proxyEn) {
                    var hostEdit by remember { mutableStateOf(proxyHost) }
                    OutlinedTextField(
                        value = hostEdit, onValueChange = { hostEdit = it; container.appPrefs.proxyHost = it },
                        placeholder = { Text("127.0.0.1", color = TextHint, fontSize = 12.sp) },
                        label = { Text("Адрес прокси", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                            focusedLabelColor = Accent, unfocusedLabelColor = TextMuted
                        )
                    )
                }
                SettingsSwitchRow("Спуфинг сессии", "Маскировка под официальный клиент", container.appPrefs.spoofEnabled) { container.appPrefs.spoofEnabled = it }
                SettingsInfo("Все запросы только к api.oneme.ru · Телеметрия отсутствует")
            }

            Spacer(Modifier.height(8.dp))

            // Уведомления
            ExpandableCard("Уведомления", Icons.Outlined.Notifications, Color(0xFF2E1A0D), Orange) {
                SettingsSwitchRow("Личные чаты", null, container.appPrefs.notifPersonal) { container.appPrefs.notifPersonal = it }
                SettingsSwitchRow("Группы", null, container.appPrefs.notifGroups) { container.appPrefs.notifGroups = it }
                SettingsSwitchRow("Каналы", null, container.appPrefs.notifChannels) { container.appPrefs.notifChannels = it }
                SettingsSwitchRow("Звук", null, container.appPrefs.notifSound) { container.appPrefs.notifSound = it }
                SettingsSwitchRow("Вибрация", null, notifV, showDivider = false) { container.appPrefs.notifVibro = it }
            }

            Spacer(Modifier.height(8.dp))



            // Прокси
            var showProxyDialog by remember { mutableStateOf(false) }
            var proxyUser by remember { mutableStateOf(container.appPrefs.proxyUser) }
            var proxyPass by remember { mutableStateOf(container.appPrefs.proxyPass) }

            if (showProxyDialog) {
                AlertDialog(
                    onDismissRequest = { showProxyDialog = false },
                    containerColor   = BgCard,
                    title = { Text("SOCKS5 прокси", style = MaterialTheme.typography.titleSmall) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Хост" to proxyHost, "Порт" to proxyPort,
                                "Логин (необязательно)" to proxyUser, "Пароль (необязательно)" to proxyPass
                            ).forEachIndexed { i, (label, value) ->
                                OutlinedTextField(
                                    value = when(i) { 0->proxyHost; 1->proxyPort; 2->proxyUser; else->proxyPass },
                                    onValueChange = { v -> when(i) { 0->proxyHost=v; 1->proxyPort=v; 2->proxyUser=v; else->proxyPass=v } },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp), singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = BgSecondary, unfocusedContainerColor = BgSecondary,
                                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                        cursorColor = Accent, focusedLabelColor = Accent, unfocusedLabelColor = TextHint
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            container.appPrefs.proxyHost = proxyHost
                            container.appPrefs.proxyPort = proxyPort.toIntOrNull() ?: 1080
                            container.appPrefs.proxyUser = proxyUser
                            container.appPrefs.proxyPass = proxyPass
                            showProxyDialog = false
                        }) { Text("Сохранить", color = Accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showProxyDialog = false }) { Text("Отмена", color = TextMuted) }
                    }
                )
            }

            ExpandableCard("Прокси", Icons.Outlined.VpnLock, BgTertiary, TextSecondary) {
                SettingsSwitch("SOCKS5 прокси", proxyEnabled) {
                    proxyEnabled = it; container.appPrefs.proxyEnabled = it
                }
                SettingsRow("Настройки прокси",
                    subtitle = if (proxyHost.isNotEmpty()) "${proxyHost}:${proxyPort}" else "Не настроен",
                    icon = Icons.Outlined.Settings, iconBgColor = BgTertiary, iconColor = TextSecondary,
                    onClick = { showProxyDialog = true }, showDivider = false)
            }

            Spacer(Modifier.height(8.dp))

            // Множественные аккаунты
            val accounts = remember { container.appPrefs.getAccounts() }
            if (accounts.isNotEmpty()) {
                ExpandableCard("Аккаунты", Icons.Outlined.ManageAccounts, BgTertiary, TextSecondary) {
                    accounts.forEach { acc ->
                        SettingsRow(
                            title = acc.phone.ifEmpty { "Аккаунт ${acc.userId}" },
                            subtitle = "userId: ${acc.userId}",
                            icon = Icons.Outlined.AccountCircle, iconBgColor = BgTertiary, iconColor = TextSecondary,
                            onClick = {
                                container.authPrefs.setToken(acc.token)
                                container.authPrefs.setUserId(acc.userId)
                            },
                            showDivider = accounts.last() != acc
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Настройки устройства
            ExpandableCard("Устройство", Icons.Outlined.PhoneAndroid, BlueDark, Blue) {
                SettingsRow("Параметры спуфинга", subtitle = "Изменить профиль устройства",
                    icon = Icons.Outlined.Security, iconBgColor = BlueDark, iconColor = Blue,
                    onClick = { onSpoofSetup() }, showDivider = false)
            }

            Spacer(Modifier.height(8.dp))

            // Сессия
            var showTokenDialog  by remember { mutableStateOf(false) }
            var showImportDialog by remember { mutableStateOf(false) }
            var importTokenText  by remember { mutableStateOf("") }
            val currentToken = container.authPrefs.getToken() ?: ""

            if (showTokenDialog) {
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                AlertDialog(
                    onDismissRequest = { showTokenDialog = false },
                    containerColor   = BgCard,
                    title = { Text("Текущий токен", style = MaterialTheme.typography.titleSmall) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Токен сессии:", fontSize = 11.sp, color = TextMuted)
                            Text(
                                currentToken,
                                fontSize = 10.sp, color = TextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BgSecondary, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                maxLines = 4,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentToken))
                            showTokenDialog = false
                        }) { Text("Скопировать", color = Accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTokenDialog = false }) { Text("Закрыть", color = TextMuted) }
                    }
                )
            }

            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    containerColor   = BgCard,
                    title = { Text("Импорт токена", style = MaterialTheme.typography.titleSmall) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Вставьте токен сессии:", fontSize = 11.sp, color = TextMuted)
                            OutlinedTextField(
                                value = importTokenText,
                                onValueChange = { importTokenText = it },
                                placeholder = { Text("An_Sx6HQ...", color = TextHint, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                minLines = 3, maxLines = 5,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor   = BgSecondary, unfocusedContainerColor = BgSecondary,
                                    focusedBorderColor      = Accent,       unfocusedBorderColor    = Border,
                                    focusedTextColor        = TextPrimary,  unfocusedTextColor      = TextPrimary,
                                    cursorColor             = Accent,
                                    focusedLabelColor       = Accent,       unfocusedLabelColor     = TextHint
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = importTokenText.length > 20,
                            onClick = {
                                container.authPrefs.setToken(importTokenText.trim())
                                showImportDialog = false
                                importTokenText  = ""
                            }
                        ) { Text("Применить", color = if (importTokenText.length > 20) Accent else TextMuted) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false; importTokenText = "" }) {
                            Text("Отмена", color = TextMuted)
                        }
                    }
                )
            }

            ExpandableCard("Сессия", Icons.Outlined.VpnKey, BgTertiary, TextSecondary) {
                SettingsRow("Экспорт токена", subtitle = "Скопировать токен текущей сессии",
                    icon = Icons.Outlined.ContentCopy, iconBgColor = BgTertiary, iconColor = TextSecondary,
                    onClick = { showTokenDialog = true })
                SettingsRow("Импорт токена", subtitle = "Войти с готовым токеном",
                    icon = Icons.Outlined.Login, iconBgColor = BgTertiary, iconColor = TextSecondary,
                    onClick = { showImportDialog = true }, showDivider = false)
            }

            Spacer(Modifier.height(8.dp))

            // О приложении
            ExpandableCard("О приложении", Icons.Outlined.Info, BgTertiary, TextMuted) {
                SettingsInfo2("Версия", "1.0.0")
                SettingsInfo2("Протокол", "oneme v11")
                SettingsInfo2("Телеметрия", "Полностью отсутствует")
                SettingsInfo2("Исходящие соединения", "api.oneme.ru:443 only", showDivider = false)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExpandableCard(
    title: String, icon: ImageVector, iconBg: Color, iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BgCard)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = TextMuted, modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit  = shrinkVertically(tween(150)) + fadeOut(tween(150))
        ) {
            Column {
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String?, checked: Boolean, showDivider: Boolean = true, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 1.dp))
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = BgSecondary, checkedTrackColor = Accent,
                uncheckedThumbColor = TextMuted, uncheckedTrackColor = BgTertiary)
        )
    }
    if (showDivider) HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 14.dp))
}

@Composable
private fun SettingsSwitch(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    SettingsSwitchRow(title, null, checked, onToggle = onToggle)
}

@Composable
private fun SettingsToggle(title: String, value: String, showDivider: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Accent)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, tint = TextHint, modifier = Modifier.size(18.dp))
    }
    if (showDivider) HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 14.dp))
}

@Composable
private fun SettingsInfo(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextHint,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
private fun SettingsInfo2(label: String, value: String, showDivider: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
    if (showDivider) HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 14.dp))
}

private fun visLabel(v: String) = when (v) {
    "everyone" -> "Все"; "contacts" -> "Контакты"; "nobody" -> "Никто"; else -> v
}

@Composable
private fun VisibilityDialog(
    title: String,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("Все", "Мои контакты", "Никто")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                options.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(opt) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(opt, color = if (opt == current) Accent else TextPrimary,
                            style = MaterialTheme.typography.bodyMedium)
                        if (opt == current) Icon(Icons.Outlined.Check, null, tint = Accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {}
    )
}
