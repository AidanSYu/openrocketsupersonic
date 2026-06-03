# CANONICAL FACTS — single source of truth for ALL 5 papers

**Every number, claim, and framing in all five manuscripts MUST agree with this file.**
Cross-paper inconsistency (e.g. one paper says "28 flights, −0.44%" and another says
"25 flights, −0.38%") is the single largest rejection vector for a multi-paper family —
reviewers cross-check companion papers. If a source draft (`paper.md`, `AST_PAPER.md`,
`Thesis/PART_*.md`, `VALIDATION_MATRIX.md`) disagrees with this file, **this file wins**
and the source must be reconciled to it.

Provenance: regenerated from `rocket-flight-database/flight_comparison.csv` (published RFD
v1.2, model column = current archived code) via `paper/data/analysis/*/analyze.py` +
`uncertainty_quantification.py`, and `paper/1_research_jsr/_DRAFTING_BRIEF.md` (the JSR
flagship brief, already through two adversarial review rounds). The JSR paper
(`1_research_jsr/`) is the reconciled reference implementation of these facts.

---

## A. HEADLINE CORPUS (authoritative)

**25-flight external corpus**, Mach 0.54–4.33 (Rogers' public RASAero II altitude-comparison
set: **23 single-stage + 2 two-stage (AeroPac 104K at M3.04 and MESOS 293K at M4.33)**).
Externally selected (not outcome-curated by us) → the accuracy statistics are an honest,
outcome-independent validation result.

> ⚠️ STAGE-COUNT FIX (cross-paper audit): the corpus is **NOT** "24 single-stage + MESOS." Per
> `rocket-flight-database/flight_comparison.csv`, flight_id 22 is "AeroPac 104K Two-Stage"
> (motor `N1048 / M685W`, diameters `4.05/3.08`, M3.04) and flight_id 25 is "MESOS 293K"
> (M4.33). So the 25-flight corpus = **23 single-stage + 2 two-stage**. Any paper saying
> "24 single-stage + MESOS" is WRONG and must be reconciled to this framing.

| Predictor | N | Mean signed | σ | RMSE | MAE | ≤±5% | ≤±10% |
|---|---|---|---|---|---|---|---|
| OpenRocket-Plus | 25 | **−0.38%** | 5.44% | 5.34% | 4.74% | 14/25 | **25/25** |
| RASAero II | 25 | +2.46% | 5.81% | 6.20% | 5.34% | 13/25 | 22/25 |

- 95% bootstrap CIs (20000 resamples, seed 0x51A7EA): mean **[−2.41, +1.72]**; σ [4.20, 6.25];
  RMSE [4.38, 6.19]; MAE [3.78, 5.70]; ≤±10% 100% [100,100].
- Mean-error CI brackets zero → predictor is **statistically unbiased** on this corpus.
- Bias²/MSE: ORP **0.01**, RAS 0.16 → ORP residual is essentially pure variance.

### Paired ORP vs RASAero II (n=25) — claim is PARITY, NOT superiority
- Mean |ORP|−|RAS| = **−0.60 pp**, 95% bootstrap CI **[−2.16, +0.96]** (straddles 0).
- Wilcoxon signed-rank on |error|: **W=143.0, p=0.615** → no significant difference.
- Bland–Altman: ±14.3% limits of agreement, mean offset −2.84%.
- **HONEST CLAIM = parity** with this version-locked RASAero set. RASAero values are Rogers'
  *recorded* predictions (not fresh independently-rerun pre-flight cases) — disclose this.
- ⚠️ FORBIDDEN FRAMING: "lower aggregate error than RASAero", "outperforms", "more accurate
  than RASAero", "beats". These appear in the stale `paper.md`/`AST_PAPER.md` and MUST be removed.

