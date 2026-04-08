package info.openrocket.core.aerodynamics.shocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Oblique shock relations: theta-beta-Mach equation and post-shock conditions.
 * <p>
 * Given a freestream Mach number and a deflection angle (wedge/cone half-angle),
 * computes the shock wave angle and all downstream flow properties.
 * <p>
 * The theta-beta-Mach relation is transcendental and solved iteratively
 * using bisection for robustness.
 * <p>
 * Reference: NACA Report 1135 — "Equations, Tables, and Charts for
 * Compressible Flow" (Ames Research Staff, 1953).
 */
public final class ObliqueShockSolver {

	private static final Logger logger = LoggerFactory.getLogger(ObliqueShockSolver.class);

	/** Ratio of specific heats for air */
	public static final double GAMMA_AIR = NormalShockRelations.GAMMA_AIR;

	/** Convergence tolerance for Newton iterations */
	private static final double TOL = 1e-12;

	/** Maximum Newton iterations */
	private static final int MAX_ITER = 100;

	// Cache for betaAtMaxDeflection — avoids repeated golden section searches
	// with the same (m1, gamma) within a single solve() call.
	private static double cachedBetaMaxM1 = Double.NaN;
	private static double cachedBetaMaxGamma = Double.NaN;
	private static double cachedBetaMaxResult = Double.NaN;

	private ObliqueShockSolver() {
		// Utility class
	}

	/**
	 * Result of an oblique shock calculation.
	 */
	public static class ObliqueShockResult {
		/** Shock wave angle in radians */
		public final double beta;
		/** Flow deflection angle in radians */
		public final double theta;
		/** Upstream Mach number */
		public final double m1;
		/** Downstream Mach number */
		public final double m2;
		/** Static pressure ratio p2/p1 */
		public final double pressureRatio;
		/** Static temperature ratio T2/T1 */
		public final double temperatureRatio;
		/** Density ratio rho2/rho1 */
		public final double densityRatio;
		/** Total pressure ratio p02/p01 */
		public final double totalPressureRatio;
		/** Whether this is the weak shock solution */
		public final boolean isWeakShock;

		public ObliqueShockResult(double beta, double theta, double m1, double m2,
				double pressureRatio, double temperatureRatio,
				double densityRatio, double totalPressureRatio, boolean isWeakShock) {
			this.beta = beta;
			this.theta = theta;
			this.m1 = m1;
			this.m2 = m2;
			this.pressureRatio = pressureRatio;
			this.temperatureRatio = temperatureRatio;
			this.densityRatio = densityRatio;
			this.totalPressureRatio = totalPressureRatio;
			this.isWeakShock = isWeakShock;
		}
	}

	// ---- Theta-Beta-Mach relation ----

	/**
	 * Compute the flow deflection angle theta from the shock angle beta
	 * and upstream Mach number.
	 * <p>
	 * tan(theta) = 2 * cot(beta) * (M1^2 * sin^2(beta) - 1) /
	 *              (M1^2 * (gamma + cos(2*beta)) + 2)
	 *
	 * @param m1    upstream Mach number (must be > 1)
	 * @param beta  shock wave angle in radians
	 * @param gamma ratio of specific heats
	 * @return flow deflection angle theta in radians
	 */
	public static double thetaFromBeta(double m1, double beta, double gamma) {
		double m1sq = m1 * m1;
		double sinB = Math.sin(beta);
		double cosB = Math.cos(beta);
		double sin2B = sinB * sinB;

		double numerator = 2.0 * cosB / sinB * (m1sq * sin2B - 1.0);
		double denominator = m1sq * (gamma + Math.cos(2.0 * beta)) + 2.0;

		return Math.atan(numerator / denominator);
	}

	/**
	 * Compute the flow deflection angle theta for air (gamma = 1.4).
	 */
	public static double thetaFromBeta(double m1, double beta) {
		return thetaFromBeta(m1, beta, GAMMA_AIR);
	}

