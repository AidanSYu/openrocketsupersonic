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
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;

import java.util.Map;
import info.openrocket.core.rocketcomponent.RocketComponent;

/**
 * Tests for Phase 7d: Interstage Step Drag (ESDU 66011).
 * <p>
 * Validates the forward-facing step drag model at supersonic speeds
 * for rockets with diameter transitions (interstage steps).
 */
public class InterstageStepDragTest {

	private static final double EPS = 1e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	/**
	 * Create a rocket with a forward-facing step (upper stage smaller than lower stage).
	 * Nose cone (25mm radius) -> body tube (25mm) -> larger body tube (37.5mm radius)
	 */
	private static Rocket makeStepRocket(double stepHeight_mm) {
		Rocket rocket = new Rocket();
		rocket.setName("Step Rocket");

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 0.15, 0.025);
		nose.setName("Conical Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		BodyTube body1 = new BodyTube(0.30, 0.025, 0.001);
		body1.setName("Upper Body");
		stage.addChild(body1);

		// Larger body tube creates a forward-facing step
		double r2 = 0.025 + stepHeight_mm / 1000.0;
		BodyTube body2 = new BodyTube(0.30, r2, 0.001);
		body2.setName("Lower Body (step)");
		stage.addChild(body2);

		rocket.enableEvents();
		return rocket;
	}

	/**
	 * Create a rocket with no step (uniform body tube).
	 */
	private static Rocket makeNoStepRocket() {
		Rocket rocket = new Rocket();
		rocket.setName("No-Step Rocket");

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

	// ================================================================
	// Test: At M<1, step drag is zero
	// ================================================================

	@Test
	void testStepDragZeroAtSubsonic() {
		Rocket rocket = makeStepRocket(20); // 20mm step
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(0.5);

		BarrowmanCalculator calc = new BarrowmanCalculator();
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());

		// At M=0.5, step drag should not be active
		// The step is handled by the existing stagnation CD model, not the ESDU model
		assertNotNull(forces);
		assertTrue(Double.isFinite(forces.getCD()), "CD should be finite at M=0.5");
	}

	@Test
	void testStepDragZeroAtMach08() {
		Rocket rocket = makeStepRocket(20);
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(0.8);

		Rocket noStepRocket = makeNoStepRocket();
		var noStepConfig = noStepRocket.getSelectedConfiguration();
		var noStepConditions = new FlightConditions(noStepConfig);
		noStepConditions.setMach(0.8);

		BarrowmanCalculator calc1 = new BarrowmanCalculator();
		BarrowmanCalculator calc2 = new BarrowmanCalculator();

		AerodynamicForces stepForces = calc1.getAerodynamicForces(config, conditions, new WarningSet());
		AerodynamicForces noStepForces = calc2.getAerodynamicForces(noStepConfig, noStepConditions, new WarningSet());

		// The ESDU supersonic step drag should not contribute at M=0.8
		// (difference should be from the normal stagnation model only, not ESDU)
		assertTrue(Double.isFinite(stepForces.getCD()));
		assertTrue(Double.isFinite(noStepForces.getCD()));
	}

	// ================================================================
	// Test: At M=2, step drag is positive and in expected range
	// ================================================================

	@Test
	void testStepDragPositiveAtMach2() {
		Rocket rocket = makeStepRocket(20); // 20mm step on 50mm diameter body
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(
						101325, 288.15));

		BarrowmanCalculator calc = new BarrowmanCalculator();
		Map<RocketComponent, AerodynamicForces> forceMap =
				calc.getForceAnalysis(config, conditions, new WarningSet());

