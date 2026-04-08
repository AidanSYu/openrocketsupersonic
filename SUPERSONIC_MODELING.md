# Supersonic & Hypersonic Aerodynamic Modeling in OpenRocket Plus

## Overview

OpenRocket Plus extends the classical Barrowman aerodynamic calculator with physics-based models for supersonic (M > 1) and hypersonic (M > 5) flight. The original OpenRocket implementation uses subsonic Barrowman theory augmented with NASA TR-R-100 empirical tables that max out around Mach 2-3.6 depending on nose shape. This extension replaces those limitations with analytical solutions valid to Mach 10+.

**Design philosophy:** Each supersonic model integrates into the existing calculator architecture through smooth C1-continuous blending, so subsonic results are unchanged while supersonic accuracy is dramatically improved. At subsonic speeds, the new code paths are either inactive or reduce to the original formulas.

---

## Architecture

```
BarrowmanCalculator (orchestrator)
  |
  +-- ShockGeometry.compute()          [Phase 3b: pre-pass]
  |     Uses: ObliqueShockSolver, PrandtlMeyerExpansion, NormalShockRelations
  |     Output: local Mach, pressure, temperature at each axial station
  |
  +-- BarrowmanStabilityCalculator     [Phase 3a: supersonic stability]
  |     |-- setShockGeometry(sg)       [passes to component calcs]
  |     |-- SymmetricComponentCalc     [body CNa/CP with Mach correction]
  |     +-- FinSetCalc                 [fin CNa with local flow correction]
  |
  +-- BarrowmanDragCalculator          [Phase 2: drag model overhaul]
        |-- Friction: Eckert reference temperature method   [2d]
        |-- Pressure: analytical wave drag                  [2a]
        |-- Base: Devan-Ashwood correlation                 [2b]
        +-- Fin wave drag: Ackeret thin-airfoil theory      [2c]
```

### Key Flow

1. `BarrowmanCalculator.getAerodynamicForces()` is called with flight conditions
2. If M > 1.0, `ShockGeometry.compute()` walks the body nose-to-tail, computing post-shock flow at each station
3. The shock geometry is passed to component calculators alongside flight conditions
4. Each component reads local post-shock conditions at its axial position
5. Stability and drag are computed using the corrected local conditions
6. At subsonic speeds, `ShockGeometry` is an inert passthrough — zero overhead

---

## Model Details

### 1. Atmospheric Model (`AtmosphericConditions.java`)

| Property | Model | Valid Range | Reference |
|----------|-------|-------------|-----------|
| Speed of sound | `a = sqrt(gamma * R * T)` | All T where air is ideal gas | US Standard Atmosphere 1976 |
| Dynamic viscosity | Sutherland's law: `mu = mu_ref * (T/T_ref)^1.5 * (T_ref + S)/(T + S)` | 100-1900 K | Sutherland 1893 |
| Effective gamma | Einstein vibrational model for N2/O2 | Up to ~5000 K stagnation | Anderson Ch. 16 |

**Effective gamma** accounts for vibrational excitation of N2 (theta_v = 3371 K) and O2 (theta_v = 2256 K) at high stagnation temperatures. Below 800 K, gamma = 1.4 (diatomic ideal gas). Above 800 K, gamma decreases toward 1.3 as vibrational modes absorb energy. This affects shock relations and all pressure calculations at M > 5.

### 2. Compressibility Factor Beta (`FlightConditions.java`)

| Regime | Formula | Notes |
|--------|---------|-------|
| Subsonic (M < 0.95) | `beta = sqrt(1 - M^2)` | Prandtl-Glauert |
| Supersonic (M > 1.05) | `beta = sqrt(M^2 - 1)` | Ackeret |
| Transonic (M 0.95-1.05) | Cubic Hermite spline | C1-continuous, positive floor |

The old implementation clamped beta at `MIN_BETA = 0.25`, which distorted all supersonic calculations (beta should be ~4.9 at M=5). The new smooth blending through M=1 eliminates this distortion and prevents the zero crossing that caused numerical instability.

### 3. Shock Relations (`aerodynamics/shocks/` package)

#### 3a. Normal Shock Relations (`NormalShockRelations.java`)

Exact analytical jump conditions across a normal shock:
- Downstream Mach: `M2^2 = [(gamma-1)*M1^2 + 2] / [2*gamma*M1^2 - (gamma-1)]`
- Pressure ratio: `p2/p1 = 1 + 2*gamma/(gamma+1) * (M1^2 - 1)`
- Temperature ratio: `T2/T1 = [1 + 2*gamma/(gamma+1)*(M1^2-1)] * [2 + (gamma-1)*M1^2] / [(gamma+1)^2 * M1^2 / (2*(gamma-1))]`

Validated against NACA Report 1135 tables to < 0.1%.

#### 3b. Oblique Shock Solver (`ObliqueShockSolver.java`)

Solves the theta-beta-Mach relation for oblique shocks:
- **2D wedge shocks**: iterative solution of `tan(theta) = 2*cot(beta) * (M1^2*sin^2(beta) - 1) / (M1^2*(gamma + cos(2*beta)) + 2)`
- **Cone shocks (Taylor-Maccoll)**: iterative cone-flow solution using the Taylor-Maccoll ODE
- **Pressure coefficients**: exact surface Cp from post-shock conditions
- **Detached shock handling**: falls back to normal shock when the deflection angle exceeds the maximum for attached shock

#### 3c. Prandtl-Meyer Expansion (`PrandtlMeyerExpansion.java`)

Isentropic expansion fan relations:
- `nu(M) = sqrt((gamma+1)/(gamma-1)) * atan(sqrt((gamma-1)/(gamma+1)*(M^2-1))) - atan(sqrt(M^2-1))`
- Downstream Mach from turning angle: `nu(M2) = nu(M1) + delta`
- Pressure and temperature ratios via isentropic relations

### 4. Wave Drag — Nose/Body (`SymmetricComponentCalc.java`)

#### 4a. Taylor-Maccoll Cone Solution (Phase 2A)

For conical nose cones, the wave drag is computed from the exact Taylor-Maccoll solution via `ObliqueShockSolver.conePressureCoefficient()`. This gives the surface pressure coefficient directly, which equals the drag coefficient for a cone at zero AoA.

**Valid range:** Any Mach where the cone shock is attached (cone half-angle < max deflection angle for the given Mach).

#### 4b. Shock-Expansion Method (Phase 2A)

For ogive and other non-conical nose shapes, a strip-integration approach:

1. Compute the initial shock at the nose tip (Taylor-Maccoll cone approximation using the local tip half-angle)
2. March downstream along the surface in N = 100 strips
3. At each strip, compute the turning angle from the previous station
4. Positive turning (surface turns away from flow): apply Prandtl-Meyer expansion
5. Negative turning (surface turns into flow): apply oblique shock compression
6. Integrate the surface pressure distribution: `Cd = 2 * integral(Cp * r * dr) / (R_aft^2 - R_fore^2)`

**Valid range:** M > 1.0, any nose shape with `foreRadius < aftRadius`.

#### 4c. Empirical Tables (NASA TR-R-100)

Retained for nose shapes without clean analytical solutions (power-law, parabolic, Haack series, ellipsoid). The tables provide pressure drag coefficients at fineness ratio 3, extrapolated to other fineness ratios using the relation:

```
Cd(f) = Cd_stagnation * (Cd_table / Cd_stagnation)^(log(f+1) / log(4))
```

Extended to higher Mach by shock-expansion where the tip supports an attached shock.

#### 4d. Transonic Drag Rise (Phase 2E)

Below the drag divergence Mach (Mdd), wave drag is zero. Above Mdd, a C1-continuous cubic Hermite polynomial connects zero drag at Mdd to the first empirical/analytical data point.

Mdd is estimated from the nose tip geometry:
```
Mdd = 0.95 - 0.15 * sin(theta_tip)^0.4
```
Calibrated against TR-R-100 onset data: Von Karman ≈ M 0.92, x=3/4 Power ≈ M 0.83.

#### 4e. Modified Newtonian Theory (Phase 4A)

For hypersonic flow (M > 5), the pressure distribution is approximated by:
```
Cp = Cp_max * sin^2(theta)
```
where `Cp_max` is computed from the Rayleigh pitot tube formula:

```
Cp_max = (2 / (gamma * M^2)) * [((gamma+1)^2 * M^2 / (4*gamma*M^2 - 2*(gamma-1)))^(gamma/(gamma-1))
         * (1 - gamma + 2*gamma*M^2) / (gamma+1) - 1]
```

Blended with shock-expansion results through M 4-6 using smoothstep interpolation. At M > 5, uses effective gamma from the real-gas model.

**Reference:** Lees (1955); Anderson, "Hypersonic and High-Temperature Gas Dynamics", Ch. 3.

### 5. Base Drag (`BarrowmanDragCalculator.java`)

| Regime | Model | Reference |
|--------|-------|-----------|
| Subsonic (M < 0.85) | `Cd_base = 0.12 + 0.13*M^2` | Hoerner Ch. 3 |
| Transonic (M 0.85-1.3) | C1 degree-4 polynomial, peak at M=1.05 (Cd=0.25) | Fitted to experimental data |
| Supersonic (M > 1.3) | `Cd_base = 0.064 + 0.186/M^2` (Devan-Ashwood) | NASA TN D-721 |

The Devan-Ashwood correlation correctly asymptotes to a nonzero constant (~0.064) at high Mach, matching experimental data for turbulent cylindrical afterbodies. The transonic polynomial matches both value and derivative at both boundaries (C1-continuous).

**Boattail correction:** For components that taper to a smaller aft radius, the converging flow reduces base drag. The correction factor depends on boattail angle (full benefit < 12 deg, zero benefit > 20 deg) and Mach number (expansion fan effects enhance reduction at supersonic speeds).

### 6. Skin Friction (`BarrowmanDragCalculator.java`)

| Regime | Model | Reference |
|--------|-------|-----------|
| Subsonic (M < 0.9) | Incompressible Cf with empirical Mach correction | Original OpenRocket |
| Transonic (M 0.9-1.1) | Linear blend | — |
| Supersonic (M > 1.1) | Eckert reference temperature method | Eckert 1955 |

**Eckert method:** At high Mach, the boundary layer is much hotter than freestream. The reference temperature `T*` accounts for this:

```
T_wall = T_e * (1 + r * (gamma-1)/2 * M^2)     [adiabatic wall, r = Pr^(1/3)]
T* = T_e * (1 + 0.032*M^2 + 0.58*(T_w/T_e - 1))
```

Reynolds number and viscosity are evaluated at `T*` using Sutherland's law, then the Cf is scaled by `T_e/T*`. This gives ~35% friction reduction at M=3 and ~55% at M=5, matching published compressible boundary layer data.

### 7. Fin Wave Drag (`FinSetCalc.java`)

Ackeret (1925) supersonic thin-airfoil wave drag:
```
Cdw = 4 * tau^2 / sqrt(M^2 - 1)
```
where `tau = thickness/chord` is the fin thickness ratio. Applied to AIRFOIL and ROUNDED cross-sections; SQUARE fins use the stagnation drag term instead.

Blended C1-continuously from zero at M=0.9 to full Ackeret at M=1.2 using cubic Hermite splines. Leading-edge sweep correction: `Cdw *= cos^2(Lambda_LE)`.

### 8. Shock Geometry Pre-Pass (`ShockGeometry.java`)

At supersonic speeds, computes local flow conditions along the entire rocket body:

1. **Nose shock:** oblique shock from tip half-angle (Taylor-Maccoll for cone tip, or wedge approximation)
2. **Surface marching:** tracks local Mach/pressure/temperature as the surface angle changes
3. **Expansion fans:** where the surface turns away from flow (e.g., shoulder of nose-to-body junction)
4. **Compression shocks:** where the surface turns into flow (e.g., transition that increases radius)
5. **Dynamic pressure ratio:** `q_local/q_free = (p_local/p_free) * (M_local/M_free)^2`

Component calculators query the shock geometry at their axial position via `getConditionsAt(x)`, which returns interpolated local conditions.

### 9. Supersonic Stability Corrections

#### Body CNa/CP (Phase 3a, `SymmetricComponentCalc.java`)

At supersonic speeds:
- **Body lift coefficient** increases modestly (K from 1.1 to max 1.3) based on crossflow drag coefficient enhancement
- **CP shifts aft** toward the planform centroid, consistent with the crossflow analogy (Allen & Perkins, NACA Report 1048)
- Both transitions are C1-continuous through M 0.8-1.3 using smoothstep blending

#### Fin CNa with Local Flow (Phase 3c, `FinSetCalc.java`)

Behind the body shock, the local Mach is reduced and local pressure is increased. The existing K1/K2/K3 supersonic fin CNa computation receives the corrected local Mach from `ShockGeometry`, and the normal force is scaled by the local dynamic pressure ratio. This typically reduces fin CNa by 5-15% at M=2-3 compared to uncorrected freestream values.

---

## Mach Regime Transitions

All regime transitions use C1-continuous blending to prevent simulation instability. Discontinuities in Cd or CNa cause the simulation stepper to oscillate or diverge near M=1.

| Transition | Region | Method |
|-----------|--------|--------|
| Beta factor | M 0.95-1.05 | Cubic Hermite spline matching value+slope at both endpoints |
| Skin friction | M 0.9-1.1 | Linear blend between subsonic correction and Eckert method |
| Base drag | M 0.85-1.3 | Degree-4 polynomial matching value+slope at boundaries, peak at M=1.05 |
| Fin wave drag | M 0.9-1.2 | Cubic Hermite from zero to Ackeret formula |
| Fin CNa | M 0.9-1.5 | Polynomial interpolation between subsonic and supersonic K1/K2/K3 |
| Nose wave drag | M 1.3-1.5 | Smoothstep blend from empirical tables to analytical |
| Body CNa/CP | M 0.8-1.3 | Smoothstep from Barrowman to supersonic correction |
| Newtonian theory | M 4.0-6.0 | Smoothstep blend from shock-expansion to Modified Newtonian |

---

## Model Validity Ranges

| Model | Valid Range | Confidence | Degradation Mode |
|-------|------------|------------|------------------|
| Barrowman CNa/CP | M < 0.8 | High | Original theory, well-validated |
| Supersonic fin CNa (K1/K2/K3) | M 1.5-5.0 | High | Linear supersonic theory |
| Taylor-Maccoll cone drag | M 1.0-10+ | High | Exact analytical solution |
| Shock-expansion wave drag | M 1.0-10+ | Moderate-High | Second-order accuracy |
| Modified Newtonian | M 5-10+ | Moderate | Approximation improves with M |
| Eckert skin friction | M 1.1-10+ | High | Validated against flat plate data |
| Devan-Ashwood base drag | M 1.3-10+ | Moderate | Empirical correlation |
| Shock geometry pre-pass | M 1.0-10+ | Moderate | First-order marching |
| Body CNa supersonic | M 1.3-5.0 | Moderate | Crossflow analogy |
| Effective gamma | Stagnation T 800-5000 K | Moderate | Does not include dissociation |

### User Warnings

| Warning | Trigger | Message |
|---------|---------|---------|
| HYPERSONIC | M > 5 | "Aerodynamic models use Modified Newtonian approximation above Mach 5. Accuracy is reduced." |
| HYPERSONIC_EXTREME | M > 10 | "Aerodynamic models have very limited validity above Mach 10. Results should be treated as rough estimates." |
| HIGH_AOA | AoA > 15 deg | "Body lift and CP calculations are less accurate at high angles of attack (> 15 deg)." |

---

## Validation Summary

### Phase 1: Foundation
- Shock solver matches NACA 1135 to < 0.1% for M 1.2-10, deflection angles 5-40 deg
- Atmosphere model matches US Standard Atmosphere 1976 to < 0.5% for alt 0-80 km
- Beta is continuous through M=1 and asymptotes to sqrt(M^2-1) by M=1.3

### Phase 2: Drag Models
- Cone pressure drag matches Taylor-Maccoll exact solution to < 1%
- No Cd discontinuities across regime transitions (dCd/dM bounded)
- Skin friction at M=3 reduced ~35% vs incompressible (matches published data)
- Base drag trend and magnitude match Hoerner and NASA TN D-721

