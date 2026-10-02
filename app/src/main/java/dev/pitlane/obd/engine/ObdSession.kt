package dev.pitlane.obd.engine

import dev.pitlane.obd.model.VehicleTelemetry
import dev.pitlane.obd.protocol.ObdPid
import dev.pitlane.obd.protocol.ObdResponseParser
import dev.pitlane.obd.protocol.ReadinessStatus
import dev.pitlane.obd.protocol.DecodedTroubleCode
import dev.pitlane.obd.transport.ElmTransport
import kotlinx.coroutines.delay

class ObdSession(private val transport: ElmTransport) {
    suspend fun initialize() {
        listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0").forEach { command ->
            runCatching { transport.exchange(command, 5_000) }
            delay(120)
        }
    }

    suspend fun readTelemetry(previous: VehicleTelemetry = VehicleTelemetry()): VehicleTelemetry {
        var result = previous
        ObdPid.entries.forEach { pid ->
            val value = runCatching { ObdResponseParser.decode(pid, transport.exchange(pid.command)) }.getOrNull()
            if (value != null) result = result.withReading(pid, value)
        }
        return result
    }

    suspend fun readReadiness(): ReadinessStatus? = ObdResponseParser.readiness(transport.exchange("0101"))

    suspend fun readTroubleCodes(): List<DecodedTroubleCode> =
        ObdResponseParser.troubleCodes(transport.exchange("03"))
}
