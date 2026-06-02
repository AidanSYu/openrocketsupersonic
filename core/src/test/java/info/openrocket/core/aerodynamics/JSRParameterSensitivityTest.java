package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.util.BaseTestCase;

/**
 * JSR parameter-sensitivity sweep (2026-05-11).
 * <p>
 * Sweeps six physical-model parameters around their nominal values for a
 * representative subset of the 25-flight validation corpus. The parameters
 * are toggled via {@link AblationConfig} fields injected into the production
 * calculators — defaults remain at nominal so the production path is
 * unaffected when the sweep is idle.
 * <p>
 * Output: {@code paper/data/analysis/sensitivity_<date>/sensitivity_sweep.csv}
 * with one row per (flight, parameter, perturbation) triplet. The companion
 * Python script {@code analyze.py} produces tornado charts and the global
 * sensitivity summary.
 * <p>
 * The 6-flight subset spans the Mach range from subsonic to hypersonic:
 * EZI-65 (M~0.6), Raven (M~1.07), Full Metal Jacket (M~2.3),
 * AeroPac 104K (M~3.0), Nike-Deacon (M~5.0), BBV (M~7.2). MESOS 293K and
 * Nike-Apache are intentionally excluded — MESOS needs custom motor preload
 * (separate test harness) and Nike-Apache is chasing a known +30% bias.
 */
@Isolated("AblationConfig scaling fields must not be observed by concurrent tests")
@ResourceLock("AERO_CPU_HEAVY")
@SuppressWarnings("unused")  // FlightCase/Param/SweepRow carry diagnostic fields for traceability
@Tag("sweep")  // paper-data regeneration (sensitivity sweep, ~12.5 min); excluded by default, run with -Psweeps
public class JSRParameterSensitivityTest extends BaseTestCase {

	private static final double FT_PER_M = 3.28084;
	private static final long PER_RUN_TIMEOUT_MS = 240_000;
	private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";
	private static final String SOUNDING_ORK_DIR = "paper/data/ork/sounding_rockets";
	private static final String[] RASP_ENG_PATHS = {
			"simvreal/rasp.eng",
			"c:/Code/OpenRocket Plus/simvreal/rasp.eng",
	};

	@BeforeAll
	static void setup() {
		AblationConfig.reset();
		for (String p : RASP_ENG_PATHS) {
			loadMotorsIntoRASAeroCache(new File(p), "rasp.eng");
		}
	}

	@AfterAll
	static void tearDown() {
		AblationConfig.reset();
	}

	private static void loadMotorsIntoRASAeroCache(File file, String displayName) {
		if (!file.exists()) return;
		try (InputStream stream = new FileInputStream(file)) {
			RASPMotorLoader loader = new RASPMotorLoader();
			List<ThrustCurveMotor.Builder> builders = loader.load(stream, displayName);
			for (ThrustCurveMotor.Builder b : builders) {
				try { RASAeroMotorsLoader.addMotorToCache(b.build()); } catch (Exception ignored) {}
			}
		} catch (Exception e) {
			System.out.println("Could not preload " + file.getPath() + ": " + e.getMessage());
		}
	}

	private enum LoaderKind { CDX1, ORK }

	private static final class FlightCase {
		final String id;
		final LoaderKind kind;
		final String relPath;
		final String label;
		final double realApogeeFt;
		final double rasAeroApogeeFt;     // optional reference; unused in sweep math
		final double approxMaxMach;
		final boolean ascentOnly;

		FlightCase(String id, LoaderKind kind, String relPath, String label,
		           double realApogeeFt, double rasAeroApogeeFt, double approxMaxMach,
		           boolean ascentOnly) {
			this.id = id;
			this.kind = kind;
			this.relPath = relPath;
			this.label = label;
			this.realApogeeFt = realApogeeFt;
			this.rasAeroApogeeFt = rasAeroApogeeFt;
			this.approxMaxMach = approxMaxMach;
			this.ascentOnly = ascentOnly;
		}
	}

