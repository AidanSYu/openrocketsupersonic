# Tuned-parameter sensitivity

## Purpose
This artifact is the reviewer-facing appendix for the empirical / heuristic constants listed in the thesis. It does not turn these terms into externally validated physics; it shows how strongly representative outputs move when the constants change.

## Quantitative coverage in `tuned_parameter_sensitivity.csv`
- Pitch damping multiplier: representative low-rate subsonic case showing linear scaling before the static-moment cap activates.
- Body damping coefficient and fin damping cap: damping-multiplier sensitivity separated into body and fin contributions.
- Magnus body fraction: `Cn_pa` sensitivity for a representative supersonic stable configuration.
- Transonic `Cmq` augmentation: exported envelope of the Gaussian boost across Mach.
- Vortex onset / saturation and `K_v`: exported ramp shape and side-force sensitivity at a high-AoA reference case.
- Crossflow fin `C_d`: post-stall `C_N` sensitivity for a representative high-AoA case.

## Case definitions
- `subsonic_reference_case`: `Cone-Cylinder-Fins`, `M=0.50`, `alpha=5 deg`, `x_CG=0.45 m`, `q=0.02 rad/s`.
- `six_fin_reference_case`: same geometry with fin count raised to 6 to expose the damping-cap effect.
- `supersonic_reference_case`: `Cone-Cylinder-Fins`, `M=2.00`, `alpha=5 deg`, `x_CG=0.45 m`, `q=0.05 rad/s`.
- `high_aoa_reference_case`: `Cone-Cylinder-Fins`, `M=0.50`, `alpha=60 deg`, `x_CG=0.45 m`.

## Interpretation
Use this artifact to answer reviewer questions of the form "how much of the result is coming from the heuristic?" These constants should still be presented as sensitivity-bounded heuristics unless and until external dynamic-stability or high-AoA data are added.

## Companion plots
- `transonic_cmq_augmentation.png`
- `vortex_sideforce_ramp.png`
