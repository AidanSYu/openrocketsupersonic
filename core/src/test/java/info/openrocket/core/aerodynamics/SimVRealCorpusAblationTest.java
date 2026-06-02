package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.logging.SimulationAbort;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.util.BaseTestCase;

/**
 * Publication ablation runner for the May 1 SimVReal validation snapshot.
 * <p>
 * This is intentionally a bounded ablation, not a retuning harness. It probes
 * the highest-leverage imported CDX1 mechanisms on the hardest 24-flight
 * SimVReal cases: stage-aware nozzle pressure-thrust and RASAero's
 * force-turbulent-BL flag. MESOS stays in its dedicated validation test because
 * it has custom motor-loading and staging diagnostics that should not be hidden
 * behind this generic subset runner.
 * The full 24-flight corpus baseline is regression-locked by
 * {@link SimVRealBenchmarkTest}; this test adds mechanism deltas without
 * multiplying runtime by rerunning every low-leverage subsonic case.
 */
@ResourceLock("AERO_CPU_HEAVY")
@Tag("sweep")  // paper-data regeneration (corpus ablation subset, ~1.7 min); excluded by default, run with -Psweeps
public class SimVRealCorpusAblationTest extends BaseTestCase {

	private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";
	private static final String REPORT_DIR = "build/reports/simvreal-ablation";
	private static final double FT_PER_METER = 3.28084;

	private static final String[] RASP_ENG_PATHS = {
			"simvreal/rasp.eng",
			"c:/Code/OpenRocket Plus/simvreal/rasp.eng",
	};

	private static final Set<String> NOZZLE_ABLATION_CASES = Set.of(
			"Don't Debate This",
			"Qu8k",
			"Proteus 6",
			"Full Metal Jacket BALLS 005",
			"A-601 Kinsel",
			"AeroPac 104K");

	private static final Set<String> TURBULENCE_ABLATION_CASES = Set.of(
			"Don't Debate This",
			"Qu8k",
			"A-601 Kinsel",
			"AeroPac 104K");

	private static final Map<String, Double> FROZEN_ERROR_PCT = new LinkedHashMap<>();
	static {
		FROZEN_ERROR_PCT.put("Don't Debate This", -6.0506);
		FROZEN_ERROR_PCT.put("Qu8k", -1.8860);
		FROZEN_ERROR_PCT.put("Proteus 6", 7.3724);
		FROZEN_ERROR_PCT.put("Full Metal Jacket BALLS 005", -1.9076);
		FROZEN_ERROR_PCT.put("A-601 Kinsel", 8.7169);
		FROZEN_ERROR_PCT.put("AeroPac 104K", -1.0097);
	}

	@BeforeAll
	static void setup() {
		for (String path : RASP_ENG_PATHS) {
			loadMotorsIntoRASAeroCache(new File(path), "rasp.eng");
		}
	}

	private static void loadMotorsIntoRASAeroCache(File file, String displayName) {
		if (!file.exists()) return;
		try (InputStream stream = new FileInputStream(file)) {
			RASPMotorLoader loader = new RASPMotorLoader();
			List<ThrustCurveMotor.Builder> builders = loader.load(stream, displayName);
			for (ThrustCurveMotor.Builder builder : builders) {
				try {
					RASAeroMotorsLoader.addMotorToCache(builder.build());
				} catch (Exception ignored) {
					// Keep going; the SimVReal corpus has duplicate aliases in a few motor files.
				}
			}
		} catch (Exception e) {
			System.out.println("Could not preload " + file.getPath() + ": " + e.getMessage());
		}
	}

