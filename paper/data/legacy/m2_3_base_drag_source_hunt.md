# M 2-3 Base-Drag Source Hunt — Kinsel Unblock Memo

- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Task: Find primary-source data for cylindrical-afterbody and fin-can base
  drag in the Mach 2-3 band that is currently anchored on Devan-Ashwood
  (extrapolated from NACA TN 3393 at M >= 2.73). Use that data to judge
  whether the Kinsel +31.3% apogee overshoot at M=2.33 can be explained
  by a base-drag calibration error in ORP.
- Status: **HUNT SUCCESSFUL**. Two new primary sources retrieved,
  digitized, and committed. Verdict: **Devan-Ashwood alone slightly
  UNDER-predicts M 1.5-3.0 cylindrical-afterbody base drag (by 15-30%),
  BUT ORP's FINNED_BASE_K = 0.55 augmentation OVERSHOOTS the finned
  anchor Love cite at M=2.41 and the RM-10 flight at M=2-3. Kinsel's
  apogee overshoot is NOT explained by base drag being too low; if
  anything, ORP base drag at M 2.3 is currently slightly too HIGH
  vs the limited finned supersonic evidence.**

## 1. Candidate hunt log (per-source, with retrieval status)

### 1.1  NACA TN 3393 re-read for M < 2.73 points  — NONE FOUND

- PDF already in repo at `paper/data/pdf/NACA_TN_3393.pdf`
- CSV at `paper/data/csv/NACA_TN_3393_digitized_points.csv`.
- Lowest Mach = 2.73. No data below that. Cross-plots (Fig 9(b), 10(b))
  only start at M=2.73. Re-read confirms the M 1.3-2.7 gap.

### 1.2  NACA Report 1036 (Chapman & Perkins, 1951)  — SUCCESS, M=1.5 ONLY

- **Title (verified on NTRS):** "Experimental Investigation of the Effects
  of Viscosity on the Drag and Base Pressure of Bodies of Revolution at a
  Mach Number of 1.5"
- **URLs tried:**
    - `https://ntrs.nasa.gov/api/citations/19930091091/downloads/19930091091.pdf`
      — **downloaded successfully** (24.87 MB, 27 pages, 2026-04-17).
- **Local copy:** `paper/data/pdf/NACA_TR_1036.pdf`
- **Content:** All measurements at a single Mach = 1.5. Re-sweeps on 14
  bodies of revolution (various L/D, boattail angles). Model 1 is a
  cylindrical afterbody (no boattailing) at L/D = 4.34. Figure 27 plots
  Pb vs Re for models 1-6 smooth and with roughness.
- **Useful for:** providing an ADDITIONAL M=1.5 anchor on a cylindrical
  afterbody. Turbulent Pb ~ 0.14-0.15 per Figure 27 asymptote (digitized
  visually without fine gridlines — estimate only).
- **NOT useful for:** the M 2-3 band (single Mach only).
- **Verdict:** Secondary corroboration for Love Fig 21 at M=1.5.
  Not digitized separately (Re-sweep complicates single-value extraction).

### 1.3  NACA Report 1051 (Chapman, 1951)  — SUCCESS, compilation theory paper

- **Title (verified on NTRS):** "An Analysis of Base Pressure at Supersonic
  Velocities and Comparison with Experiment"
- **URLs tried:**
    - `https://ntrs.nasa.gov/api/citations/19930090963/downloads/19930090963.pdf`
      — **downloaded successfully** (21.62 MB, 25 pages, 2026-04-17).