	/**
	 * One parameter sweep specification.
	 * The {@code applier} receives a multiplier or absolute override value
	 * (depending on the parameter kind); the {@code nominal} field is the
	 * production value used for the sensitivity normalization.
	 * <p>
	 * {@code activeMachMin}: parameter has no effect at maxMach below this threshold,
	 * so skip the sweep for cheap flights. Set 0 to always run.
	 */
	private static final class Param {
		final String id;
		final String displayName;
		final boolean multiplicative;   // true if perturbation is *nominal*(1+frac)
		final double nominal;
		final double[] fractions;       // e.g. {-0.20, -0.10, +0.10, +0.20}
		final java.util.function.DoubleConsumer applier;
		final double activeMachMin;

		Param(String id, String displayName, boolean multiplicative, double nominal,
		      double[] fractions, java.util.function.DoubleConsumer applier, double activeMachMin) {
			this.id = id;
			this.displayName = displayName;
			this.multiplicative = multiplicative;
			this.nominal = nominal;
			this.fractions = fractions;
			this.applier = applier;
			this.activeMachMin = activeMachMin;
		}
	}

	private static List<FlightCase> buildFlights() {
		List<FlightCase> flights = new ArrayList<>();
		flights.add(new FlightCase("ezi65", LoaderKind.CDX1, "EZI65-1.CDX1",
				"EZI-65 J450ST (subsonic)", 3965, 4214, 0.60, false));
		flights.add(new FlightCase("raven", LoaderKind.CDX1, "Raven.CDX1",
				"Raven (transonic)", 8815, 9332, 1.07, false));
		flights.add(new FlightCase("fmj1", LoaderKind.CDX1, "Full Metal Jacket1.CDX1",
				"FMJ BALLS 005 (supersonic)", 37981, 38772, 2.30, false));
		flights.add(new FlightCase("aeropac104k", LoaderKind.CDX1, "AeroPac104KStageOne&Two-2.CDX1",
				"AeroPac 104K (high supersonic)", 104659, 113786, 3.00, false));
		flights.add(new FlightCase("nike_deacon", LoaderKind.ORK, "nike_deacon_flight1.ork",
				"Nike-Deacon (sounding)", 356_000, Double.NaN, 5.0, true));
		flights.add(new FlightCase("bbv", LoaderKind.ORK, "bbv.ork",
				"Black Brant V VB (sounding)", 897_638, Double.NaN, 7.22, true));
		return flights;
	}

	private static List<Param> buildParams() {
		List<Param> params = new ArrayList<>();
		double[] tenTwenty = {-0.20, -0.10, +0.10, +0.20};
		double[] tenOnly   = {-0.10, +0.10};
		params.add(new Param("cd_scale", "Total Cd scale", true, 1.0, tenTwenty,
				frac -> AblationConfig.cdScale = 1.0 + frac, 0.0));
		// VD2 recovery factor sweep: nominal=0.88, span ±10/20% (~0.70..1.06).
		// VD2 only contributes above M=0.9, so skip for subsonic flights.
		params.add(new Param("vd2_recovery", "Friction recovery factor", true, 0.88, tenTwenty,
				frac -> AblationConfig.vd2RecoveryOverride = 0.88 * (1.0 + frac), 0.9));
		// Modified Newtonian Cp_max: blended from M=4-6, dominant at M>5. Sweep ±15%.
		double[] cpMaxFracs = {-0.15, -0.075, +0.075, +0.15};
		params.add(new Param("cpmax_scale", "Modified Newtonian Cp_max scale", true, 1.0, cpMaxFracs,
				frac -> AblationConfig.cpMaxScale = 1.0 + frac, 4.0));
		// Base drag (Devan-Ashwood A): active supersonic only.
		params.add(new Param("base_a", "Base drag intercept (Devan-Ashwood A)", true, 0.064, tenTwenty,
				frac -> AblationConfig.baseDragAOverride = 0.064 * (1.0 + frac), 1.3));
		// Slender-body decay end Mach: nominal 5.0, sweep 4.0-12.0.
		// Body needs L/D > 15 AND M between 1.05 and decayEnd to be affected, so
		// gate at M >= 1.05.
		double[] decayFracs = {-0.20, -0.10, +0.10, +0.20, +0.50, +1.40};
		params.add(new Param("slender_decay_end", "Slender-body decay end Mach", true, 5.0, decayFracs,
				frac -> AblationConfig.slenderBodyDecayEndOverride = 5.0 * (1.0 + frac), 1.05));
		params.add(new Param("density_scale", "Atmosphere density scale", true, 1.0, tenOnly,
				frac -> AblationConfig.atmosphericDensityScale = 1.0 + frac, 0.0));
		return params;
	}

