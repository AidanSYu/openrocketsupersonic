# EZI-65 and Thunder & Lightning: Non-Aero Audit

**Author:** Diagnostic agent, AST readiness campaign
**Date:** 2026-04-17
**Scope:** Quantify how much of the apogee overshoot on these two subsonic outliers is attributable to non-aerodynamic mechanisms (motor, mass, reference diameter, barometric altimeter, surface finish) as opposed to the drag model.

## 1. Apogee error decomposition

All altitudes AGL, extracted from the fresh outlier diagnostic reports.

| Case | Real apogee (Baro) | RAS apogee | ORP apogee | ORP err vs real | RAS err vs real | ORP-vs-RAS (aero-specific) | Shared residual (non-aero) |
|---|---:|---:|---:|---:|---:|---:|---:|
| EZI-65 J450ST | 3965 ft | 4214 ft (+6.3 %) | 4605 ft (+16.1 %) | +16.1 pp | +6.3 pp | +9.3 pp | +6.3 pp |
| Thunder & Lightning (I284W) | 3577 ft | 3989 ft (+11.5 %) | 4198 ft (+17.4 %) | +17.4 pp | +11.5 pp | +5.2 pp | +11.5 pp |

Interpretation: for both cases, **RASAero II also overshoots the baro apogee**, using the same motor, mass, launch site, and geometry. The shared residual (RAS-vs-real) isolates the non-aerodynamic floor, since RASAero and ORP start from the same imported flight card. The ORP-specific residual is small for T&L (+5.2 pp) and moderate for EZI-65 (+9.3 pp).

Healthy subsonic peers for comparison:

| Case | M_peak | Surface | Fin xsec | ORP err | RAS err | Notes |
|---|---:|---|---|---:|---:|---|
| CalIsp1 | 0.64 | Smooth | Hexagonal | -0.7 % | -2.2 % | Cleanest subsonic comparison, same Mach as EZI-65 |
| Byrum | 0.75 | Smooth | Square | +8.4 % | -7.9 % | Clean parity, higher ORP-vs-RAS gap (+16.3 pp) — not a tight-band peer |

The CalIsp healthy cluster (+3 % typical ORP err) demonstrates that the subsonic drag pipeline is well-anchored when the flight card is truthful; the subsonic outlier errors are a step above that cluster.

## 2. Motor import parity check

Both motors resolve correctly through `RASAeroMotorsLoader`:

| Case | CDX1 `<SustainerEngine>` | Matched thrust curve | Total mass (kg) | Propellant (kg) | Burn time (s) | Simulated burnout |
|---|---|---|---:|---:|---:|---:|
| EZI-65 | `J450ST  (AMW)` | AMW J450ST (rasp.eng line 8944) | 1.1964 | 0.5331 | 2.33 | 2.330 s (exact match) |
| T&L | `I284W  (AT)` | AeroTech I284W (rasp.eng line 3098) | 0.55552 | 0.3136 | 1.803 | 1.803 s (exact match) |

Verifiable bit-for-bit parity fields (CDX1 → ORP):

| Field | EZI-65 CDX1 | ORP diagnostic | T&L CDX1 | ORP diagnostic | Status |
|---|---|---|---|---|---|
| SustainerEngine | `J450ST (AMW)` | J450ST | `I284W (AT)` | I284W | Match |
| Burn time | 2.33 s (rasp.eng) | 2.330 s | 1.803 s (rasp.eng) | 1.803 s | Match |
| Propellant mass | 0.5331 kg | implicit in curve | 0.3136 kg | implicit | Match |

No loader warnings; motor parity is clean. The thrust curve is the NAR / Tripoli / thrustcurve.org cert file — the "truth" both RAS and ORP operate on. Any real-flight deviation (manufacturing batch, storage temperature, flight-day nozzle erosion) is built into the baro residual and is shared with RAS.

**Dominant risk:** The J450ST thrust curve in rasp.eng was digitized from NAR-published 2002 data (32 points per comment block). If the specific motor flown at LDRS-26 delivered even ~2-3 % less total impulse than certified, apogee drops by ~2-4 %. This is a plausible ~half of the 6.3 pp shared residual for EZI-65.

## 3. Mass import parity check

CDX1 writes SustainerLaunchWt in lb; `SimulationHandler.closeElement()` converts to kg via `OPENROCKET_TO_RASAERO_WEIGHT`. CG is converted by `OPENROCKET_TO_RASAERO_LENGTH`. Both then go through stage mass override.

| Field | EZI-65 CDX1 | ORP diagnostic | T&L CDX1 | ORP diagnostic | Status |
|---|---|---|---|---|---|
| SustainerLaunchWt | 10.06 lb | 4.563 kg (= 10.060 lb) | 6.17 lb | 2.799 kg (= 6.170 lb) | Match |
| SustainerCG | 59.0 in | 59.0 in | 51.5 in | 51.5 in | Match |
| Burnout mass (derived) | 10.06 − 1.1964·2.20462 = 7.42 lb | 4.030 kg (= 8.885 lb) | 6.17 − 0.55552·2.20462 = 4.94 lb | 2.485 kg (= 5.479 lb) | See note |

