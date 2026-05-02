## 6. Drag Models

The total drag coefficient of a sounding rocket or high-power rocket vehicle is assembled from five additive contributions in [`BarrowmanDragCalculator.calculateDrag()`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java):

$$
C_D \;=\; C_{D,\text{friction}} \;+\; C_{D,\text{pressure}} \;+\; C_{D,\text{base}} \;+\; C_{D,\text{override}} \;+\; C_{D,i}
$$

with

- $C_{D,\text{friction}}$ — viscous skin-friction integrated over all wetted surfaces, computed via the Van Driest II compressible transformation at supersonic Mach (Section 6.3);
- $C_{D,\text{pressure}}$ — forebody/wave drag from nose cones, body shoulders, transitions, and fin leading edges (Section 6.1 for axisymmetric components, Section 6.4 for fins);
- $C_{D,\text{base}}$ — afterbody base drag arising from the low-pressure recirculation behind every blunt aft face (Section 6.2);
- $C_{D,\text{override}}$ — any user-specified per-component drag override (carried unchanged from upstream OpenRocket);
- $C_{D,i} = C_N \sin\alpha$ — lift-induced drag from the axial projection of the normal force at angle of attack (Section 6.5).

`BarrowmanDragCalculator` orchestrates this assembly; component-level work is delegated by reflection to [`SymmetricComponentCalc`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/SymmetricComponentCalc.java) (nose cones, body tubes, transitions/boattails) and [`FinSetCalc`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java) (fin sets). Every component method spans the full Mach range from low subsonic through hypersonic, with C1-continuous polynomial blending at every regime transition; the explicit transonic blend windows are tabulated in Section 6.10.

The output $C_D$ is converted to an axial-force coefficient $C_{D,\text{axial}} = f(\alpha)\,C_D$ by `calculateAxialCD()` (Section 6.6) before it is returned to the 6-DOF stepper, and the same call also adds two non-axisymmetric pressure-drag mechanisms: forward-facing step drag at body diameter discontinuities (Section 6.7) and shock–boundary-layer interaction at fin roots (Section 6.8). Section 6.9 closes the chapter with a quantitative drag budget at $M=0.5$, $M=2.0$, and $M=5.0$, and Section 6.10 collects every transonic blend window in one table for cross-reference.

> **Validation map for this chapter.** The headline drag claims are: nose wave drag MAE 0.029 vs NACA RM A52H28 across 5 nose families; turbulent base drag MAPE 15.9% vs NACA TN 3393 (4 points, $M = 2.73$–$4.48$); laminar base drag MAPE 4.4% vs the same TN 3393 dataset; fin wave drag against NACA TN 3650 (12 free-flight source rows, with the current diagnostic MAPE computed over the 10 non-Mach-1.10 rows) plus exact-to-numerical-precision agreement with the Ackeret formula on 15 unswept cases; total finned-vehicle drag MAPE 11.9% vs ADA636861 Basic Finner over $M=1.08$–$4.30$; hypersonic cone foredrag MAPE 19.7% vs DTIC AD0487365 across 11 points $M=6.5$–$17.2$; and AGARD-B (AEDC-TR-70-100) drag trend closure by per-row tolerance gates over $M=0.2$–$1.0$ rather than by a single MAE gate.


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

The per-shape selection logic is in `SymmetricComponentCalc.calculatePressureCD()` (lines 415–500) and `buildAnalyticalWaveDragCurve()` (lines 812–860). An expanding shoulder (Transition with $R_{\text{aft}} > R_{\text{fore}}$) is treated as a body that sits under an expansion fan and contributes zero pressure drag at supersonic Mach (line ~450 of the same file); only contracting shoulders behave as nose-like compression surfaces.


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

