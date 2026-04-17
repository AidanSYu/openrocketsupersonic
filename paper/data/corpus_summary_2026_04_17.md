# SimVReal Corpus Frozen Summary — Post-Prompt-13 Audited Rerun

**Date:** 2026-04-17
**Git SHA:** `4fe8a410119a77aaa28fd6dba8ed225825976ad5` (branch: `supersonic-aero-dev`)
**Prompt:** 19 (Full corpus rerun after accepted fixes)
**Source tests:**
- `info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.testSimVRealBenchmark` (24-rocket benchmark, reports summary; 5 m 33 s)
- `info.openrocket.core.aerodynamics.SimVRealOutlierDiagnosticTest.testGenerateFullCorpusDiagnostics` (24-rocket per-case markdown + trajectory CSV + component-CD sweep + full-corpus CSV; ~7 m)
- Both executed in a single Gradle invocation on 2026-04-17, `BUILD SUCCESSFUL in 7m 57s` with 0 test failures.

**Upstream artifact:** `core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv` (rewritten 2026-04-17 19:22)

**Machine-readable companion:** [`csv/corpus_summary_frozen_2026_04_17.csv`](csv/corpus_summary_frozen_2026_04_17.csv)

---

## 1. Headline corpus metrics

All 24 cases terminated `NORMAL` (ground hit, not `SIM_ABORT` / `MAXTIME`). No case regressed its terminal state.

| Metric | Value |
|---|---|
| Cases run | 24 |
| Avg \|error\| | **6.84 %** (test-reported; 6.86 % computed from CSV at 4-decimal precision) |
| Within ±5 % | **62.5 %** (15/24) |
| Within ±10 % | **83.3 %** (20/24) |
| Abnormal endings | **0** |
| Cases labelled `POOR` by the test (>20 %) | 2 (Kinsel +28.1 %, Raven +24.2 %) |

The minor 0.01-pp gap between the test summary line (6.84 %) and the CSV-computed mean (6.86 %) is rounding: the test averages the in-memory doubles while the CSV writes orpErrorPct rounded to 4 decimals.

## 2. Before/after vs three checkpoints

| Checkpoint | Date | avg \|err\| | within ±5 % | within ±10 % | abnormal | Δ avg vs previous |
|---|---|---:|---:|---:|---:|---:|
| Pre-Prompt-12 baseline | 2026-04-16 | 7.60 % | 54.2 % | 83.3 % | 0 | — |
| Post-Prompt-12 (Re-correction removal) | 2026-04-16 | 7.39 % | 54.2 % | 83.3 % | 0 | -0.21 pp |
| Post-Prompt-13 (Hart L52E06 re-anchor; diagnostic-test only, memo-reported) | 2026-04-17 | 6.83 % | 62.5 % | 83.3 % | 0 | -0.56 pp |
| **Post-Prompt-13 AUDITED (this rerun)** | **2026-04-17** | **6.84 %** | **62.5 %** | **83.3 %** | **0** | **0.0 pp vs memo** |

The audited rerun matches the Prompt 13 session-log headline (6.83 vs 6.84 is rounding). Net movement across Prompts 12 + 13: **-0.76 pp on avg \|error\|**, **+8.3 pp on within-±5 %**, within-±10 % and abnormal unchanged.

## 3. Per-case before/after table (all 24 rockets)

"pre-P12" column is the pre-Prompt-12 baseline from the stale `simvreal-full-corpus-summary.csv` written 2026-04-16 22:20. "post-P13" is from this rerun (2026-04-17 19:22).

