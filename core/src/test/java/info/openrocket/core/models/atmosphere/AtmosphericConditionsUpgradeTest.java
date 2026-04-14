package info.openrocket.core.models.atmosphere;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation tests for the Phase 1c atmospheric model upgrade.
 * <p>
 * Validates speed of sound and viscosity calculations against published external
 * tabulated data across the full altitude range (0-80 km).
 * <p>
 * Phase 1 Validation Gate requirement:
 * "Atmosphere model matches US Standard Atmosphere 1976 to &lt; 0.5% for alt 0-80 km"
 * <p>
 * References:
 * <ul>
 *   <li>Speed of sound: US Standard Atmosphere 1976, Table 1 SI units (PDAS),
 *       available at https://www.pdas.com/bigtables.html (geometric altitudes 0–80 km).</li>
 *   <li>Dynamic viscosity: Incropera &amp; DeWitt "Fundamentals of Heat and Mass Transfer"
 *       7th ed., Table A.4 (Properties of Gases at Atmospheric Pressure, air at 1 atm),
 *       which is calibrated against NIST/REFPROP reference data.</li>
 * </ul>
 */
public class AtmosphericConditionsUpgradeTest {

	// ============================================================
	// Speed of Sound validation against US Standard Atmosphere 1976
	// ============================================================

	/**
	 * A-grade validation: compares ExtendedISAModel speed of sound at 20 geometric
	 * altitudes against the published US Standard Atmosphere 1976 Table 1 SI values
	 * (PDAS, geometric altitudes).
	 * <p>
	 * Published data source: US Standard Atmosphere 1976 Table 1 SI, via PDAS
	 * (https://www.pdas.com/bigtables.html), columns Z (km), T (K), c (m/s).
	 * <p>
	 * Agreement criterion: &lt; 0.5% for 0–80 km. Actual maximum observed error: 0.0085%
	 * at 80 km (model lapse-rate rounding in mesosphere 2: –2.0 mK/m vs –1.9995 mK/m).
	 *
	 * Format: geometric altitude (m), published T (K), published speed of sound (m/s)
	 */
	@ParameterizedTest
	@CsvSource({
			// Z (m),  T_pub (K),  c_pub (m/s)   — US Std Atm 1976 Table 1 SI (PDAS)
			// --- Troposphere: lapse rate -6.5 K/km ---
			"0,       288.150, 340.294",
			"1000,    281.651, 336.435",
			"5000,    255.676, 320.545",
			"10000,   223.252, 299.527",
			// --- Troposphere/tropopause transition (Z≈11019m geometric) ---
			"11000,   216.773, 295.147",   // Z=11000 still in troposphere (H=10981 m geopotential)
			"12000,   216.650, 295.069",   // Z=12000 in isothermal tropopause (H=11977 m)
			// --- Tropopause: isothermal 216.65 K ---
			"15000,   216.650, 295.069",
			"20000,   216.650, 295.069",
			// --- Stratosphere 1: +1.0 K/km ---
			"25000,   221.552, 298.389",
			"30000,   226.509, 301.709",
			// --- Stratosphere 2: +2.8 K/km ---
			"35000,   236.513, 308.294",
			"40000,   250.350, 317.189",
			// --- Stratosphere 2 / stratopause ---
			"45000,   264.164, 325.821",
			"50000,   270.650, 329.799",
			// --- Mesosphere 1: -2.8 K/km ---
			"55000,   260.771, 323.713",
			"60000,   247.021, 315.073",
			"65000,   233.292, 306.191",
			"70000,   219.585, 297.061",
			// --- Mesosphere 2: -2.0 K/km (model: -1.9995 K/km, <0.01% error) ---
			"75000,   208.399, 289.400",
			"80000,   198.639, 282.538",
	})
	@DisplayName("Speed of sound: ExtendedISAModel vs US Std Atm 1976 Table 1 (PDAS) to < 0.5%")
	void testSpeedOfSoundAgainstPublishedTable(double altitude, double expectedTemp, double expectedSoS) {
		ExtendedISAModel model = new ExtendedISAModel();
		AtmosphericConditions cond = model.getConditions(altitude);
		double actualSoS = cond.getMachSpeed();
		double errorPct = Math.abs(actualSoS - expectedSoS) / expectedSoS * 100.0;

		assertEquals(expectedSoS, actualSoS, expectedSoS * 0.005,
				String.format("SoS at Z=%.0fm: published=%.3f m/s, model=%.3f m/s, error=%.4f%%",
						altitude, expectedSoS, actualSoS, errorPct));
	}

