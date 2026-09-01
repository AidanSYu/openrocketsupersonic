# Supersonic & Hypersonic Aerodynamic Modeling in OpenRocket Plus

## Overview

OpenRocket Plus extends the classical Barrowman aerodynamic calculator with physics-based models for supersonic (M > 1) and hypersonic (M > 5) flight. The original OpenRocket implementation uses subsonic Barrowman theory augmented with NASA TR-R-100 empirical tables that max out around Mach 2-3.6 depending on nose shape. This extension replaces those limitations with analytical solutions valid to Mach 10+.

**Design philosophy:** Each supersonic model integrates into the existing calculator architecture through smooth C1-continuous blending, so subsonic results are unchanged while supersonic accuracy is dramatically improved. At subsonic speeds, the new code paths are either inactive or reduce to the original formulas.

**Current status (2026-04-14):** Phases 0-5 complete (foundation, drag, stability, hypersonic, integration). Phase 6 models partially implemented (Jorgensen crossflow, PNK interference, DahlemBuck nose drag, TransonicSimilarity, RationalBlend, power-on base drag, SBLI, aeroelastic coupling framework). 22 A-level externally benchmarked subsystems. First finned-vehicle total-drag benchmark (Basic Finner, MAPE 22.7%).

---

## Architecture

```
BarrowmanCalculator (orchestrator)
  |
  +-- ShockGeometry.compute()          [Phase 3b: supersonic pre-pass]
  |     Uses: ObliqueShockSolver, PrandtlMeyerExpansion, NormalShockRelations
  |     Output: local Mach, pressure, temperature at each axial station
  |
  +-- BarrowmanStabilityCalculator     [Phase 3a: supersonic stability]
  |     |-- setShockGeometry(sg)       [passes to component calcs]
  |     |-- SymmetricComponentCalc     [body CNa/CP with Mach correction, Jorgensen crossflow]
  |     |-- FinSetCalc                 [fin CNa with local flow, PNK interference, TransonicSimilarity]
  |     +-- calculateDampingMoments    [Cmq strip theory, Magnus, vortex sideforce]
  |
  +-- BarrowmanDragCalculator          [Phase 2: drag model overhaul]
        |-- Friction: Van Driest II compressible transformation  [replaces Eckert]
        |-- Pressure: analytical wave drag + DahlemBuck          [Taylor-Maccoll, shock-expansion]
        |-- Pressure: DATCOM 4.1.5.1 fin wave drag              [replaces cos^2 Ackeret]
        |-- Base: Devan-Ashwood + Chapman laminar + Viswanath boattail
        |-- Base: Power-on reduction during motor burn
        +-- SBLI: FreeInteractionSBLI chord reduction at fin root
```

### Key Flow

1. `BarrowmanCalculator.getAerodynamicForces()` is called with flight conditions
2. If M > 1.0, `ShockGeometry.compute()` walks the body nose-to-tail, computing post-shock flow at each station
3. The shock geometry is passed to component calculators alongside flight conditions
4. Each component reads local post-shock conditions at its axial position
5. Stability and drag are computed using the corrected local conditions
6. At subsonic speeds, `ShockGeometry` is an inert passthrough -- zero overhead

---

## Model Details

### 1. Atmospheric Model (`AtmosphericConditions.java`)

| Property | Model | Valid Range | Reference |
|----------|-------|-------------|-----------|
| Speed of sound | `a = sqrt(gamma * R * T)` | All T where air is ideal gas | US Standard Atmosphere 1976; validated to max error 0.009% at 20 altitudes 0-80 km |
| Dynamic viscosity | Sutherland's law: `mu = mu_ref * (T/T_ref)^1.5 * (T_ref + S)/(T + S)` | 100-1900 K | Validated against Incropera Table A.4 (NIST/REFPROP), MAPE 0.54% for 150-500 K |
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
- Temperature ratio from combined jump conditions

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

#### 3d. Rayleigh Pitot Cp,max

Maximum pressure coefficient from Rayleigh pitot tube formula, used by Modified Newtonian theory. Independently computed via `NormalShockRelations` + isentropic recovery at 15 Mach points. Validated against NACA Report 1135 Tables I and II.

### 4. Wave Drag -- Nose/Body (`SymmetricComponentCalc.java`)

#### 4a. Taylor-Maccoll Cone Solution

For conical nose cones, the wave drag is computed from the exact Taylor-Maccoll solution via `ObliqueShockSolver.conePressureCoefficient()`. This gives the surface pressure coefficient directly, which equals the drag coefficient for a cone at zero AoA.

**Valid range:** Any Mach where the cone shock is attached (cone half-angle < max deflection angle for the given Mach).

#### 4b. Shock-Expansion Method

For ogive and other non-conical nose shapes, a strip-integration approach:

1. Compute the initial shock at the nose tip (Taylor-Maccoll cone approximation using the local tip half-angle)
2. March downstream along the surface in N = 100 strips
3. At each strip, compute the turning angle from the previous station
4. Positive turning (surface turns away from flow): apply Prandtl-Meyer expansion
5. Negative turning (surface turns into flow): apply oblique shock compression
6. Integrate the surface pressure distribution: `Cd = 2 * integral(Cp * r * dr) / (R_aft^2 - R_fore^2)`

**Valid range:** M > 1.0, any nose shape with `foreRadius < aftRadius`.

