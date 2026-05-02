package info.openrocket.core.aerodynamics.barrowman;

import info.openrocket.core.util.MathUtil;

/**
 * Pitts-Nielsen-Kaattari (NACA Report 1307, 1957) Mach-dependent
 * fin-body aerodynamic interference correction factors.
 *
 * At supersonic speeds, the Mach cone from the body limits the portion
 * of the fin that "sees" the body's upwash field, reducing interference lift.
 * The correction factors F_WB (fin carryover onto body) and F_BW (body
 * carryover onto fin) multiply the subsonic Barrowman interference value
 * to produce a Mach-corrected interference factor.
 *
 * At M < 0.85 both factors return 1.0, leaving subsonic results unchanged.
 * A C1-continuous smoothstep blend is used through the transonic region
 * M 0.85-1.15 to avoid discontinuities.
 */
public class PittsNielsenKaattari {

	/** Lower edge of transonic blend region */
	private static final double M_BLEND_LOW = 0.85;
	/** Upper edge of transonic blend region */
	private static final double M_BLEND_HIGH = 1.15;
	/** Width of blend region */
	private static final double M_BLEND_WIDTH = M_BLEND_HIGH - M_BLEND_LOW;

	/** Minimum value for F_WB (fin carryover onto body) */
	private static final double F_WB_MIN = 0.5;
	/** Minimum value for F_BW (body carryover onto fin) */
	private static final double F_BW_MIN = 0.7;

	/**
	 * Compute the Mach correction factor for fin carryover onto body (F_WB).
	 * Multiplies the subsonic K_WB value to get supersonic K_WB.
	 * Returns 1.0 at M < 0.85 (no correction).
	 *
	 * @param mach        freestream or local Mach number
	 * @param bodyRadius  radius of parent body tube at fin root
	 * @param finSemispan exposed fin semispan (tip radius - root radius)
	 * @param rootChord   fin root chord length
	 * @param sweepAngleLE leading edge sweep angle (radians), unused in current
	 *                     formulation but reserved for future refinement
	 * @return correction factor in [F_WB_MIN, 1.0]
	 */
	public static double computeF_WB(double mach, double bodyRadius, double finSemispan,
									  double rootChord, double sweepAngleLE) {
		// AST 2026-05-02 ablation hook: disable PNK; revert to original Barrowman.
		if (info.openrocket.core.aerodynamics.AblationConfig.disablePNK) {
			return 1.0;
		}
		if (mach < M_BLEND_LOW) {
			return 1.0;
		}

		if (mach > M_BLEND_HIGH) {
			return computeF_WB_supersonic(mach, bodyRadius, finSemispan, rootChord);
		}

		// Transonic blend M 0.85-1.15 using smoothstep
		double t = (mach - M_BLEND_LOW) / M_BLEND_WIDTH;
		double s = t * t * (3 - 2 * t); // Hermite smoothstep, C1 continuous
		double F_sup = computeF_WB_supersonic(M_BLEND_HIGH, bodyRadius, finSemispan, rootChord);
		return 1.0 * (1 - s) + F_sup * s;
	}

	/**
	 * Compute the Mach correction for body carryover onto fin (F_BW).
	 * Smaller correction than F_WB since the body influence on fin lift
	 * is less affected by Mach cone geometry.
	 *
	 * @param mach        freestream or local Mach number
	 * @param bodyRadius  radius of parent body tube at fin root
	 * @param finSemispan exposed fin semispan
	 * @param rootChord   fin root chord length
	 * @return correction factor in [F_BW_MIN, 1.0]
	 */
	public static double computeF_BW(double mach, double bodyRadius, double finSemispan,
									  double rootChord) {
		// AST 2026-05-02 ablation hook: disable PNK; revert to original Barrowman.
		if (info.openrocket.core.aerodynamics.AblationConfig.disablePNK) {
			return 1.0;
		}
		if (mach < M_BLEND_LOW) {
			return 1.0;
		}

		if (mach > M_BLEND_HIGH) {
			return computeF_BW_supersonic(mach, bodyRadius, finSemispan, rootChord);
		}

		// Transonic blend M 0.85-1.15 using smoothstep
		double t = (mach - M_BLEND_LOW) / M_BLEND_WIDTH;
		double s = t * t * (3 - 2 * t);
		double F_sup = computeF_BW_supersonic(M_BLEND_HIGH, bodyRadius, finSemispan, rootChord);
		return 1.0 * (1 - s) + F_sup * s;
	}

	/**
	 * Pure supersonic F_WB formula (M > 1.15).
	 * Based on the reduced upwash influence when the Mach cone from the body
	 * does not encompass the full fin span.
	 */
	private static double computeF_WB_supersonic(double mach, double bodyRadius,
												  double finSemispan, double rootChord) {
		double beta_s = Math.sqrt(mach * mach - 1.0) * finSemispan / Math.max(rootChord, 1e-6);
		double r_over_s = bodyRadius / Math.max(bodyRadius + finSemispan, 1e-6);
		double correction = 1.0 - 0.3 * (1.0 - 1.0 / Math.max(beta_s, 0.1)) * Math.sqrt(r_over_s);
		return MathUtil.clamp(correction, F_WB_MIN, 1.0);
	}

	/**
	 * Pure supersonic F_BW formula (M > 1.15).
	 * Smaller magnitude correction than F_WB.
	 */
	private static double computeF_BW_supersonic(double mach, double bodyRadius,
												  double finSemispan, double rootChord) {
		double beta_s = Math.sqrt(mach * mach - 1.0) * finSemispan / Math.max(rootChord, 1e-6);
		double r_over_s = bodyRadius / Math.max(bodyRadius + finSemispan, 1e-6);
		double correction = 1.0 - 0.15 * (1.0 - 1.0 / Math.max(beta_s, 0.1)) * Math.pow(r_over_s, 0.3);
		return MathUtil.clamp(correction, F_BW_MIN, 1.0);
	}
}
