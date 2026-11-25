package com.inktrail.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.inktrail.reader.domain.model.ReaderTheme

private val LightColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = InkOnPrimary,
    primaryContainer = InkPrimaryContainer,
    onPrimaryContainer = InkOnPrimaryContainer,
    secondary = InkSecondary,
    secondaryContainer = InkSecondaryContainer,
    onSecondaryContainer = InkOnSecondaryContainer,
    background = InkPaperBg,
    onBackground = InkTextPrimary,
    surface = InkPaperSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = InkPaperSurfaceVariant,
    onSurfaceVariant = InkTextSecondary,
    outlineVariant = InkOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = InkNightPrimary,
    onPrimary = Color(0xFF0A1D33),
    primaryContainer = InkNightPrimaryContainer,
    onPrimaryContainer = Color(0xFFD5E6FA),
    secondary = Color(0xFFC5B6A2),
    secondaryContainer = Color(0xFF383128),
    onSecondaryContainer = Color(0xFFEDE2D3),
    background = InkNightBg,
    onBackground = InkNightTextPrimary,
    surface = InkNightSurface,
    onSurface = InkNightTextPrimary,
    surfaceVariant = InkNightSurfaceVariant,
    onSurfaceVariant = InkNightTextSecondary,
    outlineVariant = InkNightOutline
)

@Immutable
data class ReaderPalette(
    val theme: ReaderTheme,
    val background: Color,
    val surface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val focusLineBackground: Color,
    val divider: Color
)

fun ReaderTheme.toPalette(): ReaderPalette {
    val bg = Color(backgroundHex)
    val surf = Color(surfaceHex)
    val text = Color(primaryTextHex)
    val subText = Color(secondaryTextHex)
    val acc = Color(accentHex)
    return ReaderPalette(
        theme = this,
        background = bg,
        surface = surf,
        primaryText = text,
        secondaryText = subText,
        accent = acc,
        focusLineBackground = if (isDark) Color(0x26FFFFFF) else Color(0x14000000),
        divider = if (isDark) Color(0x29FFFFFF) else Color(0x1A000000)
    )
}

@Composable
fun InkTrailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