	/**
	 * Compute the maximum deflection angle for a given Mach number.
	 * Beyond this angle, the shock detaches and becomes a bow shock.
	 *
	 * @param m1    upstream Mach number (must be > 1)
	 * @param gamma ratio of specific heats
	 * @return maximum deflection angle in radians
	 */
	public static double maxDeflectionAngle(double m1, double gamma) {
		if (m1 <= 1.0) {
			return 0.0;
		}
		double betaAtMax = betaAtMaxDeflection(m1, gamma);
		return thetaFromBeta(m1, betaAtMax, gamma);
	}

	/**
	 * Maximum deflection angle for air (gamma = 1.4).
	 */
	public static double maxDeflectionAngle(double m1) {
		return maxDeflectionAngle(m1, GAMMA_AIR);
	}

	/**
	 * Find the shock angle beta at which the deflection angle is maximized.
	 * This divides weak solutions (beta < betaMax) from strong solutions (beta > betaMax).
	 */
	private static double betaAtMaxDeflection(double m1, double gamma) {
		// Check cache — this method is called multiple times per solve() with the same args
		if (Math.abs(m1 - cachedBetaMaxM1) < 1e-12 && Math.abs(gamma - cachedBetaMaxGamma) < 1e-12) {
			return cachedBetaMaxResult;
		}

		double machAngle = Math.asin(1.0 / m1);
		double lo = machAngle + 1e-10;
		double hi = Math.PI / 2.0 - 1e-10;

		// Golden section search for maximum theta
		double gr = (Math.sqrt(5.0) - 1.0) / 2.0;
		while (hi - lo > TOL) {
			double b1 = hi - gr * (hi - lo);
			double b2 = lo + gr * (hi - lo);
			double t1 = thetaFromBeta(m1, b1, gamma);
			double t2 = thetaFromBeta(m1, b2, gamma);
			if (t1 < t2) {
				lo = b1;
			} else {
				hi = b2;
			}
		}
		double result = (lo + hi) / 2.0;

		// Update cache
		cachedBetaMaxM1 = m1;
		cachedBetaMaxGamma = gamma;
		cachedBetaMaxResult = result;

		return result;
	}

	// ---- Solve for shock angle beta given theta and M1 ----

	/**
	 * Solve for the oblique shock wave angle beta given upstream Mach and
	 * flow deflection angle. Returns the weak shock solution by default.
	 *
	 * @param m1    upstream Mach number (must be > 1)
	 * @param theta flow deflection angle in radians (must be > 0)
	 * @param gamma ratio of specific heats
	 * @return shock wave angle beta in radians (weak solution)
	 * @throws IllegalArgumentException if theta exceeds the maximum deflection angle
	 */
	public static double betaFromTheta(double m1, double theta, double gamma) {
		return betaFromTheta(m1, theta, gamma, true);
	}

	/**
	 * Solve for the oblique shock wave angle beta for air (gamma = 1.4).
	 */
	public static double betaFromTheta(double m1, double theta) {
		return betaFromTheta(m1, theta, GAMMA_AIR, true);
	}