	@Test
	void writeParameterSensitivitySweep() throws Exception {
		File reportRoot = findReportsRoot();
		File simvrealDir = findDir(SIMVREAL_DIR);
		File orkDir = findDir(SOUNDING_ORK_DIR);

		List<FlightCase> flights = buildFlights();
		List<Param> params = buildParams();

		// Filter flights to those whose source files actually exist.
		List<FlightCase> avail = new ArrayList<>();
		for (FlightCase fc : flights) {
			File src = (fc.kind == LoaderKind.CDX1)
					? new File(simvrealDir, fc.relPath)
					: new File(orkDir, fc.relPath);
			if (src.exists()) {
				avail.add(fc);
			} else {
				System.out.println("SKIP flight " + fc.id + " (file missing: " + src + ")");
			}
		}
		assertTrue(avail.size() >= 4,
				"Need at least 4 flights for the sensitivity sweep; have " + avail.size());

		// Compute nominal apogee per flight (one warm-up + 1 measurement; we use the measurement).
		Map<String, Double> nominalApogeeFt = new LinkedHashMap<>();
		Map<String, Double> nominalMaxMach = new LinkedHashMap<>();
		Map<String, Boolean> nominalSuccess = new LinkedHashMap<>();
		for (FlightCase fc : avail) {
			AblationConfig.reset();
			SimResult nominal = runFlight(simvrealDir, orkDir, fc);
			nominalApogeeFt.put(fc.id, nominal.apogeeFt);
			nominalMaxMach.put(fc.id, nominal.maxMach);
			nominalSuccess.put(fc.id, nominal.success);
			System.out.printf(Locale.US, "[nominal] %-30s apogee=%.0f ft, M=%.2f, success=%b%n",
					fc.id, nominal.apogeeFt, nominal.maxMach, nominal.success);
		}

		List<SweepRow> rows = new ArrayList<>();
		// Add nominal rows so the CSV is self-contained.
		for (FlightCase fc : avail) {
			rows.add(new SweepRow(fc, "nominal", 0.0, 1.0,
					nominalApogeeFt.get(fc.id), nominalApogeeFt.get(fc.id),
					nominalMaxMach.get(fc.id), nominalSuccess.get(fc.id)));
		}

		// Sweep loop.
		long sweepStart = System.currentTimeMillis();
		int totalRuns = 0;
		for (FlightCase fc : avail) {
			if (!nominalSuccess.get(fc.id) || nominalApogeeFt.get(fc.id) <= 1.0) {
				System.out.println("SKIP sweep for " + fc.id + " (nominal run failed)");
				continue;
			}
			double nomApogee = nominalApogeeFt.get(fc.id);
			for (Param p : params) {
				if (fc.approxMaxMach < p.activeMachMin) {
					System.out.printf(Locale.US, "  [skip] %s: %s inactive at M=%.2f (need >= %.2f)%n",
							fc.id, p.id, fc.approxMaxMach, p.activeMachMin);
					continue;
				}
				for (double frac : p.fractions) {
					AblationConfig.reset();
					p.applier.accept(frac);
					double appliedValue = p.multiplicative ? p.nominal * (1.0 + frac) : frac;
					SimResult r = runFlight(simvrealDir, orkDir, fc);
					rows.add(new SweepRow(fc, p.id, frac, appliedValue,
							r.apogeeFt, nomApogee, r.maxMach, r.success));
					totalRuns++;
					System.out.printf(Locale.US,
							"  [sweep] %-12s %-20s frac=%+.3f -> apogee=%.0f ft (delta %+.2f%%)%n",
							fc.id, p.id, frac, r.apogeeFt,
							r.success ? 100.0 * (r.apogeeFt - nomApogee) / nomApogee : Double.NaN);
				}
			}
		}
		AblationConfig.reset();

		long sweepMin = (System.currentTimeMillis() - sweepStart) / 60_000L;
		System.out.printf(Locale.US, "Completed %d sweep runs in ~%d min%n", totalRuns, sweepMin);

		// Write CSV.
		File outDir = new File(reportRoot, "paper/data/analysis/sensitivity_2026_05_11");
		assertTrue(outDir.exists() || outDir.mkdirs(), "could not create " + outDir);
		File csv = new File(outDir, "sensitivity_sweep.csv");
		try (BufferedWriter w = new BufferedWriter(new FileWriter(csv))) {
			w.write("flight_id,flight_label,real_apogee_ft,nominal_apogee_ft,nominal_max_mach,"
					+ "param_id,perturbation_fraction,applied_value,perturbed_apogee_ft,"
					+ "delta_apogee_pct,sensitivity,success\n");
			for (SweepRow r : rows) w.write(r.toCsv() + "\n");
		}
		System.out.println("Sensitivity sweep CSV: " + csv.getAbsolutePath());

		// Sanity: the cd_scale +20% perturbation must reduce apogee on at least
		// one flight. Used to catch toggle-wiring regressions.
		long cdScaleSensitive = rows.stream()
				.filter(r -> "cd_scale".equals(r.paramId) && r.perturbationFraction > 0.15)
				.filter(r -> r.success && r.deltaApogeePct() < -0.5)
				.count();
		assertTrue(cdScaleSensitive >= 1,
				"cd_scale +20% should reduce apogee on >= 1 flight, but " + cdScaleSensitive
						+ " did. Toggle infrastructure may be broken.");
	}

