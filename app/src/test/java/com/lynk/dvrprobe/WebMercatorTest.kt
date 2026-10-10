package com.lynk.dvrprobe

import org.junit.Assert.*
import org.junit.Test

class WebMercatorTest {
    @Test fun originAndLongitudeBounds() {
        assertEquals(0.5, WebMercator.x(0.0), 1e-9)
        assertEquals(0.5, WebMercator.y(0.0), 1e-9)
        assertEquals(0.0, WebMercator.x(-180.0), 1e-9)
        assertEquals(1.0, WebMercator.x(180.0), 1e-9)
    }
    @Test fun polesAreClampedAndNorthIsUp() {
        assertTrue(WebMercator.y(90.0).isFinite())
        assertEquals(WebMercator.y(85.05112878), WebMercator.y(90.0), 1e-9)
        assertTrue(WebMercator.y(26.0) < WebMercator.y(25.0))
    }
    @Test fun fitKeepsWholeRouteVisibleAndBoundsZoom() {
        val zoom = WebMercator.fitZoom(0.01, 0.02, 1000, 600)
        assertTrue(0.01 * WebMercator.worldSize(zoom) <= 872)
        assertTrue(0.02 * WebMercator.worldSize(zoom) <= 472)
        assertEquals(18, WebMercator.fitZoom(0.0, 0.0, 1000, 600))
        assertEquals(2, WebMercator.fitZoom(1.0, 1.0, 1, 1))
    }
}
