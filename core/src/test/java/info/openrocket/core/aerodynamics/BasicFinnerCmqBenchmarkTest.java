package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
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
import info.openrocket.core.util.Coordinate;

/**
 * Benchmark: ORP pitch damping (Cmq + CmAlphaDot) vs ADA636861 Basic Finner
 * free-flight aeroballistic range data.
 * <p>
 * Source: Dupuis, A.D. and Hathaway, W., "Aeroballistic Range Tests of the
 * Basic Finner Reference Projectile at Supersonic Velocities,"
 * DREV-TM-9703, Defence Research Establishment Valcartier, August 1997.
 * DTIC Accession Number: ADA636861.
 * <p>
 * Data digitized from the PDF at
 * {@code paper/data/pdf/ADA636861.pdf}, Tables VII, VIII and IX (pages 42-48).
 * CSV: {@code paper/data/csv/ada636861_basic_finner_cmq.csv}.
 * <p>
 * <b>Critical data-type caveat.</b> Free-flight aeroballistic range data
 * produces the <b>combined</b> pitch-damping coefficient
 * {@code (Cmq + CmAlphaDot)}. It cannot separate the pure pitch damping
 * derivative from the angle-of-attack rate derivative. ORP exposes
 * {@code getCmq()} and {@code getCmAlphaDot()} separately and
 * {@code BarrowmanStabilityCalculator} hard-codes
 * {@code CmAlphaDot = 0.4 * Cmq} (see lines 187-188). Therefore the
 * physically-meaningful ORP quantity for comparison against ADA636861 is
 * {@code ORP_damping_combined = Cmq + CmAlphaDot = 1.4 * Cmq}.
 * <p>
 * <b>Moment reference point.</b> ADA636861 Table I reports CG at 16.50 cm
 * from nose = 5.500 calibres (d = 30 mm). This test explicitly sets
 * {@code FlightConditions.pitchCenter} to that position so the ORP Cmq is
 * computed about the same reference as the experiment.
 * <p>
 * <b>Transonic augmentation is on for this benchmark.</b> The
 * {@code BarrowmanStabilityCalculator} multiplies Cmq by a Gaussian
 * {@code k_transonic = 1 + 2.5 * exp(-((M-1)/0.15)^2)} centred at M=1.
 * Several of the digitized points at M 1.05-1.25 fall inside the non-trivial
 * part of that Gaussian tail. The test reports ORP-vs-experiment at the
 * native ORP value (augmentation ON) since that is what the simulator uses
 * in flight.
 * <p>
 * <b>Constants untouched.</b> This test DOES NOT modify
 * {@code DAMPING_MULTIPLIER}, {@code TRANSONIC_CMQ_PEAK} or any other
 * stability-calculator constant. Nor does it modify any A-level benchmark.
 * It is a pure comparison test: measure, export, document.
 *
 * <h2>Measured baseline (2026-04-17) and what it means for AST claims</h2>
 *
 * With unmodified ORP code, the quality-filtered MAPE against the 44 ok-flag
 * shots from ADA636861 Tables VII, VIII and IX is <b>69.1 %</b>. The gate
 * below is set to 75 % strictly as a regression guard (headroom above the
 * current baseline so future code changes that <i>worsen</i> disagreement
 * will be flagged). It is NOT tuned to claim closure and this benchmark does
 * NOT constitute closure of the finned-vehicle pitch-damping claim.
 *
 * <p>Per-band breakdown (diagnostic CSV in {@code build/reports/basic-finner-cmq/}):
 * <ul>
 *   <li><b>Near-transonic M 1.05-1.12:</b> ORP overpredicts |damping| by
 *       170-353 % due to the {@code k_transonic = 1 + 2.5 * exp(-((M-1)/0.15)^2)}
 *       Gaussian in {@code BarrowmanStabilityCalculator}. At M=1.056 the
 *       augmentation multiplier is &asymp; 3.25, which is larger than the
 *       experimental scatter supports. Peak amplitude and Gaussian width
 *       remain B-level heuristics.</li>
 *   <li><b>Supersonic M 1.25-4.5:</b> ORP consistently underpredicts
 *       |damping| by 20-50 %. The sign is correct at every point. The
 *       closest match is at M 1.254 (Table VIII, -0.6 %) and M 4.127 (Table
 *       VIII, -5.7 %). This is a systematic magnitude bias, not a sign or
 *       scaling error.</li>
 * </ul>
 *
 * <p>Per AST roadmap rules: MAPE &ge; 40 % &rArr; document as a finding, do
 * not claim closure. This test IS the finding artifact.
 */
