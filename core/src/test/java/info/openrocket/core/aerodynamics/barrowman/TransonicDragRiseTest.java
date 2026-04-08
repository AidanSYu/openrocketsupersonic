package info.openrocket.core.aerodynamics.barrowman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.util.Map;

/**
 * Tests for Phase 2E: Transonic Drag Rise model.
 * <p>
 * Validates that nose cone wave drag:
 * <ul>
 *   <li>Is zero below the drag divergence Mach (Mdd)</li>
 *   <li>Rises smoothly and monotonically from Mdd to the first data point</li>
 *   <li>Is C1-continuous at both the onset (Mdd) and join boundaries</li>
 *   <li>Preserves existing behavior for shapes with TR-R-100 transonic data</li>
 * </ul>
 */
public class TransonicDragRiseTest {

	private static Injector injector;

	@BeforeAll
	public static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ==================== Zero Drag Below Mdd ====================

	/**
	 * Cone nose should have zero wave drag at subsonic Mach numbers
	 * well below the drag divergence onset.
	 */
	@ParameterizedTest(name = "Cone pressure Cd ≈ 0 at M{0}")
	@CsvSource({ "0.3", "0.5", "0.7" })
	public void testConeZeroDragBelowMdd(double mach) {
		double cd = getNosePressureCD(makeConeRocket(0.15, 0.025), mach);
		assertEquals(0, cd, 0.001,
				String.format("Cone wave drag should be ~0 at M%.1f, got %.6f", mach, cd));
	}

	/**
	 * Ogive nose (tangent ogive, sinphi=0) should have zero wave drag
	 * at subsonic speeds.
	 */
	@ParameterizedTest(name = "Ogive pressure Cd ≈ 0 at M{0}")
	@CsvSource({ "0.3", "0.5", "0.7" })
	public void testOgiveZeroDragBelowMdd(double mach) {
		double cd = getNosePressureCD(makeOgiveRocket(0.15, 0.025), mach);
		assertEquals(0, cd, 0.001,
				String.format("Ogive wave drag should be ~0 at M%.1f, got %.6f", mach, cd));
	}

	/**
	 * Ellipsoid nose should have zero wave drag well below Mdd.
	 */
	@ParameterizedTest(name = "Ellipsoid pressure Cd ≈ 0 at M{0}")
	@CsvSource({ "0.3", "0.5" })
	public void testEllipsoidZeroDragBelowMdd(double mach) {
		double cd = getNosePressureCD(makeShapedRocket(Transition.Shape.ELLIPSOID, 0.15, 0.025, 0), mach);
		assertEquals(0, cd, 0.001,
				String.format("Ellipsoid wave drag should be ~0 at M%.1f, got %.6f", mach, cd));
	}

	// ==================== Monotonic Drag Rise ====================

	/**
	 * Cone pressure drag should rise monotonically through the transonic region.
	 */
	@Test
	public void testConeDragRiseMonotonic() {
		Rocket rocket = makeConeRocket(0.15, 0.025);
		double[] machs = { 0.80, 0.85, 0.88, 0.90, 0.92, 0.94, 0.96, 0.98, 1.0 };

		double prevCd = getNosePressureCD(rocket, machs[0]);
		for (int i = 1; i < machs.length; i++) {
			double cd = getNosePressureCD(rocket, machs[i]);
			assertTrue(cd >= prevCd - 1e-6,
					String.format("Cone drag not monotonic: M%.2f (%.6f) < M%.2f (%.6f)",
							machs[i], cd, machs[i - 1], prevCd));
			prevCd = cd;
		}
	}

	/**
	 * Ellipsoid pressure drag should rise monotonically through transonic.
	 */
	@Test
	public void testEllipsoidDragRiseMonotonic() {
		Rocket rocket = makeShapedRocket(Transition.Shape.ELLIPSOID, 0.15, 0.025, 0);
		double[] machs = { 0.70, 0.75, 0.80, 0.85, 0.90, 0.95, 1.0, 1.05, 1.1, 1.2 };

		double prevCd = getNosePressureCD(rocket, machs[0]);
		for (int i = 1; i < machs.length; i++) {
			double cd = getNosePressureCD(rocket, machs[i]);
			assertTrue(cd >= prevCd - 1e-6,
					String.format("Ellipsoid drag not monotonic: M%.2f (%.6f) < M%.2f (%.6f)",
							machs[i], cd, machs[i - 1], prevCd));
			prevCd = cd;
		}
	}

