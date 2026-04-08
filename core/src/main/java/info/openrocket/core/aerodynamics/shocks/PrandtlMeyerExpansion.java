package info.openrocket.core.aerodynamics.shocks;

/**
 * Prandtl-Meyer expansion fan relations for a calorically perfect gas.
 * <p>
 * A Prandtl-Meyer expansion fan occurs when supersonic flow encounters a
 * convex corner (expansion). The flow accelerates isentropically — no
 * entropy is produced, unlike a shock.
 * <p>
 * The key function is the Prandtl-Meyer angle nu(M), which gives the
 * total turning angle from M=1 to a given Mach number.
 * <p>
 * Reference: NACA Report 1135 — "Equations, Tables, and Charts for
 * Compressible Flow" (Ames Research Staff, 1953).
 */
public final class PrandtlMeyerExpansion {

	/** Ratio of specific heats for air */
	public static final double GAMMA_AIR = NormalShockRelations.GAMMA_AIR;

	/** Maximum Prandtl-Meyer angle (M → ∞) for gamma = 1.4: (sqrt(6)-1)*90° ≈ 130.45° */
	public static final double NU_MAX_AIR = nuMax(GAMMA_AIR);

	/** Convergence tolerance for inverse calculations */
	private static final double TOL = 1e-12;

	/** Maximum Newton iterations */
	private static final int MAX_ITER = 100;

	private PrandtlMeyerExpansion() {
		// Utility class
	}

	/**
	 * Prandtl-Meyer function: angle nu(M) in radians.
	 * <p>
	 * nu(M) = sqrt((g+1)/(g-1)) * atan(sqrt((g-1)/(g+1) * (M²-1))) - atan(sqrt(M²-1))
	 *
	 * @param mach  Mach number (must be >= 1)
	 * @param gamma ratio of specific heats
	 * @return Prandtl-Meyer angle in radians
	 */
	public static double nu(double mach, double gamma) {
		if (mach < 1.0) {
			throw new IllegalArgumentException("Prandtl-Meyer function requires M >= 1.0, got " + mach);
		}
		if (mach == 1.0) {
			return 0.0;
		}
		double gp1 = gamma + 1.0;
		double gm1 = gamma - 1.0;
		double sqrtRatio = Math.sqrt(gp1 / gm1);
		double m2m1 = mach * mach - 1.0;
		return sqrtRatio * Math.atan(Math.sqrt(gm1 / gp1 * m2m1)) - Math.atan(Math.sqrt(m2m1));
	}

	/**
	 * Prandtl-Meyer function for air (gamma = 1.4).
	 */
	public static double nu(double mach) {
		return nu(mach, GAMMA_AIR);
	}

	/**
	 * Maximum Prandtl-Meyer angle (M → ∞) in radians.
	 * nu_max = (pi/2) * (sqrt((g+1)/(g-1)) - 1)
	 *
	 * @param gamma ratio of specific heats
	 * @return maximum expansion angle in radians
	 */
	public static double nuMax(double gamma) {
		return (Math.PI / 2.0) * (Math.sqrt((gamma + 1.0) / (gamma - 1.0)) - 1.0);
	}

	/**
	 * Inverse Prandtl-Meyer function: find Mach number from nu angle.
	 * Uses Newton's method.
	 *
	 * @param nuTarget target Prandtl-Meyer angle in radians
	 * @param gamma    ratio of specific heats
	 * @return Mach number corresponding to nuTarget
	 * @throws IllegalArgumentException if nuTarget < 0 or exceeds nu_max
	 */
	public static double machFromNu(double nuTarget, double gamma) {
		if (nuTarget < 0.0) {
			throw new IllegalArgumentException("Prandtl-Meyer angle must be >= 0, got " + nuTarget);
		}
		if (nuTarget < 1e-12) {
			return 1.0;
		}
		double maxNu = nuMax(gamma);
		if (nuTarget > maxNu + 1e-8) {
			throw new IllegalArgumentException(String.format(
					"Prandtl-Meyer angle %.4f rad exceeds maximum %.4f rad",
					nuTarget, maxNu));
		}

		// Initial guess using Stanyukovich's approximation
		double nNorm = nuTarget / maxNu;
		double mGuess = 1.0 + 1.3604 * Math.pow(nNorm, 0.55);
		if (mGuess < 1.0) mGuess = 1.0 + 0.01;

		// Newton iteration
		double m = mGuess;
		for (int i = 0; i < MAX_ITER; i++) {
			double f = nu(m, gamma) - nuTarget;
			double dfdm = dnuDm(m, gamma);

			if (Math.abs(dfdm) < 1e-30) break;

			double delta = -f / dfdm;
			m += delta;

			if (m < 1.0) m = 1.0 + 1e-8;

			if (Math.abs(delta) < TOL) break;
		}

		return m;
	}

	/**
	 * Inverse Prandtl-Meyer function for air (gamma = 1.4).
	 */
	public static double machFromNu(double nuTarget) {
		return machFromNu(nuTarget, GAMMA_AIR);
	}