	private static SimResult runFlight(File simvrealDir, File orkDir, FlightCase fc) throws Exception {
		File src = (fc.kind == LoaderKind.CDX1)
				? new File(simvrealDir, fc.relPath)
				: new File(orkDir, fc.relPath);
		if (!src.exists()) {
			return SimResult.fail("FILE_MISSING");
		}
		try {
			GeneralRocketLoader loader = new GeneralRocketLoader(src);
			OpenRocketDocument doc = loader.load();
			Rocket rocket = doc.getRocket();
			// For .ork files: activate the first non-empty configuration; ensure all stages active.
			if (fc.kind == LoaderKind.ORK) {
				for (FlightConfigurationId fcid : rocket.getIds()) {
					if (fcid != null && rocket.getFlightConfiguration(fcid).hasMotors()) {
						rocket.setSelectedConfiguration(fcid);
						break;
					}
				}
				rocket.getSelectedConfiguration().setAllStages();
			}

			List<Simulation> sims = doc.getSimulations();
			Simulation sim;
			if (sims.isEmpty()) {
				sim = new Simulation(doc, rocket);
				doc.addSimulation(sim);
			} else {
				sim = sims.get(0);
			}
			sim.setFlightConfigurationId(rocket.getSelectedConfiguration().getFlightConfigurationID());
			sim.getOptions().setRandomSeed(SimVRealBenchmarkTest.BENCHMARK_RANDOM_SEED);
			// Faster timestep for the sweep (descent isn't validated).
			sim.getOptions().setTimeStep(0.05);
			sim.getOptions().setMaximumStepAngle(Math.toRadians(fc.ascentOnly ? 5.0 : 3.0));
			sim.getOptions().setMaxSimulationTime(fc.ascentOnly ? 320.0 : 2400.0);

			AtomicReference<Throwable> err = new AtomicReference<>();
			Thread t = new Thread(() -> {
				try {
					sim.simulate();
				} catch (Throwable th) {
					err.set(th);
				}
			}, "sens-" + fc.id);
			t.start();
			t.join(PER_RUN_TIMEOUT_MS);
			if (t.isAlive()) {
				t.interrupt();
				return SimResult.fail("TIMEOUT");
			}
			FlightData data = sim.getSimulatedData();
			if (err.get() != null) {
				// Use partial apogee if substantial.
				if (data != null && data.getBranchCount() > 0
						&& Double.isFinite(data.getMaxAltitude())
						&& data.getMaxAltitude() > 1.0) {
					double apFt = data.getMaxAltitude() * FT_PER_M;
					double mach = data.getMaxMachNumber();
					if (apFt > fc.realApogeeFt * 0.10) {
						return new SimResult(apFt, mach, true, "PARTIAL");
					}
				}
				return SimResult.fail("EXC:" + err.get().getClass().getSimpleName());
			}
			if (data == null || data.getBranchCount() == 0
					|| !Double.isFinite(data.getMaxAltitude())) {
				return SimResult.fail("NO_DATA");
			}
			double apFt = data.getMaxAltitude() * FT_PER_M;
			return new SimResult(apFt, data.getMaxMachNumber(), apFt > 1.0, "NORMAL");
		} catch (Throwable th) {
			return SimResult.fail("LOAD_EXC:" + th.getClass().getSimpleName());
		}
	}

