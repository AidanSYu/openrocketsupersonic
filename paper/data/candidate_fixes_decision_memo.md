# Candidate Model Fixes Decision Memo

Generated: 2026-04-16
Prompt: 11 (Build Finite Candidate List Of Model Fixes)
Agent: Claude Opus 4.6

## Context

Four outliers remain >10% apogee overshoot in the 24-case SimVReal corpus:

| Case | Error | Peak M | Regime | Parity |
|---|---|---|---|---|
| EZI-65 | +16.1% | 0.61 | Subsonic | CLEAN |
| Thunder & Lightning | +17.4% | 0.55 | Subsonic | CLEAN |
| Raven | +27.5% | 1.12 | Transonic, min-dia | CLEAN |
| A-601 Kinsel | +35.1% | 2.33 | Supersonic, fin-can | CONTAMINATED (wrong direction) |

All four overpredict apogee, meaning total drag is too low. The common residual across all four is **base drag too low** (38-43% of total Cd vs 46% in healthy cases). Fin drag is 2-9% and is NOT the problem. Body friction is consistent per-unit-L/D and is NOT the primary miss.

## DO NOT TOUCH List

These are A-level externally anchored. Any candidate that weakens them is rejected:

- Devan-Ashwood base drag constants (BASE_DRAG_A=0.064, BASE_DRAG_B=0.186)
- Van Driest II skin friction
- Taylor-Maccoll cone wave drag
- Shock-expansion ogive wave drag
- DATCOM 4.1.5.1 fin wave drag
- Chapman laminar base drag

---

## Candidate #1: Remove Lamb-Oberkampf Re Correction

**Rank: 1 (safest, highest evidence for removal)**

### Mechanism
`BarrowmanDragCalculator.java` line 1226 applies a Reynolds-number-based correction to Devan-Ashwood base drag at M > 1.3:
```
reFactor = MathUtil.clamp(1.0 - 0.08 * (logReD - 6.0), 0.7, 1.3)
```
For Kinsel at M=2.4: Re_D ~ 9.1e6 (logReD = 6.96), giving reFactor = 0.923 -- a **7.7% reduction** in base drag. Since fin-can base drag is 45% of Kinsel's total Cd, this correction removes ~3.5% from total drag.

For the Basic Finner (30mm model, Re_D ~ 3e5, logReD ~ 5.5): reFactor = 1.04, meaning the correction *increases* base drag by 4% for the benchmark geometry. Removing the correction will slightly reduce Basic Finner Cd, pushing its existing underprediction (MAPE 22.7%) modestly worse.

### File/method
- `BarrowmanDragCalculator.java`, method `calculateBaseCD(double m, FlightConditions conditions)` (line 1203-1229)
- Specifically line 1226: the `reFactor` formula

### Cases improved
- **Kinsel**: +3.5% total Cd increase. Reduces overshoot from +35.1% toward +31%.
- All supersonic cases with large Re_D get modest improvement.

### Benchmark risk
- **Basic Finner**: Currently MAPE 22.7% (gate 30%). Removing the correction reduces Basic Finner base Cd by ~4% at low Re, worsening underprediction by ~1-2% of total. MAPE would rise to ~24%. Still well within the 30% gate. The gate was specifically set wide because of the known Re mismatch (30mm model vs ORP full-scale).
- **TN 3393 base drag**: Unaffected (TN 3393 benchmark tests `calculateBaseCD(double m)` without Re correction).

### How to falsify
Run `BasicFinnerDragBenchmarkTest` and `SimVRealBenchmarkTest` before and after removal. If Basic Finner MAPE stays under 30% and Kinsel improves, the removal is safe. If Basic Finner MAPE exceeds 30%, the removal is rejected.

### AST defensibility
**High.** The correction is D-level: zero external data points in the repo. The Javadoc references "Lamb-Oberkampf (1995)" but the repo contains no validation data, no digitized reference curve, and no benchmark test for the Re correction itself. The Devan-Ashwood A-level correlation it modifies was validated *without* Re correction against TN 3393. Removing an unvalidated correction that modifies a validated correlation is the most defensible first move. The AST paper can state: "The Devan-Ashwood base drag correlation is used without Reynolds number correction, consistent with the original validation basis."

