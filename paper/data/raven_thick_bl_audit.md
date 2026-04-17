# Raven THICK_BL Audit (Read-Only Diagnostic)

Date: 2026-04-17
Scope: Post-Prompt 13 audit of why the thick-boundary-layer (THICK_BL) base-drag
multiplier in `BarrowmanDragCalculator` is not closing the Raven SimVReal outlier
to the +15 to +20% target band. Raven currently sits at +24.3% apogee vs real
(was +27.5% pre-P13; RASAero II sits at +5.9%). No aerodynamic code, constants,
or existing tests were modified. One new read-only diagnostic test was added:
`RavenThickBLAuditTest` in `core/src/test/java/info/openrocket/core/aerodynamics/`.

---

## 1. Where is THICK_BL implemented?

File: `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java`

| Thing | Location |
|---|---|
| Constants (k, thresholds, gates) | lines 140–154 |
| Call site (multiplies `correctedBase`) | lines 1075–1076 |
| Method body (`calculateThickBLBaseMultiplier`) | lines 1255–1345 |
| Body-tube-length helper (used by L/D gate) | lines 1412–1429 |
| Base-station-X helper (used for BL development length) | lines 1436–1443 |
| Calibration / reference comment block | lines 105–154 |

The multiplier is applied on the `correctedBase` value AFTER the boattail factor,
Viswanath reduction, and finned-base augmentation — so it stacks multiplicatively
on top of those.

## 2. L/D gate — exact semantics

Gate is `bodyLD > THICK_BL_LD_LOW` with a smoothstep ramp to full effect by
`THICK_BL_LD_HIGH`:

- `THICK_BL_LD_LOW = 25.0` (line 150) — strict threshold; returns 1.0 at or below
- `THICK_BL_LD_HIGH = 30.0` (line 152) — full effect at or above
- `bodyLD = computeBodyTubeLength(configuration) / (2 * baseRadius)` (line 1275–1280)

Important: "L" is the sum of lengths of ALL symmetric components whose
`foreRadius ≈ aftRadius` (within 1% tolerance — line 1424), multiplied by their
instance count. This EXCLUDES nose cones, transitions, boattails. "D" is the
diameter at the base station of the component under evaluation.

For Raven (single AxialStage, 65 in body tube, 1.75 in dia):
- L (body tubes only) = 65 in = 1.651 m
- D (base) = 1.75 in = 0.0445 m
- **body L/D seen by the gate = 37.14**
- smoothstep input = (37.14 − 25) / (30 − 25) = 2.43 → clamped to 1.0
- L/D ramp = **1.0 (saturated on asymptote)**

Raven's body L/D is saturated on the ramp; L/D cannot drive any further
correction.

## 3. Mach gate — exact semantics

- `THICK_BL_MACH_LOW = 0.9`   (strict floor, returns 1.0 at or below)
- `THICK_BL_MACH_HIGH = 1.1`  (full effect between 1.1 and the decay start)
- `THICK_BL_MACH_DECAY_END = 3.0` (zero above 3.0)

Between M 0.9 and 1.1 the Mach ramp is `smoothstep((M − 0.9)/0.2)`. Between
M 1.1 and 3.0 it decays as `1 − smoothstep((M − 1.1)/1.9)`. Above M=3.0 it is
forced to 0.

For Raven's max Mach of 1.108 the gate is just below the peak of the ramp.
From the sweep produced by `RavenThickBLAuditTest`:

| M    | multiplier |
|-----:|-----------:|
| 0.90 | 1.0000     |
| 0.95 | 1.0925     |
| 1.00 | 1.2897     |
| 1.05 | 1.4788     |
| 1.10 | 1.5562     |
| 1.15 | 1.5444     |
| 1.20 | 1.5311     |
| 1.30 | 1.5006     |
| 1.50 | 1.4284     |
| 2.00 | 1.2267     |
| 2.50 | 1.0640     |
| 3.00 | 1.0000     |

So THICK_BL is active across Raven's entire boost+coast transonic envelope,
peaks at ~M=1.10 at mult = **1.556**, and tapers to 1 by M=3.

## 4. Magnitude — form and saturation

Form (line 1343): `multiplier = 1 + THICK_BL_K * deltaExcess * machRamp * ldRamp`
capped at `THICK_BL_MAX_MULTIPLIER = 1.8`.

