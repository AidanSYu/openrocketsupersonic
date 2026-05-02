# PDF Inventory: paper/data/pdf/New/ — 2026-05-02

Reviewer: Claude (read-only research task for AST submission).
All PDFs in `paper/data/pdf/New/` were inspected at title-page / abstract level. Findings below are taken **only** from text directly visible on the inspected pages; nothing is asserted from training-data memory. Anything not directly visible is flagged.

Existing repo PDFs that matter for de-duplication:
- `paper/data/pdf/aerospace-12-00371-v2.pdf` (Bunescu et al 2025, MDPI Aerospace, Basic Finner roll motion)
- `paper/data/pdf/ADA636861.pdf` (Basic Finner — current Cmq A-level source)
- `paper/data/pdf/DDOC_T_2015_0083_ALBISSER.pdf` (already in main, separately assessed)

---

## 1. `Validation_of_the_CFD_code_used_for_determination_.pdf`

- **Title:** Validation of the CFD code used for determination of aerodynamic characteristics of non-standard AGARD-B calibration model
- **Authors:** N. D. Vidanović, B. P. Rašuo, D. B. Damljanović, Dj. S. Vuković, D. S. Ćurčić
- **Year/Venue:** Thermal Science, 2014, Vol. 18, No. 4, pp. 1223-1233 (DOI 10.2298/TSCI130409104V)
- **Content visible:** SST k-omega CFD of an AGARD-B wing-body calibration model with a non-standard circular-arc nose, vs T-38 trisonic wind tunnel (VTI Belgrade, Mach 0.2-4.0). Aerodynamic coefficients (force/moment), nose shape sensitivity in Cm.
- **Classification:** **(a) CFD comparator** *and* **(d) wind-tunnel anchor** for AGARD-B family.
- **Recommendation:** Copy to `paper/data/pdf/`. Cite in **CFD validation discussion** and as a possible AGARD-B static-coefficient anchor (CN, Cm, CD vs Mach, AoA).
- **Duplicate?** No. AGARD-B is not present in the existing `paper/data/pdf/` set.

## 2. `aerospace-12-00371-v2 (1).pdf`

- **Title:** Numerical Study of the Basic Finner Model in Rolling Motion
- **Authors:** I. Bunescu, M.-V. Hothazie, M.-G. Stoican, M.-V. Pricop, A.-I. Onel, T.-P. Afilipoae
- **Year/Venue:** Aerospace (MDPI) 2025, 12, 371 (DOI 10.3390/aerospace12050371)
- **Content visible:** URANS k-epsilon realizable, Basic Finner & Modified Basic Finner, Mach 0.4 / 0.95 / 1.6 / 2.5 / 3.5, AoA 0-50°, full 360° roll. Reports Clp, Cnp, CYrp, plus static coefs.
- **Classification:** **(a) CFD comparator** for Basic Finner.
- **Recommendation:** **DO NOT copy — duplicate.** Identical filename `aerospace-12-00371-v2.pdf` already exists in `paper/data/pdf/`. The file size (13.1MB) matches; the "(1)" suffix indicates a re-download.
- **Duplicate?** **Yes — exact duplicate of existing repo file.** Delete from `New/` or leave there as a working copy; do not stage in main folder.

## 3. `AD1008468.pdf`

- **Title:** Computational Fluid Dynamics (CFD) Simulations of a Finned Projectile with Microflaps for Flow Control
- **Authors:** Jubaraj Sahu
- **Year/Venue:** ARL-TR-7660, US Army Research Laboratory, April 2016
- **Content visible (front matter only):** ARL technical report on CFD of a finned projectile with microflap actuators for flow control. AD1008468 corresponds to the DTIC accession number on the ARL TR.
- **Classification:** **(a) CFD comparator** (finned projectile CFD) — but flow-control configuration, not a clean Basic Finner baseline. Useful as supporting CFD literature, not as a primary anchor.
- **Recommendation:** Copy to `paper/data/pdf/` as background; cite in **related work / CFD methods** discussion. Probably not a Cmq source — needs deeper read to confirm. **Flagged: I read only front matter; cannot confirm whether dynamic derivatives are reported.**
- **Duplicate?** No.

