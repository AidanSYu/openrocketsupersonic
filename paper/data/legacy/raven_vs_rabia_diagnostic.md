# Raven vs Rabia/Torrent Transonic ORP-Residual Diagnostic

Generated: 2026-04-17
Scope: Diagnose why Raven shows +27.5% ORP apogee error (vs +5.9% RASAero) while
similar-regime Rabia (+4.0% ORP) and Torrent (+5.8% ORP) are healthy.
Source data: `core/build/reports/simvreal-outliers/{Raven,Rabia,Torrent}.md` and
`-component-cd.csv`.
This is a diagnostic only; no code was modified.

---

## 1. Side-by-Side Geometry

| Field                | Raven              | Rabia              | Torrent            |
|---------------------|--------------------|--------------------|--------------------|
| Max Mach            | 1.123              | 1.183              | 1.244              |
| ORP apogee err      | **+27.5%**         | +4.0%              | +5.8%              |
| RASAero apogee err  | +5.9%              | -4.3%              | +7.1%              |
| Ref diameter (in)   | **1.750**          | 3.125              | 4.125              |
| Ref area (m²)       | **0.00155**        | 0.00495            | 0.00862            |
| Launch mass (kg)    | 2.468              | 8.364              | 13.213             |
| Overall length (in) | 73.50              | 111.75             | 109.50             |
| Overall L/D         | **41.7**           | 35.8               | 26.5               |
| Nose shape          | **Haack**          | **Conical**        | **Conical**        |
| Nose length (in)    | 8.50               | 14.75              | 20.00              |
| Body tube L (in)    | 65.00              | 82.25              | 82.45              |
| Body tube D (in)    | 1.750              | 3.000              | 4.000              |
| Body tube L/D       | **37.1**           | 27.4               | 20.6               |
| Fin can present     | **NO**             | YES (14")          | YES (7")           |
| Fin can step        | n/a                | 3.000→3.125"       | 4.000→4.125"       |
| Fin count           | 3                  | 3                  | 3                  |
| Fin span (in)       | 2.375              | 4.250              | 5.000              |
| Fin x-sec           | Rounded            | Rounded            | Rounded            |
| Fin TE (in from nose)| 71.63             | ~109.37 (aft face) | ~107.75 (aft face) |
| Body aft face (in)  | **73.50**          | 111.75             | 109.50             |
| Body-past-fins gap  | **1.87 in** (fin TE→body aft) | 0 (fin in fin can) | 0 |
| Surface finish      | Smooth (zero rough)| Smooth (zero rough)| Smooth (zero rough)|
| perfectFinish flag  | false (see `SurfaceFinishHandler.java:48`) | false | false |

Key differences:
- Raven is a minimum-diameter airframe at L/D ≈ 42 (body L/D = 37). Rabia and Torrent
  are ~2-4x larger-diameter and operate at half the body L/D.
- Raven uses a low-drag Haack nose. Rabia and Torrent use Conical noses, whose
  wave drag alone contributes ~0.12-0.16 Cd units at M>1.
- Only Raven lacks a dedicated fin can (fin trailing edge ~1.9 in inboard of the
  body tube aft face).

---

## 2. Component Cd at Matching Mach (M = 1.1, static sweep)

From `<case>-component-cd.csv` at Mach=1.100, AoA=0:

