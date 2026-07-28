package com.lynk.dvrprobe

import android.content.Context
import kotlin.math.abs

data class FuelTrendPoint(
    val timestampMs: Long,
    val value: Float,
    val distanceKm: Float,
    val spanKm: Float,
)

/**
 * Builds a distance-based fuel history from the current-trip cumulative values.
 *
 * For a cumulative average A (L/100km) at distance D (km), consumed fuel is
 * A * D / 100. Subtracting two sufficiently separated samples gives the fuel
 * consumption for that road segment. Samples are kept on an app-local distance
 * axis so several trips can form one continuous 50/100 km chart.
 */
class FuelTrendStore(context: Context) {
    companion object {
        private const val PREF_KEY_POINTS = "distance_points_v2"
        private const val PREF_PREVIOUS_DISTANCE = "previous_trip_distance_v2"
        private const val PREF_PREVIOUS_FUEL = "previous_trip_fuel_v2"
        private const val PREF_CHART_DISTANCE = "chart_distance_v2"
        private const val PREF_PENDING_DISTANCE = "pending_distance_v2"
        private const val PREF_PENDING_FUEL = "pending_fuel_v2"
        private const val PREF_HAS_PREVIOUS = "has_previous_v2"

        private const val MIN_SEGMENT_DISTANCE_KM = 3f
        private const val MAX_PENDING_DISTANCE_KM = 12f
        private const val RESET_TOLERANCE_KM = 0.5f
        private const val MAX_REASONABLE_CONSUMPTION = 60f
        private const val MAX_HISTORY_DISTANCE_KM = 140f
        private const val MAX_POINTS = 160
    }

    private val prefs = context.getSharedPreferences("fuel_energy_trend", Context.MODE_PRIVATE)

    fun observe(averageFuel: Float?, tripDistanceKm: Float?) {
        if (averageFuel == null || tripDistanceKm == null) return
        if (!averageFuel.isFinite() || !tripDistanceKm.isFinite()) return
        if (averageFuel !in 0.1f..MAX_REASONABLE_CONSUMPTION || tripDistanceKm < 0f) return

        val currentFuelLitres = averageFuel * tripDistanceKm / 100f
        if (!prefs.getBoolean(PREF_HAS_PREVIOUS, false)) {
            savePrevious(tripDistanceKm, currentFuelLitres)
            AppLog.d("TREND", "distance curve baseline initialized trip=$tripDistanceKm avg=$averageFuel")
            return
        }

        val previousDistance = prefs.getFloat(PREF_PREVIOUS_DISTANCE, tripDistanceKm)
        val previousFuel = prefs.getFloat(PREF_PREVIOUS_FUEL, currentFuelLitres)
        val deltaDistance = tripDistanceKm - previousDistance

        if (deltaDistance < -RESET_TOLERANCE_KM) {
            savePrevious(tripDistanceKm, currentFuelLitres)
            AppLog.i(
                "TREND",
                "trip reset detected old=$previousDistance new=$tripDistanceKm; pending segment retained",
            )
            return
        }
        if (deltaDistance <= 0f) {
            savePrevious(tripDistanceKm, currentFuelLitres)
            return
        }

        val deltaFuel = currentFuelLitres - previousFuel
        val pendingDistance = prefs.getFloat(PREF_PENDING_DISTANCE, 0f) + deltaDistance
        val pendingFuel = prefs.getFloat(PREF_PENDING_FUEL, 0f) + deltaFuel
        val chartDistance = prefs.getFloat(PREF_CHART_DISTANCE, 0f) + deltaDistance
        prefs.edit()
            .putFloat(PREF_CHART_DISTANCE, chartDistance)
            .putFloat(PREF_PENDING_DISTANCE, pendingDistance)
            .putFloat(PREF_PENDING_FUEL, pendingFuel)
            .apply()
        savePrevious(tripDistanceKm, currentFuelLitres)

        if (pendingDistance < MIN_SEGMENT_DISTANCE_KM) return

        val segmentConsumption = pendingFuel / pendingDistance * 100f

        if (!segmentConsumption.isFinite() ||
            segmentConsumption < 0f ||
            segmentConsumption > MAX_REASONABLE_CONSUMPTION
        ) {
            if (pendingDistance < MAX_PENDING_DISTANCE_KM) return
            AppLog.w(
                "TREND",
                "discarded accumulated segment distance=$pendingDistance fuel=$pendingFuel consumption=$segmentConsumption",
            )
            clearPending()
            return
        }

        val points = load().toMutableList()
        points += FuelTrendPoint(
            timestampMs = System.currentTimeMillis(),
            value = segmentConsumption,
            distanceKm = chartDistance,
            spanKm = pendingDistance,
        )
        persist(trim(points, chartDistance))
        clearPending()
        AppLog.i(
            "TREND",
            "segment appended distance=$pendingDistance consumption=$segmentConsumption chartKm=$chartDistance",
        )
    }

