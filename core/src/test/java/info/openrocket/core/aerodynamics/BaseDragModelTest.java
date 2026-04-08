package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for the base drag model in {@link BarrowmanDragCalculator}.
 * <p>
 * Validates:
 * - C1 continuity at transonic blend boundaries
 * - Correct subsonic and supersonic behavior
 * - Physically reasonable transonic peak
 * - Boattail correction factor
 */
public class BaseDragModelTest {

	private static final double DELTA = 1e-6;

	// ==================== Subsonic Region ====================

	@ParameterizedTest(name = "Subsonic base CD at M{0}")
	@CsvSource({
			"0.0,  0.120",
			"0.3,  0.1317",
			"0.5,  0.1525",
			"0.8,  0.2032",
	})
	public void testSubsonicBaseCD(double mach, double expected) {
		double cd = BarrowmanDragCalculator.calculateBaseCD(mach);
		assertEquals(expected, cd, 0.001,
				String.format("Subsonic base CD at M%.1f", mach));
	}

	// ==================== Supersonic Region ====================

	/**
	 * Supersonic base CD using Devan-Ashwood: Cd = 0.064 + 0.186/M².
	 */
	@ParameterizedTest(name = "Supersonic base CD at M{0}")
	@CsvSource({
			"1.5,  0.14667",
			"2.0,  0.11050",
			"3.0,  0.08467",
			"5.0,  0.07144",
	})
	public void testSupersonicBaseCD(double mach, double expected) {
		double cd = BarrowmanDragCalculator.calculateBaseCD(mach);
		assertEquals(expected, cd, 0.001,
				String.format("Supersonic base CD at M%.1f", mach));
	}

	// ==================== C1 Continuity ====================

	/**
	 * Verify C1 continuity at the lower blend boundary (M=0.85).
	 * Both value and first derivative must be continuous.
	 */
	@Test
	public void testC1ContinuityAtBlendLow() {
		double m = 0.85;
		double h = 1e-5;

		// Value continuity: values just inside and outside blend must agree
		double cdBelow = BarrowmanDragCalculator.calculateBaseCD(m - h);
		double cdAt = BarrowmanDragCalculator.calculateBaseCD(m);
		double cdAbove = BarrowmanDragCalculator.calculateBaseCD(m + h);

		assertEquals(cdAt, cdBelow, 1e-4, "Value discontinuity at lower blend boundary");
		assertEquals(cdAt, cdAbove, 1e-4, "Value discontinuity at lower blend boundary");

		// Derivative continuity: numerical derivatives from both sides must agree
		double derivBelow = (cdAt - BarrowmanDragCalculator.calculateBaseCD(m - h)) / h;
		double derivAbove = (BarrowmanDragCalculator.calculateBaseCD(m + h) - cdAt) / h;

		assertEquals(derivBelow, derivAbove, 0.01,
				String.format("Derivative discontinuity at M=%.2f: left=%.4f, right=%.4f",
						m, derivBelow, derivAbove));
	}

	/**
	 * Verify C1 continuity at the upper blend boundary (M=1.3).
	 */
	@Test
	public void testC1ContinuityAtBlendHigh() {
		double m = 1.3;
		double h = 1e-5;

		double cdBelow = BarrowmanDragCalculator.calculateBaseCD(m - h);
		double cdAt = BarrowmanDragCalculator.calculateBaseCD(m);
		double cdAbove = BarrowmanDragCalculator.calculateBaseCD(m + h);

		assertEquals(cdAt, cdBelow, 1e-4, "Value discontinuity at upper blend boundary");
		assertEquals(cdAt, cdAbove, 1e-4, "Value discontinuity at upper blend boundary");

		double derivBelow = (cdAt - BarrowmanDragCalculator.calculateBaseCD(m - h)) / h;
		double derivAbove = (BarrowmanDragCalculator.calculateBaseCD(m + h) - cdAt) / h;

		assertEquals(derivBelow, derivAbove, 0.01,
				String.format("Derivative discontinuity at M=%.2f: left=%.4f, right=%.4f",
						m, derivBelow, derivAbove));
	}

	// ==================== Transonic Peak ====================

