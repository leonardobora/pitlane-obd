# Pitlane OBD UX review and dashboard specification

**Audience:** product, Android/Compose, and field-test teams  
**Scope:** dashboard system only; no app source changes in this review  
**Target device:** Samsung Galaxy S21, portrait and landscape  
**Design intent:** an original, readable pit-telemetry language—not a replica of FuelTech, Car Scanner, or any proprietary interface.

## 1. Executive direction

Pitlane should feel like a compact race-engine data logger that tells the truth before it looks fast. Its visual signature is a **dark asphalt field, warm instrument numerals, and a single “apex” accent that moves with attention**. Data is arranged in lanes and bands rather than faux analog dials: a numeric value answers “what is it now?”, a thin history trace answers “is it moving?”, and a status chip answers “can I trust this value?”.

The app already has the right product boundaries: read-only generic emissions data, explicit DEMO/LIVE provenance, no cloud, and a Ford Ka 2017 1.0 SE field target (see `README.md:5-17`, `docs/PRODUCT.md:3-27`). The next UX increment is not more gauges. It is a shared card system with honest freshness, deliberate layouts, and a reliable interaction model for a stationary inspection.

### The three-second scan

Every Live screen must answer these questions in this order:

1. **Am I connected to a vehicle?** A persistent `DEMO`, `LINKING`, or `LIVE` rail with adapter name and last successful poll.
2. **What needs attention?** One severity strip, never a rainbow of unrelated colors.
3. **What are RPM and speed?** Large numeric heroes with unit and sample age.
4. **Are temperatures, load, throttle, voltage, and fuel plausible and current?** Compact cards with a visible trend/status marker.

No screen should require a tap to discover that a value is stale, unsupported, or synthetic.

## 2. Current implementation review

### What is a strong foundation

- `VehicleTelemetry` already has nullable values, a deterministic coherent demo generator, a `receivedAtMs`, and explicit `ConnectionMode` (`app/src/main/java/dev/pitlane/obd/model/VehicleTelemetry.kt:6-67`). Keep null as a first-class state; never coerce absent data to zero.
- The parser correctly returns `null` for `NO DATA`, malformed, or incomplete responses and has tests for those paths (`app/src/main/java/dev/pitlane/obd/protocol/ObdResponseParser.kt:48-63`, `app/src/test/java/dev/pitlane/obd/protocol/ObdResponseParserTest.kt:21-26`). The UI must preserve that honesty.
- `ObdSession` polls a declared, small generic PID set and leaves missing readings in the previous object (`app/src/main/java/dev/pitlane/obd/engine/ObdSession.kt:19-25`). That is acceptable for transport continuity, but the UI needs per-PID freshness metadata to distinguish “last known” from “current”.
- The current palette (`Ink`, `Panel`, warm `TextMain`, orange, green) is a usable night-cockpit base (`app/src/main/java/dev/pitlane/obd/MainActivity.kt:68-74`). Retain the restraint and add semantic colors/patterns rather than more decoration.
- The roadmap already calls for first-class layouts, card configuration, detail views, thresholds, and accessibility (`docs/DASHBOARD-ROADMAP.md:5-16`, `docs/DASHBOARD-ROADMAP.md:38-49`). This spec makes those decisions concrete.

### Specific UX risks to fix

1. **Freshness is global, not per field.** `VehicleTelemetry.receivedAtMs` is updated only when a PID succeeds, and failed responses leave an old field visible. A coolant value can therefore look current because RPM succeeded. Store `lastSuccessAt`, `lastAttemptAt`, `sampleState`, and optional `failureReason` per metric in the presentation/view-model layer (or evolve the model later).
2. **Current card layout is fixed and not responsive.** `LiveScreen` always renders two heroes and two three-column rows (`MainActivity.kt:192-206`). On a narrow portrait S21 this is a valid first slice, but it does not support the planned variants, text scaling, or landscape density.
3. **Status is mostly color and text.** `Header` uses `● LIVE`, `○ LINKING`, and `● DEMO` (`MainActivity.kt:183-190`), while readiness uses check/circle characters (`:212`). Add shape, label, and icon semantics; red/green alone must never carry meaning.
4. **The bottom navigation is a small clickable `Text` rather than a full target** (`MainActivity.kt:218-219`). Make the entire tab item at least 48×48 dp with content descriptions and selected state.
5. **Faults can be ambiguous.** An empty list currently says “No live reading yet” even when a successful read returned no codes (`MainActivity.kt:212`). Model `notRead`, `readEmpty`, `readError`, and `hasCodes` separately.
6. **The live loop has no visible cadence/latency/error budget.** The current session polls every second but does not expose response age or dropped PID count (`MainActivity.kt:140-145`). Show a compact health line; it is useful during the first adapter test and prevents false confidence.
7. **The demo loop can continue to mutate state while the user changes modes.** `LaunchedEffect(mode)` starts an endless demo loop when mode is DEMO (`MainActivity.kt:119-125`), while connection work uses a separate job. Keep one lifecycle-owned telemetry producer so DEMO cannot overwrite a LIVE sample after reconnect.
8. **The current settings copy is candid but dense.** Keep the step-by-step pairing guidance, but move it behind “Connection help” and make the primary state/action visible in the header.

