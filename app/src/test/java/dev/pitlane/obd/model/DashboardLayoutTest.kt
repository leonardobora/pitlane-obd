package dev.pitlane.obd.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardLayoutTest {
    @Test fun unknownStoredLayoutFallsBackToCockpit() {
        assertEquals(DashboardLayout.COCKPIT, DashboardLayout.fromStoredValue("not-a-layout"))
        assertEquals(DashboardLayout.COCKPIT, DashboardLayout.fromStoredValue(null))
    }

    @Test fun layoutsExposeStableCardCompositions() {
        assertEquals(
            listOf(DashboardCard.SPEED, DashboardCard.RPM, DashboardCard.COOLANT, DashboardCard.THROTTLE, DashboardCard.LOAD, DashboardCard.MANIFOLD, DashboardCard.FUEL, DashboardCard.VOLTAGE),
            DashboardLayout.COCKPIT.cards
        )
        assertEquals(
            listOf(DashboardCard.RPM, DashboardCard.SPEED, DashboardCard.THROTTLE, DashboardCard.LOAD, DashboardCard.COOLANT, DashboardCard.VOLTAGE),
            DashboardLayout.RACE_TELEMETRY.cards
        )
    }

    @Test fun cardsReadOnlyMapToSharedTelemetry() {
        val telemetry = VehicleTelemetry(rpm = 2400.0, speedKmh = 54.0, voltageV = 14.2)
        assertEquals(2400.0, DashboardCard.RPM.valueOf(telemetry)!!, 0.001)
        assertEquals(14.2, DashboardCard.VOLTAGE.valueOf(telemetry)!!, 0.001)
        assertNull(DashboardCard.COOLANT.valueOf(telemetry))
    }
}
