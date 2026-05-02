# Second Corpus Candidates — Professionally Instrumented Sounding Rockets

**Date:** 2026-05-02
**Author:** Research collected by Claude under user direction
**Purpose:** Identify a second, *independent* validation corpus to complement the 25-flight amateur/SACup corpus on Zenodo (DOI 10.5281/zenodo.19976138). A reviewer would call the amateur corpus circular and amateur-scoped; this dossier prepares an answer using NASA / Bristol Aerospace / NSROC sounding rockets — vehicles with documented geometry, motor curves, and radar-tracked trajectories. Candidates: Black Brant V, Black Brant IX, Terrier-Improved Orion, Nike-Apache.

**Citation hygiene:** Every entry below was either downloaded successfully (PDF on disk, magic-bytes-verified) or seen on a search-engine result page. Entries marked `[unverified]` were *not* downloaded — they are search hits whose content was not directly inspected, so report numbers/titles should not be relied on without opening the PDF first. CLAUDE.md NACA/NASA citation policy applies.

---

## Documents Downloaded

All saved under `paper/data/pdf/New/incoming/{vehicle}/`. PDF magic bytes confirmed (`%PDF-1.4`/`1.5`/`1.6`).

### Nike-Apache (`nike_apache/`)

| File | Size | Source | What it contains (per search-result text) |
|------|-----:|--------|--------------------------------------------|
| `NASA_X-721-66-568_Nike_Apache_Performance_Handbook.pdf` | 14.2 MB | NTRS (`citations/19670015760`) | NASA Goddard Performance Handbook for Nike-Apache (X-721-66-568, 1966). Aerodynamic + performance data, vehicle stability, speed/altitude/range vs time and payload, flight-test boost-phase lateral/longitudinal acceleration data. |
| `IA_DTIC_AD0687441_Apache_WindTunnel.pdf` | 5.3 MB | Internet Archive mirror of DTIC AD0687441 | Wind-tunnel tests of 0.355-scale Apache sounding rocket at Mach 2-6, Re 6.5M-23.9M, AoA -5 to +15 deg. Spin rates 0-4000 rpm, fin cant 0/-1/±2 deg. Aerodynamic characteristics including spin effects. |
| `IA_Dembrow_Theoretical_vs_Actual_NikeApache.pdf` | 1.5 MB | Internet Archive mirror of NTRS 19650076460 | Dembrow & Jamieson (NASA Goddard, 1964/65), "Comparison of theoretical with actual Nike-Apache sounding-rocket performance." Direct theory-vs-flight benchmark. |
| `NASA_X-721-66-77_NikeTomahawk_Preliminary_Performance.pdf` | 10.0 MB | NTRS (`citations/19660010190`) | Mayo (Goddard, 1966), Nike-Tomahawk Preliminary Performance Studies — particle trajectory, payload-mass effects, static margin/stability, dynamic motion, aerodynamic running-load distribution. Tomahawk shares Nike booster with Nike-Apache. |

### Black Brant V (`black_brant_v/`)

| File | Size | Source | What it contains |
|------|-----:|--------|--------------------------------------------|
| `IA_DTIC_AD0733141_BlackBrant_Churchill.pdf` | 1.2 MB | Internet Archive mirror of DTIC AD0733141 | "Black Brant Rocket AAF-VB-32 Launched at Churchill" — flight launch report from Churchill Research Range; telemetry, magnetic-tape recordings, technical data. |
| `IA_DTIC_AD0696100_BlackBrant_17_750_751.pdf` | 2.9 MB | Internet Archive mirror of DTIC AD0696100 | "Black Brant 17.750 and 17.751" — DTIC report on two specific flight vehicles (Wallops 17.XXX series = Black Brant V class). |
| `NASA_NTRS_20200002361_ASPIRE_Reconstruction.pdf` | 2.6 MB | NTRS (`citations/20200002361`) | "Reconstruction of the Advanced Supersonic Parachute Inflation Research Experiment" — ASPIRE used Terrier-Black Brant from Wallops; document quotes vehicle apogee 54.8 km @ Mach 1.1 (deployment condition); Brant burnout peak Mach 3.38. |
| `NASA_NTRS_20190028247_ASPIRE_Aero_Models.pdf` | 2.7 MB | NTRS (`citations/20190028247`) | "ASPIRE Aerodynamic Models and Flight Performance" — companion paper covering aero models used for ASPIRE Terrier-Black Brant trajectory analysis. |

### Black Brant IX (`black_brant_ix/`)

