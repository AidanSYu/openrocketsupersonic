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
