# Raven — Closure Sheet

## Header

- Case: Raven (minimum-diameter 3-fin)
- Current error: **+7.6%** apogee overshoot (2026-04-30 outlier-closure rerun; real 8,815 ft Baro; RASAero 9,332 ft, +5.9%)
- Status: **CLOSED** under the hard ±10% implementation goal
- Target: within ±10% (ideally ±5%)
- Regime: transonic (peak M = 1.07)
- Source: `core/build/reports/simvreal-outliers/Raven.md`
- Note on data freshness: this sheet supersedes the April 17 open-outlier state (`+24.22%`). The current headline value is from `paper/data/corpus_summary_2026_04_30.md` and `core/build/reports/simvreal-outliers/Raven.md`.

## Import parity warnings

- Parity matrix (`paper/data/csv/simvreal_parity_matrix.csv` row 7): `ParityClass = CLEAN`, `UnsupportedActiveCount = 0`.
- ModifiedBarrowman = False, Turbulence = False, SustainerNozzleDiameter = 0.
- Loader warnings in per-case report: none.
- Simulation warnings: "No recovery device defined in the simulation" (tumbling descent; does not affect ascent apogee).
- Conclusion: no CDX1 import artifact to explain the overshoot. Residual is pure aerodynamic-model deficit.

## Event timeline (from Raven.md)

- t = 0.000 s: launch, motor ignition (J570W in the body tube).
- t = 0.030 s: lift-off.
- t = 0.137 s: launch rod clearance.
- t = 2.052 s: motor burnout.
- t = 21.349 s: apogee (9,489 ft AGL).
- t = 24.639 s: tumbling (no recovery device).
- t = 135.270 s: ground hit / simulation end. Terminal note: NORMAL.

## Phase split (from Raven.md phase table)

| Phase | Duration | Max M | Avg Cd | Avg Cdf | Avg Cdp | Avg Cdb | Avg AoA |
|---|---:|---:|---:|---:|---:|---:|---:|
| boost | 2.052 s | 1.069 | 1.118 | 0.528 | 0.096 | 0.494 | 0.000° |
| coast | 19.297 s | 1.007 | 0.896 | 0.556 | 0.040 | 0.267 | 0.753° |
| descent | 113.921 s | 0.080 | 23.61 | 0.306 | 21.06 | 0.069 | 135.49° |

Coast is still the dominant altitude-gain phase, but the accepted rounded-fin/thick-boundary-layer/base-drag closure raises near-transonic axial drag enough to put the case inside the ±10% gate. Coast AoA remains low, so the closure is axial-drag driven rather than a stability/tumbling artifact.

## Peak-Mach drag breakdown (max-mach snapshot, M = 1.123, t = 1.698 s)

| Component | Cd total | Cdf | Cdp | Cdb | % of total |
|---|---:|---:|---:|---:|---:|
| Body Tube | 1.1697 | 0.2980 | 0.0009 | 0.8707 | 74.9% |
| Nose Cone | 0.0527 | 0.0255 | 0.0272 | 0.0000 | 3.4% |
| Fin | 0.0640 | 0.0157 | 0.0172 | 0.0312 | 4.1% |
| Rail Guide | 0.0739 | 0.0000 | 0.0739 | 0.0000 | 4.7% |
| **Rocket total** | **1.5623** | **0.3705** | **0.2275** | **0.9643** | |

Body-tube base drag is now the single dominant term, as expected for a long minimum-diameter transonic body with rounded fins near the aft base. Fin Cd remains too small to explain the closure by itself.

## Likely root-cause family

Transonic minimum-diameter body-tube base drag underprediction. The accepted closure combines a thick-boundary-layer base-drag multiplier for high-L/D airframes with rounded-fin subsonic/transonic wake scaling and saturated fin-count behavior. Raven is parity-clean, so this is an aerodynamic closure rather than an importer correction.

## Hypothesis falsification test

The closure would be falsified if the minimum-diameter/rounded-fin changes broke neighboring transonic cases or external base-drag anchors. The April 30 regression battery did not show that: full SimVReal, BaseDragModelTest, Basic Finner, RM-10, rail-button, and fin-wave regressions passed.

## Closure definition

**Closed when ORP apogee error |e| ≤ 10% on Raven with no regression in the A-level external benchmarks and no regression of any SimVReal case from within-10% to outside-10%.** This condition is now met: Raven is +7.6%, all 24 SimVReal cases are within ±10%, and the focused external aero/import regression battery passes.

## Current status

**CLOSED.** Parity-clean transonic outlier reduced to +7.6% without a case-specific Cd multiplier. Residual error is still positive and should be disclosed, but it is no longer a benchmark-blocking outlier.

## Exact files touched by this sheet

- `paper/data/outlier_closure/raven_closure.md` (this file; new)