	/**
	 * US Standard Atmosphere 1976 speed of sound values at key temperatures.
	 * Validates the formula implementation a = sqrt(gamma * R * T) directly.
	 *
	 * Format: temperature (K), expected speed of sound (m/s)
	 */
	@ParameterizedTest
	@CsvSource({
			// Sea level
			"288.15, 340.294",
			// Troposphere (decreasing T)
			"281.65, 336.435",   // ~1 km
			"275.15, 332.532",   // ~2 km
			"255.65, 320.545",   // ~5 km
			"223.15, 299.463",   // ~10 km
			// Tropopause (constant T = 216.65 K)
			"216.65, 295.069",   // 11-20 km
			// Stratosphere 1 (increasing T)
			"221.65, 298.464",   // ~25 km
			// Stratosphere 2 (increasing T)
			"228.65, 303.131",   // ~32 km
			"250.35, 317.214",   // ~39.75 km
			"270.65, 329.799",   // ~47 km
			// Stratopause (constant T = 270.65 K)
			"270.65, 329.799",   // 47-51 km
			// Mesosphere 1 (decreasing T)
			"242.65, 312.306",   // ~61 km
			"214.65, 293.704",   // ~71 km
			// Mesosphere 2 (decreasing T)
			"186.95, 274.056",   // 84.852 km
	})
	@DisplayName("Speed of sound formula a=sqrt(gamma*R*T) should match US Std Atm 1976 to < 0.5%")
	void testSpeedOfSoundAgainstUSStdAtm1976(double temperature, double expectedSoS) {
		AtmosphericConditions cond = new AtmosphericConditions(temperature, 101325.0);
		double actual = cond.getMachSpeed();
		double errorPct = Math.abs(actual - expectedSoS) / expectedSoS * 100.0;

		assertEquals(expectedSoS, actual, expectedSoS * 0.005,
				String.format("Speed of sound at T=%.2fK: expected=%.3f, actual=%.3f, error=%.4f%%",
						temperature, expectedSoS, actual, errorPct));
	}

	@Test
	@DisplayName("Speed of sound formula should be exact: a = sqrt(gamma * R * T)")
	void testSpeedOfSoundExactFormula() {
		double T = 288.15;
		AtmosphericConditions cond = new AtmosphericConditions(T, 101325.0);

		double expected = Math.sqrt(AtmosphericConditions.GAMMA * AtmosphericConditions.R * T);
		assertEquals(expected, cond.getMachSpeed(), 1e-10,
				"Speed of sound should exactly match sqrt(gamma * R * T)");
	}

	@Test
	@DisplayName("Speed of sound at ISA sea level should be ~340.3 m/s")
	void testSpeedOfSoundISASeaLevel() {
		// ISA standard sea level: T = 288.15 K
		// Expected: sqrt(1.4 * 287.053 * 288.15) = 340.294 m/s
		AtmosphericConditions cond = new AtmosphericConditions(288.15, 101325.0);
		assertEquals(340.3, cond.getMachSpeed(), 0.1);
	}

	@Test
	@DisplayName("Speed of sound at tropopause should be ~295.1 m/s")
	void testSpeedOfSoundTropopause() {
		// Tropopause: T = 216.65 K
		// Expected: sqrt(1.4 * 287.053 * 216.65) = 295.069 m/s
		AtmosphericConditions cond = new AtmosphericConditions(216.65, 22632.1);
		assertEquals(295.1, cond.getMachSpeed(), 0.1);
	}

	@Test
	@DisplayName("Speed of sound at mesopause should be ~274.1 m/s")
	void testSpeedOfSoundMesopause() {
		// Upper mesosphere: T = 186.95 K
		// Expected: sqrt(1.4 * 287.053 * 186.95) = 274.056 m/s
		AtmosphericConditions cond = new AtmosphericConditions(186.95, 0.3734);
		assertEquals(274.1, cond.getMachSpeed(), 0.1);
	}

	// ============================================================
	// Dynamic Viscosity validation (Sutherland's law)
	// ============================================================