## 3. Visual system: “Apex / Sector / Pit”

This vocabulary is original to Pitlane and gives variants a shared identity:

- **Apex:** the current value that deserves attention. Warm ivory numerals, a colored edge notch, and no ornamental dial unless a user explicitly chooses one.
- **Sector:** a card’s compact history and threshold band. It is a 30-second rolling trace (or a band for values without meaningful history), with a small “last sample 0.9 s” label.
- **Pit:** inspection states—adapter, PID support, readiness, DTCs, and raw response details. Pit views optimize for evidence, not spectacle.

### Tokens

| Token | Value/behavior | Use |
|---|---|---|
| Asphalt 950 | `#090D12` | app background; never pure black so outlines remain visible |
| Panel 900 | `#111923` | card surface |
| Panel 800 | `#17212C` | nested surface / selected card |
| Warm 50 | `#F4F0E8` | primary numerals and labels |
| Mist 400 | `#A7B2BA` | secondary labels; maintain readable contrast |
| Apex orange | existing `#F05A3E` | focus/selected/attention, not generic “bad” |
| Signal teal | `#35C7B0` | normal/current trend; pair with `NORMAL` label |
| Caution amber | `#F2B84B` | configured attention threshold or degraded freshness |
| Fault coral | `#F06A67` | confirmed fault or hard threshold; pair with `FAULT` |
| Unsupported slate | `#6F7C86` + hatch/dash | no PID support / no response; never gray zero |

Use color plus a word label plus a shape/pattern: a teal check/`NORMAL`, amber triangle/`WATCH`, coral square/`FAULT`, slate slash/`UNAVAILABLE`. Ensure normal and caution are distinguishable in deuteranopia simulation. Minimum body text is 12sp; important values are 32–56sp, never below 28sp at default scale.

Cards use 12dp corner radius, 1dp low-contrast border, 12–16dp internal padding, and 8dp minimum gaps. Avoid gradients behind text. A 2dp left status rail is more legible and less noisy than a glowing whole-card border.

## 4. Dashboard variants

All variants subscribe to the same telemetry and card registry. Switching variants changes composition, not polling, units, or data meaning. Persist selected variant and card order locally. Default to **Cockpit**.

### A. Cockpit (default, inspection-first)

**Purpose:** the everyday parked inspection and the first-run demo.

- Top rail: Pitlane mark; vehicle profile; `DEMO/LIVE/LINKING`; adapter label; `Last poll 0.9 s`; overflow for layout/edit.
- Severity strip below rail: `SYSTEM NORMAL`, `3 PIDs unavailable`, or `1 FAULT — open Faults`. It is one strip, not per-card alarm spam.
- Hero row: RPM (left) and speed (right), each 50% width in portrait. Numeric value, unit, small range band, sample age. Use a horizontal rev band on RPM rather than a round gauge.
- Secondary grid: coolant, throttle, engine load, manifold pressure, voltage, fuel. In portrait use 2 columns; each card is at least 112dp tall. In landscape use a 3-column grid with heroes occupying 2/3 width and a vertical status column.
- Bottom “provenance” row: `READ-ONLY • GENERIC OBD • DEMO` or `READ-ONLY • GENERIC OBD • Ford Ka / adapter name`.

**Concrete behavior:** tap a card opens its detail sheet; long press enters Edit mode with drag handles. The header remains pinned while the card grid scrolls in portrait. No horizontal scrolling in the main dashboard.

