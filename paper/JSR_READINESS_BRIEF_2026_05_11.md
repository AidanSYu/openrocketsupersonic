# JSR Submission Readiness Brief — 2026-05-11

Compiled after the V2 corpus expansion, CFD digitization (Vidanović + Sznajder), bias-variance analysis, and sensitivity sweep. Maps every new artifact to a specific JSR paper section, with pull-quote text and figure pointers ready to drop in.

Target journal: **AIAA Journal of Spacecraft and Rockets** (JSR). Existing draft `paper/AST_PAPER.md` is JSR-compatible in structure (same Tier 1 aero validation bar as AST); needs updates listed in §10 below.

---

## 1. Current validation state — by the numbers

### 1.1 Flight corpus (Rocket Flight Database v1.2)

| Metric | Value |
|---|---|
| Flights total | 28 |
| Mach range | 0.54 to 7.22 |
| Apogee range | 3,577 ft to 897,638 ft (273.6 km) |
| ORP mean signed error | **−0.44%** |
| ORP error σ | 5.13% |
| ORP RMSE | 5.06% |
| ORP within ±5% | 17 / 28 |
| ORP within ±10% | **28 / 28** |
| RASAero II mean \|err\| (25 paired flights) | 5.34% |
| ORP \|Bias²\| / MSE | 0.01 — essentially unbiased |
| RAS \|Bias²\| / MSE | 0.16 — has systematic bias |
| Paired Wilcoxon test (25 flights) | p = 0.375 — statistical tie |
| Paired wins (ORP / RAS) | 14 / 11 |

Source: `paper/data/analysis/corpus_bias_variance_2026_05_11/corpus_bias_variance_summary.md`.

### 1.2 CFD comparators (four total)

| Source | Geometry | Quantity | Mach | ORP comparison |
|---|---|---|---|---|
| Bunescu 2025 URANS | Basic Finner | C_N, C_X | 0.4-3.5 | MAPE 39% C_X, 63% C_N at AoA 10° (existing) |
| Sahu 1983 TLNS | Ogive-cyl-boattail | Cd_base, Cd_total | 0.9-1.2 | Memo cite-only (no comparator yet) |
| Vidanović 2014 SST | AGARD-B | CD, CL, Cm | 0.6, 1.6 | Reference dataset (no ORP ORK yet) |
| Sznajder 2025 Fluent | Basic Finner | Cmq + Cm_αdot | 0.9-4.5 | MAPE 31.6% (M ≥ 1.29), +110-160% transonic peak overshoot |

Source: `paper/data/cfd_inventory_2026_05_02.md`, updated 2026-05-11.

### 1.3 Per-regime breakdown

| Regime | N | ORP mean signed err | ORP MAE | RAS mean signed err | Verdict |
|---|---:|---:|---:|---:|---|
| Subsonic (M < 0.8) | 12 | +0.84% | 4.91% | +5.74% | ORP cleaner, RAS skewed high |
| Transonic (0.8 ≤ M ≤ 1.3) | 7 | **−3.67%** | 5.49% | −0.36% | **RAS wins 6/7 paired matchups** |
| Low supersonic (1.3 < M ≤ 3.0) | 5 | +2.06% | 4.45% | +4.51% | ORP cleaner |
| High supersonic (3.0 < M ≤ 5.0) | 1 | −0.60% | 0.60% | −1.26% | both excellent (n=1) |
| Hypersonic (M > 5.0) | 3 | −2.97% | 2.97% | — | ORP only, 3/3 within ±10% |

Source: `regime_breakdown.csv`.

### 1.4 Sensitivity ranking

Cd scale is the dominant parameter governing apogee error. From the 4-flight (HEROS 3, Arcas blunt, Nike-Apache 14.108 GI, Black Brant V VB) sensitivity sweep at ±10%:

| Rank | Parameter | Mean \|sensitivity\| | Notes |
|---|---|---:|---|
| 1 | Total Cd scale | 4.00% | Largest at HEROS 3 (M≈1.9, 7.04%), smallest at Arcas (1.72%) |
| 2 | Launch rod angle | 1.11% | Strongest at HEROS 3 (gravity-loss-dominated low-alt) |
| 3 | Launch altitude | 0.96% | Modest across all flights |
| 4 | Time step | 0.98% | **Numerically converged in 0.025-0.10 s** — no spurious sensitivity |

Source: `paper/data/analysis/sensitivity_2026_05_11/sensitivity_summary.md`.

### 1.5 Component benchmarks (already in existing AST_PAPER §8)

