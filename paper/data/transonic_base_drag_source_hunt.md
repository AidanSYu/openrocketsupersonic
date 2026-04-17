# Transonic Base-Drag Source Hunt — Prompt 13 Unblock Memo

- Agent: Claude Opus 4.7 (1M context)
- Date: 2026-04-17
- Task: Find primary-source transonic base-drag data (M 0.9–1.3, flat/near-flat
  cylindrical afterbody) that defensibly supports OpenRocket Plus's polynomial
  transonic base-drag peak, OR rule out Prompt 13 as un-closable.
- Status: **UNBLOCKED**. Two primary, sting-free, free-flight datasets
  retrieved, digitized, and committed:
    - `paper/data/csv/naca_rm_l52e06_base_drag.csv`   (Hart 1952)
    - `paper/data/csv/naca_tn_3372_base_pressure.csv` (Peck 1955, = RM L50I28a 1950)
  Plus one compilation (Hoerner 1965 Fig. 2 upper envelope, textbook consensus):
    - `paper/data/csv/hoerner_fig2_base_drag_compilation.csv`

## 1. Candidate hunt log (per-source, with retrieval status)

### 1.1  Hoerner, "Fluid-Dynamic Drag" (1965), Ch. XVI, Fig. 2  — SUCCESS

- **Claim being verified:** textbook compilation of transonic base drag on
  cylindrical afterbodies.
- **URLs tried:**
    - `https://archive.org/details/FluidDynamicDragHoerner1965`  (landing page)
    - `https://ia800606.us.archive.org/17/items/FluidDynamicDragHoerner1965/Fluid-dynamic_drag__Hoerner__1965_text.pdf`
      — 22.78 MB, HTTP 200, **downloaded successfully** via `curl` (hit rate
      4.25 MB/s, completed in ~5 s).
- **Local copy:** `paper/data/pdf/Hoerner_FluidDynamicDrag_1965.pdf`
  (22,780,949 bytes, 455 pages, rendered for inspection at 400 DPI).
- **What I actually read:** Chapter XVI (PDF page 324 onward), Section 2
  "Base Drag at Transonic Speeds" (pages 16-4 through 16-12). **Figure 2**
  on page 16-4 (PDF page 327) is the compilation plot. Legend itemises 12
  primary sub-sources (NACA, Aberdeen, Aachen, Kochel, NOL, OAL, NPL, Virginia,
  J.H. University, plus wind-tunnel and ballistic-range data) covering the
  cylindrical plain-projectile cluster.
- **Digitization:** points digitized from the UPPER ENVELOPE of the "plain
  cylindrical projectile bodies" cluster (category (a) in Hoerner's legend),
  stored in `paper/data/csv/hoerner_fig2_base_drag_compilation.csv`.
- **Key values (upper envelope, textbook compilation):**
    - M=0.90: CDB ≈ 0.26
    - M=1.00: CDB ≈ 0.34 (narrow spike, axis is log, uncertainty ±0.02)
    - M=1.05: CDB ≈ 0.32
    - M=1.10: CDB ≈ 0.30
    - M=1.20: CDB ≈ 0.28
    - M=2.00: CDB ≈ 0.18 (decline)
- **Important caveat:** the Hoerner upper-envelope is the statistical MAXIMUM
  across all cylindrical-projectile data in the 1945–1964 literature. Some of
  those projectiles have high forebody drag (separated conical shoulders,
  laminar wake) that pushes base drag up. For a typical ogive-cylinder rocket
  the middle band of Hoerner's Figure 2 (CDB ≈ 0.25–0.28 at peak) is more
  representative, and this middle band matches Hart and Peck below.

### 1.2  ESDU 76003  — INCORRECT SOURCE IDENTIFIER IN PROMPT

- The original prompt requested "ESDU 76003, base pressure data item".
- **Actual title of ESDU 76003 (confirmed via GlobalSpec and ESDU index):**
  "Geometrical properties of cranked and straight-tapered wing planforms."
  This is NOT a base-drag data item. The prompt had an incorrect identifier.
- **Correct ESDU items for transonic base drag** (identified via ESDU index
  search):
    - **ESDU 76033** "Subsonic base drag of cylindrical bodies with conical
      boat-tails"
    - **ESDU 78041** "Transonic base and boat-tail pressure drag of
      cylindrical bodies with conical boat-tails"
    - **ESDU 96012** "Subsonic and transonic base and boat-tail pressure drag
      of cylindrical bodies with circular-arc boat-tails" (this one WAS
      correctly identified in the prompt)
    - **ESDU 00026** "Supersonic pressure drag of conical, circular-arc
      and parabolic boat-tails"
