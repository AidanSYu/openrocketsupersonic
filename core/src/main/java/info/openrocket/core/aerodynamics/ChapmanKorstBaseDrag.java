package info.openrocket.core.aerodynamics;

import info.openrocket.core.util.MathUtil;

import static info.openrocket.core.models.atmosphere.AtmosphericConditions.GAMMA;

/**
 * Chapman-Korst base drag model for supersonic flow.
 * <p>
 * Computes base pressure coefficient from boundary layer edge conditions
 * using the Chapman-Korst dead-air region theory with ESDU 77021 parametric
 * corrections for boundary layer thickness effects.
 * <p>
 * The model is applicable for M &gt; 1.3 where the base flow is fully
 * supersonic. Below M 1.3, the existing Devan-Ashwood correlation should be
 * used, with a C1-continuous blend in the overlap region M 1.2-1.4.
 * <p>
 * References:
 * <ul>
 *   <li>Chapman, D.R. (1951). "An analysis of base pressure at supersonic velocities
 *       and comparison with experiment". NACA Report 1051.</li>
 *   <li>Korst, H.H. (1956). "A theory for base pressures in transonic and supersonic
 *       flow". J. Applied Mechanics, 23, 593-600.</li>
 *   <li>ESDU 77021 (1977). "Drag of a simple body at supersonic speeds: base drag".</li>
 *   <li>Hoerner, S.F. (1965). "Fluid-Dynamic Drag", Ch. 3.</li>
 * </ul>
 */
public class ChapmanKorstBaseDrag {

	/** Lower Mach of the blend region between Devan-Ashwood and Chapman-Korst. */
	static final double BLEND_LOW = 1.2;

	/** Upper Mach of the blend region between Devan-Ashwood and Chapman-Korst. */
	static final double BLEND_HIGH = 1.4;

	/**
	 * Minimum BL thickness ratio to prevent division by zero.
	 * For very thin BL (high altitude, short body), clamp here.
	 */
	private static final double MIN_THETA_RATIO = 1e-5;

	/**
	 * Maximum BL thickness ratio. Beyond this the BL is thicker than
	 * the base radius, which is unphysical for typical rockets.
	 */
	private static final double MAX_THETA_RATIO = 0.5;

	/**
	 * Compute the turbulent boundary layer momentum thickness at axial position x.
	 * <p>
	 * Uses the flat-plate turbulent boundary layer correlation:
	 * theta/x = 0.036 / Re_x^0.2
	 * <p>
	 * This is an estimate; the actual BL on a rocket body will differ due to
	 * pressure gradients, but this captures the correct scaling with Re.
	 *
	 * @param x     axial distance from the leading edge (body length to base), meters
	 * @param Re_x  Reynolds number based on distance x
	 * @return momentum thickness theta in meters
	 */
	public static double momentumThickness(double x, double Re_x) {
		if (x <= 0 || Re_x <= 0) {
			return 0;
		}
		return 0.036 * x / Math.pow(Re_x, 0.2);
	}

	/**
	 * Compute the Chapman-Korst base pressure coefficient.
	 * <p>
	 * Uses the ESDU 77021 parametric approach. The baseline (thin BL) Cd_base
	 * is computed from the Chapman-Korst inviscid theory with a calibrated
	 * correction, then adjusted for BL thickness effects.
	 * <p>
	 * The key physics: the supersonic shear layer reattaches on the wake axis
	 * at a "recompression point". The balance between expansion at the base
	 * corner and recompression through the shear layer determines the base
	 * pressure. Thicker BL entrains more mass, raising base pressure.
	 * <p>
	 * The baseline Cd_base for thin BL (turbulent) follows the trend:
	 * Cd_base ~ A + B/M^2 (same functional form as Devan-Ashwood, but with
	 * coefficients derived from the Chapman-Korst recompression theory).
	 *
	 * @param M_e         boundary layer edge Mach number at the base
	 * @param thetaRatio  boundary layer momentum thickness / base radius
	 * @return base pressure coefficient Cp_base (negative, as base pressure is below ambient)
	 */
	public static double basePressureCoefficient(double M_e, double thetaRatio) {
		if (M_e <= 1.0) {
			// Subsonic edge Mach: model not applicable, return Devan-Ashwood equivalent
			return -devanAshwoodCpBase(M_e);
		}

		// Compute Cd_base first, then convert to Cp_base
		double CdBase = baseDragCoefficientInternal(M_e, thetaRatio);

		// Cp_base = -Cd_base (negative pressure coefficient = positive drag)
		return -CdBase;
	}

