package com.lynk.dvrprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class McuByteValueTest {
    private fun dump(bytes: String, status: Int = 0, area: String = "0") =
        "Property:0x28700440,status: $status,timestamp:123,zone:0x$area,floatValues: [],int32Values: [],int64Values: [],bytes: [$bytes],string: "

    @Test fun readsCapturedAverageAndRealZero() {
        assertEquals(57, McuByteValue.decode(dump("0, 57"), "28700440", 2, 1000))
        assertEquals(0, McuByteValue.decode(dump("0, 0"), "28700440", 2, 1000))
    }

    @Test fun decodesSignedBytesAsUnsignedBigEndian() {
        assertEquals(12014, McuByteValue.decode(dump("0, 0, 46, -18"), "28700440", 4, 2000000))
    }

    @Test fun readsCapturedTripDistanceAndRejectsInvalidSpeed() {
        val subtotal = dump("0, 0, 82, 126").replace("28700440", "28700020")
        assertEquals(2111.8f, McuByteValue.decode(subtotal, "28700020", 4, 20_000_000) / 10f, 0.01f)
        assertEquals(0, McuByteValue.decode(dump("0, 0, 0, 0"), "28700440", 4, 20_000_000))
        assertThrows(IllegalStateException::class.java) {
            McuByteValue.decode(dump("31, -1"), "28700440", 2, 300)
        }
    }

    @Test fun rejectsInvalidAndUnavailableReadings() {
        for (output in listOf(dump("-1, -1"), dump("57"), dump("0, 57", 1),
            dump("0, 57", area = "1"), "Permission Denial: requires android.permission.DUMP")) {
            assertThrows(IllegalStateException::class.java) {
                McuByteValue.decode(output, "28700440", 2, 1000)
            }
        }
    }
}
