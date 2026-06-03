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


