package ru.daybyday.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Original vector emblems; every milestone has its own motif, generated locally. */
@Composable
fun AchievementArt(achievement: UiAchievement, modifier: Modifier = Modifier, unlocked: Boolean = achievement.unlockedDate != null) {
    Canvas(modifier.semantics { contentDescription = "Иллюстрация награды «${achievement.title}»" }) {
        val s = size.minDimension / 120f
        val x = (size.width - 120f * s) / 2
        val y = (size.height - 120f * s) / 2
        val palette = if (!unlocked) listOf(Color(0xFF535565), Color(0xFF383B49), Color(0xFF8A8C9E))
        else when {
            achievement.days >= 365 -> listOf(Color(0xFFBCA2FF), Color(0xFF6944BE), Color(0xFFF5D591))
            achievement.days >= 30 -> listOf(Color(0xFF8BB8FF), Color(0xFF4559A8), Color(0xFFA9F1E5))
            else -> listOf(Color(0xFF98F4DF), Color(0xFF298F83), Color(0xFFE4FFF7))
        }
        val bright = palette[0]
        val deep = palette[1]
        val ink = palette[2]
        scale(s, pivot = Offset.Zero) {
            translate(x / s, y / s) {
                drawCircle(Brush.radialGradient(listOf(bright.copy(alpha = if (unlocked) .22f else .04f), Color.Transparent), center = Offset(60f, 58f), radius = 57f), radius = 57f, center = Offset(60f, 58f))
                drawOval(Color.Black.copy(alpha = .15f), topLeft = Offset(27f, 100f), size = Size(66f, 8f))
                val crest = Path().apply {
                    moveTo(60f, 13f); cubicTo(74f, 23f, 92f, 20f, 96f, 34f)
                    lineTo(92f, 73f); cubicTo(88f, 88f, 72f, 97f, 60f, 103f)
                    cubicTo(48f, 97f, 32f, 88f, 28f, 73f); lineTo(24f, 34f)
                    cubicTo(28f, 20f, 46f, 23f, 60f, 13f); close()
                }
                drawPath(crest, Brush.linearGradient(listOf(bright, deep), Offset(30f, 16f), Offset(91f, 100f)))
                drawPath(crest, bright.copy(alpha = .75f), style = Stroke(1.5f))
                val inner = Path().apply {
                    moveTo(60f, 24f); lineTo(85f, 36f); lineTo(82f, 70f)
                    quadraticBezierTo(78f, 84f, 60f, 93f); quadraticBezierTo(42f, 84f, 38f, 70f)
                    lineTo(35f, 36f); close()
                }
                drawPath(inner, Color.Black.copy(alpha = .13f))
                drawLine(Color.White.copy(alpha = .28f), Offset(33f, 35f), Offset(36f, 62f), 2f, StrokeCap.Round)
                milestoneGlyph(achievement.days.toInt(), ink)
                if (unlocked) {
                    sparkle(Offset(100f, 26f), 5f, bright)
                    sparkle(Offset(18f, 69f), 3.5f, ink.copy(alpha = .8f))
                    drawCircle(bright.copy(alpha = .7f), 1.5f, Offset(102f, 80f))
                    drawCircle(ink.copy(alpha = .6f), 1f, Offset(21f, 19f))
                }
            }
        }
    }
}

private fun DrawScope.sparkle(at: Offset, radius: Float, color: Color) {
    drawPath(Path().apply {
        moveTo(at.x, at.y - radius); quadraticBezierTo(at.x + radius * .2f, at.y - radius * .2f, at.x + radius, at.y)
        quadraticBezierTo(at.x + radius * .2f, at.y + radius * .2f, at.x, at.y + radius)
        quadraticBezierTo(at.x - radius * .2f, at.y + radius * .2f, at.x - radius, at.y)
        quadraticBezierTo(at.x - radius * .2f, at.y - radius * .2f, at.x, at.y - radius); close()
    }, color)
}

private fun DrawScope.star(at: Offset, radius: Float, points: Int, color: Color) {
    drawPath(Path().apply {
        repeat(points * 2) { i ->
            val r = if (i % 2 == 0) radius else radius * .44f
            val a = i * PI / points - PI / 2
            val xx = at.x + cos(a).toFloat() * r
            val yy = at.y + sin(a).toFloat() * r
            if (i == 0) moveTo(xx, yy) else lineTo(xx, yy)
        }
        close()
    }, color)
}

private fun DrawScope.leaf(from: Offset, tip: Offset, color: Color) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    drawPath(Path().apply {
        moveTo(from.x, from.y)
        cubicTo(from.x + dx * .1f - dy * .4f, from.y + dy * .1f + dx * .4f, tip.x - dx * .2f - dy * .2f, tip.y - dy * .2f + dx * .2f, tip.x, tip.y)
        cubicTo(tip.x - dx * .1f + dy * .3f, tip.y - dy * .1f - dx * .3f, from.x + dx * .4f + dy * .4f, from.y + dy * .4f - dx * .4f, from.x, from.y)
    }, color)
}

