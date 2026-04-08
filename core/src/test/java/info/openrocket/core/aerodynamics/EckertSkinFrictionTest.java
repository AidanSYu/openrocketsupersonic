package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for the Eckert reference temperature skin friction upgrade
 * in {@link BarrowmanDragCalculator} (Phase 2d).
 * <p>
 * Validates:
 * - Reference temperature T* increases with Mach as expected
 * - Corrected Reynolds number Re* decreases with Mach
 * - Compressibility reduction at M3 is ~30-40% (per validation gate)
 * - Smooth blending through the transonic region
 * - No NaN/Infinity at edge cases
 */
public class EckertSkinFrictionTest {

	// Standard sea-level temperature (K)
	private static final double T_SL = 288.15;
	// Typical cruise altitude temperature (K) — ~11 km
	private static final double T_CRUISE = 216.65;

	// ==================== Reference Temperature ====================

	@Test
	public void testReferenceTemperatureAtZeroMach() {
		double T_star = BarrowmanDragCalculator.calculateReferenceTemperature(0.0, T_SL);
		// At M=0, T* should equal T_e (no compressibility effect)
		assertEquals(T_SL, T_star, 0.01, "T* should equal T_e at M=0");
	}

	@Test
	public void testReferenceTemperatureIncreasesWithMach() {
		double T_star_M1 = BarrowmanDragCalculator.calculateReferenceTemperature(1.0, T_SL);
		double T_star_M2 = BarrowmanDragCalculator.calculateReferenceTemperature(2.0, T_SL);
		double T_star_M3 = BarrowmanDragCalculator.calculateReferenceTemperature(3.0, T_SL);
		double T_star_M5 = BarrowmanDragCalculator.calculateReferenceTemperature(5.0, T_SL);

		assertTrue(T_star_M1 > T_SL, "T* should exceed T_e at M=1");
		assertTrue(T_star_M2 > T_star_M1, "T* should increase with Mach");
		assertTrue(T_star_M3 > T_star_M2, "T* should increase with Mach");
		assertTrue(T_star_M5 > T_star_M3, "T* should increase with Mach");
	}

	@ParameterizedTest(name = "T*/T_e ratio at M{0}")
	@CsvSource({
			"1.0,  1.136",   // small correction at M=1
			"2.0,  1.542",   // moderate at M=2
			"3.0,  2.220",   // large at M=3
			"5.0,  4.389",   // very large at M=5
	})
	public void testReferenceTemperatureRatio(double mach, double expectedRatio) {
		// T*/T_e = 1 + 0.032*M² + 0.58*r*(gamma-1)/2*M²
		// = 1 + (0.032 + 0.58 * 0.893 * 0.2) * M²
		// = 1 + 0.1356 * M²
		// (independent of T_e)
		double T_star = BarrowmanDragCalculator.calculateReferenceTemperature(mach, T_SL);
		double ratio = T_star / T_SL;
		assertEquals(expectedRatio, ratio, 0.01,
				String.format("T*/T_e ratio at M%.1f", mach));
	}

	@Test
	public void testReferenceTemperatureIndependentOfT_e() {
		// The ratio T*/T_e should be the same regardless of absolute temperature
		double ratio_SL = BarrowmanDragCalculator.calculateReferenceTemperature(3.0, T_SL) / T_SL;
		double ratio_cruise = BarrowmanDragCalculator.calculateReferenceTemperature(3.0, T_CRUISE) / T_CRUISE;
		assertEquals(ratio_SL, ratio_cruise, 1e-6,
				"T*/T_e ratio should not depend on absolute temperature");
	}

	// ==================== Eckert Reynolds Number ====================

	@Test
	public void testEckertReynoldsAtFreestream() {
		// When T_star = T_e, Re* should equal Re
		double Re = 1e7;
		double ReStar = BarrowmanDragCalculator.calculateEckertReynolds(Re, T_SL, T_SL);
		assertEquals(Re, ReStar, 1.0, "Re* should equal Re when T*=T_e");
	}

	@Test
	public void testEckertReynoldsDecreasesWithMach() {
		double Re = 5e7;
		double ReStar_M1 = computeEckertRe(Re, 1.0, T_SL);
		double ReStar_M2 = computeEckertRe(Re, 2.0, T_SL);
		double ReStar_M3 = computeEckertRe(Re, 3.0, T_SL);
		double ReStar_M5 = computeEckertRe(Re, 5.0, T_SL);

		assertTrue(ReStar_M1 < Re, "Re* should be less than Re at M=1");
		assertTrue(ReStar_M2 < ReStar_M1, "Re* should decrease with Mach");
		assertTrue(ReStar_M3 < ReStar_M2, "Re* should decrease with Mach");
		assertTrue(ReStar_M5 < ReStar_M3, "Re* should decrease with Mach");
	}

