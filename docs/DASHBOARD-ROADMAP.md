# Pitlane dashboard roadmap

Pitlane currently has one responsive cockpit layout. The next step is to make layouts first-class, local, and interactive without duplicating telemetry logic.

## Shared dashboard model

Keep one `VehicleTelemetry` stream and let each layout subscribe to the same state. A layout only chooses composition, color tokens, visible cards, units, and interaction behavior. Persist the selected layout and card configuration locally.

Every card should expose:

- value, unit, freshness timestamp, and data source (DEMO or LIVE)
- unsupported / no response / stale states distinct from zero
- configurable min/max bands and color thresholds
- tap for detail; long press or edit mode for reorder/hide

## Planned layouts

### 1. Cockpit (current foundation)

Large speed and RPM instruments, compact telemetry grid, connection state, and fault status. Best for a phone mounted while parked or for quick checks.

### 2. Race telemetry

Four to six large colorful tiles: RPM, speed, throttle, load, coolant, voltage. Add a scrolling sparkline strip and peak/hold values. Color should communicate status, not decoration: green normal, amber attention, red only for a meaningful threshold or stale/error state.

### 3. Diagnostics bench

No speed hero. Prioritize protocol, adapter, supported PID bitmap, raw response preview, readiness monitors, DTCs, timestamps, and request latency. Designed for tomorrow's adapter compatibility work.

### 4. Road trip / session

Large elapsed-time/session controls, sample frequency, local recording status, distance/GPS only if explicitly enabled, and export. No background upload. The phone should be stationary or mounted; the UI must not encourage handheld interaction while driving.

### 5. Minimal HUD

Two to three configurable values with very large type, high contrast, night-friendly colors, and no scrolling. Suitable for a parked inspection or an approved fixed mount; not a claim of road-safe instrumentation.

## Interaction plan

1. Add layout picker in Settings with live preview.
2. Add edit mode: reorder, hide, add cards from supported PID list.
3. Add card detail: recent samples, min/max/average, freshness, raw PID and last response.
4. Add color/threshold presets plus a custom profile.
5. Add local session recording and CSV/JSON export only after real adapter data is validated.
6. Add accessibility labels, large-text behavior, color-blind-safe patterns, and landscape testing on the S21.

## Tomorrow's validation gate

Do not tune layouts against synthetic values only. First capture the Ford Ka's actual supported PIDs and response cadence. Then choose the default cards from what the car returns, keeping unsupported fields visible as unavailable rather than inventing data.
