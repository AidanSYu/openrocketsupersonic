# Full Metal Jacket BALLS 005 — Closure Sheet

## Header

- Case: Full Metal Jacket BALLS 005 (3-fin fin-can, ogive nose, expanding fin-can shoulder 4.000" → 4.250")
- Current error: **+8.7%** apogee (ORP 41,283 ft vs real 37,981 ft Optical; RASAero 38,772 ft, +2.1%)
- Previous error: **+18.5%** before finned-body base drag augmentation (per `VALIDATION_MATRIX.md` §"Case-specific AST blockers").
- Status: **CLOSED** (within ±10%)
- Target: within ±10% (achieved); within ±5% (not achieved — residual +8.7%)
- Regime: supersonic (peak M = 2.319)
- Source: `core/build/reports/simvreal-outliers/Full_Metal_Jacket1.md`

## Import parity warnings

- Parity matrix (`simvreal_parity_matrix.csv` row 21): `ParityClass = CLEAN`, `UnsupportedActiveCount = 0`.
- Loader warnings (Full_Metal_Jacket1.md §"Loader warnings"):
  - `Ignoring unsupported RASAero setting SustainerNozzle=2.5` — redundant `<RocketDesign>` copy; `SustainerNozzleDiameter = 2.5"` IS correctly applied via `SimulationHandler.setNozzleExitDiameter()` (Prompt 4). Not flagged as contamination.
- Simulation warnings: `No recovery device defined in the simulation` (free-fall descent, does not affect ascent apogee).
- Conclusion: no parity contamination.

## Event timeline (from Full_Metal_Jacket1.md)

- t = 0.000 s: launch, motor ignition (O10000 in fin-can).
- t = 0.020 s: lift-off.
- t = 0.199 s: launch rod clearance.
- t = 2.280 s: motor burnout.
- t = 45.801 s: apogee (41,283 ft AGL).
- t = 374.729 s: ground hit / simulation end. Terminal note: NORMAL.

No recovery device: the descent phase is gravity + aero tumbling.

## Phase split (from FMJ phase table)

| Phase | Duration | Max M | Avg Cd | Avg Cdf | Avg Cdp | Avg Cdb | Avg AoA |
|---|---:|---:|---:|---:|---:|---:|---:|
| boost | 2.280 s | 2.319 | 0.520 | 0.333 | 0.022 | 0.166 | 0.001° |
| coast | 43.521 s | 2.319 | 0.560 | 0.315 | 0.024 | 0.190 | 0.612° |
| descent | 328.928 s | 0.188 | (tumbling) | | | | 91.75° |

Very short burn (2.28 s) and nearly all altitude from coast (gains 38,752 ft coast vs 2,531 ft boost). Coast AoA = 0.61°. Drag-limited ascent.

## Peak-Mach drag breakdown (max-mach snapshot = burnout snapshot, M = 2.319, t = 2.280 s)

| Component | Cd total | Cdf | Cdp | Cdb | % of total |
|---|---:|---:|---:|---:|---:|
| Fin | 0.0075 | 0.0065 | 0.0009 | 0.0000 | 2.2% |
| Body Tube | 0.1120 | 0.1120 | 0.0000 | 0.0000 | 33.3% |
| Nose Cone | 0.0534 | 0.0119 | 0.0416 | 0.0000 | 15.9% |
| Fin Can Shoulder | 0.0003 | 0.0003 | 0.0000 | 0.0000 | 0.1% |
| Fin Can | 0.1483 | 0.0170 | 0.0000 | 0.1313 | 44.1% |
| **Rocket total** | **0.3363** | **0.1607** | **0.0444** | **0.1313** | |

Fin Can base Cdb = 0.1313 dominates (39.0% of total Cd). Fin drag is 2.2% — negligible. Expanding shoulder (4.000" → 4.250") correctly produces near-zero wave/step drag (Prompt 18 documents this).

## Likely root-cause family (historical)

Was: supersonic fin-can flat-base drag underprediction without the finned-body augmentation. Per the roadmap baseline, `calculateFinnedBaseAugmentation()` raised Fin Can Cdb for FMJ to 0.1313, closing the case from +18.5% to +8.7%. The residual +8.7% places FMJ at the edge of the 10% gate; it sits in the same family as Kinsel (supersonic fin-can base drag) but with a smaller residual because the geometry is smaller and the peak Mach is lower (M 2.32 vs Kinsel's M 2.33 with 2.5× reference area).

## Hypothesis falsification test (retrospective)

If `calculateFinnedBaseAugmentation()` for FMJ at M = 2.32 (3 fins, span/radius consistent with 3-fin norm) produced an augmentation < 1.3×, the Fin Can Cdb would drop from 0.1313 toward the raw Devan-Ashwood-at-M=2.32 value of ~0.064 + 0.186/5.38 = 0.099 (10%-ish less, a direct 1% apogee regression) — still within 10% but widening the margin. Conversely, a physics-defensible increase in the augmentation (per Prompt 11 Candidate #3 with span-ratio dependence) should simultaneously close both FMJ and Kinsel without regressing the Basic Finner benchmark.

## Closure definition

**Closed when ORP apogee error |e| ≤ 10% on FMJ BALLS 005 (actual: +8.7%, within ±10%) AND the finned-body augmentation on the fin-can component is in the expected 1.3×–1.55× range at M = 2.32.** The first condition holds; the implied raw Cdb from the delivered 0.1313 gives an augmentation of ~1.32×, consistent with expectation. Stretch target of ±5% is not achieved; would require further physics-anchored closure (same Candidate #2/#3 family as Kinsel).

## Current status

**CLOSED** at +8.7%. Closure is thin (1.3 pp inside the 10% gate). A regression of the fin-can augmentation or the boundary-layer-augmentation interaction would reopen this case. Same root-cause family as Kinsel (supersonic fin-can base drag), so any Candidate #2/#3 work on Kinsel must also check FMJ does not over-shoot backward (under-apogee) after the fix.

## Exact files touched by this sheet

- `paper/data/outlier_closure/fmj_balls005_closure.md` (this file; new)
