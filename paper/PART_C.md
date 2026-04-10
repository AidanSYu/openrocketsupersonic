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
\draw[->, thick] (-0.2,0) -- (5.8,0) node[below] {$x$};
\draw[thick] (0,0) -- (0.35,1.1) -- (1.1,1.35) -- (2.4,1.35) -- (5.5,0.45);
\draw[dashed] (0.35,1.1) -- (-0.2,1.9) node[left, font=\scriptsize] {oblique shock};
\node[above] at (2.8,1.35) {$N$ strips ($\mathrm{d}x$)};
\foreach \x in {0.9,1.5,2.1,2.7,3.3} {\draw (\x,0.05) -- (\x,-0.12);}
\node[below, font=\scriptsize] at (0.05,-0.2) {nose tip};
\node[below, font=\scriptsize] at (5.4,0.2) {$R_{\mathrm{aft}}$};
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
