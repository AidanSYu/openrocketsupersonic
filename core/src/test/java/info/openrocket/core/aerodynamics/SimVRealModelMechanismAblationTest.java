package info.openrocket.core.aerodynamics;

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
import info.openrocket.core.logging.SimulationAbort;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.util.BaseTestCase;

/**
 * AST 2026-05-02 model-mechanism ablation on the full 25-flight SimVReal corpus.
 * <p>
 * For each toggle in {@link AblationConfig}, runs the full corpus and writes
 * a CSV row plus an MD report. The aggregate-level table answers the AST
 * causal-evidence question: "removing X costs Y%."
 * <p>
 * Each mutation is run sequentially (one-at-a-time). The toggles are reset
 * between runs in {@link #runMutation}. The simulator runs in its own thread
 * with a generous timeout so a divergent mutation can be reported as an
 * abnormal ending without hanging the test.
 * <p>
 * The companion {@link SimVRealCorpusAblationTest} covers import-parity flags
 * (nozzle pressure-thrust, force-turbulent-BL) on the high-M subset; this
 * test is the model-mechanism complement on the full corpus.
 */
@Isolated("AblationConfig static volatile flags must not be observed by concurrent tests")
@ResourceLock("AERO_CPU_HEAVY")
@Tag("sweep")  // paper-data regeneration (model-mechanism ablation, ~9.6 min); excluded by default, run with -Psweeps
public class SimVRealModelMechanismAblationTest extends BaseTestCase {

	private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";
	private static final double FT_PER_METER = 3.28084;
	private static final long PER_CASE_TIMEOUT_MS = 240_000;

	private static final String RASP_ENG_PATH = "simvreal/rasp.eng";

	@BeforeAll
	static void setup() {
		AblationConfig.reset();
		// Clear the JVM-global RASAero motor cache so this class binds only its own
		// rasp.eng curves regardless of which test class ran before it.
		RASAeroMotorsLoader.clearAllMotors();
		// Resolve the RASP motor file via the same repo-root walk-up used for the
		// CDX1 dir; a plain cwd-relative path fails under Gradle (cwd=core/) and
		// silently leaves the motor cache empty -> every flight aborts (24 abnormal).
		File rasp = findDir(RASP_ENG_PATH);
		if (rasp != null) {
			loadMotorsIntoRASAeroCache(rasp, "rasp.eng");
		} else {
			System.out.println("WARNING: " + RASP_ENG_PATH + " not found; SimVReal motors will not bind.");
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
			for (ThrustCurveMotor.Builder builder : builders) {
				try {
					RASAeroMotorsLoader.addMotorToCache(builder.build());
				} catch (Exception ignored) {
					// Duplicate aliases — keep going.
				}
			}
		} catch (Exception e) {
			System.out.println("Could not preload " + file.getPath() + ": " + e.getMessage());
		}
	}

