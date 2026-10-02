package dev.pitlane.obd

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.pitlane.obd.engine.ObdSession
import dev.pitlane.obd.model.AppTab
import dev.pitlane.obd.model.ConnectionMode
import dev.pitlane.obd.model.VehicleTelemetry
import dev.pitlane.obd.protocol.DecodedTroubleCode
import dev.pitlane.obd.protocol.ReadinessStatus
import dev.pitlane.obd.transport.BluetoothElmTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF090D12)
private val Panel = Color(0xFF111923)
private val Panel2 = Color(0xFF17212C)
private val TextMain = Color(0xFFF4F0E8)
private val TextMuted = Color(0xFF91A0AA)
private val Orange = Color(0xFFF05A3E)
private val Green = Color(0xFF48D597)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PitlaneTheme {
                PitlaneApp(
                    onRequestBluetooth = {
                        val permissions = if (Build.VERSION.SDK_INT >= 31) {
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        } else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                        permissionLauncher.launch(permissions)
                    },
                    bluetoothDevices = bondedDevices()
                )
            }
        }
    }

    private fun bondedDevices(): List<BluetoothDevice> = runCatching {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
        adapter.bondedDevices.sortedBy { it.name ?: it.address }
    }.getOrDefault(emptyList())
}

@Composable
private fun PitlaneApp(onRequestBluetooth: () -> Unit, bluetoothDevices: List<BluetoothDevice>) {
    var tab by remember { mutableStateOf(AppTab.LIVE) }
    var mode by remember { mutableStateOf(ConnectionMode.DEMO) }
    var telemetry by remember { mutableStateOf(VehicleTelemetry.demo(0)) }
    var tick by remember { mutableIntStateOf(0) }
    var readiness by remember { mutableStateOf<ReadinessStatus?>(null) }
    var codes by remember { mutableStateOf<List<DecodedTroubleCode>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var sessionJob by remember { mutableStateOf<Job?>(null) }
    var activeSession by remember { mutableStateOf<ObdSession?>(null) }
    var showDevices by remember { mutableStateOf(false) }
    var showWifi by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(mode) {
        if (mode == ConnectionMode.DEMO) {
            while (true) {
                telemetry = VehicleTelemetry.demo(tick++)
                delay(900)
            }
        }
    }

    fun startBluetooth(device: BluetoothDevice) {
        showDevices = false
        mode = ConnectionMode.CONNECTING
        error = null
        sessionJob?.cancel()
        sessionJob = scope.launch {
            val transport = BluetoothElmTransport(context, device.address)
            try {
                transport.connect()
                val session = ObdSession(transport)
                activeSession = session
                session.initialize()
                mode = ConnectionMode.LIVE
                while (true) {
                    telemetry = session.readTelemetry(telemetry)
                    delay(1_000)
                }
            } catch (e: Exception) {
                mode = ConnectionMode.DEMO
                activeSession = null
                error = e.message ?: "Could not connect to the adapter"
            } finally { transport.close() }
        }
    }

    Scaffold(containerColor = Ink, bottomBar = { BottomTabs(tab) { tab = it } }) { padding ->
        Column(Modifier.fillMaxSize().background(Ink).padding(padding).padding(horizontal = 16.dp)) {
            Header(mode, onConnect = { showDevices = true }, onDemo = { sessionJob?.cancel(); mode = ConnectionMode.DEMO })
            error?.let { Text(it, color = Orange, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            when (tab) {
                AppTab.LIVE -> LiveScreen(telemetry, mode)
                AppTab.FAULTS -> FaultsScreen(readiness, codes, onRead = {
                    val session = activeSession
                    if (session == null) {
                        error = "Connect an adapter to read live faults."
                    } else {
                        scope.launch {
                            runCatching {
                                readiness = session.readReadiness()
                                codes = session.readTroubleCodes()
                            }.onFailure { error = it.message ?: "Adapter did not return diagnostic data" }
                        }
                    }
                })
                AppTab.SESSION -> SessionScreen(telemetry)
                AppTab.SETTINGS -> SettingsScreen(onRequestBluetooth, onWifi = { showWifi = true })
            }
        }
    }
    if (showDevices) DeviceDialog(bluetoothDevices, onRequestBluetooth, ::startBluetooth) { showDevices = false }
    if (showWifi) WifiDialog(onDismiss = { showWifi = false }, onError = { error = it })
}

@Composable private fun PitlaneTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Ink, surface = Panel, primary = Orange, onSurface = TextMain), content = content)

@Composable private fun Header(mode: ConnectionMode, onConnect: () -> Unit, onDemo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("PITLANE", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp); Text("OBD // COCKPIT", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp) }
        val live = mode == ConnectionMode.LIVE
        Text(if (live) "● LIVE" else if (mode == ConnectionMode.CONNECTING) "○ LINKING" else "● DEMO", color = if (live) Green else Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
        OutlinedButton(onClick = if (live) onDemo else onConnect, modifier = Modifier.height(36.dp)) { Text(if (live) "STOP" else "CONNECT", fontSize = 11.sp) }
    }
}

@Composable private fun LiveScreen(t: VehicleTelemetry, mode: ConnectionMode) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HeroGauge("SPEED", t.speedKmh, "km/h", Modifier.weight(1f))
            HeroGauge("RPM", t.rpm, "rpm", Modifier.weight(1f))
        }
        Text("LIVE TELEMETRY", color = TextMuted, fontSize = 11.sp, letterSpacing = 2.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Metric("COOLANT", t.coolantC, "°C", Modifier.weight(1f)); Metric("THROTTLE", t.throttlePct, "%", Modifier.weight(1f)); Metric("LOAD", t.engineLoadPct, "%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Metric("MANIFOLD", t.manifoldKpa, "kPa", Modifier.weight(1f)); Metric("FUEL", t.fuelLevelPct, "%", Modifier.weight(1f)); Metric("VOLTAGE", t.voltageV, "V", Modifier.weight(1f))
        }
        Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text(if (mode == ConnectionMode.DEMO) "Synthetic telemetry — no vehicle connected" else "Read-only generic emissions telemetry", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(12.dp)) }
    }
}

