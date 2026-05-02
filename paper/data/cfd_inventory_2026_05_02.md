# CFD-Data-In-Repo Inventory (2026-05-02)

Scope: identify PDFs in `paper/data/pdf/` that contain published CFD (RANS / URANS / DES / LES / Euler / Thin-Layer NS) data usable as a CFD comparator for the AGARD-B or Basic Finner validation cases for the AST submission. Citations below are taken from the actual title pages of the PDFs (no training-knowledge citations).

---

## YES — usable as CFD comparator now

### 1. Bunescu et al. 2025 — URANS CFD on the Basic Finner Model (PRIMARY MATCH)

- **File:** `paper/data/pdf/aerospace-12-00371-v2.pdf`
- **Verified citation:** Bunescu, I.; Hothazie, M.-V.; Stoican, M.-G.; Pricop, M.-V.; Onel, A.-I.; Afilipoae, T.-P. "Numerical Study of the Basic Finner Model in Rolling Motion." *Aerospace* 2025, 12, 371. MDPI, open-access (CC BY 4.0). https://doi.org/10.3390/aerospace12050371. Authors: National Institute for Aerospace Research "Elie Carafoli" (INCAS), Bucharest, and University Politehnica of Bucharest. Extended version of AIAA SciTech 2024 Paper 2024-2348.
- **Method:** CFD — Unsteady Reynolds-Averaged Navier-Stokes (URANS), k-epsilon realizable turbulence model. Authors also reference comparison with k-omega SST results. Validated against the existing Basic Finner experimental database.
- **Geometry match:** Basic Finner Model (a.k.a. Army-Navy Finner / ANF) — cone-cylinder body with four rectangular fins. **Direct match** to OpenRocket Plus's existing Basic Finner aeroballistic validation case (currently anchored on ADA636861 free-flight data).
- **Mach range:** M = 0.4, 0.95, 1.6, 2.5, 3.5 (subsonic, transonic, supersonic). Spans the same regime as our existing Basic Finner benchmark.
- **AoA range:** 0 deg to 50 deg (also full 360 deg roll-angle sweep).
- **Data extractable:**
  - Figure 10a-f: C_lp, C_np, C_Yp, C_m, C_N, C_X all plotted vs Mach for AoA = 0/10/20/30/40/50 deg. **Fully digitizable** (gridded MDPI plots, distinct symbols per AoA). C_N and C_X are the most directly comparable to OpenRocket outputs.
  - Figures 11-13: roll-moment, yaw-moment, side-force coefficients vs roll angle for each Mach.
  - Figures 9 (earlier): coefficients vs AoA at fixed Mach.
  - Earlier sections (pp. 5-9, not extracted in detail) appear to contain the validation-against-experiment plots referenced in the abstract — these are the most defensible CFD-vs-OpenRocket-Plus comparators because they have already passed an experimental-validation check.
- **Why usable:** (1) Direct geometry match to existing validation case; (2) M range overlaps; (3) URANS k-epsilon is a credible modern CFD method; (4) plotted data digitizable with WebPlotDigitizer; (5) open-access CC BY licensing — figures can be reproduced with citation; (6) validated against experiment in the paper itself, so it is a CFD result that is itself benchmarked.
- **Recommended use:** Add a "CFD comparator" panel to the Basic Finner section of Part C of the paper. Digitize Figure 10e (C_N vs Mach, AoA = 0, 10, 20 deg) and Figure 10f (C_X vs Mach, same AoA) and overlay OpenRocket Plus predictions.

### 2. Sahu, Nietubicz, Steger 1983 — Thin-Layer Navier-Stokes on a Secant-Ogive-Cylinder-Boattail Projectile (SECONDARY)

