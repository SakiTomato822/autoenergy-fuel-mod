package com.lynk.dvrprobe

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TripRouteStoreTest {
    private val header = "{\"format\":\"AutoEnergyRoute\",\"schemaVersion\":1,\"coordinates\":\"WGS84\"}\n"
    private val point = RoutePoint(1000, 100, 26.0, 119.0, 5f, 20f, null, null, true)
    private fun withFile(text: String, test: (File) -> Unit) {
        val file = File.createTempFile("route-test-", ".jsonl")
        try { file.writeText(text); test(file) } finally { file.delete() }
    }
    @Test fun readsValidPointsAndIgnoresInterruptedTail() {
        withFile(header + Gson().toJson(point) + "\n{\"timestampMs\":") {
            assertEquals(listOf(point), TripRouteStore.read(it))
        }
    }
    @Test fun rejectsWrongSchemaAndCoordinateSystem() {
        for (invalid in listOf(header.replace("WGS84", "GCJ02"), header.replace(":1", ":2"))) {
            withFile(invalid) { file -> assertThrows(Exception::class.java) { TripRouteStore.read(file) } }
        }
    }
    @Test fun skipsMalformedCoordinatesAndStopMarker() {
        withFile(header + Gson().toJson(point.copy(latitude = 100.0)) + "\n{\"timestampMs\":1000}\n{\"endedAtMs\":1000}\n") {
            assertTrue(TripRouteStore.read(it).isEmpty())
        }
    }
}
