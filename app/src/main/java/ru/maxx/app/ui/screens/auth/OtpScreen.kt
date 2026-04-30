package ru.maxx.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.MaxXButton
import ru.maxx.app.ui.theme.*

@Composable
fun OtpScreen(container: AppContainer, token: String, phone: String = "", onAuthorized: () -> Unit) {
    val vm = remember { AuthViewModel(container) }
    val state by vm.state.collectAsState()

    var code         by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var passTrackId  by remember { mutableStateOf("") }
    var passHint     by remember { mutableStateOf<String?>(null) }
    var showPass     by remember { mutableStateOf(false) }
    var currentToken by remember { mutableStateOf(token) }

    // Таймер обратного отсчёта для повторного запроса кода
    var countdown    by remember { mutableStateOf(60) }
    var canResend    by remember { mutableStateOf(false) }

    // Ключ resendKey перезапускает таймер при каждом новом запросе кода
    var resendKey by remember { mutableStateOf(0) }
    LaunchedEffect(resendKey) {
        countdown = 60
        canResend = false
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        canResend = true
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthViewModel.UiState.Success          -> onAuthorized()
            is AuthViewModel.UiState.PasswordRequired -> {
                passTrackId = s.trackId
                passHint    = s.hint
                showPass    = true
            }
            is AuthViewModel.UiState.OtpSent -> {
                currentToken = s.token
                code         = ""
                resendKey++  // перезапускает таймер
            }
            is AuthViewModel.UiState.CodeExpired -> {
                // Сервер сказал что код устарел — сбрасываем
                code      = ""
                canResend = true
                countdown = 0
            }
            else -> {}
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        Box(
            Modifier.size(280.dp).offset(x = (-40).dp, y = (-60).dp)
                .blur(90.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(AccentDark.copy(alpha = 0.5f), androidx.compose.ui.graphics.Color.Transparent)
                    )
                )
        )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (showPass) "Пароль аккаунта" else "Код подтверждения",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (showPass) passHint?.let { "Подсказка: $it" } ?: "Введите пароль двухэтапной защиты"
            else "Введите код из SMS",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        if (!showPass) {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) { code = it; vm.clearError() } },
                placeholder = { Text("000000", color = TextHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center, fontSize = 26.sp, letterSpacing = 10.sp
                ),
                isError = state is AuthViewModel.UiState.Error || state is AuthViewModel.UiState.CodeExpired,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor   = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor      = Accent, unfocusedBorderColor    = Border,
                    focusedTextColor        = TextPrimary, unfocusedTextColor  = TextPrimary,
                    cursorColor             = Accent,
                    errorBorderColor        = Red, errorContainerColor        = BgCard
                )
            )
        } else {
            OutlinedTextField(
                value = password, onValueChange = { password = it; vm.clearError() },
                placeholder = { Text("Пароль", color = TextHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                isError = state is AuthViewModel.UiState.Error,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor   = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor      = Accent, unfocusedBorderColor    = Border,
                    focusedTextColor        = TextPrimary, unfocusedTextColor  = TextPrimary,
                    cursorColor             = Accent,
                    errorBorderColor        = Red, errorContainerColor        = BgCard
                )
            )
        }

        // Ошибка
        when (val s = state) {
            is AuthViewModel.UiState.Error       ->
                { Spacer(Modifier.height(6.dp)); Text(s.msg, color = Red, style = MaterialTheme.typography.labelMedium) }
            is AuthViewModel.UiState.CodeExpired ->
                { Spacer(Modifier.height(6.dp)); Text("Код устарел. Запросите новый.", color = Red, style = MaterialTheme.typography.labelMedium) }
            else -> {}
        }

        Spacer(Modifier.height(20.dp))

        MaxXButton(
            text    = if (showPass) "Войти" else "Подтвердить",
            onClick = {
                if (showPass) vm.sendPassword(passTrackId, password)
                else vm.verifyOtp(currentToken, code)
            },
            loading = state == AuthViewModel.UiState.Loading,
            enabled = (if (showPass) password.isNotEmpty() else code.length >= 4)
                && state != AuthViewModel.UiState.Loading
        )

        // Кнопка повторного запроса (только для SMS кода)
        if (!showPass) {
            Spacer(Modifier.height(12.dp))
            if (canResend) {
                TextButton(onClick = {
                    if (phone.isNotEmpty()) {
                        canResend = false
                        vm.requestOtp(phone)
                    }
                }) {
                    Text("Запросить код повторно", color = Accent, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Text(
                    "Запросить повторно через ${countdown} сек",
                    style = MaterialTheme.typography.labelMedium, color = TextMuted
                )
            }
        }
    }
}
}
}
