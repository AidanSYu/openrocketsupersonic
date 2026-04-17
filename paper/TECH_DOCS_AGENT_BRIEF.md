# Technical Documentation Update Agent Brief

You are updating the technical documentation for the supersonic/hypersonic aerodynamic extensions to OpenRocket. The project is at `c:\Code\OpenRocket Plus`.

## Naming

The project is currently called "OpenRocket Plus" (ORP) but may be integrated into the official OpenRocket repository. Keep naming neutral in documentation.

## The document to update

**`paper/Final Papers/JOSS/AST/Technical Docs/FULL_TECHNICAL_REPORT.md`** (6,675 lines)

This is the main technical report — the single source of truth for every model, equation, constant, and design decision. It is broken into 5 subsections in the same directory:
- `PART_A.md`
- `PART_B.md`
- `PART_C.md`
- `PART_D.md`
- `PART_E.md`

If a reviewer, user, or developer asks any question about "why" or "how", the answer must be in this document. Both the AST journal paper and the JOSS software paper reference this tech doc as the authoritative source for all disputed claims.

## First steps

1. **Read the existing tech report** (FULL_TECHNICAL_REPORT.md and all parts A-E) to understand what it already covers
2. **Read `paper/data/VALIDATION_MATRIX.md`** to see the current validation state (22 A-level claims)
3. **Read `paper/AST_PAPER_AGENT_BRIEF.md`** for comprehensive context about all recent validation work
4. **Read the actual source code and test files** to verify what the report says matches reality
5. **Diff the report against reality** — find what's missing, what's stale, what's wrong

## What has changed since the report was written

These are things that likely need to be added or updated:

1. **Van Driest II skin friction** replaced Eckert reference temperature method — `vanDriestIICf()` in `BarrowmanDragCalculator.java`. NASA TN D-6945 (Hopkins 1972). ~50% Cf reduction at M=5 vs incompressible.

2. **DATCOM 4.1.5.1 fin wave drag** replaced simple cos^2(Lambda) Ackeret — `FinSetCalc.datcomWaveDragCD()`. Properly classifies subsonic vs supersonic leading edges. Validated against NACA TN 3650 (12 experimental free-flight points).

3. **Chapman laminar base drag** added alongside Devan-Ashwood turbulent — `ChapmanKorstBaseDrag.laminarBaseDragCoefficient()`. Chapman (1950) NACA TN 2137. MAPE 4.4% vs TN 3393 laminar data. Applied for `isPerfectFinish()` rockets.

4. **Basic Finner finned-vehicle benchmark** — MAPE 22.7% vs ADA636861 (Dupuis & Hathaway 1997). First vehicle-level total drag validation. 8 multiple-fit + 25 single-shot CX0 points, M 1.08-4.30. `BasicFinnerDragBenchmarkTest.java`.

5. **NACA TN 3650 fin wave drag benchmark** — 12 experimental free-flight data points for 60-degree delta, t/c=0.03 and 0.06, M 1.1-1.6. `NacaTn3650FinWaveDragTest.java`.

6. **All empirical heuristics now externally anchored** (previously undocumented validation):
   - Crossflow body Cd 1.20 — exact match to Jorgensen TR R-474 Table 1 (circular cylinder)
   - Crossflow fin Cd 1.42 — matches Hoerner Ch.3 Fig.28 flat plate at h/b=0.33 (value: 1.43)
   - Pitch damping Cmq — Tobak & Wehrend NACA TN 3788, 39% agreement at M=1.5 via axis-transfer (eq. 54) and L-to-d normalization
   - Transonic Cmq augmentation — AEDC-TR-76-58 Fig.12 roll damping Clp confirms transonic peak at M~1.3-1.5, ~40% increase
   - Magnus body fraction 0.3 — BRL 1193 (Platou 1963) body-alone vs finned-body wind-tunnel data, ratio 0.3-0.8. Body and fin Magnus forces are opposite in sign.
   - Vortex Kv=0.20, onset=20 deg, saturate=40 deg — Paul & Wedemeyer (1982) EOARD-TR-82-7 ogive-cylinder CY(alpha). Bare-body CY/CN=0.52 at peak, Kv=0.20 implies 62% fin suppression.

7. **22 A-level benchmarks total** (was fewer when report was written) — see VALIDATION_MATRIX.md for the full list

8. **Hypersonic cone drag benchmark** — DTIC AD0487365 (Grabow 1965), 11 points M 6.5-17.2, MAPE 16.7%. `HypersonicConeDragBenchmarkTest.java`.

9. **Benchmark test files** — 18+ benchmark test classes now exist in `core/src/test/java/info/openrocket/core/aerodynamics/`. The report should reference all of them.

10. **Digitized CSV data** — extensive collection in `paper/data/csv/` that should be referenced in the report.

## Also update if stale

- `CLAUDE.md` — architecture descriptions, file paths, test instructions, phase status

## Rules

- Do NOT create new files — update existing ones only (FULL_TECHNICAL_REPORT.md, PART_A through PART_E, CLAUDE.md)
- Trust the code over the documentation — if they disagree, fix the docs
- Do NOT cite NACA/NASA reports from memory — verify against PDFs in `paper/data/pdf/`
- Read the actual Java source to verify equations and constants mentioned in the report
- Every model, equation, constant, and design decision should be traceable from the tech doc to the source code to the validation test to the primary reference
