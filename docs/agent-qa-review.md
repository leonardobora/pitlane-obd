# Pitlane OBD adversarial QA review

**Review ID:** `agent-qa-2026-10-01-pitlane-obd`  
**Date:** 2026-10-01 (America/Sao_Paulo)  
**Scope:** Static review of the Android runtime, Bluetooth Classic transport, OBD session, parser, Compose UI, manifest, and existing tests. No ADB, phone, vehicle, or adapter was used.

## Verification performed

- Command: `export JAVA_HOME=/home/bora/.local/opt/jdk21 ANDROID_HOME=/home/bora/Android/Sdk ANDROID_SDK_ROOT=/home/bora/Android/Sdk; ./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain`
- Result: **BUILD SUCCESSFUL**; 7 unit tests passed, 0 failures, 0 errors.
- APK produced: `app/build/outputs/apk/debug/app-debug.apk`
- APK metadata: application `dev.pitlane.obd`, version `0.1.0`, debug variant.
- APK SHA-256: `6908a31b0b942083d07ebc5cd1467eaa71557eaf26459d0023a4cfb426e7b367`
- The tests cover parser happy paths and a few malformed/no-data cases only. There are no transport, Android permission, Compose UI, lifecycle, session-concurrency, or real ELM transcript tests.

## Ranked findings

### High — Bluetooth permission grant does not refresh the adapter list

**Evidence:** `MainActivity.kt:77` registers a permission callback with an empty body. `onCreate` passes `bluetoothDevices = bondedDevices()` at `:90`; `bondedDevices()` returns an empty list when `BLUETOOTH_CONNECT` is not yet granted (`:99`). The list is not state and is not recomputed after the user grants permission. `DeviceDialog` then remains on the “No paired Bluetooth devices visible” state until the activity is recreated.

**Impact:** On a fresh Android 12+ install, the documented flow (“tap CONNECT, grant Nearby devices, select adapter”) can dead-end even when a paired ELM327 exists. This is a release-blocking field-test failure, not merely a confusing message.

**Recommendation:** Store paired devices in Compose/activity state, handle the permission result, reload the bonded set after a successful grant, and surface a denial-specific error. Refresh when the dialog opens as well.

### High — Initialization failures are swallowed and the UI can claim LIVE on a dead adapter

**Evidence:** `ObdSession.initialize()` wraps every `transport.exchange()` in `runCatching` and discards the result/error (`ObdSession.kt:12-17`). `MainActivity.kt:136-141` then sets `mode = LIVE` unconditionally after `initialize()` returns. A Bluetooth disconnect, timeout, unsupported command, or closed stream during all initialization commands therefore does not prevent the LIVE state.

**Impact:** The user may see a live indicator while the adapter never completed initialization; subsequent values can remain demo/previous values or fail one-by-one. This undermines the connection result and makes field diagnosis difficult.

**Recommendation:** Require the essential initialization exchanges to complete and validate a usable ELM response/prompt. Return a structured initialization failure, keep the state CONNECTING/ERROR rather than LIVE, and include the failed command and cause in the UI/log.

### High — Unsupported or timed-out readings retain old values without a stale/unavailable state

**Evidence:** `ObdSession.readTelemetry()` starts from the previous sample and only replaces a field when parsing succeeds (`ObdSession.kt:19-25`). `NO DATA`, malformed frames, and exchange exceptions are converted to `null` and ignored. `LiveScreen` renders the retained values as ordinary telemetry (`MainActivity.kt:192-205`) and does not show age, source, or per-field availability.

**Impact:** A disconnected or intermittently responding ELM327 can leave believable RPM, speed, temperature, or voltage values on screen. For the Ford field test, this can be mistaken for current vehicle data after an adapter timeout.

**Recommendation:** Model each reading as value plus availability/timestamp/error, mark it stale or unavailable after a failed poll, and make the UI show `—`/stale rather than silently carrying forward the last value. Stop or clearly degrade the polling state after transport failure.

### High — Fault reads can interleave with initialization and telemetry session shutdown

**Evidence:** `activeSession` is assigned immediately after `transport.connect()` and before `session.initialize()` (`MainActivity.kt:136-139`). The Faults button can launch `readReadiness()`/`readTroubleCodes()` independently (`:159-170`) while the initialization coroutine is still sending `ATZ`, `ATE0`, and other commands. `PromptStream` serializes individual `exchange()` calls with a mutex (`ElmTransport.kt:146-153`), but not the whole initialization transaction or session lifecycle. STOP/cancel closes the transport in the polling coroutine’s `finally` (`MainActivity.kt:141-149`) while a separately launched fault read may still be waiting or writing.

**Impact:** Commands can be ordered as `ATZ`, `0101`, `ATE0`, etc.; initialization can reset adapter state after a fault request, and a fault read can race with close, yielding inconsistent data or avoidable errors during a common UI action.

