package dev.pas.app.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pas.app.R

// The fonts are static instances cut from the Google Fonts files, so every weight renders the same on the phone and in Robolectric.
private val atkinson = FontFamily(Font(R.font.atkinson_regular, FontWeight.Normal), Font(R.font.atkinson_bold, FontWeight.Bold))
private val figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)
private val workSans = FontFamily(Font(R.font.work_sans_regular, FontWeight.Normal), Font(R.font.work_sans_semibold, FontWeight.SemiBold))
private val barlow = FontFamily(Font(R.font.barlow_regular, FontWeight.Normal), Font(R.font.barlow_semibold, FontWeight.SemiBold))
private val bigShoulders = FontFamily(Font(R.font.waymark_display))
private val archivoBlack = FontFamily(Font(R.font.archivo_black))
private val rubikMono = FontFamily(Font(R.font.rubik_mono))
private val youngSerif = FontFamily(Font(R.font.young_serif))
private val instrumentSerif = FontFamily(Font(R.font.instrument_serif))
private val bricolage = FontFamily(Font(R.font.bricolage_display))
private val caveat = FontFamily(Font(R.font.caveat_bold))
private val barlowCondensed = FontFamily(Font(R.font.barlow_condensed_bold))

/** Greeting and big numbers use [display], card titles use [title], everything else uses [body]. */
private fun typographyWith(display: FontFamily, title: FontFamily, body: FontFamily, greetingSize: TextUnit, titleSize: TextUnit): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = display),
        displayMedium = base.displayMedium.copy(fontFamily = display),
        displaySmall = base.displaySmall.copy(fontFamily = display),
        headlineLarge = base.headlineLarge.copy(fontFamily = display),
        headlineMedium = base.headlineMedium.copy(fontFamily = display, fontSize = greetingSize, lineHeight = greetingSize * 1.05f),
        headlineSmall = base.headlineSmall.copy(fontFamily = title, fontSize = titleSize, lineHeight = titleSize * 1.15f),
        titleLarge = base.titleLarge.copy(fontFamily = body),
        titleMedium = base.titleMedium.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = body, fontSize = 17.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = body),
    )
}

fun typographyOf(style: PasStyle): Typography = when (style) {
    PasStyle.WAYMARK -> typographyWith(bigShoulders, bigShoulders, atkinson, greetingSize = 40.sp, titleSize = 30.sp)
    PasStyle.STUDIO -> typographyWith(archivoBlack, archivoBlack, workSans, greetingSize = 32.sp, titleSize = 23.sp)
    PasStyle.RISOGRAPH -> typographyWith(rubikMono, rubikMono, workSans, greetingSize = 25.sp, titleSize = 18.sp)
    PasStyle.EMBROIDERY -> typographyWith(youngSerif, youngSerif, atkinson, greetingSize = 30.sp, titleSize = 23.sp)
    PasStyle.EVENING_SKY -> typographyWith(instrumentSerif, instrumentSerif, figtree, greetingSize = 36.sp, titleSize = 28.sp)
    PasStyle.EMBERS -> typographyWith(bricolage, bricolage, figtree, greetingSize = 40.sp, titleSize = 29.sp)
    PasStyle.CHALKBOARD -> typographyWith(caveat, caveat, atkinson, greetingSize = 42.sp, titleSize = 32.sp)
    PasStyle.METRO -> typographyWith(barlowCondensed, barlowCondensed, barlow, greetingSize = 40.sp, titleSize = 30.sp)
}

