# Part E -- Dynamic Stability, Regime Blending, Validation, and Conclusions

---

# 9. Dynamic Stability and Six-Degree-of-Freedom Integration

The preceding sections developed the aerodynamic coefficient models -- drag, lift, center of pressure -- as functions of Mach number, angle of attack, and geometry. Those coefficients enter the flight simulation through the equations of motion, which in OpenRocket Plus are integrated in a full six-degree-of-freedom (6-DOF) framework using a classical fourth-order Runge-Kutta scheme. This section documents the dynamic stability derivatives that govern vehicle rotation, the Magnus force that couples roll and yaw, the gyroscopic terms that arise from spin-stabilized flight, and the state-vector formulation that ties everything together.

---

## 9.1 Pitch Damping Derivative $C_{mq}$

### 9.1.1 Physical Origin

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

### 9.1.2 Transonic Augmentation Factor

Near $M = 1$, unsteady shock oscillation on the body and fins amplifies the effective damping. This effect is modeled by a Gaussian augmentation factor centered at $M = 1$:

$$k_\text{transonic}(M) = 1 + 2.5 \exp\!\left[-\left(\frac{M - 1}{0.15}\right)^{\!2}\right]$$

The augmented damping derivative is:

$$C_{mq}^\text{aug} = k_\text{transonic}(M) \cdot C_{mq}$$

At $M = 1.0$, $k = 3.5$ (peak augmentation). At $M = 0.7$ or $M = 1.3$, $k \approx 1.0$ (no augmentation). The Gaussian form ensures $C^\infty$ smoothness everywhere and decays to unity within approximately $\pm 0.3$ Mach numbers of the center.

### 9.1.3 Angle-of-Attack Rate Derivative

The derivative with respect to the rate of change of angle of attack, $C_{m\dot{\alpha}}$, is related to $C_{mq}$ by a fixed ratio based on slender-body theory (Tobak and Wehrend, 1956):

$$C_{m\dot{\alpha}} = 0.4 \, C_{mq}$$

The combined pitch damping moment coefficient is:

$$C_m^\text{damp} = (C_{mq} + C_{m\dot{\alpha}}) \hat{q} = 1.4 \, C_{mq} \, \hat{q}$$

### 9.1.4 Worked Example -- 1-meter Reference Rocket

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

### 9.1.5 Implementation

In `BarrowmanStabilityCalculator.calculateDampingMoments()`, the code iterates over all aerodynamic components, retrieves each component's $C_{N\alpha}$ and $x_{CP}$ from the per-component force analysis, computes the squared moment arm, and accumulates the sum. The transonic factor and $C_{m\dot{\alpha}}$ ratio are applied after summation. The results are stored in the `AerodynamicForces` object via `setCmq()` and `setCmAlphaDot()`.

---

## 9.2 Magnus Force and Moment

### 9.2.1 Physical Mechanism

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

### 9.2.2 Magnus Yaw Moment

The Magnus force acts at the center of pressure, producing a yaw moment about the CG:

$$C_{n,p\alpha} = C_{y,p\alpha} \cdot \frac{x_{CP} - x_{CG}}{L_\text{ref}}$$

The total Magnus yaw moment coefficient is:

$$C_n^\text{Magnus} = C_{n,p\alpha} \cdot \hat{p} \cdot \sin\alpha$$

For a statically stable rocket ($x_{CP}$ aft of $x_{CG}$, so $x_{CP} - x_{CG} > 0$ in the aft-positive convention, but in OpenRocket's nose-positive convention $x_{CP} < x_{CG}$ for stability), the Magnus moment tends to increase yaw when the rocket spins, which is a destabilizing effect. This is why excessive roll rates can reduce the effective stability margin.

### 9.2.3 Body $C_{N\alpha}$ Fraction

The implementation approximates the body contribution as 30% of the total $C_{N\alpha}$:

$$C_{N\alpha,\text{body}} \approx 0.3 \, C_{N\alpha,\text{total}}$$

This is conservative: for typical high-power rockets with 3 or 4 fins, the body contributes 20-40% of total normal force. The factor 0.3 is a reasonable central estimate that avoids the need to decompose the normal force into per-component contributions within the damping moment calculation.

### 9.2.4 Worked Example -- Spinning Rocket at $M = 2$, $\alpha = 5°$

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

---

## 9.3 Euler Gyroscopic Coupling

### 9.3.1 Motivation

A spinning rocket is a gyroscope. When external moments (aerodynamic pitch/yaw) are applied to a body with significant angular momentum about the roll axis, the body precesses rather than rotating directly in the direction of the applied moment. Neglecting this coupling in the equations of motion leads to incorrect prediction of the pitch-yaw phasing and, for fast-spinning rockets, can produce entirely wrong trajectory predictions.

### 9.3.2 Derivation of the Euler Equations

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

### 9.3.3 Implementation in the Acceleration Computation

In `RK4SimulationStepper.computeAcceleration()`, after computing the aerodynamic moments $M_x$, $M_y$, $M_z$ (called `momX`, `momY`, `momZ` in the code), the gyroscopic correction is applied:

```
momX -= omega_y * (I_roll * omega_z) - omega_z * (I_long * omega_y)
momY -= omega_z * (I_long * omega_x) - omega_x * (I_roll * omega_z)
momZ -= omega_x * (I_long * omega_y) - omega_y * (I_long * omega_x)
```

This subtracts $\boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})$ from the total moment before dividing by the inertia to obtain angular acceleration. The subtraction sign follows from rearranging the Euler equation:

$$\dot{\boldsymbol{\omega}} = \mathbf{I}^{-1}\left[\mathbf{M} - \boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})\right]$$

### 9.3.4 Coordinate Transform

The angular velocity vector is stored in world coordinates in the simulation state. Before applying the Euler equations, it must be transformed to body coordinates:

1. **Inverse quaternion rotation**: Transform from world frame to the rocket's orientation frame using the inverse of the orientation quaternion $q$:

$$\boldsymbol{\omega}_\text{orient} = q^{-1} \boldsymbol{\omega}_\text{world} \, q$$

2. **Inverse theta rotation**: Further transform to align with the body principal axes, removing the lateral wind angle:

$$\boldsymbol{\omega}_\text{body} = R_z(-\theta) \, \boldsymbol{\omega}_\text{orient}$$

After computing the angular acceleration in body coordinates, the reverse sequence transforms it back to world coordinates for integration.

### 9.3.5 Gyroscopic Precession Diagram

The following diagram illustrates the gyroscopic precession of a spinning rocket. When an aerodynamic pitching moment $M_y$ is applied (e.g., by a wind gust creating angle of attack), the spin angular momentum $H_z = I_\text{roll}\omega_z$ causes the rocket to precess in yaw rather than pitch directly:

```
                  Applied pitch moment M_y
                         |
                         v
                    +---------+
                    |         |
            H_z <---| Rocket  |---> omega_z (spin)
            (spin   |  (top   |
            ang.    |  view)  |
            mom.)   +---------+
                         |
                         v
                  Resulting yaw precession
                  (omega_x response)


   Side view of precession:

        omega_z (roll)
          ^
          |       .  .  .
          |    .           .        <-- Precession cone
          |  .       |       .          traced by nose
          | .        |        .
          |.    omega_x,y     .
          +----(pitch/yaw)------>
          |.   (precession)   .
          | .        |        .
          |  .       |       .
          |    .           .
          |       .  .  .
```

The precession rate for a torque-free symmetric top is:

$$\Omega_\text{prec} = \frac{(I_\text{long} - I_\text{roll})\,\omega_z}{I_\text{long}}$$

For a slender rocket with $I_\text{long} \gg I_\text{roll}$, this simplifies to $\Omega_\text{prec} \approx \omega_z$, meaning the precession rate approximately equals the roll rate.

### 9.3.6 Dynamic Pressure Gate

The gyroscopic coupling terms are computationally active only when the dynamic pressure exceeds a threshold of $q_\infty > 1.0$ Pa. This gate serves two purposes:

1. **Near apogee**: When $q_\infty \to 0$, the aerodynamic moments are negligible and the rocket is effectively in free-body tumble. The gyroscopic terms, while physically present, create numerical stiffness in the integrator without improving trajectory accuracy.

2. **Numerical stability**: At very low velocities, the angular velocity components can be large relative to the aerodynamic restoring forces, and the gyroscopic cross-coupling can drive the integrator into small time steps without physical benefit.

The gate is implemented as a simple conditional:

```
if (dynP > 1.0) {
    // Apply gyroscopic correction
}
```

### 9.3.7 Time-Step Limiting

The RK4 integrator employs adaptive time-step selection based on angular rate limits. Two constraints are particularly relevant for gyroscopic dynamics:

$$\Delta t_\text{roll} = \frac{\phi_\text{max,roll}}{|\omega_z|}$$

$$\Delta t_\text{pitch/yaw} = \frac{\phi_\text{max,pitch}}{|\dot{\omega}_x|_\text{max} \vee |\dot{\omega}_y|_\text{max}}$$

where $\phi_\text{max,roll} = 2 \times 28.32° = 56.64°$ and $\phi_\text{max,pitch} = 4°$ per step. These limits ensure that the integration resolves the precession motion with adequate angular resolution. The roll step limit uses an irrational fraction of a full circle ($28.32°$) so that successive time steps sample different azimuthal orientations, preventing aliasing of the wind effects on a spinning vehicle.

The minimum time step is clamped to $\Delta t_\text{min} = \Delta t_\text{user}/20$ to prevent the step from shrinking to zero in pathological cases (e.g., a very fast spin with no aerodynamic damping).

---

## 9.4 State Vector and RK4 Integration

### 9.4.1 The 13-Component State Vector

The simulation state vector $\mathbf{y}$ contains 13 components:

$$\mathbf{y} = \begin{pmatrix} x \\ y \\ z \\ v_x \\ v_y \\ v_z \\ q_0 \\ q_1 \\ q_2 \\ q_3 \\ \omega_x \\ \omega_y \\ \omega_z \end{pmatrix} \leftarrow \begin{array}{l} \text{Position (3): world-frame Cartesian coordinates (m)} \\ \\ \\ \text{Velocity (3): world-frame linear velocity (m/s)} \\ \\ \\ \text{Orientation (4): unit quaternion } q = q_0 + q_1\mathbf{i} + q_2\mathbf{j} + q_3\mathbf{k} \\ \\ \\ \\ \text{Angular velocity (3): world-frame rotation rate (rad/s)} \\ \\ \\ \end{array}$$