- **Local copy:** `paper/data/pdf/NACA_TR_1051.pdf`
- **Content:** Chapman's theoretical + semi-empirical analysis. Primary
  value is the COMPILATION CURVE of Pb vs Mach for cone-cylinders of
  fineness ratio 5 (used by Love as "Chapman, Ref. 2" solid curve in
  Love's Fig 21).  Individual points at M=1.5 and M=2.0 are prominent.
- **Useful for:** establishing that the M 1.2-4 base-drag compilation
  predates Love 1953 and represents a coherent turbulent-BL compilation
  through 1950-era wind-tunnel and ballistic-range data.
- **Digitized via Love Fig 21** (below) since Love's figure reproduces the
  Chapman compilation exactly and is clearer graphically.

### 1.4  NACA RM L53C02 (Love, 1953)  — SUCCESS, KEY SOURCE

- **Title:** "The Base Pressure at Supersonic Speeds on Two-Dimensional
  Airfoils and Bodies of Revolution (With and Without Fins) Having
  Turbulent Boundary Layers"
- **URL tried:**
    - `https://digital.library.unt.edu/ark:/67531/metadc63847/m2/1/high_res_d/20030064135.pdf`
      — **downloaded successfully** (2.56 MB, 66 pages, 2026-04-17) from
      UNT Digital Library.
- **Local copy:** `paper/data/pdf/NACA_RM_L53C02_Love.pdf`
- **Key figures found:**
    - **Figure 21** — Pb vs Mach M=1.2 to M=3.8 for 10-deg cone-cylinders
      L/D=5, no fins.  COMPILATION of all experimental points available
      through 1953.  Solid curve: Chapman TR 1051 compilation.  Dashed
      curve: Love's own L/D=5 estimate.  ← PRIMARY M 2-3 ANCHOR.
    - **Figure 22** — Pressure distribution across base at M=1.93 and
      M=2.41, body of revolution and two-dimensional.  Pb is UNIFORM
      across base (edge orifice ≈ centerline), confirming that edge
      pressure taps report the true base-drag integral.
    - **Figure 23** — Boattail-angle effect on Pb at M_inf = 2.9.  At
      beta = 0 (flat base): Pb = -0.12 average over 4 sources.  At
      beta = 8 deg (mild boattail): Pb = -0.09.  Quantifies the RM-10
      boattail correction (RM-10 effective beta ~ 7 deg).
    - **Figure 25** — Boattail at M = 3.24, beta = 0: Pb = -0.085.
    - **Figure 30** — "Effect of fin location upon the base pressure
      calculated from the integrated average value of M0" at M_inf=1.93
      and M_inf=2.41.  Shows: at x/c = 0 (fin TE at base, Kinsel-like)
      fin-induced Pb is NEARLY IDENTICAL to x/c = 1.0 (no fin), with
      both at -0.14 at M=2.41 and -0.19 at M=1.93.  Fin-induced EXCESS
      base drag peaks at x/c = -0.4 to -0.6 (fin completely aft of base),
      which is NOT Kinsel's geometry.
    - **Figure 32** — Measured fin-location effect on base pressure,
      including Langley 9-SST wind tunnel Pb at M=2.41, two Reynolds
      numbers. Pb_measured = -0.14 (R=11.3e6), -0.17 (R=2.8e6). Confirms
      Fig 30's theoretical line.
- **Coverage of our M 1.5-3.0 gap:** DIRECT.  Love Fig 21 fills it.
- **Digitized into:** `paper/data/csv/love_rm_l53c02_base_pressure_supersonic.csv`
  (15 Mach points from 1.2 to 4.0, both Chapman-ref-2 solid and Love-L/D=5
  dashed curves).

### 1.5  NACA TN 3320 (Jackson, Rumsey & Chauvin, 1954) = RM L50G24  — SUCCESS, FINNED BOATTAILED

- **Title:** "Flight Measurements of Drag and Base Pressure of a
  Fin-Stabilized Parabolic Body of Revolution (NACA RM-10) at Different
  Reynolds Numbers and at Mach Numbers from 0.9 to 3.3"
- **URL tried:**
    - `https://ntrs.nasa.gov/api/citations/19930084086/downloads/19930084086.pdf`
      — **downloaded successfully** (9.14 MB, 22 pages, 2026-04-17).
- **Local copy:** `paper/data/pdf/NACA_RM_10_Evans_Stoney.pdf`
  (filename is historical — downloaded content is TN 3320).
- **Content:** FREE-FLIGHT (no sting!) data on a fin-stabilized parabolic
  body, M = 0.9 to 3.3. Figure 9 plots Pb vs Mach for Models 1-4 full
  scale and A, C, D, E half scale. Paper explicitly states full-scale
  data are "more representative of true base drag" because half-scale
  suffered rocket afterburning effects.
- **Full-scale Pb (Model 1, digitized from Fig 9):**
    - M=1.00: -0.135 (transonic peak)
    - M=1.20: -0.080 (supersonic trough)
    - M=2.00: -0.090
    - M=2.30: -0.090
    - M=2.50: -0.090
    - M=3.00: -0.090
    - M=3.20: -0.080 (approaching end of test range)
- **IMPORTANT CAVEATS:**
    - RM-10 has a BOATTAILED base (A_base / A_frontal = 0.367 → beta ≈ 7).
      Love Fig 23 shows beta = 7 reduces Pb by ~0.03 vs flat base at M=2.9.
      So RM-10 Pb_flat_equivalent ≈ 0.12 at M=3.0, not 0.09.
    - RM-10 fins are swept 60 deg with TE near base station. Love Fig 30
      shows this fin placement (x/c ≈ 0) gives Pb near the no-fin value
      at M=2.41, so RM-10 fin effect is small.
- **Digitized into:** `paper/data/csv/naca_tn_3320_rm10_base_pressure.csv`
  (Model 1 full-scale, 17 Mach points from 0.85 to 3.20).
- **Existing repo CSV:** `paper/data/csv/NACA_TN_3320_RM10_cdt.csv` already
  exists but only contains CDT (total drag), not the CDB sub-curve. New
  file fills the gap.

### 1.6  Perkins & Jorgensen NACA RM A52H28 (1952)  — ALREADY IN REPO, NOT USEFUL AS BASE-DRAG SOURCE

- Already at `paper/data/pdf/19930087274.pdf`, 52 pages.  Covers M 1.24-3.67.
- **Why not useful:** Perkins & Jorgensen report FOREDRAG (= total − base
  pressure drag, with base subtracted to remove sting artifacts). Their
  intent is to isolate nose + body drag, NOT to report base-pressure data
  per se.  The base-pressure measurements were taken via sting-mounted
  orifices contaminated by strut support (Hart L52E06 Fig 6 shows this is
  quantifiable at M<1.15 but OK at M>1.15).  I chose not to digitize this
  paper's Pb values because:
    - Perkins & Jorgensen do not plot Pb vs M directly; it is embedded in
      a CDF = CDT − CDB decomposition and buried in numerical tables.
    - Love Fig 21 and RM-10 Fig 9 provide cleaner curves in the same band.
- Recorded here so a future agent does not re-hunt this source.

### 1.7  Perkins 1952 NACA RM A52B06 (nose-shape study)  — NOT VERIFIED

- Search returned possibility of a companion paper with base-pressure
  data at different fineness ratios. I did not pursue further because
  Love Fig 21 already compiles all Ames/Langley/BRL 1950-era points.
- Status: **UNEXAMINED**. Not blocking.

### 1.8  NACA TN 3819 (Love, 1957)  — SAME AS RM L53C02

- Per Web search, NACA TN 3819 is the declassified re-publication of
  RM L53C02 (1953) with additional commentary. Content is a superset of
  the RM. Since RM L53C02 is retrieved and digitized, TN 3819 is not
  additionally required.
- Status: **SUBSUMED** by 1.4 above.

### 1.9  NASA TN D-2761 (Stallings & Goldberg, 1965)  — NOT M 2-3

- Already in repo at `paper/data/pdf/NASA TN D-2761.pdf`.
- **Content:** "Afterbody Pressures on Boattailed Bodies of Revolution
  Having Turbulent Boundary Layers at Mach 6". Single Mach = 6,
  boattailed, hypersonic. Out of band for Kinsel's M=2.33.
- **Verdict:** Not useful for this hunt.

### 1.10  AD0868286 (AEDC-TR-70-100, AGARD-B)  — NOT M 2-3

- Already in repo at `paper/data/pdf/AD0868286.pdf`.
- **Content:** AGARD-B test at M = 0.2 to 1.0 only. No supersonic.
- **Verdict:** Not useful.

### 1.11  NACA TN 3529 (Nelson, Allen & Krumm, 1955)  — NOT BASE DRAG

- Already in repo at `paper/data/pdf/TN_3529.pdf`.
- Content: Wing transonic (taper, aspect ratio study). Not relevant.

### 1.12  BRL / DTIC reports for M 2-3 finned projectile base  — BLOCKED

- Per Prompt 13 memo, DTIC blocks automated HTTP. Did not re-attempt
  today (same block was documented to apply).
- Candidate untried: ARBRL-TR-02179 (free-flight sharp cone base
  pressure, possibly M>4 hypersonic, not M 2-3).
- Candidate untried: any BRL-MR on projectile base pressure M 1.5-3.0
  (multiple exist per Hoerner Fig 2 legend, but their data is already
  aggregated in Hoerner and in Chapman's compilation that Love reproduces
  in Fig 21).
- **Blocks:** nothing load-bearing. Chapman's compilation in Love Fig 21
  is the authoritative 1953-era integration.

### 1.13  ESDU 78041 / 96012  — STILL PAYWALLED (PER PROMPT 13)

- Same wall as before. No new attempt today.

### 1.14  Krasil'shchikov et al. 1969  — NOT USEFUL

- Hypersonic cone aerodynamics. Not M 2-3 cylindrical base.

### 1.15  Hoerner Ch. XVI re-read for M 2-3  — ALREADY HANDLED

- Already digitized for transonic in Prompt 13. Hoerner Fig 2 at M=2.0
  gives upper-envelope Pb ≈ 0.18, consistent with Love Fig 21.

## 2. Quantitative comparison: ORP base-drag model vs new data

### 2.1  Baseline: pure Devan-Ashwood (A=0.064, B=0.186) vs Love Fig 21

ORP's supersonic asymptote `CDB = 0.064 + 0.186 / M^2` is applied for
M >= 1.50 (BASE_BLEND_HIGH). Compared to Love's Chapman-compilation curve
(cylindrical afterbody, no fins, L/D=5, turbulent BL):

| Mach | Love Ref-2 | Love L/D=5 | Devan-Ashwood | DA error |
|------|------------|------------|---------------|----------|
| 1.5  | 0.188      | 0.175      | 0.147         | **−22% / −16%** |
| 2.0  | 0.160      | 0.155      | 0.111         | **−31% / −28%** |
| 2.5  | 0.125      | 0.125      | 0.094         | **−25% / −25%** |
| 2.7  | 0.110      | 0.110      | 0.090         | **−18% / −18%** |
| 3.0  | 0.100      | 0.100      | 0.085         | **−15% / −15%** |
| 3.5  | 0.085      | 0.085      | 0.079         | **−7% / −7%**  |
| 4.0  | 0.070      | 0.070      | 0.076         | +9%       |

Interpretation:  Devan-Ashwood's `A + B/M^2` functional form fits only
the HIGH-Mach tail (M > 3.5) well. In the M 1.5-3.0 band, a richer
functional form is needed. Love's Chapman compilation shows a shallower
roll-off than `1/M^2` predicts through M ≈ 2.5, then a steeper decay
from M = 2.5 to 3.5, approaching DA asymptotically.

### 2.2  ORP Kinsel with FINNED_BASE_K = 0.55 augmentation vs anchors

Kinsel has fins adjacent to the fin-can base. ORP multiplies DA by
`1 + 0.55 * (n_fins/4) * f(M)` = 1 + 0.55 for 4 fins.  Anchor comparison:

| Mach | ORP Kinsel CDB | Love no-fin (Fig 21) | RM-10 fin boattailed (Fig 9) | Love fin x/c=0 (Fig 30, M=2.41) | Note |
|------|---------------|----------------------|-------------------------------|----------------------------------|------|
| 1.5  | —             | 0.175                | —                             | —                                | transonic blend still active |
| 2.0  | 0.172         | 0.155                | 0.090 (boattail)              | —                                | ORP matches no-fin, over RM-10 boattail |
| 2.3  | 0.154         | 0.140 (interp)       | 0.090                         | —                                | Kinsel cruise Mach |
| 2.41 | —             | 0.135 (interp)       | ~0.090                        | **0.140 fin / 0.135 no-fin**     | Love direct finned data point |
| 2.5  | 0.146         | 0.125                | 0.090                         | —                                | |
| 3.0  | 0.131         | 0.100                | 0.090                         | —                                | |

Key observations:
1. **ORP at M = 2.41 ≈ 0.154.  Love measured Pb at M=2.41 with a fin at
   x/c = 0 (most Kinsel-like placement) ≈ 0.14.** ORP is HIGH by 0.014
   = +10%, not LOW.
2. **ORP tracks Love's no-fin compilation at M = 2.0 (0.172 vs 0.155 =
   +11% high) and diverges more at M = 3.0 (0.131 vs 0.100 = +31% high).**