	@Test
	void testWriteAstCorpusAblationSubset() throws Exception {
		File simvrealDir = findDir(SIMVREAL_DIR);
		if (simvrealDir == null) {
			System.out.println("SKIP: SimVReal directory not found.");
			return;
		}

		List<AblationCase> cases = selectedCases(simvrealDir);
		Map<String, SimResult> baselineByRocket = new LinkedHashMap<>();
		List<AblationRow> rows = new ArrayList<>();

		for (AblationCase c : cases) {
			SimResult baseline = runCase(c, Mutation.BASELINE);
			baselineByRocket.put(c.validationCase.rocketName, baseline);
			rows.add(new AblationRow(c, Mutation.BASELINE, baseline, baseline));

			double frozen = FROZEN_ERROR_PCT.get(c.validationCase.rocketName);
			double drift = Math.abs(baseline.errorPct - frozen);
			assertTrue(drift <= 2.0,
					String.format(Locale.US,
							"%s baseline drifted %.2f pp from frozen May 1 error %.4f%% to %.4f%%",
							c.validationCase.rocketName, drift, frozen, baseline.errorPct));
		}

		for (AblationCase c : cases) {
			SimResult baseline = baselineByRocket.get(c.validationCase.rocketName);
			if (NOZZLE_ABLATION_CASES.contains(c.validationCase.rocketName)) {
				rows.add(new AblationRow(c, Mutation.NO_NOZZLES, baseline, runCase(c, Mutation.NO_NOZZLES)));
			}
			if (TURBULENCE_ABLATION_CASES.contains(c.validationCase.rocketName)) {
				rows.add(new AblationRow(c, Mutation.FORCE_TURBULENT_OFF, baseline,
						runCase(c, Mutation.FORCE_TURBULENT_OFF)));
			}
		}

		writeArtifacts(rows);

		double maxTurbulenceDelta = rows.stream()
				.filter(r -> r.mutation == Mutation.FORCE_TURBULENT_OFF)
				.mapToDouble(r -> Math.abs(r.deltaErrPp()))
				.max().orElse(0.0);
		assertTrue(maxTurbulenceDelta <= 0.05,
				String.format(Locale.US,
						"Force-turbulent-BL off should remain a bounded no-op for non-perfect-finish "
								+ "SimVReal imports; max delta was %.3f pp",
						maxTurbulenceDelta));

		double maxNozzleDelta = rows.stream()
				.filter(r -> r.mutation == Mutation.NO_NOZZLES)
				.mapToDouble(r -> Math.abs(r.deltaErrPp()))
				.max().orElse(0.0);
		assertTrue(maxNozzleDelta >= 0.5,
				String.format(Locale.US,
						"No-nozzle ablation should measure a live mechanism; max delta was %.3f pp",
						maxNozzleDelta));
	}

	private static List<AblationCase> selectedCases(File simvrealDir) {
		List<AblationCase> out = new ArrayList<>();
		for (SimVRealBenchmarkTest.ValidationCase vc : SimVRealBenchmarkTest.getValidationCases()) {
			if (NOZZLE_ABLATION_CASES.contains(vc.rocketName)
					|| TURBULENCE_ABLATION_CASES.contains(vc.rocketName)) {
				out.add(new AblationCase(vc, new File(simvrealDir, vc.cdx1File), 180_000));
			}
		}
		return out;
	}