@Composable private fun HeroGauge(label: String, value: Double?, unit: String, modifier: Modifier) { Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = modifier) { Column(Modifier.padding(16.dp)) { Text(label, color = TextMuted, fontSize = 10.sp, letterSpacing = 1.5.sp); Text(value?.let { if (unit == "rpm") it.toInt().toString() else "%.0f".format(it) } ?: "—", color = TextMain, fontSize = 42.sp, fontWeight = FontWeight.Black); Text(unit, color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun Metric(label: String, value: Double?, unit: String, modifier: Modifier) { Surface(color = Panel2, shape = RoundedCornerShape(8.dp), modifier = modifier) { Column(Modifier.padding(10.dp)) { Text(label, color = TextMuted, fontSize = 9.sp); Text(value?.let { "%.1f".format(it) } ?: "—", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(unit, color = TextMuted, fontSize = 10.sp) } } }

@Composable private fun FaultsScreen(readiness: ReadinessStatus?, codes: List<DecodedTroubleCode>, onRead: () -> Unit) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("FAULTS & READINESS", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); Button(onClick = onRead) { Text("READ FROM ADAPTER") }; Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("DIAGNOSTIC TROUBLE CODES", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp); Spacer(Modifier.height(8.dp)); Text(if (codes.isEmpty()) "No live reading yet" else codes.joinToString { it.code }, color = TextMain) } }; Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("READINESS MONITORS", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp); Spacer(Modifier.height(8.dp)); Text(readiness?.let { "MIL: ${if (it.milOn) "ON" else "OFF"}  •  DTCs: ${it.confirmedDtcCount}" } ?: "Connect an adapter to inspect monitor status", color = if (readiness?.milOn == true) Orange else TextMain); readiness?.monitors?.forEach { monitor -> Text("${if (monitor.complete) "✓" else "○"} ${monitor.name}", color = if (monitor.complete) Green else TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) } } } } }

@Composable private fun SessionScreen(t: VehicleTelemetry) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("SESSION", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("LOCAL-ONLY SESSION", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp); Spacer(Modifier.height(10.dp)); Text("Samples are not uploaded. Export will be added after the live adapter path is verified.", color = TextMain, fontSize = 14.sp); Spacer(Modifier.height(10.dp)); Text("Last sample: ${t.receivedAtMs}", color = TextMuted, fontSize = 11.sp) } } } }

@Composable private fun SettingsScreen(onBluetooth: () -> Unit, onWifi: () -> Unit) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("SETTINGS", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("ADAPTERS", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp); OutlinedButton(onClick = onBluetooth) { Text("Bluetooth Classic / ELM327") }; OutlinedButton(onClick = onWifi) { Text("Wi-Fi adapter") }; HorizontalDivider(color = Color(0xFF2A3540)); Text("Privacy: no account, no analytics, no internet permission.", color = TextMuted, fontSize = 12.sp); Text("Safety: read-only generic OBD telemetry. Never use the app while driving.", color = TextMuted, fontSize = 12.sp) } } } }

@Composable private fun BottomTabs(selected: AppTab, onSelect: (AppTab) -> Unit) { TabRow(selectedTabIndex = AppTab.entries.indexOf(selected), containerColor = Ink, contentColor = Orange) { AppTab.entries.forEach { tab -> TabItem(tab, selected == tab, onSelect) } } }
@Composable private fun TabItem(tab: AppTab, selected: Boolean, onSelect: (AppTab) -> Unit) { Text(tab.name, color = if (selected) Orange else TextMuted, fontSize = 10.sp, modifier = Modifier.clickable { onSelect(tab) }.padding(vertical = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }

@Composable private fun DeviceDialog(devices: List<BluetoothDevice>, onRequest: () -> Unit, onSelect: (BluetoothDevice) -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text("PAIRED ADAPTERS", color = TextMain) }, text = { Column { if (devices.isEmpty()) { Text("No paired Bluetooth devices visible. Grant Nearby devices permission and pair your ELM327 in Android settings.", color = TextMuted); TextButton(onClick = onRequest) { Text("GRANT PERMISSION") } } else devices.forEach { device -> Text("${device.name ?: "Unnamed adapter"}\n${device.address}", color = TextMain, modifier = Modifier.fillMaxWidth().clickable { onSelect(device) }.padding(12.dp)) } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }) }

@Composable private fun WifiDialog(onDismiss: () -> Unit, onError: (String) -> Unit) { var host by remember { mutableStateOf("192.168.4.1") }; var port by remember { mutableStateOf("35000") }; AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text("WI-FI ADAPTER", color = TextMain) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("This path is prepared for common TCP ELM327 adapters. It will be wired into the live session after physical adapter testing.", color = TextMuted, fontSize = 12.sp); OutlinedTextField(host, { host = it }, label = { Text("Adapter IP") }); OutlinedTextField(port, { port = it }, label = { Text("TCP port") }) } }, confirmButton = { Button(onClick = { if (host.isBlank() || port.toIntOrNull() == null) onError("Enter a valid adapter address and port") else { onError("Wi-Fi transport is scaffolded; test with a real adapter before enabling live polling."); onDismiss() } }) { Text("SAVE") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }) }