## 4. `Computational_determination_of_dyna.pdf`

- **Title:** Computational Determination of Dynamic Stability Derivatives
- **Authors:** Janusz Sznajder
- **Year/Venue:** Transactions on Aerospace Research, Vol. 281 No. 4/2025, pp. 98-121, Łukasiewicz Institute of Aviation, Warsaw (DOI 10.2478/tar-2025-0021)
- **Content visible:** CFD methods for dynamic stability derivatives in ANSYS Fluent: (i) Moving Reference Frame, (ii) Forced Oscillation Method, (iii) Indicial Response Method. Applied to **Basic Finner missile** (high-speed, low-alpha) and SZD-9 Bocian glider. Pitch-damping sum (Cmq + Cm-alphadot) reported and compared against **experimental data** for Basic Finner. Notes a discontinuity at M=1.2.
- **Classification:** **(a) CFD comparator** for Basic Finner. Potentially **(b) indirect Cmq cross-check** if it digitises external Basic Finner experimental Cmq from yet another source.
- **Recommendation:** Copy to `paper/data/pdf/`. **High value** — this is a 2025 open-access paper that directly cites Basic Finner experimental Cmq data and runs three independent CFD methods. Cite in **dynamic stability validation** section. Worth a follow-up read to identify which "experimental data" they used (might point to another digitisable Cmq source independent of ADA636861).
- **Duplicate?** No.

## 5. `2024-SACup-ProjectTechnicalReport.pdf`

- **Title:** Project Prometheus — Team 71 Project Technical Report to the 2024 Spaceport America Cup
- **Authors:** Princeton Rocketry (Vazquez, Ji, Wang, Wallace, Olszowka, Abiani, Keuler)
- **Year:** 2024 (filed 2024 SA Cup)
- **Content visible:** 5.5"-diameter fiberglass rocket, AeroTech O5500X, target apogee 30,000 ft, OpenRocket and AeroFinSim simulations. Standard amateur SACup-class flight.
- **Classification:** **(c) flight corpus candidate** — but **same population class** as our existing 25-flight v1.0 corpus (mostly amateur SACup, 30k ft target). Adding more SACup is incremental, not a true second corpus.
- **Recommendation:** Copy if we want to **expand v1.0 corpus** rather than open a second class. For AST defence we need geometry, motor, and *measured apogee* (post-flight altimeter reading) — front matter does not yet show flight outcome. **Worth deeper read to extract apogee + altimeter telemetry.** Marginal value alone.
- **Duplicate?** No.

## 6. `40_project_report.pdf`

- **Title:** Design and Construction of a Solid Experimental Sounding Rocket, Amy — Team 40 Project Technical Report to the 2018 Spaceport America Cup
- **Authors:** SunrIde / University of Sheffield (Kalra, Kutty, Lennard, Lim, Birakasan, Rontogiannis, Schiona, Seniuc; staff Fedun, Verth)
- **Year:** 2018
- **Content visible:** 6"-diameter, Cesaroni Pro98 M3400 (later switched to 8634-M6400-VM-P per thrust curve), 10,000 ft target, OpenRocket-designed. Single-stage subsonic.
- **Classification:** **(c) flight corpus candidate** — SACup amateur class, **subsonic**. Same population as v1.0.
- **Recommendation:** Same as Prometheus. Copy if extending v1.0; not a second corpus by itself. **Need flight result from later sections.**
- **Duplicate?** No.

## 7. `65_project_report.pdf`

- **Title:** Team 65 Project Technical Report to the 2018 Spaceport America Cup
- **Authors:** UIC (Valenzeno, Cruz, Maksimowicz×2, Habel, Pekala, Begalowski, Stolz)
- **Year:** 2018
- **Content visible:** 4"-diameter G12 fiberglass, 98mm 6-grain SRAD motor reusing AeroTech 15360 case, 25-30k ft target, Von Karman 5.5:1 nose, three fins.
- **Classification:** **(c) flight corpus candidate** — SACup amateur, same population.
- **Recommendation:** Same — incremental v1.0 corpus expansion. **Need flight result.**
- **Duplicate?** No.

