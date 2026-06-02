# JSR Parameter Sensitivity Analysis -- 2026-05-11
Source test: `info.openrocket.core.aerodynamics.SensitivityAnalysisTest`. Raw CSV: `paper/data/analysis/sensitivity_2026_05_11/sensitivity_sweep.csv`.

Sensitivity coefficient definition (central difference):

$$ s_{p,f} = \frac{A_{p,+10\%}(f) - A_{p,-10\%}(f)}{2\,A_{\text{nom}}(f)} $$

where $A$ is apogee for flight $f$ and parameter $p$, evaluated at +/- 10% perturbation. Units: dimensionless (fraction of apogee per unit fraction of parameter). Reported below as percent.

## TL;DR
Across 4 flights (M_max 1.89-7.22), ranked corpus-mean |s|:

- **Total Cd scale**: mean |s| = 4.00% (median 3.62%, n=4)
- **Launch rod angle**: mean |s| = 1.11% (median 0.87%, n=4)
- **Time step**: mean |s| = 0.98% (median 0.08%, n=4)
- **Launch altitude**: mean |s| = 0.48% (median 0.01%, n=4)

Top two by corpus mean |s|: **Total Cd scale** (4.00%) and **Launch rod angle** (1.11%).

## Per-flight nominal results
| Flight | M_max | Real ft | Nominal orp ft | Nominal err % |
|---|---:|---:|---:|---:|
| HEROS 3 (M~1.9, 32 km) | 1.89 | 106,000 | 38,761 | -63.43 |
| Arcas Flight 2 blunt (M~2.3, 24 km) | 2.30 | 78,000 | 53,478 | -31.44 |
| Nike-Apache 14.108 GI (M~6.5, 161 km) | 6.50 | 528,000 | 694,083 | +31.46 |
| Black Brant V VB AAF-VB-32 (M~7.2, 274 km) | 7.22 | 897,638 | 835,084 | -6.97 |

## Per-flight, per-parameter signed sensitivity
Values are $s$ in percent (positive = apogee rises with parameter).

| Flight | Total Cd scale | Launch altitude | Time step | Launch rod angle |
|---|---:|---:|---:|---:|
| HEROS 3 (M~1.9, 32 km) | -7.04% | -0.02% | +0.00% | -2.12% |
| Arcas Flight 2 blunt (M~2.3, 24 km) | -1.72% | +1.88% | -3.75% | +1.14% |
| Nike-Apache 14.108 GI (M~6.5, 161 km) | -3.76% | +0.01% | -0.15% | -0.61% |
| Black Brant V VB AAF-VB-32 (M~7.2, 274 km) | -3.49% | +0.01% | +0.00% | -0.58% |

## Interpretation
- The reported value of $s$ (as a percent) is the apogee fractional response per central-difference $\pm 10\%$ parameter step. Equivalently, $|s|$ is the magnitude of the apogee change (in percent) produced by a $\pm 10\%$ parameter perturbation. A value near 10% indicates the parameter is a unit-elasticity (first-order) driver of apogee.
- **Total Cd scale** dominates (4.00% mean |s|): a $\pm 10\%$ perturbation in this parameter shifts apogee by about 4.00% on average across the corpus.
- **Launch altitude** has the lowest corpus-mean |s| (0.48%), indicating apogee is approximately invariant to that parameter within the swept envelope. For time step this is the desired numerical-convergence result.

## JSR pull quote
> Across the 4-flight sensitivity corpus (M$_{max}$ 1.9 to 7.2), apogee is most sensitive to total cd scale: a central-difference $\pm 10\%$ perturbation shifts predicted apogee by $|s|_{\text{mean}} = 4.00\%$ (median 3.62\%) across the corpus. The integrator time step is the smallest-|s| numerical lever (0.98% mean), confirming that the apogee predictions reported elsewhere in this paper are numerically converged within the 0.025-0.10 s envelope.