@Tag("slow")  // heavy Cmq benchmark (~13 s); excluded by default, run with -Pslow
public class BasicFinnerCmqBenchmarkTest {

	/**
	 * Regression guard. Set to 75 %, strictly above the measured baseline of
	 * 69.1 % (2026-04-17, commit on branch supersonic-aero-dev). Do NOT tune
	 * downward without re-measuring. Changes that improve (decrease) the MAPE
	 * below 50 % should tighten this gate; changes that push it above 75 %
	 * fail and require investigation.
	 */
	private static final double MAPE_GATE_PCT = 75.0;

	/** CmAlphaDot / Cmq ratio hard-coded in BarrowmanStabilityCalculator line 188. */
	private static final double ORP_CMADOT_OVER_CMQ = 0.4;

	/** Combined damping factor = (1 + CmAlphaDot/Cmq). */
	private static final double ORP_COMBINED_FACTOR = 1.0 + ORP_CMADOT_OVER_CMQ;

	/** ADA636861 Table I: CG at 16.50 cm from nose (5.500 cal, d = 30 mm). */
	private static final double CG_FROM_NOSE_M = 0.1650;

	private static final Path CSV_PATH = Path.of(
			"..", "paper", "data", "csv", "ada636861_basic_finner_cmq.csv");
	/** Fallback path if tests are run from repo root (not core/). */
	private static final Path CSV_PATH_ROOT = Path.of(
			"paper", "data", "csv", "ada636861_basic_finner_cmq.csv");

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ------------------------------------------------------------------
	// CSV parsing
	// ------------------------------------------------------------------

	private static final class Point {
		final String shot;
		final double mach;
		final double cmqExp;
		final String sourceTable;
		final String qualityFlag;

		Point(String shot, double mach, double cmqExp, String sourceTable, String qualityFlag) {
			this.shot = shot;
			this.mach = mach;
			this.cmqExp = cmqExp;
			this.sourceTable = sourceTable;
			this.qualityFlag = qualityFlag;
		}
	}

	private static List<Point> loadDigitizedPoints() throws IOException {
		Path path = Files.exists(CSV_PATH) ? CSV_PATH : CSV_PATH_ROOT;
		assertTrue(Files.exists(path),
				"ADA636861 Cmq CSV not found at " + CSV_PATH.toAbsolutePath()
						+ " or " + CSV_PATH_ROOT.toAbsolutePath());

		List<Point> out = new ArrayList<>();
		try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String line;
			boolean headerSeen = false;
			while ((line = r.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
				if (!headerSeen) {
					// First non-comment line is the header row "shot,mach,cmq,..."
					headerSeen = true;
					continue;
				}
				String[] f = trimmed.split(",");
				// Columns (per CSV header):
				//   shot, mach, cmq, source_table, dbsq, probable_error_pct, quality_flag
				if (f.length < 7) continue;
				String shot = f[0];
				double mach = Double.parseDouble(f[1]);
				double cmq = Double.parseDouble(f[2]);
				String table = f[3];
				String quality = f[6];
				out.add(new Point(shot, mach, cmq, table, quality));
			}
		}
		assertFalse(out.isEmpty(), "No digitized Cmq rows parsed from CSV");
		return out;
	}

	// ------------------------------------------------------------------
	// ORP Cmq at fixed Mach, with pitch centre at ADA636861 CG
	// ------------------------------------------------------------------

	/** Returns {ORP_cmq, ORP_cmAlphaDot, ORP_combined (= cmq + cmAlphaDot)}. */
	private double[] orpDampingCoefficients(double mach) {
		Rocket rocket = SupersonicTestRockets.makeBasicFinner();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		// CG reference: ADA636861 Table I, 16.50 cm from nose.
		conditions.setPitchCenter(new Coordinate(CG_FROM_NOSE_M, 0.0, 0.0, 0.0));
		// A non-zero pitch rate is required for calculateDampingMoments to produce
		// a finite pitch-damping moment; it does not alter the derivative itself.
		conditions.setPitchRate(0.05);
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		double cmq = forces.getCmq();
		double cmAlphaDot = forces.getCmAlphaDot();
		return new double[] { cmq, cmAlphaDot, cmq + cmAlphaDot };
	}