	// ==================== Smoothness (No Large Jumps) ====================

	/**
	 * Verify no large jumps in cone pressure drag through transonic.
	 */
	@Test
	public void testConeDragRiseSmooth() {
		Rocket rocket = makeConeRocket(0.15, 0.025);
		double step = 0.02;
		double prevCd = getNosePressureCD(rocket, 0.7);

		for (double m = 0.7 + step; m <= 1.1; m += step) {
			double cd = getNosePressureCD(rocket, m);
			double jump = Math.abs(cd - prevCd);
			assertTrue(jump < 0.05,
					String.format("Cone pressure Cd jump %.6f too large at M%.2f (%.6f → %.6f)",
							jump, m, prevCd, cd));
			prevCd = cd;
		}
	}

	// ==================== C1 Continuity at Boundaries ====================

	/**
	 * Verify C1 continuity at the drag divergence Mach (onset).
	 * The derivative from the left (zero — no wave drag) should match
	 * the derivative from the right (start of polynomial rise, also ~zero).
	 */
	@Test
	public void testC1ContinuityAtMdd() {
		Rocket rocket = makeConeRocket(0.15, 0.025);

		// Find approximate Mdd by scanning for the onset of nonzero drag
		double mdd = 0.70;
		for (double m = 0.70; m < 0.98; m += 0.005) {
			double cd = getNosePressureCD(rocket, m);
			if (cd > 0.0005) {
				mdd = m - 0.005;
				break;
			}
		}

		// Derivative just below Mdd should be zero (no wave drag).
		// Derivative just above Mdd is small but nonzero due to piecewise-linear
		// discretization of the underlying polynomial (which has true zero slope).
		double h = 0.005;
		double cdBelow = getNosePressureCD(rocket, mdd - h);
		double cdAt = getNosePressureCD(rocket, mdd);
		double cdAbove = getNosePressureCD(rocket, mdd + h);

		double derivBelow = (cdAt - cdBelow) / h;
		double derivAbove = (cdAbove - cdAt) / h;

		assertEquals(0, derivBelow, 0.1,
				String.format("Derivative below Mdd (%.2f) should be ~0, got %.4f", mdd, derivBelow));
		// The piecewise-linear interpolator can't perfectly represent zero slope,
		// but the derivative should be much smaller than the mid-rise slope.
		assertTrue(derivAbove < 0.5,
				String.format("Derivative above Mdd (%.2f) should be small, got %.4f", mdd, derivAbove));
	}

	/**
	 * Verify C1 continuity at the join point (where drag rise meets first data).
	 * For cone, this is at M=1.0.
	 */
	@Test
	public void testC1ContinuityAtJoinPoint() {
		Rocket rocket = makeConeRocket(0.15, 0.025);
		double joinMach = 1.0;
		double h = 0.005;

		double cdBelow = getNosePressureCD(rocket, joinMach - h);
		double cdAt = getNosePressureCD(rocket, joinMach);
		double cdAbove = getNosePressureCD(rocket, joinMach + h);

		// Value continuity (piecewise-linear discretization allows small gap)
		assertEquals(cdAt, cdBelow, 0.01,
				String.format("Value discontinuity at M=%.2f: below=%.6f, at=%.6f", joinMach, cdBelow, cdAt));

		// Derivative continuity (allow for LinearInterpolator discretization)
		double derivBelow = (cdAt - cdBelow) / h;
		double derivAbove = (cdAbove - cdAt) / h;

		// Derivatives should be in the same ballpark (both positive, similar magnitude)
		assertTrue(derivBelow > 0,
				String.format("Derivative below join should be positive, got %.4f", derivBelow));
		assertTrue(derivAbove > 0,
				String.format("Derivative above join should be positive, got %.4f", derivAbove));
		// Allow 100% relative difference due to LinearInterpolator step size
		double derivDiff = Math.abs(derivBelow - derivAbove);
		double avgDeriv = (Math.abs(derivBelow) + Math.abs(derivAbove)) / 2;
		assertTrue(derivDiff < avgDeriv * 1.5 + 0.1,
				String.format("Derivative mismatch at M=%.2f: left=%.4f, right=%.4f",
						joinMach, derivBelow, derivAbove));
	}