private fun DrawScope.milestoneGlyph(days: Int, color: Color) {
    when (days) {
        1 -> {
            drawLine(color, Offset(60f, 77f), Offset(60f, 52f), 3f, StrokeCap.Round)
            leaf(Offset(60f, 62f), Offset(76f, 42f), color)
            leaf(Offset(60f, 68f), Offset(44f, 51f), color.copy(alpha = .85f))
        }
        3 -> {
            drawPath(Path().apply {
                moveTo(43f, 77f); cubicTo(64f, 80f, 80f, 58f, 77f, 39f)
                cubicTo(67f, 46f, 44f, 44f, 43f, 77f); close()
            }, color)
            drawLine(Color(0xFF338F85), Offset(47f, 72f), Offset(70f, 50f), 1.5f, StrokeCap.Round)
        }
        7 -> star(Offset(60f, 59f), 22f, 5, color)
        14 -> {
            drawLine(color, Offset(60f, 78f), Offset(60f, 48f), 2.5f)
            leaf(Offset(60f, 60f), Offset(78f, 39f), color)
            leaf(Offset(60f, 72f), Offset(41f, 50f), color)
            leaf(Offset(60f, 73f), Offset(79f, 59f), color.copy(alpha = .8f))
        }
        21 -> {
            drawPath(Path().apply { moveTo(39f, 77f); lineTo(59f, 40f); lineTo(82f, 77f); close() }, color)
            drawPath(Path().apply { moveTo(53f, 52f); lineTo(59f, 40f); lineTo(67f, 54f); lineTo(60f, 51f); lineTo(56f, 56f); close() }, Color.White.copy(alpha = .65f))
        }
        30 -> {
            drawCircle(color, 13f, Offset(60f, 59f))
            repeat(10) { i ->
                val a = i * PI * 2 / 10
                drawLine(color, Offset(60f + 18f * cos(a).toFloat(), 59f + 18f * sin(a).toFloat()), Offset(60f + 23f * cos(a).toFloat(), 59f + 23f * sin(a).toFloat()), 2.5f, StrokeCap.Round)
            }
        }
        45 -> {
            drawCircle(color, 22f, Offset(60f, 59f), style = Stroke(2f))
            drawPath(Path().apply { moveTo(60f, 37f); lineTo(69f, 59f); lineTo(60f, 81f); lineTo(51f, 59f); close() }, color)
            drawCircle(Color(0xFF5470AF), 3f, Offset(60f, 59f))
        }
        60 -> {
            drawPath(Path().apply {
                moveTo(70f, 38f); cubicTo(44f, 33f, 34f, 67f, 56f, 80f)
                cubicTo(65f, 85f, 76f, 81f, 81f, 72f)
                cubicTo(54f, 77f, 48f, 51f, 70f, 38f); close()
            }, color)
            sparkle(Offset(75f, 48f), 7f, color)
        }
        90 -> {
            drawPath(Path().apply { moveTo(36f, 78f); lineTo(52f, 47f); lineTo(63f, 64f); lineTo(72f, 38f); lineTo(87f, 78f); close() }, color)
            drawLine(Color(0xFF5571AD), Offset(52f, 47f), Offset(66f, 78f), 2f)
        }
        100 -> {
            star(Offset(60f, 60f), 24f, 6, color)
            drawCircle(Color(0xFF526EAD), 10f, Offset(60f, 60f))
            sparkle(Offset(60f, 60f), 7f, color)
        }
        180 -> {
            drawCircle(color, 18f, Offset(60f, 59f), style = Stroke(4f))
            drawArc(color, -90f, 180f, true, Offset(42f, 41f), Size(36f, 36f))
            repeat(8) { i -> rotate(i * 45f, Offset(60f, 59f)) { drawLine(color, Offset(60f, 31f), Offset(60f, 35f), 2f, StrokeCap.Round) } }
        }
        270 -> {
            repeat(3) { n ->
                drawPath(Path().apply { moveTo(39f, 48f + n * 12); cubicTo(53f, 35f + n * 12, 66f, 62f + n * 12, 81f, 48f + n * 12) }, color.copy(alpha = 1f - n * .15f), style = Stroke(4f, cap = StrokeCap.Round))
            }
        }
        365 -> {
            drawPath(Path().apply { moveTo(39f, 48f); lineTo(50f, 59f); lineTo(60f, 38f); lineTo(70f, 59f); lineTo(81f, 48f); lineTo(77f, 75f); lineTo(43f, 75f); close() }, color)
            drawLine(color, Offset(45f, 80f), Offset(75f, 80f), 3f, StrokeCap.Round)
            listOf(Offset(39f, 45f), Offset(60f, 35f), Offset(81f, 45f)).forEach { drawCircle(color, 2.5f, it) }
        }
        500 -> {
            drawPath(Path().apply { moveTo(45f, 42f); lineTo(75f, 42f); lineTo(84f, 55f); lineTo(60f, 84f); lineTo(36f, 55f); close() }, color)
            val dark = Color(0xFF8F6AB7)
            drawLine(dark, Offset(36f, 55f), Offset(84f, 55f), 1.5f)
            drawLine(dark, Offset(52f, 55f), Offset(60f, 84f), 1.5f)
            drawLine(dark, Offset(68f, 55f), Offset(60f, 84f), 1.5f)
            drawLine(dark, Offset(45f, 42f), Offset(52f, 55f), 1.5f)
            drawLine(dark, Offset(75f, 42f), Offset(68f, 55f), 1.5f)
        }
        730 -> {
            leaf(Offset(56f, 72f), Offset(35f, 40f), color)
            leaf(Offset(64f, 72f), Offset(85f, 40f), color)
            star(Offset(60f, 68f), 13f, 5, color)
        }
        else -> {
            drawCircle(color, 9f, Offset(60f, 59f))
            repeat(3) { i -> rotate(i * 60f, Offset(60f, 59f)) { drawOval(color, Offset(32f, 48f), Size(56f, 22f), style = Stroke(1.8f)) } }
            drawCircle(color, 3f, Offset(84f, 48f))
        }
    }
}
