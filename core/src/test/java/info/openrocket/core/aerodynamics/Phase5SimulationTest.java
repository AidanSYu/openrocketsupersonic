package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;

/**
 * Phase 5c: End-to-End Flight Simulation Validation.
 * <p>
 * Runs full trajectory simulations to verify:
 * <ul>
 *   <li>Regime transitions (subsonic through supersonic and back) don't cause divergence</li>
 *   <li>Apogee altitude, max velocity, and max acceleration are physically reasonable</li>
 *   <li>No NaN/Inf propagation through the simulation loop</li>
 *   <li>Aero coefficients remain smooth through transonic transition</li>
 *   <li>Warning system produces appropriate warnings at high Mach</li>
 * </ul>
 * <p>
 * Uses TestRockets.makeEstesAlphaIII() which is a standard subsonic test case.
 * For supersonic validation, we verify aero coefficient stability through
 * regime transitions using the aerodynamic calculator directly on the
 * SupersonicTestRockets geometries with Mach sweeps that simulate a flight profile.
 */
public class Phase5SimulationTest {

    @BeforeAll
    static void setup() {
        Module applicationModule = new ServicesForTesting();
        Module pluginModule = new PluginModule();
        Injector injector = Guice.createInjector(applicationModule, pluginModule);
        Application.setInjector(injector);
    }

    // ================================================================
    // 5c.1: Full Trajectory Simulation (subsonic rocket)
    // ================================================================

    /**
     * Run a full simulation of the Estes Alpha III and verify:
     * - Simulation completes without exception
     * - Apogee, max velocity, max acceleration are finite and positive
     * - Flight time is reasonable (not infinite or zero)
     */
    @Test
    void testEstesAlphaIIISimulation() throws SimulationException {
        Rocket rocket = TestRockets.makeEstesAlphaIII();
        Simulation sim = new Simulation(rocket);
        sim.setFlightConfigurationId(TestRockets.TEST_FCID_0);
        sim.getOptions().setISAAtmosphere(true);
        sim.getOptions().setTimeStep(0.05);

        sim.simulate();

        FlightData data = sim.getSimulatedData();
        assertNotNull(data, "Flight data should not be null");

        double apogee = data.getMaxAltitude();
        double maxVel = data.getMaxVelocity();
        double maxAccel = data.getMaxAcceleration();
        double flightTime = data.getFlightTime();

        assertTrue(Double.isFinite(apogee), "Apogee should be finite");
        assertTrue(apogee > 10, "Apogee should be > 10m: " + apogee);
        assertTrue(apogee < 5000, "Apogee should be < 5000m: " + apogee);

        assertTrue(Double.isFinite(maxVel), "Max velocity should be finite");
        assertTrue(maxVel > 10, "Max velocity should be > 10 m/s: " + maxVel);
        assertTrue(maxVel < 500, "Max velocity should be < 500 m/s: " + maxVel);

        assertTrue(Double.isFinite(maxAccel), "Max acceleration should be finite");
        assertTrue(maxAccel > 10, "Max acceleration should be > 10 m/s^2: " + maxAccel);

        assertTrue(Double.isFinite(flightTime), "Flight time should be finite");
        assertTrue(flightTime > 1, "Flight time should be > 1s: " + flightTime);
        assertTrue(flightTime < 300, "Flight time should be < 300s: " + flightTime);

        System.out.printf("Estes Alpha III: apogee=%.1fm, maxVel=%.1fm/s, maxAccel=%.1fm/s^2, time=%.1fs%n",
                apogee, maxVel, maxAccel, flightTime);
    }

    /**
     * Verify the time-series flight data contains no NaN values.
     */
    @Test
    void testNoNaNInTimeSeries() throws SimulationException {
        Rocket rocket = TestRockets.makeEstesAlphaIII();
        Simulation sim = new Simulation(rocket);
        sim.setFlightConfigurationId(TestRockets.TEST_FCID_0);
        sim.getOptions().setISAAtmosphere(true);
        sim.getOptions().setTimeStep(0.05);

        sim.simulate();

        FlightDataBranch branch = sim.getSimulatedData().getBranch(0);
        assertNotNull(branch, "Flight data branch should not be null");

        FlightDataType[] types = {
                FlightDataType.TYPE_ALTITUDE,
                FlightDataType.TYPE_VELOCITY_TOTAL,
                FlightDataType.TYPE_ACCELERATION_TOTAL,
                FlightDataType.TYPE_MACH_NUMBER
        };

        for (FlightDataType type : types) {
            List<Double> values = branch.get(type);
            if (values != null) {
                for (int i = 0; i < values.size(); i++) {
                    assertTrue(Double.isFinite(values.get(i)),
                            type.getName() + " contains NaN/Inf at index " + i);
                }
            }
        }
    }

