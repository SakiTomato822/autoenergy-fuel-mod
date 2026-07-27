package com.lynk.dvrprobe

import android.content.Context

data class FuelTrendPoint(
    val timestampMs: Long,
    val value: Float,
    val odometerKm: Float? = null,
)

class FuelTrendStore(context: Context) {
    companion object {
        private const val PREF_KEY_POINTS = "points"
        private const val MAX_POINTS = 1440
        private const val MIN_SAMPLE_INTERVAL_MS = 60_000L
        private const val FORCED_SNAPSHOT_INTERVAL_MS = 10 * 60_000L
        private const val MAX_HISTORY_AGE_MS = 7L * 24L * 60L * 60L * 1000L
        private const val MIN_VALUE_DELTA = 0.05f
    }

    private val prefs = context.getSharedPreferences("fuel_energy_trend", Context.MODE_PRIVATE)

    fun load(): List<FuelTrendPoint> {
        val raw = prefs.getString(PREF_KEY_POINTS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        val cutoff = System.currentTimeMillis() - MAX_HISTORY_AGE_MS
        val points = raw.split("|")
            .mapNotNull { token ->
                val parts = token.split(",")
                if (parts.size !in 2..3) return@mapNotNull null
                val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
                val value = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val odometerKm = parts.getOrNull(2)?.toFloatOrNull()
                FuelTrendPoint(ts, value, odometerKm)
            }
            .filter { it.timestampMs >= cutoff }
            .takeLast(MAX_POINTS)

        if (points.isEmpty() && raw.isNotBlank()) {
            prefs.edit().remove(PREF_KEY_POINTS).apply()
        } else if (points.isNotEmpty()) {
            persist(points)
        }
        return points
    }

    fun append(value: Float, odometerKm: Float? = null) {
        val current = load().toMutableList()
        val now = System.currentTimeMillis()
        val last = current.lastOrNull()
        if (last != null) {
            val deltaMs = now - last.timestampMs
            val tooSoon = deltaMs < MIN_SAMPLE_INTERVAL_MS
            val unchanged = kotlin.math.abs(last.value - value) < MIN_VALUE_DELTA
            val forcedSnapshotDue = deltaMs >= FORCED_SNAPSHOT_INTERVAL_MS
            if (tooSoon || (unchanged && !forcedSnapshotDue)) return
        }
        current += FuelTrendPoint(now, value, odometerKm)
        persist(current.takeLast(MAX_POINTS))
    }

    fun latest(): FuelTrendPoint? = load().lastOrNull()

    fun loadForLastDistance(currentOdometerKm: Float?, distanceKm: Float = 100f): List<FuelTrendPoint> {
        val all = load()
        if (currentOdometerKm == null) return all
        val cutoff = currentOdometerKm - distanceKm
        val distancePoints = all.filter { point ->
            val odometer = point.odometerKm ?: return@filter false
            odometer in cutoff..(currentOdometerKm + 1f)
        }
        return if (distancePoints.isNotEmpty()) distancePoints else all.takeLast(1)
    }

    fun loadForLastHours(hours: Int): List<FuelTrendPoint> {
        val cutoff = System.currentTimeMillis() - hours.coerceAtLeast(1) * 60L * 60L * 1000L
        return load().filter { it.timestampMs >= cutoff }
    }

    private fun persist(points: List<FuelTrendPoint>) {
        val encoded = points.joinToString("|") {
            "${it.timestampMs},${it.value},${it.odometerKm ?: ""}"
        }
        prefs.edit().putString(PREF_KEY_POINTS, encoded).apply()
    }
}
