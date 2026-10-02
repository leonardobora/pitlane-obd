# Ford Ka 2017 1.0 SE (Brazil) — Pitlane field-test brief

**Purpose.** A parked, read-only compatibility test for the Brazilian-market Ford Ka 2017 1.0 SE using Pitlane, a Bluetooth ELM327-style adapter, and a Samsung Galaxy S21 (Android 13). This brief separates source-verified facts from vehicle-specific inference. It is not a Ford service procedure, emissions inspection, roadworthiness verdict, or diagnosis.

## Bottom line

- **Brazilian regulatory scope (verified):** IBAMA lists IN 24/2009 as the specification/certification rule for **OBDBr-2** on light Otto-cycle vehicles and IN 6/2017 as the change that calls the system **OBDBr-2+**.[3] That supports testing generic emissions-related OBD services, but does not identify this car's physical protocol, ECU addressing, or every supported PID.
- **Ford documentation (verified):** Ford's 2017 Ka owner-manual page provides the manual for vehicles built from 20 January 2016; the linked/dated owner manual is a 2017 Ford publication and covers the 1.0L Flex Fuel model.[1][2] It warns that the data connector should not receive unauthorized wireless plug-in devices because access could affect safety-related systems.[1]
- **Protocol conclusion (inference, to be tested):** ISO 15765-4 CAN is the leading candidate for a 2017 Brazilian light vehicle, but no retrieved Ford or IBAMA source states the Ka's exact OBD physical-layer variant. Do **not** record CAN as confirmed until the adapter reports a selected protocol and the car returns valid standard responses.
- **Generic OBD expectation:** A successful generic session can reasonably test supported Mode/Service 01 live emissions data, PID 01 monitor/MIL status, and Mode/Service 03 stored emissions DTCs. Generic OBD does **not** imply access to ABS, airbags, body, transmission, Ford-specific PIDs, actuator tests, or a complete vehicle scan.[4][9]

## Test conditions and safety

1. Perform setup and all phone interaction **parked**, in a ventilated location, with the parking brake applied and transmission in neutral/park. Do not hold or operate the phone while driving; Ford explicitly warns that device distraction can cause loss of control.[1]
2. Start with ignition **OFF**. Inspect the adapter and DLC for damage, bent pins, moisture, looseness, or signs of an incorrectly wired clone. Do not force the plug.
3. Plug in only a reputable, correctly wired adapter. Ford's manual specifically cautions against unauthorized wireless plug-in devices on the data connector; treat this as a real risk, not boilerplate.[1] Prefer a read-only adapter and keep the test limited to documented read commands.
4. Turn ignition to **ON** without starting the engine for initialization and key-on values. Start the engine only when explicitly testing running values, and keep the phone secured before moving the vehicle.
5. Do not issue Mode `$04` (clear/reset emissions information), coding, adaptation, actuator tests, output controls, security access, or any manufacturer-specific write command. Clearing codes can reset readiness history; the first field test must preserve the car's state.
6. Stop if the adapter becomes hot, smells abnormal, causes warning lamps or electrical symptoms, repeatedly resets, or communications become erratic. Remove it with ignition OFF. Do not leave a plug-in adapter installed unattended; it may draw battery power and may expose vehicle data.
7. A DTC or incomplete monitor is a recorded vehicle state, not proof of a failed component; official OBD rules require scan-readable standardized results for relevant monitored conditions.[8][9] Do not repair, erase, or declare the car safe based on Pitlane output; use a qualified technician for diagnosis.

## Connection procedure

### A. Before connecting

Record date/time, odometer if desired, fuel (gasoline/ethanol/unknown), ignition state, adapter brand/model, claimed chipset, firmware, transport (Bluetooth Classic SPP or BLE), and Pitlane build. Do not include the VIN, plate, GPS, or personal data in shared logs unless necessary.

The Ford manual does not give a user-facing OBD pinout or promise a generic Bluetooth workflow in the retrieved material. Locate the DLC using the vehicle/service documentation or a careful visual inspection; do not infer its location from another Ka year or trim.

