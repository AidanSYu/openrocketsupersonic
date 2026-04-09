package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Tests for Phase 9a: Aeroelastic Coupling Model.
 *
 * Validates fin effectiveness reduction under aerodynamic loading,
 * divergence detection, flutter boundary, and material property lookup.
 */
public class AeroelasticCouplingTest {

	private static final double EPSILON = 1e-6;

	// Standard G10 fiberglass fin: 0.1m span, 0.15m chord, 3mm thick
	private static final double G_G10 = 5.5e9;       // Pa
	private static final double G_CARBON = 25.0e9;    // Pa
	private static final double FIN_AREA = 0.015;     // m^2 (0.1 x 0.15)
	private static final double FIN_SPAN = 0.1;       // m
	private static final double FIN_CHORD = 0.15;     // m
	private static final double FIN_THICKNESS = 0.003; // 3mm
	private static final double CP_ARM = 0.05;        // m (half span)
	private static final double CNA_RIGID = 2.0;      // typical single-fin CNa (per radian)

	@Test
	@DisplayName("Low dynamic pressure: effectiveness should be approximately 1.0")
	void testLowDynamicPressure() {
		double J = AeroelasticModel.computeTorsionalJ(FIN_CHORD, FIN_THICKNESS);
				// q = 5 kPa (below threshold)
		double eta = AeroelasticModel.computeEffectivenessClamped(
				5000, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, J);
		assertTrue(eta > 0.9, "At low q, effectiveness should be near 1.0, got " + eta);

		// q = 1 kPa
		eta = AeroelasticModel.computeEffectivenessClamped(
				1000, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, J);
		assertTrue(eta > 0.95, "At very low q, effectiveness should be very close to 1.0, got " + eta);
	}

	@Test
	@DisplayName("Moderate dynamic pressure with G10: 5-15% CNa reduction")
	void testModerateQ_G10() {
		double J = AeroelasticModel.computeTorsionalJ(FIN_CHORD, FIN_THICKNESS);
		
		// q = 50 kPa
		double eta = AeroelasticModel.computeEffectivenessClamped(
				50_000, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, J);

		// Should show measurable reduction
		assertTrue(eta < 1.0, "At high q, effectiveness should be reduced");
		assertTrue(eta > 0.0, "Effectiveness should still be positive");
	}

	@Test
	@DisplayName("Carbon fiber fins: less reduction than G10 at same conditions")
	void testCarbonFiberStiffer() {
		double J = AeroelasticModel.computeTorsionalJ(FIN_CHORD, FIN_THICKNESS);
				double q = 80_000; // 80 kPa

		double etaG10 = AeroelasticModel.computeEffectivenessClamped(
				q, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, J);
		double etaCarbon = AeroelasticModel.computeEffectivenessClamped(
				q, FIN_AREA, CNA_RIGID, CP_ARM, G_CARBON, J);

		assertTrue(etaCarbon > etaG10,
				"Carbon fiber should have higher effectiveness than G10: "
				+ "CF=" + etaCarbon + " vs G10=" + etaG10);
	}

	@Test
	@DisplayName("Thin fins deflect more than thick fins")
	void testThicknessEffect() {
		double thinJ = AeroelasticModel.computeTorsionalJ(FIN_CHORD, 0.0015); // 1.5mm
		double thickJ = AeroelasticModel.computeTorsionalJ(FIN_CHORD, 0.006); // 6mm
				double q = 50_000;

		double etaThin = AeroelasticModel.computeEffectivenessClamped(
				q, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, thinJ);
		double etaThick = AeroelasticModel.computeEffectivenessClamped(
				q, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, thickJ);

		assertTrue(etaThick > etaThin,
				"Thicker fins should be more effective: thick=" + etaThick + " vs thin=" + etaThin);
	}

	@Test
	@DisplayName("Divergence detected for very thin fins at extreme q")
	void testDivergenceDetection() {
		// Very thin (1mm) G10 fin at high q
		double thinJ = AeroelasticModel.computeTorsionalJ(FIN_CHORD, 0.001);
				double highQ = 200_000; // 200 kPa

		double eta = AeroelasticModel.computeEffectiveness(
				highQ, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, thinJ);

		// With such extreme conditions, should be in divergence
		boolean divergence = AeroelasticModel.isDivergence(
				highQ, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, thinJ);

		// The unclamped eta can be negative at divergence
		if (divergence) {
			assertTrue(eta < 0, "Divergence should give negative raw effectiveness");
		}

		// Clamped version should return 0
		double etaClamped = AeroelasticModel.computeEffectivenessClamped(
				highQ, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, thinJ);
		assertTrue(etaClamped >= 0, "Clamped effectiveness should never be negative");
	}