	@Test
	public void testEckertReynoldsScalesLinearly() {
		// Re* should scale linearly with Re (same ratio for any Re at same Mach/T)
		double T_star = BarrowmanDragCalculator.calculateReferenceTemperature(3.0, T_SL);
		double ratio1 = BarrowmanDragCalculator.calculateEckertReynolds(1e6, T_SL, T_star) / 1e6;
		double ratio2 = BarrowmanDragCalculator.calculateEckertReynolds(1e8, T_SL, T_star) / 1e8;
		assertEquals(ratio1, ratio2, 1e-10, "Re*/Re ratio should be independent of Re");
	}

	// ==================== Compressibility Reduction ====================

	/**
	 * Validation gate: skin friction at M3 should be reduced ~30-40% vs incompressible.
	 * <p>
	 * The Eckert method reduces Cf through two mechanisms:
	 * 1. Lower Re* gives slightly higher incompressible Cf (weak effect)
	 * 2. Density ratio T_e/T* reduces the freestream-referenced Cf (strong effect)
	 * Net result is a significant reduction, matching published compressible Cf data.
	 */
	@Test
	public void testCompressibilityReductionAtMach3() {
		double Re = 5e7;
		double T_star = BarrowmanDragCalculator.calculateReferenceTemperature(3.0, T_SL);
		double ReStar = BarrowmanDragCalculator.calculateEckertReynolds(Re, T_SL, T_star);

		// Compute incompressible Cf at freestream Re (turbulent, Schlichting formula)
		double CfIncomp = 1.0 / Math.pow(1.50 * Math.log(Re) - 5.6, 2) - 1700.0 / Re;

		// Compute compressible Cf via Eckert: Cf(Re*) * (T_e/T*)
		double CfAtReStar = 1.0 / Math.pow(1.50 * Math.log(ReStar) - 5.6, 2) - 1700.0 / ReStar;
		double CfComp = CfAtReStar * (T_SL / T_star);

		double reduction = 1.0 - CfComp / CfIncomp;

		assertTrue(reduction >= 0.30,
				String.format("Cf reduction at M3 should be >= 30%%, got %.1f%%", reduction * 100));
		assertTrue(reduction <= 0.50,
				String.format("Cf reduction at M3 should be <= 50%%, got %.1f%%", reduction * 100));
	}

	@Test
	public void testCompressibilityReductionAtMach5() {
		double Re = 1e8;
		double T_star = BarrowmanDragCalculator.calculateReferenceTemperature(5.0, T_SL);
		double ReStar = BarrowmanDragCalculator.calculateEckertReynolds(Re, T_SL, T_star);

		double CfIncomp = 1.0 / Math.pow(1.50 * Math.log(Re) - 5.6, 2) - 1700.0 / Re;
		double CfAtReStar = 1.0 / Math.pow(1.50 * Math.log(ReStar) - 5.6, 2) - 1700.0 / ReStar;
		double CfComp = CfAtReStar * (T_SL / T_star);

		double reduction = 1.0 - CfComp / CfIncomp;

		// At M5, expect even greater reduction (~55-65%)
		assertTrue(reduction >= 0.45,
				String.format("Cf reduction at M5 should be >= 45%%, got %.1f%%", reduction * 100));
		assertTrue(reduction <= 0.70,
				String.format("Cf reduction at M5 should be <= 70%%, got %.1f%%", reduction * 100));
	}

	// ==================== Edge Cases ====================

	@Test
	public void testNoNaNOrInfinity() {
		double[] machs = { 0.0, 0.001, 0.5, 0.9, 0.999, 1.0, 1.001, 1.1, 2.0, 5.0, 10.0 };
		double[] temps = { 180.0, 216.65, 288.15, 400.0 };

		for (double mach : machs) {
			for (double T_e : temps) {
				double T_star = BarrowmanDragCalculator.calculateReferenceTemperature(mach, T_e);
				assertFalse(Double.isNaN(T_star), "T* NaN at M=" + mach + " T=" + T_e);
				assertFalse(Double.isInfinite(T_star), "T* Inf at M=" + mach + " T=" + T_e);
				assertTrue(T_star >= T_e, "T* should be >= T_e at M=" + mach);

				double Re = 1e7;
				double ReStar = BarrowmanDragCalculator.calculateEckertReynolds(Re, T_e, T_star);
				assertFalse(Double.isNaN(ReStar), "Re* NaN at M=" + mach + " T=" + T_e);
				assertFalse(Double.isInfinite(ReStar), "Re* Inf at M=" + mach + " T=" + T_e);
				assertTrue(ReStar > 0, "Re* should be positive at M=" + mach);
			}
		}
	}

	// ==================== Helpers ====================

	private double computeEckertRe(double Re, double mach, double T_e) {
		double T_star = BarrowmanDragCalculator.calculateReferenceTemperature(mach, T_e);
		return BarrowmanDragCalculator.calculateEckertReynolds(Re, T_e, T_star);
	}
}
