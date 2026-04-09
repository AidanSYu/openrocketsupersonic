package info.openrocket.core.aerodynamics;

/**
 * AP09-style rational function blending for aerodynamic regime transitions.
 * <p>
 * The general form blends between subsonic value f_sub and supersonic value f_sup:
 * <pre>
 *   f(M) = f_sub * g(M) + f_sup * (1 - g(M))
 * </pre>
 * where g(M) is a smooth sigmoid-like weight function based on:
 * <pre>
 *   t = (M² - Mb²) / (w * Mb²)
 *   g(M) = 0.5 * (1 - t / sqrt(1 + t²))
 * </pre>
 * <p>
 * Properties:
 * <ul>
 *   <li>g(0) → 1 (purely subsonic at M=0)</li>
 *   <li>g(Mb) = 0.5 at the blend center</li>
 *   <li>g → 0 as M → ∞ (purely supersonic at high Mach)</li>
 *   <li>Smooth (C∞) everywhere, well-defined derivative</li>
 * </ul>
 * <p>
 * This captures the physics of Prandtl-Glauert divergence subsonically and
 * Ackeret scaling supersonically better than polynomial blending.
 */
public class RationalBlend {

	/**
	 * Compute the rational blending weight g(M) that transitions from 1 (subsonic)
	 * to 0 (supersonic).
	 *
	 * @param mach            Current Mach number (must be >= 0)
	 * @param mBlendCenter    Mach number where g = 0.5 (e.g., 1.0)
	 * @param transitionWidth Controls how fast the transition occurs (e.g., 0.3
	 *                        means the bulk of the transition spans roughly M ± 0.3*Mb²)
	 * @return blending weight in [0, 1]
	 */
	public static double weight(double mach, double mBlendCenter, double transitionWidth) {
		if (mach < 0) {
			mach = 0;
		}
		if (transitionWidth <= 0) {
			// Degenerate case: step function
			return mach < mBlendCenter ? 1.0 : (mach == mBlendCenter ? 0.5 : 0.0);
		}

		double mb2 = mBlendCenter * mBlendCenter;
		double t = (mach * mach - mb2) / (transitionWidth * mb2);
		double g = 0.5 * (1.0 - t / Math.sqrt(1.0 + t * t));

		// Clamp for safety (should already be in [0,1] by construction)
		return Math.max(0.0, Math.min(1.0, g));
	}

	/**
	 * Blend between subsonic and supersonic values using rational function.
	 *
	 * @param fSub            Subsonic value
	 * @param fSup            Supersonic value
	 * @param mach            Current Mach number
	 * @param mBlendCenter    Center Mach for transition
	 * @param transitionWidth Width parameter
	 * @return blended value: fSub * g + fSup * (1 - g)
	 */
	public static double blend(double fSub, double fSup, double mach, double mBlendCenter,
			double transitionWidth) {
		double g = weight(mach, mBlendCenter, transitionWidth);
		return fSub * g + fSup * (1.0 - g);
	}

	/**
	 * Derivative of the blending weight with respect to Mach (dg/dM).
	 * Needed to verify C1 continuity.
	 * <p>
	 * From g = 0.5 * (1 - t / sqrt(1 + t²)), with t = (M² - Mb²) / (w * Mb²):
	 * <pre>
	 *   dt/dM = 2M / (w * Mb²)
	 *   dg/dt = -0.5 / (1 + t²)^(3/2)
	 *   dg/dM = dg/dt * dt/dM = -M / (w * Mb² * (1 + t²)^(3/2))
	 * </pre>
	 *
	 * @param mach            Current Mach number
	 * @param mBlendCenter    Center Mach for transition
	 * @param transitionWidth Width parameter
	 * @return dg/dM (always <= 0 for M >= 0)
	 */
	public static double weightDerivative(double mach, double mBlendCenter, double transitionWidth) {
		if (mach < 0) {
			mach = 0;
		}
		if (transitionWidth <= 0) {
			return 0.0; // Step function: derivative is 0 everywhere except at the discontinuity
		}

		double mb2 = mBlendCenter * mBlendCenter;
		double wMb2 = transitionWidth * mb2;
		double t = (mach * mach - mb2) / wMb2;
		double oneTPlusT2 = 1.0 + t * t;
		double denom = oneTPlusT2 * Math.sqrt(oneTPlusT2); // (1 + t²)^(3/2)

		return -mach / (wMb2 * denom);
	}
}
