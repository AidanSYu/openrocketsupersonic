package info.openrocket.core.aerodynamics;

import java.util.HashMap;
import java.util.Map;

/**
 * Aeroelastic coupling model for fin effectiveness reduction under aerodynamic loading.
 *
 * At high dynamic pressure, aerodynamic loads cause fins to bend and twist,
 * reducing the effective angle of attack and thus the fin's aerodynamic effectiveness.
 * This model computes an effectiveness factor (eta_ae) that knocks down the rigid-body
 * CNa to account for structural deflection.
 *
 * References:
 * - DATCOM empirical flutter boundary
 * - Bisplinghoff, Ashley, Halfman: "Aeroelasticity" (1955)
 */
public class AeroelasticModel {

	/**
	 * Minimum dynamic pressure (Pa) below which aeroelastic effects are negligible.
	 * Set very high to effectively disable the aeroelastic model until it is
	 * validated against real flight data. The current thin-rectangle torsional
	 * stiffness formula (J = chord * t^3 / 3) dramatically underestimates real
	 * fin stiffness, causing the model to predict divergence at M~0.7 for typical
	 * high-power rocket fins (0.125" fiberglass on 6" chord). Real rockets fly
	 * stably through Mach 3+ with these fin geometries.
	 * TODO Phase 9a: Validate aeroelastic model against real flutter/divergence data
	 * before re-enabling (lower this threshold back to ~10 kPa).
	 */
	public static final double Q_THRESHOLD = 1.0e12; // Effectively disabled

	/**
	 * Material shear modulus lookup table.
	 * Maps material name patterns to shear modulus in Pascals.
	 */
	private static final Map<String, Double> SHEAR_MODULUS_TABLE = new HashMap<>();

	static {
		// G values in Pa
		SHEAR_MODULUS_TABLE.put("G10 Fiberglass", 5.5e9);
		SHEAR_MODULUS_TABLE.put("Fiberglass", 5.5e9);
		SHEAR_MODULUS_TABLE.put("Carbon Fiber", 25.0e9);
		SHEAR_MODULUS_TABLE.put("Carbon fiber", 25.0e9);
		SHEAR_MODULUS_TABLE.put("Birch Plywood", 0.7e9);
		SHEAR_MODULUS_TABLE.put("Birch plywood", 0.7e9);
		SHEAR_MODULUS_TABLE.put("Plywood", 0.7e9);
		SHEAR_MODULUS_TABLE.put("Aluminum", 26.0e9);
		SHEAR_MODULUS_TABLE.put("Aluminium", 26.0e9);
		// Defaults
		SHEAR_MODULUS_TABLE.put("Balsa", 0.2e9);
		SHEAR_MODULUS_TABLE.put("Basswood", 0.4e9);
		SHEAR_MODULUS_TABLE.put("Kraft phenolic", 3.0e9);
		SHEAR_MODULUS_TABLE.put("Phenolic", 3.0e9);
		SHEAR_MODULUS_TABLE.put("Polycarbonate", 0.9e9);
		SHEAR_MODULUS_TABLE.put("Mylar", 1.0e9);
	}

	/** Default shear modulus: G10 Fiberglass (Pa) */
	public static final double DEFAULT_SHEAR_MODULUS = 5.5e9;

	/**
	 * Look up shear modulus for a material by name.
	 * Falls back to G10 Fiberglass default if not found.
	 *
	 * @param materialName the material name
	 * @return shear modulus in Pa
	 */
	public static double getShearModulus(String materialName) {
		if (materialName == null) {
			return DEFAULT_SHEAR_MODULUS;
		}

		// Try exact match first
		Double g = SHEAR_MODULUS_TABLE.get(materialName);
		if (g != null) {
			return g;
		}

		// Try case-insensitive substring match
		String lower = materialName.toLowerCase();
		for (Map.Entry<String, Double> entry : SHEAR_MODULUS_TABLE.entrySet()) {
			if (lower.contains(entry.getKey().toLowerCase())) {
				return entry.getValue();
			}
		}

		return DEFAULT_SHEAR_MODULUS;
	}

	/**
	 * Compute the torsional second moment of area (J) for a rectangular cross-section fin.
	 * For a thin rectangle: J ≈ (1/3) * chord * thickness^3
	 *
	 * @param chord     fin chord length (m)
	 * @param thickness fin thickness (m)
	 * @return torsional second moment of area (m^4)
	 */
	public static double computeTorsionalJ(double chord, double thickness) {
		if (chord <= 0 || thickness <= 0) {
			return 0;
		}
		return chord * thickness * thickness * thickness / 3.0;
	}

	/**
	 * Compute the aeroelastic effectiveness factor for a fin.
	 *
	 * The divergence dynamic pressure for a 2D airfoil section is:
	 *   q_div = G*J / (S * e * a)
	 * where e is the distance from elastic axis to AC, a is the lift-curve slope
	 * per unit span, and S is the planform area.
	 *
	 * For a finite fin, the effectiveness ratio is:
	 *   eta_ae = 1 - q / q_div
	 *
	 * We compute q_div = G * J / (c_aero * S_fin * x_cp^2) where c_aero is a
	 * calibration factor incorporating CNa and geometry. The key scaling is
	 * that effectiveness depends on (q * S * e^2) / (G * J), which is dimensionless.
	 *
	 * When eta_ae < 0 (before clamping), it indicates aeroelastic divergence.
	 *
	 * @param q         dynamic pressure (Pa)
	 * @param finArea   fin planform area (m^2)
	 * @param cNaRigid  rigid-body CNa (per radian, dimensionless — referenced to rocket ref area)
	 * @param xCp       distance from fin root (elastic axis) to CP (m)
	 * @param G         shear modulus of fin material (Pa)
	 * @param J         torsional second moment of area (m^4)
	 * @return effectiveness factor eta_ae in [0, 1], or negative if divergence
	 */
	public static double computeEffectiveness(double q, double finArea, double cNaRigid,
			double xCp, double G, double J) {
		if (q <= 0 || finArea <= 0 || G <= 0 || J <= 0) {
			return 1.0;
		}

		// Divergence parameter: q / q_div
		// q_div = G * J / (S_fin * |CNa| * xCp^2)
		// This is dimensionless: [Pa * m^4] / [m^2 * 1 * m^2] = [Pa] / [Pa] (via q numerator)
		double denom = finArea * Math.abs(cNaRigid) * xCp * xCp;
		if (denom <= 0) {
			return 1.0;
		}
		double qDiv = G * J / denom;
		double ratio = q / qDiv;

		// Effectiveness: 1 means fully rigid, 0 means divergence, negative means post-divergence
		return 1.0 - ratio;
	}

