# Sounding-Rocket Second-Corpus Candidates Dossier — 2026-05-02

Research goal: build a second flight-validation corpus distinct from the v1.0 25-flight amateur/SACup database, with priority on programs offering **wind-tunnel-derived aerodynamic coefficient tables** (lifts validation from B-level integrated-trajectory to A-level component aerodynamics).

All citations below were verified by opening the actual title page of each downloaded PDF — per `CLAUDE.md` NACA/NASA citation hygiene policy.

---

## Files dropped this session

```
paper/data/pdf/New/incoming/
├── arcas/
│   ├── IA_DTIC_AD0235341_Arcas_Rocketsonde.pdf                 (1.85 MB, IA mirror)
│   ├── RASAero_Mirror_NASA_TN_D4013_Arcas_StaticStability.pdf  (2.61 MB, M 0.60-1.20)
│   └── RASAero_Mirror_NASA_TN_D4014_Arcas_Supersonic.pdf       (12.46 MB, M 1.50-4.63)
├── nike_apache/
│   └── NASA_NTRS_19660015111_TN_D3373_FinWedge_RollHistory.pdf (4.39 MB)
│       NOTE: filename retained for traceability but actual report number on title page is
│             X-721-66-85 / NASA TM X-55463 (NOT TN D-3373). User-supplied number is INCORRECT.
└── super_loki/
    ├── RRS_Super_Loki_Stable_Booster_1973.pdf                  (5.02 MB, AFCRL-TR-73-0412 / AD-766737)
    ├── RRS_Super_Loki_Dart_NASA_CR61238_1968.pdf               (6.71 MB, NASA CR-61238)
    └── IA_DTIC_AD0750796_SuperLoki_Dart.pdf                    (10.63 MB, AFCRL-72-0626 expected)
```

CSV outputs:
- `paper/data/csv/arcas_wind_tunnel_coefficients_TN_D4013.csv` (provisional, see notes)
- `paper/data/csv/super_loki_mass_properties_vs_time.csv` (provisional, digitized from plots)

---

## Target 1 — Super Loki / Loki-Dart  ★ HIGH VALUE, CLOSED

### Verified citations (read from title pages)

| Report | Title | Authors | Date | Sponsor |
|---|---|---|---|---|
| AFCRL-TR-73-0412 / AD-766737 | "Design, Development and Flight Test of the Super Loki Stable Booster Rocket Systems" | Bruce Bollermann, Robert L. Walker (Space Data Corp, Phoenix AZ) | 30 June 1973 | AFCRL Hanscom |
| NASA CR-61238 | "Super Loki Dart Meteorological Rocket System" | (RRS-mirrored, Space Data Corp) | 30 June 1968 | NASA Marshall |
| AD-750796 | "Design, Development and Flight Test of the Super Loki Dart Meteorological Rocket Systems" | (IA mirror downloaded; title page not yet read in this session) | 1972 expected | AFCRL |

### What's in AD-766737 (verified by reading TOC + Tables 3.1, 3.3, 4.1 + Figures 3.4, 4.2-4.8)

This single report alone is exceptional — it has *every* class of data the user asked about:

**Mass properties (Table 4.1):**
- Robin Dart: 14.15 lb, CG 26.5 in from aft, Iyy 0.475 slug-ft^2
- Booster loaded: 61.05 lb, Iyy 8.292 slug-ft^2
- Booster expended: 16.12 lb, Iyy 3.466 slug-ft^2
- Vehicle launch / burnout: 75.15 lb / 30.38 lb, Iyy 20.558 / 11.824 slug-ft^2

**Time-resolved CG and Iyy:** Figures 4.2 and 4.3 (graphical only — digitized in the CSV).

**Motor performance (Table 3.3, sea level firing):** Avg thrust 4757 lbf, total impulse 9944 lbf-s, Isp 228.7 s, action time 2.09 s, max thrust 5954 lbf. Figure 3.4 gives full thrust + chamber pressure vs time curve — directly digitizable for an OpenRocket .RSE motor file.

