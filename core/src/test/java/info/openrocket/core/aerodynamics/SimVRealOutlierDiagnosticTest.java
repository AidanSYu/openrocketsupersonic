package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.SimulationAbort;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.masscalc.RigidBody;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.PodSet;
import info.openrocket.core.rocketcomponent.RailButton;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.util.BaseTestCase;

@ResourceLock("AERO_CPU_HEAVY")
public class SimVRealOutlierDiagnosticTest extends BaseTestCase {

	private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";
	private static final String REPORT_DIR = "build/reports/simvreal-outliers";
	private static final double FT_PER_METER = 3.28084;
	private static final double IN_PER_METER = 39.37007874015748;
	private static final double LB_PER_KG = 2.20462262185;

	private static final String[] TARGET_CASES = {
			// Current 4 outliers (>10% error)
			"EZI-65 J450ST",
			"Thunder & Lightning",
			"Raven",
			"A-601 Kinsel",
			// Healthy comparison cases (<5% error)
			"Byrum",
			"Caliber Isp 04 Team 3",
	};

	private static final String OUTLIER_CSV_HEADER =
			"rocket,cdx1,real_ft,rasaero_ft,orp_ft,orp_err_pct,delta_vs_rasaero_pct,max_mach,flight_time_s,"
					+ "branch_count,primary_branch,burnout_time_s,apogee_time_s,ground_hit_time_s,terminal_note,"
					+ "loader_warning_count,sim_warning_count,report_md,trajectory_csv,component_cd_csv\n";

	private static final String FULL_CORPUS_CSV_HEADER =
			"rocket,cdx1,real_ft,rasaero_ft,orp_ft,orp_err_pct,delta_vs_rasaero_pct,max_mach,"
					+ "burnout_time_s,apogee_time_s,ground_hit_time_s,terminal_note,"
					+ "loader_warning_count,sim_warning_count,ignored_setting_flags,"
					+ "report_md,trajectory_csv,component_cd_csv\n";

	private static final String IGNORED_SETTING_PREFIX = "Ignoring unsupported RASAero setting ";

	@BeforeAll
	static void setup() {
		SimVRealBenchmarkTest.setup();
	}

	@Test
	void testGenerateAstOutlierDiagnostics() throws Exception {
		File simvrealDir = findSimVRealDir();
		if (simvrealDir == null) {
			System.out.println("SKIP: SimVReal directory not found. Set working directory to project root.");
			return;
		}

		File outDir = new File(REPORT_DIR);
		outDir.mkdirs();

		List<SimVRealBenchmarkTest.ValidationCase> targetCases = new ArrayList<>();
		for (SimVRealBenchmarkTest.ValidationCase vc : SimVRealBenchmarkTest.getValidationCases()) {
			if (isTargetCase(vc.rocketName)) {
				targetCases.add(vc);
			}
		}
		assertFalse(targetCases.isEmpty(), "No target SimVReal outlier cases found");

		List<String> failures = new ArrayList<>();
		StringBuilder summary = new StringBuilder();
		summary.append(OUTLIER_CSV_HEADER);

		for (SimVRealBenchmarkTest.ValidationCase vc : targetCases) {
			try {
				DiagnosticArtifacts artifacts = generateArtifacts(vc, simvrealDir, outDir);
				summary.append(artifacts.toCsvRow()).append('\n');
				System.out.printf(Locale.US,
						"  %-28s ORP=%8.0f ft err=%+6.1f%% M%.2f -> %s%n",
						vc.rocketName,
						artifacts.orpApogeeFt,
						artifacts.orpErrorPct,
						artifacts.maxMach,
						artifacts.markdownReport.getName());
			} catch (Exception e) {
				failures.add(vc.rocketName + ": " + e.getMessage());
			}
		}

		File summaryFile = new File(outDir, "simvreal-outlier-summary.csv");
		try (BufferedWriter w = new BufferedWriter(new FileWriter(summaryFile))) {
			w.write(summary.toString());
		}

		assertTrue(failures.isEmpty(), "Failed to generate diagnostics:\n" + String.join("\n", failures));
	}