	/**
	 * Solve for the oblique shock wave angle beta, choosing weak or strong solution.
	 * <p>
	 * Uses bisection for robustness. The theta(beta) curve has a single maximum
	 * at betaMax. The weak solution lies in [machAngle, betaMax] and the strong
	 * solution lies in [betaMax, pi/2].
	 *
	 * @param m1       upstream Mach number (must be > 1)
	 * @param theta    flow deflection angle in radians (must be > 0)
	 * @param gamma    ratio of specific heats
	 * @param wantWeak true for the weak shock solution, false for strong
	 * @return shock wave angle beta in radians
	 * @throws IllegalArgumentException if theta exceeds the maximum deflection angle
	 */
	public static double betaFromTheta(double m1, double theta, double gamma, boolean wantWeak) {
		if (m1 <= 1.0) {
			throw new IllegalArgumentException("Oblique shock requires M1 > 1.0, got " + m1);
		}
		if (theta <= 0.0) {
			// Zero deflection → Mach wave
			return Math.asin(1.0 / m1);
		}

		double thetaMax = maxDeflectionAngle(m1, gamma);
		if (theta > thetaMax + 1e-8) {
			throw new IllegalArgumentException(String.format(
					"Deflection angle %.4f° exceeds max %.4f° for M=%.3f (shock detachment)",
					Math.toDegrees(theta), Math.toDegrees(thetaMax), m1));
		}
		// Clamp to max if within tolerance
		if (theta > thetaMax) {
			theta = thetaMax;
		}

		double machAngle = Math.asin(1.0 / m1);
		double betaMax = betaAtMaxDeflection(m1, gamma);

		// Bisection: theta(beta) is monotonically increasing on [machAngle, betaMax] (weak)
		// and monotonically decreasing on [betaMax, pi/2] (strong)
		double lo, hi;
		if (wantWeak) {
			lo = machAngle + 1e-10;
			hi = betaMax;
		} else {
			lo = betaMax;
			hi = Math.PI / 2.0 - 1e-10;
		}

		for (int i = 0; i < MAX_ITER; i++) {
			double mid = 0.5 * (lo + hi);
			double thetaMid = thetaFromBeta(m1, mid, gamma);
			double err = thetaMid - theta;

			if (Math.abs(err) < TOL || (hi - lo) < TOL) {
				return mid;
			}

			if (wantWeak) {
				// theta increases with beta on weak branch
				if (thetaMid < theta) {
					lo = mid;
				} else {
					hi = mid;
				}
			} else {
				// theta decreases with beta on strong branch
				if (thetaMid < theta) {
					hi = mid;
				} else {
					lo = mid;
				}
			}
		}

		return 0.5 * (lo + hi);
	}

	// ---- Full oblique shock solution ----

	/**
	 * Compute all post-shock conditions for an oblique shock.
	 * <p>
	 * The oblique shock relations reduce to normal shock relations
	 * applied to the Mach number component normal to the shock: Mn1 = M1 * sin(beta).
	 *
	 * @param m1       upstream Mach number (must be > 1)
	 * @param theta    flow deflection angle in radians
	 * @param gamma    ratio of specific heats
	 * @param wantWeak true for weak shock, false for strong
	 * @return complete shock result
	 */
	public static ObliqueShockResult solve(double m1, double theta, double gamma, boolean wantWeak) {
		double beta = betaFromTheta(m1, theta, gamma, wantWeak);
		return solveFromBeta(m1, beta, theta, gamma, wantWeak);
	}

	/**
	 * Compute all post-shock conditions for a weak oblique shock in air.
	 */
	public static ObliqueShockResult solve(double m1, double theta) {
		return solve(m1, theta, GAMMA_AIR, true);
	}

	/**
	 * Compute post-shock conditions given a known shock angle beta.
	 * Useful when beta is determined by geometry (e.g., cone shock tables).
	 *
	 * @param m1    upstream Mach number
	 * @param beta  shock wave angle in radians
	 * @param gamma ratio of specific heats
	 * @return complete shock result
	 */
	public static ObliqueShockResult solveFromBeta(double m1, double beta, double gamma) {
		double theta = thetaFromBeta(m1, beta, gamma);
		// Determine weak vs strong by comparing to max deflection beta
		double thetaMax = maxDeflectionAngle(m1, gamma);
		boolean isWeak = theta < thetaMax - 1e-8 ? beta < Math.PI / 2.0 : true;
		return solveFromBeta(m1, beta, theta, gamma, isWeak);
	}

	private static ObliqueShockResult solveFromBeta(double m1, double beta, double theta,
			double gamma, boolean isWeak) {
		// Normal component of upstream Mach
		double mn1 = m1 * Math.sin(beta);

		// Clamp mn1 to >= 1.0 (numerical safety for near-Mach-wave cases)
		if (mn1 < 1.0) {
			mn1 = 1.0;
		}

		// Apply normal shock relations to the normal Mach component
		double pRatio = NormalShockRelations.pressureRatio(mn1, gamma);
		double tRatio = NormalShockRelations.temperatureRatio(mn1, gamma);
		double rhoRatio = NormalShockRelations.densityRatio(mn1, gamma);
		double p0Ratio = NormalShockRelations.totalPressureRatio(mn1, gamma);

		// Downstream normal Mach component
		double mn2 = NormalShockRelations.downstreamMach(mn1, gamma);

		// Downstream Mach number (tangential component is preserved)
		double m2 = mn2 / Math.sin(beta - theta);

		return new ObliqueShockResult(beta, theta, m1, m2,
				pRatio, tRatio, rhoRatio, p0Ratio, isWeak);
	}

