---
title: 'OpenRocket-Plus: Open-Source Supersonic Aerodynamic Extensions for Rocket Trajectory Simulation'
tags:
  - aerodynamics
  - rockets
  - supersonic
  - flight simulation
  - Java
  - open source
authors:
  - name: Aidan Yu
    orcid: 0009-0005-9589-5314
    affiliation: 1
affiliations:
  - name: Independent Researcher
    index: 1
date: 3 June 2026
bibliography: paper.bib
license: GPL-3.0
repository-code: 'https://github.com/AidanSYu/openrocketsupersonic'
---

# Summary

OpenRocket-Plus extends OpenRocket [@niskanen2009] with a physics-based
supersonic aerodynamic model suite, enabling six-degree-of-freedom trajectory
simulation of high-power and research rockets into the supersonic regime. The
original OpenRocket aerodynamic core is built on the Barrowman slender-body
method [@barrowman1967], which assumes subsonic, shock-free flow and becomes
progressively inaccurate above approximately Mach 0.8. OpenRocket-Plus adds a
shock-geometry pre-pass and a set of compressible-flow component models — shock
relations, wave drag, base drag, and compressible skin friction — to the same
well-structured Java codebase, while preserving full backward compatibility with
existing `.ork` design files and the familiar trajectory simulator. The fork is
developed with the goal of eventual integration into upstream OpenRocket.

The accompanying research article [@yu2026jsr] reports the scientific validation:
integrated flight prediction reaches statistical parity with the commercial
baseline RASAero II [@rogers2015] on an externally selected 25-flight corpus
spanning Mach 0.54–4.33. This software paper describes the artifact itself — its
architecture, installation, testing, and extensibility — and refers the reader
to the companion paper [@yu2026jsr] and to the open Rocket Flight Database
[@rfd_zenodo] for the underlying science and ground-truth data.

# Statement of Need

High-power rocketry vehicles, university sounding rockets, and amateur research
projects routinely reach Mach 2 and beyond. Open-source trajectory simulators —
OpenRocket and RocketPy [@rocketpy2021] — rely on the Barrowman equations, which
assume subsonic, shock-free, incompressible-boundary-layer flow and therefore
lose fidelity through the transonic regime and above. Several specific
limitations constrain the original OpenRocket above approximately Mach 0.8:

1. A clamped compressibility factor (`MIN_BETA = 0.25`) that freezes $\beta$
   through the transonic regime instead of following $\beta = \sqrt{|M^2 - 1|}$,
   flattening every coefficient that scales with $1/\beta$.
2. No wave-drag computation: fin and body pressure drag are missing supersonically,
   under-predicting total drag.
3. Tabulated nose-pressure data limited to roughly Mach 3, with unphysical linear
   extrapolation above that.
4. Linear fits for speed of sound and viscosity that err well outside the
   near-sea-level temperature range.
5. No shock geometry: every component receives freestream conditions even though
   the nose shock reduces local Mach at downstream stations.
6. No supersonic center-of-pressure correction, with consequences for predicted
   stability margins.

The commercial tool RASAero II [@rogers2015] offers the needed accuracy for this
class of work but is closed-source, Windows-only, and cannot be extended or
independently audited. Recent open-source aerospace tooling [@quintart2025] and
amateur-supersonic studies underscore the demand for inspectable, reproducible
simulation, yet no open-source tool currently couples six-degree-of-freedom
trajectory simulation with validated supersonic aerodynamics. OpenRocket-Plus
fills that gap.

# State of the Field

Table 1 summarizes the aerodynamic capabilities of the principal tools.

**Table 1. Aerodynamic capability of comparable simulation tools.**

| Tool | Trajectory | Supersonic aero | Open source |
|------|-----------|-----------------|-------------|
| OpenRocket (original) | Yes (6-DOF) | No (Barrowman, M < 0.8) | Yes |
| RASAero II | Yes (3-DOF) | Yes | No |
| RocketPy | Yes (6-DOF) | No (Barrowman) | Yes |
| Digital DATCOM | No | Yes (empirical) | No |
| OpenFOAM / SU2 | No | Yes (CFD) | Yes |
| **OpenRocket-Plus** | **Yes (6-DOF)** | **Yes** | **Yes** |

Missile DATCOM and the Aeroprediction lineage [@sooy2005] provide many of the
same empirical methods but require specialist expertise and produce coefficient
tables rather than integrated trajectories. Full CFD codes such as OpenFOAM or
SU2 resolve supersonic flows in detail but are impractical for iterative vehicle
design or routine flight simulation. OpenRocket-Plus uniquely combines
six-degree-of-freedom trajectory simulation, supersonic aerodynamics, and a
fully open, extensible codebase.

# Software Design

OpenRocket-Plus is written in Java and built within OpenRocket's multi-module
Gradle project (a core simulation library plus a Swing UI). The aerodynamic
pipeline follows the original Barrowman pattern — an orchestrating
`BarrowmanCalculator` delegates to per-component calculators — extended by a
supersonic pre-pass:

```
BarrowmanCalculator
+-- ShockGeometry.compute()      [supersonic pre-pass, inert at M < 1]
+-- BarrowmanStabilityCalculator [CN, CP, Cmq, Magnus, vortex sideforce]
+-- BarrowmanDragCalculator      [friction + pressure + base + power-on]
```

`ShockGeometry` runs once per timestep. It walks the rocket nose-to-tail,
applying the $\theta$–$\beta$–$M$ relation and Taylor–Maccoll cone flow
[@taylormaccoll1933] at the nose, then marching downstream through transitions
with oblique-shock and Prandtl–Meyer relations [@naca1135] to produce local Mach,
pressure, and temperature at each axial station. The pre-pass is verified
bit-for-bit against Taylor–Maccoll cone solutions and analytic shoulder geometry.
Below Mach 1 it returns freestream conditions with no extra computation. Each
downstream calculator then reads its local post-shock conditions rather than
freestream, correcting fin normal-force slopes, dynamic pressure, interference
factors, and drag coefficients. The pre-pass is best understood as an
architectural seam that supplies correct local flow to the supersonic component
models; its contribution to integrated apogee is small (the dominant apogee
driver is base drag), but its local-flow fidelity is what makes the downstream
supersonic models physically consistent [@yu2026jsr].

The principal component models are: a complete oblique/normal-shock and
Prandtl–Meyer package [@naca1135]; Taylor–Maccoll exact wave drag for conical
noses [@taylormaccoll1933] with shock-expansion theory for ogives;
DATCOM Section 4.1.5.1 fin wave drag [@datcom1978]; a base-drag suite spanning
laminar, turbulent, and boattail regimes whose supersonic correlation
($C_{d,\mathrm{base}} = 0.064 + 0.186/M^2$) is presented as an empirical
correlation validated against NACA TN 3393 [@chapman1955] and consistent with
ESDU 77021 [@esdu77021]; Van Driest II compressible skin friction [@hopkins1971];
and Modified Newtonian theory for the hypersonic regime [@anderson2006].

All Mach-regime transitions are $C^1$-continuous. The compressibility factor
$\beta = \sqrt{|M^2 - 1|}$ uses a cubic Hermite spline through M 0.95–1.05; base
drag blends across M 0.85–1.3; turbulent base drag activates over M 1.2–1.4; fin
wave drag ramps in over M 0.9–1.2; Modified Newtonian blends into the
shock-expansion result over M 4.0–6.0. $C^1$-continuity is an architectural
requirement: discontinuities cause the Runge–Kutta trajectory integrator to
oscillate when the vehicle repeatedly crosses a Mach threshold.

**Table 2. Mach-regime blending regions ensuring $C^1$-continuous simulation.**

| Physical quantity | Blending range | Method |
|:------------------|:--------------|:-------|
| Compressibility factor $\beta$ | M 0.95–1.05 | Cubic Hermite spline |
| Skin friction | M 0.9–1.1 | Polynomial blend |
| Base drag | M 0.85–1.3 | $C^1$ cubic blend |
| Fin wave drag | M 0.9–1.2 | Hermite activation |
| Modified Newtonian | M 4.0–6.0 | Smoothstep |

An alternative `LookupTableDragCalculator` and `LookupTableStabilityCalculator`
accept user-supplied CSV tables as overrides, enabling direct comparison against
CFD or wind-tunnel data for a specific vehicle.

# Installation and Use

OpenRocket-Plus builds with the bundled Gradle wrapper and requires a Java 17+
JDK. The graphical application launches with `./gradlew run`; a runnable
distribution is produced with `./gradlew :swing:build`. Existing OpenRocket
`.ork` design files open unchanged, and the supersonic models engage
automatically once a simulation crosses into the transonic regime — no
configuration is required for the default physics path. Per-vehicle CSV override
tables can be supplied through the lookup-table calculators for users who wish to
drive the trajectory with external CFD or wind-tunnel coefficients. Full build
and usage instructions are maintained in the repository README.

# Testing

Each aerodynamic model is an independently testable Java class with an explicit
Mach validity range, and the supersonic suite is covered by a dedicated set of
JUnit tests (the broader project ships a large existing OpenRocket test suite).
The fast core test tier runs in roughly half a minute via `./gradlew :core:test`;
heavier statistical and parameter-sweep tiers are opt-in through Gradle
properties. Each model carries claim-by-claim checks against a primary reference,
and open heuristic and geometry-family gaps are explicitly classified rather than
silently treated as closed.

Table 3 lists representative component benchmarks; the metrics, references, and
their honest interpretation are documented in full in the companion research
article [@yu2026jsr].

**Table 3. Representative component-level benchmarks.**

