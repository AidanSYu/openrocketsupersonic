# Zenodo Deposit Kit — Technical Report

This file is **not part of the publication**. It collects the metadata you'll paste into Zenodo's web upload form when depositing the technical report PDF.

## Files to upload

- `OpenRocketPlus-Thesis.pdf` (the built thesis PDF in this folder)

You do not need to upload the PARTs or the markdown source. The PDF is the canonical artifact.

## Zenodo metadata fields

Copy-paste each field into the Zenodo "New Upload" form.

### Resource type
**Publication** → **Technical report** *(not "thesis" — this is positioned as a technical report, not a degree-granting thesis)*

### Title
```
Supersonic and Hypersonic Aerodynamic Extensions for OpenRocket: A Technical Report
```

### Authors
- Family name: `Yu`
- Given name: `Aidan`
- Affiliation: `Independent Researcher (acknowledges Duke University support)`
- ORCID: `0009-0005-9589-5314`

### Description / Abstract
```
This technical report documents the OpenRocket Plus aerodynamic extensions as
implemented in the current Java codebase. The work replaces the original
low-subsonic Barrowman assumptions with compressible atmosphere models, shock
and expansion solvers, transonic blending, supersonic and hypersonic drag
models, local-flow coupling for fin stability, static and dynamic stability
corrections, high-angle-of-attack effects, and numerical hardening for
six-degree-of-freedom simulation.

Validation is reported claim by claim: 27 externally benchmarked subsystem
results against published wind-tunnel and free-flight data, 9 results
calibrated against the integrated 25-flight corpus, and 1 negative external
benchmark (NACA RM-10) that bounds an excluded geometry family.

The integrated 25-flight corpus closes at 4.74% mean absolute apogee error
(mean signed error -0.38%, sigma 5.44%) with 25/25 flights within +/-10%; on
the same imported geometries the RASAero II predictions average 5.34% mean
absolute error with 22/25 within +/-10%. The honest claim is statistical
parity with this version-locked RASAero II comparison set, not superiority:
a paired Wilcoxon signed-rank test on absolute errors shows no significant
difference. The corpus is published separately as the Rocket Flight Database
v1.2 (doi:10.5281/zenodo.19976138).

The complete OpenRocket Plus source code is available as an open-source
fork at https://github.com/AidanSYu/openrocketsupersonic.
```

### Keywords
```
aerospace
aerodynamics
supersonic
hypersonic
high-power rocketry
rocket simulation
shock relations
Taylor-Maccoll
DATCOM
Van Driest II
Modified Newtonian
trajectory simulation
validation
OpenRocket
RASAero II
```

### Publication date
Use today's date when you upload.

### Language
`English`

### Communities
*Optional but recommended*: search for and add
- `Open Source Software`
- `Aerospace`

### License
**Creative Commons Attribution 4.0 International (CC-BY-4.0)**

### Version
`1.0` *(or whatever version you're publishing)*

### Related identifiers
Add each as `IsSupplementedBy` or `References`:

| Relation | Identifier | Resource type |
|---|---|---|
| `IsSupplementedBy` | `https://github.com/AidanSYu/openrocketsupersonic` | Software |
| `IsSupplementedBy` | `10.5281/zenodo.19976138` | Dataset |
| `References` | `https://www.rasaero.com/comparisons-alt.htm` | Other |

### Funding
*(Leave empty unless funded.)*

### References
Optional — Zenodo can pull these from the PDF, but you can also paste a flat reference list. Skip this on first upload; can be added later.

---

## Recommended workflow

1. Upload the PDF to Zenodo via the web UI: https://zenodo.org/deposit/new
2. Paste each field above into the form.
3. Save as draft, review, then publish. Zenodo mints the DOI immediately.
4. Once minted, add the DOI to:
   - The OpenRocket Plus README on GitHub
   - Your CV / website
   - Any AST/JSR submission reference list

5. Keep a v1.0.0 git tag of the thesis source (PARTs + metadata + build script)
   in the openrocketsupersonic repo so the PDF is reproducible from source.

## Suggested Zenodo title alternatives

If the long title is awkward, these are also defensible:

- "Compressible-Flow Aerodynamic Extensions for the OpenRocket Trajectory Simulator"
- "OpenRocket Plus: Aerodynamic Extensions to Mach 10 — Technical Report"
- "Supersonic and Hypersonic Aerodynamics for Open-Source Rocket Trajectory Simulation"