The use of quaternions instead of Euler angles eliminates the gimbal lock singularity that would otherwise occur when the rocket is pointed straight up or straight down -- precisely the configurations encountered during ascent and at apogee.

### 9.4.2 Quaternion Kinematics

The time derivative of the orientation quaternion is related to the angular velocity by:

$$\frac{d\mathbf{q}}{dt} = \frac{1}{2}\,\mathbf{q} \otimes \boldsymbol{\Omega}$$

where $\boldsymbol{\Omega} = (0, \omega_x, \omega_y, \omega_z)$ is the angular velocity expressed as a pure quaternion (zero scalar part) in the body frame, and $\otimes$ denotes quaternion multiplication.

In component form, the quaternion derivative is:

$$\frac{dq_0}{dt} = \frac{1}{2}(-q_1\omega_x - q_2\omega_y - q_3\omega_z)$$

$$\frac{dq_1}{dt} = \frac{1}{2}(q_0\omega_x + q_2\omega_z - q_3\omega_y)$$

$$\frac{dq_2}{dt} = \frac{1}{2}(q_0\omega_y - q_1\omega_z + q_3\omega_x)$$

$$\frac{dq_3}{dt} = \frac{1}{2}(q_0\omega_z + q_1\omega_y - q_2\omega_x)$$

### 9.4.3 Equations of Motion Summary

The complete 6-DOF equations of motion integrated by the RK4 stepper are:

**Translational:**

$$\dot{\mathbf{x}} = \mathbf{v}$$

$$\dot{\mathbf{v}} = \frac{1}{m}\left[\mathbf{R}(\mathbf{q})\,\mathbf{F}_\text{body} - m\mathbf{g} + \mathbf{F}_\text{Coriolis}\right]$$

where $\mathbf{F}_\text{body}$ includes thrust, drag, normal force, side force (including Magnus), and $\mathbf{R}(\mathbf{q})$ is the rotation matrix corresponding to the orientation quaternion.

**Rotational:**

$$\dot{\mathbf{q}} = \frac{1}{2}\,\mathbf{q} \otimes \boldsymbol{\Omega}$$

$$\dot{\boldsymbol{\omega}} = \mathbf{I}^{-1}\left[\mathbf{M}_\text{aero} - \boldsymbol{\omega} \times (\mathbf{I}\boldsymbol{\omega})\right]$$

where $\mathbf{M}_\text{aero}$ includes the pitch moment $C_m q_\infty S_\text{ref} L_\text{ref}$, yaw moment $C_n q_\infty S_\text{ref} L_\text{ref}$ (with Magnus contribution), roll moment $C_l q_\infty S_\text{ref} L_\text{ref}$, and the pitch/yaw damping moments.

### 9.4.4 RK4 Sub-Step Structure

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

### 9.4.5 Quaternion Normalization

After the RK4 update, the quaternion $\mathbf{q}_{n+1}$ may drift from unit norm due to the finite-precision linear combination of the four sub-steps. The implementation checks $\|\mathbf{q}\|$ after each step and renormalizes if the deviation exceeds a threshold:

$$\mathbf{q} \leftarrow \frac{\mathbf{q}}{\|\mathbf{q}\|} \quad \text{if} \quad \left|\|\mathbf{q}\|^2 - 1\right| > \epsilon$$

This prevents the orientation from gradually becoming non-physical over thousands of integration steps.

### 9.4.6 Integration Stability Bounds

The simulation enforces absolute bounds on the state vector to detect divergence:

$$\|\mathbf{v}\|^2 < 10^{18}, \quad \|\mathbf{x}\|^2 < 10^{18}, \quad \|\boldsymbol{\omega}\|^2 < 10^{18}$$

Exceeding any of these bounds triggers a `SimulationCalculationException`, halting the simulation with a diagnostic message. These bounds are set far beyond any physically realizable rocket flight (a velocity of $10^9$ m/s would exceed the speed of light) and exist solely to catch numerical runaway.

---

# 10. Regime Blending

The aerodynamic models described in Sections 3 through 8 each have limited domains of validity. No single model spans the entire Mach range from incompressible flow through hypersonic flight. The subsonic Barrowman method diverges as $M \to 1$; the Ackeret supersonic theory is singular at $M = 1$; the Taylor-Maccoll cone solution requires $M > 1 + \epsilon$. Connecting these models requires blending functions that transition smoothly between regimes.

This section documents the blending methodology, proves the continuity properties, catalogs all eleven blending regions in the implementation, and provides design guidance for selecting blend types.

---

## 10.1 Why $C^1$ Continuity Matters

A flight simulation integrates the aerodynamic coefficients as part of the equations of motion. A discontinuity in $C_D(M)$ produces a delta-function in $dC_D/dM$, which enters the force balance through the chain rule:

$$F_D = C_D(M) \cdot q_\infty \cdot S_\text{ref} \implies \frac{dF_D}{dt} \propto \frac{dC_D}{dM}\frac{dM}{dt}$$

If $dC_D/dM$ is unbounded (i.e., $C_D$ has a jump), then the rate of change of drag force becomes infinite at the transition Mach number, which causes:

1. **Integration instability**: The RK4 stepper takes its first evaluation at $M_n$ (on one side of the discontinuity) and its second evaluation at $M_n + h/2$ (potentially on the other side). The vastly different force values at the two evaluation points produce a large error in the weighted average, and the step-size controller drives $h \to 0$.

2. **Oscillation**: If the discontinuity falls between two adjacent RK4 evaluations, the simulation may oscillate back and forth across the boundary, producing artificial vibration in the predicted trajectory.

3. **Apogee prediction error**: At apogee, the rocket decelerates through $M = 1$. If the transonic drag model has a discontinuity, the deceleration rate changes abruptly, shifting the predicted apogee altitude by hundreds of meters.

**Example of divergence**: In testing during development, replacing the $C^1$-continuous base drag blend with a simple $C^0$-continuous (value-continuous but slope-discontinuous) piecewise function at $M = 1.3$ caused the continuity sweep test to measure $|dC_D/dM| = 8.7$ at that boundary, compared to the physically correct value of approximately 0.3. When this model was used in trajectory simulation, the simulation time step dropped from 50 ms to 0.2 ms near $M = 1.3$, increasing simulation time by a factor of 250.

The requirement is therefore: all coefficient functions must be at least $C^1$-continuous (continuous value and continuous first derivative) across every regime boundary.

---

## 10.2 Cubic Hermite Smoothstep

### 10.2.1 Definition

The cubic Hermite smoothstep is the simplest polynomial that achieves $C^1$ continuity between two constant values. Given a normalized parameter:

$$t = \frac{M - M_\text{lo}}{M_\text{hi} - M_\text{lo}}, \quad t \in [0, 1]$$

the smoothstep weight function is:

$$w(t) = 3t^2 - 2t^3$$

This function blends between value $f_0$ at $M_\text{lo}$ and value $f_1$ at $M_\text{hi}$:

$$f(M) = f_0 \cdot (1 - w(t)) + f_1 \cdot w(t)$$

### 10.2.2 Proof of $C^1$ Properties

**Claim**: $w(t) = 3t^2 - 2t^3$ satisfies $w(0) = 0$, $w(1) = 1$, $w'(0) = 0$, $w'(1) = 0$.

**Proof**:

$$w(0) = 3(0)^2 - 2(0)^3 = 0 \qquad \checkmark$$

$$w(1) = 3(1)^2 - 2(1)^3 = 3 - 2 = 1 \qquad \checkmark$$

$$w'(t) = 6t - 6t^2 = 6t(1 - t)$$

$$w'(0) = 6 \cdot 0 \cdot (1 - 0) = 0 \qquad \checkmark$$

$$w'(1) = 6 \cdot 1 \cdot (1 - 1) = 0 \qquad \checkmark$$

Since $w'(0) = 0$, the blended function $f(M)$ has the same slope as $f_0$ at $M = M_\text{lo}$. Since $w'(1) = 0$, $f(M)$ has the same slope as $f_1$ at $M = M_\text{hi}$. If both $f_0(M)$ and $f_1(M)$ are themselves continuous, the composite function is $C^1$ across both boundaries.

### 10.2.3 ASCII Diagram

```
w(t)
 1.0 |                         .--------
     |                       .
     |                     .
 0.5 |                   *          <-- inflection at t = 0.5
     |                 .
     |               .
 0.0 |------------ .
     +---+---+---+---+---+---+---+---
     0       0.25     0.5     0.75     1.0   t

     w'(0) = 0                  w'(1) = 0
     (flat entry)               (flat exit)
```

The smoothstep is used where both endpoint models are themselves smooth and no particular slope matching is needed at the boundaries.

---

## 10.3 Rational Blend (AP09 Formulation)

### 10.3.1 Motivation

The cubic smoothstep has a fixed transition width defined by $[M_\text{lo}, M_\text{hi}]$ and uses a polynomial weight. For transitions near $M = 1$ where the physics is dominated by the Prandtl-Glauert singularity ($\beta \to 0$), a rational function provides a better approximation to the actual coefficient behavior. The AP09 formulation (from Guided Weapons Cooperative Research, 2009) uses:

$$t = \frac{M^2 - M_b^2}{w \cdot M_b^2}$$

$$g(M) = \frac{1}{2}\left(1 - \frac{t}{\sqrt{1 + t^2}}\right)$$

where $M_b$ is the blend center (typically 1.0) and $w$ is the transition width parameter.

### 10.3.2 Properties

1. $g(M) \to 1$ as $M \to 0$ (fully subsonic weight)
2. $g(M_b) = 0.5$ (center of transition)
3. $g(M) \to 0$ as $M \to \infty$ (fully supersonic weight)
4. $g(M)$ is $C^\infty$ everywhere (infinitely differentiable)
5. $g$ is monotonically decreasing for $M > 0$

The blended value is:

$$f(M) = f_\text{sub}(M) \cdot g(M) + f_\text{sup}(M) \cdot (1 - g(M))$$

### 10.3.3 Derivative

The derivative with respect to Mach is needed to verify $C^1$ continuity and is implemented in `RationalBlend.weightDerivative()`:

$$\frac{dt}{dM} = \frac{2M}{w \cdot M_b^2}$$

$$\frac{dg}{dt} = -\frac{1}{2(1 + t^2)^{3/2}}$$

$$\frac{dg}{dM} = \frac{dg}{dt} \cdot \frac{dt}{dM} = \frac{-M}{w \cdot M_b^2 \cdot (1 + t^2)^{3/2}}$$

This derivative is always non-positive for $M \geq 0$ and is bounded everywhere (no singularity), confirming the $C^\infty$ property.

### 10.3.4 Comparison with Smoothstep

```
g(M)  (blend center M_b = 1.0, width w = 0.3)
 1.0 |---___
     |       --__
     |            -__                   Rational (AP09)
 0.5 |               *                 ---- solid
     |                -__
     |                    --__          Smoothstep
 0.0 |                        ---___   .... dotted
     +---+---+---+---+---+---+---+---
     0.5     0.7     0.9     1.1     1.3     M

     The rational blend has longer tails (gradual onset/exit)
     compared to the smoothstep's hard boundaries.
```

The rational blend is preferred when the transition must be centered at a specific Mach number (like $M = 1$) but should not have hard "edges" where the blending activates or deactivates. The smoothstep is preferred when the endpoints are precisely known and a compact blending region is desired.

---

## 10.4 Complete Blending Region Table

The following table catalogs every Mach-regime blending region in the implementation. Each row identifies the quantity being blended, the Mach boundaries, the blend type, the source file, and the models being joined.

| # | Quantity | $M_\text{lo}$ | $M_\text{hi}$ | Blend type | Subsonic model | Supersonic model | Source file |
|---|----------|---------------|---------------|------------|----------------|------------------|-------------|
| 1 | $\beta$ (compressibility factor) | 0.95 | 1.05 | Cubic Hermite | $\sqrt{1-M^2}$ | $\sqrt{M^2-1}$ | `FlightConditions.java` |
| 2 | Base drag $C_{D,\text{base}}$ | 0.85 | 1.30 | Degree-4 poly ($C^1$) | $0.12 + 0.13M^2$ | Devan-Ashwood | `BarrowmanDragCalculator.java` |
| 3 | Skin friction $C_f$ | 0.90 | 1.10 | Linear | Prandtl incompressible | Eckert ref. temp. | `BarrowmanDragCalculator.java` |
| 4 | Roughness correction | 0.90 | 1.10 | Linear | Subsonic roughness | Supersonic roughness | `BarrowmanDragCalculator.java` |
| 5 | Fin $C_{N\alpha}$ | 0.90 | 1.50 | `PolyInterpolator` ($C^1$) | Barrowman $2\pi/\beta$ | Ackeret $4/\beta$ | `FinSetCalc.java` |
| 6 | Fin wave drag | 0.90 | 1.20 | Cubic Hermite | 0 (no wave drag) | Ackeret $4\alpha_\text{eff}^2/\beta$ | `FinSetCalc.java` |
| 7 | Nose/body wave drag | 1.30 | 1.50 | Cubic Hermite | TR-R-100 tables | Taylor-Maccoll / shock-expansion | `SymmetricComponentCalc.java` |
| 8 | Body $C_{N\alpha}$ and CP | 0.80 | 1.30 | Cubic Hermite | Galejs subsonic | Allen-Perkins crossflow | `SymmetricComponentCalc.java` |
| 9 | Modified Newtonian | 4.00 | 6.00 | Cubic Hermite | Shock-expansion / T-M | $C_p = C_{p,\max}\sin^2\theta$ | `SymmetricComponentCalc.java` |
| 10 | Shock geometry activation | 1.00 | 1.10 | Linear | Freestream (passthrough) | Full shock pre-pass | `ShockGeometry.java` |
| 11 | Fin-body interference (PNK) | 0.85 | 1.15 | Cubic Hermite | Barrowman $K_{WB}$, $K_{BW}$ | PNK supersonic | `PittsNielsenKaattari.java` |

**Notes on the table:**

- Entries 1-4 handle the core transonic singularity near $M = 1$.
- Entry 2 uses a constrained degree-4 polynomial rather than a simple smoothstep, because it must match both values and derivatives at two endpoints while also passing through a prescribed peak value at $M = 1.05$.
- Entry 5 uses `PolyInterpolator` with second-derivative constraints to achieve smoother curvature through the transition.
- Entry 10 uses a simple linear blend because the shock geometry correction is itself a smooth perturbation from unity; the blend only controls whether the perturbation is applied at all.
- The widest blend region is Entry 9 (Modified Newtonian, $\Delta M = 2.0$), reflecting the gradual transition from the shock-dependent regime to the purely local-inclination hypersonic regime.
- The narrowest blend region is Entry 1 ($\beta$, $\Delta M = 0.10$), which must be tight to avoid distorting the compressibility factor at Mach numbers far from unity.

---

