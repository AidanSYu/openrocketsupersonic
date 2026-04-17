---
title: 'Supersonic and Hypersonic Aerodynamic Extensions for OpenRocket with Experimental Validation from Mach 0.3 to 17'
tags:
  - aerodynamics
  - rockets
  - supersonic
  - hypersonic
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
date: 14 April 2026
bibliography: paper.bib
license: GPL-3.0
repository-code: 'https://github.com/AidanSYu/openrocketsupersonic'
---

# Summary

This work extends OpenRocket [@niskanen2009] by replacing the aerodynamic model
suite with a physics-based implementation valid from Mach 0.3 through Mach 17+.
The original OpenRocket aerodynamic core, built on the Barrowman slender-body
method [@barrowman1967], was designed for subsonic flight and produces increasingly
inaccurate results above approximately Mach 0.8. The extended implementation adds
validated supersonic and hypersonic models to the same well-structured Java codebase
while preserving full backward compatibility with existing `.ork` rocket design
files and the familiar six-degree-of-freedom trajectory simulator. These
extensions are developed as a fork with the goal of eventual integration into
the main OpenRocket project.

The principal additions are: a complete oblique/normal shock solver and
Prandtl-Meyer expansion package [@naca1135]; a shock geometry pre-pass that
threads post-shock local flow conditions to all downstream components; Taylor-Maccoll
exact wave drag for conical noses [@taylormaccoll1933]; shock-expansion theory for
ogive bodies; DATCOM Section 4.1.5.1 fin wave drag [@datcom1978]; a four-model
base drag suite covering turbulent, laminar, and boattail regimes [@chapman1950;
@esdu77021]; Van Driest II compressible skin friction [@hopkins1971]; and Modified
Newtonian theory for the hypersonic regime [@anderson2006]. All Mach regime
transitions use C1-continuous polynomial blending to prevent trajectory integrator
instabilities near Mach 1. The software is validated through 72 aerodynamic test
files across 22 independently benchmarked subsystems.

# Statement of Need

High-power rocketry (HPR) vehicles, university sounding rockets, and amateur research
projects routinely reach Mach 2 to 5 and beyond. Yet the tools available for
open-source trajectory simulation — OpenRocket, RocketPy [@rocketpy2021], and
OpenTsiolkovsky — all rely on the Barrowman equations, which assume subsonic,
shock-free, incompressible-boundary-layer flow. Six specific failures limit
the original OpenRocket above approximately Mach 0.8:

1. A hard-clamped compressibility factor (`MIN_BETA = 0.25`) that holds beta constant
   through the transonic regime instead of following $\beta = \sqrt{|M^2 - 1|}$,
   producing a flat plateau in every coefficient that depends on $1/\beta$.
2. No wave drag computation — fin drag is skin friction only, causing systematic
   under-prediction of total drag by 10–20% at Mach 2.
3. Tabulated nose-pressure data limited to approximately Mach 3, with linear
   extrapolation above that producing physically incorrect results.
4. Linear fits for speed of sound and viscosity that err by 5–50% outside the
   near-sea-level temperature range.
5. No shock wave geometry — every component receives freestream conditions even
   though the nose shock reduces local Mach by 10–25% at the fin station.
6. No supersonic center-of-pressure correction — the CP shift of 5–10% of body
   length that occurs supersonically is absent, with implications for stability
   margin predictions.

The commercial tool RASAero II [@rogers2015] provides the accuracy needed for
HPR and research work, but is closed-source, Windows-only, and cannot be
extended or independently verified. No open-source tool currently combines
trajectory simulation with validated supersonic aerodynamics. This work fills
that gap.

# State of the Field

Table 1 summarizes the aerodynamic capabilities of the principal tools in the field.

| Tool | Trajectory | Supersonic Aero | Open Source |
|------|-----------|-----------------|-------------|
| OpenRocket (original) | Yes | No (Barrowman, M < 0.8) | Yes |
| RASAero II | Yes (3-DOF) | Yes (validated) | No |
| RocketPy | Yes | No (Barrowman) | Yes |
| Digital DATCOM | No | Yes (empirical methods) | No |
| OpenFOAM / SU2 | No | Yes (CFD) | Yes |
| **This work** | **Yes** | **Yes (validated)** | **Yes** |

Digital DATCOM [@datcom1978] provides many of the same aerodynamic methods but
requires substantial expertise to operate and produces coefficient tables rather
than integrated trajectories. Full CFD codes such as OpenFOAM or SU2 can resolve
supersonic flows in detail but are impractical for iterative vehicle design or
flight simulation. The present work uniquely occupies the combination of trajectory
simulation, validated supersonic and hypersonic aerodynamics, and a fully open,
extensible codebase.

# Software Design

The extended aerodynamic model is written in Java and built within OpenRocket's multi-module Gradle system
(core simulation library plus Swing UI). The aerodynamic pipeline follows the
original Barrowman pattern — an orchestrating `BarrowmanCalculator` delegates to
per-component calculators — extended by a supersonic pre-pass:

```
BarrowmanCalculator
+-- ShockGeometry.compute()      [supersonic pre-pass, inert at M < 1]
+-- BarrowmanStabilityCalculator [CN, CP, Cmq, Magnus, vortex sideforce]
+-- BarrowmanDragCalculator      [friction + pressure + base + power-on]
```

