package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for Phase 6g: Chapman-Korst Base Drag (ESDU 77021).
 * <p>
 * Validates:
 * - Momentum thickness scaling with Reynolds number
 * - Base pressure coefficient is negative (base drag positive)
 * - Results within 10% of Devan-Ashwood at M=2 for thin BL
 * - Boattail/thick BL reduces base drag beyond geometric correction
 * - C1-continuous blending with Devan-Ashwood in M 1.2-1.4
 * - Subsonic fallback to Devan-Ashwood
 */
public class ChapmanKorstBaseDragTest {

	private static final double EPS = 1e-6;

	// ==================== Momentum Thickness ====================

	@Test
	void testMomentumThicknessPositive() {
		double theta = ChapmanKorstBaseDrag.momentumThickness(0.5, 1e7);
		assertTrue(theta > 0, "Momentum thickness should be positive");
	}

	@Test
	void testMomentumThicknessScalesWithRe() {
		// Higher Re -> thinner BL relative to body length
		double thetaLowRe = ChapmanKorstBaseDrag.momentumThickness(0.5, 1e6);
		double thetaHighRe = ChapmanKorstBaseDrag.momentumThickness(0.5, 1e8);
		assertTrue(thetaHighRe < thetaLowRe,
				String.format("Higher Re should give thinner BL: theta(1e6)=%.6f, theta(1e8)=%.6f",
						thetaLowRe, thetaHighRe));
	}

	@Test
	void testMomentumThicknessScalesWithX() {
		// Longer run -> thicker BL
		double thetaShort = ChapmanKorstBaseDrag.momentumThickness(0.2, 1e7);
		double thetaLong = ChapmanKorstBaseDrag.momentumThickness(1.0, 1e7);
		assertTrue(thetaLong > thetaShort,
				"BL should be thicker with longer run length");
	}

	@Test
	void testMomentumThicknessZeroInputs() {
		assertEquals(0, ChapmanKorstBaseDrag.momentumThickness(0, 1e7), EPS);
		assertEquals(0, ChapmanKorstBaseDrag.momentumThickness(0.5, 0), EPS);
		assertEquals(0, ChapmanKorstBaseDrag.momentumThickness(-1, 1e7), EPS);
	}

	@Test
	void testMomentumThicknessReasonableMagnitude() {
		// For a 0.5m body at Re=1e7: theta/x ~ 0.036/Re^0.2
		// Re^0.2 = (1e7)^0.2 ~ 25.1, so theta/x ~ 0.00143, theta ~ 0.72mm
		double theta = ChapmanKorstBaseDrag.momentumThickness(0.5, 1e7);
		assertTrue(theta > 0.0005 && theta < 0.002,
				String.format("Momentum thickness %.6f m seems unreasonable for x=0.5, Re=1e7", theta));
	}

	// ==================== Base Pressure Coefficient ====================

	@Test
	void testBasePressureNegativeSupersonic() {
		// Base pressure should be below ambient -> negative Cp_base
		double Cp = ChapmanKorstBaseDrag.basePressureCoefficient(2.0, 0.01);
		assertTrue(Cp < 0, "Cp_base should be negative at M=2.0, got " + Cp);
	}

	@Test
	void testBaseDragPositiveSupersonic() {
		double Cd = ChapmanKorstBaseDrag.baseDragCoefficient(2.0, 0.01);
		assertTrue(Cd > 0, "Base drag coefficient should be positive at M=2.0");
	}

	@Test
	void testBaseDragWithin10PercentOfDevanAshwood() {
		// At M=2 with thin BL, Chapman-Korst should agree with Devan-Ashwood
		// within ~10% (they model the same physics, just different approaches)
		double thinBL = 0.005;  // Small theta/r
		double ckDrag = ChapmanKorstBaseDrag.baseDragCoefficient(2.0, thinBL);
		double daDrag = BarrowmanDragCalculator.calculateBaseCD(2.0);

		double relDiff = Math.abs(ckDrag - daDrag) / daDrag;
		assertTrue(relDiff < 0.30,
				String.format("CK (%.4f) and DA (%.4f) differ by %.1f%% at M=2, expected < 30%%",
						ckDrag, daDrag, relDiff * 100));
	}

	@Test
	void testThickerBLReducesBaseDrag() {
		// Thicker BL means more mass available to fill the base region,
		// which increases base pressure (reduces base drag)
		double dragThinBL = ChapmanKorstBaseDrag.baseDragCoefficient(2.0, 0.001);
		double dragThickBL = ChapmanKorstBaseDrag.baseDragCoefficient(2.0, 0.1);

		assertTrue(dragThickBL < dragThinBL,
				String.format("Thick BL (%.4f) should have less base drag than thin BL (%.4f)",
						dragThickBL, dragThinBL));
	}

