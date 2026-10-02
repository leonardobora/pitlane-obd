package dev.pitlane.obd.engine

import dev.pitlane.obd.model.VehicleTelemetry
import dev.pitlane.obd.protocol.ObdPid
import dev.pitlane.obd.protocol.ObdResponseParser
import dev.pitlane.obd.protocol.ReadinessStatus
import dev.pitlane.obd.protocol.DecodedTroubleCode
import dev.pitlane.obd.transport.ElmTransport
import kotlinx.coroutines.delay

class ObdSession(private val transport: ElmTransport) {
    private var supportedPids: Set<Int>? = null

    suspend fun initialize() {
        val commands = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
        var promptSeen = false
        commands.forEach { command ->
            val response = transport.exchange(command, 5_000)
            check(response.isNotBlank()) { "Adapter returned no response to $command" }
            promptSeen = promptSeen || response.contains('>')
            delay(120)
        }
        check(promptSeen) { "Adapter did not return an ELM prompt during initialization" }
        // Capability discovery avoids spending a full timeout on every unsupported PID.
        runCatching { ObdResponseParser.supportedPids(transport.exchange("0100")) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { supportedPids = it }
    }

    suspend fun readTelemetry(previous: VehicleTelemetry = VehicleTelemetry()): VehicleTelemetry {
        var result = previous
        ObdPid.entries
            .filter { supportedPids?.contains(it.id) != false }
            .forEach { pid ->
                val value = runCatching { ObdResponseParser.decode(pid, transport.exchange(pid.command)) }.getOrNull()
                result = if (value != null) result.withReading(pid, value) else result.withoutReading(pid)
            }
        return result
    }

    suspend fun readReadiness(): ReadinessStatus? = ObdResponseParser.readiness(transport.exchange("0101"))

    suspend fun readTroubleCodes(): List<DecodedTroubleCode> =
        ObdResponseParser.troubleCodes(transport.exchange("03"))
}
