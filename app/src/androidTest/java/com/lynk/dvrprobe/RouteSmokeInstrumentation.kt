package com.lynk.dvrprobe

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import java.io.File

/** Emulator-only integration check; not included in the user APK. */
class RouteSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    @Suppress("DEPRECATION")
    override fun onStart() {
        val result = Bundle()
        val manager = targetContext.getSystemService(LocationManager::class.java)
        var mocked = false
        var code = Activity.RESULT_CANCELED
        try {
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) {
                "This test must not run on a real vehicle"
            }
            targetContext.startActivity(Intent(targetContext, TripRouteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Thread.sleep(1500)
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false,
                true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
            mocked = true
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            targetContext.startForegroundService(Intent(targetContext, TripRouteService::class.java))
            Thread.sleep(1000)
            repeat(3) { index ->
                manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = 26.0 + index * 0.0001
                    longitude = 119.0 + index * 0.0001
                    accuracy = 5f; speed = 3f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                })
                Thread.sleep(5500)
                if (index == 0) targetContext.startActivity(Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            targetContext.stopService(Intent(targetContext, TripRouteService::class.java))
            Thread.sleep(1000)
            val file = File(targetContext.noBackupFilesDir, "routes").listFiles()!!.maxBy { it.name }
            val lines = file.readLines()
            check(lines.count { it.contains("\"latitude\"") } >= 3) { "GPS points not persisted: $lines" }
            check(lines.last().contains("endedAtMs")) { "Stop marker missing" }
            result.putString("result", "PASS: three mock fixes persisted including in background; explicit stop finalized local file")
            code = Activity.RESULT_OK
        } catch (error: Throwable) {
            result.putString("error", error.stackTraceToString())
        } finally {
            targetContext.stopService(Intent(targetContext, TripRouteService::class.java))
            if (mocked) runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        }
        finish(code, result)
    }
}