### Per-regime (25-flight; ORP)
| Regime | Mach | N | Bias | σ | RMSE | MAE | ≤5% | ≤10% |
|---|---|---|---|---|---|---|---|---|
| Subsonic | <0.8 | 9 | +2.54% | 4.37 | 4.83 | 4.30 | 7 | 9 |
| Transonic | 0.8–1.3 | 7 | **−3.66%** | 5.36 | 6.17 | 5.84 | 2 | 7 |
| Low supersonic | 1.3–3.0 | 5 | +1.83% | 5.70 | 5.42 | 4.61 | 3 | 5 |
| High supersonic | 3.0–5.0 | 4 | −3.98% | 2.97 | 4.73 | 3.97 | 2 | 4 |
| Hypersonic | >5.0 | **0** | — | — | — | — | — | — |

- Transonic (−3.66%) is the disclosed regime weakness (only regime where RASAero is more accurate).
- Hypersonic regime in the headline is **EMPTY** (max Mach 4.33). Above M3 is descriptive only (n≤5), no inferential claim.

### Distribution / normality (ORP, n=25)
Shapiro–Wilk W=0.905, **p=0.023** (reject normality at α=0.05); Anderson–Darling A²=0.922
(crit 0.728); skew +0.43, excess kurtosis −1.14 (light-tailed/platykurtic, NOT heavy-tailed;
max |err| = 8.7%). → primary test is non-parametric Wilcoxon; CIs are bootstrap (not normal-theory).

---

## B. IN-SAMPLE DISCLOSURE + GENERALIZATION (decontaminated holdout)
Two base-drag scale constants are B-level / corpus-frozen: `THICK_BL_K=2.2` (anchor: Raven) and
`SLENDER_BODY_K=0.0025` (anchors: Raven/Rabia/Kinsel; source diagnostic also used Torrent). So the
headline is **partly in-sample** — disclose plainly.

**Decontaminated prospective holdout** (every flight any constant touched — Raven, Rabia, Rabia
Short Fin Can, Kinsel, Torrent — placed in DEV):
- DEV (n=13): mean +0.22%, **MAE 5.47%**
- HOLDOUT (n=12, genuinely blind): mean −1.03%, **MAE 3.95%**
- Holdout MORE accurate than dev → the two constants **generalize, not overfit**. This is the PRIMARY in-sample defense.

---

## C. MECHANISM ABLATION (re-run in isolation; valid)
Effect of disabling each mechanism on corpus apogee error (mean |Δ| over the ablation set =
**24 of the 25 corpus flights (MESOS 293K excluded; 23 single-stage plus the AeroPac 104K
two-stage closure)**):

> ⚠️ ABLATION-SET FIX (cross-paper audit): the ablation set is **NOT** "24 single-stage." It is
> the 25-flight corpus minus MESOS 293K, and those 24 flights INCLUDE the AeroPac 104K two-stage
> flight (flight_id 22). Correct framing everywhere = **"24 of the 25 corpus flights (MESOS 293K
> excluded; 23 single-stage plus the AeroPac 104K two-stage closure)."**
| Mechanism | mean \|Δ\| | max \|Δ\| (flight) | Note |
|---|---|---|---|
| Finned-base augmentation (FINNED_BASE_K, EXTERNAL/Basic-Finner) | **8.10 pp** | 39.5 (Kinsel M2.19) | **dominant apogee driver** |
| Van Driest II skin friction | 0.87 pp | 7.9 (Qu8k M3.46) | matters at high Mach |
| DATCOM 4.1.5.1 fin wave drag | 0.39 pp | 1.9 (Proteus) | modest |
| ShockGeometry pre-pass | **0.15 pp** | 3.6 (FMJ BR6 M2.46) | inert subsonically |
| PNK interference / K1 floor | 0.00 | 0.00 | no apogee effect |

**HONEST pre-pass framing:** pre-pass moves *integrated apogee* by only 0.15pp mean — apogee
integrates a trajectory dominated by lower-Mach drag and the pre-pass is inert below M1. Its value
is **local-flow fidelity** (correct post-shock conditions for fin loads/stability, verified
bit-for-bit vs Taylor–Maccoll) and as the **architectural seam** enabling downstream supersonic
models — NOT a gross-apogee win. The dominant apogee mechanism is the externally-calibrated
**finned-base augmentation** — this is the hook for Paper 5.

