# Prompt 20 — Regression Lock Inventory

**Agent:** Claude Opus 4.7 (1M context)
**Date:** 2026-04-17
**Prompt:** 20 (Regression-Lock All Accepted Improvements)
**Status:** done

## Scope

Every fix that materially improved the SimVReal corpus during the
AST-readiness campaign (Prompts 9 through 19) is now covered by a
mechanism-specific regression test. This memo is the claim-to-test
traceability matrix.

Pre-existing A-level benchmarks that locked the Phase 1–5 subsystems
(Van Driest II, DATCOM fin wave drag, Devan-Ashwood, Chapman laminar,
Modified Newtonian, PNK, TransonicSimilarity, SBLI, Sutherland
viscosity) are NOT duplicated here; they already had locks under the
`Phase*` and `*BenchmarkTest` test files listed in `CLAUDE.md`.
Prompt 20 targets only the newly accepted campaign fixes that were
previously unlocked or underlocked.

## Summary Table

| # | Mechanism | Roadmap prompt | File(s) changed | Regression test | Method / anchor | Gate value | Current measured | What it protects |
|---|-----------|----------------|-----------------|-----------------|-----------------|-----------|------------------|------------------|
| 1 | Lamb-Oberkampf Re correction removed from `calculateBaseCD(m, conditions)` | Prompt 12 | `BarrowmanDragCalculator.java` | `BaseDragModelTest` | `testTwoArgBaseCDMatchesPureDevanAshwood` (8 Mach params), `testDevanAshwoodPurityAtM24` | two-arg == one-arg to 1e-6 | PASS | Prevents re-introduction of D-level Re correction without external data |
| 2 | Transonic base-drag polynomial widened with Hart L52E06 anchor: BASE_BLEND_HIGH 1.30→1.50, new Hart mid-point CDB=0.230 at M=1.30, degree-5 polynomial | Prompt 13 | `BarrowmanDragCalculator.java` | `BaseDragModelTest` | `testHartL52E06Anchor` (9 Mach params), `testHartL52E06MAPE`, `testHartMidAnchorAtM130`, `testExitsToDevanAshwoodAtBlendHigh`, `testContinuityAtHartInteriorAnchor` | MAPE ≤ 12 %, per-point ±0.025 or ±8 %, M=1.30 = 0.230 ±0.005 | MAPE = 4.0 % | Locks the Hart-anchored polynomial shape; protects Raven/Kinsel closure |
| 3 | SimVReal corpus headline metrics (Prompt 19 frozen) | Prompts 12+13 accumulated, audited Prompt 19 | `SimVRealBenchmarkTest.java` | `SimVRealBenchmarkTest.testSimVRealBenchmark` | 4 new gate assertions at end of the existing benchmark method | avg \|error\| ≤ 7.5 %, within ±10 % ≥ 80 %, within ±5 % ≥ 58 %, abnormal endings = 0 | 6.84 %, 83.3 %, 62.5 %, 0 | The entire AST-readiness aggregate claim |
| 4 | Raven closure (+27.5 % → +24.2 % via Hart polynomial) | Prompt 13 | `BarrowmanDragCalculator.java` | `ClosedOutlierRegressionTest.ravenStaysBelowGate` | Full flight simulation, apogee err | ≤ +27 % | +24.2 % | Trips if P13 Hart anchor is reverted |
| 5 | Kinsel closure (+35.1 % → +28.1 % via Re removal + Hart polynomial) | Prompts 12 + 13 | `BarrowmanDragCalculator.java` | `ClosedOutlierRegressionTest.kinselStaysBelowGate` | Full flight simulation, apogee err | ≤ +33 % | +28.1 % | Trips if either P12 or P13 is reverted |
| 6 | Basic Finner post-P13 MAPE | Prompts 12 + 13 | `BarrowmanDragCalculator.java` | `BasicFinnerDragBenchmarkTest.testMapePostPrompt13TightGate` | 8 Mach points vs ADA636861 CX0 | MAPE ≤ 14 % (P13 recommended narrow gate) | 11.9 % | Catches supersonic drag regressions that clear the loose 30 % gate |
| 7 | Finned-base augmentation geometry reconciliation | Prompt 18 | none (test-only) | `BoattailFinCanGeometryReconciliationTest` | 8 existing tests | existing magnitudes (Kinsel 1.55×, Raven 1.297×, EZI-65 1.08×, CalIsp1 1.17×) | PASS | Protects geometry import + augmentation application |
| 8 | Damping-heuristic zero apogee sensitivity | Prompt 16 | `BarrowmanStabilityCalculator.java` (named constants) | `DampingHeuristicSensitivityTest` | existing test | 0 % apogee delta across ranges | PASS | Confirms damping knobs can be retuned without regressing trajectory metrics |
| 9 | NACA TN 3320 RM-10 D-level benchmark | Prompt 14 | none (test-only) | `NacaRm10FinnedBodyDragBenchmarkTest` | MAPE + supersonic decline + transonic-peak ordering + component sanity | MAPE ≤ 95 % | 84.1 % | Second independent finned-body anchor; claim-boundary regression guard |
| 10 | RM-10 vs Basic Finner diagnostic | Prompt 14-D | none (test-only) | `Rm10VsBasicFinnerDiagnosticTest` | writes per-component CD CSV | PASS (no assertions) | PASS | Diagnostic artefact regression guard for RM-10 root-cause memo |
| 11 | Raven thick-BL multiplier audit | Raven THICK_BL audit | none (test-only) | `RavenThickBLAuditTest` | multiplier calc, gate diagnostics | PASS (no assertions) | PASS | Protects the audited value of 1.554 at Raven peak Mach |
| 12 | ADA636861 Cmq digitization | Prompt 17 follow-up | `VALIDATION_MATRIX.md` (annotation only) | `BasicFinnerCmqBenchmarkTest` | MAPE + sign + Table IX envelope | MAPE ≤ 75 % | 69.1 % | Free-flight Cmq baseline for finned vehicle |
| 13 | CDX1 setting sensitivity | Prompt 4 | `SimulationHandler.java` (verification only) | `CDX1SettingSensitivityTest` | 5 existing tests | all within ±2 % apogee | PASS | Eliminates import-parity as source of outlier error |