    fun load(): List<FuelTrendPoint> {
        val raw = prefs.getString(PREF_KEY_POINTS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split("|")
            .mapNotNull { token ->
                val parts = token.split(",")
                if (parts.size != 4) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val value = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val distance = parts[2].toFloatOrNull() ?: return@mapNotNull null
                val span = parts[3].toFloatOrNull() ?: return@mapNotNull null
                FuelTrendPoint(timestamp, value, distance, span)
            }
            .filter {
                it.value.isFinite() &&
                    it.value in 0f..MAX_REASONABLE_CONSUMPTION &&
                    it.distanceKm.isFinite() &&
                    it.spanKm > 0f
            }
            .sortedBy { it.distanceKm }
            .takeLast(MAX_POINTS)
    }

    fun loadForLastDistance(distanceKm: Float = 100f): List<FuelTrendPoint> {
        val all = load()
        val latestDistance = all.lastOrNull()?.distanceKm ?: return emptyList()
        val cutoff = latestDistance - distanceKm.coerceAtLeast(1f)
        val firstInside = all.indexOfFirst { it.distanceKm >= cutoff }
        if (firstInside < 0) return all.takeLast(1)
        val includePrevious = (firstInside - 1).coerceAtLeast(0)
        return all.drop(includePrevious)
    }

    fun weightedAverage(points: List<FuelTrendPoint>): Float? {
        val totalDistance = points.sumOf { it.spanKm.toDouble() }.toFloat()
        if (totalDistance <= 0f) return null
        val weighted = points.sumOf { (it.value * it.spanKm).toDouble() }.toFloat()
        return weighted / totalDistance
    }

    private fun trim(points: List<FuelTrendPoint>, latestDistance: Float): List<FuelTrendPoint> {
        val cutoff = latestDistance - MAX_HISTORY_DISTANCE_KM
        return points
            .filter { it.distanceKm >= cutoff || abs(it.distanceKm - cutoff) < it.spanKm }
            .takeLast(MAX_POINTS)
    }

    private fun savePrevious(distanceKm: Float, fuelLitres: Float) {
        prefs.edit()
            .putBoolean(PREF_HAS_PREVIOUS, true)
            .putFloat(PREF_PREVIOUS_DISTANCE, distanceKm)
            .putFloat(PREF_PREVIOUS_FUEL, fuelLitres)
            .apply()
    }

    private fun clearPending() {
        prefs.edit()
            .putFloat(PREF_PENDING_DISTANCE, 0f)
            .putFloat(PREF_PENDING_FUEL, 0f)
            .apply()
    }

    private fun persist(points: List<FuelTrendPoint>) {
        val encoded = points.joinToString("|") {
            "${it.timestampMs},${it.value},${it.distanceKm},${it.spanKm}"
        }
        prefs.edit().putString(PREF_KEY_POINTS, encoded).apply()
    }
}
