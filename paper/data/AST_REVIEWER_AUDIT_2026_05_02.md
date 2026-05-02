# AST Reviewer-Iffy Audit (2026-05-02)

Scope: `paper/Thesis/PART_A.md`–`PART_E.md` and the validation evidence stack under `paper/data/`. Read-only. Excludes items already flagged in REVIEWER_DEFENSE / GAP_CLOSURE / parallel subagents (Cmq B-status disclosure, RM-10 80%, model-mechanism ablation, AST_PAPER.md legacy).

## CRITICAL (4 items)

- **Headline corpus numbers in the manuscript do not match any frozen artifact.**
  - Where: `PART_A.md:3,27,94`; `PART_E.md:776,1003,1010,1156,1166,1176`. Compare to `paper/data/VALIDATION_MATRIX.md:16,32-41`, `paper/data/corpus_summary_2026_05_01.md:24-33`, `paper/data/md/rasaero_head_to_head_2026_05_01.md:9-15`.
  - Problem: The manuscript reports a **25-flight** corpus with **avg |err| 4.49 %**, **15/25 within ±5 %**, **25/25 within ±10 %**, RASAero II **5.26 %, 22/25 within ±10 %**, and lists revised RAS apogees for Gibb (4 310 vs 4 205 ft → +10.1 % vs +7.5 %), AeroPac 104K, DDT, Rabia, Torrent, Kline-Rogers, Proteus 6, Qu8k, etc. Every supporting artifact still says **24-flight**, **4.65 % / 5.55 %**, **14/24 / 12/24 within ±5 %**, **24/24 / 23/24 within ±10 %**, with the original RAS apogees. No `*_2026_05_02.md` snapshot, CSV, or rerun memo exists. A reviewer running the published `SimVRealBenchmarkTest` against commit `a1b79b6cd` will reproduce 4.65 %, not 4.49 %.
  - Fix: Either regenerate and freeze a 25-flight `simvreal_baseline_2026_05_02.csv`, head-to-head doc, and corpus summary that match the manuscript numbers exactly, or roll the manuscript back to 4.65 % / 24 flights + MESOS reported separately. Pin the resulting commit and update the matrix headline simultaneously.
  - Effort: 0.5–1 day (rerun, refreeze, edit Section 11.6).

- **PART_E:1010 "wins decisively on 8 of 25" is not derivable from any frozen per-case file.**
  - Where: `PART_E.md:1010`.
  - Problem: The 24-flight head-to-head ranks 8 ORP-decisive wins, but the per-case Δ column has shifted in the new 25-flight table (e.g., Gibb +5.6→+8.2, Rabia −2.2→−6.2, Proteus −3.2→−5.4). The 8/25 figure is not reproducible from `csv/simvreal_baseline_2026_05_01.csv`. Smells like cherry-picked RAS reruns.
  - Fix: Disclose where the new RAS values come from (fresh RASAero II reruns vs. published Rogers values). If they are fresh reruns, document the RASAero II version, settings, and provide the CSV. If they are still the published Rogers values, reconcile the discrepancy with `rasaero_head_to_head_2026_05_01.md`.
  - Effort: 0.5 day.

- **Vehicle-level validity envelope misstated as M = 0.3 to M = 10+.**
  - Where: `PART_E.md:1155` ("validated from $M = 0.3$ through $M = 10+$"); `PART_E.md:1161` ("Extends model validity to $M = 10+$"); `PART_E.md:1179` ("reliable Mach range of $M < 2$ extends to $M < 10$ in this work, a five-fold range extension").
  - Problem: Vehicle-level (full 6-DOF) validation tops at MESOS M = 4.33. M = 6.5–17.2 is component-level cone foredrag only (DTIC AD0487365, MAPE 19.7 % at the gate of 20 %). Stating "validated from M = 0.3 through M = 10+" without the qualifier conflates vehicle and component scope. The "five-fold range extension" claim is a marketing line and is the kind of overreach that gets papers rejected.
  - Fix: Replace with "vehicle-level integrated trajectory validated to M = 4.33; component-level (cone foredrag) validated to M = 17.2 with MAPE 19.7 %". Drop "five-fold" framing.
  - Effort: 30 min.