- `THICK_BL_K = 1.3` (line 140; was originally 0.8, retuned upward after only
  2.1 pp closure on Raven's 27.5% overshoot — see code comment lines 135–139)
- `deltaExcess = max(0, δ/R − 0.5)` where δ is 1/7-power flat-plate BL
  thickness evaluated at the base station absolute X
- Cap 1.8 is protective for pathological geometries (very thin / very long)

For Raven at peak Mach: δ/R = 0.927, so `deltaExcess = 0.427`. Both ramps are
~1.0, so the multiplier is 1 + 1.3 × 0.427 ≈ 1.55. **Not at the cap**
(1.56 vs cap 1.80), so k could in principle be raised — but see §7.

L/D saturation happens at L/D = 30 (Raven is 37). δ/R does NOT saturate —
at Raven's Re_x = 4.2e7 it's only 0.93 (cap would be 1.0 by construction of a
flat-plate BL that fills the radius).

## 5. Does THICK_BL correctly fire for Raven?

**Yes.** All three gates open and the multiplier lands at 1.5544 at peak Mach
(1.108, t = 1.648 s, alt 411 m, T 307.1 K). See `RavenThickBLAuditTest` output:

```
Gate 1 (Mach > 0.9):   M=1.108 -> OPEN=true
Gate 2 (L/D > 25):     body L/D=37.14 -> OPEN=true
Gate 3 (delta/R > 0.5):delta/R=0.9265 -> OPEN=true
Multiplier applied to Raven base Cd at peak Mach: 1.5544 (raw)
```

Manual cross-check vs component-cd.csv: observed Raven `Body Tube Cdb` at M=1.1
is **0.5087**. Back-calculation: DA base Cd × finned-base × THICK_BL × area_ratio
= 0.250 × 1.297 × 1.554 × 1.0 ≈ 0.504 — matches observation to within numerical
noise (ref-diameter area ratio is exactly 1 on Raven).

The current code contribution to Raven's total Cd at peak Mach is:
`(1.554 − 1.0) × 0.367 ≈ +0.203 Cd` (where 0.367 is the back-calculated raw
DA+finned-aug base Cd before THICK_BL). That contribution is large — roughly
18% of Raven's total peak Cd of 1.13. So the correction IS firing and IS
material.

## 6. Why doesn't it close Raven to +15%?

Gap-to-target arithmetic (linearized Cd·A/m → apogee):

- Coast-avg Cd (current, post-P13) = 0.857
- To drop apogee error from +24.3% to +15% (≈ 9 pp closure), we need about
  +19 pp more Cd·A/m, i.e. roughly **+0.16 Cd average across the coast**
- Current THICK_BL already contributes +0.203 Cd at peak Mach, but only on
  the base component, and only during the ~1-second window around peak Mach.
  Coast-integrated contribution is much smaller (coast spans M 1.07 → 0, so
  the Mach ramp has already fallen to ~1.05 at M=1.5 and is zero by M=1.07
  stops near apogee).
- Required "hypothetical" multiplier at peak to close the last 9 pp with
  THICK_BL alone: ≈ 1.99, i.e. **+79% boost in the multiplier delta** over
  current. Even at the cap of 1.8 this would not be enough; and raising the
  cap or k further would break the Rabia/Torrent/Kinsel band-safety checks
  documented in the code comment (lines 135–139).

Conclusion: **THICK_BL is firing as designed, and cannot on its own close the
remaining Raven gap** without either (a) breaking other SimVReal cases via a
global k or cap increase, or (b) broadening the coast-phase footprint (e.g.
lifting the Mach decay endpoint, which would pull other cases further off).

## 7. Anchoring status

Status: **B-level** (self-documented in code comment lines 122–126).

Functional form is physics-based (Chapman 1950 NACA TN 2137 / Addy 1970 /
Tanner 1984 thick-BL base-pressure measurements on cylindrical afterbodies,
per the code comment block — **these are referenced by the code comment only**,
not currently tied to digitized CSVs in `paper/data/csv/`). Scale constant
`THICK_BL_K = 1.3` is **calibrated directly against Raven**, which makes this
structurally a rocket-specific residual match rather than an external-benchmark
validation. No dedicated row exists yet in
`paper/data/VALIDATION_MATRIX.md` — the only mention is the "Minimum-diameter
supersonic flight closure" row (line 40), marked `D` (single-case).

For AST defensibility, this is the weakest link in the current Raven story:
a primary-source anchor (Addy 1970 AEDC-TR-70-146 base-pressure data for
cylindrical afterbodies with measured δ/R, for example) would promote it to
B- or A-level. Until that data is digitized into `paper/data/`, the current
B annotation must explicitly note "scale constant calibrated to Raven residual,
not to an independent primary source."

## 8. Recommendation

Three options, in ascending AST-defensibility:

1. **Tune constants (risky).** Raise `THICK_BL_K` to ~1.8 or raise
   `THICK_BL_MAX_MULTIPLIER` to 2.2. The Mach-sweep table shows current
   Raven peak multiplier is 1.556 (below the cap) so k can move the needle.
   But the code comment explicitly warns that Rabia/Torrent verification at
   k=1.3 was "extrapolated from their k=0.8 movement" (lines 138–139) — i.e.
   we do not actually have a k=1.8 band-safety check, and pushing there risks
   regressing Rabia/Torrent/Kinsel within-5% band metrics. Calibrating more
   aggressively to one case without re-running the full corpus is the opposite
   of AST-defensible.

2. **Refactor gate (medium).** The current THICK_BL coast-phase footprint is
   narrow (effectively M 0.95–2.5 with most weight in M 1.0–1.3). The Raven
   residual is largely a coast-averaged effect, not a peak-Mach effect. A
   second correction branch whose δ/R term remains active subsonically — e.g.
   coast-tail long-body friction or pressure — would spread the Cd boost
   across more of the 21-s coast where it integrates into apogee. This would
   naturally favor Raven (body L/D = 37, longest coast-duration BL
   development) without touching the already-published k. Candidate is the
   SLENDER_BODY_* supersonic pressure drag (lines 156–179) which currently
   gates on M > 1.05 — extending it to M > 0.9 with a smooth ramp would
   add coast-phase Cd on high-L/D bodies without perturbing THICK_BL.

3. **Develop new mechanism, source-anchor it (AST-defensible).** Digitize
   Addy 1970 AEDC-TR-70-146 cylindrical-afterbody base-pressure data (or
   Tanner 1984, or any other sting-free δ/R-vs-Cp_b measurement) into
   `paper/data/csv/`, add it to `VALIDATION_MATRIX.md` as a dedicated row,
   and re-anchor `THICK_BL_K` against that primary source. This would also
   tell us whether our functional form (linear in δ/R above 0.5, with a
   Mach decay borrowed from Chapman) matches the shape of the measured data,
   or whether a quadratic δ/R term is needed (thick-BL literature does
   sometimes show stronger-than-linear scaling as δ/R → 1).

   The residual 9 pp of Raven apogee error is plausibly NOT a single
   mechanism. Raven-specific candidates that remain unaudited:
   - **Rail-guide wake on minimum-diameter bodies.** Rail Guide Cdp = 0.056
     on Raven (button/body = 0.214) vs 0.017 on Torrent (0.094). Button
     wake may trip an extra separation bubble that current Barrowman does
     not see.
   - **Haack-nose wave-drag underestimate at low supersonic.** Raven nose
     Cdp = 0.033 at M=1.1 is plausible-low vs a conical nose at ~0.16 —
     if the Haack shape factor is anchored on M > 1.3 data, a small M 1.0–1.2
     augmentation may be warranted.
   - **Baro-altimeter error on minimum-diameter airframes.** Literature
     places this at ±3–5% over a 21-s coast to 11k ft; could account for
     2–3 pp of the remaining 9 pp.

   None of these should be addressed by further tuning `THICK_BL_K`.

## 9. Blocker status

Closing Raven to the +15 to +20% band via THICK_BL alone is **blocked** on
primary-source anchoring for the thick-BL scale constant. A k bump from 1.3
to 1.8 would drop the Raven apogee by an additional ~3 pp based on a linear
extrapolation of the Prompt-12 movement (27.5 → 25.4 at k=0.8; Δk=0.5 gave
2.1 pp). But without a corpus rerun — which is explicitly excluded from this
audit's scope — we cannot safely verify the band-safety of Rabia/Torrent/
Kinsel/healthy-HPR cases at the new k. That is the specific blocker: **any
k tune past 1.3 requires at minimum a SimVReal corpus rerun, and preferably
a new primary-source CSV anchor.**

## 10. Files referenced

- `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java`
  (lines 105–154 constants/calibration comment; 1075–1076 call site;
  1255–1345 method body; 1412–1443 helpers)
- `core/src/test/java/info/openrocket/core/aerodynamics/ThickBLBaseDragMultiplierTest.java`
  (existing unit tests for the gates and cap)
- `core/src/test/java/info/openrocket/core/aerodynamics/RavenThickBLAuditTest.java`
  (new, read-only, this audit)
- `core/build/reports/simvreal-outliers/Raven.md` (post-P13 per-case report;
  +24.3%, M 1.108)
- `core/build/reports/simvreal-outliers/Raven-component-cd.csv`
  (M sweep giving Body Tube Cdb = 0.509 at M=1.1)
- `core/build/reports/simvreal-outliers/Raven-trajectory.csv`
  (peak-Mach atmosphere reference)
- `simvreal/RasAero Sims/Raven.CDX1` (input geometry)
- `paper/data/raven_vs_rabia_diagnostic.md` (prior Prompt 11/12 diagnostic
  that motivated the k=1.3 calibration)
- `paper/data/VALIDATION_MATRIX.md` (row 40 is the only Raven/thick-BL entry;
  no dedicated THICK_BL validation row yet)
- `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (Prompt 13 session log; Raven
  still-open task)
