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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import info.openrocket.core.util.Coordinate;

/**
 * Comparator: ORP pitch damping (Cmq + CmAlphaDot) vs Bhagwandin &amp; Sahu (2013)
 * CFD predictions for ANF (Army-Navy Basic Finner) and AFF (Air Force Modified
 * Finner).
 * <p>
 * Source: Bhagwandin, V.A. and Sahu, J., "Numerical Prediction of Pitch Damping
 * Stability Derivatives for Finned Projectiles," ARL-TR-6725, US Army Research
 * Laboratory, Aberdeen Proving Ground, November 2013. DTIC Accession ADA592550.
 * Public release.
 * <p>
 * Data digitized from Tables A-1 (ANF, delta=0 deg, fine grid) and A-2 (AFF,
 * delta=0 deg) in the report appendix. Two CFD methods are reported in the
 * paper: planar pitching (forced sinusoidal pitch oscillation, A=0.25 deg,
 * k=0.1) and lunar coning (sigma=0.5 deg, Omega=0.0025, Magnus-corrected).
 * This comparator uses the planar pitching predictions because that is the
 * physical motion modelled by ORP's strip-theory pitch-damping
 * ({@code BarrowmanStabilityCalculator.calculateDampingMoments}).
 * <p>
 * <b>Quantity matching.</b> The paper's "PDM" is the sum
 * {@code (Cmq + Cmalpha_dot)} (pitch damping moment coefficient sum) and is
 * directly comparable to ORP's {@code getCmq() + getCmAlphaDot()} (which is
 * hard-coded as {@code 1.4 * Cmq} in the calculator).
 * <p>
 * <b>Geometry justification.</b> AFF is non-Basic-Finner: tangent ogive
 * (2.5 cal) instead of a 10-deg cone (2.84 cal), and clipped-delta fins with
 * sharp LE/TE instead of 1x1 cal square fins. This makes Bhagwandin &amp; Sahu's
 * AFF dataset a genuine independent geometry from the existing ADA636861 ANF
 * source family (which Albisser also tested).
 * <p>
 * <b>Reference points.</b>
 * <ul>
 *   <li>ANF CG: 5.500 cal from nose (consistent with ADA636861 Table I).</li>
 *   <li>AFF CG: 4.8 cal from nose (paper text).</li>
 * </ul>
 * Both use D = 0.030 m and reference area pi*D^2/4.
 * <p>
 * <b>This is publication evidence, not a regression gate.</b> No hard MAPE
 * threshold is asserted. The test produces two CSV artifacts under
 * {@code paper/data/csv/} that are read by the assessment doc.
 */
public class BhagwandinSahuCmqComparatorTest {

	/** Fin cant in Tables A-1 and A-2 used here. */
	private static final String FIN_CANT_DEG = "0";
	/** CFD method used as primary comparator. */
	private static final String METHOD = "planar_pitching";

	/** Body diameter for both ANF and AFF in the paper. */
	private static final double D = 0.030;

	/** ANF CG location: 5.500 cal from nose (ADA636861 Table I). */
	private static final double ANF_CG_FROM_NOSE_M = 5.500 * D;
	/** AFF CG location: 4.8 cal from nose (ARL-TR-6725 paper text). */
	private static final double AFF_CG_FROM_NOSE_M = 4.8 * D;

	/** ORP's hard-coded CmAlphaDot/Cmq ratio (BarrowmanStabilityCalculator line 188). */
	private static final double ORP_CMADOT_OVER_CMQ = 0.4;

	private static final Path SOURCE_CSV =
			Path.of("..", "paper", "data", "csv", "bhagwandin_sahu_2013_anf_aff_cmq.csv");
	private static final Path SOURCE_CSV_ROOT =
			Path.of("paper", "data", "csv", "bhagwandin_sahu_2013_anf_aff_cmq.csv");