fun colorSchemeOf(style: PasStyle): ColorScheme = when (style) {
    PasStyle.WAYMARK -> lightColorScheme(
        primary = Color(0xFF1F4D36), onPrimary = Color.White,
        primaryContainer = Color(0xFF1F4D36), onPrimaryContainer = Color.White,
        secondary = Color(0xFFD3271F), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF6DAD7), onSecondaryContainer = Color(0xFF5C100C),
        tertiary = Color(0xFF3E6B8A), onTertiary = Color.White,
        background = Color(0xFFE4E8E3), onBackground = Color(0xFF1B2420),
        surface = Color.White, onSurface = Color(0xFF1B2420),
        surfaceVariant = Color(0xFFD5DCD5), onSurfaceVariant = Color(0xFF56645B),
        surfaceContainerLow = Color(0xFFF3F5F2), surfaceContainer = Color(0xFFECEFEB),
        surfaceContainerHigh = Color(0xFFE2E7E1), surfaceContainerHighest = Color(0xFFD9DFD8),
        outline = Color(0xFF1B2420), outlineVariant = Color(0xFFC3CCC4),
    )
    PasStyle.STUDIO -> lightColorScheme(
        primary = Color(0xFF2F4BDB), onPrimary = Color.White,
        primaryContainer = Color(0xFF2F4BDB), onPrimaryContainer = Color.White,
        secondary = Color(0xFFFFD84D), onSecondary = Color(0xFF111111),
        secondaryContainer = Color(0xFFFFE98F), onSecondaryContainer = Color(0xFF111111),
        tertiary = Color(0xFFFF5B3A), onTertiary = Color.White,
        background = Color(0xFFF2F3F7), onBackground = Color(0xFF111111),
        surface = Color.White, onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFE3E5EE), onSurfaceVariant = Color(0xFF4A4D57),
        surfaceContainerLow = Color(0xFFF7F8FB), surfaceContainer = Color(0xFFEDEEF4),
        surfaceContainerHigh = Color(0xFFE5E7EF), surfaceContainerHighest = Color(0xFFDDE0EA),
        outline = Color(0xFF111111), outlineVariant = Color(0xFFC9CCD8),
    )
    PasStyle.RISOGRAPH -> lightColorScheme(
        primary = Color(0xFF1D3B9C), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE14D), onPrimaryContainer = Color(0xFF1D3B9C),
        secondary = Color(0xFFFF4FA3), onSecondary = Color(0xFF1D1446),
        secondaryContainer = Color(0xFFFFD3E8), onSecondaryContainer = Color(0xFF1D3B9C),
        tertiary = Color(0xFFFF4FA3), onTertiary = Color(0xFF1D1446),
        background = Color(0xFFF4F3EE), onBackground = Color(0xFF1D3B9C),
        surface = Color.White, onSurface = Color(0xFF1D3B9C),
        surfaceVariant = Color(0xFFE6E6F2), onSurfaceVariant = Color(0xFF4F63B5),
        surfaceContainerLow = Color(0xFFF9F9F6), surfaceContainer = Color(0xFFEEEEF4),
        surfaceContainerHigh = Color(0xFFE6E7F0), surfaceContainerHighest = Color(0xFFDDDFEC),
        outline = Color(0xFF1D3B9C), outlineVariant = Color(0xFFC9CDE6),
    )
    PasStyle.EMBROIDERY -> lightColorScheme(
        primary = Color(0xFFB3121F), onPrimary = Color.White,
        primaryContainer = Color(0xFF161414), onPrimaryContainer = Color(0xFFF6F2EA),
        secondary = Color(0xFFD81E2C), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5D5D2), onSecondaryContainer = Color(0xFF5E0A10),
        tertiary = Color(0xFF2E5E3A), onTertiary = Color.White,
        background = Color(0xFFF0EFEA), onBackground = Color(0xFF161414),
        surface = Color.White, onSurface = Color(0xFF161414),
        surfaceVariant = Color(0xFFE2E0DA), onSurfaceVariant = Color(0xFF5C5753),
        surfaceContainerLow = Color(0xFFF7F6F3), surfaceContainer = Color(0xFFEAE9E4),
        surfaceContainerHigh = Color(0xFFE3E1DB), surfaceContainerHighest = Color(0xFFDAD8D1),
        outline = Color(0xFF161414), outlineVariant = Color(0xFFD3D0C9),
    )
    PasStyle.EVENING_SKY -> darkColorScheme(
        primary = Color(0xFFFFB27A), onPrimary = Color(0xFF2A1709),
        primaryContainer = Color(0xFF2E2A62), onPrimaryContainer = Color(0xFFFFF3D2),
        secondary = Color(0xFFFFB27A), onSecondary = Color(0xFF2A1709),
        secondaryContainer = Color(0xFF3D3170), onSecondaryContainer = Color(0xFFFFF3D2),
        tertiary = Color(0xFFA7B0D8), onTertiary = Color(0xFF151A36),
        background = Color(0xFF151A36), onBackground = Color(0xFFFFF3D2),
        // Translucent, so the stars of the backdrop show through the cards.
        surface = Color(0x1AFFFFFF), onSurface = Color(0xFFFFF3D2),
        surfaceVariant = Color(0xFF2A2F5A), onSurfaceVariant = Color(0xFFA7B0D8),
        surfaceContainerLow = Color(0xFF1A1F42), surfaceContainer = Color(0xFF1F2449),
        surfaceContainerHigh = Color(0xFF252A52), surfaceContainerHighest = Color(0xFF2C325D),
        outline = Color(0xFF4A5285), outlineVariant = Color(0xFF2E3462),
    )
    PasStyle.EMBERS -> darkColorScheme(
        primary = Color(0xFFFF6A1F), onPrimary = Color(0xFF1A0800),
        primaryContainer = Color(0xFFE2471F), onPrimaryContainer = Color.White,
        secondary = Color(0xFFFFE9DC), onSecondary = Color(0xFF3A0D06),
        secondaryContainer = Color(0xFF4A1A12), onSecondaryContainer = Color(0xFFFFE9DC),
        tertiary = Color(0xFFFFB347), onTertiary = Color(0xFF2A1400),
        background = Color(0xFF140C0B), onBackground = Color(0xFFFFE9DC),
        surface = Color(0xFF221514), onSurface = Color(0xFFFFE9DC),
        surfaceVariant = Color(0xFF33201D), onSurfaceVariant = Color(0xFFC79E8E),
        surfaceContainerLow = Color(0xFF1C1110), surfaceContainer = Color(0xFF241615),
        surfaceContainerHigh = Color(0xFF2D1C1A), surfaceContainerHighest = Color(0xFF382321),
        outline = Color(0xFF6A443B), outlineVariant = Color(0xFF3A2622),
    )
    PasStyle.CHALKBOARD -> darkColorScheme(
        primary = Color(0xFFFFE680), onPrimary = Color(0xFF1E3A32),
        primaryContainer = Color(0xFF2A5045), onPrimaryContainer = Color(0xFFF2F1E8),
        secondary = Color(0xFFF7A8C4), onSecondary = Color(0xFF2A1520),
        secondaryContainer = Color(0xFF3B5F55), onSecondaryContainer = Color(0xFFF2F1E8),
        tertiary = Color(0xFF9FD4F5), onTertiary = Color(0xFF0E2A3A),
        background = Color(0xFF1E3A32), onBackground = Color(0xFFF2F1E8),
        surface = Color(0xFF24453B), onSurface = Color(0xFFF2F1E8),
        surfaceVariant = Color(0xFF2E5248), onSurfaceVariant = Color(0xFFB9C7BF),
        surfaceContainerLow = Color(0xFF214037), surfaceContainer = Color(0xFF1A322B),
        surfaceContainerHigh = Color(0xFF2A4F44), surfaceContainerHighest = Color(0xFF30574B),
        outline = Color(0xFF8FA89E), outlineVariant = Color(0xFF3A6055),
    )
    PasStyle.METRO -> darkColorScheme(
        primary = Color(0xFFFFC20E), onPrimary = Color(0xFF15171A),
        primaryContainer = Color(0xFF0072BC), onPrimaryContainer = Color.White,
        secondary = Color(0xFFFFC20E), onSecondary = Color(0xFF15171A),
        secondaryContainer = Color(0xFF2A2D31), onSecondaryContainer = Color(0xFFF4F5F6),
        tertiary = Color(0xFFFF4D5A), onTertiary = Color.White,
        background = Color(0xFF15171A), onBackground = Color(0xFFF4F5F6),
        surface = Color(0xFF212428), onSurface = Color(0xFFF4F5F6),
        surfaceVariant = Color(0xFF2C3035), onSurfaceVariant = Color(0xFF9AA1A9),
        surfaceContainerLow = Color(0xFF1B1D21), surfaceContainer = Color(0xFF1F2226),
        surfaceContainerHigh = Color(0xFF26292E), surfaceContainerHighest = Color(0xFF2E3237),
        outline = Color(0xFF4A4F56), outlineVariant = Color(0xFF33373C),
    )
}

