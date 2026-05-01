# Transonic Response To Dowell Review Concern

## Purpose

Professor Dowell's key point is that a slender body and a fin/control surface
should not be treated as the same transonic problem. Slender-body theory has
weak Mach dependence in normal force, while fins are low-aspect-ratio lifting
surfaces whose lift slope, interference, and local flow can change rapidly near
Mach 1.

This memo records the current OpenRocket Plus treatment and the diagnostic
artifact added for review. It is not a claim that the transonic model is fully
closed; it is the audit boundary for the first AST readiness pass.

## Current Model Split

### Slender Body / Axial Body Terms

- `FlightConditions.calculateBeta()` uses a C1 Hermite bridge from M = 0.95 to
  1.05 so the numerical beta term does not become singular at Mach 1.
- `SymmetricComponentCalc` keeps the slender-body normal-force path separate
  from fin lifting-surface logic. The current body lift increment is blended
  from M = 0.8 to M = 1.3, and CP is shifted aft with a documented heuristic.
- Body axial drag near transonic speeds uses a base-drag polynomial anchored by
  Hart NACA RM L52E06 through M approximately 1.30 before joining
  Devan-Ashwood at M = 1.50.

### Fins / Low-Aspect-Ratio Lifting Surfaces

- `FinSetCalc` evaluates fin normal force through a dedicated transonic
  interpolation and `TransonicSimilarity` path rather than reusing the
  body-only slender-body assumption.
- When `ShockGeometry` is active, fin calculations can use local post-shock
  Mach instead of freestream Mach.
- Pitts-Nielsen-Kaattari body/fin interference is production-blended through
  the transonic band and intentionally suppressed at higher Mach where the
  current implementation is not validated.
- SBLI chord reduction still needed a consistency pass at the start of this
  audit because fin CNa used local Mach while SBLI used freestream Mach.

### Dynamic Stability

- `BarrowmanStabilityCalculator` applies a Gaussian transonic augmentation to
  `Cmq` with a peak of 3.5x at M = 1.
- That term is classified as `B` evidence in `VALIDATION_MATRIX.md`, not as an
  externally closed pitch-damping model. ADA636861 Cmq data shows the current
  peak is probably too aggressive near M = 1.05-1.12.

## Diagnostic Added

`TransonicDowellDiagnosticTest` writes:

`core/build/reports/transonic-dowell/transonic_dowell_sweep.csv`

The CSV sweeps M = 0.80, 0.90, 0.95, 1.00, 1.05, 1.10, 1.20, and 1.30 for:

- body-only cone-cylinder
- the same cone-cylinder with four fins

Each row reports beta, body CNa/CP, fin CNa/CP, total CNa/CP, body-aft local
Mach, fin-midchord local Mach, `Cmq`, and `CmAlphaDot`. The test asserts the
sweep is finite and present for both geometries, making the transonic split
auditable before any constant retuning.

## Current AST Boundary

The defensible statement is:

OpenRocket Plus separates slender-body and fin transonic treatment in code, and
the new diagnostic exposes that separation quantitatively. The fin model is not
yet a Zona-style potential-flow wing-body solution, and the current
`TransonicSimilarity` curve plus transonic `Cmq` augmentation remain partly
heuristic. Further tuning is deferred until an external low-aspect-ratio
fin/control-surface benchmark or a stronger diagnostic justifies it.