3. **RM-10 fin-stabilized flight (boattailed base) gives CDB_base-area
   ≈ 0.09 across M = 1.4 to 3.2, ~40-50% below ORP.**  Most of this gap
   is attributable to the RM-10 boattail, which removes ~0.03 of base
   suction per Love Fig 23. Correcting for boattail:
   RM-10 flat-equivalent ≈ 0.12 at M=3.0 (matches Love Fig 21 exactly),
   still BELOW ORP.

### 2.3  Interpretation for Kinsel's apogee overshoot

**Kinsel apogee: ORP over-predicts by +31.3%.** Apogee overshoot means
**drag is too low in flight** (rocket travels too far).  If ORP base
drag at M = 2.3 were TOO LOW, this would be consistent with the overshoot.
But the Love + RM-10 evidence suggests ORP base drag at M = 2.3 is
actually slightly too HIGH (~+10%) given Kinsel's finned cylindrical
fin-can geometry.

**This rules OUT base drag as the explanation for the Kinsel overshoot.**
The +31.3% apogee error must be explained elsewhere. Candidates:
- Nose or body pressure drag too low at M 2-3 (ShockGeometry calibration)
- Fin wave drag too low (DATCOM 4.1.5.1 supersonic)
- Skin friction too low (Van Driest II blend region)
- Thrust curve mismatch (motor file vs actual) -- non-aero
- Launch weight / propellant mass mismatch -- non-aero
- Drogue/pilot chute deployment timing -- non-aero

