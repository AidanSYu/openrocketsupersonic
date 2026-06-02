# Corpus Bias-Variance Analysis — 25-flight Validation Corpus

**Source:** `rocket-flight-database/flight_comparison.csv`  
**Generated:** 2026-05-11  
**Mach span:** 0.54–4.33 (peak)  
**N flights:** 25 total — OpenRocket Plus n=25, RASAero II n=25

## TL;DR

Across all 25 flights, OpenRocket Plus apogee error is essentially zero-mean (-0.38%) with σ=5.44% (RMSE=5.34%, MAE=4.74%) and 25/25 flights (100%) within ±10%; the error distribution is light-tailed and mildly right-skewed (skew=+0.43, excess kurtosis=-1.14), and Shapiro–Wilk rejects normality at α=0.05 (p=0.023). On the 25 paired flights, OpenRocket Plus and RASAero II agree to within ±14.3% (Bland-Altman 1.96σ) with no statistically significant difference in |error| (Wilcoxon p=0.615). Whole-corpus bias²/MSE = 0.01 for OpenRocket Plus vs 0.16 for RASAero II, so the residual error is dominated by per-flight variance (build, motor, atmosphere) rather than systematic model bias.

## 1. Per-regime bias and variance

| Regime (Mach) | Predictor | N | Bias (%) | σ (%) | RMSE (%) | MAE (%) | |e|≤5% | |e|≤10% |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Subsonic (M < 0.8) | OpenRocket Plus | 9 | +2.54 | 4.37 | 4.83 | 4.30 | 7 | 9 |
| Subsonic (M < 0.8) | RASAero II | 9 | +3.86 | 6.11 | 6.94 | 6.13 | 4 | 7 |
| Transonic (0.8 ≤ M ≤ 1.3) | OpenRocket Plus | 7 | -3.66 | 5.36 | 6.16 | 5.84 | 2 | 7 |
| Transonic (0.8 ≤ M ≤ 1.3) | RASAero II | 7 | -0.36 | 5.51 | 5.11 | 4.29 | 4 | 7 |
| Low supersonic (1.3 < M ≤ 3.0) | OpenRocket Plus | 5 | +1.83 | 5.70 | 5.42 | 4.61 | 3 | 5 |
| Low supersonic (1.3 < M ≤ 3.0) | RASAero II | 5 | +3.18 | 4.93 | 5.44 | 4.76 | 3 | 5 |
| High supersonic (3.0 < M ≤ 5.0) | OpenRocket Plus | 4 | -3.98 | 2.97 | 4.73 | 3.98 | 2 | 4 |
| High supersonic (3.0 < M ≤ 5.0) | RASAero II | 4 | +3.32 | 7.18 | 7.05 | 6.10 | 2 | 3 |
| Hypersonic (M > 5.0) | OpenRocket Plus | 0 | — | — | — | — | — | — |
| Hypersonic (M > 5.0) | RASAero II | 0 | — | — | — | — | — | — |
| All (full corpus) | OpenRocket Plus | 25 | -0.38 | 5.44 | 5.34 | 4.74 | 14 | 25 |
| All (full corpus) | RASAero II | 25 | +2.46 | 5.81 | 6.20 | 5.34 | 13 | 22 |

![Per-regime breakdown](regime_breakdown.png)

## 2. Bias-variance decomposition (MSE = bias² + variance)

Variance here is the population variance (no Bessel correction) so that MSE = bias² + variance is an exact identity. The Bias²/MSE column quantifies what fraction of squared error is systematic bias rather than scatter.

