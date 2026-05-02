# Benchmark Source Plan

Current AST-facing source plan for gaps that should not be papered over with
legacy notes or fitted constants.

## Closed or frozen for this submission pass

| Item | Current artifact | Status |
|---|---|---|
| SimVReal corpus baseline | `VALIDATION_MATRIX.md`, `corpus_summary_2026_05_01.md` | 25-flight regression baseline (24 cases plus MESOS as flight 25; 2026-05-02 fresh rerun): 25/25 within +/-10 %, avg abs error 4.74 %. |
| Corpus ablation | `md/simvreal_corpus_ablation_2026_05_01.md` | Nozzle pressure-thrust is material; force-turbulent-BL is bounded for non-perfect-finish imports. |
| RASAero head-to-head | `md/rasaero_head_to_head_2026_05_01.md` | Included as corpus-specific evidence, not a universal dominance claim. |
| Prospective holdout split | `corpus_holdout_split_2026_05_01.md` | Freeze protocol for future tuning; does not retroactively promote the corpus to A-level. |
| RM-10 | `VALIDATION_MATRIX.md` | Negative external benchmark; excluded geometry family, not a tuned paper claim. |

## Cmq recalibration source gate

Do not recalibrate the Basic Finner / finned-vehicle Cmq multiplier on
ADA636861 alone. That would consume the only independent finned free-flight
Cmq dataset currently in the repo and merely replace one B-level heuristic with
another.

Current source split:

| Source | What it can validate | Current use |
|---|---|---|
| Tobak & Wehrend, NACA TN 3788 | Isolated cone / slender-body pitch damping theory | Body-term implementation anchor (`TobakCmqBenchmarkTest`). |
| ADA636861 Basic Finner | Combined finned-vehicle pitch damping from aeroballistic range reduction | Current B-level finned Cmq magnitude benchmark (`BasicFinnerCmqBenchmarkTest`). |

Required before tuning:

1. A second independent finned-body dynamic-stability dataset, preferably
   forced-oscillation or aeroballistic range data with Mach-resolved Cmq or
   combined `Cmq + Cm_alpha_dot`.
2. A declared development/holdout split before changing the multiplier.
3. A no-regression run of Basic Finner drag, Basic Finner Cmq, Tobak body Cmq,
   and the full SimVReal corpus gates.

Tobak TN 3788 is useful as a body-term holdout, but it is not by itself a
finned-vehicle Cmq holdout.

## Published-CFD comparison, cheap version

Do not run new RANS for the AST pass. If reviewer credit is needed, add a
published-data comparison only:

1. AGARD-B: digitize an open published CFD / wind-tunnel comparison for the
   AGARD-B geometry and overlay ORP's existing AGARD-B benchmark outputs.
2. Basic Finner: digitize a published CFD / range comparison for the Basic
   Finner family and overlay the existing `BasicFinnerDragBenchmarkTest`
   outputs.

Keep this as supporting context unless the source has enough geometry,
Reynolds-number, and uncertainty detail to become an A-level gate.

## MESOS staging ablation

The production architecture recomputes `ShockGeometry` once per aerodynamic
evaluation from the active `FlightConfiguration`, so normal staging already
uses the post-separation active stack. There is no current test seam that
deliberately freezes pre-separation shock geometry across stage separation.

If this ablation is added, keep it isolated:

1. Add a test-only shock-geometry freeze hook or injectable calculator seam.
2. Run only MESOS 293K baseline versus frozen-pre-separation geometry.
3. Report apogee, velocity, and peak-Mach deltas as an architectural ablation,
   not as a new tuning target.

Do not modify the production shock-geometry path merely to manufacture this
ablation row.
