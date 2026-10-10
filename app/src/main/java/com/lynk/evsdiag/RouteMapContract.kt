package com.lynk.dvrprobe

import kotlin.math.*

/** Provider boundary: persisted points always remain WGS84. An AMap implementation
 * must convert display coordinates to GCJ-02 without rewriting recorded history. */
internal interface RouteMapController {
    fun showRoute(points: List<RoutePoint>)
    fun fitRoute()
    fun releaseMap()
}

internal object WebMercator {
    fun x(longitude: Double) = (longitude + 180.0) / 360.0
    fun y(latitude: Double): Double {
        val radians = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
        return (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0
    }
    fun worldSize(zoom: Int) = 256.0 * (1 shl zoom)
    fun fitZoom(spanX: Double, spanY: Double, width: Int, height: Int): Int =
        (18 downTo 2).firstOrNull { zoom ->
            spanX * worldSize(zoom) <= (width - 128).coerceAtLeast(1) &&
                spanY * worldSize(zoom) <= (height - 128).coerceAtLeast(1)
        } ?: 2
}