	// ------------------------------------------------------------------
	// Main MAPE gate test + per-point CSV export
	// ------------------------------------------------------------------

	@Test
	void testMapeVsAda636861() throws IOException {
		List<Point> points = loadDigitizedPoints();

		// Build the per-point comparison table.
		StringBuilder table = new StringBuilder();
		table.append("=== ADA636861 Basic Finner Cmq benchmark ===\n");
		table.append(String.format(
				"CG reference: %.4f m from nose (%.3f cal)%n",
				CG_FROM_NOSE_M, CG_FROM_NOSE_M / 0.030));
		table.append(String.format(
				"ORP combined = Cmq + CmAlphaDot = (1 + %.2f)*Cmq (hard-coded ratio)%n%n",
				ORP_CMADOT_OVER_CMQ));
		table.append(String.format(Locale.US,
				"%-12s %-8s %-5s %-10s %-10s %-10s %-10s %-10s%n",
				"shot", "mach", "tbl", "cmq_exp", "cmq_orp", "cmadot_orp",
				"comb_orp", "err_%"));

		List<String[]> csvRows = new ArrayList<>();
		csvRows.add(new String[] { "shot", "mach", "source_table", "quality_flag",
				"cmq_exp_combined", "cmq_orp", "cmadot_orp", "combined_orp",
				"err_pct", "abs_err_pct", "included_in_mape" });

		double sumAbsPct = 0.0;
		int included = 0;
		double worstAbs = 0.0;
		String worstShot = "";
		double worstMach = Double.NaN;
		double worstErr = 0.0;

		// Cache ORP Cmq per unique Mach to keep run time bounded.
		java.util.Map<Double, double[]> cache = new java.util.LinkedHashMap<>();

		for (Point p : points) {
			double[] orp = cache.computeIfAbsent(p.mach, this::orpDampingCoefficients);
			double combined = orp[2];
			double errPct = (combined - p.cmqExp) / p.cmqExp * 100.0;

			boolean includeInMape = "ok".equalsIgnoreCase(p.qualityFlag);
			if (includeInMape) {
				sumAbsPct += Math.abs(errPct);
				included++;
				if (Math.abs(errPct) > worstAbs) {
					worstAbs = Math.abs(errPct);
					worstShot = p.shot;
					worstMach = p.mach;
					worstErr = errPct;
				}
			}

			table.append(String.format(Locale.US,
					"%-12s %-8.3f %-5s %-10.2f %-10.2f %-10.2f %-10.2f %+-9.1f%s%n",
					p.shot, p.mach, p.sourceTable, p.cmqExp,
					orp[0], orp[1], orp[2], errPct,
					includeInMape ? "" : " (" + p.qualityFlag + ")"));

			csvRows.add(new String[] {
					p.shot,
					String.format(Locale.US, "%.4f", p.mach),
					p.sourceTable,
					p.qualityFlag,
					String.format(Locale.US, "%.4f", p.cmqExp),
					String.format(Locale.US, "%.4f", orp[0]),
					String.format(Locale.US, "%.4f", orp[1]),
					String.format(Locale.US, "%.4f", orp[2]),
					String.format(Locale.US, "%.3f", errPct),
					String.format(Locale.US, "%.3f", Math.abs(errPct)),
					Boolean.toString(includeInMape)
			});
		}

		assertTrue(included > 0, "No quality_flag=ok rows available for MAPE");
		double mape = sumAbsPct / included;

		table.append(String.format(Locale.US,
				"%nQuality-filtered points (flag=ok) : %d of %d%n", included, points.size()));
		table.append(String.format(Locale.US, "MAPE (|combined_ORP - Cmq_exp| / |Cmq_exp|) : %.1f %%%n", mape));
		table.append(String.format(Locale.US, "Worst point : shot=%s M=%.3f err=%+.1f %%%n",
				worstShot, worstMach, worstErr));
		table.append(String.format(Locale.US, "Gate        : %.0f %%%n", MAPE_GATE_PCT));

		System.out.println(table);

		// Write per-point CSV artifact for audit.
		Path reportDir = Path.of("build", "reports", "basic-finner-cmq");
		Files.createDirectories(reportDir);
		Path reportCsv = reportDir.resolve("basic_finner_cmq_vs_ada636861.csv");
		try (BufferedWriter w = Files.newBufferedWriter(reportCsv, StandardCharsets.UTF_8)) {
			w.write("# ORP vs ADA636861 Basic Finner Cmq benchmark\n");
			w.write(String.format(Locale.US,
					"# CG reference: %.4f m from nose (%.3f cal)%n",
					CG_FROM_NOSE_M, CG_FROM_NOSE_M / 0.030));
			w.write("# combined_orp = ORP getCmq() + ORP getCmAlphaDot()\n");
			w.write("# err_pct = (combined_orp - cmq_exp_combined) / cmq_exp_combined * 100\n");
			w.write(String.format(Locale.US, "# MAPE (flag=ok only): %.1f %%%n", mape));
			for (String[] row : csvRows) {
				w.write(String.join(",", row));
				w.newLine();
			}
		}
		System.out.println("Report written to: " + reportCsv.toAbsolutePath());

		// Per project rules, do NOT tune the gate to pass. If ORP disagrees
		// badly, the test fails and the disagreement is reported in the CSV
		// artifact and stdout above.
		assertTrue(mape <= MAPE_GATE_PCT,
				String.format(Locale.US,
						"Basic Finner Cmq MAPE %.1f%% exceeds initial gate %.0f%% "
								+ "(worst %s M=%.3f err=%+.1f%%). "
								+ "See build/reports/basic-finner-cmq/ for full table.",
						mape, MAPE_GATE_PCT, worstShot, worstMach, worstErr));
	}

