package app.mytasks.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Theme options, in the order of the Theme setting. */
enum class ThemeChoice { Light, Dark, Black, SystemLightDark, SystemLightBlack }

data class AppColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val checkbox: Color,
    val divider: Color,
    val outline: Color,
    val accent: Color,
    val isDark: Boolean,
)

val Accent = Color(0xFFFEC656)

private val LightColors = AppColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    text = Color(0xDE000000),
    textSecondary = Color(0x99000000),
    checkbox = Color(0x8A000000),
    divider = Color(0x1F000000),
    outline = Color(0x61000000),
    accent = Accent,
    isDark = false,
)

private val DarkColors = AppColors(
    background = Color(0xFF303030),
    surface = Color(0xFF424242),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xB3FFFFFF),
    checkbox = Color(0xB3FFFFFF),
    divider = Color(0x1FFFFFFF),
    outline = Color(0x80FFFFFF),
    accent = Accent,
    isDark = true,
)

private val BlackColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
    divider = Color(0xFF2C2C2C),
)

private val LocalAppColors = staticCompositionLocalOf { BlackColors }

@Composable
fun appColors(): AppColors = LocalAppColors.current

@Composable
fun AppTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (choice) {
        ThemeChoice.Light -> LightColors
        ThemeChoice.Dark -> DarkColors
        ThemeChoice.Black -> BlackColors
        ThemeChoice.SystemLightDark -> if (systemDark) DarkColors else LightColors
        ThemeChoice.SystemLightBlack -> if (systemDark) BlackColors else LightColors
    }
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.Black,
            secondary = colors.accent,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.background,
            onSurface = colors.text,
            surfaceVariant = colors.surface,
            outline = colors.outline,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.Black,
            secondary = colors.accent,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.background,
            onSurface = colors.text,
            surfaceVariant = colors.surface,
            outline = colors.outline,
        )
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
