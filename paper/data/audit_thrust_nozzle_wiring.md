# Audit: Production Thrust/Nozzle Wiring in Simulation Path

**Date**: 2026-04-16
**Auditor**: Claude Opus 4.6
**Status**: READ-ONLY AUDIT — no code changes made

---

## Executive Summary

**`populateThrustState()` is fully implemented but deliberately commented out.** The call at `RK4SimulationStepper.java:593` is disabled with an explicit TODO comment explaining the rationale. As a result, `FlightConditions.thrustLevel` is always 0 and `nozzleAreaRatio` is always NaN during production simulation. The power-on base drag reduction model (`computePowerOnBaseDragMultiplier`) therefore always returns 1.0 — no base drag reduction ever occurs during motor burn.

This is a **known, intentional gap**, not a bug.

---

## Detailed Findings

### 1. Current Behavior: thrustLevel and nozzleAreaRatio are NEVER populated

**Evidence chain:**

| Step | File | Line | What happens |
|------|------|------|-------------|
| FlightConditions created | `AbstractSimulationStepper.java` | 64 | `new FlightConditions(config)` — defaults: `thrustLevel=0`, `nozzleAreaRatio=NaN` |
| Flight conditions populated | `AbstractSimulationStepper.java` | 62-121 | Sets atmosphere, velocity, AoA, roll/pitch/yaw. Does NOT set thrustLevel or nozzleAreaRatio. |
| Forces calculated | `RK4SimulationStepper.java` | 579-614 | Calls `calculateFlightConditions(status, store)` then proceeds to aero calc |
| **populateThrustState COMMENTED OUT** | `RK4SimulationStepper.java` | 593 | `// populateThrustState(status, store);` |
| Base drag computed | `BarrowmanDragCalculator.java` | 782 | `computePowerOnBaseDragMultiplier(conditions)` — reads `thrustLevel` (always 0) |
| Multiplier result | `BarrowmanDragCalculator.java` | 1319 | `thrustLevel <= 0` is true, returns 1.0 (no reduction) |

**Consequence:** During powered flight, base drag is NOT reduced. The rocket experiences full base drag even while the motor is burning.

### 2. The Implementation Exists and Is Complete

The `populateThrustState()` method at line 623-648 is fully implemented:

```java
private void populateThrustState(SimulationStatus status, DataStore store) {
    double totalThrust = 0;
    for (MotorClusterState mcs : status.getActiveMotors()) {
        totalThrust += mcs.getThrust(status.getSimulationTime());
    }
    if (totalThrust > 0.1) {
        store.flightConditions.setThrustLevel(Math.min(1.0, totalThrust / 100.0));
        // nozzle area ratio from stored nozzle exit diameter...
        double nozzleDia = status.getSimulationConditions().getNozzleExitDiameter();
        // ...computes ratio = (nozzleRadius/bodyRadius)^2
    } else {
        store.flightConditions.setThrustLevel(0);
    }
}
```

The implementation:
- Sums thrust from all active motor clusters at current sim time
- Uses a soft saturation: `min(1.0, totalThrust/100.0)` — any thrust above 100 N gives thrustLevel ~1.0
- Reads nozzle exit diameter from `SimulationConditions` (propagated from `SimulationOptions`)
- Computes area ratio as `(nozzleRadius/bodyRadius)^2` using `refLength/2` as body radius
- At burnout, when `getActiveMotors()` returns spent motors with zero thrust, correctly sets thrustLevel=0

### 3. Why It Was Disabled

The comment at lines 590-593 states:

```
// TODO: Power-on base drag reduction is physically correct but currently makes
// the benchmark worse because coast drag is underestimated.  Enable after
// closing the high-M finned-body coast drag gap.
```

**Interpretation:** Enabling power-on base drag reduction during boost reduces boost drag (correct physics), but the overall benchmark worsens because coast-phase drag is already too low. The net effect is: reducing boost drag makes the sim overpredict apogee even more, widening the gap vs. real flights that are already overpredicted.

This is a classic **compensating-error** situation. The too-low coast drag and the missing power-on reduction partially cancel. Fixing one without the other makes things worse on net.

### 4. Data Flow for Nozzle Exit Diameter

| Source | Path | Coverage |
|--------|------|----------|
| RASAero CDX1 import | `SimulationHandler.java:180` → `sim.getOptions().setNozzleExitDiameter(sustainerNozzleDiameter)` | 17 of 24 SimVReal cases have nonzero values |
| SimulationOptions → SimulationConditions | `SimulationOptions.java:825` → `conditions.setNozzleExitDiameter(...)` | Always propagated |
| Native OpenRocket designs | No UI exists in `swing/` for setting nozzle diameter | **Always NaN** |
| Motor database | `ThrustCurveMotor` has `getDiameter()` (casing OD) but no nozzle exit diameter | Cannot derive |

**Key finding:** For native OpenRocket designs (not imported from RASAero), nozzle exit diameter is always NaN. The drag calculator handles this gracefully: when `nozzleAreaRatio` is NaN, it uses `DEFAULT_POWER_ON_FACTOR = 0.15` (85% base drag reduction). This is a reasonable default for HPR motors.

### 5. Other Steppers

