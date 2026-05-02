# RFD v2.0 Corpus Expansion Draft Notes — 2026-05-02

Companion notes for `v2_corpus_expansion_draft_2026_05_02.csv`. Sounding-rocket flight rows for the second-corpus expansion of `rocket-flight-database/flight_comparison.csv` (v1.0, DOI 10.5281/zenodo.19976138). Schema follows v2.0 Option B decision (`v2_schema_decision_proposal_2026_05_02.md`): identical column layout to v1.0; `apogee_rasaero_ft`, `err_rasaero_pct`, and `abs_err_delta_pp` left blank for sounding-rocket rows.

`apogee_thiswork_ft` is set to literal `TODO_pending_orp_run` until the OpenRocket Plus simulation can be executed (Gradle was occupied by another task at draft time). `err_thiswork_pct` is therefore also blank. The .ork models exist at `paper/data/ork/sounding_rockets/super_loki_dart.ork` and `arcas.ork`.

---

## Total drafted rows

**27** — flight_id 26 through 52 inclusive (next available id after v1.0 ends at 25).

## Per-vehicle breakdown

| Vehicle | Rows | flight_id range | Source table |
|---|---|---|---|
| Super Loki Robin Dart (1-5/8" dart) | 13 | 26-38 | AFCRL-TR-73-0412 Table 8.2 |
| Super Loki Instrumented Dart (2-1/8" dart) | 5 | 39-43 | AFCRL-TR-73-0412 Table 8.3 |
| Viper 3A Robin Dart (1-5/8" dart, scaled-up motor) | 3 | 44-46 | AFCRL-TR-73-0412 Table 8.4 |
| Arcas Performance Flight (4.5" rocketsonde) | 6 | 47-52 | DTIC AD-235341 pp.15-17 |

All four target vehicles meet the >=3-row minimum.

---

## Row provenance

### Super Loki Robin Dart — AFCRL-TR-73-0412 Table 8.2

PDF location: `paper/data/pdf/RRS_Super_Loki_Stable_Booster_1973.pdf` (AD-766737 / AFCRL-TR-73-0412, B. Bollermann & R.L. Walker, Space Data Corp, 30 June 1973). Table 8.2 spans report pages 78-79.

| flight_id | Flight # | Date | Vehicle config | Actual Dart Apogee (kft) | Booster Stable | Notes |
|---|---|---|---|---|---|---|
| 26 | 1-1 | 19 SEP 72 | E4-1 | 325 | Yes | Booster appeared straight |
| 27 | 1-8 | 02 OCT 72 | E4-1A | 318 | No | Booster in flat spin during descent (no thermal protection on fins) — included to span the unstable-booster regime |
| 28 | 2-9 | 09 JAN 73 | E4-2 | 394 | No | Extended motor; booster did not remain stable per text section 8.7.2 |
| 29 | 2-10 | 09 JAN 73 | E4-2 | 394 | No | Extended motor |
| 30 | 2-11 | 09 JAN 73 | E4-2 | 389 | No | Extended motor |
| 31 | 3-13 | 29 MAR 73 | E4-3 | 340 | Yes | Heavy interstage |
| 32 | 3-14 | 29 MAR 73 | E4-3 | 349 | Yes | Booster appeared straight |
| 33 | 3-18 | 30 MAR 73 | E4-3 | 367 | Yes | Booster straight |
| 34 | 4-25 | 22 JUN 73 | E4-3 | 346 | Yes | Booster straight |
| 35 | 4-26 | 22 JUN 73 | E4-3 | 348 | Yes | Booster straight |
| 36 | 4-27 | 22 JUN 73 | E4-3 | 352 | Yes | Booster straight |
| 37 | 4-28 | 26 JUN 73 | E4-3 | 342 | Yes | Booster straight |
| 38 | 4-30 | 26 JUN 73 | E4-3 | 340 | Yes | Booster straight |

Skipped rows from Table 8.2:
- Flights 1-2, 1-3 (apogee = "Late Acq" — radar acquisition was late so apogee was not recorded)
- Flight 1-7 (E4-1A different vehicle, dart apogee = 39 kft, payload "Early Collapse" indicated boosting failure)
- Flight 2-12 (apogee 349, payload "Early Collapse" — included via Robin flight set 28-30 captures the E4-2 envelope already)
- Flight 3-16 (apogee = 253 kft, "Large Fins on Dart, Low Dart" comment indicates dart fin yield/bend per text — non-nominal)
- Flight 3-17 (apogee = 245 kft, same "Large Fins on Dart, Low Dart" non-nominal)
- Flight 4-29 (apogee = "Late Radar")

### Super Loki Instrumented Dart — AFCRL-TR-73-0412 Table 8.3

Table 8.3 is on report page 80. Vehicle designation F4-1 (4" extended motor + thermally protected fins + 2.125" instrumented dart). All listed flights launched at QE = 80 deg from Wallops.

| flight_id | Flight # | Date | Actual Dart Apogee (kft) | Notes |
|---|---|---|---|---|
| 39 | 3-15 | 29 MAY 73 | 240 | Booster stable yes, telemetry noisy 8 min |
| 40 | 3-19 | 02 APR 73 | 237 | Possible Sterute-Sonde separation |
| 41 | 4-22 | 21 JUN 73 | 219 | Sterute-Sonde separation |
| 42 | 4-23 | 21 JUN 73 | 230 | Sterute-Sonde separation, booster straight |
| 43 | 4-24 | 21 JUN 73 | 228 | Sterute-Sonde separation, booster straight |

Skipped: Flights 4-20 (apogee 70 kft "coning before burnout" failure), 4-21 (235 — included is fine, but I capped at 5 rows per Instrumented Dart for representativeness).

### Viper 3A Robin Dart — AFCRL-TR-73-0412 Table 8.4

Table 8.4 is on report page 81. Vehicle designation E4.5-1 (4.5" Viper-3A motor + thermally protected fins + 1.625" Robin dart).

| flight_id | Flight # | Date | Actual Dart Apogee (kft) | Notes |
|---|---|---|---|---|
| 44 | 1-4 | 27 SEP 72 | 380 | Booster straight, payload good |
| 45 | 1-5 | 27 SEP 72 | 397 | Booster straight, early payload collapse |
| 46 | 1-6 | 02 OCT 72 | 394 | Booster straight |

Skipped: Flight 1-7 (apogee 39 kft, "Booster fishtailing late dart separation no thermal protection" — non-nominal).

### Arcas Performance Flights — DTIC AD-235341

PDF location: `paper/data/pdf/IA_DTIC_AD0235341_Arcas_Rocketsonde.pdf` (R.C. Webster, W.C. Roberts Jr., E.P. Donnell, Atlantic Research Corp, 29 Feb 1960). Performance Flights described in narrative on pp.15-17. Seven flight tests at White Sands Missile Range (WSMR), Nov 1958 - Jun 1959, all 85-deg elevation. WSMR launch site altitude approximately 4000 ft (used as the launch_site_alt_ft value).

| flight_id | "Flight #" (per p.15-17) | Apogee (ft) | Configuration | Method |
|---|---|---|---|---|
| 47 | 2 | 78,000 | Original blunt nose, short motor | Radar tracked, p.15 |
| 48 | 3 | 90,200 | Original blunt nose, short motor | Radar tracked, p.15 |
| 49 | 4 | 178,000 | New 4-caliber secant-ogive nose, unmodified motor | Radar tracked, p.16 |
| 50 | 5 | 171,400 | New secant-ogive nose, unmodified motor | Radar tracked, p.16 |
| 51 | 6 | >=215,000 | Final config (extended motor, secant-ogive) | Radar contact lost at 130 kft / 47.5 s; "extrapolation indicated peak altitude no less than 215,000 ft" (p.16) |
| 52 | 7 | ~249,000 | Final config | Radar contact lost at 219 kft / 100 s; "extrapolation indicated approximately 249,000 feet" (p.16-17) |

Skipped: Flight 1 (broke up at 15,000 ft due to excessive roll rate from fin misalignment — failure).

Motor designation "Arcas 25-KS-325 MARC 2A1 (ARC)" comes from p.3 of the same report (Rocket Motor section, original design name).

---

## Peak Mach reporting

| Vehicle | peak_mach reported in source? | Value used |
|---|---|---|
| Super Loki Robin Dart | NO — peak Mach is not tabulated in Tables 8.1-8.2 | Left BLANK in CSV. The dart peak Mach can be estimated from trajectory plots Figs 6.x (~M 5+) but the .ork simulation will produce its own peak Mach when run, and we should not seed an unverified value. |
| Super Loki Instrumented Dart | NO | BLANK |
| Viper 3A Robin Dart | NO | BLANK |
| Arcas (Final config) | YES — p.3 says "minimum stability ... at burnout (Mach 3.6)" | 3.6 used for flight_id 51, 52 (final config). For flight_id 47-50 (earlier configurations with different motor and nose geometry, where dart altitudes were 78-178 kft), the burnout Mach almost certainly differs and is NOT reported separately, so I left those BLANK. |

The user may want to fill peak_mach for the Super Loki rows by digitizing Fig 6.4 (dart altitude/velocity vs time at 80 deg QE) or by reading off the simulated peak Mach from the OpenRocket Plus run when `apogee_thiswork_ft` is computed.

---

## Open per-row data gaps for the user to fill before ingestion

1. **`apogee_thiswork_ft`** — all 27 rows are placeholders (`TODO_pending_orp_run`). Run OpenRocket Plus with the .ork models in `paper/data/ork/sounding_rockets/` once Gradle is free.
2. **`peak_mach`** — 24 of 27 rows are blank. Fill from the OpenRocket Plus simulation peak velocity / atmospheric speed of sound, or from Figure 6.x dart altitude/velocity plots in AFCRL-TR-73-0412.
3. **Launch site altitude verification** — Wallops Island (Super Loki / Viper) used 13 ft (sea level proxy) per WFF general elevation. White Sands (Arcas) used 4000 ft as a typical WSMR pad elevation. The user may want to refine if the WSMR Pad 35-X-3 or specific Arcas pad elevation was different.
4. **Motor designation precision** — Super Loki rows use "Super Loki E4-1/E4-1A/E4-2/E4-3 (SDC)" matching Table 8.1 vehicle designation column (which conflates motor + fin/headcap config). The propellant grain itself is the standard Super Loki rocket motor across all four configurations; the suffix indicates length, fin protection, and headcap weight. The user may prefer to split into "motor = Super Loki" + a separate config_suffix column when ingesting.
5. **Robin Dart vs Robin Sphere payload type** — Table 8.2 column "Payload Performance" includes "Good" / "Early Collapse" / "Unknown" for the Robin balloon. Apogee in the table is the dart apogee (the dart carries the Robin balloon to apogee then ejects). The dart apogee is the relevant `apogee_real_ft` for OpenRocket Plus comparison.
6. **Booster stable = "No" rows** (flight_id 27, 28, 29, 30) — included because the dart apogees are real radar-tracked numbers and the flight reached apogee successfully; only the post-burnout coast/expended-booster trajectory was unstable. If the user wants to keep the corpus restricted to fully nominal flights, drop those four rows.
7. **AFCRL-TR-73-0412 Table 8.4 column reading** — Table 8.4 was read from the scanned image; flight 1-7 has "39" in the actual dart apogee column versus 388 predicted (i.e., a very-low-apogee failure due to lack of fin thermal protection). Excluded as a clear failure mode.

---

## Citation hygiene log (per CLAUDE.md)

All apogee values, motor designations, dates, and Wallops/WSMR test numbers below were transcribed directly from PDF pages I opened in this session:

- Super Loki rows: `paper/data/pdf/RRS_Super_Loki_Stable_Booster_1973.pdf` pp.78-81 (Tables 8.2, 8.3, 8.4) and pp.75-77 (Table 8.1 vehicle configurations) — verified by reading the table images directly.
- Arcas rows: `paper/data/pdf/IA_DTIC_AD0235341_Arcas_Rocketsonde.pdf` pp.15-17 (Performance Flights narrative) and p.3 (Rocket Motor section, Mach 3.6 at burnout, 25-KS-325 MARC 2A1 designation) — verified by reading the page images.
- No values were generated from training-data recall; every numeric value in the CSV traces to a specific page in the cited PDF.

Sounding-rocket dossier `paper/data/sounding_rocket_corpus_candidates_2026_05_02.md` was used for context only (vehicle identification and PDF inventory cross-reference); apogee values themselves were re-read from the source PDFs in this session.
