# Arcas Sounding Rocket Wind-Tunnel Coefficient Digitization Assessment

**Date:** 2026-05-02
**Sources:**
- NASA TN D-4013 (Ferris, J. C., Langley, June 1967) — *Static Stability Investigation of a
  Single-Stage Sounding Rocket at Mach Numbers from 0.60 to 1.20.* Langley 8-foot
  transonic pressure tunnel.
- NASA TN D-4014 (Babb, C. D., and Fuller, D. E., Langley, June 1967) — *Static Stability
  Investigation of a Sounding-Rocket Vehicle at Mach Numbers from 1.50 to 4.63.* Langley
  Unitary Plan wind tunnel.

Both PDFs were read directly from
`paper/data/pdf/New/incoming/arcas/RASAero_Mirror_NASA_TN_D4013_Arcas_StaticStability.pdf`
and
`paper/data/pdf/New/incoming/arcas/RASAero_Mirror_NASA_TN_D4014_Arcas_Supersonic.pdf`.

Output: `paper/data/csv/arcas_wind_tunnel_combined_2026_05_02.csv`.

## Configuration scope (this digitization)

- Body: short (configuration 1, L/d = 18.20, Arcas Robin variant). The long
  body (configuration 2, L/d = 23.77/23.80) is also reported in both papers but is
  excluded here for a single coherent comparator target.
- Fins: on, trapezoidal double-wedge.
- Fin cant: delta_F = 0 deg.
- Roll angle: phi = 0 deg.
- Reynolds number per meter: 9.8e6 (D-4013), 9.84e6 (D-4014). Stagnation temperature
  322 K (D-4013) and 339-352 K (D-4014).
- Reference area: maximum body cross-section. Reference length: body diameter.
- Moment reference center: 70 percent body length (D-4013), 63.37 percent body length
  for the short configuration (D-4014). Cm and Cm_alpha across the two papers are
  therefore NOT directly comparable; xCP (in percent body length) IS comparable.

## Figures digitized

| Quantity | Source | Figure | Page (PDF) | Method | Notes |
|---|---|---|---|---|---|
| CN_alpha vs M, M=0.60 to 1.20 | D-4013 | Fig 7(b) (CN vs alpha, fins-on dF=0) | p.22-23 | slope alpha=0 to ~6 deg, square symbol (phi=0) | linear range only |
| CA0 (CA,corr) vs M, M=0.60 to 1.20 | D-4013 | Fig 11 summary, dF=0 solid curve | p.60 | direct read at alpha approx 0 | base-corrected per report |
| xCP vs M, M=0.60 to 1.20 | D-4013 | Fig 11 top panel, dF=0 solid curve | p.60 | direct read, alpha in [-2, +2] band | percent body length |
| Cm_alpha vs M, M=0.60 to 1.20 | D-4013 | Fig 7(c) (Cm vs alpha, fins-on dF=0) | p.23-25 | slope alpha=0 to ~4 deg, square symbol | moment ctr at 70 pct L |
| CN_alpha vs M, M=1.50 to 4.63 | D-4014 | Fig 5(b) bottom panel (CN vs alpha) | p.28 (Concluded) | slope alpha=0 to ~6 deg | config 1, dF=0 |
| CA0 vs M, M=1.50 to 4.63 | D-4014 | Fig 5(b) top panel (CA vs alpha) | p.26 | direct read at alpha approx 0 | NOT base-corrected; CA includes base term |
| xCP vs M, M=1.50 to 4.63 | D-4014 | Fig 7 (xCP vs M summary) | p.41 | direct read, config 1 solid line | percent body length |
| Cm_alpha vs M, M=1.50 to 4.63 | D-4014 | Fig 5(b) middle panel (Cm vs alpha) | p.27 | slope alpha=0 to ~4 deg | moment ctr at 63.37 pct L |

## Mach coverage achieved

12 Mach points spanning M 0.60 - 4.63:

| Mach | CN_alpha | CA0 | xCP | Cm_alpha | source paper |
|---|---|---|---|---|---|
| 0.60 | yes | yes | yes | yes | D-4013 |
| 0.80 | yes | yes | yes | yes | D-4013 |
| 0.90 | yes | yes | yes | yes | D-4013 |
| 0.95 | yes | yes | yes | yes | D-4013 |
| 1.00 | yes | yes | yes | yes | D-4013 |
| 1.20 | yes | yes | yes | yes | D-4013 |
| 1.50 | yes | yes | yes | yes | D-4014 |
| 1.80 | yes | yes | yes | yes | D-4014 |
| 2.30 | yes | yes | yes | yes | D-4014 |
| 2.96 | yes | yes | yes | yes | D-4014 |
| 3.96 | yes | yes | yes | yes | D-4014 |
| 4.63 | yes | yes | yes | yes | D-4014 |

