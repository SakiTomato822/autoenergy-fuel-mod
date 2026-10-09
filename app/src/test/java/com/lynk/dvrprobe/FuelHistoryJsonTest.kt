package com.lynk.dvrprobe

import org.junit.Assert.*
import org.junit.Test

class FuelHistoryJsonTest {
    private val state = FuelHistoryState(
        points = listOf(FuelTrendPoint(1000, 6.2f, 3f, 3f)),
        hasPrevious = true, previousDistanceKm = 4.5f, previousFuelLitres = 0.3f,
        chartDistanceKm = 4.5f, pendingDistanceKm = 1.5f, pendingFuelLitres = 0.1f)

    @Test fun roundTripKeepsPointsAndUnfinishedSegment() {
        assertEquals(state, FuelHistoryJson.decode(FuelHistoryJson.encode(state)))
    }

    @Test fun rejectsCorruptionAndWrongFormat() {
        val json = FuelHistoryJson.encode(state)
        for (invalid in listOf(json.dropLast(10), json.replace("6.2", "9.2"), json.replace("AutoEnergyLocalData", "Other"))) {
            assertThrows(Exception::class.java) { FuelHistoryJson.decode(invalid) }
        }
    }

    @Test fun refusesInvalidProgressBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java) { FuelHistoryJson.encode(state.copy(pendingDistanceKm = Float.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { FuelHistoryJson.encode(state.copy(points = listOf(FuelTrendPoint(1000, -1f, 3f, 3f)))) }
    }

    @Test fun keepsFullHistoryRatherThanOnlyLast140Km() {
        val history = state.copy(points = (1..200).map { FuelTrendPoint(it.toLong(), 6f, it * 3f, 3f) })
        assertEquals(200, FuelHistoryJson.decode(FuelHistoryJson.encode(history)).points.size)
    }
}