| File | Size | Source | What it contains |
|------|-----:|--------|--------------------------------------------|
| `NASA_NTRS_20100031103_IRVE-II_Trajectory_Reconstruction.pdf` | 1.7 MB | NTRS (`citations/20100031103`) | "IRVE-II Post-Flight Trajectory Reconstruction" — Inflatable Re-entry Vehicle Experiment II flew on a Terrier-Black Brant IX from Wallops; reconstructed full trajectory with Mach. |
| `NASA_NTRS_20110012170_IRVE-4_Overview.pdf` | 4.7 MB | NTRS (`citations/20110012170`) | "IRVE-4 Overview" — Inflatable Re-entry Vehicle Experiment 4, Black Brant IX class. Mission overview + planned/post-flight performance. |

### Terrier-Improved Orion (`terrier_orion/`)

| File | Size | Source | What it contains |
|------|-----:|--------|--------------------------------------------|
| `MIT_NASA_SRHB_handbook.pdf` | 7.1 MB | MIT Snebulos mirror (`snebulos.mit.edu/.../NASA/SRHB.pdf`) | NASA "Sounding Rocket Program Handbook" (Wallops 810-HB-SRP). Contains vehicle data sheets and altitude/time profiles for *all* candidates incl. Terrier-Improved Orion. Saved here because Terrier-Orion has no other primary download yet. |

### Cross-vehicle (`handbook/`, `general/`)

| File | Size | Source | Use |
|------|-----:|--------|--------------------------------------------|
| `handbook/MIT_NASA_810-HB-SRP_Sounding_Rocket_Handbook.pdf` | 5.3 MB | snebulos.mit.edu mirror of NASA Wallops 810-HB-SRP | Older edition of the same Wallops handbook — vehicle data sheets for Black Brant V, Nike-Apache, Terrier-Orion, etc. |
| `handbook/NASA_Sounding_Rocket_Program_Handbook_2023.pdf` | 5.9 MB | nasa.gov direct (`/wp-content/uploads/2023/09/...`) | Current 2023 edition of the Wallops Sounding Rocket Program Handbook. Vehicle catalog + performance plots. |
| `general/NASA_NTRS_19680016252_Dynamic_Stability_Sounding_Rockets.pdf` | 2.2 MB | NTRS (`citations/19680016252`) | "Final Report for Dynamic Stability Study for Sounding Rockets" (1968) — generic Cmq / dynamic-stability methodology applicable to all four candidates. |

**Total downloaded: 13 PDFs, ~67 MB.** All on disk, magic-bytes-verified.

---

## Documents Identified But NOT Downloaded

These were seen on search-result pages but either failed to download (Wallops vehicle datasheets — SSL handshake failures from this network on `sites.wff.nasa.gov`, both via curl and PowerShell) or are citation-only NTRS entries (no download endpoint).

| Document | Source | Status / Why not downloaded |
|----------|--------|------------------------------|
| Wallops Code 810 datasheet `Black_Brant_V.pdf` (21.XXX) | sites.wff.nasa.gov/code810/vehicles/ | TLS connection drops from this environment. Public, free. *Try from another machine.* |
| Wallops Code 810 datasheet `Black_Brant_IX.pdf` (36.XXX) | sites.wff.nasa.gov/code810/vehicles/ | Same SSL issue. |
| Wallops Code 810 datasheet `Terrier_Imrprove Orion.pdf` (41.XXX) | sites.wff.nasa.gov/code810/vehicles/ | Same SSL issue — high priority retry target. |
| Wallops Code 810 datasheet `Black_Brant_XII.pdf` (40.XXX) | sites.wff.nasa.gov/code810/vehicles/ | Same SSL issue. |
| NTRS 19760052292 "Nike-Black Brant V development program" (Sevier/Payne/Ott/Montag) | NTRS citation page only | NTRS download endpoint returned 404 — this older Bristol Aerospace conference paper appears to be **citation-only** on NTRS. [unverified] |
| NTRS 19790041756 "Nike Black Brant V high altitude dynamic instability characteristics" | NTRS citation page only | Did not attempt download. [unverified] |
| NTRS 19640013420 "Theoretical vs actual Nike-Apache sounding rocket performance" | NTRS citation; AIAA mirror also exists | The Internet Archive mirror under accession 19650076460 (Dembrow) is downloaded. The 19640013420 NTRS record may be the same report or a duplicate. [unverified — same paper?] |
| AIAA `arc.aiaa.org/doi/10.2514/3.27633` "Theoretical vs actual Nike-Apache sounding rocket performance" | AIAA digital library | Paywalled. Skip per instructions. |
| AIAA `arc.aiaa.org/doi/abs/10.2514/6.1982-1741` "Black Brant X — low cost development" | AIAA digital library | Paywalled. Skip. |

---

## Ranking — Suitability as a Second Corpus

Criteria: (1) primary public-domain documentation available; (2) wind-tunnel-derived aero coefficients available; (3) flight test data (radar-tracked trajectory, apogee, Mach) available; (4) Mach envelope overlaps OpenRocket Plus's supersonic regime (M 1-5+); (5) geometry sufficient to model in OpenRocket; (6) license is US-government-public or CC-BY (no ITAR / no paywall).