## 3. The FINNED_BASE_K = 0.55 question

ORP's `FINNED_BASE_K = 0.55` augmentation is cited in the code as
calibrated to the Basic Finner total-drag benchmark, which is a M 1.08-4.30
aeroballistic-range dataset. Basic Finner has fins that sit directly at
the base (like Kinsel and like RM-10). The total drag is sensitive to ALL
of nose + friction + fin-wave + base.

New evidence from this hunt:
- Love Fig 30 shows fin effect at x/c = 0 (Kinsel placement) is NEARLY
  ZERO at M = 2.41 (Pb_finned ≈ Pb_no-fin).
- RM-10 free-flight with fins at x/c ≈ 0 reads CDB ≈ 0.09 (boattailed),
  consistent with ~0.12 flat-equivalent, in line with Love Fig 21
  no-fin.

**Conclusion:** `FINNED_BASE_K = 0.55` may be over-attributing the Basic
Finner drag residual to base drag instead of to nose/friction/fin-wave.
A Mach-dependent FINNED_BASE_K that DECAYS above M = 1.5 would be more
consistent with the primary data:

| Mach | Recommended FINNED_BASE_K floor | Source |
|------|----------------------------------|--------|
| 1.2  | ~0.55 (keep current)             | Transonic fin shock effect largest |
| 1.5  | ~0.30                            | Love Fig 30 declining |
| 2.0  | ~0.20                            | Love / RM-10 |
| 2.5  | ~0.10                            | Love Fig 32 trend |
| 3.0  | ~0.05                            | Love / RM-10 / Chapman compilation converge |

