# JSR Flagship — Drafting Brief (authoritative numbers + decisions)

**This file is the single source of truth for drafting `jsr_paper.tex` sections.**
Every number here is regenerated from `rocket-flight-database/flight_comparison.csv`
(the published RFD ground truth, model column = current archived code) via
`paper/data/analysis/.../analyze.py` + `uncertainty_quantification.py`, or from
`paper/data/VALIDATION_MATRIX.md` for component benchmarks. Do not invent numbers.

---

## 0. CORPUS DECISION (most important)

- **Headline corpus = 25 flights** (24 single-stage from Rogers' public RASAero II
  comparison set + MESOS 293K two-stage closure), Mach 0.54–4.33. This set is
  **externally chosen** (Rogers selected it; not outcome-curated by us), so its
  accuracy statistics are an honest validation result.
- **High-Mach sounding rockets = EXPLORATORY section, report the FULL set** (~20
  flights: 3 within ±10% — Black Brant V VB M7.22 −6.97%, Nike-Deacon M4.96
  −1.06%, Nike-Deacon M5.08 −0.89%; 17 outside — Nike-Apache +24…+38%, Nike-Cajun
  +16.6%, Arcas/HEROS −29…−69%). NEVER present these as a "28/28 within ±10%"
  headline — that would be outcome-based selection bias (the single biggest
  rejection risk). Frame: the method *reaches* Mach 7 within ±7% on
  well-characterized vehicles, but motor/geometry reconstruction uncertainty
  dominates on poorly-documented historical flights.
- Title stays supersonic-anchored (validated to M4.33); hypersonic is a
  *demonstrated method capability* (exploratory), not a headline validation claim.

---

## 1. HEADLINE NUMBERS (25-flight, reproducible)

| Predictor | N | Mean signed | σ | RMSE | MAE | ≤±5% | ≤±10% |
|---|---|---|---|---|---|---|---|
| OpenRocket-Plus | 25 | **−0.38%** | 5.44% | 5.34% | 4.74% | 14/25 | **25/25** |
| RASAero II | 25 | +2.46% | 5.81% | 6.20% | 5.34% | 13/25 | 22/25 |

- 95% bootstrap CIs (20000 resamples, seed 0x51A7EA): mean [−2.41, +1.72];
  σ [4.20, 6.25]; RMSE [4.38, 6.19]; MAE [3.78, 5.70]; ≤±5% 56% [36, 76]; ≤±10% 100% [100,100].
- Mean-error CI brackets zero → predictor is **statistically unbiased** on this corpus.
- Bias²/MSE: ORP **0.01**, RAS 0.16 → ORP residual is essentially pure variance
  (build tolerance, motor lot, atmosphere), not directional model bias.

### Paired ORP vs RASAero II (n=25)
- Mean |ORP|−|RAS| = **−0.60 pp**, 95% bootstrap CI **[−2.16, +0.96]** (straddles 0).
- Wilcoxon signed-rank on |error|: W=143.0, **p=0.615** → no significant difference.
- Bland-Altman: ±14.3% limits of agreement, mean offset −2.84%.
- **Honest claim = PARITY** on this corpus with this version-locked RASAero set.
  NOT "superiority." Disclose RASAero values are Rogers' recorded predictions
  (not fresh pre-flight reruns we can independently verify).

### Per-regime (25-flight; ORP)
| Regime | Mach | N | Bias | σ | RMSE | MAE | ≤5% | ≤10% |
|---|---|---|---|---|---|---|---|---|
| Subsonic | <0.8 | 9 | +2.54% | 4.37 | 4.83 | 4.30 | 7 | 9 |
| Transonic | 0.8–1.3 | 7 | **−3.66%** | 5.36 | 6.17 | 5.84 | 2 | 7 |
| Low supersonic | 1.3–3.0 | 5 | +1.83% | 5.70 | 5.42 | 4.61 | 3 | 5 |
| High supersonic | 3.0–5.0 | 4 | −3.98% | 2.97 | 4.73 | 3.97 | 2 | 4 |
| Hypersonic | >5.0 | 0 | — | — | — | — | — | — |

- Transonic (−3.66%) is the disclosed regime weakness. Hypersonic regime is EMPTY
  (max Mach 4.33). Above M3 is descriptive (small n), no inferential claim.

### Distribution / normality (ORP, n=25)
Shapiro-Wilk W=0.905, p=0.023 (reject normality at α=0.05); Anderson-Darling
A²=0.922 (crit 0.728); skew +0.43, excess kurtosis −1.14 (light-tailed/platykurtic,
NOT heavy-tailed; max |err| = 8.7%). → use non-parametric Wilcoxon as primary;
bootstrap CIs (not normal-theory). Low power at n=25: failing-to-reject ≠ normal.

---

## 2. IN-SAMPLE DISCLOSURE + GENERALIZATION (decontaminated holdout)

