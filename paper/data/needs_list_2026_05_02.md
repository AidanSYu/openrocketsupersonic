# Updated AST Defence Needs List — 2026-05-02 (revised end-of-day after sounding-rocket corpus session)

Status after inventorying `paper/data/pdf/New/` (9 PDFs) and deep-reading the Albisser 2015 thesis,
**plus** a sounding-rocket corpus harvest session that closed Targets 1, 2, 3, 5 (Super Loki, Arcas, Nike-Apache supplementary, Viper).

See companion files:
- `pdf_new_inventory_2026_05_02.md`
- `albisser_cmq_assessment_2026_05_02.md`
- `sounding_rocket_corpus_candidates_2026_05_02.md` ← NEW, this session

---

## What changed today

| Gap | Before today | After today |
|---|---|---|
| Cmq second independent source | Open; Albisser flagged "MAYBE" | **Still open.** Albisser geometry is Basic Finner — same lineage as ADA636861; reject as independent A-level source. Promote to B-level methodological cross-check only. |
| Second flight corpus | Open | **Largely closed (pending digitization).** Super Loki/Robin Dart, Super Loki Instrumented Dart, Viper-3A (multiple flights each in AFCRL-TR-73-0412 Tables 8.2-8.4), plus Nike-Apache 14.28 GT (TM X-55463), plus the existing HEROS 3 seed. ~30+ professional/government sounding-rocket flights identified across 4 vehicle classes — meets reviewer expectation for class-distinct second corpus. |
| Wind-tunnel A-level component anchor (non-Basic-Finner) | Existing TM X-653 only | **NEWLY AVAILABLE.** NASA TN D-4013 (Ferris, M 0.60-1.20) + TN D-4014 (Babb & Fuller, M 1.50-4.63) cover the full Arcas geometry as figures (xCP, CN, CA, Cm, CY, Cn, Cl vs alpha at 9 Mach points). Digitization required. Lifts Arcas to A-level once digitized. |
| Time-resolved CG/Iyy + thrust curve for staged sounding rocket | Open | **Closed.** AFCRL-TR-73-0412 has Figs 3.4 (T+Pc vs t), 4.2 (CG vs t), 4.3 (Iyy vs t) for Super Loki Robin Dart. Initial CSV at `paper/data/csv/super_loki_mass_properties_vs_time.csv`. |
| CFD comparator | Already covered | **Strengthened.** Sznajder 2025 (TAR/Łukasiewicz, ANSYS Fluent, 3 CFD methods on Basic Finner) and Vidanović 2014 (AGARD-B WT+CFD) add useful comparators. Bunescu 2025 confirmed duplicate. |
| Wind-tunnel anchor | Existing benchmarks (Basic Finner WT, NACA RM, NASA TM-X-653) | **Optionally strengthened** by AGARD-B (Vidanović 2014) if we choose to add a calibration-model anchor. |

---

## Remaining open gaps for AST defence

### 1. Cmq second independent source — **STILL OPEN**

Required: a Cmq vs Mach dataset on a **distinct geometry class** from Basic Finner, with extraction methodology *independent* of the Dupuis 1997/2002 ballistic-range lineage.

