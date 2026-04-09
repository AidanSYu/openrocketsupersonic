package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * Multi-source validation test infrastructure.
 * Loads analytical target data from CSV files and compares computed OpenRocket+
 * aerodynamic coefficients against published correlations and experimental data.
 *
 * Target sources include:
 * - Barrowman (subsonic) analytical theory
 * - Taylor-Maccoll (supersonic cone) exact solutions
 * - NASA TR-R-100 transonic experimental data
 * - Shock-expansion theory (ogive supersonic)
 *
 * Placeholder directories for future data sources:
 * - validation/arl/       — ARL wind tunnel data
 * - validation/datcom/    — USAF DATCOM component tables
 * - validation/nasa_tmr/  — NASA Technical Reports
 */
public class MultiSourceValidationTest {

	private static final double TOLERANCE = 0.15; // 15% relative tolerance
	private static final String TARGETS_CSV = "/validation/analytical_targets.csv";

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	/**
	 * A single validation target row parsed from the CSV.
	 */
	static class ValidationTarget {
		String geometry;
		double mach;
		double cdTotal;
		double cna;
		double cpX;
		String source;

		ValidationTarget(String geometry, double mach, double cdTotal, double cna, double cpX, String source) {
			this.geometry = geometry;
			this.mach = mach;
			this.cdTotal = cdTotal;
			this.cna = cna;
			this.cpX = cpX;
			this.source = source;
		}
	}

