package info.openrocket.core.aerodynamics;

/**
 * Plume-induced flow separation model.
 *
 * At high altitude, underexpanded rocket exhaust plumes expand beyond the base diameter,
 * causing boundary layer separation on the aft body. This reduces friction drag in the
 * separated region and decreases fin effectiveness for fins immersed in the separated flow.
 *
 * The model is active only during motor burn when the exit-to-ambient pressure ratio
 * exceeds a threshold (typically p_exit/p_ambient > 3, corresponding to roughly 10+ km altitude).
 *
 * References:
 * - Korzun & Braun, "Performance Characterization of Supersonic Retropropulsion" (2010)
 * - NASA SP-8039, "Solid Rocket Motor Nozzles" (1971)
 */
public class PlumeModel {

	/** Minimum pressure ratio (p_exit / p_ambient) to activate plume effects */
	public static final double MIN_PRESSURE_RATIO = 3.0;

	/** Separation length coefficient: L_sep = K_SEP * D_base * sqrt(blockage - 1) */
	public static final double K_SEP = 3.0;

	/** Default nozzle expansion area ratio (exit/throat) for solid rocket motors */
	public static final double DEFAULT_EXPANSION_RATIO = 8.0;

	/** Default nozzle exit pressure at design condition as fraction of chamber pressure */
	public static final double DEFAULT_EXIT_PRESSURE_RATIO = 0.1; // p_exit/p_chamber ~ 0.1 for typical SRM

	/** Default ratio of nozzle exit diameter to motor casing diameter */
	public static final double DEFAULT_EXIT_DIAMETER_RATIO = 0.7;

	/**
	 * Estimate nozzle exit pressure based on chamber conditions.
	 * For a typical solid rocket motor, the exit pressure is roughly 1 atm at sea level
	 * (designed for optimal expansion), so at altitude the plume becomes underexpanded.
	 *
	 * Without motor-specific nozzle data, we assume the nozzle is designed for
	 * sea-level operation: p_exit approximately equals 1 atm = 101325 Pa.
	 *
	 * @return estimated exit pressure in Pa
	 */
	public static double estimateExitPressure() {
		// Standard sea-level pressure — typical design exit pressure for ground-launched motors
		return 101325.0;
	}

	/**
	 * Estimate the nozzle exit diameter from the motor casing diameter.
	 *
	 * @param motorDiameter motor casing outer diameter (m)
	 * @return estimated nozzle exit diameter (m)
	 */
	public static double estimateExitDiameter(double motorDiameter) {
		return motorDiameter * DEFAULT_EXIT_DIAMETER_RATIO;
	}

	/**
	 * Compute the effective plume diameter at ambient conditions.
	 * An underexpanded plume expands beyond the nozzle exit to match ambient pressure.
	 *
	 * D_plume = D_exit * sqrt(p_exit / p_ambient) * 0.8
	 *
	 * The 0.8 factor accounts for the plume not being a uniform cylinder
	 * (the effective aerodynamic blockage diameter is less than the full expansion diameter).
	 *
	 * @param exitDiameter   nozzle exit diameter (m)
	 * @param exitPressure   nozzle exit pressure (Pa)
	 * @param ambientPressure ambient static pressure (Pa)
	 * @return effective plume diameter (m)
	 */
	public static double computePlumeDiameter(double exitDiameter, double exitPressure,
			double ambientPressure) {
		if (exitDiameter <= 0 || exitPressure <= 0 || ambientPressure <= 0) {
			return 0;
		}

		double pressureRatio = exitPressure / ambientPressure;
		if (pressureRatio <= 1.0) {
			// Overexpanded or perfectly expanded: no plume enlargement
			return exitDiameter;
		}

		return exitDiameter * Math.sqrt(pressureRatio) * 0.8;
	}

