# SUBMISSION READINESS — Consolidated Dossier (5-Paper Family)

**Author:** Aidan Yu — Department of Mechanical Engineering & Materials Science, Duke University — ORCID 0009-0005-9589-5314 — asy22@duke.edu
**Prepared:** 3 June 2026 · **Source of truth:** `paper/_shared/CANONICAL_FACTS.md`
**Overall status:** GO_WITH_FIXES (moderate risk) for all five. Two reviewer-visible cross-companion
numeric conflicts must be resolved before any peer-reviewed paper is submitted; two user pushes gate the
DOIs/code-availability that Papers 1/2/3/5 cite. The headline corpus is consistent everywhere.

---

## 1. Per-Paper Status Table

| # | Paper (genre) | Venue | PDF (clean, compiled) | Editor verdict | Primary residual risk |
|---|---|---|---|---|---|
| 1 | Research flagship | AIAA *J. Spacecraft & Rockets* | `paper/1_JSR_ShockGeometryPrePass.pdf` (62 pp; src `1_research_jsr/jsr_paper.pdf`) | GO_WITH_FIXES (moderate) | **Subsystem count "twenty" vs Thesis "27"** (manuscript §VI + cover letter say *twenty*; canonical ≈27). Pick one number family-wide. In-sample base-drag constants (disclosed; holdout defends). Parity-not-superiority framing must survive copy-edit. |
| 2 | Software paper | *Journal of Open Source Software* | `paper/2_JOSS_OpenRocketPlus.pdf` (src `2_joss/paper.md` + `2_joss/build-joss.ps1`) | GO_WITH_FIXES (moderate) | Repo-gated, not text-gated: archival code Zenodo DOI + tagged release; CI green on the released commit; fork README; `CITATION.cff` (present — verify content). Manuscript Basic Finner already 11.8% (correct); stale **22.7%** survives only in repo build-reports/legacy docs (housekeeping). |
| 3 | Data article | Elsevier *Data in Brief* | `paper/3_DataInBrief_RocketFlightDatabase.pdf` (19 pp; src `3_data_dib/dib.pdf`) | GO_WITH_FIXES (moderate) | **Self-contradiction on two-stage count** (see §2): Data Description + Table 4 say *two* two-stage (AeroPac+MESOS); spec table/objective/methods/limitations/backmatter say *one* (24 single-stage + MESOS). Must use official DiB DOCX template; co-submit/cite parent (Paper 1). Data-DOI must match current code (gated on RFD v-next push). |
| 4 | Technical report (NON-peer-reviewed) | *Zenodo* self-deposit | `paper/4_Zenodo_TechnicalReport.pdf` (src `Thesis/OpenRocketPlus-Thesis.pdf`) | GO_WITH_FIXES (moderate) | Says **"27"** subsystems (anchor for the family count). Names the refuted "JUnit parallel-execution contamination" hypothesis — currently framed *as refuted/not invoked* (defensible), but canonical F prefers omission. Deposit timing (LOW prior-pub risk *with* disclosure). |
| 5 | Research article (distinct question) | ASCE *J. Aerospace Engineering* (floor) | `paper/5_ASCEJAE_BaseDragDominance.pdf` (32 pp; src `5_basedrag/basedrag.pdf`) | GO_WITH_FIXES (moderate) | **Ablation set mislabeled "24 single-stage"** — the 24 = 25 corpus − MESOS, but still *includes* the two-stage AeroPac 104K, so the count 24 is right yet "single-stage" is wrong. Salami risk vs Paper 1 (mitigated by distinct mechanism-attribution question). Code-DOI gated on push. See §4 for the one-venue decision. |