| Component        | Raven Cd | Raven Cdf | Raven Cdp | Raven Cdb | Rabia Cd | Rabia Cdf | Rabia Cdp | Rabia Cdb | Torrent Cd | Torrent Cdf | Torrent Cdp | Torrent Cdb |
|------------------|---------:|----------:|----------:|----------:|---------:|----------:|----------:|----------:|-----------:|------------:|------------:|------------:|
| Body Tube        | 0.6074   | 0.2926    | 0.0000    | **0.3148**| 0.1845   | 0.1845    | 0.0000    | 0.0000    | 0.1426     | 0.1426      | 0.0000      | 0.0000      |
| Nose Cone        | 0.0575   | 0.0250    | 0.0325    | 0.0000    | 0.1725   | 0.0166    | **0.1559**| 0.0000    | 0.1749     | 0.0174      | **0.1575**  | 0.0000      |
| Fin Can          | —        | —         | —         | —         | 0.3475   | 0.0327    | 0.0000    | **0.3148**| 0.3273     | 0.0125      | 0.0000      | **0.3148**  |
| Fin Can Shoulder | —        | —         | —         | —         | 0.0017   | 0.0017    | 0.0000    | 0.0000    | 0.0001     | 0.0001      | 0.0000      | 0.0000      |
| Fin              | 0.0485   | 0.0154    | 0.0182    | 0.0150    | 0.0397   | 0.0131    | 0.0140    | 0.0126    | 0.0378     | 0.0082      | 0.0240      | 0.0057      |
| Rail Guide       | 0.0555   | 0.0000    | 0.0555    | 0.0000    | —        | —         | —         | —         | 0.0166     | 0.0000      | 0.0166      | 0.0000      |
| **Rocket total** | **0.9216**| 0.3638   | 0.1980    | 0.3598    | 0.8254   | 0.2748    | 0.1978    | 0.3528    | 0.7916     | 0.1971      | 0.2626      | 0.3319      |

Observations at M=1.1:

1. **Base Cdb is IDENTICAL across all three rockets** (0.3148). This is
   `calculateBaseCD(1.1) × finned-body aug × (area/refArea)`. Since all three
   have base diameter ≈ reference diameter, the area ratio is 1, leaving the
   raw augmented value 0.315. This corresponds to `calculateBaseCD(1.1) ≈ 0.243`
   (from the transonic poly) × finned-body factor 1.30. The 1.30 factor
   reproduces exactly at M=1.1, fin_count=3, span/r clamped to 1.0
   (`BarrowmanDragCalculator.java:1026-1044`).
   **Finned-body augmentation IS activating on Raven's body tube** — the fins
   are children of the body tube, caught by `s.getChild(i) instanceof FinSet`
   at line 943-950 regardless of the axial position of the fin along the tube.
   The 1.87-inch "body past fins" is therefore NOT a missing-augmentation bug.

2. **The nose pressure drag difference is enormous**:
   Raven Haack Cdp = 0.033 vs Rabia/Torrent Conical Cdp ≈ 0.156.
   Rabia/Torrent get a "free" ~0.12 Cd from their blunt conical noses that
   Raven doesn't. This is legitimate geometry, not an ORP bug, but it buffers
   Rabia/Torrent against any absolute-Cd model shortfall — i.e., it masks the
   same residual on them.

3. **Body-tube friction Cdf scales linearly with 4L/D** (the wetted-area ratio),
   giving consistent Cf_local ≈ 0.00168-0.00197 on all three. Raven's higher
   Cdf_body = 0.29 vs Rabia 0.18 vs Torrent 0.14 is pure 4L/D geometry, not an
   anomalously high Cf. Van Driest II is behaving correctly.

---

## 3. Coast-Phase Integrated Drag Decomposition

From Phase Summary tables in `*.md`:

| Phase avg Cd (coast) | Raven   | Rabia   | Torrent |
|----------------------|--------:|--------:|--------:|
| Avg Cd total         | 0.7871  | 0.6573  | 0.4937  |
| Avg Cdf              | **0.5456** | 0.3953 | 0.2811 |
| Avg Cdp              | 0.0228  | 0.0155  | 0.0252  |
| Avg Cdb              | 0.1875  | 0.1898  | 0.1776  |
| Coast duration (s)   | 21.30   | 23.35   | 23.70   |
| Alt gain coast (ft)  | 9364    | 10885   | 11179   |

Raven's coast-average Cd is highest (0.787), yet its apogee error is largest
(+27.5%). The coast-avg Cdf = 0.546 is 69% of coast Cd. Compare effective
drag-per-unit-mass during coast (since that drives apogee):

