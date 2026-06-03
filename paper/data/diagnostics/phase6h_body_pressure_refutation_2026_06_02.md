# Phase 6h re-examination: the hypersonic body-pressure hypothesis is insufficient (2026-06-02)

## Summary

The Nike-Apache 1965 family overshoots radar-tracked apogee by +24% to +38%
(monotonic with peak Mach). `SUPERSONIC_MODELING.md` Phase 6h and the v2 corpus
dossier hypothesized the cause as **missing hypersonic high-L/D body pressure
drag** — the slender-body "Fix C" pressure-drag term decays to zero at M=5
(`SLENDER_BODY_MACH_DECAY_END=5.0`), leaving ORP's pressure Cd plateaued flat at
~0.234 from M2-8 while the NASA Apache Performance Handbook (X-721-66-569 Apx A
Case 1) sits near 0.30, a mean deficit of +0.0595 Cd for M>=5.

A direct test of that hypothesis **refutes it as the dominant mechanism.**

## What was tested

A hypersonic continuation of the Fix C body pressure-drag term was implemented
(gated `body fineness > 15` using the body's OWN diameter, ramped in over
M 5.0-5.5, magnitude = a body pressure coefficient HYP_BODY_CDP on the body
frontal area, converted to the configuration reference area). A calibration
sweep varied the effective body Cd from 0 to 0.56 and recorded the radar-tracked
Apache apogee error:

| Vehicle | real (ft) | CDP=0 | CDP=0.14 | CDP=0.28 |
|---|---:|---:|---:|---:|
| Nike-Apache 14.75 GR | 570,000 | +24.79% | +21.82% | +20.05% |

The effect is ~-1.2 percentage-points of apogee error per +0.07 body Cd.
Closing the +24.79% bias by this mechanism alone would require a body pressure
coefficient of **CDP ~= 1.4** on the Apache frontal area — physically impossible
(body pressure Cd for a slender cylindrical afterbody is O(0.05-0.2)). The
mechanism therefore accounts for **less than a quarter** of the bias even at an
already-implausible magnitude.

## Why coast body pressure drag cannot close it

The deep-verification run (`SoundingRocketCorpusV2Test.testNikeApacheDeepVerification`,
14.108 GI) shows mass and staging are correct to <1%:

| Quantity | Source spec | Model | Diff |
|---|---:|---:|---:|
| Launch mass (kg) | 730.96 | 731.49 | +0.07% |
| Apache ignition mass (kg) | 133.13 | 134.11 | +0.74% |
| Nike burnout / sep / Apache ign / Apache burnout times | exact | exact | 0.00% |
| Apogee (ft) | ~528,000 | 688,004 | +30.3% |
| Peak velocity (m/s) | — | 1931.2 | — |

For a near-ballistic flight to ~160 km, apogee scales as v_burnout^2, so a +30%
apogee error corresponds to roughly a **+14% excess burnout velocity**. The bias
therefore lives in the **boost / total-supersonic-drag budget** (too little drag
accumulated by Apache burnout), not in the thin-atmosphere coast above M=5 where
dynamic pressure — and hence any coast-Cd correction — is small.

## Conclusion and disposition

- The speculative hypersonic body-pressure term was **reverted**; it is inert
  for the published 28-flight corpus (no corpus flight is simultaneously M>5 and
  body-fineness>15) and insufficient for the Apache family.
- A latent issue was identified for the future record: the Fix C slenderness
  gate uses `conditions.getRefLength()`, which on a slender sustainer riding a
  fat booster (Nike-Apache: Apache body 0.168 m dia vs Nike aft-skirt 0.4445 m
  reference) understates the body fineness (6.5 vs the true 17), so Fix C never
  engages for such vehicles. Correcting this did not close the bias (above), so
  it is documented but **not** changed (Fix C is frozen by the 2026-05-01 corpus
  gate and the change is corpus-neutral but Apache-insufficient).
- **The Nike-Apache 1965 family remains a disclosed open limitation, correctly
  held out of the validation corpus.** The cause is now better understood: a
  boost / total-supersonic drag deficit on a high-fineness dart riding a
  large-fin booster (a multi-body, high-L/D regime adjacent to the excluded
  RM-10 family), not a hypersonic body-pressure plateau. Closing it is a
  scoped future investigation (boost-phase drag decomposition for the Nike +
  Apache stack), not a single-constant patch.

This negative result strengthens, rather than weakens, the manuscript: a
plausible published-handbook-motivated hypothesis was tested and falsified with
a parameter sweep, and the excluded-family boundary is defended mechanistically.
