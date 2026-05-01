package ru.maxx.app.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ru.maxx.app.ui.theme.*

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val _e2eEnabled = MutableStateFlow(false)
    val e2eEnabled: StateFlow<Boolean> = _e2eEnabled.asStateFlow()

    private val _e2ePublicKey = MutableStateFlow<String?>(null)
    val e2ePublicKey: StateFlow<String?> = _e2ePublicKey.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init { checkE2E() }

    private fun checkE2E() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                container.e2eCrypto.getOrCreateKeyPair()
                val pub = container.e2eCrypto.exportPublicKey()
                _e2ePublicKey.value = pub.take(24) + "..."
                _e2eEnabled.value = true
            }
        }
    }

    fun enableE2E() = viewModelScope.launch {
        runCatching {
            container.e2eCrypto.getOrCreateKeyPair()
            container.e2eRepo.publishPublicKey()
            _e2eEnabled.value = true
            _e2ePublicKey.value = container.e2eCrypto.exportPublicKey().take(24) + "..."
            _toast.value = "E2E шифрование активировано"
        }.onFailure { _toast.value = "Ошибка: ${it.message}" }
    }

    fun disableE2E() = viewModelScope.launch {
        container.e2eCrypto.deleteKeys()
        _e2eEnabled.value = false
        _e2ePublicKey.value = null
        _toast.value = "E2E ключи удалены"
    }

    fun clearToast() { _toast.value = null }
}

