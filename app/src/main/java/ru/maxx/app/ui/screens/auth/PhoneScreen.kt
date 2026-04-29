package ru.maxx.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
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
fun PhoneScreen(container: AppContainer, onOtpRequested: (String, String) -> Unit) {
    val vm = remember { AuthViewModel(container) }
    val state by vm.state.collectAsState()

    var phone by remember { mutableStateOf("+7") }

    LaunchedEffect(state) {
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

        Spacer(Modifier.height(16.dp))
        Text(
            "Только api.oneme.ru — никакой телеметрии",
            style = MaterialTheme.typography.labelSmall,
            color = TextHint, textAlign = TextAlign.Center
        )
    }
}
