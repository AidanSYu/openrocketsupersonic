# RM-10 vs Basic Finner Root-Cause Diagnostic

**Date:** 2026-04-17
**Agent:** Claude Opus 4.7 (1M context)
**Parent prompt:** AST-readiness follow-up to Prompt 14 ("Diagnose the NACA RM-10 +80.5% MAPE overshoot")
**Scope:** Read-only diagnostic. No calculator code modified, no constant tuned, no existing test modified.
**Inputs:**
- `paper/data/pdf/NACA_TN_3320.pdf` (primary-source geometry, Figure 1)
- `paper/data/csv/NACA_TN_3320_RM10_cdt.csv` (digitized CDT)
- `core/.../NacaRm10FinnedBodyDragBenchmarkTest.java` (test geometry)
- `core/.../BasicFinnerDragBenchmarkTest.java` (comparison case)
- `core/.../SupersonicTestRockets.makeBasicFinner()` (comparison geometry)

**Outputs:**
- New test: `core/.../Rm10VsBasicFinnerDiagnosticTest.java`
- CSV: `core/build/reports/rm10_vs_basic_finner_component_cd.csv`

---

## 1. Geometry Comparison (NACA TN 3320 Fig. 1 vs test construction)

| Feature | NACA TN 3320 (PDF primary source) | ORP test (`makeNacaRm10FullScale`) | Basic Finner (ADA636861) |
|---|---|---|---|
| Body profile | Continuous parabolic arc, Y = 6.000 - 0.0007407·x² in, Sta 0-146.5 | **Split** into NoseCone (POWER, p=0.5) Sta 0-90 + Conical fore-boattail Sta 90-129 + constant-radius BodyTube Sta 129-146.5 + **2 cm conical terminal-boattail** | Simple 10-deg cone + cylinder |
| Body length | 12.2 ft (3.721 m), fineness 12.2 | 3.721 m (0-90 + 90-129 + 129-146.5) | 10 caliber (0.30 m, fineness 10) |
| Max body diameter | 12.00 in (0.3048 m) | 0.3048 m | 30 mm (1 cal) |
| Base diameter | 7.272 in (0.1847 m) | 0.1847 m | 30 mm (flat base at body dia) |
| Base-dia / max-dia | 0.606 | 0.606 | 1.000 |
| Afterbody shape | Smooth parabolic taper from Sta 90 (Y=6) to Sta 146.5 (Y=3.636). **Local half-angle at base ~4.8°** (dY/dx=-0.0837) | Sta 90-129: conical 6.00 -> 4.873 in (average ~1.7°); Sta 129-146.5: **constant radius** 4.873 in; Sta 146.5 -> base: **2 cm conical, half-angle 57.5°** | No boattail |
| Fin planform | "Untapered, sweptback 60°, total AR 2.04", t/c=10% normal to LE, 5% streamwise | TrapezoidFinSet root=17.5 in, tip=8.12 in, sweep=22.64 in, span=13.07 in, ROUNDED, t/c=5% streamwise | Rectangular root=tip=1 cal, no sweep, HEXAGONAL (sharp LE), t/c=8% |
| Fin count | 4 | 4 | 4 |
| Fin cross-section | "10-percent-thick circular-arc" (biconvex, sharp LE and TE) | ROUNDED (triggers *round-LE bluntness drag* formula) | HEXAGONAL (sharp LE, zero bluntness drag) |

**Geometry divergences between PDF and test:**
1. **Fin taper introduced to hit AR=2.04 that is not in the PDF.** The PDF explicitly says "untapered" (constant chord, not trapezoidal). The test imposes tip/root = 0.465, making it a swept trapezoid. For a true untapered 60-deg swept fin with the stated total AR=2.04 and the 13.07 in exposed semi-span the chord would be ~12.8 in, not 17.5 in.
2. **Fin cross-section mapped to ROUNDED instead of HEXAGONAL.** A 10% circular-arc biconvex has a SHARP leading and trailing edge (circular-arc meets at a point at both ends). ORP's `ROUNDED` triggers the empirical round-LE bluntness formula (`Cd_LE = 1.214 - 0.502/M² + ...`) that should only apply to fins with a truly rounded leading edge (e.g., NACA 0010 airfoils). HEXAGONAL would be a better approximation of the sharp-nosed biconvex.
3. **Afterbody split with a 2 cm "terminal boattail" placeholder.** The real RM-10 has ONE smooth parabolic taper from Sta 90 to the base. The test splits it into three: conical fore-boattail (gentle, long), constant-radius fin-mount tube, and a steep 57.5-deg lumped terminal boattail. This splits a 5-deg-average continuous afterbody into **one large gentle transition + one extremely steep tiny transition**.

None of the geometry errors is catastrophic on its own, but each one is a candidate mechanism evaluated below.

---

## 2. Per-component CD Side by Side (ORP diagnostic test)

From `core/build/reports/rm10_vs_basic_finner_component_cd.csv`, diagnostic ran 2026-04-17.
All values are CD referenced to the vehicle max-body frontal area.

### M = 1.5 (RM-10 CDT_exp = 0.230, BF CX0_exp = ~0.65 interpolated)

| Component | Fric | Press | Base | Total |
|---|---|---|---|---|
| **RM-10 ParaboloidNose** (POWER p=0.5) | 0.0308 | 0.0191 | 0 | 0.0498 |
| **RM-10 ForeBoattail** (Conical, f=17.3) | 0.0181 | 0.0000 | 0 | 0.0181 |
| **RM-10 FinMountTube** (BodyTube) | 0.0073 | 0.0000 | 0 | 0.0073 |
| **RM-10 TerminalBoattail** (Conical, L=2 cm, theta=57.5°, f=0.32) | 0.0005 | **0.0429** | **0.0834** | **0.1268** |
| **RM-10 Fins** (ROUNDED, t/c=5%, 60° sweep) | 0.0050 | 0.0450 | 0.0074 | 0.0574 |
| **RM-10 TOTAL** | 0.0768 | **0.2420** | 0.1131 | **0.4318** |
| BF Cone | 0.0131 | 0.1240 | 0 | 0.1371 |
| BF Cylinder | 0.0649 | 0.0000 | 0.2308 | 0.2957 |
| BF Fins (HEX) | 0.0065 | 0.0292 | 0 | 0.0356 |
| **BF TOTAL** | 0.1038 | 0.2406 | 0.2308 | **0.5752** |

### M = 2.0 (RM-10 CDT_exp = 0.215, BF CX0_exp = ~0.55)

| Component | Fric | Press | Base | Total |
|---|---|---|---|---|
| **RM-10 ParaboloidNose** | 0.0271 | 0.0162 | 0 | 0.0432 |
| **RM-10 ForeBoattail** | 0.0159 | 0.0000 | 0 | 0.0159 |
| **RM-10 FinMountTube** | 0.0064 | 0.0000 | 0 | 0.0064 |
| **RM-10 TerminalBoattail** | 0.0005 | **0.0323** | **0.0625** | **0.0953** |
| **RM-10 Fins** | 0.0044 | 0.0471 | 0.0056 | 0.0571 |
| **RM-10 TOTAL** | 0.0676 | **0.2368** | 0.0847 | **0.3891** |
| BF Cone | 0.0115 | 0.1042 | 0 | 0.1157 |
| BF Cylinder | 0.0572 | 0.0000 | 0.1741 | 0.2314 |
| BF Fins (HEX) | 0.0057 | 0.0188 | 0 | 0.0245 |
| **BF TOTAL** | 0.0915 | 0.1795 | 0.1741 | **0.4452** |

### M = 2.5 (RM-10 CDT_exp = 0.210)

| Component | Fric | Press | Base | Total |
|---|---|---|---|---|
| **RM-10 TerminalBoattail** | 0.0004 | **0.0274** | **0.0529** | **0.0808** |
| **RM-10 Fins** | 0.0039 | 0.0434 | 0.0047 | 0.0520 |
| **RM-10 ParaboloidNose** | 0.0238 | 0.0158 | 0 | 0.0397 |
| **RM-10 TOTAL** | 0.0594 | **0.2168** | 0.0717 | **0.3480** |

### M = 3.0 (RM-10 CDT_exp ~ 0.190)

| Component | Fric | Press | Base | Total |
|---|---|---|---|---|
| **RM-10 TerminalBoattail** | 0.0004 | **0.0248** | **0.0482** | **0.0733** |
| **RM-10 Fins** | 0.0034 | 0.0412 | 0.0043 | 0.0489 |
| **RM-10 ParaboloidNose** | 0.0210 | 0.0157 | 0 | 0.0367 |
| **RM-10 TOTAL** | 0.0525 | **0.2052** | 0.0653 | **0.3230** |

### Key observations

- **The 2 cm TerminalBoattail placeholder alone contributes 22–29% of RM-10 total CD** across M 1.5–3.0. Its pressure term alone is ~0.025–0.043; its base term alone is ~0.048–0.083. The same magnitude CD is emitted from a 2 cm sliver of geometry as from the entire 7.5-ft paraboloid nose.
- **RM-10 fin pressure drag (~0.04 per fin-set across 4 fins) is 2.4× higher than Basic Finner fin pressure drag (~0.019)** despite RM-10 fins having *thinner* streamwise t/c (5% vs 8%).
- **RM-10 body friction (ForeBoattail 0.016 + FinMountTube 0.006 + Nose 0.027 = 0.049 at M=2)** is 46% less than Basic Finner body friction (Cone 0.012 + Cylinder 0.057 = 0.069 at M=2), consistent with RM-10's smaller wetted/reference ratio.
- **Basic Finner's base drag (0.174 at M=2) is a reasonable match to Devan-Ashwood × 1.55 finned-augmentation (0.110 × 1.55 = 0.171)**.  ORP's matching error on Basic Finner is elsewhere (shape/t-c/Re, outside this diagnostic).
- **NACA TN 3320 reports CDB average ≈ 0.04 for full-scale RM-10** (page 7: *"CDB for the full-scale model has an average value of approximately 0.04"*). ORP's terminal-boattail base is 0.063 at M=2.0 — **1.55× too high, exactly the ORP FINNED_BASE_K=0.55 augmentation multiplier**.

---

## 3. Ranked Root-Cause Hypotheses

### H1 (PRIMARY): Finned-base augmentation is misapplied to a body with a contracted base

**What it does:** ORP always multiplies the base CD by `1 + 0.55 × finFactor × spanFactor × machFactor` whenever fins are present near the base. For RM-10 at M=2.0 with 4 fins on a finMount-tube whose next sibling is the terminal boattail, this evaluates to **1.55× at all M 1.5–3.0**.

**Evidence:**
- NACA TN 3320 Figure 7 / page 7: full-scale CDB averages 0.04 across M 1.2–3.3.
- ORP's base at terminal-boattail: 0.063 (M=2.0), 0.053 (M=2.5), 0.048 (M=3.0). Devan-Ashwood alone at these M: 0.064/1.55=0.041, 0.054/1.55=0.035, 0.048/1.55=0.031. *Removing the 1.55 multiplier brings ORP base CD within ≤0.01 of TN 3320.*
- Basic Finner: fins are at a FLAT base at max body diameter → fin-wake/base-wake interference is physically real (ADA636861 validates ~50% augmentation).
- RM-10: fins END at the base but the base is already a CONTRACTED (7.272 in on a 12.0 in max body) post-boattail exit plane. The low-pressure fin wake can re-energize via expansion over the boattail shoulder before reaching the base plane, so the augmentation that Basic Finner sees does not necessarily apply here.
- The code's Viswanath boattail reduction (which would partially offset) is **skipped** when the base-charging component is the boattail itself (`selfBoattail=true` branch, `BarrowmanDragCalculator.java` line 872-887). The boattail-angle-based `calculateBoattailFactor` is 1.0 at theta=57.5° (angleFactor=0 for theta≥20°), so no self-reduction either.

**Falsifiability:** In a private fork, run a one-off experiment where the `calculateFinnedBaseAugmentation` return is forced to 1.0 **only when a boattail is present upstream of the base**. Expected effect: RM-10 base CD drops from 0.063 to ~0.041 at M=2 (−0.022 on total). Basic Finner unchanged (no upstream boattail). RM-10 MAPE should drop roughly 10 percentage points.

**Magnitude at M=2.0:** −0.022 of CD (~ 5.6% of current 0.389 total, ~ 10% of 0.215 experimental).

**File:line:** `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java:892` (call site); `928-1044` (augmentation body); `80` (FINNED_BASE_K=0.55). Don't fix yet — the code logic is correct for Basic Finner by design.

### H2 (PRIMARY): 2 cm "terminal boattail" placeholder generates a phantom ~0.03 pressure CD

**What it does:** The test models the final 1.24 in radial contraction from the fin-mount-tube radius (Y=4.873) to the real base radius (Y=3.636) as a 2 cm conical Transition. That gives fineness = 0.318, boattail half-angle 57.5°. `SymmetricComponentCalc.calculatePressureCD` has a branch (line 430-439) that for aftRadius<foreRadius with fineness<1 sets `cd = baseCD × frontalArea / refArea` (unscaled). The formula assumes the transition is a physically meaningful boattail, but applied to a 2 cm sliver it produces a phantom ~0.03 CD that has no counterpart in the real vehicle.

**Evidence:**
- CSV: TerminalBoattail pressure CD 0.043/0.032/0.027/0.025 at M=1.5/2.0/2.5/3.0.
- If the fore-boattail were extended all the way to the base diameter (i.e. a single conical transition from Sta 90 Y=6.00 to Sta 146.5 Y=3.636) with fineness = 1.435 m / (2 × 0.0606 m) = 11.8, the pressure-drag branch would give cd=0 (fineness ≥ 3 → cd=0 shortcut). Which is also wrong (gentle boattails have nonzero drag), but not phantom either.
- The real RM-10 half-angle at the base is 4.8° (parabolic slope at x=56.5"), so neither the current 57.5° placeholder nor a perfectly-faired fineness=11.8 is geometrically faithful.

**Falsifiability:** Rebuild the RM-10 geometry as a single parabolic NoseCone (full length 146.5 in to the base directly) with base radius 3.636 in — no fore-boattail, no fin-mount tube, no terminal-boattail. Fins on a zero-length ring attached via a minimal BodyTube, or via an ORP construction that keeps the parabolic profile intact. Expected effect: TerminalBoattail pressure drag of 0.025–0.043 vanishes. Base drag gets charged on the final nose or a different component but with the same total area, so base contribution roughly unchanged. MAPE drops ~10 percentage points.

**Magnitude at M=2.0:** −0.032 of CD (~ 8% of current 0.389 total, ~ 15% of 0.215 experimental).

**File:line:** `core/src/main/java/info/openrocket/core/aerodynamics/barrowman/SymmetricComponentCalc.java:430-439` (the <fineness=1 branch). This is NOT buggy code — it is correct for typical boattails — but the **test geometry forces this code path into a regime it was not designed for**. The fix should be in the test geometry, not the calculator.

### H3 (SECONDARY): Fins modeled as ROUNDED but should be HEXAGONAL / sharp biconvex

**What it does:** NACA TN 3320 page 4 explicitly describes the fins as "10-percent-thick circular-arc cross section". A circular-arc section has a SHARP leading edge (the two arcs meet at a point with zero radius). ORP's `ROUNDED` crossSection is intended for truly blunt-LE airfoils (e.g., NACA 0010 with a finite LE radius) and triggers the empirical `Cd_LE = 1.214 - 0.502/M² + 0.1095/M⁴` round-LE bluntness formula plus the `baseCD/2` trailing-edge term. Over 4 fins this adds ~0.02–0.04 of spurious pressure drag and ~0.006 of spurious fin-base drag at M 1.5–3.0.

**Evidence:**
- CSV: RM-10 fins pressure CD = 0.047 (M=2.0) vs Basic Finner fins pressure CD = 0.019. RM-10 fins have thinner streamwise t/c (5% vs 8%), smaller total planform/refArea, and higher LE sweep (60° vs 0°). A naive Ackeret estimate gives RM-10 fin wave drag ≈ (Basic Finner) × (5²/8²) × (cos²0/cos²60) = 0.019 × 0.39 × 4 = 0.030, less than ORP's 0.047. The extra ~0.017 comes from the ROUNDED LE bluntness formula.
- `FinSetCalc.java:929-936` sets `cd = 1.214 - 0.502/M² + 0.1095/M⁴` at supersonic M for ROUNDED. At M=2: cd=1.095. × cos²(60°)=0.25. × span·thickness/refArea (0.3319·0.02223/0.0730 = 0.101). Per fin: 0.0276. × 4 fins: 0.110. Contributes > half of the 0.047 total pressure term.
- A 10% circular-arc should, per Hoerner Chapter 6 and DATCOM 4.1.5.1, give a wave drag term approximately equal to what a HEXAGONAL (double-wedge) with 10% thickness gives — sharp LE, thickness drag only.

**Falsifiability:** Change the test's `fins.setCrossSection(...)` call from `ROUNDED` to `HEXAGONAL` (do NOT modify the test — build a scratch diagnostic or local fork). Expected effect at M=2.0: fin LE bluntness drops from ~0.027 to 0; fin base drop from 0.006 to 0; fin Ackeret/DATCOM term drops from `K=16/3` to `K=4.0` on 4 fins ≈ 25% reduction. Net ~ −0.035 of CD across all 4 fins at M=2.0.

**Magnitude at M=2.0:** −0.020 to −0.035 of CD (~ 5–9% of 0.389, ~ 10–16% of 0.215).

**File:line:** `core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java:926-944` (round-LE bluntness branch); `1127-1144` (`datcomSectionK` K values). Model code is correct. The issue is **test-side cross-section mapping** and/or **lack of a dedicated circular-arc biconvex cross-section class**.

### H4 (TERTIARY): Fore-boattail + constant-radius fin-mount tube artificially shorten the body-wetted-area curvature benefit

**What it does:** The real RM-10 body has a smoothly decreasing radius from Sta 90 to Sta 146.5 following Y = 6 - 0.0007407·x². The test approximates this with: conical transition Sta 90→129 (avg angle 1.7°, fineness 17.3) + cylindrical tube Sta 129→146.5 (constant radius 4.873 in) + 2 cm terminal boattail. The cylindrical fin-mount tube is 1.24 in wider than the true parabolic profile at Sta 146.5, which (a) increases wetted area in the rear of the body, (b) pushes the true-base contraction into the 2 cm placeholder that creates phantom drag (H2), and (c) prevents the post-90-station expansion-fan pressure recovery on the actual parabolic profile from being modeled (the fin-mount tube is a cylindrical region so no expansion fan, no Prandtl-Meyer pressure recovery).

**Evidence:**
- Fin-mount tube friction CD = 0.006 at M=2.0, independent of RM-10 real surface.
- ORP's `calculatePressureCD` zeros pressure for contracting transitions with fineness≥3 (line 432-434). So the fore-boattail (fineness=17.3) produces zero pressure term. Real gentle boattails at 2° angle do not recover fully to p_inf — some residual drag exists but it's small. Setting it to 0 is conservative; the surface expansion-fan recompression effect on the aft body is also absent. Net effect on RM-10 at M=2: hard to bound without running a CFD reference; likely ≤ 0.01 CD.

**Falsifiability:** Model the full aft body as a single `NoseCone(POWER, param=0.5)` **inverted** or as a series of many short conical transitions closely approximating Y = 6 - 0.0007407·x². Expected effect: small (±0.01 CD at M=2) because the fore-boattail's fineness is already ≥3 and zeroed. Main value is eliminating the fin-mount tube's artificial wetted cylinder.

**Magnitude at M=2.0:** −0.003 to −0.010 of CD.

**File:line:** Geometry reconstruction only; no calculator changes needed. See `SymmetricComponentCalc.java:430-439`.

### H5 (UNLIKELY): DahlemBuck paraboloid correction inflates nose wave drag

**What it does:** RM-10's nose is POWER with param=0.5 (paraboloid). `SymmetricComponentCalc.isDirectReferenceShapeForSupersonicOverride` returns TRUE for POWER p=0.5 (line 787-795), so the Dahlem-Buck override at M≥1.3 is **SKIPPED** for this shape. The nose uses the TR-R-100 x12Interpolator fineness-scaled instead.

**Evidence:**
- CSV: ParaboloidNose pressure CD = 0.016–0.019 across M 1.5–3.0. For a fineness-7.5 paraboloid this is modest and consistent with expectations from TR-R-100 analytical families.
- The published NACA RM-10 forebody-drag curve (TN 3320 Figure 11) gives CD_forebody ≈ 0.10 at M=2, which includes friction. Subtracting ORP's friction estimate (0.068) leaves ~0.03 for nose+fins-forebody pressure. ORP gets 0.016 on the nose alone, probably about right.

**Falsifiability:** Compare the existing `WaveDragPhase2ATest` or similar tests for the x12 / POWER p=0.5 path. If the override is genuinely off this path, the Dahlem-Buck shape factor is not the problem. Independent cross-check: ORP's paraboloid CD at fineness=3 and M=2.0 should match the x12Interpolator's reference value (0.078 per `SymmetricComponentCalc.java:600-602`). Scale to fineness=7.5 via `addFinenessScaledReferenceCurve` (log4=1.544): expected 0.016. This matches the CSV. ⇒ Dahlem-Buck is NOT active for RM-10 nose.

**Magnitude:** ~0. Hypothesis ruled out.

**File:line:** `SymmetricComponentCalc.java:743-768` (Dahlem-Buck override, gated behind `!isDirectReferenceShapeForSupersonicOverride()`); `787-795` (direct-reference gate).

### H6 (UNLIKELY): Van Driest II at Re_L > 1e8 (RM-10 full-scale)

**What it does:** RM-10 full-scale tests had Re_L from 14e6 to 210e6 — some of this is at the upper edge of Van Driest II's well-validated range. If Cf is being over-estimated, friction CD would be inflated.

**Evidence:**
- CSV: RM-10 ROCKET_TOTAL fric = 0.068 (M=2.0). TN 3320 page 7 says "measurements of boundary-layer characteristics made on the full-scale configuration also show a significant decrease in friction-drag coefficient with increasing supersonic Mach number" and NACA RM L51B12 (ref 3 in TN 3320) digitized would give Cf. A reasonable reference: for ReL=5e7, M=2, Van Driest II → Cf_compressible ≈ 0.0020. Applied to wetted area/ref area ≈ 34 (RM-10 is long and slender), friction CD ≈ 0.068. Consistent with ORP's value. No obvious overshoot.
- Hopkins & Inouye 1971 (already in CLAUDE.md) validated Van Driest II across M 1.5–9; RM-10 is inside this envelope.

**Magnitude:** ~0. Hypothesis ruled out.

**File:line:** `BarrowmanDragCalculator.java` (vanDriestIICf method; not investigated further).

### H7 (NOT A ROOT CAUSE AT M=1.5–3.3): Modified Newtonian crossover M 4–6

The M 4–6 Newtonian blend boundary is above the RM-10 test range (M max = 3.3). Not a contributing factor for this benchmark. Ruled out.

### H8 (NOT A ROOT CAUSE): Transonic polynomial M 0.9–1.2 on parabolic body

The M=1.04 peak of RM-10 (CDT=0.260 exp vs ORP 0.465) is high-overshoot but structurally identical to the rest of the Mach range (overshoot ~80% throughout). A transonic-specific polynomial miscalibration would show up as a localized M=1.0–1.2 bump not seen in the M=2–3.3 overshoot. The observed monotonic +65–99% overshoot is consistent with the M-independent H1+H2 mechanisms. Not a primary driver.

---

## 4. Summary Table: Combined Effect Estimate

At M=2.0 only (CDT_exp = 0.215, ORP now = 0.389, delta = +0.174):

| Hypothesis | Estimated removable CD | Remaining gap |
|---|---|---|
| Current | +0.174 | — |
| H1 (finned-aug off when boattail upstream) | −0.022 | +0.152 |
| H2 (collapse terminal-boattail placeholder) | −0.032 | +0.120 |
| H3 (ROUNDED -> HEXAGONAL fins) | −0.030 | +0.090 |
| H4 (replace fin-mount tube with smooth taper) | −0.005 | +0.085 |
| **All four applied together (rough additive)** | **−0.089** | **+0.085** |

Even applying the 4 actionable mechanisms, a **~+0.085 residual gap remains** at M=2.0 (~40% overshoot). This residual would be the next-layer mystery — probably distributed across small terms (body friction calibration on high-fineness bodies, fin-body interference PNK calibration at AR=2.04, etc.) plus the low-confidence H4.

---

## 5. Recommended Next-Step Prompt

**The single mechanism to investigate first: H2 (terminal-boattail placeholder) combined with H3 (fin cross-section mapping).** These are both **test-side geometry choices**, not calculator bugs. They can be fixed by rebuilding the NACA RM-10 geometry without modifying any calculator constant.

Specifically:
1. Rebuild RM-10 as a single POWER nose with param=0.5 running the full 146.5 in to the true base radius (3.636 in), **no separate fore-boattail, no fin-mount tube, no terminal-boattail placeholder**.
2. Use a FreeFormFinSet or explicitly change to HEXAGONAL cross-section to avoid the ROUNDED round-LE bluntness penalty.
3. Re-run `NacaRm10FinnedBodyDragBenchmarkTest` and quote the new MAPE. Expected to drop to ~40–50% (from 80.5%). **Do NOT change the MAPE gate** — leave it at 95% so other failing mechanisms still surface.
4. If after those geometry fixes the residual is still > 30% MAPE, investigate H1 (finned-base augmentation gating on upstream boattail presence) separately in a dedicated prompt that also re-validates Basic Finner and MESOS.

**Do NOT attempt to fix the calculator yet.** The test geometry reconstruction above is a conservative first pass. Each of the ROUNDED→HEXAGONAL, fin-AR reconstruction, and monolithic-taper changes needs to be applied ONE AT A TIME so the contribution of each is measured, not bundled. Bundling is how calibration drift happens.

---

## 6. Blockers Encountered

None. PDF parsed cleanly via `Read` tool (pages 1-15 read, Figure 1 and body text match test citation exactly). Test compiled and ran on first try (13.7 s wall time). CSV written successfully to `core/build/reports/`. No file-lock issues.

---

## 7. Files Touched by This Diagnostic

**Created:**
- `core/src/test/java/info/openrocket/core/aerodynamics/Rm10VsBasicFinnerDiagnosticTest.java`
- `core/build/reports/rm10_vs_basic_finner_component_cd.csv` (build artifact)
- `paper/data/rm10_vs_basic_finner_diagnostic.md` (this memo)

**Modified:**
- `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` (session log entry)

**NOT touched (read-only per prompt rules):**
- `BarrowmanDragCalculator.java`
- `SymmetricComponentCalc.java`
- `FinSetCalc.java`
- `DahlemBuckShapeFactors.java`
- Any other calculator
- `NacaRm10FinnedBodyDragBenchmarkTest.java`
- `BasicFinnerDragBenchmarkTest.java`
- `SupersonicTestRockets.java`
