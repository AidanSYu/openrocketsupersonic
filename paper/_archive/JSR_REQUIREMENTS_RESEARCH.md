# AIAA Journal of Spacecraft and Rockets — Submission Requirements Research

**Date:** 2026-05-16
**Target paper:** Supersonic/hypersonic aerodynamic extensions to OpenRocket
(solo author, open-source Java code, 28-flight corpus, 4 CFD comparators)

---

## §1 Author guidelines (key bullets)

- **Submission system:** ScholarOne Manuscripts at https://mc.manuscriptcentral.com/aiaa
  (single portal for all AIAA journals; choose JSR).
- **Manuscript style:** English (American spelling), 10-pt, double-spaced,
  single-column, equations numbered sequentially (not by section), MathType or
  equivalent for math (never images). Title ≤ 12 words. Abstract 100–200 words,
  one paragraph, no acronyms or numerical references.
- **Length — Full Paper (Regular Article):** ≈ 10,000–12,000 words including
  equations and equivalent space for figures/tables.
- **Length — Technical Note / Engineering Note:** 2,500–3,500 words, **no abstract**,
  still has intro + results. Editor-only review possible; faster track.
- **Survey papers** and **Technical Comments** are separate categories.
- **Required structure:** title, authors + affiliation footnotes, abstract,
  introduction (purpose + significance vs prior work), nomenclature, body,
  conclusions, acknowledgments (funders/grants), numbered references.
- **References:** numbered bracket cites `[1]`, `[2,3]`, `[4–6]`; list all authors
  (no "et al." in list); DOI as `https://doi.org/...` mandatory when available.
  No explicit guidance on citing GitHub or Zenodo — treat as report/dataset with DOI.
- **Math symbol policy:** "Spell out everything except AIAA, NASA, NACA, AGARD, NATO."
- **Figures:** captions concise, numbered consecutively, cited in text, metric or
  dual metric/English units preferred.
- **Supplemental materials:** total upload ≤ 400 MB; datasets, tables, animations,
  video accepted; supplemental files "self-contained, no internal links to other sites"
  — Zenodo/GitHub URLs go in the references or data-availability paragraph, not as
  supplemental files. Paper "must stand on its own" per AIAA policy.
- **AI disclosure (Oct 2024):** authors must disclose any generative-AI use at
  submission. AI may improve readability/grammar/illustrations but cannot be a
  co-author or cited source.
- **Numerical accuracy policy (since 1994, last reaffirmed 2024):** any manuscript
  reporting numerical solutions must address accuracy — code verification (vs
  analytical or high-fidelity numerical solutions), spatial-discretization error
  quantified, temporal-discretization addressed, iterative convergence via
  *relative* error. Applies to engineering methods **just as it does to CFD**.
- **Open access (gold APC):** $2,700 per article, voluntary. Default is hybrid
  subscription with no page charges.

URLs:
- https://arc.aiaa.org/journal/jsr
- https://aiaa.org/publications/journals/journal-author/
- https://aiaa.org/publications/journals/Journal-Author/journal-acceptance-procedure/
- https://aiaa.org/publications/Publish-with-AIAA/Publication-Policies/Editorial-Policy-Statement-on-Numerical-and-Experimental-Accuracy/
- https://aiaa.org/publications/journals/supplemental-materials-for-journals/
- https://aiaa.org/publications/journals/reference-style-and-format/
- https://arc.aiaa.org/pb-assets/PDFs/JournalPageLimitsandWordCountGuidelines_August%202018.pdf

---

## §2 Scope fit assessment

JSR's official scope (https://arc.aiaa.org/jsr/about) covers, among other items:
*"spacecraft and missile configurations … reentry devices, transatmospheric vehicles
… applied computational fluid dynamics, applied aerothermodynamics …
applications of space technologies to other fields."*

Editorial: Olivier de Weck (MIT, EIC through 2027), Russell M. Cummings (USAFA,
Deputy Editor, hypersonics background — strongly favorable for a supersonic paper).
2024 volume: 147 articles (118 full papers, 17 technical notes, 2 surveys);
submissions up 17.2% YoY. Average review 4–8 weeks; ~6 months to first decision.

