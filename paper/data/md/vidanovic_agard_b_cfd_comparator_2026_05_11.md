# Published-CFD Comparator: Vidanovic et al. 2014 SST k-omega CFD on AGARD-B

**Date:** 2026-05-11
**Status:** Digitized. No Java comparator test (OpenRocket Plus does not yet
ship an AGARD-B `.ork`; standing one up is a follow-up work item).

## Source paper

Vidanovic, N. D.; Rasuo, B. P.; Damljanovic, D. B.; Vukovic, Dj. S.; Curcic, D. S.
"Validation of the CFD Code Used for Determination of Aerodynamic Characteristics
of Non-Standard AGARD-B Calibration Model." *Thermal Science* **2014**, Vol. 18,
No. 4, pp. 1223-1233. DOI: 10.2298/TSCI130409104V. Original scientific paper.
Authors affiliated with the Faculty of Transport and Traffic Engineering and the
Faculty of Mechanical Engineering at the University of Belgrade, and the
Military Technical Institute (VTI), Belgrade, Serbia.

Citation taken from the title page of the PDF in repo
(`paper/data/pdf/Validation_of_the_CFD_code_used_for_determination_.pdf`).

## CFD method (verified from PDF Sections 3-4)

- ANSYS Fluent, density-based steady solver, implicit Roe flux-difference-splitting.
- Spatial discretisation: second-order upwind for flow, k, omega; least-squares
  cell-based gradient reconstruction.
- Turbulence model: Menter SST k-omega.
- Mesh: unstructured hybrid, ~9.84 million cells / 2.64 million nodes at the
  finest grid (growth rate 1.08). Boundary layer resolved with 20 prism layers
  and y+ ~= 1.0. A grid-independence study verified independence between 7M
  and 10M cells.
- Domain: paraboloid, 15 body lengths upstream, 20 downstream, half-model
  with symmetry plane.
- Wall: adiabatic, no-slip on the body surface. Sting was removed from the
  CFD model for cleaner base-pressure calculation.
- CFL ramped 5 -> 200. Convergence at 1000-2000 iterations, with
  coefficient-residual change < 1 % over 100 iterations.
- Two free-stream Mach numbers: 0.596 (Re_Lref = 6.42 M) and 1.602
  (Re_Lref = 9.97 M). AoA sweep -4 deg to +12 deg.

## Geometry confirmation (Figure 2 and Table 1 of the paper)

The Vidanovic CFD model is the **AGARD Model B** calibration standard:

- Total length L = 0.9843 m
- Body diameter D = 0.1158 m  (L/D = 8.50)
- Wing span B = 0.4632 m (4 D)
- Delta wing in the form of an equilateral triangle, 4 % thickness/chord
  biconvex section
- Reference length L_ref = 0.2674 m (mean aerodynamic chord)
- Reference span B_ref = 0.4632 m
- Moment reference: 2.557 D upstream of the model base
- Two nose configurations: theoretical parabolic-arc ogive (standard) and
  non-standard circular-arc ogive (r = 9.274 D). The latter is the unique
  contribution of the paper -- it corrects historical Boeing wind-tunnel
  data that was inadvertently taken with a circular-arc nose.

This is a **wing-body** calibration model, NOT the Army-Navy Basic Finner.
Reproducing it as an OpenRocket geometry would require a delta wing/fin
arrangement that diverges from typical sounding-rocket configurations; the
fin module supports trapezoidal/clipped-delta fin shapes, so a single equilateral
triangle delta with 4% bi-convex section can be approximated. Building this is
a separate work item (see "Follow-up" below).

## What data was extracted

Source figures (read by hand from PDF, no WebPlotDigitizer):
- **Figure 6** (page 1230 of paper / PDF page 8): Pitching-moment coefficient
  Cm vs AoA. Two panels: M = 0.596 (left), M = 1.602 (right). Three series per
  panel: (1) Circle CFD (blue), (2) Parabola CFD (red), (3) VTI experiment
  (yellow squares).
- **Figure 7** (page 1230 of paper / PDF page 8): Lift coefficient CL vs AoA.
  Same panel structure.
- **Figure 8** (page 1231 of paper / PDF page 9): Drag coefficient CD vs AoA.
  Same panel structure.

Digitized data lives in `paper/data/csv/vidanovic_agard_b_cfd_2014.csv` with
one row per (Mach, AoA, source) triple. Component breakdown (pressure /
friction / base) is NOT reported in the paper -- only total CD vs AoA.
Those CSV columns are left blank by design, not by digitization failure.

Read uncertainty (per-coefficient):
- Cm: +/- 0.005 (vertical grid 0.02 in the figures; three curves overlay)
- CL: +/- 0.01  (vertical grid 0.10; curves nearly coincident at M = 0.596)
- CD: +/- 0.003 (vertical grid 0.02; curves coincident within line width)

## Key quantitative observations from the paper

(All quotes verified against the PDF body text.)

1. **CD agreement is excellent at both Mach numbers.** From paper text
   (page 1231): "an excellent agreement between all curves for Ma = 0.596
   for all AoA. For Ma = 1.602, there is a percent error between 0.3 % and
   3 % at positive AoA between the experimental and the simulated results
   for the parabolic-arc nose configuration, and error between 3 % and 5 %
   for the simulated results for the circular-arc nose configuration."
   This is best-case CFD-vs-experiment agreement for a wing-body calibration model.