	/**
	 * Derivative of the Prandtl-Meyer function: d(nu)/d(M).
	 * <p>
	 * d(nu)/dM = sqrt(M²-1) / (1 + (gamma-1)/2 * M²) / M
	 *
	 * @param mach  Mach number (must be >= 1)
	 * @param gamma ratio of specific heats
	 * @return derivative in rad per unit Mach
	 */
	public static double dnuDm(double mach, double gamma) {
		if (mach <= 1.0) return 0.0;
		double m2 = mach * mach;
		return Math.sqrt(m2 - 1.0) / (1.0 + (gamma - 1.0) / 2.0 * m2) / mach;
	}

	// ---- Expansion fan solutions ----

	/**
	 * Compute the downstream Mach number after an expansion through a given
	 * turning angle.
	 * <p>
	 * When supersonic flow turns away from itself by an angle delta (convex corner),
	 * the downstream Mach number is found from:
	 * nu(M2) = nu(M1) + delta
	 *
	 * @param m1    upstream Mach number (must be >= 1)
	 * @param delta expansion turning angle in radians (must be >= 0)
	 * @param gamma ratio of specific heats
	 * @return downstream Mach number (always > m1)
	 * @throws IllegalArgumentException if the expansion exceeds the maximum Prandtl-Meyer angle
	 */
	public static double downstreamMach(double m1, double delta, double gamma) {
		if (m1 < 1.0) {
			throw new IllegalArgumentException("Expansion requires M >= 1.0, got " + m1);
		}
		if (delta < 0.0) {
			throw new IllegalArgumentException("Expansion angle must be >= 0, got " + delta);
		}
		if (delta == 0.0) {
			return m1;
		}
		double nu2 = nu(m1, gamma) + delta;
		return machFromNu(nu2, gamma);
	}

	/**
	 * Downstream Mach after expansion for air (gamma = 1.4).
	 */
	public static double downstreamMach(double m1, double delta) {
		return downstreamMach(m1, delta, GAMMA_AIR);
	}

	/**
	 * Compute the isentropic pressure ratio across an expansion fan: p2/p1.
	 * <p>
	 * Since the expansion is isentropic:
	 * p2/p1 = [(1 + (g-1)/2 * M1²) / (1 + (g-1)/2 * M2²)]^(gamma/(gamma-1))
	 *
	 * @param m1    upstream Mach number
	 * @param m2    downstream Mach number (from downstreamMach)
	 * @param gamma ratio of specific heats
	 * @return pressure ratio p2/p1 (always < 1 for expansion)
	 */
	public static double pressureRatio(double m1, double m2, double gamma) {
		double gm1h = (gamma - 1.0) / 2.0;
		double exp = gamma / (gamma - 1.0);
		return Math.pow((1.0 + gm1h * m1 * m1) / (1.0 + gm1h * m2 * m2), exp);
	}

	/**
	 * Pressure ratio for air (gamma = 1.4).
	 */
	public static double pressureRatio(double m1, double m2) {
		return pressureRatio(m1, m2, GAMMA_AIR);
	}

	/**
	 * Compute the isentropic temperature ratio across an expansion fan: T2/T1.
	 *
	 * @param m1    upstream Mach number
	 * @param m2    downstream Mach number
	 * @param gamma ratio of specific heats
	 * @return temperature ratio T2/T1 (always < 1 for expansion)
	 */
	public static double temperatureRatio(double m1, double m2, double gamma) {
		double gm1h = (gamma - 1.0) / 2.0;
		return (1.0 + gm1h * m1 * m1) / (1.0 + gm1h * m2 * m2);
	}

	/**
	 * Temperature ratio for air (gamma = 1.4).
	 */
	public static double temperatureRatio(double m1, double m2) {
		return temperatureRatio(m1, m2, GAMMA_AIR);
	}

	/**
	 * Full expansion fan result: given upstream Mach and turning angle,
	 * compute all downstream properties.
	 *
	 * @param m1    upstream Mach number
	 * @param delta expansion turning angle in radians
	 * @param gamma ratio of specific heats
	 * @return downstream Mach, pressure ratio, temperature ratio, density ratio
	 */
	public static ExpansionResult solve(double m1, double delta, double gamma) {
		double m2 = downstreamMach(m1, delta, gamma);
		double pRatio = pressureRatio(m1, m2, gamma);
		double tRatio = temperatureRatio(m1, m2, gamma);
		double rhoRatio = pRatio / tRatio; // From ideal gas law
		return new ExpansionResult(m1, m2, delta, pRatio, tRatio, rhoRatio);
	}

	/**
	 * Full expansion result for air (gamma = 1.4).
	 */
	public static ExpansionResult solve(double m1, double delta) {
		return solve(m1, delta, GAMMA_AIR);
	}

	/**
	 * Result of a Prandtl-Meyer expansion calculation.
	 */
	public static class ExpansionResult {
		public final double m1;
		public final double m2;
		public final double turningAngle;
		public final double pressureRatio;
		public final double temperatureRatio;
		public final double densityRatio;

		public ExpansionResult(double m1, double m2, double turningAngle,
				double pressureRatio, double temperatureRatio, double densityRatio) {
			this.m1 = m1;
			this.m2 = m2;
			this.turningAngle = turningAngle;
			this.pressureRatio = pressureRatio;
			this.temperatureRatio = temperatureRatio;
			this.densityRatio = densityRatio;
		}
	}
}
