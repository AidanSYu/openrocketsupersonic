# Paper Folder Cleanup Plan

**Status:** Proposal — REQUIRES USER CONFIRMATION before any deletion.
**Date:** 2026-05-16
**Last updated:** 2026-05-16 (after pre-deletion safety checks revised the scope; see §Safety checks below).
**Based on:** PAPER_FOLDER_INVENTORY agent run + grep-based reference checks.

The user said most things in the `paper/` folder are legacy from the old AST paper and can be deleted. This plan separates the safe-to-delete legacy from the must-keep current.

## Critical finding from safety checks

The original plan suggested bulk-deleting `paper/data/legacy/`. **This is NOT safe.** Pre-deletion grep revealed:

- `paper/data/legacy/rm10_vs_basic_finner_diagnostic.md` is referenced as a load-bearing per-component decomposition artifact from `Thesis/PART_E.md` (lines 970, 1295), `Thesis/FULL_TECHNICAL_REPORT.md` (concatenated), and `data/VALIDATION_MATRIX.md` (line 171). It is currently classified as "legacy" by location but is operationally still cited.
- Deleting it would break three active documents.

**Resolution:** before bulk-deleting `paper/data/legacy/`, audit each file and either (a) move load-bearing ones to `paper/data/md/`, or (b) update referring documents to remove the citation. The conservative recommendation is to leave `paper/data/legacy/` intact for this round and focus cleanup on the truly orphaned subtrees.

---

## Tier 1 — SAFE TO DELETE (full directories; grep-verified zero active references)

These directories are explicitly legacy-marked or contain only archival references; pre-deletion grep returned no hits in any active draft (`paper.md`, `JSR_PAPER.md`, `Thesis/PART_*.md`):

- `paper/legacy/` (4 files) — old PDF builds of pre-2026 drafts
  - `AidanYu_JOSS_PAPER.pdf`
  - `AidanYu_Supersonic_Aerodynamics_Manuscript.pdf`
  - `OpenRocketPlus-Thesis.pdf`
  - `README.md`
- `paper/data/png/legacy/` (4 PNGs) — superseded figures
- `paper/data/png/hoerner_figs/` (10 PNGs) — Hoerner book figure crops; not embedded
- `paper/data/png/l52e06_figs/` (20 PNGs) — NACA L-52E06 PDF crops
- `paper/data/png/love_figs/` (30 PNGs) — Love rocket dynamics PDF crops
- `paper/data/png/peck_figs/` (22 PNGs) — Peck aerodynamics PDF crops
- `paper/data/png/rm10_figs/` (21 PNGs) — RM-10 PDF crops (RM-10 is geometry-excluded from headline)
- `paper/data/png/tr1036_figs/` (6 PNGs) — TR-1036 PDF crops

**NOT in Tier 1 (was previously here; demoted by safety check):**
- ~~`paper/data/legacy/` (20 files)~~ — at least one file (`rm10_vs_basic_finner_diagnostic.md`) is cited from `Thesis/PART_E.md` and `data/VALIDATION_MATRIX.md`. See Tier 6 below.

## Tier 2 — DELETE INDIVIDUAL FILES (~12 CSVs in paper/data/csv/)

Old comparison data superseded by current corpus / validation artifacts:

- `paper/data/csv/legacy/` (5 CSVs) — old root OpenRocket runs
- `paper/data/csv/high_m_drag_decomposition.csv`
- `paper/data/csv/hoerner_fig2_base_drag_compilation.csv`
- `paper/data/csv/naca_rm_l52e06_base_drag.csv`
- `paper/data/csv/naca_tn_3320_rm10_base_pressure.csv`
- `paper/data/csv/naca_tn_3372_base_pressure.csv`
- `paper/data/csv/nasa_tn_d4821_base_drag.csv`
- `paper/data/csv/simvreal_parity_matrix.csv`

## Tier 3 — DELETE INDIVIDUAL MD FILES (~4 files)

- `paper/data/md/legacy/` (3 MDs + README) — old validation reports from first AST draft

## Tier 4 — DELETE OLD PDF BUILD (1 file)