- **File:** `paper/data/pdf/Empirical heuristics and tuned constants validation/NUMERICAL COMPUTATION OF BASE FLOW FOR A Projectile at Transonic Speed.pdf`
- **Verified citation:** Sahu, J.; Nietubicz, C. J.; Steger, J. L. "Numerical Computation of Base Flow for a Projectile at Transonic Speeds." Technical Report ARBRL-TR-02495, U.S. Army Ballistic Research Laboratory, Aberdeen Proving Ground, MD, June 1983. AD-A130-293. Steger affiliated with Stanford University (Aero/Astro).
- **Method:** CFD — Generalized-Axisymmetric Thin-Layer Navier-Stokes, Beam-Warming implicit scheme, with a unique flow-field segmentation procedure to capture base recirculation. Re_L = 4.5e6. (Quote from the report: "The computed results for this paper represent the first application of thin-layer Navier-Stokes computational technique to predict projectile base flow at transonic velocity.")
- **Geometry match:** 3-caliber secant-ogive nose + 3-caliber cylinder + boattail (Figure 4, p. 19). NOT a Basic Finner / AGARD-B match, but a generic finless ogive-cylinder projectile — relevant to OpenRocket's symmetric-component (nose + body + base) drag pipeline.
- **Mach range:** M = 0.9 to 1.2 (transonic). Narrow window, but exactly covers the transonic drag-rise region where OpenRocket Plus's `RationalBlend` / `TransonicAreaRule` machinery is active.
- **Data extractable:**
  - Figure 14: base drag vs Mach (CFD circles, experiment triangles, McDrag semi-empirical squares). **Digitizable.**
  - Figure 15: pressure drag vs Mach.
  - Figure 16: skin-friction drag vs Mach.
  - Figure 17: total drag vs Mach (CFD vs semi-empirical).
  - Figures 7-8: longitudinal C_p distribution along the body at M = 0.9.
- **Why usable:** Real CFD (TLNS, not Euler) on a rocket-like axisymmetric body in the transonic-rise regime. The CFD-vs-experiment-vs-McDrag triple comparison in Figure 14 is exactly the kind of figure a reviewer would accept. Caveat: experimental data point in Fig. 14 was sting-mounted, which the authors note biases base pressure upward — flag this in any reproduction.
- **Recommended use:** Add a "transonic base-drag CFD comparator" panel to Part C section on base drag (already cites Devan-Ashwood, Chapman-Korst, Viswanath). Digitize Figure 14 base drag vs Mach and overlay our `ChapmanKorstBaseDrag` output.

---

## MAYBE — relevant to a different gap

### 3. Anderson 1970 — AGARD Model B Wind-Tunnel Data, M 0.2-1.0

- **File:** `paper/data/pdf/AD0868286.pdf`
- **Verified citation:** Anderson, C. F. "An Investigation of the Aerodynamic Characteristics of the AGARD Model B for Mach Numbers from 0.2 to 1.0." AEDC-TR-70-100, ARO Inc. for Arnold Engineering Development Center, Air Force Systems Command, May 1970. Prepared for NASA Marshall Space Flight Center.
- **Method:** **Wind-tunnel experiment** (AEDC Propulsion Wind Tunnel Facility) — NOT CFD.
- **Geometry match:** AGARD Model B (calibration standard). Direct match if we add an AGARD-B subsonic/transonic validation case.
- **Mach range:** M = 0.2 to 1.0. Subsonic to sonic.
- **Why MAYBE:** This is a primary-source experimental dataset, not a CFD comparator. But it could anchor a new AGARD-B validation case at subsonic/transonic conditions, complementing whatever CFD comparator we add. Useful as the experimental floor against which both OpenRocket Plus and a CFD comparator could be compared.

### 4. Albisser 2015 — Free-flight Aerodynamic-Coefficient Identification (Thesis)

- **File:** `paper/data/pdf/DDOC_T_2015_0083_ALBISSER.pdf`
- **Verified citation:** Albisser, Marie. "Identification of aerodynamic coefficients from free flight data" (also "Identification de coefficients aerodynamiques a partir de donnees de vol libre"). PhD Thesis, Universite de Lorraine / Centre de Recherche en Automatique de Nancy (CRAN) / Institut franco-allemand de recherches de Saint-Louis (ISL), defended 10 July 2015.
- **Method:** **System identification from free-flight data** — inverse problem / parameter estimation, not CFD. Applied to a space probe and a projectile.
- **Geometry match:** Generic projectile and space probe — not Basic Finner / AGARD-B.
- **Why MAYBE:** Section 4.2.3 reports identified projectile drag coefficient, roll-moment, pitch-moment slope, normal-force, and **pitch-damping coefficient** from real free-flight data. Could be a secondary validation source for our `Cmq` strip-theory model (currently anchored on Tobak TN 3788 and BRL data). Not a CFD comparator. Would need the user to confirm whether the projectile geometry is digitized in repo as a usable rocket model.

