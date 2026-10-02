package dev.pitlane.obd.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdResponseParserTest {
    @Test fun decodesRpmFromSpacedResponseAndIgnoresCommandEcho() {
        assertEquals(1726.0, ObdResponseParser.decode(ObdPid.RPM, "010C\r\n41 0C 1A F8\r>" )!!, 0.001)
    }

    @Test fun decodesValuesWithCanHeaderAndAdapterChatter() {
        assertEquals(60.0, ObdResponseParser.decode(ObdPid.SPEED, "SEARCHING...\r7E8 03 41 0D 3C\r>" )!!, 0.001)
        assertEquals(50.0, ObdResponseParser.decode(ObdPid.COOLANT_TEMP, "41055A>" )!!, 0.001)
        assertEquals(0.0, ObdResponseParser.decode(ObdPid.SHORT_FUEL_TRIM, "410680>" )!!, 0.001)
        assertEquals(13.104, ObdResponseParser.decode(ObdPid.CONTROL_MODULE_VOLTAGE, "41423330>" )!!, 0.001)
    }

    @Test fun unavailableAndMalformedResponsesStayUnavailable() {
        assertNull(ObdResponseParser.decode(ObdPid.RPM, "NO DATA>"))
        assertNull(ObdResponseParser.decode(ObdPid.RPM, "410C1A>"))
        assertNull(ObdResponseParser.decode(ObdPid.RPM, "?"))
        assertTrue(ObdResponseParser.supportedPids("NO DATA>").isEmpty())
    }

    @Test fun decodesPidSupportBitmapMostAndLeastSignificantBits() {
        val supported = ObdResponseParser.supportedPids("410080000001>")
        assertTrue(1 in supported)
        assertTrue(32 in supported)
        assertFalse(2 in supported)
        assertEquals((1..16).toSet(), ObdResponseParser.supportedPids("4100FFFF0000>"))
    }

    @Test fun decodesAndDeduplicatesDtcBytesAndSkipsPadding() {
        val codes = ObdResponseParser.troubleCodes("430133C1000000>").map { it.code }
        assertEquals(listOf("P0133", "U0100"), codes)
    }

    @Test fun decodesReadinessMilCountAndMonitorCompletion() {
        val status = ObdResponseParser.readiness("410100000101>")!!
        assertFalse(status.milOn)
        assertEquals(0, status.confirmedDtcCount)
        assertTrue(status.monitors.any { it.name == "Catalyst" && it.supported && it.complete })
    }
    @Test fun ignoresNoDataAndIncompleteDtcResponses() {
        assertTrue(ObdResponseParser.troubleCodes("NO DATA>").isEmpty())
        assertTrue(ObdResponseParser.troubleCodes("4301>").isEmpty())
    }
}
