package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.Application;

/**
 * Published-CFD comparator: ORP vs Bunescu et al. 2025 URANS k-epsilon CFD
 * on the Army-Navy Basic Finner (ANF).
 * <p>
 * Source paper (verified from title page of paper/data/pdf/aerospace-12-00371-v2.pdf):
 *   Bunescu, I.; Hothazie, M.-V.; Stoican, M.-G.; Pricop, M.-V.; Onel, A.-I.;
 *   Afilipoae, T.-P. "Numerical Study of the Basic Finner Model in Rolling Motion."
 *   Aerospace 2025, 12, 371. MDPI, open-access (CC BY 4.0).
 *   https://doi.org/10.3390/aerospace12050371
 * <p>
 * Method: URANS with realizable k-epsilon turbulence model. Validated by the
 * authors against the existing Basic Finner experimental database. Geometry:
 * 60 mm diameter, 600 mm length (L/D = 10), 10-deg cone, four rectangular
 * fins of 1.0 cal chord and 1.0 cal exposed span, 0.04 cal thickness with
 * sharp LE/TE -- the standard Army-Navy Basic Finner. Reference area pi*d^2/4.
 * <p>
 * The OpenRocket Plus benchmark geometry uses a 30 mm aeroballistic-range
 * model (SupersonicTestRockets.makeBasicFinner()) with the same caliber-based
 * dimensions; coefficients are dimensionless and directly comparable.
 * <p>
 * Comparator scope (kept small, per AST reviewer-evidence policy):
 *   - 5 points on Figure 10f: C_X vs Mach at AoA = 0 deg (M = 0.4, 0.95, 1.6, 2.5, 3.5)
 *   - 1 point on Figure 10e: C_N vs Mach at AoA = 10 deg (M = 1.6) as cross-check
 * <p>
 * At AoA = 0 deg the lift coefficient C_L = 0, so C_X (axial force) = C_D (drag).
 * The OpenRocket Plus AerodynamicForces.getCD() value is therefore the direct
 * apples-to-apples comparator for Bunescu's C_X at AoA = 0.
 * <p>
 * This test produces evidence -- not a regression gate. The MAPE assertion is
 * loose (gate set well above the expected ANF/CFD agreement) so that mismatch
 * is surfaced in the comparator CSV without breaking unrelated builds.
 */
public class BunescuANFCfdComparatorTest {

	/** Loose MAPE gate -- this is publication evidence, not a regression lock.
	 *  Set high enough to absorb digitization read uncertainty (+/- 0.03 in C_X)
	 *  plus the known ~10-30% ANF supersonic underprediction documented in
	 *  BasicFinnerDragBenchmarkTest. */
	private static final double MAPE_INFORMATIONAL_GATE_PCT = 60.0;

	/** Bunescu Figure 10 digitized points. Columns: mach, aoaDeg, coeffName, cfdValue. */
	private static final Object[][] BUNESCU_POINTS = {
			// Figure 10f -- C_X vs Mach at AoA = 0 deg (red squares)
			{ 0.40, 0.0, "C_X", 0.46 },
			{ 0.95, 0.0, "C_X", 0.91 },
			{ 1.60, 0.0, "C_X", 0.78 },
			{ 2.50, 0.0, "C_X", 0.55 },
			{ 3.50, 0.0, "C_X", 0.39 },
			// Figure 10e -- C_N vs Mach at AoA = 10 deg (yellow squares)
			{ 1.60, 10.0, "C_N", 3.40 },
	};

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	private static double computeOrpCoefficient(double mach, double aoaDeg, String coeff) {
		Rocket rocket = SupersonicTestRockets.makeBasicFinner();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(aoaDeg));
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		switch (coeff) {
			case "C_X":
				// At AoA=0, C_L = 0, so C_X (axial force coeff) = C_D (drag coeff).
				return forces.getCD();
			case "C_N":
				return forces.getCN();
			default:
				throw new IllegalArgumentException("Unsupported coefficient: " + coeff);
		}
	}

	@Test
	void testBunescuComparator() throws IOException {
		List<String> csvLines = new ArrayList<>();
		csvLines.add("mach,aoa_deg,coefficient,bunescu_cfd,orp_predicted,delta,delta_pct");

		double sumAbsPctErr = 0.0;
		int n = BUNESCU_POINTS.length;

		StringBuilder log = new StringBuilder();
		log.append("=== Bunescu et al. 2025 URANS CFD vs ORP -- Basic Finner ===\n");
		log.append(String.format("%-6s %-7s %-5s %-10s %-10s %-9s %-8s%n",
				"Mach", "AoA_deg", "Coef", "Bunescu", "ORP", "delta", "err%"));

		for (Object[] row : BUNESCU_POINTS) {
			double mach = (double) row[0];
			double aoaDeg = (double) row[1];
			String coeff = (String) row[2];
			double cfd = (double) row[3];

			double orp = computeOrpCoefficient(mach, aoaDeg, coeff);
			double delta = orp - cfd;
			double pctErr = Math.abs(delta) / Math.abs(cfd) * 100.0;
			sumAbsPctErr += pctErr;

			log.append(String.format("%-6.2f %-7.1f %-5s %-10.4f %-10.4f %+9.4f %+8.1f%%%n",
					mach, aoaDeg, coeff, cfd, orp, delta, (orp - cfd) / cfd * 100.0));

			csvLines.add(String.format("%.2f,%.1f,%s,%.4f,%.4f,%+.4f,%+.2f",
					mach, aoaDeg, coeff, cfd, orp, delta, (orp - cfd) / cfd * 100.0));
		}

		double mape = sumAbsPctErr / n;
		log.append(String.format("%nMAPE = %.2f%% (informational gate %.0f%%)%n",
				mape, MAPE_INFORMATIONAL_GATE_PCT));
		System.out.println(log);

		// Write comparator CSV to paper/data/csv/. Working dir during gradle test is
		// the core/ module, so we walk up one level to reach paper/.
		String dateStamp = LocalDate.now().toString().replace("-", "_");
		Path outDir = Paths.get("..", "paper", "data", "csv");
		if (!Files.isDirectory(outDir)) {
			// Fallback: maybe gradle ran from repo root.
			outDir = Paths.get("paper", "data", "csv");
		}
		Files.createDirectories(outDir);
		Path outFile = outDir.resolve("bunescu_anf_comparator_" + dateStamp + ".csv");
		Files.write(outFile, csvLines);
		System.out.println("Wrote comparator CSV: " + outFile.toAbsolutePath());

		// Loose, informational pass -- evidence-not-gate.
		assertTrue(mape <= MAPE_INFORMATIONAL_GATE_PCT,
				String.format("Bunescu CFD comparator MAPE %.2f%% exceeds the loose informational"
						+ " gate %.0f%%. This is not a regression gate; it indicates a substantive"
						+ " divergence from published URANS CFD on the same geometry that warrants"
						+ " investigation.",
						mape, MAPE_INFORMATIONAL_GATE_PCT));
	}
}
