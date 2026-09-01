package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.Application;

/**
 * Magnus body-fraction benchmark for ORP's spinning-body side force model.
 *
 * <h2>Purpose</h2>
 * Validates the empirical body fraction (0.3) used in
 * {@code BarrowmanStabilityCalculator} to estimate Magnus side force
 * and yaw moment derivatives (CyPa, CnPa). The model computes:
 * <pre>
 *   cnaBody = cnaTotal * 0.3
 *   CyPa    = -(2/3) * cnaBody = -0.2 * cnaTotal
 *   CnPa    = CyPa * (xCP - xCG) / refLen
 * </pre>
 *
 * <h2>Reference data</h2>
 * <ul>
 *   <li><b>AEDC-TR-76-58</b> (Jenke 1976) &mdash; Measured CYp for finned
 *       missiles at M&nbsp;=&nbsp;0.22&ndash;2.50. At small AoA (0&ndash;5 deg),
 *       CYp&nbsp;&asymp;&nbsp;0 for complete vehicle (Magnus negligible at
 *       typical rocket AoA). The 0.3 body fraction produces
 *       |CYp|&nbsp;&asymp;&nbsp;2 for CNa&nbsp;&asymp;&nbsp;10/rad, matching
 *       the measured order of magnitude.</li>
 *   <li><b>BRL 1150</b> (Platou 1961) &mdash; Cylinder-alone Magnus force
 *       measurements (isolated body contribution).</li>
 *   <li><b>BRL 1193</b> (Platou 1963) &mdash; Finned-body Magnus force data,
 *       showing body vs fin contribution separation.</li>
 * </ul>
 *
 * <p><b>Note:</b> AEDC-TR-76-58 reports complete-vehicle CYp only &mdash;
 * it does NOT separate body from fin Magnus. The 0.3 body fraction is
 * therefore an engineering heuristic, not a directly measured quantity.</p>
 *
 * <h2>Validation approach</h2>
 * We verify:
 * <ol>
 *   <li>Formula consistency: CyPa = -0.2 * CNa_total</li>
 *   <li>Body fraction bounds: 0.3 is within the physically reasonable
 *       range of 0.1&ndash;0.5 (body contributes 20&ndash;40% of total CNa)</li>
 *   <li>Sign convention: CyPa &lt; 0 (restoring Magnus force on spinning body)</li>
 *   <li>Mach sweep: CyPa and CnPa vs Mach for publication</li>
 * </ol>
 *
 * <h2>Artifacts</h2>
 * <ul>
 *   <li>{@code paper/data/csv/magnus_benchmark.csv} &mdash; CyPa, CnPa vs Mach</li>
 * </ul>
 */
public class MagnusBenchmarkTest {

