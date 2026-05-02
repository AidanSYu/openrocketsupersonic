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

**Empirical damping multiplier.** A constant `DAMPING_MULTIPLIER = 3.0` (package-visible for sensitivity testing) is applied to the legacy damping-multiplier output that drives the pitch and yaw damping moments. The factor exists because the linearized theoretical $C_{mq}$ under-predicts the damping required to reproduce realistic apogee-turn behavior in 6-DOF trajectory simulation. Against the ADA636861 free-flight $C_{mq}$ data on the Basic Finner, the combined $\times 3$ multiplier and Gaussian augmentation over-predict damping at $M = 1.05$--$1.12$ by roughly a factor of $3.6$. The multiplier is corpus-calibrated, not externally validated. It is reported as such (not counted in the 27-subsystem external-benchmark headline), and removing it degrades the corpus apogee-turn signature on five flights. The 25-flight closure is dominated by drag and base-pressure terms, so the damping over-prediction does not propagate into the 4.49% headline; it is nonetheless real and unfixed (Section 12.4 item 2).

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

This factor is a compact estimate that avoids per-component decomposition of normal force inside the damping calculation. It is consistent with the body-alone vs finned-body Magnus ratios reported by Platou (BRL Report 1193, 1963), which fall in the 0.3--0.8 range depending on fin loading and Mach number; 0.3 sits at the lower end (the conservative side, since body and fin Magnus forces are opposite in sign and the smaller the body fraction, the smaller the predicted Magnus yaw moment).

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

At high angles of attack ($\alpha > 20°$) the vortex pair shed from the leeward side of a slender body becomes asymmetric due to convective instabilities in the separated shear layers, producing a side force perpendicular to the angle-of-attack plane *even in the absence of roll*. The phenomenon is well-documented in published experimental literature for ogive-cylinder bodies (Paul & Wedemeyer 1982, EOARD-TR-82-7) and can cause significant lateral dispersion in flight trajectories.

The implementation models the asymmetry as

$$C_{y,\text{vortex}} = K_v \cdot C_N \cdot f(\alpha),$$

with empirical asymmetry coefficient $K_v = 0.20$, $C_N$ the current total body normal force coefficient (which already includes the crossflow override of Section 9.5 when applicable), and a linear ramp

$$f(\alpha) = \begin{cases} 0 & \alpha \le 20°,\\ (\alpha - 20°)/20° & 20° < \alpha < 40°,\\ 1 & \alpha \ge 40°.\end{cases}$$

The side force is added to $C_\text{side}$ after all other aerodynamic calculations. At $\alpha = 40°$ the vortex side force is 20% of the body normal force -- a substantial lateral perturbation that often dominates the yaw dynamics during tumble. A `Warning.HIGH_AOA_VORTEX` is issued when the model activates.

The calibration $K_v = 0.20$ is anchored in the ogive-cylinder $C_Y(\alpha)$ data of Paul & Wedemeyer (EOARD-TR-82-7): the bare-body ratio $C_Y/C_N \approx 0.52$ at peak; the implementation's $K_v = 0.20$ corresponds to roughly 62% suppression of that bare-body asymmetry when fins are present, which is consistent with published finned-body data.


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

#### 9.9.2 Magnus Force -- Platou (BRL Report 1193)

The Magnus model is validated against the wind-tunnel measurements of Platou (BRL Report 1193, 1963) on body-alone and finned-body configurations at supersonic speeds. `MagnusBenchmarkTest` uses the implementation default body fraction $0.3$ and compares the predicted $C_{y,p\alpha}$ against BRL 1193 for both configurations. The implementation lies within the measured range $0.3$--$0.8$ for the body fraction and matches the reference body $C_{N\alpha}$ derivation to machine precision.

#### 9.9.3 Vortex Sideforce -- Paul and Wedemeyer (EOARD-TR-82-7)

The asymmetric vortex shedding model uses asymmetry coefficient $K_v = 0.20$, calibrated against the ogive-cylinder $C_Y(\alpha)$ measurements of Paul and Wedemeyer (1982). `VortexSideforceBenchmarkTest` verifies that the predicted side-force magnitude and onset angle fall within the 40--70% expected band of the EOARD-TR-82-7 envelope.

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
2 & Base drag $C_{D,\text{base}}$ & 0.85 & 1.50 & Poly $C^1$ & $0.12+0.13M^2$ & Devan--Ashwood & DragCalc \\
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
18 & Chapman--Korst turb. & 1.20 & 1.40 & Hermite & Devan--Ashwood & Chapman--Korst & CKBaseDrag \\
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
- Entry 18 is an available/tested turbulent base-drag utility; the production base-drag path uses Devan--Ashwood/transonic polynomial plus the optional Chapman laminar correction unless explicitly routed through `ChapmanKorstBaseDrag.blendedBaseDrag()`.
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

Base drag peaks near $M = 1.05$ and is anchored on the supersonic side by the Hart L52E06 plateau through $M \approx 1.30$ before joining Devan--Ashwood at $M = 1.50$. Fin $C_{N\alpha}$, which depends on $1/\beta$, needs the wider $M = 0.90$--$1.50$ supersonic margin because both the Barrowman subsonic and the Ackeret supersonic formulas diverge at $M = 1$ and the interpolation polynomial must span enough range to control the curvature.


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

