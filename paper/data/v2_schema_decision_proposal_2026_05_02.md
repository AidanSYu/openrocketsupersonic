# Rocket Flight Database v2.0 — Schema Decision Proposal

**Date:** 2026-05-02
**Author:** Aidan Yu
**Status:** **DECIDED — Option B (keep schema, allow `apogee_rasaero_ft` blank for sounding rockets)**
**Sibling repo:** `c:\Code\OpenRocket Plus\rocket-flight-database\` (Zenodo concept DOI 10.5281/zenodo.19976138)

## Decision (2026-05-02)

The user selected **Option B**, overriding the proposal's Option C recommendation. Rationale: keep the existing `apogee_rasaero_ft` column name to preserve the visible record of *where the RAS comparison data came from* (Rogers' published set) and *where it doesn't exist* (sounding-rocket flights pulled from DTIC / NTRS). A blank cell for sounding rockets is more transparent than a generalized `reference_simulator` enum because it makes the RAS-comparable subset of the corpus immediately visible at a glance.

**Implementation impact:**
- No column rename. v1.0 rows untouched.
- New sounding-rocket rows added with `apogee_rasaero_ft`, `err_rasaero_pct`, and `abs_err_delta_pp` left blank (empty string in CSV).
- Aggregates that compare ORP vs RAS must filter out rows with blank `apogee_rasaero_ft`. The aggregate over `apogee_thiswork_ft` is unaffected.
- README needs a one-paragraph note explaining that v2.0 contains rows without RAS reference predictions and how aggregates handle them.
- Zenodo: v2.0 version DOI under the existing concept DOI is still appropriate (the row count grows substantially even though the schema is column-stable).

The Options C and D analyses below are kept for the record but are not the chosen path.

## Problem statement

v1.0 contains 25 amateur / SACup-class flights, each with a populated `apogee_rasaero_ft` cell sourced from Charles E. Rogers' published comparison set. v2.0 will ingest professionally-instrumented sounding-rocket flights (Super Loki, Arcas, Black Brant V/IX, Nike-Apache, Terrier-Orion, Astrobee D, Viper-Dart, HEROS 3). RASAero II ships no `.CDX1` fixtures for these vehicles and no published RAS apogees exist for most of them. The v1.0 schema requires `apogee_rasaero_ft` for every row — that requirement breaks at v2.0 ingest.

## Verified state of v1.0

`flight_comparison.csv` has 25 rows. All 25 have populated `apogee_rasaero_ft`, populated `apogee_thiswork_ft`, and signed/abs error columns. There are no NULL/empty cells in the ground-truth or simulator columns. The schema in `README.md` lists 14 required columns.

## Options considered

### Option A — Keep schema, exclude sounding rockets
v1.0.x stays amateur-only. Sounding rockets cited in the manuscript only.
- **Schema break:** none. **Manuscript:** weakest reviewer defense — leaves the "amateur-only corpus" gap open. **Comparator:** unchanged. **Zenodo:** patch bump only, no v2.0. **AST defensibility:** poor — reviewers will ask why the documented sounding-rocket data isn't in the corpus.

### Option B — Allow `apogee_rasaero_ft` to be NULL
Existing column accepts empty cells; aggregates filter null-RAS rows.
- **Schema break:** minor (semantics change, columns identical). **Manuscript:** workable but awkward — RAS-vs-ORP table footnotes proliferate. **Comparator:** must add null guards. **Zenodo:** v1.1 minor bump arguable. **AST defensibility:** middling — reviewers can still ask what "no reference" means; column name remains RAS-specific.

### Option C — Generalize to `apogee_reference_ft` + `reference_simulator`
Rename `apogee_rasaero_ft` → `apogee_reference_ft`, drop `err_rasaero_pct` → `err_reference_pct`, add `reference_simulator` column with a controlled vocabulary: `RASAeroII | OpenRocketLegacy | FreeFlightTelemetry | WindTunnelExtrapolation | None`.
- **Schema break:** major — column rename, requires v2.0 bump. **Manuscript:** clean — single comparator column with per-row provenance, naturally accommodates BBV vs Arcas vs SACup. **Comparator:** rename + dispatch on `reference_simulator`; small one-time refactor. **Zenodo:** clean v2.0 version DOI under existing concept DOI. **AST defensibility:** strongest — explicit per-row reference provenance is exactly what reviewers ask for.

### Option D — Second CSV `sounding_rocket_flights.csv`
Two corpora, two schemas, two aggregate tables.
- **Schema break:** none on v1.0. **Manuscript:** two separate tables, two separate aggregates, weakens the "single source of truth" pitch. **Comparator:** must read both CSVs. **Zenodo:** awkward — one deposit, two artifacts. **AST defensibility:** weak — splits the corpus story.

## Recommendation: Option C

Rationale: the user's saved direction (`project_corpus_v2_plan.md`) commits to v2.0 being a real expansion of the source-of-truth corpus, not a parallel artifact. Option C is the only option that (a) keeps a single CSV, (b) gives every row explicit reference provenance, (c) generalizes cleanly when we later add a third simulator column, and (d) reads as a deliberate v2.0 schema upgrade rather than a band-aid. The rename cost is a one-time refactor of the README, the comparator script, and ~25 existing rows — trivial relative to the reviewer-defensibility gain.

## Worked example — Arcas SR45-AR-1

Arcas powered by SR45-AR-1 (Atlantic Research Corp), launched per AD-235341 trajectory data; no RAS prediction exists, but TN D-4013/D-4014 wind-tunnel coefficients yield an extrapolated apogee.

```csv
flight_id,vehicle_name,motor,diameter_in,peak_mach,launch_site_alt_ft,apogee_real_ft,apogee_reference_ft,apogee_thiswork_ft,err_reference_pct,err_thiswork_pct,abs_err_delta_pp,flight_data_type,reference_simulator,data_source
26,Arcas Rocketsonde,SR45-AR-1 (ARC),4.50,4.10,0,213000,215800,210400,1.31,-1.22,0.09,Radar Track,WindTunnelExtrapolation,DTIC AD-235341 + NASA TN D-4013/D-4014
```

For Super Loki Robin Dart flights from AFCRL-TR-73-0412 Table 8.2, `reference_simulator=None` with `apogee_reference_ft` empty; aggregates exclude those rows from reference-vs-thiswork stats but keep them in absolute-error stats for ORP.

## Per-vehicle ingestion workflow

1. Read DTIC/NTRS source, extract motor curve + mass props + measured apogee.
2. Build `.ork` (or `.CDX1` then import) for the as-flown vehicle in OpenRocket Plus.
3. Run ORP simulation; record predicted apogee → `apogee_thiswork_ft`.
4. If a published RAS or wind-tunnel-extrapolated apogee exists, populate `apogee_reference_ft` + `reference_simulator`; else leave empty + set `reference_simulator=None`.
5. Append row to `flight_comparison.csv`, recompute aggregates in `README.md`.
6. Bump `CITATION.cff` to v2.0.0, commit, tag, re-deposit on Zenodo (new version DOI under the existing concept DOI).
