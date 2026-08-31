package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.Warning;
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

/**
 * Tests for Phase 8d: Asymmetric Vortex Shedding at high AoA.
 *
 * At AoA > 20 deg, body vortex shedding becomes asymmetric, generating
 * a side force proportional to the body normal force.
 */
public class AsymmetricVortexSheddingTest {

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
		rocket.setName("Vortex Test");

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
	void testNoVortexBelowOnset() {
		// AoA < 20 deg: no vortex side force
		FlightConditions cond = new FlightConditions(null);
		cond.setAOA(Math.toRadians(15.0));

		AerodynamicForces forces = new AerodynamicForces().zero();
		forces.setCN(1.5); // some body normal force
		WarningSet warnings = new WarningSet();

		BarrowmanCalculator.applyAsymmetricVortexShedding(cond, forces, warnings);

		assertEquals(0.0, forces.getCside(), EPSILON,
				"No side force below onset AoA");
		assertFalse(warnings.contains(Warning.HIGH_AOA_VORTEX),
				"No warning below onset AoA");
	}

	@Test
	void testVortexAtOnsetBoundary() {
		// Exactly at onset: no side force (onset is exclusive)
		FlightConditions cond = new FlightConditions(null);
		cond.setAOA(Math.toRadians(20.0));

		AerodynamicForces forces = new AerodynamicForces().zero();
		forces.setCN(1.5);
		WarningSet warnings = new WarningSet();

		BarrowmanCalculator.applyAsymmetricVortexShedding(cond, forces, warnings);

		assertEquals(0.0, forces.getCside(), EPSILON,
				"At exactly onset AoA, side force should be zero");
	}

	@Test
	void testVortexAt30Degrees() {
		// AoA = 30 deg: CY = 0.20 * CN * f, where f = (30-20)/(40-20) = 0.5
		FlightConditions cond = new FlightConditions(null);
		cond.setAOA(Math.toRadians(30.0));

		double cnBody = 2.0;
		AerodynamicForces forces = new AerodynamicForces().zero();
		forces.setCN(cnBody);
		WarningSet warnings = new WarningSet();

		BarrowmanCalculator.applyAsymmetricVortexShedding(cond, forces, warnings);

		double expectedCY = 0.20 * cnBody * 0.5;
		assertEquals(expectedCY, forces.getCside(), 0.01,
				"CY at 30 deg should be ~0.10 * CN");
	}

	@Test
	void testVortexSaturatesAt40Degrees() {
		// AoA = 40 deg: CY = 0.20 * CN (saturated)
		FlightConditions cond = new FlightConditions(null);
		cond.setAOA(Math.toRadians(40.0));

		double cnBody = 2.0;
		AerodynamicForces forces = new AerodynamicForces().zero();
		forces.setCN(cnBody);
		WarningSet warnings = new WarningSet();

		BarrowmanCalculator.applyAsymmetricVortexShedding(cond, forces, warnings);

		double expectedCY = 0.20 * cnBody;
		assertEquals(expectedCY, forces.getCside(), 0.01,
				"CY should saturate at 40 deg");
	}

	@Test
	void testVortexAboveSaturation() {
		// AoA = 50 deg: same as saturation
		FlightConditions cond = new FlightConditions(null);
		cond.setAOA(Math.toRadians(50.0));

		double cnBody = 2.0;
		AerodynamicForces forces = new AerodynamicForces().zero();
		forces.setCN(cnBody);
		WarningSet warnings = new WarningSet();

		BarrowmanCalculator.applyAsymmetricVortexShedding(cond, forces, warnings);

		double expectedCY = 0.20 * cnBody;
		assertEquals(expectedCY, forces.getCside(), 0.01,
				"CY above 40 deg should equal saturated value");
	}

	@Test
	void testVortexProportionalToCN() {
		// CY should be proportional to CN
		FlightConditions cond = new FlightConditions(null);
		cond.setAOA(Math.toRadians(35.0));

		AerodynamicForces forces1 = new AerodynamicForces().zero();
		forces1.setCN(1.0);
		WarningSet w1 = new WarningSet();
		BarrowmanCalculator.applyAsymmetricVortexShedding(cond, forces1, w1);

		AerodynamicForces forces2 = new AerodynamicForces().zero();
		forces2.setCN(3.0);
		WarningSet w2 = new WarningSet();
		BarrowmanCalculator.applyAsymmetricVortexShedding(cond, forces2, w2);

		assertEquals(3.0, forces2.getCside() / forces1.getCside(), 0.01,
				"CY should scale linearly with CN");
	}

	/**
	 * The side force must switch on above the 20 deg onset.
	 * <p>
	 * This used to assert that {@code applyAsymmetricVortexShedding} also populated the
	 * warning set. The HIGH_AOA_VORTEX advisory now comes from
	 * {@code BasicEventSimulationEngine}, which -- unlike this calculator, invoked once per
	 * RK4 sub-step -- can tell whether a recovery device is out or the rocket has landed,
	 * and so no longer warns on every descent. FlightEventsTest covers the advisory.
	 */
	@Test
	void testSideForceAppliedAboveOnset() {
		FlightConditions cond = new FlightConditions(null);
		cond.setAOA(Math.toRadians(25.0));

		AerodynamicForces forces = new AerodynamicForces().zero();
		forces.setCN(1.0);
		WarningSet warnings = new WarningSet();

		BarrowmanCalculator.applyAsymmetricVortexShedding(cond, forces, warnings);

		assertTrue(Math.abs(forces.getCside()) > 0,
				"vortex side force should be applied above the 20 deg onset");
	}

	@Test
	void testFullCalculatorIntegration() {
		// End-to-end test through BarrowmanCalculator
		Rocket rocket = buildTestRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		FlightConditions cond = new FlightConditions(config);
		cond.setMach(0.5);
		cond.setAOA(Math.toRadians(25.0));

		WarningSet warnings = new WarningSet();
		AerodynamicForces forces = calc.getAerodynamicForces(config, cond, warnings);

		// Should have non-zero side force from vortex shedding
		double cside = forces.getCside();
		assertFalse(Double.isNaN(cside), "Cside should not be NaN");
		// The HIGH_AOA_VORTEX advisory is raised by BasicEventSimulationEngine, not here --
		// see testSideForceAppliedAboveOnset for why. Only the force is checked at this level.
		// Side force should be non-zero (positive or negative depending on CN sign)
		assertTrue(Math.abs(cside) > 0 || forces.getCN() == 0,
				"Cside should be non-zero when CN is non-zero");
	}
}