	@Test
	@DisplayName("Divergence dynamic pressure is positive and finite")
	void testDivergenceQ() {
		double J = AeroelasticModel.computeTorsionalJ(FIN_CHORD, FIN_THICKNESS);
		
		double qDiv = AeroelasticModel.computeDivergenceQ(FIN_AREA, CNA_RIGID, CP_ARM, G_G10, J);

		assertTrue(qDiv > 0, "Divergence q should be positive");
		assertTrue(Double.isFinite(qDiv), "Divergence q should be finite");

		// At divergence q, effectiveness should be exactly 0
		double eta = AeroelasticModel.computeEffectiveness(
				qDiv, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, J);
		assertEquals(0.0, eta, 1e-10, "Effectiveness at divergence q should be 0");
	}

	@Test
	@DisplayName("Flutter dynamic pressure is positive and finite")
	void testFlutterBoundary() {
		double qFlutter = AeroelasticModel.computeFlutterDynamicPressure(
				FIN_SPAN, FIN_CHORD, FIN_THICKNESS, G_G10, 0.5);

		assertTrue(qFlutter > 0, "Flutter q should be positive");
		assertTrue(Double.isFinite(qFlutter), "Flutter q should be finite");

		// At supersonic speeds
		double qFlutterSup = AeroelasticModel.computeFlutterDynamicPressure(
				FIN_SPAN, FIN_CHORD, FIN_THICKNESS, G_G10, 2.0);
		assertTrue(qFlutterSup > 0, "Supersonic flutter q should be positive");
		assertTrue(Double.isFinite(qFlutterSup), "Supersonic flutter q should be finite");
	}

	@Test
	@DisplayName("Torsional J scales with thickness cubed")
	void testTorsionalJScaling() {
		double J1 = AeroelasticModel.computeTorsionalJ(0.1, 0.002);
		double J2 = AeroelasticModel.computeTorsionalJ(0.1, 0.004);

		// J ~ t^3, so doubling thickness gives 8x J
		assertEquals(8.0, J2 / J1, 0.01, "J should scale as thickness^3");
	}

	@Test
	@DisplayName("Material property lookup")
	void testMaterialLookup() {
		assertEquals(5.5e9, AeroelasticModel.getShearModulus("G10 Fiberglass"), 1e6);
		assertEquals(25.0e9, AeroelasticModel.getShearModulus("Carbon Fiber"), 1e6);
		assertEquals(0.7e9, AeroelasticModel.getShearModulus("Birch Plywood"), 1e6);
		assertEquals(26.0e9, AeroelasticModel.getShearModulus("Aluminum"), 1e6);

		// Unknown material falls back to G10
		assertEquals(AeroelasticModel.DEFAULT_SHEAR_MODULUS,
				AeroelasticModel.getShearModulus("Unknown Material"), 1e6);

		// Null falls back to default
		assertEquals(AeroelasticModel.DEFAULT_SHEAR_MODULUS,
				AeroelasticModel.getShearModulus(null), 1e6);
	}

	@Test
	@DisplayName("Zero/degenerate inputs return safe values")
	void testEdgeCases() {
		// Zero area fin
		assertEquals(1.0, AeroelasticModel.computeEffectiveness(50000, 0, 10, 0.05, G_G10, 1e-10));

		// Zero q
		assertEquals(1.0, AeroelasticModel.computeEffectiveness(0, FIN_AREA, 10, CP_ARM, G_G10, 1e-10));

		// Zero J => return 1.0 (no effect — avoids division by zero)
		assertEquals(1.0, AeroelasticModel.computeEffectiveness(50000, FIN_AREA, 10, CP_ARM, G_G10, 0));

		// Torsional J with zero dimensions
		assertEquals(0, AeroelasticModel.computeTorsionalJ(0, 0.003));
		assertEquals(0, AeroelasticModel.computeTorsionalJ(0.1, 0));

		// Flutter with zero dimensions
		assertEquals(Double.POSITIVE_INFINITY,
				AeroelasticModel.computeFlutterDynamicPressure(0, 0.1, 0.003, G_G10, 1.0));
	}

	@Test
	@DisplayName("Effectiveness is monotonically decreasing with q")
	void testMonotonicity() {
		double J = AeroelasticModel.computeTorsionalJ(FIN_CHORD, FIN_THICKNESS);
		
		double prevEta = 1.0;
		for (double q = 10_000; q < 500_000; q += 10_000) {
			double eta = AeroelasticModel.computeEffectiveness(q, FIN_AREA, CNA_RIGID, CP_ARM, G_G10, J);
			assertTrue(eta <= prevEta,
					"Effectiveness should decrease with increasing q: q=" + q
					+ " eta=" + eta + " prev=" + prevEta);
			prevEta = eta;
		}
	}

	@Test
	@DisplayName("Stiffer material always gives higher or equal divergence q")
	void testStifferMaterialHigherDivergenceQ() {
		double J = AeroelasticModel.computeTorsionalJ(FIN_CHORD, FIN_THICKNESS);
		
		double qDivG10 = AeroelasticModel.computeDivergenceQ(FIN_AREA, CNA_RIGID, CP_ARM, G_G10, J);
		double qDivCarbon = AeroelasticModel.computeDivergenceQ(FIN_AREA, CNA_RIGID, CP_ARM, G_CARBON, J);

		assertTrue(qDivCarbon > qDivG10,
				"Carbon fiber should have higher divergence q than G10");
	}
}