### B. Pair and open the transport

1. With the adapter unpowered or disconnected, pair it in Android Bluetooth settings according to the adapter's own instructions. For a Classic adapter, Pitlane should use the adapter's documented serial/RFCOMM service; for BLE, it must use the adapter's documented GATT service/characteristic profile. A Bluetooth pairing success is not an OBD protocol success.
2. Plug the adapter into the DLC with ignition OFF, then switch ignition ON.
3. Connect from Pitlane. Save the complete adapter transcript, including prompts, echoes, line endings, timeout text, and `NO DATA`/`UNABLE TO CONNECT` responses.

### C. Conservative ELM interrogation sequence

Send one command at a time, wait for the `>` prompt or a bounded response timeout, and log the exact raw reply. The ELM327 manufacturer's data sheet documents the bridge role, automatic protocol search, AT command interface, protocol selection, battery-voltage reading, OBD requests, and DTC handling.[7]

```text
ATZ       ; reset; record firmware banner and timing
ATE0      ; echo off (optional; record the reply)
ATL0      ; linefeeds off (optional)
ATS0      ; spaces off (optional; if the parser supports compact replies)
ATH0      ; headers off for normal generic parsing
ATI       ; identify adapter firmware/chip claim
AT@1      ; adapter description, if supported
ATDP      ; describe currently selected protocol, if supported
ATDPN     ; describe protocol number, if supported
ATRV      ; adapter-measured vehicle supply voltage, if supported
ATSP0     ; automatic protocol search; use only before read-only requests
0100      ; supported PIDs 01–20
0101      ; MIL status and monitor/readiness bitmap
0105      ; engine coolant temperature, if supported
010C      ; engine RPM, if supported
010D      ; vehicle speed, if supported
0111      ; throttle position, if supported
03        ; stored emissions DTCs, read-only
```

`ATSP0` means automatic selection in a genuine ELM327 command set; it is not proof that the selected bus is CAN. After a successful request, issue `ATDP`/`ATDPN` again and preserve the response. The `0100` bitmap is a capability query: only request additional PIDs whose support bit is advertised, then treat `NO DATA`, `7F`, malformed frames, or unsupported responses as results to log—not as zero values.

If auto-search fails, do not cycle arbitrary protocols indefinitely. First capture the adapter's exact error, power-cycle only with ignition OFF, and retry once. A manual protocol trial is a diagnostic experiment, not a vehicle fact; only use it if the adapter documentation exposes protocol names/numbers and record each attempt. The protocol candidates below are ordered hypotheses, not a specification.

## Likely protocol candidates

| Candidate | Confidence | What supports it | What would confirm/refute it |
|---|---|---|---|
| **ISO 15765-4 CAN, 11-bit identifiers, 500 kbit/s** | Medium inference | The ELM327 data sheet lists ISO 15765-4 among its supported OBD interfaces.[7] A 2017 light vehicle is a plausible CAN-era application, and this is the leading practical hypothesis. No retrieved Ford/IBAMA document gives the Ka's bus parameters. | `ATDP`/`ATDPN` identifies the variant and repeated standard requests return valid OBD responses; failure of auto-search or a different reported protocol refutes this exact variant. |
| **Another ISO 15765-4 CAN variant (29-bit and/or other supported bitrate)** | Low-to-medium inference | ISO 15765-4 is a family; the exact addressing/bitrate is not established by the model-year owner material.[1][7] | Adapter reports the variant, or raw headers/known-good responses establish it. |
| **ISO 14230-4 (KWP2000) or ISO 9141-2** | Low fallback | Both are listed among ELM327-supported legacy OBD interfaces.[7] They remain useful fallbacks when automatic detection reports them, but there is no vehicle-specific source here that favors either for this Ka. | `ATDP`/`ATDPN` and valid standard responses. Do not select one merely because a web forum says so. |
| **SAE J1850 PWM/VPW** | Very low | ELM327 supports those legacy families in general.[7] No retrieved Brazilian Ka source supports this candidate. | Only an explicit adapter result and valid responses could elevate it. |