	private static final Path AFF_OUT =
			Path.of("..", "paper", "data", "csv", "bhagwandin_aff_cmq_comparator_2026_05_02.csv");
	private static final Path AFF_OUT_ROOT =
			Path.of("paper", "data", "csv", "bhagwandin_aff_cmq_comparator_2026_05_02.csv");
	private static final Path ANF_OUT =
			Path.of("..", "paper", "data", "csv", "bhagwandin_anf_cmq_comparator_2026_05_02.csv");
	private static final Path ANF_OUT_ROOT =
			Path.of("paper", "data", "csv", "bhagwandin_anf_cmq_comparator_2026_05_02.csv");

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ------------------------------------------------------------------
	// CSV ingestion
	// ------------------------------------------------------------------

	private static final class Point {
		final String geometry;
		final double mach;
		final double pdmCfd;
		final double pdfCfd;
		final String source;

		Point(String geometry, double mach, double pdmCfd, double pdfCfd, String source) {
			this.geometry = geometry;
			this.mach = mach;
			this.pdmCfd = pdmCfd;
			this.pdfCfd = pdfCfd;
			this.source = source;
		}
	}

	private static List<Point> loadDigitizedPoints(String wantGeometry) throws IOException {
		Path path = Files.exists(SOURCE_CSV) ? SOURCE_CSV : SOURCE_CSV_ROOT;
		assertTrue(Files.exists(path),
				"Bhagwandin source CSV not found at " + SOURCE_CSV.toAbsolutePath()
						+ " or " + SOURCE_CSV_ROOT.toAbsolutePath());

		List<Point> out = new ArrayList<>();
		try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String line;
			boolean headerSeen = false;
			while ((line = r.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
				if (!headerSeen) {
					headerSeen = true;
					continue;
				}
				String[] f = trimmed.split(",");
				if (f.length < 7) continue;
				String geometry = f[0].trim();
				String fcant = f[1].trim();
				String method = f[2].trim();
				double mach = Double.parseDouble(f[3].trim());
				double pdm = Double.parseDouble(f[4].trim());
				double pdf = Double.parseDouble(f[5].trim());
				String source = f[6].trim();
				if (!geometry.equals(wantGeometry)) continue;
				if (!fcant.equals(FIN_CANT_DEG)) continue;
				if (!method.equals(METHOD)) continue;
				out.add(new Point(geometry, mach, pdm, pdf, source));
			}
		}
		return out;
	}

	// ------------------------------------------------------------------
	// ORP coefficient evaluation
	// ------------------------------------------------------------------