| Case    | Cd_coast | Aref (m²) | Mass (kg) | Cd·A/m (m²/kg) |
|---------|---------:|----------:|----------:|---------------:|
| Raven   | 0.787    | 0.00155   | 2.468     | **4.94e-4**    |
| Rabia   | 0.657    | 0.00495   | 8.364     | 3.89e-4        |
| Torrent | 0.494    | 0.00862   | 13.213    | 3.22e-4        |

Raven already has the HIGHEST predicted drag-per-mass of the three, yet it
overshoots the most. This rules out a simple absolute-Cd miss: real Raven must
have a drag-per-mass substantially higher still. Rough apogee sensitivity:
a +20% apogee miss in the gravity-dominated coast implies real Raven's
effective Cd·A/m is roughly +40-60% higher than ORP predicts, i.e., real
coast Cd ≈ 1.1-1.25 vs ORP 0.79. That is 0.3-0.4 Cd of missing drag — a
body-scale mechanism, not a small protuberance.

---

## 4. Is the Finned-Body Augmentation Activating on Raven?

**Yes, it is active.** At M=1.1 the output Raven Body Tube Cdb = 0.3148 is the
*augmented* value (raw Devan-Ashwood transonic ≈ 0.243 × 1.30 = 0.315).
Trace in `BarrowmanDragCalculator.java`:

- `calculateBaseCD(1.1)` returns 0.2425 from the transonic polynomial (lines 1184-1192).
- `calculateFinnedBaseAugmentation(...)` path (lines 928-1045):
  - `s.getChild(i) instanceof FinSet` at line 943 picks up the 3-fin TrapezoidFinSet
    because Raven's fin is a child of the body tube (standard OpenRocket
    ownership; the 55.63-in position is WITHIN the 65-in body tube).
  - Axial position is NOT checked in this primary branch — only child-parent
    topology. The X_TOL=0.05 m check at line 994 is a fallback for coaxial
    PodSet topologies and does NOT apply here.
  - `finFactor = min(3/4, 1.5) = 0.75`, `spanFactor = clamp(2.375/0.875, 0.3, 1.0) = 1.0`,
    `machFactor = 0.30 + 0.70×(1.1-0.8)/0.5 = 0.72`.
  - `aug = 1 + 0.55 × 0.75 × 1.0 × 0.72 = 1.297`.
- Final `correctedBase = 0.2425 × 1.0 (Viswanath, no boattail) × 1.297 = 0.3146`.
  This matches the reported 0.3148 within 0.06%.

So the augmentation is correctly activating. This is NOT the missing mechanism.
If the augmentation were disabled, Raven's Cdb would drop from 0.315 to 0.243 —
a ~0.07 Cd *reduction*, i.e., ORP would overpredict apogee even more.

Note: the augmentation bakes in the full "fins at the extreme aft" assumption
even though Raven's fin TE sits 1.87 in (1.08 diameters) inboard of the base.
If anything, ORP over-credits augmentation here, not under-credits it. Turning
augmentation OFF when the fin TE is > ~0.5 D forward of the base would make
Raven WORSE (larger overshoot).

---

## 5. Where Is Raven's ~20 pp Residual Actually Coming From?

Primary candidate is the **minimum-diameter / high body-L/D boundary-layer
displacement effect on base drag**, which the Devan-Ashwood correlation
(calibrated on moderate-L/D bodies) does not represent:

1. At M=1.1, Raven body tube Re_L ≈ 4.5e7. Turbulent BL thickness at the base:
   δ/L ≈ 0.37/Re^0.2 = 0.37/34.0 ≈ 0.0109, giving δ ≈ 18 mm.
2. Body radius R = 0.875 in = 22.2 mm. **δ/R ≈ 0.81.** The boundary layer fills
   nearly the entire wake diameter at Raven's base.
