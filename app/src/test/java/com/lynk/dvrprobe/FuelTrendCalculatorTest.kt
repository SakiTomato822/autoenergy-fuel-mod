package com.lynk.dvrprobe

import org.junit.Assert.*
import org.junit.Test

class FuelTrendCalculatorTest {
    private val pending = FuelHistoryState(hasPrevious = true, previousDistanceKm = 10f,
        previousFuelLitres = 0.6f, chartDistanceKm = 9f, pendingDistanceKm = 2f, pendingFuelLitres = 0.12f)

    @Test fun zeroDistanceWithoutAverageClearsOldTripProgress() {
        val reset = FuelTrendCalculator.observe(pending, null, 0f, 1000)
        assertFalse(reset.hasPrevious)
        assertEquals(0f, reset.pendingDistanceKm, 0f)
        assertEquals(0f, reset.pendingFuelLitres, 0f)
        assertEquals(pending.chartDistanceKm, reset.chartDistanceKm, 0f)
    }

    @Test fun resetObservedAfterDrivingDoesNotMixTrips() {
        val reset = FuelTrendCalculator.observe(pending, 8f, 1f, 1000)
        assertTrue(reset.hasPrevious)
        assertEquals(0f, reset.pendingDistanceKm, 0f)
        val next = FuelTrendCalculator.observe(reset, 8f, 4f, 2000)
        assertEquals(1, next.points.size)
        assertEquals(8f, next.points.single().value, 0.001f)
        assertEquals(3f, next.points.single().spanKm, 0f)
    }

    @Test fun shortTripResetIsNotIgnored() {
        val reset = FuelTrendCalculator.observe(pending.copy(previousDistanceKm = 0.2f), null, 0f, 1000)
        assertFalse(reset.hasPrevious)
        assertEquals(0f, reset.pendingDistanceKm, 0f)
    }

    @Test fun missingReadingsPreserveProgress() {
        assertEquals(pending, FuelTrendCalculator.observe(pending, null, null, 1000))
        assertEquals(pending, FuelTrendCalculator.observe(pending, Float.NaN, 10f, 1000))
    }

    @Test fun idlingFuelIsIncludedInNextSegment() {
        val baseline = FuelHistoryState(hasPrevious = true, previousDistanceKm = 100f, previousFuelLitres = 6f)
        val idle = FuelTrendCalculator.observe(baseline, 6.1f, 100f, 1000)
        assertEquals(baseline, idle)
        val next = FuelTrendCalculator.observe(idle, 6.1f, 103f, 2000)
        assertEquals((6.1f * 103f / 100f - 6f) / 3f * 100f, next.points.single().value, 0.001f)
    }

    @Test fun resetPreservesPublishedHistory() {
        val old = pending.copy(points = listOf(FuelTrendPoint(100, 6f, 3f, 3f)))
        assertEquals(old.points, FuelTrendCalculator.observe(old, null, 0f, 1000).points)
    }
}
