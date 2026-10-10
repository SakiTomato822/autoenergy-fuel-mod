package com.lynk.dvrprobe

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class RouteUiState(val recording: Boolean = false, val points: List<RoutePoint> = emptyList(),
    val status: String = "尚未开始记录")

object TripRouteState {
    private val mutable = MutableStateFlow(RouteUiState())
    val state = mutable.asStateFlow()
    fun publish(value: RouteUiState) { mutable.value = value }
}

/** Only explicitly started from the visible route page; never boot-started. */
class TripRouteService : Service(), LocationListener {
    companion object { const val STOP = "com.lynk.dvrprobe.STOP_ROUTE" }
    private val worker = HandlerThread("route-recorder")
    private lateinit var handler: Handler
    private lateinit var manager: LocationManager
    private var file: File? = null
    private val points = ArrayDeque<RoutePoint>()
    private var started = false
    private var lastRejectedLogMs = -30_000L

    override fun onCreate() {
        super.onCreate()
        worker.start()
        handler = Handler(worker.looper)
        manager = getSystemService(LocationManager::class.java)
        AppLog.initialize(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("route_recording", "行程轨迹记录", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, TripRouteActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 0, Intent(this, TripRouteService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            TripRouteState.publish(RouteUiState(status = "未获精确定位权限，未开始记录"))
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(43, NotificationCompat.Builder(this, "route_recording")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("正在记录本地行程轨迹")
                .setContentText("定位仅保存在本机 · 点击结束可停止定位")
                .setContentIntent(open).setOngoing(true).addAction(0, "结束记录", stop).build())
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { manager.isProviderEnabled(it) }
            if (providers.isEmpty()) error("无可用定位源，请开启车机定位")
            file = TripRouteStore.create(this)
            started = true
            TripRouteState.publish(RouteUiState(true, status = "等待有效定位（精度 ≤ 50 m）"))
            var subscribed = 0
            providers.forEach { provider ->
                runCatching { manager.requestLocationUpdates(provider, 5_000L, 0f, this, worker.looper) }
                    .onSuccess { subscribed++ }
                    .onFailure { AppLog.w("ROUTE", "provider unavailable: $provider", it) }
            }
            check(subscribed > 0) { "车机拒绝了定位订阅" }
            AppLog.i("ROUTE", "explicit recording started; local-only WGS84; providers=$providers")
        } catch (error: Exception) {
            TripRouteState.publish(RouteUiState(status = "无法开始记录：${error.message}"))
            AppLog.e("ROUTE", "start failed", error)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val reading = FuelCollectionState.latestTimedReading
        val fresh = reading?.takeIf {
            SystemClock.elapsedRealtime() - it.readCompletedElapsedMs in 0L..15_000L
        }?.snapshot
        val candidate = RoutePoint(location.time, location.elapsedRealtimeNanos / 1_000_000L,
            location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY,
            if (location.hasSpeed()) location.speed * 3.6f else null,
            fresh?.avgFuelTrip2, fresh?.trip2DistanceKm)
        val now = SystemClock.elapsedRealtime()
        val point = RouteGeometry.accept(candidate, points.lastOrNull(), now)
        if (point == null) {
            if (now - lastRejectedLogMs >= 30_000L) {
                lastRejectedLogMs = now
                AppLog.w("ROUTE", "fix rejected ageMs=${now - candidate.elapsedMs} accuracyM=${candidate.accuracyMetres} speedKmh=${candidate.speedKmh}; coordinates omitted")
                if (points.isEmpty()) TripRouteState.publish(RouteUiState(true, status = "定位读数无效（时间或精度异常），等待有效定位"))
            }
            return
        }
        try {
            TripRouteStore.append(file ?: return, point)
            points.addLast(point)
            if (points.size > 10_000) points.removeFirst()
            TripRouteState.publish(RouteUiState(true, points.toList(), "本地记录中 · GPS 车速着色 · 不代表瞬时油耗"))
        } catch (error: Exception) {
            TripRouteState.publish(RouteUiState(false, points.toList(), "保存失败，记录已停止"))
            AppLog.e("ROUTE", "write failed", error)
            stopSelf()
        }
    }
    override fun onProviderDisabled(provider: String) {
        TripRouteState.publish(RouteUiState(started, points.toList(), "定位源不可用：$provider · 等待恢复"))
    }
    override fun onProviderEnabled(provider: String) {}
    @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (::manager.isInitialized) runCatching { manager.removeUpdates(this) }
        // Finish behind queued location callbacks, on the same I/O worker.
        if (::handler.isInitialized) handler.post {
            file?.let { runCatching { TripRouteStore.finish(it) }.onFailure { AppLog.e("ROUTE", "finish failed", it) } }
            val old = TripRouteState.state.value
            TripRouteState.publish(old.copy(recording = false,
                status = if (old.recording) "记录已结束 · 数据仅保存在本机" else old.status))
            worker.quitSafely()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
