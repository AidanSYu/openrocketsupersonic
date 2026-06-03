# Rocket Flight Database — Reproducibility

This directory holds the canonical validation corpus for OpenRocket-Plus (the
supersonic extension of OpenRocket). Every number in the manuscripts is
regenerated from these files; nothing is hand-entered.

## Provenance of `flight_comparison.csv` (authoritative)

`flight_comparison.csv` is the **published Rocket Flight Database** table
(GitHub `AidanSYu/rocket-flight-database`, Zenodo DOI 10.5281/zenodo.19976138,
CC-BY-4.0). Its columns split cleanly into *external ground truth* and *model
output*:

| Column group | Columns | Source (authoritative) |
|---|---|---|
| Measured ground truth | `apogee_real_ft`, `peak_mach`, `flight_data_type`, `data_source`, `diameter_in`, `launch_site_alt_ft` | Published RFD (instrumented flight records; barometric / GPS / optical / accelerometer / radar) |
| Commercial reference | `apogee_rasaero_ft`, `err_rasaero_pct` | Rogers Aeroscience public RASAero II altitude-comparison set (as published in the RFD) |
| This work | `apogee_thiswork_ft`, `err_thiswork_pct`, `abs_err_delta_pp` | OpenRocket-Plus, regenerated from the archived code release (see below) |

The measured and RASAero II columns are **never** regenerated locally — they are
the published, externally-sourced values. Only the `apogee_thiswork_ft` column is
produced by this codebase, so the comparison is non-circular.

> **Note on the model column.** 24 of the 25 `apogee_thiswork_ft` values match the
> values originally published in RFD v1.2. The single exception is MESOS 293K:
> the archived code release used here predicts 273,056 ft (−6.96%), versus the
> 291,601 ft (−0.6%) snapshot in RFD v1.2. This is a genuine, reproducible model
> change (confirmed by running the MESOS flight in isolation — it is **not** a
> test-harness artifact), not a data error. A future RFD version will re-sync the
> model column to the archived code.

## Headline statistics (regenerated)

`flight_comparison.csv` → mean signed apogee error **−0.38 %** (95 % bootstrap CI
[−2.41, +1.72]), sample σ **5.44 %**, RMSE **5.34 %**, MAE **4.74 %**,
**25/25 within ±10 %**, 14/25 within ±5 %. Versus RASAero II (n = 25 paired):
mean +2.46 %; paired Wilcoxon on |error| p = 0.62 (statistical parity).

## Reproduction recipe (from repo root)

```
# 1. Regenerate the OpenRocket-Plus apogee predictions (apogee_thiswork_ft):
#    24 single-stage flights:
./gradlew :core:test --tests "info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.testSimVRealBenchmark" -Pslow
#    MESOS 293K two-stage closure (run in isolation; needs custom KIP motors):
./gradlew :core:test --tests "info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.testMesosFlight" -Pslow
#    (predicted apogees are printed to the test stdout / JUnit XML system-out)

# 2. Regenerate headline statistics, bootstrap CIs, and figures from the table:
python paper/data/analysis/corpus_bias_variance_2026_05_11/analyze.py
python paper/data/analysis/corpus_uncertainty_2026_06_02/uncertainty_quantification.py
```

The analysis scripts recompute every signed error from the full-precision
`apogee_*_ft` columns (the published `err_*_pct` columns are stored rounded to one
decimal), so the table's apogee values are the single source of truth. JDK 21 is
required for the Gradle build; Python deps are pinned in `paper/_build/requirements.txt`.

## Scope and honest disclosures

- **Headline = 25 flights, Mach 0.54–4.33.** The 24 single-stage flights are the
  full set published in Rogers' RASAero II altitude-comparison collection; MESOS
  293K is the only two-stage closure (the highest-Mach flight) and uses
  reconstructed KIP motors (apogee error −6.96 %). The corpus contains **no**
  flight above Mach 4.33; "hypersonic" capability is a method property
  (component-level), not a headline flight-validation claim.

- **Sounding rockets are exploratory, not headline.** Of 20 historical high-Mach
  sounding-rocket flights simulated (`sounding_rockets_exploratory.csv`), only 3
  fall within ±10 % (Black Brant V VB, two Nike-Deacon). The Nike-Apache family
  over-predicts systematically (+24…+36 %) and the Arcas/HEROS family
  under-predicts (−29…−69 %), driven by motor/geometry reconstruction
  uncertainty. Admitting only the 3 within ±10 % would be selection bias, so the
  **entire** set is reported here and excluded from the headline.

- **Two base-drag scale constants are B-level (corpus-frozen).** `THICK_BL_K`
  and `SLENDER_BODY_K` (in `BarrowmanDragCalculator`) are calibrated against the
  residuals of Raven, Rabia, Kinsel (and, via the source diagnostic, Torrent), so
  the headline is partly in-sample. Generalization is demonstrated by a
  **decontaminated** prospective holdout: with every flight the constants touched
  moved into the development set, the 12 remaining blind holdout flights are
  *more* accurate (MAE 3.95 %) than the 13 development flights (MAE 5.47 %).

## Provenance of ground truth

The 24 single-stage real apogees and RASAero II references are Rogers' published
flight-comparison values; MESOS real apogee (293,488 ft) is from the SimVReal
flight record. Sounding-rocket real apogees are radar/beacon/GPS values with the
primary source cited per row in `sounding_rockets_exploratory.csv`.