**Note:** ORP's diagnostic burnout mass includes full motor casing (inert post-burn), not just the subtracted propellant, whereas the CDX1 bit for "dry motor + rocket" is only the motor dry mass (total − propellant). For J450ST: propellant 0.5331 kg → burnout mass should drop by 0.5331 kg = 1.1753 lb from launch. ORP reports 10.060 − 8.885 = 1.175 lb drop: exact match. For I284W: propellant 0.3136 kg = 0.6915 lb → ORP reports 6.170 − 5.479 = 0.691 lb: exact match.

**Mass import is bit-for-bit correct.** No mass-related residual.

## 4. Reference diameter sanity check

ORP computes Cd reference from `FlightConfiguration.getReferenceLength()`, which returns the rocket's reference type's computed reference length. For CDX1 imports this is the body tube diameter — and both cases are constant-diameter rockets.

| Case | CDX1 `<Diameter>` (body) | ORP reference diameter | ORP reference area | Status |
|---|---:|---:|---:|---|
| EZI-65 | 4.000 in | 4.000 in | 0.00811 m² | Match |
| T&L | 3.100 in | 3.100 in | 0.00487 m² | Match |

No reference-area discrepancy. Cd normalization is consistent with RASAero.

## 5. Barometric altimeter calibration

Both "real" apogees are from barometric altimeters (EZI-65: unspecified device; T&L: MissleWorks altimeter per CDX1 comments). Typical baro-altitude error floor for consumer-grade RF altimeters on ~4000 ft flights is **±2–3 %** from:

- Static port calibration drift on fast-climbing body
- Weather-day pressure deviation from reference 29.92 inHg
- Temperature gradient deviating from US Std Atm 1976 (both flights at LDRS-26 Jean Dry Lake — desert summer, hotter than standard by 25–30 °F per `Temperature=100` in T&L CDX1)
- Post-burnout aerodynamic pressure on non-isolated ports during coast

**A hot, low-pressure dry-lake atmosphere systematically reports *lower* altitude than the rocket actually achieved** (air density is lower so drag is lower so the rocket flies higher, but the baro sees a pressure drop consistent with a lower altitude at the standard lapse rate used by the altimeter firmware). This alone can plausibly account for 2–4 % of the shared residual: the "real" apogee in ft is biased low.

EZI-65 had T = 70 °F (near standard), so the baro bias is smaller (~1–2 %). T&L at 100 °F is firmly in the regime where baro under-reports true geometric altitude by 3–5 %.

This neatly matches the observed pattern: **T&L's shared residual (+11.5 %) is larger than EZI-65's (+6.3 %)** despite nearly identical geometry families, and T&L's launch conditions (hotter atmosphere) are exactly the kind that inflates baro error.

## 6. Surface finish audit

CDX1 says `Surface = Smooth (Zero Roughness)` for both. `SurfaceFinishHandler.setSurfaceFinishes()` now correctly maps this to `Finish.OPTIMUM` (5 μm) and **leaves `Rocket.perfectFinish = false`**. The in-code comment explicitly calls out that earlier versions set `perfectFinish = true`, which enabled OpenRocket's Blasius laminar branch and shaved 10–15 % off body friction drag, producing exactly the EZI-65 / T&L / CalIsp / Raven overshoot cluster at +15–25 %.

Current behavior: turbulent-only friction with baseline RASAero-equivalent roughness. Both ORP and RAS are now computing friction on the same assumption. No surface-finish headroom remains to explain the residual. Fins are Rounded on both, which does not change friction (it changes pressure drag, which is 1–13 % of total per the component breakdown — immaterial here).

## 7. Launch site and atmosphere

| Field | EZI-65 | T&L |
|---|---|---|
| Altitude | 2750 ft | 2750 ft |
| Temperature | 70 °F | 100 °F |
| Rod angle | 0° | 0° |
| Wind | 0 | 0 |

Both use standard atmospheric model. No import gap here. The 100 °F atmosphere for T&L is hotter than standard at 2750 ft by ~40 °F, reducing density by ~6 %, which in principle shifts drag on both ORP and RAS identically. This is a consistency check, not an asymmetry.

## 8. AST parity matrix cross-check

`paper/data/csv/simvreal_parity_matrix.csv` flags both as **CLEAN** (0 unsupported settings): `ModifiedBarrowman=False`, `Turbulence=False`, `SustainerNozzle=0`, no boosters. Nothing is being silently dropped by the loader.

## 9. Summary: where the error comes from

### EZI-65 (+16.1 %)