- **Citation hygiene: load-bearing report numbers with no PDF in the repo.**
  - Where: PART_E references list 1296–1342; `paper/data/pdf/` contents.
  - Problem: The following are cited as primary anchors but have no corresponding PDF or extracted text in `paper/data/pdf/` or `paper/data/txt/`: NACA Report 1307 (Pitts–Nielsen–Kaattari, ref 32), NASA TN D-721 (Devan & Ashwood, ref 12), AEDC-TR-70-100 (Anderson 1970, ref 37 — anchors AGARD-B), BRL Report 1193 (Platou 1963, ref 33 — anchors Magnus body fraction 0.3), EOARD-TR-82-7 (Paul & Wedemeyer, ref 31 — anchors Kv = 0.20), NACA Report 1273 (Whitcomb 1956, ref 42), NACA Report 1356 (Chapman–Kuehn–Larson, ref 44), NACA Report 1048 (Allen–Perkins 1951, ref 2), NASA TN D-6996 (Jorgensen 1977, ref 23), NACA TN 2137 (Chapman 1950, ref 8). CLAUDE.md flags these exact identifier classes as hallucination-prone. NB: AEDC-TR-76-58 is in the repo but spelled `ADEC-TR-76-58.pdf` (typo) — a reviewer searching for it will not find it.
  - Fix: For each of the above, either (a) add the PDF / NTRS link / DTIC AD number plus a digitized data extract, or (b) downgrade the citation to "as cited in [secondary source]" with the original removed from the primary references list. Rename `ADEC-TR-76-58.pdf` to `AEDC-TR-76-58.pdf`.
  - Effort: 0.5–1 day.

## MAJOR (8 items)

- **Cmq `3×` and transonic Gaussian disclosed as B in the matrix but rendered "A for implementation" in body text without enough scaffolding.**
  - Where: `PART_E.md:473` ("validation matrix lists the implementation row as A ... while explicitly disclosing the B rating on the Cmq magnitude calibration constants"); `PART_C.md:923-924`; `VALIDATION_MATRIX.md:143`.
  - Problem: A reviewer skimming will read "Phase 9 dynamic stability is A-level" and miss that the *magnitude* of the dominant production constants is B-level. The hedge is buried at the end of a 9-line paragraph.
  - Fix: Lead Section 9.9 with the B disclosure ("the production-active calibration constants — pitch damping ×3 multiplier and transonic Gaussian peak — are B-level: corpus-anchored, not externally measured") before describing the A-level analytical anchors.
  - Effort: 30 min.

- **No mechanism paragraph for the RM-10 exclusion or several "open gaps".**
  - Where: `PART_E.md:1190-1200` (limitations 1–6).
  - Problem: Limitation 1 (RM-10) and limitation 4 (finned-body base drag is corpus-circular) say "not fixed because no public dataset" without explaining the *physical mechanism* the reader needs to accept the exclusion. Limitations 5 (`ModifiedBarrowman`) and 6 (high-AoA) use "not fixed because [resource]" rather than "not fixed because [physics]". Reviewers will read these as schedule excuses.
  - Fix: Add one sentence per limitation that names the physics mechanism the missing dataset would isolate (separated-flow boattail relief for RM-10, fin-wake corner-vortex pumping for finned base, etc.).
  - Effort: 1–2 hours.

- **No software / repository DOI; no commit-pinned reproduction recipe.**
  - Where: `PART_A.md:191` and dozens of GitHub `main` branch links throughout PART_B–E; `VALIDATION_MATRIX.md:24` ("pin a manuscript tag before external review" — still TODO).
  - Problem: AST and JOSS both expect a Zenodo DOI for the source code itself, separate from the dataset DOI 10.5281/zenodo.19976138. Source links resolve to `main`, which moves; the corpus snapshot says "pin a manuscript tag" but no tag is named anywhere in the manuscript. A reviewer cannot replay 4.49 % deterministically.
  - Fix: Mint a Zenodo software DOI, tag the commit (e.g., `v1.0.0-ast`), replace `/blob/main/` links with `/blob/<tag>/`, add a Section 11.x "Reproducing Section 11.6" recipe (clone, checkout tag, `./gradlew core:test --tests SimVRealBenchmarkTest`).
  - Effort: 0.5 day.