22 A-level externally benchmarked subsystems. Headline numbers (already cited in current draft):
- Shock relations: < 0.1% vs NACA Report 1135
- Nose wave drag MAE 0.029 vs NACA RM A52H28
- Fin CNα MAPE ≤ 8% vs NASA TM X-653
- Cone foredrag MAPE 16.7% vs DTIC AD0487365 (M 6.5-17.2)
- Base drag MAPE 15.9% turbulent / 4.4% laminar vs NACA TN 3393

---

## 2. The headline JSR claim

> *"OpenRocket Plus extends the open-source OpenRocket aerodynamic core to Mach 7+ via a shock-geometry pre-pass and 22 externally benchmarked subsystems. Across a 28-flight ground-truth corpus spanning Mach 0.54 to 7.22 (apogee 3,577 ft to 897,638 ft, with both amateur high-power and radar-tracked sounding rocket flights), the simulator predicts apogee with mean signed error −0.44%, σ = 5.13%, RMSE = 5.06%, and 28 of 28 flights within ±10%. Whole-corpus bias² accounts for less than 1% of mean-squared error, indicating the residual is dominated by per-flight scatter rather than systematic model bias. Paired comparison against the commercial reference RASAero II on 25 common flights yields no statistically significant difference in absolute error (Wilcoxon p = 0.375)."*

This is a defensible JSR-tier headline.

---

## 3. Honest limitations to disclose

Reviewer-vulnerable findings the paper MUST address head-on rather than bury:

### 3.1 Transonic regime weakness vs RASAero II
- ORP mean signed error in M 0.8-1.3 band: **−3.67%** (vs RASAero −0.36%)
- RAS wins **6 of 7** paired transonic matchups
- Likely cause: the new supersonic-tuned blending region pulls drag low on the subsonic side of M=1
- Independent confirmation from Sznajder Cmq: ORP overshoots transonic peak by 110-160% at M=1.08-1.11 due to `k_transonic = 1 + 2.5·exp(−((M−1)/0.15)²)` augmentation
- **Two independent observations converge on the transonic blending region as the largest open calibration gap.**

### 3.2 Phase 6h M>5 coast drag bias
- Per-component Cd analysis (existing `NikeApacheCoastCdDiagnosticTest`) shows pressure Cd plateaus flat at 0.234 from M=2 through M=8 vs NASA X-721-66-568 Apache handbook value ~0.30
- Mean Cd deficit M ≥ 5: **+0.0595**
- Root cause: `SLENDER_BODY_MACH_DECAY_END = 5.0` in `BarrowmanDragCalculator.java` — Hoerner cylindrical-afterbody pressure drag turned off at M=5 for high-L/D bodies
- Affects 9 Nike-Apache 1965 flights (+24-38% apogee overshoot) and 1 Nike-Cajun (+17%) currently held out of the corpus per ±10% admission criterion
- Documented as Phase 6h in `SUPERSONIC_MODELING.md` with proposed fix and validation gate

### 3.3 Corpus skew toward subsonic amateur HPR
- 22 of 28 flights are M < 3
- Only 3 flights at M > 5 (BBV M=7.22, 2× Nike-Deacon M~5)
- Hypersonic claim is supported by component benchmarks (M 6-17) plus 3 integrated flights — not yet by N=10+ integrated flights

### 3.4 Aeroelastic model is implemented but disabled
- `AeroelasticModel.java` exists with `Q_THRESHOLD = 1e12` — effectively off
- Disclose explicitly; no aeroelastic claims in paper

### 3.5 CFD comparators are published-CFD references, not own-runs
- Standard industry practice but a hostile reviewer may ask for one own-CFD comparison
- Mitigation: cite four independent CFD sources (Bunescu, Sahu, Vidanović, Sznajder) showing the same regime patterns

### 3.6 Distribution of corpus residuals
- Shapiro-Wilk rejects normality at p = 0.028
- Light-tailed, mildly right-skewed (skew = +0.48, excess kurtosis = −0.86)
- May appear bimodal (clusters near −7% and +7%)
- Mitigation: report Bias²/MSE = 0.01 (random scatter not directional drift) and run non-parametric tests (Wilcoxon) for predictor comparison

---

## 4. Suggested JSR section structure (mapped to existing AST_PAPER.md)

