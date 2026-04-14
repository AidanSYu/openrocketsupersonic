# Normal shock validation (NACA TR 1135 class tables)

## Claim supported
The gas-dynamic **building blocks** used inside the supersonic pipeline match **standard normal-shock tabulations** for calorically perfect air (γ = 1.4).

## Reference sources (independent of OpenRocket)
- **NACA Report 1135** — *Equations, Tables, and Charts for Compressible Flow* (Ames, 1953). NASA reprint: https://www.nasa.gov/wp-content/uploads/2023/03/equations-tables-charts-compressibleflow-report-1135.pdf
- **Normal shock tables** (γ=1.4), e.g. Wikipedia summary: https://en.wikipedia.org/wiki/Normal_shock_tables
- **Anderson**, *Modern Compressible Flow*, Appendix normal-shock relations (same closed-form expressions as implemented in `NormalShockRelations`).

## Files
| File | Description |
|------|-------------|
| `naca1135_normal_shock.csv` | Tabular reference vs `NormalShockRelations` |
| `naca1135_normal_shock.png` | Overlay p₂/p₁(M₁) |

## Interpretation
Differences should be **rounding-level** only (tabular values are 4-digit). This does **not** validate full vehicle **Cd(M)**; it validates **shock algebra** that higher-level body/fin models depend on.