	@Test
	void testWriteModelMechanismAblation() throws Exception {
		File simvrealDir = findDir(SIMVREAL_DIR);
		if (simvrealDir == null) {
			System.out.println("SKIP: SimVReal directory not found.");
			return;
		}

		List<SimVRealBenchmarkTest.ValidationCase> allCases = SimVRealBenchmarkTest.getValidationCases();

		// Filter to cases that actually have CDX1 files present.
		List<SimVRealBenchmarkTest.ValidationCase> available = new ArrayList<>();
		for (SimVRealBenchmarkTest.ValidationCase vc : allCases) {
			File cdx1 = new File(simvrealDir, vc.cdx1File);
			if (cdx1.exists()) available.add(vc);
		}
		assertTrue(available.size() >= 20,
				"Need >= 20 SimVReal cases for the model-mechanism corpus; found " + available.size());

		// Run baseline first.
		AblationConfig.reset();
		Map<String, SimResult> baselineByRocket = new LinkedHashMap<>();
		List<Row> rows = new ArrayList<>();
		for (SimVRealBenchmarkTest.ValidationCase vc : available) {
			SimResult r = runCase(simvrealDir, vc);
			baselineByRocket.put(vc.rocketName, r);
			rows.add(new Row(Mutation.BASELINE, vc, r, r));
		}

		// Then each mutation in turn.
		for (Mutation m : Mutation.values()) {
			if (m == Mutation.BASELINE) continue;
			AblationConfig.reset();
			m.apply();
			try {
				for (SimVRealBenchmarkTest.ValidationCase vc : available) {
					SimResult mutated = runCase(simvrealDir, vc);
					rows.add(new Row(m, vc, baselineByRocket.get(vc.rocketName), mutated));
				}
			} finally {
				AblationConfig.reset();
			}
		}

		// Aggregate per-mutation summary and write artifacts.
		Map<Mutation, Aggregate> aggregates = new LinkedHashMap<>();
		for (Mutation m : Mutation.values()) {
			aggregates.put(m, computeAggregate(rows, m));
		}
		writeArtifacts(rows, aggregates);

		// Validation gate 1: baseline must be sensible.
		Aggregate baseline = aggregates.get(Mutation.BASELINE);
		assertTrue(baseline.avgAbsErr <= 6.0,
				"Baseline avg |err| drift: " + baseline.avgAbsErr + "% (May 1 frozen 4.65%)");
		assertTrue(baseline.abnormalCount == 0,
				"Baseline must have zero abnormal endings; got " + baseline.abnormalCount);

		// Validation gate 2: drag-affecting mutations must produce a measurable
		// apogee delta (>= 0.05 pp) on at least one case; stability-only
		// mutations (e.g., NO_PNK, NO_K1_FLOOR) legitimately produce zero
		// apogee delta because they alter CN/CP without altering CD. The
		// stability-only effect is reported in the CSV but not asserted here
		// because the test fixture only captures apogee, not CN/CP traces.
		for (Mutation m : Mutation.values()) {
			if (m == Mutation.BASELINE) continue;
			if (!m.expectsApogeeDelta) continue;
			double maxDeltaPp = rows.stream()
					.filter(r -> r.mutation == m)
					.mapToDouble(r -> Math.abs(r.deltaPp()))
					.max().orElse(0.0);
			assertTrue(maxDeltaPp >= 0.05,
					String.format(Locale.US,
							"Mutation %s (drag-affecting) produced no measurable apogee delta (max %.4f pp). "
									+ "Toggle infrastructure may be masked or ineffective.",
							m.id, maxDeltaPp));
		}
	}

