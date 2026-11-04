package com.jim.realcash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The few glyphs the shell needs. Drawn on a Canvas to avoid pulling in an icon library. */
enum class RcIcon { WALLET, LEDGER, SETTINGS, BACK, CLOSE, MORE }

/**
 * Draws [icon] as a 24dp line glyph in [tint] (the current content color by default).
 * Decorative: callers put the meaning in an adjacent label or a contentDescription on the clickable parent.
 */
@Composable
fun RcIconGlyph(icon: RcIcon, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.085f, cap = StrokeCap.Round)
        when (icon) {
            RcIcon.MORE -> {
                for (x in listOf(0.22f, 0.5f, 0.78f)) drawCircle(tint, radius = s * 0.065f, center = Offset(s * x, s * 0.5f))
            }
            RcIcon.WALLET -> {
                drawRoundRect(tint, Offset(s * 0.12f, s * 0.25f), Size(s * 0.76f, s * 0.56f), CornerRadius(s * 0.1f), style = stroke)
                drawLine(tint, Offset(s * 0.2f, s * 0.25f), Offset(s * 0.68f, s * 0.12f), stroke.width, StrokeCap.Round)
                drawCircle(tint, radius = s * 0.055f, center = Offset(s * 0.7f, s * 0.53f))
            }
            RcIcon.LEDGER -> {
                drawRoundRect(tint, Offset(s * 0.2f, s * 0.12f), Size(s * 0.6f, s * 0.76f), CornerRadius(s * 0.06f), style = stroke)
                for (i in 0..2) {
                    val y = s * (0.34f + i * 0.17f)
                    drawLine(tint, Offset(s * 0.33f, y), Offset(s * 0.67f, y), stroke.width * 0.8f, StrokeCap.Round)
                }
            }
            RcIcon.SETTINGS -> {
                val c = Offset(s / 2, s / 2)
                drawCircle(tint, radius = s * 0.24f, center = c, style = stroke)
                drawCircle(tint, radius = s * 0.08f, center = c, style = stroke)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val from = Offset(c.x + cos(a).toFloat() * s * 0.29f, c.y + sin(a).toFloat() * s * 0.29f)
                    val to = Offset(c.x + cos(a).toFloat() * s * 0.4f, c.y + sin(a).toFloat() * s * 0.4f)
                    drawLine(tint, from, to, stroke.width * 1.3f, StrokeCap.Round)
                }
            }
            RcIcon.BACK -> {
                drawLine(tint, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.82f, s * 0.5f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.45f, s * 0.25f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.45f, s * 0.75f), stroke.width, StrokeCap.Round)
            }
            RcIcon.CLOSE -> {
                drawLine(tint, Offset(s * 0.25f, s * 0.25f), Offset(s * 0.75f, s * 0.75f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.75f, s * 0.25f), Offset(s * 0.25f, s * 0.75f), stroke.width, StrokeCap.Round)
            }
        }
    }
}