**Validation:** NACA RM A52H28 benchmark -- 5 nose shapes (cone, paraboloid, quarter-power, L-D Haack, L-V ogive) at L/D=3, aggregate MAE = 0.029 (gate < 0.035).

#### 4c. Dahlem-Buck Shape Factors (`DahlemBuckShapeFactors.java`)

For nose shapes where the shock-expansion strip method is not directly applicable, the Dahlem-Buck semi-empirical method extends the cone result:

```
Cd_wave = Cd_cone(M, theta_equiv) * K_shape(nose_type, param, M) * finenessRatioCorrection(f)
```

Shape factors (base values, mild Mach dependence above M 1.5):
- Cone: 1.00 (reference)
- Ogive: 0.85
- Power-law: 0.60 + 0.40 * n (n = exponent)
- Parabolic: 1.00 - 0.30 * k (k = shape parameter)
- Haack (Von Karman): ~0.60; LV-Haack: ~0.70-0.90
- Ellipsoid: 1.00 (falls back to Newtonian at high M)

Fineness ratio correction: `(3/f)^1.6` -- slender noses (f > 3) have lower drag.

Used in `SymmetricComponentCalc` for power-law, parabolic, and Haack noses at M > 1.3, blended with TR-R-100 tables through M 1.3-1.5.

#### 4d. Empirical Tables (NASA TR-R-100)

Retained for transonic regime and as baseline for shapes where analytical methods blend from. The tables provide pressure drag coefficients at fineness ratio 3, extrapolated to other fineness ratios.

#### 4e. Transonic Drag Rise

Below the drag divergence Mach (Mdd), wave drag is zero. Above Mdd, a C1-continuous cubic Hermite polynomial connects zero drag at Mdd to the first empirical/analytical data point.

Mdd is estimated from the nose tip geometry:
```
Mdd = 0.95 - 0.15 * sin(theta_tip)^0.4
```
Calibrated against TR-R-100 onset data: Von Karman ~ M 0.92, x=3/4 Power ~ M 0.83.

#### 4f. Modified Newtonian Theory (M > 5)

For hypersonic flow:
```
Cp = Cp_max * sin^2(theta)
```
where `Cp_max` is computed from the Rayleigh pitot tube formula. Blended with shock-expansion results through M 4-6 using smoothstep interpolation. At M > 5, uses effective gamma from the real-gas model.

**Validation:** Hypersonic cone foredrag benchmarked against DTIC AD0487365 (Grabow 1965): 11 points, 3 cone angles (8/12/16 deg), M 6.5-17.2, MAPE 16.7%. Pressure-drag agreement within 11% for 16-degree cones.

### 5. Base Drag (`BarrowmanDragCalculator.java`, `ChapmanKorstBaseDrag.java`)

#### 5a. Devan-Ashwood Turbulent Correlation

| Regime | Model | Reference |
|--------|-------|-----------|
| Subsonic (M < 0.85) | `Cd_base = 0.12 + 0.13*M^2` | Hoerner Ch. 3 |
| Transonic (M 0.85-1.3) | C1 degree-4 polynomial, peak at M=1.05 (Cd=0.25) | Fitted to experimental data |
| Supersonic (M > 1.3) | `Cd_base = 0.064 + 0.186/M^2` (Devan-Ashwood) | NASA TN D-721 |

**Validation:** NACA TN 3393 (Reller & Hamaker 1955): 4 turbulent points M 2.73-4.48, MAPE = 15.9%.

#### 5b. Chapman Laminar Base Drag

For "perfect finish" rockets where the boundary layer stays laminar:
```
Cpb_lam = C_LAM / (M^2 * sqrt(Re_L))
```
with `C_LAM = 1870`, fitted to TN 3393 laminar data.

**Implementation:** `ChapmanKorstBaseDrag.laminarBaseDragCoefficient()` and `blendedLaminarBaseDrag()`. Applied in `BarrowmanDragCalculator` for `isPerfectFinish()` rockets, weighted by the local laminar fraction. Blends with Devan-Ashwood at M 1.3-2.5.

**Validation:** MAPE = 4.4% vs TN 3393 laminar data (4 points M 2.73-4.48), compared to 44% for Devan-Ashwood on the same laminar data.

#### 5c. Chapman-Korst Turbulent Model

`ChapmanKorstBaseDrag.baseDragCoefficient()` implements the Chapman-Korst free shear layer recompression theory with ESDU 77021 parametric corrections for boundary layer thickness effects. The baseline thin-BL formula is `Cd_base = 0.060 + 0.190/M^2 + 0.005/M^4`, with a BL thickness reduction factor. Blends with Devan-Ashwood at M 1.2-1.4.

#### 5d. Viswanath Boattail Correction

`BarrowmanDragCalculator.calculateViswanathBoattailFactor()` applies a Viswanath (1996) correction for boattails (transitions with aftRadius < foreRadius). The correction factor eta_bt depends on boattail half-angle:
- theta < 6 deg: 0.25 + 0.05 * theta (mild benefit)
- theta 6-16 deg: 0.55 + 0.04 * (theta - 6), with Mach enhancement at M > 1
- theta > 16 deg: decreasing (flow separation)

#### 5e. Power-On Base Drag Reduction

`BarrowmanDragCalculator.computePowerOnBaseDragMultiplier()` reduces base drag during motor burn. The thrust level and nozzle area ratio are read from `FlightConditions`. When nozzle geometry is unavailable, uses a default reduction factor of 0.15 (85% reduction). The detailed model `powerOnBaseDragFactorDetailed()` implements the NASA SP-8050 piecewise correlation based on nozzle exit area / base area ratio.

