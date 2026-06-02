# Published-CFD Comparator: Sznajder 2025 ANSYS Fluent pitch-damping vs ORP on the Basic Finner

**Date:** 2026-05-11
**Status:** B-level CFD comparison. ORP overpredicts |damping| sharply in the
transonic peak (M 1.05-1.12), underpredicts by 25-36 % across the supersonic
band (M 1.3-4.5). The shape and sign of the residual match the existing
Bhagwandin & Sahu 2013 ANF benchmark and the in-repo
`BasicFinnerCmqBenchmarkTest` finding.

## Source paper

Sznajder, J. "Computational Determination of Dynamic Stability Derivatives."
*Transactions on Aerospace Research* **2025**, Vol. 281, No. 4, pp. 98-121.
eISSN 2545-2835. DOI: 10.2478/tar-2025-0021. Article category: research article.

Author affiliation: Lukasiewicz Research Network - Institute of Aviation,
110/114 Krakowska Ave., 02-256 Warsaw, Poland. ORCID 0000-0003-3478-490X.

Citation taken from the title page of the PDF in repo
(`paper/data/pdf/Computational_determination_of_dyna.pdf`). Open-access
journal article; the Sciendo/De Gruyter platform marks it CC-licensed
research output.

## CFD method (verified from PDF Sections 2-3)

Sznajder evaluates three CFD-based approaches to dynamic stability derivatives:

1. **MRF -- Moving Reference Frame** (Section 2.2, page 105-106). Steady-state
   Navier-Stokes solved in a non-inertial frame attached to the rotating body.
   Implemented via ANSYS Fluent's MRF capability. Two rotational velocities
   used (+/- 1 rad/s) and a finite-difference quotient extracts Cmq.
2. **FOM -- Forced Oscillation Method** (Section 2.1, page 99-101). Prescribed
   harmonic pitch motion using ANSYS Fluent's Dynamic Mesh; harmonic-component
   regression separates Cmq and Cm_alphadot. Reduced frequency k = 0.025.
   Time-step convergence (Table 1, page 110) shows < 0.15 % change in Cmq
   between dt* = 0.1 and dt* = 0.05.
3. **IRM -- Indicial Response Method** (Section 2.3, page 106). Step
   perturbation in AoA or pitch rate; the transient lift / moment history
   is integrated to recover the acceleration derivatives. No direct
   experimental analogue.

All three methods are applied to the **Basic Finner** at M = 0.9-5.0,
AoA = 0 deg (and to the SZD-9 Bocian glider at M_low, AoA 0-20 deg, which
is out of scope for this comparator).

## Geometry confirmation (Figure 2 + Section 3, page 107)

The Sznajder model is the standard **Army-Navy Basic Finner (ANF)**:

- Reference diameter d = 30 mm (per Sznajder Section 3, citing
  experimental references [6] and [14])
- 10 deg half-angle conical nose
- Rectangular fins
- Total L/D = 10 (standard ANF dimensions; the paper cites [13] for
  details)
- **CG at 6.1 d from the nose tip** (page 107)

This matches OpenRocket Plus's `SupersonicTestRockets.makeBasicFinner()`
geometry caliber-for-caliber. The only meaningful difference is the CG
reference:

- Sznajder: CG = 6.1 d
- ADA636861 / BasicFinnerCmqBenchmarkTest: CG = 5.5 d
- Difference 0.6 d translates to a small Cmq offset via the axis-transfer
  relation (Tobak TN 3788 eq. 54). This is a ~5-10 % systematic in the
  ORP-vs-Sznajder comparison.

## Experimental reference cited by Sznajder

Sznajder reference [14], cited in Fig. 3 and Fig. 7 captions:

> Dupuis, A. D.; Hathaway, W. "Aeroballistic Range Tests of the Basic Finner
> Reference Projectile at Supersonic Velocities," DREV-TM-9703, Defence
> Research Establishment Valcartier (Quebec), 1997.

