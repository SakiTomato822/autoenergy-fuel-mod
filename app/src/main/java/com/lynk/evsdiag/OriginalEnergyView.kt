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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
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
        private const val DESIGN_HEIGHT = 968f
        private const val RESET_OPTION_CHARGING = 612369154
        private const val RESET_OPTION_PARKING = 612369156
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
    private var selectedResetOption: Int? = null
    private var selectedHistoryDistanceKm = 50
    private var previewCurveAverage = 11.2f
    private var showSubtotalResetConfirmation = false
    private var onSingleTripResetOptionChanged: ((Int) -> Unit)? = null
    private var onSubtotalResetRequested: (() -> Unit)? = null
    private var onDiagnosticsRequested: (() -> Unit)? = null

    private val medium = lynkcoTypeface(R.font.lynkco_type_medium)
    private val regular = medium
    private val italic = Typeface.create(medium, Typeface.ITALIC)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
    }
    private val panelRect = RectF()
    private val mainEntryBounds = RectF(78f, 150f, 790f, 890f)
    private val statisticsEntryBounds = RectF(106f, 766f, 762f, 864f)
    private var entryPressed = false
    private val primaryText = Color.rgb(241, 243, 246)
    private val secondaryText = Color.rgb(160, 168, 180)
    private val surfaceColor = Color.rgb(34, 38, 45)
    private val accentColor = Color.rgb(63, 174, 235)
    private val statisticsBackBounds = RectF(42f, 36f, 315f, 142f)
    private val parkingResetBounds = RectF(365f, 188f, 495f, 248f)
    private val chargingResetBounds = RectF(495f, 188f, 625f, 248f)
    private val subtotalResetBounds = RectF(500f, 610f, 625f, 666f)
    private val history12Bounds = RectF(1545f, 188f, 1675f, 248f)
    private val history24Bounds = RectF(1680f, 188f, 1810f, 248f)
    private val resetDialogCancelBounds = RectF(1065f, 570f, 1195f, 630f)
    private val resetDialogConfirmBounds = RectF(1210f, 570f, 1340f, 630f)

    private val curveOffsets50Km = floatArrayOf(
        -2.6f, 1.8f, -4.1f, 0.6f, 4.8f, -1.5f, -3.2f, 2.5f, 7.2f, -0.8f,
        5.9f, -2.1f, 1.1f, -4.5f, 3.7f, -1.2f, 6.4f, -3.4f, 0.9f, 8.0f,
        -0.5f, 4.3f, -2.8f, 2.1f, -3.7f, 0.5f, 5.2f, -1.7f, 1.6f, 6.9f,
    )
    private val curveOffsets100Km = floatArrayOf(
        -1.5f, -0.8f, 0.4f, 1.8f, 0.9f, -0.6f, -1.4f, 0.2f, 2.6f,
        1.1f, -0.9f, -1.7f, -0.3f, 1.5f, 3.2f, 1.4f, 0.1f, -1.2f,
        -0.5f, 0.8f, 2.1f, 1.0f, -0.7f, 0.3f, 1.6f,
    )

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "能量中心，点击左侧下方里程统计入口；长按卡片查看诊断"
    }

    fun setSnapshot(value: FuelEnergySnapshot, isPreview: Boolean) {
        snapshot = value
        preview = isPreview
        if (isPreview && value.avgFuelTrip1 != null && value.avgFuelTrip1 > 0.1f) {
            previewCurveAverage = value.avgFuelTrip1
        }
        selectedResetOption = if (value.singleTripResetOption == RESET_OPTION_CHARGING ||
            value.singleTripResetOption == RESET_OPTION_PARKING
        ) {
            value.singleTripResetOption
        } else null
        invalidate()
    }

    fun setTrendPoints(value: List<FuelTrendPoint>) {
        trendPoints = value
        invalidate()
    }

    fun setActionCallbacks(
        onSingleTripResetOptionChanged: (Int) -> Unit,
        onSubtotalResetRequested: () -> Unit,
        onDiagnosticsRequested: () -> Unit,
    ) {
        this.onSingleTripResetOptionChanged = onSingleTripResetOptionChanged
        this.onSubtotalResetRequested = onSubtotalResetRequested
        this.onDiagnosticsRequested = onDiagnosticsRequested
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        if (scale <= 0f) return
        val dx = (width - DESIGN_WIDTH * scale) / 2f
        val dy = (height - DESIGN_HEIGHT * scale) / 2f

        // Fill the viewport behind the design so its safe-area fit cannot
        // produce black side bars. Only this decorative wallpaper is stretched.
        backgroundBitmap?.let {
            paint.alpha = 255
            paint.shader = null
            canvas.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        }

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        drawMainPage(canvas)
        if (pageProgress > 0f) {
            val layer = canvas.saveLayerAlpha(
                0f,
                0f,
                DESIGN_WIDTH,
                DESIGN_HEIGHT,
                (255f * pageProgress).roundToInt().coerceIn(0, 255),
            )
            canvas.translate(0f, 30f * (1f - pageProgress))
            drawMileageStatisticsPage(canvas)
            canvas.restoreToCount(layer)
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
                val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
                if (scale > 0f && pageProgress < 0.5f) {
                    val dx = (width - DESIGN_WIDTH * scale) / 2f
                    val dy = (height - DESIGN_HEIGHT * scale) / 2f
                    entryPressed = statisticsEntryBounds.contains((event.x - dx) / scale, (event.y - dy) / scale)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - touchDownX) > 24f || abs(event.y - touchDownY) > 24f) {
                    entryPressed = false
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val entryWasPressed = entryPressed
                entryPressed = false
                invalidate()
                val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
                if (scale <= 0f || pageAnimator?.isRunning == true) return true

                performClick()
                val dx = (width - DESIGN_WIDTH * scale) / 2f
                val dy = (height - DESIGN_HEIGHT * scale) / 2f
                val x = (event.x - dx) / scale
                val y = (event.y - dy) / scale
                if (showSubtotalResetConfirmation) {
                    when {
                        resetDialogCancelBounds.contains(x, y) -> {
                            showSubtotalResetConfirmation = false
                            invalidate()
                        }

                        resetDialogConfirmBounds.contains(x, y) -> {
                            showSubtotalResetConfirmation = false
                            invalidate()
                            onSubtotalResetRequested?.invoke()
                        }
                    }
                    return true
                }

                val deltaX = (event.x - touchDownX) / scale
                val deltaY = (event.y - touchDownY) / scale
                val pressDurationMs = event.eventTime - event.downTime
                if (
                    pageProgress < 0.5f &&
                    pressDurationMs >= 1_200L &&
                    abs(deltaX) < 45f &&
                    abs(deltaY) < 45f &&
                    mainEntryBounds.contains(x, y)
                ) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onDiagnosticsRequested?.invoke()
                    return true
                }
                val isVerticalSwipe = abs(deltaY) > 120f && abs(deltaY) > abs(deltaX) * 1.2f
                if (isVerticalSwipe) {
                    if (pageProgress < 0.5f && deltaY < 0f) animatePageTo(1f)
                    if (pageProgress >= 0.5f && deltaY > 0f) animatePageTo(0f)
                    return true
                }

                if (pageProgress < 0.5f && entryWasPressed && statisticsEntryBounds.contains(x, y)) {
                    animatePageTo(1f)
                } else if (pageProgress >= 0.5f) {
                    when {
                        statisticsBackBounds.contains(x, y) -> animatePageTo(0f)
                        parkingResetBounds.contains(x, y) -> selectResetOption(RESET_OPTION_PARKING)
                        chargingResetBounds.contains(x, y) -> selectResetOption(RESET_OPTION_CHARGING)
                        preview && subtotalResetBounds.contains(x, y) -> {
                            showSubtotalResetConfirmation = true
                            invalidate()
                        }
                        history12Bounds.contains(x, y) -> selectHistoryDistance(50)
                        history24Bounds.contains(x, y) -> selectHistoryDistance(100)
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                entryPressed = false
                invalidate()
                return true
            }
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
            duration = 220L
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
            "能量中心，点击左侧下方里程统计入口；长按卡片查看诊断"
        }
    }

    private fun selectResetOption(option: Int) {
        if (!preview) return
        if (selectedResetOption == option) return
        onSingleTripResetOptionChanged?.invoke(option)
    }

    private fun selectHistoryDistance(distanceKm: Int) {
        if (selectedHistoryDistanceKm == distanceKm) return
        selectedHistoryDistanceKm = distanceKm
        invalidate()
    }

    private fun drawLeftInformation(canvas: Canvas) {
        val data = snapshot
        panelRect.set(78f, 150f, 790f, 890f)
        paint.shader = null
        paint.color = Color.argb(225, 26, 31, 39)
        canvas.drawRoundRect(panelRect, 16f, 16f, paint)

        text(canvas, "能量中心", 126f, 225f, 36f, primaryText, medium)
        text(canvas, if (preview) "预览" else "车辆数据", 620f, 222f, 22f, secondaryText, medium)
        text(canvas, "预计续航", 126f, 308f, 24f, secondaryText, regular)
        val range = rangeText(data)
        text(canvas, range, 126f, 414f, 94f, primaryText, medium)
        val rangeWidth = paint.measureText(range)
        text(canvas, "km", 126f + rangeWidth + 14f, 412f, 28f, secondaryText, regular)

        line(canvas, 126f, 462f, 742f, 462f, Color.argb(28, 255, 255, 255))

        text(canvas, "本次平均油耗", 126f, 516f, 24f, secondaryText, medium)
        text(canvas, fuelText(data?.avgFuelTrip2), 126f, 585f, 58f, primaryText, medium)
        text(canvas, "L/100km", 126f, 622f, 22f, secondaryText, medium)
        text(canvas, "小计平均油耗", 440f, 516f, 24f, secondaryText, medium)
        text(canvas, fuelText(data?.avgFuelTrip1), 440f, 585f, 44f, primaryText, medium)
        text(canvas, "L/100km", 440f, 622f, 22f, secondaryText, medium)
        text(canvas, "小计里程", 126f, 680f, 22f, secondaryText, medium)
        text(canvas, distanceText(data?.trip1DistanceKm) + " km", 126f, 723f, 30f, primaryText, medium)
        text(canvas, "总里程", 440f, 680f, 22f, secondaryText, medium)
        text(canvas, distanceText(data?.odometerKm) + " km", 440f, 723f, 30f, primaryText, medium)

        paint.color = if (entryPressed) Color.rgb(61, 67, 77) else Color.rgb(43, 49, 59)
        canvas.drawRoundRect(statisticsEntryBounds, 12f, 12f, paint)
        text(canvas, "里程统计", 130f, 804f, 26f, primaryText, medium)
        text(canvas, "本次行程、小计与油耗估算", 130f, 840f, 22f, secondaryText, medium)
        drawChevron(canvas, 727f, statisticsEntryBounds.centerY(), 10f, secondaryText, pointsRight = true)
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
        paint.color = Color.rgb(21, 24, 30)
        paint.shader = null
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint)
        paint.shader = null

        drawChevron(canvas, 74f, 90f, 17f, Color.WHITE, pointsRight = false)
        text(canvas, "里程统计", 118f, 105f, 40f, Color.WHITE, medium)

        drawGlassPanel(canvas, RectF(78f, 165f, 650f, 555f))
        drawGlassPanel(canvas, RectF(78f, 585f, 650f, 952f))
        drawGlassPanel(canvas, RectF(680f, 165f, 1840f, 952f))

        drawCurrentTripCard(canvas)
        drawTripSummaryCard(canvas)
        drawFuelCurveCard(canvas)
        if (showSubtotalResetConfirmation) drawSubtotalResetConfirmation(canvas)
    }

    private fun drawSubtotalResetConfirmation(canvas: Canvas) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 0, 5, 9)
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint)

        val bounds = RectF(555f, 385f, 1365f, 665f)
        paint.alpha = 255
        paint.color = Color.WHITE
        paint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            intArrayOf(Color.rgb(35, 55, 67), Color.rgb(22, 39, 50)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, 16f, 16f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(90, 182, 214, 229)
        canvas.drawRoundRect(bounds, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        text(canvas, "重置小计里程", 610f, 458f, 31f, Color.WHITE, medium)
        text(
            canvas,
            "确认清除小计里程累计数据？能耗曲线不会被重置。",
            610f,
            516f,
            20f,
            Color.rgb(190, 211, 222),
            regular,
        )
        drawPill(canvas, resetDialogCancelBounds, "取消", active = false)
        drawPill(canvas, resetDialogConfirmBounds, "重置", active = true)
    }

    private fun drawCurrentTripCard(canvas: Canvas) {
        val data = snapshot
        text(canvas, "本次里程", 122f, 226f, 31f, Color.WHITE, medium)
        if (preview) drawTwoSegmentControl(
            canvas,
            RectF(365f, 188f, 625f, 248f),
            leftLabel = "停车重置",
            rightLabel = "加油重置",
            leftActive = selectedResetOption == RESET_OPTION_PARKING,
            rightActive = selectedResetOption == RESET_OPTION_CHARGING,
        ) else {
            val resetLabel = when (selectedResetOption) {
                RESET_OPTION_PARKING -> "自动重置 · 停车"
                RESET_OPTION_CHARGING -> "自动重置 · 加油"
                else -> "重置方式未知"
            }
            centeredText(canvas, resetLabel, RectF(365f, 188f, 625f, 248f), 22f, secondaryText, medium)
        }

        val resetDescription = if (selectedResetOption == null) {
            "重置方式待读取 · 此处仅显示车辆状态"
        } else if (selectedResetOption == RESET_OPTION_CHARGING) {
            "自最近一次加油重置起 · 历史曲线保留"
        } else if (selectedResetOption == RESET_OPTION_PARKING) {
            "自最近一次停车重置起 · 历史曲线保留"
        } else "*暂时无法读取车辆的自动重置方式"
        text(canvas, resetDescription, 122f, 282f, 22f, secondaryText, regular)

        statMetric(canvas, durationText(data?.trip2DurationMinutes), "行驶时长", 220f, 350f)
        statMetric(canvas, distanceText(data?.trip2DistanceKm) + " km", "行驶里程", 505f, 350f)
        statMetric(canvas, speedText(data?.trip2AvgSpeed) + " km/h", "平均车速", 220f, 472f)
        statMetric(canvas, fuelText(data?.avgFuelTrip2) + " L/100km", "本次油耗", 505f, 472f)

        line(canvas, 364f, 308f, 364f, 525f, Color.argb(85, 207, 225, 235))
        line(canvas, 122f, 414f, 608f, 414f, Color.argb(70, 207, 225, 235))
    }

    private fun drawTripSummaryCard(canvas: Canvas) {
        val data = snapshot
        text(canvas, "小计里程", 122f, 646f, 31f, Color.WHITE, medium)
        if (preview) drawPill(canvas, subtotalResetBounds, "重置数据", active = false)

        statMetric(canvas, durationText(data?.trip1DurationMinutes), "行驶时长", 220f, 755f)
        statMetric(canvas, distanceText(data?.trip1DistanceKm) + " km", "行驶里程", 505f, 755f)
        statMetric(canvas, speedText(data?.trip1AvgSpeed) + " km/h", "平均车速", 220f, 890f)
        statMetric(canvas, fuelText(data?.avgFuelTrip1) + " L/100km", "平均油耗", 505f, 890f)

        line(canvas, 364f, 700f, 364f, 940f, Color.argb(85, 207, 225, 235))
        line(canvas, 122f, 822f, 608f, 822f, Color.argb(70, 207, 225, 235))
    }

    private fun drawFuelCurveCard(canvas: Canvas) {
        val data = snapshot
        text(canvas, "油耗估算", 730f, 226f, 31f, primaryText, medium)

        drawPill(canvas, history12Bounds, "近50km", active = selectedHistoryDistanceKm == 50)
        drawPill(canvas, history24Bounds, "近100km", active = selectedHistoryDistanceKm == 100)

        val fallbackAverage = if (preview) {
            previewCurveAverage
        } else {
            data?.avgFuelTrip1 ?: 11.2f
        }.coerceIn(1f, 20f)
        val windowDistance = selectedHistoryDistanceKm.toFloat()
        val points: List<Pair<Float, Float>> = if (preview) {
            val offsets = if (selectedHistoryDistanceKm == 50) {
                curveOffsets50Km
            } else {
                curveOffsets100Km
            }
            offsets.mapIndexed { index, offset ->
                index / max(1f, offsets.lastIndex.toFloat()) to
                    (fallbackAverage + offset).coerceIn(1.5f, 20f)
            }
        } else {
            val latestDistance = trendPoints.lastOrNull()?.distanceKm
            val cutoff = (latestDistance ?: 0f) - windowDistance
            val history = trendPoints
                .filter { it.distanceKm >= cutoff }
                .map {
                    ((it.distanceKm - cutoff) / windowDistance)
                        .coerceIn(0f, 1f) to it.value.coerceIn(0f, 20f)
                }
            if (history.size >= 2) history else {
                listOf(0f to fallbackAverage, 1f to fallbackAverage)
            }
        }
        val visibleTrend = if (preview) {
            emptyList()
        } else {
            val latestDistance = trendPoints.lastOrNull()?.distanceKm ?: 0f
            val cutoff = latestDistance - windowDistance
            trendPoints.filter { it.distanceKm >= cutoff }
        }
        val weightedDistance = visibleTrend.sumOf { it.spanKm.toDouble() }.toFloat()
        val average = if (weightedDistance > 0f) {
            visibleTrend.sumOf { (it.value * it.spanKm).toDouble() }.toFloat() / weightedDistance
        } else {
            points.map { it.second }.average().toFloat()
        }.coerceIn(1f, 20f)
        text(canvas, "■", 730f, 306f, 18f, Color.rgb(20, 187, 244), medium)
        text(canvas, "L/100km", 755f, 306f, 19f, Color.rgb(210, 227, 236), regular)
        val hasCurve = preview || visibleTrend.size >= 2
        val averageLabel = if (hasCurve) "分段均值 ${fuelText(average)} L/100km" else "暂无有效分段"
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

        if (!hasCurve) {
            centeredText(canvas, "行驶后生成油耗估算", RectF(chartLeft, 510f, chartRight, 570f), 30f, primaryText, medium)
            centeredText(canvas, "累计足够的有效分段后显示曲线", RectF(chartLeft, 575f, chartRight, 625f), 24f, secondaryText, medium)
            text(canvas, "根据累计平均油耗与里程估算，非瞬时油耗", chartLeft, 900f, 22f, secondaryText, medium)
            return
        }

        val curvePath = Path()
        val areaPath = Path()
        points.forEachIndexed { index, point ->
            val x = chartLeft + (chartRight - chartLeft) * point.first
            val value = point.second
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
        points.lastOrNull()?.let { point ->
            canvas.drawCircle(chartLeft + (chartRight - chartLeft) * point.first,
                chartBottom - (chartBottom - chartTop) * point.second / 20f, 6f, paint)
        }
        text(canvas, fuelText(average), chartRight + 10f, averageY + 7f, 17f, Color.WHITE, medium)

        val xLabels = if (selectedHistoryDistanceKm == 50) {
            listOf("-50km", "-40", "-30", "-20", "-10", "此刻")
        } else {
            listOf("-100km", "-80", "-60", "-40", "-20", "此刻")
        }
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
        paint.color = surfaceColor
        paint.shader = null
        canvas.drawRoundRect(bounds, 12f, 12f, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    private fun drawTwoSegmentControl(
        canvas: Canvas,
        bounds: RectF,
        leftLabel: String,
        rightLabel: String,
        leftActive: Boolean,
        rightActive: Boolean = !leftActive,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(48, 54, 64)
        canvas.drawRoundRect(bounds, 7f, 7f, paint)

        val centerX = bounds.centerX()
        val activeBounds = if (leftActive) {
            RectF(bounds.left, bounds.top, centerX, bounds.bottom)
        } else {
            RectF(centerX, bounds.top, bounds.right, bounds.bottom)
        }
        if (leftActive || rightActive) {
            paint.color = accentColor
            canvas.drawRoundRect(activeBounds, 7f, 7f, paint)
            if (leftActive) {
                canvas.drawRect(centerX - 7f, bounds.top, centerX, bounds.bottom, paint)
            } else {
                canvas.drawRect(centerX, bounds.top, centerX + 7f, bounds.bottom, paint)
            }
        }

        line(
            canvas,
            centerX,
            bounds.top + 10f,
            centerX,
            bounds.bottom - 10f,
            Color.argb(80, 233, 242, 246),
        )
        centeredText(
            canvas,
            leftLabel,
            RectF(bounds.left, bounds.top, centerX, bounds.bottom),
            17f,
            Color.WHITE,
            medium,
        )
        centeredText(
            canvas,
            rightLabel,
            RectF(centerX, bounds.top, bounds.right, bounds.bottom),
            17f,
            Color.WHITE,
            medium,
        )
    }

    private fun drawPill(canvas: Canvas, bounds: RectF, label: String, active: Boolean) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = if (active) accentColor else Color.rgb(48, 54, 64)
        canvas.drawRoundRect(bounds, 7f, 7f, paint)
        centeredText(
            canvas,
            label,
            bounds,
            22f,
            if (active) Color.WHITE else Color.rgb(225, 234, 238),
            medium,
        )
    }

    private fun statMetric(canvas: Canvas, value: String, label: String, centerX: Float, valueY: Float) {
        paint.textSize = 30f
        paint.typeface = medium
        text(
            canvas,
            value,
            centerX - paint.measureText(value) / 2f,
            valueY,
            30f,
            Color.WHITE,
            medium,
        )
        paint.textSize = 22f
        paint.typeface = regular
        text(
            canvas,
            label,
            centerX - paint.measureText(label) / 2f,
            valueY + 48f,
            22f,
            secondaryText,
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
        val sourceHeight = min(
            bitmap.height,
            (bitmap.width * DESIGN_HEIGHT / DESIGN_WIDTH).roundToInt(),
        )
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, sourceHeight),
            RectF(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT),
            paint,
        )
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