	private static SimResult runCase(File simvrealDir, SimVRealBenchmarkTest.ValidationCase vc) throws Exception {
		File cdx1 = new File(simvrealDir, vc.cdx1File);

		GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
		OpenRocketDocument doc = loader.load();
		if (doc.getSimulations().isEmpty()) {
			return SimResult.abnormal(0, 0, "NO_SIM");
		}

		Simulation sim = doc.getSimulations().get(0);
		sim.getOptions().setTimeStep(0.05);
		sim.getOptions().setMaximumStepAngle(Math.toRadians(3));
		sim.getOptions().setMaxSimulationTime(2400);
		sim.getOptions().setRandomSeed(SimVRealBenchmarkTest.BENCHMARK_RANDOM_SEED);

		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread simThread = new Thread(() -> {
			try {
				sim.simulate();
			} catch (Throwable t) {
				thrown.set(t);
			}
		}, "ablation-" + vc.rocketName);
		simThread.start();
		simThread.join(PER_CASE_TIMEOUT_MS);
		if (simThread.isAlive()) {
			simThread.interrupt();
			return SimResult.abnormal(0, 0, "TIMEOUT");
		}
		if (thrown.get() != null) {
			String msg = thrown.get().getClass().getSimpleName();
			return SimResult.abnormal(0, 0, "EXCEPTION:" + msg);
		}

		FlightData data = sim.getSimulatedData();
		assertNotNull(data, "No flight data for " + vc.rocketName);
		if (data.getBranchCount() == 0 || !Double.isFinite(data.getMaxAltitude())
				|| data.getMaxAltitude() < 1.0) {
			return SimResult.abnormal(data == null ? 0 : data.getMaxAltitude() * FT_PER_METER,
					data == null ? 0 : data.getMaxMachNumber(), "NO_FLIGHT");
		}

		double apogeeFt = data.getMaxAltitude() * FT_PER_METER;
		double errPct = 100.0 * (apogeeFt - vc.realAltitudeFt) / vc.realAltitudeFt;
		double maxMach = data.getMaxMachNumber();
		String terminal = describeTerminalState(sim, data);
		if (!terminal.isEmpty()) {
			return new SimResult(apogeeFt, errPct, maxMach, terminal, true);
		}
		return new SimResult(apogeeFt, errPct, maxMach, "NORMAL", false);
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

	private static Aggregate computeAggregate(List<Row> rows, Mutation mutation) {
		Aggregate a = new Aggregate();
		List<Double> absErrs = new ArrayList<>();
		List<Double> deltas = new ArrayList<>();
		for (Row r : rows) {
			if (r.mutation != mutation) continue;
			a.totalCases++;
			if (r.mutated.abnormal) {
				a.abnormalCount++;
				continue;
			}
			absErrs.add(Math.abs(r.mutated.errorPct));
			if (Math.abs(r.mutated.errorPct) <= 5.0) a.within5++;
			if (Math.abs(r.mutated.errorPct) <= 10.0) a.within10++;
			if (mutation != Mutation.BASELINE) {
				deltas.add(r.deltaPp());
			}
		}
		a.avgAbsErr = absErrs.isEmpty() ? 0
				: absErrs.stream().mapToDouble(d -> d).average().orElse(0);
		a.avgSignedDeltaPp = deltas.isEmpty() ? 0
				: deltas.stream().mapToDouble(d -> d).average().orElse(0);
		a.avgAbsDeltaPp = deltas.isEmpty() ? 0
				: deltas.stream().mapToDouble(Math::abs).average().orElse(0);
		a.maxAbsDeltaPp = deltas.isEmpty() ? 0
				: deltas.stream().mapToDouble(Math::abs).max().orElse(0);
		return a;
	}

	private static void writeArtifacts(List<Row> rows, Map<Mutation, Aggregate> aggregates) throws Exception {
		File reportRoot = findReportsRoot();
		File csvDir = new File(reportRoot, "paper/data/csv");
		File mdDir = new File(reportRoot, "paper/data/md");
		assertTrue(csvDir.exists() || csvDir.mkdirs(), "could not create " + csvDir);
		assertTrue(mdDir.exists() || mdDir.mkdirs(), "could not create " + mdDir);

		File csv = new File(csvDir, "simvreal_model_mechanism_ablation_2026_05_02.csv");
		try (BufferedWriter w = new BufferedWriter(new FileWriter(csv))) {
			w.write("mutation,rocket,cdx1,real_ft,rasaero_ft,baseline_orp_ft,mutated_orp_ft,"
					+ "baseline_err_pct,mutated_err_pct,delta_err_pp,delta_ft,baseline_mach,"
					+ "mutated_mach,baseline_terminal,mutated_terminal,notes\n");
			for (Row r : rows) w.write(r.toCsvRow() + "\n");
		}

		// Build human-readable MD.
		File md = new File(mdDir, "simvreal_model_mechanism_ablation_2026_05_02.md");
		try (BufferedWriter w = new BufferedWriter(new FileWriter(md))) {
			w.write("# SimVReal Model-Mechanism Ablation - 2026-05-02\n\n");
			w.write("Source test: `info.openrocket.core.aerodynamics.SimVRealModelMechanismAblationTest`.\n\n");
			w.write("Machine-readable CSV: `paper/data/csv/simvreal_model_mechanism_ablation_2026_05_02.csv`.\n\n");
			w.write("This is the AST manuscript's model-mechanism (causal-evidence) ablation. ");
			w.write("Each mutation independently disables one of the OpenRocket Plus supersonic ");
			w.write("calculator additions and replays the full 24-flight SimVReal corpus. The ");
			w.write("delta column converts engineering claims (\"this mechanism contributes\") ");
			w.write("into corpus-level evidence (\"removing X costs Y pp of average error\").\n\n");

			w.write("## Aggregate per mutation\n\n");
			w.write("| Mutation | Mechanism | Avg \\|err\\| | Within +/-5% | Within +/-10% | Abnormal | Avg signed delta pp | Max \\|delta\\| pp |\n");
			w.write("|---|---|---:|---:|---:|---:|---:|---:|\n");
			for (Mutation m : Mutation.values()) {
				Aggregate a = aggregates.get(m);
				int total = Math.max(a.totalCases - a.abnormalCount, 1);
				w.write(String.format(Locale.US,
						"| %s | %s | %.2f%% | %.1f%% | %.1f%% | %d | %+.3f | %.3f |%n",
						m.id, m.shortDesc, a.avgAbsErr,
						100.0 * a.within5 / total,
						100.0 * a.within10 / total,
						a.abnormalCount, a.avgSignedDeltaPp, a.maxAbsDeltaPp));
			}

			w.write("\n## Per-case delta (mutated minus baseline)\n\n");
			w.write("| Mutation | Rocket | Baseline err | Mutated err | Delta pp | Delta ft | Baseline Mach | Mutated Mach | Mutated terminal |\n");
			w.write("|---|---|---:|---:|---:|---:|---:|---:|---|\n");
			for (Row r : rows) {
				if (r.mutation == Mutation.BASELINE) continue;
				w.write(String.format(Locale.US,
						"| %s | %s | %+.2f%% | %+.2f%% | %+.2f | %+.0f | %.2f | %.2f | %s |%n",
						r.mutation.id, r.vc.rocketName,
						r.baseline.errorPct, r.mutated.errorPct,
						r.deltaPp(), r.deltaFt(),
						r.baseline.maxMach, r.mutated.maxMach,
						r.mutated.terminal));
			}

			w.write("\n## Interpretation\n\n");
			w.write("Each mutation reverts a single calculator mechanism to its pre-supersonic-uplift baseline:\n\n");
			for (Mutation m : Mutation.values()) {
				if (m == Mutation.BASELINE) continue;
				Aggregate a = aggregates.get(m);
				w.write(String.format(Locale.US,
						"- **%s** (%s): avg |err| changes from %.2f%% to %.2f%% (signed delta %+.2f pp, max |delta| %.2f pp, abnormal %d/%d).\n",
						m.id, m.shortDesc,
						aggregates.get(Mutation.BASELINE).avgAbsErr, a.avgAbsErr,
						a.avgSignedDeltaPp, a.maxAbsDeltaPp,
						a.abnormalCount, a.totalCases));
			}
			w.write("\n");
			w.write("Mutations with negligible aggregate delta are not regressions — they indicate the mechanism ");
			w.write("is dormant on this corpus (e.g., DATCOM fin wave drag is small compared to body wave drag, ");
			w.write("or the K1 floor only fires on the few low-AR swept fins). The per-case delta table identifies ");
			w.write("the rockets where each mechanism is load-bearing.\n");
		}

		System.out.println("Model-mechanism ablation CSV: " + csv.getAbsolutePath());
		System.out.println("Model-mechanism ablation MD:  " + md.getAbsolutePath());
	}

	private static File findReportsRoot() {
		// Walk up to find the directory that has paper/data/csv (worktree root).
		Path current = Paths.get(System.getProperty("user.dir"));
		for (int i = 0; i < 6 && current != null; i++) {
			File candidate = current.resolve("paper/data/csv").toFile();
			if (candidate.exists()) return current.toFile();
			current = current.getParent();
		}
		// Fallback to working dir.
		return new File(System.getProperty("user.dir"));
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
		// expectsApogeeDelta: true for drag-affecting mutations, false for
		// stability-only (CN/CP) mutations that legitimately produce zero
		// apogee delta because they don't change CD. Used by the wiring-check
		// assertion in testWriteModelMechanismAblation to distinguish a dead
		// toggle from a stability-only mechanism.
		BASELINE("baseline_current", "current production model",
				() -> { /* no-op */ }, false),
		NO_SHOCK_GEOMETRY("no_shockgeometry",
				"ShockGeometry pre-pass disabled",
				() -> AblationConfig.disableShockGeometry = true, true),
		NO_PNK("no_pnk",
				"Pitts-Nielsen-Kaattari interference disabled (F_WB=F_BW=1)",
				() -> AblationConfig.disablePNK = true, false),
		NO_VAN_DRIEST_II("no_van_driest_ii",
				"Van Driest II skin friction disabled (incompressible Cf)",
				() -> AblationConfig.disableVanDriestII = true, true),
		NO_K1_FLOOR("no_k1_floor",
				"Mach-blended K1 floor disabled (no fin CNa floor)",
				() -> AblationConfig.disableK1Floor = true, false),
		NO_FINNED_BASE("no_finned_base_aug",
				"Finned-body base augmentation disabled",
				() -> AblationConfig.disableFinnedBaseCore = true, true),
		NO_DATCOM_FIN_WAVE("no_datcom_fin_wave_drag",
				"DATCOM 4.1.5.1 fin wave drag disabled",
				() -> AblationConfig.disableDatcomFinWaveDrag = true, true);

		final String id;
		final String shortDesc;
		final Runnable applyFn;
		final boolean expectsApogeeDelta;

		Mutation(String id, String shortDesc, Runnable applyFn, boolean expectsApogeeDelta) {
			this.id = id;
			this.shortDesc = shortDesc;
			this.applyFn = applyFn;
			this.expectsApogeeDelta = expectsApogeeDelta;
		}

		void apply() {
			applyFn.run();
		}
	}

	private static final class SimResult {
		final double apogeeFt;
		final double errorPct;
		final double maxMach;
		final String terminal;
		final boolean abnormal;

		SimResult(double apogeeFt, double errorPct, double maxMach, String terminal, boolean abnormal) {
			this.apogeeFt = apogeeFt;
			this.errorPct = errorPct;
			this.maxMach = maxMach;
			this.terminal = terminal;
			this.abnormal = abnormal;
		}

		static SimResult abnormal(double apogeeFt, double maxMach, String terminal) {
			return new SimResult(apogeeFt, Double.NaN, maxMach, terminal, true);
		}
	}

	private static final class Row {
		final Mutation mutation;
		final SimVRealBenchmarkTest.ValidationCase vc;
		final SimResult baseline;
		final SimResult mutated;

		Row(Mutation mutation, SimVRealBenchmarkTest.ValidationCase vc,
				SimResult baseline, SimResult mutated) {
			this.mutation = mutation;
			this.vc = vc;
			this.baseline = baseline;
			this.mutated = mutated;
		}

		double deltaPp() {
			if (Double.isNaN(mutated.errorPct) || Double.isNaN(baseline.errorPct)) return 0.0;
			return mutated.errorPct - baseline.errorPct;
		}

		double deltaFt() {
			return mutated.apogeeFt - baseline.apogeeFt;
		}

		String toCsvRow() {
			return String.format(Locale.US,
					"\"%s\",\"%s\",\"%s\",%.0f,%.0f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f,%.4f,%.4f,\"%s\",\"%s\",\"%s\"",
					mutation.id,
					vc.rocketName,
					vc.cdx1File,
					vc.realAltitudeFt,
					vc.rasAeroAltitudeFt,
					baseline.apogeeFt,
					mutated.apogeeFt,
					baseline.errorPct,
					mutated.errorPct,
					deltaPp(),
					deltaFt(),
					baseline.maxMach,
					mutated.maxMach,
					baseline.terminal,
					mutated.terminal,
					mutation.shortDesc);
		}
	}

	private static final class Aggregate {
		int totalCases = 0;
		int within5 = 0;
		int within10 = 0;
		int abnormalCount = 0;
		double avgAbsErr = 0.0;
		double avgSignedDeltaPp = 0.0;
		double avgAbsDeltaPp = 0.0;
		double maxAbsDeltaPp = 0.0;
	}
}