	/**
	 * Verify the base drag peaks in the transonic region and the peak value
	 * is physically reasonable (between 0.22 and 0.30 based on experimental data
	 * for cylindrical afterbodies).
	 */
	@Test
	public void testTransonicPeak() {
		double maxCd = 0;
		double peakMach = 0;

		for (double m = 0.0; m <= 5.0; m += 0.01) {
			double cd = BarrowmanDragCalculator.calculateBaseCD(m);
			if (cd > maxCd) {
				maxCd = cd;
				peakMach = m;
			}
		}

		// Peak should be in the transonic region
		assertTrue(peakMach >= 0.9 && peakMach <= 1.2,
				String.format("Base drag peak at M=%.2f is outside expected transonic range [0.9, 1.2]", peakMach));

		// Peak value should be physically reasonable
		assertTrue(maxCd >= 0.22 && maxCd <= 0.30,
				String.format("Base drag peak value %.4f is outside expected range [0.22, 0.30]", maxCd));
	}

	// ==================== Non-Negative & Monotonicity ====================

	/**
	 * Verify base CD is positive for all positive Mach numbers and decreases
	 * monotonically above the transonic peak.
	 */
	@Test
	public void testPositiveAndMonotonicSupersonic() {
		// Check positive everywhere
		for (double m = 0.0; m <= 10.0; m += 0.05) {
			double cd = BarrowmanDragCalculator.calculateBaseCD(m);
			assertTrue(cd > 0, String.format("Base CD is non-positive (%.6f) at M=%.2f", cd, m));
			assertFalse(Double.isNaN(cd), String.format("Base CD is NaN at M=%.2f", m));
			assertFalse(Double.isInfinite(cd), String.format("Base CD is infinite at M=%.2f", m));
		}

		// Check monotonically decreasing above M=1.3 (well past the peak)
		double prevCd = BarrowmanDragCalculator.calculateBaseCD(1.3);
		for (double m = 1.35; m <= 10.0; m += 0.05) {
			double cd = BarrowmanDragCalculator.calculateBaseCD(m);
			assertTrue(cd < prevCd,
					String.format("Base CD not decreasing: M=%.2f (%.6f) >= M=%.2f (%.6f)",
							m, cd, m - 0.05, prevCd));
			prevCd = cd;
		}
	}

	/**
	 * Verify smoothness through the transonic region — no large jumps between
	 * adjacent Mach points.
	 */
	@Test
	public void testTransonicSmoothness() {
		double step = 0.01;
		double prevCd = BarrowmanDragCalculator.calculateBaseCD(0.7);

		for (double m = 0.7 + step; m <= 1.5; m += step) {
			double cd = BarrowmanDragCalculator.calculateBaseCD(m);
			double jump = Math.abs(cd - prevCd);
			// Maximum allowable change per 0.01 Mach step
			assertTrue(jump < 0.01,
					String.format("Base CD jump too large between M=%.2f and M=%.2f: delta=%.6f",
							m - step, m, jump));
			prevCd = cd;
		}
	}

	// ==================== Boattail Correction Factor ====================

	/**
	 * No boattail (cylindrical): factor should be 1.0.
	 */
	@Test
	public void testBoattailFactorCylindrical() {
		// Equal radii — not a boattail
		assertEquals(1.0, BarrowmanDragCalculator.calculateBoattailFactor(0.025, 0.025, 0.06, 2.0), DELTA);
		// Flaring (aft > fore) — not a boattail
		assertEquals(1.0, BarrowmanDragCalculator.calculateBoattailFactor(0.020, 0.025, 0.06, 2.0), DELTA);
		// Zero length
		assertEquals(1.0, BarrowmanDragCalculator.calculateBoattailFactor(0.025, 0.020, 0.0, 2.0), DELTA);
	}

	/**
	 * Moderate boattail (small angle): factor should be less than 1.0.
	 */
	@Test
	public void testBoattailFactorModerate() {
		// Typical boattail: 25mm -> 18mm over 60mm length
		// Angle = atan(7/60) ≈ 6.7° (< 12°, full benefit)
		double factor = BarrowmanDragCalculator.calculateBoattailFactor(0.025, 0.018, 0.06, 2.0);
		assertTrue(factor < 1.0, "Boattail should reduce base drag factor");
		assertTrue(factor > 0.5, "Boattail correction should not be too aggressive");
	}

