# Pitlane OBD — product brief (0.1.0)

## Product promise

A polished, honest, local-first OBD-II instrument panel for Android: fast to understand at a glance, useful without a dongle in demo mode, and explicit about adapter limitations and data provenance.

## Audience and platform

- Android phone, first verified on Bora's Samsung Galaxy S21 (SM-G991B, Android 13).
- Initial field-test target: **Ford Ka 2017 1.0 SE, Brazilian market**. Generic OBDBr-2/OBD-II emissions telemetry is the target; the exact responding protocol and supported PIDs must be confirmed from the adapter and vehicle tomorrow.
- Bluetooth ELM327-family adapters; first release should implement standard Bluetooth Classic SPP and a documented BLE UART profile where possible.
- No cloud account or analytics. Vehicle data stays on-device unless the user deliberately exports it.

## UX direction

- Original “pit telemetry” composition, not a pixel-copy of FuelTech, Car Scanner, or another product.
- Night-cockpit foundation; warm off-white numerals, restrained signal colors, high legibility, large speed/RPM focus, compact secondary telemetry, clear connection badge, and obvious Demo/Live distinction.
- Responsive portrait and landscape layouts. Touch targets suitable for stationary inspection, not a promise of safe handheld use while moving.
- Tabs: Live, Faults, and Sessions/Settings. No hidden first-run hardware setup.

## MVP feature slices

1. **Dashboard:** RPM and vehicle speed hero gauges; coolant and intake temperature, throttle, engine load, manifold pressure, fuel trim, fuel level, and module voltage when the ECU reports them. Unknown/unavailable is displayed as such, never as zero.
2. **Demo mode:** deterministic, correlated, visibly synthetic data with start/stop controls; safe to use for screenshots and on-device UI QA without a car.
3. **Adapter:** enumerate bonded devices; connect over Bluetooth Classic SPP; perform a conservative ELM initialization; read standard PIDs; show connection state and errors. Add BLE only for an explicitly supported UART profile, not an unverified “all BLE adapters” claim.
4. **Faults and readiness:** read Mode 03 powertrain DTCs and Mode 01 PID 01 readiness status where supported. Decode standard alphanumeric DTCs; provide plain-language descriptions only for codes backed by a local dictionary. Unknown codes remain visible as codes. **Mode 04 clearing is intentionally excluded from 0.1.0** so the first release cannot erase diagnostic context or readiness data.
5. **Tests and docs:** deterministic tests for PID decoding, support bitmaps, and DTC parsing; source-cited OBD/Bluetooth research; reproducible build instructions.

## Quality gates

- Unit tests for all protocol decoding and malformed/no-data paths.
- Build APK locally, install via the explicit S21 USB serial only after a fresh concurrency check, launch Pitlane, inspect demo dashboard + navigation on device, and collect crash logs.
- Do not claim live-vehicle operation unless a compatible ELM adapter is present and tested against a vehicle while safely parked.

## Non-goals for 0.1.0

- ECU coding, control, actuator tests, CAN injection, immobilizer/airbag/security-module access.
- Manufacturer-specific PID packs or full SAE compliance.
- Telemetry upload, driver tracking, background location, cloud accounts, ads.
- Wi-Fi adapter support, every proprietary BLE dialect, or broad trip-analytics claims unless implemented and tested.
