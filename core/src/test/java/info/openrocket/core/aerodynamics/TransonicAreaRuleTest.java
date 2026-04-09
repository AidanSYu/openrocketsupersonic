package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.Application;

/**
 * Tests for Phase 6e: Transonic Area Rule (Whitcomb).
 * <p>
 * Validates:
 * - S(x) for simple cone-cylinder matches analytical pi*r(x)^2
 * - Sears-Haack body wave drag matches theoretical minimum
 * - Finned rocket has higher transonic drag than body-alone
 * - Wave drag integral is non-negative
 * - Second derivative computation is accurate
 * - Smooth blending at M 1.2-1.5
 * - Area distribution handles edge cases
 */
public class TransonicAreaRuleTest {

	private static final double EPS = 1e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ==================== Area Distribution ====================

	@Test
	void testConeCylinderAreaDistribution() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinder();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		TransonicAreaRule.AreaDistribution dist =
				TransonicAreaRule.computeAreaDistribution(config, 100);

		assertNotNull(dist);
		assertTrue(dist.x.length > 0, "Should have stations");
		assertTrue(dist.length > 0, "Should have positive length");

		// First station (nose tip): area should be near zero
		assertTrue(dist.S[0] < 1e-4,
				String.format("Nose tip area %.6f should be near zero", dist.S[0]));

		// At the body tube (past the nose): area should be pi*r^2
		// Nose is 0.15m, body starts after. r=0.025m -> area = pi*0.025^2 = 0.001963
		double expectedMaxArea = Math.PI * 0.025 * 0.025;
		double maxArea = 0;
		for (double s : dist.S) {
			maxArea = Math.max(maxArea, s);
		}

