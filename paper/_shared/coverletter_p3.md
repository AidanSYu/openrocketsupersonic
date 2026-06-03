To the Editors,
Data in Brief (Elsevier)

Dear Editors,

I am pleased to submit *The Rocket Flight Database: An Externally Curated Supersonic Sounding-Rocket and High-Power-Rocketry Apogee Corpus with Paired Commercial-Simulator Predictions* for consideration as a Data in Brief data article.

**Contribution.** The Rocket Flight Database is an openly published, externally curated apogee benchmark for supersonic rocketry: a 25-flight headline corpus spanning Mach 0.54–4.33, each flight pairing a measured apogee (barometric, GPS, optical-track, or integrated-accelerometer ground truth) with the publicly recorded RASAero II reference prediction and a reproducible OpenRocket-Plus re-simulation, plus a separate exploratory table of ~20 historical high-Mach flights (to Mach 7.2) reported in full without outcome-based selection. Every record carries per-flight provenance to its source document, and the published analysis scripts regenerate all derived statistics from the full-precision apogee columns.

**Fit and scope.** This is a data article. It documents the dataset — its schema, provenance, acquisition, and reuse value — and draws no new scientific conclusions; the accuracy analysis and its interpretation are reported and defended in the parent research article (Paper 1, below), which this article accompanies and explicitly cites as its related research article. The reuse value is as an open, non-circular benchmark for validating any rocket-trajectory or aerodynamics code against a common, version-locked set of flights.

**Companion manuscripts (disclosure).** This dataset is one work within a family of complementary outputs of distinct genres and research questions; each cites the others, and none restates another's result:

- **Paper 1 — research article**, AIAA *Journal of Spacecraft and Rockets* (in preparation): "A Shock-Geometry Pre-Pass for Supersonic Rocket Aerodynamic Prediction and Flight Validation" — the integrated method and 25-flight flight-parity validation. This is the parent research article for the present dataset; it presents and interprets the comparison statistics.
- **Paper 2 — software paper**, *Journal of Open Source Software* (in preparation): "OpenRocket-Plus" — the software artifact (architecture, install, use, tests).
- **Paper 4 — technical report (non-peer-reviewed)**, *Zenodo*: the full monograph and complete derivations/validation from which the journal works are distilled.
- **Paper 5 — research article**, ASCE *Journal of Aerospace Engineering* (in preparation): a base-drag mechanism-attribution study with external base-pressure benchmarks — a distinct research question (which closure governs the apogee error budget), not Paper 1's integrated-parity result.

These are companion works across different outlets and genres, not fragments of one study. I note in advance that an iThenticate similarity check will flag textual overlap with the Zenodo technical report (Paper 4): that report is my own self-archived, non-peer-reviewed monograph, it is cited as a related work, and it is the superset documentation from which this data article is distilled. The overlap is therefore legitimate author self-reference, not undisclosed prior publication.

**Data availability and AI disclosure.** The Rocket Flight Database is openly archived on Zenodo under DOI 10.5281/zenodo.19976138 (v1.2, CC-BY-4.0), with a mirror at https://github.com/AidanSYu/rocket-flight-database; the version-of-record described here is the synchronized deposit made at submission, whose model-prediction column matches the current archived code. The associated code is at https://github.com/AidanSYu/openrocketsupersonic (archival Zenodo DOI minted at submission). Per Data in Brief policy and good practice, generative AI tools were used for language editing, formatting, and code review only; all claims, equations, numerical values, and citation verifications were authored and independently verified by the human author. No AI authorship is claimed.

Thank you for your consideration. I confirm this work is original, the data are openly accessible under CC-BY-4.0, and the manuscript is not under review elsewhere.

Sincerely,
Aidan Yu
Independent Researcher (with acknowledged support from Duke University)
ORCID 0009-0005-9589-5314
aidansyu@gmail.com