/** The trail sign of Waymark: a plate with a pointed right end. */
private val signpostShape = GenericShape { size, _ ->
    val tipWidth = size.height * 0.1f
    moveTo(0f, 0f)
    lineTo(size.width - tipWidth, 0f)
    lineTo(size.width, size.height / 2)
    lineTo(size.width - tipWidth, size.height)
    lineTo(0f, size.height)
    close()
}

private val noColor = Color.Transparent

fun decorOf(style: PasStyle): PasDecor = when (style) {
    PasStyle.WAYMARK -> PasDecor(
        card = RoundedCornerShape(6.dp), hero = signpostShape, field = RoundedCornerShape(4.dp), pill = RoundedCornerShape(4.dp),
        borderWidth = 0.dp, borderColor = noColor, flatShadow = 0.dp, shadowColor = noColor,
        heroFill = SolidColor(Color(0xFF1F4D36)),
        backdrop = Backdrop.TOPO_LINES, dayMark = DayMark.BLAZE, labelsInCaps = true, logo = R.drawable.logo_waymark,
    )
    PasStyle.STUDIO -> PasDecor(
        card = RoundedCornerShape(18.dp), hero = RoundedCornerShape(22.dp), field = RoundedCornerShape(14.dp), pill = RoundedCornerShape(14.dp),
        borderWidth = 2.dp, borderColor = Color(0xFF111111), flatShadow = 4.dp, shadowColor = Color(0xFF111111),
        heroFill = SolidColor(Color(0xFF2F4BDB)),
        backdrop = Backdrop.DOT_GRID, dayMark = DayMark.TILE, labelsInCaps = true, logo = R.drawable.logo_studio,
    )
    PasStyle.RISOGRAPH -> PasDecor(
        card = RoundedCornerShape(2.dp), hero = RoundedCornerShape(2.dp), field = RoundedCornerShape(2.dp), pill = RoundedCornerShape(50),
        borderWidth = 0.dp, borderColor = noColor, flatShadow = 6.dp, shadowColor = Color(0xFFFF4FA3),
        heroFill = SolidColor(Color(0xFFFFE14D)),
        backdrop = Backdrop.DOT_GRID, dayMark = DayMark.OVERPRINT, labelsInCaps = true, logo = R.drawable.logo_risograph,
    )
    PasStyle.EMBROIDERY -> PasDecor(
        card = RoundedCornerShape(4.dp), hero = CutCornerShape(14.dp), field = RoundedCornerShape(4.dp), pill = CutCornerShape(50),
        borderWidth = 1.5.dp, borderColor = Color(0xFF161414), flatShadow = 0.dp, shadowColor = noColor,
        heroFill = SolidColor(Color(0xFF161414)),
        backdrop = Backdrop.PLAIN, dayMark = DayMark.STITCH, labelsInCaps = false, logo = R.drawable.logo_embroidery,
    )
    PasStyle.EVENING_SKY -> PasDecor(
        card = RoundedCornerShape(26.dp), hero = RoundedCornerShape(30.dp), field = RoundedCornerShape(18.dp), pill = RoundedCornerShape(50),
        borderWidth = 0.dp, borderColor = noColor, flatShadow = 0.dp, shadowColor = noColor,
        heroFill = Brush.verticalGradient(listOf(Color(0xFF3F2F78), Color(0xFF26285A))),
        backdrop = Backdrop.NIGHT_SKY, dayMark = DayMark.STAR, labelsInCaps = false, logo = R.drawable.logo_evening_sky,
    )
    PasStyle.EMBERS -> PasDecor(
        card = RoundedCornerShape(28.dp), hero = RoundedCornerShape(36.dp), field = RoundedCornerShape(20.dp), pill = RoundedCornerShape(50),
        borderWidth = 0.dp, borderColor = noColor, flatShadow = 0.dp, shadowColor = noColor,
        heroFill = Brush.linearGradient(listOf(Color(0xFFFF7A1F), Color(0xFFE2471F), Color(0xFFB5124A))),
        backdrop = Backdrop.EMBER_GLOW, dayMark = DayMark.EMBER, labelsInCaps = false, logo = R.drawable.logo_embers,
    )
    PasStyle.CHALKBOARD -> PasDecor(
        card = RoundedCornerShape(10.dp), hero = RoundedCornerShape(14.dp), field = RoundedCornerShape(8.dp), pill = RoundedCornerShape(50),
        borderWidth = 1.5.dp, borderColor = Color(0x59F2F1E8), flatShadow = 0.dp, shadowColor = noColor,
        heroFill = SolidColor(Color(0xFF2A5045)),
        backdrop = Backdrop.CHALK_DUST, dayMark = DayMark.CHALK, labelsInCaps = false, logo = R.drawable.logo_chalkboard,
    )
    PasStyle.METRO -> PasDecor(
        card = RoundedCornerShape(4.dp), hero = RoundedCornerShape(4.dp), field = RoundedCornerShape(4.dp), pill = RoundedCornerShape(6.dp),
        borderWidth = 0.dp, borderColor = noColor, flatShadow = 0.dp, shadowColor = noColor,
        heroFill = SolidColor(Color(0xFF0072BC)),
        backdrop = Backdrop.PLAIN, dayMark = DayMark.STATION, labelsInCaps = true, logo = R.drawable.logo_metro,
    )
}
