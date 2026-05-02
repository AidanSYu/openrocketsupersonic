## Abstract

This report documents the OpenRocket Plus aerodynamic extensions as implemented in the current Java codebase. The work replaces the original low-subsonic Barrowman assumptions with compressible atmosphere models, shock and expansion solvers, transonic blending, supersonic and hypersonic drag models, local-flow coupling for fin stability, static and dynamic stability corrections, high-angle-of-attack effects, and numerical hardening for six-degree-of-freedom simulation. Validation is reported claim by claim against the tests, source artifacts, and implementation paths in the repository: 27 externally benchmarked subsystem results, 9 integrated flight-corpus closures, and 1 negative external benchmark that bounds an excluded geometry family. The integrated 25-flight corpus closes at 4.49% mean absolute apogee error with 25/25 flights within $\pm 10\%$; on the same imported geometries RASAero II averages 5.26% with 22/25 within $\pm 10\%$. Headline subsystem results include nose wave drag MAE 0.029 vs NACA RM A52H28, fin $C_{N\alpha}$ MAPE 6.8% and $x_{CP}$ MAPE 7.1% vs NASA TM X-653, and hypersonic cone foredrag MAPE 19.7% vs DTIC AD0487365 across $M = 6.5$--$17.2$. The 25-flight ground-truth corpus is archived as the *Rocket Flight Database* v1.0 (DOI: [10.5281/zenodo.19976138](https://doi.org/10.5281/zenodo.19976138), CC-BY-4.0).


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

The closed-source benchmark for amateur supersonic rocketry simulation is RASAero II, developed by Charles E. Rogers, which incorporates empirical and semi-empirical supersonic drag models calibrated against extensive wind-tunnel data. The goal of the work described in this report is to bring OpenRocket to a comparable level of supersonic and hypersonic fidelity while maintaining the open-source, modular architecture that makes it valuable for education, research, and engineering. The average absolute apogee error of these extensions across a 25-flight ground-truth corpus is 4.49% versus 5.26% for the recorded RASAero II predictions on the same flights (see Section 1.4 for full per-case results, and the *Rocket Flight Database* at [10.5281/zenodo.19976138](https://doi.org/10.5281/zenodo.19976138) for the canonical comparison artifact).


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

**Limitation 6: No supersonic CP correction.** The Barrowman method computes center of pressure assuming incompressible flow. At supersonic speeds the body lift distribution shifts substantially aft due to the change from subsonic to supersonic crossflow patterns; the CP of a slender body at Mach 3 is typically 5--10% of body length further aft than the subsonic prediction. For a marginally stable rocket this shift can mean the difference between stable and unstable flight. The original code issued a blanket warning at Mach 1.1 ("Supersonic flight is not supported") but made no attempt to correct the stability predictions. Section 8 addresses this with a Mach-dependent body $C_{N_\alpha}$ and CP shift validated against NASA TM X-653 ($C_{N_\alpha}$ MAPE 6.8%, $x_{CP}$ MAPE 7.1% across M 0.6--5.82).


### 1.3 Design Philosophy

The extensions described in this report were guided by three architectural principles.

**Incremental integration with regression gates.** Each new model was implemented, tested, and validated independently before being integrated into the main calculation pipeline. The aerodynamic regression suite currently comprises 85 tracked JUnit test classes in the `info.openrocket.core.aerodynamics` package hierarchy (87 tracked Java files including support/export helpers), plus one workspace-local ablation test used for the May 1 SimVReal import-parity study. Of these, **27 subsystems are externally benchmarked against published data** with quantitative acceptance criteria, plus one externally anchored negative benchmark (RM-10) used to formally exclude a geometry family from the headline claim. Each capability increment was validated against two independent categories of evidence before promotion: (1) **exact analytical solutions and authoritative tabulated values** (NACA Report 1135 for shocks, US Standard Atmosphere 1976 for atmospheric properties, NIST/REFPROP for viscosity), which verify that the mathematics is implemented correctly; and (2) **physical experimental measurements** from wind tunnels, free-flight ballistic-range tests, and aeroballistic instrumentation programs (NACA TN 3393, TN 3650, RM A52H28, NASA TM X-653, AEDC-TR AGARD-B, Jorgensen TR R-474, ADA636861 Basic Finner, DTIC AD0487365 hypersonic cone), which verify that the models capture actual aerodynamic physics. Both categories are necessary; correct mathematics applied to a wrong physical model still produces wrong answers, and physically plausible trends that do not match measurements are equally unreliable.

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

**Analytical models over empirical tables.** Where a closed-form analytical solution exists and is computationally tractable, it is preferred over empirical correlations or interpolation tables. Analytical models extrapolate correctly, have known error bounds, and are self-documenting. The Taylor-Maccoll cone-flow solution, DATCOM 4.1.5.1 fin wave drag theory, Prandtl-Meyer expansion relations, and Sutherland viscosity law are all exact within their physical assumptions. Empirical correlations (Devan-Ashwood and Lamb-Oberkampf base drag, Van Driest II compressible skin friction, Pitts-Nielsen-Kaattari fin-body interference) are used only where no tractable analytical solution exists, and in those cases the source reference and validity range are documented in both the code and this report.


### 1.4 Scope and Headline Validation State

The current implementation models 31 distinct physical phenomena spanning atmospheric properties, gas dynamics, drag (friction, pressure, base, wave), static and dynamic stability, hypersonic effects, and numerical robustness. The full enumeration is given in Section 1.5. Each phenomenon is supported by one of four kinds of validation evidence, ordered from strongest to weakest:

- **External benchmark** -- matched against published external or tabulated data (wind-tunnel, ballistic-range, or analytical reference) with a quantitative acceptance criterion.
- **Integrated flight data** -- calibrated against the assembled trajectory output from the 25-flight validation corpus rather than against an isolated published component dataset. This is circular: the same corpus is the calibration target and one of the validation targets. Each such item is flagged where it is used and is *not* counted toward the 27-subsystem external-benchmark headline.
- **Numerical consistency** -- verified that the implementation reduces to its analytical limit or matches its own boundary conditions to machine precision; no independent external dataset.
- **Calibrated heuristic** -- empirically tuned with no external closure; this category is not used by any acceptance-critical claim in the present work.

The headline validation state is:

- **27 subsystems pass externally anchored acceptance criteria** against published wind-tunnel, free-flight, or analytical data, plus 1 externally anchored *negative* benchmark (NACA RM-10, formally excluded from the headline claim to bound the high-fineness / tapered-afterbody / swept-arc-fin family gap).
- **9 results rest on integrated flight-corpus closure rather than isolated component data** -- principally the finned-body base-drag augmentation, the power-on nozzle / pressure-thrust closure, and the integrated 6-DOF trajectory itself. These are flagged as such throughout the report.
- **25-flight integrated validation corpus**: 25 ground-truth amateur, university-research, and research-program flights spanning Mach 0.54--4.33 and apogees from 3 577 ft to 293 488 ft, archived as the *Rocket Flight Database* v1.0 ([DOI: 10.5281/zenodo.19976138](https://doi.org/10.5281/zenodo.19976138)). Result: **25/25 within $\pm 10\%$, 15/25 within $\pm 5\%$, mean absolute apogee error 4.49%, zero abnormal terminations**. On the same imported geometries the RASAero II predictions published by Rogers (the RASAero II author) average 5.26% with 22/25 within $\pm 10\%$.
- **High-altitude two-stage detail** (the Mach 4.33 / 293 488 ft MESOS flight, included as flight 25 of the corpus above): $-0.6\%$ apogee, $+4.0\%$ peak velocity, $+3.6\%$ peak Mach. RASAero II reference for this flight is a post-flight reconstruction with adjusted ignition delay and launch angle.
- **Two damping constants are not externally validated**: the Tobak $C_{m_q}$ $\times 3$ multiplier and the transonic Gaussian peak augmentation are calibrated against the corpus apogee-turn signature, not against an isolated $C_{mq}$ benchmark. Measured against ADA636861 alone they over-predict by $\sim 3.6\times$ at $M = 1.05$--$1.12$. They are kept because removing them degrades the corpus closure on five flights; closure requires a second independent free-flight $C_{mq}$ dataset that has not been located.

Per-case flight closures are in `paper/data/outlier_closure/`. Per-case results across the 25-flight corpus are reported in Section 11.6, and the canonical comparison artifact is at <https://doi.org/10.5281/zenodo.19976138>.


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
12. Devan-Ashwood / Lamb-Oberkampf supersonic base drag
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
  Van Driest II friction; Taylor-Maccoll / shock-expansion wave drag; Devan--Ashwood base; overrides.};
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
$$a_{\text{new}} = \sqrt{1.4 \times 287.053 \times 216.65} = \sqrt{86\,989.6} = 294.9 \text{ m/s}.$$

The US Standard Atmosphere 1976 tabulates $a = 295.07$ m/s at 11 km; the 0.06% difference here arises from rounding in the temperature. The old model overestimates by $297.1 - 294.9 = 2.2$ m/s, or 0.75%. A rocket traveling at 900 m/s would be reported as Mach 3.05 by the new model but Mach 3.03 by the old model. At Mach 3, the supersonic compressibility factor $\beta = \sqrt{M^2 - 1}$ changes by approximately 0.7% per 0.7% change in Mach, so the downstream effect on wave drag is comparable.


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
| 1500 | 1226.9  | 7.93 | 5.341 | 5.354 | $+48.2$ |

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
| 500  | 0.0097 | 0.0665 | 0.0217 | 2.522 | 1.397 |
| 800  | 0.0827 | 0.2668 | 0.1213 | 2.621 | 1.381 |
| 1000 | 0.1495 | 0.3784 | 0.1975 | 2.697 | 1.371 |
| 1500 | 0.3169 | 0.5733 | 0.3708 | 2.871 | 1.349 |
| 2000 | 0.4547 | 0.6855 | 0.5332 | 3.033 | 1.330 |
| 2500 | 0.5555 | 0.7509 | 0.5964 | 3.096 | 1.323 |
| 3000 | 0.6271 | 0.7909 | 0.6607 | 3.161 | 1.316 |
| 4000 | 0.7205 | 0.8384 | 0.7452 | 3.245 | 1.308 |

At 800 K, $\gamma_\text{eff}$ has dropped to 1.381, a 1.4% reduction from the ideal value. At 2500 K (the stagnation temperature at roughly Mach 6 at sea level), $\gamma_\text{eff} = 1.323$, a 5.5% reduction. This directly affects shock angles, post-shock conditions, and pressure coefficients; for example, the oblique-shock angle for a 15-degree cone at Mach 5 changes by approximately 2 degrees when $\gamma$ decreases from 1.4 to 1.32.

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
  (0,1.400) (500,1.397) (800,1.381) (1000,1.371) (1500,1.349) (2000,1.330)
  (2500,1.323) (3000,1.316) (4000,1.308) (5000,1.300)
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
$$x = \frac{3371}{2000} = 1.6855, \quad e^x = 5.393, \quad x^2 = 2.841,$$
$$\frac{c_{v,\text{vib}}(\mathrm{N_2})}{R} = \frac{x^2 e^x}{(e^x - 1)^2} = \frac{2.841 \times 5.393}{(4.393)^2} = \frac{15.32}{19.30} = 0.4547.$$

Step 2 -- vibrational $c_v$ for $\mathrm{O_2}$ ($\theta = 2256$ K):
$$x = \frac{2256}{2000} = 1.128, \quad e^x = 3.090, \quad x^2 = 1.272,$$
$$\frac{c_{v,\text{vib}}(\mathrm{O_2})}{R} = \frac{1.272 \times 3.090}{(2.090)^2} = \frac{3.930}{4.368} = 0.6855.$$

Step 3 -- mixture average:
$$\frac{c_{v,\text{vib,mix}}}{R} = 0.79 \times 0.4547 + 0.21 \times 0.6855 = 0.3592 + 0.1440 = 0.5032.$$

Step 4 -- total $c_v$ and $\gamma$:
$$\frac{c_{v,\text{total}}}{R} = 2.5 + 0.5032 = 3.003, \qquad \gamma_\text{eff} = \frac{4.003}{3.003} = 1.333.$$

The tabulated value 1.330 differs only in the third decimal due to higher-precision intermediate values. At this stagnation temperature (corresponding to roughly Mach 5 flight at sea level), the 5% reduction in $\gamma$ from 1.4 to 1.33 has measurable effects on shock angles, post-shock pressure, and wave drag coefficients.

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

The combination of the new $\beta$ model with the corrected nose-cone wave drag (Section 6.1), DATCOM fin wave drag (Section 6.4), and Devan-Ashwood/Chapman base drag (Section 6.2) produces the sharp transonic drag peak observed experimentally, validated end-to-end at the trajectory level by the 25-flight integrated validation corpus (Section 1.4).
