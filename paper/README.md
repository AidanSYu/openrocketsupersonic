# Paper Folder

This folder contains the publication artifacts for the OpenRocket Plus supersonic/hypersonic aerodynamics work, plus all supporting validation data, figures, and analysis.

## Active publication artifacts

| Deliverable | File | Purpose |
|---|---|---|
| **JOSS paper** | [`paper.md`](paper.md) | Short-form software paper for the *Journal of Open Source Software* — describes the open-source tooling, validation suite, and reproducibility |
| **JSR paper** | [`JSR_PAPER.md`](JSR_PAPER.md) | Full-length research paper targeted at *AIAA Journal of Spacecraft and Rockets* — methodology + integrated validation against the 28-flight Rocket Flight Database v1.2 |
| **Technical Report (Supporting Information)** | [`Thesis/FULL_TECHNICAL_REPORT.md`](Thesis/FULL_TECHNICAL_REPORT.md) | Long-form reference document accompanying the JSR paper. Built by concatenating [`Thesis/PART_A.md`](Thesis/PART_A.md) → [`Thesis/PART_E.md`](Thesis/PART_E.md). The PARTs are the source-of-truth; `FULL_TECHNICAL_REPORT.md` is the build artifact. |

## Planning and verification documents

- [`JSR_PAPER_PLAN.md`](JSR_PAPER_PLAN.md) — architectural blueprint for the JSR paper (section structure, figure/table/citation masters, risk-mitigation map)
- [`JSR_REQUIREMENTS_RESEARCH.md`](JSR_REQUIREMENTS_RESEARCH.md) — AIAA JSR submission requirements, reviewer expectations, and comparable-paper analysis
- [`JSR_CITATIONS_VERIFIED.md`](JSR_CITATIONS_VERIFIED.md) — verification status of every JSR citation (22 PASS / 6 FAIL / 1 UNREACHABLE)
- [`JSR_READINESS_BRIEF_2026_05_11.md`](JSR_READINESS_BRIEF_2026_05_11.md) — earlier readiness assessment before the rewrite from first principles
- [`CLEANUP_PLAN.md`](CLEANUP_PLAN.md) — proposed legacy-file deletion plan (awaiting confirmation)

## Folder map

```
paper/
├── paper.md                       JOSS submission
├── JSR_PAPER.md                   JSR submission (current)
├── JSR_PAPER_PLAN.md              JSR architectural blueprint
├── JSR_REQUIREMENTS_RESEARCH.md   JSR submission requirements research
├── JSR_CITATIONS_VERIFIED.md      Citation verification report
├── JSR_READINESS_BRIEF_2026_05_11.md  Earlier readiness brief
├── CLEANUP_PLAN.md                Legacy-file deletion proposal
├── TECH_DOCS_AGENT_BRIEF.md       Internal writing brief
│
├── Thesis/                        Long-form Technical Report (SI)
│   ├── PART_A.md ... PART_E.md    SI source sections (source of truth)
│   ├── FULL_TECHNICAL_REPORT.md   Built SI (concat of PART_A..E)
│   └── zenodo-deposit.md          Zenodo deposit metadata
│
├── *.yaml *.tex *.bib *.ps1       Build metadata and scripts
├── plot_all_validation.py         Figure generation
├── plot_style.py
├── requirements.txt               Python build deps
├── templates/                     Pandoc/LaTeX templates
│
└── data/                          Validation evidence and supporting data
    ├── README.md                  Detail of data folder
    ├── analysis/                  v1.2 corpus statistical analysis + sensitivity sweep
    ├── snapshots/                 Historical frozen corpus baselines
    ├── outlier_closure/           Per-case closure sheets
    ├── cfd_inventory_2026_05_02.md   Published-CFD comparator inventory
    ├── csv/                       Numeric data underlying figures
    ├── png/                       Generated figures
    ├── pdf/                       Source reference papers (NACA, NASA, ESDU, etc.)
    ├── py/                        Plotting and analysis scripts
    ├── md/                        Per-benchmark validation reports
    ├── ork/                       Sounding-rocket .ork geometry files
    └── txt/                       Extracted text from reference PDFs
```

## Where to start reading

- **Want the JSR paper headline?** → Read [`JSR_PAPER.md`](JSR_PAPER.md) §8 — 28-flight corpus, mean signed −0.44%, σ 5.13%, 28/28 within ±10%, paired Wilcoxon vs RASAero II p = 0.375.
- **Want the architectural novelty?** → [`JSR_PAPER.md`](JSR_PAPER.md) §2 — ShockGeometry pre-pass.
- **Want the corpus statistics?** → [`data/analysis/corpus_bias_variance_2026_05_11/corpus_bias_variance_summary.md`](data/analysis/corpus_bias_variance_2026_05_11/corpus_bias_variance_summary.md).
- **Want the sensitivity analysis?** → [`data/analysis/sensitivity_2026_05_11/sensitivity_summary.md`](data/analysis/sensitivity_2026_05_11/sensitivity_summary.md).
- **Want to write/edit one of the papers?** → `paper.md` (JOSS), `JSR_PAPER.md` (JSR), or `Thesis/PART_A..E.md` (SI sections).
- **Want to verify a specific validation claim?** → `data/md/<benchmark>_validation_report.md` (one report per A-level claim).
- **Want the audit trail for a specific corpus outlier?** → `data/outlier_closure/<rocket>_closure.md`.

## Corpus headline (v1.2, 28 flights)

| Metric | Value |
|---|---|
| Flights | 28 (25 paired with RASAero II) |
| Mach range | 0.54 – 7.22 |
| Apogee range | 3,577 ft – 897,638 ft (1.1 – 273.6 km) |
| Mean signed apogee error | −0.44% |
| Standard deviation | 5.13% |
| RMSE | 5.06% |
| MAE | 4.33% |
| Flights within ±10% | 28 / 28 |
| Flights within ±5% | 17 / 28 |
| Paired Wilcoxon (vs RASAero II) | p = 0.375 (statistical tie) |

Source: [`data/analysis/corpus_bias_variance_2026_05_11/`](data/analysis/corpus_bias_variance_2026_05_11/). Corpus data: [Rocket Flight Database on Zenodo](https://doi.org/10.5281/zenodo.19976138) (CC-BY-4.0).

## Build

PDF generation uses Pandoc + LaTeX (Eisvogel template under `templates/`). See `build-thesis-pdf.ps1`. Python figure generation: `pip install -r requirements.txt && python plot_all_validation.py`.