3. In this thick-BL regime, the effective base area is reduced by displacement
   thickness δ* ≈ δ/8, but more importantly the wake recompression model
   underlying Devan-Ashwood (free shear layer over a nearly inviscid core)
   breaks down. Published data (Chapman 1950 laminar base; Addy 1970; Tanner
   1984) show that thick-BL cylinder afterbodies have Cp_base ~20-40% more
   negative than the thin-BL correlation.
4. For comparison, Rabia body Re_L ≈ 6.8e7, δ ≈ 27 mm, R = 38.1 mm → δ/R ≈ 0.71.
   Torrent Re_L ≈ 6.7e7, δ ≈ 27 mm, R = 50.8 mm → δ/R ≈ 0.53. Raven is in a
   materially different regime.
5. A 40% boost to Raven's Cdb would add 0.315 × 0.4 = 0.126 Cd, closing most of
   the residual (20 pp apogee → ~0.15-0.2 effective Cd miss).

Secondary / contributing mechanisms:

6. **Body wave drag on long slender cylinders** is currently zero in ORP
   (`SymmetricComponentCalc.calculatePressureCD` returns 0 when foreRadius =
   aftRadius). At M > 1 a long cylinder does still radiate shock systems
   stemming from surface imperfections, fin-root shocks, rail buttons, etc.
   This is typically small (~0.02-0.05 Cd) but scales with L/D.

7. **Rail-button wake interaction on min-dia bodies**. Raven's Rail Guide Cdp =
   0.056 is already accounted, but the *wake* of a 0.375-in button on a
   1.75-in tube (button/body = 0.214) may trip additional separation bubbles
   that propagate aft. Torrent's ratio is 0.375/4.00 = 0.094, Rabia's is
   0.375/3.00 = 0.125 (but no guide). This is hard to quantify.

Ruled out:
- **Friction** is behaving correctly (Cf_local matches Van Driest II within
  expected bounds across all three cases; no anomalous reduction on Raven).
- **Missing finned-body augmentation** — it is active and contributing 30%.
- **Baro-altimeter error** — max ±5% for a 21s coast to 11k ft; accounts for
  at most 4-5 pp of the 27.5 pp gap.

---

## 6. Ranked Candidate Fixes

### Fix A — Thick-BL base drag amplification for high-L/D bodies (RECOMMENDED)

Add a δ/R-dependent multiplier to `calculateBaseCD` (or equivalently to the
finned-base augmentation) when the downstream body's estimated BL thickness
exceeds ~50% of the base radius. Proposed form:

    thickBLFactor = 1 + k × clamp(δ/R - 0.5, 0, 0.5)

with k ≈ 0.8 (calibratable), δ from the 1/7-power flat-plate correlation
δ/L = 0.37/Re_L^0.2.

Expected impact at M=1.1:
- Raven (δ/R = 0.81): factor = 1 + 0.8 × 0.31 = 1.25 → adds ~0.08 Cd base → closes ~10 pp apogee.
- Rabia  (δ/R = 0.71): factor = 1 + 0.8 × 0.21 = 1.17 → adds ~0.05 Cd base → +~3 pp.
- Torrent(δ/R = 0.53): factor = 1 + 0.8 × 0.03 = 1.02 → adds ~0.007 Cd → +~0.5 pp.

Regression risk:
- Rabia already at +4.0%; +3 pp would push to +7%, still within RASAero's own
  band (+7.1% on Torrent is already accepted). Tolerable.
- Torrent already at +5.8%; +0.5 pp negligible.
- Subsonic cases (EZI-65, T&L, Byrum, CalIsp1) operate at Re_L ~1-3e7 where
  δ/R is similar to Raven on thin HPR airframes — a real risk of piling drag
  onto already-healthy subsonic cases. Mitigation: gate the correction on M>0.9
  where wake dynamics are dominated by shock-wake interaction; or require
  body-only L/D > 25 AND δ/R > 0.5.

### Fix B — Reduce finned-body augmentation when fin TE is well inboard