### Expected magnitude
Small: ~3.5% of total Cd for Kinsel, closes ~3-4 percentage points of the +35.1% gap. Necessary but not sufficient alone.

---

## Candidate #2: Widen Transonic Base Drag Peak

**Rank: 2 (medium evidence, directly addresses Raven)**

### Mechanism
The transonic base drag polynomial peaks at 0.25 at M=1.05, then descends to the Devan-Ashwood anchor of 0.174 at M=1.3. At Raven's peak Mach of M=1.12, the polynomial is already well past peak, yielding approximately Cd_base ~ 0.22.

Experimental data for cylindrical afterbodies (Hoerner Ch. 3 Fig. 3.19, generic wind-tunnel data cited in ESDU) shows the transonic base drag peak is broader, often persisting at near-peak values to M 1.1-1.2 before declining. The current shape drops too fast after M=1.05.

Two adjustable parameters:
1. **Peak value** (currently 0.25 at M=1.05): Could raise to 0.27-0.30 based on Hoerner/ESDU data for cylindrical afterbodies.
2. **Peak Mach** (currently 1.05): Could shift to M=1.08-1.10 to keep the peak broader through M=1.12.

Since the polynomial is C1-continuous with fixed anchor points at M=0.85 (subsonic) and M=1.3 (Devan-Ashwood), shifting the peak rightward or raising it will automatically produce a broader peak that persists to higher Mach.

### File/method
- `BarrowmanDragCalculator.java` static initializer block (lines 114-131)
- Specifically the peak constraint: `0.25` at `1.05` (line 127)

### Cases improved
- **Raven** (M=1.12): Raising peak to 0.28 at M=1.08 would increase body tube base Cd by approximately 0.03-0.05, adding ~3-5% to total Cd. With finned-body augmentation of 1.30x, the effective increase is ~0.04-0.065. This addresses roughly 10-15 percentage points of the +27.5% gap.
- **Torrent** (M=1.22, currently +7.1%): Modest improvement possible.
- All transonic cases in M 1.0-1.2 range.

### Benchmark risk
- **Basic Finner at M=1.08**: CX0_exp = 0.863. Currently ORP underpredicts by ~14-16%. A broader transonic peak would *improve* this point (adding base drag), which is the right direction.
- **Subsonic cases**: Zero effect (peak change does not affect M < 0.85).
- **Supersonic cases**: Zero effect (polynomial ends at M=1.3 and anchors to unchanged Devan-Ashwood).

### How to falsify
1. Evaluate the polynomial at M=1.08, 1.10, 1.12, 1.15, 1.20 before and after the change.
2. Run `BasicFinnerDragBenchmarkTest` -- M=1.077 point should improve or hold.
3. Run Raven diagnostic -- apogee error should decrease.
4. Run full SimVReal -- no subsonic case should degrade.

### AST defensibility
**Medium.** The current peak of 0.25 at M=1.05 is calibrated but not directly benchmarked against digitized data in the repo. The anchors (subsonic model at M=0.85, Devan-Ashwood at M=1.3) are A-level, but the peak itself is B-level at best. Any change must cite specific data -- Hoerner Fig. 3.19 or equivalent. A web search for the exact figure data would be required before implementation. Without external data, this is tuning, not physics. However, the qualitative fact that cylindrical afterbody base drag peaks near M=1.0-1.1 and persists to M~1.2 is well-established in the literature.

### Expected magnitude
Moderate for Raven specifically (~10-15 percentage points closure). No effect on Kinsel or subsonic cases.

---

## Candidate #3: Increase FINNED_BASE_K For High Span-Ratio Configurations

**Rank: 3 (medium evidence, addresses Kinsel and Raven jointly)**

### Mechanism
The finned-body base drag augmentation factor is:
```
augmentation = 1 + FINNED_BASE_K * finFactor * spanFactor * machFactor
```
Currently `FINNED_BASE_K = 0.55`, calibrated against the Basic Finner (4 rectangular fins, span/radius = 2.0). For Kinsel at M=2.4: augmentation = 1.55 (finFactor=1.0, spanFactor=1.0, machFactor=1.0). For Raven at M=1.1: augmentation = 1.30 (finFactor=0.75, spanFactor=1.0, machFactor=0.76).

Hoerner Ch. 16 and DATCOM 4.6.3.2 report 40-60% base drag augmentation for 4-fin configurations at M 1.5-3. The current K=0.55 is at the low end. For minimum-diameter rockets where fins span a large fraction of the base, the augmentation could be 50-80%.

**Proposed change**: Increase K from 0.55 to 0.65-0.70, OR make K depend on the fin-span-to-body-radius ratio (higher K when fins are relatively large compared to body). A span-dependent K is more physically defensible: larger fins relative to body diameter create stronger vortex structures at the base.

### File/method
- `BarrowmanDragCalculator.java` line 80: `FINNED_BASE_K = 0.55`
- Optionally: `calculateFinnedBaseAugmentation()` (line 928) to add span-ratio-dependent K

### Cases improved
- **Kinsel**: At K=0.65, augmentation rises from 1.55 to 1.65. Fin-can base Cd rises from 0.138 to ~0.147, adding ~2.5% to total Cd. Closes ~3 percentage points.
- **Raven**: At K=0.65, augmentation rises from 1.30 to 1.35. Body tube base Cd rises from 0.31 to ~0.32. Closes ~1-2 percentage points. Modest effect because Raven's machFactor at M=1.12 is only 0.76.
- **Basic Finner**: Augmentation rises from 1.55 to 1.65 at M=2.0. Base drag increases, *reducing* underprediction. This is the right direction.

### Benchmark risk
- **Basic Finner**: Improves (less underprediction). MAPE should decrease.
- **Subsonic cases (EZI-65, T&L, CalIsp1)**: Minimal effect because machFactor is 0.10-0.15 at M=0.55-0.65. Augmentation changes by <1%.
- **L500Roc (+8.6%, M=2.0)**: Could push slightly higher. Currently within tolerance. Need to verify it stays under 10%.

### How to falsify
1. Run `BasicFinnerDragBenchmarkTest` -- MAPE should decrease. If it increases, K is too high.
2. Run Kinsel and Raven diagnostics -- both should improve.
3. Run full SimVReal -- verify no subsonic case degrades and L500Roc stays within 10%.

### AST defensibility
**Medium-low.** The original K=0.55 is B-level (calibrated against Basic Finner + qualitative Hoerner). Increasing it without new external data is tuning. However, the change can be defended as "calibrated against expanded vehicle-level dataset" if the Basic Finner benchmark improves simultaneously. A span-dependent formulation is more defensible than a blanket increase because it connects to the physical mechanism (larger fins create stronger base disturbance).

### Expected magnitude
Small: ~2-3 percentage points for Kinsel, ~1-2 for Raven. Cumulative with Candidates #1 and #2.

---

## Candidate #4: Subsonic Base Drag Floor (L/D-Dependent)

**Rank: 4 (low-medium evidence, addresses EZI-65 and T&L)**

### Mechanism
EZI-65 (+16.1%) and T&L (+17.4%) are subsonic (M 0.55-0.61) with simple ogive-cylinder geometry. Body tube base drag is ~37% of total. The subsonic base drag model (`0.12 + 0.13*M^2`) gives Cd_base ~ 0.16 at M=0.55. For long body tubes (L/D > 15), the boundary layer at the base is thicker, increasing wake size and base suction.

Hoerner Ch. 3 shows that base drag for cylindrical afterbodies increases with L/D up to L/D ~ 6, then plateaus. However, for L/D > 15 with turbulent BL, the displacement thickness at the base can be delta*/D ~ 0.01-0.03, which should produce base drag 5-15% higher than the short-body correlation.