- `paper/Thesis/OpenRocketPlus-Thesis.pdf` — old PDF build of the Thesis; the .md sources are authoritative, will be rebuilt after PART updates complete.

## Tier 5 — REPURPOSE OR DELETE (decision needed)

- `paper/AST_PAPER.md` — superseded by `paper/JSR_PAPER.md` (just written from first principles).
  - Option A: DELETE. Clean cut.
  - Option B: rename to `paper/legacy/AST_PAPER.md` for audit trail.
  - **Recommendation: Option A — delete.** The git history is the audit trail; no need for an in-tree archive. JSR_PAPER.md replaces it.

- `paper/ast-metadata.yaml` — AST submission metadata. JSR uses different metadata (ScholarOne fields, no AIAA-side YAML required); this file is obsolete.
  - **Recommendation: delete.** Create `jsr-metadata.yaml` when needed at submission time.

- `paper/data/AST_REVIEWER_AUDIT_2026_05_02.md` — pre-submission audit doc from the AST paper era. Likely superseded by JSR_REQUIREMENTS_RESEARCH and JSR_CITATIONS_VERIFIED.
  - **Recommendation: read once, then delete.**

- `paper/TECH_DOCS_AGENT_BRIEF.md` — internal writing brief, not part of publications.
  - **Recommendation: keep** (or move to `.claude/` if you prefer).

## Tier 6 — CASE-BY-CASE: `paper/data/legacy/` files (20 files; mixed status)

At least one file is load-bearing; others are likely safe. Three options for handling this directory:

**Option A — Conservative (recommended):** leave `paper/data/legacy/` untouched for this cleanup round. The directory is small (~20 files) and contains historical context. Revisit later if needed.

**Option B — Audit-then-delete:** for each file in `paper/data/legacy/`, grep all active docs for references; promote referenced files to `paper/data/md/`; delete unreferenced ones. ~20 minutes of grep + edit work.

**Option C — Full-archive move:** move the entire directory to a sibling `paper/_archive/` outside the publication tree; update VALIDATION_MATRIX.md and PART_E.md citations to use the new path.

Known load-bearing files in `paper/data/legacy/` (DO NOT DELETE unless references are updated first):
- `paper/data/legacy/rm10_vs_basic_finner_diagnostic.md` — cited by PART_E §11.3.6, §12.6 limitations, and VALIDATION_MATRIX.md line 171

**Recommendation: Option A** for this round. Push the audit to a follow-up task.

---

## DO NOT DELETE (must keep, even if not directly cited)

### Active drafts and analysis
- All in `paper/data/analysis/` — current statistical analysis artifacts (CURRENT)
- All in `paper/data/snapshots/` — historical baselines for diff-tracking
- All in `paper/data/outlier_closure/` — per-flight closure docs
- All in `paper/Thesis/` (PART_A through PART_E, FULL_TECHNICAL_REPORT.md, zenodo-deposit.md)
- All root-level: `paper.md`, `JSR_PAPER.md`, `JSR_PAPER_PLAN.md`, `JSR_REQUIREMENTS_RESEARCH.md`, `JSR_READINESS_BRIEF_2026_05_11.md`, `JSR_CITATIONS_VERIFIED.md`, `paper.bib`, `plot_all_validation.py`, `plot_style.py`, `requirements.txt`, `README.md`, `build-thesis-pdf.ps1`, `joss-header.tex`, `thesis-metadata.yaml`, `templates/`

### Data provenance — DO NOT DELETE
- **`paper/data/pdf/` (55 PDF files)** — primary reference source library backing every digitized CSV and validation memo. Deleting this breaks the data-provenance chain required for AIAA numerical-accuracy policy compliance. Inventory marked these "LEGACY" in the categorization sense (they are source documents, not outputs), but they are NOT safe to delete.
- `paper/data/csv/` (45+ files marked CURRENT) — validation source data
- `paper/data/md/` (28+ files marked CURRENT) — per-benchmark validation reports
- `paper/data/png/` (20 root-level PNGs marked CURRENT) — figures embedded in active manuscripts
- `paper/data/ork/` — sounding-rocket geometry files

