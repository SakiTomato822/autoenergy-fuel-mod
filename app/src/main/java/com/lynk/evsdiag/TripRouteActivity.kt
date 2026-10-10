package com.lynk.dvrprobe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class TripRouteActivity : AppCompatActivity() {
    private lateinit var route: RouteCanvas
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var recording: Button
    private var followLive = true
    private var shownPoints: List<RoutePoint> = emptyList()
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) startRecording()
        else status.text = "未获精确定位权限；你仍可查看已保存的行程"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13, 25, 36))
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label; textSize = 22f; isAllCaps = false
            setTextColor(Color.rgb(235, 244, 250))
            background = GradientDrawable().apply { setColor(Color.rgb(36, 57, 74)); cornerRadius = dp(8).toFloat() }
            setPadding(dp(22), dp(10), dp(22), dp(10))
            setOnClickListener { action() }
        }
        header.addView(button("‹ 返回") { finish() })
        header.addView(label("行程轨迹", 30f), LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginStart = dp(22) })
        header.addView(button("历史行程", ::chooseHistory))
        if (runCatching { Class.forName("android.car.Car") }.isFailure) {
            header.addView(button("演示轨迹", ::showDemo), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) })
        }
        recording = button("开始记录", ::toggleRecording)
        header.addView(recording, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) })
        root.addView(header)
        status = label("定位仅在主动开始后启用 · 本地保存，不上传", 22f)
        root.addView(status, LinearLayout.LayoutParams(-1, dp(64)))
        val body = LinearLayout(this)
        route = RouteCanvas { point ->
            val time = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(point.timestampMs))
            details.text = "选中位置  $time\n\nGPS 车速  ${point.speedKmh?.let { "%.0f km/h".format(it) } ?: "--"}\n\n定位精度  ±${point.accuracyMetres.roundToInt()} m\n\n本次累计里程  ${point.cumulativeTripKm?.let { "%.1f km".format(it) } ?: "--"}\n\n本次累计平均油耗\n${point.cumulativeAverageFuel?.let { "%.1f L/100km".format(it) } ?: "--"}\n\n这不是此位置的瞬时油耗。"
        }
        body.addView(route, LinearLayout.LayoutParams(0, -1, 2.4f))
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(28), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply { setColor(Color.rgb(27, 43, 56)); cornerRadius = dp(12).toFloat() }
        }
        details = label("尚无轨迹\n\n主动开始记录后，等待车机提供有效定位。\n\n当前为无底图轨迹预览，不显示道路或地名。", 24f)
        sidebar.addView(details, LinearLayout.LayoutParams(-1, 0, 1f))
        sidebar.addView(label("蓝色：低速  ·  青色：中速\n金色：较高速  ·  灰色：速度未知\n失去定位时断开连线", 20f))
        body.addView(sidebar, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(22) })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TripRouteState.state.collect { state ->
                    recording.text = if (state.recording) "结束记录" else "开始记录"
                    if (followLive) {
                        status.text = state.status
                        if (state.points.isNotEmpty()) display(state.points)
                    }
                }
            }
        }
        lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) {
                runCatching { TripRouteStore.list(this@TripRouteActivity).firstOrNull()?.let(TripRouteStore::read) }
            }
            if (shownPoints.isEmpty() && !TripRouteState.state.value.recording) {
                latest.onSuccess { it?.let { points -> display(points); status.text = "最近保存的行程 · 本地无底图预览" } }
                    .onFailure { status.text = "无法读取行程：${it.message}" }
            }
        }
    }

    private fun toggleRecording() {
        if (TripRouteState.state.value.recording) {
            stopService(Intent(this, TripRouteService::class.java))
            return
        }
        val dialog = android.app.Dialog(this)
        val panel = dialogPanel("开始记录行程？")
        panel.addView(label("将使用车机定位，切换到导航后仍持续记录，直到你主动结束。\n轨迹仅存本机，不上传；未接入道路底图。卸载 App 会删除轨迹。\n请停车后操作。", 24f).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24); bottomMargin = dp(28) })
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        actions.addView(dialogButton("取消") { dialog.dismiss() })
        actions.addView(dialogButton("同意并开始") {
                dialog.dismiss()
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startRecording()
                else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(22) })
        panel.addView(actions)
        showPanel(dialog, panel)
    }
    private fun startRecording() {
        followLive = true
        shownPoints = emptyList(); route.points = emptyList(); route.invalidate()
        details.text = "等待车机提供有效定位\n\n定位可能被车机限制；读不到时不生成虚假路线。"
        runCatching { ContextCompat.startForegroundService(this, Intent(this, TripRouteService::class.java)) }
            .onFailure { status.text = "启动失败：${it.message}" }
    }
    private fun chooseHistory() {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { runCatching { TripRouteStore.list(this@TripRouteActivity).take(30) } }
                .getOrElse { status.text = "历史读取失败：${it.message}"; return@launch }
            if (files.isEmpty()) { status.text = "还没有已保存的行程"; return@launch }
            val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
            val dialog = android.app.Dialog(this@TripRouteActivity)
            val panel = dialogPanel("本地行程（最近 30 次）")
            val rows = LinearLayout(this@TripRouteActivity).apply { orientation = LinearLayout.VERTICAL }
            files.forEach { file ->
                rows.addView(dialogButton(formatter.format(Date(file.name.substringAfter("route-").substringBefore('-').toLong()))) {
                    dialog.dismiss()
                    lifecycleScope.launch {
                        val points = withContext(Dispatchers.IO) { runCatching { TripRouteStore.read(file) } }
                        points.onSuccess { followLive = false; display(it); status.text = "历史行程 · 本地无底图预览" }
                            .onFailure { status.text = "行程读取失败：${it.message}" }
                    }
                }, LinearLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(12) })
            }
            panel.addView(ScrollView(this@TripRouteActivity).apply { addView(rows) }, LinearLayout.LayoutParams(-1, dp(minOf(files.size * 82, 480))))
            panel.addView(dialogButton("关闭") { dialog.dismiss() }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = dp(16) })
            showPanel(dialog, panel)
        }
    }
    private fun display(points: List<RoutePoint>) {
        shownPoints = points
        route.points = points; route.invalidate()
        val metres = points.zipWithNext().sumOf { (a, b) -> if (b.breakBefore) 0.0 else RouteGeometry.distanceMetres(a, b) }
        val duration = if (points.size < 2) 0 else ((points.last().elapsedMs - points.first().elapsedMs) / 60_000).coerceAtLeast(0)
        details.text = "定位轨迹长度\n%.2f km\n\n记录跨度\n%d min\n\n有效定位\n%d 个点\n\n点击轨迹查看读数\n\n轨迹长度含定位误差，不能替代车辆里程。".format(metres / 1000, duration, points.size) +
            if (points.size >= 10_000) "\n仅展示最近 10000 点" else ""
    }
    private fun showDemo() {
        if (TripRouteState.state.value.recording) return
        followLive = false
        val points = (0..80).map { i -> RoutePoint(1_700_000_000_000L + i * 5_000, i * 5_000L,
            26.0 + i * 0.00012, 119.0 + sin(i / 13.0) * 0.002 + i * 0.00002,
            5f, (15 + i % 70).toFloat(), 6.2f, i / 10f, i == 0 || i == 45) }
        display(points)
        status.text = "演示轨迹 · 合成数据，未写入行程文件 · 无道路底图"
    }
    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(211, 230, 241))
        typeface = ResourcesCompat.getFont(this@TripRouteActivity, R.font.lynkco_type_medium)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun dialogPanel(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(36), dp(30), dp(36), dp(30))
        background = GradientDrawable().apply { setColor(Color.rgb(27, 43, 56)); cornerRadius = dp(14).toFloat() }
        addView(label(title, 30f))
    }
    private fun dialogButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title; textSize = 24f; isAllCaps = false
        setTextColor(Color.rgb(235, 244, 250))
        typeface = ResourcesCompat.getFont(this@TripRouteActivity, R.font.lynkco_type_medium)
        background = GradientDrawable().apply { setColor(Color.rgb(36, 57, 74)); cornerRadius = dp(8).toFloat() }
        setPadding(dp(24), dp(14), dp(24), dp(14))
        setOnClickListener { action() }
    }
    private fun showPanel(dialog: android.app.Dialog, panel: View) {
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.62f).roundToInt(), -2)
    }

    private inner class RouteCanvas(val selected: (RoutePoint) -> Unit) : View(this@TripRouteActivity) {
        var points: List<RoutePoint> = emptyList()
        private var projected: List<PointF> = emptyList()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = Color.rgb(22, 38, 51)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(12).toFloat(), dp(12).toFloat(), paint)
            if (points.isEmpty()) {
                paint.color = Color.rgb(147, 174, 192); paint.textSize = dp(26).toFloat(); paint.textAlign = Paint.Align.CENTER
                canvas.drawText("等待行程轨迹 · 暂未接入底图", width / 2f, height / 2f, paint)
                paint.textAlign = Paint.Align.LEFT
                return
            }
            val referenceLat = points.map { it.latitude }.average()
            val referenceLon = points.first().longitude
            val raw = points.map { (it.longitude - referenceLon) * cos(Math.toRadians(referenceLat)) to -it.latitude }
            val minX = raw.minOf { it.first }; val maxX = raw.maxOf { it.first }
            val minY = raw.minOf { it.second }; val maxY = raw.maxOf { it.second }
            val padding = dp(64).toFloat()
            val scale = min((width - padding * 2) / max(maxX - minX, 0.0001), (height - padding * 2) / max(maxY - minY, 0.0001))
            projected = raw.map { PointF((width / 2 + (it.first - (minX + maxX) / 2) * scale).toFloat(),
                (height / 2 + (it.second - (minY + maxY) / 2) * scale).toFloat()) }
            paint.strokeWidth = dp(5).toFloat(); paint.strokeCap = Paint.Cap.ROUND
            for (i in 1 until projected.size) {
                if (points[i].breakBefore) continue
                val speed = points[i].speedKmh
                paint.color = when { speed == null -> Color.GRAY; speed < 30 -> Color.rgb(48, 170, 224)
                    speed < 60 -> Color.rgb(58, 207, 194); else -> Color.rgb(231, 193, 103) }
                canvas.drawLine(projected[i - 1].x, projected[i - 1].y, projected[i].x, projected[i].y, paint)
            }
            for (i in listOf(0, projected.lastIndex).distinct()) {
                paint.color = Color.WHITE; canvas.drawCircle(projected[i].x, projected[i].y, dp(10).toFloat(), paint)
                paint.color = if (i == 0) Color.rgb(65, 203, 159) else Color.rgb(239, 115, 111)
                canvas.drawCircle(projected[i].x, projected[i].y, dp(7).toFloat(), paint)
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                performClick()
                projected.indices.minByOrNull { hypot(projected[it].x - event.x, projected[it].y - event.y) }?.let { index ->
                    if (hypot(projected[index].x - event.x, projected[index].y - event.y) <= dp(60)) selected(points[index])
                }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
