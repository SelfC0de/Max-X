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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.maxx.app.data.prefs.AppPrefs
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.*
import androidx.compose.material.icons.outlined.Check
import ru.maxx.app.ui.theme.*

class SettingsViewModel(private val prefs: AppPrefs) : ViewModel() {
    val biometric   = prefs.biometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val passLock    = prefs.passLockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val proxyEn     = prefs.proxyEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val proxyHost   = prefs.proxyHost.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val proxyPort   = prefs.proxyPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1080)
    val notifPersonal = prefs.notifPersonal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notifGroups   = prefs.notifGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notifChannels = prefs.notifChannels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val notifSound    = prefs.notifSound.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notifVibro    = prefs.notifVibro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val spoof         = prefs.spoofEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val phoneVis      = prefs.phoneVisibility.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "nobody")
    val onlineVis     = prefs.onlineVisibility.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "everyone")

    fun setBiometric(v: Boolean)     = viewModelScope.launch { prefs.setBiometric(v) }
    fun setPassLock(v: Boolean)      = viewModelScope.launch { prefs.setPassLock(v) }
    fun setProxyEnabled(v: Boolean)  = viewModelScope.launch { prefs.setProxyEnabled(v) }
    fun setProxyHost(v: String)      = viewModelScope.launch { prefs.setProxyHost(v) }
    fun setProxyPort(v: Int)         = viewModelScope.launch { prefs.setProxyPort(v) }
    fun setNotifPersonal(v: Boolean) = viewModelScope.launch { prefs.setNotifPersonal(v) }
    fun setNotifGroups(v: Boolean)   = viewModelScope.launch { prefs.setNotifGroups(v) }
    fun setNotifChannels(v: Boolean) = viewModelScope.launch { prefs.setNotifChannels(v) }
    fun setNotifSound(v: Boolean)    = viewModelScope.launch { prefs.setNotifSound(v) }
    fun setNotifVibro(v: Boolean)    = viewModelScope.launch { prefs.setNotifVibro(v) }
    fun setSpoof(v: Boolean)         = viewModelScope.launch { prefs.setSpoofEnabled(v) }
    fun setPhoneVis(v: String)       = viewModelScope.launch { prefs.setPhoneVisibility(v) }
    fun setOnlineVis(v: String)      = viewModelScope.launch { prefs.setOnlineVisibility(v) }
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = remember { SettingsViewModel(container.appPrefs) }

    val biometric   by vm.biometric.collectAsState()
    val passLock    by vm.passLock.collectAsState()
    val proxyEn     by vm.proxyEn.collectAsState()
    val proxyHost   by vm.proxyHost.collectAsState()
    val proxyPort   by vm.proxyPort.collectAsState()
    val notifP      by vm.notifPersonal.collectAsState()
    val notifG      by vm.notifGroups.collectAsState()
    val notifCh     by vm.notifChannels.collectAsState()
    val notifS      by vm.notifSound.collectAsState()
    val notifV      by vm.notifVibro.collectAsState()
    val spoof       by vm.spoof.collectAsState()
    val phoneVis    by vm.phoneVis.collectAsState()
    val onlineVis   by vm.onlineVis.collectAsState()

    Scaffold(containerColor = BgPrimary, topBar = { MaxXTopBar("Настройки", onBack = onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {

            // Приватность
            var showPhonePicker  by remember { mutableStateOf(false) }
            var showOnlinePicker by remember { mutableStateOf(false) }

            if (showPhonePicker) {
                VisibilityDialog(
                    title   = "Кто видит номер телефона",
                    current = phoneVis,
                    onSelect = { vm.setPhoneVis(it); showPhonePicker = false },
                    onDismiss = { showPhonePicker = false }
                )
            }
            if (showOnlinePicker) {
                VisibilityDialog(
                    title   = "Кто видит статус «в сети»",
                    current = onlineVis,
                    onSelect = { vm.setOnlineVis(it); showOnlinePicker = false },
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
            }

            Spacer(Modifier.height(8.dp))

            // Безопасность
            ExpandableCard("Безопасность", Icons.Filled.Lock, PurpleDark, Purple) {
                SettingsSwitchRow("Пароль входа", "Дополнительная защита", passLock) { vm.setPassLock(it) }
                SettingsSwitchRow("Биометрия", "Отпечаток / Face ID", biometric) { vm.setBiometric(it) }
                SettingsToggle("Активные сессии", "2 устройства") {}
            }

            Spacer(Modifier.height(8.dp))

            // Сеть и прокси
            ExpandableCard("Сеть и прокси", Icons.Outlined.Wifi, BlueDark, Blue) {
                SettingsSwitchRow("SOCKS5 прокси", if (proxyEn) "$proxyHost:$proxyPort" else "Выключен", proxyEn) { vm.setProxyEnabled(it) }
                if (proxyEn) {
                    var hostEdit by remember { mutableStateOf(proxyHost) }
                    OutlinedTextField(
                        value = hostEdit, onValueChange = { hostEdit = it; vm.setProxyHost(it) },
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
                SettingsSwitchRow("Спуфинг сессии", "Маскировка под официальный клиент", spoof) { vm.setSpoof(it) }
                SettingsInfo("Все запросы только к api.oneme.ru · Телеметрия отсутствует")
            }

            Spacer(Modifier.height(8.dp))

            // Уведомления
            ExpandableCard("Уведомления", Icons.Outlined.Notifications, Color(0xFF2E1A0D), Orange) {
                SettingsSwitchRow("Личные чаты", null, notifP) { vm.setNotifPersonal(it) }
                SettingsSwitchRow("Группы", null, notifG) { vm.setNotifGroups(it) }
                SettingsSwitchRow("Каналы", null, notifCh) { vm.setNotifChannels(it) }
                SettingsSwitchRow("Звук", null, notifS) { vm.setNotifSound(it) }
                SettingsSwitchRow("Вибрация", null, notifV, showDivider = false) { vm.setNotifVibro(it) }
            }

            Spacer(Modifier.height(8.dp))

            // Внешний вид
            ExpandableCard("Внешний вид", Icons.Outlined.Palette, BgTertiary, TextSecondary) {
                SettingsToggle("Тема", "Тёмная") {}
                SettingsToggle("Размер шрифта", "Средний") {}
                SettingsToggle("Цвет акцента", "Зелёный #8CBF26", showDivider = false) {}
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
