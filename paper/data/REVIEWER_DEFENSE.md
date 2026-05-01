# Reviewer Defense

This file is the short answer key for likely reviewer questions. It is deliberately strict: if a claim is not defensible from published data or exported artifacts, it should not be in the manuscript body as a validated result.

## What we can defend now (Updated 2026-04-14)

- The atmosphere-property upgrades are source-anchored and reproducible.
- The core gas-dynamics solvers are quantitatively validated against published tables and reference cases.
- The hypersonic `Cp,max` building block is source-anchored.
- The repo can regenerate paper-facing validation artifacts from tests.
- The repo now includes a quantitative sensitivity appendix for the thesis tuning constants.
- `NACA RM A52H28` is a real external zero-lift foredrag benchmark with stored digitized points, Reynolds-matched exports, and quantitative error metrics (MAE = 0.0147). Residual cone and quarter-power biases are isolated and documented.
- **`NACA TN 3393` is now an independent base-drag benchmark** with figure-digitized Cpb on matched coefficient basis. Turbulent BL agreement is confirmed; laminar divergence is expected and documented.
- **`NASA TM X-653` is now an external static-stability benchmark** with digitized CNa and xCP/d for the NSCFB finned configuration (M 0.6-3.0). Agreement is good subsonic through M~2; M=3.0 anomaly is flagged.
- **All 10 numerical guards are proven inactive** in the validated flight envelope via runtime instrumentation (`GuardInvarianceTest.java`, 72-point sweep).

## What we should not claim yet

- Broad predictive accuracy for full-vehicle `Cd(M)` above what the three external benchmarks cover.
- Predictive static stability above M~3.0 (fin-body interference anomaly documented).
- Predictive dynamic stability or 6-DOF fidelity while pitch damping and related terms remain tuned.
- Broad Mach 10+ aerodynamic accuracy for full bodies.
- Laminar base-drag predictions (model is turbulent-calibrated).

## Standard reviewer questions

1. Which parts of the model are validated against independent published data?
Answer: normal shock, oblique shock, Prandtl-Meyer, Taylor-Maccoll cone flow, speed of sound, Sutherland viscosity, and `Cp,max` building blocks. These are exported in `paper/data`.

2. Which parts are only internally consistent, not externally closed?
Answer: `ShockGeometry` integration-layer behavior, continuity / hardening sweeps, and native OR geometry drag sweeps.

3. Which parts are still calibrated heuristics?
Answer: the static- and dynamic-stability tuning terms listed in the thesis table, including the pitch-damping multiplier and several hardening guards.

4. Do we use RASAero as validation truth?
Answer: no. RASAero comparisons are supporting diagnostics only. Any term calibrated partly from RASAero output is not treated as externally validated.

5. Why is AGARD-B not enough by itself?
Answer: because AGARD-B is transition-sensitive and therefore a diagnostic, not an anchor. The repo now has independent base-drag closure via NACA TN 3393, so AGARD-B is supplementary.

6. What does NACA RM A52H28 prove right now?
Answer: it proves the model reproduces zero-lift foredrag trends across 5 nose-shape families (MAE = 0.0147, MAPE = 12.5%). The remaining cone and quarter-power biases are isolated to specific model limitations (transonic pressure polynomial for cone, TR-R-100 table calibration for quarter-power), not general model failure. See `a52h28_bias_isolation.md`.

7. What is the strongest current publication core?
Answer: three independent external benchmarks (A52H28 foredrag, TN 3393 base drag, TM X-653 static stability), plus source-anchored gas-dynamics and atmospheric-property building blocks, plus proven guard invariance. All tied to published references with reproducible artifacts.

8. What is the minimum extra evidence required for a paper now?
Answer: the three manuscript gates (zero-lift drag, base drag, static stability) are closed. The remaining gaps are dynamic stability heuristics (pitch damping, Magnus, transonic Cmq) which should be presented as appendix material, and the M=3.0 fin-body interference anomaly which should be flagged as a known limitation.

9. How should the final manuscript describe the advanced 6-DOF features?
Answer: as simulation extensions and robustness work, unless external dynamic-stability data are added.

10. How should Mach-range claims be worded today?
Answer: the framework extends analytically into hypersonic regimes, but external validation is currently strongest for the analytical building blocks and for the low- to mid-supersonic component / vehicle cases that are closed with data.

11. What happens if the empirical constants are moved?
Answer: see `tuned_parameter_sensitivity.csv` and `tuned_parameter_sensitivity.md`. The current package shows the sign, scale, and monotonicity of the main aerodynamic heuristics under representative cases; they should still be described as sensitivity-bounded, not externally closed.

12. Are the numerical guard thresholds part of the aerodynamic validation claim?
Answer: no. They belong to software robustness, not aerodynamic accuracy. All 10 guards are now proven inactive in the validated envelope (M 0.3-5.0, AoA 0-10°) via runtime instrumentation in `GuardInvarianceTest.java`. See `guard_tuned_invariance.md` and `NUMERICAL_GUARD_AUDIT.md`.

13. What are the known model limitations?
Answer: (a) Cone transonic pressure drag is overpredicted at M 1.24-1.99 due to shape-agnostic transonic polynomial. (b) Quarter-power shape has a flat ~10-15% overprediction from TR-R-100 table calibration. (c) Base drag model is turbulent-calibrated; laminar BL data diverges as expected. (d) M=3.0 fin-body interference produces a CNa/xCP anomaly not present in experiment. All are documented with root causes in the repo.