	/**
	 * Compute the blockage ratio (plume area / base area).
	 *
	 * @param plumeDiameter effective plume diameter (m)
	 * @param baseDiameter  rocket base diameter (m)
	 * @return blockage ratio (>= 1 if plume exceeds base)
	 */
	public static double computeBlockageRatio(double plumeDiameter, double baseDiameter) {
		if (baseDiameter <= 0 || plumeDiameter <= 0) {
			return 0;
		}
		double ratio = (plumeDiameter / baseDiameter);
		return ratio * ratio;
	}

	/**
	 * Compute the axial length of separated flow behind the base.
	 *
	 * L_sep = K_SEP * D_base * sqrt(blockage - 1)
	 *
	 * @param baseDiameter  rocket base diameter (m)
	 * @param blockageRatio plume area / base area
	 * @return separation length (m), 0 if no separation
	 */
	public static double computeSeparationLength(double baseDiameter, double blockageRatio) {
		if (blockageRatio <= 1.0 || baseDiameter <= 0) {
			return 0;
		}
		return K_SEP * baseDiameter * Math.sqrt(blockageRatio - 1.0);
	}

	/**
	 * Compute the fraction of a fin span that is immersed in the separated plume flow.
	 * Assumes the plume is centered on the rocket axis and expands radially.
	 *
	 * @param plumeDiameter effective plume diameter (m)
	 * @param baseDiameter  base (body tube) diameter (m)
	 * @param finSpan       fin semi-span from body surface (m)
	 * @return overlap fraction in [0, 1]
	 */
	public static double computeFinOverlapFraction(double plumeDiameter, double baseDiameter,
			double finSpan) {
		if (plumeDiameter <= 0 || baseDiameter <= 0 || finSpan <= 0) {
			return 0;
		}

		double bodyRadius = baseDiameter / 2.0;
		double plumeRadius = plumeDiameter / 2.0;

		if (plumeRadius <= bodyRadius) {
			// Plume does not extend beyond body — no fin overlap
			return 0;
		}

		// The plume extends from body surface outward by (plumeRadius - bodyRadius)
		double plumeExtension = plumeRadius - bodyRadius;
		double overlap = Math.min(plumeExtension / finSpan, 1.0);
		return Math.max(0, overlap);
	}

	/**
	 * Compute the fin effectiveness knockdown due to plume immersion.
	 *
	 * eta_plume = max(0.1, 1 - f_overlap)
	 *
	 * A minimum of 0.1 is retained even for fully immersed fins (the plume
	 * flow still has some dynamic pressure, albeit disturbed).
	 *
	 * @param overlapFraction fraction of fin span in plume [0, 1]
	 * @return effectiveness factor in [0.1, 1.0]
	 */
	public static double computeFinEffectiveness(double overlapFraction) {
		if (overlapFraction <= 0) {
			return 1.0;
		}
		return Math.max(0.1, 1.0 - overlapFraction);
	}

	/**
	 * Check whether plume effects are active given the current conditions.
	 *
	 * @param isMotorBurning true if a motor is currently firing
	 * @param exitPressure   nozzle exit pressure (Pa)
	 * @param ambientPressure ambient pressure (Pa)
	 * @return true if plume-induced separation should be modeled
	 */
	public static boolean isPlumeActive(boolean isMotorBurning, double exitPressure,
			double ambientPressure) {
		if (!isMotorBurning || ambientPressure <= 0) {
			return false;
		}
		return (exitPressure / ambientPressure) > MIN_PRESSURE_RATIO;
	}

	/**
	 * Compute the friction drag reduction factor for the separated region.
	 * In the separated flow region, friction is effectively zero.
	 *
	 * @param separationLength length of separated region (m)
	 * @param bodyLength       total body length from base forward (m)
	 * @return friction reduction factor in [0, 1], where 1 = no reduction
	 */
	public static double computeFrictionReduction(double separationLength, double bodyLength) {
		if (separationLength <= 0 || bodyLength <= 0) {
			return 1.0;
		}
		double fraction = Math.min(separationLength / bodyLength, 0.5); // cap at 50% reduction
		return 1.0 - fraction;
	}
}
