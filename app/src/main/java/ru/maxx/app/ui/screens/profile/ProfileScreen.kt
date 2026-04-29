package ru.maxx.app.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
    val userPhone = container.authPrefs.getUserPhone() ?: "+7 *** ***-**-**"
    val initials  = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
    val userName  = container.authPrefs.getUserName() ?: "Пользователь"

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
                    OutlinedButton(onClick = {},
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Accent)
                    ) { Text("Редактировать", fontSize = 12.sp) }
                    OutlinedButton(onClick = {},
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
                    subtitle = "2 устройства", onClick = {}, showDivider = false)
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
