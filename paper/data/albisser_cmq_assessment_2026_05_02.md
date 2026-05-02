# Albisser (2015) PhD Thesis — Cmq Second-Source Assessment — 2026-05-02

Source: `paper/data/pdf/DDOC_T_2015_0083_ALBISSER.pdf`
**Title:** Identification of aerodynamic coefficients from free flight data
**Author:** Marie Albisser
**Defended:** 10 July 2015, Université de Lorraine (Doctorat en Automatique, Traitement du Signal et des Images, Génie Informatique)
**Lab:** CRAN UMR 7039 (Nancy) / ISL (French-German Research Institute of Saint-Louis)
**Pages inspected directly:** front matter, table of contents, Chapter 1 (Aerodynamic testing), Section 4.2 (Projectile results) including Table 4.3, Figures 4.30-4.34, and Conclusions.

## Verdict: **REJECT as a clean independent second source for Basic Finner Cmq.** Promotable to "supporting / cross-validation" only.

The thesis IS a high-quality free-flight aerodynamic ID effort with Cmq results presented in directly-digitisable form. But the **geometry tested is the Basic Finner**, the **same reference projectile family** that already grounds our existing A-level Cmq source (ADA636861). Using Albisser as the "second independent dataset" therefore does **not** broaden geometry coverage and risks calibration-circularity in the AST audit response.

---

## What Albisser actually contains

### Geometry (Section 1.1.2, Figure 1.2, Table 1.3)

- **Vehicle:** "fin stabilized reference projectile called Basic Finner."
- **Dimensions (verbatim from p.12):** d = 28 mm caliber, L/d = 10.0, 20° nose cone of 1 caliber length, four rectangular fins 1×1 caliber, **fin cant δ = 0° or 2°**, fin section conical with 0.08 caliber thickness at base.
- **Two CG positions:** Xcg1 ≈ 60% from nose, Xcg2 ≈ 65% from nose.
- **Reference cited for the geometry:** "Dupuis and Hathaway (1997), Dupuis (2002)" — same Basic Finner lineage that flows through ADA636861.

### Test conditions (Section 1.5.2, Table 1.5)

- Free flight at the **ISL Baldersheim open range**, 91 mm smooth-bore powder gun, 235 m fireline.
- **Initial Mach:** M0 = 1.3, 1.8, 2.6.
- **Initial AoA:** α0 = 0° or 4°.
- **Instrumentation:** 3D magnetometer + 2D/3D accelerometer on-board, telemetry off-board, plus 10.52 GHz Doppler radar and high-speed video trajectory tracker.
- **16 instrumented projectile flights** were attempted; due to electronic failures only a **subset** (4 selected models A2, B2, C2, D2) yielded data of usable quality. The estimation table (Table 4.3) reports five averaged-Mach Cmq points per CG position.

### Cmq data presentation

- **Table 4.3 (p.75)** — direct numerical values, immediately digitisable:

  | Avg M | Cmq (Xcg1) | Cmq (Xcg2) |
  |---|---|---|
  | 1.25 / 1.27 | -432.31 | -316.25 |
  | 1.77 / 1.76 | -417.87 | -303.36 |
  | 1.79 / 1.80 | -382.20 | -211.32 |
  | 2.59 / 2.47 | -368.69 | -239.20 |
  | 2.61 / 2.54 | -382.67 | -313.30 |

- **Figure 4.34 (p.79)** — Cmq vs Mach plot showing FF single fit (open red circles), FF multiple fit (filled red circles), PRODAS dashed lines for both CGs, plus the thesis estimation (yellow Xcg1, blue Xcg2). Mach axis 1.0-3.0.

### Methodology

- Inverse 6-DOF code "Inv6DoF" identifies aerodynamic parameters from free-flight magnetometer + radar data using grey-box parametric estimation, multiple-fit strategy, identifiability analysis, and a state-variable / spline aerodynamic-coefficient model.
- This is an *independent* free-flight ID method (system-ID style trajectory fit), genuinely different from any single ballistics-range data-reduction approach.

---

## Why this fails the "independent second source" bar for AST

1. **Same geometry family.** ADA636861 (our existing A-level source) and Albisser both test the Basic Finner reference projectile, with the same canonical dimensions originating in Dupuis & Hathaway 1997 / Dupuis 2002. A reviewer asking for a second *independent* Cmq source is asking for a *different geometry class* (e.g. an ogive-cylinder-flare, an Army-Navy Basic Finner derivative with different fin planform, a slender cone, or a sounding-rocket-like configuration). Adding a Basic Finner replicate does not test our calculator's generalisation.
2. **Comparator dataset overlaps.** Figure 4.34 explicitly compares the Albisser estimates against "FF single fit / FF multiple fit" data from Dupuis (2002) — the same lineage feeding ADA636861. So Albisser is not even fully independent of the existing reference set as a *measurement*; it is partly a re-analysis with a new ID procedure.
3. **Mach coverage almost identical.** Albisser covers M ≈ 1.25-2.6 — well inside our existing M 1.08-4.30 ADA636861 envelope, no new high-M or transonic ground gained.
4. **Scatter is large, by author admission.** From Section 4.2.3.5: "this is the most difficult coefficient to estimate due to its poor sensitivity to outputs. Despite the higher scattering, the general trend can be observed." Spread of 100-200 units between Albisser estimation and PRODAS at given Mach is visible in Figure 4.34.

## Where Albisser IS useful

- **Methodological cross-check:** it confirms our Basic Finner Cmq via a *different ID method* (inverse 6DOF + magnetometer telemetry, vs the ballistic-range reduction in ADA636861). Cite in an "independent ID method confirms our Cmq calibration" sentence — a B-level cross-validation, not an A-level second source.
- **Two-CG sensitivity test.** Albisser provides Cmq for *both* Xcg = 60% and 65%; we can test whether our model reproduces the Cmq sensitivity to CG migration that Albisser reports (Cmq becomes less negative as CG moves aft, e.g. -432 → -316 at M~1.25). This is a non-trivial geometric sensitivity check.
- **Space-probe data (Chapter 4.1) is genuinely different geometry** — 70°/47° blunt-cone Earth-re-entry-style probe at M0 = 2.0-3.0. **This is not a fin-stabilized rocket geometry**, so it is *not* applicable to our amateur-rocket Cmq pipeline. Reject for our scope.

## Recommended action

1. Keep Albisser in the repo as a **B-level methodological cross-validation** for Basic Finner Cmq; do **not** count it toward closing the "second independent Cmq source" gap.
2. Digitisable points (5 per CG, 10 total) can still be added to a supplementary cross-check table — minimal effort, defensive to have.
3. The actual second-source gap remains open. See `needs_list_2026_05_02.md`.
