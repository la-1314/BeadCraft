package com.beadcraft.pattern.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- MIUI X 色彩体系 ----------

val MiuiOrange = Color(0xFFFF6900)
val MiuiOrangeDeep = Color(0xFFE85A00)
val MiuiOrangeLight = Color(0xFFFF9447)
val MiuiOrangeContainer = Color(0xFFFFEDE0)
val MiuiBlue = Color(0xFF4E8DFF)

val LightBackground = Color(0xFFF4F5F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F1F4)
val LightOutline = Color(0xFFE4E6EB)

val DarkBackground = Color(0xFF101116)
val DarkSurface = Color(0xFF1B1D24)
val DarkSurfaceVariant = Color(0xFF252830)
val DarkOutline = Color(0xFF33363F)

private val LightColors = lightColorScheme(
    primary = MiuiOrange,
    onPrimary = Color.White,
    primaryContainer = MiuiOrangeContainer,
    onPrimaryContainer = MiuiOrangeDeep,
    secondary = MiuiBlue,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1C1E24),
    surface = LightSurface,
    onSurface = Color(0xFF1C1E24),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF7A7E88),
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = Color(0xFFE5484D),
)

private val DarkColors = darkColorScheme(
    primary = MiuiOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2600),
    onPrimaryContainer = MiuiOrangeLight,
    secondary = MiuiBlue,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = Color(0xFFECEDEF),
    surface = DarkSurface,
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF9CA0AA),
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = Color(0xFFFF6369),
)

/** MIUI 大标题排版：加粗、偏大 */
private val MiuiTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun BeadCraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MiuiTypography,
        content = content,
    )
}

// ---------- 共用形状 ----------

val CardRadius = 24.dp
val ChipRadius = 16.dp
val ButtonRadius = 28.dp
