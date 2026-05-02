# paper/data/legacy/

Historical research, diagnostic, and decision memos from the development campaign. **Not load-bearing for the current validation claim**; kept as audit trail showing how the program reached its 2026-05-01 state.

The current state lives in [`../VALIDATION_MATRIX.md`](../VALIDATION_MATRIX.md). The current frozen corpus baseline is [`../corpus_summary_2026_05_01.md`](../corpus_summary_2026_05_01.md). Per-case closure sheets are in [`../outlier_closure/`](../outlier_closure/).

## What's here

| File | Type | Note |
|---|---|---|
| `audit_thrust_nozzle_wiring.md` | Audit | Documents the thrustLevel/nozzle-wiring gap; closed by April 30 production-path plumbing |
| `candidate_fixes_decision_memo.md` | Decision memo | Apr 16 list of candidate fixes for the 4 then-open outliers; outcomes captured in outlier closure sheets |
| `damping_heuristic_sensitivity_memo.md` | Sensitivity study | Confirmed the Cmq `3×` multiplier and transonic Gaussian have negligible apogee effect |
| `ezi65_tl_nonaero_audit.md` | Outlier audit | EZI-65 / Thunder & Lightning subsonic outliers; superseded by their closure sheets |
| `high_m_drag_reconciliation.md` | Diagnostic | Peak-Mach drag decomposition across outliers and healthy cases; foundational for the closure |
| `kinsel_fix_result.md` | Fix result | Lamb-Oberkampf Re removal experiment for Kinsel |
| `kinsel_vs_qu8k_diagnostic.md` | Diagnostic | Boattail-geometry comparison Kinsel vs Qu8k |
| `m2_3_base_drag_source_hunt.md` | Source hunt | Located NACA TN 3393 / TR 1036 base-drag primary sources |
| `prompt12_candidate1_result.md` | Experiment | Verification of Prompt-12 fix |
| `raven_thick_bl_audit.md` | Audit | THICK_BL multiplier diagnostic on Raven; ruled out as blocker |
| `raven_vs_rabia_diagnostic.md` | Diagnostic | Min-diameter geometry isolation (Raven now closed) |
| `rm10_vs_basic_finner_diagnostic.md` | Audit | RM-10 vs Basic Finner geometry comparison; identified RM-10's gap |
| `transonic_base_drag_source_hunt.md` | Source hunt | Located Hart L52E06, Peck, Hoerner transonic base-drag sources |
| `transonic_dowell_response.md` | Reviewer-prep | Slender-body vs fin transonic-treatment rationale |
| `AST_PARALLEL_AGENT_ROADMAP.md` | Handoff board | Apr-17 snapshot with 4 then-open outliers; all 4 closed by April 30 |
| `BENCHMARK_SOURCE_PLAN.md` | Backlog | Apr-14 publication backlog; superseded by VALIDATION_MATRIX closure status |
| `CDX1_UNSUPPORTED_SETTINGS_SENSITIVITY.md` | Sensitivity study | Showed three CDX1 settings have <2 % apogee impact |
| `REVIEWER_DEFENSE_v1_apr14.md` | Stale duplicate | The Apr-14 (59-line) version; the canonical (64-line) lives at `../REVIEWER_DEFENSE.md` |
| `GAP_CLOSURE_PROGRAM_v1_apr13.md` | Stale duplicate | The Apr-13 version; the canonical (Apr-14) lives at `../GAP_CLOSURE_PROGRAM.md` |

## Why these are not deleted

- They explain *why* certain decisions were made (e.g., why the `3×` damping multiplier remains B-level)
- They show the diagnostic path that led to the 04-30 closure (so the closure isn't a black box)
- A reviewer asking "show me the audit trail for X" can be pointed here

## Citing rules

These files **must not** be cited in the AST paper, JOSS paper, or technical report. They reflect intermediate states. Cite the validation matrix, closure sheets, or peer-reviewed sources instead.
