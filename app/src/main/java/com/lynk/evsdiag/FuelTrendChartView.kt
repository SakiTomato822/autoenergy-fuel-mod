package com.lynk.dvrprobe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class FuelTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB74D")
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334955")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8D3D8")
        textSize = 30f
    }

    private var points: List<FuelTrendPoint> = emptyList()

    fun setPoints(newPoints: List<FuelTrendPoint>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val left = 50f
        val top = 24f
        val right = w - 20f
        val bottom = h - 36f

        val rows = 4
        repeat(rows + 1) { index ->
            val y = top + (bottom - top) * index / rows
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (points.size < 2) {
            canvas.drawText("等待采样点...", left, h / 2f, textPaint)
            return
        }

        val values = points.map { it.value }
        var minValue = values.minOrNull() ?: 0f
        var maxValue = values.maxOrNull() ?: 0f
        if (kotlin.math.abs(maxValue - minValue) < 0.1f) {
            minValue -= 1f
            maxValue += 1f
        }

        canvas.drawText(String.format("%.1f", maxValue), left, top + 24f, textPaint)
        canvas.drawText(String.format("%.1f", minValue), left, bottom, textPaint)

        val curve = Path()
        val coordinates = points.mapIndexed { index, point ->
            mapX(index, points.size, left, right) to mapY(point.value, minValue, maxValue, top, bottom)
        }
        curve.addMonotoneCubicThrough(coordinates)
        canvas.drawPath(curve, linePaint)
    }

    private fun mapX(index: Int, total: Int, left: Float, right: Float): Float {
        if (total <= 1) return left
        return left + (right - left) * index / (total - 1).toFloat()
    }

    private fun mapY(value: Float, minValue: Float, maxValue: Float, top: Float, bottom: Float): Float {
        val normalized = (value - minValue) / max(0.001f, maxValue - minValue)
        val clamped = min(1f, max(0f, normalized))
        return bottom - (bottom - top) * clamped
    }
}

/** Shape-preserving cubic interpolation: smoother than a hard polyline without overshoot. */
internal fun Path.addMonotoneCubicThrough(points: List<Pair<Float, Float>>, startNewPath: Boolean = true) {
    if (points.isEmpty()) return
    if (startNewPath) moveTo(points.first().first, points.first().second)
    else lineTo(points.first().first, points.first().second)
    if (points.size < 2) return

    val intervals = points.lastIndex
    val widths = FloatArray(intervals)
    val slopes = FloatArray(intervals)
    for (index in 0 until intervals) {
        val width = points[index + 1].first - points[index].first
        widths[index] = width
        slopes[index] = if (width > 0f) {
            (points[index + 1].second - points[index].second) / width
        } else 0f
    }
    val tangents = FloatArray(points.size)
    tangents[0] = slopes.first()
    tangents[points.lastIndex] = slopes.last()
    for (index in 1 until points.lastIndex) {
        val before = slopes[index - 1]
        val after = slopes[index]
        tangents[index] = if (before == 0f || after == 0f || before * after <= 0f) {
            0f
        } else {
            val previousWidth = widths[index - 1].coerceAtLeast(0f)
            val nextWidth = widths[index].coerceAtLeast(0f)
            val w1 = 2f * nextWidth + previousWidth
            val w2 = nextWidth + 2f * previousWidth
            (w1 + w2) / (w1 / before + w2 / after)
        }
    }
    for (index in 0 until intervals) {
        val slope = slopes[index]
        if (widths[index] <= 0f || slope == 0f) {
            tangents[index] = 0f
            tangents[index + 1] = 0f
            continue
        }
        if (tangents[index] * slope < 0f) tangents[index] = 0f
        if (tangents[index + 1] * slope < 0f) tangents[index + 1] = 0f
        val alpha = tangents[index] / slope
        val beta = tangents[index + 1] / slope
        val magnitude = alpha * alpha + beta * beta
        if (magnitude > 9f) {
            val scale = 3f / kotlin.math.sqrt(magnitude)
            tangents[index] = scale * alpha * slope
            tangents[index + 1] = scale * beta * slope
        }
    }
    for (index in 0 until intervals) {
        val start = points[index]
        val end = points[index + 1]
        val width = widths[index]
        if (width <= 0f) {
            lineTo(end.first, end.second)
        } else {
            cubicTo(
                start.first + width / 3f,
                start.second + tangents[index] * width / 3f,
                end.first - width / 3f,
                end.second - tangents[index + 1] * width / 3f,
                end.first,
                end.second,
            )
        }
    }
}
