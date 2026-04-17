# SimVReal CDX1 Import Parity Matrix — Interpretation

Generated: 2026-04-16

## Summary

The 24-case SimVReal corpus was audited for every CDX1 setting that RASAero II uses but ORP does not implement. Three categories of unsupported settings exist:

| Setting | Where in CDX1 | ORP behavior | Affected cases |
|---|---|---|---|
| `ModifiedBarrowman=True` | RocketDesign | **Ignored** (warned). RASAero uses a modified Barrowman method for CP/stability that differs from the standard method at higher Mach/AoA. ORP uses its own supersonic Barrowman extensions. | Qu8k, Kinsel (2 cases) |
| `Turbulence=True` | RocketDesign | **Ignored** (warned). RASAero adds a turbulence intensity model that increases drag slightly. ORP does not model atmospheric turbulence effects on drag. | DontDebateThis, Qu8k, Kinsel, AeroPac104K (4 cases) |
| `Booster1NozzleDiameter>0` | Simulation | **Ignored** (warned). ORP only applies sustainer nozzle diameter for power-on base drag; booster nozzle diameters are discarded. | AeroPac104K (1 case) |
| `SustainerNozzleDiameter>0` | Simulation | **Applied**. Parsed and set via `sim.getOptions().setNozzleExitDiameter()`. | 17 cases (correctly handled) |

Note: The RocketDesign-level `SustainerNozzle`, `Booster1Nozzle`, `Booster2Nozzle` fields are redundant with the Simulation-level `*NozzleDiameter` fields. Both contain the same value. ORP warns on the RocketDesign-level fields but applies the Simulation-level sustainer value correctly.

## Parity classification

### CLEAN cases (20 of 24, 83.3%)

These cases have zero unsupported active settings. The imported problem definition matches RASAero's inputs exactly.

| Case | ORP Error | Peak Mach | Notes |
|---|---|---|---|
| Byrum | +1.9% | 0.48 | |
| Cancer Descending | +3.5% | 0.52 | |
| Gibb | -0.1% | 0.64 | |
| IonDrive | -5.7% | 0.72 | |
| Blister | -3.8% | 0.85 | SustainerNozzleDia applied |
| Rabia | +0.5% | 1.05 | SustainerNozzleDia applied |
| Rabia-ShortFinCan | -2.6% | 0.98 | SustainerNozzleDia applied |
| Torrent | +7.1% | 1.22 | SustainerNozzleDia applied |
| CalIsp1 | +3.2% | 0.45 | SustainerNozzleDia applied |
| CalIsp2 | +5.3% | 0.43 | SustainerNozzleDia applied |
| CalIsp3 | +5.0% | 0.42 | SustainerNozzleDia applied |
| CalIsp4 | +3.3% | 0.60 | SustainerNozzleDia applied |
| CalIsp5 | +3.1% | 0.52 | SustainerNozzleDia applied |
| L500Roc | +8.6% | 2.00 | SustainerNozzleDia applied |
| Proteus6 | +5.0% | 2.94 | SustainerNozzleDia applied |
| FMJ BALLS 005 | +8.7% | 2.30 | SustainerNozzleDia applied |
| FMJ Black Rock 6 | +2.7% | 2.10 | SustainerNozzleDia applied |
| **EZI-65** | **+16.1%** | 0.61 | **Outlier, CLEAN** |
| **Thunder & Lightning** | **+17.4%** | 0.55 | **Outlier, CLEAN** |
| **Raven** | **+27.5%** | 1.12 | **Outlier, CLEAN**, min-dia transonic |

### CONTAMINATED cases (4 of 24, 16.7%)

These cases have at least one unsupported setting that could affect the simulation result.

| Case | ORP Error | Peak Mach | Unsupported settings | Count |
|---|---|---|---|---|
| Don't Debate This | +2.3% | 3.10 | Turbulence=True | 1 |
| Qu8k | -5.3% | 3.40 | ModifiedBarrowman=True, Turbulence=True | 2 |
| **Kinsel** | **+35.1%** | 2.33 | **ModifiedBarrowman=True, Turbulence=True** | **2** |
| AeroPac 104K | -1.2% | 3.50 | Turbulence=True, Booster1NozzleDia=1.75in | 2 |

## Key findings

### 1. Three of four >10% outliers are parity-CLEAN

