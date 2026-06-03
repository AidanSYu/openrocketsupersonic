# Paper Folder — Guide

> Paths below are relative to the `paper/` root (this guide lives in `paper/_shared/`).
> The single source of truth for every number/claim is [`CANONICAL_FACTS.md`](CANONICAL_FACTS.md) (this folder).

The `paper/` **root contains only the five submission-ready PDFs**. All sources, build
scripts, shared docs, supporting data, and archived drafts live in subfolders.

## The five deliverables (root)

| # | PDF (root) | Genre | Venue | Source |
|---|---|---|---|---|
| 1 | `1_JSR_ShockGeometryPrePass.pdf` | Research article | AIAA *J. Spacecraft & Rockets* | `1_research_jsr/` (LaTeX) |
| 2 | `2_JOSS_OpenRocketPlus.pdf` | Software paper | *Journal of Open Source Software* | `2_joss/` (pandoc) |
| 3 | `3_DataInBrief_RocketFlightDatabase.pdf` | Data article | Elsevier *Data in Brief* | `3_data_dib/` (LaTeX) |
| 4 | `4_Zenodo_TechnicalReport.pdf` | Technical report (non-peer-reviewed) | *Zenodo* self-deposit | `Thesis/` (pandoc) |
| 5 | `5_ASCEJAE_BaseDragDominance.pdf` | Research article (distinct question) | ASCE *J. Aerospace Engineering* | `5_basedrag/` (LaTeX) |

Companion-paper relationships and the anti-salami scope boundaries are documented in
[`CANONICAL_FACTS.md`](CANONICAL_FACTS.md) §J. Per-paper submission status, cover letters, and the
user-action gates are in [`SUBMISSION_READINESS.md`](SUBMISSION_READINESS.md).

## Folder map

```
paper/
├── 1_JSR_ShockGeometryPrePass.pdf          ┐
├── 2_JOSS_OpenRocketPlus.pdf               │
├── 3_DataInBrief_RocketFlightDatabase.pdf  ├─ the 5 submission PDFs (root = these only)
├── 4_Zenodo_TechnicalReport.pdf            │
├── 5_ASCEJAE_BaseDragDominance.pdf         ┘
│
├── 1_research_jsr/   Paper 1 LaTeX source (jsr_paper.tex + sections/ + new-aiaa.cls/.bst + jsr_paper.bib)
├── 2_joss/           Paper 2 JOSS source (paper.md, paper.bib, joss-header.tex, build-joss.ps1)
├── 3_data_dib/       Paper 3 LaTeX source (dib.tex + sections/ + dib.bib)
├── 5_basedrag/       Paper 5 LaTeX source (basedrag.tex + sections/ + basedrag.bib),
│                       plus AST_UPGRADE_HANDOFF.md and AST_PAPER.md (the AST-upgrade brief + legacy AST source)
├── Thesis/           Paper 4 source: PART_A.md … PART_E.md (source of truth),
│                       FULL_TECHNICAL_REPORT.md (build artifact), zenodo-deposit.md
│
├── _shared/          Cross-paper docs: CANONICAL_FACTS.md, SUBMISSION_READINESS.md,
│                       coverletter_p1..p5.md, and this README
├── _build/           Build/figure infrastructure: build-thesis-pdf.ps1, thesis-metadata.yaml,
│                       templates/, plot_all_validation.py, plot_style.py, requirements.txt
├── _archive/         Superseded working drafts (JSR_PAPER*.md, *_PLAN/BRIEF/RESEARCH/VERIFIED,
│                       CLEANUP_PLAN.md, ast-metadata.yaml, …) and legacy/ (pre-2026-05 PDF builds; do not cite)
│
└── data/             Validation evidence & analysis
    ├── analysis/     v1.2 corpus statistics, uncertainty, sensitivity sweep
    ├── snapshots/    historical frozen corpus baselines
    ├── outlier_closure/  per-flight closure sheets
    ├── csv/          numeric data underlying figures/tables
    ├── png/          generated figures embedded by the papers
    ├── pdf/          source reference library (NACA/NASA/ESDU/…) — data-provenance chain
    ├── md/           per-benchmark validation reports
    ├── ork/          sounding-rocket .ork geometry files
    ├── py/           plotting/analysis scripts
    └── legacy/       historical working notes (incl. the load-bearing rm10_vs_basic_finner_diagnostic.md)
```

## Corpus headline (Rocket Flight Database v1.2, 25 flights)

| Metric | Value |
|---|---|
| Flights | 25 (23 single-stage + 2 two-stage: AeroPac 104K @ M3.04, MESOS 293K @ M4.33) |
| Mach range | 0.54 – 4.33 (validated); exploratory high-Mach set to M7.2 reported separately |
| Mean signed apogee error | **−0.38%** (σ 5.44%, RMSE 5.34%, MAE 4.74%) |
| Within ±10% | **25 / 25** |
| Within ±5% | 14 / 25 |
| vs RASAero II (25 paired) | Wilcoxon W=143.0, **p = 0.615** → statistical **parity**, not superiority (\|ORP\|−\|RAS\| = −0.60 pp, 95% CI [−2.16, +0.96]) |
| Largest single-flight error | MESOS 293K: **−6.96%** (273,056 ft) — within ±10%; the standing current-code value (not a regression) |

Source: `data/analysis/corpus_bias_variance_2026_05_11/`. Corpus data:
[Rocket Flight Database on Zenodo](https://doi.org/10.5281/zenodo.19976138) (CC-BY-4.0).

## Building the deliverables

All builds are manual/local (no CI builds the papers). MiKTeX (`pdflatex`/`bibtex`) and Pandoc required.

- **Paper 1 / 3 / 5 (LaTeX):** from the paper's source dir, `pdflatex <name> → bibtex <name> → pdflatex <name> ×2`
  (`jsr_paper` in `1_research_jsr/`, `dib` in `3_data_dib/`, `basedrag` in `5_basedrag/`).
- **Paper 2 (JOSS):** `.\2_joss\build-joss.ps1` → emits `2_JOSS_OpenRocketPlus.pdf`.
- **Paper 4 (Thesis):** `.\_build\build-thesis-pdf.ps1` → concatenates `Thesis/PART_A..E.md` and emits
  `Thesis/OpenRocketPlus-Thesis.pdf`.
- After a rebuild, copy the produced PDF to its root deliverable name (the root copies are the staged submissions).
- **Figures:** `pip install -r _build/requirements.txt && python _build/plot_all_validation.py`.

## Where to start

- **Numbers/claims** → [`CANONICAL_FACTS.md`](CANONICAL_FACTS.md) (authoritative).
- **Submission status, cover letters, user-action gates** → [`SUBMISSION_READINESS.md`](SUBMISSION_READINESS.md).
- **Paper 5 → AST upgrade** → `5_basedrag/AST_UPGRADE_HANDOFF.md`.
- **A specific validation claim** → `data/md/<benchmark>_validation_report.md`.
- **A corpus outlier's audit trail** → `data/outlier_closure/<rocket>_closure.md`.
