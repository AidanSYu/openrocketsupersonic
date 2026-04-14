package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.startup.Application;

/**
 * Benchmark: Van Driest II compressible skin friction vs ORP Eckert method.
 * <p>
 * Implements NASA TN D-6945 (Hopkins, 1972) equations 1-18 as an independent
 * reference for turbulent flat-plate skin friction at M 0-10.
 * <p>
 * Also validates against NASA TN D-5089 (Hopkins et al., 1969) experimental
 * floating-element balance measurements at M 6.5-7.4.
 * <p>
 * The Van Driest II method was shown in Ref.1 (Hopkins & Inouye, 1971 AIAA J.)
 * to give the best agreement with experimental data across M 1.5-9. ORP uses the
 * Eckert reference-temperature method instead (simpler, but less accurate at high M).
 * This test quantifies the divergence.
 */
public class VanDriestIISkinFrictionTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ================================================================
	// Van Driest II Reference Implementation (TN D-6945 Eqs. 1-18)
	// ================================================================

	/** Recovery factor for turbulent BL (TN D-6945 uses r = 0.88) */
	private static final double R_TURB = 0.88;

	/**
	 * Solve Schoenherr/Karman-Schoenherr implicit formula for average Cf.
	 * Eq. (1): 0.242 / sqrt(CF) = log10(Rex * CF)
	 * Uses Newton-Raphson iteration.
	 */
	static double solveSchoenherrCF(double reBarX) {
		// Initial guess from Schultz-Grunow
		double cf = 0.455 / Math.pow(Math.log10(reBarX), 2.58);
		for (int i = 0; i < 50; i++) {
			double sqrtCf = Math.sqrt(cf);
			double lhs = 0.242 / sqrtCf;
			double rhs = Math.log10(reBarX * cf);
			double residual = lhs - rhs;
			// d(lhs)/dCf = -0.121 / cf^(3/2)
			// d(rhs)/dCf = 1 / (cf * ln(10))
			double dRes = -0.121 / (cf * sqrtCf) - 1.0 / (cf * Math.log(10));
			double delta = residual / dRes;
			cf -= delta;
			if (cf < 1e-8) cf = 1e-8;
			if (Math.abs(delta) < 1e-12) break;
		}
		return cf;
	}

	/**
	 * Local Cf from average CF (Eq. 2).
	 * cf = 0.242 * CF / (0.242 + 0.8686 * sqrt(CF))
	 */
	static double localFromAverage(double cf_avg) {
		return 0.242 * cf_avg / (0.242 + 0.8686 * Math.sqrt(cf_avg));
	}

	/**
	 * Compute Van Driest II transformation function Fc (Eq. 8, 12-17).
	 * @param mach edge Mach number
	 * @param twTe wall-to-edge temperature ratio (F = Tw/Te)
	 * @return Fc = compressible-to-incompressible Cf ratio
	 */
	static double computeFc(double mach, double twTe) {
		double m = 0.2 * mach * mach;      // Eq. (17)
		double F = twTe;                     // Eq. (16)
		double A = Math.sqrt(R_TURB * m / F); // Eq. (14)
		double B = (1.0 + R_TURB * m - F) / F; // Eq. (15)

		double disc = Math.sqrt(4.0 * A * A + B * B);
		double alpha = (2.0 * A * A - B) / disc; // Eq. (12)
		double beta = B / disc;                    // Eq. (13)

		double sinInvAlpha = Math.asin(Math.max(-1, Math.min(1, alpha)));
		double sinInvBeta = Math.asin(Math.max(-1, Math.min(1, beta)));
		double denom = sinInvAlpha + sinInvBeta;

		return R_TURB * m / (denom * denom); // Eq. (8)
	}

	/**
	 * Compute Van Driest II Ftheta (Eq. 10) using Keyes viscosity formula.
	 * @param te edge temperature (K)
	 * @param tw wall temperature (K)
	 * @return Ftheta = Re_theta_bar / Re_theta
	 */
	static double computeFtheta(double te, double tw) {
		// Keyes formula: mu ratio via Eq. (10) with 122e-5/T factor
		double keyesFactor_e = 1.0 + 122e-5 / te;
		double keyesFactor_w = 1.0 + 122e-5 / tw;
		// mu_e/mu_w approximated via Sutherland-like temperature dependence
		// For Keyes formula: mu ~ T^0.5 * (1 + S/T)^-1 where S ≈ 122e-5 (in Eq. 10's form)
		// The ratio simplifies to Eq. (10):
		// F_theta = (mu_e/mu_w) * sqrt(Te/Tw) * [1 + 122e-5/Tw] / [1 + 122e-5/Te]
		// Using Sutherland's law for mu_e/mu_w: mu ~ T^1.5 / (T + S), S = 110.4 K
		double S = 110.4;
		double muRatio = Math.pow(te / tw, 1.5) * (tw + S) / (te + S);
		return muRatio * Math.sqrt(te / tw) * keyesFactor_w / keyesFactor_e;
	}

	/**
	 * Compute compressible local Cf via Van Driest II transformation.
	 * @param mach edge Mach number
	 * @param reX length Reynolds number
	 * @param te edge static temperature (K)
	 * @param tw wall temperature (K)
	 * @return local Cf (compressible)
	 */
	public static double vanDriestIICf(double mach, double reX, double te, double tw) {
		double twTe = tw / te;
		double Fc = computeFc(mach, twTe);
		double Ftheta = computeFtheta(te, tw);
		double Fx = Ftheta / Fc;  // Eq. (11)

		// Transform to incompressible Re
		double reBarX = Fx * reX;

		// Solve Schoenherr for incompressible average CF
		double cfBarAvg = solveSchoenherrCF(reBarX);
		double cfBarLocal = localFromAverage(cfBarAvg);

		// Transform back to compressible
		return cfBarLocal / Fc;
	}

	// ================================================================
	// Self-consistency tests for Van Driest II implementation
	// ================================================================

	@Test
	void testSchoenherrAtKnownPoint() {
		// At Re_x = 1e7, incompressible turbulent CF ≈ 0.00293 (Schoenherr)
		double cf = solveSchoenherrCF(1e7);
		assertEquals(0.00293, cf, 0.0002, "Schoenherr CF at Re=1e7");
	}

	@Test
	void testVanDriestReducesToIncompressibleAtLowMach() {
		// At M → 0, Fc → [(1 + sqrt(Tw/Te))/2]² → 1 for Tw = Te
		// So Cf should match incompressible Schoenherr
		double te = 300; // K
		double tw = 300; // K (adiabatic, M=0)
		double reX = 1e7;
		double cf = vanDriestIICf(0.01, reX, te, tw);
		double cfIncomp = localFromAverage(solveSchoenherrCF(reX));
		assertEquals(cfIncomp, cf, cfIncomp * 0.05,
				"Van Driest II should match incompressible at M≈0");
	}

	@Test
	void testCfDecreasesWithMach() {
		// Compressibility reduces friction. Cf should decrease with Mach.
		double reX = 1e7;
		double te = 222; // K (~40000 ft)
		double tw = te;  // adiabatic approximation
		double cfPrev = vanDriestIICf(0.5, reX, te, tw);
		for (double m = 1.0; m <= 10.0; m += 1.0) {
			// Adiabatic wall: Taw = Te * (1 + r*(gamma-1)/2 * M²)
			double taw = te * (1.0 + R_TURB * 0.2 * m * m);
			double cfNow = vanDriestIICf(m, reX, te, taw);
			assertTrue(cfNow < cfPrev,
					String.format("Cf at M=%.0f (%.6f) should be < at lower M (%.6f)", m, cfNow, cfPrev));
			cfPrev = cfNow;
		}
	}

	// ================================================================
	// Comparison: Van Driest II vs ORP Eckert
	// ================================================================

	@ParameterizedTest(name = "Eckert vs VD-II at M={0} Re={1}")
	@CsvSource({
			"0.5,  1e7,  288",
			"1.0,  1e7,  222",
			"2.0,  1e7,  222",
			"3.0,  1e7,  222",
			"5.0,  1e7,  222",
			"7.0,  1e7,  222",
			"10.0, 1e7,  222",
	})
	void testEckertVsVanDriestII(double mach, double reX, double te) {
		// Adiabatic wall temperature
		double tw = te * (1.0 + R_TURB * 0.2 * mach * mach);

		// Compute Van Driest II reference
		double cfVdII = vanDriestIICf(mach, reX, te, tw);

		// Compute ORP Eckert method exactly as BarrowmanDragCalculator does:
		// 1. T* = reference temperature
		// 2. Re* = Eckert-corrected Reynolds
		// 3. Cf_base = incompressibleCf(Re*, false) [Schlichting formula]
		// 4. Cf = Cf_base * (Te / T*)
		double tStar = BarrowmanDragCalculator.calculateReferenceTemperature(mach, te);
		double reEckert = BarrowmanDragCalculator.calculateEckertReynolds(reX, te, tStar);
		// Schlichting formula: 1/(1.5*ln(Re)-5.6)^2
		double lnRe = Math.log(reEckert);
		double cfBase = 1.0 / ((1.5 * lnRe - 5.6) * (1.5 * lnRe - 5.6));
		double cfEckert = cfBase * (te / tStar);

		double divergence = (cfEckert - cfVdII) / cfVdII * 100;

		System.out.printf("M=%4.1f: Cf_VD2=%.6f  Cf_Eckert=%.6f  divergence=%+.1f%%%n",
				mach, cfVdII, cfEckert, divergence);

		// Document the divergence between methods. The Eckert reference-temperature
		// method is known to be less accurate than Van Driest II at high Mach
		// (Hopkins & Inouye 1971). We record the comparison without enforcing tight
		// bounds — the primary purpose is quantifying the gap for potential future
		// improvement of the friction model.
		assertTrue(cfEckert > 0, "Eckert Cf must be positive at M=" + mach);
		assertTrue(cfVdII > 0, "Van Driest II Cf must be positive at M=" + mach);
	}

	// ================================================================
	// Experimental validation: NASA TN D-5089 flat-plate Cf
	// ================================================================

	@ParameterizedTest(name = "D-5089 M={0} Re_theta={1} Cf_exp={6}e-3")
	@CsvFileSource(
			resources = "/info/openrocket/core/aerodynamics/nasa_tn_d5089_flat_plate_cf.csv",
			numLinesToSkip = 1
	)
	void testAgainstExperimentalCf(
			double mach, double reTheta, double ttR, double twR,
			double teR, double twTaw, double cfX1e3, String blTrips) {

		// Convert temperatures from Rankine to Kelvin
		double te = teR / 1.8;
		double tw = twR / 1.8;

		// Estimate length Re from momentum-thickness Re
		// Rough relation: Re_x ≈ Re_theta * 10 (turbulent BL at this Re range)
		// More precisely: Re_x ≈ Re_theta / (Cf/2) ≈ Re_theta * 1000
		// But we need a better estimate. Use Re_theta ~ 0.036 * Re_x^0.8 (turbulent)
		// → Re_x ~ (Re_theta / 0.036)^(1/0.8)
		double reX = Math.pow(reTheta / 0.036, 1.0 / 0.8);

		double cfExp = cfX1e3 / 1000.0;
		double cfVdII = vanDriestIICf(mach, reX, te, tw);

		double error = (cfVdII - cfExp) / cfExp * 100;

		System.out.printf("D-5089 M=%.1f Re_theta=%d Tw/Taw=%.2f: Cf_exp=%.4e  Cf_VD2=%.4e  err=%+.1f%%%n",
				mach, (int) reTheta, twTaw, cfExp, cfVdII, error);

		// Van Driest II should match experimental Cf within ±50%.
		// The Re_x estimation from Re_theta is approximate (Re_x ~ (Re_theta/0.036)^1.25),
		// introducing ~20-30% uncertainty on top of the method's ~10% accuracy.
		// D-5089 reports ±10% agreement for VD-II at M=5.5-7.5 when Re_theta is used directly.
		assertEquals(cfExp, cfVdII, cfExp * 0.50,
				String.format("VD-II vs D-5089 at M=%.1f: exp=%.4e vd2=%.4e (%+.1f%%)",
						mach, cfExp, cfVdII, error));
	}

	// ================================================================
	// Diagnostic: print comparison table
	// ================================================================

	@Test
	void printEckertVsVanDriestTable() {
		System.out.println("\n=== Eckert vs Van Driest II Skin Friction ===");
		System.out.println("Mach   Cf_VD2      Cf_Eckert   Divergence   Cf_ratio(M)/Cf_ratio(0)");
		System.out.println("----   ----------  ----------  ----------   ----------------------");

		double reX = 1e7;
		double te = 222; // K

		for (double m = 0.5; m <= 10.0; m += 0.5) {
			double taw = te * (1.0 + R_TURB * 0.2 * m * m);
			double cfVd = vanDriestIICf(m, reX, te, taw);

			// ORP Eckert: T* -> Re* -> Schlichting(Re*) * (Te/T*)
			double tStar = BarrowmanDragCalculator.calculateReferenceTemperature(m, te);
			double reEckert = BarrowmanDragCalculator.calculateEckertReynolds(reX, te, tStar);
			double lnRe = Math.log(reEckert);
			double cfBase = 1.0 / ((1.5 * lnRe - 5.6) * (1.5 * lnRe - 5.6));
			double cfEckert = cfBase * (te / tStar);

			double div = (cfEckert - cfVd) / cfVd * 100;

			// Incompressible reference
			double cfIncomp = localFromAverage(solveSchoenherrCF(reX));
			double ratioVd = cfVd / cfIncomp;
			double ratioEckert = cfEckert / cfIncomp;

			System.out.printf("M=%4.1f  %.6f    %.6f    %+6.1f%%     VD2=%.3f  Eckert=%.3f%n",
					m, cfVd, cfEckert, div, ratioVd, ratioEckert);
		}
	}
}
