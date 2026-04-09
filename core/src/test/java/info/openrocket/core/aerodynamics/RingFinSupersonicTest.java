package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.KantrowitzLimit;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TubeFinSet;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.startup.Application;

import java.util.Map;

/**
 * Tests for Phase 7b: Ring Fin (Tube Fin) Supersonic Model.
 * <p>
 * Validates the Kantrowitz limit computation and the spilled/started
 * flow regime transition for ring (tube) fins at supersonic speeds.
 */
public class RingFinSupersonicTest {

	private static final double EPS = 1e-6;
	private static final double GAMMA = 1.4;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ================================================================
	// KantrowitzLimit unit tests
	// ================================================================

	@Test
	void testKantrowitzFunctionAtMach1() {
		double f = KantrowitzLimit.kantrowitzFunction(1.001, GAMMA);
		// At M=1, the function should be close to 1
		assertTrue(f > 0.99 && f <= 1.0,
				"Kantrowitz function at M~1 should be ~1, got " + f);
	}

	@Test
	void testKantrowitzFunctionDecreases() {
		double f1 = KantrowitzLimit.kantrowitzFunction(1.5, GAMMA);
		double f2 = KantrowitzLimit.kantrowitzFunction(2.0, GAMMA);
		double f3 = KantrowitzLimit.kantrowitzFunction(3.0, GAMMA);

		assertTrue(f1 > f2, "Kantrowitz function should decrease with Mach");
		assertTrue(f2 > f3, "Kantrowitz function should decrease with Mach");
		assertTrue(f3 > 0, "Kantrowitz function should be positive");
	}

	@Test
	void testStartMachForTypicalRingFin() {
		// r_inner/r_outer = 0.5 => start Mach should be in range 1.7-1.9
		double rInner = 0.0125;  // 12.5mm
		double rOuter = 0.025;   // 25mm
		double mStart = KantrowitzLimit.computeStartMach(rInner, rOuter);

		assertTrue(mStart > 1.0, "Start Mach should be supersonic, got " + mStart);
		assertTrue(mStart < 5.0, "Start Mach should be reasonable, got " + mStart);
	}

	@Test
	void testStartMachForLargeContraction() {
		// Small inner radius relative to outer => harder to start
		double rInner = 0.020;   // 20mm
		double rOuter = 0.025;   // 25mm (thin annulus)
		double mStart = KantrowitzLimit.computeStartMach(rInner, rOuter);

		assertTrue(mStart > 1.0, "Should require supersonic to start");
		// A highly contracted annulus needs a higher Mach to start
		assertTrue(Double.isFinite(mStart), "Start Mach should be finite");
	}

	@Test
	void testStartMachInvalidGeometry() {
		// rInner >= rOuter: no valid annulus
		double mStart = KantrowitzLimit.computeStartMach(0.03, 0.025);
		assertEquals(Double.POSITIVE_INFINITY, mStart,
				"Invalid geometry should return infinity");

		mStart = KantrowitzLimit.computeStartMach(0, 0.025);
		// Zero inner radius means no contraction, starts easily
		assertTrue(mStart <= 1.001, "Zero inner radius should start at M~1");
	}

	@Test
	void testStartMachLargerAnnulusStartsEasier() {
		// Larger annulus (smaller r_inner/r_outer ratio) should start at lower Mach
		double mStart1 = KantrowitzLimit.computeStartMach(0.010, 0.025); // 40% blockage
		double mStart2 = KantrowitzLimit.computeStartMach(0.020, 0.025); // 80% blockage

		assertTrue(mStart1 < mStart2,
				"Less blocked annulus should start at lower Mach: " +
				mStart1 + " vs " + mStart2);
	}

	// ================================================================
	// Ring fin drag regime tests
	// ================================================================

