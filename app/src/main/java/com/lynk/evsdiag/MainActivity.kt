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
    private var pollJob: Job? = null
    private var previewMode = false
    private var simulationReceiverRegistered = false

    private val simulationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!previewMode) return
            simulatedProperties.applyCommand(intent)
            energyView.setSnapshot(simulatedProperties.snapshot(), isPreview = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enterImmersiveMode()

        reader = FuelEnergyReader(this)
        energyView = findViewById(R.id.energyView)

        previewMode = runCatching { Class.forName("android.car.Car") }.isFailure
        if (previewMode) {
            simulatedProperties = SimulatedCarProperties(initialScenario = "aggressive")
            simulatedProperties.applyCommand(intent)
            energyView.setSnapshot(simulatedProperties.snapshot(), isPreview = true)
        } else {
            requestRuntimeCarPermissions()
            startPolling()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onStart() {
        super.onStart()
        if (previewMode && !simulationReceiverRegistered) {
            val filter = IntentFilter(SimulatedCarProperties.ACTION_UPDATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(simulationReceiver, filter, RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(simulationReceiver, filter)
            }
            simulationReceiverRegistered = true
        }
    }

    override fun onStop() {
        if (simulationReceiverRegistered) {
            unregisterReceiver(simulationReceiver)
            simulationReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                val snapshot = withContext(Dispatchers.IO) { reader.readSnapshot() }
                energyView.setSnapshot(snapshot, isPreview = false)
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
            requestPermissions(missing.toTypedArray(), CAR_PERMISSION_REQUEST)
        }
    }

}
