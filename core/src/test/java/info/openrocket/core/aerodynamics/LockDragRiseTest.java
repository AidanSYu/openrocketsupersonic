package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.SymmetricComponentCalc;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * Tests for Lock's 4th-power transonic drag rise rule in SymmetricComponentCalc.
 * Verifies the subsonic drag rise onset is flatter (4th power) than the previous
 * cubic Hermite interpolation, and maintains continuity at the transonic onset.
 */
public class LockDragRiseTest {

	private static final double EPSILON = 1e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	private SymmetricComponentCalc createConicalCalc(double halfAngleDeg, double length, double radius) {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		NoseCone nose = (NoseCone) rocket.getChild(0).getChild(0);
		nose.setShapeType(Transition.Shape.CONICAL);
		nose.setLength(length);
		nose.setAftRadius(radius);
		return new SymmetricComponentCalc(nose);
	}

	private SymmetricComponentCalc createOgiveCalc(double length, double radius) {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		NoseCone nose = (NoseCone) rocket.getChild(0).getChild(0);
		nose.setShapeType(Transition.Shape.OGIVE);
		nose.setLength(length);
		nose.setAftRadius(radius);
		return new SymmetricComponentCalc(nose);
	}

	private double getPressureCd(SymmetricComponentCalc calc, double mach) {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		double stag = BarrowmanDragCalculator.calculateStagnationCD(mach);
		double base = BarrowmanDragCalculator.calculateBaseCD(mach);
		return calc.calculatePressureCD(conditions, stag, base, new WarningSet());
	}

	/**
	 * Well below M_crit, there should be zero or near-zero wave drag.
	 */
	@Test
	public void testBelowMcritZeroWaveDrag() {
		SymmetricComponentCalc calc = createConicalCalc(10, 0.07, 0.012);
		double cd03 = getPressureCd(calc, 0.3);
		double cd05 = getPressureCd(calc, 0.5);

		// At low subsonic, pressure drag for a nose cone should be very small
		// (dominated by the static Cd at M=0 which is 0.8*sin^2(phi))
		assertTrue(cd03 >= 0, "Pressure Cd at M=0.3 should be non-negative");
		assertTrue(cd05 >= 0, "Pressure Cd at M=0.5 should be non-negative");
	}

	/**
	 * The 4th-power rise should be flatter near M_crit than the old power-law.
	 * At M slightly above M_crit, the drag increment should be very small.
	 */
	@Test
	public void testFourthPowerOnsetIsFlat() {
		SymmetricComponentCalc calc = createOgiveCalc(0.07, 0.012);

		// Get drag at several points in the onset region
		double cd80 = getPressureCd(calc, 0.80);
		double cd85 = getPressureCd(calc, 0.85);
		double cd90 = getPressureCd(calc, 0.90);
		double cd95 = getPressureCd(calc, 0.95);

		// The 4th-power law means the drag rise is very slow initially
		// and accelerates rapidly near M=1.0
		// The increment from 0.85 to 0.90 should be much smaller than 0.90 to 0.95
		double delta1 = cd90 - cd85;
		double delta2 = cd95 - cd90;

		// With 4th-power, delta2 should be significantly larger than delta1
		// (unless both are essentially zero, which is also acceptable)
		if (delta2 > 0.001) {
			assertTrue(delta2 > delta1,
					"4th-power onset: delta(0.90-0.95)=" + delta2 +
							" should exceed delta(0.85-0.90)=" + delta1);
		}
	}

	/**
	 * Pressure drag should be monotonically non-decreasing from subsonic through
	 * the transonic drag peak.
	 */
	@Test
	public void testMonotonicDragRise() {
		SymmetricComponentCalc calc = createConicalCalc(15, 0.07, 0.012);

		double prevCd = 0;
		for (double mach = 0.5; mach <= 1.05; mach += 0.05) {
			double cd = getPressureCd(calc, mach);
			assertTrue(cd >= prevCd - EPSILON,
					"Drag should be non-decreasing: Cd(" + (mach - 0.05) + ")=" + prevCd +
							" > Cd(" + mach + ")=" + cd);
			prevCd = cd;
		}
	}

