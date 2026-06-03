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

**Empirical damping multiplier.** A constant `DAMPING_MULTIPLIER = 3.0` (package-visible for sensitivity testing) is applied to the legacy damping-multiplier output that drives the pitch and yaw damping moments. The factor exists because the linearized theoretical $C_{mq}$ under-predicts the damping required to reproduce realistic apogee-turn behavior in 6-DOF trajectory simulation. Against the ADA636861 free-flight $C_{mq}$ data on the Basic Finner, the combined $\times 3$ multiplier and Gaussian augmentation over-predict damping at $M = 1.05$--$1.12$ by roughly a factor of $3.6$; the Sznajder 2025 ANSYS Fluent CFD comparator independently shows a +110 to +160% overshoot at $M = 1.08$--$1.11$. The multiplier is corpus-calibrated, not externally validated. It is reported as such (not counted in the 20-subsystem external-benchmark headline), and removing it degrades the corpus apogee-turn signature on five flights. The 25-flight closure is dominated by drag and base-pressure terms, so the damping over-prediction does not propagate into the MAE 4.74% headline; it is nonetheless real and unfixed (Section 12.4 item 2).

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

This factor is a compact estimate that avoids per-component decomposition of normal force inside the damping calculation. It is consistent with the body-alone vs finned-body Magnus ratios reported by Platou ("Magnus Characteristics of Finned and Nonfinned Projectiles," *AIAA Journal* 3(1), 83–90, 1965), which fall in the 0.3--0.8 range depending on fin loading and Mach number; 0.3 sits at the lower end (the conservative side, since body and fin Magnus forces are opposite in sign and the smaller the body fraction, the smaller the predicted Magnus yaw moment).

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

At high angles of attack ($\alpha > 20°$) the vortex pair shed from the leeward side of a slender body becomes asymmetric due to convective instabilities in the separated shear layers, producing a side force perpendicular to the angle-of-attack plane *even in the absence of roll*. The phenomenon is qualitatively well-documented for ogive-cylinder bodies at high incidence and can cause significant lateral dispersion in flight trajectories.

The implementation models the asymmetry as

$$C_{y,\text{vortex}} = K_v \cdot C_N \cdot f(\alpha),$$

with empirical asymmetry coefficient $K_v = 0.20$, $C_N$ the current total body normal force coefficient (which already includes the crossflow override of Section 9.5 when applicable), and a linear ramp

$$f(\alpha) = \begin{cases} 0 & \alpha \le 20°,\\ (\alpha - 20°)/20° & 20° < \alpha < 40°,\\ 1 & \alpha \ge 40°.\end{cases}$$

The side force is added to $C_\text{side}$ after all other aerodynamic calculations. At $\alpha = 40°$ the vortex side force is 20% of the body normal force -- a substantial lateral perturbation that often dominates the yaw dynamics during tumble. A `Warning.HIGH_AOA_VORTEX` is issued when the model activates.

The asymmetry coefficient $K_v = 0.20$ has **no verifiable literature anchor** and is presented here as an internally-calibrated coefficient, not as an externally benchmarked value. It corresponds to roughly a 20% side-force fraction of the body normal force at peak, which sits in the plausible range for a fin-suppressed slender body, but it should be read as an engineering range-check rather than a closed validation. It is therefore not counted among the externally benchmarked subsystems.


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

#### 9.9.2 Magnus Force -- Platou (AIAA Journal 1965)

The Magnus model is validated against the wind-tunnel measurements of Platou, "Magnus Characteristics of Finned and Nonfinned Projectiles," *AIAA Journal* **3**(1), 83–90 (1965), DOI 10.2514/3.2791, on body-alone and finned-body configurations at supersonic speeds. The original master citation for this work as "BRL Report 1193, 1963" could not be independently verified through NTRS or DTIC search; the AIAA Journal publication is the verifiable primary source for the same work and has been adopted in place of the unverified report number. `MagnusBenchmarkTest` uses the implementation default body fraction $0.3$ and compares the predicted $C_{y,p\alpha}$ against Platou 1965 for both configurations. The implementation lies within the measured range $0.3$--$0.8$ for the body fraction and matches the reference body $C_{N\alpha}$ derivation to machine precision.

#### 9.9.3 Vortex Sideforce -- Internally-Calibrated Coefficient (No Literature Anchor)

The asymmetric vortex shedding model uses asymmetry coefficient $K_v = 0.20$. This coefficient has **no verifiable literature source** and is therefore presented as an internally-calibrated coefficient rather than an externally benchmarked value. `VortexSideforceBenchmarkTest` is an internal range-check that the predicted side-force magnitude and onset angle stay within a plausible high-incidence envelope; it is not an external-data benchmark, and the $K_v$ row is downgraded out of the A-level count and reported as a qualitative/secondary item.

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

#### 9.9.6 Second Cmq Source on a Non-Basic-Finner Geometry -- Bhagwandin & Sahu 2013

A geometry-independent cross-check is provided by the URANS pitch-damping CFD predictions of Bhagwandin and Sahu (2013), ARL-TR-6725. The report covers two slender finned geometries: the Army-Navy Basic Finner (ANF, the same configuration used by ADA636861 above) and the **Air Force Modified Finner (AFF)**, a tangent-ogive-cylinder body with a clipped-delta sharp-LE fin set. AFF differs from ANF in two of three top-level shape descriptors -- nose family (curved tangent ogive vs straight cone) and fin planform (delta vs rectangular) -- which qualifies it as a non-Basic-Finner second source for the Cmq audit.

The combined comparator `BhagwandinSahuCmqComparatorTest` reports per-band agreement against the planar-pitching CFD predictions in Tables A-1 and A-2 of the report (digitized at `paper/data/csv/bhagwandin_sahu_2013_anf_aff_cmq.csv`):

| Geometry | Mach band | Points | MAPE | Worst $|\Delta_\text{pct}|$ |
|---|---|---:|---:|---:|
| AFF | 1.30--2.50 | 5 | **18.96%** | 30.83% at $M = 2.50$ |
| ANF | 1.29--4.50 | 8 | 28.02% | 33.82% at $M = 2.00$ |

The AFF supersonic per-point signed deltas are $+4.79$, $-12.08$, $-20.99$, $-26.08$, $-30.83\%$ at $M = 1.30, 1.50, 1.75, 2.00, 2.50$. The bias on AFF is in the **same direction** as on ANF (ORP underpredicts $\lvert C_{mq} \rvert$ at supersonic Mach), which is consistent with the supersonic underprediction being a model-physics issue rather than a geometry-specific artifact. The transonic-band agreement is dominated by the same Gaussian-augmentation overshoot already documented against ADA636861 in Section 9.9.5 and is not separately informative on AFF.

This benchmark is reported as **B-level** in the present revision. Justification: the AFF supersonic MAPE of 18.96% is below the 30% closure threshold targeted in the AST roadmap and the bias direction reproduces on the second geometry, but the AFF fin planform used in the ORP comparator fixture (`makeAirForceModifiedFinner` in `SupersonicTestRockets.java`) is currently a placeholder (root chord 1.0 cal, tip 0.5 cal, sweep 0.5 cal, span 1.0 cal). The dimensional callouts of Figure 3 of the source report were not available in repo at the time of this comparator -- the ARL-TR-6725 / DTIC ADA592550 PDF has not yet been dropped into `paper/data/pdf/`, and a full needs-list with the planform values required for promotion to A-level is recorded at `paper/data/cmq_second_source_bhagwandin_2013_assessment.md` ("AFF fin planform -- needs-list"). The B-level rating reflects the incomplete fixture, not the agreement: the comparator is sign-consistent with ANF and within the supersonic band's claimed precision once the planform is calibrated. Comparator artifacts: `paper/data/csv/bhagwandin_aff_cmq_comparator_2026_05_02.csv` and `paper/data/csv/bhagwandin_anf_cmq_comparator_2026_05_02.csv`.

### 9.10 CFD Comparator -- Bunescu et al. 2025 ANF URANS

The Cmq second source above is a CFD prediction of pitch damping; an additional CFD comparator anchors the ORP total-drag pipeline against an independent open-access URANS dataset on the Basic Finner. Bunescu et al. (2025), *Aerospace* **12**(5), 371, report URANS k-epsilon predictions on the same Army-Navy Basic Finner geometry used by ADA636861 (60 mm diameter, $L/D = 10$, four 1-cal rectangular fins). Six points were digitized from Figure 10 (5 axial-force coefficient $C_X$ at AoA = 0 spanning $M = 0.40$--$3.50$, plus 1 normal-force coefficient $C_N$ at AoA = $10°$, $M = 1.60$); the comparator test `BunescuANFCfdComparatorTest` is locked at:

| Mach | AoA (deg) | Coeff | Bunescu CFD | ORP | $\Delta_\text{pct}$ |
|---|---:|---|---:|---:|---:|
| 0.40 | 0 | $C_X$ | 0.460 | 0.189 | $-58.95\%$ |
| 0.95 | 0 | $C_X$ | 0.910 | 0.461 | $-49.35\%$ |
| 1.60 | 0 | $C_X$ | 0.780 | 0.541 | $-30.67\%$ |
| 2.50 | 0 | $C_X$ | 0.550 | 0.372 | $-32.28\%$ |
| 3.50 | 0 | $C_X$ | 0.390 | 0.296 | $-24.06\%$ |
| 1.60 | 10 | $C_N$ | 3.400 | 1.245 | $-63.38\%$ |

Combined MAPE = **43.1%**; $C_X$-only MAPE = 39.1%. ORP systematically underpredicts the URANS values across the full Mach sweep, with the largest gap in the low-transonic regime and convergence at high supersonic. This result is reported honestly as **publication evidence, not a regression gate.** Three observations anchor the interpretation:

1. **The CFD-vs-ORP gap is consistent with the existing ADA636861 free-flight benchmark.** `BasicFinnerDragBenchmarkTest` already documents an 11.8% MAPE against the free-flight aeroballistic data (Section 11.3.4), with the same sign and the same Mach pattern. Bunescu's URANS sits **above** the ADA636861 free-flight values at matching Mach, so the ordering is `CFD > free-flight experiment > ORP` -- the expected pattern when free-flight aeroballistic data (sting-free, finite-Re) is the ground truth, CFD on a 60 mm full-scale model overpredicts at the transonic peak, and an analytical Barrowman-family model is the most aggressive underprediction.
2. **Reynolds-number mismatch is part of the story.** The ORP benchmark fixture is the 30 mm aeroballistic-range model used in ADA636861; Bunescu's URANS is computed on the 60 mm full-scale Basic Finner geometry. $Re_d$ differs by roughly a factor of two at matching Mach, which contributes some of the gap but does not fully explain it.
3. **The single $C_N$ point at AoA $= 10°$, $M = 1.60$ is the worst miss (-63%).** Bunescu reports $C_N = 3.4$; ORP gives 1.25. ORP's normal-force prediction in the ANF supersonic regime is anchored against the NASA TM X-653 NSCFB blunt-fin geometry (Section 11.4.1, MAPE 6.84%), not against the ANF rectangular-fin configuration. The ANF-specific $C_N$ gap may indicate that the Pitts-Nielsen-Kaattari interference factor or the cylinder-fin crossflow $C_d$ is biased low for this exact geometry; this is a flagged investigation, not a calibration adjustment.