	// ---- Cone shock (Taylor-Maccoll) ----

	/**
	 * Solve for the conical shock angle for a cone in supersonic flow
	 * using the Taylor-Maccoll equation.
	 * <p>
	 * The cone shock angle is always less than the wedge shock angle for the
	 * same deflection angle because the 3D relief effect weakens the shock.
	 * This method uses an iterative shooting approach: guess a shock angle,
	 * integrate the Taylor-Maccoll ODE from the shock to the cone surface,
	 * and iterate until the flow is tangent to the cone.
	 *
	 * @param m1        freestream Mach number (must be > 1)
	 * @param coneAngle cone half-angle in radians
	 * @param gamma     ratio of specific heats
	 * @return shock wave angle in radians
	 * @throws IllegalArgumentException if no attached shock solution exists
	 */
	public static double coneShockAngle(double m1, double coneAngle, double gamma) {
		if (m1 <= 1.0) {
			throw new IllegalArgumentException("Cone shock requires M1 > 1.0, got " + m1);
		}

		double machAngle = Math.asin(1.0 / m1);

		// The cone shock angle lies between the Mach angle and the 2D wedge shock angle
		// for the same deflection. Use the wedge shock angle as the upper bound.
		// If the wedge is also detached, use a fallback upper bound.
		// Shock angle must be greater than both the Mach angle and the cone angle
		double betaLo = Math.max(machAngle, coneAngle) + 1e-6;
		double betaHi;
		try {
			// Wedge shock for same deflection angle — cone shock is always weaker
			betaHi = betaFromTheta(m1, coneAngle, gamma, true);
		} catch (IllegalArgumentException e) {
			// Wedge is detached; use the beta at max deflection as upper bound
			// (cone can still be attached due to 3D relief)
			betaHi = betaAtMaxDeflection(m1, gamma);
		}

		// Scan for a sign change in the residual (Vθ at cone surface).
		// Only need to find a bracket; bisection refinement handles convergence.
		int nScan = 40;
		double scanStep = (betaHi - betaLo) / nScan;
		double prevResidual = coneShockResidual(m1, betaLo, coneAngle, gamma);
		double bracketLo = betaLo, bracketHi = betaHi;
		boolean found = false;

		for (int i = 1; i <= nScan; i++) {
			double beta = betaLo + i * scanStep;
			double residual = coneShockResidual(m1, beta, coneAngle, gamma);
			if (Double.isNaN(residual)) continue;
			if (Double.isNaN(prevResidual)) {
				prevResidual = residual;
				bracketLo = beta;
				continue;
			}
			if (prevResidual * residual < 0) {
				bracketLo = beta - scanStep;
				bracketHi = beta;
				found = true;
				break;
			}
			prevResidual = residual;
			bracketLo = beta;
		}

		if (!found) {
			throw new IllegalArgumentException(String.format(
					"No attached cone shock for M=%.3f, cone half-angle=%.2f°",
					m1, Math.toDegrees(coneAngle)));
		}

		// Bisection within the bracket
		double rLo = coneShockResidual(m1, bracketLo, coneAngle, gamma);
		for (int i = 0; i < MAX_ITER; i++) {
			double betaMid = 0.5 * (bracketLo + bracketHi);
			double residual = coneShockResidual(m1, betaMid, coneAngle, gamma);

			if (Double.isNaN(residual) || Math.abs(residual) < TOL || (bracketHi - bracketLo) < TOL) {
				if (Double.isNaN(residual)) {
					bracketHi = betaMid;
					continue;
				}
				return betaMid;
			}

			if (residual * rLo < 0) {
				bracketHi = betaMid;
			} else {
				bracketLo = betaMid;
				rLo = residual;
			}
		}

		return 0.5 * (bracketLo + bracketHi);
	}