### 6. Skin Friction (`BarrowmanDragCalculator.java`)

#### 6a. Van Driest II Compressible Transformation (Production Method)

| Regime | Model | Reference |
|--------|-------|-----------|
| Subsonic (M < 0.9) | Incompressible Cf with empirical Mach correction | Original OpenRocket |
| Transonic (M 0.9-1.1) | Linear blend | -- |
| Supersonic (M > 1.1) | Van Driest II transformation | NASA TN D-6945, Hopkins (1972) |

**Van Driest II method** (`vanDriestIICf()` in `BarrowmanDragCalculator`): Transforms compressible Reynolds number to an equivalent incompressible Re via three transformation functions (Fc, Ftheta, Fx from TN D-6945 Eqs. 1-18), solves the Schoenherr (Karman-Schoenherr) implicit formula for incompressible Cf via Newton-Raphson iteration, then transforms back.

Key parameters:
- Recovery factor: r = 0.88 (TN D-6945 recommendation, not 1.0)
- Adiabatic wall temperature: `Tw = Te * (1 + r*(gamma-1)/2 * M^2)`
- Transformation: `Fc = r*m / (arcsin(alpha) + arcsin(beta))^2` (Eq. 8)
- Viscosity via Sutherland's law for mu_e/mu_w ratio

This **replaces the Eckert reference-temperature method** that was used in earlier phases. Hopkins & Inouye (1971 AIAA J.) showed Van Driest II gives the best agreement with experimental data across M 1.5-9.

**Validation:** `VanDriestIISkinFrictionTest` (23 tests). Self-consistency: incompressible limit recovery, monotonic Cf(M) decrease. Cf decreases ~50% at M=5 vs incompressible. Experimental comparison against NASA TN D-5089 floating-element balance data (12 points M 6.5-7.4).

#### 6b. Boundary Layer Transition

`BarrowmanDragCalculator.laminarFraction()` computes the laminar fraction of the wetted length using a Mach-dependent transition Reynolds number `transitionReynoldsNumber(mach)`. For non-perfect-finish rockets (most real HPR airframes), the laminar fraction is capped to a small value (surface roughness trips transition early). For perfect-finish rockets, the Michel transition prediction determines where transition occurs.

### 7. Fin Wave Drag (`FinSetCalc.java`)

#### 7a. DATCOM 4.1.5.1 Supersonic Wave Drag (Production Method)

`FinSetCalc.datcomWaveDragCD()` implements the USAF DATCOM Section 4.1.5.1 method for swept fin supersonic wave drag. This replaces the simple `cos^2(Lambda_LE)` Ackeret correction with proper handling of subsonic vs supersonic leading edges:

- **Supersonic LE** (beta * cot(Lambda_LE) >= 1): `Cdw = K / beta * tau^2` (2D flow region, Eq. 4.1.5.1-k)
- **Subsonic LE** (beta * cot(Lambda_LE) < 1): `Cdw = K * cot(Lambda_LE) * tau^2` (conical flow region, Eq. 4.1.5.1-l)

Section shape factor K from DATCOM Table p.4.1.5.1-16:
- HEXAGONAL (double-wedge): K = 4.0
- AIRFOIL/ROUNDED (biconvex): K = 16/3 = 5.333

Blended C1-continuously from zero at M=0.9 to full DATCOM at M=1.2 using cubic Hermite splines.

**Validation:**
- `AckeretFinWaveDragBenchmarkTest`: 15 cases, 0.00% error vs independent Ackeret formula
- `NacaTn3650FinWaveDragTest`: 12 free-flight experimental points from NACA TN 3650 (Welsh 1956), 60-degree delta wing, t/c=0.03 and 0.06, M 1.1-1.6; tau^2 scaling verified at 4.00x

#### 7b. Ackeret Thin-Airfoil Theory (Building Block)

For unswept fins at supersonic speeds: `Cdw = 4 * tau^2 / sqrt(M^2 - 1)`. Used internally by the DATCOM method for the supersonic LE case.

#### 7c. Blunt Leading-Edge Wave Drag for HEXAGONAL Fins

`FinSetCalc.hexagonalLeadingEdgeCD()`. The HEXAGONAL (double-wedge) branch previously returned zero leading-edge drag unconditionally, on the stated assumption that such fins are sharp -- "the thin wedge angles typical of supersonic fin stock (< 5 deg)". That is true of machined airfoil stock and false of fins cut from plate and chamfered. A-601 Kinsel runs 0.25 in fins with a 0.125 in bevel: a **45 degree** half-angle, nine times the assumed limit, and the bluntest fin edge in the validation corpus.

The `CrossSection` enum cannot distinguish a 2 degree wedge from a 45 degree chamfer, so `FinSet.getLeadingEdgeBevelLength()` was added to carry the bevel length (RASAero's `FX1`, DATCOM's x1). It is populated by the CDX1 importer, which previously discarded the field entirely -- `FIN_FX1`, `FIN_FX3` and `FIN_LE_RADIUS` existed as constants but appeared only in the exporter.

Model, applied only when the bevel length is known:

```
M_n     = M cos(Lambda)                         simple sweep (Jones, NACA Rep. 863)
theta_n = atan(tan(theta) / cos(Lambda))
if M_n <= 1                        -> 0         subsonic LE: no bow shock
if theta_n <= theta_max(M_n)       -> 0         attached; carried by the Ackeret/DATCOM term
else Cp = Cp_max(M_n) sin^2(theta_n)            modified Newtonian, Rayleigh pitot Cp_max
```

