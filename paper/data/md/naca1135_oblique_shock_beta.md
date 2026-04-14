# Oblique shock validation

## Claim supported
The theta-beta-Mach solver matches published weak-shock tabulations from NACA Report 1135 / standard compressible-flow tables.

## Reference sources
- NACA Report 1135, oblique-shock charts.
- Anderson, Modern Compressible Flow.
- `ObliqueShockSolverTest` in this repo uses the same reference rows.

## Files
| File | Description |
|------|-------------|
| `naca1135_oblique_shock_beta.csv` | Tabulated reference vs ORP |
| `naca1135_oblique_shock_beta.png` | Beta(theta) overlay for M=2,3,5 |

## Interpretation
This closes a previous hole in the publication package: oblique shock validation is now exported alongside the normal-shock and Prandtl-Meyer artifacts.