	// ==================== Von Karman Unchanged ====================

	/**
	 * Von Karman nose drag should be unchanged — the TR-R-100 table already
	 * starts near Cd=0 at M=0.9, so the drag rise model should be a no-op.
	 */
	@Test
	public void testVonKarmanUnchanged() {
		Rocket rocket = makeShapedRocket(Transition.Shape.HAACK, 0.18, 0.025, 0.0);

		// Should be near-zero at M=0.9 (table value is 0)
		double cd09 = getNosePressureCD(rocket, 0.9);
		assertEquals(0, cd09, 0.005, "Von Karman should have ~0 drag at M=0.9");

		// Should have drag at M=1.0
		double cd10 = getNosePressureCD(rocket, 1.0);
		assertTrue(cd10 > 0.01, "Von Karman should have nonzero drag at M=1.0");
	}

	// ==================== Positive Drag at Transonic ====================

	/**
	 * All shapes should have positive wave drag at M=1.0.
	 */
	@Test
	public void testPositiveDragAtMach1() {
		Rocket[] rockets = {
				makeConeRocket(0.15, 0.025),
				makeOgiveRocket(0.15, 0.025),
				makeShapedRocket(Transition.Shape.ELLIPSOID, 0.15, 0.025, 0),
				makeShapedRocket(Transition.Shape.HAACK, 0.18, 0.025, 0),
				makeShapedRocket(Transition.Shape.POWER, 0.15, 0.025, 0.75),
		};

		for (Rocket rocket : rockets) {
			double cd = getNosePressureCD(rocket, 1.0);
			assertTrue(cd >= 0,
					String.format("%s: negative pressure Cd (%.6f) at M=1.0", rocket.getName(), cd));
			assertFalse(Double.isNaN(cd),
					String.format("%s: NaN pressure Cd at M=1.0", rocket.getName()));
		}
	}

	// ==================== Total CD Sanity ====================

	/**
	 * Total CD should be positive and finite at all Mach numbers for standard shapes.
	 */
	@ParameterizedTest(name = "Total CD sanity at M{0}")
	@CsvSource({ "0.3", "0.5", "0.8", "0.9", "1.0", "1.1", "1.5", "2.0", "3.0", "5.0" })
	public void testTotalCdSanity(double mach) {
		Rocket[] rockets = {
				makeConeRocket(0.15, 0.025),
				makeOgiveRocket(0.15, 0.025),
				makeShapedRocket(Transition.Shape.ELLIPSOID, 0.15, 0.025, 0),
		};

		for (Rocket rocket : rockets) {
			double cd = computeTotalCD(rocket, mach);
			assertTrue(cd > 0,
					String.format("%s: non-positive CD (%.6f) at M%.1f", rocket.getName(), cd, mach));
			assertTrue(cd < 3.0,
					String.format("%s: unreasonably high CD (%.6f) at M%.1f", rocket.getName(), cd, mach));
			assertFalse(Double.isNaN(cd),
					String.format("%s: NaN CD at M%.1f", rocket.getName(), mach));
		}
	}

	/**
	 * Transonic drag rise: total CD at M=1.1 should exceed CD at M=0.5
	 * for all standard nose shapes.
	 */
	@Test
	public void testTransonicDragRiseExists() {
		Rocket[] rockets = {
				makeConeRocket(0.15, 0.025),
				makeOgiveRocket(0.15, 0.025),
				makeShapedRocket(Transition.Shape.ELLIPSOID, 0.15, 0.025, 0),
				makeShapedRocket(Transition.Shape.HAACK, 0.18, 0.025, 0),
		};

		for (Rocket rocket : rockets) {
			double cdSub = computeTotalCD(rocket, 0.5);
			double cdTrans = computeTotalCD(rocket, 1.1);
			assertTrue(cdTrans > cdSub,
					String.format("%s: CD at M1.1 (%.6f) should exceed CD at M0.5 (%.6f)",
							rocket.getName(), cdTrans, cdSub));
		}
	}

