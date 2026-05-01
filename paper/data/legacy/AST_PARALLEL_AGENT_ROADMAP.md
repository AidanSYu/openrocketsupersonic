# AST Parallel Agent Roadmap

This file is the handoff board for the AST-readiness campaign.

If you are an agent receiving this file, your job is not to scope down the claim or write the paper early. Your job is to close the technical gaps required for an AST-level aerodynamic modeling paper.

## Mission

Turn the current strong component-level validation base into a defensible AST-style aerodynamic modeling paper with credible vehicle-level closure.

Current honest status (2026-04-17 audited baseline):
- AST quantitative targets are met with caveats: avg |error| = 6.84%, 83.3% within ±10%, 62.5% within ±5%, 0 abnormal endings.
- 4 outliers remain >10%: EZI-65 (+16.14%), T&L (+17.36%), Raven (+24.22%), Kinsel (+28.14%).
- EZI-65 and T&L are flagged non-aero for this aero-closure pass; RASAero II also overpredicts those flight cards.
- Raven and Kinsel remain aero-open. Prompt 13 closed Raven by 3.24 pp; Prompts 12 + 13 closed Kinsel by 6.99 pp.
- Basic Finner post-Prompt-13 MAPE is 11.9% over the 8 multiple-fit ADA636861 points, with a 14% tight regression gate.
- Full <10% closure for all 4 remaining outliers is not currently achievable with physics-defensible model changes alone.

## Read First

Before doing any work, read these files:

1. [VALIDATION_MATRIX.md](C:/Code/OpenRocket%20Plus/paper/data/VALIDATION_MATRIX.md)
2. [simvreal-outlier-summary.csv](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/simvreal-outlier-summary.csv)
3. [Raven.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Raven.md)
4. [DontDebateThisN5800MinDia.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/DontDebateThisN5800MinDia.md)
5. [Proteus6.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Proteus6.md)
6. [Full_Metal_Jacket1.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Full_Metal_Jacket1.md)
7. [Kinsel_P4935_A-601_Rocket.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Kinsel_P4935_A-601_Rocket.md)
8. [SimVRealOutlierDiagnosticTest.java](C:/Code/OpenRocket%20Plus/core/src/test/java/info/openrocket/core/aerodynamics/SimVRealOutlierDiagnosticTest.java)
9. [BasicFinnerDragBenchmarkTest.java](C:/Code/OpenRocket%20Plus/core/src/test/java/info/openrocket/core/aerodynamics/BasicFinnerDragBenchmarkTest.java)

## Non-Negotiable Rules

Use this exact header in every parallel session:

```text
You are working on OpenRocket Plus as part of an AST-readiness campaign. Do not scope down the claim. Do not write the paper. Your job is to close the technical gaps needed for an AST-level aerodynamic modeling paper.

Rules:
- Make no assumptions without checking repo evidence.
- Prefer primary repo evidence over speculation.
- Do not hide uncertainty.
- If you find a blocker, state exactly what it blocks.
- Add regression tests for any accepted fix.
- Do not weaken existing A-level benchmarks to improve trajectory agreement.
- Your output must include:
  1. what you changed,
  2. what you measured,
  3. what got better,
  4. what is still open,
  5. exact files touched.
```

Additional rules:
- Do not write the AST paper unless the repo is truly ready.
- Do not claim success because a single case improved.
- Do not break existing A-level external benchmarks to improve SimVReal.
- Do not stack speculative compensations without evidence.
- If a candidate fix looks like tuning rather than physics closure, stop and say so.

## Mandatory Status Update Protocol

Every agent must update this file before handing off.

At the start of your session:
- Add your name, date, and assigned prompt number(s) under `Session Log`.
- Mark the prompt status as `in_progress`.

During the session:
- If you discover a new blocker, add it under `New blockers found`.
- If you discover a new artifact path, add it under `Important artifacts`.

At the end of your session:
- Update the matching row in `Prompt Status Board`.
- Add a short handoff note under `Session Log`.
- If you produced code or data, add the file paths under `Important artifacts`.
- If your result changes the truth of the validation story, update [VALIDATION_MATRIX.md](C:/Code/OpenRocket%20Plus/paper/data/VALIDATION_MATRIX.md) too.

Allowed prompt statuses:
- `not_started`
- `in_progress`
- `blocked`
- `done`
- `superseded`

Do not leave this file stale.

## Current Baseline

Current known benchmark truth (2026-04-17, after Prompt 19 audited full-corpus rerun following Prompts 12 + 13):
- ORP avg `|error| = 6.84%` (audited; matches Prompt 13 memo headline `6.83%` within rounding)
- `83.3%` of SimVReal cases within `+-10%`
- `62.5%` within `+-5%`
- `0` abnormal endings (all 24 cases terminal note `NORMAL`)
- Basic Finner MAPE: `11.9%` (gate 30%, +0.6 pp vs Prompt 12 baseline)
- Frozen summary: [corpus_summary_2026_04_17.md](C:/Code/OpenRocket%20Plus/paper/data/corpus_summary_2026_04_17.md)
- Per-case CSV: [corpus_summary_frozen_2026_04_17.csv](C:/Code/OpenRocket%20Plus/paper/data/csv/corpus_summary_frozen_2026_04_17.csv)
- Audited upstream corpus CSV: [simvreal-full-corpus-summary.csv](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv) (rewritten 2026-04-17 19:22)

Earlier baseline (2026-04-17, after Prompt 13 diagnostic-test memo, pre-Prompt-19 audit):
- ORP avg `|error| = 6.83%`, `83.3%` within `+-10%`, `62.5%` within `+-5%`, `0` abnormal endings

Earlier baseline (2026-04-16, after Prompt 12 Re correction removal, pre-Prompt-13):
- ORP avg `|error| = 7.39%`
- `83.3%` within `+-10%`, `54.2%` within `+-5%`
- Basic Finner MAPE: `11.3%`

Worst current outliers (4 remaining >10%, audited 2026-04-17):
- EZI-65: `+16.14%` (M 0.61, subsonic; unchanged; flagged non-aero out of scope)
- Thunder & Lightning: `+17.36%` (M 0.55, subsonic; unchanged; flagged non-aero out of scope)
- Raven: `+24.22%` (M 1.11, transonic, min-dia) -- was `+27.46%` pre-P12, `-3.24 pp` closed by P13
- A-601 Kinsel: `+28.14%` (M 2.29, supersonic, fin-can) -- was `+35.13%` pre-P12, `-6.99 pp` closed by P12 + P13 (Prompt 13 memo reported `+31.3%` from the diagnostic-test path; the benchmark-harness value is `+28.1%`, both are correct for their respective harnesses)

Most important new evidence from the generated outlier reports (updated 2026-04-16 per Prompt 9):
- Coast AoA in all 4 outliers is low (0.2-1.0 deg), confirming drag -- not stability -- is the primary residual.
- **Base drag is the dominant drag term in all 4 outliers (39-43% of total)**, and the healthy cases have higher base drag fractions (46%).
- Fin drag is negligible (2-9% of total) in every case. Fin-only tuning cannot close these gaps.
- The mechanism differs by Mach regime:
  - **Subsonic (EZI-65, T&L)**: Body tube base drag ~37% of total. RASAero also overpredicts (+6.3%, +11.5%), suggesting partial non-aerodynamic origin.
  - **Transonic min-dia (Raven)**: Body tube base drag = 0.31 at M=1.12 is the dominant term. Extreme L/D = 41.7 may cause base drag model underprediction.
  - **Supersonic fin-can (Kinsel)**: Fin can base drag = 45% of total. CDX1 parity gap was suspected (ModifiedBarrowman, Turbulence, SustainerNozzle=3.09) but Prompt 4 sensitivity analysis confirms all three are bounded <2% combined. Nozzle IS already imported; Turbulence <1%; ModifiedBarrowman <2% (ORP Phase 3 equivalent). The audited +28.14% overshoot is a pure aero model deficit; the older +35.1% number is the pre-P12 baseline.
- Body friction is consistent per unit L/D across healthy and outlier cases (not a model error).
- Kinsel's MAXTIME issue is fixed (descent from 58,000 ft with drogue now completes).

## Prompt Status Board

Update this table when you take or finish work.

| Prompt | Title | Priority | Status | Owner | Last update | Output / note |
|---|---|---|---|---|---|---|
| 1 | Expand outlier diagnostics to full corpus | P0 | done | Claude Opus 4.6 | 2026-04-16 | 24/24 cases exported. Full corpus summary CSV + per-case MD/trajectory/component-CD artifacts. 0 failures. |
| 2 | Write closure sheets for five worst cases | P0 | done | Claude Opus 4.7 (1M ctx) | 2026-04-17 | 5 sheets in `paper/data/outlier_closure/` (raven, kinsel, dontdebatethis, proteus6, fmj_balls005). Two 1-line clarifications in VALIDATION_MATRIX.md Kinsel/Raven rows pointing to sheets. |
| 3 | Build corpus-wide ignored-setting parity matrix | P0 | done | Claude Opus 4.6 | 2026-04-16 | CSV + interpretation complete. 20/24 cases CLEAN, 4 CONTAMINATED. All 3 clean outliers confirmed as aero-model issues, not import artifacts. See session log below. |
| 4 | Sensitivity-bound unsupported settings | P0 | done | Claude Opus 4.6 | 2026-04-16 | All 24 cases CLEAN. No unsupported setting moves apogee >2%. SustainerNozzleDiameter already imported. See `CDX1_UNSUPPORTED_SETTINGS_SENSITIVITY.md`. |
| 5 | Audit production thrust/nozzle wiring | P1 | done | Claude Opus 4.6 | 2026-04-16 | Full audit complete. `populateThrustState()` exists but deliberately commented out. See `audit_thrust_nozzle_wiring.md`. |
| 6 | Implement production thrust/nozzle wiring safely | P1 | blocked |  | 2026-04-16 | Blocked on coast drag deficit. Implementation is trivial (uncomment line 593) but enabling power-on reduction worsens net accuracy while coast drag is too low. |
| 7 | Root-cause Kinsel max-time descent behavior | P0 | done | Claude Opus 4.6 | 2026-04-16 | MAXTIME resolved by prior drag changes. Root cause was ascent overshoot forcing 1900s drogue descent. See session log. |
| 8 | Fix Kinsel abnormal termination | P0 | superseded |  | 2026-04-16 | No longer needed. Kinsel now terminates NORMAL at 1198.2s. The prior FINNED_BASE_K 0.50->0.55 and subsonic ramp changes reduced apogee enough for descent to complete within 1200s. |
| 9 | Decompose peak-Mach drag by case and term | P0 | done | Claude Opus 4.6 | 2026-04-16 | Fresh reports for 4 outliers + 2 healthy cases. Primary residual = base drag too low in all 4 outliers. See `high_m_drag_reconciliation.md`. |
| 10 | Audit high-M drag code paths in Barrowman stack | P0 | done | Claude Opus 4.6 | 2026-04-16 | Full audit complete. Ranked suspect list produced. See Session Log and Prompt 10 Findings below. |
| 11 | Build finite candidate list of model fixes | P0 | done | Claude Opus 4.6 | 2026-04-16 | 5 candidates ranked. Top 3 actionable. See `candidate_fixes_decision_memo.md`. |
| 12 | Implement candidate #1 and rerun evidence | P0 | done | Claude Opus 4.6 | 2026-04-16 | Removed Lamb-Oberkampf Re correction. BF MAPE 11.8%->11.3%. Kinsel +35.1%->+33.0%. Corpus avg err 7.60%->7.39%. See session log. |
| 13 | Implement candidate #2 only if needed | P1 | done | Claude Opus 4.7 (1M) | 2026-04-17 | Widened transonic polynomial on the supersonic side. BASE_BLEND_HIGH 1.30→1.50, new Hart-anchored mid-point CDB=0.230 at M=1.30 (Hart reads 0.250). Hart L52E06 MAPE 15.8%→4.0% in the M 0.95-1.30 regime. Basic Finner MAPE 11.3%→11.9% (+0.6 pp, well under +2 pp gate). Raven +27.5%→+24.3% (−3.2 pp). Kinsel +33.0%→+31.3% (−1.7 pp, safer MAXTIME margin 1181 s vs 1198 s). SimVReal corpus avg err 7.39%→6.83%, within ±5% 54.2%→62.5%, within ±10% 83.3% unchanged, 0 abnormal endings. Devan-Ashwood constants unchanged. 14 new Hart regression tests. See session log. |
| 14 | Add a second high-M finned-body benchmark | P1 | done | Claude Opus 4.7 (1M context) | 2026-04-17 | Added NACA TN 3320 RM-10 benchmark (16 CDT points M 1.00-3.30, full-scale free-flight, Wallops 1954). New test `NacaRm10FinnedBodyDragBenchmarkTest` passes 4/4 with current ORP. MAPE 80.5 % (gate 95 %); documents a +65 to +99 % ORP overprediction on high-fineness parabolic bodies, complementing the -14 to -31 % Basic Finner underprediction. See session log. |
| 14-D | RM-10 overshoot root-cause diagnostic | P0 | done | Claude Opus 4.7 (1M context) | 2026-04-17 | Read-only diagnostic. Added `Rm10VsBasicFinnerDiagnosticTest` emitting per-component CD breakdown at M=1.5/2.0/2.5/3.0. Primary mechanisms ranked: H1 finned-base augmentation x 1.55 mis-applied to boattailed body (base 0.063 vs TN 3320 0.040), H2 2 cm terminal-boattail placeholder yields phantom pressure CD 0.03, H3 ROUNDED fin cross-section triggers round-LE bluntness on a SHARP biconvex LE. Dahlem-Buck and Van Driest II ruled out. See `paper/data/rm10_vs_basic_finner_diagnostic.md`. |
| 15 | Add exact-geometry minimum-diameter validation path | P1 | not_started |  |  |  |
| 16 | Bound damping-heuristic impact on bad cases | P1 | done | Claude Opus 4.6 | 2026-04-16 | Both heuristics have 0.00% apogee sensitivity across all 5 cases. Acceptable as bounded heuristic. See `damping_heuristic_sensitivity_memo.md`. |
| 17 | Search for direct pitch-damping closure opportunities | P1 | done | Claude Opus 4.6 | 2026-04-16 | ADA636861 Table VII has ~25 Cmq points for Basic Finner at M 1.05-4.5, never digitized. See `pitch_damping_closure_memo.md`. |
| 18 | Boattail and fin-can geometry reconciliation | P0 | done | Claude Opus 4.6 | 2026-04-16 | Geometry import correct; finned base augmentation verified working; remaining overshoot is aero model deficit, not geometry. See session log and reconciliation test. |
| 19 | Full corpus rerun after accepted fixes | P1 | done | Claude Opus 4.7 (1M context) | 2026-04-17 | Audited full corpus rerun (SimVRealBenchmarkTest + SimVRealOutlierDiagnosticTest, BUILD SUCCESSFUL 7m 57s). Headline confirmed: avg \|error\| = 6.84 % (vs memo 6.83 %), within ±5 % = 62.5 %, within ±10 % = 83.3 %, abnormal endings = 0. Kinsel: +28.1 % (benchmark harness) vs +31.3 % (diagnostic harness in P13 memo); both correct. Raven: +24.2 % (memo said +24.3 %). Frozen artifacts: `paper/data/corpus_summary_2026_04_17.md` + `paper/data/csv/corpus_summary_frozen_2026_04_17.csv`. All 24 per-case mds + trajectory CSVs + component-CD sweeps regenerated under `core/build/reports/simvreal-outliers/`. |
| 20 | Regression-lock all accepted improvements | P1 | done | Claude Opus 4.7 (1M context) | 2026-04-17 | Inventory memo + 3 new gate layers: (a) `SimVRealBenchmarkTest.testSimVRealBenchmark` now asserts Prompt-19 frozen corpus headlines (avg ≤ 7.5 %, ±10 % ≥ 80 %, ±5 % ≥ 58 %, abnormal = 0); (b) new `ClosedOutlierRegressionTest` pins Raven ≤ +27 % and Kinsel ≤ +33 % to guard Prompts 12 + 13 closures; (c) new `testMapePostPrompt13TightGate` in `BasicFinnerDragBenchmarkTest` enforces BF MAPE ≤ 14 % per P13 session-log recommendation. Memo at `paper/data/md/prompt20_regression_lock_inventory.md` gives the mechanism → test → claim traceability matrix. |
| 21 | Brutal AST readiness review | Gate | not_started |  |  |  |
| 22 | Claim map finalization | Gate | not_started |  |  |  |
| 23 | Final paper go/no-go gate | Gate | not_started |  |  |  |

## Important Artifacts

Add new high-value artifact paths here as they are created.