	private static File findReportsRoot() {
		Path cur = Paths.get(System.getProperty("user.dir"));
		for (int i = 0; i < 6 && cur != null; i++) {
			File candidate = cur.resolve("paper/data/csv").toFile();
			if (candidate.exists()) return cur.toFile();
			cur = cur.getParent();
		}
		return new File(System.getProperty("user.dir"));
	}

	private static File findDir(String rel) {
		File d = new File(rel);
		if (d.exists()) return d;
		Path cur = Paths.get(System.getProperty("user.dir"));
		for (int i = 0; i < 6 && cur != null; i++) {
			File c = cur.resolve(rel).toFile();
			if (c.exists()) return c;
			cur = cur.getParent();
		}
		return null;
	}

	private static final class SimResult {
		final double apogeeFt;
		final double maxMach;
		final boolean success;
		final String note;

		SimResult(double apogeeFt, double maxMach, boolean success, String note) {
			this.apogeeFt = apogeeFt;
			this.maxMach = maxMach;
			this.success = success;
			this.note = note;
		}

		static SimResult fail(String reason) {
			return new SimResult(Double.NaN, Double.NaN, false, reason);
		}
	}

	private static final class SweepRow {
		final FlightCase fc;
		final String paramId;
		final double perturbationFraction;
		final double appliedValue;
		final double perturbedApogeeFt;
		final double nominalApogeeFt;
		final double maxMach;
		final boolean success;

		SweepRow(FlightCase fc, String paramId, double perturbationFraction, double appliedValue,
		         double perturbedApogeeFt, double nominalApogeeFt, double maxMach, boolean success) {
			this.fc = fc;
			this.paramId = paramId;
			this.perturbationFraction = perturbationFraction;
			this.appliedValue = appliedValue;
			this.perturbedApogeeFt = perturbedApogeeFt;
			this.nominalApogeeFt = nominalApogeeFt;
			this.maxMach = maxMach;
			this.success = success;
		}

		double deltaApogeePct() {
			if (!success || nominalApogeeFt <= 0 || !Double.isFinite(perturbedApogeeFt)) return Double.NaN;
			return 100.0 * (perturbedApogeeFt - nominalApogeeFt) / nominalApogeeFt;
		}

		double sensitivity() {
			if (perturbationFraction == 0.0) return 0.0;
			double d = deltaApogeePct();
			if (Double.isNaN(d)) return Double.NaN;
			// Normalized: % apogee change per 100% parameter change
			return d / perturbationFraction;
		}

		String toCsv() {
			return String.format(Locale.US,
					"\"%s\",\"%s\",%.0f,%.0f,%.3f,\"%s\",%.4f,%.6f,%.3f,%.4f,%.4f,%b",
					fc.id, fc.label, fc.realApogeeFt, nominalApogeeFt, fc.approxMaxMach,
					paramId, perturbationFraction, appliedValue,
					Double.isFinite(perturbedApogeeFt) ? perturbedApogeeFt : Double.NaN,
					deltaApogeePct(), sensitivity(), success);
		}
	}
}