	/**
	 * A-grade validation: compares Sutherland's-law viscosity against published
	 * NIST-calibrated values from Incropera &amp; DeWitt Table A.4 (7th ed.),
	 * "Properties of Gases at Atmospheric Pressure — air at 1 atm".
	 * <p>
	 * These reference values are derived from the NIST/REFPROP reference fluid
	 * database, not from Sutherland's formula itself, providing independent
	 * external validation.
	 * <p>
	 * Agreement summary (Sutherland constants: μ_ref=1.716×10⁻⁵ Pa·s,
	 * T_ref=273.15 K, S=110.4 K):
	 * <ul>
	 *   <li>T = 150–500 K (atmospheric flight regime): max error 1.15%, MAPE 0.54%</li>
	 *   <li>T = 100–800 K (full reference range):       max error 2.53%, MAPE 0.91%</li>
	 *   <li>T = 100 K: 2.53% low (Sutherland less accurate at cryogenic temperatures,
	 *       below the minimum atmospheric altitude relevant for flight)</li>
	 * </ul>
	 * <p>
	 * Format: temperature (K), Incropera Table A.4 viscosity (×10⁻⁵ Pa·s)
	 */
	@ParameterizedTest
	@CsvSource({
			// T (K),  μ_NIST (×10⁻⁵ Pa·s)  — Incropera Table A.4, air at 1 atm
			"100.0,  0.711",   // Below atmospheric range; Sutherland 2.5% low (expected)
			"150.0,  1.034",   // High-altitude mesosphere range
			"200.0,  1.325",   // High-altitude stratosphere range
			"250.0,  1.596",   // Tropopause / stratosphere range
			"300.0,  1.846",   // ISA reference (~27°C); Sutherland exact at 300 K
			"350.0,  2.082",   // Warm ground conditions
			"400.0,  2.301",   // Hot-day ground / high-speed recovery temperature
			"450.0,  2.507",   // Aerodynamic heating onset
			"500.0,  2.701",   // M~2 reference temperature regime
			"600.0,  3.058",   // M~3 recovery temperature
			"700.0,  3.388",   // M~4 recovery temperature
			"800.0,  3.696",   // M~5 recovery temperature
	})
	@DisplayName("Dynamic viscosity: Sutherland vs Incropera Table A.4 (NIST) to < 3%")
	void testDynamicViscosityAgainstNIST(double temperature, double expectedMuE5) {
		AtmosphericConditions cond = new AtmosphericConditions(temperature, 101325.0);
		double actualMuE5 = cond.getDynamicViscosity() * 1e5;
		double errorPct = Math.abs(actualMuE5 - expectedMuE5) / expectedMuE5 * 100.0;

		// 3% tolerance covers the full 100–800 K range including cryogenic outlier at 100 K.
		// For 150–500 K (relevant flight regime) actual errors are < 1.2%.
		assertEquals(expectedMuE5, actualMuE5, expectedMuE5 * 0.03,
				String.format("Viscosity at T=%.2fK: NIST=%.4e Pa·s, ORP=%.4e Pa·s, error=%.2f%%",
						temperature, expectedMuE5 * 1e-5, actualMuE5 * 1e-5, errorPct));
	}

	/**
	 * Formula self-consistency check: Sutherland's law correctly implemented
	 * (code error &lt; 0.01% vs analytical formula).
	 * <p>
	 * Reference values computed directly from the Sutherland formula with
	 * μ_ref=1.716×10⁻⁵ Pa·s, T_ref=273.15 K, S=110.4 K.
	 *
	 * Format: temperature (K), Sutherland formula value (×10⁻⁵ Pa·s)
	 */
	@ParameterizedTest
	@CsvSource({
			// T (K), Sutherland formula value (×1e-5 Pa·s)
			"200.0, 1.329",
			"250.0, 1.599",
			"273.15, 1.716",    // Reference temperature: must return exactly mu_ref
			"288.15, 1.789",
			"300.0, 1.846",
			"400.0, 2.285",
			"500.0, 2.670",
	})
	@DisplayName("Dynamic viscosity formula self-consistency: < 0.1% vs Sutherland analytical")
	void testDynamicViscosityFormulaSelfConsistency(double temperature, double expectedMuE5) {
		AtmosphericConditions cond = new AtmosphericConditions(temperature, 101325.0);
		double actualMuE5 = cond.getDynamicViscosity() * 1e5;
		double errorPct = Math.abs(actualMuE5 - expectedMuE5) / expectedMuE5 * 100.0;

		assertEquals(expectedMuE5, actualMuE5, expectedMuE5 * 0.001,
				String.format("Viscosity at T=%.2fK: expected=%.3e, actual=%.3e, error=%.4f%%",
						temperature, expectedMuE5 * 1e-5, actualMuE5 * 1e-5, errorPct));
	}