**Best candidate categories (in priority order, all subject to verification — see citation hygiene note):**
- **Slender cone or cone-cylinder-flare projectile:** Army-Navy spinner derivatives, ogive-cylinder, or HVAR-class research projectiles with published forced-oscillation or free-flight Cmq data. (Not Basic Finner.)
- **Sounding-rocket-class fin-stabilised body:** different fin planform (e.g. tapered/swept vs Basic Finner's rectangular), different L/d, ideally flight-extracted Cmq from radar-tracked free flight.
- **Forced-oscillation wind-tunnel rig** Cmq data on a body distinct from Basic Finner (would also unlock cross-method validation: forced oscillation vs free flight).

**Quantitative target:** 6-12 digitisable Cmq points spanning at least M 1.0-3.5 on a non-Basic-Finner geometry, from a peer-reviewed or government-lab source.

**Action items:**
- (a) Read Sznajder 2025 (`Computational_determination_of_dyna.pdf`) in full — identify which experimental Basic Finner Cmq dataset it cites; if it points to anything beyond Dupuis-lineage, that is a lead.
- (b) Web search for: "pitch damping coefficient Cmq forced oscillation wind tunnel" + a non-Basic-Finner reference geometry.
- (c) Solicit user input: do they have access to NSWC Dahlgren range data, BRL range data, or AEDC forced-oscillation reports on a non-Basic-Finner body? **All such citations must be user-verified or web-confirmed before use** (project NACA/NASA citation policy).

**Manuscript implication if unfilled:** Cmq remains B-level. We will need to state the limitation explicitly in the validation section.

### 2. Second flight corpus — **LARGELY CLOSED, DIGITIZATION REMAINS**

**Closed by 2026-05-02 sounding-rocket session.** See `sounding_rocket_corpus_candidates_2026_05_02.md` for the full dossier. Available now:

- AFCRL-TR-73-0412 / AD-766737: Super Loki Robin Dart (~71 km), Super Loki Instrumented Dart (~106 km), Viper-3A — flight summaries in Tables 8.2-8.4, mass properties in Table 4.1 + Figs 4.2-4.3, thrust curve in Fig 3.4, aero coeffs in Figs 4.4-4.8, all from a single Space Data Corp 1973 final report.
- NASA TN D-4013 + D-4014: Wind-tunnel coefficients for the Arcas geometry, M 0.60 to 4.63, two body lengths, two fin cant angles. Component-level A-level once digitized.
- TM X-55463 / X-721-66-85: Nike-Apache 14.28 GT (single instrumented flight, Wallops 1964) — adds roll-rate validation. Note user-supplied "TN D-3373" was incorrect; cite TM X-55463.
- Already on hand: HEROS 3 (Kobald 2018), ASPIRE/IRVE-II Black Brant V.

**Remaining work to convert into v2.0 corpus release:**
- Digitize Tables 8.2-8.4 (apogee, time, range, max velocity per flight) into per-flight CSV records compatible with v1.0 corpus schema.
- Digitize TN D-4013 + D-4014 figures into a per-Mach × per-alpha CN/CA/Cm coefficient table for component-level validation.
- Build OpenRocket .ork files for Super Loki Robin Dart, Super Loki Instrumented Dart, Viper-3A, and Arcas using geometry + mass properties from the AFCRL and Atlantic Research reports.

### 2-OBSOLETE. Second flight corpus — original status (kept for reference)

Required: a corpus of **>= 10-15 flights** that is meaningfully different from the v1.0 Zenodo 25-flight corpus (which is amateur SACup-class up to ~M4.18).

**Population classes that would close the gap:**
- **Professional/research sounding rockets:** Black Brant family, Terrier-Orion/Terrier-Improved Orion, Nike-class boosters, ESA REXUS, DLR/MORABA flights. These reach 100-300 km, span M 5-8, and have published trajectory data.
- **Hybrid/large student programs:** HEROS 1-3 (HyEnD, Stuttgart), MIRAS (HyEnD), Norwegian Nammo Nucleus, BLOODHOUND-class, USC RPL Traveler series. **HEROS 3 (Kobald 2018) is now in the New folder and is the strongest single seed.**
- **High-power amateur, but supersonic and instrumented:** ESRA SACup 30k SRAD supersonic flights, Carmack-prize-class flights, Kármán-line student attempts.
- **Historical/declassified:** sounding rocket telemetry with public trajectory data.

**Quantitative target for AST:** at least **10 flights** in a second class, with measured apogee + (ideally) altimeter/IMU time series. If 10 are not feasible, **even 5-7 high-quality professional sounding-rocket flights** would defensibly demonstrate generalisation beyond amateur SACup.

**Action items:**
- (a) Copy `Kobald2018A Record Flight of the Hybrid Sounding Rocket HEROS 3_JSASS.pdf` to `paper/data/pdf/`. Contact HyEnD / DLR for HEROS 1, 2, 3 telemetry if not in publication.
- (b) Investigate REXUS / MORABA published trajectory archives.
- (c) Investigate NASA Wallops / Goddard sounding-rocket trajectory release.
- (d) Decide whether to (i) extend v1.0 corpus to v1.x with more SACup (Prometheus 2024, SunrIde 2018, UIC 2018, ARIS TELL 2018) — defensible but does not really answer the reviewer's generalisation question — vs (ii) build a true v2 corpus from a different population. Recommendation: do both. v1.x is cheap; v2 is the AST-defensible answer.

**Manuscript implication if unfilled:** the "generalisation across vehicle classes" claim has to be hedged. We can still publish, but a reviewer will likely request this revision.

### 3. Other items still missing for AST defence (lower priority)

- **Transonic regime experimental anchor at a different geometry.** Existing transonic data is Basic-Finner-heavy. If we get a second-source corpus or non-Basic-Finner Cmq, this gets resolved as a side effect.
- **End-to-end runtime/performance benchmark** (per Phase 5b in `CLAUDE.md` / `SUPERSONIC_MODELING.md`) — make sure profiled numbers are in the manuscript.
- **Aeroelastic validation** — currently disabled in code (`AeroelasticModel.Q_THRESHOLD = 1e12`). For AST it is acceptable to declare this out of scope as long as the manuscript is explicit.
- **Second supersonic stability anchor beyond NASA TM X-653** — not strictly required if we have other A-level subsystems, but reviewers may ask.

---

## Concrete next-actions, ranked

1. **Move from `New/` to `paper/data/pdf/`:** HEROS 3 (Kobald 2018), Sznajder 2025, Vidanović 2014, Sahu ARL-TR-7660. Skip Bunescu (duplicate). Defer 4 SACup project reports until a v1.x extension decision is made.
2. **Read Sznajder 2025 in full** to harvest its experimental Basic Finner Cmq citations — may point to a non-Dupuis source.
3. **Decide v1.x vs v2 corpus strategy** with the user, then act on it.
4. **For the AST manuscript draft:** in the limitations / future-work section, state explicitly that (a) Cmq is currently B-level pending a non-Basic-Finner source, and (b) corpus generalisation is being addressed in a forthcoming v2 release. This pre-empts the obvious reviewer comments without overpromising.

---

## Citation hygiene reminder

Per project `CLAUDE.md`: **no NACA/NASA/AGARD/AEDC report numbers in the manuscript** unless web-verified or user-supplied as PDFs. The AGARD-B paper (Vidanović 2014) is safe — we have the PDF. The Albisser thesis is safe. The Sznajder 2025 paper is safe. Anything else suggested above as a *candidate* must be verified before it lands in the bibliography.
