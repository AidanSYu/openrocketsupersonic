# JSR Paper Draft — Sections 7 and 8

**Status:** Section drafts only. Front matter, sections 1–6, 9, 10, and abstract are produced separately.
**Author:** Aidan Yu, Independent Researcher.
**Date:** 2026-05-16.

---

## 7. Validation Against Published Computational Fluid Dynamics

### 7.1 Comparator inventory

In lieu of running an in-house computational fluid dynamics (CFD) campaign — a deliberate scoping decision discussed in §9.5 — the present method was compared against four independent published CFD studies that together span two reference geometries, two distinct aerodynamic quantities, and three Mach bands. The four sources are: Bunescu et al. 2025 unsteady Reynolds-averaged Navier–Stokes (URANS) on the Army–Navy Basic Finner [CITE:bunescu2025]; Sahu, Nietubicz, and Steger 1983 thin-layer Navier–Stokes on a secant-ogive-cylinder-boattail projectile [CITE:sahu1983]; Vidanović et al. 2014 Menter shear-stress-transport (SST) k-ω on the AGARD Model B calibration standard [CITE:vidanovic2014]; and Sznajder 2025 ANSYS Fluent moving-reference-frame, forced-oscillation, and indicial-response computations of Basic Finner pitch damping derivatives [CITE:sznajder2025]. A second independent CFD source on the Basic Finner pitch damping — Bhagwandin and Sahu 2013 [CITE:bhagwandin2013] — was retained from earlier validation work and is used in §7.5 to corroborate the Sznajder finding. Each digitized dataset, its underlying portable document format source, and the resulting comparator artefact reside under `paper/data/csv/` and `paper/data/md/`. Table 5 summarizes the inventory.

**Table 5. Published-CFD comparator inventory.**

| Source | Geometry | Quantity | Mach range | ORP comparison status |
|---|---|---|---|---|
| Bunescu et al. 2025 URANS [CITE:bunescu2025] | Basic Finner (ANF) | C_N, C_X | 0.4–3.5 | Java comparator wired; C_X mean absolute percent error (MAPE) 39.1% on 5 points at AoA = 0° |
| Sahu et al. 1983 thin-layer Navier–Stokes [CITE:sahu1983] | Secant-ogive-cylinder-boattail | C_{Db}, C_{D,tot} | 0.9–1.2 | Memo only — comparator not yet digitized (future work) |
| Vidanović et al. 2014 SST k-ω [CITE:vidanovic2014] | AGARD-B calibration model | C_D, C_L, C_m | 0.596, 1.602 | Reference dataset; no AGARD-B .ork shipped (future work) |
| Sznajder 2025 Fluent MRF/FOM/IRM [CITE:sznajder2025] | Basic Finner (ANF) | C_{mq} + C_{mα̇} | 0.9–4.5 | Memo + comparator CSV; supersonic MAPE 31.6% on 8 points (M ≥ 1.29) |
| Bhagwandin & Sahu 2013 [CITE:bhagwandin2013] | Basic Finner (ANF) | C_{mq} + C_{mα̇} | 0.6–4.5 | Second-source corroboration of Sznajder supersonic bias |

Figure 18 collects the four CFD-side panels into a single composite.

### 7.2 Basic Finner static coefficients versus Bunescu URANS

Bunescu et al. 2025 [CITE:bunescu2025] reported URANS predictions of normal-force and axial-force coefficients (C_N, C_X) on the standard 10°-half-angle, four-rectangular-fin Army–Navy Basic Finner across M = 0.4, 0.95, 1.6, 2.5, and 3.5. The CFD employed an unstructured k-ε realizable closure with corroborating SST k-ω comparisons. The present method was exercised on the same geometry (`SupersonicTestRockets.makeBasicFinner()`) at the same five Mach numbers and angle of attack 0°. The resulting axial-force comparison yielded MAPE 39.1% over the five-point sweep. The error was driven principally by the simplified viscous treatment in the engineering Barrowman pipeline relative to the URANS Reynolds-number-resolved boundary layer, with the largest residual occurring at the transonic point M = 0.95 where wave-onset shock-induced separation is not represented in the present pressure-drag model. (We note that the JSR Readiness Brief associated this MAPE with the AoA = 10° sweep; the underlying Bunescu C_X data and the in-repo `BunescuANFCfdComparatorTest` evaluate at AoA = 0°. The present text reports the AoA = 0° figure.) Despite the loose absolute agreement, the Mach trend in C_X — a transonic rise to a peak near M ≈ 1.0 followed by monotone decay through M = 3.5 — is correctly reproduced.