---

## D. COMPONENT BENCHMARKS (authoritative — corrected values)
| Subsystem | Reference (bib key) | Metric | Value | Status |
|---|---|---|---|---|
| Speed of sound | US Std Atm 1976 | max err | 0.016% | A |
| Sutherland viscosity | NIST/Incropera | MAPE vs reference data | **0.54%** (formula self-consistency 0.012%; NIST gate <3%) | A |
| Normal shock | NACA 1135 (naca1135) | max err | <0.01% | A |
| Oblique shock θ-β-M | NACA 1135 (naca1135) | max angle err | 0.021% | A |
| Prandtl–Meyer | NACA 1135 (naca1135) | max abs angle err | 0.004° | A |
| Taylor–Maccoll cone | published cone tables (taylormaccoll1933) | max shock-angle rel err | 0.825% (gate 1%) | A |
| Cp,max / Rayleigh pitot | NACA 1135 | 15 pts | exact | A |
| ShockGeometry pre-pass | analytic | cone / shoulder err | 0% / 4e−11% | A |
| Nose/body wave drag (5 shapes) | NACA RM A52H28 (a52h28) | MAE | **0.029** (gate 0.035) | A |
| Base drag, turbulent | NACA TN 3393 (chapman1955) + Hart L52E06 | MAPE | **15.9%** (Hart 4.0%) | A |
| Base drag, laminar | Chapman (chapman1955) | MAPE | **4.4%** | A |
| Fin wave drag (DATCOM 4.1.5.1) | NACA TN 3650 (ulmann1956) | MAPE | **21%** (Ackeret check 0.00%) | A |
| Compressible skin friction | Van Driest II (hopkins1971) + NASA TN D-5089 | self-consistent | ✓ | A |
| Hypersonic cone foredrag | DTIC AD0487365 (grabow1965) | MAPE | **19.7%** (11 pts M6.5–17.2, max +57.0%) | A |
| Static stability CNα | NASA TM X-653 (nielsen1962) | MAPE | **6.84%** (10 pts M0.6–5.82) | A |
| Static stability xCP/d | NASA TM X-653 (nielsen1962) | MAPE | **7.11%** | A |
| **Basic Finner total drag** | **ADA636861 (dupuis1997)** | **MAPE** | **11.8%** (8 pts M1.08–4.30) | A |

### A-LEVEL COUNT — RECONCILED DECISION (cross-paper audit #2)
**aLevelDecision: the canonical A-level headline count is TWENTY (20).** This is the reconciled
count that ALL papers must use. Provenance and reconciliation:

- **Paper 1 (`1_research_jsr/sections/06_benchmarks.tex`)** is the reconciled reference: its
  benchmark table `tab:benchmarks` has exactly **20 numbered A-level rows**, the prose says
  "Twenty subsystems currently meet the A-level standard," the caption calls them "Representative
  externally benchmarked A-level subsystems," and the abstract (`jsr_paper.tex`) says "twenty
  component models." Paper 1 deliberately **demoted three rows** that older docs counted as A:
  (i) hypersonic cone foredrag → B-level/exploratory (thin-cone limitation, 19.7% MAPE);
  (ii) AGARD-B total drag → qualitative secondary (≈22.6% MAPE, "loose qualitative closure");
  (iii) vortex sideforce Kv=0.20 → internally-calibrated (no verifiable external anchor).
- **The "27" in `paper/data/VALIDATION_MATRIX.md` and `paper/Thesis/PART_A.md` is STALE** and
  must be revised. It is not even internally consistent: the matrix's own claim map lists only
  **23** rows at Status A (8 foundations + 7 drag + 7 stability + 1 vehicle), and it still counts
  the three rows Paper 1 demoted (cone foredrag, AGARD-B, Kv). The "27" headline has no defensible
  derivation that survives those three demotions.