- Two base-drag scale constants are B-level / corpus-frozen: `THICK_BL_K=2.2`
  (anchor: Raven) and `SLENDER_BODY_K=0.0025` (anchors: Raven/Rabia/Kinsel; source
  diagnostic also used Torrent). So the headline is **partly in-sample**.
- **Decontaminated prospective holdout** (every flight the constants touched —
  Raven, Rabia, Rabia Short Fin Can, Kinsel, Torrent — placed in DEV):
  - DEV (n=13): mean +0.22%, **MAE 5.47%**
  - HOLDOUT (n=12, genuinely blind): mean −1.03%, **MAE 3.95%**
  - Holdout MORE accurate than dev → the two constants **generalize, not overfit**.
- This is the PRIMARY in-sample defense. (Original split listed Rabia in holdout,
  which was contaminated; decontaminated split fixes it — see commit 6e63fc971.)

---

## 3. MECHANISM ABLATION (re-run in isolation; valid)

Disabling each mechanism, effect on corpus apogee error (mean |Δ|, 24 single-stage):
| Mechanism | mean |Δ| | max |Δ| (flight) | Note |
|---|---|---|---|
| Finned-base augmentation (FINNED_BASE_K, EXTERNAL/Basic-Finner) | **8.10 pp** | 39.5 (Kinsel M2.19) | dominant apogee driver |
| Van Driest II skin friction | 0.87 pp | 7.9 (Qu8k M3.46) | matters at high Mach |
| DATCOM 4.1.5.1 fin wave drag | 0.39 pp | 1.9 (Proteus) | modest |
| **ShockGeometry pre-pass** | **0.15 pp** | 3.6 (FMJ BR6 M2.46) | inert subsonically |
| PNK interference / K1 floor | 0.00 | 0.00 | no apogee effect |

**HONEST pre-pass framing [F3]:** the pre-pass moves *integrated apogee* by only
0.15pp mean — because apogee integrates a trajectory dominated by lower-Mach drag,
and the pre-pass is inert below M1. Its value is **local-flow fidelity** (correct
post-shock conditions for fin loads/stability, verified bit-for-bit vs
Taylor-Maccoll) and as the **architectural seam** enabling the downstream
supersonic models — NOT a gross-apogee win. Do NOT overclaim. The dominant apogee
mechanism is the externally-calibrated finned-base augmentation.

---

## 4. CORRECTED COMPONENT BENCHMARKS (Table 4 — true current values)

Source: `paper/data/VALIDATION_MATRIX.md` + metric CSVs. KEY CORRECTIONS vs old draft:
- **Finned-vehicle total drag (Basic Finner ADA636861): MAPE 11.8%** (8 pts, M1.08–4.30) — NOT 22.7% (stale).
- **Hypersonic cone foredrag (DTIC AD0487365): MAPE 19.7%** (11 pts, M6.5–17.2, max +57%) — NOT 16.7% and NOT 17.6%.
- Speed of sound max err 0.016%; oblique-shock max err 0.021%; Prandtl-Meyer 0.004°; Taylor-Maccoll shock-angle 0.825% (gate 1%).
- Nose/body foredrag (A52H28): MAE 0.029 (gate 0.035). Base turbulent (TN3393): MAPE 15.9%. Base laminar: 4.4% is the **Chapman** closure (attribute to Chapman, NOT the TN3393 laminar row which is 44% legacy).
- Static stability (X-653): CNα MAPE 6.84%, xCP/d MAPE 7.11% (10 pts each).
- **A-level count: ~27** (not 22). Update "Twenty-Two" → current count, OR state count carefully.
- **DOWNGRADE out of A-level (to qualitative/secondary):** AGARD-B row (its own report calls it "loose qualitative trend closure"; total-drag MAPE 22.6%) and Vortex-sideforce Kv=0.20 (range-check, not MAPE; source unverifiable). Transonic Cmq augmentation is B-level (overshoots +110–160% per Sznajder).

---

## 5. EQUATION + CITATION FIXES

### [F4] Van Driest II Eq (17) — the model is CORRECT; the draft equation was mis-transcribed.
Write it to MATCH the code (`BarrowmanDragCalculator.computeVD2Fc`):
- m = (γ−1)/2 · Mₑ² ;  F = Tw/Te ;  r = 0.88 (recovery factor)
- A = √(r·m / F) ;  B = (1 + r·m − F) / F
- Fc = r·m / (arcsin α + arcsin β)² ,  α = (2A²−B)/√(4A²+B²) ,  β = B/√(4A²+B²)
- Numerator is **r·m (= T_aw/Te − 1)**, NOT "Tw/Te − 1". For adiabatic wall (Tw/Te=1+r·m), B=0.
- Ftheta from Sutherland viscosity ratio; Fx=Ftheta/Fc; Cf = Cf_inc/Fc. Cite hopkins1971 + NASA TN D-6945.

### [F5] Base drag — DROP the fabricated "Devan-Ashwood" citation entirely.
Cd_base = 0.064 + 0.186/M² → present as an empirical supersonic correlation
**validated against NACA TN 3393 (chapman1955) and consistent with ESDU 77021
(esdu77021)**. Do not attribute to "Devan-Ashwood / NASA TN D-721" (fabricated).

