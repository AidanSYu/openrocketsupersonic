package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.Application;

/**
 * Benchmark: ORP total CD vs ADA636861 Basic Finner aeroballistic data.
 * <p>
 * Source: Dupuis, A.D. and Hathaway, W., "Aeroballistic Range Tests of the
 * Basic Finner Reference Projectile at Supersonic Velocities,"
 * DREV-TM-9703, Defence Research Establishment Valcartier, Aug 1997.
 * DTIC Accession Number: ADA636861.
 * <p>
 * The Basic Finner (Army-Navy Basic Finner, ANF) is the standard calibration
 * body for missile/projectile aerodynamics. 10-deg cone + cylinder (L/D=10) +
 * 4 rectangular fins with sharp leading edges (HEXAGONAL cross-section).
 * <p>
 * CX0 = zero-yaw axial force coefficient (total drag at alpha=0), referenced
 * to body cross-section area S = pi*d^2/4. Data from multiple-fit analysis
 * of aeroballistic range free-flight, M 1.077 to 4.30.
 * <p>
 * <b>First finned-vehicle total-drag benchmark in the repo.</b>
 * <p>
 * Baseline (2026-04-14): MAPE = 22.7 %, consistent underprediction (-14 to
 * -31 %). ORP underpredicts mid-supersonic drag, likely due to Reynolds number
 * mismatch (30mm model Re_d ~ 1-6e5 vs ORP full-scale) and simplified base
 * drag model for finned flat-base geometry.
 */
public class BasicFinnerDragBenchmarkTest {

	/** MAPE gate. Baseline 22.7 % (2026-04-14). Gate at 30 % to catch regressions.
	 * Remaining gap is understood: Re mismatch (30mm model Re_d ~3e5 vs ORP
	 * full-scale ~1.4e6), simplified base drag for finned flat-base geometry,
	 * and HEXAGONAL cross-section assumes sharp TE when the actual fins have
	 * blunt trailing edges (0.08 cal). Do NOT tune model constants to close
	 * this gap without independent validation from a second finned-body dataset. */
	private static final double MAPE_GATE_PCT = 30.0;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	private double computeTotalCd(double mach) {
		Rocket rocket = SupersonicTestRockets.makeBasicFinner();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		return forces.getCD();
	}

	private double[] computeComponents(double mach) {
		Rocket rocket = SupersonicTestRockets.makeBasicFinner();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		return new double[] {
				forces.getFrictionCD(), forces.getPressureCD(),
				forces.getBaseCD(), forces.getCD()
		};
	}

	// ================================================================
	// ADA636861 multiple-fit CX0 data (8 points, M 1.077 to 4.30)
	// Per-point tolerances set at current error + margin for regression.
	// ================================================================

	@ParameterizedTest(name = "Basic Finner M={0}: CX0_exp={1}")
	@CsvSource({
			// Near-transonic: ~14-16% underprediction
			"1.077, 0.863, 25",
			"1.293, 0.731, 25",
			// Mid-supersonic: ~30% underprediction (base drag gap)
			"1.832, 0.585, 40",
			"2.375, 0.484, 40",
			"2.718, 0.435, 40",
			// High supersonic: improving to ~15-26%
			"3.147, 0.373, 35",
			"3.734, 0.309, 30",
			"4.300, 0.271, 25",
	})
	void testCx0VsADA636861(double mach, double cx0Exp, double tolPct) {
		double cdOrp = computeTotalCd(mach);
		double errPct = Math.abs(cdOrp - cx0Exp) / cx0Exp * 100;

		System.out.printf("Basic Finner M=%.3f: ADA636861=%.3f ORP=%.3f err=%+.1f%%%n",
				mach, cx0Exp, cdOrp, (cdOrp - cx0Exp) / cx0Exp * 100);

		assertTrue(errPct <= tolPct,
				String.format("Basic Finner M=%.3f: ORP=%.4f exp=%.4f err=%.1f%% > %.0f%%",
						mach, cdOrp, cx0Exp, errPct, tolPct));
	}

	// ================================================================
	// Full diagnostic with component breakdown + MAPE gate
	// ================================================================

	@Test
	void testDiagnosticAndMAPE() {
		double[][] data = {
				{ 1.077, 0.863 },
				{ 1.293, 0.731 },
				{ 1.832, 0.585 },
				{ 2.375, 0.484 },
				{ 2.718, 0.435 },
				{ 3.147, 0.373 },
				{ 3.734, 0.309 },
				{ 4.300, 0.271 },
		};

		double sumAbsPctErr = 0;
		StringBuilder sb = new StringBuilder();
		sb.append("=== Basic Finner CX0 Benchmark (ADA636861) ===\n");
		sb.append(String.format("%-8s %-8s %-8s %-10s %-8s %-8s %-8s%n",
				"Mach", "CX0_exp", "CD_orp", "err%", "fric", "press", "base"));

		for (double[] row : data) {
			double mach = row[0];
			double cx0Exp = row[1];
			double[] comp = computeComponents(mach);
			double cdOrp = comp[3];
			double bias = (cdOrp - cx0Exp) / cx0Exp * 100;
			sumAbsPctErr += Math.abs(bias);
			sb.append(String.format("%-8.3f %-8.3f %-8.3f %+-9.1f%% %-8.4f %-8.4f %-8.4f%n",
					mach, cx0Exp, cdOrp, bias, comp[0], comp[1], comp[2]));
		}

		double mape = sumAbsPctErr / data.length;
		sb.append(String.format("%nMAPE = %.1f%% (gate = %.0f%%)", mape, MAPE_GATE_PCT));
		System.out.println(sb);

		assertTrue(mape <= MAPE_GATE_PCT,
				String.format("Basic Finner MAPE %.1f%% exceeds gate %.0f%%", mape, MAPE_GATE_PCT));
	}

	// ================================================================
	// Trend tests
	// ================================================================

	@Test
	void testSupersonicDragDecline() {
		double cd15 = computeTotalCd(1.5);
		double cd25 = computeTotalCd(2.5);
		double cd35 = computeTotalCd(3.5);
		assertTrue(cd15 > cd25,
				String.format("CD should decrease: CD(M=1.5)=%.4f > CD(M=2.5)=%.4f", cd15, cd25));
		assertTrue(cd25 > cd35,
				String.format("CD should decrease: CD(M=2.5)=%.4f > CD(M=3.5)=%.4f", cd25, cd35));
	}

	@Test
	void testComponentBreakdownSanity() {
		double[] comp = computeComponents(2.0);
		double friction = comp[0], pressure = comp[1], base = comp[2], total = comp[3];

		System.out.printf("Basic Finner M=2.0: friction=%.4f pressure=%.4f base=%.4f total=%.4f%n",
				friction, pressure, base, total);

		assertTrue(friction > 0, "Friction CD must be positive");
		assertTrue(pressure > 0, "Pressure CD must be positive");
		assertTrue(base > 0, "Base CD must be positive");
		assertTrue(pressure > friction,
				String.format("Wave drag (%.4f) should exceed friction (%.4f) at M=2", pressure, friction));
	}
}
