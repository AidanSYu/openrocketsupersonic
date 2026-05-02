# Paper Folder

This folder contains the three publication artifacts for the OpenRocket Plus supersonic/hypersonic aerodynamics work, plus all supporting validation data, source figures, and research history.

## The three publication artifacts

| Deliverable | File | Purpose |
|---|---|---|
| **JOSS paper** | [`paper.md`](paper.md) | Short-form software paper for the *Journal of Open Source Software* — describes the open-source tooling, validation suite, and reproducibility |
| **AST paper** | [`AST_PAPER.md`](AST_PAPER.md) | Research paper targeted at *Aerospace Science and Technology* — methodology + integrated validation against a 24-flight real-world corpus |
| **Technical Report (Supporting Information)** | [`Thesis/FULL_TECHNICAL_REPORT.md`](Thesis/FULL_TECHNICAL_REPORT.md) | Long-form reference document accompanying the AST paper as Supporting Information. Built by concatenating [`Thesis/PART_A.md`](Thesis/PART_A.md) → [`Thesis/PART_E.md`](Thesis/PART_E.md). The PARTs are the source-of-truth; `FULL_TECHNICAL_REPORT.md` is the build artifact. |

## Folder map

```
paper/
├── paper.md                       JOSS submission
├── AST_PAPER.md                   AST submission
├── TECH_DOCS_AGENT_BRIEF.md       Writing brief for agent assistance
│
├── Thesis/                        Long-form Technical Report (SI)
│   ├── PART_A.md ... PART_E.md    SI source sections (source of truth)
│   ├── FULL_TECHNICAL_REPORT.md   Built SI (concat of PART_A..E)
│   └── OpenRocketPlus-Thesis.pdf  PDF build output
│
├── *.yaml *.tex *.bib *.ps1       Build metadata and scripts
├── plot_all_validation.py         Figure generation
├── plot_style.py
├── requirements.txt               Python build deps
├── templates/                     Pandoc/LaTeX templates
│
├── legacy/                        Stale PDF builds from before May 1
│
└── data/                          Validation evidence and supporting data
    ├── README.md                  Detail of data folder
    ├── VALIDATION_MATRIX.md       *** Single dashboard for the publication gate ***
    ├── corpus_summary_2026_05_01.md   Current frozen flight-corpus baseline
    ├── REVIEWER_DEFENSE.md        Pre-prepared answers to anticipated reviewer questions
    ├── GAP_CLOSURE_PROGRAM.md     Cross-cut gap-closure tracker
    ├── snapshots/                 Historical frozen corpus baselines (for diff-tracking)
    ├── outlier_closure/           Per-case closure sheets (one per former outlier)
    ├── csv/                       All numeric data underlying figures
    ├── png/                       Generated figures
    ├── pdf/                       Source reference papers (NACA, NASA, ESDU, etc.)
    ├── py/                        Plotting and analysis scripts
    ├── txt/                       Extracted text from reference PDFs
    ├── md/                        Per-benchmark validation reports (one per A-level claim)
    │   └── legacy/                Older versions of validation reports
    └── legacy/                    Historical research/diagnostic memos
```

## Where to start reading

- **Want the headline state?** → [`data/VALIDATION_MATRIX.md`](data/VALIDATION_MATRIX.md). Embedded SimVReal regression baseline + AST publication-gate status. ~3 min read.
- **Want to write/edit one of the papers?** → `paper.md` (JOSS), `AST_PAPER.md` (AST), or `Thesis/PART_A..E.md` (SI sections).
- **Want to verify a specific validation claim?** → Click through from the `VALIDATION_MATRIX.md` claim map to the named test or `data/md/<benchmark>_validation_report.md`.
- **Want the audit trail for a specific outlier?** → `data/outlier_closure/<rocket>_closure.md`.
- **Want the history of how we got here?** → `data/legacy/` and `data/snapshots/`.

## Build

PDF generation uses Pandoc + LaTeX (Eisvogel template under `templates/`). See `build-thesis-pdf.ps1`. Python figure generation: `pip install -r requirements.txt && python plot_all_validation.py`.
