package dev.vaultdrop.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * Type stays fixed across seeds — only colour is dynamic.
 *
 * Roboto for chrome; a monospace face for anything that is a path, a date or
 * file content. The design specifies IBM Plex Mono; to use it, drop the OFL
 * `.ttf` files into `res/font/` and change [Mono] to a `FontFamily(...)` over
 * them. Until then this resolves to the platform mono face, which carries the
 * same fixed-advance role.
 */
val Mono: FontFamily = FontFamily.Monospace

/** Chrome face. The platform default is Roboto on every device the app targets. */
val Ui: FontFamily = FontFamily.SansSerif
