package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the AP09-style rational function blending utility.
 * Pure math — no Guice or Rocket objects needed.
 */
class RationalBlendTest {

	private static final double MB = 1.0;
	private static final double W = 0.3;

	@Test
	void weightIsNearOneAtZeroMach() {
		// g(0) approaches 1 but doesn't reach it exactly (sigmoid asymptote)
		double g = RationalBlend.weight(0.0, MB, W);
		assertTrue(g > 0.95, "g(0) should be near 1.0 (fully subsonic), was " + g);

		// With a narrower transition, g(0) is even closer to 1
		double gNarrow = RationalBlend.weight(0.0, MB, 0.1);
		assertTrue(gNarrow > g, "g(0) with narrower transition should be closer to 1.0, was " + gNarrow);
	}

	@Test
	void weightIsHalfAtBlendCenter() {
		double g = RationalBlend.weight(MB, MB, W);
		assertEquals(0.5, g, 1e-10, "g(Mb) should be exactly 0.5");
	}

	@Test
	void weightApproachesZeroAtHighMach() {
		double g = RationalBlend.weight(5.0, MB, W);
		assertTrue(g < 0.01, "g(5.0) should be near zero, was " + g);
	}

	@Test
	void weightIsMonotonicallyDecreasing() {
		double prev = RationalBlend.weight(0.0, MB, W);
		for (double m = 0.1; m <= 5.0; m += 0.1) {
			double curr = RationalBlend.weight(m, MB, W);
			assertTrue(curr <= prev + 1e-12,
					"Weight should be monotonically decreasing at M=" + m
							+ " (prev=" + prev + ", curr=" + curr + ")");
			prev = curr;
		}
	}

	@Test
	void blendReturnsSubsonicAtLowMach() {
		double val = RationalBlend.blend(10.0, 20.0, 0.3, MB, W);
		assertTrue(val < 11.0, "At M=0.3 blend should be near the subsonic value, was " + val);
	}

	@Test
	void blendReturnsSupersonicAtHighMach() {
		double val = RationalBlend.blend(10.0, 20.0, 3.0, MB, W);
		assertEquals(20.0, val, 0.5, "At M=3.0 blend should be near the supersonic value");
	}

	@Test
	void blendReturnsMidpointAtCenter() {
		double val = RationalBlend.blend(10.0, 20.0, MB, MB, W);
		assertEquals(15.0, val, 1e-10, "At blend center, result should be midpoint of fSub and fSup");
	}

	@Test
	void derivativeIsNegative() {
		// dg/dM should be <= 0 for all M > 0 (weight is decreasing)
		for (double m = 0.1; m <= 5.0; m += 0.1) {
			double dg = RationalBlend.weightDerivative(m, MB, W);
			assertTrue(dg <= 0.0,
					"dg/dM should be non-positive at M=" + m + ", was " + dg);
		}
	}

	@Test
	void derivativeIsContinuous() {
		// Verify analytical derivative matches numerical central difference
		double h = 1e-6;
		for (double m = 0.2; m <= 4.0; m += 0.3) {
			double analytical = RationalBlend.weightDerivative(m, MB, W);
			double gPlus = RationalBlend.weight(m + h, MB, W);
			double gMinus = RationalBlend.weight(m - h, MB, W);
			double numerical = (gPlus - gMinus) / (2.0 * h);
			assertEquals(numerical, analytical, 1e-4,
					"Analytical and numerical derivatives should agree at M=" + m);
		}
	}

	@Test
	void narrowTransitionIsSharper() {
		// With smaller w, the weight should change more rapidly near the center
		double narrowW = 0.1;
		double wideW = 1.0;
		double mTest = MB + 0.2; // slightly above center

		double gNarrow = RationalBlend.weight(mTest, MB, narrowW);
		double gWide = RationalBlend.weight(mTest, MB, wideW);

		// Narrow transition should be closer to 0 (more supersonic) above center
		assertTrue(gNarrow < gWide,
				"Narrow transition should drop faster: gNarrow=" + gNarrow + " gWide=" + gWide);
	}

	@Test
	void wideTransitionIsSmoother() {
		// With larger w, the weight should be closer to 0.5 away from the extremes
		double wideW = 2.0;
		double mTest = MB + 0.5;

		double gWide = RationalBlend.weight(mTest, MB, wideW);
		double gNarrow = RationalBlend.weight(mTest, MB, 0.1);

		// Wide transition stays closer to 0.5
		assertTrue(Math.abs(gWide - 0.5) < Math.abs(gNarrow - 0.5),
				"Wide transition should be closer to 0.5 at M=" + mTest);
	}

	@Test
	void differentBlendCenters() {
		double[] centers = { 0.9, 1.0, 1.2 };
		for (double mb : centers) {
			// g should be exactly 0.5 at each center
			double g = RationalBlend.weight(mb, mb, W);
			assertEquals(0.5, g, 1e-10, "g(Mb) should be 0.5 at center Mb=" + mb);

			// Below center should be > 0.5, above should be < 0.5
			double gBelow = RationalBlend.weight(mb * 0.8, mb, W);
			double gAbove = RationalBlend.weight(mb * 1.2, mb, W);
			assertTrue(gBelow > 0.5, "g below center should be > 0.5 at Mb=" + mb);
			assertTrue(gAbove < 0.5, "g above center should be < 0.5 at Mb=" + mb);
		}
	}
}
