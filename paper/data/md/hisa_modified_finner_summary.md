# HISA Modified Finner archive page — disposition

## Source
- URL: https://hisa.gitlab.io/archive/asc/modifiedFinner/notes/modifiedFinner.html
- Solver family: HiSA (High-Speed Aerodynamic) — OpenFOAM-based density-based compressible solver hosted on GitLab Pages.
- Test article (per URL slug + supplied context): "Modified Finner" — geometrically the AFF (Air Force Modified Finner) family, distinct from the ANF Basic Finner (cone + square fins).

## Status (2026-05-03) — page text retrieved via WebFetch, plot data still inaccessible
**Page text successfully ingested.** A WebFetch call against the canonical
URL on 2026-05-03 returned the methodology section, geometry description,
reference list, and Mach range. The numerical Cmq / pitch-moment-slope
values themselves are not in the page text — they are embedded in two
plot images (`aeroNormSlope.png`, `aeroPitchSlope.png`) which a
text-mode WebFetch cannot decode. Tabular values therefore remain
unrecovered.

**Fetch log:**
- Attempt 1 (prior session): WebFetch denied by sandbox.
- Attempt 2 (2026-05-02 follow-up): WebFetch denied by sandbox.
- Attempt 3 (2026-05-03): WebFetch succeeded for the notes HTML; image
  endpoints returned 404 / are not text-readable; sibling auto-index
  pages (`archive/asc/`, `archive/asc/modifiedFinner/`,
  `archive/asc/modifiedFinner/notes/`) all 404 — GitLab Pages does not
  expose directory listings.

## What was retrieved (verbatim from the notes page)

- **Geometry callout (text only):** "a 2.5 caliber tangent-ogive cylinder
  forebody with 4 trapezoidal fins." No dimensional values for root chord,
  tip chord, sweep, span, or thickness appear in the page text.
- **Mach band covered:** 0.6 – 2.5.
- **Plots referenced (binary, not extracted):** `aeroNormSlope.png`
  (normal-force slope vs Mach) and `aeroPitchSlope.png` (pitching-moment
  slope vs Mach).
- **References cited on the page:**
  - Bhagwandin & Sahu (2013), "Numerical prediction of pitch damping
    stability derivatives for finned projectiles" (i.e. the very
    ARL-TR-6725 / ADA592550 source we are already using).
  - Samardzic et al. (2007), subsonic wind-tunnel data (T-38).
  - Murman (2005), dynamic-derivatives methodology.
  - Dunn (1989), aeropredictive methods.
- **Free-flight anchors named on the page:** "Army Research Lab (ARL)"
  and "Defence Research and Development Canada (DRDC)" free-flight data
  for "test flights SF and MF". The SF/MF nomenclature matches Dupuis
  (2002) DRDC-Valcartier free-flight runs (SF = Stable Finner / ANF,
  MF = Modified Finner / AFF).

CSV: still not produced. `paper/data/csv/hisa_modified_finner_cmq.csv`
does not exist because no tabular numerical values were obtained from
the page text.

## Independence assessment (resolved 2026-05-03)

The HiSA Modified Finner case validates **against the same source family
we already use**:

- It cites Bhagwandin & Sahu (2013) ARL-TR-6725 directly. The ARL-TR-6725
  PDF was supplied by the user on 2026-05-03 and its reference list
  (refs 26-29) confirms its own anchors are DRDC Dupuis 1997/2002
  (DREV-TM-9703, TM 2002-136 ANF, TM 2002-008 AFF) plus AFRL West 1981
  (AFATL-TR-81-87). The ARL-TR-6725 dataset is already digitized at
  `paper/data/csv/bhagwandin_sahu_2013_anf_aff_cmq.csv`, so
  HiSA-vs-Bhagwandin is CFD-vs-CFD on the same geometry and not an
  independent third source.
- It cites DRDC SF/MF free flights, which are the same Dupuis 2002 family
  already used as the experimental anchor for our existing ANF benchmark
  and the same SF/MF runs Bhagwandin & Sahu use.

The HiSA reference list is a strict subset of the Bhagwandin & Sahu
reference list. Therefore HiSA does **not** introduce a new independent
experimental anchor for AFF Cmq. It is at most a *methodology
cross-check* against identical underlying data, and that finding is now
definitive (no longer pending on PDF availability).

## Data recovery quantification
- Recoverable from page text: methodology summary, geometry topology,
  Mach range, reference list. **Zero numerical Cmq/PDM values.**
- Recoverable from page plots: would require image OCR / plot
  digitization, which has not been performed and would yield only the
  same Bhagwandin & Sahu / Dupuis values we already hold.
- Coverage: not assessable for tabular values; methodology coverage
  reproduces material we already have.

## A/B/C-level verdict
**Verdict: D / not a validation source.** Justification:
1. The numerical values on the page are not in text form, only in plot
   images we cannot machine-read.
2. The validation anchors named on the page (Bhagwandin & Sahu 2013, DRDC
   SF/MF Dupuis-style free flights) are sources we already use, so even
   if we digitized the plots they would not constitute an independent
   third source.
3. HiSA is itself another CFD solver (HiSA on OpenFOAM); using its
   predictions to validate ORP would be CFD-vs-CFD on shared anchors.

## Recommendation
**Do not cite HISA as a Cmq validation source in the AST manuscript.**
The Bhagwandin & Sahu 2013 ARL-TR-6725 ingestion is the primary
second-source closure artifact and remains the headline second source.
The HISA page is a methodology cross-check tied to the same anchors and
adds no independent evidence.

If a future agent ever digitizes `aeroPitchSlope.png` to extract the HiSA
Cmq curve, the resulting CSV should be filed as a *methodology
cross-check artifact* (HiSA-vs-Bhagwandin / HiSA-vs-Dupuis), not as a
new validation source against ORP.