	/**
	 * Generate reproducible diagnostic artifacts for EVERY SimVReal validation case.
	 * Produces per-case markdown + trajectory CSV + component-CD sweep, plus a
	 * machine-readable full-corpus summary CSV with ignored-setting flags.
	 *
	 * The existing worst-case focused export above is preserved unchanged.
	 */
	@Test
	void testGenerateFullCorpusDiagnostics() throws Exception {
		File simvrealDir = findSimVRealDir();
		if (simvrealDir == null) {
			System.out.println("SKIP: SimVReal directory not found. Set working directory to project root.");
			return;
		}

		File outDir = new File(REPORT_DIR);
		outDir.mkdirs();

		List<SimVRealBenchmarkTest.ValidationCase> allCases = SimVRealBenchmarkTest.getValidationCases();
		assertFalse(allCases.isEmpty(), "No SimVReal validation cases found");

		List<String> failures = new ArrayList<>();
		StringBuilder summary = new StringBuilder();
		summary.append(FULL_CORPUS_CSV_HEADER);

		int successCount = 0;
		for (SimVRealBenchmarkTest.ValidationCase vc : allCases) {
			File cdx1 = new File(simvrealDir, vc.cdx1File);
			if (!cdx1.exists()) {
				failures.add(vc.rocketName + ": CDX1 file not found: " + cdx1.getAbsolutePath());
				continue;
			}
			try {
				DiagnosticArtifacts artifacts = generateArtifacts(vc, simvrealDir, outDir);
				summary.append(artifacts.toFullCorpusCsvRow()).append('\n');
				successCount++;
				System.out.printf(Locale.US,
						"  [%2d/%-2d] %-28s ORP=%8.0f ft err=%+6.1f%% M%.2f flags=%s -> %s%n",
						successCount,
						allCases.size(),
						vc.rocketName,
						artifacts.orpApogeeFt,
						artifacts.orpErrorPct,
						artifacts.maxMach,
						artifacts.ignoredSettingFlags,
						artifacts.markdownReport.getName());
			} catch (Exception e) {
				failures.add(vc.rocketName + ": " + e.getMessage());
				System.out.printf(Locale.US, "  FAIL    %-28s %s%n", vc.rocketName, e.getMessage());
			}
		}

		File summaryFile = new File(outDir, "simvreal-full-corpus-summary.csv");
		try (BufferedWriter w = new BufferedWriter(new FileWriter(summaryFile))) {
			w.write(summary.toString());
		}

		System.out.printf(Locale.US, "%nFull corpus diagnostics: %d/%d succeeded, %d failed%n",
				successCount, allCases.size(), failures.size());
		if (!failures.isEmpty()) {
			System.out.println("Failures:");
			for (String f : failures) {
				System.out.println("  - " + f);
			}
		}

		assertTrue(successCount > 0, "No diagnostics generated at all");
		// Allow partial failures (missing CDX1 files) but warn
		if (!failures.isEmpty()) {
			System.out.println("WARNING: " + failures.size() + " cases failed to export (see above).");
		}
	}

	private static DiagnosticArtifacts generateArtifacts(SimVRealBenchmarkTest.ValidationCase vc,
			File simvrealDir, File outDir) throws Exception {
		File cdx1 = new File(simvrealDir, vc.cdx1File);
		assertTrue(cdx1.exists(), "CDX1 file not found: " + cdx1.getAbsolutePath());

		GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
		OpenRocketDocument doc = loader.load();
		WarningSet loadWarnings = loader.getWarnings().clone();

		List<Simulation> sims = doc.getSimulations();
		assertFalse(sims.isEmpty(), "No simulations found in " + vc.cdx1File);

		Simulation sim = sims.get(0);
		sim.getOptions().setTimeStep(0.05);
		sim.getOptions().setMaximumStepAngle(Math.toRadians(3));
		runSimulationWithTimeout(sim, 120_000, vc.rocketName);

		FlightData data = sim.getSimulatedData();
		assertNotNull(data, "No flight data produced for " + vc.rocketName);
		assertTrue(data.getBranchCount() > 0, "No flight branches for " + vc.rocketName);

		Rocket rocket = doc.getRocket();
		WarningSet simWarnings = sim.getSimulatedWarnings() != null ? sim.getSimulatedWarnings().clone() : new WarningSet();
		int primaryBranchIndex = findPrimaryBranchIndex(data);
		FlightDataBranch primaryBranch = data.getBranch(primaryBranchIndex);

		double orpApogeeFt = data.getMaxAltitude() * FT_PER_METER;
		double orpErrorPct = 100.0 * (orpApogeeFt - vc.realAltitudeFt) / vc.realAltitudeFt;
		double deltaVsRasPct = 100.0 * (orpApogeeFt - vc.rasAeroAltitudeFt) / vc.rasAeroAltitudeFt;
		double maxMach = data.getMaxMachNumber();
		String terminalNote = describeTerminalState(sim, data);

		double burnoutTime = resolveBurnoutTime(primaryBranch);
		double apogeeTime = resolveApogeeTime(primaryBranch);
		double groundHitTime = resolveGroundHitTime(primaryBranch);
		int burnoutIndex = resolveBurnoutIndex(primaryBranch, burnoutTime);
		int apogeeIndex = resolveApogeeIndex(primaryBranch, apogeeTime);

		String slug = sanitizeBaseName(vc.cdx1File.replace(".CDX1", ""));
		File markdownReport = new File(outDir, slug + ".md");
		File trajectoryCsv = new File(outDir, slug + "-trajectory.csv");
		File componentCdCsv = new File(outDir, slug + "-component-cd.csv");

		writeTrajectoryCsv(primaryBranch, burnoutIndex, apogeeIndex, trajectoryCsv);
		writeComponentCdCsv(rocket, maxMach, componentCdCsv);
		writeMarkdownReport(vc, doc, loadWarnings, simWarnings, sim, data, primaryBranch, primaryBranchIndex,
				burnoutTime, apogeeTime, groundHitTime, burnoutIndex, apogeeIndex,
				orpApogeeFt, orpErrorPct, deltaVsRasPct, terminalNote,
				markdownReport, trajectoryCsv, componentCdCsv);

		String ignoredSettingFlags = extractIgnoredSettingFlags(loadWarnings);

		return new DiagnosticArtifacts(vc, orpApogeeFt, orpErrorPct, deltaVsRasPct, maxMach, data.getFlightTime(),
				data.getBranchCount(), primaryBranchIndex, burnoutTime, apogeeTime, groundHitTime, terminalNote,
				loadWarnings.size(), simWarnings.size(), ignoredSettingFlags,
				markdownReport, trajectoryCsv, componentCdCsv);
	}