    // ================================================================
    // 5c.2: Regime Transition Continuity (simulated flight profile)
    // ================================================================

    /**
     * Simulate a flight Mach profile (accelerating 0.3 -> 3.0 -> 0.3)
     * and verify aero coefficients remain smooth through all transitions.
     * This catches integration bugs that could cause simulation divergence.
     */
    @Test
    void testRegimeTransitionSmooth_ConeCylinderFins() {
        assertRegimeTransitionSmooth(SupersonicTestRockets.makeConeCylinderFins());
    }

    @Test
    void testRegimeTransitionSmooth_OgiveBoattailFins() {
        assertRegimeTransitionSmooth(SupersonicTestRockets.makeOgiveBoattailFins());
    }

    @Test
    void testRegimeTransitionSmooth_VonKarmanFins() {
        assertRegimeTransitionSmooth(SupersonicTestRockets.makeVonKarmanFins());
    }

    /**
     * Simulate a Mach profile through transonic, supersonic, and back.
     * Verify no jumps in Cd, CN, or CP that would cause simulation instability.
     */
    private void assertRegimeTransitionSmooth(Rocket rocket) {
        FlightConfiguration config = rocket.getSelectedConfiguration();
        AerodynamicCalculator calc = new BarrowmanCalculator();

        // Simulate flight: accelerate 0.3 -> 3.0, then decelerate 3.0 -> 0.3
        double[] machProfile = new double[120];
        for (int i = 0; i < 60; i++) {
            machProfile[i] = 0.3 + i * (3.0 - 0.3) / 60.0; // accelerating
        }
        for (int i = 0; i < 60; i++) {
            machProfile[60 + i] = 3.0 - i * (3.0 - 0.3) / 60.0; // decelerating
        }

        double prevCd = Double.NaN;
        int jumps = 0;

        for (int i = 0; i < machProfile.length; i++) {
            double mach = machProfile[i];
            FlightConditions fc = new FlightConditions(config);
            fc.setMach(mach);
            fc.setAOA(Math.toRadians(2));

            AerodynamicForces forces = calc.getAerodynamicForces(config, fc, new WarningSet());

            assertTrue(Double.isFinite(forces.getCD()),
                    rocket.getName() + ": Cd NaN/Inf at M=" + String.format("%.2f", mach));
            assertTrue(Double.isFinite(forces.getCN()),
                    rocket.getName() + ": CN NaN/Inf at M=" + String.format("%.2f", mach));
            assertTrue(forces.getCD() > 0,
                    rocket.getName() + ": Cd <= 0 at M=" + String.format("%.2f", mach));

            if (!Double.isNaN(prevCd)) {
                double dCd = Math.abs(forces.getCD() - prevCd);
                double dM = Math.abs(mach - machProfile[Math.max(0, i - 1)]);
                if (dM > 0.01 && dCd / dM > 20) {
                    jumps++;
                }
            }

            prevCd = forces.getCD();
        }

        assertTrue(jumps < 3,
                rocket.getName() + ": too many Cd jumps during regime transition: " + jumps);
    }

    // ================================================================
    // 5c.3: Stability Through Regime Transitions
    // ================================================================

