package com.physcard.physics.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

private val LightColors = lightColorScheme(
    primary = Jade,
    onPrimary = Color.White,
    primaryContainer = JadeSoft,
    onPrimaryContainer = JadeDeep,
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EBE4),
    onSecondaryContainer = Ink,
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4E4CF),
    onTertiaryContainer = Color(0xFF4B2E10),
    error = Coral,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Paper,
    onBackground = Ink,
    surface = PaperElevated,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECEDE5),
    onSurfaceVariant = Color(0xFF4C554E),
    outline = Color(0xFFBFC4BA),
    outlineVariant = Line,
    surfaceTint = Jade,
    inverseSurface = Color(0xFF2C332E),
    inverseOnSurface = Paper,
    inversePrimary = Mint,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF052014),
    primaryContainer = MintSoft,
    onPrimaryContainer = Color(0xFFC9F5D8),
    secondary = NightSage,
    onSecondary = Color(0xFF142019),
    secondaryContainer = NightSurface,
    onSecondaryContainer = Frost,
    tertiary = NightAmber,
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = Color(0xFF4A3218),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = NightCoral,
    onError = Color(0xFF5F150E),
    errorContainer = Color(0xFF4B1D18),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Night,
    onBackground = Frost,
    surface = NightElevated,
    onSurface = Frost,
    surfaceVariant = NightSurface,
    onSurfaceVariant = Color(0xFFB4C1B7),
    outline = Color(0xFF637068),
    outlineVariant = NightLine,
    surfaceTint = Mint,
    inverseSurface = Frost,
    inverseOnSurface = Night,
    inversePrimary = Jade,
    scrim = Color.Black,
)

@Composable
fun PhysicsFormulaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
