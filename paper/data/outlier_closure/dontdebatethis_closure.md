# Don't Debate This N5800 Min Dia — Closure Sheet

## Header

- Case: Don't Debate This N5800 Min Dia (3-fin fin-can, minimum-diameter class, conical nose)
- Current error: **+2.3%** apogee (ORP 57,867 ft vs real 56,573 ft Baro; RASAero 61,982 ft, +9.6%)
- Previous error: **+18.2%** before finned-body base drag augmentation was introduced (per `VALIDATION_MATRIX.md` §"Case-specific AST blockers").
- Status: **CLOSED** (within ±5%)
- Target: within ±10% (achieved); within ±5% (achieved)
- Regime: supersonic (peak M = 3.061)
- Source: `core/build/reports/simvreal-outliers/DontDebateThisN5800MinDia.md`

## Import parity warnings

- Parity matrix (`simvreal_parity_matrix.csv` row 19): `ParityClass = CONTAMINATED`, `UnsupportedActiveCount = 1`.
- Loader warnings (DontDebateThisN5800MinDia.md §"Loader warnings"):
  - `Ignoring unsupported RASAero setting Turbulence=True`
  - `Ignoring unsupported RASAero setting SustainerNozzle=2.488`
- Prompt 3 / Prompt 4 bounding:
  - `SustainerNozzleDiameter = 2.488"` IS correctly applied via `SimulationHandler.setNozzleExitDiameter()`. The warning is the redundant `<RocketDesign>` copy. Live Prompt 4 sensitivity: 0.0% apogee delta.
  - `Turbulence=True` analytical bound: <1.2% apogee.
- Combined active parity impact: <1.5% apogee. Does not materially affect the closure.
- Simulation warnings: `Recovery device deployment at high speed (23.8 m/s): "Recovery Event 2"` (main chute deploys when it reaches its altitude trigger at descent speed 23.8 m/s; does not affect ascent apogee).

## Event timeline (from DDT .md)

- t = 0.000 s: launch, motor ignition (N5800-CS, mounted in fin-can).
- t = 0.029 s: lift-off.
- t = 0.159 s: launch rod clearance.
- t = 3.594 s: motor burnout.
- t = 52.837 s: apogee (57,867 ft AGL).
- t = 52.838 s: Recovery Event 1 (drogue) deployed.
- t = 525.932 s: Recovery Event 2 (main) deployed at 23.8 m/s (warning).
- t = 607.270 s: ground hit / simulation end. Terminal note: NORMAL.

## Phase split (from DDT phase table)

| Phase | Duration | Max M | Avg Cd | Avg Cdf | Avg Cdp | Avg Cdb | Avg AoA |
|---|---:|---:|---:|---:|---:|---:|---:|
| boost | 3.594 s | 3.061 | 0.528 | 0.194 | 0.034 | 0.156 | 3.34° |
| coast | 49.243 s | 3.037 | 0.514 | 0.255 | 0.033 | 0.184 | 0.927° |
| descent | 554.433 s | 0.221 | (chute-dominated) | | | | 34.87° |

Coast AoA 0.93° is low, boost AoA 3.34° is non-trivial (a high-thrust motor with short burn on a minimum-diameter geometry). Coast (49 s) gains 50,957 ft vs boost's 6,910 ft. Ascent closure is coast-dominated.

## Peak-Mach drag breakdown (max-mach snapshot, M = 3.061, t = 3.423 s)

| Component | Cd total | Cdf | Cdp | Cdb | % of total |
|---|---:|---:|---:|---:|---:|
| Fin | 0.0063 | 0.0048 | 0.0015 | 0.0000 | 2.5% |
| Fin Can Shoulder | 0.0005 | 0.0005 | 0.0000 | 0.0000 | 0.2% |
| Nose Cone | 0.0445 | 0.0090 | 0.0354 | 0.0000 | 17.5% |
| Fin Can | 0.1241 | 0.0138 | 0.0000 | 0.1104 | 48.7% |
| Body Tube | 0.0667 | 0.0667 | 0.0000 | 0.0000 | 26.2% |
| **Rocket total** | **0.2548** | **0.1044** | **0.0400** | **0.1104** | |

Fin Can base Cdb = 0.1104 dominates (43.3% of total). Conical nose contributes the usual supersonic pressure drag (17.5%). Fin drag is 2.5% — negligible. Closure came from lifting the Fin Can base drag via the finned-body augmentation, not from fin tuning.

## Likely root-cause family (historical)

Was: supersonic fin-can base-drag underprediction without the finned-body augmentation applied. Per VALIDATION_MATRIX.md row for "Finned-body base drag augmentation" and the roadmap baseline, the introduction of `calculateFinnedBaseAugmentation()` in `BarrowmanDragCalculator` (FINNED_BASE_K = 0.55, Mach ramp M 0.2→1.3, fin-count/span scaling, 5% boattail taper gate) raised the Fin Can Cdb enough to move DDT from +18.2% to +2.3%. The augmentation is B-level anchored to Hoerner Ch.16 + the Basic Finner benchmark.

## Hypothesis falsification test (retrospective)

If future changes to `calculateFinnedBaseAugmentation()` produce an augmentation < 1.25× for DDT's geometry at M = 3.06 (3 fins, span/radius consistent with rocket-family norms), the Fin Can Cdb will drop below ~0.095 and DDT apogee will regress toward +15%. This is the exact regression guard: `calculateFinnedBaseAugmentation()` for DDT at M = 3.06 must remain ≥ ~1.3× (current delivered Cdb = 0.1104 implies Devan-Ashwood raw ≈ 0.084 at M = 3.06, so augmentation ≈ 1.31×). If the augmentation drops below that, the closure is broken.

## Closure definition

**Closed when ORP apogee error |e| ≤ 10% on DDT (actual: +2.3%, within ±5%) AND the finned-body augmentation applied to DDT's fin-can base term at peak Mach remains ≥ 1.25× AND no A-level benchmark regresses.** All three sub-conditions hold in the current build.

## Current status

**CLOSED** at +2.3%. Mechanism: finned-body base drag augmentation applied to fin-can component. Regression guard: augmentation magnitude at DDT's M = 3.06 must not drop below ~1.25×. Parity contamination (Turbulence flag) is bounded <1.2% and does not threaten the closure.

## Exact files touched by this sheet

- `paper/data/outlier_closure/dontdebatethis_closure.md` (this file; new)
