# Peak-Mach Drag Decomposition: Outliers vs Healthy Cases

Generated: 2026-04-16
Source: `SimVRealOutlierDiagnosticTest.testGenerateAstOutlierDiagnostics()` (fresh run)
Data: `core/build/reports/simvreal-outliers/` per-case markdown + component-CD sweep CSVs

## 1. Summary Table

All values at peak-Mach flight condition (in-flight snapshot from simulation trajectory, not static sweep).

| Case | Role | M_peak | ORP err | RAS err | Cd_total | Cd_f | Cd_p | Cd_b | f% | p% | b% |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| EZI-65 J450ST | outlier | 0.61 | +16.1% | +6.3% | 0.490 | 0.221 | 0.062 | 0.207 | 45.1 | 12.7 | 42.2 |
| Thunder & Lightning | outlier | 0.55 | +17.4% | +11.5% | 0.458 | 0.267 | 0.012 | 0.179 | 58.3 | 2.7 | 39.1 |
| Raven | outlier | 1.12 | +27.5% | +5.9% | 0.917 | 0.361 | 0.202 | 0.354 | 39.4 | 22.0 | 38.6 |
| A-601 Kinsel | outlier | 2.33 | +35.1% | -3.9% | 0.350 | 0.141 | 0.066 | 0.143 | 40.4 | 18.7 | 40.9 |
| Byrum | healthy | 0.75 | +8.4% | -7.9% | 0.620 | 0.283 | 0.050 | 0.288 | 45.6 | 8.1 | 46.3 |
| CalIsp1 | healthy | 0.64 | -0.7% | -2.2% | 0.416 | 0.219 | 0.004 | 0.194 | 52.5 | 1.0 | 46.5 |

## 2. Component-Level Breakdown at Peak Mach

### 2a. EZI-65 J450ST (M=0.610, subsonic, +16.1%)

| Component | Cd | Cdf | Cdp | Cdb | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.3502 | 0.1676 | 0.0000 | 0.1826 | 71.5% |
| Nose Cone | 0.0205 | 0.0205 | 0.0000 | 0.0000 | 4.2% |
| Fin | 0.0378 | 0.0109 | 0.0189 | 0.0080 | 7.7% |
| Rail Guide | 0.0028 | 0.0000 | 0.0028 | 0.0000 | 0.6% |
| **Total** | **0.4896** | **0.2207** | **0.0624** | **0.2065** | |

Key: Body tube dominates (71.5%). Base drag on body tube = 0.1826, which is 37.3% of total.

### 2b. Thunder & Lightning (M=0.551, subsonic, +17.4%)

| Component | Cd | Cdf | Cdp | Cdb | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.3613 | 0.1903 | 0.0000 | 0.1710 | 78.9% |
| Nose Cone | 0.0322 | 0.0322 | 0.0000 | 0.0000 | 7.0% |
| Fin | 0.0184 | 0.0148 | 0.0009 | 0.0026 | 4.0% |
| Rail Guide | 0.0047 | 0.0000 | 0.0047 | 0.0000 | 1.0% |
| **Total** | **0.4581** | **0.2670** | **0.0122** | **0.1790** | |

Key: Body tube dominates even more (78.9%). Very thin fins (0.063 in) produce negligible pressure drag. Body tube base drag = 0.171, which is 37.3% of total.

### 2c. Raven (M=1.123, transonic, +27.5%)

| Component | Cd | Cdf | Cdp | Cdb | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.6005 | 0.2904 | 0.0000 | 0.3101 | 65.5% |
| Nose Cone | 0.0587 | 0.0248 | 0.0339 | 0.0000 | 6.4% |
| Fin | 0.0487 | 0.0153 | 0.0188 | 0.0146 | 5.3% |
| Rail Guide | 0.0560 | 0.0000 | 0.0560 | 0.0000 | 6.1% |
| **Total** | **0.9171** | **0.3610** | **0.2022** | **0.3539** | |

Key: Body tube = 65.5%. Body tube base drag = 0.3101 (33.8% of total). This is a minimum-diameter rocket (1.750 in dia, L/D = 41.7). The body tube has NO fin-can and no finned-body base drag augmentation (augmentation only applies when fins are mounted on the aft-most body tube). The base drag of 0.31 at M=1.12 looks too low for this geometry.

Rail button drag = 0.056 is significant (6.1%) but likely correct for a protuberance at M=1.1.

### 2d. A-601 Kinsel (M=2.328, supersonic, +35.1%)

