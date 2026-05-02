# Pitch-Damping Closure Opportunities — Claim-Boundary Memo

## Prompt 17 deliverable (READ-ONLY research, no code changes)

## Executive Summary

**Status update:** the Cmq column from ADA636861 Table VII has since been digitized into `paper/data/csv/ada636861_basic_finner_cmq.csv` and consumed by `BasicFinnerCmqBenchmarkTest`. The remaining closure problem is no longer data absence; it is the documented mismatch between the current damping model and the digitized free-flight Cmq values.

---

## Question 1: Can existing repo data tighten the B-level damping claims?

**Yes — partially.** ADA636861 (DREV aeroballistic range report for the Basic Finner) contains direct finned-vehicle Cmq data, now digitized and regression-tested.

### What exists in the repo

| Source | File | Contains Cmq? | Geometry | Status |
|--------|------|---------------|----------|--------|
| ADA636861 PDF | `paper/data/pdf/ADA636861.pdf` | **YES** — Table VII (per-shot), Table VIII (multi-fit) | Basic Finner (finned vehicle) | Digitized into `paper/data/csv/ada636861_basic_finner_cmq.csv`; benchmarked by `BasicFinnerCmqBenchmarkTest` |
| ADA636861 CSV | `paper/data/csv/ADA636861_basic_finner_cx0.csv` | NO — only CX0, CNa, CMa | Basic Finner | Incomplete extraction |
| Tobak TN 3788 | `paper/data/csv/naca_tn_3788_cone_stability_derivatives.csv` | CNq/CNa_dot for cones | 10° and 20° cones (body-only) | Digitized, A-level for cone theory |
| AEDC-TR-76-58 | `paper/data/csv/aedc_tr_76_58_roll_damping.csv` | NO — roll damping Clp only | Generic | Header explicitly states no Cmq |
| BRL R-1216 | `paper/data/pdf/` (if present) | Theoretical framework only | N/A | Not a data source |
| DynamicStabilityBenchmarkTest | test code | Self-consistency only | Benchmark geometry | A-level for internal consistency |

### ADA636861 Table VII Cmq data (now represented in CSV)

These are per-shot values from 6-DOF trajectory reduction of free-flight aeroballistic range firings:

| Mach | Cmq | Shot context |
|------|-----|-------------|
| 1.056 | -319.4 | Near-transonic |
| 1.057 | -333.3 | Near-transonic |
| 1.116 | -289.2 | Low supersonic |
| 1.254 | -416.1 | Supersonic |
| 1.332 | -475.1 | Supersonic (highest absolute Cmq) |
| 1.799 | -331.7 | Supersonic |
| 1.846 | -277.2 | Supersonic |
| 1.850 | -330.3 | Supersonic |
| 2.348 | -338.2 | Supersonic |
| 2.364 | -360.2 | Supersonic |
| 2.414 | -278.8 | Supersonic |
| 2.663 | -307.3 | Supersonic |
| 2.741 | -242.1 | Supersonic |
| 2.969 | -319.3 | Supersonic |
| 2.970 | -250.8 | Supersonic |
| 3.312 | -263.6 | Supersonic |
| 3.337 | -205.7 | Supersonic |
| 3.681 | -209.0 | Supersonic |
| 3.741 | -244.4 | Supersonic |
| 3.774 | -228.2 | Supersonic |
| 4.127 | -195.3 | Supersonic |
| 4.422 | -261.0 | High supersonic |
| 4.471 | -214.1 | High supersonic |

**Key observation:** The near-transonic shots (M 1.05–1.33) show absolute Cmq = 289–475, significantly higher than the M 2–4 range (195–360). This is qualitatively consistent with ORP's transonic Cmq augmentation, which predicts elevated absolute Cmq near M = 1.0.

---

## Question 2: What exact data type is still missing?

### For the `3x` multiplier (B-level → potential A-level)

**Current status:** A direct comparison of ORP's total Cmq against the digitized ADA636861 Cmq now exists in `BasicFinnerCmqBenchmarkTest`.