	/**
	 * Cone shock angle for air (gamma = 1.4).
	 */
	public static double coneShockAngle(double m1, double coneAngle) {
		return coneShockAngle(m1, coneAngle, GAMMA_AIR);
	}

	/**
	 * Compute post-shock conditions on a cone surface using Taylor-Maccoll theory.
	 * <p>
	 * Returns the surface Mach number, pressure ratio, and temperature ratio
	 * on the cone surface (not immediately behind the shock).
	 *
	 * @param m1        freestream Mach number
	 * @param coneAngle cone half-angle in radians
	 * @param gamma     ratio of specific heats
	 * @return shock result with surface conditions
	 */
	public static ObliqueShockResult solveCone(double m1, double coneAngle, double gamma) {
		double betaCone = coneShockAngle(m1, coneAngle, gamma);

		// Post-shock conditions immediately behind the conical shock
		double mn1 = m1 * Math.sin(betaCone);
		if (mn1 < 1.0) mn1 = 1.0;

		// Integrate Taylor-Maccoll ODE to find surface conditions
		double[] surfaceState = taylorMaccollIntegrate(m1, betaCone, coneAngle, gamma);
		double vSurface = surfaceState[0]; // V/Vmax on cone surface
		double mSurface = vToMach(vSurface, gamma);

		// Surface conditions via total pressure path (isentropic from post-shock to surface).
		// Uses freestream total pressure → shock loss → isentropic expansion to surface Mach.
		// This avoids the numerically sensitive intermediate M2 computation.
		double gm1 = gamma - 1.0;
		double gm1h = gm1 / 2.0;
		double p0Ratio = NormalShockRelations.totalPressureRatio(mn1, gamma);
		double p01_over_p1 = Math.pow(1.0 + gm1h * m1 * m1, gamma / gm1);
		double p0s_over_ps = Math.pow(1.0 + gm1h * mSurface * mSurface, gamma / gm1);
		double pRatioSurface = p0Ratio * p01_over_p1 / p0s_over_ps;

		double t01_over_t1 = 1.0 + gm1h * m1 * m1;
		double t0s_over_ts = 1.0 + gm1h * mSurface * mSurface;
		double tRatioSurface = t01_over_t1 / t0s_over_ts;
		double rhoRatioSurface = pRatioSurface / tRatioSurface;

		return new ObliqueShockResult(betaCone, coneAngle, m1, mSurface,
				pRatioSurface, tRatioSurface, rhoRatioSurface, p0Ratio, true);
	}

	/**
	 * Compute cone surface conditions for air (gamma = 1.4).
	 */
	public static ObliqueShockResult solveCone(double m1, double coneAngle) {
		return solveCone(m1, coneAngle, GAMMA_AIR);
	}

	/**
	 * Compute the pressure coefficient on a cone surface.
	 * Cp = 2/(gamma*M1^2) * (p_surface/p1 - 1)
	 *
	 * @param m1        freestream Mach number
	 * @param coneAngle cone half-angle in radians
	 * @param gamma     ratio of specific heats
	 * @return pressure coefficient Cp
	 */
	public static double conePressureCoefficient(double m1, double coneAngle, double gamma) {
		ObliqueShockResult result = solveCone(m1, coneAngle, gamma);
		return 2.0 / (gamma * m1 * m1) * (result.pressureRatio - 1.0);
	}

	/**
	 * Cone pressure coefficient for air (gamma = 1.4).
	 */
	public static double conePressureCoefficient(double m1, double coneAngle) {
		return conePressureCoefficient(m1, coneAngle, GAMMA_AIR);
	}

	// ---- Taylor-Maccoll ODE integration ----

	/**
	 * Residual function for cone shock angle bisection.
	 * Integrates Taylor-Maccoll from shock angle to cone angle;
	 * residual = radial velocity at cone surface (should be zero).
	 */
	private static double coneShockResidual(double m1, double beta, double coneAngle, double gamma) {
		try {
			double[] state = taylorMaccollIntegrate(m1, beta, coneAngle, gamma);
			return state[1]; // vr at cone surface — should be zero for correct beta
		} catch (Exception e) {
			return Double.NaN;
		}
	}

