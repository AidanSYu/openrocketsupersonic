# MESOS 293K - Closure Sheet

## Header

- Case: MESOS 293K Flight (two-stage O4374 booster + M787 sustainer), Black Rock Desert NV (launch altitude 3,910 ft, read from CDX1 `<LaunchSite><Altitude>`)
- Current error: **-0.6%** apogee vs real GPS, **+4.0%** max velocity, **+3.6%** peak Mach
- Status: **CLOSED** under the plan gates (`|apogee error| < 10%`, `|max velocity error| < 5%`)
- Regime: high-supersonic / low-hypersonic two-stage flight; real peak Mach 4.18, ORP peak Mach 4.33 (trajectory-derived, altitude-correct speed of sound)
- Source: `core/build/reports/simvreal-outliers/MESOS_293K_Flight.md` and `SimVRealValidationTest.testMesos293K`

## Result

| Metric | Real | RASAero II | ORP | ORP Error |
|---|---:|---:|---:|---:|
| Apogee | 293,488 ft | 289,789 ft | 291,601 ft | -0.6% |
| Max velocity | 4,047 ft/s | N/A | 4,210 ft/s | +4.0% |
| Peak Mach | 4.18 | 4.23 | 4.33 | +3.6% |

The validation test reports `RESULT: PASS (within 10%)`. The explicit velocity gate also passes at +4.0%.

## Mach reporting fix (2026-05-01)

A previous snapshot of this sheet reported "ORP max velocity Mach 3.74 in validation output; diagnostic path reports Mach 4.28." This was not a physics issue — it was a display bug in `SimVRealValidationTest.reportResult` and `testMesos293K` which computed display Mach as `peak_velocity_m_s / 343.0` (hardcoded sea-level speed of sound) instead of reading the trajectory-derived peak Mach from `data.getMaxMachNumber()`. The simulation itself was always tracking Mach correctly per timestep via `FlightConditions.setMach(velocity / atmosphericConditions.getMachSpeed())`, where `getMachSpeed()` uses `sqrt(gamma * R * T(altitude))`. The diagnostic and benchmark code paths (`SimVRealOutlierDiagnosticTest`, `SimVRealBenchmarkTest`) already used `data.getMaxMachNumber()` and so were always correct. The fix aligned the validation test's display path with the other two paths. The current trajectory-derived peak Mach is 4.33, consistent with peak velocity 4,210 ft/s occurring at high altitude where the speed of sound is approximately 972 m/s.

## Staging And Branches

The expected two-branch sequence is present:

- Booster motor ignition at t = 0.000 s.
- Booster burnout and stage separation at t = 7.941 s.
- Sustainer ignition at t = 23.103 s.
- Sustainer burnout at t = 33.692 s.
- Primary-branch apogee at t = 147.692 s.
- Primary-branch ground hit at t = 288.843 s.
- Booster branch apogee and ground hit are also produced normally.

Terminal note is `NORMAL`. There is one simulation warning for no recovery device, which is expected for the imported MESOS flight setup and does not indicate an abnormal branch ending.

## Import Parity Notes

Loader warnings:

- `ModifiedBarrowman=True` remains unsupported.
- `Turbulence=True` is honored by forcing fully turbulent skin friction.
- `SustainerNozzle=2.15` and `Booster1Nozzle=3.33` are surfaced as unsupported design-copy warnings, while stage nozzle diameters are available to the production pressure-thrust correction path.

The closure is not a case-specific aerodynamic multiplier. The accepted mechanisms are stage-aware nozzle pressure-thrust, turbulence parity, and geometry-gated high-M drag behavior that also passes the full SimVReal and external aero regression battery.

## Closure Definition

**Closed when ORP apogee error is under +/-10%, max velocity error is under +/-5%, branch count and staging events are correct, and the full SimVReal/external aero regression battery remains green.** This condition is now met.

## Residual Risk

MESOS remains a demanding validation case because it combines custom motors, high-altitude pressure thrust, staging, and Mach 3+ coast aerodynamics. Future changes to nozzle handling, stage activation, or high-M base/fin drag should rerun `testMesos293K` and inspect the branch timeline, not just the apogee number.