**Remaining gap:** The comparison does not promote the 3x multiplier to A-level; the report now discloses MAPE 69%, correct sign, supersonic under-prediction, and near-transonic over-prediction.

**What this would prove:** The combined effect of strip-theory Cmq × getDampingMultiplier() × 3 produces the correct total pitch-damping magnitude for a finned vehicle at M 1.05–4.5.

### For the transonic Cmq augmentation (B-level → bounded B or potential A)

**Missing:** Direct Cmq data at M < 1.05 to validate the peak location and amplitude of the Gaussian augmentation `k = 1 + 2.5*exp(-((M-1)/0.15)^2)`.

**Partial evidence available:** ADA636861's near-transonic shots (M 1.05–1.33) show elevated absolute Cmq, consistent with a transonic peak. However:
- The lowest Mach point is 1.056, so the subsonic side of the peak is not covered
- The data shows scatter (±30% between shots at similar Mach), so the peak amplitude cannot be precisely pinned
- The Gaussian width parameter (0.15) cannot be directly validated from this data alone

**What would fully close it:** Forced-oscillation wind tunnel data or additional free-flight data at M 0.8–1.2 for a finned vehicle. No such source was found in the repo.

### For `CmAlphaDot = 0.4 * Cmq` (B-level)

**Missing:** Any direct measurement of CmAlphaDot separate from Cmq. Free-flight aeroballistic range data produces (Cmq + CmAlphaDot) as a combined quantity. ADA636861's "Cmq" column likely represents this sum, as is standard for range testing.

**Implication:** ORP's total pitch damping coefficient is `Cmq + CmAlphaDot = Cmq + 0.4*Cmq = 1.4*Cmq`. The ADA636861 "Cmq" is actually `(Cmq + CmAlphaDot)_experimental`. So the comparison should be: `ORP_Cmq * 1.4` vs `ADA636861_Cmq`. This is actually favorable — it means the ADA636861 data validates the combined damping, which is the physically meaningful quantity.

---

## Question 3: Can the paper survive with a bounded-heuristic appendix?

**Yes, but the stronger path is now clear.**

### Survival path (bounded heuristic)

The paper can present:
1. Strip-theory Cmq accumulation: A-level (self-consistency < 0.5%, Tobak cone trend validated)
2. 3x multiplier: B-level bounded heuristic, with sensitivity analysis showing apogee insensitivity (if Prompt 16 confirms this)
3. Transonic augmentation: B-level bounded heuristic, physically motivated (transonic pitch damping amplification is well-established in the literature)
4. CmAlphaDot = 0.4*Cmq: B-level, standard engineering approximation

This is defensible for AST if presented honestly as "calibrated heuristic within bounded sensitivity range."

### Promotion path (recommended)

Compare a recalibrated damping model against the already digitized ADA636861 Cmq holdout for the Basic Finner:
1. If a revised model matches ADA636861 combined (Cmq + CmAlphaDot) within 30--50% without degrading the flight corpus, promote the multiplier path toward A-level.
2. If the near-transonic data (M 1.05--1.33) remains elevated but the subsonic side is still unobserved, keep the Gaussian augmentation as bounded-B.
3. If the mismatch remains systematic, keep the present report language: external data exists, but it does not validate the current tuning constants as isolated component physics.

**Effort estimate:** model recalibration plus a holdout rerun is now the remaining work; data digitization itself is complete.

---

## Detailed Source Audit

### Sources checked (all in repo)

1. **ADA636861.pdf** (DREV Basic Finner aeroballistic range report)
   - 152 pages, M 1.05–4.5, 25 individual shots + 8 multi-fit points
   - Table VII: per-shot CX0, CNa, CMa, **Cmq**, CnPa, Clp, ClDelta
   - Table VIII: 6-DOF single-fit coefficients with probable errors, **including Cmq**
   - Executive summary confirms: "All of the main aerodynamic coefficients (CX0, CNα, CMα, CMq, Cnpα, Clp, Clδ) were very well determined"
   - Cmq column now digitized into `paper/data/csv/ada636861_basic_finner_cmq.csv` and consumed by `BasicFinnerCmqBenchmarkTest`