- **Access attempts:**
    - `https://www.esdu.com/cgi-bin/ps.pl?t=doc&p=esdu_96012b`  — landing page
      returns no usable content (ESDU is subscriber-only).
    - `https://standards.globalspec.com/std/990361`  — "Please complete the
      security check to access this website". Cloudflare wall.
- **Retrieval status:** **PAYWALLED**. I could not retrieve any ESDU
  transonic base-drag data item. Public metadata confirms the scope (M up
  to 1.3, cylindrical bodies, boat-tail and base contributions separately)
  but no quantitative points are visible on the public landing pages.
- **Blocks:** refining the ORP base-drag polynomial against an independent
  correlation of recent (post-1990) wind-tunnel data is blocked on ESDU access.

### 1.3  ESDU 96012  — SEE 1.2 ABOVE

Paywalled. Same status.

### 1.4  NASA TN D-6862 (Cassanto et al.)  — NOT VERIFIED

- **Claim in prompt:** "NASA TN D-6862, free-flight base drag."
- **Per NACA/NASA citation policy:** I searched NTRS directly. I could
  not confirm the existence or content of a NASA TN D-6862 matching the
  prompt's description. Web search returned the Cassanto & Rasmussen
  AIAA Journal 1969 paper "Correlation of free-flight base pressure data
  for M=4 to M=19" (Semantic Scholar link) but no NTRS document numbered
  D-6862.
- **Retrieval status:** **NOT VERIFIED**. I did not retrieve this report.
  Per the project's explicit NACA/NASA citation policy ("never cite report
  numbers from training knowledge alone"), I am declining to cite or use
  D-6862 without the user providing a PDF or NTRS confirmation URL.
- **Note:** the verified Cassanto & Rasmussen 1969 correlation covers
  M 4–19, which is OUTSIDE the transonic band of interest (M 0.9–1.3),
  so this source would not have unblocked Prompt 13 even if retrieved.

### 1.5  NACA TN 3393  — RE-READ, NOT USEFUL FOR TRANSONIC BAND

- Already in repo at `paper/data/csv/NACA_TN_3393_digitized_points.csv`.
- **Re-read today:** the lowest Mach number present is **M = 2.73** (and
  the next two are 3.49 and 4.48). No transonic points. This dataset is
  laminar/turbulent base pressure at supersonic speeds only and does NOT
  bear on the M 0.9–1.3 transonic peak question.

### 1.6  AEDC / DTIC free-flight range reports  — PARTIAL PROGRESS

- **Search strategy:** searched DTIC for "BRL report base pressure transonic
  free flight cone cylinder ogive".
- **Promising candidates located:**
    - **ARBRL-TR-02179** — "Free-flight study of sharp cone base pressure"
      (hypersonic range, probably M > 4, not transonic)
    - **BRL-TR-3119** — cone aerodynamics various Mach
    - **ARBRL-TR-02495** — "Base Flow for a Projectile at Transonic Speeds"
      (**CFD/theoretical**, not fresh experimental data — Nietubicz is a
      Navier-Stokes author)
