package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.util.Map;

/**
 * Guards that every nose shape's pressure-drag curve actually extends through
 * the supersonic/hypersonic envelope instead of freezing at the last empirical
 * table key.
 * <p>
 * {@code SymmetricComponentCalc.extendWithShockExpansion} cannot use the
 * shock-expansion strip march on shapes with a cusp-like mathematical tip (Von
 * Karman 52.5 deg, LV-Haack 56.1 deg, POWER p=0.5 84.3 deg, ellipsoid). It used
 * to bail out entirely for those, which left the interpolator ending at its last
 * empirical key -- and {@code LinearInterpolator} constant-extrapolates beyond
 * its final point. The result was a nose Cd frozen flat from M ~ 2-3 all the way
 * to M 10 on some of the most common high-power nose shapes, where wave drag
 * should instead fall toward the Modified Newtonian limit.
 * <p>
 * These assertions are deliberately weak (a shape-independent "the curve is not
 * a horizontal line, and it is not rising steeply") so that they constrain the
 * defect without pinning any particular drag model.
 */
public class NoseWaveDragHighMachExtensionTest {

	private static final double NOSE_LENGTH = 0.15;
	private static final double RADIUS = 0.025;

	@BeforeAll
	public static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@ParameterizedTest(name = "{0} p={1} extends past the empirical table")
	@CsvSource({
			"CONICAL,   0.0",
			"OGIVE,     1.0",
			"OGIVE,     0.75",
			"HAACK,     0.0",
			"HAACK,     0.333",
			"POWER,     0.25",
			"POWER,     0.5",
			"POWER,     0.75",
			"PARABOLIC, 0.6",
			"PARABOLIC, 1.0",
			"ELLIPSOID, 0.0",
	})
	public void testNoseCdIsNotFrozenAtHighMach(Transition.Shape shape, double param) {
		double cd4 = nosePressureCd(shape, param, 4.0);
		double cd10 = nosePressureCd(shape, param, 10.0);

		assertTrue(cd4 > 0, String.format(
				"%s p=%.3f: pressure Cd at M4 should be positive (got %.6f)", shape, param, cd4));

		// The curve must not be a horizontal constant-extrapolation.
		double relChange = Math.abs(cd10 - cd4) / cd4;
		assertTrue(relChange > 0.01, String.format(
				"%s p=%.3f: pressure Cd is frozen between M4 (%.6f) and M10 (%.6f) -- "
						+ "the interpolator is constant-extrapolating past its last key",
				shape, param, cd4, cd10));
	}

	@ParameterizedTest(name = "{0} p={1} stays bounded at hypersonic Mach")
	@CsvSource({
			"CONICAL,   0.0",
			"OGIVE,     1.0",
			"HAACK,     0.0",
			"HAACK,     0.333",
			"POWER,     0.25",
			"POWER,     0.5",
			"POWER,     0.75",
			"PARABOLIC, 0.6",
			"PARABOLIC, 1.0",
			"ELLIPSOID, 0.0",
	})
	public void testNoseCdStaysPhysicalAtHighMach(Transition.Shape shape, double param) {
		double cd2 = nosePressureCd(shape, param, 2.0);
		double cd10 = nosePressureCd(shape, param, 10.0);

		// Wave drag must not grow without bound; Newtonian Cp_max rises only
		// ~2% from M2 to the M -> infinity limit, so any large increase means the
		// extension has gone unphysical.
		assertTrue(cd10 < 1.5 * cd2, String.format(
				"%s p=%.3f: pressure Cd rose from %.6f at M2 to %.6f at M10", shape, param, cd2, cd10));
		assertTrue(cd10 > 0, String.format(
				"%s p=%.3f: pressure Cd went non-positive at M10 (%.6f)", shape, param, cd10));
	}

	private double nosePressureCd(Transition.Shape shape, double param, double mach) {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(shape, NOSE_LENGTH, RADIUS);
		if (shape.usesParameter()) {
			nose.setShapeParameter(param);
		}
		nose.setThickness(0.002);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.60, RADIUS, 0.001);
		stage.addChild(body);
		rocket.enableEvents();

		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);

		Map<RocketComponent, AerodynamicForces> forceMap =
				calc.getForceAnalysis(config, conditions, new WarningSet());
		for (Map.Entry<RocketComponent, AerodynamicForces> e : forceMap.entrySet()) {
			if (e.getKey() instanceof NoseCone) {
				double cd = e.getValue().getPressureCD();
				return Double.isNaN(cd) ? 0 : cd;
			}
		}
		return 0;
	}
}