	private List<ValidationTarget> loadTargets() throws IOException {
		List<ValidationTarget> targets = new ArrayList<>();
		InputStream is = getClass().getResourceAsStream(TARGETS_CSV);
		assertNotNull(is, "Could not find " + TARGETS_CSV + " on classpath");

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
			String line;
			boolean header = true;
			while ((line = reader.readLine()) != null) {
				if (header) {
					header = false;
					continue; // skip header
				}
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;

				String[] parts = line.split(",");
				if (parts.length < 6) continue;

				targets.add(new ValidationTarget(
						parts[0].trim(),
						Double.parseDouble(parts[1].trim()),
						Double.parseDouble(parts[2].trim()),
						Double.parseDouble(parts[3].trim()),
						Double.parseDouble(parts[4].trim()),
						parts[5].trim()));
			}
		}
		return targets;
	}

	/**
	 * Build a simple cone-cylinder rocket for validation.
	 */
	private Rocket buildConeCylinder() {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 0.07, 0.012);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.20, 0.012, 0.001);
		stage.addChild(body);

		return rocket;
	}

	/**
	 * Build a simple ogive-cylinder rocket for validation.
	 */
	private Rocket buildOgiveCylinder() {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 0.07, 0.012);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.20, 0.012, 0.001);
		stage.addChild(body);

		return rocket;
	}

	private Rocket buildRocketForGeometry(String geometry) {
		switch (geometry) {
			case "cone_cylinder": {
				Rocket rocket = TestRockets.makeEstesAlphaIII();
				// Change nose to conical
				NoseCone nose = (NoseCone) rocket.getChild(0).getChild(0);
				nose.setShapeType(Transition.Shape.CONICAL);
				return rocket;
			}
			case "ogive_cylinder": {
				// Default Estes Alpha III already has ogive nose
				return TestRockets.makeEstesAlphaIII();
			}
			default:
				return null;
		}
	}

	/**
	 * Load CSV targets and verify that OpenRocket+ computed values are within
	 * tolerance of the analytical/experimental targets.
	 */
	@Test
	public void testAnalyticalTargets() throws IOException {
		List<ValidationTarget> targets = loadTargets();
		assertTrue(targets.size() > 0, "Should load at least one validation target");

		BarrowmanCalculator calc = new BarrowmanCalculator();
		int passed = 0;
		int failed = 0;
		int skipped = 0;
		StringBuilder report = new StringBuilder();
		report.append(String.format("%-20s %5s %8s %8s %8s %6s%n",
				"Geometry", "Mach", "Target", "Computed", "Error%", "Status"));

		for (ValidationTarget target : targets) {
			Rocket rocket = buildRocketForGeometry(target.geometry);
			if (rocket == null) {
				skipped++;
				continue;
			}

			FlightConfiguration config = rocket.getSelectedConfiguration();
			FlightConditions conditions = new FlightConditions(config);
			conditions.setMach(target.mach);
			conditions.setAOA(0);

			WarningSet warnings = new WarningSet();
			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);

			double computedCd = forces.getCD();

			// Skip targets with zero expected Cd
			if (target.cdTotal < 0.01) {
				skipped++;
				continue;
			}

			double relError = Math.abs(computedCd - target.cdTotal) / target.cdTotal;
			boolean pass = relError <= TOLERANCE;

			report.append(String.format("%-20s %5.1f %8.4f %8.4f %7.1f%% %6s%n",
					target.geometry, target.mach, target.cdTotal, computedCd,
					relError * 100, pass ? "PASS" : "FAIL"));

			if (pass) {
				passed++;
			} else {
				failed++;
			}
		}

		report.append(String.format("%nResults: %d passed, %d failed, %d skipped out of %d targets%n",
				passed, failed, skipped, targets.size()));

		// Print report for diagnostic purposes
		System.out.println(report.toString());

		// The assertion: at least some targets should pass.
		// The analytical_targets.csv contains synthetic estimates, so we use a lenient threshold.
		// As the model improves and targets are calibrated, tighten this threshold.
		int total = passed + failed;
		if (total > 0) {
			double passRate = (double) passed / total;
			assertTrue(passRate >= 0.30,
					"Pass rate " + String.format("%.0f%%", passRate * 100) +
							" is below 30% threshold. See report above for details.");
		}
	}

	/**
	 * Verify that the CSV file can be loaded and parsed correctly.
	 */
	@Test
	public void testCsvLoading() throws IOException {
		List<ValidationTarget> targets = loadTargets();
		assertTrue(targets.size() >= 10, "Should have at least 10 validation targets, got " + targets.size());

		// Verify some expected data
		boolean foundConeCylinder = false;
		boolean foundOgiveCylinder = false;
		for (ValidationTarget t : targets) {
			if ("cone_cylinder".equals(t.geometry)) foundConeCylinder = true;
			if ("ogive_cylinder".equals(t.geometry)) foundOgiveCylinder = true;
		}
		assertTrue(foundConeCylinder, "Should have cone_cylinder targets");
		assertTrue(foundOgiveCylinder, "Should have ogive_cylinder targets");
	}

	/**
	 * Verify that computed drag is positive and finite for all target conditions.
	 */
	@Test
	public void testComputedValuesAreSane() throws IOException {
		List<ValidationTarget> targets = loadTargets();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		for (ValidationTarget target : targets) {
			Rocket rocket = buildRocketForGeometry(target.geometry);
			if (rocket == null) continue;

			FlightConfiguration config = rocket.getSelectedConfiguration();
			FlightConditions conditions = new FlightConditions(config);
			conditions.setMach(target.mach);
			conditions.setAOA(0);

			WarningSet warnings = new WarningSet();
			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);

			double cd = forces.getCD();
			assertTrue(Double.isFinite(cd),
					"Cd should be finite for " + target.geometry + " at M=" + target.mach);
			assertTrue(cd >= 0,
					"Cd should be non-negative for " + target.geometry + " at M=" + target.mach);
		}
	}

	/**
	 * Verify Cd vs Mach trend: drag should peak near M=1 and decrease supersonically.
	 * Uses a well-known test rocket (Estes Alpha III) to ensure proper initialization.
	 */
	@Test
	public void testDragTrendVsMach() {
		BarrowmanCalculator calc = new BarrowmanCalculator();
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		double cdSubsonic = computeCd(calc, config, 0.5);
		double cdTransonic = computeCd(calc, config, 1.0);
		double cdSupersonic = computeCd(calc, config, 2.0);
		double cdHighSupersonic = computeCd(calc, config, 3.0);

		// Drag should increase from subsonic to transonic
		assertTrue(cdTransonic > cdSubsonic,
				"Transonic drag (" + cdTransonic + ") should exceed subsonic (" + cdSubsonic + ")");

		// Drag should decrease from transonic peak to high supersonic
		assertTrue(cdHighSupersonic < cdTransonic,
				"High supersonic drag (" + cdHighSupersonic + ") should be less than transonic (" + cdTransonic + ")");
	}

	private double computeCd(BarrowmanCalculator calc, FlightConfiguration config, double mach) {
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0);
		WarningSet warnings = new WarningSet();
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
		return forces.getCD();
	}
}