	// ------------------------------------------------------------------
	// Sign + sanity trend tests (these should never require tuning).
	// ------------------------------------------------------------------

	/** Cmq must be stabilizing (negative) at all Mach numbers tested. */
	@Test
	void testCmqSignIsStabilizing() {
		double[] machPoints = { 1.077, 1.293, 1.832, 2.375, 2.718, 3.147, 3.734, 4.300 };
		for (double m : machPoints) {
			double[] orp = orpDampingCoefficients(m);
			assertTrue(orp[0] < 0, "Cmq should be negative (stabilizing) at M=" + m
					+ ", got " + orp[0]);
			assertTrue(orp[2] < 0,
					"Combined damping should be negative at M=" + m + ", got " + orp[2]);
		}
	}

	/**
	 * At each multi-fit Mach point from ADA636861 Table IX, ORP damping must
	 * stay inside a loose order-of-magnitude envelope of the experiment. The
	 * envelope bounds are chosen to catch gross regressions (sign flip,
	 * decade drift, unit scaling errors) while tolerating the current known
	 * biases documented in the class Javadoc:
	 * <ul>
	 *   <li>Transonic (M&lt;=1.1): ORP overpredicts by up to ~3.7x due to
	 *       the {@code k_transonic} Gaussian augmentation.</li>
	 *   <li>Mid-supersonic: ORP under-predicts by 20-50 %.</li>
	 * </ul>
	 * The envelope {@code 0.5 < ratio < 5.0} admits both biases while still
	 * asserting the sign is correct and the magnitude is not orders of
	 * magnitude off.
	 */
	@Test
	void testEnvelopeVsMultiFit() throws IOException {
		List<Point> points = loadDigitizedPoints();
		int checked = 0;
		for (Point p : points) {
			if (!"IX".equals(p.sourceTable)) continue;
			double[] orp = orpDampingCoefficients(p.mach);
			double combined = orp[2];
			double ratio = combined / p.cmqExp;
			assertTrue(ratio > 0.0,
					"ORP damping sign differs from experiment at M=" + p.mach
							+ " (orp=" + combined + " exp=" + p.cmqExp + ")");
			assertTrue(ratio > 0.5 && ratio < 5.0,
					String.format(Locale.US,
							"ORP/exp ratio out of 0.5..5.0 envelope at M=%.3f: "
									+ "orp=%.2f exp=%.2f ratio=%.2f",
							p.mach, combined, p.cmqExp, ratio));
			checked++;
		}
		assertTrue(checked >= 5, "Expected >=5 Table IX multi-fit points, got " + checked);
	}
}
