package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Validates the Chapman (1950, NACA TN 2137) laminar base drag formula
 * against NACA TN 3393 (Reller &amp; Hamaker 1955) figure-digitized data.
 * <p>
 * TN 3393 model: 10-caliber tangent ogive + cylindrical afterbody, l/d = 5.
 * Laminar data: natural (no artificial trip), condensation-corrected.
 * Turbulent data: carborundum particles at nose forced early transition.
 * <p>
 * Physics context: at supersonic Mach numbers a laminar boundary layer
 * produces LESS base drag than a turbulent one. The laminar shear layer
 * has lower momentum than a turbulent shear layer, so it entrains less
 * ambient fluid into the base recirculation zone, allowing the base to
 * expand to a lower pressure (less suction, i.e., lower Cpb). The
 * Devan-Ashwood turbulent model therefore OVERPREDICTS laminar base drag
 * above M ≈ 3 (MAPE 44 %; up to 86 % at M 4.48). Chapman's Re^(−1/2)
 * Mach^(−2) formula reduces this to MAPE ≈ 4 % on the same data.
 */
public class ChapmanLaminarBaseDragTest {

	// ─────────────────────────────────────────────────────────────────────────
	// TN 3393 laminar formula: Cpb_lam = C_LAM / (M^2 * sqrt(Re_L))
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Unit accuracy test: {@link ChapmanKorstBaseDrag#laminarBaseDragCoefficient}
	 * vs NACA TN 3393 condensation-corrected laminar data.
	 * <p>
	 * Acceptance criterion: each point within 10 % of the digitized value (vs
	 * 22–86 % for Devan-Ashwood on the same data). MAPE target ≤ 10 %.
	 * <p>
	 * Data provenance (NACA_TN_3393_digitized_points.csv):
	 * <ul>
	 *   <li>M = 2.73, Re = 4.0 × 10⁶: Cpb = 0.1150 (Fig 16 / 9(b), 80% confidence)</li>
	 *   <li>M = 3.49, Re = 5.0 × 10⁶: Cpb = 0.0680 (Fig 16 / 9(b), 80% confidence)</li>
	 *   <li>M = 4.03, Re = 6.0 × 10⁶: Cpb = 0.0493 (Fig 16 / 9(b), 80% confidence)</li>
	 *   <li>M = 4.48, Re = 6.0 × 10⁶: Cpb = 0.0391 (Fig 16 / 9(b), 80% confidence)</li>
	 * </ul>
	 */
	@ParameterizedTest(name = "Chapman laminar at M={0}, Re={1}e6: within {3}% of TN3393 Cpb={2}")
	@CsvSource({
			// mach, Re_millions, TN3393_Cpb_laminar, max_pct_error
			"2.73, 4.0, 0.1150, 10",
			"3.49, 5.0, 0.0680, 10",
			"4.03, 6.0, 0.0493, 10",
			"4.48, 6.0, 0.0391, 10",
	})
	void testChapmanLaminarVsTN3393(double mach, double reMill, double tn3393Cpb, double maxPctError) {
		double reL = reMill * 1.0e6;
		double predicted = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL);

		double pctError = Math.abs(predicted - tn3393Cpb) / tn3393Cpb * 100.0;
		assertTrue(pctError <= maxPctError,
				String.format("Chapman laminar at M=%.2f, Re=%.0fe6: predicted=%.4f, TN3393=%.4f, error=%.1f%% (limit %.0f%%)",
						mach, reMill, predicted, tn3393Cpb, pctError, maxPctError));
	}

	/**
	 * MAPE across all four TN 3393 laminar points must be below 10 %.
	 * This is the primary benchmark gate for the laminar closure.
	 * Devan-Ashwood MAPE on the same data is 44 %.
	 */
	@Test
	void testChapmanLaminarMAPEBelowTen() {
		double[][] data = {
				{2.73, 4.0e6, 0.1150},
				{3.49, 5.0e6, 0.0680},
				{4.03, 6.0e6, 0.0493},
				{4.48, 6.0e6, 0.0391},
		};

		double sumPct = 0;
		for (double[] row : data) {
			double mach = row[0], reL = row[1], observed = row[2];
			double predicted = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL);
			sumPct += Math.abs(predicted - observed) / observed * 100.0;
		}
		double mape = sumPct / data.length;

