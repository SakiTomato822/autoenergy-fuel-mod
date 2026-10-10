package com.lynk.dvrprobe

internal object FuelQuantity {
    // Nominal capacity confirmed by the owner; percentage conversion is approximate.
    const val CAPACITY_LITRES = 50f
    fun estimateLitres(percent: Int?, capacity: Float = CAPACITY_LITRES): Float? {
        if (percent == null || percent !in 0..100 || !capacity.isFinite() || capacity <= 0f) return null
        return percent * capacity / 100f
    }
}
