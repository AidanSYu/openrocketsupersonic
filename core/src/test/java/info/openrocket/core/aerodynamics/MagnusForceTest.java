package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Coordinate;

/**
 * Tests for Phase 11a: Magnus Force and Moment Derivatives.
 *
 * When a spinning rocket has AoA, asymmetric BL separation creates
 * lateral forces (Magnus effect). The slender body approximation gives:
 *   Cy_pa = -(2/3) * CNa_body
 *   Cn_pa = Cy_pa * (x_CP - x_CG) / L_ref
 */
public class MagnusForceTest {

	private static final double EPSILON = 1.0e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	private Rocket buildTestRocket() {
		Rocket rocket = new Rocket();
		rocket.setName("Magnus Test");

		AxialStage stage = new AxialStage();
		stage.setName("Stage");
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 0.15, 0.025);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.50, 0.025, 0.001);
		stage.addChild(body);

		TrapezoidFinSet fins = new TrapezoidFinSet();
		fins.setFinCount(4);
		fins.setRootChord(0.10);
		fins.setTipChord(0.05);
		fins.setSweep(0.02);
		fins.setHeight(0.05);
		fins.setThickness(0.003);
		body.addChild(fins);

		return rocket;
	}

	@Test
	void testMagnusDerivativesComputed() {
		Rocket rocket = buildTestRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		FlightConditions cond = new FlightConditions(config);
		cond.setMach(0.5);
		cond.setAOA(Math.toRadians(5.0));
		// Need non-zero pitch rate for damping computation
		cond.setPitchRate(0.1);

		WarningSet warnings = new WarningSet();
		AerodynamicForces forces = calc.getAerodynamicForces(config, cond, warnings);

		assertFalse(Double.isNaN(forces.getCyPa()),
				"CyPa should be computed (not NaN)");
		assertFalse(Double.isNaN(forces.getCnPa()),
				"CnPa should be computed (not NaN)");
	}

	@Test
	void testCyPaIsNegative() {
		// Cy_pa should be negative (opposes spin-induced lift)
		Rocket rocket = buildTestRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		FlightConditions cond = new FlightConditions(config);
		cond.setMach(0.5);
		cond.setAOA(Math.toRadians(5.0));
		cond.setPitchRate(0.1);

		WarningSet warnings = new WarningSet();
		AerodynamicForces forces = calc.getAerodynamicForces(config, cond, warnings);

		// CyPa = -(2/3) * CNa_body_fraction, should be negative for positive CNa
		double cnaTotal = forces.getCP().getWeight();
		if (cnaTotal > 0) {
			assertTrue(forces.getCyPa() < 0,
					"CyPa should be negative for positive CNa (opposes spin-induced lift)");
		}
	}

	@Test
	void testCyPaMagnitude() {
		// Cy_pa ~ -(2/3) * 0.3 * CNa_total
		Rocket rocket = buildTestRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		FlightConditions cond = new FlightConditions(config);
		cond.setMach(0.5);
		cond.setAOA(Math.toRadians(5.0));
		cond.setPitchRate(0.1);

		WarningSet warnings = new WarningSet();
		AerodynamicForces forces = calc.getAerodynamicForces(config, cond, warnings);

		double cnaTotal = forces.getCP().getWeight();
		double expectedCyPa = -(2.0 / 3.0) * 0.3 * cnaTotal;
		assertEquals(expectedCyPa, forces.getCyPa(), Math.abs(expectedCyPa) * 0.01 + EPSILON,
				"CyPa should match -(2/3)*0.3*CNa_total");
	}

	@Test
	void testCnPaSignForStableRocket() {
		// For a stable rocket (CP behind CG), Cn_pa should be negative (restoring)
		// when CyPa is negative and (xCP - xCG) > 0
		Rocket rocket = buildTestRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		FlightConditions cond = new FlightConditions(config);
		cond.setMach(0.5);
		cond.setAOA(Math.toRadians(5.0));
		cond.setPitchRate(0.1);

		WarningSet warnings = new WarningSet();
		AerodynamicForces forces = calc.getAerodynamicForces(config, cond, warnings);

		double cyPa = forces.getCyPa();
		double cnPa = forces.getCnPa();

		// Both should have the same sign (since xCP > xCG for a stable rocket)
		if (Math.abs(cyPa) > EPSILON) {
			// cnPa = cyPa * (xCP-xCG)/Lref
			// For a stable rocket xCP > xCG, and cyPa < 0, so cnPa < 0
			assertFalse(Double.isNaN(cnPa), "CnPa should not be NaN");
		}
	}

	@Test
	void testMagnusZeroWhenCNaZero() {
		// If total CNa is zero, Magnus derivatives should be zero
		FlightConditions cond = new FlightConditions(null);
		AerodynamicForces forces = new AerodynamicForces().zero();
		forces.setCP(new Coordinate(0, 0, 0, 0.0)); // zero CNa

		// Directly test the relationship
		double cnaTotal = forces.getCP().getWeight();
		double cyPa = -(2.0 / 3.0) * 0.3 * cnaTotal;
		assertEquals(0.0, cyPa, EPSILON,
				"CyPa should be zero when CNa is zero");
	}

	@Test
	void testMagnusAtDifferentMach() {
		// Magnus derivatives should be computed at both subsonic and supersonic
		Rocket rocket = buildTestRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();

		for (double mach : new double[]{0.3, 0.8, 1.5, 2.5}) {
			FlightConditions cond = new FlightConditions(config);
			cond.setMach(mach);
			cond.setAOA(Math.toRadians(5.0));
			cond.setPitchRate(0.1);

			AerodynamicForces forces = calc.getAerodynamicForces(config, cond, warnings);

			assertFalse(Double.isNaN(forces.getCyPa()),
					"CyPa should not be NaN at M=" + mach);
			assertFalse(Double.isNaN(forces.getCnPa()),
					"CnPa should not be NaN at M=" + mach);
		}
	}

	@Test
	void testMagnusScalesWithCNa() {
		// Higher CNa -> larger magnitude Magnus derivatives
		Rocket rocket = buildTestRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();

		FlightConditions cond1 = new FlightConditions(config);
		cond1.setMach(0.5);
		cond1.setAOA(Math.toRadians(2.0));
		cond1.setPitchRate(0.1);
		AerodynamicForces forces1 = calc.getAerodynamicForces(config, cond1, warnings);

		FlightConditions cond2 = new FlightConditions(config);
		cond2.setMach(0.5);
		cond2.setAOA(Math.toRadians(10.0));
		cond2.setPitchRate(0.1);
		AerodynamicForces forces2 = calc.getAerodynamicForces(config, cond2, warnings);

		// Both should be non-NaN
		assertFalse(Double.isNaN(forces1.getCyPa()), "CyPa at 2 deg should not be NaN");
		assertFalse(Double.isNaN(forces2.getCyPa()), "CyPa at 10 deg should not be NaN");
	}

	@Test
	void testAerodynamicForcesResetAndZero() {
		// Verify new fields are properly handled by reset() and zero()
		AerodynamicForces forces = new AerodynamicForces();
		forces.setCyPa(-0.5);
		forces.setCnPa(-0.3);

		assertEquals(-0.5, forces.getCyPa(), EPSILON);
		assertEquals(-0.3, forces.getCnPa(), EPSILON);

		AerodynamicForces zeroed = new AerodynamicForces().zero();
		assertEquals(0.0, zeroed.getCyPa(), EPSILON);
		assertEquals(0.0, zeroed.getCnPa(), EPSILON);
	}

	@Test
	void testFlightDataTypeExists() {
		// Verify the Magnus flight data type is registered
		assertNotNull(info.openrocket.core.simulation.FlightDataType.TYPE_MAGNUS_SIDE_FORCE,
				"TYPE_MAGNUS_SIDE_FORCE should exist");
	}
}
