# Kinsel vs Qu8k / FMJ Geometry & Drag Differentiator

Generated: 2026-04-17
Source: `core/build/reports/simvreal-outliers/{Kinsel_P4935_A-601_Rocket,Qu8k,Full_Metal_Jacket1,Full_Metal_Jacket2}.md`
Context: P9 (`high_m_drag_reconciliation.md`) + P11 (`candidate_fixes_decision_memo.md`)

Kinsel overshoots real apogee by **+35.1%** at peak M=2.33. Qu8k is **−1.8%** at M=3.43. Both share the *identical* three ignored CDX1 settings (`ModifiedBarrowman=True`, `Turbulence=True`, `SustainerNozzle=...`), so CDX1 parity is NOT the driver. FMJ1 (+8.7%, M=2.32) and FMJ2 (+3.8%, M=2.48) are also close in Mach and only have the nozzle setting ignored. This memo isolates the unique Kinsel differentiator.

---

## 1. Geometry Inventory

| Metric | Kinsel | Qu8k | FMJ1 | FMJ2 |
|---|---|---|---|---|
| Real apogee (ft) | 42 771 | 121 478 | 37 981 | 30 038 |
| ORP error vs real | **+35.1%** | −1.8% | +8.7% | +3.8% |
| RAS error vs real | −3.9% | −1.5% | +2.1% | +8.4% |
| ORP vs RAS | **+40.6%** | −0.4% | +6.5% | −4.2% |
| Peak Mach | 2.33 | 3.43 | 2.32 | 2.48 |
| Burnout time (s) | **11.93** | 10.00 | 2.28 | 2.28 |
| Launch mass (kg) | 70.06 | 142.66 | 31.75 | 29.48 |
| Burnout mass (kg) | 40.05 | 70.14 | 20.41 | 18.14 |
| Ref diameter (in) | 6.500 (fin can) | 8.375 (fin can) | 4.250 (fin can) | 4.250 (fin can) |
| Ref area (m²) | 0.02141 | 0.03554 | 0.00915 | 0.00915 |
| Nose shape / length | Ogive 35.0 in | Conical 42.0 in | Ogive 16.0 in | Conical 5.75 in |
| Body tube OD (in) | 6.125 | 8.000 | 4.000 | 4.000 |
| Body tube length (in) | 113.5 | 99.25 | 101.57 | 103.07 |
| Body L/D | 17.5 | 12.4 | 25.4 | 25.8 |
| Fin-can shoulder (fore→aft in) | 6.125→6.500 | 8.000→8.375 | 4.000→4.250 | 4.000→4.250 |
| Fin-can length (in) | 20.0 | 24.0 | 14.5 | 14.5 |
| Fin-can OD (in) | 6.500 | 8.375 | 4.250 | 4.250 |
| **Boattail aft of fin can** | **NONE** | **8.375→6.700, 1.1 in** | NONE | NONE |
| Fin count | 4 | 4 | 3 | 3 |
| Fin root / tip / span / sweep (in) | 17.0 / 6.0 / 7.0 / 9.5 | 22.0 / 5.0 / 7.5 / 14.0 | 12.5 / 4.0 / 4.4 / 8.5 | 12.5 / 4.0 / 4.4 / 8.5 |
| Fin thickness (in) / xsec | 0.250 / Hex DW | 0.250 / Hex DW | 0.125 / Hex DW | 0.125 / Hex DW |
| Base-exposed radius (in) | 3.250 (fin-can flat base) | 3.350 (boattail exit) | 2.125 (fin-can flat base) | 2.125 (fin-can flat base) |
| **Base-area / Ref-area** | **1.000** | **0.640** | **1.000** | **1.000** |
| Ignored CDX1 settings | ModBarrow, Turb, Nozzle=3.09 | ModBarrow, Turb, Nozzle=6 | Nozzle=2.5 | Nozzle=2.5 |

**One decisive geometric difference between Qu8k and everything else: Qu8k has a 1.1 in boattail (8.375 → 6.700 in) aft of the fin can. Kinsel, FMJ1, and FMJ2 all have a flat fin-can base.** The boattail cuts Qu8k's exposed base area to 64% of the reference area, and Viswanath/boattail corrections further reduce the base-pressure coefficient. That alone is why Qu8k's Cd_b at M=3.43 is only 0.069 vs Kinsel's 0.143 at M=2.33.