**Important distinction:** SAE J1962 describes the diagnostic connector requirements and contact allocation; its catalog description does not identify the protocol used by a specific Ka.[6] Do not infer CAN solely from seeing a 16-pin connector. Do not infer OBDBr-2+ from a protocol name: the Brazilian regulatory classification and the electrical transport are different facts.[3]

## What generic OBD can honestly show

### Reasonable first-pass scope

- Adapter identity, protocol-selection result, connection timing, voltage, and raw responses.
- Supported PID bitmaps (`0100`, then subsequent ranges if advertised).
- Standardized live powertrain/emissions-related values such as RPM, speed, coolant temperature, calculated load, intake temperature, throttle position, fuel trims, MAP, and fuel-system status **only where the ECU advertises and returns them**. PID names/encodings must be tied to the applicable SAE/ISO data definition; a supported bit alone does not guarantee a useful or stable value.[4][9]
- PID 01 MIL state and monitor/readiness bits. Readiness means monitor test completion state, not a complete health score or automatic inspection pass.[4][9]
- Stored generic emissions DTCs using Mode `$03`, decoded conservatively and retaining the raw bytes. A no-code reply is not evidence that no non-emissions module has faults.

### Explicit non-promises

Generic OBD is not a whole-vehicle scan. Do not promise ABS, airbag/SRS, body, immobilizer, transmission, instrument cluster, Ford-specific PIDs, manufacturer DTCs, freeze-frame completeness, Mode `$06` coverage, bidirectional control, coding, or a Brazilian inspection result. EPA material describes OBD in the context of emission-control/emission-related monitoring, and its scan-tool inspection example is not a Ford-specific service guarantee.[4][5]

The Ford manual says service personnel can read technical error/event information with special diagnostic equipment and warns against unauthorized devices; this supports keeping Pitlane's initial test read-only and narrow, not assuming that a generic adapter can replace Ford workshop equipment.[1]

## Raw-log schema

Store **one record per adapter command and one record per received line/frame**, plus a session header. Preserve the exact raw text before normalization. JSON Lines is recommended:

```json
{
  "schema_version": "pitlane.obd.raw.v1",
  "session_id": "2026-10-01T12:34:56Z_adapterA",
  "timestamp_utc": "2026-10-01T12:34:57.123Z",
  "vehicle": {"make": "Ford", "model": "Ka", "model_year": 2017, "engine": "1.0L Flex Fuel", "trim": "SE", "market": "BR"},
  "phone": {"model": "Samsung SM-G991B", "android": "13"},
  "adapter": {"brand": null, "model": null, "claimed_chip": null, "firmware": null, "transport": "Bluetooth Classic|BLE|Wi-Fi"},
  "vehicle_state": {"ignition": "off|on|running", "engine_rpm": null, "moving": false},
  "direction": "tx|rx",
  "command_or_raw": "010C\\r|41 0C 1A F8",
  "normalized": null,
  "selected_protocol": null,
  "response_kind": "prompt|data|no_data|error|timeout|banner|unknown",
  "elapsed_ms": null,
  "transport_error": null,
  "parser_error": null,
  "privacy_redactions": []
}
```

Required minimum fields are `session_id`, UTC timestamp, ignition/motion state, direction, exact command/raw response, response classification, elapsed time, selected protocol when known, and adapter firmware/banner. Keep a separate parsed snapshot table with `pid`, raw bytes, decoded value, unit, formula/version, source timestamp, and `quality` (`valid`, `unsupported`, `stale`, `malformed`, `timeout`). Never turn an absent response into zero.

## Acceptance criteria for tomorrow

The test is successful if it produces a replayable transcript and can answer all of these without guessing:

1. Did the adapter identify itself and maintain a transport connection?
2. What protocol did the adapter actually select/report, and was it confirmed by valid vehicle responses?
3. Did `0100` return a valid support bitmap?
4. Did `0101` return a valid MIL/readiness response, and which monitors were incomplete?
5. Did `010C`, `010D`, and other requested PIDs return stable, correctly decoded values or explicit unsupported/error states?
6. Did `03` return stored DTC bytes, a clean response, or an error—and was nothing cleared?
7. Were there disconnects, resets, unusual voltage, or safety symptoms?

If only the adapter banner is returned, classify the outcome as **adapter/transport reached, vehicle protocol unconfirmed**. If the adapter reports a protocol but no standard response is obtained, classify it as **protocol claim unconfirmed by vehicle data**. Preserve both conclusions rather than upgrading either to compatibility.

## Sources

[1] Ford Motor Company Brasil, *Ka Manual do Proprietário* (2017 publication; PDF, including 1.0L Flex Fuel, data-connector warning, and safety guidance): https://www.ford.com.br/content/dam/Ford/website-assets/latam/br/servico-ao-cliente/manuais/2018/manuais-do-proprietario/Ka-Manual%20do%20Proprietario-MY18.pdf

[2] Ford Brasil, *2017 Ford Ka — Owner manuals* (official vehicle page; identifies the manual for vehicles built from 20/01/2016): https://www.ford.com.br/support/vehicle/ka/2017/owner-manuals

[3] IBAMA, *Legislação — Emissões* (official index; OBDBr-2 IN 24/2009 and OBDBr-2+ IN 6/2017 entries): https://www.gov.br/ibama/pt-br/assuntos/emissoes-e-residuos/emissoes/legislacao-emissoes

[4] U.S. EPA, *Vehicle Emissions On-Board Diagnostics (OBD)* (official OBD program/technical material index): https://www.epa.gov/state-and-local-transportation/vehicle-emissions-board-diagnostics-obd

[5] U.S. EPA, *Inspection of Class E Vehicles* (official scan-tool inspection example referencing SAE J1978/J1979 and generic OBD testing): https://www.epa.gov/sites/default/files/2017-10/documents/2003-me-vehicle-inspection.pdf

[6] SAE International, *J1962_201607 Diagnostic Connector* (official standard catalog page; connector scope, not vehicle-specific protocol): https://www.sae.org/standards/content/j1962_201607

[7] Elm Electronics, *ELM327 OBD to RS232 Interpreter, ELM327DSJ* (manufacturer data sheet; AT commands, automatic protocol search, supported protocol families, OBD/DTC functions): https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf

[8] U.S. eCFR, 40 CFR 86.1806-17, *Onboard diagnostics* (official example of model-year OBD requirements and scan-readable standardized results): https://www.ecfr.gov/current/title-40/chapter-I/subchapter-C/part-86/subpart-A/section-86.1806-17

[9] U.S. eCFR, 40 CFR 85.2231, *Inspection and maintenance requirements* (official generic OBD readiness/monitor and DTC inspection context): https://www.ecfr.gov/current/title-40/part-85/section-85.2231

## Limitations and confidence notes

- No retrieved primary Ford document states the Ka 2017 1.0 SE's exact ISO/SAE transport, CAN bitrate, CAN identifier width, connector pinout, or adapter pairing profile. Those are deliberately left as field-test findings.
- The vehicle-specific protocol ranking is an engineering hypothesis based on model era and the ELM327's supported families, not a Ford specification.[1][7][8]
- OBDBr-2/OBDBr-2+ establishes Brazilian regulatory OBD scope, not that every generic PID or every ECU is exposed to Pitlane.[3]
- SAE J1979/J2012 definitions are standards material; this brief does not reproduce a full proprietary PID/DTC table. Use licensed/current references for production-grade decoding.[4][9]
- The Ford manual is a whole-line manual and explicitly notes that equipment can vary by vehicle; trim/market/build differences can matter.[1][2]
- A Bluetooth adapter can be defective, counterfeit, incorrectly wired, or implement only part of the ELM command set. Record the banner and raw replies; do not trust the label alone.[7]