Total: 12 Mach x 4 quantities = 48 digitized data values.

Note: D-4013 also tested M=0.60-1.20 with the long body (L/d=23.80). D-4014 tested only
M=1.80-4.63 for the long body. These long-body data are NOT included in the v1 combined
CSV but are easy follow-up if needed for a CN+CP comparison vs body length.

## Confidence distribution

- High: 0 (no tabulated data is available; both papers present results only as plotted
  figures, no numerical tables)
- Medium: 9 of 12 Mach rows
- Low: 3 of 12 Mach rows (M=1.20, M=3.96, M=4.63 — combinations of partial occlusion in
  Fig 11 transonic peak, and very flat Cm_alpha curves at high M where slope is hard to
  read precisely)

The Cm_alpha entries are the lowest-confidence subset: at high Mach (>3) the moment
slope is small and the curve traces in Fig 5(b) middle panel are short before pitch-up
sets in (~8 deg). Reader uncertainty on those slopes is at least pm 0.05 /deg, comparable
to the magnitude of the slope itself.

## Reviewer-defensibility verdict

**B-level eyeball quality** with a clear path to A-level. Specifically:

- Both papers provide externally-produced wind-tunnel data with documented test conditions,
  Reynolds number, instrument accuracy, and trip-strip transition fixing. Reading the
  plotted figures yields data with reader-uncertainty that is small compared to model-vs-
  RASAero discrepancies typical of OpenRocket Plus benchmarks (CN_alpha pm 0.005-0.010 /deg
  reader vs typical 8 percent model error).
- The data are NOT available as numerical tables, so all values are visual digitizations
  of printed curves with grid lines. This is acceptable for B-level publication evidence
  but not directly auditable against the original report at the precision a reviewer would
  expect for an A-level claim.
- A-level promotion requires: (1) an Arcas geometry .ork file, (2) a comparator test
  (similar in style to BunescuANFCfdComparatorTest), and (3) ideally an independent
  re-digitization (e.g. WebPlotDigitizer) to bound reader uncertainty.

## Suggested next steps

1. **Build Arcas .ork model.** Geometry from D-4013 Fig 1: ogive nose 4.71d long, cylindrical
   centerbody for short L/d=18.20, boattailed afterbody with reflex lip 1.78d long, four
   trapezoidal double-wedge fins. Fin geometry from D-4013 Fig 1(b) and D-4014 Fig 1(b).
   Body diameter d = 5.72 cm (2.25 in.) at half-scale; full Arcas is d = 11.43 cm.
2. **Run ORP at digitized Mach points** with the same Reynolds number per meter as the
   tunnel tests (9.8e6 / m) to match test conditions; turbulent BL forced via trip strips
   (so use turbulent Van Driest II in ORP).
3. **Write comparator test** `ArcasWindTunnelComparatorTest` modeled after
   `core/.../aerodynamics/BunescuANFCfdComparatorTest.java`, with assertions on CN_alpha
   MAPE, CA0 MAPE, and xCP error in calibers (use body length 18.20 d to convert percent
   body length to calibers).
4. **Re-digitize key figures with WebPlotDigitizer** for the 3 low-confidence Mach rows
   (1.20, 3.96, 4.63) and update the combined CSV with the higher-precision values.
5. **Add the long-body configuration 2** as a second target if body-length sensitivity is
   informative (long body has noticeably different xCP behaviour per D-4014 Fig 7).

## Key caveats for users of this CSV

- D-4013 CA0 values are **base-corrected** (CA,corr in the report). D-4014 CA0 values
  are **uncorrected for base axial force** — they include the base pressure contribution.
  Across the M=1.20/1.50 boundary the apparent CA jump is partly real (transonic drag
  rise plus base flow change at the reflex lip; see D-4014 first paragraph of Results)
  and partly definitional. The `ca0_note` column flags this. Users running an ORP
  comparator should compare against the appropriate quantity for each Mach segment.
- Cm_alpha values across the two papers use different moment reference centers
  (70 vs 63.37 pct body length). To convert: shift by `(0.7 - 0.6337) * L/d = 0.0663 * 18.2 = 1.207 calibers`,
  multiplied by CN_alpha. Practical use: convert both to a single moment center
  (or equivalently, use xCP, which IS reference-center-independent and is the better
  cross-paper comparison metric for static stability).
- The xCP track (Fig 11 D-4013 + Fig 7 D-4014) shows the CP moving rearward through
  the transonic peak (M~1.0-1.2: xCP ~ 86 pct L) and then progressively forward as M
  increases (xCP drops to ~56 pct L at M=4.63). This is a strong, externally-validated
  trend the ORP supersonic stability calculator must capture.