---

## 2. Component Cd Breakdown at Peak Mach

Values from the per-case `max-mach` snapshot (actual flight state, not sweep).

### Kinsel (M=2.33, air p=56.9 kPa, T=259.8 K)

| Component | Cd_total | Cd_f | Cd_p | Cd_b | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.0830 | 0.0830 | 0 | 0 | 23.7% |
| Nose Cone | 0.0339 | 0.0171 | 0.0168 | 0 | 9.7% |
| Fin | 0.0082 | 0.0063 | 0.0019 | 0 | 2.3% |
| Fin Can | 0.1586 | 0.0155 | 0 | **0.1431** | 45.3% |
| Fin Can Shoulder | 0.0004 | 0.0004 | 0 | 0 | 0.1% |
| Rail Guide | 0.0206 | 0 | 0.0206 | 0 | 5.9% |
| **TOTAL** | **0.3501** | 0.1414 | 0.0655 | **0.1431** | |

### Qu8k (M=3.43, air p=59.4 kPa, T=262.7 K)

| Component | Cd_total | Cd_f | Cd_p | Cd_b | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.0439 | 0.0439 | 0 | 0 | 17.2% |
| Nose Cone | 0.0373 | 0.0093 | 0.0279 | 0 | 14.6% |
| Fin | 0.0042 | 0.0036 | 0.0006 | 0 | 1.6% |
| Fin Can | 0.0111 | 0.0111 | 0 | 0 | 4.4% |
| Fin Can Shoulder | 0.0003 | 0.0003 | 0 | 0 | 0.1% |
| Rail Guide | 0.0234 | 0 | 0.0234 | 0 | 9.2% |
| Boattail | 0.0986 | 0.0006 | 0.0287 | **0.0693** | 38.7% |
| **TOTAL** | **0.2549** | 0.0798 | 0.1058 | **0.0693** | |

### FMJ1 (M=2.32, air p=92.7 kPa, T=293.3 K)

| Component | Cd_total | Cd_f | Cd_p | Cd_b | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.1120 | 0.1120 | 0 | 0 | 33.3% |
| Nose Cone | 0.0534 | 0.0119 | 0.0416 | 0 | 15.9% |
| Fin | 0.0075 | 0.0065 | 0.0009 | 0 | 2.2% |
| Fin Can | 0.1483 | 0.0170 | 0 | **0.1313** | 44.1% |
| Fin Can Shoulder | 0.0003 | 0.0003 | 0 | 0 | 0.1% |
| **TOTAL** | **0.3363** | 0.1607 | 0.0444 | **0.1313** | |

### FMJ2 (M=2.48, air p=92.1 kPa, T=292.8 K)

| Component | Cd_total | Cd_f | Cd_p | Cd_b | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.1103 | 0.1103 | 0 | 0 | 21.0% |
| Nose Cone | 0.2507 | 0.0033 | 0.2474 | 0 | 47.8% |
| Fin | 0.0072 | 0.0063 | 0.0009 | 0 | 1.4% |
| Fin Can | 0.1417 | 0.0165 | 0 | **0.1252** | 27.0% |
| Fin Can Shoulder | 0.0003 | 0.0003 | 0 | 0 | 0.1% |
| **TOTAL** | **0.5245** | 0.1493 | 0.2500 | **0.1252** | |

Notes:
- The **base-drag coefficient itself** (0.131 / 0.125 / 0.143 Cd) is *essentially identical* across Kinsel / FMJ1 / FMJ2 at similar Mach. The fin-can base-drag model is **not mis-scaling for Kinsel**; it produces the same value per unit reference area as for the FMJ rockets.
- Qu8k's base Cd is only 0.069 because the boattail reduces the exposed-base / ref-area ratio to 0.64 and the Viswanath boattail correction further trims the coefficient.
- FMJ2's inflated *total* Cd (0.525) comes entirely from a 5.75 in conical nose (half-angle ≈ 19°). That explains why FMJ2 with the same fin can as FMJ1 has 56% higher total Cd — nose wave drag, not base drag.

---

## 3. Base-drag Share of Total Cd

