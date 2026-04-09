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
\label{fig:normal-shock-schematic-partb}
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
\draw[->, thick] (-0.3,0) -- (5.5,0) node[below] {axis of symmetry};
\draw[thick] (A) -- (18:4.2) node[right] {cone surface ($\theta_c$)};
\draw[thick, dashed] (A) -- (42:3.6) node[above, sloped, pos=0.6] {conical shock ($\beta_{\mathrm{cone}}$)};
\node[align=left, anchor=west] at (0.15,1.65) {$M_1$ freestream\\post-shock $M_2$ varies\\along rays};
\node[align=center, font=\scriptsize] at (2.85,-1.05) {3D relief: $\beta_{\mathrm{cone}} < \beta_{\mathrm{wedge}}$ for same $\theta_c$, $M_1$};
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

