package ru.maxx.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxx.app.ui.theme.*

@Composable
fun MaxXTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column {
        Spacer(Modifier.windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars).background(BgSecondary))
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp)
                .background(BgSecondary).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Accent)
                }
            } else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            Spacer(Modifier.width(4.dp))
        }
        HorizontalDivider(color = Border.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

@Composable
fun AvatarView(
    initials: String, size: Int = 44,
    bgColor: Color = AccentDark, textColor: Color = Accent,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2).uppercase(),
            fontSize = (size / 3.5).sp, fontWeight = FontWeight.Medium, color = textColor
        )
    }
}

@Composable
fun OnlineDot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(9.dp).clip(CircleShape).background(Accent))
}

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(RoundedCornerShape(10.dp)).background(Accent).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 10.sp, fontWeight = FontWeight.Medium, color = BgSecondary
        )
    }
}

@Composable
fun MaxXButton(
    text: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false
) {
    val elevationState = animateDpAsState(if (enabled && !loading) 4.dp else 0.dp, label = "btn_elev")
    Button(
        onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = elevationState.value),
        colors = ButtonDefaults.buttonColors(
            containerColor         = Accent,  contentColor         = BgPrimary,
            disabledContainerColor = BgCard,  disabledContentColor = TextMuted
        )
    ) {
        AnimatedContent(loading, label = "btn_content",
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BgPrimary, strokeWidth = 2.dp)
            } else {
                Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp)
            }
        }
    }
}

@Composable
fun MaxXTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextHint, style = MaterialTheme.typography.bodyMedium) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        singleLine = singleLine, enabled = enabled,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = BgCard, unfocusedContainerColor = BgCard,
            focusedBorderColor      = Accent, unfocusedBorderColor = Border,
            focusedTextColor        = TextPrimary, unfocusedTextColor = TextPrimary,
            cursorColor             = Accent, disabledContainerColor = BgCard,
            disabledBorderColor     = Border
        )
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
    ) { content() }
}

@Composable
fun SettingsRow(
    title: String, subtitle: String? = null,
    icon: ImageVector? = null, iconBgColor: Color = BgTertiary, iconColor: Color = Accent,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val mod = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick)
    else Modifier.fillMaxWidth()
    Row(modifier = mod.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(iconBgColor),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 1.dp))
        }
        trailing?.invoke()
    }
    if (showDivider) HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(start = if (icon != null) 58.dp else 16.dp))
}

@Composable
fun StatusTag(text: String, type: TagType = TagType.OK) {
    val (bg, fg) = when (type) {
        TagType.OK   -> AccentDark to Accent
        TagType.WARN -> Color(0xFF2E1A0D) to Orange
        TagType.OFF  -> BgTertiary to TextMuted
        TagType.ERR  -> Color(0xFF2E0D0D) to Red
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp)
    ) { Text(text, fontSize = 10.sp, color = fg, fontWeight = FontWeight.Medium) }
}

enum class TagType { OK, WARN, OFF, ERR }
