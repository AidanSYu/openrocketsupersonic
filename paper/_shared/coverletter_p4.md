**Aidan Yu**
Independent Researcher (acknowledges Duke University support)
ORCID: 0009-0005-9589-5314
aidansyu@gmail.com

3 June 2026

**To: Zenodo Editorial / Curation (self-deposit)**

**Re: Technical report deposit — *Supersonic and Hypersonic Aerodynamic Extensions for OpenRocket: A Technical Report***

To the Editor,

I am depositing the attached technical report documenting the OpenRocket Plus aerodynamic extensions as implemented in the open-source Java codebase. The work replaces OpenRocket's original low-subsonic Barrowman assumptions with compressible atmosphere models, shock and expansion solvers, transonic blending, supersonic/hypersonic drag models, a shock-geometry pre-pass supplying local post-shock conditions for fin stability, and static/dynamic-stability and high-angle-of-attack corrections. Validation is reported claim by claim: 27 externally benchmarked subsystems, one negative external benchmark (NACA RM-10) that bounds an excluded geometry family, and an integrated 25-flight ground-truth corpus (Mach 0.54–4.33) closing at −0.38% mean signed apogee error (σ = 5.44%, MAE 4.74%, 25/25 within ±10%); on the same imported geometries RASAero II averages 5.34% mean absolute error. A paired Wilcoxon signed-rank test (W = 143.0, p = 0.615) shows no significant difference, so the claim is statistical **parity** with this version-locked RASAero II set — not superiority.

**Resource type and positioning.** This is a self-archived **technical report** (resource type: *Technical report*, **not** thesis — it is not a degree-granting document). It is **non-peer-reviewed gray literature / preprint-equivalent**: the complete monograph that the four peer-reviewed and software/data distillations summarize and cite. I request deposit under **CC-BY-4.0**.

**Companion-manuscripts disclosure (anti-fragmentation).** This report is the superset documentation for a five-item family that spans distinct genres and research questions, each citing the others; this deposit is not a fifth slice of a single result:

- **Paper 1 — research article**, AIAA *Journal of Spacecraft and Rockets* (in preparation/for submission): "A Shock-Geometry Pre-Pass for Supersonic Rocket Aerodynamic Prediction and Flight Validation" — the integrated method and 25-flight parity result.
- **Paper 2 — software paper**, *Journal of Open Source Software* (in preparation/for submission): "OpenRocket-Plus: Open-Source Supersonic Aerodynamic Extensions for Rocket Trajectory Simulation" — describes the software artifact, not new science.
- **Paper 3 — data article**, Elsevier *Data in Brief* (in preparation/for submission): "The Rocket Flight Database" — the dataset, with Paper 1 as parent article.
- **Paper 5 — research article**, ASCE *Journal of Aerospace Engineering* (in preparation/for submission): "Base Drag Dominates the Apogee Error Budget in Open-Source Supersonic Rocket Simulation" — a distinct mechanism-attribution question (which closure governs apogee error), not Paper 1's integrated-parity question.

This report (Paper 4) is the full technical record; the journal/software/data manuscripts are non-overlapping distillations. Per prior-publication policy, a gray-literature deposit of this kind is low-risk and is disclosed to every venue.

**Data, code, and AI disclosure.** The validation corpus is published separately as the Rocket Flight Database v1.2 (Zenodo DOI 10.5281/zenodo.19976138, CC-BY-4.0). The source code is at github.com/AidanSYu/openrocketsupersonic (GPL-3.0; archival code DOI minted at submission). Generative AI assisted with language editing, formatting, and code review only; all claims, equations, and numbers were authored and verified by me, and no AI is credited as an author.

I confirm the work is original, the deposit does not infringe third-party rights, and the above companion relationships are disclosed in full. Thank you for hosting this record.

Sincerely,
Aidan Yu
