package ru.maxx.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MaxXTypography = Typography(
    titleLarge   = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    titleMedium  = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    titleSmall   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    bodyLarge    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = TextSecondary),
    bodyMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = TextSecondary),
    bodySmall    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = TextMuted),
    labelLarge   = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary),
    labelMedium  = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = TextMuted),
    labelSmall   = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, color = TextHint),
)
