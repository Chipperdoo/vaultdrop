package dev.vaultdrop.ui.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

object ContrastUtils {
    private const val WCAG_AA_RATIO = 4.5f
    private const val WCAG_AAA_RATIO = 7f
    private const val WCAG_LARGE_TEXT_AA_RATIO = 3f
    private const val WCAG_LARGE_TEXT_AAA_RATIO = 4.5f

    fun calculateRelativeLuminance(color: Color): Double {
        val red = color.red.toDouble()
        val green = color.green.toDouble()
        val blue = color.blue.toDouble()

        val r = if (red <= 0.03928) red / 12.92 else ((red + 0.055) / 1.055).pow(2.4)
        val g = if (green <= 0.03928) green / 12.92 else ((green + 0.055) / 1.055).pow(2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else ((blue + 0.055) / 1.055).pow(2.4)

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun calculateContrastRatio(foreground: Color, background: Color): Float {
        val l1 = calculateRelativeLuminance(foreground)
        val l2 = calculateRelativeLuminance(background)

        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)

        return ((lighter + 0.05) / (darker + 0.05)).toFloat()
    }

    fun meetsWCAG_AA_Text(foreground: Color, background: Color): Boolean {
        return calculateContrastRatio(foreground, background) >= WCAG_AA_RATIO
    }

    fun meetsWCAG_AAA_Text(foreground: Color, background: Color): Boolean {
        return calculateContrastRatio(foreground, background) >= WCAG_AAA_RATIO
    }

    fun meetsWCAG_AA_LargeText(foreground: Color, background: Color): Boolean {
        return calculateContrastRatio(foreground, background) >= WCAG_LARGE_TEXT_AA_RATIO
    }

    fun meetsWCAG_AAA_LargeText(foreground: Color, background: Color): Boolean {
        return calculateContrastRatio(foreground, background) >= WCAG_LARGE_TEXT_AAA_RATIO
    }

    fun isHighContrastModeEnabled(context: Context): Boolean {
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return accessibilityManager?.isEnabled == true
    }
}

fun Color.contrastRatioWith(background: Color): Float {
    return ContrastUtils.calculateContrastRatio(this, background)
}

fun Color.meetsWCAG_AA_Text(background: Color): Boolean {
    return ContrastUtils.meetsWCAG_AA_Text(this, background)
}

fun Color.meetsWCAG_AAA_Text(background: Color): Boolean {
    return ContrastUtils.meetsWCAG_AAA_Text(this, background)
}

fun Color.meetsWCAG_AA_LargeText(background: Color): Boolean {
    return ContrastUtils.meetsWCAG_AA_LargeText(this, background)
}

fun Color.meetsWCAG_AAA_LargeText(background: Color): Boolean {
    return ContrastUtils.meetsWCAG_AAA_LargeText(this, background)
}
