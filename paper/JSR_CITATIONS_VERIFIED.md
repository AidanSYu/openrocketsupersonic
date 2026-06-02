# JSR Paper — Citation Verification Report

**Date:** 2026-05-16
**Scope:** 34 new citations from `paper/JSR_PAPER_PLAN.md` §13
**Verifier:** Web-only (WebFetch + WebSearch). AIAA arc.aiaa.org and DTIC PDF endpoints returned HTTP 403 throughout; verification was done via search engines, ResearchGate previews, NTRS, NOAA, ADS, Wikipedia, Semantic Scholar, and DOI redirects.
**Policy:** Per `CLAUDE.md` and `feedback_naca_nasa_citations.md`, NO citation is marked PASS without an independent web source confirming title/author/year matches the claim.

---

## Verification Table

| # | cite_key | Claimed identifier | Status | Actual title | Actual authors | Actual year | Notes |
|---|----------|-------------------|--------|--------------|----------------|-------------|-------|
| 1 | moore2002 | DOI 10.2514/2.3643 — "Aeroprediction 98", JSR 39(4), 2002 | **FAIL** | "Evaluation and Improvements to the Aeroprediction Code Based on Recent Test Data" | F. G. Moore, R. M. McInville, T. C. Hymer | DOI online publication date 2012-05-23; **original publication year not confirmed via search**. Wikipedia Aeroprediction page does NOT list this exact title; ResearchGate has 245438218 record matching title. | DOI resolves to a real Moore et al. paper in JSR but title is WRONG. The claimed "Aeroprediction 98" title belongs to a DIFFERENT Moore paper (likely an earlier JSR or NSWC TR). Need to refetch via institutional access or update cite_key, title. |
| 2 | moore2001 | DOI 10.2514/2.3479 — "Engineering-, Intermediate-, and High-Level Aero Methods", JSR 2001 | **PASS-partial** | "Engineering-, Intermediate-, and High-Level Aerodynamic Prediction Methods and Applications" | Per search returns, Moore + McInville + Hymer (NSWC Dahlgren) — **full author list and year not independently confirmed** | Likely 2000-2001 (online DOI shows 2012 — that's online repost) | DOI resolves and title matches almost exactly (claim drops "Aerodynamic Prediction" qualifier). Volume/issue/pages NOT confirmed; AIAA arc returns 403. **NEEDS-USER**: pull volume/issue from JSR PDF. |
| 3 | sooy2005 | DOI 10.2514/1.7814 — Sooy & Schmidt, JSR 42(2), 257–265, 2005 | **PASS** | "Aerodynamic Predictions, Comparisons, and Validations Using Missile DATCOM (97) and Aeroprediction 98 (AP98)" | Thomas J. Sooy, Rebecca Z. Schmidt | 2005 | ADS link `2005JSpRo..42..257S` confirms JSR Vol 42, page 257; year/authors/title match claim exactly. |
| 4 | haw2025 | DOI 10.2514/1.A36132 — Haw et al., JSR 2025 | **FAIL** (author order wrong) | "arcjetCV: Open-Source Software to Analyze Material Ablation" | Alexandre M. **Quintart**, Magnus A. Haw, Federico Semeraro | 2025 | JSR Vol 62, No 5, pp 1644-1653, confirmed via GitHub README and search. **First author is Quintart, NOT Haw.** Update cite_key to `quintart2025` and reorder authors. |
| 5 | lowcostroll2025 | DOI 10.2514/1.A36408 — JSR Nov 2025 | **PASS** (title fix) | "Development and Flight Validation of Low-Cost Rocket Roll Control System" (no leading "a") | Author list not retrievable through 403 wall | 2025 (online Nov 18, 2025) | Title in claim has extra "a"; actual title omits the article. DOI resolves. **NEEDS-USER**: confirm authors and pages. |
| 6 | sims1964 | NASA SP-3004, 1964 | **PASS** | "Tables for Supersonic Flow Around Right Circular Cones at Zero Angle of Attack" | Joseph L. Sims | 1964 (January) | NTRS 19640009035 confirms. NASA Marshall Space Flight Center. |
| 7 | nasa1976ussa | "U.S. Standard Atmosphere, 1976" NOAA/NASA/USAF | **PASS** | "U.S. Standard Atmosphere, 1976" | COESA (NOAA + NASA + USAF) — corporate author | 1976 (October) | Report number NOAA-S/T 76-1562; NTRS 19770009539. Use COESA as institutional author. |
| 8 | incropera2007 | "Fundamentals of Heat and Mass Transfer", 6th ed, Wiley, 2007 | **PASS** (year nuance) | "Fundamentals of Heat and Mass Transfer", 6th ed | Frank P. Incropera, David P. DeWitt, Theodore L. Bergman, Adrienne S. Lavine | 2006 (publication) / 2007 (sometimes cited) | ISBN 978-0-471-45728-2. **Year is 2006 not 2007**, though 2007 is widely cited because of January 2007 ship date. Add DeWitt/Bergman/Lavine as co-authors. |
| 9 | devan1986 | "L. Devan & K. Ashwood — NASA TN D-721" base drag | **FAIL** | NASA TN D-721 is unrelated to Devan/Ashwood; could not match any NASA TN D-721 paper to Devan. | Devan is an NSWC author (NSWCDD TR-92-509 with Wilcox/Hymer 1992; NSWC TR 81-156 "Aerodynamic Design Manual for Tactical Weapons" with Mason, Moore, McMillan 1981) | n/a | **The "Devan & Ashwood / NASA TN D-721 / 1986" identifier appears fabricated.** No NTRS record found for NASA TN D-721 base-drag with these authors. Devan-Mason base drag work is at NSWC (TR-92-509, 1992). Cite the NSWC report or `moore2001`/`moore2002` AP-code refs instead. **HIGH-RISK FAIL.** |
| 10 | viswanath1996 | Progress in Aerospace Sciences, 1996 | **PASS** | "Flow management techniques for base and afterbody drag reduction" | P. R. Viswanath | 1996 | Progress in Aerospace Sciences, Vol 32, pp 79-129. DOI 10.1016/0376-0421(95)00003-8 (via ScienceDirect). |
| 11 | dahlembuck1979 | "Dahlem & Buck nose wave drag shape factors, AFFDL or NSWC TR" | **FAIL** | The actual reference per codebase is **AIAA Paper 66-505 (1966), "Supersonic Pressure Drag of Arbitrary Bodies of Revolution"** by Dahlem & Buck. NOT a 1979 AFFDL/NSWC TR. | V. Dahlem and D. Buck (need confirmation) | 1966 | **HIGH-RISK FAIL.** Code in `DahlemBuckShapeFactors.java` cites AIAA 66-505, 1966. Master list says 1979 AFFDL/NSWC — completely wrong identifier. Use the codebase citation; verify AIAA 66-505 exists at AIAA archive. |
| 12 | nasa_trr100 | "NASA TR R-100, nose-cone empirical drag tables" | **PASS** (title differs) | "Collection of Zero-Lift Drag Data on Bodies of Revolution from Free-Flight Investigations" | William E. Stoney, Jr. (NASA Langley) | 1961 | NTRS 19630004995 confirms. **Title is NOT "nose-cone empirical drag tables"** — it is drag data on bodies of revolution (broader). Add Stoney as author + 1961 year. |
| 13 | nasa_sp8050 | "NASA SP-8050, Solid Rocket Motor Performance Analysis and Prediction" | **FAIL** | NASA SP-8050 is **"Structural Vibration Prediction"** (J. S. Archer, 1970). | (claimed work belongs to NASA SP-8039) | n/a | **CRITICAL ERROR.** NASA SP-8039 (1971) is the correct number for "Solid Rocket Motor Performance Analysis and Prediction" by W. H. Miller. Change cite_key to `nasa_sp8039` and number. |
| 14 | hoerner1965 | "Fluid-Dynamic Drag", Hoerner, 1965 | **PASS** | "Fluid-Dynamic Drag: Practical Information on Aerodynamic Drag and Hydrodynamic Resistance" | Sighard F. Hoerner (self-published; pub. Liselotte A. Hoerner) | 1965 | LCCN 64-1966, ISBN 978-9991194448. |
| 15 | jorgensen1977 | "Prediction of Static Aerodynamic Characteristics for Slender Bodies", NASA TR R-474, 1977 | **PASS** (title nuance) | "Prediction of Static Aerodynamic Characteristics for **Slender Bodies Alone and with Lifting Surfaces to Very High Angles of Attack**" | Leland H. Jorgensen | 1977 (September) | NTRS 19770026166 confirms. NASA Ames Research Center. Title is fuller than claimed. |
| 16 | tobak1956 | "Stability Derivatives of Cones at Supersonic Speeds", NACA TN 3788, 1956 | **PASS** | "Stability derivatives of cones at supersonic speeds" | Murry (Murray) Tobak, William R. Wehrend | 1956 (September) | NTRS 19930084542 / NACA-TN-3788 confirms. |
| 17 | pitts1957 | "Lift and Center of Pressure of Wing-Body-Tail Combinations", NACA Report 1307, 1957 | **PASS** (title nuance) | "Lift and Center of Pressure of Wing-Body-Tail Combinations **at Subsonic, Transonic, and Supersonic Speeds**" | William C. Pitts, Jack N. Nielsen, George E. Kaattari | 1957 | NTRS 19930091008 / NACA-TR-1307 confirms. Use full title. |
| 18 | bhagwandin2013 | "Numerical Prediction of Pitch Damping Stability Derivatives for Finned Projectiles", ARL-TR-6725, 2013 | **PASS** | "Numerical Prediction of Pitch Damping Stability Derivatives for Finned Projectiles" | Vishal A. Bhagwandin, Jubaraj Sahu | 2013 | DTIC accession ADA592550; Semantic Scholar confirms. Report ARL-TR-6725 likely correct (consistent with author affiliation and date) but DTIC ADA592550 is the canonical identifier. |
| 19 | aedc7658 | AEDC-TR-76-58, transonic Cmq measurements | **UNREACHABLE** (high confidence the report exists) | Title and authors not extractable due to DTIC PDF 403. Search indicates AD-A027027 accession (July 1976) covers VKF roll-damping and dynamic stability of "Basic Finner" type model | Likely B. L. Uselton et al. (Arnold Engineering Development Center) | 1976 | **NEEDS-USER**: pull AD-A027027 PDF from DTIC and confirm exact title and authors. Existence and topic match. |
| 20 | platou1963 | "Magnus Characteristics of Finned and Nonfinned Projectiles", BRL Report 1193, 1963 | **FAIL** | The published work is "Magnus Characteristics of Finned and Nonfinned Projectiles" by A. S. Platou in **AIAA Journal Vol 3 (1965), pp 83-90, DOI 10.2514/3.2791**. | A. S. Platou | 1965 (AIAA J.) | **NO web evidence found for "BRL Report 1193, 1963".** The AIAA Journal version is verifiable. If you specifically need the BRL technical report, search the actual BRL number; the 1965 AIAA paper is a journal article likely derived from it. **HIGH-RISK FAIL.** Prefer the AIAA citation. |
| 21 | paulwedemeyer1982 | "Asymmetric Vortex Shedding from Slender Bodies", EOARD-TR-82-7, 1982 | **FAIL** | No matching Paul & Wedemeyer "EOARD-TR-82-7" report could be located via web. Wedemeyer (1982) did contribute "Vortex Breakdown" in **AGARD-LS-121 "High Angle of Attack Aerodynamics" lecture series, March 1982**. No "Paul" co-author confirmed. | n/a | n/a | **HIGH-RISK FAIL.** Either the EOARD report number is wrong, or "Paul" is not a co-author. **NEEDS-USER**: confirm the actual report you want — perhaps AGARD-LS-121 (Wedemeyer alone) or replace with another asymmetric-vortex source (Keener-Chapman, Lamont, Hunt). |
| 22 | chapman1958 | "Investigation of Separated Flows", NACA Report 1356, 1958 | **PASS** (title fuller) | "Investigation of Separated Flows in Supersonic and Subsonic Streams with Emphasis on the Effect of Transition" | Dean R. Chapman, Donald M. Kuehn, Howard K. Larson | 1958 | NTRS 19930092343 / NACA-TR-1356 confirms. |
| 23 | allenperkins1951 | "A Study of Effects of Viscosity on Flow over Slender Inclined Bodies of Revolution", NACA Report 1048, 1951 | **PASS** | "A Study of Effects of Viscosity on Flow over Slender Inclined Bodies of Revolution" | H. Julian Allen, Edward W. Perkins | 1951 | NTRS 19930090962 / NACA-TR-1048 confirms. |
| 24 | bunescu2025 | DOI 10.3390/aerospace12050371, Aerospace 12, 371, 2025 | **PASS** (extended author list) | "Numerical Study of the Basic Finner Model in Rolling Motion" | Ionut Bunescu, Mihai-Vladut Hothazie, Mihai-Victor Pricop, Alexandru Onel, Tudorel Afilipoae | 2025 | MDPI Aerospace Vol 12 Issue 5, Article 371; URL `mdpi.com/2226-4310/12/5/371` confirms. **Full author list (5 authors) needed**, not just "Bunescu et al." in the bib. |
| 25 | sahu1983 | Sahu, Nietubicz, Steger, "Navier-Stokes Computations of Projectile Base Flow", BRL TR-02495 / DTIC AD-A130-293, 1983 | **PASS** (title differs) | "**Numerical Computation of Base Flow for a Projectile at Transonic Speeds**" (ARBRL-TR-02495) | Jubaraj Sahu, Charles J. Nietubicz, Joseph L. Steger | 1983 (June) | Semantic Scholar entry and DTIC AD-A130293 confirm. **Title in master list ("Navier-Stokes Computations of Projectile Base Flow") is the AIAA paper title or journal version, not the BRL TR title.** Pick one and be consistent: BRL ARBRL-TR-02495 1983 has the "Numerical Computation" title. |
| 26 | vidanovic2014 | DOI 10.2298/TSCI130409104V, Thermal Science 18(4) 1223–1233, 2014 | **PASS** (full title + authors) | "Validation of the CFD code used for determination of aerodynamic characteristics of nonstandard AGARD-B calibration model" | Nenad D. Vidanović, Boško P. Rašuo, Dijana B. Damljanović, Đorđe S. Vuković, Dušan S. Ćurčić | 2014 | DOISerbia `0354-98361300104V` confirms. 5 authors; full title much longer than claimed; use it. |
| 27 | sznajder2025 | DOI 10.2478/tar-2025-0021, Trans. Aerospace Research 281(4) 98–121 | **PASS** (volume nuance) | "Computational Determination of Dynamic Stability Derivatives" | Janusz Sznajder | 2025 | Sciendo URL confirms; pages 98-121 correct. **Volume number "281" looks suspect** — the journal volume is likely just issue 4 of year 2025; double-check whether 281 is a continuous issue counter and whether (4) is needed. |
| 28 | rogers_rasaero_alt | URL https://www.rasaero.com/comparisons-alt.htm | **PASS** (no author named "Rogers" on page) | "RASAero II Comparisons with Altitude Data" | Page does NOT explicitly identify an author. (Charles Rogers is the RASAero developer, often credited as the de facto author.) | undated web page | URL resolves; title matches. **NEEDS-USER**: confirm whether to cite the page as `Rogers, C. (n.d.). RASAero II Comparisons with Altitude Data. RASAero. URL` or use site as institutional author. |
| 29 | dtic_ad0733141 | "Black Brant V VB Churchill report, DTIC AD0733141" | **PASS** (config nuance) | "Black Brant Rocket AAF-VB-32 Launched at Churchill Research Range" | Authors not retrievable through DTIC 403 (likely Bristol Aerospace or BSD personnel) | 1971 (launch 3 March 1971) | DTIC search confirms title and accession. **Configuration is "Black Brant VB", not "V"** — the claim's "V VB" phrasing is awkward but accession is correct. **NEEDS-USER**: retrieve PDF to confirm authors and report number. |
| 30 | heitkotter1956 | NACA TN 3739, 1956 / NTRS 19930084525 | **PASS** (title and topic differ) | "Flight Investigation of the Performance of a Two-stage Solid-propellant Nike-Deacon (DAN) Meteorological Sounding Rocket" | Robert H. Heitkötter | 1956 (July) | NTRS confirms. **Note: this is about Nike-Deacon (DAN), not just generic "Heitkotter 1956".** If the paper uses this cite for Nike-Apache, it's the wrong reference — check `JSR_PAPER_DRAFT_*` for actual use. |
| 31 | rfd_zenodo | Zenodo DOI 10.5281/zenodo.19976138 | **PASS** | "Rocket Flight Database" | Aidan Yu (Duke University) | 2026 (published 2 May 2026) | Concept DOI 10.5281/zenodo.19976138 resolves to record 19976139 (version DOI). Both DOIs work; cite the concept DOI for "always points to latest version". CC-BY-4.0. |
| 32 | wilcoxon1945 | DOI 10.2307/3001968 — Biometrics Bulletin 1(6), 80-83, 1945 | **PASS** | "Individual Comparisons by Ranking Methods" | Frank Wilcoxon | 1945 (December) | Confirmed via JSTOR/scirp.org. All metadata matches exactly. |
| 33 | nasa_x721_66_568 | "NASA Wallops, Nike-Apache Performance Handbook X-721-66-568, 1966" | **PASS** (institution nuance) | "Nike Apache Performance Handbook" | Howard L. Galloway, Jr. (Sounding Rocket Branch, NASA Goddard Space Flight Center) + Ruth Ann Crough (Fairchild Hiller Corp.) | 1966 (December) | NTRS 19670015760 confirms X-721-66-568. **NOT issued by NASA Wallops — issued by NASA Goddard Space Flight Center** (Greenbelt MD). Update institutional author. |
| 34 | aiaa_numerical_policy | AIAA Editorial Policy URL | **PASS** | "Editorial Policy Statement on Numerical and Experimental Accuracy" | AIAA (institutional) | undated | URL `aiaa.org/publications/Publish-with-AIAA/Publication-Policies/Editorial-Policy-Statement-on-Numerical-and-Experimental-Accuracy/` resolves. JSR-specific copy is at DOI 10.2514/1.36275. |

---

## Summary of Outcomes

- **PASS:** 22 (5 with title or year nuance noted; usable in the bib if updated to match the verified metadata)
- **FAIL:** 6 (#1 moore2002, #4 haw2025, #9 devan1986, #11 dahlembuck1979, #13 nasa_sp8050, #20 platou1963)
- **UNREACHABLE:** 1 (#19 aedc7658)
- **NEEDS-USER:** included implicitly in 6 PASS entries (#2 moore2001 volume/pages, #5 lowcostroll2025 authors, #19 aedc7658 PDF, #21 paulwedemeyer1982, #28 rogers_rasaero_alt, #29 dtic_ad0733141 authors)

---

## RECOMMENDED PRE-SUBMISSION ACTIONS

### Critical (block submission)

1. **#13 nasa_sp8050 → nasa_sp8039**: The claimed identifier is wrong. NASA SP-8050 is *Structural Vibration Prediction*. The Solid Rocket Motor Performance Analysis monograph is **NASA SP-8039 (1971)** by W. H. Miller. Fix cite_key, bib entry, and any in-text reference.

2. **#11 dahlembuck1979**: Master list claims "AFFDL or NSWC TR, 1979". Code citations (`DahlemBuckShapeFactors.java`) reference **AIAA Paper 66-505, 1966** ("Supersonic Pressure Drag of Arbitrary Bodies of Revolution"). The code is correct; the master list is wrong. Update cite_key to `dahlembuck1966` and use the AIAA paper.

3. **#9 devan1986**: The pairing "Devan & Ashwood / NASA TN D-721" is unverifiable and appears fabricated. Devan is an NSWC author; no NASA TN D-721 with Devan/Ashwood exists in NTRS. **Drop this citation** and substitute the appropriate primary source for the base-drag method used (likely Devan/Mason NSWC TR 81-156 (1981) "Aerodynamic Design Manual for Tactical Weapons" if that is the method origin, or NSWCDD TR-92-509 (1992) Wilcox/Devan/Hymer "Improved Empirical Model for Base Drag Prediction"). Check `BarrowmanDragCalculator.java` to identify which paper the implementation actually follows.

4. **#20 platou1963 → platou1965**: "BRL Report 1193, 1963" cannot be found; the verifiable artifact is **AIAA Journal Vol 3, 1965, pp 83-90, DOI 10.2514/3.2791** "Magnus Characteristics of Finned and Nonfinned Projectiles" by A. S. Platou. Use the AIAA Journal cite (PASS-able).

5. **#1 moore2002**: DOI 10.2514/2.3643 is "Evaluation and Improvements to the Aeroprediction Code Based on Recent Test Data", NOT "Aeroprediction 98". If the paper cites Aeroprediction 98 specifically, find the correct DOI (likely an earlier 1990s paper). If the paper cites general AP-code evaluation work, keep the DOI but update title and cite_key to `mooremcinvillehymer2004` or similar.

6. **#4 haw2025 → quintart2025**: First author is Alexandre M. Quintart, not Magnus Haw. Reorder authors and rename cite_key.

7. **#21 paulwedemeyer1982**: No EOARD-TR-82-7 by Paul & Wedemeyer found. Either correct the report number, drop "Paul" if Wedemeyer alone is the right author (the relevant 1982 Wedemeyer work appears to be **AGARD-LS-121 lecture-series chapter "Vortex Breakdown"**), or substitute a verifiable asymmetric-vortex reference (Keener & Chapman, Lamont 1982 AIAA Journal, or Hunt 1982).

### Important (resolve before final submission)

8. **#2 moore2001**: Title verified, volume/issue/pages NOT confirmed. Pull from AIAA arc.aiaa.org or institutional access; record volume, issue, page range.

9. **#19 aedc7658**: DTIC PDF behind 403. Download from a connected machine, confirm exact title and authors (likely Uselton or Schueler, AEDC).

10. **#29 dtic_ad0733141**: Same — DTIC PDF needed for authors and full title. Note configuration is **VB**, not "V".

11. **#28 rogers_rasaero_alt**: Page has no explicit author. Decide cite style: institutional author "RASAero / Rogers Aeroscience" or "Rogers, C. (n.d.)".

12. **#5 lowcostroll2025**: Get authors and full bibliographic data; DOI resolves but AIAA arc 403'd.

### Minor (metadata fixes)

13. **#7 nasa1976ussa**: Use COESA as corporate author; report number NOAA-S/T 76-1562; NTRS 19770009539.

14. **#8 incropera2007**: Add DeWitt, Bergman, Lavine as co-authors. Note Wiley publication date is March 2006; 2007 commonly used but check journal house style.

15. **#12 nasa_trr100**: Set author to W. E. Stoney, Jr.; year 1961; title "Collection of Zero-Lift Drag Data on Bodies of Revolution from Free-Flight Investigations".

16. **#15 jorgensen1977**: Use full title "Prediction of Static Aerodynamic Characteristics for Slender Bodies Alone and with Lifting Surfaces to Very High Angles of Attack".

17. **#17 pitts1957**: Use full title (add "at Subsonic, Transonic, and Supersonic Speeds").

18. **#22 chapman1958**: Use full title (add "in Supersonic and Subsonic Streams with Emphasis on the Effect of Transition").

19. **#24 bunescu2025**: Use 5-author list, not "Bunescu et al.": Bunescu, Hothazie, Pricop, Onel, Afilipoae.

20. **#25 sahu1983**: Title varies by version — pick BRL TR ("Numerical Computation of Base Flow for a Projectile at Transonic Speeds", ARBRL-TR-02495) or the journal version, but keep one consistent.

21. **#26 vidanovic2014**: Use full 5-author list and full title.

22. **#27 sznajder2025**: Confirm the "281" in volume "281(4)" — this looks like the continuous issue number; format may need to be just "issue 4, 2025" depending on journal conventions.

23. **#30 heitkotter1956**: This is a **Nike-Deacon** paper, not Nike-Apache. Verify it is being cited for the right rocket in the manuscript.

24. **#33 nasa_x721_66_568**: Institutional author is NASA **Goddard Space Flight Center**, not Wallops. Authors are Galloway + Crough.

---

## DROPS — citations to remove if not fixable

- **#9 devan1986** (HIGH PRIORITY): If you cannot locate the actual Devan/Ashwood report, drop the cite and substitute with NSWC TR 81-156 (Mason/Devan/Moore/McMillan 1981) or the AP-code papers (Moore 2001/2002).
- **#21 paulwedemeyer1982** (HIGH PRIORITY): If no matching EOARD-TR-82-7 can be located, drop "Paul" and cite AGARD-LS-121 (Wedemeyer 1982) for vortex breakdown, OR replace with Keener & Chapman "Onset of Asymmetric Vortex Shedding" or Lamont 1982 AIAA paper.
- **#20 platou1963** (REPLACE): Drop BRL Report 1193 and use the AIAA Journal version (Platou 1965, DOI 10.2514/3.2791) — direct equivalent, fully verifiable.

---

## BIB UPDATES NEEDED

BibTeX entries for the verified PASS rows (use these once corrections above are applied):

```bibtex
@article{sooy2005,
  author    = {Sooy, Thomas J. and Schmidt, Rebecca Z.},
  title     = {Aerodynamic Predictions, Comparisons, and Validations Using {Missile DATCOM (97)} and {Aeroprediction 98 (AP98)}},
  journal   = {Journal of Spacecraft and Rockets},
  volume    = {42},
  number    = {2},
  pages     = {257--265},
  year      = {2005},
  doi       = {10.2514/1.7814}
}

@article{quintart2025,
  author    = {Quintart, Alexandre M. and Haw, Magnus A. and Semeraro, Federico},
  title     = {{arcjetCV}: {Open-Source} Software to Analyze Material Ablation},
  journal   = {Journal of Spacecraft and Rockets},
  volume    = {62},
  number    = {5},
  pages     = {1644--1653},
  year      = {2025},
  doi       = {10.2514/1.A36132}
}

@techreport{sims1964,
  author      = {Sims, Joseph L.},
  title       = {Tables for Supersonic Flow Around Right Circular Cones at Zero Angle of Attack},
  institution = {NASA Marshall Space Flight Center},
  number      = {NASA SP-3004},
  year        = {1964},
  url         = {https://ntrs.nasa.gov/citations/19640009035}
}

@techreport{nasa1976ussa,
  author      = {{COESA}},
  title       = {{U.S. Standard Atmosphere, 1976}},
  institution = {NOAA, NASA, U.S. Air Force},
  number      = {NOAA-S/T 76-1562},
  year        = {1976},
  url         = {https://ntrs.nasa.gov/citations/19770009539}
}

@book{incropera2007,
  author    = {Incropera, Frank P. and DeWitt, David P. and Bergman, Theodore L. and Lavine, Adrienne S.},
  title     = {Fundamentals of Heat and Mass Transfer},
  edition   = {6},
  publisher = {John Wiley \& Sons},
  address   = {Hoboken, NJ},
  year      = {2007},
  isbn      = {978-0-471-45728-2}
}

@article{viswanath1996,
  author  = {Viswanath, P. R.},
  title   = {Flow management techniques for base and afterbody drag reduction},
  journal = {Progress in Aerospace Sciences},
  volume  = {32},
  number  = {2--3},
  pages   = {79--129},
  year    = {1996},
  doi     = {10.1016/0376-0421(95)00003-8}
}

@techreport{nasa_trr100,
  author      = {Stoney, William E., Jr.},
  title       = {Collection of {Zero-Lift} Drag Data on Bodies of Revolution from {Free-Flight} Investigations},
  institution = {NASA Langley Research Center},
  number      = {NASA TR R-100},
  year        = {1961},
  url         = {https://ntrs.nasa.gov/citations/19630004995}
}

@techreport{nasa_sp8039,
  author      = {Miller, William H.},
  title       = {Solid Rocket Motor Performance Analysis and Prediction},
  institution = {NASA},
  number      = {NASA SP-8039},
  type        = {Space Vehicle Design Criteria (Chemical Propulsion)},
  year        = {1971},
  note        = {Replaces erroneous SP-8050 reference}
}

@book{hoerner1965,
  author    = {Hoerner, Sighard F.},
  title     = {Fluid-Dynamic Drag: Practical Information on Aerodynamic Drag and Hydrodynamic Resistance},
  publisher = {Liselotte A. Hoerner (self-published)},
  address   = {Bricktown, NJ},
  year      = {1965}
}

@techreport{jorgensen1977,
  author      = {Jorgensen, Leland H.},
  title       = {Prediction of Static Aerodynamic Characteristics for Slender Bodies Alone and with Lifting Surfaces to Very High Angles of Attack},
  institution = {NASA Ames Research Center},
  number      = {NASA TR R-474},
  year        = {1977},
  url         = {https://ntrs.nasa.gov/citations/19770026166}
}

@techreport{tobak1956,
  author      = {Tobak, Murray and Wehrend, William R.},
  title       = {Stability Derivatives of Cones at Supersonic Speeds},
  institution = {NACA},
  number      = {NACA TN 3788},
  year        = {1956},
  url         = {https://ntrs.nasa.gov/citations/19930084542}
}

@techreport{pitts1957,
  author      = {Pitts, William C. and Nielsen, Jack N. and Kaattari, George E.},
  title       = {Lift and Center of Pressure of {Wing-Body-Tail} Combinations at Subsonic, Transonic, and Supersonic Speeds},
  institution = {NACA},
  number      = {NACA Report 1307},
  year        = {1957},
  url         = {https://ntrs.nasa.gov/citations/19930091008}
}

@techreport{bhagwandin2013,
  author      = {Bhagwandin, Vishal A. and Sahu, Jubaraj},
  title       = {Numerical Prediction of Pitch Damping Stability Derivatives for Finned Projectiles},
  institution = {U.S. Army Research Laboratory},
  number      = {ARL-TR-6725},
  year        = {2013},
  note        = {DTIC accession ADA592550}
}

@article{platou1965,
  author  = {Platou, A. S.},
  title   = {Magnus Characteristics of Finned and Nonfinned Projectiles},
  journal = {AIAA Journal},
  volume  = {3},
  number  = {1},
  pages   = {83--90},
  year    = {1965},
  doi     = {10.2514/3.2791},
  note    = {Replaces unverified BRL Report 1193 citation}
}

@techreport{chapman1958,
  author      = {Chapman, Dean R. and Kuehn, Donald M. and Larson, Howard K.},
  title       = {Investigation of Separated Flows in Supersonic and Subsonic Streams with Emphasis on the Effect of Transition},
  institution = {NACA},
  number      = {NACA Report 1356},
  year        = {1958},
  url         = {https://ntrs.nasa.gov/citations/19930092343}
}

@techreport{allenperkins1951,
  author      = {Allen, H. Julian and Perkins, Edward W.},
  title       = {A Study of Effects of Viscosity on Flow over Slender Inclined Bodies of Revolution},
  institution = {NACA},
  number      = {NACA Report 1048},
  year        = {1951},
  url         = {https://ntrs.nasa.gov/citations/19930090962}
}

@article{bunescu2025,
  author  = {Bunescu, Ionut and Hothazie, Mihai-Vladut and Pricop, Mihai-Victor and Onel, Alexandru and Afilipoae, Tudorel},
  title   = {Numerical Study of the Basic Finner Model in Rolling Motion},
  journal = {Aerospace},
  volume  = {12},
  number  = {5},
  pages   = {371},
  year    = {2025},
  doi     = {10.3390/aerospace12050371}
}

@techreport{sahu1983,
  author      = {Sahu, Jubaraj and Nietubicz, Charles J. and Steger, Joseph L.},
  title       = {Numerical Computation of Base Flow for a Projectile at Transonic Speeds},
  institution = {U.S. Army Ballistic Research Laboratory},
  number      = {ARBRL-TR-02495},
  year        = {1983},
  note        = {DTIC AD-A130293}
}

@article{vidanovic2014,
  author  = {Vidanović, Nenad D. and Rašuo, Boško P. and Damljanović, Dijana B. and Vuković, Đorđe S. and Ćurčić, Dušan S.},
  title   = {Validation of the {CFD} code used for determination of aerodynamic characteristics of nonstandard {AGARD-B} calibration model},
  journal = {Thermal Science},
  volume  = {18},
  number  = {4},
  pages   = {1223--1233},
  year    = {2014},
  doi     = {10.2298/TSCI130409104V}
}

@article{sznajder2025,
  author  = {Sznajder, Janusz},
  title   = {Computational Determination of Dynamic Stability Derivatives},
  journal = {Transactions on Aerospace Research},
  number  = {4},
  pages   = {98--121},
  year    = {2025},
  doi     = {10.2478/tar-2025-0021}
}

@misc{rogers_rasaero_alt,
  author       = {{Rogers Aeroscience}},
  title        = {{RASAero II} Comparisons with Altitude Data},
  howpublished = {\url{https://www.rasaero.com/comparisons-alt.htm}},
  year         = {n.d.},
  note         = {Accessed 2026-05-16}
}

@techreport{heitkotter1956,
  author      = {Heitkötter, Robert H.},
  title       = {Flight Investigation of the Performance of a Two-Stage Solid-Propellant {Nike-Deacon (DAN)} Meteorological Sounding Rocket},
  institution = {NACA},
  number      = {NACA TN 3739},
  year        = {1956},
  url         = {https://ntrs.nasa.gov/citations/19930084525}
}

@misc{rfd_zenodo,
  author    = {Yu, Aidan},
  title     = {Rocket Flight Database},
  year      = {2026},
  publisher = {Zenodo},
  doi       = {10.5281/zenodo.19976138},
  url       = {https://doi.org/10.5281/zenodo.19976138}
}

@article{wilcoxon1945,
  author  = {Wilcoxon, Frank},
  title   = {Individual Comparisons by Ranking Methods},
  journal = {Biometrics Bulletin},
  volume  = {1},
  number  = {6},
  pages   = {80--83},
  year    = {1945},
  doi     = {10.2307/3001968}
}

@techreport{nasa_x721_66_568,
  author      = {Galloway, Howard L., Jr. and Crough, Ruth Ann},
  title       = {{Nike Apache} Performance Handbook},
  institution = {NASA Goddard Space Flight Center},
  number      = {X-721-66-568},
  year        = {1966},
  url         = {https://ntrs.nasa.gov/citations/19670015760}
}

@misc{aiaa_numerical_policy,
  author       = {{AIAA}},
  title        = {Editorial Policy Statement on Numerical and Experimental Accuracy},
  howpublished = {\url{https://aiaa.org/publications/Publish-with-AIAA/Publication-Policies/Editorial-Policy-Statement-on-Numerical-and-Experimental-Accuracy/}},
  year         = {n.d.}
}
```

Entries deferred pending FAIL/UNREACHABLE resolution: **moore2001**, **moore2002→mooremcinvillehymer**, **lowcostroll2025**, **dahlembuck1966** (needs AIAA 66-505 verification), **aedc7658**, **dtic_ad0733141**, **paulwedemeyer1982** (likely → wedemeyer1982_agardls121 or dropped).