- **Case-selection disclosure for the head-to-head is in the supporting doc but not in the manuscript.**
  - Where: `paper/data/md/rasaero_head_to_head_2026_05_01.md:5,50` (case-selection paragraph and claim-boundary paragraph). PART_E.md:993-1014 does not paraphrase either.
  - Problem: The corpus is the union of Rogers' published comparison set and CDX1 file comments, *not curated by either tool*. RASAero II is closed-source and the values used are recorded predictions, not fresh runs. None of this is stated in Section 11.6. A reviewer will read Section 11.6 as "ORP beats RASAero" and assume curated-by-author selection.
  - Fix: Add the two sentences from `rasaero_head_to_head_2026_05_01.md:5,50` verbatim to the head of Section 11.6.1, plus one line on RASAero II's closed-source nature.
  - Effort: 15 min.

- **No uncertainty / sensitivity bounds on the headline 4.49 %.**
  - Where: `PART_E.md:1003`.
  - Problem: A single point estimate with no confidence interval, bootstrap, or sensitivity to the named tuned constants (Table 12.1) reads as overconfident. The matrix already names a ±2 pp regression policy, but it is not surfaced as the operative uncertainty bound on the headline.
  - Fix: Quote the ±2 pp per-case policy as the de facto uncertainty bound; ideally add bootstrap 95 % CI on the aggregate (one Python pass, ~50 lines).
  - Effort: 2–4 hours.

- **31 phenomena vs 27 subsystems vs 9 corpus-anchored — counts not derivable from the matrix without a crosswalk.**
  - Where: `PART_A.md:83,103-135` (31 phenomena); `PART_A.md:92` (27 subsystems); `PART_E.md:774,1166,1175` (27 + 9 + 1).
  - Problem: 27 + 9 + 1 = 37; 31 phenomena; matrix lists ~25 named A-level rows + 9 B + 1 negative. The arithmetic is reconcilable but only with effort. A reviewer will think numbers are inconsistent.
  - Fix: One paragraph in Section 1.5 mapping each of the 31 phenomena to one row of the validation matrix and stating which of the 27/9/1 buckets it falls into.
  - Effort: 1–2 hours.

- **MESOS counted as flight 25 in the manuscript but as a "separate test" everywhere else.**
  - Where: `PART_A.md:95`, `PART_E.md:1046`, vs `VALIDATION_MATRIX.md:43,75`, `corpus_summary_2026_05_01.md:5,35`.
  - Problem: MESOS is the only flight with adjusted ignition delay and launch angle in the RAS reference (PART_A:95), making it methodologically distinct. Folding it into the headline 25-flight count is defensible but changes the meaning of "100 % within ±10 %". A reviewer who reads both the manuscript and the matrix will see two different counts.
  - Fix: Either (a) keep MESOS as flight 25 and update matrix/summary to match (preferred — single source of truth), or (b) report 24 + 1 = "24 flights plus MESOS detail case" everywhere.
  - Effort: 1 hour.

- **CDX1 ModifiedBarrowman gap disclosed as "not fixed because development cost is hard to justify".**
  - Where: `PART_E.md:1198`.
  - Problem: This sentence reads as a project-management excuse, not a scientific limitation. RASAero is closed-source, so the disclosure should frame the gap as "RASAero's transonic stability formulation is not published; reverse-engineering would require black-box probing without a public spec".
  - Fix: Reword to a one-line scientific disclosure.
  - Effort: 5 min.

## MINOR (7 items)

- **"wins decisively" / "industry-standard"** (PART_E:1010, 1179). Marketing tone. Replace with "lower aggregate apogee error".
- **"comprehensive compressible-flow simulation"** (PART_E:1155). Drop "comprehensive".
- **"five-fold range extension"** (PART_E:1179). Already flagged above; reiterating because it reads as adjective-laden marketing.
- **"the only multi-stage powered-flight closure"** (PART_E:1084). Honest but reads like an admission of n=1; lead with the criterion ("MESOS 293K is the single multi-stage staging closure available in the public corpus").
- **Reference 6 (AP09) reads "exact public report metadata is not present in the repository".** This is honest but invites a reviewer to ask why an unrecoverable citation is in the references list. Move to a code-comment provenance footnote.
- **No acknowledgments / funding / conflict-of-interest section anywhere in PART_A–E.** AST requires these. Even "no funding, no COI" needs to be stated.
- **No author affiliation in PART_A–E.** Affiliation appears only in `Thesis/zenodo-deposit.md:26` (Duke University). Manuscript front-matter is missing.

