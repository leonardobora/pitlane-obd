package dev.pitlane.obd.model

/** A named composition of the shared telemetry stream. Layouts do not own or alter session state. */
enum class DashboardLayout(
    val title: String,
    val description: String,
    val cards: List<DashboardCard>
) {
    COCKPIT(
        title = "Cockpit",
        description = "Speed and RPM heroes with supporting engine metrics.",
        cards = listOf(
            DashboardCard.SPEED,
            DashboardCard.RPM,
            DashboardCard.COOLANT,
            DashboardCard.THROTTLE,
            DashboardCard.LOAD,
            DashboardCard.MANIFOLD,
            DashboardCard.FUEL,
            DashboardCard.VOLTAGE
        )
    ),
    RACE_TELEMETRY(
        title = "Race telemetry",
        description = "Six equally weighted values for a compact telemetry view.",
        cards = listOf(
            DashboardCard.RPM,
            DashboardCard.SPEED,
            DashboardCard.THROTTLE,
            DashboardCard.LOAD,
            DashboardCard.COOLANT,
            DashboardCard.VOLTAGE
        )
    );

    companion object {
        const val STORAGE_KEY = "dashboard_layout"

        fun fromStoredValue(value: String?): DashboardLayout =
            entries.firstOrNull { it.name == value } ?: COCKPIT
    }
}

enum class DashboardCard(val label: String, val unit: String) {
    SPEED("SPEED", "km/h"),
    RPM("RPM", "rpm"),
    COOLANT("COOLANT", "°C"),
    THROTTLE("THROTTLE", "%"),
    LOAD("LOAD", "%"),
    MANIFOLD("MANIFOLD", "kPa"),
    FUEL("FUEL", "%"),
    VOLTAGE("VOLTAGE", "V");

    fun valueOf(telemetry: VehicleTelemetry): Double? = when (this) {
        SPEED -> telemetry.speedKmh
        RPM -> telemetry.rpm
        COOLANT -> telemetry.coolantC
        THROTTLE -> telemetry.throttlePct
        LOAD -> telemetry.engineLoadPct
        MANIFOLD -> telemetry.manifoldKpa
        FUEL -> telemetry.fuelLevelPct
        VOLTAGE -> telemetry.voltageV
    }
}
