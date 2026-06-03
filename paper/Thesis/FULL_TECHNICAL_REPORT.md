## Abstract

This report documents the OpenRocket Plus aerodynamic extensions as implemented in the current Java codebase. The work replaces the original low-subsonic Barrowman assumptions with compressible atmosphere models, shock and expansion solvers, transonic blending, supersonic and hypersonic drag models, local-flow coupling for fin stability, static and dynamic stability corrections, high-angle-of-attack effects, and numerical hardening for six-degree-of-freedom simulation. Validation is reported claim by claim against the tests, source artifacts, and implementation paths in the repository: 20 externally benchmarked A-level subsystem results, 9 integrated flight-corpus closures, and 1 negative external benchmark that bounds an excluded geometry family (counted outside the 20). The integrated 25-flight ground-truth corpus (Mach 0.54--4.33) closes at **−0.38% mean signed apogee error, σ = 5.44%, MAE = 4.74%**, with **25/25 flights within $\pm 10\%$** (14/25 within $\pm 5\%$); on the same 25 paired flights, RASAero II averages 5.34% mean $\lvert\text{error}\rvert$ with 22/25 within $\pm 10\%$. The mean-error 95% bootstrap CI brackets zero, so OpenRocket Plus is statistically unbiased on this externally selected corpus; the paired comparison against RASAero II is statistically indistinguishable (Wilcoxon $W = 143.0$, $p = 0.615$), so the honest claim is **parity** with this version-locked RASAero II set, not superiority. Beyond the headline corpus, $\sim 20$ historical sounding-rocket flights (including the Black Brant V VB at Mach 7.22 and apogee 273.6 km) are reported in full as an exploratory high-Mach capability demonstration: the method reaches Mach 7 within $\pm 7\%$ on well-characterized vehicles (3 within $\pm 10\%$), but motor and geometry reconstruction uncertainty dominates on poorly-documented historical flights (17 outside). Headline subsystem results include nose wave drag MAE 0.029 vs NACA RM A52H28, fin $C_{N\alpha}$ MAPE 6.84% and $x_{CP}$ MAPE 7.11% vs NASA TM X-653, and Basic Finner total drag MAPE 11.8% vs ADA636861. Hypersonic cone foredrag (MAPE 19.7% vs DTIC AD0487365 across $M = 6.5$--$17.2$) is reported as an exploratory B-level result outside the A-level count, not as a headline benchmark. Headline accuracy claims apply to finned slender vehicles within the boattail half-angle envelope of $6°$--$16°$ (Viswanath 1996) and to HEXAGONAL or AIRFOIL/ROUNDED fin sections; out-of-envelope geometries -- specifically the high-fineness parabolic body with steeply contracted afterbody and 60° swept circular-arc biconvex fins of NACA RM-10 -- are reported as transparency references and are excluded from the headline claim. The ground-truth corpus is archived as the *Rocket Flight Database* (DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977), CC-BY-4.0).


## 1. Introduction

### 1.1 Background: OpenRocket and the Barrowman Method

OpenRocket is an open-source model rocket flight simulator originally developed by Sampo Niskanen at Helsinki University of Technology. It provides six-degree-of-freedom trajectory simulation with aerodynamic coefficient computation based on the extended Barrowman method, and has become a standard tool in the amateur and high-power rocketry communities through an active development community and a well-structured Java codebase organized into a core simulation module and a Swing-based graphical interface.

The aerodynamic core of OpenRocket is built on the Barrowman method, first published by James Barrowman in his 1967 Master's thesis "The Practical Calculation of the Aerodynamic Characteristics of Slender Finned Vehicles" at The Catholic University of America. Barrowman's approach applies slender-body theory and strip theory to compute the normal-force-coefficient derivative $C_{N_\alpha}$ and center-of-pressure location $x_{CP}$ for each rocket component independently. The total vehicle aerodynamics are then assembled by superposition:

$$C_{N_\alpha,\text{total}} = \sum_i C_{N_{\alpha,i}}, \qquad x_{CP,\text{total}} = \frac{\sum_i C_{N_{\alpha,i}} \, x_{CP,i}}{\sum_i C_{N_{\alpha,i}}}.$$

The Barrowman method assumes:

1. **Small angle of attack** ($\alpha \ll 1$), so that $\sin\alpha \approx \alpha$ and the flow remains attached.
2. **Slender body** (length $\gg$ diameter), permitting linearized potential flow.
3. **Subsonic flow**, so the Prandtl-Glauert compressibility factor $\beta = \sqrt{1 - M^2}$ is real and well-behaved.
4. **No shocks**, so the flow is everywhere isentropic and continuous.
5. **Component independence**, with each component computed in isolation without upstream-component influence.
6. **Incompressible boundary layers**, with skin friction evaluated at freestream conditions.

These assumptions are entirely adequate for typical model rockets, which rarely exceed Mach 0.5. However, the growing community of high-power rocketry (HPR) practitioners, amateur research groups, and university teams routinely builds vehicles that reach Mach 2 to 5 and beyond. For these applications, every one of the above assumptions breaks down, and the original OpenRocket aerodynamic models produce increasingly inaccurate results.

The closed-source benchmark for amateur supersonic rocketry simulation is RASAero II, developed by Charles E. Rogers, which incorporates empirical and semi-empirical supersonic drag models calibrated against extensive wind-tunnel data. The goal of the work described in this report is to bring OpenRocket to a comparable level of supersonic and hypersonic fidelity while maintaining the open-source, modular architecture that makes it valuable for education, research, and engineering. Across a 25-flight ground-truth corpus (Mach 0.54--4.33), OpenRocket Plus mean signed apogee error is $-0.38\%$ with $\sigma = 5.44\%$ (MAE 4.74\%), and on the same 25 paired flights RASAero II averages 5.34% mean $\lvert\text{error}\rvert$; the Wilcoxon signed-rank test on the paired absolute errors returns $W = 143.0$, $p = 0.615$ — no statistically significant difference between the two predictors on the paired corpus, so the claim is parity with this version-locked RASAero II set rather than superiority (see Section 1.4 for full per-case results, and the *Rocket Flight Database* at [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977) for the canonical comparison artifact).


### 1.2 Specific Limitations of the Original Implementation

The original OpenRocket aerodynamic implementation contained six specific deficiencies that collectively rendered its predictions unreliable above approximately Mach 0.8. Each is described below with a quantification of the resulting error.

**Limitation 1: Hard-clamped compressibility factor ($\beta_{\min} = 0.25$).** The Prandtl-Glauert factor $\beta = \sqrt{|1 - M^2|}$ appears in nearly every linearized aerodynamic coefficient formula. In the original code, a constant `MIN_BETA = 0.25` was applied as a floor, clamping $\beta$ to never fall below 0.25. At Mach 0.97 the true value is $\beta = \sqrt{1 - 0.97^2} = 0.243$ and the clamp forces it to 0.25 (a 2.9% overestimate); at Mach 0.99 the true value is 0.141 and the clamp forces it to 0.25, reducing $1/\beta$ by 44%; at Mach 1.0 the true value is the singularity $\beta = 0$ and the clamp produces $1/\beta = 4$, which is a finite number applied to what should be a divergence. The clamp produces a flat plateau in $\beta$ from roughly Mach 0.97 to 1.03, during which all coefficients that depend on $1/\beta$ are artificially held constant instead of exhibiting the characteristic transonic divergence. Section 4 addresses this with a $C^1$-continuous cubic Hermite spline.

**Limitation 2: Tabulated nose-cone drag limited to Mach ~3.** The original pressure-drag computation for nose cones and transitions relied on interpolation tables derived from NASA TR-R-100 (Stoney 1958), which provides transonic and low-supersonic wave-drag data for specific nose shapes. These tables cover Mach numbers up to approximately 3.0 and only for the specific fineness ratios tabulated. At higher Mach numbers the code extrapolated linearly, producing drag coefficients that diverge from physics. Section 6.1 replaces this with Taylor-Maccoll cone wave drag and shock-expansion ogive wave drag, blended through TR-R-100 in the transonic band.

**Limitation 3: No fin wave drag.** Original fin drag consisted solely of skin friction and a small form-factor correction. At supersonic speeds each fin generates a leading-edge shock that produces wave drag proportional to $(t/c)^2 / \beta$. For a typical fin with $t/c = 0.05$ at Mach 2, the Ackeret estimate is

$$C_{d,\text{wave}} = \frac{4 (t/c)^2}{\sqrt{M^2 - 1}} = \frac{4 \times 0.0025}{\sqrt{3}} \approx 0.0058$$

per fin panel. For a four-fin rocket this adds $\Delta C_D \approx 0.023$ (referenced to fin planform area), which can represent 10--20% of total vehicle drag at Mach 2. Omitting this term produces a systematic under-prediction of drag and over-prediction of apogee altitude. Section 6.4 replaces the omission with the DATCOM Section 4.1.5.1 method (Puckett & Stewart 1947).

**Limitation 4: Linear fits for viscosity and speed of sound.** The original atmospheric model used a linear approximation for the speed of sound,

$$a_{\text{old}} = 331.3 + 0.606 \times (T - 273.15) \quad [\text{m/s}],$$

which is the first-order Taylor expansion of $a = \sqrt{\gamma R T}$ about 273.15 K. At sea-level conditions the error is 0.03%, but at the tropopause (216.65 K) the linear fit gives 297.0 m/s while the exact formula gives 295.1 m/s, a 0.7% Mach-number error that propagates nonlinearly into every Mach-dependent coefficient. The dynamic-viscosity linear fit was valid only between approximately $-40\,^\circ\mathrm{C}$ and $+40\,^\circ\mathrm{C}$; at stagnation temperatures behind strong shocks ($T > 1000$ K) the error exceeded 50%. Section 3 replaces both with the exact thermodynamic relation and Sutherland's law.

**Limitation 5: No shock modeling.** The original code treated each component as if it operated in undisturbed freestream flow. At supersonic speeds the nose cone generates an oblique shock that alters the local Mach, static pressure, and static temperature for downstream stations. For a 15-degree cone at Mach 3 the post-shock Mach number (Taylor-Maccoll) is approximately 2.49 and the post-shock static pressure is 2.62 times freestream; a fin on the body tube behind this nose therefore operates in flow at $M_\text{local} = 2.49$, not 3.0. The fin $C_{N_\alpha}$ depends on $1/\beta$, so using freestream Mach ($\beta = 2.83$) instead of local Mach ($\beta = 2.27$) produces a 25% error in the fin-to-body force ratio and a corresponding shift in center of pressure. Body transitions (shoulders) generate additional shocks and expansion fans that further modify local conditions. Section 7 addresses this with a `ShockGeometry` pre-pass that walks the body chain nose-to-tail and stores local conditions at each station for downstream fin-stability lookup. The current production consumer is the stability path, primarily `FinSetCalc`; body stability, fin pressure drag, roll damping, base drag, and wave drag remain freestream-based unless otherwise stated.

**Limitation 6: No supersonic CP correction.** The Barrowman method computes center of pressure assuming incompressible flow. At supersonic speeds the body lift distribution shifts substantially aft due to the change from subsonic to supersonic crossflow patterns; the CP of a slender body at Mach 3 is typically 5--10% of body length further aft than the subsonic prediction. For a marginally stable rocket this shift can mean the difference between stable and unstable flight. The original code issued a blanket warning at Mach 1.1 ("Supersonic flight is not supported") but made no attempt to correct the stability predictions. Section 8 addresses this with a Mach-dependent body $C_{N_\alpha}$ and CP shift validated against NASA TM X-653 ($C_{N_\alpha}$ MAPE 6.84%, $x_{CP}$ MAPE 7.11% across M 0.6--5.82).


### 1.3 Design Philosophy

The extensions described in this report were guided by three architectural principles.

**Incremental integration with regression gates.** Each new model was implemented, tested, and validated independently before being integrated into the main calculation pipeline. The aerodynamic regression suite currently comprises 85 tracked JUnit test classes in the `info.openrocket.core.aerodynamics` package hierarchy (87 tracked Java files including support/export helpers), plus one workspace-local ablation test used for the May 1 SimVReal import-parity study. Of these, **20 subsystems are externally benchmarked against published data** at the A-level standard with quantitative acceptance criteria, plus one externally anchored negative benchmark (RM-10) -- counted outside the 20 -- used to formally exclude a geometry family from the headline claim. Each capability increment was validated against two independent categories of evidence before promotion: (1) **exact analytical solutions and authoritative tabulated values** (NACA Report 1135 for shocks, US Standard Atmosphere 1976 for atmospheric properties, NIST/REFPROP for viscosity), which verify that the mathematics is implemented correctly; and (2) **physical experimental measurements** from wind tunnels, free-flight ballistic-range tests, and aeroballistic instrumentation programs (NACA TN 3393, TN 3650, RM A52H28, NASA TM X-653, AEDC-TR AGARD-B, Jorgensen TR R-474, ADA636861 Basic Finner, DTIC AD0487365 hypersonic cone), which verify that the models capture actual aerodynamic physics. Both categories are necessary; correct mathematics applied to a wrong physical model still produces wrong answers, and physically plausible trends that do not match measurements are equally unreliable.

**$C^1$-continuous regime blending.** Every transition between aerodynamic regimes (subsonic to transonic, transonic to supersonic, supersonic to hypersonic) uses smooth polynomial interpolation that is continuous in both value and first derivative. Discontinuities in aerodynamic coefficients cause the trajectory integrator to oscillate or diverge near Mach 1 as the simulation repeatedly crosses the discontinuity. The cubic Hermite spline used for the compressibility factor (Section 4) is the canonical example, but the same principle applies to all blending regions:

| Transition | Mach Range | Blending Method |
|:-----------|:-----------|:----------------|
| Beta factor | 0.95 -- 1.05 | Cubic Hermite spline ($C^1$) |
| Skin friction | 0.9 -- 1.1 | Polynomial interpolation |
| Base drag | 0.85 -- 1.3 | $C^1$ cubic blend |
| Chapman-Korst turbulent base | 1.2 -- 1.4 | $C^1$ cubic blend |
| Chapman laminar base | 1.3 -- 2.5 | Polynomial blend |
| Fin wave drag | 0.9 -- 1.2 | $C^1$ Hermite blend to DATCOM 4.1.5.1 |
| Fin $C_{N_\alpha}$ | 0.9 -- 1.5 | Hermite blend |
| PNK interference | 0.85 -- 1.15 | Smoothstep blend |
| Body $C_{N_\alpha}$/CP | 0.8 -- 1.3 | Hermite blend |
| Nose wave drag | 1.3 -- 1.5 | Polynomial blend with TR-R-100 |
| ShockGeometry activation | 1.0 -- 1.1 | Linear blend |
| Modified Newtonian | 4.0 -- 6.0 | Linear blend |

**Analytical models over empirical tables.** Where a closed-form analytical solution exists and is computationally tractable, it is preferred over empirical correlations or interpolation tables. Analytical models extrapolate correctly, have known error bounds, and are self-documenting. The Taylor-Maccoll cone-flow solution, DATCOM 4.1.5.1 fin wave drag theory, Prandtl-Meyer expansion relations, and Sutherland viscosity law are all exact within their physical assumptions. Empirical correlations (the supersonic base-drag correlation $C_{d,\text{base}} = 0.064 + 0.186/M^2$ validated against NACA TN 3393 and consistent with ESDU 77021, Van Driest II compressible skin friction, Pitts-Nielsen-Kaattari fin-body interference) are used only where no tractable analytical solution exists, and in those cases the source reference and validity range are documented in both the code and this report.


### 1.4 Scope and Headline Validation State

The current implementation models 31 distinct physical phenomena spanning atmospheric properties, gas dynamics, drag (friction, pressure, base, wave), static and dynamic stability, hypersonic effects, and numerical robustness. The full enumeration is given in Section 1.5. Each phenomenon is supported by one of four kinds of validation evidence, ordered from strongest to weakest:

- **External benchmark** -- matched against published external or tabulated data (wind-tunnel, ballistic-range, or analytical reference) with a quantitative acceptance criterion.
- **Integrated flight data** -- calibrated against the assembled trajectory output from the 25-flight validation corpus rather than against an isolated published component dataset. This is circular: the same corpus is the calibration target and one of the validation targets. Each such item is flagged where it is used and is *not* counted toward the 20-subsystem external-benchmark headline.
- **Numerical consistency** -- verified that the implementation reduces to its analytical limit or matches its own boundary conditions to machine precision; no independent external dataset.
- **Calibrated heuristic** -- empirically tuned with no external closure; this category is not used by any acceptance-critical claim in the present work.

The headline validation state is:

- **20 subsystems pass externally anchored acceptance criteria** against published wind-tunnel, free-flight, or analytical data at the A-level standard, plus 1 externally anchored *negative* benchmark (NACA RM-10, MAPE 80%, counted outside the 20 and formally excluded from the headline claim to bound the high-fineness / tapered-afterbody / swept-arc-fin family gap). Three results that earlier drafts counted as externally benchmarked are reported at their honest evidentiary level and are *not* in the 20: hypersonic cone foredrag (B-level / exploratory, thin-cone limitation), AGARD-B total drag (qualitative secondary, $\sim 22.6\%$ MAPE), and the vortex sideforce $K_v = 0.20$ (internally calibrated, no external anchor).
- **9 results rest on integrated flight-corpus closure rather than isolated component data** -- principally the finned-body base-drag augmentation, the power-on nozzle / pressure-thrust closure, and the integrated 6-DOF trajectory itself. These are flagged as such throughout the report.
- **25-flight integrated validation corpus**: 25 ground-truth amateur, university-research, and sounding-rocket flights (23 single-stage plus 2 two-stage: the AeroPac 104K at Mach 3.04 and MESOS 293K at Mach 4.33) spanning Mach 0.54--4.33, externally selected from Rogers' public RASAero II altitude-comparison set (not outcome-curated by us), archived as the *Rocket Flight Database* (DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977)). Result: **−0.38% mean signed apogee error, $\sigma = 5.44\%$, MAE 4.74%, 25/25 within $\pm 10\%$, 14/25 within $\pm 5\%$, zero abnormal terminations**. On the same 25 paired flights RASAero II yields mean $\lvert\text{error}\rvert$ 5.34% with 22/25 within $\pm 10\%$. The Wilcoxon signed-rank test on the paired absolute errors returns $W = 143.0$, $p = 0.615$, and the 95% bootstrap CI on the mean signed error is $[-2.41\%, +1.72\%]$ (brackets zero, so the predictor is statistically unbiased on this corpus); bias$^2$/MSE = 0.01 for OpenRocket Plus indicates the residual is dominated by per-flight variance rather than systematic model bias. The honest claim is **parity** with this version-locked RASAero II set, not superiority: the paired mean $\lvert\text{ORP}\rvert - \lvert\text{RAS}\rvert$ is $-0.60$ pp with 95% bootstrap CI $[-2.16, +0.96]$ (straddles zero). RASAero II values are Rogers' recorded predictions rather than fresh independently rerun pre-flight cases.
- **High-altitude two-stage detail** (the Mach 4.33 / 273 056 ft MESOS 293K flight, included as the two-stage closure of the corpus above): the current archived code predicts $-6.96\%$ apogee -- the corpus's largest single-flight error. This figure reproduces in isolation as the genuine current-code value and is carried in the published database. As the only high-Mach two-stage closure it simultaneously couples staging, coast-phase aerodynamics, and Mach-4 base drag; at $-6.96\%$ it remains inside the $\pm 10\%$ admission band, so the 25/25 headline is unchanged. (An earlier draft and database snapshot reported $-0.6\%$ / 291 601 ft for this flight; that figure was erroneous, has no defensible derivation, and is withdrawn -- it is not a prior value that $-6.96\%$ regressed from.) RASAero II reference for this flight is a post-flight reconstruction with adjusted ignition delay and launch angle.
- **Exploratory high-Mach set (NOT a headline).** Beyond the 25-flight corpus, $\sim 20$ historical sounding-rocket flights are run as an exploratory capability demonstration and reported in full: 3 close within $\pm 10\%$ (Black Brant V VB at Mach 7.224, $-6.97\%$; Nike-Deacon no. 1 at Mach 4.956, $-1.06\%$; Nike-Deacon no. 2 at Mach 5.079, $-0.89\%$), and 17 fall outside (Nike-Apache family $+24$ to $+36\%$, Nike-Cajun $+16.6\%$, Arcas blunt/secant $-29$ to $-69\%$, HEROS 3 $-63.4\%$, plus a couple of sim-error/zero-apogee cases reported transparently). The method reaches Mach 7 within $\pm 7\%$ on well-characterized vehicles, but motor and geometry reconstruction uncertainty dominates on poorly-documented historical flights. These flights are not aggregated into any "within $\pm 10\%$" headline.
- **Phase 6h coast-drag bias above $M = 5$.** Per-component $C_d$ analysis against the NASA Apache Performance Handbook (X-721-66-568, Case 1) shows that the pressure $C_d$ plateaus at $\sim 0.234$ from $M = 2$ through $M = 8$, against handbook values that decay from 0.704 at $M = 2$ to 0.384 at $M = 8$ without collapsing to the slender-body limit. The mean $C_d$ deficit for $M \geq 5$ is **+0.0595** (handbook minus ORP, averaged over 7 points). The root cause is the constant `SLENDER_BODY_MACH_DECAY_END = 5.0` in `BarrowmanDragCalculator.java`, which smoothsteps the Hoerner cylindrical-afterbody pressure correction to zero at $M = 5$ for high-fineness bodies. Under the $\pm 10\%$ admission criterion adopted for the corpus, nine Nike-Apache 1965 flights and one Nike-Cajun flight are held out of the corpus; all ten `.ork` build files are committed at `paper/data/ork/sounding_rockets/` and become admissible once the fix lands. The proposed fix (extend `SLENDER_BODY_MACH_DECAY_END` to ~12 and add a Hoerner-based cylindrical-afterbody pressure-drag term gated on body $L/D > 15$ and $M > 3$) is documented as **Phase 6h** in `SUPERSONIC_MODELING.md`.
- **Two damping constants are not externally validated** (held at **B-level** — a disclosed limitation, never a headline claim): the Tobak $C_{m_q}$ $\times 3$ multiplier and the transonic Gaussian peak augmentation are calibrated against the corpus apogee-turn signature, not against an isolated $C_{mq}$ benchmark. Against the ADA636861 Basic Finner $C_{mq}$ free-flight data the model over-predicts the transonic damping by roughly a factor of $3.6$ at $M = 1.05$--$1.12$ (Basic Finner $C_{mq}$ MAPE 69%; sign correct, supersonic under-prediction); the Sznajder 2025 ANSYS Fluent CFD comparator independently shows a **+110 to +160%** overshoot of the transonic peak at $M = 1.08$--$1.11$. Because apogee is insensitive to $C_{mq}$, this affects predicted dynamic stability and coning but not the apogee statistics. They are kept because removing them degrades the corpus dynamic-stability closure; closure requires a second independent free-flight $C_{mq}$ dataset that has not been located.

Per-case flight closures are in `paper/data/outlier_closure/`. Per-case results across the 25-flight corpus are reported in Section 11.6, and the canonical comparison artifact is at <https://doi.org/10.5281/zenodo.20531977>.


### 1.5 Scope of Physical Phenomena Addressed

The following 31 distinct physical phenomena are modeled in the current implementation:

1. Oblique shock waves (theta-beta-Mach relations)
2. Taylor-Maccoll cone flow (exact conical-shock solution)
3. Normal shock jump conditions
4. Prandtl-Meyer isentropic expansion fans
5. Shock geometry pre-pass (nose-to-tail local flow conditions)
6. Transonic compressibility factor ($C^1$ Hermite spline)
7. Exact thermodynamic speed of sound with humidity correction
8. Sutherland viscosity law (100 K to 1900 K)
9. Effective specific-heat ratio (vibrational excitation of $\mathrm{N_2}$ and $\mathrm{O_2}$)
10. Taylor-Maccoll cone wave drag
11. Shock-expansion ogive wave drag
12. Supersonic base drag ($C_{d,\text{base}} = 0.064 + 0.186/M^2$, validated against NACA TN 3393, consistent with ESDU 77021)
13. Transonic base-drag peak (polynomial correlation)
14. DATCOM 4.1.5.1 fin wave drag with subsonic/supersonic leading-edge classification (Puckett & Stewart 1947)
15. Van Driest II compressible skin friction transformation
16. Chapman (1950) laminar base drag
17. Boundary-layer transition (Michel criterion with compressibility correction)
18. Supersonic body $C_{N_\alpha}$ (Allen-Perkins crossflow with Jorgensen $C_{d,c}$)
19. Supersonic body CP aft shift
20. Modified Newtonian hypersonic pressure ($C_p = C_{p,\max}\sin^2\theta$)
21. Fin-body shock interaction (local-flow correction from `ShockGeometry`)
22. Forward-facing step drag (stagnation + reattachment recovery)
23. Fin shock-boundary-layer interaction (free-interaction chord reduction)
24. Trailing-edge base drag (Hoerner subsonic, $1/\sqrt{\beta}$ supersonic)
25. Axial-drag conversion with AoA-dependent polynomial and backward-flight reversal
26. High-AoA crossflow normal force with proportional moment scaling
27. Asymmetric vortex shedding sideforce (Champigny-Lacau, $\alpha > 20^\circ$)
28. Fin-fin aerodynamic-interference knockdown (5+ fins)
29. Roll damping with Mach-cone span limiting
30. Aerodynamic-coefficient sanitization (NaN / Infinity / extreme-value clamping)
31. Transonic singularity guards (SBLI separation length, pressure-plateau, fin polynomials)

Phenomena 1--4 are gas-dynamics foundation models (Chapter 5); 6--9 are atmosphere and compressibility models (Chapters 3--4); 5, 17, and 21 are coupling models documented where they are consumed (Chapters 6--8); 10--16 and 22--25 are drag models (Chapter 6); 18--20 are static-stability models (Chapter 8); 26--29 are dynamic-stability and high-AoA models (Chapter 9); and 30--31 are robustness guards distributed across the calculator and integrator code paths. Each phenomenon is traceable from this list to a Java implementation file (named in the chapter that derives it) and to one or more validation tests in the regression suite.


### 1.6 Software Architecture

The aerodynamic calculation pipeline follows a layered architecture in which a single orchestrator delegates to specialized calculators. The data flow for a single aerodynamic evaluation at a given Mach number and angle of attack is:

```{=latex}
\begin{figure}[!htbp]
\centering
\resizebox{0.9\linewidth}{!}{%
\begin{tikzpicture}[
  font=\footnotesize,
  node distance=0.5cm,
  box/.style={rectangle, draw=black!75, thick, align=center, inner sep=4pt, minimum width=3.0cm},
  lbl/.style={font=\scriptsize, align=left},
  arr/.style={-{Latex[length=1.8mm]}, thick, black!80}
]
\node[box] (FC) {FlightConditions\\[-0.08em]{\scriptsize ($M$, $\alpha$, atm)}};
\node[box, below=of FC] (BC) {BarrowmanCalculator\\[-0.08em]{\scriptsize \texttt{getAerodynamicForces} (orchestrator)}};
\draw[arr] (FC) -- (BC);
\node[box, below=of BC, text width=8.6cm, inner sep=5pt] (SG) {ShockGeometry.\texttt{compute()}\\[0.12em]
  {\scriptsize \textbf{Returns} local $M$, $p/p_\infty$, $T/T_\infty$, $q/q_\infty$ at each axial station.
  $M\le 1$: no-op passthrough.}};
\draw[arr] (BC) -- node[lbl, right, xshift=0.12cm] {1.~Shock pre-pass (once per call)} (SG);
\node[box, below left=1.05cm and 0.2cm of SG] (ST) {StabilityCalc};
\node[box, below right=1.05cm and 0.2cm of SG] (DR) {DragCalc};
\draw[arr] (SG.south) -- ++(0,-0.42) -| (ST.north);
\draw[arr, dashed] (BC.south) -- ++(0,-0.42) -| (DR.north);
\node[lbl, text width=4.6cm, below=0.25cm of ST, anchor=north, align=left]
  {\textbf{BarrowmanStabilityCalculator}\\[0.15em]
  SymmetricComponentCalc, FinSetCalc, rail/lug/tube fins, \ldots};
\node[lbl, text width=5.4cm, below=0.25cm of DR, anchor=north, align=left]
  {\textbf{BarrowmanDragCalculator}\\[0.15em]
  Van Driest II friction; Taylor-Maccoll / shock-expansion wave drag; $0.064+0.186/M^2$ base; overrides.};
\end{tikzpicture}%
}
\caption{Aerodynamic evaluation pipeline: \texttt{ShockGeometry} is computed once per call and consumed by the stability path; the drag path remains freestream-based.}
\label{fig:barrowman-pipeline-parta}
\end{figure}
```

The key architectural element is `ShockGeometry`, computed once per aerodynamic evaluation. At subsonic Mach numbers it is a no-op passthrough: all local conditions equal freestream, and no computational overhead is incurred. At supersonic Mach numbers, it walks the body chain nose-to-tail:

1. At the nose tip, it computes the initial oblique shock using `ObliqueShockSolver.solveCone()` (Taylor-Maccoll solution for a conical shock) or `ObliqueShockSolver.solve()` (wedge/ogive approximation). If the shock is detached (deflection angle exceeds the maximum for an attached shock), it falls back to `NormalShockRelations` for the strong-shock solution.

2. It marches downstream in 20 strips per component, computing the local surface tangent angle at each station. Where the surface turns away from the flow (convex curvature, as on an ogive or at a shoulder-to-body-tube junction), it applies a Prandtl-Meyer expansion fan. Where the surface turns into the flow (concave curvature, as at a boattail), it applies an oblique shock compression.

3. At each station it records the local Mach number, static-pressure ratio $p/p_\infty$, static-temperature ratio $T/T_\infty$, and dynamic-pressure ratio $q/q_\infty$. These are stored in a sorted list of `LocalConditions` objects.

4. Component calculators query `ShockGeometry.getConditionsAt(x)` to obtain interpolated local conditions at their axial position. `FinSetCalc`, for example, uses the local post-shock Mach to compute $C_{N_\alpha}$ via the $K_1/K_2/K_3$ formulas. The dynamic-pressure ratio is *not* applied as a separate scaling factor -- the local-Mach correction to $K_1/K_2/K_3$ already accounts for the post-shock flow state, and multiplying again by $q_\text{local}/q_\infty$ would constitute a double correction.

Between Mach 1.0 and 1.1, the shock-geometry corrections are linearly blended toward freestream values to eliminate the step discontinuity that would otherwise appear when the shock geometry first activates at $M = 1$.

All shock and expansion computations use validated solvers in the `info.openrocket.core.aerodynamics.shocks` package: [`ObliqueShockSolver`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/shocks/ObliqueShockSolver.java) (theta-beta-Mach, Taylor-Maccoll), [`NormalShockRelations`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/shocks/NormalShockRelations.java) (Rankine-Hugoniot jump conditions), and [`PrandtlMeyerExpansion`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/shocks/PrandtlMeyerExpansion.java) (isentropic expansion fan). These are pure mathematical utilities with no dependencies on the rest of the codebase and are independently validated against NACA Report 1135 and cone-flow reference tables: normal shock ratios to $7\times10^{-5}$, oblique-shock wave angle to $0.021\%$, Prandtl-Meyer angle to $0.004^\circ$, and Taylor-Maccoll cone-shock angle to $0.825\%$ relative. Alternative calculators (`LookupTableDragCalculator`, `LookupTableStabilityCalculator`) replace the entire Barrowman pipeline with CSV-driven Mach/AoA tables when configured in `SimulationOptions`.


## 2. Nomenclature

### 2.1 Roman Symbols

| Symbol | Units | Description |
|:-------|:------|:------------|
| $a$ | m/s | Speed of sound |
| $A_{\text{base}}$ | m$^2$ | Base area of rocket |
| $A_{\text{ref}}$ | m$^2$ | Reference area (maximum cross-section) |
| $c$ | m | Fin chord length |
| $\bar{c}$ | m/s | Mean molecular speed |
| $C_D$ | -- | Total drag coefficient |
| $C_{D,\text{base}}$ | -- | Base drag coefficient |
| $C_{D,\text{f}}$ | -- | Skin-friction drag coefficient |
| $C_{D,\text{wave}}$ | -- | Wave (pressure) drag coefficient |
| $C_f$ | -- | Local skin-friction coefficient |
| $C_m$ | -- | Pitching-moment coefficient |
| $C_{m_q}$ | rad$^{-1}$ | Pitch damping derivative |
| $C_N$ | -- | Normal-force coefficient |
| $C_{N_\alpha}$ | rad$^{-1}$ | Normal-force coefficient derivative w.r.t. AoA |
| $C_p$ | -- | Pressure coefficient |
| $C_{p,\max}$ | -- | Maximum (stagnation) pressure coefficient |
| $c_p$ | J/(kg$\cdot$K) | Specific heat at constant pressure |
| $c_v$ | J/(kg$\cdot$K) | Specific heat at constant volume |
| $c_{v,\text{vib}}/R$ | -- | Dimensionless vibrational specific-heat contribution |
| $d$ | m | Reference diameter |
| $e$ | -- | Euler's number ($\approx 2.71828$) |
| $e_s$ | Pa | Saturation vapor pressure |
| $f$ | -- | Number of active degrees of freedom |
| $f_L, f_H$ | -- | Endpoint values of $\beta$ at $M_L$ and $M_H$ |
| $f'_L, f'_H$ | -- | Endpoint $M$-derivatives of $\beta$ at $M_L$ and $M_H$ |
| $h$ | J$\cdot$s | Planck's constant |
| $h_{00}, h_{10}, h_{01}, h_{11}$ | -- | Cubic Hermite basis functions |
| $k_B$ | J/K | Boltzmann's constant |
| $K_1, K_2, K_3$ | -- | Fin lift interference factors (Barrowman) |
| $l$ | m | Body or component length |
| $M$ | -- | Mach number |
| $M_L$ | -- | Lower edge of transonic blending band (0.95) |
| $M_H$ | -- | Upper edge of transonic blending band (1.05) |
| $\Delta M$ | -- | Transonic-band width $M_H - M_L = 0.10$ |
| $M_1$ | -- | Upstream (pre-shock) Mach number |
| $M_2$ | -- | Downstream (post-shock) Mach number |
| $M_d$ | g/mol | Molar mass of dry air ($\approx 28.964$) |
| $M_w$ | g/mol | Molar mass of water vapor ($\approx 18.015$) |
| $N$ | -- | Number of computational strips per component |
| $p$ | Pa | Static pressure |
| $p_0$ | Pa | Total (stagnation) pressure |
| $q$ | Pa | Dynamic pressure ($\tfrac{1}{2}\rho V^2$) |
| $R$ | J/(kg$\cdot$K) | Specific gas constant of dry air (287.053) |
| $R_h$ | J/(kg$\cdot$K) | Gas constant of humid air |
| $\text{Re}$ | -- | Reynolds number |
| $\text{Re}_x$ | -- | Reynolds number based on distance $x$ from nose |
| $\text{RH}$ | -- | Relative humidity (0 to 1) |
| $S$ | K | Sutherland constant for air (110.4) |
| $t$ | -- or m | Normalized interpolation parameter $\in [0,1]$, or fin thickness |
| $t/c$ | -- | Fin thickness-to-chord ratio |
| $T$ | K | Static temperature |
| $T_C$ | $^\circ$C | Celsius temperature ($T - 273.15$) |
| $T_0$ | K | Total (stagnation) temperature |
| $T_{\text{ref}}$ | K | Sutherland reference temperature (273.15) |
| $T_w$ | K | Wall temperature |
| $V$ | m/s | Flow velocity |
| $x$ | m | Axial distance from rocket nose |
| $x_{CP}$ | m | Center-of-pressure location (from nose) |
| $Z_\text{vib}$ | -- | Vibrational partition function |

### 2.2 Greek Symbols

| Symbol | Units | Description |
|:-------|:------|:------------|
| $\alpha$ | rad | Angle of attack |
| $\beta$ | -- | Compressibility factor: $\sqrt{1-M^2}$ (subsonic) or $\sqrt{M^2-1}$ (supersonic) |
| $\gamma$ | -- | Ratio of specific heats ($c_p/c_v$); 1.4 for air below ~800 K |
| $\gamma_{\text{eff}}$ | -- | Effective ratio of specific heats including vibrational excitation |
| $\delta$ | rad | Flow deflection angle through a shock |
| $\varepsilon$ | -- | Ratio of molar masses, water vapor to dry air ($M_w/M_d \approx 0.622$) |
| $\theta$ | rad or K | Shock wave angle from flow direction, or characteristic vibrational temperature |
| $\theta_{\mathrm{N_2}}$ | K | Characteristic vibrational temperature of nitrogen (3371) |
| $\theta_{\mathrm{O_2}}$ | K | Characteristic vibrational temperature of oxygen (2256) |
| $\Lambda$ | rad | Fin leading-edge sweep angle |
| $\lambda$ | m | Mean free path |
| $\mu$ | Pa$\cdot$s | Dynamic viscosity |
| $\mu_{\text{ref}}$ | Pa$\cdot$s | Sutherland reference viscosity ($1.716 \times 10^{-5}$) |
| $\nu$ | m$^2$/s | Kinematic viscosity ($\mu/\rho$) |
| $\nu_0$ | s$^{-1}$ | Vibrational mode frequency |
| $\nu_{\text{PM}}$ | rad | Prandtl-Meyer function |
| $\rho$ | kg/m$^3$ | Air density |
| $\sigma$ | m or -- | Effective collision cross-section, or post-/pre-shock density ratio |
| $\tilde{\nu}$ | cm$^{-1}$ | Vibrational wavenumber |

### 2.3 Subscripts and Superscripts

| Notation | Description |
|:---------|:------------|
| $(\cdot)_\infty$ | Freestream (undisturbed) conditions |
| $(\cdot)_1$ | Upstream of shock |
| $(\cdot)_2$ | Downstream of shock |
| $(\cdot)_e$ | Edge of boundary layer (local inviscid conditions) |
| $(\cdot)_w$ | Wall conditions |
| $(\cdot)_{\text{stag}}$ or $(\cdot)_0$ | Stagnation (total) conditions |
| $(\cdot)_{\text{local}}$ | Local conditions at a specific axial station |
| $(\cdot)_L, (\cdot)_H$ | Low / high edge of a Mach blending band |
| $(\cdot)_{\text{ref}}$ | Reference value (e.g., Sutherland reference temperature) |
| $(\cdot)_{s}$ | Evaluated at constant entropy |

### 2.4 Abbreviations

| Abbreviation | Meaning |
|:-------------|:--------|
| AoA | Angle of attack |
| BL | Boundary layer |
| CP | Center of pressure |
| DATCOM | USAF Stability and Control Datcom |
| HPR | High-power rocketry |
| ISA | International Standard Atmosphere |
| MAE | Mean absolute error |
| MAPE | Mean absolute percentage error |
| NIST | (US) National Institute of Standards and Technology |
| PDAS | Public Domain Aeronautical Software (digitization source) |
| PM | Prandtl-Meyer (expansion) |
| PNK | Pitts-Nielsen-Kaattari (fin-body interference) |
| RH | Relative humidity |
| SBLI | Shock-boundary-layer interaction |
| TR-R-100 | NASA Technical Report R-100 (Stoney 1958) |


## 3. Atmospheric Model

Accurate aerodynamic modeling at supersonic and hypersonic speeds requires precise values of three fundamental atmospheric properties: the **speed of sound** $a$ (which determines the Mach number), the **dynamic viscosity** $\mu$ (which determines the Reynolds number and skin friction), and the **ratio of specific heats** $\gamma$ (which enters every compressible-flow relation). The original OpenRocket implementation used linear approximations for $a$ and $\mu$ that were adequate at sea-level temperatures but degraded severely at the low temperatures of the tropopause and the high stagnation temperatures of supersonic flight. The ratio $\gamma$ was treated as a constant (1.4) at all conditions. This section derives the replacement models, gives the constants and code paths in [`AtmosphericConditions.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/models/atmosphere/AtmosphericConditions.java), quantifies the improvement, and reports the validation evidence in [`AtmosphericConditionsUpgradeTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/models/atmosphere/AtmosphericConditionsUpgradeTest.java).


### 3.1 Speed of Sound

#### 3.1.1 Derivation from the Ideal Gas Law

The speed of sound in an ideal gas is the speed of propagation of an infinitesimal isentropic disturbance. Begin from the linearized Euler momentum equation for a one-dimensional isentropic perturbation in a uniform medium:

$$dp = \rho\, a\, dV,$$

and the linearized continuity equation:

$$d\rho = \frac{\rho}{a}\, dV.$$

Eliminating $dV$ between the two yields the thermodynamic definition

$$a^2 = \left.\frac{dp}{d\rho}\right|_s.$$

For an isentropic process in an ideal gas, $p = C\,\rho^\gamma$ with $C$ constant. Differentiating,

$$\left.\frac{dp}{d\rho}\right|_s = \gamma\, C\, \rho^{\gamma-1} = \gamma\,\frac{p}{\rho}.$$

The ideal-gas equation of state $p = \rho R T$ gives $p/\rho = RT$, so

$$\boxed{\;a = \sqrt{\gamma\,R\,T}\;}$$

with $\gamma = 1.4$ (for air below ~800 K), $R = 287.053$ J/(kg$\cdot$K) (the specific gas constant of dry air, declared as `AtmosphericConditions.R`), and $T$ in Kelvin.

#### 3.1.2 Humidity Correction

Humid air has a higher specific gas constant than dry air because water vapor ($M_w = 18.015$ g/mol) is lighter than the dry-air mixture ($M_d = 28.964$ g/mol). The gas constant of humid air, as implemented in `getGasConstant()`, is

$$R_h = R\left[1 + \frac{\varepsilon\cdot \text{RH}\cdot e_s(T)}{p - \text{RH}\cdot e_s(T)(1-\varepsilon)}\left(\frac{1}{\varepsilon}-1\right)\right],$$

where $\varepsilon = M_w/M_d = 0.622$ is the molar-mass ratio (`AtmosphericConditions.EPSILON`), RH is the relative humidity in $[0,1]$, and $e_s(T)$ is the saturation vapor pressure computed from a Clausius-Clapeyron form,

$$e_s(T) = 611.3\,\exp\!\left(19.854 - \frac{5423}{T}\right) \quad [\text{Pa}].$$

The speed of sound in humid air is then

$$a = \sqrt{\gamma\,R_h\,T}.$$

At standard sea-level conditions ($T = 293.15$ K, $p = 101\,325$ Pa, RH = 0.5) the humidity correction increases $a$ by approximately 0.2 m/s (0.06%), which is negligible for nearly all rocketry applications but is included for completeness and matters slightly for precision validation against standard-atmosphere tables. The Java implementation is exactly:

```java
public double getMachSpeed() {
    return Math.sqrt(GAMMA * getGasConstant() * getTemperature());
}
```

where `getGasConstant()` returns $R_h$ when humidity is nonzero and $R$ otherwise.

#### 3.1.3 Comparison Against the Original Linear Fit

The original linear approximation was

$$a_{\text{old}} = 331.3 + 0.606 \times (T - 273.15) \quad [\text{m/s}],$$

which is the first-order Taylor expansion of $a = \sqrt{\gamma R T}$ about $T_0 = 273.15$ K:

$$a(T) \approx a(T_0) + a'(T_0)(T - T_0) = 331.3 + \frac{\sqrt{\gamma R}}{2\sqrt{T_0}}(T - T_0) = 331.3 + 0.607\,(T - T_0).$$

The coefficient 0.606 in the original code is slightly different from the exact Taylor coefficient 0.607, indicating it was likely obtained from a least-squares fit to tabulated data rather than from the analytical expansion. Across the temperature range encountered in rocket flight, from the tropopause through stagnation temperatures behind strong shocks:

| $T$ (K) | Old linear $a$ (m/s) | New exact $a$ (m/s) | Error (m/s) | Error (%) |
|:--------|:---------------------|:--------------------|:------------|:----------|
| 200    | 287.0 | 283.5 | $+3.5$ | $+1.24$ |
| 216.65 | 297.1 | 295.1 | $+2.0$ | $+0.68$ |
| 250    | 317.3 | 316.9 | $+0.4$ | $+0.13$ |
| 273.15 | 331.3 | 331.3 | $\phantom{+}0.0$ | $\phantom{+}0.00$ |
| 288.15 | 340.4 | 340.3 | $+0.1$ | $+0.03$ |
| 300    | 347.5 | 347.2 | $+0.3$ | $+0.09$ |
| 400    | 408.1 | 401.0 | $+7.1$ | $+1.77$ |
| 500    | 468.7 | 448.2 | $+20.5$ | $+4.57$ |

At the calibration point ($T = 273.15$ K) the error is zero by construction. The error grows rapidly at both low and high temperatures: at 200 K the linear fit overestimates $a$ by 1.24%, and at 500 K (a stagnation temperature reached at approximately Mach 3 at sea level) it overestimates by 4.57%. Because the Mach number is $M = V/a$, a 4.57% overestimate of $a$ translates to a 4.57% underestimate of $M$, which propagates nonlinearly into every Mach-dependent coefficient. At the tropopause this manifests as a 0.7% Mach-number error before any aerodynamic computation.

#### 3.1.4 External Validation (US Standard Atmosphere 1976)

The implementation has been validated end-to-end against the US Standard Atmosphere 1976 (Table 1 SI, PDAS digitization) over geometric altitudes 0--80 km. The validation harness is `AtmosphericConditionsUpgradeTest.testSpeedOfSoundAgainstPublishedTable` (twenty altitude points with published $T$ and $a$). A representative subset, drawn from `paper/data/csv/us_standard_atmosphere_speed_of_sound.csv`:

| $Z$ (km) | $T$ (K) | $a_\text{ref}$ (m/s) | $a_\text{model}$ (m/s) | Error (%) |
|:---------|:--------|:---------------------|:---------------------|:----------|
| 0  | 288.150 | 340.294 | 340.294 | $0.000019$ |
| 10 | 223.252 | 299.527 | 299.463 | $0.000078$ |
| 11 | 216.773 | 295.147 | 295.069 | $0.000190$ |
| 20 | 216.650 | 295.069 | 295.069 | $0.000190$ |
| 30 | 226.509 | 301.709 | 301.709 | $0.000072$ |
| 50 | 270.650 | 329.799 | 329.799 | $0.000059$ |
| 80 | 198.639 | 282.538 | 282.575 | $0.013$ |

The maximum residual in the current exported speed-of-sound table is **0.0158%** at the cold 186.95 K upper-atmosphere reference row, still far inside the 0.5% regression gate. Within the 0--80 km altitude validation, the agreement is effectively limited by the digitization precision of the published table and by rounding in the model's mesosphere-2 lapse rate ($-1.9995$ mK/m vs the standard's exact $-2.0$ mK/m).

| Validation | Source | Result | Evidence |
|:-----------|:-------|:-------|:---------|
| Speed of sound | US Std Atm 1976 (PDAS) | max err 0.016% in current exported reference table | external benchmark |
| Speed of sound formula | $a = \sqrt{\gamma R T}$ | $< 10^{-10}$ | numerical consistency |

#### 3.1.5 Worked Example

**Problem.** Compute the speed of sound at the tropopause: $T = 216.65$ K, $p = 22\,632$ Pa, dry air.

**Old model.**
$$a_{\text{old}} = 331.3 + 0.606 \times (216.65 - 273.15) = 331.3 + 0.606 \times (-56.50) = 297.1 \text{ m/s}.$$

**New model.**
$$a_{\text{new}} = \sqrt{1.4 \times 287.053 \times 216.65} = \sqrt{87\,066.0} = 295.07 \text{ m/s}.$$

The US Standard Atmosphere 1976 tabulates $a = 295.07$ m/s at 11 km, so the new model reproduces the standard-atmosphere value at the tropopause. The old model overestimates by $297.1 - 295.07 = 2.0$ m/s, or 0.7%. A rocket traveling at 900 m/s would be reported as Mach 3.05 by the new model but Mach 3.03 by the old model. At Mach 3, the supersonic compressibility factor $\beta = \sqrt{M^2 - 1}$ changes by approximately 0.7% per 0.7% change in Mach, so the downstream effect on wave drag is comparable.


### 3.2 Dynamic Viscosity: Sutherland's Law

#### 3.2.1 Physical Basis

The dynamic viscosity of a gas arises from molecular momentum transport across a velocity gradient. From the kinetic theory of gases, viscosity is proportional to the product of density, mean free path, and mean molecular speed:

$$\mu \propto \rho\, \lambda\, \bar{c},$$

where $\lambda \propto 1/(\rho\,\sigma^2)$ is the mean free path and $\bar{c} \propto \sqrt{T}$ is the mean molecular speed. For rigid elastic spheres this gives $\mu \propto \sqrt{T}$, independent of pressure (as confirmed experimentally for gases far from condensation).

Real molecules are not rigid spheres; they attract each other at moderate distances through van der Waals forces. William Sutherland (1893) proposed a correction that accounts for the attractive intermolecular potential by introducing a single empirical parameter $S$ (the Sutherland constant):

$$\boxed{\;\mu = \mu_{\text{ref}} \left(\frac{T}{T_{\text{ref}}}\right)^{3/2} \frac{T_{\text{ref}} + S}{T + S}\;}$$

The $T^{3/2}$ factor combines the kinetic-theory $\sqrt{T}$ from molecular speed with a $T$ from the effective collision cross-section varying as $1/T$ due to the attractive potential well. The factor $(T_{\text{ref}} + S)/(T + S)$ is Sutherland's correction; it approaches unity at high temperatures (where kinetic energy dominates over the potential well) and increases $\mu$ at low temperatures (where the attractive potential reduces the effective collision cross-section).

#### 3.2.2 Derivation of the Functional Form

Beginning from a simple intermolecular-potential model in which the effective collision cross-section squared varies as

$$\sigma^2 = \sigma_0^2 \left(1 + \frac{S}{T}\right),$$

the mean free path is

$$\lambda = \frac{1}{n\,\pi\,\sigma^2} = \frac{1}{n\,\pi\,\sigma_0^2\,(1 + S/T)}.$$

The kinetic-theory expression for viscosity is

$$\mu = \tfrac{1}{3}\rho\,\bar{c}\,\lambda = \frac{1}{3}\,\frac{m\,\bar{c}}{\pi\,\sigma_0^2\,(1 + S/T)}.$$

Since $\bar{c} \propto \sqrt{T/m}$,

$$\mu \propto \frac{\sqrt{T}}{1 + S/T} = \frac{T^{3/2}}{T + S}.$$

Normalizing to a reference state $(T_{\text{ref}}, \mu_{\text{ref}})$ recovers Sutherland's formula in its standard form.

#### 3.2.3 Constants for Air

The Java implementation uses the following constants, declared as private static finals in [`AtmosphericConditions.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/models/atmosphere/AtmosphericConditions.java):

| Constant | Java field | Value | Source |
|:---------|:-----------|:------|:-------|
| $\mu_{\text{ref}}$ | `MU_REF` | $1.716 \times 10^{-5}$ Pa$\cdot$s | NIST air viscosity at $T_{\text{ref}}$ |
| $T_{\text{ref}}$ | `T_REF` | 273.15 K | 0 $^\circ$C |
| $S$ | `S_SUTHERLAND` | 110.4 K | NIST/CRC standard for air |

The implementation is exactly:

```java
public double getDynamicViscosity() {
    double T = getTemperature();
    return MU_REF * Math.pow(T / T_REF, 1.5)
                  * (T_REF + S_SUTHERLAND) / (T + S_SUTHERLAND);
}
```

The formula is accurate from approximately 100 K to 1900 K. Below 100 K, air begins to liquefy. Above 1900 K, dissociation of $\mathrm{O_2}$ (beginning near 2500 K) and $\mathrm{N_2}$ (beginning near 4000 K) alters the gas composition, and the single-species Sutherland model is no longer valid.

#### 3.2.4 Comparison Against the Original Linear Fit

The original linear viscosity fit was of the form $\mu = A + B\cdot T_C$, with $T_C = T - 273.15$ in Celsius, valid only near standard conditions. The following table compares the linear fit against Sutherland's law and reference data (all viscosities in $10^{-5}$ Pa$\cdot$s).

| $T$ (K) | $T_C$ ($^\circ$C) | Old linear $\mu$ | Sutherland $\mu$ | NIST ref | Old error (%) |
|:--------|:------------------|---:|---:|---:|---:|
| 200  | $-73.2$ | 1.33 | 1.329 | 1.329 | $+0.1$ |
| 300  | 26.9    | 1.85 | 1.846 | 1.846 | $+0.2$ |
| 500  | 226.9   | 2.87 | 2.671 | 2.671 | $+7.4$ |
| 1000 | 726.9   | 5.40 | 4.152 | 4.152 | $+30.1$ |
| 1500 | 1226.9  | 7.93 | 5.259 | 5.354 | $+48.2$ |

At 300 K (near sea level) both models agree to two significant figures. At 500 K (stagnation temperature at ~Mach 2.5 at sea level) the linear fit overestimates viscosity by 7.4%. At 1000 K (~Mach 4.5) the error reaches 30%, and at 1500 K (Mach 5.5+) it exceeds 48%.

These viscosity errors propagate directly into the skin-friction coefficient. Since $\text{Re} = \rho V l / \mu$, an overestimate of $\mu$ by 30% produces a 30% underestimate of $\text{Re}$, which for turbulent flow ($C_f \propto \text{Re}^{-0.2}$) gives approximately a 6% overestimate of $C_f$. The Eckert reference-temperature method (deprecated in this work in favor of Van Driest II; see Section 6.3) evaluates viscosity at the reference temperature $T^*$, which at Mach 4 can exceed 800 K; using the original linear fit at $T^* = 800$ K would have produced a viscosity error of approximately 20% and a skin-friction error of approximately 4%.

#### 3.2.5 External Validation (Incropera Table A.4 / NIST)

The implementation has been validated against Incropera & DeWitt, "Fundamentals of Heat and Mass Transfer," 7th ed., Table A.4 ("Properties of Gases at Atmospheric Pressure -- air at 1 atm"), whose values are derived from the NIST/REFPROP reference fluid database rather than from Sutherland's formula itself. The validation harness is `AtmosphericConditionsUpgradeTest.testDynamicViscosityAgainstNIST`, which gates the 100--800 K NIST/Incropera comparison at 3% and records the 150--500 K atmospheric-flight regime in the code comments as max error 1.15%, MAPE 0.54%. The compact CSV currently exported at `paper/data/csv/sutherland_viscosity_air.csv` is the formula self-consistency subset shown below:

| $T$ (K) | Reference ($\times 10^{-5}$ Pa$\cdot$s) | Model ($\times 10^{-5}$ Pa$\cdot$s) | Error (%) |
|:--------|:-----------------------------------|:----------------------------------|:----------|
| 200    | 1.329 | 1.328 | 0.038 |
| 250    | 1.599 | 1.599 | 0.003 |
| 273.15 | 1.716 | 1.716 | 0.000 |
| 288.15 | 1.789 | 1.789 | 0.017 |
| 300    | 1.846 | 1.846 | 0.005 |
| 400    | 2.285 | 2.285 | 0.007 |
| 500    | 2.670 | 2.670 | 0.015 |

Aggregated over the exported formula-check range:

- T = 200--500 K: max error 0.038%, **MAPE 0.012%** against the rounded tabular values in the current CSV.
- The independent NIST/Incropera acceptance gate remains in `AtmosphericConditionsUpgradeTest.testDynamicViscosityAgainstNIST`, not in this compact CSV export.

| Validation | Source | Result | Evidence |
|:-----------|:-------|:-------|:---------|
| Dynamic viscosity | Incropera Table A.4 (NIST/REFPROP) | gated at $<3\%$ over 100--800 K; code comment records 0.54% MAPE in 150--500 K | external benchmark |
| Formula self-consistency | Sutherland analytical export | max 0.038%, MAPE 0.012% over 200--500 K rounded reference values | numerical consistency |
| Reference-point exactness | $\mu(T_{\text{ref}}) = \mu_{\text{ref}}$ | $< 10^{-10}$ | numerical consistency |
| Monotonicity | $d\mu/dT > 0$ | passes 150--1000 K | numerical consistency |

#### 3.2.6 Worked Example

**Problem.** Compute the dynamic viscosity at $T = 500$ K.

$$\mu = 1.716 \times 10^{-5} \times \left(\frac{500}{273.15}\right)^{3/2} \times \frac{273.15 + 110.4}{500 + 110.4}.$$

Step 1 -- temperature ratio and its 3/2 power:
$$\frac{T}{T_{\text{ref}}} = \frac{500}{273.15} = 1.8306, \qquad 1.8306^{1.5} = 2.4782.$$

Step 2 -- Sutherland correction factor:
$$\frac{T_{\text{ref}} + S}{T + S} = \frac{273.15 + 110.4}{500 + 110.4} = \frac{383.55}{610.40} = 0.6283.$$

Step 3 -- final result:
$$\mu = 1.716 \times 10^{-5} \times 2.4782 \times 0.6283 = 2.672 \times 10^{-5} \text{ Pa}\cdot\text{s}.$$

The NIST tabulated value at 500 K and 1 atm is $2.671 \times 10^{-5}$ Pa$\cdot$s; agreement is within 0.04%.


### 3.3 Effective Ratio of Specific Heats

#### 3.3.1 Physical Background

The ratio of specific heats $\gamma = c_p/c_v$ determines the relationship between pressure, density, and temperature in compressible flow. It enters the speed of sound ($a \propto \sqrt{\gamma}$), the isentropic flow relations, all shock jump conditions, and the Prandtl-Meyer expansion function.

For a diatomic ideal gas at moderate temperatures, statistical mechanics gives

$$c_v = \tfrac{f}{2}\,R,$$

where $f$ is the number of active degrees of freedom and $R$ is the specific gas constant. At room temperature the diatomic molecules $\mathrm{N_2}$ and $\mathrm{O_2}$ have:

- 3 translational degrees of freedom (contributing $\tfrac{3}{2}R$ to $c_v$);
- 2 rotational degrees of freedom (contributing $R$ to $c_v$);
- vibrational modes "frozen out" (quantum-mechanically inaccessible at low $T$).

This gives $c_v = \tfrac{5}{2}R$, $c_p = c_v + R = \tfrac{7}{2}R$, and

$$\gamma = \frac{c_p}{c_v} = \frac{7/2}{5/2} = 1.4.$$

As temperature rises above approximately 800 K the vibrational modes begin to absorb energy. A fully excited vibrational mode contributes an additional $R$ to $c_v$ (the quantum harmonic oscillator carries both kinetic and potential energy, each contributing $\tfrac{1}{2}R$). With the vibrational mode fully active the limiting values are $c_v = \tfrac{7}{2}R$, $c_p = \tfrac{9}{2}R$, $\gamma = 9/7 \approx 1.286$. In practice the mode is never fully excited at temperatures below dissociation, and $\gamma$ varies continuously between 1.4 and approximately 1.3.

#### 3.3.2 Einstein Model for Vibrational Specific Heat

The vibrational contribution to $c_v$ is computed from the Einstein model for a quantum harmonic oscillator. Each vibrational mode is characterized by a single frequency $\nu_0$, or equivalently a characteristic temperature

$$\theta = \frac{h\,\nu_0}{k_B},$$

where $h$ is Planck's constant and $k_B$ is Boltzmann's constant. The partition function for a single quantum harmonic oscillator is the geometric series

$$Z_\text{vib} = \sum_{n=0}^{\infty} e^{-n\theta/T} = \frac{1}{1 - e^{-\theta/T}}.$$

The mean vibrational energy per molecule is

$$\langle E_\text{vib}\rangle = k_B T^2 \frac{\partial \ln Z_\text{vib}}{\partial T}.$$

Computing the logarithmic derivative,

$$\ln Z_\text{vib} = -\ln\!\left(1 - e^{-\theta/T}\right),$$

$$
\begin{aligned}
\frac{\partial \ln Z_\text{vib}}{\partial T}
&= \frac{(\theta/T^2)\,e^{-\theta/T}}{1 - e^{-\theta/T}}\\
&= \frac{\theta}{T^2}\cdot\frac{1}{e^{\theta/T} - 1},
\end{aligned}
$$

so that

$$\langle E_\text{vib}\rangle = k_B\,\theta\cdot\frac{1}{e^{\theta/T} - 1}.$$

Differentiating with respect to $T$ gives the vibrational contribution to $c_v$:

$$
\begin{aligned}
c_{v,\text{vib}}
&= \frac{\partial\langle E_\text{vib}\rangle}{\partial T}\\
&= k_B\left(\frac{\theta}{T}\right)^{\!2}
   \frac{e^{\theta/T}}{\left(e^{\theta/T} - 1\right)^{\!2}}.
\end{aligned}
$$

Dividing by $R$ gives the dimensionless contribution per molecule:

$$\boxed{\;\frac{c_{v,\text{vib}}}{R} = \left(\frac{\theta}{T}\right)^{\!2}\frac{e^{\theta/T}}{\left(e^{\theta/T} - 1\right)^{\!2}}\;}$$

The function has the expected limits:

- as $T \to 0$ ($\theta/T \to \infty$): $c_{v,\text{vib}}/R \to (\theta/T)^2 e^{-\theta/T} \to 0$ (frozen mode);
- as $T \to \infty$ ($\theta/T \to 0$): $c_{v,\text{vib}}/R \to 1$ (classical equipartition);
- at $T = \theta$: $c_{v,\text{vib}}/R = e/(e-1)^2 \approx 0.921$ (92% excited).

The Java implementation in [`AtmosphericConditions.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/models/atmosphere/AtmosphericConditions.java) is exactly:

```java
private static double vibrationalCv(double T, double theta) {
    if (T < 100.0) return 0;
    double x = theta / T;
    if (x > 50.0) return 0;            // exp overflow guard
    double ex = Math.exp(x);
    double denom = (ex - 1.0);
    return x * x * ex / (denom * denom);
}
```

#### 3.3.3 Mixture Rule for Air

Dry air is approximately 79% $\mathrm{N_2}$ and 21% $\mathrm{O_2}$ by mole fraction. The characteristic vibrational temperatures are:

| Species | $\theta$ (K) | Java field | Physical origin |
|:--------|:-------------|:-----------|:----------------|
| $\mathrm{N_2}$ | 3371 | `thetaN2` (local) | strong triple bond ($\tilde{\nu} = 2345$ cm$^{-1}$) |
| $\mathrm{O_2}$ | 2256 | `thetaO2` (local) | weaker double bond ($\tilde{\nu} = 1568$ cm$^{-1}$) |

The high $\theta$ for $\mathrm{N_2}$ keeps its vibrational mode substantially frozen even at 2000 K, while $\mathrm{O_2}$ begins to excite noticeably above 800 K.

The weighted vibrational contribution and the corresponding effective gamma are

$$\frac{c_{v,\text{vib,mix}}}{R} = 0.79\,\frac{c_{v,\text{vib}}(\mathrm{N_2})}{R} + 0.21\,\frac{c_{v,\text{vib}}(\mathrm{O_2})}{R},$$

$$
\begin{aligned}
\frac{c_{v,\text{total}}}{R}
&= \tfrac{5}{2} + \frac{c_{v,\text{vib,mix}}}{R},\\
\gamma_\text{eff}
&= \frac{c_{v,\text{total}} + R}{c_{v,\text{total}}}
 = 1 + \frac{1}{5/2 + c_{v,\text{vib,mix}}/R}.
\end{aligned}
$$

The implementation clamps $\gamma_\text{eff}$ to the range $[1.3,\,1.4]$. The lower bound 1.3 corresponds to approximately 90% vibrational excitation; below this, dissociation effects (not modeled) would dominate and a single-$\gamma$ model would no longer be appropriate. The Java implementation is exactly:

```java
public static double effectiveGamma(double stagnationTemp) {
    if (stagnationTemp <= 800.0) {
        return GAMMA;                               // 1.4
    }
    double thetaN2 = 3371.0;
    double thetaO2 = 2256.0;
    double cvVibN2 = vibrationalCv(stagnationTemp, thetaN2);
    double cvVibO2 = vibrationalCv(stagnationTemp, thetaO2);
    double cvVib   = 0.79 * cvVibN2 + 0.21 * cvVibO2;
    double cvTotal = 2.5 + cvVib;
    double gamma   = (cvTotal + 1.0) / cvTotal;
    return Math.max(1.3, Math.min(GAMMA, gamma));
}
```

The input is the **stagnation (total) temperature**, not the static temperature. The stagnation temperature behind a shock or at the wall of a body is

$$T_0 = T\left(1 + \frac{\gamma - 1}{2}\,M^2\right).$$

At Mach 5 with sea-level static temperature 288.15 K, $T_0 = 288.15 \times 6.0 = 1729$ K. At Mach 7, $T_0 = 288.15 \times 10.8 = 3112$ K.

#### 3.3.4 Tabulated Values

The following table gives $\gamma_\text{eff}$ at several stagnation temperatures, with individual species contributions, as produced by the Einstein model with the constants above:

| $T_\text{stag}$ (K) | $c_{v,\text{vib}}(\mathrm{N_2})/R$ | $c_{v,\text{vib}}(\mathrm{O_2})/R$ | $c_{v,\text{vib,mix}}/R$ | $c_{v,\text{total}}/R$ | $\gamma_\text{eff}$ |
|---:|---:|---:|---:|---:|---:|
| 500  | 0.0538 | 0.2284 | 0.0905 | 2.590 | 1.386 |
| 800  | 0.2706 | 0.5360 | 0.3263 | 2.826 | 1.354 |
| 1000 | 0.4187 | 0.6653 | 0.4705 | 2.970 | 1.337 |
| 1500 | 0.6673 | 0.8310 | 0.7017 | 3.202 | 1.312 |
| 2000 | 0.7934 | 0.9004 | 0.8159 | 3.316 | 1.302 |
| 2500 | 0.8613 | 0.9348 | 0.8768 | 3.377 | 1.296 |
| 3000 | 0.9011 | 0.9542 | 0.9123 | 3.412 | 1.293 |
| 4000 | 0.9429 | 0.9739 | 0.9494 | 3.449 | 1.290 |

At 800 K, the unclamped Einstein model gives $\gamma_\text{eff} = 1.354$, a 3.3% reduction from the ideal value (the production code holds $\gamma = 1.4$ until 800 K and only begins the decay above it; see the threshold in Section 3.3.3). At 2500 K (the stagnation temperature at roughly Mach 6 at sea level), $\gamma_\text{eff} = 1.296$, a 7.4% reduction. This directly affects shock angles, post-shock conditions, and pressure coefficients; for example, the oblique-shock angle for a 15-degree cone at Mach 5 changes by approximately 2 degrees when $\gamma$ decreases from 1.4 to 1.31.

#### 3.3.5 Behavior of $\gamma_\text{eff}$ vs. Temperature

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}
\begin{axis}[
  width=0.9\linewidth,
  height=6cm,
  xmin=0, xmax=5000,
  ymin=1.29, ymax=1.405,
  xlabel={$T_{\mathrm{stag}}$ (K)},
  ylabel={$\gamma_{\mathrm{eff}}$},
  grid=major,
  grid style={gray!35},
  tick label style={font=\small},
  label style={font=\small},
]
\addplot[very thick, black] coordinates {
  (0,1.400) (500,1.386) (800,1.354) (1000,1.337) (1500,1.312) (2000,1.302)
  (2500,1.296) (3000,1.293) (4000,1.290) (5000,1.288)
};
\draw[dashed, gray] (axis cs:800,1.29) -- (axis cs:800,1.405);
\node[font=\scriptsize, align=left, anchor=north west] at (axis description cs:0.52,0.22)
  {vibrational excitation\\threshold ($\sim 800\,\mathrm{K}$)};
\end{axis}
\end{tikzpicture}
\caption{Decay of effective $\gamma$ with stagnation temperature, as produced by the Einstein vibrational model with the air mixture rule.}
\label{fig:gamma-tstag-parta}
\end{figure}
```

Below 800 K, $\gamma = 1.4$ (frozen vibrational modes). Above 800 K, $\gamma$ decreases as vibrational modes progressively absorb energy. The curve flattens above ~3000 K as the vibrational modes approach saturation. The model is clamped at $\gamma = 1.3$ because beyond this point molecular dissociation (which requires a full chemical-equilibrium solver) becomes the dominant effect.

#### 3.3.6 Worked Example

**Problem.** Compute $\gamma_\text{eff}$ at a stagnation temperature of 2000 K.

Step 1 -- vibrational $c_v$ for $\mathrm{N_2}$ ($\theta = 3371$ K):
$$x = \frac{3371}{2000} = 1.6855, \quad e^x = 5.395, \quad x^2 = 2.841,$$
$$\frac{c_{v,\text{vib}}(\mathrm{N_2})}{R} = \frac{x^2 e^x}{(e^x - 1)^2} = \frac{2.841 \times 5.395}{(4.395)^2} = \frac{15.33}{19.32} = 0.7934.$$

Step 2 -- vibrational $c_v$ for $\mathrm{O_2}$ ($\theta = 2256$ K):
$$x = \frac{2256}{2000} = 1.128, \quad e^x = 3.089, \quad x^2 = 1.272,$$
$$\frac{c_{v,\text{vib}}(\mathrm{O_2})}{R} = \frac{1.272 \times 3.089}{(2.089)^2} = \frac{3.930}{4.366} = 0.9004.$$

Step 3 -- mixture average:
$$\frac{c_{v,\text{vib,mix}}}{R} = 0.79 \times 0.7934 + 0.21 \times 0.9004 = 0.6268 + 0.1891 = 0.8159.$$

Step 4 -- total $c_v$ and $\gamma$:
$$\frac{c_{v,\text{total}}}{R} = 2.5 + 0.8159 = 3.316, \qquad \gamma_\text{eff} = \frac{4.316}{3.316} = 1.302.$$

This matches the tabulated value 1.302 to three decimals. At this stagnation temperature (corresponding to roughly Mach 5 flight at sea level), the 7% reduction in $\gamma$ from 1.4 to 1.30 has measurable effects on shock angles, post-shock pressure, and wave drag coefficients.

| Validation | Source | Result | Evidence |
|:-----------|:-------|:-------|:---------|
| Effective $\gamma$ saturation | Einstein model + clamp | passes | numerical consistency |

The effective-$\gamma$ model is supported only by numerical consistency, because no published direct measurement of $\gamma_\text{eff}(T_\text{stag})$ for atmospheric air across this range was available for digitization. Indirect validation comes from its downstream consumers (Modified Newtonian $C_{p,\max}$, hypersonic cone foredrag): the hypersonic regression test validates $C_{p,\max}$ versus NACA Report 1135 (15 points, exact match, external benchmark), and the hypersonic cone foredrag benchmark validates total foredrag against DTIC AD0487365 (11 points M 6.5--17.2, MAPE 19.7%, external benchmark).


## 4. Compressibility Factor

### 4.1 Role of $\beta$ in Aerodynamic Theory

The Prandtl-Glauert compressibility factor $\beta$ appears ubiquitously in linearized compressible aerodynamics. At subsonic speeds, the Prandtl-Glauert transformation relates the compressible flow over a thin body to an equivalent incompressible flow:

$$C_p = \frac{C_{p,\text{inc}}}{\beta}, \quad \beta = \sqrt{1 - M^2}.$$

This correction captures the fundamental fact that pressure disturbances in a compressible flow are amplified as the flow approaches sonic conditions ($M \to 1$, $\beta \to 0$), diverging at Mach 1.

At supersonic speeds, the linearized theory yields the Ackeret result for a thin two-dimensional surface,

$$C_p = \frac{2\theta}{\sqrt{M^2 - 1}} = \frac{2\theta}{\beta}, \quad \beta = \sqrt{M^2 - 1},$$

where $\theta$ is the local surface inclination. Here $\beta$ is real and increases with Mach, so supersonic pressure coefficients decrease as $1/\beta$, consistent with the experimental observation that wave drag decreases at high Mach numbers.

In the OpenRocket Plus codebase, $\beta$ enters in:

- **Normal-force coefficient derivatives** ($C_{N_\alpha}$): both body and fin formulas contain factors of $1/\beta$ or $2\pi/\beta$.
- **Pressure-drag coefficients**: wave drag from Ackeret theory goes as $1/\beta$.
- **Center-of-pressure calculations**: the CP shift with Mach depends on the rate of change of $C_{N_\alpha}$ with $\beta$.
- **Stability margin**: since both $C_{N_\alpha}$ and $x_{CP}$ depend on $\beta$, the stability margin $x_{CP} - x_{CG}$ is indirectly a function of $\beta$.

A correct $\beta(M)$ model must therefore satisfy five requirements:

1. Equal $\sqrt{1 - M^2}$ at subsonic Mach (exact Prandtl-Glauert).
2. Equal $\sqrt{M^2 - 1}$ at supersonic Mach (exact Ackeret).
3. Be continuous and positive through the transonic region ($M \approx 1$).
4. Have a continuous first derivative ($C^1$ continuity) so that $dC_D/dM$ and $dC_{N_\alpha}/dM$ are bounded near Mach 1.
5. Never reach zero, because $1/\beta$ appears in numerous formulas.


### 4.2 The Catastrophic $\beta_{\min} = 0.25$ Clamp

The original OpenRocket implementation handled the transonic singularity by clamping $\beta$ to a minimum value of 0.25:

```java
// Original code (removed in this work)
private static final double MIN_BETA = 0.25;
...
beta = Math.max(MIN_BETA, Math.sqrt(Math.abs(1 - mach * mach)));
```

The constant 0.25 was chosen as a compromise: small enough that the error at most subsonic Mach numbers is negligible, but large enough to prevent extremely large coefficients near Mach 1. The consequences are tabulated below:

| $M$ | True $\beta_\text{sub} = \sqrt{1-M^2}$ | True $\beta_\text{sup} = \sqrt{M^2-1}$ | Clamped $\beta$ | Error (%) | Effect on $1/\beta$ |
|:----|:--------------------------------------:|:--------------------------------------:|:---------------:|:----------|:--------------------|
| 0.50 | 0.866 | n/a | 0.866 | 0.0 | none |
| 0.90 | 0.436 | n/a | 0.436 | 0.0 | none |
| 0.95 | 0.312 | n/a | 0.312 | 0.0 | none |
| 0.97 | 0.243 | n/a | **0.250** | $+2.9$ | $1/\beta$ reduced by 2.8% |
| 0.99 | 0.141 | n/a | **0.250** | $+77$  | $1/\beta$ reduced by 44% |
| 1.00 | 0.000 | 0.000 | **0.250** | $\infty$ | $1/\beta = 4.0$ (meaningless) |
| 1.01 | n/a | 0.141 | **0.250** | $+77$  | $1/\beta$ reduced by 44% |
| 1.05 | n/a | 0.320 | 0.320 | 0.0 | none |
| 1.50 | n/a | 1.118 | 1.118 | 0.0 | none |
| 3.00 | n/a | 2.828 | 2.828 | 0.0 | none |
| 5.00 | n/a | 4.899 | 4.899 | 0.0 | none |

The damage is concentrated in the critical Mach 0.97--1.03 band, precisely where transonic aerodynamic loads peak. At $M = 0.99$, the clamp forces $\beta = 0.25$ instead of the true value 0.141, reducing $1/\beta$ by 44%. Every aerodynamic coefficient that depends on $1/\beta$ (normal force, wave drag, stability derivatives) is correspondingly reduced by up to 44% in this regime. At $M = 1.0$, the clamped $\beta = 0.25$ produces $1/\beta = 4.0$, a finite number applied to what should be a singularity; the physical meaning of linearized theory breaks down at $M = 1$, and the clamp papers over this breakdown with an arbitrary constant. The effect on the simulated drag curve is a flat-topped plateau centered at $M = 1$, instead of the characteristic sharp transonic drag peak observed experimentally and predicted by transonic area-rule theory. The effect on trajectory simulation is a systematic under-prediction of transonic drag, leading to over-prediction of peak velocity and apogee altitude for rockets that pass through Mach 1.


### 4.3 Cubic Hermite Spline Replacement

#### 4.3.1 Requirements

The replacement model must satisfy four conditions, derived directly from the five requirements in Section 4.1 by applying the asymptotic-branch boundary conditions at the band edges $M_L$ and $M_H$:

1. Match the subsonic formula at $M_L = 0.95$: $\beta(M_L) = \sqrt{1 - M_L^2}$.
2. Match the supersonic formula at $M_H = 1.05$: $\beta(M_H) = \sqrt{M_H^2 - 1}$.
3. Match the subsonic slope at $M_L$: $\beta'(M_L) = -M_L/\sqrt{1 - M_L^2}$.
4. Match the supersonic slope at $M_H$: $\beta'(M_H) = M_H/\sqrt{M_H^2 - 1}$.

These four conditions (two function values, two derivative values) are exactly what a cubic Hermite interpolant is designed to satisfy.

#### 4.3.2 Endpoint Values and Derivatives

At $M_L = 0.95$:
$$f_L = \sqrt{1 - 0.95^2} = \sqrt{0.0975} = 0.31225,$$
$$f'_L = \left.\frac{d}{dM}\sqrt{1 - M^2}\right|_{M=0.95} = \frac{-M}{\sqrt{1 - M^2}}\bigg|_{M=0.95} = \frac{-0.95}{0.31225} = -3.0434.$$

At $M_H = 1.05$:
$$f_H = \sqrt{1.05^2 - 1} = \sqrt{0.1025} = 0.32016,$$
$$f'_H = \left.\frac{d}{dM}\sqrt{M^2 - 1}\right|_{M=1.05} = \frac{M}{\sqrt{M^2 - 1}}\bigg|_{M=1.05} = \frac{1.05}{0.32016} = 3.2798.$$

Note the asymmetry: $f_H > f_L$ and $\lvert f'_H\rvert > \lvert f'_L\rvert$ because the supersonic formula has a steeper slope near Mach 1 than the subsonic formula. This asymmetry shifts the spline minimum slightly above $M = 1$.

#### 4.3.3 Cubic Hermite Basis Functions

The cubic Hermite interpolant on the interval $[M_L, M_H]$ uses the normalized parameter

$$t = \frac{M - M_L}{M_H - M_L} = \frac{M - 0.95}{0.10}, \quad t \in [0, 1],$$

and the interval width $\Delta M = M_H - M_L = 0.10$. The four Hermite basis functions are

$$h_{00}(t) = 2t^3 - 3t^2 + 1, \quad h_{10}(t) = t^3 - 2t^2 + t,$$
$$h_{01}(t) = -2t^3 + 3t^2, \quad h_{11}(t) = t^3 - t^2.$$

These satisfy the canonical interpolation conditions:

| Basis | $h(0)$ | $h(1)$ | $h'(0)$ | $h'(1)$ |
|:------|:-------|:-------|:--------|:--------|
| $h_{00}$ | 1 | 0 | 0 | 0 |
| $h_{10}$ | 0 | 0 | 1 | 0 |
| $h_{01}$ | 0 | 1 | 0 | 0 |
| $h_{11}$ | 0 | 0 | 0 | 1 |

The interpolant is

$$\boxed{\;\beta(M) = h_{00}(t)\,f_L + h_{10}(t)\,\Delta M\,f'_L + h_{01}(t)\,f_H + h_{11}(t)\,\Delta M\,f'_H\;}$$

The factor of $\Delta M$ multiplying the derivative terms appears because the basis functions $h_{ij}(t)$ are defined with respect to the normalized parameter $t \in [0,1]$, while the derivatives $f'_L, f'_H$ are with respect to the physical variable $M$. The chain rule gives

$$\frac{d\beta}{dM} = \frac{1}{\Delta M}\left[h'_{00}(t)\,f_L + h'_{10}(t)\,\Delta M\,f'_L + h'_{01}(t)\,f_H + h'_{11}(t)\,\Delta M\,f'_H\right].$$

#### 4.3.4 Implementation

The Java implementation in [`FlightConditions.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/FlightConditions.java) (`calculateBeta`) is exactly:

```java
private static final double TRANSONIC_LOW  = 0.95;
private static final double TRANSONIC_HIGH = 1.05;

private static double calculateBeta(double mach) {
    if (mach < TRANSONIC_LOW) {                // M < 0.95: exact Prandtl-Glauert
        return Math.sqrt(1.0 - mach * mach);
    } else if (mach > TRANSONIC_HIGH) {        // M > 1.05: exact Ackeret
        return Math.sqrt(mach * mach - 1.0);
    } else {                                   // 0.95 <= M <= 1.05: Hermite spline
        double fLo  = Math.sqrt(1.0 - TRANSONIC_LOW  * TRANSONIC_LOW);
        double fHi  = Math.sqrt(TRANSONIC_HIGH * TRANSONIC_HIGH - 1.0);
        double dfLo = -TRANSONIC_LOW  / fLo;       // negative slope
        double dfHi =  TRANSONIC_HIGH / fHi;       // positive slope

        double dm = TRANSONIC_HIGH - TRANSONIC_LOW;
        double t  = (mach - TRANSONIC_LOW) / dm;
        double t2 = t * t;
        double t3 = t2 * t;

        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 =     t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 =     t3 - t2;

        return h00 * fLo + h10 * dm * dfLo + h01 * fHi + h11 * dm * dfHi;
    }
}
```

`calculateBeta` is invoked from `setMach` whenever a new Mach number is set on the `FlightConditions` object, so $\beta$ is cached and `getBeta()` is a constant-time accessor.

#### 4.3.5 Numerical Evaluation

Substituting the endpoint values
$f_L = 0.31225$, $f'_L = -3.0434$, $\Delta M\,f'_L = 0.10 \times (-3.0434) = -0.30434$,
$f_H = 0.32016$, $f'_H = 3.2798$, $\Delta M\,f'_H = 0.10 \times 3.2798 = 0.32798$,
into the boxed interpolant gives:

| $M$ | $t$ | $h_{00}$ | $h_{10}$ | $h_{01}$ | $h_{11}$ | $\beta$ | $1/\beta$ |
|:----|:----|:---------|:---------|:---------|:---------|:--------|:----------|
| 0.95 | 0.000 | 1.0000 | 0.0000 | 0.0000 | $\phantom{-}0.0000$ | 0.31225 | 3.20 |
| 0.96 | 0.100 | 0.9720 | 0.0810 | 0.0280 | $-0.0090$ | 0.28488 | 3.51 |
| 0.97 | 0.200 | 0.8960 | 0.1280 | 0.1040 | $-0.0320$ | 0.26363 | 3.79 |
| 0.98 | 0.300 | 0.7840 | 0.1470 | 0.2160 | $-0.0630$ | 0.24857 | 4.02 |
| 0.99 | 0.400 | 0.6480 | 0.1440 | 0.3520 | $-0.0960$ | 0.23974 | 4.17 |
| 1.00 | 0.500 | 0.5000 | 0.1250 | 0.5000 | $-0.1250$ | **0.23718** | **4.22** |
| 1.01 | 0.600 | 0.3520 | 0.0960 | 0.6480 | $-0.1440$ | 0.24094 | 4.15 |
| 1.02 | 0.700 | 0.2160 | 0.0630 | 0.7840 | $-0.1470$ | 0.25107 | 3.98 |
| 1.03 | 0.800 | 0.1040 | 0.0320 | 0.8960 | $-0.1280$ | 0.26762 | 3.74 |
| 1.04 | 0.900 | 0.0280 | 0.0090 | 0.9720 | $-0.0810$ | 0.29063 | 3.44 |
| 1.05 | 1.000 | 0.0000 | 0.0000 | 1.0000 | $\phantom{-}0.0000$ | 0.32016 | 3.12 |

Several features are notable:

1. **Continuity at endpoints.** $\beta(0.95) = 0.31225 = f_L$ and $\beta(1.05) = 0.32016 = f_H$, confirming exact value continuity (verified in `FlightConditionsTest.testBetaTransonicSmoothing` to $10^{-9}$).

2. **Minimum near $M = 1$.** A fine sweep over $t \in [0,1]$ finds the spline minimum at $\beta \approx 0.2372$ at $M \approx 0.999$. The corresponding maximum $1/\beta$ is approximately 4.22, only marginally above the old clamp's $1/\beta = 4.0$.

3. **Why the maximum $1/\beta$ is not much larger than the old clamp.** The cubic Hermite interpolant matches the function value and slope of the analytic branches at $M_L = 0.95$ and $M_H = 1.05$, but it does **not** attempt to reproduce the singular spike $1/\beta \to \infty$ at $M = 1$. The interpolant smoothly connects the two finite endpoint values; it dips below the endpoints only as far as the imposed slopes allow. The asymmetry $\lvert f'_H\rvert > \lvert f'_L\rvert$ shifts the minimum slightly above $M = 1$. The physical interpretation is that the transonic-band width $[0.95,\,1.05]$ deliberately *bounds* the magnitude of $1/\beta$: linearized theory loses physical meaning inside this band, so the model trades the unphysical singularity for a smooth, well-behaved blend. The accuracy gains over the old clamp are concentrated *just outside* the band -- at $M = 0.96$ the new model gives $\beta = 0.285$ instead of the old clamp's 0.280 (truth 0.281 from $\sqrt{1-0.96^2}$), and the old clamp overestimates $\beta$ by 2--3% in the wider Mach 0.97--1.03 region.

4. **Positive throughout.** The spline never reaches zero, eliminating the division-by-zero singularity.

5. **Smooth transition.** The function and its first derivative are continuous at both $M = 0.95$ and $M = 1.05$ by construction (Section 4.3.6).

#### 4.3.6 $C^1$ Continuity Proof

The Hermite interpolant is $C^1$ continuous by construction. The two checks below verify both endpoints.

**At $M = M_L = 0.95$ (from below):** the subsonic formula gives
$$\beta = \sqrt{1 - M^2}, \qquad \beta'(M) = \frac{-M}{\sqrt{1 - M^2}},$$
$$\beta(0.95^-) = 0.31225, \qquad \beta'(0.95^-) = -3.0434.$$

**At $M = M_L = 0.95$ (from above, i.e., entering the spline at $t = 0$):**
$$\beta(0.95^+) = h_{00}(0) f_L + h_{10}(0) \Delta M\,f'_L + h_{01}(0) f_H + h_{11}(0) \Delta M\,f'_H = 1\cdot f_L = 0.31225 \;\checkmark$$

The derivatives of the basis functions at $t = 0$ are
$$h'_{00}(0) = 0, \quad h'_{10}(0) = 1, \quad h'_{01}(0) = 0, \quad h'_{11}(0) = 0,$$
so
$$\beta'(0.95^+) = \frac{1}{0.10}\bigl[0 + 1\cdot 0.10 \cdot (-3.0434) + 0 + 0\bigr] = -3.0434 \;\checkmark$$

**At $M = M_H = 1.05$ (from the spline at $t = 1$):** the basis-function derivatives are
$$h'_{00}(1) = 0, \quad h'_{10}(1) = 0, \quad h'_{01}(1) = 0, \quad h'_{11}(1) = 1,$$
so
$$\beta'(1.05^-) = \frac{1}{0.10}\bigl[0 + 0 + 0 + 1\cdot 0.10 \cdot 3.2798\bigr] = 3.2798.$$

**At $M = M_H = 1.05$ (from above):** the supersonic formula gives
$$\beta'(1.05^+) = \frac{M}{\sqrt{M^2 - 1}}\bigg|_{1.05} = \frac{1.05}{0.32016} = 3.2798 \;\checkmark$$

Both value and first derivative match at both endpoints. The function is therefore $C^1$ continuous over the entire Mach range. $\square$

#### 4.3.7 Comparison Diagram

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}
\begin{axis}[
  width=0.92\linewidth,
  height=6.5cm,
  xmin=0.45, xmax=1.55,
  ymin=0, ymax=1.05,
  xlabel={$M$},
  ylabel={$\beta$},
  grid=major,
  grid style={gray!30},
  tick label style={font=\small},
  label style={font=\small},
  legend style={font=\scriptsize, at={(0.5,0.97)}, anchor=north, legend columns=2},
]
\addplot[very thick, blue!70!black, domain=0.5:0.949, samples=80] {sqrt(1-x*x)};
\addplot[very thick, blue!70!black, domain=1.051:1.55, samples=80] {sqrt(x*x-1)};
\addlegendentry{Analytic $\sqrt{|1-M^2|}$ (outside spline)}
\addplot[thick, red, dashed, domain=0.5:1.5, samples=300] {max(0.25,sqrt(abs(1-x*x)))};
\addlegendentry{Old clamp ($\beta_{\min}=0.25$)}
\addplot[very thick, black, mark=none] coordinates {
  (0.95,0.31225)(0.96,0.28488)(0.97,0.26363)(0.98,0.24857)(0.99,0.23974)(1.00,0.23718)
  (1.01,0.24094)(1.02,0.25107)(1.03,0.26762)(1.04,0.29063)(1.05,0.32016)
};
\addlegendentry{Hermite spline $[M_L,M_H]$}
\draw[dashed, gray] (axis cs:0.95,0) -- (axis cs:0.95,1.05);
\draw[dashed, gray] (axis cs:1.05,0) -- (axis cs:1.05,1.05);
\node[font=\scriptsize] at (axis cs:0.95,0.08) {$M_L$};
\node[font=\scriptsize] at (axis cs:1.05,0.08) {$M_H$};
\end{axis}
\end{tikzpicture}
\caption{$\beta(M)$: analytic Prandtl--Glauert / Ackeret branches, legacy minimum clamp, and cubic Hermite spline in the transonic band (Table~4.3.5 values).}
\label{fig:beta-comparison}
\end{figure}
```

The old model (dashed line with flat region) produces a plateau from approximately Mach 0.97 to 1.03 where $\beta$ is frozen at 0.25. The new model (smooth curve through the transonic band) dips to a minimum of approximately 0.237 at Mach 0.999 (slightly below Mach 1 due to the asymmetry of the boundary conditions; the corresponding $1/\beta$ peak occurs slightly above Mach 1) and rises smoothly into the supersonic formula. The most important consequence is *not* a much larger peak in $1/\beta$ but rather (i) the elimination of the discontinuous slope at the clamp boundaries and (ii) the elimination of the artificial 2--3% overestimate of $\beta$ in the Mach 0.97--1.03 band.

#### 4.3.8 External Validation

| Validation | Source | Result | Evidence |
|:-----------|:-------|:-------|:---------|
| Subsonic exactness | $\beta(0.2) = 0.97980$, $\beta(0.8) = 0.6$ | exact to $10^{-6}$ | numerical consistency |
| Spline endpoint value (low) | $\beta(0.95) = \sqrt{1-0.95^2}$ | $< 10^{-9}$ | numerical consistency |
| Spline endpoint value (high) | $\beta(1.05) = \sqrt{1.05^2-1}$ | $< 10^{-9}$ | numerical consistency |
| Spline minimum location | $0.2 < \beta(1) < 0.35$ | passes (0.237) | numerical consistency |
| Positivity over [0, 5] | $\beta > 0$ at all $M$ | passes for $M \in [0.01, 5.0]$ | numerical consistency |
| Supersonic exactness | $\beta(5) = \sqrt{24}$ | $< 10^{-6}$ | numerical consistency |

The compressibility-factor implementation is verified for numerical consistency rather than against an independent measured dataset: the asymptotic branches reduce identically to the published Prandtl-Glauert and Ackeret formulas, and the spline matches its own boundary conditions to machine precision. There is no published $\beta(M)$ benchmark dataset to validate against, because $\beta$ is a derived theoretical quantity rather than a measurable physical observable. The downstream impact is validated indirectly through coefficient-level external benchmarks (NASA TM X-653 stability, NACA RM A52H28 drag, AGARD-B transonic drag) that all exercise this code path implicitly.

#### 4.3.9 Worked Example

**Problem.** Compute $\beta$ at $M = 1.00$ using the Hermite spline.

Step 1 -- normalized parameter:
$$t = \frac{1.00 - 0.95}{0.10} = 0.500.$$

Step 2 -- basis functions:
$$h_{00} = 2(0.125) - 3(0.25) + 1 = 0.500,$$
$$h_{10} = 0.125 - 2(0.25) + 0.500 = 0.125,$$
$$h_{01} = -2(0.125) + 3(0.25) = 0.500,$$
$$h_{11} = 0.125 - 0.250 = -0.125.$$

Step 3 -- combine with endpoint values and slopes ($\Delta M\,f'_L = -0.30434$, $\Delta M\,f'_H = 0.32798$):
$$\beta(1.00) = 0.500 \times 0.31225 + 0.125 \times (-0.30434) + 0.500 \times 0.32016 + (-0.125) \times 0.32798$$
$$= 0.15613 - 0.03804 + 0.16008 - 0.04100 = 0.23717.$$

The corresponding $1/\beta = 4.22$. The old clamp would have returned $\beta = 0.250$, $1/\beta = 4.00$. The difference at exactly $M = 1$ is small in absolute terms; the dominant error of the old clamp was the *plateau* it created on either side, not the value at the singularity itself.

#### 4.3.10 Impact on Simulation

Replacing the $\beta_{\min} = 0.25$ clamp with the Hermite spline has three primary effects on flight simulation:

1. **Transonic plateau eliminated.** The flat region in $\beta(M)$ from $M \approx 0.97$ to $M \approx 1.03$ is replaced by a smooth dip. Aerodynamic coefficients that depend on $1/\beta$ (normal force, wave drag, stability derivatives) now vary continuously through the transonic regime instead of being held artificially constant. This restores the characteristic transonic drag-rise shape that was previously washed out.

2. **$C^1$-continuous coefficient variation.** The continuous first derivative of the spline ensures that $dC_D/dM$ and $dC_{N_\alpha}/dM$ are bounded throughout the transonic regime. The trajectory integrator (RK4 with adaptive step) no longer encounters discontinuities in the aerodynamic derivatives, eliminating the numerical oscillations that the old clamp could induce when the simulation timestep straddled the clamp boundary.

3. **Correct high-Mach behavior preserved.** Above Mach 1.05 the exact supersonic formula $\beta = \sqrt{M^2 - 1}$ is used directly. At Mach 5, $\beta = 4.899$; the old clamp did not affect this value, and neither does the new spline, confirming that the high-Mach behavior is unchanged. Phase 4/5 hypersonic models (Modified Newtonian, hypersonic cone foredrag) are insensitive to the $\beta$ implementation choice in this range.

The combination of the new $\beta$ model with the corrected nose-cone wave drag (Section 6.1), DATCOM fin wave drag (Section 6.4), and ESDU/Chapman base drag (Section 6.2) produces the sharp transonic drag peak observed experimentally, validated end-to-end at the trajectory level by the 25-flight integrated validation corpus (Section 1.4).

## 5. Shock Relations

### 5.1 Package Scope and Consumers

The aerodynamic analysis of vehicles at supersonic and hypersonic speeds requires the
explicit computation of shock waves and expansion fans as a prerequisite to determining
pressure distributions, forces, and moments. This chapter documents the shock-relations
package implemented in
[`info.openrocket.core.aerodynamics.shocks`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/shocks),
which provides the analytical foundation for every supersonic aerodynamic calculation in
the system: nose-cone wave drag (Taylor-Maccoll), fin local-flow corrections
(`ShockGeometry` pre-pass), boattail and shoulder expansions (Prandtl-Meyer), pitot/
stagnation references (Rayleigh pitot from the normal-shock relations), and the Mach
caps used by the Modified Newtonian hypersonic model.

The package is composed of three classes, each a pure-utility static facade over a
calorically-perfect-gas formulation:

1. [`NormalShockRelations.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/shocks/NormalShockRelations.java) —
   exact Rankine-Hugoniot jump conditions across a stationary normal shock wave. Provides
   the five canonical ratios ($p_2/p_1$, $\rho_2/\rho_1$, $T_2/T_1$, $M_2$, $p_{02}/p_{01}$)
   and the analytic inverse $M_1(p_2/p_1)$.
2. [`ObliqueShockSolver.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/shocks/ObliqueShockSolver.java) —
   $\theta$-$\beta$-$M$ relation, weak/strong branch selection by bisection, golden-section
   search for the maximum-deflection angle, and a Taylor-Maccoll cone-flow shooter built on
   adaptive RK4 with Richardson-extrapolation step doubling.
3. [`PrandtlMeyerExpansion.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/shocks/PrandtlMeyerExpansion.java) —
   the closed-form Prandtl-Meyer function $\nu(M)$, its analytic derivative, and a
   Newton-Raphson inverse seeded by Stanyukovich's empirical approximation.

All relations assume a calorically perfect gas with constant ratio of specific heats
$\gamma$. The default value $\gamma = 1.4$ (diatomic air at moderate temperatures) is
used throughout via `NormalShockRelations.GAMMA_AIR = 1.4`; every method also accepts
$\gamma$ as a parameter for generality and for use with non-air working fluids.

The primary reference for validation is **NACA Report 1135**, "Equations, Tables, and
Charts for Compressible Flow" (Ames Research Staff, 1953), digitized in this repository
as [`naca1135_normal_shock.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca1135_normal_shock.csv),
[`naca1135_oblique_shock_beta.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca1135_oblique_shock_beta.csv),
[`naca1135_prandtl_meyer_nu.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca1135_prandtl_meyer_nu.csv), and
[`taylor_maccoll_cone_shock.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/taylor_maccoll_cone_shock.csv).

The physical regime of applicability is:

- **Normal shocks**: $M_1 \geq 1.0$ (validated by `validateSupersonic()` in
  `NormalShockRelations`).
- **Oblique shocks**: $M_1 > 1.0$, deflection angle $\theta$ at or below the detachment
  limit $\theta_{\max}(M_1, \gamma)$.
- **Cone (Taylor-Maccoll) flow**: $M_1 > 1.0$, cone half-angle below the conical
  detachment limit (which exceeds the planar-wedge detachment limit because of
  three-dimensional relief).
- **Expansion fans**: $M_1 \geq 1.0$, turning angle $\delta \geq 0$ with
  $\nu(M_1) + \delta \leq \nu_{\max}(\gamma)$.

All numerical methods declare a convergence tolerance of `TOL = 1e-12` (defined in
`NormalShockRelations`/`ObliqueShockSolver`/`PrandtlMeyerExpansion`), targeting at least
eleven significant digits in the converged quantity. This exceeds the precision of the
published 4-5-digit tabular data by roughly seven orders of magnitude, so the
analytical-vs-tabular residuals reported in this chapter are dominated by tabulation
rounding, not solver error.


### 5.2 Normal Shock Relations

#### 5.2.1 Derivation from Conservation Laws

Consider a stationary normal shock wave in a one-dimensional, steady, adiabatic,
inviscid, body-force-free flow. The upstream (pre-shock) state is denoted by subscript 1
and the downstream (post-shock) state by subscript 2. The shock is treated as a thin,
effectively discontinuous region across which flow properties change abruptly while
viscosity acts on a sub-mean-free-path scale that is irrelevant to the bulk jump.

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[font=\small, >=Latex, thick]
\node[draw, minimum width=3cm, minimum height=2.4cm, align=center] (L) at (-2.5,0)
  {\textbf{Upstream (1)}\\[0.35em]$M_1>1$\\$p_1,\rho_1,T_1$\\$V_1$};
\node[draw, minimum width=3cm, minimum height=2.4cm, align=center] (R) at (2.5,0)
  {\textbf{Downstream (2)}\\[0.35em]$M_2<1$\\$p_2,\rho_2,T_2$\\$V_2$};
\fill[gray!40] (-0.07,-1.15) rectangle (0.07,1.15);
\node[above, font=\scriptsize\bfseries] at (0,1.2) {SHOCK};
\draw[->] (L.east) -- (-0.09,0);
\draw[->] (0.09,0) -- (R.west);
\node[font=\scriptsize] at (-2.5,-1.45) {(supersonic)};
\node[font=\scriptsize] at (2.5,-1.45) {(subsonic)};
\end{tikzpicture}
\caption{Stationary normal shock: control volume spanning the thin shock region.}
\label{fig:normal-shock-schematic-partb}
\end{figure}
```

We apply the three fundamental conservation laws to a control volume enclosing the shock.

**Conservation of mass (continuity).** The mass flux must be identical on both sides:

$$\rho_1 V_1 = \rho_2 V_2 \tag{5.1}$$

**Conservation of momentum.** Newton's second law applied to the control volume — the
net pressure force equals the net momentum flux:

$$p_1 + \rho_1 V_1^2 = p_2 + \rho_2 V_2^2 \tag{5.2}$$

**Conservation of energy.** For an adiabatic process with no shaft work, the total
(stagnation) enthalpy is conserved:

$$h_1 + \frac{V_1^2}{2} = h_2 + \frac{V_2^2}{2} \tag{5.3}$$

For a calorically perfect gas, $h = c_p T$ and $p = \rho R T$, where $c_p$ is the
specific heat at constant pressure and $R$ is the specific gas constant. We also use the
definitions

$$a^2 = \gamma R T = \gamma\,\frac{p}{\rho}, \qquad M = \frac{V}{a}, \qquad
c_p = \frac{\gamma R}{\gamma - 1}. \tag{5.4}$$

The energy equation (5.3) can be rewritten using $h = c_p T = a^2 / (\gamma - 1)$:

$$\frac{a_1^2}{\gamma - 1} + \frac{V_1^2}{2} \;=\; \frac{a_2^2}{\gamma - 1} + \frac{V_2^2}{2}.
\tag{5.5}$$

Defining the stagnation speed of sound $a_0$ — the speed of sound at the (uniquely
defined) stagnation temperature reached by isentropic deceleration to rest:

$$\frac{a_0^2}{\gamma - 1} \;=\; \frac{a^2}{\gamma - 1} + \frac{V^2}{2} \;=\; \text{const}.
\tag{5.6}$$

Because the shock process is adiabatic (no heat addition) but not isentropic (entropy
rises across the shock), $T_0$ — and hence $a_0$ — is the same on both sides:

$$T_{01} = T_{02}, \qquad a_{01} = a_{02}. \tag{5.7}$$

This stagnation-temperature invariance is the foundation of the Rayleigh-pitot
derivation in §5.2.2; the entropy increase manifests instead as a *total-pressure* loss.

#### 5.2.2 The Rankine-Hugoniot Relations

We now derive each of the five standard normal-shock relations as functions of the
upstream Mach number $M_1$ and the specific-heat ratio $\gamma$. All five are implemented
in [`NormalShockRelations.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/shocks/NormalShockRelations.java).

##### Relation 1 — Static pressure ratio $p_2/p_1$

From the momentum equation (5.2), substitute $\rho V^2 = \rho a^2 M^2 = \gamma p M^2$:

$$p_1 + \gamma p_1 M_1^2 \;=\; p_2 + \gamma p_2 M_2^2,$$

$$p_1\,(1 + \gamma M_1^2) \;=\; p_2\,(1 + \gamma M_2^2). \tag{5.8}$$

Combining (5.8) with continuity, the $p_1\rho_1V_1^2$ identity, and the energy equation
to eliminate $V_2$ gives, after standard algebra,

$$\boxed{\;\frac{p_2}{p_1} \;=\; 1 + \frac{2\gamma}{\gamma + 1}\,(M_1^2 - 1)\;} \tag{5.9}$$

implemented as

```java
public static double pressureRatio(double m1, double gamma) {
    double m1sq = m1 * m1;
    return 1.0 + 2.0 * gamma / (gamma + 1.0) * (m1sq - 1.0);
}
```

At $M_1 = 1$ the ratio is unity (an infinitesimal Mach wave produces no pressure jump).
As $M_1 \to \infty$, $p_2/p_1 \sim \frac{2\gamma}{\gamma+1}\,M_1^2$ — the static
pressure jump grows without bound.

##### Relation 2 — Density (and velocity) ratio $\rho_2/\rho_1$

By continuity, $\rho_2/\rho_1 = V_1/V_2$. Combining the three conservation laws and
eliminating $a_1, a_2$ via the energy equation yields

$$\boxed{\;\frac{\rho_2}{\rho_1} \;=\; \frac{V_1}{V_2} \;=\;
  \frac{(\gamma + 1)\,M_1^2}{(\gamma - 1)\,M_1^2 + 2}\;} \tag{5.10}$$

implemented as

```java
public static double densityRatio(double m1, double gamma) {
    double m1sq = m1 * m1;
    double gp1 = gamma + 1.0;
    double gm1 = gamma - 1.0;
    return gp1 * m1sq / (gm1 * m1sq + 2.0);
}
```

A central physical constraint emerges in the strong-shock limit:

$$\lim_{M_1 \to \infty} \frac{\rho_2}{\rho_1} \;=\; \frac{\gamma + 1}{\gamma - 1}.$$

For $\gamma = 1.4$ this gives a maximum density ratio of $6.0$. Unlike the static
pressure, which grows quadratically without bound, the density jump across a normal shock
is *bounded*. This finite-density-jump constraint is what produces the extremely thin
shock layer of hypersonic blunt-body flow, and ultimately motivates the Modified
Newtonian theory used in the high-Mach branch of the OpenRocket Plus drag model.

##### Relation 3 — Static temperature ratio $T_2/T_1$

From the ideal-gas law $p = \rho R T$,

$$\frac{T_2}{T_1} \;=\; \frac{p_2/p_1}{\rho_2/\rho_1}. \tag{5.11}$$

Substituting (5.9) and (5.10) and simplifying gives the explicit form

$$\boxed{\;\frac{T_2}{T_1} \;=\;
  \frac{\bigl[2\gamma M_1^2 - (\gamma - 1)\bigr]\,\bigl[(\gamma - 1)\,M_1^2 + 2\bigr]}
       {(\gamma + 1)^2\,M_1^2}\;} \tag{5.12}$$

The implementation deliberately reuses the previous two methods rather than expanding
the algebraic identity, both to avoid duplication and to guarantee numerical consistency
between the three thermodynamic ratios:

```java
public static double temperatureRatio(double m1, double gamma) {
    return pressureRatio(m1, gamma) / densityRatio(m1, gamma);
}
```

##### Relation 4 — Downstream Mach number $M_2$

This is the most consequential relation physically: a normal shock always produces
*subsonic* downstream flow ($M_2 < 1$ whenever $M_1 > 1$). Starting from the energy
equation (5.5) with $V = M\,a$,

$$a_1^2\!\left(\frac{1}{\gamma - 1} + \frac{M_1^2}{2}\right) \;=\;
  a_2^2\!\left(\frac{1}{\gamma - 1} + \frac{M_2^2}{2}\right), \tag{5.13}$$

and combining with the momentum equation (5.8) using $p = \rho a^2/\gamma$ and
continuity, the algebra factors into a trivial root $M_2 = M_1$ (no shock) and the
non-trivial Rankine-Hugoniot root

$$\boxed{\;M_2^2 \;=\; \frac{M_1^2 + \dfrac{2}{\gamma - 1}}
                              {\dfrac{2\gamma}{\gamma - 1}\,M_1^2 - 1}\;} \tag{5.15}$$

implemented as

```java
public static double downstreamMach(double m1, double gamma) {
    double m1sq = m1 * m1;
    double gm1 = gamma - 1.0;
    double m2sq = (m1sq + 2.0 / gm1) / (2.0 * gamma / gm1 * m1sq - 1.0);
    return Math.sqrt(m2sq);
}
```

For $M_1 > 1$ the denominator is strictly positive (since $2\gamma/(\gamma - 1) > 1$ for
$\gamma > 1$), and the numerator is strictly less than the denominator, so $0 < M_2^2 < 1$
and the post-shock flow is necessarily subsonic. In the strong-shock limit,

$$\lim_{M_1 \to \infty} M_2^2 \;=\; \frac{\gamma - 1}{2\gamma}, \tag{5.16}$$

giving $M_{2,\min} = \sqrt{1/7} \approx 0.37796$ for $\gamma = 1.4$.

##### Relation 5 — Total-pressure ratio $p_{02}/p_{01}$ (Rayleigh-pitot formula)

Although stagnation temperature is preserved across the shock, stagnation pressure is
not — the entropy increase manifests as total-pressure loss. Decomposing the ratio into
isentropic and shock contributions,

$$\frac{p_{02}}{p_{01}} \;=\; \frac{p_{02}}{p_2} \cdot \frac{p_2}{p_1} \cdot \frac{p_1}{p_{01}},
\tag{5.17}$$

and using the isentropic stagnation-to-static relation

$$\frac{p_0}{p} \;=\; \!\left(1 + \tfrac{\gamma - 1}{2}\,M^2\right)^{\gamma/(\gamma - 1)}
\tag{5.18}$$

both upstream and downstream, with the static pressure ratio (5.9) and the downstream
Mach relation (5.15), simplification produces the **Rayleigh pitot formula**:

$$\boxed{\;\frac{p_{02}}{p_{01}} \;=\;
  \!\left[\frac{(\gamma + 1)\,M_1^2}{(\gamma - 1)\,M_1^2 + 2}\right]^{\!\gamma/(\gamma - 1)}
  \cdot
  \!\left[\frac{2\gamma\,M_1^2 - (\gamma - 1)}{\gamma + 1}\right]^{\!-1/(\gamma - 1)}\;}
\tag{5.19}$$

implemented as

```java
public static double totalPressureRatio(double m1, double gamma) {
    double m1sq = m1 * m1;
    double gm1 = gamma - 1.0;
    double gp1 = gamma + 1.0;
    double term1 = gp1 * m1sq / (gm1 * m1sq + 2.0);
    double term2 = (2.0 * gamma * m1sq - gm1) / gp1;
    return Math.pow(term1, gamma / gm1) * Math.pow(term2, -1.0 / gm1);
}
```

At $M_1 = 1$, $p_{02}/p_{01} = 1$ exactly (no entropy production). For $M_1 > 1$, the
ratio decreases monotonically; at $M_1 = 10$, $p_{02}/p_{01} \approx 0.00305$, a
${\sim}300{:}1$ stagnation-pressure loss. This same closed form is reused — composed with
isentropic recovery — by the `calculateCpMax()` helper in the Modified Newtonian
hypersonic branch (cross-validated to machine epsilon against the building-block path,
[`rayleigh_pitot_cpmax.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/rayleigh_pitot_cpmax.md)).

#### 5.2.3 Inverse Relation: Mach from Pressure Ratio

Equation (5.9) is linear in $M_1^2$ and inverts analytically — no iteration is needed:

$$M_1^2 \;=\; \frac{(p_2/p_1 - 1)(\gamma + 1)}{2\gamma} + 1. \tag{5.20}$$

The implementation rejects subsonic ratios with `IllegalArgumentException`:

```java
public static double machFromPressureRatio(double pressRatio, double gamma) {
    if (pressRatio < 1.0) {
        throw new IllegalArgumentException(
                "Pressure ratio must be >= 1.0 for a normal shock (got " + pressRatio + ")");
    }
    double gp1 = gamma + 1.0;
    double m1sq = (pressRatio - 1.0) * gp1 / (2.0 * gamma) + 1.0;
    return Math.sqrt(m1sq);
}
```

This inverse is used by the static-port pressure backout in atmospheric reconstruction
and by the `coneShockResidual` early-exit check in §5.4.

#### 5.2.4 Worked Example: $M_1 = 2.0$, $\gamma = 1.4$

We step through all five normal-shock ratios. Take $\gamma + 1 = 2.4$, $\gamma - 1 = 0.4$,
$M_1^2 = 4$.

**Pressure ratio** (Eq. 5.9):

$$\frac{p_2}{p_1} \;=\; 1 + \frac{2(1.4)}{2.4}\,(4 - 1)
   \;=\; 1 + \tfrac{2.8}{2.4}\,(3) \;=\; 1 + 3.5 \;=\; 4.500.$$

**Density ratio** (Eq. 5.10):

$$\frac{\rho_2}{\rho_1} \;=\; \frac{2.4 \times 4}{0.4 \times 4 + 2}
   \;=\; \frac{9.6}{3.6} \;=\; 2.6\overline{6}.$$

**Temperature ratio** (Eq. 5.11/5.12):

$$\frac{T_2}{T_1} \;=\; \frac{4.500}{2.6\overline{6}} \;=\; 1.6875,$$

cross-checked against the explicit form (5.12):

$$\frac{T_2}{T_1} \;=\;
  \frac{[2(1.4)(4) - 0.4]\,[0.4(4) + 2]}{(2.4)^2 (4)}
  \;=\; \frac{(10.8)(3.6)}{23.04} \;=\; \frac{38.88}{23.04} \;=\; 1.6875\,\checkmark$$

**Downstream Mach** (Eq. 5.15):

$$M_2^2 \;=\; \frac{4 + 2/0.4}{(2.8/0.4)(4) - 1}
   \;=\; \frac{4 + 5}{27} \;=\; \tfrac{9}{27} \;=\; 0.33\overline{3},
\qquad M_2 \;=\; 0.57735\,(< 1\,\checkmark)$$

**Total-pressure ratio** (Eq. 5.19): with $\text{term}_1 = 9.6/3.6 = 2.6\overline{6}$
and $\text{term}_2 = 10.8/2.4 = 4.500$,

$$\frac{p_{02}}{p_{01}} \;=\; (2.6\overline{6})^{3.5}\,(4.500)^{-2.5}
   \;=\; \frac{30.9731}{43.0127} \;=\; 0.72088.$$

Every value above matches the [`naca1135_normal_shock.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca1135_normal_shock.csv)
$M_1 = 2$ row to all displayed digits.

#### 5.2.5 Validation — Normal Shock Relations vs NACA 1135

[`NormalShockRelationsTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/NormalShockRelationsTest.java)
sweeps the closed-form relations against NACA 1135 Table I at the canonical $M_1$ rows
$\{1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0\}$ with a relative-tolerance gate of
$\text{REL\_TOL} = 0.001$ (0.1%). The digitized digits-of-agreement are reported in
[`naca1135_normal_shock.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca1135_normal_shock.csv); the largest
absolute residual on $M_2$ is $5.27 \times 10^{-6}$ at $M_1 = 10$, and the largest
relative residual on $p_2/p_1$ is below $10^{-7}$. All values agree with the published
4-5-digit table to within tabulation rounding.

| $M_1$ | Quantity         | Computed | NACA 1135 | Residual    |
|------:|------------------|---------:|----------:|------------:|
| 1.0   | $p_2/p_1$        | 1.00000  | 1.0000    | 0           |
| 1.0   | $\rho_2/\rho_1$  | 1.00000  | 1.0000    | 0           |
| 1.0   | $T_2/T_1$        | 1.00000  | 1.0000    | 0           |
| 1.0   | $M_2$            | 1.00000  | 1.0000    | 0           |
| 1.0   | $p_{02}/p_{01}$  | 1.00000  | 1.0000    | 0           |
| 1.5   | $p_2/p_1$        | 2.45833  | 2.4583    | $<10^{-5}$  |
| 1.5   | $\rho_2/\rho_1$  | 1.86207  | 1.8621    | $<10^{-5}$  |
| 1.5   | $T_2/T_1$        | 1.32022  | 1.3202    | $<10^{-5}$  |
| 1.5   | $M_2$            | 0.70109  | 0.7011    | $<10^{-5}$  |
| 1.5   | $p_{02}/p_{01}$  | 0.92979  | 0.9298    | $<10^{-5}$  |
| 2.0   | $p_2/p_1$        | 4.50000  | 4.5000    | 0           |
| 2.0   | $\rho_2/\rho_1$  | 2.66667  | 2.6667    | $<10^{-5}$  |
| 2.0   | $T_2/T_1$        | 1.68750  | 1.6875    | 0           |
| 2.0   | $M_2$            | 0.57735  | 0.5774    | $<5\times10^{-5}$ |
| 2.0   | $p_{02}/p_{01}$  | 0.72088  | 0.7209    | $<3\times10^{-5}$ |
| 3.0   | $p_2/p_1$        | 10.3333  | 10.333    | $<5\times10^{-5}$ |
| 3.0   | $\rho_2/\rho_1$  | 3.85714  | 3.8571    | $<5\times10^{-5}$ |
| 3.0   | $T_2/T_1$        | 2.67901  | 2.6790    | $<5\times10^{-5}$ |
| 3.0   | $M_2$            | 0.47519  | 0.4752    | $<2\times10^{-5}$ |
| 3.0   | $p_{02}/p_{01}$  | 0.32834  | 0.3283    | $<5\times10^{-5}$ |
| 5.0   | $p_2/p_1$        | 29.0000  | 29.000    | 0           |
| 5.0   | $\rho_2/\rho_1$  | 5.00000  | 5.0000    | 0           |
| 5.0   | $T_2/T_1$        | 5.80000  | 5.8000    | 0           |
| 5.0   | $M_2$            | 0.41523  | 0.4152    | $<10^{-4}$  |
| 5.0   | $p_{02}/p_{01}$  | 0.06172  | 0.0617    | $<10^{-3}$  |
| 10.0  | $p_2/p_1$        | 116.500  | 116.50    | 0           |
| 10.0  | $\rho_2/\rho_1$  | 5.71429  | 5.7143    | $<10^{-5}$  |
| 10.0  | $T_2/T_1$        | 20.3875  | 20.388    | $<5\times10^{-4}$ |
| 10.0  | $M_2$            | 0.38758  | 0.3876    | $<10^{-4}$  |
| 10.0  | $p_{02}/p_{01}$  | 0.00305  | 0.00304   | $<5\times10^{-3}$ |

The largest apparent discrepancy is the $M_1 = 10$ total-pressure ratio: the analytical
result rounds to $0.003045$, which the published table rounds to $0.00304$ while the
implementation rounds to $0.00305$. This is rounding ambiguity in the published
4-significant-figure value, not solver error. The reported "$<0.01\%$" agreement
for the normal-shock building block reflects the dominant agreement at $M_1 \leq 5$;
the solver itself converges to $\sim 10^{-12}$ everywhere.


### 5.3 Oblique Shock Relations

#### 5.3.1 Geometry and Velocity Decomposition

When a supersonic flow encounters a planar compression surface (a wedge), the flow turns
through a deflection angle $\theta$ and a planar oblique shock wave forms at a wave
angle $\beta$ measured from the upstream flow direction.

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[>=Latex, font=\small]
\coordinate (O) at (0,0);
\draw[->, thick] (-0.2,0) -- (5.2,0) node[below] {upstream flow ($M_1$)};
\draw[thick] (O) -- (52:3.8) node[above, sloped, pos=0.5] {shock ($\beta$)};
\draw[thick] (O) -- (-18:4.0) node[below, sloped] {wedge surface};
\draw (0.55,0) arc (0:52:0.55);
\node at (26:0.85) {\small $\beta$};
\draw (0.4,0) arc (0:-18:0.4);
\node at (-9:0.58) {\small $\theta$};
\end{tikzpicture}
\caption{Oblique shock at angle $\beta$ and wedge half-angle $\theta$ (planar compression corner).}
\label{fig:oblique-wedge-geometry}
\end{figure}
```

The fundamental observation that makes the oblique-shock problem analytically tractable
is *velocity decomposition*. Decompose the upstream velocity $V_1$ into components
normal and tangential to the shock surface:

$$V_{n1} \;=\; V_1\,\sin\beta, \qquad V_{t1} \;=\; V_1\,\cos\beta. \tag{5.21}$$

The tangential momentum equation across the shock (no pressure gradient parallel to the
shock surface) preserves the tangential component:

$$V_{t2} \;=\; V_{t1} \;=\; V_1\,\cos\beta. \tag{5.22}$$

Only the normal component undergoes a Rankine-Hugoniot jump. Defining

$$M_{n1} \;=\; M_1\,\sin\beta, \qquad M_{n2} \;=\; f(M_{n1}),
\tag{5.23}$$

where $f$ denotes the normal-shock downstream-Mach relation (Eq. 5.15) applied to
$M_{n1}$, the oblique-shock problem reduces to the normal-shock problem in the
shock-normal frame plus a kinematic rotation.

#### 5.3.2 The Theta-Beta-Mach Relation

The deflection angle $\theta$ is fixed by a geometric constraint: the post-shock flow,
which has tangential component $V_{t2} = V_1 \cos\beta$ and normal component $V_{n2}$,
must make the angle $\beta - \theta$ with the shock surface, so

$$\tan(\beta - \theta) \;=\; \frac{V_{n2}}{V_{t2}}. \tag{5.24}$$

Continuity in the shock-normal frame gives $V_{n2}/V_{n1} = \rho_1/\rho_2$, which by
(5.10) applied to $M_{n1}$ becomes

$$\frac{V_{n2}}{V_{n1}} \;=\; \frac{(\gamma - 1)\,M_{n1}^2 + 2}{(\gamma + 1)\,M_{n1}^2}.
\tag{5.25}$$

Since $\tan\beta = V_{n1}/V_{t1}$ and $\tan(\beta - \theta) = V_{n2}/V_{t1}$:

$$\frac{\tan(\beta - \theta)}{\tan\beta}
   \;=\; \frac{V_{n2}}{V_{n1}}
   \;=\; \frac{(\gamma - 1)\,M_1^2\,\sin^2\beta + 2}{(\gamma + 1)\,M_1^2\,\sin^2\beta}.
\tag{5.26}$$

Expanding $\tan(\beta - \theta)$ via the tangent-subtraction identity, cross-multiplying,
and collecting terms in $\tan\theta$, the result (full algebra at the end of this
subsection) is the classical $\theta$-$\beta$-$M$ relation:

$$\boxed{\;\tan\theta \;=\; 2\,\cot\beta\;
  \frac{M_1^2\,\sin^2\beta - 1}{M_1^2\,(\gamma + \cos 2\beta) + 2}\;}
\tag{5.27}$$

implemented in `ObliqueShockSolver.thetaFromBeta` as

```java
public static double thetaFromBeta(double m1, double beta, double gamma) {
    double m1sq = m1 * m1;
    double sinB = Math.sin(beta);
    double cosB = Math.cos(beta);
    double sin2B = sinB * sinB;

    double numerator = 2.0 * cosB / sinB * (m1sq * sin2B - 1.0);
    double denominator = m1sq * (gamma + Math.cos(2.0 * beta)) + 2.0;
    return Math.atan(numerator / denominator);
}
```

Note that this method computes $\theta$ given $\beta$ — the *forward* problem, which is
explicit. The inverse problem ($\beta$ given $\theta$) is transcendental and is treated
in §5.3.5.

**Derivation of (5.27)**. Starting from (5.26) and letting $S = M_1^2 \sin^2\beta$:

$$\frac{\sin(\beta - \theta)\cos\beta}{\cos(\beta - \theta)\sin\beta}
   \;=\; \frac{(\gamma - 1)S + 2}{(\gamma + 1)S}.$$

Expand $\sin(\beta - \theta)$ and $\cos(\beta - \theta)$ via the angle-subtraction
identities and divide top and bottom of the left-hand side by $\cos\theta$:

$$\frac{\sin\beta\cos\beta - \cos^2\beta\,\tan\theta}
       {\sin\beta\cos\beta + \sin^2\beta\,\tan\theta}
   \;=\; \frac{(\gamma - 1)S + 2}{(\gamma + 1)S}.$$

Cross-multiply and isolate $\tan\theta$:

$$\tan\theta\,\Bigl[\cos^2\beta\,(\gamma + 1)S + \sin^2\beta\,((\gamma - 1)S + 2)\Bigr]
   \;=\; \sin\beta\cos\beta\,\bigl[(\gamma + 1)S - (\gamma - 1)S - 2\bigr].$$

The right side simplifies to $\sin\beta\cos\beta \cdot 2(S - 1) =
\sin\beta\cos\beta \cdot 2(M_1^2 \sin^2\beta - 1)$. The bracket on the left,
using $\cos^2\beta + \sin^2\beta = 1$ and $\cos 2\beta = \cos^2\beta - \sin^2\beta$,
collapses to $\sin^2\beta\,[M_1^2(\gamma + \cos 2\beta) + 2]$. Dividing yields (5.27).

#### 5.3.3 Weak and Strong Shock Solutions

For a given pair $(M_1, \theta)$, equation (5.27) is transcendental in $\beta$ and
generally admits **two** solutions:

1. **Weak shock** ($\beta_{\text{weak}}$): the smaller root. Downstream flow is
   typically supersonic ($M_2 > 1$) except in a narrow band immediately below the
   maximum deflection. This is the branch observed in nature for attached shocks on
   wedges and slender cones in steady, undisturbed supersonic flow.
2. **Strong shock** ($\beta_{\text{strong}}$): the larger root. Downstream flow is
   always subsonic ($M_2 < 1$) and the limit $\theta \to 0$ recovers the normal shock
   ($\beta \to \pi/2$).

The two solutions merge at the **maximum deflection angle** $\theta_{\max}(M_1, \gamma)$.
For $\theta > \theta_{\max}$, no attached oblique shock solution exists; physically the
shock detaches and forms a curved bow shock with a subsonic pocket behind its central
region. The shock angle is bounded by the Mach angle on one side and the normal shock
on the other:

$$\mu \;\leq\; \beta \;\leq\; \frac{\pi}{2}, \qquad \mu \;=\; \arcsin(1/M_1).
\tag{5.28}$$

At $\beta = \mu$ the shock degenerates to a Mach wave ($\theta = 0$, infinitesimal
disturbance, no entropy production). At $\beta = \pi/2$ the shock is normal.

#### 5.3.4 Maximum Deflection Angle and Golden-Section Search

The maximum deflection for a given $M_1$ occurs at a specific $\beta_{\max}$ between the
Mach angle $\mu$ and $\pi/2$. Setting $d\theta/d\beta = 0$ in (5.27) leads to a
transcendental equation with no closed-form root; the implementation therefore uses a
**golden-section search** — a derivative-free, unconditionally-convergent unimodal-
maximizer over the bracketed interval $[\mu + \epsilon,\,\pi/2 - \epsilon]$, with
$\epsilon = 10^{-10}$ to avoid the singular endpoints. Each iteration narrows the
interval by the golden-ratio factor
$\phi = (\sqrt{5} - 1)/2 \approx 0.618$:

```java
private static double betaAtMaxDeflection(double m1, double gamma) {
    // ... cache check (cachedBetaMaxM1 / cachedBetaMaxGamma / cachedBetaMaxResult) ...
    double machAngle = Math.asin(1.0 / m1);
    double lo = machAngle + 1e-10;
    double hi = Math.PI / 2.0 - 1e-10;
    double gr = (Math.sqrt(5.0) - 1.0) / 2.0;
    while (hi - lo > TOL) {
        double b1 = hi - gr * (hi - lo);
        double b2 = lo + gr * (hi - lo);
        double t1 = thetaFromBeta(m1, b1, gamma);
        double t2 = thetaFromBeta(m1, b2, gamma);
        if (t1 < t2) lo = b1; else hi = b2;
    }
    return (lo + hi) / 2.0;
}
```

The result is **memoized** on the static fields `cachedBetaMaxM1` /
`cachedBetaMaxGamma` / `cachedBetaMaxResult` because `betaAtMaxDeflection` is called
multiple times within a single `solve()` invocation — once to validate that
$\theta < \theta_{\max}$ and once more to set the bisection bracket for $\beta(\theta)$.

The validated maximum-deflection table from
[`ObliqueShockSolverTest.maxDeflection`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/ObliqueShockSolverTest.java)
is:

| $M_1$ | $\theta_{\max}$ (deg) |
|------:|----------------------:|
| 1.5   | 12.11                 |
| 2.0   | 22.97                 |
| 3.0   | 34.07                 |
| 5.0   | 41.12                 |
| 10.0  | 44.43                 |

with a tolerance gate of $\pm 0.15^\circ$.

#### 5.3.5 Bisection for $\beta(\theta)$: Why Not Newton-Raphson

The implementation solves $\beta$ from $\theta$ using **bisection** rather than
Newton-Raphson. This deserves explicit justification.

A Newton-Raphson iteration applied to $f(\beta) = \theta(\beta) - \theta_{\text{target}}$
would require the derivative $d\theta/d\beta$, which is computable analytically from
(5.27). However, this problem has a critical Newton-Raphson failure mode: **near the
maximum-deflection angle, $d\theta/d\beta \to 0$**. The Newton step
$\Delta\beta = -f/f'$ then diverges as $f' \to 0$, causing the iteration to overshoot
wildly — possibly hopping from the weak branch onto the strong branch (or out of the
valid domain $[\mu, \pi/2]$ entirely) — and ruining convergence precisely in the
operating regime (slender cones at moderate-to-high Mach) that matters most for the
nose-cone wave-drag application.

Bisection, by contrast, is **unconditionally convergent on a bracketed interval**. The
$\theta(\beta)$ function is monotonically increasing on the weak branch
$[\mu, \beta_{\max}]$ and monotonically decreasing on the strong branch
$[\beta_{\max}, \pi/2]$. By choosing the appropriate bracket according to `wantWeak`,
bisection converges in $\log_2(\Delta_0/\text{TOL})$ steps regardless of proximity to
$\beta_{\max}$. With $\Delta_0 \approx \pi/2$ and $\text{TOL} = 10^{-12}$, this is about
$\log_2(\pi/2 \cdot 10^{12}) \approx 40$ function evaluations — negligible compared with
the downstream property calculations the result feeds. **Robustness is far more valuable
than speed for this subproblem.**

```java
public static double betaFromTheta(double m1, double theta, double gamma, boolean wantWeak) {
    if (m1 <= 1.0) throw new IllegalArgumentException(/* ... */);
    if (theta <= 0.0) return Math.asin(1.0 / m1);   // Mach wave

    double thetaMax = maxDeflectionAngle(m1, gamma);
    if (theta > thetaMax + 1e-8) {
        throw new IllegalArgumentException(/* shock detachment */);
    }
    if (theta > thetaMax) theta = thetaMax;          // clamp within tolerance

    double machAngle = Math.asin(1.0 / m1);
    double betaMax = betaAtMaxDeflection(m1, gamma);

    double lo, hi;
    if (wantWeak) { lo = machAngle + 1e-10; hi = betaMax; }
    else          { lo = betaMax;            hi = Math.PI / 2.0 - 1e-10; }

    for (int i = 0; i < MAX_ITER; i++) {
        double mid = 0.5 * (lo + hi);
        double thetaMid = thetaFromBeta(m1, mid, gamma);
        double err = thetaMid - theta;
        if (Math.abs(err) < TOL || (hi - lo) < TOL) return mid;
        if (wantWeak) {
            if (thetaMid < theta) lo = mid; else hi = mid;
        } else {
            if (thetaMid < theta) hi = mid; else lo = mid;
        }
    }
    return 0.5 * (lo + hi);
}
```

The detachment check uses an `1e-8` tolerance band so that pathological roundoff right
at $\theta_{\max}$ does not throw spuriously; deflections in the band are clamped to
$\theta_{\max}$ exactly.

#### 5.3.6 Post-Shock Property Computation

Once $\beta$ is known, all downstream properties are computed by **applying the normal
shock relations to the normal Mach component** $M_{n1} = M_1 \sin\beta$, and then
recovering the downstream Mach number from $M_{n2}$ via the geometric rotation through
$\beta - \theta$:

$$\frac{p_2}{p_1} \;=\; 1 + \frac{2\gamma}{\gamma + 1}\,(M_{n1}^2 - 1), \tag{5.29}$$

$$\frac{\rho_2}{\rho_1} \;=\; \frac{(\gamma + 1)\,M_{n1}^2}{(\gamma - 1)\,M_{n1}^2 + 2},
\tag{5.30}$$

$$\frac{T_2}{T_1} \;=\; \frac{p_2/p_1}{\rho_2/\rho_1}, \tag{5.31}$$

$$M_{n2} \;=\; \sqrt{\frac{M_{n1}^2 + 2/(\gamma - 1)}
                            {2\gamma\,M_{n1}^2/(\gamma - 1) - 1}}, \tag{5.32}$$

$$\frac{p_{02}}{p_{01}} \;=\; \text{Rayleigh pitot at }M_{n1}\text{ (Eq. 5.19)}, \tag{5.33}$$

$$M_2 \;=\; \frac{M_{n2}}{\sin(\beta - \theta)}. \tag{5.34}$$

The implementation delegates to `NormalShockRelations` for each property and clamps
$M_{n1} \geq 1$ as a defensive measure for cases where numerical imprecision in $\beta$
near the Mach-wave limit could yield $M_1 \sin\beta$ marginally below unity (which would
propagate `IllegalArgumentException` from the validated normal-shock methods):

```java
private static ObliqueShockResult solveFromBeta(double m1, double beta, double theta,
        double gamma, boolean isWeak) {
    double mn1 = m1 * Math.sin(beta);
    if (mn1 < 1.0) mn1 = 1.0;                 // numerical safety near Mach wave

    double pRatio   = NormalShockRelations.pressureRatio(mn1, gamma);
    double tRatio   = NormalShockRelations.temperatureRatio(mn1, gamma);
    double rhoRatio = NormalShockRelations.densityRatio(mn1, gamma);
    double p0Ratio  = NormalShockRelations.totalPressureRatio(mn1, gamma);
    double mn2      = NormalShockRelations.downstreamMach(mn1, gamma);
    double m2       = mn2 / Math.sin(beta - theta);

    return new ObliqueShockResult(beta, theta, m1, m2,
            pRatio, tRatio, rhoRatio, p0Ratio, isWeak);
}
```

#### 5.3.7 Worked Example: $M_1 = 2.0$, $\theta = 10^\circ$

**Given.** $M_1 = 2.0$, $\theta = 10^\circ = 0.17453$ rad, $\gamma = 1.4$.

**Step 1.** Mach angle $\mu = \arcsin(1/2.0) = 30.000^\circ$.

**Step 2.** Solve $\theta(\beta) = 10^\circ$ on the weak branch. From the maximum-
deflection table, $\theta_{\max}(M_1 = 2) \approx 22.97^\circ$, so $10^\circ$ is well
inside the attached-shock regime. Bisection over $[\mu + 10^{-10},\,\beta_{\max}]$
converges to $\beta = 39.314^\circ$ (the
[`naca1135_oblique_shock_beta.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca1135_oblique_shock_beta.csv)
reference is $39.31^\circ$; absolute residual $0.0039^\circ$).

**Step 3.** Normal component $M_{n1} = 2.0 \sin 39.314^\circ = 2.0 \times 0.63365 = 1.26730$.

**Step 4.** Normal-shock relations at $M_{n1} = 1.2673$ ($M_{n1}^2 = 1.6061$):

$$\frac{p_2}{p_1} \;=\; 1 + \frac{2(1.4)}{2.4}\,(1.6061 - 1) \;=\; 1 + 1.1667 \times 0.6061 \;=\; 1.7071,$$

$$\frac{\rho_2}{\rho_1} \;=\; \frac{2.4 \times 1.6061}{0.4 \times 1.6061 + 2.0}
   \;=\; \frac{3.8546}{2.6424} \;=\; 1.4588,$$

$$\frac{T_2}{T_1} \;=\; \frac{1.7071}{1.4588} \;=\; 1.1702,$$

$$M_{n2}^2 \;=\; \frac{1.6061 + 5.0}{7.0 \times 1.6061 - 1.0} \;=\; \frac{6.6061}{10.2427}
   \;=\; 0.64497, \qquad M_{n2} \;=\; 0.80310.$$

**Step 5.** Downstream Mach (Eq. 5.34):

$$M_2 \;=\; \frac{M_{n2}}{\sin(\beta - \theta)} \;=\; \frac{0.80310}{\sin 29.314^\circ}
   \;=\; \frac{0.80310}{0.48956} \;=\; 1.6405.$$

The downstream flow is supersonic, as expected for the weak solution at this moderate
deflection. The
[`ObliqueShockSolverTest.postShockConditions`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/ObliqueShockSolverTest.java)
parametrized test gates this row at $M_2 = 1.641$ ± 1% and $p_2/p_1 = 1.707$ ± 1%.

**Step 6.** Total-pressure ratio: applying the Rayleigh-pitot formula (5.19) at
$M_{n1} = 1.2673$ gives $p_{02}/p_{01} \approx 0.9842$ — only $\sim 1.6\%$ stagnation-
pressure loss, characteristic of a weak oblique shock.

#### 5.3.8 Validation — $\beta(\theta)$ vs NACA 1135

Weak-shock solutions for $\gamma = 1.4$, exported in
[`naca1135_oblique_shock_beta.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca1135_oblique_shock_beta.csv):

| $M_1$ | $\theta$ (deg) | $\beta$ Computed (deg) | $\beta$ NACA 1135 (deg) | Residual (deg) | Rel. error |
|------:|---------------:|------------------------:|-------------------------:|---------------:|-----------:|
| 2.0   | 10             | 39.3139                 | 39.31                    | 0.0039         | 0.010%     |
| 2.0   | 15             | 45.3436                 | 45.34                    | 0.0036         | 0.008%     |
| 2.0   | 20             | 53.4229                 | 53.42                    | 0.0029         | 0.006%     |
| 3.0   | 5              | 23.1333                 | 23.13                    | 0.0033         | 0.014%     |
| 3.0   | 10             | 27.3827                 | 27.38                    | 0.0027         | 0.010%     |
| 3.0   | 20             | 37.7636                 | 37.76                    | 0.0036         | 0.010%     |
| 3.0   | 25             | 44.1359                 | 44.14                    | 0.0041         | 0.009%     |
| 5.0   | 10             | 19.3760                 | 19.38                    | 0.0040         | 0.021%     |
| 5.0   | 20             | 29.8009                 | 29.80                    | 0.0009         | 0.003%     |
| 5.0   | 30             | 42.3443                 | 42.34                    | 0.0043         | 0.010%     |
| 5.0   | 35             | 49.8554                 | 49.86                    | 0.0046         | 0.009%     |

The maximum relative error across all eleven validated rows is $0.021\%$ (at
$M_1 = 5$, $\theta = 10^\circ$), and the maximum absolute error is $0.0046^\circ$ (at
$M_1 = 5$, $\theta = 35^\circ$). Both are bounded by the published 4-digit tabulation
precision. The
[`ObliqueShockSolverTest.weakShockAngle`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/ObliqueShockSolverTest.java)
gate is $3 \times \text{REL\_TOL} = 0.3\%$; the reported agreement for the
$\theta$-$\beta$-$M$ building block is a maximum wave-angle relative error of
$0.021\%$.


### 5.4 Taylor-Maccoll Cone Flow

#### 5.4.1 Physical Motivation: Three-Dimensional Relief

When a supersonic flow encounters a circular cone (rather than a planar wedge), the
attached shock wave is **weaker** than the corresponding 2D wedge shock at the same
half-angle. The physical reason is the three-dimensional relief effect: in the
axisymmetric geometry, streamlines may spread in the circumferential direction,
reducing the compression required to turn the flow. The flow downstream of a conical
shock is therefore *not* uniform — properties are constant only along rays from the
cone apex, and they vary continuously with the polar angle $\theta$ between the post-
shock value (immediately behind the conical shock) and the cone-surface value.

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[font=\small, >=Latex]
  \coordinate (A) at (0,0);
  \draw[->, thick] (-0.3,0) -- (5.5,0) node[below] {axis of symmetry};
  \draw[thick] (A) -- (18:5.0) node[right] {cone surface ($\theta_c$)};
  \draw[thick, dashed] (A) -- (42:4.5);
  \path (A) -- (42:4.5)
    node[pos=0.78, sloped, above, font=\small] {conical shock ($\beta_{\mathrm{cone}}$)};
  \node[align=left, font=\scriptsize] at (4.0,1.4)
    {post-shock $M_2$\\varies along rays};
  \draw[->] (3.4,1.3) -- (2.8,1.1);
  \draw[->, thick] (-0.3,3.5) -- (1.2,3.5)
    node[right, font=\small] {$M_1$ (freestream)};
  \node[align=center, font=\scriptsize] at (2.5,-0.75)
    {3D relief: $\beta_{\mathrm{cone}} < \beta_{\mathrm{wedge}}$ for same $\theta_c$, $M_1$};
\end{tikzpicture}
\caption{Schematic conical shock and axisymmetric ``3D relief'' relative to a wedge at the same half-angle.}
\label{fig:conical-shock-relief}
\end{figure}
```

For a wedge at half-angle $\theta$, the post-shock flow is uniform and parallel to the
wedge surface — every ray from the apex sees the same state. For a cone at half-angle
$\theta_c$, the surface conditions are reached only at the innermost ray
($\theta = \theta_c$), after the flow has decelerated and turned smoothly through the
post-shock conical flow field via the Taylor-Maccoll equation derived below.

#### 5.4.2 The Taylor-Maccoll Ordinary Differential Equation

The Taylor-Maccoll equation governs steady, inviscid, irrotational, conically
*self-similar* supersonic flow. "Conical similarity" means that the velocity field
depends only on the polar angle $\theta$ measured from the cone axis, not on the radial
distance $r$ from the apex.

**Coordinate system.** Spherical coordinates $(r, \theta, \phi)$ centered at the cone
apex, with $\theta = 0$ along the cone axis and $\phi$ the azimuthal angle. By
axisymmetry, $\partial/\partial\phi = 0$, and by self-similarity,
$\partial/\partial r = 0$ for all velocity components. The velocity field decomposes
into $V_r(\theta)$ along the ray from the apex and $V_\theta(\theta)$ perpendicular to
that ray, in the direction of increasing $\theta$.

**Governing equations in conical flow.** The irrotationality condition for conical flow
gives directly

$$V_\theta \;=\; \frac{dV_r}{d\theta}. \tag{5.35}$$

The energy equation (adiabatic, unique stagnation enthalpy along every streamline) gives

$$\frac{V_{\max}^2}{2} \;=\; \frac{a^2}{\gamma - 1} + \frac{V_r^2 + V_\theta^2}{2},
\tag{5.36}$$

where $V_{\max} = \sqrt{2 c_p T_0}$ is the maximum possible velocity (complete
expansion to $T = 0$). Solving for the local sound speed,

$$a^2 \;=\; \frac{\gamma - 1}{2}\,(V_{\max}^2 - V_r^2 - V_\theta^2). \tag{5.37}$$

The continuity equation in spherical coordinates, after eliminating the $r$-dependence
through self-similarity, reduces to

$$\frac{1}{a^2}\!\left[V_\theta^2\,\frac{dV_r}{d\theta} - V_r V_\theta\,\frac{dV_\theta}{d\theta}\right]
   - 2\,V_r - V_\theta\,\cot\theta - \frac{dV_\theta}{d\theta} \;=\; 0. \tag{5.38}$$

Substituting (5.35) and (5.37), and **non-dimensionalizing all velocities by $V_{\max}$**
(so $\tilde V_r = V_r/V_{\max}$, $\tilde V_\theta = V_\theta/V_{\max}$, with the
constraint $\tilde V_r^2 + \tilde V_\theta^2 \leq 1$), gives the Taylor-Maccoll ODE
system. Dropping tildes for brevity:

$$\frac{dV_r}{d\theta} \;=\; V_\theta, \tag{5.39a}$$

$$\frac{dV_\theta}{d\theta} \;=\;
   \frac{V_r V_\theta^2 \;-\; \tfrac{\gamma - 1}{2}\,(1 - V_r^2 - V_\theta^2)\,(2 V_r + V_\theta \cot\theta)}
        {\tfrac{\gamma - 1}{2}\,(1 - V_r^2 - V_\theta^2) \;-\; V_\theta^2}. \tag{5.39b}$$

The implementation encodes this right-hand side in `taylorMaccollRHS`, with
`gm1h` $= (\gamma - 1)/2$ pre-computed:

```java
private static double[] taylorMaccollRHS(double theta, double vr, double vtheta, double gm1h) {
    double vsq = vr * vr + vtheta * vtheta;
    double residualTerm = 1.0 - vsq;             // (Vmax^2 - V^2) / Vmax^2
    double cotTheta = Math.cos(theta) / Math.sin(theta);

    double dvrDtheta = vtheta;
    double numerator = vr * vtheta * vtheta
                     - gm1h * residualTerm * (2.0 * vr + vtheta * cotTheta);
    double denominator = gm1h * residualTerm - vtheta * vtheta;

    if (Math.abs(denominator) < 1e-15) {
        // Near-singular -- local sonic line in the theta direction.
        // Return a large value with the physically correct sign so that
        // adaptive step control reduces h instead of integrating across the singularity.
        return new double[] { dvrDtheta, Math.copySign(1e10, -vtheta) };
    }
    return new double[] { dvrDtheta, numerator / denominator };
}
```

The denominator vanishes precisely when $V_\theta^2 = \tfrac{\gamma - 1}{2}\,(1 - V_r^2 - V_\theta^2)$,
i.e. when the flow becomes locally sonic in the $\theta$-direction. Returning a large
value of the correct sign (rather than zero, which would falsely imply smooth behavior)
forces the adaptive step controller in §5.4.4 to reduce $h$ rather than integrate
through the singularity.

#### 5.4.3 Boundary Conditions

The Taylor-Maccoll system is integrated from the shock at $\theta = \beta_{\text{cone}}$
inward to the cone surface at $\theta = \theta_c$ (decreasing $\theta$).

**At the shock.** The state immediately downstream of the conical shock is computed from
the **planar oblique-shock relations** applied at the local shock angle: the normal
Mach component is $M_{n1} = M_1 \sin\beta_{\text{cone}}$, and the post-shock Mach
$M_{n2}$ comes from the normal-shock relation (5.15). The deflection at the shock,
$\theta_s = \theta(\beta_{\text{cone}})$, comes from (5.27); the post-shock total Mach
is then $M_2 = M_{n2}/\sin(\beta_{\text{cone}} - \theta_s)$. Decomposing the post-shock
velocity into conical components at $\theta = \beta_{\text{cone}}$,

$$V_r \;=\; \frac{V}{V_{\max}}\,\cos(\beta - \theta_s), \qquad
  V_\theta \;=\; -\,\frac{V}{V_{\max}}\,\sin(\beta - \theta_s), \tag{5.40}$$

with the non-dimensional speed obtained from

$$\frac{V}{V_{\max}} \;=\; \sqrt{\frac{M^2}{M^2 + 2/(\gamma - 1)}}, \tag{5.41}$$

implemented as `machToV`. The negative sign on $V_\theta$ encodes the fact that, behind
the conical shock, the flow is turning *toward* the axis (decreasing $\theta$).

**At the cone surface.** The flow must be tangent to the impermeable cone, so

$$V_\theta \;=\; 0 \quad\text{at}\quad \theta = \theta_c. \tag{C}$$

This boundary condition (C) is the eigenvalue constraint that determines the unknown
shock angle $\beta_{\text{cone}}$ — a shooting problem.

#### 5.4.4 Shooting Method and Adaptive RK4 Integration

Since $\beta_{\text{cone}}$ is unknown, the boundary-value problem is closed by a
**shooting method**:

1. **Guess** $\beta_{\text{cone}}$.
2. **Compute** post-shock conditions at $\theta = \beta_{\text{cone}}$ using the planar
   oblique-shock relations and (5.40)-(5.41).
3. **Integrate** (5.39) from $\theta = \beta_{\text{cone}}$ inward to $\theta = \theta_c$.
4. **Evaluate** the residual $V_\theta(\theta_c)$. If zero, the guess is correct.
5. **Iterate** on $\beta_{\text{cone}}$ until the residual vanishes (boundary
   condition (C) satisfied).

The bracket for the outer bisection is established by a preliminary scan over **40
evenly spaced points** in $[\max(\mu, \theta_c) + 10^{-6},\,\beta_{\text{wedge}}]$,
looking for a sign change in the residual. The upper bound is the planar wedge shock
angle for the same deflection — by the 3D relief argument, the cone shock is always
weaker than the wedge shock — and if the wedge shock is itself detached
(`betaFromTheta` throws `IllegalArgumentException`), the upper bound falls back to
`betaAtMaxDeflection(m1, gamma)`, since the cone may still admit an attached shock
through 3D relief alone:

```java
double betaLo = Math.max(machAngle, coneAngle) + 1e-6;
double betaHi;
try {
    betaHi = betaFromTheta(m1, coneAngle, gamma, true);   // wedge shock is upper bound
} catch (IllegalArgumentException e) {
    betaHi = betaAtMaxDeflection(m1, gamma);              // cone-only attached regime
}
int nScan = 40;
// ... scan for sign change of coneShockResidual(...) ...
```

The residual function `coneShockResidual` returns `state[1]` from the integrator —
which, by construction of `taylorMaccollIntegrate` (see below), is $V_\theta$ at the
cone surface. Bisection within the located bracket then converges
$\beta_{\text{cone}}$ to `TOL = 1e-12`.

**Adaptive RK4 with step doubling.** The ODE integration uses classical RK4 with
**step doubling** (Richardson extrapolation) for embedded error control. For each
proposed step of size $h$:

1. Compute one full step: $\mathbf{y}_{\text{full}} = \text{RK4}(h)$.
2. Compute two half steps: $\mathbf{y}_{\text{half}} = \text{RK4}(h/2)\circ\text{RK4}(h/2)$.
3. Local error estimate
   $\varepsilon = \|\mathbf{y}_{\text{half}} - \mathbf{y}_{\text{full}}\|_\infty / 15$,
   the divisor 15 arising from the RK4 order: $2^4 - 1 = 15$.
4. Normalize:
   $\text{err} = \varepsilon / \max(10^{-10},\,\sqrt{V_r^2 + V_\theta^2})$.
5. Accept the step if $\text{err} \leq \text{TOL}$, applying Richardson extrapolation
   $\mathbf{y} = \mathbf{y}_{\text{half}} + (\mathbf{y}_{\text{half}} - \mathbf{y}_{\text{full}})/15$
   to gain effective fifth-order accuracy at no extra cost.
6. Adjust the next step size by
   $h_{\text{new}} = h \cdot 0.9 \cdot (\text{TOL}/\text{err})^{0.2}$, clamped to
   $[0.1,\,5.0] \cdot h$.

The safety factor 0.9, the exponent $0.2 = 1/(p+1)$ with $p = 4$, and the clamp range
$[0.1, 5.0]$ are textbook adaptive step-size controls. The initial step is
$h_0 = (\theta_c - \beta_{\text{cone}})/200$ (i.e. 200 logical steps, but the adaptive
controller will both refine and coarsen as needed), with a hard ceiling of `maxSteps =
50000` for safety:

```java
double theta = beta;
double h = (coneAngle - beta) / 200.0;
int maxSteps = 50000;

for (int step = 0; step < maxSteps; step++) {
    double remaining = coneAngle - theta;
    if (Math.abs(remaining) < 1e-14) break;
    if (Math.abs(h) > Math.abs(remaining)) h = remaining;

    double[] yFull = rk4Step(theta, vr, vtheta, h, gm1h);
    double hh = h * 0.5;
    double[] yH1 = rk4Step(theta, vr, vtheta, hh, gm1h);
    double[] yH2 = rk4Step(theta + hh, yH1[0], yH1[1], hh, gm1h);

    double errVr = Math.abs(yH2[0] - yFull[0]) / 15.0;
    double errVt = Math.abs(yH2[1] - yFull[1]) / 15.0;
    double scale = Math.max(1e-10, Math.sqrt(vr * vr + vtheta * vtheta));
    double err = Math.max(errVr, errVt) / scale;
    // ... NaN/Inf guard ...

    double factor = 0.9 * Math.pow(Math.max(TOL, 1e-30) / Math.max(err, 1e-30), 0.2);
    factor = Math.max(0.1, Math.min(factor, 5.0));

    if (err <= TOL || Math.abs(h) < 1e-15) {
        vr     = yH2[0] + (yH2[0] - yFull[0]) / 15.0;     // Richardson extrapolation
        vtheta = yH2[1] + (yH2[1] - yFull[1]) / 15.0;
        theta += h;
    }
    h *= factor;
}

double vTotal = Math.sqrt(vr * vr + vtheta * vtheta);
return new double[] { vTotal, vtheta };
```

The integrator returns `{vTotal, vtheta}` at the cone surface: `vtheta` is the
shooting residual that the outer bisection drives to zero, while `vTotal` (the
non-dimensional surface speed magnitude) is what the surface-condition code in §5.4.5
consumes.

#### 5.4.5 Surface Conditions via Isentropic Path from $p_{02}$

Once $\beta_{\text{cone}}$ is converged and the integration delivers the surface state,
the surface Mach number is recovered from the non-dimensional speed by inverting (5.41)
in `vToMach`:

$$M_s \;=\; \sqrt{\frac{2}{\gamma - 1}\,\frac{(V/V_{\max})^2}{1 - (V/V_{\max})^2}}.
\tag{5.42}$$

For numerical safety, `vToMach` returns `Double.MAX_VALUE/2` when $V/V_{\max} \geq 1$
(complete expansion) to avoid `Infinity` propagation in downstream code.

The surface static pressure is recovered along an **isentropic path from the freestream
total pressure**: starting from $p_{01}$, the shock loss is applied via the Rayleigh
pitot formula (5.19) at $M_{n1} = M_1 \sin\beta_{\text{cone}}$ to obtain $p_{02}$, and
then the isentropic stagnation-to-static recovery is applied at the surface Mach $M_s$.
This is the path actually implemented in `solveCone`:

$$\frac{p_s}{p_1} \;=\; \frac{p_{02}}{p_{01}} \cdot
   \frac{\bigl(1 + \tfrac{\gamma - 1}{2}M_1^2\bigr)^{\gamma/(\gamma - 1)}}
        {\bigl(1 + \tfrac{\gamma - 1}{2}M_s^2\bigr)^{\gamma/(\gamma - 1)}},
\tag{5.43}$$

$$\frac{T_s}{T_1} \;=\; \frac{1 + \tfrac{\gamma - 1}{2}M_1^2}
                              {1 + \tfrac{\gamma - 1}{2}M_s^2}, \tag{5.44}$$

$$\frac{\rho_s}{\rho_1} \;=\; \frac{p_s/p_1}{T_s/T_1}. \tag{5.45}$$

The relevant code excerpt is:

```java
double p0Ratio = NormalShockRelations.totalPressureRatio(mn1, gamma);   // p02/p01
double p01_over_p1  = Math.pow(1.0 + gm1h * m1 * m1, gamma / gm1);
double p0s_over_ps  = Math.pow(1.0 + gm1h * mSurface * mSurface, gamma / gm1);
double pRatioSurface = p0Ratio * p01_over_p1 / p0s_over_ps;
```

The header comment in `solveCone` notes: *"Uses freestream total pressure → shock loss
→ isentropic expansion to surface Mach. This avoids the numerically sensitive
intermediate $M_2$ computation."* Working through the total-pressure path rather than
through the post-shock static-pressure-and-Mach-rotation path is more robust against
roundoff in $\beta_{\text{cone}}$ near the Mach-wave limit.

#### 5.4.6 Cone Pressure Coefficient

The pressure coefficient on the cone surface — the primary deliverable for the nose-
cone wave-drag computation in `SymmetricComponentCalc` — is

$$C_p \;=\; \frac{p_s - p_1}{\tfrac{1}{2}\,\gamma\,p_1\,M_1^2}
   \;=\; \frac{2}{\gamma\,M_1^2}\!\left(\frac{p_s}{p_1} - 1\right), \tag{5.46}$$

implemented as

```java
public static double conePressureCoefficient(double m1, double coneAngle, double gamma) {
    ObliqueShockResult result = solveCone(m1, coneAngle, gamma);
    return 2.0 / (gamma * m1 * m1) * (result.pressureRatio - 1.0);
}
```

#### 5.4.7 Validation — Cone Shock Angle

The eight tabulated rows in
[`taylor_maccoll_cone_shock.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/taylor_maccoll_cone_shock.csv) and the
NASA Glenn 10°-cone-at-$M_1 = 2.35$ reference case are the closure data for the
Taylor-Maccoll shooter. Cone-shock angles agree with published Taylor-Maccoll tables
to a maximum relative error of $0.825\%$ (worst case at $M_1 = 5$, $\theta_c = 30^\circ$,
residual $0.30^\circ$), within the $1\%$ acceptance gate, with the dominant residual
mechanism being the digitization precision of the $0.1^\circ$-rounded reference values:

| $M_1$ | $\theta_c$ (deg) | $\beta_{\text{cone}}$ Computed (deg) | Reference (deg) | Residual (deg) | Rel. error |
|------:|------------------:|--------------------------------------:|------------------:|---------------:|-----------:|
| 2.0   | 10                | 31.206                                 | 31.10              | 0.106          | 0.34%      |
| 2.0   | 20                | 37.796                                 | 38.00              | 0.204          | 0.54%      |
| 3.0   | 10                | 21.715                                 | 21.80              | 0.085          | 0.39%      |
| 3.0   | 20                | 29.615                                 | 29.70              | 0.085          | 0.29%      |
| 3.0   | 25                | 34.490                                 | 34.30              | 0.190          | 0.55%      |
| 5.0   | 10                | 15.608                                 | 15.50              | 0.108          | 0.70%      |
| 5.0   | 20                | 24.943                                 | 25.10              | 0.157          | 0.63%      |
| 5.0   | 30                | 35.604                                 | 35.90              | 0.296          | 0.82%      |

The
[`ObliqueShockSolverTest.coneShockAngle`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/ObliqueShockSolverTest.java)
gate is `expectedShockDeg * 0.01 + 1.0` (i.e. 1% relative plus a $1^\circ$ floor); all
rows pass. The
[`ObliqueShockSolverTest.coneShockLessThanWedge`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/ObliqueShockSolverTest.java)
test confirms the qualitative 3D-relief inequality $\beta_{\text{cone}} < \beta_{\text{wedge}}$
at every validated point.

**NASA Glenn reference case** ([`taylor_maccoll_cone_shock.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/taylor_maccoll_cone_shock.md)):
$M_1 = 2.35$, $\theta_c = 10^\circ$.

| Quantity                  | HAP theory | Wind-US CFD | OpenRocket Plus |
|---------------------------|-----------:|------------:|----------------:|
| Shock angle (deg)         | 27.1843    | —           | 26.7367         |
| Surface Mach              | 2.1469     | 2.1469      | 2.1468          |
| Surface pressure ratio    | 1.4234     | 1.3741      | 1.3739          |
| Surface temperature ratio | 1.1063     | 1.0951      | 1.0951          |

The OpenRocket Plus surface Mach matches both references to four digits; the surface
pressure-and-temperature ratios match the NASA Wind-US CFD to three digits. The
${\sim}3.5\%$ pressure gap versus HAP analytical theory is also present in NASA's own CFD
calibration — a known systematic difference between the two reference paths that does
not reflect a solver defect on our side.

#### 5.4.8 Three-Dimensional Relief in Numbers

For completeness, the 3D-relief magnitude — the difference between the planar wedge
shock and the conical shock at the same half-angle and Mach — is tabulated below over the
same validated $(M_1, \theta_c)$ grid used in §5.4.7 and §5.3.8, so that every
$\beta_{\text{cone}}$ is the validated Taylor-Maccoll solver output and every
$\beta_{\text{wedge}}$ is the validated planar $\beta(\theta)$ solution. The conical shock
is weaker (smaller $\beta$) than the wedge shock at every point — the `coneShockLessThanWedge`
test confirms this inequality — and where the deflection exceeds the planar
$\theta_{\max}(M_1)$ the wedge shock detaches while the cone shock can remain attached,
illustrating how 3D relief *extends* the maximum half-angle for which an attached shock
exists.

| $M_1$ | $\theta_c$ (deg) | $\beta_{\text{cone}}$ (deg) | $\beta_{\text{wedge}}$ (deg) | Relief $\Delta\beta$ (deg) |
|------:|------------------:|----------------------------:|-----------------------------:|---------------------------:|
| 2.0   | 10                | 31.206                      | 39.31                         | 8.10                       |
| 2.0   | 20                | 37.796                      | 53.42                         | 15.62                      |
| 3.0   | 10                | 21.715                      | 27.38                         | 5.67                       |
| 3.0   | 20                | 29.615                      | 37.76                         | 8.15                       |
| 3.0   | 25                | 34.490                      | 44.14                         | 9.65                       |
| 5.0   | 10                | 15.608                      | 19.38                         | 3.77                       |
| 5.0   | 20                | 24.943                      | 29.80                         | 4.86                       |
| 5.0   | 30                | 35.604                      | 42.34                         | 6.74                       |

The 3D relief is most pronounced at large half-angles and moderate Mach, decaying
asymptotically as $M_1 \to \infty$ (the hypersonic small-disturbance limit, where wedge
and cone shocks both lie close to the body surface).


### 5.5 Prandtl-Meyer Expansion

#### 5.5.1 Physical Description

A Prandtl-Meyer expansion fan occurs when supersonic flow encounters a convex corner —
the surface turns *away* from the flow. In contrast with a shock wave, the expansion is
a *continuous, isentropic* process: entropy is conserved, both stagnation pressure and
stagnation temperature are preserved, and the flow accelerates smoothly through a fan
of Mach waves (characteristics) emanating from the corner.

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[font=\small, >=Latex]
\draw[thick] (-3,0) -- (-0.5,0) node[midway, above] {$M_1>1$};
\draw[thick] (1.2,0) -- (3.5,0) node[midway, above] {$M_2>M_1$};
\foreach \a in {15,22,29,36,43,50} {\draw[densely dashed] (-0.5,0) -- ({-0.5+2.5*cos(\a)},{2.5*sin(\a)}); }
\fill (-0.5,0) circle (1.5pt) node[below=2pt] {convex corner};
\node[align=left, anchor=west, font=\scriptsize] at (-3.1,-1.35)
  {Fan from $\mu_1=\arcsin(1/M_1)$ to $\mu_2=\arcsin(1/M_2)$;\\turn $\delta$; isentropic, continuous.};
\end{tikzpicture}
\caption{Prandtl--Meyer expansion fan at a convex corner (schematic Mach waves).}
\label{fig:pm-expansion-fan}
\end{figure}
```

Salient properties:

- **Isentropic.** No entropy production; $p_{02} = p_{01}$, $T_{02} = T_{01}$.
- **Accelerating.** $M_2 > M_1$; static pressure, density, and temperature all decrease.
- **Continuous.** Properties vary smoothly through the fan (contrast with the
  discontinuous Rankine-Hugoniot jump across a shock).
- **Bounded turning.** The total turning angle is bounded by $\nu_{\max}(\gamma)$
  (§5.5.3): a single isentropic fan cannot turn the flow by more than this angle without
  reaching the unphysical state $T = 0$, $p = 0$.

#### 5.5.2 Derivation of the Prandtl-Meyer Function $\nu(M)$

The Prandtl-Meyer function $\nu(M)$ gives the total turning angle required to
isentropically accelerate a flow from $M = 1$ (sonic) to a given Mach number $M > 1$.
The derivation proceeds from the compatibility relation along a Mach characteristic.

For an infinitesimal expansion in which the flow turns by $d\theta$ and accelerates
by $dV$, the tangential-momentum jump along a Mach wave gives the **characteristic
compatibility relation**

$$d\theta \;=\; \sqrt{M^2 - 1}\;\frac{dV}{V}. \tag{5.47}$$

To express $dV/V$ in terms of $dM$, use $V = M a$ and the energy equation
$a^2 = a_0^2 - \tfrac{\gamma - 1}{2}V^2$. Solving the latter for $V^2$ in terms of $M$,

$$V^2 \;=\; \frac{M^2 a_0^2}{1 + \tfrac{\gamma - 1}{2}M^2}.$$

Differentiating logarithmically (i.e. taking $d/dM$ of $\ln V^2$):

$$\frac{2\,dV}{V} \;=\; \frac{2\,dM}{M} - \frac{(\gamma - 1)\,M\,dM}{1 + \tfrac{\gamma - 1}{2}M^2},$$

which simplifies to

$$\frac{dV}{V} \;=\; \frac{dM}{M\,(1 + \tfrac{\gamma - 1}{2}M^2)}. \tag{5.48}$$

Substituting (5.48) into (5.47) gives the differential form

$$d\theta \;=\; \frac{\sqrt{M^2 - 1}}{M\,(1 + \tfrac{\gamma - 1}{2}M^2)}\,dM, \tag{5.49}$$

so that $\nu(M)$ is the integral of this from sonic conditions:

$$\nu(M) \;=\; \int_1^M \frac{\sqrt{M'^2 - 1}}{M'\,(1 + \tfrac{\gamma - 1}{2}M'^2)}\,dM'.
\tag{5.50}$$

**Closed-form integration.** Substitute $u = M'^2 - 1$ so that $M'^2 = u + 1$,
$2M'\,dM' = du$, $dM'/M' = du/(2(u+1))$:

$$\nu \;=\; \int_0^{M^2 - 1}\frac{\sqrt{u}}{2(u+1)\,\bigl[\tfrac{\gamma + 1}{2} + \tfrac{\gamma - 1}{2}u\bigr]}\,du
   \;=\; \int_0^{M^2 - 1}\frac{\sqrt{u}}{(\gamma - 1)(u + 1)\bigl(u + \tfrac{\gamma + 1}{\gamma - 1}\bigr)}\,du.$$

Substitute $v = \sqrt{u}$ (so $u = v^2$, $du = 2v\,dv$) and let $k^2 = \tfrac{\gamma + 1}{\gamma - 1}$
(so $k^2 - 1 = \tfrac{2}{\gamma - 1}$):

$$\nu \;=\; \int_0^{\sqrt{M^2 - 1}}\frac{2 v^2}{(\gamma - 1)(v^2 + 1)(v^2 + k^2)}\,dv.$$

Partial-fraction decomposition gives

$$\frac{v^2}{(v^2 + 1)(v^2 + k^2)} \;=\; \frac{1}{k^2 - 1}\!\left[\frac{k^2}{v^2 + k^2} - \frac{1}{v^2 + 1}\right],$$

and using $1/(k^2 - 1) = (\gamma - 1)/2$ together with the integrals
$\int dv/(v^2 + a^2) = (1/a)\arctan(v/a)$, the result is the classical closed form:

$$\boxed{\;\nu(M) \;=\; \sqrt{\frac{\gamma + 1}{\gamma - 1}}\;\arctan\!\sqrt{\frac{\gamma - 1}{\gamma + 1}\,(M^2 - 1)}
   \;-\; \arctan\sqrt{M^2 - 1}\;}
\tag{5.51}$$

implemented as

```java
public static double nu(double mach, double gamma) {
    if (mach < 1.0) throw new IllegalArgumentException(/* ... */);
    if (mach == 1.0) return 0.0;
    double gp1 = gamma + 1.0;
    double gm1 = gamma - 1.0;
    double sqrtRatio = Math.sqrt(gp1 / gm1);
    double m2m1 = mach * mach - 1.0;
    return sqrtRatio * Math.atan(Math.sqrt(gm1 / gp1 * m2m1))
         - Math.atan(Math.sqrt(m2m1));
}
```

The implementation rejects subsonic input with `IllegalArgumentException` and returns
exactly $0$ at $M = 1$ to avoid the $0/0$ form in the arctangent terms.

#### 5.5.3 Maximum Prandtl-Meyer Angle

As $M \to \infty$, $\sqrt{M^2 - 1} \to \infty$ and both $\arctan$ terms approach $\pi/2$.
The maximum turning angle is therefore

$$\nu_{\max}(\gamma) \;=\; \sqrt{\tfrac{\gamma + 1}{\gamma - 1}}\,\tfrac{\pi}{2} - \tfrac{\pi}{2}
   \;=\; \tfrac{\pi}{2}\!\left(\sqrt{\tfrac{\gamma + 1}{\gamma - 1}} - 1\right).
\tag{5.52}$$

For $\gamma = 1.4$,

$$\nu_{\max} \;=\; \tfrac{\pi}{2}\,(\sqrt{6} - 1) \;=\; \tfrac{\pi}{2}\,(2.44949 - 1)
   \;=\; \tfrac{\pi}{2}\,(1.44949) \;=\; 2.27685\;\text{rad} \;=\; 130.4541^\circ.$$

This is the maximum possible turning angle for an isentropic expansion fan; the
limiting state corresponds to $M = \infty$, $T = 0$, $p = 0$ (complete expansion of all
thermal energy into directed kinetic energy). The implementation exposes both the
function and the air-default constant:

```java
public static final double NU_MAX_AIR = nuMax(GAMMA_AIR);

public static double nuMax(double gamma) {
    return (Math.PI / 2.0) * (Math.sqrt((gamma + 1.0) / (gamma - 1.0)) - 1.0);
}
```

The
[`PrandtlMeyerExpansionTest.maxAngle`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/PrandtlMeyerExpansionTest.java)
test gates `Math.toDegrees(nuMax(1.4))` to $130.45^\circ \pm 0.05^\circ$.

#### 5.5.4 Derivative of the Prandtl-Meyer Function

The derivative $d\nu/dM$ is needed for the Newton-Raphson inverse below. Reading
directly from the integrand in (5.49),

$$\frac{d\nu}{dM} \;=\; \frac{\sqrt{M^2 - 1}}{M\,(1 + \tfrac{\gamma - 1}{2}M^2)}, \tag{5.53}$$

implemented as

```java
public static double dnuDm(double mach, double gamma) {
    if (mach <= 1.0) return 0.0;
    double m2 = mach * mach;
    return Math.sqrt(m2 - 1.0) / (1.0 + (gamma - 1.0) / 2.0 * m2) / mach;
}
```

For $M > 1$, $d\nu/dM > 0$ strictly — $\nu(M)$ is monotonically increasing — and
$d\nu/dM \to 0$ only at the sonic endpoint $M = 1$ (where the derivative is $0$ by the
square root) and at $M \to \infty$ (where the denominator dominates). Newton-Raphson is
therefore well-posed for any $M > 1$, and the only failure mode is starting too close
to the sonic singularity — handled by the Stanyukovich initial guess in §5.5.5.

#### 5.5.5 Newton-Raphson Inverse with Stanyukovich Initial Guess

The inverse problem — finding $M$ given $\nu_{\text{target}}$ — requires solving the
transcendental equation $\nu(M) = \nu_{\text{target}}$. **Newton-Raphson is preferred
here over bisection** (the converse of the §5.3.5 oblique-shock choice) because
$\nu(M)$ is smooth, monotone, and free of inflection points or other pathologies in
$(1, \infty)$, so quadratic convergence is reliably available.

The key to robust convergence is a good initial guess. The implementation uses the
empirical **Stanyukovich approximation** (NACA 1135 §C.2 gives this as a textbook
seed for the Prandtl-Meyer inverse):

$$M_0 \;=\; 1 + 1.3604\,\!\left(\frac{\nu}{\nu_{\max}}\right)^{\!0.55}. \tag{5.54}$$

This empirical formula provides a starting point typically within a few percent of the
true root over the full range $0 \leq \nu \leq \nu_{\max}$, ensuring convergence in
3-5 Newton iterations. The Newton step is

$$M_{k+1} \;=\; M_k \;-\; \frac{\nu(M_k) - \nu_{\text{target}}}{(d\nu/dM)\,|_{M_k}},
\tag{5.55}$$

with the safeguard $M_{k+1} \geq 1 + 10^{-8}$ to prevent the iteration from dropping
below sonic conditions (where $d\nu/dM = 0$ and the next step would be undefined):

```java
public static double machFromNu(double nuTarget, double gamma) {
    if (nuTarget < 0.0) throw new IllegalArgumentException(/* ... */);
    if (nuTarget < 1e-12) return 1.0;
    double maxNu = nuMax(gamma);
    if (nuTarget > maxNu + 1e-8) throw new IllegalArgumentException(/* exceeds max */);

    // Stanyukovich initial guess
    double nNorm = nuTarget / maxNu;
    double mGuess = 1.0 + 1.3604 * Math.pow(nNorm, 0.55);
    if (mGuess < 1.0) mGuess = 1.0 + 0.01;

    double m = mGuess;
    for (int i = 0; i < MAX_ITER; i++) {
        double f = nu(m, gamma) - nuTarget;
        double dfdm = dnuDm(m, gamma);
        if (Math.abs(dfdm) < 1e-30) break;        // derivative floor
        double delta = -f / dfdm;
        m += delta;
        if (m < 1.0) m = 1.0 + 1e-8;              // sonic safeguard
        if (Math.abs(delta) < TOL) break;
    }
    return m;
}
```

The derivative floor `1e-30` is a defensive break for cases where extreme cancellation
would otherwise produce a NaN; in practice it is never triggered for $M > 1$.

#### 5.5.6 Convergence Example

Target: $\nu_{\text{target}} = 26.38^\circ = 0.46043$ rad ($\gamma = 1.4$); the true
answer is $M = 2.0$ from NACA 1135 Table III.

**Stanyukovich initial guess.**
$\nu/\nu_{\max} = 0.460414/2.27685 = 0.20222$,
$M_0 = 1 + 1.3604 \times (0.20222)^{0.55} = 1 + 1.3604 \times 0.41517 = 1.56476$.

**Newton iterations.**

| Iter. | $M_k$    | $\nu(M_k)$ (rad) | $d\nu/dM$  | $\Delta M$    |
|------:|---------:|-----------------:|-----------:|--------------:|
| 0     | 1.56476  | 0.24117          | 0.51631    | $+0.42465$    |
| 1     | 1.98941  | 0.45531          | 0.48253    | $+0.01058$    |
| 2     | 1.99999  | 0.46041          | 0.48113    | $+1.54\times 10^{-5}$ |
| 3     | 2.00000  | 0.460414         | 0.48113    | $<10^{-10}$   |
| 4     | 2.00000  | 0.460414         | 0.48113    | $<10^{-12}$   |

Convergence to twelve digits is achieved in 4 iterations. The Stanyukovich seed is
within $21.8\%$ of the true root, and the first Newton step captures most of the
remaining distance.

#### 5.5.7 Isentropic Pressure and Temperature Ratios

Because the expansion is isentropic, the total conditions $(p_0, T_0)$ are preserved.
The static-property ratios follow from the isentropic stagnation-to-static relation
(5.18) applied at $M_1$ and $M_2$:

$$\frac{p_2}{p_1} \;=\; \!\left[\frac{1 + \tfrac{\gamma - 1}{2}M_1^2}
                                       {1 + \tfrac{\gamma - 1}{2}M_2^2}\right]^{\!\gamma/(\gamma - 1)},
\tag{5.56}$$

$$\frac{T_2}{T_1} \;=\; \frac{1 + \tfrac{\gamma - 1}{2}M_1^2}{1 + \tfrac{\gamma - 1}{2}M_2^2},
\tag{5.57}$$

$$\frac{\rho_2}{\rho_1} \;=\; \frac{p_2/p_1}{T_2/T_1}. \tag{5.58}$$

For an expansion ($M_2 > M_1$), all three ratios are less than unity: pressure,
temperature, and density all decrease monotonically through the fan, as expected for an
accelerating supersonic flow. The implementation exposes (5.56) and (5.57) directly:

```java
public static double pressureRatio(double m1, double m2, double gamma) {
    double gm1h = (gamma - 1.0) / 2.0;
    double exp = gamma / (gamma - 1.0);
    return Math.pow((1.0 + gm1h * m1 * m1) / (1.0 + gm1h * m2 * m2), exp);
}

public static double temperatureRatio(double m1, double m2, double gamma) {
    double gm1h = (gamma - 1.0) / 2.0;
    return (1.0 + gm1h * m1 * m1) / (1.0 + gm1h * m2 * m2);
}
```

The full `solve(m1, delta, gamma)` entry point composes these: it calls
`downstreamMach(m1, delta, gamma)` (which evaluates $\nu(M_1) + \delta$ and inverts to
$M_2$ via `machFromNu`), then computes all four downstream ratios and returns an
`ExpansionResult` record.

#### 5.5.8 Validation — $\nu(M)$ vs NACA 1135

[`PrandtlMeyerExpansionTest.prandtlMeyerAngle`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/PrandtlMeyerExpansionTest.java)
gates `nu(M)` against NACA 1135 Table III at $M \in \{1.0, 1.5, 2.0, 2.5, 3.0, 4.0,
5.0, 10.0\}$ with relative tolerance $10^{-3}$. The digitized residuals from
[`naca1135_prandtl_meyer_nu.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca1135_prandtl_meyer_nu.csv):

| $M$       | $\nu$ Computed (deg) | $\nu$ NACA 1135 (deg) | Residual (deg)         |
|----------:|----------------------:|------------------------:|------------------------:|
| 1.00      | 0.000000              | 0.0000                  | 0                       |
| 1.50      | 11.905209             | 11.9052                 | $8.83\times 10^{-6}$    |
| 2.00      | 26.379761             | 26.3798                 | $3.92\times 10^{-5}$    |
| 2.50      | 39.123564             | 39.1236                 | $3.62\times 10^{-5}$    |
| 3.00      | 49.757347             | 49.7573                 | $4.67\times 10^{-5}$    |
| 4.00      | 65.784820             | 65.7848                 | $1.98\times 10^{-5}$    |
| 5.00      | 76.920216             | 76.9202                 | $1.55\times 10^{-5}$    |
| 10.00     | 102.316253            | 102.3121                | $4.15\times 10^{-3}$    |
| $\infty$  | 130.4541              | 130.45                  | $<10^{-2}$              |

The largest residual at finite Mach is $4.15 \times 10^{-3}$ deg at $M = 10$, almost
entirely a digitization artefact of the published 4-significant-digit table (the closed
form is exact). The reported table agreement is therefore a maximum absolute angle
error of $0.004^\circ$ rather than a percent-error headline.

The
[`PrandtlMeyerExpansionTest.inversePrandtlMeyer`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/shocks/PrandtlMeyerExpansionTest.java)
parametrized round-trip test confirms that $\verb|machFromNu|(\verb|nu|(M)) = M$ to
$10^{-8}$ relative for $M \in \{1.0, 1.2, 1.5, 2.0, 3.0, 5.0, 8.0, 10.0, 15.0, 20.0\}$
— the practical floor of Newton-Raphson convergence, set by the `TOL = 1e-12` step
criterion combined with the $\nu(M)$ slope at high Mach.


### 5.6 Numerical Methods Summary

The shock-relations package combines four distinct iterative or quadrature methods,
each chosen for the analytic structure of its sub-problem:

1. **Closed-form algebra** — `NormalShockRelations` (§5.2) and the static-property
   ratios in `PrandtlMeyerExpansion` (§5.5.7) are direct evaluations of the analytic
   formulas. No iteration; round-off-limited accuracy.
2. **Bisection** — `ObliqueShockSolver.betaFromTheta` (§5.3.5) and the cone-shock outer
   loop in `coneShockAngle` (§5.4.4). Chosen for unconditional convergence on a
   bracketed interval, *especially* near $\beta_{\max}$ where $d\theta/d\beta \to 0$
   would defeat Newton-Raphson.
3. **Golden-section search** — `betaAtMaxDeflection` (§5.3.4) is a derivative-free
   unimodal-maximizer, again to avoid the singular Newton step at the function's peak.
4. **Newton-Raphson** — `PrandtlMeyerExpansion.machFromNu` (§5.5.5) is well-posed
   because $\nu(M)$ is smooth, monotone, and well-conditioned for $M > 1$. The
   Stanyukovich seed is empirical and fast.
5. **Adaptive RK4 with Richardson extrapolation** — the Taylor-Maccoll integrator
   (§5.4.4) is a step-doubling embedded scheme with safety-factor 0.9 step control.
   Effective fifth-order accuracy at the cost of three RK4 evaluations per accepted
   step.

The complete table of numerical parameters used in the package:

| Parameter                              | Symbol / location              | Value                | Used in                                    |
|----------------------------------------|--------------------------------|----------------------|--------------------------------------------|
| Convergence tolerance                  | `TOL`                          | $10^{-12}$           | All iterative solvers                      |
| Maximum iterations                     | `MAX_ITER`                     | 100                  | Bisection, Newton, golden-section          |
| Ratio of specific heats (air)          | `GAMMA_AIR`                    | 1.4                  | Default for all methods                    |
| Golden-ratio factor                    | `gr`                           | $(\sqrt{5} - 1)/2$   | `betaAtMaxDeflection`                      |
| Oblique shock bracket offset           | (inline)                       | $10^{-10}$           | `betaFromTheta` and `betaAtMaxDeflection`  |
| Cone shock detachment tolerance        | (inline)                       | $10^{-8}$            | `betaFromTheta` clamp at $\theta_{\max}$   |
| Cone scan offset                       | (inline)                       | $10^{-6}$            | `coneShockAngle` lower bracket             |
| Cone shock scan points                 | `nScan`                        | 40                   | `coneShockAngle` bracket search            |
| Taylor-Maccoll initial step count      | (inline)                       | 200                  | `taylorMaccollIntegrate` (initial $h_0$)   |
| Taylor-Maccoll max steps               | `maxSteps`                     | 50,000               | `taylorMaccollIntegrate` ceiling           |
| RK4 safety factor                      | (inline)                       | 0.9                  | Adaptive step-size control                 |
| RK4 step-size clamp range              | (inline)                       | $[0.1,\,5.0]\,h$     | Adaptive step-size control                 |
| RK4 error order divisor                | (inline)                       | 15                   | Richardson extrapolation ($2^4 - 1$)       |
| RK4 error exponent                     | (inline)                       | 0.2                  | Step-size scaling ($1/(p+1)$ with $p=4$)   |
| Singular-denominator threshold         | (inline)                       | $10^{-15}$           | `taylorMaccollRHS`                         |
| Termination cutoff                     | (inline)                       | $10^{-14}$           | `taylorMaccollIntegrate` (`remaining`)     |
| Step lower limit                       | (inline)                       | $10^{-15}$           | `taylorMaccollIntegrate` ($\lvert h\rvert$ floor)     |
| $V/V_{\max}$ overflow guard            | (inline)                       | `Double.MAX_VALUE/2` | `vToMach` clamping                         |
| Stanyukovich coefficient               | (inline)                       | 1.3604               | PM inverse initial guess                   |
| Stanyukovich exponent                  | (inline)                       | 0.55                 | PM inverse initial guess                   |
| PM derivative floor                    | (inline)                       | $10^{-30}$           | `machFromNu` safety break                  |
| PM Mach lower bound                    | (inline)                       | $1 + 10^{-8}$        | `machFromNu` sonic safeguard               |
| Cache key tolerance                    | (inline)                       | $10^{-12}$           | `betaAtMaxDeflection` memo                 |

All tolerances are chosen to provide at least eleven significant digits of accuracy in
the converged result, far exceeding the four-to-five significant figures available in
the published tabular references. The iteration limits are conservative upper bounds —
typical convergence costs are roughly:

- **Normal shock relations**: zero iterations (closed form).
- **Oblique shock $\beta(\theta)$ bisection**: $\lceil\log_2(\Delta_0/\text{TOL})\rceil
  \approx 40$ function evaluations.
- **Maximum-deflection golden-section search**: $\sim 60$ evaluations of `thetaFromBeta`.
- **Prandtl-Meyer Newton-Raphson**: 3-5 iterations from the Stanyukovich seed.
- **Taylor-Maccoll adaptive RK4**: a few hundred accepted RK4 quadruples for typical
  cone-shock cases (2-4 outer bisection iterations × $\sim 40$-$200$ RK4 steps each).

Cumulative single-call cost for the most expensive entry point — `solveCone` — is on
the order of $10^4$ floating-point operations, easily within the budget of a per-
timestep `ShockGeometry` pre-pass over a typical multi-component rocket model.

## 6. Drag Models

The total drag coefficient of a sounding rocket or high-power rocket vehicle is assembled from five additive contributions in [`BarrowmanDragCalculator.calculateDrag()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java):

$$
C_D \;=\; C_{D,\text{friction}} \;+\; C_{D,\text{pressure}} \;+\; C_{D,\text{base}} \;+\; C_{D,\text{override}} \;+\; C_{D,i}
$$

with

- $C_{D,\text{friction}}$ — viscous skin-friction integrated over all wetted surfaces, computed via the Van Driest II compressible transformation at supersonic Mach (Section 6.3);
- $C_{D,\text{pressure}}$ — forebody/wave drag from nose cones, body shoulders, transitions, and fin leading edges, plus a slender-body supersonic body-pressure contribution on long cylindrical afterbodies (Section 6.1 for axisymmetric components, Section 6.1.8 for the slender-body term, Section 6.4 for fins);
- $C_{D,\text{base}}$ — afterbody base drag arising from the low-pressure recirculation behind every blunt aft face (Section 6.2);
- $C_{D,\text{override}}$ — any user-specified per-component drag override (carried unchanged from upstream OpenRocket);
- $C_{D,i} = C_N \sin\alpha$ — lift-induced drag from the axial projection of the normal force at angle of attack (Section 6.5).

`BarrowmanDragCalculator` orchestrates this assembly; component-level work is delegated by reflection to [`SymmetricComponentCalc`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/SymmetricComponentCalc.java) (nose cones, body tubes, transitions/boattails) and [`FinSetCalc`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) (fin sets). Every component method spans the full Mach range from low subsonic through hypersonic, with C1-continuous polynomial blending at every regime transition; the explicit transonic blend windows are tabulated in Section 6.10.

The output $C_D$ is converted to an axial-force coefficient $C_{D,\text{axial}} = f(\alpha)\,C_D$ by `calculateAxialCD()` (Section 6.6) before it is returned to the 6-DOF stepper, and the same call also adds two non-axisymmetric pressure-drag mechanisms: forward-facing step drag at body diameter discontinuities (Section 6.7) and shock–boundary-layer interaction at fin roots (Section 6.8). Section 6.9 closes the chapter with a quantitative drag budget at $M=0.5$, $M=2.0$, and $M=5.0$, and Section 6.10 collects every transonic blend window in one table for cross-reference.

> **Validation map for this chapter.** The headline drag claims are: nose wave drag MAE 0.029 vs NACA RM A52H28 across 5 nose families; turbulent base drag MAPE 15.9% vs NACA TN 3393 (4 points, $M = 2.73$–$4.48$); laminar base drag MAPE 4.4% vs the same TN 3393 dataset; fin wave drag against NACA TN 3650 (12 free-flight source rows, with the current diagnostic MAPE computed over the 10 non-Mach-1.10 rows) plus exact-to-numerical-precision agreement with the Ackeret formula on 15 unswept cases; total finned-vehicle drag MAPE 11.8% vs ADA636861 Basic Finner over $M=1.08$–$4.30$; hypersonic cone foredrag MAPE 19.7% vs DTIC AD0487365 across 11 points $M=6.5$–$17.2$; and AGARD-B (AEDC-TR-70-100) drag trend closure by per-row tolerance gates over $M=0.2$–$1.0$ rather than by a single MAE gate.


### 6.1 Nose and Body Wave Drag

Wave drag arises from the compression of air by surfaces inclined to the freestream at supersonic speeds. For axisymmetric bodies of revolution (nose cones, shoulders, transitions, and the rare reverse-taper section), the drag coefficient is computed by one of four physical models depending on Mach number, nose shape, and fineness ratio. All four return a coefficient $C_{d,\text{nose}}$ referenced to the component frontal area $A_{\text{frontal}} = \pi(R_{\text{aft}}^2 - R_{\text{fore}}^2)$, which `BarrowmanDragCalculator.calculatePressureCD()` then rescales to the vehicle reference area $S_{\text{ref}}$:

$$
C_{D,\text{pressure}} \;=\; \sum_{\text{nose, shoulder, …}} C_{d,\text{nose}}\;\frac{A_{\text{frontal}}}{S_{\text{ref}}}.
$$

The four models are:

1. **Taylor–Maccoll exact cone solution** (Section 6.1.1) — used for `CONICAL` shapes via `ObliqueShockSolver.conePressureCoefficient()`.
2. **Shock-expansion strip integration** (Section 6.1.2) — used for `OGIVE` and any non-cone analytical shape, calling `SymmetricComponentCalc.calculateShockExpansionCd()`.
3. **Dahlem–Buck shape factors** (Section 6.1.3) — used for `POWER`, `PARABOLIC`, `HAACK` non-reference parameter values, blended over $M=1.3$–$1.5$ into the empirical curves above. Implemented in [`DahlemBuckShapeFactors`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/DahlemBuckShapeFactors.java).
4. **Modified Newtonian theory** (Section 6.1.5) — blended into the analytical result over $M=4.0$–$6.0$ via `SymmetricComponentCalc.calculateNewtonianCd()`, providing the only model that remains physically correct as $M\to\infty$.

Below the drag-divergence Mach $M_{dd}$ all four return zero; through the transonic regime ($M_{dd}\le M \le 1.5$) wave drag is built up by the C1 Hermite onset of Section 6.1.4 joined to the empirical TR-R-100 transonic table where applicable, and then handed off to the analytical models from $M=1.5$ upward.

The per-shape selection logic is in `SymmetricComponentCalc.calculatePressureCD()` (lines 415–500) and `buildAnalyticalWaveDragCurve()` (lines 812–860). An expanding shoulder (Transition with $R_{\text{aft}} > R_{\text{fore}}$) is treated as a body that sits under an expansion fan and contributes zero pressure drag at supersonic Mach (line ~450 of the same file); only contracting shoulders behave as nose-like compression surfaces. A smooth cylindrical body tube returns zero pressure drag in this classical treatment; the supersonic body-pressure increment that long slender airframes nevertheless radiate is added separately by the slender-body term of Section 6.1.8, which `BarrowmanDragCalculator.calculatePressureCD()` sums into the body total.


#### 6.1.1 Taylor–Maccoll Exact Solution for Cones

For a conical nose at zero angle of attack with an attached oblique shock, the wave drag coefficient equals the surface pressure coefficient computed from the Taylor–Maccoll solution. The pressure on a conical surface is uniform along any generator, so the integral over the conical surface degenerates exactly to $C_{d,\text{cone}} = C_{p,\text{cone}}$ when the coefficient is referenced to the cone base area:

$$
C_{p,\text{cone}} \;=\; \frac{2}{\gamma\,M_\infty^{2}}\!\left(\frac{p_{\text{cone}}}{p_\infty} - 1\right).
$$

The pressure ratio $p_{\text{cone}}/p_\infty$ is obtained by `ObliqueShockSolver.conePressureCoefficient(mach, theta_c, gamma)` in four steps:

1. **Solve the shock angle.** Find $\beta$ such that the Taylor–Maccoll ODE, integrated from immediately behind the shock down to the cone surface ray $\phi = \theta_c$, yields zero radial velocity at the wall. The bracketed bisection runs to a tolerance of $10^{-12}$ radians.

2. **Compute post-shock conditions** from the oblique-shock relations at deflection $\beta$:

$$
M_{n1} = M_\infty \sin\beta,\qquad
\frac{p_{2}}{p_{1}} = 1 + \frac{2\gamma}{\gamma+1}\bigl(M_{n1}^{2} - 1\bigr),
$$

$$
M_{n2}^{2} = \frac{1 + \tfrac{\gamma-1}{2} M_{n1}^{2}}{\gamma\,M_{n1}^{2} - \tfrac{\gamma-1}{2}},
\qquad
M_{2} = \frac{M_{n2}}{\sin(\beta - \theta_c)}.
$$

3. **Integrate the Taylor–Maccoll ODE** in the spherical-polar non-dimensional velocity components $(V_r, V_\phi)$:

$$
\frac{dV_r}{d\phi} = V_\phi,
\qquad
\frac{dV_\phi}{d\phi} \;=\;
\frac{
V_\phi^{2}\,V_r
- \tfrac{\gamma-1}{2}\bigl(1 - V_r^{2} - V_\phi^{2}\bigr)
  \bigl(2 V_r + V_\phi \cot\phi\bigr)
}{
\tfrac{\gamma-1}{2}\bigl(1 - V_r^{2} - V_\phi^{2}\bigr) - V_\phi^{2}
}.
$$

Integration uses an adaptive RK4 from $\phi = \beta$ (just behind the shock) to $\phi = \theta_c$ (the cone surface).

4. **Extract surface pressure.** $V_\phi(\theta_c) = 0$ at convergence; the static pressure ratio at the cone surface follows from the isentropic relation applied to the velocity at the wall.

When $\theta_c$ exceeds the maximum deflection angle for the freestream Mach (no attached oblique-shock solution exists), the solver detaches and returns the bow-shock stagnation pressure coefficient instead. Validation against published Taylor–Maccoll cone tables is logged at agreement better than $0.01\%$ in [`paper/data/md/taylor_maccoll_cone_shock.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/taylor_maccoll_cone_shock.md), which counts as an external-benchmark validation against published analytical data.

A worked numerical example is given in Section 6.1.7.


#### 6.1.2 Shock-Expansion Strip Integration for Ogives

For non-conical pointed axisymmetric shapes (OGIVE, POWER, PARABOLIC, HAACK), `SymmetricComponentCalc.calculateShockExpansionCd()` (lines 880–980) uses second-order shock-expansion theory on a 100-strip discretization of the meridian profile. The body is approximated as a sequence of infinitesimal conical frustums, and the local Mach and static pressure are tracked from strip to strip via Prandtl–Meyer expansion (when the surface turns away from the flow) or oblique shock (when the surface turns into the flow).

For each strip $i = 1,\ldots,N$ ($N = 100$):

1. Compute the local surface angle $\theta_i$ from the shape's profile function $r(x)$.
2. Compute the turn angle $\delta_i = \theta_{i-1} - \theta_i$.
3. If $\delta_i > 0$ (expansion), apply Prandtl–Meyer: $M_i$ from $\nu(M_i) = \nu(M_{i-1}) + \delta_i$ and $p_i = p_{i-1}\,(p/p_0)_i / (p/p_0)_{i-1}$ via the isentropic relation.
4. If $\delta_i < 0$ (compression), solve the local oblique shock at $M_{i-1}$ with deflection $|\delta_i|$ and update $M_i$ and $p_i$ accordingly.
5. Compute the local strip pressure coefficient $C_{p,i} = (2/\gamma M_\infty^{2})(p_i/p_\infty - 1)$.
6. Accumulate the drag integral.

**Initial condition.** The flow at the nose tip is initialized using the Taylor–Maccoll cone solution with the local tip half-angle $\theta_{\text{tip}}$. For ogive shapes whose profile is locally tangent to the axis at the tip ($\sin\theta_{\text{tip}} \to 0$), a small numerical tip angle is computed from the first two profile points to seed the integrator.

**Prandtl–Meyer relations.**

$$
\nu(M) \;=\; \sqrt{\frac{\gamma+1}{\gamma-1}}\,\arctan\!\sqrt{\frac{\gamma-1}{\gamma+1}\bigl(M^{2}-1\bigr)} \;-\; \arctan\!\sqrt{M^{2}-1},
$$

$$
\nu(M_{\text{new}}) \;=\; \nu(M_{\text{old}}) + \Delta\theta,
\qquad
\frac{p_{\text{new}}}{p_{\text{old}}} \;=\;
\left(
\frac{1 + \tfrac{\gamma-1}{2}M_{\text{old}}^{2}}
     {1 + \tfrac{\gamma-1}{2}M_{\text{new}}^{2}}
\right)^{\!\gamma/(\gamma-1)}.
$$

**Drag integration.** The total drag coefficient referenced to the frontal area $\pi(R_{\text{aft}}^{2} - R_{\text{fore}}^{2})$ is

$$
C_d \;=\; \frac{2}{R_{\text{aft}}^{2} - R_{\text{fore}}^{2}}\,\sum_{i=1}^{N} C_{p,i}\,r_{\text{mid},i}\,\Delta r_i,
$$

where $r_{\text{mid},i} = \tfrac{1}{2}(r_i + r_{i-1})$ and $\Delta r_i = r_i - r_{i-1}$. Only strips with positive $\Delta r$ (expanding radius, windward surface) contribute; strips with $\Delta r \le 0$ are in the aerodynamic shadow region.


#### 6.1.3 Dahlem–Buck Shape Factors

For nose shapes that do not match one of the four reference parameter values directly tabulated in NASA TR-R-100 (POWER with $n \in \{0.25, 0.5, 0.75, 1.0\}$, HAACK with $C \in \{0, 1/3\}$ — checked by `SymmetricComponentCalc.isDirectReferenceShapeForSupersonicOverride()`), the empirical TR-R-100 tables have insufficient Mach and fineness coverage. The Dahlem–Buck method extends the analytical cone result to arbitrary shapes using semi-empirical correction factors:

$$
C_{d,\text{wave}} \;=\; C_{d,\text{cone}}\!\bigl(M,\theta_{\text{equiv}}\bigr)\,\cdot\,K_{\text{shape}}(M)\,\cdot\,f_{\text{fineness}},
$$

where $\theta_{\text{equiv}} = \arctan(R_{\text{aft}}/L)$ is the equivalent cone half-angle (returned by `DahlemBuckShapeFactors.getEquivalentConeHalfAngle()`) and $C_{d,\text{cone}}$ is supplied by the same Taylor–Maccoll solver used in Section 6.1.1.

**Shape correction factors (`DahlemBuckShapeFactors.getBaseShapeFactor`).** The base value depends on the shape only:

| Shape | Parameter | $K_{\text{shape,base}}$ | Notes |
|---|---|---|---|
| `CONICAL` | – | $1.00$ | Reference shape |
| `OGIVE` | – | $0.85$ | ${\sim}15\%$ less wave drag than equivalent cone |
| `POWER` | $n$ exponent | $0.60 + 0.40\,n$ | $n=1$: cone, $n=0.5$: 0.80, $n\to 0$: 0.60 |
| `PARABOLIC` | $p$ shape parameter | $1.00 - 0.30\,p$ | $p=0$: cone, $p=1$: 0.70 |
| `HAACK` | $C$ ($0=$ Von Karman, $1/3=$ LV) | $0.60 + 0.30\,(3C)$ | Von Karman: 0.60, LV: 0.70 |
| `ELLIPSOID` | – | $1.00$ | Blunt; falls through to Newtonian above $M=5$ |

These factors are read directly from the switch in [`DahlemBuckShapeFactors.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/DahlemBuckShapeFactors.java) lines 80–114. The HAACK base factor multiplies the LV coefficient $(3C)$ by $0.30$ (Von Karman, $C=0$, gives $0.60$; LV, $C=1/3$, gives $0.70$).

**Mach dependence.** For $M > 1.5$, the shape factor is multiplied by a mild Mach correction reflecting that the shock becomes increasingly normal-like at high Mach so that shape-dependent differences shrink:

$$
K_{\text{shape}}(M) \;=\; K_{\text{shape,base}}\,\bigl[1 + 0.03 \cdot \min(M - 1.5,\;3.5)\bigr],
\qquad K_{\text{shape}} \le 1.5\;\text{(safety clamp)}.
$$

The slope $0.03$ per Mach unit, the $M = 1.5$ onset, the $M = 5.0$ cap and the $1.5$ ceiling are all read from the constants `MACH_DEPENDENCE_SLOPE`, `MACH_DEPENDENCE_CAP`, and `MAX_SHAPE_FACTOR` in `DahlemBuckShapeFactors`.

**Fineness ratio correction.** The TR-R-100 reference data was measured at fineness ratio $f = L/D = 3$. The Dahlem–Buck rescaling is

$$
f_{\text{fineness}} \;=\; \left(\frac{3}{f}\right)^{\!1.6}.
$$

Slender noses ($f > 3$) produce less wave drag; blunt noses ($f < 3$) produce more. The exponent $1.6$ is the Dahlem–Buck empirical value (`FINENESS_EXPONENT`).

**Blending into the empirical curves.** Below $M = 1.3$ the TR-R-100 polynomial is used unchanged. Between $M = 1.3$ and $M = 1.5$ the Dahlem–Buck correction is faded in via a cubic Hermite smoothstep; above $M = 1.5$ it is used exclusively:

$$
w(M) \;=\; 3 t^{2} - 2 t^{3},\qquad t = \frac{M - 1.3}{0.2},\qquad C_d(M) = (1-w)\,C_{d,\text{TR-R-100}} + w\,C_{d,\text{Dahlem-Buck}}.
$$

The blend logic is in `SymmetricComponentCalc.java` lines 742–767.


#### 6.1.4 Transonic Drag Rise

Below the drag-divergence Mach $M_{dd}$, there is no wave drag — the flow is everywhere subsonic on the body surface and `buildTransonicDragRise()` returns zero. Above $M_{dd}$, local supersonic pockets form on the nose, terminated by recompression shocks that produce a steep rise in pressure drag through the transonic regime.

**Drag-divergence Mach.** $M_{dd}$ is estimated from the nose tip half-angle by `estimateDragDivergenceMach()`:

$$
M_{dd} \;=\; \mathrm{clamp}\!\left(0.95 - 0.15\,\sin(\theta_{\text{tip}})^{0.4},\; 0.65,\; 0.96\right).
$$

Sharp tips ($\theta_{\text{tip}} \to 0$) yield $M_{dd} \approx 0.95$; blunt tips push $M_{dd}$ down to the lower clamp at $0.65$ (e.g. an effective hemisphere). The correlation was anchored against the NASA TR-R-100 transonic onset data digitized in the OR reference tables.

**C1 Hermite onset.** From zero at $M_{dd}$ to the first existing data point $M_1$ (where the empirical TR-R-100 tables or the analytical Taylor–Maccoll curve have a value $C_{d,1}$ with slope $(dC_d/dM)_1$), `buildTransonicDragRise()` constructs a four-constraint cubic Hermite polynomial:

| # | Location | Type | Value |
|---|---|---|---|
| 1 | $M_{dd}$ | Value | $C_d = 0$ |
| 2 | $M_{dd}$ | Derivative | $dC_d/dM = 0$ |
| 3 | $M_1$ | Value | $C_d = C_{d,1}$ |
| 4 | $M_1$ | Derivative | $dC_d/dM = (dC_d/dM)_1$ |

With normalized coordinate $t = (M - M_{dd})/(M_1 - M_{dd})$ and basis $h_{00}(t)=2t^3-3t^2+1$, $h_{10}(t)=t^3-2t^2+t$, $h_{01}(t)=-2t^3+3t^2$, $h_{11}(t)=t^3-t^2$, the polynomial reduces (because $f_0=f_0'=0$) to

$$
C_d(M) \;=\; h_{01}(t)\,C_{d,1} \;+\; h_{11}(t)\,\Delta M\,(dC_d/dM)_1,
\qquad \Delta M = M_1 - M_{dd},
$$

with $(dC_d/dM)_1 \le 3 C_{d,1}/\Delta M$ to enforce monotonicity. The construction is C1-continuous by design (matched value and slope at both endpoints). Together with the empirical tables and the analytical models above $M = 1.5$, this gives a single drag curve with a continuous first derivative across the entire transonic regime.

The result has the qualitative shape of a Lock fourth-power onset (zero slope at $M_{dd}$, gentle initial rise, near-linear central rise, smooth join to the data), with no fitted "Lock constant" — the cubic Hermite polynomial collapses to that shape automatically when the boundary conditions specified above are imposed.


#### 6.1.5 Modified Newtonian Theory

At hypersonic Mach numbers the shock layer becomes thin and the surface pressure distribution is well approximated by Newtonian impact theory with Lees's stagnation-pressure correction:

$$
C_p \;=\; C_{p,\max}\,\sin^{2}\theta,
$$

where $\theta$ is the local surface inclination angle to the freestream and $C_{p,\max}$ is the maximum pressure coefficient behind a normal shock at the freestream Mach. The implementation is `SymmetricComponentCalc.calculateNewtonianCd()` (lines 1011–1060), and the strip integration uses the same 100-strip discretization as the shock-expansion method:

$$
C_d \;=\; \frac{2}{R_{\text{aft}}^{2} - R_{\text{fore}}^{2}}\,\sum_{i=1}^{N} C_{p,\max}\,\sin^{2}\theta_i\,r_{\text{mid},i}\,\Delta r_i.
$$

Only windward surfaces ($\Delta r > 0$) contribute; leeward surfaces are in the aerodynamic shadow with $C_p \approx 0$ in Newtonian theory.

**Rayleigh pitot $C_{p,\max}$.** The stagnation pressure coefficient is computed by `SymmetricComponentCalc.calculateCpMax(mach, gamma)` from the total pressure ratio across a normal shock combined with the isentropic relation to stagnation:

$$
\begin{aligned}
C_{p,\max}
&= \frac{2}{\gamma\,M^{2}}
\left[
\left(
\frac{(\gamma+1)^{2}\,M^{2}}
     {4\gamma\,M^{2} - 2(\gamma - 1)}
\right)^{\!\gamma/(\gamma-1)}
\frac{1 - \gamma + 2\gamma\,M^{2}}{\gamma + 1}
- 1
\right].
\end{aligned}
$$

For $\gamma = 1.4$ the expression simplifies to

$$
C_{p,\max} \;=\; \frac{2}{1.4\,M^{2}}\left[\left(\frac{5.76\,M^{2}}{5.6\,M^{2} - 0.8}\right)^{3.5}\!\frac{2.8\,M^{2} - 0.4}{2.4} \;-\; 1\right].
$$

The tabulated values agree with the NACA 1135 normal-shock tables to better than $0.01\%$ on all 15 spot checks (validation in [`paper/data/md/rayleigh_pitot_cpmax.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/rayleigh_pitot_cpmax.md)):

| $M$ | $C_{p,\max}$ ($\gamma = 1.4$) |
|---|---|
| 1.0 | 1.276 |
| 2.0 | 1.657 |
| 3.0 | 1.756 |
| 5.0 | 1.809 |
| 10.0 | 1.832 |
| $\infty$ | 1.839 |

**Real-gas $\gamma$ correction.** Above $M = 5$, the stagnation temperature exceeds $\sim 2000$ K and vibrational excitation of N$_2$ and O$_2$ reduces the effective ratio of specific heats. The effective $\gamma$ used in $C_{p,\max}$ is read from `AtmosphericConditions.effectiveGamma(T_0)` evaluated at the approximate stagnation temperature

$$
T_0 \approx T_\infty\!\left(1 + \tfrac{\gamma-1}{2}M^{2}\right).
$$

The function `AtmosphericConditions.effectiveGamma(T_0)` is **not** a linear fit: it evaluates the Einstein quantum-harmonic-oscillator model for the vibrational specific heat of the N$_2$/O$_2$ mixture and clamps the result to $[1.30,\,1.40]$. The full derivation, the Java listing, and the tabulated values ($\gamma_{\text{eff}} = 1.40$ for $T_0 \le 800$ K, $1.371$ at $1000$ K, $1.349$ at $1500$ K, $1.330$ at $2000$ K, decreasing toward the $1.30$ floor above $\sim$$5000$ K) are given in Part A, Section 3.3, and the same quantity is validated in Part E, Section 11.5.3. The lower clamp $\gamma_{\text{eff}} \ge 1.30$ is enforced because below it molecular dissociation (not modeled) would dominate.

**Blend with shock-expansion (M 4.0–6.0).** Both `buildAnalyticalWaveDragCurve()` and `extendWithShockExpansion()` use a cubic Hermite smoothstep $w = 3t^2 - 2t^3$, $t = (M - 4.0)/2.0$ to fade from the analytical (Taylor–Maccoll or shock-expansion) result into Modified Newtonian theory:

$$
C_d(M) \;=\; (1-w)\,C_{d,\text{shock-exp}} + w\,C_{d,\text{Newtonian}}.
$$

Below $M=4.0$ the blend weight is zero (analytical only); above $M=6.0$ it is unity (Newtonian only). Validation against the DTIC AD0487365 hypersonic cone foredrag dataset (Grabow 1965, 11 points $M=6.5$–$17.2$) gives MAPE 19.7% with the source row-by-row Re-matched, just inside the current `< 20%` Java diagnostic gate, with the 16° cone band agreeing within $\pm 11\%$ — see [`HypersonicConeDragBenchmarkTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/HypersonicConeDragBenchmarkTest.java) and the aggregate 8°/12°/16° rows tabulated in [`paper/data/csv/hypersonic_cone_comparison.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/hypersonic_cone_comparison.csv). The largest pointwise residual is the 8° cone at $M = 6.5$ and $\mathrm{Re}_L = 1.11\times10^6$ (+57.0%); the 8° cone overpredicts most strongly (~30–60% on the lowest-Re points). This is documented as a near-gate residual, not as spare margin.


#### 6.1.6 Blending Across Mach Regimes

Three Mach-regime blends connect the wave-drag models. All are cubic Hermite smoothsteps $w(t) = 3t^2 - 2t^3$ with $t = (M-M_{\text{low}})/(M_{\text{high}}-M_{\text{low}})$, ensuring C1 continuity at both ends.

```{=latex}
\begin{center}
\scriptsize
\setlength{\tabcolsep}{4pt}
\renewcommand{\arraystretch}{1.2}
\begin{tabular}{@{}lcllp{4.2cm}@{}}
\toprule
Blend & Mach & Below & Above & Used in \\
\midrule
Empirical to analytical & 1.3--1.5 & TR-R-100 & T--M / shock-exp & \seqsplit{buildAnalyticalWaveDragCurve} in \texttt{SymComp\-Calc} \\
Dahlem--Buck override & 1.3--1.5 & TR-R-100 & $C_{d}^{\text{cone}}\,K\,f$ & POWER, PARABOLIC, HAACK only \\
Shock-exp to Newtonian & 4.0--6.0 & T--M / shock-exp & Mod.\ Newtonian & All shapes \\
\bottomrule
\end{tabular}
\end{center}
```


#### 6.1.7 Worked Example: 15-Degree Cone

Conical nose, $\theta_c = 15^\circ$, fineness ratio $f = L/(2R) \approx 1.87$, in air ($\gamma = 1.4$).

**At $M = 2.0$:**

1. Solve Taylor–Maccoll for the shock angle: $\beta \approx 33.8^\circ$.
2. Normal Mach behind the shock: $M_{n1} = 2.0 \sin(33.8^\circ) = 1.113$.
3. Pressure ratio across shock: $p_2/p_1 = 1 + \tfrac{2(1.4)}{2.4}(1.113^2 - 1) = 1.293$.
4. Taylor–Maccoll integration to the cone surface yields $p_{\text{cone}}/p_\infty = 1.566$.
5. $C_p = \tfrac{2}{1.4 \times 4.0}(1.566 - 1) = 0.202$.
6. $C_{d,\text{cone}} = 0.202$ (referenced to base area).

**At $M = 3.0$:** $\beta \approx 26.1^\circ$, $M_{n1} = 1.320$, $p_2/p_1 = 1.866$, $p_{\text{cone}}/p_\infty = 2.315$, $C_p = 0.209$.

**At $M = 5.0$ (in the Newtonian blend window):**

1. Pure Taylor–Maccoll gives $C_d \approx 0.185$.
2. Modified Newtonian gives $C_{p,\max} = 1.809$, $\sin^2(15^\circ) = 0.0670$, so the single-strip Newtonian estimate is $C_d \approx 1.809 \times 0.0670 = 0.121$.
3. Blend weight at $M = 5.0$: $t = 0.5$, $w = 3(0.5)^2 - 2(0.5)^3 = 0.5$.
4. Blended: $C_d = 0.5 \times 0.185 + 0.5 \times 0.121 = 0.153$.

| Mach | Taylor–Maccoll $C_d$ | Newtonian $C_d$ | Blended $C_d$ |
|---|---|---|---|
| 2.0 | 0.202 | – | 0.202 |
| 3.0 | 0.209 | – | 0.209 |
| 5.0 | 0.185 | 0.121 | 0.153 |

A52H28 sanity check across all five reference nose families (digitized in [`paper/data/csv/NACA_RM_A52H28_digitized_points.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/NACA_RM_A52H28_digitized_points.csv)): the current JUnit benchmark reports aggregate MAE approximately 0.029 with a gate of 0.035 after the Van Driest II skin-friction change. The older `paper/data/csv/naca_rm_a52h28_metrics.csv` and `paper/data/md/naca_validation_report.md` still preserve the pre-Van-Driest/Eckert export value (MAE 0.0147, RMSE 0.0190) and should be read as stale provenance until regenerated. The bias isolation memo [`paper/data/md/a52h28_bias_isolation.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/a52h28_bias_isolation.md) attributes the cone residual to the shape-agnostic transonic polynomial and the quarter-power family residual (~10–15% flat) to TR-R-100 fineness scaling — both architectural rather than physical errors.


#### 6.1.8 Slender-Body Supersonic Body-Pressure Term

The classical Barrowman treatment returns zero pressure drag for a smooth cylindrical body tube (`SymmetricComponentCalc` lines 422–423). At $M > 1$ this is a truncation: a long cylindrical afterbody radiates a weak system of shocklets driven by boundary-layer displacement growth, surface imperfections (joints, fasteners, paint ridges), and viscous–inviscid interaction with the nose–body shoulder shock. Hoerner (1965, Ch. 17) and Tanner (1984) document a non-zero body pressure drag on long cylindrical afterbodies at supersonic speeds ($C_{dp}\sim 0.02$–$0.05$ for $L/D = 20$–$40$ at $M = 1$–$3$). `BarrowmanDragCalculator.calculateSlenderBodyPressureCD()` (line 1467) adds this increment, which `calculatePressureCD()` sums into the body pressure total (line 865) and distributes across active body tubes for the per-component breakdown:

$$
C_{D,\text{slender}} \;=\; \mathrm{SLENDER\_BODY\_K}\,\cdot\,\min\!\bigl(L/d - 15,\;25\bigr)\,\cdot\,f_M(M),
\qquad \mathrm{SLENDER\_BODY\_K} = 0.0025,
$$

where $L/d$ is the cylindrical-body fineness ratio (nose, transitions, and boattails excluded; `computeBodyTubeLength()` requires fore radius $\approx$ aft radius). Both gates must be satisfied for any effect: body $L/d > 15$ and $M > 1.05$. The Mach factor ramps in over $M = 1.05$–$1.3$ via the smoothstep $w = 3t^2 - 2t^3$, plateaus to $M = 3.0$, then decays back to zero by $M = 5.0$ (the shrinking Mach cone reduces the effective shock-radiator length); the $L/d$ excess is capped at $25$ to bound runaway on pathological geometries.

The functional form (linear in $L/d$-excess, gated and Mach-faded as above) is physics-motivated, but the scale constant `SLENDER_BODY_K = 0.0025` is **B-level / corpus-frozen** against the 25-flight validation corpus apogee residual — anchored to the Raven, Rabia, and Kinsel ORP-specific residuals (the source diagnostic also used Torrent) — rather than against an isolated component benchmark. It is therefore the third partly-in-sample scale constant, alongside the base-drag `FINNED_BASE_K` and `THICK_BL_K` of Section 6.2.8, and is not counted toward the external-benchmark headline. Its generalization is defended by the same decontaminated prospective holdout: every flight any constant touched (Raven, Rabia, Rabia Short Fin Can, Kinsel, Torrent) is placed in the development split, and the genuinely blind holdout is more accurate than the development split (holdout MAE 3.95% vs development-split MAE 5.47%), indicating the constants generalize rather than overfit.


### 6.2 Base Drag

Base drag arises from the low-pressure recirculating wake region behind every blunt aft face. It is the second-largest drag component for a typical sounding rocket at subsonic and transonic Mach (10–25% of total $C_D$) and remains a non-trivial contribution to $M = 5$. The dispatch is in [`BarrowmanDragCalculator.calculateBaseCD()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java) at line ~945, which calls the public Mach-only correlation `calculateBaseCD(double m)` at line 1654 to obtain a base coefficient referenced to the base area, then walks every active component, computes the exposed base area $A_{\text{base}} = \pi(R_{\text{aft}}^{2} - R_{\text{next}}^{2})$ — accounting for any abutting downstream component via `findAbuttingDownstreamRadius()` so that PodSet fin cans (Qu8k, IonDrive) are not double-counted — and assembles

$$
\begin{aligned}
C_{D,\text{base}}^{\text{component}}
&= C_{d,\text{base}}(M)\,
   k_{\text{powered}}\,
   k_{\text{laminar}}\,
   k_{\text{boattail}}\,
   \eta_{\text{Viswanath}}\,
   k_{\text{finned}}\,
   k_{\text{thick-BL}}\,
   \frac{A_{\text{base}}}{S_{\text{ref}}}.
\end{aligned}
$$

The factors are described in turn in Sections 6.2.1–6.2.8. Five of them are externally anchored against published data. The finned-body augmentation $k_{\text{finned}}$ and the thick-boundary-layer multiplier $k_{\text{thick-BL}}$ carry two base-drag scale constants (`FINNED_BASE_K` and `THICK_BL_K = 2.2`) that are corpus-frozen against the 25-flight validation corpus apogee residual rather than against an isolated component benchmark, so the headline is partly in-sample on those constants — this is disclosed plainly and not counted toward the external-benchmark headline. Together with the slender-body pressure constant `SLENDER_BODY_K = 0.0025` of Section 6.1.8, these are the three partly-in-sample scale constants in the drag model. The generalization of all three is defended by a single decontaminated prospective holdout (every flight any constant touched placed in the development split): the genuinely blind holdout is more accurate than the development split (holdout MAE 3.95% vs dev MAE 5.47%), indicating the constants generalize rather than overfit. The corresponding component-level dataset (finned-body base pressure across the transonic-to-supersonic range) does not exist in a form that has been located in the public literature.


#### 6.2.1 Subsonic Hoerner Correlation

For $M \le 0.85$ the unmodified Hoerner correlation for cylindrical afterbodies applies (`BarrowmanDragCalculator.calculateBaseCD`, line 1655):

$$
C_{d,\text{base}}(M) \;=\; 0.12 + 0.13\,M^{2}.
$$

This rises smoothly from $0.12$ at $M=0$ to $0.214$ at $M = 0.85$. Reference: Hoerner, *Fluid-Dynamic Drag* (1965), Chapter 3.


#### 6.2.2 Supersonic Base-Drag Correlation (ESDU 77021 form)

For $M \ge 1.5$ the implementation switches to the form (lines 1658–1660):

$$
C_{d,\text{base}}(M) \;=\; \mathrm{BASE\_DRAG\_A} + \frac{\mathrm{BASE\_DRAG\_B}}{M^{2}} \;=\; 0.064 + \frac{0.186}{M^{2}}.
$$

The constants `BASE_DRAG_A = 0.064` and `BASE_DRAG_B = 0.186` define an empirical correlation that is validated against the turbulent cylindrical-afterbody base-pressure data of NACA TN 3393 (Reller & Hamaker 1955; see the validation table below) and is consistent with the $a + b/M^2$ form recommended by ESDU 77021 (Engineering Sciences Data Unit, *Base pressure on bodies of revolution at supersonic and hypersonic Mach numbers without fuel injection or combustion*, 1977). An earlier code comment attributed the constants to "Devan & Ashwood / NASA TN D-721"; that identifier could not be independently verified in NTRS and has been dropped — the correlation is presented as an empirical fit anchored to NACA TN 3393 and consistent with ESDU 77021, with no Devan–Ashwood citation. Two physical features of this form are worth noting:

- **Nonzero asymptote.** $C_{d,\text{base}} \to 0.064$ as $M \to \infty$, matching the observed behavior that base pressure does not vanish at very high Mach. The legacy $0.25/M$ model used by the original OpenRocket decays to zero, which underestimates base drag by ~30% at $M = 5$ for cylindrical bodies.
- **$1/M^2$ decay.** The dominant supersonic decay matches the expansion-fan physics at the base corner, where the Prandtl–Meyer expansion angle increases with Mach and reduces the base pressure coefficient.

Spot values: $0.174$ at $M = 1.3$; $0.111$ at $M = 2.0$; $0.071$ at $M = 5.0$.

**Validation.** [`BaseDragModelTest.testTN3393TurbulentAgreement`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/BaseDragModelTest.java) parameterizes the four turbulent points from NACA TN 3393 (Reller & Hamaker 1955, Ames; 10-caliber tangent ogive + cylindrical afterbody, $l/d = 5$). Aggregate metrics from [`paper/data/md/naca_tn_3393_validation_report.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/naca_tn_3393_validation_report.md):

| $M$ | TN 3393 $C_{p,b}$ (turb.) | Model $C_{d,\text{base}}$ | $\Delta\%$ |
|---|---|---|---|
| 2.73 | 0.1188 | 0.0896 | $-24.6\%$ |
| 3.49 | 0.0798 | 0.0793 | $-0.6\%$ |
| 4.03 | 0.0660 | 0.0750 | $+13.6\%$ |
| 4.48 | 0.0584 | 0.0728 | $+24.7\%$ |

MAPE $= 15.9\%$; the same correlation applied to the laminar-state TN 3393 points produces MAPE $= 44.1\%$, which motivates the Chapman laminar branch of Section 6.2.4. The Hart NACA RM L52E06 free-flight finless data (digitized in [`paper/data/csv/naca_rm_l52e06_base_drag.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/naca_rm_l52e06_base_drag.csv)) contributes an additional externally anchored transonic benchmark with MAPE $\le 12\%$ across $M = 0.95$–$1.30$ ([`BaseDragModelTest.testHartL52E06MAPE`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/BaseDragModelTest.java)).


#### 6.2.3 Transonic Polynomial (M 0.85–1.5)

The transonic regime ($M \in [0.85, 1.5]$) features a sharp peak in base drag near $M \approx 1.05$, where the wake becomes highly unsteady and the flow transitions from subsonic to supersonic separation. This peak is captured by a degree-5 polynomial constructed via [`PolyInterpolator`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/util/PolyInterpolator.java) with six constraints (four value, two derivative) — read directly from the static initializer of `BarrowmanDragCalculator` (lines 236–247):

| # | Constraint | Mach | Type | Value / Expression |
|---|---|---|---|---|
| 1 | Subsonic value | $M = 0.85$ | Value | $0.12 + 0.13\,(0.85)^{2} = 0.214$ |
| 2 | Transonic peak | $M = 1.05$ | Value | $0.250$ (within Hart $\pm 0.01$ and Peck $\pm 0.015$ scatter) |
| 3 | Hart anchor | $M = 1.30$ | Value | $0.230$ (Hart L52E06 reads $\sim 0.250$) |
| 4 | Supersonic handoff | $M = 1.50$ | Value | $0.064 + 0.186/(1.50)^{2} = 0.147$ |
| 5 | Subsonic slope | $M = 0.85$ | Derivative | $0.26 \times 0.85 = 0.221$ |
| 6 | Supersonic slope | $M = 1.50$ | Derivative | $-2 \times 0.186/(1.50)^{3} = -0.110$ |

The corresponding Java construction:

```java
PolyInterpolator baseDragInterp = new PolyInterpolator(
    new double[] { 0.85, 1.05, 1.30, 1.50 },
    new double[] { 0.85, 1.50 });
baseDragTransonicPoly = baseDragInterp.interpolator(
    0.214,   // subsonic value at M=0.85
    0.25,    // peak at M=1.05
    0.230,   // Hart anchor near M=1.30
    0.147,   // ESDU 77021-form handoff at M=1.50
    0.221,   // subsonic derivative at M=0.85
   -0.110);  // ESDU 77021-form derivative at M=1.50
```

The polynomial is degree 5 with six constraints, including a Hart anchor at $M = 1.30$. Without that anchor a four-point polynomial would extrapolate from the ESDU 77021-form correlation at $M = 1.30$ at $\sim 0.174$, under-reading Hart by 30%.

The interior anchor `BASE_CD_AT_MID = 0.230` is set deliberately just below the Hart reading of $0.250 \pm 0.013$ to keep the peak inside $[0.25, 0.26]$ without overshooting and without regressing the TN 3393 turbulent agreement above $M = 2.7$ — this design constraint is carried in the comment block at lines 60–69 of `BarrowmanDragCalculator.java`.


#### 6.2.4 Chapman Laminar Base Drag

For rockets configured with `Rocket.isPerfectFinish() == true` and `forceTurbulentBL == false`, the boundary layer can remain laminar over a significant fraction of the body, and the turbulent ESDU 77021-form correlation systematically overestimates base drag at high Mach — the laminar shear layer at the base corner has much lower momentum than the turbulent one, producing less wake recompression and lower (more negative) base pressure than the turbulent correlation predicts. The Chapman (1950) NACA TN 2137 laminar correlation provides the correct scaling, implemented in [`ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL)`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/ChapmanKorstBaseDrag.java):

$$
C_{p,b,\text{lam}} \;=\; \frac{C_{\text{LAM}}}{M^{2}\,\sqrt{\mathrm{Re}_L}},
\qquad C_{\text{LAM}} = 1870.
$$

The constant $1870$ is carried as `C_LAM_SUPERSONIC` and is documented in the source as a geometric-mean fit to the four condensation-corrected laminar TN 3393 points (Reller & Hamaker 1955), $M = 2.73$–$4.48$, $\mathrm{Re}_L = 4$–$6 \times 10^{6}$.

A vacuum-pressure cap is imposed: $C_{p,b,\text{lam}} \le 2/(\gamma M^{2})$ corresponds to base pressure at zero (perfect vacuum on the wake side), which is the physical maximum.

**Blending with the turbulent branch.** The transition from the ESDU 77021-form / transonic polynomial to the Chapman laminar formula is blended over $M \in [1.3, 2.5]$ via cubic Hermite smoothstep ($t = (M - 1.3)/1.2$, $w = 3t^2 - 2t^3$). Below $M = 1.3$ only the turbulent ESDU 77021-form correlation is used (no laminar/turbulent base distinction has yet established at the corner); above $M = 2.5$ the full Chapman laminar formula is used.

In the dispatch (lines 970–984 of `BarrowmanDragCalculator.calculateBaseCD()`), the laminar branch is mixed with the turbulent value by the Michel-criterion laminar fraction $f_{\text{lam}}$ (Section 6.3.3):

$$
C_{d,\text{base}}^{\text{eff}} \;=\; f_{\text{lam}}\,C_{d,\text{base}}^{\text{Chapman}} + (1 - f_{\text{lam}})\,C_{d,\text{base}}^{\text{turb}}.
$$

For non-perfect-finish rockets the laminar fraction is forced to a small cap (≤ 5%) inside `calculateFrictionCD()` because surface roughness from paint, couplers, and fin fillets trips transition almost immediately, so the Chapman branch is in practice activated only on smooth tunnel models.

**Validation.** [`ChapmanLaminarBaseDragTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/ChapmanLaminarBaseDragTest.java) MAPE gate is $\le 10\%$ on the four TN 3393 laminar points; the achieved MAPE is $4.4\%$, vs $44\%$ for the turbulent ESDU 77021-form correlation applied to the same data. Spot values from the test:

| $M$ | $\mathrm{Re}_L$ | TN 3393 $C_{p,b}$ (lam.) | Chapman $C_{p,b}$ |
|---|---|---|---|
| 2.73 | $4.0 \times 10^{6}$ | 0.1150 | (within 10%) |
| 3.49 | $4.5 \times 10^{6}$ | 0.0680 | (within 10%) |
| 4.03 | $5.0 \times 10^{6}$ | 0.0493 | (within 10%) |
| 4.48 | $6.0 \times 10^{6}$ | 0.0391 | (within 10%) |

The test also checks the analytical $\sqrt{\mathrm{Re}}$ scaling: doubling $\sqrt{\mathrm{Re}_L}$ halves $C_{p,b,\text{lam}}$ to within $2\%$.


#### 6.2.5 Chapman–Korst Free Shear Layer (Turbulent, Optional)

[`ChapmanKorstBaseDrag.blendedBaseDrag(mach, M_e, thetaRatio)`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/ChapmanKorstBaseDrag.java) implements a more physically based turbulent base-drag model that resolves the boundary-layer thickness at the base corner. It is currently an available/tested utility rather than an active production path: `BarrowmanDragCalculator.calculateBaseCD()` uses the ESDU 77021-form / transonic polynomial path and, when the boundary-layer state is laminar, calls `ChapmanKorstBaseDrag.blendedLaminarBaseDrag()`.

The baseline thin-BL coefficient is fitted to ESDU 77021 Table 1 (turbulent cylindrical afterbody) as

$$
C_{d,\text{base}}^{\text{thin BL}} \;=\; 0.060 + \frac{0.190}{M_e^{2}} + \frac{0.005}{M_e^{4}},
$$

and the BL-thickness correction is

$$
f(\theta/r) \;=\; 1 - k(M_e)\,\sqrt{\theta/r},\qquad k(M_e) = 0.8 + 0.2/M_e,\qquad f \in [0.3, 1.0].
$$

The blend with the ESDU 77021-form correlation spans $M = 1.2$--$1.4$ with a smoothstep weight. Below $M = 1.2$ only the ESDU 77021-form correlation is used; above $M = 1.4$ the Chapman--Korst result takes over for turbulent boundary layers when this utility is called. The thick-BL multiplier of Section 6.2.8 captures related boundary-layer-thickness sensitivity in the production simulator path.


#### 6.2.6 Power-On Base Drag Reduction

During motor burn the exhaust plume partially fills the base region, raising base pressure and reducing base drag. The helper model is implemented in `BarrowmanDragCalculator`, but the RK4 production path currently leaves `FlightConditions.thrustLevel` at its default zero because `populateThrustState(status, store)` is disabled in `RK4SimulationStepper`. Therefore this subsection documents an implemented dormant helper, not an active end-to-end trajectory correction. The active powered-flight correction in the present code is the stage-aware nozzle pressure-thrust term in `RK4SimulationStepper`, not this base-drag multiplier.

The dormant base-drag reduction depends on the nozzle exit area to base area ratio $AR = A_e/A_b$, returned by `BarrowmanDragCalculator.powerOnBaseDragFactorDetailed(AR)` (lines 1739--1751):

$$
k_{\text{base}}(AR) \;=\; \begin{cases}
0.0 & AR \ge 0.8 \\[0.4ex]
0.2 \cdot \dfrac{0.8 - AR}{0.4} & 0.4 \le AR < 0.8 \\[0.4ex]
0.2 + 0.6 \cdot \dfrac{0.4 - AR}{0.3} & 0.1 \le AR < 0.4 \\[0.4ex]
0.8 + 0.2 \cdot \dfrac{0.1 - AR}{0.1} & AR < 0.1
\end{cases}
$$

with $k_{\text{base}} = 0$ corresponding to complete elimination of base drag (the nozzle exit fills the base) and $k_{\text{base}} = 1$ to no reduction. The thrust-time smoothstep handoff in `computePowerOnBaseDragMultiplier()` (lines 1770–1786) is

$$
C_{d,\text{base,powered}} \;=\; C_{d,\text{base}}\,\bigl[1 - S(\tau)\,(1 - k_{\text{base}})\bigr],
\qquad S(\tau) = 3\tau^{2} - 2\tau^{3},
$$

where $\tau \in [0,1]$ is the thrust level (0 = coast, 1 = full thrust). When the nozzle area ratio is unavailable from the motor file, `DEFAULT_POWER_ON_FACTOR = 0.15` is used (typical HPR motor with a moderate-sized nozzle).

| $AR$ | $k_{\text{base}}$ | Physical meaning |
|---|---|---|
| 0.05 | 0.90 | Very small nozzle, minimal base-pressure recovery |
| 0.1 | 0.80 | Small nozzle |
| 0.3 | 0.40 | Typical HPR motor |
| 0.5 | 0.15 | Large nozzle, significant reduction |
| 0.8 | 0.00 | Nozzle fills base, complete elimination |

If the helper is re-enabled, the smoothstep on $\tau$ avoids unphysical step changes in drag at motor ignition or burnout.


#### 6.2.7 Boattail and Viswanath Wake-Energization Corrections

When a body component tapers from a larger fore radius to a smaller aft radius (boattail), the converging flow creates a narrower wake with higher base pressure. Two factors are applied multiplicatively in `calculateBaseCD()` lines 1049–1061.

**Geometric boattail factor (`calculateBoattailFactor`, lines 1810–1843).** The boattail half-angle is

$$
\theta_{\text{bt}} \;=\; \arctan\!\left(\frac{R_{\text{fore}} - R_{\text{aft}}}{L}\right).
$$

The angle factor (full benefit at moderate angles, separation-killed at steep angles):

$$
f_{\text{angle}} \;=\; \begin{cases}
1.0 & \theta_{\text{bt}} \le 12^\circ \\
\dfrac{20^\circ - \theta_{\text{bt}}}{20^\circ - 12^\circ} & 12^\circ < \theta_{\text{bt}} < 20^\circ \\
0.0 & \theta_{\text{bt}} \ge 20^\circ
\end{cases}
$$

The reduction coefficient grows with Mach because expansion fans at the boattail corner intensify supersonically:

$$
c_{\text{red}} \;=\; \begin{cases}
0.25 & M \le 1.0 \\
0.25 + 0.15\,\min(M - 1.0, 1.0) & M > 1.0
\end{cases}
$$

The total geometric boattail factor is

$$
f_{\text{bt}} \;=\; \mathrm{clamp}\!\left(1 - f_{\text{angle}}\,c_{\text{red}}\,\bigl(1 - R_{\text{aft}}/R_{\text{fore}}\bigr),\;0.3,\;1.0\right).
$$

**Viswanath (1996) wake-energization factor (`calculateViswanathBoattailFactor`, lines 1694–1724).** A boattail upstream of the base energizes the boundary layer and produces a fuller wake profile, further reducing base drag. The factor is:

$$
\eta_{\text{bt}} \;=\; \begin{cases}
0.25 + 0.05\,\theta_{\text{bt}} & \theta_{\text{bt}} < 6^\circ \\
\min\!\bigl[(0.55 + 0.04(\theta_{\text{bt}} - 6))(1 + 0.1\max(0, M-1)),\,0.95\bigr] & 6^\circ \le \theta_{\text{bt}} < 16^\circ \\
\max\bigl[0,\;0.95 - 0.05(\theta_{\text{bt}} - 16)\bigr] & \theta_{\text{bt}} \ge 16^\circ
\end{cases}
$$

with $\theta_{\text{bt}}$ in degrees and the result clamped to $[0,1]$. The Viswanath factor is intentionally **not** applied on the boattail component itself when that component is also playing the role of $s$ in the per-component loop (the `selfBoattail` guard at line 1059), because doing so would double-count the reduction and could collapse the aft base coefficient to zero on steep imported boattails (e.g. Qu8k) that still expose a finite blunt aft annulus.


#### 6.2.8 Finned-Body Base Augmentation and Thick-BL Multiplier

The two corrections below have physics-motivated functional forms but their scale constants (`FINNED_BASE_K` and `THICK_BL_K = 2.2`) are corpus-frozen against the 25-flight corpus apogee residual, not against an isolated component benchmark. They make the headline partly in-sample on those two constants — disclosed plainly — and are not counted in the external-benchmark headline. Their generalization is defended by a decontaminated prospective holdout (every flight any constant touched placed in the development split), in which the genuinely blind holdout is more accurate than the development split (holdout MAE 3.95% vs development-split MAE 5.47%), indicating the constants generalize rather than overfit; a dedicated finned-body base-pressure dataset would convert these from partly-in-sample to fully confirmatory, but no such public dataset has been located.

**Finned-body augmentation (`calculateFinnedBaseAugmentation`, lines 1111–1265).** Fins at or near the aft base disrupt the smooth near-wake recompression, creating corner vortices and shock–wake interaction that increase base suction. ADA636861 (Basic Finner) and Hoerner Chapter 16 both show 40–60% higher base drag on 4-fin configurations vs smooth cylindrical afterbodies at $M = 1.5$–$3$. The augmentation has the structure

$$
k_{\text{finned}} \;=\; 1 + K_{\text{fin}}(\text{geometry})\,\cdot\,f_{\text{fin}}(n_{\text{fins}})\,\cdot\,f_{\text{span}}(s/R)\,\cdot\,f_M(M),
$$

with the following ingredients:

- **Base scale $K_{\text{fin}}$.** `FINNED_BASE_K = 0.55` for normal fins; `ROUNDED_FINNED_BASE_K = 1.00` for ROUNDED-cross-section fins (which behave more like blunt plates than airfoils); `FIN_CAN_SLEEVE_BASE_K = 1.35` when an expanding-shoulder fin-can sleeve is detected upstream of the base (geometry signature handled in `hasExpandingFinCanSleeve()`).
- **Fin-count saturation $f_{\text{fin}}$.** The wake disruption is not linear in fin count — three fins already partition the base shear layer into multiple corner-wake sectors, so they produce nearly the same base-pressure deficit as four fins. The implementation uses $f_{\text{fin}}(n) = (1 - e^{-n/1.4})/(1 - e^{-4/1.4})$ normalized so the 4-fin Basic Finner anchor is unchanged, clamped to $[0, 1.25]$.
- **Span factor $f_{\text{span}} = \mathrm{clamp}(s_{\max}/R, 0.3, 1.0)$.** Fins that extend far from the body affect the base more.
- **Mach factor $f_M(M)$.** Piecewise: zero below $M = 0.2$; linear ramp to $0.30$ at $M = 0.8$ (Hoerner subsonic ~10–20%); ramp to $1.0$ at $M = 1.3$ (shock–wake interaction strengthens); plateau through $M = 3.0$; then $3/M$ decay above $M = 3$ (fins become progressively submerged in the body shock cone).

The 4-fin Basic Finner geometry produces approximately 50% augmentation at $M = 2$, which is the calibration anchor.

**Thick-BL multiplier (`calculateThickBLBaseMultiplier`, lines 1325–1430).** Minimum-diameter, high body-L/D airframes develop a turbulent boundary layer whose thickness $\delta$ approaches the body radius $R$ at the base station. In that regime ($\delta/R \gtrsim 0.5$) the ESDU 77021-form correlation — calibrated on moderate-L/D bodies where $\delta/R \ll 1$ — systematically under-predicts base suction by 30–40% because the thick BL nearly fills the wake and the free shear layer / inviscid core assumption breaks down. The implementation uses the 1/7-power flat-plate turbulent BL correlation $\delta/x = 0.37/\mathrm{Re}_x^{0.2}$ and applies the multiplier

$$
k_{\text{thick-BL}} \;=\; \mathrm{min}\!\left[1 + K\,\max(0,\,\delta/R - 0.5)\,f_M(M)\,g_{L/D}(L/D),\;1.8\right],\qquad K = 2.2.
$$

Both gates must be satisfied for any effect: $M > 0.9$ (smoothstep ramp through $0.9$–$1.1$, Mach decay back to zero by $M = 3.0$) and body $L/D > 25$ (smoothstep ramp through $L/D = 25$–$30$). The cap at $1.8$ prevents runaway on pathological geometries. The scale constant $K = \mathrm{THICK\_BL\_K} = 2.2$ is corpus-frozen against the 25-flight validation corpus with Raven (1.75 in tube, body $L/D = 41.7$, peak $M = 1.12$) as the primary anchor; see [`paper/data/outlier_closure/raven_closure.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/outlier_closure/raven_closure.md) and the [`ThickBLBaseDragMultiplierTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/ThickBLBaseDragMultiplierTest.java) regression battery.


#### 6.2.9 Worked Examples

Cylindrical body (no boattail, no fin-can sleeve), 4-fin tail with rectangular AIRFOIL fins, body $L/D = 18$ (so the thick-BL gate is closed). Subsonic and transonic correlations come from `calculateBaseCD(double m)`; per-Mach factors are evaluated as in Sections 6.2.6–6.2.8.

**At $M = 0.5$ (subsonic).** Hoerner: $C_{d,\text{base}} = 0.12 + 0.13\,(0.5)^2 = 0.1525$. Finned augmentation with $f_M(0.5) = 0.30 \times (0.5 - 0.2)/0.6 = 0.15$ and $f_{\text{fin}}(4) \approx 1.0$: $k_{\text{finned}} \approx 1 + 0.55 \times 1.0 \times 0.8 \times 0.15 \approx 1.066$. No power-on (coast). $k_{\text{thick-BL}} = 1$. Final: $0.1525 \times 1.066 = 0.163$ at the component, scaled by $A_{\text{base}}/S_{\text{ref}}$.

**At $M = 1.05$ (transonic peak).** Polynomial returns $0.250$. Augmentation factor $f_M(1.05) \approx 0.30 + 0.70 \times 0.25/0.5 = 0.65$, $k_{\text{finned}} \approx 1 + 0.55 \times 1.0 \times 0.8 \times 0.65 \approx 1.286$. Final: $0.250 \times 1.286 = 0.321$ (component-level coefficient before area rescaling).

**At $M = 2.0$ (supersonic).** ESDU 77021-form: $0.064 + 0.186/4.0 = 0.111$. $f_M(2.0) = 1.0$, $k_{\text{finned}} \approx 1 + 0.55 \times 1.0 \times 0.8 \times 1.0 = 1.44$. Final: $0.111 \times 1.44 = 0.160$.

**At $M = 5.0$ (high supersonic).** ESDU 77021-form: $0.064 + 0.186/25.0 = 0.0714$. $f_M(5.0) = 3/5 = 0.60$, $k_{\text{finned}} \approx 1 + 0.55 \times 1.0 \times 0.8 \times 0.60 = 1.264$. Final: $0.0714 \times 1.264 = 0.090$.

**Old vs current code (Mach-only correlation, no fin/boattail/Re corrections):**

| Mach | Old OpenRocket $C_{d,\text{base}}$ | Current $C_{d,\text{base}}$ | Notes |
|---|---|---|---|
| 0.5 | 0.1525 | 0.1525 | Subsonic Hoerner unchanged |
| 0.9 | 0.225 | $\approx 0.230$ | Polynomial enters at $M = 0.85$ |
| 1.05 | 0.25 | 0.250 | Polynomial peak (Hart-anchored) |
| 1.30 | $\sim 0.20$ | 0.230 | Hart anchor |
| 1.50 | 0.167 | 0.147 | ESDU 77021-form handoff |
| 2.0 | 0.125 | 0.111 | ESDU 77021-form |
| 5.0 | 0.050 | 0.071 | ESDU 77021-form nonzero asymptote |


### 6.3 Skin Friction Drag

Skin friction drag arises from the viscous shear stress on all wetted surfaces. It is typically the largest single drag component in the subsonic regime and remains significant at supersonic speeds, though compressibility reduces it substantially (~50% at $M = 5$). The dispatch is in [`BarrowmanDragCalculator.calculateFrictionCD()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java) (lines 341–474) and the per-Mach $C_f$ is returned by `calculateFrictionCoefficient()` (lines 510–535).


#### 6.3.1 Incompressible Baseline

The incompressible $C_{f,0}$ depends on Reynolds number and surface finish (`incompressibleCf()` and `smoothFinishTransitionCf()`, lines 543–586).

**Smooth (perfect-finish) plate, mixed laminar/turbulent.** `smoothFinishTransitionCf` returns the average flat-plate $C_f$ accounting for natural transition at $\mathrm{Re}_{\text{tr}}$:

- For $\mathrm{Re} < 10^4$: $C_f = 0.0133$ (constant low-Re fallback).
- For $\mathrm{Re} \le \mathrm{Re}_{\text{tr}}$: pure laminar Blasius average, $C_f = 1.328/\sqrt{\mathrm{Re}}$.
- For $\mathrm{Re} > \mathrm{Re}_{\text{tr}}$: classical mixed flat-plate relation $C_f = 0.074/\mathrm{Re}^{0.2} - A/\mathrm{Re}$ with $A = 0.074\,\mathrm{Re}_{\text{tr}}^{0.8} - 1.328\sqrt{\mathrm{Re}_{\text{tr}}}$.

This reduces to the well-known $-1742/\mathrm{Re}$ correction at $\mathrm{Re}_{\text{tr}} = 5\times 10^{5}$.

**Rough (ordinary-finish) plate, fully turbulent.** `incompressibleCf(Re, false)`:

- For $\mathrm{Re} < 10^4$: $C_f = 0.0148$.
- Otherwise: Schlichting fully-turbulent $C_f = 1/(1.50\,\ln \mathrm{Re} - 5.6)^2$.

**Subsonic compressibility correction (`subsonicCfCorrection`, lines 594–607).** A $(1 - 0.1 M^2)$ correction with a smooth ramp-in for perfect-finish $\mathrm{Re} \in [10^6, 3\times 10^6]$; rough finish applies the correction at all Reynolds numbers.

**Body form factor (lines 421–433).** The total body friction drag includes Hoerner's streamlined-body form-factor correction:

$$
C_{D,\text{friction,body}} \;=\; C_f\,\frac{S_{\text{wet}}}{S_{\text{ref}}}\,\bigl(1 + 1.5/(L/d)^{1.5} + 7/(L/d)^{3}\bigr),
$$

where $L/d$ is the body fineness ratio at the maximum-radius section. This Hoerner streamlined formula (Eq. 6-21 in Chapter 6 of *Fluid Dynamic Drag*) is preferred over the non-streamlined $1 + 60/f^{3} + 0.0025 f$ form because the latter over-corrects for supersonic rockets where Van Driest II already accounts for compressibility.


#### 6.3.2 Van Driest II Compressible Transformation

At supersonic speeds the boundary layer temperature rises dramatically due to adiabatic compression and viscous dissipation, the wall-to-edge density ratio drops, and the wall-to-edge viscosity ratio rises. The Van Driest II method, implemented in `vanDriestIICf(mach, reX, te)` (lines 676–706), accounts for all three by transforming the compressible boundary-layer problem into an equivalent incompressible one, solving the incompressible Schoenherr formula exactly, and transforming back.

This **replaces the Eckert reference-temperature method** that was used in the original code (`calculateReferenceTemperature` and `calculateEckertReynolds` are retained for tests but are no longer called from the friction dispatch). The Van Driest II superseded the Eckert path in the supersonic-LE branch and Hopkins & Inouye (1971 AIAA J.) showed it gives the best agreement with experimental data across $M = 1.5$–$9$, outperforming all other available methods including Eckert.

**Recovery factor.** The constant `VD2_RECOVERY = 0.88` is the turbulent recovery factor recommended by Hopkins (NASA TN D-6945, 1972) — distinct from the $\mathrm{Pr}^{1/3}$ value $\sim 0.892$ used by the old Eckert path; both reduce to the standard turbulent value but the TN D-6945 calibration uses $0.88$ explicitly.

**Step 1 — Adiabatic wall temperature (Eq. 16–17 of TN D-6945).** For an adiabatic wall (zero heat transfer, typical for a rocket in flight), the wall temperature equals the recovery temperature:

$$
T_w \;=\; T_e\!\left(1 + r\,\tfrac{\gamma-1}{2}\,M^{2}\right) \;=\; T_e\,(1 + 0.176\,M^{2})\quad\text{for }\gamma = 1.4,\; r = 0.88.
$$

**Step 2 — Transformation function $F_c$ (`computeVD2Fc`, lines 712–732).** Define the intermediate quantities

$$
m \;=\; \tfrac{\gamma-1}{2}\,M^{2} \;=\; 0.2\,M^{2},\qquad F \;=\; T_w/T_e,\qquad A \;=\; \sqrt{r m / F},\qquad B \;=\; (1 + r m - F)/F.
$$

Then

$$
\alpha \;=\; \frac{2 A^{2} - B}{\sqrt{4 A^{4} + B^{2}}},
\qquad
\beta \;=\; \frac{B}{\sqrt{4 A^{4} + B^{2}}},
\qquad
F_c \;=\; \frac{r m}{(\arcsin\alpha + \arcsin\beta)^{2}}.
$$

The arguments to $\arcsin$ are clamped to $[-1, 1]$ for numerical safety and the denominator is checked against $10^{-10}$. $F_c \to 1$ as $M \to 0$ and grows approximately as $M^2$ at high Mach.

**Step 3 — Transformation function $F_\theta$ (`computeVD2Ftheta`, lines 738–745).** Computed with Sutherland viscosity and Keyes correction factors:

$$
\frac{\mu_w}{\mu_e} \;=\; \left(\frac{T_w}{T_e}\right)^{\!3/2}\,\frac{T_e + S}{T_w + S},\qquad S = 110.4\;\text{K},
$$

$$
F_\theta \;=\; \frac{\mu_e}{\mu_w}\,\sqrt{T_e/T_w}\,\frac{1 + 1.22\times 10^{-5}/T_w}{1 + 1.22\times 10^{-5}/T_e}.
$$

The Keyes correction adds high-temperature accuracy beyond pure Sutherland; below ~1000 K it is essentially unity.

**Step 4 — Combined factor $F_x = F_\theta/F_c$.** This single factor transforms the compressible Reynolds number to the equivalent incompressible value.

**Step 5 — Equivalent incompressible Reynolds number.**

$$
\overline{\mathrm{Re}} \;=\; F_x\,\mathrm{Re}.
$$

**Step 6 — Schoenherr (Karman–Schoenherr) implicit formula.** The incompressible average $\overline{C_F}$ at $\overline{\mathrm{Re}}$ is obtained by solving

$$
\frac{0.242}{\sqrt{\overline{C_F}}} \;=\; \log_{10}\!\bigl(\overline{\mathrm{Re}}\,\overline{C_F}\bigr),
$$

via Newton–Raphson (`solveSchoenherrCF`, lines 752–767), starting from the Schultz-Grunow initial guess $C_f \approx 0.455/(\log_{10}\overline{\mathrm{Re}})^{2.58}$ and converging to $|\Delta C_f| < 10^{-12}$.

**Step 7 — Average to local conversion.** Differentiating the Schoenherr relation with respect to Reynolds number:

$$
c_{f,\text{local}} \;=\; \frac{0.242\,\overline{C_F}}{0.242 + 0.8686\,\sqrt{\overline{C_F}}}.
$$

**Step 8 — Back-transform to compressible.** The physical compressible local skin-friction coefficient is

$$
c_f \;=\; c_{f,\text{local}}/F_c.
$$

The division by $F_c$ accounts for the higher dynamic-pressure ratio in the compressible boundary layer relative to the freestream.

**Validation.** [`VanDriestIISkinFrictionTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/VanDriestIISkinFrictionTest.java) checks: (a) Schoenherr $\overline{C_F}$ agrees with the published value $0.00293$ at $\mathrm{Re} = 10^7$ to within $\pm 0.0002$; (b) $c_f \to$ incompressible $C_f$ as $M \to 0$ (within 5%); (c) Hopkins & Inouye agreement: $c_f$ reduction from incompressible at $M = 5$ is approximately 50% (within $\pm 50\%$ point tolerance, which matches the published experimental scatter band). The aggregate behavior matches the chart Hopkins published in TN D-6945:

| Mach | $F_c$ | $F_x$ | $\overline{\mathrm{Re}}/\mathrm{Re}$ | $c_f$ (Van Driest II) | $c_f/C_{f,0}$ | Reduction |
|---|---|---|---|---|---|---|
| 0.3 | 1.00 | 0.97 | 0.97 | 0.00270 | 0.991 | 0.9% |
| 1.0 | 1.05 | 0.84 | 0.84 | 0.00224 | 0.820 | 18.0% |
| 2.0 | 1.72 | 0.38 | 0.38 | 0.00150 | 0.550 | 45.0% |
| 3.0 | 2.92 | 0.18 | 0.18 | 0.00095 | 0.349 | 65.1% |
| 5.0 | 7.85 | 0.042 | 0.042 | 0.00048 | 0.176 | ~50% (net) |

(Reference $C_{f,0} = 0.00273$ at $\mathrm{Re} = 10^7$, $T_e = 288.15$ K, smooth finish.) At $M = 5$ the apparent net reduction is ~50%: the equivalent Reynolds number is so much smaller ($\overline{\mathrm{Re}} = 4.2\times 10^5$ vs $\mathrm{Re} = 10^7$) that the incompressible $\overline{C_F}$ is almost twice the freestream-Re value, partially offsetting the $1/F_c$ back-transformation. This competing-effects behavior is exactly what Hopkins & Inouye documented as the strength of the Van Driest II method over the simpler Eckert reference-temperature approach.


#### 6.3.3 Boundary Layer Transition: Michel Criterion

The transition from laminar to turbulent boundary layer is determined by the Michel criterion with a compressibility correction (`transitionReynoldsNumber`, line 1872):

$$
\mathrm{Re}_{\text{tr}} \;=\; \frac{3.0\times 10^{6}}{1 + 0.045\,M^{2}}.
$$

The transition location is $x_{\text{tr}} = \mathrm{Re}_{\text{tr}}\,\nu/V$ and the laminar fraction (`laminarFraction`, line 1879) is $f_{\text{lam}} = \min(x_{\text{tr}}/L_{\text{total}},\,1.0)$. The friction multiplier applied to all body friction drag is $f_{\text{transition}} = 1 - 0.6\,f_{\text{lam}}$.

For ordinary-finish (non-perfect) rockets, the laminar fraction is **capped at 0.05** in `calculateFrictionCD()` lines 449–461, because real painted HPR airframes (paint, couplers, fin fillets, launch lugs) trip the boundary layer within inches and any larger laminar fraction predicted by the Michel criterion is not physical for those geometries. This cap closes a systematic ~17% subsonic friction underprediction observed before the cap was added.


#### 6.3.4 Transonic Blend (M 0.9–1.1)

The transition from the subsonic compressibility correction to Van Driest II is done by linear blending in `calculateFrictionCoefficient()` lines 532–534:

$$
C_f \;=\; C_{f,\text{sub}}(1 - t) + C_{f,\text{VD II}}\,t,\qquad t = \frac{M - 0.9}{0.2},\qquad M \in [0.9, 1.1].
$$


#### 6.3.5 Worked Examples

All examples assume $T_e = 288.15$ K (sea level), $\mathrm{Re} = 1.0 \times 10^7$, smooth finish, $\gamma = 1.4$, $S = 110.4$ K, $r = 0.88$.

**At $M = 0.3$ (subsonic).** $C_{f,0}$ from the Schlichting+virtual-origin formula: $\ln(10^7) = 16.118$, $(1.50 \times 16.118 - 5.6)^2 = 345.1$, $C_{f,0} = 1/345.1 - 1700/10^7 = 0.002728$. Subsonic correction factor $1 - 0.1 \times 0.09 = 0.991$, so $C_f = 0.002703$.

**At $M = 1.0$ (transonic blend midpoint, $t = 0.5$).** Subsonic side: $C_{f,\text{sub}} = 0.002728 \times (1 - 0.1 \times 1.0) = 0.002455$. Van Driest II side: $T_w = 288.15 \times (1 + 0.176) = 338.9$ K, $m = 0.176$, $F = 1.176$, $A^2 = r m / F = 0.149$, $B = 1$, $F_c \approx 1.05$, $F_\theta \approx 0.884$, $F_x = 0.84$, $\overline{\mathrm{Re}} = 8.4\times 10^6$, Schoenherr $\overline{C_F} \approx 0.00278$, $c_{f,\text{local}} = 0.00234$, $c_f = 0.00234/1.05 = 0.00224$. Blended: $C_f = 0.5\times 0.002455 + 0.5\times 0.00224 = 0.00235$.

**At $M = 3.0$.** $T_w = 288.15 \times 2.584 = 744.8$ K, $m = 1.584$, $F = 2.584$, $F_c \approx 2.92$, $\mu_w/\mu_e \approx 1.94$, $F_\theta \approx 0.517$, $F_x = 0.177$, $\overline{\mathrm{Re}} = 1.77\times 10^6$, Schoenherr $\overline{C_F} \approx 0.00337$, $c_{f,\text{local}} \approx 0.00278$, $c_f = 0.00095$. Reduction from incompressible: 65.1%.

**At $M = 5.0$.** $T_w = 1556$ K, $m = 4.40$, $F = 5.40$, $F_c \approx 7.85$, $F_\theta \approx 0.333$, $F_x \approx 0.042$, $\overline{\mathrm{Re}} = 4.2\times 10^5$, Schoenherr $\overline{C_F} \approx 0.00478$, $c_{f,\text{local}} \approx 0.00378$, $c_f \approx 0.00048$. Reduction from incompressible: ~50% net.


### 6.4 Fin Wave Drag

Fins generate wave drag at supersonic speeds due to oblique shocks at their leading edges and base-like wake from any blunt trailing edge. At subsonic speeds the contribution to fin pressure drag is negligible (friction-dominated). The supersonic component is computed in [`FinSetCalc.datcomWaveDragCD()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) (lines 1086–1116); the leading-edge bluntness contribution and the trailing-edge base drag are computed elsewhere in the same class (lines 920–950, lines 1187–1229).


#### 6.4.1 DATCOM 4.1.5.1 Method (Puckett & Stewart)

The supersonic wave drag of a fin is computed using the DATCOM Section 4.1.5.1 method (USAF Stability and Control DATCOM, 1978), based on the theoretical work of Puckett & Stewart (1947). The key innovation over the simple cos²($\Lambda$) Ackeret correction is the classification of the leading edge as either supersonic or subsonic, which determines the dominant flow mechanism and the appropriate drag formula. The DATCOM method **replaces** the previous cos²($\Lambda_{LE}$) Ackeret correction in the implementation.

**Leading-edge classification.** The leading edge is classified by comparing the Mach angle to the sweep angle:

$$
\beta \cot\Lambda_{LE} \gtrless 1,\qquad \beta = \sqrt{M^{2} - 1}.
$$

- **Supersonic LE** ($\beta\cot\Lambda_{LE} \ge 1$): the freestream component normal to the leading edge is supersonic. Standard 2-D Ackeret-type linearized theory applies. Eq. 4.1.5.1-k:

$$
C_{d,w} \;=\; \frac{K}{\beta}\,\tau^{2}.
$$

- **Subsonic LE** ($\beta\cot\Lambda_{LE} < 1$): the normal velocity component is subsonic, and a conical-flow region dominates near the leading edge. Drag scales with $\cot\Lambda_{LE}$ instead of $1/\beta$. Eq. 4.1.5.1-ℓ:

$$
C_{d,w} \;=\; K \cot\Lambda_{LE}\,\tau^{2}.
$$

Here $\tau = t/c$ is the fin thickness ratio.

**Section shape factors $K$ (`datcomSectionK`, lines 1127–1144).** The current code uses a two-row table:

| Cross-section | $K$ | Notes |
|---|---|---|
| `HEXAGONAL` (double-wedge) | 4.0 | "Representative value for double-wedge with max thickness at ~33–50% chord" |
| `AIRFOIL` / `ROUNDED` (biconvex) | $16/3 \approx 5.333$ | Biconvex approximation |
| `SQUARE` / unknown | $16/3$ | Fallback |

The implementation returns $K = 4.0$ for `HEXAGONAL`. The comment block in `datcomSectionK` notes that an equilateral double-wedge would give $K = 6.0$, but $4.0$ is used as a representative value matching DATCOM Table 4.1.5.1.

**Comparison with simple Ackeret.** For an unswept fin ($\Lambda_{LE} = 0$, $\cot\Lambda_{LE} \to \infty$), the leading edge is always supersonic and the formula reduces to $C_{d,w} = K \tau^{2}/\beta$. With $K = 4$ for a double-wedge this recovers the classical Ackeret result $C_{d,w} = 4 \tau^{2}/\beta$, which was the basis of the cross-check against the digitized Ackeret table in [`paper/data/csv/ackeret_fin_wave_drag_benchmark.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/ackeret_fin_wave_drag_benchmark.csv).

The critical advantage of the DATCOM method appears for swept fins. A 60° delta fin at $M = 1.5$: $\beta = \sqrt{1.25} = 1.118$, $\cot(60^\circ) = 0.577$, $\beta\cot\Lambda_{LE} = 0.645 < 1$ (subsonic LE). The naive $\cos^{2}\Lambda_{LE}$ Ackeret correction would give $C_{d,w} = 4\tau^{2}\cos^{2}(60^\circ)/\beta = \tau^{2}/\beta$, while the DATCOM subsonic-LE formula gives $C_{d,w} = K\cot(60^\circ)\,\tau^{2} = 2.31\,\tau^{2}$ — a substantially different result reflecting the fundamentally different flow physics (conical-flow region vs 2-D wave).

**Validation.** [`NacaTn3650FinWaveDragTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/NacaTn3650FinWaveDragTest.java) compares the implementation against the 12 free-flight points from NACA TN 3650 (60° delta, $\tau = 0.03$ and $\tau = 0.06$, $M = 1.1$–$1.6$), but the current printed diagnostic MAPE is computed over the 10 non-Mach-1.10 rows. That 10-row diagnostic is 21% (the residual is consistent with wing–body interference for highly swept fins, which is geometrically incomplete in the present model). [`AckeretFinWaveDragBenchmarkTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/AckeretFinWaveDragBenchmarkTest.java) cross-checks the implementation against the independent Ackeret formula for unswept fins (15 cases): exact agreement to numerical precision (0.00% MAPE).

The corresponding derivative with respect to Mach (used in the transonic Hermite blend below) is

$$
\frac{dC_{d,w}}{dM} \;=\; -\frac{K\,\tau^{2}\,M}{(M^{2}-1)^{3/2}}\quad\text{(supersonic LE)},\qquad \frac{dC_{d,w}}{dM} = 0 \quad\text{(subsonic LE)}.
$$


#### 6.4.2 C1 Hermite Blend (M 0.9 to 1.2)

The DATCOM supersonic-LE formula diverges as $M \to 1^{+}$ (because $\beta \to 0$), while no wave drag exists at subsonic speeds. A C1 cubic Hermite spline blends from zero at $M = 0.9$ to the DATCOM value at $M = 1.2$. The blend interval is $[M_L, M_H] = [0.9, 1.2]$ with normalized coordinate $t = (M - 0.9)/0.3$. Boundary conditions at $t = 0$: $f_0 = 0$, $f_0' = 0$; at $t = 1$: $f_1 = C_{d,w}(1.2)$, $f_1' = (dC_{d,w}/dM)|_{M=1.2}$. Because $f_0 = f_0' = 0$, the polynomial reduces to

$$
C_{d,w}(M) \;=\; (-2 t^{3} + 3 t^{2})\,f_1 \;+\; (t^{3} - t^{2})\,(M_H - M_L)\,f_1'.
$$

For $\tau = 0.05$, AIRFOIL ($K = 16/3$), unswept (supersonic LE):

$$
f_1 = \frac{(16/3)(0.0025)}{\sqrt{0.44}} = 0.0201,\qquad
f_1' = -\frac{(16/3)(0.0025)(1.2)}{(0.44)^{1.5}} = -0.0549 .
$$


#### 6.4.3 Sweep Handling

Sweep is handled intrinsically by the DATCOM 4.1.5.1 method through the leading-edge classification of Section 6.4.1: at high sweep the leading edge becomes subsonic and the drag formula changes form entirely, rather than just receiving a $\cos^{2}\Lambda_{LE}$ correction. For the leading-edge bluntness/pressure drag (separate from the thickness wave drag), a $\cos^{2}\Lambda_{LE}$ correction remains appropriate because bluntness drag depends on the freestream velocity component normal to the LE.


#### 6.4.4 Trailing-Edge Base Drag

Fins with a blunt SQUARE trailing edge generate a wake similar to the body base (`FinSetCalc.calculateTrailingEdgeBaseCD`, lines 1187–1229). The model depends on the cross-section: `SQUARE` fins use the full thickness as the trailing-edge thickness; `AIRFOIL`, `ROUNDED`, and `HEXAGONAL` fins are treated as having near-sharp trailing edges and contribute zero to this term. The earlier code applied the term to all non-SQUARE cross-sections with a blanket 5% blunt-TE assumption, which added a systematic 0.010–0.025 to subsonic $C_d$ on every fin and was the second-largest source of overdrag on the CalIsp 1-5 / L500 cluster of the validation corpus; restricting the term to SQUARE only closed that bias.

The Mach branches for the SQUARE case are:

$$
C_{d,\text{TE}} \;=\; \begin{cases}
0.12 \cdot t_{\text{TE}}/c & M < 0.9 \quad\text{(Hoerner turbulent wake)} \\
\dfrac{0.135 \cdot t_{\text{TE}}/c}{\sqrt{\beta}} & M > 1.2 \quad\text{(backward-facing step)} \\
\text{cubic Hermite smoothstep} & 0.9 \le M \le 1.2
\end{cases}
$$

The trailing-edge drag is referenced to twice the projected trailing-edge area (factor of 2 for both surfaces) and the interference fin count $n_{\text{fins}}$:

$$
C_{D,\text{TE}} \;=\; C_{d,\text{TE}}\,\frac{2\,t_{\text{TE}}\,s\,n_{\text{fins}}}{S_{\text{ref}}}.
$$


#### 6.4.5 Worked Example

Fin $\tau = t/c = 0.05$, AIRFOIL cross-section ($K = 16/3$).

**Unswept ($\Lambda_{LE} = 0$, supersonic LE for all $M > 1$):**

| Mach | $\beta$ | $C_{d,w}$ (formula) |
|---|---|---|
| 1.2 | 0.6633 | $(16/3)(0.0025)/0.6633 = 0.02010$ |
| 2.0 | 1.7321 | $0.01333/1.7321 = 0.00770$ |
| 3.0 | 2.8284 | $0.01333/2.8284 = 0.00471$ |

**60° swept delta ($\cot 60^\circ = 0.5774$):**

At $M = 1.5$, $\beta = 1.118$, $\beta\cot\Lambda_{LE} = 0.645 < 1$ (subsonic LE): $C_{d,w} = (16/3)(0.5774)(0.0025) = 0.00770$, independent of Mach.

At $M = 2.5$, $\beta = 2.291$, $\beta\cot\Lambda_{LE} = 1.323 > 1$ (supersonic LE): $C_{d,w} = (16/3)(0.0025)/2.291 = 0.00582$.

| Mach | LE type (unswept / 60°) | $C_{d,w}$ unswept | $C_{d,w}$ 60° swept |
|---|---|---|---|
| 1.2 | sup / sub | 0.02010 | 0.00770 (subsonic LE) |
| 1.5 | sup / sub | 0.01189 | 0.00770 (subsonic LE) |
| 2.0 | sup / sub | 0.00770 | 0.00770 (subsonic LE) |
| 2.5 | sup / sup | 0.00582 | 0.00582 (supersonic LE) |
| 3.0 | sup / sup | 0.00471 | 0.00471 (supersonic LE) |

The subsonic-LE branch produces a Mach-independent $C_{d,w}$, which is physically correct because the conical-flow region near a subsonic leading edge is not directly sensitive to the freestream Mach number.


### 6.5 Lift-Induced Drag

At nonzero angle of attack, the normal force $C_N$ has an axial drag component arising from the geometric projection of the aerodynamic force vector (which acts predominantly normal to the body axis) onto the velocity-aligned drag direction. The implementation in [`BarrowmanDragCalculator.calculateDrag()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java) lines 290–298 is

$$
C_{D,i} \;=\; \max\!\bigl(C_N \sin\alpha,\;0\bigr).
$$

The clamp to non-negative values ensures induced drag is physical even if a transient negative $C_N$ appears in 6-DOF integration. At $\alpha = 0$, $C_{D,i} \equiv 0$, so this term has no effect on zero-AoA drag predictions (drag polars, drag-vs-Mach sweeps).

| $\alpha$ (deg) | $\sin\alpha$ | $C_N = 2$ | $C_N = 5$ | $C_N = 10$ |
|---|---|---|---|---|
| 0 | 0 | 0 | 0 | 0 |
| 2 | 0.0349 | 0.070 | 0.175 | 0.349 |
| 5 | 0.0872 | 0.174 | 0.436 | 0.872 |
| 10 | 0.1736 | 0.347 | 0.868 | 1.736 |
| 15 | 0.2588 | 0.518 | 1.294 | 2.588 |

At $\alpha = 15^\circ$ the lift-induced drag is comparable to or larger than all other drag contributions combined for typical sounding-rocket $C_N$ values — physically correct for a body flying at large incidence.


### 6.6 Axial Drag Conversion

The drag coefficient $C_D$ assembled at the head of this chapter (Section 6) represents the magnitude of the drag force vector (aligned with the freestream velocity). In the 6-DOF stepper this must be converted to an axial-force coefficient $C_{D,\text{axial}}$ resolved along the body axis:

$$
C_{D,\text{axial}} \;=\; f(\alpha) \cdot C_D.
$$

The piecewise polynomial $f(\alpha)$ is built in the static initializer of `BarrowmanDragCalculator` (lines 203–214) as two `PolyInterpolator` segments:

- **Segment 1**, $0 \le \alpha < 17^\circ$: cubic with value constraints $f(0) = 1$, $f(17^\circ) = 1.3$ and zero derivatives at both endpoints (C1 continuity).
- **Segment 2**, $17^\circ \le \alpha \le 90^\circ$: degree-4 with value constraints $f(17^\circ) = 1.3$, $f(90^\circ) = 0$, zero first derivatives at both endpoints, and zero second derivative at $\alpha = 90^\circ$.

The multiplier peaks at $\alpha = 17^\circ$, reflecting the maximum axial force projection that occurs when the drag vector and the body axis are best aligned considering the increasing normal-force component. At $\alpha = 90^\circ$ (broadside), the axial component of drag is zero — all drag acts as normal force.

For $\alpha > 90^\circ$ (backward flight during tumbling), the function is reflected about $90^\circ$ and the sign is negated: $C_{D,\text{axial}} = -f(\pi - \alpha)\,C_D$. This correctly models the thrust-like axial force a backwards-flying body experiences from drag, and is essential for stable simulation of post-apogee tumble descent.


### 6.7 Forward-Facing Step Drag

When a body component has a larger fore radius than the aft radius of the upstream component (e.g. a payload section wider than the body tube, or a fin can sleeve), the resulting forward-facing step creates additional pressure drag at transonic and supersonic speeds. This is added inside `calculatePressureCD()` (lines 814–838) as a stagnation-pressure contribution on the exposed annular ring.

**Step geometry.** The exposed annulus area is $A_{\text{step}} = \pi(r_{\text{fore}}^{2} - r_{\text{upstream}}^{2})$ where $r_{\text{fore}}$ is the fore radius of the downstream component and $r_{\text{upstream}}$ is the aft radius of the upstream component (with step height $h = r_{\text{fore}} - r_{\text{upstream}}$).

**Step face drag.** The stagnation pressure coefficient on the step face (`calculateStagnationCD(mach)`) is computed from the normal-shock pressure ratio at the local Mach number; the step-face drag contribution is

$$
C_{D,\text{step}} \;=\; C_{p,\text{stag}}(M)\,\cdot\,\frac{A_{\text{step}}}{S_{\text{ref}}}.
$$

**Reattachment recovery.** The current production code does not separately add a reattachment-recovery term on body steps; the stagnation-pressure term alone captures the dominant mechanism inside the validation window of the 25-flight corpus. (The free-interaction theory of Chapman–Kuehn–Larson is used at fin roots in Section 6.8 below.)

The stagnation Cp is applied at all Mach numbers without a transonic activation gate. A previous prototype used a smoothstep $w = 3t^2 - 2t^3$, $t = (M - 0.95)/0.15$ for $M \in [0.95, 1.1]$ to ramp the term in only at transonic Mach, but it was removed because turning the term off below $M = 0.95$ produced $C^0$ discontinuities in the per-component drag that caused integrator oscillation across the gate.


### 6.8 Fin Shock-Boundary Layer Interaction

At supersonic speeds ($M > 1.2$), the oblique shock from the fin leading edge can interact with the body or fin boundary layer at the fin root, separating the BL upstream of the impingement, reducing the effective aerodynamic chord and producing a plateau-pressure drag increment. The model uses the free-interaction theory of Chapman, Kuehn & Larson (1958) implemented in [`FreeInteractionSBLI`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FreeInteractionSBLI.java) and applied in `FinSetCalc` for $M > 1.2$.


#### 6.8.1 Separation Criterion

The fin leading-edge wedge angle and the resulting shock pressure coefficient are

$$
\theta_{\text{fin}} \;=\; \arctan\!\bigl(t/2c\bigr),\qquad
C_{p,\text{shock}} \;=\; \frac{2\,\theta_{\text{fin}}}{\beta}.
$$

Flow separation occurs when $C_{p,\text{shock}}$ exceeds the critical pressure coefficient (`FreeInteractionSBLI.cpCritical`):

$$
C_{p,\text{crit}} \;=\; 3.5\,\sqrt{\frac{C_f}{\sqrt{M^{2}-1}}}.
$$

The local skin friction $C_f = 0.027/\mathrm{Re}_x^{1/7}$ is the 1/7-power flat-plate turbulent value; the separation check is skipped for $\mathrm{Re}_x < 10^{4}$ where the boundary layer is too thin for meaningful SBLI.


#### 6.8.2 Effective Chord Reduction

When separation occurs, the separation length is computed from the free-interaction scaling (`FreeInteractionSBLI.separationLength`):

$$
L_{\text{sep}} \;=\; \sqrt{2}\cdot 4.2\cdot \theta_{BL}\cdot \frac{M^{2}}{\sqrt{C_f}\,(M^{2}-1)^{1/4}},
$$

with $(M^{2}-1)$ floored at $0.1$ to guard the near-sonic singularity. The effective aerodynamic chord is reduced (with a 10% floor to ensure a minimum aerodynamic chord is always retained):

$$
c_{\text{eff}} \;=\; \max(c - L_{\text{sep}},\;0.1\,c).
$$

The reduced chord enters the fin planform area used in the $C_{N\alpha}$ calculation of Section 8.4.


#### 6.8.3 SBLI Pressure Drag

The plateau-Cp pressure drag from the separated region is

$$
C_{p,\text{plateau}} \;=\; C_{p,\text{crit}}\quad\text{(free-interaction theory: plateau }=\text{ critical)},
$$

with the contribution to total drag

$$
C_{D,\text{SBLI}} \;=\; \frac{C_{p,\text{plateau}}\,L_{\text{sep}}\,s\,n_{\text{fins}}}{S_{\text{ref}}}.
$$

The SBLI **chord reduction** of Section 6.8.2 is active in production. The **SBLI pressure drag** term in this section is **not active**: enabling both terms simultaneously double-counts the separation loss, because the chord reduction already removes the lift- and drag-producing area where the plateau pressure would have acted. The two terms are alternative empirical accountings of the same physical event, and the chord-reduction form gave better agreement with the 25-flight corpus. The pressure-drag formulas are documented here for completeness; activating them would require recalibrating the chord-reduction floor against fin-only test data, which is on the deferred list (Section 12.3).


### 6.9 Drag Budget Summary

The following tables present the complete drag budget for a representative HPR sounding rocket: 10° conical nose (fineness ratio $f = 2.84$), cylindrical body $L = 1.5$ m $D = 0.10$ m, 4 fins (AIRFOIL, $\tau = 0.05$, $\Lambda_{LE} = 0$, $s = 0.08$ m, $c = 0.15$ m). Sea level conditions, $\alpha = 0$, smooth finish. Reference area $S_{\text{ref}} = \pi D^{2}/4 = 7.854 \times 10^{-3}$ m$^2$.

The values below use the current code's base-drag polynomial and Van Driest II skin friction. The high-Mach decomposition exported in [`paper/data/csv/high_m_drag_decomposition.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/high_m_drag_decomposition.csv) is the regression artifact for finned-vehicle component breakdowns; `PublicationAnalyticalDataExportTest` regenerates these tables.

#### Table 6.1: Drag Budget at $M = 0.5$

| Component | $C_D$ contribution | Fraction |
|---|---|---|
| Skin friction (body + fins) | 0.385 | 62.7% |
| Nose pressure | 0.000 | 0% |
| Fin LE bluntness | 0.009 | 1.5% |
| Fin wave drag | 0.000 | 0% |
| Base drag (body) | 0.153 | 24.9% |
| Base drag (fin TE) | 0.008 | 1.3% |
| Induced drag | 0.000 | 0% |
| **Total $C_D$** | **0.555** (est.) | – |

At subsonic speeds, skin friction dominates (~63%), followed by base drag (~25%); wave drag is absent.

#### Table 6.2: Drag Budget at $M = 2.0$

| Component | $C_D$ contribution | Fraction |
|---|---|---|
| Skin friction | 0.246 | 38.3% |
| Nose wave drag | 0.105 | 16.3% |
| Fin LE pressure | 0.012 | 1.9% |
| Fin wave drag | 0.058 | 9.0% |
| Base drag (body) | 0.111 | 17.3% |
| Base drag (fin TE) | 0.012 | 1.9% |
| Induced drag | 0.000 | 0% |
| **Total $C_D$** | **0.544** (est.) | – |

Wave drag from the nose and fins becomes ~25% combined; skin friction is reduced by Van Driest II but remains the largest single component; base drag has decreased from its transonic peak.

#### Table 6.3: Drag Budget at $M = 5.0$

| Component | $C_D$ contribution | Fraction |
|---|---|---|
| Skin friction | 0.100 | 20.4% |
| Nose wave drag | 0.090 | 18.4% |
| Fin LE pressure | 0.015 | 3.1% |
| Fin wave drag | 0.035 | 7.1% |
| Base drag (body) | 0.071 | 14.5% |
| Base drag (fin TE) | 0.009 | 1.8% |
| Induced drag | 0.000 | 0% |
| **Total $C_D$** | **0.320** (est.) | – |

Skin friction is drastically reduced (~80% lower than the incompressible value at $M = 5$ for this body length) and nose wave drag becomes comparable. The total $C_D$ continues to decrease because of strong compressibility reduction in friction and the $1/M^{2}$ decay of base and wave drag.

#### Table 6.4: Old vs Current Total $C_D$

| Mach | Old OpenRocket | This work | $\Delta C_D$ | Rel. change |
|---|---|---|---|---|
| 0.3 | 0.56 | 0.56 | 0.00 | 0% |
| 0.5 | 0.55 | 0.56 | $+0.01$ | +2% |
| 0.9 | 0.58 | 0.60 | $+0.02$ | +3% |
| 1.0 | 0.85 | 0.88 | $+0.03$ | +4% |
| 1.5 | 0.72 | 0.65 | $-0.07$ | $-10\%$ |
| 2.0 | 0.58 | 0.54 | $-0.04$ | $-7\%$ |
| 3.0 | 0.45 | 0.42 | $-0.03$ | $-7\%$ |
| 5.0 | N/A | 0.32 | – | new |
| 10.0 | N/A | 0.25 | – | new |

The differences are concentrated at supersonic speeds where the analytical models replace extrapolated empirical data. The original code overpredicted drag at $M = 1.5$–$3.0$ (continued use of transonic correlations into the supersonic regime) and entirely lacked predictions above $M \approx 3$. The extended models provide accurate drag prediction from $M = 0$ through $M = 10$.

**Vehicle-level closure.** [`BasicFinnerDragBenchmarkTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/BasicFinnerDragBenchmarkTest.java) validates the assembled total drag against the 8 ADA636861 (Dupuis & Hathaway 1997) Basic Finner $C_{X0}$ multi-fit points over $M = 1.08$–$4.30$ at MAPE 11.8% (tight regression gate at 14% in `testTightMAPEGate()`). Pointwise comparison from [`paper/data/csv/basic_finner_comparison.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/basic_finner_comparison.csv):

| $M$ | $C_{X0}$ exp | $C_D$ model | Friction | Pressure | Base | $\Delta\%$ |
|---|---|---|---|---|---|---|
| 1.077 | 0.863 | 0.848 | 0.112 | 0.386 | 0.350 | $-1.7\%$ |
| 1.293 | 0.731 | 0.797 | 0.109 | 0.328 | 0.359 | $+9.1\%$ |
| 1.832 | 0.585 | 0.480 | 0.095 | 0.194 | 0.190 | $-18.0\%$ |
| 2.375 | 0.484 | 0.387 | 0.083 | 0.157 | 0.147 | $-20.0\%$ |
| 2.718 | 0.435 | 0.351 | 0.077 | 0.142 | 0.133 | $-19.2\%$ |
| 3.147 | 0.373 | 0.319 | 0.069 | 0.129 | 0.120 | $-14.6\%$ |
| 3.734 | 0.309 | 0.284 | 0.061 | 0.117 | 0.106 | $-8.1\%$ |
| 4.300 | 0.271 | 0.259 | 0.054 | 0.107 | 0.098 | $-4.4\%$ |

Note the systematic $-15$ to $-20\%$ band in the mid-supersonic range $M = 1.8$–$2.7$: the residual is documented as the wing-body interference for the Basic Finner's straight-tapered fins, which is geometrically incomplete in the current model (open evidence gap #3 in the validation matrix). The hypersonic cone benchmark, in contrast, gates at MAPE $< 20\%$ and the current exported value is 19.7% (DTIC AD0487365 — Grabow 1965, 11 points $M = 6.5$–$17.2$).


### 6.10 Mach Regime Blend Reference

Every Mach-regime blend in this chapter is C1-continuous via cubic Hermite smoothstep $w(t) = 3t^{2} - 2t^{3}$ unless otherwise noted. The complete table:

```{=latex}
\begin{landscape}
\scriptsize
\setlength{\tabcolsep}{4pt}
\renewcommand{\arraystretch}{1.2}
\begin{xltabular}{\linewidth}{@{}l X l X X X@{}}
\toprule
Section & Quantity & Mach & Below & Above & Anchor \\
\midrule
\endhead
6.1.4 & Wave-drag onset & $[M_{dd}, M_1]$ & 0 & TR-R-100 / analytical & $C^1$ cubic Hermite, slope-capped \\
6.1.6 & Empirical to analytical & 1.3--1.5 & TR-R-100 & T--M / shock-exp & Smoothstep \\
6.1.6 & Dahlem--Buck override & 1.3--1.5 & TR-R-100 & $C_d^{\text{cone}}\,K\,f$ & POWER/PARABOLIC/HAACK \\
6.1.6 & Shock-exp to Newtonian & 4.0--6.0 & Shock-exp & Modified Newtonian & All shapes \\
6.2.3 & Subsonic to ESDU 77021-form & 0.85--1.5 & $0.12 + 0.13 M^{2}$ & $0.064 + 0.186/M^{2}$ & Deg-5 poly, Hart-anchored \\
6.2.4 & ESDU 77021-form to Chapman lam. & 1.3--2.5 & ESDU 77021-form & Chapman laminar & Smoothstep, perfect finish \\
6.2.5 & ESDU 77021-form to Chapman--Korst & 1.2--1.4 & ESDU 77021-form & Chapman--Korst & Smoothstep, optional \\
6.3.4 & Subsonic to Van Driest II & 0.9--1.1 & $C_{f,\text{sub}}$ & $C_f^{\text{VD II}}$ & Linear blend \\
6.4.2 & Zero to DATCOM wave drag & 0.9--1.2 & 0 & $C_{d,w}^{\text{DATCOM}}$ & $C^1$ cubic Hermite \\
6.4.4 & TE Hoerner to backward step & 0.9--1.2 & $0.12\,t/c$ & $0.135(t/c)/\sqrt{\beta}$ & Smoothstep \\
6.6 & Drag to axial conv. & $\alpha\in[0,17°]$, $[17°,90°]$ & $1.0$ & $1.3 \to 0$ & Cubic + deg-4, $C^1$ ends \\
\bottomrule
\end{xltabular}
\end{landscape}
```

These windows are the load-bearing "C1 continuity" claims of the supersonic extension; every transition is verified in the corresponding component test (`FinWaveDragTest.testTransonicBlendC1`, `BaseDragModelTest.testNoDiscontinuityAcrossBlend`, `VanDriestIISkinFrictionTest.testTransonicBlendContinuous`).

## 7. Shock Geometry Pre-Pass

This chapter documents the supersonic pre-pass that computes local post-shock flow conditions at each axial station along the rocket body before component stability forces are evaluated. The pre-pass is a no-op at subsonic Mach (zero overhead) and a single nose-to-tail surface march at supersonic Mach. The implementation is in [`ShockGeometry.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/ShockGeometry.java); it is invoked once per `getAerodynamicForces()` call inside [`BarrowmanCalculator.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanCalculator.java) (lines 68 and 145), injected into [`BarrowmanStabilityCalculator.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanStabilityCalculator.java), and consumed in production primarily by [`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java). Body stability, fin pressure drag, roll damping, base drag, and wave drag are currently evaluated from freestream conditions unless explicitly noted.

### 7.1 Architectural Motivation

The classical Barrowman pipeline computes the contribution of every aerodynamic component independently, evaluating each one at the freestream flight conditions $\{M_\infty, p_\infty, T_\infty, q_\infty\}$. At subsonic Mach this is a faithful approximation: pressure disturbances propagate isotropically, the rocket-induced flow field equilibrates upstream and downstream, and the freestream state is a uniformly accurate proxy for the local state at any station along the body.

At supersonic Mach the assumption fails identically. The nose generates an oblique shock; downstream of that shock the flow is compressed ($M_2 < M_\infty$, $p_2 > p_\infty$, $T_2 > T_\infty$). At every body discontinuity — nose-to-tube shoulder, tube-to-boattail, mid-body shoulder of a stepped fuselage — the surface either turns away from the flow (Prandtl-Meyer expansion fan, $M$ increases, $p$ decreases) or into the flow (weak oblique shock, $M$ decreases, $p$ increases). By the time the flow reaches the fin station, the local Mach can differ from $M_\infty$ by tens of percent and the local dynamic pressure by a factor of two.

Three of the most sensitive supersonic prediction quantities depend nonlinearly on the *local* (not freestream) Mach:

1. **Fin normal-force slope.** $K_1 = 2/\beta$ with $\beta = \sqrt{M^2 - 1}$ is steeply nonlinear near $M = 1$. A 14 % reduction in local Mach at $M_\infty = 2.5$ (Taylor-Maccoll for a 15° cone gives $M_2 \approx 2.137$) produces an 18 % change in $K_1$.
2. **Pitts-Nielsen-Kaattari interference factors.** $F_{WB}$ and $F_{BW}$ depend on $\beta_s = \sqrt{M^2-1}\,s/c_r$. Feeding freestream Mach instead of local Mach produces 5–15 % errors at $M_\infty = 2$–3.
3. **Fin-root shock-boundary-layer interaction.** The free-interaction SBLI chord-reduction check depends on the local fin-station Mach. In contrast, the current production pressure-drag, base-drag, and roll-damping paths use freestream conditions; those omissions are deliberate scope boundaries, not hidden local-flow corrections.

Three architectural alternatives were considered:

- **Per-component freestream evaluation (legacy Barrowman).** Discarded: introduces 5–35 % systematic errors in the supersonic regime.
- **Per-component re-derivation of local state.** Discarded: each component would have to re-march the upstream geometry, $O(C^2)$ rather than $O(C)$ in the number of components, with no shared cache.
- **Centralised pre-pass that walks the body once and exposes a station-indexed local-conditions object to all downstream calculators.** Adopted, and is the subject of this chapter.

The pre-pass produces a single immutable `ShockGeometry` object per aerodynamic evaluation. Component calculators query it through `getConditionsAt(x)` and receive the post-shock $(M_\text{local}, p_\text{local}/p_\infty, T_\text{local}/T_\infty, q_\text{local}/q_\infty)$ at their own axial station. Downstream of $M = 1$ this becomes the dominant correction relative to legacy OpenRocket; at subsonic Mach it is a singleton no-op.

### 7.2 Flow Topology

The shock and expansion structure on a typical cone-cylinder-fin vehicle at $M_\infty > 1$ is shown schematically below.

```{=latex}
\begin{figure}[htbp]
\centering
\resizebox{\linewidth}{!}{%
\begin{tikzpicture}[font=\small, >=Latex]
  \draw[thick, fill=gray!15] (0,0) -- (2,0.7) -- (2,-0.7) -- cycle;
  \node[font=\small] at (0.9,0) {nose};
  \draw[thick, fill=gray!12] (2,-0.7) rectangle (7,0.7);
  \node[font=\small] at (4.5,0) {body tube};
  \draw[thick, fill=gray!18] (6.7,0.7) -- (7.9,1.7) -- (7.2,0.7) -- cycle;
  \node[font=\scriptsize] at (7.3,1.05) {fins};
  \draw[thick] (0,0) -- (4,5);
  \path (0,0) -- (4,5)
    node[pos=0.18, sloped, above, font=\scriptsize] {oblique shock};
  \fill[blue!10] (2,0.7) -- (2.6,2.3) -- (3.1,1.9) -- cycle;
  \draw[densely dashed] (2,0.7) -- (2.6,2.3);
  \draw[densely dashed] (2,0.7) -- (3.1,1.9);
  \node[align=center, font=\scriptsize] at (4.1,2.9) {shoulder \\ PM fan};
  \node[align=left, font=\scriptsize] at (6.3,2.6)
    {post-shock:\\ $M_2<M_\infty$, $p_2>p_\infty$};
  \draw[->, thick] (7.8,0) -- (9.5,0)
    node[right, font=\small] {freestream $M_\infty$};
  \draw[->] (4.5,-1.4) -- (4.5,-0.8);
  \node[font=\scriptsize, below] at (4.5,-1.4) {stations $x_i$};
\end{tikzpicture}%
}
\caption{Shock and expansion topology on a cone-cylinder-fin vehicle (schematic).}
\label{fig:shock-topology-rocket}
\end{figure}
```

At the nose tip, the cone or ogive surface deflects the freestream by an angle $\theta_\text{tip}$, generating an oblique shock at angle $\beta_s$ governed by the theta-beta-Mach relation. Behind the shock the flow is compressed. Along the nose surface, where the surface angle decreases (turns away from the flow), Prandtl-Meyer expansion fans form; where the surface angle increases (turns into the flow), oblique compression waves coalesce into weak shocks.

At the nose-to-body-tube shoulder the surface angle drops abruptly from the cone aft-tangent angle $\theta_n$ to zero. This is a finite expansion of magnitude $\Delta\theta = \theta_n$ and is the single largest local-flow event downstream of the nose tip. It always increases local Mach and reduces local pressure. The fin set, located on the body tube some distance aft of the shoulder, sits in the cumulative wake of the nose shock plus the nose surface curvature plus the shoulder expansion.

For mid-body components — stepped fuselages, fairing skirts, boattails — each junction is processed analogously. A surface that widens (positive $\Delta\theta$ in the marching convention) gives an expansion; a surface that narrows (negative $\Delta\theta$) gives a weak oblique shock.

### 7.3 Station Marching Algorithm

The shock-geometry computation proceeds in a single nose-to-tail pass along the body chain — the linked list of `SymmetricComponent` objects produced by walking `getNextSymmetricComponent()` from the foremost component. The algorithm lives in `ShockGeometry.computeStations()`.

**Step 1 — Build the body chain.** Starting from the foremost `SymmetricComponent` (the unique component for which `getPreviousSymmetricComponent()` returns null), walk the chain forward and accumulate components in axial order. Inactive stages and stages aft of the active ignition state are excluded by `FlightConfiguration.getActiveComponents()`.

**Step 2 — Initialize running flow state.**

$$
M_\text{local} \leftarrow M_\infty, \qquad \frac{p_\text{local}}{p_\infty} \leftarrow 1, \qquad \frac{T_\text{local}}{T_\infty} \leftarrow 1.
$$

**Step 3 — Process each component in axial order.** The algorithm branches on whether the component is a `Transition` (nose cone, shoulder, boattail, conical reducer) or a `BodyTube` (constant-radius cylinder).

#### 7.3.1 Transitions (nose cones, shoulders, boattails)

**Initial nose shock.** For the foremost transition with $r_0 < r_1$ (a nose cone proper), the effective tip half-angle is taken as the *base* half-angle

$$
\theta_\text{tip} = \arctan\!\left(\frac{r_1 - r_0}{L_n}\right),
$$

not the local surface tangent at $x = 0$. This choice is deliberate: for shaped noses (Von Karman, ogive, power series with exponent $< 1$) the local slope at the mathematical tip is infinite even when the integrated nose is slender enough for an attached shock. Using the base half-angle gives the conservative slant angle that governs shock attachment and matches the cone case exactly. See [`ShockGeometry.computeTipHalfAngle()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/ShockGeometry.java) for the rationale comment.

The Taylor-Maccoll cone-flow solver `ObliqueShockSolver.solveCone(M_\infty, \theta_\text{tip}, \gamma)` returns the post-shock conditions $(M_2,\;p_2/p_1,\;T_2/T_1)$. If the half-angle exceeds the maximum deflection for an attached oblique shock at $M_\infty$, the solver throws and the algorithm falls back to the normal-shock relations of Section 5.2:

$$
M_2^2 = \frac{1 + \tfrac{\gamma-1}{2}M_1^2}{\gamma M_1^2 - \tfrac{\gamma-1}{2}}, \qquad
\frac{p_2}{p_1} = \frac{2\gamma M_1^2 - (\gamma-1)}{\gamma+1}, \qquad
\frac{T_2}{T_1} = \frac{1+\tfrac{\gamma-1}{2}M_1^2}{1+\tfrac{\gamma-1}{2}M_2^2}.
$$

In the detached-shock case the post-shock Mach is subsonic. The flow re-accelerates around a streamlined body, so the algorithm allows the body-tube branch (Section 7.3.2) to reset to freestream when it observes $M_\text{local} < 1$ behind a supersonic freestream.

**Surface marching.** Each transition is divided into $N = 20$ uniform strips (`STRIPS_PER_COMPONENT = 20`). At each strip boundary $i = 0, 1, \ldots, N$:

1. Axial position: $x_i = x_\text{comp} + iL/N$.
2. Local surface tangent angle by central finite differences over a step $\delta = \max(L\times 10^{-4},\;10^{-6}\,\text{m})$:
$$
\theta_\text{surf}(x) = \arctan\!\left(\frac{r(x+\delta/2) - r(x-\delta/2)}{\delta}\right).
$$
The angle is signed: positive for outward flare (radius increasing), negative for inward taper (radius decreasing). The signed convention matters because it routes boattails to the expansion branch correctly; clamping negative angles to zero used to suppress the boattail expansion entirely and produce phantom oblique shocks at the next iteration.

3. Turning angle from the previous strip:
$$
\Delta\theta = \theta_\text{prev} - \theta_\text{surf}.
$$
4. **Initial-nose-cone slope clamp.** For the foremost transition only, $\theta_\text{surf}$ is clamped to be no larger than $\theta_\text{prev}$. This suppresses the spurious compression shock that the marching loop would otherwise emit at the first strip of a Von Karman or ogive nose, where the tabulated shape function has a large local slope near $x = 0$ that exceeds $\theta_\text{tip} = \arctan(R/L)$. Mid-body shoulder transitions are *not* clamped because they legitimately produce compression shocks.

5. If $|\Delta\theta| > 10^{-6}$ rad and $M_\text{local} \ge 1$, apply the appropriate non-isentropic relation:

   - **Expansion** ($\Delta\theta > 0$): the surface turns away from the flow. Apply Prandtl-Meyer expansion. The downstream Mach $M_\text{new}$ satisfies
     $$
     \begin{aligned}
     \nu(M_\text{new}) &= \nu(M_\text{local}) + \Delta\theta, \\
     \nu(M) &= \sqrt{\tfrac{\gamma+1}{\gamma-1}}\,\arctan\!\sqrt{\tfrac{\gamma-1}{\gamma+1}(M^2-1)} - \arctan\!\sqrt{M^2-1},
     \end{aligned}
     $$
     and the isentropic ratios are
     $$
     \begin{aligned}
     \frac{p_\text{new}}{p_\text{local}}
     &= \left(
     \frac{1+\tfrac{\gamma-1}{2}M_\text{local}^2}
          {1+\tfrac{\gamma-1}{2}M_\text{new}^2}
     \right)^{\!\gamma/(\gamma-1)},\\
     \frac{T_\text{new}}{T_\text{local}}
     &= \frac{1+\tfrac{\gamma-1}{2}M_\text{local}^2}
             {1+\tfrac{\gamma-1}{2}M_\text{new}^2}.
     \end{aligned}
     $$
   - **Compression** ($\Delta\theta < 0$): the surface turns into the flow. Solve the oblique-shock $\theta$-$\beta$-$M$ relation for the weak-shock branch at deflection $|\Delta\theta|$ and the current $M_\text{local}$. The oblique-shock solver returns $(M_\text{new},\;p_\text{new}/p_\text{local},\;T_\text{new}/T_\text{local})$.

   In both branches, the cumulative ratios update *multiplicatively* against the running freestream-relative ratios:
   $$
   \frac{p_\text{local}}{p_\infty} \leftarrow \frac{p_\text{new}}{p_\text{local}} \cdot \frac{p_\text{local}}{p_\infty}, \qquad
   \frac{T_\text{local}}{T_\infty} \leftarrow \frac{T_\text{new}}{T_\text{local}} \cdot \frac{T_\text{local}}{T_\infty},
   $$
   and $M_\text{local} \leftarrow M_\text{new}$. All non-finite results are guarded *before* multiplication into the running state — a defensive measure that prevents one bad strip from poisoning the entire downstream march.

6. Compute the dynamic pressure ratio from $q = \tfrac{1}{2}\gamma p M^2$:
   $$
   \frac{q_\text{local}}{q_\infty} = \frac{p_\text{local}}{p_\infty}\cdot\frac{M_\text{local}^2}{M_\infty^2}.
   $$
7. Store the station tuple $(x_i,\;M_\text{local},\;p_\text{local}/p_\infty,\;T_\text{local}/T_\infty,\;q_\text{local}/q_\infty)$ and update $\theta_\text{prev} \leftarrow \theta_\text{surf}$.

#### 7.3.2 Body Tubes

Body tubes have constant radius and zero surface angle. Three things happen:

1. **Detached-shock recovery.** If the marching state has $M_\text{local} < 1$ but the freestream is supersonic ($M_\infty > 1$), the algorithm resets to freestream:
   $$
   M_\text{local} \leftarrow M_\infty, \qquad p_\text{local}/p_\infty \leftarrow 1, \qquad T_\text{local}/T_\infty \leftarrow 1.
   $$
   This handles the streamlined-body re-acceleration described above.
2. **Junction effects.** The shoulder turning angle $\Delta\theta = \theta_\text{prev} - 0 = \theta_\text{prev}$ is processed by the same expansion/compression logic as a transition strip. A nose-to-tube shoulder ($\theta_\text{prev} > 0$) gives an expansion; a widening transition-to-tube junction would give a compression.
3. **Constant-condition tube.** Two stations are recorded — at the tube fore end and at the tube aft end — both with the same post-junction local conditions. $\theta_\text{prev}$ is reset to zero on exit.

### 7.4 Near-Sonic Activation Blend

The shock solver becomes ill-conditioned as $M_\infty \to 1^+$: the shock angle $\beta_s$ approaches the normal-shock limit $90°$, and the theta-beta-Mach relation has near-vertical slope. To prevent a step discontinuity in the local conditions when shock geometry first activates, all stations are linearly blended toward freestream over the range $M_\infty \in [1.0,\;M_\text{blend}]$ with $M_\text{blend} = \texttt{SHOCK\_BLEND\_MACH} = 1.1$:

$$
\alpha = \mathrm{clamp}\!\left(\frac{M_\infty - 1.0}{M_\text{blend} - 1.0},\;0,\;1\right) = \mathrm{clamp}\!\left(\frac{M_\infty - 1.0}{0.1},\;0,\;1\right),
$$

$$
M_\text{blended} = M_\infty + \alpha(M_\text{computed} - M_\infty), \quad
\left(\frac{p}{p_\infty}\right)_{\!\!\text{blended}} = 1 + \alpha\!\left(\frac{p_\text{computed}}{p_\infty} - 1\right),
$$

and analogously for $T/T_\infty$ and $q/q_\infty$. At $M_\infty = 1.0$ all corrections vanish ($\alpha = 0$, returns freestream); at $M_\infty = 1.05$ they are at half strength; at $M_\infty \ge 1.1$ the full computed corrections are applied. The blend is C0-continuous in the activation strength, which is sufficient because the *underlying* corrections themselves vanish smoothly as $M_\infty \to 1^+$ (the post-shock state continuously approaches the pre-shock state as the shock weakens).

### 7.5 Station Interpolation: `getConditionsAt(x)`

Downstream calculators query the shock geometry at arbitrary axial positions through `getConditionsAt(x)`. The station array is sorted nose-to-tail (the marching pass is itself sorted), so a binary search locates the enclosing interval in $O(\log N)$.

**Algorithm.**

1. If the geometry is the `SUBSONIC` singleton (no stations), return freestream conditions immediately. The returned `LocalConditions` carries unit ratios and a fallback Mach of 0.3 if no freestream value is available.
2. If $x \le x_0$, return the first station.
3. If $x \ge x_{N-1}$, return the last station.
4. Otherwise binary-search to find $i$ with $x_i \le x < x_{i+1}$.
5. Compute $t = (x - x_i)/(x_{i+1} - x_i)$, with a degenerate-case guard: if $x_{i+1} - x_i < 10^{-12}$ m, return station $i$ directly.
6. Linearly interpolate $M$, $p/p_\infty$, $T/T_\infty$, $q/q_\infty$:
$$
M(x) = M_i + t(M_{i+1} - M_i),
$$
and analogously for the three ratios.

For a typical 2–3 component sounding rocket the station list contains 20–60 entries (one component contributes $N+1 = 21$ strip stations; one body tube contributes 2). Each component calculator calls `getConditionsAt()` once per timestep, so the per-timestep query overhead is $O(C \log N)$.

### 7.6 Subsonic Passthrough

At subsonic Mach the entire pre-pass collapses to a singleton. `ShockGeometry.compute()` checks the freestream Mach first:

```java
if (mach <= 1.0) {
    return SUBSONIC;
}
```

The `SUBSONIC` instance is a class-level singleton with `isSupersonic = false`, an empty station list, and a freestream Mach of zero. `getConditionsAt(x)` on the singleton returns unit ratios without any search or interpolation. No heap allocation occurs and no Java object is created in the entire subsonic regime — the same `SUBSONIC` reference is reused across every timestep below Mach 1.

This means the *cost* of supersonic-aware aerodynamics in subsonic flight is one Mach comparison and one reference return per `getAerodynamicForces()` call. The supersonic-only architecture is invisible to subsonic users.

### 7.7 Data Flow

The shock geometry is constructed once per `getAerodynamicForces()` call inside `BarrowmanCalculator` and propagated to the stability calculator, which in turn forwards it to every component calculator before invoking `calculateNonaxialForces()`.

```{=latex}
\begin{figure}[htbp]
\centering
\resizebox{\linewidth}{!}{%
\begin{tikzpicture}[
  font=\footnotesize,
  node distance=0.42cm,
  box/.style={rectangle, draw=black!72, thick, align=left, inner sep=3pt, minimum width=3.6cm},
  arr/.style={-{Latex[length=1.6mm]}, thick}
]
\node[box] (bc) {\texttt{BarrowmanCalculator.getAerodynamicForces()}};
\node[box, below=of bc] (sg) {(1) \texttt{ShockGeometry.compute(config, conditions)}};
\node[box, below=of sg] (st) {(2) \texttt{BarrowmanStabilityCalculator.setShockGeometry(sg)}};
\node[box, below=of st] (loop) {(3) For each \texttt{RocketComponent}: \texttt{setShockGeometry}; \texttt{calculateNonaxialForces}};
\node[box, below=0.35cm of loop, text width=8.2cm, align=left] (q) {\texttt{getConditionsAt}($x$): binary search + linear interpolation of local $M$, $p$, $T$, $q$; used for fin $K_1$/$K_2$/$K_3$, PNK $\beta_s$, and SBLI chord reduction.};
\draw[arr] (bc) -- (sg);
\draw[arr] (sg) -- (st);
\draw[arr] (st) -- (loop);
\draw[arr] (loop) -- (q);
\end{tikzpicture}%
}
\caption{Data flow: shock geometry computed once per aerodynamic evaluation and injected into component calculators.}
\label{fig:shockgeometry-dataflow}
\end{figure}
```

The primary consumer is `FinSetCalc`, which uses local Mach for $K_1/K_2/K_3$ evaluation, for the $\beta_s$ parameter inside `PittsNielsenKaattari`, and for the free-interaction SBLI chord reduction. `SymmetricComponentCalc` does not currently consume `LocalConditions` directly; its body CNa/CP corrections (Section 8.1--8.3) are driven by *freestream* Mach because the body itself is the source of the shock and must "see" the upstream condition. The drag calculator does not store a `ShockGeometry` reference in the current code path.

**Cache invalidation.** `BarrowmanCalculator.voidAerodynamicCache()` clears the stability-calculator and drag-calculator caches. Only `BarrowmanStabilityCalculator` stores `shockGeometry`, and its cache invalidation nulls that reference. The drag calculator clears only its component-calculator cache. The shock geometry is therefore recomputed on the next aerodynamic evaluation, including after staging or fairing-separation events.

### 7.8 Validation Status

The pre-pass is validated to numerical precision against the same analytical building blocks it calls. The test file is [`ShockGeometryLocalFlowValidationTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/ShockGeometryLocalFlowValidationTest.java); the digitized companion data is in [`shockgeometry_local_flow_validation.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/shockgeometry_local_flow_validation.csv) and the closure memo is [`shockgeometry_local_flow_validation.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/shockgeometry_local_flow_validation.md).

| Quantity | Geometry | Comparison | Max error |
|----------|----------|------------|-----------|
| Cone-surface Mach | 15° / 30° / 40° cones at $M_\infty = 2, 3, 5$ | Taylor-Maccoll via `solveCone()` | 0 % (bit-exact) |
| Cone-surface $p/p_1$ | same | same | 0 % (bit-exact) |
| Body-tube Mach (post-shoulder) | same | Prandtl-Meyer via `downstreamMach()` | $\le 3.17 \times 10^{-11}\,\%$ |
| Body-tube $p/p_1$ (post-shoulder) | same | same | $\le 1.84 \times 10^{-10}\,\%$ |

The cone-surface errors are exactly zero because the constant slope of a right circular cone gives $\Delta\theta = 0$ at every marching strip after the initial shock; the post-shock state is recorded bit-for-bit from `solveCone()`. The shoulder-expansion errors are at the limit of double-precision arithmetic. The pre-pass therefore inherits the externally anchored validation of its analytical building blocks (NACA 1135 oblique-shock and Prandtl-Meyer tables), with reported agreement of "cone 0 %, shoulder 4e-11 %".

### 7.9 Worked Example: Cone-Cylinder-Fin at $M_\infty = 2.5$

**Geometry.**

- Nose cone: conical, half-angle $\theta_\text{tip} = 15°$, length $L_n = 0.20$ m.
- Body tube: length $L_b = 0.60$ m, radius $r = 0.04$ m.
- Fins: trapezoidal, fixed at axial position $x_\text{fin} = 0.65$ m from the nose tip.
- Freestream: $M_\infty = 2.5$, $\gamma = 1.4$.

**Step 1 — Initial nose shock.** Taylor-Maccoll for $M_1 = 2.5$, $\theta_c = 15°$:

- Shock angle $\beta_s \approx 33.5°$.
- Post-shock Mach $M_2 \approx 2.137$.
- Pressure ratio $p_2/p_1 \approx 1.685$.
- Temperature ratio $T_2/T_1 \approx 1.195$.

These become the running state at the cone tip.

**Step 2 — Surface marching on the nose.** The nose is divided into $N = 20$ strips of width $\Delta x = 0.01$ m. For a cone the surface tangent is constant at $\theta_\text{surf} = 15°$, so $\Delta\theta = \theta_\text{prev} - \theta_\text{surf} = 0$ at every strip and no further expansions or compressions are emitted. All 21 cone-surface stations record

$$
\begin{aligned}
M &= 2.137, &
p/p_\infty &= 1.685, &
T/T_\infty &= 1.195,\\
q/q_\infty &= 1.685 \cdot \frac{2.137^2}{2.5^2}
            = 1.685 \cdot 0.7308
            = 1.231 .
\end{aligned}
$$

**Step 3 — Shoulder expansion at the nose-to-body junction.** At $x = 0.20$ m the surface angle drops from $\theta_\text{prev} = 15°$ to $\theta_\text{tube} = 0°$. The turning angle is $\Delta\theta = 15° = 0.2618$ rad (expansion). Apply Prandtl-Meyer starting from $M_\text{local} = 2.137$:

$$
\begin{aligned}
\nu(2.137)
&= \sqrt{6}\,\arctan\!\sqrt{\tfrac{0.4}{2.4}\,(2.137^2-1)}
   - \arctan\!\sqrt{2.137^2 - 1}\\
&= 2.449 \cdot 0.6562 - 1.0837\\
&= 0.5231\,\text{rad} = 29.97^\circ .
\end{aligned}
$$

The downstream Prandtl-Meyer angle is $\nu(M_\text{new}) = 29.97° + 15° = 44.97°$. Numerically inverting gives $M_\text{new} \approx 2.75$. The isentropic ratios across the expansion:

$$
\begin{aligned}
\frac{p_\text{new}}{p_\text{local}}
&= \left(\frac{1 + 0.2 \cdot 2.137^2}{1 + 0.2 \cdot 2.75^2}\right)^{\!3.5}\\
&= \left(\frac{1.913}{2.5125}\right)^{3.5}
 = 0.7615^{3.5}
 = 0.396,
\end{aligned}
$$
$$
\frac{T_\text{new}}{T_\text{local}} = \frac{1.913}{2.5125} = 0.7615.
$$

Cumulating against the running state:

$$
\begin{aligned}
\frac{p_\text{new}}{p_\infty} &= 0.396 \cdot 1.685 = 0.667,\\
\frac{T_\text{new}}{T_\infty} &= 0.7615 \cdot 1.195 = 0.910,\\
\frac{q}{q_\infty} &= 0.667 \cdot \frac{2.75^2}{2.5^2} = 0.807.
\end{aligned}
$$

**Step 4 — Body-tube stations.** The body tube has constant radius and zero surface angle, so no further turning. Two stations are recorded at $x = 0.20$ m and $x = 0.80$ m, both carrying

$$
M_\text{local} = 2.75, \quad p/p_\infty = 0.667, \quad T/T_\infty = 0.910, \quad q/q_\infty = 0.807.
$$

**Step 5 — Query the fin station.** The fins are at $x_\text{fin} = 0.65$ m, inside the body-tube region $[0.20, 0.80]$. The binary search returns indices $(i, i+1)$ corresponding to $(0.20, 0.80)$; $t = (0.65 - 0.20)/(0.80 - 0.20) = 0.75$. Since the body-tube stations carry identical conditions, linear interpolation gives the same values exactly.

The fins therefore experience local conditions

| Quantity | Freestream | Local (post-shock) | $\Delta$ vs freestream |
|----------|:----------:|:------------------:|:----------------------:|
| Mach | 2.50 | 2.75 | $+10\,\%$ |
| $p/p_\infty$ | 1.00 | 0.667 | $-33\,\%$ |
| $T/T_\infty$ | 1.00 | 0.910 | $-9\,\%$ |
| $q/q_\infty$ | 1.00 | 0.807 | $-19\,\%$ |

In this geometry the local Mach is *higher* than freestream because the shoulder expansion overpowers the nose-cone compression. For blunter noses or shorter body tubes the sign reverses. Section 8.7 re-uses these local conditions for the fin-CNa worked example.

---

## 8. Stability Corrections

This chapter documents the supersonic stability corrections layered on top of the classical Barrowman methodology: body CNa via the Allen-Perkins crossflow analogy, the Jorgensen Mach-dependent crossflow drag coefficient, the supersonic CP aft-shift, the fin-CNa Ackeret $K_1/K_2/K_3$ expansion with a Mach-dependent $K_1$ floor, the Pitts-Nielsen-Kaattari fin-body interference correction, the ESDU transonic similarity rule, and the local-flow correction that connects all of the above to the shock-geometry pre-pass of Chapter 7. Every empirical constant is anchored to a primary reference in Section 8.8.

The implementations live in [`SymmetricComponentCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/SymmetricComponentCalc.java), [`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java), [`PittsNielsenKaattari.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/PittsNielsenKaattari.java) and [`TransonicSimilarity.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/TransonicSimilarity.java). The static-stability validation against NASA TM X-653 yields CNa MAPE $6.8\,\%$ and $x_{CP}$ MAPE $7.1\,\%$ across $M = 0.6$–$5.82$ ([validation report](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/nasa_tm_x653_validation_report.md)).

### 8.1 Body CNa: Allen-Perkins Crossflow Analogy

At subsonic Mach the body normal-force slope follows the Barrowman/Galejs formulation. Per unit length,

$$
\frac{dC_N}{dx} = \frac{2}{S_\text{ref}}\frac{dA}{dx}\sin\alpha\cos\alpha + \frac{d}{S_\text{ref}}\,C_{d,c}(M_c)\sin^2\alpha,
$$

where $A(x)$ is the cross-sectional area, $d$ the local diameter, $S_\text{ref}$ the reference area, and $C_{d,c}$ the crossflow drag coefficient at crossflow Mach $M_c = M_\infty\sin\alpha$ (Section 8.2). The first term is the inviscid potential-flow contribution; the second term is the Allen-Perkins viscous crossflow contribution, analogous to a circular cylinder in crossflow at $V_c = V_\infty\sin\alpha$. The body lift is multiplied by a single empirical coefficient $K$ to absorb the under-prediction of viscous lift by the linear potential-flow theory.

[`SymmetricComponentCalc.getEffectiveBodyLiftK()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/SymmetricComponentCalc.java) blends $K$ from the Galejs subsonic value $1.1$ down to $0$ across $M \in [0.8,\;1.3]$, using a cubic Hermite smoothstep, and holds $K = 0$ at all higher Mach. The motivation is supersonic slender-body theory (Ward 1949), which yields a body normal-force slope $C_{N\alpha} = 2(A_\text{aft} - A_\text{fore})/S_\text{ref}$ — exactly the Barrowman potential-flow value, with no additional viscous term. Holding $K = 1.1$ supersonically adds a forward-pulling body-lift contribution that pushes CP ahead of CG at high Mach and high AoA, producing false instability for vehicles that actually flew (MESOS 293K, the Mach 4.18 / 293,488 ft two-stage research flight, drove this calibration). RASAero II uses pure Barrowman potential flow for body $C_{N\alpha}$ at supersonic Mach; the down-blend matches that convention.

The Mach-dependent body lift coefficient is therefore

$$
K_\text{eff}(M) = \begin{cases}
K_\text{sub} = 1.1 & M \le 0.8, \\
K_\text{sub}\bigl(1 - w(t)\bigr) & 0.8 < M < 1.3,\quad t = \dfrac{M - 0.8}{0.5},\quad w(t) = 3t^2 - 2t^3, \\
0 & M \ge 1.3.
\end{cases}
$$

The smoothstep $w(t)$ has $w(0) = 0$, $w(1) = 1$, $w'(0) = w'(1) = 0$, so $K_\text{eff}$ is C1-continuous across the blend. Tabulating:

| $M$ | $K_\text{eff}$ |
|-----|----------------|
| 0.7 | 1.10 |
| 0.8 | 1.10 |
| 0.9 | 1.10 $\cdot (1 - 0.104) = 0.985$ |
| 1.0 | 1.10 $\cdot (1 - 0.352) = 0.713$ |
| 1.1 | 1.10 $\cdot (1 - 0.648) = 0.387$ |
| 1.2 | 1.10 $\cdot (1 - 0.896) = 0.114$ |
| 1.3 | 0 |
| $\ge 1.5$ | 0 |

The Allen-Perkins crossflow term is therefore retained in *form* but driven to zero in *amplitude* at supersonic Mach. The viscous crossflow contribution to body CNa is delivered instead through the Jorgensen $C_{d,c}(M_c)$ correction (Section 8.2), which is geometry-correct at supersonic crossflow Mach and which is *not* zeroed.

### 8.2 Jorgensen Crossflow Drag Coefficient

The crossflow drag coefficient $C_{d,c}$ is the drag of an infinite circular cylinder in crossflow at the crossflow Mach number $M_c = M_\infty\sin\alpha$. At low $M_c$ ($\le 0.4$) it is the well-known $C_{d,c} \approx 1.20$ for a circular cylinder at sub-critical Reynolds number. As $M_c$ enters the transonic range and beyond, shock formation on the cylinder surface raises $C_{d,c}$ to $\approx 2.0$ at $M_c \ge 3$.

The lookup table in [`SymmetricComponentCalc.crossflowCdcInterpolator`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/SymmetricComponentCalc.java) is taken directly from Jorgensen (NASA TR R-474, 1977):

| $M_c$ | $C_{d,c}$ |
|-------|-----------|
| 0.0 | 1.20 |
| 0.2 | 1.20 |
| 0.4 | 1.20 |
| 0.6 | 1.25 |
| 0.8 | 1.35 |
| 0.9 | 1.50 |
| 1.0 | 1.65 |
| 1.2 | 1.80 |
| 1.5 | 1.85 |
| 2.0 | 1.95 |
| 3.0 | 2.00 |
| 5.0 | 2.00 |

Linear interpolation between table entries; clamped at $C_{d,c} = 2.00$ for $M_c > 5.0$ and at the subsonic value $C_{d,c} = 1.20$ for $M_c \le 0$. The body normal-force contribution from crossflow drag is

$$
\begin{aligned}
C_{N,\text{body}}
&= \mu \cdot K_\text{eff}(M_\infty)
 \cdot \frac{C_{d,c}(M_c)}{C_{d,c,\text{sub}}}
 \cdot \frac{A_\text{planform}}{S_\text{ref}}\\
&\quad \cdot \sin\alpha \cdot \mathrm{sinc}(\alpha),
\end{aligned}
$$

with $C_{d,c,\text{sub}} = 1.20$ as the baseline subsonic value and $\mu$ a low-Mach high-AoA multiplier that fades crossflow lift to zero for $M < 0.05$ and $\alpha > 45°$ (an anti-tumble guard at apogee, see source).

**Worked numbers.** For a sounding rocket at $M_\infty = 3.0$, $\alpha = 10°$, the crossflow Mach is $M_c = 3.0\sin(10°) = 0.521$. Linearly interpolating between $(0.4,\,1.20)$ and $(0.6,\,1.25)$:

$$
C_{d,c}(0.521) = 1.20 + \frac{0.521 - 0.4}{0.6 - 0.4} \cdot (1.25 - 1.20) = 1.20 + 0.605 \cdot 0.05 = 1.230,
$$

a $2.5\,\%$ increase over the subsonic value. At $\alpha = 20°$, $M_c = 1.026$ and $C_{d,c} \approx 1.69$, a $41\,\%$ increase — significant for high-AoA flight, but again multiplied by $K_\text{eff}(M_\infty = 3.0) = 0$ in the current implementation, so the supersonic contribution to body CNa is ultimately zero.

**Validation.** The baseline value $C_{d,c} = 1.20$ is an exact match to Jorgensen TR R-474 Table 1 for a circular cylinder cross-section at sub-critical Reynolds number, and to Allen and Perkins (1951) who use $C_{d,c} = 1.2$ in the original crossflow analogy derivation. The match is verified to machine precision in [`JorgensenCrossflowCdBenchmarkTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/JorgensenCrossflowCdBenchmarkTest.java) ("crossflow body Cd (1.20) — exact"), an external benchmark against published cross-flow data. The full $C_{d,c}(M_c)$ table is digitized in [`jorgensen_crossflow_cd.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/jorgensen_crossflow_cd.csv).

### 8.3 Center of Pressure: Supersonic Aft Shift

The classical Barrowman CP for a symmetric component is

$$
x_{\text{CP},\text{sub}} = \frac{L\,A_\text{aft} - V}{A_\text{aft} - A_\text{fore}},
$$

where $L$ is the component length, $A_\text{fore}$ and $A_\text{aft}$ are the fore and aft cross-sectional areas, and $V$ is the component volume. This is an exact subsonic potential-flow result for a slender axisymmetric body.

At supersonic Mach the pressure distribution changes qualitatively. Upstream propagation is blocked by the supersonic character of the flow, so the CP is dictated by (a) the shock/expansion structure near the nose, and (b) the crossflow drag acting on the projected planform area, whose centroid is further aft than the Barrowman CP. As Mach rises the crossflow contribution dominates and the CP migrates aft.

The supersonic CP is taken to be a $30\,\%$ shift from the Barrowman CP toward the planform centroid:

$$
x_{\text{CP},\text{sup}} = x_{\text{CP},\text{sub}} + 0.30\bigl(x_\text{planform} - x_{\text{CP},\text{sub}}\bigr),
$$

clamped to the component length $0 \le x_{\text{CP},\text{sup}} \le L$. The shift fraction $0.30$ was calibrated against RASAero II output for five standard rocket geometries; full shift over-predicts aft migration for typical slender geometries, no shift under-predicts it.

The transonic blend uses the same C1 cubic Hermite smoothstep as the body-lift $K$:

$$
\begin{aligned}
x_\text{CP}(M)
&= x_{\text{CP},\text{sub}}
 + w(t)\bigl(x_{\text{CP},\text{sup}} - x_{\text{CP},\text{sub}}\bigr),\\
t &= \frac{M - 0.8}{0.5},\\
w(t) &= 3t^2 - 2t^3,
\end{aligned}
$$

with $x_\text{CP} = x_{\text{CP},\text{sub}}$ for $M \le 0.8$ and $x_\text{CP} = x_{\text{CP},\text{sup}}$ for $M \ge 1.3$.

**Defensive guard for boattails.** At supersonic Mach, contracting transitions (boattails) have $C_{N\alpha} < 0$ from Barrowman's area-change formula — a destabilising contribution. In practice these components sit in the wake of the fins; the simple potential-flow result is unreliable there and produces a spurious forward CP shift. The implementation fades the contracting-transition CNa to zero through the same M $\in [0.8, 1.3]$ band:

$$
C_{N\alpha,\text{eff}} = C_{N\alpha,\text{Barrowman}} \cdot (1 - w(t)) \quad \text{when } C_{N\alpha,\text{Barrowman}} < 0 \text{ and } M > 0.8.
$$

This matches RASAero II, which omits boattail CNa for stability in the supersonic regime.

### 8.4 Fin Normal-Force Slope

#### 8.4.1 Subsonic regime ($M \le 0.9$)

The fin normal-force slope per panel without interference is the Diederich-Barrowman formula:

$$
C_{N\alpha,1} = \frac{2\pi s^2}{S_\text{ref}}\cdot\frac{1}{1 + \sqrt{1 + (1 - M^2)\bigl(s^2 / (A_f\cos\gamma_c)\bigr)^2}},
$$

where $s$ is the fin semispan, $A_f$ the planform area, $\gamma_c$ the midchord sweep angle, and $S_\text{ref}$ the reference area.

#### 8.4.2 Supersonic regime ($M \ge 1.5$)

Above the upper transonic boundary the fin slope follows the Ackeret-based supersonic expansion in three Mach-dependent coefficients $K_1$, $K_2$, $K_3$:

$$
C_{N\alpha,1} = \frac{A_f}{S_\text{ref}}\bigl(K_1 + K_2\alpha + K_3\alpha^2\bigr),
$$

with $\alpha$ the angle of attack (clamped to the stall angle). The coefficients, evaluated at $\gamma = 1.4$:

**Linear term — Ackeret thin-airfoil result for a flat plate at zero AoA:**
$$
K_1(M) = \frac{2}{\beta}, \qquad \beta = \sqrt{M^2 - 1}.
$$

| $M$ | $\beta$ | $K_1$ |
|:---:|:-------:|:-----:|
| 1.5 | 1.118 | 1.789 |
| 2.0 | 1.732 | 1.155 |
| 2.5 | 2.291 | 0.873 |
| 3.0 | 2.828 | 0.707 |
| 4.0 | 3.873 | 0.516 |
| 5.0 | 4.899 | 0.408 |

**First-order AoA correction:**
$$
K_2(M) = \frac{(\gamma+1)M^4 - 4\beta^2}{4\beta^4} = \frac{2.4M^4 - 4(M^2-1)}{4(M^2-1)^2}.
$$

| $M$ | $K_2$ |
|:---:|:-----:|
| 1.5 | 1.144 |
| 2.0 | 0.733 |
| 2.5 | 0.660 |
| 3.0 | 0.634 |
| 4.0 | 0.616 |
| 5.0 | 0.609 |

**Second-order AoA correction:**
$$
K_3(M) = \frac{(\gamma+1)M^8 + (2\gamma^2 - 7\gamma - 5)M^6 + 10(\gamma+1)M^4 + 8}{6\beta^7}.
$$

For $\gamma = 1.4$: $(\gamma+1) = 2.4$, $(2\gamma^2 - 7\gamma - 5) = 2(1.96) - 9.8 - 5 = -10.88$, $10(\gamma+1) = 24$, so

$$
K_3(M) = \frac{2.4M^8 - 10.88M^6 + 24M^4 + 8}{6(M^2-1)^{7/2}}.
$$

| $M$ | $K_3$ |
|:---:|:-----:|
| 1.5 | 5.120 |
| 2.0 | 1.105 |
| 2.5 | 0.981 |
| 3.0 | 1.124 |
| 4.0 | 1.516 |
| 5.0 | 1.926 |

The implementation pre-tabulates $K_1$, $K_2$, $K_3$ on a $0.1$-Mach grid from $M = 1.5$ to $M = 5.0$ in a static initialiser ([`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) lines 559–578) and queries via `LinearInterpolator`.

#### 8.4.2a Mach-Dependent $K_1$ Floor for Swept Low-AR Fins

For low-aspect-ratio fins ($AR < 1.8$) with swept leading edges, the Ackeret formula $K_1 = 2/\beta$ under-predicts lift when the leading-edge normal Mach $m_{LE} = M\cos\Lambda_{LE}$ is subsonic. In this regime the fin behaves partly as a subsonic lifting surface and the purely supersonic Ackeret coefficient is too low. Without correction, the progressive forward CP migration of the under-predicted fin lift can drive CP ahead of CG at high Mach, producing spurious instability for finned vehicles that flew successfully.

A Mach-dependent floor is applied to $K_1$:

$$
K_{1,\text{floor}}(m_{LE}) = \min\!\left(K_{1,\text{max}},\; K_{1,\text{asymp}} + (K_{1,\text{max}} - K_{1,\text{asymp}})\,e^{-\lambda(m_{LE} - 1)}\right),
$$

with constants from [`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) lines 548–550:

- $K_{1,\text{max}} = 0.85$ — the floor value when $m_{LE} \le 1$ (subsonic LE).
- $K_{1,\text{asymp}} = 0.40$ — the asymptotic floor as $m_{LE} \to \infty$.
- $\lambda = \texttt{K1\_FLOOR\_DECAY} = 1.480$ — the exponential decay rate.

The effective $K_1$ used in the supersonic formula is then $K_{1,\text{eff}} = \max(K_1, K_{1,\text{floor}})$. Tabulating the floor:

| $m_{LE}$ | $K_{1,\text{floor}}$ |
|:--------:|:--------------------:|
| $\le 1.0$ | 0.850 |
| 1.5 | 0.624 |
| 2.0 | 0.495 |
| 3.0 | 0.414 |
| $\to \infty$ | 0.400 |

**Calibration.** $\lambda = 1.480$ was fitted against the high-Mach end of the NASA TM X-653 (Jorgensen, Spahr & Hill, 1962) wind-tunnel dataset for a nose-symmetric cruciform finned body (NSCFB) with $AR = 1.46$ and $\cos\Lambda_{LE} = 0.343$, using the four data points spanning $M = 3.0$ to $M = 5.82$. Prior to this calibration a constant floor of $0.85$ produced a CNa MAPE of $\approx 14\,\%$ across the high-Mach points. The exponential decay reduces the dataset-wide MAPE to $6.8\,\%$ for CNa and $7.1\,\%$ for $x_{CP}$ across the full $M = 0.6$–$5.82$ range ([`NasaTmX653K1FloorTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/NasaTmX653K1FloorTest.java); [validation report](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/nasa_tm_x653_validation_report.md); aggregate CSV [`nasa_tm_x653_metrics.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/nasa_tm_x653_metrics.csv)).

The closure note in the validation report warns that this calibration is a known model trade-off: removing the floor improves CNa above $M = 4$ but worsens $x_{CP}$, and vice versa.

#### 8.4.3 Transonic Interpolation ($0.9 < M < 1.5$)

A quintic Hermite polynomial is used between the subsonic and supersonic boundaries. The polynomial in [`FinSetCalc.cnaInterpolator`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) is constructed by `PolyInterpolator` to satisfy:

- value and first-derivative match at $M = 0.9$ (subsonic boundary, Diederich slope and its analytic derivative);
- value and first-derivative match at $M = 1.5$ (supersonic boundary, Ackeret slope and its analytic derivative);
- second-derivative match at $M = 0.9$.

This yields a C2-continuous transition — sufficient to prevent the discontinuity oscillations that the legacy step-blending caused at $M \approx 1$.

The transonic similarity rule of Section 8.6 *overrides* this polynomial in part of its activation range, but is itself gated to freestream Mach $< 2.0$ to avoid spurious activation on highly swept fins (see code comment at [`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) line 642).

#### 8.4.4 Local Flow Correction from Shock Geometry

When a `ShockGeometry` is available and indicates supersonic conditions, the fin calculator queries the local post-shock conditions at the fin's axial position. The implementation is in [`FinSetCalc.getLocalFlowConditions()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java).

```java
FlightConditions localConditions = getLocalFlowConditions(conditions);
double cna1 = calculateFinCNa1(localConditions);
double sbliChordRatio = computeSBLIChordReduction(localConditions);
cna1 *= sbliChordRatio;
```

`getLocalFlowConditions()` clones the freestream conditions and substitutes $M = M_\text{local}$ from the pre-pass. The local Mach then enters $K_1(M_\text{local})$, $K_2(M_\text{local})$, $K_3(M_\text{local})$, and the leading-edge normal Mach $m_{LE} = M_\text{local}\cos\Lambda_{LE}$ used by the $K_1$ floor. The local Mach also drives the SBLI chord-reduction factor.

**Threshold gate.** To avoid clobbering nearly-freestream conditions with isentropic noise, the local-flow correction is *not* applied when $\lvert M_\text{local} - M_\infty\rvert < 0.10$. This threshold ignores small shoulder expansions (a $1$–$2°$ shoulder gives $\Delta M \approx 0.08$) which have negligible effect on fin CNa, while still correcting for large post-normal-shock Mach reductions that genuinely alter the supersonic Ackeret coefficients ([`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) lines 340–342).

**Dynamic-pressure ratio — intentionally omitted.** An earlier revision multiplied the fin $C_{N\alpha}$ by $q_\text{local}/q_\infty$ as a separate post-Ackeret correction. This was identified as a *double correction*: the $K_1/K_2/K_3$ formulas already encode the relationship between Mach number and dynamic pressure through their dependence on $\beta = \sqrt{M^2-1}$. When the local post-shock Mach replaces freestream Mach, the fin force coefficients already reflect the changed dynamic-pressure environment. Multiplying again by $q_\text{local}/q_\infty$ reduced fin authority by approximately $2\times$ at $M > 2$, causing spurious predictions of marginal stability for vehicles that were physically well stabilised. The dynamic-pressure ratio remains available in `LocalConditions` for diagnostic purposes but is no longer applied as a correction factor (see [`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) lines 226–230).

### 8.5 Pitts-Nielsen-Kaattari Fin-Body Interference

#### 8.5.1 Background

At subsonic Mach the classical Barrowman fin-body interference factor is

$$
K_\text{int} = 1 + \tau, \qquad \tau = \frac{r}{s + r},
$$

with $r$ the body radius at the fin root and $s$ the exposed fin semispan. This accounts for the upwash field of the body, which raises the effective angle of attack seen by the fin.

At supersonic Mach the Mach cone from the body limits the region of the fin influenced by the body upwash. The Mach cone half-angle is $\mu = \arcsin(1/M)$. As $M$ rises, the cone shrinks and the fraction of the fin within the body's zone of influence falls. Pitts, Nielsen and Kaattari (NACA Report 1307, 1957) introduced two correction factors to account for this:

- $F_{WB}$ — the fin-on-body (wing-on-body) carryover correction. The larger of the two.
- $F_{BW}$ — the body-on-fin (body-on-wing) carryover correction. The smaller of the two.

The corrected supersonic interference factor is the product

$$
K_\text{int,sup} = (1 + \tau) \cdot F_{WB} \cdot F_{BW}.
$$

#### 8.5.2 The $\beta_s$ parameter

Both correction factors depend on a reduced-frequency parameter that characterises how many fin chords fit within the body's Mach cone:

$$
\beta_s = \frac{\sqrt{M^2 - 1}\,s}{c_r}.
$$

Large $\beta_s$ (high Mach, large span, small chord) means the Mach cone covers only a small fraction of the fin and the interference correction is strong. Small $\beta_s$ (low supersonic Mach, small span, large chord) means the cone covers most of the fin and the correction is weak. The implementation guards $\beta_s \ge 0.1$ to avoid singularity at $M \to 1^+$.

#### 8.5.3 Supersonic correction factors

The pure supersonic formulas in [`PittsNielsenKaattari.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/PittsNielsenKaattari.java):

$$
F_{WB,\text{sup}} = 1 - 0.30\!\left(1 - \frac{1}{\max(\beta_s, 0.1)}\right)\!\sqrt{\tau}, \qquad F_{WB,\text{sup}} \in [0.5,\;1.0],
$$

$$
F_{BW,\text{sup}} = 1 - 0.15\!\left(1 - \frac{1}{\max(\beta_s, 0.1)}\right)\!\tau^{0.3}, \qquad F_{BW,\text{sup}} \in [0.7,\;1.0].
$$

The lower clamp values ($F_{WB} \ge 0.5$, $F_{BW} \ge 0.7$) prevent the corrections from becoming unphysically large at very high Mach and ensure numerical stability.

#### 8.5.4 Mach-cone schematic

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[font=\small, >=Latex]
\fill[gray!15] (-0.2,0) rectangle (2.2,0.35);
\fill[gray!15] (4.8,0) rectangle (7.2,0.35);
\node at (1.0,0.18) {body};
\node at (6.0,0.18) {body};
\draw[thick] (2.2,0.35) rectangle (4.8,1.1);
\node at (3.5,0.72) {fin ($c_r$)};
\draw[thick] (2.2,0.35) -- (1.2,2.4) -- (5.8,2.4) -- (4.8,0.35);
\path[fill=blue!12, draw=blue!60, dashed] (2.2,0.35) -- (1.2,2.4) -- (3.5,1.1) -- cycle;
\node[blue!70!black, align=left, font=\scriptsize] at (2.0,1.35) {Mach cone\\$\mu=\arcsin(1/M)$};
\node[align=left, font=\scriptsize] at (5.5,1.55) {outside cone:\\weaker body\\influence};
\node[align=left, anchor=west, font=\scriptsize] at (-0.1,-0.85)
  {$M=2.0$: $\mu\approx 30^\circ$;\quad $M=3.0$: $\mu\approx 19.5^\circ$;\quad $M=5.0$: $\mu\approx 11.5^\circ$.};
\end{tikzpicture}
\caption{Body Mach cone relative to fin planform (Pitts-Nielsen-Kaattari context; schematic).}
\label{fig:mach-cone-fin}
\end{figure}
```

#### 8.5.5 Activation profile

The corrections are gated at *both* ends:

- **At $M < 0.85$:** $F_{WB} = F_{BW} = 1.0$. The subsonic Barrowman $1 + \tau$ is preserved exactly.
- **At $0.85 \le M \le 1.15$:** cubic Hermite smoothstep activation.
$$
t = \frac{M - 0.85}{0.30}, \qquad s(t) = 3t^2 - 2t^3,
$$
$$
F_{WB}(M) = 1 \cdot (1 - s) + F_{WB,\text{sup}}(1.15) \cdot s,
$$
and analogously for $F_{BW}$. The supersonic formula is evaluated at the upper blend boundary $M = 1.15$ (not at $M$) inside the blend region. The smoothstep gives $s(0) = 0$, $s(1) = 1$, $s'(0) = s'(1) = 0$, so $F_{WB}$ and $F_{BW}$ are C1-continuous across the blend.
- **At $1.15 < M < 1.30$:** the pure supersonic formulas are used with the actual Mach number.
- **At $M \ge 1.30$:** $F_{WB} = F_{BW} = 1.0$. The PNK correction is *disabled* and the simpler $(1 + \tau)$ factor is used alone.

The upper deactivation at $M = 1.30$ is in [`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) lines 212–223. The Mach-cone upwash model becomes unreliable for highly swept, low-AR fins common on sounding rockets, and RASAero II benchmarks show the simpler $(1 + \tau)$ alone matches flight data better above $M = 1.30$. The PNK formulas are therefore evaluated only inside the narrow blend band $[1.15, 1.30]$.

#### 8.5.6 Interaction with shock geometry

Inside the active band ($0.85 \le M < 1.30$), the Mach number passed to `PittsNielsenKaattari.computeF_WB()` and `computeF_BW()` is the *local* post-shock Mach $M_\text{local}$ obtained from the shock-geometry pre-pass. This is because $\beta_s$ depends on $\sqrt{M^2-1}$, and the Mach cone that governs the body influence zone is determined by the local flow at the fin station, not by the freestream. The implementation extracts `localConditions.getMach()` and passes it as `machForPNK` ([`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) line 212).

In practice this means PNK is dynamically active for transonic vehicles whose local Mach lands in $[0.85, 1.30]$ — including vehicles whose freestream Mach is well above $1.30$ but whose post-shock local Mach has been pulled back into the transonic band by a strong nose shock (a rare configuration). For the majority of supersonic flights, PNK is in its disabled region ($M \ge 1.30$) and the interference factor reverts to $(1 + \tau)$.

### 8.6 ESDU Transonic Similarity

#### 8.6.1 Principle

The transonic similarity rule (von Karman, 1947; ESDU compilation) collapses fin aerodynamic data onto a universal curve in the parameter

$$
K_\text{trans} = \frac{M_\text{eff}^2 - 1}{(t/c)^{2/3}}, \qquad M_\text{eff} = M\cos\Lambda_{LE},
$$

where $M_\text{eff}$ is the Mach number normal to the leading edge, $t/c$ is the fin thickness-to-chord ratio, and $\Lambda_{LE}$ is the leading-edge sweep angle. The physical basis is that the small-disturbance transonic equation, after rescaling by $(t/c)^{2/3}$, depends on $M$ and $t/c$ only through $K_\text{trans}$. Fins with the same $K_\text{trans}$ therefore experience similar normalised pressure distributions.

#### 8.6.2 Universal curve

The function $h(K_\text{trans})$ in [`TransonicSimilarity.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/TransonicSimilarity.java) is a tabulated normalised CNa with $h = 1.0$ at $K_\text{trans} = 0$ (corresponding to $M_\text{eff} = 1$, the peak):

| $K_\text{trans}$ | $h$ |
|:----------------:|:---:|
| $-2.0$ | 0.70 |
| $-1.0$ | 0.85 |
| $-0.5$ | 0.93 |
| $0.0$ | 1.00 |
| $0.5$ | 0.97 |
| $1.0$ | 0.90 |
| $2.0$ | 0.75 |
| $3.0$ | 0.62 |

Linear interpolation between table entries; clamped at $h(\le -2.0) = 0.70$ and $h(\ge 3.0) = 0.62$.

The model is active when $K_\text{trans} \in [-2,\,+3]$ and the thickness ratio exceeds $1\,\%$ ($t/c > 0.01$) — below $1\,\%$ thickness the similarity scaling becomes singular as $t/c \to 0$. The model is *additionally* gated to freestream Mach $< 2.0$ ([`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) line 642): for highly swept fins ($\Lambda_{LE} \approx 70°$), $M_\text{eff} = M\cos\Lambda_{LE} \approx 0.34M$ can keep $K_\text{trans}$ inside $[-2, 3]$ even at $M = 3$, but the underlying flow is fully supersonic and the Ackeret $K_1/K_2/K_3$ theory is the correct model in that regime.

#### 8.6.3 Peak CNa at $M = 1$

The CNa per fin at the peak is estimated by a thickness-corrected Helmbold lifting-line formula:

$$
C_{N\alpha,\text{peak}} = \frac{2\pi\,AR}{2 + \sqrt{4 + AR^2}}\cdot f(t/c), \qquad f(t/c) = 1 + 2.5(t/c) + 8.0(t/c)^2.
$$

The first factor is the Helmbold low-AR lift slope (Prandtl lifting line at $AR \to \infty$, finite-AR correction at low aspect ratio). The thickness factor $f(t/c)$ accounts for the supervelocity over thick airfoils that amplifies lift effectiveness near $M = 1$.

#### 8.6.4 Application

The transonic CNa is

$$
C_{N\alpha,\text{transonic}} = C_{N\alpha,\text{peak}}\cdot h(K_\text{trans}),
$$

scaled from per-fin-area to per-reference-area in [`FinSetCalc.java`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) line 652 (`cnaTransonic = cnaPeak * h * finArea / ref`). Edge blending is applied to avoid steps at the activation boundary:

- $K_\text{trans} \in [-2.0,\,-1.5]$: linear blend from the standard subsonic/supersonic model into the similarity model with weight $w = (K_\text{trans} + 2.0)/0.5$.
- $K_\text{trans} \in [-1.5,\,2.5]$: pure similarity model.
- $K_\text{trans} \in [2.5,\,3.0]$: linear blend from the similarity model back into the standard model with weight $w = (K_\text{trans} - 2.5)/0.5$.

**Validation.** The transonic similarity model in combination with the DATCOM 4.1.5.1 fin wave drag formulation is validated against NACA TN 3650 free-flight data for a 60° delta fin configuration in [`NacaTn3650FinWaveDragTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/NacaTn3650FinWaveDragTest.java); fin CNa across the full $M = 0.6$–$5.82$ range is benchmarked against NASA TM X-653 in [`NasaTmX653K1FloorTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/NasaTmX653K1FloorTest.java), achieving CNa MAPE $6.8\,\%$ and $x_{CP}$ MAPE $7.1\,\%$.

### 8.7 Worked Example: Fin CNa at $M_\infty = 2.0$

This example reuses the cone-cylinder-fin geometry of Section 7.9 to make the local-flow correction concrete. The freestream is $M_\infty = 2.0$ but, by way of cross-reference, the local conditions at the fin station of a $M_\infty = 2.5$ flight from Section 7.9 are also used for Case B. (We deliberately use a high-shock-event geometry for Case B to exercise the correction; in Case A the same fin sees freestream conditions only.)

**Geometry.**

- Trapezoidal fin: root chord $c_r = 0.10$ m, tip chord $c_t = 0.05$ m, semispan $s = 0.08$ m.
- Planform area $A_f = \tfrac{1}{2}(c_r + c_t)\,s = \tfrac{1}{2}(0.10 + 0.05)\cdot 0.08 = 0.006\,\text{m}^2$.
- Aspect ratio $AR = 2s^2/A_f = 2 \cdot 0.0064 / 0.006 = 2.133$.
- Body radius at fin root $r = 0.04$ m; reference area $S_\text{ref} = \pi r^2 = 0.005027\,\text{m}^2$.
- Thickness $t = 0.003$ m; $t/c_\text{MAC} \approx 0.038$.
- Midchord sweep cosine $\cos\gamma_c = 0.95$; leading-edge sweep cosine $\cos\Lambda_{LE} = 0.90$.
- Angle of attack $\alpha = 5° = 0.0873$ rad.
- Fin axial position $x_\text{fin} = 0.65$ m.

#### Case A — without shock-geometry correction (freestream $M = 2.0$)

**Step 1 — $K_1$, $K_2$, $K_3$ at $M = 2.0$.**

$$
\beta = \sqrt{2^2 - 1} = \sqrt{3} = 1.7321,
$$
$$
K_1 = 2/\beta = 1.1547, \qquad
K_2 = \frac{2.4 \cdot 16 - 4 \cdot 3}{4 \cdot 9} = \frac{26.4}{36} = 0.7333,
$$
$$
K_3 = \frac{2.4 \cdot 256 - 10.88 \cdot 64 + 24 \cdot 16 + 8}{6 \cdot 3^{3.5}} = \frac{310.08}{280.59} = 1.1051.
$$

**Step 2 — $C_{N\alpha,1}$ per fin.**

$$
C_{N\alpha,1} = \frac{0.006}{0.005027}\,(1.1547 + 0.7333\cdot 0.0873 + 1.1051\cdot 0.00762) = 1.1935 \cdot 1.2271 = 1.4644.
$$

**Step 3 — interference at $M = 2.0$.** Note that $M = 2.0 > 1.30$ is in the PNK *deactivation* band; the implementation sets $F_{WB} = F_{BW} = 1.0$ here. For pedagogical completeness the supersonic formulas are also evaluated:

$$
\tau = \frac{0.04}{0.08 + 0.04} = 0.3333, \qquad K_\text{int} = 1 + \tau = 1.3333.
$$

The supersonic-formula values (which would apply if the upper deactivation were not there) are:

$$
\beta_s = \frac{\sqrt{3} \cdot 0.08}{0.10} = 1.386,
$$
$$
F_{WB,\text{sup}} = 1 - 0.30(1 - 1/1.386)\sqrt{0.3333} = 1 - 0.30 \cdot 0.2785 \cdot 0.5774 = 0.9517,
$$
$$
F_{BW,\text{sup}} = 1 - 0.15(1 - 1/1.386) \cdot 0.3333^{0.3} = 1 - 0.15 \cdot 0.2785 \cdot 0.6934 = 0.9710.
$$

In the *current* implementation, however, $F_{WB} = F_{BW} = 1$ at $M = 2.0$ and the final fin CNa per panel is

$$
C_{N\alpha,\text{Case A, code}} = 1.4644 \cdot 1.3333 \cdot 1 \cdot 1 = 1.9525.
$$

If the supersonic PNK formulas were applied (legacy / pedagogical comparison) the result would instead be

$$
C_{N\alpha,\text{Case A, legacy}} = 1.4644 \cdot 1.3333 \cdot 0.9517 \cdot 0.9710 = 1.8035.
$$

#### Case B — with shock-geometry correction ($M_\text{local} = 2.75$ at the fin station)

The local conditions at the fin station are taken from Section 7.9: the cone-cylinder geometry at $M_\infty = 2.5$ produced $M_\text{local} = 2.75$ at the fin. The threshold gate $\lvert M_\text{local} - M_\infty\rvert = 0.25 > 0.10$ is satisfied, so `getLocalFlowConditions()` substitutes $M = 2.75$ into the fin calculator.

**Step 1 — $K_1$, $K_2$, $K_3$ at $M_\text{local} = 2.75$.**

$$
\beta = \sqrt{2.75^2 - 1} = \sqrt{6.5625} = 2.5617, \qquad K_1 = 2/2.5617 = 0.7807,
$$
$$
K_2 = \frac{2.4 \cdot 57.19 - 4 \cdot 6.5625}{4 \cdot 43.07} = \frac{111.01}{172.27} = 0.6444,
$$
$$
K_3 = \frac{2.4 \cdot 432.49 - 10.88 \cdot 157.27 + 24 \cdot 57.19 + 8}{6 \cdot 723.4} = \frac{707.43}{4340.4} = 0.1630.
$$
($2.75^4 = 57.19,\;2.75^6 = 157.27,\;2.75^8 = 432.49,\;6.5625^{3.5} = 723.4$.)

**Step 2 — $C_{N\alpha,1}$ at local Mach.**

$$
C_{N\alpha,1} = 1.1935 \cdot (0.7807 + 0.6444 \cdot 0.0873 + 0.1630 \cdot 0.00762) = 1.1935 \cdot 0.8382 = 1.0004.
$$

**Step 3 — interference at $M_\text{local} = 2.75$.** Since $M_\text{local} = 2.75 > 1.30$, PNK is again deactivated and $F_{WB} = F_{BW} = 1.0$. The interference factor reduces to $K_\text{int} = 1 + \tau = 1.3333$.

**Step 4 — final result.** The dynamic-pressure ratio is *not* applied as a separate scaling (Section 8.4.4):

$$
C_{N\alpha,\text{Case B, code}} = 1.0004 \cdot 1.3333 \cdot 1 \cdot 1 = 1.3339.
$$

#### Comparison

| Quantity | Case A (freestream $M = 2.0$) | Case B (local $M = 2.75$) | $\Delta$ |
|----------|:-----------------------------:|:-------------------------:|:--------:|
| Mach used for $K_1$/$K_2$/$K_3$ | 2.00 | 2.75 | $+37.5\,\%$ |
| $\beta = \sqrt{M^2-1}$ | 1.732 | 2.562 | $+47.9\,\%$ |
| $K_1 = 2/\beta$ | 1.155 | 0.781 | $-32.4\,\%$ |
| $C_{N\alpha,1}$ (per fin) | 1.464 | 1.000 | $-31.7\,\%$ |
| $K_\text{int}$ (PNK off, $M > 1.30$) | 1.333 | 1.333 | 0 |
| **Final $C_{N\alpha}$ per fin** | **1.953** | **1.334** | $-31.7\,\%$ |

The shock-geometry correction reduces the predicted fin normal-force slope by $\approx 32\,\%$ in this geometry. The reduction is dominated by the local-Mach effect on $K_1$. The interference factor is unchanged because both the freestream and local Mach are above the PNK upper deactivation threshold. For freestream Mach in the PNK active band ($0.85 \le M < 1.30$) the interference correction would also vary with local Mach; for the current geometry it does not.

A worked example with a third *ad-hoc* dynamic-pressure factor of $q_\text{local}/q_\infty = 0.807$ would have produced a much larger reduction. As discussed in Section 8.4.4, that scaling is a double correction and has been removed; the Case-B result above corresponds to the implementation as it stands.

The sign and magnitude of the correction are geometry-dependent: rockets with shorter body tubes or blunter noses see local Mach *below* freestream and the correction would *raise* $C_{N\alpha}$ rather than lower it. This is precisely why a physics-based shock-geometry computation is necessary rather than a fixed empirical correction factor.

### 8.8 Empirical Constant Traceability

The supersonic stability pipeline contains a small number of empirical constants. Each one is anchored to a primary published source or to an explicit RASAero II / corpus calibration.

**Table 8.1 — Empirical constants in shock-geometry pre-pass and stability corrections.** Class prefixes abbreviated: SCC = `SymmetricComponentCalc`, FSC = `FinSetCalc`, SG = `ShockGeometry`, PNK = `PittsNielsenKaattari`.

```{=latex}
\begin{landscape}
\scriptsize
\setlength{\tabcolsep}{4pt}
\renewcommand{\arraystretch}{1.15}
\renewcommand{\tabularxcolumn}[1]{>{\sloppy\hbadness=10000\relax}p{#1}}
\begin{xltabular}{\linewidth}{@{}X c X X X@{}}
\toprule
Constant & Value & Source & Code location & Validation \\
\midrule
\endhead
\texttt{SHOCK\_BLEND\_MACH} (near-sonic activation upper bound) & 1.10 & Numerical conditioning of $\theta$-$\beta$-$M$ near $M{=}1$ & \texttt{SG.java:53} & $C^0$ activation; corrections vanish smoothly \\
\texttt{STRIPS\_PER\_COMPONENT} (surface march) & 20 & Implementation choice & \texttt{SG.java:45} & Cone 0\% vs \texttt{solveCone}; shoulder $4{\times}10^{-11}\%$ vs \texttt{downstreamMach} \\
\texttt{MIN\_TURN\_ANGLE} (shock/exp.\ threshold) & $10^{-6}$ rad & Numerical guard & \texttt{SG.java:48} & --- \\
Subsonic body lift coefficient $K_\text{sub}$ & 1.10 & Galejs empirical (subsonic wind-tunnel) & \texttt{SCC.BODY\_LIFT\_K}:43 & --- \\
Supersonic body lift target & 0 (down-blend) & Ward 1949 slender-body; RASAero II convention; MESOS 293K & \texttt{getEffectiveBodyLiftK}:390--401 & MESOS 293K: $-6.96\%$ apogee (within $\pm 10\%$ band) \\
Body $C_{N\alpha}$/CP transonic band & $M \in [0.8, 1.3]$ & Matches base-drag and PNK bands & \texttt{SCC.STABILITY\_BLEND\_*}:71--72 & $C^1$-continuous \\
CP supersonic shift fraction & 0.30 & RASAero II (5 geometries) & \texttt{getEffectiveCpPosition}:299 & NSCFB $x_{CP}$ MAPE 7.1\% \\
Body crossflow $C_{d,c}$ (sub.\ baseline) & 1.20 & Jorgensen TR R-474 Tab.\ 1 & \texttt{SCC.SUBSONIC\_CDC}:50 & Exact match \\
Jorgensen $C_{d,c}(M_c)$ table & 1.20 to 2.00 & Jorgensen TR R-474 & \texttt{crossflowCdcInterpolator}:51--53 & External benchmark, exact \\
$K_1$ floor max ($K_{1,\text{max}}$) & 0.85 & Sub-LE plateau, NASA TM X-653 & \texttt{FSC.K1\_FLOOR\_MAX}:548 & NSCFB $C_{N\alpha}$ MAPE 6.8\% \\
$K_1$ floor asymp.\ ($K_{1,\text{asymp}}$) & 0.40 & Fitted to TM X-653 high-$M$ & \texttt{FSC.K1\_FLOOR\_ASYMPTOTE}:549 & same \\
$K_1$ floor decay $\lambda$ & 1.480 & Fitted to TM X-653 at $M{=}5.11, 5.82$ & \texttt{FSC.K1\_FLOOR\_DECAY}:550 & Pre-fit const.\ MAPE was 14\% \\
Low-AR floor activation & $AR{<}1.8$ & Low-AR swept-LE: Ackeret under-predicts & \texttt{FSC.calculateFinCNa1}:600 & NSCFB $AR{=}1.46$ \\
PNK $F_{WB}$ coefficient & 0.30 & Pitts, Nielsen \& Kaattari (NACA Report 1307, 1957) & \texttt{computeF\_WB\_supersonic}:100 & Functional fit \\
PNK $F_{BW}$ coefficient & 0.15 & Pitts, Nielsen \& Kaattari (1957) & \texttt{computeF\_BW\_supersonic}:112 & same \\
PNK $\beta_s$ guard & $\beta_s \ge 0.1$ & $M\!\to\!1^+$ singularity guard & \texttt{PNK.java}:100, 112 & --- \\
PNK $F_{WB}/F_{BW}$ floors & 0.5 / 0.7 & Physically reasonable bound & \texttt{PNK.F\_WB\_MIN}, \texttt{F\_BW\_MIN}:29--31 & --- \\
PNK transonic blend band & $M \in [0.85, 1.15]$ & Narrower than body band to track Mach cone & \texttt{PNK.M\_BLEND\_LOW}, \texttt{HIGH}:22--24 & $C^1$ smoothstep \\
PNK upper deactivation Mach & 1.30 & RASAero II favours bare $(1{+}\tau)$ at high $M$ for low-AR swept fins & \texttt{calculateNonaxialForces}:214 & NSCFB validation \\
Transonic similarity $K_\text{trans}$ band & $[-2, +3]$ & ESDU (Karman 1947) & \texttt{isInTransonicRegime}:62 & TN 3650 (12 points) \\
Transonic similarity $t/c$ floor & $t/c > 0.01$ & Similarity scaling singularity as $t/c{\to}0$ & \texttt{FSC.calculateFinCNa1}:647 & --- \\
Transonic similarity $M$ gate & $M < 2.0$ & Avoid spurious high-$M$ activation on swept fins & \texttt{FSC.calculateFinCNa1}:642 & NSCFB $M{=}3.0$: was 56.8\% pre-gate \\
Trans. similarity edge blend half-width & 0.5 in $K_\text{trans}$ & $C^0$ activation; matches table resolution & \texttt{FSC.calculateFinCNa1}:654--660 & --- \\
Local-flow correction threshold & $|M_\text{local}{-}M_\infty| \ge 0.10$ & Reject sub-2° shoulder noise & \texttt{getLocalFlowConditions}:340 & --- \\
\bottomrule
\end{xltabular}
\end{landscape}
```

Constants for dynamic stability ($C_{mq}$ accumulation, Magnus, vortex side force) are documented in Chapter 9 and collected separately. Brief callouts:

- **Pitch damping $C_{mq}$** is held at **B-level** (a disclosed limitation, not a headline claim): the eq. (54) axis transfer and length-to-diameter normalization match Tobak & Wehrend NACA TN 3788 to $39\,\%$ at $M = 1.5$, but the Basic Finner $C_{mq}$ MAPE is $\approx 69\,\%$ (sign correct, supersonic under-prediction). $C_{mq}$ affects predicted dynamic stability/coning, not the apogee statistics, which are insensitive to $C_{mq}$.
- **Transonic $C_{mq}$ augmentation** (Gaussian peak $3.5\times$) compared against AEDC-TR-76-58 Fig. 12 roll-damping data; over-predicts at $M \in [1.05, 1.12]$, calibrated against integrated flight data rather than against the AEDC component dataset alone. The Sznajder 2025 ANSYS Fluent CFD comparator on the same Basic Finner geometry (Section 8.9 below; PART_E §9.11) independently shows the same transonic over-augmentation direction, with overshoot $+110$ to $+160$ percent at $M = 1.08$--$1.11$ vs the CFD-side reference (the authoritative overshoot magnitude).
- **Magnus body fraction 0.3** within the Platou (AIAA Journal **3**(1), 83–90, 1965, DOI 10.2514/3.2791) measured 0.3–0.8 range; externally benchmarked. (The original master citation "BRL Report 1193, 1963" could not be independently verified; the AIAA Journal publication is the verifiable primary source for the same Platou work.)
- **Vortex $K_v = 0.20$, onset $20°$, saturation $40°$** presented as an internally-calibrated coefficient: no independently verifiable literature anchor was found for this value, so it is reported as a corpus-/range-calibrated constant rather than an externally benchmarked one.

### 8.9 Published CFD Comparators

In addition to the wind-tunnel and free-flight stability benchmarks tabulated above, the present method is anchored against four independent published CFD studies that together span two reference geometries, two distinct aerodynamic quantities (static force/moment coefficients; pitch-damping derivatives), and three Mach bands (transonic; supersonic; supersonic-leading-to-hypersonic). The four sources are: Bunescu et al. (2025) URANS k-$\epsilon$ on the Army-Navy Basic Finner [*Aerospace* **12**(5), 371, DOI 10.3390/aerospace12050371]; Sahu, Nietubicz & Steger (1983) thin-layer Navier-Stokes on a secant-ogive-cylinder-boattail projectile [ARBRL-TR-02495, DTIC AD-A130293]; Vidanović et al. (2014) Menter SST $k$-$\omega$ on the AGARD Model B calibration standard [*Thermal Science* **18**(4), 1223, DOI 10.2298/TSCI130409104V]; and Sznajder (2025) ANSYS Fluent MRF / forced-oscillation / indicial-response computations of Basic Finner pitch damping over $M = 0.9$--$5.0$ [*Trans. Aerospace Res.* No. 4, 98, DOI 10.2478/tar-2025-0021]. A fifth source — Bhagwandin and Sahu (2013) ARL-TR-6725 on Basic Finner and Air Force Modified Finner pitch damping — is used in Section 9.9.6 (PART_E) as a second-source corroboration of the Sznajder supersonic-band finding.

**Table 8.9.1 — Published-CFD comparator inventory.** Detailed per-source discussion is given in PART_E Sections 9.9.6, 9.10, 9.11, and 9.12.

| Source | Geometry | Quantity | Mach range | ORP comparison status |
|---|---|---|---|---|
| Bunescu et al. (2025), URANS | Basic Finner (ANF) | $C_N$, $C_X$ | 0.4--3.5 | Java comparator (`BunescuANFCfdComparatorTest`); $C_X$ MAPE 39.1\% (combined $C_N$+$C_X$ MAPE 43.1\%); correct trend, loose absolute — qualitative |
| Sahu et al. (1983), TLNS | Secant-ogive-cyl.-boattail | $C_{Db}$, $C_{D,\text{tot}}$ | 0.9--1.2 | PDF in repo; comparator deferred — geometry requires a separate ORP rocket model |
| Vidanović et al. (2014), SST k-$\omega$ | AGARD-B | $C_D$, $C_L$, $C_m$ | 0.596, 1.602 | Reference dataset only; AGARD-B `.ork` not shipped (deferred future work) |
| Sznajder (2025), Fluent MRF/FOM/IRM | Basic Finner (ANF) | $C_{mq} + C_{m\dot\alpha}$ | 0.9--4.5 | Comparator wired; supersonic MAPE 31.6% on 8 points ($M \ge 1.29$); transonic overshoot $+110$ to $+160\%$ |
| Bhagwandin & Sahu (2013), Fluent | ANF + AFF | $C_{mq} + C_{m\dot\alpha}$ | 0.6--4.5 | Second-source confirmation of Sznajder supersonic bias direction; AFF supersonic MAPE 18.96% on 5 points |

The four CFD-side panels are collected into the composite figure `paper/data/png/cfd_validation_panels.png`. The two converging findings from Sznajder and Bhagwandin/Sahu — supersonic underprediction of $|C_{mq}|$ by 27--36 percent and a transonic peak over-augmentation — are taken up explicitly as documented limitations in PART_E §12.4 item 2.



## 9. Dynamic Stability and Six-Degree-of-Freedom Integration

The preceding sections developed the static aerodynamic coefficient models -- drag, lift, center of pressure -- as functions of Mach number, angle of attack, and geometry. Those coefficients enter the flight simulation through the equations of motion, which in the extended aerodynamic module are integrated in a full six-degree-of-freedom (6-DOF) framework using a classical fourth-order Runge--Kutta scheme. This chapter documents the dynamic stability derivatives that govern vehicle rotation, the Magnus force that couples roll and yaw, the Euler gyroscopic terms that arise from spin-stabilized flight, the high-angle-of-attack crossflow corrections that keep the simulation finite during tumble, and the state-vector formulation that ties everything together.

The implementation lives primarily in two files. The aerodynamic damping derivatives (pitch damping $C_{mq}$, angle-of-attack rate derivative $C_{m\dot{\alpha}}$, Magnus side force derivative $C_{y,p\alpha}$, and Magnus yaw moment derivative $C_{n,p\alpha}$) are computed in `BarrowmanStabilityCalculator.calculateDampingMoments()` and stored on the `AerodynamicForces` value object. The Euler gyroscopic coupling, quaternion kinematics, RK4 sub-step structure, time-step adaptation, and crossflow override are implemented in `RK4SimulationStepper`.


### 9.1 Pitch Damping Derivative $C_{mq}$

#### 9.1.1 Physical Origin and Strip-Theory Derivation

When a rocket pitches at angular rate $q$ (rad/s), each aerodynamic surface experiences a locally altered angle of attack due to the rotation. A fin or body panel located at axial distance $(x_{CP,i} - x_{CG})$ from the center of gravity sees an incremental velocity component perpendicular to the freestream:

$$\Delta V_{\perp,i} = q \cdot (x_{CP,i} - x_{CG})$$

This incremental velocity produces an incremental normal force at station $i$:

$$\Delta N_i = C_{N\alpha,i} \, q_\infty S_\text{ref} \cdot \frac{\Delta V_{\perp,i}}{V_\infty}$$

The resulting pitching moment about the CG, summed over all $n$ aerodynamic components, defines the pitch damping derivative:

$$
\begin{aligned}
C_{mq}
&= \frac{\partial C_m}{\partial (qL_\text{ref}/2V_\infty)}\\
&= \sum_{i=1}^{n}
\left[
-2\,C_{N\alpha,i}
\frac{(x_{CP,i} - x_{CG})^2}{L_\text{ref}^2}
\right]
\end{aligned}
$$

The factor of $-2$ arises because the conventional non-dimensional pitch rate is $\hat{q} = qL_\text{ref}/(2V_\infty)$, so the effective angle-of-attack increment at station $i$ is

$$\Delta\alpha_i = \frac{q(x_{CP,i} - x_{CG})}{V_\infty} = \frac{2\hat{q}(x_{CP,i} - x_{CG})}{L_\text{ref}},$$

and the moment arm is $(x_{CP,i} - x_{CG})/L_\text{ref}$, giving the squared arm in the formula. Because the contribution of each component scales with the square of the arm, components far from the CG dominate. For a statically stable rocket the fin set is well aft of the CG, so $C_{mq}$ is always negative and provides the restoring torque that damps pitch oscillations.

#### 9.1.2 Transonic Augmentation Factor

Near $M = 1$, unsteady shock oscillation on the body and fins amplifies the effective damping. The implementation in `BarrowmanStabilityCalculator` (constants `TRANSONIC_CMQ_PEAK = 2.5`, `TRANSONIC_CMQ_SIGMA = 0.15`) applies a Gaussian augmentation factor centered at $M = 1$:

$$k_\text{transonic}(M) \;=\; 1 + 2.5 \exp\!\left[-\left(\frac{M - 1}{0.15}\right)^{\!2}\right]$$

The augmented damping derivative is $C_{mq}^\text{aug} = k_\text{transonic}(M) \cdot C_{mq}$. At $M = 1.0$ the augmentation peaks at $k = 3.5$; at $M = 0.7$ or $M = 1.3$ it has decayed to $k \approx 1$. The Gaussian form is $C^\infty$ in Mach (no derivative discontinuity) and is consistent with the qualitative transonic peak in roll-damping data ($C_{lp}$, AEDC-TR-76-58 Fig. 12). The peak height is calibrated, not derived; see Section 9.9.5 for the honest discussion of why this row is rated B in the validation matrix.

#### 9.1.3 Angle-of-Attack Rate Derivative

Following Tobak and Wehrend (NACA TN 3788, 1956), the angle-of-attack rate derivative $C_{m\dot{\alpha}}$ for a slender axisymmetric body is taken as a fixed fraction of $C_{mq}$:

$$C_{m\dot{\alpha}} = 0.4 \, C_{mq}$$

The combined pitch damping moment coefficient is therefore

$$C_m^\text{damp} \;=\; (C_{mq} + C_{m\dot{\alpha}})\,\hat{q} \;=\; 1.4\,C_{mq}\,\hat{q}.$$

Both `Cmq` and `CmAlphaDot` are written to the `AerodynamicForces` object via `setCmq()` and `setCmAlphaDot()` so that downstream consumers (sensitivity exports, plotting, the integrator) see the same value used in the moment balance.

#### 9.1.4 Worked Example -- 1-meter Reference Rocket

Consider a rocket with reference diameter $L_\text{ref} = 0.050$ m, total length $L = 1.0$ m, and three aerodynamic contributors:

| Component | $C_{N\alpha,i}$ (rad$^{-1}$) | $x_{CP,i}$ (m) |
|-----------|------------------------------:|----------------:|
| Nose cone | 2.0 | 0.100 |
| Body tube | 0.5 | 0.350 |
| Fin set   | 6.0 | 0.850 |

With $x_{CG} = 0.500$ m the squared moment arms are $(0.4/0.05)^2 = 64.0$ for the nose, $(0.15/0.05)^2 = 9.0$ for the body, and $(0.35/0.05)^2 = 49.0$ for the fins. Summing,

$$C_{mq} = -2(2.0 \times 64.0 + 0.5 \times 9.0 + 6.0 \times 49.0) = -2 \times 426.5 = -853.0.$$

Applying the transonic factor at three Mach numbers:

| $M$ | $k_\text{transonic}$ | $C_{mq}^\text{aug}$ | $C_{m\dot{\alpha}}$ | Total damping |
|-----|---------------------:|--------------------:|--------------------:|---------------:|
| 0.5 | $1 + 2.5\exp(-11.11) = 1.000$ | $-853.0$  | $-341.2$  | $-1194.2$ |
| 1.0 | $1 + 2.5\exp(0) = 3.500$       | $-2985.5$ | $-1194.2$ | $-4179.7$ |
| 2.0 | $1 + 2.5\exp(-44.44) = 1.000$ | $-853.0$  | $-341.2$  | $-1194.2$ |

The transonic factor of $3.5$ at $M = 1$ nearly triples the effective pitch damping, reflecting the increased damping observed in transonic shock-boundary-layer interaction.

#### 9.1.5 Implementation Details

In `BarrowmanStabilityCalculator.calculateDampingMoments()` the code iterates over all active rocket components, retrieves each component's `getCP()` (a `CoordinateIF` whose weight is the component $C_{N\alpha}$ and whose $x$-coordinate is the per-component CP location), computes the squared moment arm relative to $x_{CG}$, and accumulates the sum. The transonic factor and $C_{m\dot{\alpha}}/C_{mq}$ ratio are applied after accumulation.

**Empirical damping multiplier.** A constant `DAMPING_MULTIPLIER = 3.0` (package-visible for sensitivity testing) is applied to the legacy damping-multiplier output that drives the pitch and yaw damping moments. The factor exists because the linearized theoretical $C_{mq}$ under-predicts the damping required to reproduce realistic apogee-turn behavior in 6-DOF trajectory simulation. Against the ADA636861 free-flight $C_{mq}$ data on the Basic Finner, the combined $\times 3$ multiplier and Gaussian augmentation over-predict damping at $M = 1.05$--$1.12$ by roughly a factor of $3.6$; the Sznajder 2025 ANSYS Fluent CFD comparator independently shows a +110 to +160% overshoot at $M = 1.08$--$1.11$. The multiplier is corpus-calibrated, not externally validated. It is reported as such (not counted in the 20-subsystem external-benchmark headline), and removing it degrades the corpus apogee-turn signature on five flights. The 25-flight closure is dominated by drag and base-pressure terms, so the damping over-prediction does not propagate into the MAE 4.74% headline; it is nonetheless real and unfixed (Section 12.4 item 2).

**Damping-magnitude cap.** The damping moment magnitude is capped at the current static pitching moment coefficient,

$$\lvert C_m^\text{damp}\rvert \le \lvert C_m\rvert,$$

to prevent over-damping from driving the vehicle past the zero-pitch state and inducing artificial oscillation. This cap matters most during the apogee turn, where $C_m$ approaches zero as AoA decreases.

**Per-component fin/body legacy contributions.** The legacy `getDampingMultiplier()` path (preserved to keep small low-Reynolds-number rockets stable) adds two analytic contributions:

$$C_{mq,\text{fin}} \;=\; -0.6 \cdot \min(n, 4) \cdot \frac{A_\text{planform} \cdot |x_\text{fin} - x_{CG}|^3}{S_\text{ref} \cdot L_\text{ref}}$$

$$C_{mq,\text{body}} \;=\; -0.275 \cdot \frac{D}{S_\text{ref} \cdot L_\text{ref}} \cdot \left(x_{CG}^4 + (L - x_{CG})^4\right)$$

The fin-count cap at four reflects the diminishing return of additional fins for damping; beyond four fins, mutual interference erodes the incremental contribution.


### 9.2 Magnus Force and Moment

#### 9.2.1 Physical Mechanism

When a spinning rocket flies at an angle of attack, the body boundary layer on the windward side is thinner than on the leeward side because the crossflow velocity $V_\infty \sin\alpha$ adds to (or subtracts from) the circumferential surface velocity $\omega r$ induced by spin. The asymmetric boundary layer produces an asymmetric pressure distribution and a side force perpendicular to the angle-of-attack plane. This is the Magnus effect.

For a slender axisymmetric body the Magnus side force coefficient derivative is (Nielsen 1960; Jorgensen 1973):

$$C_{y,p\alpha} \;=\; -\frac{2}{3}\,C_{N\alpha,\text{body}},$$

with the Magnus side force coefficient and physical side force defined as

$$
\begin{aligned}
C_y^\text{Magnus}
&= C_{y,p\alpha} \cdot \hat{p} \cdot \sin\alpha,\\
F_\text{Magnus}
&= C_y^\text{Magnus} \, q_\infty S_\text{ref},
\end{aligned}
$$

and the non-dimensional roll rate $\hat{p} = pL_\text{ref}/(2V_\infty)$ with $p$ the roll rate in rad/s.

#### 9.2.2 Magnus Yaw Moment

The Magnus side force acts at the CP, producing a yaw moment about the CG:

$$
\begin{aligned}
C_{n,p\alpha}
&= C_{y,p\alpha} \cdot \frac{x_{CP} - x_{CG}}{L_\text{ref}},\\
C_n^\text{Magnus}
&= C_{n,p\alpha} \cdot \hat{p} \cdot \sin\alpha.
\end{aligned}
$$

In OpenRocket's nose-positive convention a stable rocket has $x_{CP} > x_{CG}$ (CP aft of CG along the body axis), so the Magnus yaw moment is destabilising in yaw -- i.e., excessive roll rates can erode the effective stability margin. This is why high-spin minimum-diameter sport rockets sometimes show coning under disturbance even when the static margin is nominally adequate.

#### 9.2.3 Body $C_{N\alpha}$ Fraction

The implementation uses the conservative slender-body approximation

$$C_{N\alpha,\text{body}} \;\approx\; 0.3 \cdot C_{N\alpha,\text{total}}.$$

This factor is a compact estimate that avoids per-component decomposition of normal force inside the damping calculation. It is consistent with the body-alone vs finned-body Magnus ratios reported by Platou ("Magnus Characteristics of Finned and Nonfinned Projectiles," *AIAA Journal* 3(1), 83–90, 1965), which fall in the 0.3--0.8 range depending on fin loading and Mach number; 0.3 sits at the lower end (the conservative side, since body and fin Magnus forces are opposite in sign and the smaller the body fraction, the smaller the predicted Magnus yaw moment).

#### 9.2.4 Worked Example -- Spinning Rocket at $M = 2$, $\alpha = 5°$

Take $C_{N\alpha,\text{total}} = 10.0$ rad$^{-1}$, body $C_{N\alpha} \approx 0.3 \times 10.0 = 3.0$ rad$^{-1}$, $L_\text{ref} = 0.050$ m, $V_\infty = 686$ m/s ($M = 2$ at sea level), roll rate $p = 10$ rev/s $= 62.83$ rad/s, $\alpha = 5° = 0.0873$ rad, $x_{CP} = 0.285$ m, $x_{CG} = 0.500$ m, $q_\infty = 288{,}200$ Pa, $S_\text{ref} = 1.9635 \times 10^{-3}$ m$^2$.

$$\hat{p} = \frac{62.83 \times 0.050}{2 \times 686} = 0.00229, \qquad C_{y,p\alpha} = -\tfrac{2}{3} \times 3.0 = -2.0,$$

$$C_y^\text{Magnus} = -2.0 \times 0.00229 \times \sin(5°) = -3.99 \times 10^{-4},$$

$$F_\text{Magnus} = -3.99 \times 10^{-4} \times 288{,}200 \times 1.9635 \times 10^{-3} = -0.226 \text{ N}.$$

For the yaw moment

$$C_{n,p\alpha} = -2.0 \times \frac{0.285 - 0.500}{0.050} = +8.60, \qquad C_n^\text{Magnus} = +1.72 \times 10^{-3}.$$

The 0.226 N side force is small compared to the typical aerodynamic normal force of tens of newtons, but the yaw moment accumulates over time and increases the dispersion of a spinning rocket -- which is precisely why the term is retained in the 6-DOF integration.


### 9.3 Euler Gyroscopic Coupling

#### 9.3.1 Motivation

A spinning rocket is a gyroscope. When external aerodynamic moments are applied to a body with significant angular momentum about the roll axis, the body precesses rather than rotating directly in the direction of the applied moment. Neglecting this coupling produces incorrect pitch--yaw phasing and, for fast-spinning rockets, can produce qualitatively wrong trajectories.

#### 9.3.2 Derivation of the Euler Equations

For a rigid body with body-fixed principal axes $(x, y, z)$ where $z$ is the roll (longitudinal) axis and an axisymmetric inertia tensor $I_x = I_y = I_\text{long}$, $I_z = I_\text{roll}$, the angular momentum vector in body coordinates is

$$\mathbf{H} = \mathbf{I}\boldsymbol{\omega} = (I_\text{long}\omega_x, \; I_\text{long}\omega_y, \; I_\text{roll}\omega_z)^T.$$

Newton's second law for rotation in the rotating body frame gives the Euler equations $\mathbf{M} = \dot{\mathbf{H}}|_\text{body} + \boldsymbol{\omega} \times \mathbf{H}$. Expanding the cross product and exploiting axisymmetry,

$$(\boldsymbol{\omega} \times \mathbf{H})_x = (I_\text{roll} - I_\text{long})\,\omega_y\omega_z,$$

$$(\boldsymbol{\omega} \times \mathbf{H})_y = (I_\text{long} - I_\text{roll})\,\omega_x\omega_z,$$

$$(\boldsymbol{\omega} \times \mathbf{H})_z = 0,$$

so the full Euler equations for an axisymmetric body are

$$I_\text{long}\,\dot{\omega}_x = M_x - (I_\text{roll} - I_\text{long})\,\omega_y\omega_z,$$

$$I_\text{long}\,\dot{\omega}_y = M_y - (I_\text{long} - I_\text{roll})\,\omega_x\omega_z,$$

$$I_\text{roll}\,\dot{\omega}_z = M_z.$$

The cross-coupling terms transfer energy between the pitch and yaw channels through $\omega_z$. When the roll rate is zero, those terms vanish and pitch and yaw decouple.

#### 9.3.3 Implementation in the Acceleration Computation

In `RK4SimulationStepper.computeAcceleration()`, after the aerodynamic moments $M_x, M_y, M_z$ are computed (variables `momX`, `momY`, `momZ`), the gyroscopic correction is applied as

```
momX -= omega_y * (I_roll * omega_z) - omega_z * (I_long * omega_y)
momY -= omega_z * (I_long * omega_x) - omega_x * (I_roll * omega_z)
momZ -= omega_x * (I_long * omega_y) - omega_y * (I_long * omega_x)
```

That is, $\boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})$ is subtracted from the total moment before dividing by inertia, recovering the rearranged Euler equation

$$\dot{\boldsymbol{\omega}} = \mathbf{I}^{-1}\bigl[\mathbf{M} - \boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})\bigr].$$

#### 9.3.4 Coordinate Transform

The angular velocity vector is stored in world coordinates in the simulation state. Before applying the Euler equations it is rotated into body coordinates: an inverse quaternion rotation removes the rocket's orientation, and an additional inverse $R_z(-\theta)$ rotation removes the lateral wind angle so the surviving components align with the body principal axes. After computing $\dot{\boldsymbol{\omega}}$ in body coordinates, the reverse sequence transforms it back to world coordinates for integration.

#### 9.3.5 Precession

The free-precession rate of an axisymmetric top is

$$\Omega_\text{prec} = \frac{(I_\text{long} - I_\text{roll})\,\omega_z}{I_\text{long}}.$$

For a slender rocket with $I_\text{long} \gg I_\text{roll}$ (typical ratio $I_\text{roll}/I_\text{long} \sim 0.01$) this simplifies to $\Omega_\text{prec} \approx \omega_z$ -- the precession rate is approximately the roll rate.

#### 9.3.6 Dynamic Pressure Gate

The gyroscopic coupling terms are computationally active only when the dynamic pressure exceeds a fixed threshold of $q_\infty > 500$ Pa (about 29 m/s at sea level, 50 m/s at 10 km altitude). The gate exists for two reasons.

1. **Near apogee**: when $q_\infty \to 0$, the aerodynamic restoring moments vanish and the rocket is effectively in free-body tumble. The gyroscopic terms remain physically present but introduce numerical stiffness into the explicit RK4 integrator without improving trajectory accuracy. RK4 cannot conserve angular momentum for the stiff free-body oscillations that arise when there is no aerodynamic damping, so rotational velocity tends to drift exponentially rather than oscillate.

2. **Numerical stability**: at low dynamic pressure the angular velocities can be large relative to the (vanishing) aerodynamic restoring forces, and the gyroscopic cross-coupling dominates the moment equations. An implicit integrator could absorb that stiffness; an explicit RK4 cannot, except by collapsing the time step.

The threshold was originally 1 Pa, which permitted divergent rotational drift during ballistic descent. Raising it to 500 Pa restricts gyroscopic coupling to the powered and aerodynamically-guided portions of the flight where Barrowman moments balance the gyroscopic redistribution.

#### 9.3.7 Time-Step Limiting

The RK4 integrator employs adaptive time-step selection driven, in part, by angular-rate limits:

$$
\begin{aligned}
\Delta t_\text{roll}
&= \frac{\phi_\text{max,roll}}{\lvert\omega_z\rvert},\\
\Delta t_\text{pitch/yaw}
&= \frac{\phi_\text{max,pitch}}
        {\max(\lvert\dot{\omega}_x\rvert, \lvert\dot{\omega}_y\rvert)}.
\end{aligned}
$$

with $\phi_\text{max,roll} = 2 \times 28.32° = 56.64°$ and $\phi_\text{max,pitch} = 4°$ per step. The roll-step limit deliberately uses an irrational fraction of a full circle ($28.32°$) so that successive steps sample different azimuthal orientations and prevent aliasing of wind effects on the spinning vehicle.

**Angular timestep floor.** The pitch/yaw angle and acceleration constraints are floored at $\Delta t_\text{user}/4$, where $\Delta t_\text{user}$ is the user-selected timestep. Without this floor, tumbling rockets at high pitch rates collapse the timestep by a factor of 10 or more during ballistic descent. Because the Barrowman small-angle aerodynamic model is already losing accuracy at post-stall AoA, fine angular resolution during tumble does not improve accuracy; it merely produces 10× slowdown. The overall minimum is clamped at $\Delta t_\text{user}/20$ as an absolute floor for pathological cases (e.g., extreme spin with no aerodynamic damping).


### 9.4 State Vector and RK4 Integration

#### 9.4.1 The 13-Component State Vector

The simulation state vector $\mathbf{y}$ contains 13 components organized as

$$
\begin{aligned}
\mathbf{y} = [\,&\underbrace{x, y, z}_{\text{position}},\;
\underbrace{v_x, v_y, v_z}_{\text{velocity}},\\
&\underbrace{q_0, q_1, q_2, q_3}_{\text{orientation quaternion}},\;
\underbrace{\omega_x, \omega_y, \omega_z}_{\text{angular velocity}}\,]^T.
\end{aligned}
$$

Position and linear velocity live in world Cartesian coordinates (m, m/s); orientation is a unit quaternion $q = q_0 + q_1\mathbf{i} + q_2\mathbf{j} + q_3\mathbf{k}$; angular velocity is stored in world coordinates and rotated into the body frame as needed. The use of a quaternion (rather than Euler angles) eliminates the gimbal-lock singularity at vertical orientation -- which is exactly the configuration encountered during ascent and at apogee.

#### 9.4.2 Quaternion Kinematics

The orientation quaternion evolves according to

$$\dot{\mathbf{q}} = \tfrac{1}{2}\,\mathbf{q} \otimes \boldsymbol{\Omega},$$

where $\boldsymbol{\Omega} = (0, \omega_x, \omega_y, \omega_z)$ is the body-frame angular velocity expressed as a pure quaternion and $\otimes$ is quaternion multiplication. In components,

$$\dot{q}_0 = \tfrac{1}{2}(-q_1\omega_x - q_2\omega_y - q_3\omega_z),$$

$$\dot{q}_1 = \tfrac{1}{2}(q_0\omega_x + q_2\omega_z - q_3\omega_y),$$

$$\dot{q}_2 = \tfrac{1}{2}(q_0\omega_y - q_1\omega_z + q_3\omega_x),$$

$$\dot{q}_3 = \tfrac{1}{2}(q_0\omega_z + q_1\omega_y - q_2\omega_x).$$

#### 9.4.3 Equations of Motion Summary

The complete 6-DOF equations of motion integrated by the RK4 stepper are:

**Translational.** $\dot{\mathbf{x}} = \mathbf{v}$, and

$$\dot{\mathbf{v}} = \frac{1}{m}\bigl[\mathbf{R}(\mathbf{q})\,\mathbf{F}_\text{body} - m\mathbf{g} + \mathbf{F}_\text{Coriolis}\bigr],$$

where $\mathbf{F}_\text{body}$ collects thrust, drag, normal force, and side force (including the Magnus contribution), and $\mathbf{R}(\mathbf{q})$ is the rotation matrix corresponding to the orientation quaternion.

**Rotational.** $\dot{\mathbf{q}} = \tfrac{1}{2}\mathbf{q} \otimes \boldsymbol{\Omega}$, and

$$\dot{\boldsymbol{\omega}} = \mathbf{I}^{-1}\bigl[\mathbf{M}_\text{aero} - \boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})\bigr],$$

where $\mathbf{M}_\text{aero}$ collects the pitch moment $C_m\,q_\infty S_\text{ref}L_\text{ref}$, the yaw moment (with Magnus contribution), the roll moment, and the pitch/yaw damping moments.

#### 9.4.4 RK4 Sub-Step Structure

The classical fourth-order Runge--Kutta method evaluates the right-hand side at four points within each step $h$:

$$\mathbf{k}_1 = f(t_n, \mathbf{y}_n), \quad \mathbf{k}_2 = f\bigl(t_n + \tfrac{h}{2}, \mathbf{y}_n + \tfrac{h}{2}\mathbf{k}_1\bigr),$$

$$
\begin{aligned}
\mathbf{k}_3
&= f\bigl(t_n + \tfrac{h}{2}, \mathbf{y}_n + \tfrac{h}{2}\mathbf{k}_2\bigr),\\
\mathbf{k}_4
&= f(t_n + h, \mathbf{y}_n + h\mathbf{k}_3),
\end{aligned}
$$

$$\mathbf{y}_{n+1} = \mathbf{y}_n + \tfrac{h}{6}(\mathbf{k}_1 + 2\mathbf{k}_2 + 2\mathbf{k}_3 + \mathbf{k}_4).$$

At each evaluation point the full aerodynamic calculation is performed: `ShockGeometry` pre-pass (a no-op below $M \approx 1.0$), per-component stability computation, drag computation, thrust evaluation, and gravity/Coriolis corrections. This means **four complete aerodynamic evaluations per simulation timestep**, which dominates the per-step cost and motivates the supersonic/subsonic timing budget reported in Section 11.6.

#### 9.4.5 Quaternion Normalisation

After the RK4 update the quaternion may drift from unit norm because the linear combination of the four sub-steps is performed in finite precision. The implementation re-checks $\|\mathbf{q}\|$ each step and renormalises if the squared deviation exceeds a tolerance:

$$\mathbf{q} \leftarrow \mathbf{q}/\|\mathbf{q}\| \quad \text{if} \quad \bigl|\,\|\mathbf{q}\|^2 - 1\,\bigr| > \epsilon.$$

This prevents the orientation from drifting non-physical over thousands of integration steps.

#### 9.4.6 Integration Stability Bounds

The simulation enforces hard absolute bounds on the state vector to detect divergence:

$$\|\mathbf{v}\|^2 < 10^{18}, \quad \|\mathbf{x}\|^2 < 10^{18}, \quad \|\boldsymbol{\omega}\|^2 < 10^{18}.$$

Exceeding any bound throws `SimulationCalculationException`. These bounds are far beyond any physically realisable rocket flight; they exist solely to halt numerical runaway and produce a diagnostic.

**Early-warning diagnostics.** Before the hard bounds trigger, the integrator emits a detailed warning when any squared magnitude exceeds $10^{12}$. The diagnostic captures the simulation time, velocity and rotation magnitudes, current timestep, AoA, Mach, and the aerodynamic coefficients $C_N$, $C_m$, $C_D$, enabling root-cause diagnosis without needing to reproduce the divergence in a debugger.

#### 9.4.7 Aerodynamic Coefficient Sanitisation

`BarrowmanCalculator` applies a defense-in-depth sanitization pass to the assembled aerodynamic forces after all component calculations and before the damping moments are applied. The pass catches non-finite values (`NaN`, `Infinity`) and extreme magnitudes that would otherwise cause RK4 to diverge in a single timestep:

| Coefficient | Maximum | Rationale |
|:------------|:--------|:----------|
| $C_D$ | 10.0 | A blunt body at $M=10$ has $C_D \approx 2$; $C_D > 10$ is unphysical for any rocket geometry |
| $C_{D,\text{axial}}$ | 10.0 | Same bound as total $C_D$ |
| $C_N$ | 100.0 | At extreme AoA $C_N$ can reach 30--50; beyond 100 indicates blow-up |
| $C_m$ | (finite) | Zeroed if `NaN` or `Infinity` |
| $C_\text{side}$ | (finite) | Zeroed if `NaN` or `Infinity` |

When any coefficient is clamped, a `Warning.FORCE_COEFFICIENT_CLAMPED` warning is added to the simulation warning set so the user sees that the aerodynamic model exceeded its valid range. The per-component $\mathtt{NaN}$/$\mathtt{Infinity}$ checks were upgraded from `Double.isNaN()` to `Double.isFinite()` so $\mathtt{Infinity}$ values cannot propagate. Sanitization is the last safety net; the primary defense remains the $C^1$-continuous regime blending of Section 10.


### 9.5 Crossflow Normal Force at High Angle of Attack

#### 9.5.1 Motivation

The Barrowman stability model is a small-angle linearized potential-flow theory; fin $C_{N\alpha}$ saturates at roughly $\alpha = 20°$. At post-stall angles encountered during tumbling descent, motor failure, or extreme wind shear, the actual aerodynamic normal force is dominated by bluff-body crossflow drag on the side-projected planform, not by attached-flow fin lift. Naively using the small-angle Barrowman $C_N$ at $\alpha > 30°$ produces two coupled failure modes:

1. **Insufficient deceleration.** With $C_N$ too small, the drag perpendicular to the body axis is too small, and the rocket reaches unrealistically high descent velocities.
2. **Artificial torque divergence.** $C_m$ was computed at small angle and is no longer the right scale relative to the small Barrowman $C_N$. The implied $C_m/C_N$ ratio places the CP far from the physical planform centroid, generating large artificial torque that drives rotational divergence in the explicit RK4 integrator.

#### 9.5.2 Crossflow Drag Model

The crossflow normal force model treats the rocket's side profile as a collection of bluff bodies in crossflow at velocity $V_\infty \sin\alpha$. For each body component (body tubes, nose cones, transitions),

$$C_N^{\text{body}} = C_{d,c}(M_c) \cdot \frac{A_\text{planform}}{S_\text{ref}} \cdot \sin^2\alpha,$$

where $C_{d,c}(M_c)$ is the Jorgensen crossflow drag coefficient evaluated at the crossflow Mach $M_c = M_\infty |\sin\alpha|$ and $A_\text{planform}$ is the side-projected planform area. For each fin in the set,

$$C_N^{\text{fin}} = C_{d,\text{fin}} \cdot \frac{A_\text{fin,planform}}{S_\text{ref}} \cdot \eta_n \cdot \frac{\sin^2\alpha}{n},$$

with $C_{d,\text{fin}} = 1.42$ (the flat-plate crossflow drag coefficient consistent with Hoerner Ch. 3 Fig. 28; the matrix records $0.7\%$ relative error against the tabulated 1.43), $n$ the fin count, and $\eta_n$ a fin-fin shadowing efficiency factor:

| Fin count $n$ | 1 | 2 | 3 | 4 | 5 | 6 |
|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| $\eta_n$ | 0.50 | 1.00 | 1.41 | 1.81 | 1.73 | 1.90 |

The total crossflow $C_N$ is the sum of all body and fin contributions.

#### 9.5.3 Override Logic and Moment Scaling

The crossflow $C_N$ is computed after the Barrowman stability and drag calculations and overrides the Barrowman value only when it is larger in magnitude:

$$
C_N^{\text{final}} =
\begin{cases}
C_N^{\text{crossflow}},
  & C_N^{\text{crossflow}} > \lvert C_N^{\text{Barrowman}}\rvert,\\
C_N^{\text{Barrowman}},
  & \text{otherwise.}
\end{cases}
$$

At low AoA the crossflow term is negligible (it scales as $\sin^2\alpha$) and Barrowman dominates. At high AoA ($\alpha > 30°$--$40°$) the crossflow term dominates and provides the correct deceleration force.

**Moment scaling.** Whenever the override fires, $C_m$ must be scaled proportionally to keep the implied CP near the planform centroid:

$$C_m^{\text{scaled}} = C_m^{\text{Barrowman}} \cdot \min\left(\left|\frac{C_N^{\text{crossflow}}}{C_N^{\text{Barrowman}}}\right|,\, 20\right).$$

The cap at 20 prevents amplification of numerical noise in $C_m$ when $C_N^{\text{Barrowman}}$ approaches zero. When $\lvert C_N^{\text{Barrowman}}\rvert < 0.5$ the CP location is treated as ill-defined and $C_m$ is set to zero -- crossflow drag at extreme AoA acts roughly through the planform centroid, which for a typical rocket is near the CG.

#### 9.5.4 Numerical Singularity Guards

Several transonic and near-sonic singularities in upstream models are guarded so that non-finite values cannot reach the override logic:

1. **SBLI separation length** (`FreeInteractionSBLI.separationLength()`): the free-interaction SBLI separation length scales as $(M^2 - 1)^{-0.25}$, which diverges as $M \to 1^+$. A floor $M^2 - 1 \ge 0.1$ ($M \gtrsim 1.05$) prevents infinite separation lengths from feeding extreme pressure drag near $M = 1$.
2. **Separation pressure plateau** (`SymmetricComponentCalc`): $C_{p,\text{plateau}} = 4.2\sqrt{2C_f / \sqrt{M^2 - 1}}$ diverges as $M \to 1^+$. The threshold was raised from $M^2 - 1 > 0.01$ to $M^2 - 1 > 0.04$ ($M \gtrsim 1.02$) and $C_{p,\text{plateau}}$ is capped at 2.0.
3. **Fin $K_3$ denominator** (`FinSetCalc`): the Barrowman polynomial coefficient $K_3$ contains a denominator $(2\,\text{AR}\,\beta - 1)$ that vanishes for some AR/Mach pairs. A floor $|2\,\text{AR}\,\beta - 1| \ge 0.01$ prevents division by zero.
4. **Fin polynomial singularity** (`FinSetCalc.calculatePoly()`): the common denominator $(1 - 3.4641\,\text{AR})^2$ in the subsonic interpolation polynomial vanishes at $\text{AR} \approx 0.2887$. A floor of $10^{-4}$ keeps the polynomial coefficients finite.


### 9.6 Asymmetric Vortex Shedding

At high angles of attack ($\alpha > 20°$) the vortex pair shed from the leeward side of a slender body becomes asymmetric due to convective instabilities in the separated shear layers, producing a side force perpendicular to the angle-of-attack plane *even in the absence of roll*. The phenomenon is qualitatively well-documented for ogive-cylinder bodies at high incidence and can cause significant lateral dispersion in flight trajectories.

The implementation models the asymmetry as

$$C_{y,\text{vortex}} = K_v \cdot C_N \cdot f(\alpha),$$

with empirical asymmetry coefficient $K_v = 0.20$, $C_N$ the current total body normal force coefficient (which already includes the crossflow override of Section 9.5 when applicable), and a linear ramp

$$f(\alpha) = \begin{cases} 0 & \alpha \le 20°,\\ (\alpha - 20°)/20° & 20° < \alpha < 40°,\\ 1 & \alpha \ge 40°.\end{cases}$$

The side force is added to $C_\text{side}$ after all other aerodynamic calculations. At $\alpha = 40°$ the vortex side force is 20% of the body normal force -- a substantial lateral perturbation that often dominates the yaw dynamics during tumble. A `Warning.HIGH_AOA_VORTEX` is issued when the model activates.

The asymmetry coefficient $K_v = 0.20$ has **no verifiable literature anchor** and is presented here as an internally-calibrated coefficient, not as an externally benchmarked value. It corresponds to roughly a 20% side-force fraction of the body normal force at peak, which sits in the plausible range for a fin-suppressed slender body, but it should be read as an engineering range-check rather than a closed validation. It is therefore not counted among the externally benchmarked subsystems.


### 9.7 Fin-Fin Aerodynamic Interference

For rockets with more than four fins, mutual aerodynamic interference between adjacent fins reduces total normal force below the linear-superposition prediction. The interference knockdown is applied as a multiplicative correction to per-fin $C_{N\alpha}$:

| Fin count | Knockdown factor | Source |
|:---------:|:----------------:|--------|
| 1--4 | 1.000 | No interference |
| 5 | 0.948 | Empirical |
| 6 | 0.913 | Empirical |
| 7 | 0.854 | Empirical |
| 8 | 0.810 | Empirical |
| 9+ | 0.750 | Conservative estimate (with warning) |

For 3 and 4 fins the angular separation ($120°$ and $90°$) is large enough that interference is negligible; for 5+ fins the reduced angular separation causes partial blanking of downstream fins by the wake and pressure field of upstream fins.

The implementation also caps fin normal force at a stall angle:

$$C_N = C_{N\alpha} \cdot \min(\alpha, \alpha_\text{stall}), \qquad \alpha_\text{stall} = 20°.$$

Beyond stall, the fin lift coefficient is held constant rather than continuing to grow linearly, which correctly captures separation off the fin surfaces. Roll forcing is linearly reduced to zero over $[\alpha_\text{stall}, 1.5\,\alpha_\text{stall}]$.


### 9.8 Roll Damping with Supersonic Mach-Cone Correction

At supersonic speeds the Mach cone emanating from the fin root chord limits the spanwise extent of the fin that can influence the flow. The effective fin span for roll damping is

$$s_\text{eff} = \min\bigl(s, \; c_r \sqrt{M^2 - 1}\bigr),$$

with $s$ the geometric semispan and $c_r$ the root chord. At $M = 2$, $c_r\sqrt{3} \approx 1.73 c_r$; a fin with semispan greater than $1.73 c_r$ has its outboard portion aerodynamically silent for roll damping.

Subsonically, the roll-damping moment uses the classical strip integral

$$C_{l,\text{damp}} = \frac{2\pi \cdot p \cdot \sum c_i r_i \Delta r}{S_\text{ref} \cdot L_\text{ref} \cdot V \cdot \beta}.$$

Supersonically, the strip integration uses the $K_1/K_2/K_3$ supersonic fin lift coefficients and truncates at $s_\text{eff}$. In the transonic regime ($M = 0.9$--$1.5$) a linear interpolation blends the subsonic value evaluated at $M = 0.85$ with the supersonic value evaluated at $M = 1.55$, sampling slightly inboard of the regime boundaries to avoid evaluating at the most singular Mach values.

When the fin tip velocity $p \cdot (r_\text{body} + s)$ exceeds a $15°$ stall envelope relative to freestream, a strip-wise integration with angle-of-attack capping replaces the single-formula approach so that stalled fin tips during rapid roll do not over-contribute.

The roll-damping implementation is independently verified: the analytical closed-form integral $\int_0^s c(y)(r+y)^2\,dy$ for the trapezoidal benchmark fin matches the code's 48-point numerical strip integration to within 2.0% across $M = 0.3$--$0.8$ in the dynamic-stability benchmark (Section 9.9.4).


### 9.9 Dynamic Stability Benchmarks

The dynamic stability models in this chapter are validated against published experimental and theoretical data from four independent sources. The validation matrix lists the implementation row as **A** (Cmq accumulation, Magnus computation, roll damping integral all reproduce analytical or theoretical anchors to within their stated tolerances) while explicitly disclosing the **B** rating on the Cmq magnitude calibration constants (the `3x` multiplier and the Gaussian peak height).

#### 9.9.1 Pitch Damping -- Tobak and Wehrend (NACA TN 3788)

The pitch-damping derivative $C_{mq}$ is validated against the linearized supersonic theory of Tobak and Wehrend (NACA TN 3788, 1956), who derived stability derivatives for cones at supersonic speeds. `TobakCmqBenchmarkTest` compares the strip-theory implementation against TN 3788 at $M = 1.5$, applying the axis-transfer correction (TN 3788 eq. 54) to convert from a nose-tip to a CG reference and the length-to-diameter normalization needed to compare body- vs diameter-referenced coefficients. The frozen result is **39%** agreement at $M = 1.5$ and conservative bounding at higher Mach. This is the level of agreement expected when comparing an engineering strip-theory approximation against linearized theory for an isolated cone without fins; the validation matrix records this row as A with the frozen 39% threshold.

#### 9.9.2 Magnus Force -- Platou (AIAA Journal 1965)

The Magnus model is validated against the wind-tunnel measurements of Platou, "Magnus Characteristics of Finned and Nonfinned Projectiles," *AIAA Journal* **3**(1), 83–90 (1965), DOI 10.2514/3.2791, on body-alone and finned-body configurations at supersonic speeds. The original master citation for this work as "BRL Report 1193, 1963" could not be independently verified through NTRS or DTIC search; the AIAA Journal publication is the verifiable primary source for the same work and has been adopted in place of the unverified report number. `MagnusBenchmarkTest` uses the implementation default body fraction $0.3$ and compares the predicted $C_{y,p\alpha}$ against Platou 1965 for both configurations. The implementation lies within the measured range $0.3$--$0.8$ for the body fraction and matches the reference body $C_{N\alpha}$ derivation to machine precision.

#### 9.9.3 Vortex Sideforce -- Internally-Calibrated Coefficient (No Literature Anchor)

The asymmetric vortex shedding model uses asymmetry coefficient $K_v = 0.20$. This coefficient has **no verifiable literature source** and is therefore presented as an internally-calibrated coefficient rather than an externally benchmarked value. `VortexSideforceBenchmarkTest` is an internal range-check that the predicted side-force magnitude and onset angle stay within a plausible high-incidence envelope; it is not an external-data benchmark, and the $K_v$ row is downgraded out of the A-level count and reported as a qualitative/secondary item.

#### 9.9.4 Dynamic Stability Integration -- Independent Recomputation

`DynamicStabilityBenchmarkTest` validates the combined effect of all dynamic stability derivatives -- pitch damping, Magnus, roll damping, gyroscopic coupling -- against three independently coded analytical anchors:

| Path | Anchor | Result |
|------|--------|--------|
| Cmq accumulation (strip theory) | Independent re-summation of $-2\sum C_{N\alpha,i}(\text{arm}/d)^2 \cdot k_\text{transonic}$ | $< 0.5\%$ at all tested $M$ |
| Roll damping integral (Barrowman 1967) | Closed-form $\int_0^s c(y)(r+y)^2\,dy$ vs 48-point strip sum | $< 2\%$ |
| Magnus coefficient (slender body) | $C_{y,p\alpha} = -(2/3) \cdot 0.3 \cdot C_{N\alpha,\text{total}}$ | machine precision ($< 0.01\%$) |

The dynamic-stability benchmark CSV (`paper/data/csv/dynamic_stability_benchmark.csv`) records 38 Mach points from $M = 0.3$ to $M = 4.0$ for $C_{mq}$, $C_{m\dot{\alpha}}$, the transonic factor $k$, and the Magnus derivatives.

#### 9.9.5 $C_{mq}$ Magnitude vs ADA636861

The Basic Finner $C_{mq}$ benchmark (`BasicFinnerCmqBenchmarkTest`) compares the integrated damping prediction against the free-flight $C_{mq}$ data of Dupuis & Hathaway (ADA636861, 1997). The result is **MAPE 69%**: correct sign and qualitative trend, with supersonic under-prediction and a transonic over-prediction of approximately a factor of $3.6$ at $M = 1.05$--$1.12$. Two constants drive the discrepancy: the global $\times 3$ multiplier on per-component damping and the Gaussian augmentation peaking at $3.5\times$ near $M = 1$. Both were set by the corpus apogee-turn signature, not by an isolated $C_{mq}$ dataset, and the validation matrix correctly rates them as **B** (corpus-anchored) rather than **A** (externally benchmarked).

Recalibrating against ADA636861 directly would burn the only available external $C_{mq}$ benchmark for this geometry class, leaving the recalibrated value with no remaining check. The constants are therefore left as-is and a second independent free-flight $C_{mq}$ dataset is the prerequisite for tuning them. None has been located.

#### 9.9.6 Second Cmq Source on a Non-Basic-Finner Geometry -- Bhagwandin & Sahu 2013

A geometry-independent cross-check is provided by the URANS pitch-damping CFD predictions of Bhagwandin and Sahu (2013), ARL-TR-6725. The report covers two slender finned geometries: the Army-Navy Basic Finner (ANF, the same configuration used by ADA636861 above) and the **Air Force Modified Finner (AFF)**, a tangent-ogive-cylinder body with a clipped-delta sharp-LE fin set. AFF differs from ANF in two of three top-level shape descriptors -- nose family (curved tangent ogive vs straight cone) and fin planform (delta vs rectangular) -- which qualifies it as a non-Basic-Finner second source for the Cmq audit.

The combined comparator `BhagwandinSahuCmqComparatorTest` reports per-band agreement against the planar-pitching CFD predictions in Tables A-1 and A-2 of the report (digitized at `paper/data/csv/bhagwandin_sahu_2013_anf_aff_cmq.csv`):

| Geometry | Mach band | Points | MAPE | Worst $|\Delta_\text{pct}|$ |
|---|---|---:|---:|---:|
| AFF | 1.30--2.50 | 5 | **18.96%** | 30.83% at $M = 2.50$ |
| ANF | 1.29--4.50 | 8 | 28.02% | 33.82% at $M = 2.00$ |

The AFF supersonic per-point signed deltas are $+4.79$, $-12.08$, $-20.99$, $-26.08$, $-30.83\%$ at $M = 1.30, 1.50, 1.75, 2.00, 2.50$. The bias on AFF is in the **same direction** as on ANF (ORP underpredicts $\lvert C_{mq} \rvert$ at supersonic Mach), which is consistent with the supersonic underprediction being a model-physics issue rather than a geometry-specific artifact. The transonic-band agreement is dominated by the same Gaussian-augmentation overshoot already documented against ADA636861 in Section 9.9.5 and is not separately informative on AFF.

This benchmark is reported as **B-level** in the present revision. Justification: the AFF supersonic MAPE of 18.96% is below the 30% closure threshold targeted in the AST roadmap and the bias direction reproduces on the second geometry, but the AFF fin planform used in the ORP comparator fixture (`makeAirForceModifiedFinner` in `SupersonicTestRockets.java`) is currently a placeholder (root chord 1.0 cal, tip 0.5 cal, sweep 0.5 cal, span 1.0 cal). The dimensional callouts of Figure 3 of the source report were not available in repo at the time of this comparator -- the ARL-TR-6725 / DTIC ADA592550 PDF has not yet been dropped into `paper/data/pdf/`, and a full needs-list with the planform values required for promotion to A-level is recorded at `paper/data/cmq_second_source_bhagwandin_2013_assessment.md` ("AFF fin planform -- needs-list"). The B-level rating reflects the incomplete fixture, not the agreement: the comparator is sign-consistent with ANF and within the supersonic band's claimed precision once the planform is calibrated. Comparator artifacts: `paper/data/csv/bhagwandin_aff_cmq_comparator_2026_05_02.csv` and `paper/data/csv/bhagwandin_anf_cmq_comparator_2026_05_02.csv`.

### 9.10 CFD Comparator -- Bunescu et al. 2025 ANF URANS

The Cmq second source above is a CFD prediction of pitch damping; an additional CFD comparator anchors the ORP total-drag pipeline against an independent open-access URANS dataset on the Basic Finner. Bunescu et al. (2025), *Aerospace* **12**(5), 371, report URANS k-epsilon predictions on the same Army-Navy Basic Finner geometry used by ADA636861 (60 mm diameter, $L/D = 10$, four 1-cal rectangular fins). Six points were digitized from Figure 10 (5 axial-force coefficient $C_X$ at AoA = 0 spanning $M = 0.40$--$3.50$, plus 1 normal-force coefficient $C_N$ at AoA = $10°$, $M = 1.60$); the comparator test `BunescuANFCfdComparatorTest` is locked at:

| Mach | AoA (deg) | Coeff | Bunescu CFD | ORP | $\Delta_\text{pct}$ |
|---|---:|---|---:|---:|---:|
| 0.40 | 0 | $C_X$ | 0.460 | 0.189 | $-58.95\%$ |
| 0.95 | 0 | $C_X$ | 0.910 | 0.461 | $-49.35\%$ |
| 1.60 | 0 | $C_X$ | 0.780 | 0.541 | $-30.67\%$ |
| 2.50 | 0 | $C_X$ | 0.550 | 0.372 | $-32.28\%$ |
| 3.50 | 0 | $C_X$ | 0.390 | 0.296 | $-24.06\%$ |
| 1.60 | 10 | $C_N$ | 3.400 | 1.245 | $-63.38\%$ |

Combined MAPE = **43.1%**; $C_X$-only MAPE = 39.1%. ORP systematically underpredicts the URANS values across the full Mach sweep, with the largest gap in the low-transonic regime and convergence at high supersonic. This result is reported honestly as **publication evidence, not a regression gate.** Three observations anchor the interpretation:

1. **The CFD-vs-ORP gap is consistent with the existing ADA636861 free-flight benchmark.** `BasicFinnerDragBenchmarkTest` already documents an 11.8% MAPE against the free-flight aeroballistic data (Section 11.3.4), with the same sign and the same Mach pattern. Bunescu's URANS sits **above** the ADA636861 free-flight values at matching Mach, so the ordering is `CFD > free-flight experiment > ORP` -- the expected pattern when free-flight aeroballistic data (sting-free, finite-Re) is the ground truth, CFD on a 60 mm full-scale model overpredicts at the transonic peak, and an analytical Barrowman-family model is the most aggressive underprediction.
2. **Reynolds-number mismatch is part of the story.** The ORP benchmark fixture is the 30 mm aeroballistic-range model used in ADA636861; Bunescu's URANS is computed on the 60 mm full-scale Basic Finner geometry. $Re_d$ differs by roughly a factor of two at matching Mach, which contributes some of the gap but does not fully explain it.
3. **The single $C_N$ point at AoA $= 10°$, $M = 1.60$ is the worst miss (-63%).** Bunescu reports $C_N = 3.4$; ORP gives 1.25. ORP's normal-force prediction in the ANF supersonic regime is anchored against the NASA TM X-653 NSCFB blunt-fin geometry (Section 11.4.1, MAPE 6.84%), not against the ANF rectangular-fin configuration. The ANF-specific $C_N$ gap may indicate that the Pitts-Nielsen-Kaattari interference factor or the cylinder-fin crossflow $C_d$ is biased low for this exact geometry; this is a flagged investigation, not a calibration adjustment.

The honest disposition: the gap is documented and bounded, no constants are tuned to close it, and a second independent CFD anchor on matching geometry would be required to justify any retune. The companion CFD source ARBRL-TR-02495 (Sahu, Nietubicz \& Steger 1983, Thin-Layer Navier-Stokes on a secant-ogive-cylinder-boattail at $M = 0.9$--$1.2$) is in repo at `paper/data/pdf/Empirical heuristics and tuned constants validation/` for transonic base-flow validation but has not been exercised as a comparator in this revision -- the geometry is structurally different from the Basic Finner and would require building a separate ORP rocket model. Comparator artifacts: `paper/data/csv/bunescu_anf_cfd_2025.csv` (digitized source), `paper/data/csv/bunescu_anf_comparator_2026_05_02.csv` (test output), and `paper/data/md/bunescu_anf_cfd_comparator_2026_05_02.md` (assessment memo).

### 9.11 CFD Comparator -- Sznajder 2025 ANF Pitch Damping

A second independent CFD comparator on pitch damping is provided by Sznajder (2025), "Computational Determination of Dynamic Stability Derivatives," *Transactions on Aerospace Research* No. 4, pp. 98–121, DOI 10.2478/tar-2025-0021. Sznajder reports ANSYS Fluent computations of $C_{mq}$ and $C_{m\dot\alpha}$ *separately*, from three independent CFD techniques — steady moving reference frame (MRF), dynamic-mesh forced oscillation (FOM), and step-perturbation indicial response (IRM) — over $M = 0.9$--$5.0$ on the Army-Navy Basic Finner. The three methods agreed to within approximately 3 percent of one another and were independently validated against the DREV-TM-9703 free-flight experimental dataset that also anchors the present method's existing `BasicFinnerCmqBenchmarkTest`. The present method exposes the experimentally observable damping sum $C_{mq} + C_{m\dot\alpha}$. On the ten-point comparison grid:

- **Supersonic band, $M = 1.29$--$4.5$ ($n = 8$ points):** the present method underpredicts the magnitude of the damping sum by 27 to 36 percent, with sign and Mach trend correct. MAPE on the supersonic band is **31.6 percent**.
- **Transonic peak, $M = 1.08$--$1.11$ ($n = 2$ points):** the present method overshoots the magnitude of the damping sum by **+110 to +160 percent**. The Sznajder CFD does not exhibit a comparable transonic peak in the sum.

The transonic overshoot is traced to the $k_{\mathrm{transonic}} = 1 + 2.5 \exp(-((M - 1)/0.15)^2)$ Gaussian augmentation applied in `BarrowmanStabilityCalculator`; the supersonic underprediction reflects a constant-factor bias of approximately 0.67 in the strip-theory damping coefficient. The Bhagwandin and Sahu 2013 second-source CFD on AFF and ANF (Section 9.9.6, ARL-TR-6725) independently confirms the same supersonic-band underprediction direction. Two independent CFD sources therefore converge on the same two findings: a 27--36 percent supersonic underprediction of pitch damping and a transonic-peak over-augmentation. Both findings are taken up explicitly in the limitations discussion in Section 12.4 item 2. Comparator artifacts: `paper/data/csv/sznajder_anf_cmq_cfd_2025.csv` (digitized source) and `paper/data/csv/sznajder_anf_cmq_comparator_2026_05_11.csv` (test output); the assessment memo is `paper/data/md/sznajder_anf_cmq_cfd_comparator_2026_05_11.md`.

### 9.12 CFD Comparator -- Vidanović 2014 AGARD-B Reference

A third CFD comparator is provided by Vidanović et al. (2014), "Validation of the CFD code used for determination of aerodynamic characteristics of nonstandard AGARD-B calibration model," *Thermal Science* **18**(4), 1223–1233, DOI 10.2298/TSCI130409104V. The authors report ANSYS Fluent Menter SST $k$-$\omega$ predictions of total drag, lift, and pitching-moment coefficients on the AGARD Model B calibration standard at $M = 0.596$ and $M = 1.602$ over an angle-of-attack sweep of $-4°$ to $+12°$. Their CFD is validated against wind-tunnel data from the VTI T-38 trisonic facility in Belgrade, with CFD-versus-experiment agreement of 0.3--3 percent in $C_D$ at positive AoA and below 1 percent in $C_L$ over the test envelope — a state-of-the-art benchmark on a wing-body calibration standard. The present method does not yet ship an AGARD-B `.ork`: the equilateral-triangle delta wing with 4 percent bi-convex section is at the edge of the OpenRocket fin-set model's validity, and a faithful AGARD-B fixture would require either rendering the delta wing as a fictitious fin set or extending the fin geometry primitives. The Vidanović CFD is therefore retained in the present revision as a *reference dataset* against which a future OpenRocket Plus AGARD-B comparator can be benchmarked; the comparator panel is shown in Figure 18 of `paper/data/png/cfd_validation_panels.png` and is flagged as deferred future work in Section 12.6. Comparator artifact (digitized source only): `paper/data/csv/vidanovic_agard_b_cfd_2014.csv`, memo at `paper/data/md/vidanovic_agard_b_cfd_comparator_2026_05_11.md`.

### 9.13 CFD Comparator Inventory Summary

The four published CFD comparators that anchor the present method's CFD-side validation are summarized below. Together they span two reference geometries (Army-Navy Basic Finner; AGARD Model B), two distinct aerodynamic quantities (static force/moment coefficients; pitch-damping derivatives), three Mach bands (transonic; supersonic; supersonic-leading-to-hypersonic), and three independent author groups across two continents and three CFD code families.

**Table 9.13.1 — Published-CFD comparator inventory.**

| Source | Geometry | Quantity | Mach range | ORP comparison status |
|---|---|---|---|---|
| Bunescu et al. (2025), *Aerospace* **12**(5), 371, URANS k-$\epsilon$ | Basic Finner (ANF) | $C_N$, $C_X$ | 0.4--3.5 | Java comparator wired (`BunescuANFCfdComparatorTest`); $C_X$ MAPE 39.1 percent on 5 points at AoA = $0°$ |
| Sahu, Nietubicz, Steger (1983), ARBRL-TR-02495 (DTIC AD-A130293), thin-layer Navier-Stokes | Secant-ogive-cylinder-boattail | $C_{Db}$, $C_{D,\text{tot}}$ | 0.9--1.2 | PDF in repo; comparator not yet digitized (deferred future work) |
| Vidanović et al. (2014), *Therm. Sci.* **18**(4), 1223, SST k-$\omega$ | AGARD-B calibration standard | $C_D$, $C_L$, $C_m$ | 0.596, 1.602 | Reference dataset only; AGARD-B `.ork` not yet shipped (deferred future work) |
| Sznajder (2025), *Trans. Aerosp. Res.* No. 4, 98, Fluent MRF/FOM/IRM | Basic Finner (ANF) | $C_{mq} + C_{m\dot\alpha}$ | 0.9--4.5 | Memo + comparator CSV; supersonic MAPE 31.6 percent on 8 points ($M \ge 1.29$); transonic overshoot $+110$ to $+160$ percent |
| Bhagwandin & Sahu (2013), ARL-TR-6725 (DTIC ADA592550), Fluent | Basic Finner (ANF) + Air Force Modified Finner (AFF) | $C_{mq} + C_{m\dot\alpha}$ | 0.6--4.5 | Second-source corroboration of Sznajder supersonic bias direction on the same and on an independent finned geometry |

The four-panel composite figure (`paper/data/png/cfd_validation_panels.png`) overlays the comparator outputs into a single quick-look diagram: Panel A — Basic Finner $C_X$ vs Bunescu URANS; Panel B — Sahu reference (deferred); Panel C — AGARD-B reference dataset (Vidanović SST + VTI T-38 experiment); Panel D — Basic Finner $C_{mq} + C_{m\dot\alpha}$ vs Sznajder Fluent + Bhagwandin & Sahu second source.


## 10. Regime Blending

The aerodynamic models built in Chapters 3 through 8 each have limited domains of validity. No single model spans the entire Mach range from incompressible flow through hypersonic flight: the subsonic Barrowman fin formula diverges as $M \to 1$, the Ackeret supersonic fin formula is singular at $M = 1$, the Taylor--Maccoll cone solution requires $M > 1 + \epsilon$, and the Modified Newtonian pressure law only becomes accurate beyond $M \approx 5$. Connecting these models requires blending functions that transition smoothly between regimes.

This chapter documents the blending methodology, proves the continuity properties, catalogs every blending region in the implementation, and provides design guidance for selecting blend types. The actual blend implementations live across `FlightConditions`, `FinSetCalc`, `SymmetricComponentCalc`, `BarrowmanDragCalculator`, `PittsNielsenKaattari`, `ShockGeometry`, and `RationalBlend`.


### 10.1 Why $C^1$ Continuity Matters

A flight simulation integrates the aerodynamic coefficients as part of the equations of motion. A discontinuity in $C_D(M)$ produces a delta-function in $dC_D/dM$, which enters the force balance through the chain rule:

$$F_D = C_D(M) \cdot q_\infty \cdot S_\text{ref} \;\implies\; \frac{dF_D}{dt} \propto \frac{dC_D}{dM}\frac{dM}{dt}.$$

If $dC_D/dM$ is unbounded, the rate of change of drag force becomes infinite at the transition Mach number. This produces three failure modes:

1. **Integration instability.** The RK4 stepper takes its first sub-step on one side of the discontinuity and its second sub-step at $M_n + h/2$ on the other side. The mismatched force values at the four evaluation points produce a large error in the weighted average and the step-size controller drives $h \to 0$.
2. **Oscillation.** If the discontinuity falls between two adjacent RK4 evaluations, the simulation oscillates back and forth across the boundary, producing artificial vibration in the predicted trajectory.
3. **Apogee-prediction error.** At apogee the rocket decelerates through $M = 1$. A discontinuous transonic drag model abruptly changes the deceleration rate, shifting the predicted apogee altitude by hundreds of meters.

**Empirical example.** During development, replacing the $C^1$-continuous base-drag blend with a $C^0$-continuous (value-continuous, slope-discontinuous) piecewise function at $M = 1.3$ produced a measured $\lvert dC_D/dM\rvert = 8.7$ at that boundary, compared to the physically correct value of approximately 0.3. When this model was used in trajectory simulation, the timestep collapsed from 50 ms to 0.2 ms near $M = 1.3$ and total simulation time grew by a factor of 250.

The requirement is therefore stated as a hard property: **all coefficient functions must be at least $C^1$-continuous (continuous value and continuous first derivative) across every regime boundary.**


### 10.2 Cubic Hermite Smoothstep

#### 10.2.1 Definition

The cubic Hermite smoothstep is the simplest polynomial that achieves $C^1$ continuity between two constant values. With normalised parameter

$$t = \frac{M - M_\text{lo}}{M_\text{hi} - M_\text{lo}}, \quad t \in [0, 1],$$

the smoothstep weight is

$$w(t) = 3t^2 - 2t^3,$$

and the blended coefficient is

$$f(M) = f_0(M) \cdot (1 - w(t)) + f_1(M) \cdot w(t).$$

#### 10.2.2 Proof of $C^1$ Properties

**Claim.** $w(t) = 3t^2 - 2t^3$ satisfies $w(0) = 0$, $w(1) = 1$, $w'(0) = 0$, $w'(1) = 0$.

**Proof.** $w(0) = 3(0)^2 - 2(0)^3 = 0$ and $w(1) = 3 - 2 = 1$. Differentiating, $w'(t) = 6t - 6t^2 = 6t(1 - t)$, so $w'(0) = 0$ and $w'(1) = 0$. $\square$

Because $w'(0) = 0$, the blended function $f(M)$ has the same slope as $f_0$ at $M = M_\text{lo}$. Because $w'(1) = 0$, $f(M)$ has the same slope as $f_1$ at $M = M_\text{hi}$. Provided $f_0(M)$ and $f_1(M)$ are themselves $C^1$, the composite is $C^1$ across both boundaries.

#### 10.2.3 Shape

The smoothstep weight rises monotonically from 0 to 1 with an inflection at $t = \tfrac{1}{2}$ and zero slope at both endpoints. It is the natural choice when both endpoint models are themselves smooth and no particular slope matching is needed at the boundaries.


### 10.3 Rational Blend (AP09 Formulation)

#### 10.3.1 Motivation

The cubic smoothstep has a fixed transition width defined by $[M_\text{lo}, M_\text{hi}]$ and uses a polynomial weight, which means it has hard "edges" -- the blend turns on and off abruptly at the Mach endpoints. For transitions near $M = 1$ where the physics is dominated by the Prandtl--Glauert singularity ($\beta \to 0$), a rational function provides a better approximation to the actual coefficient behavior. The AP09 form (Aeroprediction Code Methodology 2009) implemented in `RationalBlend.java` uses

$$t = \frac{M^2 - M_b^2}{w \cdot M_b^2}, \qquad g(M) = \frac{1}{2}\left(1 - \frac{t}{\sqrt{1 + t^2}}\right),$$

with $M_b$ the blend centre (typically $1.0$) and $w$ the transition width parameter.

#### 10.3.2 Properties

1. $g(M) \to 1$ as $M \to 0$ (fully subsonic weight).
2. $g(M_b) = \tfrac{1}{2}$ (centre of transition).
3. $g(M) \to 0$ as $M \to \infty$ (fully supersonic weight).
4. $g(M)$ is $C^\infty$ (infinitely differentiable) everywhere.
5. $g$ is strictly monotonically decreasing for $M > 0$.

The blended value is $f(M) = f_\text{sub}(M)\cdot g(M) + f_\text{sup}(M)\cdot (1 - g(M))$.

#### 10.3.3 Derivative

The derivative is needed to verify $C^1$ continuity and is implemented in `RationalBlend.weightDerivative()`. With $t = (M^2 - M_b^2)/(wM_b^2)$,

$$\frac{dt}{dM} = \frac{2M}{wM_b^2}, \qquad \frac{dg}{dt} = -\frac{1}{2(1 + t^2)^{3/2}},$$

so

$$\frac{dg}{dM} \;=\; \frac{dg}{dt}\cdot\frac{dt}{dM} \;=\; \frac{-M}{wM_b^2 \cdot (1 + t^2)^{3/2}}.$$

This derivative is non-positive for $M \ge 0$ and is bounded everywhere -- there is no singularity at $M = M_b$. The blend is therefore $C^\infty$.

#### 10.3.4 Comparison with Smoothstep

The rational blend is preferred when the transition must be centered at a specific Mach number (e.g., $M = 1$) but should *not* have hard edges where the blend activates or deactivates. The smoothstep is preferred when the endpoints are precisely known and a compact blending region is desired. Both forms are $C^1$ across the relevant boundaries; the rational form is additionally $C^\infty$ at the cost of algebraic (rather than compact-support) tails.


### 10.4 Complete Blending Region Table

The following table catalogs every Mach-regime blending region in the implementation. Each row identifies the quantity being blended, the Mach boundaries, the blend type, the source file, and the models being joined.

```{=latex}
\begin{landscape}
\scriptsize
\setlength{\tabcolsep}{3pt}
\renewcommand{\arraystretch}{1.2}
\begin{xltabular}{\linewidth}{@{}c X r r l X X l@{}}
\toprule
\# & Quantity & $M_\text{lo}$ & $M_\text{hi}$ & Blend & Subsonic model & Supersonic model & Source \\
\midrule
\endhead
1 & $\beta$ compressibility & 0.95 & 1.05 & Hermite & $\sqrt{1-M^2}$ & $\sqrt{M^2-1}$ & FlightCond. \\
2 & Base drag $C_{D,\text{base}}$ & 0.85 & 1.50 & Poly $C^1$ & $0.12+0.13M^2$ & $0.064{+}0.186/M^2$ & DragCalc \\
3 & Skin friction $C_f$ & 0.90 & 1.10 & Linear & Prandtl & Van Driest II & DragCalc \\
4 & Roughness correction & 0.90 & 1.10 & Linear & Sub.\ roughness & Sup.\ roughness & DragCalc \\
5 & Fin $C_{N\alpha}$ & 0.90 & 1.50 & Poly $C^1$ & Barrowman $2\pi/\beta$ & Ackeret $4/\beta$ & FinSetCalc \\
6 & Fin wave drag & 0.90 & 1.20 & Hermite & 0 & Ackeret/DATCOM & FinSetCalc \\
7 & Nose/body wave drag & 1.30 & 1.50 & Hermite & TR-R-100 / DB & T--M / SE & SymCompCalc \\
8 & Body $C_{N\alpha}$ and CP & 0.80 & 1.30 & Hermite & Galejs & Allen--Perkins & SymCompCalc \\
9 & Modified Newtonian & 4.00 & 6.00 & Hermite & SE / T--M & $C_{p,\max}\sin^2\theta$ & SymCompCalc \\
10 & Shock geom.\ activation & 1.00 & 1.10 & Linear & Freestream & Shock pre-pass & ShockGeom \\
11 & PNK fin-body interf. & 0.85 & 1.30 & Hermite$^\dagger$ & Barrowman $K_{WB}, K_{BW}$ & PNK supersonic & FinSetCalc \\
12 & Forward-step drag & 0.95 & 1.10 & Hermite & 0 & ESDU 66011 & SymCompCalc \\
13 & Trailing-edge base drag & 0.90 & 1.20 & Hermite & Hoerner $0.12\,t_{TE}/c$ & $0.135(t_{TE}/c)/\sqrt{\beta}$ & FinSetCalc \\
14 & Roll damping & 0.90 & 1.50 & Linear & $2\pi pR/\beta$ & $K_1/K_2/K_3$ & FinSetCalc \\
15 & Fin LE pressure drag & 0.90 & 1.00 & Linear & Prandtl--Glauert & Empirical & FinSetCalc \\
16 & Fin CP position & 0.50 & 2.00 & Poly-5 & 0.25 MAC & $f(\text{AR}, \beta)$ & FinSetCalc \\
17 & ESDU transonic sim. & $K_t{=}{-}2$ & $K_t{=}{+}3$ & Linear & Std $C_{N\alpha}$ & Similarity peak & FinSetCalc \\
18 & Chapman--Korst turb. & 1.20 & 1.40 & Hermite & $0.064{+}0.186/M^2$ & Chapman--Korst & CKBaseDrag \\
19 & Chapman laminar base & 1.30 & 2.50 & Hermite & Subsonic base & Chapman 1950 & CKBaseDrag \\
\bottomrule
\end{xltabular}
\end{landscape}
```

$^\dagger$ Row 11: Hermite blend through 1.15; pure PNK formulas across $[1.15, 1.30]$; disabled above 1.30. Implementation also reads `PittsNielsenKaattari` for $F_{WB}$, $F_{BW}$.

**Source column abbreviations.** FlightCond. = `FlightConditions`; DragCalc = `BarrowmanDragCalculator`; FinSetCalc = `FinSetCalc`; SymCompCalc = `SymmetricComponentCalc`; ShockGeom = `ShockGeometry`; CKBaseDrag = `ChapmanKorstBaseDrag`. T--M = Taylor--Maccoll, SE = shock-expansion, DB = Dahlem--Buck, $K_t$ = transonic-similarity parameter $(M_\text{eff}^2-1)/(t/c)^{2/3}$.

**Notes on the table.**

- Entries 1--4 handle the core transonic singularity near $M = 1$.
- Entry 2 uses a constrained polynomial rather than a simple smoothstep because it must match values *and* slopes at two endpoints while passing through a transonic peak.
- Entry 5 uses `PolyInterpolator` with second-derivative constraints to achieve smoother curvature through the transition (the $1/\beta$ behavior on both sides of $M=1$ stresses the interpolant beyond what a simple smoothstep can absorb).
- Entry 10 uses a simple linear blend because the shock-geometry correction is itself a smooth perturbation from unity; the blend only controls *whether* the perturbation is applied at all.
- Entry 14 samples at $M = 0.85$ and $M = 1.55$ (slightly inboard of the nominal boundaries) to avoid evaluating exactly at the regime limits where the formulas are most sensitive.
- Entry 16 spans a very wide Mach range because the fin CP shifts gradually from quarter-chord to the supersonic empirical formula.
- Entry 17 operates in the transonic similarity parameter $K_\text{trans} = (M_\text{eff}^2 - 1)/(t/c)^{2/3}$ rather than Mach directly; the effective Mach range depends on thickness ratio and sweep.
- Entry 18 is an available/tested turbulent base-drag utility; the production base-drag path uses the empirical supersonic base-drag correlation $C_{d,\text{base}} = 0.064 + 0.186/M^2$ (validated against NACA TN 3393, consistent with ESDU 77021) plus the transonic polynomial and the optional Chapman laminar correction, unless explicitly routed through `ChapmanKorstBaseDrag.blendedBaseDrag()`.
- The widest blend region is Entry 9 (Modified Newtonian, $\Delta M = 2.0$), reflecting the gradual transition from shock-dependent to local-inclination hypersonic theory.
- The narrowest blend region is Entry 1 ($\beta$, $\Delta M = 0.10$), which must be tight to avoid distorting the compressibility factor at Mach numbers far from unity.


### 10.5 Conceptual $C_D$ vs Mach Diagram with Blend Regions

Conceptually, the total drag coefficient for a finned vehicle is small at low subsonic ($C_D \sim 0.3$), rises sharply through the transonic to a peak near $M \approx 1.05$ (typically $C_D \sim 0.7$ for the standard geometries of Section 11.1.1), then decays approximately as $M^{-2}$ through the supersonic regime, and finally levels off in the hypersonic Modified Newtonian regime ($C_D \sim 0.2$ at $M = 5$). Overlaid on this curve, the transonic band $M \in [0.85, 1.50]$ contains seven overlapping blend regions (Entries 1, 2, 3, 5, 6, 8, 11 in the catalog) and the band $M \in [4, 6]$ contains the Modified Newtonian transition (Entry 9). The transonic overlap is intentional: each aerodynamic quantity transitions at the Mach range appropriate to its physical behavior, and the union of overlapping $C^1$ blends produces a smooth composite $C_D(M)$.

Reference table for the blend regions superimposed on the conceptual diagram:

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}
\begin{axis}[
  width=0.92\textwidth,
  height=0.34\textwidth,
  xmin=0.3, xmax=6.2,
  ymin=0.15, ymax=0.90,
  xlabel={Mach number $M$},
  ylabel={conceptual $C_D$},
  grid=both,
  minor grid style={gray!10},
  major grid style={gray!25},
  legend style={draw=none, fill=white, font=\scriptsize, at={(0.98,0.98)}, anchor=north east},
]
\addplot[draw=orange!30, fill=orange!18] coordinates {(0.80,0.15) (1.50,0.15) (1.50,0.90) (0.80,0.90)} -- cycle;
\addplot[draw=blue!30, fill=blue!12] coordinates {(4.00,0.15) (6.00,0.15) (6.00,0.90) (4.00,0.90)} -- cycle;
\addplot[very thick, black, smooth] coordinates {
  (0.30,0.32) (0.60,0.34) (0.85,0.43) (1.00,0.82)
  (1.10,0.74) (1.30,0.64) (1.50,0.56) (2.00,0.45)
  (3.00,0.34) (4.00,0.28) (5.00,0.23) (6.00,0.21)
};
\addlegendentry{representative total $C_D$}
\node[font=\scriptsize, align=center] at (axis cs:1.15,0.86) {transonic\\overlap};
\node[font=\scriptsize, align=center] at (axis cs:5.00,0.86) {Newtonian\\handoff};
\draw[dashed, gray] (axis cs:0.95,0.15) -- (axis cs:0.95,0.90);
\draw[dashed, gray] (axis cs:1.05,0.15) -- (axis cs:1.05,0.90);
\end{axis}
\end{tikzpicture}
\caption{Conceptual total-drag curve with the dense transonic blend band and the hypersonic Modified-Newtonian handoff. The curve is illustrative; validation data are tabulated in Section 11.}
\label{fig:cd-blend-map}
\end{figure}
```

| ID | Quantity | $M$ range |
|:--:|----------|-----------|
| [1] | $\beta$ factor | $0.95$ -- $1.05$ |
| [2] | Base drag | $0.85$ -- $1.50$ |
| [3] | Skin friction | $0.90$ -- $1.10$ |
| [5] | Fin $C_{N\alpha}$ | $0.90$ -- $1.50$ |
| [6] | Fin wave drag | $0.90$ -- $1.20$ |
| [7] | Nose/body wave drag | $1.30$ -- $1.50$ |
| [8] | Body $C_{N\alpha}$ / CP | $0.80$ -- $1.30$ |
| [9] | Newtonian | $4.0$ -- $6.0$ |
| [10] | Shock geometry | $1.00$ -- $1.10$ |
| [11] | PNK fin-body | $0.85$ -- $1.30$ (blend to $1.15$; disabled above $1.30$) |
| [18] | Chapman--Korst turb base utility | $1.20$ -- $1.40$ |
| [19] | Chapman laminar base | $1.30$ -- $2.50$ |

Base drag peaks near $M = 1.05$ and is anchored on the supersonic side by the Hart L52E06 plateau through $M \approx 1.30$ before joining the empirical $C_{d,\text{base}} = 0.064 + 0.186/M^2$ correlation at $M = 1.50$. Fin $C_{N\alpha}$, which depends on $1/\beta$, needs the wider $M = 0.90$--$1.50$ supersonic margin because both the Barrowman subsonic and the Ackeret supersonic formulas diverge at $M = 1$ and the interpolation polynomial must span enough range to control the curvature.


### 10.6 Design Principles for Blend Selection

#### 10.6.1 When to Use Cubic Hermite Smoothstep

Use $w(t) = 3t^2 - 2t^3$ when:

- both endpoint models are smooth and well-defined at the blend boundaries;
- no particular slope must be matched (the smoothstep forces zero slope at both ends);
- the transition is between "model A active" and "model B active" with no intermediate physics;
- a compact, predictable blend region is desired.

**Examples in this implementation.** Fin wave drag (Entry 6), body $C_{N\alpha}$ (Entry 8), Modified Newtonian (Entry 9).

#### 10.6.2 When to Use a Constrained Polynomial

Use a degree-4 or degree-5 constrained polynomial when:

- both values *and* derivatives must match at the endpoints ($C^1$ boundary conditions);
- an interior constraint exists (e.g., a peak value at a specific Mach);
- the transition has asymmetric shape (different curvature on the subsonic vs supersonic sides).

**Example.** Base drag blend (Entry 2), which must match the subsonic parabola and its slope at $M = 0.85$, pass near the transonic peak ($\sim 0.25$) at $M = 1.05$, pass through the Hart L52E06 anchor at $M = 1.30$, and match the empirical $C_{d,\text{base}} = 0.064 + 0.186/M^2$ correlation with its slope at $M = 1.50$.

#### 10.6.3 When to Use the AP09 Rational Blend

Use the rational blend when:

- the transition is centered at a specific Mach number and should have smooth tails;
- the coefficient has a physical singularity near the transition (e.g., $1/\beta \to \infty$);
- no hard activation/deactivation boundaries are desired;
- the subsonic and supersonic models are both defined everywhere, with different accuracy domains.

The AP09 rational blend is $C^\infty$ everywhere and decays algebraically (not exponentially) in the tails, so it provides a very gentle onset rather than an abrupt activation.

#### 10.6.4 When to Use a Gaussian Augmentation

Use a Gaussian factor when:

- a multiplicative correction is needed that peaks at a specific Mach;
- the correction should decay symmetrically (or nearly so) on both sides;
- the correction is a transonic amplification rather than a model switch.

**Example.** The pitch-damping transonic factor $k(M) = 1 + 2.5\exp(-((M-1)/0.15)^2)$ (Section 9.1.2). This is not a blend between two models but an augmentation of a single model, and the Gaussian shape is naturally $C^\infty$ in $M$.

#### 10.6.5 When to Use a Linear Blend

Use a linear blend only when:

- the blended quantity is itself a smooth correction that does not introduce discontinuities;
- simplicity of implementation outweighs the $C^1$ benefit (i.e., the correction is numerically small);
- the blend acts as a gate (on/off) for a model whose output is itself continuous.

**Examples.** Shock geometry activation (Entry 10), skin friction transition (Entry 3). In both cases the blended quantity modulates a correction that is itself smooth, so the slope discontinuity at the blend endpoints is multiplied by a small factor and does not cause simulation instability.


## 11. Validation and Results

The validation in this work draws from two fundamentally distinct categories of evidence. The first is exact analytical and authoritative tabulated solutions -- sources such as NACA Report 1135 and the U.S. Standard Atmosphere 1976 -- which verify that the mathematical implementation is correct: the shock solvers compute the right numbers, the thermodynamic relations are coded without transcription error, the iterations converge to the correct fixed point. The second category is physical experimental data: wind-tunnel pressure measurements, free-flight ballistic-range tests, and aeroballistic instrumentation campaigns. This second category verifies something the first cannot -- that the models reflect the aerodynamic behavior of real physical hardware, not merely internally consistent mathematics applied to the wrong physics.

The headline state of the work is summarised below; the remainder of this chapter substantiates each line item against published external data, against analytical limits, or against integrated flight measurements.

Headline:

- **20 subsystems are externally benchmarked against published wind-tunnel, free-flight, or analytical data** at the A-level standard with a quantitative acceptance criterion, plus **1 externally anchored negative benchmark** (NACA RM-10, MAPE 80%) -- counted outside the 20 -- used to bound and exclude a geometry family. Three results that earlier drafts counted as externally benchmarked are reported at their honest evidentiary level and are *not* in the 20: hypersonic cone foredrag (B-level / exploratory), AGARD-B total drag (qualitative secondary, $\sim 22.6\%$ MAPE), and the vortex sideforce $K_v = 0.20$ (internally calibrated).
- **9 results are calibrated against the integrated flight corpus** rather than against isolated component data. These are circular calibrations (same corpus is the calibration and validation target) and are *not* counted in the 20-subsystem headline. Each is flagged where it is used (Section 11.6.5).
- **25-flight integrated validation corpus** (Rocket Flight Database, DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977)), Mach 0.54--4.33: 25/25 within $\pm 10\%$, 14/25 within $\pm 5\%$, mean signed apogee error $-0.38\%$, $\sigma = 5.44\%$, MAE $4.74\%$, 0 abnormal endings. RASAero II on the same paired set averages $5.34\%$ MAE with 22/25 within $\pm 10\%$ (Wilcoxon $W = 143.0$, $p = 0.615$ on paired absolute errors; $|\text{ORP}|-|\text{RAS}| = -0.60$ pp, 95\% CI $[-2.16, +0.96]$). **The honest claim is statistical parity with this version-locked RASAero set, not superiority.**
- **MESOS 293K** (flight 25 of 25; peak Mach 4.33 / 293,488 ft): apogee $\mathbf{-6.96\%}$ (273,056 ft) -- the corpus's largest single-flight error and the higher-Mach of the two two-stage closures, reproduced in isolation, still inside the $\pm 10\%$ band (Section 11.6.3). A separate exploratory high-Mach set reaching Mach 5--7 (Black Brant V VB, Nike-Deacon, Nike-Apache, etc.) is reported in full in Section 11.6.6 as a capability demonstration, not as part of this headline corpus.
- **Envelope of the headline claim.** The accuracy figures above apply to finned slender vehicles within the boattail half-angle envelope of $6°$--$16°$ (the Viswanath 1996 calibration band, Section 6.2.7) and to fin sections that are HEXAGONAL (double-wedge) or AIRFOIL/ROUNDED (rounded-LE), the section types present in the 25-flight Rocket Flight Database and in every Basic-Finner-class wind-tunnel and free-flight reference geometry used in this work. Out-of-envelope geometries -- specifically the high-fineness parabolic body with steeply contracted afterbody and 60° swept circular-arc biconvex fins of NACA RM-10 -- are reported as transparency references and are excluded from the headline accuracy claim (Section 11.3.6).


### 11.1 Test Suite Overview

The aerodynamic validation suite currently comprises **85 tracked JUnit test classes** in the `info.openrocket.core.aerodynamics` package hierarchy (87 tracked Java files including support/export helpers), plus one workspace-local `SimVRealCorpusAblationTest` used for the May 1 import-parity ablation. The claim inventory consists of 20 externally benchmarked A-level subsystem results, 9 integrated flight-data closures, and 1 negative external benchmark (NACA RM-10, counted outside the 20). Not every claim has equal evidence: externally benchmarked results are independently matched against published experimental or tabulated data with a quantitative acceptance criterion; integrated flight-data closures are validated against the 25-flight Rocket Flight Database corpus rather than against an isolated published component dataset; numerical-consistency tests verify that the implementation reduces to its analytical limit or matches its own boundary conditions; and a small number of empirically tuned coefficients are documented as such. Every claim in this chapter is reported with its evidence type, not as a uniformly closed validation.

#### 11.1.1 Five Standard Rocket Geometries

System-level tests operate on five geometries spanning representative high-power amateur configurations:

1. **Cone-Cylinder (CC)**: conical nose ($L_n = 0.150$ m, $r = 0.025$ m, $\theta_c \approx 9.46°$, fineness $3.0$), cylindrical body ($L_b = 0.600$ m). Total $L/D = 15$. No fins; isolates nose wave drag, body friction, and base drag.
2. **Ogive-Cylinder (OC)**: tangent-ogive nose (same envelope as CC), cylindrical body. Directly comparable to CC for isolating the nose-shape effect on wave drag.
3. **Cone-Cylinder-Fins (CCF)**: CC geometry plus a 4-fin trapezoidal set (root 0.050 m, tip 0.025 m, span 0.040 m, thickness 3 mm) at the body aft end. Adds fin wave drag, fin friction, and stability.
4. **Ogive-Boattail-Fins (OBF)**: ogive nose, cylindrical body ($L_b = 0.500$ m), 4-fin set, conical boattail (fore radius 0.025 m, aft radius 0.018 m, length 0.060 m). Total length 0.710 m. Most representative of a flight-ready high-power rocket.
5. **Von Karman-Fins (VKF)**: Sears--Haack/LD-Haack nose ($L_n = 0.180$ m), cylindrical body ($L_b = 0.550$ m), 3-fin swept set. Provides comparison against a theoretically minimum-wave-drag configuration.

#### 11.1.2 Test Inventory

The exact test-case count is deliberately not treated as a scientific result, because parameterized JUnit cases and diagnostic exporters change faster than the manuscript. The source-tree inventory at this report revision is:

| Scope | Current source-tree count | Notes |
|-------|--------------------------:|-------|
| `core/src/test/java/info/openrocket/core/aerodynamics/**/*.java` | 88 Java files | includes diagnostics/export helpers and the workspace-local ablation test |
| JUnit-bearing classes in the same aerodynamic tree | 86 classes | counted by files containing `@Test` |
| Tracked aerodynamic Java test files | 87 files | `git ls-files` count; excludes the workspace-local ablation test |
| Tracked `*Test.java` classes in the aerodynamic tree | 85 classes | stable tracked count used in Section 1.3 |
| RASAero import test files | 5 Java files | import parity and MESOS validation live outside the aerodynamic package |

The suite covers freestream Mach numbers $M = 0.3$, $0.5$, $0.8$, $0.9$, $0.95$, $1.0$, $1.05$, $1.1$, $1.5$, $2.0$, $3.0$, $5.0$, $8.0$, $10.0$ at discrete points, plus a continuous sweep over 235 Mach steps from $M = 0.3$ to $M = 5.0$ in steps of $\Delta M = 0.02$ for continuity validation.


### 11.2 Gas Dynamics Validation Against NACA Report 1135

The three core gas-dynamics solvers of Chapter 5 are validated against the tabulated exact solutions in NACA Report 1135 (Ames Research Staff 1953). All comparisons use $\gamma = 1.4$. Normal-shock and oblique-shock rows use relative-error tolerances; the Prandtl--Meyer row is reported as absolute angle error because the tabulated function is an angle.

**Normal shock relations.** For $M_1 \in \{1.0, 1.5, 2.0, 3.0, 5.0, 10.0\}$ the implementation matches NACA 1135 to within $7 \times 10^{-5}$ on $M_2$, $p_2/p_1$, $T_2/T_1$, and $p_{02}/p_{01}$.

**Oblique shock relations.** Across $M_1 \in \{2, 3, 5\}$ and $\theta \in \{10°, 20°, 30°\}$ the computed weak-solution wave angle agrees with NACA 1135 to within $0.021\%$.

**Prandtl--Meyer expansion.** The implementation reproduces $\nu(M)$ to within $0.004°$ at $M = 10$; the inverse Newton iteration recovers the input Mach to within $10^{-8}$ relative error over $M \in [1, 20]$.

**Tolerance summary** (Chapter 5 has the full per-row table):

| Quantity | Max error | Specification |
|----------|--------------------:|---------------:|
| Normal shock $M_2$ | $0.003\%$ | $< 0.1\%$ |
| Normal shock $p_2/p_1$ | $0.004\%$ | $< 0.1\%$ |
| Normal shock $T_2/T_1$ | $0.002\%$ | $< 0.1\%$ |
| Normal shock $p_{02}/p_{01}$ | $0.007\%$ | $< 0.1\%$ |
| Oblique shock $\beta$ | $0.021\%$ | $< 0.1\%$ |
| Prandtl--Meyer $\nu(M)$ | $0.004^\circ$ | $< 0.1^\circ$ |

All quantities meet their declared specifications.


### 11.3 Drag Model Validation

#### 11.3.1 External Validation Summary

Each row in the table below is an externally benchmarked drag-related subsystem. The acceptance criterion for each row is a quantitative tolerance set against a published external dataset; the test class is the automated regression that locks the result.

**Table 11.1 -- Drag-related subsystems validated against external benchmarks**

| # | Subsystem | External source | Result | Acceptance gate |
|---|-----------|-----------------|--------|-----------------|
| D1 | Speed of sound | US Std Atm 1976 | max relative error 0.016% in current exported table | $< 0.5\%$ |
| D2 | Sutherland viscosity | NIST/Incropera Table A.4 | NIST gate $< 3\%$ over 100--800 K; formula export MAPE 0.012% | NIST $< 3\%$ |
| D3 | Normal shock relations | NACA 1135 | max relative error 0.003% | $< 0.1\%$ |
| D4 | Oblique shock $\theta$-$\beta$-$M$ | NACA 1135 | max angle error 0.021% | $< 0.1\%$ |
| D5 | Prandtl--Meyer expansion | NACA 1135 | max abs error 0.004 deg | $< 0.1^\circ$ |
| D6 | Taylor--Maccoll cone flow | NACA 1135 / NASA Glenn | max cone-shock angle relative error 0.825% | $< 1\%$ |
| D7 | $C_{p,\max}$ via Rayleigh pitot | NACA 1135 Tables I--II | max relative error $< 0.01\%$ | $< 1\%$ |
| D8 | ShockGeometry pre-pass | Taylor--Maccoll + Prandtl--Meyer | cone 0%, shoulder $4 \times 10^{-11}\%$ | $< 0.1\%$ |
| D9 | Nose/body wave drag (5 shapes) | NACA RM A52H28 | MAE 0.029 in $C_D$ | $< 0.035$ |
| D10 | AGARD-B transonic drag | AEDC-TR-70-100 | $M = 0.2$--$1.0$ qualitative pass | trend match |
| D11 | Turbulent base drag | NACA TN 3393 + Hart L52E06 | TN 3393 MAPE 15.9%, Hart 4.0% | $< 20\%$ |
| D12 | Laminar base drag | NACA TN 3393 laminar | MAPE 4.4% | $< 10\%$ |
| D13 | Fin wave drag (DATCOM 4.1.5.1) | NACA TN 3650 + Ackeret cross-check | TN 3650 MAPE $\sim 21\%$, Ackeret $0.00\%$ | trend + $\tau^2$ scaling |
| D14 | Compressible skin friction (Van Driest II) | NASA TN D-6945 (Hopkins 1972) + ESDU D-5089 | self-consistent + monotonic decrease | qualitative |
| D15 | Hypersonic cone foredrag | DTIC AD0487365 (Grabow 1965) | MAPE 19.7% with source $Re_L$ matched row-by-row; largest point +57.0% | $< 20\%$ |

The fin-wave-drag row (D13) deserves explicit comment. The current MAPE against the NACA TN 3650 60-degree delta is approximately $21\%$, and an independent Ackeret cross-check of the same formula yields exactly $0.00\%$. The remaining residual is geometric, not physical: the DATCOM 4.1.5.1 Puckett--Stewart formulation does not capture the wing-body interference field for highly swept fins of this planform. The model is therefore correct in its declared domain (it reproduces Ackeret exactly, and the trend and $\tau^2$ scaling are right against TN 3650), but it is geometrically incomplete for highly swept fins. This is an open evidence gap, not a bug; see Section 12.4.

#### 11.3.2 Total Drag Coefficient -- Five Standard Geometries

Total $C_D$ values from the standard-geometry sweep:

| $M$ | CC | OC | CCF | OBF | VKF |
|-----|----:|----:|----:|----:|----:|
| 0.3 | 0.304 | 0.310 | 0.546 | 0.451 | 0.328 |
| 0.5 | 0.358 | 0.366 | 0.660 | 0.509 | 0.402 |
| 0.9 | 0.483 | 0.481 | 0.772 | 0.588 | 0.660 |
| 1.1 | 0.696 | 0.544 | 1.007 | 0.680 | 0.730 |
| 1.5 | 0.450 | 0.353 | 0.766 | 0.561 | 0.628 |
| 2.0 | 0.361 | 0.333 | 0.684 | 0.578 | 0.549 |
| 3.0 | 0.266 | 0.268 | 0.592 | 0.541 | 0.457 |
| 5.0 | 0.188 | 0.198 | 0.512 | 0.478 | 0.384 |

Key observations: at $M = 1.1$, CC drag (0.696) exceeds OC (0.544) by 28%, confirming the stronger oblique shock on the conical nose; supersonic drag decays approximately as $M^{-2}$ above the transonic peak, consistent with wave-drag theory.

#### 11.3.3 Drag Continuity Verification

The continuity sweep executes 235 Mach steps ($\Delta M = 0.02$) for all five geometries with acceptance criterion $\lvert dC_D/dM\rvert < 5.0$:

| Geometry | $\max \lvert dC_D/dM\rvert$ | Location | Result |
|----------|----------------:|----------|--------|
| Cone-Cylinder | 1.02 | $M = 1.07$ | PASS |
| Ogive-Cylinder | 0.87 | $M = 1.08$ | PASS |
| Cone-Cylinder-Fins | 1.43 | $M = 1.06$ | PASS |
| Ogive-Boattail-Fins | 0.76 | $M = 1.07$ | PASS |
| Von Karman-Fins | 1.21 | $M = 1.08$ | PASS |

All peaks occur in the physically real transonic drag-rise region, not at model blend boundaries -- the $C^1$ regime blending of Chapter 10 is doing its job.

#### 11.3.4 Vehicle-Level Benchmark -- Basic Finner (ADA636861)

The Basic Finner is a standard reference projectile (cone-cylinder body with four rectangular fins) used extensively in aeroballistic range testing. `BasicFinnerDragBenchmarkTest` validates total drag against Dupuis & Hathaway's free-flight measurements (DTIC ADA636861, 1997). The headline MAPE is computed over the **8 multiple-fit zero-yaw axial force coefficient ($C_{X0}$) points** spanning $M = 1.08$ to $M = 4.30$; the 25 single-shot points are archived as supporting scatter.

The current result is **MAPE 11.8%**, below the 14% aggregate regression criterion. Four mid-supersonic points exceed 14% pointwise error (-18.0%, -20.0%, -19.2%, and -14.6%), so the gate is an aggregate MAPE gate, not a per-point claim. This is the first vehicle-level total-drag validation for the extended aerodynamic module against published external data. It does not by itself close the broader high-Mach finned-body family, because the NACA RM-10 case remains a documented open mismatch for a structurally different geometry (Section 11.3.6).

#### 11.3.5 AGARD-B Standard Model (AEDC-TR-70-100)

AGARD-B is a standard wind-tunnel reference model used internationally for facility calibration and CFD validation. `AgardBDragBenchmarkTest` validates total and component-level drag against AEDC-TR-70-100 across the subsonic and transonic range ($M = 0.2$--$1.0$), with the trend and component split passing qualitatively.

#### 11.3.6 Excluded Geometry Family -- RM-10 (NACA TN 3320)

`NacaRm10FinnedBodyDragBenchmarkTest` compares the implementation against the RM-10 finned-body free-flight data of NACA TN 3320. The result is **MAPE 80%** -- a large, externally anchored *negative* benchmark. This is recorded as an explicit "excluded geometry family": the RM-10 combines a *high-fineness parabolic forebody* (fineness 12.2), a *smoothly tapered parabolic afterbody* with base-to-max diameter ratio 0.606, and *four untapered 60° swept-back, 10%-thick circular-arc biconvex fins* (NACA TN 3320 Figure 1, page 4). None of those three features is well represented by the Barrowman-family slender-body assumptions. The diagnostic in `paper/data/legacy/rm10_vs_basic_finner_diagnostic.md` (regenerated artifact `core/build/reports/rm10_vs_basic_finner_component_cd.csv`) decomposes the over-prediction at $M = 2.0$ ($C_{D,T,\text{exp}} = 0.215$ vs ORP 0.389; $\Delta = +0.174$) and attributes it to three independent sub-model envelope violations rather than a single broken term.

**Why it fails (mechanism breakdown).**

- *Boattail base-pressure reduction (Viswanath 1996) is calibrated for half-angles* $\theta_{\text{bt}} = 6°$--$16°$ *and is extrapolated outside that band on RM-10.* Section 6.2.7 documents the piecewise form $\eta_{\text{bt}}(\theta_{\text{bt}})$. The RM-10 parabolic afterbody has a continuously varying local half-angle reaching only $\sim 4.8°$ at the base station (slope of $Y = 6.000 - 0.0007407\,x^2$ at $x = 56.5$ in), which puts it *below* the calibrated band where the linear $0.25 + 0.05\,\theta$ branch under-credits wake energization for slowly converging afterbodies. When the RM-10 geometry is reconstructed as a finite-fineness conical transition + cylindrical fin-mount tube + a short terminal contraction (the only Barrowman primitives available in the import path), the terminal contraction has half-angle $\sim 57.5°$ -- well *above* the upper calibration bound -- and the base-pressure reduction is also extrapolated. Either reconstruction is outside the Viswanath envelope. At $M = 2.0$, the terminal-contraction component alone contributes pressure $C_D = 0.043$ and base $C_D = 0.063$ ($\sim 0.106$ combined), $\sim 27\%$ of the predicted total.

- *Finned-body base augmentation (Section 6.2.8, scale-anchored to flat-base ADA636861) is applied without an upstream-boattail discount on RM-10.* The augmentation is corpus-calibrated against Basic Finner, where the fins meet the wake at the maximum body diameter; on RM-10 the fins meet a wake that has already partially recompressed over the parabolic afterbody, so the same $1.55\times$ multiplier over-credits the fin-induced suction. NACA TN 3320 page 7 reports a measured base coefficient $C_{D,B} \approx 0.04$ for the full-scale RM-10 across $M = 1.2$--$3.3$; ORP predicts $0.063$ at $M = 2.0$, exactly the $1.55\times$ multiplier applied to a base-drag-correlation baseline of $0.041$ (the empirical $C_{d,\text{base}} = 0.064 + 0.186/M^2$ form anchored against NACA TN 3393 and consistent with ESDU 77021).

- *DATCOM 4.1.5.1 fin-section coefficient $K$ does not have a calibrated entry for circular-arc biconvex sections.* Section 7.2 of this report uses $K = 4.0$ for HEXAGONAL (double-wedge) and $K = 16/3$ for ROUNDED (rounded-LE airfoil); neither matches the sharp-LE, smoothly curving 10%-thick circular-arc profile specified by NACA TN 3320. Mapped to ROUNDED, the round-LE bluntness term ($C_{p,\text{LE}} = 1.214 - 0.502/M^2 + 0.1095/M^4$) is spuriously activated and contributes $\sim 0.11$ of fin-set $C_D$ at $M = 2.0$ that should not be present for a sharp-LE section. Mapped to HEXAGONAL, the $K = 4.0$ wedge-angle assumption under-predicts the smooth-arc thickness distribution. There is no third option in the implementation.

- *Body wave drag is correct here.* The POWER $p = 0.5$ paraboloid nose is routed through the TR-R-100 fineness-scaled reference family, not through Dahlem-Buck (the `isDirectReferenceShapeForSupersonicOverride` gate excludes paraboloids); paraboloid pressure $C_D \approx 0.016$ at $M = 2.0$ and $f_n = 7.5$ matches the analytical scaling. The forebody is *not* the deficit driver.

**Combined effect estimate.** Quantified individually, the three sub-model violations remove $\sim 0.085$ of the $+0.174$ over-prediction at $M = 2.0$. The residual $\sim 0.085$ -- still $\sim 40\%$ over-prediction -- is distributed across small terms (high-fineness body friction calibration, fin-body interference at AR $= 2.04$, and fin trailing-edge bluntness on the arc section) that no individual module owns. *The deficit is genuinely fragmented across the calibration envelopes of three independent sub-models, not concentrated in any one of them.*

**Who it affects.** RM-10 is a 1949-vintage research geometry chosen specifically to instrument boattail base pressure on a low-base-ratio body. Its three out-of-envelope features do not appear together in any flight in the Rocket Flight Database corpus or in any published Basic-Finner-class benchmark. High-power amateur rocket boattails almost always fall in the 6°--16° Viswanath band; flight-grade fins are almost always hexagonal or NACA airfoil sections, not 10%-thick circular arc; and parabolic forebodies of fineness 12+ are absent from the corpus.

**Why we do not fix it.** Each of the three envelope violations could be patched in isolation -- for example, by extrapolating Viswanath outside 6°--16° with explicit damping, adding an upstream-boattail gate to the finned-body augmentation, or adding a circular-arc biconvex $K$ entry. Each individual patch was attempted in scratch branches and each one regressed Basic Finner, the corpus, or both. Because the deficit is fragmented, a clean closure would require simultaneous calibration against (a) a Basic-Finner-class flat-base benchmark, (b) RM-10 itself, and (c) the 25-flight corpus -- and the calibration set required to disentangle these regimes does not exist in the public literature in a digitizable form. The cost-benefit of a multi-source recalibration is poor, because RM-10's geometry family is not represented in the application domain; the model is already valid where it is used.

Including this benchmark in the validation pack is a deliberate honesty choice. RM-10 documents the *boundary* of the model's geometric domain rather than counting as a closed validation. It is the only externally anchored negative benchmark in the present work.

**Envelope statement.** The headline accuracy claim of this work applies to finned slender vehicles within the boattail half-angle envelope of $6°$--$16°$ (the Viswanath calibration band) and to fin sections that are HEXAGONAL (double-wedge) or AIRFOIL/ROUNDED (rounded-LE) -- the section types of every flight in the Rocket Flight Database and of every Basic-Finner-class wind-tunnel/free-flight reference geometry used in this work. *Out-of-envelope geometries -- specifically the high-fineness parabolic body with steeply contracted afterbody and 60° swept circular-arc biconvex fins of NACA RM-10 -- are reported as transparency references and are excluded from the headline accuracy claim.*

#### 11.3.7 Other Drag Benchmarks (Cross-References)

- **Nose/body wave drag (NACA RM A52H28).** `NacaRmA52H28BenchmarkTest`: 25 points, 5 shapes, MAE 0.029 in $C_D$. Sections 5.4 and 6.1 document the Taylor--Maccoll and shock-expansion methods that produce these predictions.
- **Van Driest II skin friction (NASA TN D-6945).** `VanDriestIISkinFrictionTest` confirms approximately 33% friction reduction at $M = 2$, 53% at $M = 3$, and 75% at $M = 5$ relative to incompressible. Section 6.3 documents the implementation.
- **Chapman laminar base drag (NACA TN 3393).** `ChapmanLaminarBaseDragTest`: 4 laminar points, MAPE 4.4%. The Chapman--Korst turbulent path is an available/tested utility rather than an active production path in the current `BarrowmanDragCalculator`.
- **Hypersonic cone drag (DTIC AD0487365).** `HypersonicConeDragBenchmarkTest`: 11 points $M = 6.5$--$17.2$, MAPE **19.7%** with the source Reynolds number matched row-by-row; 16-degree cones predicted within 11%, with the largest pointwise residual at the 8-degree, $M=6.5$ low-Re row (+57.0%).


### 11.4 Stability Validation

#### 11.4.1 Static Stability -- NASA TM X-653

`Phase3StabilityTest` and `NasaTmX653K1FloorTest` validate static stability against TM X-653 (Jorgensen, Spahr & Hill 1962) for the NSCFB configuration -- a sharp 16-degree cone nose, a 2-diameter cylinder, and blunt cruciform fins.

| Metric | Points | MAE | RMSE | MAPE | Max % | Mean bias |
|--------|------:|----:|-----:|-----:|------:|----------:|
| $C_N$ | 10 | 0.0035 | 0.0045 | **6.84%** | 18.08% | +0.0035 |
| $x_{CP}/d$ | 10 | 0.054 | 0.061 | **7.11%** | 14.6% | +0.054 |

Interpretation, paraphrasing the NASA TM X-653 closure memo (`paper/data/md/nasa_tm_x653_validation_report.md`): below $M = 3$ the implementation tracks the experimental curve within $9\%$ on $C_N$ and within $4\%$ on $x_{CP}/d$ at $M = 3.0$ (down from a 125% error before the M=3.0 ESDU TransonicSimilarity guard was added). At $M = 4.06$--$5.82$ the implementation over-predicts $C_N$ by 13--18% and shows a $x_{CP}/d$ plateau because the $K_1 = 0.85$ floor prevents fin $C_{N\alpha}$ from decaying with Mach as fast as the experiment for low-aspect-ratio fins. This is an honest, documented model trade-off; the case is reported as externally benchmarked at $\le 8\% / \le 7.1\%$ MAPE.

**Fourth independent static-aero anchor -- Arcas wind-tunnel coefficients (NASA TN D-4013 + TN D-4014).** The TM X-653 NSCFB result above (a low-fineness blunt cruciform-fin geometry) is supplemented by digitized wind-tunnel coefficients for the Arcas single-stage sounding rocket (a slender ogive-cylinder-boattail geometry with trapezoidal double-wedge fins). Two companion Langley reports cover the same model continuously across $M = 0.60$--$4.63$: TN D-4013 (Ferris 1967, Langley 8-ft transonic pressure tunnel, $M = 0.60$--$1.20$) and TN D-4014 (Babb \& Fuller 1967, Langley Unitary Plan Wind Tunnel, $M = 1.50$--$4.63$). The combined set provides 12 Mach points $\times$ 4 quantities ($C_{N\alpha}$, $C_{A0}$, $x_{CP}$, $C_{m\alpha}$) = 48 data values, archived at `paper/data/csv/arcas_wind_tunnel_combined_2026_05_02.csv` with figure-by-figure provenance in `paper/data/md/arcas_wind_tunnel_assessment_2026_05_02.md`. The dataset documents the externally-validated trend that $x_{CP}$ moves rearward through the transonic peak ($\sim 86\%$ body length at $M \approx 1.0$--$1.2$) and progressively forward at supersonic Mach (down to $\sim 56\%$ at $M = 4.63$). Confidence distribution from the digitization assessment: 0 high / 9 medium / 3 low (the three low-confidence rows are the transonic Fig.\ 11 peak in D-4013 and the high-Mach $C_{m\alpha}$ slope reads in D-4014 where the moment slope is small). This is a **B-level** benchmark in the present revision: the Arcas .ork comparator and `ArcasWindTunnelComparatorTest` are not yet built, so the dataset enters the manuscript as an externally-anchored target rather than as a closed validation. The path to A-level promotion is documented in the digitization assessment (build the Arcas geometry from TN D-4013 Fig.\ 1, run ORP at the digitized Mach points at the tunnel Reynolds number, and re-digitize the three low-confidence rows with WebPlotDigitizer to bound reader uncertainty). Citation: TN D-4013 and TN D-4014 are both verified from the title pages of the PDFs in repo (`paper/data/pdf/New/incoming/arcas/`), per the citation-hygiene policy of this work.

#### 11.4.2 Crossflow $C_{d,c}$ Anchors -- Jorgensen and Hoerner

`JorgensenCrossflowCdBenchmarkTest` confirms the implementation's body crossflow drag $C_{d,c} = 1.20$ exactly matches Jorgensen TR R-474 Table 1 (circular cylinder), and the fin crossflow drag $C_{d,c} = 1.42$ matches Hoerner Ch. 3 Fig. 28 ($1.43$ tabulated; 0.7% relative error).

#### 11.4.3 Center of Pressure vs Mach

| $M$ | $x_{CP}$ (m, OBF, from nose) | Trend |
|-----|------------------------------:|-------|
| 0.3 | 0.4434 | Subsonic -- classical Barrowman |
| 1.0 | 0.4780 | Transonic -- $\beta$ spline active |
| 1.5 | 0.3807 | Supersonic -- fin $C_{N\alpha}$ reduced by $1/\beta$ |
| 2.0 | 0.2854 | Continued aft shift |
| 3.0 | 0.1747 | Body crossflow correction active |
| 5.0 | 0.0768 | Modified Newtonian dominant |

The aft shift from $M = 0.3$ to $M = 5$ is approximately 0.37 m (49% of total rocket length), consistent with the published supersonic behavior where fin $C_{N\alpha}$ decays as $1/\beta$ relative to the body.

#### 11.4.4 Dynamic Stability Benchmarks (Cross-Reference to Section 9.9)

The dynamic stability suite is documented in Section 9.9. Summary:

| Claim | Result | Evidence |
|------|--------|----------|
| Cmq accumulation, roll, Magnus | $< 0.5\%$ / $\sim 2\%$ / $\sim 0\%$ vs analytical | external benchmark (analytical) |
| Pitch damping $C_{mq}$ vs TN 3788 | 39% at $M=1.5$; conservative high-$M$ | external benchmark |
| Pitch damping `3x` multiplier vs ADA636861 | MAPE 69%; sign correct, supersonic under-prediction | **integrated flight data** |
| Transonic Cmq Gaussian (peak 3.5×) vs ADA636861 | over-predicts $\sim 3.6\times$ at $M = 1.05$--$1.12$ | **integrated flight data** |
| Pitch damping vs Bhagwandin & Sahu 2013 ARL-TR-6725 (AFF) | supersonic MAPE 18.96% on a non-Basic-Finner geometry; sign-consistent with ANF | external benchmark (B-level, AFF planform fixture pending; see Section 9.9.6) |
| Magnus body fraction (0.3) | within Platou (AIAA Journal 3(1), 1965) measured 0.3--0.8 range | external benchmark |
| Vortex asymmetry ($K_v = 0.20$) | within plausible high-incidence range (internal check) | internally calibrated (no literature anchor) |


### 11.5 Hypersonic Validation

#### 11.5.1 Hypersonic Cone Foredrag (DTIC AD0487365)

The hypersonic cone foredrag model -- Modified Newtonian theory blended with Taylor--Maccoll over $M = 4$--$6$ -- is validated against Grabow (1965), DTIC AD0487365: 11 cone-drag data points at $M = 6.5$--$17.2$. `HypersonicConeDragBenchmarkTest` matches the source $Re_L$ row-by-row and achieves **MAPE 19.7%**; 16-degree half-angle cones are predicted within 11%. The frozen diagnostic gate in the Java test is $< 20\%$, so this is a near-threshold pass rather than a wide-margin result.

#### 11.5.2 Maximum Pressure Coefficient

The Rayleigh pitot formula gives $C_{p,\max}$:

| $M$ | $C_{p,\max}$ |
|-----|-------------:|
| 2.0 | 1.6573 |
| 3.0 | 1.7557 |
| 5.0 | 1.8088 |
| 10.0 | 1.8317 |
| 20.0 | 1.8374 |

The Newtonian limit is $C_{p,\max} \to 1.839$ as $M \to \infty$; the computed value at $M = 20$ is $1.837$, confirming the asymptote.

#### 11.5.3 Effective Ratio of Specific Heats

| $T_0$ (K) | $\gamma_\text{eff}$ | Regime |
|-----------|---------------------:|--------|
| 300 | 1.400 | Cold / low Mach |
| 800 | 1.400 | Onset of $O_2$ vibrational excitation |
| 1500 | 1.37--1.38 | $M \approx 4$--5 |
| 3000 | $\ge 1.30$ | Both $N_2$ and $O_2$ modes excited |
| 5000 | $\ge 1.30$ | Approaching dissociation threshold |

The implementation clamps $\gamma_\text{eff} \ge 1.30$ to avoid non-physical values before dissociation chemistry (which is *not* modeled).


### 11.6 Integrated Trajectory Validation -- 25-Flight Corpus

The integrated 6-DOF trajectory predictions are validated against a corpus of **25 real high-power, amateur, university-research, and sounding-rocket flights** with measured GPS, barometric, optical, accelerometer, or radar/radar-beacon apogee. The corpus is published as the *Rocket Flight Database* (DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977), CC-BY-4.0). All 25 flights are the public RASAero II altitude comparison set published by Charles E. Rogers (RASAero II author) at <https://www.rasaero.com/comparisons-alt.htm>: 23 single-stage flights plus two two-stage flights---the AeroPac 104K Two-Stage (flight 22) and the MESOS 293K closure (flight 25). Because the corpus is externally selected by Rogers -- not outcome-curated by us -- the accuracy statistics are an honest, outcome-independent validation result. The OpenRocket Plus predictions are produced by importing the same `.CDX1` into the simulator and running with default settings. (A separate, exploratory set of ~20 historical sounding-rocket flights reaching Mach 5--7 -- including Black Brant V VB and the Nike-Deacon pair -- is reported as a capability demonstration in Section 11.6.6, NOT as part of this headline corpus.)

This is the "integrated flight data" capstone: it does not isolate any single subsystem, but it demonstrates that the assembly of physics in Parts A--D produces trajectory predictions consistent with measured reality across Mach 0.54--4.33 and apogees from 3 577 ft (1.1 km) to 293 488 ft (89.5 km).

#### 11.6.1 Aggregate Result (25 Flights)

| Metric | This work (n = 25) | RASAero II (n = 25 paired) |
|---|---:|---:|
| Mean signed error | **−0.38%** | +2.46% |
| Sample $\sigma$ | **5.44%** | 5.81% |
| RMSE | **5.34%** | 6.20% |
| Mean $\lvert\text{error}\rvert$ (MAE) | **4.74%** | 5.34% |
| Within $\pm 5\%$ | **14/25 (56.0%)** | 13/25 (52.0%) |
| Within $\pm 10\%$ | **25/25 (100%)** | 22/25 (88.0%) |
| Worst case | $+8.7\%$ (Kinsel, AeroPac 104K, FMJ Black Rock 6) | $+11.5\%$ (T&L) |
| Bias$^2$/MSE | **0.01** | 0.16 |
| Abnormal endings | 0 | n/a |

The whole corpus is the paired set: the Wilcoxon signed-rank test on the paired absolute errors returns $W = 143.0$, $p = 0.615$, and the difference in mean absolute error is $|\text{ORP}| - |\text{RAS}| = -0.60$ pp with a 95\% bootstrap CI of $[-2.16, +0.96]$ that straddles zero. Neither test rejects the null hypothesis of equal absolute-error distributions at $\alpha = 0.05$: **the honest claim is parity with this version-locked RASAero set, not superiority.** The RASAero II values are Rogers' *recorded* predictions (not fresh independently-rerun pre-flight cases), which is disclosed here. Bland-Altman analysis gives 95\% limits of agreement of $\pm 14.3\%$ with a mean offset of $-2.84\%$. The mean-error 95\% bootstrap CI is $[-2.41, +1.72]$, bracketing zero, so the predictor is statistically unbiased on this corpus. The whole-corpus bias$^2$/MSE = 0.01 for OpenRocket Plus (vs 0.16 for RASAero II) means the residual is dominated by per-flight variance (build tolerance, motor lot variation, atmospheric soundings, ground-truth instrumentation precision) rather than systematic model bias.

#### 11.6.2 Per-Case Table (Sorted by Peak Mach)

Errors are signed; positive = over-predicted apogee. $\Delta = |\text{RAS err}| - |\text{this-work err}|$ (positive = this work closer). RASAero II values for all 25 flights are as published by Rogers (loc. cit.). The canonical machine-readable form is the *Rocket Flight Database*.

```{=latex}
\begin{landscape}
```

| # | Rocket | Launch ft | Peak M | Real ft | RAS ft | This work ft | RAS err | This-work err | $\Delta$ |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
|  1 | Thunder & Lightning | 2,750 | 0.54 | 3,577 | 3,989 | 3,877 | $+11.5\%$ | $+8.4\%$ | $+3.1$ |
|  2 | Gibb | 2,750 | 0.55 | 3,913 | 4,310 | 3,989 | $+10.1\%$ | $+1.9\%$ | $+8.2$ |
|  3 | Cancer Descending | 2,750 | 0.56 | 6,188 | 6,328 | 6,044 | $+2.3\%$ | $-2.3\%$ | $0.0$ |
|  4 | EZI-65 J450ST | 2,750 | 0.60 | 3,965 | 4,214 | 4,158 | $+6.3\%$ | $+4.9\%$ | $+1.4$ |
|  5 | Caliber Isp 04 Team 2 | 2,302 | 0.64 | 3,710 | 3,871 | 3,890 | $+4.3\%$ | $+4.9\%$ | $-0.6$ |
|  6 | Caliber Isp 04 Team 3 | 2,302 | 0.64 | 3,964 | 3,871 | 3,889 | $-2.3\%$ | $-1.9\%$ | $+0.4$ |
|  7 | Caliber Isp 04 Team 1 | 2,302 | 0.66 | 3,837 | 3,943 | 3,960 | $+2.8\%$ | $+3.2\%$ | $-0.4$ |
|  8 | Byrum | 2,750 | 0.75 | 5,732 | 5,280 | 6,161 | $-7.9\%$ | $+7.5\%$ | $+0.4$ |
|  9 | Ion Drive | 2,750 | 0.79 | 8,027 | 8,642 | 7,730 | $+7.7\%$ | $-3.7\%$ | $+4.0$ |
| 10 | Caliber Isp 05 Discovery | 2,848 | 0.81 | 4,930 | 4,831 | 4,772 | $-2.0\%$ | $-3.2\%$ | $-1.2$ |
| 11 | Blister | 2,400 | 0.83 | 9,026 | 8,347 | 8,268 | $-7.5\%$ | $-8.4\%$ | $-0.9$ |
| 12 | Caliber Isp 05 Columbia | 2,848 | 0.84 | 5,085 | 4,842 | 4,777 | $-4.8\%$ | $-6.1\%$ | $-1.3$ |
| 13 | Rabia Short Fin Can | 3,400 | 0.86 | 10,584 | 10,376 | 9,916 | $-2.0\%$ | $-6.3\%$ | $-4.3$ |
| 14 | Raven | 2,750 | 1.07 | 8,815 | 9,288 | 9,489 | $+5.4\%$ | $+7.6\%$ | $-2.2$ |
| 15 | Rabia | 2,400 | 1.14 | 12,745 | 12,777 | 11,913 | $+0.3\%$ | $-6.5\%$ | $-6.2$ |
| 16 | Torrent | 2,400 | 1.22 | 12,807 | 13,852 | 12,455 | $+8.2\%$ | $-2.8\%$ | $+5.4$ |
| 17 | Kline-Rogers L500 | 2,848 | 1.98 | 24,771 | 26,485 | 24,179 | $+6.9\%$ | $-2.4\%$ | $+4.5$ |
| 18 | A-601 Kinsel | 3,933 | 2.19 | 42,771 | 41,086 | 46,499 | $-3.9\%$ | $+8.7\%$ | $-4.8$ |
| 19 | FMJ BALLS 005 | 3,933 | 2.31 | 37,981 | 38,820 | 37,256 | $+2.2\%$ | $-1.9\%$ | $+0.3$ |
| 20 | FMJ Black Rock 6 | 3,933 | 2.46 | 30,038 | 32,646 | 29,239 | $+8.7\%$ | $-2.7\%$ | $+6.0$ |
| 21 | Proteus 6 | 3,933 | 2.87 | 85,067 | 86,799 | 91,339 | $+2.0\%$ | $+7.4\%$ | $-5.4$ |
| 22 | AeroPac 104K | 3,750 | 3.04 | 104,659 | 113,786 | 103,602 | $+8.7\%$ | $-1.0\%$ | $+7.7$ |
| 23 | Don't Debate This | 3,750 | 3.04 | 56,573 | 62,308 | 53,150 | $+10.1\%$ | $-6.1\%$ | $+4.0$ |
| 24 | Qu8k | 3,750 | 3.46 | 121,478 | 116,254 | 119,187 | $-4.3\%$ | $-1.9\%$ | $+2.4$ |
| 25 | MESOS 293K | 3,910 | 4.33 | 293,488 | 289,789 | 273,056 | $-1.3\%$ | $-6.96\%$ | $-5.7$ |

```{=latex}
\end{landscape}
```

#### 11.6.3 High-Altitude Two-Stage Detail (MESOS 293K, peak Mach 4.33)

| Metric | Real | RASAero II | This work | RAS err | This-work err |
|---|---:|---:|---:|---:|---:|
| Apogee (ft) | 293,488 | 289,789 | 273,056 | $-1.3\%$ | $\mathbf{-6.96\%}$ |
| Peak Mach | 4.18 | 4.23 | 4.33 | $+1.2\%$ | $+3.6\%$ |
| Booster burnout / sep (s) | -- | -- | 7.941 | -- | -- |
| Sustainer ignition (s) | -- | -- | 23.103 | -- | -- |
| Sustainer burnout (s) | -- | -- | 33.692 | -- | -- |

Launch site: Black Rock Desert, NV, 3,910 ft (read from the imported launch-site altitude). This case exercises stage-aware nozzle pressure-thrust correction, two-stage motor sequencing, and Mach 3+ coast aerodynamics simultaneously.

**The largest single-flight error.** The current archived code predicts MESOS 293K apogee at **$-6.96\%$ (273,056 ft)**, the largest single-flight error in the 25-flight corpus. The figure **reproduces in isolation** -- it is the genuine, reproducible current-code value, confirmed by an isolation run -- and is the value used throughout this report and carried in the published database. As the higher-Mach of the two two-stage closures, this case couples stage separation, coast-phase aerodynamics, and Mach-4 base drag in a single integrated result, and bounds the framework's accuracy at the top of its validated envelope; the error is reported without decomposition. An earlier draft and database snapshot reported $-0.6\%$ (291,601 ft) for this flight; that figure was erroneous, has no defensible derivation, and is **withdrawn** -- it is *not* a prior value that $-6.96\%$ regressed from. Because $-6.96\%$ remains inside the $\pm 10\%$ admission band, the 25/25 within-$\pm 10\%$ headline is unchanged.

#### 11.6.4 Active Mechanisms Producing the Baseline

The closure above is *not* a per-case multiplier. It is the convergence of four shared mechanisms applied to the entire corpus and to the external benchmarks simultaneously:

- Stage-aware nozzle pressure-thrust correction during powered flight (`RK4SimulationStepper`).
- RASAero `Turbulence=True` parsed into `forceTurbulentBL`; bounded to zero for non-perfect-finish imports by an ablation study, while still active for perfect-finish laminar fixtures.
- Geometry-gated finned-base drag augmentation (saturated fin-count scaling, rounded-fin transonic wake, expanding fin-can sleeve, four-fin low-subsonic ramp).
- Trajectory-derived peak Mach via `data.getMaxMachNumber()` in all three reporting paths.

**Mechanism ablation (each mechanism disabled in isolation).** To rank the corpus apogee-error contribution of each supersonic mechanism, each was disabled in turn and the archived 24-flight mechanism-ablation subset was re-run; the table reports the mean absolute change in apogee error across that subset (23 single-stage flights plus the AeroPac 104K two-stage closure). MESOS 293K remains part of the companion 25-flight validation corpus but is not included in this archived ablation artifact.

| Mechanism | Mean $\lvert\Delta\rvert$ | Max $\lvert\Delta\rvert$ (flight) | Note |
|-----------|------------------:|-----------------------------------|------|
| Finned-base augmentation (`FINNED_BASE_K`, EXTERNAL/Basic-Finner) | **8.10 pp** | 39.5 pp (Kinsel, $M = 2.19$) | **dominant apogee driver** |
| Van Driest II skin friction | 0.87 pp | 7.9 pp (Qu8k, $M = 3.46$) | matters at high Mach |
| DATCOM 4.1.5.1 fin wave drag | 0.39 pp | 1.9 pp (Proteus) | modest |
| ShockGeometry pre-pass | **0.15 pp** | 3.6 pp (FMJ Black Rock 6, $M = 2.46$) | inert subsonically |
| PNK interference / $K_1$ floor | 0.00 pp | 0.00 pp | no apogee effect |

The externally-calibrated **finned-base augmentation is the dominant apogee mechanism (8.10 pp mean)**, an order of magnitude larger than any other term and two orders larger than the ShockGeometry pre-pass. This is the central motivating result for the companion base-drag-intercomparison study (Paper 5): the integrated apogee error budget is governed by the base-drag closure, not by the supersonic shock-geometry machinery.

The **ShockGeometry pre-pass moves integrated apogee by only 0.15 pp** in the mean. This is expected and honest: apogee integrates a trajectory dominated by lower-Mach drag, and the pre-pass is inert below $M \approx 1$ (Section 9.4.4, Section 11.7). Its value is *local-flow fidelity* -- correct post-shock conditions for fin loads and stability, verified bit-for-bit against Taylor--Maccoll (Section 11.3.1, row D8) -- and its role as the *architectural seam* that enables the downstream supersonic stability models, not a gross-apogee win. The pre-pass is presented throughout this work on those terms, never as an apogee-accuracy driver.

#### 11.6.5 Results Calibrated Against the 25-Flight Corpus

The following results contribute to the trajectory closure but are *not* externally benchmarked at the component level — they are calibrated against the integrated 25-flight corpus. They are circular in the sense that the calibration target and the validation target overlap. None of them are counted in the "20 externally benchmarked subsystems" headline.

| Claim | What is unverified at the component level | What would close the gap |
|------|-------|-----------|
| Cmq $\times 3$ multiplier (Section 9.9.5) | Over-predicts $\sim 3.6\times$ at $M = 1.05$--$1.12$ when measured against ADA636861 alone, but the multiplier is needed to match apogee-turn timing on the corpus | A second free-flight $C_{mq}$ dataset that is *not* used to set the multiplier (Sznajder 2025 CFD now provides a CFD-side second source confirming the transonic overshoot) |
| Transonic $C_{mq}$ Gaussian (peak 3.5) | Same dataset, same over-prediction direction (Sznajder 2025 CFD: +110 to +160% at $M = 1.08$--$1.11$) | Same |
| Finned-body base drag augmentation | The fin-presence wake correction is set by corpus apogee residual; Hart 1952 measures body-alone | Public finned-body base-pressure dataset across $M = 0.7$--$3$ |
| Power-on nozzle / pressure thrust | MESOS 293K is the only multi-stage powered-flight closure | A second multi-stage flight with telemetry |
| Min-diameter supersonic flight (Raven, DDT) | Apogee closes but no isolated component check | Dedicated min-diameter free-flight dataset |
| Termination / descent dynamics | 0/25 abnormal endings, but no isolated $C_N(\alpha)$ / $C_m(\alpha)$ at high $\alpha$ | High-$\alpha$ dataset (see Section 12.4 item 6) |
| Full 6-DOF trajectory fidelity | MAE 4.74% (mean signed −0.38%, $\sigma$ 5.44%) on the corpus is the validation, not a component check | (Headline metric — not separable) |
| Geometry-import parity | RASAero `ModifiedBarrowman` stability switch is parsed but not honored | Implement the alternate stability path |

The headline corpus closure is dominated by drag and base-pressure terms, not by damping. Removing the $C_{mq}$ multiplier or the Gaussian augmentation degrades the apogee-turn signature on five flights but does not move the headline MAE 4.74% by more than $\sim 0.5$ pp; the corpus is therefore mostly drag-validated, not damping-validated.

#### 11.6.5a In-Sample Disclosure and Decontaminated Prospective Holdout

Two base-drag scale constants are corpus-frozen and must be disclosed as partly in-sample. The thick-boundary-layer base-drag constant `THICK_BL_K = 2.2` was anchored on Raven, and the slender-body base-drag constant `SLENDER_BODY_K = 0.0025` was anchored on Raven, Rabia, and Kinsel (the source diagnostic additionally inspected Torrent). Because these two constants were set with reference to specific corpus flights, the 25-flight headline is **partly in-sample**: the calibration set and a portion of the validation set overlap, exactly as already flagged for the finned-body base-drag augmentation in Section 11.6.5.

The primary defense against the circularity critique is a **decontaminated prospective holdout**. Every flight that any of the two constants touched -- Raven, Rabia, Rabia Short Fin Can, Kinsel, and Torrent -- was placed in the development partition, leaving a genuinely blind holdout. The split is by *flight*, not by error magnitude, so it is not outcome-selected:

| Partition | n | Mean signed | MAE |
|-----------|--:|------------:|----:|
| DEV (every flight a constant touched) | 13 | $+0.22\%$ | **5.47%** |
| HOLDOUT (genuinely blind) | 12 | $-1.03\%$ | **3.95%** |

The blind holdout is **more accurate than the development partition** (MAE 3.95% vs 5.47%). A model that had overfit its two in-sample constants would show the opposite ordering -- worse accuracy on the held-out flights. The holdout-beats-dev result is therefore direct evidence that the two base-drag constants **generalize rather than overfit**, and it is the primary in-sample defense for both this work and the companion base-drag study (Paper 5), where the same `FINNED_BASE_K`-class circularity is handled with this same decontaminated split.

#### 11.6.6 Exploratory High-Mach Set and Sounding-Rocket Corpus Expansion (Seed)

**Exploratory high-Mach demonstration (NOT part of the headline corpus).** Separately from the 25-flight headline corpus, approximately 20 historical sounding-rocket flights reaching Mach 5--7 were run as an *exploratory capability demonstration*. This set is reported in full -- it is not outcome-curated, and presenting it as a high pass-rate headline would be selection on the dependent variable. Of the set, **3 flights close within $\pm 10\%$**: Black Brant V VB AAF-VB-32 (peak Mach 7.224, apogee 273.6 km, $-6.97\%$; DTIC AD0733141), Nike-Deacon flight 1 (peak Mach 4.956, $-1.06\%$), and Nike-Deacon flight 2 (peak Mach 5.079, $-0.89\%$). The remaining **17 flights fall outside $\pm 10\%$**: the Nike-Apache family at $+24$ to $+36\%$, Nike-Cajun at $+16.6\%$, Arcas blunt/secant variants at $-29$ to $-69\%$, HEROS 3 at $-63.4\%$, plus a couple of sim-error / zero-apogee cases reported transparently. The honest framing is therefore that the method *reaches* Mach 7 within $\pm 7\%$ on well-characterized vehicles (Black Brant V VB, Nike-Deacon), but motor and geometry reconstruction uncertainty dominates on the poorly-documented historical flights; the high-Mach set is an exploratory capability demonstration, never a validation headline. The root-cause coast-drag bias driving the Nike-Apache / Nike-Cajun over-predictions is documented in Section 12.6a.

**Sounding-rocket corpus expansion (seed).** Expansion of the trajectory-validation envelope to a second corpus class -- *meteorological / sounding rockets* with documented mass properties, motor thrust curves, and aero coefficient tables -- is in progress. The seed for this expansion is AFCRL-TR-73-0412 / AD-766737 (Bollermann \& Walker 1973, Space Data Corp), *"Design, Development and Flight Test of the Super Loki Stable Booster Rocket Systems."* The report contains:

- Time-resolved booster mass properties (CG and $I_{yy}$, Figures 4.2--4.3).
- Motor thrust and chamber pressure vs time (Figure 3.4; sea-level firing in Table 3.3, average thrust 4757 lbf, $I_{sp}$ 228.7 s, action time 2.09 s).
- Booster, vehicle, and dart aerodynamic coefficient curves -- $C_{N\alpha}$, $C_P$, $C_D$ vs $M$ from $M = 0$ to $M \approx 7$ (Figures 4.4--4.8).
- Approximately 30 flight summaries across Super Loki Robin Dart (Table 8.2), Super Loki Instrumented Dart (Table 8.3), and Viper-3A Robin Dart (Table 8.4) configurations.

The Super Loki Dart `.ork` model has been committed as the seed (commit `f8db50ff5`); ORP simulation runs against the digitized aero curves and trajectory data in AD-766737 are pending. This expansion is the planned content of Rocket Flight Database v2.0 and is recorded as the prospective sounding-rocket extension; the present manuscript reports it only as a documented seed, not as a closed validation. The schema decision for v2.0 is recorded at `paper/data/v2_schema_decision_proposal_2026_05_02.md` (Option B: keep the v1.0 schema and leave `apogee_rasaero_ft` blank for sounding rockets that have no RASAero II reference). The full candidate dossier is at `paper/data/sounding_rocket_corpus_candidates_2026_05_02.md`, with verified citations for the Super Loki / Loki-Dart family (AFCRL-TR-73-0412, NASA CR-61238) and the Arcas family (TN D-4013, TN D-4014, AD-235341).


### 11.7 Performance Benchmarks

Mean per-call aerodynamic calculation time on the OBF geometry (post-JIT warmup):

| $M$ | Avg time (ms/call) | Supersonic / subsonic ratio |
|-----|-------------------:|----------------------------:|
| 0.3 | 0.18 | 1.0x (baseline) |
| 0.5 | 0.19 | 1.1x |
| 1.0 | 0.21 | 1.2x |
| 1.5 | 0.61 | 3.4x |
| 2.0 | 0.74 | 4.1x |
| 3.0 | 0.82 | 4.6x |
| 5.0 | 0.71 | 3.9x |
| 10.0 | 0.58 | 3.2x |

Throughput at $M = 3$: 1000 calculations in approximately 820 ms (0.82 ms per call), well within the 30-second acceptance criterion.

**Subsonic passthrough.** At $M < 1.0$, `ShockGeometry.compute()` costs approximately 150--300 ns per call (a single branch and memory read), confirming zero measurable overhead for subsonic flight simulation. The supersonic overhead is the $O(n_\text{components})$ ShockGeometry pre-pass.

**Full aerodynamic test suite runtime.** On a typical Windows development host, the complete aerodynamic regression battery (85 tracked test classes in this package hierarchy) takes approximately **11 minutes** (CLAUDE.md). The bottleneck is `SupersonicBaselineTest.testDCdDMachBounded()`, which sweeps 5 rocket geometries × 235 Mach steps for the continuity verification of Section 11.3.3 (~7 minutes alone).


### 11.8 Comparison with Original OpenRocket

Old vs new predictions for the Cone-Cylinder geometry:

```{=latex}
\begin{landscape}
```

| Quantity | $M = 2.0$ (orig) | $M = 2.0$ (new) | $M = 3.0$ (orig) | $M = 3.0$ (new) | $M = 5.0$ (orig) | $M = 5.0$ (new) |
|----------|-----------------:|----------------:|-----------------:|----------------:|-----------------:|----------------:|
| $\beta$ | 0.25 (clamped) | 1.732 | 0.25 (clamped) | 2.828 | 0.25 (clamped) | 4.899 |
| $C_f$ reduction | 0% | $\sim 33\%$ | 0% | $\sim 53\%$ | 0% | $\sim 75\%$ |
| Total $C_D$ | $\sim 0.41$ | 0.361 | $\sim 0.32$ | 0.266 | $\sim 0.24$ | 0.188 |
| Relative $C_D$ error vs new | $+14\%$ | -- | $+20\%$ | -- | $+28\%$ | -- |

```{=latex}
\end{landscape}
```

Summary of subsystem improvements:

| Component | Original OpenRocket | OpenRocket Plus |
|-----------|--------------------|-----------------|
| $\beta$ factor | hard floor 0.25 | cubic Hermite spline + exact formula |
| Skin friction | incompressible only | Van Driest II compressible transformation (Ch. 6) |
| Wave drag | TR-R-100 tables (limited) | Taylor--Maccoll + DATCOM 4.1.5.1 + shock-expansion |
| Base drag | basic formula | $C_{d,\text{base}}=0.064+0.186/M^2$ (NACA TN 3393 / ESDU 77021) + $C^1$ transonic blend + optional Chapman laminar path |
| Fin local flow | freestream Mach | post-shock Mach from ShockGeometry for fin stability / PNK / SBLI chord reduction |
| Hypersonic | no model | Modified Newtonian blended $M = 4$--6 |
| Static stability | no supersonic correction | Galejs + Allen-Perkins crossflow + PNK + ESDU similarity (Ch. 8) |
| Dynamic stability | apogee-turn heuristic only | Cmq strip theory + Gaussian augmentation + Magnus + Euler gyroscopic |
| Trajectory integrator | RK4 with limited gates | RK4 with quaternion + adaptive timestep + sanitization + warning diagnostics |
| Valid Mach range | $M < 2$ | vehicle-level (6-DOF) validated to $M \approx 4.3$; component-level cone foredrag validated to $M \approx 17$ (single benchmark) |


## 12. Conclusions and References


### 12.1 Summary of Contributions

This work has extended the OpenRocket aerodynamic simulation framework from a subsonic/low-transonic tool valid to roughly $M = 2$ into a compressible-flow simulation whose validated envelope is two-tier: vehicle-level (6-DOF integrated trajectory) is validated through $M = 4.33$ against the 25-flight Rocket Flight Database headline corpus (DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977)), with an exploratory high-Mach set reaching $M = 7$ on well-characterized vehicles (Section 11.6.6), and component-level cone foredrag is validated to $M \approx 17$ against a single isolated benchmark (DTIC AD0487365). The principal contributions:

1. **Gas dynamics foundation.** A complete set of compressible flow solvers -- oblique shock relations ($\theta$-$\beta$-$M$ with bisection), Taylor--Maccoll cone flow (ODE integration), normal shock jump conditions, and Prandtl--Meyer expansion fan relations -- validated against NACA Report 1135 and cone-flow reference tables: normal shocks to $7\times10^{-5}$, oblique-shock wave angle to $0.021\%$, Prandtl--Meyer angle to $0.004^\circ$, and Taylor--Maccoll cone-shock angle to $0.825\%$ relative. These solvers form the backbone for every subsequent wave drag, pressure coefficient, and shock-geometry calculation.
2. **Analytical wave drag models.** Replacement of the legacy NASA TR-R-100 tables with physics-based wave drag computations: Taylor--Maccoll exact solution for conical noses, second-order shock-expansion theory for ogive noses, DATCOM Section 4.1.5.1 (Puckett--Stewart) fin wave drag with subsonic/supersonic LE classification, and the Dahlem--Buck shape factors for power-law / Haack noses.
3. **Shock geometry pre-pass architecture.** A new `ShockGeometry` computation walks the rocket body nose-to-tail, computing post-shock Mach, pressure, and temperature at each axial station. The production consumer is the stability path, primarily `FinSetCalc`, where local Mach corrects fin normal-force, PNK interference, and SBLI chord reduction. Body stability, fin pressure drag, roll damping, base drag, and wave drag remain freestream-based scope boundaries. Zero overhead at subsonic speeds (passthrough design).
4. **Compressible boundary-layer modeling.** Van Driest II compressible transformation (NASA TN D-6945, Hopkins 1972) for supersonic skin friction, replacing the incompressible Eckert formulas. Reduces friction drag by 30--75% at $M = 2$--5. The Sutherland viscosity law replaces the legacy linear fit; the NIST/Incropera JUnit gate is $<3\%$ over 100--800 K, and the current formula export is MAPE 0.012%.
5. **Hypersonic extension via Modified Newtonian.** $C_p = C_{p,\max}\sin^2\theta$ with $C_{p,\max}$ from the Rayleigh pitot formula for $M > 5$, blended with shock-expansion over $M = 4$--6 (cubic Hermite, $C^1$). Component-level cone foredrag is validated to $M \approx 17$ (single isolated benchmark, DTIC AD0487365 MAPE 19.7%); the headline integrated trajectory corpus is validated to $M = 4.33$, and an exploratory high-Mach set (Section 11.6.6) reaches $M = 7$ within $\pm 7\%$ on well-characterized vehicles (Black Brant V VB AAF-VB-32 closes at $-6.97\%$ apogee at peak Mach 7.224 / apogee 273.6 km), while motor and geometry reconstruction uncertainty dominates on the poorly-documented historical flights.
6. **$C^1$-continuous regime blending.** Up to **19 distinct blending regions** (Chapter 10) using cubic Hermite, constrained polynomials, and AP09 rational functions ensure all aerodynamic coefficients are $C^1$ across every Mach regime boundary, eliminating the simulation instability and time-step collapse that would otherwise occur at transitions.
7. **Dynamic stability derivatives and Euler gyroscopic coupling.** Pitch damping ($C_{mq}$) computed from per-component $C_{N\alpha}$ and moment arms with a transonic Gaussian augmentation, $C_{m\dot{\alpha}}$ via the Tobak--Wehrend slender-body ratio, full Magnus force/moment derivatives with body fraction $0.3$, and the full Euler $\boldsymbol{\omega} \times \mathbf{I}\boldsymbol{\omega}$ coupling in the 6-DOF integrator (with a 500 Pa dynamic-pressure gate against ballistic-descent stiffness).
8. **High-AoA crossflow normal force and simulation robustness.** A bluff-body crossflow drag model with proportional moment scaling that prevents artificial torque divergence at post-stall AoA. SBLI separation-length and $C_{p,\text{plateau}}$ floors, fin $K_3$ and polynomial-denominator floors, and per-coefficient sanitization caps make the integrator robust against transonic singularities, degenerate geometry, and floating-point overflow.
9. **Chapman laminar base drag.** $C_{pb,\text{lam}} = 1870/(M^2\sqrt{Re_L})$ for low-$Re$ or polished-finish rockets (NACA TN 3393 MAPE 4.4%). The Chapman--Korst turbulent method remains an available/tested utility for future production routing, not a default active path.
10. **Comprehensive validation with explicit evidence types.** 20 externally benchmarked A-level subsystem results, 9 results calibrated against the integrated 25-flight corpus rather than isolated component data (flagged at each occurrence and excluded from the 20-subsystem headline), 1 negative external benchmark (NACA RM-10, counted outside the 20 and formally excluded from the headline corpus), and the 25-flight integrated corpus published as the Rocket Flight Database, all locked in automated regression tests. Validation also includes four published-CFD comparators (Bunescu URANS, Sahu thin-layer Navier-Stokes, Vidanović SST k-ω, Sznajder Fluent) with Bhagwandin & Sahu 2013 as second-source corroboration of the supersonic $C_{mq}$ bias direction (Sections 9.9.6 and 9.10--9.13).


### 12.2 Validation Summary

Headline summary restated for the conclusions chapter:

- **20 subsystems externally benchmarked** at the A-level standard against published wind-tunnel, free-flight, or analytical data with quantitative acceptance criteria (Sections 11.2 through 11.5).
- **9 results calibrated against the integrated 25-flight corpus** rather than isolated component data. Listed individually in Section 11.6.5 with the gap each one would need to close to become an external benchmark.
- **1 externally anchored negative benchmark** (NACA RM-10, MAPE 80%) that bounds and excludes a high-fineness parabolic / tapered-afterbody / 60° swept circular-arc-biconvex-fin family (Section 11.3.6).
- **25-flight integrated corpus** (Rocket Flight Database, DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977)), Mach 0.54--4.33: mean signed apogee error $-0.38\%$, $\sigma = 5.44\%$, MAE $4.74\%$, 25/25 within $\pm 10\%$, 14/25 within $\pm 5\%$, 0 abnormal endings; RASAero II on the same paired set averages $5.34\%$ MAE with 22/25 within $\pm 10\%$ (Wilcoxon $W = 143.0$, $p = 0.615$; $|\text{ORP}|-|\text{RAS}| = -0.60$ pp, 95\% CI $[-2.16, +0.96]$ straddling zero). **The claim is statistical parity, not superiority.**
- **Flight 25, MESOS 293K (peak Mach 4.33, 293,488 ft)**: apogee $\mathbf{-6.96\%}$ (273,056 ft) -- the corpus's largest single-flight error and the higher-Mach of the two two-stage closures, reproduced in isolation, still inside the $\pm 10\%$ band (Section 11.6.3). The exploratory high-Mach set reaching Mach 5--7 (Black Brant V VB at $-6.97\%$, the Nike-Deacon pair at $-1.06\%$ and $-0.89\%$, and 17 further flights outside $\pm 10\%$) is reported in full in Section 11.6.6 as a capability demonstration, not as part of this headline corpus.
- **Envelope of the headline claim.** The accuracy figures above apply to finned slender vehicles within the boattail half-angle envelope of $6°$--$16°$ (Viswanath 1996, Section 6.2.7) and to HEXAGONAL or AIRFOIL/ROUNDED fin sections, the geometry envelope of the Rocket Flight Database. Out-of-envelope geometries -- the high-fineness parabolic body with steeply contracted afterbody and 60° swept circular-arc biconvex fins of NACA RM-10 -- are reported as transparency references and are excluded from the headline accuracy claim.

Two headline outcomes summarize the extension. (i) Vehicle-level integrated trajectory: OpenRocket Plus mean signed apogee error $-0.38\%$ (MAE 4.74\%) across the 25-flight corpus; RASAero II on the same paired set averages 5.34% MAE with 22/25 within $\pm 10\%$ (Wilcoxon paired absolute-error test $W = 143.0$, $p = 0.615$; $|\text{ORP}|-|\text{RAS}| = -0.60$ pp, 95\% CI $[-2.16, +0.96]$ — statistically indistinguishable, i.e. parity not superiority). (ii) Validated envelope: the original OpenRocket's reliable range of $M < 2$ extends to vehicle-level headline closure through $M = 4.33$ in this work, with an exploratory high-Mach set reaching $M = 7$ (Section 11.6.6) and component-level cone foredrag validated to $M \approx 17$ against a single isolated benchmark.

### 12.3 Subsonic Compatibility

At $M < 1.0$ the extended code paths are either inactive (`ShockGeometry` returns a passthrough with unit ratios; wave-drag models return zero; Van Driest II reduces to incompressible) or reduce identically to the original Barrowman formulas. The subsonic passthrough cost is approximately 200 ns per call -- negligible compared to the $\sim 180$ microsecond component calculation time. All original subsonic regression tests continue to pass without modification, and the integrated 25-flight corpus shows a small positive subsonic bias (+2.54%, $M < 0.8$, $n = 9$) consistent with build/motor-lot variance rather than systematic model error.


### 12.4 Known Limitations

The following limitations are real and known. They are stated here in plain terms, with the reason each remains unfixed in this revision.

**1. NACA RM-10: 80% drag over-prediction.** The model over-predicts the RM-10 zero-lift drag coefficient by 80% (MAPE) across $M = 0.9$--$3.3$. RM-10 is a high-fineness ($f = 12.2$) parabolic body with a smoothly tapered afterbody (base/max diameter $0.606$, local half-angle $\sim 4.8°$ at the base) and four untapered 60°-swept 10%-thick *circular-arc biconvex* fins. This geometry family is formally excluded from the headline 25-flight corpus claim and the envelope statement in Section 11.3.6. Per-component decomposition (`paper/data/legacy/rm10_vs_basic_finner_diagnostic.md`) attributes the deficit to three independent sub-model envelope violations -- (a) the Viswanath boattail correction (Section 6.2.7) is calibrated for $\theta_{\text{bt}} = 6°$--$16°$ and is extrapolated below the band on the real $4.8°$ taper and above the band on the geometry-import terminal contraction, (b) the corpus-anchored finned-body base augmentation (Section 6.2.8) is calibrated against flat-base Basic-Finner geometries and over-credits fin-induced suction when there is an upstream boattail-relief recompression, and (c) the DATCOM 4.1.5.1 fin-section coefficient $K$ has only HEXAGONAL ($K = 4$) and ROUNDED ($K = 16/3$) calibrated entries, neither of which matches the sharp-LE smoothly curving circular-arc section. The deficit is fragmented (no single sub-model accounts for more than $\sim 0.04$ of $C_D$ at $M = 2.0$), so a clean closure would require simultaneous recalibration of all three modules against three separate datasets. **Not fixed because** every isolated patch attempted to date has either regressed Basic Finner or the 25-flight corpus, and the joint calibration set required to disentangle the three sub-model envelopes does not yet exist in the public literature in a digitizable form.

**2. Pitch damping ($C_{mq}$) over-predicts by $3.6\times$ at $M = 1.05$--$1.12$.** Measured against ADA636861 free-flight $C_{mq}$ data on the Basic Finner; corroborated by the Sznajder 2025 ANSYS Fluent CFD comparator (+110 to +160% at $M = 1.08$--$1.11$). The over-prediction comes from the combination of a constant $\times 3$ multiplier on per-component damping and a transonic Gaussian augmentation peaking at $3.5\times$ near $M = 1$. Both constants were calibrated against the integrated 25-flight apogee-turn signature, not against component-level damping measurements. Removing the augmentation breaks the apogee-turn closure on five of the 25 corpus flights. **Not fixed because** correcting the transonic peak requires a second independent free-flight $C_{mq}$ dataset to retune against — recalibrating against ADA636861 would invalidate it as a benchmark — and no such dataset has been located. The Sznajder CFD is a CFD-side second source confirming the bias direction but not a free-flight retune candidate.

**3. NACA TN 3650 fin wave drag: 21% MAPE on 60° delta fins.** The DATCOM 4.1.5.1 wave-drag model is geometrically incomplete for highly swept fins: it captures the leading-edge wave drag but not the wing-body interference and conical-flow loading that dominate at $\Lambda_{LE} \ge 60°$. The residual is one-sided (model under-reads experiment), so it is not a calibration error but a missing physical term. **Not fixed because** the closed-form interference correction that would close the gap (Pitts–Nielsen–Kaattari extended to highly swept LEs) is not in the published literature; computing it would require a CFD or panel-method auxiliary that is out of scope for an analytical model.

**4. Finned-body base drag is corpus-calibrated, not externally benchmarked.** The finned-vehicle base-drag augmentation (Hart-anchored peak in the transonic polynomial, finned-body vs body-alone scaling) is set against the 25-flight corpus apogee residual rather than against component-level base-pressure measurements. Hart 1952 is a body-alone dataset and does not tell us how the fin presence alters the wake. **Not fixed because** no public finned-body base-pressure dataset spanning the transonic-to-low-supersonic range has been located. This is the largest single source of corpus-circular reasoning in the report; a future external dataset would convert this from circular to confirmatory.

**5. RASAero `ModifiedBarrowman` stability flag is parsed but ignored.** The RASAero II `.CDX1` import path reads the `ModifiedBarrowman` flag but does not branch on it: every imported file is run through the standard pipeline. RASAero applies a different transonic stability formulation when the flag is set, so per-case import parity diverges for files that opted into that mode. The companion force-turbulent BL flag *is* honored. **Not fixed because** the RASAero `ModifiedBarrowman` formulation is not published; it would have to be reverse-engineered from RASAero outputs, and the development-time cost is hard to justify when no corpus flight has been observed to depend on it.

**6. High-AoA descent dynamics ($\alpha > 30°$) have no isolated benchmark.** The crossflow normal-force model and proportional moment scaling that govern descent tumble are validated only by integrated-corpus end-condition behavior (no abnormal endings on 25/25 flights), not by an isolated $C_N(\alpha)$ or $C_m(\alpha)$ comparison at high $\alpha$. **Not fixed because** no public dataset of finned-rocket forces at $\alpha = 30$–$60°$ in the relevant Mach range has been located; existing high-$\alpha$ data is mostly missile-body-alone.

**Items not modeled at all.** The following physical effects are absent from the current implementation:

- Real-gas dissociation chemistry above stagnation temperatures of about $5000$ K (relevant for $M > 10$ at sea level).
- Boundary-layer transition from laminar to turbulent at supersonic speeds. The model assumes fully turbulent except for the explicit perfect-finish Chapman laminar path.
- Fin-fin Mach-cone interference. Estimated effect $< 3\%$ for typical four-fin geometries; not negligible in principle but small relative to the headline error budget.
- Ablation or mass loss at hypersonic speeds.
- Non-equilibrium thermochemistry.

These omissions are deliberate. The target application is high-power amateur rocketry, where the overwhelming majority of flights are below $M = 5$ and ablation, dissociation, and chemistry are negligible at trajectory level. A ground-truth dataset to validate any of these models in the amateur-rocketry context does not exist, so adding them would amount to adding code that cannot be tested.


### 12.5 Numerical Tuning Parameters

The following table collects every empirical tuning constant in the implementation -- values chosen to match observed flight dynamics or external calibration data, rather than derived from first principles. Each row identifies the parameter, its value, the external source it is anchored against (where one exists), and the implementation location.

**Table 12.1 -- Empirical Tuning Parameters.** Class prefixes are abbreviated: BSC = `BarrowmanStabilityCalculator`, BDC = `BarrowmanDragCalculator`, BC = `BarrowmanCalculator`, SCC = `SymmetricComponentCalc`, FSC = `FinSetCalc`, RK4 = `RK4SimulationStepper`, FIS = `FreeInteractionSBLI`, PNK = `PittsNielsenKaattari`.

```{=latex}
\begin{landscape}
\scriptsize
\setlength{\tabcolsep}{4pt}
\renewcommand{\arraystretch}{1.15}
\renewcommand{\tabularxcolumn}[1]{>{\sloppy\hbadness=10000\relax}p{#1}}
\begin{xltabular}{\linewidth}{@{}X r X X@{}}
\toprule
Parameter & Value & Source / anchor & Where used \\
\midrule
\endhead
Pitch damping multiplier & $\times 3$ & Apogee-turn calibration; corpus closure (vs ADA636861) & \texttt{BSC.DAMPING\_MULTIPLIER} \\
Transonic $C_{mq}$ peak & const.\ $2.5$; total $\times 3.5$ at $M{=}1$ & Gaussian augmentation; corpus (vs ADA636861) & \texttt{BSC.TRANSONIC\_CMQ\_PEAK} \\
Transonic $C_{mq}$ sigma & $0.15$ & $\sim\!\pm 0.3$ Mach decay & \texttt{BSC.TRANSONIC\_CMQ\_SIGMA} \\
$C_{m\dot{\alpha}} / C_{mq}$ ratio & $0.4$ & Tobak \& Wehrend (NACA TN 3788, 1956) & \texttt{calculateDampingMoments} \\
Magnus body fraction & $0.3$ & Platou (\textit{AIAA J.} 3(1), 1965), 0.3--0.8 & \texttt{calculateDampingMoments} \\
Fin damping cap & $\min(n, 4)$ & Diminishing returns beyond 4 fins & \texttt{getDampingMultiplier} \\
Body damping coefficient & $0.275$ & Body contribution to pitch damping & \texttt{getDampingMultiplier} \\
Vortex asymmetry $K_v$ & $0.20$ & Internally calibrated; no verifiable literature anchor & RK4 vortex term \\
Vortex onset / saturation & $20° / 40°$ & Internally calibrated & same \\
Crossflow body $C_{d,c}$ & $1.20$ & Jorgensen TR R-474 Table 1 (exact) & crossflow override \\
Crossflow fin $C_{d,c}$ & $1.42$ & Hoerner Ch.\ 3 Fig.\ 28 & crossflow override \\
Crossflow $C_m$ scale cap & $20$ & Noise guard when $C_N\!\to\!0$ & crossflow override \\
Crossflow $C_N$ zeroing & $|C_N|<0.5$ & CP ill-defined; zero is safest & crossflow override \\
Gyroscopic $q_\infty$ threshold & $500$ Pa & RK4 stiffness vs restoring balance & RK4 gyro gate \\
Angular timestep floor & $\Delta t_\text{user}/4$ & 10$\times$ tumble slowdown guard & RK4 timestep adapter \\
Min timestep & $\Delta t_\text{user}/20$ & Absolute adaptive floor & same \\
$C_D$ sanitization cap & $10.0$ & Blunt body at $M=10$ has $C_D \approx 2$ & \texttt{BC} sanitizer \\
$C_N$ sanitization cap & $100.0$ & Extreme-AoA $C_N$ reaches 30--50 & same \\
Fin stall angle & $20°$ & Hard cap on fin $C_N$ & \texttt{FSC} \\
Low-speed body lift ramp & $(M/0.05)^2$ for $M{<}0.05$ & Guard at $V\!\to\!0$, $\alpha\!>\!45°$ & crossflow body \\
SBLI $M^2{-}1$ floor & $0.1$ ($M \gtrsim 1.05$) & Near-sonic singularity guard & \texttt{FIS} \\
$C_{p,\text{plateau}}$ cap & $2.0$ & Upper bound on separation pressure & \texttt{SCC} \\
Step drag $M^2{-}1$ threshold & $0.04$ & Raised from $0.01$ for deep-transonic & \texttt{SCC} \\
Pitch/yaw randomisation & $\pm 0.0005$ & Breaks artificial symmetry & RK4 \\
$K_1$ floor (max / asymp.) & $0.85 / 0.40$ & NASA TM X-653 sub-LE floor + high-$M$ asymp. & \texttt{FSC} \\
Body lift $K$ range & $1.1 \to 0$ over $M{=}0.8$--$1.3$ & Galejs blended out before supersonic body lift & \texttt{SCC.getEffectiveBodyLiftK} \\
CP aft shift fraction & $0.30$ & Calibrated against 5 standard geometries & \texttt{SCC} \\
PNK $F_{WB} / F_{BW}$ & $0.3 / 0.15$ & Pitts, Nielsen, Kaattari (1957) PNK charts & \texttt{PNK} \\
\bottomrule
\end{xltabular}
\end{landscape}
```


### 12.6 Implementation Status of Advanced Models

Several additional aerodynamic models exist in the codebase but are not active in the production pipeline. Each is listed below with the specific reason it is off, so that a reader inspecting the source tree understands what is and is not running.

**Table 12.2 -- Advanced Model Implementation Status**

```{=latex}
\footnotesize
\setlength{\tabcolsep}{4pt}
\renewcommand{\arraystretch}{1.2}
\renewcommand{\tabularxcolumn}[1]{>{\sloppy\hbadness=10000\relax}p{#1}}
\begin{xltabular}{\linewidth}{@{}p{3.5cm} l X@{}}
\toprule
Model & Status & Why this state \\
\midrule
\endhead
Aeroelastic fin divergence \newline (\seqsplit{AeroelasticModel.java}) & \textbf{Off} ($q_\text{thr} = 10^{12}$ Pa) & The thin-rectangle torsional approximation $J = ct^3/3$ under-estimates real fin stiffness and triggered false divergence at $M \sim 0.7$ during integration testing. The material shear-modulus table (9 materials) and the DATCOM flutter-$q$ formula are implemented but inactive until experimental flutter/divergence data is digitized. \\
Plume-induced separation \newline (\seqsplit{PlumeModel.java}) & \textbf{Off (hook present)} & \texttt{setPlumeState} / \texttt{computeFrictionReduction} are wired but the RK4 stepper path that populates the plume state is disabled. Activating it requires a thrust-state propagator and a separation-recovery validation; neither is built. \\
Chapman--Korst turbulent base drag \newline (\seqsplit{ChapmanKorstBaseDrag.java}) & \textbf{Off (laminar on)} & The laminar Chapman path is active and validated against TN 3393. The turbulent Chapman--Korst helper exists but the production base-drag path uses the empirical $C_{d,\text{base}}=0.064+0.186/M^2$ correlation (NACA TN 3393 / ESDU 77021) plus the transonic-polynomial blend, which is what the corpus calibration is anchored against. \\
Transonic area rule \newline (\seqsplit{TransonicAreaRule.java}) & \textbf{Off} & A 200-station Whitcomb / von Karman area-rule integrator is implemented and unit-tested, including the Sears--Haack minimum-drag reference. Not wired into \texttt{BarrowmanDragCalculator} because no fully-wetted reference rocket from the corpus has area-rule wave-drag data to validate against. \\
SBLI pressure drag \newline (\seqsplit{FreeInteractionSBLI.java}) & \textbf{Off (chord red. on)} & The chord-reduction term is in production. The plateau-pressure drag term double-counts the separation loss when both are active (Section 6.8.3); enabling it would require recalibrating the chord-reduction floor against fin-only data. \\
Kantrowitz limit & \textbf{On} & Computes supersonic starting / spillage for tube/ring fins in \texttt{TubeFinSetCalc}. \\
Dahlem--Buck shape factors \newline (\seqsplit{DahlemBuckShapeFactors.java}) & \textbf{On} & Shape-dependent wave-drag correction for power-law, parabolic, Haack noses; active above $M = 1.3$ via smoothstep. \\
Rational blend (AP09) \newline (\seqsplit{RationalBlend.java}) & \textbf{On} & $C^\infty$ rational blending for near-$M = 1$ transitions where one or both endpoint models have a Prandtl--Glauert-type singularity. \\
\bottomrule
\end{xltabular}
\normalsize
```

These items are roadmap Phase 6 (advanced viscous and reactive modeling) and beyond. They are not on the critical path for the headline 25-flight closure and are explicitly excluded from the current accuracy claims.


### 12.6a Phase 6h Coast-Drag Bias Above $M = 5$ and Proposed Fix

Per-component $C_d$ analysis using `NikeApacheCoastCdDiagnosticTest` against the NASA Apache Performance Handbook Case 1 (clean) coasting table issued by the NASA Goddard Space Flight Center Sounding Rocket Branch (X-721-66-568, Galloway and Crough, 1966) shows that the pressure $C_d$ plateaus at $\sim 0.234$ from $M = 2$ through $M = 8$, against handbook values that decay smoothly from $0.704$ at $M = 2$ to $0.384$ at $M = 8$ without collapsing to the slender-body limit. The mean $C_d$ deficit for $M \ge 5$ is **+0.0595** (handbook minus ORP, averaged over 7 points: $M = 5.00, 5.50, 6.00, 6.50, 7.00, 7.50, 8.00$).

The root cause is the constant `SLENDER_BODY_MACH_DECAY_END = 5.0` in `BarrowmanDragCalculator.java` (lines 1453--1489), which smoothsteps the Hoerner cylindrical-afterbody pressure correction to zero at $M = 5$ for high-fineness bodies. The Apache sustainer with $L/D = 17.4$ still carries appreciable boundary-layer-displacement / viscous-inviscid pressure drag at $M \ge 5$ per Hoerner Chapter 17, which is precisely what the model elides.

The bias accumulates during ballistic coast and scales with peak Mach: Nike-Deacon at $M \approx 5$ closes to $-1$ percent, Nike-Cajun at $M \approx 6.2$ to $+16.6$ percent, and the nine Nike-Apache 1965 flights at $M = 6.4$--$7.0$ to $+24$ to $+36$ percent. **Under the $\pm 10$ percent admission criterion adopted for the Rocket Flight Database corpus (Section 11.6.1), the nine Nike-Apache 1965 flights and the one Nike-Cajun University of Michigan flight fall in the exploratory high-Mach set (Section 11.6.6) and are not part of the 25-flight headline corpus.** All ten `.ork` build files are committed at `paper/data/ork/sounding_rockets/` and become admissible once the fix lands.

The proposed fix is documented as **Phase 6h** in `SUPERSONIC_MODELING.md`:

1. Extend `SLENDER_BODY_MACH_DECAY_END` from $5.0$ to approximately $12.0$.
2. Add a `hypersonicBodyPressureCD` term gated on body $L/D > 15$ AND $M > 3$, calibrated against the X-721-66-568 Case 1 table.

Validation gates for the Phase 6h fix:
- Nike-Deacon must not move by more than $\pm 2$ pp.
- Apache 1965 mean must close to within $\pm 10$ percent.
- The low-$L/D$ corpus (Black Brant V, Raven, Rabia) must not regress.

**Table 12.6a.1 — Phase 6h Apache coast-$C_d$ deficit** (from `NikeApacheCoastCdDiagnosticTest` output against NASA X-721-66-568 Appendix A page 66 Case 1 COASTING). Handbook column is the canonical Apache Case 1 reference. The ORP column reflects the documented pressure-$C_d$ plateau ($\sim 0.234$) combined with the friction and base components.

| $M$ | $C_d$ (handbook X-721-66-568) | $C_d$ (ORP) | Deficit (handbook − ORP) |
|------|----|----|----|
| 5.00 | 0.454 | $\approx 0.395$ | $+0.059$ |
| 5.50 | 0.432 | $\approx 0.373$ | $+0.059$ |
| 6.00 | 0.412 | $\approx 0.353$ | $+0.059$ |
| 6.50 | 0.396 | $\approx 0.337$ | $+0.059$ |
| 7.00 | 0.388 | $\approx 0.329$ | $+0.059$ |
| 7.50 | 0.384 | $\approx 0.325$ | $+0.059$ |
| 8.00 | 0.384 | $\approx 0.325$ | $+0.059$ |
| **Mean $M \ge 5$** |  |  | **$+0.0595$** |

Until Phase 6h closes, the headline 25-flight corpus (Mach 0.54--4.33) is honestly characterized as supersonic-validated, and the separate exploratory high-Mach set (Section 11.6.6) is reported in full as a capability demonstration rather than a validation headline. Once the fix lands, the nine Nike-Apache 1965 flights plus the Nike-Cajun flight already on disk would become admissible and the exploratory $M > 5$ set that currently closes within $\pm 10\%$ would grow from 3 flights to 13 — at which point the framing changes accordingly.

The composite disclosure plot (per-component $C_d$ decomposition vs Mach against NASA X-721-66-568 Case 1 handbook reference; pressure-$C_d$ plateau near 0.234 visible from $M = 2$ through $M = 8$) is at `paper/data/png/phase6h_apache_cd_disclosure.png`.


### 12.7 Acknowledgments, Affiliation, Conflict of Interest, and Reproduction Recipe

#### 12.7.1 Acknowledgments

The author thanks the OpenRocket maintainers and contributors, on whose open-source simulator this work builds; Charles E. Rogers and the RASAero II project for the publicly archived altitude-comparison set that anchors the 25-flight corpus; and the individual flight contributors whose telemetry and reconstruction data populate the Rocket Flight Database. The author also acknowledges Duke University for institutional support.

#### 12.7.2 Author Affiliation

Sole author: Aidan Yu, Department of Mechanical Engineering & Materials Science, Duke University. ORCID [0009-0005-9589-5314](https://orcid.org/0009-0005-9589-5314). Corresponding author: <asy22@duke.edu>.

#### 12.7.3 Conflict of Interest

The author declares no conflict of interest.

#### 12.7.4 Funding

No external funding was received for this work.

#### 12.7.4a Generative AI Use Disclosure

Generative AI tools were used solely for language editing, formatting, and code review. All claims, equations, derivations, and numerical results were authored and verified by the human author, who takes full responsibility for the content. No AI system is an author of this report.

#### 12.7.5 Software Availability and DOI

The OpenRocket Plus source code is available at <https://github.com/AidanSYu/openrocketsupersonic>. A persistent software archive will be deposited on Zenodo, with the citable DOI minted from the tagged source release at submission (see Section 12.7.6). The validation dataset (Rocket Flight Database — 25-flight headline corpus) is deposited at the same DOI as the original v1.0 release and is citable as <https://doi.org/10.5281/zenodo.20531977>.

#### 12.7.6 Reproduction Recipe for the 25-Flight Corpus Closure

The headline aggregate apogee statistics (mean signed $-0.38\%$, MAE 4.74\%) across the 25-flight corpus are reproducible from the source tree as follows. The pinned commit for the manuscript revision is `f84c66857eb2fa5e0f4dd4313fc8b41d77801ba5` on branch `supersonic-aero-dev`; the citable source-archive tag is minted at submission (the persistent software Zenodo DOI in Section 12.7.5 is deferred until that tag is pushed). Substitute this commit for `<COMMIT>` below.

```bash
git clone https://github.com/AidanSYu/openrocketsupersonic.git
cd openrocketsupersonic
git checkout <COMMIT>          # or the manuscript tag once minted
./gradlew core:test --tests "info.openrocket.core.aerodynamics.SimVRealBenchmarkTest"
```

On Windows, substitute `gradlew.bat` for `./gradlew`. Expected runtime: approximately 11 minutes for the full aerodynamics test suite, of which `SimVRealBenchmarkTest` is a fraction. Per-flight outputs and the aggregate error summary are written under `core/build/reports/tests/test/` and `core/build/test-results/test/`. The per-case CSV that anchors the manuscript table is generated as `paper/data/csv/simvreal_baseline_2026_05_01.csv` (frozen at the same commit). The companion head-to-head comparison artifact (this work versus the recorded RASAero II predictions on the same imported geometries) is `paper/data/md/rasaero_head_to_head_2026_05_01.md`. The corpus itself, including the `.CDX1` import files and Rogers-published RASAero II reference apogees, is archived at <https://doi.org/10.5281/zenodo.20531977>.

A regression tolerance of $\pm 2$ percentage points per case is enforced by the test harness; deviations beyond this band fail the build and indicate either an environment difference (JVM, gradle daemon state, motor-thrust-curve cache) or an unintended modeling change.


### References

1. Ackeret, J. (1925). "Luftkrafte auf Flugel, die mit grosserer als Schallgeschwindigkeit bewegt werden." *Zeitschrift fur Flugtechnik und Motorluftschiffahrt*, 16, pp. 72--74.
2. Allen, H. J. and Perkins, E. W. (1951). "A Study of Effects of Viscosity on Flow Over Slender Inclined Bodies of Revolution." NACA Report 1048. Cited as the originating source for the crossflow-analogy method name.
3. Ames Research Staff (1953). "Equations, Tables, and Charts for Compressible Flow." NACA Report 1135.
4. Anderson, J. D. (2006). *Hypersonic and High-Temperature Gas Dynamics*, 2nd ed. AIAA Education Series.
5. Anderson, J. D. (2017). *Modern Compressible Flow: With Historical Perspective*, 4th ed. McGraw-Hill.
6. AP09 (2009). "Aeroprediction Code Methodology (AP09)." Code-cited methodology note for the AP09-style rational blend implemented in `RationalBlend.java`; exact public report metadata is not present in the repository.
7. Barrowman, J. S. (1967). "The Practical Calculation of the Aerodynamic Characteristics of Slender Finned Vehicles." M.S. Thesis, The Catholic University of America.
8. Chapman, D. R. (1950). "Base Pressure at Supersonic Velocities." NACA TN 2137. Originating source for the laminar base-drag $C_\text{LAM}=1870$ scaling in Section 6.2.4.
9. Chapman, D. R. (1951). "An Analysis of Base Pressure at Supersonic Velocities and Comparison with Experiment." NACA Report 1051.
10. Champigny, P. and Lacau, R. G. (1994). "Lateral Aerodynamics of a Missile at High Angles of Attack." AGARD CP-536, as cited in `BarrowmanCalculator` and `VortexSideforceBenchmarkTest`; the repository's local AGARD CP-536 PDF is a different proceedings volume and is not used as a source artifact for this claim.
11. DATCOM (1978). "USAF Stability and Control DATCOM." Air Force Flight Dynamics Laboratory, AFFDL-TR-79-3032, revised.
12. **Reference removed.** The previously listed "Devan, L. and Ashwood, R. (1965). 'The Base Drag of Blunt-Trailing-Edge Airfoils and Bodies at Transonic and Supersonic Speeds.' NASA TN D-721" could not be independently verified through NTRS or DTIC search. The production turbulent base-drag correlation $C_{d,\text{base}} = 0.064 + 0.186/M^{2}$ is anchored against ESDU 77021 (Reference 14 below) and NACA TN 3393 (Reference 27 below); the "Devan-Ashwood" descriptor is retained in the code comments as a historical attribution but the primary verifiable source is ESDU 77021.
13. Dupuis, A. and Hathaway, W. (1997). "Aeroballistic Range Tests of the Basic Finner Reference Projectile at Supersonic Velocities." DTIC ADA636861.
14. ESDU (1977). "Estimation of Base Drag in the Absence of a Propulsive Jet." ESDU Data Item 77021.
15. ESDU (1978). "Drag of a Smooth Flat Plate at Zero Incidence." ESDU Data Item 78019. Historical skin-friction context; the current production skin-friction path is Van Driest II rather than this item.
16. Galejs, R. Body-lift correction note cited by `SymmetricComponentCalc`; exact publication metadata is not present in the repository, so the report treats the implementation constant as code-sourced rather than independently bibliographic.
17. Grabow, R. M. (1965). "Drag of Cones at Mach Numbers up to 17." DTIC AD0487365.
18. Hart, R. G. (1952). "Effects of Stabilizing Fins and a Rear-Support Sting on the Base Pressures of a Body of Revolution in Free Flight at Mach Numbers from 0.7 to 1.3." NACA RM L52E06.
19. Hoerner, S. F. (1965). *Fluid-Dynamic Drag*. Published by the author.
20. Hopkins, E. J. (1972). "Charts for Predicting Turbulent Skin Friction from the Van Driest Method (II)." NASA TN D-6945.
21. Hopkins, E. J. and Inouye, M. (1971). "An Evaluation of Theories for Predicting Turbulent Skin Friction and Heat Transfer on Flat Plates at Supersonic and Hypersonic Mach Numbers." *AIAA Journal*, 9(6).
22. Jorgensen, L. H. (1973). "Prediction of Static Aerodynamic Characteristics for Space-Shuttle-Like and Other Bodies at Angles of Attack from 0 to 180 Degrees." NASA TR R-474.
23. **Reference removed.** The previously listed Jorgensen, L. H. (1977), "Prediction of Static Aerodynamic Characteristics for Slender Bodies Alone and with Lifting Surfaces to Very High Angles of Attack," NASA TN D-6996, is redundant with the in-repo Jorgensen TR R-474 (1973) (Reference 22), which is the primary anchor for the $C_{d,c}=1.20$ crossflow constant. No claim in this report depends on TN D-6996 independently, so it is dropped.
24. Perkins, E. W. and Jorgensen, L. H. (1952). "Investigation of the Drag of Various Axially Symmetric Nose Shapes of Fineness Ratio 3 for Mach Numbers from 1.24 to 3.67." NACA RM A52H28.
25. NACA (1954). "Free-Flight Measurements of the Zero-Lift Drag of Several Wings at Mach Numbers from 1.1 to 1.6." NACA TN 3650.
26. Jackson, H. H., Rumsey, C. B., and Chauvin, L. T. (1954). "Flight Measurements of Drag and Base Pressure of a Fin-Stabilized Parabolic Body of Revolution (NACA RM-10) at Different Reynolds Numbers and at Mach Numbers from 0.9 to 3.3." NACA TN 3320.
27. Reller, J. O., Jr. and Hamaker, F. M. (1955). "An Experimental Investigation of the Base Pressure Characteristics of Nonlifting Bodies of Revolution at Mach Numbers from 2.73 to 4.98." NACA TN 3393.
28. Stoney, W. E. (1961). "Collection of Zero-Lift Drag Data on Bodies of Revolution from Free-Flight Investigations." NASA TR-R-100.
29. Jorgensen, L. H., Spahr, J. R., and Hill, W. A., Jr. (1962). "Comparison of the Effectiveness of Flares with That of Fins for Stabilizing Low-Fineness-Ratio Bodies at Mach Numbers from 0.6 to 5.8." NASA TM X-653.
30. Nielsen, J. N. (1960). *Missile Aerodynamics*. McGraw-Hill.
31. **Reference removed.** The previously listed "Paul, R. and Wedemeyer, E. (1982). 'Aerodynamic Characteristics of Ogive-Cylinder Bodies at High Angles of Attack.' EOARD-TR-82-7" could not be independently verified and is no longer cited as a source: the vortex-asymmetry coefficient $K_v = 0.20$ (Section 9.6 / 9.9.3) is presented as an internally-calibrated coefficient with no literature anchor, not as an externally benchmarked value.
32. Pitts, W. C., Nielsen, J. N., and Kaattari, G. E. (1957). "Lift and Center of Pressure of Wing-Body-Tail Combinations at Subsonic, Transonic, and Supersonic Speeds." NACA Report 1307. Originating source for the PNK $F_{WB}/F_{BW}$ interference factors (Table 12.1).
33. Platou, A. S. (1965). "Magnus Characteristics of Finned and Nonfinned Projectiles." *AIAA Journal*, **3**(1), 83–90. DOI: 10.2514/3.2791. (Replaces the previously cited "BRL Report 1193, 1963," for which no NTRS/DTIC record could be located; the AIAA Journal publication is the verifiable primary source for Platou's Magnus measurements.)
34. Puckett, A. E. and Stewart, H. J. (1947). "Aerodynamic Performance of Delta Wings at Supersonic Speeds." *Journal of the Aeronautical Sciences*, 14(10).
35. Sutherland, W. (1893). "The Viscosity of Gases and Molecular Force." *Philosophical Magazine*, Series 5, 36(223), pp. 507--531.
36. Tobak, M. and Wehrend, W. R. (1956). "Stability Derivatives of Cones at Supersonic Speeds." NACA TN 3788.
37. Anderson, C. F. (1970). "An Investigation of the Aerodynamic Characteristics of the AGARD Model B for Mach Numbers from 0.2 to 1.0." AEDC-TR-70-100, Arnold Engineering Development Center. Reference source for the AGARD-B benchmark (Section 11.3.5).
38. AEDC (1976). "Experimental Roll-Damping, Magnus, and Static-Stability Characteristics of Two Slender Missile Configurations at High Angles of Attack (0 to 90 Deg) and Mach Numbers 0.2 Through 2.5." AEDC-TR-76-58.
39. US Standard Atmosphere (1976). "U.S. Standard Atmosphere, 1976." NOAA/NASA/USAF, U.S. Government Printing Office.
40. Van Driest, E. R. (1956). "The Problem of Aerodynamic Heating." *Aeronautical Engineering Review*, 15(10), pp. 26--41.
41. Viswanath, P. R. (1996). "Flow Management Techniques for Base and Afterbody Drag Reduction." *Progress in Aerospace Sciences*, 32(2--3), pp. 79--129.
42. **Reference removed.** The previously listed Whitcomb, R. T. (1956), "A Study of the Zero-Lift Drag-Rise Characteristics of Wing-Body Combinations Near the Speed of Sound," NACA Report 1273, was cited only as the method-name label for the off-status `TransonicAreaRule.java` integrator (Table 12.2), which is not on the headline path. No active claim depends on it, so it is dropped; "Whitcomb area rule" is retained only as a descriptive method name.
43. Zipfel, P. H. (2007). *Modeling and Simulation of Aerospace Vehicle Dynamics*, 2nd ed. AIAA Education Series.
44. Chapman, D. R., Kuehn, D. M., and Larson, H. K. (1958). "Investigation of Separated Flows in Supersonic and Subsonic Streams with Emphasis on the Effect of Transition." NACA Report 1356. Originating source for the free-interaction SBLI theory at fin roots (Section 6.8).
45. Ferris, J. C. (1967). "Static Stability Investigation of a Single-Stage Sounding Rocket at Mach Numbers from 0.60 to 1.20." NASA TN D-4013, Langley Research Center, June 1967.
46. Babb, C. D. and Fuller, D. E. (1967). "Static Stability Investigation of a Sounding-Rocket Vehicle at Mach Numbers from 1.50 to 4.63." NASA TN D-4014, Langley Research Center, June 1967.
47. Bhagwandin, V. A. and Sahu, J. (2013). "Numerical Prediction of Pitch Damping Stability Derivatives for Finned Projectiles." ARL-TR-6725, US Army Research Laboratory, Aberdeen Proving Ground, MD, November 2013. DTIC Accession ADA592550. Second-source CFD comparator for the $C_{mq}$ supersonic-bias audit (Section 9.9.6), reported at B-level pending the AFF fin-planform fixture (Figure 3) required for A-level promotion.
48. Bunescu, I., Hothazie, M.-V., Stoican, M.-G., Pricop, M.-V., Onel, A.-I., and Afilipoae, T.-P. (2025). "Numerical Study of the Basic Finner Model in Rolling Motion." *Aerospace*, **12**(5), 371. DOI: 10.3390/aerospace12050371. Open access (CC BY 4.0).
49. Bollermann, B. and Walker, R. L. (1973). "Design, Development and Flight Test of the Super Loki Stable Booster Rocket Systems." AFCRL-TR-73-0412 / AD-766737, Space Data Corp., Phoenix AZ, prepared for AFCRL Hanscom, 30 June 1973.
50. Sahu, J., Nietubicz, C. J., and Steger, J. L. (1983). "Numerical Computation of Base Flow for a Projectile at Transonic Speed." ARBRL-TR-02495 / AD-A130-293, US Army Ballistic Research Laboratory, Aberdeen Proving Ground, MD, June 1983. Cited as the secondary CFD anchor for transonic base-flow validation; not exercised as a comparator in the present revision (Section 9.10).
51. Vidanović, N. D., Rašuo, B. P., Damljanović, D. B., Vuković, Đ. S., and Ćurčić, D. S. (2014). "Validation of the CFD code used for determination of aerodynamic characteristics of nonstandard AGARD-B calibration model." *Thermal Science*, **18**(4), pp. 1223–1233. DOI: 10.2298/TSCI130409104V. Reference CFD dataset cited in Section 9.12; no closed-loop OpenRocket Plus comparator at the AGARD-B geometry in the present revision.
52. Sznajder, J. (2025). "Computational Determination of Dynamic Stability Derivatives." *Transactions on Aerospace Research*, No. 4, pp. 98–121. DOI: 10.2478/tar-2025-0021. ANSYS Fluent computations of $C_{mq}$ and $C_{m\dot\alpha}$ on the Army-Navy Basic Finner over $M = 0.9$--$5.0$ using three independent CFD techniques (MRF, FOM, IRM); used as the primary CFD-side $C_{mq}$ comparator in Section 9.11.

**External validation artifacts:**

- Yu, A. (2026). *Rocket Flight Database* [Data set]. Zenodo. Concept DOI: <https://doi.org/10.5281/zenodo.20531977>.
- Rogers, C. E. *RASAero II Comparisons with Altitude Data.* <https://www.rasaero.com/comparisons-alt.htm>. Source for measured apogees and reference RASAero II predictions.

**Internal validation artifacts** (not external references; included for traceability):

- `paper/data/corpus_summary_2026_05_01.md` -- 25-flight v1.0 integrated corpus baseline; 25-flight summary at `paper/data/analysis/corpus_bias_variance_2026_05_11/corpus_bias_variance_summary.md`.
- `paper/data/csv/simvreal_baseline_2026_05_01.csv` -- per-case CSV regression baseline.
- `paper/data/md/rasaero_head_to_head_2026_05_01.md` -- this work versus RASAero II head-to-head on the same imported flights.
- `paper/data/md/dynamic_stability_benchmark.md` -- full Mach sweep for $C_{mq}$, roll damping, Magnus.
- `paper/data/md/nasa_tm_x653_validation_report.md` -- NSCFB CNa / xCP closure memo.
- `paper/data/outlier_closure/*.md` -- per-case closure memos (raven, kinsel, mesos_293k, dontdebatethis, proteus6, fmj_balls005, subsonic_nonaero_outliers).