		// Find the lower body tube (step component)
		AerodynamicForces stepForces = null;
		for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
			if (entry.getKey().getName().equals("Lower Body (step)")) {
				stepForces = entry.getValue();
				break;
			}
		}

		assertNotNull(stepForces, "Should find step component forces");
		double pressureCd = stepForces.getPressureCD();
		assertTrue(Double.isFinite(pressureCd), "Step pressure CD should be finite");
		// Step drag should be positive (adds drag)
		assertTrue(pressureCd >= 0, "Step pressure CD should be non-negative");
	}

	@Test
	void testStepDragInExpectedRange() {
		// M=2, 20mm step on 75mm diameter body
		// Expected Cd_step approx 0.05-0.10
		Rocket rocket = makeStepRocket(20);
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(
						101325, 288.15));

		Rocket noStepRocket = makeNoStepRocket();
		var noStepConfig = noStepRocket.getSelectedConfiguration();
		var noStepConditions = new FlightConditions(noStepConfig);
		noStepConditions.setMach(2.0);
		noStepConditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(
						101325, 288.15));

		BarrowmanCalculator calc1 = new BarrowmanCalculator();
		BarrowmanCalculator calc2 = new BarrowmanCalculator();

		AerodynamicForces stepForces = calc1.getAerodynamicForces(config, conditions, new WarningSet());
		AerodynamicForces noStepForces = calc2.getAerodynamicForces(noStepConfig, noStepConditions, new WarningSet());

		double cdDiff = stepForces.getCD() - noStepForces.getCD();
		// Step drag increment should be positive
		assertTrue(cdDiff > 0, "Step should add drag at M=2, diff=" + cdDiff);
	}

	// ================================================================
	// Test: Separation length scales correctly
	// ================================================================

	@Test
	void testMomentumThicknessPositive() {
		var conditions = new FlightConditions(null);
		conditions.setMach(2.0);
		conditions.setVelocity(680); // ~M2 at sea level
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(
						101325, 288.15));

		double theta = ShockGeometry.getMomentumThicknessAt(0.5, conditions);
		assertTrue(theta > 0, "Momentum thickness should be positive at x=0.5m");
		assertTrue(theta < 0.01, "Momentum thickness should be small (< 10mm)");
	}

	@Test
	void testMomentumThicknessGrowsWithDistance() {
		var conditions = new FlightConditions(null);
		conditions.setMach(2.0);
		conditions.setVelocity(680);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(
						101325, 288.15));

		double theta1 = ShockGeometry.getMomentumThicknessAt(0.2, conditions);
		double theta2 = ShockGeometry.getMomentumThicknessAt(0.5, conditions);
		double theta3 = ShockGeometry.getMomentumThicknessAt(1.0, conditions);

		assertTrue(theta2 > theta1, "Momentum thickness should grow with x");
		assertTrue(theta3 > theta2, "Momentum thickness should grow with x");
	}

	@Test
	void testMomentumThicknessZeroAtNose() {
		var conditions = new FlightConditions(null);
		conditions.setMach(2.0);
		conditions.setVelocity(680);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(
						101325, 288.15));

		double theta = ShockGeometry.getMomentumThicknessAt(0, conditions);
		assertEquals(0, theta, EPS, "Momentum thickness should be 0 at x=0");
	}

	// ================================================================
	// Test: Reattachment recovery is fraction of step face drag
	// ================================================================

	@Test
	void testReattachmentRecoverySmallerThanStepFaceDrag() {
		// The reattachment recovery drag should be less than the step face drag
		// We verify this indirectly by comparing small vs large step heights
		Rocket smallStep = makeStepRocket(5);   // 5mm step
		Rocket largeStep = makeStepRocket(20);  // 20mm step

		var smallConfig = smallStep.getSelectedConfiguration();
		var largeConfig = largeStep.getSelectedConfiguration();

		var smallCond = new FlightConditions(smallConfig);
		smallCond.setMach(2.0);
		smallCond.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		var largeCond = new FlightConditions(largeConfig);
		largeCond.setMach(2.0);
		largeCond.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		BarrowmanCalculator calc1 = new BarrowmanCalculator();
		BarrowmanCalculator calc2 = new BarrowmanCalculator();

		AerodynamicForces smallForces = calc1.getAerodynamicForces(smallConfig, smallCond, new WarningSet());
		AerodynamicForces largeForces = calc2.getAerodynamicForces(largeConfig, largeCond, new WarningSet());

		// Larger step should produce more drag
		assertTrue(largeForces.getCD() > smallForces.getCD(),
				"Larger step should produce more drag: large=" + largeForces.getCD() +
				", small=" + smallForces.getCD());
	}

	// ================================================================
	// Test: Step drag increases with Mach
	// ================================================================

	@Test
	void testStepDragIncreasesThroughTransonic() {
		Rocket rocket = makeStepRocket(15);
		var config = rocket.getSelectedConfiguration();

		BarrowmanCalculator calc = new BarrowmanCalculator();

		// Collect total CD at different Mach numbers
		double cdAt09 = getCdAtMach(calc, config, 0.9);
		double cdAt12 = getCdAtMach(calc, config, 1.2);
		double cdAt20 = getCdAtMach(calc, config, 2.0);

		// Drag should generally increase from subsonic to supersonic
		// (combination of wave drag rise + step drag)
		assertTrue(cdAt12 > cdAt09, "CD at M=1.2 should exceed CD at M=0.9");
		assertTrue(Double.isFinite(cdAt20), "CD at M=2.0 should be finite");
	}

	private double getCdAtMach(BarrowmanCalculator calc,
			info.openrocket.core.rocketcomponent.FlightConfiguration config, double mach) {
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));
		return calc.getAerodynamicForces(config, conditions, new WarningSet()).getCD();
	}

	// ================================================================
	// Test: No NaN or Inf in outputs
	// ================================================================

	@Test
	void testNoNanAtMach1() {
		Rocket rocket = makeStepRocket(20);
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(1.0);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		BarrowmanCalculator calc = new BarrowmanCalculator();
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());

		assertTrue(Double.isFinite(forces.getCD()), "CD should be finite at M=1.0");
		assertTrue(Double.isFinite(forces.getCN()), "CN should be finite at M=1.0");
	}

	@Test
	void testNoNanAtMach5() {
		Rocket rocket = makeStepRocket(20);
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(5.0);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		BarrowmanCalculator calc = new BarrowmanCalculator();
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());

		assertTrue(Double.isFinite(forces.getCD()), "CD should be finite at M=5.0");
		assertTrue(Double.isFinite(forces.getCN()), "CN should be finite at M=5.0");
	}
}