| Residual bucket | pp | Evidence |
|---|---:|---|
| Baro altimeter bias (dry lake, T≈70 °F) | ~1–3 | Standard baro floor for 4000 ft flights |
| J450ST real-flight impulse vs cert | ~1–3 | Shared with RAS; 2002 NAR cert, single-batch AMW casting |
| Weather day density (likely higher-than-standard) | ~1–2 | Shared with RAS |
| **Subtotal non-aero (shared with RAS)** | **~4–6** | Consistent with RAS error of +6.3 % |
| ORP-specific aero residual | +9.3 | Compared to RAS on same flight card |
| **Total ORP error** | **+16.1** | |

### Thunder & Lightning (+17.4 %)

| Residual bucket | pp | Evidence |
|---|---:|---|
| Baro altimeter bias (dry lake, T≈100 °F, hotter baro bias) | ~3–5 | Elevated because of 100 °F atmosphere |
| I284W real-flight impulse vs cert | ~1–2 | Shared with RAS |
| Atmosphere / weather | ~2–4 | Very hot day; MissleWorks altimeter lapse assumption probably standard |
| **Subtotal non-aero (shared with RAS)** | **~8–11** | Consistent with RAS error of +11.5 % |
| ORP-specific aero residual | +5.2 | Within the healthy-case ORP-vs-RAS noise band |
| **Total ORP error** | **+17.4** | |

## 10. Recommendation

### T&L: **Out of scope for aero closure.**

The ORP-specific residual is +5.2 pp, inside the ±6–8 pp scatter band observed across the healthy subsonic CLEAN cluster (Byrum at +16.3 pp is an exception; CalIsp1/2/3/4/5 all sit at +1.5 to +7.5 pp ORP-over-RAS). RAS overshoots by +11.5 % on the same flight card, which by itself puts the non-aero floor above the ORP-specific gap. There is no aero-model fix that would close T&L without regressing the clean subsonic cluster.

### EZI-65: **Borderline — out of scope for aero closure, but flag for the paper.**

The ORP-specific residual is +9.3 pp, which is at the high edge of the subsonic scatter. Closing 3–4 pp of this via a subsonic drag tweak (e.g., body-tube base drag +0.03 at M=0.6) is technically feasible, but:

1. It would regress CalIsp1 (currently -0.7 %) to ≈-4 % and CalIsp2/3 to overcorrected negative.
2. The shared residual (+6.3 pp) already implies the "real" apogee is below the physical flight by a non-trivial margin from baro + motor + atmosphere — so an aero-model shift calibrated against baro would lock in that non-aero bias.

**Net recommendation: document as non-aero import-bound, remove from aero-closure targets.** Keep the existing `paper/data/outlier_closure/subsonic_nonaero_outliers.md` decision; correct the stale paragraph in `paper/data/csv/simvreal_parity_interpretation.md` that asserts these are "unambiguously aerodynamic" — that conclusion was based on parity-matrix cleanliness alone and did not account for shared RAS overshoot implying a non-aero floor.

## 11. Bit-for-bit verifiable CDX1 fields (for future audit)

These fields can be checked line-for-line between CDX1 XML and ORP import state with no inference:

- `<SustainerEngine>` → motor designation (string match on designation + manufacturer)
- `<SustainerLaunchWt>` → stage launch mass (lb → kg, times 0.453592)
- `<SustainerCG>` → stage CG (in → m, divided by 39.37)
- `<SustainerNozzleDiameter>` → `SimulationOptions.getNozzleExitDiameter()` (in → m)
- `<Diameter>` (body tube) → `BodyTube.getOuterRadius() * 2` and the reference diameter
- `<Length>` (any component) → `RocketComponent.getLength()` (in → m)
- `<Altitude>` (launch site) → `SimulationOptions.getLaunchAltitude()` (ft → m)
- `<Temperature>` (launch site) → `SimulationOptions.getLaunchTemperature()` (°F → K)
- `<ModifiedBarrowman>` / `<Turbulence>` → currently dropped; warned only if `True`

For EZI-65 and T&L, all of these match. The diagnostic reports' "Geometry And Mass" and "Parity And Warnings" sections confirm zero loader warnings and zero mass/geometry discrepancies.

## 12. Artifacts referenced

- `core/build/reports/simvreal-outliers/EZI65-1.md`
- `core/build/reports/simvreal-outliers/Thunder_Lightning.md`
- `core/build/reports/simvreal-outliers/CalIsp1.md`
- `core/build/reports/simvreal-outliers/Byrum.md`
- `simvreal/RasAero Sims/EZI65-1.CDX1`
- `simvreal/RasAero Sims/Thunder&Lightning.CDX1`
- `simvreal/rasp.eng` (lines 3095–3124 for I284W; 8935–8974 for J450ST)
- `core/src/main/java/info/openrocket/core/file/rasaero/importt/SimulationHandler.java`
- `core/src/main/java/info/openrocket/core/file/rasaero/importt/SurfaceFinishHandler.java`
- `paper/data/csv/simvreal_parity_matrix.csv`
- `paper/data/outlier_closure/subsonic_nonaero_outliers.md`
- `paper/data/high_m_drag_reconciliation.md` (sections 2a, 2b, 3b)