### Phase 3: Stability & Shock Interaction
- CP moves aft at supersonic speeds as predicted by crossflow theory
- Body CNa increases with Mach per slender body theory
- Fin CNa with shock-corrected local Mach differs from uncorrected by 5-15% at M=2-3
- No stability discontinuities at regime transitions

### Phase 4: Hypersonic Extensions
- Modified Newtonian Cp_max matches Rayleigh pitot values to < 1% for M 2-50
- Effective gamma matches vibrational partition function to < 1%
- No NaN, Infinity, or divergence at M = 0, 0.999, 1.0, 1.001, 10.0, AoA = 0-20 deg
- All 5 standard rocket geometries produce valid results at M=10

### Phase 5: Integration Validation (verified 2026-04-08, 534 tests, 0 failures)

**5a. Comprehensive Validation Suite (60 tests):**
- Cone shock angles match NACA 1135 analytical solutions
- Normal shock downstream Mach/pressure match exact jump conditions
- Prandtl-Meyer function values match tabulated data
- Cp_max asymptotes correctly at high Mach (Rayleigh pitot)
- Effective gamma physics: decreases with temperature, clamps at 1.3 floor
- Total Cd positive and finite for all 5 geometries, M 0.3-10.0
- CNa finite and physically reasonable across full Mach range
- CP within rocket bounds at all Mach points
- dCd/dM bounded at all Mach points (no discontinuities in any geometry)
- Zero AoA produces CN ≈ 0; CN increases monotonically with AoA
- Ogive pressure drag < cone pressure drag at M 1.5-3.0 (shock-expansion regime)
- Fins increase both drag and CNa (physics verified)
- Boattail reduces total drag (physics verified)
- Von Karman drag ≤ cone drag (minimum-drag body verified)
- Skin friction decreases with Mach at supersonic speeds (Eckert method verified)
- Base drag asymptotes to nonzero constant at high Mach (Devan-Ashwood verified)
- ShockGeometry: subsonic returns non-supersonic, supersonic returns valid stations, extreme Mach handled
- Drag breakdown: friction + pressure + base sums to total; friction dominates subsonic, pressure dominates supersonic
- Beta factor: continuous through M=1, matches exact formulas at M=0.95 and M=1.05

**5b. Performance Benchmarking (5 tests):**
- Single aero calculation at any Mach: < 50 ms (with reused calculator, post-warmup)
- Supersonic M=3 absolute speed: < 100 ms per calculation
- Throughput: 1000 calculations at M=3 in < 30 seconds
- Subsonic ShockGeometry: < 10 μs (effectively zero overhead)
- All 5 geometries × 97 Mach steps = 485 calculations without exceptions

**5c. End-to-End Simulation (8 tests):**
- Estes Alpha III full trajectory: apogee, max velocity, max acceleration all finite and physically reasonable
- No NaN/Inf in time-series data (altitude, velocity, acceleration, Mach)
- Regime transition continuity: simulated flight profile M 0.3→3.0→0.3 in 120 steps, < 3 Cd jumps per geometry
- CP within rocket bounds for all 3 finned geometries through M 0.3-10.0
- Stability margin positive at all tested Mach points for finned rockets
- Warning system: no HYPERSONIC at M=3, HYPERSONIC at M=6, HYPERSONIC_EXTREME at M=12

---

## Phase 6: Accuracy Improvements — Closing the RASAero II Gap

The Phases 1–5 models establish correct supersonic physics. Phase 6 targets the remaining accuracy gaps versus RASAero II and incorporates state-of-the-art methods to push fidelity beyond what either code currently achieves. Organized by priority: gap-closing fixes first, then SOTA enhancements.

### Architecture Impact

```
BarrowmanCalculator (orchestrator)
  |
  +-- ShockGeometry.compute()              [existing Phase 3b pre-pass]
  |
  +-- BarrowmanStabilityCalculator
  |     |-- SymmetricComponentCalc         [6a: Jorgensen crossflow Cd_c]
  |     |-- FinSetCalc                     [6f: Pitts-Nielsen-Kaattari K_WB/K_BW]
  |     +-- FinSetCalc                     [6h: ESDU transonic similarity]
  |
  +-- BarrowmanDragCalculator
  |     |-- Friction: Eckert (unchanged)
  |     |-- Pressure: analytical wave drag [6c: Dahlem-Buck for non-conical noses]
  |     |-- Pressure: transonic            [6e: Whitcomb area rule]
  |     |-- Base: Devan-Ashwood            [6b: power-on correction, 6g: Chapman-Korst upgrade]
  |     +-- Fin wave drag: Ackeret         [6h: ESDU transonic similarity]
  |
  +-- Regime blending                      [6d: AP09 rational functions]
```

---

### 6a. Jorgensen Supersonic Crossflow Drag Coefficient

**Priority:** Easy win — highest impact-to-effort ratio.

**Problem:** Body lift at M > 2 uses a constant crossflow drag coefficient Cd_c = 1.2 (appropriate for subsonic/low-supersonic crossflow). At supersonic crossflow Mach numbers the actual Cd_c rises to 1.8–2.0, causing body normal force to be underpredicted by 10–30%.

**Method:**
- Implement a Mach-dependent crossflow drag coefficient lookup table based on Jorgensen (NASA TR R-829, 1977)
- The crossflow Mach number is `M_crossflow = M * sin(alpha)`, where alpha is the angle of attack
- For circular cylinders in crossflow:

| M_crossflow | Cd_c |
|-------------|------|
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

- Interpolate linearly between table points
- At M_crossflow > 5, clamp to 2.0 (Newtonian limit for cylinder)

**Files to modify:**
- `SymmetricComponentCalc.java` — `calculateNonaxialForces()`: replace constant Cd_c with table lookup using `M * sin(alpha)`

**Validation:**
- Body CNa at M=3, alpha=5° should increase ~15–25% vs current constant Cd_c
- At subsonic crossflow (low Mach or low AoA), results should be unchanged (Cd_c = 1.2)
- Compare against Allen-Perkins crossflow theory predictions from NACA Report 1048

---

### 6b. Power-On Base Drag Correction (NASA SP-8050)

**Priority:** Easy win — large drag error during motor burn.

**Problem:** During motor burn, the exhaust plume fills the rocket base region, raising base pressure and reducing base drag to near zero. The current model applies full unpowered base drag at all times, overpredicting total drag by 20–50% during the burn phase.

**Method:**
- Implement the NASA SP-8050 power-on base pressure correlation
- The correction factor depends on the ratio of nozzle exit pressure to freestream pressure and the ratio of nozzle exit area to base area:

```
p_b/p_inf = f(p_e/p_inf, A_e/A_b)
```

- Simplified model for solid rocket motors (typical amateur/HPR case):
  1. During burn (thrust > 0), compute the jet pressure ratio: `JPR = p_exit / p_freestream`
  2. Compute the area ratio: `AR = A_nozzle_exit / A_base`
  3. Base drag reduction factor:

```
If AR >= 0.8:   k_base = 0.0   (plume fills base entirely)
If AR >= 0.4:   k_base = 0.2 * (0.8 - AR) / 0.4
If AR >= 0.1:   k_base = 0.2 + 0.6 * (0.4 - AR) / 0.3
If AR <  0.1:   k_base = 0.8 + 0.2 * (0.1 - AR) / 0.1
```

  4. At high JPR (underexpanded jet), further reduce: `k_base *= max(0, 1 - 0.5 * log10(JPR))` for JPR > 1
  5. Apply: `Cd_base_powered = k_base * Cd_base_unpowered`

- At motor burnout, ramp base drag back to unpowered value over 0.1 seconds (C1 transition) to avoid a step change that could destabilize the simulation

**Files to modify:**
- `BarrowmanDragCalculator.java` — base drag computation: check if motor is firing, apply reduction factor
- Need to access motor thrust and nozzle geometry from `FlightConfiguration` / `MotorConfiguration`
- May need to add nozzle exit area and exit pressure to motor data model if not already available

**Data requirements:**
- Nozzle exit diameter: available from motor definition or estimated from motor diameter
- Exit pressure: can be estimated from chamber pressure and expansion ratio, or defaulted to atmospheric for a first approximation
- If exact motor data is unavailable, use a default `k_base = 0.15` during burn (85% reduction) as a reasonable estimate for typical HPR motors

**Validation:**
- During motor burn, total Cd should drop noticeably (base drag is typically 15–30% of total)
- At burnout, Cd should smoothly return to unpowered value
- Compare simulated apogee against flight data for well-documented flights where motor burn extends into supersonic regime

---

### 6c. Dahlem-Buck Nose Wave Drag

**Priority:** Easy win — extends nose drag accuracy for non-conical shapes.

**Problem:** The current implementation uses Taylor-Maccoll (exact for cones) and shock-expansion (good for ogives) but falls back to limited TR-R-100 tables for power-law, parabolic, Haack, and ellipsoidal noses. The tables cover limited fineness ratios and max out at M 2–3.6.

**Method:**
- Implement the Dahlem-Buck semi-empirical wave drag method, which extends the cone result to arbitrary nose shapes using shape correction factors
- The method computes wave drag as:

```
Cd_wave = Cd_cone(M, theta_equiv) * K_shape(nose_type, fineness_ratio)
```

- Where `theta_equiv` is an equivalent cone half-angle derived from the nose geometry:
  - For power-law noses (y = r * (x/L)^n): `theta_equiv = atan(r / (n * L))`  (slope at tip for pointed shapes, or use average slope)
  - For Haack series: `theta_equiv` from the aft-end slope
  - For parabolic: `theta_equiv` from the average slope

- Shape correction factors K_shape (from Dahlem-Buck, validated against wind tunnel data):

| Nose Type | K_shape | Notes |
|-----------|---------|-------|
| Cone | 1.000 | Reference shape |
| 3/4 Power | 0.72–0.80 | Lower drag than cone (favorable pressure gradient) |
| 1/2 Power (parabolic) | 0.65–0.75 | Blunter tip, moderate drag |
| Ogive (tangent) | 0.80–0.90 | Close to cone |
| Von Karman (LD Haack) | 0.55–0.65 | Near-minimum drag body |
| Haack (LV) | 0.60–0.70 | Minimum drag for given volume |
| Ellipsoid | 0.90–1.10 | Blunt; uses Modified Newtonian at high M |

- K_shape has mild Mach dependence (5–10% variation M 1.5–5.0); use linear interpolation vs Mach
- For fineness ratio correction, use the relation:

```
Cd_wave(f) = Cd_wave(f=3) * (3/f)^1.6
```

  This captures the strong sensitivity to nose slenderness more accurately than the current log-based extrapolation.

**Files to modify:**
- `SymmetricComponentCalc.java` — `calculatePressureDrag()`: add Dahlem-Buck path for nose types where shape factors are available
- Retain shock-expansion as the primary method for ogives and cones; Dahlem-Buck serves as the primary method for power-law, Haack, ellipsoid noses, and as a cross-check for all shapes
- New utility class or lookup table: `DahlemBuckShapeFactors.java` in `aerodynamics/barrowman/`

**Validation:**
- Von Karman nose drag < tangent ogive drag < cone drag (at same fineness ratio and Mach)
- Cd_wave for cone matches Taylor-Maccoll to within 2% (K_shape = 1.0)
- Compare against TR-R-100 data where available; Dahlem-Buck should agree within 5%
- At M > 3.6 (beyond TR-R-100 range), Dahlem-Buck provides continued coverage

---

### 6d. AP09 Rational Function Regime Blending

**Priority:** Easy win — improves all regime transitions.

**Problem:** Current regime blending uses cubic Hermite splines and polynomial interpolation. These ensure C1 continuity but don't capture the correct asymptotic behavior in subsonic/supersonic limits. A rational function of the form `f(M) = (a + bM²)/(1 + cM²)` naturally captures the physics: Prandtl-Glauert `1/sqrt(1-M²)` behavior subsonically and `1/sqrt(M²-1)` supersonically.

**Method:**
- Replace polynomial blending with AP09-style rational function interpolation where appropriate
- The general form for a coefficient that transitions between subsonic value `f_sub` and supersonic value `f_sup`:

```
f(M) = f_sub * g(M) + f_sup * (1 - g(M))

where g(M) = (1 + c₁*M²) / (1 + c₂*M² + c₃*M⁴)
```

- The constants c₁, c₂, c₃ are chosen to match:
  1. `g(0) = 1` (purely subsonic at M=0)
  2. `g → 0` as `M → ∞` (purely supersonic at high Mach)
  3. `g(M_blend) = 0.5` at the desired blend center (e.g., M=1.0)
  4. `dg/dM` matches desired slope at blend center

- Specific applications:

| Transition | Current Method | AP09 Replacement |
|-----------|---------------|-----------------|
| Fin CNa (M 0.9–1.5) | Polynomial interpolation | `CNa = CNa_sub * g(M) + CNa_sup * (1-g(M))` with g tuned for M 0.9–1.5 |
| Body CNa/CP (M 0.8–1.3) | Smoothstep | Rational blend with correct Prandtl-Glauert divergence |
| Nose wave drag (M 1.3–1.5) | Smoothstep | Rational blend matching transonic rise shape |
| Newtonian blend (M 4–6) | Smoothstep | Retain smoothstep (rational not needed at high M) |

- Do NOT replace all blending — only where the rational function captures physics better:
  - Keep cubic Hermite for beta factor (M 0.95–1.05) — the singularity demands tight control
  - Keep linear blend for skin friction (M 0.9–1.1) — simple and adequate
  - Keep degree-4 polynomial for base drag transonic peak — the peak shape is empirical

**Files to modify:**
- New utility: `RationalBlend.java` in `aerodynamics/` with static methods for the blending function and its derivative
- `FinSetCalc.java` — fin CNa transonic interpolation
- `SymmetricComponentCalc.java` — body CNa/CP and nose wave drag blending
- Update existing blending to call `RationalBlend` where applicable

**Validation:**
- All existing regression tests must still pass (same values at endpoints, smoother through transitions)
- dCd/dM and dCNa/dM should be smoother through transonic — verify by checking second derivative is bounded
- Plot Cd vs Mach curves with both old polynomial and new rational blending to confirm improved shape

---

### 6e. Transonic Area Rule Wave Drag (Whitcomb / ESDU 78019)

**Priority:** Medium effort, highest impact — the #1 practical error source.

**Problem:** The current transonic drag model uses component-level TR-R-100 tables and polynomial blending. This misses fin-body interference wave drag: when fins are present, the abrupt cross-sectional area increase at the fin station generates significant wave drag near M=1 that is NOT captured by summing nose wave drag + fin wave drag independently. This is typically the largest single error source at M 0.9–1.3.

**Method:**
- Implement the Whitcomb transonic area rule: wave drag depends on the axial distribution of total cross-sectional area S(x), including body, fins, launch lugs, and any other protuberances
- The supersonic area rule generalizes this to oblique cutting planes at the Mach angle

**Step 1: Cross-sectional area distribution S(x)**
- Walk the rocket nose-to-tail, computing total cross-sectional area at N=200 axial stations
- Body contribution: `pi * r(x)²` from the `SymmetricComponent` radius profile
- Fin contribution at each station: fin chord × thickness × number of fins (projected into the cutting plane)
- Launch lug, rail button contributions: small but include for completeness
- Store as array `S[0..N-1]` with corresponding `x[0..N-1]`

**Step 2: Sears-Haack equivalent body**
- Compute the second derivative `S''(x)` of the area distribution (central differences)
- The wave drag coefficient is (from Whitcomb/ESDU 78019):

```
Cd_wave_area = -(1 / (2*pi*S_ref)) * integral_0^L integral_0^L S''(x1) * S''(x2) * |x1-x2| * (ln|x1-x2| - 1) dx1 dx2
```

- This double integral can be computed numerically using the trapezoidal rule on the discretized S''(x)
- `S_ref` is the reference area (maximum cross-section or base area, consistent with other Cd definitions)

**Step 3: Mach-dependent oblique cutting planes**
- At supersonic Mach, the relevant area distribution is cut at the Mach angle `mu = asin(1/M)`:

```
S_theta(x) = cross-sectional area in a plane inclined at angle mu to the axis
```

- For small mu (high Mach), this approaches the axial distribution
- For mu near 90° (near M=1), this is the standard transonic area rule
- In practice, for M 0.95–1.3, use the axial S(x); for M > 1.3, transition to oblique cuts

**Step 4: Integration with existing drag model**
- The area-rule wave drag REPLACES the component-sum transonic wave drag in the range M_dd to M ~1.3
- Above M ~1.3, transition back to analytical component wave drag (Taylor-Maccoll + shock-expansion + Ackeret)
- Use C1-continuous blending in the handoff region (M 1.2–1.5):

```
Cd_wave_total = blend(Cd_area_rule, Cd_component_sum, M, 1.2, 1.5)
```

- Below M_dd (drag divergence Mach), wave drag = 0 as before

**Files to modify:**
- New class: `TransonicAreaRule.java` in `aerodynamics/`
  - `computeAreaDistribution(FlightConfiguration, Mach)` — walks components, returns S(x)
  - `computeWaveDrag(S_x, x, S_ref)` — evaluates the double integral
- `BarrowmanDragCalculator.java` — in the pressure drag assembly, call area rule for M near 1 instead of summing component pressure drags
- `BarrowmanCalculator.java` — may need to pass `FlightConfiguration` geometry to the drag calculator (currently only flight conditions and component forces are passed)

**Performance consideration:**
- The double integral is O(N²) where N is the number of axial stations
- With N=200, this is 40,000 evaluations per timestep — acceptable
- Cache the area distribution S(x) and only recompute when geometry changes (staging events)

**Validation:**
- For a Sears-Haack body (minimum wave drag shape), the computed Cd should match the theoretical minimum: `Cd = 128 * V² / (pi * L⁴)` where V is volume
- For a rocket with fins, transonic drag should be HIGHER than nose-only + fin-only (interference)
- Compare total Cd at M=1.05 against RASAero II — this is where the area rule has the most impact
- The drag rise onset Mach should agree with M_dd estimates from Phase 2e

---

### 6f. Pitts-Nielsen-Kaattari Fin-Body Interference Factors

**Priority:** Medium effort, high impact on stability accuracy.

**Problem:** The current fin-body aerodynamic model uses Barrowman's subsonic interference factors (K_WB for fin-on-body carryover, K_BW for body-on-fin carryover) at all Mach numbers. These factors are derived from slender-body theory and overpredict fin-body lift by 10–20% at supersonic speeds because the Mach cone from the body limits the portion of the fin that "sees" the body's upwash field.

**Method:**
- Implement the Pitts-Nielsen-Kaattari (PNK) Mach-dependent interference factors from NASA TR R-1307 (1959) and NACA TN 3967
- The key insight: at supersonic speeds, the body's influence on fin lift is limited to the region within the Mach cone emanating from the body-fin junction

**K_WB (fin carryover onto body):**
```
K_WB(M, r/s) = K_WB_sub(r/s) * F_WB(M, r/s, Lambda_LE)
```
where:
- `K_WB_sub(r/s)` is the existing Barrowman subsonic value (function of body radius / fin semispan)
- `F_WB` is the Mach correction factor:

```
For M < 1.0:   F_WB = 1.0  (subsonic, no correction)
For M > 1.0:   F_WB = 1.0 - 0.3 * (1 - 1/beta_s) * (r/s)^0.5
                where beta_s = sqrt(M^2 - 1) * s/c_r
```