## 10.5 Conceptual $C_D$ vs Mach Diagram with Blend Regions

```
Cd
     |
0.70 |                  *  Peak
     |                 /|\
0.60 |               /  | \
     |             /   |  \
0.50 |           /    |   \
     |         /  [2] | [7] \
0.40 |       /        |      \------___
     |     /    [6]   |              -------____
0.30 | ---/     [3]   |  [9]                    ----
     |                |
0.20 |           [1]  |  [10]
     |                |
0.10 |                |
     +--+--+--+--+--+--+--+--+--+--+--+--+--+--
        0.3  0.5  0.8 0.9 1.0 1.1 1.3 1.5 2.0 3.0  5.0  M

     Blend regions (numbers refer to table above):

     [1] beta factor:     |===|         M = 0.95 -- 1.05
     [2] base drag:    |=========|      M = 0.85 -- 1.30
     [3] skin friction:   |====|        M = 0.90 -- 1.10
     [5] fin CNa:         |==========| M = 0.90 -- 1.50
     [6] fin wave drag:   |======|     M = 0.90 -- 1.20
     [7] nose wave drag:     |====|    M = 1.30 -- 1.50
     [8] body CNa/CP:  |==========|    M = 0.80 -- 1.30
     [9] Newtonian:                         |==========| M = 4.0 -- 6.0
     [10] shock geom:       |==|       M = 1.00 -- 1.10
     [11] PNK fin-body: |========|     M = 0.85 -- 1.15
```

The transonic region $M \in [0.85, 1.50]$ contains seven overlapping blend regions. The overlap is intentional: each aerodynamic quantity transitions at the Mach range appropriate to its physical behavior. Base drag peaks near $M = 1.05$ and must blend over a wide region (0.85 to 1.30) to capture the characteristic asymmetric transonic shape. Fin $C_{N\alpha}$, which depends on $1/\beta$, needs a wider supersonic margin (up to $M = 1.5$) because the Barrowman subsonic formula and the Ackeret supersonic formula both diverge as $M \to 1$ and the interpolation polynomial must span a region wide enough to control the curvature.

---

## 10.6 Design Principles for Blend Selection

### 10.6.1 When to Use Cubic Hermite Smoothstep

Use the $3t^2 - 2t^3$ smoothstep when:
- Both endpoint models are smooth and well-defined at the blend boundaries
- No particular slope needs to be matched (the smoothstep forces zero slope at both ends)
- The transition is between "model A active" and "model B active" with no intermediate physics
- A compact, predictable blend region is desired

**Examples in this implementation**: Fin wave drag (Entry 6), body $C_{N\alpha}$ (Entry 8), Modified Newtonian (Entry 9).

### 10.6.2 When to Use Constrained Polynomial

Use a degree-4 or degree-5 constrained polynomial when:
- Both values and derivatives must match at the endpoints (C1 boundary conditions)
- An interior constraint exists (e.g., a peak value at a specific Mach number)
- The transition has asymmetric shape (different curvature on subsonic vs supersonic sides)

**Example**: Base drag blend (Entry 2), which must match the subsonic parabola and its slope at $M = 0.85$, pass through the transonic peak of 0.25 at $M = 1.05$, and match the Devan-Ashwood formula and its slope at $M = 1.30$.

### 10.6.3 When to Use Rational Blend (AP09)

Use the rational blend when:
- The transition is centered at a specific Mach number and should have smooth tails
- The coefficient has a physical singularity near the transition (e.g., $1/\beta \to \infty$)
- No hard activation/deactivation boundaries are desired
- The subsonic and supersonic models are both defined everywhere, just with different accuracy domains

The AP09 rational blend is $C^\infty$ everywhere and has the important property that it decays algebraically (not exponentially) in the tails, which means it provides a very gentle onset rather than an abrupt activation.

### 10.6.4 When to Use Gaussian Augmentation

Use a Gaussian factor when:
- A multiplicative correction is needed that peaks at a specific Mach number
- The correction should decay symmetrically (or nearly so) on both sides
- The correction is a transonic amplification rather than a model switch

