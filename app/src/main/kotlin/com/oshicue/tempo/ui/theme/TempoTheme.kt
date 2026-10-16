package com.oshicue.tempo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.oshicue.tempo.R
import kotlin.math.abs
import kotlin.math.pow

/**
 * Dynamic colors mirroring the iOS TempoStyle. Member accents always keep the
 * dark-mode contrast adjustment in both appearances, so the stored representative
 * color stays stable while remaining readable.
 */
data class TempoPalette(
    val background: Color,
    val surface: Color,
    val controlSurface: Color,
    val ink: Color,
    val muted: Color,
    val pink: Color,
    val primaryFill: Color,
    val onPrimary: Color,
    val brandPink: Color,
    val line: Color,
    val swatchOutline: Color,
    val runningIndicator: Color,
    val cardShadow: Color,
    val dark: Boolean
) {
    companion object {
        val light = TempoPalette(
            background = hexColor("FFFFFF"),
            surface = hexColor("FFFFFF"),
            controlSurface = hexColor("F7F8FA"),
            ink = hexColor("202027"),
            muted = hexColor("62626D"),
            pink = hexColor("C51C4B"),
            primaryFill = hexColor("C51C4B"),
            onPrimary = Color.White,
            brandPink = hexColor("FF3D6E"),
            line = hexColor("ECEEF2"),
            swatchOutline = hexColor("85858F"),
            runningIndicator = hexColor("317A54"),
            cardShadow = Color.Black.copy(alpha = 0.012f),
            dark = false
        )
        val dark = TempoPalette(
            background = hexColor("0A0A0D"),
            surface = hexColor("18181F"),
            controlSurface = hexColor("24242E"),
            ink = hexColor("F4F3EE"),
            muted = hexColor("A4A4B4"),
            pink = hexColor("FF668C"),
            primaryFill = hexColor("D82051"),
            onPrimary = Color.White,
            brandPink = hexColor("FF3D6E"),
            line = hexColor("363640"),
            swatchOutline = hexColor("898995"),
            runningIndicator = hexColor("92E5BE"),
            cardShadow = Color.Transparent,
            dark = true
        )
    }
}

private val memberSurface = doubleArrayOf(0x18 / 255.0, 0x18 / 255.0, 0x1F / 255.0)

/**
 * Lifts a member color toward white until it reads against the dark card surface
 * and its 27% tint, matching TempoStyle.memberAccent on iOS.
 */
fun memberAccent(hex: String): Color {
    val source = components(hex)
    val destination = 1.0
    val target = 4.5
    var candidate = source
    for (step in 0..100) {
        val fraction = step / 100.0
        candidate = DoubleArray(3) { index -> source[index] * (1 - fraction) + destination * fraction }
        val tinted = DoubleArray(3) { index -> candidate[index] * 0.27 + memberSurface[index] * 0.73 }
        if (contrast(candidate, memberSurface) >= target && contrast(candidate, tinted) >= target) break
    }
    return Color(candidate[0].toFloat(), candidate[1].toFloat(), candidate[2].toFloat())
}

fun hexColor(hex: String): Color {
    val value = hex.trim().trimStart('#').toLongOrNull(16) ?: 0xFF3D6E
    return Color(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
        alpha = 1f
    )
}

fun Color.toHexString(): String {
    val red = (this.red * 255).toInt().coerceIn(0, 255)
    val green = (this.green * 255).toInt().coerceIn(0, 255)
    val blue = (this.blue * 255).toInt().coerceIn(0, 255)
    return "%02X%02X%02X".format(red, green, blue)
}

private fun components(hex: String): DoubleArray {
    val value = hex.trim().trimStart('#').toLongOrNull(16) ?: 0xFF3D6E
    return doubleArrayOf(
        ((value shr 16) and 255) / 255.0,
        ((value shr 8) and 255) / 255.0,
        (value and 255) / 255.0
    )
}

private fun contrast(first: DoubleArray, second: DoubleArray): Double {
    val firstValue = luminance(first)
    val secondValue = luminance(second)
    return (maxOf(firstValue, secondValue) + 0.05) / (minOf(firstValue, secondValue) + 0.05)
}

private fun luminance(rgb: DoubleArray): Double {
    val linear = rgb.map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
}

/** Variable weight instance of the bundled Unbounded font. */
private fun unboundedFamily(weight: Int): FontFamily = FontFamily(
    Font(
        R.font.unbounded,
        weight = FontWeight(weight),
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight))
    )
)

val UnboundedRegular: FontFamily by lazy { unboundedFamily(400) }
val UnboundedSemibold: FontFamily by lazy { unboundedFamily(600) }
val UnboundedBold: FontFamily by lazy { unboundedFamily(700) }
val UnboundedBlack: FontFamily by lazy { unboundedFamily(900) }

/** Countdown digits: Unbounded semibold with fixed-width figures. */
fun tempoDigits(size: Float, color: Color? = null): TextStyle = TextStyle(
    fontFamily = UnboundedSemibold,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    fontFeatureSettings = "tnum",
    color = color ?: Color.Unspecified
)

/** Mirrors the iOS tempoTime formatter: MM:SS below an hour, H:MM:SS above. */
fun tempoTime(seconds: Double): String {
    val value = maxOf(0, kotlin.math.ceil(seconds).toLong())
    return if (value >= 3600) {
        "%d:%02d:%02d".format(value / 3600, value / 60 % 60, value % 60)
    } else {
        "%02d:%02d".format(value / 60, value % 60)
    }
}

val LocalTempoPalette = staticCompositionLocalOf { TempoPalette.light }

@Composable
fun tempoPalette(): TempoPalette = LocalTempoPalette.current

@Composable
fun OshicueTempoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) TempoPalette.dark else TempoPalette.light
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.pink,
            onPrimary = palette.onPrimary,
            surface = palette.surface,
            onSurface = palette.ink,
            background = palette.background,
            onBackground = palette.ink,
            outline = palette.line,
            surfaceVariant = palette.controlSurface,
            onSurfaceVariant = palette.muted
        )
    } else {
        lightColorScheme(
            primary = palette.pink,
            onPrimary = palette.onPrimary,
            surface = palette.surface,
            onSurface = palette.ink,
            background = palette.background,
            onBackground = palette.ink,
            outline = palette.line,
            surfaceVariant = palette.controlSurface,
            onSurfaceVariant = palette.muted
        )
    }
    CompositionLocalProvider(LocalTempoPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
    }
}

/** Formats integral second counts without decimals for the accessibility label. */
fun tempoSecondsLabel(seconds: Double): String {
    val rounded = kotlin.math.round(seconds)
    return if (abs(rounded - seconds) < 0.001) rounded.toLong().toString() else seconds.toString()
}