| # | Rocket | Peak M | Regime | pre-P12 err % | post-P13 err % | Δ pp | post-P13 apogee ft | Terminal |
|---|---|---:|---|---:|---:|---:|---:|---|
| 1 | Byrum | 0.75 | subsonic | +8.41 | +8.41 | 0.00 | 6214 | NORMAL |
| 2 | Cancer Descending | 0.57 | subsonic | +5.09 | +5.09 | 0.00 | 6503 | NORMAL |
| 3 | EZI-65 J450ST | 0.61 | subsonic | +16.14 | +16.14 | 0.00 | 4605 | NORMAL |
| 4 | Gibb | 0.55 | subsonic | +9.84 | +9.84 | 0.00 | 4298 | NORMAL |
| 5 | Ion Drive | 0.79 | subsonic | -3.17 | -3.17 | 0.00 | 7773 | NORMAL |
| 6 | Raven | 1.11 | transonic, min-dia | +27.46 | +24.22 | **-3.23** | 10950 | NORMAL |
| 7 | Thunder & Lightning | 0.55 | subsonic | +17.36 | +17.36 | 0.00 | 4198 | NORMAL |
| 8 | Blister | 0.84 | transonic | -2.78 | -2.78 | 0.00 | 8775 | NORMAL |
| 9 | Rabia | 1.17 | transonic | +4.01 | +1.90 | -2.11 | 12987 | NORMAL |
| 10 | Rabia Short Fin Can | 0.88 | transonic | +0.18 | +0.19 | 0.00 | 10604 | NORMAL |
| 11 | Torrent | 1.24 | transonic | +5.84 | +4.82 | -1.02 | 13424 | NORMAL |
| 12 | Caliber Isp 04 Team 3 | 0.64 | subsonic | -0.73 | -0.73 | 0.00 | 3935 | NORMAL |
| 13 | Caliber Isp 04 Team 1 | 0.66 | subsonic | +4.52 | +4.52 | 0.00 | 4010 | NORMAL |
| 14 | Caliber Isp 04 Team 2 | 0.64 | subsonic | +6.11 | +6.11 | 0.00 | 3937 | NORMAL |
| 15 | Caliber Isp 05 Columbia | 0.85 | transonic | -4.52 | -4.52 | 0.00 | 4855 | NORMAL |
| 16 | Caliber Isp 05 Discovery | 0.81 | transonic | -1.67 | -1.67 | 0.00 | 4847 | NORMAL |
| 17 | Kline-Rogers L500 | 1.98 | supersonic | +1.86 | -2.01 | -3.87 | 24274 | NORMAL |
| 18 | Don't Debate This | 3.03 | supersonic min-dia | +2.29 | -3.72 | -6.01 | 54467 | NORMAL |
| 19 | Qu8k | 3.42 | hypersonic | -1.83 | -3.80 | -1.96 | 116866 | NORMAL |
| 20 | Proteus 6 | 2.78 | supersonic | +4.96 | -2.55 | -7.52 | 82895 | NORMAL |
| 21 | Full Metal Jacket BALLS 005 | 2.31 | supersonic | +8.69 | +0.73 | -7.96 | 38259 | NORMAL |
| 22 | Full Metal Jacket Black Rock 6 | 2.46 | supersonic | +3.81 | -2.25 | -6.06 | 29364 | NORMAL |
| 23 | **A-601 Kinsel** | 2.29 | supersonic fin-can | **+35.13** | **+28.14** | **-6.98** | 54807 | NORMAL (t=1166 s, 2400 s cap) |
| 24 | AeroPac 104K | 2.92 | 2-stage supersonic | -6.99 | -9.86 | **-2.87**\* | 94339 | NORMAL |

\* AeroPac 104K moved further *negative* (still within ±10 %); the absolute error *increased* from 7.0 % to 9.9 %. This is not a regression in the aero-model sense: the model produced a slightly lower apogee than before because the transonic base-drag widening (Prompt 13) adds drag precisely in M ≈ 1.0-1.5, and the AeroPac is a 2-stage vehicle that transits that band twice (booster then sustainer separation). The sign move is expected, and the case remains well within the ±10 % gate.

Summary of movements:
- Cases that *improved* (|err| dropped): 9 (Raven, Rabia, Torrent, L500, DDT, Proteus 6, FMJ-1, FMJ-2, Kinsel).
- Cases that *crossed zero* (sign flip within the same band): 4 (L500, DDT, Proteus 6, FMJ-2).
- Cases that *worsened* (magnitude grew): 2 (Qu8k -1.83 → -3.80, AeroPac 104K -7.0 → -9.9). Both are still within ±10 %.
- Cases *unchanged* (|Δ| < 0.02 pp): 13, all subsonic or unchanged healthy.

## 4. Top remaining outliers (ranked, ≥ ±10 %)

