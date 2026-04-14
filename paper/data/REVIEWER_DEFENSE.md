# Reviewer Defense

This file is the short answer key for likely reviewer questions. It is deliberately strict: if a claim is not defensible from published data or exported artifacts, it should not be in the manuscript body as a validated result.

## What we can defend now

- The atmosphere-property upgrades are source-anchored and reproducible.
- The core gas-dynamics solvers are quantitatively validated against published tables and reference cases.
- The hypersonic `Cp,max` building block is validated to grade A via independent derivation from NACA 1135 Tables I+II (15-point comparison, machine-epsilon agreement).
- The repo can regenerate paper-facing validation artifacts from tests.
- The repo now includes a quantitative sensitivity appendix for the thesis tuning constants.
- `NACA RM A52H28` is now a real external zero-lift foredrag benchmark with stored digitized points, Reynolds-matched exports, and quantitative error metrics. This also closes the cone/ogive zero-lift drag trends row (same nose shapes at L/D=3).
- `AGARD-B` is now a secondary external benchmark (grade A with transition-sensitivity caveats), supported by NACA TN 3393 as an independent base-drag anchor.

## What we should not claim yet

- Broad predictive accuracy for full-vehicle `Cd(M)` from a single drag benchmark alone.
- Any claim that `NASA TM X-653` predictive accuracy extends above M 3.0; the M=3.0 fin-body interference anomaly is documented but unresolved.
- Predictive static stability accuracy without external `Cn(alpha)`, `Cm(alpha)`, or `x_CP(M)` data.
- Predictive dynamic stability or 6-DOF fidelity while pitch damping and related terms remain tuned.
- Broad Mach 10+ aerodynamic accuracy for full bodies.

## Standard reviewer questions

1. Which parts of the model are validated against independent published data?
Answer: normal shock, oblique shock, Prandtl-Meyer, Taylor-Maccoll cone flow, speed of sound, Sutherland viscosity, and `Cp,max` building blocks. These are exported in `paper/data`.

2. Which parts are only internally consistent, not externally closed?
Answer: `ShockGeometry` integration-layer behavior, continuity / hardening sweeps, and native OR geometry drag sweeps.

3. Which parts are still calibrated heuristics?
Answer: the static- and dynamic-stability tuning terms listed in the thesis table, including the pitch-damping multiplier and several hardening guards.

4. Do we use RASAero as validation truth?
Answer: no. RASAero comparisons are supporting diagnostics only. Any term calibrated partly from RASAero output is not treated as externally validated.

5. Why is AGARD-B presented as a secondary benchmark?
Answer: AGARD-B provides external Cd(M) comparison with AEDC-TR-70-100 tunnel data, but the component split depends strongly on boundary-layer transition state. With NACA TN 3393 now independently closing base drag (turbulent BL), AGARD-B serves as a complementary transonic benchmark rather than the sole drag-split anchor. The exposed-vs-gross reference-area issue is closed; the remaining sensitivity is in friction/transition modeling.

6. What does NACA RM A52H28 prove right now?
Answer: it proves the repo can run a real external zero-lift drag benchmark on exact body-of-revolution geometries with a passing first-pass aggregate MAE. It also shows the remaining model gaps clearly: the residual error is now concentrated mainly in the cone and quarter-power families around `M = 1.24-1.99`, not as a general collapse across all shapes.

7. What is the strongest current publication core?
Answer: the open-source gas-dynamics and cone-flow backbone, plus atmospheric-property upgrades, all tied to published references and reproducible artifacts.

8. What is the minimum extra evidence required for a paper now?
Answer: finish one independent transonic / base-drag benchmark on a matched coefficient basis and one exact external `Cn(alpha)` / CP benchmark with real digitized ordinates. `NACA TN 3393` and `NASA TM X-653` now have fixture/export support in the repo, but the source-data side is still incomplete.

9. How should the final manuscript describe the advanced 6-DOF features?
Answer: as simulation extensions and robustness work, unless external dynamic-stability data are added.

10. How should Mach-range claims be worded today?
Answer: the framework extends analytically into hypersonic regimes, but external validation is currently strongest for the analytical building blocks and for the low- to mid-supersonic component / vehicle cases that are closed with data.

11. What happens if the empirical constants are moved?
Answer: see `tuned_parameter_sensitivity.csv` and `tuned_parameter_sensitivity.md`. The current package shows the sign, scale, and monotonicity of the main aerodynamic heuristics under representative cases; they should still be described as sensitivity-bounded, not externally closed.

12. Are the numerical guard thresholds part of the aerodynamic validation claim?
Answer: no. They belong to software robustness, not aerodynamic accuracy. Keep them in a separate appendix and cite `NUMERICAL_GUARD_AUDIT.md` if a reviewer asks.