### B. Race telemetry (trend-first)

**Purpose:** see changing values during a stationary test or controlled dyno/garage observation, without implying a certified racing instrument.

- Six cards maximum: RPM, speed, throttle, load, coolant, voltage.
- Each card has a 30-second sparkline, current value, min/max hold, and sample age. Hold values reset only through an explicit `RESET PEAKS` action.
- Use a top “sector strip” with three fixed bands: `IDLE / WORKING / HOT` for each metric only where a configured threshold exists.
- Default polling cadence remains transport-controlled; show `sample interval` rather than promising a rate.
- If age exceeds 2 seconds, freeze the line and overlay a dashed `STALE` mark; after 10 seconds, replace the current value with `LAST 2.4 s` and a stale badge.

### C. Diagnostics bench (evidence-first)

**Purpose:** adapter compatibility and mechanic-style investigation.

- Header: connection state, transport (`Bluetooth Classic SPP`), adapter name, initialization result, and last error.
- Rows: PID support bitmap summary; per-PID response status; request latency; last response timestamp; raw response expandable by request.
- Readiness card distinguishes `SUPPORTED + COMPLETE`, `SUPPORTED + NOT COMPLETE`, and `NOT REPORTED`. DTC card distinguishes `NOT READ`, `READ — 0 CODES`, `READ ERROR`, and codes. Never say “clear” because Mode 04 is intentionally out of scope (`docs/PRODUCT.md:25-27`).
- A “copy diagnostic bundle” action may export local text/JSON only after confirmation; show exactly what leaves the device.

### D. Session (local capture)

**Purpose:** a deliberate, parked test session, not passive trip tracking.

- Start screen states what is recorded: selected PIDs, timestamps, adapter identity; no location by default.
- During recording: elapsed time, samples received/expected, dropped responses, and a prominent `STOP SESSION` action. Do not show a fake distance or route.
- On stop: summary of duration, sample count, min/max/mean where available, unsupported count, and export formats. Clearly mark DEMO sessions and prevent them being mistaken for vehicle records.

### E. Minimal (large type)

**Purpose:** glanceable stationary inspection when the user has selected two or three fields.

- Exactly 2–3 cards, full width, 48–64sp values, no scroll, no sparkline, no decorative gauge.
- A fixed bottom rail holds mode, stale state, and `OPEN FULL DASHBOARD`.
- Respect Android font scaling by allowing the layout to become vertical; never clip units or values.

## 5. Interaction model

### Navigation

Keep four top-level destinations, but use readable labels and icons: `Live`, `Faults`, `Session`, `Settings`. In landscape, a compact navigation rail is preferable; in portrait use bottom navigation. The selected item has label + filled shape; unselected items retain labels, not icon-only tabs.

### Card actions

- **Tap:** detail sheet. Include definition, current value/unit, source, sample age, status explanation, recent samples, min/max/mean, and raw PID/request only in an “Advanced” disclosure.
- **Long press or Edit:** enters edit mode; provide drag handle, hide toggle, and `Reset layout`. Do not make ordinary tap reorder.
- **Add card:** presents supported and unavailable PIDs in separate groups. Unavailable cards can be added intentionally for diagnosis but are labeled unavailable; they must not disappear silently.
- **Threshold edit:** preset (`Generic OBD`, `Ford Ka conservative`, `Custom`) plus explicit units and reset. A threshold change is local presentation logic, never a claim of manufacturer diagnosis.
- **Swipe:** avoid swipe-to-dismiss on cards; it conflicts with vertical scrolling and is poor in a parked, one-handed inspection. Use explicit buttons.

### Connection flow

1. User taps `CONNECT`; a sheet says “Park safely. Ignition ON. Pair the adapter in Android first.”
2. Permission request is tied to the action and explains Nearby devices, consistent with `docs/research.md:5-10`.
3. Choose paired device with human-readable name, transport, and last-used timestamp; do not expose a raw address as the primary label.
4. Show a staged progress line: `PAIR → INIT → FIND PIDs → LIVE`. Each stage can expand to the exact failure and retry action.
5. On success, land on Cockpit with `LIVE` plus adapter name. On failure, retain DEMO only if the user explicitly chooses it; never silently fall back while implying live data.

## 6. Thresholds and status semantics

