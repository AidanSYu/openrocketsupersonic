# paper/data/snapshots/

Frozen historical SimVReal corpus baselines. Each file is an immutable snapshot from a named date. Use these to track regressions or improvements across the development timeline.

The **current** baseline is [`../corpus_summary_2026_05_01.md`](../corpus_summary_2026_05_01.md) plus the diffable CSV [`../csv/simvreal_baseline_2026_05_01.csv`](../csv/simvreal_baseline_2026_05_01.csv) and the embedded data table in [`../VALIDATION_MATRIX.md`](../VALIDATION_MATRIX.md).

## What's here

| Snapshot | Avg \|err\| | Within ±5 % | Within ±10 % | Notes |
|---|---:|---:|---:|---|
| `corpus_summary_2026_04_17.md` | 6.84 % | 62.5 % | 83.3 % | Post-Prompt-13 audited rerun. 4 outliers > ±10 %: Kinsel, Raven, T&L, EZI-65. |
| `corpus_summary_2026_04_30.md` | 4.65 % | 58.3 % | 100 % | Outlier-closure rerun. All 4 outliers brought inside ±10 %. MESOS at −1.2 % apogee (peak-Mach reported as 3.74 due to display bug — fixed 2026-05-01). |

## When to add a new snapshot

A new snapshot should be added when **any** of the following happens:

1. A code change moves the avg \|error\| by ≥ 0.5 percentage points
2. A code change moves any per-case error by ≥ 2 percentage points
3. A new SimVReal flight is added to the corpus
4. A regression-noteworthy event (e.g., an A-level benchmark gate widens or tightens)

Procedure:
1. Run `SimVRealBenchmarkTest.testSimVRealBenchmark` and `SimVRealValidationTest.testMesos293K`
2. Copy the new `core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv` to `paper/data/csv/simvreal_baseline_<date>.csv`
3. Write a new `paper/data/corpus_summary_<date>.md` (use the latest as a template)
4. Move the previous baseline to this folder
5. Update `VALIDATION_MATRIX.md` "Frozen SimVReal baseline" section and "Change history" table
