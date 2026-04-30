package ru.maxx.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.MaxXButton
import ru.maxx.app.ui.theme.*

@Composable
fun PhoneScreen(
    container: AppContainer,
    onOtpRequested: (String, String) -> Unit,
    onAuthorized: () -> Unit = {}
) {
    val vm    = remember { AuthViewModel(container) }
    val state by vm.state.collectAsState()

    var phone          by remember { mutableStateOf("+7") }
    var showTokenLogin by remember { mutableStateOf(false) }
    var tokenInput     by remember { mutableStateOf("") }

    // Fade-in при открытии
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Пульс иконки
    val pulse = rememberInfiniteTransition(label = "pulse")
    val iconScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "icon_scale"
    )
    // Glow цвет анимация
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    LaunchedEffect(state) {
        if (state is AuthViewModel.UiState.Success) { onAuthorized() }
        if (state is AuthViewModel.UiState.OtpSent) {
            onOtpRequested((state as AuthViewModel.UiState.OtpSent).token, phone)
            vm.clearError()
        }
    }

    // Диалог входа по токену
    if (showTokenLogin) {
        AlertDialog(
            onDismissRequest = { showTokenLogin = false },
            containerColor   = BgCard,
            title = { Text("Вход по токену", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Вставьте токен сессии:", fontSize = 11.sp, color = TextMuted)
                    OutlinedTextField(
                        value = tokenInput, onValueChange = { tokenInput = it },
                        placeholder = { Text("An_Sx6HQ...", color = TextHint, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp), minLines = 3, maxLines = 5,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgSecondary, unfocusedContainerColor = BgSecondary,
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = tokenInput.length > 20,
                    onClick = { container.authPrefs.setToken(tokenInput.trim()); showTokenLogin = false; vm.loginWithToken(tokenInput.trim()) }
                ) { Text("Войти", color = if (tokenInput.length > 20) Accent else TextMuted) }
            },
            dismissButton = {
                TextButton(onClick = { showTokenLogin = false; tokenInput = "" }) {
                    Text("Отмена", color = TextMuted)
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BgPrimary).statusBarsPadding()
    ) {
        // Фоновое свечение
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(listOf(
                        Accent.copy(alpha = glowAlpha * 0.25f),
                        Color.Transparent
                    )),
                    CircleShape
                )
        )

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.weight(0.8f))

                // Логотип с glow эффектом
                Box(contentAlignment = Alignment.Center) {
                    // Glow ring
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.radialGradient(listOf(
                                    Accent.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                ))
                            )
                    )
                    Image(
                        painter = painterResource(id = ru.maxx.app.R.drawable.ic_launcher_logo),
                        contentDescription = "Max-X",
                        modifier = Modifier
                            .size(96.dp)
                            .scale(iconScale)
                            .clip(RoundedCornerShape(22.dp))
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    "Max-X",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Безопасный мессенджер",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(48.dp))

                // Поле телефона
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Номер телефона") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Phone, null, tint = Accent, modifier = Modifier.size(18.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = BgCard, unfocusedContainerColor = BgCard,
                        focusedBorderColor      = Accent, unfocusedBorderColor    = Border.copy(alpha = 0.5f),
                        focusedTextColor        = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor             = Accent,
                        focusedLabelColor       = Accent, unfocusedLabelColor     = TextHint
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Кнопка входа
                MaxXButton(
                    text    = "Получить код",
                    onClick = { vm.requestOtp(phone.trim()) },
                    loading = state == AuthViewModel.UiState.Loading,
                    enabled = phone.length >= 11 && state != AuthViewModel.UiState.Loading
                )

                // Ошибка
                AnimatedVisibility(
                    visible = state is AuthViewModel.UiState.Error,
                    enter = fadeIn() + expandVertically(),
                    exit  = fadeOut() + shrinkVertically()
                ) {
                    if (state is AuthViewModel.UiState.Error) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF3D1515))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, null,
                                tint = Color(0xFFFF6A6A), modifier = Modifier.size(16.dp))
                            Text((state as AuthViewModel.UiState.Error).msg,
                                fontSize = 12.sp, color = Color(0xFFFF6A6A))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Вход по токену
                TextButton(
                    onClick = { showTokenLogin = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Key, null,
                        tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Войти по токену сессии",
                        style = MaterialTheme.typography.labelMedium, color = TextMuted)
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Только api.oneme.ru · никакой телеметрии",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHint.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
