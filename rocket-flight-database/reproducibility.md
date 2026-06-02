# Rocket Flight Database — Reproducibility

This directory holds the canonical validation corpus for OpenRocket-Plus (the
supersonic/hypersonic extension of OpenRocket). Every number in the manuscripts
is regenerated from these files; nothing is hand-entered.

## Files

| File | Contents |
|---|---|
| `flight_comparison.csv` | **Headline corpus** — 25 flights (24 single-stage amateur/SACup + MESOS 293K two-stage), Mach 0.54–4.33. Columns include the raw measured, RASAero II, and OpenRocket-Plus apogees so every error is *derived*, not entered. |
| `sounding_rockets_exploratory.csv` | **Exploratory** — all 20 historical high-Mach sounding-rocket runs (Nike-Apache, Nike-Cajun, Nike-Deacon, Black Brant V, Arcas, HEROS). Reported in full with an `headline_admissible` flag. NOT part of the validated headline (see *Scope*). |

## Headline statistics (regenerated)

`flight_comparison.csv` → mean signed apogee error **−0.38 %**, sample σ **5.44 %**,
RMSE **5.34 %**, **25/25 within ±10 %**, 14/25 within ±5 %.

## Reproduction recipe (from repo root)

```
# 1. Regenerate the simulator outputs (writes core/build/reports/simvreal-outliers/)
./gradlew :core:test \
  --tests "info.openrocket.core.aerodynamics.SimVRealOutlierDiagnosticTest" \
  --tests "info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.testMesosFlight" \
  -Psweeps

# 2. Assemble the master table from the simulator outputs (NON-CIRCULAR: reads only sim outputs)
python paper/data/py/build_flight_comparison.py

# 3. Regenerate headline statistics + figures
python paper/data/analysis/corpus_bias_variance_2026_05_11/analyze.py
```

JDK 21 is required for the Gradle build. Python deps are pinned in
`paper/requirements.txt`.

## Scope and honest disclosures

- **Headline = 25 flights, Mach 0.54–4.33.** The 24 single-stage flights are the
  full set imported from Rogers' public RASAero II flight-comparison collection;
  MESOS 293K is the only two-stage closure and uses reconstructed KIP motors
  (apogee error −6.95 %).
- **Sounding rockets are exploratory, not headline.** Of 20 historical high-Mach
  sounding-rocket flights simulated, only 3 fall within ±10 % (Black Brant V,
  two Nike-Deacon). The Nike-Apache family over-predicts systematically
  (+24…+36 %) and the Arcas/HEROS family under-predicts (−29…−69 %), driven by
  motor/geometry reconstruction uncertainty. Admitting only the 3 within ±10 %
  would be selection bias, so the **entire** set is reported here and excluded
  from the headline. High-Mach sounding-rocket prediction is future work.
- **Two base-drag scale constants are B-level (corpus-frozen).** `THICK_BL_K`
  and `SLENDER_BODY_K` (in `BarrowmanDragCalculator`) are calibrated against
  corpus residuals (Raven/Rabia/Kinsel). These flights remain in the headline,
  so the headline is partly in-sample. Generalization is demonstrated by the
  prospective holdout: the 15 untuned holdout flights are *more* accurate
  (mean |err| 4.20 %) than the 10 development-lock flights (5.56 %). See
  `corpus_holdout_split_2026_05_01` for the split.

## Provenance of ground truth

The 24 single-stage real apogees are Rogers' published flight values; MESOS real
apogee (293,488 ft) is from the SimVReal flight record. Sounding-rocket real
apogees are radar/beacon/GPS values with the primary source cited per row in
`sounding_rockets_exploratory.csv`.