	/** Returns {Cmq, CmAlphaDot, combined PDM = Cmq + CmAlphaDot}. */
	private double[] orpDamping(Rocket rocket, double mach, double cgFromNoseM) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		conditions.setPitchCenter(new Coordinate(cgFromNoseM, 0.0, 0.0, 0.0));
		// Non-zero pitch rate required for calculateDampingMoments to compute
		// finite damping moment; does not change the derivative.
		conditions.setPitchRate(0.05);
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		double cmq = forces.getCmq();
		double cmAlphaDot = forces.getCmAlphaDot();
		return new double[] { cmq, cmAlphaDot, cmq + cmAlphaDot };
	}

	// ------------------------------------------------------------------
	// Comparator runners
	// ------------------------------------------------------------------

	private static class CompareResult {
		double mape;
		int included;
		int total;
		double worstAbsPct;
		double worstMach;
		List<String[]> rows = new ArrayList<>();
	}

	private CompareResult runComparator(String geometry, Rocket rocket, double cgFromNoseM,
			Path outPath, Path outPathRoot) throws IOException {
		List<Point> points = loadDigitizedPoints(geometry);
		assertFalse(points.isEmpty(),
				"No " + geometry + " points loaded from Bhagwandin source CSV");

		CompareResult out = new CompareResult();
		out.total = points.size();
		out.rows.add(new String[] { "mach", "pdm_bhagwandin_cfd", "pdf_bhagwandin_cfd",
				"cmq_orp", "cmadot_orp", "pdm_orp_predicted",
				"delta", "delta_pct", "abs_delta_pct" });

		double sumAbsPct = 0.0;
		Map<Double, double[]> cache = new LinkedHashMap<>();

		for (Point p : points) {
			double[] orp = cache.computeIfAbsent(p.mach,
					m -> orpDamping(rocket, m, cgFromNoseM));
			double pdmOrp = orp[2];
			double delta = pdmOrp - p.pdmCfd;
			double deltaPct = (p.pdmCfd != 0.0) ? (delta / p.pdmCfd) * 100.0 : Double.NaN;
			double absDeltaPct = Math.abs(deltaPct);

			if (Double.isFinite(deltaPct)) {
				sumAbsPct += absDeltaPct;
				out.included++;
				if (absDeltaPct > out.worstAbsPct) {
					out.worstAbsPct = absDeltaPct;
					out.worstMach = p.mach;
				}
			}

			out.rows.add(new String[] {
					String.format(Locale.US, "%.4f", p.mach),
					String.format(Locale.US, "%.4f", p.pdmCfd),
					String.format(Locale.US, "%.4f", p.pdfCfd),
					String.format(Locale.US, "%.4f", orp[0]),
					String.format(Locale.US, "%.4f", orp[1]),
					String.format(Locale.US, "%.4f", pdmOrp),
					String.format(Locale.US, "%.4f", delta),
					Double.isFinite(deltaPct) ? String.format(Locale.US, "%.3f", deltaPct) : "NaN",
					Double.isFinite(deltaPct) ? String.format(Locale.US, "%.3f", absDeltaPct) : "NaN"
			});
		}
		out.mape = (out.included > 0) ? (sumAbsPct / out.included) : Double.NaN;

		// Resolve writable output path
		Path target = outPath;
		try {
			Files.createDirectories(target.getParent());
		} catch (IOException ignored) {
			target = outPathRoot;
			Files.createDirectories(target.getParent());
		}

		try (BufferedWriter w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
			w.write("# ORP vs Bhagwandin & Sahu 2013 (ARL-TR-6725) CFD pitch damping comparator\n");
			w.write("# Geometry: " + geometry + "\n");
			w.write("# Method (paper): " + METHOD + ", fin cant " + FIN_CANT_DEG + " deg\n");
			w.write(String.format(Locale.US,
					"# CG reference: %.4f m (%.3f cal at D=%.3f m)%n",
					cgFromNoseM, cgFromNoseM / D, D));
			w.write("# pdm_orp_predicted = ORP getCmq() + ORP getCmAlphaDot() (combined)\n");
			w.write("# delta = pdm_orp_predicted - pdm_bhagwandin_cfd\n");
			w.write("# delta_pct = delta / pdm_bhagwandin_cfd * 100\n");
			w.write(String.format(Locale.US,
					"# MAPE: %.2f %% over %d/%d points; worst |delta_pct|=%.2f %% at M=%.3f%n",
					out.mape, out.included, out.total, out.worstAbsPct, out.worstMach));
			for (String[] row : out.rows) {
				w.write(String.join(",", row));
				w.newLine();
			}
		}
		System.out.println("[" + geometry + "] comparator CSV: " + target.toAbsolutePath());
		System.out.printf(Locale.US,
				"[%s] MAPE = %.2f %%  (worst %.2f %% at M=%.3f, %d/%d points)%n",
				geometry, out.mape, out.worstAbsPct, out.worstMach, out.included, out.total);

		return out;
	}

	// ------------------------------------------------------------------
	// Tests — emit artifacts; no hard MAPE threshold (publication evidence).
	// ------------------------------------------------------------------

	@Test
	void testAffComparatorVsBhagwandinCfd() throws IOException {
		Rocket aff = SupersonicTestRockets.makeAirForceModifiedFinner();
		CompareResult res = runComparator("AFF", aff, AFF_CG_FROM_NOSE_M, AFF_OUT, AFF_OUT_ROOT);
		// Sanity-only: at least half the points must produce a finite ORP value.
		assertTrue(res.included >= res.total / 2,
				"AFF: ORP produced finite Cmq for only " + res.included + "/" + res.total
						+ " points; investigate ORP failure modes.");
	}

	@Test
	void testAnfComparatorVsBhagwandinCfd() throws IOException {
		Rocket anf = SupersonicTestRockets.makeBasicFinner();
		CompareResult res = runComparator("ANF", anf, ANF_CG_FROM_NOSE_M, ANF_OUT, ANF_OUT_ROOT);
		assertTrue(res.included >= res.total / 2,
				"ANF: ORP produced finite Cmq for only " + res.included + "/" + res.total
						+ " points; investigate ORP failure modes.");
	}
}
