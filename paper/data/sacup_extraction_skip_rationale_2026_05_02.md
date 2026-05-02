# SACup PDF Extraction — Skip Rationale (2026-05-02)

Per the v2.0 vehicle-parameter expansion task: read the four SACup PDFs in `paper/data/pdf/New/` (the originals were NOT promoted to `paper/data/pdf/` per `pdf_copied_2026_05_02.md`), extract YAML for any report containing all four required pieces:

1. Recorded **actual** apogee (radar / GPS / barometric measurement, post-flight)
2. Vehicle geometry (body diameter, length, fin geometry, nose shape)
3. Motor designation + manufacturer (must be a COTS motor present in the OpenRocket motor DB)
4. Launch site altitude / elevation

**Outcome: 0 of 4 reports qualify.** All four are pre-flight design / progress submissions per the SACup ESRA Project Technical Report deliverable schedule (submitted with the Entry Form & Progress Update before the actual launch), so they document predicted/simulated apogees only. No vehicle YAML files were created. Per CLAUDE.md citation hygiene policy ("Never cite ... data from training knowledge alone ... Never fabricate"), missing apogees were NOT inferred from external sources or training memory.

ESRA archive harvesting (where post-flight altimeter logs may be paired with these reports) is the deferred follow-up task noted in the original instructions.

---

## Per-report verdicts

### Princeton Prometheus 2024 — `New/2024-SACup-ProjectTechnicalReport.pdf`