Thresholds must be explicit, unit-aware, and reversible. They are **attention bands**, not a diagnosis. Start with conservative generic defaults and mark them as defaults in the detail sheet; do not label them “safe” for the Ford Ka until field evidence supports that claim.

| Metric | Normal starting band | Attention example | Hard attention example | UI rule |
|---|---:|---:|---:|---|
| Coolant | 70–105 °C | 105–112 °C | >112 °C | amber/coral only after freshness is current; show `TEMP HIGH` |
| ECU voltage | 13.2–14.8 V while running | 12.4–13.1 or 14.9–15.2 V | <12.0 or >15.5 V | label context `engine state unknown`; do not alarm on key-on alone |
| RPM | 0–redline configured by profile | near profile limit | above profile limit | use profile setting; no generic redline pretence |
| Throttle/load | no universal fault band | none by default | none by default | trend only; avoid implying a problem from normal driving input |
| Fuel trim | -10% to +10% | ±10–20% | ±20% | show `TRIM OUTSIDE DEFAULT` and link to detail, not `FAULT` |
| Intake temperature | profile-dependent | >45 °C | >60 °C | amber/coral only when a profile provides context |

Rules: (a) status evaluation runs only on a successful current sample; (b) missing/unavailable has precedence over thresholds; (c) stale has precedence over normal color; (d) one global severity strip summarizes the worst state; (e) users can mute threshold colors, but cannot hide `STALE`, `UNAVAILABLE`, or `DEMO` provenance.

Recommended freshness states: `CURRENT` ≤2 s, `AGING` >2–5 s, `STALE` >5–15 s, `EXPIRED` >15 s. These are UI defaults tied to the polling contract, not OBD standards. If the adapter reports a request timeout or no response, show the last value with a diagonal hatch and `STALE — no response 7 s`; after `EXPIRED`, show `—` plus `NO RECENT DATA`, preserving the last value only in detail history.

## 7. Unsupported, stale, error, and demo states

Each card needs a state machine, not a nullable number alone:

- **Unsupported:** `NOT SUPPORTED` / slate slash icon / no numeric value. Explain “vehicle or adapter did not advertise this PID.”
- **Not queried:** `NOT CHECKED` / action `CHECK SUPPORT` in Diagnostics bench.
- **No response:** `NO RESPONSE` / keep last successful timestamp in detail; count it in connection health.
- **Stale:** value may remain visible for orientation, but it is dimmed, patterned, and never colored normal. Show age.
- **Parse error:** `BAD RESPONSE` / offer raw response in Diagnostics; never show a guessed number.
- **Demo:** value can be bright, but every screen has a persistent `DEMO • SYNTHETIC` badge and a patterned orange/blue provenance rail. The demo notice must not rely on a toast.
- **Live:** `LIVE • READ-ONLY`; show source adapter and current freshness.
- **Connecting:** progress state with cancel; do not show old LIVE green without an age indicator.

Accessibility labels should read the complete fact, e.g. “Coolant, 91 degrees Celsius, current, live, sampled 0.9 seconds ago” or “Fuel level, unavailable, PID not supported; last checked today at 14:02.”

## 8. S21 portrait and landscape constraints

### Portrait (approximately 360–412dp content width)

- Header rail consumes no more than 72dp; keep the mode badge visible while scrolling.
- Two hero cards side by side only when font scale ≤1.15; at larger scale stack them.
- Secondary cards use two columns, minimum 112dp width and 104dp height. Six cards require vertical scroll; never shrink values below 28sp.
- Bottom navigation targets are 64dp high including system insets. The primary connect/stop action remains reachable without scrolling.
- Detail sheets are full-width, max three actions in the first view; raw data is a disclosure.

### Landscape (S21 about 800dp wide in common orientation)

- Use a left navigation rail of 80–96dp and a content area; do not simply stretch portrait cards.
- Cockpit: hero row takes 58–62% of content width, secondary grid takes the remainder; keep all six secondary cards visible without horizontal scrolling at default font scale.
- Diagnostics bench can use two panes: requests/status left, selected response detail right. Collapse to a single vertical flow at accessibility font scales.
- Respect cutouts, gesture insets, and rotation without resetting selected layout, card order, or DEMO/LIVE state.
- `android:configChanges` currently includes orientation/screen size (`app/src/main/AndroidManifest.xml:17-20`); still retain state in a view model/saved state rather than relying on configuration suppression.

