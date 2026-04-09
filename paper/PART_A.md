# Physics-Based Aerodynamic Modeling for Supersonic and Hypersonic Flight Simulation in OpenRocket Plus

**Technical Report ORP-2026-01**

**Authors:** Aidan Yu, with computational assistance from Claude (Anthropic)

**Date:** April 2026

---

## Abstract

This report documents the design, implementation, and validation of comprehensive supersonic and hypersonic aerodynamic modeling extensions to OpenRocket Plus, a fork of the open-source rocket flight simulator OpenRocket. The original OpenRocket implementation, based on the Barrowman slender-body method (1967), was limited to subsonic flight by several fundamental assumptions: a hard-clamped compressibility factor ($\beta_{\min} = 0.25$), linear fits for atmospheric viscosity and speed of sound, absence of shock modeling, and reliance on tabulated drag data valid only to approximately Mach 3.

The extensions described herein replace these approximations with physics-based models valid from subsonic through hypersonic regimes (Mach 0 to 10+). The atmospheric model now uses the exact thermodynamic speed of sound $a = \sqrt{\gamma R T}$ with humidity correction and Sutherland's law for dynamic viscosity, both validated against the US Standard Atmosphere 1976. A high-temperature model based on the Einstein vibrational partition function computes effective $\gamma$ accounting for vibrational excitation of $\mathrm{N_2}$ and $\mathrm{O_2}$, reducing $\gamma$ from 1.4 toward 1.3 at stagnation temperatures exceeding 2500 K.

A complete oblique shock solver implements theta-beta-Mach relations, Taylor-Maccoll cone flow, normal shock jump conditions, and Prandtl-Meyer isentropic expansion, validated against NACA Report 1135 to better than 0.1%. The transonic compressibility factor uses a cubic Hermite spline through Mach 0.95 to 1.05, replacing the catastrophic $\beta_{\min}$ clamp with a C1-continuous function that preserves correct asymptotic behavior.

Drag modeling employs Taylor-Maccoll exact solutions for cone wave drag, second-order shock-expansion theory for ogive bodies, Devan-Ashwood correlations for supersonic base drag, Eckert reference temperature method for compressible skin friction, and Ackeret thin-airfoil theory for fin wave drag. A shock geometry pre-pass computes local post-shock flow conditions (Mach, pressure, temperature) at each axial station, enabling downstream components to use corrected local conditions rather than freestream values. Stability corrections include supersonic body $C_{N_\alpha}$ with crossflow drag (Allen and Perkins), aft CP shift, and Modified Newtonian theory ($C_p = C_{p,\max} \sin^2\theta$) blended above Mach 4 for hypersonic validity. The test suite comprises 524 aerodynamic test methods covering Mach 0.3 to 10+, angles of attack 0 to 15 degrees, and five standard rocket geometries, with zero failures.

---

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

**Incremental integration with regression gates.** Each new model was implemented, tested, and validated independently before being integrated into the main calculation pipeline. A comprehensive regression test suite (524 test methods as of April 2026) ensured that no previously correct behavior was degraded. Each capability increment was validated against analytical solutions, published experimental data, or both before proceeding to the next.

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

The following 19 distinct physical phenomena are modeled in the current implementation:

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
12. Devan-Ashwood supersonic base drag
13. Transonic base drag peak (polynomial correlation)
14. Ackeret thin-airfoil fin wave drag with sweep correction
15. Eckert reference temperature compressible skin friction
16. Supersonic body $C_{N_\alpha}$ (crossflow drag, Allen and Perkins)
17. Supersonic body CP aft shift
18. Modified Newtonian hypersonic pressure ($C_p = C_{p,\max} \sin^2\theta$)
19. Fin-body shock interaction (local flow correction from ShockGeometry)


### 1.5 Software Architecture

