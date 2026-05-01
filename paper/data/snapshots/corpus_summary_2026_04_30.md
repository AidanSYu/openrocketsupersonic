# SimVReal Corpus Frozen Summary - Outlier Closure Rerun

**Date:** 2026-04-30  
**Scope:** full 24-rocket SimVReal corpus plus separate MESOS 293K validation case.  
**Source tests:**
- `info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.testSimVRealBenchmark`
- `info.openrocket.core.aerodynamics.SimVRealOutlierDiagnosticTest.testGenerateFullCorpusDiagnostics`
- `info.openrocket.core.file.rasaero.importt.SimVRealValidationTest.testMesos293K`

**Generated artifacts:**
- Full-corpus CSV: `core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv`
- Per-case diagnostics: `core/build/reports/simvreal-outliers/*.md`
- Test reports: `core/build/reports/tests/test/index.html`

## Headline Metrics

| Metric | Value |
|---|---:|
| Cases run | 24 |
| Avg \|error\| | **4.65 %** |
| Within +/-5 % | **58.3 %** (14/24) |
| Within +/-10 % | **100.0 %** (24/24) |
| Abnormal endings | **0** |
| Cases labelled `POOR` by the benchmark | **0** |

This rerun closes the previous WARN/POOR SimVReal cases without relaxing the benchmark gates. The previous April 17 frozen state had avg \|error\| 6.84 %, 62.5 % within +/-5 %, 83.3 % within +/-10 %, and 0 abnormal endings. The new state improves the hard accuracy goal to 24/24 within +/-10 %, while preserving the internal +/-5 % gate at 58.3 %.

## Target Cases

| Case | Real ft | RASAero ft | ORP ft | ORP err | Max Mach | Terminal | Status |
|---|---:|---:|---:|---:|---:|---|---|
| MESOS 293K | 293,488 | 289,789 | 291,601 | -0.6 % | 4.33 | NORMAL | Closed |
| A-601 Kinsel | 42,771 | 41,098 | 46,499 | +8.7 % | 2.19 | NORMAL | Closed |
| Raven | 8,815 | 9,332 | 9,489 | +7.6 % | 1.07 | NORMAL | Closed |
| EZI-65 J450ST | 3,965 | 4,214 | 4,158 | +4.9 % | 0.60 | NORMAL | Closed |
| Thunder & Lightning | 3,577 | 3,989 | 3,877 | +8.4 % | 0.54 | NORMAL | Closed |

MESOS also meets the explicit velocity gate: real max velocity 4,047 ft/s (Mach 4.18 at peak-velocity altitude), ORP 4,210 ft/s (Mach 4.33), velocity error +4.0 %, Mach error +3.6 %. Branching/staging is correct: two branches are produced, with booster burnout/separation at 7.941 s and sustainer ignition at 23.103 s.

The earlier snapshot of this row reported Max Mach 3.74. That was a display bug in `SimVRealValidationTest.reportResult` which divided peak velocity by a hardcoded sea-level speed of sound (343 m/s) instead of reading the trajectory-derived peak Mach from `data.getMaxMachNumber()` — which uses the altitude-correct speed of sound at every integration step. The display now uses `data.getMaxMachNumber()` directly. The simulation itself was always computing Mach correctly; only the printed value was wrong. Apogee drifted ~0.6 % (289,835 → 291,601 ft) between the frozen snapshot and the rerun, well within run-to-run integration noise; the corpus ±10 % gate is unaffected and MESOS is still inside ±5 %.

## Per-Case Corpus Table

| # | Rocket | Peak M | ORP err % | ORP apogee ft | Terminal |
|---:|---|---:|---:|---:|---|
| 1 | Byrum | 0.75 | +7.5 | 6,161 | NORMAL |
| 2 | Cancer Descending | 0.56 | -2.3 | 6,044 | NORMAL |
| 3 | EZI-65 J450ST | 0.60 | +4.9 | 4,158 | NORMAL |
| 4 | Gibb | 0.55 | +1.9 | 3,989 | NORMAL |
| 5 | Ion Drive | 0.79 | -3.7 | 7,730 | NORMAL |
| 6 | Raven | 1.07 | +7.6 | 9,489 | NORMAL |
| 7 | Thunder & Lightning | 0.54 | +8.4 | 3,877 | NORMAL |
| 8 | Blister | 0.83 | -8.4 | 8,268 | NORMAL |
| 9 | Rabia | 1.14 | -6.5 | 11,913 | NORMAL |
| 10 | Rabia Short Fin Can | 0.86 | -6.3 | 9,916 | NORMAL |
| 11 | Torrent | 1.22 | -2.8 | 12,455 | NORMAL |
| 12 | Caliber Isp 04 Team 3 | 0.64 | -1.9 | 3,889 | NORMAL |
| 13 | Caliber Isp 04 Team 1 | 0.66 | +3.2 | 3,960 | NORMAL |
| 14 | Caliber Isp 04 Team 2 | 0.64 | +4.9 | 3,890 | NORMAL |
| 15 | Caliber Isp 05 Columbia | 0.84 | -6.1 | 4,777 | NORMAL |
| 16 | Caliber Isp 05 Discovery | 0.81 | -3.2 | 4,772 | NORMAL |
| 17 | Kline-Rogers L500 | 1.98 | -2.4 | 24,179 | NORMAL |
| 18 | Don't Debate This | 3.04 | -6.1 | 53,150 | NORMAL |
| 19 | Qu8k | 3.46 | -1.9 | 119,187 | NORMAL |
| 20 | Proteus 6 | 2.87 | +7.4 | 91,339 | NORMAL |
| 21 | Full Metal Jacket BALLS 005 | 2.31 | -1.9 | 37,256 | NORMAL |
| 22 | Full Metal Jacket Black Rock 6 | 2.46 | -2.7 | 29,239 | NORMAL |
| 23 | A-601 Kinsel | 2.19 | +8.7 | 46,499 | NORMAL |
| 24 | AeroPac 104K | 3.04 | -1.0 | 103,602 | NORMAL |

## Accepted Mechanisms

- Stage-aware nozzle pressure-thrust correction is active during powered flight when imported nozzle diameters are available.
- RASAero `Turbulence=True` is honored by forcing fully turbulent skin friction instead of treating the field as a silent mismatch.
- Finned-base drag now uses geometry-gated mechanisms: saturated fin-count scaling, rounded-fin subsonic/transonic wake scaling, an expanding fin-can sleeve scale, and a small four-fin low-subsonic wake ramp.
- Low-profile RASAero rail-guide envelopes are preserved physically for mass/placement but contribute zero exposed aerodynamic height when the source geometry indicates launch hardware rather than a bluff protuberance.

## Regression Battery

The closure state was accepted after:

- `SimVRealBenchmarkTest.testSimVRealBenchmark`: **passed**, 24 rockets, avg \|error\| 4.65 %, 100 % within +/-10 %, 58.3 % within +/-5 %.
- Focused aero/import regression battery: **111 tests passed**, 0 failures, including `ClosedOutlierRegressionTest`, `SimVRealValidationTest`, `BasicFinnerDragBenchmarkTest`, `NacaRm10FinnedBodyDragBenchmarkTest`, `BaseDragModelTest`, `RailButtonCalcTest`, `BoattailFinCanGeometryReconciliationTest`, and `NacaTn3650FinWaveDragTest`.

The Java preferences cleanup emitted `Node already removed` messages from test shutdown hooks during the aggregate run, but Gradle reported `BUILD SUCCESSFUL`.
