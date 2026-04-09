package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;

/**
 * Tests for lift-induced drag (CDi = CN * sin(alpha)).
 * Verifies that at nonzero angle of attack, the axial projection of
 * normal force is properly added to total drag.
 */
public class LiftInducedDragTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	/**
	 * Build a simple rocket: ogive nose + body tube (no fins).
	 */
	private Rocket makeFinlessRocket() {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 0.10, 0.02);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.30, 0.02, 0.001);
		stage.addChild(body);

		return rocket;
	}

	/**
	 * Build a rocket with fins: ogive nose + body tube + trapezoidal fins.
	 */
	private Rocket makeRocketWithFins() {
		// Use Estes Alpha III which has fins and thus higher CNa
		return TestRockets.makeEstesAlphaIII();
	}

	private AerodynamicForces computeForces(Rocket rocket, double aoaRad) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(0.3);
		conditions.setAOA(aoaRad);
		WarningSet warnings = new WarningSet();
		return calc.getAerodynamicForces(config, conditions, warnings);
	}

	/**
	 * At zero AoA, induced drag must be zero -- no change from baseline.
	 */
	@Test
	public void inducedDragZeroAtZeroAoA() {
		Rocket rocket = makeRocketWithFins();
		AerodynamicForces forces = computeForces(rocket, 0.0);

		// CD should equal friction + pressure + base + override (no induced drag)
		double componentSum = forces.getFrictionCD() + forces.getPressureCD()
				+ forces.getBaseCD() + forces.getOverrideCD();
		assertEquals(componentSum, forces.getCD(), 1e-10,
				"At zero AoA, total CD should equal component sum with no induced drag");
	}

	/**
	 * At nonzero AoA, induced drag must be positive.
	 */
	@Test
	public void inducedDragPositiveAtNonzeroAoA() {
		Rocket rocket = makeRocketWithFins();
		double alpha = Math.toRadians(5.0);
		AerodynamicForces forces = computeForces(rocket, alpha);

		// CD should exceed the zero-lift drag components
		double componentSum = forces.getFrictionCD() + forces.getPressureCD()
				+ forces.getBaseCD() + forces.getOverrideCD();
		double CDi = forces.getCD() - componentSum;
		assertTrue(CDi > 0,
				"At 5 deg AoA, induced drag should be positive, got CDi=" + CDi);
	}

	/**
	 * Induced drag at 10 deg should exceed that at 5 deg (CN and sin both increase).
	 */
	@Test
	public void inducedDragIncreasesWithAoA() {
		Rocket rocket = makeRocketWithFins();

		AerodynamicForces forces5 = computeForces(rocket, Math.toRadians(5.0));
		double componentSum5 = forces5.getFrictionCD() + forces5.getPressureCD()
				+ forces5.getBaseCD() + forces5.getOverrideCD();
		double CDi5 = forces5.getCD() - componentSum5;

		AerodynamicForces forces10 = computeForces(rocket, Math.toRadians(10.0));
		double componentSum10 = forces10.getFrictionCD() + forces10.getPressureCD()
				+ forces10.getBaseCD() + forces10.getOverrideCD();
		double CDi10 = forces10.getCD() - componentSum10;

		assertTrue(CDi10 > CDi5,
				"Induced drag at 10 deg (" + CDi10 + ") should exceed that at 5 deg (" + CDi5 + ")");
	}

	/**
	 * A rocket with fins (higher CNa) should have more induced drag than a finless one.
	 */
	@Test
	public void inducedDragIncreasesWithCNa() {
		double alpha = Math.toRadians(5.0);

		Rocket finless = makeFinlessRocket();
		AerodynamicForces forcesFinless = computeForces(finless, alpha);
		double csFinless = forcesFinless.getFrictionCD() + forcesFinless.getPressureCD()
				+ forcesFinless.getBaseCD() + forcesFinless.getOverrideCD();
		double CDiFinless = forcesFinless.getCD() - csFinless;

		Rocket withFins = makeRocketWithFins();
		AerodynamicForces forcesWithFins = computeForces(withFins, alpha);
		double csWithFins = forcesWithFins.getFrictionCD() + forcesWithFins.getPressureCD()
				+ forcesWithFins.getBaseCD() + forcesWithFins.getOverrideCD();
		double CDiWithFins = forcesWithFins.getCD() - csWithFins;

		assertTrue(CDiWithFins > CDiFinless,
				"Rocket with fins (CDi=" + CDiWithFins + ") should have more induced drag than finless (CDi=" + CDiFinless + ")");
	}

	/**
	 * Sanity check: at alpha=10 deg, CDi should be in a physically reasonable range.
	 * For a finned rocket, CNa ~ 10-30, alpha = 0.175 rad, so CN ~ CNa * alpha ~ 1.7-5.2,
	 * CDi = CN * sin(alpha) ~ 0.3-0.9. Check it's roughly in this ballpark.
	 */
	@Test
	public void inducedDragMagnitudeReasonable() {
		double alpha = Math.toRadians(10.0);
		Rocket rocket = makeRocketWithFins();
		AerodynamicForces forces = computeForces(rocket, alpha);

		double componentSum = forces.getFrictionCD() + forces.getPressureCD()
				+ forces.getBaseCD() + forces.getOverrideCD();
		double CDi = forces.getCD() - componentSum;

		// CN = CNa * sin(alpha) for linear theory; CDi = CN * sin(alpha)
		// For Estes Alpha III, CNa ~ 26, alpha = 0.1745
		// CN ~ 26 * sin(0.1745) ~ 4.5, CDi ~ 4.5 * sin(0.1745) ~ 0.78
		// Allow wide range: 0.1 to 5.0
		assertTrue(CDi > 0.1,
				"CDi at 10 deg should be at least 0.1, got " + CDi);
		assertTrue(CDi < 5.0,
				"CDi at 10 deg should be less than 5.0, got " + CDi);
	}

	/**
	 * Total CD at nonzero AoA must exceed total CD at zero AoA
	 * (induced drag adds to the zero-lift drag).
	 */
	@Test
	public void totalDragIncreasesWithAoA() {
		Rocket rocket = makeRocketWithFins();

		AerodynamicForces forces0 = computeForces(rocket, 0.0);
		AerodynamicForces forces5 = computeForces(rocket, Math.toRadians(5.0));

		assertTrue(forces5.getCD() > forces0.getCD(),
				"Total CD at 5 deg AoA (" + forces5.getCD()
						+ ") should exceed total CD at 0 deg (" + forces0.getCD() + ")");
	}
}
