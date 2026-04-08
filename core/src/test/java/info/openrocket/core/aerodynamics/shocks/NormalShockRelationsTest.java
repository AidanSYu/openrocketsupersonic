package info.openrocket.core.aerodynamics.shocks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for normal shock relations validated against NACA Report 1135 tables.
 * <p>
 * Reference values from NACA Report 1135, Table I: "Normal Shock,
 * gamma = 1.4" (Ames Research Staff, 1953).
 * <p>
 * Tolerance: < 0.1% relative error for all tabulated quantities.
 */
@DisplayName("Normal Shock Relations (NACA 1135)")
class NormalShockRelationsTest {

	private static final double REL_TOL = 0.001; // 0.1%

	/**
	 * NACA 1135 Table I: downstream Mach number M2 vs upstream M1.
	 */
	@ParameterizedTest(name = "M1={0} → M2={1}")
	@CsvSource({
			"1.0,   1.00000",
			"1.2,   0.84217",
			"1.5,   0.70109",
			"2.0,   0.57735",
			"2.5,   0.51299",
			"3.0,   0.47519",
			"4.0,   0.43496",
			"5.0,   0.41523",
			"10.0,  0.38757",
	})
	void downstreamMach(double m1, double expectedM2) {
		double m2 = NormalShockRelations.downstreamMach(m1);
		assertEquals(expectedM2, m2, expectedM2 * REL_TOL,
				String.format("M2 at M1=%.1f", m1));
	}

	/**
	 * NACA 1135 Table I: static pressure ratio p2/p1.
	 */
	@ParameterizedTest(name = "M1={0} → p2/p1={1}")
	@CsvSource({
			"1.0,   1.00000",
			"1.5,   2.45833",
			"2.0,   4.50000",
			"3.0,   10.3333",
			"5.0,   29.0000",
			"10.0,  116.500",
	})
	void pressureRatio(double m1, double expected) {
		double actual = NormalShockRelations.pressureRatio(m1);
		assertEquals(expected, actual, expected * REL_TOL,
				String.format("p2/p1 at M1=%.1f", m1));
	}

	/**
	 * NACA 1135 Table I: density ratio rho2/rho1.
	 */
	@ParameterizedTest(name = "M1={0} → rho2/rho1={1}")
	@CsvSource({
			"1.0,   1.00000",
			"1.5,   1.86207",
			"2.0,   2.66667",
			"3.0,   3.85714",
			"5.0,   5.00000",
			"10.0,  5.71429",
	})
	void densityRatio(double m1, double expected) {
		double actual = NormalShockRelations.densityRatio(m1);
		assertEquals(expected, actual, expected * REL_TOL,
				String.format("rho2/rho1 at M1=%.1f", m1));
	}

	/**
	 * NACA 1135 Table I: temperature ratio T2/T1.
	 */
	@ParameterizedTest(name = "M1={0} → T2/T1={1}")
	@CsvSource({
			"1.0,   1.00000",
			"1.5,   1.32022",
			"2.0,   1.68750",
			"3.0,   2.67901",
			"5.0,   5.80000",
			"10.0,  20.3875",
	})
	void temperatureRatio(double m1, double expected) {
		double actual = NormalShockRelations.temperatureRatio(m1);
		assertEquals(expected, actual, expected * REL_TOL,
				String.format("T2/T1 at M1=%.1f", m1));
	}

	/**
	 * NACA 1135 Table I: total pressure ratio p02/p01.
	 */
	@ParameterizedTest(name = "M1={0} → p02/p01={1}")
	@CsvSource({
			"1.0,   1.00000",
			"1.5,   0.92979",
			"2.0,   0.72088",
			"3.0,   0.32834",
			"5.0,   0.06172",
			"10.0,  0.00304",
	})
	void totalPressureRatio(double m1, double expected) {
		double actual = NormalShockRelations.totalPressureRatio(m1);
		assertEquals(expected, actual, Math.max(expected * REL_TOL, 1e-5),
				String.format("p02/p01 at M1=%.1f", m1));
	}

	@Test
	@DisplayName("M1=1 is identity (zero-strength shock)")
	void identityShock() {
		assertEquals(1.0, NormalShockRelations.pressureRatio(1.0), 1e-10);
		assertEquals(1.0, NormalShockRelations.densityRatio(1.0), 1e-10);
		assertEquals(1.0, NormalShockRelations.temperatureRatio(1.0), 1e-10);
		assertEquals(1.0, NormalShockRelations.downstreamMach(1.0), 1e-10);
		assertEquals(1.0, NormalShockRelations.totalPressureRatio(1.0), 1e-10);
	}

	@Test
	@DisplayName("M2 is always subsonic for M1 > 1")
	void downstreamAlwaysSubsonic() {
		for (double m1 = 1.01; m1 <= 20.0; m1 += 0.5) {
			double m2 = NormalShockRelations.downstreamMach(m1);
			assertTrue(m2 < 1.0, "M2 should be < 1 for M1=" + m1 + ", got M2=" + m2);
		}
	}

	@Test
	@DisplayName("Density ratio approaches (gamma+1)/(gamma-1) = 6 at high Mach")
	void densityRatioLimit() {
		double limit = (NormalShockRelations.GAMMA_AIR + 1.0) / (NormalShockRelations.GAMMA_AIR - 1.0);
		double ratio = NormalShockRelations.densityRatio(100.0);
		assertEquals(limit, ratio, limit * 0.001);
	}

	@Test
	@DisplayName("Inverse: Mach from pressure ratio is consistent")
	void inversePressureRatio() {
		for (double m1 = 1.2; m1 <= 10.0; m1 += 0.5) {
			double pRatio = NormalShockRelations.pressureRatio(m1);
			double recovered = NormalShockRelations.machFromPressureRatio(pRatio);
			assertEquals(m1, recovered, m1 * 1e-10,
					"Round-trip M1 from pressure ratio");
		}
	}

	@Test
	@DisplayName("Subsonic M1 throws exception")
	void subsonicThrows() {
		assertThrows(IllegalArgumentException.class,
				() -> NormalShockRelations.pressureRatio(0.8));
		assertThrows(IllegalArgumentException.class,
				() -> NormalShockRelations.downstreamMach(0.5));
	}

	@Test
	@DisplayName("Custom gamma: diatomic vs monatomic gas")
	void customGamma() {
		// Monatomic gas (gamma = 5/3): stronger density ratio limit = 4
		double gammaM = 5.0 / 3.0;
		double limit = (gammaM + 1.0) / (gammaM - 1.0); // = 4
		double ratio = NormalShockRelations.densityRatio(100.0, gammaM);
		assertEquals(limit, ratio, limit * 0.001);
	}
}