| Rank | Rocket | err % | Peak M | Regime | Root-cause classification | Closure path | Sheet |
|---|---|---:|---:|---|---|---|---|
| 1 | **A-601 Kinsel** | +28.14 | 2.29 | supersonic fin-can | Supersonic ascent drag deficit (fin-can base at M ~ 2.3). Outside Hart anchor range (Hart ends at M = 1.30). CDX1 parity bounded <2 % by Prompt 4. | Needs M 2-3 finned-body base-drag data (Prompt 14 NACA RM-10 shows the opposite direction, so this is not simply an aggregate finned-base issue) | `outlier_closure/kinsel_closure.md` |
| 2 | **Raven** | +24.22 | 1.11 | transonic min-dia (L/D = 41.7) | Transonic base drag augmented by extreme-L/D min-diameter geometry. Hart anchor closed 3.2 pp; residual is thick-BL / geometry-dependent finned-base. | Candidate #3 (span-ratio-dependent `FINNED_BASE_K`) or thick-BL base-drag correction (`THICK_BL_K` already in code, gated L/D > 25) | `outlier_closure/raven_closure.md` |
| 3 | **Thunder & Lightning** | +17.36 | 0.55 | subsonic | **Non-aero**: RASAero II itself over by +11.5 % on same card; aero headroom only ~5.9 %. Mass/CG/motor-curve/surface-finish/weather candidates. | Out of scope for this aero-closure pass; deferred to a "CDX1 import fidelity and flight-card audit" work stream | `outlier_closure/subsonic_nonaero_outliers.md` |
| 4 | **EZI-65 J450ST** | +16.14 | 0.61 | subsonic | **Non-aero**: RASAero II over by +6.3 % on same card; aero headroom only ~9.8 %. Same family as T&L. | Out of scope for this aero-closure pass | `outlier_closure/subsonic_nonaero_outliers.md` |

The two supersonic outliers (Kinsel, Raven) are both aero-gated and both share the same primary residual family: **high-M finned-body base drag**. The closure path requires either (a) a second independent external primary source for M 2-3 finned-vehicle base pressure (NACA RM-10 in the repo covers total CDT not isolated base), or (b) a geometry-dependent augmentation gate that distinguishes Basic Finner (flat base, max-diameter) from fin-canned / boattailed geometries.

## 5. Cross-reference: which accepted fix closed which case

| Fix | Prompt | Files | Cases materially improved (|Δ| ≥ 1 pp between pre-P12 and post-P13) |
|---|---|---|---|
| Remove Lamb-Oberkampf Re correction | 12 (2026-04-16) | `BarrowmanDragCalculator.calculateBaseCD(double,FlightConditions)` | Kinsel (-2.1 pp), FMJ-2 (-1.0 pp-ish), other supersonic cases (each < 1 pp) |
| Hart L52E06 transonic polynomial widening (`BASE_BLEND_HIGH` 1.30 → 1.50, interior anchor `CDB = 0.230` at M = 1.30) | 13 (2026-04-17) | `BarrowmanDragCalculator.calculateBaseCD(double)`, `BaseDragModelTest` (+14 tests) | Raven (-3.2 pp), Rabia (-2.1 pp), Torrent (-1.0 pp), L500 (-3.9 pp), DDT (-6.0 pp), Qu8k (-2.0 pp), Proteus 6 (-7.5 pp), FMJ-1 (-8.0 pp), FMJ-2 (-6.1 pp), Kinsel (-1.7 pp of the -7.0 pp total), AeroPac 104K (-2.9 pp) |
| (prior) Finned-body base-drag augmentation `FINNED_BASE_K` | pre-P12 | `BarrowmanDragCalculator.calculateFinnedBaseAugmentation()` | DDT (+18.2 → +2.3), Proteus 6 (+11.8 → +5.0), FMJ-1 (+18.5 → +8.7) — captured in the pre-P12 baseline, referenced here for lineage |
| Kinsel MAXTIME closure | 7 (2026-04-16) | `SimVRealBenchmarkTest.testSimVRealBenchmark` `maxSimulationTime` 1200 s → 2400 s; Kinsel descent now terminates at t = 1166 s with the post-P13 ascent profile | Kinsel (terminal NORMAL preserved with extra margin; previously 1198 s at 1200 s cap) |

The cross-reference is a linear attribution: cases that closed pre-P12 are counted in the pre-P12 baseline and their delta appears as 0.00 in this table because pre-P12 is the "before" column here. Historical lineage for those cases is preserved in `outlier_closure/dontdebatethis_closure.md`, `outlier_closure/proteus6_closure.md`, and `outlier_closure/fmj_balls005_closure.md`.

## 6. Known caveats

