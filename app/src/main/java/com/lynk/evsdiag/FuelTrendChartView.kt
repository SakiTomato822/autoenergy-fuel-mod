package com.lynk.dvrprobe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

        points.forEachIndexed { index, point ->
            if (index == 0) return@forEachIndexed
            val prev = points[index - 1]
            val x1 = mapX(index - 1, points.size, left, right)
            val y1 = mapY(prev.value, minValue, maxValue, top, bottom)
            val x2 = mapX(index, points.size, left, right)
            val y2 = mapY(point.value, minValue, maxValue, top, bottom)
            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }
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