`ShockGeometry` is computed once per timestep. It walks the rocket nose-to-tail,
applies the theta-beta-Mach relation and Taylor-Maccoll cone flow at the nose,
and marches downstream through body transitions using oblique shock and
Prandtl-Meyer expansion relations to produce local Mach, pressure, and
temperature at each axial station. At subsonic Mach, the pre-pass returns
freestream conditions with no additional computation. Each downstream component
calculator reads its local post-shock conditions from the pre-pass result
rather than from freestream, correcting fin normal force slopes, dynamic pressure,
interference factors, and drag coefficients.

All Mach regime transitions are C1-continuous. The compressibility factor
$\beta = \sqrt{|M^2 - 1|}$ uses a cubic Hermite spline through M 0.95–1.05;
base drag blends across M 0.85–1.3; Chapman-Korst turbulent base drag activates
M 1.2–1.4; fin wave drag ramps in M 0.9–1.2; Modified Newtonian blends into
the shock-expansion result M 4.0–6.0. The requirement for C1-continuity
throughout is architectural: discontinuities cause the Runge-Kutta trajectory
integrator to oscillate when the vehicle repeatedly crosses a Mach threshold.

An alternative `LookupTableDragCalculator` and `LookupTableStabilityCalculator`
accept user-supplied CSV tables as overrides, enabling direct comparison against
CFD or wind-tunnel data for specific vehicles.

Table 2 lists the C1-continuous blending regions that prevent trajectory integrator
instabilities when the vehicle crosses Mach regime boundaries.

**Table 2. Mach regime blending regions ensuring C1-continuous simulation.**

| Physical quantity | Blending range | Method |
|:------------------|:--------------|:-------|
| Compressibility factor $\beta$ | M 0.95–1.05 | Cubic Hermite spline |
| Skin friction | M 0.9–1.1 | Polynomial blend |
| Base drag | M 0.85–1.3 | C1 cubic blend |
| Fin wave drag | M 0.9–1.2 | Hermite activation |
| Modified Newtonian | M 4.0–6.0 | Smoothstep |

# Research Impact

Table 3 summarizes validation results for the principal subsystems.

| Subsystem | Reference | MAPE |
|-----------|-----------|------|
| Oblique/normal shock relations | NACA 1135 [@naca1135] | < 0.01% |
| Nose wave drag (5 shapes) | NACA RM A52H28 [@a52h28] | 29.3% |
| Base drag, turbulent | NACA TN 3393 [@chapman1955] | 15.9% |
| Base drag, laminar | NACA TN 3393 [@chapman1955] | 4.4% |
| Fin wave drag | NACA TN 3650 [@ulmann1956] | 21% |
| Fin CNa (M 0.6–5.82) | NASA TM X-653 [@nielsen1962] | 6.8% |
| Fin xCP (M 0.6–5.82) | NASA TM X-653 [@nielsen1962] | 7.1% |
| Basic Finner total drag | ADA636861 [@dupuis1997] | 22.7% |
| Hypersonic cone drag (M 6.5–17.2) | DTIC AD0487365 [@grabow1965] | 17.8% |

![Nose wave drag coefficient versus Mach number for five nose shapes at fineness ratio 3, comparing the present model against NACA RM A52H28 wind-tunnel data. Aggregate MAE = 0.0328, MAPE = 29.3%.](data/png/naca_rm_a52h28_validation.png)

The shock solver matches NACA 1135 tabular values to better than 0.01% across
Mach 1.2–10 and cone half-angles 5–40°. Nose wave drag validated on five
distinct nose shapes (MAPE 29.3%) covers the practical geometry space using
Taylor-Maccoll (cones), second-order shock-expansion (ogives), and Dahlem-Buck
shape factors (power-law and Haack Series). Fin aerodynamics validated against
NASA TM X-653 — 26 data points spanning M 0.6 to 5.82 across multiple fin
geometries — produce CNa MAPE 6.8% and center-of-pressure MAPE 7.1%. The Van Driest II compressible
skin friction transformation [@hopkins1971] reduces $C_f$ by approximately 50%
relative to the incompressible value at Mach 5, consistent with the Hopkins and
Inouye (1971) experimental survey.

The vehicle-level Basic Finner benchmark (MAPE 22.7%, 8 points, M 1.08–4.30)
reflects real free-flight range measurements on 30 mm projectiles [@dupuis1997],
a more stringent test than wind-tunnel pressure data because it includes
manufacturing variation, sabot separation, base flow unsteadiness, and all
other physical effects present in actual flight. The hypersonic cone drag
benchmark (MAPE 17.8%, 11 points, M 6.5–17.2) confirms usable accuracy
through the Modified Newtonian regime.

The software enables a class of research and educational activities that was
previously inaccessible without commercial tools: trajectory optimization for
HPR vehicles at Mach 2–5, aerodynamic stability analysis across the full
transonic-to-supersonic transition, and open, reproducible comparison of
competing aerodynamic models. The extensible architecture — each model is
an independently testable Java class with explicit Mach validity ranges —
makes it straightforward to incorporate improved correlations as the research
literature advances.

Known limitations include elevated MAPE for nose shapes
where small absolute drag values amplify percentage errors (power-law and
Haack noses via empirical Dahlem-Buck shape factors), systematic
underprediction of total vehicle drag on the Basic Finner projectile
suggesting a missing interference or transition drag source, and reduced
center-of-pressure accuracy (xCP MAPE 7.1%) that degrades in the
transonic band where the transonic similarity approximation is least accurate. The transonic regime (M 0.8–1.3) remains the most
challenging for all models. Real-gas dissociation chemistry above
approximately Mach 7 is not included.

# Acknowledgements

The author thanks the OpenRocket development community for the original
open-source codebase on which this work is built.

# References