| Required item | Found? | Source |
|---|---|---|
| Actual apogee | NO | p.4 Fig. 2 CONOPS lists "Apogee & Drogue Deployment ... Altitude: 29,700 ft" — this is from the simulated trajectory shown alongside other simulated states (Liftoff 0.04 s, Boost Cutoff 4 s/4855 ft/1903 ft·s⁻¹), not from a recovered altimeter or GPS log. No section reports flight outcome. |
| Vehicle geometry | PARTIAL | p.1 abstract: 5.5" dia, 12'11" (155") long, 78 lb wet; p.3 §III.C: 24" Madcow VK nose, 36" upper + 18" middle + 70" lower G12 body, 5.5"->4" boattail 5.5" long; "Four trapezoidal fins" — fin root/tip/sweep dimensions NOT given (p.3 only describes ply-up; numeric dimensions are in cited "figure 13 Appendix" not in the PDF I have). |
| Motor | YES | p.3 §III.B: AeroTech O5500X, 129.88 N·s specific impulse, 4.0 s burn (note: O5500X is in OpenRocket motor DB). p.2 mentions original order was Cesaroni O3400 — switched to O5500X late. |
| Launch site altitude | NO | "Spaceport America Cup" / "30,000 feet" target stated; pad elevation never given numerically in the report. |

**Skip reason:** No recovered apogee. Report was submitted as pre-flight design documentation; the 29,700 ft figure is the OpenRocket-simulated apogee (consistent with their target of "about 30,000 feet" stated on p.1).

### SunrIDe Amy 2018 — `New/40_project_report.pdf` (University of Sheffield)

| Required item | Found? | Source |
|---|---|---|
| Actual apogee | NO | p.4: "the parachute deployment will occur at apogee (about 10047 ft)" — this is the OpenRocket-predicted apogee. p.8 Fig. 6 is captioned "Mission simulation" (OpenRocket Vertical motion vs time plot, peak ~3100 m). p.9 Appendix A is captioned "Table 2. Simulation results". No measured/post-flight value anywhere. |
| Vehicle geometry | YES (mostly) | p.2 Fig. 1: total height 107.5", nose 24.016", 1st body tube 32.874", 2nd body tube 6.299", 3rd body tube 41.26"; fin span 21.968"; p.9 Table 2: fuselage ID 6", OD 6.22", fin thickness 0.4", 4 fins, ogive nose. Fin chord/sweep not separately tabulated (only "clipped delta" qualitative). |
| Motor | INCONSISTENT | p.1 abstract + p.8 §III.2 say "Cesaroni Pro98 M3400" (M3400-P, 1.36 s burn, 9994.5 N·s impulse per Table 2 — matches Cesaroni 9994M3400-P database entry); but p.3 Fig. 2 labels the thrust curve as "Cesaroni 8634-M6400-VM-P" / "Pro98-4G" with 8634.4 N·s impulse and 6365 N average — this is a DIFFERENT motor (M6400-VM-P) plotted in the figure. Two motors named in the same report; not unambiguous which the as-built rocket used. |
| Launch site altitude | NO | Spaceport America Cup implied; numeric pad elevation not stated. p.6 main parachute deploys "at 1250 ft MSL" — that's MSL altitude of the deployment point, not the pad. |

**Skip reason:** No recovered apogee, motor designation ambiguous between two listed Cesaroni motors. Even if the M3400-P (the OpenRocket DB entry) is the true motor, the missing actual apogee is dispositive.

### UIC 2018 — `New/65_project_report.pdf` (University of Illinois at Chicago)

| Required item | Found? | Source |
|---|---|---|
| Actual apogee | NO | p.7 Fig. 8: "Max altitude: 25,500 ft. AGL" — this is the SRAD motor simulation output (SRAD N1745, 14,000 N·s, computed in their motor sim tool); p.3 says "expected apogee is 25,000 feet". No flight/recovery section. p.10 §Revisions describes prior test launches (L3150 in November, N2000 at Argonia) but does not give measured apogees for the SACup full-altitude test. |
| Vehicle geometry | YES | p.3 §II.A: 11.25 ft (135") tall, 4" diameter, 3-fin MaxQ aluminum fin can; p.9 Fig. 9 detailed: 23" VK nose 5.5:1, 30" upper body, 60" booster body — all 4.024" OD G12. |
| Motor | NO (not COTS) | The 30K SRAD division flies a Student-Researched-And-Developed motor. p.3 describes the SRAD N1745 (APHTBP 85,15 propellant, 6 BATES grains, mixed/cast by team). Per task instructions, only COTS motors in the OpenRocket DB qualify. SRAD motors require a custom thrust-curve which would itself need verification — and the report's own caveat (p.3) "we have not had a chance to test this motor yet" means even the simulated curve is unverified. |
| Launch site altitude | NO | Not stated. |

**Skip reason:** SRAD (custom) motor, not COTS — fails the task's "different from Super Loki / Arcas" rule that the motor must be in the OpenRocket DB. Also no recovered apogee.

### ARIS TELL 2018 — `New/100_Project-Report.pdf` (ETH Zurich + HSLU)

| Required item | Found? | Source |
|---|---|---|
| Actual apogee | NO | p.23 (Appendix C, ESRA Entry Form & Progress Update v18.1, submitted 25.05.2018): "Predicted Apogee (feet AGL): 11242.65" with comment "RockSim + MatLab Simulation - Use of Air Brakes". Date stamped before SACup 2018 launch. p.25 "Planned Tests" table shows "Final Rocket Test Flight 4.28.18 ... Major Issues ... Test launch cancelled due to delivery delay" — they did not even test-fly before submitting this report, let alone fly at SACup. |
| Vehicle geometry | YES | p.3 Table 1: 150 mm OD, 2419 mm length, 18.65 kg dry; p.4 Fig. 3: lower body 675 mm, upper body 786 mm, nose 458 mm + 500 mm tip; p.10 §H: 3 SRAD fins (CFRP+Al backbone), boat-tail. |
| Motor | YES | p.6 §C + Table 2: AeroTech M2400 (98 mm × 597 mm, 7716.5 N·s total impulse, 2400 N average, 3.2 s burn, 6451 g total mass). M2400 is in the OpenRocket motor DB. |
| Launch site altitude | NO | Spaceport America implied; numeric pad elevation not stated. |
| Active aerodynamic surfaces | YES (CONFOUND) | p.17 §N: 3 air brakes deployed at burnout to bleed energy and hit target apogee. Per `pdf_new_inventory_2026_05_02.md`: "air-brake control confounds clean aero comparison" — even with a recovered apogee, this rocket would not be a clean aero benchmark unless the brake duty cycle were also recovered from telemetry. |

**Skip reason:** No recovered apogee (predicted only); also active air-brake control would confound the aero comparison even with a recovered apogee.

---

## Net result

- 4 PDFs reviewed
- 4 vehicle YAMLs created: 0
- 0 motor inline curves needed (all 4 reports either skipped before motor stage, or motor is COTS)
- 0 commits to `paper/data/ork/sounding_rockets/vehicles/`

If the user wants any of these vehicles in the v2.0 corpus, the path forward is:

1. ESRA archive harvesting — locate the post-flight altimeter logs (`soundingrocket.org` flight database publishes recovered altitudes for many SACup teams). The 4 reports here would then become the design-side citation; the ESRA flight record would supply `apogee_real_ft`.
2. Direct contact with each team for the recovered Stratologger / Telemega CSV.
3. Promotion of these reports out of `New/` into `paper/data/pdf/` only after step 1 or 2 supplies the missing apogee.

This task is bounded to "process the 4 PDFs already in repo" (per user instructions: "Do NOT pursue web expansion in this task"), so the corpus-side action terminates here without YAMLs.
