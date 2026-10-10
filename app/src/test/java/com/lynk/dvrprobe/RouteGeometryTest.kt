package com.lynk.dvrprobe

import org.junit.Assert.*
import org.junit.Test

class RouteGeometryTest {
    private val a = RoutePoint(1000, 10_000, 26.0, 119.0, 5f, 40f, null, null)
    @Test fun rejectsStaleImpreciseAndInvalidPositions() {
        assertNull(RouteGeometry.accept(a, null, 50_001))
        assertNull(RouteGeometry.accept(a.copy(accuracyMetres = 100f), null, 10_000))
        assertNull(RouteGeometry.accept(a.copy(latitude = Double.NaN), null, 10_000))
        assertNull(RouteGeometry.accept(a.copy(longitude = 200.0), null, 10_000))
    }
    @Test fun throttlesDuplicatesAndRejectsTeleport() {
        assertNull(RouteGeometry.accept(a.copy(elapsedMs = 12_000), a, 12_000))
        assertNull(RouteGeometry.accept(a.copy(elapsedMs = 15_000, latitude = 27.0), a, 15_000))
    }
    @Test fun breaksPolylineAfterSignalGap() {
        val next = RouteGeometry.accept(a.copy(elapsedMs = 50_000), a, 50_000)!!
        assertTrue(next.breakBefore)
        assertTrue(RouteGeometry.accept(a, null, 10_000)!!.breakBefore)
    }
    @Test fun connectsValidNearbyFixes() {
        val b = a.copy(elapsedMs = 15_000, latitude = 26.0001)
        assertFalse(RouteGeometry.accept(b, a, 15_000)!!.breakBefore)
        assertEquals(11.12, RouteGeometry.distanceMetres(a, b), 0.1)
    }
}
