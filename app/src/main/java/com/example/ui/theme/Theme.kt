package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = CinemaVioletDark,
    onPrimary = CinemaOnVioletDark,
    primaryContainer = CinemaVioletContainerDark,
    onPrimaryContainer = CinemaOnVioletContainerDark,
    secondary = CinemaTealDark,
    onSecondary = CinemaOnTealDark,
    secondaryContainer = CinemaTealContainerDark,
    onSecondaryContainer = CinemaOnTealContainerDark,
    tertiary = CinemaAmberDark,
    onTertiary = CinemaOnAmberDark,
    tertiaryContainer = CinemaAmberContainerDark,
    onTertiaryContainer = CinemaOnAmberContainerDark,
    background = CinemaBgDark,
    onBackground = CinemaOnSurfaceDark,
    surface = CinemaSurfaceDark,
    onSurface = CinemaOnSurfaceDark,
    surfaceVariant = CinemaSurfaceContainerHighDark,
    onSurfaceVariant = CinemaOnSurfaceVariantDark,
    surfaceContainerLowest = CinemaBgDark,
    surfaceContainerLow = CinemaSurfaceDark,
    surfaceContainer = CinemaSurfaceContainerDark,
    surfaceContainerHigh = CinemaSurfaceContainerHighDark,
    outline = CinemaOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = CinemaVioletLight,
    onPrimary = CinemaOnVioletLight,
    primaryContainer = CinemaVioletContainerLight,
    onPrimaryContainer = CinemaOnVioletContainerLight,
    secondary = CinemaTealLight,
    onSecondary = CinemaOnTealLight,
    secondaryContainer = CinemaTealContainerLight,
    onSecondaryContainer = CinemaOnTealContainerLight,
    tertiary = CinemaAmberLight,
    onTertiary = CinemaOnAmberLight,
    tertiaryContainer = CinemaAmberContainerLight,
    onTertiaryContainer = CinemaOnAmberContainerLight,
    background = CinemaBgLight,
    onBackground = CinemaOnSurfaceLight,
    surface = CinemaSurfaceLight,
    onSurface = CinemaOnSurfaceLight,
    surfaceVariant = CinemaSurfaceContainerHighLight,
    onSurfaceVariant = CinemaOnSurfaceVariantLight,
    surfaceContainerLowest = CinemaBgLight,
    surfaceContainerLow = CinemaSurfaceLight,
    surfaceContainer = CinemaSurfaceContainerLight,
    surfaceContainerHigh = CinemaSurfaceContainerHighLight,
    outline = CinemaOutlineLight
)

@Composable
fun CineBoardTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    language: AppLanguage = AppLanguage.AR,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = cineBoardTypography(language),
        content = content
    )
}
