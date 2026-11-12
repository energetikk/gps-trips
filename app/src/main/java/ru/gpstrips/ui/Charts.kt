package ru.gpstrips.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Мини-график скорости по точкам поездки.
 * Ось Y нормируется на максимум, поэтому выбросы приёмника не ломают картинку.
 */
@Composable
fun SpeedChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = P.accent,
    showFill: Boolean = true
) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = w / (values.size - 1).toFloat()

        fun x(i: Int) = i * stepX
        fun y(v: Float) = h - (v / maxV) * h * 0.92f

        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }

        if (showFill) {
            val fill = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = h
                )
            )
        }

        drawPath(
            line,
            color = color,
            style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // отметка максимума
        val maxIdx = values.indexOfFirst { it == maxV }
        if (maxIdx >= 0) {
            drawCircle(color.copy(alpha = 0.9f), radius = 3.5f, center = Offset(x(maxIdx), y(maxV)))
        }
    }
}

/** Полоса-«гистограмма» по дням: высота = расстояние за день. */
@Composable
fun DayBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = P.accent2
) {
    if (values.isEmpty()) {
        Box(modifier)
        return
    }
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val gap = 4f
        val barW = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val bh = (v / maxV) * size.height
            drawRoundRect(
                color = if (v <= 0f) color.copy(alpha = 0.15f) else color.copy(alpha = 0.85f),
                topLeft = Offset(i * (barW + gap), size.height - bh),
                size = Size(barW, bh.coerceAtLeast(2f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
            )
        }
    }
}