2. **AEDC-TR-76-58** (roll damping data)
   - CSV header explicitly states: "No pitch damping (Cmq, Cmalpha_dot) data in this report"
   - Contains only Clp (roll damping) — irrelevant for pitch damping closure

3. **Tobak TN 3788** (cone stability derivatives)
   - Analytical theory for isolated cones, not finned vehicles
   - Provides CNq and CNa_dot, which relate to Cmq via moment arm
   - ORP's cone-only Cmq matches Tobak trend after axis transfer (39% at M=1.5)
   - A-level for cone theory validation, but does NOT validate the finned-vehicle 3x multiplier

4. **DynamicStabilityBenchmarkTest**
   - Self-consistency validation only (code implements its own formulas correctly)
   - Cmq accumulation < 0.5%, roll damping 2.0%, Magnus 0.00%
   - A-level for internal consistency, NOT for external physical accuracy

5. **AEDC-TSR-78-V30, AEDC-TR-78-21, AGARDograph 121, BRL R-1216**
   - None contain direct pitch-damping data for finned vehicles
   - BRL R-1216 is a theoretical framework (equations of motion)
   - AGARDograph 121 is a techniques survey

### Sources NOT in repo that could help (for future work)

- Forced-oscillation wind tunnel data for finned vehicles at M 0.8–1.2 (subsonic transonic peak)
- AGARD free-flight range compilations (various reports)
- Additional Basic Finner range data from other facilities (BRL, AEDC, NOL)

---

## Recommendations

### Immediate (highest ROI)

1. Treat `BasicFinnerCmqBenchmarkTest` and `paper/data/csv/ada636861_basic_finner_cmq.csv` as the current source of truth for the isolated damping comparison.
2. Keep the 3x multiplier and transonic Gaussian as B-level claims unless a revised model reduces the ADA636861 mismatch without degrading the integrated flight corpus.
3. Use near-transonic data points (M 1.05--1.33) only to bound the transonic augmentation qualitatively; they do not validate the subsonic side of the peak.

### Conditional (future recalibration)

4. If a revised model matches within 30--50% on a held-out ADA636861 Cmq comparison, update VALIDATION_MATRIX.md to promote the relevant damping claim.
5. If match remains poor, preserve the current paper language: the implementation is numerically consistent, but the isolated component damping constants are not externally closed.

### Paper strategy

7. Present combined (Cmq + CmAlphaDot) comparison against ADA636861 as the primary pitch-damping validation
8. Present strip-theory self-consistency as the secondary (implementation correctness) validation
9. Present transonic augmentation as physically motivated + bounded by sensitivity (Prompt 16) + partially supported by near-transonic ADA636861 data
10. Disclose subsonic transonic peak as unvalidated in the M < 1.0 regime

---

## Files examined

- `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanStabilityCalculator.java` (lines 116–173, 391–421)
- `core/src/test/java/info/openrocket/core/aerodynamics/TobakCmqBenchmarkTest.java`
- `core/src/test/java/info/openrocket/core/aerodynamics/DynamicStabilityBenchmarkTest.java`
- `paper/data/csv/tobak_cmq_benchmark.csv`
- `paper/data/csv/aedc_tr_76_58_roll_damping.csv`
- `paper/data/csv/naca_tn_3788_cone_stability_derivatives.csv`
- `paper/data/csv/ADA636861_basic_finner_cx0.csv`
- `paper/data/csv/dynamic_stability_benchmark.csv`
- `paper/data/md/dynamic_stability_benchmark.md`
- `paper/data/pdf/ADA636861.pdf` (pages 1–45, Tables I–VIII)
- `paper/data/VALIDATION_MATRIX.md`
