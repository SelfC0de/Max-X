package ru.maxx.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.MaxXButton
import ru.maxx.app.ui.theme.*

@Composable
fun PhoneScreen(container: AppContainer, onOtpRequested: (String, String) -> Unit, onAuthorized: () -> Unit = {}) {
    val vm = remember { AuthViewModel(container) }
    val state by vm.state.collectAsState()

    var phone by remember { mutableStateOf("+7") }
    var showTokenLogin by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }

    // Вход через токен напрямую
    if (showTokenLogin) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTokenLogin = false },
            containerColor   = ru.maxx.app.ui.theme.BgCard,
            title = { androidx.compose.material3.Text("Вход по токену", style = androidx.compose.material3.MaterialTheme.typography.titleSmall) },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Text("Вставьте токен сессии:", fontSize = 11.sp, color = ru.maxx.app.ui.theme.TextMuted)
                    androidx.compose.material3.OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = { androidx.compose.material3.Text("An_Sx6HQ...", color = ru.maxx.app.ui.theme.TextHint, fontSize = 11.sp) },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 3, maxLines = 5,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = ru.maxx.app.ui.theme.BgSecondary,
                            unfocusedContainerColor = ru.maxx.app.ui.theme.BgSecondary,
                            focusedBorderColor      = ru.maxx.app.ui.theme.Accent,
                            unfocusedBorderColor    = ru.maxx.app.ui.theme.Border,
                            focusedTextColor        = ru.maxx.app.ui.theme.TextPrimary,
                            unfocusedTextColor      = ru.maxx.app.ui.theme.TextPrimary,
                            cursorColor             = ru.maxx.app.ui.theme.Accent
                        )
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = tokenInput.length > 20,
                    onClick = {
                        container.authPrefs.setToken(tokenInput.trim())
                        showTokenLogin = false
                        vm.loginWithToken(tokenInput.trim())
                    }
                ) { androidx.compose.material3.Text("Войти", color = if (tokenInput.length > 20) ru.maxx.app.ui.theme.Accent else ru.maxx.app.ui.theme.TextMuted) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTokenLogin = false; tokenInput = "" }) {
                    androidx.compose.material3.Text("Отмена", color = ru.maxx.app.ui.theme.TextMuted)
                }
            }
        )
    }

    LaunchedEffect(state) {
        if (state is AuthViewModel.UiState.Success) { onAuthorized() }
        if (state is AuthViewModel.UiState.OtpSent) {
            onOtpRequested((state as AuthViewModel.UiState.OtpSent).token, phone)
            vm.clearError()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = ru.maxx.app.R.drawable.ic_launcher_logo),
            contentDescription = "Max-X",
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(22.dp))
        )

        Spacer(Modifier.height(28.dp))
        Text("Max-X", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text("Введите номер для входа", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = phone, onValueChange = { phone = it; vm.clearError() },
            placeholder = { Text("+7 900 000-00-00", color = TextHint) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            isError = state is AuthViewModel.UiState.Error,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                focusedBorderColor = Accent, unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                cursorColor = Accent, errorBorderColor = Red, errorContainerColor = BgCard
            )
        )

        if (state is AuthViewModel.UiState.Error) {
            Spacer(Modifier.height(6.dp))
            Text((state as AuthViewModel.UiState.Error).msg, color = Red, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(20.dp))

        MaxXButton(
            text = "Получить код",
            onClick = { vm.requestOtp(phone.trim()) },
            loading = state == AuthViewModel.UiState.Loading,
            enabled = phone.length >= 11 && state != AuthViewModel.UiState.Loading
        )

        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.TextButton(
            onClick = { showTokenLogin = true },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.Text(
                "Войти по токену сессии",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Только api.oneme.ru — никакой телеметрии",
            style = MaterialTheme.typography.labelSmall,
            color = TextHint, textAlign = TextAlign.Center
        )
    }
}