	/**
	 * Integrate the Taylor-Maccoll ODE from the shock to the cone surface.
	 * <p>
	 * The Taylor-Maccoll equation in non-dimensional form (V normalized by Vmax):
	 * <pre>
	 *   dVr/dθ = Vθ
	 *   dVθ/dθ = [ Vr * Vθ^2 - (γ-1)/2 * (1 - Vr^2 - Vθ^2) * (2*Vr + Vθ*cot(θ)) ]
	 *            / [ (γ-1)/2 * (1 - Vr^2 - Vθ^2) - Vθ^2 ]
	 * </pre>
	 * <p>
	 * Integration proceeds from θ = beta (shock) toward θ = coneAngle (surface).
	 * At the cone surface, Vθ (the radial component perpendicular to the ray
	 * from the apex) should be zero.
	 *
	 * @return {V_total, Vr_at_surface} where Vr is the component to check for zero
	 */
	private static double[] taylorMaccollIntegrate(double m1, double beta, double coneAngle,
			double gamma) {
		double gm1h = (gamma - 1.0) / 2.0; // (gamma-1)/2

		// Post-shock conditions at the shock surface
		double mn1 = m1 * Math.sin(beta);
		if (mn1 < 1.0) mn1 = 1.0;

		double mn2 = NormalShockRelations.downstreamMach(mn1, gamma);
		// Deflection angle at the shock for the oblique shock
		double thetaShock = thetaFromBeta(m1, beta, gamma);

		// Post-shock Mach number
		double m2 = mn2 / Math.sin(beta - thetaShock);

		// Non-dimensional velocity V/Vmax
		double vOverVmax = machToV(m2, gamma);

		// Decompose post-shock velocity into conical coordinates at angle theta=beta.
		// Flow direction is at angle thetaShock from axis; ray is at angle beta.
		// Angle between flow and ray = beta - thetaShock.
		// Vtheta negative: flow directed toward axis (decreasing theta).
		double vr = vOverVmax * Math.cos(beta - thetaShock);
		double vtheta = -vOverVmax * Math.sin(beta - thetaShock);

		// Adaptive RK4 integration from theta=beta down to theta=coneAngle.
		// Uses step doubling with Richardson extrapolation for error control.
		double theta = beta;
		double h = (coneAngle - beta) / 200.0;
		int maxSteps = 50000;

		for (int step = 0; step < maxSteps; step++) {
			double remaining = coneAngle - theta;
			if (Math.abs(remaining) < 1e-14) break;
			if (Math.abs(h) > Math.abs(remaining)) h = remaining;

			double[] yFull = rk4Step(theta, vr, vtheta, h, gm1h);
			double hh = h * 0.5;
			double[] yH1 = rk4Step(theta, vr, vtheta, hh, gm1h);
			double[] yH2 = rk4Step(theta + hh, yH1[0], yH1[1], hh, gm1h);

			double errVr = Math.abs(yH2[0] - yFull[0]) / 15.0;
			double errVt = Math.abs(yH2[1] - yFull[1]) / 15.0;
			double scale = Math.max(1e-10, Math.sqrt(vr * vr + vtheta * vtheta));
			double err = Math.max(errVr, errVt) / scale;

			if (Double.isNaN(err) || Double.isInfinite(err)) {
				h *= 0.1;
				if (Math.abs(h) < 1e-15) break;
				continue;
			}

			double factor = 0.9 * Math.pow(Math.max(TOL, 1e-30) / Math.max(err, 1e-30), 0.2);
			factor = Math.max(0.1, Math.min(factor, 5.0));

			if (err <= TOL || Math.abs(h) < 1e-15) {
				vr = yH2[0] + (yH2[0] - yFull[0]) / 15.0;
				vtheta = yH2[1] + (yH2[1] - yFull[1]) / 15.0;
				theta += h;
			}

			h *= factor;
		}

		double vTotal = Math.sqrt(vr * vr + vtheta * vtheta);
		return new double[] { vTotal, vtheta };
	}