## 9. Accessibility and legibility acceptance bar

- Every interactive control has a semantic label, role, selected/checked state, and a 48×48dp minimum touch target. This directly fixes the current text-only tab targets.
- No meaning is conveyed by color alone. Test with grayscale and common red/green color-vision simulation.
- Support font scales 1.0, 1.3, and 2.0 without clipped numbers, truncated units, or inaccessible overflow actions. Use `sp`, not fixed-pixel typography.
- TalkBack order: connection/provenance → severity → heroes → secondary cards → navigation. A stale/unsupported explanation follows the value in the same node or immediately after it.
- Avoid flashing and animated color changes. Sparklines animate only when a new sample arrives and pause when stale.
- Contrast target: at least 4.5:1 for normal text and 3:1 for large text/UI boundaries. Verify actual rendered colors rather than trusting token names.
- Provide a “high contrast” option that raises border/label contrast and replaces subtle hatch patterns with explicit status text.

## 10. Field-test protocol for the Ford Ka target

Do this parked, ignition ON, with the phone mounted or held by a second person. The product docs correctly call for capturing actual supported PIDs and cadence before tuning defaults (`docs/DASHBOARD-ROADMAP.md:47-49`).

1. Record adapter model/firmware, Bluetooth transport, Android version, vehicle profile, and timestamp. Never infer compatibility from the adapter label alone.
2. Connect with engine off; verify the UI shows `LINKING → LIVE`, adapter identity, initialization completion, and no synthetic values. Capture the PID support bitmap and response latency.
3. With engine off, confirm voltage and unsupported behavior. A missing PID must be `UNAVAILABLE`, not `0`.
4. Start the engine while parked. Observe 60 seconds at idle; check RPM, coolant, intake, load, throttle, manifold, trims, fuel level, MAF, and voltage for cadence and plausibility. Record min/max and the UI status, not just raw values.
5. Induce no-response safely by disconnecting Bluetooth only; verify per-card aging/stale/expired states, one severity summary, and no silent fall back to DEMO.
6. Read readiness and DTCs twice. Verify the states distinguish “not read”, “read with zero codes”, incomplete monitors, and actual codes. Do not clear anything; Mode 04 is out of scope.
7. Test DEMO separately and verify exports/history are visibly marked synthetic.
8. Rotate portrait/landscape and set font scale 1.3 and 2.0. Verify state, layout choice, card order, labels, and actions survive without clipping.

Field acceptance is a matrix of observed response/status evidence, not a screenshot approval. Store no cloud telemetry; any diagnostic bundle/export must be explicit, local-first, and reviewable before sharing, consistent with `README.md:13-16` and `docs/research.md:30-35`.

## 11. Implementation handoff order

1. Introduce a presentation `MetricState`/`MetricSnapshot` registry with per-PID timestamps, source, support, last error, and threshold status. Preserve parser null/error semantics.
2. Build tokenized card primitives: `MetricCard`, `HeroMetric`, `StatusRail`, `FreshnessBadge`, `ProvenanceBadge`, and a shared detail sheet. Add semantics and minimum target sizes at this layer.
3. Implement Cockpit responsive breakpoints and stateful edit mode; then add Race telemetry using the same cards and a bounded sample history.
4. Correct Faults/Readiness state modeling and implement Diagnostics bench before adding new adapters or more PIDs.
5. Add local layout/threshold persistence and saved-state restoration. Keep session recording/export behind live adapter validation.
6. Add screenshotless Compose tests for state rendering, semantics, font scaling, and portrait/landscape composition; use real adapter field evidence for thresholds and cadence.

### Definition of done for the first dashboard-system release

- Cockpit, Race telemetry, Diagnostics bench, Session, and Minimal are selectable from a preview picker and persist locally.
- Every card visibly distinguishes current, aging, stale, expired, unsupported, no response, parse error, DEMO, and LIVE.
- A successful empty DTC read is not confused with “not read”; readiness support and completion are separate.
- Portrait and landscape S21 layouts pass at default and 1.3× font scale without horizontal scrolling or clipped action labels.
- TalkBack and grayscale review find no color-only meaning, unreachable action, or unlabeled status.
- A parked Ford Ka adapter session produces a documented support/cadence matrix; no threshold is presented as a vehicle safety diagnosis.