Fit by category:

| Topic | JSR fit | Evidence |
|---|---|---|
| (a) Aerodynamic model validation w/ flight-corpus benchmarks | **Strong** | Sooy & Schmidt (2005, doi 10.2514/1.7814) validate Missile DATCOM/AP98 vs wind-tunnel; Moore et al. Aeroprediction series (10.2514/2.3643, 10.2514/2.3479) — direct semi-empirical precedent. |
| (b) Open-source software validation studies | **Yes (recent)** | arcjetCV (2025, doi 10.2514/1.A36132) — open-source Python on GitHub, published as JSR full paper. Sets explicit precedent for code-as-deliverable. |
| (c) Improving classical engineering methods (Barrowman, DATCOM) | **Strong, on-mission** | Aeroprediction code papers (Moore 1994, 2002) are exactly this template; missile DATCOM updates routinely appear. JSR's mandate explicitly includes "missile configurations" + "applied aerothermodynamics." |
| (d) Solo-author manuscripts | **Permitted, common** | No co-author requirement. Editorial procedure refers throughout to "the author(s)." |

**Verdict:** the paper sits squarely inside JSR scope. JSR is the right home over
*Journal of Aircraft* (excludes spacecraft/missiles), *AIAA Journal* (broader/more
fundamental), or *J. Propulsion & Power* (propulsion-focused). The supersonic-missile
aerodynamics framing is the strongest match.

---

## §3 Reviewer expectations — recent comparable papers

**P1. Development and Flight Validation of a Low-Cost Rocket Roll Control System**
DOI 10.2514/1.A36408 (JSR, online Nov 2025).
Amateur high-power rocket; uses OpenRocket as design tool; 6-DOF simulation +
flight test validation. Full paper. Demonstrates JSR accepts amateur-rocket
hardware/sim papers when end-to-end validation is shown.

**P2. arcjetCV: Open-Source Software to Analyze Material Ablation**
DOI 10.2514/1.A36132 (JSR, online Apr 2025), Haw et al.
Software-paper template: GitHub repo cited (magnus-haw/arcjetCV), method paper
with validation against arcjet test data. Critical precedent for the OSS angle.

**P3. Aerodynamic Predictions, Comparisons, and Validations Using Missile DATCOM (97)
and Aeroprediction 98 (AP98)** — Sooy & Schmidt, JSR 42(2), 257–265, 2005,
DOI 10.2514/1.7814.
The single closest structural analog: compares two semi-empirical aero codes
across body-alone, body-wing-tail, body-tail configs vs wind-tunnel data;
reports CN, Cm, CA, xCP errors by Mach/AoA. **Use as the explicit comparator
template.**

**P4. Dynamic Simulation of Reusable Rocket Aerodynamics Turning 0-to-180 Degrees**
DOI 10.2514/1.A36335 (JSR 2024). Numerical+experimental validation of a flight
maneuver. Shows extended-attitude rocket sim papers are in scope.

**P5. Supersonic Aerodynamic Enhancement of Swept Grid Fins (HiFUN)**
DOI 10.2514/1.A36413. Supersonic Mach 1.8/2.5/3.5 sweep with CFD validation —
illustrates expected fidelity of a supersonic aero paper at JSR.

Common patterns across these papers (informing your structure):
- 6–10 sections; ~15–25 figures for full papers, ≤ 10 for technical notes.
- Validation section is the longest; errors quoted as MAPE/MAE with explicit Mach/AoA range.
- Limitations addressed in a dedicated subsection before conclusions.
- All cite at least one independent comparator (wind tunnel, CFD, or commercial code).

---

## §4 Structural recommendations for THIS paper

Submit as **Full-Length Paper** (not Technical Note). Justification: 22 subsystems,
28-flight corpus, novel architectural contribution (ShockGeometry pre-pass) and
4 CFD comparators — well beyond the "limited scope" Technical Note remit.

Recommended outline (target ~11,000 words, ~22 figures):

1. **Introduction** — gap: open-source rocketry sim accurate only to M~1.1; need
   for M>5 fidelity; cite RASAero II, OpenRocket thesis, Sooy & Schmidt 2005.