| Rocket | Cd_b / Cd_total at peak M | Flat fin-can base? | Boattail? | err |
|---|---:|:---:|:---:|---:|
| Kinsel | **40.9%** | YES | no | +35.1% |
| Qu8k | 27.2% | no | YES (1.1 in) | −1.8% |
| FMJ1 | 39.0% | YES | no | +8.7% |
| FMJ2 | 23.9% (shadowed by big nose Cd) | YES | no | +3.8% |

The *absolute* base Cd is 0.125–0.143 for all three flat-base cases. Kinsel is NOT suffering from a base-drag model that specifically mis-handles the fin-can-over-body stepped geometry — it gives the same answer as FMJ.

---

## 4. Finned-Body Augmentation Inputs

From `BarrowmanDragCalculator.calculateFinnedBaseAugmentation()` (line 928):

| Rocket | n_fins | finFactor = min(n/4, 1.5) | span (in) | body radius (in) | spanFactor = clamp(span/R, 0.3, 1.0) | machFactor @ peak | K = 0.55 | Augmentation |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Kinsel | 4 | 1.00 | 7.0 | 3.250 | 1.00 | 1.00 (M=2.33 in [1.3, 3.0]) | 0.55 | **1.55** |
| Qu8k | 4 | 1.00 | 7.5 | 3.350* | 1.00 | 3/M = 0.874 (M=3.43 > 3.0) | 0.55 | 1.48 |
| FMJ1 | 3 | 0.75 | 4.4 | 2.125 | 1.00 | 1.00 | 0.55 | **1.413** |
| FMJ2 | 3 | 0.75 | 4.4 | 2.125 | 1.00 | 1.00 | 0.55 | **1.413** |

*For Qu8k the augmentation is applied at the boattail face (exit radius 3.35 in), but the boattail factor dominates anyway.

Observation: Kinsel's augmentation (1.55) is the **largest** of the three flat-base rockets, not the smallest. Any increase in `FINNED_BASE_K` would raise Kinsel's base Cd more than FMJ's, pushing Kinsel's drag *higher* (good) but also pushing FMJ1 and FMJ2 higher (slightly worse for cases already within tolerance). This rules out Candidate #3 (increase K) as a *Kinsel-specific* fix — it does not uniquely address Kinsel's gap.

---

## 5. Flight Profile (coast-phase dominated)

| Rocket | Burnout (s) | Apogee (s) | Coast (s) | Boost Δalt (ft) | Coast Δalt (ft) | % altitude in coast |
|---|---:|---:|---:|---:|---:|---:|
| Kinsel | 11.93 | 59.00 | **47.07** | 15 503 | 42 291 | **73%** |
| Qu8k | 10.00 | 86.38 | 76.38 | 19 073 | 100 178 | 84% |
| FMJ1 | 2.28 | 45.80 | 43.52 | 2 531 | 38 752 | 94% |
| FMJ2 | 2.28 | 38.33 | 36.05 | 2 728 | 28 455 | 91% |

| Rocket | Boost Avg Cd | Boost Avg Cd_b | Coast Avg Cd | Coast Avg Cd_b |
|---|---:|---:|---:|---:|
| Kinsel | 0.462 | 0.180 | 0.577 | 0.200 |
| Qu8k | 0.364 | 0.105 | 0.622 | 0.126 |
| FMJ1 | 0.520 | 0.166 | 0.560 | 0.190 |
| FMJ2 | 0.589 | 0.158 | 0.589 | 0.181 |

Key observation: **Kinsel's boost phase is 11.9 s long — 5× longer than either FMJ — and contributes 27% of apogee altitude** (versus 6–9% for FMJ). Any under-prediction of drag during the long boost phase compounds severely for Kinsel. FMJ's boost is so short (2.3 s, 2 500 ft) that boost-phase drag bias barely moves apogee.

Kinsel's coast-average Cd (0.577) is actually *higher* than FMJ1 (0.560) and comparable to FMJ2 (0.589). **Coast-phase drag is NOT the issue** — it's correctly calibrated relative to FMJ. The gap opens during **boost**, where Kinsel spends 11.9 s in the thick atmosphere (ORP burnout at ~15 500 ft, p = 56.9 kPa ≈ 56% of sea level) accumulating a ~42 000 ft coast. The ratio coast/boost altitude for Kinsel is 2.7, for FMJ1 is 15.3. So Kinsel's final apogee is **much more sensitive to Cd integrated over the boost phase** than FMJ's.

