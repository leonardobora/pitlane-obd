package dev.pitlane.obd.protocol

import java.util.Locale

/** Standard generic emissions-related PIDs used by the live dashboard. */
enum class ObdPid(
    val id: Int,
    val label: String,
    val unit: String,
    val payloadBytes: Int
) {
    ENGINE_LOAD(0x04, "Engine load", "%", 1),
    COOLANT_TEMP(0x05, "Coolant", "°C", 1),
    SHORT_FUEL_TRIM(0x06, "STFT", "%", 1),
    LONG_FUEL_TRIM(0x07, "LTFT", "%", 1),
    INTAKE_PRESSURE(0x0B, "Manifold", "kPa", 1),
    RPM(0x0C, "Engine speed", "rpm", 2),
    SPEED(0x0D, "Vehicle speed", "km/h", 1),
    INTAKE_TEMP(0x0F, "Intake air", "°C", 1),
    MAF(0x10, "Air flow", "g/s", 2),
    THROTTLE(0x11, "Throttle", "%", 1),
    FUEL_LEVEL(0x2F, "Fuel level", "%", 1),
    CONTROL_MODULE_VOLTAGE(0x42, "ECU voltage", "V", 2);

    val command: String get() = "01" + id.toString(16).uppercase(Locale.US).padStart(2, '0')
}

data class DecodedTroubleCode(
    val code: String,
    val family: String,
    val description: String
)

data class ReadinessMonitor(val name: String, val supported: Boolean, val complete: Boolean)

data class ReadinessStatus(
    val milOn: Boolean,
    val confirmedDtcCount: Int,
    val diesel: Boolean,
    val monitors: List<ReadinessMonitor>
)

/** Pure ELM/OBD response decoding. Handles compact or spaced bytes, echoed requests,
 * CAN headers, CR/LF and the ELM prompt without treating missing data as zero. */
object ObdResponseParser {
    private val hexLine = Regex("^[0-9A-Fa-f\\s:>]+$")

    fun decode(pid: ObdPid, response: String): Double? {
        val payload = responseBytes(service = 0x41, pid = pid.id, length = pid.payloadBytes, response = response)
            ?: return null
        val a = payload[0].toInt() and 0xFF
        val b = payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
        return when (pid) {
            ObdPid.ENGINE_LOAD, ObdPid.THROTTLE, ObdPid.FUEL_LEVEL -> a * 100.0 / 255.0
            ObdPid.COOLANT_TEMP, ObdPid.INTAKE_TEMP -> (a - 40).toDouble()
            ObdPid.SHORT_FUEL_TRIM, ObdPid.LONG_FUEL_TRIM -> (a - 128) * 100.0 / 128.0
            ObdPid.INTAKE_PRESSURE -> a.toDouble()
            ObdPid.RPM -> (a * 256 + b) / 4.0
            ObdPid.SPEED -> a.toDouble()
            ObdPid.MAF -> (a * 256 + b) / 100.0
            ObdPid.CONTROL_MODULE_VOLTAGE -> (a * 256 + b) / 1000.0
        }
    }

    /** Decode Mode 01 PID 01: MIL, confirmed DTC count, and generic readiness bits. */
    fun readiness(response: String): ReadinessStatus? {
        val bytes = responseBytes(service = 0x41, pid = 0x01, length = 4, response = response) ?: return null
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        val c = bytes[2].toInt() and 0xFF
        val d = bytes[3].toInt() and 0xFF
        val diesel = b and 0x08 != 0
        val common = listOf(
            ReadinessMonitor("Fuel system", b and 0x02 != 0, b and 0x20 == 0),
            ReadinessMonitor("Misfire", b and 0x01 != 0, b and 0x10 == 0)
        )
        val specificNames = if (diesel) {
            listOf("NMHC catalyst", "NOx after-treatment", "Exhaust gas sensor", "Reserved", "Boost pressure", "Reserved", "PM filter", "EGR / VVT")
        } else {
            listOf("Catalyst", "Heated catalyst", "Evaporative system", "Secondary air", "Reserved", "Oxygen sensor", "Oxygen sensor heater", "EGR / VVT")
        }
        val specific = specificNames.mapIndexedNotNull { index, name ->
            if (name == "Reserved") null else ReadinessMonitor(name, c and (1 shl index) != 0, d and (1 shl index) != 0)
        }
        return ReadinessStatus(a and 0x80 != 0, a and 0x7F, diesel, common + specific)
    }
    /** Decode the 32-PID support bitmap returned for Mode 01 PID 00, 20, 40… */
    fun supportedPids(response: String, requestedBasePid: Int = 0x00): Set<Int> {
        val bytes = responseBytes(service = 0x41, pid = requestedBasePid, length = 4, response = response)
            ?: return emptySet()
        return buildSet {
            for (bitIndex in 0 until 32) {
                val byteValue = bytes[bitIndex / 8].toInt() and 0xFF
                if ((byteValue and (1 shl (7 - bitIndex % 8))) != 0) {
                    add(requestedBasePid + bitIndex + 1)
                }
            }
        }
    }

    /** Decode Mode 03 DTC response bytes (e.g. 43 01 33 -> P0133). */
    fun troubleCodes(response: String): List<DecodedTroubleCode> {
        val payload = responseBytesAfterService(service = 0x43, response = response) ?: return emptyList()
        if (payload.size < 2) return emptyList()
        return payload.asList()
            .chunked(2)
            .takeWhile { it.size == 2 }
            .mapNotNull { pair ->
                val first = pair[0].toInt() and 0xFF
                val second = pair[1].toInt() and 0xFF
                if (first == 0 && second == 0) return@mapNotNull null
                val family = when (first ushr 6) {
                    0 -> "P"
                    1 -> "C"
                    2 -> "B"
                    else -> "U"
                }
                val digit = (first ushr 4) and 0x03
                val code = family + digit.toString() + (first and 0x0F).toString(16).uppercase(Locale.US) +
                    second.toString(16).uppercase(Locale.US).padStart(2, '0')
                DecodedTroubleCode(code, family, "")
            }
            .distinctBy { it.code }
    }

    private fun responseBytes(service: Int, pid: Int, length: Int, response: String): ByteArray? {
        val needle = "%02X%02X".format(Locale.US, service, pid)
        val compact = responseHex(response)
        val start = compact.indexOf(needle, ignoreCase = true)
        if (start < 0) return null
        val from = start + needle.length
        val to = from + length * 2
        if (to > compact.length) return null
        return decodeHex(compact.substring(from, to))
    }

    private fun responseBytesAfterService(service: Int, response: String): ByteArray? {
        val compact = responseHex(response)
        val needle = "%02X".format(Locale.US, service)
        val start = compact.indexOf(needle, ignoreCase = true)
        if (start < 0) return null
        val payload = compact.substring(start + needle.length)
        if (payload.length < 2 || payload.length % 2 != 0) return null
        return decodeHex(payload)
    }

    /** Keep only lines that consist of response bytes; drop echoed AT commands and adapter chatter. */
    private fun responseHex(response: String): String = response
        .uppercase(Locale.US)
        .lineSequence()
        .map(String::trim)
        .filter { line -> line.isNotEmpty() && hexLine.matches(line) }
        .joinToString(separator = "") { line -> line.filter { it.isHexDigit() } }

    private fun decodeHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return runCatching {
            ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'A'..'F'
}
