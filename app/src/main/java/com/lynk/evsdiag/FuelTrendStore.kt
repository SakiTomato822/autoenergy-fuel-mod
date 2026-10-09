package com.lynk.dvrprobe

import android.content.Context

data class FuelTrendPoint(
    val timestampMs: Long,
    val value: Float,
    val distanceKm: Float,
    val spanKm: Float,
)

/** All durable history and sampling progress live in the shared JSON source. */
class FuelTrendStore(private val context: Context) {
    fun observe(averageFuel: Float?, tripDistanceKm: Float?) {
        var added = false
        LocalFuelDataSource.update(context) { previous ->
            val next = FuelTrendCalculator.observe(previous, averageFuel, tripDistanceKm, System.currentTimeMillis())
            added = next.points.size > previous.points.size
            if (added) next.points.last().let {
                AppLog.i("TREND", "estimated segment appended distance=${it.spanKm} consumption=${it.value}")
            }
            if (previous.pendingDistanceKm > 0f && next.pendingDistanceKm == 0f && !added &&
                tripDistanceKm != null && tripDistanceKm < previous.previousDistanceKm) {
                AppLog.i("TREND", "trip reset; discarded unfinished segment=${previous.pendingDistanceKm}km")
            }
            next
        }
        LocalFuelDataSource.flush(force = added)
    }

    fun flush() = LocalFuelDataSource.flush()
    fun load(): List<FuelTrendPoint> = LocalFuelDataSource.read(context).points

    fun loadForLastDistance(distanceKm: Float = 100f): List<FuelTrendPoint> {
        val all = load()
        val latest = all.lastOrNull()?.distanceKm ?: return emptyList()
        val first = all.indexOfFirst { it.distanceKm >= latest - distanceKm.coerceAtLeast(1f) }
        return if (first < 0) all.takeLast(1) else all.drop((first - 1).coerceAtLeast(0))
    }

    fun weightedAverage(points: List<FuelTrendPoint>): Float? {
        val distance = points.sumOf { it.spanKm.toDouble() }
        return if (distance <= 0) null else (points.sumOf { (it.value * it.spanKm).toDouble() } / distance).toFloat()
    }
}