| Component | Cd | Cdf | Cdp | Cdb | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.0830 | 0.0830 | 0.0000 | 0.0000 | 23.7% |
| Nose Cone | 0.0339 | 0.0171 | 0.0168 | 0.0000 | 9.7% |
| Fin | 0.0082 | 0.0063 | 0.0019 | 0.0000 | 2.3% |
| Fin Can | 0.1586 | 0.0155 | 0.0000 | 0.1431 | 45.3% |
| Rail Guide | 0.0206 | 0.0000 | 0.0206 | 0.0000 | 5.9% |
| Fin Can Shoulder | 0.0004 | 0.0004 | 0.0000 | 0.0000 | 0.1% |
| **Total** | **0.3501** | **0.1414** | **0.0655** | **0.1431** | |

Key: Fin Can base drag dominates at 45.3% of total. This is the only outlier with a dedicated fin can. The fin can base drag of 0.1431 at M=2.33 is the largest single drag term. The body tube contributes only friction (23.7%). Fin drag is negligible (2.3%).

Ignored loader settings: `ModifiedBarrowman=True`, `Turbulence=True`, `SustainerNozzle=3.09` -- these could be significant.

### 2e. Byrum (M=0.749, subsonic, +8.4% -- moderate healthy)

| Component | Cd | Cdf | Cdp | Cdb | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.4277 | 0.2129 | 0.0000 | 0.2147 | 68.9% |
| Nose Cone | 0.0259 | 0.0259 | 0.0000 | 0.0000 | 4.2% |
| Fin | 0.0543 | 0.0146 | 0.0155 | 0.0243 | 8.8% |
| Rail Guide | 0.0019 | 0.0000 | 0.0019 | 0.0000 | 0.3% |
| **Total** | **0.6204** | **0.2826** | **0.0503** | **0.2875** | |

Key: Similar structure to EZI-65 and T&L but with higher total Cd and proportionally more base drag (46.3% vs 42.2% and 39.1%). Byrum has Square fin cross-section (higher fin Cd), which may help close drag gap.

### 2f. Caliber Isp 04 Team 3 (M=0.637, subsonic, -0.7% -- ideal healthy)

| Component | Cd | Cdf | Cdp | Cdb | Share |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.3450 | 0.1514 | 0.0000 | 0.1935 | 82.9% |
| Nose Cone | 0.0239 | 0.0239 | 0.0000 | 0.0000 | 5.7% |
| Fin | 0.0108 | 0.0108 | 0.0000 | 0.0000 | 2.6% |
| Rail Guide | 0.0020 | 0.0000 | 0.0020 | 0.0000 | 0.5% |
| **Total** | **0.4162** | **0.2187** | **0.0040** | **0.1935** | |

Key: Hexagonal fins produce nearly zero pressure/base drag at subsonic. Body tube dominates even more (82.9%). Base drag of 0.194 at M=0.64 is 46.5% of total. Fin contribution is tiny.

## 3. Recurring Patterns

### 3a. Common overshoot mechanism: all 4 outliers overpredict apogee

All 4 outliers show positive apogee error (ORP flies too high), meaning ORP drag is too low during the coast phase. The coast-phase average Cd confirms this:

| Case | Coast avg Cd | Coast avg Cdf | Coast avg Cdp | Coast avg Cdb |
|---|---:|---:|---:|---:|
| EZI-65 | 0.485 | 0.293 | 0.014 | 0.152 |
| T&L | 0.525 | 0.353 | 0.007 | 0.138 |
| Raven | 0.787 | 0.546 | 0.023 | 0.188 |
| Kinsel | 0.577 | 0.290 | 0.034 | 0.200 |
| Byrum | 0.644 | 0.387 | 0.044 | 0.192 |
| CalIsp1 | 0.544 | 0.290 | 0.002 | 0.135 |

### 3b. Subsonic outlier pattern (EZI-65, T&L)

- Both are subsonic (M < 0.65) ogive-cylinder rockets with no fin can
- Both have Rounded fin cross-sections
- RASAero also overpredicts for both (+6.3% and +11.5%), though less than ORP
- The common residual is **body tube base drag + body tube friction**
- Body tube accounts for 72-79% of total drag
- Base drag is 37-39% of total
- No fin-can structure to receive augmentation
- Pressure drag is small (3-13% of total)

**Hypothesis**: For these simple ogive-cylinder rockets, the body-tube base drag at subsonic speeds is underestimated by approximately 0.03-0.05 Cd (~7-12% of total), consistent with the overshoot. Since RASAero also overpredicts, part of the miss may be non-aerodynamic (mass/motor import, surface finish).

### 3c. Transonic minimum-diameter pattern (Raven)

