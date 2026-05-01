# NACA TN 3393 Digitization Notes

| Field | Description |
|---|---|
| **Source** | `NACA TN 3393` (March 1955, Ames Aeronautical Laboratory). |
| **Figures** | The provisional values come from Figures 14 and 15, which chart laminar and turbulent base pressure ratios. |
| **Digits** | Each row in `NACA_TN_3393_digitized_points.csv` currently carries an estimated `pb_ratio` taken from the textual summaries (e.g., "about 60 percent of the limiting value" and "82 percent at Mach 4.48"). |
| **Confidence** | `confidence_pct` is set to 65% because the values are derived from narrative statements rather than precise pixel coordinates. |

## Remaining work
1. Re-digitize Figures 14 and 15 with precise pixel picks so each row has exact `(Mach, pb_ratio)` pairs tied to the correct Reynolds number and finish condition.
2. Annotate every data point with the source figure, caption, and any uncertainty (add those columns to the CSV before re-running the benchmark script).
3. Once the OR export (`naca_tn_3393_openrocket_base.csv`) exists, rerun `naca_tn_3393_benchmark.py` to populate the pointwise comparison and validation report with real metrics.