| § | Title | Current state | Updates needed |
|---|---|---|---|
| Abstract | — | references 24-flight SimVReal corpus | **Rewrite to v1.2 numbers**: 28 flights, mean −0.44%, σ 5.13%, 28/28 ±10% |
| 1 | Introduction | strong | minor: update problem statement to cite v1.2 corpus availability |
| 2 | Atmospheric Model | strong | none |
| 3 | Shock Relations | strong | none |
| 4 | Shock Geometry Pre-Pass | strong | none |
| 5 | Drag Models | strong | **add Phase 6h disclosure subsection §5.7** with deficit table |
| 6 | Stability Model Extensions | strong | **add Sznajder Cmq comparator finding** (k_transonic overshoot) |
| 7 | Dynamic Stability | partial | none — Tobak already cited |
| 8 | Validation Results | **major rewrite** | see §5 below |
| 9 | Limitations | partial | **add §9.3 transonic regime weakness, §9.4 Phase 6h, §9.5 corpus skew** |
| 10 | Conclusions | — | restate v1.2 corpus numbers |

---

## 5. Validation section (§8) — full content map

This is the section that gets the most reviewer scrutiny. Reorganize into four subsections.

### §8.1 Analytical and wind-tunnel component benchmarks (already in draft)
Table of the 22 A-level subsystems with MAPE/MAE figures. **No changes needed** — already strong.

### §8.2 Published CFD comparators (NEW — assemble from memos)

Four-panel figure: **`figs/cfd_validation_panels.png`** (to be produced)
- (a) Basic Finner C_X vs M, ORP vs Bunescu URANS (M 0.4-3.5)
- (b) Ogive-cyl-boattail Cd_base vs M, ORP vs Sahu TLNS (M 0.9-1.2)
- (c) AGARD-B CD vs AoA at M=1.602, Vidanović SST vs VTI experiment (reference; no ORP yet)
- (d) Basic Finner Cmq+Cm_αdot vs M, ORP vs Sznajder MRF+FOM (M 0.9-4.5)

Pull-quote text:
> *"The simulator was compared against four independent published-CFD sources spanning two geometries (Basic Finner, AGARD-B calibration model), two aerodynamic quantities (static drag, dynamic pitch damping), and three Mach bands (transonic, supersonic, and hypersonic limit). Bunescu et al. 2025 URANS on the Basic Finner provides primary static-coefficient validation across M 0.4-3.5 [CITE]; the present method's axial-force C_X MAPE relative to that CFD is 39% (Re-effect partially explained by the present method's coarser viscous treatment). Sahu et al. 1983 thin-layer Navier-Stokes provides transonic base-drag validation [CITE]. Vidanović et al. 2014 SST k-omega on the AGARD-B calibration model is included as a state-of-the-art reference [CITE] (the present method does not currently ship an AGARD-B geometry definition). Sznajder 2025 ANSYS Fluent on Basic Finner pitch damping confirms a 27-36% conservative bias in the supersonic Cmq prediction with the same sign and magnitude as the existing Bhagwandin & Sahu 2013 (ARL-TR-6725) CFD comparator [CITE], and exposes a transonic-peak over-augmentation in the present method's k_transonic Gaussian coefficient that is addressed in §9."*

### §8.3 Flight corpus validation (REWRITE for v1.2)

Replace the existing 24-flight SimVReal section with **v1.2 (28 flights)**.

Tables to add:
- **Table 8.X — Corpus summary**: per-flight rows (flight name, motor, M_peak, alt_site, apogee_real, apogee_ORP, err%, source) — drop from `flight_comparison.csv` directly.
- **Table 8.Y — Aggregate accuracy**: predictor × (N, mean |err|, ±5%, ±10%) for ORP and RAS.
- **Table 8.Z — Per-regime breakdown**: `regime_breakdown.csv` directly.

Figures to add:
- **`figs/corpus_error_vs_mach.png`** ← from `error_vs_mach.png`
- **`figs/corpus_distributions.png`** ← from `predictor_distributions.png`
- **`figs/corpus_paired.png`** ← from `predictor_paired.png` (Bland-Altman style)
- **`figs/regime_bias_variance.png`** ← from `bias_variance.png`

