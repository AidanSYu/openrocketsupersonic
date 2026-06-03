To the Editors,
Journal of Open Source Software

Dear Editors,

I am pleased to submit *OpenRocket-Plus: Open-Source Supersonic Aerodynamic Extensions for Rocket Trajectory Simulation* for consideration as a JOSS software paper.

**Contribution.** OpenRocket-Plus extends the open-source OpenRocket simulator with a physics-based supersonic aerodynamic model suite — a shock-geometry pre-pass plus compressible-flow component models (oblique/normal-shock and Prandtl–Meyer relations, Taylor–Maccoll wave drag, DATCOM fin wave drag, a base-drag suite, and Van Driest II skin friction) — enabling six-degree-of-freedom trajectory simulation of high-power and research rockets into the supersonic regime, while preserving full backward compatibility with existing `.ork` design files.

**Fit and scope.** This is a software paper. It describes the artifact itself — architecture, installation, use, testing, and extensibility — and does not present new research results. The scientific validation (a 25-flight integrated-flight comparison) is reported in the companion research article and is summarized here only to establish scope; this manuscript explicitly defers all statistics, hypothesis testing, and confidence intervals to that article. The headline accuracy framing is statistical *parity* with the commercial RASAero II baseline on an externally selected corpus spanning Mach 0.54–4.33 — parity, not superiority.

**Companion manuscripts (disclosure).** This software is one artifact within a family of complementary works of distinct genres and research questions; each cites the others, and none restates another's result:

- **Paper 1 — research article**, AIAA *Journal of Spacecraft and Rockets* (in preparation): "A Shock-Geometry Pre-Pass for Supersonic Rocket Aerodynamic Prediction and Flight Validation" — the integrated method and 25-flight flight-parity validation. This software paper cites it for the science.
- **Paper 3 — data article**, Elsevier *Data in Brief* (in preparation): "The Rocket Flight Database" — the schema, provenance, and reuse value of the ground-truth dataset; its parent research article is Paper 1.
- **Paper 4 — technical report (non-peer-reviewed)**, *Zenodo*: the full monograph and complete derivations/validation from which the others are distilled.
- **Paper 5 — research article**, ASCE *Journal of Aerospace Engineering* (in preparation): a base-drag mechanism-attribution study with external base-pressure benchmarks — a distinct research question (which closure governs the apogee error budget), not Paper 1's integrated-parity result.

These are companion works across different outlets and genres, not fragments of one study.

**Availability and disclosure.** The code is openly available at https://github.com/AidanSYu/openrocketsupersonic under GPL-3.0; a tagged release with an archival Zenodo DOI is minted at submission. The Rocket Flight Database is published at Zenodo DOI 10.5281/zenodo.19976138 (v1.2, CC-BY-4.0). Per JOSS policy and good practice, generative AI assistants were used for language editing, formatting, and code review only; all models, equations, numerical results, and citation verifications were authored and independently verified by the human author. No AI authorship is claimed.

Thank you for your consideration. I confirm this work is original, the software is open and reusable, and the manuscript is not under review elsewhere.

Sincerely,
Aidan Yu
Independent Researcher (with acknowledged support from Duke University)
ORCID 0009-0005-9589-5314
asy22@duke.edu
