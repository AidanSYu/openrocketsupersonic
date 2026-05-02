# HISA Modified Finner archive page — disposition

## Source
- URL: https://hisa.gitlab.io/archive/asc/modifiedFinner/notes/modifiedFinner.html
- Solver family: HiSA (High-Speed Aerodynamic) — OpenFOAM-based density-based compressible solver hosted on GitLab Pages.
- Test article (per URL slug + supplied context): "Modified Finner" — geometrically the AFF (Air Force Modified Finner) family, distinct from the ANF Basic Finner (cone + square fins).

## Status (2026-05-02)
**Page content not ingested by automated agent.** WebFetch was denied by the
permission layer when this comparator was generated, so no numerical values,
plots, or methodology details from the HISA archive page have been extracted
into the repository.

**TODO for the user / next-pass agent:** manually fetch the page (browser or
curl) and:
1. If the page contains tabulated Cmq/PDM or pitch-damping data points,
   digitize into `paper/data/csv/hisa_modified_finner_cmq.csv` with columns
   `mach,pdm,pdf,method,grid,source` (or whatever the page reports).
2. If the page only contains a methodology / case-setup writeup with no
   numerical results, leave this file as the disposition record and note that
   the HISA archive is methodology-only for our purposes.
3. Look for a sister "results" or "validation" page linked from the case page;
   that is where HiSA archive cases typically place the actual data points.
4. If the data exists but the HiSA case is itself validated against
   Bhagwandin & Sahu 2013 (ARL-TR-6725) or against Dupuis 2002 free-flight
   Cmq, mark this dataset as a **methodology cross-check only** (it is not
   independent of our existing sources).

## Independence assessment
The HiSA archive case is a CFD validation case. Its independence as a
"second source" for OpenRocket Plus Cmq depends on whether HiSA validates
against:
- (a) Original free-flight experiments (DRDC-Valcartier Dupuis 2002, AFRL/Eglin
      West 1981 AFATL-TR-81-87) — would make it a useful methodology
      cross-check, but the underlying experimental data is the same family we
      already use.
- (b) Bhagwandin & Sahu 2013 CFD++ predictions — would make it CFD-vs-CFD,
      not an independent source.
- (c) An entirely different experiment/dataset — would be most valuable.

Without ingesting the page, this cannot be determined.

## Recommendation
Treat HISA as **methodology-only / pending** for the AST submission. The
Bhagwandin & Sahu 2013 ARL-TR-6725 ingestion (this commit) is the primary
second-source closure artifact; the HISA page can be added as a tertiary
cross-check in a follow-up commit if the user provides the page content or
re-enables WebFetch for that domain.
