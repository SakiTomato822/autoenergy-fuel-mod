package com.lynk.dvrprobe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    companion object {
        private const val CAR_PERMISSION_REQUEST = 1001
        private val runtimeCarPermissions = arrayOf(
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_SPEED",
        )
    }

    private lateinit var reader: FuelEnergyReader
    private lateinit var energyView: OriginalEnergyView
    private lateinit var simulatedProperties: SimulatedCarProperties
    private lateinit var trendStore: FuelTrendStore
    private var pollJob: Job? = null
    private var previewMode = false
    private var simulationReceiverRegistered = false
    private var pollSequence = 0L
    private var lastAvailabilitySignature: String? = null
    private var lastErrorDiagnostics: Set<String> = emptySet()

    private val simulationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!previewMode) return
            AppLog.i("SIM", "received command action=${intent.action} extras=${intent.extras?.keySet()}")
            simulatedProperties.applyCommand(intent)
            energyView.setSnapshot(simulatedProperties.snapshot(), isPreview = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.initialize(this)
        val metrics = resources.displayMetrics
        AppLog.i(
            "BOOT",
            "version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER}/${Build.MODEL} " +
                "display=${metrics.widthPixels}x${metrics.heightPixels}@${metrics.densityDpi}dpi",
        )
        setContentView(R.layout.activity_main)
        enterImmersiveMode()

        reader = FuelEnergyReader(this)
        trendStore = FuelTrendStore(this)
        energyView = findViewById(R.id.energyView)

        previewMode = runCatching { Class.forName("android.car.Car") }.isFailure
        if (previewMode) {
            AppLog.i("MODE", "android.car.Car unavailable; using simulator")
            simulatedProperties = SimulatedCarProperties(initialScenario = "aggressive")
            simulatedProperties.applyCommand(intent)
            energyView.setSnapshot(simulatedProperties.snapshot(), isPreview = true)
        } else {
            AppLog.i("MODE", "android.car.Car available; using vehicle CarProperty data")
            requestRuntimeCarPermissions()
            startPolling()
        }

        energyView.setActionCallbacks(
            onSingleTripResetOptionChanged = ::changeSingleTripResetOption,
            onSubtotalResetRequested = ::resetSubtotalTrip,
            onDiagnosticsRequested = ::openDiagnostics,
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onStart() {
        super.onStart()
        AppLog.d("LIFECYCLE", "onStart previewMode=$previewMode")
        if (previewMode && !simulationReceiverRegistered) {
            val filter = IntentFilter(SimulatedCarProperties.ACTION_UPDATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(simulationReceiver, filter, RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(simulationReceiver, filter)
            }
            simulationReceiverRegistered = true
            AppLog.d("SIM", "simulation receiver registered")
        }
    }

    override fun onStop() {
        if (simulationReceiverRegistered) {
            unregisterReceiver(simulationReceiver)
            simulationReceiverRegistered = false
            AppLog.d("SIM", "simulation receiver unregistered")
        }
        AppLog.d("LIFECYCLE", "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        AppLog.i("LIFECYCLE", "onDestroy polling cancelled")
        super.onDestroy()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollSequence = 0L
        AppLog.i("POLL", "vehicle polling started interval=5000ms")
        pollJob = lifecycleScope.launch {
            while (isActive) {
                runCatching {
                    val snapshot = withContext(Dispatchers.IO) { reader.readSnapshot() }
                    pollSequence += 1
                    logSnapshot(snapshot)
                    val averageFuel = snapshot.avgFuelTrip1 ?: snapshot.avgFuelTrip2
                    if (averageFuel != null) {
                        trendStore.append(averageFuel, snapshot.odometerKm)
                    }
                    energyView.setSnapshot(snapshot, isPreview = false)
                    energyView.setTrendPoints(
                        trendStore.loadForLastHours(24),
                    )
                }.onFailure {
                    AppLog.e("POLL", "poll#$pollSequence failed", it)
                }
                delay(5_000L)
            }
        }
    }

    private fun enterImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun requestRuntimeCarPermissions() {
        val missing = runtimeCarPermissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            AppLog.w("PERMISSION", "requesting missing car permissions=$missing")
            requestPermissions(missing.toTypedArray(), CAR_PERMISSION_REQUEST)
        } else {
            AppLog.i("PERMISSION", "runtime car permissions already granted")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAR_PERMISSION_REQUEST) return
        val result = permissions.mapIndexed { index, permission ->
            "$permission=${grantResults.getOrNull(index) == android.content.pm.PackageManager.PERMISSION_GRANTED}"
        }
        AppLog.i("PERMISSION", "runtime permission result=$result")
    }

    private fun changeSingleTripResetOption(option: Int) {
        AppLog.i("ACTION", "single-trip reset option requested value=$option previewMode=$previewMode")
        if (previewMode) {
            simulatedProperties.setSingleTripResetOption(option)
            energyView.setSnapshot(simulatedProperties.snapshot(), isPreview = true)
            AppLog.i("ACTION", "simulator reset option applied value=$option")
            return
        }
        lifecycleScope.launch {
            val (success, refreshed) = withContext(Dispatchers.IO) {
                val success = reader.writeSingleTripResetOption(option)
                success to reader.readSnapshot()
            }
            val fallback = if (success) {
                option
            } else if (option == SimulatedCarProperties.RESET_OPTION_CHARGING) {
                SimulatedCarProperties.RESET_OPTION_PARKING
            } else {
                SimulatedCarProperties.RESET_OPTION_CHARGING
            }
            energyView.setSnapshot(
                refreshed.copy(singleTripResetOption = refreshed.singleTripResetOption ?: fallback),
                isPreview = false,
            )
            AppLog.i(
                "ACTION",
                "single-trip reset option result success=$success reported=${refreshed.singleTripResetOption}",
            )
            if (!success) {
                Toast.makeText(this@MainActivity, "车机未授权修改自动重置方式", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetSubtotalTrip() {
        AppLog.i("ACTION", "subtotal reset requested previewMode=$previewMode")
        if (previewMode) {
            simulatedProperties.resetSubtotalTrip()
            energyView.setSnapshot(simulatedProperties.snapshot(), isPreview = true)
            AppLog.i("ACTION", "simulator subtotal reset applied")
            return
        }
        lifecycleScope.launch {
            val (success, refreshed) = withContext(Dispatchers.IO) {
                val success = reader.resetSubtotalTrip()
                success to reader.readSnapshot()
            }
            energyView.setSnapshot(refreshed, isPreview = false)
            AppLog.i("ACTION", "subtotal reset result success=$success")
            if (!success) {
                Toast.makeText(this@MainActivity, "车机未授权重置小计里程", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDiagnostics() {
        AppLog.i("UI", "diagnostics requested from main card long press")
        startActivity(Intent(this, DiagnosticsActivity::class.java))
    }

    private fun logSnapshot(snapshot: FuelEnergySnapshot) {
        val availability = listOf(
            snapshot.avgFuelTrip1 != null,
            snapshot.avgFuelTrip2 != null,
            snapshot.fuelPercent != null,
            snapshot.oilRangeKm != null,
            snapshot.totalRangeKm != null,
            snapshot.odometerKm != null,
            snapshot.trip1DistanceKm != null,
            snapshot.trip2DistanceKm != null,
        ).joinToString(separator = "") { if (it) "1" else "0" }

        val shouldLogSummary =
            pollSequence == 1L ||
                availability != lastAvailabilitySignature ||
                pollSequence % 12L == 0L
        if (shouldLogSummary) {
            AppLog.i(
                "SNAPSHOT",
                "poll#$pollSequence availability=$availability " +
                    "avg1=${snapshot.avgFuelTrip1} avg2=${snapshot.avgFuelTrip2} " +
                    "fuel=${snapshot.fuelPercent}% oilRange=${snapshot.oilRangeKm}km " +
                    "totalRange=${snapshot.totalRangeKm}km odo=${snapshot.odometerKm}km " +
                    "trip1=${snapshot.trip1DistanceKm}km trip2=${snapshot.trip2DistanceKm}km " +
                    "reset=${snapshot.singleTripResetOption}",
            )
            lastAvailabilitySignature = availability
        }

        if (pollSequence == 1L) {
            snapshot.diagnostics.forEach { AppLog.d("CAR", it) }
        }
        val errors = snapshot.diagnostics
            .filter {
                it.contains("error", ignoreCase = true) ||
                    it.contains("invalid", ignoreCase = true) ||
                    it.contains("null", ignoreCase = true)
            }
            .toSet()
        (errors - lastErrorDiagnostics).forEach { AppLog.w("CAR", it) }
        lastErrorDiagnostics = errors
    }

}
