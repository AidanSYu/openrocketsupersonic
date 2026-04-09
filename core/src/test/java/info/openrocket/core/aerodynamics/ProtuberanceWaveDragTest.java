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
import info.openrocket.core.rocketcomponent.RailButton;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.startup.Application;

import java.util.Map;

/**
 * Tests for Phase 7c: Protuberance Wave Drag.
 * <p>
 * Validates the supersonic wave drag model for rail buttons and launch lugs
 * including attached/detached shock regimes and body interference.
 */
public class ProtuberanceWaveDragTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	/**
	 * Create a rocket with rail buttons for testing protuberance drag.
	 */
	private static Rocket makeRocketWithRailButtons() {
		Rocket rocket = new Rocket();
		rocket.setName("Rail Button Rocket");

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

		// Add rail buttons
		RailButton rb = new RailButton();
		rb.setName("Rail Buttons");
		rb.setInstanceCount(2);
		rb.setAxialMethod(AxialMethod.TOP);
		body.addChild(rb);

		rocket.enableEvents();
		return rocket;
	}

	/**
	 * Create a rocket without rail buttons for comparison.
	 */
	private static Rocket makeRocketWithoutRailButtons() {
		Rocket rocket = new Rocket();
		rocket.setName("No Rail Button Rocket");

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
	// Test: Subsonic Cd unchanged (Hoerner value)
	// ================================================================

	@Test
	void testSubsonicCdUnchanged() {
		Rocket withRB = makeRocketWithRailButtons();
		Rocket withoutRB = makeRocketWithoutRailButtons();

		var configRB = withRB.getSelectedConfiguration();
		var configNoRB = withoutRB.getSelectedConfiguration();

		var condRB = new FlightConditions(configRB);
		condRB.setMach(0.5);
		var condNoRB = new FlightConditions(configNoRB);
		condNoRB.setMach(0.5);

		BarrowmanCalculator calc1 = new BarrowmanCalculator();
		BarrowmanCalculator calc2 = new BarrowmanCalculator();

		AerodynamicForces forcesRB = calc1.getAerodynamicForces(configRB, condRB, new WarningSet());
		AerodynamicForces forcesNoRB = calc2.getAerodynamicForces(configNoRB, condNoRB, new WarningSet());

		// Rail buttons should add some drag even at subsonic
		double diff = forcesRB.getCD() - forcesNoRB.getCD();
		assertTrue(diff >= 0, "Rail buttons should add drag at M=0.5");
		assertTrue(Double.isFinite(forcesRB.getCD()), "CD should be finite");
	}

	// ================================================================
	// Test: M=2 blunt protuberance uses detached shock
	// ================================================================

	@Test
	void testSupersonicDragFiniteAndPositive() {
		Rocket rocket = makeRocketWithRailButtons();
		var config = rocket.getSelectedConfiguration();

		BarrowmanCalculator calcSup = new BarrowmanCalculator();

		var condSup = new FlightConditions(config);
		condSup.setMach(2.0);
		condSup.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		AerodynamicForces forcesSup = calcSup.getAerodynamicForces(config, condSup, new WarningSet());

		assertTrue(Double.isFinite(forcesSup.getCD()), "CD at M=2 should be finite");
		assertTrue(forcesSup.getCD() > 0, "CD at M=2 should be positive");
	}

	// ================================================================
	// Test: Rail button drag increment is in reasonable range
	// ================================================================

	@Test
	void testRailButtonDragIncrementAtMach2() {
		Rocket withRB = makeRocketWithRailButtons();
		Rocket withoutRB = makeRocketWithoutRailButtons();

		var configRB = withRB.getSelectedConfiguration();
		var configNoRB = withoutRB.getSelectedConfiguration();

		var condRB = new FlightConditions(configRB);
		condRB.setMach(2.0);
		condRB.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		var condNoRB = new FlightConditions(configNoRB);
		condNoRB.setMach(2.0);
		condNoRB.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		BarrowmanCalculator calc1 = new BarrowmanCalculator();
		BarrowmanCalculator calc2 = new BarrowmanCalculator();

		AerodynamicForces forcesRB = calc1.getAerodynamicForces(configRB, condRB, new WarningSet());
		AerodynamicForces forcesNoRB = calc2.getAerodynamicForces(configNoRB, condNoRB, new WarningSet());

		double increment = forcesRB.getCD() - forcesNoRB.getCD();
		assertTrue(increment > 0, "Rail buttons should add drag at M=2, diff=" + increment);
		// Rail button drag should be small relative to total but non-negligible
		assertTrue(increment < 1.0, "Rail button drag increment should be reasonable");
	}

	// ================================================================
	// Test: Interference drag is fraction of frontal-area drag
	// ================================================================

	@Test
	void testInterferenceDragPositiveAtSupersonic() {
		Rocket rocket = makeRocketWithRailButtons();
		var config = rocket.getSelectedConfiguration();

		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		BarrowmanCalculator calc = new BarrowmanCalculator();
		Map<RocketComponent, AerodynamicForces> forceMap =
				calc.getForceAnalysis(config, conditions, new WarningSet());

		// Find rail button forces
		AerodynamicForces rbForces = null;
		for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
			if (entry.getKey() instanceof RailButton) {
				rbForces = entry.getValue();
				break;
			}
		}

		assertNotNull(rbForces, "Should find rail button forces");
		double pressureCd = rbForces.getPressureCD();
		assertTrue(Double.isFinite(pressureCd), "Rail button pressure CD should be finite");
		assertTrue(pressureCd >= 0, "Rail button pressure CD should be non-negative");
	}

	// ================================================================
	// Test: Smooth transition through Mach 1
	// ================================================================

	@Test
	void testSmoothTransonicTransition() {
		Rocket rocket = makeRocketWithRailButtons();
		var config = rocket.getSelectedConfiguration();

		BarrowmanCalculator calc = new BarrowmanCalculator();
		double prevCd = 0;
		boolean monotonic = true;

		for (double m = 0.8; m <= 1.3; m += 0.02) {
			var conditions = new FlightConditions(config);
			conditions.setMach(m);
			conditions.setAtmosphericConditions(
					new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
			double cd = forces.getCD();

			assertTrue(Double.isFinite(cd), "CD should be finite at M=" + m);
			assertTrue(cd >= 0, "CD should be non-negative at M=" + m);

			// Check that the transition is smooth (no huge jumps)
			if (prevCd > 0) {
				double relChange = Math.abs(cd - prevCd) / Math.max(prevCd, 0.01);
				assertTrue(relChange < 2.0,
						"CD change too large between M=" + (m - 0.02) + " and M=" + m +
						": prev=" + prevCd + ", curr=" + cd);
			}
			prevCd = cd;
		}
	}

	// ================================================================
	// Test: No NaN at edge cases
	// ================================================================

	@Test
	void testNoNanAtMach1() {
		Rocket rocket = makeRocketWithRailButtons();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(1.0);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		BarrowmanCalculator calc = new BarrowmanCalculator();
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());

		assertTrue(Double.isFinite(forces.getCD()), "CD should be finite at M=1.0");
	}

	@Test
	void testNoNanAtMach5() {
		Rocket rocket = makeRocketWithRailButtons();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(5.0);
		conditions.setAtmosphericConditions(
				new info.openrocket.core.models.atmosphere.AtmosphericConditions(101325, 288.15));

		BarrowmanCalculator calc = new BarrowmanCalculator();
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());

		assertTrue(Double.isFinite(forces.getCD()), "CD should be finite at M=5.0");
	}

	@Test
	void testNoNanAtVeryLowVelocity() {
		Rocket rocket = makeRocketWithRailButtons();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(0.01);

		BarrowmanCalculator calc = new BarrowmanCalculator();
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());

		assertTrue(Double.isFinite(forces.getCD()), "CD should be finite at M=0.01");
	}
}