## Cross-document inconsistencies

| Field | Manuscript (PART_A/E) | Matrix / Summary / Head-to-head | Action |
|---|---|---|---|
| Corpus size | 25 flights | 24 flights + MESOS separate | reconcile |
| Avg \|err\| ORP | 4.49 % | 4.65 % | reconcile |
| Avg \|err\| RAS | 5.26 % | 5.55 % | reconcile |
| Within ±10 % ORP | 25/25 | 24/24 (+ MESOS) | reconcile |
| Within ±10 % RAS | 22/25 | 23/24 | reconcile |
| Within ±5 % ORP | 15/25 (60.0 %) | 14/24 (58.3 %) | reconcile |
| Worst RAS | +11.5 % T&L still appears, but DDT now +10.1 % vs head-to-head +9.6 % | +11.5 % T&L | reconcile |
| Mean signed err RAS | +2.3 % (PART_E:1007) | +2.1 % (matrix:40) | reconcile |
| Gibb RAS apogee | 4 310 ft (PART_E:1023) | 4 205 ft (matrix:50, head-to-head:24) | reconcile |
| AeroPac 104K RAS | 113 786 ft, +8.7 % (PART_E:1043) | 113 786 ft, +8.7 % (matches) | OK |
| DDT RAS | 62 308 ft, +10.1 % (PART_E:1044) | 61 982 ft, +9.6 % (matrix:71) | reconcile |
| Rabia RAS | 12 777 ft, +0.3 % (PART_E:1036) | 12 197 ft, −4.3 % (matrix:63) | reconcile (largest single flip) |
| Torrent RAS | 13 852 ft, +8.2 % (PART_E:1037) | 13 717 ft, +7.1 % (matrix:64) | reconcile |
| Kline-Rogers RAS | 26 485 ft, +6.9 % (PART_E:1038) | 26 509 ft, +7.0 % (matrix:65) | reconcile |
| Proteus 6 RAS | 86 799 ft, +2.0 % (PART_E:1042) | 81 499 ft, −4.2 % (matrix:69) | reconcile (sign flip) |
| Qu8k RAS | 116 254 ft, −4.3 % (PART_E:1045) | 119 684 ft, −1.5 % (matrix:72) | reconcile |
| Rabia Short Fin Can RAS | 10 376 ft, −2.0 % (PART_E:1034) | 10 225 ft, −3.4 % (matrix:61) | reconcile |
| Subsystem count | 27 A-level (PART_A:92, PART_E:774) | 27 A-level (matrix:14) | OK |
| (CLAUDE.md mentions "22 A-level" — stale, but is dev guide not manuscript; flag if user wants) | — | — | — |
| Test class count | 85 (PART_A:59, PART_E:1112) | "72 test files" (CLAUDE.md) | clarify counting basis |

The Rabia and Proteus 6 sign-flips on RAS error are the most concerning: they imply RAS reruns the manuscript does not document.

## Bottom-line assessment

The MAJOR tier dominates. The science is defensible and the matrix is honest, but the manuscript and the evidence stack disagree on the headline numbers and on roughly a third of the per-case RAS apogees, with no documented rerun. That single inconsistency, plus the missing software DOI, plus the M=10+ envelope phrasing, is enough by itself to draw a major-revisions response from a careful AST reviewer. None of these is a science problem; all are paperwork. Path to submittable: refreeze a 25-flight corpus snapshot to match the manuscript (or vice versa), mint a software DOI and tag the commit, fix the M envelope wording, fill in the missing PDFs / downgrade those citations, and add an acknowledgments / affiliation block. Total effort: 2–3 days of focused cleanup, no new modelling.