---

## 6. Where the ~35% Overshoot Comes From (Theory)

Kinsel has essentially the same per-Mach drag decomposition as FMJ1 at peak M, but FMJ1 only overshoots by 8.7%. The differential is dominated by **burnout kinematics**, not a per-Mach Cd bias:

1. **Thrust-matched to atmosphere**: Kinsel's P4935 motor burns for 11.9 s through M = 0 → 2.33 while still inside dense atmosphere (56 kPa). The FMJ O10000 burns for 2.3 s, exits dense air fast, and does most work at low Cd. For Kinsel, any under-counted drag between M = 0.5 and M = 2.0 is integrated over 11.9 s at high ρv² and costs apogee directly.

2. **Power-on base drag during boost is too aggressive**. The `SustainerNozzle=3.09` on a 6.5 in base (area ratio A_exit/A_base = (3.09/6.5)² = 0.226 → unusually small) is ignored on import, and `computePowerOnBaseDragMultiplier()` falls back to `DEFAULT_POWER_ON_FACTOR`. Kinsel's boost-avg Cd_b = 0.180 vs coast-avg 0.200 — only a 10% reduction — suggests the default is already conservative. But for FMJ the boost phase is so short this doesn't matter; for Kinsel 11.9 s at this Cd_b bias matters a lot. However: both under- and over-prediction of boost base drag move apogee, and boost Cd_b is already close to coast. So this is probably worth **≤ 3–5 pp**.

3. **Turbulence=True ignored**: Kinsel is a 6.5 in × 169 in rocket. At M = 2.33, Re_L ≈ ρ V L / μ ≈ 0.76 kg/m³ × 780 m/s × 4.29 m / 1.65e-5 Pa·s ≈ 1.5e8 — fully turbulent. If RASAero's `Turbulence=True` forces fully-turbulent BL where ORP's Van Driest II still allows laminar runs near the nose, the friction Cd could be 5–8% higher in RAS. Kinsel's body-tube Cd_f = 0.083 is suspiciously *low* per unit L/D (0.0048/(L/D), vs 0.0093 for EZI-65 and 0.0098 for T&L per `high_m_drag_reconciliation.md` §4b). Some of that is legitimate supersonic compressibility relief, but the jump from 0.0078 (Raven at M=1.12) to 0.0048 (Kinsel at M=2.33) is larger than Van Driest II predicts alone. Suggests **laminar fraction too large** during the Kinsel boost, before natural transition.

4. **Ignored ModifiedBarrowman=True**: RASAero's "modified Barrowman" adds a body-CP correction at high α that doesn't feed drag directly but can cause real hardware to fly slightly unstable / corkscrew at high Mach, bleeding energy. ORP flies too straight (Kinsel coast avg AoA 0.79°) and keeps too much kinetic energy. **Hard to quantify without corkscrew loss measurement**, flagged but not primary.

**Bottom line**: Kinsel's drag *coefficients* are in family with FMJ1/FMJ2 at peak M; the real gap is that the long boost phase (11.9 s) magnifies a ~10–15% boost-average-Cd shortfall into ~35% apogee overshoot. The boost-average Cd shortfall is plausibly driven by (a) too-low friction on the long body tube (premature laminar assumption — `Turbulence=True` not honored), plus (b) the transonic base drag window being narrow (Candidate #2) which for FMJ's 2.3 s transit is invisible but for Kinsel's 11.9 s transit integrates.

---

## 7. Ranked Candidate Fixes — Kinsel-Specific Lens

### Fix A — Apply a Turbulence-flag friction boost for `Turbulence=True` CDX1 imports

**Mechanism**: When the loader encounters `Turbulence=True`, flag the rocket and force `BarrowmanDragCalculator.calculateFrictionCoefficient()` to treat the boundary layer as fully turbulent from X/L = 0 (or reduce the laminar run to a small trip at the nose-body junction). Currently the flag is silently ignored.

**Expected impact**:
- Kinsel: body-tube Cd_f rises ~10–15% at supersonic, adding ~0.010–0.012 to total Cd at M 2.3. Over the 11.9 s boost this contributes ~8–12 pp apogee closure.
- Qu8k: same flag set; body-tube Cd_f rises similarly. Qu8k was −1.8%; this would push it further negative (under-shoot). **Regression risk for Qu8k.**
- FMJ1 / FMJ2: flag *not* set (only Nozzle is ignored for FMJ). No effect. Safe.
- CalIsp, Byrum, EZI-65, T&L: likely `Turbulence=True` in most CDX1s by default — **broad regression risk** unless we audit which cases have it set.

