# SimVReal Holdout Split - 2026-05-01

Machine-readable CSV: `paper/data/csv/simvreal_holdout_split_2026_05_01.csv`

This is a prospective freeze protocol, not a claim that the May 1 numbers were generated from a historically untouched holdout. The current 24-flight SimVReal result is a full-corpus regression baseline. From this freeze forward, any tuning of RM-10, Cmq, base drag, nozzle handling, or CDX1 import parity must declare which split it touches before the change is made.

## Roles

| Role | Count | Use |
|---|---:|---|
| `development_lock` | 10 | Cases already used in closure work or explicit ablation. They may diagnose mechanisms, but any further tuning must be disclosed. |
| `prospective_holdout` | 15 | No tuning against these cases after 2026-05-01. They are the first-line regression and generalization check for future changes. |

The split includes MESOS 293K as a separate development-lock stress case because it has custom motor loading and dedicated two-stage event diagnostics.

## Policy

- A future change passes the corpus gate only if it preserves the frozen headline: avg absolute error <= 5%, 24/24 within +/-10%, and 0 abnormal endings.
- A future change that moves any prospective-holdout case by more than +/-2 pp needs a mechanism note before it can be accepted.
- A Cmq recalibration may use ADA636861 only as a development source if another independent free-flight or forced-oscillation dataset is held out. Without that second source, Cmq remains B-level.
- RM-10 is not a tuning target for the headline claim. Its high-fineness parabolic nose, tapered afterbody, and 60-degree swept-arc fin family is excluded unless a separate geometry-family model is introduced and validated without regressing Basic Finner.

## Headline Interpretation

The holdout split makes future work auditable. It does not retroactively promote the SimVReal corpus to A-level validation because the corpus is integrated flight evidence with atmosphere, motor, mass, instrumentation, and import-parity confounders. It supports a B-level integrated trajectory claim.