**Recommendation:** Expose a session state that is READY only after initialization and disable fault reads until then. Serialize initialization, polling, and fault operations through one session-level gate; cancel child operations before closing the transport and clear `activeSession` on every stop/dispose path.

### Medium — No capability discovery causes slow, noisy polling against unsupported PIDs

**Evidence:** `ObdSession.readTelemetry()` requests every `ObdPid.entries` item every cycle (`ObdSession.kt:19-24`) and does not use the implemented `supportedPids()` parser. Each default exchange timeout is 3.5 seconds (`ElmTransport.kt:27`), so a vehicle/adapter that does not support several listed PIDs can take tens of seconds per dashboard refresh.

**Impact:** The first Ford Ka session may appear frozen or show stale values while unsupported commands time out. Repeated unsupported requests also produce unnecessary adapter traffic and obscure which PIDs the ECU actually supports.

**Recommendation:** Query Mode 01 PID 00 (and subsequent ranges when needed) after initialization, poll only supported PIDs, use bounded per-cycle timing, and report unsupported versus no-response separately.

### Medium — Compose controls have weak accessibility and touch-target semantics

**Evidence:** `TabItem` is a `Text` with a raw `Modifier.clickable` (`MainActivity.kt:218-219`) rather than a Material `Tab`; the clickable area is only the text plus vertical padding and has no role/selected semantics. Device rows are also raw clickable `Text` elements (`:221`).

**Impact:** TalkBack users may not get tab roles or selection state, and small adapter rows are harder to hit reliably in a parked-car field setup. This is especially risky if the device name/address is the only visible target.

**Recommendation:** Use Material `Tab`/semantics for tabs and `ListItem`/button semantics for adapters with a full-width minimum touch target, explicit content descriptions, and visible selected/focus states.

### Low — Bluetooth device metadata access is not isolated from permission revocation

**Evidence:** `bondedDevices()` guards `BLUETOOTH_CONNECT` and catches exceptions (`MainActivity.kt:96-101`), but the already-returned `BluetoothDevice` objects are later read directly for `name` and `address` while rendering the dialog (`:221`). A permission can be revoked or become unavailable between those operations.

**Impact:** Usually this becomes an empty/error path, but a `SecurityException` during composition is a possible UI crash path on Android versions enforcing the permission at the property access.

**Recommendation:** Convert devices to a permission-safe UI model while loading, catch metadata access at the boundary, and never call permission-gated Bluetooth properties from composable rendering.

## Tomorrow's Ford Ka / ELM327 field-test checklist

Perform all checks while parked. Do not operate the phone while driving, and do not clear codes.

1. Record vehicle, adapter brand/model/chipset claim, Classic vs BLE, `ATI` response, Pitlane version, Android version, and time in `docs/FIELD-TEST-LOG.md`.
2. Confirm the Ford Ka ignition state: key ON with engine off first; verify the adapter powers up; then start the engine only after the link is stable.
3. Pair the adapter in Android Settings before opening Pitlane. On a fresh install, verify that granting Nearby devices actually populates the adapter list; if not, record it as the permission defect above.
4. Tap CONNECT, select the paired adapter, and record time to LINKING, initialization transcript, first valid prompt, and transition to LIVE. Capture `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`, `ATI` where available.
5. Confirm the actual protocol selected by the ELM327/vehicle; treat ISO 15765-4 CAN as a hypothesis, not a guarantee.
6. With engine off/key ON and then idling, record raw responses plus displayed values for `0100`, `0101`, `0104`, `0105`, `0106`, `0107`, `010B`, `010C`, `010D`, `010F`, `0110`, `0111`, `012F`, and `0142`. Note `NO DATA`, timeouts, or unsupported PIDs explicitly.
7. Check that speed is 0 while stationary, RPM is plausible only with the engine running, coolant/intake temperatures are plausible, voltage changes between key ON and running, and no unavailable field is rendered as zero or as a stale believable value.
8. Open FAULTS only after LIVE and verify that reading readiness/DTCs does not interrupt telemetry or re-run initialization. Record MIL state, DTC count, exact codes, and incomplete monitors.
9. Test a deliberate adapter disconnect/reconnect once while parked. Verify the UI leaves LIVE, stops presenting fresh-looking values, reports the error, and can reconnect without force-closing the app.
10. Stop the session, relaunch if needed, and verify no code-clearing command (`04`) is sent. Preserve a sanitized raw transcript; remove VIN, plate, GPS, and other identifying data before sharing.

## Acceptance assessment

- **Build gate:** pass.
- **Unit-test gate:** pass for the existing 7 parser tests, but coverage is narrow.
- **Runtime/permission gate:** not passed; no ADB/phone test was allowed, and static review found a fresh-install permission/list dead end.
- **Vehicle compatibility gate:** not assessed; requires the specified Ford Ka, a paired ELM327, and a parked field test.
- **Recommendation:** do not treat the current build as field-test-ready until the permission refresh, initialization error handling, stale-data model, and session-operation ordering are addressed or explicitly accepted as known blockers.