	/**
	 * Compute the aeroelastic effectiveness, clamped to [0, 1] for use in force calculations.
	 * Returns 0 at divergence rather than going negative.
	 *
	 * @param q         dynamic pressure (Pa)
	 * @param finArea   fin planform area (m^2)
	 * @param cNaRigid  rigid-body CNa (per radian, dimensionless)
	 * @param xCp       distance from fin root to CP (m)
	 * @param G         shear modulus (Pa)
	 * @param J         torsional second moment of area (m^4)
	 * @return clamped effectiveness in [0, 1]
	 */
	public static double computeEffectivenessClamped(double q, double finArea, double cNaRigid,
			double xCp, double G, double J) {
		double eta = computeEffectiveness(q, finArea, cNaRigid, xCp, G, J);
		return Math.max(0, Math.min(1.0, eta));
	}

	/**
	 * Check whether the fin is in an aeroelastic divergence condition.
	 * Divergence occurs when the aerodynamic twist exceeds the structural restoring torque.
	 *
	 * @param q         dynamic pressure (Pa)
	 * @param finArea   fin planform area (m^2)
	 * @param cNaRigid  rigid-body CNa
	 * @param xCp       CP moment arm (m)
	 * @param G         shear modulus (Pa)
	 * @param J         torsional second moment of area (m^4)
	 * @return true if divergence (eta_ae < 0)
	 */
	public static boolean isDivergence(double q, double finArea, double cNaRigid,
			double xCp, double G, double J) {
		return computeEffectiveness(q, finArea, cNaRigid, xCp, G, J) < 0;
	}

	/**
	 * Compute the divergence dynamic pressure — the q at which eta_ae = 0.
	 * Above this q, the fin is in static divergence.
	 *
	 * q_div = G * J / (S_fin * |CNa| * xCp^2)
	 *
	 * @param finArea   fin planform area (m^2)
	 * @param cNaRigid  rigid-body CNa
	 * @param xCp       CP moment arm (m)
	 * @param G         shear modulus (Pa)
	 * @param J         torsional second moment of area (m^4)
	 * @return divergence dynamic pressure (Pa), or Double.POSITIVE_INFINITY if parameters are zero
	 */
	public static double computeDivergenceQ(double finArea, double cNaRigid,
			double xCp, double G, double J) {
		double denom = finArea * Math.abs(cNaRigid) * xCp * xCp;
		if (denom <= 0) {
			return Double.POSITIVE_INFINITY;
		}
		return G * J / denom;
	}

	/**
	 * Compute the flutter dynamic pressure using the DATCOM empirical formula.
	 *
	 * q_flutter = G * (2 * (t/c)^3) / ((AR_fin + 2) * (lambda + 1) / (AR_fin))
	 *
	 * Simplified for a rectangular/trapezoidal fin:
	 * q_flutter ≈ G * (t/c)^3 * AR / (AR + 2)
	 *
	 * With Mach correction factor: q_flutter *= sqrt(1 - M^2) for M < 1 (subsonic),
	 * or 1/sqrt(M^2 - 1) for M > 1 (supersonic).
	 *
	 * @param span      fin semi-span (m)
	 * @param chord     mean chord length (m)
	 * @param thickness fin thickness (m)
	 * @param G         shear modulus (Pa)
	 * @param mach      freestream Mach number
	 * @return flutter dynamic pressure (Pa)
	 */
	public static double computeFlutterDynamicPressure(double span, double chord,
			double thickness, double G, double mach) {
		if (span <= 0 || chord <= 0 || thickness <= 0 || G <= 0) {
			return Double.POSITIVE_INFINITY;
		}

		double tc = thickness / chord; // thickness ratio
		double ar = 2.0 * span / chord; // fin aspect ratio (semi-span based)

		// DATCOM flutter boundary (simplified)
		double qFlutter = G * 2.0 * tc * tc * tc / ((ar + 2.0) / ar);

		// Mach number correction
		if (mach < 0.9) {
			// Subsonic: flutter boundary increases with compressibility
			qFlutter *= Math.sqrt(Math.max(0.01, 1.0 - mach * mach));
		} else if (mach > 1.1) {
			// Supersonic: flutter boundary decreases
			qFlutter /= Math.sqrt(mach * mach - 1.0);
		} else {
			// Transonic: interpolate (flutter boundary is lowest here)
			double subFactor = Math.sqrt(1.0 - 0.9 * 0.9); // at M=0.9
			double supFactor = 1.0 / Math.sqrt(1.1 * 1.1 - 1.0); // at M=1.1
			double t = (mach - 0.9) / 0.2;
			double factor = subFactor * (1.0 - t) + supFactor * t;
			qFlutter *= factor;
		}

		return qFlutter;
	}
}