**However**: RASAero also overpredicts for both cases (+6.3% and +11.5%). This suggests part of the error is non-aerodynamic (mass/motor import, barometric altimeter bias, surface finish). A model fix that targets only the ORP-vs-reality gap (16% and 17%) would overfit if 6-11% of that gap is shared with RASAero.

### File/method
- `BarrowmanDragCalculator.java`: `calculateBaseCD(double m)` (line 1184)
- Would add an L/D-dependent correction factor to the subsonic branch

### Cases improved
- **EZI-65**: ~3-5 percentage points closure if base drag increases by 10-15%.
- **T&L**: Similar magnitude.
- **Other subsonic cases**: Byrum (+8.4%) could worsen slightly; CalIsp1 (-0.7%) could go more negative.

### Benchmark risk
- **CalIsp1** (-0.7%): Currently near-perfect. Any subsonic base drag increase will push it more negative. This is the primary risk -- it is a CLEAN case with excellent agreement.
- **Byrum** (+8.4%): Would worsen, potentially crossing 10% threshold.
- **All subsonic cases**: Uniformly affected.

### How to falsify
1. Run full SimVReal. If CalIsp1 degrades below -3% or Byrum crosses 10%, the fix is rejected.
2. The fix is only defensible if it closes EZI-65/T&L without worsening the subsonic cluster mean.
3. Check whether RASAero's error (+6.3%, +11.5%) accounts for most of the gap -- if so, the ORP model is not the problem.

### AST defensibility
**Low.** The subsonic base drag model (`0.12 + 0.13*M^2`) is well-established for cylindrical afterbodies. Adding an L/D correction is semi-empirical and risks overfitting to two cases. Since RASAero also overpredicts, the evidence points toward non-aerodynamic causes (barometric altimeter bias, motor performance variation, surface finish) rather than a systematic model error. The AST paper is better served by acknowledging these cases as "residual outliers with partially non-aerodynamic origin" than by adding a tuning knob.

### Expected magnitude
3-5 percentage points per case, but with significant risk of worsening the subsonic cluster.

---

## Candidate #5: Kinsel Expanding Shoulder Pressure Drag

**Rank: 5 (lowest evidence, addresses only Kinsel)**

### Mechanism
Kinsel has an expanding shoulder transition from 6.125" to 6.500" diameter. Currently, `SymmetricComponentCalc.java` line 450-452 explicitly zeroes wave drag for expanding shoulders (foreRadius > 0), which is correct for smooth Prandtl-Meyer expansion fans. However, the expansion creates a local pressure change that increases downstream boundary layer thickness and affects base drag -- effects not currently captured.

The step face area is `pi * (3.25^2 - 3.0625^2) = 3.72 in^2 = 0.0024 m^2`, or 11.2% of the reference area. The stagnation Cp at M=2.42 is ~0.46. A forward-facing step of this size should produce step drag of `0.46 * 0.112 ≈ 0.051` Cd. However, the ESDU 66011 step drag code (`calculateStepDrag`) checks `foreRadius - upstreamAftRadius` and for a smooth shoulder transition this difference is zero -- there is no step discontinuity.

The missing physics is: the expansion fan on the diverging shoulder creates a favorable pressure gradient locally, but the subsequent recompression at the shoulder-to-fin-can junction creates a pressure rise that the current code does not account for. This is an interaction effect that would require a new model.

### File/method
- `SymmetricComponentCalc.java` line 450-452: expanding shoulder zero-wave-drag logic
- Would need a new expansion-recompression interaction model

### Cases improved
- **Kinsel only**: ~5 percentage points maximum.

### Benchmark risk
- **Other expanding-shoulder cases**: Would need audit. MESOS booster had a shoulder that was previously overestimating drag (fixed by zeroing).
- Risk of reintroducing the MESOS-style overestimation that was previously fixed.