@Composable
fun ProfileScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
    onFavoritesClick: () -> Unit = {}
) {
    val vm = remember { ProfileViewModel(container) }
    val e2eEnabled  by vm.e2eEnabled.collectAsState()
    val e2eKey      by vm.e2ePublicKey.collectAsState()
    val toast       by vm.toast.collectAsState()
    val snackbar    = remember { SnackbarHostState() }

    LaunchedEffect(toast) {
        toast?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short); vm.clearToast() }
    }

    val userId    = container.authPrefs.getUserId() ?: "—"
    val userName  = container.authPrefs.getUserName() ?: "Пользователь"
    val userPhone = container.authPrefs.getUserPhone() ?: "+7 *** ***-**-**"
    val initials  = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

    var showEditDialog    by remember { mutableStateOf(false) }
    var showStatusDialog  by remember { mutableStateOf(false) }
    var showSessionsDialog by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var sessionsLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var editName by remember { mutableStateOf(userName) }
    var statusText by remember { mutableStateOf("") }

    // Диалог редактирования имени
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor   = BgCard,
            title = { Text("Редактировать профиль", style = MaterialTheme.typography.titleSmall) },
            text = {
                OutlinedTextField(
                    value = editName, onValueChange = { editName = it },
                    label = { Text("Имя", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BgSecondary, unfocusedContainerColor = BgSecondary,
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = Accent, focusedLabelColor = Accent, unfocusedLabelColor = TextHint
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    container.authPrefs.setUserName(editName)
                    showEditDialog = false
                }) { Text("Сохранить", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Отмена", color = TextMuted) }
            }
        )
    }

    // Диалог статуса
    if (showStatusDialog) {
        val statuses = listOf("" to "Нет статуса", "🌙 Не беспокоить" to "Не беспокоить",
            "💼 На работе" to "На работе", "🏠 Дома" to "Дома", "📵 Нет связи" to "Нет связи")
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            containerColor   = BgCard,
            title = { Text("Установить статус", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    statuses.forEach { (value, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { statusText = value; showStatusDialog = false }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = if (statusText == value) Accent else TextPrimary,
                                style = MaterialTheme.typography.bodyMedium)
                            if (statusText == value) Icon(Icons.Outlined.Check, null,
                                tint = Accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Диалог активных сессий
    if (showSessionsDialog) {
        AlertDialog(
            onDismissRequest = { showSessionsDialog = false },
            containerColor   = BgCard,
            title = { Text("Активные сессии", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionsLoading) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Accent, modifier = Modifier.size(24.dp))
                        }
                    } else if (sessions.isEmpty()) {
                        Text("Нет данных о сессиях", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    } else {
                        val currentId = container.authPrefs.getUserId()?.toString()
                        sessions.forEach { s ->
                            val sid      = s["sessionId"]?.toString() ?: s["id"]?.toString() ?: ""
                            // deviceType: MAX WEB / MAX Android / MAX iOS
                            val devType  = s["deviceType"]?.toString() ?: s["clientType"]?.toString() ?: ""
                            val devName  = s["deviceName"]?.toString() ?: s["device"]?.toString() ?: ""
                            val browser  = s["browser"]?.toString() ?: s["client"]?.toString() ?: ""
                            val os       = s["osVersion"]?.toString() ?: s["os"]?.toString() ?: ""
                            val app      = s["appVersion"]?.toString() ?: ""
                            val ip       = s["ip"]?.toString() ?: s["ipAddress"]?.toString() ?: ""
                            val country  = s["country"]?.toString() ?: s["countryName"]?.toString() ?: ""
                            val city     = s["city"]?.toString() ?: ""
                            // Название: "MAX WEB" / "MAX Android" / "MAX iOS"
                            val label = when {
                                devType.contains("WEB", ignoreCase = true)     -> "MAX WEB"
                                devType.contains("ANDROID", ignoreCase = true) -> "MAX Android"
                                devType.contains("IOS", ignoreCase = true)     -> "MAX iOS"
                                devType.isNotEmpty() -> "MAX ${devType.lowercase().replaceFirstChar { it.uppercaseChar() }}"
                                devName.isNotEmpty() -> devName
                                else -> "Устройство"
                            }
                            // Подзаголовок: "Chrome, Windows" / "iPhone 15 Pro Max, iOS 17"
                            val line1 = listOfNotNull(
                                browser.ifEmpty { null },
                                devName.ifEmpty { null },
                                os.ifEmpty { null }
                            ).distinct().joinToString(", ")
                            // Гео: "Finland, Uusimaa, IP 94.237.113.76"
                            val line2 = listOfNotNull(
                                country.ifEmpty { null },
                                city.ifEmpty { null },
                                if (ip.isNotEmpty()) "IP $ip" else null
                            ).joinToString(", ")

                            val lastSeen = s["lastActivity"]?.toString() ?: s["lastSeen"]?.toString() ?: s["seen"]?.toString() ?: ""
                            val lastSeenFmt = if (lastSeen.isNotEmpty() && lastSeen.all { it.isDigit() }) {
                                val ms = lastSeen.toLong() * if (lastSeen.length <= 10) 1000L else 1L
                                val cal = java.util.Calendar.getInstance().also { it.timeInMillis = ms }
                                String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                            } else lastSeen

                            val isCurr = s["current"] as? Boolean ?: s["isCurrent"] as? Boolean ?: false
                            val isOnline = s["online"] as? Boolean ?: s["status"] == 1 ?: false

                            SessionItemFull(
                                label      = label,
                                line1      = line1,
                                line2      = line2,
                                lastSeen   = lastSeenFmt,
                                isCurrent  = isCurr,
                                isOnline   = isOnline,
                                onTerminate = if (!isCurr) ({
                                    scope.launch {
                                        container.session.terminateSession(sid)
                                        sessions = container.session.loadSessions()
                                    }
                                }) else null
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSessionsDialog = false }) { Text("Закрыть", color = TextMuted) }
            }
        )
    }

    Scaffold(
        containerColor = BgPrimary,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MaxXTopBar("Профиль", actions = {

            })
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {

            // Аватар блок
            Column(
                modifier = Modifier.fillMaxWidth().background(BgSecondary).padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Box(
                        modifier = Modifier.size(82.dp).clip(CircleShape)
                            .background(AccentDark).border(2.dp, Accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(initials.ifEmpty { "?" }, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Accent) }
                    // E2E значок
                    if (e2eEnabled) {
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape)
                                .background(Accent).align(Alignment.BottomEnd).offset((-2).dp, (-2).dp),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Lock, null, tint = BgSecondary, modifier = Modifier.size(12.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(userName, style = MaterialTheme.typography.titleMedium)
                Text(userPhone, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showEditDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Accent)
                    ) { Text("Редактировать", fontSize = 12.sp) }
                    OutlinedButton(onClick = { showStatusDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Border)
                    ) { Text("Статус", fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Основные ссылки
            SettingsCard(Modifier.padding(horizontal = 12.dp)) {
                SettingsRow("Избранное", icon = Icons.Filled.Bookmark, iconBgColor = AccentDark, iconColor = Accent, onClick = onFavoritesClick)
                SettingsRow("Мои каналы", icon = Icons.Outlined.Campaign, iconBgColor = PurpleDark, iconColor = Purple, onClick = {})
                SettingsRow("Активные сессии", icon = Icons.Outlined.Devices, iconBgColor = BlueDark, iconColor = Blue,
                    subtitle = "Управление сессиями", onClick = {
                        showSessionsDialog = true
                        sessionsLoading = true
                        scope.launch {
                            sessions = container.session.loadSessions()
                            sessionsLoading = false
                        }
                    }, showDivider = false)
            }

            Spacer(Modifier.height(8.dp))

            // E2E шифрование
            SettingsCard(Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (e2eEnabled) AccentDark else BgTertiary), Alignment.Center) {
                            Icon(Icons.Default.Lock, null, tint = if (e2eEnabled) Accent else TextMuted, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("E2E шифрование", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (e2eEnabled) "Активно · ключ: ${e2eKey ?: "..."}" else "Отключено",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e2eEnabled) Accent else TextMuted
                            )
                        }
                        Switch(
                            checked = e2eEnabled,
                            onCheckedChange = { if (it) vm.enableE2E() else vm.disableE2E() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BgSecondary, checkedTrackColor = Accent,
                                uncheckedThumbColor = TextMuted, uncheckedTrackColor = BgTertiary
                            )
                        )
                    }
                    AnimatedVisibility(visible = e2eEnabled) {
                        Box(Modifier.fillMaxWidth().padding(top = 10.dp)
                            .clip(RoundedCornerShape(8.dp)).background(BgTertiary).padding(10.dp)) {
                            Text(
                                "Сообщения шифруются локально. Сервер видит только зашифрованные данные с префиксом __e2e__.",
                                style = MaterialTheme.typography.labelMedium, color = TextHint
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Настройки + выход
            SettingsCard(Modifier.padding(horizontal = 12.dp)) {
                SettingsRow("Настройки", icon = Icons.Outlined.Settings, onClick = onSettingsClick)
                SettingsRow("Выйти из аккаунта", icon = Icons.Outlined.Logout,
                    iconBgColor = androidx.compose.ui.graphics.Color(0xFF2E0D0D), iconColor = Red,
                    showDivider = false, onClick = onLogout)
            }

            Spacer(Modifier.height(16.dp))
            Text("Max-X v1.0.0 · Нулевая телеметрия · api.oneme.ru only",
                style = MaterialTheme.typography.labelSmall, color = TextHint,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SessionItemFull(
    label: String, line1: String, line2: String,
    lastSeen: String, isCurrent: Boolean, isOnline: Boolean,
    onTerminate: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(BgSecondary, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = TextPrimary)
            if (line1.isNotEmpty())
                Text(line1, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            if (line2.isNotEmpty())
                Text(line2, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (onTerminate != null) {
                Spacer(Modifier.height(4.dp))
                Text("Завершить сессию", fontSize = 12.sp, color = Red,
                    modifier = Modifier.clickable { onTerminate() })
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isCurrent || isOnline) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(7.dp).background(Accent, androidx.compose.foundation.shape.CircleShape))
                    Text("В сети", fontSize = 10.sp, color = Accent)
                }
            } else if (lastSeen.isNotEmpty()) {
                Text(lastSeen, fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}