All five compile to clean PDFs. Cover letters drafted: `_shared/coverletter_p1.md` … `coverletter_p5.md`
(each discloses the full family). The core headline corpus — mean −0.38%, σ 5.44%, RMSE 5.34%, MAE 4.74%,
25/25 within ±10%, 14/25 within ±5%; RASAero parity (Wilcoxon W=143.0, p=0.615; |ORP|−|RAS| = −0.60 pp,
CI [−2.16,+0.96]); Basic Finner 11.8%; cone foredrag 19.7% (max +57%); Sutherland 0.54%/0.012%; MESOS −6.96%
(largest single-flight error, NOT a regression); ablation 8.10/0.87/0.39/0.15/0.00 pp; decontaminated holdout 3.95% (n=12) < 5.47% (n=13);
exploratory high-Mach 3 pass / 17 fail; Kv=0.20 internally-calibrated — is consistent across all FINAL sources.
No fabricated Devan-Ashwood or paulwedemeyer citations survive in any FINAL source.

---

## 2. The Two Reviewer-Visible Cross-Companion Conflicts (RESOLVE BEFORE SUBMISSION)

### CONFLICT A — AeroPac 104K stage count (the published data is the tiebreaker)
The released dataset is unambiguous. `rocket-flight-database/flight_comparison.csv`, flight_id 22:
`vehicle_name = "AeroPac 104K Two-Stage"`, `motor = "N1048 / M685W (AT)"` (two stages), `diameter_in = "4.05/3.08"`
(two stage diameters). MESOS 293K (flight_id 25) is likewise two-stage. **The true corpus is therefore
23 single-stage + 2 two-stage.** Every other flight has a single motor and single diameter.

Current FINAL-source state (verified this pass — note this has MOVED since the audit snapshot):
- **Paper 1 (JSR): CORRECT.** "23 single-stage … 2 two-stage (AeroPac 104K … and MESOS 293K)"
  (`08_corpus.tex:16-17`, `11_conclusions.tex:58`, `10_limitations.tex:75`). No action.
- **Paper 3 (DIB): CONTRADICTS ITSELF.** Data Description + verbatim Table 4 say *two* two-stage
  (`05_data_description.tex:27-29,64,123`) — CORRECT; but spec table (`02:42`), objective (`04:22-23`),
  methods (`06:24-29`), limitations (`07:57`) and backmatter (`08:49`) say "24 single-stage + the single
  two-stage MESOS 293K closure" — WRONG and self-inconsistent. **Fix: change those five places to
  "23 single-stage + 2 two-stage (AeroPac 104K and MESOS 293K)"** so DiB agrees with itself, with Paper 1,
  and with its own released data. (Recompile `dib.pdf`.)
- **CANONICAL_FACTS.md: WRONG.** §A line 21 still says "24 single-stage + MESOS 293K two-stage." Update to
  "23 single-stage + 2 two-stage (AeroPac 104K and MESOS 293K)" so the source-of-truth stops propagating the
  error. (Headline statistics are unaffected — still N=25.)
- **Paper 5 (basedrag): label-only fix** (see Conflict-adjacent item below).

### CONFLICT B — Externally-benchmarked subsystem count: "twenty" (Paper 1) vs "27" (Paper 4)
- Paper 1 §VI (`06_benchmarks.tex:43`) and the JSR cover letter say **"Twenty subsystems … A-level."**
- Paper 4/Thesis says **"27 externally benchmarked subsystems"** throughout (`PART_A.md:3,59,92`, `PART_E.md:839,848`).
- Canonical D says **A-level count ≈ 27** (plus RM-10 as a negative/exclusion benchmark).
- **Fix: reconcile to one number.** Most defensible: state "27 externally benchmarked subsystems (plus one
  negative exclusion benchmark, NACA RM-10)" in Paper 1 §VI **and** the JSR cover letter, matching canonical
  and the Thesis. If "twenty" was meant as "twenty *representative* subsystems shown in Table X" (a displayed
  subset, not the total), reword to make that explicit so it cannot read as a conflicting total. Either way,
  the family must show one A-level total. Recompile `jsr_paper.pdf` and re-export the cover letter.

