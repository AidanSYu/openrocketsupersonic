package info.openrocket.core.aerodynamics.barrowman;

import info.openrocket.core.rocketcomponent.Transition;

/**
 * Dahlem-Buck (AIAA 66-505) semi-empirical wave drag shape correction factors.
 * <p>
 * Computes wave drag for non-conical nose shapes as:
 * <pre>
 *   Cd_wave = Cd_cone(M, theta_equiv) * K_shape * finenessRatioCorrection(f)
 * </pre>
 * where K_shape corrects the cone result for the actual nose shape, and the
 * fineness ratio correction adjusts from the reference fineness ratio of 3.
 * <p>
 * This provides better supersonic coverage for POWER, PARABOLIC, and HAACK shapes
 * than the TR-R-100 tables, which are limited to a few discrete fineness ratios
 * and Mach ranges.
 * <p>
 * References:
 * <ul>
 *   <li>Dahlem & Buck, "Supersonic Pressure Drag of Arbitrary Bodies of Revolution",
 *       AIAA Paper 66-505, 1966</li>
 *   <li>Hoerner, "Fluid-Dynamic Drag", Chapter 16</li>
 * </ul>
 */
public class DahlemBuckShapeFactors {

    /** Reference fineness ratio for the correction formula. */
    private static final double REFERENCE_FINENESS = 3.0;

    /** Fineness ratio power-law exponent (Dahlem-Buck). */
    private static final double FINENESS_EXPONENT = 1.6;

    /** Mach dependence coefficient: K_shape increases by this fraction per Mach unit above 1.5. */
    private static final double MACH_DEPENDENCE_SLOPE = 0.03;

    /** Maximum Mach number for the linear Mach dependence (caps above this). */
    private static final double MACH_DEPENDENCE_CAP = 5.0;

    /** Safety clamp for the shape factor to prevent unphysical values. */
    private static final double MAX_SHAPE_FACTOR = 1.5;

    /**
     * Get shape correction factor for a given nose type and Mach number.
     * K_shape = 1.0 for cone (reference shape), typically < 1.0 for lower-drag shapes
     * like ogives and Haack series.
     *
     * @param shape          the nose cone shape type
     * @param shapeParameter the shape-specific parameter (e.g., power exponent for POWER,
     *                       0 = Von Karman / 1/3 = LV-Haack for HAACK)
     * @param mach           freestream Mach number (should be > 1.0 for meaningful results)
     * @return the shape correction factor K_shape
     */
    public static double getShapeFactor(Transition.Shape shape, double shapeParameter, double mach) {
        double kBase = getBaseShapeFactor(shape, shapeParameter);

        // For the reference shape (cone), K is always exactly 1.0
        if (shape == Transition.Shape.CONICAL) {
            return 1.0;
        }

        // Mild Mach dependence: K_shape increases slightly with Mach for most shapes
        // (shock becomes more normal-like, shape differences matter less)
        double machFactor = 1.0;
        if (mach > 1.5) {
            double effectiveMach = Math.min(mach, MACH_DEPENDENCE_CAP);
            machFactor = 1.0 + MACH_DEPENDENCE_SLOPE * (effectiveMach - 1.5);
        }

        return Math.min(kBase * machFactor, MAX_SHAPE_FACTOR);
    }

    /**
     * Get the base (Mach-independent) shape factor for a given nose shape.
     *
     * @param shape          the nose cone shape type
     * @param shapeParameter the shape-specific parameter
     * @return the base shape correction factor
     */
    static double getBaseShapeFactor(Transition.Shape shape, double shapeParameter) {
        switch (shape) {
            case CONICAL:
                return 1.00;

            case OGIVE:
                // Ogives produce ~15% less wave drag than equivalent cone
                return 0.85;

            case POWER:
                // Power-law exponent: n=1.0 -> conical, n=0.75 -> 0.76, n=0.5 -> 0.70
                // Linear interpolation: k = 0.60 + 0.40 * n
                // At n=1.0: k=1.0 (conical), at n=0.5: k=0.80, at n=0: k=0.60
                return 0.60 + 0.40 * clamp01(shapeParameter);

            case PARABOLIC:
                // Parabolic series: shape param 0 = conical, 1 = full parabola
                // Full parabola has ~30% less wave drag than cone
                return 1.00 - 0.30 * clamp01(shapeParameter);

            case HAACK:
                // shapeParameter: 0 = LD-Haack (Von Karman, minimum drag), 1/3 = LV-Haack
                // Von Karman is near-optimal: k ~ 0.60
                // LV-Haack: k ~ 0.70 (slightly more drag)
                return 0.60 + 0.30 * clamp01(shapeParameter / (1.0 / 3.0));

            case ELLIPSOID:
                // Blunt shape — essentially stagnation pressure at tip
                // Dahlem-Buck is not well-suited; fallback to 1.0 (use Newtonian instead)
                return 1.00;

            default:
                return 1.00;  // Unknown shape, fall back to cone
        }
    }

    /**
     * Compute equivalent cone half-angle for a non-conical nose shape.
     * This is the half-angle of a cone with the same base radius and length,
     * which serves as the reference geometry for Dahlem-Buck.
     *
     * @param length    nose cone length
     * @param aftRadius base radius of the nose cone
     * @return equivalent cone half-angle in radians
     */
    public static double getEquivalentConeHalfAngle(double length, double aftRadius) {
        if (length <= 0) {
            return Math.PI / 2;  // Flat face
        }
        return Math.atan(aftRadius / length);
    }

    /**
     * Fineness ratio correction factor.
     * Scales wave drag from the reference fineness ratio (f=3) to the actual fineness ratio.
     * <pre>
     *   correction = (3 / f)^1.6
     * </pre>
     * Slender noses (f > 3) have lower drag; blunt noses (f < 3) have higher drag.
     *
     * @param finenessRatio the nose cone fineness ratio (length / diameter)
     * @return the fineness ratio correction factor
     */
    public static double finenessRatioCorrection(double finenessRatio) {
        if (finenessRatio <= 0) {
            return 1.0;
        }
        return Math.pow(REFERENCE_FINENESS / finenessRatio, FINENESS_EXPONENT);
    }

    /**
     * Clamp a value to the range [0, 1].
     */
    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