## 8. `100_Project-Report.pdf`

- **Title:** Project TELL — Team 100 Project Technical Report to the 2018 Spaceport America Cup
- **Authors:** ARIS (ETH Zurich + HSLU)
- **Year:** 2018
- **Content visible:** 150 mm diameter, AeroTech M2400, 10,000 ft target, dual deployment, **active air-brake control system** (3 air brakes). 18.65 kg dry mass, 2419 mm length.
- **Classification:** **(c) flight corpus candidate** — SACup-class but active control complicates clean aero validation; recoverable air-brake telemetry might be useful.
- **Recommendation:** Lower priority for v2 corpus — air brakes confound free-flight aero comparison unless brake duty cycle is logged and modelled. **Need flight result.**
- **Duplicate?** No.

## 9. `Kobald2018A Record Flight of the Hybrid Sounding Rocket HEROS 3_JSASS.pdf`

- **Title:** A Record Flight of the Hybrid Sounding Rocket HEROS 3
- **Authors:** Kobald, Schmierer, Fischer, Tomilin, Petrarolo (HyEnD, Stuttgart / DLR Hardthausen)
- **Year/Venue:** Trans. JSASS Aerospace Tech. Japan, Vol 16 No 3 pp. 312-317 (2018), DOI 10.2322/tastj.16.312
- **Content visible:** HEROS 3, hybrid 10 kN paraffin/N2O, **launched 8 Nov 2016 from Esrange (Sweden), apogee 32,300 m (106,000 ft)**, 7.5 m, 75 kg dry. World altitude record for student-built hybrid. ASTOS trajectory simulation cited in text; on-board GPS data; 80° flat launch angle.
- **Classification:** **(c) second flight corpus candidate** — **HIGH VALUE**. This is *not* SACup-class: hybrid propulsion, professional/large student-team, European launch site, 100 kft altitude regime, peer-reviewed publication-grade flight data. Distinct vehicle population from our amateur SACup database.
- **Recommendation:** **Copy to `paper/data/pdf/` immediately.** Best single PDF in the New folder for the second-corpus gap, *if* we can extract or obtain the underlying GPS/IMU telemetry. Cite in **Section: Generalisation across flight regimes**. **Flagged:** the publication shows a trajectory plot but does not necessarily ship raw telemetry; we may need to contact HyEnD/DLR for the time-series. Single flight, so it cannot close the second-corpus gap alone — would need to be combined with peer flights (HEROS 1/2, MIRAS, or ESA/DLR REXUS launches).
- **Duplicate?** No.

---

## Summary table

| PDF | Class | Recommend copy to main pdf/ | Cite from |
|---|---|---|---|
| Validation_of_the_CFD_code_AGARD-B (Vidanović 2014) | a, d | Yes | CFD validation; AGARD-B WT anchor |
| aerospace-12-00371-v2 (1) (Bunescu 2025) | a | **No (duplicate)** | already cited |
| AD1008468 / ARL-TR-7660 (Sahu 2016) | a (background) | Yes (low priority) | CFD related work |
| Computational_determination_of_dyna (Sznajder 2025) | a, b? | **Yes (high priority)** | Dynamic stability validation |
| 2024-SACup Prometheus | c (incremental) | Optional | corpus v1.x extension |
| 40_project_report SunrIde Amy 2018 | c (incremental) | Optional | corpus v1.x extension |
| 65_project_report UIC 2018 | c (incremental) | Optional | corpus v1.x extension |
| 100_Project TELL ARIS 2018 | c (low) | Optional | air-brake-aware corpus |
| Kobald 2018 HEROS 3 | c (**second-corpus seed**) | **Yes (high priority)** | second-corpus / generalisation |
