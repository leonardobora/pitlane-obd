# Pitlane field-test log

Use one copy per adapter/vehicle session. Keep the raw adapter transcript if available; a parsed value without the raw response is hard to debug.

## Session

- Date/time:
- Vehicle: Ford Ka 2017 1.0 SE, Brazilian market
- Expected generic scope: OBDBr-2 / OBD-II emissions-related powertrain data. Do not assume ABS, airbag, body, transmission, or Ford-specific module access.
- Likely first protocol candidate: ISO 15765-4 CAN; confirm from the ELM initialization response rather than treating this as guaranteed.
- Android device: Samsung S21 SM-G991B
- Adapter: brand / model / claimed chipset / Classic or BLE / firmware (`ATI`):
- Transport: Bluetooth Classic / BLE / Wi-Fi
- Ignition state: engine off / key on / running
- Pitlane version:

## Connection

- Pairing succeeded: yes/no
- Connection succeeded: yes/no
- Initialization response:
- Selected protocol (`ATSP0` or detected):
- Time to first valid PID:
- Disconnects/timeouts:

## Readings

Record a few stable snapshots while parked. Do not operate the phone while driving.

| Time | RPM | Speed | Coolant °C | Intake °C | Throttle % | Load % | MAP kPa | STFT % | LTFT % | Fuel % | Voltage V |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| | | | | | | | | | | | |

## Faults/readiness

- MIL: on/off/unknown
- Confirmed DTC count:
- Codes exactly as displayed:
- Readiness incomplete monitors:
- Did you clear anything? **Pitlane 0.1.0 must not clear codes.**

## Adapter transcript

Paste sanitized raw responses here. Remove VIN, plate, GPS, or personally identifying data if sharing outside this project.

```text
ATI
ATSP0
0100
0101
010C
010D
03
```

## Result

- What worked:
- What failed:
- Adapter/vehicle compatibility conclusion:
- Next fix or feature:
