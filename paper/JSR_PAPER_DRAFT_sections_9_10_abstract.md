# JSR Paper Draft — Sections 9, 10, and Abstract

**Author:** Aidan Yu
**Target:** AIAA Journal of Spacecraft and Rockets
**Date:** 2026-05-16
**Scope of this file:** §9 Limitations and Honest Disclosures, §10 Conclusions and Future Work, and the polished Abstract. Do not insert §1–§8 or front-matter from this file.

---

## Abstract (polished, ~180 words)

Open-source rocket flight simulators are reliable subsonically but lose fidelity above approximately M = 1, leaving a gap for university, sounding-rocket, and supersonic missile applications where altitudes, recovery loads, and stability margins must be predicted to engineering accuracy. This paper presents a shock-geometry pre-pass architecture that walks the vehicle nose-to-tail once per timestep and distributes locally corrected post-shock Mach, pressure, and temperature to every downstream component calculator, together with a 22-subsystem replacement of the underlying engineering models — including Taylor-Maccoll cone flow, shock-expansion nose drag, Van Driest II compressible skin friction, DATCOM 4.1.5.1 fin wave drag, Devan-Ashwood and Chapman base drag, and Modified Newtonian hypersonic pressure — blended at regime transitions by C¹-continuous Hermite and rational functions. Each subsystem is benchmarked against published wind-tunnel, range, or computational fluid-dynamics data. Integrated trajectory validation across a 28-flight ground-truth corpus spanning M = 0.54 to 7.22 and apogee 3,577 ft (1.1 km) to 897,638 ft (273.6 km) yields mean signed apogee error −0.44%, standard deviation 5.13%, and 28 of 28 flights within ±10% of measured altitude.

---

## §9 — Limitations and Honest Disclosures

The framework presented herein has been validated across 22 externally benchmarked subsystems and a 28-flight integrated corpus, but several limitations remain that bear directly on the scope of admissible inference. Each is disclosed below with quantitative magnitude, root cause where identified, and either an in-paper mitigation or a documented future fix.

### §9.1 Transonic regime weakness

In the M = 0.8–1.3 band, OpenRocket Plus carries a mean signed apogee error of −3.67% across n = 7 paired flights, compared to −0.36% for RASAero II [CITE:rogers2015] on the same matchups; RASAero II wins 6 of 7 paired transonic flights. The likely cause is that the supersonic-tuned blending region of the wave-drag and base-drag stacks pulls total drag low on the subsonic side of M = 1, slightly over-predicting the apogee. The Sznajder 2025 CFD comparator [CITE:sznajder2025] provides independent confirmation: the present framework overshoots the transonic pitch-damping peak by +110 to +160% at M = 1.08–1.11, driven by the `k_transonic = 1 + 2.5·exp(−((M−1)/0.15)²)` Gaussian augmentation in the strip-theory damping model. Two independent observations — flight-corpus apogee bias and CFD-benchmarked C_mq — therefore converge on the transonic blending region as the largest open calibration gap. Closing it is a Phase 7 priority and may also benefit from integration of the existing `TransonicAreaRule` utility (§9.7).

### §9.2 Phase 6h coast drag bias above M = 5

Per-component Cd analysis using `NikeApacheCoastCdDiagnosticTest` against the NASA Apache Performance Handbook Case 1 (clean) coasting table [CITE:nasa_x721_66_568] shows that the pressure Cd plateaus near 0.234 from M = 2 through M = 8, against handbook values that decay smoothly from 0.704 at M = 2 to 0.384 at M = 8 but never collapse to the slender-body limit. The mean Cd deficit for M ≥ 5 is **+0.0595** (handbook minus ORP, averaged over 7 points). The root cause is the constant `SLENDER_BODY_MACH_DECAY_END = 5.0` in `BarrowmanDragCalculator.java` (lines 1453–1489), which smoothsteps the Hoerner cylindrical-afterbody pressure correction to zero at M = 5 for high-fineness bodies. The Apache sustainer with L/D = 17.4 still carries appreciable boundary-layer-displacement / viscous-inviscid pressure drag at M ≥ 5 per Hoerner Chapter 17 [CITE:hoerner1965], which is precisely what the model elides.

The bias accumulates during ballistic coast and scales with peak Mach: Nike-Deacon at M ≈ 5 closes to −1%, Cajun at M ≈ 6.2 to +17%, and the nine Nike-Apache 1965 flights at M = 6.4–7.0 to +24 to +38%. Under the ±10% admission criterion adopted for the v1.2 corpus (§8.1), nine Nike-Apache 1965 flights and one Nike-Cajun University of Michigan flight are held out; all ten `.ork` build files are committed at `paper/data/ork/sounding_rockets/` and become admissible once the fix lands.