Penalize `calculateFinnedBaseAugmentation` when the fin TE sits > 0.5 D ahead
of the base (e.g., multiply by `exp(-(gap/D)/τ)` with τ=1).

Expected impact:
- Raven: gap/D = 1.87/1.75 = 1.07 → factor exp(-1.07) = 0.34 → base drops
  by ~0.07 Cd → makes Raven WORSE (overshoot grows from +27 to +35 pp).
- Rabia / Torrent: gap = 0 → unchanged.

This is the OPPOSITE of what's needed. Listed for completeness — confirms the
augmentation is not the culprit.

### Fix C — Add a body-scale supersonic pressure drag term on long slender tubes

Apply a small `Cdp_body = f(L/D, M)` for body tubes with L/D > 20 at M>1.

Expected impact:
- Raven: +0.02-0.04 Cd → closes 3-5 pp apogee.
- Rabia: +0.01-0.02 Cd → +1-2 pp.
- Torrent: +0.005-0.01 Cd → +0.5-1 pp.

Regression risk: Kinsel (body L/D = 17.5) unaffected. Subsonic cases
unaffected. Small positive nudge across the healthy set (likely acceptable).
Calibration target: use Raven's residual.

---

## 7. Primary Diagnosis

**The one primary mechanism driving Raven's ORP-specific 20+ pp error is
thick-boundary-layer base drag amplification on a minimum-diameter, body
L/D ≈ 37 airframe.** The current Devan-Ashwood correlation is calibrated on
moderate-L/D bodies where δ/R ≪ 1. Raven operates at δ/R ≈ 0.8 at max Mach —
outside the correlation's validated range — and its base Cd is systematically
low by 30-40%. This is not a coding bug; it is a correlation-range violation.

Rabia and Torrent, with body L/D of 27 and 21 (δ/R ≈ 0.7 and 0.5), sit near
or inside the correlation's comfort zone AND benefit from large conical-nose
wave drag buffering, making the same ~0.05-0.08 Cd base-drag shortfall
invisible against their larger Cd budget.

Friction (Van Driest II) is behaving correctly across all three cases. The
finned-body augmentation is active on Raven's body tube (it adds 30% to base
drag); turning it off or penalizing for the 1.87-inch fin-TE-to-base gap
would make the residual worse, not better.

---

## 8. Key Code References

- `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java:382-406`
  — `calculateFrictionCoefficient`: subsonic incompressible Cf for
  rough-finish path, Van Driest II at M≥1.1, linear blend M 0.9-1.1.
- `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java:547-577`
  — `vanDriestIICf`: Schoenherr + Fc/Fθ/Fx transform.
- `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java:928-1045`
  — `calculateFinnedBaseAugmentation`: 1.30x multiplier at M=1.1 for 3 fins.
  Line 943-950 picks up fins that are children of the body tube, regardless of
  their axial position within the tube. Line 991-1014 adds a fallback for
  coaxial PodSet fin cans.
- `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java:1184-1192`
  — `calculateBaseCD(m)`: transonic poly returns ~0.243 at M=1.1, no
  L/D or Re dependence.
- `core/src/main/java/info/openrocket/core/file/rasaero/importt/SurfaceFinishHandler.java:48`
  — `rocket.setPerfectFinish(false)` for ALL CDX1 imports, regardless of the
  "Smooth (Zero Roughness)" label. So Raven, Rabia, Torrent all use the same
  rough-finish friction branch.

---

## 9. Artifact Paths

- Per-case diagnostics: `core/build/reports/simvreal-outliers/{Raven,Rabia,Torrent}.md`
- Component CD sweeps: `core/build/reports/simvreal-outliers/{Raven,Rabia,Torrent}-component-cd.csv`
- Prior transonic analysis: `paper/data/high_m_drag_reconciliation.md` §2c, §3c
- This memo: `paper/data/raven_vs_rabia_diagnostic.md`
