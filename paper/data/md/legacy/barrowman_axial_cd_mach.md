# Engineering-model Cd(M) sweep (native OR geometry)

## Claim supported
Documents the **total drag coefficient** produced by the current `BarrowmanCalculator` stack for two **canonical axisymmetric** test rockets (`SupersonicTestRockets`: cone-cylinder and ogive-cylinder) at **α = 0**, sea-level atmosphere.

## What this is / is not
- **Is:** a reproducible **ORP baseline curve** for publication figures and regression tracking when you change fin/body/wave-drag modules.
- **Is not (yet):** a pass/fail against independent wind-tunnel Cd(M) for the same metal model. For that, add columns from a cited tunnel report or digitized AGARD configuration data (e.g. AGARD-B and related calibration literature — see https://en.wikipedia.org/wiki/AGARD-B_wind_tunnel_model and NATO AGARD archives).

## Suggested next external column
- **AGARD / NATO RTO** calibration models and missile-aero short courses often tabulate forces for standard wind-tunnel shapes; pick one geometry you can match exactly in ORK and merge tunnel **Cd(M)** into the CSV.

## Files
| File | Description |
|------|-------------|
| `barrowman_axial_cd_mach.csv` | Mach sweep + CD breakdown |
| `barrowman_axial_cd_mach.png` | Cone vs ogive overlay |
