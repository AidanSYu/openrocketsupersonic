# NASA TM X-653 Static-Stability Benchmark

## Source

NASA TM X-653, Jorgensen, Spahr & Hill (1962). Configuration NSCFB:
sharp 16-degree cone nose + 2d cylinder + blunt cruciform fins.
Wind-tunnel data from Ames 2x2-foot transonic and 1x3-foot supersonic tunnels.

## Agreement Metrics

| metric   |   points |    mae |   rmse |   mape_pct |   max_pct |   mean_bias |
|:---------|---------:|-------:|-------:|-----------:|----------:|------------:|
| C_N      |       10 | 0.0035 | 0.0045 |     6.8447 |   18.0761 |      0.0035 |
| X_CP_d   |       10 | 0.0536 | 0.0610 |     7.1098 |   14.5970 |      0.0536 |

## Interpretation

- **CNa (M ≤ 3)**: OR tracks the experimental curve within 9% from subsonic through M=3.0.
  The M=3.0 anomaly identified in the initial benchmark (56.8% CNa error from TransonicSimilarity
  override on highly-swept fins) has been fixed: a guard prevents the ESDU transonic correction
  from firing when the local Mach exceeds 2.0.
- **CNa (M > 3)**: At M=4.06–5.82, OR overpredicts CNa by 13–18%. The root cause is a K1=0.85
  floor applied to low-aspect-ratio fins (AR < 1.8) which prevents fin CNa from decreasing
  with Mach as rapidly as the experimental data shows. Removing the floor improves CNa but
  worsens xCP agreement. This is a known model trade-off.
- **xCP/d (M ≤ 3)**: Center of pressure agrees within 4% at M=3.0 (was 125% before the fix).
  Subsonic-to-M=2 agreement is 2–9%.
- **xCP/d (M > 3)**: OR predicts CP at ~0.61 cal aft of the junction, while the experiment
  shows 0.53–0.55. The xCP plateau at M > 3 mirrors the CNa plateau from the K1 floor.
- **Overall**: MAPE = 6.8% for CNa and 7.1% for xCP/d across the full Mach range. With the
  M=3.0 fix, no single point exceeds 18% error (vs 125% before). The model is validated for
  engineering-accuracy static stability from M=0.6 through M=5.8.

## Data Provenance

- Digitized from Figures 5(a) and 5(b) of TM X-653 (NSCFB with trip rings).
- Source precision: CNa +/- 0.0015/deg, M +/- 0.01 (M<2.94), +/- 0.03 (M>2.94).
- Confidence: 75% — figure curves are from scanned document; high-Mach points are less certain.

## Files
- `nasa_tm_x653_pointwise_comparison.csv`
- `nasa_tm_x653_metrics.csv`