		assertEquals(expectedMaxArea, maxArea, expectedMaxArea * 0.05,
				String.format("Max area %.6f should be pi*r^2 = %.6f", maxArea, expectedMaxArea));
	}

	@Test
	void testConeCylinderAreaDistributionNoseConeProfile() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinder();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		TransonicAreaRule.AreaDistribution dist =
				TransonicAreaRule.computeAreaDistribution(config, 200);

		// For a conical nose of length 0.15m and radius 0.025m:
		// r(x) = 0.025 * (x / 0.15) for 0 <= x <= 0.15
		// S(x) = pi * r(x)^2 = pi * 0.025^2 * (x/0.15)^2
		double noseLength = 0.15;
		double maxR = 0.025;

		for (int i = 0; i < dist.x.length; i++) {
			double x = dist.x[i];
			if (x < noseLength && x > 0.005) {
				double expectedR = maxR * (x / noseLength);
				double expectedArea = Math.PI * expectedR * expectedR;
				double relErr = Math.abs(dist.S[i] - expectedArea) / expectedArea;
				assertTrue(relErr < 0.15,
						String.format("At x=%.4f: area=%.6f, expected=%.6f, relErr=%.2f%%",
								x, dist.S[i], expectedArea, relErr * 100));
			}
		}
	}

	@Test
	void testAreaDistributionMonotonicOnNoseCone() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinder();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		TransonicAreaRule.AreaDistribution dist =
				TransonicAreaRule.computeAreaDistribution(config, 200);

		// Area should be non-decreasing along the nose cone (first 0.15m)
		double prevArea = 0;
		for (int i = 0; i < dist.x.length; i++) {
			if (dist.x[i] > 0.15) break;
			assertTrue(dist.S[i] >= prevArea - EPS,
					String.format("Area should be non-decreasing on nose: x=%.4f, S=%.6f < prev=%.6f",
							dist.x[i], dist.S[i], prevArea));
			prevArea = dist.S[i];
		}
	}

	@Test
	void testFinnedRocketHasLargerArea() {
		Rocket bodyOnly = SupersonicTestRockets.makeConeCylinder();
		Rocket withFins = SupersonicTestRockets.makeConeCylinderFins();

		TransonicAreaRule.AreaDistribution distBody =
				TransonicAreaRule.computeAreaDistribution(bodyOnly.getSelectedConfiguration(), 200);
		TransonicAreaRule.AreaDistribution distFins =
				TransonicAreaRule.computeAreaDistribution(withFins.getSelectedConfiguration(), 200);

		// Sum total area (proxy for volume)
		double totalBody = 0, totalFins = 0;
		for (double s : distBody.S) totalBody += s;
		for (double s : distFins.S) totalFins += s;

		assertTrue(totalFins > totalBody,
				String.format("Finned rocket (%.6f) should have more total area than body-only (%.6f)",
						totalFins, totalBody));
	}

	// ==================== Second Derivative ====================

	@Test
	void testSecondDerivativeConstantArea() {
		// Constant S(x) = c -> S''(x) = 0
		double[] S = new double[100];
		java.util.Arrays.fill(S, 1.0);
		double[] Spp = TransonicAreaRule.computeSecondDerivative(S, 0.01);

		for (int i = 1; i < Spp.length - 1; i++) {
			assertEquals(0, Spp[i], 1e-10,
					"Second derivative of constant should be zero at index " + i);
		}
	}

	@Test
	void testSecondDerivativeLinearArea() {
		// Linear S(x) = a*x + b -> S''(x) = 0
		int N = 100;
		double[] S = new double[N];
		double dx = 0.01;
		for (int i = 0; i < N; i++) {
			S[i] = 2.0 * i * dx + 1.0;
		}
		double[] Spp = TransonicAreaRule.computeSecondDerivative(S, dx);

		for (int i = 1; i < Spp.length - 1; i++) {
			assertEquals(0, Spp[i], 1e-8,
					"Second derivative of linear function should be zero at index " + i);
		}
	}

	@Test
	void testSecondDerivativeQuadratic() {
		// S(x) = x^2 -> S''(x) = 2
		int N = 100;
		double dx = 0.01;
		double[] S = new double[N];
		for (int i = 0; i < N; i++) {
			double x = i * dx;
			S[i] = x * x;
		}
		double[] Spp = TransonicAreaRule.computeSecondDerivative(S, dx);

		for (int i = 2; i < N - 2; i++) {
			assertEquals(2.0, Spp[i], 0.01,
					String.format("S'' should be 2.0 at index %d, got %.6f", i, Spp[i]));
		}
	}

	// ==================== Wave Drag Computation ====================

	@Test
	void testWaveDragNonNegative() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinder();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		TransonicAreaRule.AreaDistribution dist =
				TransonicAreaRule.computeAreaDistribution(config, 200);
		double S_ref = Math.PI * 0.025 * 0.025;

		double Cd = TransonicAreaRule.computeWaveDrag(dist, S_ref);
		assertTrue(Cd >= 0, "Wave drag should be non-negative, got " + Cd);
	}

	@Test
	void testWaveDragFinite() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		TransonicAreaRule.AreaDistribution dist =
				TransonicAreaRule.computeAreaDistribution(config, 200);
		double S_ref = Math.PI * 0.025 * 0.025;

		double Cd = TransonicAreaRule.computeWaveDrag(dist, S_ref);
		assertTrue(Double.isFinite(Cd), "Wave drag should be finite");
	}

	@Test
	void testFinnedRocketHigherWaveDragThanBodyAlone() {
		double S_ref = Math.PI * 0.025 * 0.025;

		Rocket bodyOnly = SupersonicTestRockets.makeConeCylinder();
		TransonicAreaRule.AreaDistribution distBody =
				TransonicAreaRule.computeAreaDistribution(bodyOnly.getSelectedConfiguration(), 200);
		double CdBody = TransonicAreaRule.computeWaveDrag(distBody, S_ref);

		Rocket withFins = SupersonicTestRockets.makeConeCylinderFins();
		TransonicAreaRule.AreaDistribution distFins =
				TransonicAreaRule.computeAreaDistribution(withFins.getSelectedConfiguration(), 200);
		double CdFins = TransonicAreaRule.computeWaveDrag(distFins, S_ref);

		assertTrue(CdFins > CdBody,
				String.format("Finned rocket wave drag (%.6f) should exceed body-only (%.6f)",
						CdFins, CdBody));
	}

	@Test
	void testWaveDragZeroForConstantArea() {
		// A constant-area body should have zero wave drag (S'' = 0 everywhere)
		double[] x = new double[200];
		double[] S = new double[200];
		double dx = 0.005;
		for (int i = 0; i < 200; i++) {
			x[i] = i * dx;
			S[i] = 0.002; // constant area
		}

		TransonicAreaRule.AreaDistribution dist =
				new TransonicAreaRule.AreaDistribution(x, S, 1.0);
		double Cd = TransonicAreaRule.computeWaveDrag(dist, 0.002);
		assertEquals(0, Cd, 1e-10, "Constant area body should have zero wave drag");
	}

	// ==================== Sears-Haack Minimum Drag ====================

	@Test
	void testSearsHaackMinDragFormula() {
		// For a Sears-Haack body with V = 0.001 m^3, L = 1.0 m, S_ref = 0.001 m^2
		double V = 0.001;
		double L = 1.0;
		double S_ref = 0.001;

		double Cd = TransonicAreaRule.searsHaackMinDrag(V, L, S_ref);
		// 128 * V^2 / (pi * L^4 * S_ref) = 128 * 1e-6 / (pi * 1 * 1e-3) = 128e-6 / 3.14159e-3 = 0.04074
		double expected = 128.0 * V * V / (Math.PI * L * L * L * L * S_ref);
		assertEquals(expected, Cd, EPS, "Sears-Haack formula should match");
	}

	@Test
	void testSearsHaackPositive() {
		double Cd = TransonicAreaRule.searsHaackMinDrag(0.001, 0.5, 0.002);
		assertTrue(Cd > 0, "Sears-Haack drag should be positive");
		assertTrue(Double.isFinite(Cd), "Sears-Haack drag should be finite");
	}

	@Test
	void testSearsHaackZeroLength() {
		assertEquals(0, TransonicAreaRule.searsHaackMinDrag(0.001, 0, 0.001), EPS);
	}

	// ==================== Blending ====================

	@Test
	void testBlendBelowLow() {
		double ar = 0.5;
		double comp = 0.3;
		double result = TransonicAreaRule.blendWaveDrag(1.1, ar, comp);
		assertEquals(ar, result, EPS, "Below blend low should return area rule value");
	}

	@Test
	void testBlendAboveHigh() {
		double ar = 0.5;
		double comp = 0.3;
		double result = TransonicAreaRule.blendWaveDrag(1.6, ar, comp);
		assertEquals(comp, result, EPS, "Above blend high should return component value");
	}

	@Test
	void testBlendMidpoint() {
		double ar = 0.5;
		double comp = 0.3;
		double midMach = (TransonicAreaRule.BLEND_LOW + TransonicAreaRule.BLEND_HIGH) / 2.0;
		double result = TransonicAreaRule.blendWaveDrag(midMach, ar, comp);
		// At midpoint of smoothstep, should be ~0.5 blend
		assertTrue(result > comp && result < ar,
				String.format("Blend at midpoint (%.4f) should be between %.4f and %.4f",
						result, comp, ar));
	}

	@Test
	void testBlendSmooth() {
		double ar = 0.5;
		double comp = 0.3;
		double step = 0.01;

		double prev = TransonicAreaRule.blendWaveDrag(1.0, ar, comp);
		for (double m = 1.0 + step; m <= 1.7; m += step) {
			double val = TransonicAreaRule.blendWaveDrag(m, ar, comp);
			double jump = Math.abs(val - prev);
			assertTrue(jump < 0.02,
					String.format("Blend jump at M=%.2f: %.6f", m, jump));
			prev = val;
		}
	}

	// ==================== Edge Cases ====================

	@Test
	void testEmptyConfiguration() {
		Rocket rocket = new Rocket();
		rocket.enableEvents();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		TransonicAreaRule.AreaDistribution dist =
				TransonicAreaRule.computeAreaDistribution(config, 100);

		// Should handle gracefully: either empty or all-zero
		assertNotNull(dist);
	}

	@Test
	void testSmallStationCount() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinder();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		// Even with very few stations, should not crash
		TransonicAreaRule.AreaDistribution dist =
				TransonicAreaRule.computeAreaDistribution(config, 5);
		assertNotNull(dist);
		// Clamped to minimum 10
		assertTrue(dist.x.length >= 10, "Should enforce minimum station count");
	}

	@Test
	void testWaveDragWithTooFewPoints() {
		double[] x = {0, 0.5};
		double[] S = {0, 0.001};
		TransonicAreaRule.AreaDistribution dist =
				new TransonicAreaRule.AreaDistribution(x, S, 0.5);
		double Cd = TransonicAreaRule.computeWaveDrag(dist, 0.001);
		assertEquals(0, Cd, EPS, "Too few points should return 0");
	}
}