	/**
	 * Internal computation of Chapman-Korst base drag coefficient.
	 * <p>
	 * The baseline thin-BL result uses a parametric fit calibrated to match
	 * Chapman-Korst theory results tabulated in ESDU 77021:
	 * <ul>
	 *   <li>M 1.5: Cd_base ~ 0.145</li>
	 *   <li>M 2.0: Cd_base ~ 0.110</li>
	 *   <li>M 3.0: Cd_base ~ 0.085</li>
	 *   <li>M 5.0: Cd_base ~ 0.070</li>
	 * </ul>
	 * These match the Devan-Ashwood values closely for thin turbulent BL,
	 * which is the expected behavior since both models describe the same physics.
	 * <p>
	 * The BL thickness correction reduces base drag for thicker BL (more mass
	 * entrainment into the recirculation zone).
	 */
	private static double baseDragCoefficientInternal(double M_e, double thetaRatio) {
		thetaRatio = MathUtil.clamp(thetaRatio, MIN_THETA_RATIO, MAX_THETA_RATIO);

		// Baseline (thin BL) Chapman-Korst base drag coefficient.
		// Functional form: Cd_base = A + B/M^2 + C/M^4
		// Calibrated to ESDU 77021 Table 1 (turbulent, cylindrical afterbody).
		double M2 = M_e * M_e;
		double baselineCd = 0.060 + 0.190 / M2 + 0.005 / (M2 * M2);

		// BL thickness correction: thicker BL -> lower base drag.
		// From ESDU 77021 Fig. 4: the correction is approximately
		// f(theta/r) = 1 - k * sqrt(theta/r) for small theta/r,
		// where k depends weakly on Mach.
		// For theta/r ~ 0.01 (typical rocket): ~5-10% reduction.
		// For theta/r ~ 0.1 (very thick BL): ~20-30% reduction.
		double k = 0.8 + 0.2 / M_e;  // ~1.0 at M=1.3, ~0.84 at M=5
		double blReduction = 1.0 - k * Math.sqrt(thetaRatio);
		blReduction = MathUtil.clamp(blReduction, 0.3, 1.0);

		return baselineCd * blReduction;
	}

	/**
	 * Compute the base drag coefficient (positive) using Chapman-Korst theory.
	 * <p>
	 * Cd_base = -Cp_base (convert negative pressure coefficient to positive drag).
	 * The result is referenced to the base area.
	 *
	 * @param M_e        boundary layer edge Mach at the base
	 * @param thetaRatio BL momentum thickness / base radius
	 * @return base drag coefficient (positive, referenced to base area)
	 */
	public static double baseDragCoefficient(double M_e, double thetaRatio) {
		return -basePressureCoefficient(M_e, thetaRatio);
	}

	/**
	 * Compute the blended base drag coefficient that transitions from
	 * Devan-Ashwood (low Mach) to Chapman-Korst (high Mach) through
	 * the blend region M 1.2-1.4.
	 * <p>
	 * Below M 1.2: pure Devan-Ashwood.
	 * Above M 1.4: pure Chapman-Korst.
	 * M 1.2-1.4: smoothstep blend.
	 *
	 * @param mach       freestream Mach number
	 * @param M_e        boundary layer edge Mach at the base (from ShockGeometry or freestream)
	 * @param thetaRatio BL momentum thickness / base radius
	 * @return blended base drag coefficient (referenced to base area)
	 */
	public static double blendedBaseDrag(double mach, double M_e, double thetaRatio) {
		if (mach <= BLEND_LOW) {
			return BarrowmanDragCalculator.calculateBaseCD(mach);
		}

		double ckDrag = baseDragCoefficient(M_e, thetaRatio);

		if (mach >= BLEND_HIGH) {
			return ckDrag;
		}

		// Smoothstep blend in [BLEND_LOW, BLEND_HIGH]
		double t = (mach - BLEND_LOW) / (BLEND_HIGH - BLEND_LOW);
		double s = smoothstep(t);

		double daDrag = BarrowmanDragCalculator.calculateBaseCD(mach);
		return daDrag * (1.0 - s) + ckDrag * s;
	}

	/**
	 * Equivalent Devan-Ashwood Cp_base (as a positive magnitude) for comparison.
	 */
	private static double devanAshwoodCpBase(double mach) {
		return BarrowmanDragCalculator.calculateBaseCD(mach);
	}

	/**
	 * Hermite smoothstep: 3t^2 - 2t^3, zero derivative at endpoints.
	 */
	private static double smoothstep(double t) {
		t = MathUtil.clamp(t, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