2. **Architectural Contribution** — ShockGeometry pre-pass (the novel piece). Lead
   with this. Diagram of the calculator pipeline.
3. **Subsystem Models** — table of 22 benchmarked subsystems with model, reference,
   and validation MAPE. Body wave drag, base drag, fin wave drag, friction (Van
   Driest II), CN/CP, Cmq, hypersonic Newtonian — one subsection each, condensed.
4. **Validation against published experiments and CFD** — NACA RM A52H28, TN
   3393, TM X-653, Bhagwandin, Sznajder, Vidanovic. MAPE table by source.
5. **Flight-Corpus Integration Test** — 28 flights M 0.54–7.22; mean signed
   −0.44%. Compare apogee, max-q, max-M against measured flight data, against
   RASAero II as a commercial baseline. This is the headline result.
6. **Limitations** — slender-body decay at M>5 (Phase 6h known bias), no own-CFD,
   aeroelastic disabled, transonic area rule not yet integrated. Be explicit; JSR
   reviewers reward candor.
7. **Conclusion + Future Work.**
8. **Data & Code Availability** statement: Rocket Flight Database (Zenodo DOI
   10.5281/zenodo.19976138 + v2 update); source fork on GitHub with permanent
   release-tag DOI from Zenodo. Match arcjetCV's wording (10.2514/1.A36132).
9. **References** — all NACA/NASA/DTIC reports with report numbers; web-verified.
   AIAA reference style demands DOIs on everything available.

Math/style: every Mach-dependent blend (PolyInterpolator, RationalBlend) noted with
its C1 region; error tables explicit; figures vector PDF.

---

## §5 Risks/red flags for THIS paper

1. **AIAA numerical-accuracy policy explicitly applies** even when the paper presents
   no CFD of its own. You must (a) point to verification of each engineering submodel
   against analytical/exact solutions (Taylor-Maccoll, NACA 1135 shocks — already
   done), (b) describe error quantification (you do — MAPE tables), (c) demonstrate
   convergence of integrated solver if any iteration is used. Likely satisfied;
   make this explicit in §2 or §3 of the manuscript.
2. **Novelty framing.** Sooy & Schmidt (2005) and Moore's Aeroprediction papers
   exist. Position ShockGeometry pre-pass as the new contribution; pitch this as
   "first open-source M>5 framework with end-to-end flight-corpus validation" — not
   as "another semi-empirical code." Editors look for *advance over prior work*.
3. **"Out of scope" desk-rejection risk: low but non-zero.** JSR is squarely about
   spacecraft & missiles; amateur sounding rockets fly because of paper P1
   (10.2514/1.A36408). Make the manuscript title emphasize *missile/sounding rocket
   supersonic aerodynamics*, not *amateur model rockets*, to align with JSR self-image.
4. **Solo-author + independent-researcher affiliation:** acceptable but uncommon.
   Strengthen by (a) being meticulous about citations, (b) zero typos, (c)
   reproducible artifacts (Zenodo DOI, GitHub release tag), (d) consider listing
   any institutional collaborator who reviewed drafts in Acknowledgments.
5. **Comparison against RASAero II as a "commercial reference."** This is normal
   (P3 does it with AP98). State that RASAero II results were obtained per its
   published documentation and version-locked. Don't compare against an
   undocumented configuration.
6. **No own CFD runs.** Mitigate by citing four published-CFD comparators
   (Bhagwandin, Sznajder, Vidanovic, HISA Modified Finner — note from CLAUDE memory
   that HISA is *not* fully independent). The Cmq second-source assessment paper
   may be the weakest link; remove it from independence claims.
7. **The Nike-Apache M>5 coast-Cd under-prediction** (Phase 6h, project memory):
   disclose, do not hide. Reviewers will find it. Reporting *known* error bands is
   a strength.

---

## §6 Submission mechanics checklist

