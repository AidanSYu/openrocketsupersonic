# NASA TM X-653 Static-Stability Benchmark Plan

## Source Material
- **Document:** `paper/data/pdf/NASA_TM_X_653.pdf`
- **Extracted text:** `paper/data/txt/nasa_tm_x653_extracted.txt`
- **Scope:** Mach 0.6 to 3.8, finned low-fineness bodies with conical noses, cylindrical midbodies, and cruciform fins.
- **Key outcomes to capture:** zero-angle-of-attack normal-force slope (`C_N`), center of pressure location (`X_CP`), and base-normalization references for each Mach and AoA combination listed in the memo (figures 6 through 10 and associated tables).

## Geometry/Data Snapshot
- Nose pieces: sharp cone (NS) and spherical-blunted cone (NB, 2rS/d=0.30) with matching cone half-angle.
- Cylindrical bodies: lengths of 2d and 3.37d, all paired with 1.373d aft sections (flares or fins).
- Fin aftersections share the 20° flare planform; configurations include sharp (NS-FF) and blunt fins with both fin-dominated and flare-dominated stabilities.
- Boundary-layer tripping rings, wire diameters, and testing tunnels (Ames 2x2 transonic and 1x3 supersonic) are described on pages 4–7 and control the transition state.

## Data Extraction Tasks
1. Digitize the tabulated normal-force curve slopes and CP positions referenced in figures 6–10 and the following tables; capture Mach, AoA, `C_N`, `X_CP`, reference length, and notes about separation.
2. Record provenance for each row: figure/table identifier, page number, digitization method (manual pick, interpolation), and any uncertainty notes.
3. Store the digitized dataset at `paper/data/csv/NASA_TM_X653_digitized_points.csv` and provenance at `paper/data/csv/NASA_TM_X653_provenance.csv`.

## Benchmark Execution Plan
1. Wait for `PublicationAnalyticalDataExportTest` (or a new stability export test) to produce the ORP comparison CSV (expected naming template: `nasa_tm_x653_*` in `paper/data/csv`). The file should contain `Mach`, `AoA_deg`, `C_N`, `C_P`, and `X_CP` computed from the Barrowman/lookup stability chain.
2. Run `paper/data/py/nasa_tm_x653_benchmark.py` once the ORP export is available. The script will merge the experimental/ORP datasets, compute `MAE/RMSE/MAPE` for `C_N` and `X_CP`, generate pointwise tables, and write a markdown/CSV summary.
3. Guard the benchmark with acceptance gates (e.g., `MAE < 0.05`, `MAPE < 10%` for critical Mach bands) to ensure reviewers can see where predictions are credible.

## Immediate Next Steps
1. Populate `NACA_TM_X653_digitized_points.csv` with the first batch of points (start with Mach 0.6, 0.9, 1.2 for the standard fin configuration) using the available figures.
2. Confirm the OR geometry fixture in `SupersonicTestRockets` (via collaboration) matches the published configuration so exported data will align.
3. Once both datasets exist, revisit this plan to finalize metrics and defensive text in `paper/data/md/nasa_tm_x653_validation_report.md`.
