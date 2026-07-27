package com.lynk.dvrprobe

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max
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
    private var pageProgress = 0f
    private var pageAnimator: ValueAnimator? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var trendPoints: List<FuelTrendPoint> = emptyList()

    private val medium = lynkcoTypeface(R.font.lynkco_type_medium)
    private val regular = medium
    private val italic = Typeface.create(medium, Typeface.ITALIC)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
    }
    private val panelRect = RectF()
    private val mainEntryBounds = RectF(78f, 150f, 790f, 890f)
    private val statisticsBackBounds = RectF(42f, 36f, 315f, 142f)

    private val curveOffsets = floatArrayOf(
        -2.6f, 1.8f, -4.1f, 0.6f, 4.8f, -1.5f, -3.2f, 2.5f, 7.2f, -0.8f,
        5.9f, -2.1f, 1.1f, -4.5f, 3.7f, -1.2f, 6.4f, -3.4f, 0.9f, 8.0f,
        -0.5f, 4.3f, -2.8f, 2.1f, -3.7f, 0.5f, 5.2f, -1.7f, 1.6f, 6.9f,
    )

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "能量中心，点击左侧卡片查看里程统计"
    }

    fun setSnapshot(value: FuelEnergySnapshot, isPreview: Boolean) {
        snapshot = value
        preview = isPreview
        invalidate()
    }

    fun setTrendPoints(value: List<FuelTrendPoint>) {
        trendPoints = value
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

        drawMainPage(canvas)
        if (pageProgress > 0f) {
            canvas.save()
            canvas.translate(0f, DESIGN_HEIGHT * (1f - pageProgress))
            drawMileageStatisticsPage(canvas)
            canvas.restore()
        }

        canvas.restore()
    }

    private fun drawMainPage(canvas: Canvas) {
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        drawBitmapLayer(canvas, backgroundBitmap)
        drawBitmapLayer(canvas, fuelCylinder())
        drawLeftInformation(canvas)
        drawFuelInformation(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
                if (scale <= 0f || pageAnimator?.isRunning == true) return true

                val deltaX = (event.x - touchDownX) / scale
                val deltaY = (event.y - touchDownY) / scale
                val isVerticalSwipe = abs(deltaY) > 120f && abs(deltaY) > abs(deltaX) * 1.2f
                if (isVerticalSwipe) {
                    if (pageProgress < 0.5f && deltaY < 0f) animatePageTo(1f)
                    if (pageProgress >= 0.5f && deltaY > 0f) animatePageTo(0f)
                    return true
                }

                performClick()
                val dx = (width - DESIGN_WIDTH * scale) / 2f
                val dy = (height - DESIGN_HEIGHT * scale) / 2f
                val x = (event.x - dx) / scale
                val y = (event.y - dy) / scale
                if (pageProgress < 0.5f && mainEntryBounds.contains(x, y)) {
                    animatePageTo(1f)
                } else if (pageProgress >= 0.5f && statisticsBackBounds.contains(x, y)) {
                    animatePageTo(0f)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        pageAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun animatePageTo(target: Float) {
        pageAnimator?.cancel()
        pageAnimator = ValueAnimator.ofFloat(pageProgress, target).apply {
            duration = 360L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pageProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        contentDescription = if (target > 0.5f) {
            "里程统计，点击左上角或向下滑动返回"
        } else {
            "能量中心，点击左侧卡片查看里程统计"
        }
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
        drawChevron(
            canvas,
            748f,
            678f,
            11f,
            Color.argb(125, 172, 211, 231),
            pointsRight = true,
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

    private fun drawMileageStatisticsPage(canvas: Canvas) {
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = LinearGradient(
            0f,
            0f,
            DESIGN_WIDTH,
            DESIGN_HEIGHT,
            intArrayOf(
                Color.rgb(18, 59, 73),
                Color.rgb(10, 30, 41),
                Color.rgb(7, 18, 26),
            ),
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint)
        paint.shader = null

        drawChevron(canvas, 74f, 90f, 17f, Color.WHITE, pointsRight = false)
        text(canvas, "里程统计", 118f, 105f, 40f, Color.WHITE, medium)

        drawGlassPanel(canvas, RectF(78f, 165f, 650f, 492f))
        drawGlassPanel(canvas, RectF(78f, 520f, 650f, 975f))
        drawGlassPanel(canvas, RectF(680f, 165f, 1840f, 975f))

        drawCurrentTripCard(canvas)
        drawTripSummaryCard(canvas)
        drawFuelCurveCard(canvas)
    }

    private fun drawCurrentTripCard(canvas: Canvas) {
        val data = snapshot
        text(canvas, "本次里程", 122f, 226f, 31f, Color.WHITE, medium)
        drawPill(canvas, RectF(385f, 190f, 510f, 246f), "停车重置", active = true)
        drawPill(canvas, RectF(516f, 190f, 625f, 246f), "补能重置", active = false)

        statMetric(
            canvas,
            durationText(data?.trip1DurationMinutes),
            "行驶时长",
            175f,
            325f,
        )
        statMetric(
            canvas,
            distanceText(data?.trip1DistanceKm) + " km",
            "行驶里程",
            373f,
            325f,
        )
        statMetric(
            canvas,
            speedText(data?.trip1AvgSpeed) + " km/h",
            "平均车速",
            555f,
            325f,
        )

        line(canvas, 278f, 285f, 278f, 410f, Color.argb(85, 207, 225, 235))
        line(canvas, 468f, 285f, 468f, 410f, Color.argb(85, 207, 225, 235))
    }

    private fun drawTripSummaryCard(canvas: Canvas) {
        val data = snapshot
        text(canvas, "小计里程", 122f, 582f, 31f, Color.WHITE, medium)
        drawPill(canvas, RectF(505f, 548f, 625f, 604f), "重置数据", active = false)

        statMetric(canvas, durationText(data?.trip2DurationMinutes), "行驶时长", 175f, 677f)
        statMetric(canvas, distanceText(data?.trip2DistanceKm), "行驶里程 km", 373f, 677f)
        statMetric(canvas, speedText(data?.trip2AvgSpeed), "平均车速 km/h", 555f, 677f)
        line(canvas, 278f, 635f, 278f, 746f, Color.argb(85, 207, 225, 235))
        line(canvas, 468f, 635f, 468f, 746f, Color.argb(85, 207, 225, 235))

        statMetric(canvas, fuelText(data?.avgFuelTrip2), "平均油耗 L/100km", 125f, 800f)
        statMetric(canvas, fuelText(data?.avgFuelTrip1), "本次油耗 L/100km", 385f, 800f)
        line(canvas, 350f, 770f, 350f, 862f, Color.argb(85, 207, 225, 235))

        text(canvas, "能耗分布", 122f, 895f, 21f, Color.rgb(205, 221, 230), medium)
        panelRect.set(122f, 918f, 606f, 930f)
        paint.color = Color.rgb(20, 220, 92)
        canvas.drawRoundRect(RectF(122f, 918f, 548f, 930f), 6f, 6f, paint)
        paint.color = Color.rgb(37, 173, 245)
        canvas.drawRect(548f, 918f, 588f, 930f, paint)
        paint.color = Color.rgb(225, 232, 224)
        canvas.drawRoundRect(RectF(588f, 918f, 606f, 930f), 6f, 6f, paint)
        text(canvas, "驾驶 88%", 122f, 958f, 17f, Color.rgb(184, 205, 216), regular)
        text(canvas, "空调 9%", 360f, 958f, 17f, Color.rgb(184, 205, 216), regular)
        text(canvas, "其他 3%", 518f, 958f, 17f, Color.rgb(184, 205, 216), regular)
    }

    private fun drawFuelCurveCard(canvas: Canvas) {
        val data = snapshot
        text(canvas, "能耗曲线", 730f, 226f, 31f, Color.WHITE, medium)
        drawChevron(canvas, 885f, 213f, 10f, Color.rgb(220, 232, 238), pointsRight = false, vertical = true)

        drawPill(canvas, RectF(1295f, 188f, 1415f, 248f), "电耗", active = false)
        drawPill(canvas, RectF(1418f, 188f, 1538f, 248f), "油耗", active = true)
        drawPill(canvas, RectF(1570f, 188f, 1690f, 248f), "近50km", active = false)
        drawPill(canvas, RectF(1693f, 188f, 1813f, 248f), "近100km", active = true)

        val average = (data?.avgFuelTrip1 ?: 11.2f).coerceIn(1f, 20f)
        text(canvas, "■", 730f, 306f, 18f, Color.rgb(20, 187, 244), medium)
        text(canvas, "L/100km", 755f, 306f, 19f, Color.rgb(210, 227, 236), regular)
        val averageLabel = "平均油耗: ${fuelText(data?.avgFuelTrip1)} L/100km"
        paint.textSize = 19f
        paint.typeface = medium
        text(
            canvas,
            averageLabel,
            1795f - paint.measureText(averageLabel),
            306f,
            19f,
            Color.rgb(220, 232, 238),
            medium,
        )

        val chartLeft = 770f
        val chartTop = 350f
        val chartRight = 1780f
        val chartBottom = 855f
        for (step in 0..4) {
            val value = step * 5f
            val y = chartBottom - (chartBottom - chartTop) * value / 20f
            line(canvas, chartLeft, y, chartRight, y, Color.argb(54, 174, 205, 221))
            text(canvas, value.roundToInt().toString(), 725f, y + 7f, 18f, Color.rgb(179, 203, 215), regular)
        }
        line(canvas, chartLeft, chartTop, chartLeft, chartBottom, Color.argb(90, 185, 214, 228))
        line(canvas, chartLeft, chartBottom, chartRight, chartBottom, Color.argb(90, 185, 214, 228))

        val points = if (preview) {
            curveOffsets.map { (average + it).coerceIn(1.5f, 20f) }
        } else {
            val history = trendPoints.takeLast(60).map { it.value.coerceIn(0f, 20f) }
            if (history.size >= 2) history else listOf(average, average)
        }
        val curvePath = Path()
        val areaPath = Path()
        points.forEachIndexed { index, value ->
            val x = chartLeft + (chartRight - chartLeft) * index / max(1, points.lastIndex)
            val y = chartBottom - (chartBottom - chartTop) * value / 20f
            if (index == 0) {
                curvePath.moveTo(x, y)
                areaPath.moveTo(x, chartBottom)
                areaPath.lineTo(x, y)
            } else {
                curvePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(chartRight, chartBottom)
        areaPath.close()

        paint.shader = LinearGradient(
            0f,
            chartTop,
            0f,
            chartBottom,
            Color.argb(230, 0, 166, 242),
            Color.argb(108, 0, 99, 191),
            Shader.TileMode.CLAMP,
        )
        paint.style = Paint.Style.FILL
        canvas.drawPath(areaPath, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(75, 225, 255)
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(curvePath, paint)

        val averageY = chartBottom - (chartBottom - chartTop) * average / 20f
        paint.pathEffect = DashPathEffect(floatArrayOf(5f, 6f), 0f)
        paint.strokeWidth = 2f
        paint.color = Color.rgb(159, 247, 255)
        canvas.drawLine(chartLeft, averageY, chartRight, averageY, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        canvas.drawCircle(chartRight, averageY, 6f, paint)
        text(canvas, fuelText(average), chartRight + 10f, averageY + 7f, 17f, Color.WHITE, medium)

        val xLabels = listOf("-100km", "-80", "-60", "-40", "-20", "此刻")
        xLabels.forEachIndexed { index, label ->
            val x = chartLeft + (chartRight - chartLeft) * index / (xLabels.size - 1)
            paint.textSize = 18f
            paint.typeface = regular
            val labelX = when (index) {
                0 -> x
                xLabels.lastIndex -> x - paint.measureText(label)
                else -> x - paint.measureText(label) / 2f
            }
            text(canvas, label, labelX, 900f, 18f, Color.rgb(194, 214, 224), regular)
        }
    }

    private fun drawGlassPanel(canvas: Canvas, bounds: RectF) {
        paint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            intArrayOf(Color.argb(112, 118, 135, 145), Color.argb(52, 72, 91, 103)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, 12f, 12f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(55, 213, 231, 240)
        canvas.drawRoundRect(bounds, 12f, 12f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPill(canvas: Canvas, bounds: RectF, label: String, active: Boolean) {
        paint.color = if (active) Color.rgb(8, 172, 235) else Color.argb(84, 210, 221, 226)
        canvas.drawRoundRect(bounds, 7f, 7f, paint)
        centeredText(
            canvas,
            label,
            bounds,
            if (label.length > 4) 17f else 19f,
            if (active) Color.WHITE else Color.rgb(225, 234, 238),
            medium,
        )
    }

    private fun statMetric(canvas: Canvas, value: String, label: String, centerX: Float, valueY: Float) {
        paint.textSize = 26f
        paint.typeface = medium
        text(
            canvas,
            value,
            centerX - paint.measureText(value) / 2f,
            valueY,
            26f,
            Color.WHITE,
            medium,
        )
        paint.textSize = 18f
        paint.typeface = regular
        text(
            canvas,
            label,
            centerX - paint.measureText(label) / 2f,
            valueY + 48f,
            18f,
            Color.rgb(201, 218, 227),
            regular,
        )
    }

    private fun drawChevron(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
        pointsRight: Boolean,
        vertical: Boolean = false,
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, radius / 4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        val path = Path()
        if (vertical) {
            path.moveTo(centerX - radius, centerY - radius / 2f)
            path.lineTo(centerX, centerY + radius / 2f)
            path.lineTo(centerX + radius, centerY - radius / 2f)
        } else if (pointsRight) {
            path.moveTo(centerX - radius / 2f, centerY - radius)
            path.lineTo(centerX + radius / 2f, centerY)
            path.lineTo(centerX - radius / 2f, centerY + radius)
        } else {
            path.moveTo(centerX + radius / 2f, centerY - radius)
            path.lineTo(centerX - radius / 2f, centerY)
            path.lineTo(centerX + radius / 2f, centerY + radius)
        }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.alpha = 255
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

    private fun speedText(value: Float?): String =
        value?.let { "%.0f".format(it) } ?: "--"

    private fun durationText(minutes: Int?): String {
        if (minutes == null) return "--"
        if (minutes < 60) return "${minutes}min"
        return "${minutes / 60}h${minutes % 60}min"
    }
}
