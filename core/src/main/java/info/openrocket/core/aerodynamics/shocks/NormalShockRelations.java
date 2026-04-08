package info.openrocket.core.aerodynamics.shocks;

/**
 * Normal shock jump relations for a calorically perfect gas.
 * <p>
 * All relations are exact analytical solutions from gas dynamics theory.
 * Default gamma = 1.4 (air). Validated against NACA Report 1135 tables.
 * <p>
 * Reference: NACA Report 1135 — "Equations, Tables, and Charts for
 * Compressible Flow" (Ames Research Staff, 1953).
 */
public final class NormalShockRelations {

	/** Ratio of specific heats for air */
	public static final double GAMMA_AIR = 1.4;

	private NormalShockRelations() {
		// Utility class
	}

	// ---- Static pressure ratio p2/p1 ----

	/**
	 * Static pressure ratio across a normal shock: p2/p1.
	 *
	 * @param m1    upstream Mach number (must be >= 1)
	 * @param gamma ratio of specific heats
	 * @return p2/p1
	 */
	public static double pressureRatio(double m1, double gamma) {
		validateSupersonic(m1);
		double m1sq = m1 * m1;
		return 1.0 + 2.0 * gamma / (gamma + 1.0) * (m1sq - 1.0);
	}

	/**
	 * Static pressure ratio across a normal shock for air (gamma = 1.4).
	 */
	public static double pressureRatio(double m1) {
		return pressureRatio(m1, GAMMA_AIR);
	}

	// ---- Density ratio rho2/rho1 (= velocity ratio V1/V2) ----

	/**
	 * Density ratio across a normal shock: rho2/rho1.
	 * Also equals upstream/downstream velocity ratio V1/V2.
	 *
	 * @param m1    upstream Mach number (must be >= 1)
	 * @param gamma ratio of specific heats
	 * @return rho2/rho1
	 */
	public static double densityRatio(double m1, double gamma) {
		validateSupersonic(m1);
		double m1sq = m1 * m1;
		double gp1 = gamma + 1.0;
		double gm1 = gamma - 1.0;
		return gp1 * m1sq / (gm1 * m1sq + 2.0);
	}

	/**
	 * Density ratio across a normal shock for air (gamma = 1.4).
	 */
	public static double densityRatio(double m1) {
		return densityRatio(m1, GAMMA_AIR);
	}

	// ---- Temperature ratio T2/T1 ----

	/**
	 * Static temperature ratio across a normal shock: T2/T1.
	 *
	 * @param m1    upstream Mach number (must be >= 1)
	 * @param gamma ratio of specific heats
	 * @return T2/T1
	 */
	public static double temperatureRatio(double m1, double gamma) {
		return pressureRatio(m1, gamma) / densityRatio(m1, gamma);
	}

	/**
	 * Static temperature ratio across a normal shock for air (gamma = 1.4).
	 */
	public static double temperatureRatio(double m1) {
		return temperatureRatio(m1, GAMMA_AIR);
	}

	// ---- Downstream Mach number M2 ----

	/**
	 * Downstream Mach number after a normal shock.
	 *
	 * @param m1    upstream Mach number (must be >= 1)
	 * @param gamma ratio of specific heats
	 * @return downstream Mach number (always < 1 for m1 > 1)
	 */
	public static double downstreamMach(double m1, double gamma) {
		validateSupersonic(m1);
		double m1sq = m1 * m1;
		double gm1 = gamma - 1.0;
		double gp1 = gamma + 1.0;
		double m2sq = (m1sq + 2.0 / gm1) / (2.0 * gamma / gm1 * m1sq - 1.0);
		return Math.sqrt(m2sq);
	}

	/**
	 * Downstream Mach number after a normal shock for air (gamma = 1.4).
	 */
	public static double downstreamMach(double m1) {
		return downstreamMach(m1, GAMMA_AIR);
	}

	// ---- Total pressure ratio p02/p01 ----

	/**
	 * Total (stagnation) pressure ratio across a normal shock: p02/p01.
	 * This is always <= 1 (total pressure decreases across a shock).
	 *
	 * @param m1    upstream Mach number (must be >= 1)
	 * @param gamma ratio of specific heats
	 * @return p02/p01
	 */
	public static double totalPressureRatio(double m1, double gamma) {
		validateSupersonic(m1);
		double m1sq = m1 * m1;
		double gm1 = gamma - 1.0;
		double gp1 = gamma + 1.0;

		// Rayleigh pitot formula:
		// p02/p01 = [ (gp1 * M1^2) / (gm1 * M1^2 + 2) ]^(gamma/(gamma-1))
		//         * [ (2*gamma*M1^2 - gm1) / gp1 ]^(-1/(gamma-1))
		double term1 = gp1 * m1sq / (gm1 * m1sq + 2.0);
		double term2 = (2.0 * gamma * m1sq - gm1) / gp1;
		return Math.pow(term1, gamma / gm1) * Math.pow(term2, -1.0 / gm1);
	}

	/**
	 * Total (stagnation) pressure ratio across a normal shock for air (gamma = 1.4).
	 */
	public static double totalPressureRatio(double m1) {
		return totalPressureRatio(m1, GAMMA_AIR);
	}

	// ---- Mach number from pressure ratio (inverse) ----

	/**
	 * Compute upstream Mach number from the static pressure ratio p2/p1.
	 *
	 * @param pressRatio static pressure ratio p2/p1 (must be >= 1)
	 * @param gamma      ratio of specific heats
	 * @return upstream Mach number
	 */
	public static double machFromPressureRatio(double pressRatio, double gamma) {
		if (pressRatio < 1.0) {
			throw new IllegalArgumentException(
					"Pressure ratio must be >= 1.0 for a normal shock (got " + pressRatio + ")");
		}
		double gp1 = gamma + 1.0;
		double m1sq = (pressRatio - 1.0) * gp1 / (2.0 * gamma) + 1.0;
		return Math.sqrt(m1sq);
	}

	/**
	 * Compute upstream Mach number from the static pressure ratio for air (gamma = 1.4).
	 */
	public static double machFromPressureRatio(double pressRatio) {
		return machFromPressureRatio(pressRatio, GAMMA_AIR);
	}

	private static void validateSupersonic(double m1) {
		if (m1 < 1.0) {
			throw new IllegalArgumentException(
					"Normal shock requires supersonic upstream Mach >= 1.0, got " + m1);
		}
	}
}
