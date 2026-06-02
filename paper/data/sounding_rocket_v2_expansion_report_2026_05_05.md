# Sounding Rocket V2 Expansion Report - 2026-05-05

## Scope

This pass drops Super Loki and Viper dart flights from the active v2 validation path. They remain useful data, but they are a separate spin-launched dart problem: helical launcher exit spin, dart/booster separation, dart aero, and spin-coupled coast are not represented well enough in the current OpenRocket Plus setup.

I built replacement non-helical candidate models for:

- Terrier-Improved Orion
- Terrier-Improved Malemute
- Black Brant IX
- Aerobee 150A
- Nike-Apache, already built in the prior pass, remains active

New source PDFs added locally:

- `paper/data/pdf/NASA_TP_20230006855_Sounding_Rockets_User_Handbook.pdf`
- `paper/data/pdf/NASA_TR_R_226_Aerobee_Compendium_1959_1963.pdf`

## New Artifacts

Generated ORKs:

- `paper/data/ork/sounding_rockets/terrier_improved_orion.ork`
- `paper/data/ork/sounding_rockets/terrier_improved_malemute.ork`
- `paper/data/ork/sounding_rockets/black_brant_ix_aspire_sr02.ork`
- `paper/data/ork/sounding_rockets/aerobee_150a_4_65gi.ork`

Source dossiers:

- `paper/data/ork/sounding_rockets/vehicles/terrier_improved_orion_screening.yaml`
- `paper/data/ork/sounding_rockets/vehicles/terrier_improved_malemute_screening.yaml`
- `paper/data/ork/sounding_rockets/vehicles/black_brant_ix_aspire_screening.yaml`
- `paper/data/ork/sounding_rockets/vehicles/aerobee_150a_screening.yaml`

Simulation CSV:

- `paper/data/csv/v2_replacement_screening_runs_2026_05_05.csv`

## Source Data Pulled

NASA/TP-20230006855 gives strong screening data for the modern solid stacks:

- Improved Orion: 14 in diameter, 105 in long, 943 lb, dual-phase thrust around 20,000 lbf for 6 s then 4,000 lbf to 24 s.
- Terrier-Improved Orion: Terrier MK12/MK70 first stage, 18 in diameter, 169 in long, four 4.8 sq ft fins, no-payload stack about 2,850 lb with MK12 and 3,150 lb with MK70.
- Improved Malemute: 16 in diameter, 130 in long, 11.7 s burn.
- Terrier-Improved Malemute: no-payload stack about 3,315 lb with MK12 and 3,615 lb with MK70.
- Black Brant IX: Terrier MK12/MK70 first stage, Black Brant upper 17.26 in diameter, 223 in long, 2,827 lb loaded, 2,223 lb propellant, 23,317 lbf average thrust, 27.5 s action time.

NASA flight pages give specific modern flight targets:

- Terrier-Improved Orion RockOn/RockSat-C, Wallops, 2016-06-24: 74 mi payload altitude.
- Terrier-Improved Malemute RockSat-X, Wallops, 2016-08-17: 95 mi payload altitude.

NTRS 20190028247 gives the cleanest new reconstructed target:

- ASPIRE SR02, Black Brant IX MOD2, 2018-03-31: apogee 54.82 km at T+123.49 s; 2nd-stage burnout T+34.10 s; payload separation T+103.99 s.

NASA TR R-226 gives Aerobee 150A data:

- Aerobee 150A: about 30 ft long, 15 in diameter.
- Booster: 2.5KS-18000, 18,600 lbf, 2.5 s, 520 lb loaded.
- Liquid sustainer: ANFA/IRFNA, 4,100 lbf, 51.5 s, 208,690 lbf-s.
- Flight 4.65GI: Wallops, 1963-09-25, 177.5 lb payload, 139.5 statute mi apogee, T+256 s apogee, T+54.6 s sustainer burnout.

## Replacement Screening Results

Command:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path '.tmp\gradle-home').Path
.\gradlew.bat core:test --tests info.openrocket.core.aerodynamics.SoundingRocketCorpusV2Test.testReplacementScreeningVehicles --no-daemon
```

Result: passed. Java preferences warnings are sandbox noise.

| Vehicle | Source Apogee ft | ORP Apogee ft | Error | Status |
|---|---:|---:|---:|---|
| Terrier-Improved Orion MK12 | 390,720 | - | - | Diverged at T+7.26 s after reaching 13,405 ft / Mach 3.22. |
| Terrier-Improved Malemute MK12 | 501,600 | - | - | Diverged at T+7.29 s after reaching 11,528 ft / Mach 2.68. |
| Black Brant IX MOD2 ASPIRE SR02 | 179,856 | 211,586 | +17.64% | Best new candidate; apogee captured before descent/integrator failure. |
| Aerobee 150A NASA 4.65GI | 736,560 | - | - | Diverged at T+41.99 s after reaching 6,437 ft; liquid/tower model not adequate yet. |

## Interpretation

Black Brant IX is the best replacement for the dropped dart cases. The model is still screening-grade because Terrier thrust and detailed fin/mass distribution are synthesized, but it reaches apogee and lands within 17.6% against a modern reconstructed ASPIRE flight. That is good enough to justify deeper modeling.

Terrier-Improved Orion and Terrier-Improved Malemute are still good corpus candidates, but the first-pass ORKs are not validation-ready. The early divergence points to missing stack details: exact Terrier thrust/mass history, tailcan/interstage geometry, flight payload CG, upper-stage fin planforms, and spin dynamics. These are not helical-launcher problems, but they are still roll-stabilized sounding rocket problems.

Aerobee 150A has excellent historical data, but it should be treated as its own modeling project. The current RSE approximation forces a pressure-fed liquid sustainer into a solid-motor abstraction and does not model tower guidance, tank depletion CG, or liquid propulsion transients. The source is rich enough to build this properly later.

Nike-Apache remains the best source-faithful deep-data historical model, but its +31% apogee overprediction still indicates aero/dynamics mismatch rather than staging/mass construction failure.

Black Brant V VB remains the best current 100 km+ high-altitude result at -6.97%, but its thrust curve and fin geometry are still partly synthesized.

## Recommendation

Active validation set now:

- Black Brant V VB: keep as the best current high-altitude benchmark.
- Black Brant IX ASPIRE SR02: promote as the next modern high-altitude benchmark to refine.
- Nike-Apache: keep for staging/mass/thrust verification and aero-model failure analysis.
- Arcas: keep as historical low/mid-altitude checks.

Deferred:

- Super Loki and Viper: spin-launched dart study only.
- Terrier-Improved Orion and Terrier-Improved Malemute: keep as candidates, but do not judge ORP accuracy until exact Terrier/upper-stage thrust, payload mass/CG, and fin/tailcan geometry are sourced.
- Aerobee 150A: keep as a high-value liquid/tower-launch project, not a quick solid-stack ORK.
