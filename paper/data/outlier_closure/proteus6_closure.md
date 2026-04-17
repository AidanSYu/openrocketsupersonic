# Proteus 6 — Closure Sheet

## Header

- Case: Proteus 6 (3-fin, conical nose, terminating boattail 6.000" → 5.000")
- Current error: **+5.0%** apogee (ORP 89,290 ft vs real 85,067 ft Accel; RASAero 81,499 ft, −4.2%)
- Previous error: **+11.8%** before boattail + preceding-sibling fin-search fixes (per `VALIDATION_MATRIX.md`).
- Status: **CLOSED** (within ±5%)
- Target: within ±10% (achieved); within ±5% (achieved)
- Regime: supersonic (peak M = 2.817)
- Source: `core/build/reports/simvreal-outliers/Proteus6.md`

## Import parity warnings

- Parity matrix (`simvreal_parity_matrix.csv` row 20): `ParityClass = CLEAN`, `UnsupportedActiveCount = 0`.
- Loader warnings (Proteus6.md §"Loader warnings"):
  - `Ignoring unsupported RASAero setting SustainerNozzle=4.8` — this is the redundant `<RocketDesign>` copy; `SustainerNozzleDiameter` IS correctly applied elsewhere (Prompt 4). Not flagged as contamination by the parity matrix.
- Sim warnings: `Recovery device deployment at high speed (25.4 m/s): "Recovery Event 1"` (single-event deployment at apogee with descent speed 25.4 m/s; does not affect ascent apogee).
- Conclusion: no parity contamination. Closure is aerodynamic-model.

## Event timeline (from Proteus6.md)

- t = 0.000 s: launch, motor ignition (P9381 in body tube).
- t = 0.080 s: lift-off.
- t = 0.420 s: launch rod clearance.
- t = 8.000 s: motor burnout.
- t = 73.050 s: apogee (89,290 ft AGL).
- t = 73.051 s: Recovery Event 1 deployed (25.4 m/s warning).
- t = 637.821 s: ground hit / simulation end. Terminal note: NORMAL.

## Phase split (from Proteus6 phase table)

| Phase | Duration | Max M | Avg Cd | Avg Cdf | Avg Cdp | Avg Cdb | Avg AoA |
|---|---:|---:|---:|---:|---:|---:|---:|
| boost | 8.000 s | 2.817 | 0.471 | 0.265 | 0.098 | 0.107 | 0.102° |
| coast | 65.050 s | 2.781 | 0.650 | 0.334 | 0.115 | 0.131 | 1.033° |
| descent | 564.771 s | 0.437 | (chute-dominated) | | | | 33.90° |

Coast (65 s) gains 74,650 ft vs boost's 14,640 ft. Coast AoA 1.03° is low. Coast Cd_b of 0.131 is below the fin-can cases (DDT 0.184, FMJ 0.190) because Proteus 6 has a boattail-to-5.000" end, not a flat fin-can base.

## Peak-Mach drag breakdown (max-mach snapshot, M = 2.817, t = 7.350 s)

| Component | Cd total | Cdf | Cdp | Cdb | % of total |
|---|---:|---:|---:|---:|---:|
| Fin | 0.0070 | 0.0048 | 0.0021 | 0.0000 | 2.3% |
| Rail Guide | 0.0128 | 0.0000 | 0.0128 | 0.0000 | 4.2% |
| Body Tube | 0.1041 | 0.1041 | 0.0000 | 0.0000 | 33.8% |
| Boattail | 0.1075 | 0.0008 | 0.0267 | 0.0800 | 34.9% |
| Nose Cone | 0.0496 | 0.0105 | 0.0391 | 0.0000 | 16.1% |
| **Rocket total** | **0.3078** | **0.1299** | **0.0979** | **0.0800** | |

Boattail component carries the base drag (Cdb = 0.080) and a non-trivial pressure/wave drag (Cdp = 0.027). The boattail, not the body tube, is the aft-most sibling; per Prompt 18, the finned-body augmentation code correctly searches for fins on the preceding sibling body tube and attributes the augmentation to the component producing base drag. Fin drag is 2.3% — negligible.

## Likely root-cause family (historical)

Was: (a) the boattail's reduced base area (5.000" aft diameter vs 6.000" reference diameter) was under-weighting the base-drag reduction; (b) the finned-body base drag augmentation was not finding the fins because they are siblings of the body tube rather than of the boattail. The two fixes per `VALIDATION_MATRIX.md` closures table were:
1. Boattail factor from Hoerner Ch.16 / DATCOM 4.6.3.2 family correctly reduces Cd_b with boattail half-angle at supersonic.
2. Preceding-sibling fin search in `calculateFinnedBaseAugmentation()` correctly applies the fin augmentation to the terminating boattail when fins are mounted on the upstream body tube.

Together these brought Proteus 6 from +11.8% to +5.0%.

## Hypothesis falsification test (retrospective)

If the boattail factor for Proteus 6's geometry (6.000" → 5.000" over 1.000", half-angle ≈ 26.6°) at M = 2.82 were overridden to 1.0 (no boattail reduction applied), or if the preceding-sibling fin search were disabled and the augmentation dropped to 1.0, Proteus 6 would regress toward +11-12%. The fact that it sits at +5.0% and the individual Cdb = 0.080 is in the expected Devan-Ashwood-times-boattail-times-augmentation range confirms both fixes are active.

## Closure definition

**Closed when ORP apogee error |e| ≤ 10% on Proteus 6 (actual: +5.0%, within ±5%) AND the boattail component Cdb at peak Mach is non-zero and attenuated relative to a flat base of the same reference area AND the finned-body augmentation is attributed to the boattail via the preceding-sibling search.** All three sub-conditions hold.

## Current status

**CLOSED** at +5.0%. Mechanism: boattail factor + preceding-sibling fin search in finned-body base augmentation. Parity CLEAN. No further action required for this case.

## Exact files touched by this sheet

- `paper/data/outlier_closure/proteus6_closure.md` (this file; new)