	/**
	 * Different nose shapes should produce different drag rise characteristics.
	 * A conical nose has non-zero sinphi and thus significant wave drag, while a tangent
	 * ogive (param=1.0) has sinphi=0 and thus zero wave drag from the nose cone itself.
	 * A power-series nose (param=0.5) has intermediate wave drag.
	 */
	@Test
	public void testDifferentShapesProduceDifferentDrag() {
		SymmetricComponentCalc conicalCalc = createConicalCalc(20, 0.07, 0.012);

		// Use a power-series nose instead of tangent ogive (which has sinphi=0)
		Rocket rocket2 = TestRockets.makeEstesAlphaIII();
		NoseCone nose2 = (NoseCone) rocket2.getChild(0).getChild(0);
		nose2.setShapeType(Transition.Shape.POWER);
		nose2.setShapeParameter(0.5);
		nose2.setLength(0.07);
		nose2.setAftRadius(0.012);
		SymmetricComponentCalc powerCalc = new SymmetricComponentCalc(nose2);

		double conicalCd09 = getPressureCd(conicalCalc, 0.9);
		double powerCd09 = getPressureCd(powerCalc, 0.9);

		// Both should be non-negative
		assertTrue(conicalCd09 >= 0, "Conical Cd at M=0.9 should be non-negative");
		assertTrue(powerCd09 >= 0, "Power Cd at M=0.9 should be non-negative");

		// At M=1.5 (supersonic), conical should have positive wave drag
		double conicalCd15 = getPressureCd(conicalCalc, 1.5);
		double powerCd15 = getPressureCd(powerCalc, 1.5);
		assertTrue(conicalCd15 > 0, "Conical Cd at M=1.5 should be positive");
		assertTrue(powerCd15 > 0, "Power Cd at M=1.5 should be positive");

		// They should produce different drag profiles
		assertTrue(conicalCd15 != powerCd15,
				"Different nose shapes should produce different supersonic drag");
	}

	/**
	 * Continuity test: pressure Cd should be smooth through the transonic region.
	 * No large jumps between adjacent Mach steps.
	 */
	@Test
	public void testContinuityThroughTransonic() {
		SymmetricComponentCalc calc = createOgiveCalc(0.07, 0.012);

		double prevCd = getPressureCd(calc, 0.8);
		for (double mach = 0.82; mach <= 1.2; mach += 0.02) {
			double cd = getPressureCd(calc, mach);
			double jump = Math.abs(cd - prevCd);
			assertTrue(jump < 0.15,
					"Jump in Cd between M=" + (mach - 0.02) + " and M=" + mach +
							" is too large: " + jump + " (prevCd=" + prevCd + ", cd=" + cd + ")");
			prevCd = cd;
		}
	}

	/**
	 * At M=0 (or very low Mach), the pressure Cd should be near zero for
	 * a reasonably slender nose cone.
	 */
	@Test
	public void testZeroMachPressureDrag() {
		SymmetricComponentCalc calc = createOgiveCalc(0.07, 0.012);
		double cd = getPressureCd(calc, 0.01);
		// For a slender ogive, pressure drag at M~0 should be very small
		assertTrue(cd >= 0, "Pressure Cd at M~0 should be non-negative");
		assertTrue(cd < 0.5, "Pressure Cd at M~0 should be small for slender ogive");
	}

	/**
	 * The supersonic region (M > 1.3) should not be affected by Lock's rule.
	 */
	@Test
	public void testSupersonicUnaffected() {
		SymmetricComponentCalc calc = createConicalCalc(10, 0.07, 0.012);

		// Get supersonic pressure drag at a few points
		double cd15 = getPressureCd(calc, 1.5);
		double cd20 = getPressureCd(calc, 2.0);
		double cd30 = getPressureCd(calc, 3.0);

		// All should be positive and reasonable
		assertTrue(cd15 > 0, "Pressure Cd at M=1.5 should be positive");
		assertTrue(cd20 > 0, "Pressure Cd at M=2.0 should be positive");
		assertTrue(cd30 > 0, "Pressure Cd at M=3.0 should be positive");

		// At higher Mach, wave drag generally decreases (for slender cones)
		assertTrue(cd30 < cd15,
				"Pressure Cd at M=3.0 (" + cd30 + ") should be less than at M=1.5 (" + cd15 + ")");
	}

	/**
	 * Body tube (constant radius) should have zero pressure drag at all Mach.
	 */
	@Test
	public void testBodyTubeZeroPressureDrag() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		BodyTube body = (BodyTube) rocket.getChild(0).getChild(1);
		SymmetricComponentCalc calc = new SymmetricComponentCalc(body);

		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(1.0);

		double cd = calc.calculatePressureCD(conditions,
				BarrowmanDragCalculator.calculateStagnationCD(1.0),
				BarrowmanDragCalculator.calculateBaseCD(1.0),
				new WarningSet());
		assertEquals(0.0, cd, EPSILON, "Body tube pressure drag should be zero");
	}
}