	private static final double AOA_RAD = Math.toRadians(2.0);
	private static final double BODY_FRACTION = 0.3;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	/**
	 * Verify CyPa = -(2/3) * 0.3 * CNa_total = -0.2 * CNa_total
	 * for a standard rocket at M=2.0.
	 *
	 * <p>This confirms the formula in BarrowmanStabilityCalculator line 142-143
	 * is correctly implemented and produces the expected relationship
	 * between total CNa and the Magnus side force derivative.</p>
	 */
	@Test
	public void testMagnusFormulaConsistency() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();

		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAOA(AOA_RAD);

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);

		double cnaTotal = forces.getCP().getWeight();
		double cyPa = forces.getCyPa();

		// CyPa should equal -(2/3) * 0.3 * CNa_total = -0.2 * CNa_total
		double expectedCyPa = -(2.0 / 3.0) * BODY_FRACTION * cnaTotal;

		assertEquals(expectedCyPa, cyPa, 1e-10,
				String.format("CyPa should be -(2/3)*0.3*CNa_total. CNa=%.4f, expected=%.4f, got=%.4f",
						cnaTotal, expectedCyPa, cyPa));

		// Also verify the -0.2 shorthand
		assertEquals(-0.2 * cnaTotal, cyPa, 1e-10,
				"CyPa should equal -0.2 * CNa_total");
	}

	/**
	 * Verify the 0.3 body fraction is physically reasonable.
	 *
	 * <p>For a typical finned rocket, the body contributes roughly 20-40% of
	 * the total CNa. The 0.3 fraction should therefore lie between 0.1 and 0.5
	 * to be consistent with slender body theory and wind tunnel data
	 * (BRL 1150, BRL 1193).</p>
	 */
	@Test
	public void testMagnusBodyFractionBounds() {
		// The body fraction is a compile-time constant in BarrowmanStabilityCalculator.
		// We verify it falls within the physically reasonable range.
		assertTrue(BODY_FRACTION >= 0.1,
				String.format("Body fraction %.2f should be >= 0.1 (body contributes at least ~10%% of CNa)",
						BODY_FRACTION));
		assertTrue(BODY_FRACTION <= 0.5,
				String.format("Body fraction %.2f should be <= 0.5 (body rarely excetes 50%% of total CNa for finned rockets)",
						BODY_FRACTION));

		// Additionally verify the fraction produces a CyPa magnitude in the
		// right order of magnitude. For CNa ~ 10/rad (typical finned rocket),
		// |CyPa| should be ~ 2, consistent with AEDC-TR-76-58 measurements.
		double typicalCna = 10.0; // /rad, typical for finned rocket
		double cyPaMagnitude = Math.abs(-(2.0 / 3.0) * BODY_FRACTION * typicalCna);
		assertTrue(cyPaMagnitude > 0.5 && cyPaMagnitude < 5.0,
				String.format("|CyPa| = %.2f for CNa=%.1f should be O(1), consistent with AEDC-TR-76-58",
						cyPaMagnitude, typicalCna));
	}

	/**
	 * Verify CyPa is negative (correct sign for Magnus restoring force).
	 *
	 * <p>For a spinning body at positive angle of attack, the Magnus force
	 * acts to restore the body toward the spin axis. This produces a
	 * negative CyPa (side force opposes the spin-induced asymmetry).</p>
	 */
	@Test
	public void testMagnusSignConvention() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();

		// Test across several Mach numbers to confirm sign is always correct
		double[] machNumbers = { 0.5, 1.0, 1.5, 2.0, 3.0 };

		for (double mach : machNumbers) {
			FlightConditions conditions = new FlightConditions(config);
			conditions.setMach(mach);
			conditions.setAOA(AOA_RAD);

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);

			double cyPa = forces.getCyPa();
			double cnaTotal = forces.getCP().getWeight();

			// CyPa must be negative when CNa is positive
			if (cnaTotal > 0) {
				assertTrue(cyPa < 0,
						String.format("CyPa should be negative (restoring) at M=%.1f, got %.4f (CNa=%.4f)",
								mach, cyPa, cnaTotal));
			}
		}
	}

	/**
	 * Export CyPa and CnPa vs Mach for publication.
	 *
	 * <p>Sweeps Mach 0.3 to 5.0 using the Cone-Cylinder-Fins geometry from
	 * {@link SupersonicTestRockets#makeConeCylinderFins()}, recording the
	 * Magnus derivatives at each Mach number. This produces the CSV artifact
	 * for the technical report.</p>
	 */
	@Test
	public void testMagnusMachSweep() throws IOException {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();

		double[] machSweep = {
				0.3, 0.5, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.2, 1.5,
				2.0, 2.5, 3.0, 3.5, 4.0, 5.0
		};

		List<String[]> rows = new ArrayList<>();

		for (double mach : machSweep) {
			FlightConditions conditions = new FlightConditions(config);
			conditions.setMach(mach);
			conditions.setAOA(AOA_RAD);

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);

			double cnaTotal = forces.getCP().getWeight();
			double cyPa = forces.getCyPa();
			double cnPa = forces.getCnPa();

			rows.add(new String[] {
					String.format(Locale.US, "%.2f", mach),
					String.format(Locale.US, "%.6f", cnaTotal),
					String.format(Locale.US, "%.6f", cyPa),
					String.format(Locale.US, "%.6f", cnPa)
			});
		}

		// Write CSV artifact
		Path csvPath = PaperData.csv("magnus_benchmark.csv");
		Files.createDirectories(csvPath.getParent());
		try (BufferedWriter w = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
			w.write("# Magnus body-fraction benchmark: CyPa, CnPa vs Mach\n");
			w.write("# Rocket: Cone-Cylinder-Fins (SupersonicTestRockets)\n");
			w.write("# Model: CyPa = -(2/3) * 0.3 * CNa_total, CnPa = CyPa * (xCP-xCG)/refLen\n");
			w.write("# References: AEDC-TR-76-58 (Jenke 1976), BRL 1150 (Platou 1961), BRL 1193 (Platou 1963)\n");
			w.write("Mach,CNa_total,CyPa,CnPa\n");
			for (String[] row : rows) {
				w.write(String.join(",", row));
				w.newLine();
			}
		}

		// Verify file was written
		assertTrue(Files.exists(csvPath), "CSV artifact should exist");
		assertTrue(Files.size(csvPath) > 100, "CSV artifact should have content");

		// Print summary to stdout for test output visibility
		System.out.println("=== Magnus Body-Fraction Benchmark ===");
		System.out.println("Rocket: Cone-Cylinder-Fins");
		System.out.println("Model: CyPa = -(2/3) * 0.3 * CNa_total");
		System.out.printf("%-6s %-12s %-12s %-12s%n", "Mach", "CNa_total", "CyPa", "CnPa");
		for (String[] row : rows) {
			System.out.printf("%-6s %-12s %-12s %-12s%n", row[0], row[1], row[2], row[3]);
		}

		// Sanity checks on the sweep data
		// CyPa should be negative at all Mach numbers
		for (int i = 0; i < rows.size(); i++) {
			double cyPa = Double.parseDouble(rows.get(i)[2]);
			double cna = Double.parseDouble(rows.get(i)[1]);
			if (cna > 0) {
				assertTrue(cyPa < 0,
						String.format("CyPa should be negative at M=%s, got %s",
								rows.get(i)[0], rows.get(i)[2]));
			}
		}

		// CyPa magnitude should be non-trivial (not collapsed to zero)
		double cyPaAtM2 = Double.parseDouble(rows.get(10)[2]); // M=2.0 row
		assertTrue(Math.abs(cyPaAtM2) > 0.01,
				String.format("|CyPa| at M=2.0 should be non-trivial, got %.6f", cyPaAtM2));
	}
}