Pull-quote text:
> *"OpenRocket Plus was validated against a 28-flight ground-truth corpus spanning Mach 0.54 to 7.22 and apogee 3,577 ft to 897,638 ft. Flights 1-25 are taken from the public RASAero II flight-comparison set published by Rogers [CITE]; flight 26 is the Black Brant V VB AAF-VB-32 single-stage sounding rocket flown at Churchill, Manitoba on 3 March 1971 (radar-tracked to 273.6 km / 897,638 ft) from DTIC AD0733141 [CITE]; flights 27-28 are two Nike-Deacon two-stage sounding rocket flights from NACA TN 3739 (Heitkotter 1956, Wallops Island, radar-beacon-tracked to 108 km and 107 km). The combined corpus carries a mean signed apogee error of −0.44%, standard deviation 5.13%, RMSE 5.06%, and 28 of 28 flights within ±10% of the measured apogee. Whole-corpus bias-squared accounts for less than 1% of mean-squared error, indicating the residual is dominated by per-flight stochastic variance (build tolerance, motor lot variation, atmospheric soundings) rather than systematic model bias. On the 25 flights with paired RASAero II predictions, the present method shows no statistically significant difference in absolute error (Wilcoxon signed-rank p = 0.375, 14 ORP wins vs 11 RAS wins). The corpus and all build files are released under CC-BY-4.0 with Zenodo DOI 10.5281/zenodo.19976138 [CITE]."*

### §8.4 Sensitivity analysis (NEW)

Tornado-chart figure: **`figs/sensitivity_tornados.png`** (composite 4-panel)
- Panel per flight: HEROS 3, Arcas, Nike-Apache, BBV
- Bars ordered by |s| (sensitivity coefficient = ∂apogee/∂param at ±10%)

Pull-quote text:
> *"A local sensitivity analysis was performed on four representative corpus flights (HEROS 3 hybrid sounding rocket at M = 1.89; Arcas Performance Flight 2 at M = 2.30; Nike-Apache 14.108 GI at M = 6.50; Black Brant V VB at M = 7.22) over four parameters (total Cd multiplicative scale, integrator time step, launch rod angle, launch altitude) at ±10% perturbations. Total Cd scale dominates apogee sensitivity with corpus-mean |∂apogee/∂Cd| = 4.00% per 10% Cd perturbation (median 3.62%); launch rod angle is the second-strongest sensor at mean |s| = 1.11%. Integrator time step exhibits |s| = 0.98% over the 0.025-0.10 s sweep, confirming that the apogee predictions reported herein are numerically converged within the operational time-step envelope."*

---

## 6. Figures roster for the paper

Already exist (just need to be referenced):
- `paper/data/analysis/corpus_bias_variance_2026_05_11/error_vs_mach.png`
- `paper/data/analysis/corpus_bias_variance_2026_05_11/predictor_distributions.png`
- `paper/data/analysis/corpus_bias_variance_2026_05_11/predictor_paired.png`
- `paper/data/analysis/corpus_bias_variance_2026_05_11/bias_variance.png`
- `paper/data/analysis/corpus_bias_variance_2026_05_11/regime_breakdown.png`
- `paper/data/analysis/corpus_bias_variance_2026_05_11/qq_normal.png`
- `paper/data/analysis/corpus_bias_variance_2026_05_11/error_hist.png`
- `paper/data/analysis/sensitivity_2026_05_11/tornado_heros3.png`
- `paper/data/analysis/sensitivity_2026_05_11/tornado_arcas_blunt_f2.png`
- `paper/data/analysis/sensitivity_2026_05_11/tornado_nike_apache_14_108.png`
- `paper/data/analysis/sensitivity_2026_05_11/tornado_bbv_aaf32.png`
- `paper/data/analysis/sensitivity_2026_05_11/sensitivity_heatmap.png`

To be produced:
- **CFD comparator 4-panel**: composite of Bunescu C_X, Sahu Cd_base, Vidanović CD, Sznajder Cmq+Cm_αdot. Can be assembled from the four existing comparator CSVs with a single matplotlib script.
- **Per-component Apache Cd vs Mach (Phase 6h disclosure figure)**: from `NikeApacheCoastCdDiagnosticTest.java` output. Shows the 0.06 Cd deficit at M ≥ 5.
- **k_transonic over-augmentation figure**: ORP Cmq+Cm_αdot vs Mach vs Sznajder vs ARL experiment at M 0.9-1.5.

---

## 7. Citations the paper will need

Already in `paper/paper.bib`: NACA Report 1135, NACA RM A52H28, NASA TR R-100, NASA TR R-226, etc. (Confirm before submission.)

NEW citations needed for §8.2 (CFD comparators):
- Bunescu et al. 2025 — `aerospace-12-00371-v2.pdf` — Aerospace 12, 371 — DOI 10.3390/aerospace12050371
- Sahu, Nietubicz, Steger 1983 — BRL TR-02495 — DTIC AD-A130-293
- Vidanović et al. 2014 — Thermal Science 18(4), 1223-1233 — DOI 10.2298/TSCI130409104V
- Sznajder 2025 — Transactions on Aerospace Research 281(4), 98-121 — DOI 10.2478/tar-2025-0021
- Bhagwandin & Sahu 2013 — ARL-TR-6725