	private static SimResult runCase(AblationCase c, Mutation mutation) throws Exception {
		assertTrue(c.cdx1File.exists(), "CDX1 file not found: " + c.cdx1File.getAbsolutePath());

		GeneralRocketLoader loader = new GeneralRocketLoader(c.cdx1File);
		OpenRocketDocument doc = loader.load();
		assertFalse(doc.getSimulations().isEmpty(), "No simulations in " + c.cdx1File.getName());

		Simulation sim = doc.getSimulations().get(0);
		sim.getOptions().setTimeStep(0.05);
		sim.getOptions().setMaximumStepAngle(Math.toRadians(3));
		sim.getOptions().setMaxSimulationTime(2400);
		sim.getOptions().setRandomSeed(SimVRealBenchmarkTest.BENCHMARK_RANDOM_SEED);

		if (mutation == Mutation.NO_NOZZLES) {
			for (int stage = 0; stage <= 2; stage++) {
				sim.getOptions().setNozzleExitDiameterForStage(stage, Double.NaN);
			}
		} else if (mutation == Mutation.FORCE_TURBULENT_OFF) {
			sim.getOptions().setForceTurbulentBL(false);
		}

		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread simThread = new Thread(() -> {
			try {
				sim.simulate();
			} catch (Throwable t) {
				thrown.set(t);
			}
		}, "simvreal-ablation-" + c.validationCase.rocketName + "-" + mutation.id);
		simThread.start();
		simThread.join(c.timeoutMs);
		if (simThread.isAlive()) {
			simThread.interrupt();
			throw new AssertionError(c.validationCase.rocketName + " " + mutation.id
					+ " timed out after " + c.timeoutMs + " ms");
		}
		if (thrown.get() != null) {
			throw new AssertionError(c.validationCase.rocketName + " " + mutation.id
					+ " failed", thrown.get());
		}

		FlightData data = sim.getSimulatedData();
		assertNotNull(data, "No flight data for " + c.validationCase.rocketName);
		assertTrue(data.getBranchCount() > 0, "No flight branches for " + c.validationCase.rocketName);

		double apogeeFt = data.getMaxAltitude() * FT_PER_METER;
		double errorPct = 100.0 * (apogeeFt - c.validationCase.realAltitudeFt)
				/ c.validationCase.realAltitudeFt;
		String terminal = describeTerminalState(sim, data);
		assertTrue(terminal.isEmpty(),
				c.validationCase.rocketName + " " + mutation.id + " ended abnormally: " + terminal);
		return new SimResult(apogeeFt, errorPct, data.getMaxMachNumber(), terminal.isEmpty() ? "NORMAL" : terminal);
	}

	private static String describeTerminalState(Simulation sim, FlightData data) {
		FlightEvent abortEvent = findFirstEvent(data, FlightEvent.Type.SIM_ABORT);
		if (abortEvent != null && abortEvent.getData() instanceof SimulationAbort abort) {
			return String.format(Locale.US, "ABORT:%s@%.1fs", abort.getCause().name(), abortEvent.getTime());
		}

		double maxTime = sim.getOptions().getMaxSimulationTime();
		double tolerance = Math.max(1.0e-6, sim.getOptions().getTimeStep());
		for (int branchIndex = 0; branchIndex < data.getBranchCount(); branchIndex++) {
			FlightEvent simulationEnd = data.getBranch(branchIndex).getLastEvent(FlightEvent.Type.SIMULATION_END);
			if (simulationEnd == null) continue;
			boolean groundHit = data.getBranch(branchIndex).getLastEvent(FlightEvent.Type.GROUND_HIT) != null;
			if (!groundHit && simulationEnd.getTime() >= maxTime - tolerance) {
				return String.format(Locale.US, "MAXTIME@%.0fs", simulationEnd.getTime());
			}
		}
		return "";
	}

	private static FlightEvent findFirstEvent(FlightData data, FlightEvent.Type type) {
		for (int branchIndex = 0; branchIndex < data.getBranchCount(); branchIndex++) {
			for (FlightEvent event : data.getBranch(branchIndex).getEvents()) {
				if (event.getType() == type) {
					return event;
				}
			}
		}
		return null;
	}

