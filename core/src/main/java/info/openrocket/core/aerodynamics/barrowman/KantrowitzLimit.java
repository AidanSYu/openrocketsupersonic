package info.openrocket.core.aerodynamics.barrowman;

/**
 * Kantrowitz limit computation for ring fin (tube fin) flow starting.
 * <p>
 * A ring fin (annular duct) has two distinct supersonic flow regimes:
 * <ul>
 *   <li><b>Spilled (unstarted)</b>: The bow shock stands off the inlet and
 *       the flow cannot pass through the annulus supersonically. High drag.</li>
 *   <li><b>Started</b>: The shock is swallowed and the internal flow is
 *       supersonic (with possible internal shock train). Much lower drag.</li>
 * </ul>
 * <p>
 * The Kantrowitz criterion determines the minimum freestream Mach number
 * at which the inlet can start. It requires that the flow, after passing
 * through a normal shock at the inlet lip, can still accelerate through
 * the minimum area (throat) isentropically.
 * <p>
 * Reference: Kantrowitz, A. and Donaldson, C. (1945). "Preliminary Investigation
 * of Supersonic Diffusers." NACA ACR L5D20 (later NACA WR L-713).
 *
 * @see FinSetCalc
 */
public final class KantrowitzLimit {

	private static final double GAMMA = 1.4;

	private KantrowitzLimit() {
		// utility class
	}

	/**
	 * Compute the Kantrowitz starting Mach number for a ring fin.
	 * <p>
	 * The starting condition requires that the post-normal-shock mass flow
	 * can pass through the geometric throat (minimum area) of the annulus.
	 * This gives:
	 * <pre>
	 *   A_throat / A_capture = f(M)
	 * </pre>
	 * where f(M) is the Kantrowitz function. The start Mach is the value of M
	 * where f(M) = the geometric contraction ratio A_throat/A_capture.
	 * <p>
	 * For a simple ring fin with constant inner/outer radii:
	 * <ul>
	 *   <li>A_capture = pi * (r_outer^2 - r_inner^2) — the annular inlet area</li>
	 *   <li>A_throat = A_capture (no contraction for simple ring fin)</li>
	 * </ul>
	 * But the effective contraction comes from the body inside the ring fin.
	 *
	 * @param rInner inner radius of the ring fin (body radius + gap)
	 * @param rOuter outer radius of the ring fin
	 * @return starting Mach number, or Double.POSITIVE_INFINITY if the fin cannot start
	 */
	public static double computeStartMach(double rInner, double rOuter) {
		return computeStartMach(rInner, rOuter, GAMMA);
	}

	/**
	 * Compute the Kantrowitz starting Mach number for a ring fin with
	 * specified gamma.
	 *
	 * @param rInner inner radius of the ring fin
	 * @param rOuter outer radius of the ring fin
	 * @param gamma  ratio of specific heats
	 * @return starting Mach number, or Double.POSITIVE_INFINITY if the fin cannot start
	 */
	public static double computeStartMach(double rInner, double rOuter, double gamma) {
		if (rOuter <= rInner || rInner < 0 || rOuter <= 0) {
			return Double.POSITIVE_INFINITY;
		}

		// Contraction ratio: ratio of annular area to capture area
		// For a ring fin mounted on a body of radius r_body, with the fin at r_outer:
		// A_annular = pi * (r_outer^2 - r_inner^2)
		// A_capture = pi * r_outer^2 (full circle captured by outer radius)
		// The effective contraction ratio is A_annular / A_capture
		double contractionRatio = (rOuter * rOuter - rInner * rInner) / (rOuter * rOuter);

		if (contractionRatio >= 1.0) {
			// No contraction — starts at M=1 (trivially)
			return 1.0;
		}
		if (contractionRatio <= 0) {
			return Double.POSITIVE_INFINITY;
		}

		// Find M where kantrowitzFunction(M, gamma) = contractionRatio
		// The Kantrowitz function is monotonically decreasing from 1 at M=1 to 0 as M->inf
		// Use bisection on [1.0, 20.0]
		double mLow = 1.001;
		double mHigh = 20.0;

		double fLow = kantrowitzFunction(mLow, gamma);
		double fHigh = kantrowitzFunction(mHigh, gamma);

		// If the contraction ratio is above the M=1 limit, inlet can always start
		if (contractionRatio >= fLow) {
			return mLow;
		}

		// If the contraction ratio is below the high-Mach limit, inlet cannot start
		if (contractionRatio <= fHigh) {
			return Double.POSITIVE_INFINITY;
		}

		// Bisection
		for (int i = 0; i < 100; i++) {
			double mMid = 0.5 * (mLow + mHigh);
			double fMid = kantrowitzFunction(mMid, gamma);

			if (Math.abs(fMid - contractionRatio) < 1e-10) {
				return mMid;
			}

			if (fMid > contractionRatio) {
				mLow = mMid;
			} else {
				mHigh = mMid;
			}
		}

		return 0.5 * (mLow + mHigh);
	}

	/**
	 * Compute the Kantrowitz function: the ratio of the post-normal-shock
	 * mass-flow-per-unit-area to the freestream value, evaluated at the
	 * throat condition.
	 * <p>
	 * This is derived from the normal shock relations and isentropic flow:
	 * <pre>
	 *   f(M) = (1/M) * ((2 + (gamma-1)*M^2) / (gamma+1))^((gamma+1)/(2*(gamma-1)))
	 *          * ((2*gamma*M^2 - (gamma-1)) / (gamma+1))^(-1/(gamma-1))
	 * </pre>
	 * <p>
	 * This represents the minimum area ratio (A_star / A) downstream of a normal
	 * shock at Mach M, which determines whether the flow can start.
	 *
	 * @param mach  freestream Mach number (must be &gt; 1)
	 * @param gamma ratio of specific heats
	 * @return Kantrowitz function value (0 &lt; f &lt;= 1)
	 */
	public static double kantrowitzFunction(double mach, double gamma) {
		if (mach <= 1.0) {
			return 1.0;
		}

		// The Kantrowitz limit: for a normal shock at Mach M, compute the
		// minimum throat-to-capture area ratio needed to pass the post-shock
		// flow through the duct.
		//
		// The key insight: the mass flow through the capture area equals the
		// mass flow at the sonic throat. Using the isentropic relations and
		// the normal shock total pressure loss:
		//
		//   A_throat/A_capture = (1/M) * (A*/A)_isentropic * (p02/p01)
		//
		// where p02/p01 is the total pressure ratio across the normal shock
		// (always <= 1 for M > 1).

		double M2 = mach * mach;
		double gp1 = gamma + 1.0;
		double gm1 = gamma - 1.0;

		// Isentropic A*/A for the upstream Mach:
		// A*/A = M * ((2/(gp1)) * (1 + (gm1/2)*M^2))^(-(gp1)/(2*gm1))
		double isentropicAstarOverA = mach
				* Math.pow((2.0 / gp1) * (1.0 + gm1 / 2.0 * M2),
						-(gp1 / (2.0 * gm1)));

		// Total pressure ratio across normal shock (p02/p01):
		// p02/p01 = [((gp1)*M^2) / ((gm1)*M^2 + 2)]^(gamma/(gamma-1))
		//           * [gp1 / (2*gamma*M^2 - gm1)]^(1/(gamma-1))
		double p0ratio = Math.pow(gp1 * M2 / (gm1 * M2 + 2.0), gamma / gm1)
				* Math.pow(gp1 / (2.0 * gamma * M2 - gm1), 1.0 / gm1);

		// Minimum throat/capture area ratio
		return isentropicAstarOverA * p0ratio;
	}
}
