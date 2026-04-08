package info.openrocket.core.aerodynamics.shocks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Prandtl-Meyer expansion fan relations validated against
 * NACA Report 1135 tables.
 * <p>
 * Reference values from NACA Report 1135, Table III: "Prandtl-Meyer Function,
 * gamma = 1.4" and from Anderson, "Modern Compressible Flow", App. C.
 */
@DisplayName("Prandtl-Meyer Expansion (NACA 1135)")
class PrandtlMeyerExpansionTest {

	private static final double DEG = Math.PI / 180.0;
	private static final double REL_TOL = 0.001; // 0.1%

	/**
	 * NACA 1135 Table III: Prandtl-Meyer angle nu(M) in degrees.
	 */
	@ParameterizedTest(name = "M={0} → ν={1}°")
	@CsvSource({
			"1.0,    0.0000",
			"1.5,   11.9052",
			"2.0,   26.3798",
			"2.5,   39.1236",
			"3.0,   49.7573",
			"4.0,   65.7848",
			"5.0,   76.9202",
			"10.0, 102.3121",
	})
	void prandtlMeyerAngle(double mach, double expectedNuDeg) {
		double nu = PrandtlMeyerExpansion.nu(mach);
		double nuDeg = Math.toDegrees(nu);
		if (expectedNuDeg == 0.0) {
			assertEquals(0.0, nuDeg, 1e-8, "nu(1) = 0");
		} else {
			assertEquals(expectedNuDeg, nuDeg, expectedNuDeg * REL_TOL,
					String.format("ν at M=%.1f", mach));
		}
	}

	@Test
	@DisplayName("Maximum Prandtl-Meyer angle for air ≈ 130.45°")
	void maxAngle() {
		double maxDeg = Math.toDegrees(PrandtlMeyerExpansion.nuMax(1.4));
		assertEquals(130.45, maxDeg, 0.05);
	}

	/**
	 * Inverse: machFromNu(nu(M)) = M for various Mach numbers.
	 */
	@ParameterizedTest(name = "Round-trip M={0}")
	@CsvSource({
			"1.0", "1.2", "1.5", "2.0", "3.0", "5.0", "8.0", "10.0", "15.0", "20.0",
	})
	void inversePrandtlMeyer(double mach) {
		double nu = PrandtlMeyerExpansion.nu(mach);
		double recovered = PrandtlMeyerExpansion.machFromNu(nu);
		assertEquals(mach, recovered, mach * 1e-8,
				String.format("Round-trip M=%.1f", mach));
	}

	// ---- Expansion fan calculations ----

	@Test
	@DisplayName("Expansion increases Mach number")
	void expansionIncreasesMach() {
		double m2 = PrandtlMeyerExpansion.downstreamMach(2.0, 10.0 * DEG);
		assertTrue(m2 > 2.0, "Post-expansion Mach should increase");
	}

	@Test
	@DisplayName("Expansion decreases pressure and temperature")
	void expansionDecreasesPressureAndTemp() {
		PrandtlMeyerExpansion.ExpansionResult r = PrandtlMeyerExpansion.solve(2.0, 15.0 * DEG);
		assertTrue(r.pressureRatio < 1.0, "Pressure decreases in expansion");
		assertTrue(r.temperatureRatio < 1.0, "Temperature decreases in expansion");
		assertTrue(r.densityRatio < 1.0, "Density decreases in expansion");
		assertTrue(r.m2 > r.m1, "Mach increases in expansion");
	}

	@Test
	@DisplayName("Zero expansion gives identity")
	void zeroExpansion() {
		PrandtlMeyerExpansion.ExpansionResult r = PrandtlMeyerExpansion.solve(3.0, 0.0);
		assertEquals(3.0, r.m2, 1e-10);
		assertEquals(1.0, r.pressureRatio, 1e-10);
		assertEquals(1.0, r.temperatureRatio, 1e-10);
	}

	/**
	 * Verify specific expansion results.
	 * M1=2.0, delta=20° → M2 ≈ 2.833
	 */
	@Test
	@DisplayName("M=2.0 expansion by 20° gives M2 ≈ 2.833")
	void specificExpansion() {
		double m2 = PrandtlMeyerExpansion.downstreamMach(2.0, 20.0 * DEG);
		// nu(2.0) = 26.38°, nu(2.0) + 20° = 46.38°, M(46.38°) ≈ 2.833
		assertEquals(2.833, m2, 0.01);
	}

	/**
	 * Isentropic pressure ratio: verify against known formula.
	 * p2/p1 = [(1 + 0.2*M1²) / (1 + 0.2*M2²)]^3.5 for gamma=1.4
	 */
	@Test
	@DisplayName("Pressure ratio matches isentropic formula")
	void pressureRatioFormula() {
		double m1 = 2.0, m2 = 3.0;
		double expected = Math.pow((1.0 + 0.2 * m1 * m1) / (1.0 + 0.2 * m2 * m2), 3.5);
		double actual = PrandtlMeyerExpansion.pressureRatio(m1, m2);
		assertEquals(expected, actual, expected * 1e-10);
	}

	@Test
	@DisplayName("Derivative dnu/dM is positive for M > 1")
	void derivativePositive() {
		for (double m = 1.01; m <= 10.0; m += 0.5) {
			double dnu = PrandtlMeyerExpansion.dnuDm(m, 1.4);
			assertTrue(dnu > 0, "dnu/dM should be positive at M=" + m);
		}
	}

	@Test
	@DisplayName("Subsonic Mach throws exception")
	void subsonicThrows() {
		assertThrows(IllegalArgumentException.class,
				() -> PrandtlMeyerExpansion.nu(0.5));
		assertThrows(IllegalArgumentException.class,
				() -> PrandtlMeyerExpansion.downstreamMach(0.5, 10.0 * DEG));
	}

	@Test
	@DisplayName("Exceeding max expansion angle throws exception")
	void exceedMaxAngle() {
		double maxNu = PrandtlMeyerExpansion.nuMax(1.4);
		assertThrows(IllegalArgumentException.class,
				() -> PrandtlMeyerExpansion.machFromNu(maxNu + 1.0));
	}

	@Test
	@DisplayName("Custom gamma: monatomic gas nu_max ≈ 208°")
	void customGamma() {
		double gamma = 5.0 / 3.0;
		double maxDeg = Math.toDegrees(PrandtlMeyerExpansion.nuMax(gamma));
		// nu_max = (pi/2)*(sqrt(4) - 1) = pi/2 = 90° for gamma=5/3
		assertEquals(90.0, maxDeg, 0.01);
	}
}
