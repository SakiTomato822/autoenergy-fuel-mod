package com.lynk.dvrprobe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var reader: FuelEnergyReader
    private lateinit var energyView: OriginalEnergyView
    private lateinit var simulatedProperties: SimulatedCarProperties
    private lateinit var trendStore: FuelTrendStore
    private var pollJob: Job? = null
    private var previewMode = false
    private var simulationReceiverRegistered = false
    private lateinit var permissions: FuelPermissionGate

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
        WindowCompat.setDecorFitsSystemWindows(window, true)
        AppLog.initialize(this)
        val metrics = resources.displayMetrics
        AppLog.i(
            "BOOT",
            "version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER}/${Build.MODEL} " +
                "display=${metrics.widthPixels}x${metrics.heightPixels}@${metrics.densityDpi}dpi",
        )
        setContentView(R.layout.activity_main)

        reader = FuelEnergyReader(this)
        trendStore = FuelTrendStore(this)
        energyView = findViewById(R.id.energyView)
        permissions = FuelPermissionGate(this) { if (!previewMode) startPolling() }
        logContentViewport()

        previewMode = runCatching { Class.forName("android.car.Car") }.isFailure
        if (previewMode) {
            AppLog.i("MODE", "android.car.Car unavailable; using simulator")
            simulatedProperties = SimulatedCarProperties(initialScenario = "aggressive")
            simulatedProperties.applyCommand(intent)
            energyView.setSnapshot(simulatedProperties.snapshot(), isPreview = true)
        } else {
            AppLog.i("MODE", "android.car.Car available; using vehicle CarProperty data")
            permissions.check(promptWhenMissing = true)
        }

        energyView.setActionCallbacks(
            onSingleTripResetOptionChanged = ::changeSingleTripResetOption,
            onSubtotalResetRequested = ::resetSubtotalTrip,
            onDiagnosticsRequested = ::openDiagnostics,
        )
        if (intent.getBooleanExtra(FuelPermissionGate.EXTRA_CHECK, false) && previewMode) {
            permissions.check(promptWhenMissing = true)
        }
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

    override fun onResume() {
        super.onResume()
        if (::permissions.isInitialized && !previewMode) permissions.check(promptWhenMissing = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(FuelPermissionGate.EXTRA_CHECK, false)) {
            permissions.check(promptWhenMissing = true)
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        if (::permissions.isInitialized) permissions.close()
        reader.close()
        AppLog.i("LIFECYCLE", "onDestroy polling cancelled")
        super.onDestroy()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true || isFinishing || isDestroyed) return
        runCatching { FuelCollectionService.start(this) }.onFailure {
            AppLog.e("PERMISSION", "collector startup failed", it)
            Toast.makeText(this, "采集服务未能启动，请在诊断页查看原因", Toast.LENGTH_LONG).show()
            return
        }
        pollJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                FuelCollectionState.snapshots.collect { snapshot ->
                    if (snapshot != null) {
                        energyView.setSnapshot(snapshot, isPreview = false)
                        energyView.setTrendPoints(withContext(Dispatchers.IO) {
                            runCatching { trendStore.loadForLastDistance(100f) }
                                .onFailure { AppLog.e("DATA", "history unavailable; select an intact JSON file", it) }
                                .getOrDefault(emptyList())
                        })
                    }
                }
            }
        }
    }

    private fun logContentViewport() {
        energyView.post {
            val insets = ViewCompat.getRootWindowInsets(energyView)
                ?.getInsets(WindowInsetsCompat.Type.systemBars())
            AppLog.i(
                "DISPLAY",
                "content=${energyView.width}x${energyView.height} " +
                    "systemInsets=${insets?.left},${insets?.top},${insets?.right},${insets?.bottom} " +
                    "decorFitsSystemWindows=true",
            )
        }
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
            energyView.setSnapshot(
                refreshed,
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

}
