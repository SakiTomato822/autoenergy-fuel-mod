package com.lynk.dvrprobe

import org.junit.Assert.*
import org.junit.Test

class FuelQuantityTest {
    @Test fun convertsConfirmedNominalCapacity() {
        assertEquals(11f, FuelQuantity.estimateLitres(22)!!, 0.001f)
        assertEquals(34f, FuelQuantity.estimateLitres(68)!!, 0.001f)
        assertEquals(0f, FuelQuantity.estimateLitres(0)!!, 0f)
        assertEquals(50f, FuelQuantity.estimateLitres(100)!!, 0f)
    }
    @Test fun rejectsMissingOrInvalidReadings() {
        assertNull(FuelQuantity.estimateLitres(null))
        assertNull(FuelQuantity.estimateLitres(101))
        assertNull(FuelQuantity.estimateLitres(-1))
        assertNull(FuelQuantity.estimateLitres(22, Float.NaN))
    }
}