The honest disposition: the gap is documented and bounded, no constants are tuned to close it, and a second independent CFD anchor on matching geometry would be required to justify any retune. The companion CFD source ARBRL-TR-02495 (Sahu, Nietubicz \& Steger 1983, Thin-Layer Navier-Stokes on a secant-ogive-cylinder-boattail at $M = 0.9$--$1.2$) is in repo at `paper/data/pdf/Empirical heuristics and tuned constants validation/` for transonic base-flow validation but has not been exercised as a comparator in this revision -- the geometry is structurally different from the Basic Finner and would require building a separate ORP rocket model. Comparator artifacts: `paper/data/csv/bunescu_anf_cfd_2025.csv` (digitized source), `paper/data/csv/bunescu_anf_comparator_2026_05_02.csv` (test output), and `paper/data/md/bunescu_anf_cfd_comparator_2026_05_02.md` (assessment memo).

### 9.11 CFD Comparator -- Sznajder 2025 ANF Pitch Damping

A second independent CFD comparator on pitch damping is provided by Sznajder (2025), "Computational Determination of Dynamic Stability Derivatives," *Transactions on Aerospace Research* No. 4, pp. 98–121, DOI 10.2478/tar-2025-0021. Sznajder reports ANSYS Fluent computations of $C_{mq}$ and $C_{m\dot\alpha}$ *separately*, from three independent CFD techniques — steady moving reference frame (MRF), dynamic-mesh forced oscillation (FOM), and step-perturbation indicial response (IRM) — over $M = 0.9$--$5.0$ on the Army-Navy Basic Finner. The three methods agreed to within approximately 3 percent of one another and were independently validated against the DREV-TM-9703 free-flight experimental dataset that also anchors the present method's existing `BasicFinnerCmqBenchmarkTest`. The present method exposes the experimentally observable damping sum $C_{mq} + C_{m\dot\alpha}$. On the ten-point comparison grid:

- **Supersonic band, $M = 1.29$--$4.5$ ($n = 8$ points):** the present method underpredicts the magnitude of the damping sum by 27 to 36 percent, with sign and Mach trend correct. MAPE on the supersonic band is **31.6 percent**.
- **Transonic peak, $M = 1.08$--$1.11$ ($n = 2$ points):** the present method overshoots the magnitude of the damping sum by **+110 to +160 percent**. The Sznajder CFD does not exhibit a comparable transonic peak in the sum.

The transonic overshoot is traced to the $k_{\mathrm{transonic}} = 1 + 2.5 \exp(-((M - 1)/0.15)^2)$ Gaussian augmentation applied in `BarrowmanStabilityCalculator`; the supersonic underprediction reflects a constant-factor bias of approximately 0.67 in the strip-theory damping coefficient. The Bhagwandin and Sahu 2013 second-source CFD on AFF and ANF (Section 9.9.6, ARL-TR-6725) independently confirms the same supersonic-band underprediction direction. Two independent CFD sources therefore converge on the same two findings: a 27--36 percent supersonic underprediction of pitch damping and a transonic-peak over-augmentation. Both findings are taken up explicitly in the limitations discussion in Section 12.4 item 2. Comparator artifacts: `paper/data/csv/sznajder_anf_cmq_cfd_2025.csv` (digitized source) and `paper/data/csv/sznajder_anf_cmq_comparator_2026_05_11.csv` (test output); the assessment memo is `paper/data/md/sznajder_anf_cmq_cfd_comparator_2026_05_11.md`.

### 9.12 CFD Comparator -- Vidanović 2014 AGARD-B Reference

A third CFD comparator is provided by Vidanović et al. (2014), "Validation of the CFD code used for determination of aerodynamic characteristics of nonstandard AGARD-B calibration model," *Thermal Science* **18**(4), 1223–1233, DOI 10.2298/TSCI130409104V. The authors report ANSYS Fluent Menter SST $k$-$\omega$ predictions of total drag, lift, and pitching-moment coefficients on the AGARD Model B calibration standard at $M = 0.596$ and $M = 1.602$ over an angle-of-attack sweep of $-4°$ to $+12°$. Their CFD is validated against wind-tunnel data from the VTI T-38 trisonic facility in Belgrade, with CFD-versus-experiment agreement of 0.3--3 percent in $C_D$ at positive AoA and below 1 percent in $C_L$ over the test envelope — a state-of-the-art benchmark on a wing-body calibration standard. The present method does not yet ship an AGARD-B `.ork`: the equilateral-triangle delta wing with 4 percent bi-convex section is at the edge of the OpenRocket fin-set model's validity, and a faithful AGARD-B fixture would require either rendering the delta wing as a fictitious fin set or extending the fin geometry primitives. The Vidanović CFD is therefore retained in the present revision as a *reference dataset* against which a future OpenRocket Plus AGARD-B comparator can be benchmarked; the comparator panel is shown in Figure 18 of `paper/data/png/cfd_validation_panels.png` and is flagged as deferred future work in Section 12.6. Comparator artifact (digitized source only): `paper/data/csv/vidanovic_agard_b_cfd_2014.csv`, memo at `paper/data/md/vidanovic_agard_b_cfd_comparator_2026_05_11.md`.

### 9.13 CFD Comparator Inventory Summary

The four published CFD comparators that anchor the present method's CFD-side validation are summarized below. Together they span two reference geometries (Army-Navy Basic Finner; AGARD Model B), two distinct aerodynamic quantities (static force/moment coefficients; pitch-damping derivatives), three Mach bands (transonic; supersonic; supersonic-leading-to-hypersonic), and three independent author groups across two continents and three CFD code families.

**Table 9.13.1 — Published-CFD comparator inventory.**

| Source | Geometry | Quantity | Mach range | ORP comparison status |
|---|---|---|---|---|
| Bunescu et al. (2025), *Aerospace* **12**(5), 371, URANS k-$\epsilon$ | Basic Finner (ANF) | $C_N$, $C_X$ | 0.4--3.5 | Java comparator wired (`BunescuANFCfdComparatorTest`); $C_X$ MAPE 39.1 percent on 5 points at AoA = $0°$ |
| Sahu, Nietubicz, Steger (1983), ARBRL-TR-02495 (DTIC AD-A130293), thin-layer Navier-Stokes | Secant-ogive-cylinder-boattail | $C_{Db}$, $C_{D,\text{tot}}$ | 0.9--1.2 | PDF in repo; comparator not yet digitized (deferred future work) |
| Vidanović et al. (2014), *Therm. Sci.* **18**(4), 1223, SST k-$\omega$ | AGARD-B calibration standard | $C_D$, $C_L$, $C_m$ | 0.596, 1.602 | Reference dataset only; AGARD-B `.ork` not yet shipped (deferred future work) |
| Sznajder (2025), *Trans. Aerosp. Res.* No. 4, 98, Fluent MRF/FOM/IRM | Basic Finner (ANF) | $C_{mq} + C_{m\dot\alpha}$ | 0.9--4.5 | Memo + comparator CSV; supersonic MAPE 31.6 percent on 8 points ($M \ge 1.29$); transonic overshoot $+110$ to $+160$ percent |
| Bhagwandin & Sahu (2013), ARL-TR-6725 (DTIC ADA592550), Fluent | Basic Finner (ANF) + Air Force Modified Finner (AFF) | $C_{mq} + C_{m\dot\alpha}$ | 0.6--4.5 | Second-source corroboration of Sznajder supersonic bias direction on the same and on an independent finned geometry |

The four-panel composite figure (`paper/data/png/cfd_validation_panels.png`) overlays the comparator outputs into a single quick-look diagram: Panel A — Basic Finner $C_X$ vs Bunescu URANS; Panel B — Sahu reference (deferred); Panel C — AGARD-B reference dataset (Vidanović SST + VTI T-38 experiment); Panel D — Basic Finner $C_{mq} + C_{m\dot\alpha}$ vs Sznajder Fluent + Bhagwandin & Sahu second source.


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
2 & Base drag $C_{D,\text{base}}$ & 0.85 & 1.50 & Poly $C^1$ & $0.12+0.13M^2$ & $0.064{+}0.186/M^2$ & DragCalc \\
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
18 & Chapman--Korst turb. & 1.20 & 1.40 & Hermite & $0.064{+}0.186/M^2$ & Chapman--Korst & CKBaseDrag \\
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
- Entry 18 is an available/tested turbulent base-drag utility; the production base-drag path uses the empirical supersonic base-drag correlation $C_{d,\text{base}} = 0.064 + 0.186/M^2$ (validated against NACA TN 3393, consistent with ESDU 77021) plus the transonic polynomial and the optional Chapman laminar correction, unless explicitly routed through `ChapmanKorstBaseDrag.blendedBaseDrag()`.
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

Base drag peaks near $M = 1.05$ and is anchored on the supersonic side by the Hart L52E06 plateau through $M \approx 1.30$ before joining the empirical $C_{d,\text{base}} = 0.064 + 0.186/M^2$ correlation at $M = 1.50$. Fin $C_{N\alpha}$, which depends on $1/\beta$, needs the wider $M = 0.90$--$1.50$ supersonic margin because both the Barrowman subsonic and the Ackeret supersonic formulas diverge at $M = 1$ and the interpolation polynomial must span enough range to control the curvature.


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

**Example.** Base drag blend (Entry 2), which must match the subsonic parabola and its slope at $M = 0.85$, pass near the transonic peak ($\sim 0.25$) at $M = 1.05$, pass through the Hart L52E06 anchor at $M = 1.30$, and match the empirical $C_{d,\text{base}} = 0.064 + 0.186/M^2$ correlation with its slope at $M = 1.50$.

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

