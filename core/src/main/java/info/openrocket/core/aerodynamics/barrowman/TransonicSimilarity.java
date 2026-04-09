package info.openrocket.core.aerodynamics.barrowman;

/**
 * ESDU transonic similarity rule for fin CNa.
 * Collapses fin aerodynamic data onto a universal curve using
 * K_trans = (M^2 - 1) / (t/c)^(2/3).
 */
public class TransonicSimilarity {

	// Universal function h(K_trans) - peak at K_trans ≈ 0 (M ≈ 1)
	private static final double[] K_TRANS_POINTS = { -2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0, 3.0 };
	private static final double[] H_VALUES =       {  0.70, 0.85, 0.93, 1.00, 0.97, 0.90, 0.75, 0.62 };

	/**
	 * Compute the transonic similarity parameter.
	 * @param mach Mach number
	 * @param thicknessRatio fin thickness-to-chord ratio (t/c)
	 * @param sweepAngleLE leading edge sweep angle (radians)
	 * @return K_trans
	 */
	public static double computeKTrans(double mach, double thicknessRatio, double sweepAngleLE) {
		double mEff = mach * Math.cos(sweepAngleLE);
		if (thicknessRatio < 1e-6) return (mEff > 1.0) ? 100 : -100;
		return (mEff * mEff - 1.0) / Math.pow(thicknessRatio, 2.0 / 3.0);
	}

	/**
	 * Compute the universal curve h(K_trans) using linear interpolation.
	 * @param kTrans the transonic similarity parameter
	 * @return h value in [0, 1]
	 */
	public static double universalCurve(double kTrans) {
		if (kTrans <= K_TRANS_POINTS[0]) return H_VALUES[0];
		if (kTrans >= K_TRANS_POINTS[K_TRANS_POINTS.length - 1]) return H_VALUES[H_VALUES.length - 1];
		// linear interpolation
		for (int i = 0; i < K_TRANS_POINTS.length - 1; i++) {
			if (kTrans <= K_TRANS_POINTS[i + 1]) {
				double t = (kTrans - K_TRANS_POINTS[i]) / (K_TRANS_POINTS[i + 1] - K_TRANS_POINTS[i]);
				return H_VALUES[i] + t * (H_VALUES[i + 1] - H_VALUES[i]);
			}
		}
		return H_VALUES[H_VALUES.length - 1];
	}

	/**
	 * Compute the peak CNa at M=1 for a fin of given aspect ratio and thickness.
	 * @param aspectRatio fin aspect ratio
	 * @param thicknessRatio t/c
	 * @return peak CNa per fin
	 */
	public static double computeCNaPeak(double aspectRatio, double thicknessRatio) {
		double fThickness = 1.0 + 2.5 * thicknessRatio + 8.0 * thicknessRatio * thicknessRatio;
		return 2 * Math.PI * aspectRatio / (2 + Math.sqrt(4 + aspectRatio * aspectRatio)) * fThickness;
	}

	/**
	 * Check if the transonic similarity model is active (K_trans in [-2, +3]).
	 * @param kTrans the transonic similarity parameter
	 * @return true if in the transonic similarity regime
	 */
	public static boolean isInTransonicRegime(double kTrans) {
		return kTrans >= -2.0 && kTrans <= 3.0;
	}
}