### #1. **Nike-Apache** — STRONGEST CANDIDATE

- **Pros:** Three primary documents in hand: a full Performance Handbook (X-721-66-568) with stability + flight-test boost-phase data, a Mach 2-6 wind-tunnel report with AoA and spin sweeps (DTIC AD0687441), and an explicit theory-vs-flight comparison paper (Dembrow). Mach envelope (Apache stage hits ~M 5+) directly overlaps the supersonic regime ORP targets. All US government, all public-domain.
- **Cons:** 1960s vehicle, fewer recent flights to validate against — but this is *exactly the kind of "professional, instrumented, decades of data" reviewer asks for.*
- **Best use:** Primary second-corpus vehicle. Rebuild geometry from handbook drawings; compare ORP Cd(M), CNa(M), CP(M) against AD0687441 wind-tunnel curves; compare full trajectory against Dembrow theory-vs-flight.

### #2. **Black Brant V (incl. Terrier-Black Brant ASPIRE)** — STRONG

- **Pros:** Two ASPIRE NTRS papers (2019/2020) provide *modern* trajectory reconstruction with Mach data on Terrier-Black Brant — much higher data fidelity than 1960s Nike-Apache. Two DTIC flight reports (AD0696100, AD0733141) provide additional flight cases. Mach envelope to ~M 3.4 burnout. Vehicle widely flown for 60+ years; geometry well documented in Wallops handbooks.
- **Cons:** No standalone wind-tunnel coefficient report yet found on public archives — aero coefficients have to come from the ASPIRE aero-models paper (which references but may not fully publish them) or be back-derived from ASPIRE trajectory reconstructions.
- **Best use:** Trajectory-level validation (apogee, max Mach, dynamic pressure profile) against ASPIRE flights. Cd-curve cross-check using ASPIRE aero-models report.

### #3. **Black Brant IX (Terrier-Black Brant IX, IRVE)** — GOOD

- **Pros:** IRVE-II trajectory reconstruction (2010) is a clean, modern, NASA-published flight-test reference for Terrier-Black Brant IX. IRVE-4 overview adds a second mission. Full trajectory + Mach available.
- **Cons:** Only two trajectory documents in hand; aero coefficients not directly published. Same vehicle family as BB-V so partly redundant.
- **Best use:** Add 1-2 IRVE flights to the BB-V corpus to broaden the Mach envelope.

### #4. **Terrier-Improved Orion** — WEAK without retry

- **Pros:** Modern, frequently flown vehicle (RockOn / RockSat-C every year out of Wallops); geometry and motor curves are in the SRHB. Smallest of the four — Mach envelope similar to amateur high-power rockets, *which is actually a strength* if you want apples-to-apples comparison with the v1.0 corpus.
- **Cons:** No primary aero or flight-test report located in this session beyond what is inside the SRHB handbook. The Wallops vehicle datasheet (`Terrier_Imrprove Orion.pdf`) failed to download from this environment due to SSL — this should be retried; it likely contains the geometry and altitude/time profile.
- **Best use:** Geometry + motor reference for an OR model; trajectory targets would require contacting NSROC / Wallops or retrieving Wallops past-mission summaries.

---

## Recommendation

**Top recommendation: Build the second corpus around Nike-Apache as the anchor vehicle, augmented with 2-3 ASPIRE / IRVE Black Brant flights.**

This combination gives the reviewer: (a) wind-tunnel coefficients (Apache AD0687441), (b) a 1960s flight-test theory-vs-actual benchmark (Dembrow), (c) modern radar-tracked trajectory reconstructions (ASPIRE 2020, IRVE-II 2010), (d) full government-public licensing, (e) Mach envelope from M ~1 through M 5+ that exercises the same supersonic models the v1.0 amateur corpus does. Total of ~5-7 independent flights from a *completely different vehicle class, era, and instrumentation chain* than the SACup corpus — directly answers the "circular / amateur" reviewer concern.

**Next steps (out of scope for this session):**
1. Retry the four Wallops Code 810 datasheets from a different network — they are the canonical OpenRocket-buildable geometry sources.
2. Open `IA_DTIC_AD0687441_Apache_WindTunnel.pdf` and digitize Cd, CN, Cmq tables vs Mach.
3. Open `NASA_X-721-66-568_Nike_Apache_Performance_Handbook.pdf` and extract the geometry drawings + flight-test acceleration traces.
4. Open both ASPIRE PDFs and pull apogee/Mach profile points for benchmark comparison.
5. Verify that NTRS 19640013420 and IA 19650076460 are the same Dembrow report (suspected duplicates).