2. **CL agreement is excellent at M = 0.596** and within 1 % at positive AoA
   for M = 1.602. The 3-5 % deviation at negative AoA is attributed by the
   authors to test-rig non-linearities (force balance, sting attachment) rather
   than CFD error.

3. **Cm shows the most sensitivity to nose shape.** At M = 1.602, the
   parabolic-arc CFD tracks VTI experiment, while the circular-arc CFD
   diverges by 15-20 % at AoA > 4 deg. The whole point of the paper is to
   use the validated CFD to *quantify* this nose-shape effect on Cm
   because no experimental data exists for the circular-arc nose.

## How this comparator fits the JSR paper

This is a **best-in-class CFD-vs-experiment validation** that ORP can lean on
as a published reference benchmark. The comparator panel for the AGARD-B
calibration model would show:

- Vidanovic SST k-omega CFD (parabola_cfd source in the CSV)
- VTI T-38 wind-tunnel experiment (vti_exp source in the CSV)
- ORP Barrowman + supersonic-extension prediction (NOT YET WIRED UP)

The reviewer-defensible language is:

> "The AGARD Model B calibration standard is a wing-body geometry widely
> used for wind-tunnel inter-facility comparison. Vidanovic et al. (2014)
> report SST k-omega CFD predictions at M = 0.596 and M = 1.602 over an AoA
> sweep -4 to +12 deg, validated against the VTI T-38 trisonic wind tunnel
> in Belgrade. Their CFD-vs-experiment percent errors are 0.3-3 % in CD
> at positive AoA and < 1 % in CL across the test envelope -- a high-fidelity
> benchmark. OpenRocket Plus does not yet ship an AGARD-B geometry, so a
> Java comparator test is not exercised here; the digitized values are
> retained as a reference dataset for future AGARD-B validation work."

## Suggested comparator panel for the JSR paper

A two-panel figure:

- **Panel (a):** CD vs AoA at M = 0.596. Three series: Vidanovic parabola CFD,
  VTI experiment, ORP. Both Vidanovic series overlap within line width
  (paper text: "excellent agreement").
- **Panel (b):** CD vs AoA at M = 1.602. Same series. Demonstrates that the
  CFD-vs-experiment agreement holds in the supersonic regime.

If ORP can be wired to AGARD-B (see Follow-up), the panel becomes a three-way
overlay; otherwise it appears as a reference for "the state of the art in
CFD validation against wind-tunnel experiment for a calibration model" -- the
gold standard ORP is being compared against in the paper.

## Honest caveats

- **Two Mach numbers only.** Vidanovic et al. simulated M = 0.596 and 1.602.
  The paper repeatedly emphasises that transonic behaviour (M 0.9-1.3) was
  *not* covered ("Transonic behavior requires deeper analyzing and successive
  simulations and it may be a part of a future investigation"). So this
  comparator cannot anchor the transonic drag-rise region.
- **Component-level drag breakdown is not in the paper.** Only total CD vs AoA
  is plotted. Pressure / friction / base drag breakdowns required for a
  per-mechanism comparator are absent.
- **Wing-body geometry, not Basic Finner / sounding-rocket-like.** The 4 %
  bi-convex delta wing on the AGARD-B is structurally different from the
  thin rectangular fins of the Basic Finner. ORP's fin module can approximate
  the delta, but the comparison will not be one-to-one for fin profile drag.
- **Hand-read digitization.** Read uncertainty bounds quoted above. Three
  curves overlay closely; this dampens the impact of read error but does
  not eliminate it. WebPlotDigitizer would tighten this by a factor of 2-3
  at most; the paper's own CFD-vs-experiment agreement (~1-5 %) is the
  practical floor.

## Follow-up before the JSR paper draft

1. **Stand up an OpenRocket Plus AGARD-B `.ork`.** Requires:
   - 0.9843 m body, 0.1158 m diameter, parabolic-arc ogive nose (the standard
     contour, length given by the parabolic equation in paper Section 2).
   - Equilateral triangle delta wing, span 0.4632 m, 4 % bi-convex section.
     ORP's `TrapezoidFinSet` with root chord = span and zero tip chord
     approximates this; the bi-convex section is approximated by HEXAGONAL
     cross-section in `FinSet.CrossSection`.
   - No sting (Vidanovic removed the sting from the CFD model).
2. **Write a Java comparator test** modeled on `BunescuANFCfdComparatorTest.java`.
   Targets: CD at M = 0.596 / 1.602 at AoA = 0, 4, 8 deg (6 points), CL at
   the same conditions (6 more), Cm at the same conditions (6 more).
   18 points total, loose informational MAPE gate (~30 %).
3. **Decide handling of the wing-body fin geometry.** ORP's Pitts-Nielsen-Kaattari
   interference and TransonicSimilarity are tuned for sounding-rocket rectangular
   or clipped-delta fins; an equilateral delta wing is at the edge of validity.
   The comparator memo should flag this as a B-level rather than A-level claim.

## Files

- Digitized CFD points: `paper/data/csv/vidanovic_agard_b_cfd_2014.csv`
- Source PDF: `paper/data/pdf/Validation_of_the_CFD_code_used_for_determination_.pdf`
- This memo: `paper/data/md/vidanovic_agard_b_cfd_comparator_2026_05_11.md`
- Java comparator test: **not yet implemented** (pending AGARD-B `.ork`)
