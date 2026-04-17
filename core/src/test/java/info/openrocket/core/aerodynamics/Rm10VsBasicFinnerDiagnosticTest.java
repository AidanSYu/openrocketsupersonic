package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.startup.Application;

/**
 * Read-only diagnostic test: compares per-component CD breakdown for
 * NACA RM-10 (NACA TN 3320) vs. Basic Finner (ADA636861) side by side at
 * a handful of supersonic Mach points, and emits a CSV under
 * {@code core/build/reports/} to support root-cause analysis of the
 * +80.5 % MAPE RM-10 overshoot noted in Prompt 14.
 *
 * <p>This test MODIFIES NO CALCULATOR CODE and MODIFIES NO EXISTING TEST.
 * It rebuilds the two geometries from scratch using the exact construction
 * used by the primary benchmarks (RM-10 geometry verbatim from
 * {@code NacaRm10FinnedBodyDragBenchmarkTest#makeNacaRm10FullScale()};
 * Basic Finner from {@code SupersonicTestRockets#makeBasicFinner()}) and
 * runs {@link BarrowmanCalculator#getForceAnalysis} at each Mach point.
 *
 * <p>Output CSV columns:
 * <pre>
 *   mach,case,component,friction,pressure,base,total
 * </pre>
 * One row per component + a rocket-total row per Mach per case.
 *
 * <p>See {@code paper/data/rm10_vs_basic_finner_diagnostic.md} for the
 * ranked root-cause analysis that consumes this CSV.
 */
public class Rm10VsBasicFinnerDiagnosticTest {

	private static final double[] DIAGNOSTIC_MACHS = { 1.5, 2.0, 2.5, 3.0 };

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ================================================================
	// RM-10 geometry (copied verbatim from NacaRm10FinnedBodyDragBenchmarkTest
	// to keep the diagnostic self-contained; test file is NOT modified).
	// ================================================================

	private static final double RM10_FULL_BODY_DIAMETER_M   = 0.3048;     // 12.00 in
	private static final double RM10_FIN_LE_RADIUS_M        = 0.123774;   //  4.873 in
	private static final double RM10_FULL_BASE_DIAMETER_M   = 0.184709;   //  7.272 in
	private static final double RM10_NOSE_LENGTH_M          = 2.286;      // 90.00 in
	private static final double RM10_FORE_BOATTAIL_LENGTH_M = 0.9906;     // 39.00 in
	private static final double RM10_FIN_MOUNT_LENGTH_M     = 0.4445;     // 17.50 in
	private static final double RM10_FIN_ROOT_CHORD_M       = 0.4445;     // 17.50 in
	private static final double RM10_FIN_SEMI_SPAN_M        = 0.3319;     // 13.07 in
	private static final double RM10_FIN_STREAMWISE_TC      = 0.05;

	private static Rocket makeRm10() {
		Rocket rocket = new Rocket();
		rocket.setName("NACA RM-10 (diagnostic)");
		rocket.setPerfectFinish(true);

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.POWER,
				RM10_NOSE_LENGTH_M, RM10_FULL_BODY_DIAMETER_M / 2.0);
		nose.setShapeParameter(0.5);
		nose.setName("ParaboloidNose");
		nose.setThickness(0.002);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		Transition foreBoattail = new Transition();
		foreBoattail.setShapeType(Transition.Shape.CONICAL);
		foreBoattail.setForeRadius(RM10_FULL_BODY_DIAMETER_M / 2.0);
		foreBoattail.setAftRadius(RM10_FIN_LE_RADIUS_M);
		foreBoattail.setLength(RM10_FORE_BOATTAIL_LENGTH_M);
		foreBoattail.setThickness(0.002);
		foreBoattail.setName("ForeBoattail");
		foreBoattail.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(foreBoattail);

		BodyTube finMount = new BodyTube(RM10_FIN_MOUNT_LENGTH_M,
				RM10_FIN_LE_RADIUS_M, 0.002);
		finMount.setName("FinMountTube");
		finMount.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(finMount);

		double finTipChordM = 0.2062; // 8.12 in (AR=2.04 reconstruction)
		double sweepLength = RM10_FIN_SEMI_SPAN_M * Math.tan(Math.toRadians(60.0));
		double finThickness = RM10_FIN_STREAMWISE_TC * RM10_FIN_ROOT_CHORD_M;

		TrapezoidFinSet fins = new TrapezoidFinSet(4,
				RM10_FIN_ROOT_CHORD_M, finTipChordM, sweepLength, RM10_FIN_SEMI_SPAN_M);
		fins.setName("Rm10Fins");
		fins.setCrossSection(FinSet.CrossSection.ROUNDED);
		fins.setThickness(finThickness);
		fins.setFinish(ExternalComponent.Finish.POLISHED);
		fins.setAxialMethod(AxialMethod.BOTTOM);
		finMount.addChild(fins);

