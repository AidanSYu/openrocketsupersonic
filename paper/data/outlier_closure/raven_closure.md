# Raven — Closure Sheet

## Header

- Case: Raven (minimum-diameter 3-fin)
- Current error: **+27.5%** apogee overshoot (ORP 11,235 ft vs real 8,815 ft Baro; RASAero 9,332 ft, +5.9%)
- Status: **OPEN**
- Target: within ±10% (ideally ±5%)
- Regime: transonic (peak M = 1.123)
- Source: `core/build/reports/simvreal-outliers/Raven.md`
- Note on data freshness: the per-case diagnostic CSV and the `simvreal-outlier-summary.csv` predate Prompt 12 (Lamb-Oberkampf Re correction removal). Raven error is unchanged by Prompt 12 because the Re correction is active only at M > 1.3 and Raven peaks at M = 1.12. Numbers in this sheet are therefore current.

## Import parity warnings

- Parity matrix (`paper/data/csv/simvreal_parity_matrix.csv` row 7): `ParityClass = CLEAN`, `UnsupportedActiveCount = 0`.
- ModifiedBarrowman = False, Turbulence = False, SustainerNozzleDiameter = 0.
- Loader warnings in per-case report: none.
- Simulation warnings: "No recovery device defined in the simulation" (tumbling descent; does not affect ascent apogee).
- Conclusion: no CDX1 import artifact to explain the overshoot. Residual is pure aerodynamic-model deficit.

## Event timeline (from Raven.md)

- t = 0.000 s: launch, motor ignition (J570W in the body tube).
- t = 0.030 s: lift-off.
- t = 0.137 s: launch rod clearance.
- t = 2.052 s: motor burnout.
- t = 23.349 s: apogee (11,235 ft AGL).
- t = 26.686 s: tumbling (no recovery device).
- t = 156.517 s: ground hit / simulation end. Terminal note: NORMAL.

## Phase split (from Raven.md phase table)

| Phase | Duration | Max M | Avg Cd | Avg Cdf | Avg Cdp | Avg Cdb | Avg AoA |
|---|---:|---:|---:|---:|---:|---:|---:|
| boost | 2.052 s | 1.123 | 0.848 | 0.524 | 0.077 | 0.247 | 0.000° |
| coast | 21.296 s | 1.094 | 0.787 | 0.546 | 0.023 | 0.188 | 0.631° |
| descent | 133.168 s | 0.083 | 24.69 | 0.282 | 22.34 | 0.055 | 134.99° |

Coast is 91% of the powered-to-apogee time. Coast average Cd is 0.787 and coast average AoA is only 0.63°. The deficit lives in coast-phase axial drag, not in stability or AoA-dependent terms.

## Peak-Mach drag breakdown (max-mach snapshot, M = 1.123, t = 1.698 s)

| Component | Cd total | Cdf | Cdp | Cdb | % of total |
|---|---:|---:|---:|---:|---:|
| Body Tube | 0.6005 | 0.2904 | 0.0000 | 0.3101 | 65.5% |
| Nose Cone | 0.0587 | 0.0248 | 0.0339 | 0.0000 | 6.4% |
| Fin | 0.0487 | 0.0153 | 0.0188 | 0.0146 | 5.3% |
| Rail Guide | 0.0560 | 0.0000 | 0.0560 | 0.0000 | 6.1% |
| **Rocket total** | **0.9171** | **0.3610** | **0.2022** | **0.3539** | |

Body tube base drag (Cdb = 0.310) is the single dominant term and the only plausible closure lever. Friction/pressure across components is consistent with Van Driest II + nose shock-expansion anchoring. Fin Cd (0.049, 5.3%) is too small to move apogee by itself — even doubling it adds ~5% to total Cd.

## Likely root-cause family

Transonic minimum-diameter body-tube base drag underprediction. Per Prompt 9 reconciliation (`paper/data/high_m_drag_reconciliation.md` §3c): body L/D = 37.1, no fin-can (fins sit directly on the single body tube that runs past the fin trailing edge), and the transonic base-drag polynomial at M = 1.12 is already past its M = 1.05 peak of 0.25, giving roughly Cd_base ~ 0.22 before finned-body augmentation. Even with the 3-fin / high-span augmentation factor (Prompt 18 measurement: 1.297 at M = 1.1), the delivered Cd_base = 0.31 is below the ~0.40 needed to close apogee.

## Hypothesis falsification test

If ORP body-tube Cdb at M = 1.12 could be forced to ~0.40 (from the current 0.31) and the coast-average Cd consequently rose by ~0.06–0.08, and Raven apogee *did not* close toward +10%, then the "transonic-minimum-diameter base drag" hypothesis would be falsified and the residual would have to live elsewhere (e.g., body-tube friction model, protuberance drag from rail guide at M > 1, or boundary-layer thickening effect not captured by Devan-Ashwood). Conversely, if the same forced Cdb drops Raven below +10%, the hypothesis is confirmed and Candidate #2 (`paper/data/candidate_fixes_decision_memo.md`: widen transonic base drag peak, anchored to Hoerner Fig. 3.19 or ESDU data) is the defensible mechanism.

## Closure definition

**Closed when ORP apogee error |e| ≤ 10% on Raven with no regression in the 22 A-level external benchmarks and no regression of any SimVReal case from within-10% to outside-10%.** Stretch goal: |e| ≤ 5%. A physics-anchored closure requires an externally digitized transonic base-drag peak dataset (Hoerner / ESDU / TN 3393 transonic, per Prompt 11 Open Question #2) before modifying the peak polynomial.

## Current status

**OPEN.** Parity-clean, single worst clean outlier. Mechanism identified (transonic base-drag peak underprediction on minimum-diameter geometry); Candidate #2 pre-gated on external data that is not yet in the repo. Prompt 12 (Re correction removal) did not reach this case because the correction is inactive at M < 1.3. No code changes were made by this prompt.

## Exact files touched by this sheet

- `paper/data/outlier_closure/raven_closure.md` (this file; new)