### Two minor (label/framing) residuals
- **Ablation-set label (Papers 4 and 5).** The 24-flight ablation set = 25-flight corpus minus MESOS, but it
  still *contains* the two-stage AeroPac 104K. The count **24 is correct**; the descriptor **"single-stage" is
  wrong**. Reword to "24-flight ablation corpus (the 25-flight corpus excluding the two-stage MESOS 293K closure)"
  in Paper 5 (`01_abstract.tex:11`, `04_methodology.tex:105-114`, `05_results.tex:79,87`, `08_conclusions.tex:18`)
  and the matching Thesis passages. Low reviewer risk but trivially correct.
- **RESOLVED 2026-06-03 — MESOS framing corrected.** The "−6.96% is a regression from −0.6%/291,601 ft"
  narrative has been **removed from all papers**. The −6.96% (273,056 ft) value is now reported as the standing,
  reproducible current-code prediction and the corpus's largest single-flight error; the earlier −0.6%/291,601 ft
  figure was erroneous (no defensible derivation) and is **withdrawn**. No "regression," no "under investigation,"
  no "re-sync to recover," and no contamination story appears in Papers 1/3/4 or CANONICAL_FACTS §F.

---

## 3. Consolidated User-Action Checklist (GATES SUBMISSION — each needs a git push or a human decision)

These cannot be done by the drafting workflow; they require the user.

| # | Action | Type | Gates | Notes |
|---|---|---|---|---|
| U1 | **Mint the archival code Zenodo DOI + push a tagged release** of `github.com/AidanSYu/openrocketsupersonic` (GPL-3.0) | git push + Zenodo | Papers **1, 2, 5** code-availability | The minted DOI + tag must be written back into all three manuscripts' availability statements and cover letters before submission. |
| U2 | **DONE (2026): RFD republished** at Zenodo **DOI 10.5281/zenodo.20531977**, MESOS model column corrected to −6.96% / 273,056 ft; CC-BY-4.0. All manuscripts + cover letters now cite this DOI. **Remaining check:** confirm the DOI resolves publicly and the deposited CSV's MESOS row reads −6.96% before submitting Papers 1/3. | Zenodo (live) | Papers **1, 3** data-DOI consistency | Replaces the earlier 10.5281/zenodo.19976138 deposit; the "v1.2" version label has been dropped from all manuscripts in favor of the versionless DOI citation. |
| U3 | **Confirm Zenodo monograph (Paper 4) deposit timing** | human decision | family disclosure | LOW prior-pub risk *with* disclosure (per the Paper-4 research and every cover letter). Deposit-then-disclose is fine; depositing Paper 4 first gives the others a citable superset DOI. |
| U4 | **Paper 2 repo readiness:** CI green on the released commit; fork README present; verify `CITATION.cff` content (file exists at repo root); fix the stale **22.7% → 11.8%** in any repo-facing Basic Finner doc | git push | Paper **2** | Manuscript itself is correct (11.8%). 22.7% survives only in `core/.../ReleaseNotes.md`, build-reports and `paper/data/legacy/*` — repo housekeeping, not a manuscript blocker. |
| U5 | **DiB submission mechanics:** obtain and fill the official Data in Brief **DOCX template**; co-submit or cite the parent (Paper 1) at submission | human action | Paper **3** | DiB requires the parent research article be identified; Paper 1 is that parent. |
| U6 | **Apply the §2 conflict fixes** (AeroPac stage count in DiB ×5 places + CANONICAL; subsystem 20→27 in JSR §VI + JSR cover letter; ablation "single-stage" label in Papers 4/5; optional contamination-line drop), then **recompile** `dib.pdf`, `jsr_paper.pdf`, `basedrag.pdf`, Thesis, and re-export the JSR cover letter | edit + recompile | Papers **1, 3, 4, 5** | Text-only; no push needed. This is the priority pre-submission edit. |

---

## 4. Submission Sequencing (avoids dual submission; uses cover-letter companion disclosure)

