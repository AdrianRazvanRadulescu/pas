package dev.pas.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Each theme is either light or dark on purpose: its colours are part of its personality, not a mode. */
enum class PasStyle(val key: String, val title: String, val tagline: String, val isDark: Boolean) {
    WAYMARK("waymark", "Waymark", "Mountain trail. Every day you start is a mark on the path.", isDark = false),
    STUDIO("studio", "Studio", "Thick outlines, buttons that really press down.", isDark = false),
    RISOGRAPH("risograph", "Risograph", "A poster printed in neon pink and blue, with offset shadows.", isDark = false),
    EMBROIDERY("embroidery", "Embroidery", "Red and black on white, cross-stitch.", isDark = false),
    EVENING_SKY("evening_sky", "Evening Sky", "Sunset violet, stars lighting up one by one.", isDark = true),
    EMBERS("embers", "Embers", "Glowing coal: molten orange on warm black.", isDark = true),
    CHALKBOARD("chalkboard", "Chalkboard", "Chalk on a green board, handwritten.", isDark = true),
    METRO("metro", "Metro", "Colored lines, stations, signage lettering.", isDark = true);

    companion object {
        val DEFAULT = WAYMARK

        fun fromKey(key: String): PasStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

enum class Backdrop { PLAIN, TOPO_LINES, DOT_GRID, NIGHT_SKY, EMBER_GLOW, CHALK_DUST }

enum class DayMark { BLAZE, TILE, OVERPRINT, STITCH, STAR, EMBER, CHALK, STATION }

/** What a theme changes beyond colours and fonts: the shapes, the outline and flat shadow of cards, the backdrop and the week marks. */
data class PasDecor(
    val card: Shape,
    val hero: Shape,
    val field: Shape,
    val pill: Shape,
    val borderWidth: Dp,
    val borderColor: Color,
    val flatShadow: Dp,
    val shadowColor: Color,
    val heroFill: Brush,
    val backdrop: Backdrop,
    val dayMark: DayMark,
    val labelsInCaps: Boolean,
    @DrawableRes val logo: Int,
)

private val LocalPasDecor = staticCompositionLocalOf { decorOf(PasStyle.DEFAULT) }

object Pas {
    val decor: PasDecor
        @Composable @ReadOnlyComposable get() = LocalPasDecor.current
}

val quoteTextStyle: TextStyle
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 27.sp)

@Composable
fun PasTheme(style: PasStyle = PasStyle.DEFAULT, content: @Composable () -> Unit) {
    val colorScheme = remember(style) { colorSchemeOf(style) }
    val typography = remember(style) { typographyOf(style) }
    val decor = remember(style) { decorOf(style) }
    CompositionLocalProvider(LocalPasDecor provides decor) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
