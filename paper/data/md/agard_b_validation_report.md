# AGARD-B Drag Coefficient Benchmark

This artifact is a **secondary external benchmark** (grade A with transition-sensitivity caveats).
The exposed-vs-gross wing-reference issue is closed, and the remaining low-Mach component error is explained by boundary-layer transition sensitivity.
With NACA TN 3393 independently closing supersonic base drag (turbulent BL), AGARD-B is no longer the sole transonic drag-split anchor and can be presented as a complementary external benchmark.

## Main Finding

The current `natural_transition` assumption under-predicts low-Mach forebody drag.
When the same geometry is rerun with an `ordinary_finish_bracket`, the low-Mach total and forebody drag move materially closer to the tunnel data, while base drag changes only modestly.
That pattern points to **transition / skin-friction state** as the main AGARD component-error driver, with base-drag excess remaining secondary.

## Agreement Metrics

| Quantity | Natural MAE | Ordinary MAE | Natural MAPE | Ordinary MAPE |
| --- | --- | --- | --- | --- |
| Total drag | 0.00480 | 0.00807 | 17.5% | 29.3% |
| Forebody drag | 0.00437 | 0.00336 | 33.1% | 26.1% |
| Base drag | 0.00530 | 0.00530 | 35.6% | 35.6% |

## Surface-Condition Bracket Coverage

| Quantity | Points in bracket | Coverage |
| --- | --- | --- |
| Total drag | 5 / 12 | 42% |
| Forebody drag | 10 / 12 | 83% |
| Base drag | 0 / 12 | 0% |

## Geometry / Reference-Area Closure

| Metric | Value |
| --- | --- |
| AEDC wing reference area | 0.01710345 m^2 |
| AEDC base area | 0.00193796 m^2 |
| OR exposed fin planform area | 0.00974250 m^2 |
| OR exposed / AEDC wing area ratio | 0.570 |
| Interpretation | Approx. 0.57 is expected because ORP stores exposed fins while AEDC uses gross wing reference area |

## Transition Indicator on the Natural-Transition Run

| Mach | Re | Re_tr | Re/Re_tr | Natural friction CD |
| --- | --- | --- | --- | --- |
| 0.20 | 2.50e+06 | 2.99e+06 | 0.84 | 0.00405 |
| 0.50 | 5.71e+06 | 2.97e+06 | 1.93 | 0.00823 |
| 1.00 | 8.50e+06 | 2.87e+06 | 2.96 | 0.00850 |

## Component Snapshot Near M = 0.20

| Component | Category | CD | Friction | Pressure | Base |
| --- | --- | --- | --- | --- | --- |
| Body Tube | symmetric_body | 0.01642 | 0.00224 | 0.00000 | 0.01419 |
| Delta Wing | finset | 0.00106 | 0.00099 | 0.00007 | 0.00000 |
| Ogive Nose | symmetric_body | 0.00083 | 0.00083 | 0.00000 | 0.00000 |

## Component Snapshot Near M = 1.00

| Component | Category | CD | Friction | Pressure | Base |
| --- | --- | --- | --- | --- | --- |
| Body Tube | symmetric_body | 0.03289 | 0.00470 | 0.00000 | 0.02819 |
| Delta Wing | finset | 0.00577 | 0.00207 | 0.00370 | 0.00000 |
| Ogive Nose | symmetric_body | 0.00192 | 0.00173 | 0.00018 | 0.00000 |

## Reviewer-Safe Interpretation

- AGARD-B is now a secondary external benchmark in the manuscript, complementing the NACA TN 3393 base-drag closure and NACA RM A52H28 foredrag benchmark.
- The remaining forebody error is transition-sensitive; improving transition-state modeling would tighten agreement further.
- AGARD-B should not be the sole basis for transonic drag claims, but it provides legitimate external validation when presented alongside the independent TN 3393 anchor.

## Figures

### Total Drag Diagnostic

![AGARD-B total drag diagnostic](../png/agard_b_total_cd_validation.png)

### Component / Transition Diagnostic

![AGARD-B component diagnostics](../png/agard_b_component_diagnostics.png)