- **Reconciled framing (the single defensible wording both papers use):** there are **twenty (20)
  externally benchmarked A-level subsystems** (the Table `tab:benchmarks` population), **plus one
  externally-anchored *negative*/exclusion benchmark** (NACA RM-10, MAPE 80%, used to bound out a
  geometry family, counted OUTSIDE the 20). Cone foredrag, AGARD-B, and vortex Kv are reported at
  their honest evidentiary level (B-level / qualitative-secondary / internally-calibrated) and are
  NOT in the A-level count. Paper 4 (`PART_A.md`) and `VALIDATION_MATRIX.md` must be revised from
  "27" to "20" (and the AST-gate item-1 line "27 clean A-level rows" likewise → "20").
- Curated-subset note: Paper 1's table is *both* the curated representative set *and* the full
  reconciled A-level population — it is not a sample of a larger 27; the 27 was an over-count.
  Do NOT invent a "20-of-27" mapping (no such mapping exists).

- ⚠️ STALE VALUES TO FIX: Basic Finner is **11.8%** NOT 11.9%/22.7%; cone is **19.7%** NOT 16.7%/17.6%.
- **DOWNGRADED out of A-level** (to qualitative/secondary): AGARD-B total-drag (its own report calls
  it "loose qualitative trend closure", MAPE ~22%); Vortex-sideforce Kv=0.20 (range-check, source
  unverifiable → present as internally-calibrated coefficient, no literature anchor); hypersonic
  cone foredrag (exploratory B-level, thin-cone limitation). These three demotions are WHY the
  count is 20, not 23/27.

### CFD comparators (Paper 1 §7; all B-level, published third-party — we ran NO own CFD)
- Bunescu URANS (bunescu2025), Basic Finner CX, M0.4–3.5: correct trend, loose absolute (MAPE ~43%, qualitative).
- Sahu thin-layer NS (sahu1983), ogive-cyl-boattail base drag, M0.9–1.2: digitization deferred (memo only).
- Vidanović SST k-ω (vidanovic2014), AGARD-B, M0.596/1.602: reference dataset, no AGARD-B build shipped (qualitative, ~22%).
- Sznajder Fluent (sznajder2025), Basic Finner Cmq+Cmα̇, M0.9–5.0: supersonic MAPE **31.6%** (n=8, M≥1.29), sign/trend correct; transonic overshoot **+110 to +160%** at M1.08–1.11.
- Bhagwandin & Sahu CFD++ (bhagwandin2013), AFF/ANF finners: AFF **19.0%** (n=5, M1.30–2.50), ANF **28.0%** (n=8, M1.29–4.50) — corroborating second source.

---

## E. DYNAMIC STABILITY / PITCH DAMPING — explicitly B-level (honest weakness)
- Transonic Cmq 3× multiplier + Gaussian peak: overshoots reference by **+110 to +160%** (Sznajder).
- Basic Finner Cmq MAPE 69%; sign correct, supersonic under-prediction.
- Held at **B-level**; affects predicted dynamic stability/coning, NOT apogee statistics (apogee insensitive to Cmq).
- Do NOT build a headline research claim on Cmq — it is a disclosed limitation.

---

## F. MESOS 293K — THE STANDING TWO-STAGE HIGH-MACH RESULT (the honest position)
- Current archived code predicts MESOS 293K at **−6.96% (273,056 ft)** against the measured 293,488 ft.
  This is the **standing, correct model prediction** and the value used THROUGHOUT all papers.
- It is the **largest single-flight error in the corpus** and the highest-Mach case (the only high-Mach
  two-stage closure); it bounds the framework's accuracy at the top of the validated envelope, where
  staging, coast-phase aerodynamics, and Mach-4 base drag compound.
