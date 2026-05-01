# Subsonic Non-Aero Outliers: EZI-65 and Thunder & Lightning

**Status (2026-04-30):** Closed under the hard ±10% implementation goal. The earlier April 17 note classified these as non-aero residuals because both RASAero and ORP overshot the same flight cards; after the shared rail/base/fin wake updates, both now pass without case-specific coefficients.

## Cases

| Case | ORP error | RASAero II error | Mach regime | Peak Mach |
|---|---|---|---|---|
| EZI-65 | +4.9% | +6.3% | subsonic | 0.60 |
| Thunder & Lightning | +8.4% | +11.5% | subsonic | 0.54 |

## Why these are bounded above for an aero-only fix

The original residual an aero-model change could conceivably address was bounded by the gap between ORP and RASAero II on the same imported flight card:

- EZI-65: ~9.8% aero headroom (16.1 − 6.3)
- Thunder & Lightning: ~5.9% aero headroom (17.4 − 11.5)

Both are subsonic (M < 0.7), where ORP's friction/pressure/base-drag models are the most mature and well-anchored in the repo (A-level viscosity, speed of sound, Chapman laminar, Van Driest II). The accepted closure did not globally inflate all subsonic drag. It used shared geometry mechanisms that also protect neighboring cases: rounded-fin/finned-base wake scaling, rail-guide aerodynamic-height parity for low-profile imports, and a small four-fin low-subsonic wake ramp.

The remaining component of the error, which RASAero also produces, should still be treated as flight-card scatter rather than something to tune out. Candidate non-aero mechanisms remain:

1. **Motor total-impulse / thrust-curve import discrepancy.** CDX1 thrust curves, if imported with a different reference density or truncation rule than the real motor, can shift total impulse by several percent.
2. **Mass / CG mismatch.** Import of propellant mass fractions, fin-can mass, avionics mass not transferred from CDX1 to ORP cleanly.
3. **Surface finish.** Perfect-finish flag vs actual rocket finish changes skin friction by the full 0.5–1.5% band at subsonic.
4. **Launch conditions.** Wind speed, wind azimuth, launch rod angle — minor but non-zero effects on apogee.
5. **Weather / atmospheric density.** Flight-day density vs standard atmosphere, typically a few percent.

All five are non-aerodynamic and should not be "fixed" with per-rocket aerodynamic multipliers.

## Decision

- Keep both cases in the SimVReal corpus and in the aggregate gates.
- Treat the remaining offsets as acceptable flight-card scatter after shared aerodynamic/importer fixes.
- Do not apply any per-rocket correction to force them closer to zero.

## Impact on corpus metrics

The April 30 full-corpus rerun reports avg \|error\| = 4.65%, 100% within ±10%, 58.3% within ±5%, and 0 abnormal endings. There are no remaining SimVReal cases outside ±10%.

## Cross-references

- `paper/data/VALIDATION_MATRIX.md` — trajectory validation and AST readiness rows
- `paper/data/corpus_summary_2026_04_30.md`
- `paper/data/outlier_closure/raven_closure.md`
- `paper/data/outlier_closure/kinsel_closure.md`
- `paper/data/AST_PARALLEL_AGENT_ROADMAP.md` — Prompt 13 session log (blocked), Prompt 14 session log (RM-10 finding)