- Minimum-diameter (1.750 in) with extreme L/D = 41.7
- Transonic peak Mach = 1.12
- Body tube base drag = 0.31 at M=1.12 is the dominant term
- **Fin-body base drag augmentation does NOT apply**: Raven has no fin can. Fins are directly on the body tube, but the body tube runs well past the fin trailing edge (body tube ends 73.5 in, fin trailing edge is at ~63.1 in). The augmentation code searches for fins on the aft-most body tube, which here is the single body tube. However, the finned-body augmentation was calibrated for rockets where fins are at the extreme aft end, not where the body extends well past the fins.
- The 27.5% overshoot is the second largest, suggesting the transonic base drag model underpredicts for minimum-diameter geometries
- The Raven body tube length is 65 in / 1.75 in dia = L/D = 37.1 for the body tube alone. At this extreme ratio, boundary layer thickness at the base may be large relative to diameter, increasing base drag

**Hypothesis**: The base drag model (Devan-Ashwood for turbulent, calibrated for blunt-base cylinders) may underpredict for minimum-diameter/high-L/D bodies where the thick boundary layer at the base increases wake size. The 0.31 Cd_base at M=1.12 may need to be 0.40+ to close the gap.

### 3d. Supersonic fin-can pattern (Kinsel)

- Largest outlier (+35.1%) with supersonic M=2.33
- Fin can base drag = 0.143 is the dominant term (45.3% of total)
- Body tube contributes only friction (23.7%)
- Fin drag is negligible (2.3%)
- Has 3 ignored CDX1 settings: `ModifiedBarrowman=True`, `Turbulence=True`, `SustainerNozzle=3.09`
- The `SustainerNozzle=3.09` ignored setting means a 3.09-in nozzle exit diameter (on a 6.5-in reference diameter rocket). At M=2.3 with an expanding plume, this could materially reduce base drag by filling the wake. ORP ignores this.
- `Turbulence=True` in RASAero enables higher drag; ignoring it means ORP may use wrong surface finish assumption
- RASAero underpredicts by -3.9%, so the "truth" base drag may be even higher than RASAero computes

**Hypothesis**: The Kinsel overshoot has two reinforcing causes:
1. **Power-on nozzle effects during boost**: The 3.09-in nozzle on a 6.5-in body creates a significant base-area-fill effect. During the 12s boost phase (60% of ascent altitude), ignoring this plume effect leaves base drag artificially high during boost -- but since the apogee is *overpredicted*, this is not the issue. The problem is coast-phase drag being too low.
2. **Coast-phase drag deficit**: After burnout at M=2.3, the base drag on the fin can (0.143) needs to be higher. The Devan-Ashwood base drag at M=2.3 gives Cd_base = 0.064 + 0.186/M^2 = 0.064 + 0.034 = 0.098 (for a flat-base cylinder). The 0.143 already includes finned-body augmentation. But the augmentation may be insufficient at M > 2.
3. **CDX1 parity gap**: The ignored `Turbulence=True` setting may increase skin friction in RASAero that ORP does not apply.

## 4. Cross-Case Comparison: Where Is the Missing Drag?

### 4a. Base drag fraction

| Case | Cd_base | base% | err | Notes |
|---|---:|---:|---:|---|
| EZI-65 | 0.207 | 42.2 | +16.1% | Subsonic, no fin-can |
| T&L | 0.179 | 39.1 | +17.4% | Subsonic, no fin-can |
| Raven | 0.354 | 38.6 | +27.5% | Transonic, min-dia, no fin-can |
| Kinsel | 0.143 | 40.9 | +35.1% | Supersonic, fin-can |
| Byrum | 0.288 | 46.3 | +8.4% | Subsonic, no fin-can |
| CalIsp1 | 0.194 | 46.5 | -0.7% | Subsonic, no fin-can |

Observation: The healthy cases (Byrum, CalIsp1) have **higher base drag fractions** (46%) than the outliers (39-42%). This is counterintuitive but consistent: the healthy cases have higher total Cd, so any base drag deficit is a smaller fraction of the correct answer. The outliers may have the correct friction but insufficient base drag.

### 4b. Friction per body length

| Case | body L/D | Cdf_body | Cdf_body / (L/D) | err |
|---|---:|---:|---:|---:|
| EZI-65 | 18.0 | 0.168 | 0.0093 | +16.1% |
| T&L | 19.5 | 0.190 | 0.0098 | +17.4% |
| Raven | 37.1 | 0.290 | 0.0078 | +27.5% |
| Kinsel | 17.5 (body) + 3.1 (fin-can) | 0.083 + 0.016 | 0.0048 | +35.1% |
| Byrum | 23.9 | 0.213 | 0.0089 | +8.4% |
| CalIsp1 | 15.5 | 0.151 | 0.0098 | -0.7% |

