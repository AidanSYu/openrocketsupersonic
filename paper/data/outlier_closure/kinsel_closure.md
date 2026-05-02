# A-601 Kinsel — Closure Sheet

## Header

- Case: A-601 Kinsel (large HPR, 4-fin, expanding fin-can shoulder)
- Current error: **+8.7%** apogee overshoot (2026-04-30 outlier-closure rerun)
- Status: **CLOSED** under the hard ±10% implementation goal
- Target: within ±10% (ideally ±5%)
- Regime: supersonic (peak M = 2.19)
- Source: `core/build/reports/simvreal-outliers/Kinsel_P4935_A-601_Rocket.md`
- Note on data freshness: this sheet supersedes the April 17 open-outlier state (`+28.14%`). The current headline value is from `paper/data/corpus_summary_2026_05_01.md` and `core/build/reports/simvreal-outliers/Kinsel_P4935_A-601_Rocket.md`. The April 30 snapshot is now archived at `paper/data/snapshots/corpus_summary_2026_04_30.md`.

## Import parity warnings

- Parity matrix (`simvreal_parity_matrix.csv` row 24): `ParityClass = CONTAMINATED`, `UnsupportedActiveCount = 2`.
- Loader warnings (Kinsel_P4935_A-601_Rocket.md §"Loader warnings"):
  - `Ignoring unsupported RASAero setting ModifiedBarrowman=True`
  - `Ignoring unsupported RASAero setting Turbulence=True`
  - `Ignoring unsupported RASAero setting SustainerNozzle=3.09`
- Prompt 3 / Prompt 4 bounding (`paper/data/legacy/CDX1_UNSUPPORTED_SETTINGS_SENSITIVITY.md`):
  - `SustainerNozzleDiameter` IS correctly applied via `SimulationHandler.setNozzleExitDiameter()`; the `SustainerNozzle=3.09` warning is the *redundant* `<RocketDesign>` copy. Live sensitivity on Kinsel = 0.0% apogee delta.
  - `Turbulence=True` analytical bound: <1.2% apogee (5% laminar cap bounds the impact).
  - `ModifiedBarrowman=True` analytical bound: <2% apogee (ORP Phase 3 provides equivalent corrections and it is a stability-only flag).
- Combined unsupported-setting bound for Kinsel remains small relative to the former +28% miss. The accepted closure is not a per-rocket importer fudge; it comes from geometry-gated fin-can/base-drag physics plus existing nozzle/turbulence parity work.
- Sim warnings: none.

## Event timeline (from Kinsel_P4935_A-601_Rocket.md)

- t = 0.000 s: launch, motor ignition (P4935 in the fin-can body tube).
- t = 0.043 s: lift-off.
- t = 0.430 s: launch rod clearance.
- t = 11.930 s: motor burnout.
- t = 51.651 s: apogee (46,499 ft AGL; real = 42,771 ft GPS).
- t = 59.002 s: Recovery Event 1 (drogue) deployed.
- t = 996.180 s: Recovery Event 2 (main) deployed.
- t = 1063.832 s: ground hit / simulation end under the benchmark cap.

## Phase split (from Kinsel phase table)

| Phase | Duration | Max M | Avg Cd | Avg Cdf | Avg Cdp | Avg Cdb | Avg AoA |
|---|---:|---:|---:|---:|---:|---:|---:|
| boost | 11.930 s | 2.185 | 0.561 | 0.243 | 0.056 | 0.263 | 0.001° |
| coast | 39.721 s | 2.185 | 0.637 | 0.283 | 0.036 | 0.269 | 0.752° |
| descent | 1012.181 s | 0.080 | (dominated by parachute) | | | | 91.31° |

Coast remains the altitude-dominant phase, but the accepted fin-can/base-drag closure reduced coast gain and moved the case under the hard ±10% gate. The MAXTIME fragility is also gone: ground hit now occurs with wide margin.

## Peak-Mach drag breakdown (max-mach snapshot, M = 2.328, t = 11.920 s)

| Component | Cd total | Cdf | Cdp | Cdb | % of total |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.0976 | 0.0859 | 0.0118 | 0.0000 | 19.8% |
| Nose Cone | 0.0464 | 0.0177 | 0.0287 | 0.0000 | 9.4% |
| Fin | 0.0086 | 0.0066 | 0.0020 | 0.0000 | 1.7% |
| Fin Can | 0.2601 | 0.0161 | 0.0021 | 0.2419 | 52.7% |
| Fin Can Shoulder | 0.0004 | 0.0004 | 0.0000 | 0.0000 | 0.1% |
| Rail Guide | 0.0274 | 0.0000 | 0.0274 | 0.0000 | 5.5% |
| **Rocket total** | **0.4937** | **0.1463** | **0.1055** | **0.2419** | |

The Fin Can base drag (Cdb = 0.242) is now the dominant closure term. The accepted model treats Kinsel as a 4-fin expanding fin-can sleeve, applying the sleeve-specific base scale while preserving the Basic Finner external benchmark. Fin drag remains too small to be the closing mechanism by itself.

## Likely root-cause family

Supersonic fin-can base drag underprediction. The accepted closure uses geometry rather than a case switch: 4 fins at an aft expanding sleeve increase wake/base-pressure deficit relative to a smooth cylindrical afterbody. Stage-aware nozzle pressure-thrust and fully turbulent import parity are also active, but the largest Kinsel movement comes from the fin-can base term.

## Hypothesis falsification test

The closure would be falsified if the expanding-sleeve scale regressed the Basic Finner, RM-10, or full SimVReal gates. It did not: `BasicFinnerDragBenchmarkTest`, `NacaRm10FinnedBodyDragBenchmarkTest`, and the 24-case SimVReal benchmark all passed in the April 30 regression battery.

## Closure definition

**Closed when ORP apogee error |e| ≤ 10% on Kinsel with no regression in the A-level external benchmarks and no new outliers created in the SimVReal corpus.** This condition is now met: Kinsel is +8.7%, all 24 SimVReal cases are within ±10%, and the focused external aero/import regression battery passes.

## Current status

**CLOSED.** The case is now within the hard ±10% implementation goal at +8.7% and terminates normally at t = 1063.832 s. Residual error remains positive and should be disclosed, but it is no longer a benchmark-blocking outlier.

## Exact files touched by this sheet

- `paper/data/outlier_closure/kinsel_closure.md` (this file; new)
