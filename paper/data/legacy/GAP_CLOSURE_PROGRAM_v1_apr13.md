# Gap Closure Program

This file is the execution tracker for moving the aerodynamic model from thesis-grade evidence to reviewer-defensible publication evidence.

## Objective

Close every item that is currently:

- `not defensible yet`
- `needs qualification`
- `diagnostic only`
- `heuristic / tuned / robustness-only`

without collapsing manuscript claims into overstatement.

## Current State

The repo now has:

- strong analytical building-block validation
- one real external zero-lift drag benchmark (`NACA RM A52H28`) with a passing first-pass aggregate MAE and narrowed residual bias
- one useful but non-anchor diagnostic benchmark (`AGARD-B`)
- benchmark fixture/export support for `NACA TN 3393` and `NASA TM X-653`
- sensitivity exports for tuned parameters
- a clear robustness-vs-aerodynamics boundary
- a documented guard/tuned invariance package

The repo does **not** yet have:

- a clean external base-drag closure case on a matched coefficient basis
- a clean external static-stability / CP closure case with digitized published ordinates
- external closure for dynamic stability heuristics
- a basis for broad whole-vehicle or Mach-10+ predictive claims

## Workstreams

### 1. Zero-Lift Drag Closure

Goal: make external `Cd(M)` claims reviewer-defensible.

Subproblems:

- explain and reduce the remaining `A52H28` cone / quarter-power residuals
- finish the independent transonic / base-drag benchmark on `NACA TN 3393`
- keep `AGARD-B` diagnostic until the independent base-drag anchor exists

Definition of done:

- one exact external body-of-revolution benchmark with good absolute error
- one independent transonic / base-drag benchmark
- no need to rely on AGARD alone to justify drag-split tuning

### 2. Static Stability Closure

Goal: make `Cn(alpha)`, `Cm(alpha)`, and `x_CP` claims reviewer-defensible.

Subproblems:

- finish digitizing `NASA TM X-653` now that fixture/export support exists
- determine whether additional body-only or interference datasets are needed
- separate body lift, fin lift, and interference-layer validation

Definition of done:

- at least one exact external nonzero-AoA benchmark
- explicit agreement metrics for `Cn(alpha)` and/or `x_CP`

### 3. Dynamic / High-AoA Heuristic Exposure

Goal: reduce the manuscript risk from tuned terms.

Subproblems:

- pitch damping multiplier
- body damping coefficient / fin cap
- Magnus fraction
- transonic `Cmq` boost
- vortex asymmetry and onset/saturation
- crossflow fin `Cd`

Definition of done:

- each heuristic either has external data closure or is explicitly de-scoped to appendix/supporting material

### 4. Numerical Guard Separation

Goal: prove numerical safeguards are not contaminating aerodynamic claims.

Subproblems:

- invariance sweeps for thresholds / caps / floors
- trigger-case audits
- clean-case confirmation that guards stay inactive where claims are made

Definition of done:

- reviewer can see which thresholds are software-only and why they do not alter clean aerodynamic validation cases

## Immediate Order

1. Finish `NACA TN 3393` figure digitization and coefficient-basis closure.
2. Finish `NASA TM X-653` digitization and comparison metrics.
3. Separate the remaining `A52H28` cone / quarter-power residuals into transition-state versus pressure-drag causes.
4. Re-rank tuned heuristics after the new external datasets are in place.

## Ranked Risk Register

### Highest-risk model gaps

- Static stability / CP claims are still unsupported by an external `Cn(alpha)` / `x_CP` dataset.
- Pitch damping, Magnus fraction, and transonic `Cmq` augmentation remain tuned heuristics.
- Crossflow replacement logic and vortex-asymmetry terms remain tuned heuristics for high AoA.
- Base drag is still unclosed by an independent external dataset.

### Medium-risk model gaps

- `A52H28` residuals are now narrower but still likely mix transition-state ambiguity at `M = 1.44` with remaining cone / quarter-power pressure-drag model error.
- `AGARD-B` is informative but still transition-sensitive and therefore unsuitable as the sole transonic tuning anchor.

### Lower-risk but mandatory transparency items

- Numerical guard thresholds, clamps, and timestep floors need invariance / trigger-map documentation.

## Immediate Fix Hypotheses To Test

### A52H28

1. Keep the polished / perfect-finish and Reynolds-matched export assumptions locked for the benchmark articles.
2. Isolate the remaining cone overprediction at `M = 1.24-1.99`.
3. Isolate the remaining quarter-power overprediction at `M = 1.24-1.99`.
4. Resolve the `M = 1.44` duplicate-Re / transition-state ambiguity as far as the source permits.

### AGARD-B / Base Drag

1. Keep AGARD as a transition/base-drag diagnostic only.
2. Replace the provisional `NACA TN 3393` proxy ordinates with figure-derived points and a matched coefficient basis.
3. Instrument/report transition-state quantities alongside AGARD friction results.

### Static Stability

1. Keep the TM geometry fixture and export in the repo as the comparison target.
2. Digitize `Cn(alpha)` / `x_CP` ordinates from `paper/data/pdf/NASA_TM_X_653.pdf`.
3. Export and report comparison metrics into `paper/data`.

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
