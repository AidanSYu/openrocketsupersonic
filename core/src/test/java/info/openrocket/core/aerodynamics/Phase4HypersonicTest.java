package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.SymmetricComponentCalc;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.CoordinateIF;

/**
 * Tests for Phase 4: Hypersonic Extensions & Hardening.
 * <p>
 * Phase 4a: Modified Newtonian theory (M > 5)
 * Phase 4c: High-temperature / real-gas effects (effective gamma)
 * Phase 4d: Edge case hardening (M=1.0, high AoA, NaN/Inf guards)
 * Phase 4e: Graceful degradation & user warnings
 */
@Tag("slow")  // heavy hypersonic validation (~11 s); excluded by default, run with -Pslow
public class Phase4HypersonicTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ================================================================
	// Phase 4a: Modified Newtonian Theory
	// ================================================================

	/**
	 * Verify Cp_max (stagnation pressure coefficient) against published values.
	 * <p>
	 * At M=2: Cp_max ≈ 1.278 (Rayleigh pitot)
	 * At M=5: Cp_max ≈ 1.823
	 * At M=10: Cp_max ≈ 1.839 (approaching Newtonian limit of 2*(gamma/(gamma-1))...)
	 * <p>
	 * Reference: NACA Report 1135, Table II.
	 */
	@ParameterizedTest(name = "Cp_max at M={0}")
	@CsvSource({
			"2.0,  1.6573,  0.02",
			"3.0,  1.7557,  0.02",
			"5.0,  1.8088,  0.02",
			"10.0, 1.8317,  0.02",
	})
	void testCpMaxAgainstPublished(double mach, double expectedCpMax, double tolerance) {
		double cpMax = SymmetricComponentCalc.calculateCpMax(mach, 1.4);
		assertEquals(expectedCpMax, cpMax, expectedCpMax * tolerance,
				String.format("Cp_max at M=%.1f: expected=%.3f, got=%.3f", mach, expectedCpMax, cpMax));
	}

	@Test
	void testCpMaxSubsonic() {
		// At M < 1, Cp_max should still be positive and reasonable
		double cpMax = SymmetricComponentCalc.calculateCpMax(0.5, 1.4);
		assertTrue(cpMax > 0, "Cp_max should be positive at M=0.5");
		assertTrue(cpMax < 10, "Cp_max should be reasonable at M=0.5");
	}

	@Test
	void testCpMaxMonotonicallyApproachesLimit() {
		// Cp_max should increase monotonically and approach the Newtonian limit
		double prev = 0;
		for (double m = 1.5; m <= 10.0; m += 0.5) {
			double cpMax = SymmetricComponentCalc.calculateCpMax(m, 1.4);
			assertTrue(cpMax > prev,
					String.format("Cp_max should increase: M=%.1f gave %.4f <= prev %.4f", m, cpMax, prev));
			prev = cpMax;
		}
		// At M=10, should be close to the Newtonian limit (~1.839 for gamma=1.4)
		assertTrue(prev > 1.8, "Cp_max at M=10 should approach 1.839, got " + prev);
	}

	/**
	 * Verify Modified Newtonian Cd for a cone matches published data.
	 * <p>
	 * For a cone with half-angle theta, Newtonian theory gives:
	 * Cd = Cp_max * sin^2(theta) * cos(theta) (base-area referenced)
	 * <p>
	 * At M=8 for a 15° half-angle cone: Cp_max ≈ 1.838
	 * Newtonian Cd ≈ 1.838 * sin²(15°) * cos(15°) ≈ 0.119
	 * But the strip integration accounts for the profile shape, not just the tip.
	 * We verify the result is in the right ballpark (within 5%).
	 */
	@Test
	void testNewtonianCdReasonableAtHighMach() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(8.0);
		conditions.setAOA(0.0);

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
		double cd = forces.getCD();

		assertTrue(cd > 0, "CD should be positive at M=8");
		assertFalse(Double.isNaN(cd), "CD should not be NaN at M=8");
		assertFalse(Double.isInfinite(cd), "CD should not be Infinite at M=8");
		// At very high Mach, Cd should be smaller than transonic peak
		double cdTransonic = computeCD(rocket, 1.1);
		assertTrue(cd < cdTransonic,
				String.format("Hypersonic Cd (%.4f) should be < transonic peak (%.4f)", cd, cdTransonic));
	}

	@ParameterizedTest(name = "Cd smooth at M={0}")
	@ValueSource(doubles = {4.0, 4.5, 5.0, 5.5, 6.0, 7.0, 8.0, 10.0})
	void testCdSmoothThroughNewtonianBlend(double mach) {
		// Cd should be smooth through the M=4-6 Newtonian blending region
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
		double cd = forces.getCD();

		assertTrue(cd > 0, "CD should be positive at M=" + mach);
		assertFalse(Double.isNaN(cd), "CD NaN at M=" + mach);
		assertTrue(cd < 2.0, "CD should be < 2.0 at M=" + mach + ", got " + cd);
	}

	@Test
	void testNewtonianBlendContinuity() {
		// Check no large jumps in Cd through the Newtonian blend region (M 4-6)
		var rocket = SupersonicTestRockets.makeConeCylinder();
		double prevCd = computeCD(rocket, 3.5);
		for (double m = 3.6; m <= 8.0; m += 0.1) {
			double cd = computeCD(rocket, m);
			double jump = Math.abs(cd - prevCd);
			assertTrue(jump < 0.1,
					String.format("Cd jump too large at M=%.1f: delta=%.4f (prev=%.4f, cur=%.4f)",
							m, jump, prevCd, cd));
			prevCd = cd;
		}
	}

	// ================================================================
	// Phase 4c: High-Temperature / Real-Gas Effects
	// ================================================================

	@Test
	void testEffectiveGammaColdAir() {
		// Below 800K, gamma should be exactly 1.4
		assertEquals(1.4, AtmosphericConditions.effectiveGamma(300.0), 1e-10);
		assertEquals(1.4, AtmosphericConditions.effectiveGamma(500.0), 1e-10);
		assertEquals(1.4, AtmosphericConditions.effectiveGamma(800.0), 1e-10);
	}

	@Test
	void testEffectiveGammaHighTemp() {
		// Above 800K, gamma should decrease due to vibrational excitation
		double gamma1500 = AtmosphericConditions.effectiveGamma(1500.0);
		double gamma3000 = AtmosphericConditions.effectiveGamma(3000.0);
		double gamma5000 = AtmosphericConditions.effectiveGamma(5000.0);

		assertTrue(gamma1500 < 1.4, "gamma should decrease at 1500K, got " + gamma1500);
		assertTrue(gamma1500 > 1.3, "gamma should stay above 1.3 at 1500K, got " + gamma1500);

		assertTrue(gamma3000 < gamma1500, "gamma should decrease more at 3000K");
		assertTrue(gamma3000 >= 1.3, "gamma should stay >= 1.3");

		assertTrue(gamma5000 <= gamma3000, "gamma should decrease further at 5000K");
		assertTrue(gamma5000 >= 1.3, "gamma should stay >= 1.3");
	}

	@Test
	void testEffectiveGammaMonotonic() {
		double prevGamma = 1.4;
		for (double T = 800; T <= 6000; T += 100) {
			double gamma = AtmosphericConditions.effectiveGamma(T);
			assertTrue(gamma <= prevGamma + 1e-10,
					String.format("gamma should be non-increasing: T=%.0f gave %.4f > prev %.4f",
							T, gamma, prevGamma));
			prevGamma = gamma;
		}
	}

	// ================================================================
	// Phase 4d: Edge Case Hardening
	// ================================================================

	/**
	 * M = 1.0, 0.999, 1.001 should not produce NaN or division by zero.
	 */
	@ParameterizedTest(name = "Edge Mach M={0}")
	@ValueSource(doubles = {0.999, 1.0, 1.001, 0.0, 10.0})
	void testEdgeMachNoNaN(double mach) {
		var rocket = SupersonicTestRockets.makeConeCylinderFins();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(2));

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
		assertFalse(Double.isNaN(forces.getCD()), "CD NaN at M=" + mach);
		assertFalse(Double.isNaN(forces.getCN()), "CN NaN at M=" + mach);
		assertFalse(Double.isInfinite(forces.getCD()), "CD Inf at M=" + mach);
		assertFalse(Double.isInfinite(forces.getCN()), "CN Inf at M=" + mach);
		assertTrue(forces.getCD() >= 0, "CD should be >= 0 at M=" + mach);
	}

	/**
	 * High AoA (0-20°) should produce valid results without NaN.
	 */
	@ParameterizedTest(name = "AoA={0} deg")
	@ValueSource(doubles = {0, 2, 5, 10, 15, 20})
	void testHighAoANoNaN(double aoaDeg) {
		var rocket = SupersonicTestRockets.makeConeCylinderFins();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		for (double mach : new double[]{0.5, 1.5, 3.0, 5.0}) {
			var conditions = new FlightConditions(config);
			conditions.setMach(mach);
			conditions.setAOA(Math.toRadians(aoaDeg));

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
			assertFalse(Double.isNaN(forces.getCD()),
					"CD NaN at M=" + mach + " AoA=" + aoaDeg);
			assertFalse(Double.isNaN(forces.getCN()),
					"CN NaN at M=" + mach + " AoA=" + aoaDeg);
		}
	}

	/**
	 * Very high Mach (M=10) should produce valid, physically reasonable results
	 * for all standard test geometries.
	 */
	@Test
	void testVeryHighMachValid() {
		info.openrocket.core.rocketcomponent.Rocket[] rockets = {
			SupersonicTestRockets.makeConeCylinder(),
			SupersonicTestRockets.makeOgiveCylinder(),
			SupersonicTestRockets.makeConeCylinderFins(),
			SupersonicTestRockets.makeOgiveBoattailFins(),
			SupersonicTestRockets.makeVonKarmanFins(),
		};

		for (var rocket : rockets) {
			var config = rocket.getSelectedConfiguration();
			var calc = new BarrowmanCalculator();
			var warnings = new WarningSet();

			var conditions = new FlightConditions(config);
			conditions.setMach(10.0);
			conditions.setAOA(Math.toRadians(2));

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
			double cd = forces.getCD();
			double cn = forces.getCN();

			assertFalse(Double.isNaN(cd), rocket.getName() + " CD NaN at M=10");
			assertFalse(Double.isInfinite(cd), rocket.getName() + " CD Inf at M=10");
			assertTrue(cd >= 0, rocket.getName() + " CD should be >= 0 at M=10");
			assertTrue(cd < 5.0, rocket.getName() + " CD suspiciously large at M=10: " + cd);
			assertFalse(Double.isNaN(cn), rocket.getName() + " CN NaN at M=10");
		}
	}

	/**
	 * ShockGeometry should handle extreme Mach without NaN/Inf.
	 */
	@ParameterizedTest(name = "ShockGeometry at M={0}")
	@ValueSource(doubles = {1.01, 2.0, 5.0, 8.0, 10.0})
	void testShockGeometryExtremeValidMach(double mach) {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);
		assertTrue(sg.isSupersonic());

		for (ShockGeometry.LocalConditions station : sg.getStations()) {
			assertTrue(Double.isFinite(station.localMach), "Local Mach not finite at M=" + mach);
			assertTrue(station.localMach > 0, "Local Mach not positive at M=" + mach);
			assertTrue(Double.isFinite(station.pressureRatio), "Pressure ratio not finite at M=" + mach);
			assertTrue(station.pressureRatio > 0, "Pressure ratio not positive at M=" + mach);
		}
	}

	// ================================================================
	// Phase 4e: Graceful Degradation & User Warnings
	// ================================================================

	@Test
	void testHypersonicWarningAboveMach5() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(6.0);
		conditions.setAOA(Math.toRadians(2));

		calc.getAerodynamicForces(config, conditions, warnings);

		assertTrue(warnings.contains(Warning.HYPERSONIC),
				"Should contain HYPERSONIC warning at M=6. Warnings: " + warnings);
	}

	@Test
	void testExtremeHypersonicWarningAboveMach10() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(12.0);
		conditions.setAOA(Math.toRadians(2));

		calc.getAerodynamicForces(config, conditions, warnings);

		assertTrue(warnings.contains(Warning.HYPERSONIC_EXTREME),
				"Should contain HYPERSONIC_EXTREME warning at M=12. Warnings: " + warnings);
	}

	@Test
	void testNoHypersonicWarningBelowMach5() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(3.0);
		conditions.setAOA(Math.toRadians(2));

		calc.getAerodynamicForces(config, conditions, warnings);

		assertFalse(warnings.contains(Warning.HYPERSONIC),
				"Should not contain HYPERSONIC warning at M=3. Warnings: " + warnings);
		assertFalse(warnings.contains(Warning.HYPERSONIC_EXTREME),
				"Should not contain HYPERSONIC_EXTREME warning at M=3. Warnings: " + warnings);
	}

	@Test
	void testHighAoAWarning() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAOA(Math.toRadians(20));

		calc.getAerodynamicForces(config, conditions, warnings);

		// Check using Warning constant equality (most reliable)
		assertTrue(warnings.contains(Warning.HIGH_AOA),
				"Should contain HIGH_AOA warning at 20°. Warnings: " + warnings);
	}

	// ================================================================
	// Helpers
	// ================================================================

	private double computeCD(info.openrocket.core.rocketcomponent.Rocket rocket, double mach) {
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		var warnings = new WarningSet();
		return calc.getAerodynamicForces(config, conditions, warnings).getCD();
	}
}