Observation: Friction per unit L/D is consistent across most cases (0.0078-0.0098) except Kinsel where the supersonic compressibility reduces friction. Raven's lower value (0.0078) is consistent with compressibility effects at M=1.12. Friction is unlikely to be the primary deficit for any outlier.

### 4c. Fin cross-section effect

| Case | Fin xsec | Fin Cd | Fin Cdp | err |
|---|---|---:|---:|---:|
| EZI-65 | Rounded | 0.038 | 0.019 | +16.1% |
| T&L | Rounded | 0.018 | 0.001 | +17.4% |
| Raven | Rounded | 0.049 | 0.019 | +27.5% |
| Kinsel | Hexagonal | 0.008 | 0.002 | +35.1% |
| Byrum | Square | 0.054 | 0.016 | +8.4% |
| CalIsp1 | Hexagonal | 0.011 | 0.000 | -0.7% |

Observation: Rounded fins produce more pressure drag than Hexagonal fins at subsonic. But the differences are small (0.01-0.02 Cd) and cannot explain 10-35% apogee errors. Fin drag is a secondary term in all cases.

## 5. Diagnosis Summary

### Primary residual: Body/fin-can base drag

For all 4 outliers, the common residual is **base drag being too low**. The specific mechanism differs by Mach regime:

1. **Subsonic (EZI-65, T&L)**: Body tube base drag at M=0.5-0.6 appears underestimated by ~0.03-0.05. However, RASAero also overpredicts for both cases (especially T&L at +11.5%), so part of the miss may be non-aerodynamic (motor/mass import, surface finish, or barometric altimeter calibration).

2. **Transonic minimum-diameter (Raven)**: Body tube base drag at M=1.1 is 0.31 and needs to be ~0.40+ to close the gap. The extreme L/D (41.7) means the boundary layer at the base is thick relative to diameter, likely increasing wake size beyond what Devan-Ashwood predicts for standard L/D bodies. The finned-body augmentation may not activate correctly when fins are well inboard of the base.

3. **Supersonic fin-can (Kinsel)**: Fin can base drag at M=2.3 dominates (45% of total). The ignored `SustainerNozzle=3.09` CDX1 setting means ORP does not model the plume effect during boost. During coast at M=2.3, the fin-can base drag of 0.143 may still be too low. CDX1 import parity (Turbulence, ModifiedBarrowman) is a confounding factor.

### Secondary residuals

- **CDX1 import parity**: Kinsel has 3 ignored settings. These must be bounded before attributing the full +35.1% to aerodynamic model error.
- **Surface finish / transition state**: EZI-65 and T&L have Rounded fins, which in real flight may trip the boundary layer earlier. ORP's surface finish model may undercount this effect.
- **Non-aerodynamic factors**: Both subsonic outliers have barometric altimeter data which can have systematic bias (overshoot of 5-10% is within baro calibration uncertainty for small rockets at low altitude).

### What is NOT the problem

- **Nose/body pressure drag**: Small share (2-22%) except at transonic where it is expected.
- **Fin drag**: 2-9% of total in all cases. Even doubling fin drag would not materially change apogee.
- **Body tube friction**: Consistent across healthy and outlier cases per unit L/D.
- **Fin-can shoulder/transition drag**: Negligible in all cases.

## 6. Recommended Fix Priorities

(Decomposition only -- fixes are deferred to dedicated closure tracks.)

1. **Bound Kinsel CDX1 parity gap first** (ModifiedBarrowman, Turbulence, Nozzle). If bounding these closes 15+ percentage points, the aerodynamic residual is manageable.
2. **Investigate minimum-diameter base drag** (Raven). This is the cleanest test case for a base drag model deficiency: simple geometry, no import parity issues, no fin-can.
3. **Assess whether subsonic body base drag** (EZI-65, T&L) is a model issue or a non-aerodynamic import/data quality issue. RASAero's own overprediction suggests the latter.

## 7. Artifact Paths

- Fresh diagnostic reports: `core/build/reports/simvreal-outliers/*.md`
- Component-CD sweep CSVs: `core/build/reports/simvreal-outliers/*-component-cd.csv`
- Trajectory CSVs: `core/build/reports/simvreal-outliers/*-trajectory.csv`
- Summary CSV: `core/build/reports/simvreal-outliers/simvreal-outlier-summary.csv`
- Decomposition CSV: `paper/data/csv/high_m_drag_decomposition.csv`