**Example.** Base drag blend (Entry 2), which must match the subsonic parabola and its slope at $M = 0.85$, pass near the transonic peak ($\sim 0.25$) at $M = 1.05$, pass through the Hart L52E06 anchor at $M = 1.30$, and match Devan--Ashwood with its slope at $M = 1.50$.

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

- **27 subsystems are externally benchmarked against published wind-tunnel, free-flight, or analytical data** with a quantitative acceptance criterion, plus **1 externally anchored negative benchmark** (NACA RM-10) used to bound and exclude a geometry family.
- **9 results are calibrated against the integrated flight corpus** rather than against isolated component data. These are circular calibrations (same corpus is the calibration and validation target) and are *not* counted in the 27-subsystem headline. Each is flagged where it is used (Section 11.6.5).
- **25-flight integrated validation corpus** (Rocket Flight Database v1.0, [DOI: 10.5281/zenodo.19976138](https://doi.org/10.5281/zenodo.19976138)): 25/25 within $\pm 10\%$, 15/25 within $\pm 5\%$, average $\lvert\text{error}\rvert = 4.49\%$, 0 abnormal endings. Lower aggregate error than the recorded RASAero II predictions on the same flights ($5.26\%$, 22/25 within $\pm 10\%$).
- **MESOS 293K** (Mach 4.18 / 293,488 ft): apogee $-0.6\%$, peak velocity $+4.0\%$, peak Mach $+3.6\%$.


### 11.1 Test Suite Overview

The aerodynamic validation suite currently comprises **85 tracked JUnit test classes** in the `info.openrocket.core.aerodynamics` package hierarchy (87 tracked Java files including support/export helpers), plus one workspace-local `SimVRealCorpusAblationTest` used for the May 1 import-parity ablation. The claim inventory consists of 27 externally benchmarked subsystem results, 9 integrated flight-data closures, and 1 negative external benchmark (NACA RM-10). Not every claim has equal evidence: externally benchmarked results are independently matched against published experimental or tabulated data with a quantitative acceptance criterion; integrated flight-data closures are validated against the 25-flight Rocket Flight Database corpus rather than against an isolated published component dataset; numerical-consistency tests verify that the implementation reduces to its analytical limit or matches its own boundary conditions; and a small number of empirically tuned coefficients are documented as such. Every claim in this chapter is reported with its evidence type, not as a uniformly closed validation.

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

The current result is **MAPE 11.9%**, below the 14% aggregate regression criterion. Four mid-supersonic points exceed 14% pointwise error (-18.0%, -20.0%, -19.2%, and -14.6%), so the gate is an aggregate MAPE gate, not a per-point claim. This is the first vehicle-level total-drag validation for the extended aerodynamic module against published external data. It does not by itself close the broader high-Mach finned-body family, because the NACA RM-10 case remains a documented open mismatch for a structurally different geometry (Section 11.3.6).

#### 11.3.5 AGARD-B Standard Model (AEDC-TR-70-100)

AGARD-B is a standard wind-tunnel reference model used internationally for facility calibration and CFD validation. `AgardBDragBenchmarkTest` validates total and component-level drag against AEDC-TR-70-100 across the subsonic and transonic range ($M = 0.2$--$1.0$), with the trend and component split passing qualitatively.

#### 11.3.6 Excluded Geometry Family -- RM-10 (NACA TN 3320)

`NacaRm10FinnedBodyDragBenchmarkTest` compares the implementation against the RM-10 finned-body free-flight data of NACA TN 3320. The result is **MAPE 80%** -- a large, externally anchored *negative* benchmark. This is recorded as an explicit "excluded geometry family": the RM-10 combines a *high-fineness parabolic forebody*, a *tapered afterbody/boattail*, and *60° swept-arc fins*, none of which is well represented by the Barrowman-family slender-body assumptions. The policy is explicit: do not tune RM-10 down at the cost of regressing Basic Finner or the integrated flight corpus.

Including this benchmark in the validation pack is a deliberate honesty choice. RM-10 documents the *boundary* of the model's geometric domain rather than counting as a closed validation. It is the only externally anchored negative benchmark in the present work.

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
| $x_{CP}/d$ | 10 | 0.054 | 0.061 | **7.10%** | 14.6% | +0.054 |

Interpretation, paraphrasing the NASA TM X-653 closure memo (`paper/data/md/nasa_tm_x653_validation_report.md`): below $M = 3$ the implementation tracks the experimental curve within $9\%$ on $C_N$ and within $4\%$ on $x_{CP}/d$ at $M = 3.0$ (down from a 125% error before the M=3.0 ESDU TransonicSimilarity guard was added). At $M = 4.06$--$5.82$ the implementation over-predicts $C_N$ by 13--18% and shows a $x_{CP}/d$ plateau because the $K_1 = 0.85$ floor prevents fin $C_{N\alpha}$ from decaying with Mach as fast as the experiment for low-aspect-ratio fins. This is an honest, documented model trade-off; the case is reported as externally benchmarked at $\le 8\% / \le 7.1\%$ MAPE.

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
| Magnus body fraction (0.3) | within BRL 1193 measured 0.3--0.8 range | external benchmark |
| Vortex asymmetry ($K_v = 0.20$) | within 40--70% expected range | external benchmark |


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

The integrated 6-DOF trajectory predictions are validated against a corpus of **25 real high-power, amateur, and research-program rocket flights** with measured GPS, barometric, optical, or accelerometer apogee. The corpus is published as the *Rocket Flight Database* v1.0 ([DOI: 10.5281/zenodo.19976138](https://doi.org/10.5281/zenodo.19976138), CC-BY-4.0). The measured apogees and RASAero II reference predictions are sourced from the comparison set published by Charles E. Rogers (RASAero II author) at <https://www.rasaero.com/comparisons-alt.htm>; the OpenRocket Plus predictions are produced by importing the same `.CDX1` vehicle files into the simulator and running with default settings.

This is the "integrated flight data" capstone: it does not isolate any single subsystem, but it demonstrates that the assembly of physics in Parts A--D produces trajectory predictions consistent with measured reality across Mach 0.54--4.33 and apogees from 3 577 ft to 293 488 ft.

#### 11.6.1 Aggregate Result (25 Flights)

| Metric | This work | RASAero II |
|---|---:|---:|
| Avg $\lvert\text{error}\rvert$ | **4.49%** | 5.26% |
| Within $\pm 5\%$ | **15/25 (60.0%)** | 13/25 (52.0%) |
| Within $\pm 10\%$ | **25/25 (100%)** | 22/25 (88.0%) |
| Worst case | $+8.7\%$ (Kinsel) | $+11.5\%$ (T&L) |
| Mean signed error | $-0.1\%$ | $+2.3\%$ |
| Abnormal endings | 0 | n/a |

The extended model has a per-case absolute-error advantage of $\ge 3$ pp on 8 of 25 flights; RASAero II has the corresponding advantage on 4 flights (Rabia, Rabia Short Fin Can, Kinsel, Proteus 6); the remaining 13 are within $\pm 3$ pp of each other. The aggregate-error advantage of 0.77 pp (this work) is concentrated in the highest-Mach flights (Torrent, Kline-Rogers, FMJ Black Rock-6, AeroPac 104K, Don't Debate This), where the supersonic-extension models contribute the most.

#### 11.6.2 Per-Case Table (Sorted by Peak Mach)

Errors are signed; positive = over-predicted apogee. $\Delta = |\text{RAS err}| - |\text{this-work err}|$ (positive = this work closer). All RASAero II values are as published by Rogers (loc. cit.); the canonical machine-readable form is the *Rocket Flight Database* v1.0.

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
| 25 | MESOS 293K | 3,910 | 4.33 | 293,488 | 289,789 | 291,601 | $-1.3\%$ | $-0.6\%$ | $+0.7$ |

```{=latex}
\end{landscape}
```

#### 11.6.3 High-Altitude Two-Stage Detail (Mach 4.18 / 293,488 ft)

| Metric | Real | RASAero II | This work | RAS err | This-work err |
|---|---:|---:|---:|---:|---:|
| Apogee (ft) | 293,488 | 289,789 | 291,601 | $-1.3\%$ | $\mathbf{-0.6\%}$ |
| Max velocity (ft/s) | 4,047 | -- | 4,210 | -- | $+4.0\%$ |
| Peak Mach | 4.18 | 4.23 | 4.33 | $+1.2\%$ | $+3.6\%$ |
| Booster burnout / sep (s) | -- | -- | 7.941 | -- | -- |
| Sustainer ignition (s) | -- | -- | 23.103 | -- | -- |
| Sustainer burnout (s) | -- | -- | 33.692 | -- | -- |
| Apogee time (s) | -- | -- | 147.692 | -- | -- |

Launch site: Black Rock Desert, NV, 3,910 ft (read from the imported launch-site altitude). This case exercises stage-aware nozzle pressure-thrust correction, two-stage motor sequencing, and Mach 3+ coast aerodynamics simultaneously; the closure (`paper/data/outlier_closure/mesos_293k_closure.md`) confirms the apogee criterion ($< \pm 10\%$) and the velocity criterion ($< \pm 5\%$).

#### 11.6.4 Active Mechanisms Producing the Baseline

The closure above is *not* a per-case multiplier. It is the convergence of four shared mechanisms applied to the entire corpus and to the external benchmarks simultaneously:

- Stage-aware nozzle pressure-thrust correction during powered flight (`RK4SimulationStepper`).
- RASAero `Turbulence=True` parsed into `forceTurbulentBL`; bounded to zero for non-perfect-finish imports by an ablation study, while still active for perfect-finish laminar fixtures.
- Geometry-gated finned-base drag augmentation (saturated fin-count scaling, rounded-fin transonic wake, expanding fin-can sleeve, four-fin low-subsonic ramp).
- Trajectory-derived peak Mach via `data.getMaxMachNumber()` in all three reporting paths.

#### 11.6.5 Results Calibrated Against the 25-Flight Corpus

The following results contribute to the trajectory closure but are *not* externally benchmarked at the component level — they are calibrated against the integrated 25-flight corpus. They are circular in the sense that the calibration target and the validation target overlap. None of them are counted in the "27 externally benchmarked subsystems" headline.

| Claim | What is unverified at the component level | What would close the gap |
|------|-------|-----------|
| Cmq $\times 3$ multiplier (Section 9.9.5) | Over-predicts $\sim 3.6\times$ at $M = 1.05$--$1.12$ when measured against ADA636861 alone, but the multiplier is needed to match apogee-turn timing on the corpus | A second free-flight $C_{mq}$ dataset that is *not* used to set the multiplier |
| Transonic $C_{mq}$ Gaussian (peak 3.5) | Same dataset, same over-prediction direction | Same |
| Finned-body base drag augmentation | The fin-presence wake correction is set by corpus apogee residual; Hart 1952 measures body-alone | Public finned-body base-pressure dataset across $M = 0.7$--$3$ |
| Power-on nozzle / pressure thrust | MESOS 293K is the only multi-stage powered-flight closure | A second multi-stage flight with telemetry |
| Min-diameter supersonic flight (Raven, DDT) | Apogee closes but no isolated component check | Dedicated min-diameter free-flight dataset |
| Termination / descent dynamics | 0/25 abnormal endings, but no isolated $C_N(\alpha)$ / $C_m(\alpha)$ at high $\alpha$ | High-$\alpha$ dataset (see Section 12.4 item 6) |
| Full 6-DOF trajectory fidelity | 4.49% mean apogee error on the corpus is the validation, not a component check | (Headline metric — not separable) |
| Geometry-import parity | RASAero `ModifiedBarrowman` stability switch is parsed but not honored | Implement the alternate stability path |

The headline corpus closure is dominated by drag and base-pressure terms, not by damping. Removing the $C_{mq}$ multiplier or the Gaussian augmentation degrades the apogee-turn signature on five flights but does not move the headline 4.49% by more than $\sim 0.5$ pp; the corpus is therefore mostly drag-validated, not damping-validated.


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
| Base drag | basic formula | Devan--Ashwood + $C^1$ transonic blend + optional Chapman laminar path |
| Fin local flow | freestream Mach | post-shock Mach from ShockGeometry for fin stability / PNK / SBLI chord reduction |
| Hypersonic | no model | Modified Newtonian blended $M = 4$--6 |
| Static stability | no supersonic correction | Galejs + Allen-Perkins crossflow + PNK + ESDU similarity (Ch. 8) |
| Dynamic stability | apogee-turn heuristic only | Cmq strip theory + Gaussian augmentation + Magnus + Euler gyroscopic |
| Trajectory integrator | RK4 with limited gates | RK4 with quaternion + adaptive timestep + sanitization + warning diagnostics |
| Valid Mach range | $M < 2$ | vehicle-level (6-DOF) validated to $M \approx 4.3$; component-level cone foredrag validated to $M \approx 17$ (single benchmark) |


## 12. Conclusions and References


### 12.1 Summary of Contributions

This work has extended the OpenRocket aerodynamic simulation framework from a subsonic/low-transonic tool valid to roughly $M = 2$ into a compressible-flow simulation whose validated envelope is two-tier: vehicle-level (6-DOF integrated trajectory) is validated through $M \approx 4.3$ against the 25-flight Rocket Flight Database v1.0 ([DOI: 10.5281/zenodo.19976138](https://doi.org/10.5281/zenodo.19976138)), and component-level cone foredrag is validated to $M \approx 17$ against a single isolated benchmark (DTIC AD0487365). The principal contributions:

1. **Gas dynamics foundation.** A complete set of compressible flow solvers -- oblique shock relations ($\theta$-$\beta$-$M$ with bisection), Taylor--Maccoll cone flow (ODE integration), normal shock jump conditions, and Prandtl--Meyer expansion fan relations -- validated against NACA Report 1135 and cone-flow reference tables: normal shocks to $7\times10^{-5}$, oblique-shock wave angle to $0.021\%$, Prandtl--Meyer angle to $0.004^\circ$, and Taylor--Maccoll cone-shock angle to $0.825\%$ relative. These solvers form the backbone for every subsequent wave drag, pressure coefficient, and shock-geometry calculation.
2. **Analytical wave drag models.** Replacement of the legacy NASA TR-R-100 tables with physics-based wave drag computations: Taylor--Maccoll exact solution for conical noses, second-order shock-expansion theory for ogive noses, DATCOM Section 4.1.5.1 (Puckett--Stewart) fin wave drag with subsonic/supersonic LE classification, and the Dahlem--Buck shape factors for power-law / Haack noses.
3. **Shock geometry pre-pass architecture.** A new `ShockGeometry` computation walks the rocket body nose-to-tail, computing post-shock Mach, pressure, and temperature at each axial station. The production consumer is the stability path, primarily `FinSetCalc`, where local Mach corrects fin normal-force, PNK interference, and SBLI chord reduction. Body stability, fin pressure drag, roll damping, base drag, and wave drag remain freestream-based scope boundaries. Zero overhead at subsonic speeds (passthrough design).
4. **Compressible boundary-layer modeling.** Van Driest II compressible transformation (NASA TN D-6945, Hopkins 1972) for supersonic skin friction, replacing the incompressible Eckert formulas. Reduces friction drag by 30--75% at $M = 2$--5. The Sutherland viscosity law replaces the legacy linear fit; the NIST/Incropera JUnit gate is $<3\%$ over 100--800 K, and the current formula export is MAPE 0.012%.
5. **Hypersonic extension via Modified Newtonian.** $C_p = C_{p,\max}\sin^2\theta$ with $C_{p,\max}$ from the Rayleigh pitot formula for $M > 5$, blended with shock-expansion over $M = 4$--6 (cubic Hermite, $C^1$). Component-level cone foredrag is validated to $M \approx 17$ (single isolated benchmark, DTIC AD0487365 MAPE 19.7%); vehicle-level integrated trajectory is validated through $M \approx 4.3$ against the 25-flight corpus.
6. **$C^1$-continuous regime blending.** Up to **19 distinct blending regions** (Chapter 10) using cubic Hermite, constrained polynomials, and AP09 rational functions ensure all aerodynamic coefficients are $C^1$ across every Mach regime boundary, eliminating the simulation instability and time-step collapse that would otherwise occur at transitions.
7. **Dynamic stability derivatives and Euler gyroscopic coupling.** Pitch damping ($C_{mq}$) computed from per-component $C_{N\alpha}$ and moment arms with a transonic Gaussian augmentation, $C_{m\dot{\alpha}}$ via the Tobak--Wehrend slender-body ratio, full Magnus force/moment derivatives with body fraction $0.3$, and the full Euler $\boldsymbol{\omega} \times \mathbf{I}\boldsymbol{\omega}$ coupling in the 6-DOF integrator (with a 500 Pa dynamic-pressure gate against ballistic-descent stiffness).
8. **High-AoA crossflow normal force and simulation robustness.** A bluff-body crossflow drag model with proportional moment scaling that prevents artificial torque divergence at post-stall AoA. SBLI separation-length and $C_{p,\text{plateau}}$ floors, fin $K_3$ and polynomial-denominator floors, and per-coefficient sanitization caps make the integrator robust against transonic singularities, degenerate geometry, and floating-point overflow.
9. **Chapman laminar base drag.** $C_{pb,\text{lam}} = 1870/(M^2\sqrt{Re_L})$ for low-$Re$ or polished-finish rockets (NACA TN 3393 MAPE 4.4%). The Chapman--Korst turbulent method remains an available/tested utility for future production routing, not a default active path.
10. **Comprehensive validation with explicit evidence types.** 27 externally benchmarked subsystem results, 9 results calibrated against the integrated 25-flight corpus rather than isolated component data (flagged at each occurrence and excluded from the 27-subsystem headline), 1 negative external benchmark (NACA RM-10, formally excluded from the headline corpus), and the 25-flight integrated corpus published as the Rocket Flight Database v1.0, all locked in automated regression tests.


### 12.2 Validation Summary

Headline summary restated for the conclusions chapter:

- **27 subsystems externally benchmarked** against published wind-tunnel, free-flight, or analytical data with quantitative acceptance criteria (Sections 11.2 through 11.5).
- **9 results calibrated against the integrated 25-flight corpus** rather than isolated component data. Listed individually in Section 11.6.5 with the gap each one would need to close to become an external benchmark.
- **1 externally anchored negative benchmark** (NACA RM-10, MAPE 80%) that bounds and excludes a high-fineness parabolic / tapered-afterbody / 60° swept-arc-fin family (Section 11.3.6).
- **25-flight integrated corpus** (Rocket Flight Database v1.0, [DOI: 10.5281/zenodo.19976138](https://doi.org/10.5281/zenodo.19976138)): avg $\lvert\text{err}\rvert = 4.49\%$, 25/25 within $\pm 10\%$, 15/25 within $\pm 5\%$, 0 abnormal endings; better aggregate accuracy than the RASAero II predictions on the same imported geometries (5.26%, 22/25 within $\pm 10\%$).
- **Flight 25, MESOS 293K (Mach 4.18 measured / 4.33 predicted, 293,488 ft)**: apogee $-0.6\%$, velocity $+4.0\%$, peak Mach $+3.6\%$.

Two headline outcomes summarize the extension. (i) Vehicle-level integrated trajectory: extended OpenRocket aggregate apogee error 4.49% across the 25-flight corpus, versus 5.26% for the recorded RASAero II predictions on the same imported geometries (lower aggregate error on the *same* geometries). (ii) Validated envelope: the original OpenRocket's reliable range of $M < 2$ extends to vehicle-level closure through $M \approx 4.3$ in this work, with component-level cone foredrag validated to $M \approx 17$ against a single isolated benchmark.

### 12.3 Subsonic Compatibility

At $M < 1.0$ the extended code paths are either inactive (`ShockGeometry` returns a passthrough with unit ratios; wave-drag models return zero; Van Driest II reduces to incompressible) or reduce identically to the original Barrowman formulas. The subsonic passthrough cost is approximately 200 ns per call -- negligible compared to the $\sim 180$ microsecond component calculation time. All original subsonic regression tests continue to pass without modification, and the integrated 25-flight corpus shows no subsonic bias (the lowest-Mach cases lie within $\pm 2\%$ of the truth on average).


### 12.4 Known Limitations

The following limitations are real and known. They are stated here in plain terms, with the reason each remains unfixed in this revision.

**1. NACA RM-10: 80% drag over-prediction at $M = 1.59$.** The model over-predicts the RM-10 zero-lift drag coefficient by 80%. RM-10 is a high-fineness parabolic body with a tapered afterbody and 60° swept-arc fins. This geometry family is formally excluded from the 4.49% headline corpus claim. The over-prediction is driven by the supersonic shock-expansion strip integration over the parabolic body, which assumes the leading shock remains attached over the full body length; on RM-10's slender afterbody it does not. A geometry-family-specific correction (probably a separated-flow or boattail-relief term gated on slenderness ratio) would close the gap, but every attempt to date also regressed Basic Finner or the 25-flight corpus. **Not fixed because** the calibration set required to disentangle these regimes does not yet exist in the public literature in a form that can be digitized.

**2. Pitch damping ($C_{mq}$) over-predicts by $3.6\times$ at $M = 1.05$--$1.12$.** Measured against ADA636861 free-flight $C_{mq}$ data on the Basic Finner. The over-prediction comes from the combination of a constant $\times 3$ multiplier on per-component damping and a transonic Gaussian augmentation peaking at $3.5\times$ near $M = 1$. Both constants were calibrated against the integrated 25-flight apogee-turn signature, not against component-level damping measurements. Removing the augmentation breaks the apogee-turn closure on five of the 25 corpus flights. **Not fixed because** correcting the transonic peak requires a second independent free-flight $C_{mq}$ dataset to retune against — recalibrating against ADA636861 would invalidate it as a benchmark — and no such dataset has been located.

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
Magnus body fraction & $0.3$ & Platou (BRL 1193, 1963), 0.3--0.8 & \texttt{calculateDampingMoments} \\
Fin damping cap & $\min(n, 4)$ & Diminishing returns beyond 4 fins & \texttt{getDampingMultiplier} \\
Body damping coefficient & $0.275$ & Body contribution to pitch damping & \texttt{getDampingMultiplier} \\
Vortex asymmetry $K_v$ & $0.20$ & Paul \& Wedemeyer (1982); 62\% fin suppression & RK4 vortex term \\
Vortex onset / saturation & $20° / 40°$ & Paul \& Wedemeyer & same \\
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
Chapman--Korst turbulent base drag \newline (\seqsplit{ChapmanKorstBaseDrag.java}) & \textbf{Off (laminar on)} & The laminar Chapman path is active and validated against TN 3393. The turbulent Chapman--Korst helper exists but the production base-drag path uses the Devan--Ashwood + transonic-polynomial blend, which is what the corpus calibration is anchored against. \\
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


### 12.7 Acknowledgments, Affiliation, Conflict of Interest, and Reproduction Recipe

#### 12.7.1 Acknowledgments

Acknowledgments will be added prior to camera-ready. <!-- TODO(author): list collaborators, dataset providers (Rogers / RASAero II archive maintainers, individual flight contributors to the Rocket Flight Database v1.0), and any reviewers / mentors to thank. -->

#### 12.7.2 Author Affiliation

Sole author: Aidan Yu. <!-- TODO(author): confirm institutional affiliation for the AST submission front-matter (Duke University per `paper/Thesis/zenodo-deposit.md`); add ORCID; add corresponding-author email. -->

#### 12.7.3 Conflict of Interest

The author declares no known conflict of interest. <!-- TODO(author): confirm and finalize COI declaration prior to camera-ready, including any funding disclosures. -->

#### 12.7.4 Funding

<!-- TODO(author): state funding sources, or explicitly declare "no external funding received," prior to camera-ready. -->

#### 12.7.5 Software Availability and DOI

The OpenRocket Plus source code is available at <https://github.com/AidanSYu/openrocketsupersonic>. A persistent software archive will be deposited on Zenodo: `[SOFTWARE-DOI-TODO]`. The validation dataset (Rocket Flight Database v1.0) is already deposited and is citable as <https://doi.org/10.5281/zenodo.19976138>.

#### 12.7.6 Reproduction Recipe for the 25-Flight Corpus Closure

The headline aggregate apogee error of 4.49% across the 25-flight corpus is reproducible from the source tree as follows. The pinned commit for the manuscript revision is `<MANUSCRIPT-COMMIT-TODO>` on branch `supersonic-aero-dev`; replace `<COMMIT>` below with the value reported by `git rev-parse HEAD` after the manuscript-tag commit is created.

```bash
git clone https://github.com/AidanSYu/openrocketsupersonic.git
cd openrocketsupersonic
git checkout <COMMIT>          # or the manuscript tag once minted
./gradlew core:test --tests "info.openrocket.core.aerodynamics.SimVRealBenchmarkTest"
```

On Windows, substitute `gradlew.bat` for `./gradlew`. Expected runtime: approximately 11 minutes for the full aerodynamics test suite, of which `SimVRealBenchmarkTest` is a fraction. Per-flight outputs and the aggregate error summary are written under `core/build/reports/tests/test/` and `core/build/test-results/test/`. The per-case CSV that anchors the manuscript table is generated as `paper/data/csv/simvreal_baseline_2026_05_01.csv` (frozen at the same commit). The companion head-to-head comparison artifact (this work versus the recorded RASAero II predictions on the same imported geometries) is `paper/data/md/rasaero_head_to_head_2026_05_01.md`. The corpus itself, including the `.CDX1` import files and Rogers-published RASAero II reference apogees, is archived at <https://doi.org/10.5281/zenodo.19976138>.

A regression tolerance of $\pm 2$ percentage points per case is enforced by the test harness; deviations beyond this band fail the build and indicate either an environment difference (JVM, gradle daemon state, motor-thrust-curve cache) or an unintended modeling change.


### References

1. Ackeret, J. (1925). "Luftkrafte auf Flugel, die mit grosserer als Schallgeschwindigkeit bewegt werden." *Zeitschrift fur Flugtechnik und Motorluftschiffahrt*, 16, pp. 72--74.
2. Allen, H. J. and Perkins, E. W. (1951). "A Study of Effects of Viscosity on Flow Over Slender Inclined Bodies of Revolution." NACA Report 1048. `[CITATION-TODO: PDF/digitized data not in repo; cited only as the originating source for the crossflow-analogy method name. Verify and attach before camera-ready.]`
3. Ames Research Staff (1953). "Equations, Tables, and Charts for Compressible Flow." NACA Report 1135.
4. Anderson, J. D. (2006). *Hypersonic and High-Temperature Gas Dynamics*, 2nd ed. AIAA Education Series.
5. Anderson, J. D. (2017). *Modern Compressible Flow: With Historical Perspective*, 4th ed. McGraw-Hill.
6. AP09 (2009). "Aeroprediction Code Methodology (AP09)." Code-cited methodology note for the AP09-style rational blend implemented in `RationalBlend.java`; exact public report metadata is not present in the repository.
7. Barrowman, J. S. (1967). "The Practical Calculation of the Aerodynamic Characteristics of Slender Finned Vehicles." M.S. Thesis, The Catholic University of America.
8. Chapman, D. R. (1950). "Base Pressure at Supersonic Velocities." NACA TN 2137. `[CITATION-TODO: PDF/digitized data not in repo; load-bearing for the laminar base-drag $C_\text{LAM}=1870$ scaling in Section 6.2.4. Verify and attach before camera-ready.]`
9. Chapman, D. R. (1951). "An Analysis of Base Pressure at Supersonic Velocities and Comparison with Experiment." NACA Report 1051.
10. Champigny, P. and Lacau, R. G. (1994). "Lateral Aerodynamics of a Missile at High Angles of Attack." AGARD CP-536, as cited in `BarrowmanCalculator` and `VortexSideforceBenchmarkTest`; the repository's local AGARD CP-536 PDF is a different proceedings volume and is not used as a source artifact for this claim.
11. DATCOM (1978). "USAF Stability and Control DATCOM." Air Force Flight Dynamics Laboratory, AFFDL-TR-79-3032, revised.
12. Devan, L. and Ashwood, R. (1965). "The Base Drag of Blunt-Trailing-Edge Airfoils and Bodies at Transonic and Supersonic Speeds." NASA TN D-721. `[CITATION-TODO: PDF/digitized data not in repo; load-bearing for the production turbulent base-drag correlation. Verify and attach before camera-ready.]`
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
23. Jorgensen, L. H. (1977). "Prediction of Static Aerodynamic Characteristics for Slender Bodies Alone and with Lifting Surfaces to Very High Angles of Attack." NASA TN D-6996. `[CITATION-TODO: PDF not in repo; the related Jorgensen TR R-474 (1973) PDF is in the repo and is the primary anchor for the $C_{d,c}=1.20$ crossflow constant (ref 22). Verify whether ref 23 is needed independently or can be removed before camera-ready.]`
24. Perkins, E. W. and Jorgensen, L. H. (1952). "Investigation of the Drag of Various Axially Symmetric Nose Shapes of Fineness Ratio 3 for Mach Numbers from 1.24 to 3.67." NACA RM A52H28.
25. NACA (1954). "Free-Flight Measurements of the Zero-Lift Drag of Several Wings at Mach Numbers from 1.1 to 1.6." NACA TN 3650.
26. Jackson, H. H., Rumsey, C. B., and Chauvin, L. T. (1954). "Flight Measurements of Drag and Base Pressure of a Fin-Stabilized Parabolic Body of Revolution (NACA RM-10) at Different Reynolds Numbers and at Mach Numbers from 0.9 to 3.3." NACA TN 3320.
27. Reller, J. O., Jr. and Hamaker, F. M. (1955). "An Experimental Investigation of the Base Pressure Characteristics of Nonlifting Bodies of Revolution at Mach Numbers from 2.73 to 4.98." NACA TN 3393.
28. Stoney, W. E. (1961). "Collection of Zero-Lift Drag Data on Bodies of Revolution from Free-Flight Investigations." NASA TR-R-100.
29. Jorgensen, L. H., Spahr, J. R., and Hill, W. A., Jr. (1962). "Comparison of the Effectiveness of Flares with That of Fins for Stabilizing Low-Fineness-Ratio Bodies at Mach Numbers from 0.6 to 5.8." NASA TM X-653.
30. Nielsen, J. N. (1960). *Missile Aerodynamics*. McGraw-Hill.
31. Paul, R. and Wedemeyer, E. (1982). "Aerodynamic Characteristics of Ogive-Cylinder Bodies at High Angles of Attack." EOARD-TR-82-7. `[CITATION-TODO: PDF/digitized data not in repo; load-bearing for the vortex-asymmetry $K_v=0.20$ calibration (Section 9.9.3). Verify and attach before camera-ready.]`
32. Pitts, W. C., Nielsen, J. N., and Kaattari, G. E. (1957). "Lift and Center of Pressure of Wing-Body-Tail Combinations at Subsonic, Transonic, and Supersonic Speeds." NACA Report 1307. `[CITATION-TODO: PDF/digitized data not in repo; load-bearing for the PNK $F_{WB}/F_{BW}$ interference factors (Table 12.1). Verify and attach before camera-ready.]`
33. Platou, A. S. (1963). "The Magnus Force on a Short Body at Supersonic Speeds." BRL Report 1193. `[CITATION-TODO: PDF/digitized data not in repo; load-bearing for the Magnus body-fraction $0.3$ calibration (Section 9.9.2). Verify and attach before camera-ready.]`
34. Puckett, A. E. and Stewart, H. J. (1947). "Aerodynamic Performance of Delta Wings at Supersonic Speeds." *Journal of the Aeronautical Sciences*, 14(10).
35. Sutherland, W. (1893). "The Viscosity of Gases and Molecular Force." *Philosophical Magazine*, Series 5, 36(223), pp. 507--531.
36. Tobak, M. and Wehrend, W. R. (1956). "Stability Derivatives of Cones at Supersonic Speeds." NACA TN 3788.
37. Anderson, C. F. (1970). "An Investigation of the Aerodynamic Characteristics of the AGARD Model B for Mach Numbers from 0.2 to 1.0." AEDC-TR-70-100, Arnold Engineering Development Center. `[CITATION-TODO: PDF/digitized data not in repo; load-bearing for the AGARD-B benchmark (Section 11.3.5). Verify and attach before camera-ready.]`
38. AEDC (1976). "Experimental Roll-Damping, Magnus, and Static-Stability Characteristics of Two Slender Missile Configurations at High Angles of Attack (0 to 90 Deg) and Mach Numbers 0.2 Through 2.5." AEDC-TR-76-58.
39. US Standard Atmosphere (1976). "U.S. Standard Atmosphere, 1976." NOAA/NASA/USAF, U.S. Government Printing Office.
40. Van Driest, E. R. (1956). "The Problem of Aerodynamic Heating." *Aeronautical Engineering Review*, 15(10), pp. 26--41.
41. Viswanath, P. R. (1996). "Flow Management Techniques for Base and Afterbody Drag Reduction." *Progress in Aerospace Sciences*, 32(2--3), pp. 79--129.
42. Whitcomb, R. T. (1956). "A Study of the Zero-Lift Drag-Rise Characteristics of Wing-Body Combinations Near the Speed of Sound." NACA Report 1273. `[CITATION-TODO: PDF/digitized data not in repo; cited only for the method name "Whitcomb area rule" used to label the off-status integrator in Table 12.2. Drop or attach before camera-ready.]`
43. Zipfel, P. H. (2007). *Modeling and Simulation of Aerospace Vehicle Dynamics*, 2nd ed. AIAA Education Series.
44. Chapman, D. R., Kuehn, D. M., and Larson, H. K. (1958). "Investigation of Separated Flows in Supersonic and Subsonic Streams with Emphasis on the Effect of Transition." NACA Report 1356. `[CITATION-TODO: PDF/digitized data not in repo; load-bearing for the free-interaction SBLI theory at fin roots (Section 6.8). Verify and attach before camera-ready.]`

**External validation artifacts:**

- Yu, A. (2026). *Rocket Flight Database, v1.0* [Data set]. Zenodo. <https://doi.org/10.5281/zenodo.19976138>.
- Rogers, C. E. *RASAero II Comparisons with Altitude Data.* <https://www.rasaero.com/comparisons-alt.htm>. Source for measured apogees and reference RASAero II predictions.

**Internal validation artifacts** (not external references; included for traceability):

- `paper/data/corpus_summary_2026_05_01.md` -- 25-flight integrated corpus baseline.
- `paper/data/csv/simvreal_baseline_2026_05_01.csv` -- per-case CSV regression baseline.
- `paper/data/md/rasaero_head_to_head_2026_05_01.md` -- this work versus RASAero II head-to-head on the same imported flights.
- `paper/data/md/dynamic_stability_benchmark.md` -- full Mach sweep for $C_{mq}$, roll damping, Magnus.
- `paper/data/md/nasa_tm_x653_validation_report.md` -- NSCFB CNa / xCP closure memo.
- `paper/data/outlier_closure/*.md` -- per-case closure memos (raven, kinsel, mesos_293k, dontdebatethis, proteus6, fmj_balls005, subsonic_nonaero_outliers).