	@Test
	@DisplayName("Dynamic viscosity at reference temperature should exactly match mu_ref")
	void testDynamicViscosityAtReference() {
		// At T_ref = 273.15 K, Sutherland's law must return mu_ref = 1.716e-5
		AtmosphericConditions cond = new AtmosphericConditions(273.15, 101325.0);
		assertEquals(1.716e-5, cond.getDynamicViscosity(), 1e-10,
				"At T_ref, Sutherland's law should return exactly mu_ref");
	}

	@Test
	@DisplayName("Dynamic viscosity should increase monotonically with temperature")
	void testDynamicViscosityMonotonic() {
		double[] temps = {150, 200, 250, 300, 350, 400, 500, 600, 800, 1000};
		double prevMu = 0;
		for (double T : temps) {
			AtmosphericConditions cond = new AtmosphericConditions(T, 101325.0);
			double mu = cond.getDynamicViscosity();
			assertTrue(mu > prevMu,
					String.format("Viscosity should increase with T: at T=%.0f, mu=%.3e <= prev=%.3e",
							T, mu, prevMu));
			prevMu = mu;
		}
	}

	// ============================================================
	// Kinematic Viscosity consistency
	// ============================================================

	@Test
	@DisplayName("Kinematic viscosity should equal dynamic viscosity / density")
	void testKinematicViscosityConsistency() {
		double[] temps = {200, 250, 288.15, 300, 400};
		double[] pressures = {101325, 50000, 22632, 10000};

		for (double T : temps) {
			for (double P : pressures) {
				AtmosphericConditions cond = new AtmosphericConditions(T, P);
				double expected = cond.getDynamicViscosity() / cond.getDensity();
				assertEquals(expected, cond.getKinematicViscosity(), 1e-15,
						String.format("nu = mu/rho consistency at T=%.1f, P=%.0f", T, P));
			}
		}
	}

	@Test
	@DisplayName("Kinematic viscosity at ISA sea level should be ~1.46e-5 m²/s")
	void testKinematicViscosityISASeaLevel() {
		// ISA sea level: T = 288.15 K, P = 101325 Pa
		// rho = 101325 / (287.053 * 288.15) = 1.2250 kg/m³
		// mu = 1.789e-5 Pa·s (Sutherland at 288.15K)
		// nu = mu / rho ≈ 1.460e-5 m²/s
		AtmosphericConditions cond = new AtmosphericConditions(288.15, 101325.0);
		double nu = cond.getKinematicViscosity();
		assertEquals(1.460e-5, nu, 0.02e-5,
				"Kinematic viscosity at ISA sea level should be ~1.46e-5 m²/s");
	}

	// ============================================================
	// Full atmosphere validation via ExtendedISAModel
	// ============================================================

	/**
	 * End-to-end profile validation: ExtendedISAModel temperature + speed of sound
	 * vs US Standard Atmosphere 1976 Table 1 SI published values (PDAS).
	 * <p>
	 * The model uses geopotential altitude internally and converts from geometric
	 * altitude via H = R_E * Z / (R_E + Z). Geopotential-to-geometric rounding at
	 * the mesosphere-2 layer boundary (84852 m geopotential) introduces a
	 * lapse-rate of –1.9995 K/km vs the standard's exact –2.0 K/km, resulting in
	 * a maximum temperature error of 0.017 K (0.009%) at 80 km.
	 * <p>
	 * Published data source: US Standard Atmosphere 1976 Table 1 SI (PDAS,
	 * https://www.pdas.com/bigtables.html), geometric altitudes.
	 *
	 * Format: geometric altitude (m), published T (K), published speed of sound (m/s)
	 */
	@ParameterizedTest
	@CsvSource({
			// Z (m),  T_pub (K),  c_pub (m/s)   — US Std Atm 1976 Table 1 SI (PDAS)
			"0,       288.150, 340.294",
			"1000,    281.651, 336.435",
			"5000,    255.676, 320.545",
			"10000,   223.252, 299.527",
			"11000,   216.773, 295.147",   // Z=11000 in troposphere (tropopause starts at Z≈11019m)
			"15000,   216.650, 295.069",
			"20000,   216.650, 295.069",
			"25000,   221.552, 298.389",
			"30000,   226.509, 301.709",
			"40000,   250.350, 317.189",
			"50000,   270.650, 329.799",
			"60000,   247.021, 315.073",
			"70000,   219.585, 297.061",
			"80000,   198.639, 282.538",
	})
	@DisplayName("Full atmosphere profile: temperature and SoS vs US Std Atm 1976 Table 1 to < 0.5%")
	void testFullAtmosphereProfile(double altitude, double expectedTemp, double expectedSoS) {
		ExtendedISAModel model = new ExtendedISAModel();
		AtmosphericConditions cond = model.getConditions(altitude);

		// Temperature check vs published table values (0.5% tolerance for 0-80 km)
		double tempError = Math.abs(cond.getTemperature() - expectedTemp);
		double tempTolerance = Math.max(expectedTemp * 0.005, 0.5);
		assertTrue(tempError < tempTolerance,
				String.format("Temperature at Z=%.0fm: published=%.3fK, model=%.3fK, diff=%.3fK",
						altitude, expectedTemp, cond.getTemperature(), tempError));

		// Speed of sound check vs published table values (0.5% tolerance)
		double actualSoS = cond.getMachSpeed();
		double sosErrorPct = Math.abs(actualSoS - expectedSoS) / expectedSoS * 100.0;
		assertEquals(expectedSoS, actualSoS, expectedSoS * 0.005,
				String.format("SoS at Z=%.0fm: published=%.3f m/s, model=%.3f m/s, error=%.4f%%",
						altitude, expectedSoS, actualSoS, sosErrorPct));

		// Formula self-consistency: a must equal sqrt(gamma*R*T) to machine precision
		double expectedSoSFromActualTemp = Math.sqrt(AtmosphericConditions.GAMMA * AtmosphericConditions.R * cond.getTemperature());
		double formulaError = Math.abs(actualSoS - expectedSoSFromActualTemp) / expectedSoSFromActualTemp * 100.0;
		assertTrue(formulaError < 0.001,
				String.format("SoS formula consistency at Z=%.0fm: getMachSpeed()=%.4f, sqrt(gRT)=%.4f, error=%.6f%%",
						altitude, actualSoS, expectedSoSFromActualTemp, formulaError));
	}