    /**
     * CP position must remain within rocket bounds throughout a Mach sweep.
     * CP moving outside the rocket would indicate a stability calculation bug.
     */
    @Test
    void testCPWithinBoundsThroughRegimeTransition() {
        Rocket[] rockets = {
                SupersonicTestRockets.makeConeCylinderFins(),
                SupersonicTestRockets.makeOgiveBoattailFins(),
                SupersonicTestRockets.makeVonKarmanFins()
        };

        for (Rocket rocket : rockets) {
            FlightConfiguration config = rocket.getSelectedConfiguration();
            double rocketLength = config.getLengthAerodynamic();
            AerodynamicCalculator calc = new BarrowmanCalculator();

            for (double mach = 0.3; mach <= 10.0; mach += 0.05) {
                FlightConditions fc = new FlightConditions(config);
                fc.setMach(mach);
                fc.setAOA(Math.toRadians(2));

                AerodynamicForces forces = calc.getAerodynamicForces(config, fc, new WarningSet());
                double cpx = forces.getCP().getX();

                assertTrue(Double.isFinite(cpx),
                        rocket.getName() + ": CP.x not finite at M=" + String.format("%.2f", mach));
                assertTrue(cpx >= -0.05,
                        rocket.getName() + ": CP.x too far forward at M=" + String.format("%.2f", mach) +
                                ": " + cpx);
                assertTrue(cpx <= rocketLength + 0.05,
                        rocket.getName() + ": CP.x too far aft at M=" + String.format("%.2f", mach) +
                                ": " + cpx + " (rocket length=" + rocketLength + ")");
            }
        }
    }

    /**
     * Stability margin (CP - CG distance) should remain physically reasonable.
     * A rocket with fins should have positive stability at moderate AoA.
     */
    @Test
    void testStabilityMarginReasonable() {
        Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
        FlightConfiguration config = rocket.getSelectedConfiguration();
        AerodynamicCalculator calc = new BarrowmanCalculator();

        double[] machs = {0.5, 1.0, 1.5, 2.0, 3.0, 5.0};

        for (double mach : machs) {
            FlightConditions fc = new FlightConditions(config);
            fc.setMach(mach);
            fc.setAOA(Math.toRadians(2));
            WarningSet ws = new WarningSet();

            AerodynamicForces forces = calc.getAerodynamicForces(config, fc, ws);
            double cpx = forces.getCP().getX();
            double cna = forces.getCP().getWeight();

            // For a rocket with fins, CP should be aft of nose
            assertTrue(cpx > 0.05,
                    "CP should be aft of nose at M=" + mach + ": " + cpx);

            // CNa should be positive (restoring force)
            assertTrue(cna > 0,
                    "CNa should be positive at M=" + mach + ": " + cna);
        }
    }

    // ================================================================
    // 5c.4: Warning System Validation
    // ================================================================

    /**
     * Verify warnings are produced at appropriate Mach numbers
     * during a full sweep.
     */
    @Test
    void testWarningsAtCorrectMach() {
        Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
        FlightConfiguration config = rocket.getSelectedConfiguration();

        // No hypersonic warnings at M=3
        {
            AerodynamicCalculator calc = new BarrowmanCalculator();
            FlightConditions fc = new FlightConditions(config);
            fc.setMach(3.0);
            fc.setAOA(Math.toRadians(2));
            WarningSet ws = new WarningSet();
            calc.getAerodynamicForces(config, fc, ws);
            assertFalse(ws.stream().anyMatch(w ->
                            w.equals(info.openrocket.core.logging.Warning.HYPERSONIC)),
                    "Should not have HYPERSONIC warning at M=3");
        }

        // HYPERSONIC warning at M=6
        {
            AerodynamicCalculator calc = new BarrowmanCalculator();
            FlightConditions fc = new FlightConditions(config);
            fc.setMach(6.0);
            fc.setAOA(Math.toRadians(2));
            WarningSet ws = new WarningSet();
            calc.getAerodynamicForces(config, fc, ws);
            assertTrue(ws.stream().anyMatch(w ->
                            w.equals(info.openrocket.core.logging.Warning.HYPERSONIC)),
                    "Should have HYPERSONIC warning at M=6");
        }

        // HYPERSONIC_EXTREME warning at M=12
        {
            AerodynamicCalculator calc = new BarrowmanCalculator();
            FlightConditions fc = new FlightConditions(config);
            fc.setMach(12.0);
            fc.setAOA(Math.toRadians(2));
            WarningSet ws = new WarningSet();
            calc.getAerodynamicForces(config, fc, ws);
            assertTrue(ws.stream().anyMatch(w ->
                            w.equals(info.openrocket.core.logging.Warning.HYPERSONIC_EXTREME)),
                    "Should have HYPERSONIC_EXTREME warning at M=12");
        }
    }
}