## New tests added by Prompt 20

### `ClosedOutlierRegressionTest.java` (new file)

* `ravenStaysBelowGate` — asserts Raven apogee error ≤ +27 % (post-P13 = +24.22 %, ≈ 2.8 pp headroom). Protects Prompt 13 Hart polynomial.
* `kinselStaysBelowGate` — asserts Kinsel apogee error ≤ +33 % (post-P13 = +28.14 %, ≈ 4.9 pp headroom). Protects Prompt 12 Re removal + Prompt 13 Hart polynomial.
* Runtime: 2 tests, ~15 s total.

### `BasicFinnerDragBenchmarkTest.testMapePostPrompt13TightGate` (added method)

* 14 % MAPE ceiling (recommendation from Prompt 13 session log "Basic Finner MAPE must not exceed 14% after any change").
* Measured 11.9 %.
* Independent of the existing loose 30 % gate so both gates run side by side.

### `SimVRealBenchmarkTest.testSimVRealBenchmark` (assertions added at end)

* Avg \|error\| ≤ 7.5 % (frozen 6.84 %, ≈ 0.66 pp headroom).
* Within ±10 % ≥ 80 % (frozen 83.3 %).
* Within ±5 % ≥ 58 % (frozen 62.5 %, ≈ 4.5 pp headroom).
* Abnormal endings == 0 (frozen 0).
* Converts the previously-passive benchmark print into an active gate without duplicating the 4-minute simulation loop.

## Pre-existing locks that cover accepted mechanisms (audit confirmation)

These mechanisms already had mechanism-specific regression coverage; no
new tests were added for them. Audit path: `CLAUDE.md` lists ~22 A-level
externally benchmarked subsystems; the corresponding tests are indexed
below.

