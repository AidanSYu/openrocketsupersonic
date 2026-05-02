# NACA TN 3393 Base Pressure Benchmark

This note preserves the legacy Devan-Ashwood diagnostic against the TN 3393
base-pressure data. The current production laminar validation is the Chapman
benchmark in `core/src/test/java/info/openrocket/core/aerodynamics/ChapmanLaminarBaseDragTest.java`,
which is the one cited in the manuscript for the 4.4% laminar closure.

## Coefficient Basis

Both the experimental and OpenRocket data are now on the same coefficient basis:
$C_{D,b} = |P_b| = \text{pb\_ratio} \times \frac{2}{\gamma M^2}$

where `pb_ratio` is the fraction of the vacuum-limit base pressure coefficient
($P_{b,\text{vac}} = -2/(\gamma M^2)$) reported in NACA TN 3393.

The OR export column `baseCD` is the absolute value of the Devan-Ashwood
base drag model output, referenced to base area, which is the same quantity.

## Agreement Metrics

Legacy Devan-Ashwood diagnostics:

Collected metrics per boundary-layer state:

| boundary_layer              | meets_gate   |   points |    mae |   rmse |    mape |   max_pct |   mean_bias |
|:----------------------------|:-------------|---------:|-------:|-------:|--------:|----------:|------------:|
| laminar                     | no           |        4 | 0.0240 | 0.0253 | 44.1896 |   85.9690 |      0.0113 |
| turbulent (fixed roughness) | no           |        4 | 0.0133 | 0.0169 | 15.8895 |   24.7353 |     -0.0016 |

## Pointwise Comparison

|   Mach | BL State                    |   TN 3393 Cpb |   ORP baseCD |   Error |   Error % |
|-------:|:----------------------------|--------------:|-------------:|--------:|----------:|
| 2.7300 | laminar                     |        0.1150 |       0.0896 | -0.0254 |  -22.0524 |
| 2.7300 | turbulent (fixed roughness) |        0.1188 |       0.0896 | -0.0292 |  -24.5668 |
| 3.4900 | laminar                     |        0.0680 |       0.0793 |  0.0112 |   16.5293 |
| 3.4900 | turbulent (fixed roughness) |        0.0798 |       0.0793 | -0.0005 |   -0.6073 |
| 4.0300 | laminar                     |        0.0493 |       0.0750 |  0.0257 |   52.2077 |
| 4.0300 | turbulent (fixed roughness) |        0.0660 |       0.0750 |  0.0090 |   13.6484 |
| 4.4800 | laminar                     |        0.0391 |       0.0728 |  0.0337 |   85.9690 |
| 4.4800 | turbulent (fixed roughness) |        0.0584 |       0.0728 |  0.0144 |   24.7353 |

The scatter/line plot is available in `naca_tn_3393_base_pressure.png`.

## Data Provenance

- **Source**: NACA TN 3393, Reller & Hamaker (1955), Ames Aeronautical Laboratory.
- **Model**: 10-caliber tangent ogive nose + cylindrical afterbody, l/d = 5 (Model 2).
- **Figures**: Digitized from Figures 9(b), 10(b), and 16 (condensation-corrected).
- **Coefficient basis**: `Cpb = pb_ratio × 2/(γM²)` converted from reported vacuum-limit fractions.
- **Confidence**: 80% — values are derived from cross-plot interpolation of condensation-corrected curves.

## Interpretation

The Devan-Ashwood base drag model in OpenRocket predicts base drag as a function of
Mach number alone. NACA TN 3393 demonstrates that base pressure depends strongly on
boundary-layer state (laminar vs turbulent) and Reynolds number. The OR model effectively
represents a turbulent-boundary-layer correlation, so we expect closer agreement with
the turbulent data and systematic under-prediction compared to the laminar data
(which has higher base drag due to the thinner boundary layer at high supersonic speeds).
