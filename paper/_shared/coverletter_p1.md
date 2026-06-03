# Cover Letter — Submission to AIAA *Journal of Spacecraft and Rockets*

Aidan Yu
Department of Mechanical Engineering & Materials Science, Duke University
ORCID 0009-0005-9589-5314
asy22@duke.edu

3 June 2026

To the Editor-in-Chief and Associate Editors,
*Journal of Spacecraft and Rockets*

Dear Editors,

I am pleased to submit the original research article "A Shock-Geometry Pre-Pass for Supersonic Rocket Aerodynamic Prediction and Flight Validation" for consideration in the *Journal of Spacecraft and Rockets*.

**Contribution.** The manuscript extends the open-source OpenRocket simulator beyond its subsonic Barrowman lineage through a once-per-timestep shock-geometry pre-pass that distributes post-shock local-flow conditions to each downstream drag, normal-force, and stability calculator, unifying Taylor–Maccoll cone flow, Prandtl–Meyer expansion, shock relations, Van Driest II skin friction, and DATCOM fin wave drag in one auditable implementation. Twenty component models are externally benchmarked against published references, and the integrated simulator is validated against a 25-flight externally selected ground-truth corpus spanning Mach 0.54 to 4.33 (mean signed apogee error −0.38%, 95% bootstrap CI [−2.41, +1.72], all 25 flights within ±10%). On a decontaminated 12-flight blind holdout the model is more accurate (MAE 3.95%) than on the 13 development flights (5.47%). Against RASAero II on the 25 paired flights there is no statistically significant difference in absolute error (Wilcoxon p = 0.615); the honest claim is parity with this version-locked commercial baseline, not superiority. High-Mach behavior is reported exploratorily and without cherry-picking (3 of 20 historical flights within ±10%).

**Fit and scope.** The work follows recent precedent in this journal for validated open-source aerospace software accompanied by a documented campaign and a permanent code archive (Quintart et al. 2025; the 2025 low-cost roll-control study), combining open-source supersonic aerodynamics with reproducible flight-corpus validation. It is, to my knowledge, original and is not under consideration elsewhere.

**Companion-manuscript disclosure (anti-fragmentation).** This article is the research flagship of a deliberately structured family of distinct-genre works addressing different questions, each cross-citing the others; I disclose all of them proactively so that the relationships are transparent:

- *Software paper* — "OpenRocket-Plus: Open-Source Supersonic Aerodynamic Extensions for Rocket Trajectory Simulation," *Journal of Open Source Software* (in preparation). Describes the software artifact (architecture, install, tests); cites this article for the science. It does not re-argue the validation result.
- *Data article* — "The Rocket Flight Database," Elsevier *Data in Brief* (in preparation). Documents the dataset schema, provenance, and reuse value; names this article as its parent. It draws no new scientific conclusions.
- *Research article* — "Base Drag Dominates the Apogee Error Budget in Open-Source Supersonic Rocket Simulation: A Mechanism-Attribution Study with External Base-Pressure Benchmarks," ASCE *Journal of Aerospace Engineering* (in preparation). A distinct research question — base-drag mechanism attribution against external base-pressure benchmarks — not the present article's integrated-parity result.
- *Technical report* — full monograph / superset documentation, to be self-archived on *Zenodo* (non-peer-reviewed). The other works are distillations of it.

The Zenodo technical report is a non-peer-reviewed self-archived superset; it is cited as documentation, not a competing prior publication.

**Data, code, and AI disclosure.** The Rocket Flight Database is archived at Zenodo (DOI 10.5281/zenodo.20531977, CC-BY-4.0) with a GitHub mirror; the OpenRocket-Plus source (github.com/AidanSYu/openrocketsupersonic, GPL-3.0) carries an archival code Zenodo DOI minted at submission, with analysis scripts that regenerate every reported figure and statistic. In accordance with the AIAA policy on artificial intelligence (October 2024), a generative AI assistant (Anthropic Claude) was used for language editing, formatting, and code review only; all claims, equations, and numerical results were authored, derived, and verified by the human author, who takes full responsibility. No AI system qualifies for authorship.

Thank you for your consideration. I would be glad to provide any further information.

Sincerely,

Aidan Yu
Department of Mechanical Engineering & Materials Science, Duke University