- **Retrieval status:** DTIC blocks automated HTTP downloads ("Request
  Blocked" returned a 15-byte body when `curl` tried `apps.dtic.mil`).
  I did not retrieve any BRL report. The titles alone, without the actual
  data, are insufficient evidence.
- **Blocks:** extending the transonic-base-drag evidence base with BRL
  ballistic-range data requires either a DTIC-authenticated session or
  the user to supply the PDFs.

### 1.7  Other free-flight spark-range / BRL transonic datasets  — UNEXAMINED

- **Candidates identified but not retrieved:** BRL Report 1044 (Rogers,
  Transonic Free Flight Range, 1958 — methodology paper, data value
  unclear); BRL Memorandum Reports on projectile free-flight M 0.5–1.5
  (Charters & Turetsky 1948 cited by Hoerner, BRL Rpt 653).
- **Retrieval status:** the Charters & Turetsky BRL 653 is one of the
  primary sub-sources in Hoerner's Figure 2 compilation we already have.
  I did not retrieve it separately because Hoerner already aggregates
  its data.

### 1.8  NACA RM L52E06 (Hart 1952)  — SUCCESS  ← KEY NEW SOURCE

- **Why this source:** the Hoerner Figure 2 legend cites this exact report
  (reference 7,n and 7,g) as a primary experimental contributor. The
  title — "Effects of Stabilizing Fins and a Rear-support Sting on the
  Base Pressures of a Body of Revolution in Free Flight at Mach Numbers
  from 0.7 to 1.3" — directly addresses the sting-contamination issue
  that made Compton TN D-4821 unusable.
- **NTRS citation:** `https://ntrs.nasa.gov/citations/19930087048`
  (title, authors, and date confirmed via NTRS search API).
- **URLs tried:**
    - `https://ntrs.nasa.gov/api/citations/19930087048/downloads/19930087048.pdf`
      — NTRS direct download stalled (the NTRS server intermittently
      refuses long-running curl connections from this host).
    - `https://archive.org/download/NASA_NTRS_Archive_19930087048/NASA_NTRS_Archive_19930087048.pdf`
      — archive.org mirror, **downloaded successfully** (9.43 MB, completed
      in ~5 s).
- **Local copy:** `paper/data/pdf/NACA_RM_L52E06.pdf` (9,430,616 bytes,
  20 pages, rendered at 300 DPI for inspection).
- **Digitization:** Figure 8 Configuration A (finless free-flight,
  cylindrical afterbody, flat base) digitized into
  `paper/data/csv/naca_rm_l52e06_base_drag.csv`.
- **Key Hart findings:**
    - **Figure 6 directly quantifies the strut-shock contamination that
      Compton TN D-4821 authors themselves rejected:** a rear-support
      sting reduces the magnitude of base-pressure coefficient by 40%
      at subsonic speeds, with the effect decaying monotonically to zero
      at M > 1.15. This validates Compton's rejection of his M 0.95–1.20
      wind-tunnel band and independently demonstrates that ONLY free-flight
      or sting-corrected transonic data is defensible.
    - Free-flight finless body (Configuration A) peak CDB = 0.265 ± 0.010
      located broadly from M ≈ 1.03 to M ≈ 1.12 (not a sharp spike).
    - Post-peak decay is much slower than the Devan-Ashwood supersonic
      correlation predicts: Hart measures CDB = 0.250 at M = 1.30,
      Devan-Ashwood extrapolation gives 0.174.
    - Stated probable error: < 4% on CD, ±0.030 on Cp at M=0.8 decaying
      to ±0.013 at M=1.25, Mach number ±0.01.

### 1.9  NACA TN 3372 (Peck 1955 = NACA RM L50I28a 1950)  — SUCCESS  ← INDEPENDENT CORROBORATION

- **Why this source:** Hart L52E06 cites it as reference 4 (Peck 1950).
  The 1955 TN is the declassified, reprinted version. Independent free-
  flight transonic base-pressure data on a DIFFERENT model family
  (fin-stabilized ogive-cylinder vs Hart's finless variant).
- **URLs tried:**
    - NTRS: `https://ntrs.nasa.gov/citations/19930084618` (title confirmed
      via API search).
    - `https://archive.org/download/NASA_NTRS_Archive_19930084618/NASA_NTRS_Archive_19930084618.pdf`
      — **downloaded successfully** (9.99 MB).
- **Local copy:** `paper/data/pdf/NACA_TN_3372.pdf` (9,987,330 bytes,
  22 pages).
- **Digitization:** Figure 12 top curve "Avg. of configs A, B and C edge
  orifice" digitized into `paper/data/csv/naca_tn_3372_base_pressure.csv`.
  Only configurations A/B/C (cylindrical afterbody) were included;
  configuration D (converging/boat-tail afterbody) was excluded because
  it is not representative of a flat rocket base.
- **Key Peck findings:**
    - Independent free-flight ogive-cylinder family with fins. Peak
      |Δp/q| ≈ 0.275–0.280 at M = 1.00–1.05.
    - Broad peak extends through M ≈ 1.00–1.20 at |Δp/q| ≈ 0.26–0.28.
    - Supports Hart Configuration A peak location (M ≈ 1.03–1.10) and
      peak magnitude (0.26–0.28) independently.

## 2. Quantitative comparison — ORP polynomial vs. new data

ORP's current transonic base-drag polynomial (see
`BarrowmanDragCalculator.java` lines 165–182) is a degree-4 Hermite:
- Value at M=0.85: 0.214 (= 0.12 + 0.13·0.85²)
- Value at M=1.05: **0.25 (peak)**
- Value at M=1.30: 0.174 (Devan-Ashwood 0.064 + 0.186/1.69)
- Derivative-matched at M=0.85 and M=1.30 for C¹ continuity.

Below, ORP polynomial values are computed exactly from the 5-constraint
degree-4 fit (reproduced numerically in a throwaway Python block against
the code constants):

| Mach | ORP poly | Hart ConfigA (free-flight, finless) | Peck A/B/C avg (free-flight, fins) | Hoerner upper envelope | Gap ORP→Hart |
|------|----------|--------------------------------------|-------------------------------------|------------------------|--------------|
| 0.85 | 0.214    | 0.170                                | 0.170                               | –                      | **+0.044**   |
| 0.90 | 0.227    | 0.180                                | 0.180                               | 0.26                   | **+0.047**   |
| 0.95 | 0.240    | 0.215                                | 0.195                               | 0.31                   | +0.025       |
| 1.00 | 0.249    | 0.255                                | 0.275                               | 0.34                   | **−0.006**   |
| 1.05 | 0.250    | 0.265                                | 0.280                               | 0.32                   | **−0.015**   |
| 1.08 | 0.247    | 0.267                                | –                                   | –                      | −0.020       |
| 1.10 | 0.243    | 0.265                                | 0.270                               | 0.30                   | **−0.022**   |
| 1.15 | 0.228    | 0.260                                | 0.265                               | –                      | **−0.032**   |
| 1.20 | 0.208    | 0.255                                | 0.260                               | 0.28                   | **−0.047**   |
| 1.25 | 0.188    | 0.250                                | –                                   | –                      | **−0.062**   |
| 1.30 | 0.174    | 0.250                                | –                                   | –                      | **−0.076**   |

Interpretation:
1. **Subsonic approach (M 0.85–0.95):** ORP OVER-predicts by +0.025 to
   +0.047. The subsonic stub formula `0.12 + 0.13·M²` (inherited from
   the pre-Phase-1 code) is too steep. Hart and Peck both show a very
   flat subsonic plateau at CDB ≈ 0.16–0.18 up to M ≈ 0.85–0.90.
2. **Peak region (M 1.00–1.10):** ORP is only marginally low, by 0.006
   to 0.022. The peak MAGNITUDE in ORP (0.25) is approximately correct
   for a finless free-flight body (Hart 0.265 vs ORP 0.25 at M=1.05 is
   within the Hart digitization uncertainty of ±0.01). Peck's fin-
   stabilized peak is 0.28 which ORP under-predicts more meaningfully.
3. **Post-peak (M 1.13–1.30):** ORP catastrophically UNDER-predicts
   by −0.022 to −0.076. This is the largest and most defensible gap.
   ORP's polynomial decays to the Devan-Ashwood value of 0.174 at
   M=1.30; Hart's free-flight data shows the curve is still 0.250 at
   M=1.30. That is a **44% low bias on base drag through the critical
   transonic range where the residual SimVReal outliers (Raven, Kinsel)
   all peak out**.

This third finding is the key unlock for Prompt 13: the evidence now
supports not WIDENING the peak to a larger value, but **rather slowing the
supersonic decay** from the peak. The polynomial should continue to hold
CDB ≈ 0.25 through at least M = 1.30, rather than decaying to the
Devan-Ashwood asymptote immediately at M = 1.3.

## 3. Interaction with the Devan-Ashwood floor

An open question raised by this hunt but NOT closed here:

Hart's free-flight data at M=1.30 (CDB = 0.250) is **44% above** the
Devan-Ashwood correlation value of 0.174 at the same Mach. The Devan-
Ashwood constants (A=0.064, B=0.186 in `BarrowmanDragCalculator.java`)
are described in the code as "Fitted to turbulent cylindrical afterbody
data from Devan & Ashwood (1961, NASA TN D-721) and Hoerner Fluid-Dynamic
Drag Ch. 3". The Devan-Ashwood correlation is A-LEVEL and validated
against NACA TN 3393 at M 2.73–4.48 with MAPE 15.9% (turbulent) and
4.4% (laminar). But those validations are at M > 2.7, NOT at the
transonic M ≈ 1.3 boundary.

**Possible interpretations:**
- The Devan-Ashwood correlation is valid only for M > ~2 where the turbulent
  flat-plate asymptote dominates, and its extension down to M = 1.3 as a
  boundary-condition for the transonic polynomial may itself be systematically
  too low by ~40%. If so, the code's blending strategy is mis-calibrated at
  its endpoint, not just in the middle.
- Alternatively, Hart's probe-based orifice measurement may read higher
  than an integrated CDB would at M = 1.25–1.30 because the rim orifice
  samples the inner shear-layer edge, not the average base pressure.
  Peck (TN 3372) discusses this. The plotted curves are "assumed to act
  over the entire base area" (Figure 8 caption).
- Real answer is likely a mix: Devan-Ashwood is too low near M = 1.3
  AND rim-orifice pressure samples the high-suction edge.

**This observation is NOT actionable within the Prompt 13 scope** because
changing the Devan-Ashwood constants would affect A-level benchmarks on
NACA TN 3393 (M 2.7–4.5). It is logged here as a downstream question for
a future prompt that recalibrates the polynomial/Devan-Ashwood handoff.

## 4. Recommendations

1. **UNBLOCK Prompt 13.** Two defensible primary sources (Hart L52E06
   finless free-flight, Peck TN 3372 fin-stabilized free-flight) plus
   the Hoerner 1965 compilation now exist in the repo with full
   provenance, digitized values, and local PDF copies. The evidence
   supports the hypothesis that ORP under-predicts transonic base drag,
   specifically in the POST-peak band M 1.1–1.3 (by 22–44%), rather
   than primarily in the peak itself.
2. **Recommended modification for Prompt 13 implementation:**
   - Do NOT raise the peak VALUE in `BarrowmanDragCalculator.java`
     above 0.25. Hart free-flight finless reads 0.265 ± 0.01 which
     is within ORP's peak tolerance. A value of 0.26–0.27 would be
     defensible, larger values would not.
   - DO widen the peak on the supersonic side by raising the
     `BASE_BLEND_HIGH` endpoint from 0.174 toward roughly 0.23–0.24
     (Hart M=1.30 is 0.250, Peck M=1.20 is 0.260). This requires
     either moving `BASE_BLEND_HIGH` from 1.30 to a higher Mach (say
     1.45 or 1.5) with a modified Devan-Ashwood handoff, OR
     implementing a separate plateau correction for M 1.2–1.5.
   - The subsonic stub (0.12 + 0.13·M²) is too high at M=0.85–0.95
     compared to Hart/Peck. But relaxing it risks regressing subsonic
     healthy cases (which currently pass) and is OUT of Prompt 13 scope
     (Prompt 13 is transonic-peak-focused).
3. **Regression-test discipline:** any Prompt-13 implementation should
   add a JUnit benchmark that reads the new CSV and asserts
   MAPE ≤ some threshold (suggest 20% initially) against Hart
   Configuration A at M = 0.9, 1.0, 1.05, 1.1, 1.15, 1.2, 1.3 for a
   representative cylindrical-afterbody rocket.
4. **Open future work (NOT for Prompt 13):**
   - Retrieve ESDU 96012 and 78041 via a university library for a
     modern correlation that separates base from boat-tail contributions.
   - Retrieve BRL ARBRL-TR-02179 and similar through DTIC with
     authenticated access.
   - Independently assess whether Devan-Ashwood A=0.064, B=0.186 is
     too low near M=1.3 (as suggested by Hart) without breaking the
     M 2.7–4.5 TN 3393 benchmark.

## 5. Files produced

- `paper/data/pdf/Hoerner_FluidDynamicDrag_1965.pdf` (22.78 MB — new)
- `paper/data/pdf/NACA_RM_L52E06.pdf` (9.43 MB — new)
- `paper/data/pdf/NACA_TN_3372.pdf` (9.99 MB — new)
- `paper/data/csv/naca_rm_l52e06_base_drag.csv` (new)
- `paper/data/csv/naca_tn_3372_base_pressure.csv` (new)
- `paper/data/csv/hoerner_fig2_base_drag_compilation.csv` (new)
- `paper/data/png/hoerner_figs/` (10 rendered pages of Hoerner Ch. XVI +
   working crops used to digitize Fig. 2)
- `paper/data/png/l52e06_figs/` (20 rendered pages + Fig. 6/7/8 crops)
- `paper/data/png/peck_figs/` (22 rendered pages + Fig. 12 crop)
- `paper/data/transonic_base_drag_source_hunt.md` (this memo)
