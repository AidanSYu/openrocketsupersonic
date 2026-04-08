package info.openrocket.core.models.atmosphere;

import info.openrocket.core.util.BugException;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.Monitorable;
import info.openrocket.core.util.ModID;

/**
 * Represents atmospheric conditions at a specific point, containing fundamental
 * properties (temperature and pressure) and methods to calculate derived properties.
 * This class serves as the basic unit of atmospheric data in the simulation.
 */
public class AtmosphericConditions implements Cloneable, Monitorable {

	/** Specific gas constant of dry air (J/(kg*K)). */
	public static final double R = 287.053;

	/** Specific heat ratio of air (dimensionless). */
	public static final double GAMMA = 1.4;

	/** Ratio of the molar mass of water vapor and dry air */
	public static final double EPSILON = 0.622;

	/** The standard air pressure (Pa). */
	public static final double STANDARD_PRESSURE = 101325.0;

	/** The standard air temperature (K). */
	public static final double STANDARD_TEMPERATURE = 293.15;

	/** The standard air humidity. */
	public static final double STANDARD_HUMIDITY = 0;

	/** Air pressure, in Pascals. */
	private double pressure;

	/** Air temperature, in Kelvins. */
	private double temperature;

	/** Relative air humidity. */
	private double relativeHumidity;

	private ModID modID;

	/**
	 * Construct standard atmospheric conditions.
	 */
	public AtmosphericConditions() {
		this(STANDARD_TEMPERATURE, STANDARD_PRESSURE, STANDARD_HUMIDITY);
	}

	public AtmosphericConditions(double temperature, double pressure) {
		this.setTemperature(temperature);
		this.setPressure(pressure);
		this.setRelativeHumidity(STANDARD_HUMIDITY);
		this.modID = new ModID();
	}

	/**
	 * Construct specified atmospheric conditions.
	 * 
	 * @param temperature the temperature in Kelvins.
	 * @param pressure    the pressure in Pascals.
	 */
	public AtmosphericConditions(double temperature, double pressure, double relativeHumidity) {
		this.setTemperature(temperature);
		this.setPressure(pressure);
		this.setRelativeHumidity(relativeHumidity);
		this.modID = new ModID();
	}

	public double getPressure() {
		return pressure;
	}