	/**
	 * Single RK4 step for the Taylor-Maccoll ODE system.
	 */
	private static double[] rk4Step(double theta, double vr, double vtheta, double h,
			double gm1h) {
		double[] k1 = taylorMaccollRHS(theta, vr, vtheta, gm1h);
		double[] k2 = taylorMaccollRHS(theta + 0.5 * h,
				vr + 0.5 * h * k1[0], vtheta + 0.5 * h * k1[1], gm1h);
		double[] k3 = taylorMaccollRHS(theta + 0.5 * h,
				vr + 0.5 * h * k2[0], vtheta + 0.5 * h * k2[1], gm1h);
		double[] k4 = taylorMaccollRHS(theta + h,
				vr + h * k3[0], vtheta + h * k3[1], gm1h);
		return new double[] {
				vr + h / 6.0 * (k1[0] + 2.0 * k2[0] + 2.0 * k3[0] + k4[0]),
				vtheta + h / 6.0 * (k1[1] + 2.0 * k2[1] + 2.0 * k3[1] + k4[1])
		};
	}

	/**
	 * Right-hand side of the Taylor-Maccoll ODE system.
	 * <p>
	 * State variables: Vr (radial velocity / Vmax), Vθ (tangential / Vmax)
	 * The ODE is:
	 *   dVr/dθ = Vθ
	 *   dVθ/dθ = [Vr*Vθ² - gm1h*(1 - Vr² - Vθ²)*(2*Vr + Vθ*cot(θ))] / [gm1h*(1 - Vr² - Vθ²) - Vθ²]
	 */
	private static double[] taylorMaccollRHS(double theta, double vr, double vtheta, double gm1h) {
		double vsq = vr * vr + vtheta * vtheta;
		double residualTerm = 1.0 - vsq; // (Vmax² - V²)/Vmax²
		double cotTheta = Math.cos(theta) / Math.sin(theta);

		double dvrDtheta = vtheta;

		double numerator = vr * vtheta * vtheta - gm1h * residualTerm * (2.0 * vr + vtheta * cotTheta);
		double denominator = gm1h * residualTerm - vtheta * vtheta;

		if (Math.abs(denominator) < 1e-15) {
			// Near-singular — flow approaching sonic in the theta direction.
			// Return a large value with the correct sign to signal the integration to stop,
			// rather than returning 0.0 which incorrectly implies smooth behavior.
			logger.debug("Taylor-Maccoll denominator near-singular at theta={}, vr={}, vtheta={}",
					theta, vr, vtheta);
			return new double[] { dvrDtheta, Math.copySign(1e10, -vtheta) };
		}

		double dvthetaDtheta = numerator / denominator;

		return new double[] { dvrDtheta, dvthetaDtheta };
	}

	// ---- Velocity-Mach conversion for Taylor-Maccoll ----

	/**
	 * Convert Mach number to non-dimensional velocity V/Vmax.
	 * Vmax = sqrt(2*cp*T0) is the maximum possible velocity (complete expansion to T=0).
	 * V/Vmax = sqrt(M² / (M² + 2/(gamma-1)))
	 */
	private static double machToV(double mach, double gamma) {
		double gm1 = gamma - 1.0;
		double m2 = mach * mach;
		return Math.sqrt(m2 / (m2 + 2.0 / gm1));
	}

	/**
	 * Convert non-dimensional velocity V/Vmax back to Mach number.
	 */
	private static double vToMach(double vOverVmax, double gamma) {
		double gm1 = gamma - 1.0;
		double v2 = vOverVmax * vOverVmax;
		if (v2 >= 1.0) {
			// V/Vmax = 1.0 corresponds to complete expansion to zero temperature,
			// i.e., infinite Mach. Return a very large finite value to avoid
			// Infinity/NaN propagation in downstream calculations.
			return Double.MAX_VALUE / 2;
		}
		return Math.sqrt(2.0 / gm1 * v2 / (1.0 - v2));
	}
}