| Regime | Predictor | N | Bias (%) | Bias² (%²) | Variance (%²) | MSE (%²) | Bias²/MSE |
|---|---|---:|---:|---:|---:|---:|---:|
| Subsonic | OpenRocket Plus | 9 | +2.54 | 6.43 | 16.94 | 23.37 | 0.28 |
| Subsonic | RASAero II | 9 | +3.86 | 14.90 | 33.21 | 48.11 | 0.31 |
| Transonic | OpenRocket Plus | 7 | -3.66 | 13.38 | 24.62 | 37.99 | 0.35 |
| Transonic | RASAero II | 7 | -0.36 | 0.13 | 25.98 | 26.11 | 0.00 |
| Low supersonic | OpenRocket Plus | 5 | +1.83 | 3.33 | 26.02 | 29.35 | 0.11 |
| Low supersonic | RASAero II | 5 | +3.18 | 10.12 | 19.44 | 29.56 | 0.34 |
| High supersonic | OpenRocket Plus | 4 | -3.98 | 15.82 | 6.60 | 22.41 | 0.71 |
| High supersonic | RASAero II | 4 | +3.32 | 11.05 | 38.67 | 49.72 | 0.22 |
| Hypersonic | OpenRocket Plus | 0 | — | — | — | — | — |
| Hypersonic | RASAero II | 0 | — | — | — | — | — |
| All | OpenRocket Plus | 25 | -0.38 | 0.15 | 28.36 | 28.51 | 0.01 |
| All | RASAero II | 25 | +2.46 | 6.04 | 32.46 | 38.50 | 0.16 |

![Bias-variance stacked bars](bias_variance.png)

**Whole-corpus reading.** OpenRocket Plus full-corpus MSE = 28.51 (%²) with bias²/MSE = 0.01; RASAero II (n=25) MSE = 38.50 (%²), bias²/MSE = 0.16. In both predictors variance dominates bias.

## 3. Error distribution analysis

**OpenRocket Plus (n=25).** Shapiro–Wilk: W=0.905, p=0.023; Anderson–Darling: A²=0.921, crit(5%)=0.728 → reject normality; skew=+0.43, excess kurtosis=-1.14. Normality verdict: **reject normality at α=0.05**.

**RASAero II (n=25).** Shapiro–Wilk: W=0.952, p=0.282; Anderson–Darling: A²=0.381, crit(5%)=0.728 → fail to reject normality; skew=-0.17, excess kurtosis=-1.14. Normality verdict: **fail to reject normality at α=0.05**.

![OpenRocket Plus QQ vs normal](qq_normal.png)

![Signed-error histogram + KDE](error_hist.png)

![Side-by-side predictor distributions](predictor_distributions.png)

**Shape commentary.** OpenRocket Plus signed error is right-skewed, platykurtic (flat, light-tailed; few or no outliers) (skew=+0.43, excess kurtosis=-1.14); RASAero II is approximately symmetric, platykurtic (flat, light-tailed; few or no outliers) (skew=-0.17, excess kurtosis=-1.14). The OpenRocket Plus distribution fails Shapiro–Wilk and Anderson–Darling — driven by the platykurtic (flat-topped) shape rather than heavy tails — so confidence intervals computed from a normal assumption will be slightly anti-conservative; the maximum absolute error in the full corpus is 8.7%, well inside ±10% for every flight. There is no evidence of bimodality or a systematic supersonic over-prediction lobe.

## 4. Paired predictor comparison (n=25 flights with both)

- Median Δ(|ORP|−|RAS|) = -0.30 pp
- Mean Δ(|ORP|−|RAS|) = -0.60 pp
- Win counts (predictor closer to truth): OpenRocket Plus **13**, RASAero II **12**, ties **0**
- Wilcoxon signed-rank on |error|: W=143.00, p=0.6150
- Paired t-test on |error|: t=-0.73, p=0.4742

![Paired comparison plots](predictor_paired.png)

Bland-Altman shows the two predictors agree to within ±14.3% (95% limits of agreement) with a mean offset of -2.84%. There is no detectable Mach-dependent bias in their disagreement (color-coded scatter).

## 5. Mach-dependent residual plot

![Signed error vs Mach](error_vs_mach.png)