	public void setPressure(double pressure) {
		if (pressure <= 0) {
			throw new IllegalArgumentException("Pressure must be positive (Pascals)");
		}
		this.pressure = pressure;
		this.modID = new ModID();
	}

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		if (temperature <= 0) {
			throw new IllegalArgumentException("Temperature must be positive (Kelvin)");
		}
		this.temperature = temperature;
		this.modID = new ModID();
	}

	/**
	 * Get the relative humidity (0 to 1).
	 * @return the relative humidity
	 */
	public double getRelativeHumidity() {
		return relativeHumidity;
	}

	/**
	 * Set the relative humidity (0 to 1).
	 * @param relativeHumidity the relative humidity
	 */
	public void setRelativeHumidity(double relativeHumidity) {
		if (relativeHumidity < 0 || relativeHumidity > 1) {
			throw new IllegalArgumentException("Humidity must be between 0 and 1");
		}
		this.relativeHumidity = relativeHumidity;
		this.modID = new ModID();
	}

	/**
	 * Calculate the saturation water pressure using the Clausius-Clapeyron equation.
	 * @return The saturation vapor pressure in Pa
	 */
	public double vaporPressureSaturation() {
		// 611.3 * Math.exp(5423 * (1/273.15 - 1/getTemperature()));
		return 611.3 * Math.exp(19.854 - 5423/getTemperature());
	}

	/**
	 * Calculate the gas constant of humid air.
     * - EPSILON is the ratio of the molar mass of water vapor and dry air
     * - R is the gas constant of dry air
     * - e_s(T) is the temperature-dependent saturation vapor pressure
     * - RH is the relative humidity
     *
     * @return The gas constant of air in J/kg*K. R if humidity is 0
	 */
    public double getGasConstant() {
		if (getRelativeHumidity() > 0) {
			double numerator = EPSILON * getRelativeHumidity() * vaporPressureSaturation();
			double denominator = getPressure() - getRelativeHumidity() * vaporPressureSaturation() * (1 - EPSILON);
			double scalingFactor = (1/EPSILON - 1);

			return R * (1 + numerator*scalingFactor/denominator);
		} else {
			return R;
		}


    }
	/**
	 * Calculate the current density of air using the ideal gas law for dry air.
	 * The formula used is rho = P/(R*T) where:
	 * - rho is the density in kg/m3
	 * - P is the pressure in Pa
	 * - R is the gas constant for air
	 * - T is the temperature in Kelvin
	 *
	 * @return The current air density in kg/m3
	 */
	public double getDensity() {
		return getPressure() / (getGasConstant() * getTemperature());
	}

	/**
	 * Return the current speed of sound.
	 * <p>
	 * Computed from the exact thermodynamic relation:
	 * <code>a = sqrt(gamma * R * T)</code>
	 * where gamma is the ratio of specific heats (1.4 for air), R is the specific
	 * gas constant of air (287.053 J/(kg·K)), and T is the temperature in Kelvin.
	 * <p>
	 * This replaces the previous linear approximation <code>c = 331.3 + 0.606*(T-273.15)</code>
	 * which was only accurate within ~30°C of 0°C. The exact formula is valid at
	 * all temperatures where air behaves as an ideal gas (up to ~2000K before
	 * dissociation effects become significant).
	 * <p>
	 * Reference: US Standard Atmosphere, 1976.
	 * 
	 * @return the current speed of sound in m/s.
	 */
	public double getMachSpeed() {
		return Math.sqrt(GAMMA * getGasConstant() * getTemperature());
	}

	/** Sutherland's law reference dynamic viscosity (Pa·s) at T_ref. */
	private static final double MU_REF = 1.716e-5;

	/** Sutherland's law reference temperature (K). */
	private static final double T_REF = 273.15;

	/** Sutherland's law constant for air (K). */
	private static final double S_SUTHERLAND = 110.4;

	/**
	 * Return the dynamic viscosity of air using Sutherland's law.
	 * <p>
	 * <code>mu = mu_ref * (T/T_ref)^(3/2) * (T_ref + S) / (T + S)</code>
	 * <p>
	 * where mu_ref = 1.716e-5 Pa·s at T_ref = 273.15 K, and S = 110.4 K.
	 * This is accurate from approximately 100 K to 1900 K.
	 * <p>
	 * This replaces the previous linear approximation which was only valid
	 * between -40°C and 40°C.
	 * <p>
	 * Reference: NIST; Sutherland, W. (1893). "The viscosity of gases and
	 * molecular force". Philosophical Magazine. 5 (36): 507–531.
	 *
	 * @return the dynamic viscosity in Pa·s (kg/(m·s)).
	 */
	public double getDynamicViscosity() {
		double T = getTemperature();
		return MU_REF * Math.pow(T / T_REF, 1.5) * (T_REF + S_SUTHERLAND) / (T + S_SUTHERLAND);
	}

	/**
	 * Return the current kinematic viscosity of the air.
	 * <p>
	 * Computed as dynamic viscosity divided by density: <code>nu = mu / rho</code>.
	 * The dynamic viscosity is calculated using Sutherland's law
	 * (see {@link #getDynamicViscosity()}).
	 * 
	 * @return the current kinematic viscosity in m²/s.
	 */
	public double getKinematicViscosity() {
		return getDynamicViscosity() / getDensity();
	}

	/**
	 * Compute the effective ratio of specific heats accounting for
	 * vibrational excitation at high temperatures (Phase 4c).
	 * <p>
	 * At temperatures below ~800 K, air behaves as a diatomic ideal gas
	 * with gamma = 1.4 (5 degrees of freedom: 3 translational + 2 rotational).
	 * Above ~800 K, vibrational modes of N2 and O2 become excited, adding
	 * energy storage capacity and reducing gamma toward ~1.3.
	 * <p>
	 * This uses a simplified model based on the characteristic vibrational
	 * temperatures of N2 (3371 K) and O2 (2256 K), weighted by their
	 * atmospheric fractions (0.79 N2, 0.21 O2).
	 * <p>
	 * Note: This does NOT account for dissociation (O2 above ~2500 K,
	 * N2 above ~4000 K) or ionization. Those effects require a full
	 * equilibrium chemistry solver and are beyond current scope.
	 *
	 * @param stagnationTemp the stagnation (total) temperature in Kelvin
	 * @return effective gamma, in range [1.3, 1.4]
	 */
	public static double effectiveGamma(double stagnationTemp) {
		if (stagnationTemp <= 800.0) {
			return GAMMA;
		}

		// Characteristic vibrational temperatures
		double thetaN2 = 3371.0; // K
		double thetaO2 = 2256.0; // K

		// Vibrational contribution to Cv (dimensionless, per molecule)
		// cv_vib = (theta/T)^2 * exp(theta/T) / (exp(theta/T) - 1)^2
		double cvVibN2 = vibrationalCv(stagnationTemp, thetaN2);
		double cvVibO2 = vibrationalCv(stagnationTemp, thetaO2);

		// Weighted average (79% N2, 21% O2)
		double cvVib = 0.79 * cvVibN2 + 0.21 * cvVibO2;

		// Base Cv for diatomic gas: 5/2 R (translational + rotational)
		// gamma = (Cv + R) / Cv = 1 + R/Cv
		// With vibrational: Cv_total = 5/2 + cvVib (in units of R)
		double cvTotal = 2.5 + cvVib;
		double gamma = (cvTotal + 1.0) / cvTotal;

		// Clamp to physically reasonable range
		return Math.max(1.3, Math.min(GAMMA, gamma));
	}

	/**
	 * Dimensionless vibrational specific heat contribution using the
	 * Einstein model for a harmonic oscillator.
	 *
	 * @param T     temperature (K)
	 * @param theta characteristic vibrational temperature (K)
	 * @return vibrational Cv / R (dimensionless)
	 */
	private static double vibrationalCv(double T, double theta) {
		if (T < 100.0) return 0;
		double x = theta / T;
		if (x > 50.0) return 0; // exp overflow guard
		double ex = Math.exp(x);
		double denom = (ex - 1.0);
		return x * x * ex / (denom * denom);
	}

	/**
	 * Return a copy of the atmospheric conditions.
	 */
	@Override
	public AtmosphericConditions clone() {
		try {
			return (AtmosphericConditions) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new BugException("CloneNotSupportedException encountered!");
		}
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof AtmosphericConditions))
			return false;
		AtmosphericConditions o = (AtmosphericConditions) other;
		return MathUtil.equals(this.pressure, o.pressure) && MathUtil.equals(this.temperature, o.temperature);
	}

	@Override
	public int hashCode() {
		return (int) (this.pressure + this.temperature * 1000);
	}

	@Override
	public ModID getModID() {
		return modID;
	}

	@Override
	public String toString() {
		return String.format("AtmosphericConditions[T=%.2f,P=%.2f]", getTemperature(), getPressure());
	}

}