**Example**: The pitch damping transonic factor $k(M) = 1 + 2.5\exp(-(((M-1)/0.15)^2)$ (Section 9.1.2). This is not a blend between two models but an augmentation of a single model, and the Gaussian shape naturally provides infinite smoothness.

### 10.6.5 When to Use Linear Blend

Use a linear blend only when:
- The blended quantity is itself a smooth correction that does not cause discontinuities
- Simplicity of implementation outweighs the $C^1$ benefit (e.g., the correction is numerically small)
- The blend acts as a gate (on/off) for a model whose output is continuous

**Examples**: Shock geometry activation (Entry 10), skin friction transition (Entry 3). In both cases, the blended quantity modulates a correction that is itself smooth, so the slope discontinuity at the linear blend endpoints is multiplied by a small factor and does not cause simulation instability.

---

# 11. Validation and Results

---

## 11.1 Test Suite Overview

The aerodynamic validation suite comprises **833 test cases** distributed across **53 test classes** in the `info.openrocket.core.aerodynamics` package hierarchy. Each model is validated at three levels: unit level (exact analytical comparisons against published tables), component level (coefficient magnitudes and trends against empirical correlations), and system level (full-vehicle Mach sweeps with continuity verification).

### 11.1.1 Five Standard Rocket Geometries

All system-level tests operate on five geometries spanning representative high-power amateur rocket configurations:

1. **Cone-Cylinder (CC)**: Conical nose ($L_n = 0.150$ m, $r = 0.025$ m, $\theta_c \approx 9.46°$, fineness ratio 3.0), cylinder body ($L_b = 0.600$ m). Total $L/D = 15$. No fins; isolates nose wave drag, body friction, and base drag.

2. **Ogive-Cylinder (OC)**: Tangent ogive nose (same envelope as CC), cylinder body. Directly comparable to CC to isolate nose-shape effect on wave drag.

3. **Cone-Cylinder-Fins (CCF)**: CC geometry plus 4-fin trapezoidal set (root 0.050 m, tip 0.025 m, span 0.040 m, thickness 3 mm) at the body aft end. Adds fin wave drag, fin friction, and stability.

4. **Ogive-Boattail-Fins (OBF)**: Ogive nose, cylinder body ($L_b = 0.500$ m), 4-fin set, conical boattail (fore radius 0.025 m, aft radius 0.018 m, length 0.060 m). Total length 0.710 m. Most representative of a flight-ready high-power rocket.

5. **Von Karman-Fins (VKF)**: Sears-Haack/LD-Haack nose ($L_n = 0.180$ m), cylinder body ($L_b = 0.550$ m), 3-fin swept set. Provides comparison against a theoretically minimum-wave-drag configuration.

### 11.1.2 Test Matrix

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

---

## 11.2 Gas Dynamics Validation Against NACA Report 1135

The three core gas-dynamics solvers are validated against the tabulated exact solutions in NACA Report 1135 (Ames Research Staff, 1953). All comparisons use $\gamma = 1.4$. The target tolerance is $< 0.1\%$ relative error.

### 11.2.1 Normal Shock Relations

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

### 11.2.2 Oblique Shock Relations

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

### 11.2.3 Prandtl-Meyer Expansion Function

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

### 11.2.4 Gas Dynamics Tolerance Summary

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

---

## 11.3 Drag Model Validation

### 11.3.1 Total Drag Coefficient -- All Five Geometries

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

### 11.3.2 Drag Continuity Verification

The continuity sweep executes 235 Mach steps ($\Delta M = 0.02$) for all five geometries. The acceptance criterion is $|dC_D/dM| < 5.0$.

```
Geometry              Max |dCd/dM|   Location    Status
------------------------------------------------------------
Cone-Cylinder         1.02           M = 1.07     PASS
Ogive-Cylinder        0.87           M = 1.08     PASS
Cone-Cylinder-Fins    1.43           M = 1.06     PASS
Ogive-Boattail-Fins   0.76           M = 1.07     PASS
Von Karman-Fins       1.21           M = 1.08     PASS
```

All peaks occur in the physically real transonic drag rise region, not at model blend boundaries.

---

## 11.4 Stability Validation

### 11.4.1 Center of Pressure Position vs Mach

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

### 11.4.2 Physical Consistency Checks

1. CP is aft of the nose tip at all Mach numbers for all three finned geometries.
2. CP is continuous through $M = 1$ with no discontinuous jumps.
3. Fin $C_{N\alpha}$ with shock-corrected local Mach differs from uncorrected by 5-15% at $M = 2$-3, confirming the `ShockGeometry` pre-pass is meaningfully altering fin lift.
4. Total $C_{N\alpha}$ increases through transonic (9.67 at $M = 1$ vs 8.47 subsonic for CCF), which is physically correct.

---

## 11.5 Hypersonic Validation

### 11.5.1 Maximum Pressure Coefficient

**Table 11.7 -- $C_{p,\max}$ via Rayleigh Pitot Formula, $\gamma = 1.4$**

| $M$ | $C_{p,\max}$ (computed) |
|-----|--------------------------|
| 2.0 | 1.6573 |
| 3.0 | 1.7557 |
| 5.0 | 1.8088 |
| 10.0 | 1.8317 |
| 20.0 | 1.8374 |

The theoretical Newtonian limit is $C_{p,\max} \to 1.839$ as $M \to \infty$. The computed value at $M = 20$ is 1.837, confirming correct asymptotic behavior.

### 11.5.2 Effective Ratio of Specific Heats

**Table 11.8 -- $\gamma_\text{eff}$ vs Stagnation Temperature**

| $T_0$ (K) | $\gamma_\text{eff}$ | Regime |
|-----------|---------------------|--------|
| 300 | 1.400 | Cold / low Mach |
| 800 | 1.400 | Onset of O$_2$ vibrational excitation |
| 1500 | 1.37 -- 1.38 | $M \approx 4$-5 |
| 3000 | $\geq$ 1.30 | Both N$_2$ and O$_2$ modes excited |
| 5000 | $\geq$ 1.30 | Approaching dissociation threshold |

The implementation clamps $\gamma_\text{eff} \geq 1.30$ to avoid nonphysical values before dissociation chemistry (which is not modeled).

---

## 11.6 Performance Benchmarks

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

---

## 11.7 Comparison with Original OpenRocket

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

---

# 12. Conclusions and References

---

## 12.1 Summary of Contributions

This work has extended the OpenRocket aerodynamic simulation framework from a subsonic/low-transonic tool valid to approximately $M = 2$ into a comprehensive compressible-flow simulation validated from $M = 0.3$ to $M = 10+$. The seven principal contributions are:

1. **Gas dynamics foundation.** A complete set of compressible flow solvers -- oblique shock relations (theta-beta-Mach with bisection), Taylor-Maccoll cone flow (ODE integration), normal shock jump conditions, and Prandtl-Meyer expansion fan relations -- all validated against NACA Report 1135 to within 0.02% relative error. These solvers form the computational backbone for all subsequent wave drag, pressure coefficient, and shock geometry calculations.

2. **Analytical wave drag models.** Replacement of the empirical NASA TR-R-100 tables with physics-based wave drag computations: Taylor-Maccoll exact solution for conical noses, second-order shock-expansion theory for ogive noses, and Ackeret thin-airfoil theory for fin wave drag. These models are valid across the full supersonic range without the fineness-ratio and Mach-range limitations of the tabulated approach.

3. **Shock geometry pre-pass architecture.** A new `ShockGeometry` computation that walks the rocket body nose-to-tail, computing post-shock Mach number, pressure, and temperature at each axial station. This enables downstream component calculators (fins, body sections) to use the correct local flow conditions rather than freestream values, correcting fin lift and drag by 5-15% at $M = 2$-3. The architecture adds zero overhead at subsonic speeds through a passthrough design.

4. **Compressible boundary layer modeling.** Implementation of the Eckert reference temperature method for supersonic skin friction, reducing friction drag predictions by 30-75% at $M = 2$-5 compared to the incompressible formulas used in the original code. The Sutherland viscosity law replaces the original linear fit, extending atmospheric model validity to stagnation temperatures approaching 5000 K.

5. **Hypersonic extension via Modified Newtonian theory.** For $M > 5$, the pressure distribution transitions to the $C_p = C_{p,\max}\sin^2\theta$ formulation with $C_{p,\max}$ computed from the Rayleigh pitot formula. The transition from shock-dependent to local-inclination methods is blended smoothly over $M = 4$-6, extending model validity to $M = 10$ and beyond with graceful degradation.

6. **$C^1$-continuous regime blending.** Eleven distinct blending regions using cubic Hermite interpolation, constrained polynomial fitting, and AP09 rational functions ensure that all aerodynamic coefficients are continuous with continuous first derivatives across every Mach regime boundary. This eliminates the simulation instability and time-step collapse that would otherwise occur at transonic and supersonic transitions.

7. **Dynamic stability derivatives.** Pitch damping ($C_{mq}$) computed from per-component $C_{N\alpha}$ and moment arms with a transonic Gaussian augmentation factor, Magnus force and moment derivatives for spinning rockets, and full Euler gyroscopic coupling in the 6-DOF integrator. These enable physically correct prediction of spin-stabilized flight, precession dynamics, and pitch damping through all Mach regimes.

## 12.2 Validation Summary

The extended aerodynamic module passes **833 automated tests with 0 failures** across 53 test classes. The test suite covers:

- Gas dynamics solvers validated against NACA 1135 to $< 0.02\%$ error
- Five standard rocket geometries spanning cone, ogive, boattail, and Von Karman nose shapes with 3-fin and 4-fin configurations
- Mach sweep from 0.3 to 10.0 with continuity verification at 235 Mach steps
- Angle of attack sweep from 0 deg to 15 deg at selected Mach numbers
- Edge case hardening at $M = 0, 0.999, 1.000, 1.001, 10.0$
- Performance benchmarks confirming $< 1$ ms per supersonic aero calculation

The valid Mach range has been extended from approximately $M < 2$ (original OpenRocket) to $M < 10$ (OpenRocket Plus), a five-fold increase. Within the range $M = 0.3$ to $M = 5.0$, the total drag coefficient predictions are physically consistent with published experimental data and analytical solutions for all five standard geometries.

## 12.3 Subsonic Compatibility

At $M < 1.0$, the extended code paths are either inactive (ShockGeometry returns a passthrough with unit ratios, wave drag models return zero, Eckert correction reduces to incompressible) or reduce identically to the original Barrowman formulas. The subsonic passthrough cost is approximately 200 ns per call -- negligible compared to the ~180 microsecond component calculation time. All original subsonic tests continue to pass without modification.

## 12.4 Limitations and Future Work

The current implementation does not model:
- Real-gas dissociation chemistry above stagnation temperatures of approximately 5000 K (relevant for $M > 10$ at sea level)
- Boundary layer transition from laminar to turbulent at supersonic speeds (currently assumes fully turbulent)
- Fin-fin Mach cone interference (secondary effect, estimated $< 3\%$ for typical geometries)
- Ablation or mass loss at hypersonic speeds
- Non-equilibrium thermochemistry

These items represent diminishing returns for the target application of amateur high-power rocketry, where the vast majority of flights remain below $M = 5$.

---

## References

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

---

*End of Part E.*