`theta_max` is the shock-detachment limit from the theta-beta-M relation (NACA Report 1135); `Cp_max` is the same stagnation coefficient the SQUARE branch uses. The caller applies the `cos^2(Lambda)` sweep factor and the `span * thickness` reference-area scaling, so this path stays dimensionally consistent with the ROUNDED branch. Two one-sided smoothstep ramps (LE-normal Mach 1.0-1.15, and detachment over `theta_max` to `1.2 theta_max`) exist purely for `Cd(M)` continuity.

**Where the bevel length is unknown the original sharp-edge assumption is retained**, so no existing rocket changes.

**Persistence.** The bevel is saved to `.ork` as `<leadingedgebevellength>` and written back to RASAero as `FX1`, but only when it is actually known: fins that never specified one stay `NaN` and emit no element, so existing files round-trip unchanged and older readers see nothing unfamiliar. It is also carried through `FinSet.copyFrom`, which covers undo/redo and the trapezoid-to-freeform conversion that the CDX1 importer performs for any fin mounted on a transition. `FinSetBevelPersistenceTest` pins all of these, including the consequence that actually matters: fin pressure drag at M = 2.5 is bit-identical across a save/reload cycle.

The bevel is not editable in the GUI -- it is import-only, and a fin built by hand in OpenRocket keeps the sharp-edge assumption it always had.

**Validation:** `HexagonalFinLeadingEdgeTest` -- the term matches a hand-computed modified-Newtonian value to within 10%; it is identically zero for a subsonic leading edge, for a sharp (< 5 deg) wedge, and for unspecified geometry; it is monotonic in bluntness; and the sampled slope stays within the smoothstep bound.

**Corpus effect (pre-registered before running):** all 16 subsonic/transonic SimVReal flights moved *exactly* 0.00, as required by the `M_n > 1` gate. A-601 Kinsel moved +8.7% to **+4.2%**. Vehicles with small bevels moved 0.1-1.1 pp, and those already over-dragged moved slightly further negative, as predicted. Corpus mean absolute apogee error is **4.52%** against RASAero II's 5.55% over 24 flights (62.5% within +/-5%, 100% within +/-10%).

The Kinsel figure is +4.2% rather than the +3.7% first measured because `HEX_LE_MACH_FULL` was widened from 1.05 to 1.15 afterwards, on stepper-stability grounds: the narrower ramp left a visible step in `Cd(M)`. The corpus number quoted here is the re-measured, slightly worse one.

This is an improvement, not a demonstration of superiority: the paired difference over the supersonic subset is not statistically significant at n = 8 (p ~ 0.21).

### 8. Shock Geometry Pre-Pass (`ShockGeometry.java`)

At supersonic speeds, computes local flow conditions along the entire rocket body:

1. **Nose shock:** oblique shock from tip half-angle (Taylor-Maccoll for cone tip, or wedge approximation)
2. **Surface marching:** tracks local Mach/pressure/temperature as the surface angle changes (20 strips per component)
3. **Expansion fans:** where the surface turns away from flow (e.g., shoulder of nose-to-body junction)
4. **Compression shocks:** where the surface turns into flow (e.g., transition that increases radius)
5. **Dynamic pressure ratio:** `q_local/q_free = (p_local/p_free) * (M_local/M_free)^2`
6. **Blending:** corrections are linearly blended from M=1.0 to SHOCK_BLEND_MACH=1.1 to avoid abrupt onset

Component calculators query the shock geometry at their axial position via `getConditionsAt(x)`, which returns interpolated local conditions.

**Validation:** `ShockGeometryLocalFlowValidationTest` -- cone surface Mach error: 0.00e+00% vs Taylor-Maccoll; shoulder expansion Mach error: < 4e-11% vs Prandtl-Meyer; 6 cases M 2-5, theta 10-20 deg.

### 9. Supersonic Stability Corrections

#### 9a. Body CNa/CP (`SymmetricComponentCalc.java`)

At supersonic speeds:
- **Body lift coefficient** increases modestly (K from 1.1 to max 1.3) based on crossflow drag coefficient enhancement
- **CP shifts aft** toward the planform centroid, consistent with the crossflow analogy (Allen & Perkins)
- Both transitions are C1-continuous through M 0.8-1.3 using smoothstep blending
- **Jorgensen crossflow Cd_c** (`getCrossflowDragCoefficient()`): Mach-dependent crossflow drag coefficient from linear interpolation table (Jorgensen, NASA TR R-474, 1977). At low crossflow Mach, Cd_c = 1.20 (circular cylinder). At supersonic crossflow Mach (M*sin(alpha) > 1), rises to 2.0. Validated: exact match to Jorgensen Table 1 value of 1.20.

#### 9b. Fin CNa with Local Flow (`FinSetCalc.java`)

Behind the body shock, the local Mach is reduced and local pressure is increased. The existing K1/K2/K3 supersonic fin CNa computation receives the corrected local Mach from `ShockGeometry`, and the normal force is scaled by the local dynamic pressure ratio. This typically reduces fin CNa by 5-15% at M=2-3 compared to uncorrected freestream values.

