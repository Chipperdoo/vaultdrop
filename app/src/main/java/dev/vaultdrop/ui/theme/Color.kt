package dev.vaultdrop.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The fallback schemes — what ships for pre-Android-12 devices, where
 * `dynamicColorScheme` returns nothing. On Android 12+ every one of these is
 * replaced by the wallpaper-derived role of the same name; nothing in the
 * layout is tied to a hue.
 *
 * Values are the ochre seed from the design's fallback column.
 */

// Dark — the default.
private val DarkSurface = Color(0xFF15130F)
private val DarkOnSurface = Color(0xFFE9E1D5)
private val DarkSurfaceContainer = Color(0xFF211E19)
private val DarkSurfaceContainerHigh = Color(0xFF2C2822)
private val DarkOutline = Color(0xFF7A7263)
private val DarkOnSurfaceVariant = Color(0xFF9A9081)
private val DarkPrimary = Color(0xFFECC182)
private val DarkOnPrimary = Color(0xFF422C00)
private val DarkPrimaryContainer = Color(0xFF5E441C)
private val DarkOnPrimaryContainer = Color(0xFFFFDDAD)
private val DarkError = Color(0xFFFFB4AB)
private val DarkErrorContainer = Color(0xFF3D1512)
private val DarkOnErrorContainer = Color(0xFFFFB4AB)

// Light — warm paper ground, ochre at 30% lightness.
private val LightSurface = Color(0xFFFFF8F1)
private val LightOnSurface = Color(0xFF1E1B16)
private val LightSurfaceContainer = Color(0xFFF5ECE1)
private val LightSurfaceContainerHigh = Color(0xFFEFE5D9)
private val LightOutline = Color(0xFF7D7667)
private val LightOnSurfaceVariant = Color(0xFF5C5548)
private val LightPrimary = Color(0xFF7B5800)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFFFDDAD)
private val LightOnPrimaryContainer = Color(0xFF281900)
private val LightError = Color(0xFFBA1A1A)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

val VaultDropDarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerLow = DarkSurfaceContainer,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkSurfaceContainerHigh,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    error = DarkError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

val VaultDropLightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerLow = LightSurfaceContainer,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightSurfaceContainerHigh,
    background = LightSurface,
    onBackground = LightOnSurface,
    error = LightError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)