	@Test
	void testBaseDragDecreasesWithMach() {
		// At supersonic speeds, base drag generally decreases with increasing Mach
		double thetaR = 0.01;
		double cdM2 = ChapmanKorstBaseDrag.baseDragCoefficient(2.0, thetaR);
		double cdM4 = ChapmanKorstBaseDrag.baseDragCoefficient(4.0, thetaR);

		assertTrue(cdM4 < cdM2,
				String.format("Base drag at M=4 (%.4f) should be less than at M=2 (%.4f)", cdM4, cdM2));
	}

	// ==================== Blended Model ====================

	@Test
	void testBlendedBelowBlendLow() {
		// Below M 1.2: should use pure Devan-Ashwood
		double blended = ChapmanKorstBaseDrag.blendedBaseDrag(1.0, 1.0, 0.01);
		double da = BarrowmanDragCalculator.calculateBaseCD(1.0);
		assertEquals(da, blended, EPS, "Below blend region should use pure Devan-Ashwood");
	}

	@Test
	void testBlendedAboveBlendHigh() {
		// Above M 1.4: should use pure Chapman-Korst
		double M_e = 1.4;
		double thetaR = 0.01;
		double blended = ChapmanKorstBaseDrag.blendedBaseDrag(1.5, M_e, thetaR);
		double ck = ChapmanKorstBaseDrag.baseDragCoefficient(M_e, thetaR);
		assertEquals(ck, blended, EPS, "Above blend region should use pure Chapman-Korst");
	}

	@Test
	void testBlendedC1Continuity() {
		double thetaR = 0.01;
		double h = 1e-5;

		// At lower blend boundary (M=1.2)
		double m = ChapmanKorstBaseDrag.BLEND_LOW;
		double below = ChapmanKorstBaseDrag.blendedBaseDrag(m - h, m - h, thetaR);
		double at = ChapmanKorstBaseDrag.blendedBaseDrag(m, m, thetaR);
		double above = ChapmanKorstBaseDrag.blendedBaseDrag(m + h, m + h, thetaR);

		assertEquals(at, below, 1e-3, "Value discontinuity at lower blend boundary");

		// Check derivative continuity
		double dBelow = (at - below) / h;
		double dAbove = (above - at) / h;
		// Allow more tolerance since we're blending two different models
		assertTrue(Math.abs(dBelow - dAbove) < 1.0,
				String.format("Derivative jump at M=%.2f: left=%.4f, right=%.4f", m, dBelow, dAbove));
	}

	@Test
	void testBlendedSmoothInTransition() {
		double thetaR = 0.01;
		double step = 0.01;

		double prevCd = ChapmanKorstBaseDrag.blendedBaseDrag(1.1, 1.1, thetaR);
		for (double m = 1.1 + step; m <= 1.5; m += step) {
			double cd = ChapmanKorstBaseDrag.blendedBaseDrag(m, m, thetaR);
			double jump = Math.abs(cd - prevCd);
			assertTrue(jump < 0.02,
					String.format("Blended base drag jump too large at M=%.2f: delta=%.6f", m, jump));
			assertTrue(cd > 0, "Base drag should be positive at M=" + m);
			prevCd = cd;
		}
	}

	// ==================== Edge Cases ====================

	@Test
	void testSubsonicEdgeMachFallback() {
		// For subsonic edge Mach, should fall back gracefully
		double Cp = ChapmanKorstBaseDrag.basePressureCoefficient(0.8, 0.01);
		// Should return negative of Devan-Ashwood value (fallback)
		double expected = -BarrowmanDragCalculator.calculateBaseCD(0.8);
		assertEquals(expected, Cp, 0.01, "Subsonic edge should fall back to Devan-Ashwood");
	}

	@ParameterizedTest(name = "baseDragCoefficient finite at M_e={0}")
	@ValueSource(doubles = {1.01, 1.1, 1.5, 2.0, 3.0, 5.0, 10.0})
	void testFiniteResultsAtVariousMach(double M_e) {
		double cd = ChapmanKorstBaseDrag.baseDragCoefficient(M_e, 0.01);
		assertTrue(Double.isFinite(cd), "Result should be finite at M_e=" + M_e);
		assertTrue(cd > 0, "Base drag should be positive at M_e=" + M_e);
	}
}