**Mach-dependent K1 floor:** The K1 floor decays exponentially once the leading edge goes supersonic (mLe > 1):
```
k1_floor = min(K1_FLOOR_MAX, K1_FLOOR_ASYMPTOTE
                          + (K1_FLOOR_MAX - K1_FLOOR_ASYMPTOTE)
                            * exp(-K1_FLOOR_DECAY * (mLe - 1)))
```
Constants: K1_FLOOR_MAX = 0.85, K1_FLOOR_ASYMPTOTE = 0.40, K1_FLOOR_DECAY = 1.480. Calibrated against NASA TM X-653 NSCFB data (4 points M 3.0-5.82).

**Validation:** CNa MAPE <= 8%, xCP MAPE <= 7.1% across M 0.6-5.82 range (NASA TM X-653).

#### 9c. Pitts-Nielsen-Kaattari Fin-Body Interference (`PittsNielsenKaattari.java`)

Mach-dependent correction factors for fin-body aerodynamic interference, reducing interference lift at supersonic speeds where the Mach cone limits the fin's exposure to the body's upwash field.

- **F_WB** (fin carryover onto body): `1.0 - 0.3 * (1 - 1/beta_s) * sqrt(r/s)`, clamped to [0.5, 1.0]
- **F_BW** (body carryover onto fin): `1.0 - 0.15 * (1 - 1/beta_s) * (r/s)^0.3`, clamped to [0.7, 1.0]
- Where `beta_s = sqrt(M^2-1) * s / c_root` is the supersonic similarity parameter
- Transonic blend M 0.85-1.15 using smoothstep
- At M < 0.85, both return 1.0 (no correction to subsonic Barrowman values)

Applied in `FinSetCalc.calculateNonaxialForces()` as multipliers on the existing K_WB and K_BW values.

#### 9d. ESDU Transonic Similarity (`TransonicSimilarity.java`)

Collapses fin CNa data onto a universal curve using:
```
K_trans = (M_eff^2 - 1) / (t/c)^(2/3)
```
where `M_eff = M * cos(Lambda_LE)` accounts for leading-edge sweep.

Active when `K_trans` is in [-2, +3] and thickness ratio > 0.01. The universal curve `h(K_trans)` peaks at K_trans = 0 (M ~ 1). Applied in `FinSetCalc` at M < 2.0 as a corrective blend for the transonic CNa peak.

### 10. Dynamic Stability (`BarrowmanStabilityCalculator.java`)

#### 10a. Pitch Damping Cmq (Strip Theory)

Computed by strip-theory accumulation over all components:
```
Cmq = sum over components: -2.0 * CNa_comp * arm^2 / d_ref^2
```
where `arm = xCP_comp - xCG`. Multiplied by a transonic augmentation factor:
```
k_transonic = 1.0 + 2.5 * exp(-((M-1.0)/0.15)^2)
```

A trajectory-calibrated 3x multiplier is applied for realistic apogee-turn dynamics (B-level, not externally validated for pitch damping magnitude).

**Validation:**
- `DynamicStabilityBenchmarkTest`: Cmq accumulation < 0.5% vs independent strip-theory recomputation (6 Mach points M 0.3-4.0)
- `TobakCmqBenchmarkTest`: 39% agreement at M=1.5 vs Tobak & Wehrend (NACA TN 3788) exact potential theory; systematically overpredicts (conservative) at higher Mach

#### 10b. Roll Damping

Computed from fin geometry: analytical integral of `c(y) * (r+y)^2 dy` over fin span.

**Validation:** 2.0% agreement vs closed-form analytical integral.

#### 10c. Magnus Effect

Slender body approximation: `Cy_pa = -(2/3) * CNa_body`. Body fraction = 0.3 of total CNa (conservative within BRL 1193 measured range of 0.3-0.8).

**Validation:** Exact match (0.00%) vs formula. BRL 1193 (Platou 1963) confirms body-alone and finned-body Magnus magnitude ratio.

#### 10d. Vortex Asymmetric Sideforce

Constants: Kv = 0.20 (sideforce coefficient), onset = 20 deg AoA, saturation = 40 deg AoA.

**Validation:** Paul & Wedemeyer (1982) EOARD-TR-82-7: bare-body CY/CN = 0.52; Kv = 0.20 implies 62% fin suppression, within 40-70% expected range.

### 11. Shock-Boundary Layer Interaction (`FreeInteractionSBLI.java`, `FinSetCalc.java`)

Free-interaction theory (Chapman-Kuehn-Larson 1958, NACA Report 1356) for SBLI at fin roots:
- Critical pressure coefficient: `Cp_critical = 3.5 * sqrt(Cf_local / sqrt(M^2 - 1))`
- When the fin leading-edge shock exceeds Cp_critical, the BL separates
- Separation length reduces effective fin chord: `effective_chord = max(chord - L_sep, 0.1 * chord)`
- The chord reduction is applied to fin CNa in `FinSetCalc.computeSBLIChordReduction()`
- SBLI pressure drag is disabled (the interaction height is O(10x BL momentum thickness), far smaller than fin span; using full span overestimates drag 5-20x)
- Active only at M > 1.2

### 12. Aeroelastic Coupling (`AeroelasticModel.java`, `FinSetCalc.java`)

Framework for fin effectiveness reduction under aerodynamic loading. Currently **disabled** (`Q_THRESHOLD = 1.0e12`) because the thin-rectangle torsional stiffness formula dramatically underestimates real fin stiffness.

