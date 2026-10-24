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
    primary = Azure,
    onPrimary = Color.White,
    primaryContainer = AzureSoft,
    onPrimaryContainer = AzureDeep,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealSoft,
    onSecondaryContainer = Color(0xFF063A3A),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = Color(0xFF4A2F0C),
    error = Coral,
    onError = Color.White,
    errorContainer = Color(0xFFFBE0DD),
    onErrorContainer = Color(0xFF45100C),
    background = Paper,
    onBackground = Ink,
    surface = PaperElevated,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF1F7),
    onSurfaceVariant = InkSoft,
    outline = LineStrong,
    outlineVariant = Line,
    surfaceTint = Azure,
    inverseSurface = Color(0xFF23252B),
    inverseOnSurface = Paper,
    inversePrimary = AzureBright,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = AzureBright,
    onPrimary = Color(0xFF04173D),
    primaryContainer = AzureDim,
    onPrimaryContainer = Color(0xFFCBDCFF),
    secondary = TealBright,
    onSecondary = Color(0xFF00201F),
    secondaryContainer = TealDim,
    onSecondaryContainer = Color(0xFFB7EFEE),
    tertiary = AmberBright,
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = AmberDim,
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = CoralBright,
    onError = Color(0xFF5F150E),
    errorContainer = Color(0xFF4B1D18),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Night,
    onBackground = Frost,
    surface = NightElevated,
    onSurface = Frost,
    surfaceVariant = NightSurface,
    onSurfaceVariant = FrostSoft,
    outline = Color(0xFF5A6070),
    outlineVariant = NightLine,
    surfaceTint = AzureBright,
    inverseSurface = Frost,
    inverseOnSurface = Night,
    inversePrimary = Azure,
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
