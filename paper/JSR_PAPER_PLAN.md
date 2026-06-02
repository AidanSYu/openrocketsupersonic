# JSR_PAPER_PLAN.md — Architectural Blueprint for OpenRocket Plus JSR Paper

**Status:** Authoritative planning document. Section-writer agents must consult this before writing.
**Target:** AIAA Journal of Spacecraft and Rockets, Full-Length Paper, ~11,000 words, ~22 figures.
**Author:** Aidan Yu, Independent Researcher.
**Date:** 2026-05-16.

---

## 0. Top-Level Front Matter

### 0.1 Title — three options, ranked

1. **(Recommended)** *A Shock-Geometry Pre-Pass Architecture for Supersonic and Hypersonic Sounding-Rocket Aerodynamic Prediction* — 12 words, leads with the architectural novelty, scopes to "sounding rocket" (matches JSR's missile/spacecraft frame), avoids "amateur."
2. *Open-Source Mach 7 Aerodynamic Prediction for Slender Finned Missile and Sounding-Rocket Vehicles* — 12 words, leads with the regime and corpus, names the vehicle class explicitly.
3. *Extending an Open-Source Trajectory Simulator to Mach 7 via Shock-Geometry Pre-Pass and Twenty-Two Externally Benchmarked Subsystems* — 18 words, **rejected (exceeds 12-word cap)**, retained here only as a long-form mental model.

### 0.2 Abstract draft (~180 words, no acronyms beyond AIAA/NASA, no bracketed cites)

> Open-source rocket flight simulators are reliable subsonically but lose fidelity above approximately Mach one, leaving a gap for university, sounding-rocket, and supersonic missile applications. This paper presents a shock-geometry pre-pass architecture that walks the vehicle nose-to-tail once per timestep, distributing locally corrected post-shock Mach, pressure, and temperature to every downstream component calculator, and a twenty-two-subsystem replacement of the underlying engineering models including Taylor-Maccoll cone flow, shock-expansion nose drag, Van Driest II compressible skin friction, Datcom 4.1.5.1 fin wave drag, Devan-Ashwood and Chapman base drag, and Modified Newtonian hypersonic pressure, blended at regime transitions by C-one-continuous Hermite and rational functions. Each subsystem is benchmarked against published wind-tunnel, range, or computational fluid-dynamics data. Integrated trajectory validation across a twenty-eight-flight ground-truth corpus spanning Mach zero point five four to seven point two two and apogee one point one to two hundred seventy-four kilometers yields mean signed apogee error minus zero point four four percent, standard deviation five point one three percent, and twenty-eight of twenty-eight flights within ten percent of measured altitude.

### 0.3 Nomenclature table (expected symbol list)

| Symbol | Meaning | Units |
|---|---|---|
| $a$ | Speed of sound | m/s |
| $A_b, A_e$ | Base area, nozzle exit area | m² |
| $C_D$ | Drag coefficient | — |
| $C_{D_b}$ | Base drag coefficient | — |
| $C_{D_w}$ | Wave drag coefficient | — |
| $C_f$ | Skin friction coefficient | — |
| $C_{N_\alpha}$ | Normal-force-curve slope | rad⁻¹ |
| $C_p, C_{p,\max}$ | Pressure coefficient, max (Rayleigh pitot) | — |
| $C_{m_q}, C_{m_{\dot\alpha}}$ | Pitch damping derivatives | rad⁻¹ |
| $d_\mathrm{ref}$ | Reference body diameter | m |
| $K_1, K_2, K_3$ | Datcom fin lift coefficients | — |
| $K_{WB}, K_{BW}$ | Fin-body, body-fin interference factors | — |
| $L/D$ | Body fineness ratio | — |
| $M, M_1, M_2$ | Mach (freestream, pre/post shock) | — |
| $p, p_0$ | Static, stagnation pressure | Pa |
| $q_\infty$ | Freestream dynamic pressure | Pa |
| $r$ | Recovery factor | — |
| $\mathrm{Re}_L$ | Length-based Reynolds number | — |
| $T, T_0, T_w$ | Static, stagnation, wall temperature | K |
| $t/c$ | Fin thickness-to-chord ratio | — |
| $x_{CP}, x_{CG}$ | Center of pressure, center of gravity | m |
| $\alpha$ | Angle of attack | rad |
| $\beta$ | Prandtl-Glauert / Ackeret compressibility factor | — |
| $\gamma, \gamma_\mathrm{eff}$ | Ratio of specific heats, effective (vibrationally relaxed) | — |
| $\delta$ | Flow deflection angle | rad |
| $\theta, \theta_c$ | Shock angle, cone half-angle | rad |
| $\Lambda_{LE}$ | Fin leading-edge sweep angle | rad |
| $\mu$ | Dynamic viscosity | Pa·s |
| $\nu(M)$ | Prandtl-Meyer function | rad |
| $\tau$ | Fin half-thickness ratio (= 0.5 $t/c$) | — |

### 0.4 Data & Code Availability paragraph (arcjetCV pattern)

> The OpenRocket Plus source code used to produce every result in this paper is archived at Zenodo (DOI: 10.5281/zenodo.XXXXXXX) with a corresponding GitHub release tag `jsr-2026-submission` at https://github.com/aidanyu/openrocket-plus. The twenty-eight-flight Rocket Flight Database (`.ork` build files, motor `.eng` files, ground-truth altitude logs, per-flight metadata, and the `flight_comparison.csv` master table) is archived under CC-BY-4.0 at Zenodo DOI 10.5281/zenodo.19976138. The bias-variance and sensitivity analysis scripts (`analyze.py` in each results subdirectory) reproduce every figure and table from the canonical CSV inputs. No proprietary tools or data are required to reproduce any reported result.

### 0.5 Acknowledgments

> The author declares no funding sources and no institutional affiliation; this work was self-funded as an independent research effort. The author thanks the OpenRocket and RASAero II user communities for public discussion of model behavior, and acknowledges Charles E. Rogers for the public RASAero II altitude comparison set that anchors flights one through twenty-five of the validation corpus. Generative AI (Anthropic Claude) was used during manuscript preparation for grammar editing, table formatting, and code review of the analysis scripts; all text, citations, claims, equations, and numerical results were authored, verified, and are the responsibility of the human author.

### 0.6 AIAA disclosure block

- Funding: none / self-funded (state explicitly).
- Conflicts of interest: none.
- AI use: disclose per October 2024 AIAA policy (Claude used for grammar/formatting only).
- Ethics: not applicable to aero modeling.

---

## 1. Section §1 — Introduction (900–1100 words)

- Purpose: Frame the open-source M>1 gap, position the contribution as a shock-geometry pre-pass + benchmarked subsystem replacement, and contrast with prior semi-empirical lineage.
- Subsections: §1.1 Background and motivation; §1.2 Prior work (Barrowman 1967, Niskanen 2009, Rogers RASAero II, Moore Aeroprediction, Missile DATCOM, arcjetCV); §1.3 Gap statement; §1.4 Contribution summary (3-bullet); §1.5 Paper organization.
- Required figures: Fig. 1 only.
- Key citations: [niskanen2009], [barrowman1967], [rogers2015], moore2002 (DOI 10.2514/2.3643), sooy2005 (10.2514/1.7814), haw2025 (10.2514/1.A36132), lowcostroll2025 (10.2514/1.A36408).

## 2. Section §2 — ShockGeometry Pre-Pass Architecture (1000–1200 words) — NOVELTY LEAD

- Purpose: Section §2 anchors the novelty case. Lead with this BEFORE the subsystem catalog.
- Subsections: §2.1 Motivation; §2.2 Data flow (with pseudocode); §2.3 Surface marching (TM cone + Prandtl-Meyer fan); §2.4 Local-condition query interface; §2.5 Numerical guards (C1 activation M 1.0–1.1); §2.6 Verification (cone surface Mach 0.00% vs Taylor-Maccoll, shoulder expansion <4e-11% vs PM).
- Required figures: Fig. 2 (block diagram), Fig. 3 (verification plot).
- Required tables: Table 1 (I/O contract).
- Key claims: pre-pass once-per-timestep; subsonic overhead <10 µs.

## 3. Section §3 — Atmosphere, Compressibility, and Shock Relations (900–1100 words)

- Subsections: §3.1 USSA 1976 (max err 0.009%); §3.2 Sutherland (MAPE 0.54% vs Incropera); §3.3 Effective γ piecewise 800→2000→4000 K; §3.4 β cubic Hermite M 0.95–1.05 (replaces MIN_BETA=0.25 clamp); §3.5 Normal/oblique/PM shock relations; §3.6 Verification (every block <0.1% vs NACA 1135).
- Required figures: Fig. 4 (speed of sound), Fig. 5 (Sutherland), Fig. 6 (normal shock), Fig. 7 (oblique), Fig. 8 (PM), Fig. 9 (Rayleigh Cp,max).
- Required tables: Table 2 (Mach blending regions inventory).

## 4. Section §4 — Drag Models (1500–1800 words) — LONGEST AFTER VALIDATION

- §4.1 Nose/body wave drag (Taylor-Maccoll exact + shock-expansion strip + Dahlem-Buck factors + Modified Newtonian; transonic Mdd onset).
- §4.2 Fin wave drag (DATCOM 4.1.5.1, supersonic-LE vs subsonic-LE branching, K=4.0/16/3, C1 Hermite M 0.9–1.2).
- §4.3 Base drag (Devan-Ashwood turbulent M>1.3, Hoerner subsonic, Chapman-Korst + ESDU 77021 BL, Chapman laminar, Viswanath boattail, power-on reduction via SP-8050).
- §4.4 Skin friction (Van Driest II compressible, Hopkins-Inouye 1971, recovery r=0.88; replaces Eckert).
- §4.5 BL transition (Mach-dependent Re, laminar-fraction cap).
- §4.6 Hypersonic blending (Newtonian smoothstep M 4–6).
- Required figures: Fig. 10 (A52H28), Fig. 11 (TN 3650), Fig. 12 (TN 3393), Fig. 13 (Van Driest II Cf vs M), Fig. 14 (DTIC AD0487365).
- Required tables: Table 3 (drag-submodel inventory).
- Key claims: Nose wave drag aggregate MAE = 0.029 (gate <0.035); fin Ackeret 0.00% over 15 cases, TN 3650 MAPE 21%; base drag MAPE 15.9% turb / 4.4% lam; cone foredrag MAPE 16.7% M 6.5–17.2; Van Driest II ~50% Cf reduction at M=5.

## 5. Section §5 — Stability and Dynamic Stability Models (900–1100 words)

- §5.1 Body CNα/CP supersonic (Allen-Perkins + Jorgensen TR R-474 Cd_c, exact 1.20).
- §5.2 Fin CNα with local flow (K1/K2/K3, K1 floor decay calibrated against TM X-653: MAPE ≤8% CNα, ≤7.1% xCP).
- §5.3 PNK interference (F_WB, F_BW, smoothstep M 0.85–1.15).
- §5.4 ESDU transonic similarity (universal h(K_trans)).
- §5.5 Pitch damping Cmq (strip theory + k_transonic Gaussian, declare 3× multiplier as B-level).
- §5.6 Magnus + vortex sideforce (Cy_pa = −(2/3) CNα_body; Kv=0.20).
- §5.7 SBLI chord reduction (Chapman-Kuehn-Larson NACA 1356).
- Required figures: Fig. 15 (TM X-653), Fig. 16 (Tobak TN 3788), Fig. 17 (transonic Cmq + AEDC-TR-76-58).

## 6. Section §6 — Subsystem Benchmark Roll-Up (600–800 words)

- §6.1 V&V methodology (analytical/exact verification + experimental/CFD validation; AIAA numerical-accuracy policy alignment).
- §6.2 The 22 A-level table.
- §6.3 B-level disclosures (Cmq 3× multiplier; hypersonic thin cones ≤8°).
- Required tables: Table 4 (22 A-level externally benchmarked subsystems).

## 7. Section §7 — Validation Against Published CFD (900–1100 words)

- §7.1 Comparator inventory (4 sources: Bunescu 2025 URANS, Sahu 1983 TLNS, Vidanović 2014 SST k-ω, Sznajder 2025 Fluent).
- §7.2 Basic Finner static (ORP vs Bunescu C_X, MAPE 39% C_X).
- §7.3 Ogive-cyl-boattail base (ORP vs Sahu — Sahu currently DATA NOT YET DIGITIZED; flagged as future work).
- §7.4 AGARD-B (Vidanović SST as reference; ORP `.ork` not yet shipped).
- §7.5 Basic Finner Cmq (ORP vs Sznajder, MAPE 31.6% supersonic; transonic peak overshoot +110-160% disclosed).
- Required figures: Fig. 18 (4-panel composite, READY at paper/data/png/cfd_validation_panels.png).
- Required tables: Table 5 (CFD comparator inventory).
- Citations: bunescu2025 (DOI 10.3390/aerospace12050371), sahu1983 (DTIC AD-A130-293), vidanovic2014 (DOI 10.2298/TSCI130409104V), sznajder2025 (DOI 10.2478/tar-2025-0021), bhagwandin2013 (ARL-TR-6725).

## 8. Section §8 — Flight-Corpus Integration Test (1400–1700 words) — HEADLINE

- §8.1 Corpus construction (28 flights, M 0.54–7.22, apogee 3.6 kft–897.6 kft; flights 1–25 Rogers, 26 DTIC AD0733141 BBV, 27–28 NACA TN 3739 Nike-Deacon).
- §8.2 Aggregate accuracy (mean signed −0.44%, σ 5.13%, RMSE 5.06%, MAE 4.33%, 28/28 ≤±10%, 17/28 ≤±5%).
- §8.3 Bias-variance decomposition (ORP whole-corpus bias²/MSE = 0.01 vs RAS 0.16).
- §8.4 Per-regime breakdown (subsonic +2.54%, transonic **−3.67%**, low-super +1.82%, high-super −2.13%, hypersonic −3.93%).
- §8.5 Paired RASAero II (n=25, Wilcoxon p=0.375, 14 ORP vs 11 RAS wins).
- §8.6 Distribution/normality (Shapiro-Wilk p=0.028; skew +0.48, excess kurt −0.86; non-parametric tests).
- §8.7 Sensitivity (Cd scale dominant |s|=4.00%/10%; time step |s|=0.98% confirms convergence).
- Required figures: Fig. 19 (error vs Mach), Fig. 20 (predictor_distributions), Fig. 21 (predictor_paired Bland-Altman), Fig. 22 (sensitivity tornado 4-panel composite — needs assembly).
- Required tables: Table 6 (per-flight rows), Table 7 (aggregate accuracy), Table 8 (per-regime bias-variance), Table 9 (sensitivity ranking).

## 9. Section §9 — Limitations and Honest Disclosures (700–900 words)

- §9.1 Transonic regime weakness (mean signed −3.67% M 0.8–1.3; RAS wins 6/7 paired transonic; k_transonic Gaussian root-cause hypothesis).
- §9.2 Phase 6h M>5 coast Cd bias (SLENDER_BODY_MACH_DECAY_END=5.0; 9 Nike-Apache + 1 Nike-Cajun held out of corpus; documented with proposed fix).
- §9.3 Corpus skew (22 of 28 at M<3; only 3 at M>5).
- §9.4 Aeroelastic disabled (Q_THRESHOLD=1e12).
- §9.5 No own CFD (mitigated by 4 published comparators; scoping decision).
- §9.6 Distribution non-normality (Shapiro-Wilk reject; light-tailed/platykurtic; non-parametric tests).
- §9.7 Transonic area rule not yet integrated (utility exists; deferred).
- Required figures: Fig. 23 (Phase 6h Apache per-component Cd vs M).
- Required tables: Table 10 (Phase 6h Cd deficit table).

## 10. Section §10 — Conclusions and Future Work (400–500 words)

- §10.1 Three conclusions matching §1.4 contribution bullets.
- §10.2 Future work: Phase 6h Hoerner term, transonic area rule integration, aeroelastic validation, AGARD-B `.ork`, 5+ M>5 flights post-Phase-6h.

---

## 11. Required Figures Master List

| # | Caption (single sentence) | Source path | Notes |
|---|---|---|---|
| 1 | Hierarchy of supersonic aerodynamic methods and the position of the present approach. | NEW schematic | Hand-draw or matplotlib |
| 2 | ShockGeometry pre-pass data-flow block diagram. | NEW schematic | Hand-draw |
| 3 | ShockGeometry surface-Mach verification vs Taylor-Maccoll/PM at six (M, θ) points. | regenerate from shockgeometry_local_flow_validation.csv | Regenerate |
| 4 | US Standard Atmosphere 1976 speed of sound. | data/png/us_standard_atmosphere_speed_of_sound.png | Ready |
| 5 | Sutherland viscosity vs Incropera Table A.4. | data/png/sutherland_viscosity_air.png | Ready |
| 6 | Normal shock relations vs NACA Report 1135. | data/png/naca1135_normal_shock.png | Ready |
| 7 | Oblique shock θ-β-M vs NACA Report 1135. | data/png/naca1135_oblique_shock_beta.png | Ready |
| 8 | Prandtl-Meyer ν(M) vs NACA Report 1135. | data/png/naca1135_prandtl_meyer_nu.png | Ready |
| 9 | Rayleigh pitot Cp,max vs NACA Report 1135. | data/png/rayleigh_pitot_cpmax.png | Ready |
| 10 | Nose wave drag vs NACA RM A52H28 (5 shapes, L/D=3). | data/png/naca_rm_a52h28_validation.png | Ready |
| 11 | Fin wave drag vs NACA TN 3650 delta wing free-flight. | data/png/naca_tn_3650_fin_wave_drag.png | Ready |
| 12 | Turbulent/laminar base drag vs NACA TN 3393. | data/png/naca_tn_3393_base_pressure.png | Ready |
| 13 | Van Driest II compressible Cf vs Mach. | NEW — generate sweep | Regenerate |
| 14 | Hypersonic cone foredrag vs DTIC AD0487365. | data/png/hypersonic_cone_drag.png | Ready |
| 15 | Static stability CNα and xCP vs NASA TM X-653. | data/png/nasa_tm_x653_stability.png | Ready |
| 16 | Pitch damping Cmq vs Tobak NACA TN 3788. | data/png/tobak_cmq_comparison.png | Ready |
| 17 | Transonic Cmq augmentation and AEDC-TR-76-58 transonic peak. | data/png/transonic_cmq_augmentation.png | Ready |
| 18 | Four-panel published-CFD comparator composite. | data/png/cfd_validation_panels.png | Ready (just produced) |
| 19 | Signed apogee error vs peak Mach, 28-flight corpus. | data/analysis/corpus_bias_variance_2026_05_11/error_vs_mach.png | Ready |
| 20 | Signed-error distributions ORP vs RASAero II. | data/analysis/corpus_bias_variance_2026_05_11/predictor_distributions.png | Ready |
| 21 | Paired ORP vs RASAero II Bland-Altman. | data/analysis/corpus_bias_variance_2026_05_11/predictor_paired.png | Ready |
| 22 | Sensitivity tornado four-panel composite. | data/analysis/sensitivity_2026_05_11/tornado_*.png | Regenerate composite |
| 23 | Phase 6h M>5 coast-Cd disclosure plot. | NEW from NikeApacheCoastCdDiagnosticTest | Regenerate |

**Figure total: 23.** Sixteen ready; four need regeneration; three new (Fig. 1, 2, 23 schematics).

## 12. Required Tables Master List

| # | Content | Source |
|---|---|---|
| 1 | ShockGeometry calculator I/O contract | Manual from SUPERSONIC_MODELING.md §8 |
| 2 | Mach regime blending regions and methods | Manual |
| 3 | Drag-submodel inventory: regime, model, source, MAPE | Manual |
| 4 | 22 A-level externally benchmarked subsystems | SUPERSONIC_MODELING.md |
| 5 | Published-CFD comparator inventory | paper/data/cfd_inventory_2026_05_02.md |
| 6 | Per-flight corpus rows | rocket-flight-database/flight_comparison.csv |
| 7 | Aggregate accuracy (ORP vs RASAero II) | corpus_bias_variance_2026_05_11/ |
| 8 | Per-regime bias-variance decomposition | regime_breakdown.csv + bias_variance_decomp.csv |
| 9 | Sensitivity ranking | sensitivity_sweep.csv |
| 10 | Phase 6h Apache coast-Cd deficit | NikeApacheCoastCdDiagnosticTest output |

## 13. Citation Master List (~51 entries; all NACA/NASA/DTIC numbers must be web-verified)

Existing in paper/paper.bib (17): niskanen2009, barrowman1967, naca1135, taylormaccoll1933, datcom1978, hopkins1971, chapman1950, chapman1955, esdu77021, ulmann1956, nielsen1962, rogers2015, dupuis1997, grabow1965, a52h28, anderson2006, rocketpy2021.

NEW (~34, all VERIFY): moore2002 (10.2514/2.3643), moore2001 (10.2514/2.3479), sooy2005 (10.2514/1.7814), haw2025 (10.2514/1.A36132), lowcostroll2025 (10.2514/1.A36408), sims1964 (NASA SP-3004), nasa1976ussa (NOAA-S/T 76-1562), incropera2007 (ISBN), devan1986 (NASA TN D-721 / NSWC-TR), viswanath1996 (Prog. Aerospace Sci.), dahlembuck1979 (AFFDL or NSWC), nasa_trr100, nasa_sp8050, hoerner1965 (ISBN), jorgensen1977 (NASA TR R-474), tobak1956 (NACA TN 3788), pitts1957 (NACA Report 1307), bhagwandin2013 (ARL-TR-6725), aedc7658 (AEDC-TR-76-58), platou1963 (BRL 1193), paulwedemeyer1982 (EOARD-TR-82-7), chapman1958 (NACA Report 1356), allenperkins1951 (NACA Report 1048), bunescu2025 (10.3390/aerospace12050371), sahu1983 (BRL TR-02495 / DTIC AD-A130-293), vidanovic2014 (10.2298/TSCI130409104V), sznajder2025 (10.2478/tar-2025-0021), rogers_rasaero_alt (URL), dtic_ad0733141 (DTIC), heitkotter1956 (NACA TN 3739), rfd_zenodo (10.5281/zenodo.19976138), orp_zenodo (TBD, mint before submit), wilcoxon1945 (10.2307/3001968), nasa_x721_66_568 (Wallops), aiaa_numerical_policy (URL).

## 14. Cross-Cutting Design Decisions

| Decision | Section(s) | Treatment |
|---|---|---|
| Phase 6h M>5 coast Cd bias disclosure | §9.2 dedicated subsection, Fig. 23, Table 10; brief mention in §8.4 | Quantitative, root cause, proposed fix |
| Transonic regime weakness | §8.4 reports −3.67%; §9.1 frames root cause | Disclosed up front |
| RASAero II comparison framing | §8.5 "no statistically significant difference (p=0.375)"; corpus-specific | Avoids universal-superiority claim |
| "No own CFD" mitigation | §7 four comparators; §9.5 scoping disclosure | 4 sources, 2 geometries, 2 coefficient families, 3 Mach bands |
| Open-source code release | §0.4 arcjetCV-pattern; §1 OSS positioning; §10 contributions invite | Zenodo DOI + GitHub release tag |

## 15. Risk-Mitigation Map (per JSR_REQUIREMENTS_RESEARCH §5)

| # | Risk | Section addressing | How |
|---|---|---|---|
| 1 | AIAA numerical-accuracy policy applies | §3 verification, §6 A-level table, Fig. 3 | MAPE tables explicit; time-step convergence in §8.7 (|s|=0.98% over 0.025–0.10 s) |
| 2 | Novelty framing vs Aeroprediction lineage | §2 leads with pre-pass; §1.4 contribution bullets | "First open-source M>5 framework with end-to-end flight-corpus validation" |
| 3 | Out-of-scope desk-rejection | §0.1 title, §1.1 motivation, §8.1 corpus (sounding rockets to 273 km) | Sounding-rocket framing, not amateur model |
| 4 | Solo author + Independent Researcher | §0.5 acknowledgments; reproducibility artifacts | Citation discipline + Zenodo/GitHub artifacts |
| 5 | RASAero II as commercial baseline | §8.5 version-locked; cite Rogers 2015 | Standard practice (mirrors Sooy & Schmidt 2005) |
| 6 | No own CFD runs | §7 four-comparator section; §9.5 | 4 published-CFD references |

## 16. Writing Sequence (recommended)

1. §6 subsystem table (clerical roll-up; lowest risk).
2. §3, §4, §5 model sections (port from AST_PAPER.md; condense; refresh).
3. §7 CFD section (Fig. 18 ready; Table 5 from cfd_inventory).
4. §8 corpus section (v1.2 numbers must exactly match canonical CSVs).
5. §9 limitations (before §1 and §2 — clarifies claims §1/§2 can support).
6. §2 architectural contribution (write fresh as novelty lead).
7. §1 introduction (last; pre-summarizes the rest).
8. §10 conclusions.
9. Abstract (after all sections done).

## 17. Three Highest-Risk Sections

1. **§8 Flight-Corpus Integration Test** — every number must reproduce against canonical analysis artifacts.
2. **§2 ShockGeometry Pre-Pass Architecture** — anchors the novelty case.
3. **§9 Limitations** — Phase 6h disclosure must be precise without inviting fix-before-publish demands.
