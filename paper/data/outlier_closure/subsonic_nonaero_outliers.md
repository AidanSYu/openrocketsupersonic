# Subsonic Non-Aero Outliers: EZI-65 and Thunder & Lightning

**Status (2026-04-17):** Flagged non-aero out of scope for the current aero-closure pass.

## Cases

| Case | ORP error | RASAero II error | Mach regime | Peak Mach |
|---|---|---|---|---|
| EZI-65 | +16.1% | +6.3% | subsonic | 0.61 |
| Thunder & Lightning | +17.4% | +11.5% | subsonic | 0.55 |

## Why these are bounded above for an aero-only fix

The residual an aero-model change could conceivably address is, at best, the gap between ORP and RASAero II on the same imported flight card:

- EZI-65: ~9.8% aero headroom (16.1 − 6.3)
- Thunder & Lightning: ~5.9% aero headroom (17.4 − 11.5)

Both are subsonic (M < 0.7), where ORP's friction/pressure/base-drag models are the most mature and well-anchored in the repo (A-level viscosity, speed of sound, Chapman laminar, Van Driest II). Pushing ORP's subsonic drag further to close these cases would almost certainly regress the healthy subsonic cluster that already sits within ±5%.

The remaining component of the error — roughly 6.3% on EZI-65 and 11.5% on T&L, which RASAero also produces — is not aerodynamic. Candidate non-aero mechanisms, in descending order of likelihood:

1. **Motor total-impulse / thrust-curve import discrepancy.** CDX1 thrust curves, if imported with a different reference density or truncation rule than the real motor, can shift total impulse by several percent.
2. **Mass / CG mismatch.** Import of propellant mass fractions, fin-can mass, avionics mass not transferred from CDX1 to ORP cleanly.
3. **Surface finish.** Perfect-finish flag vs actual rocket finish changes skin friction by the full 0.5–1.5% band at subsonic.
4. **Launch conditions.** Wind speed, wind azimuth, launch rod angle — minor but non-zero effects on apogee.
5. **Weather / atmospheric density.** Flight-day density vs standard atmosphere, typically a few percent.

All five are non-aerodynamic. None can be fixed by changes to `BarrowmanDragCalculator.java` or the rest of the aero stack.

## Decision

- Document as non-aero residual.
- Exclude from aero-model gating for this closure pass.
- Deferred to a separate "CDX1 import fidelity and flight-card audit" work stream (not yet prompted).

## Impact on corpus metrics

With these two cases flagged, the remaining aero-gated outlier list is:

- **Raven** (+27.5%, transonic M 1.12) — OPEN, blocked on external transonic base-drag data (Prompt 13 decision gate).
- **Kinsel** (+33.0%, supersonic M 2.33) — OPEN, dominant fin-can base drag term.

The current aero-closure campaign should focus on these two, plus the newly-surfaced RM-10 overshoot inconsistency (Prompt 14 finding: ORP overpredicts RM-10 CDT by +80.5% MAPE while underpredicting Basic Finner by 22.7% — the two benchmarks now bracket a physics disagreement that must be reconciled before further transonic peak tuning is defensible).

## Cross-references

- `paper/data/VALIDATION_MATRIX.md` — Case-specific AST blockers table (EZI-65 and T&L rows now flagged)
- `paper/data/outlier_closure/raven_closure.md`
- `paper/data/outlier_closure/kinsel_closure.md`
- `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` — Prompt 13 session log (blocked), Prompt 14 session log (RM-10 finding)