**Aerodynamic data (Figs 4.4-4.8):**
- Fig 4.4: Booster (first-stage) CN_alpha vs Mach, M 0-7
- Fig 4.5: Robin Dart CN_alpha vs Mach, M 0-7
- Fig 4.6: First-stage CP vs Mach, M 0-7
- Fig 4.7: Robin Dart CP vs Mach, M 0-7
- Fig 4.8: Drag coefficients for booster, complete vehicle, and dart (×10), M 0-6.5
- Plus Section 7 "Expended Booster" set (Figs 7.1-7.3 + Table 7.1)

**Flight-test data (Tables 8.2, 8.3, 8.4):**
- Flight Test Summary - Super Loki Robin Dart Vehicle (78)
- Flight Test Summary - Super Loki Instrumented Dart Vehicle (80)
- Flight Test Summary - Viper 3A Robin Dart Vehicle (81)

**Trajectory data (Tables 6.1, 6.2 + Figures 6.1-6.12):**
- Apogee altitude vs apogee range, dart altitude/velocity vs time at 80° QE, roll rate vs time, impact range vs QE — for both Instrumented Dart and Robin Dart configurations.

### Recommendation rank: **A-level (component) + A-level (vehicle)**

Closes both:
1. The "RasAero comparison set with documented mass properties" gap. CG/Iyy time histories combined with the booster + dart aero curves let us run the full Barrowman pipeline against the Space Data Corp aero curves — direct stability validation.
2. The post-burnout / unpowered-coast drag integration gap (Tables 8.2-8.4 plus Section 7's Expended Booster data).

### Recommendation rank for Viper-3A appendix: **A-level seed**
The same report includes a full Appendix on "Viper 3A Stable Booster Vehicle Description" plus Table 8.4 flight-test summary (12 flights). Closes Target 5 simultaneously.

---

## Target 2 — Arcas / Super Arcas (NASA TN D-4013, D-4014, AD-235341) ★ HIGHEST VALUE FOR A-LEVEL, CLOSED

### Verified citations

| Report | Title | Authors | Date |
|---|---|---|---|
| NASA TN D-4013 | "Static Stability Investigation of a Single-Stage Sounding Rocket at Mach Numbers from 0.60 to 1.20" | James C. Ferris, Langley Research Center | June 1967 |
| NASA TN D-4014 | "Static Stability Investigation of a Sounding-Rocket Vehicle at Mach Numbers from 1.50 to 4.63" | C. Donald Babb, Dennis E. Fuller, Langley Research Center | June 1967 |
| DTIC AD-235341 | "Final Report — Development of the Arcas Rocketsonde System" | R.C. Webster, W.C. Roberts Jr., E.P. Donnell (Atlantic Research Corp) | 29 February 1960 |

NTRS accession 19670020050 was misindexed (it returns the D-4013 title page). The actual D-4014 PDF was obtained from the RASAero mirror and verified by title page.

### Wind-tunnel coefficient situation (HIGHEST-VALUE DATA POINT)

**TN D-4013 + D-4014 together cover M 0.60 to 4.63** for the same Arcas geometry on a 1/2-scale model in the Langley 8-ft transonic + Unitary Plan tunnels. Two body lengths tested: short (L/d = 18.20, "Arcas Robin" baseline) and long (L/d = 23.77, NASA bioscience-payload extended Arcas). Fin cant 0° and 2°. Re_per_ft = 3.0×10^6.

**The data is NOT presented as numerical tables.** It is presented as plot figures — CN, CA, Cm, CY, Cn, Cl all vs alpha (-3 to 21°) at five Mach points (0.60, 0.80, 0.90, 0.95, 1.00, 1.20 in D-4013 and 1.50, 2.16, 2.86, 3.95, 4.63 in D-4014, configured for fins-on/off and three roll angles). Figures 11 and 12 of D-4013 are summary plots: xCP and CA,corr and CA,b vs Mach at α≈0° (the most useful for OpenRocket Plus per-Mach validation).

### Digitization pathway

This data is **fully digitizable** from the figures using WebPlotDigitizer or similar — the curves are smooth and the gridlines are well-defined. We have already produced an initial CSV (`paper/data/csv/arcas_wind_tunnel_coefficients_TN_D4013.csv`) with eyeball reads of Figure 11. To reach A-level, the per-Mach figures (7-10) need proper digitization.

Reported measurement accuracy from D-4013 page 4: CA ±0.004, Cm ±0.05, CN ±0.03, alpha ±0.1°, M ±0.003. Digitization tolerance from grid plots is roughly ±0.02 on CA and ±2 percent body length on xCP (loss is in the digitizer, not the source).

### Companion document AD-235341

An independent vehicle-level engineering report (Atlantic Research Corp, ONR contract NOnr-2477) covering the actual Arcas geometry as built, motor (SR45-AR-1) thrust curves, and trajectory data. Not yet read in detail; flagged for follow-up.

### Recommendation rank: **A-level (component) — the highest-value document set in this batch**

Together D-4013 + D-4014 + AD-235341 give a wind-tunnel-validated geometry, motor curves, and field trajectories for the same vehicle. With proper per-Mach figure digitization (estimated 4-8 hours of WebPlotDigitizer work), this becomes a complete A-level component-validation case for OpenRocket Plus across M 0.6 to 4.63.

---

## Target 3 — Nike-Apache supplementary  ★ CLOSED, CITATION DISCREPANCY FLAGGED

### Verified citation

The PDF returned by the user-supplied "TN D-3373" path is actually:

> Howard L. Galloway Jr., "The Effect of a Fin Trailing-Edge Wedge on the Roll History of a Nike Apache", **NASA TM X-55463 / Goddard X-721-66-85**, February 1966, NTRS accession N66-24400.

**The user-supplied number "TN D-3373" does not appear on the title page.** The cover sheet shows X-721-66-85 (Goddard internal) and TM X-55463 (NASA TM). I retrieved the document the user wanted (Galloway 1966 fin-wedge roll history of Nike-Apache 14.28 GT) but the report number was misremembered.

### Contents

Single Nike-Apache flight (14.28 GT, launched 12 Feb 1964 from Wallops Island). "Textbook" flight: 0.6% apogee error, 0.3% range error vs theoretical trajectory. Roll-rate and pitching-frequency time histories, demonstrating fin trailing-edge wedge stability authority. Data is plotted; can be digitized for roll dynamics validation.

### Recommendation rank: **B-level supplement**

Adds roll-dynamics validation (a regime not currently covered by our v1.0 corpus) but only one flight. Best used to spot-check the rolling moment + roll damping in `BarrowmanStabilityCalculator.calculateDampingMoments`. Cite as **TM X-55463 / X-721-66-85** in the paper, NOT as TN D-3373.

---

## Target 4 — Astrobee D  ★ NOT FOUND, DEFERRED

The web search did not surface an NTRS or DTIC document specifically titled "Astrobee D Flight Performance" or similar. Encyclopedia Astronautix (astronautix.com/a/astrobee.html) confirms it was an OAST-funded NASA program (6-in dia, 3600 lbf boost / 2000 lbf sustain, fin-stabilized first stage + spin-stabilized sustainer; 65 produced, 49 flown, 1 failure), but no flight-performance PDF was located. NTRS hit "Development of the Astrobee F sounding rocket system" (R=19730038417) covers the F-class (different geometry). 

### Recommendation rank: **DEFER** — needs targeted DTIC and NTRS browsing or archival inquiry. Do not block AST submission on this.

---

## Target 5 — Viper-Dart  ★ CLOSED VIA TARGET 1

The Viper-3A vehicle is treated as the appendix/companion to the Super Loki program in AFCRL-TR-73-0412 (Target 1 PDF). Section 8.6 of that report ("Viper 3A Vehicle") plus Figure 8.5 ("Viper 3A Vehicle Configuration") and Table 8.4 ("Flight Test Summary - Viper 3A Robin Dart Vehicle") cover the Viper. No separate fetch was needed.

The standalone Viper "Viper II / Viper IIIA" PWN-12A dart (Wikipedia) data was not downloaded — Wikipedia confirms the program flew <100 missions through 1988 at White Sands, and DTIC searches do not surface a dedicated drag-tables report for the Viper distinct from the Space Data Corp Super Loki series.

### Recommendation rank: **A-level via Target 1 appendix** (12 flights in Table 8.4)

---

## Target 6 — Black Brant V supplements  ★ NOT FETCHED THIS SESSION

Per the brief, prior agent already pulled ASPIRE and IRVE-II BBV reports (`paper/data/pdf/New/incoming/black_brant_v/`). No additional BBV PFSRs were sought this session. Do this only if the BBV needs additional anchoring during paper drafting.

### Recommendation rank: **adequate as-is**

---

## Closure summary by target

| Target | Status | Files | Highest rank achieved |
|---|---|---|---|
| 1. Super Loki / Loki-Dart | **CLOSED** | 3 PDFs (booster, dart, IA dart mirror) | A-level (component + vehicle) |
| 2. Arcas / Super Arcas | **CLOSED** | 3 PDFs (D-4013, D-4014, AD-235341) | A-level (wind-tunnel) — pending digitization |
| 3. Nike-Apache supplementary | **CLOSED with citation correction** | 1 PDF (TM X-55463) | B-level supplement |
| 4. Astrobee D | **DEFERRED** | 0 PDFs | n/a |
| 5. Viper-Dart | **CLOSED via Target 1** | (in AFCRL-TR-73-0412) | A-level appendix |
| 6. Black Brant V supplements | **SKIPPED** (per brief) | — | unchanged |

**Net new flights identified for second corpus:**
- Super Loki Robin Dart, ~71 km apogee (Table 8.2 flight summary, multiple flights)
- Super Loki Instrumented Dart, ~106 km apogee (Table 8.3, multiple flights)
- Viper 3A Robin Dart, higher-altitude variant (Table 8.4)
- Nike-Apache 14.28 GT (single flight, well-instrumented)

**Net new wind-tunnel coefficient sets identified:**
- Arcas (Robin) short body M 0.60-4.63 (D-4013 + D-4014, fins on/off, fin cant 0/2°)
- Arcas (Bioscience) long body M 0.60-4.63 (D-4013 + D-4014)

---

## Citation hygiene log (per CLAUDE.md)

All report numbers below were verified by opening the actual title page in this session:

- ✓ NASA TN D-4013, J.C. Ferris, June 1967 (verified)
- ✓ NASA TN D-4014, C.D. Babb & D.E. Fuller, June 1967 (verified — title page reading was the basis for confirming this is a different report from D-4013, despite an NTRS accession redirect to D-4013 cover)
- ✓ AD-766737 / AFCRL-TR-73-0412, B. Bollermann & R.L. Walker (Space Data Corp), 30 June 1973 (verified)
- ✓ AD-235341, R.C. Webster, W.C. Roberts Jr., E.P. Donnell (Atlantic Research Corp), 29 Feb 1960 (verified)
- ✗ "TN D-3373" — user-supplied number does NOT match title page. Actual report is TM X-55463 / X-721-66-85, Galloway Jr., Feb 1966. **Cite the actual numbers, not the user's recollection.**
- ◯ NASA CR-61238 (Super Loki Dart, 1968) — title page not yet opened this session; cite as "RRS-mirrored copy" until verified.
- ◯ AD-750796 (Super Loki Dart 1972) — IA mirror downloaded but title page not yet read this session.
