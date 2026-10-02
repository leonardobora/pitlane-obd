# Pitlane OBD

**A privacy-first, open-source Android OBD-II dashboard.** Live vehicle data in a motorsport-inspired cockpit; original visual design, not a clone of another product.

> OBD-II is for diagnostics, not vehicle control. Use Pitlane while parked. Never operate the phone while driving.

## Current release

Pitlane 0.1.0 is the first installable slice: an instrument-panel dashboard with a no-hardware demo mode, an ELM327 Bluetooth connection path, live standard-PID decoding, and a DTC workflow. The demo is clearly labeled and never presents generated values as readings from a connected vehicle. Adapter/vehicle compatibility depends on the hardware and supported emissions PIDs; this is not a claim of full SAE J1979 compliance.

## Principles

- **No telemetry to us:** no account, analytics, advertising, or network permission in the app.
- **Explicit connection:** Bluetooth permissions are requested only when the user chooses to connect.
- **Read-only diagnostics:** live PIDs and trouble codes are read; Mode 04 clearing, actuator tests, coding, and ECU writes are not part of the initial release.
- **Original UI:** high-contrast, glanceable motorsport instrumentation with its own Pitlane identity.
- **Open source:** Apache-2.0. Contributions and forks are welcome.

## Build

Requirements: JDK 21, Android SDK Platform 36, Android Build Tools 36.0.0.

```sh
./gradlew testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Hardware

The first release targets common ELM327-compatible Bluetooth adapters. Demo mode exercises the UI without an adapter. This first build does not yet claim support for every clone, vendor-specific service, Wi-Fi dongle, proprietary PID, or ECU/module write operation. See [`docs/research.md`](docs/research.md) for cited protocol and licensing research, and [`docs/PRODUCT.md`](docs/PRODUCT.md) for scope.

## Safety and limitations

Pitlane does not control the vehicle and cannot replace a qualified mechanic or a manufacturer-level scan tool. Generic OBD-II commonly exposes emissions-related powertrain data; it does not guarantee access to ABS, airbag, body, transmission, or manufacturer-specific modules. Values and code descriptions are informational. Do not clear codes before recording them; clearing may erase useful diagnostic context and readiness information.