This is identical (per DTIC) to ADA636861, the existing experimental anchor
for ORP's `BasicFinnerCmqBenchmarkTest`. Sznajder and ORP are therefore
comparing against the same experimental ground truth, by independent
extractions of the same dataset.

## Figures digitized

(See `paper/data/csv/sznajder_anf_cmq_cfd_2025.csv` for the full digitization.)

| Figure | Page | Content | Series |
|--------|------|---------|--------|
| Fig. 3 | 108 | Cmq vs Mach (M 0.9-5.0) | MRF (red X), FOM (red +), experiment (blue circles) |
| Fig. 6 | 111 | Cm_alphadot vs Mach (M 0.9-5.0) | FOM (red X+line), IRM (blue diamonds) |
| Fig. 7 | 111 | (Cmq + Cm_alphadot) vs Mach | MRF+FOM combined (red X+line), exp-avg-fits (green diamonds), exp-single (blue circles) |

Read uncertainty (per-coefficient, hand-read from PDF, no WebPlotDigitizer):
- Cmq:           +/- 15 (Fig. 3 grid 100; symbol size ~3 % of full scale)
- Cm_alphadot:   +/- 8  (Fig. 6 grid 50; transonic peak region noisier)
- Sum total:     +/- 20 (Fig. 7 grid 100; experimental scatter dominates)

## Key paper-internal findings

(All quotes / numbers verified against the PDF text.)

1. **Cmq computational agreement (page 108):** "The difference between results
   of the two computational approaches applied is approximately 2.5 % of the
   MRF value at Mach numbers above 2.5, and approximately 4 % at lower
   Mach numbers, close to 1." MRF and FOM agree to within ~3 % overall.

2. **Time-step convergence of FOM (Table 1, page 110):** dt* = 0.05 to 0.3,
   change in Cmq is 2.47 % (largest at largest step). Practical convergence
   reached at dt* = 0.1 (-0.15 % change vs dt* = 0.05).

3. **Discontinuity at M = 1.2 in Cm_alphadot and the sum (page 111):**
   "Very good agreement between the results of both methods is visible,
   except at a single point at M = 1.2, where both methods predict a sharp,
   discontinuous rise of the derivative. This discontinuity also appears
   in the experimental data shown in Figure 7." Sznajder leaves this as
   a physical feature rather than an artefact.

4. **Sum vs experiment (page 119, Conclusions):** "For the damping-sum
   derivative (Cm_alphadot + Cmq), the computational results were within
   the scatter of the available experimental data. The only significant
   discrepancy between the Indicial Response and Forced Oscillation methods
   occurred at M = 1.2."

## ORP-vs-Sznajder comparator (the actual comparison)

The Sznajder CFD provides Cmq separately from Cm_alphadot. The natural ORP
analogue is `Cmq + Cm_alphadot` (since ORP hard-codes `Cm_alphadot = 0.4 * Cmq`
in `BarrowmanStabilityCalculator`, and the
`BasicFinnerCmqBenchmarkTest` uses the combined value). Since ORP's
combined damping was already computed for the Bhagwandin & Sahu 2013
comparator at the same Mach grid (with `k_transonic` active and CG
at 5.5 d), we reuse those values directly rather than re-running the
same simulator twice.

Sznajder MRF+FOM sum (digitized from Fig. 7, interpolated to ORP-grid Mach)
vs ORP combined damping:

