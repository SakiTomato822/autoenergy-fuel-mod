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
        if (averageFuel == null || tripDistanceKm == null || !averageFuel.isFinite() || !tripDistanceKm.isFinite()) return
        if (averageFuel !in 0.1f..60f || tripDistanceKm < 0f) return
        val fuel = averageFuel * tripDistanceKm / 100f
        var added = false
        LocalFuelDataSource.update(context) { previous ->
            if (!previous.hasPrevious || tripDistanceKm - previous.previousDistanceKm < -0.5f) {
                return@update previous.copy(hasPrevious = true, previousDistanceKm = tripDistanceKm, previousFuelLitres = fuel)
            }
            val deltaDistance = tripDistanceKm - previous.previousDistanceKm
            // Retain the fuel baseline while idling, so fuel burned at zero
            // distance contributes to the next driven segment.
            if (deltaDistance <= 0f) return@update previous
            val distance = previous.pendingDistanceKm + deltaDistance
            val consumed = previous.pendingFuelLitres + fuel - previous.previousFuelLitres
            val progress = previous.copy(previousDistanceKm = tripDistanceKm, previousFuelLitres = fuel,
                chartDistanceKm = previous.chartDistanceKm + deltaDistance,
                pendingDistanceKm = distance, pendingFuelLitres = consumed)
            if (distance < 3f) return@update progress
            val consumption = consumed / distance * 100f
            if (!consumption.isFinite() || consumption !in 0f..60f) {
                return@update if (distance < 12f) progress else progress.copy(pendingDistanceKm = 0f, pendingFuelLitres = 0f)
            }
            added = true
            val point = FuelTrendPoint(System.currentTimeMillis(), consumption, progress.chartDistanceKm, distance)
            AppLog.i("TREND", "segment appended distance=$distance consumption=$consumption")
            progress.copy(points = progress.points + point, pendingDistanceKm = 0f, pendingFuelLitres = 0f)
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