		assertTrue(mape < 10.0,
				String.format("Chapman laminar MAPE = %.1f%% vs TN 3393 laminar data (limit 10%%)", mape));
	}

	/**
	 * Chapman laminar MAPE is substantially better than Devan-Ashwood on the
	 * same laminar dataset. Devan-Ashwood achieves ~44 % MAPE on TN 3393 laminar;
	 * Chapman must beat it by at least a factor of 3 (MAPE < 15 %).
	 */
	@Test
	void testChapmanBetterThanDevanAshwoodOnLaminarData() {
		double[][] data = {
				{2.73, 4.0e6, 0.1150},
				{3.49, 5.0e6, 0.0680},
				{4.03, 6.0e6, 0.0493},
				{4.48, 6.0e6, 0.0391},
		};

		double chapmanMape = 0, daMape = 0;
		for (double[] row : data) {
			double mach = row[0], reL = row[1], observed = row[2];
			double chapman = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL);
			double da = BarrowmanDragCalculator.calculateBaseCD(mach);
			chapmanMape += Math.abs(chapman - observed) / observed;
			daMape += Math.abs(da - observed) / observed;
		}
		chapmanMape = chapmanMape / data.length * 100.0;
		daMape = daMape / data.length * 100.0;

		assertTrue(chapmanMape < daMape / 3.0,
				String.format("Chapman MAPE (%.1f%%) should be < Devan-Ashwood MAPE/3 (%.1f%%/3 = %.1f%%)",
						chapmanMape, daMape, daMape / 3.0));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Physics: laminar Cpb < turbulent Cpb at high supersonic Mach
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * At M ≥ 3.5, the Chapman laminar formula should predict lower base drag
	 * than the Devan-Ashwood turbulent correlation, consistent with TN 3393
	 * (laminar rows are below turbulent rows at each Mach point).
	 */
	@ParameterizedTest(name = "Chapman laminar < Devan-Ashwood at M={0}, Re=5e6")
	@CsvSource({"3.5", "4.0", "4.5", "5.0"})
	void testLaminarLowerThanTurbulentAtHighMach(double mach) {
		double reL = 5.0e6;
		double laminar = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL);
		double turbulent = BarrowmanDragCalculator.calculateBaseCD(mach);

		assertTrue(laminar < turbulent,
				String.format("At M=%.1f laminar (%.4f) should be < turbulent D-A (%.4f)",
						mach, laminar, turbulent));
	}

	/**
	 * The laminar formula must predict a POSITIVE base drag coefficient
	 * (base pressure below ambient) and must not exceed the vacuum limit
	 * (2/(γM²) ≈ 1.429/M²).
	 */
	@ParameterizedTest(name = "laminarBaseDragCoefficient is physical at M={0}, Re=5e6")
	@CsvSource({"2.0", "3.0", "4.0", "5.0", "8.0", "10.0"})
	void testLaminarCoeffIsPhysical(double mach) {
		double reL = 5.0e6;
		double cd = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL);

		assertTrue(cd >= 0,
				String.format("Laminar Cpb must be non-negative at M=%.1f, got %.6f", mach, cd));
		double vacuumLimit = 2.0 / (1.4 * mach * mach);
		assertTrue(cd <= vacuumLimit,
				String.format("Laminar Cpb (%.4f) exceeds vacuum limit (%.4f) at M=%.1f",
						cd, vacuumLimit, mach));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Re-scaling: laminar Cpb decreases with Re
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Higher Re → thinner laminar BL → less mass filling the wake →
	 * lower base pressure → lower Cpb (less base drag). The Re^(−1/2)
	 * scaling means doubling Re reduces Cpb by √2 ≈ 29 %.
	 */
	@Test
	void testLaminarCpbDecreasesWithRe() {
		double mach = 3.5;
		double cdLow = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, 2.0e6);
		double cdMid = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, 5.0e6);
		double cdHigh = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, 1.0e7);

		assertTrue(cdHigh < cdMid && cdMid < cdLow,
				String.format("Laminar Cpb should decrease with Re: Re2M=%.4f, Re5M=%.4f, Re10M=%.4f",
						cdLow, cdMid, cdHigh));

		// Doubling Re from 2e6 to 8e6 → Cpb should drop by factor ~√(8/2) = 2
		double cdRe8M = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, 8.0e6);
		double actualRatio = cdLow / cdRe8M;
		assertEquals(2.0, actualRatio, 0.05,
				String.format("Re^(1/2) scaling: Cpb(2e6)/Cpb(8e6) should be ~2.0, got %.3f", actualRatio));
	}

	/**
	 * Re^(−1/2) exponent is verified directly: increasing Re by 4× should
	 * reduce Cpb by exactly 2× (since √4 = 2).
	 */
	@Test
	void testLaminarReSqrtScaling() {
		double mach = 4.0;
		double reA = 4.0e6;
		double reB = 16.0e6;  // 4× larger
		double cdA = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reA);
		double cdB = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reB);

		double ratio = cdA / cdB;
		assertEquals(2.0, ratio, 0.02,
				String.format("Re^(1/2) scaling: Cpb(Re=4e6)/Cpb(Re=16e6) = %.3f, expected 2.00", ratio));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// blendedLaminarBaseDrag: continuity and boundary conditions
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Below {@code LAM_BLEND_LOW} the blended result equals Devan-Ashwood,
	 * since the laminar correction has not yet been activated.
	 */
	@Test
	void testBlendedBelowBlendLow() {
		double mach = 1.0;
		double reL = 5.0e6;
		double blended = ChapmanKorstBaseDrag.blendedLaminarBaseDrag(mach, reL);
		double da = BarrowmanDragCalculator.calculateBaseCD(mach);
		assertEquals(da, blended, 1e-9,
				"Below LAM_BLEND_LOW blended result must equal Devan-Ashwood");
	}

	/**
	 * Above {@code LAM_BLEND_HIGH} the blended result equals the full Chapman
	 * laminar formula.
	 */
	@Test
	void testBlendedAboveBlendHigh() {
		double mach = 3.5;
		double reL = 5.0e6;
		double blended = ChapmanKorstBaseDrag.blendedLaminarBaseDrag(mach, reL);
		double chapman = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL);
		assertEquals(chapman, blended, 1e-9,
				"Above LAM_BLEND_HIGH blended result must equal Chapman laminar");
	}

	/**
	 * The blended transition must be C0-continuous: no sudden jumps between
	 * adjacent Mach steps in the blend region M 1.3-2.5.
	 */
	@Test
	void testBlendedC0ContinuityInTransition() {
		double reL = 5.0e6;
		double step = 0.02;
		double prev = ChapmanKorstBaseDrag.blendedLaminarBaseDrag(
				ChapmanKorstBaseDrag.LAM_BLEND_LOW, reL);

		for (double m = ChapmanKorstBaseDrag.LAM_BLEND_LOW + step;
			 m <= ChapmanKorstBaseDrag.LAM_BLEND_HIGH + 0.1;
			 m += step) {
			double cd = ChapmanKorstBaseDrag.blendedLaminarBaseDrag(m, reL);
			double jump = Math.abs(cd - prev);
			assertTrue(jump < 0.015,
					String.format("Blended laminar drag jump %.5f at M=%.2f exceeds tolerance 0.015", jump, m));
			assertTrue(cd > 0 && Double.isFinite(cd),
					String.format("Non-physical blended drag %.4f at M=%.2f", cd, m));
			prev = cd;
		}
	}

	/**
	 * Value continuity at the upper blend boundary (M = 2.5): the blended
	 * result just below and just above 2.5 must agree to within 1e-3.
	 */
	@Test
	void testBlendedValueContinuityAtBlendHigh() {
		double reL = 5.0e6;
		double h = 1e-5;
		double mHigh = ChapmanKorstBaseDrag.LAM_BLEND_HIGH;

		double below = ChapmanKorstBaseDrag.blendedLaminarBaseDrag(mHigh - h, reL);
		double above = ChapmanKorstBaseDrag.blendedLaminarBaseDrag(mHigh + h, reL);

		assertEquals(below, above, 1e-3,
				String.format("Value discontinuity at LAM_BLEND_HIGH: below=%.6f, above=%.6f", below, above));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Edge cases and numerical guards
	// ─────────────────────────────────────────────────────────────────────────

	@Test
	void testLaminarFallbackAtSubsonic() {
		double da = BarrowmanDragCalculator.calculateBaseCD(0.8);
		double lam = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(0.8, 5e6);
		assertEquals(da, lam, 1e-9, "Subsonic: Chapman laminar must return Devan-Ashwood fallback");
	}

	@Test
	void testLaminarFallbackAtDegenerateRe() {
		double da = BarrowmanDragCalculator.calculateBaseCD(3.0);
		double lam = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(3.0, 0.0);
		assertEquals(da, lam, 1e-9, "Re=0: Chapman laminar must return Devan-Ashwood fallback");
	}

	@Test
	void testLaminarFiniteAtExtremeMach() {
		for (double mach : new double[]{1.5, 5.0, 10.0, 20.0}) {
			double cd = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, 5e6);
			assertTrue(Double.isFinite(cd) && cd > 0,
					String.format("Non-physical laminar drag %.6f at M=%.1f", cd, mach));
		}
	}

	/**
	 * At very low Reynolds number the formula would otherwise exceed the
	 * vacuum limit. Verify the vacuum-limit clamp is active.
	 */
	@Test
	void testVacuumLimitClampAtLowRe() {
		double mach = 3.0;
		double reL = 1.0e4;  // very low Re
		double cd = ChapmanKorstBaseDrag.laminarBaseDragCoefficient(mach, reL);
		double vacuumLimit = 2.0 / (1.4 * mach * mach);
		assertTrue(cd <= vacuumLimit,
				String.format("At low Re=1e4, M=3: Cpb (%.4f) must be ≤ vacuum limit (%.4f)",
						cd, vacuumLimit));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Regression: Devan-Ashwood turbulent results unaffected
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * The static {@code calculateBaseCD(double)} method (Devan-Ashwood, turbulent)
	 * must be completely unchanged by the addition of the laminar path.
	 */
	@ParameterizedTest(name = "Devan-Ashwood unchanged at M={0}")
	@CsvSource({
			"1.5,  0.14667",
			"2.0,  0.11050",
			"3.0,  0.08467",
			"4.0,  0.07563",
			"5.0,  0.07144",
	})
	void testDevanAshwoodUnchanged(double mach, double expectedCd) {
		double cd = BarrowmanDragCalculator.calculateBaseCD(mach);
		assertEquals(expectedCd, cd, 0.001,
				String.format("Devan-Ashwood (turbulent) at M=%.1f must be unchanged", mach));
	}
}