| Mach | Sznajder MRF+FOM | ORP combined | delta | delta_pct | notes |
|------|-----------------:|-------------:|------:|----------:|-------|
| 1.08 | -433.0 | -1127.4 | -694.4 | +160.4 % | k_transonic peak |
| 1.11 | -399.0 |  -843.3 | -444.3 | +111.4 % | k_transonic tail |
| 1.29 | -471.0 |  -348.3 | +122.7 |  -26.1 % | |
| 1.50 | -440.0 |  -287.5 | +152.5 |  -34.7 % | |
| 2.00 | -355.0 |  -226.3 | +128.7 |  -36.3 % | |
| 2.50 | -289.2 |  -192.7 |  +96.5 |  -33.4 % | |
| 3.00 | -260.0 |  -171.5 |  +88.5 |  -34.0 % | |
| 3.50 | -245.7 |  -156.8 |  +88.9 |  -36.2 % | |
| 4.00 | -210.0 |  -146.0 |  +64.0 |  -30.5 % | |
| 4.50 | -190.0 |  -137.7 |  +52.3 |  -27.5 % | |

- **MAPE (all 10 points):** 53.0 %
- **MAPE excluding 2 transonic-overshoot points (M >= 1.29):** 31.6 %
- **Sign:** ORP under predicts |damping| across the supersonic band by 25-36 %
  (consistent across M 1.29-4.5).
- **Transonic peak:** ORP over predicts |damping| by 110-160 % at M 1.08-1.11
  due to the `k_transonic = 1 + 2.5 exp(-((M-1)/0.15)^2)` Gaussian
  augmentation in `BarrowmanStabilityCalculator`. Sznajder does NOT show
  a comparable peak in the (Cmq + Cm_alphadot) sum, despite the M = 1.2
  discontinuity in Cm_alphadot alone.

## Interpretation

This is **not a clean validation** -- ORP differs from Sznajder by 27-36 %
in the supersonic band and by 110-160 % in the transonic peak. Three points
anchor the interpretation:

1. **Direction and magnitude match the existing Bhagwandin & Sahu 2013
   comparator.** That benchmark
   (`paper/data/csv/bhagwandin_anf_cmq_comparator_2026_05_02.csv`)
   reports MAPE 50.78 % over 13 points on the same ANF geometry against
   independent ARL-TR-6725 ANSYS Fluent CFD, with the same direction
   (ORP underpredicts |damping| supersonically, overshoots transonically).
   Sznajder is a second-CFD anchor on the same geometry, confirming the
   gap is robust to the choice of CFD reference. The result is honest
   publication evidence, not closure.

2. **The transonic overshoot is a documented `k_transonic` calibration
   issue, not a Sznajder-specific finding.** The peak-amplitude of 2.5x
   and Gaussian width 0.15 are heuristic constants documented in
   `BasicFinnerCmqBenchmarkTest`. Sznajder Figure 7 shows the experimental
   sum at M 1.1 ranges from -325 (avg of multi-fit) to -605 (single-test
   outlier); the line CFD value is ~-395. ORP's -1127 at M = 1.08 sits
   well below even the lowest experimental scatter and is clearly a
   miscalibration of the augmentation amplitude.

3. **The 30 % supersonic underprediction is a Mach-trend match.** Both
   Sznajder MRF and ORP show monotone decay of |damping| with Mach
   from 1.5 to 4.5, with the same curvature. The constant-factor
   ratio (~0.67) suggests the strip-theory CN_a coefficient or the
   ORP_CMADOT_OVER_CMQ = 0.4 factor are jointly low by ~50 % in absolute
   terms, not a regime-specific failure.

## Reviewer-defensible language for the JSR paper

> "B-level CFD comparison: OpenRocket Plus was compared against the
> open-access ANSYS Fluent MRF / FOM / IRM CFD of Sznajder (2025) on
> the Army-Navy Basic Finner over Mach 0.9-4.5 at zero AoA. Sznajder
> provides Cmq and Cm_alphadot separately from three independent CFD
> methods (steady non-inertial frame, forced oscillation, indicial
> response); the present comparison uses the (Cmq + Cm_alphadot) sum
> from the MRF+FOM combination since that is the experimentally
> observable quantity (matching the ADA636861 / DREV-TM-9703 free-flight
> data Sznajder also compares against). Excluding two
> transonic-peak points (M = 1.08, 1.11) where ORP's `k_transonic`
> Gaussian augmentation overshoots, ORP underpredicts |damping| by
> 27-36 % across M = 1.29-4.5 with sign and Mach-trend correct
> (MAPE 31.6 % on 8 points). The shape and direction of this residual
> match the existing ARL-TR-6725 Bhagwandin & Sahu CFD comparator on
> the same geometry (MAPE 50.78 % over 13 points), with both CFD
> sources independently showing ORP systematically conservative in
> the supersonic pitch-damping prediction."