- The −6.96% **reproduces in isolation** from the archived code release (genuine current-code value).
- ⚠️ There is **NO "regression" and NO earlier validated value.** Earlier drafts and database snapshots
  reported −0.6% / −0.64% (291,601 ft) for this flight; that figure was **erroneous (a drafting artifact),
  has no defensible derivation, and is WITHDRAWN.** There is no defensible path back to it. Do NOT describe
  −6.96% as a regression from −0.6%, do NOT say the cause is "under investigation," do NOT promise to
  "re-sync/recover" a prior value, and do NOT cite the "JUnit parallel-execution contamination" story
  (also refuted). −6.96% simply IS the model's prediction for this flight.
- The published Rocket Flight Database (version of record) carries the −6.96% value in its model column;
  an earlier deposited version (v1.2) that carried the erroneous figure is superseded.
- −6.96% is within the ±10% admission band, so the 25/25 headline is unchanged.

---

## G. EXPLORATORY HIGH-MACH (Paper 1 §9 — full set, NO cherry-picking)
~20 historical sounding-rocket flights run as an EXPLORATORY capability demonstration (NOT a headline):
- **3 within ±10%**: Black Brant V VB (M7.224, −6.97%), Nike-Deacon no.1 (M4.956, −1.06%), Nike-Deacon no.2 (M5.079, −0.89%).
- **17 outside**: Nike-Apache family +24…+36%, Nike-Cajun +16.6%, Arcas blunt/secant −29…−69%, HEROS 3 −63.4%; plus a couple sim-error/zero-apogee cases reported transparently.
- ⚠️ FORBIDDEN: presenting these as a "28/28 within ±10%" headline (outcome-based selection — the
  single biggest integrity flag). FRAME: the method *reaches* Mach 7 within ±7% on well-characterized
  vehicles, but motor/geometry reconstruction uncertainty dominates on poorly-documented historical flights.

---

## H. CITATION RULES (hard)
- ⚠️ **DROP the fabricated "Devan-Ashwood" / "NASA TN D-721" base-drag citation entirely.** It appears
  in stale `AST_PAPER.md` ("Devan-Ashwood/Chapman/Viswanath base drag"). The supersonic base-drag
  correlation `Cd_base = 0.064 + 0.186/M²` is presented as an empirical correlation **validated against
  NACA TN 3393 (chapman1955) and consistent with ESDU 77021 (esdu77021)**. No Devan-Ashwood.
- Vortex Kv=0.20: NO valid literature source → present as an internally-calibrated coefficient (do NOT cite paulwedemeyer).
- Prior-art set: niskanen2009 (OpenRocket), rocketpy2021, barrowman1967, rogers2015 (RASAero II),
  sooy2005 + mooremcinvillehymer2002 + moore2001 (Missile DATCOM/Aeroprediction lineage),
  quintart2025 + lowcostroll2025 (JSR open-source / amateur-supersonic precedent).
- Van Driest II Eq must match the code form (Fc = r·m/(arcsin α + arcsin β)², numerator r·m = T_aw/Te−1; r=0.88). Cite hopkins1971 + NASA TN D-6945.

---

## I. IDENTIFIERS
- RFD dataset: Zenodo DOI **10.5281/zenodo.19976138** (v1.2), CC-BY-4.0; mirror https://github.com/AidanSYu/rocket-flight-database
- Code: https://github.com/AidanSYu/openrocketsupersonic ; archival code Zenodo DOI + release tag = **mint at submission (BLOCKED on user push)**.
- Author: Aidan Yu, ORCID 0009-0005-9589-5314, Independent Researcher (acknowledges Duke University support). Email asy22@duke.edu.
- AI disclosure required per AIAA Oct-2024 policy (and good practice in all venues): generative AI used for language editing / formatting / code review only; all claims/equations/numbers authored & verified by the human author; no AI authorship.
- License: code GPL-3.0; data CC-BY-4.0.

---

## J. THE FIVE PAPERS — SCOPE BOUNDARIES (anti-salami)
A reviewer's first question for a 5-paper family is "is this least-publishable-unit slicing?" The
defense is that the five are **different genres / different research questions**, each citing the others.