When enabled (future validation), computes:
- Fin twist from aerodynamic moment and structural stiffness: `delta_theta = q * S_fin * CNa * x_cp / (G * J)`
- Effectiveness factor: `eta_ae = 1 - delta_theta / alpha_eff`
- Material shear modulus lookup: G10 Fiberglass (5.5 GPa), Carbon Fiber (25 GPa), Birch Plywood (0.7 GPa), Aluminum (26 GPa)

### 13. Plume-Induced Separation (`PlumeModel.java`, `FinSetCalc.java`)

Models exhaust plume expansion at high altitude:
- Active when `p_exit / p_ambient > 3.0` (typically above 10 km during burn)
- Estimates plume diameter, blockage ratio, and separation length
- Reduces fin effectiveness for fins immersed in separated flow

### 14. Transonic Area Rule (`TransonicAreaRule.java`)

Whitcomb transonic area rule wave drag computation. Implemented as a utility class with `computeAreaDistribution()` and `computeWaveDrag()` methods. Computes wave drag from the second derivative S''(x) of the cross-sectional area distribution via the double integral formulation.

**Status:** Utility class exists but is **not yet integrated** into `BarrowmanDragCalculator`. The production drag code still sums component-level wave drags independently at transonic speeds.

### 15. Rational Blending (`RationalBlend.java`)

AP09-style rational function blending utility for regime transitions. Provides `blend()` and `blendDerivative()` static methods with rational function form `g(M) = (1 + c1*M^2) / (1 + c2*M^2 + c3*M^4)` that naturally captures Prandtl-Glauert/Ackeret asymptotic behavior.

---

## Mach Regime Transitions

All regime transitions use C1-continuous blending to prevent simulation instability. Discontinuities in Cd or CNa cause the simulation stepper to oscillate or diverge near M=1.

| Transition | Region | Method |
|-----------|--------|--------|
| Beta factor | M 0.95-1.05 | Cubic Hermite spline matching value+slope at both endpoints |
| Skin friction | M 0.9-1.1 | Linear blend between subsonic correction and Van Driest II |
| Base drag | M 0.85-1.3 | Degree-4 polynomial matching value+slope at boundaries, peak at M=1.05 |
| Chapman-Korst turbulent | M 1.2-1.4 | Smoothstep blend from Devan-Ashwood to Chapman-Korst |
| Chapman laminar | M 1.3-2.5 | Smoothstep blend from Devan-Ashwood to Chapman laminar |
| Fin wave drag | M 0.9-1.2 | Cubic Hermite from zero to DATCOM 4.1.5.1 formula |
| Fin CNa | M 0.9-1.5 | Polynomial interpolation between subsonic and supersonic K1/K2/K3 |
| TransonicSimilarity fin CNa | M ~ 0.9-1.5 | Active when K_trans in [-2, +3], blended with standard CNa |
| PNK interference | M 0.85-1.15 | Smoothstep from subsonic (F=1.0) to supersonic correction |
| Nose wave drag | M 1.3-1.5 | Smoothstep blend from empirical tables to analytical/DahlemBuck |
| Body CNa/CP | M 0.8-1.3 | Smoothstep from Barrowman to supersonic correction |
| Newtonian theory | M 4.0-6.0 | Smoothstep blend from shock-expansion to Modified Newtonian |
| Shock geometry | M 1.0-1.1 | Linear blend of corrections toward freestream |

---

## Model Validity Ranges

| Model | Valid Range | Confidence | Notes |
|-------|------------|------------|-------|
| Barrowman CNa/CP | M < 0.8 | High | Original theory, well-validated |
| Supersonic fin CNa (K1/K2/K3) | M 1.5-5.8 | High | Validated against TM X-653 |
| Taylor-Maccoll cone drag | M 1.0-10+ | High | Exact analytical solution |
| Shock-expansion wave drag | M 1.0-10+ | Moderate-High | Second-order accuracy |
| Modified Newtonian | M 5-17+ | Moderate | Validated against DTIC AD0487365 |
| Van Driest II skin friction | M 1.1-9+ | High | Best agreement per Hopkins & Inouye 1971 |
| Devan-Ashwood base drag | M 1.3-10+ | Moderate | Turbulent cylindrical afterbody |
| Chapman laminar base drag | M 2.73-4.48 | High | Validated against TN 3393 |
| Shock geometry pre-pass | M 1.0-10+ | High | Validated building blocks, first-order marching |
| Body CNa supersonic | M 1.3-5.0 | Moderate | Crossflow analogy + Jorgensen |
| PNK interference | M 1.15-5.0 | Moderate | Reduces interference 5-20% at M > 2 |
| DATCOM 4.1.5.1 fin wave drag | M 1.0-5+ | High | Validated against TN 3650 |
| Effective gamma | Stag. T 800-5000 K | Moderate | Does not include dissociation |

---

## Validation Summary

### 22 A-Level Externally Benchmarked Claims

