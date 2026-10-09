package com.lynk.dvrprobe

/** Distance-segment estimates from rounded cumulative readings, not instantaneous fuel flow. */
internal object FuelTrendCalculator {
    fun observe(previous: FuelHistoryState, averageFuel: Float?, tripDistanceKm: Float?, timestampMs: Long): FuelHistoryState {
        if (tripDistanceKm == null || !tripDistanceKm.isFinite() || tripDistanceKm < 0f) return previous
        // Zero-distance snapshots deliberately have no average. Detect resets
        // before rejecting that average, including a reset after a very short trip.
        val reset = previous.hasPrevious &&
            (tripDistanceKm - previous.previousDistanceKm < -0.5f ||
                (tripDistanceKm == 0f && previous.previousDistanceKm > 0f))
        val base = if (reset) previous.copy(hasPrevious = false, previousDistanceKm = 0f,
            previousFuelLitres = 0f, pendingDistanceKm = 0f, pendingFuelLitres = 0f) else previous
        if (averageFuel == null || !averageFuel.isFinite() || averageFuel !in 0.1f..60f) return base
        val fuel = averageFuel * tripDistanceKm / 100f
        if (!base.hasPrevious) return base.copy(hasPrevious = true,
            previousDistanceKm = tripDistanceKm, previousFuelLitres = fuel)
        val deltaDistance = tripDistanceKm - base.previousDistanceKm
        // Keep the fuel baseline while idling; include that fuel in the next segment.
        if (deltaDistance <= 0f) return base
        val distance = base.pendingDistanceKm + deltaDistance
        val consumed = base.pendingFuelLitres + fuel - base.previousFuelLitres
        val progress = base.copy(previousDistanceKm = tripDistanceKm, previousFuelLitres = fuel,
            chartDistanceKm = base.chartDistanceKm + deltaDistance,
            pendingDistanceKm = distance, pendingFuelLitres = consumed)
        if (distance < 3f) return progress
        val consumption = consumed / distance * 100f
        if (!consumption.isFinite() || consumption !in 0f..60f) {
            return if (distance < 12f) progress else progress.copy(pendingDistanceKm = 0f, pendingFuelLitres = 0f)
        }
        val point = FuelTrendPoint(timestampMs, consumption, progress.chartDistanceKm, distance)
        return progress.copy(points = progress.points + point, pendingDistanceKm = 0f, pendingFuelLitres = 0f)
    }
}