| Item | Status / action |
|---|---|
| Submission portal | ScholarOne https://mc.manuscriptcentral.com/aiaa — pick JSR |
| Manuscript file | Single PDF, double-spaced, 10-pt, single-column |
| LaTeX template | Overleaf "Preparation of Papers for AIAA Technical Journals" (https://www.overleaf.com/latex/templates/preparation-of-papers-for-aiaa-technical-journals/mqqbqqvyhtwm) |
| Title | ≤ 12 words; emphasize "supersonic" and "missile/rocket" |
| Abstract | 100–200 words, one paragraph, no acronyms, no `[ref]` |
| Author block | Full name, affiliation "Independent Researcher, City, State, Country", email |
| Nomenclature | Required given symbol density of aero paper |
| Figures | Vector PDF/EPS, captions concise, dual SI/English units OK |
| Data availability paragraph | Zenodo DOI(s) + GitHub release-tag URL; cite arcjetCV (10.2514/1.A36132) format |
| AI use disclosure | Required at submission — disclose any LLM use in manuscript prep |
| Copyright form | Sign at acceptance; "No-Infringement Statement" |
| Conflicts of interest | None to declare → state explicitly |
| Funding | None / self-funded → state explicitly |
| Ethics statement | None required for aero modeling; verify in submission form |
| Open access | Optional $2,700 APC; default subscription has no page charges |
| Suggested reviewers | Optional; suggest hypersonics/missile-aero authors not affiliated with author |
| Cover letter | Note novelty: ShockGeometry pre-pass + 28-flight integrated corpus + open-source release |
| Expected timeline | First decision ~6 months; up to 2 revision cycles; rebuttal allowed on rejection |
| Permanent artifacts before submission | Mint a Zenodo DOI for the exact code revision used; tag the GitHub commit |
| Reference verification | All NACA/NASA/DTIC numbers web-checked per project policy (no LLM-only citations) |

**Final note.** The strongest evidence that this paper fits JSR is the Aeroprediction
lineage (Moore et al.) + arcjetCV (open-source on GitHub) + the 2025 Low-Cost Roll
Control paper (amateur HPR + OpenRocket). All three precedents are in JSR. The paper
should self-consciously position itself as the open-source successor to the Moore
Aeroprediction line.

---

### Sources

- Journal of Spacecraft and Rockets (JSR) — https://arc.aiaa.org/journal/jsr
- JSR About (scope) — https://arc.aiaa.org/jsr/about
- JSR Editors — https://arc.aiaa.org/jsr/editors
- AIAA Journal Author hub — https://aiaa.org/publications/journals/journal-author/
- AIAA Journal Acceptance Procedure — https://aiaa.org/publications/journals/Journal-Author/journal-acceptance-procedure/
- AIAA Journal Scopes — https://aiaa.org/publications/journals/Journal-Scopes-and-Content/
- AIAA Editorial Policy on Numerical & Experimental Accuracy — https://aiaa.org/publications/Publish-with-AIAA/Publication-Policies/Editorial-Policy-Statement-on-Numerical-and-Experimental-Accuracy/
- AIAA Supplemental Materials — https://aiaa.org/publications/journals/supplemental-materials-for-journals/
- AIAA Reference Style and Format — https://aiaa.org/publications/journals/reference-style-and-format/
- AIAA Page Limits & Word Counts (Aug 2018) — https://arc.aiaa.org/pb-assets/PDFs/JournalPageLimitsandWordCountGuidelines_August%202018.pdf
- AIAA Open Access charges — https://aiaa.org/publications/open-access/
- AIAA Ethics + AI disclosure (Oct 2024) — https://aiaa.org/publications/publish-with-aiaa/ethical-standards-for-publication-of-aeronautics-and-astronautics-research/
- Sooy & Schmidt 2005, doi 10.2514/1.7814
- Moore Aeroprediction 2002, doi 10.2514/2.3643
- Engineering-/Intermediate-/High-Level Aero Methods, doi 10.2514/2.3479
- arcjetCV, doi 10.2514/1.A36132
- Low-Cost Rocket Roll Control, doi 10.2514/1.A36408
- Reusable Rocket Turning 0-180, doi 10.2514/1.A36335
- Swept Grid Fins HiFUN, doi 10.2514/1.A36413
- LetPub JSR profile (review speed, indexing) — https://www.letpub.com/index.php?page=journalapp&view=detail&journalid=5166
