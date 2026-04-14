# Taylor-Maccoll cone-flow validation

## Claim supported
The cone-shock / Taylor-Maccoll solver matches published conical-flow reference cases used in standard gas-dynamics texts and NASA Glenn validation material.

## Reference sources
- Published Taylor-Maccoll cone-shock tables used in `ObliqueShockSolverTest`.
- NASA Glenn 10 degree cone at Mach 2.35 validation case.

## Files
| File | Description |
|------|-------------|
| `taylor_maccoll_cone_shock.csv` | Cone-shock reference vs ORP |
| `taylor_maccoll_cone_shock.png` | Shock-angle overlay |

## NASA Glenn reference case

| Quantity | Published reference | ORP |
|----------|---------------------|-----|
| Shock angle (deg) | 27.1843 | 26.7367 |
| Surface Mach | 2.1469 | 2.1468 |
| Surface pressure ratio | 1.4234 | 1.3739 |
| Surface temperature ratio | 1.1063 | 1.0951 |

## Interpretation
This is the strongest published-data support for the Taylor-Maccoll building block. It validates a solver that later feeds cone wave drag and local-flow pre-pass logic.