---

## Execution plan (revised after safety checks; awaiting user approval)

```bash
# Tier 1 — directory removals (safety-checked: zero active references)
rm -r "paper/legacy"
rm -r "paper/data/png/legacy"
rm -r "paper/data/png/hoerner_figs"
rm -r "paper/data/png/l52e06_figs"
rm -r "paper/data/png/love_figs"
rm -r "paper/data/png/peck_figs"
rm -r "paper/data/png/rm10_figs"
rm -r "paper/data/png/tr1036_figs"

# Tier 2 — CSV cleanup
rm -r "paper/data/csv/legacy"
rm "paper/data/csv/high_m_drag_decomposition.csv"
rm "paper/data/csv/hoerner_fig2_base_drag_compilation.csv"
rm "paper/data/csv/naca_rm_l52e06_base_drag.csv"
rm "paper/data/csv/naca_tn_3320_rm10_base_pressure.csv"
rm "paper/data/csv/naca_tn_3372_base_pressure.csv"
rm "paper/data/csv/nasa_tn_d4821_base_drag.csv"
rm "paper/data/csv/simvreal_parity_matrix.csv"

# Tier 3 — MD legacy
rm -r "paper/data/md/legacy"

# Tier 4 — old PDF build
rm "paper/Thesis/OpenRocketPlus-Thesis.pdf"

# Tier 5 — superseded by JSR
rm "paper/AST_PAPER.md"
rm "paper/ast-metadata.yaml"
rm "paper/data/AST_REVIEWER_AUDIT_2026_05_02.md"  # optional; read once first

# Tier 6 — paper/data/legacy/ — LEFT IN PLACE this round. See §Tier 6 above.

# Post-deletion verification
grep -r "AST_PAPER\|ast-metadata" paper/*.md paper/Thesis/*.md  # expect empty
grep -r "hoerner_figs\|l52e06_figs\|love_figs\|peck_figs\|rm10_figs\|tr1036_figs" paper/*.md paper/Thesis/*.md  # expect empty
```

Total expected deletion this round: ~133 PNGs + ~12 CSVs/MDs + 3-4 root files. After cleanup, `paper/` should drop from ~389 to ~245 files. The deferred `paper/data/legacy/` audit (Tier 6) would remove an additional ~18 files when handled separately.

---

## Pre-deletion safety checks (executed 2026-05-16)

Results:

1. `*_figs/` reference check on PNG subdirs (Tier 1 PNGs): **0 hits in active docs.** SAFE to delete.
2. `legacy/` path reference check: **11 hits.** Investigated; resolved as:
   - `paper/data/legacy/rm10_vs_basic_finner_diagnostic.md` is load-bearing — see Tier 6.
   - `paper/data/md/legacy/README.md` and `paper/data/legacy/README.md` are just archive markers — fine to delete with their parent directories.
   - `paper/README.md` and `paper/data/VALIDATION_MATRIX.md` describe legacy folders in pointer text — soft references, harmless after deletion.
   - `paper/data/outlier_closure/*.md` and `paper/data/md/prompt20_regression_lock_inventory.md` mention legacy in passing — harmless after deletion.
3. `AST_PAPER` / `ast-metadata` reference check: **7 hits.** Resolved:
   - `paper/README.md` already updated to JSR_PAPER (2026-05-16).
   - `paper/JSR_PAPER_PLAN.md` and `paper/JSR_READINESS_BRIEF_2026_05_11.md` reference AST_PAPER as the source-of-content for the rewrite — historical context, leave as-is.
   - `paper/TECH_DOCS_AGENT_BRIEF.md` — internal brief; harmless to retain mention.
   - `paper/data/AST_REVIEWER_AUDIT_2026_05_02.md` — itself part of the cleanup, see Tier 5.

---

## Recommendation

The revised Tier 1-5 cleanup is safe to execute. Git history preserves everything we delete, so recovery is one `git checkout` away. Tier 6 (paper/data/legacy/ files audit) is deferred to a follow-up session.

**Awaiting user approval to execute Tier 1-5.**
