# HISA Modified Finner archive page — disposition

## Source
- URL: https://hisa.gitlab.io/archive/asc/modifiedFinner/notes/modifiedFinner.html
- Solver family: HiSA (High-Speed Aerodynamic) — OpenFOAM-based density-based compressible solver hosted on GitLab Pages.
- Test article (per URL slug + supplied context): "Modified Finner" — geometrically the AFF (Air Force Modified Finner) family, distinct from the ANF Basic Finner (cone + square fins).

## Status (2026-05-02) — second WebFetch attempt also denied
**Page content STILL not ingested by automated agent.** A second WebFetch
attempt against the same URL on 2026-05-02 was again denied by the sandbox
permission layer (response: "Permission to use WebFetch has been denied").
No numerical values, plots, methodology details, geometry tables, or links
to sister/results pages have been extracted into the repository at this time.

**Failure log:**
- Attempt 1 (prior session): WebFetch denied by sandbox.
- Attempt 2 (2026-05-02 follow-up): WebFetch denied by sandbox.

No CSV was produced (`paper/data/csv/hisa_modified_finner_cmq.csv` does not
exist) because no data could be retrieved.

**TODO for the user / next-pass agent:** manually fetch the page (browser or
curl outside the sandbox) and:
1. If the page contains tabulated Cmq/PDM or pitch-damping data points,
   digitize into `paper/data/csv/hisa_modified_finner_cmq.csv` with columns
   `mach,cmq_or_pdm,source_page,source_section`.
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

## Data recovery quantification
- Recoverable so far: **none** (zero bytes ingested across two WebFetch
  attempts).
- Coverage: not assessable.

## A/B/C-level verdict
**Verdict: C (pending) — methodology page status unknown, no usable
numerical data ingested.** The page may in fact be A- or B-level once
fetched, but on the basis of the evidence available in this repository
right now, the HISA Modified Finner archive cannot be claimed as a
validation source. Independence vs Bhagwandin & Sahu 2013 ARL-TR-6725
remains undetermined because we have not seen what experimental anchor
HiSA references.

## Recommendation
Treat HISA as **C / pending** for the AST submission. The Bhagwandin &
Sahu 2013 ARL-TR-6725 ingestion is the primary second-source closure
artifact; the HISA page can be promoted to A/B in a follow-up commit if
and only if the user provides the page content (e.g. saves a local HTML
copy into `paper/data/raw/`) or re-enables WebFetch for the
`hisa.gitlab.io` domain. Until then, do not cite HISA as a Cmq validation
source in the manuscript.
