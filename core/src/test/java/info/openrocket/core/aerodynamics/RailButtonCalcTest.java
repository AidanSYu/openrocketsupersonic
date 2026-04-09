package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.logging.WarningSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.RailButtonCalc;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.RailButton;
import info.openrocket.core.rocketcomponent.LaunchLug;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.TestRockets;

public class RailButtonCalcTest {
	protected final double EPSILON = 0.0001;

	private static Injector injector;

	@BeforeAll
	public static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();

		injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	public void testRailButtons() {

		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		// Get the body tube...
		BodyTube tube = (BodyTube) rocket.getChild(0).getChild(1);

		// Replace the launch lug with a (single) railbutton
		LaunchLug lug = (LaunchLug) tube.getChild(1);
		rocket.removeChild(lug);

		RailButton button = new RailButton();
		tube.addChild(button);

		// Button parameters from Binder Design standard 1010
		button.setOuterDiameter(0.011);
		button.setInnerDiameter(0.006);

		button.setBaseHeight(0.002);
		button.setFlangeHeight(0.002);
		button.setTotalHeight(0.008);

		button.setAxialMethod(AxialMethod.ABSOLUTE);
		button.setAxialOffset(1.0);

		// Set up flight conditions at subsonic speed (below Phase 7c wave drag blend)
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(0.8);

		BarrowmanCalculator barrowmanObj = new BarrowmanCalculator();
		RailButtonCalc calcObj = new RailButtonCalc(button);

		// Compute pressure CD from the rail button calculator
		WarningSet warnings = new WarningSet();
		double testcd = calcObj.calculatePressureCD(conditions,
				barrowmanObj.calculateStagnationCD(conditions.getMach()), 0, warnings);

		// Rail button CD should be positive, finite, and small relative to the rocket
		assertTrue(testcd > 0, "Rail button CD should be positive, got " + testcd);
		assertTrue(testcd < 0.1, "Rail button CD should be small, got " + testcd);
		assertFalse(Double.isNaN(testcd), "Rail button CD should not be NaN");

		// Regression baseline: verify against current model output
		assertEquals(0.01729, testcd, 0.001, "Rail button CD regression baseline");
	}
}