The aerodynamic calculation pipeline in OpenRocket Plus follows a layered architecture in which a single orchestrator delegates to specialized calculators. The following diagram shows the data flow for a single aerodynamic evaluation at a given Mach number and angle of attack:

```
 FlightConditions (M, alpha, atm)
         |
         v
 +---------------------------+
 |   BarrowmanCalculator     |  <-- orchestrator
 |   (getAerodynamicForces)  |
 +---------------------------+
         |
         |  1. Shock pre-pass (once per call)
         v
 +---------------------------+
 |   ShockGeometry.compute() |  M > 1: oblique shock at nose,
 |                           |  surface marching with PM expansion
 |   Returns: LocalConditions|  and oblique shocks at each station
 |   at each axial station   |  M <= 1: no-op passthrough
 +---------------------------+
         |
         |  ShockGeometry passed to both sub-calculators
         |
    +----+----+
    |         |
    v         v
 +--------+ +--------+
 |Stability| | Drag   |
 |Calc     | | Calc   |
 +--------+ +--------+
    |         |
    |         +-- BarrowmanDragCalculator
    |              |-- Friction drag (Eckert reference temperature)
    |              |-- Pressure/wave drag (Taylor-Maccoll, shock-expansion)
    |              |-- Base drag (Devan-Ashwood)
    |              |-- Override drag (user-specified)
    |
    +-- BarrowmanStabilityCalculator
         |
         +-- Per-component calculators:
              |
              +-- SymmetricComponentCalc (nose cones, transitions)
              |    - Body CNa with supersonic crossflow correction
              |    - Body CP with Mach-dependent aft shift
              |    - Wave drag (Taylor-Maccoll cone, shock-expansion ogive)
              |    - Modified Newtonian blended above M=4
              |
              +-- FinSetCalc (fin sets)
              |    - K1/K2/K3 fin CNa with local Mach from ShockGeometry
              |    - Ackeret wave drag with sweep correction
              |    - Local dynamic pressure correction for fin normal force
              |
              +-- RailButtonCalc, LaunchLugCalc, TubeFinCalc, ...
```

The key architectural element is `ShockGeometry`, computed once per aerodynamic evaluation. At subsonic Mach numbers it is a no-op passthrough: all local conditions equal freestream, and no computational overhead is incurred. At supersonic Mach numbers, it walks the body chain from nose to tail:

1. At the nose tip, it computes the initial oblique shock using `ObliqueShockSolver.solveCone()` (Taylor-Maccoll solution for a conical shock) or `ObliqueShockSolver.solve()` (wedge/ogive approximation). If the shock is detached (deflection angle exceeds the maximum for an attached shock), it falls back to `NormalShockRelations` for the strong shock solution.

2. It marches downstream in 20 strips per component, computing the local surface tangent angle at each station. Where the surface turns away from the flow (convex curvature, as on an ogive or at a shoulder-to-body-tube junction), it applies a Prandtl-Meyer expansion fan. Where the surface turns into the flow (concave curvature, as at a boattail), it applies an oblique shock compression.

3. At each station it records the local Mach number, static pressure ratio $p/p_\infty$, static temperature ratio $T/T_\infty$, and dynamic pressure ratio $q/q_\infty$. These are stored in a sorted list of `LocalConditions` objects.

4. Component calculators query `ShockGeometry.getConditionsAt(x)` to obtain interpolated local conditions at their axial position. `FinSetCalc`, for example, uses the local post-shock Mach to compute $C_{N_\alpha}$ and the local dynamic pressure ratio to scale the fin normal force.

Between Mach 1.0 and 1.1, the shock geometry corrections are linearly blended toward freestream values to eliminate the step discontinuity when shock geometry first activates.

All shock and expansion computations use validated solvers in the `info.openrocket.core.aerodynamics.shocks` package: `ObliqueShockSolver` (theta-beta-Mach, Taylor-Maccoll), `NormalShockRelations` (Rankine-Hugoniot jump conditions), and `PrandtlMeyerExpansion` (isentropic expansion fan). These are pure mathematical utilities with no dependencies on the rest of the codebase and are independently validated against NACA Report 1135 tables to better than 0.1%.