	private static void writeArtifacts(List<AblationRow> rows) throws Exception {
		File outDir = new File(REPORT_DIR);
		assertTrue(outDir.exists() || outDir.mkdirs(), "Could not create " + outDir.getAbsolutePath());

		File csv = new File(outDir, "simvreal_corpus_ablation_2026_05_01.csv");
		try (BufferedWriter w = new BufferedWriter(new FileWriter(csv))) {
			w.write("mutation,rocket,cdx1,real_ft,rasaero_ft,baseline_orp_ft,mutated_orp_ft,"
					+ "baseline_err_pct,mutated_err_pct,delta_err_pp,delta_ft,baseline_mach,"
					+ "mutated_mach,terminal,notes\n");
			for (AblationRow row : rows) {
				w.write(row.toCsvRow());
				w.newLine();
			}
		}

		File md = new File(outDir, "simvreal_corpus_ablation_2026_05_01.md");
		try (BufferedWriter w = new BufferedWriter(new FileWriter(md))) {
			w.write("# SimVReal Corpus Ablation - 2026-05-01\n\n");
			w.write("Bounded AST publication ablation. Baseline rows rerun the hardest 24-flight SimVReal cases; "
					+ "mutation rows isolate imported nozzle pressure-thrust and force-turbulent-BL effects.\n\n");
			w.write("MESOS 293K is handled by `SimVRealValidationTest.testMesos293K` because that case has "
					+ "custom two-stage motor loading and a dedicated staging event report.\n\n");
			w.write("| Mutation | Rocket | Baseline err | Mutated err | Delta pp | Delta ft | Max Mach | Notes |\n");
			w.write("|---|---:|---:|---:|---:|---:|---:|---|\n");
			for (AblationRow row : rows) {
				w.write(String.format(Locale.US,
						"| %s | %s | %+.2f%% | %+.2f%% | %+.2f | %+.0f | %.2f | %s |%n",
						row.mutation.id, row.c.validationCase.rocketName,
						row.baseline.errorPct, row.mutated.errorPct,
						row.deltaErrPp(), row.deltaFt(), row.mutated.maxMach, row.mutation.notes));
			}
			w.write("\nCSV: `simvreal_corpus_ablation_2026_05_01.csv`\n");
		}

		System.out.println("Ablation CSV: " + csv.getAbsolutePath());
		System.out.println("Ablation report: " + md.getAbsolutePath());
	}

	private static File findDir(String relativePath) {
		File dir = new File(relativePath);
		if (dir.exists()) return dir;

		Path current = Paths.get(System.getProperty("user.dir"));
		for (int i = 0; i < 6 && current != null; i++) {
			File candidate = current.resolve(relativePath).toFile();
			if (candidate.exists()) return candidate;
			current = current.getParent();
		}
		return null;
	}

	private enum Mutation {
		BASELINE("baseline_current", "current production model"),
		NO_NOZZLES("no_nozzle_pressure_thrust", "stage nozzle diameters cleared"),
		FORCE_TURBULENT_OFF("force_turbulent_bl_off", "RASAero Turbulence=True disabled");

		final String id;
		final String notes;

		Mutation(String id, String notes) {
			this.id = id;
			this.notes = notes;
		}
	}

	private static final class AblationCase {
		final SimVRealBenchmarkTest.ValidationCase validationCase;
		final File cdx1File;
		final long timeoutMs;

		AblationCase(SimVRealBenchmarkTest.ValidationCase validationCase, File cdx1File, long timeoutMs) {
			this.validationCase = validationCase;
			this.cdx1File = cdx1File;
			this.timeoutMs = timeoutMs;
		}
	}

	private static final class SimResult {
		final double apogeeFt;
		final double errorPct;
		final double maxMach;
		final String terminal;

		SimResult(double apogeeFt, double errorPct, double maxMach, String terminal) {
			this.apogeeFt = apogeeFt;
			this.errorPct = errorPct;
			this.maxMach = maxMach;
			this.terminal = terminal;
		}
	}

	private static final class AblationRow {
		final AblationCase c;
		final Mutation mutation;
		final SimResult baseline;
		final SimResult mutated;

		AblationRow(AblationCase c, Mutation mutation, SimResult baseline, SimResult mutated) {
			this.c = c;
			this.mutation = mutation;
			this.baseline = baseline;
			this.mutated = mutated;
		}

		double deltaErrPp() {
			return mutated.errorPct - baseline.errorPct;
		}

		double deltaFt() {
			return mutated.apogeeFt - baseline.apogeeFt;
		}

		String toCsvRow() {
			SimVRealBenchmarkTest.ValidationCase vc = c.validationCase;
			return String.format(Locale.US,
					"\"%s\",\"%s\",\"%s\",%.0f,%.0f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f,%.4f,%.4f,\"%s\",\"%s\"",
					mutation.id,
					vc.rocketName,
					vc.cdx1File,
					vc.realAltitudeFt,
					vc.rasAeroAltitudeFt,
					baseline.apogeeFt,
					mutated.apogeeFt,
					baseline.errorPct,
					mutated.errorPct,
					deltaErrPp(),
					deltaFt(),
					baseline.maxMach,
					mutated.maxMach,
					mutated.terminal,
					mutation.notes);
		}
	}
}