	/**
	 * Extract ignored-setting flags from loader warnings.
	 * Returns a semicolon-delimited string like "ModifiedBarrowman=True;Turbulence=True;SustainerNozzle=1.25"
	 * or empty string if none.
	 */
	private static String extractIgnoredSettingFlags(WarningSet loadWarnings) {
		if (loadWarnings == null || loadWarnings.isEmpty()) {
			return "";
		}
		List<String> flags = new ArrayList<>();
		for (Warning w : loadWarnings) {
			String text = w.toString();
			if (text.startsWith(IGNORED_SETTING_PREFIX)) {
				// Extract "ModifiedBarrowman=True" from "Ignoring unsupported RASAero setting ModifiedBarrowman=True."
				String setting = text.substring(IGNORED_SETTING_PREFIX.length());
				if (setting.endsWith(".")) {
					setting = setting.substring(0, setting.length() - 1);
				}
				flags.add(setting);
			}
		}
		return String.join(";", flags);
	}

	private static void writeMarkdownReport(SimVRealBenchmarkTest.ValidationCase vc,
			OpenRocketDocument doc, WarningSet loadWarnings, WarningSet simWarnings, Simulation sim, FlightData data,
			FlightDataBranch primaryBranch, int primaryBranchIndex,
			double burnoutTime, double apogeeTime, double groundHitTime, int burnoutIndex, int apogeeIndex,
			double orpApogeeFt, double orpErrorPct, double deltaVsRasPct, String terminalNote,
			File markdownReport, File trajectoryCsv, File componentCdCsv) throws Exception {
		Rocket rocket = doc.getRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		RigidBody launchMass = MassCalculator.calculateLaunch(config);
		RigidBody burnoutMass = MassCalculator.calculateBurnout(config);

		int maxMachIndex = indexOfMaximum(primaryBranch.get(FlightDataType.TYPE_MACH_NUMBER));
		int maxAltIndex = indexOfMaximum(primaryBranch.get(FlightDataType.TYPE_ALTITUDE));

		PhaseSummary boost = summarizePhase(primaryBranch, 0, burnoutIndex, "boost");
		PhaseSummary coast = summarizePhase(primaryBranch, burnoutIndex, apogeeIndex, "coast");
		PhaseSummary descent = summarizePhase(primaryBranch, apogeeIndex, primaryBranch.getLength() - 1, "descent");

		StringBuilder md = new StringBuilder();
		md.append("# ").append(vc.rocketName).append(" AST Outlier Diagnostic\n\n");
		md.append("## Comparison\n\n");
		md.append(String.format(Locale.US, "- CDX1: `%s`%n", vc.cdx1File));
		md.append(String.format(Locale.US, "- Real apogee: `%.0f ft` (%s)%n", vc.realAltitudeFt, vc.dataSource));
		md.append(String.format(Locale.US, "- RASAero II apogee: `%.0f ft` (`%+.1f%%`)%n",
				vc.rasAeroAltitudeFt, vc.rasAeroErrorPct()));
		md.append(String.format(Locale.US, "- ORP apogee: `%.0f ft` (`%+.1f%%` vs real, `%+.1f%%` vs RASAero)%n",
				orpApogeeFt, orpErrorPct, deltaVsRasPct));
		md.append(String.format(Locale.US, "- Max Mach: `%.3f`%n", data.getMaxMachNumber()));
		md.append(String.format(Locale.US, "- Flight time: `%.1f s` across `%d` branch(es); primary branch = `%d`%n",
				data.getFlightTime(), data.getBranchCount(), primaryBranchIndex));
		md.append(String.format(Locale.US, "- Terminal note: `%s`%n%n",
				terminalNote.isEmpty() ? "NORMAL" : terminalNote));

		md.append("## Geometry And Mass\n\n");
		md.append(String.format(Locale.US, "- Reference diameter: `%.3f in`%n", config.getReferenceLength() * IN_PER_METER));
		md.append(String.format(Locale.US, "- Reference area: `%.5f m^2`%n", config.getReferenceArea()));
		md.append(String.format(Locale.US, "- Launch mass: `%.3f kg` (`%.3f lb`), CG `%.3f in` from nose%n",
				launchMass.getMass(), launchMass.getMass() * LB_PER_KG, launchMass.getCenterOfMass().getX() * IN_PER_METER));
		md.append(String.format(Locale.US, "- Burnout mass: `%.3f kg` (`%.3f lb`), CG `%.3f in` from nose%n%n",
				burnoutMass.getMass(), burnoutMass.getMass() * LB_PER_KG, burnoutMass.getCenterOfMass().getX() * IN_PER_METER));

		md.append("| Component | Type | Length (in) | Position (in) | Detail |\n");
		md.append("|---|---:|---:|---:|---|\n");
		for (RocketComponent component : rocket) {
			md.append("| ")
					.append(escapePipes(component.getName()))
					.append(" | ")
					.append(component.getClass().getSimpleName())
					.append(" | ")
					.append(fmt(component.getLength() * IN_PER_METER))
					.append(" | ")
					.append(fmt(component.getPosition().getX() * IN_PER_METER))
					.append(" | ")
					.append(escapePipes(componentDetail(component)))
					.append(" |\n");
		}
		md.append('\n');

		md.append("## Parity And Warnings\n\n");
		appendWarnings(md, "Loader warnings", loadWarnings);
		appendWarnings(md, "Simulation warnings", simWarnings);

		md.append("## Key Events\n\n");
		md.append(String.format(Locale.US, "- Burnout time: `%s`%n", formatMaybeSeconds(burnoutTime)));
		md.append(String.format(Locale.US, "- Apogee time: `%s`%n", formatMaybeSeconds(apogeeTime)));
		md.append(String.format(Locale.US, "- Ground hit time: `%s`%n%n", formatMaybeSeconds(groundHitTime)));

		for (int branchIndex = 0; branchIndex < data.getBranchCount(); branchIndex++) {
			FlightDataBranch branch = data.getBranch(branchIndex);
			md.append(String.format(Locale.US, "### Branch %d%n%n", branchIndex));
			for (FlightEvent event : branch.getEvents()) {
				md.append(String.format(Locale.US, "- `t=%.3f s` `%s` source=`%s` data=`%s`%n",
						event.getTime(),
						event.getType(),
						eventSourceName(event),
						safeText(event.getData())));
			}
			md.append('\n');
		}

		md.append("## Phase Summary\n\n");
		md.append("| Phase | Start (s) | End (s) | Duration (s) | Alt start (ft) | Alt end (ft) | Delta alt (ft) | Max Mach | Avg Cd | Avg Cdf | Avg Cdp | Avg Cdb | Avg AoA (deg) | Avg stability (cal) | Min damping ratio |\n");
		md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
		appendPhaseRow(md, boost);
		appendPhaseRow(md, coast);
		appendPhaseRow(md, descent);
		md.append('\n');

		md.append("## Actual-State Component Breakdown\n\n");
		appendStateBreakdown(md, config, primaryBranch, burnoutIndex, "burnout");
		appendStateBreakdown(md, config, primaryBranch, maxMachIndex, "max-mach");
		appendStateBreakdown(md, config, primaryBranch, maxAltIndex, "apogee");

		md.append("## Artifact Paths\n\n");
		md.append(String.format(Locale.US, "- Trajectory CSV: `%s`%n", trajectoryCsv.getPath().replace('\\', '/')));
		md.append(String.format(Locale.US, "- Component Cd sweep CSV: `%s`%n", componentCdCsv.getPath().replace('\\', '/')));

		try (BufferedWriter w = new BufferedWriter(new FileWriter(markdownReport))) {
			w.write(md.toString());
		}
	}

