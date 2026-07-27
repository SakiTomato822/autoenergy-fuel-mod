package com.lynk.dvrprobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

class OriginalEnergyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    companion object {
        private const val DESIGN_WIDTH = 1920f
        private const val DESIGN_HEIGHT = 1080f
    }

    private val backgroundBitmap = decodeBitmap(R.drawable.energy_background_fuel_only)
    private val bitmapCache = mutableMapOf<Int, Bitmap>()
    private var snapshot: FuelEnergySnapshot? = null
    private var preview = false

    private val medium = lynkcoTypeface(R.font.lynkco_type_medium)
    private val regular = medium
    private val italic = Typeface.create(medium, Typeface.ITALIC)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
    }
    private val panelRect = RectF()

    fun setSnapshot(value: FuelEnergySnapshot, isPreview: Boolean) {
        snapshot = value
        preview = isPreview
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        val dx = (width - DESIGN_WIDTH * scale) / 2f
        val dy = (height - DESIGN_HEIGHT * scale) / 2f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        drawBitmapLayer(canvas, backgroundBitmap)
        drawBitmapLayer(canvas, fuelCylinder())
        drawLeftInformation(canvas)
        drawFuelInformation(canvas)
        drawBottomActions(canvas)

        canvas.restore()
    }

    private fun drawLeftInformation(canvas: Canvas) {
        val data = snapshot
        panelRect.set(78f, 150f, 790f, 890f)
        paint.shader = LinearGradient(
            panelRect.left,
            panelRect.top,
            panelRect.right,
            panelRect.bottom,
            intArrayOf(Color.argb(128, 15, 32, 45), Color.argb(55, 13, 28, 40)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(panelRect, 18f, 18f, paint)
        paint.shader = null

        text(canvas, "能量中心", 126f, 225f, 38f, Color.WHITE, medium)
        text(canvas, "续航里程", 126f, 330f, 26f, Color.rgb(158, 181, 197), regular)
        val range = rangeText(data)
        text(canvas, range, 126f, 435f, 94f, Color.WHITE, regular)
        val rangeWidth = paint.measureText(range)
        text(canvas, "km", 126f + rangeWidth + 18f, 434f, 30f, Color.rgb(164, 187, 202), regular)

        line(canvas, 126f, 495f, 724f, 495f, Color.argb(80, 94, 176, 218))

        metric(canvas, "平均油耗 · 本次", fuelText(data?.avgFuelTrip1), "L/100km", 126f, 566f)
        metric(canvas, "平均油耗 · 长期", fuelText(data?.avgFuelTrip2), "L/100km", 430f, 566f)
        metric(canvas, "小计里程", distanceText(data?.trip1DistanceKm), "km", 126f, 716f)
        metric(canvas, "总里程", distanceText(data?.odometerKm), "km", 430f, 716f)

        text(
            canvas,
            if (preview) "预览数据" else "车辆实时数据",
            126f,
            842f,
            20f,
            Color.argb(185, 129, 183, 211),
            regular,
        )
    }

    private fun drawFuelInformation(canvas: Canvas) {
        val percent = snapshot?.fuelPercent
        val markerPercent = fuelLevelPercent()
        val markerY = 315f + (1f - markerPercent / 100f) * 360f
        drawSkewedPercent(canvas, percent?.toString() ?: "--", markerY)

        canvas.save()
        canvas.rotate(-10f, 1330f, markerY + 36f)
        text(
            canvas,
            "燃油余量",
            1328f,
            markerY + 40f,
            24f,
            Color.rgb(153, 190, 210),
            italic,
        )
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(170, 35, 183, 242)
        val marker = Path().apply {
            moveTo(1390f, markerY)
            lineTo(1435f, markerY - 14f)
            lineTo(1475f, markerY - 14f)
        }
        canvas.drawPath(marker, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBottomActions(canvas: Canvas) {
        panelRect.set(1128f, 914f, 1430f, 990f)
        paint.color = Color.argb(70, 26, 71, 96)
        canvas.drawRoundRect(panelRect, 38f, 38f, paint)
        centeredText(canvas, "平均油耗", panelRect, 24f, Color.rgb(190, 216, 230), medium)

        panelRect.set(1455f, 914f, 1778f, 990f)
        paint.color = Color.argb(100, 20, 115, 157)
        canvas.drawRoundRect(panelRect, 38f, 38f, paint)
        centeredText(canvas, "里程统计", panelRect, 24f, Color.WHITE, medium)
    }

    private fun drawSkewedPercent(canvas: Canvas, number: String, markerY: Float) {
        paint.textSize = 52f
        paint.typeface = medium
        val numberWidth = paint.measureText(number)
        paint.textSize = 28f
        val percentWidth = paint.measureText("%")
        val totalWidth = numberWidth + 6f + percentWidth
        val originX = 1385f - totalWidth
        val baselineY = markerY + 0.17632698f * totalWidth

        canvas.save()
        canvas.translate(originX, baselineY - 72f)
        canvas.skew(0f, -0.17632698f)

        text(canvas, number, 0f, 72f, 52f, Color.WHITE, medium)
        text(
            canvas,
            "%",
            numberWidth + 6f,
            72f,
            28f,
            Color.rgb(170, 204, 222),
            medium,
        )
        canvas.restore()
    }

    private fun metric(
        canvas: Canvas,
        label: String,
        value: String,
        unit: String,
        x: Float,
        y: Float,
    ) {
        text(canvas, label, x, y, 22f, Color.rgb(148, 174, 190), regular)
        text(canvas, value, x, y + 72f, 45f, Color.WHITE, regular)
        val valueWidth = paint.measureText(value)
        text(canvas, unit, x + valueWidth + 12f, y + 70f, 18f, Color.rgb(145, 171, 188), regular)
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        typeface: Typeface,
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.typeface = typeface
        canvas.drawText(value, x, y, paint)
    }

    private fun centeredText(
        canvas: Canvas,
        value: String,
        bounds: RectF,
        size: Float,
        color: Int,
        typeface: Typeface,
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.typeface = typeface
        val metrics = paint.fontMetrics
        val x = bounds.centerX() - paint.measureText(value) / 2f
        val y = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(value, x, y, paint)
    }

    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        paint.color = color
        paint.strokeWidth = 1f
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    private fun drawBitmapLayer(canvas: Canvas, bitmap: Bitmap?) {
        if (bitmap == null) return
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT), paint)
    }

    private fun fuelCylinder(): Bitmap? {
        val level = fuelLevelPercent()
        val resourceId = resources.getIdentifier(
            "cylinder_gasoline_$level",
            "drawable",
            context.packageName,
        )
        if (resourceId == 0) return null
        return bitmapCache.getOrPut(resourceId) { decodeBitmap(resourceId) ?: return null }
    }

    private fun fuelLevelPercent(): Int {
        val percent = snapshot?.fuelPercent?.coerceIn(0, 100) ?: 0
        return ((percent / 2f).roundToInt() * 2).coerceIn(0, 100)
    }

    private fun decodeBitmap(resourceId: Int): Bitmap? =
        runCatching { BitmapFactory.decodeResource(resources, resourceId) }.getOrNull()

    private fun lynkcoTypeface(fontResourceId: Int): Typeface =
        runCatching {
            val family = FontFamily.Builder(Font.Builder(resources, fontResourceId).build()).build()
            Typeface.CustomFallbackBuilder(family)
                .setSystemFallback("sans-serif-medium")
                .build()
        }.getOrElse {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun rangeText(data: FuelEnergySnapshot?): String =
        (data?.oilRangeKm ?: data?.totalRangeKm)?.toString() ?: "--"

    private fun fuelText(value: Float?): String = value?.let { "%.1f".format(it) } ?: "--"

    private fun distanceText(value: Float?): String =
        value?.let { if (it >= 1000f) "%,.0f".format(it) else "%.1f".format(it) } ?: "--"
}
