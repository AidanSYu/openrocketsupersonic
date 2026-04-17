# A-601 Kinsel — Closure Sheet

## Header

- Case: A-601 Kinsel (large HPR, 4-fin, expanding fin-can shoulder)
- Current error: **+33.0%** apogee overshoot (post-Prompt-12; was +35.1% in the stale diagnostic CSV)
- Status: **OPEN** (worst overall outlier)
- Target: within ±10% (ideally ±5%). Realistic physics-defensible target per decision memo: ~+27% after Candidate #2+#3, still outside 10%.
- Regime: supersonic (peak M = 2.328)
- Source: `core/build/reports/simvreal-outliers/Kinsel_P4935_A-601_Rocket.md`
- Note on data freshness: per-case diagnostic markdown and summary CSV (`simvreal-outlier-summary.csv`) predate Prompt 12. The diagnostic shows +35.1%; the current corpus number after Lamb-Oberkampf Re correction removal is +33.0% (roadmap Prompt 12 session log). Component numbers below are from the stale snapshot; Prompt 18 warns these under-count fin-can augmentation by roughly the Re-correction factor (~0.923 at Kinsel's Re_D, i.e. the unaugmented baseline was ~7.7% higher than the stale CSV shows). The ranking of components is unchanged.

## Import parity warnings

- Parity matrix (`simvreal_parity_matrix.csv` row 24): `ParityClass = CONTAMINATED`, `UnsupportedActiveCount = 2`.
- Loader warnings (Kinsel_P4935_A-601_Rocket.md §"Loader warnings"):
  - `Ignoring unsupported RASAero setting ModifiedBarrowman=True`
  - `Ignoring unsupported RASAero setting Turbulence=True`
  - `Ignoring unsupported RASAero setting SustainerNozzle=3.09`
- Prompt 3 / Prompt 4 bounding (`paper/data/CDX1_UNSUPPORTED_SETTINGS_SENSITIVITY.md`):
  - `SustainerNozzleDiameter` IS correctly applied via `SimulationHandler.setNozzleExitDiameter()`; the `SustainerNozzle=3.09` warning is the *redundant* `<RocketDesign>` copy. Live sensitivity on Kinsel = 0.0% apogee delta.
  - `Turbulence=True` analytical bound: <1.2% apogee (5% laminar cap bounds the impact).
  - `ModifiedBarrowman=True` analytical bound: <2% apogee (ORP Phase 3 provides equivalent corrections and it is a stability-only flag).
- Combined unsupported-setting bound for Kinsel: <4% apogee in the direction that would *increase* drag (reduce apogee). Cannot account for the +33.0% overshoot; it could at best close ~4pp.
- Sim warnings: none.

## Event timeline (from Kinsel_P4935_A-601_Rocket.md)

- t = 0.000 s: launch, motor ignition (P4935 in the fin-can body tube).
- t = 0.043 s: lift-off.
- t = 0.430 s: launch rod clearance.
- t = 11.930 s: motor burnout.
- t = 59.001 s: apogee (57,794 ft AGL; real = 42,771 ft GPS).
- t = 59.002 s: Recovery Event 1 (drogue) deployed.
- t = 1130.902 s: Recovery Event 2 (main) deployed.
- t = 1198.205 s: ground hit / simulation end. Terminal note: NORMAL (1.8 s under the 1200 s cap; Prompt 7 resolved the previous MAXTIME).

## Phase split (from Kinsel phase table)

| Phase | Duration | Max M | Avg Cd | Avg Cdf | Avg Cdp | Avg Cdb | Avg AoA |
|---|---:|---:|---:|---:|---:|---:|---:|
| boost | 11.930 s | 2.328 | 0.462 | 0.241 | 0.041 | 0.180 | 0.001° |
| coast | 47.071 s | 2.328 | 0.577 | 0.290 | 0.034 | 0.200 | 0.794° |
| descent | 1139.204 s | 0.104 | (dominated by parachute) | | | | 90.72° |

Coast (47 s) is 4× longer than boost and gains 42,291 ft vs boost's 15,503 ft. Coast AoA is 0.79°. The overshoot is an ascent-drag-deficit problem; the MAXTIME fragility is a downstream consequence.

## Peak-Mach drag breakdown (max-mach snapshot, M = 2.328, t = 11.920 s)

| Component | Cd total | Cdf | Cdp | Cdb | % of total |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.0830 | 0.0830 | 0.0000 | 0.0000 | 23.7% |
| Nose Cone | 0.0339 | 0.0171 | 0.0167 | 0.0000 | 9.7% |
| Fin | 0.0082 | 0.0063 | 0.0019 | 0.0000 | 2.3% |
| Fin Can | 0.1586 | 0.0155 | 0.0000 | 0.1431 | 45.3% |
| Fin Can Shoulder | 0.0004 | 0.0004 | 0.0000 | 0.0000 | 0.1% |
| Rail Guide | 0.0206 | 0.0000 | 0.0206 | 0.0000 | 5.9% |
| **Rocket total** | **0.3501** | **0.1414** | **0.0655** | **0.1431** | |

The Fin Can base drag (Cdb = 0.143, 40.9% of total Cd) is the single dominant term. Per Prompt 18, the augmentation factor for this geometry at M = 2.4 is 1.55 (4 fins at full span/radius), so the unaugmented Devan-Ashwood baseline is ~0.092. The expanding shoulder (6.125" → 6.500") correctly produces zero wave drag (expansion fan, not shock) and zero step drag (smooth transition); Prompt 18 test confirms this is a modeling correctness, not a bug. Fin drag is 2.3% — fin-only tuning cannot close this gap.

## Likely root-cause family

Supersonic fin-can base drag underprediction. Per Prompt 9 (`high_m_drag_reconciliation.md` §3d) and Prompt 18, the mechanism is pure aero-model deficit in the fin-can base term at M > 2, with CDX1 contamination being wrong-direction (the unsupported settings would *increase* drag and thus reduce the overshoot). Prompt 12 removed the Lamb-Oberkampf Re correction for 2.1 pp of closure; the residual needs Candidate #2 (transonic/supersonic base-drag amplitude) and/or Candidate #3 (FINNED_BASE_K increase, span-ratio-dependent).

## Hypothesis falsification test

If the Devan-Ashwood supersonic base correlation (Cdb = 0.064 + 0.186/M²) at M = 2.33 is already the correct flat-base value and the finned-body augmentation of 1.55× is already at the upper end of Hoerner Ch.16's 40–60% range, and yet Kinsel still overshoots by >25%, then the hypothesis "fin-can base drag too low" is partially falsified and an additional mechanism must exist: e.g., protuberance drag (rail button at M = 2.33 is only 5.9%; could be under-modeled), unmodeled excrescence drag (couplers, wiring channels at HPR scale), or a Re-dependent augmentation that rises (not falls) with Re_D. A concrete numerical falsifier: if the Basic Finner external benchmark (A-level, ADA636861) cannot be made to simultaneously improve (current MAPE 11.3%, previously 22.7%), no defensible base-drag change will close Kinsel without breaking anchor.

## Closure definition

**Closed when ORP apogee error |e| ≤ 10% on Kinsel with no regression in the 22 A-level external benchmarks (specifically Basic Finner MAPE ≤ 12%) and no new outliers created in the SimVReal corpus.** Stretch: |e| ≤ 5%. Realistic physics-defensible estimate per Prompt 11 decision memo is ~+27% (Candidate #2+#3 combined), which misses the 10% gate. The AST paper must therefore either (a) implement Candidates #2/#3 with external data anchors to reach +27%-ish and document Kinsel as a "residual outlier with identified mechanism," or (b) exclude Kinsel from the headline corpus with stated rationale (CDX1 parity + HPR-scale excrescence drag unmodeled).

## Current status

**OPEN.** Worst overall outlier. Prompt 12 closed 2.1 pp (+35.1% → +33.0%). MAXTIME risk is fragile (1.8 s under the 1200 s cap); any further apogee closure will also widen that safety margin. Candidate #2 still pre-gated on external data.

## Exact files touched by this sheet

- `paper/data/outlier_closure/kinsel_closure.md` (this file; new)
