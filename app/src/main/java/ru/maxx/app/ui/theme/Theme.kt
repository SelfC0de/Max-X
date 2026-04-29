package ru.maxx.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра Max-X
val BgPrimary     = Color(0xFF0A0A10)
val BgSecondary   = Color(0xFF0D0D14)
val BgTertiary    = Color(0xFF13131E)
val BgCard        = Color(0xFF161622)
val Border        = Color(0xFF1A1A28)
val BorderLight   = Color(0xFF222230)

val Accent        = Color(0xFF8CBF26)
val AccentDark    = Color(0xFF1A2E0D)
val AccentMid     = Color(0xFF2A4A14)
val AccentLight   = Color(0xFFC8E8A0)

val TextPrimary   = Color(0xFFE8E8F0)
val TextSecondary = Color(0xFFD0D0E0)
val TextMuted     = Color(0xFF66667A)
val TextHint      = Color(0xFF44445A)

val BubbleIn      = Color(0xFF161622)
val BubbleOut     = Color(0xFF1A2E0D)
val BubbleTextIn  = Color(0xFFC8C8D8)
val BubbleTextOut = Color(0xFFC8E8A0)

val Red           = Color(0xFFFF6A6A)
val Orange        = Color(0xFFFF9F4A)
val Blue          = Color(0xFF4A9EFF)
val Purple        = Color(0xFFB06AFF)
val BlueDark      = Color(0xFF0D1A2A)
val PurpleDark    = Color(0xFF1A0D2A)

private val colors = darkColorScheme(
    primary               = Accent,
    onPrimary             = BgSecondary,
    primaryContainer      = AccentDark,
    onPrimaryContainer    = AccentLight,
    secondary             = Blue,
    onSecondary           = BgPrimary,
    background            = BgPrimary,
    surface               = BgSecondary,
    surfaceVariant        = BgTertiary,
    onBackground          = TextPrimary,
    onSurface             = TextPrimary,
    onSurfaceVariant      = TextMuted,
    outline               = Border,
    outlineVariant        = BorderLight,
    error                 = Red,
    onError               = BgPrimary,
)

@Composable
fun MaxXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = MaxXTypography, content = content)
}