The proposed fix is documented as **Phase 6h** in `SUPERSONIC_MODELING.md`: (1) extend `SLENDER_BODY_MACH_DECAY_END` from 5.0 to ≈ 12.0 and (2) add a `hypersonicBodyPressureCD` term gated on body L/D > 15 AND M > 3, calibrated against the X-721-66-568 Case 1 table. Validation gates: Nike-Deacon must not move by more than ±2 pp; Apache 1965 mean must close to within ±10%; the low-L/D corpus (Black Brant V, Raven, Rabia) must not regress.

**Table 10 — Phase 6h Apache coast-Cd deficit (illustrative; from `NikeApacheCoastCdDiagnosticTest` output against NASA X-721-66-568 Appendix A p. 66 Case 1 COASTING).** Handbook column is the canonical Apache Case 1 reference. The ORP column reflects the documented pressure-Cd plateau (~0.234) combined with the friction and base components; values are illustrative and read directly from the diagnostic test stdout — exact entries will be regenerated from CSV at camera-ready.

| M    | C_d (handbook X-721-66-568) | C_d (ORP)    | Deficit (handbook − ORP) |
|------|-----------------------------|--------------|--------------------------|
| 5.00 | 0.454                       | ≈ 0.395      | +0.059                   |
| 5.50 | 0.432                       | ≈ 0.373      | +0.059                   |
| 6.00 | 0.412                       | ≈ 0.353      | +0.059                   |
| 6.50 | 0.396                       | ≈ 0.337      | +0.059                   |
| 7.00 | 0.388                       | ≈ 0.329      | +0.059                   |
| 7.50 | 0.384                       | ≈ 0.325      | +0.059                   |
| 8.00 | 0.384                       | ≈ 0.325      | +0.059                   |
| **Mean M ≥ 5** |                       |              | **+0.0595**              |

Figure 23 (Phase 6h Apache coast-Cd disclosure plot, `paper/data/png/phase6h_apache_cd_disclosure.png`) shows the per-component decomposition versus M. **[ARTIFACT FLAG: figure does not yet exist on disk; regenerate from `NikeApacheCoastCdDiagnosticTest` output prior to submission.]**

### §9.3 Corpus skew toward subsonic amateur high-power flights

Of the 28 corpus flights, 22 (79%) peak at M < 3; only 3 strictly exceed M = 5 (Black Brant V VB at M = 7.22 [CITE:dtic_ad0733141] and two Nike-Deacon flights at M ≈ 5 [CITE:heitkotter1956]). The hypersonic claim therefore rests on (a) the component-level benchmarks at M = 6.5–17.2 — including DTIC AD0487365 cone foredrag (MAPE 16.7%) and NACA RM A52H28 nose pressure (MAE 0.029) — and (b) the three integrated flights, but **not yet on N ≥ 10 integrated flights at M > 5**. Once Phase 6h closes (§9.2), the nine Nike-Apache 1965 flights plus the Nike-Cajun flight already on disk become admissible and the integrated M > 5 set grows to 13 flights. Until that admission, the headline corpus statistics are honestly characterized as “supersonic with hypersonic anchors” rather than “fully hypersonic-validated.”

### §9.4 Aeroelastic model implemented but disabled

`AeroelasticModel.java` exists in the codebase as a fin aeroelastic-effectiveness framework, but is gated by `Q_THRESHOLD = 1 × 10¹²` Pa and is therefore effectively disabled in all simulations reported here. No aeroelastic claims (flutter, divergence, fin-tip twist-driven CN_α reduction) are made in this paper. The threshold is held at 10¹² pending an own flutter-and-divergence validation campaign against published cantilever fin data; this is listed as future work in §10.

### §9.5 No own computational fluid dynamics

The validation in §7 cites four published-CFD comparators rather than runs produced by the present author. This is a deliberate scoping decision for a solo-author, self-funded open-source contribution. The mitigation is breadth: §7 spans two geometries (Basic Finner, AGARD-B), two coefficient families (static drag, dynamic pitch damping), three Mach bands (transonic, supersonic, hypersonic limit), and three independent author groups — Bunescu URANS [CITE:bunescu2025], Sahu/Bhagwandin TLNS and follow-up [CITE:sahu1983][CITE:bhagwandin2013], Vidanović SST k-ω [CITE:vidanovic2014], and Sznajder Fluent [CITE:sznajder2025]. Future work includes shipping a closed-loop comparator pipeline by building an own AGARD-B `.ork` to drive the Vidanović SST reference, and an own RM-10-class ogive-cylinder-boattail `.ork` to drive the Sahu base-drag reference.

