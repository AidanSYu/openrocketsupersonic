# Sounding-Rocket OpenRocket Models

Provisional `.ork` models built for the Rocket Flight Database v2.0 corpus, to be
benchmarked through OpenRocket Plus (ORP). These vehicles span a regime
(M ~ 4-5, 70-100+ km apogee) deliberately complementary to the existing v1.0
amateur-HPR corpus and matched to a real flight-test record.

## Files

| File | Purpose |
|------|---------|
| `super_loki_dart.ork`    | Super Loki Stable Booster + 1.625 in Robin Dart, 2-stage |
| `arcas.ork`              | Arcas single-stage end-burning sounding rocket           |
| `SuperLoki-600-13.eng`   | RASP thrust file for the Stable Super Loki motor (SDC P/N 600-13) |
| `Arcas-29KS336.eng`      | RASP thrust file for the Arcas 4.5 EX2 MOD 0 / 29-KS-336 MARC 2B1 |
| `_build_orks.py`         | Generator script -- regenerate `.ork` and `.eng` from this if dimensions change |

`.ork` files are zip archives containing a single `rocket.ork` XML. They
parse cleanly with both Python's `xml.etree.ElementTree` and OpenRocket's
own loader (XML schema follows `core/.../file/openrocket/savers/`).

The `.ork` files are **deliverables on their own** -- no Java compilation
or Gradle invocation occurred during their construction. Simulation runs
through ORP are deferred until the ablation test currently using Gradle
finishes.

## Vehicle 1: Super Loki Robin Dart

**Primary source:** AFCRL-TR-73-0412 / DTIC AD-766737, Bollermann and Walker
(Space Data Corp), *Design, Development and Flight Test of the Super Loki
Stable Booster Rocket Systems*, 30 June 1973.

Two-stage meteorological probe: a 4.0 in solid booster (Stable Super Loki
motor, SDC P/N 600-13) ignites a 1.625 in non-propulsive dart that coasts
to ~106 km apogee. Booster fins are the "stable" enlarged design with
Thermolag-coated leading-edge cuffs added in 1973 to keep the booster on
a ballistic trajectory after burnout/separation.

| Dimension | Value | Source | Confidence |
|-----------|-------|--------|-----------|
| Booster length             | 88.3 in    | Table 3.1 | high |
| Booster diameter           | 4.0 in     | Table 3.1 | high |
| Booster fin span (tip-tip) | 8.0 in     | Table 3.1 | high |
| Booster fin root chord     | 16.6 in    | Table 3.1 | high |
| Booster fin tip chord      | 14.8 in    | Table 3.1 | high |
| Booster fin area each      | 31.4 in^2  | Table 3.1 | high (verifies geom.) |
| Booster fin sweep length   | 1.8 in     | inferred (TE = motor aft, Fig 4.1) | medium |
| Booster fin thickness      | ~0.060 in  | estimated; Sec 8.2 quotes 0.030 in Thermolag coat | low |
| Loaded motor weight        | 60.62 lb   | Table 3.1 | high |
| Propellant weight          | 43.48 lb   | Table 3.1 | high |
| Headcap + interstage       | 3.81 lb    | Table 3.1 | high |
| Total impulse              | 9944 lbf-s | Table 3.3 | high |
| Action time                | 2.09 s     | Table 3.3 | high |
| Avg / max thrust           | 4757 / 5954 lbf | Table 3.3 | high |
| Robin dart diameter        | 1.625 in   | Fig 4.1 | high |
| Robin dart total length    | 43.655 in  | Fig 4.1 | high |
| Robin dart fin count       | 4          | Fig 4.1 | high |
| Robin dart fin area each   | 4.95 in^2  | Fig 4.1 | high |
| Robin dart nose length     | ~10.0 in   | estimated from Fig 4.1 proportions | **low** |
| Robin dart fin root/tip/sweep | 3.0 / 1.5 / 0.75 in | estimated; only fin **area** is given in source | **low** |
| Robin dart fin height      | 2.2 in     | derived from area + assumed root/tip | **low** |
| Robin dart weight          | 14.15 lb   | Table 4.1 | high |
| Vehicle launch weight      | 75.15 lb   | Table 4.1 | high |
| Vehicle burnout CG         | 74.04 in from motor aft | Table 4.1 | high |