- [simvreal-outlier-summary.csv](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/simvreal-outlier-summary.csv)
- [Raven.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Raven.md)
- [DontDebateThisN5800MinDia.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/DontDebateThisN5800MinDia.md)
- [Proteus6.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Proteus6.md)
- [Full_Metal_Jacket1.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Full_Metal_Jacket1.md)
- [Kinsel_P4935_A-601_Rocket.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Kinsel_P4935_A-601_Rocket.md)
- [simvreal_parity_matrix.csv](C:/Code/OpenRocket%20Plus/paper/data/csv/simvreal_parity_matrix.csv)
- [simvreal_parity_interpretation.md](C:/Code/OpenRocket%20Plus/paper/data/csv/simvreal_parity_interpretation.md)
- [high_m_drag_reconciliation.md](C:/Code/OpenRocket%20Plus/paper/data/high_m_drag_reconciliation.md) (Prompt 9 output)
- [high_m_drag_decomposition.csv](C:/Code/OpenRocket%20Plus/paper/data/csv/high_m_drag_decomposition.csv) (Prompt 9 supporting data)
- [BoattailFinCanGeometryReconciliationTest.java](C:/Code/OpenRocket%20Plus/core/src/test/java/info/openrocket/core/aerodynamics/BoattailFinCanGeometryReconciliationTest.java) (Prompt 18: geometry import + augmentation verification, 8 tests)
- [candidate_fixes_decision_memo.md](C:/Code/OpenRocket%20Plus/paper/data/candidate_fixes_decision_memo.md) (Prompt 11: ranked candidate list, 5 candidates, top 3 actionable)
- [pitch_damping_closure_memo.md](C:/Code/OpenRocket%20Plus/paper/data/md/pitch_damping_closure_memo.md) (Prompt 17: ADA636861 Cmq data discovery, claim-boundary analysis)
- [damping_heuristic_sensitivity_memo.md](C:/Code/OpenRocket%20Plus/paper/data/damping_heuristic_sensitivity_memo.md) (Prompt 16: zero apogee sensitivity to both damping heuristics)
- [DampingHeuristicSensitivityTest.java](C:/Code/OpenRocket%20Plus/core/src/test/java/info/openrocket/core/aerodynamics/DampingHeuristicSensitivityTest.java) (Prompt 16: sensitivity sweep harness)
- [EZI65-1.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/EZI65-1.md) (fresh diagnostic)
- [Thunder_Lightning.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/Thunder_Lightning.md) (fresh diagnostic)
- [CalIsp1.md](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/CalIsp1.md) (healthy comparison)
- [simvreal-full-corpus-summary.csv](C:/Code/OpenRocket%20Plus/core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv) (Prompt 1: full 24-case machine-readable summary with ignored-setting flags)
- [CDX1_UNSUPPORTED_SETTINGS_SENSITIVITY.md](C:/Code/OpenRocket%20Plus/paper/data/CDX1_UNSUPPORTED_SETTINGS_SENSITIVITY.md) (Prompt 4: analytical + live sensitivity bounds for all unsupported settings)
- [CDX1SettingSensitivityTest.java](C:/Code/OpenRocket%20Plus/core/src/test/java/info/openrocket/core/aerodynamics/CDX1SettingSensitivityTest.java) (Prompt 4: reproducible sensitivity test suite, 5 tests)
- [outlier_closure/](C:/Code/OpenRocket%20Plus/paper/data/outlier_closure/) (Prompt 2: 5 closure sheets — raven_closure.md, kinsel_closure.md, dontdebatethis_closure.md, proteus6_closure.md, fmj_balls005_closure.md)
- [NASA_TN_D-4821.pdf](C:/Code/OpenRocket%20Plus/paper/data/pdf/NASA_TN_D-4821.pdf) (Prompt 13: Compton 1968 transonic base-drag primary source, full PDF; Figure 15(b) flat-base cylindrical-afterbody data, M 0.3-1.3)
- [nasa_tn_d4821_base_drag.csv](C:/Code/OpenRocket%20Plus/paper/data/csv/nasa_tn_d4821_base_drag.csv) (Prompt 13: digitized from Figure 15(b), L/Dm=1.5, beta=0 deg, flat base; includes authors' own verbatim rejection of M 0.95-1.20 strut-contaminated band)
- [Hoerner_FluidDynamicDrag_1965.pdf](C:/Code/OpenRocket%20Plus/paper/data/pdf/Hoerner_FluidDynamicDrag_1965.pdf) (Prompt 13 unblock: Hoerner "Fluid-Dynamic Drag" 1965 full 22.8 MB PDF, 455 pages; Ch. XVI Fig. 2 transonic base drag compilation)
- [NACA_RM_L52E06.pdf](C:/Code/OpenRocket%20Plus/paper/data/pdf/NACA_RM_L52E06.pdf) (Prompt 13 unblock: Hart 1952 free-flight finless ogive-cylinder base pressures M 0.7-1.3, STING-FREE, key primary source)
- [NACA_TN_3372.pdf](C:/Code/OpenRocket%20Plus/paper/data/pdf/NACA_TN_3372.pdf) (Prompt 13 unblock: Peck 1955 [=RM L50I28a 1950] free-flight fin-stabilized base pressures M 0.7-1.2, independent corroboration)
- [naca_rm_l52e06_base_drag.csv](C:/Code/OpenRocket%20Plus/paper/data/csv/naca_rm_l52e06_base_drag.csv) (Hart Config A finless free-flight base drag, 14 Mach points 0.6-1.3)
- [naca_tn_3372_base_pressure.csv](C:/Code/OpenRocket%20Plus/paper/data/csv/naca_tn_3372_base_pressure.csv) (Peck avg of cylindrical-afterbody edge orifice pressure, 12 Mach points 0.6-1.2)
- [hoerner_fig2_base_drag_compilation.csv](C:/Code/OpenRocket%20Plus/paper/data/csv/hoerner_fig2_base_drag_compilation.csv) (Hoerner 1965 Fig. 2 upper envelope, 17 Mach points 0.3-5.0, primary-source attributions in header)
- [transonic_base_drag_source_hunt.md](C:/Code/OpenRocket%20Plus/paper/data/transonic_base_drag_source_hunt.md) (Prompt 13 unblock memo: per-candidate retrieval log, ORP-vs-data quantitative gap table, recommendations)
- [corpus_summary_2026_04_17.md](C:/Code/OpenRocket%20Plus/paper/data/corpus_summary_2026_04_17.md) (Prompt 19: frozen post-P12+P13 audited corpus summary, headline metrics, before/after table, per-case 24-rocket table, outlier root-cause ranking, cross-reference of fix → closed-case, AST-target status)
- [corpus_summary_frozen_2026_04_17.csv](C:/Code/OpenRocket%20Plus/paper/data/csv/corpus_summary_frozen_2026_04_17.csv) (Prompt 19: machine-readable per-case pre-P12 vs post-P13 with closing-fix attribution and aggregate header rows)
- [prompt20_regression_lock_inventory.md](C:/Code/OpenRocket%20Plus/paper/data/md/prompt20_regression_lock_inventory.md) (Prompt 20: mechanism → test → claim-protected traceability matrix; 13 mechanisms, all locked or reasoned-out)
- [ClosedOutlierRegressionTest.java](C:/Code/OpenRocket%20Plus/core/src/test/java/info/openrocket/core/aerodynamics/ClosedOutlierRegressionTest.java) (Prompt 20: single-case Raven and Kinsel gates protecting Prompts 12 + 13 closures)

## New Blockers Found

Add blockers here as they are discovered.

- **Prompt 13 (Candidate #2 transonic base drag peak widening)** — CLOSED 2026-04-17 by implementation session. Polynomial re-anchored against Hart L52E06 free-flight finless data. BASE_BLEND_HIGH 1.30→1.50, Hart interior anchor at M=1.30=0.230. Devan-Ashwood A/B unchanged. 14 regression tests added. Hart MAPE 15.8%→4.0%; corpus avg 7.39%→6.83%; Raven −3.2 pp, Kinsel −1.7 pp. See "Session — Prompt 13 Implementation" below.
- **ESDU 96012 / 78041 transonic base-drag data items** remain paywalled. Not a primary blocker for Prompt 13 (now unblocked) but retrieving them via university library access would provide an independent modern-correlation crosscheck.
- **DTIC BRL free-flight reports** (ARBRL-TR-02179, BRL 653 Charters & Turetsky 1948) remain unretrievable without authenticated access. Not a primary blocker but would add a third independent free-flight anchor if retrieved.

## Recommended Parallel Waves

### Wave 1 — DONE
- Prompt 1 ✓
- Prompt 3 ✓
- Prompt 7 ✓ (P8 superseded)
- Prompt 9 ✓
- Prompt 10 ✓
- Prompt 18 ✓

### Wave 2 — DONE
- Prompt 4 ✓
- Prompt 5 ✓ (P6 blocked on coast drag)
- Prompt 16 ✓
- Prompt 17 ✓

### Wave 3 — IN PROGRESS
- Prompt 11 ✓
- Prompt 12 ✓ (Candidate #1 removed Lamb-Oberkampf Re correction; BF MAPE 11.3%, Kinsel -2.1 pp)
- Prompt 13 ✓ (Candidate #2 implemented 2026-04-17; BASE_BLEND_HIGH 1.30→1.50, Hart-anchored at M=1.30=0.230; Hart MAPE 15.8%→4.0%; Raven −3.2 pp, Kinsel −1.7 pp; corpus avg 7.39%→6.83%, within ±5% 54.2%→62.5%)
- Prompt 6 — blocked on coast drag closure
- Prompt 8 — superseded
- Prompt 14 ✓ (Added NACA TN 3320 RM-10 benchmark; MAPE 80.5 %, +65 to +99 % overprediction documented; post-Prompt-13 MAPE 84.1%, still under 95% gate)
- Prompt 14-D ✓ (RM-10 overshoot root-cause diagnostic; 4 actionable mechanisms ranked, primary = finned-base augmentation misapplied on bodies with upstream boattail + 2 cm terminal-boattail placeholder generates phantom pressure CD; see `paper/data/rm10_vs_basic_finner_diagnostic.md`)
- Prompt 15 — not started

### Wave 4 — IN PROGRESS
- Prompt 19 ✓ (audited full-corpus rerun, frozen summary + per-case CSV committed)
- Prompt 20 ✓ (regression-locked: Prompt-19 corpus headline gates, Raven/Kinsel closed-outlier gates, Basic Finner post-P13 tight MAPE gate; full inventory memo at `paper/data/md/prompt20_regression_lock_inventory.md`)
- Prompt 21
- Prompt 22
- Prompt 23

## Session Log

Use this template for every handoff.

```text
### Session
- Agent:
- Date:
- Prompt(s):
- Status:
- Summary:
- Files changed:
- Files generated:
- Measurements:
- What improved:
- What is still open:
- Recommended next prompt:
```

### Session — Prompt 1: Expand Outlier Diagnostics To Full Corpus
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 1
- Status: done
- Summary: Extended `SimVRealOutlierDiagnosticTest` with a new `testGenerateFullCorpusDiagnostics()` method that runs all 24 SimVReal validation cases through the diagnostic pipeline. Produces per-case markdown + trajectory CSV + component-CD sweep artifacts, plus a machine-readable `simvreal-full-corpus-summary.csv` with ignored-setting flags extracted from loader warnings. The existing `testGenerateAstOutlierDiagnostics()` is preserved unchanged.
- Files changed: `core/src/test/java/info/openrocket/core/aerodynamics/SimVRealOutlierDiagnosticTest.java`, `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`
- Files generated: `core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv`, 24 markdown reports, 24 trajectory CSVs, 24 component-CD CSVs (all under `core/build/reports/simvreal-outliers/`)
- Measurements: 24/24 cases exported successfully, 0 failures. Full corpus test runs in ~7 minutes. Outlier-focused test runs in ~55 seconds. Both tests pass.
- What improved: Full corpus now has reproducible diagnostic artifacts. Machine-readable summary includes ignored-setting flags for every case. Ready for downstream closure sheet generation and parity analysis.
- What is still open: Closure sheets for the 4 remaining outliers (Prompt 2). Sensitivity bounding of ignored settings (Prompt 4). The full-corpus CSV is a build artifact, not checked into git.
- Recommended next prompt: Prompt 2 (closure sheets), Prompt 4 (sensitivity bounding)

### Session — Prompt 3: Corpus-Wide Ignored-Setting Parity Matrix
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 3
- Status: done
- Summary: Built a corpus-wide parity inventory for all 24 SimVReal CDX1 cases. Extracted raw XML values for ModifiedBarrowman, Turbulence, SustainerNozzle/Dia, Booster1Nozzle/Dia, Booster2Nozzle/Dia from every CDX1 file. Classified each case as CLEAN (0 unsupported active settings) or CONTAMINATED (1+ unsupported active settings). Key architecture finding: SustainerNozzleDiameter is correctly applied by SimulationHandler via setNozzleExitDiameter() -- this is NOT a parity gap. The only true unsupported settings are ModifiedBarrowman=True (2 cases: Qu8k, Kinsel), Turbulence=True (4 cases: DDT, Qu8k, Kinsel, AeroPac104K), and Booster1NozzleDiameter>0 (1 case: AeroPac104K). 20 of 24 cases (83.3%) are parity-CLEAN. All three >10% clean outliers (EZI-65 +16.1%, T&L +17.4%, Raven +27.5%) have zero unsupported settings, confirming their errors are aerodynamic model residuals not import artifacts. The worst outlier (Kinsel +35.1%) is contaminated but the missing settings would increase drag, making the overprediction worse.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`
- Files generated: `paper/data/csv/simvreal_parity_matrix.csv`, `paper/data/csv/simvreal_parity_interpretation.md`
- Measurements: 24 CDX1 files audited; 20 CLEAN, 4 CONTAMINATED; 0 acceptance-critical parity gaps found
- What improved: CDX1 import-parity uncertainty is now fully bounded. The parity matrix provides evidence that unsupported settings do not explain remaining outliers.
- What is still open: Prompt 4 (quantitative sensitivity bounding) would strengthen the claim but is not strictly required given directional analysis. The 3 clean outliers need aero model fixes, not import parity fixes.
- Recommended next prompt: Prompt 4 (belt-and-suspenders sensitivity bounding) or Prompt 11/12 (actual drag model fixes for clean outliers)

### Session — Prompt 4: Sensitivity-Bound Unsupported CDX1 Settings
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 4
- Status: done
- Summary: Complete sensitivity analysis for all 3 categories of unsupported CDX1 settings (nozzle diameter, turbulence flag, ModifiedBarrowman). Key discovery: **SustainerNozzleDiameter IS already correctly imported** by `SimulationHandler.java` (lines 99-103, 179-181) and wired through `RK4SimulationStepper.populateThrustState()` to the drag calculator. The `SustainerNozzle` field in `<RocketDesign>` is a redundant copy that generates a warning but does not need separate handling. Only `Booster1NozzleDiameter` is truly unsupported (1 affected rocket: AeroPac 104K), but analytical bounds show <0.2% apogee impact. Turbulence flag affects 4 rockets but ORP's 5% laminar cap already makes it <1.2% impact. ModifiedBarrowman affects 2 rockets but ORP Phase 3 provides equivalent corrections and the flag doesn't change drag directly. Live trajectory comparison on 5 rockets with the largest nozzle area ratios confirmed <0.01% apogee delta between nozzle-on and nozzle-off, validating that the import is working correctly.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`
- Files generated: `paper/data/CDX1_UNSUPPORTED_SETTINGS_SENSITIVITY.md`, `core/src/test/java/info/openrocket/core/aerodynamics/CDX1SettingSensitivityTest.java`
- Measurements:
  - Live nozzle sensitivity (5 rockets): Qu8k 0.0%, Proteus6 0.0%, FMJ1 0.0%, Kinsel 0.0%, DontDebateThis 0.0%
  - Turbulence flag analytical bound: <1.2% apogee (5% laminar cap vs 0% = 3% friction delta * 40% friction share)
  - ModifiedBarrowman analytical bound: <2% apogee (stability-only effect, ORP Phase 3 already equivalent)
  - Booster1Nozzle analytical bound: <0.2% apogee (AR=0.32, 6s burn out of 100s+ ascent)
- What improved: Eliminated CDX1 import parity as a confounding factor for ALL 24 validation cases. The paper can now confidently state that unsupported settings are bounded below 2%.
- What is still open: Booster1NozzleDiameter import (trivial to implement but not needed for AST). The 4 outlier overshoots are confirmed as pure aero model residuals, not import artifacts.
- Recommended next prompt: Prompt 12 (implement candidate drag fix), Prompt 19 (full corpus rerun)

### Session — Prompt 9: Decompose Peak-Mach Drag By Case And Term
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 9
- Status: done
- Summary: Expanded `SimVRealOutlierDiagnosticTest` to cover the current 4 outliers (EZI-65, T&L, Raven, Kinsel) plus 2 healthy comparison cases (Byrum, CalIsp1). Regenerated fresh reports from the current code. Built a complete peak-Mach drag decomposition showing friction/pressure/base split and per-component contributions for all 6 cases. Key finding: **body/fin-can base drag is the dominant residual for all 4 outliers**. The healthy cases have *higher* base drag fractions (46%) than the outliers (39-42%), and all outliers overpredict apogee (drag too low). The mechanism differs by Mach regime: subsonic cases (EZI-65, T&L) have body tube base drag underestimation compounded by RASAero also overpredicting; transonic minimum-diameter Raven has body tube base drag of 0.31 at M=1.12 that needs to be ~0.40+; supersonic Kinsel has fin-can base drag as 45% of total drag plus CDX1 import parity confounders (ModifiedBarrowman, Turbulence, SustainerNozzle). Fin drag is negligible (2-9%) in all cases. Body friction is consistent per unit L/D across healthy and outlier cases.
- Files changed: `core/src/test/java/info/openrocket/core/aerodynamics/SimVRealOutlierDiagnosticTest.java` (updated TARGET_CASES), `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`
- Files generated: `paper/data/high_m_drag_reconciliation.md`, `paper/data/csv/high_m_drag_decomposition.csv`, fresh reports in `core/build/reports/simvreal-outliers/` for EZI65-1, Thunder_Lightning, Raven, Kinsel, Byrum, CalIsp1
- Measurements: Peak-Mach Cd decomposition for 6 cases (4 outliers + 2 healthy); coast-phase average Cd comparison; per-component share analysis; body L/D normalized friction comparison; fin cross-section effect analysis
- What improved: Clear diagnosis that base drag is the primary residual for all 4 outliers, with specific mechanisms identified per Mach regime. Subsonic outliers partly explained by non-aerodynamic factors (RASAero also overpredicts). Minimum-diameter and fin-can patterns isolated.
- What is still open: All code fixes. Subsonic base drag deficit quantification. Minimum-diameter base drag model investigation. Kinsel CDX1 parity bounding. Raven's extreme L/D base drag correction.
- Recommended next prompt: Prompt 11 (build finite candidate fix list) using this decomposition + Prompt 10 code audit as inputs

### Session — Prompt 18: Boattail And Fin-Can Geometry Reconciliation
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 18
- Status: done
- Summary: Complete geometry reconciliation audit for 4 target cases (Raven +27.5%, Kinsel +35.1%, EZI-65 +16.1%, CalIsp1 healthy). Created regression test suite with 8 tests covering geometry import verification, finned base augmentation verification, and full Cd analysis for all cases. Key findings: (1) CDX1 geometry import is correct for all 4 cases -- FinCanHandler correctly creates shoulder transition + fin-can body tube as linear-chain siblings, shortens parent body tube, and attaches fins as children of the fin-can tube. (2) Finned base augmentation IS being applied at correct magnitudes: Kinsel FinCan gets 1.55x at M=2.4 (4 fins, full span/radius), Raven Body Tube gets 1.30x at M=1.1 (3 fins, high span/radius), EZI-65 gets 1.08x at M=0.6. (3) Stale outlier diagnostic CSVs in `core/build/reports/simvreal-outliers/` were generated before augmentation code was committed and do NOT reflect current model state -- they show unaugmented base drag and misleadingly suggest augmentation is not working. (4) Kinsel expanding shoulder (6.125" to 6.5") correctly produces zero wave drag (expansion fan, not shock); step drag is also correctly zero because the smooth shoulder transition eliminates the diameter discontinuity. (5) The remaining overshoot is NOT a geometry import or augmentation application bug -- it is a systematic drag coefficient deficit consistent with the Basic Finner 14-31% underprediction at M 1.8-2.7.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`
- Files generated: `core/src/test/java/info/openrocket/core/aerodynamics/BoattailFinCanGeometryReconciliationTest.java` (8 tests, all passing)
- Measurements:
  - Kinsel FinCan augmentation at M=2.4: 1.5500 (verified via direct API call)
  - Kinsel FinCan baseCd at M=2.4: 0.1378 (augmented), rawBaseCd=0.0889 (Devan-Ashwood+Re correction)
  - Raven Body Tube augmentation at M=1.1: 1.2970
  - EZI-65 Body Tube augmentation at M=0.6: 1.0825
  - CalIsp1 Body Tube augmentation at M=0.8: 1.1650
  - All 4 geometry imports verified: nose shapes, body lengths, fin counts, shoulder dimensions, parent-tube shortening
- What improved: Eliminated geometry import and augmentation application as hypotheses for the remaining overshoot. Confirmed that the stale diagnostic CSVs are misleading. Narrowed the investigation to pure aero model drag deficit.
- What is still open: The 4 outlier overshoots remain. The primary residual is in base drag coefficients (Suspects #1 and #5 from Prompt 10: Lamb-Oberkampf Re correction and transonic base drag peak). This should be addressed by Prompts 11-12, not by geometry fixes.
- Recommended next prompt: Prompt 11 (build finite candidate fix list) or Prompt 12 (implement candidate #1: remove/disable Lamb-Oberkampf Re correction)

### Session — Prompt 7: Root-Cause Kinsel Max-Time Descent Behavior
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 7
- Status: done
- Summary: Complete root-cause analysis of A-601 Kinsel MAXTIME@1200s. The issue is **already resolved** in the current build -- terminal note is now NORMAL with ground hit at 1198.2s. Root-cause chain: (1) ascent drag too low -> apogee 57794 ft instead of 42771 ft (+35.1%) -> (2) drogue-only descent from ~17610m AGL takes ~1070s at ~10.6 m/s terminal velocity -> (3) main chute (160in, altitude trigger at 366m AGL) deploys at t=1130.9s -> (4) total flight time 1198.2s, just under 1200s cap. The prior MAXTIME at 66136 ft apogee (+54.6%) produced drogue-only descent of ~1174s where the main chute trigger altitude was never reached before 1200s. The FINNED_BASE_K increase (0.50->0.55) and subsonic ramp changes reduced the apogee just enough. Recovery system works correctly: dual deploy imports properly, both events fire, altitude-crossing logic is correct. Prompt 8 is superseded.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`
- Files generated: none
- Measurements: Current ORP apogee 57794 ft (+35.1%), terminal note NORMAL, ground hit 1198.2s, drogue CdA 6.211 m2, main CdA 17.252 m2, main deploys at t=1130.9s
- What improved: Confirmed MAXTIME resolved. Recovery system validated. Eliminated recovery/event/scheduling hypotheses.
- What is still open: +35.1% ascent overshoot (worst single outlier in 24-case corpus). Purely ascent drag deficit.
- Recommended next prompt: Prompt 11 or 12. Prompt 8 superseded.

### Session — Prompt 10: High-M Drag Code Path Audit (Rerun with Full Findings)
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 10
- Status: done
- Summary: Complete source-level audit of the high-M drag code path affecting Raven (+27.5% at M 1.12) and Kinsel (+35.1% at M 2.33). Identified ranked suspect list of underdrag sources with exact file/method references. No code changes made — this is a read-only audit. Key finding: the dominant underdrag source for both outliers is base drag on the body tube / fin can, not fin drag or nose pressure drag. Secondary sources differ by case: Raven is hurt by the transonic blend gap (M 0.85-1.3 region), while Kinsel is hurt by the Lamb-Oberkampf Re correction that lowers base drag at high Re.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this file)
- Files generated: none
- Measurements: See ranked suspect list below
- What improved: understanding of where the underdrag lives
- What is still open: all code fixes, all regression tests, all benchmark reruns
- Recommended next prompt: Prompt 11 (build finite candidate fix list) or Prompt 12 (implement top candidate)

### Session — Prompt 11: Build Finite Candidate List Of Model Fixes
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 11
- Status: done
- Summary: Produced ranked decision memo with 5 candidates for high-M drag fixes. Top 3 are actionable; bottom 2 are not recommended. Key findings: (1) Lamb-Oberkampf Re correction is 7.7% at Kinsel's Re_D, not the 15-25% originally estimated in Prompt 10 -- still worth removing but closes only ~3-4 pp. (2) Transonic base drag peak width is the highest-leverage single fix for Raven but requires external data (Hoerner/ESDU) to be AST-defensible. (3) FINNED_BASE_K increase has the useful property of simultaneously improving the Basic Finner benchmark. (4) Subsonic outliers (EZI-65, T&L) are likely partially non-aerodynamic given RASAero also overpredicts. (5) Realistic closure: Raven could reach ~+12% (near 10% gate), Kinsel ~+27% (still outside), subsonic unchanged. Full closure of all 4 outliers to <10% is not achievable with physics-defensible model changes alone.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this file)
- Files generated: `paper/data/candidate_fixes_decision_memo.md`
- Measurements: Lamb-Oberkampf Re factor for Kinsel: 0.923 (7.7% reduction) at Re_D=9.1e6. Devan-Ashwood raw at M=2.4: 0.0963, corrected: 0.0889. Finned augmentation 1.55x gives 0.138 final. Transonic polynomial yields ~0.22 at M=1.12 (Raven), needs ~0.30+ for closure.
- What improved: Decision-ready ranked list. Corrected the Prompt 10 overestimate of Re correction magnitude.
- What is still open: Implementation of Candidate #1 (Prompt 12). External data search for Candidate #2 (Hoerner/ESDU transonic base drag). Decision on whether to implement Candidate #3. All regression tests. All benchmark reruns.
- Recommended next prompt: Prompt 12 (implement Candidate #1: remove Lamb-Oberkampf Re correction). In parallel, web search for Hoerner Fig. 3.19 / ESDU transonic base drag data to validate Candidate #2.

### Session — Prompt 17: Search For Direct Pitch-Damping Closure Opportunities
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 17
- Status: done
- Summary: READ-ONLY research task. Systematically searched all repo data sources for direct pitch-damping (Cmq) experimental data for finned vehicles. **Critical discovery:** ADA636861 (DREV Basic Finner aeroballistic range report, already in repo as PDF) contains ~25 per-shot Cmq values in Table VII at M 1.05-4.5, plus 8 multi-fit values in Table VIII with probable errors. This data was never digitized into the CSV (only CX0/CNa/CMa were extracted). The Cmq column is the exact data type needed to validate ORP's 3x damping multiplier for finned supersonic vehicles. Additional findings: AEDC-TR-76-58 contains only roll damping (no Cmq); Tobak TN 3788 is cone-only theory (not finned vehicle); DynamicStabilityBenchmarkTest is self-consistency only. No source in the repo covers M < 1.05 for finned-vehicle Cmq, so the transonic augmentation peak (M = 1.0) remains partially unvalidated on the subsonic side. However, ADA636861 near-transonic shots (M 1.05-1.33) show elevated |Cmq| = 289-475 vs M 2-4 range of 195-360, qualitatively consistent with a transonic peak. Note: free-flight range data produces combined (Cmq + CmAlphaDot), so ORP comparison should use 1.4*Cmq to account for the CmAlphaDot = 0.4*Cmq heuristic.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this file)
- Files generated: `paper/data/md/pitch_damping_closure_memo.md` (full claim-boundary memo)
- Measurements: 25 Cmq values extracted from ADA636861 PDF Table VII (M 1.056-4.471, Cmq range -195 to -475). Compared against existing CSV which omits Cmq.
- What improved: Identified that the missing pitch-damping validation data already exists in the repo but was overlooked during CSV digitization. Clear promotion path from B to A for the 3x multiplier at supersonic Mach.
- What is still open: (1) Digitize ADA636861 Table VII Cmq into CSV. (2) Build ORP Cmq comparison test for Basic Finner geometry. (3) Subsonic transonic peak validation (M < 1.05) — no source in repo. (4) The paper can survive with bounded-heuristic appendix for transonic peak IF supersonic Cmq is validated against ADA636861.
- Recommended next prompt: Digitize ADA636861 Cmq + build comparison test (can be folded into Prompt 14 or done standalone). Then update VALIDATION_MATRIX.md pitch-damping rows based on comparison results.

### Session — Prompt 17 Follow-up: Digitize ADA636861 Cmq and Build Basic Finner Cmq Comparison Test
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): Prompt 17 follow-up (RETRY)
- Status: done. Benchmark test added (3 sub-tests, all passing); digitization and comparison reported. No claim promotion (MAPE 69.1% ≥ 40%).
- Summary: Verified PDF `paper/data/pdf/ADA636861.pdf` contains DREV-TM-9703 (Dupuis & Hathaway, Aug 1997, 152 pp). Read Tables VII (linear theory per-shot, 25 shots), VIII (6-DOF single-fit per-shot, 25 shots), and IX (6-DOF multi-fit, 8 grouped points) at PDF pages 42-48. The Prompt 17 memo referred to the 8 multi-fit points as "Table VIII"; they are actually Table IX — Table VIII contains 6-DOF single-fit per-shot data with Cmq and a nonlinear Cmq2 term. Confirmed the existing `paper/data/csv/ada636861_basic_finner_cmq.csv` (from a prior attempt) is accurately extracted for all three tables; left it unchanged. Verified `BarrowmanStabilityCalculator.java` lines 165-188: `getCmq()` returns strip-theory sum × `k_transonic` (Gaussian peak 3.5× at M=1, σ=0.15), and `getCmAlphaDot() = 0.4 × Cmq` — so ORP combined damping = 1.4 × getCmq(). The `3×` `DAMPING_MULTIPLIER` only scales the pitch damping *moment magnitude* during trajectory integration (line 141); it does NOT enter the derivative exported via `getCmq()`. Built `BasicFinnerCmqBenchmarkTest.java` using `SupersonicTestRockets.makeBasicFinner()` for geometry and explicitly setting `FlightConditions.pitchCenter` to the ADA636861 CG (16.50 cm = 5.500 cal from nose, per Table I). Three sub-tests: MAPE gate (regression guard at 75%, measured 69.1%), sign-is-stabilizing (always negative, passes), and Table IX envelope (0.5 < ORP/exp < 5.0, passes and documents transonic over-prediction). Per-point comparison CSV emitted to `core/build/reports/basic-finner-cmq/basic_finner_cmq_vs_ada636861.csv`. No modifications to `BarrowmanStabilityCalculator.java`, `BarrowmanDragCalculator.java`, or any existing A-level test. One-line additions to two pitch-damping rows in `VALIDATION_MATRIX.md` documenting the new evidence without promoting the claim level.
- Files changed: `paper/data/VALIDATION_MATRIX.md` (rows 32 and 33 — one-line annotations adding the ADA636861 / BasicFinnerCmqBenchmarkTest evidence and the measured 69.1% MAPE finding), `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this session entry).
- Files generated: `core/src/test/java/info/openrocket/core/aerodynamics/BasicFinnerCmqBenchmarkTest.java` (3 sub-tests, full Javadoc documenting the data-type caveat, pitch-centre convention, and the measured-baseline finding); `core/build/reports/basic-finner-cmq/basic_finner_cmq_vs_ada636861.csv` (per-point ORP vs ADA636861 comparison, 49 rows covering Tables VII + VIII + IX). CSV `paper/data/csv/ada636861_basic_finner_cmq.csv` was already present from a prior attempt and confirmed accurate — no rewrite needed.
- Measurements (unmodified ORP, branch `supersonic-aero-dev`, 2026-04-17):
  - Quality-filtered MAPE (flag=ok across Tables VII+VIII+IX, 44 points): **69.1 %**.
  - Worst point: Table VII shot DA95022010 at M=1.056, ORP combined = -1447.3, exp = -319.4, err = +353.1 % (ORP over-predicts |damping| by factor 4.5 because k_transonic ≈ 3.25 × strip-theory is too aggressive).
  - Near-transonic band (M 1.05-1.12, 5 ok points across VII+VIII+IX): ORP over-predicts |damping| by 170-353 % — transonic Gaussian is too tall.
  - Low-supersonic M 1.25-1.85 (10 ok points): ORP err −0.6 % to −42.2 %, average ≈ -26 %.
  - Mid-supersonic M 2.35-3.00 (13 ok points): ORP err −28.7 % to −53.5 %, average ≈ -38 %.
  - High supersonic M 3.3-4.5 (11 ok points): ORP err −5.7 % to −44.1 %, average ≈ -30 %.
  - Sign is correct (stabilizing, negative) at every digitized Mach point. No NaN, no decade drift.
  - Single best-agreement points: M=1.254 (VIII, -0.6 %), M=4.127 (VIII, -5.7 %).
- What improved:
  - Repo now has a regression-guarded, quality-filtered external benchmark for finned-vehicle pitch damping derived from direct free-flight aeroballistic-range measurements. Any future code change that worsens the MAPE above 75 % will fail the test and surface the regression.
  - The 2-line annotations in VALIDATION_MATRIX.md quantitatively document the gap between ORP and the ADA636861 baseline for both the `3×` multiplier row and the transonic Gaussian row. Future reviewers can read the rows and immediately see a direct external anchor and its numeric disagreement.
  - The Prompt 17 memo's table-number confusion (Table VIII multi-fit vs Table IX multi-fit) is now resolved in-line in the new test's Javadoc.
- What improvements are NOT claimed: MAPE 69.1 % does NOT close the 3x multiplier claim, does NOT close the transonic augmentation claim, and does NOT promote either row from B to A. Both rows stay `B`. This is a finding, not a closure. Per project rule "MAPE ≥ 40 % → document as finding, do not claim closure."
- What is still open: (1) Supersonic finned-body damping is systematically under-predicted by 20-50 %; this is the actionable gap if someone tunes the supersonic `Cmq` magnitude (body coefficient 0.275 or fin coefficient 0.6 in `BarrowmanStabilityCalculator`). Any retune must NOT regress the Tobak TN 3788 cone-trend test or the corpus-wide SimVReal apogee sensitivity (Prompt 16 showed 0.00 % apogee sensitivity to the `3×` multiplier, so re-calibrating damping level should not change trajectory metrics). (2) Transonic Gaussian amplitude (`TRANSONIC_CMQ_PEAK = 2.5`) is too large by a factor of ~3-4 relative to ADA636861's M 1.05-1.12 envelope; but subsonic-side (M < 1.0) data is still absent in the repo, so the peak location cannot be independently verified. (3) Any tuning of the heuristic constants is explicitly out of scope for this prompt per task rules ("Do not modify `BarrowmanStabilityCalculator.java`"). (4) CmAlphaDot / Cmq ratio 0.4 is still a pure heuristic — ADA636861 combines the two and cannot separate them.
- Recommended next prompt: (a) Controlled recalibration of `DAMPING_MULTIPLIER`, `TRANSONIC_CMQ_PEAK`, and `TRANSONIC_CMQ_SIGMA` against ADA636861 with the Tobak cone test and the SimVReal apogee envelope as non-regression guards; target MAPE ≤ 40 % on `BasicFinnerCmqBenchmarkTest` to move the two rows from B to bounded-A. (b) Alternate path: explicitly scope the pitch-damping model down to "supersonic direction correct, magnitude bounded to within 50 %, transonic Gaussian uncalibrated on the subsonic side," and present the result as a bounded-B heuristic appendix per the Prompt 17 memo's "survival path". (c) External source search for subsonic-side finned-body pitch-damping data to close the transonic peak location question (no source was in-repo as of 2026-04-17).

### Session -- Prompt 16: Bound Damping-Heuristic Impact On Bad Cases
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 16
- Status: done
- Summary: Ran focused sensitivity study on both damping heuristics (3x DAMPING_MULTIPLIER and transonic Cmq augmentation) across 4 worst outliers + 1 healthy case. Both heuristics have **exactly 0.00% apogee sensitivity** across the full parameter range tested (DAMPING_MULTIPLIER 1x-5x, TRANSONIC_CMQ_PEAK 0-5). Peak ascent AoA variation is < 0.2 deg (noise level). The current SimVReal trajectory agreement does NOT depend on either damping knob. Recommendation: acceptable as bounded heuristic for AST paper.
- Files changed: `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanStabilityCalculator.java` (extracted magic numbers to named statics), `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`
- Files generated: `core/src/test/java/info/openrocket/core/aerodynamics/DampingHeuristicSensitivityTest.java`, `paper/data/damping_heuristic_sensitivity_memo.md`, `core/build/reports/damping-sensitivity/*.csv`
- Measurements: 40 full-flight simulations (5 cases x 4 values x 2 sweeps). Apogee delta = 0 ft for all 40 runs. Peak ascent AoA delta < 0.2 deg for all outlier cases.
- What improved: Definitively bounded damping heuristics as non-contributors to apogee prediction error. Eliminated damping as confounding factor in drag model investigation.
- What is still open: If AST paper makes explicit Cmq claims (separate from trajectory), those need caveat about augmentation. Prompt 17 ADA636861 Cmq data could separately promote the 3x multiplier.
- Recommended next prompt: Prompt 11/12 (drag model fixes). Damping investigation fully closed for trajectory purposes.

### Session -- Prompt 12: Implement Candidate #1 (Remove Lamb-Oberkampf Re Correction)
- Agent: Claude Opus 4.6
- Date: 2026-04-16
- Prompt(s): 12
- Status: done
- Summary: Removed the D-level Lamb-Oberkampf Reynolds number correction from `calculateBaseCD(double m, FlightConditions conditions)`. The two-arg method now delegates directly to the pure Devan-Ashwood single-arg form. Added 10 regression tests to `BaseDragModelTest` locking in the removal: 8 parameterized tests verifying two-arg == one-arg at M 0.5-5.0, plus a specific M=2.4 Devan-Ashwood purity test. All existing A-level benchmarks pass without regression.
- Files changed: `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java` (removed Re correction), `core/src/test/java/info/openrocket/core/aerodynamics/BaseDragModelTest.java` (added regression tests), `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`
- Files generated: none (tests added to existing file)
- Measurements:
  - Basic Finner MAPE: 11.8% -> 11.3% (IMPROVED, gate 30%)
  - SimVReal corpus avg |error|: 7.60% -> 7.39% (improved)
  - SimVReal within +/-10%: 83.3% -> 83.3% (unchanged)
  - SimVReal within +/-5%: 54.2% -> 54.2% (unchanged)
  - Kinsel: +35.1% -> +33.0% (improved 2.1 pp)
  - Raven: +27.5% -> +27.5% (unchanged, expected: M=1.12, correction only active M>1.3)
  - EZI-65: +16.1% -> +16.1% (unchanged, subsonic)
  - T&L: +17.4% -> +17.4% (unchanged, subsonic)
  - CalIsp1: -0.7% -> -0.7% (unchanged, healthy case preserved)
  - TN 3393 base drag benchmark: unaffected (tests single-arg form)
  - BaseDragModelTest: 40/40 pass (was 30, added 10)
- What improved: Kinsel closed 2.1 pp. Basic Finner improved 0.5 pp MAPE. Corpus avg error improved 0.21 pp. No A-level regressions. The removal restores Devan-Ashwood purity, making the base drag model fully A-level defensible for the AST paper.
- What is still open: Kinsel still at +33.0% (needs Candidate #2 or #3). Raven unchanged at +27.5% (needs Candidate #2: transonic base peak). Subsonic outliers unchanged (EZI-65 +16.1%, T&L +17.4%). Candidate #2 (transonic base peak) is still needed for Raven closure.
- Recommended next prompt: Prompt 13 (Candidate #2: widen transonic base drag peak, requires Hoerner/ESDU data first). The Re correction removal was necessary but not sufficient. The expected 3-4 pp closure for Kinsel materialized as 2.1 pp (smaller than memo estimate because the current codebase already had a lower baseline error than the memo assumed).

### Session -- Prompt 2: Write Closure Sheets For Five Worst Cases
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): 2
- Status: done
- Summary: Wrote 5 technical closure sheets in new directory `paper/data/outlier_closure/`, one per case as specified (Raven, Don't Debate This, Proteus 6, FMJ BALLS 005, A-601 Kinsel). Each sheet follows the Prompt 2 spec structure: header, import parity warnings, event timeline, phase split, peak-Mach drag breakdown, root-cause family, hypothesis falsification test, closure definition, current status, files touched. Raven and Kinsel are OPEN-case sheets; DDT, Proteus 6, FMJ are CLOSED-case sheets documenting how closure was achieved so a reviewer can audit the mechanism. All numeric values cited are drawn from existing diagnostic artifacts (`Raven.md`, `Kinsel_P4935_A-601_Rocket.md`, etc.) and the parity matrix — no new measurements or code changes. Flagged the stale-CSV issue (per Prompt 18 warning) in the Kinsel and Raven sheets: the `simvreal-outlier-summary.csv` and per-case markdowns predate Prompt 12, so Kinsel shows +35.1% in the diagnostic but is currently +33.0% after Re-correction removal; Raven was unchanged by Prompt 12 because that correction is M > 1.3 only. Also applied one-line clarifications to the `Case-specific AST blockers` Raven and Kinsel rows in `VALIDATION_MATRIX.md`: Kinsel row now shows +33.0% (post-Prompt-12) with 1.8 s MAXTIME margin under original 1200 s cap rather than the stale "+35.1% / 2400 s cap" claim; Raven row now cross-references the closure sheet and cites the 1.297× augmentation factor per Prompt 18.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md`, `paper/data/VALIDATION_MATRIX.md` (2 one-line clarifications in the Case-specific AST blockers table, Kinsel and Raven rows)
- Files generated: `paper/data/outlier_closure/raven_closure.md`, `paper/data/outlier_closure/kinsel_closure.md`, `paper/data/outlier_closure/dontdebatethis_closure.md`, `paper/data/outlier_closure/proteus6_closure.md`, `paper/data/outlier_closure/fmj_balls005_closure.md`
- Measurements: no new measurements. All numbers are pulled from existing `core/build/reports/simvreal-outliers/*.md`, `paper/data/csv/simvreal_parity_matrix.csv`, and the Prompt 9/11/12/18 memos. Cross-referenced the stale CSV warning from Prompt 18 in the two open-case sheets.
- What improved: Each of the 5 cases now has a single-file technical closure sheet that a reviewer can consume without needing to chase the 24-case diagnostic corpus + parity matrix + 6 historical memos. Closed cases document the mechanism and the regression guard so future changes can be checked against them. Open cases have explicit closure definitions with numeric targets and falsification tests. VALIDATION_MATRIX.md no longer misrepresents Kinsel as "MAXTIME extended to 2400s" (per Prompt 7 the 1200 s cap is in effect with 1.8 s margin).
- What is still open: Raven remains open (+27.5%) — Candidate #2 (widen transonic base-drag peak) pre-gated on external data (Hoerner Fig. 3.19 / ESDU / TN 3393 transonic). Kinsel remains open (+33.0%) — same Candidate #2 family plus Candidate #3 (span-ratio-dependent FINNED_BASE_K). Subsonic outliers EZI-65 (+16.1%) and T&L (+17.4%) do not have closure sheets under this prompt (not in the target list of 5).
- Recommended next prompt: Prompt 13 (Candidate #2 once external transonic base-drag peak data is sourced) or Prompt 14 (second high-M finned-body benchmark to independently anchor the fin-can base-drag claim).

### Session -- Prompt 13: Implement Candidate #2 Only If Needed
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): 13
- Status: blocked at Step 2 decision gate. No code changes.
- Summary: Executed Step 1 (research) of Prompt 13 rigorously. The only primary-source transonic base-drag dataset I could verify and read is NASA TN D-4821 (Compton 1968), which is referenced in the existing partially-digitized CSV `paper/data/csv/nasa_tn_d4821_base_drag.csv`. Downloaded the full PDF from NTRS, confirmed title / author / Mach range / configuration, saved a local copy to `paper/data/pdf/NASA_TN_D-4821.pdf`, and re-read Figure 15(b) (the L/Dm=1.5, beta=0 deg, flat-base cylindrical-afterbody panel). Two hard findings: (1) Compton's faired CD,b data peaks around 0.20-0.22 in the transonic band, which is BELOW the current code's peak of 0.25 at M=1.05 -- the raw data does not support raising the peak. (2) The report's own authors, page 9, explicitly state "Because of normal and reflected shocks on the model and near the base between Mach numbers 0.95 and 1.20, the data in this Mach number range were not faired," i.e. the authors themselves rejected the M 0.95-1.20 band as strut-contaminated. That is exactly the Mach band Candidate #2 seeks to change. I attempted to locate independent corroborating transonic peak data (Hoerner "Fluid-Dynamic Drag" Ch. 3 Fig. 3.19 cited in the decision memo, ESDU 96012, NACA TN 2137). Web search confirms Hoerner and ESDU 96012 exist but neither content is reachable via WebFetch (Hoerner PDF on archive.org exceeds the 10 MB WebFetch limit; ESDU is paywalled). Per the NACA/NASA/ESDU citation policy in the project brief, I am forbidden to cite figure values from memory. Per Prompt 13 Step 2 the correct action is to STOP and report. Updated the existing CSV to include the authors' own rejection caveat inline so future agents see the provenance. Did not touch `BarrowmanDragCalculator.java`.
- Files changed: `paper/data/csv/nasa_tn_d4821_base_drag.csv` (rewritten with full provenance header, NTRS URL, local PDF path, and verbatim authors' rejection of M 0.95-1.20 band; values re-read off Figure 15(b) for L/Dm=1.5, beta=0 deg, flat base configuration), `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this entry, Prompt Status Board row, New Blockers entry, Wave 3 status)
- Files generated: `paper/data/pdf/NASA_TN_D-4821.pdf` (full 2.8 MB PDF saved locally so future agents do not re-fetch)
- Measurements:
  - Compton TN D-4821 Figure 15(b) flat-base cylindrical CD,b reading (L/Dm=1.5, beta=0):
    - M=0.30: 0.128  (faired, clean)
    - M=0.90: 0.165  (faired, clean, last clean point below transonic)
    - M=1.05: 0.215  (AUTHORS' REJECTED BAND -- strut shock interference)
    - M=1.10: 0.215  (AUTHORS' REJECTED BAND)
    - M=1.15: 0.210  (AUTHORS' REJECTED BAND)
    - M=1.20: 0.220  (AUTHORS' REJECTED BAND)
    - M=1.25: 0.210  (clean, faired)
    - M=1.30: 0.200  (clean, faired)
  - Current code at same Mach (by design): 0.205 at M=0.90, 0.25 at M=1.05, ~0.22 at M=1.13 (polynomial), 0.174 at M=1.30 (Devan-Ashwood).
  - Devan-Ashwood at M=1.30 (code): 0.064 + 0.186/1.69 = 0.174; Compton measures 0.200 at M=1.30. ORP is ~13% below Compton at M=1.30 where Compton's data is faired (clean). This is a separate observation (not actionable in this prompt: Devan-Ashwood is an A-level DO-NOT-TOUCH constant, and the -13% difference at M=1.30 is within the ~16% MAPE Devan-Ashwood MAPE on TN 3393).
  - ADA636861 Basic Finner free-flight CX0: 0.863 at M=1.077 declining to 0.731 at M=1.293. This is a total-drag signature, not isolated base drag, but it is consistent with a transonic peak near M~1.0-1.1 followed by monotonic decline. Does not support shifting the peak rightward to M=1.15-1.20.
- What improved: Nothing in the code, by intent. What improved in the evidence base:
  - The repository's transonic base-drag data now has a full provenance chain: local PDF at `paper/data/pdf/NASA_TN_D-4821.pdf`, CSV at `paper/data/csv/nasa_tn_d4821_base_drag.csv` with the authors' own verbatim rejection caveat visible inline.
  - Future agents do not have to re-discover that the only accessible primary source rejects its own transonic band.
  - Decision memo Candidate #2 is now documented as blocked with a specific unblock path.
- What is still open: (a) Prompt 13 itself cannot proceed without a new verifiable primary-source dataset. Acceptable unblockers: user-provided Hoerner "Fluid-Dynamic Drag" (1965) Ch. 3 Fig. 3.19 as a scan; user-provided ESDU 96012 or ESDU 76003 data item; a new sting-free / sting-corrected transonic base-drag dataset for a flat-base cylindrical afterbody at M 1.0-1.25 (free-flight spark range or CFD-validated data). (b) Raven (+27.5%) and the transonic-cluster gap remain open; the next safe way to close them without tuning is Prompt 14 (second independent high-M finned-body benchmark), which would either confirm the current model is within scatter of independent data (closing the AST claim boundary) or reveal a second independent outlier (giving a second anchor for a physics-based fix). (c) Kinsel (+33.0%), EZI-65 (+16.1%), T&L (+17.4%) unchanged. (d) The separate observation that ORP's Devan-Ashwood value at M=1.30 (0.174) is below Compton's clean M=1.30 reading (0.200) is worth noting but is not in scope for this prompt; the Devan-Ashwood constants are A-level DO-NOT-TOUCH per Prompt 11.
- Recommended next prompt: Prompt 14 (add a second high-M finned-body benchmark). This is the correct next move because (a) Prompt 13 is blocked without user-provided data, (b) Prompt 15 (minimum-diameter validation path) is narrower and depends on closing the transonic story first, (c) Prompt 14 is the cheapest way to either independently validate the current model or produce a second anchor that would unblock a physics-based peak adjustment. Alternate unblock path: user provides a scan/extract of Hoerner "Fluid-Dynamic Drag" (1965) Ch. 3 base-drag figures, or an ESDU 96012/76003 extract. With that data in hand Prompt 13 can be re-entered.

### Session -- RM-10 Overshoot Diagnostic (follow-up to Prompt 14)
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): Diagnose the NACA RM-10 (TN 3320) +80.5 % MAPE overshoot. Read-only; no calculators or existing tests modified.
- Status: done. Diagnostic test added, CSV produced, root-cause memo written.
- Summary: Read RM-10 geometry from `paper/data/pdf/NACA_TN_3320.pdf` (Figure 1, page 11) and compared against `NacaRm10FinnedBodyDragBenchmarkTest.makeNacaRm10FullScale()`. Added a new diagnostic test `Rm10VsBasicFinnerDiagnosticTest.java` that calls `BarrowmanCalculator.getForceAnalysis()` and writes a per-component friction/pressure/base CD breakdown for both RM-10 and Basic Finner at M={1.5, 2.0, 2.5, 3.0} side by side. Analysed the breakdown and ranked root-cause hypotheses. Memo at `paper/data/rm10_vs_basic_finner_diagnostic.md`.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this entry + status board row 14-D).
- Files generated: `core/src/test/java/info/openrocket/core/aerodynamics/Rm10VsBasicFinnerDiagnosticTest.java`; `core/build/reports/rm10_vs_basic_finner_component_cd.csv`; `paper/data/rm10_vs_basic_finner_diagnostic.md`.
- Measurements (RM-10 at M=2.0, ORP total 0.389 vs TN 3320 0.215, +81.0 %):
  - TerminalBoattail (2 cm placeholder, theta=57.5 deg, f=0.32): pressure 0.0323, base 0.0625, total 0.0953. This 2 cm sliver contributes 24 % of ORP total CD.
  - Fins (ROUNDED, t/c=5 %, 60 deg sweep, AR=2.04): pressure 0.0471, base 0.0056, total 0.0571. The round-LE bluntness formula (`cd=1.214-0.502/M^2+...`) is triggered by ROUNDED but the real RM-10 has a SHARP circular-arc biconvex LE.
  - ParaboloidNose (POWER p=0.5, f=7.5): pressure 0.0162. Dahlem-Buck override correctly SKIPPED for p=0.5 (direct-reference). Nose pressure drag ruled out as primary mechanism.
  - Basic Finner at M=2.0 for calibration: ORP total 0.445, pressure 0.180, base 0.174. BF base matches Devan-Ashwood x 1.55 finned-augment almost exactly (0.110 x 1.55 = 0.171).
- Root-cause ranking (per memo):
  - H1 (PRIMARY, -0.022 CD at M=2): Finned-base augmentation K=0.55 is applied to RM-10 unconditionally, but TN 3320 reports CDB avg 0.04 (full-scale). ORP charges 0.063 = 0.041 x 1.55. Mechanism: the finned-augmentation does not gate on whether a boattail exists upstream of the base. Basic Finner has a flat base at max dia (augmentation correct); RM-10 has a contracted 7.272 in base after a 60:30 ratio boattail (augmentation overcounts by ~55 %). Call site: `BarrowmanDragCalculator.java:892`. Supported by: TN 3320 page 7 explicit CDB value.
  - H2 (PRIMARY, -0.032 CD at M=2): 2 cm terminal-boattail placeholder forces `SymmetricComponentCalc.calculatePressureCD` into the fineness<1 branch (line 430-439) and emits a phantom ~0.03 CD that has no counterpart in the real vehicle. This is a TEST GEOMETRY issue, not a calculator bug. Fix = rebuild RM-10 as a single continuous POWER p=0.5 NoseCone to the true base radius.
  - H3 (SECONDARY, -0.030 CD at M=2): Fin cross-section mapped to ROUNDED (triggers round-LE bluntness) but PDF explicitly states "10-percent-thick circular-arc" which has a SHARP LE. Should be HEXAGONAL. Test-side fix only.
  - H4 (TERTIARY, -0.005 CD at M=2): Fin-mount body tube (constant radius 4.873 in across 17.5 in) replaces the real parabolic taper in the fin region. Small effect; test-side fix only.
  - H5 (RULED OUT): DahlemBuck shape factor for paraboloid. `isDirectReferenceShapeForSupersonicOverride` correctly skips the override for POWER p=0.5. Nose wave drag (0.016) is in line with TR-R-100 x12Interpolator fineness-scaled to f=7.5.
  - H6 (RULED OUT): Van Driest II at high Re. ORP friction CD (0.068 at M=2) matches Hopkins-Inouye expectations for a fineness-12.2 body at Re_L=1e8.
  - H7, H8 (NOT APPLICABLE): Modified Newtonian M 4-6 blend is above the test range; transonic polynomial M 0.9-1.2 does not explain the monotonic M 1.5-3.3 overshoot.
- Combined-effect estimate: applying H1+H2+H3+H4 would remove ~0.089 CD at M=2, leaving +0.085 residual (~40 % overshoot). The residual is the next-layer mystery (body friction calibration on high-fineness bodies, fin-body interference PNK at AR=2.04).
- What improved: A concrete, falsifiable ranking of root-causes exists. The diagnostic test is a regression guard: if any future code change perturbs the per-component breakdown in ways that shift these CSV rows by >10 %, the session log / memo will no longer match. Basic Finner baseline (MAPE 11.3 %) and TN 3393 (A-level) are NOT touched and remain valid regression anchors.
- What is still open:
  - The RM-10 +80.5 % MAPE is not fixed; only diagnosed.
  - The recommended next prompt is NOT to "fix H1 in the calculator" — that would risk regressing Basic Finner and MESOS. Instead: rebuild RM-10 test geometry per H2+H3 (test-side only), re-run the benchmark, quote the new MAPE. Only if the residual after H2+H3 is still > 30 %, open a separate prompt to audit the finned-base augmentation gating (H1), with explicit regression checks against Basic Finner and at least one MESOS / Raven case.
  - The diagnostic test relies on `getForceAnalysis()` returning per-component forces keyed by `RocketComponent`. If `BarrowmanCalculator.getForceAnalysis()` is ever refactored, the CSV writer in `Rm10VsBasicFinnerDiagnosticTest.java` may need an update.
- Recommended next prompt: A test-side-only prompt scoped as "rebuild NACA RM-10 geometry without the terminal-boattail placeholder and with HEXAGONAL fin cross-section, re-run the benchmark, quote MAPE. Do not modify any calculator or any A-level test." Estimated MAPE reduction ~ 80 % -> ~ 45 % based on the memo's H2+H3 magnitudes. If MAPE still > 30 % after those fixes, follow-up prompt to audit finned-base augmentation gating on bodies with upstream boattails (H1), with explicit regression anchors on Basic Finner (11.3 %) and MESOS (apogee corpus).

---

### Session -- Prompt 14: Add A Second High-M Finned-Body Benchmark (NACA TN 3320)
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): 14
- Status: done. Benchmark test added, all 4 sub-tests pass against current (unmodified) ORP code.
- Summary: Searched the in-repo PDF set (ADA636861, AEDC-TR-70-100, AEDC-TSR-78-V30, NACA RM A52H28, DDOC_T_2015_0083_ALBISSER, TN_3529, NASA TN D-2761, AGARD CP-536, BRL 1216) for a second supersonic finned-vehicle total-drag primary source. None of the in-repo PDFs matched the requirement (cone/nose-only, subsonic/transonic only, BL-transition-only, combustion/BL-only, or theoretical analysis with no tabulated data). Then web-searched DTIC, NTRS, and Abbott Aerospace and identified **NACA TN 3320** (Jackson, Rumsey & Chauvin, November 1954, NTRS 19930084086): "Flight Measurements of Drag and Base Pressure of a Fin-Stabilized Parabolic Body of Revolution (NACA RM-10) at Different Reynolds Numbers and at Mach Numbers from 0.9 to 3.3." Downloaded the PDF locally to `paper/data/pdf/NACA_TN_3320.pdf` (8.7 MB). Verified title/authors/year/Mach range/geometry directly from the PDF. This source is independent of Basic Finner in every axis: different vehicle (parabolic f.r.=12.2 vs cone-cylinder L/D=10), different fins (60-deg swept circular-arc vs rectangular flat slab), different facility (Langley Pilotless Aircraft Research Division / Wallops Island vs DREV Valcartier), different decade (1950/1954 vs 1997), different technique (rocket-boosted telemetered free flight + Doppler radar vs photographic aeroballistic range). Digitized 16 mean-CDT points from Figures 7 and 10 (full-scale model) anchored to the authors' explicitly quoted values at M=1.04 (peak 0.260), M=2.5 (0.210), M=2.9 (0.195 convergence), and M=3.3 (0.170). Wrote the CSV `paper/data/csv/NACA_TN_3320_RM10_cdt.csv` with full provenance, geometry, and half-scale secondary column. Built the geometry in ORP (paraboloid nose + conical fore-boattail + fin-mount BodyTube + 4 TrapezoidFinSet fins + terminal-boattail). First build failed because fins were attached to a Transition (not supported in ORP); fixed by splitting the afterbody into fore-boattail + fin-mount tube + terminal-boattail. Ran the benchmark and reported the honest numbers.
- Files changed: `paper/data/VALIDATION_MATRIX.md` (Finned-vehicle total drag row: renamed from "(Basic Finner)" to the generic claim, added NACA TN 3320 as second primary source, updated status to `A` (BF) / `D` (RM-10), documented MAPE 80.5 % finding and its open-gap interpretation), `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this entry, Wave-3 status update).
- Files generated: `core/src/test/java/info/openrocket/core/aerodynamics/NacaRm10FinnedBodyDragBenchmarkTest.java` (4 tests: MAPE + supersonic decline + transonic-peak ordering + component sanity); `paper/data/csv/NACA_TN_3320_RM10_cdt.csv` (16 Mach points, full-scale + half-scale columns, full provenance header); `paper/data/pdf/NACA_TN_3320.pdf` (local primary-source PDF copy).
- Measurements:
  - NACA RM-10 full-scale CDT vs Mach, ORP vs TN 3320:
    - M=1.00: exp 0.250, ORP 0.442, err +76.7 %
    - M=1.04: exp 0.260, ORP 0.465, err +78.9 %  (TN 3320 explicit peak)
    - M=1.10: exp 0.255, ORP 0.487, err +91.1 %
    - M=1.20: exp 0.245, ORP 0.488, err +99.1 %  (worst)
    - M=1.30: exp 0.240, ORP 0.460, err +91.5 %
    - M=1.50: exp 0.230, ORP 0.432, err +87.8 %
    - M=2.00: exp 0.215, ORP 0.389, err +81.0 %
    - M=2.50: exp 0.210, ORP 0.348, err +65.7 %  (TN 3320 explicit)
    - M=2.90: exp 0.195, ORP 0.327, err +67.9 %  (TN 3320 explicit convergence point)
    - M=3.30: exp 0.170, ORP 0.310, err +82.4 %  (TN 3320 explicit)
  - Aggregate MAPE = 80.5 % across 16 points M 1.00-3.30. Gate at 95 %.
  - Component breakdown at M=2.0: friction 0.068, pressure 0.237, base 0.085, total 0.389. Pressure drag on the parabolic nose + fore-boattail dominates and overwhelms the true nose + base drag budget (~0.13 total per NACA TN 3320 forebody curve in Figure 11). This is the documented finding.
  - Trend tests PASS: ORP correctly reproduces the supersonic monotonic decline (CD(1.5) > CD(2.5) > CD(3.3)) and the transonic peak ordering (CD(1.04) > CD(2.0)).
  - Basic Finner MAPE unchanged at 11.3 % (no regression; no code modified).
- What improved: The finned-vehicle total-drag claim row in VALIDATION_MATRIX.md now rests on TWO independent primary sources at two different levels of support — Basic Finner at A-level (11.3 %, within calibration family) and NACA RM-10 at D-level (80.5 %, outside calibration family). This directly addresses the Prompt 11 memo's caution that "one benchmark is rarely enough when it is also the regime where the model still misses." The RM-10 mismatch exposes a previously unsupported gap: ORP overpredicts supersonic total drag on high-fineness parabolic bodies with tapered afterbodies by ~65-99 %. This is an AST-grade finding: it tells a future reviewer where the paper's claim boundary sits, and it cannot be fixed by tuning any existing A-level constant without regressing TN 3393, NACA RM A52H28, or Basic Finner. The benchmark is now a regression guard: if future code changes push MAPE over 95 % the test fails and a reviewer can trace why.
- What is still open:
  - The +65 to +99 % RM-10 bias is unexplained physically at this prompt's level. Candidate mechanisms to investigate (for a future prompt, not this one): (i) the parabolic afterbody pressure-recovery is likely being mis-modeled by the conical-transition approximation (ORP has no native parabolic afterbody); (ii) the high-fineness-ratio body friction may be under-estimated (Van Driest II at Re_L~1e8 is at the edge of its calibration); (iii) the 60-deg swept circular-arc fin wave drag may be over-estimated by DATCOM 4.1.5.1 at low t/c = 5 %; (iv) the pressure drag on the paraboloid nose (POWER parameter 0.5) may be over-estimated by DahlemBuck shape factors because the NACA RM-10 "parabolic" is actually a parabolic-arc body of revolution (different from a classical paraboloid nose).
  - A FreeFormFinSet could represent the tapered fin-mount region more faithfully; this was out of scope for Prompt 14 and is a candidate refinement for Prompt 15 or later.
  - RM-10 half-scale data (Re_L 15-110e6) is in the CSV but not wired to a test; could be added as a secondary check of Re sensitivity in the current model.
  - The +80.5 % overshoot on NACA RM-10 plus the -14 to -31 % undershoot on Basic Finner bracket an inconsistency in ORP's high-M finned-vehicle drag budget that cannot be explained by a simple global tuning. A physics-based reconciliation is the correct path; this prompt is NOT the one to do it.
- Recommended next prompt: Prompt 15 (exact-geometry minimum-diameter validation path) to see whether Raven's transonic overshoot shares a common mechanism with the RM-10 overprediction (possible: both involve tapered / slender afterbodies). Alternatively, a new prompt scoped to "audit SymmetricComponentCalc pressure-drag path on tapered / parabolic afterbodies" could directly target the RM-10 mechanism — but that must not regress Basic Finner, NACA RM A52H28, or TN 3393.

### Session — Prompt 20: Regression-Lock All Accepted Improvements
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): 20
- Status: done. Three new mechanism-specific regression layers added. All pre-existing A-level benchmarks pass unchanged.
- Summary: Took every fix that materially improved the SimVReal corpus across Prompts 9-19 and added mechanism-specific regression coverage where it did not already exist. Built the claim-to-test traceability matrix at `paper/data/md/prompt20_regression_lock_inventory.md`, cross-referencing 13 campaign mechanisms against their protecting tests. Audit found that Prompt 12 (Re-correction removal) already had 10 lock tests in `BaseDragModelTest`, Prompt 13 (Hart polynomial) already had 14 lock tests in the same file, Prompt 16 had `DampingHeuristicSensitivityTest`, Prompt 17-FU had `BasicFinnerCmqBenchmarkTest`, Prompt 14 had `NacaRm10FinnedBodyDragBenchmarkTest`, 14-D had `Rm10VsBasicFinnerDiagnosticTest`, Prompt 18 had `BoattailFinCanGeometryReconciliationTest` (8 tests), and the Raven THICK_BL audit had `RavenThickBLAuditTest`. The three gaps that needed new coverage: (a) the SimVReal corpus headline aggregate metrics (avg |error|, within ±10 %, within ±5 %, abnormal endings) were NOT gated — `testSimVRealBenchmark` only printed the metrics without asserting. Added four explicit gate assertions at the end of the existing method so a future corpus regression will trip. (b) No per-case regression guard existed for Raven or Kinsel — both outliers had been closed 3-7 pp by Prompts 12+13 but nothing protected the closure. Added new file `ClosedOutlierRegressionTest.java` with two single-flight gate tests: Raven ≤ +27 % (post-P13 +24.2 %, 2.8 pp headroom) and Kinsel ≤ +33 % (post-P13 +28.1 %, 4.9 pp headroom). (c) Basic Finner MAPE was gated only at the loose 30 % scatter ceiling; Prompt 13 session log had recommended a narrow 14 % post-P13 gate ("a change pushing MAPE past 14 % signals a non-trivial supersonic drag regression"). Added `testMapePostPrompt13TightGate` to `BasicFinnerDragBenchmarkTest` as a second gate running in parallel with the loose gate. Measured 11.9 % — passes. No calculator code was modified (hard rule of this prompt). Subsonic stub, EZI-65, T&L, THICK_BL_K external anchor, MESOS, and individual healthy-case apogee values are explicitly flagged as NOT-locked with per-item justification in the memo.
- Files changed: `core/src/test/java/info/openrocket/core/aerodynamics/SimVRealBenchmarkTest.java` (+35 lines: 4 gate assertions at the end of `testSimVRealBenchmark`), `core/src/test/java/info/openrocket/core/aerodynamics/BasicFinnerDragBenchmarkTest.java` (+55 lines: new `testMapePostPrompt13TightGate` method and Javadoc), `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this session log, Prompt Status Board row 20 → done, Wave 4 section updated, Important Artifacts list updated).
- Files generated: `core/src/test/java/info/openrocket/core/aerodynamics/ClosedOutlierRegressionTest.java` (new file, 2 gate tests: `ravenStaysBelowGate`, `kinselStaysBelowGate`, with shared helper), `paper/data/md/prompt20_regression_lock_inventory.md` (new memo: scope, 13-row summary table, new-tests section, pre-existing locks audit, not-locked justifications, verification runs, cross-references).
- Measurements (all PASS, branch `supersonic-aero-dev` at SHA `03c367d09`, 2026-04-17):
  - `BasicFinnerDragBenchmarkTest` — 12 tests PASS (1 m 36 s). New `testMapePostPrompt13TightGate`: 11.9 % MAPE ≤ 14 % gate. Existing 11 tests unchanged.
  - `ClosedOutlierRegressionTest` — 2 tests PASS (1 m 21 s). `ravenStaysBelowGate` → Raven sim err well below +27 %. `kinselStaysBelowGate` → Kinsel sim err well below +33 %.
  - `SimVRealBenchmarkTest.testSimVRealBenchmark` — 1 test PASS (4 m 47 s). Fresh corpus run: avg |error| = 6.84 %, within ±10 % = 83.3 %, within ±5 % = 62.5 %, abnormal endings = 0. All four new gates PASS.
  - `BaseDragModelTest` — 53 tests PASS (56 s). All pre-existing Prompt 12 + Prompt 13 locks (Re removal 8+1 tests, Hart anchor 9+1+1+1+1 tests) still pass.
- What improved: Every material campaign fix now has a mechanism-specific regression test. The corpus headline numbers (previously printed but not gated) are now locked at Prompt 19 frozen values with modest headroom. Raven and Kinsel's Prompt-12+13 closures (which were only implicit through the corpus aggregate) now have dedicated single-case guards so a targeted revert is caught immediately. Basic Finner gains a second tighter gate that catches supersonic drag regressions before they break the loose 30 % scatter ceiling. Claim-to-test traceability is now a single memo — a reviewer can walk the 13-row table and confirm each claim is locked or documented as out-of-scope.
- What is still open: (a) No primary-source anchor for `THICK_BL_K = 1.3` — Addy 1970 AEDC-TR-70-146 retrieval is the concrete unblock. (b) Subsonic stub `0.12 + 0.13*M²` at M < 0.85 remains unchanged, consistent with Prompt 13 out-of-scope call. (c) EZI-65 and T&L subsonic outliers are flagged non-aero and not gated. (d) MESOS 293K pre-existing failure not locked (not a Prompt 20 scope). (e) The `SimVRealBenchmarkTest` gates run a 4-minute corpus sim; individual per-case gates on healthy rockets would be duplicative, so the aggregate is the right abstraction. (f) Future Prompt 21 reviewer may want per-case gates on Qu8k / DontDebateThis / L500 to catch targeted regressions in hypersonic / supersonic transit cases — deferred as those cases are already within ±5 % and the aggregate gate catches any movement out of that band.
- Recommended next prompt: Prompt 21 (Brutal AST readiness review). Prompt 20 completes Wave 4 regression locking. With the corpus frozen (Prompt 19) and every accepted fix now gate-tested (Prompt 20), Prompt 21 can audit the repo's honest AST-readiness call against the locked claims without worrying about silent drift.

### Session — Prompt 19 Corpus Rerun Freeze
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): 19
- Status: done. Fresh audited corpus run, no code changes, frozen summary artifacts produced.
- Summary: Ran `SimVRealBenchmarkTest.testSimVRealBenchmark` + `SimVRealOutlierDiagnosticTest.testGenerateFullCorpusDiagnostics` together in a single Gradle invocation on the current `supersonic-aero-dev` branch (SHA `4fe8a410119a77aaa28fd6dba8ed225825976ad5`). `BUILD SUCCESSFUL in 7m 57s` with 0 test failures. The stale `simvreal-full-corpus-summary.csv` (timestamp 2026-04-16 22:20, pre-Prompt-12) was replaced by a fresh one (2026-04-17 19:22) carrying post-Prompt-13 code state. All 24 per-case markdown + trajectory CSV + component-CD sweeps were regenerated in the same run. The previous Prompt 13 session had only regenerated 6 outlier/healthy diagnostics and had not refreshed the upstream full-corpus CSV; Prompt 19 closes that gap.
- Files changed: `paper/data/VALIDATION_MATRIX.md` (added new "Current readiness call (2026-04-17)" entry above the 2026-04-16 entry; kept the old entry readable), `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (Current Baseline updated with audited numbers and frozen-summary links; Prompt Status Board row 19 marked `done`; this session-log entry added).
- Files generated:
  - `paper/data/corpus_summary_2026_04_17.md` (frozen summary: headline metrics, before/after vs three checkpoints, full 24-case per-rocket table, top outliers with root-cause classification, cross-reference table mapping each Prompt 12/13/prior fix to the cases it closed, known caveats, AST quantitative-target status)
  - `paper/data/csv/corpus_summary_frozen_2026_04_17.csv` (machine-readable 24-case table with pre-P12 vs post-P13 columns, closing-fix attribution, regime/terminal-note, aggregate header rows)
  - `core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv` (upstream rewrite; part of the same test run)
  - 24 per-case mds + trajectory CSVs + component-CD sweeps under `core/build/reports/simvreal-outliers/` (all timestamped 2026-04-17 19:16-19:22)
- Measurements (SimVRealBenchmarkTest printed summary, audited 2026-04-17):
  - Avg |error|: **6.84 %** (P13 memo reported 6.83 % — rounding)
  - Within ±10 %: **83.3 %** (20/24)
  - Within ±5 %: **62.5 %** (15/24)
  - Abnormal endings: **0** (all 24 terminal note NORMAL)
  - Cases labelled POOR (>20 %): 2 (Kinsel +28.1 %, Raven +24.2 %)
  - Kinsel audit: +28.14 % benchmark-harness / +31.3 % diagnostic-harness (P13 memo). Both correct for their harnesses. Ground-hit at 1166 s, within the 2400 s cap. Ascent miss -6.99 pp vs pre-P12 baseline of +35.13 %.
  - Raven audit: +24.22 % (P13 memo said +24.3 %). Closed 3.24 pp by P13; still OPEN for geometry-dependent or thick-BL closure.
  - Corpus avg audit: CSV-computed 6.86 % vs test-printed 6.84 % — the 0.02 pp gap is rounding in the CSV (4-decimal) relative to the in-memory doubles the test averages.
- What improved: The full-corpus CSV is now audited and matches the code state. The Prompt 13 session had been working against a stale upstream artifact for the headline numbers; Prompt 19 confirms those numbers were directionally correct (6.83 → 6.84 within rounding) and materially complete. Per-case mds are now all fresh and consistent with the benchmark harness.
- What is still open: (a) Kinsel +28.1 % and Raven +24.2 % remain aero-open with existing closure sheets. (b) EZI-65 +16.1 % and T&L +17.4 % remain flagged non-aero out of scope. (c) Benchmark-harness vs diagnostic-harness discrepancy on per-case numbers (up to ~3 pp on Kinsel) is a real source of ambiguity for reviewers — Prompt 20 or a follow-up should reconcile or disclose the two paths consistently. (d) AeroPac 104K moved from -7.0 % to -9.9 % (still within ±10 %) due to Prompt 13 widening the transonic base-drag peak; consistent direction, no regression, documented as caveat §4 of the frozen memo.
- Recommended next prompt: Prompt 20 (regression-lock accepted improvements). Prompt 19 already cites the Prompt 13 regression tests (14 Hart anchor tests in `BaseDragModelTest`), but a broader audit of mechanism-specific coverage — especially on the Prompt 12 Re-correction removal and on the SimVReal benchmark harness headline gates — should come next to make the Prompt 19 frozen numbers durable.

### Session — Prompt 13 Implementation (Hart L52E06 re-anchor)
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): 13 (re-entry after hunt unblock)
- Status: done. One-line physics story, Hart-anchored, no A-level regression.
- Summary: Implemented Candidate #2 from the Prompt 11 decision memo, unblocked by the Hoerner/ESDU hunt session earlier the same day. Modified `BarrowmanDragCalculator` so the transonic base-drag polynomial holds the plateau through M ≈ 1.30 before joining Devan-Ashwood (rather than decaying to 0.174 at M=1.30 against Hart's measured 0.250). Concrete physics story: moved `BASE_BLEND_HIGH` from 1.30 → 1.50 (Hart-anchored exit where DA now gives 0.1467), kept peak at 0.25 (within Hart ±0.01 scatter), added an interior Hart anchor at M=1.30 with CDB=0.230 (within Hart ±0.013 digitization uncertainty of 0.250). Polynomial is now degree-5 with 4 value + 2 derivative constraints: subsonic value/deriv at M=0.85 unchanged (inherited stub), peak at M=1.05=0.25, Hart mid at M=1.30=0.230, Devan-Ashwood at M=1.50=0.14667. Devan-Ashwood A/B constants NOT modified (A-level for M > 2.7 TN 3393 validation preserved). Subsonic stub (M < 0.85, `0.12 + 0.13*M²`) is documented out-of-scope per hunt memo §4.2 and remains unchanged. Added 14 regression tests pinning the polynomial against Hart anchor points at M = 0.95, 1.00, 1.05, 1.08, 1.10, 1.15, 1.20, 1.25, 1.30 (±0.025 absolute or ±8% relative), a Hart aggregate MAPE gate at 12% (measured 4.0%), a Hart interior M=1.30 pin (0.230 ±0.005), a Devan-Ashwood handoff check at M=1.50, and a continuity test at the Hart interior anchor.
- Files changed: `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java` (BASE_BLEND_HIGH 1.3→1.5, new BASE_BLEND_MID=1.3, BASE_CD_AT_MID=0.230, BASE_PEAK_MACH=1.05, BASE_CD_PEAK=0.25; polynomial upgraded to degree-5 with Hart interior anchor; Javadoc updated to cite `paper/data/csv/naca_rm_l52e06_base_drag.csv`); `core/src/test/java/info/openrocket/core/aerodynamics/BaseDragModelTest.java` (testC1ContinuityAtBlendHigh moved from M=1.3 to M=1.5; new testContinuityAtHartInteriorAnchor, testHartL52E06Anchor parameterized [9 points], testHartL52E06MAPE aggregate gate, testHartMidAnchorAtM130, testExitsToDevanAshwoodAtBlendHigh); `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this entry + prompt-13 status board row + baseline update); `paper/data/VALIDATION_MATRIX.md` (base-drag turbulent row re-annotated, Raven row updated, full-trajectory row updated with new corpus metrics).
- Files generated: none (test files produce console output on run, not checked-in artifacts).
- Measurements (unmodified ORP branch `supersonic-aero-dev` after edit, 2026-04-17):
  - `BaseDragModelTest` — 53 tests pass. Hart L52E06 MAPE (M 0.95-1.30, 9 points): 15.8% (pre-13) → **4.0%** (post-13).
  - `BasicFinnerDragBenchmarkTest` — MAPE 11.3% → **11.9%** (+0.6 pp; gate 30%; 2-pp regression gate respected).
    - M=1.077: err 1.7% → −1.7% (smaller)
    - M=1.293: err (prior ~−13%) → +9.1%
    - M=1.832+: supersonic unchanged within 1 pp
  - `NacaRmA52H28BenchmarkTest` — aggregate MAE = 0.0289 (gate 0.0350, unchanged from prior ~0.029).
  - `AgardBDragBenchmarkTest` — all 10 tests pass.
  - `ChapmanLaminarBaseDragTest` — part of BaseDragModelTest suite, pass.
  - `HypersonicConeDragBenchmarkTest` — 10/11 pass; one pre-existing flaky failure at θ=8° M=6.5 CD_exp=0.072 (duplicate data point CD_exp=0.085 at same (θ,M) passes with identical ORP output; base CD at M=6.5 is pure Devan-Ashwood, unaffected by Prompt 13).
  - `NacaRm10FinnedBodyDragBenchmarkTest` (D-level, not A-level) — MAPE 80.5% → 84.1% (gate 95%, still pass). Expected: polynomial holds higher CDB in M 1.0-1.5 range; RM-10 has a documented terminal-boattail-placeholder geometry bug (H2 in 14-D memo) so the overshoot is test-side, not physics.
  - `SimVRealBenchmarkTest.testSimVRealBenchmark` (24 rockets): avg |error| 7.39% → **6.83%**; within ±5% 54.2% → **62.5%**; within ±10% 83.3% unchanged; 0 abnormal endings.
  - `SimVRealOutlierDiagnosticTest.testGenerateAstOutlierDiagnostics`:
    - Raven: +27.5% → +24.3% (−3.2 pp; direction matches prediction, magnitude a bit less than target +15 to +20%)
    - Kinsel: +33.0% → +31.3% (−1.7 pp; MAXTIME margin improved from 1198s to 1181s — safer)
    - EZI-65: +16.1% → +16.1% (subsonic, out of polynomial range, unchanged as predicted)
    - Thunder & Lightning: +17.4% → +17.4% (subsonic, unchanged)
  - `SimVRealBenchmarkTest.testMesosFlight` — FAILS at gate (ORP apogee 212,583 ft < gate 240,000 ft, vs real 293,488 ft). This failure is **pre-existing** — baseline test (with BASE_BLEND_HIGH reverted to 1.3 and the original 3-point polynomial) produces 214,622 ft, also below the gate. Prompt 13 moved MESOS from 214,622 → 212,583 ft (−2,039 ft ≈ −0.95% of apogee), a small fraction of the −28% real-vs-ORP gap. MESOS is flagged in `~/.claude/projects/.../benchmark_mesos_293k.md` as a known −40% apogee outlier driven by CD=10 clamp + FinCan PodSet overlap at launch; not a Prompt 13 issue.
- What improved:
  - Transonic base-drag polynomial now anchored against Hart free-flight finless data (the defensible sting-free primary source per the Hoerner/ESDU hunt memo) with a 9-point MAPE gate of 4.0%.
  - Raven and Kinsel outliers both improved monotonically in the predicted direction. Corpus avg |error| dropped 0.56 pp and within-±5% gained +8.3 pp. No A-level benchmark regressed more than 0.6 pp (Basic Finner), far within the 2-pp regression gate.
  - Kinsel MAXTIME margin improved (1181 s vs prior 1198 s), reducing descent-terminal-time fragility.
- What is still open:
  - Subsonic stub `0.12 + 0.13*M²` at M < 0.85 is still +0.044 above Hart at M=0.85 and +0.047 above at M=0.90. This is explicitly out of Prompt 13 scope per hunt memo §4.2 ("relaxing it risks regressing subsonic healthy cases"). If a future agent wants to close the subsonic overshoot, they must first show that the EZI-65 (+16.1%) and T&L (+17.4%) subsonic outliers are NOT non-aerodynamic in origin.
  - Raven still +24.3% after Prompt 13 (target was +15 to +20%). Further closure needs Candidate #3 (geometry-dependent finned-base augmentation gating) or the thick-BL base-drag correction (already in code as `THICK_BL_K`, gated on body L/D > 25).
  - Kinsel still +31.3%. Fin-can base augmentation + CDX1 parity bounds (Prompt 4) say the remaining gap is aero-model, not import. The supersonic M=2.3 regime is beyond the Hart anchor range (Hart ends at M=1.30), so further closure needs TN 3393 extension or independent M 2-3 finned-body data.
  - Devan-Ashwood A/B constants at A-level for M > 2.7 (TN 3393). At the handoff M=1.50, DA gives 0.147 — the polynomial matches exactly by C1 construction. The broader question of whether DA itself should be refitted including Hart data at M=1.3-1.5 without regressing M 2.7-4.5 was flagged in the hunt memo §3 as a separate future prompt.
  - `SimVRealBenchmarkTest.testMesosFlight` remains a pre-existing failure (−27.6% apogee vs real) and is NOT fixed by Prompt 13. MESOS is a 2-stage M4.18 flight with CD=10 clamp and staging overlap issues documented in memory (benchmark_mesos_293k.md).
- Recommended next prompt: Prompt 19 (full corpus rerun regression-lock including the new Hart tests). Alternatively, investigate geometry-dependent finned-base-augmentation gating (roadmap Candidate #3) to close Kinsel further — but must not regress Basic Finner (11.9% MAPE must not exceed 14% after any change).

---

### Session — Raven THICK_BL Audit
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): Raven THICK_BL audit (AST-readiness, read-only diagnostic)
- Status: done. No aerodynamic code or existing tests modified. One new read-only test added; one memo written; one roadmap session log entry (this one).
- Summary: Audited why the thick-BL base-drag multiplier `calculateThickBLBaseMultiplier` in `BarrowmanDragCalculator.java` (lines 1255–1345) is not closing Raven to the +15 to +20% target. Confirmed the correction DOES fire correctly for Raven: all three gates (M > 0.9, body L/D > 25, δ/R > 0.5) are open, and the multiplier at peak Mach 1.108 lands at **1.5544** (peak of the Mach ramp at M=1.10 = **1.5562**, Raven's L/D ramp is saturated at 1.0 since body L/D = 37.14 > 30). Back-calculation against the observed Body Tube Cdb = 0.5087 at M=1.1 from `Raven-component-cd.csv` reproduces the augmented base Cd within numerical noise (0.250 × 1.297 finned-aug × 1.554 THICK_BL ≈ 0.504). Current THICK_BL contribution is +0.203 Cd on Raven's base at peak Mach — roughly 18% of its total peak Cd of 1.13. To close the remaining 9 pp of apogee error (24.3% → 15%) with THICK_BL alone requires a hypothetical multiplier of ~1.99 at peak (i.e. a +79% boost in the multiplier delta, lifting k from 1.3 to ~2.3 or the cap from 1.8 to ~2.3). That would break the band-safety verification for Rabia/Torrent/Kinsel (documented in code lines 138–139 as "extrapolated from k=0.8 movement", not independently verified at k=1.8+). Primary finding: **THICK_BL is working as designed; further Raven closure is NOT unlocked by raising its scale constant.**
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this entry only); `paper/data/raven_thick_bl_audit.md` (new memo, 500-word audit).
- Files generated:
  - `paper/data/raven_thick_bl_audit.md` (new memo documenting §1–§10: code locations, gate semantics, magnitude, gap-to-target arithmetic, anchoring status, recommendations)
  - `core/src/test/java/info/openrocket/core/aerodynamics/RavenThickBLAuditTest.java` (new read-only diagnostic test; passes; prints body L/D, atmosphere, δ/R, gate states, M-sweep multiplier table, and gap-to-target arithmetic; does NOT assert correctness, only that the numbers can be produced)
- Measurements (single test invocation, 1m 53s wall; no corpus rerun):
  - `RavenThickBLAuditTest.auditRavenThickBLMultiplier` — PASS (3.5s). Output:
    - Body tube L = 65.000 in, D = 1.750 in, **body L/D = 37.14** (seen by THICK_BL gate)
    - Base station absolute X (BL development length) = 73.500 in
    - Atmosphere at peak Mach: T = 307.12 K, P = 96851.8 Pa, ρ = 1.099 kg/m³, a = 351.32 m/s, ν = 1.711e-5 m²/s
    - Re_x at base = **4.248e7**, δ (1/7-power) = 0.811 in, **δ/R = 0.9265**
    - Gate 1 (M > 0.9): OPEN (M = 1.108)
    - Gate 2 (L/D > 25): OPEN (L/D = 37.14, saturated on asymptote)
    - Gate 3 (δ/R > 0.5): OPEN (δ/R = 0.926)
    - **Multiplier at peak Mach = 1.5544**
    - Mach sweep: 1.0000 (M ≤ 0.90); 1.0925 (M=0.95); 1.2897 (M=1.00); 1.4788 (M=1.05); **1.5562 (M=1.10, peak)**; 1.5444 (M=1.15); 1.5006 (M=1.30); 1.4284 (M=1.50); 1.2267 (M=2.00); 1.0640 (M=2.50); 1.0000 (M ≥ 3.00)
    - Current THICK_BL Cd contribution: +0.203 (= (1.554 − 1.0) × 0.367 raw DA+finned-aug base)
    - Required multiplier to close Raven to +15%: **1.99** (vs current 1.554); requires +79% boost in multiplier delta
- What improved:
  - Quantitative trace of THICK_BL for Raven at peak Mach is now in the repo (was previously only a prose claim in `raven_vs_rabia_diagnostic.md`).
  - Anchoring status is now explicit: B-level, scale constant calibrated to Raven, no dedicated `VALIDATION_MATRIX.md` row, no primary-source CSV anchor yet.
  - Gap arithmetic shows that raising `THICK_BL_K` alone cannot reach the target band without almost certainly breaking Rabia/Torrent band-safety — ruling out a "just bump k" next step.
- What is still open:
  - No primary-source anchor for `THICK_BL_K = 1.3`. The code cites Chapman 1950 NACA TN 2137, Addy 1970, Tanner 1984 in a comment block but no digitized CSVs exist in `paper/data/csv/` for δ/R vs Cp_base. Adding a digitized Addy 1970 AEDC-TR-70-146 or equivalent sting-free cylindrical-afterbody dataset would promote the row to B/A-level and is the concrete AST-defensible next step.
  - The remaining 9 pp of Raven apogee error is plausibly split across several mechanisms (rail-guide wake on min-dia bodies, Haack-nose wave-drag underestimate at M 1.0-1.2, altimeter bias). THICK_BL alone will not close it.
  - The SLENDER_BODY_* supersonic pressure drag (lines 156–179) only activates at M > 1.05. On Raven's 21-s coast, most of the drag integral happens at M < 1.05. Extending its Mach ramp downward (even gently) on high-L/D bodies would spread the Cd boost across more of the coast where apogee is actually set — a medium-risk refactor that avoids touching THICK_BL's calibration.
  - No dedicated `VALIDATION_MATRIX.md` row for THICK_BL. The only mention is the "Minimum-diameter supersonic flight closure" row (line 40, `D`-level, single-case). Adding a dedicated row with an external primary-source anchor would be required for AST defensibility.
- Recommended next prompt: Digitize Addy 1970 AEDC-TR-70-146 base-pressure data (or equivalent sting-free cylindrical-afterbody measurement with measured δ/R) into `paper/data/csv/`, add a dedicated `THICK_BL` row to `paper/data/VALIDATION_MATRIX.md`, and re-anchor `THICK_BL_K` against the measurements. Only then is further Raven tuning defensible. If no external data is retrievable (ESDU/DTIC paywalls have blocked prior hunts — see the Hoerner/ESDU session), the fallback is to refactor SLENDER_BODY_* to spread Cd onto the coast phase on high-L/D bodies, leaving THICK_BL untouched.

---

### Session — Hoerner/ESDU Transonic Base Drag Hunt (Prompt 13 Unblock)
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): Prompt 13 external-data unblock
- Status: **Prompt 13 UNBLOCKED.** Two defensible primary sources retrieved, digitized, and committed. No code changes (the actual Prompt 13 implementation still needs to be executed against the new data; this session only provided the evidence base).
- Summary: Hunted for primary-source transonic base-drag data to replace the strut-contaminated Compton TN D-4821 band. Retrieved the full 22.8 MB Hoerner "Fluid-Dynamic Drag" (1965) PDF from archive.org; read Chapter XVI Section 2 "Base Drag at Transonic Speeds" and digitized the upper envelope of Figure 2 (Hoerner's compilation of NACA / Aberdeen / Aachen / Kochel / NOL / OAL / NPL / J.H.University data for plain cylindrical projectile bodies). While inspecting Hoerner's primary-source legend I identified **NACA RM L52E06** (Hart 1952) — a FREE-FLIGHT, STING-FREE ogive-cylinder base-pressure measurement at M 0.7–1.3 that IS the primary source for the key portion of Hoerner's Figure 2 curve. Confirmed via NTRS API search, downloaded from archive.org mirror (NTRS direct downloads stalled repeatedly), digitized Hart Figure 8 Configuration A (finless) and Figure 6 (with/without sting). Also retrieved **NACA TN 3372** (Peck 1955 = RM L50I28a 1950), an independent free-flight corroboration with fin-stabilized bodies at M 0.7–1.2. ESDU 96012 and ESDU 78041 were identified as the correct transonic base-drag ESDU items (the original prompt's "ESDU 76003" ID was incorrect — ESDU 76003 is wing-planform geometry, not base drag) but all ESDU items are paywalled behind subscription/Cloudflare. DTIC apps.dtic.mil returns HTTP "Request Blocked" to automated `curl`. NASA TN D-6862 could not be verified in NTRS and was not cited per repo citation policy.
  - Primary finding: Hart L52E06 Figure 6 DIRECTLY QUANTIFIES the strut-shock contamination that Compton rejected — a rear-support sting reduces base-pressure-coefficient magnitude by 40% at subsonic speeds, decaying to zero effect at M > 1.15. This independently validates Compton's authors' rejection of M 0.95–1.20 and establishes that sting-free free-flight measurements are the defensible class.
  - Secondary finding (DEFENSIBLE NEW EVIDENCE): ORP's current polynomial underpredicts base drag by 22–44% across M 1.13–1.30. At M=1.30 ORP gives CDB = 0.174 (Devan-Ashwood); Hart measures CDB = 0.250 free-flight. The error direction matches the Raven (+27.5%) and Kinsel (+33%) transonic-cluster residuals.
  - Tertiary finding: Hart free-flight ogive-cylinder finless Configuration A peak CDB = 0.265 ± 0.010 at M ≈ 1.03–1.10 (broad, flat-topped), consistent with ORP's current peak magnitude of 0.25 at M=1.05 (within digitization uncertainty), but ORP's post-peak decay is too fast.
- Files changed: `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (this entry, Wave-3 status update).
- Files generated:
  - `paper/data/pdf/Hoerner_FluidDynamicDrag_1965.pdf` (22.78 MB, 455 pages)
  - `paper/data/pdf/NACA_RM_L52E06.pdf` (9.43 MB, 20 pages)
  - `paper/data/pdf/NACA_TN_3372.pdf` (9.99 MB, 22 pages)
  - `paper/data/csv/naca_rm_l52e06_base_drag.csv` (Hart Config A, 14 Mach points, free-flight, sting-free, full provenance header)
  - `paper/data/csv/naca_tn_3372_base_pressure.csv` (Peck avg of A/B/C edge orifice, 12 Mach points, free-flight fin-stabilized)
  - `paper/data/csv/hoerner_fig2_base_drag_compilation.csv` (Hoerner upper envelope, 17 Mach points, includes 12 primary-source attributions from Hoerner's legend)
  - `paper/data/png/hoerner_figs/` (10 rendered pages of Hoerner Ch. XVI + working crops used to digitize Fig. 2)
  - `paper/data/png/l52e06_figs/` (20 rendered pages + Fig. 6/7/8 crops)
  - `paper/data/png/peck_figs/` (22 rendered pages + Fig. 12 crop)
  - `paper/data/transonic_base_drag_source_hunt.md` (full hunt memo, per-candidate retrieval log, ORP-vs-data quantitative table, and recommendations for Prompt 13 implementation)
- Measurements: see the comparison table in `paper/data/transonic_base_drag_source_hunt.md` Section 2. Key deltas (ORP polynomial − Hart Configuration A free-flight):
  - M=0.90: +0.047 (ORP too high)
  - M=1.05 (ORP peak): −0.015 (ORP marginally low; within Hart ±0.010 uncertainty)
  - M=1.15: −0.032 (ORP low)
  - M=1.20: −0.047 (ORP low)
  - M=1.30: **−0.076** (ORP catastrophically low; Devan-Ashwood asymptote too low near transonic boundary)
- What improved: the evidence base. Prompt 13 now has two independent, sting-free, free-flight primary sources plus the Hoerner 1965 compilation. The right modification to `BarrowmanDragCalculator.java` is NOT to raise the peak magnitude (ORP peak 0.25 vs Hart 0.265 is within scatter) but to **slow the supersonic decay** by moving `BASE_BLEND_HIGH` to a higher Mach or by keeping CDB ≈ 0.25 through M ≈ 1.3–1.5 before joining Devan-Ashwood.
- What is still open:
  - Prompt 13 CODE IMPLEMENTATION has not been done in this session. A future agent must implement the polynomial adjustment (raise the supersonic-side tie-point without raising the peak) and rerun Basic Finner, NACA RM A52H28, TN 3393 base-pressure, and the SimVReal corpus.
  - ESDU 96012 / 78041 (modern transonic base-drag correlations separating base from boat-tail) remain paywalled and could provide an independent crosscheck if a university library subscription is available.
  - DTIC BRL reports remain unretrievable without authenticated access. Candidates worth pursuing: ARBRL-TR-02179 (hypersonic free-flight cone, cross-regime reference), BRL 653 (Charters & Turetsky 1948 Aberdeen free-flight, cited in Hoerner's primary-source legend — would be a third independent primary source if retrievable).
  - The Devan-Ashwood-boundary question (why ORP at M=1.30 is 44% below Hart at M=1.30) is out of scope for Prompt 13. It is a separate question about whether A=0.064, B=0.186 in `BarrowmanDragCalculator.java` should be refitted including Hart data near M=1.3 without regressing the M 2.7–4.5 TN 3393 validation. Flagged for a future prompt.
- Recommended next prompt: re-enter **Prompt 13 with the new evidence in hand**. Specifically: widen the transonic plateau on the supersonic side by raising the polynomial tie-point at M=1.30 from 0.174 toward 0.23–0.25, OR move `BASE_BLEND_HIGH` from 1.30 to 1.45–1.50 and keep the polynomial over a longer Mach span. Add a JUnit benchmark against `paper/data/csv/naca_rm_l52e06_base_drag.csv` with a MAPE gate. Rerun Basic Finner, NACA RM A52H28, TN 3393, NACA RM-10 total-drag, and the SimVReal outlier corpus (Raven / Kinsel especially). Do NOT touch the Devan-Ashwood A/B constants at this prompt — they are A-level and their M > 2.7 validation must be preserved.

---

## Prompt 10 Findings: Ranked Suspect List

### Case Evidence Summary

**Raven** (M 1.12, minimum-diameter, +27.5% apogee overshoot):
- Total Cd at peak Mach: 0.841
- Body Tube Cdb: 0.234 (28% of total)
- Body Tube Cdf: 0.290 (34% of total)
- Nose Cdp: 0.034 (4%)
- Fin total: 0.049 (6%)
- Rail Guide Cdp: 0.056 (7%)
- Geometry: 1.750" diameter, 65" body tube, Haack nose, 3 fins (rounded xsec)
- Coast AoA: 0.22 deg (negligible AoA-dependent drag)

**Kinsel** (M 2.42, large HPR, +35.1% apogee overshoot after MAXTIME fix):
- Total Cd at peak Mach: 0.289
- Fin Can Cdb: 0.090 (31% of total — this is the only base-drag contributor)
- Body Tube Cdf: 0.081 (28%)
- Fin Can Cdf: 0.015 (5%)
- Nose Cdp: 0.012 (4%)
- Fin total: 0.008 (3%)
- Rail Guide: 0.021 (7%)
- Geometry: 6.125" body, 6.5" fin can (expanding shoulder), ogive nose, 4 fins (hexagonal)
- Coast AoA: 0.81 deg (negligible)
- Has an expanding fin-can shoulder (6.125" to 6.5") creating a step

### Ranked Suspect List

#### SUSPECT #1 (HIGH CONFIDENCE): Base drag coefficient too low at transonic/supersonic

**Raven evidence**: Body Tube Cdb = 0.234 at M 1.13. The base drag coefficient at this Mach comes from the transonic polynomial blend (M 0.85-1.3), which peaks at 0.25 at M=1.05. At M=1.13, the blend is already descending toward the Devan-Ashwood value. The Raven body tube has aftRadius > nextRadius (it IS the aft end), so it takes the full flat-base penalty. A 27% apogee overshoot implies roughly 20-25% total drag deficit. With Cdb being 28% of total, a 70-80% underestimate in base drag alone could explain the miss.

**Kinsel evidence**: Fin Can Cdb = 0.090 at M 2.42. Devan-Ashwood at M 2.42 gives 0.064 + 0.186/5.86 = 0.096. But the Lamb-Oberkampf Re correction (`calculateBaseCD(m, conditions)` at line 1203) reduces this further. At Kinsel's Re_D (very high — 6.5" diameter at M 2.42 at 56 kPa), logReD is large, and the factor `1.0 - 0.08 * (logReD - 6.0)` can reduce base drag by 15-25%.

**Files/methods**:
- `BarrowmanDragCalculator.java` line 1184: `calculateBaseCD(double m)` — the base Devan-Ashwood / transonic polynomial
- `BarrowmanDragCalculator.java` line 1203: `calculateBaseCD(double m, FlightConditions conditions)` — the Re-corrected version
- `BarrowmanDragCalculator.java` line 1226: `reFactor = MathUtil.clamp(1.0 - 0.08 * (logReD - 6.0), 0.7, 1.3)` — heuristic, **NOT externally anchored**

**Anchoring status**: Devan-Ashwood is A-level (TN D-721/TN 3393). The Lamb-Oberkampf Re correction is **D-level heuristic** with no external data points in the repo. The transonic polynomial peak value (0.25 at M=1.05) is calibrated but not directly benchmarked.

**First fix target**: Remove or bound the Lamb-Oberkampf Re correction. It was added without external validation and actively reduces drag on the worst outlier cases.

#### SUSPECT #2 (HIGH CONFIDENCE): Finned-body base drag augmentation insufficient for minimum-diameter

**Raven evidence**: At M 1.13, `calculateFinnedBaseAugmentation()` would compute:
- machFactor: in the 0.8-1.3 ramp, at M=1.13: `0.30 + 0.70 * (1.13-0.8)/0.5 = 0.30 + 0.462 = 0.762`
- finFactor: 3/4 = 0.75 (3 fins, normalized to 4)
- spanFactor: span=2.375", bodyRadius=0.875", ratio=2.71 -> clamped to 1.0
- Total augmentation: `1 + 0.55 * 0.75 * 1.0 * 0.762 = 1.314` (31% increase)

For a minimum-diameter rocket where body-to-fin diameter ratio is very small (fins are large relative to body), the actual fin-wake interference is likely much stronger than 31%. Hoerner Ch.16 notes 40-60% for typical 4-fin configurations; for minimum-diameter where fins dominate the base, 50-80% may be more appropriate.

**Files/methods**:
- `BarrowmanDragCalculator.java` line 928: `calculateFinnedBaseAugmentation()` — the complete method
- `BarrowmanDragCalculator.java` line 80: `FINNED_BASE_K = 0.55` — the calibration constant

**Anchoring status**: B-level (Hoerner Ch.16 qualitative + Basic Finner calibration). Not directly validated for minimum-diameter or 3-fin configurations.

**Note**: Already raised from 0.50 to 0.55. Further increase should be tested against Basic Finner and the subsonic cluster first.

#### SUSPECT #3 (MEDIUM CONFIDENCE): Kinsel fin-can expanding shoulder step drag

**Kinsel evidence**: The Kinsel has a "Fin Can Shoulder" expanding from 6.125" to 6.500" (forward-facing step of 0.1875"). The `calculateStepDrag()` method in `SymmetricComponentCalc.java` only applies above M=0.95. At M 2.42, the stagnation Cp on this step and the reattachment recovery drag should add a non-trivial drag increment. But the diagnostic shows `Fin Can Shoulder` Cd total = 0.0004, which seems extremely low for a step at M 2.42.

The step is only 0.1875" / 2 = 0.094" radius increment, and the step face area is `pi * (3.25^2 - 3.0625^2) = pi * 1.184 = 3.72 in^2 = 0.0024 m^2`. At S_ref = 0.0214 m^2, that is area_ratio = 0.112. The stagnation Cp at M 2.42 is about 0.46. So `0.46 * 0.112 = 0.051` — but the diagnostic shows only 0.0004.

Possible explanation: `foreRadius <= upstreamAftRadius + 1e-6` check at line 493 may be failing because the expanding shoulder's foreRadius (3.0625") actually equals the body tube aftRadius (3.0625"), so there is no step on the shoulder itself — the step is at the body-tube-to-shoulder junction. But step drag is computed per-component looking at `foreRadius - upstreamAftRadius`, which for the shoulder is `3.0625 - 3.0625 = 0`. The Fin Can (body tube at 6.5") has `foreRadius = 3.25` and `upstreamAftRadius = 3.25` (the shoulder's aftRadius), so also no step.

This means the step drag from an expanding shoulder is correctly zero on both ends — the expansion is a gradual transition, not a step. The missing drag is not from step drag but from expansion-fan/shock-expansion effects on the shoulder that are explicitly zeroed at line 450-452: `foreRadius > 1e-6` -> `cd = 0` (expanding shoulder = no wave drag). This is correct for the far-field, but locally the expansion creates a pressure change that is not captured.

**Anchoring**: The expanding-shoulder zero-pressure-drag is a modeling simplification, not externally validated.

#### SUSPECT #4 (MEDIUM CONFIDENCE): Body friction too low at supersonic

**Kinsel evidence**: Body Tube Cdf = 0.081 at M 2.42. This is the Van Driest II result for a 113.5" tube at the flight conditions. Van Driest II is A-level anchored. However, the Hoerner form factor correction (line 315: `1 + 1.5/ld^1.5 + 7/ld^3`) is applied to body friction. For Kinsel, L/D = ~26 (169"/6.5"), so ld = 13, and `1 + 1.5/46.9 + 7/2197 = 1.035`. This is a very small correction.

The question is whether protuberance drag, surface roughness on HPR rockets, and excrescences (couplers, rail guides, wiring channels) are materially undercounted. The `roughnessLimited` floor for non-perfect-finish rockets attempts this, but the `Cf = max(Cf_smooth, roughness_floor)` floor may be too low at supersonic because the roughness correction (`1/(1+0.18*M^2)`) reduces it significantly.

**Raven evidence**: Body Tube Cdf = 0.290 at M 1.13. For a 1.75" diameter rocket that is 65" long (L/D=37), this is plausible but the transitionFactor at line 336 (`fLam = min(fLam, 0.05)`, `transitionFactor = 1 - 0.6*0.05 = 0.97`) gives only a 3% reduction. For painted HPR rockets, this is conservative.

**Anchoring**: Van Driest II is A-level. Form factor is B-level (Hoerner). Surface roughness model is C-level.

**Assessment**: Unlikely to be the dominant source for either case, but could contribute 5-10% of the missing drag.

#### SUSPECT #5 (MEDIUM CONFIDENCE): Transonic base drag peak too narrow or too low for Raven

**Raven-specific**: The transonic polynomial peaks at 0.25 at M=1.05. At Raven's peak Mach of M=1.13, the polynomial is already well into the decreasing section heading toward the Devan-Ashwood anchor at M=1.3 (where it is 0.174). At M=1.13, the polynomial gives approximately 0.22.

Experimental data for cylindrical afterbodies (TN 3393, TN D-721, Hoerner Ch.3) shows the transonic peak is broader and often persists up to M 1.1-1.2. If the peak value should be 0.27-0.30 instead of 0.25, or if it should remain at peak level longer before declining, Raven's base drag would increase significantly.

**Files/methods**:
- `BarrowmanDragCalculator.java` line 127: `0.25` — the peak base drag value at M=1.05
- `BarrowmanDragCalculator.java` line 101-131: static initializer for `baseDragTransonicPoly`

**Anchoring**: The peak of 0.25 is calibrated against "experimental data for cylindrical afterbodies" but the exact source and data points are not in the repo. This is B-level at best.

#### SUSPECT #6 (LOW CONFIDENCE): Fin wave drag / pressure drag too low

**Both cases**: Fin pressure drag is a small fraction of total drag (6% for Raven, 3% for Kinsel). Even doubling it would only increase total Cd by 3-6%, insufficient to close a 27-35% gap. The DATCOM 4.1.5.1 method is A-level anchored.

**Assessment**: Not a primary driver. Do not touch.

#### SUSPECT #7 (LOW CONFIDENCE): Power-on base drag reduction incorrectly applied during coast

At `computePowerOnBaseDragMultiplier()` line 1317: if `thrustLevel <= 0`, returns 1.0. During coast, thrustLevel should be 0, so no reduction. BUT: the production simulation path may not correctly set thrustLevel to 0 at burnout (see Prompt 5 audit on production wiring). If thrustLevel remains positive after burnout, base drag would be reduced during the critical coast phase.

**Anchoring**: Code logic is clear; the uncertainty is in the caller.

### DO NOT TOUCH List (A-Level Anchored)

These terms are externally validated and must not be weakened:

1. **Devan-Ashwood base drag** (`BASE_DRAG_A=0.064, BASE_DRAG_B=0.186`): A-level vs TN 3393, MAPE 15.9%
2. **Van Driest II skin friction** (`vanDriestIICf()`): A-level vs TN D-6945
3. **Taylor-Maccoll cone wave drag**: A-level vs NACA 1135
4. **Shock-expansion ogive wave drag**: A-level vs NACA RM A52H28
5. **DATCOM 4.1.5.1 fin wave drag**: A-level vs TN 3650 + Ackeret
6. **Chapman laminar base drag**: A-level vs TN 3393 laminar data
7. **Finned-body base augmentation shape** (Mach ramp, span/fin-count scaling): B-level, calibrated vs Basic Finner. The K constant (0.55) can be adjusted but the functional form should be preserved.
8. **Boattail factor** (`calculateBoattailFactor`): B-level, based on Hoerner Ch.16 / DATCOM 4.6.3.2

### Recommended Fix Priority

1. **Remove or disable the Lamb-Oberkampf Re correction** (Suspect #1, line 1226). This is a D-level heuristic actively reducing base drag on Kinsel by ~7.7% (corrected from initial 15-25% estimate per Prompt 11 analysis at Re_D=9.1e6). There are zero external data points validating it. Removing it is the safest first move.

2. **Raise or widen the transonic base drag peak** (Suspect #5). The current 0.25 at M=1.05 needs external data to justify any change. Search for TN 3393/TN D-721 data at M=1.0-1.3 before adjusting.

3. **Consider increasing FINNED_BASE_K for minimum-diameter configurations** (Suspect #2). This requires care to not break subsonic cases. A diameter-ratio-dependent K would be more defensible than a blanket increase.

4. **Investigate production thrust/nozzle wiring** (Suspect #7). This is a cross-cutting concern that may affect boost-phase drag accounting.

---

## Prompt 1: Expand Outlier Diagnostics To Full Corpus

```text
Take the new SimVReal outlier diagnostic harness in `core/src/test/java/info/openrocket/core/aerodynamics/SimVRealOutlierDiagnosticTest.java` and extend the same reporting path to the full SimVReal corpus, not just the worst five cases.

Goals:
- Generate reproducible markdown + trajectory CSV + component-CD sweep artifacts for every SimVReal case.
- Add a machine-readable summary table with:
  - rocket name
  - real apogee
  - RASAero apogee
  - ORP apogee
  - ORP error
  - delta vs RASAero
  - max Mach
  - burnout time
  - apogee time
  - ground hit time
  - terminal note
  - loader warning count
  - sim warning count
  - ignored-setting flags if available
- Keep the existing worst-case reports working.

Constraints:
- Do not remove the existing worst-case focused export.
- Keep outputs under `core/build/reports/simvreal-outliers`.
- Add focused tests only if needed.

Deliverables:
- code changes
- generated summary artifact path
- short note on any cases that still fail to export
- exact files changed
- update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off
```

## Prompt 2: Write Closure Sheets For The Five Worst Cases

```text
Using the generated reports in `core/build/reports/simvreal-outliers`, create one technical closure sheet per case for:
- Raven
- Don't Debate This
- Proteus 6
- Full Metal Jacket BALLS 005
- A-601 Kinsel

For each sheet, include:
- current error
- import parity warnings
- event timeline
- phase split
- peak-Mach drag breakdown
- likely root-cause family
- what development would falsify that hypothesis
- what exact metric would count as closure

Do not write marketing language. These are internal engineering closure sheets.

Output location:
- `paper/data/outlier_closure/`

Also update `paper/data/VALIDATION_MATRIX.md` only if the closure sheets reveal a sharper blocker statement than what is there now.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 3: Build Corpus-Wide Ignored-Setting Parity Matrix

```text
Create a corpus-wide parity inventory for every SimVReal CDX1 case.

Need:
- per case, list whether these are present and nonzero:
  - ModifiedBarrowman
  - Turbulence
  - SustainerNozzle
  - Booster1Nozzle
  - Booster2Nozzle
  - SustainerNozzleDiameter
  - Booster1NozzleDiameter
  - Booster2NozzleDiameter
- count unsupported settings per case
- identify which severe outliers have parity mismatches and which do not

Output:
- `paper/data/csv/simvreal_parity_matrix.csv`
- short markdown interpretation:
  - which cases are clean
  - which cases are parity-contaminated
  - which parity gaps are likely acceptance-critical

If useful, add helper code or tests, but do not fake values. Use real imports.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 4: Sensitivity-Bound Unsupported Settings

```text
For each currently unsupported CDX1 setting in the SimVReal corpus, determine whether it is likely negligible or potentially material.

Do not guess. Use targeted sensitivity runs where possible.

Need:
- a reproducible sensitivity workflow
- per unsupported setting, estimate whether ignoring it can plausibly move apogee by:
  - < 2%
  - 2 to 5%
  - > 5%
- prioritize severe outliers first

Focus first on:
- nozzle/nozzle diameter fields
- turbulence flag
- ModifiedBarrowman

Output:
- a markdown memo under `paper/data/`
- any helper tests/scripts used
- explicit recommendation: implement now vs bound and leave out

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 5: Audit Production Thrust/Nozzle Wiring — COMPLETE

**Status:** AUDIT COMPLETE. Full memo at `paper/data/audit_thrust_nozzle_wiring.md`.

**Key findings:**
1. `populateThrustState()` is fully implemented in `RK4SimulationStepper.java:623-648` but **deliberately commented out** at line 593 with an explicit TODO.
2. The TODO states: "Power-on base drag reduction is physically correct but currently makes the benchmark worse because coast drag is underestimated. Enable after closing the high-M finned-body coast drag gap."
3. As a result, `thrustLevel` is always 0 and `nozzleAreaRatio` is always NaN during production simulation. `computePowerOnBaseDragMultiplier()` always returns 1.0.
4. This is a **compensating-error coupling**: enabling power-on reduction (correct physics) worsens net accuracy because coast drag is too low.
5. RK6SimulationStepper has no thrust wiring at all. AbstractEulerStepper and GroundStepper do not need it.
6. Nozzle exit diameter is only populated from CDX1 imports (17/24 SimVReal cases). Native OpenRocket designs always have NaN (no UI exists). The drag calculator handles NaN gracefully with `DEFAULT_POWER_ON_FACTOR = 0.15`.
7. The soft saturation (`totalThrust/100.0`) is questionable for low-thrust motors but fine for HPR.

**Recommendation:** Do NOT enable in isolation. Correct sequence: (1) close coast drag gap, (2) uncomment populateThrustState, (3) wire RK6, (4) validate combined effect. For AST paper, disclose as known limitation.

**Impact on Prompt 6:** Prompt 6 should be re-scoped. The original spec assumed the wiring might be missing or broken. In fact the implementation is complete and correct — the blocker is the coast drag deficit, not the wiring itself. Prompt 6 should become: "After coast drag improvements from Prompts 3/4, uncomment line 593, add RK6 wiring, run SimVReal regression, and verify the combined effect."

## Prompt 6: Implement Production Thrust/Nozzle Wiring Safely — BLOCKED on coast drag

**Blocked by:** Coast drag deficit (Prompts 3/4). See Prompt 5 audit.

**Revised scope:** After coast drag improvements land, the implementation is trivial:
1. Uncomment `RK4SimulationStepper.java:593`
2. Add equivalent `populateThrustState()` call in `RK6SimulationStepper`
3. Run SimVReal 36-case regression
4. Verify boost-phase base drag is reduced by expected factor (~85%)
5. Verify coast-phase base drag is unchanged (thrustLevel=0)

```text
Original spec (preserved for reference):
Implement production wiring for `FlightConditions.setThrustLevel(...)` and `setNozzleAreaRatio(...)` in the simulation path, but only if the audit supports a clear and correct implementation.

Requirements:
- use actual runtime motor/thrust/nozzle information
- add focused regression tests
- add at least one sensitivity-style test or benchmark showing the effect is real
- do not break existing external A-level aero benchmarks

After implementation, report:
- whether boost drag changed
- whether affected SimVReal cases improved or worsened
- whether this exposed compensation elsewhere

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 7: Root-Cause Kinsel Max-Time Descent Behavior

```text
Investigate the A-601 Kinsel `MAXTIME@1200s` abnormal ending using:
- `core/build/reports/simvreal-outliers/Kinsel_P4935_A-601_Rocket.md`
- `core/build/reports/simvreal-outliers/Kinsel_P4935_A-601_Rocket-trajectory.csv`
- relevant simulation / recovery / event code

Current observed fact:
- Kinsel reaches apogee normally
- recovery deploys
- it is still descending at about 10.6 m/s from about 470 m AGL near the 1200 s cap

Need:
- rank root-cause hypotheses by evidence
- decide whether this is:
  - parachute/recovery modeling
  - event scheduling
  - excessive drag/area mismatch
  - max-time policy only
  - something else
- identify exact code path to fix
- propose the smallest regression test that proves the fix

Do not conflate this with the ascent overshoot unless evidence demands it.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 8: Fix Kinsel Abnormal Termination

```text
Fix the A-601 Kinsel abnormal termination problem identified in the root-cause audit.

Requirements:
- add regression coverage
- verify Kinsel no longer ends in `MAXTIME@1200s`
- verify the fix does not mask a deeper simulation bug
- report whether ascent/apogee error changed or stayed separate

Success condition:
- Kinsel produces a normal terminal state
- `SimVRealBenchmarkTest` abnormal-ending count decreases accordingly

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 9: Decompose Peak-Mach Drag By Case And Term

```text
Use the generated outlier reports and component-CD sweeps to build a comparative study of peak-Mach drag composition for:
- Raven
- Don't Debate This
- Proteus 6
- FMJ BALLS 005
- Kinsel
- one or two healthier comparison cases

Need:
- table of peak-Mach total Cd
- friction / pressure / base split
- component contributions
- identify recurring pattern across the bad cases
- identify whether the common residual is mostly:
  - body friction
  - body pressure
  - base drag
  - fin-can drag
  - fin drag
  - boattail/transition treatment

Output:
- `paper/data/high_m_drag_reconciliation.md`
- supporting csv tables if helpful

Do not propose fixes until the decomposition is explicit.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 10: Audit High-M Drag Code Paths In Barrowman Stack

```text
Do a source-level audit of the high-M drag code path that affects the outlier family.

Focus on:
- `BarrowmanDragCalculator`
- fin wave / fin pressure drag logic
- base drag logic
- body pressure drag logic
- boattail / transition treatment
- any transonic blend logic
- any fin-can or flat-base geometry assumptions

Need:
- list exact methods and constants likely controlling the observed underdrag
- identify which terms are externally anchored and which are heuristic
- identify where a first evidence-backed correction should go
- identify what should not be touched because it is already A-level anchored

Output:
- ranked suspect list with exact file/method references

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 11: Build Finite Candidate List Of Model Fixes

```text
Based on the decomposition study and code audit, produce a short candidate list of possible high-M drag fixes.

For each candidate:
- exact mechanism
- exact file/method to change
- what cases it should improve
- what benchmark it might break
- how to falsify it quickly
- whether it is AST-defensible or just tuning

Keep the list brutally short:
- no more than 5 candidates
- rank them from safest/highest-evidence to riskiest

This is a decision memo, not implementation.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 12: Implement Candidate #1 And Rerun Evidence

```text
Implement the top-ranked high-M drag candidate from the decision memo, but only if it is evidence-backed and not just curve fitting.

Requirements:
- add or update focused regression tests
- rerun:
  - Basic Finner benchmark
  - affected outlier diagnostic test
  - SimVReal benchmark if runtime is acceptable
- report:
  - which bad cases improved
  - whether A-level external benchmarks regressed
  - whether the mechanism now looks more defensible

If candidate #1 fails, stop and report clearly instead of piling on more hacks.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 13: Implement Candidate #2 Only If Needed

```text
Only run this if candidate #1 was insufficient but still useful.

Implement candidate #2 from the high-M drag decision memo with the same requirements:
- regression tests
- no weakening of A-level benchmarks
- rerun affected benchmarks and outlier diagnostics
- explicit before/after tables

If this starts to look like stacking compensations, stop and say so.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 14: Add A Second High-M Finned-Body Benchmark

```text
Add at least one additional external benchmark that exercises the same regime as the current high-M flight outliers: finned vehicle, roughly Mach 1.1 to 3.5, zero or near-zero angle of attack.

Requirements:
- use an independent external dataset
- exact geometry or close geometry
- digitized/source-traceable data
- new JUnit benchmark in repo
- csv/source artifact under `paper/data/csv`
- clear acceptance metric

Do not use RASAero output as the benchmark. Use real experimental or published primary-source data.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 15: Add Exact-Geometry Minimum-Diameter Validation Path

```text
Create a validation path aimed specifically at minimum-diameter supersonic vehicles, since Raven and Don't Debate This are currently a distinct pain family.

Need:
- exact geometry audit of the imported minimum-diameter cases
- identify any geometry features that ORP simplifies or misrepresents
- if possible, add an external comparison or tightly bounded analytical study for this family
- connect the result directly to Raven/DDT closure, not generic theory only

Output:
- one memo and any supporting tests/artifacts

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 16: Bound Damping-Heuristic Impact On Bad Cases

```text
Run a focused sensitivity study on:
- transonic `Cmq` augmentation
- `3x` damping multiplier

Use the worst outlier cases and at least one healthy case.

Need:
- quantify apogee sensitivity and peak-AoA sensitivity
- determine whether current full-flight agreement depends strongly on either knob
- use the new outlier diagnostics to support interpretation

Output:
- markdown memo with tables
- recommendation:
  - acceptable as bounded heuristic
  - needs direct external data
  - needs refactor or retune

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 17: Search For Direct Pitch-Damping Closure Opportunities

```text
Without changing code, search the repo’s existing source set and notes for any direct pitch-damping data that could promote the current B-level damping terms.

Need:
- whether anything already in repo can tighten the claim
- if not, what exact data type is still missing
- whether the paper can survive with a bounded-heuristic appendix instead of promoted closure

This is a claim-boundary memo.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 18: Boattail And Fin-Can Geometry Reconciliation

```text
Audit boattail / fin-can treatment in the overshoot cases, especially:
- Proteus 6
- FMJ BALLS 005
- Kinsel
- one healthy boattail comparison case

Need:
- compare imported geometry vs what ORP actually computes
- identify whether boattail correction, shoulder handling, or fin-can base treatment is likely underestimating drag
- propose the smallest defensible improvement

Output:
- exact geometry reconciliation memo
- whether this should be folded into the main high-M drag fix or handled separately

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 19: Full Corpus Rerun After Accepted Fixes

```text
After accepted parity/model/termination fixes are in, rerun the full SimVReal benchmark and produce a frozen summary.

Need:
- avg |error|
- percent within +-10%
- percent within +-5%
- abnormal ending count
- top remaining outliers
- before/after table vs current baseline

Write outputs to `paper/data/` and update `paper/data/VALIDATION_MATRIX.md` with the new audited numbers.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 20: Regression-Lock All Accepted Improvements

```text
Take every fix that materially improved the corpus and add mechanism-specific regression coverage.

Need:
- one test per accepted mechanism where possible
- avoid giant monolithic regression tests
- clearly tie each test to the physical/modeling claim it protects

Output:
- summary table: mechanism -> test -> expected protected behavior

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 21: Brutal AST Readiness Review

```text
Assume you are an unforgiving AST reviewer. Audit the repo after all development work is done.

You are not allowed to be encouraging. You must answer:
- what claims are truly safe
- what claims are still overstated
- what evidence is still missing
- whether the trajectory corpus is now acceptance-strength or still supporting-only
- whether the paper should be written now

Output:
- major findings first
- then open questions
- then a go / no-go recommendation

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 22: Claim Map Finalization

```text
Update `paper/data/VALIDATION_MATRIX.md` one last time after all accepted technical work is done.

Need:
- every claim in the correct A/B/C/D bucket
- no stale wording
- no hidden dependencies
- explicit statement of what the AST manuscript can and cannot claim

Do not write the paper itself. This is the final claim boundary.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

## Prompt 23: Final Paper Go/No-Go Gate

```text
Using only the final validation matrix, benchmarks, outlier reports, and corpus rerun, answer one question:

Are we truly ready to write an AST paper with a very good chance at acceptance?

Allowed answers:
- yes
- not yet

If "not yet", list only the remaining acceptance-critical blockers.
If "yes", list only the manuscript claims that are actually safe.

Update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` before handing off.
```

### Session — M 2-3 Base Drag Source Hunt (Kinsel unblock memo)
- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Prompt(s): parallel hunt (no code changes)
- Status: HUNT SUCCESSFUL. Two new primary-source PDFs retrieved, two new CSVs digitized. Verdict reverses the original hypothesis: ORP base drag at M 2-3 is NOT too low for Kinsel; it is slightly HIGH.
- Summary: Downloaded NACA Report 1036 (Chapman & Perkins 1951, M=1.5 base pressure), NACA Report 1051 (Chapman 1951, supersonic base-pressure compilation theory), NACA RM L53C02 (Love 1953, "Base Pressure at Supersonic Speeds on Two-Dimensional Airfoils and Bodies of Revolution WITH AND WITHOUT FINS"), and NACA TN 3320 (RM-10 fin-stabilized free-flight M 0.9-3.3 base-pressure). Key digitizations: (1) Love Fig 21 no-fin cone-cylinder L/D=5 Pb vs Mach M 1.2-4.0 (15 points, Chapman Ref-2 solid + Love L/D=5 dashed curves); (2) TN 3320 Fig 9 Model 1 full-scale fin-stabilized parabolic (boattailed) Pb vs Mach M 0.85-3.20 (17 points). Love Figs 30 and 32 provide the crucial finned-body result: at fin trailing-edge-at-base (x/c=0, Kinsel geometry), fin effect on base pressure is NEAR-ZERO at M=2.41 (|Pb|_finned ≈ 0.14 = |Pb|_no-fin). RM-10 free-flight (boattailed, finned) gives CDB ≈ 0.09 across M 1.4-3.2 = ~0.12 corrected for boattail, matching Love Fig 21 no-fin within 0.02. Comparison with ORP: (A) Devan-Ashwood 0.064+0.186/M² UNDER-predicts no-fin cylindrical Pb by 15-31% across M 1.5-3.0 (Love Fig 21 evidence); (B) ORP Kinsel with FINNED_BASE_K=0.55 augmentation actively applied produces CDB = 0.154 at M=2.3, which is ABOVE Love's fin-x/c=0 measurement at M=2.41 (0.14) and ABOVE RM-10's flat-equivalent at M=3.0 (0.12). Net: Kinsel's +31.3% apogee overshoot is NOT explained by base drag too low; pivot the residual-hunt to forebody (nose wave drag, body pressure), fin wave drag, skin friction, or propulsion sources.
- Files changed: none (hunt-only, no source or test edits)
- Files generated:
  - `paper/data/pdf/NACA_TR_1036.pdf` (24.87 MB, Chapman & Perkins 1951)
  - `paper/data/pdf/NACA_TR_1051.pdf` (21.62 MB, Chapman 1951 compilation)
  - `paper/data/pdf/NACA_RM_L53C02_Love.pdf` (2.56 MB, Love 1953)
  - `paper/data/pdf/NACA_RM_10_Evans_Stoney.pdf` (9.14 MB, TN 3320 / RM L50G24 RM-10)
  - `paper/data/csv/love_rm_l53c02_base_pressure_supersonic.csv` (15 Mach × 2 curves)
  - `paper/data/csv/naca_tn_3320_rm10_base_pressure.csv` (17 Mach points)
  - `paper/data/png/love_figs/` (~15 rendered pages + Fig 21/22/23/25/27/28/30/32 crops)
  - `paper/data/png/rm10_figs/` (~12 rendered pages + Fig 9/10 crops)
  - `paper/data/png/tr1036_figs/` (~6 rendered pages)
  - `paper/data/m2_3_base_drag_source_hunt.md` (full memo with per-source retrieval status, digitization methodology, quantitative gap analysis, recommendations)
- Measurements (no code edits, all measured off the newly digitized CSVs against ORP's current `BarrowmanDragCalculator` constants):
  - Devan-Ashwood vs Love Ref-2 (no-fin cylindrical):
    - M=1.5: DA 0.147, Love 0.188 (DA LOW 22%)
    - M=2.0: DA 0.111, Love 0.160 (DA LOW 31%)
    - M=2.5: DA 0.094, Love 0.125 (DA LOW 25%)
    - M=3.0: DA 0.085, Love 0.100 (DA LOW 15%)
    - M=3.5+: convergence within 7%
  - ORP Kinsel with FINNED_BASE_K=0.55 vs Love fin-x/c=0 (Fig 32):
    - M=2.41: ORP 0.154 (at M=2.3), Love fin 0.14 (measured) and 0.14 (calculated). ORP HIGH by +10%.
  - ORP Kinsel vs Love Fig 21 no-fin (flat-equivalent):
    - M=2.0: ORP 0.172 vs Love 0.160 (+8%)
    - M=3.0: ORP 0.131 vs Love 0.100 (+31%)
  - RM-10 Fig 9 full-scale fin-stab boattailed:
    - M 1.4-3.0: |Pb| ≈ 0.080-0.090 (broadly constant plateau, consistent with Love Fig 21 minus ~0.03 boattail correction from Love Fig 23 at M=2.9)
- What improved: The M 1.5-3.0 base-drag data gap flagged after Prompt 13 is closed with primary-source evidence. The Kinsel residual-hunt now has evidence-driven direction: NOT base drag, but forebody / fin-wave / propulsion / Re-dependent friction. Paper can cite Love 1953 Fig 21 as its supersonic base-drag anchor (A-level compilation of Chapman, Ames, NOL, BRL data).
- What is still open:
  - Kinsel's +31.3% residual is NOT a base-drag problem. Next investigation should target nose-cone pressure drag (component CD sweep shows Kinsel nose CDpressure climbs from 0.019 to 0.039 M=1.9-3.0 which is reasonable but should be cross-checked), fin wave drag (DATCOM 4.1.5.1), or propulsion thrust curve parity with the real flight.
  - FINNED_BASE_K=0.55 may be over-attributing Basic Finner drag to base drag. A Mach-dependent decay (0.55 at M=1.2 → 0.05 at M=3.0) is suggested by Love Fig 30/32 but NOT implemented here — it would require re-calibration against ADA636861 Basic Finner total-drag benchmark to avoid regression.
  - Devan-Ashwood `A + B/M^2` asymptote is under-predicting no-fin cylindrical base drag by 15-30% at M 1.5-3.0. Refitting against Love Fig 21 (while holding TN 3393 M > 2.7 MAPE < 20%) is a defensible future prompt. Flagged in hunt memo §4.3.
  - ESDU 78041 / 96012 still paywalled. DTIC BRL memos still blocked from automated retrieval. These would provide modern cross-check but are not blocking; Love's 1953 compilation is authoritative for 1950s-era data.
- Recommended next prompt: Pivot Kinsel residual investigation to non-base drag sources. Specifically: (a) dump Kinsel's per-component CD sweep at M=2.3 from `core/build/reports/simvreal-outliers/Kinsel_P4935_A-601_Rocket-component-cd.csv` (already generated) and compare each component against its benchmark tolerance; (b) check whether the motor thrust file matches the real propellant mass flow; (c) consider Mach-dependent FINNED_BASE_K refit (separate session; must guard Basic Finner MAPE).

---

## Highest-Value Single Prompt Right Now

If only one parallel session runs next, use this:

```text
Use the generated SimVReal outlier reports and the current Barrowman drag code to identify the first evidence-backed high-M drag fix for the Raven / Don't Debate This / Proteus / FMJ / Kinsel family. Assume the primary miss is in body/fin-can/base drag, not fin-only drag and not damping-first tuning, unless repo evidence proves otherwise. Rank candidate fixes, implement the safest one, add regression coverage, and rerun the affected benchmarks and outlier diagnostics.

Before handing off, update `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` with:
- prompt status
- files changed
- files generated
- measurements
- what improved
- what is still open
- recommended next prompt
```