**Regression risk**: MEDIUM-HIGH unless preceded by audit of how many of the 24 SimVReal cases set `Turbulence=True`. Qu8k is the direct failure mode. Needs pairing with a smaller `FINNED_BASE_K` or a Qu8k-specific boattail offset.

### Fix B — Remove Lamb-Oberkampf Re correction (Candidate #1 from P11)

**Mechanism**: Drop the `reFactor = 1 − 0.08 (log₁₀Re_D − 6)` multiplier at M > 1.3 in `calculateBaseCD`.

**Expected impact**: Kinsel Re_D ≈ 9e6 → reFactor ≈ 0.92, removal adds +8% to fin-can base Cd (0.143 → 0.154) → +3–4 pp apogee closure. Qu8k Re_D at boattail also ≈ 1e7, same +8% → adds ~0.005 to Cd_b. Since Qu8k was −1.8%, this pushes it to roughly −3% to −4% (still within 10% band). FMJ has Re_D ≈ 4e6 → reFactor ≈ 0.97, removal adds ~3% to base Cd → FMJ1 +8.7% → +6% (improves), FMJ2 +3.8% → +1% (improves).

**Regression risk**: LOW. Basic Finner MAPE moves 22.7% → ~24%, well inside the 30% gate. All SimVReal cases either improve or move within-band further negative.

### Fix C — Widen transonic base-drag peak (Candidate #2 from P11)

**Mechanism**: Raise the peak of the transonic polynomial from 0.25 @ M=1.05 to 0.28 @ M=1.08, broadening the peak through M=1.15.

**Expected impact on Kinsel**: NONE at peak M=2.33 (polynomial only active M 0.85–1.30). However, Kinsel *passes through* the transonic window during its 11.9 s boost. Over that window the fin-can base Cd_b would rise ~15–20% for the 0.5–1.0 s transit → ~0.5–1.5 pp apogee closure. Small but positive.

**Regression risk**: LOW-MEDIUM. Helps Raven (primary target), helps Kinsel slightly, no effect on pure subsonic or pure supersonic cases. Needs data anchor before commit.

---

## 8. Summary Table

| Fix | Kinsel closure (pp) | Qu8k risk | FMJ1/2 risk | Broader regression | Safe to try? |
|---|---:|---|---|---|---|
| **A: Honor `Turbulence=True`** | **8–12** | pushes Qu8k more negative; MEDIUM | no effect (flag not set) | HIGH if many cases set the flag without verification | NO without audit |
| **B: Remove Lamb-Oberkampf Re factor** | 3–4 | mild (Qu8k −1.8 → −3 to −4%) | improves both | Basic Finner MAPE 22.7 → ~24 | **YES** |
| **C: Widen transonic peak** | 0.5–1.5 | none | none | Raven big improvement; no subsonic degradation | YES with Hoerner/ESDU data anchor |

**Combined (B + C)**: Kinsel closes ~4–6 pp (+35% → +29% to +31%). Not full closure.

**Full Kinsel closure** (< 10%) requires Fix A, which *must* be gated behind a per-case `Turbulence=True` flag audit. The audit is already possible from the existing CDX1 loader-warning corpus and does not require code changes.

---

## 9. Data Gaps / Flags

- **Component-CD sweep CSVs** for all four cases exist (`*-component-cd.csv`) and cover M 0.3 … peak at AoA=0. Not re-read here but confirmed present.
- **No analytical breakdown of what RASAero does with `Turbulence=True`** exists in the repo. The 8–12 pp estimate for Fix A is inferred from the body-tube-friction-per-L/D metric in `high_m_drag_reconciliation.md` §4b, not from RAS source. Before implementing Fix A, recommend a web-anchored check of RASAero's turbulence model.
- **Kinsel long-boost apogee sensitivity** is inferred from the boost-altitude fraction (27%) rather than from a direct boost-vs-coast Cd perturbation study. A sensitivity run with Cd_boost × 1.10 would quantify it precisely but is out of scope here (no code changes allowed).