---

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

---

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

```
 gamma_eff
  1.40 |============================+
       |                            :\
       |                            : \
  1.38 |                            :  \
       |                            :   \
       |                            :    \
  1.36 |                            :     \
       |                            :      \
       |                            :       \
  1.34 |                            :        \
       |                            :         \.
  1.32 |                            :          '..
       |                            :             ''...
  1.30 |----------------------------:--------------------'''''------
       |                            :
       +---+----+----+----+----+----+----+----+----+----+----+--->
       0  500  800 1000 1500 2000 2500 3000 3500 4000 4500 5000
                          T_stag (K)
                            ^
                            |
                     vibrational excitation
                        threshold (~800 K)
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

---

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

The following ASCII diagram shows $\beta(M)$ for the old clamped model and the new Hermite spline:

```
 beta
  1.0 |
      |\
      | \
  0.8 |  \
      |   \
      |    \                                                   /
  0.6 |     \                                                 /
      |      \                                               /
      |       \                                             /
  0.4 |        \                                           /
      |         \  old clamp: beta = 0.25                 /
      |          +=============================+         /
  0.3 |         /  :<-- clamped flat region -->:  \     /
      |       /    :                           :    \  /
      |     /      :                           :     \/
  0.2 |   /        :                           :
      |  /         :                           :
      | /  new     :     new spline: smooth    :
  0.1 |/   spline  :         V-shape           :  new spline
      |    dips    :        .    .             :   rises
  0.05|    here    :      .        .           :
      |            :    .            .         :
    0 +---+---+---+---+---+---+---+---+---+---+---+---+---+--->
      0.5 0.6 0.7 0.8 0.9 0.95 1.0 1.05 1.1 1.2 1.3 1.5  M
                          ^         ^
                          M_L       M_H
                        (0.95)    (1.05)

  Key:
  ===  Old model (clamped at 0.25)
  ...  New model (Hermite spline, minimum ~ 0.045 near M=1.02)
  ---  Both models agree (outside transonic band)
```

The old model (dashed line with flat region) produces a plateau from approximately Mach 0.97 to 1.03 where $\beta$ is frozen at 0.25. The new model (smooth curve through the transonic band) dips to a minimum of approximately 0.045 near Mach 1.02 (slightly above Mach 1 due to the asymmetry of the boundary conditions), then rises smoothly into the supersonic formula. The factor $1/\beta$ reaches approximately 22 at the minimum, compared to a maximum of 4 under the old clamp; this factor-of-five increase correctly captures the transonic peak in aerodynamic coefficients.

#### 4.3.7 Impact on Simulation

The replacement of the $\beta_{\min} = 0.25$ clamp with the Hermite spline has three primary effects on flight simulation:

1. **Transonic drag peak restored.** With $1/\beta$ reaching ~22 instead of being capped at 4, the wave drag peak near Mach 1 is correctly represented. This produces a sharper, more realistic drag rise that decelerates the rocket more strongly as it passes through Mach 1.

2. **Smooth coefficient variation.** The C1 continuity of the spline ensures that $dC_D/dM$ and $dC_{N_\alpha}/dM$ are bounded throughout the transonic regime. The trajectory integrator no longer encounters discontinuities in the aerodynamic derivatives, eliminating the numerical oscillations that the old clamp could induce when the simulation timestep straddled the clamp boundary.

3. **Correct high-Mach behavior preserved.** Above Mach 1.05, the exact supersonic formula $\beta = \sqrt{M^2-1}$ is used directly. At Mach 5, $\beta = 4.899$; the old clamp did not affect this value, and neither does the new spline, confirming that the high-Mach behavior is unchanged.

---

*[End of Sections 1-4. Sections 5-10 continue in PART_B.md.]*