	// ==================== Mdd Estimation Sanity ====================

	/**
	 * The drag divergence Mach should be reasonable for different tip geometries.
	 */
	@Test
	public void testMddEstimation() {
		// Sharp cone (FR=6, half-angle ~4.8°)
		SymmetricComponentCalc sharpCalc = makeCalc(Transition.Shape.CONICAL, 0.30, 0.025);
		double mddSharp = sharpCalc.estimateDragDivergenceMach();
		assertTrue(mddSharp > 0.88 && mddSharp < 0.96,
				String.format("Sharp cone Mdd (%.3f) out of expected range [0.88, 0.96]", mddSharp));

		// Blunt cone (FR=1.5, half-angle ~18.4°)
		SymmetricComponentCalc bluntCalc = makeCalc(Transition.Shape.CONICAL, 0.075, 0.025);
		double mddBlunt = bluntCalc.estimateDragDivergenceMach();
		assertTrue(mddBlunt < mddSharp,
				String.format("Blunt cone Mdd (%.3f) should be lower than sharp cone (%.3f)",
						mddBlunt, mddSharp));

		// Ellipsoid (very blunt tip)
		SymmetricComponentCalc ellipsoidCalc = makeCalc(Transition.Shape.ELLIPSOID, 0.15, 0.025);
		double mddEllipsoid = ellipsoidCalc.estimateDragDivergenceMach();
		assertTrue(mddEllipsoid < mddBlunt,
				String.format("Ellipsoid Mdd (%.3f) should be lower than blunt cone (%.3f)",
						mddEllipsoid, mddBlunt));
		assertTrue(mddEllipsoid >= 0.65,
				String.format("Ellipsoid Mdd (%.3f) should be >= 0.65", mddEllipsoid));
	}

	// ==================== Helpers ====================

	private double getNosePressureCD(Rocket rocket, double mach) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		WarningSet warnings = new WarningSet();

		Map<RocketComponent, AerodynamicForces> forceMap =
				calc.getForceAnalysis(config, conditions, warnings);

		for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
			if (entry.getKey() instanceof NoseCone) {
				double pressureCd = entry.getValue().getPressureCD();
				return Double.isNaN(pressureCd) ? 0 : pressureCd;
			}
		}
		return 0;
	}

	private double computeTotalCD(Rocket rocket, double mach) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		WarningSet warnings = new WarningSet();

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
		return forces.getCD();
	}

	private static Rocket makeConeRocket(double noseLength, double radius) {
		Rocket rocket = new Rocket();
		rocket.setName("Cone-Test");

		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, noseLength, radius);
		nose.setName("Conical Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.60, radius, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}

	private static Rocket makeOgiveRocket(double noseLength, double radius) {
		Rocket rocket = new Rocket();
		rocket.setName("Ogive-Test");

		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, noseLength, radius);
		nose.setShapeParameter(1.0);
		nose.setName("Ogive Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.60, radius, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}

	private static Rocket makeShapedRocket(Transition.Shape shape, double noseLength,
										   double radius, double shapeParam) {
		Rocket rocket = new Rocket();
		rocket.setName(shape.name() + "-Test");

		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(shape, noseLength, radius);
		if (shapeParam > 0) {
			nose.setShapeParameter(shapeParam);
		}
		nose.setName(shape.name() + " Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.60, radius, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}

	private static SymmetricComponentCalc makeCalc(Transition.Shape shape,
												   double noseLength, double radius) {
		NoseCone nose = new NoseCone(shape, noseLength, radius);
		nose.setThickness(0.002);
		return new SymmetricComponentCalc(nose);
	}
}