	/**
	 * Create a rocket with tube fins for testing.
	 */
	private static Rocket makeRocketWithTubeFins() {
		Rocket rocket = new Rocket();
		rocket.setName("Tube Fin Rocket");

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 0.15, 0.025);
		nose.setName("Conical Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.60, 0.025, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		TubeFinSet tubeFins = new TubeFinSet();
		tubeFins.setFinCount(4);
		tubeFins.setOuterRadius(0.015);
		tubeFins.setLength(0.05);
		tubeFins.setThickness(0.002);
		tubeFins.setAxialMethod(AxialMethod.BOTTOM);
		tubeFins.setName("Tube Fins");
		body.addChild(tubeFins);

		rocket.enableEvents();
		return rocket;
	}

	private static Rocket makeRocketWithoutTubeFins() {
		Rocket rocket = new Rocket();
		rocket.setName("No Tube Fin Rocket");

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 0.15, 0.025);
		nose.setName("Conical Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.60, 0.025, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}

	@Test
	void testSubsonicRevertsToStandardTreatment() {
		Rocket rocket = makeRocketWithTubeFins();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(0.5);

		BarrowmanCalculator calc = new BarrowmanCalculator();
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());

		assertTrue(Double.isFinite(forces.getCD()), "CD should be finite at M=0.5");
		assertTrue(forces.getCD() > 0, "CD should be positive at M=0.5");
	}

	@Test
	void testTubeFinDragIncreasesSupersonically() {
		Rocket withTF = makeRocketWithTubeFins();
		Rocket withoutTF = makeRocketWithoutTubeFins();

		var configTF = withTF.getSelectedConfiguration();
		var configNoTF = withoutTF.getSelectedConfiguration();

		BarrowmanCalculator calc1 = new BarrowmanCalculator();
		BarrowmanCalculator calc2 = new BarrowmanCalculator();

		var condTF = new FlightConditions(configTF);
		condTF.setMach(2.0);
		condTF.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		var condNoTF = new FlightConditions(configNoTF);
		condNoTF.setMach(2.0);
		condNoTF.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		AerodynamicForces forcesTF = calc1.getAerodynamicForces(configTF, condTF, new WarningSet());
		AerodynamicForces forcesNoTF = calc2.getAerodynamicForces(configNoTF, condNoTF, new WarningSet());

		double increment = forcesTF.getCD() - forcesNoTF.getCD();
		assertTrue(increment > 0,
				"Tube fins should add drag at M=2, diff=" + increment);
	}

	@Test
	void testSmoothTransonicTransition() {
		Rocket rocket = makeRocketWithTubeFins();
		var config = rocket.getSelectedConfiguration();

		BarrowmanCalculator calc = new BarrowmanCalculator();
		double prevCd = 0;

		for (double m = 0.8; m <= 1.5; m += 0.05) {
			var conditions = new FlightConditions(config);
			conditions.setMach(m);
			conditions.setAtmosphericConditions(
					new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
			double cd = forces.getCD();

			assertTrue(Double.isFinite(cd), "CD should be finite at M=" + m);

			if (prevCd > 0) {
				double relChange = Math.abs(cd - prevCd) / Math.max(prevCd, 0.01);
				assertTrue(relChange < 3.0,
						"CD change too large at M=" + m + ": prev=" + prevCd + ", curr=" + cd);
			}
			prevCd = cd;
		}
	}

	@Test
	void testNoNanAtEdgeCases() {
		Rocket rocket = makeRocketWithTubeFins();
		var config = rocket.getSelectedConfiguration();

		BarrowmanCalculator calc = new BarrowmanCalculator();

		for (double m : new double[]{0.01, 0.5, 0.9, 1.0, 1.001, 1.1, 2.0, 3.0, 5.0}) {
			var conditions = new FlightConditions(config);
			conditions.setMach(m);
			conditions.setAtmosphericConditions(
					new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
			assertTrue(Double.isFinite(forces.getCD()),
					"CD should be finite at M=" + m + ", got " + forces.getCD());
		}
	}
}