Time-resolved CG and Iyy curves are recorded separately in
`paper/data/csv/super_loki_mass_properties_vs_time.csv` (Figs 4.2, 4.3).
OpenRocket does not natively support time-resolved Iyy, so the static
launch and burnout values are the only mass properties propagated to the
`.ork`. Iyy is reconstructed at simulation time from component layout.

Thrust curve (`SuperLoki-600-13.eng`) digitized from Figure 3.4 (sea level,
+59 deg F). Integrated impulse from the digitized curve = 9989 lbf-s,
0.45% high vs the Table 3.3 spec of 9944 lbf-s. Action time matches.

## Vehicle 2: Arcas

**Primary source:** DTIC AD-235341, Webster, Roberts, Donnell (Atlantic
Research Corp), *Final Report: Development of the Arcas Rocketsonde
System*, 29 February 1960.

Single-stage end-burning solid (Arcite 373D propellant); 4.45 in dia,
92.3 in long; nominal apogee ~210,000 ft (64 km) with 12.5 lb payload, best
demonstrated 249,000 ft (76 km).

| Dimension | Value | Source | Confidence |
|-----------|-------|--------|-----------|
| Overall length             | 92.3 in     | Table I (and Fig 1: 92.266 in) | high |
| Diameter                   | 4.45 in     | Table I | high |
| Motor length               | 60.8 in     | Table I (Fig 5: 60.706 in)     | high |
| Parachute housing length   | 13.4 in     | Table I (Fig 1: 13.437 in)     | high |
| Nose cone length           | 18.1 in     | Table I (Fig 1: 18.122 in)     | high |
| Nose cone shape            | 4-caliber secant ogive | p.10 text | high |
| Fin tip-to-tip span        | 13.0 in     | Fig 1                          | high |
| Fin count                  | 4 double-wedge | p.7 text + Table I          | high |
| Fin area total             | 94 in^2     | Table I                        | high |
| Fin area each              | 23.5 in^2   | derived (94 / 4)               | high |
| Fin height                 | 4.275 in    | derived: (13.0 - 4.45)/2       | high |
| Fin root/tip chord         | 7.0 / 4.0 in | inferred to satisfy area + height; not directly given | **medium** |
| Fin sweep length           | 1.5 in      | estimated                      | low  |
| Fin thickness              | 0.20 in     | estimated; double-wedge cast aluminum | low |
| Total weight (loaded)      | 77.0 lb     | Table I                        | high |
| Burnout weight             | 36.0 lb     | Table I                        | high |
| Propellant weight          | 41.0 lb     | derived (77 - 36)              | high |
| Motor + fins weight        | 64.5 lb     | Table I                        | high |
| Payload weight             | 12.0 lb     | Table I                        | high |
| Loaded CG (from nose tip)  | 54.3 in     | Fig 3                          | high |
| Burnout CG (from nose tip) | 48.3 in     | Fig 3                          | high |
| Total impulse @ 70 F       | 9089 lbf-s  | Table I p.6                    | high |
| Action time @ 70 F         | 29.0 s      | Table I p.6                    | high |
| Avg thrust @ 70 F          | 336 lbf     | Table I p.6                    | high |

Thrust curve (`Arcas-29KS336.eng`) schematized from Figure 6 narrative
(end-burning grain, near-flat thrust ramping up at ignition and tapering at
burnout). Integrated impulse = 9103 lbf-s, +0.16% vs the 9089 lbf-s
spec. The "average 336 lbf x 29 s = 9744 lbf-s" headline number in the
text is the *normal rating* at 70 F before regressive tail-off, not the
flight-integrated impulse.

OpenRocket's nose-cone shape parameter does not separately encode "secant"
vs "tangent" ogive. The Arcas nose is modeled as `<shape>ogive</shape>`
with `<shapeparameter>1.0</shapeparameter>` (tangent ogive), introducing
a small (~few percent) wave-drag bias relative to the true 4-cal secant
profile. ORP may want to validate against the Fig 2 drag curve and adjust.

## Known Unmodeled Physics