- **20 subsystems are externally benchmarked against published wind-tunnel, free-flight, or analytical data** at the A-level standard with a quantitative acceptance criterion, plus **1 externally anchored negative benchmark** (NACA RM-10, MAPE 80%) -- counted outside the 20 -- used to bound and exclude a geometry family. Three results that earlier drafts counted as externally benchmarked are reported at their honest evidentiary level and are *not* in the 20: hypersonic cone foredrag (B-level / exploratory), AGARD-B total drag (qualitative secondary, $\sim 22.6\%$ MAPE), and the vortex sideforce $K_v = 0.20$ (internally calibrated).
- **9 results are calibrated against the integrated flight corpus** rather than against isolated component data. These are circular calibrations (same corpus is the calibration and validation target) and are *not* counted in the 20-subsystem headline. Each is flagged where it is used (Section 11.6.5).
- **25-flight integrated validation corpus** (Rocket Flight Database, DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977)), Mach 0.54--4.33: 25/25 within $\pm 10\%$, 14/25 within $\pm 5\%$, mean signed apogee error $-0.38\%$, $\sigma = 5.44\%$, MAE $4.74\%$, 0 abnormal endings. RASAero II on the same paired set averages $5.34\%$ MAE with 22/25 within $\pm 10\%$ (Wilcoxon $W = 143.0$, $p = 0.615$ on paired absolute errors; $|\text{ORP}|-|\text{RAS}| = -0.60$ pp, 95\% CI $[-2.16, +0.96]$). **The honest claim is statistical parity with this version-locked RASAero set, not superiority.**
- **MESOS 293K** (flight 25 of 25; peak Mach 4.33 / 293,488 ft): apogee $\mathbf{-6.96\%}$ (273,056 ft) -- the corpus's largest single-flight error and the higher-Mach of the two two-stage closures, reproduced in isolation, still inside the $\pm 10\%$ band (Section 11.6.3). A separate exploratory high-Mach set reaching Mach 5--7 (Black Brant V VB, Nike-Deacon, Nike-Apache, etc.) is reported in full in Section 11.6.6 as a capability demonstration, not as part of this headline corpus.
- **Envelope of the headline claim.** The accuracy figures above apply to finned slender vehicles within the boattail half-angle envelope of $6°$--$16°$ (the Viswanath 1996 calibration band, Section 6.2.7) and to fin sections that are HEXAGONAL (double-wedge) or AIRFOIL/ROUNDED (rounded-LE), the section types present in the 25-flight Rocket Flight Database and in every Basic-Finner-class wind-tunnel and free-flight reference geometry used in this work. Out-of-envelope geometries -- specifically the high-fineness parabolic body with steeply contracted afterbody and 60° swept circular-arc biconvex fins of NACA RM-10 -- are reported as transparency references and are excluded from the headline accuracy claim (Section 11.3.6).


### 11.1 Test Suite Overview

The aerodynamic validation suite currently comprises **85 tracked JUnit test classes** in the `info.openrocket.core.aerodynamics` package hierarchy (87 tracked Java files including support/export helpers), plus one workspace-local `SimVRealCorpusAblationTest` used for the May 1 import-parity ablation. The claim inventory consists of 20 externally benchmarked A-level subsystem results, 9 integrated flight-data closures, and 1 negative external benchmark (NACA RM-10, counted outside the 20). Not every claim has equal evidence: externally benchmarked results are independently matched against published experimental or tabulated data with a quantitative acceptance criterion; integrated flight-data closures are validated against the 25-flight Rocket Flight Database corpus rather than against an isolated published component dataset; numerical-consistency tests verify that the implementation reduces to its analytical limit or matches its own boundary conditions; and a small number of empirically tuned coefficients are documented as such. Every claim in this chapter is reported with its evidence type, not as a uniformly closed validation.

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

The current result is **MAPE 11.8%**, below the 14% aggregate regression criterion. Four mid-supersonic points exceed 14% pointwise error (-18.0%, -20.0%, -19.2%, and -14.6%), so the gate is an aggregate MAPE gate, not a per-point claim. This is the first vehicle-level total-drag validation for the extended aerodynamic module against published external data. It does not by itself close the broader high-Mach finned-body family, because the NACA RM-10 case remains a documented open mismatch for a structurally different geometry (Section 11.3.6).

#### 11.3.5 AGARD-B Standard Model (AEDC-TR-70-100)

AGARD-B is a standard wind-tunnel reference model used internationally for facility calibration and CFD validation. `AgardBDragBenchmarkTest` validates total and component-level drag against AEDC-TR-70-100 across the subsonic and transonic range ($M = 0.2$--$1.0$), with the trend and component split passing qualitatively.

#### 11.3.6 Excluded Geometry Family -- RM-10 (NACA TN 3320)

`NacaRm10FinnedBodyDragBenchmarkTest` compares the implementation against the RM-10 finned-body free-flight data of NACA TN 3320. The result is **MAPE 80%** -- a large, externally anchored *negative* benchmark. This is recorded as an explicit "excluded geometry family": the RM-10 combines a *high-fineness parabolic forebody* (fineness 12.2), a *smoothly tapered parabolic afterbody* with base-to-max diameter ratio 0.606, and *four untapered 60° swept-back, 10%-thick circular-arc biconvex fins* (NACA TN 3320 Figure 1, page 4). None of those three features is well represented by the Barrowman-family slender-body assumptions. The diagnostic in `paper/data/legacy/rm10_vs_basic_finner_diagnostic.md` (regenerated artifact `core/build/reports/rm10_vs_basic_finner_component_cd.csv`) decomposes the over-prediction at $M = 2.0$ ($C_{D,T,\text{exp}} = 0.215$ vs ORP 0.389; $\Delta = +0.174$) and attributes it to three independent sub-model envelope violations rather than a single broken term.

**Why it fails (mechanism breakdown).**

- *Boattail base-pressure reduction (Viswanath 1996) is calibrated for half-angles* $\theta_{\text{bt}} = 6°$--$16°$ *and is extrapolated outside that band on RM-10.* Section 6.2.7 documents the piecewise form $\eta_{\text{bt}}(\theta_{\text{bt}})$. The RM-10 parabolic afterbody has a continuously varying local half-angle reaching only $\sim 4.8°$ at the base station (slope of $Y = 6.000 - 0.0007407\,x^2$ at $x = 56.5$ in), which puts it *below* the calibrated band where the linear $0.25 + 0.05\,\theta$ branch under-credits wake energization for slowly converging afterbodies. When the RM-10 geometry is reconstructed as a finite-fineness conical transition + cylindrical fin-mount tube + a short terminal contraction (the only Barrowman primitives available in the import path), the terminal contraction has half-angle $\sim 57.5°$ -- well *above* the upper calibration bound -- and the base-pressure reduction is also extrapolated. Either reconstruction is outside the Viswanath envelope. At $M = 2.0$, the terminal-contraction component alone contributes pressure $C_D = 0.043$ and base $C_D = 0.063$ ($\sim 0.106$ combined), $\sim 27\%$ of the predicted total.

- *Finned-body base augmentation (Section 6.2.8, scale-anchored to flat-base ADA636861) is applied without an upstream-boattail discount on RM-10.* The augmentation is corpus-calibrated against Basic Finner, where the fins meet the wake at the maximum body diameter; on RM-10 the fins meet a wake that has already partially recompressed over the parabolic afterbody, so the same $1.55\times$ multiplier over-credits the fin-induced suction. NACA TN 3320 page 7 reports a measured base coefficient $C_{D,B} \approx 0.04$ for the full-scale RM-10 across $M = 1.2$--$3.3$; ORP predicts $0.063$ at $M = 2.0$, exactly the $1.55\times$ multiplier applied to a base-drag-correlation baseline of $0.041$ (the empirical $C_{d,\text{base}} = 0.064 + 0.186/M^2$ form anchored against NACA TN 3393 and consistent with ESDU 77021).

- *DATCOM 4.1.5.1 fin-section coefficient $K$ does not have a calibrated entry for circular-arc biconvex sections.* Section 7.2 of this report uses $K = 4.0$ for HEXAGONAL (double-wedge) and $K = 16/3$ for ROUNDED (rounded-LE airfoil); neither matches the sharp-LE, smoothly curving 10%-thick circular-arc profile specified by NACA TN 3320. Mapped to ROUNDED, the round-LE bluntness term ($C_{p,\text{LE}} = 1.214 - 0.502/M^2 + 0.1095/M^4$) is spuriously activated and contributes $\sim 0.11$ of fin-set $C_D$ at $M = 2.0$ that should not be present for a sharp-LE section. Mapped to HEXAGONAL, the $K = 4.0$ wedge-angle assumption under-predicts the smooth-arc thickness distribution. There is no third option in the implementation.

- *Body wave drag is correct here.* The POWER $p = 0.5$ paraboloid nose is routed through the TR-R-100 fineness-scaled reference family, not through Dahlem-Buck (the `isDirectReferenceShapeForSupersonicOverride` gate excludes paraboloids); paraboloid pressure $C_D \approx 0.016$ at $M = 2.0$ and $f_n = 7.5$ matches the analytical scaling. The forebody is *not* the deficit driver.

**Combined effect estimate.** Quantified individually, the three sub-model violations remove $\sim 0.085$ of the $+0.174$ over-prediction at $M = 2.0$. The residual $\sim 0.085$ -- still $\sim 40\%$ over-prediction -- is distributed across small terms (high-fineness body friction calibration, fin-body interference at AR $= 2.04$, and fin trailing-edge bluntness on the arc section) that no individual module owns. *The deficit is genuinely fragmented across the calibration envelopes of three independent sub-models, not concentrated in any one of them.*

**Who it affects.** RM-10 is a 1949-vintage research geometry chosen specifically to instrument boattail base pressure on a low-base-ratio body. Its three out-of-envelope features do not appear together in any flight in the Rocket Flight Database corpus or in any published Basic-Finner-class benchmark. High-power amateur rocket boattails almost always fall in the 6°--16° Viswanath band; flight-grade fins are almost always hexagonal or NACA airfoil sections, not 10%-thick circular arc; and parabolic forebodies of fineness 12+ are absent from the corpus.

**Why we do not fix it.** Each of the three envelope violations could be patched in isolation -- for example, by extrapolating Viswanath outside 6°--16° with explicit damping, adding an upstream-boattail gate to the finned-body augmentation, or adding a circular-arc biconvex $K$ entry. Each individual patch was attempted in scratch branches and each one regressed Basic Finner, the corpus, or both. Because the deficit is fragmented, a clean closure would require simultaneous calibration against (a) a Basic-Finner-class flat-base benchmark, (b) RM-10 itself, and (c) the 25-flight corpus -- and the calibration set required to disentangle these regimes does not exist in the public literature in a digitizable form. The cost-benefit of a multi-source recalibration is poor, because RM-10's geometry family is not represented in the application domain; the model is already valid where it is used.

Including this benchmark in the validation pack is a deliberate honesty choice. RM-10 documents the *boundary* of the model's geometric domain rather than counting as a closed validation. It is the only externally anchored negative benchmark in the present work.

**Envelope statement.** The headline accuracy claim of this work applies to finned slender vehicles within the boattail half-angle envelope of $6°$--$16°$ (the Viswanath calibration band) and to fin sections that are HEXAGONAL (double-wedge) or AIRFOIL/ROUNDED (rounded-LE) -- the section types of every flight in the Rocket Flight Database and of every Basic-Finner-class wind-tunnel/free-flight reference geometry used in this work. *Out-of-envelope geometries -- specifically the high-fineness parabolic body with steeply contracted afterbody and 60° swept circular-arc biconvex fins of NACA RM-10 -- are reported as transparency references and are excluded from the headline accuracy claim.*

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
| $x_{CP}/d$ | 10 | 0.054 | 0.061 | **7.11%** | 14.6% | +0.054 |

