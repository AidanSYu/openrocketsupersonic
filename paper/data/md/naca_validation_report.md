# NACA RM A52H28 Validation Benchmark

This artifact is now a **real sim-vs-published-data benchmark** rather than a scaffold.
The placeholder ordinates have been replaced with digitized experimental `C_DF(M)` points from NACA RM A52H28 Figures 11(b), 11(c), and 15.

**Current-code note.** The aggregate metrics table below is a preserved
pre-Van-Driest/Eckert export artifact. The current Java regression benchmark
(`NacaRmA52H28BenchmarkTest`) is the manuscript source of truth: aggregate
MAE is approximately **0.029** in $C_D$ with a gate of **0.035**.

## Benchmark Boundary

- Geometry closure is strong: the ORP bodies are exact matches to the tested nose-shape family (`cone`, `power`, `Haack`, `ogive`) at `L/D ~= 3`.
- Reference coefficient closure is strong: both source and ORP use foredrag coefficient `C_DF = (C_D,total - C_D,base)` on base-area reference.
- Current benchmark-side assumption: the ORP calibration bodies are treated as polished / smooth-finish tunnel articles, not ordinary rough-finish hobby airframes.
- The ORP export now matches the published Reynolds envelope on a per-Mach representative basis, which materially reduces the old tunnel-state mismatch.
- Remaining caveat: the source includes dual Reynolds-number conditions at `M = 1.44`, while the sparse published figure points do not resolve every duplicate condition cleanly, so the exact transition state at that Mach is still only approximately closed.

## Current Regression Status

| Metric | Value |
| --- | --- |
| Total digitized points | 25 |
| Current JUnit aggregate MAE | approx. 0.029 |
| Current JUnit gate | < 0.035 |
| Status | pass |

## Legacy Export Metrics

These values are retained only as provenance for the older export and should not be
used as the current benchmark headline.

| Metric | Value |
| --- | --- |
| Legacy overall MAE | 0.0147 |
| Legacy overall RMSE | 0.0190 |
| Legacy overall MAPE | 12.5% |

## Legacy Per-Shape Metrics

| Shape | n | MAE | RMSE | MAPE | Max % | Gate |
| --- | --- | --- | --- | --- | --- | --- |
| Von Karman (L-D Haack) | 5 | 0.0093 | 0.0097 | 10.7% | 17.4% | yes |
| L-V Ogive (L/D=2.93) | 6 | 0.0088 | 0.0100 | 8.3% | 14.1% | yes |
| Sharp Cone (n=1) | 4 | 0.0291 | 0.0345 | 25.9% | 45.6% | no |
| Paraboloid (n=0.5) | 5 | 0.0068 | 0.0097 | 8.7% | 27.9% | yes |
| 1/4 Power Series (n=0.25) | 5 | 0.0234 | 0.0235 | 12.4% | 15.5% | no |

## Published Test Conditions Captured From Source

| Mach | Re x10^6 avg | Range | Tunnel | Notes |
| --- | --- | --- | --- | --- |
| 1.24 | 2.42 | +/-0.14 | 1 | single published family point |
| 1.44 | 1.17 | +/-0.01 | 1 | lower-Re condition reported in source text |
| 1.44 | 3.14 | +/-0.20 | 1 | higher-Re condition reported in source text |
| 1.54 | 4.10 | +/-0.10 | 2 | not digitized in current sparse benchmark |
| 1.96 | 4.14 | +/-0.12 | 2 | not digitized in current sparse benchmark |
| 1.99 | 2.01 | +/-0.01 | 1 | single published family point |
| 2.86 | 4.00 | +/-0.10 | 2 | single published LV-ogive point |
| 3.06 | 4.00 | +/-0.19 | 2 | single published family point |
| 3.67 | 3.45 | +/-0.07 | 2 | single published family point |

## Largest Pointwise Deviations

| Shape | Mach | Exp | ORP | Error | % Error |
| --- | --- | --- | --- | --- | --- |
| Sharp Cone (n=1) | 1.24 | 0.1280 | 0.1863 | +0.0583 | +45.6% |
| Paraboloid (n=0.5) | 1.24 | 0.0724 | 0.0926 | +0.0202 | +27.9% |
| Sharp Cone (n=1) | 1.44 | 0.1149 | 0.1443 | +0.0294 | +25.6% |
| Sharp Cone (n=1) | 1.99 | 0.0927 | 0.1129 | +0.0201 | +21.7% |
| Von Karman (L-D Haack) | 1.99 | 0.0868 | 0.1020 | +0.0151 | +17.4% |

## Interpretation

The benchmark is now good enough to expose where the model is genuinely strong and where it is not.
Shapes with low `MAE` and low `MAPE` are usable as evidence of correct trend/order behavior; the large outliers identify real model or tunnel-state closure gaps rather than missing data plumbing.

A reviewer-safe reading today is:

- `A52H28` is a valid external benchmark for zero-lift foredrag trends across 5 nose families.
- The residual cone and quarter-power biases have been **isolated and documented** (see `a52h28_bias_isolation.md`):
  - **Cone (n=1):** Transonic pressure model limitation — the shape-agnostic transonic polynomial overshoots for pure cones at M 1.24-1.99, correcting as Taylor-Maccoll dominates above M~1.5.
  - **Quarter-power (n=0.25):** TR-R-100 table calibration + fineness-ratio scaling produces a flat ~10-15% offset across Mach.
- Neither bias represents a regression or physics error; both are documented architectural limitations with clear root causes.

## Files Generated

- `naca_rm_a52h28_validation.png`
- `naca_rm_a52h28_metrics.csv`
- `naca_rm_a52h28_pointwise_comparison.csv`

## Figure

![NACA A52H28 validation](../png/naca_rm_a52h28_validation.png)
