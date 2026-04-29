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
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.MaxXButton
import ru.maxx.app.ui.theme.*

@Composable
fun OtpScreen(container: AppContainer, token: String, onAuthorized: () -> Unit) {
    val vm = remember { AuthViewModel(container) }
    val state by vm.state.collectAsState()

    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passTrackId by remember { mutableStateOf("") }
    var passHint by remember { mutableStateOf<String?>(null) }
    var showPass by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthViewModel.UiState.Success -> onAuthorized()
            is AuthViewModel.UiState.PasswordRequired -> {
                passTrackId = s.trackId
                passHint = s.hint
                showPass = true
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgPrimary).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (showPass) "Пароль аккаунта" else "Код подтверждения",
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (showPass) passHint?.let { "Подсказка: $it" } ?: "Введите пароль двухэтапной защиты"
            else "Введите код из SMS",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        if (!showPass) {
            OutlinedTextField(
                value = code, onValueChange = { if (it.length <= 6) { code = it; vm.clearError() } },
                placeholder = { Text("000000", color = TextHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center, fontSize = 26.sp, letterSpacing = 10.sp
                ),
                isError = state is AuthViewModel.UiState.Error,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    cursorColor = Accent, errorBorderColor = Red, errorContainerColor = BgCard
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
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    cursorColor = Accent, errorBorderColor = Red, errorContainerColor = BgCard
                )
            )
        }

        if (state is AuthViewModel.UiState.Error) {
            Spacer(Modifier.height(6.dp))
            Text((state as AuthViewModel.UiState.Error).msg, color = Red, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(20.dp))

        MaxXButton(
            text = if (showPass) "Войти" else "Подтвердить",
            onClick = {
                if (showPass) vm.sendPassword(passTrackId, password)
                else vm.verifyOtp(token, code)
            },
            loading = state == AuthViewModel.UiState.Loading,
            enabled = (if (showPass) password.isNotEmpty() else code.length >= 4) && state != AuthViewModel.UiState.Loading
        )
    }
}