The shaded bands mark ±5% and ±10% error envelopes; the dotted vertical lines are regime boundaries. Quadratic trend fits are shown for guidance only — the corpus is too small for a formal LOESS bandwidth.

## Pull quotes (for the JSR results section)

> The combined 25-flight corpus mean OpenRocket Plus apogee error is -0.38% with σ=5.44% (RMSE=5.34%); 25/25 flights agree with flight measurement to within ±10%.

> The subsonic regime (M < 0.8, n=9) shows mean bias +2.54% (bias² = 6.43 %²) and is dominated by per-flight variance (16.94 %²), indicating that the subsonic Barrowman + Van Driest II baseline carries minimal systematic offset.

> In the high-supersonic regime (3.0 < M ≤ 5.0, n=4) OpenRocket Plus shows mean bias -3.98% with sample σ=2.97%. The headline corpus tops out at Mach 4.33 (MESOS 293K, the two-stage closure). High-Mach sounding-rocket flights are reported separately as an exploratory set (`sounding_rockets_exploratory.csv`) where motor/geometry reconstruction uncertainty drives large systematic errors; they are excluded from this validated headline corpus.

## Honest limitations

- **Corpus composition.** 24 of 25 flights are amateur HPR launches benchmarked in Rogers' RASAero II comparison set; MESOS 293K is the one two-stage closure (the highest-Mach flight at M 4.33). Most flights cluster at M < 1.3 (the regime where OpenRocket has historically been calibrated), so corpus-mean metrics are dominated by subsonic behavior. High-Mach sounding rockets are deliberately excluded from the headline (see *Scope*) and reported as exploratory only.
- **In-sample base-drag constants.** Two B-level scale constants (THICK_BL_K, SLENDER_BODY_K) are corpus-frozen on Raven/Rabia/Kinsel, which remain in the corpus, so the headline is partly in-sample. Generalization is shown by the prospective holdout: the 15 untuned holdout flights (mean |err| 4.20%) are MORE accurate than the 10 development-lock flights (5.56%).
- **Post-flight-tuned RASAero references.** Per Rogers' source notes, the RASAero II apogees for MESOS 293K and AeroPac 104K are post-flight simulations (ignition delay / launch angle adjusted to match flight), so the head-to-head on those two flights is not a blind forward comparison and is flagged as such.
- **Sparse high-Mach data.** No flight exceeds M=5 in the headline corpus (MESOS at M 4.33 is the maximum); only a handful fall in 3 < M ≤ 5. Per-regime statistics above M=3 are descriptive, not inferential — no claim of statistical significance is made for the supersonic bias estimates and a single outlier could move the regime mean by several percentage points.
- **Heterogeneous truth sources.** Apogee ground truth ranges from barometric altimeter (≈1% noise) through GPS, optical track, integrated accelerometer, and radar/radar-beacon track. Truth uncertainty is not subtracted from the reported error — published radar precision for the Nike-Deacon flights is ±1–5 kft (≈0.3–1.4%).
- **Per-flight independence.** Three pairs of repeated builds (Caliber Isp 04 teams 1/2/3, Caliber Isp 05 Discovery/Columbia, Full Metal Jacket BALLS 005 / Black Rock 6) share geometry and motor; treating their errors as fully independent slightly under-states the σ on the regime means.
- **Distribution tests have low power at n=25–28.** Failing to reject normality does not establish normality; it only means we lack evidence against it at this sample size.

## Artifacts

- `regime_breakdown.csv` / `regime_breakdown.png`
- `bias_variance_decomp.csv` / `bias_variance.png`
- `qq_normal.png`, `error_hist.png`, `predictor_distributions.png`
- `paired_comparison.csv`, `predictor_paired.png`
- `error_vs_mach.png`
- `normality_tests.csv`
- `paired_test_summary.csv`
- `corpus_bias_variance_summary.md` (this file)

Run `python analyze.py` from this directory to regenerate all artifacts from the canonical `flight_comparison.csv`.
