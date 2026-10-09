package com.lynk.dvrprobe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FuelCollectionState {
    private val current = MutableStateFlow<FuelEnergySnapshot?>(null)
    val snapshots = current.asStateFlow()
    fun publish(snapshot: FuelEnergySnapshot) { current.value = snapshot }
}

class FuelCollectionService : Service() {
    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FuelCollectionService::class.java))
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var reader: FuelEnergyReader
    private var collecting: Job? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.initialize(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("fuel_collection", "油耗后台记录", NotificationManager.IMPORTANCE_LOW))
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(42, NotificationCompat.Builder(this, "fuel_collection")
            .setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("能量中心正在记录")
            .setContentText("后台采集油耗与里程 · 每累计 3 km 记录曲线点")
            .setContentIntent(intent).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build())
        reader = FuelEnergyReader(applicationContext)
        AppLog.i("COLLECTOR", "foreground service created version=${BuildConfig.VERSION_NAME}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (collecting?.isActive != true) collecting = scope.launch {
            val store = FuelTrendStore(applicationContext)
            var sequence = 0L
            var lastErrors = emptySet<String>()
            while (isActive) {
                try {
                    val snapshot = reader.readSnapshot()
                    FuelCollectionState.publish(snapshot)
                    store.observe(snapshot.avgFuelTrip2, snapshot.trip2DistanceKm)
                    sequence++
                    if (sequence == 1L || sequence % 12L == 0L) {
                        AppLog.i("COLLECTOR", "sample#$sequence avg1=${snapshot.avgFuelTrip1} avg2=${snapshot.avgFuelTrip2} trip1=${snapshot.trip1DistanceKm} trip2=${snapshot.trip2DistanceKm} odo=${snapshot.odometerKm} fuel=${snapshot.fuelPercent}% range=${snapshot.oilRangeKm}km reset=${snapshot.singleTripResetOption} points=${store.load().size}")
                    }
                    if (sequence == 1L) snapshot.diagnostics.forEach { AppLog.d("CAR", it) }
                    val errors = snapshot.diagnostics.filter { it.contains("error", true) || it.contains("invalid", true) }.toSet()
                    (errors - lastErrors).forEach { AppLog.w("CAR", it) }
                    lastErrors = errors
                    store.flush()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Throwable) { AppLog.e("COLLECTOR", "sample failed", error) }
                delay(5_000L)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        if (::reader.isInitialized) reader.close()
        AppLog.i("COLLECTOR", "service destroyed; system may restart sticky service")
        super.onDestroy()
    }
}

class FuelBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AppLog.initialize(context)
        runCatching { FuelCollectionService.start(context) }
            .onSuccess { AppLog.i("COLLECTOR", "auto start received action=${intent.action}") }
            .onFailure { AppLog.e("COLLECTOR", "auto start failed", it) }
    }
}
