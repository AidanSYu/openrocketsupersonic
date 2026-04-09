package info.openrocket.core.aerodynamics.barrowman;

/**
 * Free-interaction theory for Shock-Boundary Layer Interaction (SBLI).
 *
 * At the fin root on a body, the body boundary layer encounters the fin
 * leading-edge oblique shock. When the shock is strong enough, the BL
 * separates, reducing effective chord and adding plateau pressure drag.
 *
 * References:
 * - Chapman, Kuehn & Larson (1958) "Investigation of separated flows in
 *   supersonic and subsonic streams with emphasis on the effect of transition"
 *   NACA Report 1356
 * - Delery & Marvin (1986) "Shock-Wave Boundary Layer Interactions"
 *   AGARDograph 280
 */
public final class FreeInteractionSBLI {

	private FreeInteractionSBLI() {
		// utility class
	}

	/**
	 * Critical pressure coefficient for incipient separation.
	 * Based on free-interaction theory (Chapman-Kuehn-Larson):
	 *   Cp_critical = 3.5 * sqrt(Cf_local / sqrt(M^2 - 1))
	 *
	 * @param mach    local Mach number (must be > 1.0)
	 * @param cfLocal local skin friction coefficient
	 * @return Cp_critical, or Double.MAX_VALUE if mach <= 1.0
	 */
	public static double cpCritical(double mach, double cfLocal) {
		if (mach <= 1.0 || cfLocal <= 0.0) {
			return Double.MAX_VALUE;
		}
		double m2m1 = mach * mach - 1.0;
		return 3.5 * Math.sqrt(cfLocal / Math.sqrt(m2m1));
	}

	/**
	 * Determines whether the boundary layer separates under the given shock.
	 *
	 * @param mach     local Mach number
	 * @param cfLocal  local skin friction coefficient
	 * @param cpShock  pressure coefficient imposed by the shock
	 * @return true if cpShock exceeds Cp_critical (separation)
	 */
	public static boolean isSeparated(double mach, double cfLocal, double cpShock) {
		return cpShock > cpCritical(mach, cfLocal);
	}

	/**
	 * Separation length upstream of the shock impingement.
	 * From free-interaction scaling:
	 *   L_sep = sqrt(2) * 4.2 * theta_BL * M^2 / (sqrt(Cf) * (M^2-1)^0.25)
	 *
	 * @param mach    local Mach number (must be > 1.0)
	 * @param cfLocal local skin friction coefficient (must be > 0)
	 * @param thetaBL momentum thickness of the boundary layer at the interaction point
	 * @return separation length in meters, or 0 if subsonic/invalid
	 */
	public static double separationLength(double mach, double cfLocal, double thetaBL) {
		if (mach <= 1.0 || cfLocal <= 0.0 || thetaBL <= 0.0) {
			return 0.0;
		}
		double m2m1 = mach * mach - 1.0;
		return Math.sqrt(2.0) * 4.2 * thetaBL * mach * mach
				/ (Math.sqrt(cfLocal) * Math.pow(m2m1, 0.25));
	}

	/**
	 * Estimate momentum thickness at a given station using the 1/5-power-law
	 * turbulent BL approximation:
	 *   theta = 0.036 * x / Re_x^0.2
	 *
	 * @param xStation distance from the nose tip (m)
	 * @param reX      Reynolds number based on xStation
	 * @return estimated momentum thickness (m)
	 */
	public static double estimateMomentumThickness(double xStation, double reX) {
		if (xStation <= 0.0 || reX <= 0.0) {
			return 0.0;
		}
		return 0.036 * xStation / Math.pow(reX, 0.2);
	}

	/**
	 * Plateau pressure coefficient in the separated region.
	 * From free-interaction theory, the plateau Cp is approximately
	 * equal to the critical Cp (the pressure at which separation initiates).
	 *
	 * @param mach    local Mach number
	 * @param cfLocal local skin friction coefficient
	 * @return plateau Cp value
	 */
	public static double cpPlateau(double mach, double cfLocal) {
		return cpCritical(mach, cfLocal);
	}
}