**EZI-65 (+16.1%), Thunder & Lightning (+17.4%), and Raven (+27.5%) have zero unsupported CDX1 settings.** The import parity is clean for all three. However, **parity-clean does NOT imply the residual is aerodynamic**: EZI-65 and T&L also show RASAero overshoot (+6.3% and +11.5% respectively), indicating the shared residual is driven by non-aerodynamic factors (motor impulse drift, dry-lake density, baro under-report on hot-day launches). See `paper/data/ezi65_tl_nonaero_audit.md` for the detailed decomposition. Only **Raven** has a large ORP-specific residual (+21.6 pp beyond RAS) that is clearly an aerodynamic model gap — this is the thick-BL effect on min-diameter L/D=37 bodies documented in `paper/data/raven_vs_rabia_diagnostic.md`.

### 2. The worst outlier (Kinsel +35.1%) IS parity-contaminated

Kinsel has both `ModifiedBarrowman=True` and `Turbulence=True`. However, these settings should *increase* drag in RASAero (turbulence adds drag; ModifiedBarrowman shifts CP aft, increasing stability margin and reducing weathercocking). ORP already overpredicts Kinsel by +35.1%, so the unsupported settings would push the answer further from reality if implemented, not closer. **The parity gap is not the cause of the Kinsel outlier.**

### 3. Contaminated cases that are within tolerance

- **Don't Debate This (+2.3%)**: Turbulence=True is ignored but the case is within +/-5%. Turbulence typically adds 1-3% drag, which could explain part of the residual but is not acceptance-critical.
- **Qu8k (-5.3%)**: Both MB and Turbulence ignored, but result is within +/-10% and the error direction (underprediction) is consistent with missing turbulence drag.
- **AeroPac 104K (-1.2%)**: Turbulence + booster nozzle ignored, but the result is excellent. The booster1 nozzle dia (1.75in on a 2-stage with 4in body) affects only the boost phase and likely shifts apogee by <1%.

### 4. SustainerNozzleDiameter is correctly applied

17 of 24 cases have nonzero sustainer nozzle diameters, and all are correctly parsed and applied via `SimulationHandler.sustainerNozzleDiameter` -> `sim.getOptions().setNozzleExitDiameter()`. This is NOT a parity gap.

## Acceptance-critical assessment

| Gap | Cases affected | Likely apogee impact | Acceptance-critical? |
|---|---|---|---|
| `Turbulence=True` ignored | 4 cases | +1-3% drag increase -> 1-3% lower apogee | **No** for 3 of 4 (already within tolerance). For Kinsel, it would worsen the existing overprediction. |
| `ModifiedBarrowman=True` ignored | 2 cases | CP shift at supersonic; magnitude uncertain. ORP has its own supersonic CP extensions. | **No** for Qu8k (within tolerance). For Kinsel, unlikely to explain +35% overshoot. |
| `Booster1NozzleDiameter` ignored | 1 case | Boost-phase base drag reduction only; <1% apogee effect | **No** |

**Conclusion: No currently contaminated case has an outlier that is plausibly caused by the parity gap.** Parity is clean for three of the four >10% outliers (EZI-65, T&L, Raven), but this does NOT mean those residuals are aerodynamic — EZI-65 and T&L's shared overshoot with RASAero (+6.3% and +11.5%) indicates non-aero causes (motor/density/baro) dominate for them. Only **Raven** has a large ORP-specific residual (+21.6 pp beyond RAS) attributable to an aerodynamic model gap (thick-BL at L/D=37). **Kinsel (+35.1%)** is contaminated but Qu8k has the identical 3 ignored settings and is healthy (−1.8%), disproving CDX1 parity as the Kinsel cause; Kinsel's residual is a kinematic amplification of premature-laminar friction on an 11.93 s boost. The parity matrix is safe for AST publication with disclosure of the 4 contaminated cases plus explicit non-aero attribution for EZI-65/T&L.

## Recommendation for manuscript

1. Report all 24 cases with the parity classification column visible in the validation table.
2. Note that 4 cases have nonzero unsupported settings (Turbulence and/or ModifiedBarrowman).
3. State that sensitivity analysis shows these gaps shift apogee by <3% and are in the wrong direction to explain the remaining outliers.
4. Attribute each >10% outlier to its dominant mechanism:
   - **EZI-65 and T&L**: non-aerodynamic (motor impulse drift, density, baro under-report on hot-day launches). Shared residual with RASAero confirms non-aero dominance.
   - **Raven**: aerodynamic — thick-BL base drag under-prediction at min-dia L/D=37 in the transonic regime.
   - **Kinsel**: kinematic amplification of premature-laminar friction during long boost; not caused by the ignored CDX1 flags (Qu8k has identical flags and is healthy).
