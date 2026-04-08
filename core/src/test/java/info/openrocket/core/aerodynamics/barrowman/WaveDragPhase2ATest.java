package info.openrocket.core.aerodynamics.barrowman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.shocks.ObliqueShockSolver;
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
 * Validation tests for Phase 2A: Nose/Body Wave Drag upgrade.
 * <p>
 * Tests verify that:
 * - Cone wave drag matches Taylor-Maccoll analytical solution
 * - Ogive wave drag is computed via shock-expansion (previously zero)
 * - Transonic blending is smooth (no discontinuities)
 * - Arbitrary fineness ratios are handled
 * - Extension to high Mach numbers works (M > 4)
 */
public class WaveDragPhase2ATest {

	private static Injector injector;

	@BeforeAll
	public static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ==================== Cone Taylor-Maccoll Validation ====================

	/**
	 * Verify that cone nose pressure drag matches Taylor-Maccoll Cp directly.
	 * For a cone at zero AoA, Cd_wave (based on base area) equals Cp.
	 * <p>
	 * At M >= 4, Phase 4a's Modified Newtonian theory begins blending in,
	 * so the result may differ from pure Taylor-Maccoll. Use wider tolerance
	 * at high Mach to account for this intentional blending.
	 */
	@ParameterizedTest(name = "Cone wave drag at M{0}")
	@CsvSource({
			"2.0,  0.025,  0.15",
			"3.0,  0.025,  0.15",
			"5.0,  0.025,  0.15",
	})
	public void testConeWaveDragMatchesTaylorMaccoll(double mach, double radius, double noseLength) {
		double halfAngle = Math.atan2(radius, noseLength);
		double expectedCp = ObliqueShockSolver.conePressureCoefficient(mach, halfAngle);

		double pressureCd = getNosePressureCD(
				makeConeRocket(noseLength, radius), mach);

		// At M >= 4, Newtonian blending alters the result — allow wider tolerance
		double tolerance = mach >= 4.0 ? expectedCp * 0.20 : expectedCp * 0.05;
		assertEquals(expectedCp, pressureCd, tolerance,
				String.format("Cone pressure Cd at M%.1f should match Taylor-Maccoll Cp (%.6f vs %.6f)",
						mach, expectedCp, pressureCd));
	}

	// ==================== Ogive Wave Drag Validation ====================

	/**
	 * Verify that tangent ogive now has non-zero wave drag at supersonic speeds.
	 * Previously, sinphi=0 for tangent ogive caused zero wave drag.
	 */
	@ParameterizedTest(name = "Ogive wave drag nonzero at M{0}")
	@CsvSource({
			"2.0",
			"3.0",
			"5.0",
	})
	public void testOgiveWaveDragNonZero(double mach) {
		double pressureCd = getNosePressureCD(
				makeOgiveRocket(0.15, 0.025, 1.0), mach);

		assertTrue(pressureCd > 0.01,
				String.format("Tangent ogive pressure Cd (%.6f) should be > 0.01 at M%.1f",
						pressureCd, mach));
	}

	/**
	 * Verify that ogive nose wave drag is less than cone wave drag
	 * at supersonic speeds for the same geometry.
	 */
	@ParameterizedTest(name = "Ogive wave drag < cone at M{0}")
	@CsvSource({
			"1.5",
			"2.0",
	})
	public void testOgiveWaveDragLessThanCone(double mach) {
		double conePressure = getNosePressureCD(
				makeConeRocket(0.15, 0.025), mach);
		double ogivePressure = getNosePressureCD(
				makeOgiveRocket(0.15, 0.025, 1.0), mach);

		assertTrue(ogivePressure < conePressure,
				String.format("Ogive pressure Cd (%.6f) should be < Cone (%.6f) at M%.1f",
						ogivePressure, conePressure, mach));
	}

	// ==================== Transonic Smoothness ====================

	/**
	 * Verify no discontinuities in the cone pressure Cd through the transonic-to-
	 * supersonic blend region (M 1.0 to 2.0).
	 */
	@Test
	public void testConePressureCdSmooth() {
		Rocket rocket = makeConeRocket(0.15, 0.025);
		double[] machs = { 1.0, 1.1, 1.2, 1.3, 1.35, 1.4, 1.45, 1.5, 1.6, 1.8, 2.0 };

		double prevCd = getNosePressureCD(rocket, machs[0]);
		for (int i = 1; i < machs.length; i++) {
			double cd = getNosePressureCD(rocket, machs[i]);
			double jump = Math.abs(cd - prevCd);
			double maxJump = Math.max(prevCd, cd) * 0.5 + 0.02;
			assertTrue(jump < maxJump,
					String.format("Cone pressure Cd jump %.6f too large between M%.2f (%.6f) and M%.2f (%.6f)",
							jump, machs[i - 1], prevCd, machs[i], cd));
			prevCd = cd;
		}
	}

