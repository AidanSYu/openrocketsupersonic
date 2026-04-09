package info.openrocket.core.models.atmosphere;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class AtmosphericSoundingTest {

	private static final double R_AIR = 287.053;
	private static final double GAMMA = 1.4;

	private static InputStream csv(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("Parse a simple 3-row CSV without header")
	void parseCsvBasic() throws IOException {
		String data = "0,288.15,101325\n5000,255.65,54048\n10000,223.15,26499\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "test");

		assertEquals(3, s.getDataPointCount());
		assertEquals(288.15, s.getTemperature(0), 1e-6);
		assertEquals(101325, s.getPressure(0), 1e-6);
		assertEquals(223.15, s.getTemperature(10000), 1e-6);
		assertEquals(26499, s.getPressure(10000), 1e-6);
	}

	@Test
	@DisplayName("Parse CSV with a header row (non-numeric first field)")
	void parseCsvWithHeader() throws IOException {
		String data = "altitude,temperature,pressure\n0,288.15,101325\n5000,255.65,54048\n10000,223.15,26499\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "with-header");

		assertEquals(3, s.getDataPointCount());
		assertEquals(288.15, s.getTemperature(0), 1e-6);
	}

	@Test
	@DisplayName("Temperature is linearly interpolated between data points")
	void interpolatesTemperature() throws IOException {
		String data = "0,300,101325\n10000,200,26499\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "interp");

		// Midpoint should be average
		assertEquals(250.0, s.getTemperature(5000), 1e-6);
		// Quarter point
		assertEquals(275.0, s.getTemperature(2500), 1e-6);
	}

	@Test
	@DisplayName("Pressure is linearly interpolated between data points")
	void interpolatesPressure() throws IOException {
		String data = "0,300,100000\n10000,200,20000\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "interp");

		assertEquals(60000.0, s.getPressure(5000), 1e-6);
	}

	@Test
	@DisplayName("Altitudes below minimum are clamped to first value")
	void clampsBelowMinAltitude() throws IOException {
		String data = "1000,280,90000\n5000,250,54000\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "clamp");

		assertEquals(280, s.getTemperature(-500), 1e-6);
		assertEquals(90000, s.getPressure(0), 1e-6);
		assertEquals(280, s.getTemperature(500), 1e-6);
	}

	@Test
	@DisplayName("Altitudes above maximum are clamped to last value")
	void clampsAboveMaxAltitude() throws IOException {
		String data = "0,288,101325\n10000,223,26499\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "clamp");

		assertEquals(223, s.getTemperature(20000), 1e-6);
		assertEquals(26499, s.getPressure(50000), 1e-6);
	}

	@Test
	@DisplayName("Density follows ideal gas law: rho = p / (R * T)")
	void densityFromIdealGas() throws IOException {
		String data = "0,288.15,101325\n10000,223.15,26499\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "density");

		double expectedRho = 101325.0 / (R_AIR * 288.15);
		assertEquals(expectedRho, s.getDensity(0), 1e-6);

		double expectedRho10k = 26499.0 / (R_AIR * 223.15);
		assertEquals(expectedRho10k, s.getDensity(10000), 1e-6);
	}

	@Test
	@DisplayName("Speed of sound follows a = sqrt(gamma * R * T)")
	void speedOfSoundCalculation() throws IOException {
		String data = "0,288.15,101325\n10000,223.15,26499\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "sos");

		double expectedA = Math.sqrt(GAMMA * R_AIR * 288.15);
		assertEquals(expectedA, s.getSpeedOfSound(0), 1e-6);

		double expectedA10k = Math.sqrt(GAMMA * R_AIR * 223.15);
		assertEquals(expectedA10k, s.getSpeedOfSound(10000), 1e-6);
	}

	@Test
	@DisplayName("Standard atmosphere sounding matches sea level conditions")
	void standardAtmosphereMatchesSeaLevel() {
		AtmosphericSounding std = AtmosphericSounding.standardAtmosphere();

		// ISA sea level: T = 288.15 K, p = 101325 Pa
		assertEquals(288.15, std.getTemperature(0), 1.0);
		assertEquals(101325, std.getPressure(0), 100.0);
	}

	@Test
	@DisplayName("Standard atmosphere sounding matches tropopause (~11 km)")
	void standardAtmosphereMatchesTropopause() {
		AtmosphericSounding std = AtmosphericSounding.standardAtmosphere();

		// ISA tropopause: T ~ 216.65 K at 11 km
		assertEquals(216.65, std.getTemperature(11000), 2.0);
	}

	@Test
	@DisplayName("Data point count is correct after parsing")
	void dataPointCount() throws IOException {
		String data = "0,288,101325\n1000,282,89876\n2000,275,79501\n3000,269,70121\n4000,262,61660\n";
		AtmosphericSounding s = AtmosphericSounding.fromCsv(csv(data), "count");

		assertEquals(5, s.getDataPointCount());
		assertEquals(0, s.getMinAltitude(), 1e-6);
		assertEquals(4000, s.getMaxAltitude(), 1e-6);
	}

	@Test
	@DisplayName("Standard atmosphere has 81 data points (0 to 80 km)")
	void standardAtmosphereDataPoints() {
		AtmosphericSounding std = AtmosphericSounding.standardAtmosphere();

		assertEquals(81, std.getDataPointCount());
		assertEquals(0, std.getMinAltitude(), 1e-6);
		assertEquals(80000, std.getMaxAltitude(), 1e-6);
	}
}