### Citation key map (use jsr_paper.bib):
- AGARD-B [CITE:CHECK-agard-b] → `anderson1970agardb` (AEDC-TR-70-100). Mark provisional / qualitative.
- Vortex Kv=0.20 [CITE:CHECK-paulwedemeyer] → NO valid source; present Kv=0.20 as an internally-calibrated coefficient, remove the A-level/literature-anchored claim. Do NOT cite paulwedemeyer.
- Renamed/resolved keys in bib: quintart2025, platou1965, nasa_sp8039, dahlembuck1966, mooremcinvillehymer2002, moore2001, lowcostroll2025, aedc7658, dtic_ad0733141.
- Prior art [F2]: cite niskanen2009 (OpenRocket), rocketpy2021, barrowman1967, rogers2015 (RASAero II), sooy2005 + mooremcinvillehymer2002 + moore2001 (Missile DATCOM/Aeroprediction lineage), quintart2025 + lowcostroll2025 (JSR OSS/amateur-supersonic precedent).

---

## 6. [F1]–[F12] RESOLUTION CHECKLIST

- [F1] Title/scope: validated SUPERSONIC to M4.33; hypersonic = capability + exploratory. ✅ (title set)
- [F2] §1.2 prior art: add Missile DATCOM/Aeroprediction + OpenRocket/RocketPy/RASAero. (cites in §5 above)
- [F3] §2: pre-pass contribution = architecture + local-flow fidelity; ablation shows 0.15pp apogee (honest, not a big win). Finned-base aug dominant.
- [F4] §3.5 Eq(17): use the verified code form above.
- [F5] §4.3: drop Devan-Ashwood → ESDU 77021 / NACA TN 3393.
- [F6] §6: true MAPEs (Basic Finner 11.8%, cone 19.7%); resolve/downgrade AGARD-B + vortex CITE:CHECK.
- [F7] §6.3: in-sample disclosure (THICK_BL_K, SLENDER_BODY_K B-level) + decontaminated holdout generalization.
- [F8] §8: 25-flight honest numbers; dev/holdout (decontaminated); bootstrap CIs; RASAero parity (Wilcoxon p=0.62, CI straddles 0) NOT superiority; post-flight-tuned RAS disclosure.
- [F9] §8.1: corpus = Rogers' external 25 (outcome-independent inclusion). State this explicitly.
- [F10] §9 (new): exploratory high-Mach — full ~20 sounding rockets (3 pass / 17 fail), honest, no cherry-pick.
- [F11] Data/Code Availability: RFD DOI 10.5281/zenodo.19976138 (v1.2); code Zenodo DOI + release tag = mint at submission (flag: needs push). GitHub AidanSYu/openrocketsupersonic + AidanSYu/rocket-flight-database.
- [F12] §limitations: in-sample (2 constants) + transonic weakness (−3.66%) + sparse high-Mach + heterogeneous truth + MESOS regression (−6.96% current vs −0.6% archived; disclose) + no own CFD (4 published comparators) + Cmq 3× multiplier B-level.

---

## 7. FIGURES (regenerated on reconciled 25-flight data)
Corpus (in `paper/data/analysis/corpus_bias_variance_2026_05_11/`): error_vs_mach.png,
predictor_distributions.png, predictor_paired.png, regime_breakdown.png, bias_variance.png,
qq_normal.png. Bootstrap: `corpus_uncertainty_2026_06_02/bootstrap_distributions.png`.
Benchmarks (in `paper/data/png/`): naca1135_*, us_standard_atmosphere_*, sutherland_*,
rayleigh_pitot_*, taylor_maccoll_*, naca_rm_a52h28_validation, naca_tn_3393_base_pressure,
naca_tn_3650_fin_wave_drag, van_driest_ii_cf_vs_mach, hypersonic_cone_drag,
nasa_tm_x653_stability, tobak_cmq_comparison, transonic_cmq_augmentation,
shockgeometry_block_diagram, shockgeometry_surface_mach_verification,
cfd_validation_panels, sensitivity_tornado_composite, aerodynamic_methods_hierarchy.
(Some benchmark CSVs/pngs need a deliberate regen pass to ensure they reflect the
true MAPEs in §4 — verify Basic Finner / cone figures show 11.8% / 19.7%.)

## 8. SENSITIVITY framing [F8/F12]
The 4-flight operational sweep (Cd-scale, dt, altitude, rod-angle) runs on the
high-Mach EXPLORATORY vehicles — frame as the worst-case (fastest, highest-Mach)
trajectories that bound the gentler headline flights for numerical convergence.
Cd-scale dominates (mean |s|≈4%/10%); time-step |s|≈0.98% over 0.025–0.10 s →
numerically converged (AIAA accuracy policy). Be explicit the sweep vehicles are
exploratory; in-sample defense rests on the decontaminated holdout, not this sweep.