Each cover letter already discloses all five works proactively as distinct genres/questions (anti-salami).
Recommended order:

1. **Deposit Paper 4 (Zenodo monograph) FIRST** (U3). Non-peer-reviewed gray literature; mint its DOI so
   Papers 1/2/3/5 can cite the superset and so the self-overlap (esp. DiB iThenticate) is a disclosed,
   legitimate author self-reference rather than undisclosed prior publication.
2. **Push code (U1) and republish RFD v-next (U2)**, then write the minted code DOI + synced data DOI into
   all manuscripts and cover letters.
3. **Apply §2 fixes and recompile (U6).**
4. **Submit Paper 1 (JSR)** — the flagship/parent. Its cover letter discloses the family.
5. **Submit Paper 3 (DiB)** naming Paper 1 as parent (U5), and **Paper 2 (JOSS)** once the repo is release-ready (U4).
   These may go in parallel after Paper 1 is in review; they are different genres at different venues, each
   cross-citing — not dual submission of the same work.
6. **Submit Paper 5** to its single chosen venue (§5) **last**, after the family context is established.

**Dual-submission guard:** the only genuine salami risk is Paper 1 ↔ Paper 5. Each cover letter spells out
that Paper 5 is a distinct mechanism-attribution question (which closure governs the apogee budget; 8.10 pp
base-drag vs 0.15 pp pre-pass) and defers all integrated-parity statistics to Paper 1. Never submit the same
content to two venues; see §5 for Paper 5's one-venue rule.

---

## 5. Paper 5 Plan — submit EXACTLY ONE venue (never both)

- **Floor (ready now): ASCE *Journal of Aerospace Engineering*.** The delivered content — mechanism
  attribution (ablation 8.10/0.87/0.39/0.15/0.00 pp) anchored to *external* base-pressure benchmarks
  (NACA TN 3393 turbulent 15.9% / laminar 4.4%; Hart RM L52E06 4.0%), with the in-sample FINNED_BASE_K
  neutralized by the decontaminated holdout (3.95% < 5.47%) — is a defensible, "won't-get-rejected"
  engineering-method-validation article. Free / no APC, Q2. This is the locked default (canonical §J).
- **Escalation (optional): *Aerospace Science and Technology*.** Requires a NEW **per-closure apogee-swap
  experiment** (intercompare Chapman–Korst, Chapman laminar, ESDU 77021, 0.064+0.186/M², Viswanath boattail,
  finned-base augmentation by swapping each closure and measuring the apogee shift) plus an optional
  finned-base base-pressure benchmark. Only re-aim at AST **if that experiment actually lands**; AST was
  NO_GO as currently drafted (irreducible CFD-genre risk + needs the new experiment).
- **HARD RULE:** submit Paper 5 to **AST if the new experiment lands, else ASCE JAE — NEVER both.**
  Publishing the same study in two venues is duplicate publication. The current PDF
  (`5_ASCEJAE_BaseDragDominance.pdf`, 32 pp) is the ASCE-JAE-floor version and is submission-ready once
  U1 (code DOI) and the U6 ablation-label fix are applied.

---

## 6. Bottom Line

Submission-ready pending: (a) the two text fixes in §2 (AeroPac stage count → DiB self-consistency + CANONICAL;
subsystem 20→27 in JSR + its cover letter) and the two minor label/framing items, recompiled (U6); (b) two
user pushes — archival code DOI + tagged release (U1) and the RFD v-next MESOS re-sync (U2); (c) the Paper-4
Zenodo deposit confirmed (U3) and the Paper-2 repo + Paper-3 DOCX mechanics (U4, U5). The honest-framing
spine — parity not superiority, supersonic-validated to M4.33 with hypersonic exploratory, full 3-pass/17-fail
high-Mach disclosure, in-sample constants defended by holdout, MESOS −6.96% as the standing largest-error value
(erroneous −0.6% withdrawn), no fabricated citations —
holds across all five FINAL sources.