	/**
	 * Boattail correction should increase at supersonic speeds
	 * (more drag reduction) compared to subsonic.
	 */
	@Test
	public void testBoattailFactorSupersonicEnhancement() {
		double foreR = 0.025, aftR = 0.018, length = 0.06;

		double factorSubsonic = BarrowmanDragCalculator.calculateBoattailFactor(foreR, aftR, length, 0.5);
		double factorSupersonic = BarrowmanDragCalculator.calculateBoattailFactor(foreR, aftR, length, 2.0);

		assertTrue(factorSupersonic < factorSubsonic,
				String.format("Supersonic boattail factor (%.4f) should be less than subsonic (%.4f)",
						factorSupersonic, factorSubsonic));
	}

	/**
	 * Steep boattail angle (> 20°) should have no benefit (factor = 1.0).
	 */
	@Test
	public void testBoattailFactorSteepAngle() {
		// Very steep: 25mm -> 10mm over 20mm → angle = atan(15/20) ≈ 36.9°
		double factor = BarrowmanDragCalculator.calculateBoattailFactor(0.025, 0.010, 0.020, 2.0);
		assertEquals(1.0, factor, DELTA, "Steep boattail should have no base drag benefit");
	}

	/**
	 * More aggressive boattail (larger diameter ratio) should give more
	 * drag reduction than a mild one.
	 */
	@Test
	public void testBoattailFactorScalesWithRatio() {
		double length = 0.10;  // Long enough for small angles
		double mach = 1.5;

		double factorMild = BarrowmanDragCalculator.calculateBoattailFactor(0.025, 0.022, length, mach);
		double factorAggressive = BarrowmanDragCalculator.calculateBoattailFactor(0.025, 0.015, length, mach);

		assertTrue(factorAggressive < factorMild,
				String.format("More aggressive boattail (%.4f) should have lower factor than mild (%.4f)",
						factorAggressive, factorMild));
	}

	// ==================== Devan-Ashwood Validation ====================

	/**
	 * Validation gate: base drag magnitude and trend vs Mach matches published data.
	 * <p>
	 * Experimental data for cylindrical afterbodies with turbulent boundary layers
	 * from Devan & Ashwood (NASA TN D-721), Hoerner Ch. 3, and Sigal & Danberg:
	 * <ul>
	 *   <li>M 1.5: Cd_b ≈ 0.14–0.17</li>
	 *   <li>M 2.0: Cd_b ≈ 0.10–0.13</li>
	 *   <li>M 3.0: Cd_b ≈ 0.08–0.10</li>
	 *   <li>M 5.0: Cd_b ≈ 0.06–0.08</li>
	 * </ul>
	 */
	@ParameterizedTest(name = "Base CD at M{0} within published range [{1}, {2}]")
	@CsvSource({
			"1.5,  0.14,  0.17",
			"2.0,  0.10,  0.13",
			"3.0,  0.08,  0.10",
			"5.0,  0.06,  0.08",
	})
	public void testBaseCDMatchesPublishedData(double mach, double low, double high) {
		double cd = BarrowmanDragCalculator.calculateBaseCD(mach);
		assertTrue(cd >= low && cd <= high,
				String.format("Base CD at M%.1f = %.4f, expected [%.2f, %.2f] from published data",
						mach, cd, low, high));
	}

	/**
	 * Verify that base drag asymptotes to a nonzero constant at high Mach,
	 * matching the Newtonian limit for base pressure.
	 */
	@Test
	public void testHighMachAsymptote() {
		double cd5 = BarrowmanDragCalculator.calculateBaseCD(5.0);
		double cd10 = BarrowmanDragCalculator.calculateBaseCD(10.0);
		double cd20 = BarrowmanDragCalculator.calculateBaseCD(20.0);

		// Should converge toward BASE_DRAG_A ≈ 0.064
		assertTrue(cd5 > 0.06, "Base CD at M5 should be > 0.06");
		assertTrue(cd10 > 0.06, "Base CD at M10 should be > 0.06");
		assertTrue(cd20 > 0.06, "Base CD at M20 should be > 0.06");

		// Convergence: difference between M10 and M20 should be very small
		double diff = Math.abs(cd10 - cd20);
		assertTrue(diff < 0.005,
				String.format("Base CD should converge at high Mach: M10=%.4f, M20=%.4f, diff=%.4f",
						cd10, cd20, diff));
	}
}