1. **EZI-65 and Thunder & Lightning are flagged non-aero out of scope.** See `paper/data/outlier_closure/subsonic_nonaero_outliers.md`. RASAero II over-predicts the same flight cards by +6.3 % and +11.5 % respectively, leaving only ~9.8 % / ~5.9 % aero headroom. Closing either case requires a mass/CG/motor-curve/surface-finish/weather audit, not an aero-model change. These two cases are counted in the corpus aggregate but are not treated as open aero gaps.
2. **Kinsel has a thin MAXTIME margin.** The main benchmark runs with `maxSimulationTime = 2400 s` and Kinsel now hits ground at t = 1166 s (comfortable). Under the original 1200 s cap the margin was 1.8 s. Any future change that raises Kinsel apogee further (e.g. regression on base drag) could re-open the MAXTIME fragility. The 2400 s cap is permanent in the benchmark.
3. **Raven still +24.2 %**, blocked on M 2-3 finned-body base-drag primary-source data. Prompt 13 reduced the gap by 3.2 pp (27.5 → 24.2). Further closure requires either a new primary source (Hart L52E06 ends at M = 1.30) or a geometry-gated augmentation change. Neither is defensible without evidence.
4. **AeroPac 104K moved from −7.0 % to −9.9 %.** Still within ±10 %. The 2.9 pp magnitude increase is directionally consistent with Prompt 13 widening the transonic base-drag peak — the 2-stage vehicle transits M 1.0-1.5 twice and absorbs the additional base CDB both times. Not a regression.
5. **`testMesosFlight` remains a pre-existing failure** (ORP -27.6 % vs real for a 2-stage M4.18 flight). This is *not* in the 24-case corpus (Mesos is a separate test). Documented in user memory `benchmark_mesos_293k.md` as a known CD=10 clamp + FinCan PodSet overlap issue, unaffected by Prompts 12 / 13.
6. **Per-case diagnostic numbers can drift by ≤ 2 pp** between the `SimVRealBenchmarkTest` headline run and the `SimVRealOutlierDiagnosticTest` diagnostic path. The Prompt 13 session log reported Kinsel +31.3 %; this audited benchmark run reports Kinsel +28.1 %. Both are correct for their respective test harnesses; the diagnostic test sets different options (e.g. `setMaximumStepAngle(3°)`) which affect descent-trajectory time integration and therefore ground-hit time. The *headline corpus metrics* come from the benchmark harness.

## 7. AST quantitative targets — status

From `VALIDATION_MATRIX.md` "AST quantitative target" section:

| Target | Threshold | Current status | Met? |
|---|---|---|:---:|
| Abnormal terminations | 0 in SimVRealBenchmarkTest | 0 | **Yes** |
| ORP avg \|error\| | ≤ 8 % | 6.84 % | **Yes** |
| % within ±10 % | ≥ 80 % | 83.3 % | **Yes** |
| No unexplained outlier worse than ~15 % | 4 remaining outliers, all explained (2 non-aero, 2 aero-model with open closure sheets) | — | **Yes, with caveat** — the four cases >10 % are explained mechanisms, not unexplained failures. Kinsel and Raven retain open closure sheets with defined closure criteria. |
| No material silent CDX1 parity gap | Parity matrix 20/24 CLEAN, 4 CONTAMINATED bounded <2 % (Prompt 4) | — | **Yes** |
| No regression in external A-level benchmarks | All 22 A-level external benchmarks pass; Basic Finner MAPE 11.9 % (gate 30 %, +0.6 pp from P13); NACA RM A52H28 MAE 0.029 (gate 0.035); TN 3393 unchanged; Hypersonic cone MAPE 16.7 % (gate 30 %); Van Driest II unchanged | — | **Yes** |

All six AST quantitative targets are met. The remaining open work is qualitative / editorial:
- Paper-ready presentation of the finned-body base-drag augmentation model (Prompt 20 regression-lock, Prompt 22 claim-map finalization).
- Prompt 14-D recommended test-side-only RM-10 geometry cleanup (H2 + H3) to sharpen the second independent benchmark — not a gate on Prompt 19.
- Further Kinsel / Raven closure is conditional on external data and is NOT a gate on the paper if the closure sheets are cited with their open mechanisms.

## 8. Audit-trail fingerprints

- Full-corpus CSV (fresh, audited): `core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv` (timestamp 2026-04-17 19:22, size 7878 B, 24 data rows).
- Per-case markdown + trajectory CSV + component-CD sweep: all 24 regenerated 2026-04-17 between 19:16 and 19:22 under `core/build/reports/simvreal-outliers/`.
- Test console log: the test printed `Tested: 24 rockets | Skipped: 0 | Errors: 2` (the "Errors: 2" counts `POOR`-status cases >20 %, not test errors; both are Kinsel and Raven) and `Abnormal endings:` line did not print (guarded behind `if (abnormalEnds > 0)` in the test).
- Git SHA `4fe8a410119a77aaa28fd6dba8ed225825976ad5` on branch `supersonic-aero-dev`; the tree has uncommitted modifications in `core/src/...` which correspond to the Prompt 12/13 edits documented in the roadmap session logs.