## Honest caveats

- **CG mismatch (0.6 d).** Sznajder uses CG = 6.1 d, ORP+ADA636861+
  Bhagwandin used CG = 5.5 d. Axis-transfer per Tobak TN 3788 eq. 54
  introduces a ~5-10 % systematic; the supersonic-band MAPE 31.6 %
  is therefore loose by that amount but the sign of the residual
  is unaffected.
- **Hand-read digitization with substantial experimental scatter.** Fig. 7
  alone shows experimental sum values spanning -158 to -605 at M near 1.1.
  Sznajder smooths this in his Fig. 7 line; ORP's transonic overshoot
  is unambiguous regardless.
- **The Bhagwandin comparator used ORP's `getCmq()` + `getCmAlphaDot()`
  combined value with `k_transonic` ON.** That is the value the simulator
  uses in flight; we re-use the same numbers here without re-running.
- **No Java comparator test added.** The existing `BasicFinnerCmqBenchmarkTest`
  (vs ADA636861 experiment) and the Bhagwandin & Sahu comparator (vs
  ARL-TR-6725 CFD) already exercise ORP's Basic Finner Cmq pipeline.
  A separate `SznajderANFCmqCfdComparatorTest` would re-emit the same
  ORP numbers against a third reference; that work item is queued but
  not exercised here.

## Direct comparison to existing TobakCmqBenchmarkTest

The existing `TobakCmqBenchmarkTest` in repo validates ORP's strip-theory
Cmq against the closed-form Tobak TN 3788 result for a **cone-only
geometry** (10 deg and 20 deg half-angle cones, no body tube, no fins).
That test is therefore not an apples-to-apples comparator for Sznajder
Figure 3, which is the full Basic Finner (cone + cylinder + fins).

The right ORP-side comparator for Sznajder is the existing
`BasicFinnerCmqBenchmarkTest`, which:
- Uses `SupersonicTestRockets.makeBasicFinner()` (cone + cylinder + fins).
- Sets CG via `setPitchCenter(0.1650 m)` = 5.500 cal.
- Runs `getCmq() + getCmAlphaDot()` over Mach 0.5-4.5.
- Outputs `paper/data/csv/ada636861_basic_finner_cmq.csv` (the source for
  the ORP column values in the comparator table above).

The above comparator table is therefore equivalent to a hypothetical
`SznajderANFCmqCfdComparatorTest` that reuses the
`BasicFinnerCmqBenchmarkTest` ORP outputs and overlays Sznajder Fig. 7.

## Files

- Digitized CFD points: `paper/data/csv/sznajder_anf_cmq_cfd_2025.csv`
- Comparator output: `paper/data/csv/sznajder_anf_cmq_comparator_2026_05_11.csv`
- Source PDF: `paper/data/pdf/Computational_determination_of_dyna.pdf`
- This memo: `paper/data/md/sznajder_anf_cmq_cfd_comparator_2026_05_11.md`
- Existing ORP benchmark (re-used as ORP-side input):
  - Test: `core/src/test/java/info/openrocket/core/aerodynamics/BasicFinnerCmqBenchmarkTest.java`
  - Output CSV: `paper/data/csv/ada636861_basic_finner_cmq.csv`
  - Bhagwandin comparator (parallel CFD source): `paper/data/csv/bhagwandin_anf_cmq_comparator_2026_05_02.csv`