### How to falsify
Estimate the expansion-recompression drag analytically for the Kinsel geometry and compare against the gap. If the analytical estimate is < 2% of total Cd, the mechanism is insufficient.

### AST defensibility
**Low.** This would be a new semi-empirical model for an interaction effect that is not in DATCOM or standard references. High risk of being challenged as ad-hoc. Additionally, this addresses only one case (Kinsel) and Kinsel is parity-CONTAMINATED, making it harder to isolate the aerodynamic model contribution.

### Expected magnitude
Uncertain. Likely 2-5% of total Cd if the interaction is real, but the modeling uncertainty is high.

---

## Summary Ranking

| Rank | Candidate | Safety | Evidence | Cases | AST-defensible | Expected closure (pp) |
|---|---|---|---|---|---|---|
| 1 | Remove Lamb-Oberkampf Re correction | HIGH | D-level removal | Kinsel (+3-4), all M>1.3 | YES | 3-4 |
| 2 | Widen transonic base drag peak | MEDIUM | Needs Hoerner/ESDU data | Raven (+10-15), transonic cluster | CONDITIONAL on data | 10-15 (Raven) |
| 3 | Increase FINNED_BASE_K | MEDIUM-LOW | Basic Finner calibration | Kinsel (+2-3), Raven (+1-2), Basic Finner | CONDITIONAL on BF improvement | 2-3 |
| 4 | Subsonic base drag L/D correction | LOW | RASAero also misses | EZI-65 (+3-5), T&L (+3-5) | NO (tuning) | 3-5 |
| 5 | Kinsel shoulder interaction drag | LOW | No reference model | Kinsel only (+2-5) | NO (ad-hoc) | 2-5 |

## Recommended Implementation Order

1. **Candidate #1 first** -- it is the safest because it removes an unvalidated heuristic rather than adding a new one. The Devan-Ashwood A-level correlation stands on its own without Re correction.

2. **Candidate #2 second** -- but ONLY after a web search for Hoerner Fig. 3.19 or ESDU base drag data at M 1.0-1.3 to justify the peak adjustment. Without data, this is tuning and should not be implemented.

3. **Candidate #3 third** -- only if Candidates #1 and #2 are insufficient and the Basic Finner benchmark simultaneously improves.

4. **Candidates #4 and #5 are not recommended** for implementation. The subsonic outliers should be acknowledged as partially non-aerodynamic, and the Kinsel shoulder interaction is too speculative.

## Realistic Closure Expectations

Even implementing all three top candidates, the expected closure is:
- **Kinsel**: +35.1% -> ~+27% (removal of Re correction + increased K). Still outside 10%.
- **Raven**: +27.5% -> ~+12% (wider transonic peak + increased K). Near the 10% boundary.
- **EZI-65/T&L**: Unchanged at +16%/+17%.

This is insufficient for full closure but moves Raven within reach of 10% and demonstrates systematic improvement rather than case-by-case tuning. The AST paper would need to acknowledge the remaining outliers with documented mechanisms.

## Open Questions

1. **Is the Lamb-Oberkampf reference real?** A web search for "Lamb Oberkampf 1995 base drag Reynolds" would determine if this is a real paper with published data, which would change its anchoring level from D to potentially B.

2. **What is the actual transonic base drag peak for cylindrical afterbodies?** Hoerner Fig. 3.19, ESDU data sheets for blunt-base cylinders, or NACA TN 3393 transonic data would directly anchor any peak adjustment.

3. **How much of the subsonic outlier gap is non-aerodynamic?** A barometric altimeter bias study or motor performance variation analysis would bound this. If 10% of the 16-17% gap is non-aerodynamic, the model residual is only 6-7% and within acceptable limits.

4. **Should the Basic Finner underprediction be considered a blocker?** At MAPE 22.7%, the ORP underpredicts drag for a 30mm model by 14-31%. This is understood (Re mismatch, blunt TE not modeled), but any candidate that worsens it is suspect. The current gate of 30% provides headroom but is already generous.