| # | Paper | Genre | Outlet (provisional) | Headline question / scope | MUST NOT |
|---|---|---|---|---|---|
| 1 | Research flagship | Peer-reviewed research article | **J. Spacecraft & Rockets (AIAA)** | "Does the integrated open-source simulator predict supersonic apogee accurately, and how does it compare to the commercial baseline?" → 25-flight parity result + pre-pass architecture. | — |
| 2 | Software | Software paper (describes the artifact, not new science) | **JOSS** (Journal of Open Source Software) | "Here is the open-source tool: architecture, install, use, tests." Cites Paper 1 for the science. | Re-derive or re-argue the research result; JOSS is about the software. |
| 3 | Data | Data article (companion to a parent research article) | **Data in Brief (Elsevier)** | "Here is the Rocket Flight Database: schema, provenance, reuse value." Cites Paper 1 as parent. | Draw new scientific conclusions; DiB describes data, not analysis. |
| 4 | Technical report | Self-archived monograph (NOT peer-reviewed) | **Zenodo** | The complete technical documentation / derivations / full validation. The superset; the others are distillations. | — (no salami risk: not a journal) |
| 5 | Base-drag mechanism-attribution | Peer-reviewed research article — **distinct research question** | **LOCKED (user decision 2026-06): ASCE *Journal of Aerospace Engineering* (free / no APC, Q2, genre-fits engineering-method validation).** AST was NO_GO as drafted (needs a new per-closure apogee-swap experiment + irreducible CFD-genre risk); ASCE JAE is the defensible "won't-get-rejected" home for the delivered content. Title/scope reframed to mechanism-attribution + external base-pressure benchmarks (NOT a per-closure intercomparison, which we are not running). | "Which base-drag closure governs integrated supersonic-rocket apogee?" An intercomparison of base-drag models (Chapman–Korst, Chapman laminar, ESDU 77021, 0.064+0.186/M², Viswanath boattail, finned-base augmentation) anchored on the ablation finding that base drag dominates the apogee error budget (8.10pp vs 0.15pp pre-pass). **DEFENSIBILITY MANDATE for AST's bar:** lead with the externally-validated base-pressure benchmarks (TN 3393, Hart, Chapman, Sahu) as the substantive physics; use the ablation as the motivating finding; handle the in-sample `FINNED_BASE_K` head-on with the decontaminated holdout (neutralizes the circularity critique). | Repeat Paper 1's integrated-parity headline; let the in-sample constant read as the "result". This must stand on the base-drag mechanism/closure question alone. **If the workflow judges this too thin / too overlapping with Paper 1 / too circular for AST, it must say so in the verdict and recommend the fallback (honest NO-GO over a forced GO).** |

**Cross-citation rule:** each paper cites the others where relevant (Paper 1 ↔ data DOI, software repo;
Paper 5 cites Paper 1 for the integrated context then diverges). Companion-paper relationships (1↔2↔3↔4)
are standard and NOT salami. The only genuine salami risk is 1↔5 — Paper 5 lives or dies on being a
genuinely distinct research question.

---

## K. HONEST-FRAMING CHECKLIST (apply to every paper)
1. Parity with RASAero, never superiority.
2. Supersonic-validated to M4.33; hypersonic = exploratory capability, never a headline validation claim.
3. High-Mach set reported in FULL (3 pass / 17 fail), never cherry-picked to "28/28".
4. Two base-drag constants disclosed as partly in-sample; generalization shown via decontaminated holdout.
5. Pre-pass = architecture + local-flow fidelity (0.15pp apogee), not a gross-apogee win.
6. MESOS −6.96% (273,056 ft) is the standing prediction for the hardest two-stage high-Mach case and the corpus's largest single-flight error — NOT a regression; the earlier −0.6%/291,601 ft figure is withdrawn (no contamination story).
7. No own CFD — four published comparators + corroborating fifth.
8. Cmq pitch-damping is B-level (+110–160% overshoot) — a limitation, never a headline.
9. No fabricated citations (no Devan-Ashwood; no paulwedemeyer for Kv).
10. Measurement floor ~1% in heterogeneous ground truth is NOT subtracted (conservative reporting).