		Transition terminalBoattail = new Transition();
		terminalBoattail.setShapeType(Transition.Shape.CONICAL);
		terminalBoattail.setForeRadius(RM10_FIN_LE_RADIUS_M);
		terminalBoattail.setAftRadius(RM10_FULL_BASE_DIAMETER_M / 2.0);
		terminalBoattail.setLength(0.02);
		terminalBoattail.setThickness(0.002);
		terminalBoattail.setName("TerminalBoattail");
		terminalBoattail.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(terminalBoattail);

		rocket.enableEvents();
		return rocket;
	}

	// ================================================================
	// Driver
	// ================================================================

	@Test
	void diagnosticBreakdownRm10VsBasicFinner() throws IOException {
		Path outDir = Paths.get("build", "reports");
		Files.createDirectories(outDir);
		Path csv = outDir.resolve("rm10_vs_basic_finner_component_cd.csv");

		try (BufferedWriter w = Files.newBufferedWriter(csv)) {
			w.write("mach,case,component,shape_or_kind,length_m,fore_r_m,aft_r_m,friction,pressure,base,total\n");

			for (double mach : DIAGNOSTIC_MACHS) {
				emitCase(w, "RM10", makeRm10(), mach);
				emitCase(w, "BF",   SupersonicTestRockets.makeBasicFinner(), mach);
			}
		}

		System.out.println("Wrote component-CD diagnostic CSV: " + csv.toAbsolutePath());

		// Sanity: the CSV must contain at least one row per case per Mach
		long rows = Files.lines(csv).count();
		// header + 2 cases * 4 machs * (>=4 rows / case) -> plenty of rows
		assertTrue(rows > 1 + 2 * DIAGNOSTIC_MACHS.length, "diagnostic CSV is suspiciously short: " + rows);
	}

	/**
	 * Run the Barrowman calculator, emit one CSV row per aerodynamic component
	 * plus a single ROCKET_TOTAL row per (case, mach).
	 */
	private static void emitCase(BufferedWriter w, String caseName, Rocket rocket, double mach) throws IOException {
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);

		Map<RocketComponent, AerodynamicForces> forceMap =
				calc.getForceAnalysis(config, conditions, new WarningSet());

		double sumFric = 0, sumPress = 0, sumBase = 0;

		// Per-component rows
		for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
			RocketComponent c = entry.getKey();
			AerodynamicForces f = entry.getValue();
			if (!c.isAerodynamic()) continue;

			double fric  = safe(f.getFrictionCD());
			double press = safe(f.getPressureCD());
			double base  = safe(f.getBaseCD());
			double tot   = fric + press + base;

			String kind = c.getClass().getSimpleName();
			double length = 0, foreR = 0, aftR = 0;
			if (c instanceof Transition) {
				Transition t = (Transition) c;
				length = t.getLength();
				foreR  = t.getForeRadius();
				aftR   = t.getAftRadius();
				kind = kind + ":" + t.getShapeType()
						+ "(p=" + String.format(Locale.ROOT, "%.2f", t.getShapeParameter()) + ")";
			} else if (c instanceof BodyTube) {
				BodyTube bt = (BodyTube) c;
				length = bt.getLength();
				foreR  = bt.getOuterRadius();
				aftR   = bt.getOuterRadius();
			} else if (c instanceof FinSet) {
				FinSet fs = (FinSet) c;
				length = fs.getLength();
				foreR  = fs.getSpan();
				aftR   = fs.getThickness();
				kind = kind + ":" + fs.getCrossSection() + ":n=" + fs.getFinCount();
			}

			w.write(String.format(Locale.ROOT,
					"%.3f,%s,%s,%s,%.4f,%.4f,%.4f,%.6f,%.6f,%.6f,%.6f%n",
					mach, caseName, safeName(c), kind, length, foreR, aftR,
					fric, press, base, tot));

			sumFric  += fric;
			sumPress += press;
			sumBase  += base;
		}

		// Rocket-total row
		AerodynamicForces total = forceMap.get(rocket);
		double tFric  = total != null ? safe(total.getFrictionCD())  : sumFric;
		double tPress = total != null ? safe(total.getPressureCD()) : sumPress;
		double tBase  = total != null ? safe(total.getBaseCD())     : sumBase;
		double tTot   = total != null ? safe(total.getCD())          : (tFric + tPress + tBase);

		w.write(String.format(Locale.ROOT,
				"%.3f,%s,ROCKET_TOTAL,-,%.4f,%.4f,%.4f,%.6f,%.6f,%.6f,%.6f%n",
				mach, caseName, 0.0, 0.0, 0.0, tFric, tPress, tBase, tTot));

		// Also print to stdout for at-a-glance diagnostic review
		System.out.printf(Locale.ROOT,
				"[%s M=%.2f] fric=%.4f press=%.4f base=%.4f total=%.4f%n",
				caseName, mach, tFric, tPress, tBase, tTot);
	}

	private static String safeName(RocketComponent c) {
		String n = c.getName();
		return n == null ? c.getClass().getSimpleName() : n.replace(',', '_').replace(' ', '_');
	}

	private static double safe(double v) {
		return Double.isFinite(v) ? v : 0.0;
	}
}