Interpretation, paraphrasing the NASA TM X-653 closure memo (`paper/data/md/nasa_tm_x653_validation_report.md`): below $M = 3$ the implementation tracks the experimental curve within $9\%$ on $C_N$ and within $4\%$ on $x_{CP}/d$ at $M = 3.0$ (down from a 125% error before the M=3.0 ESDU TransonicSimilarity guard was added). At $M = 4.06$--$5.82$ the implementation over-predicts $C_N$ by 13--18% and shows a $x_{CP}/d$ plateau because the $K_1 = 0.85$ floor prevents fin $C_{N\alpha}$ from decaying with Mach as fast as the experiment for low-aspect-ratio fins. This is an honest, documented model trade-off; the case is reported as externally benchmarked at $\le 8\% / \le 7.1\%$ MAPE.

**Fourth independent static-aero anchor -- Arcas wind-tunnel coefficients (NASA TN D-4013 + TN D-4014).** The TM X-653 NSCFB result above (a low-fineness blunt cruciform-fin geometry) is supplemented by digitized wind-tunnel coefficients for the Arcas single-stage sounding rocket (a slender ogive-cylinder-boattail geometry with trapezoidal double-wedge fins). Two companion Langley reports cover the same model continuously across $M = 0.60$--$4.63$: TN D-4013 (Ferris 1967, Langley 8-ft transonic pressure tunnel, $M = 0.60$--$1.20$) and TN D-4014 (Babb \& Fuller 1967, Langley Unitary Plan Wind Tunnel, $M = 1.50$--$4.63$). The combined set provides 12 Mach points $\times$ 4 quantities ($C_{N\alpha}$, $C_{A0}$, $x_{CP}$, $C_{m\alpha}$) = 48 data values, archived at `paper/data/csv/arcas_wind_tunnel_combined_2026_05_02.csv` with figure-by-figure provenance in `paper/data/md/arcas_wind_tunnel_assessment_2026_05_02.md`. The dataset documents the externally-validated trend that $x_{CP}$ moves rearward through the transonic peak ($\sim 86\%$ body length at $M \approx 1.0$--$1.2$) and progressively forward at supersonic Mach (down to $\sim 56\%$ at $M = 4.63$). Confidence distribution from the digitization assessment: 0 high / 9 medium / 3 low (the three low-confidence rows are the transonic Fig.\ 11 peak in D-4013 and the high-Mach $C_{m\alpha}$ slope reads in D-4014 where the moment slope is small). This is a **B-level** benchmark in the present revision: the Arcas .ork comparator and `ArcasWindTunnelComparatorTest` are not yet built, so the dataset enters the manuscript as an externally-anchored target rather than as a closed validation. The path to A-level promotion is documented in the digitization assessment (build the Arcas geometry from TN D-4013 Fig.\ 1, run ORP at the digitized Mach points at the tunnel Reynolds number, and re-digitize the three low-confidence rows with WebPlotDigitizer to bound reader uncertainty). Citation: TN D-4013 and TN D-4014 are both verified from the title pages of the PDFs in repo (`paper/data/pdf/New/incoming/arcas/`), per the citation-hygiene policy of this work.

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
| Pitch damping vs Bhagwandin & Sahu 2013 ARL-TR-6725 (AFF) | supersonic MAPE 18.96% on a non-Basic-Finner geometry; sign-consistent with ANF | external benchmark (B-level, AFF planform fixture pending; see Section 9.9.6) |
| Magnus body fraction (0.3) | within Platou (AIAA Journal 3(1), 1965) measured 0.3--0.8 range | external benchmark |
| Vortex asymmetry ($K_v = 0.20$) | within plausible high-incidence range (internal check) | internally calibrated (no literature anchor) |


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

The integrated 6-DOF trajectory predictions are validated against a corpus of **25 real high-power, amateur, university-research, and sounding-rocket flights** with measured GPS, barometric, optical, accelerometer, or radar/radar-beacon apogee. The corpus is published as the *Rocket Flight Database* (DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977), CC-BY-4.0). All 25 flights are the public RASAero II altitude comparison set published by Charles E. Rogers (RASAero II author) at <https://www.rasaero.com/comparisons-alt.htm>: 23 single-stage flights plus two two-stage flights---the AeroPac 104K Two-Stage (flight 22) and the MESOS 293K closure (flight 25). Because the corpus is externally selected by Rogers -- not outcome-curated by us -- the accuracy statistics are an honest, outcome-independent validation result. The OpenRocket Plus predictions are produced by importing the same `.CDX1` into the simulator and running with default settings. (A separate, exploratory set of ~20 historical sounding-rocket flights reaching Mach 5--7 -- including Black Brant V VB and the Nike-Deacon pair -- is reported as a capability demonstration in Section 11.6.6, NOT as part of this headline corpus.)

This is the "integrated flight data" capstone: it does not isolate any single subsystem, but it demonstrates that the assembly of physics in Parts A--D produces trajectory predictions consistent with measured reality across Mach 0.54--4.33 and apogees from 3 577 ft (1.1 km) to 293 488 ft (89.5 km).

#### 11.6.1 Aggregate Result (25 Flights)

| Metric | This work (n = 25) | RASAero II (n = 25 paired) |
|---|---:|---:|
| Mean signed error | **−0.38%** | +2.46% |
| Sample $\sigma$ | **5.44%** | 5.81% |
| RMSE | **5.34%** | 6.20% |
| Mean $\lvert\text{error}\rvert$ (MAE) | **4.74%** | 5.34% |
| Within $\pm 5\%$ | **14/25 (56.0%)** | 13/25 (52.0%) |
| Within $\pm 10\%$ | **25/25 (100%)** | 22/25 (88.0%) |
| Worst case | $+8.7\%$ (Kinsel, AeroPac 104K, FMJ Black Rock 6) | $+11.5\%$ (T&L) |
| Bias$^2$/MSE | **0.01** | 0.16 |
| Abnormal endings | 0 | n/a |

The whole corpus is the paired set: the Wilcoxon signed-rank test on the paired absolute errors returns $W = 143.0$, $p = 0.615$, and the difference in mean absolute error is $|\text{ORP}| - |\text{RAS}| = -0.60$ pp with a 95\% bootstrap CI of $[-2.16, +0.96]$ that straddles zero. Neither test rejects the null hypothesis of equal absolute-error distributions at $\alpha = 0.05$: **the honest claim is parity with this version-locked RASAero set, not superiority.** The RASAero II values are Rogers' *recorded* predictions (not fresh independently-rerun pre-flight cases), which is disclosed here. Bland-Altman analysis gives 95\% limits of agreement of $\pm 14.3\%$ with a mean offset of $-2.84\%$. The mean-error 95\% bootstrap CI is $[-2.41, +1.72]$, bracketing zero, so the predictor is statistically unbiased on this corpus. The whole-corpus bias$^2$/MSE = 0.01 for OpenRocket Plus (vs 0.16 for RASAero II) means the residual is dominated by per-flight variance (build tolerance, motor lot variation, atmospheric soundings, ground-truth instrumentation precision) rather than systematic model bias.

#### 11.6.2 Per-Case Table (Sorted by Peak Mach)

Errors are signed; positive = over-predicted apogee. $\Delta = |\text{RAS err}| - |\text{this-work err}|$ (positive = this work closer). RASAero II values for all 25 flights are as published by Rogers (loc. cit.). The canonical machine-readable form is the *Rocket Flight Database*.

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
| 25 | MESOS 293K | 3,910 | 4.33 | 293,488 | 289,789 | 273,056 | $-1.3\%$ | $-6.96\%$ | $-5.7$ |

```{=latex}
\end{landscape}
```

#### 11.6.3 High-Altitude Two-Stage Detail (MESOS 293K, peak Mach 4.33)

| Metric | Real | RASAero II | This work | RAS err | This-work err |
|---|---:|---:|---:|---:|---:|
| Apogee (ft) | 293,488 | 289,789 | 273,056 | $-1.3\%$ | $\mathbf{-6.96\%}$ |
| Peak Mach | 4.18 | 4.23 | 4.33 | $+1.2\%$ | $+3.6\%$ |
| Booster burnout / sep (s) | -- | -- | 7.941 | -- | -- |
| Sustainer ignition (s) | -- | -- | 23.103 | -- | -- |
| Sustainer burnout (s) | -- | -- | 33.692 | -- | -- |

Launch site: Black Rock Desert, NV, 3,910 ft (read from the imported launch-site altitude). This case exercises stage-aware nozzle pressure-thrust correction, two-stage motor sequencing, and Mach 3+ coast aerodynamics simultaneously.

**The largest single-flight error.** The current archived code predicts MESOS 293K apogee at **$-6.96\%$ (273,056 ft)**, the largest single-flight error in the 25-flight corpus. The figure **reproduces in isolation** -- it is the genuine, reproducible current-code value, confirmed by an isolation run -- and is the value used throughout this report and carried in the published database. As the higher-Mach of the two two-stage closures, this case couples stage separation, coast-phase aerodynamics, and Mach-4 base drag in a single integrated result, and bounds the framework's accuracy at the top of its validated envelope; the error is reported without decomposition. An earlier draft and database snapshot reported $-0.6\%$ (291,601 ft) for this flight; that figure was erroneous, has no defensible derivation, and is **withdrawn** -- it is *not* a prior value that $-6.96\%$ regressed from. Because $-6.96\%$ remains inside the $\pm 10\%$ admission band, the 25/25 within-$\pm 10\%$ headline is unchanged.

#### 11.6.4 Active Mechanisms Producing the Baseline

The closure above is *not* a per-case multiplier. It is the convergence of four shared mechanisms applied to the entire corpus and to the external benchmarks simultaneously:

- Stage-aware nozzle pressure-thrust correction during powered flight (`RK4SimulationStepper`).
- RASAero `Turbulence=True` parsed into `forceTurbulentBL`; bounded to zero for non-perfect-finish imports by an ablation study, while still active for perfect-finish laminar fixtures.
- Geometry-gated finned-base drag augmentation (saturated fin-count scaling, rounded-fin transonic wake, expanding fin-can sleeve, four-fin low-subsonic ramp).
- Trajectory-derived peak Mach via `data.getMaxMachNumber()` in all three reporting paths.

**Mechanism ablation (each mechanism disabled in isolation).** To rank the corpus apogee-error contribution of each supersonic mechanism, each was disabled in turn and the archived 24-flight mechanism-ablation subset was re-run; the table reports the mean absolute change in apogee error across that subset (23 single-stage flights plus the AeroPac 104K two-stage closure). MESOS 293K remains part of the companion 25-flight validation corpus but is not included in this archived ablation artifact.

| Mechanism | Mean $\lvert\Delta\rvert$ | Max $\lvert\Delta\rvert$ (flight) | Note |
|-----------|------------------:|-----------------------------------|------|
| Finned-base augmentation (`FINNED_BASE_K`, EXTERNAL/Basic-Finner) | **8.10 pp** | 39.5 pp (Kinsel, $M = 2.19$) | **dominant apogee driver** |
| Van Driest II skin friction | 0.87 pp | 7.9 pp (Qu8k, $M = 3.46$) | matters at high Mach |
| DATCOM 4.1.5.1 fin wave drag | 0.39 pp | 1.9 pp (Proteus) | modest |
| ShockGeometry pre-pass | **0.15 pp** | 3.6 pp (FMJ Black Rock 6, $M = 2.46$) | inert subsonically |
| PNK interference / $K_1$ floor | 0.00 pp | 0.00 pp | no apogee effect |