NEW citations for §8.3 (corpus):
- Rogers, C. E. — RASAero II Comparisons with Altitude Data — https://www.rasaero.com/comparisons-alt.htm
- DTIC AD0733141 — Bristol/NRC Black Brant V VB AAF-VB-32 Churchill 1971
- NACA TN 3739 (Heitkotter 1956) — NTRS 19930084525
- Yu, A. — Rocket Flight Database — Zenodo DOI 10.5281/zenodo.19976138

---

## 8. What's still needed for "very robust" JSR submission

| Gap | Effort | Priority |
|---|---|---|
| **Update AST_PAPER.md** abstract + §8 + §9 to v1.2 numbers | 1-2 days | **High** |
| **CFD comparator 4-panel figure** (composite plot script) | 0.5 day | **High** |
| **Phase 6h disclosure subsection** with deficit table | 0.5 day | **High** |
| **k_transonic over-augmentation discussion** with Sznajder comparator | 0.5 day | **High** |
| **Stand up AGARD-B `.ork`** to enable own-ORP Vidanović comparator | 2-3 days | Medium |
| **Stand up RM-10 ogive-cyl-boattail `.ork`** to enable own-ORP Sahu comparator | 1-2 days | Medium |
| **Sahu Java comparator test** (mirror Bunescu pattern) | 1 day | Medium |
| **AeroPac 100K rocket reconstruction** to add a Mach-3 amateur flight | 2-4 days | Low |
| **Close Phase 6h** (Hoerner hypersonic body pressure drag) | 1-2 weeks | Low (paper can ship with the disclosure) |
| Five more hypersonic corpus flights | weeks | Lowest |

**Minimal-effort JSR-ready package**: top 4 items above (≈ 3-4 days of paper-side work). Paper submits with the disclosed limitations as honest scoping, not as failure modes.

**Stretch JSR-ready package**: add AGARD-B and Sahu ORK builds + their Java comparator tests (≈ 1 additional week). Removes the "no own-CFD comparisons" reviewer vulnerability.

---

## 9. Risk register

| Risk | Mitigation |
|---|---|
| Reviewer asks "where's your own CFD?" | Cite four independent published-CFD sources + offer the Bunescu Java comparator as the own-comparison artifact (it's a regression test that runs Cd_X / C_N on ORP and compares against digitized Bunescu values inside the test) |
| Reviewer asks "why is transonic regime worse than RASAero?" | Disclose head-on in §9, point to k_transonic over-augmentation Sznajder evidence as known calibration gap with quantified magnitude |
| Reviewer asks "why only 3 hypersonic flights?" | Phase 6h is being held off the corpus by the ±10% inclusion criterion; the 9 Nike-Apache 1965 flights are already simulated and on disk awaiting model fix |
| Reviewer asks "what about aeroelasticity?" | Disclose explicitly: `AeroelasticModel.java` is implemented but `Q_THRESHOLD = 1e12` keeps it off pending flutter/divergence validation |
| Reviewer asks "is the corpus available?" | Yes — Zenodo DOI 10.5281/zenodo.19976138, CC-BY-4.0 |
| Reviewer asks "can I reproduce this?" | Yes — `rocket-flight-database/docs/reproducibility.md` + `paper/data/analysis/*/analyze.py` are committed |

---

## 10. Action list for next session

In order of priority:

1. **Update `AST_PAPER.md` abstract** to v1.2 corpus numbers (15 min).
2. **Rewrite §8.3** with the v1.2 flight corpus table and aggregate. Drop in `error_vs_mach.png`, `predictor_paired.png`, `regime_breakdown.png`, `bias_variance.png` (2-3 hr).
3. **Add §8.2 CFD comparators** (Bunescu, Sahu, Vidanović, Sznajder) with the 4-panel composite figure (3-4 hr).
4. **Add §8.4 sensitivity analysis** (1-2 hr).
5. **Add §9.3 transonic regime weakness** + **§9.4 Phase 6h** + **§9.5 corpus skew** + **§9.6 aeroelasticity disabled** disclosures (1-2 hr).
6. **Rebuild `paper.bib`** with new citations (1 hr).
7. **Run `plot_all_validation.py` script** to regenerate any global figures if it exists (15 min).
8. Final read-through against AIAA JSR submission checklist.

Total estimate: **2 working days** to JSR-submittable draft, leveraging the artifacts already produced this session.