| Stepper | Has populateThrustState? | Has any thrust wiring? |
|---------|-------------------------|----------------------|
| `RK4SimulationStepper` | Yes (commented out) | No active wiring |
| `RK6SimulationStepper` | No | No |
| `AbstractEulerStepper` | No | No |
| `GroundStepper` | No | No |

If this is enabled in RK4, it should also be added to RK6 for consistency. The Euler and Ground steppers are for launch rod and ground phases where base drag reduction is irrelevant.

### 6. Risks of Uncommenting the Line

**Risk 1 — Benchmark regression (HIGH, KNOWN):** The TODO comment explicitly warns that this makes benchmarks worse. Without first closing the coast drag gap, enabling this would:
- Reduce boost-phase drag by ~85% of base drag (for default factor)
- Base drag is ~15-25% of total supersonic drag
- Net total drag reduction during boost: ~13-21%
- This directly increases apogee overshoot on SimVReal cases

**Risk 2 — Soft saturation threshold (LOW):** The `totalThrust / 100.0` normalization means a 50 N motor gives thrustLevel=0.5 (partial reduction). For most HPR motors (500-5000+ N), this saturates to 1.0 immediately. For low-thrust motors (model rocket C/D class at 5-15 N), the reduction would be partial. This is probably wrong — even a small motor fills the base region with exhaust. A binary on/off at ~1 N threshold would be more physically correct.

**Risk 3 — Nozzle area ratio computation (LOW):** Uses `refLength/2` as body radius. `refLength` is the reference length from `FlightConfiguration`, which is the maximum body diameter. This is correct for single-body rockets but could be wrong for complex multi-body configurations.

**Risk 4 — Stale state after staging (LOW):** `populateThrustState` queries `status.getActiveMotors()`, which is managed by the simulation engine and updates on staging events. The thrust will correctly go to zero when a stage separates and its motors are no longer in the active motor list.

**Risk 5 — Two call sites need wiring (MEDIUM):** `calculateFlightConditions` is called at both line 108 (initial step) and line 588 (via `calculateForces`). The `populateThrustState` call is only after line 588. The line-108 call is the initial RK4 step setup. If thrust state is needed for the aero calc inside the RK4 substeps, it may need to be wired at both sites. However, the initial call at line 108 appears to be for data recording, not force computation, so this is likely fine.

### 7. What Tests Are Needed Before Enabling

1. **Unit test for populateThrustState**: Create a mock SimulationStatus with known active motors and verify thrustLevel and nozzleAreaRatio are set correctly on FlightConditions.

2. **Burnout transition test**: Verify that thrustLevel transitions cleanly from 1.0 to 0.0 at motor burnout. No lingering positive thrustLevel after burnout.

3. **Staging test**: Verify that after stage separation, thrustLevel from the separated stage's motors does not persist.

4. **SimVReal regression**: Run the full 36-case benchmark before and after, documenting the delta. Expect apogee overshoot to increase. This is acceptable ONLY if coast drag improvements are also applied to compensate.

5. **Component-level base drag verification**: Log base drag Cd during boost vs coast for a reference rocket. Verify boost-phase base drag is reduced by the expected factor and coast-phase base drag is unchanged.

---

## Recommendation

**Do NOT enable `populateThrustState` in isolation.** The TODO comment is correct: it is physically right but will worsen the benchmark until coast drag is fixed.

**Correct sequence:**
1. Close the coast drag gap first (Suspects #1-#4 in the roadmap diagnostic)
2. Then uncomment `populateThrustState`
3. Verify the combined effect on SimVReal
4. Also wire thrust state into RK6SimulationStepper for completeness

**For the AST paper:** This should be disclosed as a known limitation. The power-on base drag model is implemented and unit-tested (`PowerOnBaseDragTest.java` has 12 tests) but not wired into production simulation. The paper can state: "Power-on base drag reduction is modeled but disabled in the current validation because the coast-phase drag deficit creates a compensating-error coupling." This is honest and defensible.

---

## Files Examined

| File | Relevance |
|------|-----------|
| `core/.../simulation/RK4SimulationStepper.java` | Contains `populateThrustState()` (commented out at line 593) |
| `core/.../simulation/AbstractSimulationStepper.java` | `calculateFlightConditions()` — does not set thrust state |
| `core/.../simulation/RK6SimulationStepper.java` | No thrust wiring at all |
| `core/.../simulation/AbstractEulerStepper.java` | No thrust wiring (expected) |
| `core/.../aerodynamics/FlightConditions.java` | Defaults: `thrustLevel=0`, `nozzleAreaRatio=NaN` |
| `core/.../aerodynamics/BarrowmanDragCalculator.java` | `computePowerOnBaseDragMultiplier()` at line 1317, `calculateBaseCD()` at line 782 |
| `core/.../simulation/SimulationOptions.java` | `nozzleExitDiameter` stored, propagated to SimulationConditions |
| `core/.../simulation/SimulationConditions.java` | Receives nozzle diameter from SimulationOptions |
| `core/.../file/rasaero/importt/SimulationHandler.java` | Sets nozzle diameter from CDX1 import |
| `swing/` (entire module) | No UI for nozzle exit diameter — native designs always NaN |