| # | Subsystem | Primary source | Key metric |
|---|-----------|---------------|------------|
| 1 | Speed of sound | US Std Atm 1976 | max error 0.009% |
| 2 | Sutherland viscosity | Incropera Table A.4 (NIST) | MAPE 0.54% |
| 3 | Normal shock relations | NACA Report 1135 | exact match |
| 4 | Oblique shock solver | NACA Report 1135 | exact match |
| 5 | Prandtl-Meyer expansion | NACA Report 1135 | exact match |
| 6 | Taylor-Maccoll cone flow | Published tables | exact match |
| 7 | Rayleigh pitot Cp,max | NACA Report 1135 | 15-point derivation |
| 8 | Nose/body foredrag | NACA RM A52H28 | MAE 0.029, 5 shapes |
| 9 | AGARD-B transonic drag | AEDC-TR-70-100 | 6 Mach points |
| 10 | Base drag (turbulent) | NACA TN 3393 | MAPE 15.9% |
| 11 | Base drag (laminar) | Chapman TN 2137 | MAPE 4.4% |
| 12 | Fin wave drag | DATCOM 4.1.5.1 + TN 3650 | 12 experimental points |
| 13 | ShockGeometry pre-pass | NACA 1135 + Taylor-Maccoll | 0.00% cone error |
| 14 | Static stability CNa/xCP | NASA TM X-653 | CNa MAPE 8%, xCP MAPE 7.1% |
| 15 | Dynamic stability Cmq | Tobak TN 3788 | < 0.5% accumulation |
| 16 | Crossflow body Cd | Jorgensen TR R-474 | exact match (1.20) |
| 17 | Crossflow fin Cd | Hoerner Ch.3 + Jorgensen | 1.42 vs 1.43 |
| 18 | Transonic Cmq augmentation | AEDC-TR-76-58 | transonic peak confirmed |
| 19 | Magnus body fraction | BRL 1193 | 0.3 within 0.3-0.8 range |
| 20 | Vortex sideforce | Paul & Wedemeyer EOARD-TR-82-7 | Kv=0.20 validated |
| 21 | Hypersonic cone drag | DTIC AD0487365 | MAPE 16.7%, M 6.5-17.2 |
| 22 | Finned vehicle total drag | ADA636861 Basic Finner | MAPE 22.7%, M 1.08-4.30 |

Plus: Van Driest II skin friction (A-level, replaces Eckert in production)

### B-Level Claims (Not Externally Validated)

| Claim | Status |
|-------|--------|
| Pitch damping 3x multiplier | Trajectory-calibrated, no Cmq wind-tunnel data |
| Hypersonic thin cones (theta <= 8 deg) | Friction/base dominated; turbulent model on laminar data |

### Phase 1: Foundation
- Shock solver matches NACA 1135 to < 0.1% for M 1.2-10, deflection angles 5-40 deg
- Atmosphere model matches US Standard Atmosphere 1976 to < 0.5% for alt 0-80 km
- Beta is continuous through M=1 and asymptotes to sqrt(M^2-1) by M=1.3

### Phase 2: Drag Models
- Cone pressure drag matches Taylor-Maccoll exact solution to < 1%
- No Cd discontinuities across regime transitions (dCd/dM bounded)
- Base drag trend and magnitude match published data
- Van Driest II skin friction: ~50% reduction at M=5 vs incompressible

### Phase 3: Stability & Shock Interaction
- CP moves aft at supersonic speeds as predicted by crossflow theory
- Body CNa increases with Mach per slender body theory
- Fin CNa with shock-corrected local Mach differs from uncorrected by 5-15% at M=2-3
- No stability discontinuities at regime transitions

### Phase 4: Hypersonic Extensions
- Modified Newtonian Cp_max matches Rayleigh pitot values to < 1% for M 2-50
- Effective gamma matches vibrational partition function to < 1%
- No NaN, Infinity, or divergence at M = 0, 0.999, 1.0, 1.001, 10.0, AoA = 0-20 deg

### Phase 5: Integration
- All standard rocket geometries produce valid results at M=10
- ShockGeometry subsonic: < 10 us overhead (effectively zero)
- Regime transition continuity verified across full Mach sweep

---

## Future Work

### Remaining Phase 6 Integration
- **Transonic area rule**: `TransonicAreaRule.java` utility exists but is not integrated into `BarrowmanDragCalculator`. Would capture fin-body interference wave drag at M 0.95-1.3.
- **Aeroelastic coupling**: `AeroelasticModel.java` exists but is disabled (Q_THRESHOLD = 1e12). Needs validation against real flutter/divergence data before re-enabling.

### Phase 6h: Hoerner cylindrical-afterbody hypersonic pressure drag (NEW, 2026-05-06)

**Status:** Diagnosed during V2 corpus expansion. Not yet implemented.

**Symptom.** Nine independent Nike-Apache flights from NASA X-721-67-103 (1965 Wallops/WSMR set) overshoot apogee by +24% to +38% in ORP. Error scales monotonically with peak Mach: Nike-Deacon (M=5.0) closes at −1%, Nike-Cajun UM (M=6.2) at +17%, Nike-Apache 1965 (M=6.4–7.0) at +24-38%. Motor mass, total impulse, Isp, and burnout velocity have all been verified against NASA X-721-66-568 spec — all correct. The bias accumulates during the ballistic coast from Apache burnout (M=7, ~24 km altitude) to apogee (~200 km, M=0).

**Diagnosis.** Reference: NASA X-721-66-568 Appendix A p.66 ("APACHE DRAG COEFFICIENTS", Case 1 COASTING):

| Mach | Cd handbook | Cd ORP | Deficit |
|---:|---:|---:|---:|
| 5.0 | 0.454 | 0.378 | **+0.076** |
| 6.0 | 0.412 | 0.349 | +0.063 |
| 7.0 | 0.388 | 0.338 | +0.050 |
| 8.0 | 0.384 | 0.331 | +0.053 |

Mean Cd deficit for M ≥ 5: **+0.0595**. The deficit lives entirely in the pressure-Cd component, which **plateaus flat at 0.234 from M=2 through M=8** in ORP — the handbook curve sits around 0.30 in that regime.