The externally-calibrated **finned-base augmentation is the dominant apogee mechanism (8.10 pp mean)**, an order of magnitude larger than any other term and two orders larger than the ShockGeometry pre-pass. This is the central motivating result for the companion base-drag-intercomparison study (Paper 5): the integrated apogee error budget is governed by the base-drag closure, not by the supersonic shock-geometry machinery.

The **ShockGeometry pre-pass moves integrated apogee by only 0.15 pp** in the mean. This is expected and honest: apogee integrates a trajectory dominated by lower-Mach drag, and the pre-pass is inert below $M \approx 1$ (Section 9.4.4, Section 11.7). Its value is *local-flow fidelity* -- correct post-shock conditions for fin loads and stability, verified bit-for-bit against Taylor--Maccoll (Section 11.3.1, row D8) -- and its role as the *architectural seam* that enables the downstream supersonic stability models, not a gross-apogee win. The pre-pass is presented throughout this work on those terms, never as an apogee-accuracy driver.

#### 11.6.5 Results Calibrated Against the 25-Flight Corpus

The following results contribute to the trajectory closure but are *not* externally benchmarked at the component level — they are calibrated against the integrated 25-flight corpus. They are circular in the sense that the calibration target and the validation target overlap. None of them are counted in the "20 externally benchmarked subsystems" headline.

| Claim | What is unverified at the component level | What would close the gap |
|------|-------|-----------|
| Cmq $\times 3$ multiplier (Section 9.9.5) | Over-predicts $\sim 3.6\times$ at $M = 1.05$--$1.12$ when measured against ADA636861 alone, but the multiplier is needed to match apogee-turn timing on the corpus | A second free-flight $C_{mq}$ dataset that is *not* used to set the multiplier (Sznajder 2025 CFD now provides a CFD-side second source confirming the transonic overshoot) |
| Transonic $C_{mq}$ Gaussian (peak 3.5) | Same dataset, same over-prediction direction (Sznajder 2025 CFD: +110 to +160% at $M = 1.08$--$1.11$) | Same |
| Finned-body base drag augmentation | The fin-presence wake correction is set by corpus apogee residual; Hart 1952 measures body-alone | Public finned-body base-pressure dataset across $M = 0.7$--$3$ |
| Power-on nozzle / pressure thrust | MESOS 293K is the only multi-stage powered-flight closure | A second multi-stage flight with telemetry |
| Min-diameter supersonic flight (Raven, DDT) | Apogee closes but no isolated component check | Dedicated min-diameter free-flight dataset |
| Termination / descent dynamics | 0/25 abnormal endings, but no isolated $C_N(\alpha)$ / $C_m(\alpha)$ at high $\alpha$ | High-$\alpha$ dataset (see Section 12.4 item 6) |
| Full 6-DOF trajectory fidelity | MAE 4.74% (mean signed −0.38%, $\sigma$ 5.44%) on the corpus is the validation, not a component check | (Headline metric — not separable) |
| Geometry-import parity | RASAero `ModifiedBarrowman` stability switch is parsed but not honored | Implement the alternate stability path |

The headline corpus closure is dominated by drag and base-pressure terms, not by damping. Removing the $C_{mq}$ multiplier or the Gaussian augmentation degrades the apogee-turn signature on five flights but does not move the headline MAE 4.74% by more than $\sim 0.5$ pp; the corpus is therefore mostly drag-validated, not damping-validated.

#### 11.6.5a In-Sample Disclosure and Decontaminated Prospective Holdout

Two base-drag scale constants are corpus-frozen and must be disclosed as partly in-sample. The thick-boundary-layer base-drag constant `THICK_BL_K = 2.2` was anchored on Raven, and the slender-body base-drag constant `SLENDER_BODY_K = 0.0025` was anchored on Raven, Rabia, and Kinsel (the source diagnostic additionally inspected Torrent). Because these two constants were set with reference to specific corpus flights, the 25-flight headline is **partly in-sample**: the calibration set and a portion of the validation set overlap, exactly as already flagged for the finned-body base-drag augmentation in Section 11.6.5.

The primary defense against the circularity critique is a **decontaminated prospective holdout**. Every flight that any of the two constants touched -- Raven, Rabia, Rabia Short Fin Can, Kinsel, and Torrent -- was placed in the development partition, leaving a genuinely blind holdout. The split is by *flight*, not by error magnitude, so it is not outcome-selected:

| Partition | n | Mean signed | MAE |
|-----------|--:|------------:|----:|
| DEV (every flight a constant touched) | 13 | $+0.22\%$ | **5.47%** |
| HOLDOUT (genuinely blind) | 12 | $-1.03\%$ | **3.95%** |

The blind holdout is **more accurate than the development partition** (MAE 3.95% vs 5.47%). A model that had overfit its two in-sample constants would show the opposite ordering -- worse accuracy on the held-out flights. The holdout-beats-dev result is therefore direct evidence that the two base-drag constants **generalize rather than overfit**, and it is the primary in-sample defense for both this work and the companion base-drag study (Paper 5), where the same `FINNED_BASE_K`-class circularity is handled with this same decontaminated split.

#### 11.6.6 Exploratory High-Mach Set and Sounding-Rocket Corpus Expansion (Seed)

**Exploratory high-Mach demonstration (NOT part of the headline corpus).** Separately from the 25-flight headline corpus, approximately 20 historical sounding-rocket flights reaching Mach 5--7 were run as an *exploratory capability demonstration*. This set is reported in full -- it is not outcome-curated, and presenting it as a high pass-rate headline would be selection on the dependent variable. Of the set, **3 flights close within $\pm 10\%$**: Black Brant V VB AAF-VB-32 (peak Mach 7.224, apogee 273.6 km, $-6.97\%$; DTIC AD0733141), Nike-Deacon flight 1 (peak Mach 4.956, $-1.06\%$), and Nike-Deacon flight 2 (peak Mach 5.079, $-0.89\%$). The remaining **17 flights fall outside $\pm 10\%$**: the Nike-Apache family at $+24$ to $+36\%$, Nike-Cajun at $+16.6\%$, Arcas blunt/secant variants at $-29$ to $-69\%$, HEROS 3 at $-63.4\%$, plus a couple of sim-error / zero-apogee cases reported transparently. The honest framing is therefore that the method *reaches* Mach 7 within $\pm 7\%$ on well-characterized vehicles (Black Brant V VB, Nike-Deacon), but motor and geometry reconstruction uncertainty dominates on the poorly-documented historical flights; the high-Mach set is an exploratory capability demonstration, never a validation headline. The root-cause coast-drag bias driving the Nike-Apache / Nike-Cajun over-predictions is documented in Section 12.6a.

**Sounding-rocket corpus expansion (seed).** Expansion of the trajectory-validation envelope to a second corpus class -- *meteorological / sounding rockets* with documented mass properties, motor thrust curves, and aero coefficient tables -- is in progress. The seed for this expansion is AFCRL-TR-73-0412 / AD-766737 (Bollermann \& Walker 1973, Space Data Corp), *"Design, Development and Flight Test of the Super Loki Stable Booster Rocket Systems."* The report contains:

- Time-resolved booster mass properties (CG and $I_{yy}$, Figures 4.2--4.3).
- Motor thrust and chamber pressure vs time (Figure 3.4; sea-level firing in Table 3.3, average thrust 4757 lbf, $I_{sp}$ 228.7 s, action time 2.09 s).
- Booster, vehicle, and dart aerodynamic coefficient curves -- $C_{N\alpha}$, $C_P$, $C_D$ vs $M$ from $M = 0$ to $M \approx 7$ (Figures 4.4--4.8).
- Approximately 30 flight summaries across Super Loki Robin Dart (Table 8.2), Super Loki Instrumented Dart (Table 8.3), and Viper-3A Robin Dart (Table 8.4) configurations.

The Super Loki Dart `.ork` model has been committed as the seed (commit `f8db50ff5`); ORP simulation runs against the digitized aero curves and trajectory data in AD-766737 are pending. This expansion is the planned content of Rocket Flight Database v2.0 and is recorded as the prospective sounding-rocket extension; the present manuscript reports it only as a documented seed, not as a closed validation. The schema decision for v2.0 is recorded at `paper/data/v2_schema_decision_proposal_2026_05_02.md` (Option B: keep the v1.0 schema and leave `apogee_rasaero_ft` blank for sounding rockets that have no RASAero II reference). The full candidate dossier is at `paper/data/sounding_rocket_corpus_candidates_2026_05_02.md`, with verified citations for the Super Loki / Loki-Dart family (AFCRL-TR-73-0412, NASA CR-61238) and the Arcas family (TN D-4013, TN D-4014, AD-235341).


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
| Base drag | basic formula | $C_{d,\text{base}}=0.064+0.186/M^2$ (NACA TN 3393 / ESDU 77021) + $C^1$ transonic blend + optional Chapman laminar path |
| Fin local flow | freestream Mach | post-shock Mach from ShockGeometry for fin stability / PNK / SBLI chord reduction |
| Hypersonic | no model | Modified Newtonian blended $M = 4$--6 |
| Static stability | no supersonic correction | Galejs + Allen-Perkins crossflow + PNK + ESDU similarity (Ch. 8) |
| Dynamic stability | apogee-turn heuristic only | Cmq strip theory + Gaussian augmentation + Magnus + Euler gyroscopic |
| Trajectory integrator | RK4 with limited gates | RK4 with quaternion + adaptive timestep + sanitization + warning diagnostics |
| Valid Mach range | $M < 2$ | vehicle-level (6-DOF) validated to $M \approx 4.3$; component-level cone foredrag validated to $M \approx 17$ (single benchmark) |


## 12. Conclusions and References


### 12.1 Summary of Contributions

This work has extended the OpenRocket aerodynamic simulation framework from a subsonic/low-transonic tool valid to roughly $M = 2$ into a compressible-flow simulation whose validated envelope is two-tier: vehicle-level (6-DOF integrated trajectory) is validated through $M = 4.33$ against the 25-flight Rocket Flight Database headline corpus (DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977)), with an exploratory high-Mach set reaching $M = 7$ on well-characterized vehicles (Section 11.6.6), and component-level cone foredrag is validated to $M \approx 17$ against a single isolated benchmark (DTIC AD0487365). The principal contributions:

1. **Gas dynamics foundation.** A complete set of compressible flow solvers -- oblique shock relations ($\theta$-$\beta$-$M$ with bisection), Taylor--Maccoll cone flow (ODE integration), normal shock jump conditions, and Prandtl--Meyer expansion fan relations -- validated against NACA Report 1135 and cone-flow reference tables: normal shocks to $7\times10^{-5}$, oblique-shock wave angle to $0.021\%$, Prandtl--Meyer angle to $0.004^\circ$, and Taylor--Maccoll cone-shock angle to $0.825\%$ relative. These solvers form the backbone for every subsequent wave drag, pressure coefficient, and shock-geometry calculation.
2. **Analytical wave drag models.** Replacement of the legacy NASA TR-R-100 tables with physics-based wave drag computations: Taylor--Maccoll exact solution for conical noses, second-order shock-expansion theory for ogive noses, DATCOM Section 4.1.5.1 (Puckett--Stewart) fin wave drag with subsonic/supersonic LE classification, and the Dahlem--Buck shape factors for power-law / Haack noses.
3. **Shock geometry pre-pass architecture.** A new `ShockGeometry` computation walks the rocket body nose-to-tail, computing post-shock Mach, pressure, and temperature at each axial station. The production consumer is the stability path, primarily `FinSetCalc`, where local Mach corrects fin normal-force, PNK interference, and SBLI chord reduction. Body stability, fin pressure drag, roll damping, base drag, and wave drag remain freestream-based scope boundaries. Zero overhead at subsonic speeds (passthrough design).
4. **Compressible boundary-layer modeling.** Van Driest II compressible transformation (NASA TN D-6945, Hopkins 1972) for supersonic skin friction, replacing the incompressible Eckert formulas. Reduces friction drag by 30--75% at $M = 2$--5. The Sutherland viscosity law replaces the legacy linear fit; the NIST/Incropera JUnit gate is $<3\%$ over 100--800 K, and the current formula export is MAPE 0.012%.
5. **Hypersonic extension via Modified Newtonian.** $C_p = C_{p,\max}\sin^2\theta$ with $C_{p,\max}$ from the Rayleigh pitot formula for $M > 5$, blended with shock-expansion over $M = 4$--6 (cubic Hermite, $C^1$). Component-level cone foredrag is validated to $M \approx 17$ (single isolated benchmark, DTIC AD0487365 MAPE 19.7%); the headline integrated trajectory corpus is validated to $M = 4.33$, and an exploratory high-Mach set (Section 11.6.6) reaches $M = 7$ within $\pm 7\%$ on well-characterized vehicles (Black Brant V VB AAF-VB-32 closes at $-6.97\%$ apogee at peak Mach 7.224 / apogee 273.6 km), while motor and geometry reconstruction uncertainty dominates on the poorly-documented historical flights.
6. **$C^1$-continuous regime blending.** Up to **19 distinct blending regions** (Chapter 10) using cubic Hermite, constrained polynomials, and AP09 rational functions ensure all aerodynamic coefficients are $C^1$ across every Mach regime boundary, eliminating the simulation instability and time-step collapse that would otherwise occur at transitions.
7. **Dynamic stability derivatives and Euler gyroscopic coupling.** Pitch damping ($C_{mq}$) computed from per-component $C_{N\alpha}$ and moment arms with a transonic Gaussian augmentation, $C_{m\dot{\alpha}}$ via the Tobak--Wehrend slender-body ratio, full Magnus force/moment derivatives with body fraction $0.3$, and the full Euler $\boldsymbol{\omega} \times \mathbf{I}\boldsymbol{\omega}$ coupling in the 6-DOF integrator (with a 500 Pa dynamic-pressure gate against ballistic-descent stiffness).
8. **High-AoA crossflow normal force and simulation robustness.** A bluff-body crossflow drag model with proportional moment scaling that prevents artificial torque divergence at post-stall AoA. SBLI separation-length and $C_{p,\text{plateau}}$ floors, fin $K_3$ and polynomial-denominator floors, and per-coefficient sanitization caps make the integrator robust against transonic singularities, degenerate geometry, and floating-point overflow.
9. **Chapman laminar base drag.** $C_{pb,\text{lam}} = 1870/(M^2\sqrt{Re_L})$ for low-$Re$ or polished-finish rockets (NACA TN 3393 MAPE 4.4%). The Chapman--Korst turbulent method remains an available/tested utility for future production routing, not a default active path.
10. **Comprehensive validation with explicit evidence types.** 20 externally benchmarked A-level subsystem results, 9 results calibrated against the integrated 25-flight corpus rather than isolated component data (flagged at each occurrence and excluded from the 20-subsystem headline), 1 negative external benchmark (NACA RM-10, counted outside the 20 and formally excluded from the headline corpus), and the 25-flight integrated corpus published as the Rocket Flight Database, all locked in automated regression tests. Validation also includes four published-CFD comparators (Bunescu URANS, Sahu thin-layer Navier-Stokes, Vidanović SST k-ω, Sznajder Fluent) with Bhagwandin & Sahu 2013 as second-source corroboration of the supersonic $C_{mq}$ bias direction (Sections 9.9.6 and 9.10--9.13).


### 12.2 Validation Summary

Headline summary restated for the conclusions chapter:

- **20 subsystems externally benchmarked** at the A-level standard against published wind-tunnel, free-flight, or analytical data with quantitative acceptance criteria (Sections 11.2 through 11.5).
- **9 results calibrated against the integrated 25-flight corpus** rather than isolated component data. Listed individually in Section 11.6.5 with the gap each one would need to close to become an external benchmark.
- **1 externally anchored negative benchmark** (NACA RM-10, MAPE 80%) that bounds and excludes a high-fineness parabolic / tapered-afterbody / 60° swept circular-arc-biconvex-fin family (Section 11.3.6).
- **25-flight integrated corpus** (Rocket Flight Database, DOI: [10.5281/zenodo.20531977](https://doi.org/10.5281/zenodo.20531977)), Mach 0.54--4.33: mean signed apogee error $-0.38\%$, $\sigma = 5.44\%$, MAE $4.74\%$, 25/25 within $\pm 10\%$, 14/25 within $\pm 5\%$, 0 abnormal endings; RASAero II on the same paired set averages $5.34\%$ MAE with 22/25 within $\pm 10\%$ (Wilcoxon $W = 143.0$, $p = 0.615$; $|\text{ORP}|-|\text{RAS}| = -0.60$ pp, 95\% CI $[-2.16, +0.96]$ straddling zero). **The claim is statistical parity, not superiority.**
- **Flight 25, MESOS 293K (peak Mach 4.33, 293,488 ft)**: apogee $\mathbf{-6.96\%}$ (273,056 ft) -- the corpus's largest single-flight error and the higher-Mach of the two two-stage closures, reproduced in isolation, still inside the $\pm 10\%$ band (Section 11.6.3). The exploratory high-Mach set reaching Mach 5--7 (Black Brant V VB at $-6.97\%$, the Nike-Deacon pair at $-1.06\%$ and $-0.89\%$, and 17 further flights outside $\pm 10\%$) is reported in full in Section 11.6.6 as a capability demonstration, not as part of this headline corpus.
- **Envelope of the headline claim.** The accuracy figures above apply to finned slender vehicles within the boattail half-angle envelope of $6°$--$16°$ (Viswanath 1996, Section 6.2.7) and to HEXAGONAL or AIRFOIL/ROUNDED fin sections, the geometry envelope of the Rocket Flight Database. Out-of-envelope geometries -- the high-fineness parabolic body with steeply contracted afterbody and 60° swept circular-arc biconvex fins of NACA RM-10 -- are reported as transparency references and are excluded from the headline accuracy claim.

Two headline outcomes summarize the extension. (i) Vehicle-level integrated trajectory: OpenRocket Plus mean signed apogee error $-0.38\%$ (MAE 4.74\%) across the 25-flight corpus; RASAero II on the same paired set averages 5.34% MAE with 22/25 within $\pm 10\%$ (Wilcoxon paired absolute-error test $W = 143.0$, $p = 0.615$; $|\text{ORP}|-|\text{RAS}| = -0.60$ pp, 95\% CI $[-2.16, +0.96]$ — statistically indistinguishable, i.e. parity not superiority). (ii) Validated envelope: the original OpenRocket's reliable range of $M < 2$ extends to vehicle-level headline closure through $M = 4.33$ in this work, with an exploratory high-Mach set reaching $M = 7$ (Section 11.6.6) and component-level cone foredrag validated to $M \approx 17$ against a single isolated benchmark.

### 12.3 Subsonic Compatibility

At $M < 1.0$ the extended code paths are either inactive (`ShockGeometry` returns a passthrough with unit ratios; wave-drag models return zero; Van Driest II reduces to incompressible) or reduce identically to the original Barrowman formulas. The subsonic passthrough cost is approximately 200 ns per call -- negligible compared to the $\sim 180$ microsecond component calculation time. All original subsonic regression tests continue to pass without modification, and the integrated 25-flight corpus shows a small positive subsonic bias (+2.54%, $M < 0.8$, $n = 9$) consistent with build/motor-lot variance rather than systematic model error.


### 12.4 Known Limitations

The following limitations are real and known. They are stated here in plain terms, with the reason each remains unfixed in this revision.

**1. NACA RM-10: 80% drag over-prediction.** The model over-predicts the RM-10 zero-lift drag coefficient by 80% (MAPE) across $M = 0.9$--$3.3$. RM-10 is a high-fineness ($f = 12.2$) parabolic body with a smoothly tapered afterbody (base/max diameter $0.606$, local half-angle $\sim 4.8°$ at the base) and four untapered 60°-swept 10%-thick *circular-arc biconvex* fins. This geometry family is formally excluded from the headline 25-flight corpus claim and the envelope statement in Section 11.3.6. Per-component decomposition (`paper/data/legacy/rm10_vs_basic_finner_diagnostic.md`) attributes the deficit to three independent sub-model envelope violations -- (a) the Viswanath boattail correction (Section 6.2.7) is calibrated for $\theta_{\text{bt}} = 6°$--$16°$ and is extrapolated below the band on the real $4.8°$ taper and above the band on the geometry-import terminal contraction, (b) the corpus-anchored finned-body base augmentation (Section 6.2.8) is calibrated against flat-base Basic-Finner geometries and over-credits fin-induced suction when there is an upstream boattail-relief recompression, and (c) the DATCOM 4.1.5.1 fin-section coefficient $K$ has only HEXAGONAL ($K = 4$) and ROUNDED ($K = 16/3$) calibrated entries, neither of which matches the sharp-LE smoothly curving circular-arc section. The deficit is fragmented (no single sub-model accounts for more than $\sim 0.04$ of $C_D$ at $M = 2.0$), so a clean closure would require simultaneous recalibration of all three modules against three separate datasets. **Not fixed because** every isolated patch attempted to date has either regressed Basic Finner or the 25-flight corpus, and the joint calibration set required to disentangle the three sub-model envelopes does not yet exist in the public literature in a digitizable form.

**2. Pitch damping ($C_{mq}$) over-predicts by $3.6\times$ at $M = 1.05$--$1.12$.** Measured against ADA636861 free-flight $C_{mq}$ data on the Basic Finner; corroborated by the Sznajder 2025 ANSYS Fluent CFD comparator (+110 to +160% at $M = 1.08$--$1.11$). The over-prediction comes from the combination of a constant $\times 3$ multiplier on per-component damping and a transonic Gaussian augmentation peaking at $3.5\times$ near $M = 1$. Both constants were calibrated against the integrated 25-flight apogee-turn signature, not against component-level damping measurements. Removing the augmentation breaks the apogee-turn closure on five of the 25 corpus flights. **Not fixed because** correcting the transonic peak requires a second independent free-flight $C_{mq}$ dataset to retune against — recalibrating against ADA636861 would invalidate it as a benchmark — and no such dataset has been located. The Sznajder CFD is a CFD-side second source confirming the bias direction but not a free-flight retune candidate.

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
Magnus body fraction & $0.3$ & Platou (\textit{AIAA J.} 3(1), 1965), 0.3--0.8 & \texttt{calculateDampingMoments} \\
Fin damping cap & $\min(n, 4)$ & Diminishing returns beyond 4 fins & \texttt{getDampingMultiplier} \\
Body damping coefficient & $0.275$ & Body contribution to pitch damping & \texttt{getDampingMultiplier} \\
Vortex asymmetry $K_v$ & $0.20$ & Internally calibrated; no verifiable literature anchor & RK4 vortex term \\
Vortex onset / saturation & $20° / 40°$ & Internally calibrated & same \\
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
Chapman--Korst turbulent base drag \newline (\seqsplit{ChapmanKorstBaseDrag.java}) & \textbf{Off (laminar on)} & The laminar Chapman path is active and validated against TN 3393. The turbulent Chapman--Korst helper exists but the production base-drag path uses the empirical $C_{d,\text{base}}=0.064+0.186/M^2$ correlation (NACA TN 3393 / ESDU 77021) plus the transonic-polynomial blend, which is what the corpus calibration is anchored against. \\
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


### 12.6a Phase 6h Coast-Drag Bias Above $M = 5$ and Proposed Fix

Per-component $C_d$ analysis using `NikeApacheCoastCdDiagnosticTest` against the NASA Apache Performance Handbook Case 1 (clean) coasting table issued by the NASA Goddard Space Flight Center Sounding Rocket Branch (X-721-66-568, Galloway and Crough, 1966) shows that the pressure $C_d$ plateaus at $\sim 0.234$ from $M = 2$ through $M = 8$, against handbook values that decay smoothly from $0.704$ at $M = 2$ to $0.384$ at $M = 8$ without collapsing to the slender-body limit. The mean $C_d$ deficit for $M \ge 5$ is **+0.0595** (handbook minus ORP, averaged over 7 points: $M = 5.00, 5.50, 6.00, 6.50, 7.00, 7.50, 8.00$).

The root cause is the constant `SLENDER_BODY_MACH_DECAY_END = 5.0` in `BarrowmanDragCalculator.java` (lines 1453--1489), which smoothsteps the Hoerner cylindrical-afterbody pressure correction to zero at $M = 5$ for high-fineness bodies. The Apache sustainer with $L/D = 17.4$ still carries appreciable boundary-layer-displacement / viscous-inviscid pressure drag at $M \ge 5$ per Hoerner Chapter 17, which is precisely what the model elides.

The bias accumulates during ballistic coast and scales with peak Mach: Nike-Deacon at $M \approx 5$ closes to $-1$ percent, Nike-Cajun at $M \approx 6.2$ to $+16.6$ percent, and the nine Nike-Apache 1965 flights at $M = 6.4$--$7.0$ to $+24$ to $+36$ percent. **Under the $\pm 10$ percent admission criterion adopted for the Rocket Flight Database corpus (Section 11.6.1), the nine Nike-Apache 1965 flights and the one Nike-Cajun University of Michigan flight fall in the exploratory high-Mach set (Section 11.6.6) and are not part of the 25-flight headline corpus.** All ten `.ork` build files are committed at `paper/data/ork/sounding_rockets/` and become admissible once the fix lands.

The proposed fix is documented as **Phase 6h** in `SUPERSONIC_MODELING.md`:

1. Extend `SLENDER_BODY_MACH_DECAY_END` from $5.0$ to approximately $12.0$.
2. Add a `hypersonicBodyPressureCD` term gated on body $L/D > 15$ AND $M > 3$, calibrated against the X-721-66-568 Case 1 table.

Validation gates for the Phase 6h fix:
- Nike-Deacon must not move by more than $\pm 2$ pp.
- Apache 1965 mean must close to within $\pm 10$ percent.
- The low-$L/D$ corpus (Black Brant V, Raven, Rabia) must not regress.

**Table 12.6a.1 — Phase 6h Apache coast-$C_d$ deficit** (from `NikeApacheCoastCdDiagnosticTest` output against NASA X-721-66-568 Appendix A page 66 Case 1 COASTING). Handbook column is the canonical Apache Case 1 reference. The ORP column reflects the documented pressure-$C_d$ plateau ($\sim 0.234$) combined with the friction and base components.

| $M$ | $C_d$ (handbook X-721-66-568) | $C_d$ (ORP) | Deficit (handbook − ORP) |
|------|----|----|----|
| 5.00 | 0.454 | $\approx 0.395$ | $+0.059$ |
| 5.50 | 0.432 | $\approx 0.373$ | $+0.059$ |
| 6.00 | 0.412 | $\approx 0.353$ | $+0.059$ |
| 6.50 | 0.396 | $\approx 0.337$ | $+0.059$ |
| 7.00 | 0.388 | $\approx 0.329$ | $+0.059$ |
| 7.50 | 0.384 | $\approx 0.325$ | $+0.059$ |
| 8.00 | 0.384 | $\approx 0.325$ | $+0.059$ |
| **Mean $M \ge 5$** |  |  | **$+0.0595$** |

Until Phase 6h closes, the headline 25-flight corpus (Mach 0.54--4.33) is honestly characterized as supersonic-validated, and the separate exploratory high-Mach set (Section 11.6.6) is reported in full as a capability demonstration rather than a validation headline. Once the fix lands, the nine Nike-Apache 1965 flights plus the Nike-Cajun flight already on disk would become admissible and the exploratory $M > 5$ set that currently closes within $\pm 10\%$ would grow from 3 flights to 13 — at which point the framing changes accordingly.

The composite disclosure plot (per-component $C_d$ decomposition vs Mach against NASA X-721-66-568 Case 1 handbook reference; pressure-$C_d$ plateau near 0.234 visible from $M = 2$ through $M = 8$) is at `paper/data/png/phase6h_apache_cd_disclosure.png`.


### 12.7 Acknowledgments, Affiliation, Conflict of Interest, and Reproduction Recipe

#### 12.7.1 Acknowledgments

The author thanks the OpenRocket maintainers and contributors, on whose open-source simulator this work builds; Charles E. Rogers and the RASAero II project for the publicly archived altitude-comparison set that anchors the 25-flight corpus; and the individual flight contributors whose telemetry and reconstruction data populate the Rocket Flight Database. The author also acknowledges Duke University for institutional support.

#### 12.7.2 Author Affiliation

Sole author: Aidan Yu, Department of Mechanical Engineering & Materials Science, Duke University. ORCID [0009-0005-9589-5314](https://orcid.org/0009-0005-9589-5314). Corresponding author: <asy22@duke.edu>.

#### 12.7.3 Conflict of Interest

The author declares no conflict of interest.

#### 12.7.4 Funding

No external funding was received for this work.

#### 12.7.4a Generative AI Use Disclosure

Generative AI tools were used solely for language editing, formatting, and code review. All claims, equations, derivations, and numerical results were authored and verified by the human author, who takes full responsibility for the content. No AI system is an author of this report.

#### 12.7.5 Software Availability and DOI

The OpenRocket Plus source code is available at <https://github.com/AidanSYu/openrocketsupersonic>. A persistent software archive will be deposited on Zenodo, with the citable DOI minted from the tagged source release at submission (see Section 12.7.6). The validation dataset (Rocket Flight Database — 25-flight headline corpus) is deposited at the same DOI as the original v1.0 release and is citable as <https://doi.org/10.5281/zenodo.20531977>.

#### 12.7.6 Reproduction Recipe for the 25-Flight Corpus Closure

The headline aggregate apogee statistics (mean signed $-0.38\%$, MAE 4.74\%) across the 25-flight corpus are reproducible from the source tree as follows. The pinned commit for the manuscript revision is `f84c66857eb2fa5e0f4dd4313fc8b41d77801ba5` on branch `supersonic-aero-dev`; the citable source-archive tag is minted at submission (the persistent software Zenodo DOI in Section 12.7.5 is deferred until that tag is pushed). Substitute this commit for `<COMMIT>` below.

```bash
git clone https://github.com/AidanSYu/openrocketsupersonic.git
cd openrocketsupersonic
git checkout <COMMIT>          # or the manuscript tag once minted
./gradlew core:test --tests "info.openrocket.core.aerodynamics.SimVRealBenchmarkTest"
```

On Windows, substitute `gradlew.bat` for `./gradlew`. Expected runtime: approximately 11 minutes for the full aerodynamics test suite, of which `SimVRealBenchmarkTest` is a fraction. Per-flight outputs and the aggregate error summary are written under `core/build/reports/tests/test/` and `core/build/test-results/test/`. The per-case CSV that anchors the manuscript table is generated as `paper/data/csv/simvreal_baseline_2026_05_01.csv` (frozen at the same commit). The companion head-to-head comparison artifact (this work versus the recorded RASAero II predictions on the same imported geometries) is `paper/data/md/rasaero_head_to_head_2026_05_01.md`. The corpus itself, including the `.CDX1` import files and Rogers-published RASAero II reference apogees, is archived at <https://doi.org/10.5281/zenodo.20531977>.

A regression tolerance of $\pm 2$ percentage points per case is enforced by the test harness; deviations beyond this band fail the build and indicate either an environment difference (JVM, gradle daemon state, motor-thrust-curve cache) or an unintended modeling change.


### References

1. Ackeret, J. (1925). "Luftkrafte auf Flugel, die mit grosserer als Schallgeschwindigkeit bewegt werden." *Zeitschrift fur Flugtechnik und Motorluftschiffahrt*, 16, pp. 72--74.
2. Allen, H. J. and Perkins, E. W. (1951). "A Study of Effects of Viscosity on Flow Over Slender Inclined Bodies of Revolution." NACA Report 1048. Cited as the originating source for the crossflow-analogy method name.
3. Ames Research Staff (1953). "Equations, Tables, and Charts for Compressible Flow." NACA Report 1135.
4. Anderson, J. D. (2006). *Hypersonic and High-Temperature Gas Dynamics*, 2nd ed. AIAA Education Series.
5. Anderson, J. D. (2017). *Modern Compressible Flow: With Historical Perspective*, 4th ed. McGraw-Hill.
6. AP09 (2009). "Aeroprediction Code Methodology (AP09)." Code-cited methodology note for the AP09-style rational blend implemented in `RationalBlend.java`; exact public report metadata is not present in the repository.
7. Barrowman, J. S. (1967). "The Practical Calculation of the Aerodynamic Characteristics of Slender Finned Vehicles." M.S. Thesis, The Catholic University of America.
8. Chapman, D. R. (1950). "Base Pressure at Supersonic Velocities." NACA TN 2137. Originating source for the laminar base-drag $C_\text{LAM}=1870$ scaling in Section 6.2.4.
9. Chapman, D. R. (1951). "An Analysis of Base Pressure at Supersonic Velocities and Comparison with Experiment." NACA Report 1051.
10. Champigny, P. and Lacau, R. G. (1994). "Lateral Aerodynamics of a Missile at High Angles of Attack." AGARD CP-536, as cited in `BarrowmanCalculator` and `VortexSideforceBenchmarkTest`; the repository's local AGARD CP-536 PDF is a different proceedings volume and is not used as a source artifact for this claim.
11. DATCOM (1978). "USAF Stability and Control DATCOM." Air Force Flight Dynamics Laboratory, AFFDL-TR-79-3032, revised.
12. **Reference removed.** The previously listed "Devan, L. and Ashwood, R. (1965). 'The Base Drag of Blunt-Trailing-Edge Airfoils and Bodies at Transonic and Supersonic Speeds.' NASA TN D-721" could not be independently verified through NTRS or DTIC search. The production turbulent base-drag correlation $C_{d,\text{base}} = 0.064 + 0.186/M^{2}$ is anchored against ESDU 77021 (Reference 14 below) and NACA TN 3393 (Reference 27 below); the "Devan-Ashwood" descriptor is retained in the code comments as a historical attribution but the primary verifiable source is ESDU 77021.
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
23. **Reference removed.** The previously listed Jorgensen, L. H. (1977), "Prediction of Static Aerodynamic Characteristics for Slender Bodies Alone and with Lifting Surfaces to Very High Angles of Attack," NASA TN D-6996, is redundant with the in-repo Jorgensen TR R-474 (1973) (Reference 22), which is the primary anchor for the $C_{d,c}=1.20$ crossflow constant. No claim in this report depends on TN D-6996 independently, so it is dropped.
24. Perkins, E. W. and Jorgensen, L. H. (1952). "Investigation of the Drag of Various Axially Symmetric Nose Shapes of Fineness Ratio 3 for Mach Numbers from 1.24 to 3.67." NACA RM A52H28.
25. NACA (1954). "Free-Flight Measurements of the Zero-Lift Drag of Several Wings at Mach Numbers from 1.1 to 1.6." NACA TN 3650.
26. Jackson, H. H., Rumsey, C. B., and Chauvin, L. T. (1954). "Flight Measurements of Drag and Base Pressure of a Fin-Stabilized Parabolic Body of Revolution (NACA RM-10) at Different Reynolds Numbers and at Mach Numbers from 0.9 to 3.3." NACA TN 3320.
27. Reller, J. O., Jr. and Hamaker, F. M. (1955). "An Experimental Investigation of the Base Pressure Characteristics of Nonlifting Bodies of Revolution at Mach Numbers from 2.73 to 4.98." NACA TN 3393.
28. Stoney, W. E. (1961). "Collection of Zero-Lift Drag Data on Bodies of Revolution from Free-Flight Investigations." NASA TR-R-100.
29. Jorgensen, L. H., Spahr, J. R., and Hill, W. A., Jr. (1962). "Comparison of the Effectiveness of Flares with That of Fins for Stabilizing Low-Fineness-Ratio Bodies at Mach Numbers from 0.6 to 5.8." NASA TM X-653.
30. Nielsen, J. N. (1960). *Missile Aerodynamics*. McGraw-Hill.
31. **Reference removed.** The previously listed "Paul, R. and Wedemeyer, E. (1982). 'Aerodynamic Characteristics of Ogive-Cylinder Bodies at High Angles of Attack.' EOARD-TR-82-7" could not be independently verified and is no longer cited as a source: the vortex-asymmetry coefficient $K_v = 0.20$ (Section 9.6 / 9.9.3) is presented as an internally-calibrated coefficient with no literature anchor, not as an externally benchmarked value.
32. Pitts, W. C., Nielsen, J. N., and Kaattari, G. E. (1957). "Lift and Center of Pressure of Wing-Body-Tail Combinations at Subsonic, Transonic, and Supersonic Speeds." NACA Report 1307. Originating source for the PNK $F_{WB}/F_{BW}$ interference factors (Table 12.1).
33. Platou, A. S. (1965). "Magnus Characteristics of Finned and Nonfinned Projectiles." *AIAA Journal*, **3**(1), 83–90. DOI: 10.2514/3.2791. (Replaces the previously cited "BRL Report 1193, 1963," for which no NTRS/DTIC record could be located; the AIAA Journal publication is the verifiable primary source for Platou's Magnus measurements.)
34. Puckett, A. E. and Stewart, H. J. (1947). "Aerodynamic Performance of Delta Wings at Supersonic Speeds." *Journal of the Aeronautical Sciences*, 14(10).
35. Sutherland, W. (1893). "The Viscosity of Gases and Molecular Force." *Philosophical Magazine*, Series 5, 36(223), pp. 507--531.
36. Tobak, M. and Wehrend, W. R. (1956). "Stability Derivatives of Cones at Supersonic Speeds." NACA TN 3788.
37. Anderson, C. F. (1970). "An Investigation of the Aerodynamic Characteristics of the AGARD Model B for Mach Numbers from 0.2 to 1.0." AEDC-TR-70-100, Arnold Engineering Development Center. Reference source for the AGARD-B benchmark (Section 11.3.5).
38. AEDC (1976). "Experimental Roll-Damping, Magnus, and Static-Stability Characteristics of Two Slender Missile Configurations at High Angles of Attack (0 to 90 Deg) and Mach Numbers 0.2 Through 2.5." AEDC-TR-76-58.
39. US Standard Atmosphere (1976). "U.S. Standard Atmosphere, 1976." NOAA/NASA/USAF, U.S. Government Printing Office.
40. Van Driest, E. R. (1956). "The Problem of Aerodynamic Heating." *Aeronautical Engineering Review*, 15(10), pp. 26--41.
41. Viswanath, P. R. (1996). "Flow Management Techniques for Base and Afterbody Drag Reduction." *Progress in Aerospace Sciences*, 32(2--3), pp. 79--129.
42. **Reference removed.** The previously listed Whitcomb, R. T. (1956), "A Study of the Zero-Lift Drag-Rise Characteristics of Wing-Body Combinations Near the Speed of Sound," NACA Report 1273, was cited only as the method-name label for the off-status `TransonicAreaRule.java` integrator (Table 12.2), which is not on the headline path. No active claim depends on it, so it is dropped; "Whitcomb area rule" is retained only as a descriptive method name.
43. Zipfel, P. H. (2007). *Modeling and Simulation of Aerospace Vehicle Dynamics*, 2nd ed. AIAA Education Series.
44. Chapman, D. R., Kuehn, D. M., and Larson, H. K. (1958). "Investigation of Separated Flows in Supersonic and Subsonic Streams with Emphasis on the Effect of Transition." NACA Report 1356. Originating source for the free-interaction SBLI theory at fin roots (Section 6.8).
45. Ferris, J. C. (1967). "Static Stability Investigation of a Single-Stage Sounding Rocket at Mach Numbers from 0.60 to 1.20." NASA TN D-4013, Langley Research Center, June 1967.
46. Babb, C. D. and Fuller, D. E. (1967). "Static Stability Investigation of a Sounding-Rocket Vehicle at Mach Numbers from 1.50 to 4.63." NASA TN D-4014, Langley Research Center, June 1967.
47. Bhagwandin, V. A. and Sahu, J. (2013). "Numerical Prediction of Pitch Damping Stability Derivatives for Finned Projectiles." ARL-TR-6725, US Army Research Laboratory, Aberdeen Proving Ground, MD, November 2013. DTIC Accession ADA592550. Second-source CFD comparator for the $C_{mq}$ supersonic-bias audit (Section 9.9.6), reported at B-level pending the AFF fin-planform fixture (Figure 3) required for A-level promotion.
48. Bunescu, I., Hothazie, M.-V., Stoican, M.-G., Pricop, M.-V., Onel, A.-I., and Afilipoae, T.-P. (2025). "Numerical Study of the Basic Finner Model in Rolling Motion." *Aerospace*, **12**(5), 371. DOI: 10.3390/aerospace12050371. Open access (CC BY 4.0).
49. Bollermann, B. and Walker, R. L. (1973). "Design, Development and Flight Test of the Super Loki Stable Booster Rocket Systems." AFCRL-TR-73-0412 / AD-766737, Space Data Corp., Phoenix AZ, prepared for AFCRL Hanscom, 30 June 1973.
50. Sahu, J., Nietubicz, C. J., and Steger, J. L. (1983). "Numerical Computation of Base Flow for a Projectile at Transonic Speed." ARBRL-TR-02495 / AD-A130-293, US Army Ballistic Research Laboratory, Aberdeen Proving Ground, MD, June 1983. Cited as the secondary CFD anchor for transonic base-flow validation; not exercised as a comparator in the present revision (Section 9.10).
51. Vidanović, N. D., Rašuo, B. P., Damljanović, D. B., Vuković, Đ. S., and Ćurčić, D. S. (2014). "Validation of the CFD code used for determination of aerodynamic characteristics of nonstandard AGARD-B calibration model." *Thermal Science*, **18**(4), pp. 1223–1233. DOI: 10.2298/TSCI130409104V. Reference CFD dataset cited in Section 9.12; no closed-loop OpenRocket Plus comparator at the AGARD-B geometry in the present revision.
52. Sznajder, J. (2025). "Computational Determination of Dynamic Stability Derivatives." *Transactions on Aerospace Research*, No. 4, pp. 98–121. DOI: 10.2478/tar-2025-0021. ANSYS Fluent computations of $C_{mq}$ and $C_{m\dot\alpha}$ on the Army-Navy Basic Finner over $M = 0.9$--$5.0$ using three independent CFD techniques (MRF, FOM, IRM); used as the primary CFD-side $C_{mq}$ comparator in Section 9.11.

**External validation artifacts:**

- Yu, A. (2026). *Rocket Flight Database* [Data set]. Zenodo. Concept DOI: <https://doi.org/10.5281/zenodo.20531977>.
- Rogers, C. E. *RASAero II Comparisons with Altitude Data.* <https://www.rasaero.com/comparisons-alt.htm>. Source for measured apogees and reference RASAero II predictions.

**Internal validation artifacts** (not external references; included for traceability):

- `paper/data/corpus_summary_2026_05_01.md` -- 25-flight v1.0 integrated corpus baseline; 25-flight summary at `paper/data/analysis/corpus_bias_variance_2026_05_11/corpus_bias_variance_summary.md`.
- `paper/data/csv/simvreal_baseline_2026_05_01.csv` -- per-case CSV regression baseline.
- `paper/data/md/rasaero_head_to_head_2026_05_01.md` -- this work versus RASAero II head-to-head on the same imported flights.
- `paper/data/md/dynamic_stability_benchmark.md` -- full Mach sweep for $C_{mq}$, roll damping, Magnus.
- `paper/data/md/nasa_tm_x653_validation_report.md` -- NSCFB CNa / xCP closure memo.
- `paper/data/outlier_closure/*.md` -- per-case closure memos (raven, kinsel, mesos_293k, dontdebatethis, proteus6, fmj_balls005, subsonic_nonaero_outliers).
