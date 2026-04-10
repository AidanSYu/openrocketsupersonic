package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;

/**
 * Tests for crossflow drag at high angle of attack (tumbling).
 * Verifies that a tumbling rocket experiences dramatically higher
 * normal force than Barrowman theory predicts, matching real-world
 * behavior where an unstable rocket cannot accelerate to high Mach.
 */
public class CrossflowDragTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	private AerodynamicForces computeForces(Rocket rocket, double mach, double aoaDeg) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(aoaDeg));
		WarningSet warnings = new WarningSet();
		return calc.getAerodynamicForces(config, conditions, warnings);
	}

	/**
	 * At 90 deg AoA, CN must be much larger than at 0 deg.
	 * The rocket's side profile acts as a bluff body with crossflow drag
	 * proportional to planform area, which is 10-20x the reference area.
	 */
	@Test
	public void cnMassiveAt90DegAoA() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		AerodynamicForces forces0 = computeForces(rocket, 0.5, 0);
		AerodynamicForces forces90 = computeForces(rocket, 0.5, 90);

		double cn0 = Math.abs(forces0.getCN());
		double cn90 = Math.abs(forces90.getCN());

		assertTrue(cn90 > 5.0,
				"CN at 90 deg AoA should be large (bluff body crossflow), got " + cn90);
		assertTrue(cn90 > cn0 + 1.0,
				"CN at 90 deg (" + cn90 + ") should be much larger than at 0 deg (" + cn0 + ")");
	}

	/**
	 * At 45 deg AoA, crossflow drag should already be significant.
	 */
	@Test
	public void cnSignificantAt45DegAoA() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		AerodynamicForces forces = computeForces(rocket, 1.5, 45);

		double cn = Math.abs(forces.getCN());

		// At 45 deg, sin^2(45) = 0.5, so crossflow CN is about half
		// the 90 deg value. For a finned rocket this should be > 2.
		assertTrue(cn > 2.0,
				"CN at 45 deg AoA should be significant (crossflow regime), got " + cn);
	}

	/**
	 * CN must increase with AoA in the post-stall regime.
	 */
	@Test
	public void cnIncreasesPostStall() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		AerodynamicForces forces30 = computeForces(rocket, 0.5, 30);
		AerodynamicForces forces60 = computeForces(rocket, 0.5, 60);
		AerodynamicForces forces90 = computeForces(rocket, 0.5, 90);

		double cn30 = Math.abs(forces30.getCN());
		double cn60 = Math.abs(forces60.getCN());
		double cn90 = Math.abs(forces90.getCN());

		assertTrue(cn60 > cn30,
				"CN at 60 deg (" + cn60 + ") should exceed CN at 30 deg (" + cn30 + ")");
		assertTrue(cn90 > cn60,
				"CN at 90 deg (" + cn90 + ") should exceed CN at 60 deg (" + cn60 + ")");
	}

	/**
	 * At low AoA (5 deg), the crossflow drag should NOT dominate.
	 * The existing Barrowman CN should remain unchanged.
	 */
	@Test
	public void noChangeAtLowAoA() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		AerodynamicForces forces = computeForces(rocket, 0.3, 5);

		double cn = Math.abs(forces.getCN());
		// CN at 5 deg should be in the normal Barrowman range
		assertTrue(cn > 1.0 && cn < 10.0,
				"CN at 5 deg AoA should be in normal Barrowman range, got " + cn);
	}

	/**
	 * Supersonic crossflow: at Mach 2 and 90 deg AoA, the crossflow Mach is 2.0.
	 * The Jorgensen crossflow Cd_c rises to ~1.95, so CN should increase.
	 */
	@Test
	public void supersonicCrossflowIncreasesForce() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		AerodynamicForces forcesSub = computeForces(rocket, 0.3, 90);
		AerodynamicForces forcesSup = computeForces(rocket, 2.0, 90);

		double cnSub = Math.abs(forcesSub.getCN());
		double cnSup = Math.abs(forcesSup.getCN());

		// At supersonic crossflow Mach, Cd_c rises from 1.2 to ~1.95
		assertTrue(cnSup > cnSub,
				"CN at Mach 2 crossflow (" + cnSup + ") should exceed subsonic crossflow (" + cnSub + ")");
	}

	/**
	 * The velocity-direction deceleration force at 90 deg AoA and Mach 2
	 * should be enormous compared to zero-AoA drag.
	 * At 90 deg, the body axis is perpendicular to velocity, so all
	 * deceleration comes from the normal force projected onto velocity:
	 * F_decel = CN * q * S * sin(alpha) = CN * q * S at alpha=90.
	 * CN should be >> CD at zero AoA (10x or more).
	 */
	@Test
	public void tumblingDragMuchHigherThanStreamlined() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		AerodynamicForces forces0 = computeForces(rocket, 2.0, 0);
		AerodynamicForces forces90 = computeForces(rocket, 2.0, 90);

		double cd0 = forces0.getCD();
		double cn90 = Math.abs(forces90.getCN());

		// At 90 deg, the effective drag (CN at 90 deg) should be much larger
		// than the streamlined drag (CD at 0 deg). A typical rocket has
		// planform area >> reference area, so expect 5-20x.
		assertTrue(cn90 > cd0 * 5,
				"Tumbling CN (" + cn90 + ") should be >> streamlined CD (" + cd0
						+ ") — expect at least 5x for proper crossflow drag");
	}
}
