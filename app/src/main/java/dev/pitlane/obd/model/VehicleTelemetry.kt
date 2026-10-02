package dev.pitlane.obd.model

import dev.pitlane.obd.protocol.ObdPid
import kotlin.math.roundToInt

data class VehicleTelemetry(
    val rpm: Double? = null,
    val speedKmh: Double? = null,
    val coolantC: Double? = null,
    val intakeC: Double? = null,
    val throttlePct: Double? = null,
    val engineLoadPct: Double? = null,
    val manifoldKpa: Double? = null,
    val shortFuelTrimPct: Double? = null,
    val longFuelTrimPct: Double? = null,
    val fuelLevelPct: Double? = null,
    val mafGps: Double? = null,
    val voltageV: Double? = null,
    val receivedAtMs: Long = System.currentTimeMillis()
) {
    fun withReading(pid: ObdPid, value: Double, atMs: Long = System.currentTimeMillis()): VehicleTelemetry = when (pid) {
        ObdPid.RPM -> copy(rpm = value, receivedAtMs = atMs)
        ObdPid.SPEED -> copy(speedKmh = value, receivedAtMs = atMs)
        ObdPid.COOLANT_TEMP -> copy(coolantC = value, receivedAtMs = atMs)
        ObdPid.INTAKE_TEMP -> copy(intakeC = value, receivedAtMs = atMs)
        ObdPid.THROTTLE -> copy(throttlePct = value, receivedAtMs = atMs)
        ObdPid.ENGINE_LOAD -> copy(engineLoadPct = value, receivedAtMs = atMs)
        ObdPid.INTAKE_PRESSURE -> copy(manifoldKpa = value, receivedAtMs = atMs)
        ObdPid.SHORT_FUEL_TRIM -> copy(shortFuelTrimPct = value, receivedAtMs = atMs)
        ObdPid.LONG_FUEL_TRIM -> copy(longFuelTrimPct = value, receivedAtMs = atMs)
        ObdPid.FUEL_LEVEL -> copy(fuelLevelPct = value, receivedAtMs = atMs)
        ObdPid.MAF -> copy(mafGps = value, receivedAtMs = atMs)
        ObdPid.CONTROL_MODULE_VOLTAGE -> copy(voltageV = value, receivedAtMs = atMs)
    }

    companion object {
        /** Coherent synthetic values for UI exploration; never used in LIVE state. */
        fun demo(tick: Int, nowMs: Long = System.currentTimeMillis()): VehicleTelemetry {
            val phase = tick * 0.19
            val rpm = (1650 + 1850 * (0.5 + 0.5 * kotlin.math.sin(phase))).roundToInt().toDouble()
            val speed = (32 + rpm / 45.0 + 9 * kotlin.math.sin(phase * 0.31)).coerceIn(0.0, 128.0)
            val load = (18 + 47 * (0.5 + 0.5 * kotlin.math.sin(phase + 0.9))).coerceIn(0.0, 100.0)
            return VehicleTelemetry(
                rpm = rpm,
                speedKmh = speed,
                coolantC = 88 + 2.4 * kotlin.math.sin(phase * 0.07),
                intakeC = 28 + 1.8 * kotlin.math.sin(phase * 0.11 + 1.0),
                throttlePct = (12 + 31 * (0.5 + 0.5 * kotlin.math.sin(phase + 0.5))).coerceIn(0.0, 100.0),
                engineLoadPct = load,
                manifoldKpa = 31 + 22 * (0.5 + 0.5 * kotlin.math.sin(phase + 0.4)),
                shortFuelTrimPct = 1.6 * kotlin.math.sin(phase * 0.6),
                longFuelTrimPct = 2.3,
                fuelLevelPct = 67.0,
                mafGps = (2.0 + load * 0.17).coerceIn(2.0, 40.0),
                voltageV = 14.1 + 0.16 * kotlin.math.sin(phase * 0.2),
                receivedAtMs = nowMs
            )
        }
    }
}

data class TelemetrySample(
    val elapsedMs: Long,
    val telemetry: VehicleTelemetry
)

enum class ConnectionMode { DEMO, CONNECTING, LIVE }

enum class AppTab { LIVE, FAULTS, SESSION, SETTINGS }