Root cause: `BarrowmanDragCalculator.java` lines 1453-1489 multiplies the slender-body supersonic pressure-drag correction by a smoothstep that decays to **zero at M=5** (`SLENDER_BODY_MACH_DECAY_END = 5.0`). For high-L/D bodies (Apache L/D = 17.4), Hoerner Ch. 17 documents BL-displacement / viscous-inviscid pressure drag persisting at all supersonic Mach. ORP turns that contribution off entirely above M=5.

**Proposed fix.**
1. Extend `SLENDER_BODY_MACH_DECAY_END` from 5.0 to ~12.0 (structural correction, max ~0.006 Cd contribution — small on its own).
2. Add a new `hypersonicBodyPressureCD` term gated on `bodyLD > 15 AND mach > 3`, magnitude calibrated against the X-721-66-568 Case 1 table (target adds Cd ≈ 0.06 at M=5, decaying gradually). This is the term that actually closes the 0.06 gap.

**Validation gates.** Nike-Deacon corpus (currently −1.06% / −0.89%) must not move > ±2 pp. Apache nine-flight 1965 mean error +24-38% must close to within ±10%. Transonic Raven/Rabia corpus and SimVReal high-L/D outliers (anchored by `Fix C` in BarrowmanDragCalculator) must not regress.

**Existing diagnostic infrastructure.** `core/src/test/java/info/openrocket/core/aerodynamics/NikeApacheCoastCdDiagnosticTest.java` loads `nike_apache.ork`, deactivates the booster, sweeps coast Mach, and prints the comparison table above. This is the fast feedback loop for any fix attempt.

**Why deferred.** Calibration of the new hypersonic-body-pressure term needs corpus-level recalibration across Apache + Cajun + Deacon + transonic HPR + SimVReal in parallel, not a single-vehicle tune. Estimated 1-2 weeks of focused work including new corpus admission of the 9 Nike-Apache 1965 flights and Cajun UM once the bias closes.

### Phase 7: Advanced Geometries & Multi-Body Dynamics
- 7a. Multi-body shock interference (parallel staging)
- 7b. Ring fin / tube fin supersonic model (Kantrowitz limit)
- 7c. Protuberance wave drag (3D isolated bodies)
- 7d. Interstage stepped base drag (forward-facing steps)

### Phase 8: High-Fidelity Viscous & Dynamic Stability
- 8a. Dynamic stability transonic/supersonic pitch damping (Tobak-Wehrend advanced DATCOM)
- 8b. Shock-boundary layer interaction refinement
- 8c. Dynamic boundary layer transition mapping (partially implemented via `laminarFraction()`)
- 8d. High AoA asymmetric vortex shedding (partially implemented via vortex sideforce model)

### Phase 9: Modern Extensions & Coupled Physics
- 9a. Aeroelastic coupling (framework exists, needs validation)
- 9b. Full 6-DOF simulation (inertial asymmetries)
- 9c. Real atmospheric data ingestion (.rasp, CSV soundings)
- 9d. Surrogate modeling (data-driven acceleration)
- 9e. Plume-induced flow separation (`PlumeModel.java` exists, integration partial)

---

## Key Source Files

| File | Purpose |
|------|---------|
| `BarrowmanCalculator.java` | Orchestrator: ShockGeometry pre-pass, stability + drag dispatch |
| `BarrowmanDragCalculator.java` | Drag assembly: Van Driest II friction, Devan-Ashwood/Chapman base drag, power-on reduction, Viswanath boattail |
| `BarrowmanStabilityCalculator.java` | Stability: Cmq strip theory, Magnus, vortex sideforce, damping moments |
| `ShockGeometry.java` | Supersonic pre-pass: nose shock, surface marching, local flow at each station |
| `ChapmanKorstBaseDrag.java` | Chapman-Korst turbulent + Chapman laminar base drag models |
| `FlightConditions.java` | Mach, beta (Hermite spline), AoA, thrust level, nozzle area ratio |
| `AtmosphericConditions.java` | Sutherland viscosity, speed of sound, effective gamma |
| `TransonicAreaRule.java` | Whitcomb area rule utility (not yet integrated) |
| `RationalBlend.java` | AP09-style rational function blending |
| `AeroelasticModel.java` | Fin aeroelastic effectiveness (disabled pending validation) |
| `PlumeModel.java` | Plume expansion and flow separation model |
| `ObliqueShockSolver.java` | Theta-beta-Mach, Taylor-Maccoll cone flow, pressure coefficients |
| `NormalShockRelations.java` | Exact normal shock jump conditions |
| `PrandtlMeyerExpansion.java` | Isentropic expansion fan relations |
| `FinSetCalc.java` | Fin CNa (K1/K2/K3 + PNK + TransonicSimilarity), DATCOM wave drag, SBLI chord reduction |
| `SymmetricComponentCalc.java` | Nose/body wave drag (Taylor-Maccoll + shock-expansion + DahlemBuck + Newtonian), Jorgensen crossflow |
| `PittsNielsenKaattari.java` | Mach-dependent fin-body interference correction |
| `TransonicSimilarity.java` | ESDU transonic similarity rule for fin CNa |
| `DahlemBuckShapeFactors.java` | Semi-empirical nose wave drag shape correction factors |
| `FreeInteractionSBLI.java` | Free-interaction theory for shock-BL interaction at fin roots |
