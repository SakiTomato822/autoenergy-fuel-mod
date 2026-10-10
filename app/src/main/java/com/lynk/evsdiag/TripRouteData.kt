package com.lynk.dvrprobe

import kotlin.math.*

data class RoutePoint(
    val timestampMs: Long,
    val elapsedMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float,
    val speedKmh: Float?,
    val cumulativeAverageFuel: Float?,
    val cumulativeTripKm: Float?,
    val breakBefore: Boolean = false,
)

internal object RouteGeometry {
    fun distanceMetres(a: RoutePoint, b: RoutePoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun accept(point: RoutePoint, previous: RoutePoint?, nowElapsedMs: Long): RoutePoint? {
        if (!point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0 ||
            !point.accuracyMetres.isFinite() || point.accuracyMetres !in 0f..50f ||
            point.timestampMs <= 0 || point.elapsedMs < 0 ||
            nowElapsedMs - point.elapsedMs !in 0L..30_000L ||
            (point.speedKmh != null && (!point.speedKmh.isFinite() || point.speedKmh !in 0f..250f))) return null
        if (previous == null) return point.copy(breakBefore = true)
        val dt = point.elapsedMs - previous.elapsedMs
        if (dt < 4_000L) return null
        // Do not join across lost positioning, or a process/clock session boundary.
        if (dt > 30_000L) return point.copy(breakBefore = true)
        if (distanceMetres(previous, point) / (dt / 1000.0) > 70.0) return null
        return point.copy(breakBefore = false)
    }
}