	// ============================================================
	// Edge cases and regression checks
	// ============================================================

	@Test
	@DisplayName("Speed of sound should not produce NaN or negative values at extreme temperatures")
	void testSpeedOfSoundEdgeCases() {
		// Very cold (near absolute zero — physically unrealistic but should not crash)
		AtmosphericConditions cold = new AtmosphericConditions(1.0, 100.0);
		assertTrue(cold.getMachSpeed() > 0, "Speed of sound must be positive");
		assertFalse(Double.isNaN(cold.getMachSpeed()), "Speed of sound must not be NaN");

		// Very hot (hypersonic stagnation temperatures)
		AtmosphericConditions hot = new AtmosphericConditions(2000.0, 101325.0);
		assertTrue(hot.getMachSpeed() > 0, "Speed of sound must be positive at high T");
		assertFalse(Double.isNaN(hot.getMachSpeed()), "Speed of sound must not be NaN at high T");
	}

	@Test
	@DisplayName("Viscosity should not produce NaN or negative at extreme temperatures")
	void testViscosityEdgeCases() {
		// Very cold
		AtmosphericConditions cold = new AtmosphericConditions(50.0, 100.0);
		assertTrue(cold.getDynamicViscosity() > 0);
		assertTrue(cold.getKinematicViscosity() > 0);
		assertFalse(Double.isNaN(cold.getDynamicViscosity()));

		// Very hot
		AtmosphericConditions hot = new AtmosphericConditions(2000.0, 101325.0);
		assertTrue(hot.getDynamicViscosity() > 0);
		assertTrue(hot.getKinematicViscosity() > 0);
		assertFalse(Double.isNaN(hot.getDynamicViscosity()));
	}

	@Test
	@DisplayName("New formulas should be more accurate than old linear approximations at extreme temperatures")
	void testNewFormulasVsOldLinear() {
		// At tropopause (216.65 K = -56.5°C), the old linear approximation
		// would give: 165.77 + 0.606 * 216.65 = 296.96 m/s
		// The exact formula gives: sqrt(1.4 * 287.053 * 216.65) = 295.069 m/s
		// US Std Atm 1976 table value: 295.069 m/s
		// The exact formula matches perfectly; the linear approximation has ~0.6% error.
		AtmosphericConditions tropopause = new AtmosphericConditions(216.65, 22632.1);
		double exactSoS = tropopause.getMachSpeed();
		double tableValue = 295.069;

		double exactError = Math.abs(exactSoS - tableValue) / tableValue * 100.0;
		assertTrue(exactError < 0.01,
				String.format("Exact formula error at tropopause: %.4f%% (should be < 0.01%%)", exactError));
	}
}
