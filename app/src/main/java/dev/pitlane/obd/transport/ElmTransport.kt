package dev.pitlane.obd.transport

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.InetAddresses
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Byte-stream abstraction for ELM327-compatible serial/tcp adapters. */
interface ElmTransport {
    suspend fun connect()
    suspend fun exchange(command: String, timeoutMs: Long = 3_500): String
    suspend fun close()
}

/** RFCOMM Serial Port Profile transport for paired Bluetooth Classic ELM327 adapters. */
class BluetoothElmTransport(
    context: Context,
    private val address: String
) : ElmTransport {
    private val appContext = context.applicationContext
    private var socket: BluetoothSocket? = null
    private var stream: PromptStream? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: error("Bluetooth is not available on this device")
        check(adapter.isEnabled) { "Turn on Bluetooth, then try again" }
        val device = adapter.getRemoteDevice(address)
        val candidate = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            candidate.connect()
            socket = candidate
            stream = PromptStream(candidate.inputStream, candidate.outputStream)
        } catch (error: Throwable) {
            runCatching { candidate.close() }
            throw error
        }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String =
        checkNotNull(stream) { "Bluetooth adapter is not connected" }.exchange(command, timeoutMs)

    override suspend fun close() {
        stream?.close()
        stream = null
        withContext(Dispatchers.IO) { runCatching { socket?.close() } }
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

/** Wi-Fi ELM327 adapter transport (user supplies adapter IP and TCP port). */
class WifiElmTransport(
    private val host: String,
    private val port: Int
) : ElmTransport {
    private var socket: Socket? = null
    private var stream: PromptStream? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(validHost(host)) { "Enter a valid adapter IPv4 address or hostname" }
        val candidate = Socket()
        try {
            candidate.connect(InetSocketAddress(host.trim(), port), 7_000)
            candidate.soTimeout = 0
            socket = candidate
            stream = PromptStream(candidate.getInputStream(), candidate.getOutputStream())
        } catch (error: Throwable) {
            runCatching { candidate.close() }
            throw error
        }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String =
        checkNotNull(stream) { "Wi-Fi adapter is not connected" }.exchange(command, timeoutMs)

    override suspend fun close() {
        stream?.close()
        stream = null
        withContext(Dispatchers.IO) { runCatching { socket?.close() } }
        socket = null
    }

    private fun validHost(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > 253 || trimmed.any(Char::isWhitespace)) return false
        if (Build.VERSION.SDK_INT >= 29 && trimmed.count { it == '.' } == 3) {
            return runCatching { InetAddresses.isNumericAddress(trimmed) }.getOrDefault(false)
        }
        return trimmed.matches(Regex("[A-Za-z0-9.-]+"))
    }
}

/** Serializes ELM request/response exchanges and reads complete frames through the `>` prompt. */
private class PromptStream(
    private val input: InputStream,
    private val output: OutputStream
) : Closeable {
    private val frames = Channel<String>(Channel.UNLIMITED)
    private val mutex = Mutex()
    private var reader = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val bytes = StringBuilder()
        val buffer = ByteArray(256)
        try {
            while (currentCoroutineContext().isActive) {
                val count = input.read(buffer)
                if (count < 0) break
                for (index in 0 until count) {
                    val char = (buffer[index].toInt() and 0xFF).toChar()
                    bytes.append(char)
                    if (char == '>') {
                        frames.send(bytes.toString())
                        bytes.clear()
                    } else if (bytes.length > 4096) {
                        bytes.delete(0, bytes.length - 1024)
                    }
                }
            }
        } catch (_: Exception) {
            // Socket closure is the normal way the blocking reader is stopped.
        } finally {
            frames.close()
        }
    }

    suspend fun exchange(command: String, timeoutMs: Long): String = mutex.withLock {
        while (frames.tryReceive().isSuccess) { /* discard any stale prompt before issuing a new command */ }
        withContext(Dispatchers.IO) {
            output.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
            output.flush()
        }
        withTimeout(timeoutMs) { frames.receive() }
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
        reader.cancel()
        frames.close()
    }
}