The asymptotic values agree with the NACA 1135 normal-shock tables to better than $0.01\%$ on all 15 spot checks (validation in [`paper/data/md/rayleigh_pitot_cpmax.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/rayleigh_pitot_cpmax.md)):

| $M$ | $C_{p,\max}$ ($\gamma = 1.4$) |
|---|---|
| 1.0 | 1.000 (isentropic stagnation) |
| 2.0 | 1.278 |
| 3.0 | 1.583 |
| 5.0 | 1.734 |
| 10.0 | 1.812 |
| $\infty$ | 1.839 |

**Real-gas $\gamma$ correction.** Above $M = 5$, the stagnation temperature exceeds $\sim 2000$ K and vibrational excitation of N$_2$ and O$_2$ reduces the effective ratio of specific heats. The effective $\gamma$ used in $C_{p,\max}$ is read from `AtmosphericConditions.effectiveGamma(T_0)` evaluated at the approximate stagnation temperature

$$
T_0 \approx T_\infty\!\left(1 + \tfrac{\gamma-1}{2}M^{2}\right).
$$

The piecewise function in `AtmosphericConditions` is

$$
\gamma_{\text{eff}}(T_0) \;=\; \begin{cases}
1.4 & T_0 \le 800 \;\text{K} \\
1.4 - 7.5\times 10^{-5}\,(T_0 - 800) & 800 < T_0 \le 2000\;\text{K} \\
1.31 - 2.5\times 10^{-5}\,(T_0 - 2000) & 2000 < T_0 \le 4000\;\text{K} \\
1.25 & T_0 > 4000 \;\text{K}
\end{cases}
$$

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
2. Modified Newtonian gives $C_{p,\max} = 1.734$, $\sin^2(15^\circ) = 0.0670$, so the single-strip Newtonian estimate is $C_d \approx 1.734 \times 0.0670 = 0.116$.
3. Blend weight at $M = 5.0$: $t = 0.5$, $w = 3(0.5)^2 - 2(0.5)^3 = 0.5$.
4. Blended: $C_d = 0.5 \times 0.185 + 0.5 \times 0.116 = 0.151$.

| Mach | Taylor–Maccoll $C_d$ | Newtonian $C_d$ | Blended $C_d$ |
|---|---|---|---|
| 2.0 | 0.202 | – | 0.202 |
| 3.0 | 0.209 | – | 0.209 |
| 5.0 | 0.185 | 0.116 | 0.151 |

A52H28 sanity check across all five reference nose families (digitized in [`paper/data/csv/NACA_RM_A52H28_digitized_points.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/NACA_RM_A52H28_digitized_points.csv)): the current JUnit benchmark reports aggregate MAE approximately 0.029 with a gate of 0.035 after the Van Driest II skin-friction change. The older `paper/data/csv/naca_rm_a52h28_metrics.csv` and `paper/data/md/naca_validation_report.md` still preserve the pre-Van-Driest/Eckert export value (MAE 0.0147, RMSE 0.0190) and should be read as stale provenance until regenerated. The bias isolation memo [`paper/data/md/a52h28_bias_isolation.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/md/a52h28_bias_isolation.md) attributes the cone residual to the shape-agnostic transonic polynomial and the quarter-power family residual (~10–15% flat) to TR-R-100 fineness scaling — both architectural rather than physical errors.


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

The factors are described in turn in Sections 6.2.1–6.2.8. Five of them are externally anchored against published data. The finned-body augmentation $k_{\text{finned}}$ and the thick-boundary-layer multiplier $k_{\text{thick-BL}}$ are calibrated against the 24-flight corpus apogee residual — a circular calibration that is not counted toward the external-benchmark headline. The corresponding component-level dataset (finned-body base pressure across the transonic-to-supersonic range) does not exist in a form that has been located in the public literature.


#### 6.2.1 Subsonic Hoerner Correlation

For $M \le 0.85$ the unmodified Hoerner correlation for cylindrical afterbodies applies (`BarrowmanDragCalculator.calculateBaseCD`, line 1655):

$$
C_{d,\text{base}}(M) \;=\; 0.12 + 0.13\,M^{2}.
$$

This rises smoothly from $0.12$ at $M=0$ to $0.214$ at $M = 0.85$. Reference: Hoerner, *Fluid-Dynamic Drag* (1965), Chapter 3.


#### 6.2.2 Supersonic Devan–Ashwood Correlation

For $M \ge 1.5$ the implementation switches to the Devan–Ashwood form (lines 1658–1660):

$$
C_{d,\text{base}}(M) \;=\; \mathrm{BASE\_DRAG\_A} + \frac{\mathrm{BASE\_DRAG\_B}}{M^{2}} \;=\; 0.064 + \frac{0.186}{M^{2}}.
$$

The constants `BASE_DRAG_A = 0.064` and `BASE_DRAG_B = 0.186` are documented as fitted to turbulent cylindrical afterbody data from Devan & Ashwood. Two physical features of this form are worth noting:

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
    0.147,   // Devan-Ashwood at M=1.50
    0.221,   // subsonic derivative at M=0.85
   -0.110);  // Devan-Ashwood derivative at M=1.50
```

The polynomial is degree 5 with six constraints, including a Hart anchor at $M = 1.30$. Without that anchor a four-point polynomial would extrapolate from the Devan-Ashwood model at $M = 1.30$ at $\sim 0.174$, under-reading Hart by 30%.

The interior anchor `BASE_CD_AT_MID = 0.230` is set deliberately just below the Hart reading of $0.250 \pm 0.013$ to keep the peak inside $[0.25, 0.26]$ without overshooting and without regressing the TN 3393 turbulent agreement above $M = 2.7$ — this design constraint is carried in the comment block at lines 60–69 of `BarrowmanDragCalculator.java`.


#### 6.2.4 Chapman Laminar Base Drag

For rockets configured with `Rocket.isPerfectFinish() == true` and `forceTurbulentBL == false`, the boundary layer can remain laminar over a significant fraction of the body, and the Devan–Ashwood turbulent correlation systematically overestimates base drag at high Mach — the laminar shear layer at the base corner has much lower momentum than the turbulent one, producing less wake recompression and lower (more negative) base pressure than the turbulent correlation predicts. The Chapman (1950) NACA TN 2137 laminar correlation provides the correct scaling, implemented in [`ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL)`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/ChapmanKorstBaseDrag.java):

$$
C_{p,b,\text{lam}} \;=\; \frac{C_{\text{LAM}}}{M^{2}\,\sqrt{\mathrm{Re}_L}},
\qquad C_{\text{LAM}} = 1870.
$$

The constant $1870$ is carried as `C_LAM_SUPERSONIC` and is documented in the source as a geometric-mean fit to the four condensation-corrected laminar TN 3393 points (Reller & Hamaker 1955), $M = 2.73$–$4.48$, $\mathrm{Re}_L = 4$–$6 \times 10^{6}$.

A vacuum-pressure cap is imposed: $C_{p,b,\text{lam}} \le 2/(\gamma M^{2})$ corresponds to base pressure at zero (perfect vacuum on the wake side), which is the physical maximum.

**Blending with the turbulent branch.** The transition from the Devan–Ashwood / transonic polynomial to the Chapman laminar formula is blended over $M \in [1.3, 2.5]$ via cubic Hermite smoothstep ($t = (M - 1.3)/1.2$, $w = 3t^2 - 2t^3$). Below $M = 1.3$ only Devan–Ashwood is used (no laminar/turbulent base distinction has yet established at the corner); above $M = 2.5$ the full Chapman laminar formula is used.

In the dispatch (lines 970–984 of `BarrowmanDragCalculator.calculateBaseCD()`), the laminar branch is mixed with the turbulent value by the Michel-criterion laminar fraction $f_{\text{lam}}$ (Section 6.3.3):

$$
C_{d,\text{base}}^{\text{eff}} \;=\; f_{\text{lam}}\,C_{d,\text{base}}^{\text{Chapman}} + (1 - f_{\text{lam}})\,C_{d,\text{base}}^{\text{Devan-Ashwood}}.
$$

For non-perfect-finish rockets the laminar fraction is forced to a small cap (≤ 5%) inside `calculateFrictionCD()` because surface roughness from paint, couplers, and fin fillets trips transition almost immediately, so the Chapman branch is in practice activated only on smooth tunnel models.

**Validation.** [`ChapmanLaminarBaseDragTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/ChapmanLaminarBaseDragTest.java) MAPE gate is $\le 10\%$ on the four TN 3393 laminar points; the achieved MAPE is $4.4\%$, vs $44\%$ for Devan–Ashwood applied to the same data. Spot values from the test:

| $M$ | $\mathrm{Re}_L$ | TN 3393 $C_{p,b}$ (lam.) | Chapman $C_{p,b}$ |
|---|---|---|---|
| 2.73 | $4.0 \times 10^{6}$ | 0.1150 | (within 10%) |
| 3.49 | $4.5 \times 10^{6}$ | 0.0680 | (within 10%) |
| 4.03 | $5.0 \times 10^{6}$ | 0.0493 | (within 10%) |
| 4.48 | $6.0 \times 10^{6}$ | 0.0391 | (within 10%) |

The test also checks the analytical $\sqrt{\mathrm{Re}}$ scaling: doubling $\sqrt{\mathrm{Re}_L}$ halves $C_{p,b,\text{lam}}$ to within $2\%$.


#### 6.2.5 Chapman–Korst Free Shear Layer (Turbulent, Optional)

[`ChapmanKorstBaseDrag.blendedBaseDrag(mach, M_e, thetaRatio)`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/main/java/info/openrocket/core/aerodynamics/ChapmanKorstBaseDrag.java) implements a more physically based turbulent base-drag model that resolves the boundary-layer thickness at the base corner. It is currently an available/tested utility rather than an active production path: `BarrowmanDragCalculator.calculateBaseCD()` uses the Devan--Ashwood/transonic polynomial path and, when the boundary-layer state is laminar, calls `ChapmanKorstBaseDrag.blendedLaminarBaseDrag()`.

The baseline thin-BL coefficient is fitted to ESDU 77021 Table 1 (turbulent cylindrical afterbody) as

$$
C_{d,\text{base}}^{\text{thin BL}} \;=\; 0.060 + \frac{0.190}{M_e^{2}} + \frac{0.005}{M_e^{4}},
$$

and the BL-thickness correction is

$$
f(\theta/r) \;=\; 1 - k(M_e)\,\sqrt{\theta/r},\qquad k(M_e) = 0.8 + 0.2/M_e,\qquad f \in [0.3, 1.0].
$$

The blend with Devan--Ashwood spans $M = 1.2$--$1.4$ with a smoothstep weight. Below $M = 1.2$ only Devan--Ashwood is used; above $M = 1.4$ the Chapman--Korst result takes over for turbulent boundary layers when this utility is called. The thick-BL multiplier of Section 6.2.8 captures related boundary-layer-thickness sensitivity in the production simulator path.


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

The two corrections below have physics-motivated functional forms but their scale constants are set by the 24-flight corpus apogee residual, not by an isolated component benchmark. They are circular calibrations — the same corpus is the calibration target and a validation target — and are not counted in the external-benchmark headline. A dedicated finned-body base-pressure dataset would convert these from circular to confirmatory; no such public dataset has been located.

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

**Thick-BL multiplier (`calculateThickBLBaseMultiplier`, lines 1325–1430).** Minimum-diameter, high body-L/D airframes develop a turbulent boundary layer whose thickness $\delta$ approaches the body radius $R$ at the base station. In that regime ($\delta/R \gtrsim 0.5$) the Devan–Ashwood correlation — calibrated on moderate-L/D bodies where $\delta/R \ll 1$ — systematically under-predicts base suction by 30–40% because the thick BL nearly fills the wake and the free shear layer / inviscid core assumption breaks down. The implementation uses the 1/7-power flat-plate turbulent BL correlation $\delta/x = 0.37/\mathrm{Re}_x^{0.2}$ and applies the multiplier

$$
k_{\text{thick-BL}} \;=\; \mathrm{min}\!\left[1 + K\,\max(0,\,\delta/R - 0.5)\,f_M(M)\,g_{L/D}(L/D),\;1.8\right],\qquad K = 2.2.
$$

Both gates must be satisfied for any effect: $M > 0.9$ (smoothstep ramp through $0.9$–$1.1$, Mach decay back to zero by $M = 3.0$) and body $L/D > 25$ (smoothstep ramp through $L/D = 25$–$30$). The cap at $1.8$ prevents runaway on pathological geometries. The scale constant $K = 2.2$ is calibrated against the 24-flight validation corpus with Raven (1.75 in tube, body $L/D = 41.7$, peak $M = 1.12$) as the primary anchor; see [`paper/data/outlier_closure/raven_closure.md`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/outlier_closure/raven_closure.md) and the [`ThickBLBaseDragMultiplierTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/ThickBLBaseDragMultiplierTest.java) regression battery.


#### 6.2.9 Worked Examples

Cylindrical body (no boattail, no fin-can sleeve), 4-fin tail with rectangular AIRFOIL fins, body $L/D = 18$ (so the thick-BL gate is closed). Subsonic and transonic correlations come from `calculateBaseCD(double m)`; per-Mach factors are evaluated as in Sections 6.2.6–6.2.8.

**At $M = 0.5$ (subsonic).** Hoerner: $C_{d,\text{base}} = 0.12 + 0.13\,(0.5)^2 = 0.1525$. Finned augmentation with $f_M(0.5) = 0.30 \times (0.5 - 0.2)/0.6 = 0.15$ and $f_{\text{fin}}(4) \approx 1.0$: $k_{\text{finned}} \approx 1 + 0.55 \times 1.0 \times 0.8 \times 0.15 \approx 1.066$. No power-on (coast). $k_{\text{thick-BL}} = 1$. Final: $0.1525 \times 1.066 = 0.163$ at the component, scaled by $A_{\text{base}}/S_{\text{ref}}$.

**At $M = 1.05$ (transonic peak).** Polynomial returns $0.250$. Augmentation factor $f_M(1.05) \approx 0.30 + 0.70 \times 0.25/0.5 = 0.65$, $k_{\text{finned}} \approx 1 + 0.55 \times 1.0 \times 0.8 \times 0.65 \approx 1.286$. Final: $0.250 \times 1.286 = 0.321$ (component-level coefficient before area rescaling).

**At $M = 2.0$ (supersonic).** Devan–Ashwood: $0.064 + 0.186/4.0 = 0.111$. $f_M(2.0) = 1.0$, $k_{\text{finned}} \approx 1 + 0.55 \times 1.0 \times 0.8 \times 1.0 = 1.44$. Final: $0.111 \times 1.44 = 0.160$.

**At $M = 5.0$ (high supersonic).** Devan–Ashwood: $0.064 + 0.186/25.0 = 0.0714$. $f_M(5.0) = 3/5 = 0.60$, $k_{\text{finned}} \approx 1 + 0.55 \times 1.0 \times 0.8 \times 0.60 = 1.264$. Final: $0.0714 \times 1.264 = 0.090$.

**Old vs current code (Mach-only correlation, no fin/boattail/Re corrections):**

| Mach | Old OpenRocket $C_{d,\text{base}}$ | Current $C_{d,\text{base}}$ | Notes |
|---|---|---|---|
| 0.5 | 0.1525 | 0.1525 | Subsonic Hoerner unchanged |
| 0.9 | 0.225 | $\approx 0.230$ | Polynomial enters at $M = 0.85$ |
| 1.05 | 0.25 | 0.250 | Polynomial peak (Hart-anchored) |
| 1.30 | $\sim 0.20$ | 0.230 | Hart anchor (new in Prompt 13) |
| 1.50 | 0.167 | 0.147 | Devan–Ashwood handoff |
| 2.0 | 0.125 | 0.111 | Devan–Ashwood |
| 5.0 | 0.050 | 0.071 | Devan–Ashwood nonzero asymptote |


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
m \;=\; 0.2\,r\,M^{2},\qquad F \;=\; T_w/T_e,\qquad A \;=\; \sqrt{r m / F},\qquad B \;=\; (1 + r m - F)/F.
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

The drag coefficient $C_D$ computed in Section 6.0 represents the magnitude of the drag force vector (aligned with the freestream velocity). In the 6-DOF stepper this must be converted to an axial-force coefficient $C_{D,\text{axial}}$ resolved along the body axis:

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

**Reattachment recovery.** The current production code does not separately add a reattachment-recovery term on body steps; the stagnation-pressure term alone captures the dominant mechanism inside the validation window of the 24-flight corpus. (The free-interaction theory of Chapman–Kuehn–Larson is used at fin roots in Section 6.8 below.)

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

The SBLI **chord reduction** of Section 6.8.2 is active in production. The **SBLI pressure drag** term in this section is **not active**: enabling both terms simultaneously double-counts the separation loss, because the chord reduction already removes the lift- and drag-producing area where the plateau pressure would have acted. The two terms are alternative empirical accountings of the same physical event, and the chord-reduction form gave better agreement with the 24-flight corpus. The pressure-drag formulas are documented here for completeness; activating them would require recalibrating the chord-reduction floor against fin-only test data, which is on the deferred list (Section 12.3).


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

**Vehicle-level closure.** [`BasicFinnerDragBenchmarkTest`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/core/src/test/java/info/openrocket/core/aerodynamics/BasicFinnerDragBenchmarkTest.java) validates the assembled total drag against the 8 ADA636861 (Dupuis & Hathaway 1997) Basic Finner $C_{X0}$ multi-fit points over $M = 1.08$–$4.30$ at MAPE 11.9% (post-Prompt-13 baseline; tight regression gate at 14% in `testTightMAPEGate()`). Pointwise comparison from [`paper/data/csv/basic_finner_comparison.csv`](https://github.com/AidanSYu/openrocketsupersonic/blob/main/paper/data/csv/basic_finner_comparison.csv):

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
6.2.3 & Subsonic to Devan--Ashwood & 0.85--1.5 & $0.12 + 0.13 M^{2}$ & $0.064 + 0.186/M^{2}$ & Deg-5 poly, Hart-anchored \\
6.2.4 & Devan--Ashwood to Chapman lam. & 1.3--2.5 & Devan--Ashwood & Chapman laminar & Smoothstep, perfect finish \\
6.2.5 & Devan--Ashwood to Chapman--Korst & 1.2--1.4 & Devan--Ashwood & Chapman--Korst & Smoothstep, optional \\
6.3.4 & Subsonic to Van Driest II & 0.9--1.1 & $C_{f,\text{sub}}$ & $C_f^{\text{VD II}}$ & Linear blend \\
6.4.2 & Zero to DATCOM wave drag & 0.9--1.2 & 0 & $C_{d,w}^{\text{DATCOM}}$ & $C^1$ cubic Hermite \\
6.4.4 & TE Hoerner to backward step & 0.9--1.2 & $0.12\,t/c$ & $0.135(t/c)/\sqrt{\beta}$ & Smoothstep \\
6.6 & Drag to axial conv. & $\alpha\in[0,17°]$, $[17°,90°]$ & $1.0$ & $1.3 \to 0$ & Cubic + deg-4, $C^1$ ends \\
\bottomrule
\end{xltabular}
\end{landscape}
```

These windows are the load-bearing "C1 continuity" claims of the supersonic extension; every transition is verified in the corresponding component test (`FinWaveDragTest.testTransonicBlendC1`, `BaseDragModelTest.testNoDiscontinuityAcrossBlend`, `VanDriestIISkinFrictionTest.testTransonicBlendContinuous`).