| Subsystem | Reference | Metric | Value |
|-----------|-----------|--------|-------|
| Oblique/normal-shock relations | NACA 1135 [@naca1135] | max err | < 0.01% |
| Taylor–Maccoll cone | Cone tables [@taylormaccoll1933] | max shock-angle rel err | 0.825% |
| Nose/body wave drag (5 shapes) | NACA RM A52H28 [@a52h28] | MAE | 0.029 |
| Base drag, turbulent | NACA TN 3393 [@chapman1955] | MAPE | 15.9% |
| Base drag, laminar (Chapman) | NACA TN 3393 [@chapman1955] | MAPE | 4.4% |
| Fin wave drag (DATCOM 4.1.5.1) | NACA TN 3650 [@ulmann1956] | MAPE | 21% |
| Fin $C_{N\alpha}$ (M 0.6–5.82) | NASA TM X-653 [@nielsen1962] | MAPE | 6.84% |
| Fin $x_{CP}/d$ (M 0.6–5.82) | NASA TM X-653 [@nielsen1962] | MAPE | 7.11% |
| Basic Finner total drag (M 1.08–4.30) | ADA636861 [@dupuis1997] | MAPE | 11.8% |
| Hypersonic cone foredrag (M 6.5–17.2) | DTIC AD0487365 [@grabow1965] | MAPE | 19.7% |

![Nose/body wave-drag coefficient versus Mach number for five nose shapes, comparing OpenRocket-Plus against NACA RM A52H28 wind-tunnel data. The headline accuracy metric is the aggregate mean absolute error, MAE = 0.029; the corresponding MAPE of 29.3% is inflated by the small absolute drag values of the power-law and Haack shapes and is reported here only for context.](data/png/naca_rm_a52h28_validation.png)

The shock solver matches NACA 1135 tabular values to better than 0.01% across the
supersonic range. Nose/body wave drag is validated on five distinct nose shapes
with an aggregate MAE of 0.029 (the headline metric); the corresponding MAPE of
29.3% is a small-denominator artifact for the low-drag power-law and Haack shapes
and is not used as the headline figure. Fin aerodynamics validated against NASA
TM X-653 (M 0.6–5.82) give $C_{N\alpha}$ MAPE 6.84% and center-of-pressure MAPE
7.11%. The Basic Finner free-flight range benchmark (total-drag MAPE 11.8%, M
1.08–4.30) [@dupuis1997] is a stringent test because it includes manufacturing
variation, sabot effects, and base-flow unsteadiness absent from wind-tunnel
pressure data. The hypersonic cone-foredrag benchmark (MAPE 19.7%, 11 points,
M 6.5–17.2) [@grabow1965] establishes the upper Mach extent of *component-level*
validation.

# Scope of Validation

The companion research article [@yu2026jsr] validates integrated flight
prediction to **Mach 4.33** on an externally selected 25-flight corpus
(Mach 0.54–4.33; released as the Rocket Flight Database [@rfd_zenodo] under
CC-BY-4.0), reporting **statistical parity** with the version-locked RASAero II
baseline — parity, **not** superiority. See that article for the full
statistics, hypothesis test, and confidence intervals.

*Component* benchmarks extend higher in Mach than the integrated flight set: the
cone-foredrag check reaches **Mach 17.2** (Table 3). Separately, an
**exploratory** high-Mach demonstration runs roughly twenty historical
sounding-rocket flights and shows that the method *can reach* Mach 7 within ±7%
on well-characterized vehicles (e.g., Black Brant VB at Mach 7.22), while
motor- and geometry-reconstruction uncertainty dominates on poorly documented
historical flights. That exploratory set is reported in full in [@yu2026jsr]
and is explicitly **not** a validation headline; OpenRocket-Plus is presented as
flight-validated to Mach 4.33 and component-validated to Mach 17.2.

# Limitations

Documented limitations include: the transonic band (M 0.8–1.3), the most
challenging regime for all comparable tools and the disclosed weakness here;
elevated MAPE for the low-drag power-law and Haack noses, where small absolute
values amplify percentage error (the MAE of 0.029 is the meaningful metric);
pitch-damping ($C_{mq}$) dynamic-stability derivatives, held at a lower
confidence tier (the transonic augmentation overshoots reference CFD), affecting
predicted coning but not apogee; and high-fineness slender-body pressure drag that
decays toward zero above Mach 5, producing a quantified positive apogee bias on
high-L/D hypersonic flights (documented as ongoing work). Real-gas dissociation
chemistry above roughly Mach 7 is not modeled. The full statistical treatment,
including the in-sample disclosure for two base-drag scale constants and a
decontaminated prospective holdout demonstrating generalization, is given in the
companion article [@yu2026jsr].

# Acknowledgements

The author thanks the OpenRocket development community for the original
open-source codebase on which this work builds, and acknowledges support from
Duke University. Generative AI assistants (Anthropic Claude) were used for
language editing, formatting, and code review only; all aerodynamic models,
equations, validation comparisons, numerical results, and citation verifications
were authored and independently verified by the human author against primary
references. No AI authorship is claimed.

# References
