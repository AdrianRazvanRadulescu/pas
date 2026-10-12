package dev.pas.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Every card in the app: the theme decides its shape, outline and flat shadow. */
@Composable
fun PasCard(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = Pas.decor.card,
    contentColor: Color = contentColorFor(color),
    content: @Composable () -> Unit,
) {
    val decor = Pas.decor
    val border = if (decor.borderWidth > 0.dp) BorderStroke(decor.borderWidth, decor.borderColor) else null
    Surface(
        color = color,
        contentColor = contentColor,
        shape = shape,
        border = border,
        modifier = Modifier
            .flatShadow(shape, decor.flatShadow, decor.shadowColor)
            .then(modifier),
        content = content,
    )
}

/** The one card that carries the day's main action; some themes fill it with a gradient. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val decor = Pas.decor
    PasCard(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = decor.hero,
        modifier = modifier.background(decor.heroFill, decor.hero),
        content = content,
    )
}

private fun Modifier.flatShadow(shape: Shape, offset: Dp, color: Color): Modifier {
    if (offset == 0.dp) return this
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val offsetPx = offset.toPx()
        onDrawBehind {
            translate(offsetPx, offsetPx) { drawOutline(outline, color) }
        }
    }
}

/**
 * The screen background. Every pattern is built once per size into a single path,
 * so scrolling never redraws hundreds of shapes.
 */
fun Modifier.pasBackdrop(backdrop: Backdrop, background: Color, pattern: Color): Modifier = drawWithCache {
    val patternPath = when (backdrop) {
        Backdrop.TOPO_LINES -> topoLines(size)
        Backdrop.DOT_GRID -> dotGrid(size, spacing = 18.dp.toPx(), radius = 1.2.dp.toPx())
        Backdrop.NIGHT_SKY -> stars(size, count = 70, maxRadius = 1.6.dp.toPx())
        else -> null
    }
    val glow = when (backdrop) {
        Backdrop.NIGHT_SKY -> Brush.verticalGradient(listOf(Color(0xFF2B2456), background, Color(0xFF0F1328)))
        Backdrop.EMBER_GLOW -> Brush.radialGradient(
            listOf(Color(0x47FF6A1F), Color.Transparent),
            center = Offset(size.width * 0.9f, 0f),
            radius = size.width * 1.1f,
        )
        Backdrop.CHALK_DUST -> Brush.radialGradient(
            listOf(Color(0x14F2F1E8), Color.Transparent),
            center = Offset(size.width * 0.25f, size.height * 0.3f),
            radius = size.width * 0.8f,
        )
        else -> null
    }
    onDrawBehind {
        drawRect(background)
        glow?.let { drawRect(it) }
        patternPath?.let { path ->
            if (backdrop == Backdrop.TOPO_LINES) drawPath(path, pattern, style = Stroke(width = 1.dp.toPx()))
            else drawPath(path, pattern)
        }
    }
}

private fun topoLines(size: Size): Path {
    val path = Path()
    val lineGap = size.width / 7
    var baseline = size.height * 0.04f
    var lineIndex = 0
    while (baseline < size.height) {
        val phase = lineIndex * 0.7f
        path.moveTo(0f, baseline)
        var x = 0f
        while (x <= size.width) {
            val wave = sin(x / size.width * 2 * PI.toFloat() + phase) * lineGap * 0.35f
            path.lineTo(x, baseline + wave)
            x += 12f
        }
        baseline += lineGap
        lineIndex++
    }
    return path
}

private fun dotGrid(size: Size, spacing: Float, radius: Float): Path {
    val path = Path()
    var y = spacing / 2
    while (y < size.height) {
        var x = spacing / 2
        while (x < size.width) {
            path.addOval(Rect(Offset(x, y), radius))
            x += spacing
        }
        y += spacing
    }
    return path
}

private fun stars(size: Size, count: Int, maxRadius: Float): Path {
    val random = Random(7)
    val path = Path()
    repeat(count) {
        val center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height * 0.5f)
        path.addOval(Rect(center, maxRadius * (0.3f + random.nextFloat() * 0.7f)))
    }
    return path
}

private val dayInitials = listOf("M", "T", "W", "T", "F", "S", "S")