	private static void appendStateBreakdown(StringBuilder md, FlightConfiguration config, FlightDataBranch branch,
			int index, String label) {
		if (index < 0 || index >= branch.getLength()) {
			md.append(String.format(Locale.US, "### %s%n%n- Not available.%n%n", label));
			return;
		}

		double mach = valueAt(branch, FlightDataType.TYPE_MACH_NUMBER, index);
		double aoaRad = valueAt(branch, FlightDataType.TYPE_AOA, index);
		double pressure = valueAt(branch, FlightDataType.TYPE_AIR_PRESSURE, index);
		double temperature = valueAt(branch, FlightDataType.TYPE_AIR_TEMPERATURE, index);
		double pitchRate = valueAt(branch, FlightDataType.TYPE_PITCH_RATE, index);
		double yawRate = valueAt(branch, FlightDataType.TYPE_YAW_RATE, index);
		double rollRate = valueAt(branch, FlightDataType.TYPE_ROLL_RATE, index);

		if (Double.isNaN(mach) || Double.isNaN(pressure) || Double.isNaN(temperature)) {
			md.append(String.format(Locale.US, "### %s%n%n- Insufficient flight-state data for component breakdown.%n%n", label));
			return;
		}

		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(aoaRad);
		conditions.setPitchRate(pitchRate);
		conditions.setYawRate(yawRate);
		conditions.setRollRate(rollRate);
		conditions.setAtmosphericConditions(new AtmosphericConditions(temperature, pressure));

		BarrowmanCalculator calc = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();
		Map<RocketComponent, AerodynamicForces> forceMap = calc.getForceAnalysis(config, conditions, warnings);

		md.append(String.format(Locale.US,
				"### %s%n%n- Time: `%.3f s`%n- Mach: `%.3f`%n- AoA: `%.3f deg`%n- Air temperature: `%.2f K`%n- Air pressure: `%.1f Pa`%n%n",
				label,
				valueAt(branch, FlightDataType.TYPE_TIME, index),
				mach,
				Math.toDegrees(aoaRad),
				temperature,
				pressure));
		md.append("| Component | Cd total | Cdf | Cdp | Cdb |\n");
		md.append("|---|---:|---:|---:|---:|\n");
		for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
			RocketComponent component = entry.getKey();
			AerodynamicForces forces = entry.getValue();
			if (forces == null || Double.isNaN(forces.getCD()) || Math.abs(forces.getCD()) < 1.0e-8) {
				continue;
			}
			md.append("| ")
					.append(escapePipes(component.getName()))
					.append(" | ")
					.append(fmt(forces.getCD()))
					.append(" | ")
					.append(fmt(zeroIfNaN(forces.getFrictionCD())))
					.append(" | ")
					.append(fmt(zeroIfNaN(forces.getPressureCD())))
					.append(" | ")
					.append(fmt(zeroIfNaN(forces.getBaseCD())))
					.append(" |\n");
		}
		if (!warnings.isEmpty()) {
			md.append('\n');
			appendWarnings(md, "Breakdown warnings", warnings);
		}
	}

	private static void writeComponentCdCsv(Rocket rocket, double maxMach, File csvOut) throws Exception {
		double sweepMax = Math.max(3.5, Math.ceil((maxMach + 0.5) * 10.0) / 10.0);
		List<AeroCoeffientExporter.ComponentAeroData> data =
				AeroCoeffientExporter.machSweep(rocket, 0.3, sweepMax, 0.1, 0.0);
		try (BufferedWriter w = new BufferedWriter(new FileWriter(csvOut))) {
			AeroCoeffientExporter.writeCsv(data, w);
		}
	}

	private static void writeTrajectoryCsv(FlightDataBranch branch, int burnoutIndex, int apogeeIndex, File csvOut)
			throws Exception {
		try (BufferedWriter w = new BufferedWriter(new FileWriter(csvOut))) {
			w.write("index,phase,time_s,altitude_m,velocity_m_s,velocity_z_m_s,mach,thrust_N,drag_N,cd_total,cd_friction,cd_pressure,cd_base,mass_kg,cg_m,cp_m,stability_cal,aoa_deg,pitch_rate_deg_s,roll_rate_deg_s,yaw_rate_deg_s,damping_ratio,cmq,air_temp_K,air_pressure_Pa,air_density_kg_m3\n");
			for (int i = 0; i < branch.getLength(); i++) {
				w.write(String.format(Locale.US,
						"%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
						i,
						phaseLabel(i, burnoutIndex, apogeeIndex),
						csvNum(valueAt(branch, FlightDataType.TYPE_TIME, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_ALTITUDE, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_VELOCITY_TOTAL, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_VELOCITY_Z, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_MACH_NUMBER, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_THRUST_FORCE, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_DRAG_FORCE, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_DRAG_COEFF, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_FRICTION_DRAG_COEFF, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_PRESSURE_DRAG_COEFF, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_BASE_DRAG_COEFF, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_MASS, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_CG_LOCATION, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_CP_LOCATION, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_STABILITY, i)),
						csvNum(Math.toDegrees(valueAt(branch, FlightDataType.TYPE_AOA, i))),
						csvNum(Math.toDegrees(valueAt(branch, FlightDataType.TYPE_PITCH_RATE, i))),
						csvNum(Math.toDegrees(valueAt(branch, FlightDataType.TYPE_ROLL_RATE, i))),
						csvNum(Math.toDegrees(valueAt(branch, FlightDataType.TYPE_YAW_RATE, i))),
						csvNum(valueAt(branch, FlightDataType.TYPE_DAMPING_RATIO, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_PITCH_DAMPING_DERIVATIVE, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_AIR_TEMPERATURE, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_AIR_PRESSURE, i)),
						csvNum(valueAt(branch, FlightDataType.TYPE_AIR_DENSITY, i))));
			}
		}
	}

	private static PhaseSummary summarizePhase(FlightDataBranch branch, int rawStartIndex, int rawEndIndex, String label) {
		if (branch.getLength() == 0) {
			return PhaseSummary.empty(label);
		}
		int startIndex = clampIndex(rawStartIndex, branch.getLength());
		int endIndex = clampIndex(rawEndIndex, branch.getLength());
		if (endIndex < startIndex) {
			endIndex = startIndex;
		}

		double startTime = valueAt(branch, FlightDataType.TYPE_TIME, startIndex);
		double endTime = valueAt(branch, FlightDataType.TYPE_TIME, endIndex);
		double altStartFt = valueAt(branch, FlightDataType.TYPE_ALTITUDE, startIndex) * FT_PER_METER;
		double altEndFt = valueAt(branch, FlightDataType.TYPE_ALTITUDE, endIndex) * FT_PER_METER;

		return new PhaseSummary(
				label,
				startTime,
				endTime,
				endTime - startTime,
				altStartFt,
				altEndFt,
				altEndFt - altStartFt,
				maxInRange(branch, FlightDataType.TYPE_MACH_NUMBER, startIndex, endIndex),
				averageInRange(branch, FlightDataType.TYPE_DRAG_COEFF, startIndex, endIndex),
				averageInRange(branch, FlightDataType.TYPE_FRICTION_DRAG_COEFF, startIndex, endIndex),
				averageInRange(branch, FlightDataType.TYPE_PRESSURE_DRAG_COEFF, startIndex, endIndex),
				averageInRange(branch, FlightDataType.TYPE_BASE_DRAG_COEFF, startIndex, endIndex),
				Math.toDegrees(averageInRange(branch, FlightDataType.TYPE_AOA, startIndex, endIndex)),
				averageInRange(branch, FlightDataType.TYPE_STABILITY, startIndex, endIndex),
				minInRange(branch, FlightDataType.TYPE_DAMPING_RATIO, startIndex, endIndex));
	}

	private static void appendPhaseRow(StringBuilder md, PhaseSummary phase) {
		md.append("| ")
				.append(phase.label)
				.append(" | ")
				.append(fmtOrDash(phase.startTimeS))
				.append(" | ")
				.append(fmtOrDash(phase.endTimeS))
				.append(" | ")
				.append(fmtOrDash(phase.durationS))
				.append(" | ")
				.append(fmtOrDash(phase.altStartFt))
				.append(" | ")
				.append(fmtOrDash(phase.altEndFt))
				.append(" | ")
				.append(fmtOrDash(phase.altDeltaFt))
				.append(" | ")
				.append(fmtOrDash(phase.maxMach))
				.append(" | ")
				.append(fmtOrDash(phase.avgCd))
				.append(" | ")
				.append(fmtOrDash(phase.avgFrictionCd))
				.append(" | ")
				.append(fmtOrDash(phase.avgPressureCd))
				.append(" | ")
				.append(fmtOrDash(phase.avgBaseCd))
				.append(" | ")
				.append(fmtOrDash(phase.avgAoaDeg))
				.append(" | ")
				.append(fmtOrDash(phase.avgStabilityCal))
				.append(" | ")
				.append(fmtOrDash(phase.minDampingRatio))
				.append(" |\n");
	}

	private static void appendWarnings(StringBuilder md, String label, WarningSet warnings) {
		md.append("### ").append(label).append('\n').append('\n');
		if (warnings == null || warnings.isEmpty()) {
			md.append("- None.\n\n");
			return;
		}
		for (Warning warning : warnings) {
			md.append("- ").append(safeText(warning)).append('\n');
		}
		md.append('\n');
	}

	private static double resolveBurnoutTime(FlightDataBranch branch) {
		FlightEvent burnout = branch.getLastEvent(FlightEvent.Type.BURNOUT);
		if (burnout != null) {
			return burnout.getTime();
		}
		List<Double> thrust = branch.get(FlightDataType.TYPE_THRUST_FORCE);
		List<Double> time = branch.get(FlightDataType.TYPE_TIME);
		if (thrust == null || time == null) {
			return Double.NaN;
		}
		for (int i = 1; i < Math.min(thrust.size(), time.size()); i++) {
			double prev = thrust.get(i - 1);
			double curr = thrust.get(i);
			if (prev > 1.0 && curr <= 0.5) {
				return time.get(i);
			}
		}
		return Double.NaN;
	}

	private static double resolveApogeeTime(FlightDataBranch branch) {
		FlightEvent apogee = branch.getFirstEvent(FlightEvent.Type.APOGEE);
		if (apogee != null) {
			return apogee.getTime();
		}
		int index = indexOfMaximum(branch.get(FlightDataType.TYPE_ALTITUDE));
		return valueAt(branch, FlightDataType.TYPE_TIME, index);
	}

	private static double resolveGroundHitTime(FlightDataBranch branch) {
		FlightEvent groundHit = branch.getLastEvent(FlightEvent.Type.GROUND_HIT);
		return groundHit != null ? groundHit.getTime() : Double.NaN;
	}

	private static int resolveBurnoutIndex(FlightDataBranch branch, double burnoutTime) {
		if (Double.isNaN(burnoutTime)) {
			return 0;
		}
		int index = branch.getDataIndexOfTime(burnoutTime);
		return index >= 0 ? index : 0;
	}

	private static int resolveApogeeIndex(FlightDataBranch branch, double apogeeTime) {
		if (!Double.isNaN(apogeeTime)) {
			int index = branch.getDataIndexOfTime(apogeeTime);
			if (index >= 0) {
				return index;
			}
		}
		return indexOfMaximum(branch.get(FlightDataType.TYPE_ALTITUDE));
	}

	private static int findPrimaryBranchIndex(FlightData data) {
		int bestIndex = 0;
		double bestAltitude = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < data.getBranchCount(); i++) {
			double altitude = data.getBranch(i).getMaximum(FlightDataType.TYPE_ALTITUDE);
			if (Double.isNaN(altitude)) {
				continue;
			}
			if (altitude > bestAltitude) {
				bestAltitude = altitude;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	private static double valueAt(FlightDataBranch branch, FlightDataType type, int index) {
		if (branch == null || index < 0) {
			return Double.NaN;
		}
		List<Double> values = branch.get(type);
		if (values == null || index >= values.size()) {
			return Double.NaN;
		}
		return values.get(index);
	}

	private static double averageInRange(FlightDataBranch branch, FlightDataType type, int startIndex, int endIndex) {
		double sum = 0.0;
		int count = 0;
		for (int i = startIndex; i <= endIndex; i++) {
			double value = valueAt(branch, type, i);
			if (!Double.isNaN(value)) {
				sum += value;
				count++;
			}
		}
		return count == 0 ? Double.NaN : sum / count;
	}

	private static double maxInRange(FlightDataBranch branch, FlightDataType type, int startIndex, int endIndex) {
		double max = Double.NaN;
		for (int i = startIndex; i <= endIndex; i++) {
			double value = valueAt(branch, type, i);
			if (Double.isNaN(value)) {
				continue;
			}
			if (Double.isNaN(max) || value > max) {
				max = value;
			}
		}
		return max;
	}

	private static double minInRange(FlightDataBranch branch, FlightDataType type, int startIndex, int endIndex) {
		double min = Double.NaN;
		for (int i = startIndex; i <= endIndex; i++) {
			double value = valueAt(branch, type, i);
			if (Double.isNaN(value)) {
				continue;
			}
			if (Double.isNaN(min) || value < min) {
				min = value;
			}
		}
		return min;
	}

	private static int indexOfMaximum(List<Double> values) {
		if (values == null || values.isEmpty()) {
			return -1;
		}
		int bestIndex = 0;
		double bestValue = values.get(0);
		for (int i = 1; i < values.size(); i++) {
			double value = values.get(i);
			if (Double.isNaN(value)) {
				continue;
			}
			if (Double.isNaN(bestValue) || value > bestValue) {
				bestValue = value;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	private static void runSimulationWithTimeout(Simulation sim, long timeoutMs, String rocketName) throws Exception {
		final RuntimeException[] thrown = new RuntimeException[1];
		Thread simThread = new Thread(() -> {
			try {
				sim.simulate();
			} catch (RuntimeException e) {
				thrown[0] = e;
			} catch (Exception e) {
				thrown[0] = new RuntimeException(e);
			}
		});
		simThread.start();
		simThread.join(timeoutMs);
		if (simThread.isAlive()) {
			simThread.interrupt();
			throw new IllegalStateException("Simulation timed out after " + timeoutMs + " ms for " + rocketName);
		}
		if (thrown[0] != null) {
			throw thrown[0];
		}
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
			if (simulationEnd == null) {
				continue;
			}
			boolean groundHit = data.getBranch(branchIndex).getLastEvent(FlightEvent.Type.GROUND_HIT) != null;
			if (!groundHit && simulationEnd.getTime() >= maxTime - tolerance) {
				return String.format(Locale.US, "MAXTIME@%.0fs", simulationEnd.getTime());
			}
		}
		return "";
	}

	private static FlightEvent findFirstEvent(FlightData data, FlightEvent.Type type) {
		for (int branchIndex = 0; branchIndex < data.getBranchCount(); branchIndex++) {
			FlightEvent event = data.getBranch(branchIndex).getFirstEvent(type);
			if (event != null) {
				return event;
			}
		}
		return null;
	}

	private static File findSimVRealDir() {
		File dir = new File(SIMVREAL_DIR);
		if (dir.exists()) {
			return dir;
		}
		Path current = Paths.get(System.getProperty("user.dir"));
		for (int i = 0; i < 5; i++) {
			File candidate = current.resolve(SIMVREAL_DIR).toFile();
			if (candidate.exists()) {
				return candidate;
			}
			current = current.getParent();
			if (current == null) {
				break;
			}
		}
		return null;
	}

	private static boolean isTargetCase(String rocketName) {
		for (String target : TARGET_CASES) {
			if (target.equals(rocketName)) {
				return true;
			}
		}
		return false;
	}

	private static String phaseLabel(int index, int burnoutIndex, int apogeeIndex) {
		if (index <= burnoutIndex) {
			return "boost";
		}
		if (index <= apogeeIndex) {
			return "coast";
		}
		return "descent";
	}

	private static int clampIndex(int index, int length) {
		if (length <= 0) {
			return 0;
		}
		if (index < 0) {
			return 0;
		}
		if (index >= length) {
			return length - 1;
		}
		return index;
	}

	private static String componentDetail(RocketComponent component) {
		if (component instanceof NoseCone noseCone) {
			return String.format(Locale.US, "shape=%s baseDia=%.3f in",
					noseCone.getShapeType(),
					noseCone.getBaseRadius() * 2.0 * IN_PER_METER);
		}
		if (component instanceof BodyTube bodyTube) {
			return String.format(Locale.US, "outerDia=%.3f in",
					bodyTube.getOuterRadius() * 2.0 * IN_PER_METER);
		}
		if (component instanceof Transition transition && !(component instanceof NoseCone)) {
			return String.format(Locale.US, "foreDia=%.3f in aftDia=%.3f in",
					transition.getForeRadius() * 2.0 * IN_PER_METER,
					transition.getAftRadius() * 2.0 * IN_PER_METER);
		}
		if (component instanceof TrapezoidFinSet finSet) {
			return String.format(Locale.US,
					"count=%d root=%.3f in tip=%.3f in span=%.3f in sweep=%.3f in thick=%.3f in xsec=%s",
					finSet.getFinCount(),
					finSet.getRootChord() * IN_PER_METER,
					finSet.getTipChord() * IN_PER_METER,
					finSet.getSpan() * IN_PER_METER,
					finSet.getSweep() * IN_PER_METER,
					finSet.getThickness() * IN_PER_METER,
					finSet.getCrossSection());
		}
		if (component instanceof FinSet finSet) {
			return String.format(Locale.US,
					"count=%d span=%.3f in thick=%.3f in xsec=%s",
					finSet.getFinCount(),
					finSet.getSpan() * IN_PER_METER,
					finSet.getThickness() * IN_PER_METER,
					finSet.getCrossSection());
		}
		if (component instanceof RailButton railButton) {
			return String.format(Locale.US, "outerDia=%.3f in",
					railButton.getOuterDiameter() * IN_PER_METER);
		}
		if (component instanceof PodSet) {
			return "pod assembly";
		}
		if (component instanceof SymmetricComponent symmetric) {
			return String.format(Locale.US, "foreDia=%.3f in aftDia=%.3f in",
					symmetric.getForeRadius() * 2.0 * IN_PER_METER,
					symmetric.getAftRadius() * 2.0 * IN_PER_METER);
		}
		return "";
	}

	private static String sanitizeBaseName(String text) {
		return text.replaceAll("[^A-Za-z0-9._-]+", "_");
	}

	private static String eventSourceName(FlightEvent event) {
		return event.getSource() == null ? "null" : event.getSource().getName();
	}

	private static String safeText(Object value) {
		if (value == null) {
			return "null";
		}
		return escapePipes(String.valueOf(value)).replace("\r", " ").replace("\n", " ");
	}

	private static String escapePipes(String text) {
		return text == null ? "" : text.replace("|", "\\|");
	}

	private static String fmt(double value) {
		return String.format(Locale.US, "%.4f", value);
	}

	private static String fmtOrDash(double value) {
		return Double.isNaN(value) ? "-" : fmt(value);
	}

	private static String csvNum(double value) {
		return Double.isNaN(value) ? "" : String.format(Locale.US, "%.8f", value);
	}

	private static String formatMaybeSeconds(double value) {
		return Double.isNaN(value) ? "n/a" : String.format(Locale.US, "%.3f s", value);
	}

	private static double zeroIfNaN(double value) {
		return Double.isNaN(value) ? 0.0 : value;
	}

	private static final class DiagnosticArtifacts {
		private final SimVRealBenchmarkTest.ValidationCase validationCase;
		private final double orpApogeeFt;
		private final double orpErrorPct;
		private final double deltaVsRasPct;
		private final double maxMach;
		private final double flightTimeS;
		private final int branchCount;
		private final int primaryBranchIndex;
		private final double burnoutTimeS;
		private final double apogeeTimeS;
		private final double groundHitTimeS;
		private final String terminalNote;
		private final int loadWarningCount;
		private final int simWarningCount;
		private final String ignoredSettingFlags;
		private final File markdownReport;
		private final File trajectoryCsv;
		private final File componentCdCsv;

		private DiagnosticArtifacts(SimVRealBenchmarkTest.ValidationCase validationCase,
				double orpApogeeFt, double orpErrorPct, double deltaVsRasPct, double maxMach, double flightTimeS,
				int branchCount, int primaryBranchIndex, double burnoutTimeS, double apogeeTimeS, double groundHitTimeS,
				String terminalNote, int loadWarningCount, int simWarningCount, String ignoredSettingFlags,
				File markdownReport, File trajectoryCsv, File componentCdCsv) {
			this.validationCase = validationCase;
			this.orpApogeeFt = orpApogeeFt;
			this.orpErrorPct = orpErrorPct;
			this.deltaVsRasPct = deltaVsRasPct;
			this.maxMach = maxMach;
			this.flightTimeS = flightTimeS;
			this.branchCount = branchCount;
			this.primaryBranchIndex = primaryBranchIndex;
			this.burnoutTimeS = burnoutTimeS;
			this.apogeeTimeS = apogeeTimeS;
			this.groundHitTimeS = groundHitTimeS;
			this.terminalNote = terminalNote;
			this.loadWarningCount = loadWarningCount;
			this.simWarningCount = simWarningCount;
			this.ignoredSettingFlags = ignoredSettingFlags;
			this.markdownReport = markdownReport;
			this.trajectoryCsv = trajectoryCsv;
			this.componentCdCsv = componentCdCsv;
		}

		/** CSV row for the original outlier-focused summary (backward compatible). */
		private String toCsvRow() {
			return String.format(Locale.US,
					"%s,%s,%.0f,%.0f,%.0f,%.4f,%.4f,%.4f,%.4f,%d,%d,%s,%s,%s,%s,%d,%d,%s,%s,%s",
					csvText(validationCase.rocketName),
					csvText(validationCase.cdx1File),
					validationCase.realAltitudeFt,
					validationCase.rasAeroAltitudeFt,
					orpApogeeFt,
					orpErrorPct,
					deltaVsRasPct,
					maxMach,
					flightTimeS,
					branchCount,
					primaryBranchIndex,
					csvNum(burnoutTimeS),
					csvNum(apogeeTimeS),
					csvNum(groundHitTimeS),
					csvText(terminalNote.isEmpty() ? "NORMAL" : terminalNote),
					loadWarningCount,
					simWarningCount,
					csvText(markdownReport.getPath().replace('\\', '/')),
					csvText(trajectoryCsv.getPath().replace('\\', '/')),
					csvText(componentCdCsv.getPath().replace('\\', '/')));
		}

		/** CSV row for the full-corpus summary with ignored-setting flags. */
		private String toFullCorpusCsvRow() {
			return String.format(Locale.US,
					"%s,%s,%.0f,%.0f,%.0f,%.4f,%.4f,%.4f,%s,%s,%s,%s,%d,%d,%s,%s,%s,%s",
					csvText(validationCase.rocketName),
					csvText(validationCase.cdx1File),
					validationCase.realAltitudeFt,
					validationCase.rasAeroAltitudeFt,
					orpApogeeFt,
					orpErrorPct,
					deltaVsRasPct,
					maxMach,
					csvNum(burnoutTimeS),
					csvNum(apogeeTimeS),
					csvNum(groundHitTimeS),
					csvText(terminalNote.isEmpty() ? "NORMAL" : terminalNote),
					loadWarningCount,
					simWarningCount,
					csvText(ignoredSettingFlags),
					csvText(markdownReport.getPath().replace('\\', '/')),
					csvText(trajectoryCsv.getPath().replace('\\', '/')),
					csvText(componentCdCsv.getPath().replace('\\', '/')));
		}

		private static String csvText(String text) {
			String safe = text == null ? "" : text.replace("\"", "\"\"");
			return "\"" + safe + "\"";
		}
	}

	private static final class PhaseSummary {
		private final String label;
		private final double startTimeS;
		private final double endTimeS;
		private final double durationS;
		private final double altStartFt;
		private final double altEndFt;
		private final double altDeltaFt;
		private final double maxMach;
		private final double avgCd;
		private final double avgFrictionCd;
		private final double avgPressureCd;
		private final double avgBaseCd;
		private final double avgAoaDeg;
		private final double avgStabilityCal;
		private final double minDampingRatio;

		private PhaseSummary(String label, double startTimeS, double endTimeS, double durationS,
				double altStartFt, double altEndFt, double altDeltaFt, double maxMach, double avgCd,
				double avgFrictionCd, double avgPressureCd, double avgBaseCd, double avgAoaDeg,
				double avgStabilityCal, double minDampingRatio) {
			this.label = label;
			this.startTimeS = startTimeS;
			this.endTimeS = endTimeS;
			this.durationS = durationS;
			this.altStartFt = altStartFt;
			this.altEndFt = altEndFt;
			this.altDeltaFt = altDeltaFt;
			this.maxMach = maxMach;
			this.avgCd = avgCd;
			this.avgFrictionCd = avgFrictionCd;
			this.avgPressureCd = avgPressureCd;
			this.avgBaseCd = avgBaseCd;
			this.avgAoaDeg = avgAoaDeg;
			this.avgStabilityCal = avgStabilityCal;
			this.minDampingRatio = minDampingRatio;
		}

		private static PhaseSummary empty(String label) {
			return new PhaseSummary(label, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
					Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		}
	}
}