### 5. AEDC-TR-78-21 (Collins, Coles, Hicks 1978) — Turbulent BL Mean Flow

- **File:** `paper/data/pdf/AEDC-TR-78-21.pdf`
- **Verified citation:** Collins, D. J.; Coles, D. E.; Hicks, J. W. "Measurements in the Turbulent Boundary Layer at Constant Pressure in Subsonic and Supersonic Flow, Part I: Mean Flow." AEDC-TR-78-21 / NASA-CR-156989, Jet Propulsion Laboratory, California Institute of Technology, May 1978.
- **Method:** **Wind-tunnel experiment** with laser-Doppler velocimetry — NOT CFD. Flat plate at M_e = 0.1 and adiabatic nozzle wall at M_e = 0.6, 0.8, 1.0, 1.3, 2.2.
- **Why MAYBE:** Could supplement Van Driest II skin-friction validation (currently anchored on TN D-6945). Not a CFD comparator and not an AGARD-B / Basic Finner geometry.

---

## NO — not CFD or wrong geometry

- **`paper/data/pdf/AEDC-TSR-78-V30.pdf`** — Boudreau 1978, "Artificially Induced Transition Results from a 7-deg, 14-7-deg Biconic, and 5-deg Cone at Mach 9 in the AEDC-VKF Tunnel F." Wind-tunnel BL transition trip study, M = 9. Abstract states explicitly: "**Test results are not included in the report.**" Useless.
- **`paper/data/pdf/Empirical heuristics and tuned constants validation/AGARD CP-536.pdf`** — AGARD Conference Proceedings 536, "Fuels and Combustion Technology for Advanced Aircraft Engines," 1993. Combustion / propulsion symposium — not CFD on rocket aerodynamics.
- **`paper/data/pdf/Empirical heuristics and tuned constants validation/A STATUS REPORT ON HIGH ALPHA TECHNOLOGY PROGRAM (HATP)...pdf`** — NASA CP-10143 Vol 1, Fourth High Alpha Conference (NASA Dryden, July 1994). High-alpha aerodynamics of fighter aircraft (F-18 HARV) — wrong geometry class.
- **`paper/data/pdf/Empirical heuristics and tuned constants validation/AIAA_experimental_paper.pdf`** — Passaggia et al., "Wind-tunnel experiments and separation control of a NACA 4412 with 25-deg sweep at high Reynolds numbers," AIAA SciTech 2022 (HAL hal-03592557). Swept-wing flow control — wrong geometry.
- **`paper/data/pdf/Empirical heuristics and tuned constants validation/3 GAPS/`** — Contents (DATCOM, Magnus force on rotating cylinder, Magnus force on finned body, NACA RM A50L07, NASA TR R-474 Jorgensen, USAF DATCOM, etc.) are empirical / textbook references already used by the project, not CFD.

---

## Already-known PDFs (one-line confirmation)

- `NACA_TN_3320.pdf` — RM-10 reference, used.
- `NACA RM A53H28.pdf` — nose drag (ogive cone-cylinder), used.
- `ADA636861.pdf` — Basic Finner aeroballistic free-flight, used.
- `NASA_TM_X_653.pdf` — fin static stability, used.
- `NACA_TN_3393.pdf` — base drag (turbulent + laminar), used.
- `Hoerner_FluidDynamicDrag_1965.pdf`, `Mc Coy Modern Exterior Ballistic.pdf` — textbooks, used.

---

## Bottom-line recommendation

**Yes — the AST CFD-comparator demand can be satisfied from PDFs already in repo.** Use Bunescu et al. 2025 (`aerospace-12-00371-v2.pdf`) as the primary CFD comparator for the existing Basic Finner validation case — it is URANS k-epsilon CFD on the same geometry over M 0.4-3.5, with digitizable C_N, C_X, C_m, C_lp, C_np, C_Yp data and an open-access CC BY license. As a secondary CFD comparator on a different gap (transonic base drag, M 0.9-1.2), use Sahu, Nietubicz, Steger 1983 (`NUMERICAL COMPUTATION OF BASE FLOW...pdf`), which provides Thin-Layer Navier-Stokes base/pressure/friction/total drag for a secant-ogive-cylinder-boattail projectile and includes an experiment-vs-CFD-vs-McDrag overlay (Figure 14) that is an ideal template for our own comparison panel. No new papers need to be sourced.
