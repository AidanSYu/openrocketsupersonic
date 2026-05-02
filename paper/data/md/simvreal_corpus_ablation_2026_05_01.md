# SimVReal Corpus Ablation - 2026-05-01

Source test: `info.openrocket.core.aerodynamics.SimVRealCorpusAblationTest.testWriteAstCorpusAblationSubset`

Machine-readable CSV: `paper/data/csv/simvreal_corpus_ablation_2026_05_01.csv`

This is a bounded AST publication ablation. It reruns the hardest high-M SimVReal cases from the frozen 25-flight corpus (24 cases from `SimVRealBenchmarkTest.testSimVRealBenchmark` plus MESOS 293K as flight 25) and toggles two imported CDX1 mechanisms:

- `no_nozzle_pressure_thrust`: clears all stage nozzle exit diameters before simulation.
- `force_turbulent_bl_off`: disables RASAero `Turbulence=True` after import.

MESOS 293K's powered-flight closure is reported in detail by `SimVRealBenchmarkTest.testMesosFlight` (flight 25 in the manuscript headline) because that case has custom two-stage motor loading and a dedicated staging event report; this nozzle/turbulence ablation does not toggle MESOS itself.

## Results

| Mutation | Rocket | Baseline err | Mutated err | Delta pp | Delta ft | Max Mach | Notes |
|---|---:|---:|---:|---:|---:|---:|---|
| baseline_current | Don't Debate This | -6.05% | -6.05% | +0.00 | +0 | 3.04 | current production model |
| baseline_current | Qu8k | -1.89% | -1.89% | +0.00 | +0 | 3.46 | current production model |
| baseline_current | Proteus 6 | +7.37% | +7.37% | +0.00 | +0 | 2.87 | current production model |
| baseline_current | Full Metal Jacket BALLS 005 | -1.91% | -1.91% | +0.00 | +0 | 2.31 | current production model |
| baseline_current | A-601 Kinsel | +8.72% | +8.72% | +0.00 | +0 | 2.19 | current production model |
| baseline_current | AeroPac 104K | -1.01% | -1.01% | +0.00 | +0 | 3.04 | current production model |
| no_nozzle_pressure_thrust | Don't Debate This | -6.05% | -6.88% | -0.83 | -472 | 3.02 | stage nozzle diameters cleared |
| force_turbulent_bl_off | Don't Debate This | -6.05% | -6.05% | -0.00 | -0 | 3.04 | RASAero Turbulence=True disabled |
| no_nozzle_pressure_thrust | Qu8k | -1.89% | -8.27% | -6.39 | -7757 | 3.39 | stage nozzle diameters cleared |
| force_turbulent_bl_off | Qu8k | -1.89% | -1.89% | +0.00 | +0 | 3.46 | RASAero Turbulence=True disabled |
| no_nozzle_pressure_thrust | Proteus 6 | +7.37% | +2.05% | -5.33 | -4530 | 2.81 | stage nozzle diameters cleared |
| no_nozzle_pressure_thrust | Full Metal Jacket BALLS 005 | -1.91% | -2.05% | -0.14 | -54 | 2.30 | stage nozzle diameters cleared |
| no_nozzle_pressure_thrust | A-601 Kinsel | +8.72% | +5.70% | -3.02 | -1292 | 2.14 | stage nozzle diameters cleared |
| force_turbulent_bl_off | A-601 Kinsel | +8.72% | +8.72% | -0.00 | -0 | 2.19 | RASAero Turbulence=True disabled |
| no_nozzle_pressure_thrust | AeroPac 104K | -1.01% | -14.54% | -13.53 | -14158 | 2.84 | stage nozzle diameters cleared |
| force_turbulent_bl_off | AeroPac 104K | -1.01% | -1.01% | +0.00 | +0 | 3.04 | RASAero Turbulence=True disabled |

## Interpretation

Stage nozzle exit diameter is an active, material mechanism for the high-altitude and multi-stage cases. Clearing it moves AeroPac by -13.53 pp, Qu8k by -6.39 pp, Proteus by -5.33 pp, and Kinsel by -3.02 pp.

The force-turbulent-BL flag is bounded to zero on the tested SimVReal imports. This is expected after the May 1 implementation change: RASAero smooth paint does not enable ORP perfect-finish laminar flow, so these rockets already use the turbulent rough-plate branch. The flag still matters for synthetic perfect-finish laminar fixtures, where `BoundaryLayerTransitionTest` covers it directly.

This ablation supports the methodology section, but it does not convert the full SimVReal corpus into A-level component validation. It is B-level integrated evidence with explicit mechanism isolation.