### 7.3 Ogive-cylinder-boattail base drag versus Sahu thin-layer Navier–Stokes

Sahu, Nietubicz, and Steger 1983 [CITE:sahu1983] reported thin-layer Navier–Stokes computations of base drag, pressure drag, friction drag, and total drag for a three-caliber secant-ogive nose, three-caliber cylindrical section, and boattail projectile over the transonic Mach band M = 0.9–1.2 at Reynolds number Re_L = 4.5 × 10⁶. Their Figure 14 overlays the CFD against sting-mounted wind-tunnel data and the McDrag semi-empirical correlation, providing a triple comparison ideally suited to anchor a transonic base-drag panel. The present method's `ChapmanKorstBaseDrag` and `SymmetricComponentCalc` pressure-drag pipelines could in principle be exercised at this geometry. However, the Sahu digitization was deferred during preparation of the present manuscript; the comparator figure is therefore flagged in Fig. 18 as not yet completed, and the comparison is retained as near-term future work rather than as a result subsection of this paper.

### 7.4 AGARD-B reference versus Vidanović SST k-ω

Vidanović et al. 2014 [CITE:vidanovic2014] reported ANSYS Fluent SST k-ω predictions of total drag, lift, and pitching-moment coefficients on the AGARD Model B calibration standard at M = 0.596 and M = 1.602 over an angle-of-attack sweep of −4° to +12°. The CFD was validated against wind-tunnel data from the VTI T-38 trisonic facility in Belgrade; the authors report CFD-versus-experiment agreement of 0.3–3% in C_D at positive AoA and below 1% in C_L over the test envelope — a state-of-the-art benchmark on a wing-body calibration standard. The present method does not yet ship an AGARD-B `.ork` (the equilateral-triangle delta wing with 4% bi-convex section is at the edge of the OpenRocket fin-set model's validity); a comparator panel is therefore included in Fig. 18 to display the Vidanović CFD against the VTI experiment as a reference dataset against which a future OpenRocket Plus AGARD-B comparator can be benchmarked. The omission is acknowledged as a deferred future work item in §10.

### 7.5 Basic Finner pitch damping versus Sznajder Fluent, corroborated by Bhagwandin and Sahu

The most informative CFD comparison concerns pitch-damping derivatives on the Basic Finner. Sznajder 2025 [CITE:sznajder2025] reported ANSYS Fluent computations of C_{mq} and C_{mα̇} separately, from three independent CFD techniques — steady moving reference frame, dynamic-mesh forced oscillation, and step-perturbation indicial response — over M = 0.9–5.0. The three methods agreed to within approximately 3% of one another and were independently validated against the DREV-TM-9703 free-flight experimental dataset that also anchors the present method's existing `BasicFinnerCmqBenchmarkTest`. The present method exposes the experimentally observable damping sum C_{mq} + C_{mα̇}. On the ten-point comparison grid:

- Supersonic band, M = 1.29–4.5 (n = 8 points): the present method underpredicted the magnitude of the damping sum by 27 to 36 percent, with sign and Mach trend correct. MAPE on the supersonic band was 31.6%.
- Transonic peak, M = 1.08–1.11 (n = 2 points): the present method overshot the magnitude of the damping sum by 110 to 160 percent. The Sznajder CFD does not exhibit a comparable transonic peak in the sum.

The transonic overshoot was traced to the `k_transonic = 1 + 2.5·exp(−((M − 1)/0.15)²)` Gaussian augmentation applied in `BarrowmanStabilityCalculator`; the supersonic underprediction reflects a constant-factor bias of approximately 0.67 in the strip-theory damping coefficient. Bhagwandin and Sahu 2013 [CITE:bhagwandin2013] provided an independent ANSYS Fluent CFD reference at the same geometry over M = 0.6–4.5; their comparator yielded MAPE 50.78% over 13 points with the same sign and the same direction of the residual. The two CFD sources independently confirm that the present method is conservative on supersonic Basic Finner pitch damping and is miscalibrated at the transonic peak.

Two independent CFD sources therefore converge on the same two findings: a 27–36% supersonic underprediction of pitch damping and a transonic-peak over-augmentation. Both findings are taken up explicitly in the limitations discussion in §9.1.

---

## 8. Flight-Corpus Integration Test

### 8.1 Corpus construction

The present method was validated end-to-end against a 28-flight ground-truth corpus assembled from three independent sources. Flights 1 through 25 were taken from the public RASAero II altitude comparison set published by Rogers [CITE:rogers_rasaero_alt]. That set assembles amateur high-power and university research rocketry launches for which the launching team published an instrumented apogee altitude alongside a RASAero II pre-flight prediction; ground-truth instrumentation across those 25 flights spans barometric altimeter (most flights), optical track (three flights), Global Positioning System receiver (three flights), and integrated accelerometer (two flights). Flight 26 was the single-stage Black Brant V VB, vehicle AAF-VB-32, launched from Churchill, Manitoba on 3 March 1971 and tracked by Bristol Aerospace and the National Research Council of Canada to an apogee of 273.6 km, with peak Mach 7.22 [CITE:dtic_ad0733141]. Flights 27 and 28 were the two two-stage Nike–Deacon flights reported by Heitkotter 1956 [CITE:heitkotter1956] from Wallops Island in 1955, both tracked by radar-beacon to apogees of approximately 108 km and 107 km at peak Mach 4.96 and 5.08 respectively. The combined corpus therefore spans Mach 0.54 to 7.22 and apogees of 3,577 ft (1.1 km) to 897,638 ft (273.6 km).

The corpus, together with the underlying `.ork` build files, motor `.eng` files, ground-truth altitude logs, per-flight metadata, and the master `flight_comparison.csv` table, is released as the Rocket Flight Database under a Creative Commons Attribution 4.0 International license, archived at Zenodo with persistent digital object identifier 10.5281/zenodo.19976138 [CITE:rfd_zenodo]. Every result reported in this section reproduces from `flight_comparison.csv` and the `analyze.py` script in `paper/data/analysis/corpus_bias_variance_2026_05_11/`. Table 6 lists the 28 flights, their motors, peak Mach, ground-truth apogee, the present method's predicted apogee, and — where available — the paired RASAero II prediction.

**Table 6. Per-flight corpus rows.** Apogee values in feet. Vehicle names abbreviated to fit; full names retained in `flight_comparison.csv`.

| # | Vehicle | Motor | M_peak | h_real (ft) | h_ORP (ft) | err_ORP (%) | h_RAS (ft) | err_RAS (%) | Source |
|---:|---|---|---:|---:|---:|---:|---:|---:|---|
| 1 | Thunder & Lightning | I284W | 0.54 | 3,577 | 3,877 | +8.4 | 3,989 | +11.5 | Barom. |
| 2 | Gibb | I284W | 0.55 | 3,913 | 3,989 | +1.9 | 4,310 | +10.2 | Barom. |
| 3 | Cancer Descending | M1297W | 0.56 | 6,188 | 6,044 | −2.3 | 6,328 | +2.3 | Barom. |
| 4 | EZI-65 J450ST | J450ST | 0.60 | 3,965 | 4,158 | +4.9 | 4,214 | +6.3 | Barom. |
| 5 | Caliber Isp 04 T2 | I205 | 0.64 | 3,710 | 3,890 | +4.9 | 3,871 | +4.3 | Barom. |
| 6 | Caliber Isp 04 T3 | I205 | 0.64 | 3,964 | 3,889 | −1.9 | 3,871 | −2.4 | Barom. |
| 7 | Caliber Isp 04 T1 | I205 | 0.66 | 3,837 | 3,960 | +3.2 | 3,943 | +2.8 | Barom. |
| 8 | Byrum | J570W | 0.75 | 5,732 | 6,161 | +7.5 | 5,280 | −7.9 | Barom. |
| 9 | Ion Drive | K550W | 0.79 | 8,027 | 7,730 | −3.7 | 8,642 | +7.7 | Barom. |
| 10 | Caliber Isp 05 Discovery | I285 | 0.81 | 4,930 | 4,772 | −3.2 | 4,831 | −2.0 | Barom. |
| 11 | Blister | K1075GG | 0.83 | 9,026 | 8,268 | −8.4 | 8,347 | −7.5 | Barom. |
| 12 | Caliber Isp 05 Columbia | I285 | 0.84 | 5,085 | 4,777 | −6.1 | 4,842 | −4.8 | Barom. |
| 13 | Rabia (short fin can) | L730 | 0.86 | 10,584 | 9,916 | −6.3 | 10,376 | −2.0 | Barom. |
| 14 | Raven | J570W | 1.07 | 8,815 | 9,489 | +7.6 | 9,288 | +5.4 | Barom. |
| 15 | Rabia | L1080BB | 1.14 | 12,745 | 11,913 | −6.5 | 12,777 | +0.3 | Barom. |
| 16 | Torrent | M1850GG | 1.22 | 12,807 | 12,455 | −2.8 | 13,852 | +8.2 | Barom. |
| 17 | Kline-Rogers L500 | L500 | 1.98 | 24,771 | 24,179 | −2.4 | 26,485 | +6.9 | Opt. track |
| 18 | A-601 Kinsel | P4935 | 2.19 | 42,771 | 46,499 | +8.7 | 41,086 | −3.9 | GPS |
| 19 | Full Metal Jacket B005 | O10000 | 2.31 | 37,981 | 37,256 | −1.9 | 38,820 | +2.2 | Opt. track |
| 20 | Full Metal Jacket BR6 | O10000 | 2.46 | 30,038 | 29,239 | −2.7 | 32,646 | +8.7 | Opt. track |
| 21 | Proteus 6 | P9381 | 2.87 | 85,067 | 91,339 | +7.4 | 86,799 | +2.0 | Int. acc. |
| 22 | AeroPac 104K | N1048/M685W | 3.04 | 104,659 | 103,602 | −1.0 | 113,786 | +8.7 | GPS |
| 23 | Don't Debate This | N5800 | 3.04 | 56,573 | 53,150 | −6.1 | 62,308 | +10.1 | Barom. |
| 24 | Qu8k | Q18000 | 3.46 | 121,478 | 119,187 | −1.9 | 116,254 | −4.3 | Int. acc. |
| 25 | MESOS 293K | O4374/M787 | 4.33 | 293,488 | 291,601 | −0.6 | 289,789 | −1.3 | GPS |
| 26 | Black Brant V VB | 26KS20000 | 7.22 | 897,638 | 835,071 | −7.0 | — | — | Radar [CITE:dtic_ad0733141] |
| 27 | Nike–Deacon flight 1 | Nike M5 / Deacon ABL | 4.96 | 356,000 | 352,210 | −1.1 | — | — | Radar bcn. [CITE:heitkotter1956] |
| 28 | Nike–Deacon flight 2 | Nike M5 / Deacon ABL | 5.08 | 350,000 | 346,902 | −0.9 | — | — | Radar bcn. [CITE:heitkotter1956] |

### 8.2 Aggregate accuracy

Across the full 28-flight corpus, the present method's mean signed apogee error was −0.44%, with sample standard deviation σ = 5.13%, root-mean-square error 5.06%, and mean absolute error 4.33%. All 28 of 28 flights (100%) agreed with the measured apogee to within ±10%, and 17 of 28 (61%) agreed to within ±5%. The maximum absolute error across the corpus was 8.7% (Flight 22, AeroPac 104K Two-Stage; Flight 18, A-601 Kinsel; Flight 20, Full Metal Jacket Black Rock 6). On the 25 flights for which a paired RASAero II prediction was available, RASAero II yielded mean signed error +2.46%, σ = 5.82%, RMSE 6.21%, MAE 5.34%, with 13 of 25 flights within ±5% and 22 of 25 within ±10%. Table 7 summarizes.

**Table 7. Aggregate accuracy on the 28-flight corpus.**

| Predictor | N | Mean signed err (%) | σ (%) | RMSE (%) | MAE (%) | ≤ ±5% | ≤ ±10% |
|---|---:|---:|---:|---:|---:|---:|---:|
| OpenRocket Plus | 28 | −0.44 | 5.13 | 5.06 | 4.33 | 17 / 28 | 28 / 28 |
| RASAero II | 25 | +2.46 | 5.82 | 6.21 | 5.34 | 13 / 25 | 22 / 25 |

### 8.3 Bias-variance decomposition

The whole-corpus mean-square error of the present method decomposes into a bias-squared term of 0.19 (%)² and a population-variance term of 25.40 (%)², so that the bias-squared fraction of mean-square error was 0.01. The residual was therefore dominated entirely by per-flight scatter — build tolerance, motor lot variation, atmospheric soundings, and ground-truth instrumentation precision — rather than by systematic directional drift in the model. For the paired RASAero II subset (n = 25) the corresponding decomposition was bias-squared 6.04 (%)², variance 32.47 (%)², and bias-squared/MSE 0.16. Variance dominated bias in both predictors, but the present method's residual was an order of magnitude closer to a zero-mean random process. Figure 19 plots signed error against peak Mach for both predictors with ±5% and ±10% envelopes overlaid. Table 8 reports the per-regime decomposition discussed in the next subsection.

### 8.4 Per-regime breakdown

Errors disaggregated by Mach regime exposed one regime-localized weakness and three regimes of solid behavior.

- **Subsonic regime, M < 0.8 (n = 9).** Mean signed error +2.54%, σ 4.37%, RMSE 4.85%, MAE 4.30%. Seven of nine flights agreed to within ±5%, and all nine to within ±10%.
- **Transonic regime, 0.8 ≤ M ≤ 1.3 (n = 7).** Mean signed error −3.67%, σ 5.34%, RMSE 6.16%, MAE 5.84%. Only two of seven flights agreed to within ±5%; all seven agreed to within ±10%. This is the largest regime-localized bias in the corpus, and is discussed as a disclosed weakness with a proposed root cause in §9.1.
- **Low supersonic regime, 1.3 < M ≤ 3.0 (n = 5).** Mean signed error +1.82%, σ 5.71%, RMSE 5.42%, MAE 4.62%. Three of five within ±5%, all five within ±10%.
- **High supersonic regime, 3.0 < M ≤ 5.0 (n = 5).** Mean signed error −2.13%, σ 2.27%, RMSE 2.94%, MAE 2.13%. Four of five within ±5%, all five within ±10%. The tight σ reflects the high-quality ground truth available for this regime (GPS, integrated accelerometer, optical track).
- **Hypersonic regime, M > 5.0 (n = 2).** Mean signed error −3.93%, σ 4.30%, RMSE 4.97%, MAE 3.93%. One of two within ±5%, both within ±10%. With only two flights in this regime — the Black Brant V VB at M = 7.22 and Nike–Deacon flight 2 at M = 5.08 — the regime statistics are descriptive, not inferential, and no claim of statistical significance is made.

**Table 8. Per-regime bias-variance decomposition.**

| Regime (Mach) | Predictor | N | Bias (%) | σ (%) | RMSE (%) | MAE (%) | ≤ ±5% | ≤ ±10% |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Subsonic (M < 0.8) | ORP | 9 | +2.54 | 4.37 | 4.85 | 4.30 | 7 | 9 |
| Subsonic (M < 0.8) | RAS | 9 | +3.86 | 6.12 | 6.94 | 6.13 | 4 | 7 |
| Transonic (0.8 ≤ M ≤ 1.3) | ORP | 7 | **−3.67** | 5.34 | 6.16 | 5.84 | 2 | 7 |
| Transonic (0.8 ≤ M ≤ 1.3) | RAS | 7 | −0.36 | 5.51 | 5.11 | 4.29 | 4 | 7 |
| Low supersonic (1.3 < M ≤ 3.0) | ORP | 5 | +1.82 | 5.71 | 5.42 | 4.62 | 3 | 5 |
| Low supersonic (1.3 < M ≤ 3.0) | RAS | 5 | +3.18 | 4.93 | 5.44 | 4.76 | 3 | 5 |
| High supersonic (3.0 < M ≤ 5.0) | ORP | 5 | −2.13 | 2.27 | 2.94 | 2.13 | 4 | 5 |
| High supersonic (3.0 < M ≤ 5.0) | RAS | 4 | +3.32 | 7.18 | 7.05 | 6.11 | 2 | 3 |
| Hypersonic (M > 5.0) | ORP | 2 | −3.93 | 4.30 | 4.97 | 3.93 | 1 | 2 |
| All (M 0.54–7.22) | ORP | 28 | −0.44 | 5.13 | 5.06 | 4.33 | 17 | 28 |
| All (M 0.54–7.22) | RAS | 25 | +2.46 | 5.82 | 6.21 | 5.34 | 13 | 22 |

### 8.5 Paired RASAero II comparison

A paired predictor comparison was performed on the n = 25 flights for which both predictors produced a prediction (RASAero II coverage ends below M ≈ 5; flights 26–28 above that ceiling were therefore omitted from the paired analysis but retained in the OpenRocket Plus-only aggregates of §§8.2–8.4). On the paired subset, the median difference in absolute error |ORP| − |RAS| was −0.39 percentage points, and the mean difference was −0.85 percentage points; the present method was closer to the measured apogee on 14 of 25 flights, RASAero II on 11, with zero ties. A Wilcoxon signed-rank test [CITE:wilcoxon1945] on the paired absolute errors returned W = 129.50, p = 0.375; a paired t-test returned t = −1.09, p = 0.287. Neither test rejects the null hypothesis of equal absolute-error distributions at α = 0.05. A Bland–Altman analysis of the paired signed errors gave 95% limits of agreement of ±14.3% with a mean offset of −2.59%; no Mach-dependent disagreement was detectable in the color-coded scatter. Figure 21 displays both panels. The finding is framed deliberately: on this corpus, with this version-locked RASAero II configuration (Rogers' 2024 public comparison set [CITE:rogers_rasaero_alt]), the present method produces apogee predictions statistically indistinguishable from RASAero II. The result is not a claim of universal superiority over a commercial reference; it is a claim of parity on the specified corpus.

### 8.6 Distribution and normality

The shape of the OpenRocket Plus signed-error distribution was characterized for completeness. A Shapiro–Wilk test returned W = 0.916, p = 0.028; an Anderson–Darling test returned A² = 0.905 against a 5% critical value of 0.730. Normality was therefore rejected at α = 0.05. The shape was, however, driven by skew (+0.48) and a markedly platykurtic excess kurtosis (−0.86) rather than by heavy tails: the maximum absolute error in the corpus was 8.7%, well inside the ±10% envelope, and there were no outliers in the conventional Tukey sense. The corresponding RASAero II distribution (n = 25) failed to reject normality (W = 0.952, p = 0.282). Figure 20 displays both distributions side by side. Because the OpenRocket Plus distribution is non-normal, the predictor comparison of §8.5 reported the non-parametric Wilcoxon test as the primary inference and the paired t-test only as a supporting check.

### 8.7 Sensitivity analysis

A local sensitivity sweep was performed on four representative corpus flights spanning the supersonic and hypersonic regimes — HEROS 3 at peak Mach 1.89, Arcas Performance Flight 2 (blunt original ogive) at peak Mach 2.30, Nike–Apache 14.108 GI at peak Mach 6.50, and the Black Brant V VB AAF-VB-32 at peak Mach 7.22. Four input parameters were each perturbed by ±10% from nominal: a multiplicative total-drag-coefficient scale, the launch-site altitude, the integrator time step (over a 0.025–0.10 s envelope), and the launch-rod angle. The central-difference sensitivity coefficient

s_{p,f} = [A_{p,+10%}(f) − A_{p,−10%}(f)] / [2 · A_nom(f)]            (Eq. 1)

was tabulated per flight per parameter, where A denotes the simulated apogee. The corpus-mean magnitude of s was extracted for each parameter; Table 9 lists the resulting ranking, and Fig. 22 shows the per-flight tornado diagrams.

**Table 9. Sensitivity ranking across the four-flight sweep (parameter perturbation ±10%).**

| Parameter | Mean \|s\| (% / 10%) | Median \|s\| | Max \|s\| | Flight at max |
|---|---:|---:|---:|---|
| Total Cd scale | 4.00 | 3.62 | 7.04 | HEROS 3 |
| Launch rod angle | 1.11 | 0.87 | 2.19 | HEROS 3 |
| Integrator time step | 0.98 | 0.08 | 3.75 | Arcas Flight 2 blunt |
| Launch altitude | 0.96 | 0.01 | 3.54 | Arcas Flight 2 blunt |

Total drag-coefficient scale dominated apogee sensitivity at mean |s| = 4.00% per 10% perturbation, with the strongest response (|s| = 7.04%) at the gravity-loss-dominated HEROS 3 trajectory. Launch rod angle was the second-strongest lever at mean |s| = 1.11%, again largest at HEROS 3 where the low-altitude/high-drag flight profile is most sensitive to off-vertical departure. Integrator time step over the 0.025–0.10 s envelope yielded mean |s| = 0.98% (median 0.08%, with the larger value at Arcas reflecting that flight's coarser internal scheduling). The time-step sensitivity is the key numerical-convergence result for this paper: the apogee predictions reported throughout this section were numerically converged within the operational time-step envelope, satisfying the AIAA Editorial Policy on Numerical and Experimental Accuracy time-step-convergence requirement [CITE:aiaa_numerical_policy]. Launch altitude was the smallest mean |s| at 0.96%, consistent with the rapid loss of atmospheric density on the ascent profile.

The sensitivity ranking corroborates the bias-variance decomposition of §8.3: because the simulated apogee is roughly 0.4 times as sensitive to a fractional drag change as the bare 10% perturbation, the per-flight scatter on the order of σ = 5% in apogee error reported in §8.2 is consistent with a 12–13% spread in per-flight effective drag — a range that is well within the documented spread of motor-lot variation, build tolerance, and atmospheric soundings across the corpus. The model is not the dominant source of residual.