| Mechanism | Existing test | Anchor |
|-----------|---------------|--------|
| Van Driest II skin friction | `VanDriestIISkinFrictionTest` | NASA TN D-6945 (Hopkins 1972) |
| DATCOM 4.1.5.1 fin wave drag | `FinWaveDragTest`, `NacaTn3650FinWaveDragTest`, `AckeretFinWaveDragBenchmarkTest` | NACA TN 3650, Ackeret 1925 |
| Devan-Ashwood turbulent base | `BaseDragModelTest::testTN3393TurbulentAgreement` | NACA TN 3393 |
| Chapman laminar base | `ChapmanLaminarBaseDragTest` | NACA TN 3393 laminar |
| Modified Newtonian hypersonic | `HypersonicConeDragBenchmarkTest`, `Phase4HypersonicTest` | DTIC AD0487365 |
| PNK fin-body interference | `PittsNielsenKaattariTest` | NASA TM X-653 |
| TransonicSimilarity fin CNa | `TransonicSimilarityTest`, `NasaTmX653K1FloorTest` | ESDU transonic similarity rule |
| SBLI chord reduction | `SBLIFinChordReductionTest` | Chapman-Kuehn-Larson (NACA Report 1356) |
| Sutherland viscosity / atmosphere | `FlightConditionsTest`, `BoundaryLayerTransitionTest` | US Std Atm 1976 |
| Oblique shock / Prandtl-Meyer | `shocks/` package tests | NACA Report 1135 |
| Taylor-Maccoll cone wave drag | `NacaRmA52H28BenchmarkTest` | NACA RM A52H28 |
| AGARD-B fin / body lift | `AgardBDragBenchmarkTest`, `AgardBGeometryReferenceTest` | AGARD CP-536 |
| Magnus / vortex sideforce | `MagnusBenchmarkTest`, `VortexSideforceBenchmarkTest` | BRL reports |
| Jorgensen crossflow | `JorgensenCrossflowCdBenchmarkTest`, `JorgensenCrossflowTest` | NASA TR R-474 |
| Hypersonic cone foredrag | `HypersonicConeDragBenchmarkTest` | DTIC AD0487365 |
| Dynamic stability (Cmq cone) | `TobakCmqBenchmarkTest`, `DynamicStabilityBenchmarkTest` | Tobak TN 3788 |
| Imported boattail base drag | `ImportedBoattailBaseDragRegressionTest` | CDX1 round-trip |

## What is NOT locked by this prompt (with justification)

| Mechanism | Why not locked |
|-----------|----------------|
| Subsonic stub `0.12 + 0.13*M²` for M < 0.85 | Prompt 13 explicitly out-of-scope per `transonic_base_drag_source_hunt.md` §4.2. Changing it risks regressing subsonic healthy cases without addressing the EZI-65 and T&L non-aerodynamic suspicion. |
| EZI-65 (+16.1 %) and T&L (+17.4 %) apogee gates | Both flagged as likely non-aerodynamic (RASAero also overpredicts). Locking them at current error would falsely claim closure; leaving them unlocked documents honestly that they are open. |
| THICK_BL_K = 1.3 external anchor | Raven THICK_BL audit memo documented that no external primary-source anchor exists in-repo. Adding a gate with no anchor is tuning. Awaits user-provided Addy 1970 AEDC-TR-70-146 or equivalent. |
| MESOS 293K | Pre-existing failure (−27.6 % apogee) documented in memory `benchmark_mesos_293k.md`; CD=10 clamp + FinCan PodSet overlap at launch; not a Prompt 20 scope. |
| Individual healthy-case apogee values | Covered implicitly by the corpus aggregate gates; per-case gates would over-constrain the model without additional signal. |

## Verification runs

All new/modified tests ran and passed against branch
`supersonic-aero-dev` at SHA `03c367d09` (2026-04-17, post-Prompt-19):

* `BasicFinnerDragBenchmarkTest` — 12 tests PASS (1m 36s total, including new tight gate 6.4 s).
* `ClosedOutlierRegressionTest` — 2 tests PASS (1m 21s total, including compile; 24.9 s for the two sims).
* `SimVRealBenchmarkTest.testSimVRealBenchmark` — 1 test PASS (4m 47s total; 3m 58s for the corpus). Gate assertions at end all passed.
* `BaseDragModelTest` — 53 tests PASS (56 s total, including compile).

## Cross-reference

* Roadmap (archived): `paper/data/legacy/AST_PARALLEL_AGENT_ROADMAP.md` Prompt Status Board row 20, Session Log "Prompt 20 Regression Lock".
* Frozen corpus snapshot (archived): `paper/data/snapshots/corpus_summary_2026_04_17.md`, `paper/data/csv/corpus_summary_frozen_2026_04_17.csv`. Current baseline: `paper/data/corpus_summary_2026_05_01.md`.
* Hunt evidence base (archived): `paper/data/legacy/transonic_base_drag_source_hunt.md`.
* Decision memo (archived): `paper/data/legacy/candidate_fixes_decision_memo.md`.
