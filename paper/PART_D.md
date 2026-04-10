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
\draw[->, very thick] (-0.3,1.2) -- (8.5,1.2) node[right] {freestream $M_\infty$};
\draw[thick] (1.0,1.2) -- (2.2,2.35) node[midway, above, sloped] {oblique shock};
\fill[blue!8] (1.0,0.35) -- (2.2,2.35) -- (2.0,0.35) -- cycle;
\node[align=left, anchor=west] at (4.0,1.75) {\scriptsize post-shock:\\\scriptsize $M_2<M_\infty$, $p_2>p_\infty$};
\draw[thick, fill=gray!12] (0,0.35) -- (1.8,1.35) -- (1.8,0.35) -- cycle;
\node at (0.9,0.85) {nose};
\draw[thick, fill=gray!12] (1.8,0.35) rectangle (5.2,1.35);
\node at (3.5,0.85) {body tube};
\draw[thick, fill=gray!18] (5.0,0.35) -- (6.2,1.9) -- (5.6,0.35) -- cycle;
\node at (5.5,1.0) {\scriptsize fins};
\draw[densely dashed] (1.8,1.35) -- (2.1,2.0);
\node[font=\scriptsize, align=center] at (2.25,2.35) {shoulder\\PM fan};
\draw[->] (4.0,-0.35) -- (4.0,0.25) node[below=8pt, font=\scriptsize] {stations $x_i$};
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