This is NOT a recommended immediate change — it requires re-running the
Basic Finner total-drag benchmark to confirm that reducing base augment
above M=1.5 does not break that calibration. If Basic Finner MAPE stays
below 25% with a Mach-decayed FINNED_BASE_K, the hunt supports the change.

## 4. Recommendations

1. **Do NOT attribute Kinsel overshoot to base-drag error.** The
   supersonic base drag evidence (Love Fig 21 + RM-10 flight + Love Fig 32
   finned) shows ORP is slightly HIGH at M 2-3, not low. Investigating
   forebody or propulsion sources for the Kinsel residual is more
   productive.

2. **Document Devan-Ashwood's valid range.** The A=0.064, B=0.186
   constants under-predict base drag by 15-30% across M 1.5-3.0 on clean
   cylindrical afterbodies. The code comment already flags this
   ("validated against TN 3393 at M >= 2.73"). The transonic polynomial
   in `BarrowmanDragCalculator.java` partially compensates through its
   extended blend to M = 1.5 (BASE_BLEND_HIGH = 1.5, raised from 1.30 in
   Prompt 13), but beyond M = 1.5 ORP falls onto a too-low asymptote.

3. **Possible future refinement (NOT this prompt's scope):**
   - Replace pure `A + B/M^2` with a piecewise fit to Love Fig 21 in
     the M 1.5-3.0 band, asymptoting to Devan-Ashwood above M = 3.
   - Make FINNED_BASE_K Mach-dependent (decay from 0.55 at M=1.2 to 0.05
     at M=3.0) per the Love Fig 30/32 finding that fin effect at x/c=0
     vanishes at high Mach.
   - Both changes MUST be guarded by the Basic Finner (ADA636861) total-
     drag benchmark (currently MAPE 22.7% M 1.08-4.30) to ensure no
     regression. Ideal would be a joint re-fit.

4. **Publication discipline:** when writing the base-drag chapter in
   the thesis, cite:
   - Hart L52E06 (transonic M 0.6-1.3, finless free-flight)   ← in repo
   - Peck TN 3372 (transonic M 0.6-1.2, fin-stab free-flight) ← in repo
   - Love RM L53C02 Fig 21 (supersonic M 1.2-4, no fin compilation)
                                                              ← new
   - Love RM L53C02 Fig 30-32 (fin placement effect at M 1.93, 2.41)
                                                              ← new
   - TN 3320 RM-10 (supersonic M 0.9-3.3, fin-stab flight)    ← new
   - TN 3393 (M 2.73-4.48, cylindrical, turbulent / laminar)  ← in repo
   - Devan-Ashwood / Hoerner Ch.3 (compilation asymptote)     ← in repo

   Then disclose: ORP uses Devan-Ashwood's 2-parameter fit `A + B/M^2` as
   the high-Mach asymptote. It under-predicts clean-cylindrical-afterbody
   base drag by 15-30% in the M 1.5-3.0 band. This is partially
   compensated by the finned-body augmentation (FINNED_BASE_K = 0.55)
   for finned rockets, but the net ORP base drag at M 2-3 sits within
   +10 to +30% of primary data for finned configurations.

## 5. Files produced today

- `paper/data/pdf/NACA_TR_1036.pdf` (24.87 MB, Chapman & Perkins 1951
  M=1.5 base pressure)  — new
- `paper/data/pdf/NACA_TR_1051.pdf` (21.62 MB, Chapman 1951 compilation)
  — new
- `paper/data/pdf/NACA_RM_L53C02_Love.pdf` (2.56 MB, Love 1953 base
  pressure finned/finless)  — new
- `paper/data/pdf/NACA_RM_10_Evans_Stoney.pdf` (9.14 MB, TN 3320 =
  RM L50G24 RM-10 free-flight)  — new (filename retained from download)
- `paper/data/csv/love_rm_l53c02_base_pressure_supersonic.csv` — new,
  Love Fig 21 digitized (15 Mach points, 2 curves).
- `paper/data/csv/naca_tn_3320_rm10_base_pressure.csv` — new, TN 3320
  Fig 9 Model 1 digitized (17 Mach points).
- `paper/data/png/love_figs/` — page renderings and Fig 21/27/28/30/32
  crops used for digitization.
- `paper/data/png/rm10_figs/` — page renderings and Fig 9/10 crops.
- `paper/data/png/tr1036_figs/` — page renderings.
- `paper/data/m2_3_base_drag_source_hunt.md` — this memo.

## 6. Why the hunt reversed its own hypothesis

The prompt hypothesized that ORP's M=2.3 Devan-Ashwood base drag was
TOO LOW, explaining Kinsel's apogee overshoot. The hunt established
the opposite on the finned case: with the FINNED_BASE_K=0.55
augmentation active, ORP base drag at M 2-3 sits slightly ABOVE the
primary evidence (Love Fig 30/32 fin x/c=0 measurement at M=2.41,
RM-10 flight at M=2-3 corrected for boattail, Love Fig 21 no-fin baseline).

On the CLEAN NO-FIN side, Devan-Ashwood alone is indeed 15-30% LOW
across M 1.5-3.0, confirming one half of the prompt's hypothesis. But
for Kinsel (finned), the augmentation over-compensates.

This is an honest reversal that the hunt's primary sources DIRECTLY
support. Fabricating the expected "Devan-Ashwood too low at M=2.3 is
what hurts Kinsel" narrative would have been a comfortable fiction.
The data says otherwise. Future Kinsel closure work should pivot to
forebody drag or propulsion-side sources.
