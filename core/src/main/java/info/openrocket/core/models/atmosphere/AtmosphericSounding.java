package info.openrocket.core.models.atmosphere;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 9c: Custom atmospheric sounding data.
 * <p>
 * Supports CSV files with columns: altitude(m), temperature(K), pressure(Pa).
 * Data is stored as sorted arrays and linearly interpolated between data points.
 * Values outside the data range are clamped to the nearest boundary value.
 * <p>
 * Density and speed of sound are derived from temperature and pressure using
 * the ideal gas law and standard air properties.
 */
public class AtmosphericSounding {

	/** Specific gas constant of dry air, J/(kg*K). */
	private static final double R_AIR = AtmosphericConditions.R;

	/** Ratio of specific heats for air. */
	private static final double GAMMA = AtmosphericConditions.GAMMA;

	private final double[] altitudes;
	private final double[] temperatures;
	private final double[] pressures;
	private final String sourceName;

	/**
	 * Construct an atmospheric sounding from pre-validated arrays.
	 *
	 * @param altitudes    altitudes in metres, must be strictly increasing
	 * @param temperatures temperatures in Kelvin, must all be positive
	 * @param pressures    pressures in Pascals, must all be positive
	 * @param sourceName   descriptive name for this sounding (e.g. file name)
	 */
	public AtmosphericSounding(double[] altitudes, double[] temperatures, double[] pressures, String sourceName) {
		if (altitudes == null || temperatures == null || pressures == null) {
			throw new IllegalArgumentException("Arrays must not be null");
		}
		if (altitudes.length < 2) {
			throw new IllegalArgumentException("At least 2 data points are required");
		}
		if (altitudes.length != temperatures.length || altitudes.length != pressures.length) {
			throw new IllegalArgumentException("All arrays must have the same length");
		}
		for (int i = 0; i < altitudes.length; i++) {
			if (temperatures[i] <= 0) {
				throw new IllegalArgumentException("Temperature must be positive at index " + i);
			}
			if (pressures[i] <= 0) {
				throw new IllegalArgumentException("Pressure must be positive at index " + i);
			}
			if (i > 0 && altitudes[i] <= altitudes[i - 1]) {
				throw new IllegalArgumentException("Altitudes must be strictly increasing at index " + i);
			}
		}

		this.altitudes = altitudes.clone();
		this.temperatures = temperatures.clone();
		this.pressures = pressures.clone();
		this.sourceName = sourceName;
	}

	/**
	 * Parse a CSV sounding file.
	 * <p>
	 * Expected format: one row per altitude with comma-separated values
	 * {@code altitude,temperature,pressure}. An optional header row is detected
	 * and skipped if the first field cannot be parsed as a number.
	 *
	 * @param input      input stream to read from
	 * @param sourceName descriptive name for error messages
	 * @return a new {@link AtmosphericSounding}
	 * @throws IOException if reading fails or the data is invalid
	 */
	public static AtmosphericSounding fromCsv(InputStream input, String sourceName) throws IOException {
		List<double[]> rows = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			boolean firstLine = true;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}

				String[] parts = line.split(",");
				if (parts.length < 3) {
					if (firstLine) {
						// Tolerate a short header row
						firstLine = false;
						continue;
					}
					throw new IOException("Expected at least 3 columns, got " + parts.length + " in: " + line);
				}

				// Detect header row
				if (firstLine) {
					firstLine = false;
					try {
						Double.parseDouble(parts[0].trim());
					} catch (NumberFormatException e) {
						// Non-numeric first field means this is a header row
						continue;
					}
				}

				try {
					double alt = Double.parseDouble(parts[0].trim());
					double temp = Double.parseDouble(parts[1].trim());
					double press = Double.parseDouble(parts[2].trim());
					rows.add(new double[] { alt, temp, press });
				} catch (NumberFormatException e) {
					throw new IOException("Cannot parse numeric values in: " + line, e);
				}
			}
		}

		if (rows.size() < 2) {
			throw new IOException("Need at least 2 data rows, got " + rows.size());
		}

		double[] alts = new double[rows.size()];
		double[] temps = new double[rows.size()];
		double[] press = new double[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			double[] row = rows.get(i);
			alts[i] = row[0];
			temps[i] = row[1];
			press[i] = row[2];
		}

		return new AtmosphericSounding(alts, temps, press, sourceName);
	}

	/**
	 * Create an {@link AtmosphericSounding} equivalent to the US Standard Atmosphere 1976.
	 * Generates data points every 1000 m from 0 to 80 000 m using the built-in
	 * {@link ExtendedISAModel}. Useful for testing -- results should closely match
	 * the built-in model.
	 *
	 * @return a sounding representing the standard atmosphere
	 */
	public static AtmosphericSounding standardAtmosphere() {
		ExtendedISAModel isa = new ExtendedISAModel();
		int n = 81; // 0 to 80000 inclusive, step 1000
		double[] alts = new double[n];
		double[] temps = new double[n];
		double[] press = new double[n];

		for (int i = 0; i < n; i++) {
			double alt = i * 1000.0;
			AtmosphericConditions cond = isa.getConditions(alt);
			alts[i] = alt;
			temps[i] = cond.getTemperature();
			press[i] = cond.getPressure();
		}

		return new AtmosphericSounding(alts, temps, press, "US Standard Atmosphere 1976");
	}

	/**
	 * Get interpolated temperature at the given altitude.
	 *
	 * @param altitude altitude in metres
	 * @return temperature in Kelvin
	 */
	public double getTemperature(double altitude) {
		return interpolate(altitudes, temperatures, altitude);
	}

	/**
	 * Get interpolated pressure at the given altitude.
	 *
	 * @param altitude altitude in metres
	 * @return pressure in Pascals
	 */
	public double getPressure(double altitude) {
		return interpolate(altitudes, pressures, altitude);
	}

	/**
	 * Get interpolated density at the given altitude using the ideal gas law.
	 * {@code rho = p / (R * T)}
	 *
	 * @param altitude altitude in metres
	 * @return density in kg/m^3
	 */
	public double getDensity(double altitude) {
		return getPressure(altitude) / (R_AIR * getTemperature(altitude));
	}

	/**
	 * Get speed of sound at the given altitude.
	 * {@code a = sqrt(gamma * R * T)}
	 *
	 * @param altitude altitude in metres
	 * @return speed of sound in m/s
	 */
	public double getSpeedOfSound(double altitude) {
		return Math.sqrt(GAMMA * R_AIR * getTemperature(altitude));
	}

	/** @return number of data points in this sounding */
	public int getDataPointCount() {
		return altitudes.length;
	}

	/** @return minimum altitude in the data set (metres) */
	public double getMinAltitude() {
		return altitudes[0];
	}

	/** @return maximum altitude in the data set (metres) */
	public double getMaxAltitude() {
		return altitudes[altitudes.length - 1];
	}

	/** @return descriptive source name */
	public String getSourceName() {
		return sourceName;
	}

	// ---- private helpers ----

	/**
	 * Linear interpolation with clamping at boundaries.
	 */
	private static double interpolate(double[] x, double[] y, double xVal) {
		if (xVal <= x[0]) {
			return y[0];
		}
		if (xVal >= x[x.length - 1]) {
			return y[y.length - 1];
		}

		// Binary search for the bracketing interval
		int lo = 0;
		int hi = x.length - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (x[mid] <= xVal) {
				lo = mid;
			} else {
				hi = mid;
			}
		}

		double fraction = (xVal - x[lo]) / (x[hi] - x[lo]);
		return y[lo] + fraction * (y[hi] - y[lo]);
	}
}
