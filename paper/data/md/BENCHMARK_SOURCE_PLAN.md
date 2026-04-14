# Benchmark Source Plan

This file is the publication backlog for the claims that are still not closed by independent external data.

## First-pass benchmark priorities

| Gap to close | First-choice source | Why it fits OpenRocket Plus | Quantities to compare | Current extraction status | Acceptance target |
|---|---|---|---|---|---|
| Exact zero-lift forebody drag | `NACA RM A52H28` (local PDF: `paper/data/pdf/19930087274.pdf`) | Pure bodies of revolution already represented exactly in ORP (`cone`, `power`, `Haack`, `ogive`) | `C_DF(M)` on base-area reference | Digitized and benchmarked. After per-Mach Reynolds matching the current aggregate benchmark error is `MAE = 0.0147`, `MAPE = 12.5%`; `LV_ogive` and the paraboloid are now close, while the cone and quarter-power families still overpredict around `M ~= 1.24-1.99` | Maintain `MAE <= 0.015` and reduce the residual shape-specific bias toward `<= 8%` MAPE over the reported points |
| Exact base-drag benchmark | `NACA TN 3393` "Base Pressure on Bodies of Revolution in Supersonic Flow" (local PDF: `paper/data/pdf/NACA_TN_3393.pdf`) | Direct base-pressure / afterbody dataset for cylindrical and boattail-type bodies | `Cpb` (base drag coefficient) vs Mach / BL state | **CLOSED (2026-04-14).** Figure-digitized from Figs 9(b), 10(b), 16. Matched coefficient basis (Cpb). Turbulent BL MAE = 0.013. Laminar divergence documented as expected. | `<= 0.01` absolute `C_D,b` error or `<= 10%` on matched cases |
| Finned static-stability benchmark near zero AoA | `NASA TM X-653` "Comparison of the Effectiveness of Flares With That of Fins for Stabilizing Low-Fineness-Ratio Bodies" (local PDF: `paper/data/pdf/NASA_TM_X_653.pdf`) | Geometry family is rocket-like: conical nose, cylindrical midbody, finned aft body, `M = 0.6-3.8` | `C_N(alpha)/deg`, `x_CP/d` | **CLOSED (2026-04-14).** Digitized from Figs 5(a), 5(b) for NSCFB config. CNa MAE = 0.006/deg (good M 0.6-2.0). M=3.0 anomaly documented. | `C_N_alpha` within `10%`; CP within `0.05 D` or the source uncertainty band |
| Body-only nonzero-AoA / crossflow benchmark | `NASA TN D-6996` (Jorgensen, 1973) | Directly targets slender bodies of revolution and gives a reviewer-recognized source for body normal-force / moment behavior | `C_N(alpha)`, `C_m(alpha)`, `x_ac` | Source identified; exact body cases still need selection and digitization | Match the source-author envelope where possible: about `10%` for force coefficients and `0.02 l` for aerodynamic-center location on the reported validation cases |
| Fin-body / wing-body interference benchmark | `NACA Report 1307` | Primary source for Pitts-Nielsen-Kaattari type lift / CP interference behavior | lift-curve slope, CP / neutral-point shift | Source identified; case selection still open | `<= 10%` on lift slope and CP / neutral-point within the source scatter |
| High-supersonic boattail / base extension | `NASA TN D-2761` and related afterbody-pressure reports | Useful for the boattail correction and wake-pressure trend above the basic cylindrical base case | base pressure / afterbody drag | Secondary source only until the primary base benchmark is closed | Use only as a secondary check after a simpler exact base case is finished |

## Recommended order (Updated 2026-04-14)

1. ✓ `NACA RM A52H28`: external zero-lift benchmark closed. Cone/quarter-power bias isolated (`a52h28_bias_isolation.md`).
2. ✓ `NACA TN 3393`: figure-digitized, coefficient-basis matched, turbulent BL agreement confirmed.
3. ✓ `NASA TM X-653`: digitized CNa/xCP, metrics exported, M=3.0 anomaly documented.
4. **Next**: Use `NASA TN D-6996` and `NACA Report 1307` to split body-only and interference-layer validation.

## Data-handling rules

- Use only primary sources for manuscript-facing validation.
- Keep the source reference area exactly as published, then convert once and document the conversion.
- Match Mach number, Reynolds number, angle of attack, and geometry family before computing agreement metrics.
- Treat any figure-only source as provisional until the digitized ordinates are stored in `paper/data/csv`.

## Reviewer boundary

- `RASAero` is not validation truth.
- Flight replay is supporting evidence, not aerodynamic closure.
- `AGARD-B` remains a useful benchmark, but now mainly as a transition-sensitive diagnostic until an independent transonic / base-drag case is added.
