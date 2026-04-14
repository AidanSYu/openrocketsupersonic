# Gap Closure Program

This file is the execution tracker for moving the aerodynamic model from thesis-grade evidence to reviewer-defensible publication evidence.

## Objective

Close every item that is currently:

- `not defensible yet`
- `needs qualification`
- `diagnostic only`
- `heuristic / tuned / robustness-only`

without collapsing manuscript claims into overstatement.

## Current State (Updated 2026-04-14)

The repo now has:

- strong analytical building-block validation (NACA 1135 shocks, Prandtl-Meyer, Taylor-Maccoll, atmosphere)
- one real external zero-lift drag benchmark (`NACA RM A52H28`) with a passing first-pass aggregate MAE = 0.0147 and isolated residual biases
- **one independent base-drag benchmark (`NACA TN 3393`)** with figure-digitized Cpb on matched coefficient basis, confirming turbulent BL agreement
- **one external static-stability benchmark (`NASA TM X-653`)** with digitized CNa and xCP/d for NSCFB finned config, M 0.6-3.0
- one useful diagnostic benchmark (`AGARD-B`) for transition-sensitivity analysis
- sensitivity exports for tuned parameters
- a clear robustness-vs-aerodynamics boundary
- **runtime-proven guard invariance** via `GuardInvarianceTest.java` (72-point Mach/AoA sweep, 0 violations)
- **documented A52H28 cone and quarter-power bias isolation** (transonic pressure vs TR-R-100 table causes separated)

The repo does **not** yet have:

- external closure for dynamic stability heuristics (pitch damping, Magnus, transonic Cmq)
- a basis for broad whole-vehicle or Mach-10+ predictive claims
- laminar base-drag agreement (model is turbulent-calibrated)

## Workstreams

### 1. Zero-Lift Drag Closure — **CLOSED**

Goal: make external `Cd(M)` claims reviewer-defensible.

**Completed:**

- A52H28 benchmark: MAE = 0.0147, 5 nose shapes, figure-digitized, Reynolds-matched
- A52H28 bias isolation: cone residual → transonic pressure model, quarter-power → TR-R-100 table (documented in `a52h28_bias_isolation.md`)
- NACA TN 3393 base drag: figure-digitized Cpb on matched coefficient basis, turbulent BL agreement confirmed
- AGARD-B retained as diagnostic, no longer sole transonic anchor

### 2. Static Stability Closure — **CLOSED**

Goal: make `Cn(alpha)` and `x_CP` claims reviewer-defensible.

**Completed:**

- NASA TM X-653: CNa and xCP/d digitized from Figures 5(a), 5(b) for NSCFB config
- Agreement metrics: CNa MAE < 0.003/deg for M 0.6-2.0
- M=3.0 fin-body interference anomaly documented and flagged

**Remaining (future work):**

- Additional body-only or interference datasets (NASA TN D-6996, NACA Report 1307)
- Higher AoA validation

### 3. Dynamic / High-AoA Heuristic Exposure — **PARTIALLY CLOSED**

Goal: reduce the manuscript risk from tuned terms.

**Completed:**

- T03 Magnus body fraction: confirmed within bounds
- T01 pitch damping: flagged for further external data

**Pending:**

- T02 body/fin damping cap
- T04 transonic Cmq augmentation
- T05 vortex asymmetry
- T06 crossflow fin Cd

Each pending heuristic is explicitly de-scoped to appendix/supporting material per the validation matrix.

### 4. Numerical Guard Separation — **CLOSED**

Goal: prove numerical safeguards are not contaminating aerodynamic claims.

**Completed:**

- `GuardInvarianceTest.java`: 72-point Mach/AoA runtime sweep, all 10 guards inactive
- Beta continuity through transonic verified
- No NaN/Infinity at edge conditions including M=1.0
- Results exported to `guard_tuned_invariance_metrics.csv` for reviewer inspection
- `guard_tuned_invariance.md` documents all evidence

## Immediate Order (Updated 2026-04-14)

Items 1-4 are now complete. Next priorities:

1. ~~Finish `NACA TN 3393` figure digitization and coefficient-basis closure.~~ **DONE**
2. ~~Finish `NASA TM X-653` digitization and comparison metrics.~~ **DONE**
3. ~~Separate the remaining `A52H28` cone / quarter-power residuals into transition-state versus pressure-drag causes.~~ **DONE**
4. ~~Re-rank tuned heuristics after the new external datasets are in place.~~ **DONE** (T01 flagged, T03 passed, T02/T04/T05/T06 de-scoped to appendix)

**Future work priorities:**
5. External data for T01 pitch-damping multiplier (highest-risk remaining tuned term).
6. Investigate M=3.0 fin-body interference anomaly in NASA TM X-653 comparison.
7. Additional external datasets: NASA TN D-6996 (body-only crossflow), NACA Report 1307 (fin-body interference).

## Ranked Risk Register (Updated 2026-04-14)

### Highest-risk model gaps

- Pitch damping, transonic `Cmq` augmentation, and vortex-asymmetry terms remain tuned heuristics (de-scoped to appendix).
- M=3.0 fin-body interference anomaly produces CNa/xCP spike not present in TM X-653 data.

### Medium-risk model gaps

- `A52H28` cone and quarter-power residuals are now isolated and documented but not reduced.
- `AGARD-B` remains transition-sensitive (useful diagnostic, not primary anchor).
- Laminar base-drag predictions diverge from experiment (model is turbulent-calibrated).

### Lower-risk — now closed

- ~~Static stability / CP claims unsupported by external dataset~~ → Closed by NASA TM X-653
- ~~Base drag unclosed by independent external dataset~~ → Closed by NACA TN 3393
- ~~Numerical guard invariance undocumented~~ → Closed by `GuardInvarianceTest.java`

## Fix Hypotheses — Status (Updated 2026-04-14)

### A52H28 — **Isolated, documented**

1. ✓ Polished / perfect-finish and Reynolds-matched export assumptions locked.
2. ✓ Cone overprediction isolated to transonic pressure model (`a52h28_bias_isolation.md`).
3. ✓ Quarter-power overprediction isolated to TR-R-100 table + fineness scaling.
4. M=1.44 dual-Re ambiguity documented as inherent source limitation.

### AGARD-B / Base Drag — **Base-drag closed independently**

1. ✓ AGARD retained as transition/base-drag diagnostic only.
2. ✓ NACA TN 3393 now has figure-derived Cpb on matched coefficient basis.
3. Transition-state instrumentation alongside AGARD remains future work.

### Static Stability — **First closure achieved**

1. ✓ TM geometry fixture and export in repo.
2. ✓ CNa and xCP/d digitized from TM X-653 Figures 5(a), 5(b).
3. ✓ Comparison metrics exported to `paper/data`.

## Files To Touch Next

- `core/src/test/java/info/openrocket/core/aerodynamics/PublicationAnalyticalDataExportTest.java`
- `core/src/test/java/info/openrocket/core/aerodynamics/SupersonicTestRockets.java`
- `core/src/main/java/info/openrocket/core/aerodynamics/barrowman/SymmetricComponentCalc.java`
- `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java`
- `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanStabilityCalculator.java`
- `paper/data/py/*`
- `paper/data/csv/*`
- `paper/data/md/*`

## Notes

- `RASAero` remains supporting evidence, not truth.
- `AGARD-B` remains diagnostic until base-drag and transition closure improve.
- Manuscript claims should stay inside the currently closed evidence envelope at every stage of this program.