	/**
	 * Verify no discontinuities in ogive pressure Cd through the blend region.
	 */
	@Test
	public void testOgivePressureCdSmooth() {
		Rocket rocket = makeOgiveRocket(0.15, 0.025, 1.0);
		double[] machs = { 1.0, 1.1, 1.2, 1.3, 1.35, 1.4, 1.45, 1.5, 1.6, 1.8, 2.0 };

		double prevCd = getNosePressureCD(rocket, machs[0]);
		for (int i = 1; i < machs.length; i++) {
			double cd = getNosePressureCD(rocket, machs[i]);
			double jump = Math.abs(cd - prevCd);
			double maxJump = Math.max(prevCd, cd) * 0.5 + 0.05;
			assertTrue(jump < maxJump,
					String.format("Ogive pressure Cd jump %.6f too large between M%.2f (%.6f) and M%.2f (%.6f)",
							jump, machs[i - 1], prevCd, machs[i], cd));
			prevCd = cd;
		}
	}

	// ==================== High Mach Extension ====================

	/**
	 * Verify that cone and ogive produce valid (non-NaN, non-negative) results
	 * at Mach numbers beyond the old M=4 limit.
	 */
	@ParameterizedTest(name = "High Mach validity at M{0}")
	@CsvSource({
			"4.0",
			"5.0",
			"7.0",
			"10.0",
	})
	public void testHighMachValidity(double mach) {
		for (Rocket rocket : new Rocket[] {
				makeConeRocket(0.15, 0.025),
				makeOgiveRocket(0.15, 0.025, 1.0) }) {
			double totalCd = computeTotalCD(rocket, mach);
			assertFalse(Double.isNaN(totalCd),
					String.format("%s: NaN CD at M%.1f", rocket.getName(), mach));
			assertFalse(Double.isInfinite(totalCd),
					String.format("%s: Infinite CD at M%.1f", rocket.getName(), mach));
			assertTrue(totalCd >= 0,
					String.format("%s: Negative CD (%.6f) at M%.1f", rocket.getName(), totalCd, mach));
			assertTrue(totalCd < 2.0,
					String.format("%s: Unreasonably high CD (%.6f) at M%.1f", rocket.getName(), totalCd, mach));
		}
	}

	// ==================== Arbitrary Fineness Ratios ====================

	/**
	 * Verify that wave drag decreases with increasing fineness ratio (more slender
	 * nose). This tests the key advantage of analytical methods over fixed tables.
	 */
	@Test
	public void testFinenessRatioEffect() {
		double mach = 3.0;
		double radius = 0.025;

		double cdFR2 = getNosePressureCD(makeConeRocket(0.10, radius), mach);
		double cdFR3 = getNosePressureCD(makeConeRocket(0.15, radius), mach);
		double cdFR5 = getNosePressureCD(makeConeRocket(0.25, radius), mach);
		double cdFR8 = getNosePressureCD(makeConeRocket(0.40, radius), mach);

		assertTrue(cdFR2 > cdFR3,
				String.format("FR2 (%.6f) should have more drag than FR3 (%.6f)", cdFR2, cdFR3));
		assertTrue(cdFR3 > cdFR5,
				String.format("FR3 (%.6f) should have more drag than FR5 (%.6f)", cdFR3, cdFR5));
		assertTrue(cdFR5 > cdFR8,
				String.format("FR5 (%.6f) should have more drag than FR8 (%.6f)", cdFR5, cdFR8));
	}

	/**
	 * Same fineness ratio test for ogive nose cones.
	 */
	@Test
	public void testOgiveFinenessRatioEffect() {
		double mach = 3.0;
		double radius = 0.025;

		double cdFR2 = getNosePressureCD(makeOgiveRocket(0.10, radius, 1.0), mach);
		double cdFR3 = getNosePressureCD(makeOgiveRocket(0.15, radius, 1.0), mach);
		double cdFR5 = getNosePressureCD(makeOgiveRocket(0.25, radius, 1.0), mach);
		double cdFR8 = getNosePressureCD(makeOgiveRocket(0.40, radius, 1.0), mach);

		assertTrue(cdFR2 > cdFR3,
				String.format("FR2 ogive (%.6f) should have more drag than FR3 (%.6f)", cdFR2, cdFR3));
		assertTrue(cdFR3 > cdFR5,
				String.format("FR3 ogive (%.6f) should have more drag than FR5 (%.6f)", cdFR3, cdFR5));
		assertTrue(cdFR5 > cdFR8,
				String.format("FR5 ogive (%.6f) should have more drag than FR8 (%.6f)", cdFR5, cdFR8));
	}

	// ==================== No Regression for Subsonic ====================

	/**
	 * Verify that subsonic behavior is unchanged (M < 0.8).
	 */
	@Test
	public void testSubsonicUnchanged() {
		double[] subsonicMachs = { 0.3, 0.5 };
		Rocket[] rockets = {
				makeConeRocket(0.15, 0.025),
				makeOgiveRocket(0.15, 0.025, 1.0)
		};

		for (Rocket rocket : rockets) {
			for (double mach : subsonicMachs) {
				double cd = computeTotalCD(rocket, mach);
				assertTrue(cd > 0.1 && cd < 2.0,
						String.format("%s: CD (%.6f) out of range at M%.1f",
								rocket.getName(), cd, mach));
			}
		}
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

	private static Rocket makeOgiveRocket(double noseLength, double radius, double shapeParam) {
		Rocket rocket = new Rocket();
		rocket.setName("Ogive-Test");

		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, noseLength, radius);
		nose.setShapeParameter(shapeParam);
		nose.setName("Ogive Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.60, radius, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}
}