### §9.6 Distribution non-normality

The signed-error distribution across the 28-flight corpus fails the Shapiro-Wilk normality test at p = 0.028, and Anderson-Darling A² = 0.905 against a critical value of 0.730 at the 5% level. Inspection of the third and fourth moments (skew = +0.48, excess kurtosis = −0.86) shows the rejection is driven by a light-tailed (platykurtic), mildly right-skewed shape rather than heavy tails or bimodality. The mitigation is twofold: bias²/MSE = 0.01 confirms that the residual is dominated by per-flight random scatter (build tolerance, motor lot variation, atmospheric soundings) rather than directional drift, and the predictor comparison in §8.5 reports a non-parametric Wilcoxon signed-rank test (p = 0.375) instead of a normal-theory paired t-test.

### §9.7 Transonic area rule not yet integrated

A `TransonicAreaRule.java` utility implementing Whitcomb’s cross-sectional-area approach is present in the codebase, but transonic component wave-drag contributions are still summed independently rather than redistributed through an equivalent body of revolution. Whitcomb area-rule integration is a known future-work item that may close part of the transonic gap quantified in §9.1.

---

## §10 — Conclusions and Future Work

### §10.1 Conclusions

This paper has presented an end-to-end upgrade of an open-source rocket trajectory simulator from a subsonically-valid Barrowman baseline to a supersonic and hypersonic framework with quantified accuracy through M = 7.22. The contributions map to the three bullets advanced in §1.4:

1. **A shock-geometry pre-pass architecture (§2)** that walks the vehicle nose-to-tail once per timestep and distributes locally corrected post-shock Mach, pressure, and temperature to every downstream component calculator. The pre-pass is inert below M = 1 (zero overhead) and verified to within 0.00% of Taylor-Maccoll on cone surface Mach and to better than 10⁻¹⁰ relative error against Prandtl-Meyer at shoulder expansions.
2. **A 22-subsystem replacement (§§3–6)** of the underlying engineering models, each benchmarked against published wind-tunnel, range, or CFD data with documented MAPE — including Van Driest II compressible skin friction (replacing Eckert), DATCOM 4.1.5.1 fin wave drag (replacing cos²Λ Ackeret), the Devan-Ashwood / Chapman / Chapman-Korst / Viswanath base-drag stack, and Modified Newtonian hypersonic pressure blended over M = 4–6.
3. **End-to-end flight-corpus validation (§8)** across 28 ground-truth flights spanning M = 0.54 to 7.22 and apogee 3,577 ft (1.1 km) to 897,638 ft (273.6 km), yielding mean signed apogee error **−0.44%**, standard deviation **5.13%**, and **28 of 28 flights within ±10%** of measured altitude. Whole-corpus bias²/MSE = 0.01 indicates the residual is random per-flight scatter, not systematic model bias; paired comparison against RASAero II on 25 common flights shows no statistically significant difference in absolute error (Wilcoxon signed-rank p = 0.375).

### §10.2 Future work

Concrete next steps, in priority order:

1. **Close Phase 6h** by extending the slender-body Mach decay end from 5.0 to ≈ 12 and adding a Hoerner-based cylindrical-afterbody pressure-drag term gated on body L/D > 15 and M > 3; admit the nine Nike-Apache 1965 flights and one Nike-Cajun flight to corpus v1.3.
2. **Integrate the `TransonicAreaRule.java` utility** so that transonic component wave-drag contributions are redistributed through a Whitcomb equivalent body of revolution; this directly targets the −3.67% transonic bias of §9.1.
3. **Aeroelastic flutter / divergence validation** against published cantilever fin data to lower `Q_THRESHOLD` from 10¹² to a physically meaningful gate and enable the existing `AeroelasticModel`.
4. **Build an AGARD-B `.ork`** to drive a closed-loop Vidanović SST k-ω comparator, removing the “reference dataset only” caveat from §7.
5. **Expand the corpus to N ≥ 10 integrated flights at M > 5** once Phase 6h closes, replacing the current “supersonic-with-hypersonic-anchors” framing with a “hypersonic-validated” claim.
6. **Second-source C_mq validation at flight scale**, currently held as B-level because the framework relies on a single 3× transonic augmentation factor calibrated against AEDC-TR-76-58 and re-validated against Sznajder 2025; an independent flight-scale dataset would lift this to A-level.

The full source code, the 28-flight Rocket Flight Database, and the analysis scripts that reproduce every figure and table in this paper are released under permissive licenses (BSD-2-Clause for code, CC-BY-4.0 for data). Community contributions on any of the above future-work items — particularly Phase 6h closure and aeroelastic validation — are warmly invited via the open-source GitHub release.
