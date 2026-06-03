To the Editor,
Journal of Aerospace Engineering (ASCE)

Dear Editor,

I am pleased to submit *Base Drag Dominates the Apogee Error Budget in Open-Source Supersonic Rocket Simulation: A Mechanism-Attribution Study with External Base-Pressure Benchmarks* for consideration as a research article in the *Journal of Aerospace Engineering*.

**Contribution.** This applied-aerodynamics and flight-mechanics study asks how a single aerodynamic closure propagates through a six-degree-of-freedom trajectory to govern the quantity a vehicle designer most cares about — predicted apogee. A controlled mechanism ablation across a 24-flight single-stage corpus (Mach 0.54–3.46) localizes the base-drag closure at the top of the supersonic-rocket apogee error budget: disabling the finned-base drag augmentation shifts mean apogee error by 8.10 percentage points — roughly nine times the next submodel (Van Driest II skin friction, 0.87 pp) and about 54 times the shock-geometry pre-pass (0.15 pp). The closure branches are anchored to external, out-of-sample base-pressure measurements (NACA TN 3393 turbulent 15.9% MAPE, laminar 4.4%; Hart NACA RM L52E06 free-flight subset 4.0%), and the two corpus-frozen scale constants are shown to generalize via a decontaminated prospective holdout (blind subset MAE 3.95% versus 5.47% on development).

**Fit and scope.** This is a mechanism-attribution study supported by external component benchmarks — not a per-closure apogee intercomparison, and not an integrated-validation or parity claim. By identifying where modeling and validation effort should be concentrated in supersonic-rocket apogee prediction, it fits the journal's scope for aerospace vehicle modeling and analysis.

**Companion manuscripts (disclosure).** This article belongs to a family of complementary works of distinct genres and research questions; each cites the others, and none restates another's result:

- **Paper 1 — research article**, AIAA *Journal of Spacecraft and Rockets* (in preparation): "A Shock-Geometry Pre-Pass for Supersonic Rocket Aerodynamic Prediction and Flight Validation" — the integrated method and 25-flight flight-parity validation against the commercial RASAero II baseline (parity, not superiority). *Distinct from the present work:* Paper 1 reports integrated apogee parity treating base drag as one component among many; the present article diverges into a focused per-mechanism attribution, ranking which submodel governs the error budget and benchmarking the dominant closure against external base-pressure data. The integrated head-to-head and trajectory-level statistics are deferred entirely to Paper 1; this manuscript stands or falls on the base-drag mechanism question alone.
- **Paper 2 — software paper**, *Journal of Open Source Software* (in preparation): "OpenRocket-Plus" — the open-source software artifact (architecture, install, use, tests), not new science.
- **Paper 3 — data article**, Elsevier *Data in Brief* (in preparation): "The Rocket Flight Database" — the schema, provenance, and reuse value of the ground-truth dataset; its parent research article is Paper 1.
- **Paper 4 — technical report (non-peer-reviewed)**, *Zenodo*: the full monograph and complete derivations/validation from which the others are distilled.

These are companion works across different outlets and genres, not fragments of one study.

**Availability and disclosure.** The Rocket Flight Database (v1.2) is archived on Zenodo at DOI 10.5281/zenodo.19976138 (CC-BY-4.0). The simulation code is openly developed at https://github.com/AidanSYu/openrocketsupersonic under GPL-3.0; an archival snapshot of the exact code version, with a matching Zenodo DOI and a tagged release, is minted at submission. The ablation and intercomparison scripts that regenerate every table reside in the repository and operate directly on the published dataset, so every reported quantity is reproducible. Generative AI tools were used solely for language editing, formatting, and code review; all models, equations, numerical results, and claims were authored and independently verified by the human author, and no AI authorship is claimed.

Thank you for your consideration. I confirm this work is original and is not under review elsewhere.

Sincerely,
Aidan Yu
Independent Researcher (with acknowledged support from Duke University)
ORCID 0009-0005-9589-5314
asy22@duke.edu
