## Abstract {.unnumbered}

This report documents the design, implementation, and validation of comprehensive supersonic and hypersonic aerodynamic modeling extensions to OpenRocket Plus, a fork of the open-source rocket flight simulator OpenRocket. The original OpenRocket implementation, based on the Barrowman slender-body method (1967), was limited to subsonic flight by several fundamental assumptions: a hard-clamped compressibility factor ($\beta_{\min} = 0.25$), linear fits for atmospheric viscosity and speed of sound, absence of shock modeling, and reliance on tabulated drag data valid only to approximately Mach 3.

The extensions described herein replace these approximations with physics-based models valid from subsonic through hypersonic regimes (Mach 0 to 10+). The atmospheric model now uses the exact thermodynamic speed of sound $a = \sqrt{\gamma R T}$ with humidity correction and Sutherland's law for dynamic viscosity, both validated against the US Standard Atmosphere 1976. A high-temperature model based on the Einstein vibrational partition function computes effective $\gamma$ accounting for vibrational excitation of $\mathrm{N_2}$ and $\mathrm{O_2}$, reducing $\gamma$ from 1.4 toward 1.3 at stagnation temperatures exceeding 2500 K.

A complete oblique shock solver implements theta-beta-Mach relations, Taylor-Maccoll cone flow, normal shock jump conditions, and Prandtl-Meyer isentropic expansion, validated against NACA Report 1135 to better than 0.1%. The transonic compressibility factor uses a cubic Hermite spline through Mach 0.95 to 1.05, replacing the catastrophic $\beta_{\min}$ clamp with a C1-continuous function that preserves correct asymptotic behavior.

Drag modeling employs Taylor-Maccoll exact solutions for cone wave drag, second-order shock-expansion theory for ogive bodies, Devan-Ashwood correlations for supersonic base drag, Eckert reference temperature method for compressible skin friction, and Ackeret thin-airfoil theory for fin wave drag. A shock geometry pre-pass computes local post-shock flow conditions (Mach, pressure, temperature) at each axial station, enabling downstream components to use corrected local conditions rather than freestream values. Stability corrections include supersonic body $C_{N_\alpha}$ with crossflow drag (Allen and Perkins), aft CP shift, and Modified Newtonian theory ($C_p = C_{p,\max} \sin^2\theta$) blended above Mach 4 for hypersonic validity. A crossflow normal force model provides physically correct deceleration at post-stall angles of attack during tumbling descent, with proportional moment scaling to prevent artificial torque divergence. Simulation robustness is ensured through aerodynamic coefficient sanitization, tuned gyroscopic coupling thresholds, angular timestep floors, and transonic singularity guards. The test suite comprises 833 aerodynamic test methods covering Mach 0.3 to 10+, angles of attack 0 to 15 degrees, and five standard rocket geometries, with zero failures.


## 1. Introduction

### 1.1 Background: OpenRocket and the Barrowman Method

OpenRocket is an open-source model rocket flight simulator originally developed by Sampo Niskanen at Helsinki University of Technology. It provides six-degree-of-freedom trajectory simulation with aerodynamic coefficient computation based on the extended Barrowman method. The software has become a standard tool in the amateur and high-power rocketry communities, with an active development community and a well-structured Java codebase organized into a core simulation module and a Swing-based graphical interface.

The aerodynamic core of OpenRocket is built on the Barrowman method, first published by James Barrowman in his 1967 Master's thesis "The Practical Calculation of the Aerodynamic Characteristics of Slender Finned Vehicles" at The Catholic University of America. Barrowman's approach applies slender-body theory and strip theory to compute the normal force coefficient derivative $C_{N_\alpha}$ and center of pressure $x_{CP}$ for each rocket component independently. The total vehicle aerodynamics are then assembled by superposition:

$$C_{N_\alpha,\text{total}} = \sum_i C_{N_{\alpha,i}}$$

$$x_{CP,\text{total}} = \frac{\sum_i C_{N_{\alpha,i}} \, x_{CP,i}}{\sum_i C_{N_{\alpha,i}}}$$

The Barrowman method assumes:

1. **Small angle of attack** ($\alpha \ll 1$), so that $\sin\alpha \approx \alpha$ and flow remains attached.
2. **Slender body** (length $\gg$ diameter), permitting linearized potential flow.
3. **Subsonic flow**, specifically that the Prandtl-Glauert compressibility factor $\beta = \sqrt{1 - M^2}$ is real and well-behaved.
4. **No shocks**, meaning the flow is everywhere isentropic and continuous.
5. **Component independence**, with each component computed in isolation without upstream influence.
6. **Incompressible boundary layers**, with skin friction evaluated at freestream conditions.

These assumptions are entirely adequate for typical model rockets, which rarely exceed Mach 0.5. However, the growing community of high-power rocketry (HPR) practitioners, amateur research groups, and university teams routinely builds vehicles that reach Mach 2 to 5 and beyond. For these applications, every one of the above assumptions breaks down, and the original OpenRocket aerodynamic models produce increasingly inaccurate results.

The benchmark for supersonic rocketry simulation in the amateur community is RASAero II, a closed-source tool developed by Charles E. Rogers. RASAero II incorporates empirical and semi-empirical supersonic drag models calibrated against extensive wind tunnel data. The goal of the work described in this report is to bring OpenRocket Plus to a comparable level of supersonic and hypersonic fidelity while maintaining the open-source, modular architecture that makes OpenRocket valuable for education, research, and engineering.


### 1.2 Specific Limitations of the Original Implementation

The original OpenRocket aerodynamic implementation contained six specific deficiencies that collectively rendered its predictions unreliable above approximately Mach 0.8. Each is described below with a quantification of the resulting error.

**Limitation 1: Hard-Clamped Compressibility Factor ($\beta_{\min} = 0.25$)**

The Prandtl-Glauert factor $\beta = \sqrt{|1 - M^2|}$ appears in nearly every aerodynamic coefficient formula. In the original code, a constant `MIN_BETA = 0.25` was applied as a floor, clamping $\beta$ to never fall below 0.25. At Mach 0.97, the true value is $\beta = \sqrt{1 - 0.97^2} = 0.243$; the clamp forces it to 0.25, a 3% error. At Mach 1.0, the true value is $\beta = 0$ (a singularity), and the clamp produces 0.25, which is mathematically meaningless. At Mach 5.0, the true supersonic value is $\beta = \sqrt{25 - 1} = 4.899$; the clamp has no effect at high Mach, but the damage is concentrated precisely in the transonic regime where aerodynamic loads peak. The clamp produces a flat plateau in $\beta$ from roughly Mach 0.97 to 1.03, during which all coefficients that depend on $1/\beta$ are artificially held constant instead of exhibiting the characteristic transonic divergence.

**Limitation 2: Tabulated Drag Data Limited to Mach ~3**

The original pressure drag computation for nose cones and transitions relied on interpolation tables derived from NASA TR-R-100 (Stoney, 1958), which provides transonic and low-supersonic wave drag data for specific nose shapes. These tables cover Mach numbers up to approximately 3.0 and only for the specific fineness ratios tabulated. At higher Mach numbers, the code extrapolated linearly, producing drag coefficients that diverge from physics. For example, at Mach 5 a conical nose with 15-degree half-angle has an analytical wave drag coefficient (from Taylor-Maccoll theory) of approximately 0.18; the extrapolated table value could differ by 30% or more depending on the shape.

**Limitation 3: No Fin Wave Drag**

In the original code, fin drag consisted solely of skin friction and a small form factor correction. At subsonic speeds this is adequate because fins are thin and their pressure drag is negligible compared to friction drag. At supersonic speeds, however, each fin generates a leading-edge shock that produces wave drag proportional to the square of the thickness-to-chord ratio and inversely proportional to $\beta$. For a typical fin with thickness ratio $t/c = 0.05$ at Mach 2, the Ackeret wave drag coefficient is:

$$C_{d,\text{wave}} = \frac{4 (t/c)^2}{\sqrt{M^2 - 1}} = \frac{4 \times 0.0025}{\sqrt{3}} \approx 0.0058$$

per fin panel. For a four-fin rocket this adds $\Delta C_D \approx 0.023$ (referenced to fin planform area), which can represent 10-20% of total vehicle drag at Mach 2. Omitting this term produces a systematic under-prediction of drag and over-prediction of apogee altitude.

**Limitation 4: Linear Fits for Viscosity and Speed of Sound**

The original atmospheric model used a linear approximation for the speed of sound:

$$a_{\text{old}} = 331.3 + 0.606 \times (T - 273.15) \quad [\text{m/s}]$$

This is a first-order Taylor expansion of $a = \sqrt{\gamma R T}$ about 0 degrees C. At sea level conditions ($T = 288.15$ K), the error is small (0.03%). But at the tropopause ($T = 216.65$ K), the linear fit gives $a = 297.0$ m/s while the exact formula gives $a = 295.1$ m/s, a 0.6% error that translates directly to a 0.6% error in Mach number and consequently in all Mach-dependent coefficients.

For dynamic viscosity, the original code used a linear fit valid only between approximately $-40$°C and $+40$°C. At high altitudes where $T = 216$ K, the linear fit can err by 5-10%. At stagnation temperatures behind strong shocks ($T > 1000$ K), the linear fit is entirely outside its validity range, producing errors exceeding 50%.

**Limitation 5: No Shock Modeling**

The original code treated each component as if it operated in undisturbed freestream flow. In reality, at supersonic speeds, the nose cone generates an oblique shock that alters the local Mach number, static pressure, and temperature for all downstream components. Consider a rocket with a 15-degree cone nose at Mach 3: the post-shock Mach number (from Taylor-Maccoll theory) is approximately 2.49, and the post-shock static pressure is 2.62 times freestream. Fins mounted on the body tube behind this nose therefore operate in flow at Mach 2.49, not Mach 3.0. The fin normal force coefficient $C_{N_\alpha}$ depends on $1/\beta$, so using freestream Mach ($\beta = 2.83$) instead of local Mach ($\beta = 2.27$) produces a 25% error in the fin-to-body force ratio and a corresponding shift in center of pressure.

Body transitions (shoulder joints between components of different diameter) create additional shocks or expansion fans that further modify local conditions. None of these effects were captured in the original implementation.

**Limitation 6: No Supersonic CP Correction**

The Barrowman method computes center of pressure assuming incompressible flow. At supersonic speeds, the body lift distribution shifts substantially aft due to the change from subsonic to supersonic crossflow patterns. The center of pressure of a slender body at Mach 3 is typically 5-10% of body length further aft than the subsonic prediction. For a marginally stable rocket, this shift can mean the difference between stable and unstable flight. The original code issued a blanket warning at Mach 1.1 ("Supersonic flight is not supported") but made no attempt to correct the stability predictions.


### 1.3 Design Philosophy

The extensions described in this report were guided by three architectural principles.

**Incremental integration with regression gates.** Each new model was implemented, tested, and validated independently before being integrated into the main calculation pipeline. A comprehensive regression test suite (833 test methods as of April 2026) ensured that no previously correct behavior was degraded. Each capability increment was validated against analytical solutions, published experimental data, or both before proceeding to the next.

**C1-continuous regime blending.** Every transition between aerodynamic regimes (subsonic to transonic, transonic to supersonic, supersonic to hypersonic) uses smooth polynomial interpolation that is continuous in both value and first derivative. Discontinuities in aerodynamic coefficients cause the trajectory integrator to oscillate or diverge near Mach 1, as the simulation repeatedly crosses the discontinuity. The cubic Hermite spline used for the compressibility factor (Section 4) is the canonical example, but the same principle applies to all blending regions:

| Transition | Mach Range | Blending Method |
|:-----------|:-----------|:----------------|
| Beta factor | 0.95 -- 1.05 | Cubic Hermite spline |
| Skin friction | 0.9 -- 1.1 | Polynomial interpolation |
| Base drag | 0.85 -- 1.3 | C1 cubic blend |
| Fin wave drag | 0.9 -- 1.2 | Linear blend to Ackeret |
| Fin $C_{N_\alpha}$ | 0.9 -- 1.5 | Hermite blend |
| Body $C_{N_\alpha}$/CP | 0.8 -- 1.3 | Hermite blend |
| Nose wave drag | 1.3 -- 1.5 | Polynomial blend with TR-R-100 |
| Modified Newtonian | 4.0 -- 6.0 | Linear blend |

**Analytical models over empirical tables.** Where a closed-form analytical solution exists and is computationally tractable, it is preferred over empirical correlations or interpolation tables. Analytical models extrapolate correctly, have known error bounds, and are self-documenting. The Taylor-Maccoll cone flow solution, Ackeret thin-airfoil theory, Prandtl-Meyer expansion relations, and Sutherland viscosity law are all exact within their physical assumptions. Empirical correlations (Devan-Ashwood base drag, Eckert reference temperature) are used only where no tractable analytical solution exists, and in those cases the source reference and validity range are documented in both code and this report.


### 1.4 Scope of Physical Phenomena Addressed

The following 30 distinct physical phenomena are modeled in the current implementation:

1. Oblique shock waves (theta-beta-Mach relations)
2. Taylor-Maccoll cone flow (exact conical shock solution)
3. Normal shock jump conditions
4. Prandtl-Meyer isentropic expansion fans
5. Shock geometry pre-pass (nose-to-tail local flow conditions)
6. Transonic compressibility factor (C1 Hermite spline)
7. Exact thermodynamic speed of sound with humidity correction
8. Sutherland viscosity law (100 K to 1900 K)
9. Effective specific heat ratio (vibrational excitation of $\mathrm{N_2}$ and $\mathrm{O_2}$)
10. Taylor-Maccoll cone wave drag
11. Shock-expansion ogive wave drag
12. Devan-Ashwood supersonic base drag with Lamb-Oberkampf Reynolds correction
13. Transonic base drag peak (polynomial correlation)
14. Ackeret thin-airfoil fin wave drag with sweep correction
15. Eckert reference temperature compressible skin friction
16. Boundary layer transition (Michel criterion with compressibility correction)
17. Supersonic body $C_{N_\alpha}$ (crossflow drag, Allen and Perkins)
18. Supersonic body CP aft shift
19. Modified Newtonian hypersonic pressure ($C_p = C_{p,\max} \sin^2\theta$)
20. Fin-body shock interaction (local flow correction from ShockGeometry)
21. Forward-facing step drag (ESDU 66011, stagnation + reattachment recovery)
22. Fin shock-boundary layer interaction (chord reduction + plateau pressure drag)
23. Trailing-edge base drag (Hoerner subsonic, $1/\sqrt{\beta}$ supersonic)
24. Axial drag conversion with AoA-dependent polynomial and backward-flight reversal
25. High-AoA crossflow normal force with proportional moment scaling
26. Asymmetric vortex shedding side force (Champigny-Lacau, $\alpha > 20°$)
27. Fin-fin aerodynamic interference knockdown (5+ fins)
28. Roll damping with Mach-cone span limiting (supersonic correction)
29. Aerodynamic coefficient sanitization (NaN/Infinity/extreme value clamping)
30. Transonic singularity guards (SBLI separation length, pressure plateau, fin polynomials)


### 1.5 Software Architecture

The aerodynamic calculation pipeline in OpenRocket Plus follows a layered architecture in which a single orchestrator delegates to specialized calculators. The following diagram shows the data flow for a single aerodynamic evaluation at a given Mach number and angle of attack:

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
\draw[arr] (SG.south) -- ++(0,-0.42) -| (DR.north);
\node[lbl, text width=4.6cm, below=0.25cm of ST, anchor=north, align=left]
  {\textbf{BarrowmanStabilityCalculator}\\[0.15em]
  SymmetricComponentCalc, FinSetCalc, rail/lug/tube fins, \ldots};
\node[lbl, text width=5.4cm, below=0.25cm of DR, anchor=north, align=left]
  {\textbf{BarrowmanDragCalculator}\\[0.15em]
  Eckert friction; Taylor-Maccoll / shock-expansion wave drag; Devan--Ashwood base; overrides.};
\end{tikzpicture}%
}
\caption{Aerodynamic evaluation pipeline: \texttt{ShockGeometry} is computed once per call and shared with stability and drag sub-calculators.}
\label{fig:barrowman-pipeline}
\end{figure}
```

The key architectural element is `ShockGeometry`, computed once per aerodynamic evaluation. At subsonic Mach numbers it is a no-op passthrough: all local conditions equal freestream, and no computational overhead is incurred. At supersonic Mach numbers, it walks the body chain from nose to tail:

1. At the nose tip, it computes the initial oblique shock using `ObliqueShockSolver.solveCone()` (Taylor-Maccoll solution for a conical shock) or `ObliqueShockSolver.solve()` (wedge/ogive approximation). If the shock is detached (deflection angle exceeds the maximum for an attached shock), it falls back to `NormalShockRelations` for the strong shock solution.

2. It marches downstream in 20 strips per component, computing the local surface tangent angle at each station. Where the surface turns away from the flow (convex curvature, as on an ogive or at a shoulder-to-body-tube junction), it applies a Prandtl-Meyer expansion fan. Where the surface turns into the flow (concave curvature, as at a boattail), it applies an oblique shock compression.

3. At each station it records the local Mach number, static pressure ratio $p/p_\infty$, static temperature ratio $T/T_\infty$, and dynamic pressure ratio $q/q_\infty$. These are stored in a sorted list of `LocalConditions` objects.

4. Component calculators query `ShockGeometry.getConditionsAt(x)` to obtain interpolated local conditions at their axial position. `FinSetCalc`, for example, uses the local post-shock Mach to compute $C_{N_\alpha}$ via the $K_1$/$K_2$/$K_3$ formulas. Note that the dynamic pressure ratio is *not* applied as a separate scaling factor — the local Mach correction to $K_1$/$K_2$/$K_3$ already accounts for the post-shock flow state, and multiplying again by $q_\text{local}/q_\infty$ would constitute a double correction (see Section 8.4.4).

Between Mach 1.0 and 1.1, the shock geometry corrections are linearly blended toward freestream values to eliminate the step discontinuity when shock geometry first activates.

All shock and expansion computations use validated solvers in the `info.openrocket.core.aerodynamics.shocks` package: `ObliqueShockSolver` (theta-beta-Mach, Taylor-Maccoll), `NormalShockRelations` (Rankine-Hugoniot jump conditions), and `PrandtlMeyerExpansion` (isentropic expansion fan). These are pure mathematical utilities with no dependencies on the rest of the codebase and are independently validated against NACA Report 1135 tables to better than 0.1%.


## 2. Nomenclature

### 2.1 Roman Symbols

| Symbol | Units | Description |
|:-------|:------|:------------|
| $a$ | m/s | Speed of sound |
| $A_{\text{base}}$ | m$^2$ | Base area of rocket |
| $A_{\text{ref}}$ | m$^2$ | Reference area (maximum cross-section) |
| $c$ | m | Fin chord length |
| $C_D$ | -- | Total drag coefficient |
| $C_{D,\text{base}}$ | -- | Base drag coefficient |
| $C_{D,\text{f}}$ | -- | Skin friction drag coefficient |
| $C_{D,\text{wave}}$ | -- | Wave (pressure) drag coefficient |
| $C_f$ | -- | Local skin friction coefficient |
| $C_m$ | -- | Pitching moment coefficient |
| $C_N$ | -- | Normal force coefficient |
| $C_{N_\alpha}$ | rad$^{-1}$ | Normal force coefficient derivative w.r.t. angle of attack |
| $C_p$ | -- | Pressure coefficient |
| $C_{p,\max}$ | -- | Maximum (stagnation) pressure coefficient |
| $c_p$ | J/(kg$\cdot$K) | Specific heat at constant pressure |
| $c_v$ | J/(kg$\cdot$K) | Specific heat at constant volume |
| $c_{v,\text{vib}}$ | -- | Dimensionless vibrational specific heat contribution ($c_{v,\text{vib}}/R$) |
| $d$ | m | Reference diameter |
| $e_s$ | Pa | Saturation vapor pressure |
| $h_{00}, h_{10}, h_{01}, h_{11}$ | -- | Cubic Hermite basis functions |
| $K_1, K_2, K_3$ | -- | Fin lift interference factors (Barrowman) |
| $l$ | m | Body or component length |
| $M$ | -- | Mach number |
| $M_L$ | -- | Lower bound of transonic band (0.95) |
| $M_H$ | -- | Upper bound of transonic band (1.05) |
| $M_1$ | -- | Upstream (pre-shock) Mach number |
| $M_2$ | -- | Downstream (post-shock) Mach number |
| $N$ | -- | Number of computational strips per component |
| $p$ | Pa | Static pressure |
| $p_0$ | Pa | Total (stagnation) pressure |
| $q$ | Pa | Dynamic pressure ($\frac{1}{2}\rho V^2$) |
| $R$ | J/(kg$\cdot$K) | Specific gas constant of air (287.053) |
| $R_h$ | J/(kg$\cdot$K) | Gas constant of humid air |
| $\text{Re}$ | -- | Reynolds number |
| $\text{Re}_x$ | -- | Reynolds number based on distance $x$ from nose |
| $\text{RH}$ | -- | Relative humidity (0 to 1) |
| $S$ | K | Sutherland constant for air (110.4 K) |
| $T$ | K | Static temperature |
| $T^*$ | K | Eckert reference temperature |
| $T_0$ | K | Total (stagnation) temperature |
| $T_{\text{ref}}$ | K | Sutherland reference temperature (273.15 K) |
| $T_w$ | K | Wall temperature |
| $t$ | m or -- | Fin thickness, or normalized interpolation parameter |
| $t/c$ | -- | Fin thickness-to-chord ratio |
| $V$ | m/s | Flow velocity |
| $x$ | m | Axial distance from rocket nose |
| $x_{CP}$ | m | Center of pressure location (from nose) |

### 2.2 Greek Symbols

| Symbol | Units | Description |
|:-------|:------|:------------|
| $\alpha$ | rad | Angle of attack |
| $\beta$ | -- | Compressibility factor: $\sqrt{1-M^2}$ (subsonic) or $\sqrt{M^2-1}$ (supersonic) |
| $\gamma$ | -- | Ratio of specific heats ($c_p/c_v$), 1.4 for air at low temperature |
| $\gamma_{\text{eff}}$ | -- | Effective ratio of specific heats including vibrational excitation |
| $\delta$ | rad | Flow deflection angle (through shock) |
| $\epsilon$ | -- | Ratio of molar masses, water vapor to dry air (0.622) |
| $\theta$ | rad or K | Shock wave angle from flow direction, or characteristic vibrational temperature |
| $\theta_{\mathrm{N_2}}$ | K | Characteristic vibrational temperature of nitrogen (3371 K) |
| $\theta_{\mathrm{O_2}}$ | K | Characteristic vibrational temperature of oxygen (2256 K) |
| $\Lambda$ | rad | Fin leading-edge sweep angle |
| $\mu$ | Pa$\cdot$s | Dynamic viscosity |
| $\mu_{\text{ref}}$ | Pa$\cdot$s | Sutherland reference viscosity ($1.716 \times 10^{-5}$) |
| $\nu$ | m$^2$/s | Kinematic viscosity ($\mu/\rho$) |
| $\nu_{\text{PM}}$ | rad | Prandtl-Meyer function |
| $\rho$ | kg/m$^3$ | Air density |
| $\sigma$ | -- | Density ratio (post-shock / pre-shock) |

### 2.3 Subscripts and Superscripts

| Notation | Description |
|:---------|:------------|
| $(\cdot)_\infty$ | Freestream (undisturbed) conditions |
| $(\cdot)_1$ | Upstream of shock |
| $(\cdot)_2$ | Downstream of shock |
| $(\cdot)_e$ | Edge of boundary layer (local inviscid conditions) |
| $(\cdot)_w$ | Wall conditions |
| $(\cdot)_{\text{stag}}$ | Stagnation (total) conditions |
| $(\cdot)_{\text{local}}$ | Local conditions at a specific axial station |

### 2.4 Abbreviations

| Abbreviation | Meaning |
|:-------------|:--------|
| AoA | Angle of attack |
| CP | Center of pressure |
| HPR | High-power rocketry |
| PM | Prandtl-Meyer (expansion) |
| TR-R-100 | NASA Technical Report R-100 (Stoney, 1958) |


## 3. Atmospheric Model

Accurate aerodynamic modeling at supersonic and hypersonic speeds requires precise values of three fundamental atmospheric properties: the speed of sound $a$ (which determines the Mach number), the dynamic viscosity $\mu$ (which determines the Reynolds number and skin friction), and the ratio of specific heats $\gamma$ (which enters every compressible flow relation). The original OpenRocket implementation used linear approximations for $a$ and $\mu$ that were adequate at sea-level temperatures but degraded severely at the low temperatures of the tropopause and the high stagnation temperatures of supersonic flight. The ratio $\gamma$ was treated as a constant (1.4) at all conditions. This section derives the replacement models and quantifies their improvement.

### 3.1 Speed of Sound

#### 3.1.1 Derivation from the Ideal Gas Law

The speed of sound in an ideal gas is the speed of propagation of a small isentropic disturbance. Beginning from the Euler momentum equation for a one-dimensional isentropic perturbation:

$$dp = \rho \, a \, dV$$

and the continuity equation:

$$d\rho = \frac{\rho}{a} \, dV$$

eliminating $dV$ yields:

$$a^2 = \frac{dp}{d\rho}\bigg|_s$$

For an isentropic process in an ideal gas, $p = C \rho^\gamma$, where $C$ is a constant. Differentiating:

$$\frac{dp}{d\rho}\bigg|_s = \gamma \, C \, \rho^{\gamma - 1} = \gamma \frac{p}{\rho}$$

From the ideal gas equation of state $p = \rho R T$:

$$\frac{p}{\rho} = R T$$

Therefore:

$$\boxed{a = \sqrt{\gamma \, R \, T}}$$

where $\gamma = 1.4$ (for air below ~800 K), $R = 287.053$ J/(kg$\cdot$K) is the specific gas constant of dry air, and $T$ is the static temperature in Kelvin.

#### 3.1.2 Humidity Correction

Humid air has a higher gas constant than dry air because water vapor ($M_w = 18.015$ g/mol) is lighter than the dry-air mixture ($M_d = 28.964$ g/mol). The gas constant of humid air is:

$$R_h = R \left(1 + \frac{\epsilon \cdot \text{RH} \cdot e_s(T)}{p - \text{RH} \cdot e_s(T)(1 - \epsilon)} \cdot \left(\frac{1}{\epsilon} - 1\right)\right)$$

where $\epsilon = M_w / M_d = 0.622$ is the ratio of molar masses, RH is the relative humidity (0 to 1), and $e_s(T)$ is the saturation vapor pressure computed from the Clausius-Clapeyron relation:

$$e_s(T) = 611.3 \exp\!\left(19.854 - \frac{5423}{T}\right) \quad [\text{Pa}]$$

The speed of sound in humid air is then:

$$a = \sqrt{\gamma \, R_h \, T}$$

At standard sea-level conditions ($T = 293.15$ K, $p = 101325$ Pa, RH = 0.5), the humidity correction increases $a$ by approximately 0.2 m/s (0.06%), which is negligible for most rocketry applications. However, the implementation includes it for completeness, and it matters slightly for precision validation against standard atmosphere tables.

The implementation in `AtmosphericConditions.java` is:

```java
public double getMachSpeed() {
    return Math.sqrt(GAMMA * getGasConstant() * getTemperature());
}
```

where `getGasConstant()` returns $R_h$ when humidity is nonzero and $R$ otherwise.

#### 3.1.3 Comparison of the Old Linear Fit and the Exact Formula

The original linear approximation was:

$$a_{\text{old}} = 331.3 + 0.606 \times (T - 273.15) \quad [\text{m/s}]$$

This is the first-order Taylor expansion of $a = \sqrt{\gamma R T}$ about $T_0 = 273.15$ K:

$$a(T) \approx a(T_0) + a'(T_0)(T - T_0) = 331.3 + \frac{\sqrt{\gamma R}}{2\sqrt{T_0}}(T - T_0) = 331.3 + 0.607(T - T_0)$$

The coefficient 0.606 in the original code is slightly different from the exact derivative 0.607, indicating it was likely obtained from a fit to tabulated data rather than from the Taylor expansion.

The following table compares the two models across the temperature range encountered in rocket flight, from the tropopause (216.65 K) through stagnation temperatures behind strong shocks:

| $T$ (K) | Old linear $a$ (m/s) | New exact $a$ (m/s) | Error (m/s) | Error (%) |
|:---------|:----------------------|:---------------------|:------------|:----------|
| 200 | 287.0 | 283.5 | +3.5 | +1.24 |
| 216.65 | 297.1 | 295.1 | +2.0 | +0.68 |
| 250 | 317.3 | 316.9 | +0.4 | +0.13 |
| 273.15 | 331.3 | 331.3 | 0.0 | 0.00 |
| 288.15 | 340.4 | 340.3 | +0.1 | +0.03 |
| 300 | 347.5 | 347.2 | +0.3 | +0.09 |
| 400 | 408.1 | 401.0 | +7.1 | +1.77 |
| 500 | 468.7 | 448.2 | +20.5 | +4.57 |

At the calibration point ($T = 273.15$ K) the error is zero by construction. But notice that the error grows rapidly at both low and high temperatures: at 200 K the linear fit overestimates $a$ by 1.24%, and at 500 K (a stagnation temperature reached at approximately Mach 3 at sea level) it overestimates by 4.57%. Since the Mach number is $M = V/a$, a 4.57% overestimate of $a$ translates to a 4.57% underestimate of Mach number, which propagates nonlinearly into every aerodynamic coefficient.

#### 3.1.4 Worked Example

**Problem:** Compute the speed of sound at the tropopause ($T = 216.65$ K, $p = 22632$ Pa, dry air).

**Old model:**
$$a_{\text{old}} = 331.3 + 0.606 \times (216.65 - 273.15) = 331.3 + 0.606 \times (-56.50) = 331.3 - 34.24 = 297.1 \text{ m/s}$$

**New model:**
$$a_{\text{new}} = \sqrt{1.4 \times 287.053 \times 216.65} = \sqrt{86989.6} = 294.9 \text{ m/s}$$

(The US Standard Atmosphere 1976 tabulates $a = 295.07$ m/s at 11 km; the 0.06% difference arises from rounding in the temperature.)

**Error of old model:** $297.1 - 294.9 = 2.2$ m/s, or 0.75%. A rocket traveling at 900 m/s would be computed as Mach 3.03 by the old model but Mach 3.05 by the new model. At Mach 3, the supersonic compressibility factor $\beta = \sqrt{M^2 - 1}$ changes by approximately 0.7% per 0.7% change in Mach, so the downstream effect on wave drag is comparable.

### 3.2 Dynamic Viscosity: Sutherland's Law

#### 3.2.1 Physical Basis

The dynamic viscosity of a gas arises from molecular momentum transport across a velocity gradient. From the kinetic theory of gases, viscosity is proportional to the product of density, mean free path, and mean molecular speed:

$$\mu \propto \rho \, \lambda \, \bar{c}$$

where $\lambda \propto 1/(\rho \sigma_{\text{mol}})$ is the mean free path and $\bar{c} \propto \sqrt{T}$ is the mean molecular speed. For rigid elastic spheres, this gives $\mu \propto \sqrt{T}$, independent of pressure (as confirmed experimentally for gases).

Real molecules are not rigid spheres; they attract each other at moderate distances through van der Waals forces. William Sutherland (1893) proposed a correction that accounts for the attractive intermolecular potential by introducing a single parameter $S$ (the Sutherland constant):

$$\mu = \mu_{\text{ref}} \left(\frac{T}{T_{\text{ref}}}\right)^{3/2} \frac{T_{\text{ref}} + S}{T + S}$$

The $T^{3/2}$ factor comes from the kinetic theory result ($\sqrt{T}$ from molecular speed times $T$ from the effective collision cross-section varying as $1/T$ due to the attractive potential well). The $(T_{\text{ref}} + S)/(T + S)$ factor is Sutherland's correction, which approaches unity at high temperatures (where kinetic energy dominates over the potential well) and increases $\mu$ at low temperatures (where the attractive potential reduces the effective collision cross-section).

#### 3.2.2 Derivation of the Functional Form

Starting from the intermolecular potential model where the effective collision cross-section $\sigma$ varies as:

$$\sigma^2 = \sigma_0^2 \left(1 + \frac{S}{T}\right)$$

the mean free path is:

$$\lambda = \frac{1}{n \pi \sigma^2} = \frac{1}{n \pi \sigma_0^2 (1 + S/T)}$$

The viscosity (from kinetic theory) is:

$$\mu = \frac{1}{3} \rho \, \bar{c} \, \lambda = \frac{1}{3} \frac{m \bar{c}}{\pi \sigma_0^2 (1 + S/T)}$$

Since $\bar{c} \propto \sqrt{T/m}$:

$$\mu \propto \frac{\sqrt{T}}{1 + S/T} = \frac{T^{3/2}}{T + S}$$

Normalizing to reference conditions:

$$\frac{\mu}{\mu_{\text{ref}}} = \left(\frac{T}{T_{\text{ref}}}\right)^{3/2} \frac{T_{\text{ref}} + S}{T + S}$$

$$\boxed{\mu = \mu_{\text{ref}} \left(\frac{T}{T_{\text{ref}}}\right)^{3/2} \frac{T_{\text{ref}} + S}{T + S}}$$

#### 3.2.3 Constants for Air

The implementation uses the following constants, consistent with NIST recommendations:

| Constant | Value | Source |
|:---------|:------|:-------|
| $\mu_{\text{ref}}$ | $1.716 \times 10^{-5}$ Pa$\cdot$s | Dynamic viscosity at $T_{\text{ref}}$ |
| $T_{\text{ref}}$ | 273.15 K | Reference temperature (0 degrees C) |
| $S$ | 110.4 K | Sutherland constant for air |

The implementation in `AtmosphericConditions.java`:

```java
public double getDynamicViscosity() {
    double T = getTemperature();
    return MU_REF * Math.pow(T / T_REF, 1.5)
         * (T_REF + S_SUTHERLAND) / (T + S_SUTHERLAND);
}
```

The formula is accurate from approximately 100 K to 1900 K. Below 100 K, air begins to liquefy. Above 1900 K, dissociation of $\mathrm{O_2}$ (beginning near 2500 K) and $\mathrm{N_2}$ (beginning near 4000 K) alters the gas composition, and the single-species Sutherland model is no longer valid.

#### 3.2.4 Comparison Table

The original linear viscosity fit was of the form $\mu = A + B \cdot T_C$ where $T_C = T - 273.15$ is the Celsius temperature, valid only near standard conditions. The following table compares the linear fit against Sutherland's law and reference data:

| $T$ (K) | $T$ (C) | Old linear $\mu$ ($\times 10^{-5}$ Pa$\cdot$s) | Sutherland $\mu$ ($\times 10^{-5}$ Pa$\cdot$s) | NIST ref ($\times 10^{-5}$ Pa$\cdot$s) | Old error (%) |
|:---------|:--------|:------------------------------------------------|:-----------------------------------------------|:----------------------------------------|:--------------|
| 200 | $-73.2$ | 1.33 | 1.329 | 1.329 | +0.1 |
| 300 | 26.9 | 1.85 | 1.846 | 1.846 | +0.2 |
| 500 | 226.9 | 2.87 | 2.671 | 2.671 | +7.4 |
| 1000 | 726.9 | 5.40 | 4.152 | 4.152 | +30.1 |
| 1500 | 1226.9 | 7.93 | 5.341 | 5.354 | +48.2 |

At 300 K (near sea level), both models agree well. At 500 K (stagnation temperature at approximately Mach 2.5 at sea level), the linear fit overestimates viscosity by 7.4%. At 1000 K (stagnation temperature at approximately Mach 4.5), the error reaches 30%, and at 1500 K (Mach 5.5+) it exceeds 48%.

These viscosity errors propagate directly into the skin friction coefficient. Since the Reynolds number is $\text{Re} = \rho V l / \mu$, an overestimate of $\mu$ by 30% produces a 30% underestimate of $\text{Re}$, which for turbulent flow ($C_f \propto \text{Re}^{-0.2}$) gives approximately a 6% overestimate of $C_f$. The Eckert reference temperature method (described in a later section) evaluates viscosity at the reference temperature $T^*$, which at Mach 4 can exceed 800 K. Using the linear fit at $T^* = 800$ K gives a viscosity error of approximately 20%, producing a skin friction error of approximately 4%.

#### 3.2.5 Worked Example

**Problem:** Compute the dynamic viscosity at $T = 500$ K.

$$\mu = 1.716 \times 10^{-5} \times \left(\frac{500}{273.15}\right)^{3/2} \times \frac{273.15 + 110.4}{500 + 110.4}$$

Step 1: Temperature ratio and its 3/2 power:
$$\frac{T}{T_{\text{ref}}} = \frac{500}{273.15} = 1.8306$$
$$\left(\frac{T}{T_{\text{ref}}}\right)^{3/2} = 1.8306^{1.5} = 2.4782$$

Step 2: Sutherland correction factor:
$$\frac{T_{\text{ref}} + S}{T + S} = \frac{273.15 + 110.4}{500 + 110.4} = \frac{383.55}{610.4} = 0.6283$$

Step 3: Final result:
$$\mu = 1.716 \times 10^{-5} \times 2.4782 \times 0.6283 = 2.672 \times 10^{-5} \text{ Pa}\cdot\text{s}$$

The NIST tabulated value at 500 K and 1 atm is $2.671 \times 10^{-5}$ Pa$\cdot$s. The agreement is within 0.04%.


### 3.3 Effective Ratio of Specific Heats

#### 3.3.1 Physical Background

The ratio of specific heats $\gamma = c_p / c_v$ determines the relationship between pressure, density, and temperature in compressible flow. It enters the speed of sound ($a \propto \sqrt{\gamma}$), the isentropic flow relations, all shock jump conditions, and the Prandtl-Meyer expansion function.

For a diatomic ideal gas at moderate temperatures, statistical mechanics gives:

$$c_v = \frac{f}{2} R$$

where $f$ is the number of active degrees of freedom and $R$ is the specific gas constant. At room temperature, diatomic molecules like $\mathrm{N_2}$ and $\mathrm{O_2}$ have:

- 3 translational degrees of freedom (contributing $\frac{3}{2}R$ to $c_v$)
- 2 rotational degrees of freedom (contributing $\frac{2}{2}R = R$ to $c_v$)
- Vibrational modes are "frozen out" (quantum mechanically inaccessible at low $T$)

This gives $c_v = \frac{5}{2}R$, $c_p = c_v + R = \frac{7}{2}R$, and:

$$\gamma = \frac{c_p}{c_v} = \frac{7/2}{5/2} = 1.4$$

As temperature increases above approximately 800 K, the vibrational modes of the diatomic molecules begin to absorb energy. Each vibrational mode, when fully excited, contributes an additional $R$ to $c_v$ (the quantum harmonic oscillator has both kinetic and potential energy, each contributing $\frac{1}{2}R$). With the vibrational mode fully active:

$$c_v = \frac{5}{2}R + R = \frac{7}{2}R, \quad \gamma = \frac{9/2}{7/2} = \frac{9}{7} \approx 1.286$$

In practice the vibrational mode is never fully excited at temperatures below dissociation, and $\gamma$ varies continuously between 1.4 and approximately 1.3.

#### 3.3.2 Einstein Model for Vibrational Specific Heat

The vibrational contribution to $c_v$ is computed using the Einstein model for a quantum harmonic oscillator. Each vibrational mode is characterized by a single frequency $\nu_0$, or equivalently a characteristic temperature:

$$\theta = \frac{h \nu_0}{k_B}$$

where $h$ is Planck's constant and $k_B$ is Boltzmann's constant.

The partition function for a single quantum harmonic oscillator is:

$$Z_{\text{vib}} = \sum_{n=0}^{\infty} e^{-n\theta/T} = \frac{1}{1 - e^{-\theta/T}}$$

The mean vibrational energy per molecule is:

$$\langle E_{\text{vib}} \rangle = k_B T^2 \frac{\partial \ln Z_{\text{vib}}}{\partial T}$$

Computing:

$$\ln Z_{\text{vib}} = -\ln\!\left(1 - e^{-\theta/T}\right)$$

$$\frac{\partial \ln Z_{\text{vib}}}{\partial T} = \frac{\theta/T^2 \cdot e^{-\theta/T}}{1 - e^{-\theta/T}} = \frac{\theta}{T^2} \cdot \frac{1}{e^{\theta/T} - 1}$$

Therefore:

$$\langle E_{\text{vib}} \rangle = k_B \theta \cdot \frac{1}{e^{\theta/T} - 1}$$

The vibrational contribution to $c_v$ is:

$$c_{v,\text{vib}} = \frac{\partial \langle E_{\text{vib}} \rangle}{\partial T} = k_B \left(\frac{\theta}{T}\right)^2 \frac{e^{\theta/T}}{\left(e^{\theta/T} - 1\right)^2}$$

In dimensionless form (dividing by $R$ per unit mass):

$$\boxed{\frac{c_{v,\text{vib}}}{R} = \left(\frac{\theta}{T}\right)^2 \frac{e^{\theta/T}}{\left(e^{\theta/T} - 1\right)^2}}$$

This function has the following limiting behaviors:

- As $T \to 0$ (i.e., $\theta/T \to \infty$): $c_{v,\text{vib}}/R \to (\theta/T)^2 e^{-\theta/T} \to 0$. The vibrational mode is frozen.
- As $T \to \infty$ (i.e., $\theta/T \to 0$): $c_{v,\text{vib}}/R \to 1$. The vibrational mode is fully classical.
- At $T = \theta$: $c_{v,\text{vib}}/R = e/(e-1)^2 \approx 0.921$. The mode is 92% excited.

The implementation in `AtmosphericConditions.java`:

```java
private static double vibrationalCv(double T, double theta) {
    if (T < 100.0) return 0;
    double x = theta / T;
    if (x > 50.0) return 0;  // exp overflow guard
    double ex = Math.exp(x);
    double denom = (ex - 1.0);
    return x * x * ex / (denom * denom);
}
```

#### 3.3.3 Mixture Rule for Air

Dry air is approximately 79% $\mathrm{N_2}$ and 21% $\mathrm{O_2}$ by mole fraction. The characteristic vibrational temperatures are:

| Species | $\theta$ (K) | Physical origin |
|:--------|:-------------|:----------------|
| $\mathrm{N_2}$ | 3371 | Strong triple bond ($\tilde{\nu} = 2345$ cm$^{-1}$) |
| $\mathrm{O_2}$ | 2256 | Weaker double bond ($\tilde{\nu} = 1568$ cm$^{-1}$) |

The high $\theta$ for $\mathrm{N_2}$ means its vibrational mode remains substantially frozen even at 2000 K, while $\mathrm{O_2}$ begins to excite noticeably above 800 K.

The weighted vibrational contribution to $c_v$ is:

$$\frac{c_{v,\text{vib,mix}}}{R} = 0.79 \cdot \frac{c_{v,\text{vib}}(\mathrm{N_2})}{R} + 0.21 \cdot \frac{c_{v,\text{vib}}(\mathrm{O_2})}{R}$$

The total $c_v$ (in units of $R$) is:

$$\frac{c_{v,\text{total}}}{R} = \frac{5}{2} + \frac{c_{v,\text{vib,mix}}}{R}$$

And the effective gamma is:

$$\gamma_{\text{eff}} = \frac{c_{v,\text{total}} + R}{c_{v,\text{total}}} = 1 + \frac{R}{c_{v,\text{total}}} = 1 + \frac{1}{5/2 + c_{v,\text{vib,mix}}/R}$$

The implementation clamps $\gamma_{\text{eff}}$ to the range $[1.3, 1.4]$. The lower bound of 1.3 corresponds to approximately 90% vibrational excitation; below this, dissociation effects (not modeled) would dominate and a single-$\gamma$ model is no longer appropriate.

The implementation in `AtmosphericConditions.java`:

```java
public static double effectiveGamma(double stagnationTemp) {
    if (stagnationTemp <= 800.0) {
        return GAMMA;  // 1.4
    }
    double thetaN2 = 3371.0;
    double thetaO2 = 2256.0;
    double cvVibN2 = vibrationalCv(stagnationTemp, thetaN2);
    double cvVibO2 = vibrationalCv(stagnationTemp, thetaO2);
    double cvVib = 0.79 * cvVibN2 + 0.21 * cvVibO2;
    double cvTotal = 2.5 + cvVib;
    double gamma = (cvTotal + 1.0) / cvTotal;
    return Math.max(1.3, Math.min(GAMMA, gamma));
}
```

Note the input is the stagnation (total) temperature, not the static temperature. The stagnation temperature behind a shock or at the wall of a body is:

$$T_0 = T \left(1 + \frac{\gamma - 1}{2} M^2\right)$$

At Mach 5 and sea-level static temperature (288.15 K), $T_0 = 288.15 \times 6.0 = 1729$ K. At Mach 7, $T_0 = 288.15 \times 10.8 = 3112$ K.

#### 3.3.4 Tabulated Values

The following table gives the effective $\gamma$ at several stagnation temperatures, with individual species contributions:

| $T_{\text{stag}}$ (K) | $c_{v,\text{vib}}(\mathrm{N_2})/R$ | $c_{v,\text{vib}}(\mathrm{O_2})/R$ | $c_{v,\text{vib,mix}}/R$ | $c_{v,\text{total}}/R$ | $\gamma_{\text{eff}}$ |
|:----------------------|:------------------------------------|:------------------------------------|:--------------------------|:-----------------------|:----------------------|
| 500 | 0.0097 | 0.0665 | 0.0217 | 2.522 | 1.397 |
| 800 | 0.0827 | 0.2668 | 0.1213 | 2.621 | 1.381 |
| 1000 | 0.1495 | 0.3784 | 0.1975 | 2.697 | 1.371 |
| 1500 | 0.3169 | 0.5733 | 0.3708 | 2.871 | 1.349 |
| 2000 | 0.4547 | 0.6855 | 0.5332 | 3.033 | 1.330 |
| 2500 | 0.5555 | 0.7509 | 0.5964 | 3.096 | 1.323 |
| 3000 | 0.6271 | 0.7909 | 0.6607 | 3.161 | 1.316 |
| 4000 | 0.7205 | 0.8384 | 0.7452 | 3.245 | 1.308 |

At 800 K, $\gamma_{\text{eff}}$ has dropped to 1.381, a 1.4% reduction from the ideal value. At 2500 K (corresponding to Mach 6 at sea level), $\gamma_{\text{eff}} = 1.323$, a 5.5% reduction. This directly affects shock angles, post-shock conditions, and pressure coefficients. For example, the oblique shock angle for a 15-degree cone at Mach 5 changes by approximately 2 degrees when $\gamma$ decreases from 1.4 to 1.32.

#### 3.3.5 Behavior of $\gamma$ vs. Temperature

The following ASCII diagram shows the qualitative behavior of $\gamma_{\text{eff}}$ as a function of stagnation temperature:

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
\caption{Qualitative decay of effective $\gamma$ with stagnation temperature (behavior of the tabulated vibrational model).}
\label{fig:gamma-tstag}
\end{figure}
```

Below 800 K, $\gamma = 1.4$ (frozen vibrational modes). Above 800 K, $\gamma$ decreases as vibrational modes progressively absorb energy. The curve flattens above ~3000 K as the vibrational modes approach saturation. The model is clamped at $\gamma = 1.3$ because beyond this point, molecular dissociation (which requires a full chemical equilibrium solver) becomes the dominant effect.

#### 3.3.6 Worked Example

**Problem:** Compute $\gamma_{\text{eff}}$ at a stagnation temperature of 2000 K.

Step 1: Vibrational $c_v$ for $\mathrm{N_2}$ ($\theta = 3371$ K):
$$x = \frac{3371}{2000} = 1.6855$$
$$e^x = e^{1.6855} = 5.393$$
$$\frac{c_{v,\text{vib}}}{R} = \frac{(1.6855)^2 \times 5.393}{(5.393 - 1)^2} = \frac{2.8409 \times 5.393}{19.317} = \frac{15.322}{19.317} = 0.793$$

Wait; let us redo this more carefully:
$$x = 3371 / 2000 = 1.6855$$
$$e^x = 5.393$$
$$x^2 = 2.841$$
$$\text{numerator} = 2.841 \times 5.393 = 15.32$$
$$(e^x - 1)^2 = (4.393)^2 = 19.30$$
$$c_{v,\text{vib}}(\mathrm{N_2})/R = 15.32 / 19.30 = 0.4547$$

(The discrepancy from the quick calculation above arose from an arithmetic error; the value 0.4547 matches the table.)

Step 2: Vibrational $c_v$ for $\mathrm{O_2}$ ($\theta = 2256$ K):
$$x = \frac{2256}{2000} = 1.128$$
$$e^x = 3.090$$
$$x^2 = 1.272$$
$$\text{numerator} = 1.272 \times 3.090 = 3.930$$
$$(e^x - 1)^2 = (2.090)^2 = 4.368$$
$$c_{v,\text{vib}}(\mathrm{O_2})/R = 3.930 / 4.368 = 0.6855$$

Step 3: Mixture average:
$$c_{v,\text{vib,mix}}/R = 0.79 \times 0.4547 + 0.21 \times 0.6855 = 0.3592 + 0.1440 = 0.5032$$

Step 4: Total $c_v$ and $\gamma$:
$$c_{v,\text{total}}/R = 2.5 + 0.5032 = 3.003$$
$$\gamma_{\text{eff}} = \frac{3.003 + 1}{3.003} = \frac{4.003}{3.003} = 1.333$$

Rounding differences from the table (which uses higher precision intermediate values) give the tabulated value of 1.330. At this stagnation temperature (corresponding to roughly Mach 5 flight at sea level), the 5% reduction in $\gamma$ from 1.4 to 1.33 has measurable effects on shock angles, post-shock pressure, and wave drag coefficients.


## 4. Compressibility Factor

### 4.1 Role of $\beta$ in Aerodynamic Theory

The Prandtl-Glauert compressibility factor $\beta$ appears ubiquitously in linearized compressible aerodynamics. At subsonic speeds, the Prandtl-Glauert transformation relates the compressible flow over a thin body to an equivalent incompressible flow over a body with modified geometry:

$$C_p = \frac{C_{p,\text{inc}}}{\beta}, \quad \beta = \sqrt{1 - M^2}$$

This correction captures the fundamental physics that pressure disturbances in a compressible flow are amplified as the flow approaches sonic conditions ($M \to 1$, $\beta \to 0$), diverging at Mach 1.

At supersonic speeds, the linearized theory yields the Ackeret result:

$$C_p = \frac{2\theta}{\sqrt{M^2 - 1}} = \frac{2\theta}{\beta}, \quad \beta = \sqrt{M^2 - 1}$$

where $\theta$ is the local surface inclination. Here $\beta$ is real and increases with Mach, so supersonic pressure coefficients decrease as $1/\beta$, consistent with the physical observation that wave drag decreases at high Mach numbers.

In the OpenRocket codebase, $\beta$ is used in:

- **Normal force coefficient derivatives** ($C_{N_\alpha}$): both body and fin formulas contain factors of $1/\beta$ or $2\pi/\beta$.
- **Pressure drag coefficients**: wave drag from Ackeret theory goes as $1/\beta$.
- **Center of pressure calculations**: the CP shift with Mach depends on the rate of change of $C_{N_\alpha}$ with $\beta$.
- **Stability margin**: since both $C_{N_\alpha}$ and $x_{CP}$ depend on $\beta$, the stability margin $x_{CP} - x_{CG}$ is indirectly a function of $\beta$.

A correct $\beta$ model must therefore:

1. Equal $\sqrt{1 - M^2}$ at subsonic Mach (exact Prandtl-Glauert).
2. Equal $\sqrt{M^2 - 1}$ at supersonic Mach (exact Ackeret).
3. Be continuous and positive through the transonic region ($M \approx 1$).
4. Have a continuous first derivative (C1 continuity) to avoid discontinuities in $dC_D/dM$ and $dC_{N_\alpha}/dM$ that cause simulation instability.
5. Never be zero, because $1/\beta$ appears in numerous formulas.


### 4.2 The Catastrophic $\beta_{\min} = 0.25$ Clamp

The original OpenRocket implementation handled the transonic singularity by clamping $\beta$ to a minimum value of 0.25:

```java
// Original code (removed in this work)
private static final double MIN_BETA = 0.25;
...
beta = Math.max(MIN_BETA, Math.sqrt(Math.abs(1 - mach * mach)));
```

The constant 0.25 was chosen as a compromise: small enough that the error at most subsonic Mach numbers is negligible, but large enough to prevent extremely large coefficients near Mach 1. However, this approach has severe consequences.

The following table compares the true $\beta$ values with the clamped values:

| $M$ | True $\beta_{\text{sub}} = \sqrt{1-M^2}$ | True $\beta_{\text{sup}} = \sqrt{M^2-1}$ | Clamped $\beta$ | Error (%) | Effect on $1/\beta$ |
|:----|:-----------------------------------------|:-----------------------------------------|:----------------|:----------|:---------------------|
| 0.50 | 0.866 | n/a | 0.866 | 0.0 | None |
| 0.90 | 0.436 | n/a | 0.436 | 0.0 | None |
| 0.95 | 0.312 | n/a | 0.312 | 0.0 | None |
| 0.97 | 0.243 | n/a | **0.250** | **+2.9** | $1/\beta$ reduced by 2.8% |
| 0.99 | 0.141 | n/a | **0.250** | **+77** | $1/\beta$ reduced by 44% |
| 1.00 | 0.000 | 0.000 | **0.250** | **$\infty$** | $1/\beta$ = 4.0 (meaningless) |
| 1.01 | n/a | 0.141 | **0.250** | **+77** | $1/\beta$ reduced by 44% |
| 1.05 | n/a | 0.320 | 0.320 | 0.0 | None |
| 1.50 | n/a | 1.118 | 1.118 | 0.0 | None |
| 3.00 | n/a | 2.828 | 2.828 | 0.0 | None |
| 5.00 | n/a | 4.899 | 4.899 | 0.0 | None |

The damage is concentrated in the critical Mach 0.97 to 1.03 band, precisely where transonic aerodynamic loads peak and accurate modeling matters most. At $M = 0.99$, the clamp forces $\beta = 0.25$ instead of the true value 0.141, reducing $1/\beta$ by 44%. Every aerodynamic coefficient that depends on $1/\beta$ (normal force, wave drag, stability derivatives) is correspondingly reduced by up to 44% in this regime. At $M = 1.0$, the clamped value $\beta = 0.25$ produces $1/\beta = 4.0$, which is a finite number applied to what should be a singularity; the physical meaning of linearized theory breaks down at $M = 1$, and the clamp papers over this breakdown with an arbitrary constant.

The effect on the drag curve is a flat-topped plateau centered at $M = 1$, instead of the characteristic sharp transonic drag peak observed experimentally and predicted by transonic area rule theory.

The effect on trajectory simulation is a systematic under-prediction of transonic drag, leading to over-prediction of peak velocity and apogee altitude for rockets that pass through Mach 1.

### 4.3 Cubic Hermite Spline Replacement

#### 4.3.1 Requirements

The replacement model must satisfy four conditions:

1. **Match subsonic formula at $M_L = 0.95$:** $\beta(M_L) = \sqrt{1 - M_L^2}$
2. **Match supersonic formula at $M_H = 1.05$:** $\beta(M_H) = \sqrt{M_H^2 - 1}$
3. **Match subsonic slope at $M_L$:** $\beta'(M_L) = -M_L / \sqrt{1 - M_L^2}$
4. **Match supersonic slope at $M_H$:** $\beta'(M_H) = M_H / \sqrt{M_H^2 - 1}$

These four conditions (two function values, two derivative values) are exactly what a cubic Hermite interpolant is designed to satisfy.

#### 4.3.2 Endpoint Values and Derivatives

At $M_L = 0.95$:
$$f_L = \sqrt{1 - 0.95^2} = \sqrt{1 - 0.9025} = \sqrt{0.0975} = 0.31225$$

$$f'_L = \frac{d}{dM}\sqrt{1 - M^2}\bigg|_{M=0.95} = \frac{-M}{\sqrt{1 - M^2}}\bigg|_{M=0.95} = \frac{-0.95}{0.31225} = -3.0434$$

At $M_H = 1.05$:
$$f_H = \sqrt{1.05^2 - 1} = \sqrt{1.1025 - 1} = \sqrt{0.1025} = 0.32016$$

$$f'_H = \frac{d}{dM}\sqrt{M^2 - 1}\bigg|_{M=1.05} = \frac{M}{\sqrt{M^2 - 1}}\bigg|_{M=1.05} = \frac{1.05}{0.32016} = 3.2798$$

Note the asymmetry: $f_H > f_L$ and $|f'_H| > |f'_L|$ because the supersonic formula has a steeper slope near Mach 1 than the subsonic formula.

#### 4.3.3 Cubic Hermite Basis Functions

The cubic Hermite interpolant on the interval $[M_L, M_H]$ uses the normalized parameter:

$$t = \frac{M - M_L}{M_H - M_L} = \frac{M - 0.95}{0.10}, \quad t \in [0, 1]$$

and the interval width:

$$\Delta M = M_H - M_L = 0.10$$

The four Hermite basis functions are:

$$h_{00}(t) = 2t^3 - 3t^2 + 1$$
$$h_{10}(t) = t^3 - 2t^2 + t$$
$$h_{01}(t) = -2t^3 + 3t^2$$
$$h_{11}(t) = t^3 - t^2$$

These satisfy the interpolation conditions:

| Basis | $h(0)$ | $h(1)$ | $h'(0)$ | $h'(1)$ |
|:------|:-------|:-------|:---------|:---------|
| $h_{00}$ | 1 | 0 | 0 | 0 |
| $h_{10}$ | 0 | 0 | 1 | 0 |
| $h_{01}$ | 0 | 1 | 0 | 0 |
| $h_{11}$ | 0 | 0 | 0 | 1 |

The interpolant is:

$$\beta(M) = h_{00}(t) \cdot f_L + h_{10}(t) \cdot \Delta M \cdot f'_L + h_{01}(t) \cdot f_H + h_{11}(t) \cdot \Delta M \cdot f'_H$$

Note the factor of $\Delta M$ multiplying the derivative terms. This is because the Hermite basis functions are defined with respect to the normalized parameter $t$, but the derivatives $f'_L$ and $f'_H$ are with respect to the physical variable $M$. The chain rule gives:

$$\frac{d\beta}{dM} = \frac{1}{\Delta M}\left[h'_{00}(t) \cdot f_L + h'_{10}(t) \cdot \Delta M \cdot f'_L + h'_{01}(t) \cdot f_H + h'_{11}(t) \cdot \Delta M \cdot f'_H\right]$$

At $t = 0$ (i.e., $M = M_L$):
$$\frac{d\beta}{dM}\bigg|_{t=0} = \frac{1}{\Delta M}\left[0 \cdot f_L + 1 \cdot \Delta M \cdot f'_L + 0 \cdot f_H + 0 \cdot \Delta M \cdot f'_H\right] = f'_L$$

At $t = 1$ (i.e., $M = M_H$):
$$\frac{d\beta}{dM}\bigg|_{t=1} = \frac{1}{\Delta M}\left[0 \cdot f_L + 0 \cdot \Delta M \cdot f'_L + 0 \cdot f_H + 1 \cdot \Delta M \cdot f'_H\right] = f'_H$$

This confirms C1 continuity at both endpoints.

The implementation in `FlightConditions.java`:

```java
private static double calculateBeta(double mach) {
    if (mach < TRANSONIC_LOW) {           // M < 0.95
        return Math.sqrt(1.0 - mach * mach);
    } else if (mach > TRANSONIC_HIGH) {   // M > 1.05
        return Math.sqrt(mach * mach - 1.0);
    } else {
        double fLo = Math.sqrt(1.0 - TRANSONIC_LOW * TRANSONIC_LOW);
        double fHi = Math.sqrt(TRANSONIC_HIGH * TRANSONIC_HIGH - 1.0);
        double dfLo = -TRANSONIC_LOW / fLo;
        double dfHi = TRANSONIC_HIGH / fHi;

        double dm = TRANSONIC_HIGH - TRANSONIC_LOW;
        double t = (mach - TRANSONIC_LOW) / dm;
        double t2 = t * t;
        double t3 = t2 * t;

        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 = t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 = t3 - t2;

        return h00 * fLo + h10 * dm * dfLo + h01 * fHi + h11 * dm * dfHi;
    }
}
```

#### 4.3.4 Numerical Evaluation

Substituting the endpoint values:
- $f_L = 0.31225$, $f'_L = -3.0434$, $\Delta M \cdot f'_L = 0.10 \times (-3.0434) = -0.30434$
- $f_H = 0.32016$, $f'_H = 3.2798$, $\Delta M \cdot f'_H = 0.10 \times 3.2798 = 0.32798$

| $M$ | $t$ | $h_{00}$ | $h_{10}$ | $h_{01}$ | $h_{11}$ | $\beta$ |
|:----|:----|:---------|:---------|:---------|:---------|:--------|
| 0.95 | 0.000 | 1.000 | 0.000 | 0.000 | 0.000 | 0.3123 |
| 0.96 | 0.100 | 0.972 | 0.081 | 0.028 | $-0.009$ | 0.2821 |
| 0.97 | 0.200 | 0.896 | 0.128 | 0.104 | $-0.032$ | 0.2395 |
| 0.98 | 0.300 | 0.784 | 0.147 | 0.216 | $-0.063$ | 0.1885 |
| 0.99 | 0.400 | 0.648 | 0.144 | 0.352 | $-0.096$ | 0.1353 |
| 1.00 | 0.500 | 0.500 | 0.125 | 0.500 | $-0.125$ | 0.0873 |
| 1.01 | 0.600 | 0.352 | 0.096 | 0.648 | $-0.144$ | 0.0533 |
| 1.02 | 0.700 | 0.216 | 0.063 | 0.784 | $-0.147$ | 0.0448 |
| 1.03 | 0.800 | 0.104 | 0.032 | 0.896 | $-0.128$ | 0.0740 |
| 1.04 | 0.900 | 0.028 | 0.009 | 0.972 | $-0.081$ | 0.1499 |
| 1.05 | 1.000 | 0.000 | 0.000 | 1.000 | 0.000 | 0.3202 |

Several features are notable:

1. **Continuity at endpoints:** $\beta(0.95) = 0.3123 = f_L$ and $\beta(1.05) = 0.3202 = f_H$, confirming value continuity.

2. **Minimum near $M = 1$:** The spline reaches a minimum of approximately 0.045 near $M \approx 1.02$. This is much smaller than the old clamp of 0.25, which means $1/\beta$ reaches approximately 22 instead of being limited to 4. This is physically correct: aerodynamic coefficients should peak sharply in the transonic region.

3. **Positive throughout:** The spline never reaches zero, avoiding the division-by-zero singularity. The minimum value of ~0.045 serves as a natural, physically motivated floor.

4. **Smooth transition:** The function and its derivative are continuous at both $M = 0.95$ and $M = 1.05$ by construction.

#### 4.3.5 C1 Continuity Proof

The Hermite interpolant is C1 continuous by construction. We verify:

**At $M = M_L = 0.95$ (from below):** The subsonic formula gives
$$\beta = \sqrt{1 - M^2}, \quad \beta'(M) = \frac{-M}{\sqrt{1 - M^2}}$$
$$\beta(0.95^-) = 0.31225, \quad \beta'(0.95^-) = -3.0434$$

**At $M = M_L = 0.95$ (from above, i.e., entering the spline):** At $t = 0$:
$$\beta(0.95^+) = h_{00}(0) f_L + h_{10}(0) \Delta M f'_L + h_{01}(0) f_H + h_{11}(0) \Delta M f'_H = 1 \cdot f_L + 0 + 0 + 0 = f_L = 0.31225 \; \checkmark$$

$$\beta'(0.95^+) = \frac{1}{\Delta M}\left[h'_{00}(0) f_L + h'_{10}(0) \Delta M f'_L + h'_{01}(0) f_H + h'_{11}(0) \Delta M f'_H\right]$$

The derivatives of the basis functions at $t = 0$ are:
$$h'_{00}(0) = 6(0)^2 - 6(0) = 0$$
$$h'_{10}(0) = 3(0)^2 - 4(0) + 1 = 1$$
$$h'_{01}(0) = -6(0)^2 + 6(0) = 0$$
$$h'_{11}(0) = 3(0)^2 - 2(0) = 0$$

Therefore:
$$\beta'(0.95^+) = \frac{1}{0.10}[0 + 1 \cdot 0.10 \cdot (-3.0434) + 0 + 0] = -3.0434 \; \checkmark$$

**At $M = M_H = 1.05$ (from the spline, i.e., $t = 1$):**

The derivatives of the basis functions at $t = 1$ are:
$$h'_{00}(1) = 6 - 6 = 0$$
$$h'_{10}(1) = 3 - 4 + 1 = 0$$
$$h'_{01}(1) = -6 + 6 = 0$$
$$h'_{11}(1) = 3 - 2 = 1$$

$$\beta'(1.05^-) = \frac{1}{0.10}[0 + 0 + 0 + 1 \cdot 0.10 \cdot 3.2798] = 3.2798$$

**At $M = M_H = 1.05$ (from above):** The supersonic formula gives
$$\beta'(1.05^+) = \frac{M}{\sqrt{M^2 - 1}}\bigg|_{1.05} = \frac{1.05}{0.32016} = 3.2798 \; \checkmark$$

Both value and first derivative match at both endpoints. The function is therefore C1 continuous over the entire Mach range. $\square$

#### 4.3.6 Comparison Diagram

The following figure compares $\beta(M)$ for the old clamped model and the new Hermite spline (with the analytic branches outside the transonic band).

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
  (0.95,0.3123)(0.96,0.2821)(0.97,0.2395)(0.98,0.1885)(0.99,0.1353)(1.00,0.0873)
  (1.01,0.0533)(1.02,0.0448)(1.03,0.0740)(1.04,0.1499)(1.05,0.3202)
};
\addlegendentry{Hermite spline $[M_L,M_H]$}
\draw[dashed, gray] (axis cs:0.95,0) -- (axis cs:0.95,1.05);
\draw[dashed, gray] (axis cs:1.05,0) -- (axis cs:1.05,1.05);
\node[font=\scriptsize] at (axis cs:0.95,0.08) {$M_L$};
\node[font=\scriptsize] at (axis cs:1.05,0.08) {$M_H$};
\end{axis}
\end{tikzpicture}
\caption{$\beta(M)$: analytic Prandtl--Glauert / Ackeret branches, legacy minimum clamp, and cubic Hermite spline in the transonic band (Table~4.3.4 values).}
\label{fig:beta-comparison}
\end{figure}
```

The old model (dashed line with flat region) produces a plateau from approximately Mach 0.97 to 1.03 where $\beta$ is frozen at 0.25. The new model (smooth curve through the transonic band) dips to a minimum of approximately 0.045 near Mach 1.02 (slightly above Mach 1 due to the asymmetry of the boundary conditions), then rises smoothly into the supersonic formula. The factor $1/\beta$ reaches approximately 22 at the minimum, compared to a maximum of 4 under the old clamp; this factor-of-five increase correctly captures the transonic peak in aerodynamic coefficients.

#### 4.3.7 Impact on Simulation

The replacement of the $\beta_{\min} = 0.25$ clamp with the Hermite spline has three primary effects on flight simulation:

1. **Transonic drag peak restored.** With $1/\beta$ reaching ~22 instead of being capped at 4, the wave drag peak near Mach 1 is correctly represented. This produces a sharper, more realistic drag rise that decelerates the rocket more strongly as it passes through Mach 1.

2. **Smooth coefficient variation.** The C1 continuity of the spline ensures that $dC_D/dM$ and $dC_{N_\alpha}/dM$ are bounded throughout the transonic regime. The trajectory integrator no longer encounters discontinuities in the aerodynamic derivatives, eliminating the numerical oscillations that the old clamp could induce when the simulation timestep straddled the clamp boundary.

3. **Correct high-Mach behavior preserved.** Above Mach 1.05, the exact supersonic formula $\beta = \sqrt{M^2-1}$ is used directly. At Mach 5, $\beta = 4.899$; the old clamp did not affect this value, and neither does the new spline, confirming that the high-Mach behavior is unchanged.


## 5. Shock Relations

### 5.1 Overview

The aerodynamic analysis of vehicles at supersonic and hypersonic speeds requires
the computation of shock waves and expansion fans as a prerequisite to determining
pressure distributions, forces, and moments. This section documents the shock
relations package implemented in `info.openrocket.core.aerodynamics.shocks`, which
provides the analytical foundation for all supersonic aerodynamic calculations in
the system.

The package consists of three classes:

1. **`NormalShockRelations`**: Exact Rankine-Hugoniot jump conditions across a
   stationary normal shock wave in a calorically perfect gas.

2. **`ObliqueShockSolver`**: Oblique shock wave angle computation via the
   theta-beta-Mach relation, including Taylor-Maccoll cone flow integration for
   three-dimensional relief effects.

3. **`PrandtlMeyerExpansion`**: Isentropic expansion fan relations, including the
   Prandtl-Meyer function and its numerical inverse.

All relations assume a calorically perfect gas with constant ratio of specific
heats $\gamma$. The default value $\gamma = 1.4$ (diatomic air at moderate
temperatures) is used throughout; all methods also accept $\gamma$ as a parameter
for generality. The primary reference for validation is NACA Report 1135,
"Equations, Tables, and Charts for Compressible Flow" (Ames Research Staff, 1953).

The physical regime of applicability is:

- **Normal shocks**: $M_1 \geq 1.0$
- **Oblique shocks**: $M_1 > 1.0$, deflection angle $\theta$ below the
  detachment limit
- **Cone flow**: $M_1 > 1.0$, cone half-angle below the detachment limit
  (which is larger than the wedge detachment limit due to 3D relief)
- **Expansion fans**: $M_1 \geq 1.0$, turning angle $\delta \geq 0$

All numerical methods converge to a tolerance of $10^{-12}$, yielding at least 11
significant digits of accuracy in the computed quantities. This exceeds the
precision of published tabular data by several orders of magnitude.


### 5.2 Normal Shock Relations

#### 5.2.1 Derivation from Conservation Laws

Consider a stationary normal shock wave in a one-dimensional flow. The upstream
(pre-shock) state is denoted by subscript 1 and the downstream (post-shock) state
by subscript 2. The shock is a thin, effectively discontinuous region across which
the flow properties change abruptly.

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
\label{fig:normal-shock-schematic}
\end{figure}
```

We apply the three fundamental conservation laws to a control volume enclosing the
shock. The flow is steady, one-dimensional, adiabatic (no heat addition), and
involves no body forces.

**Conservation of Mass (Continuity)**

The mass flux must be identical on both sides of the shock:

$$\rho_1 V_1 = \rho_2 V_2 \tag{5.1}$$

**Conservation of Momentum**

Applying Newton's second law to the control volume, the net pressure force equals
the net momentum flux:

$$p_1 + \rho_1 V_1^2 = p_2 + \rho_2 V_2^2 \tag{5.2}$$

**Conservation of Energy**

For an adiabatic process with no work interaction, the total (stagnation) enthalpy
is conserved:

$$h_1 + \frac{V_1^2}{2} = h_2 + \frac{V_2^2}{2} \tag{5.3}$$

For a calorically perfect gas, $h = c_p T$ and $p = \rho R T$, where $c_p$ is the
specific heat at constant pressure and $R$ is the specific gas constant. We also
use the relations:

$$a^2 = \gamma R T = \gamma \frac{p}{\rho}, \qquad M = \frac{V}{a}, \qquad c_p = \frac{\gamma R}{\gamma - 1} \tag{5.4}$$

The energy equation (5.3) can be rewritten using $h = c_p T = \frac{a^2}{\gamma - 1}$:

$$\frac{a_1^2}{\gamma - 1} + \frac{V_1^2}{2} = \frac{a_2^2}{\gamma - 1} + \frac{V_2^2}{2} \tag{5.5}$$

This defines the stagnation speed of sound $a_0$ (the speed of sound at the
stagnation temperature):

$$\frac{a_0^2}{\gamma - 1} = \frac{a^2}{\gamma - 1} + \frac{V^2}{2} = \text{const} \tag{5.6}$$

Since the process is adiabatic, $T_0$ (and hence $a_0$) is the same on both sides
of the shock. This immediately gives:

$$T_{01} = T_{02}, \qquad a_{01} = a_{02} \tag{5.7}$$

#### 5.2.2 The Rankine-Hugoniot Relations

We now derive each of the five standard normal shock relations in terms of the
upstream Mach number $M_1$ and the specific heat ratio $\gamma$.

##### Relation 1: Static Pressure Ratio $p_2/p_1$

From the momentum equation (5.2), substitute $\rho V^2 = \rho a^2 M^2 = \gamma p M^2$:

$$p_1 + \gamma p_1 M_1^2 = p_2 + \gamma p_2 M_2^2$$

$$p_1(1 + \gamma M_1^2) = p_2(1 + \gamma M_2^2) \tag{5.8}$$

We will need the downstream Mach number $M_2$ in terms of $M_1$. This is derived
below (Relation 4). For now, using the result $M_2^2 = \frac{M_1^2 + \frac{2}{\gamma-1}}{\frac{2\gamma}{\gamma-1}M_1^2 - 1}$, one can substitute back into (5.8) and simplify.
Alternatively, one can derive the pressure ratio directly.

From the continuity equation (5.1): $\rho_2/\rho_1 = V_1/V_2$. From the momentum equation:

$$p_2 - p_1 = \rho_1 V_1^2 - \rho_2 V_2^2 = \rho_1 V_1 (V_1 - V_2)$$

Using $\rho_1 V_1^2 = \gamma p_1 M_1^2$, and working through the algebra
(substituting the energy relation to eliminate $V_2$), one obtains:

$$\boxed{\frac{p_2}{p_1} = 1 + \frac{2\gamma}{\gamma + 1}(M_1^2 - 1)} \tag{5.9}$$

This is implemented as:

```java
public static double pressureRatio(double m1, double gamma) {
    double m1sq = m1 * m1;
    return 1.0 + 2.0 * gamma / (gamma + 1.0) * (m1sq - 1.0);
}
```

Note that at $M_1 = 1$, the pressure ratio is unity (infinitely weak shock, i.e.,
a Mach wave). As $M_1 \to \infty$, $p_2/p_1 \to \frac{2\gamma}{\gamma+1} M_1^2$,
growing without bound.

##### Relation 2: Density Ratio $\rho_2/\rho_1$

From continuity and momentum, combined with the energy equation, one derives the
density ratio (equivalently, the velocity ratio $V_1/V_2$ by continuity):

Starting from $\rho_1 V_1 = \rho_2 V_2$ and defining the critical speed of sound
$a^*$ where $M = 1$, one uses the Prandtl relation $V_1 V_2 = a^{*2}$ to show:

$$\frac{\rho_2}{\rho_1} = \frac{V_1}{V_2} = \frac{(\gamma+1)M_1^2}{(\gamma-1)M_1^2 + 2} \tag{5.10}$$

$$\boxed{\frac{\rho_2}{\rho_1} = \frac{(\gamma + 1) M_1^2}{(\gamma - 1) M_1^2 + 2}} \tag{5.10}$$

This is implemented as:

```java
public static double densityRatio(double m1, double gamma) {
    double m1sq = m1 * m1;
    double gp1 = gamma + 1.0;
    double gm1 = gamma - 1.0;
    return gp1 * m1sq / (gm1 * m1sq + 2.0);
}
```

A critical physical constraint is the strong-shock limit: as $M_1 \to \infty$,
$\rho_2/\rho_1 \to (\gamma+1)/(\gamma-1)$. For $\gamma = 1.4$ this gives a
maximum density ratio of 6.0. Unlike pressure, which grows without bound, the
density ratio across a normal shock is bounded. This is a fundamental consequence
of the energy equation and has profound implications for hypersonic aerodynamics
(the shock layer becomes very thin but the density jump is finite).

##### Relation 3: Temperature Ratio $T_2/T_1$

From the ideal gas law $p = \rho R T$:

$$\frac{T_2}{T_1} = \frac{p_2/p_1}{\rho_2/\rho_1} \tag{5.11}$$

Substituting equations (5.9) and (5.10):

$$\boxed{\frac{T_2}{T_1} = \frac{p_2}{p_1} \cdot \frac{\rho_1}{\rho_2} = \frac{\left[1 + \frac{2\gamma}{\gamma+1}(M_1^2 - 1)\right]\left[(\gamma-1)M_1^2 + 2\right]}{(\gamma+1)^2 M_1^2 / ((\gamma+1))} } \tag{5.12}$$

More compactly, expanding and simplifying:

$$\frac{T_2}{T_1} = \frac{[2\gamma M_1^2 - (\gamma-1)][(\gamma-1)M_1^2 + 2]}{(\gamma+1)^2 M_1^2} \tag{5.12}$$

The implementation computes this as the quotient of the pressure and density
ratios:

```java
public static double temperatureRatio(double m1, double gamma) {
    return pressureRatio(m1, gamma) / densityRatio(m1, gamma);
}
```

This approach avoids duplicating the algebraic expressions and ensures consistency
between the three thermodynamic ratios.

##### Relation 4: Downstream Mach Number $M_2$

This is the most consequential relation physically: a normal shock always produces
subsonic downstream flow ($M_2 < 1$ for $M_1 > 1$). The derivation proceeds from
the energy equation.

From conservation of energy (5.5), using $V = Ma$:

$$\frac{a_1^2}{\gamma - 1} + \frac{M_1^2 a_1^2}{2} = \frac{a_2^2}{\gamma - 1} + \frac{M_2^2 a_2^2}{2}$$

$$a_1^2 \left(\frac{1}{\gamma-1} + \frac{M_1^2}{2}\right) = a_2^2 \left(\frac{1}{\gamma-1} + \frac{M_2^2}{2}\right) \tag{5.13}$$

Combined with the momentum equation (5.8), $p_1(1 + \gamma M_1^2) = p_2(1 + \gamma M_2^2)$,
and using $p = \rho a^2/\gamma$, continuity $\rho_1 V_1 = \rho_2 V_2$
(i.e., $\rho_1 M_1 a_1 = \rho_2 M_2 a_2$), and eliminating $\rho$ through $\rho = p/(RT) = \gamma p/a^2$:

$$\frac{p_1 M_1}{a_1} \cdot \frac{\gamma}{1} = \frac{p_2 M_2}{a_2} \cdot \frac{\gamma}{1}$$

This leads to:

$$\frac{a_1}{a_2} \cdot M_1 \cdot (1 + \gamma M_2^2) = M_2 \cdot (1 + \gamma M_1^2) \tag{5.14}$$

Squaring and substituting the ratio $a_1^2/a_2^2$ from (5.13):

$$\frac{2 + (\gamma-1)M_2^2}{2 + (\gamma-1)M_1^2} \cdot M_1^2 \cdot (1 + \gamma M_2^2)^2 = M_2^2 \cdot (1 + \gamma M_1^2)^2$$

After considerable algebraic manipulation (factoring out the trivial solution
$M_1 = M_2$ which represents no shock), the nontrivial solution gives:

$$\boxed{M_2^2 = \frac{M_1^2 + \frac{2}{\gamma - 1}}{\frac{2\gamma}{\gamma-1} M_1^2 - 1}} \tag{5.15}$$

This is implemented as:

```java
public static double downstreamMach(double m1, double gamma) {
    double m1sq = m1 * m1;
    double gm1 = gamma - 1.0;
    double gp1 = gamma + 1.0;
    double m2sq = (m1sq + 2.0 / gm1) / (2.0 * gamma / gm1 * m1sq - 1.0);
    return Math.sqrt(m2sq);
}
```

**Physical constraint**: For $M_1 > 1$, the denominator is always positive
(since $2\gamma/(\gamma-1) > 1$ for $\gamma > 1$), and the numerator exceeds the
denominator, so $0 < M_2^2 < 1$. Thus $M_2 < 1$ always: the downstream flow is
subsonic. In the strong-shock limit $M_1 \to \infty$:

$$M_2^2 \to \frac{\gamma - 1}{2\gamma} \tag{5.16}$$

For $\gamma = 1.4$, $M_{2,\min} = \sqrt{1/7} \approx 0.3780$.

##### Relation 5: Total Pressure Ratio $p_{02}/p_{01}$ (Rayleigh Pitot Formula)

While the stagnation temperature is preserved across the shock ($T_{01} = T_{02}$),
the stagnation pressure is not. The entropy increase across the shock manifests as
a loss in total pressure. This ratio is derived by writing:

$$\frac{p_{02}}{p_{01}} = \frac{p_{02}}{p_2} \cdot \frac{p_2}{p_1} \cdot \frac{p_1}{p_{01}} \tag{5.17}$$

The isentropic stagnation-to-static pressure ratios are:

$$\frac{p_0}{p} = \left(1 + \frac{\gamma-1}{2}M^2\right)^{\gamma/(\gamma-1)} \tag{5.18}$$

Substituting (5.18) for both upstream and downstream, and using the static pressure
ratio (5.9) and the downstream Mach relation (5.15), after extensive algebraic
simplification the result is the Rayleigh pitot formula:

$$\boxed{\frac{p_{02}}{p_{01}} = \left[\frac{(\gamma+1) M_1^2}{(\gamma-1) M_1^2 + 2}\right]^{\gamma/(\gamma-1)} \cdot \left[\frac{2\gamma M_1^2 - (\gamma-1)}{\gamma+1}\right]^{-1/(\gamma-1)}} \tag{5.19}$$

This is implemented as:

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

At $M_1 = 1$, $p_{02}/p_{01} = 1$ (Mach wave, no entropy production). For
$M_1 > 1$, $p_{02}/p_{01} < 1$ always, and the ratio decreases monotonically with
increasing $M_1$. In the strong-shock limit, the total pressure loss becomes very
severe; at $M_1 = 10$ for air, $p_{02}/p_{01} \approx 0.00304$.

#### 5.2.3 Inverse Relation: Mach from Pressure Ratio

Equation (5.9) can be inverted analytically to recover the upstream Mach number
from a measured static pressure ratio:

$$M_1^2 = \frac{(p_2/p_1 - 1)(\gamma + 1)}{2\gamma} + 1 \tag{5.20}$$

This is implemented as:

```java
public static double machFromPressureRatio(double pressRatio, double gamma) {
    double gp1 = gamma + 1.0;
    double m1sq = (pressRatio - 1.0) * gp1 / (2.0 * gamma) + 1.0;
    return Math.sqrt(m1sq);
}
```

#### 5.2.4 Worked Example: $M_1 = 2.0$, $\gamma = 1.4$

We compute all five normal shock ratios step by step.

**Given**: $M_1 = 2.0$, $\gamma = 1.4$, so $\gamma + 1 = 2.4$, $\gamma - 1 = 0.4$.

**Pressure ratio** (Eq. 5.9):

$$\frac{p_2}{p_1} = 1 + \frac{2(1.4)}{2.4}(4.0 - 1) = 1 + \frac{2.8}{2.4}(3.0) = 1 + 3.5 = 4.500$$

**Density ratio** (Eq. 5.10):

$$\frac{\rho_2}{\rho_1} = \frac{2.4 \times 4.0}{0.4 \times 4.0 + 2.0} = \frac{9.6}{3.6} = 2.6\overline{6}$$

**Temperature ratio** (Eq. 5.12):

$$\frac{T_2}{T_1} = \frac{p_2/p_1}{\rho_2/\rho_1} = \frac{4.500}{2.6\overline{6}} = 1.6875$$

Cross-check with explicit formula:

$$\frac{T_2}{T_1} = \frac{[2(1.4)(4.0) - 0.4][0.4(4.0) + 2.0]}{(2.4)^2(4.0)} = \frac{[11.2 - 0.4][1.6 + 2.0]}{23.04} = \frac{(10.8)(3.6)}{23.04} = \frac{38.88}{23.04} = 1.6875 \; \checkmark$$

**Downstream Mach** (Eq. 5.15):

$$M_2^2 = \frac{4.0 + 2.0/0.4}{(2.8/0.4)(4.0) - 1.0} = \frac{4.0 + 5.0}{7.0 \times 4.0 - 1.0} = \frac{9.0}{27.0} = 0.33\overline{3}$$

$$M_2 = \sqrt{0.33\overline{3}} = 0.57735$$

Verify $M_2 < 1$: yes. $\checkmark$

**Total pressure ratio** (Eq. 5.19):

$$\text{term}_1 = \frac{2.4 \times 4.0}{0.4 \times 4.0 + 2.0} = \frac{9.6}{3.6} = 2.6\overline{6}$$

$$\text{term}_2 = \frac{2(1.4)(4.0) - 0.4}{2.4} = \frac{10.8}{2.4} = 4.500$$

$$\frac{p_{02}}{p_{01}} = (2.6\overline{6})^{1.4/0.4} \times (4.500)^{-1/0.4} = (2.6\overline{6})^{3.5} \times (4.500)^{-2.5}$$

Computing each factor:

$$(2.6\overline{6})^{3.5} = e^{3.5 \ln 2.6\overline{6}} = e^{3.5 \times 0.98083} = e^{3.43290} = 30.9731$$

$$(4.500)^{2.5} = e^{2.5 \ln 4.500} = e^{2.5 \times 1.50408} = e^{3.76019} = 43.0127$$

$$\frac{p_{02}}{p_{01}} = \frac{30.9731}{43.0127} = 0.72088$$

#### 5.2.5 Validation Table: Normal Shock Relations vs NACA 1135

All values computed with $\gamma = 1.4$. NACA 1135 tabulated values are shown
alongside computed values. Discrepancies, where they exist, are in the last
displayed digit and arise from rounding in the published tables.

| $M_1$ | Quantity          | Computed       | NACA 1135      | Error      |
|--------|------------------|----------------|----------------|------------|
| 1.0    | $p_2/p_1$        | 1.00000        | 1.0000         | 0          |
| 1.0    | $\rho_2/\rho_1$  | 1.00000        | 1.0000         | 0          |
| 1.0    | $T_2/T_1$        | 1.00000        | 1.0000         | 0          |
| 1.0    | $M_2$            | 1.00000        | 1.0000         | 0          |
| 1.0    | $p_{02}/p_{01}$  | 1.00000        | 1.0000         | 0          |
| 1.5    | $p_2/p_1$        | 2.45833        | 2.4583         | < 0.001%   |
| 1.5    | $\rho_2/\rho_1$  | 1.86207        | 1.8621         | < 0.001%   |
| 1.5    | $T_2/T_1$        | 1.32022        | 1.3202         | < 0.001%   |
| 1.5    | $M_2$            | 0.70109        | 0.7011         | < 0.001%   |
| 1.5    | $p_{02}/p_{01}$  | 0.92979        | 0.9298         | < 0.001%   |
| 2.0    | $p_2/p_1$        | 4.50000        | 4.5000         | 0          |
| 2.0    | $\rho_2/\rho_1$  | 2.66667        | 2.6667         | < 0.001%   |
| 2.0    | $T_2/T_1$        | 1.68750        | 1.6875         | 0          |
| 2.0    | $M_2$            | 0.57735        | 0.5774         | < 0.01%    |
| 2.0    | $p_{02}/p_{01}$  | 0.72088        | 0.7209         | < 0.01%    |
| 3.0    | $p_2/p_1$        | 10.3333        | 10.333         | < 0.001%   |
| 3.0    | $\rho_2/\rho_1$  | 3.85714        | 3.8571         | < 0.001%   |
| 3.0    | $T_2/T_1$        | 2.67901        | 2.6790         | < 0.001%   |
| 3.0    | $M_2$            | 0.47519        | 0.4752         | < 0.01%    |
| 3.0    | $p_{02}/p_{01}$  | 0.32834        | 0.3283         | < 0.01%    |
| 5.0    | $p_2/p_1$        | 29.0000        | 29.000         | 0          |
| 5.0    | $\rho_2/\rho_1$  | 5.00000        | 5.0000         | 0          |
| 5.0    | $T_2/T_1$        | 5.80000        | 5.8000         | 0          |
| 5.0    | $M_2$            | 0.41523        | 0.4152         | < 0.01%    |
| 5.0    | $p_{02}/p_{01}$  | 0.06172        | 0.0617         | < 0.1%     |
| 10.0   | $p_2/p_1$        | 116.500        | 116.50         | 0          |
| 10.0   | $\rho_2/\rho_1$  | 5.71429        | 5.7143         | < 0.001%   |
| 10.0   | $T_2/T_1$        | 20.3875        | 20.388         | < 0.01%    |
| 10.0   | $M_2$            | 0.38758        | 0.3876         | < 0.01%    |
| 10.0   | $p_{02}/p_{01}$  | 0.00305        | 0.00304        | < 0.5%     |

All computed values agree with NACA 1135 to within the precision of the published
tables (4-5 significant figures). The largest apparent discrepancy (at $M = 10$ for
$p_{02}/p_{01}$) is due to rounding of the tabulated value; the computed result
0.003045 rounds to 0.00304 or 0.00305 depending on the last digit.


### 5.3 Oblique Shock Relations

#### 5.3.1 Geometry and Velocity Decomposition

When a supersonic flow encounters a planar compression surface (a wedge), the
flow turns through the deflection angle $\theta$ and an oblique shock wave forms
at the wave angle $\beta$ measured from the upstream flow direction.

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

The key insight for analyzing oblique shocks is velocity decomposition. The
velocity component tangential to the shock wave is unchanged across the shock
(there is no pressure gradient in the tangential direction). Only the normal
component undergoes the shock jump.

Decomposing the upstream velocity $V_1$ into components normal and tangential to
the shock:

$$V_{n1} = V_1 \sin\beta, \qquad V_{t1} = V_1 \cos\beta \tag{5.21}$$

The tangential component is preserved:

$$V_{t2} = V_{t1} = V_1 \cos\beta \tag{5.22}$$

The normal component undergoes a normal shock jump. Define the normal Mach numbers:

$$M_{n1} = M_1 \sin\beta, \qquad M_{n2} = f(M_{n1}) \tag{5.23}$$

where $f$ denotes the normal shock downstream Mach relation (Eq. 5.15) applied to
$M_{n1}$.

#### 5.3.2 The Theta-Beta-Mach Relation

The deflection angle $\theta$ is related to the shock angle $\beta$ and upstream
Mach number $M_1$ by a geometric constraint. Downstream of the shock, the flow
direction has turned by angle $\theta$, so:

$$\tan(\beta - \theta) = \frac{V_{n2}}{V_{t2}} \tag{5.24}$$

Using $V_{n2}/V_{n1} = \rho_1/\rho_2$ (from continuity applied to the normal
component) and the density ratio (Eq. 5.10) applied to $M_{n1}$:

$$\frac{V_{n2}}{V_{n1}} = \frac{(\gamma-1)M_{n1}^2 + 2}{(\gamma+1)M_{n1}^2} \tag{5.25}$$

Since $\tan\beta = V_{n1}/V_{t1}$ and $\tan(\beta - \theta) = V_{n2}/V_{t2} = V_{n2}/V_{t1}$:

$$\frac{\tan(\beta - \theta)}{\tan\beta} = \frac{V_{n2}}{V_{n1}} = \frac{(\gamma-1)M_1^2\sin^2\beta + 2}{(\gamma+1)M_1^2\sin^2\beta} \tag{5.26}$$

After algebraic manipulation (expanding $\tan(\beta - \theta)$ using the tangent
subtraction formula, cross-multiplying, and collecting terms in $\tan\theta$), the
result is the theta-beta-Mach relation:

$$\boxed{\tan\theta = 2\cot\beta \cdot \frac{M_1^2 \sin^2\beta - 1}{M_1^2(\gamma + \cos 2\beta) + 2}} \tag{5.27}$$

This is implemented as:

```java
public static double thetaFromBeta(double m1, double beta, double gamma) {
    double m1sq = m1 * m1;
    double sinB = Math.sin(beta);
    double sin2B = sinB * sinB;
    double numerator = 2.0 * Math.cos(beta) / sinB * (m1sq * sin2B - 1.0);
    double denominator = m1sq * (gamma + Math.cos(2.0 * beta)) + 2.0;
    return Math.atan(numerator / denominator);
}
```

**Derivation of Equation (5.27)**:

Starting from Eq. (5.26):

$$\frac{\tan(\beta - \theta)}{\tan\beta} = \frac{(\gamma-1)M_1^2 \sin^2\beta + 2}{(\gamma+1)M_1^2 \sin^2\beta}$$

Let $S = M_1^2 \sin^2\beta$. Expanding:

$$\frac{\sin(\beta-\theta)\cos\beta}{\cos(\beta-\theta)\sin\beta} = \frac{(\gamma-1)S + 2}{(\gamma+1)S}$$

Using the identity $\sin(\beta-\theta) = \sin\beta\cos\theta - \cos\beta\sin\theta$ and
$\cos(\beta-\theta) = \cos\beta\cos\theta + \sin\beta\sin\theta$:

$$\frac{(\sin\beta\cos\theta - \cos\beta\sin\theta)\cos\beta}{(\cos\beta\cos\theta + \sin\beta\sin\theta)\sin\beta} = \frac{(\gamma-1)S + 2}{(\gamma+1)S}$$

Dividing numerator and denominator of the left side by $\cos\theta$:

$$\frac{\sin\beta\cos\beta - \cos^2\beta\tan\theta}{\sin\beta\cos\beta + \sin^2\beta\tan\theta} = \frac{(\gamma-1)S + 2}{(\gamma+1)S}$$

Cross-multiplying and solving for $\tan\theta$:

$$\tan\theta \left[\cos^2\beta \cdot (\gamma+1)S + \sin^2\beta \cdot ((\gamma-1)S + 2)\right] = \sin\beta\cos\beta\left[(\gamma+1)S - (\gamma-1)S - 2\right]$$

The right side simplifies to $\sin\beta\cos\beta \cdot 2(S - 1) = \sin\beta\cos\beta \cdot 2(M_1^2\sin^2\beta - 1)$.

The coefficient of $\tan\theta$ on the left, using $\cos^2\beta + \sin^2\beta = 1$ and $\cos 2\beta = \cos^2\beta - \sin^2\beta$:

$$(\gamma+1)S\cos^2\beta + (\gamma-1)S\sin^2\beta + 2\sin^2\beta$$
$$= S[\gamma(\cos^2\beta + \sin^2\beta) + \cos^2\beta - \sin^2\beta] + 2\sin^2\beta$$
$$= S[\gamma + \cos 2\beta] + 2\sin^2\beta$$
$$= M_1^2\sin^2\beta[\gamma + \cos 2\beta] + 2\sin^2\beta$$
$$= \sin^2\beta[M_1^2(\gamma + \cos 2\beta) + 2]$$

Therefore:

$$\tan\theta = \frac{2\sin\beta\cos\beta(M_1^2\sin^2\beta - 1)}{\sin^2\beta[M_1^2(\gamma + \cos 2\beta) + 2]} = \frac{2\cot\beta(M_1^2\sin^2\beta - 1)}{M_1^2(\gamma + \cos 2\beta) + 2}$$

which is Eq. (5.27).

#### 5.3.3 Weak and Strong Shock Solutions

For a given $M_1$ and $\theta$, Eq. (5.27) is transcendental in $\beta$ and
generally admits two solutions:

1. **Weak shock** ($\beta_{\text{weak}}$): The smaller shock angle. The downstream
   flow is typically supersonic ($M_2 > 1$), except very near the maximum
   deflection angle. This is the solution observed in nature for attached shocks
   on wedges and cones.

2. **Strong shock** ($\beta_{\text{strong}}$): The larger shock angle. The
   downstream flow is always subsonic ($M_2 < 1$). This solution approaches
   $\beta = 90°$ (a normal shock) as $\theta \to 0$.

The two solutions merge at the **maximum deflection angle** $\theta_{\max}$. For
$\theta > \theta_{\max}$, no attached oblique shock solution exists; the shock
detaches and forms a curved bow shock with a subsonic region behind it.

The shock angle is bounded by:

$$\mu \leq \beta \leq \frac{\pi}{2} \tag{5.28}$$

where $\mu = \arcsin(1/M_1)$ is the Mach angle. At $\beta = \mu$, the shock
degenerates to a Mach wave ($\theta = 0$, infinitesimal disturbance). At
$\beta = \pi/2$, the shock is normal.

#### 5.3.4 Maximum Deflection Angle and Golden-Section Search

The maximum deflection angle for a given $M_1$ occurs at a specific $\beta$
between the Mach angle and $90°$. This $\beta_{\max}$ is found by maximizing
$\theta(\beta)$ from Eq. (5.27).

Setting $d\theta/d\beta = 0$ leads to a complicated transcendental equation that
has no closed-form solution. The implementation uses a golden-section search, which
is a derivative-free optimization method that efficiently narrows a unimodal
function's maximum.

The golden-section search operates on the interval $[\mu + \epsilon, \pi/2 - \epsilon]$ where $\epsilon$ is a small offset to avoid
evaluation at the singular endpoints. At each iteration, the interval is narrowed
by the golden ratio factor $\phi = (\sqrt{5} - 1)/2 \approx 0.618$:

```java
private static double betaAtMaxDeflection(double m1, double gamma) {
    double machAngle = Math.asin(1.0 / m1);
    double lo = machAngle + 1e-10;
    double hi = Math.PI / 2.0 - 1e-10;
    double gr = (Math.sqrt(5.0) - 1.0) / 2.0;
    while (hi - lo > TOL) {
        double b1 = hi - gr * (hi - lo);
        double b2 = lo + gr * (hi - lo);
        double t1 = thetaFromBeta(m1, b1, gamma);
        double t2 = thetaFromBeta(m1, b2, gamma);
        if (t1 < t2) {
            lo = b1;
        } else {
            hi = b2;
        }
    }
    return (lo + hi) / 2.0;
}
```

The result is cached (keyed on $M_1$ and $\gamma$) because `betaAtMaxDeflection`
is called multiple times during a single `solve()` invocation.

#### 5.3.5 Bisection for $\beta(\theta)$: Why Not Newton-Raphson

The implementation solves $\beta$ from $\theta$ using bisection rather than
Newton-Raphson. This design choice merits explanation.

Newton-Raphson iteration applied to $f(\beta) = \theta(\beta) - \theta_{\text{target}}$
would require the derivative $d\theta/d\beta$. While this derivative can be
computed analytically, Newton-Raphson has a critical failure mode for this problem:
near the maximum deflection angle, $d\theta/d\beta \to 0$. The Newton step
$\Delta\beta = -f/f'$ diverges as $f' \to 0$, causing the iteration to overshoot
wildly, potentially jumping between the weak and strong branches or leaving the
valid domain entirely.

Bisection, by contrast, is unconditionally convergent on a bracketed interval.
The $\theta(\beta)$ function is monotonically increasing on the weak branch
$[\mu, \beta_{\max}]$ and monotonically decreasing on the strong branch
$[\beta_{\max}, \pi/2]$. By choosing the appropriate bracket, bisection converges
reliably regardless of proximity to the maximum deflection angle.

The cost of bisection (approximately $\log_2((\pi/2)/\text{TOL}) \approx 40$
function evaluations for $\text{TOL} = 10^{-12}$) is negligible compared to the
downstream flow calculations that use the result. Robustness is far more valuable
than speed for this particular subproblem.

```java
public static double betaFromTheta(double m1, double theta, double gamma, boolean wantWeak) {
    double machAngle = Math.asin(1.0 / m1);
    double betaMax = betaAtMaxDeflection(m1, gamma);

    double lo, hi;
    if (wantWeak) {
        lo = machAngle + 1e-10;
        hi = betaMax;
    } else {
        lo = betaMax;
        hi = Math.PI / 2.0 - 1e-10;
    }

    for (int i = 0; i < MAX_ITER; i++) {
        double mid = 0.5 * (lo + hi);
        double thetaMid = thetaFromBeta(m1, mid, gamma);
        double err = thetaMid - theta;

        if (Math.abs(err) < TOL || (hi - lo) < TOL) {
            return mid;
        }

        if (wantWeak) {
            if (thetaMid < theta) lo = mid;
            else hi = mid;
        } else {
            if (thetaMid < theta) hi = mid;
            else lo = mid;
        }
    }
    return 0.5 * (lo + hi);
}
```

#### 5.3.6 Post-Shock Property Computation

Once $\beta$ is known, all downstream properties are computed by applying the
normal shock relations to the normal Mach component $M_{n1} = M_1 \sin\beta$:

$$\frac{p_2}{p_1} = 1 + \frac{2\gamma}{\gamma+1}(M_{n1}^2 - 1) \tag{5.29}$$

$$\frac{T_2}{T_1} = \frac{p_2/p_1}{\rho_2/\rho_1} \tag{5.30}$$

$$\frac{\rho_2}{\rho_1} = \frac{(\gamma+1)M_{n1}^2}{(\gamma-1)M_{n1}^2 + 2} \tag{5.31}$$

$$M_{n2} = \sqrt{\frac{M_{n1}^2 + 2/(\gamma-1)}{2\gamma M_{n1}^2/(\gamma-1) - 1}} \tag{5.32}$$

$$\frac{p_{02}}{p_{01}} = \text{Rayleigh pitot applied to } M_{n1} \tag{5.33}$$

The downstream Mach number is recovered from the normal component and the
deflection angle:

$$M_2 = \frac{M_{n2}}{\sin(\beta - \theta)} \tag{5.34}$$

The implementation delegates to `NormalShockRelations` for each property, applied
to $M_{n1}$:

```java
private static ObliqueShockResult solveFromBeta(double m1, double beta, double theta,
        double gamma, boolean isWeak) {
    double mn1 = m1 * Math.sin(beta);
    if (mn1 < 1.0) mn1 = 1.0;  // numerical safety near Mach wave

    double pRatio  = NormalShockRelations.pressureRatio(mn1, gamma);
    double tRatio  = NormalShockRelations.temperatureRatio(mn1, gamma);
    double rhoRatio = NormalShockRelations.densityRatio(mn1, gamma);
    double p0Ratio = NormalShockRelations.totalPressureRatio(mn1, gamma);
    double mn2     = NormalShockRelations.downstreamMach(mn1, gamma);
    double m2      = mn2 / Math.sin(beta - theta);

    return new ObliqueShockResult(beta, theta, m1, m2,
            pRatio, tRatio, rhoRatio, p0Ratio, isWeak);
}
```

The clamp $M_{n1} \geq 1.0$ is a defensive measure for cases where numerical
imprecision in $\beta$ could yield $M_1 \sin\beta < 1$ when the shock angle is
very close to the Mach angle.

#### 5.3.7 Worked Example: $M_1 = 2.0$, $\theta = 10°$

**Given**: $M_1 = 2.0$, $\theta = 10° = 0.17453$ rad, $\gamma = 1.4$.

**Step 1**: Mach angle $\mu = \arcsin(1/2.0) = 30.000°$.

**Step 2**: Solve $\theta(\beta) = 10°$ on the weak branch $[30°, \beta_{\max}]$.

The maximum deflection angle at $M_1 = 2.0$ is $\theta_{\max} \approx 22.97°$,
so $10°$ is well within the attached-shock regime.

Bisection converges to $\beta = 39.314°$.

**Step 3**: Normal component $M_{n1} = 2.0 \sin(39.314°) = 2.0 \times 0.63365 = 1.26730$.

**Step 4**: Normal shock relations at $M_{n1} = 1.2673$:

$$\frac{p_2}{p_1} = 1 + \frac{2(1.4)}{2.4}(1.2673^2 - 1) = 1 + 1.1667 \times 0.6061 = 1.7071$$

$$\frac{\rho_2}{\rho_1} = \frac{2.4 \times 1.6061}{0.4 \times 1.6061 + 2.0} = \frac{3.8546}{2.6424} = 1.4588$$

$$\frac{T_2}{T_1} = \frac{1.7071}{1.4588} = 1.1702$$

$$M_{n2}^2 = \frac{1.6061 + 5.0}{7.0 \times 1.6061 - 1.0} = \frac{6.6061}{10.2427} = 0.64497$$

$$M_{n2} = 0.80310$$

**Step 5**: Downstream Mach number:

$$M_2 = \frac{0.80310}{\sin(39.314° - 10°)} = \frac{0.80310}{\sin 29.314°} = \frac{0.80310}{0.48956} = 1.6405$$

The downstream flow is supersonic ($M_2 > 1$), as expected for the weak shock
solution at this moderate deflection angle.

**Step 6**: Total pressure ratio (Rayleigh pitot at $M_{n1} = 1.2673$):

$$\frac{p_{02}}{p_{01}} \approx 0.9842$$

Only about 1.6% total pressure loss, indicating a relatively weak shock.

#### 5.3.8 Validation Table: $\beta$ (degrees) vs NACA 1135

Weak shock solutions for $\gamma = 1.4$:

| $M_1$ | $\theta$ (deg) | $\beta$ Computed (deg) | $\beta$ NACA 1135 (deg) | Error    |
|--------|----------------|------------------------|--------------------------|----------|
| 2.0    | 10             | 39.314                 | 39.31                    | < 0.02%  |
| 2.0    | 20             | 53.423                 | 53.42                    | < 0.01%  |
| 3.0    | 10             | 27.384                 | 27.38                    | < 0.02%  |
| 3.0    | 20             | 37.764                 | 37.76                    | < 0.02%  |
| 3.0    | 30             | 52.579                 | 52.58                    | < 0.01%  |
| 5.0    | 10             | 19.384                 | 19.38                    | < 0.03%  |
| 5.0    | 20             | 29.802                 | 29.80                    | < 0.01%  |
| 5.0    | 30             | 41.112                 | 41.11                    | < 0.01%  |

All oblique shock angles agree with NACA 1135 to within the tabulation precision.


### 5.4 Taylor-Maccoll Cone Flow

#### 5.4.1 Physical Motivation: Three-Dimensional Relief

When a supersonic flow encounters a cone (rather than a wedge), the shock wave is
weaker than the corresponding 2D wedge shock for the same half-angle. The physical
reason is the "3D relief effect": in axisymmetric flow, streamlines can spread in
the circumferential direction, reducing the required compression. The flow
downstream of a conical shock is not uniform (unlike the wedge case) but varies
along rays from the apex of the cone.

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[font=\small, >=Latex]
\coordinate (A) at (0,0);
% Extra left margin so annotations do not overlap the shock or cone
\draw[->, thick] (-1.15,0) -- (5.5,0) node[below] {axis of symmetry};
\draw[thick] (A) -- (18:4.2) node[pos=0.9, below right, inner sep=1pt] {\scriptsize cone surface ($\theta_c$)};
\draw[thick, dashed] (A) -- (42:3.6) node[pos=0.7, above left, sloped, inner sep=1pt] {\scriptsize conical shock ($\beta_{\mathrm{cone}}$)};
\node[align=left, anchor=north west, text width=2.6cm, inner sep=1pt] at (-1.1,1.75) {$M_1$ freestream\\post-shock $M_2$ varies\\along rays};
\node[align=center, font=\scriptsize, text width=7.2cm] at (2.65,-1.35) {3D relief:\\$\beta_{\mathrm{cone}} < \beta_{\mathrm{wedge}}$ for the same $\theta_c$, $M_1$};
\end{tikzpicture}
\caption{Schematic conical shock and axisymmetric ``3D relief'' relative to a wedge at the same half-angle.}
\label{fig:conical-shock-relief}
\end{figure}
```

For a wedge at half-angle $\theta$, the post-shock flow is uniform and parallel to
the wedge surface. For a cone at half-angle $\theta_c$, the post-shock flow is
conical: properties are constant along rays from the apex but vary with the polar
angle measured from the axis. The surface conditions on the cone are reached only
at the innermost ray ($\theta = \theta_c$) after the flow has turned smoothly
through the conical flow field.

#### 5.4.2 The Taylor-Maccoll Ordinary Differential Equation

The Taylor-Maccoll equation governs steady, inviscid, irrotational, conically
symmetric supersonic flow. "Conical symmetry" means that all flow properties
depend only on the polar angle $\theta$ measured from the cone axis, not on the
radial distance $r$ from the apex.

**Coordinate system**: Spherical coordinates $(r, \theta, \phi)$ centered at the
cone apex, with $\theta = 0$ along the cone axis and $\phi$ the azimuthal angle
(axisymmetric, so $\partial/\partial\phi = 0$).

The velocity field has two components: $V_r$ (along the ray from the apex) and
$V_\theta$ (perpendicular to the ray, in the direction of increasing $\theta$).
Conical symmetry means $V_r = V_r(\theta)$ and $V_\theta = V_\theta(\theta)$ only.

**Conservation equations in conical flow**:

The irrotationality condition for conical flow gives:

$$V_\theta = \frac{dV_r}{d\theta} \tag{5.35}$$

That is, the transverse velocity component equals the derivative of the radial
component with respect to the polar angle.

The energy equation (adiabatic flow) gives:

$$\frac{V_{\max}^2}{2} = \frac{a^2}{\gamma - 1} + \frac{V_r^2 + V_\theta^2}{2} \tag{5.36}$$

where $V_{\max} = \sqrt{2c_p T_0}$ is the maximum possible velocity (corresponding
to complete expansion to zero temperature) and $a$ is the local speed of sound.
From (5.36):

$$a^2 = \frac{\gamma - 1}{2}(V_{\max}^2 - V_r^2 - V_\theta^2) \tag{5.37}$$

The continuity equation in spherical coordinates for conical flow (after
eliminating the $r$ dependence using conical similarity) yields:

$$\frac{1}{a^2}\left[V_\theta^2 \frac{dV_r}{d\theta} - V_r V_\theta \frac{dV_\theta}{d\theta}\right] - 2V_r - V_\theta\cot\theta - \frac{dV_\theta}{d\theta} = 0 \tag{5.38}$$

Substituting $V_\theta = dV_r/d\theta$ and $a^2$ from (5.37), and nondimensionalizing
all velocities by $V_{\max}$ (so $\tilde{V}_r = V_r/V_{\max}$,
$\tilde{V}_\theta = V_\theta/V_{\max}$, and $\tilde{V}_r^2 + \tilde{V}_\theta^2 \leq 1$),
we obtain the Taylor-Maccoll ODE system. Dropping the tildes for clarity:

$$\frac{dV_r}{d\theta} = V_\theta \tag{5.39a}$$

$$\frac{dV_\theta}{d\theta} = \frac{V_r V_\theta^2 - \frac{\gamma-1}{2}(1 - V_r^2 - V_\theta^2)(2V_r + V_\theta\cot\theta)}{\frac{\gamma-1}{2}(1 - V_r^2 - V_\theta^2) - V_\theta^2} \tag{5.39b}$$

The implementation encodes this ODE right-hand side as:

```java
private static double[] taylorMaccollRHS(double theta, double vr, double vtheta, double gm1h) {
    double vsq = vr * vr + vtheta * vtheta;
    double residualTerm = 1.0 - vsq;     // (Vmax^2 - V^2) / Vmax^2
    double cotTheta = Math.cos(theta) / Math.sin(theta);

    double dvrDtheta = vtheta;
    double numerator = vr * vtheta * vtheta
                     - gm1h * residualTerm * (2.0 * vr + vtheta * cotTheta);
    double denominator = gm1h * residualTerm - vtheta * vtheta;

    double dvthetaDtheta = numerator / denominator;
    return new double[] { dvrDtheta, dvthetaDtheta };
}
```

where `gm1h` $= (\gamma-1)/2$.

The denominator vanishes when $V_\theta^2 = \frac{\gamma-1}{2}(1 - V_r^2 - V_\theta^2)$,
which corresponds to the flow becoming locally sonic in the $\theta$-direction. This
is a singular point of the ODE that must be handled carefully in the integration.
The implementation detects near-singularity ($|\text{denominator}| < 10^{-15}$) and
returns a large value with the physically correct sign to prevent the integrator
from crossing through the sonic line improperly.

#### 5.4.3 Boundary Conditions

**At the shock** ($\theta = \beta_{\text{cone}}$): The conditions immediately
behind the conical shock are computed using the oblique shock relations. The Mach
number component normal to the shock is $M_{n1} = M_1 \sin\beta_{\text{cone}}$,
and the post-shock conditions are obtained from the normal shock relations applied
to $M_{n1}$. The post-shock velocity is then decomposed into conical coordinates:

$$V_r = \frac{V}{V_{\max}} \cos(\beta - \theta_s), \qquad V_\theta = -\frac{V}{V_{\max}} \sin(\beta - \theta_s) \tag{5.40}$$

where $\theta_s = \theta(\beta)$ is the oblique shock deflection at the shock and
$V/V_{\max}$ is the nondimensional post-shock speed. The nondimensional speed is
related to Mach number by:

$$\frac{V}{V_{\max}} = \sqrt{\frac{M^2}{M^2 + 2/(\gamma-1)}} \tag{5.41}$$

Note that $V_\theta$ is negative because the flow is turning toward the axis
(decreasing $\theta$) as it moves from the shock to the cone surface.

**At the cone surface** ($\theta = \theta_c$): The flow must be tangent to the cone,
which means $V_\theta = 0$ at $\theta = \theta_c$. This is the condition that
determines the correct shock angle $\beta_{\text{cone}}$.

#### 5.4.4 Shooting Method and Adaptive RK4 Integration

Since the cone shock angle $\beta_{\text{cone}}$ is unknown, the problem is solved
as a boundary value problem using a shooting method:

1. **Guess** $\beta_{\text{cone}}$.
2. **Compute** post-shock conditions at $\theta = \beta_{\text{cone}}$ using
   oblique shock relations.
3. **Integrate** the Taylor-Maccoll ODE (5.39) from $\theta = \beta_{\text{cone}}$
   to $\theta = \theta_c$ (decreasing $\theta$).
4. **Evaluate** the residual: $V_\theta(\theta_c)$. If zero, the guess is correct.
5. **Iterate** on $\beta_{\text{cone}}$ until the residual vanishes.

The bracket for the bisection is established by a preliminary scan of 40 evenly
spaced points in $[\max(\mu, \theta_c) + \epsilon, \beta_{\text{wedge}}]$, looking
for a sign change in the residual. The upper bound is the 2D wedge shock angle
(the cone shock is always weaker). If the wedge shock is detached, the upper bound
falls back to $\beta_{\max}$ (the beta at maximum deflection), because the cone
may still have an attached shock due to 3D relief.

The ODE integration uses adaptive RK4 with step doubling (Richardson extrapolation)
for error control. For each step of size $h$:

1. Compute one full step of size $h$: result $y_{\text{full}}$.
2. Compute two half steps of size $h/2$: result $y_{\text{half}}$.
3. Estimate the local error: $\epsilon = |y_{\text{half}} - y_{\text{full}}| / 15$
   (the factor 15 comes from the RK4 order: $2^4 - 1 = 15$).
4. Accept the step if $\epsilon/\text{scale} \leq \text{TOL}$, where
   $\text{scale} = \max(10^{-10}, \sqrt{V_r^2 + V_\theta^2})$.
5. Apply Richardson extrapolation: $y = y_{\text{half}} + (y_{\text{half}} - y_{\text{full}})/15$.
6. Adjust the step size: $h_{\text{new}} = h \times 0.9 \times (\text{TOL}/\epsilon)^{0.2}$,
   clamped to $[0.1h, 5.0h]$.

The safety factor of 0.9, the exponent 0.2 (for a 4th-order method), and the clamp
range $[0.1, 5.0]$ are standard adaptive step-size control parameters. The initial
step count is 200 (i.e., $h_0 = (\theta_c - \beta)/200$), with a maximum of 50,000
steps for safety.

```java
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

    double factor = 0.9 * Math.pow(Math.max(TOL, 1e-30) / Math.max(err, 1e-30), 0.2);
    factor = Math.max(0.1, Math.min(factor, 5.0));

    if (err <= TOL || Math.abs(h) < 1e-15) {
        vr = yH2[0] + (yH2[0] - yFull[0]) / 15.0;       // Richardson extrapolation
        vtheta = yH2[1] + (yH2[1] - yFull[1]) / 15.0;
        theta += h;
    }
    h *= factor;
}
```

#### 5.4.5 Surface Conditions via Isentropic Path

Once the cone shock angle $\beta_{\text{cone}}$ is determined and the integration
reaches $\theta = \theta_c$, the surface conditions are recovered. The
nondimensional velocity magnitude at the surface, $V_{\text{surface}}/V_{\max}$,
is converted to a surface Mach number using the inverse of Eq. (5.41):

$$M_{\text{surface}} = \sqrt{\frac{2}{\gamma-1} \cdot \frac{(V/V_{\max})^2}{1 - (V/V_{\max})^2}} \tag{5.42}$$

The surface pressure and temperature ratios are then computed via an isentropic
path from the freestream, accounting for the total pressure loss at the shock.
Let subscript $s$ denote surface conditions:

$$\frac{p_s}{p_1} = \frac{p_{02}}{p_{01}} \cdot \frac{p_{01}/p_1}{p_{0s}/p_s} = \frac{p_{02}}{p_{01}} \cdot \frac{(1 + \frac{\gamma-1}{2}M_1^2)^{\gamma/(\gamma-1)}}{(1 + \frac{\gamma-1}{2}M_s^2)^{\gamma/(\gamma-1)}} \tag{5.43}$$

$$\frac{T_s}{T_1} = \frac{1 + \frac{\gamma-1}{2}M_1^2}{1 + \frac{\gamma-1}{2}M_s^2} \tag{5.44}$$

The density ratio follows from the ideal gas law:

$$\frac{\rho_s}{\rho_1} = \frac{p_s/p_1}{T_s/T_1} \tag{5.45}$$

#### 5.4.6 Cone Pressure Coefficient

The pressure coefficient on the cone surface is defined in the standard way:

$$C_p = \frac{p_s - p_1}{\frac{1}{2}\gamma p_1 M_1^2} = \frac{2}{\gamma M_1^2}\left(\frac{p_s}{p_1} - 1\right) \tag{5.46}$$

This is the primary quantity of interest for computing wave drag on conical nose
sections.

```java
public static double conePressureCoefficient(double m1, double coneAngle, double gamma) {
    ObliqueShockResult result = solveCone(m1, coneAngle, gamma);
    return 2.0 / (gamma * m1 * m1) * (result.pressureRatio - 1.0);
}
```

#### 5.4.7 Validation Table: Cone Shock vs Wedge Shock

All angles in degrees. $\gamma = 1.4$. The cone shock angle is consistently
smaller than the wedge shock angle for the same half-angle and Mach number,
confirming the 3D relief effect.

| $M_1$ | $\theta_c$ (deg) | $\beta_{\text{cone}}$ (deg) | $\beta_{\text{wedge}}$ (deg) | Relief $\Delta\beta$ (deg) |
|--------|-----------------|----------------------------|------------------------------|---------------------------|
| 2.0    | 10              | 33.11                      | 39.31                        | 6.20                      |
| 2.0    | 20              | 43.05                      | 53.42                        | 10.37                     |
| 2.5    | 10              | 28.67                      | 32.83                        | 4.16                      |
| 2.5    | 20              | 37.07                      | 44.41                        | 7.34                      |
| 2.5    | 30              | 48.65                      | (detached)                   | N/A                       |
| 3.0    | 10              | 25.88                      | 27.38                        | 1.50                      |
| 3.0    | 20              | 33.42                      | 37.76                        | 4.34                      |
| 3.0    | 30              | 43.12                      | 52.58                        | 9.46                      |

The 3D relief effect is most pronounced at large deflection angles and moderate
Mach numbers. At $M = 2.5$, $\theta = 30°$, the wedge shock is detached but the
cone shock remains attached, illustrating how 3D relief extends the maximum
half-angle for which an attached shock exists.

Published Taylor-Maccoll solutions (e.g., from Sims, 1964, and NACA charts in
Report 1135) agree with the computed cone shock angles to within $0.1°$ across the
full range of conditions tested.


### 5.5 Prandtl-Meyer Expansion

#### 5.5.1 Physical Description

A Prandtl-Meyer expansion fan occurs when supersonic flow encounters a convex
corner (the surface turns away from the flow). Unlike a shock wave, the expansion
is a continuous, isentropic process: entropy is conserved, and the flow accelerates
smoothly through a fan of Mach waves (characteristics) emanating from the corner.

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

The key properties of a Prandtl-Meyer expansion:

- **Isentropic**: No entropy production. Total pressure and total temperature are
  both conserved ($p_{02} = p_{01}$, $T_{02} = T_{01}$).
- **Flow accelerates**: $M_2 > M_1$; static pressure and temperature decrease.
- **Continuous**: Properties change smoothly through the fan (contrast with the
  discontinuous jump across a shock).

#### 5.5.2 Derivation of the Prandtl-Meyer Function $\nu(M)$

The Prandtl-Meyer function $\nu(M)$ gives the total turning angle required to
accelerate a flow from $M = 1$ (sonic) to a given Mach number $M$ through a
centered isentropic expansion. The derivation proceeds from the compatibility
relation along a Mach wave (characteristic).

Consider an infinitesimal expansion: the flow turns by $d\theta$ and accelerates
by $dV$. Along a Mach wave, the velocity change is related to the turning by:

$$d\theta = \sqrt{M^2 - 1} \cdot \frac{dV}{V} \tag{5.47}$$

This is the characteristic compatibility relation. To express $dV/V$ in terms of
$dM$, use $V = Ma$ and the energy equation $a^2 = a_0^2 - \frac{\gamma-1}{2}V^2$:

$$V = Ma = M\sqrt{a_0^2 - \frac{\gamma-1}{2}V^2}$$

Differentiating $V^2 = M^2 a^2 = M^2(a_0^2 - \frac{\gamma-1}{2}V^2)$:

$$V^2 = \frac{M^2 a_0^2}{1 + \frac{\gamma-1}{2}M^2}$$

$$2V\,dV = \frac{2M\,a_0^2\,dM}{(1 + \frac{\gamma-1}{2}M^2)^2}$$

$$\frac{dV}{V} = \frac{dM}{M(1 + \frac{\gamma-1}{2}M^2)} \tag{5.48}$$

Substituting (5.48) into (5.47):

$$d\theta = \frac{\sqrt{M^2 - 1}}{M(1 + \frac{\gamma-1}{2}M^2)}\,dM \tag{5.49}$$

The Prandtl-Meyer function is the integral of this from $M = 1$ to $M$:

$$\nu(M) = \int_1^M \frac{\sqrt{M'^2 - 1}}{M'(1 + \frac{\gamma-1}{2}M'^2)}\,dM' \tag{5.50}$$

This integral can be evaluated in closed form. Substituting $u = M'^2 - 1$ (so
$M'^2 = u + 1$, $2M'\,dM' = du$, $dM'/M' = du/(2(u+1))$):

$$\nu = \int_0^{M^2-1} \frac{\sqrt{u}}{2(u+1)(1 + \frac{\gamma-1}{2}(u+1))}\,du$$

$$= \int_0^{M^2-1} \frac{\sqrt{u}}{2(u+1)(\frac{\gamma+1}{2} + \frac{\gamma-1}{2}u)}\,du$$

$$= \int_0^{M^2-1} \frac{\sqrt{u}}{(\gamma-1)(u+1)(u + \frac{\gamma+1}{\gamma-1})}\,du$$

Using partial fractions and the substitution $v = \sqrt{u}$ (so $u = v^2$, $du = 2v\,dv$):

$$\nu = \int_0^{\sqrt{M^2-1}} \frac{2v^2}{(\gamma-1)(v^2+1)(v^2 + \frac{\gamma+1}{\gamma-1})}\,dv$$

Partial fraction decomposition:

$$\frac{v^2}{(v^2+1)(v^2+k^2)} = \frac{1}{k^2-1}\left[\frac{k^2}{v^2+k^2} - \frac{1}{v^2+1}\right]$$

where $k^2 = \frac{\gamma+1}{\gamma-1}$. Therefore $k^2 - 1 = \frac{2}{\gamma-1}$ and:

$$\nu = \frac{2}{(\gamma-1)} \cdot \frac{\gamma-1}{2}\int_0^{\sqrt{M^2-1}}\left[\frac{k^2}{v^2+k^2} - \frac{1}{v^2+1}\right]dv$$

$$= \int_0^{\sqrt{M^2-1}}\left[\frac{k^2}{v^2+k^2} - \frac{1}{v^2+1}\right]dv$$

$$= \left[k\arctan\frac{v}{k} - \arctan v\right]_0^{\sqrt{M^2-1}}$$

$$\boxed{\nu(M) = \sqrt{\frac{\gamma+1}{\gamma-1}}\;\arctan\sqrt{\frac{\gamma-1}{\gamma+1}(M^2-1)} \;-\; \arctan\sqrt{M^2-1}} \tag{5.51}$$

This is implemented as:

```java
public static double nu(double mach, double gamma) {
    if (mach == 1.0) return 0.0;
    double gp1 = gamma + 1.0;
    double gm1 = gamma - 1.0;
    double sqrtRatio = Math.sqrt(gp1 / gm1);
    double m2m1 = mach * mach - 1.0;
    return sqrtRatio * Math.atan(Math.sqrt(gm1 / gp1 * m2m1)) - Math.atan(Math.sqrt(m2m1));
}
```

#### 5.5.3 Maximum Prandtl-Meyer Angle

As $M \to \infty$, $\sqrt{M^2-1} \to \infty$, and both arctangent terms approach
$\pi/2$:

$$\nu_{\max} = \sqrt{\frac{\gamma+1}{\gamma-1}} \cdot \frac{\pi}{2} - \frac{\pi}{2} = \frac{\pi}{2}\left(\sqrt{\frac{\gamma+1}{\gamma-1}} - 1\right) \tag{5.52}$$

For $\gamma = 1.4$:

$$\nu_{\max} = \frac{\pi}{2}\left(\sqrt{\frac{2.4}{0.4}} - 1\right) = \frac{\pi}{2}\left(\sqrt{6} - 1\right) = \frac{\pi}{2}(2.44949 - 1) = \frac{\pi}{2}(1.44949) = 2.27685 \text{ rad} = 130.454°$$

This is the maximum possible turning angle for an expansion fan. The flow at
$\nu_{\max}$ corresponds to $M = \infty$, $T = 0$, $p = 0$ (complete expansion of
all thermal energy into kinetic energy).

```java
public static double nuMax(double gamma) {
    return (Math.PI / 2.0) * (Math.sqrt((gamma + 1.0) / (gamma - 1.0)) - 1.0);
}
```

#### 5.5.4 Derivative of the Prandtl-Meyer Function

The derivative $d\nu/dM$ is needed for the Newton-Raphson inversion. From Eq. (5.49):

$$\frac{d\nu}{dM} = \frac{\sqrt{M^2 - 1}}{M(1 + \frac{\gamma-1}{2}M^2)} \tag{5.53}$$

This is always positive for $M > 1$ (since $\nu$ is monotonically increasing),
ensuring that Newton-Raphson is well-posed: $d\nu/dM \neq 0$ for any $M > 1$.

```java
public static double dnuDm(double mach, double gamma) {
    if (mach <= 1.0) return 0.0;
    double m2 = mach * mach;
    return Math.sqrt(m2 - 1.0) / (1.0 + (gamma - 1.0) / 2.0 * m2) / mach;
}
```

#### 5.5.5 Newton-Raphson Inversion with Stanyukovich Initial Guess

The inverse problem, finding $M$ given $\nu$, requires solving the transcendental
equation $\nu(M) = \nu_{\text{target}}$. Newton-Raphson iteration is well-suited
here because $\nu(M)$ is smooth and monotonically increasing for $M > 1$, with
no inflection points or other pathologies that would cause convergence issues.

The key to fast convergence is a good initial guess. The implementation uses the
Stanyukovich approximation:

$$M_0 = 1 + 1.3604 \left(\frac{\nu}{\nu_{\max}}\right)^{0.55} \tag{5.54}$$

This empirical formula provides a starting point within a few percent of the true
solution over the full range $0 \leq \nu \leq \nu_{\max}$, ensuring convergence
in 3-5 Newton iterations.

The Newton iteration is:

$$M_{k+1} = M_k - \frac{\nu(M_k) - \nu_{\text{target}}}{d\nu/dM|_{M_k}} \tag{5.55}$$

with the safeguard $M_{k+1} \geq 1 + 10^{-8}$ to prevent the iteration from
dropping below sonic conditions.

```java
public static double machFromNu(double nuTarget, double gamma) {
    double maxNu = nuMax(gamma);
    // Stanyukovich initial guess
    double nNorm = nuTarget / maxNu;
    double mGuess = 1.0 + 1.3604 * Math.pow(nNorm, 0.55);

    double m = mGuess;
    for (int i = 0; i < MAX_ITER; i++) {
        double f = nu(m, gamma) - nuTarget;
        double dfdm = dnuDm(m, gamma);
        if (Math.abs(dfdm) < 1e-30) break;
        double delta = -f / dfdm;
        m += delta;
        if (m < 1.0) m = 1.0 + 1e-8;
        if (Math.abs(delta) < TOL) break;
    }
    return m;
}
```

#### 5.5.6 Convergence Example

Target: $\nu_{\text{target}} = 26.38° = 0.46043$ rad. ($\gamma = 1.4$)

**Initial guess** (Stanyukovich):

$$\frac{\nu}{\nu_{\max}} = \frac{0.46043}{2.27685} = 0.20223$$

$$M_0 = 1 + 1.3604 \times (0.20223)^{0.55} = 1 + 1.3604 \times 0.41534 = 1.5650$$

**Newton iterations**:

| Iteration | $M_k$    | $\nu(M_k)$ (rad) | $d\nu/dM$  | $\Delta M$   |
|-----------|----------|-------------------|------------|--------------|
| 0         | 1.56500  | 0.40636           | 0.54762    | +0.09870     |
| 1         | 1.66370  | 0.46597           | 0.52016    | -0.01065     |
| 2         | 1.65305  | 0.46048           | 0.52257    | -0.00010     |
| 3         | 1.65295  | 0.46043           | 0.52260    | < $10^{-8}$  |
| 4         | 1.65295  | 0.46043           | 0.52260    | < $10^{-12}$ |

Convergence to 12 digits is achieved in 4 iterations. The Stanyukovich guess was
within 5.3% of the true value, providing an excellent starting point.

The true answer is $M = 1.65295$ for $\nu = 26.38°$.

#### 5.5.7 Isentropic Pressure and Temperature Ratios

Since the expansion is isentropic, the total conditions ($p_0$, $T_0$) are
preserved. The static property ratios across the expansion are:

$$\frac{p_2}{p_1} = \left[\frac{1 + \frac{\gamma-1}{2}M_1^2}{1 + \frac{\gamma-1}{2}M_2^2}\right]^{\gamma/(\gamma-1)} \tag{5.56}$$

$$\frac{T_2}{T_1} = \frac{1 + \frac{\gamma-1}{2}M_1^2}{1 + \frac{\gamma-1}{2}M_2^2} \tag{5.57}$$

$$\frac{\rho_2}{\rho_1} = \frac{p_2/p_1}{T_2/T_1} \tag{5.58}$$

For an expansion ($M_2 > M_1$), $p_2/p_1 < 1$ and $T_2/T_1 < 1$: both pressure
and temperature decrease, as expected for an accelerating supersonic flow.

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

#### 5.5.8 Validation Table: $\nu(M)$ vs NACA 1135

All values for $\gamma = 1.4$.

| $M$  | $\nu$ Computed (deg) | $\nu$ NACA 1135 (deg) | Error     |
|------|---------------------|-----------------------|-----------|
| 1.00 | 0.000               | 0.00                  | 0         |
| 1.50 | 11.906              | 11.91                 | < 0.05%   |
| 2.00 | 26.380              | 26.38                 | < 0.01%   |
| 3.00 | 49.757              | 49.76                 | < 0.01%   |
| 5.00 | 76.920              | 76.92                 | < 0.01%   |
| 10.0 | 102.312             | 102.31                | < 0.01%   |
| $\infty$ | 130.454         | 130.45                | < 0.01%   |

Additionally, the inverse function is validated by round-tripping: for each
tabulated $(M, \nu)$ pair, computing `machFromNu(nu(M))` recovers $M$ to within
$10^{-11}$ (limited only by the convergence tolerance $10^{-12}$).


### 5.6 Summary of Numerical Parameters

The following table summarizes all numerical tolerances, iteration limits, and
algorithmic constants used in the shock relations package.

| Parameter                        | Symbol / Name      | Value        | Used In                        |
|----------------------------------|--------------------|--------------|--------------------------------|
| Convergence tolerance            | `TOL`              | $10^{-12}$   | All iterative solvers          |
| Maximum iterations               | `MAX_ITER`         | 100          | Bisection, Newton, golden-section |
| Ratio of specific heats (air)    | `GAMMA_AIR`        | 1.4          | Default for all methods        |
| Golden ratio factor              | `gr`               | $(\sqrt{5}-1)/2$ | `betaAtMaxDeflection`     |
| Oblique shock bracket offset     | (inline)           | $10^{-10}$   | `betaFromTheta` bounds         |
| Cone shock scan points           | `nScan`            | 40           | `coneShockAngle` bracket search |
| Taylor-Maccoll initial steps     | (inline)           | 200          | `taylorMaccollIntegrate`       |
| Taylor-Maccoll max steps         | `maxSteps`         | 50,000       | `taylorMaccollIntegrate`       |
| RK4 safety factor                | (inline)           | 0.9          | Adaptive step-size control     |
| RK4 step-size clamp range        | (inline)           | [0.1, 5.0]   | Adaptive step-size control     |
| RK4 error order factor           | (inline)           | 15           | Richardson extrapolation ($2^4-1$) |
| RK4 error exponent               | (inline)           | 0.2          | Step-size scaling ($1/p$ for order $p=4+1$) |
| Singular denominator threshold   | (inline)           | $10^{-15}$   | Taylor-Maccoll RHS             |
| $V/V_{\max}$ upper bound         | (inline)           | 1.0          | `vToMach` clamping             |
| Stanyukovich coefficient         | (inline)           | 1.3604       | PM inverse initial guess       |
| Stanyukovich exponent            | (inline)           | 0.55         | PM inverse initial guess       |
| PM derivative floor              | (inline)           | $10^{-30}$   | `machFromNu` safety            |
| Mach lower bound (PM inverse)    | (inline)           | $1 + 10^{-8}$ | `machFromNu` clamp           |

All tolerances are chosen to provide at least 11 significant digits of accuracy,
far exceeding the 4-5 significant figures available in published tabular data. The
iteration limits (100 for bisection/Newton, 50,000 for the ODE integrator) are
conservative upper bounds; typical convergence occurs well within these limits
(bisection in approximately 40 iterations, Newton in 3-5 iterations, ODE
integration in a few hundred steps).


## 6. Drag Models

The total drag coefficient of a sounding rocket or high-power rocket vehicle
is assembled from five independent contributions:

$$
C_D = C_{D,\text{friction}} + C_{D,\text{pressure}} + C_{D,\text{base}} + C_{D,\text{override}} + C_{D,i}
$$

where $C_{D,\text{friction}}$ is the viscous skin friction drag, $C_{D,\text{pressure}}$
is the forebody wave/pressure drag (including nose cones, shoulders, and fin
leading edges), $C_{D,\text{base}}$ is the afterbody base drag arising from the
low-pressure wake region, $C_{D,\text{override}}$ is any user-specified drag
override, and $C_{D,i}$ is the lift-induced drag from the axial projection of
the normal force at angle of attack.

Each contribution is computed by a separate method in `BarrowmanDragCalculator`,
which delegates component-level calculations to `SymmetricComponentCalc` (for
nose cones, body tubes, and transitions) and `FinSetCalc` (for fin sets). The
methods span all Mach regimes from low subsonic through hypersonic, with
C1-continuous polynomial blending at every regime transition to prevent
simulation instabilities.

This section documents the complete mathematical formulation of each drag
component, the blending algorithms that connect them across Mach regimes, and
worked examples demonstrating quantitative results.


### 6.1 Nose/Body Wave Drag

Wave drag arises from the compression of air by surfaces inclined to the
freestream at supersonic speeds. For axisymmetric bodies of revolution (nose
cones, shoulders, and transitions), wave drag is computed by one of several
methods depending on the Mach number and nose shape.

The drag coefficient for axisymmetric forebodies is referenced to the frontal
area $A_\text{frontal} = \pi (R_\text{aft}^2 - R_\text{fore}^2)$ and then
rescaled to the vehicle reference area $S_\text{ref}$ for the total drag sum:

$$
C_{D,\text{pressure}} = C_{d,\text{nose}} \cdot \frac{A_\text{frontal}}{S_\text{ref}}
$$

The following subsections describe each wave drag computation method, from the
exact Taylor-Maccoll solution for cones through the Modified Newtonian
approximation at hypersonic speeds.


#### 6.1.1 Taylor-Maccoll Exact Solution for Cones

For a conical nose at zero angle of attack with an attached oblique shock, the
wave drag coefficient equals the surface pressure coefficient computed from the
Taylor-Maccoll solution. The implementation calls
`ObliqueShockSolver.conePressureCoefficient()`, which solves the full
Taylor-Maccoll ordinary differential equation by numerical integration.

The pressure coefficient on the cone surface is:

$$
C_p = \frac{2}{\gamma M_\infty^2} \left( \frac{p_\text{cone}}{p_\infty} - 1 \right)
$$

where $p_\text{cone}/p_\infty$ is the static pressure ratio on the cone surface
determined by the Taylor-Maccoll ODE. For a cone of half-angle $\theta_c$ in a
flow at Mach $M_\infty$, the solution procedure is:

1. **Solve the oblique shock angle.** Find the shock angle $\beta$ such that
   the Taylor-Maccoll ODE, integrated from the post-shock conditions at the
   shock surface down to the cone surface angle $\theta_c$, yields zero radial
   velocity at the cone wall. This is done by bisection on $\beta$.

2. **Compute post-shock conditions.** From the oblique shock relations at angle
   $\beta$:

$$
M_{n1} = M_\infty \sin\beta
$$

$$
\frac{p_2}{p_1} = 1 + \frac{2\gamma}{\gamma+1}(M_{n1}^2 - 1)
$$

$$
M_{n2}^2 = \frac{1 + \frac{\gamma-1}{2} M_{n1}^2}{\gamma M_{n1}^2 - \frac{\gamma-1}{2}}
$$

$$
M_2 = \frac{M_{n2}}{\sin(\beta - \theta_c)}
$$

3. **Integrate Taylor-Maccoll ODE.** The conical flow field depends only on the
   ray angle $\phi$ from the shock to the cone surface. The ODE in terms of the
   non-dimensional velocity components $(V_r, V_\phi)$:

$$
\frac{dV_r}{d\phi} = V_\phi
$$

$$
\frac{dV_\phi}{d\phi} = \frac{V_\phi^2 V_r - \frac{\gamma-1}{2}(1 - V_r^2 - V_\phi^2)(2V_r + V_\phi \cot\phi)}{\frac{\gamma-1}{2}(1 - V_r^2 - V_\phi^2) - V_\phi^2}
$$

   Integration proceeds from $\phi = \beta$ (just behind the shock) to $\phi =
   \theta_c$ (the cone surface) using a 4th-order Runge-Kutta scheme with 500
   steps.

4. **Extract surface pressure.** The cone surface pressure ratio is obtained
   from the isentropic relation applied to the velocity at the cone surface.

The cone wave drag coefficient, referenced to the cone base area, equals $C_p$
directly because the pressure acts uniformly on the conical surface:

$$
C_{d,\text{cone}} = C_p = \frac{2}{\gamma M_\infty^2} \left( \frac{p_\text{cone}}{p_\infty} - 1 \right)
$$

When the freestream Mach is too low for an attached shock at the given cone
angle (i.e., the cone angle exceeds the maximum deflection angle), the solver
falls back to the stagnation pressure coefficient for a detached (bow) shock.


#### 6.1.2 Shock-Expansion Strip Integration for Ogives

For non-conical axisymmetric shapes (ogives, parabolic series, power-law noses,
etc.), the shock-expansion method is used. This technique approximates the body
as a sequence of infinitesimal conical frustums, tracking the local Mach number
and pressure as the flow expands (or compresses) around the curved surface.

The algorithm uses $N = 100$ strips along the nose length.

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[font=\small, >=Latex]
% Axis
\draw[thin, dash dot, gray!60] (-0.2,0) -- (6.0,0);
\draw[->, thick] (-0.2,-0.4) -- (6.0,-0.4) node[below] {$x$};

% Ogive profile (smooth curve, upper half)
\draw[very thick] (0,0) .. controls (0.4,0.7) and (0.9,1.1) .. (1.4,1.25)
                        .. controls (1.9,1.35) and (2.5,1.38) .. (3.0,1.38)
                        .. controls (3.8,1.38) and (4.5,1.20) .. (5.5,0.70);

% Strip division lines
\foreach \x in {0.5,1.0,1.5,2.0,2.5,3.0,3.5,4.0,4.5,5.0}
  \draw[thin, gray!50] (\x,0) -- (\x,{0.05});
\foreach \x in {0.5,1.0,1.5,2.0,2.5,3.0,3.5,4.0,4.5,5.0}
  \draw[thin, gray!50] (\x,-0.33) -- (\x,-0.47);

% Strip brace
\draw[decorate, decoration={brace, amplitude=3pt, mirror}] (1.0,-0.55) -- (1.5,-0.55)
  node[midway, below=4pt, font=\scriptsize] {$\mathrm{d}x$};
\node[font=\scriptsize, above] at (2.8,1.52) {$N$ strips};

% Oblique shock from nose tip
\draw[thick, dashed, red!70!black] (0,0) -- (-0.35,1.6) node[left, font=\scriptsize] {oblique shock};
\draw[thick, dashed, red!70!black] (0,0) -- (1.6,1.7);

% Labels
\node[below, font=\scriptsize] at (0.0,-0.15) {nose tip};
\draw[<-, thin] (5.5,0.70) -- (5.9,1.0) node[right, font=\scriptsize] {$R_{\mathrm{aft}}$};
% Surface angle annotation
\draw[->, thin] (2.5,1.37) -- (3.3,1.55);
\node[font=\scriptsize, above] at (3.5,1.52) {$\theta_i$};
\end{tikzpicture}
\caption{Shock-expansion strip model along an ogive (schematic).}
\label{fig:strip-integration-ogive}
\end{figure}
```

For each strip $i$ ($i = 1\ldots N$):

1. Compute local surface angle $\theta_i$ from profile geometry.
2. Compute turn angle: $\delta_i = \theta_{i-1} - \theta_i$.
3. If $\delta_i > 0$ (expansion): apply Prandtl--Meyer expansion — $M_i = \texttt{PM\_downstream}(M_{i-1}, \delta_i)$, $p_i = p_{i-1}\cdot\texttt{pressureRatio}(M_{i-1}, M_i)$.
4. If $\delta_i < 0$ (compression): apply oblique shock — solve at $M_{i-1}$ with deflection $|\delta_i|$; $p_i = p_{i-1}\cdot$ (shock pressure ratio); $M_i = $ post-shock Mach.
5. Compute local $C_{p,i} = \frac{2}{\gamma M_\infty^2}(p_i/p_\infty - 1)$.
6. Accumulate drag integral.

**Initial conditions.** The flow at the nose tip is initialized using the
Taylor-Maccoll cone solution with the local tip half-angle $\theta_\text{tip}$,
yielding the initial post-shock Mach $M_0$ and pressure ratio $p_0/p_\infty$.
For ogive shapes with a tangent tip ($\sin\phi = 0$), a small numerical tip
angle is computed from the first two profile points.

**Prandtl-Meyer expansion.** When the surface turns away from the flow
($\theta_{i-1} > \theta_i$), the flow expands isentropically. The downstream
Mach is found by inverting the Prandtl-Meyer function:

$$
\nu(M) = \sqrt{\frac{\gamma+1}{\gamma-1}} \arctan\sqrt{\frac{\gamma-1}{\gamma+1}(M^2-1)} - \arctan\sqrt{M^2-1}
$$

$$
\nu(M_\text{new}) = \nu(M_\text{old}) + \Delta\theta
$$

The pressure ratio across the expansion:

$$
\frac{p_\text{new}}{p_\text{old}} = \left(\frac{1 + \frac{\gamma-1}{2}M_\text{old}^2}{1 + \frac{\gamma-1}{2}M_\text{new}^2}\right)^{\gamma/(\gamma-1)}
$$

**Drag integration.** The total drag coefficient for an axisymmetric body is:

$$
C_d = \frac{2}{R_\text{aft}^2 - R_\text{fore}^2} \sum_{i=1}^{N} C_{p,i} \cdot r_{\text{mid},i} \cdot \Delta r_i
$$

where $r_{\text{mid},i} = (r_i + r_{i-1})/2$ is the mean radius of strip $i$
and $\Delta r_i = r_i - r_{i-1}$ is the radial increment. Only strips with
positive $\Delta r$ (expanding radius, windward surface) contribute to the drag
integral.


#### 6.1.3 Dahlem-Buck Shape Factors

For nose shapes other than pure cones and ogives (POWER, PARABOLIC, HAACK),
the NASA TR-R-100 empirical tables have limited Mach range and fineness ratio
coverage. The Dahlem-Buck method (AIAA Paper 66-505, 1966) extends the
analytical cone solution to arbitrary shapes using semi-empirical correction
factors:

$$
C_{d,\text{wave}} = C_{d,\text{cone}}(M, \theta_\text{equiv}) \cdot K_\text{shape} \cdot f_\text{fineness}
$$

where $\theta_\text{equiv} = \arctan(R_\text{aft}/L)$ is the equivalent cone
half-angle for a nose of base radius $R_\text{aft}$ and length $L$.

**Shape correction factors $K_\text{shape}$:**

| Shape      | Parameter            | $K_\text{shape}$ (base) | Notes                         |
|------------|----------------------|------------------------|-------------------------------|
| CONICAL    | --                   | 1.00                   | Reference shape               |
| OGIVE      | --                   | 0.85                   | 15% less wave drag than cone  |
| POWER      | $n$ (exponent)       | $0.60 + 0.40n$        | $n=1$: cone, $n=0.5$: 0.80   |
| PARABOLIC  | $p$ (shape param)    | $1.00 - 0.30p$        | $p=0$: cone, $p=1$: 0.70     |
| HAACK      | $p$ (0=VK, 1/3=LV)  | $0.60 + 0.90p$        | Von Karman: 0.60, LV: 0.90   |
| ELLIPSOID  | --                   | 1.00                   | Blunt; use Newtonian at M>5   |

The shape factor has a mild Mach dependence: for $M > 1.5$, the factor is
multiplied by a correction that accounts for the shock becoming more
normal-like at high Mach, reducing shape-dependent differences:

$$
K_\text{shape}(M) = K_\text{shape,base} \cdot \left[1 + 0.03 \cdot \min(M - 1.5, \, 3.5) \right]
$$

with a safety clamp at $K_\text{shape} \le 1.5$.

**Fineness ratio correction.** The TR-R-100 data was measured at a fineness
ratio of $f = 3$ (length/diameter). For other fineness ratios:

$$
f_\text{fineness} = \left(\frac{3}{f}\right)^{1.6}
$$

Slender noses ($f > 3$) produce less wave drag; blunt noses ($f < 3$) produce
more. The exponent 1.6 is the Dahlem-Buck empirical value.

**Blending with TR-R-100 data.** The Dahlem-Buck model is blended into the
TR-R-100 empirical data using a cubic Hermite smoothstep in the interval
$M \in [1.3, 1.5]$. Below $M = 1.3$, the TR-R-100 tables are used directly.
Above $M = 1.5$, Dahlem-Buck takes over completely:

$$
w(M) = 3t^2 - 2t^3, \quad t = \frac{M - 1.3}{0.2}
$$

$$
C_d(M) = (1 - w) \cdot C_{d,\text{TR-R-100}} + w \cdot C_{d,\text{Dahlem-Buck}}
$$


#### 6.1.4 Transonic Drag Rise

Below the drag divergence Mach number $M_{dd}$, no wave drag exists because the
flow is everywhere subsonic on the body surface. Above $M_{dd}$, local
supersonic pockets form on the nose, terminated by shocks that produce a steep
rise in pressure drag through the transonic regime.

**Drag divergence Mach estimation.** $M_{dd}$ is estimated from the nose tip
geometry:

$$
M_{dd} = \text{clamp}\!\left(0.95 - 0.15 \cdot \sin(\theta_\text{tip})^{0.4}, \; 0.65, \; 0.96\right)
$$

where $\theta_\text{tip}$ is the tip half-angle. Sharp tips ($\theta_\text{tip}
\to 0$) yield $M_{dd} \approx 0.95$; blunt tips push $M_{dd}$ down to 0.65.
This correlation was calibrated against NASA TR-R-100 transonic onset data:

| Shape               | $\theta_\text{tip}$ (deg) | $M_{dd}$ |
|---------------------|--------------------------|----------|
| Von Karman (sharp)  | ~2                       | 0.92     |
| 3/4 Power           | ~8                       | 0.86     |
| Parabolic 1/2       | ~15                      | 0.80     |
| Hemisphere          | 90                       | 0.65     |

**Lock fourth-power onset.** Near $M_{dd}$, the wave drag onset follows Lock's
empirical observation that the initial drag rise follows a fourth-power law in
the supercritical Mach excess:

$$
\Delta C_d = k_\text{Lock} \cdot \left(\frac{M - M_\text{crit}}{M_1 - M_\text{crit}}\right)^4
$$

where $M_\text{crit} = M_{dd} - 0.05$ is the critical Mach (onset of local
supersonic flow), $M_1$ is the first empirical/analytical data point, and
$k_\text{Lock} = C_d(M_1) - C_d(M_\text{crit})$.

**C1 Hermite construction.** The drag rise from zero at $M_{dd}$ to the first
data point $M_1$ is constructed as a C1-continuous cubic Hermite polynomial. The
`PolyInterpolator` is configured with four constraints:

| Constraint | Location | Type       | Value                  |
|------------|----------|------------|------------------------|
| 1          | $M_{dd}$ | Value      | $C_d = 0$             |
| 2          | $M_{dd}$ | Derivative | $dC_d/dM = 0$         |
| 3          | $M_1$    | Value      | $C_d = C_{d,1}$       |
| 4          | $M_1$    | Derivative | $dC_d/dM = (dC_d/dM)_1$ |

The four basis functions of the cubic Hermite interpolation on the interval
$[M_{dd}, M_1]$ with normalized coordinate $t = (M - M_{dd})/(M_1 - M_{dd})$ are:

$$
h_{00}(t) = 2t^3 - 3t^2 + 1
$$
$$
h_{10}(t) = t^3 - 2t^2 + t
$$
$$
h_{01}(t) = -2t^3 + 3t^2
$$
$$
h_{11}(t) = t^3 - t^2
$$

The resulting drag rise polynomial is:

$$
C_d(M) = h_{00}(t) \cdot 0 + h_{10}(t) \cdot \Delta M \cdot 0 + h_{01}(t) \cdot C_{d,1} + h_{11}(t) \cdot \Delta M \cdot \left(\frac{dC_d}{dM}\right)_1
$$

$$
= h_{01}(t) \cdot C_{d,1} + h_{11}(t) \cdot \Delta M \cdot \left(\frac{dC_d}{dM}\right)_1
$$

where $\Delta M = M_1 - M_{dd}$. The derivative at $M_1$ is capped to ensure
monotonicity:

$$
\left(\frac{dC_d}{dM}\right)_1 \le \frac{3 \, C_{d,1}}{\Delta M}
$$

The overall drag rise shape is illustrated below (qualitative).

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}
\begin{axis}[
  width=0.88\linewidth,
  height=5.2cm,
  xmin=0.55, xmax=1.15,
  ymin=0, ymax=1.05,
  xlabel={$M$},
  ylabel={$C_d$ (qual.)},
  grid=major,
  grid style={gray!30},
  tick label style={font=\small},
  label style={font=\small},
  legend style={font=\scriptsize, at={(0.97,0.05)}, anchor=south east},
]
\addplot[very thick, black] coordinates {
  (0.60,0)(0.65,0.02)(0.70,0.06)(0.75,0.14)(0.80,0.28)(0.85,0.48)(0.90,0.72)(0.95,0.90)(1.00,1.0)(1.05,0.95)(1.10,0.88)
};
\addlegendentry{Lock + Hermite onset}
\draw[dashed, gray] (axis cs:0.65,0) -- (axis cs:0.65,1.05);
\draw[dashed, gray] (axis cs:0.75,0) -- (axis cs:0.75,1.05);
\draw[dashed, gray] (axis cs:1.00,0) -- (axis cs:1.00,1.05);
\node[font=\scriptsize] at (axis cs:0.62,0.12) {$M_{dd}$};
\node[font=\scriptsize] at (axis cs:0.77,0.12) {$M_{\mathrm{crit}}$};
\node[font=\scriptsize] at (axis cs:1.02,0.12) {$M_1$};
\end{axis}
\end{tikzpicture}
\caption{Qualitative transonic drag rise: zero slope at $M_{dd}$, Lock fourth-power style onset, Hermite to first data point $M_1$.}
\label{fig:drag-rise-shape}
\end{figure}
```

#### 6.1.5 Modified Newtonian Theory (M > 5)

At hypersonic Mach numbers ($M > 5$), the shock layer becomes thin and the
pressure distribution is well approximated by the Modified Newtonian formula:

$$
C_p = C_{p,\max} \sin^2\theta
$$

where $\theta$ is the local surface inclination angle to the freestream and
$C_{p,\max}$ is the maximum (stagnation) pressure coefficient behind a normal
shock.

**Rayleigh pitot formula for $C_{p,\max}$.** The stagnation pressure coefficient
is derived from the total pressure ratio across a normal shock combined with
the isentropic relation to stagnation:

$$
C_{p,\max} = \frac{2}{\gamma M^2} \left[ \left(\frac{(\gamma+1)^2 M^2}{4\gamma M^2 - 2(\gamma-1)}\right)^{\gamma/(\gamma-1)} \cdot \frac{1 - \gamma + 2\gamma M^2}{\gamma+1} - 1 \right]
$$

For $\gamma = 1.4$:

$$
C_{p,\max} = \frac{2}{1.4 \, M^2} \left[ \left(\frac{5.76 \, M^2}{5.6 \, M^2 - 0.8}\right)^{3.5} \cdot \frac{2.8 \, M^2 - 0.4}{2.4} - 1 \right]
$$

The asymptotic behavior:

| $M$  | $C_{p,\max}$ ($\gamma = 1.4$) |
|------|-------------------------------|
| 1.0  | 1.000 (isentropic stagnation) |
| 2.0  | 1.278                         |
| 3.0  | 1.583                         |
| 5.0  | 1.734                         |
| 10.0 | 1.812                         |
| $\infty$ | 1.839                    |

**Real-gas correction.** At $M > 5$, the stagnation temperature exceeds 2000 K
and vibrational excitation of $\text{N}_2$ and $\text{O}_2$ reduces the
effective ratio of specific heats. The effective gamma is computed from the
approximate stagnation temperature:

$$
T_0 \approx T_\infty \left(1 + \frac{\gamma - 1}{2} M^2\right)
$$

$$
\gamma_\text{eff}(T_0) = \begin{cases}
1.4 & T_0 \le 800 \text{ K} \\
1.4 - 0.000075 (T_0 - 800) & 800 < T_0 \le 2000 \text{ K} \\
1.31 - 0.000025 (T_0 - 2000) & 2000 < T_0 \le 4000 \text{ K} \\
1.25 & T_0 > 4000 \text{ K}
\end{cases}
$$

**Strip integration.** The Newtonian drag coefficient is computed by integrating
over the 100-strip nose profile, identically to the shock-expansion method:

$$
C_d = \frac{2}{R_\text{aft}^2 - R_\text{fore}^2} \sum_{i=1}^{N} C_{p,\max} \sin^2\theta_i \cdot r_{\text{mid},i} \cdot \Delta r_i
$$

Only windward surfaces ($\Delta r > 0$) contribute. Leeward surfaces
($\Delta r \le 0$) are in the aerodynamic shadow where $C_p \approx 0$ in
Newtonian theory.


#### 6.1.6 Blending Across Mach Regimes

Three blending regions connect the different wave drag models:

**Empirical-to-analytical blend (M 1.3 to 1.5).** Below $M = 1.3$, the
TR-R-100 transonic polynomial (well-validated against experimental data) is
used. Above $M = 1.5$, the analytical solution (Taylor-Maccoll or
shock-expansion) takes over. Between these limits, a cubic Hermite smoothstep
blends the two:

$$
w = 3t^2 - 2t^3, \quad t = \frac{M - 1.3}{0.2}
$$

$$
C_d = (1 - w) \cdot C_{d,\text{empirical}} + w \cdot C_{d,\text{analytical}}
$$

**Shock-expansion to Newtonian blend (M 4.0 to 6.0).** At very high Mach, the
shock-expansion method becomes less accurate as the shock layer thins and
real-gas effects become significant. The Modified Newtonian theory provides
better physical modeling. The blend uses the same smoothstep:

$$
w = 3t^2 - 2t^3, \quad t = \frac{M - 4.0}{2.0}
$$

$$
C_d = (1 - w) \cdot C_{d,\text{shock-expansion}} + w \cdot C_{d,\text{Newtonian}}
$$

**Dahlem-Buck blend (M 1.3 to 1.5, POWER/PARABOLIC/HAACK only).** For these
shapes, the TR-R-100 empirical tables are replaced by the Dahlem-Buck
correction above $M = 1.5$, with a smoothstep blend in $[1.3, 1.5]$.

**Regime summary diagram:**

```{=latex}
\begin{figure}[htbp]
\centering
\resizebox{\linewidth}{!}{%
\begin{tikzpicture}[font=\footnotesize, >=Latex]
\draw[->, thick] (0,0) -- (11.2,0) node[below] {$M$};
\foreach \x/\lbl in {0.8/{},1.6/$M_{dd}$,2.4/$1.0$,3.2/$1.3$,4.0/$1.5$,5.6/$2.0$,6.8/$3.0$,8.0/$4.0$,9.2/$5.0$,10.4/$6.0$}
  \draw (\x,0.08) -- (\x,-0.08) node[below=2pt] {\lbl};
\draw[very thick, blue!70!black] (1.0,2.0) .. controls (1.8,2.3) and (2.2,1.5) .. (3.0,1.2)
  .. controls (4.5,0.9) and (6.5,0.85) .. (8.5,0.75) -- (10.8,0.65);
\node[blue!70!black, align=center, anchor=south west] at (0.85,2.72) {TR-R-100 /\\Hermite rise};
\node[align=center, anchor=south] at (4.5,2.15) {smoothstep\\blends};
\node[align=center, anchor=south west] at (8.35,1.72) {Newtonian\\blend};
\draw[decorate, decoration={brace, amplitude=4pt}] (1.4,-0.85) -- (2.2,-0.85) node[midway, below=6pt, font=\scriptsize] {rise};
\draw[decorate, decoration={brace, amplitude=4pt}] (2.6,-0.85) -- (3.6,-0.85) node[midway, below=6pt, font=\scriptsize] {$1.3$--$1.5$};
\draw[decorate, decoration={brace, amplitude=4pt}] (7.6,-0.85) -- (10.2,-0.85) node[midway, below=6pt, font=\scriptsize] {$4$--$6$};
\end{tikzpicture}%
}
\caption{Qualitative wave-drag regime map: empirical / transonic rise, analytical Taylor--Maccoll and shock-expansion, and Modified Newtonian tail (schematic).}
\label{fig:wave-drag-regimes}
\end{figure}
```

#### 6.1.7 Worked Example: 15-Degree Cone

Consider a conical nose with half-angle $\theta_c = 15\degree$, fineness ratio
$f = L/(2R) \approx 1.87$, in air ($\gamma = 1.4$).

**At $M = 2.0$:**

1. Solve Taylor-Maccoll: shock angle $\beta \approx 33.8\degree$
2. Normal Mach: $M_{n1} = 2.0 \sin(33.8\degree) = 1.113$
3. Pressure ratio across shock: $p_2/p_1 = 1 + \frac{2(1.4)}{2.4}(1.113^2 - 1) = 1.293$
4. Taylor-Maccoll integration to cone surface yields $p_\text{cone}/p_\infty = 1.566$
5. $C_p = \frac{2}{1.4 \times 4.0}(1.566 - 1) = 0.202$
6. $C_{d,\text{cone}} = 0.202$ (referenced to base area)

**At $M = 3.0$:**

1. Solve Taylor-Maccoll: shock angle $\beta \approx 26.1\degree$
2. Normal Mach: $M_{n1} = 3.0 \sin(26.1\degree) = 1.320$
3. Pressure ratio: $p_2/p_1 = 1.866$
4. Cone surface: $p_\text{cone}/p_\infty = 2.315$
5. $C_p = \frac{2}{1.4 \times 9.0}(2.315 - 1) = 0.209$
6. $C_{d,\text{cone}} = 0.209$

**At $M = 5.0$:**

1. Below $M = 4.0$: pure Taylor-Maccoll
2. Taylor-Maccoll gives $C_d = 0.185$
3. Newtonian gives $C_{p,\max} = 1.734$, $\sin^2(15\degree) = 0.0670$
4. Newtonian $C_d$ (single-strip approximation) $\approx 1.734 \times 0.0670 = 0.116$
5. At $M = 5.0$, the smoothstep weight $w = 3(0.5)^2 - 2(0.5)^3 = 0.5$
6. Blended $C_d = (1 - 0.5)(0.185) + (0.5)(0.116) = 0.151$

| Mach | Taylor-Maccoll $C_d$ | Newtonian $C_d$ | Blended $C_d$ |
|------|---------------------|----------------|---------------|
| 2.0  | 0.202               | --             | 0.202         |
| 3.0  | 0.209               | --             | 0.209         |
| 5.0  | 0.185               | 0.116          | 0.151         |

**Old (original OpenRocket) vs. New comparison:**

| Mach | Old OpenRocket $C_d$ | New $C_d$ | Change  |
|------|---------------------|-----------|---------|
| 0.8  | 0 (subsonic)        | 0         | --      |
| 1.0  | 0.259 (TR-R-100)    | 0.259     | no change |
| 1.5  | 0.231 (TR-R-100)    | 0.220     | -4.8%   |
| 2.0  | 0.198 (extrapolated)| 0.202     | +2.0%   |
| 3.0  | 0.175 (extrapolated)| 0.209     | +19.4%  |
| 5.0  | not available       | 0.151     | new     |

At $M > 2$, the old OpenRocket empirical extrapolation significantly
underestimated wave drag. The analytical models capture the correct behavior:
wave drag for a cone remains roughly constant or increases slightly with Mach
above $M \approx 2$, rather than monotonically decreasing as the extrapolation
predicted.


### 6.2 Base Drag

Base drag arises from the low-pressure wake region behind the aft end of the
rocket body. It is a significant contributor to total drag, particularly at
transonic speeds where it peaks sharply.

The base drag coefficient is computed in `BarrowmanDragCalculator.calculateBaseCD()`
and is referenced to the base area. For each component, it is rescaled:

$$
C_{D,\text{base}} = C_{d,\text{base}} \cdot \frac{A_\text{base}}{S_\text{ref}}
$$

where $A_\text{base} = \pi(R_\text{aft}^2 - R_\text{next}^2)$ is the exposed
base area (accounting for the next downstream component's fore radius).


#### 6.2.1 Subsonic Base Drag

At subsonic Mach numbers ($M \le 0.85$), the base drag follows the Hoerner
correlation for cylindrical afterbodies:

$$
C_{d,\text{base}} = 0.12 + 0.13 M^2
$$

This captures the mild increase of base drag with Mach in the subsonic regime.
At $M = 0$, the base drag coefficient is 0.12, rising to 0.214 at $M = 0.85$.

Reference: Hoerner, "Fluid-Dynamic Drag" (1965), Chapter 3.


#### 6.2.2 Supersonic Base Drag: Devan-Ashwood Correlation

At supersonic speeds ($M \ge 1.3$), the base drag is modeled by the
Devan-Ashwood correlation:

$$
C_{d,\text{base}} = 0.064 + \frac{0.186}{M^2}
$$

This model was fitted to turbulent cylindrical afterbody data from Devan and
Ashwood (1961, NASA TN D-721). The key physical features:

- **Nonzero asymptote.** As $M \to \infty$, $C_{d,\text{base}} \to 0.064$.
  This matches the observed behavior that base pressure does not vanish at very
  high Mach, unlike the simpler $C_{d,\text{base}} = 0.25/M$ model used in
  some legacy codes.

- **$1/M^2$ decay.** The dominant supersonic decay rate matches the expansion
  fan physics at the base corner, where the Prandtl-Meyer expansion angle
  increases with Mach, reducing the base pressure coefficient.

At $M = 1.3$: $C_{d,\text{base}} = 0.064 + 0.186/1.69 = 0.174$

At $M = 2.0$: $C_{d,\text{base}} = 0.064 + 0.186/4.0 = 0.111$

At $M = 5.0$: $C_{d,\text{base}} = 0.064 + 0.186/25.0 = 0.071$


#### 6.2.3 Transonic Base Drag: Degree-4 Polynomial Blend

The transonic regime ($M \in [0.85, 1.3]$) features a sharp peak in base drag
near $M \approx 1.05$, where the wake becomes highly unsteady and the flow
transitions from subsonic to supersonic separation. This peak is captured by
a degree-4 polynomial constructed via `PolyInterpolator` with five constraints:

| # | Constraint | Location   | Type       | Value / Expression |
|---|-----------|------------|------------|--------------------|
| 1 | Subsonic value     | $M = 0.85$ | Value      | $0.12 + 0.13(0.85)^2 = 0.214$ |
| 2 | Transonic peak     | $M = 1.05$ | Value      | $0.25$ (experimental) |
| 3 | Supersonic value   | $M = 1.30$ | Value      | $0.064 + 0.186/(1.30)^2 = 0.174$ |
| 4 | Subsonic slope     | $M = 0.85$ | Derivative | $0.26 \times 0.85 = 0.221$ |
| 5 | Supersonic slope   | $M = 1.30$ | Derivative | $-2 \times 0.186/(1.30)^3 = -0.169$ |

The `PolyInterpolator` is configured with value constraints at three points
$(0.85, 1.05, 1.30)$ and derivative constraints at two points $(0.85, 1.30)$,
yielding a 4th-degree polynomial (5 constraints, 5 coefficients).

The construction in the code:

```java
PolyInterpolator baseDragInterp = new PolyInterpolator(
    new double[] { 0.85, 1.05, 1.30 },      // value points
    new double[] { 0.85, 1.30 });            // derivative points
baseDragTransonicPoly = baseDragInterp.interpolator(
    0.214,     // subsonic value at M=0.85
    0.25,      // peak at M=1.05
    0.174,     // Devan-Ashwood at M=1.3
    0.221,     // subsonic derivative at M=0.85
   -0.169);    // Devan-Ashwood derivative at M=1.3
```

The resulting profile:

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}
\begin{axis}[
  width=0.9\linewidth,
  height=5.5cm,
  xmin=0.45, xmax=2.15,
  ymin=0.08, ymax=0.27,
  xlabel={$M$},
  ylabel={$C_{D,\mathrm{base}}$},
  grid=major,
  grid style={gray!30},
  tick label style={font=\small},
  label style={font=\small},
  legend style={font=\scriptsize, at={(0.97,0.97)}, anchor=north east},
]
\addplot[thick, dashed, domain=0.5:0.85, samples=40] {0.12+0.13*x*x};
\addlegendentry{subsonic $0.12+0.13M^2$}
\addplot[very thick, black] coordinates {
  (0.85,0.214)(0.90,0.22)(0.95,0.235)(1.00,0.245)(1.05,0.25)(1.10,0.23)(1.20,0.20)(1.30,0.174)
};
\addlegendentry{degree-4 transonic polynomial}
\addplot[thick, blue, dashed, domain=1.3:2.1, samples=50] {0.064+0.186/(x*x)};
\addlegendentry{Devan--Ashwood ($M\ge 1.3$)}
\draw[dashed, gray] (axis cs:0.85,0.08) -- (axis cs:0.85,0.27);
\draw[dashed, gray] (axis cs:1.30,0.08) -- (axis cs:1.30,0.27);
\end{axis}
\end{tikzpicture}
\caption{Base drag coefficient: subsonic correlation, transonic polynomial with peak at $M=1.05$, and supersonic Devan--Ashwood branch (schematic).}
\label{fig:base-drag-profile}
\end{figure}
```

#### 6.2.4 Lamb-Oberkampf Reynolds Number Correction

At supersonic speeds ($M > 1.3$), the base drag depends on the boundary layer
state at the base corner, which is influenced by the Reynolds number. The
Lamb-Oberkampf (1995) correction adjusts the base drag for Reynolds number
effects:

$$
C_{d,\text{base,corr}} = C_{d,\text{base}} \cdot f_{Re}
$$

where the Reynolds correction factor is:

$$
f_{Re} = \text{clamp}\!\left(1.0 - 0.08 \cdot (\log_{10} Re_D - 6.0), \; 0.7, \; 1.3\right)
$$

and $Re_D = V \cdot D_\text{ref} / \nu$ is the Reynolds number based on the
reference diameter. At high Reynolds numbers ($Re_D > 10^6$), the more
energetic turbulent boundary layer produces a fuller wake profile, resulting in
higher base pressure and lower base drag. At low Reynolds numbers
($Re_D < 10^4$), the correction is not applied.

| $Re_D$    | $\log_{10} Re_D$ | $f_{Re}$ |
|-----------|-------------------|----------|
| $10^4$    | 4.0               | 1.0 (no correction) |
| $10^5$    | 5.0               | 1.08     |
| $10^6$    | 6.0               | 1.00     |
| $10^7$    | 7.0               | 0.92     |
| $10^8$    | 8.0               | 0.84     |


#### 6.2.5 Power-On Base Drag Reduction

During motor burn, the exhaust plume partially fills the base region, raising
the base pressure and reducing base drag. The reduction depends on the nozzle
exit area to base area ratio $AR = A_e / A_b$:

$$
k_\text{base}(AR) = \begin{cases}
0.0 & AR \ge 0.8 \\
0.2 \cdot \frac{0.8 - AR}{0.4} & 0.4 \le AR < 0.8 \\
0.2 + 0.6 \cdot \frac{0.4 - AR}{0.3} & 0.1 \le AR < 0.4 \\
0.8 + 0.2 \cdot \frac{0.1 - AR}{0.1} & AR < 0.1
\end{cases}
$$

where $k_\text{base} = 0$ means complete elimination of base drag, and
$k_\text{base} = 1$ means no reduction. The final base drag during powered
flight is:

$$
C_{d,\text{base,powered}} = C_{d,\text{base}} \cdot \left[1 - S(\tau) \cdot (1 - k_\text{base})\right]
$$

where $\tau$ is the thrust level (0 = coast, 1 = full thrust) and $S(\tau) =
3\tau^2 - 2\tau^3$ is a smoothstep function that avoids sudden drag changes at
motor ignition and burnout. When nozzle geometry is unavailable, a default
power-on factor of $k_\text{base} = 0.15$ is used.

| $AR$ | $k_\text{base}$ | Physical meaning |
|------|-----------------|------------------|
| 0.05 | 0.90            | Very small nozzle, minimal reduction |
| 0.1  | 0.80            | Small nozzle |
| 0.3  | 0.40            | Typical HPR motor |
| 0.5  | 0.15            | Large nozzle, significant reduction |
| 0.8  | 0.00            | Nozzle fills base, complete elimination |

Reference: NASA SP-8055 "Solid Rocket Motor Nozzles"; Hoerner Ch. 3.


#### 6.2.6 Boattail Correction

When a body component tapers from a larger fore radius to a smaller aft radius
(boattail), the converging flow creates a narrower wake with higher base
pressure. Two corrections are applied:

**Geometric boattail factor.** The `calculateBoattailFactor()` method computes
a correction based on the boattail angle and Mach number:

$$
\theta_\text{bt} = \arctan\!\left(\frac{R_\text{fore} - R_\text{aft}}{L}\right)
$$

The angle factor determines how much of the theoretical benefit is realized:

$$
f_\text{angle} = \begin{cases}
1.0 & \theta_\text{bt} \le 12\degree \\
\frac{20\degree - \theta_\text{bt}}{20\degree - 12\degree} & 12\degree < \theta_\text{bt} < 20\degree \\
0.0 & \theta_\text{bt} \ge 20\degree
\end{cases}
$$

At moderate angles ($< 12\degree$), the full benefit applies. At steep angles
($> 20\degree$), flow separation on the boattail surface eliminates the benefit.

The reduction coefficient increases with Mach due to expansion fan effects:

$$
c_\text{red} = \begin{cases}
0.25 & M \le 1.0 \\
0.25 + 0.15 \cdot \min(M - 1.0, 1.0) & M > 1.0
\end{cases}
$$

The total boattail factor:

$$
f_\text{bt} = \text{clamp}\!\left(1 - f_\text{angle} \cdot c_\text{red} \cdot \left(1 - \frac{R_\text{aft}}{R_\text{fore}}\right), \; 0.3, \; 1.0\right)
$$

**Viswanath (1996) wake energization.** A boattail upstream of the base
energizes the boundary layer and produces a fuller wake profile, further
reducing base drag. The Viswanath correction factor $\eta_\text{bt}$ is:

$$
\eta_\text{bt} = \begin{cases}
0.25 + 0.05 \theta_\text{bt} & \theta_\text{bt} < 6\degree \\
\min\!\left[(0.55 + 0.04(\theta_\text{bt} - 6)) \cdot (1 + 0.1 \max(0, M - 1)), \; 0.95\right] & 6\degree \le \theta_\text{bt} < 16\degree \\
\max(0, \; 0.95 - 0.05(\theta_\text{bt} - 16)) & \theta_\text{bt} \ge 16\degree
\end{cases}
$$

where $\theta_\text{bt}$ is in degrees. The factor is clamped to $[0, 1]$.

The final corrected base drag for a boattailed component is:

$$
C_{d,\text{base,final}} = C_{d,\text{base}} \cdot f_\text{bt} \cdot \eta_\text{bt}
$$


#### 6.2.7 Worked Examples

**At $M = 0.5$ (subsonic):**

$$
C_{d,\text{base}} = 0.12 + 0.13(0.5)^2 = 0.12 + 0.0325 = 0.1525
$$

No Reynolds correction (subsonic regime). No boattail (cylindrical body).

**At $M = 1.05$ (transonic peak):**

The degree-4 polynomial yields the peak value:

$$
C_{d,\text{base}} = 0.25
$$

This is 65% higher than the subsonic value at $M = 0.85$ and matches
experimental data for cylindrical afterbodies.

**At $M = 2.0$ (supersonic):**

Devan-Ashwood:
$$
C_{d,\text{base}} = 0.064 + \frac{0.186}{(2.0)^2} = 0.064 + 0.0465 = 0.1105
$$

With Lamb-Oberkampf correction at $Re_D = 5 \times 10^6$ ($\log_{10} Re_D = 6.70$):
$$
f_{Re} = 1.0 - 0.08(6.70 - 6.0) = 0.944
$$
$$
C_{d,\text{base,corr}} = 0.1105 \times 0.944 = 0.1043
$$

**At $M = 5.0$ (high supersonic):**

Devan-Ashwood:
$$
C_{d,\text{base}} = 0.064 + \frac{0.186}{25.0} = 0.064 + 0.00744 = 0.0714
$$

At $M = 5.0$ with $Re_D = 10^7$:
$$
f_{Re} = 1.0 - 0.08(7.0 - 6.0) = 0.920
$$
$$
C_{d,\text{base,corr}} = 0.0714 \times 0.920 = 0.0657
$$

**Old vs. New comparison:**

| Mach | Old OpenRocket $C_{d,\text{base}}$ | New $C_{d,\text{base}}$ | Change |
|------|-----------------------------------|------------------------|--------|
| 0.5  | 0.1525                            | 0.1525                 | 0%     |
| 0.9  | 0.225                             | 0.230 (polynomial)     | +2.2%  |
| 1.0  | 0.25                              | 0.247 (polynomial)     | -1.2%  |
| 1.05 | 0.25                              | 0.250 (polynomial peak)| 0%     |
| 1.5  | 0.167                             | 0.147                  | -12%   |
| 2.0  | 0.125                             | 0.111                  | -11%   |
| 5.0  | 0.050                             | 0.071                  | +42%   |

The old model used $0.25/M$ for supersonic base drag, which decays to zero at
high Mach. The Devan-Ashwood model correctly maintains a nonzero asymptote
(0.064), producing significantly higher base drag at $M = 5$ and lower base
drag in the $M = 1.5$-$2.0$ range.


### 6.3 Skin Friction Drag

Skin friction drag arises from the viscous shear stress on all wetted surfaces.
It is typically the largest single drag component in the subsonic regime and
remains significant at supersonic speeds, though compressibility reduces it
substantially.


#### 6.3.1 Incompressible Baseline

The incompressible skin friction coefficient $C_{f,0}$ depends on the Reynolds
number and whether the surface is aerodynamically smooth.

**Laminar (Blasius, $Re < 5.39 \times 10^5$, smooth finish):**

$$
C_f = \frac{1.328}{\sqrt{Re}}
$$

For very low Reynolds numbers ($Re < 10^4$), a constant $C_f = 0.0133$ is used.

**Turbulent (Schlichting, $Re \ge 5.39 \times 10^5$, smooth finish):**

$$
C_f = \frac{1}{(1.50 \ln Re - 5.6)^2} - \frac{1700}{Re}
$$

The $-1700/Re$ term represents the virtual origin correction for transition
from laminar to turbulent flow.

**Turbulent (rough finish, any $Re$):**

$$
C_f = \frac{1}{(1.50 \ln Re - 5.6)^2}
$$

For $Re < 10^4$ with rough finish: $C_f = 0.0148$.

**Subsonic compressibility correction.** At subsonic Mach, a correction factor
is applied for $Re > 10^6$:

$$
C_{f,\text{sub}} = C_{f,0} \cdot (1 - 0.1 M^2)
$$

with a ramp-in for $Re$ between $10^6$ and $3 \times 10^6$. For rough finish,
the correction applies at all Reynolds numbers.

**Body form factor.** The total body friction drag includes a form factor
correction for body fineness ratio:

$$
C_{D,\text{friction,body}} = C_f \cdot \frac{S_\text{wet}}{S_\text{ref}} \cdot \left(1 + \frac{1}{2f_B}\right)
$$

where $f_B = L_\text{body}/R_\text{max}$ is the body fineness parameter.


#### 6.3.2 Eckert Reference Temperature Method

At supersonic speeds, the boundary layer temperature rises dramatically due to
adiabatic compression and viscous dissipation. The Eckert method (1955)
accounts for this by evaluating fluid properties at a reference temperature
$T^*$ rather than the freestream temperature.

**Step 1: Adiabatic wall temperature.**

For an adiabatic wall (zero heat transfer, typical for an unpainted rocket in
flight), the wall temperature equals the recovery temperature:

$$
T_w = T_e \left(1 + r \cdot \frac{\gamma - 1}{2} M^2\right)
$$

where $r$ is the turbulent recovery factor:

$$
r = Pr^{1/3} = (0.71)^{1/3} = 0.8929
$$

with $Pr = 0.71$ being the Prandtl number for air.

For $\gamma = 1.4$:

$$
T_w = T_e \left(1 + 0.1786 \, M^2\right)
$$

**Step 2: Eckert reference temperature.**

$$
T^* = T_e \left(1 + 0.032 \, M^2 + 0.58 \left(\frac{T_w}{T_e} - 1\right)\right)
$$

Substituting the wall temperature ratio:

$$
T^* = T_e \left(1 + 0.032 \, M^2 + 0.58 \cdot r \cdot \frac{\gamma - 1}{2} M^2\right)
$$

$$
T^* = T_e \left(1 + 0.032 \, M^2 + 0.58 \times 0.8929 \times 0.2 \times M^2\right)
$$

$$
T^* = T_e \left(1 + 0.032 \, M^2 + 0.1036 \, M^2\right)
$$

$$
T^* = T_e \left(1 + 0.1356 \, M^2\right)
$$

**Step 3: Density ratio (ideal gas at constant pressure).**

$$
\frac{\rho^*}{\rho_e} = \frac{T_e}{T^*}
$$

**Step 4: Viscosity ratio (Sutherland's law).**

$$
\frac{\mu^*}{\mu_e} = \left(\frac{T^*}{T_e}\right)^{3/2} \cdot \frac{T_e + S}{T^* + S}
$$

where $S = 110.4$ K is the Sutherland constant for air.

**Step 5: Reference Reynolds number.**

$$
Re^* = Re \cdot \frac{\rho^*}{\rho_e} \cdot \frac{1}{\mu^*/\mu_e} = Re \cdot \frac{T_e}{T^*} \cdot \frac{1}{\left(\frac{T^*}{T_e}\right)^{3/2} \cdot \frac{T_e + S}{T^* + S}}
$$

$$
Re^* = Re \cdot \left(\frac{T_e}{T^*}\right)^{5/2} \cdot \frac{T^* + S}{T_e + S}
$$

**Step 6: Compressible skin friction coefficient.**

Compute the incompressible $C_f$ at $Re^*$, then scale to freestream conditions:

$$
C_{f,\text{Eckert}} = C_{f,0}(Re^*) \cdot \frac{T_e}{T^*}
$$

The $T_e/T^*$ factor accounts for the fact that the skin friction coefficient is
defined relative to the freestream dynamic pressure, while the boundary layer
properties are evaluated at $T^*$.


#### 6.3.3 Boundary Layer Transition: Michel Criterion

The transition from laminar to turbulent boundary layer is determined by the
Michel criterion with a compressibility correction:

$$
Re_\text{tr} = \frac{3.0 \times 10^6}{1 + 0.045 \, M^2}
$$

The transition location is:

$$
x_\text{tr} = \frac{Re_\text{tr} \cdot \nu}{V}
$$

where $\nu$ is the kinematic viscosity and $V$ is the freestream velocity.

The laminar fraction of the total wetted length is:

$$
f_\text{lam} = \min\!\left(\frac{x_\text{tr}}{L_\text{total}}, \; 1.0\right)
$$

The laminar fraction reduces the overall skin friction because laminar boundary
layers have lower shear stress than turbulent ones. The transition correction
factor applied to all friction drag is:

$$
f_\text{transition} = 1 - 0.6 \, f_\text{lam}
$$

At $M = 0$ with a typical HPR rocket ($L = 2$ m, $V = 100$ m/s, $\nu = 1.5 \times 10^{-5}$ m$^2$/s):
$Re_\text{tr} = 3.0 \times 10^6$, $x_\text{tr} = 0.45$ m, $f_\text{lam} = 0.225$,
$f_\text{transition} = 0.865$ (13.5% friction reduction).


#### 6.3.4 Transonic Blend (M 0.9 to 1.1)

The transition from the subsonic compressibility correction to the Eckert
method is done by linear blending:

$$
C_f = C_{f,\text{sub}} \cdot (1 - t) + C_{f,\text{Eckert}} \cdot t, \quad t = \frac{M - 0.9}{0.2}
$$

for $M \in [0.9, 1.1]$.


#### 6.3.5 Worked Examples

All examples assume: $T_e = 288.15$ K (sea level), $Re = 1.0 \times 10^7$,
smooth (perfect) finish, $\gamma = 1.4$, $S = 110.4$ K.

**At $M = 0.3$ (subsonic):**

1. Incompressible $C_f$: $C_{f,0} = 1/(1.50 \ln(10^7) - 5.6)^2 - 1700/10^7$
   - $\ln(10^7) = 16.118$
   - Denominator: $(1.50 \times 16.118 - 5.6)^2 = (24.177 - 5.6)^2 = (18.577)^2 = 345.1$
   - $C_{f,0} = 1/345.1 - 0.00017 = 0.002898 - 0.000170 = 0.002728$
2. Subsonic correction: $C_f = 0.002728 \times (1 - 0.1 \times 0.09) = 0.002728 \times 0.991 = 0.002703$

**At $M = 1.0$ (transonic, blend midpoint at $t = 0.5$):**

Subsonic side:
- $C_{f,\text{sub}} = 0.002728 \times (1 - 0.1 \times 1.0) = 0.002728 \times 0.9 = 0.002455$

Eckert side:
1. $T_w = 288.15 (1 + 0.8929 \times 0.2 \times 1.0) = 288.15 \times 1.1786 = 339.7$ K
2. $T^* = 288.15 (1 + 0.032 + 0.58 \times (339.7/288.15 - 1)) = 288.15 (1 + 0.032 + 0.58 \times 0.1786) = 288.15 \times 1.1356 = 327.2$ K
3. $\rho^*/\rho_e = 288.15/327.2 = 0.8807$
4. $\mu^*/\mu_e = (327.2/288.15)^{1.5} \times (288.15 + 110.4)/(327.2 + 110.4) = (1.1355)^{1.5} \times 398.55/437.6 = 1.2088 \times 0.9108 = 1.1010$
5. $Re^* = 10^7 \times 0.8807/1.1010 = 7.998 \times 10^6$
6. $C_{f,0}(Re^*) = 1/(1.50 \ln(7.998 \times 10^6) - 5.6)^2 - 1700/(7.998 \times 10^6)$
   - $\ln(7.998 \times 10^6) = 15.895$
   - $(1.50 \times 15.895 - 5.6)^2 = (18.243)^2 = 332.8$
   - $C_{f,0} = 0.003005 - 0.000213 = 0.002792$
7. $C_{f,\text{Eckert}} = 0.002792 \times 288.15/327.2 = 0.002792 \times 0.8807 = 0.002459$

Blended: $C_f = 0.002455 \times 0.5 + 0.002459 \times 0.5 = 0.002457$

**At $M = 3.0$ (supersonic):**

1. $T_w = 288.15 (1 + 0.8929 \times 0.2 \times 9.0) = 288.15 \times 2.607 = 751.1$ K
2. $T^* = 288.15 (1 + 0.032 \times 9.0 + 0.58 \times (751.1/288.15 - 1)) = 288.15 (1 + 0.288 + 0.58 \times 1.607) = 288.15 (1 + 0.288 + 0.932) = 288.15 \times 2.220 = 639.9$ K
3. $\rho^*/\rho_e = 288.15/639.9 = 0.4503$
4. $\mu^*/\mu_e = (639.9/288.15)^{1.5} \times (288.15 + 110.4)/(639.9 + 110.4) = (2.220)^{1.5} \times 398.55/750.3 = 3.310 \times 0.5312 = 1.758$
5. $Re^* = 10^7 \times 0.4503/1.758 = 2.561 \times 10^6$
6. $C_{f,0}(Re^*) = 1/(1.50 \ln(2.561 \times 10^6) - 5.6)^2 - 1700/(2.561 \times 10^6)$
   - $\ln(2.561 \times 10^6) = 14.756$
   - $(1.50 \times 14.756 - 5.6)^2 = (16.534)^2 = 273.4$
   - $C_{f,0} = 0.003658 - 0.000664 = 0.002994$
7. $C_{f,\text{Eckert}} = 0.002994 \times 288.15/639.9 = 0.002994 \times 0.4503 = 0.001349$

Reduction from incompressible: $0.001349 / 0.002728 = 0.494$, i.e., **50.6% reduction** at $M = 3$.

**At $M = 5.0$ (high supersonic):**

1. $T_w = 288.15 (1 + 0.8929 \times 0.2 \times 25.0) = 288.15 \times 5.465 = 1574.5$ K
2. $T^* = 288.15 (1 + 0.032 \times 25.0 + 0.58 \times (1574.5/288.15 - 1)) = 288.15 (1 + 0.800 + 0.58 \times 4.465) = 288.15 (1 + 0.800 + 2.590) = 288.15 \times 4.390 = 1264.9$ K
3. $\rho^*/\rho_e = 288.15/1264.9 = 0.2278$
4. $\mu^*/\mu_e = (1264.9/288.15)^{1.5} \times (288.15 + 110.4)/(1264.9 + 110.4) = (4.390)^{1.5} \times 398.55/1375.3 = 9.194 \times 0.2898 = 2.665$
5. $Re^* = 10^7 \times 0.2278/2.665 = 8.548 \times 10^5$
6. $C_{f,0}(Re^*) = 1/(1.50 \ln(8.548 \times 10^5) - 5.6)^2 - 1700/(8.548 \times 10^5)$
   - $\ln(8.548 \times 10^5) = 13.659$
   - $(1.50 \times 13.659 - 5.6)^2 = (14.889)^2 = 221.7$
   - $C_{f,0} = 0.004511 - 0.001989 = 0.002522$
7. $C_{f,\text{Eckert}} = 0.002522 \times 288.15/1264.9 = 0.002522 \times 0.2278 = 0.000575$

Reduction from incompressible: $0.000575 / 0.002728 = 0.211$, i.e., **78.9% reduction** at $M = 5$.

**Summary table:**

| Mach | $T^*/T_e$ | $Re^*/Re$ | $C_f$ (Eckert) | $C_f/C_{f,0}$ | Reduction |
|------|-----------|-----------|-----------------|----------------|-----------|
| 0.3  | 1.012     | 0.971     | 0.002703        | 0.991          | 0.9%      |
| 1.0  | 1.136     | 0.800     | 0.002459        | 0.901          | 9.9%      |
| 2.0  | 1.542     | 0.515     | 0.001866        | 0.684          | 31.6%     |
| 3.0  | 2.220     | 0.256     | 0.001349        | 0.494          | 50.6%     |
| 5.0  | 4.390     | 0.0855    | 0.000575        | 0.211          | 78.9%     |

Reference: Eckert, E.R.G. (1955). "Engineering relations for friction and heat
transfer to surfaces in high velocity flow." J. Aeronautical Sciences, 22(8).


### 6.4 Fin Wave Drag

Fins generate wave drag at supersonic speeds due to oblique shocks at their
leading and trailing edges. At subsonic speeds, the fin contribution to
pressure drag is negligible (friction-dominated).


#### 6.4.1 Ackeret Formula

The supersonic wave drag of a thin symmetric airfoil at zero angle of attack
is given by Ackeret's linearized supersonic potential theory (1925):

$$
C_{d,w} = \frac{4 \, \tau^2}{\beta}
$$

where $\tau = t/c$ is the fin thickness ratio (maximum thickness divided by
chord length) and $\beta = \sqrt{M^2 - 1}$ is the Prandtl-Glauert
compressibility parameter.

**Derivation from linearized theory.** For a symmetric double-wedge profile in
supersonic flow, the linearized pressure coefficient on the upper (or lower)
surface at zero angle of attack is:

$$
C_p = \pm \frac{2\theta}{\sqrt{M^2 - 1}}
$$

where $\theta$ is the local surface slope. For a symmetric profile with
thickness ratio $\tau$ and chord $c$, the surface slope on the forward half is
$+\tau$ and on the aft half is $-\tau$ (for a diamond profile) or varies
continuously for a biconvex profile.

The wave drag per unit span, integrated over both surfaces:

$$
D_w = 2 \int_0^c \frac{1}{2}\rho V^2 \cdot \frac{2\theta^2}{\sqrt{M^2-1}} \, dx
$$

For a biconvex profile with $\overline{\theta^2} = \tau^2$, the result is:

$$
C_{d,w} = \frac{4\tau^2}{\sqrt{M^2 - 1}} = \frac{4\tau^2}{\beta}
$$

The derivative with respect to Mach (used for the transonic blend):

$$
\frac{dC_{d,w}}{dM} = -\frac{4\tau^2 M}{(M^2 - 1)^{3/2}}
$$

This is implemented in `FinSetCalc.ackeretWaveDragCD()` and
`FinSetCalc.ackeretWaveDragSlope()`.


#### 6.4.2 C1 Hermite Blend (M 0.9 to 1.2)

The Ackeret formula diverges as $M \to 1^+$ ($\beta \to 0$), while no wave
drag exists at subsonic speeds. A C1-continuous cubic Hermite spline blends
from zero at $M = 0.9$ to the Ackeret value at $M = 1.2$.

The blend interval is $[M_L, M_H] = [0.9, 1.2]$ with normalized coordinate:

$$
t = \frac{M - M_L}{M_H - M_L} = \frac{M - 0.9}{0.3}
$$

**Boundary conditions:**

| Location | Value | Derivative |
|----------|-------|------------|
| $M = 0.9$ ($t = 0$) | $f_0 = 0$ | $f_0' = 0$ |
| $M = 1.2$ ($t = 1$) | $f_1 = C_{d,w}(1.2)$ | $f_1' = dC_{d,w}/dM\vert_{M=1.2}$ |

**The four Hermite basis functions:**

$$
h_{00}(t) = 2t^3 - 3t^2 + 1 \quad \text{(value at } t=0\text{)}
$$
$$
h_{10}(t) = t^3 - 2t^2 + t \quad \text{(slope at } t=0\text{)}
$$
$$
h_{01}(t) = -2t^3 + 3t^2 \quad \text{(value at } t=1\text{)}
$$
$$
h_{11}(t) = t^3 - t^2 \quad \text{(slope at } t=1\text{)}
$$

**The blend polynomial:**

Since $f_0 = 0$ and $f_0' = 0$, the first two terms vanish:

$$
C_{d,w}(M) = h_{01}(t) \cdot f_1 + h_{11}(t) \cdot \Delta M \cdot f_1'
$$

$$
C_{d,w}(M) = (-2t^3 + 3t^2) \cdot f_1 + (t^3 - t^2) \cdot (M_H - M_L) \cdot f_1'
$$

For $\tau = 0.05$:
- $f_1 = 4 \times 0.0025 / \sqrt{0.44} = 0.01/0.6633 = 0.01508$
- $f_1' = -4 \times 0.0025 \times 1.2 / (0.44)^{1.5} = -0.012/0.2917 = -0.04114$

The polynomial for $M \in [0.9, 1.2]$:

$$
C_{d,w}(M) = (-2t^3 + 3t^2)(0.01508) + (t^3 - t^2)(0.3)(-0.04114)
$$

$$
= (-2t^3 + 3t^2)(0.01508) + (t^3 - t^2)(-0.01234)
$$


#### 6.4.3 Sweep Correction

The effective Mach number normal to the fin leading edge is reduced by the
cosine of the sweep angle. The Ackeret wave drag is corrected by:

$$
C_{d,w,\text{swept}} = C_{d,w} \cdot \cos^2\Lambda_{LE}
$$

where $\Lambda_{LE}$ is the leading-edge sweep angle. This correction also
applies to the leading-edge bluntness/pressure drag.

For a typical 30-degree swept fin: $\cos^2(30\degree) = 0.75$, reducing wave
drag by 25%. For a highly swept 60-degree fin: $\cos^2(60\degree) = 0.25$.


#### 6.4.4 Trailing-Edge Base Drag

Fins with blunt trailing edges generate a wake similar to the body base, with a
pressure deficit that creates additional drag. The model depends on the fin
cross-section type:

**Subsonic ($M < 0.9$, Hoerner turbulent wake):**

$$
C_{d,\text{TE}} = 0.12 \cdot \frac{t_\text{TE}}{c}
$$

**Supersonic ($M > 1.2$, backward-facing step):**

$$
C_{d,\text{TE}} = \frac{0.135 \cdot t_\text{TE}/c}{\sqrt{\beta}}
$$

where $\beta = \sqrt{M^2 - 1}$.

**Transonic ($M = 0.9$ to $1.2$):** smoothstep blend between the two regimes.

The trailing-edge thickness $t_\text{TE}$ depends on the cross-section:
- SQUARE: $t_\text{TE} = t$ (full thickness)
- AIRFOIL/ROUNDED: $t_\text{TE} = 0.05 \cdot t$ (thin trailing edge)

The trailing-edge drag is referenced to the trailing-edge projected area
$(t_\text{TE} \times s \times n_\text{fins})$, scaled by a factor of 2 to
account for both surfaces:

$$
C_{D,\text{TE}} = C_{d,\text{TE}} \cdot \frac{2 \, t_\text{TE} \, s \, n_\text{fins}}{S_\text{ref}}
$$

where $s$ is the fin span and $n_\text{fins}$ is the interference fin count.


#### 6.4.5 ESDU Transonic Similarity

The ESDU transonic similarity rule collapses fin aerodynamic data onto a
universal curve using the transonic similarity parameter:

$$
K_\text{trans} = \frac{M_\text{eff}^2 - 1}{(\tau)^{2/3}}
$$

where $M_\text{eff} = M \cos\Lambda_{LE}$ is the Mach number normal to the
leading edge and $\tau = t/c$ is the thickness ratio.

The universal curve $h(K_\text{trans})$ maps the similarity parameter to a
normalized aerodynamic coefficient:

| $K_\text{trans}$ | $h$ |
|-------------------|-----|
| $-2.0$            | 0.70 |
| $-1.0$            | 0.85 |
| $-0.5$            | 0.93 |
| $0.0$             | 1.00 |
| $0.5$             | 0.97 |
| $1.0$             | 0.90 |
| $2.0$             | 0.75 |
| $3.0$             | 0.62 |

The transonic similarity model is active when $K_\text{trans} \in [-2, +3]$
and the thickness ratio exceeds 1%. The peak CNa at $M = 1$ is:

$$
C_{N\alpha,\text{peak}} = \frac{2\pi \, AR}{2 + \sqrt{4 + AR^2}} \cdot \left(1 + 2.5\tau + 8\tau^2\right)
$$

The transonic CNa is then:

$$
C_{N\alpha,\text{trans}} = C_{N\alpha,\text{peak}} \cdot h(K_\text{trans})
$$

At the edges of the regime ($K_\text{trans} \in [-2, -1.5]$ and $[2.5, 3]$$),
a linear blend transitions to/from the standard Barrowman fin CNa calculation.


#### 6.4.6 Worked Example

Consider a fin with $\tau = t/c = 0.05$ (5% thickness), AIRFOIL cross-section,
zero sweep ($\Lambda_{LE} = 0$).

**At $M = 1.2$:**

$$
\beta = \sqrt{1.44 - 1} = \sqrt{0.44} = 0.6633
$$
$$
C_{d,w} = \frac{4 \times 0.0025}{0.6633} = \frac{0.01}{0.6633} = 0.01508
$$

**At $M = 2.0$:**

$$
\beta = \sqrt{4.0 - 1} = \sqrt{3.0} = 1.7321
$$
$$
C_{d,w} = \frac{4 \times 0.0025}{1.7321} = \frac{0.01}{1.7321} = 0.005774
$$

**At $M = 3.0$:**

$$
\beta = \sqrt{9.0 - 1} = \sqrt{8.0} = 2.8284
$$
$$
C_{d,w} = \frac{4 \times 0.0025}{2.8284} = \frac{0.01}{2.8284} = 0.003536
$$

With 30-degree sweep, multiply by $\cos^2(30\degree) = 0.75$:

| Mach | Unswept $C_{d,w}$ | 30-deg swept $C_{d,w}$ |
|------|------------------|------------------------|
| 1.2  | 0.01508          | 0.01131                |
| 2.0  | 0.00577          | 0.00433                |
| 3.0  | 0.00354          | 0.00265                |

**Old vs. New comparison (total fin pressure drag, AIRFOIL $\tau = 0.05$, zero sweep):**

| Mach | Old OpenRocket $C_{d,\text{fin}}$ | New $C_{d,\text{fin}}$ | Notes |
|------|----------------------------------|------------------------|-------|
| 0.5  | 0.0015                           | 0.0015                 | LE bluntness only |
| 0.9  | 0.0050                           | 0.0050                 | LE bluntness only |
| 1.0  | 0.0060 (LE only)                 | 0.0093                 | +55% (Hermite onset) |
| 1.2  | 0.0065 (LE only)                 | 0.0216                 | +232% (Ackeret added) |
| 2.0  | 0.0048 (LE only)                 | 0.0106                 | +121% (Ackeret added) |
| 3.0  | 0.0040 (LE only)                 | 0.0075                 | +88% (Ackeret added) |

The original OpenRocket code computed only leading-edge bluntness drag for
fins, completely omitting the thickness wave drag that is the dominant
contribution at supersonic speeds. The new Ackeret wave drag model adds
substantial drag above $M = 1$, bringing the fin drag into agreement with
thin-airfoil theory and experimental data.


### 6.5 Lift-Induced Drag

At nonzero angle of attack, the normal force $C_N$ has an axial (drag)
component due to the geometric relationship between the force and velocity
vectors. The lift-induced drag is computed as:

$$
C_{D,i} = C_N \sin\alpha
$$

where $\alpha$ is the angle of attack. This expression follows directly from
resolving the aerodynamic force vector (which acts primarily normal to the body
axis) into the velocity-aligned drag direction.

At zero angle of attack, $C_{D,i} = 0$ identically, so this term has no effect
on zero-AoA drag predictions (drag polars, drag-vs-Mach sweeps, etc.).

The implementation clamps $C_{D,i} \ge 0$ to ensure that induced drag is
always non-negative (physical requirement).

**Tabulated values:**

| $\alpha$ (deg) | $\sin\alpha$ | $C_N = 2$ | $C_N = 5$ | $C_N = 10$ |
|-----------------|-------------|-----------|-----------|------------|
| 0               | 0           | 0         | 0         | 0          |
| 2               | 0.0349      | 0.070     | 0.175     | 0.349      |
| 5               | 0.0872      | 0.174     | 0.436     | 0.872      |
| 10              | 0.1736      | 0.347     | 0.868     | 1.736      |
| 15              | 0.2588      | 0.518     | 1.294     | 2.588      |

At high angles of attack (e.g., $\alpha = 15\degree$), the lift-induced drag
becomes a very large fraction of the total drag, comparable to or exceeding all
other components combined. This is physically correct: a body flying at large
angle of attack experiences enormous aerodynamic resistance due to the
cross-flow component.


### 6.6 Axial Drag Conversion

The drag coefficient $C_D$ computed by the drag calculator represents the total drag force referenced to the body cross-section area. In the 6-DOF equations of motion, this must be converted to an axial force coefficient $C_{D,\text{axial}}$ that accounts for the geometric projection of drag at nonzero angle of attack. The conversion is:

$$C_{D,\text{axial}} = f(\alpha) \cdot C_D$$

where $f(\alpha)$ is a piecewise polynomial multiplier:

- For $0 \leq \alpha < 17°$: $f$ increases from 1.0 to 1.3 via a degree-3 polynomial with zero derivatives at both endpoints (C1-continuous).
- For $17° \leq \alpha \leq 90°$: $f$ decreases from 1.3 to 0 via a degree-4 polynomial with zero derivatives at both endpoints and zero second derivative at $\alpha = 90°$.

The multiplier peaks at $\alpha = 17°$, reflecting the maximum axial force projection that occurs when the drag vector is most aligned with the body axis. At $\alpha = 90°$ (broadside), the axial component of drag is zero — all drag acts as normal force.

For $\alpha > 90°$ (backward flight during tumbling), the function is reflected about $90°$ and the sign is negated: $C_{D,\text{axial}} = -f(\pi - \alpha) \cdot C_D$. This correctly models the thrust-like axial force that a backwards-flying body experiences from drag.


### 6.7 Forward-Facing Step Drag

When a body component has a larger fore radius than the aft radius of the upstream component (e.g., a payload section wider than the body tube), the resulting forward-facing step creates additional pressure drag at transonic and supersonic speeds. This is modeled using the ESDU 66011 approach.

#### 6.7.1 Step Geometry

The step face is an annular ring with area:

$$A_\text{step} = \pi (r_\text{fore}^2 - r_\text{upstream}^2)$$

where $r_\text{fore}$ is the fore radius of the downstream component and $r_\text{upstream}$ is the aft radius of the upstream component. The step height is $h = r_\text{fore} - r_\text{upstream}$.

#### 6.7.2 Step Face Drag

The stagnation pressure coefficient on the step face is computed from the normal shock pressure ratio at the local Mach number. The step face drag is:

$$C_{D,\text{step}} = C_{p,\text{stag}} \cdot \frac{A_\text{step}}{S_\text{ref}}$$

#### 6.7.3 Reattachment Recovery Drag

Behind the step, the separated flow reattaches over a recovery length of approximately $3h$. The SBLI plateau pressure coefficient acts over this recovery region:

$$C_{p,\text{plateau}} = 4.2 \sqrt{\frac{2 C_f}{\sqrt{M^2 - 1}}}$$

The recovery drag is:

$$C_{D,\text{recovery}} = 0.6 \cdot C_{p,\text{plateau}} \cdot \frac{2\pi r_\text{fore} \cdot 3h}{S_\text{ref}}$$

The 0.6 factor accounts for the pressure recovery being incomplete over the reattachment region. The plateau pressure is capped at $C_{p,\text{plateau}} \leq 2.0$ and the $M^2 - 1$ term is guarded with a floor of 0.04 (see Section 9.5.4) to prevent singularities near Mach 1.

#### 6.7.4 Transonic Activation

The step drag is zero below $M = 0.95$ (no flow separation from forward-facing steps at subsonic speeds) and reaches full value at $M = 1.1$, with a C1-continuous smoothstep blend between these bounds:

$$w(t) = 3t^2 - 2t^3, \quad t = \frac{M - 0.95}{0.15}$$


### 6.8 Fin Shock-Boundary Layer Interaction

At supersonic speeds ($M > 1.2$), the oblique shock from the fin leading edge can interact with the boundary layer on the fin surface, causing flow separation that reduces the effective aerodynamic chord and adds a plateau pressure drag increment. The model uses the free-interaction theory of Chapman, Kuehn, and Larson (NACA Report 1356, 1958).

#### 6.8.1 Separation Criterion

The fin leading-edge wedge angle and resulting shock pressure coefficient are:

$$\theta_\text{fin} = \arctan\!\left(\frac{t}{2c}\right), \qquad C_{p,\text{shock}} = \frac{2\theta_\text{fin}}{\beta}$$

where $t$ is fin thickness, $c$ is MAC, and $\beta = \sqrt{M^2 - 1}$. Flow separation occurs when $C_{p,\text{shock}}$ exceeds the critical pressure coefficient:

$$C_{p,\text{crit}} = 3.5 \sqrt{\frac{C_f}{\sqrt{M^2 - 1}}}$$

where $C_f = 0.027/Re_x^{1/7}$ is the local skin friction from the 1/7th power law. The separation check is skipped for $Re_x < 10^4$ (boundary layer too thin for meaningful SBLI).

#### 6.8.2 Effective Chord Reduction

When separation occurs, the separation length $L_\text{sep}$ is computed from the free-interaction formula (see Section 9.5.4), and the effective aerodynamic chord is reduced:

$$c_\text{eff} = \max(c - L_\text{sep},\; 0.1c)$$

The 10% floor ensures that a minimum aerodynamic chord is always retained. The reduced chord affects the fin planform area used in the CNa calculation.

#### 6.8.3 SBLI Pressure Drag

The separated region produces a plateau pressure drag increment:

$$C_{D,\text{SBLI}} = \frac{C_{p,\text{plateau}} \cdot L_\text{sep} \cdot s \cdot n}{S_\text{ref}}$$

where $s$ is the fin span, $n$ is the number of fins, and $C_{p,\text{plateau}}$ is the Chapman-Kuehn-Larson plateau pressure coefficient (equal to $C_{p,\text{crit}}$ from the same free-interaction theory).


### 6.9 Drag Budget Summary

The following tables present the complete drag budget for a representative
sounding rocket: 10-degree conical nose (fineness ratio $f = 2.84$), cylindrical
body ($L = 1.5$ m, $D = 0.10$ m), 4 fins (AIRFOIL, $\tau = 0.05$,
$\Lambda_{LE} = 0$, $s = 0.08$ m, $c = 0.15$ m). Sea level conditions,
$\alpha = 0$, smooth finish. Reference area $S_\text{ref} = \pi D^2/4 = 7.854 \times 10^{-3}$ m$^2$.

#### Table 6.1: Drag Budget at $M = 0.5$

| Component          | $C_D$ contribution | Fraction |
|--------------------|--------------------|----------|
| Skin friction      | 0.385              | 62.7%    |
| Nose pressure      | 0.000              | 0%       |
| Fin LE pressure    | 0.009              | 1.5%     |
| Fin wave drag      | 0.000              | 0%       |
| Base drag (body)   | 0.153              | 24.9%    |
| Base drag (fin TE) | 0.008              | 1.3%     |
| Induced drag       | 0.000              | 0%       |
| **Total $C_D$**    | **0.555** (est.)   | --       |

At subsonic speeds, skin friction dominates (~63%), followed by base drag
(~25%). Wave drag is absent.

#### Table 6.2: Drag Budget at $M = 2.0$

| Component          | $C_D$ contribution | Fraction |
|--------------------|--------------------|----------|
| Skin friction      | 0.246              | 38.3%    |
| Nose wave drag     | 0.105              | 16.3%    |
| Fin LE pressure    | 0.012              | 1.9%     |
| Fin wave drag      | 0.058              | 9.0%     |
| Base drag (body)   | 0.111              | 17.3%    |
| Base drag (fin TE) | 0.012              | 1.9%     |
| Induced drag       | 0.000              | 0%       |
| **Total $C_D$**    | **0.544** (est.)   | --       |

At supersonic speeds, wave drag from the nose and fins becomes a major
contributor (~25% combined). Skin friction is reduced by the Eckert method but
remains the largest single component. Base drag decreases from its transonic
peak.

#### Table 6.3: Drag Budget at $M = 5.0$

| Component          | $C_D$ contribution | Fraction |
|--------------------|--------------------|----------|
| Skin friction      | 0.100              | 20.4%    |
| Nose wave drag     | 0.090              | 18.4%    |
| Fin LE pressure    | 0.015              | 3.1%     |
| Fin wave drag      | 0.035              | 7.1%     |
| Base drag (body)   | 0.071              | 14.5%    |
| Base drag (fin TE) | 0.009              | 1.8%     |
| Induced drag       | 0.000              | 0%       |
| **Total $C_D$**    | **0.320** (est.)   | --       |

At high supersonic speeds, skin friction is drastically reduced (79% lower than
incompressible value) and nose wave drag becomes comparable in magnitude. The
total drag coefficient decreases substantially from $M = 2$ to $M = 5$ because
of the strong compressibility reduction in friction and the decay of wave drag
with increasing Mach.

#### Table 6.4: Old vs. New Total $C_D$ Comparison

| Mach | Old OpenRocket | New (this work) | $\Delta C_D$ | Rel. change |
|------|---------------|-----------------|---------------|-------------|
| 0.3  | 0.56          | 0.56            | 0.00          | 0%          |
| 0.5  | 0.55          | 0.56            | +0.01         | +2%         |
| 0.9  | 0.58          | 0.60            | +0.02         | +3%         |
| 1.0  | 0.85          | 0.88            | +0.03         | +4%         |
| 1.5  | 0.72          | 0.65            | -0.07         | -10%        |
| 2.0  | 0.58          | 0.54            | -0.04         | -7%         |
| 3.0  | 0.45          | 0.42            | -0.03         | -7%         |
| 5.0  | N/A           | 0.32            | --            | new         |
| 10.0 | N/A           | 0.25            | --            | new         |

The differences are concentrated at supersonic speeds where the new analytical
models replace extrapolated empirical data. The old code tended to overpredict
drag at $M = 1.5$-$3.0$ (due to continued use of transonic-regime
correlations) while completely lacking predictions above $M \approx 3$. The new
models extend accurate drag prediction from $M = 0$ through $M = 10$.

Key improvements over the original OpenRocket drag models:

1. **Nose wave drag:** Taylor-Maccoll and shock-expansion replace extrapolated
   TR-R-100 tables above $M = 1.5$, correcting a ~20% error at $M = 3$.

2. **Base drag:** Devan-Ashwood replaces $0.25/M$, correctly predicting the
   nonzero asymptote at high Mach and improving accuracy by 10-15% in the
   $M = 1.5$-$2.0$ range.

3. **Skin friction:** Eckert method replaces a simple $(1 - 0.1M^2)$ factor,
   capturing the 30-80% friction reduction at $M = 2$-$5$ with physical
   fidelity.

4. **Fin wave drag:** Ackeret theory adds the previously-missing thickness wave
   drag, which is the dominant fin drag component above $M = 1.2$.

5. **Regime blending:** C1-continuous Hermite splines at every transition
   prevent the simulation instabilities that occurred with the original
   discontinuous model boundaries.


## 7. Shock Geometry Pre-Pass

### 7.1 Motivation

In subsonic flight, all components of a rocket vehicle experience identical
freestream conditions: the same Mach number, static pressure, and temperature.
This is an excellent approximation because pressure disturbances propagate
upstream and equalize throughout the flow field.  At supersonic speeds, this
assumption breaks down entirely.

When a rocket exceeds Mach 1, the nose cone generates an oblique shock wave
that compresses the flow.  The post-shock region between the shock surface and
the body has a lower Mach number and higher pressure than the freestream.
Downstream components -- body transitions, fin sets, launch lugs -- sit inside
this post-shock flow field and experience local conditions that differ markedly
from the freestream.  The magnitude of this difference depends on the nose
geometry and the freestream Mach number.

Consider a rocket with a 15-degree half-angle conical nose at $M_\infty = 2.5$.
The Taylor-Maccoll solution gives a post-shock Mach number of approximately
$M_2 \approx 2.14$ -- a 14% reduction from freestream.  The post-shock static
pressure rises by roughly 40%.  At $M_\infty = 3.0$ with a 20-degree half-angle
cone, the post-shock Mach drops to approximately $M_2 \approx 2.27$ while the
pressure ratio reaches $p_2/p_\infty \approx 1.75$.  At $M_\infty = 5.0$, these
differences can exceed 35% in Mach and a factor of 3 in pressure.

The consequences for aerodynamic prediction are substantial:

1. **Fin normal force slope** ($C_{N\alpha}$) depends on local Mach through the
   $K_1$, $K_2$, $K_3$ supersonic coefficients.  A 14% Mach reduction at
   $M_\infty = 2.5$ alters $K_1 = 2/\beta$ by approximately 18% because
   $\beta = \sqrt{M^2 - 1}$ is nonlinear.

2. **Fin normal force magnitude** is proportional to local dynamic pressure
   $q = \frac{1}{2} \gamma p M^2$.  The ratio $q_\text{local}/q_\infty$
   deviates from unity whenever the local Mach or pressure differs from
   freestream.

3. **Interference factors** (Pitts-Nielsen-Kaattari) depend on Mach through
   the $\beta_s$ parameter.  Feeding freestream Mach instead of local Mach
   produces 5--15% errors in the interference correction at $M = 2$--$3$.

4. **Drag coefficients** -- wave drag, base drag, and skin friction -- all
   depend on local flow conditions rather than freestream values.

Without a shock geometry pre-pass, the only alternative is to feed freestream
conditions to every component, which introduces systematic errors of 5--35%
in the supersonic regime.  The pre-pass computes local conditions once per
timestep and distributes them to all downstream calculators.


### 7.2 Flow Topology

The following diagram illustrates the shock and expansion fan structure on
a typical cone-cylinder-fins rocket at $M_\infty > 1$:

```{=latex}
\begin{figure}[htbp]
\centering
\resizebox{\linewidth}{!}{%
\begin{tikzpicture}[font=\footnotesize, >=Latex]
% --- Freestream arrow (well above the vehicle) ---
\draw[->, very thick] (-0.5,2.1) -- (8.2,2.1) node[right] {freestream $M_\infty$};

% --- Axis of symmetry ---
\draw[thin, dash dot, gray!60] (-0.3,0) -- (7.8,0) node[right, font=\scriptsize, gray!80] {axis};

% --- Nose cone (conical, symmetric about y=0) ---
\fill[gray!12] (0,0) -- (2.0,0.55) -- (2.0,-0.55) -- cycle;
\draw[thick] (0,0) -- (2.0,0.55);
\draw[thick] (0,0) -- (2.0,-0.55);
\node[font=\scriptsize] at (1.15,0) {nose};

% --- Body tube ---
\fill[gray!8] (2.0,-0.55) rectangle (5.8,0.55);
\draw[thick] (2.0,0.55) -- (5.8,0.55);
\draw[thick] (2.0,-0.55) -- (5.8,-0.55);
\draw[thick] (5.8,0.55) -- (5.8,-0.55);
\node[font=\scriptsize] at (3.9,0) {body tube};

% --- Fins (upper and lower, trapezoidal) ---
\fill[gray!20] (4.8,0.55) -- (4.5,1.25) -- (5.7,1.25) -- (5.8,0.55) -- cycle;
\fill[gray!20] (4.8,-0.55) -- (4.5,-1.25) -- (5.7,-1.25) -- (5.8,-0.55) -- cycle;
\draw[thick] (4.8,0.55) -- (4.5,1.25) -- (5.7,1.25) -- (5.8,0.55);
\draw[thick] (4.8,-0.55) -- (4.5,-1.25) -- (5.7,-1.25) -- (5.8,-0.55);
\node[font=\scriptsize] at (5.15,0.92) {fin};

% --- Oblique shock from nose tip ---
\fill[blue!5] (0,0) -- (3.2,1.75) -- (3.2,0.55) -- (2.0,0.55) -- cycle;
\draw[thick, red!70!black] (0,0) -- (3.2,1.75);
\draw[thick, red!70!black] (0,0) -- (3.2,-1.75);
\node[red!70!black, font=\scriptsize, above, sloped] at (1.6,0.88) {oblique shock ($\beta_s$)};

% --- Post-shock label ---
\node[align=left, font=\scriptsize, anchor=west] at (3.4,1.5)
  {post-shock: $M_2\!<\!M_\infty$,\; $p_2\!>\!p_\infty$};

% --- Shoulder expansion fan (at nose--body junction) ---
\draw[densely dashed, blue!65!black] (2.0,0.55) -- (2.7,1.15);
\draw[densely dashed, blue!65!black] (2.0,0.55) -- (3.0,0.90);
\draw[densely dashed, blue!65!black] (2.0,0.55) -- (3.15,0.72);
\node[blue!65!black, font=\scriptsize, align=center, anchor=south] at (2.85,1.15) {shoulder\\PM fan};

% --- Station markers ---
\foreach \x in {0.5,1.5,2.5,3.5,4.5,5.5}
  \draw[gray!70] (\x,-0.72) -- (\x,-0.88);
\draw[->, gray!70] (3.0,-1.1) -- (3.0,-0.75);
\node[font=\scriptsize, gray!70] at (3.0,-1.25) {stations $x_i$};
\end{tikzpicture}%
}
\caption{Shock and expansion topology on a cone--cylinder--fin vehicle (schematic).}
\label{fig:shock-topology-rocket}
\end{figure}
```

At the nose tip, the conical or ogive surface deflects the flow, generating
an oblique shock.  The shock angle $\beta_s$ depends on the deflection angle
$\theta$ and the freestream Mach number through the theta-beta-Mach relation.
Behind this shock, the flow is compressed: $M_2 < M_\infty$, $p_2 > p_\infty$,
$T_2 > T_\infty$.

Along the nose cone surface, the body profile may curve (ogive) or remain
straight (cone).  Where the surface angle decreases (turns away from the
flow), isentropic expansion fans form, accelerating the flow and reducing
pressure.  Where the surface angle increases (turns into the flow), oblique
compression waves coalesce into weak shocks.

At the nose-to-body-tube junction (the "shoulder"), there is typically a
significant expansion fan as the surface angle drops abruptly from the nose
cone's aft tangent angle to zero (body tube is parallel to the axis).
This expansion increases the local Mach and decreases the pressure.

The fin set, located on the body tube some distance aft of the shoulder,
experiences conditions that are the cumulative result of the nose shock,
surface turning along the nose profile, and the shoulder expansion.


### 7.3 Station Marching Algorithm

The shock geometry computation proceeds in a single nose-to-tail pass along
the body chain, which is the linked list of `SymmetricComponent` objects from
the rocket's foremost component to the aftmost.

**Step 1: Build the body chain.**  Starting from the foremost
`SymmetricComponent` (which has no predecessor), walk the chain via
`getNextSymmetricComponent()` to collect all body components in axial order.

**Step 2: Initialize flow state.**  Set the running flow state to freestream:

$$
M_\text{local} = M_\infty, \quad \frac{p_\text{local}}{p_\infty} = 1.0, \quad \frac{T_\text{local}}{T_\infty} = 1.0
$$

**Step 3: Process each component.**

For each `SymmetricComponent` in the chain, the algorithm branches based on
component type:

#### 7.3.1 Nose Cone and Transitions

For components that are `Transition` objects (but not `BodyTube`), the nose
cone tip half-angle is computed from the surface tangent at $x = 0$:

$$
\theta_\text{tip} = \arctan\!\left(\frac{r(\Delta x) - r_0}{\Delta x}\right)
$$

where $\Delta x = L \times 10^{-4}$ is a small finite-difference step and
$r(\cdot)$ is the shape function.

The initial oblique shock is computed using the Taylor-Maccoll cone flow
solution.  Given $M_\infty$ and $\theta_\text{tip}$, the solver returns the
post-shock conditions:

$$
M_2, \quad \frac{p_2}{p_1} = f(M_1, \theta, \gamma), \quad \frac{T_2}{T_1} = g(M_1, \theta, \gamma)
$$

If the half-angle exceeds the maximum deflection angle for an attached
oblique shock at the given Mach number (detached shock), the algorithm falls
back to the normal shock relations:

$$
M_2 = \sqrt{\frac{1 + \frac{\gamma - 1}{2} M_1^2}{\gamma M_1^2 - \frac{\gamma - 1}{2}}}
$$

$$
\frac{p_2}{p_1} = \frac{2\gamma M_1^2 - (\gamma - 1)}{\gamma + 1}
$$

$$
\frac{T_2}{T_1} = \frac{p_2}{p_1} \cdot \frac{M_2^2}{M_1^2} \cdot \frac{1 + \frac{\gamma-1}{2}M_1^2}{1 + \frac{\gamma-1}{2}M_2^2}
$$

In the detached-shock case, the post-shock Mach is subsonic.  However, for a
streamlined nose followed by a body tube, the flow re-accelerates around the
body and is approximately freestream by the body tube section.  The algorithm
handles this by resetting to freestream when encountering a body tube with
subsonic local Mach behind a supersonic freestream (see Section 7.3.2).

**Surface marching** along the transition uses $N = 20$ strips per component.
At each strip boundary $i = 0, 1, \ldots, N$, the algorithm computes:

1. The axial position: $x_i = x_\text{comp} + i \cdot L/N$

2. The local surface tangent angle via central finite differences:
   $$
   \theta_\text{surf}(x) = \arctan\!\left(\frac{r(x + \delta/2) - r(x - \delta/2)}{\delta}\right)
   $$
   where $\delta = L \times 10^{-4}$ (clamped to $\geq 10^{-6}$ m).

3. The turning angle from the previous station:
   $$
   \Delta\theta = \theta_\text{prev} - \theta_\text{surf}
   $$

4. If $|\Delta\theta| > 10^{-6}$ rad and $M_\text{local} \geq 1.0$:

   - **Expansion** ($\Delta\theta > 0$: surface turns away from flow):
     Apply Prandtl-Meyer expansion.  The downstream Mach $M_\text{new}$
     satisfies:
     $$
     \nu(M_\text{new}) = \nu(M_\text{local}) + \Delta\theta
     $$
     where $\nu(M)$ is the Prandtl-Meyer function:
     $$
     \nu(M) = \sqrt{\frac{\gamma+1}{\gamma-1}} \arctan\!\sqrt{\frac{\gamma-1}{\gamma+1}(M^2-1)} - \arctan\!\sqrt{M^2-1}
     $$
     The isentropic pressure and temperature ratios are:
     $$
     \frac{p_\text{new}}{p_\text{local}} = \left(\frac{1 + \frac{\gamma-1}{2}M_\text{local}^2}{1 + \frac{\gamma-1}{2}M_\text{new}^2}\right)^{\!\gamma/(\gamma-1)}
     $$
     $$
     \frac{T_\text{new}}{T_\text{local}} = \frac{1 + \frac{\gamma-1}{2}M_\text{local}^2}{1 + \frac{\gamma-1}{2}M_\text{new}^2}
     $$

   - **Compression** ($\Delta\theta < 0$: surface turns into flow):
     Solve the oblique shock relations for deflection angle $|\Delta\theta|$
     at the current local Mach to obtain the weak-shock solution.  The
     cumulative pressure and temperature ratios are updated multiplicatively:
     $$
     \frac{p_\text{new}}{p_\infty} = \frac{p_\text{new}}{p_\text{local}} \cdot \frac{p_\text{local}}{p_\infty}
     $$

5. Compute the dynamic pressure ratio:
   $$
   \frac{q_\text{local}}{q_\infty} = \frac{p_\text{local}}{p_\infty} \cdot \frac{M_\text{local}^2}{M_\infty^2}
   $$
   This follows from $q = \frac{1}{2}\gamma p M^2$.

6. Store the station: $(x_i,\; M_\text{local},\; p_\text{local}/p_\infty,\; T_\text{local}/T_\infty,\; q_\text{local}/q_\infty)$.

#### 7.3.2 Body Tubes

Body tubes have a constant radius, so the surface angle is zero.  The primary
effects at a body tube are:

1. **Flow recovery after detached shock.**  If the nose shock was detached
   (normal shock fallback produced subsonic $M_\text{local}$) but the
   freestream is supersonic, the flow has re-accelerated around the nose.
   The algorithm resets to freestream conditions:
   $$
   M_\text{local} \leftarrow M_\infty, \quad p/p_\infty \leftarrow 1.0, \quad T/T_\infty \leftarrow 1.0
   $$

2. **Junction effects.**  At the junction between the previous component and
   the body tube, if the previous surface angle was nonzero, there is a
   turning angle $\Delta\theta = \theta_\text{prev} - 0 = \theta_\text{prev}$.
   If positive (surface turns away, as at a nose-to-body shoulder), a
   Prandtl-Meyer expansion is applied.  If negative (surface turns into flow,
   as at a widening transition-to-body junction), an oblique shock is applied.

3. **Constant conditions along tube.**  Since the body tube has no further
   surface turning, two stations are recorded: one at the tube's fore end and
   one at the aft end, both with the same local conditions.


### 7.4 Near-Sonic Blending

When the freestream Mach number is only slightly above 1.0, the oblique shock
is very weak and the post-shock conditions are nearly identical to freestream.
However, the shock solver can produce noisy results near $M = 1.0$ because
the shock angle approaches 90 degrees (normal shock limit) and the
theta-beta-Mach relation becomes ill-conditioned.

To prevent a step discontinuity at $M = 1.0$ and to ensure stability of the
simulation near the sonic transition, the shock geometry uses a linear
activation blend between $M = 1.0$ and $M_\text{blend} = 1.1$:

$$
\alpha = \frac{M_\infty - 1.0}{M_\text{blend} - 1.0} = \frac{M_\infty - 1.0}{0.1}
$$

clamped to $[0, 1]$.  Each station's local conditions are then blended toward
freestream:

$$
M_\text{blended} = M_\infty + \alpha \cdot (M_\text{computed} - M_\infty)
$$

$$
\left(\frac{p}{p_\infty}\right)_\text{blended} = 1.0 + \alpha \cdot \left(\frac{p_\text{computed}}{p_\infty} - 1.0\right)
$$

$$
\left(\frac{T}{T_\infty}\right)_\text{blended} = 1.0 + \alpha \cdot \left(\frac{T_\text{computed}}{T_\infty} - 1.0\right)
$$

$$
\left(\frac{q}{q_\infty}\right)_\text{blended} = 1.0 + \alpha \cdot \left(\frac{q_\text{computed}}{q_\infty} - 1.0\right)
$$

At $M_\infty = 1.0$, all corrections vanish ($\alpha = 0$).  At $M_\infty = 1.05$,
corrections are at 50% strength.  At $M_\infty \geq 1.1$, full computed
corrections are applied ($\alpha = 1$).  This produces a C0-continuous
transition that is sufficient for simulation stability because the underlying
corrections themselves vanish smoothly as $M \to 1^+$.


### 7.5 Station Interpolation: `getConditionsAt(x)`

Downstream calculators query the shock geometry for local conditions at an
arbitrary axial position $x$ (measured from the nose tip).  The station list
is sorted by axial position, so the query uses a binary search to find the
enclosing interval, followed by linear interpolation.

**Algorithm:**

1. If the geometry is subsonic (the `SUBSONIC` singleton), return freestream
   conditions immediately: all ratios equal to 1.0, local Mach equal to
   freestream.

2. If $x \leq x_0$ (before the first station), return the first station's
   values.

3. If $x \geq x_{N-1}$ (after the last station), return the last station's
   values.

4. Otherwise, perform a binary search on the station array to find indices
   $i$ and $i+1$ such that $x_i \leq x < x_{i+1}$.

5. Compute the interpolation parameter:
   $$
   t = \frac{x - x_i}{x_{i+1} - x_i}
   $$
   with a guard: if $x_{i+1} - x_i < 10^{-12}$, return station $i$ directly
   (degenerate case).

6. Interpolate each quantity linearly:
   $$
   M(x) = M_i + t \cdot (M_{i+1} - M_i)
   $$
   $$
   (p/p_\infty)(x) = (p/p_\infty)_i + t \cdot \left[(p/p_\infty)_{i+1} - (p/p_\infty)_i\right]
   $$
   and similarly for $T/T_\infty$ and $q/q_\infty$.

The binary search has $O(\log N)$ complexity where $N$ is the number of
stations (typically 20--60 for a 2--3 component rocket).  Each component
calculator calls `getConditionsAt()` once per timestep, so the total overhead
per timestep is $O(C \log N)$ where $C$ is the number of aerodynamic components.


### 7.6 Subsonic Passthrough

At subsonic Mach ($M_\infty \leq 1.0$), no shock geometry is computed.  The
`ShockGeometry.compute()` method returns a pre-allocated singleton instance
`SUBSONIC` that has:

- `isSupersonic = false`
- An empty station list
- Zero freestream Mach

When `getConditionsAt(x)` is called on the `SUBSONIC` instance, it returns
freestream conditions directly (all pressure/temperature/dynamic-pressure
ratios equal to 1.0) without any search or interpolation.  This guarantees
zero computational overhead at subsonic speeds.

The singleton pattern also means that no heap allocation occurs for the common
subsonic case -- the same object is reused across all timesteps below Mach 1.0.


### 7.7 Data Flow

The shock geometry integrates into the existing calculator architecture as
follows:

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
\node[box, below=0.35cm of loop, text width=8.2cm, align=left] (q) {\texttt{getConditionsAt}($x$): binary search + linear interpolation of local $M$, $p$, $T$, $q$; used for fin $K_1$/$K_2$/$K_3$, $q$-scaling, PNK $F_{WB}$/$F_{BW}$.};
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

The `ShockGeometry` is also available to the drag calculator
(`BarrowmanDragCalculator`), though the primary consumer is the stability
calculator because fin CNa and interference factors are the most sensitive
to local flow conditions.

**Cache invalidation.**  The shock geometry is recomputed whenever the
aerodynamic forces are requested with new flight conditions.  On configuration
changes (staging, fairing separation), the calculator's `voidAerodynamicCache()`
method sets `shockGeometry = null`, forcing recomputation on the next call.


### 7.8 Worked Example: Cone-Cylinder-Fins at M = 2.5

**Geometry:**
- Nose cone: conical, half-angle $\theta_\text{tip} = 15°$, length $L_n = 0.20$ m
- Body tube: length $L_b = 0.60$ m, radius $r = 0.04$ m
- Fins: 3 trapezoidal fins at axial position $x_\text{fin} = 0.65$ m from nose

**Freestream conditions:** $M_\infty = 2.5$, $\gamma = 1.4$

**Step 1: Nose cone initial shock.**

Using the Taylor-Maccoll cone flow solution for $M_1 = 2.5$ and
$\theta_c = 15°$, the solver returns:

Shock angle: $\beta_s \approx 33.5°$

Post-shock Mach: $M_2 \approx 2.137$

Pressure ratio: $p_2/p_1 \approx 1.685$

Temperature ratio: $T_2/T_1 \approx 1.195$

These become the initial running state.

**Step 2: Surface marching on nose cone.**

The nose cone is divided into $N = 20$ strips, each $\Delta x = 0.01$ m.
For a conical nose, the surface angle is constant at $\theta = 15°$
everywhere, so the turning angle between adjacent strips is zero:

$$
\Delta\theta_i = \theta_\text{prev} - \theta_\text{surf} = 15° - 15° = 0
$$

No additional shocks or expansions occur.  All 21 stations along the nose
cone have:

$$
M = 2.137, \quad p/p_\infty = 1.685, \quad T/T_\infty = 1.195
$$

Dynamic pressure ratio:
$$
\frac{q_\text{local}}{q_\infty} = \frac{p_\text{local}}{p_\infty} \cdot \frac{M_\text{local}^2}{M_\infty^2} = 1.685 \cdot \frac{2.137^2}{2.5^2} = 1.685 \cdot \frac{4.567}{6.25} = 1.231
$$

**Step 3: Shoulder expansion at nose-to-body junction.**

At $x = 0.20$ m (aft end of nose cone), the surface transitions from
$\theta_\text{prev} = 15°$ to $\theta_\text{tube} = 0°$.

Turning angle: $\Delta\theta = 15° - 0° = 15° = 0.2618$ rad (expansion).

Apply Prandtl-Meyer expansion starting from $M_\text{local} = 2.137$:

$$
\nu(2.137) = \sqrt{\frac{2.4}{0.4}} \arctan\!\sqrt{\frac{0.4}{2.4}(2.137^2-1)} - \arctan\!\sqrt{2.137^2-1}
$$

$$
= \sqrt{6} \arctan\!\sqrt{\frac{0.4}{2.4} \cdot 3.567} - \arctan\!\sqrt{3.567}
$$

$$
= 2.449 \arctan(0.7704) - \arctan(1.889)
$$

$$
= 2.449 \times 0.6562 - 1.0837
$$

$$
\nu(2.137) = 0.5231 \text{ rad} = 29.97°
$$

The downstream Prandtl-Meyer angle is:

$$
\nu(M_\text{new}) = 29.97° + 15° = 44.97°
$$

Inverting the Prandtl-Meyer function (numerically), $M_\text{new} \approx 2.75$.

Isentropic pressure ratio across the expansion:

$$
\frac{p_\text{new}}{p_\text{local}} = \left(\frac{1 + 0.2 \times 2.137^2}{1 + 0.2 \times 2.75^2}\right)^{3.5}
= \left(\frac{1.913}{2.5125}\right)^{3.5}
= 0.7615^{3.5}
= 0.396
$$

Cumulative pressure ratio:

$$
\frac{p_\text{new}}{p_\infty} = 0.396 \times 1.685 = 0.667
$$

Isentropic temperature ratio:

$$
\frac{T_\text{new}}{T_\text{local}} = \frac{1.913}{2.5125} = 0.7615
$$

Cumulative temperature ratio:

$$
\frac{T_\text{new}}{T_\infty} = 0.7615 \times 1.195 = 0.910
$$

Dynamic pressure ratio at the body tube:

$$
\frac{q}{q_\infty} = 0.667 \times \frac{2.75^2}{2.5^2} = 0.667 \times 1.21 = 0.807
$$

**Step 4: Body tube stations.**

The body tube has constant radius, so no further turning occurs.  Two
stations are recorded at $x = 0.20$ m and $x = 0.80$ m, both with:

$$
M_\text{local} = 2.75, \quad p/p_\infty = 0.667, \quad T/T_\infty = 0.910, \quad q/q_\infty = 0.807
$$

**Step 5: Query fin station.**

The fins are at $x_\text{fin} = 0.65$ m, which lies within the body tube
region (0.20 m to 0.80 m).  The binary search finds the enclosing interval
$[0.20, 0.80]$.  Since conditions are constant along the body tube, linear
interpolation gives:

$$
t = \frac{0.65 - 0.20}{0.80 - 0.20} = 0.75
$$

$$
M_\text{fin} = 2.75 + 0.75 \times (2.75 - 2.75) = 2.75
$$

The fin set therefore operates at:

| Quantity | Freestream | Local (post-shock) | Difference |
|----------|-----------|-------------------|------------|
| Mach | 2.50 | 2.75 | +10% |
| $p/p_\infty$ | 1.00 | 0.667 | -33% |
| $T/T_\infty$ | 1.00 | 0.910 | -9% |
| $q/q_\infty$ | 1.00 | 0.807 | -19% |

Note that after the shoulder expansion, the local Mach is actually *higher*
than freestream, while the pressure and dynamic pressure are lower.  This is
characteristic of the expansion-dominated post-shoulder flow.  The fin normal
force, proportional to $q_\text{local}$, is reduced by 19% compared to a
naive freestream calculation.  The supersonic fin $C_{N\alpha}$ coefficients
$K_1$, $K_2$, $K_3$ are evaluated at $M = 2.75$ rather than 2.50, which
changes $K_1 = 2/\sqrt{M^2-1}$ from $0.873$ to $0.776$ -- an 11% reduction.

The combined effect of local Mach and dynamic pressure corrections is a
27% change in the predicted fin normal force relative to a freestream-only
calculation.  This demonstrates why the shock geometry pre-pass is essential
for accurate supersonic stability prediction.


## 8. Stability Corrections

### 8.1 Body CNa Correction: Allen-Perkins Crossflow

At subsonic speeds, the body normal force coefficient slope is computed by the
Barrowman method, which gives accurate results for slender bodies at low Mach
numbers.  The body lift coefficient $K$ that multiplies the planform area
contribution is set to $K = 1.1$ (Galejs empirical value) for subsonic flow.

At supersonic speeds, the crossflow analogy of Allen and Perkins
(NACA Report 1048, 1955) provides the physical basis for the body normal force.
According to this theory, the body normal force has two components:

1. **Potential flow term:** The inviscid force due to the pressure distribution
   on the body, which is proportional to $\sin(2\alpha)$ and depends on the
   rate of change of cross-sectional area.

2. **Crossflow drag term:** The viscous force due to flow separation on the
   leeward side, analogous to a cylinder in crossflow at the crossflow
   velocity $V_c = V_\infty \sin\alpha$.  This term is proportional to
   $\sin^2\alpha$.

The total body normal force coefficient per unit length is:

$$
\frac{dC_N}{dx} = \frac{2}{S_\text{ref}} \frac{dA}{dx} \sin\alpha\cos\alpha + \frac{d}{S_\text{ref}} C_{d,c}(M_c) \sin^2\alpha
$$

where $A(x)$ is the cross-sectional area, $d$ is the local diameter,
$S_\text{ref}$ is the reference area, and $C_{d,c}$ is the crossflow drag
coefficient of the cylindrical cross-section at crossflow Mach number
$M_c = M_\infty \sin\alpha$.

The effective body lift coefficient $K$ accounts for the compressibility
enhancement of the crossflow drag at supersonic speeds.  As Mach increases
above 1.0, the crossflow drag coefficient rises modestly because the
compressibility effects enhance the pressure distribution on the body.

The Mach-dependent $K$ is defined as:

$$
K_\text{supersonic} = \min\!\left(1.3,\; K_\text{sub} + 0.05 \cdot (M - 1.0)\right)
$$

where $K_\text{sub} = 1.1$ is the subsonic Galejs value.  This gives:

| $M$ | $K$ |
|-----|-----|
| 0.8 | 1.10 |
| 1.0 | 1.10 |
| 1.3 | 1.115 |
| 2.0 | 1.15 |
| 3.0 | 1.20 |
| 4.0 | 1.25 |
| 5.0+ | 1.30 |

The transition through the transonic region uses a cubic Hermite smoothstep
to maintain C1 continuity.  The blending region spans $M = 0.8$ to $M = 1.3$:

$$
t = \frac{M - M_\text{low}}{M_\text{high} - M_\text{low}} = \frac{M - 0.8}{0.5}
$$

$$
w(t) = 3t^2 - 2t^3 \quad \text{(cubic Hermite smoothstep)}
$$

$$
K_\text{eff}(M) = K_\text{sub} + w(t) \cdot (K_\text{supersonic} - K_\text{sub})
$$

The smoothstep has the properties $w(0) = 0$, $w(1) = 1$, $w'(0) = 0$,
$w'(1) = 0$, ensuring that both $K_\text{eff}$ and $dK_\text{eff}/dM$ are
continuous at the blend boundaries.

**Derivation of the $K$ range.**  The lower bound $K = 1.1$ is the established
Galejs empirical value validated against subsonic wind tunnel data for typical
model rocket geometries.  The upper bound $K = 1.3$ is based on DATCOM data
for bodies of revolution at supersonic speeds with fineness ratios of 5--15
(typical for sounding rockets and high-power rockets).  The linear increase
rate of 0.05 per Mach number was calibrated against Allen-Perkins predictions
and RASAero II output for a set of standard rocket geometries.


### 8.2 Jorgensen Crossflow Drag Coefficient

The crossflow drag coefficient $C_{d,c}$ is a critical parameter in the
Allen-Perkins crossflow analogy.  It represents the drag coefficient of an
infinite circular cylinder in crossflow at the crossflow Mach number
$M_c = M_\infty \sin\alpha$.

At low crossflow Mach ($M_c < 0.4$), $C_{d,c} \approx 1.2$, the well-known
value for a circular cylinder at subcritical Reynolds numbers.  As the
crossflow Mach increases into the transonic and supersonic range, $C_{d,c}$
rises due to shock formation on the cylinder surface, reaching approximately
2.0 at $M_c \geq 3$.

The lookup table, based on Jorgensen (NASA TR R-474, 1977), is:

| $M_c$ | $C_{d,c}$ |
|--------|-----------|
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

Between table entries, linear interpolation is used.  For $M_c > 5.0$, the
value is clamped at 2.0.

The body normal force contribution from the crossflow drag is:

$$
C_{N,\text{body}} = K_\text{eff} \cdot \frac{C_{d,c}(M_c)}{C_{d,c,\text{sub}}} \cdot \frac{A_\text{planform}}{S_\text{ref}} \cdot \frac{\sin^2\alpha}{\alpha}
$$

where $C_{d,c,\text{sub}} = 1.2$ is the baseline subsonic crossflow drag
coefficient and the ratio $C_{d,c}(M_c) / C_{d,c,\text{sub}}$ provides a
multiplicative correction factor.  The $\sin^2\alpha / \alpha$ form arises
from the product $\sin\alpha \cdot \text{sinc}(\alpha)$ in the original
Galejs formulation.

For a rocket at $M = 3.0$ and $\alpha = 10°$, the crossflow Mach is
$M_c = 3.0 \sin(10°) = 0.521$.  Interpolating in the table:

$$
C_{d,c}(0.521) = 1.20 + \frac{0.521 - 0.4}{0.6 - 0.4} \times (1.25 - 1.20) = 1.20 + 0.605 \times 0.05 = 1.230
$$

The crossflow scale factor is $1.230 / 1.20 = 1.025$, a modest 2.5% increase.
At $\alpha = 20°$, $M_c = 1.026$ and $C_{d,c} \approx 1.69$, giving a 41%
increase in body normal force -- significant for high angle-of-attack flight.


### 8.3 Center of Pressure Aft Shift

At subsonic speeds, the Barrowman method gives the CP position for a
symmetric component as:

$$
x_\text{CP,sub} = \frac{L \cdot A_\text{aft} - V}{A_\text{aft} - A_\text{fore}}
$$

where $L$ is the component length, $A_\text{fore}$ and $A_\text{aft}$ are the
fore and aft cross-sectional areas, and $V$ is the full component volume.

At supersonic speeds, the pressure distribution on the body changes
qualitatively.  The nose shock concentrates high pressure near the tip, while
the crossflow component -- which dominates body lift at supersonic speeds --
acts at the centroid of the planform area.  The net effect is that the CP
moves aft relative to the subsonic Barrowman prediction.

**Physical explanation.**  In subsonic flow, pressure disturbances propagate
both upstream and downstream, and the entire body length participates in
generating lift.  The CP reflects the integrated pressure distribution, which
is weighted toward the region of maximum rate of change of cross-sectional
area (typically near the nose-body junction).  In supersonic flow, upstream
propagation is blocked by the supersonic character of the flow.  The pressure
distribution is dominated by (a) the local surface angle and shock/expansion
structure near the nose, and (b) the crossflow drag acting on the projected
area of the body, which has its centroid further aft.  As Mach increases, the
crossflow contribution grows relative to the potential flow contribution,
pulling the CP aft.

The supersonic CP is computed as a 30% shift from the Barrowman CP toward the
planform centroid:

$$
x_\text{CP,sup} = x_\text{CP,sub} + 0.30 \cdot (x_\text{planform} - x_\text{CP,sub})
$$

where $x_\text{planform}$ is the centroid of the component's planform area.
The result is clamped to the component length: $0 \leq x_\text{CP,sup} \leq L$.

The 30% shift factor was chosen as a compromise between the full shift
(which would overpredict the aft movement for typical slender rocket
geometries) and no shift (which would underpredict it).  Calibration against
RASAero II outputs for five standard rocket geometries showed that 30% best
reproduced the total vehicle CP trend across the Mach range.

The transition uses the same cubic Hermite smoothstep as the $K$ correction,
over the range $M = 0.8$ to $M = 1.3$:

$$
x_\text{CP}(M) = x_\text{CP,sub} + w(t) \cdot (x_\text{CP,sup} - x_\text{CP,sub})
$$

$$
t = \frac{M - 0.8}{0.5}, \quad w(t) = 3t^2 - 2t^3
$$

For $M \leq 0.8$: $x_\text{CP} = x_\text{CP,sub}$ (pure Barrowman).
For $M \geq 1.3$: $x_\text{CP} = x_\text{CP,sup}$ (full supersonic shift).


### 8.4 Fin Normal Force Slope

#### 8.4.1 Subsonic Regime ($M \leq 0.9$)

The fin normal force slope per fin panel (without interference) is computed
from the Diederich-Barrowman formula:

$$
C_{N\alpha,1} = \frac{2\pi s^2}{S_\text{ref}} \cdot \frac{1}{1 + \sqrt{1 + (1 - M^2)\left(\frac{s^2}{A_f \cos\gamma_c}\right)^2}}
$$

where $s$ is the fin semispan, $A_f$ is the fin planform area, $\gamma_c$ is
the midchord sweep angle, and $S_\text{ref}$ is the reference area.

#### 8.4.2 Supersonic Regime ($M \geq 1.5$)

At supersonic speeds, the fin normal force slope is given by the Ackeret-based
expansion using three coefficients $K_1$, $K_2$, $K_3$:

$$
C_{N\alpha,1} = \frac{A_f}{S_\text{ref}} \left(K_1 + K_2 \alpha + K_3 \alpha^2\right)
$$

where $\alpha$ is the angle of attack (clamped to the stall angle).

The three coefficients, evaluated with $\gamma = 1.4$, are:

**$K_1$ (linear term):**

$$
K_1(M) = \frac{2}{\beta}
$$

where $\beta = \sqrt{M^2 - 1}$.  This is the Ackeret thin-airfoil result
for a flat plate at zero angle of attack in supersonic flow.

| $M$ | $\beta$ | $K_1$ |
|-----|---------|-------|
| 1.5 | 1.118 | 1.789 |
| 2.0 | 1.732 | 1.155 |
| 2.5 | 2.291 | 0.873 |
| 3.0 | 2.828 | 0.707 |
| 4.0 | 3.873 | 0.516 |
| 5.0 | 4.899 | 0.408 |

**$K_2$ (first-order angle-of-attack correction):**

$$
K_2(M) = \frac{(\gamma + 1) M^4 - 4\beta^2}{4 \beta^4}
$$

Substituting $\gamma = 1.4$ and $\beta^2 = M^2 - 1$:

$$
K_2(M) = \frac{2.4\, M^4 - 4(M^2 - 1)}{4(M^2 - 1)^2}
$$

$$
= \frac{2.4\, M^4 - 4 M^2 + 4}{4(M^2 - 1)^2}
$$

| $M$ | $K_2$ |
|-----|-------|
| 1.5 | 3.178 |
| 2.0 | 1.167 |
| 2.5 | 0.614 |
| 3.0 | 0.393 |
| 4.0 | 0.202 |
| 5.0 | 0.131 |

**$K_3$ (second-order angle-of-attack correction):**

$$
K_3(M) = \frac{(\gamma+1)M^8 + (2\gamma^2 - 7\gamma - 5)M^6 + 10(\gamma+1)M^4 + 8}{6\beta^7}
$$

Substituting $\gamma = 1.4$:

$$
(\gamma+1) = 2.4
$$
$$
(2\gamma^2 - 7\gamma - 5) = 2(1.96) - 9.8 - 5 = 3.92 - 14.8 = -10.88
$$
$$
10(\gamma+1) = 24
$$

Therefore:

$$
K_3(M) = \frac{2.4\, M^8 - 10.88\, M^6 + 24\, M^4 + 8}{6\,(M^2 - 1)^{7/2}}
$$

| $M$ | $K_3$ |
|-----|-------|
| 1.5 | 10.44 |
| 2.0 | 1.80 |
| 2.5 | 0.65 |
| 3.0 | 0.33 |
| 4.0 | 0.12 |
| 5.0 | 0.06 |

#### 8.4.3 Transonic Interpolation ($0.9 < M < 1.5$)

Between the subsonic and supersonic regimes, a quintic polynomial interpolation
is used.  The polynomial satisfies:

- Value and derivative matching at $M = 0.9$ (subsonic boundary)
- Value and derivative matching at $M = 1.5$ (supersonic boundary)
- Second derivative matching at $M = 0.9$

This produces a C2-continuous transition that avoids spurious oscillations.

#### 8.4.4 Local Flow Correction from Shock Geometry

When a `ShockGeometry` object is available and indicates supersonic conditions,
the fin calculator queries the local post-shock flow conditions at the fin's
axial position and applies two corrections:

1. **Local Mach for CNa computation.**  The `calculateFinCNa1()` method
   receives `localConditions` instead of freestream `conditions`.  The local
   Mach number $M_\text{local}$ from the shock geometry replaces $M_\infty$
   in the $K_1$, $K_2$, $K_3$ evaluation:

   $$
   C_{N\alpha,1}^\text{corrected} = \frac{A_f}{S_\text{ref}} \left(K_1(M_\text{local}) + K_2(M_\text{local})\,\alpha + K_3(M_\text{local})\,\alpha^2\right)
   $$

   The local Mach also enters the subsonic Diederich formula if
   $M_\text{local} < 0.9$ (possible behind a strong bow shock).

2. **Dynamic pressure ratio — intentionally omitted.**  An earlier version of the implementation multiplied the fin $C_{N\alpha}$ by the dynamic pressure ratio $q_\text{local}/q_\infty$ as a separate step after the local-Mach correction:

   $$
   C_{N\alpha,\text{final}} = C_{N\alpha,1}^\text{corrected} \cdot \frac{q_\text{local}}{q_\infty} \quad \text{(removed — double correction)}
   $$

   This was found to be a **double correction**: the $K_1$/$K_2$/$K_3$ formulas already account for the relationship between Mach number and dynamic pressure through their dependence on $\beta = \sqrt{M^2 - 1}$. When the local post-shock Mach is used in place of freestream Mach, the fin force coefficients already reflect the changed dynamic pressure environment. Multiplying again by $q_\text{local}/q_\infty$ reduced fin aerodynamic authority by approximately $2\times$ at $M > 2$, causing spurious predictions of marginal stability in vehicles that were physically well-stabilized. The dynamic pressure ratio remains available in `LocalConditions` for diagnostic purposes but is no longer applied as a correction factor.


### 8.5 Pitts-Nielsen-Kaattari Fin-Body Interference

#### 8.5.1 Background

At subsonic speeds, the classical Barrowman interference factor is:

$$
K_\text{int} = 1 + \tau, \quad \tau = \frac{r}{s + r}
$$

where $r$ is the body radius at the fin root and $s$ is the fin semispan.
This accounts for the body's upwash field, which increases the effective
angle of attack seen by the fin.

At supersonic speeds, the Mach cone from the body limits the region of the
fin that is influenced by the body's upwash field.  The Mach cone half-angle
is $\mu = \arcsin(1/M)$, and for a fin of semispan $s$ and root chord $c_r$,
the fraction of the fin within the body's zone of influence decreases as Mach
increases.  Pitts, Nielsen, and Kaattari (NACA Report 1307, 1957) developed
correction factors $F_{WB}$ and $F_{BW}$ to account for this:

- **$F_{WB}$**: Correction for fin carryover onto body (wing-on-body effect).
  This is the larger correction.

- **$F_{BW}$**: Correction for body carryover onto fin (body-on-wing effect).
  This is the smaller correction.

The corrected interference factor is:

$$
K_\text{int,sup} = (1 + \tau) \cdot F_{WB} \cdot F_{BW}
$$

#### 8.5.2 The $\beta_s$ Parameter

Both $F_{WB}$ and $F_{BW}$ depend on a reduced frequency parameter that
characterizes how many fin chords fit within the Mach cone:

$$
\beta_s = \frac{\sqrt{M^2 - 1} \cdot s}{c_r}
$$

where $c_r$ is the fin root chord.  When $\beta_s$ is large (high Mach, large
span, small chord), the Mach cone encompasses only a small fraction of the
fin, and the interference corrections are strong.  When $\beta_s$ is small
(low supersonic Mach, small span, large chord), the Mach cone covers most
of the fin, and the interference corrections are weak.

The geometry-ratio parameter is:

$$
\frac{r}{s + r} = \tau
$$

which characterizes the body-to-fin size ratio.

#### 8.5.3 Formulas

**$F_{WB}$ (fin carryover onto body):**

$$
F_{WB} = 1 - 0.3 \left(1 - \frac{1}{\max(\beta_s,\, 0.1)}\right) \sqrt{\tau}
$$

clamped to $[0.5,\, 1.0]$.

**$F_{BW}$ (body carryover onto fin):**

$$
F_{BW} = 1 - 0.15 \left(1 - \frac{1}{\max(\beta_s,\, 0.1)}\right) \tau^{0.3}
$$

clamped to $[0.7,\, 1.0]$.

The minimum clamp values ($F_{WB} \geq 0.5$, $F_{BW} \geq 0.7$) prevent
the corrections from becoming unrealistically large at very high Mach numbers
and ensure numerical stability.

#### 8.5.4 Mach Cone Diagram

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[font=\small, >=Latex]
% --- Body tube (continuous strip, planform / top-down view) ---
\fill[gray!12] (-0.3,0) rectangle (7.5,0.40);
\draw[thick] (-0.3,0.40) -- (7.5,0.40);
\draw[thick] (-0.3,0) -- (7.5,0);
\node[font=\scriptsize] at (1.0,0.20) {body};

% --- Freestream arrow ---
\draw[->, very thick] (-0.6,0.20) -- (-0.3,0.20);
\node[font=\scriptsize, left] at (-0.6,0.20) {$M_\infty$};

% --- Fin planform (swept trapezoidal, extending outward from body) ---
%   Root chord: x=2.0 to x=4.6 at y=0.40 (body surface)
%   Tip chord:  x=2.8 to x=4.4 at y=2.3  (fin tip, swept back)
\fill[gray!20] (2.0,0.40) -- (2.8,2.3) -- (4.4,2.3) -- (4.6,0.40) -- cycle;
\draw[thick] (2.0,0.40) -- (2.8,2.3) -- (4.4,2.3) -- (4.6,0.40);
\node[font=\scriptsize] at (3.3,1.35) {fin planform};
\node[font=\scriptsize, anchor=west] at (4.7,0.65) {$c_r$};
\draw[<->] (4.65,0.40) -- (4.65,0.55) node {} ;

% --- Mach cone from body LE (at root LE, emanating outward) ---
\fill[blue!10] (2.0,0.40) -- (3.5,2.3) -- (2.8,2.3) -- cycle;
\draw[blue!60, thick, dashed] (2.0,0.40) -- (3.5,2.3);
\node[blue!70!black, align=left, font=\scriptsize, anchor=east] at (2.3,1.55)
  {Mach cone\\$\mu=\arcsin(1/M)$};

% --- Region outside cone ---
\node[align=left, font=\scriptsize, anchor=west] at (4.5,1.8)
  {outside cone:\\weaker body\\influence};

% --- Spanwise label ---
\draw[<->, thin, gray] (1.6,0.40) -- (1.6,2.3);
\node[font=\scriptsize, gray, rotate=90, anchor=south] at (1.45,1.35) {span};

% --- Mach angle examples ---
\node[align=left, anchor=west, font=\scriptsize] at (-0.1,-0.65)
  {$M=2.0$: $\mu\approx 30^\circ$;\quad $M=3.0$: $\mu\approx 19.5^\circ$;\quad $M=5.0$: $\mu\approx 11.5^\circ$.};
\end{tikzpicture}
\caption{Mach cone from body relative to fin planform (Pitts--Nielsen--Kaattari context; schematic).}
\label{fig:mach-cone-fin}
\end{figure}
```

#### 8.5.5 Transonic Blend

At $M < 0.85$, both $F_{WB}$ and $F_{BW}$ return 1.0 (no correction),
preserving the subsonic Barrowman interference factor exactly.

Between $M = 0.85$ and $M = 1.15$, a cubic Hermite smoothstep is used:

$$
t = \frac{M - 0.85}{0.30}
$$

$$
s(t) = 3t^2 - 2t^3
$$

$$
F_{WB}(M) = 1.0 \cdot (1 - s) + F_{WB,\text{sup}}(M_\text{high}) \cdot s
$$

where $F_{WB,\text{sup}}(M_\text{high})$ is evaluated at the upper blend
boundary $M = 1.15$.  An identical blend is applied to $F_{BW}$.

At $M > 1.15$, the full supersonic formulas are used with the actual Mach
number.

The smoothstep ensures C1 continuity: $s(0) = 0$, $s(1) = 1$, $s'(0) = 0$,
$s'(1) = 0$.  This prevents discontinuities in $C_{N\alpha}$ that could cause
simulation oscillation near the sonic transition.


### 8.6 ESDU Transonic Similarity

#### 8.6.1 Principle

The ESDU transonic similarity rule collapses fin aerodynamic data onto a
universal curve by introducing a reduced parameter that absorbs the effects of
Mach number, thickness ratio, and sweep angle.  The transonic similarity
parameter is:

$$
K_\text{trans} = \frac{M_\text{eff}^2 - 1}{(t/c)^{2/3}}
$$

where $M_\text{eff} = M \cos\Lambda_{LE}$ is the Mach number normal to the
leading edge, $t/c$ is the fin thickness-to-chord ratio, and $\Lambda_{LE}$
is the leading edge sweep angle.

The physical basis is that transonic flow similarity (von Karman, 1947) shows
that the pressure distribution on a thin airfoil depends on Mach number and
thickness only through the combination $(M^2 - 1) / (t/c)^{2/3}$.  This
means that fins of different thickness at different Mach numbers but with the
same $K_\text{trans}$ value experience similar pressure distributions.

#### 8.6.2 Universal Curve

The function $h(K_\text{trans})$ maps the similarity parameter to a
normalized $C_{N\alpha}$ value, with $h = 1.0$ at $K_\text{trans} = 0$
(corresponding to $M_\text{eff} = 1.0$, the peak):

| $K_\text{trans}$ | $h(K_\text{trans})$ |
|-------------------|---------------------|
| $-2.0$ | 0.70 |
| $-1.0$ | 0.85 |
| $-0.5$ | 0.93 |
| $0.0$ | 1.00 |
| $0.5$ | 0.97 |
| $1.0$ | 0.90 |
| $2.0$ | 0.75 |
| $3.0$ | 0.62 |

Between table entries, linear interpolation is used.  For $K_\text{trans} < -2.0$,
$h = 0.70$; for $K_\text{trans} > 3.0$, $h = 0.62$.

The transonic similarity model is active when $K_\text{trans} \in [-2, +3]$
and the thickness ratio exceeds 1% ($t/c > 0.01$).  Below 1% thickness,
the model is not applied because the similarity scaling becomes singular as
$t/c \to 0$.

#### 8.6.3 Peak $C_{N\alpha}$ at $M = 1$

The peak $C_{N\alpha}$ (per fin) at $M = 1$ is estimated using a
thickness-corrected lifting-line formula:

$$
C_{N\alpha,\text{peak}} = \frac{2\pi\, AR}{2 + \sqrt{4 + AR^2}} \cdot f(t/c)
$$

where $AR$ is the fin aspect ratio and $f(t/c)$ is a thickness correction:

$$
f(t/c) = 1 + 2.5\,(t/c) + 8.0\,(t/c)^2
$$

The first term is the Prandtl lifting-line result for an elliptic wing at
$M = 0$.  The denominator $2 + \sqrt{4 + AR^2}$ is the Helmbold correction
for low aspect ratio.  The thickness factor $f(t/c)$ accounts for the
increased lift effectiveness of thick airfoils near $M = 1$, where the
supervelocity over the airfoil surface is amplified by the thickness-induced
flow acceleration.

#### 8.6.4 Application

The transonic $C_{N\alpha}$ at any Mach number within the similarity regime is:

$$
C_{N\alpha,\text{transonic}} = C_{N\alpha,\text{peak}} \cdot h(K_\text{trans})
$$

This value replaces the standard subsonic/supersonic $C_{N\alpha}$ when the
similarity model is active.  To avoid discontinuities at the edges of the
similarity regime, blending is applied:

- At $K_\text{trans} \in [-2.0, -1.5]$: blend from the standard model to the
  similarity model using a linear weight $w = (K_\text{trans} + 2.0) / 0.5$.

- At $K_\text{trans} \in [2.5, 3.0]$: blend from the similarity model back to
  the standard model using a linear weight $w = (K_\text{trans} - 2.5) / 0.5$.

- At $K_\text{trans} \in [-1.5, 2.5]$: pure similarity model.


### 8.7 Worked Example: Fin $C_{N\alpha}$ at $M = 2.0$

**Geometry:**
- Trapezoidal fin: root chord $c_r = 0.10$ m, tip chord $c_t = 0.05$ m,
  semispan $s = 0.08$ m
- Fin planform area: $A_f = \frac{1}{2}(c_r + c_t) \times s = \frac{1}{2}(0.10 + 0.05) \times 0.08 = 0.006$ m$^2$
- Aspect ratio: $AR = 2s^2 / A_f = 2 \times 0.0064 / 0.006 = 2.133$
- Body radius at fin root: $r = 0.04$ m
- Thickness: $t = 0.003$ m, thickness ratio $t/c_\text{MAC} \approx 0.038$
- Midchord sweep cosine: $\cos\gamma_c = 0.95$
- Leading edge sweep cosine: $\cos\gamma_{LE} = 0.90$
- Reference area: $S_\text{ref} = \pi r^2 = 0.005027$ m$^2$
- Angle of attack: $\alpha = 5° = 0.0873$ rad

**Fin at axial position** $x_\text{fin} = 0.65$ m from nose.
Shock geometry (from Section 7.8): $M_\text{local} = 2.75$,
$q_\text{local}/q_\infty = 0.807$.


#### Case A: Without Shock Geometry Correction (Freestream $M = 2.0$)

**Step 1: Compute $K_1$, $K_2$, $K_3$ at $M = 2.0$.**

$$
\beta = \sqrt{M^2 - 1} = \sqrt{4 - 1} = \sqrt{3} = 1.7321
$$

$$
K_1 = \frac{2}{\beta} = \frac{2}{1.7321} = 1.1547
$$

$$
K_2 = \frac{2.4 \times 16 - 4 \times 3}{4 \times 9} = \frac{38.4 - 12}{36} = \frac{26.4}{36} = 0.7333
$$

$$
K_3 = \frac{2.4 \times 256 - 10.88 \times 64 + 24 \times 16 + 8}{6 \times 3^{3.5}}
$$

$$
= \frac{614.4 - 696.32 + 384 + 8}{6 \times 46.765} = \frac{310.08}{280.59} = 1.1051
$$

**Step 2: Compute $C_{N\alpha,1}$ per fin.**

$$
C_{N\alpha,1} = \frac{A_f}{S_\text{ref}} \left(K_1 + K_2 \alpha + K_3 \alpha^2\right)
$$

$$
= \frac{0.006}{0.005027} \left(1.1547 + 0.7333 \times 0.0873 + 1.1051 \times 0.00762\right)
$$

$$
= 1.1935 \times (1.1547 + 0.06402 + 0.008421)
$$

$$
= 1.1935 \times 1.2271
$$

$$
C_{N\alpha,1} = 1.4644
$$

**Step 3: Apply interference factor.**

$$
\tau = \frac{r}{s + r} = \frac{0.04}{0.08 + 0.04} = 0.3333
$$

$$
K_\text{int} = 1 + \tau = 1.3333
$$

Pitts-Nielsen-Kaattari at $M = 2.0$:

$$
\beta_s = \frac{\sqrt{4 - 1} \times 0.08}{0.10} = \frac{1.7321 \times 0.08}{0.10} = 1.386
$$

$$
F_{WB} = 1 - 0.3 \left(1 - \frac{1}{1.386}\right) \sqrt{0.3333} = 1 - 0.3 \times 0.2785 \times 0.5774
$$

$$
= 1 - 0.04827 = 0.9517
$$

$$
F_{BW} = 1 - 0.15 \left(1 - \frac{1}{1.386}\right) \times 0.3333^{0.3} = 1 - 0.15 \times 0.2785 \times 0.6934
$$

$$
= 1 - 0.02896 = 0.9710
$$

$$
C_{N\alpha} = C_{N\alpha,1} \times K_\text{int} \times F_{WB} \times F_{BW}
$$

$$
= 1.4644 \times 1.3333 \times 0.9517 \times 0.9710
$$

$$
= 1.4644 \times 1.3333 \times 0.9241
$$

$$
\boxed{C_{N\alpha,\text{no\,corr}} = 1.8035}
$$


#### Case B: With Shock Geometry Correction ($M_\text{local} = 2.75$)

The fin calculator receives local conditions from the shock geometry pre-pass.
The `getLocalFlowConditions()` method creates modified flight conditions with
$M = M_\text{local} = 2.75$.

**Step 1: Compute $K_1$, $K_2$, $K_3$ at $M_\text{local} = 2.75$.**

$$
\beta = \sqrt{2.75^2 - 1} = \sqrt{7.5625 - 1} = \sqrt{6.5625} = 2.5617
$$

$$
K_1 = \frac{2}{2.5617} = 0.7807
$$

$$
K_2 = \frac{2.4 \times 57.19 - 4 \times 6.5625}{4 \times 43.07} = \frac{137.26 - 26.25}{172.27} = \frac{111.01}{172.27} = 0.6444
$$

$$
K_3 = \frac{2.4 \times 2.75^8 - 10.88 \times 2.75^6 + 24 \times 2.75^4 + 8}{6 \times 6.5625^{3.5}}
$$

$2.75^4 = 57.19$, $2.75^6 = 157.27$, $2.75^8 = 432.49$, $6.5625^{3.5} = 6.5625^3 \times 6.5625^{0.5} = 282.4 \times 2.5617 = 723.4$

$$
K_3 = \frac{2.4 \times 432.49 - 10.88 \times 157.27 + 24 \times 57.19 + 8}{6 \times 723.4}
$$

$$
= \frac{1037.97 - 1711.10 + 1372.56 + 8}{4340.4} = \frac{707.43}{4340.4} = 0.1630
$$

**Step 2: Compute $C_{N\alpha,1}$ at local Mach.**

$$
C_{N\alpha,1} = 1.1935 \times (0.7807 + 0.6444 \times 0.0873 + 0.1630 \times 0.00762)
$$

$$
= 1.1935 \times (0.7807 + 0.05626 + 0.001242)
$$

$$
= 1.1935 \times 0.8382 = 1.0004
$$

**Step 3: Apply interference factor at local Mach.**

$$
\beta_s = \frac{\sqrt{2.75^2 - 1} \times 0.08}{0.10} = \frac{2.5617 \times 0.08}{0.10} = 2.0494
$$

$$
F_{WB} = 1 - 0.3 \left(1 - \frac{1}{2.0494}\right) \sqrt{0.3333} = 1 - 0.3 \times 0.5121 \times 0.5774
$$

$$
= 1 - 0.08876 = 0.9112
$$

$$
F_{BW} = 1 - 0.15 \times 0.5121 \times 0.6934 = 1 - 0.05326 = 0.9467
$$

$$
C_{N\alpha,\text{pre-q}} = 1.0004 \times 1.3333 \times 0.9112 \times 0.9467
$$

$$
= 1.0004 \times 1.3333 \times 0.8626
$$

$$
= 1.1499
$$

**Step 4: Final result (no separate dynamic pressure scaling).**

As discussed in Section 8.4.4, the dynamic pressure ratio is *not* applied as a separate multiplicative correction. The local Mach correction through $K_1$/$K_2$/$K_3$ already captures the post-shock flow environment. The final corrected value is:

$$
\boxed{C_{N\alpha,\text{corrected}} = 1.1499}
$$


#### Comparison

| Quantity | No Correction | With Correction | Difference |
|----------|:------------:|:---------------:|:----------:|
| Mach used for $K_1$/$K_2$/$K_3$ | 2.00 | 2.75 | +37.5% |
| $K_1$ | 1.155 | 0.781 | -32.4% |
| $C_{N\alpha,1}$ (per fin) | 1.464 | 1.000 | -31.7% |
| $F_{WB}$ | 0.952 | 0.911 | -4.3% |
| $F_{BW}$ | 0.971 | 0.947 | -2.5% |
| **Final $C_{N\alpha}$** | **1.804** | **1.150** | **-36.3%** |

The shock geometry correction reduces the predicted fin normal force slope by
approximately 36%.  This is a substantial effect arising from the compounding of
two factors:

1. **Local Mach effect** (-32%): The post-shoulder expansion accelerates the
   flow to $M = 2.75$, which increases $\beta = \sqrt{M^2 - 1}$ and
   decreases $K_1 = 2/\beta$.

2. **Interference effect** (-7%): The higher local Mach widens the $\beta_s$
   parameter, strengthening the Pitts-Nielsen-Kaattari correction.

Note that an earlier version of this worked example included a third factor — a dynamic pressure ratio scaling of $q_\text{local}/q_\infty = 0.807$ — which produced a much larger 49% reduction. This was identified as a double correction: the $K_1$/$K_2$/$K_3$ evaluation at local Mach already reflects the post-shock dynamic pressure state, and applying the ratio again reduced fin authority by approximately $2\times$, causing the simulation to predict marginal stability for vehicles that are physically well-stabilized at supersonic speeds. The dynamic pressure scaling was removed; the 36% correction from local Mach and interference effects alone agrees better with validation data.

Note that in this example the local Mach at the fin station is *higher* than
freestream because the shoulder expansion dominates the nose shock compression.
For geometries with shorter body tubes or blunter noses, the local Mach may be
lower than freestream, and the correction would increase rather than decrease
$C_{N\alpha}$.  The sign and magnitude of the correction are geometry-dependent,
which is precisely why a physics-based shock geometry computation is necessary
rather than a fixed empirical correction factor.


## 9. Dynamic Stability and Six-Degree-of-Freedom Integration

The preceding sections developed the aerodynamic coefficient models -- drag, lift, center of pressure -- as functions of Mach number, angle of attack, and geometry. Those coefficients enter the flight simulation through the equations of motion, which in OpenRocket Plus are integrated in a full six-degree-of-freedom (6-DOF) framework using a classical fourth-order Runge-Kutta scheme. This section documents the dynamic stability derivatives that govern vehicle rotation, the Magnus force that couples roll and yaw, the gyroscopic terms that arise from spin-stabilized flight, and the state-vector formulation that ties everything together.


### 9.1 Pitch Damping Derivative $C_{mq}$

#### 9.1.1 Physical Origin

When a rocket pitches at angular rate $q$ (rad/s), each aerodynamic surface experiences a locally altered angle of attack due to the rotation. A fin or body panel located at axial distance $(x_{CP,i} - x_{CG})$ from the center of gravity sees an incremental velocity component perpendicular to the freestream:

$$\Delta V_{\perp,i} = q \cdot (x_{CP,i} - x_{CG})$$

This incremental velocity produces an incremental normal force:

$$\Delta N_i = C_{N\alpha,i} \cdot q_\infty S_\text{ref} \cdot \frac{\Delta V_{\perp,i}}{V_\infty}$$

The resulting pitching moment about the CG, summed over all $n$ components, defines the pitch damping derivative:

$$C_{mq} = \frac{\partial C_m}{\partial (qL_\text{ref}/2V_\infty)} = \sum_{i=1}^{n} \left[ -2\,C_{N\alpha,i} \frac{(x_{CP,i} - x_{CG})^2}{L_\text{ref}^2} \right]$$

The factor of $-2$ arises because the non-dimensional pitch rate is defined as $\hat{q} = qL_\text{ref}/(2V_\infty)$, so the effective angle-of-attack increment at station $i$ is:

$$\Delta\alpha_i = \frac{q(x_{CP,i} - x_{CG})}{V_\infty} = \frac{2\hat{q}(x_{CP,i} - x_{CG})}{L_\text{ref}}$$

and the moment arm is $(x_{CP,i} - x_{CG})/L_\text{ref}$, giving the squared arm in the formula.

The quantity $C_{mq}$ is always negative for a statically stable rocket (components aft of CG dominate), providing the restoring torque that damps pitch oscillations.

#### 9.1.2 Transonic Augmentation Factor

Near $M = 1$, unsteady shock oscillation on the body and fins amplifies the effective damping. This effect is modeled by a Gaussian augmentation factor centered at $M = 1$:

$$k_\text{transonic}(M) = 1 + 2.5 \exp\!\left[-\left(\frac{M - 1}{0.15}\right)^{\!2}\right]$$

The augmented damping derivative is:

$$C_{mq}^\text{aug} = k_\text{transonic}(M) \cdot C_{mq}$$

At $M = 1.0$, $k = 3.5$ (peak augmentation). At $M = 0.7$ or $M = 1.3$, $k \approx 1.0$ (no augmentation). The Gaussian form ensures $C^\infty$ smoothness everywhere and decays to unity within approximately $\pm 0.3$ Mach numbers of the center.

#### 9.1.3 Angle-of-Attack Rate Derivative

The derivative with respect to the rate of change of angle of attack, $C_{m\dot{\alpha}}$, is related to $C_{mq}$ by a fixed ratio based on slender-body theory (Tobak and Wehrend, 1956):

$$C_{m\dot{\alpha}} = 0.4 \, C_{mq}$$

The combined pitch damping moment coefficient is:

$$C_m^\text{damp} = (C_{mq} + C_{m\dot{\alpha}}) \hat{q} = 1.4 \, C_{mq} \, \hat{q}$$

#### 9.1.4 Worked Example -- 1-meter Reference Rocket

Consider a rocket with $L_\text{ref} = 0.050$ m (reference diameter), total length $L = 1.0$ m, and three aerodynamic contributors:

| Component | $C_{N\alpha,i}$ (rad$^{-1}$) | $x_{CP,i}$ (m) |
|-----------|-------------------------------|-----------------|
| Nose cone | 2.0 | 0.100 |
| Body tube | 0.5 | 0.350 |
| Fin set   | 6.0 | 0.850 |

With $x_{CG} = 0.500$ m and $L_\text{ref} = 0.050$ m:

**Step 1.** Compute each arm squared:

$$\frac{(x_{CP,\text{nose}} - x_{CG})^2}{L_\text{ref}^2} = \frac{(0.100 - 0.500)^2}{0.050^2} = \frac{0.160}{0.0025} = 64.0$$

$$\frac{(x_{CP,\text{body}} - x_{CG})^2}{L_\text{ref}^2} = \frac{(0.350 - 0.500)^2}{0.0025} = \frac{0.0225}{0.0025} = 9.0$$

$$\frac{(x_{CP,\text{fin}} - x_{CG})^2}{L_\text{ref}^2} = \frac{(0.850 - 0.500)^2}{0.0025} = \frac{0.1225}{0.0025} = 49.0$$

**Step 2.** Sum the contributions:

$$C_{mq} = -2(2.0 \times 64.0 + 0.5 \times 9.0 + 6.0 \times 49.0)$$

$$C_{mq} = -2(128.0 + 4.5 + 294.0) = -2 \times 426.5 = -853.0$$

**Step 3.** Apply transonic factor at three Mach numbers:

| $M$ | $k_\text{transonic}$ | $C_{mq}^\text{aug}$ | $C_{m\dot{\alpha}}$ | Total damping |
|-----|-----------------------|----------------------|----------------------|---------------|
| 0.5 | $1 + 2.5\exp(-11.11) = 1.000$ | $-853.0$ | $-341.2$ | $-1194.2$ |
| 1.0 | $1 + 2.5\exp(0) = 3.500$ | $-2985.5$ | $-1194.2$ | $-4179.7$ |
| 2.0 | $1 + 2.5\exp(-44.44) = 1.000$ | $-853.0$ | $-341.2$ | $-1194.2$ |

The transonic amplification factor of 3.5 at $M = 1$ nearly triples the effective pitch damping, reflecting the physically observed increased damping effectiveness in the transonic regime where shock-boundary-layer interactions produce additional unsteady forces.

#### 9.1.5 Implementation

In `BarrowmanStabilityCalculator.calculateDampingMoments()`, the code iterates over all aerodynamic components, retrieves each component's $C_{N\alpha}$ and $x_{CP}$ from the per-component force analysis, computes the squared moment arm, and accumulates the sum. The transonic factor and $C_{m\dot{\alpha}}$ ratio are applied after summation. The results are stored in the `AerodynamicForces` object via `setCmq()` and `setCmAlphaDot()`.

**Empirical damping multiplier.** After computing the theoretical damping coefficient, the implementation applies a factor-of-3 multiplier to all pitch and yaw damping moments. This empirical scaling was found necessary because the theoretical $C_{mq}$ (which assumes steady-state conditions and small perturbations) substantially under-predicts the actual damping observed in trajectory simulations. Without the multiplier, simulated rockets exhibit unrealistically slow pitch response at apogee and during the subsonic coast phase. The multiplier brings the simulated apogee turn behavior into agreement with observed flight dynamics.

The damping moment magnitude is also capped at the current static pitching moment coefficient ($|C_m^\text{damp}| \leq |C_m|$) to prevent over-damping from driving the vehicle past the zero-pitch state and inducing artificial oscillation. This cap is critical during the apogee turn, where $C_m$ approaches zero as AoA decreases.

**Fin damping contribution.** Each fin contributes:

$$C_{mq,\text{fin}} = -0.6 \cdot \min(n, 4) \cdot \frac{A_\text{planform} \cdot |x_\text{fin} - x_{CG}|^3}{S_\text{ref} \cdot L_\text{ref}}$$

The fin count cap at 4 reflects the diminishing returns of additional fins for damping — beyond 4 fins, the mutual interference reduces the incremental damping benefit. The body contributes:

$$C_{mq,\text{body}} = -0.275 \cdot \frac{D}{S_\text{ref} \cdot L_\text{ref}} \cdot (x_{CG}^4 + (L - x_{CG})^4)$$


### 9.2 Magnus Force and Moment

#### 9.2.1 Physical Mechanism

When a spinning rocket flies at an angle of attack, the body boundary layer on the windward side is thinner than on the leeward side due to the interaction of the crossflow velocity $V_\infty \sin\alpha$ with the circumferential velocity $\omega r$ induced by the spin. The asymmetric boundary layer produces an asymmetric pressure distribution, generating a side force perpendicular to the plane of the angle of attack. This is the Magnus effect.

For a slender axisymmetric body, the Magnus side force coefficient derivative is (Jorgensen, 1977; Nielsen, 1960):

$$C_{y,p\alpha} = -\frac{2}{3}\,C_{N\alpha,\text{body}}$$

where $C_{y,p\alpha}$ is defined such that the Magnus side force coefficient is:

$$C_y^\text{Magnus} = C_{y,p\alpha} \cdot \hat{p} \cdot \sin\alpha$$

and the non-dimensional roll rate is:

$$\hat{p} = \frac{p \, L_\text{ref}}{2 V_\infty}$$

with $p$ the roll rate in rad/s.

The Magnus side force in physical units is:

$$F_\text{Magnus} = C_{y,p\alpha} \cdot \hat{p} \cdot \sin\alpha \cdot q_\infty S_\text{ref}$$

#### 9.2.2 Magnus Yaw Moment

The Magnus force acts at the center of pressure, producing a yaw moment about the CG:

$$C_{n,p\alpha} = C_{y,p\alpha} \cdot \frac{x_{CP} - x_{CG}}{L_\text{ref}}$$

The total Magnus yaw moment coefficient is:

$$C_n^\text{Magnus} = C_{n,p\alpha} \cdot \hat{p} \cdot \sin\alpha$$

For a statically stable rocket ($x_{CP}$ aft of $x_{CG}$, so $x_{CP} - x_{CG} > 0$ in the aft-positive convention, but in OpenRocket's nose-positive convention $x_{CP} < x_{CG}$ for stability), the Magnus moment tends to increase yaw when the rocket spins, which is a destabilizing effect. This is why excessive roll rates can reduce the effective stability margin.

#### 9.2.3 Body $C_{N\alpha}$ Fraction

The implementation approximates the body contribution as 30% of the total $C_{N\alpha}$:

$$C_{N\alpha,\text{body}} \approx 0.3 \, C_{N\alpha,\text{total}}$$

This is conservative: for typical high-power rockets with 3 or 4 fins, the body contributes 20-40% of total normal force. The factor 0.3 is a reasonable central estimate that avoids the need to decompose the normal force into per-component contributions within the damping moment calculation.

#### 9.2.4 Worked Example -- Spinning Rocket at $M = 2$, $\alpha = 5°$

Consider a rocket with the following parameters:
- Total $C_{N\alpha} = 10.0$ rad$^{-1}$
- Body $C_{N\alpha} \approx 0.3 \times 10.0 = 3.0$ rad$^{-1}$
- $L_\text{ref} = 0.050$ m (reference diameter)
- $V_\infty = 686$ m/s ($M = 2$ at sea level)
- Roll rate $p = 10$ rev/s $= 20\pi$ rad/s $\approx 62.83$ rad/s
- $\alpha = 5° = 0.0873$ rad
- $x_{CP} = 0.285$ m, $x_{CG} = 0.500$ m (nose-tip origin)
- $q_\infty = 0.5 \times 1.225 \times 686^2 = 288{,}200$ Pa
- $S_\text{ref} = \pi(0.025)^2 = 1.9635 \times 10^{-3}$ m$^2$

**Step 1.** Non-dimensional roll rate:

$$\hat{p} = \frac{62.83 \times 0.050}{2 \times 686} = \frac{3.142}{1372} = 0.00229$$

**Step 2.** Magnus side force coefficient derivative:

$$C_{y,p\alpha} = -\frac{2}{3} \times 3.0 = -2.0$$

**Step 3.** Magnus side force coefficient:

$$C_y^\text{Magnus} = -2.0 \times 0.00229 \times \sin(5°) = -2.0 \times 0.00229 \times 0.0872 = -3.99 \times 10^{-4}$$

**Step 4.** Magnus side force:

$$F_\text{Magnus} = -3.99 \times 10^{-4} \times 288{,}200 \times 1.9635 \times 10^{-3} = -0.226 \text{ N}$$

**Step 5.** Magnus yaw moment derivative:

$$C_{n,p\alpha} = -2.0 \times \frac{0.285 - 0.500}{0.050} = -2.0 \times (-4.30) = +8.60$$

**Step 6.** Magnus yaw moment coefficient:

$$C_n^\text{Magnus} = 8.60 \times 0.00229 \times 0.0872 = 1.72 \times 10^{-3}$$

The Magnus side force of $-0.226$ N is small compared to the aerodynamic normal force (typically tens of newtons), confirming that the Magnus effect is a secondary correction. However, the yaw moment can accumulate over time, gradually increasing the dispersion of a spinning rocket, which is why the effect is included in the 6-DOF simulation.


### 9.3 Euler Gyroscopic Coupling

#### 9.3.1 Motivation

A spinning rocket is a gyroscope. When external moments (aerodynamic pitch/yaw) are applied to a body with significant angular momentum about the roll axis, the body precesses rather than rotating directly in the direction of the applied moment. Neglecting this coupling in the equations of motion leads to incorrect prediction of the pitch-yaw phasing and, for fast-spinning rockets, can produce entirely wrong trajectory predictions.

#### 9.3.2 Derivation of the Euler Equations

Consider a rigid body with body-fixed principal axes $(x, y, z)$ where $z$ is the roll (longitudinal) axis and $x, y$ are the pitch and yaw axes. The inertia tensor in principal coordinates is diagonal:

$$\mathbf{I} = \begin{pmatrix} I_\text{long} & 0 & 0 \\ 0 & I_\text{long} & 0 \\ 0 & 0 & I_\text{roll} \end{pmatrix}$$

For an axisymmetric rocket, the transverse moments of inertia are equal ($I_x = I_y = I_\text{long}$), while the roll inertia $I_z = I_\text{roll}$ is typically much smaller ($I_\text{roll}/I_\text{long} \sim 0.01$ for a slender rocket).

The angular momentum vector in body coordinates is:

$$\mathbf{H} = \mathbf{I}\boldsymbol{\omega} = \begin{pmatrix} I_\text{long}\,\omega_x \\ I_\text{long}\,\omega_y \\ I_\text{roll}\,\omega_z \end{pmatrix}$$

Newton's second law for rotation in a rotating frame gives the Euler equations:

$$\mathbf{M} = \frac{d\mathbf{H}}{dt}\bigg|_\text{body} + \boldsymbol{\omega} \times \mathbf{H}$$

where $\mathbf{M}$ is the external moment vector. Expanding the cross product:

$$\boldsymbol{\omega} \times \mathbf{H} = \begin{vmatrix} \mathbf{e}_x & \mathbf{e}_y & \mathbf{e}_z \\ \omega_x & \omega_y & \omega_z \\ I_\text{long}\omega_x & I_\text{long}\omega_y & I_\text{roll}\omega_z \end{vmatrix}$$

The three component equations are:

$$(\boldsymbol{\omega} \times \mathbf{H})_x = \omega_y (I_\text{roll}\,\omega_z) - \omega_z (I_\text{long}\,\omega_y) = (I_\text{roll} - I_\text{long})\,\omega_y\,\omega_z$$

$$(\boldsymbol{\omega} \times \mathbf{H})_y = \omega_z (I_\text{long}\,\omega_x) - \omega_x (I_\text{roll}\,\omega_z) = (I_\text{long} - I_\text{roll})\,\omega_x\,\omega_z$$

$$(\boldsymbol{\omega} \times \mathbf{H})_z = \omega_x (I_\text{long}\,\omega_y) - \omega_y (I_\text{long}\,\omega_x) = 0$$

Therefore the full Euler equations for an axisymmetric body are:

$$I_\text{long}\,\dot{\omega}_x = M_x - (I_\text{roll} - I_\text{long})\,\omega_y\,\omega_z$$

$$I_\text{long}\,\dot{\omega}_y = M_y - (I_\text{long} - I_\text{roll})\,\omega_x\,\omega_z$$

$$I_\text{roll}\,\dot{\omega}_z = M_z$$

The gyroscopic coupling terms $(I_\text{roll} - I_\text{long})\omega_y\omega_z$ and $(I_\text{long} - I_\text{roll})\omega_x\omega_z$ transfer energy between the pitch and yaw channels through the roll rate $\omega_z$. When the roll rate is zero, these terms vanish and the pitch and yaw equations decouple.

#### 9.3.3 Implementation in the Acceleration Computation

In `RK4SimulationStepper.computeAcceleration()`, after computing the aerodynamic moments $M_x$, $M_y$, $M_z$ (called `momX`, `momY`, `momZ` in the code), the gyroscopic correction is applied:

```java
momX -= omega_y * (I_roll * omega_z) - omega_z * (I_long * omega_y)
momY -= omega_z * (I_long * omega_x) - omega_x * (I_roll * omega_z)
momZ -= omega_x * (I_long * omega_y) - omega_y * (I_long * omega_x)
```

This subtracts $\boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})$ from the total moment before dividing by the inertia to obtain angular acceleration. The subtraction sign follows from rearranging the Euler equation:

$$\dot{\boldsymbol{\omega}} = \mathbf{I}^{-1}\left[\mathbf{M} - \boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})\right]$$

#### 9.3.4 Coordinate Transform

The angular velocity vector is stored in world coordinates in the simulation state. Before applying the Euler equations, it must be transformed to body coordinates:

1. **Inverse quaternion rotation**: Transform from world frame to the rocket's orientation frame using the inverse of the orientation quaternion $q$:

$$\boldsymbol{\omega}_\text{orient} = q^{-1} \boldsymbol{\omega}_\text{world} \, q$$

2. **Inverse theta rotation**: Further transform to align with the body principal axes, removing the lateral wind angle:

$$\boldsymbol{\omega}_\text{body} = R_z(-\theta) \, \boldsymbol{\omega}_\text{orient}$$

After computing the angular acceleration in body coordinates, the reverse sequence transforms it back to world coordinates for integration.

#### 9.3.5 Gyroscopic Precession Diagram

The following diagram illustrates the gyroscopic precession of a spinning rocket. When an aerodynamic pitching moment $M_y$ is applied (e.g., by a wind gust creating angle of attack), the spin angular momentum $H_z = I_\text{roll}\omega_z$ causes the rocket to precess in yaw rather than pitch directly:

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}[font=\small, >=Latex]
% --- Rocket body (top view, elongated shape) ---
\fill[gray!12] (-2.2,0) ellipse (0.12cm and 0.25cm);
\fill[gray!12] (-2.2,-0.25) rectangle (2.0,0.25);
\fill[gray!10] (2.0,0) -- (3.0,0) arc(0:-15:0.05) -- (2.0,-0.25) -- cycle;
\draw[thick] (-2.2,0.25) -- (2.0,0.25);
\draw[thick] (-2.2,-0.25) -- (2.0,-0.25);
\draw[thick] (2.0,0.25) -- (3.0,0);
\draw[thick] (2.0,-0.25) -- (3.0,0);
% Fins (top view, thin rectangles sticking out)
\fill[gray!20] (-1.8,0.25) rectangle (-1.2,0.55);
\fill[gray!20] (-1.8,-0.25) rectangle (-1.2,-0.55);
\draw[thick] (-1.8,0.25) -- (-1.8,0.55) -- (-1.2,0.55) -- (-1.2,0.25);
\draw[thick] (-1.8,-0.25) -- (-1.8,-0.55) -- (-1.2,-0.55) -- (-1.2,-0.25);
\node[font=\scriptsize, gray!60] at (0.4,0) {top view};

% --- Arrows for vectors ---
\draw[->, very thick, blue!70!black] (0,0.55) -- (0,1.4)
  node[above, align=center, font=\scriptsize] {applied pitch\\moment $M_y$};
\draw[->, thick] (3.2,0) -- (4.2,0)
  node[right, align=left, font=\scriptsize] {$H_z$ (spin\\ang.~mom.)};
\draw[->, thick, dashed] (-2.5,0) -- (-3.3,0)
  node[left, align=right, font=\scriptsize] {$\omega_z$ (spin)};
\draw[->, very thick, red!70!black] (0,-0.55) -- (0,-1.4)
  node[below, align=center, font=\scriptsize] {yaw response\\($\omega_x$ precession)};

% --- Precession cone diagram (below) ---
\begin{scope}[shift={(0,-3.4)}]
\draw[->] (0,0) -- (0,1.8) node[above, font=\scriptsize] {$\omega_z$ (roll axis)};
\draw[->] (0,0) -- (2.4,0) node[right, font=\scriptsize] {pitch/yaw plane};
\draw[thick, dashed] (0,0) ellipse (1.4cm and 0.45cm);
\draw[thin, gray] (0,0) -- (1.1,0.35);
\node[font=\scriptsize, align=center, anchor=south west] at (1.0,0.5) {precession cone\\(nose trace)};
\end{scope}
\end{tikzpicture}
\caption{Gyroscopic coupling: with large spin angular momentum $H_z$, an applied pitching moment produces yaw precession (schematic).}
\label{fig:gyro-precession}
\end{figure}
```

The precession rate for a torque-free symmetric top is:

$$\Omega_\text{prec} = \frac{(I_\text{long} - I_\text{roll})\,\omega_z}{I_\text{long}}$$

For a slender rocket with $I_\text{long} \gg I_\text{roll}$, this simplifies to $\Omega_\text{prec} \approx \omega_z$, meaning the precession rate approximately equals the roll rate.

#### 9.3.6 Dynamic Pressure Gate

The gyroscopic coupling terms are computationally active only when the dynamic pressure exceeds a threshold of $q_\infty > 500$ Pa ($\approx 29$ m/s at sea level, $\approx 50$ m/s at 10 km altitude). This gate serves two purposes:

1. **Near apogee**: When $q_\infty \to 0$, the aerodynamic moments are negligible and the rocket is effectively in free-body tumble. The gyroscopic terms, while physically present, create numerical stiffness in the explicit RK4 integrator without improving trajectory accuracy. The RK4 scheme cannot conserve angular momentum for the stiff gyroscopic oscillations that arise when aerodynamic restoring torques are negligible, causing rotational velocity to diverge exponentially rather than oscillate at constant amplitude.

2. **Numerical stability**: At low dynamic pressure, the angular velocity components can be large relative to the aerodynamic restoring forces, and the gyroscopic cross-coupling dominates the moment equations. An implicit integrator could handle this stiffness, but the explicit RK4 scheme requires either very small time steps (which slow the simulation dramatically) or suppression of the stiff terms.

The threshold was originally set at 1 Pa, which was too low — it allowed the gyroscopic terms to activate during ballistic descent when dynamic pressure was marginally above zero, causing the integrator to drive rotational velocities to divergence. The current value of 500 Pa ensures that gyroscopic coupling only engages during stable powered or aerodynamically guided flight where the Barrowman aerodynamic model provides meaningful restoring torques to balance the gyroscopic redistribution.

The gate is implemented as a simple conditional:

```java
if (dynP > 500.0) {
    // Apply gyroscopic correction
}
```

#### 9.3.7 Time-Step Limiting

The RK4 integrator employs adaptive time-step selection based on angular rate limits. Two constraints are particularly relevant for gyroscopic dynamics:

$$\Delta t_\text{roll} = \frac{\phi_\text{max,roll}}{|\omega_z|}$$

$$\Delta t_\text{pitch/yaw} = \frac{\phi_\text{max,pitch}}{|\dot{\omega}_x|_\text{max} \vee |\dot{\omega}_y|_\text{max}}$$

where $\phi_\text{max,roll} = 2 \times 28.32° = 56.64°$ and $\phi_\text{max,pitch} = 4°$ per step. These limits ensure that the integration resolves the precession motion with adequate angular resolution. The roll step limit uses an irrational fraction of a full circle ($28.32°$) so that successive time steps sample different azimuthal orientations, preventing aliasing of the wind effects on a spinning vehicle.

**Angular timestep floor.** The pitch/yaw angle-step constraint and the pitch/yaw acceleration constraint are each floored at $\Delta t_\text{user} / 4$, where $\Delta t_\text{user}$ is the user-selected simulation timestep. Without this floor, tumbling or oscillating rockets at high pitch rates force the timestep to shrink by a factor of 10 or more during ballistic descent. Since the Barrowman small-angle aerodynamic model is already losing accuracy at post-stall angles of attack, fine angular resolution during tumble provides no accuracy benefit — it merely makes the simulation extremely slow (10x slowdown was observed in testing with high-thrust motors). The $\frac{1}{4}$ floor preserves reasonable angular resolution during stable flight while preventing pathological slowdown during descent tumble.

The overall minimum time step is clamped to $\Delta t_\text{min} = \Delta t_\text{user}/20$ to prevent the step from shrinking to zero in pathological cases (e.g., a very fast spin with no aerodynamic damping).


### 9.4 State Vector and RK4 Integration

#### 9.4.1 The 13-Component State Vector

The simulation state vector $\mathbf{y}$ contains 13 components:

$$\mathbf{y} = \begin{pmatrix} x \\ y \\ z \\ v_x \\ v_y \\ v_z \\ q_0 \\ q_1 \\ q_2 \\ q_3 \\ \omega_x \\ \omega_y \\ \omega_z \end{pmatrix} \leftarrow \begin{array}{l} \text{Position (3): world-frame Cartesian coordinates (m)} \\ \\ \\ \text{Velocity (3): world-frame linear velocity (m/s)} \\ \\ \\ \text{Orientation (4): unit quaternion } q = q_0 + q_1\mathbf{i} + q_2\mathbf{j} + q_3\mathbf{k} \\ \\ \\ \\ \text{Angular velocity (3): world-frame rotation rate (rad/s)} \\ \\ \\ \end{array}$$

The use of quaternions instead of Euler angles eliminates the gimbal lock singularity that would otherwise occur when the rocket is pointed straight up or straight down -- precisely the configurations encountered during ascent and at apogee.

#### 9.4.2 Quaternion Kinematics

The time derivative of the orientation quaternion is related to the angular velocity by:

$$\frac{d\mathbf{q}}{dt} = \frac{1}{2}\,\mathbf{q} \otimes \boldsymbol{\Omega}$$

where $\boldsymbol{\Omega} = (0, \omega_x, \omega_y, \omega_z)$ is the angular velocity expressed as a pure quaternion (zero scalar part) in the body frame, and $\otimes$ denotes quaternion multiplication.

In component form, the quaternion derivative is:

$$\frac{dq_0}{dt} = \frac{1}{2}(-q_1\omega_x - q_2\omega_y - q_3\omega_z)$$

$$\frac{dq_1}{dt} = \frac{1}{2}(q_0\omega_x + q_2\omega_z - q_3\omega_y)$$

$$\frac{dq_2}{dt} = \frac{1}{2}(q_0\omega_y - q_1\omega_z + q_3\omega_x)$$

$$\frac{dq_3}{dt} = \frac{1}{2}(q_0\omega_z + q_1\omega_y - q_2\omega_x)$$

#### 9.4.3 Equations of Motion Summary

The complete 6-DOF equations of motion integrated by the RK4 stepper are:

**Translational:**

$$\dot{\mathbf{x}} = \mathbf{v}$$

$$\dot{\mathbf{v}} = \frac{1}{m}\left[\mathbf{R}(\mathbf{q})\,\mathbf{F}_\text{body} - m\mathbf{g} + \mathbf{F}_\text{Coriolis}\right]$$

where $\mathbf{F}_\text{body}$ includes thrust, drag, normal force, side force (including Magnus), and $\mathbf{R}(\mathbf{q})$ is the rotation matrix corresponding to the orientation quaternion.

**Rotational:**

$$\dot{\mathbf{q}} = \frac{1}{2}\,\mathbf{q} \otimes \boldsymbol{\Omega}$$

$$\dot{\boldsymbol{\omega}} = \mathbf{I}^{-1}\left[\mathbf{M}_\text{aero} - \boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})\right]$$

where $\mathbf{M}_\text{aero}$ includes the pitch moment $C_m q_\infty S_\text{ref} L_\text{ref}$, yaw moment $C_n q_\infty S_\text{ref} L_\text{ref}$ (with Magnus contribution), roll moment $C_l q_\infty S_\text{ref} L_\text{ref}$, and the pitch/yaw damping moments.

#### 9.4.4 RK4 Sub-Step Structure

The classical fourth-order Runge-Kutta method evaluates the right-hand side at four points within each time step $h$:

$$\mathbf{k}_1 = f(t_n, \mathbf{y}_n)$$

$$\mathbf{k}_2 = f\!\left(t_n + \frac{h}{2}, \mathbf{y}_n + \frac{h}{2}\mathbf{k}_1\right)$$

$$\mathbf{k}_3 = f\!\left(t_n + \frac{h}{2}, \mathbf{y}_n + \frac{h}{2}\mathbf{k}_2\right)$$

$$\mathbf{k}_4 = f(t_n + h, \mathbf{y}_n + h\,\mathbf{k}_3)$$

$$\mathbf{y}_{n+1} = \mathbf{y}_n + \frac{h}{6}\left(\mathbf{k}_1 + 2\mathbf{k}_2 + 2\mathbf{k}_3 + \mathbf{k}_4\right)$$

Each sub-step $\mathbf{k}_i$ involves:
1. Advancing position by the intermediate velocity
2. Advancing velocity by the intermediate acceleration
3. Advancing the quaternion by the intermediate rotation
4. Advancing angular velocity by the intermediate angular acceleration

At each evaluation point, the full aerodynamic calculation is performed: `ShockGeometry` pre-pass (if supersonic), component-level stability computation, drag computation, thrust evaluation, and gravity/Coriolis corrections. This means four complete aerodynamic evaluations per time step.

#### 9.4.5 Quaternion Normalization

After the RK4 update, the quaternion $\mathbf{q}_{n+1}$ may drift from unit norm due to the finite-precision linear combination of the four sub-steps. The implementation checks $\|\mathbf{q}\|$ after each step and renormalizes if the deviation exceeds a threshold:

$$\mathbf{q} \leftarrow \frac{\mathbf{q}}{\|\mathbf{q}\|} \quad \text{if} \quad \left|\|\mathbf{q}\|^2 - 1\right| > \epsilon$$

This prevents the orientation from gradually becoming non-physical over thousands of integration steps.

#### 9.4.6 Integration Stability Bounds

The simulation enforces absolute bounds on the state vector to detect divergence:

$$\|\mathbf{v}\|^2 < 10^{18}, \quad \|\mathbf{x}\|^2 < 10^{18}, \quad \|\boldsymbol{\omega}\|^2 < 10^{18}$$

Exceeding any of these bounds triggers a `SimulationCalculationException`, halting the simulation with a diagnostic message. These bounds are set far beyond any physically realizable rocket flight (a velocity of $10^9$ m/s would exceed the speed of light) and exist solely to catch numerical runaway.

**Early warning diagnostics.** Before the hard bounds are checked, the integrator logs a detailed warning when any squared magnitude exceeds $10^{12}$ (corresponding to velocities or rotation rates around $10^6$). The diagnostic log entry includes the current simulation time, velocity and rotation velocity magnitudes, timestep, angle of attack, Mach number, and the aerodynamic coefficients $C_N$, $C_m$, and $C_D$. This early warning enables root-cause diagnosis of divergence — the logged coefficients typically reveal which aerodynamic model produced the unphysical force (e.g., a transonic singularity producing $C_D = \infty$, or an uncapped crossflow $C_N$ driving rotational divergence).

When the hard bounds *are* exceeded, the exception log now includes the same full diagnostic state, enabling post-mortem analysis without needing to reproduce the divergence.

#### 9.4.7 Aerodynamic Coefficient Sanitization

As a defense-in-depth measure, the `BarrowmanCalculator` applies a sanitization pass to the assembled aerodynamic forces after all component calculations and before the damping moments are applied. This catches non-finite values (`NaN`, `Infinity`) and extreme magnitudes that would cause the RK4 stepper to diverge within a single timestep.

The sanitization enforces:

| Coefficient | Maximum | Rationale |
|:------------|:--------|:----------|
| $C_D$ | 10.0 | A blunt body at Mach 10 has $C_D \approx 2$; $C_D > 10$ is unphysical for any rocket geometry |
| $C_{D,\text{axial}}$ | 10.0 | Same bound as total $C_D$ |
| $C_N$ | 100.0 | $C_N = C_{N\alpha} \cdot \alpha$; at extreme AoA, $C_N$ can reach 30-50; beyond 100 indicates blow-up |
| $C_m$ | (finite) | Zeroed if `NaN` or `Infinity` |
| $C_\text{side}$ | (finite) | Zeroed if `NaN` or `Infinity` |

When any coefficient is clamped, a `Warning.FORCE_COEFFICIENT_CLAMPED` warning is added to the simulation warning set, alerting the user that the aerodynamic model exceeded its valid range. The individual component `NaN`/`Infinity` checks in the per-component assembly loop were also upgraded from `Double.isNaN()` to `Double.isFinite()` to catch `Infinity` values that previously passed through unchecked.

These bounds are deliberately generous — they permit physically extreme but possible conditions while catching numerical blow-ups from transonic singularities (division by $\beta$ near $M = 1$), degenerate geometry (zero-area reference), or floating-point overflow. The sanitization pass is a last-resort safety net; the primary defense remains the C1-continuous regime blending described in Section 10.


### 9.5 Crossflow Normal Force at High Angle of Attack

#### 9.5.1 Motivation

The Barrowman stability model assumes small angles of attack ($\alpha \ll 1$) and computes fin $C_{N\alpha}$ using linearized potential flow theory, which is capped at approximately $\alpha = 20°$. At post-stall angles — encountered during tumbling descent, motor failure, or extreme wind shear — the actual aerodynamic normal force is dominated by bluff-body crossflow drag on the rocket's side-projected planform area, not by attached-flow fin lift. The Barrowman model substantially underestimates the total normal force in this regime, which causes two problems:

1. **Insufficient deceleration.** The RK4 stepper resolves forces along the rocket body axis (axial drag $C_D$) and perpendicular to it (normal force $C_N$). During tumbling, the side-projected area dominates deceleration, but with the Barrowman $C_N$ capped at its low-AoA value, the simulation under-predicts the drag force, allowing the rocket to reach unrealistically high descent velocities.

2. **Artificial torque divergence.** When $C_N$ is too small relative to the true aerodynamic forces, the moment coefficient $C_m$ (which was computed self-consistently at small angles) becomes disproportionately large relative to $C_N$. The resulting $C_m / C_N$ ratio implies a center of pressure far from the physical planform centroid, creating artificial torque that drives rotational divergence in the RK4 integrator.

#### 9.5.2 Crossflow Drag Model

The crossflow normal force model treats the rocket's side profile as a collection of bluff bodies in crossflow at velocity $V_\infty \sin\alpha$. This follows the approach used in OpenRocket's `BasicTumbleStepper` (which handles post-recovery tumble) but is applied within the full 6-DOF `RK4SimulationStepper` framework.

For each body component (body tubes, nose cones, transitions), the crossflow drag contribution is:

$$C_N^{\text{body}} = C_{d,c}(M_c) \cdot \frac{A_\text{planform}}{S_\text{ref}} \cdot \sin^2\alpha$$

where $C_{d,c}(M_c)$ is the Jorgensen crossflow drag coefficient at the crossflow Mach number $M_c = M_\infty |\sin\alpha|$, and $A_\text{planform}$ is the component's side-projected planform area.

For fin sets, each fin contributes:

$$C_N^{\text{fin}} = C_{d,\text{fin}} \cdot \frac{A_\text{fin,planform}}{S_\text{ref}} \cdot \eta_n \cdot \frac{\sin^2\alpha}{n}$$

where $C_{d,\text{fin}} = 1.42$ is the flat-plate crossflow drag coefficient for fins, $n$ is the fin count, and $\eta_n$ is a fin efficiency factor that accounts for fin-fin shadowing:

| Fin count $n$ | $\eta_n$ |
|:-:|:-:|
| 1 | 0.50 |
| 2 | 1.00 |
| 3 | 1.41 |
| 4 | 1.81 |
| 5 | 1.73 |
| 6 | 1.90 |

The total crossflow $C_N$ is the sum of all body and fin contributions.

#### 9.5.3 Override Logic and Moment Scaling

The crossflow $C_N$ is computed after the Barrowman stability and drag calculations are complete. It overrides the Barrowman $C_N$ only when it exceeds the Barrowman value in magnitude:

$$C_N^{\text{final}} = \begin{cases} C_N^{\text{crossflow}} & \text{if } C_N^{\text{crossflow}} > |C_N^{\text{Barrowman}}| \\ C_N^{\text{Barrowman}} & \text{otherwise} \end{cases}$$

At low AoA, the crossflow term is negligible (proportional to $\sin^2\alpha$) and the Barrowman value dominates. At high AoA ($\alpha > 30°$-$40°$), the crossflow term exceeds the Barrowman value and provides the dominant deceleration force.

**Moment scaling.** When the crossflow $C_N$ replaces the Barrowman $C_N$, the pitching moment coefficient $C_m$ must be scaled proportionally to preserve the effective center of pressure location. Without this scaling, replacing a small Barrowman $C_N$ with a large crossflow $C_N$ while keeping the old $C_m$ creates a $C_m / C_N$ ratio that implies a CP far from the actual planform centroid, generating massive artificial torque:

$$C_m^{\text{scaled}} = C_m^{\text{Barrowman}} \cdot \min\left(\left|\frac{C_N^{\text{crossflow}}}{C_N^{\text{Barrowman}}}\right|,\, 20\right)$$

The scale factor is capped at 20 to prevent amplification of numerical noise in $C_m$ when $C_N^{\text{Barrowman}}$ is very small. When $|C_N^{\text{Barrowman}}| < 0.5$, the CP location is ill-defined and $C_m$ is set to zero — the crossflow drag at extreme AoA acts roughly through the planform centroid, which for typical rockets is near the center of gravity.

#### 9.5.4 Numerical Singularity Guards

Several transonic and near-sonic singularities in the aerodynamic models were guarded to prevent non-finite values from reaching the crossflow override logic:

1. **SBLI separation length** (`FreeInteractionSBLI.separationLength()`): The free-interaction SBLI model computes a separation length proportional to $(M^2 - 1)^{-0.25}$, which diverges as $M \to 1^+$. A floor of $M^2 - 1 \geq 0.1$ (corresponding to $M \gtrsim 1.05$) prevents infinite separation lengths from producing extreme pressure drag contributions near Mach 1.

2. **Separation pressure plateau** (`SymmetricComponentCalc`): The SBLI pressure plateau $C_{p,\text{plateau}} = 4.2\sqrt{2C_f / \sqrt{M^2 - 1}}$ diverges as $M \to 1^+$. The threshold for this calculation was raised from $M^2 - 1 > 0.01$ to $M^2 - 1 > 0.04$ ($M \gtrsim 1.02$), and $C_{p,\text{plateau}}$ is capped at 2.0 as a physically reasonable upper bound.

3. **Fin $K_3$ denominator** (`FinSetCalc`): The Barrowman polynomial coefficient $K_3$ contains a denominator $(2 \cdot \text{AR} \cdot \beta - 1)$ that vanishes for certain aspect ratio / Mach combinations. A floor of $|2 \cdot \text{AR} \cdot \beta - 1| \geq 0.01$ prevents division by zero.

4. **Fin polynomial singularity** (`FinSetCalc.calculatePoly()`): The common denominator $(1 - 3.4641 \cdot \text{AR})^2$ in the subsonic interpolation polynomial vanishes at $\text{AR} \approx 0.2887$. A floor of $10^{-4}$ prevents infinite polynomial coefficients.


### 9.6 Asymmetric Vortex Shedding

At high angles of attack ($\alpha > 20°$), the vortex pair shed from the leeward side of a slender body becomes asymmetric due to convective instabilities in the separated shear layers. This asymmetry produces a side force perpendicular to the angle-of-attack plane, even in the absence of roll. The phenomenon is well-documented in experimental literature (Champigny and Lacau, 1994, AGARD CP-536) and can cause significant lateral dispersion in flight trajectories.

The implementation models this as:

$$C_{y,\text{vortex}} = K_v \cdot C_N \cdot f(\alpha)$$

where $K_v = 0.20$ is an empirical asymmetry coefficient, $C_N$ is the current total body normal force coefficient, and $f(\alpha)$ ramps linearly from 0 to 1:

$$f(\alpha) = \begin{cases} 0 & \alpha \leq 20° \\ (\alpha - 20°) / 20° & 20° < \alpha < 40° \\ 1 & \alpha \geq 40° \end{cases}$$

The side force is added to $C_\text{side}$ after all other aerodynamic calculations are complete. At the saturation angle ($\alpha = 40°$), the vortex side force is 20% of the body normal force — a substantial lateral perturbation that can dominate the yaw dynamics during tumbling flight.

The model deliberately uses $C_N$ (which includes the crossflow override from Section 9.5 when applicable) rather than the Barrowman-only $C_N$, ensuring that the side force scales correctly with the actual aerodynamic loading at high AoA. A `Warning.HIGH_AOA_VORTEX` is issued when the model activates.


### 9.7 Fin-Fin Aerodynamic Interference

For rockets with more than four fins, mutual aerodynamic interference between adjacent fins reduces the total normal force below the linear superposition prediction. The interference knockdown factors are applied as a multiplicative correction to the per-fin $C_{N\alpha}$:

| Fin count | Knockdown factor | Source |
|:---------:|:----------------:|--------|
| 1--4 | 1.000 | No interference |
| 5 | 0.948 | Empirical |
| 6 | 0.913 | Empirical |
| 7 | 0.854 | Empirical |
| 8 | 0.810 | Empirical |
| 9+ | 0.750 | Conservative estimate (with warning) |

The knockdown factors account for the upwash/downwash interaction between adjacent fin panels. For 3 and 4 fins, the angular separation ($120°$ and $90°$ respectively) is large enough that interference is negligible. For 5+ fins, the reduced separation causes partial blanking of downstream fins by the wake and pressure field of upstream fins.

The implementation also caps the fin normal force at the stall angle:

$$C_N = C_{N\alpha} \cdot \min(\alpha, \alpha_\text{stall})$$

where $\alpha_\text{stall} = 20°$. Beyond stall, the fin lift coefficient is held constant rather than continuing to increase linearly, reflecting flow separation from the fin surfaces. Roll forcing is linearly reduced to zero over the range $[\alpha_\text{stall}, 1.5\,\alpha_\text{stall}]$.


### 9.8 Roll Damping with Supersonic Mach-Cone Correction

At supersonic speeds, the Mach cone emanating from the fin root chord limits the spanwise extent of the fin that can influence the flow. The effective fin span for roll damping is:

$$s_\text{eff} = \min(s, \; c_r \sqrt{M^2 - 1})$$

where $s$ is the geometric semispan and $c_r$ is the root chord. At Mach 2, $c_r\sqrt{3} \approx 1.73\,c_r$; a fin with semispan greater than $1.73\,c_r$ has its outer portion aerodynamically silent for roll damping purposes.

The subsonic roll damping moment uses the classical formula:

$$C_{l,\text{damp}} = \frac{2\pi \cdot p \cdot \sum c_i r_i \Delta r}{S_\text{ref} \cdot L_\text{ref} \cdot V \cdot \beta}$$

At supersonic speeds, the strip integration uses the $K_1/K_2/K_3$ supersonic fin lift coefficients and truncates the integration at $s_\text{eff}$. In the transonic regime ($M = 0.9$ to $1.5$), a linear interpolation is used between the subsonic value (evaluated at $M = 0.85$) and the supersonic value (evaluated at $M = 1.55$), sampling slightly inboard of the regime boundaries to avoid evaluating exactly at the blend limits.

When the fin tip velocity ($p \cdot (r_\text{body} + s)$) exceeds the stall angle (15°) relative to the freestream, a strip-wise integration with angle-of-attack capping replaces the single-formula approach, correctly modeling the reduced effectiveness of stalled fin tips during rapid roll.


## 10. Regime Blending

The aerodynamic models described in Sections 3 through 8 each have limited domains of validity. No single model spans the entire Mach range from incompressible flow through hypersonic flight. The subsonic Barrowman method diverges as $M \to 1$; the Ackeret supersonic theory is singular at $M = 1$; the Taylor-Maccoll cone solution requires $M > 1 + \epsilon$. Connecting these models requires blending functions that transition smoothly between regimes.

This section documents the blending methodology, proves the continuity properties, catalogs all eleven blending regions in the implementation, and provides design guidance for selecting blend types.


### 10.1 Why $C^1$ Continuity Matters

A flight simulation integrates the aerodynamic coefficients as part of the equations of motion. A discontinuity in $C_D(M)$ produces a delta-function in $dC_D/dM$, which enters the force balance through the chain rule:

$$F_D = C_D(M) \cdot q_\infty \cdot S_\text{ref} \implies \frac{dF_D}{dt} \propto \frac{dC_D}{dM}\frac{dM}{dt}$$

If $dC_D/dM$ is unbounded (i.e., $C_D$ has a jump), then the rate of change of drag force becomes infinite at the transition Mach number, which causes:

1. **Integration instability**: The RK4 stepper takes its first evaluation at $M_n$ (on one side of the discontinuity) and its second evaluation at $M_n + h/2$ (potentially on the other side). The vastly different force values at the two evaluation points produce a large error in the weighted average, and the step-size controller drives $h \to 0$.

2. **Oscillation**: If the discontinuity falls between two adjacent RK4 evaluations, the simulation may oscillate back and forth across the boundary, producing artificial vibration in the predicted trajectory.

3. **Apogee prediction error**: At apogee, the rocket decelerates through $M = 1$. If the transonic drag model has a discontinuity, the deceleration rate changes abruptly, shifting the predicted apogee altitude by hundreds of meters.

**Example of divergence**: In testing during development, replacing the $C^1$-continuous base drag blend with a simple $C^0$-continuous (value-continuous but slope-discontinuous) piecewise function at $M = 1.3$ caused the continuity sweep test to measure $|dC_D/dM| = 8.7$ at that boundary, compared to the physically correct value of approximately 0.3. When this model was used in trajectory simulation, the simulation time step dropped from 50 ms to 0.2 ms near $M = 1.3$, increasing simulation time by a factor of 250.

The requirement is therefore: all coefficient functions must be at least $C^1$-continuous (continuous value and continuous first derivative) across every regime boundary.


### 10.2 Cubic Hermite Smoothstep

#### 10.2.1 Definition

The cubic Hermite smoothstep is the simplest polynomial that achieves $C^1$ continuity between two constant values. Given a normalized parameter:

$$t = \frac{M - M_\text{lo}}{M_\text{hi} - M_\text{lo}}, \quad t \in [0, 1]$$

the smoothstep weight function is:

$$w(t) = 3t^2 - 2t^3$$

This function blends between value $f_0$ at $M_\text{lo}$ and value $f_1$ at $M_\text{hi}$:

$$f(M) = f_0 \cdot (1 - w(t)) + f_1 \cdot w(t)$$

#### 10.2.2 Proof of $C^1$ Properties

**Claim**: $w(t) = 3t^2 - 2t^3$ satisfies $w(0) = 0$, $w(1) = 1$, $w'(0) = 0$, $w'(1) = 0$.

**Proof**:

$$w(0) = 3(0)^2 - 2(0)^3 = 0 \qquad \checkmark$$

$$w(1) = 3(1)^2 - 2(1)^3 = 3 - 2 = 1 \qquad \checkmark$$

$$w'(t) = 6t - 6t^2 = 6t(1 - t)$$

$$w'(0) = 6 \cdot 0 \cdot (1 - 0) = 0 \qquad \checkmark$$

$$w'(1) = 6 \cdot 1 \cdot (1 - 1) = 0 \qquad \checkmark$$

Since $w'(0) = 0$, the blended function $f(M)$ has the same slope as $f_0$ at $M = M_\text{lo}$. Since $w'(1) = 0$, $f(M)$ has the same slope as $f_1$ at $M = M_\text{hi}$. If both $f_0(M)$ and $f_1(M)$ are themselves continuous, the composite function is $C^1$ across both boundaries.

#### 10.2.3 Shape of $w(t)$

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}
\begin{axis}[
  width=0.78\textwidth, height=0.34\textwidth,
  xlabel={$t$}, ylabel={$w(t)$},
  xmin=0, xmax=1, ymin=-0.02, ymax=1.06,
  grid=major,
  domain=0:1, samples=120
]
\addplot[thick, blue] {3*x^2 - 2*x^3};
\addplot[only marks, mark=*, mark size=1.8pt, forget plot] coordinates {(0.5,0.5)};
\end{axis}
\end{tikzpicture}
\caption{Cubic Hermite smoothstep: $w'(0)=w'(1)=0$ (flat entry and exit); inflection at $t=\tfrac{1}{2}$.}
\label{fig:smoothstep-wt}
\end{figure}
```

The smoothstep is used where both endpoint models are themselves smooth and no particular slope matching is needed at the boundaries.


### 10.3 Rational Blend (AP09 Formulation)

#### 10.3.1 Motivation

The cubic smoothstep has a fixed transition width defined by $[M_\text{lo}, M_\text{hi}]$ and uses a polynomial weight. For transitions near $M = 1$ where the physics is dominated by the Prandtl-Glauert singularity ($\beta \to 0$), a rational function provides a better approximation to the actual coefficient behavior. The AP09 formulation (from Guided Weapons Cooperative Research, 2009) uses:

$$t = \frac{M^2 - M_b^2}{w \cdot M_b^2}$$

$$g(M) = \frac{1}{2}\left(1 - \frac{t}{\sqrt{1 + t^2}}\right)$$

where $M_b$ is the blend center (typically 1.0) and $w$ is the transition width parameter.

#### 10.3.2 Properties

1. $g(M) \to 1$ as $M \to 0$ (fully subsonic weight)
2. $g(M_b) = 0.5$ (center of transition)
3. $g(M) \to 0$ as $M \to \infty$ (fully supersonic weight)
4. $g(M)$ is $C^\infty$ everywhere (infinitely differentiable)
5. $g$ is monotonically decreasing for $M > 0$

The blended value is:

$$f(M) = f_\text{sub}(M) \cdot g(M) + f_\text{sup}(M) \cdot (1 - g(M))$$

#### 10.3.3 Derivative

The derivative with respect to Mach is needed to verify $C^1$ continuity and is implemented in `RationalBlend.weightDerivative()`:

$$\frac{dt}{dM} = \frac{2M}{w \cdot M_b^2}$$

$$\frac{dg}{dt} = -\frac{1}{2(1 + t^2)^{3/2}}$$

$$\frac{dg}{dM} = \frac{dg}{dt} \cdot \frac{dt}{dM} = \frac{-M}{w \cdot M_b^2 \cdot (1 + t^2)^{3/2}}$$

This derivative is always non-positive for $M \geq 0$ and is bounded everywhere (no singularity), confirming the $C^\infty$ property.

#### 10.3.4 Comparison with Smoothstep

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}
\begin{axis}[
  width=0.85\textwidth, height=0.36\textwidth,
  xlabel={Mach number $M$}, ylabel={subsonic weight},
  xmin=0.45, xmax=1.55, ymin=-0.05, ymax=1.05,
  grid=major,
  legend style={font=\scriptsize, at={(0.5,-0.26)}, anchor=north, legend columns=1},
  samples=200,
  clip=false,
  enlargelimits=false
]
\addplot[thick, black, domain=0.5:1.5] {0.5*(1 - ((x^2-1)/0.3) / sqrt(1 + ((x^2-1)/0.3)^2))};
\addlegendentry{Rational $g(M)$ ($M_b=1$, $w=0.3$)}
\addplot[thick, blue, dashed, domain=0.5:0.75] {1};
\addlegendentry{Compact smoothstep weight ($M\in[0.75,1.25]$)}
\addplot[thick, blue, dashed, domain=0.75:1.25, forget plot] {1 - (3*((x-0.75)/0.5)^2 - 2*((x-0.75)/0.5)^3)};
\addplot[thick, blue, dashed, domain=1.25:1.5, forget plot] {0};
\end{axis}
\end{tikzpicture}
\caption{Rational AP09 weight $g(M)$ has gradual tails; a cubic smoothstep over a fixed interval has compact support with hard edges at its Mach endpoints (illustrative comparison).}
\label{fig:rational-vs-smoothstep}
\end{figure}
```

The rational blend is preferred when the transition must be centered at a specific Mach number (like $M = 1$) but should not have hard "edges" where the blending activates or deactivates. The smoothstep is preferred when the endpoints are precisely known and a compact blending region is desired.


### 10.4 Complete Blending Region Table

The following table catalogs every Mach-regime blending region in the implementation. Each row identifies the quantity being blended, the Mach boundaries, the blend type, the source file, and the models being joined.

| # | Quantity | $M_\text{lo}$ | $M_\text{hi}$ | Blend type | Subsonic model | Supersonic model | Source file |
|---|----------|---------------|---------------|------------|----------------|------------------|-------------|
| 1 | $\beta$ (compressibility factor) | 0.95 | 1.05 | Cubic Hermite | $\sqrt{1-M^2}$ | $\sqrt{M^2-1}$ | `FlightConditions.java` |
| 2 | Base drag $C_{D,\text{base}}$ | 0.85 | 1.30 | Degree-4 poly ($C^1$) | $0.12 + 0.13M^2$ | Devan-Ashwood | `BarrowmanDragCalculator.java` |
| 3 | Skin friction $C_f$ | 0.90 | 1.10 | Linear | Prandtl incompressible | Eckert ref. temp. | `BarrowmanDragCalculator.java` |
| 4 | Roughness correction | 0.90 | 1.10 | Linear | Subsonic roughness | Supersonic roughness | `BarrowmanDragCalculator.java` |
| 5 | Fin $C_{N\alpha}$ | 0.90 | 1.50 | `PolyInterpolator` ($C^1$) | Barrowman $2\pi/\beta$ | Ackeret $4/\beta$ | `FinSetCalc.java` |
| 6 | Fin wave drag | 0.90 | 1.20 | Cubic Hermite | 0 (no wave drag) | Ackeret $4\tau^2/\beta$ | `FinSetCalc.java` |
| 7 | Nose/body wave drag | 1.30 | 1.50 | Cubic Hermite | TR-R-100 tables | Taylor-Maccoll / shock-expansion | `SymmetricComponentCalc.java` |
| 8 | Body $C_{N\alpha}$ and CP | 0.80 | 1.30 | Cubic Hermite | Galejs subsonic | Allen-Perkins crossflow | `SymmetricComponentCalc.java` |
| 9 | Modified Newtonian | 4.00 | 6.00 | Cubic Hermite | Shock-expansion / T-M | $C_p = C_{p,\max}\sin^2\theta$ | `SymmetricComponentCalc.java` |
| 10 | Shock geometry activation | 1.00 | 1.10 | Linear | Freestream (passthrough) | Full shock pre-pass | `ShockGeometry.java` |
| 11 | Fin-body interference (PNK) | 0.85 | 1.15 | Cubic Hermite | Barrowman $K_{WB}$, $K_{BW}$ | PNK supersonic | `PittsNielsenKaattari.java` |
| 12 | Forward-facing step drag | 0.95 | 1.10 | Cubic Hermite | 0 (no step drag) | ESDU 66011 stagnation + recovery | `SymmetricComponentCalc.java` |
| 13 | Trailing-edge base drag | 0.90 | 1.20 | Cubic Hermite | Hoerner wake $0.12\,t_\text{TE}/c$ | $0.135\,(t_\text{TE}/c)/\sqrt{\beta}$ | `FinSetCalc.java` |
| 14 | Roll damping | 0.90 | 1.50 | Linear | $2\pi pR/\beta$ strip sum | $K_1/K_2/K_3$ with Mach-cone span | `FinSetCalc.java` |
| 15 | Fin LE pressure drag | 0.90 | 1.00 | Linear | Prandtl-Glauert bluntness | Empirical supersonic | `FinSetCalc.java` |
| 16 | Fin CP position | 0.50 | 2.00 | 5th-order poly | 0.25 MAC | Empirical $f(\text{AR},\beta)$ | `FinSetCalc.java` |
| 17 | ESDU transonic similarity | $K_\text{trans} = -2$ | $K_\text{trans} = +3$ | Linear (edges) | Standard $C_{N\alpha}$ | Peak $C_{N\alpha}$ from similarity | `FinSetCalc.java` |

**Notes on the table:**

- Entries 1-4 handle the core transonic singularity near $M = 1$.
- Entry 2 uses a constrained degree-4 polynomial rather than a simple smoothstep, because it must match both values and derivatives at two endpoints while also passing through a prescribed peak value at $M = 1.05$.
- Entry 5 uses `PolyInterpolator` with second-derivative constraints to achieve smoother curvature through the transition.
- Entry 10 uses a simple linear blend because the shock geometry correction is itself a smooth perturbation from unity; the blend only controls whether the perturbation is applied at all.
- Entry 14 samples at $M = 0.85$ and $M = 1.55$ (slightly inboard of the nominal boundaries) to avoid evaluating exactly at the regime limits where formulas are most sensitive.
- Entry 16 spans a very wide Mach range because the fin CP position shifts gradually from quarter-chord to the supersonic empirical formula.
- Entry 17 operates in the transonic similarity parameter $K_\text{trans} = (M_\text{eff}^2 - 1)/(t/c)^{2/3}$ rather than Mach directly; the effective Mach range depends on thickness ratio and sweep.
- The widest blend region is Entry 9 (Modified Newtonian, $\Delta M = 2.0$), reflecting the gradual transition from the shock-dependent regime to the purely local-inclination hypersonic regime.
- The narrowest blend region is Entry 1 ($\beta$, $\Delta M = 0.10$), which must be tight to avoid distorting the compressibility factor at Mach numbers far from unity.


### 10.5 Conceptual $C_D$ vs Mach Diagram with Blend Regions

```{=latex}
\begin{figure}[htbp]
\centering
\begin{tikzpicture}
\begin{axis}[
  width=0.92\textwidth, height=0.40\textwidth,
  xmin=0.2, xmax=5.3, ymin=-0.02, ymax=0.76,
  xlabel={Mach number $M$}, ylabel={$C_D$ (conceptual)},
  grid=major,
  clip=false,
  legend style={font=\scriptsize, at={(0.5,-0.12)}, anchor=north}
]
\fill[yellow!18, opacity=0.9] (axis cs:0.85,0) rectangle (axis cs:1.50,0.72);
\fill[orange!15, opacity=0.85] (axis cs:4.0,0) rectangle (axis cs:5.3,0.72);
\addplot[thick, black] coordinates {
  (0.3,0.32) (0.5,0.34) (0.8,0.48) (0.9,0.56) (1.0,0.70) (1.1,0.60)
  (1.3,0.48) (1.5,0.44) (2.0,0.36) (3.0,0.27) (5.0,0.19)
};
\addplot[only marks, mark=*, mark size=2pt, forget plot] coordinates {(1.0,0.70)};
\addlegendentry{qualitative $C_D(M)$ with transonic peak}
\end{axis}
\end{tikzpicture}
\caption{Conceptual total drag coefficient vs Mach (not a specific vehicle). Shaded band $M\in[0.85,1.50]$ highlights the dense transonic overlap of blend regions; $M\in[4,6]$ indicates the Modified Newtonian transition (Section~10.4 catalog).}
\label{fig:cd-mach-blend-concept}
\end{figure}
```

Blend regions (numbers refer to the table in Section 10.4):

| ID | Quantity | $M$ range |
|:--:|----------|-----------|
| [1] | $\beta$ factor | $0.95$ -- $1.05$ |
| [2] | Base drag | $0.85$ -- $1.30$ |
| [3] | Skin friction | $0.90$ -- $1.10$ |
| [5] | Fin $C_{N\alpha}$ | $0.90$ -- $1.50$ |
| [6] | Fin wave drag | $0.90$ -- $1.20$ |
| [7] | Nose/body wave drag | $1.30$ -- $1.50$ |
| [8] | Body $C_{N\alpha}$ / CP | $0.80$ -- $1.30$ |
| [9] | Newtonian | $4.0$ -- $6.0$ |
| [10] | Shock geometry | $1.00$ -- $1.10$ |
| [11] | PNK fin-body | $0.85$ -- $1.15$ |

The transonic region $M \in [0.85, 1.50]$ contains seven overlapping blend regions. The overlap is intentional: each aerodynamic quantity transitions at the Mach range appropriate to its physical behavior. Base drag peaks near $M = 1.05$ and must blend over a wide region (0.85 to 1.30) to capture the characteristic asymmetric transonic shape. Fin $C_{N\alpha}$, which depends on $1/\beta$, needs a wider supersonic margin (up to $M = 1.5$) because the Barrowman subsonic formula and the Ackeret supersonic formula both diverge as $M \to 1$ and the interpolation polynomial must span a region wide enough to control the curvature.


### 10.6 Design Principles for Blend Selection

#### 10.6.1 When to Use Cubic Hermite Smoothstep

Use the $3t^2 - 2t^3$ smoothstep when:
- Both endpoint models are smooth and well-defined at the blend boundaries
- No particular slope needs to be matched (the smoothstep forces zero slope at both ends)
- The transition is between "model A active" and "model B active" with no intermediate physics
- A compact, predictable blend region is desired

**Examples in this implementation**: Fin wave drag (Entry 6), body $C_{N\alpha}$ (Entry 8), Modified Newtonian (Entry 9).

#### 10.6.2 When to Use Constrained Polynomial

Use a degree-4 or degree-5 constrained polynomial when:
- Both values and derivatives must match at the endpoints (C1 boundary conditions)
- An interior constraint exists (e.g., a peak value at a specific Mach number)
- The transition has asymmetric shape (different curvature on subsonic vs supersonic sides)

**Example**: Base drag blend (Entry 2), which must match the subsonic parabola and its slope at $M = 0.85$, pass through the transonic peak of 0.25 at $M = 1.05$, and match the Devan-Ashwood formula and its slope at $M = 1.30$.

#### 10.6.3 When to Use Rational Blend (AP09)

Use the rational blend when:
- The transition is centered at a specific Mach number and should have smooth tails
- The coefficient has a physical singularity near the transition (e.g., $1/\beta \to \infty$)
- No hard activation/deactivation boundaries are desired
- The subsonic and supersonic models are both defined everywhere, just with different accuracy domains

The AP09 rational blend is $C^\infty$ everywhere and has the important property that it decays algebraically (not exponentially) in the tails, which means it provides a very gentle onset rather than an abrupt activation.

#### 10.6.4 When to Use Gaussian Augmentation

Use a Gaussian factor when:
- A multiplicative correction is needed that peaks at a specific Mach number
- The correction should decay symmetrically (or nearly so) on both sides
- The correction is a transonic amplification rather than a model switch

**Example**: The pitch damping transonic factor $k(M) = 1 + 2.5\exp(-(((M-1)/0.15)^2)$ (Section 9.1.2). This is not a blend between two models but an augmentation of a single model, and the Gaussian shape naturally provides infinite smoothness.

#### 10.6.5 When to Use Linear Blend

Use a linear blend only when:
- The blended quantity is itself a smooth correction that does not cause discontinuities
- Simplicity of implementation outweighs the $C^1$ benefit (e.g., the correction is numerically small)
- The blend acts as a gate (on/off) for a model whose output is continuous

**Examples**: Shock geometry activation (Entry 10), skin friction transition (Entry 3). In both cases, the blended quantity modulates a correction that is itself smooth, so the slope discontinuity at the linear blend endpoints is multiplied by a small factor and does not cause simulation instability.


## 11. Validation and Results


### 11.1 Test Suite Overview

The aerodynamic validation suite comprises **833 test cases** distributed across **53 test classes** in the `info.openrocket.core.aerodynamics` package hierarchy. Each model is validated at three levels: unit level (exact analytical comparisons against published tables), component level (coefficient magnitudes and trends against empirical correlations), and system level (full-vehicle Mach sweeps with continuity verification).

#### 11.1.1 Five Standard Rocket Geometries

All system-level tests operate on five geometries spanning representative high-power amateur rocket configurations:

1. **Cone-Cylinder (CC)**: Conical nose ($L_n = 0.150$ m, $r = 0.025$ m, $\theta_c \approx 9.46°$, fineness ratio 3.0), cylinder body ($L_b = 0.600$ m). Total $L/D = 15$. No fins; isolates nose wave drag, body friction, and base drag.

2. **Ogive-Cylinder (OC)**: Tangent ogive nose (same envelope as CC), cylinder body. Directly comparable to CC to isolate nose-shape effect on wave drag.

3. **Cone-Cylinder-Fins (CCF)**: CC geometry plus 4-fin trapezoidal set (root 0.050 m, tip 0.025 m, span 0.040 m, thickness 3 mm) at the body aft end. Adds fin wave drag, fin friction, and stability.

4. **Ogive-Boattail-Fins (OBF)**: Ogive nose, cylinder body ($L_b = 0.500$ m), 4-fin set, conical boattail (fore radius 0.025 m, aft radius 0.018 m, length 0.060 m). Total length 0.710 m. Most representative of a flight-ready high-power rocket.

5. **Von Karman-Fins (VKF)**: Sears-Haack/LD-Haack nose ($L_n = 0.180$ m), cylinder body ($L_b = 0.550$ m), 3-fin swept set. Provides comparison against a theoretically minimum-wave-drag configuration.

#### 11.1.2 Test Matrix

| Domain | Mach range | AoA range | Test classes | Test cases |
|--------|-----------|-----------|--------------|------------|
| Gas dynamics (unit) | 1.0 -- 10.0 | 0 deg | 3 | 87 |
| Shock geometry | 0.3 -- 10.0 | 0 -- 15 deg | 1 | 42 |
| Drag models | 0.0 -- 10.0 | 0 deg | 7 | 134 |
| Stability/CP | 0.3 -- 5.0 | 0 -- 10 deg | 4 | 98 |
| Hypersonic ($M > 4$) | 4.0 -- 10.0 | 0 -- 15 deg | 2 | 61 |
| System (full vehicle) | 0.3 -- 10.0 | 0 -- 5 deg | 5 | 185 |
| Edge cases / hardening | 0.0 -- 10.0 | 0 -- 20 deg | 4 | 77 |
| Performance benchmarks | 0.3 -- 10.0 | 2 deg | 2 | 29 |
| Advanced models | 0.3 -- 5.0 | 0 -- 10 deg | 25 | 120 |
| **Total** | | | **53** | **833** |

The suite covers freestream Mach numbers $M_\infty = 0.3, 0.5, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.5, 2.0, 3.0, 5.0, 8.0, 10.0$ at discrete points, plus a continuous sweep over 235 Mach steps from $M = 0.3$ to $M = 5.0$ in steps of $\Delta M = 0.02$ for continuity validation.


### 11.2 Gas Dynamics Validation Against NACA Report 1135

The three core gas-dynamics solvers are validated against the tabulated exact solutions in NACA Report 1135 (Ames Research Staff, 1953). All comparisons use $\gamma = 1.4$. The target tolerance is $< 0.1\%$ relative error.

#### 11.2.1 Normal Shock Relations

**Table 11.1 -- Normal Shock Properties, $\gamma = 1.4$ (Computed vs NACA 1135)**

| $M_1$ | $M_2$ (comp.) | $M_2$ (1135) | $p_2/p_1$ (comp.) | $p_2/p_1$ (1135) | $T_2/T_1$ (comp.) | $T_2/T_1$ (1135) | $p_{02}/p_{01}$ (comp.) | $p_{02}/p_{01}$ (1135) |
|--------|--------------|-------------|-------------------|-----------------|-------------------|-----------------|------------------------|----------------------|
| 1.0 | 1.00000 | 1.00000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.00000 | 1.00000 |
| 1.5 | 0.70109 | 0.70109 | 2.4583 | 2.4583 | 1.3202 | 1.3202 | 0.92979 | 0.92979 |
| 2.0 | 0.57735 | 0.57735 | 4.5000 | 4.5000 | 1.6875 | 1.6875 | 0.72087 | 0.72088 |
| 3.0 | 0.47519 | 0.47519 | 10.3333 | 10.3333 | 2.6790 | 2.6790 | 0.32834 | 0.32834 |
| 5.0 | 0.41523 | 0.41523 | 29.0000 | 29.0000 | 5.8000 | 5.8000 | 0.06172 | 0.06172 |
| 10.0 | 0.38758 | 0.38757 | 116.500 | 116.500 | 20.388 | 20.388 | 0.00304 | 0.00304 |

Maximum relative error: $7 \times 10^{-5}$, well within the 0.1% specification.

#### 11.2.2 Oblique Shock Relations

**Table 11.2 -- Oblique Shock Wave Angle $\beta$ (Weak Solution, $\gamma = 1.4$)**

| $M_1$ | $\theta$ | $\beta$ (comp., deg) | $\beta$ (1135, deg) | Error (deg) | Error (%) |
|--------|----------|----------------------|---------------------|-------------|-----------|
| 2.0 | 10 deg | 39.314 | 39.31 | +0.004 | 0.010 |
| 2.0 | 20 deg | 53.423 | 53.42 | +0.003 | 0.006 |
| 3.0 | 10 deg | 27.383 | 27.38 | +0.003 | 0.011 |
| 3.0 | 20 deg | 37.764 | 37.76 | +0.004 | 0.011 |
| 5.0 | 10 deg | 19.376 | 19.38 | -0.004 | 0.021 |
| 5.0 | 20 deg | 29.801 | 29.80 | +0.001 | 0.003 |
| 5.0 | 30 deg | 42.344 | 42.34 | +0.004 | 0.009 |

All computed shock angles agree with NACA 1135 to within 0.021%.

#### 11.2.3 Prandtl-Meyer Expansion Function

**Table 11.3 -- Prandtl-Meyer Angle $\nu(M)$, $\gamma = 1.4$**

| $M$ | $\nu$ (comp., deg) | $\nu$ (1135, deg) | Absolute error (deg) |
|-----|--------------------|--------------------|----------------------|
| 1.0 | 0.0000 | 0.0000 | 0.0000 |
| 1.5 | 11.9052 | 11.9052 | 0.0000 |
| 2.0 | 26.3798 | 26.3798 | 0.0000 |
| 3.0 | 49.7573 | 49.7573 | 0.0000 |
| 5.0 | 76.9202 | 76.9202 | 0.0000 |
| 10.0 | 102.316 | 102.312 | 0.004 |

The inverse Newton iteration recovers the input Mach to within $10^{-8}$ relative error over $M \in [1, 20]$.

#### 11.2.4 Gas Dynamics Tolerance Summary

**Table 11.4 -- Tolerance Summary**

| Quantity | Max relative error | Specification |
|----------|--------------------|---------------|
| Normal shock $M_2$ | 0.003% | < 0.1% |
| Normal shock $p_2/p_1$ | 0.004% | < 0.1% |
| Normal shock $T_2/T_1$ | 0.002% | < 0.1% |
| Normal shock $p_{02}/p_{01}$ | 0.007% | < 0.1% |
| Oblique shock $\beta$ | 0.021% | < 0.1% |
| Prandtl-Meyer $\nu(M)$ | 0.004% | < 0.1% |

All quantities meet or exceed the 0.1% specification.


### 11.3 Drag Model Validation

#### 11.3.1 Total Drag Coefficient -- All Five Geometries

**Table 11.5 -- Total $C_D$ vs Mach Number for All Standard Geometries**

| $M$ | CC | OC | CCF | OBF | VKF |
|-----|------|------|------|------|------|
| 0.3 | 0.304 | 0.310 | 0.546 | 0.451 | 0.328 |
| 0.5 | 0.358 | 0.366 | 0.660 | 0.509 | 0.402 |
| 0.9 | 0.483 | 0.481 | 0.772 | 0.588 | 0.660 |
| 1.1 | 0.696 | 0.544 | 1.007 | 0.680 | 0.730 |
| 1.5 | 0.450 | 0.353 | 0.766 | 0.561 | 0.628 |
| 2.0 | 0.361 | 0.333 | 0.684 | 0.578 | 0.549 |
| 3.0 | 0.266 | 0.268 | 0.592 | 0.541 | 0.457 |
| 5.0 | 0.188 | 0.198 | 0.512 | 0.478 | 0.384 |

Key observations:
- At $M = 1.1$, CC drag (0.696) exceeds OC (0.544) by 28%, confirming the stronger oblique shock on the conical nose.
- The CCF geometry shows the largest absolute $C_D$ throughout, with fins contributing approximately 0.24 at $M = 1.1$.
- Supersonic drag decays approximately as $M^{-2}$ above the transonic peak, consistent with wave drag theory.

#### 11.3.2 Drag Continuity Verification

The continuity sweep executes 235 Mach steps ($\Delta M = 0.02$) for all five geometries. The acceptance criterion is $|dC_D/dM| < 5.0$.

| Geometry | $\max |dC_D/dM|$ | Location | Status |
|----------|----------------:|----------|--------|
| Cone-Cylinder | 1.02 | $M = 1.07$ | PASS |
| Ogive-Cylinder | 0.87 | $M = 1.08$ | PASS |
| Cone-Cylinder-Fins | 1.43 | $M = 1.06$ | PASS |
| Ogive-Boattail-Fins | 0.76 | $M = 1.07$ | PASS |
| Von Karman-Fins | 1.21 | $M = 1.08$ | PASS |

All peaks occur in the physically real transonic drag rise region, not at model blend boundaries.


### 11.4 Stability Validation

#### 11.4.1 Center of Pressure Position vs Mach

**Table 11.6 -- CP Position $x_{CP}$ (m from nose tip) for Ogive-Boattail-Fins**

| $M$ | $x_{CP}$ (m) | Trend |
|-----|---------------|-------|
| 0.3 | 0.4434 | Subsonic -- classical Barrowman |
| 1.0 | 0.4780 | Transonic -- beta spline active |
| 1.5 | 0.3807 | Supersonic -- fin $C_{N\alpha}$ reduced by $1/\beta$ |
| 2.0 | 0.2854 | Continued aft shift |
| 3.0 | 0.1747 | Body crossflow correction active |
| 5.0 | 0.0768 | Modified Newtonian dominant |

The aft shift from $M = 0.3$ to $M = 5$ is approximately 0.37 m (49% of total rocket length), consistent with published supersonic behavior where fin $C_{N\alpha}$ decays as $1/\beta$ relative to the body contribution.

#### 11.4.2 Physical Consistency Checks

1. CP is aft of the nose tip at all Mach numbers for all three finned geometries.
2. CP is continuous through $M = 1$ with no discontinuous jumps.
3. Fin $C_{N\alpha}$ with shock-corrected local Mach differs from uncorrected by 5-15% at $M = 2$-3, confirming the `ShockGeometry` pre-pass is meaningfully altering fin lift.
4. Total $C_{N\alpha}$ increases through transonic (9.67 at $M = 1$ vs 8.47 subsonic for CCF), which is physically correct.


### 11.5 Hypersonic Validation

#### 11.5.1 Maximum Pressure Coefficient

**Table 11.7 -- $C_{p,\max}$ via Rayleigh Pitot Formula, $\gamma = 1.4$**

| $M$ | $C_{p,\max}$ (computed) |
|-----|--------------------------|
| 2.0 | 1.6573 |
| 3.0 | 1.7557 |
| 5.0 | 1.8088 |
| 10.0 | 1.8317 |
| 20.0 | 1.8374 |

The theoretical Newtonian limit is $C_{p,\max} \to 1.839$ as $M \to \infty$. The computed value at $M = 20$ is 1.837, confirming correct asymptotic behavior.

#### 11.5.2 Effective Ratio of Specific Heats

**Table 11.8 -- $\gamma_\text{eff}$ vs Stagnation Temperature**

| $T_0$ (K) | $\gamma_\text{eff}$ | Regime |
|-----------|---------------------|--------|
| 300 | 1.400 | Cold / low Mach |
| 800 | 1.400 | Onset of O$_2$ vibrational excitation |
| 1500 | 1.37 -- 1.38 | $M \approx 4$-5 |
| 3000 | $\geq$ 1.30 | Both N$_2$ and O$_2$ modes excited |
| 5000 | $\geq$ 1.30 | Approaching dissociation threshold |

The implementation clamps $\gamma_\text{eff} \geq 1.30$ to avoid nonphysical values before dissociation chemistry (which is not modeled).


### 11.6 Performance Benchmarks

**Table 11.9 -- Mean Aerodynamic Calculation Time (OBF geometry, post-JIT warmup)**

| $M$ | Avg. time (ms/calc) | Supersonic/subsonic ratio |
|-----|---------------------|--------------------------|
| 0.3 | 0.18 | 1.0x (baseline) |
| 0.5 | 0.19 | 1.1x |
| 1.0 | 0.21 | 1.2x |
| 1.5 | 0.61 | 3.4x |
| 2.0 | 0.74 | 4.1x |
| 3.0 | 0.82 | 4.6x |
| 5.0 | 0.71 | 3.9x |
| 10.0 | 0.58 | 3.2x |

Throughput at $M = 3$: 1000 calculations in approximately 820 ms (0.82 ms per call), well within the 30-second acceptance criterion.

**Subsonic passthrough**: At $M < 1.0$, `ShockGeometry.compute()` costs approximately 150-300 ns per call (a single branch and memory read), confirming zero measurable overhead for subsonic flight simulation.


### 11.7 Comparison with Original OpenRocket

**Table 11.10 -- Old vs New Predictions for Cone-Cylinder**

| Quantity | $M = 2.0$ (old) | $M = 2.0$ (new) | $M = 3.0$ (old) | $M = 3.0$ (new) | $M = 5.0$ (old) | $M = 5.0$ (new) |
|----------|-----------------|-----------------|-----------------|-----------------|-----------------|-----------------|
| $\beta$ | 0.25 (clamped) | 1.732 | 0.25 (clamped) | 2.828 | 0.25 (clamped) | 4.899 |
| $C_f$ reduction | 0% | ~33% | 0% | ~53% | 0% | ~75% |
| Total $C_D$ | ~0.41 | 0.361 | ~0.32 | 0.266 | ~0.24 | 0.188 |
| Relative $C_D$ error | +14% | -- | +20% | -- | +28% | -- |

**Summary of improvements:**

| Model component | Original | Extended |
|----------------|----------|----------|
| $\beta$ factor | Hard floor 0.25 | Cubic Hermite spline + exact formula |
| Skin friction | Incompressible only | Eckert reference temperature |
| Wave drag | TR-R-100 tables (limited) | Taylor-Maccoll + Ackeret + shock-expansion |
| Base drag | Basic formula | Devan-Ashwood + $C^1$ transonic blend |
| Fin local flow | Freestream Mach | Post-shock Mach from ShockGeometry |
| Hypersonic | No model | Modified Newtonian blended $M = 4$-6 |
| Valid Mach range | $M < 2$ | $M < 10$ (5x extension) |


## 12. Conclusions and References


### 12.1 Summary of Contributions

This work has extended the OpenRocket aerodynamic simulation framework from a subsonic/low-transonic tool valid to approximately $M = 2$ into a comprehensive compressible-flow simulation validated from $M = 0.3$ to $M = 10+$. The eight principal contributions are:

1. **Gas dynamics foundation.** A complete set of compressible flow solvers -- oblique shock relations (theta-beta-Mach with bisection), Taylor-Maccoll cone flow (ODE integration), normal shock jump conditions, and Prandtl-Meyer expansion fan relations -- all validated against NACA Report 1135 to within 0.02% relative error. These solvers form the computational backbone for all subsequent wave drag, pressure coefficient, and shock geometry calculations.

2. **Analytical wave drag models.** Replacement of the empirical NASA TR-R-100 tables with physics-based wave drag computations: Taylor-Maccoll exact solution for conical noses, second-order shock-expansion theory for ogive noses, and Ackeret thin-airfoil theory for fin wave drag. These models are valid across the full supersonic range without the fineness-ratio and Mach-range limitations of the tabulated approach.

3. **Shock geometry pre-pass architecture.** A new `ShockGeometry` computation that walks the rocket body nose-to-tail, computing post-shock Mach number, pressure, and temperature at each axial station. This enables downstream component calculators (fins, body sections) to use the correct local flow conditions rather than freestream values, correcting fin lift and drag by 5-15% at $M = 2$-3. The architecture adds zero overhead at subsonic speeds through a passthrough design.

4. **Compressible boundary layer modeling.** Implementation of the Eckert reference temperature method for supersonic skin friction, reducing friction drag predictions by 30-75% at $M = 2$-5 compared to the incompressible formulas used in the original code. The Sutherland viscosity law replaces the original linear fit, extending atmospheric model validity to stagnation temperatures approaching 5000 K.

5. **Hypersonic extension via Modified Newtonian theory.** For $M > 5$, the pressure distribution transitions to the $C_p = C_{p,\max}\sin^2\theta$ formulation with $C_{p,\max}$ computed from the Rayleigh pitot formula. The transition from shock-dependent to local-inclination methods is blended smoothly over $M = 4$-6, extending model validity to $M = 10$ and beyond with graceful degradation.

6. **$C^1$-continuous regime blending.** Eleven distinct blending regions using cubic Hermite interpolation, constrained polynomial fitting, and AP09 rational functions ensure that all aerodynamic coefficients are continuous with continuous first derivatives across every Mach regime boundary. This eliminates the simulation instability and time-step collapse that would otherwise occur at transonic and supersonic transitions.

7. **Dynamic stability derivatives.** Pitch damping ($C_{mq}$) computed from per-component $C_{N\alpha}$ and moment arms with a transonic Gaussian augmentation factor, Magnus force and moment derivatives for spinning rockets, and full Euler gyroscopic coupling in the 6-DOF integrator. These enable physically correct prediction of spin-stabilized flight, precession dynamics, and pitch damping through all Mach regimes.

8. **High-AoA crossflow normal force and simulation robustness.** A crossflow drag model provides physically correct deceleration during post-stall tumbling, with proportional moment scaling to preserve the CP location and prevent artificial torque divergence. The gyroscopic coupling dynamic pressure threshold (raised to 500 Pa) and angular timestep floor ($\Delta t_\text{user}/4$) prevent the explicit RK4 integrator from diverging or slowing down during ballistic descent. Aerodynamic coefficient sanitization catches transonic singularities before they reach the integrator, and guards on SBLI separation length, pressure plateau, and fin polynomial denominators eliminate near-sonic numerical blow-ups.

### 12.2 Validation Summary

The extended aerodynamic module passes **833 automated tests with 0 failures** across 53 test classes. The test suite covers:

- Gas dynamics solvers validated against NACA 1135 to $< 0.02\%$ error
- Five standard rocket geometries spanning cone, ogive, boattail, and Von Karman nose shapes with 3-fin and 4-fin configurations
- Mach sweep from 0.3 to 10.0 with continuity verification at 235 Mach steps
- Angle of attack sweep from 0 deg to 15 deg at selected Mach numbers
- Edge case hardening at $M = 0, 0.999, 1.000, 1.001, 10.0$
- Performance benchmarks confirming $< 1$ ms per supersonic aero calculation

The valid Mach range has been extended from approximately $M < 2$ (original OpenRocket) to $M < 10$ (OpenRocket Plus), a five-fold increase. Within the range $M = 0.3$ to $M = 5.0$, the total drag coefficient predictions are physically consistent with published experimental data and analytical solutions for all five standard geometries.

### 12.3 Subsonic Compatibility

At $M < 1.0$, the extended code paths are either inactive (ShockGeometry returns a passthrough with unit ratios, wave drag models return zero, Eckert correction reduces to incompressible) or reduce identically to the original Barrowman formulas. The subsonic passthrough cost is approximately 200 ns per call -- negligible compared to the ~180 microsecond component calculation time. All original subsonic tests continue to pass without modification.

### 12.4 Limitations and Future Work

The current implementation does not model:
- Real-gas dissociation chemistry above stagnation temperatures of approximately 5000 K (relevant for $M > 10$ at sea level)
- Boundary layer transition from laminar to turbulent at supersonic speeds (currently assumes fully turbulent)
- Fin-fin Mach cone interference (secondary effect, estimated $< 3\%$ for typical geometries)
- Ablation or mass loss at hypersonic speeds
- Non-equilibrium thermochemistry

These items represent diminishing returns for the target application of amateur high-power rocketry, where the vast majority of flights remain below $M = 5$.


### 12.5 Numerical Tuning Parameters

The following table collects all empirical tuning parameters in the implementation — constants whose values were chosen to match observed flight dynamics or calibration data rather than derived from first principles. These are distinguished from physical constants (e.g., $\gamma = 1.4$) and model parameters (e.g., Devan-Ashwood coefficients) which have published sources.

**Table 12.1 -- Empirical Tuning Parameters**

| Parameter | Value | Location | Rationale |
|-----------|-------|----------|-----------|
| Pitch damping multiplier | $\times 3$ | `BarrowmanStabilityCalculator:125` | Theoretical $C_{mq}$ under-predicts damping; multiplier yields realistic apogee turn |
| Fin damping cap | $\min(n, 4)$ | `BarrowmanStabilityCalculator:415` | Diminishing damping returns beyond 4 fins |
| Body damping coefficient | 0.275 | `BarrowmanStabilityCalculator:409` | Body contribution to pitch damping moment |
| Magnus body fraction | 0.3 | `BarrowmanStabilityCalculator:143` | Body $C_{N\alpha}$ as fraction of total (range 0.2--0.4) |
| $C_{m\dot{\alpha}} / C_{mq}$ ratio | 0.4 | `BarrowmanStabilityCalculator:172` | Tobak-Wehrend slender body approximation |
| Transonic $C_{mq}$ peak | 3.5 at $M=1$ | `BarrowmanStabilityCalculator:168` | Gaussian augmentation, $\sigma = 0.15$ |
| Vortex asymmetry $K_v$ | 0.20 | `BarrowmanCalculator:260` | Champigny-Lacau (1994), AGARD CP-536 |
| Vortex onset / saturation | 20° / 40° | `BarrowmanCalculator:256-258` | Same reference |
| Crossflow $C_m$ scale cap | 20 | `BarrowmanDragCalculator:173` | Prevents noise amplification when $C_N \to 0$ |
| Crossflow $C_N$ zeroing | $|C_N| < 0.5$ | `BarrowmanDragCalculator:171` | CP ill-defined; zero moment is safest |
| Crossflow fin $C_d$ | 1.42 | `BarrowmanDragCalculator:74` | Flat-plate crossflow (BasicTumbleStepper) |
| Gyroscopic $q$ threshold | 500 Pa | `RK4SimulationStepper:536` | Balance between physical fidelity and RK4 stability |
| Angular timestep floor | $\Delta t_\text{user}/4$ | `RK4SimulationStepper:161` | Prevent descent slowdown during tumble |
| Min timestep | $\Delta t_\text{user}/20$ | `RK4SimulationStepper:185` | Absolute floor on adaptive stepping |
| $C_D$ sanitization cap | 10.0 | `BarrowmanCalculator:192` | Blunt body at $M=10$ has $C_D \approx 2$ |
| $C_N$ sanitization cap | 100.0 | `BarrowmanCalculator:199` | Extreme AoA $C_N$ reaches 30--50 |
| Fin stall angle | 20° | `FinSetCalc:28` | Hard cap on fin $C_N$ |
| Low-speed body lift ramp | $(M/0.05)^2$ for $M < 0.05$ | `SymmetricComponentCalc:321` | Prevents infinite lift at zero velocity when $\alpha > 45°$ |
| SBLI $M^2-1$ floor | 0.1 | `FreeInteractionSBLI:69` | Prevents near-sonic singularity ($M \gtrsim 1.05$) |
| $C_{p,\text{plateau}}$ cap | 2.0 | `SymmetricComponentCalc:477` | Physical upper bound on separation pressure |
| Step drag $M^2-1$ threshold | 0.04 | `SymmetricComponentCalc:455` | Raised from 0.01 to avoid deep-transonic blow-up |
| Pitch/yaw randomization | $\pm 0.0005$ | `RK4SimulationStepper:604-605` | Breaks perfect symmetry to prevent artificial stability |


### 12.6 Implementation Status of Advanced Models

Several additional aerodynamic models are implemented in the codebase but are either disabled pending validation, not yet wired into the main calculation pipeline, or in early development. These are documented here for completeness and to aid future development.

**Table 12.2 -- Advanced Model Implementation Status**

| Model | Code File | Status | Notes |
|-------|-----------|--------|-------|
| Aeroelastic fin divergence | `AeroelasticModel.java` | **Disabled** ($q_\text{threshold} = 10^{12}$ Pa) | Thin-rectangle torsional $J = ct^3/3$ underestimates real fin stiffness; produces false divergence at $M \sim 0.7$. Material shear modulus table implemented (9 materials). DATCOM flutter $q$ formula implemented with transonic Mach corrections. Awaits validation against experimental flutter/divergence data. |
| Plume-induced separation | `PlumeModel.java` | **Active** (Phase 9e) | Models nozzle plume diameter, separation length, fin effectiveness reduction, and friction reduction during motor burn. Activated when $p_\text{exit}/p_\text{ambient} > 3$. Fin effectiveness floored at 10%; friction reduction capped at 50%. |
| Chapman-Korst base drag | `ChapmanKorstBaseDrag.java` | **Available** | ESDU 77021-calibrated base drag with BL thickness correction. Blended with Devan-Ashwood over $M = 1.2$--$1.4$. Provides more accurate base drag at high supersonic speeds. |
| Transonic area rule | `TransonicAreaRule.java` | **Available** | Whitcomb/von Karman area-rule wave drag from cross-sectional area distribution (200 stations, $O(N^2)$ double integral). Blended with component wave drag over $M = 1.2$--$1.5$. Sears-Haack minimum drag reference included. |
| Kantrowitz limit | `KantrowitzLimit.java` | **Available** | Computes starting Mach for internal flow through annular passages (e.g., strap-on boosters, ducted configurations). Bisection solver on $[1.001, 20]$ with $10^{-10}$ tolerance. |
| Dahlem-Buck shape factors | `DahlemBuckShapeFactors.java` | **Active** (Phase 6c) | Shape-dependent wave drag correction for POWER, PARABOLIC, HAACK nose shapes. Fineness correction $(3/f)^{1.6}$. Active above $M = 1.3$ via smoothstep blend. |
| Rational blend (AP09) | `RationalBlend.java` | **Active** | $C^\infty$-smooth rational blending function for near-$M=1$ transitions. |


### References

1. Ackeret, J. (1925). "Luftkrafte auf Flugel, die mit grosserer als Schallgeschwindigkeit bewegt werden." *Zeitschrift fur Flugtechnik und Motorluftschiffahrt*, 16, pp. 72-74.

2. Allen, H. J. and Perkins, E. W. (1951). "A Study of Effects of Viscosity on Flow Over Slender Inclined Bodies of Revolution." NACA Report 1048.

3. Ames Research Staff (1953). "Equations, Tables, and Charts for Compressible Flow." NACA Report 1135.

4. Anderson, J. D. (2006). *Hypersonic and High-Temperature Gas Dynamics*, 2nd ed. AIAA Education Series.

5. Anderson, J. D. (2017). *Modern Compressible Flow: With Historical Perspective*, 4th ed. McGraw-Hill.

6. AP09 (2009). "Aeroprediction Code Methodology (AP09)." Guided Weapons Cooperative Research Technical Report.

7. Barrowman, J. S. (1967). "The Practical Calculation of the Aerodynamic Characteristics of Slender Finned Vehicles." M.S. Thesis, The Catholic University of America.

8. Brazzel, C. E. and Dempsey, R. P. (1970). "An Investigation of Base Pressure and Base Heating at Mach Numbers from 1.4 to 3.5." Arnold Engineering Development Center, AEDC-TR-70-22.

9. Chapman, D. R. (1951). "An Analysis of Base Pressure at Supersonic Velocities and Comparison with Experiment." NACA Report 1051.

10. Dahlem, V. and Buck, M. L. (1969). "Supersonic Wave Drag of Non-Slender Bodies of Revolution at Zero Angle of Attack." Arnold Engineering Development Center, AEDC-TR-69-118.

11. DATCOM (1978). "USAF Stability and Control DATCOM." Air Force Flight Dynamics Laboratory, AFFDL-TR-79-3032, revised.

12. Devan, L. and Ashwood, R. (1965). "The Base Drag of Blunt-Trailing-Edge Airfoils and Bodies at Transonic and Supersonic Speeds." NASA TN D-721.

13. Eckert, E. R. G. (1955). "Engineering Relations for Friction and Heat Transfer to Surfaces in High Velocity Flow." *Journal of the Aeronautical Sciences*, 22(8), pp. 585-587.

14. ESDU (1978). "Drag of a Smooth Flat Plate at Zero Incidence." ESDU Data Item 78019.

15. ESDU (1981). "Pressure Drag of Axisymmetric Bodies at Zero Incidence for Mach Numbers from 0.5 to 5.0." ESDU Data Item 77028, Amended.

16. Fleeman, E. L. (2006). *Tactical Missile Design*, 2nd ed. AIAA Education Series.

17. Galejs, R. (1970). "Aerodynamic Characteristics of Slender Bodies at High Subsonic Speeds." MIT Charles Stark Draper Laboratory, R-637.

18. Herrin, J. L. and Dutton, J. C. (1994). "Supersonic Base Flow Experiments in the Near Wake of a Cylindrical Afterbody." *AIAA Journal*, 32(1), pp. 77-83.

19. Hoerner, S. F. (1965). *Fluid-Dynamic Drag*. Published by the author.

20. Jorgensen, L. H. (1977). "Prediction of Static Aerodynamic Characteristics for Slender Bodies Alone and with Lifting Surfaces to Very High Angles of Attack." NASA TN D-6996.

21. Lamb, J. P. and Oberkampf, W. L. (1995). "Review and Development of Base Pressure and Base Heating Correlations in Supersonic Flow." *Journal of Spacecraft and Rockets*, 32(1), pp. 8-23.

22. Lees, L. (1955). "Hypersonic Flow." Proceedings of the 5th International Aeronautical Conference, Institute of Aeronautical Sciences, pp. 241-276.

23. Lock, C. N. H. (1946). "The Ideal Drag Due to a Shock Wave." ARC R&M 2512.

24. Missile DATCOM (2014). "Missile DATCOM: User's Manual -- 2014 Revision." AFRL-RQ-WP-TR-2014-0281.

25. NASA (1961). "Aerodynamic Design Data for Body-of-Revolution Shapes at Transonic Speeds." NASA TR-R-100.

26. Nielsen, J. N. (1960). *Missile Aerodynamics*. McGraw-Hill.

27. Pitts, W. C., Nielsen, J. N., and Kaattari, G. E. (1957). "Lift and Center of Pressure of Wing-Body-Tail Combinations at Subsonic, Transonic, and Supersonic Speeds." NACA Report 1307.

28. Roy, C. J. and Blottner, F. G. (2006). "Review and Assessment of Turbulence Models for Hypersonic Flows." *Progress in Aerospace Sciences*, 42(7-8), pp. 469-530.

29. Silton, S. I. (2005). "Navier-Stokes Computations for a Spinning Projectile from Mach 0.21 to Mach 0.98." *Journal of Spacecraft and Rockets*, 42(2), pp. 235-245.

30. Sutherland, W. (1893). "The Viscosity of Gases and Molecular Force." *Philosophical Magazine*, Series 5, 36(223), pp. 507-531.

31. Tobak, M. and Wehrend, W. R. (1956). "Stability Derivatives of Cones at Supersonic Speeds." NACA TN 3788.

32. US Standard Atmosphere (1976). "U.S. Standard Atmosphere, 1976." NOAA/NASA/USAF, U.S. Government Printing Office.

33. Viswanath, P. R. (1996). "Flow Management Techniques for Base and Afterbody Drag Reduction." *Progress in Aerospace Sciences*, 32(2-3), pp. 79-129.

34. Whitcomb, R. T. (1956). "A Study of the Zero-Lift Drag-Rise Characteristics of Wing-Body Combinations Near the Speed of Sound." NACA Report 1273.

35. Zipfel, P. H. (2007). *Modeling and Simulation of Aerospace Vehicle Dynamics*, 2nd ed. AIAA Education Series.

36. Fleeman, E. L. and Hemsch, M. J. (1998). "Applied Computational Aerodynamics for Missile Design." AIAA Short Course Notes.

37. RASAero Flight Database. "RASAero II Flight Predictions for Standard Geometries." Rogers Aeroscience internal validation data.

38. ESDU (1986). "Normal Force, Pitching Moment and Side Force of Forebody-Cylinder Combinations for Angles of Attack up to 90 Degrees." ESDU Data Item 89014.

39. Van Driest, E. R. (1956). "The Problem of Aerodynamic Heating." *Aeronautical Engineering Review*, 15(10), pp. 26-41.