/** The seven days of this week, drawn as the theme's mark: trail blazes, stars, stitches, metro stations… */
@Composable
fun WeekMarks(startedDays: Set<LocalDate>, monday: LocalDate, today: LocalDate, litColor: Color, dimColor: Color) {
    val mark = Pas.decor.dayMark
    val rowModifier = if (mark == DayMark.STATION) {
        Modifier.drawBehind {
            val y = 13.dp.toPx()
            val inset = size.width / 14
            drawLine(litColor, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
    } else {
        Modifier
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = rowModifier.fillMaxWidth()) {
        for (dayIndex in 0..6) {
            val day = monday.plusDays(dayIndex.toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Canvas(Modifier.size(26.dp)) {
                    drawDayMark(mark, isLit = day in startedDays, isToday = day == today, litColor = litColor, dimColor = dimColor)
                }
                Spacer(Modifier.height(4.dp))
                Text(dayInitials[dayIndex], style = MaterialTheme.typography.labelSmall, color = dimColor)
            }
        }
    }
}

private fun DrawScope.drawDayMark(mark: DayMark, isLit: Boolean, isToday: Boolean, litColor: Color, dimColor: Color) {
    val unit = size.minDimension
    val stroke = unit * 0.09f
    when (mark) {
        DayMark.BLAZE -> {
            val plate = Rect(unit * 0.08f, unit * 0.2f, unit * 0.92f, unit * 0.8f)
            if (isLit) {
                drawRect(Color.White, plate.topLeft, plate.size)
                drawRect(litColor, Offset(plate.left, plate.top + plate.height / 3), Size(plate.width, plate.height / 3))
            } else {
                drawRect(dimColor, plate.topLeft, plate.size, style = Stroke(stroke))
            }
        }
        DayMark.TILE -> {
            val tile = Rect(unit * 0.1f, unit * 0.1f, unit * 0.9f, unit * 0.9f)
            if (isLit) drawRect(litColor, tile.topLeft, tile.size)
            drawRect(if (isLit) Color(0xFF111111) else dimColor, tile.topLeft, tile.size, style = Stroke(stroke))
        }
        DayMark.OVERPRINT -> {
            val radius = unit * 0.3f
            val first = Offset(unit * 0.38f, unit * 0.38f)
            val second = Offset(unit * 0.62f, unit * 0.62f)
            if (isLit) {
                drawCircle(Color(0xFF1D3B9C), radius, first)
                drawCircle(litColor.copy(alpha = 0.85f), radius, second)
            } else {
                drawCircle(dimColor, radius, first, style = Stroke(stroke))
                drawCircle(dimColor, radius, second, style = Stroke(stroke))
            }
        }
        DayMark.STITCH -> {
            val color = if (isLit) litColor else dimColor
            val width = if (isLit) unit * 0.16f else stroke
            drawLine(color, Offset(unit * 0.2f, unit * 0.2f), Offset(unit * 0.8f, unit * 0.8f), width, StrokeCap.Round)
            drawLine(color, Offset(unit * 0.8f, unit * 0.2f), Offset(unit * 0.2f, unit * 0.8f), width, StrokeCap.Round)
        }
        DayMark.STAR -> {
            val star = starPath(center, outerRadius = unit * 0.46f, innerRadius = unit * 0.2f)
            if (isLit) {
                drawCircle(litColor.copy(alpha = 0.25f), unit * 0.5f)
                drawPath(star, Color(0xFFFFF3D2))
            } else {
                drawPath(star, dimColor, style = Stroke(stroke * 0.7f))
            }
        }
        DayMark.EMBER -> {
            if (isLit) {
                drawCircle(Brush.radialGradient(listOf(Color(0x99FF6A1F), Color.Transparent), center, unit * 0.5f), unit * 0.5f)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFFFE9A8), Color(0xFFFF7A1F), Color(0xFFB5124A)), center, unit * 0.36f),
                    unit * 0.34f,
                )
            } else {
                drawCircle(dimColor, unit * 0.34f, style = Stroke(stroke))
            }
        }
        DayMark.CHALK -> {
            if (isLit) {
                drawArc(litColor, startAngle = -60f, sweepAngle = 330f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(unit * 0.08f, unit * 0.1f), size = Size(unit * 0.84f, unit * 0.8f))
                val tick = Path().apply {
                    moveTo(unit * 0.3f, unit * 0.52f)
                    lineTo(unit * 0.45f, unit * 0.66f)
                    lineTo(unit * 0.72f, unit * 0.32f)
                }
                drawPath(tick, Color(0xFFFFE680), style = Stroke(stroke * 1.2f, cap = StrokeCap.Round))
            } else {
                drawLine(dimColor, Offset(unit * 0.3f, unit * 0.5f), Offset(unit * 0.7f, unit * 0.5f), stroke, StrokeCap.Round)
            }
        }
        DayMark.STATION -> {
            drawCircle(if (isLit) Color.White else Color(0xFF15171A), unit * 0.36f)
            drawCircle(if (isLit) litColor else dimColor, unit * 0.36f, style = Stroke(stroke * 1.6f))
        }
    }
    if (isToday) {
        drawCircle(litColor, unit * 0.06f, Offset(center.x, size.height + unit * 0.02f))
    }
}

private fun starPath(center: Offset, outerRadius: Float, innerRadius: Float): Path = Path().apply {
    for (pointIndex in 0 until 10) {
        val radius = if (pointIndex % 2 == 0) outerRadius else innerRadius
        val angle = -PI / 2 + pointIndex * PI / 5
        val point = Offset(center.x + (radius * cos(angle)).toFloat(), center.y + (radius * sin(angle)).toFloat())
        if (pointIndex == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}
