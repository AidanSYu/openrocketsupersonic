# Numerical Guard Audit

This file separates aerodynamic claims from software-robustness guards. Reviewers should not be asked to accept a numerical stabilizer as evidence of aerodynamic validity.

## Publication rule

- Put externally validated aerodynamics in the validation section.
- Put the items below in a robustness / software-quality appendix.
- Do not claim that a guard threshold is "validated aerodynamics" unless it has its own external dataset.

## Guard-only items

| Parameter | Current role | Current evidence in repo | Reviewer-safe wording | Remaining work |
|---|---|---|---|---|
| Gyroscopic `q` threshold (`500 Pa`) | Prevents explicit RK4 stiffness at very low dynamic pressure | `RK4SimulationStepper` regression tests, trajectory smoke tests | Numerical activation gate only | Add clean-case invariance sweep showing negligible effect when aerodynamic restoring moments dominate |
| Angular timestep floor (`dt_user/4`) | Prevents tumble descent slowdown | timestep / stability tests in simulation package | Integrator safeguard only | Add runtime / invariance table for representative tumbling cases |
| Minimum timestep (`dt_user/20`) | Absolute lower bound on adaptive stepping | simulation regression tests | Integrator safeguard only | Same as above |
| `C_D` / `C_N` sanitization caps | Catch blow-ups before they hit the stepper | edge-case and hardening tests | Numerical clamp, not aerodynamic evidence | Add trigger-case audit proving clean cases do not touch the caps |
| Crossflow `C_m` scale cap (`20`) | Prevents moment amplification when replacing small-angle `C_N` with post-stall crossflow `C_N` | high-AoA tests | Numerical safeguard for CP preservation | Add high-AoA sensitivity table showing when the cap becomes active |
| Crossflow `C_N` zeroing threshold (`|C_N| < 0.5`) | Avoids ill-defined CP when Barrowman `C_N` is near zero | high-AoA tests | Numerical safeguard only | Add trigger-map by Mach / AoA showing the active region |
| SBLI `M^2-1` floor (`0.1`) | Prevents near-sonic singularity | SBLI / transonic hardening tests | Numerical denominator floor | Add "inactive outside transonic edge" confirmation sweep |
| Pressure-plateau cap (`2.0`) | Prevents unphysical separated-flow pressure | component-level hardening tests | Physical upper bound / guardrail | Add source note or replace with externally sourced bound |
| Step-drag threshold (`0.04`) | Prevents deep-transonic step-drag blow-up | interstage / step-drag tests | Numerical threshold only | Add matched before/after trigger-case comparison |
| Pitch / yaw randomization (`±0.0005`) | Breaks perfect numerical symmetry | simulation behavior tests | Monte-Carlo-style symmetry breaker only | Keep out of deterministic validation claims |

## Aerodynamic heuristics that still need external closure

These are not pure numerical guards, so they belong in the sensitivity appendix and eventually need external data:

- Pitch damping multiplier.
- Body damping coefficient and fin damping cap.
- Magnus body fraction.
- Transonic `Cmq` augmentation.
- Vortex asymmetry coefficient and onset / saturation angles.
- Crossflow fin `C_d`.

See `tuned_parameter_sensitivity.csv` and `tuned_parameter_sensitivity.md` for the current quantitative sensitivity package.