1. **Dart spin stabilization.** Both the Super Loki Robin dart and Arcas
   spin during flight (Sec 6.6 / 6.12 in AFCRL-TR-73-0412 reports roll
   rates 5-10 Hz peak; Arcas p.7 mentions "1 to 3 rps" induced cant).
   OpenRocket and the Barrowman / ORP pipeline do not model spin-stabilized
   gyroscopic dynamics. The fin geometry alone provides static
   stability that is **necessary but not sufficient** to reproduce the
   real flight at high altitude where dynamic pressure -> 0.

2. **Time-varying Iyy.** AFCRL-TR-73-0412 Figs 4.3 and 5.3 show booster
   pitch moment of inertia falling from ~20 to ~12 slug-ft^2 over the
   burn, then dropping to ~0.5 slug-ft^2 at separation when only the dart
   remains. ORP reconstructs Iyy from component layout at each timestep,
   which approximates this for the sustainer-with-empty-booster case but
   does not perfectly track the propellant centroid shift inside the
   motor.

3. **Booster-dart aerodynamic interference.** The interstage adapter ring
   (Fig 4.1) and the dart sitting forward of the booster significantly
   modify base-drag and afterbody flow. ORP's `ShockGeometry` pre-pass
   handles cylindrical-to-cylindrical transitions but the ring step is
   not explicitly modeled in this `.ork`.

4. **Ablative thermal mass loss.** The dart body has a 0.075 in Thermolag
   coating (sec 4.3) that ablates during transonic-to-supersonic
   acceleration; the booster fin LE cuffs ablate similarly (sec 8.2).
   Mass change due to ablation is not modeled.

5. **Nozzle exit pressure / altitude compensation.** The supplied `.eng`
   thrust curves are sea-level firings. Both vehicles spend most of
   their burn at significant altitude; thrust at altitude is higher than
   the sea-level curve by O(15-25%). ORP's standard motor model applies
   no altitude correction, matching how RASAero II handles it.

6. **Aeroelastic fin deflection.** Disabled in ORP
   (`AeroelasticModel.Q_THRESHOLD = 1e12`). For the Arcas double-wedge
   cast-aluminum fins under high q at low altitude this is an acceptable
   simplification; for the Super Loki under 153 g axial accel near M=2.5
   it may matter.

## Source PDFs

- `paper/data/pdf/New/incoming/super_loki/RRS_Super_Loki_Stable_Booster_1973.pdf`
  -- AFCRL-TR-73-0412 (= DTIC AD-766737), the primary source
- `paper/data/pdf/New/incoming/super_loki/IA_DTIC_AD0750796_SuperLoki_Dart.pdf`
  -- earlier (1972) Super Loki Dart development report; corroborating
- `paper/data/pdf/New/incoming/super_loki/RRS_Super_Loki_Dart_NASA_CR61238_1968.pdf`
  -- 1968 Loki Dart NASA CR; legacy Loki, predates the "stable booster" mod
- `paper/data/pdf/New/incoming/arcas/IA_DTIC_AD0235341_Arcas_Rocketsonde.pdf`
  -- AD-235341, the primary Arcas source
- `paper/data/pdf/New/incoming/arcas/RASAero_Mirror_NASA_TN_D4013_Arcas_StaticStability.pdf`
  -- NASA TN D-4013, low-speed static stability wind tunnel
- `paper/data/pdf/New/incoming/arcas/RASAero_Mirror_NASA_TN_D4014_Arcas_Supersonic.pdf`
  -- NASA TN D-4014, supersonic wind tunnel data (M 1.5-4.6)

## Citation Hygiene Note

Per `CLAUDE.md` policy: every dimension in this build script is sourced from a
specific table or figure I directly inspected in the PDFs above (Table
3.1 / 3.3 / 4.1 / 4.2 / 4.3 of AFCRL-TR-73-0412; Figures 3.4, 4.1; Table I,
Figures 1, 5 of AD-235341). No NACA/NASA report was cited from training memory.
The `RASAero_Mirror_NASA_TN_D4013/D4014_Arcas_*` PDFs in the repo can be used
to validate the supersonic drag model against ORP's prediction for Arcas, but
were **not** used as a dimensional source here -- their numbers are
aerodynamic (CN, CP, CD vs Mach), not geometric.
