# SimVReal CDX1 Parity Interpretation - 2026-05-01

This note supersedes the April parity interpretation that listed Raven/Kinsel-era outliers and described `Turbulence=True` as ignored.

Current source of truth:

- Frozen corpus: `paper/data/csv/simvreal_baseline_2026_05_01.csv`
- Ablation artifact: `paper/data/csv/simvreal_corpus_ablation_2026_05_01.csv`
- RASAero head-to-head summary: `paper/data/md/rasaero_head_to_head_2026_05_01.md`

## Current Parity State

| CDX1 feature | Current handling | Evidence |
|---|---|---|
| Per-simulation nozzle exit diameters | Imported by stage into `SimulationOptions` and used by the RK4 pressure-thrust correction. | No-nozzle ablation moves AeroPac -13.53 pp, Qu8k -6.39 pp, Proteus -5.33 pp, Kinsel -3.02 pp. |
| Design-level nozzle fields | Logged as informational notes when nonzero. They are not labeled unsupported because the per-simulation nozzle fields are consumed when present. | `RASAeroLoaderTest.testWarnsForUnsupportedRASAeroSettings` |
| `Turbulence=True` | Imported as `forceTurbulentBL`. For ordinary RASAero smooth-paint imports, this is bounded to zero because the rocket is not marked `perfectFinish`; synthetic perfect-finish fixtures still use it to bypass laminar skin friction. | `SimVRealCorpusAblationTest`, `BoundaryLayerTransitionTest` |
| `ModifiedBarrowman=True` | Still unsupported as a RASAero-specific stability-model switch. | Loader warning retained; no direct drag effect claimed. |

## Reviewer-Safe Interpretation

The remaining active CDX1 parity gap is `ModifiedBarrowman=True`. Nozzle handling is now a live simulation mechanism, and `Turbulence=True` is both parsed and bounded by ablation for the SimVReal imports.

Do not use the older `simvreal_parity_matrix.csv` as a current accuracy table unless it has been regenerated against the May 1 baseline. Use `simvreal_baseline_2026_05_01.csv` for current ORP/RASAero/real comparisons.
