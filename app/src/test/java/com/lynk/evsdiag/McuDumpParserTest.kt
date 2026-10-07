package com.lynk.dvrprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class McuDumpParserTest {
    private fun dump(bytes: String, status: Int = 0, area: Int = 0) =
        "VehiclePropValue{prop=0x28700440, areaId=$area, status=$status, byteValues=[$bytes]}"
    @Test fun decodesReportedFuel() { assertEquals(57L, McuDumpParser.parse(dump("0,57"), 0x28700440, 2)) }
    @Test fun unsignedSignedBytes() { assertEquals(255L, McuDumpParser.parse(dump("0,-1"), 0x28700440, 2)) }
    @Test fun rangeFourBytes() { assertEquals(97L, McuDumpParser.parse(dump("0,0,0,97"), 0x28700440, 4)) }
    @Test fun refusesWrongProperty() { assertNull(McuDumpParser.parse(dump("0,57"), 0x28700048, 2)) }
    @Test fun refusesWrongArea() { assertNull(McuDumpParser.parse(dump("0,57", area=1), 0x28700440, 2)) }
    @Test fun refusesUnavailable() { assertNull(McuDumpParser.parse(dump("0,57", status=1), 0x28700440, 2)) }
    @Test fun refusesWrongLength() { assertNull(McuDumpParser.parse(dump("57"), 0x28700440, 2)) }
    @Test fun refusesMalformedByte() { assertNull(McuDumpParser.parse(dump("0,256"), 0x28700440, 2)) }
    @Test fun refusesMissingStatus() { assertNull(McuDumpParser.parse("prop=0x28700440 area=0 bytes=[0,57]", 0x28700440, 2)) }
}
