package com.lynk.dvrprobe

import android.content.Context

/** Read-only DHU615G MCU fallback, requiring the ADB-grantable DUMP permission. */
class CarShellFuelSource(private val context: Context) {
    data class Reading(
        val fuelPercent: Int?,
        val fuelRangeKm: Int?,
        val avgFuelSubtotal: Float?,
        val avgFuelThisTrip: Float?,
        val singleTripResetOption: Int?,
        val odometerKm: Float? = null,
        val subtotalDistanceKm: Float? = null,
        val currentDistanceKm: Float? = null,
        val subtotalSpeedKmh: Float? = null,
        val currentSpeedKmh: Float? = null,
        val subtotalDurationMinutes: Int? = null,
        val currentDurationMinutes: Int? = null,
    )

    fun read(diagnostics: MutableList<String>): Reading {
        val raw = McuDumpSource(context).read(diagnostics)
        fun value(key: String, maximum: Long): Int? = raw[key]?.let {
            if (it in 0..maximum) it.toInt() else {
                diagnostics += "mcu.dump $key invalid value=$it"
                null
            }
        }
        val supportedUnit = raw["fuelUnit"] == 1L
        if (!supportedUnit) diagnostics += "mcu.dump fuel unit unavailable or unsupported: ${raw["fuelUnit"]}"
        return Reading(
            fuelPercent = value("fuelPercent", 100),
            fuelRangeKm = value("oilRange", 5000),
            avgFuelSubtotal = if (supportedUnit) value("subtotalFuel", 1000)?.div(10f) else null,
            avgFuelThisTrip = if (supportedUnit) value("currentFuel", 1000)?.div(10f) else null,
            singleTripResetOption = when (raw["resetOption"]) {
                1L -> 612369154
                2L -> 612369156
                else -> null
            },
            odometerKm = value("odometer", 2_000_000)?.toFloat(),
            subtotalDistanceKm = value("subtotalDistance", 20_000_000)?.div(10f),
            currentDistanceKm = value("currentDistance", 20_000_000)?.div(10f),
            subtotalSpeedKmh = value("subtotalSpeed", 300)?.toFloat(),
            currentSpeedKmh = value("currentSpeed", 300)?.toFloat(),
            subtotalDurationMinutes = value("subtotalDuration", 315_360_000)?.div(60),
            currentDurationMinutes = value("currentDuration", 315_360_000)?.div(60),
        )
    }
}

internal object McuByteValue {
    fun decode(output: String, propertyId: String, expectedBytes: Int, maximum: Int): Int {
        val value = McuDumpParser.parse(output, propertyId.toLong(16), expectedBytes)
            ?: error("property unavailable or invalid")
        check(value in 0L..maximum.toLong()) { "invalid value=$value" }
        return value.toInt()
    }
}
