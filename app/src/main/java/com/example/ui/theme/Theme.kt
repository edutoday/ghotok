package com.example.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

data class ThemeController(
    val isDarkTheme: Boolean = false,
    val onToggleTheme: (Boolean) -> Unit = {}
)

val LocalThemeController = compositionLocalOf { ThemeController() }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB1C1),
    onPrimary = Color(0xFF650020),
    primaryContainer = CrimsonPrimary,
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = GoldSecondaryLight,
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF623F00),
    onSecondaryContainer = GoldContainer,
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF003828),
    tertiaryContainer = Color(0xFF00513B),
    onTertiaryContainer = EmeraldContainer,
    background = DarkBackground,
    onBackground = Color(0xFFEDE0DE),
    surface = DarkSurface,
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD7C1C3),
    surfaceContainerLowest = Color(0xFF160E10),
    surfaceContainerLow = Color(0xFF221719),
    surfaceContainer = Color(0xFF261A1C),
    surfaceContainerHigh = Color(0xFF312325),
    surfaceContainerHighest = Color(0xFF3C2D30),
    outline = Color(0xFF9F8C8E),
    outlineVariant = Color(0xFF524345)
)

private val LightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = CrimsonPrimaryDark,
    secondary = GoldSecondary,
    onSecondary = Color.White,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = Color(0xFF064E3B),
    background = OffWhiteBackground,
    onBackground = Color(0xFF1E191A),
    surface = SoftSurface,
    onSurface = Color(0xFF1E191A),
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = Color(0xFF524345),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF9F5),
    surfaceContainer = Color(0xFFF7F2EB),
    surfaceContainerHigh = Color(0xFFF1ECE4),
    surfaceContainerHighest = Color(0xFFEBE5DC),
    outline = Color(0xFF857375),
    outlineVariant = OutlineVariant
)

private const val THEME_PREFS = "ghotok_theme_prefs"
private const val KEY_DARK_MODE = "is_dark_mode"
private const val KEY_BANGLA_MODE = "is_bangla_mode"

@Composable
fun MyApplicationTheme(
    defaultDarkTheme: Boolean = false, // Default to Light Mode
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
    }
    var isDarkTheme by remember {
        mutableStateOf(prefs.getBoolean(KEY_DARK_MODE, defaultDarkTheme))
    }
    var isBangla by remember {
        mutableStateOf(prefs.getBoolean(KEY_BANGLA_MODE, false))
    }

    val controller = remember(isDarkTheme) {
        ThemeController(
            isDarkTheme = isDarkTheme,
            onToggleTheme = { newDark ->
                isDarkTheme = newDark
                prefs.edit().putBoolean(KEY_DARK_MODE, newDark).apply()
            }
        )
    }

    val languageController = remember(isBangla) {
        LanguageController(
            isBangla = isBangla,
            onToggleBangla = { newBangla ->
                isBangla = newBangla
                prefs.edit().putBoolean(KEY_BANGLA_MODE, newBangla).apply()
            }
        )
    }

    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalThemeController provides controller,
        LocalLanguageController provides languageController
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun ThemeToggleSwitch(modifier: Modifier = Modifier) {
    val themeController = LocalThemeController.current
    val isLight = !themeController.isDarkTheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Switch(
            checked = isLight,
            onCheckedChange = { lightSelected ->
                themeController.onToggleTheme(!lightSelected)
            },
            modifier = Modifier
                .scale(0.82f)
                .testTag("theme_toggle_switch"),
            thumbContent = {
                Icon(
                    imageVector = if (isLight) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isLight) "Light Mode" else "Dark Mode",
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedIconColor = MaterialTheme.colorScheme.surface
            )
        )
        Spacer(modifier = Modifier.width(2.dp))
    }
}