- `beta_s` is the supersonic similarity parameter (Mach number × semispan / root chord)
- When `beta_s < 1` (Mach cone doesn't reach fin tip), the correction is smaller
- When `beta_s > 2`, the correction saturates at ~0.7–0.85 depending on `r/s`

**K_BW (body carryover onto fin):**
```
K_BW(M, r/s) = K_BW_sub(r/s) * F_BW(M, r/s)
```
where:
```
For M < 1.0:   F_BW = 1.0
For M > 1.0:   F_BW = 1.0 - 0.15 * (1 - 1/beta_s) * (r/s)^0.3
```

- The body carryover correction is smaller because the body is a more efficient carrier of lift (slender-body theory remains more valid)

**Transonic blending (M 0.85–1.15):**
- Use rational blend (from 6d) or smoothstep between `F = 1.0` and the supersonic value
- The correction must be smooth through M = 1 to prevent CP jumps

**Implementation:**
- New utility class: `PittsNielsenKaattari.java` in `aerodynamics/barrowman/`
  - `computeK_WB(M, r_body, s_fin, c_root, Lambda_LE)` — returns corrected K_WB
  - `computeK_BW(M, r_body, s_fin, c_root)` — returns corrected K_BW
- Both methods return the subsonic value when M < 0.85 (no change to subsonic results)

**Files to modify:**
- `FinSetCalc.java` — where K_WB and K_BW are currently computed/used in `calculateNonaxialForces()`, multiply by the PNK Mach correction factors
- The existing subsonic K_WB/K_BW formulas remain as the baseline; PNK only modifies them at M > ~0.85

**Validation:**
- At M < 0.85, fin CNa must be unchanged (F_WB = F_BW = 1.0)
- At M = 2.0, total fin-body CNa should decrease 10–20% compared to uncorrected
- CP should shift slightly aft (reduced fin-body lift means body contribution is relatively larger)
- Compare against wind tunnel data from NASA TN 3967 for a body-fin combination at M 1.5–3.0

---

### 6g. Chapman-Korst Base Drag Model (ESDU 77021)

**Priority:** Medium effort — improves base drag accuracy, especially with boattails.

**Problem:** The current Devan-Ashwood correlation is a simple curve fit that doesn't account for boundary layer state at the base. For rockets with boattails, the boundary layer arriving at the base is thinner and more energetic, which changes the base pressure significantly. The current boattail correction is geometric only.

**Method:**
- Implement the Chapman-Korst free shear layer recompression model as documented in ESDU 77021
- The base pressure is determined by the condition that the free shear layer emanating from the base edge must recompress to match the downstream pressure

**Base pressure coefficient:**
```
Cp_base = f(M_e, theta_BL, Re_theta)
```
where:
- `M_e` is the boundary layer edge Mach number at the base (from ShockGeometry)
- `theta_BL` is the boundary layer momentum thickness at the base
- `Re_theta` is the Reynolds number based on momentum thickness

**Boundary layer momentum thickness estimation:**
```
theta/x = 0.036 / Re_x^0.2    (turbulent flat plate, compressibility-corrected)
```
- Apply the Eckert reference temperature correction (already implemented for skin friction)
- `x` is the wetted length from nose to base

**Chapman-Korst base pressure:**
```
Cp_base = -2/(gamma*M_e^2) * [p_base/p_e - 1]
```
where `p_base/p_e` is computed from the recompression condition:
```
p_base/p_e = [1 + 0.25*(gamma-1)*M_e^2]^(-gamma/(gamma-1)) * f(theta_BL/r_base)
```

- The function `f(theta_BL/r_base)` captures the effect of boundary layer thickness relative to base radius:
  - Thin BL (low theta/r): base pressure is lower (higher base drag)
  - Thick BL (high theta/r): base pressure is higher (lower base drag)

**Simplified implementation (recommended for first pass):**
- Use the ESDU 77021 parametric curves digitized into lookup tables
- Tables: `Cp_base(M_e, theta_BL/r_base)` for turbulent boundary layers
- Interpolate bilinearly between table entries

**Boattail integration:**
- The boundary layer arriving at the base after a boattail is modified by the adverse/favorable pressure gradient
- For a converging boattail (favorable gradient): BL thins, base drag increases slightly
- For a diverging afterbody: BL thickens, base drag decreases
- The ShockGeometry pre-pass already provides local Mach at the base; use this as M_e

**Files to modify:**
- `BarrowmanDragCalculator.java` — replace or augment Devan-Ashwood with Chapman-Korst for the base drag term
- New utility: `ChapmanKorstBaseDrag.java` in `aerodynamics/` with lookup tables and interpolation
- Use ShockGeometry to get local Mach at the base station
- Compute BL momentum thickness from wetted length and Eckert reference conditions

**Fallback:**
- If ShockGeometry is not available (subsonic), fall back to the existing Devan-Ashwood/Hoerner model
- Chapman-Korst is most valuable at M > 1.3 where the free shear layer physics dominate

**Validation:**
- At M = 2.0 with no boattail, base Cd should be within 10% of Devan-Ashwood (they should agree for simple cylindrical afterbodies)
- With a 10° boattail, base drag should decrease more than the current geometric-only correction predicts
- Compare against NASA TN D-721 experimental data (the same data Devan-Ashwood was fitted to) — Chapman-Korst should match or improve the fit

---

### 6h. ESDU Transonic Similarity for Fin Aerodynamics

**Priority:** Medium effort — captures the CNa peak near M=1 that linear theory misses.

**Problem:** Fin CNa exhibits a characteristic peak near M = 1.0–1.1 due to transonic nonlinear effects. The current polynomial interpolation between subsonic (Barrowman) and supersonic (K1/K2/K3 linear theory) values doesn't capture the peak magnitude or shape correctly. This affects stability margin predictions in the most critical flight regime (transonic).

**Method:**
- Implement the ESDU transonic similarity rule, which collapses fin aerodynamic data onto universal curves using the transonic similarity parameter:

```
K_trans = (M^2 - 1) / (t/c)^(2/3)
```

where `t/c` is the fin thickness-to-chord ratio.

**CNa in the transonic regime:**
- The similarity parameter K_trans maps fin CNa onto a universal curve that captures the transonic peak:

```
For K_trans < -2 (subsonic):    CNa = CNa_sub / sqrt(1 - M^2)    [Prandtl-Glauert]
For K_trans in [-2, +3] (transonic):  CNa = CNa_peak * h(K_trans)   [universal curve]
For K_trans > +3 (supersonic):  CNa = CNa_sup * 4 / sqrt(M^2 - 1)  [Ackeret]
```

- The universal function `h(K_trans)` has a peak at `K_trans ≈ 0` (M ≈ 1):

| K_trans | h(K_trans) |
|---------|-----------|
| -2.0 | 0.70 |
| -1.0 | 0.85 |
| -0.5 | 0.93 |
| 0.0 | 1.00 |
| 0.5 | 0.97 |
| 1.0 | 0.90 |
| 2.0 | 0.75 |
| 3.0 | 0.62 |

- `CNa_peak` depends on fin planform:
```
CNa_peak = 2*pi * A / (2 + sqrt(4 + A^2)) * (1 + F_thickness(t/c))
```
where `A` is the fin aspect ratio and `F_thickness` accounts for thickness effects:
```
F_thickness = 1.0 + 2.5 * (t/c) + 8.0 * (t/c)^2
```

**Leading-edge sweep correction:**
- The effective Mach for swept fins: `M_eff = M * cos(Lambda_LE)`
- Apply the similarity parameter using M_eff instead of M
- This shifts the transonic peak to a higher freestream Mach for swept fins (physically correct)

**Files to modify:**
- `FinSetCalc.java` — in the CNa computation, add a transonic similarity path:
  - Compute `K_trans` from current Mach and fin `t/c`
  - If `K_trans` is in the transonic band [-2, +3], use the universal curve
  - Blend smoothly into the existing Prandtl-Glauert (subsonic) and linear theory (supersonic) values
- New utility: `TransonicSimilarity.java` in `aerodynamics/barrowman/` with the universal curve lookup and interpolation

**Validation:**
- Fin CNa should exhibit a clear peak near M = 1.0–1.1, with magnitude 20–40% above the subsonic value
- The peak Mach should shift higher for swept fins
- Thicker fins (higher t/c) should have a broader transonic region (K_trans range maps to a wider Mach range)
- At M < 0.8 and M > 1.5, results should match existing model (similarity parameter maps to sub/supersonic limits)
- Compare against ESDU 70012 and DATCOM fin data tables

---

### Implementation Order

The items are ordered to maximize incremental value while managing dependencies:

| Step | Item | Dependencies | Estimated Tests |
|------|------|-------------|-----------------|
| 1 | 6a: Jorgensen crossflow Cd_c | None | 8–10 |
| 2 | 6b: Power-on base drag | Motor data access | 10–12 |
| 3 | 6c: Dahlem-Buck nose drag | None | 8–10 |
| 4 | 6d: AP09 rational blending | None | 10–12 |
| 5 | 6h: ESDU transonic fin similarity | None (but benefits from 6d) | 10–12 |
| 6 | 6f: PNK fin-body interference | Benefits from 6d | 10–12 |
| 7 | 6g: Chapman-Korst base drag | ShockGeometry (Phase 3b) | 10–12 |
| 8 | 6e: Transonic area rule | FlightConfiguration geometry access | 15–20 |

**Total: ~80–100 new tests**

Steps 1–4 are independent and can be developed in parallel. Steps 5–6 benefit from the rational blending utility (step 4) but are not blocked by it. Step 7 uses ShockGeometry which already exists. Step 8 is the most complex and should be implemented last.

### Phase 6 Validation Gate

- **Body CNa:** At M=3, alpha=5°, body CNa increases 15–25% vs Phase 5 baseline (Jorgensen crossflow)
- **Powered drag:** During motor burn, total Cd decreases 15–40% vs unpowered (power-on base drag)
- **Nose drag:** Von Karman drag < ogive drag < cone drag at all M > 1.5 (Dahlem-Buck shape factors)
- **Transonic Cd:** Total Cd at M=1.05 for finned rockets within 10% of RASAero II (area rule)
- **Fin-body CNa:** At M=2, fin-body CNa reduced 10–20% vs uncorrected (PNK factors)
- **Base drag with boattail:** Chapman-Korst predicts larger boattail benefit than geometric-only correction
- **Fin transonic CNa:** Peak CNa near M=1 is 20–40% above subsonic (ESDU similarity)
- **Regime transitions:** All dCd/dM and dCNa/dM bounded through every transition (rational blending)
- **Subsonic regression:** All existing subsonic tests pass unchanged
- **RASAero II gap:** Total Cd vs Mach within 5% of RASAero II for standard geometries M 0.5–5.0

---

## Phase 7: Advanced Geometries & Multi-Body Dynamics

To achieve "extreme accuracy for all designs," the aerodynamic engine must be expanded to handle multi-body interference, non-planar aerodynamic surfaces, and exotic flight configurations ("odd rocs", parallel staging).

### 7a. Multi-Body Shock Interference (Parallel Staging)
- **Problem:** Strap-on boosters create massive asymmetric bow shocks that impinge on the core stage, creating high asymmetric pressures and plume interference.
- **Method:** Model parallel staging using a 3D intersection mapping of Mach cones to compute cross-body shock impingement. This relies on generating oblique shock networks between cylindrical bodies and quantifying the asymmetrical lift and interference drag.

### 7b. Ring Fin / Tube Fin Supersonic Model
- **Problem:** `Ackeret` theory only handles planar airfoils. Ring fins transition from a detached bow shock (spilled flow, huge drag) to an internal oblique shock system (swallowed shock, lower drag) across a critical Mach number.
- **Method:** Implement internal 1D compressible flow matching (Kantrowitz limit) to predict whether tubular fins will "swallow" their shocks or spill them, dynamically calculating the $C_D$ switchover point based on internal contraction ratios.

### 7c. Protuberance Wave Drag
- **Problem:** Large asymmetrical protuberances (cameras, massive launch lugs) produce 3D oblique shocks that wrap around the body, perturbing the boundary layer and moving the CP asymmetrically.
- **Method:** Implement Hoerner's 3D isolated protuberance wave drag methods. Account for the induced localized expansion/compression regions affecting pitch and yaw stability independently.

### 7d. Interstage Stepped Base Drag
- **Problem:** Multi-stage rockets often have exposed forward-facing step joints or interstage vents generating localized separation bubbles and extreme wave drag not captured by standard boattail models.
- **Method:** Augment Chapman-Korst methods with forward-facing step analytics (ESDU 66011) to account for pressure recovery in separated regions before the reattachment point.

---

## Phase 8: High-Fidelity Viscous & Dynamic Stability

At extreme speeds and high angles of attack, boundary layers, viscous effects, and pitch damping dictate vehicle survivability and accurately resolve the divergence of complex shapes.

### 8a. Dynamic Stability (Transonic/Supersonic Pitch Damping)
- **Problem:** While static stability ($C_{N_{\alpha}}$, $C_P$) is handled, dynamic pitch damping derivatives ($C_{m_q}$ and $C_{m_{\dot{\alpha}}}$) change heavily in transonic regimes. Some shapes experience *negative* damping near Mach 1.
- **Method:** Implement the Tobak-Wehrend and advanced DATCOM methods to resolve $C_{m_q}$ and $C_{m_{\dot{\alpha}}}$. This simulates wobbling limit cycles and prevents catastrophic artificial stabilization during simulation.

### 8b. Shock-Boundary Layer Interaction (SBLI)
- **Problem:** A strong shock interacting with a turbulent boundary layer ahead of fin roots causes localized flow separation, severely reducing fin control authority.
- **Method:** Apply semi-empirical correction factors mapping the shock strength and local Reynolds number to a boundary-layer separation extent, dynamically reducing effective root chord and fin lift capabilities.

### 8c. Dynamic Boundary Layer Transition Mapping
- **Problem:** Models assume uniformly fully turbulent or fully laminar states, but in reality, transition points shift heavily with Mach number and localized heating. 
- **Method:** Calculate boundary layer transition dynamically over the wetted body length based on the local Reynolds number, Mach number correction, and a surface roughness geometric factor to pinpoint exact $C_f$ integration transitions.

### 8d. High AoA Asymmetric Vortex Shedding
- **Problem:** While Jorgensen crossflow models high-AoA normal forces, it assumes symmetry. At extreme angles (>20°), even perfect bodies shed asymmetric vortices.
- **Method:** Model the stochastic side-force coefficient envelope generated by alternating vortex shedding to correctly simulate "odd roc" divergence vectors at high incidence angles.

---

## Phase 9: Modern Extensions & Coupled Physics

These improvements move beyond classical analytical aerodynamics into coupled multi-physics domains. They address limitations inherent in the 1950s–1970s methods used in Phases 1–8 by leveraging modern computational capabilities and real-world data.

### 9a. Aeroelastic Coupling (Fluid-Structure Interaction)

**Problem:** All aerodynamic models (Ackeret wave drag, Pitts-Nielsen fin lift, K1/K2/K3 supersonic CNa) assume the rocket is perfectly rigid. At Mach 2+ and high dynamic pressure, aerodynamic loading causes fins to bend and twist, reducing their effective angle of attack — a phenomenon called *aeroelastic loss of effectiveness*. Currently, OpenRocket only warns if the user exceeds a flutter velocity estimate; it does not adjust aerodynamic coefficients for structural deflection.

**Method:**
- At each timestep, compute local dynamic pressure `q` at each fin station (from `ShockGeometry` or freestream)
- Estimate fin twist angle `delta_theta` from aerodynamic moment and fin structural stiffness:

```
delta_theta = q * S_fin * CNa_rigid * x_cp / (G * J)
```

where:
- `S_fin` is fin planform area
- `x_cp` is distance from fin root to CP (moment arm)
- `G * J` is the torsional stiffness (shear modulus × polar moment of inertia)
- Material properties: lookup table for common fin materials (G10 fiberglass, carbon fiber, plywood, aluminum)

- Apply aeroelastic effectiveness factor:

```
eta_ae = 1 - delta_theta / alpha_eff
CNa_effective = eta_ae * CNa_rigid
```

- When `eta_ae < 0`, the fin has reversed effectiveness (divergence) — flag as a critical warning
- Flutter boundary check: compare dynamic pressure against the fin's critical flutter speed using the DATCOM empirical method

**Additional reference:** Meijer & Dala (ICAS, 2014) provide a validated zero-order flutter prediction method using shock-expansion theory + local piston theory. Computationally cheap enough for per-timestep evaluation. Simmons (AFIT, 2009) provides flutter boundary data validated against an actual in-flight fin failure (Falcon LAUNCH V sounding rocket).

**User interface:**
- Fin material selection (dropdown: G10, carbon fiber, birch plywood, aluminum, custom)
- Custom material: user specifies shear modulus G and thickness
- Simulation output: aeroelastic effectiveness ratio vs time, flutter margin vs time

**Files to modify:**
- `FinSetCalc.java` — apply `eta_ae` knockdown to CNa after existing computation
- New: `AeroelasticModel.java` in `aerodynamics/` — material properties, stiffness estimation, flutter check
- Fin component data model — add material type and thickness fields

**Validation:**
- At low q (subsonic, low altitude), `eta_ae ≈ 1.0` — no change to existing results
- At q = 50 kPa with thin G10 fins, expect 5–15% CNa reduction
- Flutter boundary should match DATCOM predictions for standard fin geometries
- Divergence (eta_ae < 0) should trigger a simulation warning
- Falcon LAUNCH V integration test: simulate the flight and verify flutter onset near actual failure point

---

### 9b. Full 6-DOF Simulation (Inertial Asymmetries)

**Problem:** The simulation assumes a symmetrical mass distribution: CG on the centerline and a diagonalized inertia tensor. For asymmetric configurations (clustered motors, camera pods, asymmetric fin layouts, parallel staging), the off-axis CG and cross-products of inertia create coupled roll-yaw-pitch dynamics that the current 3-DOF rotational model cannot capture.

**Method:**
- Replace the current simplified rotational dynamics with a full 6-DOF integration using Euler's equations with a complete 3×3 inertia tensor:

```
[I] * d(omega)/dt + omega × ([I] * omega) = M_aero + M_thrust + M_gravity
```

- Compute the full inertia tensor `[I]` from component masses and positions:
  - Diagonal terms: `I_xx`, `I_yy`, `I_zz` (existing roll/pitch/yaw moments)
  - Cross terms: `I_xy`, `I_xz`, `I_yz` (zero for symmetric rockets, nonzero for asymmetric)
- Use quaternion-based orientation integration to avoid gimbal lock:

```
dq/dt = 0.5 * q ⊗ [0, omega_x, omega_y, omega_z]
```

- The aerodynamic force model must then resolve forces and moments in all three body axes, not just pitch plane

**Additional reference:** NASA/CR-2012-217475 provides damping derivative formulations (Cmq, Cm_alpha_dot) for 6-DOF missile simulation. MAPLEAF (University of Calgary, 2021) already implements full 6-DOF rocket simulation and can serve as a cross-validation tool.

**Architectural impact:**
- `RK4SimulationStepper.java` — expand state vector from [position, velocity, pitch angle, pitch rate] to [position(3), velocity(3), quaternion(4), angular velocity(3)] = 13 state variables
- `AbstractSimulationStepper.java` — compute full 3-axis aero moments from component forces
- `AerodynamicForces.java` — add side force `CY`, yaw moment `Cn`, roll moment `Cl` fields
- Phase 8d asymmetric vortex shedding becomes a natural input to the side force channel

**Validation:**
- For symmetric rockets, 6-DOF results must match existing 3-DOF results exactly (cross-products = 0)
- For a rocket with deliberate CG offset, simulate and verify induced coning motion
- Compare coning frequency against analytical prediction: `omega_cone = (CNa * q * S * d) / (I_pitch * V)`
- Verify conservation of angular momentum in coast phase (no external torques except gravity gradient)
- Cross-validate against MAPLEAF 6-DOF output for the same geometry

---

### 9c. Real Atmospheric Data Ingestion

**Problem:** The US Standard Atmosphere 1976 is a yearly average for mid-latitudes. Real atmospheric conditions vary significantly with location, season, and time of day. Because wave drag peaks precisely at Mach 1.0, a few m/s shift in the local speed of sound (from temperature variation) alters the Mach number at which the drag peak occurs, which can shift predicted apogee by hundreds of meters.

**Method:**
- Support ingestion of atmospheric sounding data in standard formats:
  - `.rasp` files (balloon sounding data, widely used in rocketry)
  - `.csv` with columns: altitude, temperature, pressure, humidity, wind speed, wind direction
  - GFS/RAP model output (NOAA numerical weather prediction)
- Parse the sounding into an altitude-indexed lookup table replacing the standard atmosphere
- At each altitude during simulation:
  - Interpolate temperature → compute local speed of sound: `a = sqrt(gamma * R * T)`
  - Interpolate pressure and temperature → compute local density: `rho = p / (R * T)`
  - Compute viscosity via Sutherland's law at the actual temperature
  - Mach and Reynolds numbers use the actual local fluid properties

**Humidity correction:**
- Humid air has lower molecular weight → higher speed of sound at same temperature
- Effective gas constant: `R_eff = R_dry * (1 + 0.608 * w)` where `w` is mixing ratio
- Effect is small (< 1%) but matters for precision apogee prediction

**Files to modify:**
- New: `AtmosphericSounding.java` in `models/atmosphere/` — parser and interpolation
- `AtmosphericConditions.java` — add a mode that queries the sounding table instead of the standard model
- `SimulationOptions.java` — add atmosphere source selection (standard vs. custom sounding)
- UI: file picker for sounding data, preview plot of temperature/pressure vs altitude

**Validation:**
- With a standard atmosphere sounding file, results must match the existing model exactly
- With a hot-day sounding (+20°C at surface), speed of sound increases ~3.5%, shifting transonic drag peak
- Predicted apogee should change by the expected amount for the temperature difference
- Compare against flight data from launches with known weather conditions

---

### 9d. Surrogate Modeling (Data-Driven Acceleration)

**Problem:** The Phase 6e transonic area rule requires O(N²) numerical integration per timestep. Compositing 20+ analytical models creates blending "seams" that can be mathematically fragile. As the model complexity grows through Phases 6–8, computation time per timestep increases, potentially slowing interactive simulation.

**Method:**
- Generate a training dataset by running the analytical models (Phases 1–8) across a large design space:
  - Nose shape (5 types) × fineness ratio (5 values) × fin count (3/4/6) × fin geometry (AR, sweep, t/c: ~20 combinations) × Mach (50 points, M 0.3–10) × AoA (5 points, 0–10°)
  - Total: ~375,000 parameter combinations
  - For each: store Cd_total, CNa_total, CP, and per-component breakdowns
- Train a lightweight surrogate model:
  - **Gaussian Process (GP):** best for small datasets, provides uncertainty estimates
  - **Neural network (small MLP):** 3 hidden layers × 64 neurons, trained on the full dataset
  - Input: [nose_type, fineness_ratio, fin_AR, fin_sweep, fin_tc, fin_count, body_radius, Mach, AoA]
  - Output: [Cd_friction, Cd_pressure, Cd_base, CNa, x_CP]
- Query the surrogate in O(1) time during simulation instead of running the full analytical pipeline

**Hybrid approach (recommended):**
- Use the surrogate as a **fast predictor** for timesteps where geometry hasn't changed
- Run the full analytical model at key points (Mach regime transitions, geometry changes from staging)
- If the surrogate and analytical model disagree by > 5%, fall back to analytical
- This gives O(1) average case with analytical accuracy at critical points

**Architecture:**
- New: `SurrogateAeroModel.java` in `aerodynamics/` — loads trained model, provides query interface
- Model file: `.onnx` or custom binary format stored as a resource
- Training pipeline: separate Python script using scikit-learn or PyTorch, run offline
- `BarrowmanCalculator.java` — optional surrogate bypass when enabled in `SimulationOptions`

**Validation:**
- Surrogate predictions must match analytical models to < 2% RMS over the training domain
- Out-of-distribution inputs (geometries outside training range) must be detected and flagged
- Simulation results with surrogate vs analytical must agree within 1% on apogee for standard geometries
- Measure speedup: target 10–50× faster than full analytical pipeline

---

### 9e. Plume-Induced Flow Separation

**Problem:** Phase 6b models how the motor plume affects base pressure (reducing base drag during burn). However, at high altitudes where nozzles become highly underexpanded, the exhaust plume expands dramatically, creating a virtual aerodynamic blockage that extends well beyond the base diameter. This expanded plume sends a shock forward along the rocket body, separating the boundary layer over the aft body and fins. Sounding rockets traversing the upper atmosphere can lose 50% of fin lift and drag just before burnout — a critical stability concern.

**Method:**
- Compute the plume expansion ratio at altitude:

```
D_plume / D_exit = f(p_exit / p_ambient)
```

For highly underexpanded nozzles (`p_exit / p_ambient > 5`), the plume diameter grows as:
```
D_plume ≈ D_exit * sqrt(p_exit / p_ambient) * k_expansion
```
where `k_expansion ≈ 0.8` accounts for non-ideal expansion.

- When the plume diameter exceeds the base diameter, compute the effective blockage:

```
blockage_ratio = (D_plume / D_base)^2
```

- The plume-induced shock propagates forward along the body. Estimate the separation length:

```
L_sep = k_sep * D_base * (blockage_ratio - 1)^0.5
```

where `k_sep ≈ 2–4` depends on the boundary layer state (from Chapman-Korst, Phase 6g).

- Within the separated region:
  - Skin friction drops to near zero
  - Fin lift effectiveness is reduced: `eta_plume = max(0.1, 1 - f_overlap)` where `f_overlap` is the fraction of fin span immersed in separated flow
  - Fin drag is also reduced (separated flow has lower dynamic pressure)

**Trigger conditions:**
- Only significant when `p_exit / p_ambient > 3` (typically above 10 km altitude during burn)
- Most severe for motors with low expansion ratio nozzles at high altitude
- Check at each timestep during motor burn; deactivate after burnout

**Files to modify:**
- `BarrowmanDragCalculator.java` — modify skin friction and fin drag in the separated region
- `FinSetCalc.java` — apply `eta_plume` knockdown to CNa for fins within the separation zone
- Extend Phase 6b power-on base drag model with plume expansion geometry
- Need nozzle exit conditions from motor data (exit pressure, exit diameter)

**Validation:**
- At sea level (p_exit ≈ p_ambient), no plume effect — results unchanged
- At 30 km altitude with a typical HPR motor, plume diameter should exceed base diameter by 2–3×
- Fin effectiveness reduction should be 20–50% depending on fin position relative to base
- Compare against published sounding rocket flight data where plume effects are documented

---

### Phase 9 Validation Gate

- **Aeroelastic:** Thin fins at high q show measurable CNa reduction; divergence detected before flutter
- **6-DOF:** Symmetric rockets reproduce 3-DOF results; asymmetric rockets show physically correct coning
- **Atmosphere:** Custom sounding data shifts transonic peak and apogee by expected amounts
- **Surrogate:** < 2% RMS error vs analytical; 10×+ speedup; out-of-distribution detection works
- **Plume:** Sea-level results unchanged; high-altitude fin effectiveness drops during burn as predicted
- **Subsonic regression:** All existing tests pass unchanged

---

## Phase 10: Modern Data Sources & Validation Databases

The Phases 1–8 models draw primarily from 1950s–1970s analytical methods and empirical data (Taylor-Maccoll, Ackeret, DATCOM, NASA TR-R-100, Devan-Ashwood). Since those publications, significant modern data has become available — wind tunnel revalidation, CFD-validated correlations, updated engineering databases, open-source simulation tools, and flight-derived validation data. Phase 10 integrates these sources to provide both more accurate model inputs and stronger validation baselines.

### 10a. High-Impact Model Replacements

#### Lamb & Oberkampf Base Drag Correlation (1995)

**Replaces:** Devan-Ashwood (1961) in Phase 2b / Phase 6g

**What it provides:** A supersonic base drag correlation with explicit **Reynolds number dependence** — a real physical effect that Devan-Ashwood ignores entirely. Base drag is 30–50% of total drag at supersonic speeds for finned rockets, so even a 10% improvement here is significant.

**Correlation form:**
```
Cd_base = f(M, Re_D)
```
where Re_D is the Reynolds number based on base diameter. At high Re (turbulent wake), base drag is lower than the Devan-Ashwood prediction; at low Re, it's higher.

**Source:** Lamb, J.P. & Oberkampf, W.L. (1995). "Review and Development of Base Pressure and Base Heating Correlations in Supersonic Flow." Journal of Spacecraft and Rockets, 32(1), 8-23.

**Access:** Paper behind AIAA paywall; correlation formula is simple enough to implement from the abstract/tables. Mach range: 1.0–5.0+.

**Implementation:**

File: `BarrowmanDragCalculator.java` — supersonic base drag computation (currently `Cd_base = 0.064 + 0.186/M^2`)

1. Compute base Reynolds number: `Re_D = rho * V * D_base / mu` (all quantities available from `FlightConditions` and `AtmosphericConditions`)
2. Replace the Devan-Ashwood formula with:

```java
// Lamb-Oberkampf base drag correlation (Ref 31)
// Valid M > 1.0, Re_D > 1e4
double logReD = Math.log10(reD);
double Cd_base_LO = (0.064 + 0.186 / (mach * mach))
                  * (1.0 - 0.08 * (logReD - 6.0));  // Re correction centered at Re=1e6
// Clamp Re correction factor to [0.7, 1.3] for safety
Cd_base_LO *= MathUtil.clamp(1.0 - 0.08 * (logReD - 6.0), 0.7, 1.3);
```

3. Keep the existing Devan-Ashwood as the fallback when Re_D is unavailable or < 1e4
4. The transonic polynomial (M 0.85–1.3) stays unchanged — Lamb-Oberkampf only applies at M > 1.3
5. Update the boattail correction to use Viswanath data (see below)

**Test targets from Herrin & Dutton (Ref 37):**

| Mach | Re_D | Cd_base (measured) | Devan-Ashwood | Lamb-Oberkampf |
|------|------|-------------------|---------------|----------------|
| 2.46 | 1.5e6 | 0.092 | 0.095 | ~0.091 |
| 2.46 | 6.7e6 | 0.085 | 0.095 | ~0.086 |

The Re dependence is the key — Devan-Ashwood gives the same value for both, Lamb-Oberkampf captures the measured difference.

---

#### Missile DATCOM 2014 (AFRL)

**Supplements:** TR-R-100 tables, manual DATCOM lookups, and serves as a validation oracle

**What it provides:** The Air Force Research Laboratory maintained and updated Missile DATCOM through the 2010s. The 2014 version expanded Mach coverage and improved axisymmetric body methods. It produces **component-level Cd, CNa, CP vs Mach tables** directly for arbitrary missile/rocket geometries. Mach range: 0–20+.

**Source:** AFRL Missile DATCOM, distributed through DTIC (Defense Technical Information Center). The source code is **public domain**.

**Usage strategy:**
1. Run Missile DATCOM on the 5 standard validation rocket geometries
2. Extract component-level Cd, CNa, CP at 50 Mach points per geometry
3. Use as a **three-way validation baseline** alongside RASAero II and analytical targets
4. Where OpenRocket Plus disagrees with both DATCOM and RASAero, investigate
5. Optionally extract DATCOM's internal lookup tables for direct use where they improve on TR-R-100

**Implementation:**

Step 1 — Generate validation data (offline, one-time):
1. Install Missile DATCOM from DTIC (Fortran source, compiles with gfortran)
2. Create input files for the 5 standard validation geometries:
   - Simple cone-cylinder (15° half-angle, fineness ratio 5)
   - Tangent ogive-cylinder (fineness ratio 3)
   - Cone-cylinder with 4 fins (trapezoidal, AR=2)
   - Ogive-boattail with 4 fins
   - Multi-stage (2-stage with interstage shoulder)
3. Run at Mach points: 0.3, 0.5, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.3, 1.5, 2.0, 3.0, 5.0
4. Extract from DATCOM output: total Cd, CNa_body, CNa_fin, CP, Cd_friction, Cd_pressure, Cd_base

Step 2 — Store as test resources:
```
core/src/test/resources/validation/datcom/
  cone_cylinder_cd.csv        # Mach, Cd_total, Cd_friction, Cd_pressure, Cd_base
  cone_cylinder_stability.csv # Mach, CNa, CP
  ogive_cylinder_cd.csv
  ... (one pair per geometry)
```

Step 3 — Automated comparison tests:
```java
// In a new DATCOMValidationTest.java
@ParameterizedTest
@CsvFileSource(resources = "/validation/datcom/cone_cylinder_cd.csv")
void totalCdMatchesDATCOM(double mach, double datcomCd, ...) {
    // Build matching rocket geometry
    // Compute OR+ Cd at this Mach
    // Assert within tolerance (10% for Phase 5, 5% target for Phase 6+)
}
```

Step 4 — Optional: extract DATCOM internal tables for direct use where they improve on TR-R-100 (particularly for nose shapes at high fineness ratios and M > 3.6 where TR-R-100 runs out).

---

#### Silton & Weinacht — ARL Projectile Aerodynamics (2005–2020)

**Supplements:** Validation baseline (ground truth from wind tunnel + CFD)

**What it provides:** Army Research Lab published extensively on CFD-validated aerodynamics for axisymmetric bodies with fins. Key datasets include tabulated Cd and CNa vs Mach (M 0.5–4.0) for standard projectile shapes, validated against Ballistic Research Lab wind tunnel data.

**Key papers:**
- Silton, S.I. (2005). "Navier-Stokes Computations for a Spinning Projectile from Subsonic to Supersonic Speeds." AIAA Journal, 43(2).
- Silton, S.I. & Weinacht, P. (2008-2015). Multiple papers on fin-stabilized projectile aerodynamics at M 0.5–4.0.
- DeSpirito, J. & Heavey, K.R. (2004-2008). CFD studies of fin-body interference at supersonic speeds, quantifying interference effects relevant to Phase 3c/3d.

**Access:** ARL technical reports are **freely available on DTIC** (dtic.mil). AIAA journal papers require subscription.

**Implementation:**

1. Download ARL technical reports from DTIC (search "Silton projectile aerodynamics" or report numbers ARL-TR-xxxx)
2. Digitize the tabulated Cd/CNa vs Mach data into CSV files:

```
core/src/test/resources/validation/arl/
  silton_2005_projectile_cd.csv   # Mach, Cd_total, Cd_pressure, Cd_base
  silton_2005_projectile_cna.csv  # Mach, AoA, CNa
  despirito_2006_finbody.csv      # Mach, CNa_total, CNa_fin, CNa_body
```

3. Create test class `ARLWindTunnelValidationTest.java`:
   - Map each ARL geometry to an OpenRocket `Rocket` object (match dimensions exactly)
   - Compute OR+ aero coefficients at the same Mach/AoA points
   - Report error vs measured data (target: < 10% for total Cd, < 15% for component breakdown)

4. The DeSpirito fin-body data is especially valuable for validating Phase 3c (fin local flow correction) and Phase 6f (PNK interference factors) — it provides measured fin-body interference at M 1.5–3.0.

---

#### Roy & Blottner — Compressible Skin Friction Validation (2006)

**Validates:** Eckert reference temperature method (Phase 2d)

**What it provides:** Sandia National Labs report revalidating the Hopkins-Inouye compressible flat plate data compilation against modern CFD. Confirmed accurate to M=10. Provides tabulated Cf vs Mach and Re for turbulent flat plates with compressibility correction.

**Source:** Roy, C.J. & Blottner, F.G. (2006). "Review and Assessment of Turbulence Models for Hypersonic Flows." Sandia report SAND2006-3952. **Freely available** from Sandia.

**Also recommends Van Driest II** as a marginal improvement over Eckert (<5% difference below M=5):
```
Cf_compressible = Cf_incompressible(Re_eff) / F_c
```
where F_c is the Van Driest compressibility factor using the reference enthalpy method. Slightly more accurate than Eckert's reference temperature at M > 3.

**Implementation:**

Step 1 — Validation (immediate):
- Download SAND2006-3952 from Sandia (free PDF)
- Extract Table 3 (compressible Cf vs Mach and Re for turbulent flat plates)
- Add to `EckertSkinFrictionTest.java` as additional test targets:

```java
// Roy & Blottner validation targets (Ref 34, Table 3)
// Cf_turbulent at Re_x = 1e7, adiabatic wall
assertCfWithinTolerance(mach=2.0, expected=0.00187, tolerance=0.05);
assertCfWithinTolerance(mach=3.0, expected=0.00156, tolerance=0.05);
assertCfWithinTolerance(mach=5.0, expected=0.00112, tolerance=0.05);
```

Step 2 — Van Driest II upgrade (optional, Phase 6 timeframe):

File: `BarrowmanDragCalculator.java` — `calculateFrictionCoefficient` supersonic branch

```java
// Van Driest II compressibility transformation
// More accurate than Eckert at M > 3 (difference < 5% below M=5)
double Tw_Te = 1.0 + r * (gamma - 1.0) / 2.0 * mach * mach;  // adiabatic wall
double A = (Tw_Te - 1.0) / 2.0;
double B = (gamma - 1.0) / 2.0 * mach * mach / Tw_Te;
double alpha_vd = (2 * A - B) / Math.sqrt(4 * A + B * B);
double beta_vd = B / Math.sqrt(4 * A + B * B);
double Fc = Tw_Te / (1.0 + (gamma - 1.0) / 2.0 * mach * mach)
          * (Math.asin(alpha_vd) + Math.asin(beta_vd))
          / Math.asin(1.0);  // simplified form
double Cf_compressible = Cf_incompressible / Fc;
```

Keep Eckert as the default; Van Driest II as a configuration option in `SimulationOptions` for users who want maximum skin friction accuracy.

---

### 10b. Gap-Filling Sources

#### ESDU Data Items (Updated Through 2000s)

The Engineering Sciences Data Unit maintains the **gold standard** engineering correlations, updated with modern data through the 2000s. Subscription required, but widely available in university aerospace libraries.

| ESDU Item | Topic | Replaces/Supplements | Mach Range |
|-----------|-------|---------------------|------------|
| 77020 | Transonic drag of bodies of revolution | TR-R-100 interpolation | 0.6–1.4 |
| 78041 | Supersonic base drag | Devan-Ashwood | 1.0–5.0 |
| 78019 | Pressure drag of bodies at zero incidence, transonic | Transonic drag rise (Phase 2e) | 0.8–1.4 |
| 70012 | Fin-body interference factors | Barrowman K_WB/K_BW | 0.5–3.0 |
| 77021 | Base pressure at supersonic speeds | Chapman-Korst (Phase 6g) | 1.0–5.0 |
| 66011 | Forward-facing steps in supersonic flow | Interstage drag (Phase 7d) | 1.0–4.0 |

**Implementation priority and integration points:**

1. **ESDU 77020 → `SymmetricComponentCalc.java`** (transonic pressure drag)
   - Replace TR-R-100 table interpolation in the M 0.6–1.4 range with ESDU 77020 parameterization
   - ESDU parameterizes by fineness ratio and nose shape family — more complete coverage than TR-R-100
   - Keep TR-R-100 as fallback for shapes ESDU doesn't cover
   - Digitize the ESDU curves into a 2D lookup table: `Cd_wave(M, fineness_ratio)` per nose family

2. **ESDU 78041 → `BarrowmanDragCalculator.java`** (supersonic base drag)
   - Can serve as a cross-check for Lamb-Oberkampf or as the primary correlation
   - Provides base drag as `Cd_base(M, Re_D, theta_boattail)` — includes boattail angle directly
   - Digitize into a 3D lookup: `baseDragTable[machIndex][reIndex][boattailIndex]`
   - Interpolate trilinearly at runtime

3. **ESDU 70012 → `FinSetCalc.java`** (fin-body interference)
   - Cross-check for PNK factors (Phase 6f)
   - If ESDU values differ from PNK by > 10%, investigate which better matches ARL wind tunnel data
   - Digitize as `K_interference(M, r/s, AR)` lookup table

Store digitized ESDU data as CSV resources:
```
core/src/test/resources/esdu/
  esdu77020_transonic_body_drag.csv    # shape, fineness_ratio, Mach, Cd_wave
  esdu78041_base_drag.csv              # Mach, Re_D, boattail_angle, Cd_base
  esdu70012_fin_interference.csv       # Mach, r_over_s, AR, K_WB, K_BW
```

---

#### Viswanath — Boattail Base Drag (1996)

**Supplements:** Phase 2b boattail correction (currently geometric-only)

**What it provides:** Comprehensive correlations for boattail angle effects on base drag across M 0.5–3.5. Accounts for the pressure gradient history of the boundary layer arriving at the base — something the current geometric-only correction misses.

**Source:** Viswanath, P.R. (1996). "Flow Management Techniques for Base and Afterbody Drag Reduction." Progress in Aerospace Sciences, 32(2), 79-129. Elsevier paywall but widely cited.

**Key correlation:** Base drag reduction as a function of boattail half-angle, boattail length-to-diameter ratio, and Mach number. Provides 2D lookup table data.

**Implementation:**

File: `BarrowmanDragCalculator.java` — boattail correction in base drag computation

Currently the boattail correction is purely geometric (function of base area ratio). Replace with:

```java
// Viswanath boattail base drag correction (Ref 35)
// Inputs: boattail half-angle (deg), L_bt/D_body ratio, Mach
double theta_bt = boattailHalfAngle;  // from component geometry
double LD_bt = boattailLength / bodyDiameter;
double eta_bt;

if (theta_bt < 6.0) {
    // Mild boattail: large benefit, insensitive to Mach
    eta_bt = 0.25 + 0.05 * theta_bt;
} else if (theta_bt < 16.0) {
    // Optimal range: benefit depends on L/D and Mach
    double eta_geom = 0.25 + 0.05 * 6.0 + 0.04 * (theta_bt - 6.0);
    double machFactor = 1.0 + 0.1 * Math.max(0, mach - 1.0);  // supersonic enhancement
    eta_bt = eta_geom * machFactor;
    eta_bt = Math.min(eta_bt, 0.95);  // can't eliminate all base drag
} else {
    // Steep boattail: flow separates, benefit diminishes
    eta_bt = Math.max(0.0, 0.95 - 0.05 * (theta_bt - 16.0));
}

// eta_bt = fraction of base drag REMAINING (0 = no base drag, 1 = full base drag)
Cd_base *= eta_bt;
```

The key improvement over the current model: at supersonic Mach with a 10° boattail, the expansion fan at the boattail edge energizes the wake, providing more base drag reduction than the subsonic geometric scaling predicts.

---

#### Fleeman — Tactical Missile Design (2006)

**Supplements:** Entire aerodynamic model as a cross-check

**What it provides:** Engineering-level Cd, CNa, CP correlations and tabulated coefficients for missile-like configurations. Essentially a modernized, simplified DATCOM in book form. Same fidelity level as OpenRocket Plus targets.

**Source:** Fleeman, E.L. (2006). "Tactical Missile Design." 2nd ed., AIAA Education Series. Purchase required (~$80).

**Implementation:**

Not a code integration — Fleeman serves as a **design-time cross-check reference**. When implementing any Phase 6–9 model:

1. Look up Fleeman's simplified formula for the same coefficient
2. Compare your detailed model's output against Fleeman's estimate
3. If they differ by > 20%, one of them is wrong — investigate before proceeding
4. Fleeman's simplified correlations can also serve as fallback values for exotic geometries where detailed models fail

Fleeman's key quick-check formulas (from Chapter 4):
- Total Cd at M=2 for a typical missile: `Cd ≈ 0.8 * (d/L)^0.6 + Cd_base` 
- CNa for body alone: `CNa ≈ 2 * (L/d)` (per radian, slender body limit)
- CP for fins: approximately at 25% MAC for subsonic, shifting to ~40% MAC for supersonic

---

#### Herrin & Dutton / Bourdon & Dutton — Modern Base Flow Data (1994–2003)

**Validates:** Devan-Ashwood / Lamb-Oberkampf base drag models

**What they provide:** Detailed experimental studies of supersonic base flow with measured Cd_base vs Mach for M 1.5–3.0. Showed Devan-Ashwood **overpredicts** at higher Mach numbers.

**Key papers:**
- Herrin, J.L. & Dutton, J.C. (1994). "Supersonic Base Flow Experiments in the Near Wake of a Cylindrical Afterbody." AIAA Journal, 32(1).
- Bourdon, C.J. & Dutton, J.C. (2001). "Planar Visualizations of Large-Scale Turbulent Structures in Axisymmetric Supersonic Base Flows." Physics of Fluids, 13(9).

**Implementation:**

Add measured data points to `BaseDragModelTest.java`:

```java
// Herrin & Dutton (1994) measured base drag - Ref 37
// Cylindrical afterbody, turbulent BL, no boattail
@Test
void baseDragMatchesHerrinDutton() {
    // M=2.46, Re_D=1.5e6
    assertBaseCd(mach=2.46, reD=1.5e6, expected=0.092, tolerance=0.10);
    // M=2.46, Re_D=6.7e6 — lower Cd due to higher Re (more energetic wake)
    assertBaseCd(mach=2.46, reD=6.7e6, expected=0.085, tolerance=0.10);
}

// Bourdon & Dutton (2001) measured base drag - Ref 38
@Test
void baseDragMatchesBourdonDutton() {
    assertBaseCd(mach=2.46, reD=3.2e6, expected=0.089, tolerance=0.10);
}
```

These test targets validate both the Mach dependence (existing Devan-Ashwood) and the Re dependence (Lamb-Oberkampf upgrade). If Devan-Ashwood passes the Mach test but fails the Re test, that confirms the need for the Lamb-Oberkampf upgrade.

---

### 10c. Validation Infrastructure

#### NASA Turbulence Modeling Resource

**URL:** turbmodels.larc.nasa.gov (freely accessible)

**What it provides:** CFD validation cases including supersonic axisymmetric bodies with tabulated experimental reference data. Most useful for skin friction and boundary layer transition validation.

**Implementation:**

1. Download validation case data from the NASA TMR website (plain text / CSV format)
2. Key cases for OpenRocket Plus validation:
   - **Supersonic flat plate:** Cf vs x at M=2.0, 3.0, 5.0 — validates Eckert/Van Driest
   - **Cone flow:** surface pressure vs cone angle at M=2.0, 4.0 — validates Taylor-Maccoll
3. Store in `core/src/test/resources/validation/nasa_tmr/`
4. Create `NASATMRValidationTest.java` comparing OR+ predictions against experimental reference

This is low-effort, high-value: the data is already digitized and freely downloadable.

---

#### AGARD Advisory Reports (Digitized)

**Key reports:**
- AGARD-AR-138 (1979): Extensive wind tunnel data for missile configurations
- AGARD-AR-303 (1994): CFD validation cases for missiles/projectiles with experimental data

**Access:** Available through NATO STO archives. Several researchers (Silton at ARL) published reanalysis with modern CFD comparisons.

**Implementation:**

1. Obtain AGARD-AR-303 from NATO STO archives (free for NATO member country citizens) or through university library
2. Focus on "Standard Missile" configurations in the report — these have complete geometry definitions and measured aero data
3. Digitize: geometry (dimensions), and force/moment data vs Mach and AoA
4. For each AGARD configuration:
   - Build a matching `Rocket` object in test code
   - Run full `BarrowmanCalculator.getAerodynamicForces()` sweep
   - Compare Cd, CNa, CP against measured data at M 0.5–4.0
5. Store as `core/src/test/resources/validation/agard/agard303_config_*.csv`
6. These are the most comprehensive end-to-end validation cases available — complete vehicle with fins, measured in a research-grade wind tunnel

---

#### Lock's Transonic Drag Rise Rule

**Supplements:** Phase 2e transonic drag rise model

**What it provides:** A simple but effective correlation for the subsonic drag rise (M_crit to M=1.0):

```
Delta_Cd = k * [(M - M_crit) / (1 - M_crit)]^4
```

Combined with existing TR-R-100 data for M > 1.0, this fills the gap in the subsonic onset region where the current model transitions from zero wave drag to the first empirical data point.

**Source:** Lock, C.N.H. (1946). "The Prediction of the Drag of Aerofoils and Bodies at High Subsonic Speeds." ARC R&M 2455. Despite the 1946 date, the correlation is still used in modern transonic codes.

**Implementation:**

File: `SymmetricComponentCalc.java` — transonic drag rise computation (Phase 2e)

Currently the drag rise from M_dd to the first empirical/analytical data point uses a cubic Hermite polynomial. Replace the onset shape with Lock's 4th-power rule:

```java
// Lock's transonic drag rise rule (Ref 39)
// Replaces arbitrary cubic Hermite onset shape with physics-based formula
double M_dd = computeDragDivergenceMach(noseShape, finenessRatio);  // existing Phase 2e
double M_crit = M_dd - 0.05;  // critical Mach slightly below drag divergence

if (mach > M_crit && mach < 1.0) {
    // Subsonic drag rise (Lock)
    double x = (mach - M_crit) / (1.0 - M_crit);
    double deltaCd = k_lock * x * x * x * x;  // 4th-power rise
    // k_lock calibrated per nose shape to match TR-R-100 at M=1.0
    Cd_wave += deltaCd;
} else if (mach >= 1.0) {
    // Existing supersonic wave drag (Taylor-Maccoll / shock-expansion / TR-R-100)
    // ... unchanged
}
```

The 4th-power shape captures the physical drag-rise onset much better than a cubic Hermite — it stays low longer (flat bottom of the drag bucket) and then rises sharply near M=1, matching experimental curves. The constant `k_lock` is determined by requiring continuity with the supersonic wave drag at M=1.0.

---

### Recommended Validation Strategy Using Modern Sources

The most powerful use of these sources is a **multi-source validation matrix**:

```
                    Analytical    Missile     ARL Wind    RASAero II    OpenRocket
Component           Target        DATCOM      Tunnel      Output        Plus
─────────────────────────────────────────────────────────────────────────────────
Cone wave drag      Taylor-       DATCOM      Silton      RASAero       OR+
                    Maccoll       2014        2005-15
Base drag           Lamb-         DATCOM      Herrin &    RASAero       OR+
                    Oberkampf     2014        Dutton
Skin friction       Roy &         DATCOM      Hopkins-    —             OR+
                    Blottner      2014        Inouye
Fin CNa             Linear        DATCOM      DeSpirito   RASAero       OR+
                    theory        2014        2004-08
Transonic Cd        ESDU          DATCOM      AGARD       RASAero       OR+
                    77020         2014        AR-303
Total Cd            —             DATCOM      ARL data    RASAero       OR+
                                  2014
```

For each component and total, compare OpenRocket Plus against **all available sources**. Where OR+ matches the measured data (ARL, AGARD) more closely than RASAero or DATCOM, that's quantifiable, publishable proof of accuracy.

**Implementation as test infrastructure:**

Directory structure:
```
core/src/test/resources/validation/
  arl/                          # Silton, DeSpirito wind tunnel data (ground truth)
    silton_2005_projectile_cd.csv
    silton_2005_projectile_cna.csv
    despirito_2006_finbody_interference.csv
  datcom/                       # Missile DATCOM 2014 computed outputs
    cone_cylinder_cd.csv
    ogive_cylinder_cd.csv
    cone_cylinder_fins_cd.csv
    ogive_boattail_fins_cd.csv
    multistage_cd.csv
    (matching _stability.csv files for CNa/CP)
  esdu/                         # ESDU digitized correlations
    esdu77020_transonic_body_drag.csv
    esdu78041_base_drag.csv
    esdu70012_fin_interference.csv
  rasaero/                      # RASAero II outputs (existing baseline)
    (existing files from Phase 0b)
  nasa_tmr/                     # NASA Turbulence Modeling Resource experimental data
    flat_plate_cf_m2.csv
    flat_plate_cf_m5.csv
    cone_surface_pressure.csv
  agard/                        # AGARD-AR-303 complete vehicle wind tunnel data
    agard303_config1.csv
    agard303_config2.csv
```

Test classes:
```java
// Master validation test that reports error matrix across all sources
public class MultiSourceValidationTest {
    @ParameterizedTest
    @MethodSource("allGeometriesAndMachPoints")
    void reportErrorMatrix(RocketGeometry geom, double mach) {
        double orPlusCd = computeORPlusCd(geom, mach);
        
        // Load all available reference values
        OptionalDouble datcomCd = loadDATCOM(geom, mach);
        OptionalDouble arlCd = loadARL(geom, mach);      // wind tunnel (gold standard)
        OptionalDouble rasaeroCd = loadRASAero(geom, mach);
        
        // Report errors vs each source
        reportError("OR+ vs DATCOM", orPlusCd, datcomCd);
        reportError("OR+ vs ARL",    orPlusCd, arlCd);
        reportError("OR+ vs RASAero", orPlusCd, rasaeroCd);
        
        // Hard assertion: must be within 10% of wind tunnel data
        arlCd.ifPresent(ref -> 
            assertEquals(ref, orPlusCd, ref * 0.10, 
                "Cd at M=" + mach + " exceeds 10% error vs wind tunnel"));
    }
}
```

This infrastructure makes accuracy improvements **measurable at every commit** — you can see exactly which source each model agrees/disagrees with and by how much.

### Implementation Order for Modern Data Integration

| Step | Action | Effort | Blocked By |
|------|--------|--------|------------|
| 1 | Download & digitize ARL data from DTIC | 1–2 hours | Nothing |
| 2 | Download Roy & Blottner (SAND2006-3952), add Cf targets | 1 hour | Nothing |
| 3 | Download NASA TMR flat plate data, add validation tests | 1 hour | Nothing |
| 4 | Set up Missile DATCOM, generate 5-geometry sweep | 2–4 hours | DATCOM install |
| 5 | Implement Lamb-Oberkampf base drag correlation | 2–3 hours | Nothing |
| 6 | Implement Lock drag-rise onset | 1–2 hours | Nothing |
| 7 | Implement Viswanath boattail correction | 1–2 hours | Nothing |
| 8 | Implement Van Driest II skin friction (optional) | 2–3 hours | Nothing |
| 9 | Obtain & digitize ESDU items | 3–5 hours | ESDU access |
| 10 | Obtain & digitize AGARD-AR-303 | 2–3 hours | AGARD access |
| 11 | Build MultiSourceValidationTest framework | 3–4 hours | Steps 1–4 |

Steps 1–3 are free, quick, and immediately useful — do these first. Steps 5–7 are code changes that directly improve accuracy. Steps 9–10 depend on library access.

---

### 10d. Freely Available Aerodynamic Databases

#### NASA CR-2835/CR-2836 — Missile Aerodynamic Data Compilation (1977)

**What it provides:** Volumes I and II compile aerodynamic coefficients for ~30 declassified missile configurations. Tabulated data includes base drag, zero-lift drag, lift curve slope, aerodynamic center location, sideslip derivatives, and control effectiveness. Mach range: 0.2–4.63.

**Access:** Freely downloadable from NASA Technical Reports Server (NTRS).

**Implementation:**
- Digitize Cd/CNa/CP tables for the 5–10 configurations most similar to amateur rockets (cone-cylinder-fin bodies)
- Store as `core/src/test/resources/validation/nasa_cr2835/`
- Use as additional validation targets alongside ARL and DATCOM data
- ~30 configurations provide the most diverse geometry validation set available from a single source

---

#### NASA TN D-4013 / TN D-4014 — ARCAS Sounding Rocket Wind Tunnel Data

**What it provides:** Static stability data for ARCAS sounding rocket configurations. Mach 1.5–4.63, AoA -4° to 20°, Re 3.0×10⁶/ft. Includes CD, CP location, and stability derivatives.

**Access:** Freely downloadable from NTRS.

**Implementation:**
- Ideal validation case — a real finned rocket body with published supersonic wind tunnel data
- RASAero already matches this data well, so it's a direct head-to-head benchmark
- Build an ARCAS `Rocket` geometry in test code and compare OR+ predictions
- Store as `core/src/test/resources/validation/arcas/`
- This should be a **primary Phase 5+ validation geometry** — a real sounding rocket, not a synthetic test case

---

#### PDAS — Public Domain Aeronautical Software Collection

**What it provides:** ~100+ public-domain NASA/NACA aerodynamic codes with full Fortran source and test cases. Key programs:
- **FRICTION:** Implements Van Driest II compressible skin friction (NASA TN D-6945 method) — can generate reference validation data for Phase 2d
- **D2500:** Supersonic wave drag via area rule — can cross-check Phase 6e transonic area rule calculations
- **PANAIR:** Subsonic/supersonic panel method — can generate reference solutions for complex geometries

**Access:** All free at pdas.com. Fortran source compiles with gfortran.

**Implementation:**
- Run FRICTION to generate Cf reference tables at M 0.3–10, Re 10⁵–10⁹ → validation targets for `EckertSkinFrictionTest`
- Run D2500 on standard geometries → cross-check for `TransonicAreaRuleTest` (Phase 6e)
- These are one-time offline runs; store outputs as CSV test resources

---

#### NASA TMR — Supersonic/Hypersonic DNS Data (GitHub)

**What it provides:** DNS (Direct Numerical Simulation) data for supersonic and hypersonic zero-pressure-gradient turbulent boundary layers at Mach 2.5–14. Skin friction coefficients from 4 independent CFD codes. Now hosted on GitHub (tmbwg.github.io/turbmodels/).

**Access:** Freely downloadable.

**Implementation:**
- This is **gold-standard** validation for Phase 2d skin friction — DNS is more accurate than experiment for simple geometries
- Download Cf vs Re_x data at M=2.5, 5.0, 8.0, 14.0
- Add as high-confidence test targets in `EckertSkinFrictionTest`:

```java
// NASA TMR DNS skin friction targets (gold standard)
// Turbulent flat plate, adiabatic wall
assertCfWithinTolerance(mach=2.5, reX=1e7, expected=DNS_VALUE, tolerance=0.03);
assertCfWithinTolerance(mach=5.0, reX=1e7, expected=DNS_VALUE, tolerance=0.05);
```

---

#### RASAero II Flight Validation Database

**What it provides:** Published flight-vs-prediction comparisons for dozens of high-power rockets. Average error 3.38%, with 78.8% of predictions within ±10%. Includes specific rockets with exact predicted vs actual apogees.

**Access:** Freely available at rasaero.com/comparisons-flight.htm.

**Implementation:**
- This is the **definitive benchmark** — if OR+ can match RASAero's 3–5% apogee accuracy on these published cases, the project succeeds
- For each rocket in the RASAero database:
  1. Build matching geometry in OR+ (dimensions from the published comparison)
  2. Run trajectory simulation with matching motor and launch conditions
  3. Compare predicted apogee against both RASAero prediction and actual flight data
- Target: < 5% average apogee error across the published comparison set
- Store comparison results as a tracking dashboard

---

### 10e. Open-Source Cross-Validation Tools

#### MAPLEAF (University of Calgary, 2021)

**What it provides:** Open-source Python 6-DOF rocket simulation validated against flight data, wind tunnel tests, NASA simulators, Missile DATCOM, Aeroprediction, OpenRocket, and RockSim.

**Access:** GitHub (github.com/henrystoldt/MAPLEAF). Free.

**Implementation:**
- Run MAPLEAF on the 5 standard validation geometries at matching Mach/AoA points
- Compare MAPLEAF's aero coefficients against OR+ — disagreements highlight potential bugs in either tool
- Study MAPLEAF's supersonic aero module for alternative implementation approaches
- Particularly useful for cross-checking Phase 9b (6-DOF dynamics) since MAPLEAF already implements full 6-DOF

---

#### OpenFOAM Rocket Aerodynamic Toolchain (TUM, 2023)

**What it provides:** Complete open-source CFD pipeline for rocket aero analysis. Covers subsonic/transonic/supersonic. Outputs CSV files with Cd, CNa, Cm vs AoA and Mach. Validated against wind tunnel data.

**Access:** GitHub (github.com/WyllDuck/OpenFOAM-ToolChain-for-Rocket-Aerodynamic-Analysis). Free.

**Implementation:**
- This is the best path to generating **CFD reference data for arbitrary geometries** without writing your own CFD pipeline
- Run the toolchain on each of the 5 standard validation geometries
- Generate Cd/CNa sweeps at M 0.5, 0.8, 1.0, 1.2, 1.5, 2.0, 3.0, 5.0
- Store results as `core/src/test/resources/validation/cfd/`
- CFD data fills the gap where wind tunnel data isn't available (uncommon geometries, specific Mach points)
- The wind-tunnel-validated methodology provides higher confidence than running raw CFD without validation

---

#### Independent Barrowman Implementations

Two independent implementations exist for cross-validation:
- **Foley (Edinburgh, 2021):** MATLAB implementation including Busemann 2nd-order airfoil theory for supersonic fins (GitHub: liamfoley123/Aerodynamic-Simulation-of-a-Rocket)
- **Open Aerospace:** Pure Python Barrowman implementation including supersonic calculations (GitHub: open-aerospace/barrowman, GPLv3)

**Usage:** When implementing Phase 6–8 changes, run the same test case through these independent codes. Agreement builds confidence; disagreement identifies bugs. The Foley implementation is especially relevant for validating supersonic fin calculations.

---

### 10f. Modern Correlations & Methods

#### Meador & Smart — Improved Reference Enthalpy Method (2005)

**What it provides:** Improved reference temperature/enthalpy equations for both laminar and turbulent compressible boundary layers. More accurate than the original Eckert method at high Mach, particularly for non-adiabatic walls.

**Source:** Meador, W.E. & Smart, M.K. (2005). "Reference Enthalpy Method Developed from Solutions of the Boundary-Layer Equations." AIAA Journal, 43(1).

**Implementation:**

File: `BarrowmanDragCalculator.java` — reference temperature computation

Replace the Eckert T* formula with the Meador-Smart improvement:
```java
// Meador-Smart reference enthalpy (Ref NEW)
// Improved over Eckert at M > 3 and non-adiabatic walls
double T_star = T_e * (0.45 + 0.55 * Tw_Te + 0.16 * r * (gamma - 1.0) / 2.0 * mach * mach);
```

The difference from Eckert is subtle but systematic at M > 3: Meador-Smart gives ~2–3% lower T*, which propagates to ~1–2% lower Cf. This compounds over the full body length.

---

#### Syvertson & Dennis — 2nd-Order Shock-Expansion (NACA-TR-1328, 1957)

**What it provides:** Second-order shock-expansion method for zero-lift pressure distributions and normal-force derivatives on bodies of revolution. More accurate than the first-order method currently planned for Phase 2a ogive wave drag.

**Source:** Freely available from PDAS (pdas.com/refs/rep1328.pdf) and NTRS.

**Implementation:**

The current Phase 2a shock-expansion implementation is first-order (march downstream, apply PM expansion or oblique shock at each strip). The Syvertson-Dennis 2nd-order correction adds:

```java
// 2nd-order correction to shock-expansion pressure coefficient
// Accounts for streamline curvature and body curvature interaction
double Cp_2nd = Cp_1st + deltaP_curvature;
// deltaP_curvature = f(local body curvature, local Mach, surface angle change rate)
```

A 2024 Aeronautical Journal study ("Evaluation of Reduced-Order Models for Supersonic/Hypersonic Bodies") confirmed that SOSE (second-order shock-expansion) provides **exceptional accuracy** with ~20 elements per streamline vs hundreds of thousands of CFD points. This validates SOSE as the optimal method for ogive wave drag — accurate, fast, no CFD needed.

**Integration:** Upgrade `SymmetricComponentCalc.java` shock-expansion loop from 1st to 2nd order. The correction is small at moderate Mach but significant at M > 3 for slender ogives (5–10% improvement in Cd accuracy).

---

#### Hopkins & Inouye — Van Driest II Tabulated Charts (NASA TN D-6945, 1972)

**What it provides:** Complete tabulated charts for turbulent skin friction via Van Driest II method. Mach 0–10, Re 10⁵–10⁹, wall temperature ratios 0.2–1.0.

**Access:** Freely available from NTRS.

**Implementation:**
- Digitize the charts into a 3D lookup table: `Cf(M, Re, Tw/Te)`
- Use as both a validation target AND a fast-lookup alternative to computing Van Driest II analytically
- Bilinear interpolation on the table is faster than evaluating the transformation equations
- Store as `core/src/main/resources/aerodynamics/vandriest2_cf_tables.csv`

---

#### Hansen — High-Temperature Air Properties (NASA TR R-50, 1959)

**What it provides:** Closed-form approximations for air properties from 500–15,000 K at 0.0001–100 atm. Includes effective gamma, speed of sound, viscosity, and thermal conductivity as functions of temperature and pressure.

**Access:** Freely available from NTRS.

**Implementation:**
- Direct lookup tables or curve fits for Phase 4c real-gas effects
- Covers the temperature range relevant to stagnation temperatures up to Mach 10+
- The effective gamma data validates the Einstein vibrational model already implemented in `AtmosphericConditions.java`
- For M > 7 where dissociation matters, Hansen provides the dissociation-corrected gamma values that the current model approximates

---

#### NASA/CR-2012-217475 — Missile Aerodynamics for Ascent and Re-entry (2012)

**What it provides:** Aerodynamic force/moment equations for 6-DOF missile simulation including static coefficients and **damping derivatives** at supersonic speeds.

**Access:** Freely available from NTRS.

**Implementation:**
- Reference equations for Phase 8a (dynamic stability / pitch damping)
- Provides Cmq and Cm_alpha_dot formulations that OpenRocket currently lacks
- These damping derivatives are critical for predicting dynamic stability — a rocket can be statically stable but dynamically divergent near M=1

---

### 10g. Accuracy Studies & Known Gaps

#### KTH Thesis — OpenRocket Drag Overprediction (2024)

**What it provides:** Rigorous comparison of OpenRocket vs Ansys Fluent CFD for the "Mjolnir" rocket. Found OpenRocket **overpredicts drag by 12–73%** across Mach 0.2–3.0 and altitudes up to 10 km. Developed multivariate correction correlations achieving 0.3% RMSE against CFD.

**Access:** Freely downloadable from KTH DiVA portal.

**Implementation:**
- Quantifies exactly where and by how much OpenRocket's current aero model fails
- The 12–73% overprediction range helps prioritize which Phase 2 sub-tasks have the biggest impact:
  - Highest error at transonic (M 0.9–1.1) → confirms Phase 6e (area rule) as top priority
  - Large error at supersonic → confirms Phase 2a-2d upgrades are needed
- The correction correlations could serve as an interim improvement while physics-based models are developed:

```java
// KTH empirical correction factor (interim, until Phases 2-6 are complete)
// Cd_corrected = Cd_openrocket * correction(M, altitude)
// Remove this once physics-based models are validated
```

- Most importantly, this thesis provides a **quantified error baseline** — after implementing Phases 2–6, run the same comparison. If errors drop from 12–73% to < 10%, that's measurable progress.

---

#### AIAA 2025 — In-Flight Drag Determination Framework

**What it provides:** Framework for reconstructing drag coefficient from in-flight accelerometer, pressure, and temperature data during supersonic flight. Found features in flight data absent from simulations, including vibrational signatures and timing discrepancies at transonic regime transitions.

**Source:** AIAA Regional Student Conferences 2025, DOI: 10.2514/6.2025-104785.

**Implementation:**
- Methodology for validating OR+ against real flight data in the future
- The finding about transonic timing discrepancies is directly relevant — it suggests the transonic drag rise onset Mach (M_dd) and peak location may differ from models
- If OR+ users adopt this framework, it creates a feedback loop: fly → measure → compare → improve

---

#### Sooy & Schmidt — DATCOM vs AP98 Comparison (2005)

**What it provides:** Systematic comparison of Missile DATCOM and Aeroprediction 98 against wind tunnel data for body-wing-tail, body-tail, and other configurations. Normal force prediction had minimal error for both codes; axial force had larger variation.

**Source:** Journal of Spacecraft and Rockets, 2005, Vol. 42, No. 4.

**Implementation:**
- The wind tunnel data referenced in this paper provides additional validation targets
- Key finding: axial force (drag) is harder to predict than normal force (lift) — this is consistent with the observation that Cd accuracy is harder than CNa accuracy
- The accuracy numbers help calibrate expectations: if DATCOM and AP98 both show 5–15% Cd errors vs wind tunnel, OR+ achieving < 10% is a strong result

---

### 10h. Boundary Layer Transition & Fin Flutter

#### NATO STO-TR-AVT-240 — Hypersonic Boundary Layer Transition (2019)

**What it provides:** 400-page NATO report covering e^N method, second-mode mechanics, crossflow instability, roughness effects, and bluntness effects for boundary layer transition prediction.

**Access:** Freely downloadable from NATO STO publications.

**Implementation:**
- Authoritative reference for Phase 8c (dynamic boundary layer transition mapping)
- For engineering purposes, the report's correlation-based N-factor guidelines provide a simple transition criterion:

```java
// NATO AVT-240 simplified transition criterion
// N_crit = 5-10 depending on freestream disturbance level
// N_crit = 5 for noisy environment (amateur rocket), 8-10 for quiet (wind tunnel)
double N_crit = 6.0;  // typical for HPR launch conditions
double Re_transition = computeTransitionReynolds(mach, N_crit, surfaceRoughness);
```

- This replaces the current assumption of fully turbulent flow everywhere — more accurate for smooth rockets at moderate Re

---

#### Meijer & Dala — Fin Flutter Prediction (ICAS, 2014)

**What it provides:** Zero-order flutter prediction using shock-expansion theory + local piston theory + FEM plates. Validated against Euler CFD and experimental data. Computationally cheap.

**Access:** Freely available from ICAS archive.

**Implementation:**
- Directly relevant to Phase 9a (aeroelastic coupling)
- Provides a validated low-cost method suitable for real-time flutter boundary estimation
- The piston theory approach is fast enough for per-timestep evaluation:

```java
// Meijer-Dala flutter dynamic pressure (simplified)
// q_flutter = f(fin_geometry, material_stiffness, Mach)
double q_flutter = computeFlutterDynamicPressure(finSpan, finChord, finThickness, 
                                                  shearModulus, mach);
if (q_local > 0.8 * q_flutter) {
    warnUser("Approaching fin flutter boundary");
}
```

---

#### Simmons — Sounding Rocket Fin Flutter (AFIT, 2009)

**What it provides:** Flutter velocity prediction tool for sounding rocket fins, developed after an actual in-flight fin failure (Falcon LAUNCH V). Validated against real failure case.

**Access:** Freely available from DTIC (ADA502110).

**Implementation:**
- **Validated against a real flight failure** — this is rare and extremely valuable
- Provides flutter boundary data for actual rocket fins, not just generic airfoils
- The failure case can serve as an integration test: simulate the Falcon LAUNCH V flight and verify OR+ predicts flutter onset near the actual failure point

---

### Phase 10 Validation Gate

- **Data acquisition:** At least 3 freely available databases digitized and stored as test resources (ARL, NASA TMR, ARCAS)
- **Multi-source validation:** `MultiSourceValidationTest` reports error vs ≥ 3 independent sources per component
- **Model upgrades:** Lamb-Oberkampf base drag, Lock drag-rise, and Viswanath boattail corrections implemented and validated
- **CFD cross-check:** OpenFOAM toolchain run on ≥ 3 standard geometries, results stored as validation data
- **RASAero benchmark:** OR+ apogee predictions within 5% of actual flight data for ≥ 10 published RASAero comparison cases
- **Error quantification:** Documented improvement vs KTH thesis baseline (12–73% drag overprediction reduced to < 10%)
- **Subsonic regression:** All existing tests pass unchanged

---

## Optimization & Performance

As the aerodynamic model grows in complexity (Phases 6–10 add significant per-timestep computation), performance optimization becomes critical for interactive simulation. The following strategies keep simulation time manageable.

### Caching Strategy

**ShockGeometry caching:**
- The shock geometry pre-pass (Phase 3b) is the most expensive single computation at supersonic speeds
- Cache the `ShockGeometry` result and only recompute when:
  - Mach number changes by more than 0.01 (relative to last computation)
  - AoA changes by more than 0.1°
  - Rocket geometry changes (staging event, fairing separation)
- Between cache hits, interpolate linearly from the last two computed states
- Expected hit rate: > 90% of timesteps during smooth flight (RK4 takes small steps)

**Area rule caching (Phase 6e):**
- The cross-sectional area distribution `S(x)` depends only on geometry, not flight conditions
- Compute `S(x)` once at simulation start and after each staging event
- The wave drag integral depends on Mach (through the oblique cutting angle) — cache the integral result keyed on Mach with tolerance 0.02
- For the O(N²) double integral, precompute the `S''(x)` kernel matrix once; only the Mach-dependent weighting changes

**Component calculator results:**
- Cache per-component Cd and CNa keyed on (Mach, AoA, Reynolds) with configurable tolerance
- Most components have smooth, slowly-varying coefficients — cache tolerance of (0.01, 0.1°, 1%) gives > 80% hit rate
- Invalidate all caches on geometry change

### Lazy Evaluation

**Subsonic fast path:**
- At M < 0.8, skip all supersonic code paths entirely (already implemented: `ShockGeometry` is inert)
- Skip Chapman-Korst (use Hoerner), skip Ackeret fin drag (zero), skip Taylor-Maccoll (use TR-R-100 tables)
- This ensures subsonic simulations have zero overhead from supersonic model additions

**Conditional model activation:**
- Phase 9a aeroelastic: only compute when `q > q_threshold` (e.g., 10 kPa) — below this, deflections are negligible
- Phase 9e plume interaction: only compute when motor is burning AND `p_exit/p_ambient > 3`
- Phase 9d surrogate: use as the default path; fall back to analytical only at regime transitions or geometry changes

### Numerical Efficiency

**Shock solver convergence:**
- The oblique shock solver uses Newton-Raphson iteration; typical convergence in 3–5 iterations
- Pre-seed with the previous timestep's solution (warm start) to reduce iterations to 1–2
- For Taylor-Maccoll cone flow, cache the ODE solution for each cone angle; only recompute if Mach changes significantly

**Interpolation table precomputation:**
- Jorgensen crossflow Cd_c (Phase 6a): 12-point table, linear interpolation — negligible cost
- ESDU transonic similarity (Phase 6h): precompute the universal curve as a 50-point cubic spline at initialization
- Dahlem-Buck shape factors (Phase 6c): precompute per nose shape at initialization
- Chapman-Korst base pressure (Phase 6g): bilinear interpolation on a 20×20 grid — precompute at initialization

**Vectorized Mach sweeps:**
- For design-space exploration (Cd vs Mach plots), batch all Mach points and compute shared quantities once:
  - Atmospheric conditions for all altitudes
  - Fin geometry parameters (constant across Mach)
  - Shape factors and material properties
- Process all Mach points in a single pass through each component calculator

### Memory Management

**Object pooling:**
- `FlightConditions`, `AerodynamicForces`, `ShockGeometry` are created every timestep
- Pool and reuse these objects to reduce GC pressure (critical for Java, where GC pauses affect real-time feel)
- `ShockGeometry` internal arrays (local Mach, pressure, temperature at each station) should be pre-allocated to the maximum component count

**Array sizing:**
- Area rule `S(x)` array: allocate once at N=200 and reuse
- Shock geometry station arrays: allocate to max component count + margin, don't resize per-step
- Surrogate model input/output tensors: allocate once at initialization

### Performance Targets

| Operation | Target | Measured (2026-04-08) | Notes |
|-----------|--------|----------------------|-------|
| Single aero calc (any Mach, post-warmup) | < 50 ms | < 5 ms typical | Reused calculator, JIT-warmed |
| Supersonic M=3 absolute | < 100 ms | < 10 ms typical | ShockGeometry + component calcs |
| Subsonic ShockGeometry | < 10 μs | < 1 μs | Mach check + immediate return |
| 1000 supersonic calculations | < 30 s | ~2.5 s | Phase 5b benchmark, reused calc |
| Supersonic timestep (with area rule) | < 3 ms | N/A | Phase 6e target (not yet implemented) |
| Surrogate model query | < 0.05 ms | N/A | Phase 9d target |
| Full Mach sweep (M 0.3–5.0, 50 points) | < 100 ms | ~80 ms | For interactive plots |
| ShockGeometry cache hit | < 0.01 ms | N/A | Caching not yet implemented |
| Full trajectory simulation (launch to apogee) | < 30 s | ~15 s | Must stay interactive |

### Profiling & Monitoring

- Instrument `BarrowmanCalculator.getAerodynamicForces()` with elapsed-time tracking
- Log cache hit rates for ShockGeometry, area rule, and component calculators
- Flag any single timestep exceeding 10 ms as a performance warning
- Track GC pause frequency during simulation — if > 1 pause/second, investigate object allocation
- Regression test: `Phase5PerformanceTest` must continue to pass with each new phase addition

---

## References

16. **Jorgensen, L.H.** (1977). "Prediction of Static Aerodynamic Characteristics for Slender Bodies Alone and with Lifting Surfaces to Very High Angles of Attack." NASA TR R-474.
17. **NASA SP-8050** (1971). "Liquid Rocket Engine Nozzles." NASA Space Vehicle Design Criteria.
18. **Dahlem, V. & Buck, M.** (1966). "A Method for Predicting Zero-Lift Wave Drag of Slender Bodies of Revolution." AIAA Paper 66-505.
19. **Pitts, W.C., Nielsen, J.N. & Kaattari, G.E.** (1957). "Lift and Center of Pressure of Wing-Body-Tail Combinations at Subsonic, Transonic, and Supersonic Speeds." NACA Report 1307.
20. **Whitcomb, R.T.** (1956). "A Study of the Zero-Lift Drag-Rise Characteristics of Wing-Body Combinations Near the Speed of Sound." NACA Report 1273.
21. **ESDU 78019** (1978). "A Method for Estimating the Pressure Drag of Bodies of Revolution at Zero Incidence in the Transonic Regime." Engineering Sciences Data Unit.
22. **ESDU 77021** (1977). "Estimation of Base Pressure Coefficients at Supersonic Speeds." Engineering Sciences Data Unit.
23. **ESDU 70012** (1970). "Aerodynamic Characteristics of Rectangular Planform Controls at Transonic Speeds." Engineering Sciences Data Unit.
24. **Chapman, D.R.** (1951). "An Analysis of Base Pressure at Supersonic Velocities and Comparison with Experiment." NACA Report 1051.
25. **AP09** (Aerolab Processing, 2009). Rational function interpolation methods for aerodynamic coefficient databases.

26. **Tobak, M. & Wehrend, W.R.** (1956). "Stability Derivatives of Cones at Supersonic Speeds." NACA TN 3788.
27. **Bisplinghoff, R.L., Ashley, H. & Halfman, R.L.** (1955). "Aeroelasticity." Addison-Wesley. Ch. 6 (fin flutter and divergence).
28. **Zipfel, P.H.** (2007). "Modeling and Simulation of Aerospace Vehicle Dynamics." 2nd ed., AIAA Education Series. Ch. 4-5 (6-DOF equations of motion, quaternion integration).
29. **ESDU 66011** (1966). "Drag of Forward-Facing Steps in Supersonic Flow." Engineering Sciences Data Unit.
30. **NASA SP-8001** (1964). "Buffeting During Atmospheric Ascent." NASA Space Vehicle Design Criteria.
31. **Lamb, J.P. & Oberkampf, W.L.** (1995). "Review and Development of Base Pressure and Base Heating Correlations in Supersonic Flow." Journal of Spacecraft and Rockets, 32(1), 8-23.
32. **AFRL Missile DATCOM** (2014). Air Force Research Laboratory. Public domain source code distributed via DTIC.
33. **Silton, S.I.** (2005). "Navier-Stokes Computations for a Spinning Projectile from Subsonic to Supersonic Speeds." AIAA Journal, 43(2).
34. **Roy, C.J. & Blottner, F.G.** (2006). "Review and Assessment of Turbulence Models for Hypersonic Flows." Sandia National Labs, SAND2006-3952.
35. **Viswanath, P.R.** (1996). "Flow Management Techniques for Base and Afterbody Drag Reduction." Progress in Aerospace Sciences, 32(2), 79-129.
36. **Fleeman, E.L.** (2006). "Tactical Missile Design." 2nd ed., AIAA Education Series.
37. **Herrin, J.L. & Dutton, J.C.** (1994). "Supersonic Base Flow Experiments in the Near Wake of a Cylindrical Afterbody." AIAA Journal, 32(1).
38. **Bourdon, C.J. & Dutton, J.C.** (2001). "Planar Visualizations of Large-Scale Turbulent Structures in Axisymmetric Supersonic Base Flows." Physics of Fluids, 13(9).
39. **Lock, C.N.H.** (1946). "The Prediction of the Drag of Aerofoils and Bodies at High Subsonic Speeds." ARC R&M 2455.
40. **AGARD-AR-303** (1994). "CFD Validation for Missile Configurations." NATO Advisory Group for Aerospace Research and Development.
41. **White, F.M.** (2006). "Viscous Fluid Flow." 3rd ed., McGraw-Hill. Ch. 7 (compressible boundary layers).
42. **Nielsen, J.N.** (1960, reprinted 2011). "Missile Aerodynamics." McGraw-Hill / AIAA reprint.
43. **ESDU 77020** (1977, updated 2000s). "Drag of Bodies of Revolution at Zero Incidence at Transonic Speeds." Engineering Sciences Data Unit.
44. **ESDU 78041** (1978, updated 2000s). "Supersonic Base Drag of Cylindrical Bodies." Engineering Sciences Data Unit.
45. **Suliman, M.A. et al.** (2009). "Numerical Investigation of Base Drag Reduction." AIAA Paper.
46. **DeSpirito, J. & Heavey, K.R.** (2006). "CFD Computation of Magnus Effect for Finned Projectiles." AIAA Atmospheric Flight Mechanics Conference.
47. **NASA CR-2835/CR-2836** (1977). "Analysis and Compilation of Missile Aerodynamic Data." Volumes I and II, ~30 declassified missile configurations, M 0.2–4.63.
48. **NASA/CR-2012-217475** (2012). Watts, M.E. & McCarter, A. "Missile Aerodynamics for Ascent and Re-entry." 6-DOF force/moment equations with damping derivatives.
49. **NASA TN D-4013 / TN D-4014**. ARCAS sounding rocket wind tunnel data. M 1.5–4.63, AoA -4° to 20°.
50. **Meador, W.E. & Smart, M.K.** (2005). "Reference Enthalpy Method Developed from Solutions of the Boundary-Layer Equations." AIAA Journal, 43(1).
51. **Hopkins, E.J. & Inouye, M.** (1972). "Charts for Predicting Turbulent Skin Friction from the Van Driest Method (II)." NASA TN D-6945.
52. **Syvertson, C.A. & Dennis, D.H.** (1957). "A Second-Order Shock-Expansion Method Applicable to Bodies of Revolution Near Zero Lift." NACA-TR-1328.
53. **Hansen, C.F.** (1959). "Approximations for the Thermodynamic and Transport Properties of High-Temperature Air." NASA TR R-50.
54. **Meijer, J.J. & Dala, L.** (2014). "Aeroelastic Prediction for Missile Fins in Supersonic Flows." ICAS 2014-0423.
55. **Simmons, R.** (2009). "Aeroelastic Optimization of Sounding Rocket Fins." AFIT Thesis, DTIC ADA502110.
56. **NATO STO-TR-AVT-240** (2019). "Hypersonic Boundary-Layer Transition Prediction." NATO Science and Technology Organization.
57. **Stoldt, H. et al.** (2021). "MAPLEAF: A 6-DOF Rocket Simulator." University of Calgary. Open source.
58. **KTH Thesis** (2024). "Finding an Empirical Model for a Rocket's Drag Coefficients." KTH Royal Institute of Technology. OpenRocket vs CFD comparison showing 12–73% drag overprediction.
59. **Sooy, T.J. & Schmidt, R.H.** (2005). "Aerodynamic Predictions, Comparisons, and Validations Using Missile DATCOM (97) and Aeroprediction 98." J. Spacecraft and Rockets, 42(4).
60. **RASAero II Flight Validation Database**. Rogers, C.E. rasaero.com/comparisons-flight.htm. Published flight-vs-prediction comparisons, average error 3.38%.

1. **NACA Report 1135** (1953). "Equations, Tables, and Charts for Compressible Flow." Ames Research Staff.
2. **NASA TR-R-100** (1961). "Collection of Zero-Lift Drag Data on Bodies of Revolution from Free-Flight Investigations." Hoerner, S.F.
3. **NASA TN D-721** (1961). Devan, L. & Ashwood, P.F. "An Investigation of the Base Pressure and Base Heating Behind a Series of Bodies of Revolution at Free-Stream Mach Numbers from 1.7 to 4.0."
4. **Hoerner, S.F.** (1965). "Fluid-Dynamic Drag." Published by the author.
5. **Eckert, E.R.G.** (1955). "Engineering Relations for Friction and Heat Transfer to Surfaces in High Velocity Flow." Journal of the Aeronautical Sciences, 22(8), 585-587.
6. **Ackeret, J.** (1925). "Luftkrafte auf Flugel die mit grosserer als Schallgeschwindigkeit bewegt werden." Zeitschrift fur Flugtechnik und Motorluftschiffahrt, 16, 72-74.
7. **Anderson, J.D.** (2006). "Hypersonic and High-Temperature Gas Dynamics." 2nd ed., AIAA Education Series, Ch. 3-4.
8. **Anderson, J.D.** (2017). "Fundamentals of Aerodynamics." 6th ed., McGraw-Hill, Ch. 9, 12, 15.
9. **Allen, H.J. & Perkins, E.W.** (1951). "A Study of Effects of Viscosity on Flow over Slender Inclined Bodies of Revolution." NACA Report 1048.
10. **Lees, L.** (1955). "Hypersonic Flow." Proceedings of the 5th International Aeronautical Conference, Los Angeles.
11. **Sutherland, W.** (1893). "The Viscosity of Gases and Molecular Force." Philosophical Magazine, 5(36), 507-531.
12. **Barrowman, J.S.** (1967). "The Practical Calculation of the Aerodynamic Characteristics of Slender Finned Vehicles." M.S. Thesis, Catholic University of America.
13. **Galejs, J.** (1970). Extension of Barrowman method for body lift on axisymmetric bodies.
14. **USAF DATCOM** (1978). "Data Compendium: Stability and Control." Wright-Patterson AFB. Sections 4.6 (base drag), 5.2 (body aerodynamics).
15. **US Standard Atmosphere** (1976). NOAA/NASA/USAF. Temperature, pressure, and density profiles 0-1000 km.

---

## Test Coverage

**Full suite: 534 tests, 0 failures** (verified 2026-04-08)

| Test Class | Tests | Covers |
|-----------|-------|--------|
| `NormalShockRelationsTest` | 39 | Normal shock jump conditions, pressure/temperature/density ratios (NACA 1135) |
| `ObliqueShockSolverTest` | 7 | Oblique shock, cone shock (Taylor-Maccoll), detached shock fallback |
| `PrandtlMeyerExpansionTest` | 17 | PM function, inverse PM, downstream Mach, pressure ratios |
| `FlightConditionsTest` | 15 | Beta factor, transonic Hermite smoothing, high-Mach asymptote |
| `EckertSkinFrictionTest` | 13 | Reference temperature, Reynolds correction, compressibility reduction |
| `BaseDragModelTest` | 23 | Devan-Ashwood, transonic peak, boattail factor, C1 continuity |
| `WaveDragPhase2ATest` | 17 | Taylor-Maccoll cone validation, ogive shock-expansion, smoothness, fineness ratio |
| `FinWaveDragTest` | 35 | Ackeret formula, sweep correction, blending, thickness scaling |
| `TransonicDragRiseTest` | 27 | Drag divergence Mach, C1 continuity, drag rise shape |
| `ShockGeometryTest` | 19 | Pre-pass computation, station interpolation, subsonic passthrough |
| `Phase3StabilityTest` | 32 | Body CNa/CP supersonic correction, fin local flow, Mach sweep |
| `Phase4HypersonicTest` | 40 | Newtonian theory, Cp_max, effective gamma, edge cases (M=0.999-10) |
| `SupersonicBaselineTest` | 66 | Regression baselines, dCd/dM bounded (5 geometries × 235 Mach steps) |
| `Phase5ValidationTest` | 60 | Analytical solutions, Mach/AoA/geometry sweeps, cross-model consistency |
| `Phase5PerformanceTest` | 5 | Single calc speed (<50ms), throughput (1000 in <30s), subsonic passthrough |
| `Phase5SimulationTest` | 8 | Full trajectory (Estes Alpha III), regime transitions, CP bounds, warnings |
| `BarrowmanCalculatorTest` | 20 | Full rocket integration (original subsonic) |
| `FinSetCalcTest` | 10 | Fin CNa and CP (original) |
| `SymmetricComponentCalcTest` | 3 | Nose drag (original) |
| `LookupTableDragCalculatorTest` | 12 | CSV-based drag calculator |
| `LookupTableStabilityCalculatorTest` | 10 | CSV-based stability calculator |
| `MachAoALookupTest` | 5 | Mach/AoA interpolation utility |
| `RailButtonCalcTest` | 1 | Rail button aerodynamics |

| **Phase 6 (planned)** | | |
| `JorgensenCrossflowTest` | ~10 | Mach-dependent Cd_c, body CNa improvement |
| `PowerOnBaseDragTest` | ~12 | Base drag reduction during burn, burnout transition |
| `DahlemBuckWaveDragTest` | ~10 | Shape factors, fineness ratio scaling, nose type ordering |
| `RationalBlendTest` | ~12 | AP09 blending, asymptotic behavior, C1 continuity |
| `TransonicAreaRuleTest` | ~18 | S(x) computation, Sears-Haack, fin interference drag |
| `PittsNielsenKaattariTest` | ~12 | K_WB/K_BW Mach correction, transonic blend |
| `ChapmanKorstBaseDragTest` | ~12 | BL momentum thickness, base pressure, boattail effect |
| `TransonicSimilarityTest` | ~12 | Universal curve, CNa peak, sweep correction |

| **Phase 7 (planned)** | | |
| `MultiBodyInterferenceTest` | ~15 | Strap-on shocks, asymmetric lift, drag penalties |
| `RingFinSupersonicTest` | ~10 | Shock swallowing, Kantrowitz limit, spillage drag |
| `ProtuberanceTest` | ~12 | 3D isolated shock drag, side-force generation |
| `InterstageStepTest` | ~8 | Forward steps, separated flow bubble drag |

| **Phase 8 (planned)** | | |
| `PitchDampingDynamicTest` | ~20 | $C_{m_q}$ transonic drop, dynamic stability limits |
| `ShockBoundaryLayerTest` | ~12 | Fin root SBLI separation, CNa knockdown |
| `BoundaryLayerTransitionTest` | ~15 | Transition length, roughness-driven early transition |
| `AsymmetricVortexAoATest` | ~10 | High AoA > 20 deg, stochastic side force generation |

| **Phase 9 (planned)** | | |
| `AeroelasticFinTest` | ~12 | Fin deflection under load, CNa knockdown vs q, flutter margin |
| `SixDofInertiaTest` | ~15 | Off-axis CG, full inertia tensor, coupled roll-yaw |
| `CustomAtmosphereTest` | ~10 | Sounding data ingestion, Mach/Re recalculation |
| `SurrogateModelTest` | ~8 | NN/GP query, accuracy vs analytical, interpolation bounds |
| `PlumeInteractionTest` | ~12 | Overexpanded plume BL separation, fin effectiveness loss |

| **Phase 10 (planned)** | | |
| `LambOberkampfBaseDragTest` | ~8 | Re-dependent base drag, Herrin-Dutton validation |
| `VanDriestSkinFrictionTest` | ~8 | Van Driest II vs Eckert, NASA TMR DNS validation |
| `DATCOMValidationTest` | ~15 | Component-level Cd/CNa vs Missile DATCOM 2014 |
| `ARLWindTunnelValidationTest` | ~10 | Silton/DeSpirito measured data comparison |
| `MultiSourceValidationTest` | ~20 | Error matrix across all sources |
| `ARCASValidationTest` | ~8 | ARCAS sounding rocket wind tunnel data |
| `FlightDataValidationTest` | ~10 | RASAero published flight comparisons |

**Total: 534 implemented (all passing) + ~340 planned (Phase 6-10) aerodynamic tests** covering every model from subsonic through hypersonic complex-body dynamics.

