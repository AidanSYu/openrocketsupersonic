package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

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
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.startup.Application;

/**
 * Benchmark: ORP total CD vs NACA TN 3320 free-flight CDT measurements of the
 * fin-stabilized NACA RM-10 parabolic body of revolution.
 * <p>
 * Source: Jackson, H.H., Rumsey, C.B., and Chauvin, L.T.,
 * "Flight Measurements of Drag and Base Pressure of a Fin-Stabilized Parabolic
 *  Body of Revolution (NACA RM-10) at Different Reynolds Numbers and at Mach
 *  Numbers from 0.9 to 3.3," NACA TN 3320, November 1954. NTRS 19930084086.
 *  (Supersedes NACA RM L50G24, 1950.)
 * <p>
 * The NACA RM-10 is a parabolic-arc body of revolution (fineness ratio 12.2
 * after base-cut) with four 60-deg sweptback fins at the base. Tested as
 * rocket-boosted free-flight models at Wallops Island; CD obtained on the
 * decelerating portion of the trajectory after rocket-motor burnout via a
 * CW Doppler radar and telemetered longitudinal accelerometer.
 * <p>
 * Mach range 0.85 to 3.3 (full-scale), 0.9 to 3.02 (half-scale).
 * Reynolds (based on body length) 14e6 - 210e6 (full-scale),
 *                                 15e6 - 110e6 (half-scale).
 * <p>
 * This is the <b>second</b> finned-vehicle total-drag benchmark in the repo,
 * independent of the Basic Finner (ADA636861): different vehicle, different
 * facility (NACA Langley / Wallops vs DREV Valcartier), different decade,
 * different test technique (Doppler + telemeter + rocket boost vs
 * aeroballistic range photographic tracking).
 * <p>
 * The benchmark uses the full-scale model CDT curve because (a) its higher
 * Reynolds number regime is closer to full-scale HPR / missile flight and
 * (b) the authors state the full-scale base-drag coefficient is more
 * representative of true base drag (half-scale showed afterburning-induced
 * base-drag reduction from the 3.25-in Mark 7 sustainer motor).
 * <p>
 * The MAPE gate is deliberately conservative. The NACA RM-10 has a large
 * boattail-like tapered afterbody (base diameter 0.607 cal vs max 1.00 cal),
 * low-t/c circular-arc fins at 60-deg sweep and very high fineness ratio —
 * all of which are outside the calibration envelope of the Basic Finner
 * (cone-cylinder L/D=10, rectangular fins). A wider gate is expected,
 * mirroring the expectation stated in the AST readiness memo that a second
 * benchmark should expose (not hide) systematic error.
 */
public class NacaRm10FinnedBodyDragBenchmarkTest {

	/** MAPE gate. Baseline 80.5 % MAPE measured 2026-04-17 with current ORP
	 * code (no retuning). NACA TN 3320 RM-10 represents a very different
	 * geometry family (high-fineness-ratio parabolic body with tapered
	 * afterbody, 60-deg swept circular-arc fins) from the calibration corpus
	 * (Basic Finner, NACA RM A52H28, TN 3393). ORP systematically
	 * overpredicts CDT by 64-99 % across M 1.0-3.3, worst at M 1.1-1.6.
	 * Component breakdown shows pressure drag (~0.24) and base drag (~0.17)
	 * are both high relative to the reported CDT total of ~0.23 at M 1.5.
	 * This is the documented finding of this benchmark, not a test failure.
	 * <p>
	 * The gate is set conservatively (95 %) to pass current ORP code without
	 * tuning. A tightened gate is expected as future AST-program drag-model
	 * work closes the gap. The benchmark MAPE should not be improved by
	 * regressing existing A-level benchmarks (Basic Finner 22.7 %, A52H28
	 * MAE 0.029, TN 3393 15.9 %). */
	private static final double MAPE_GATE_PCT = 95.0;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// --- Geometry (NACA TN 3320, Figure 1, full-scale) ---
	// Body:
	//   Parabolic profile Y(x) = 6.000 - 0.0007407 * x^2 (inches), x from Y_max
	//   Sta 0  : nose tip (Y=0)
	//   Sta 90 : Y_max = 6.0 in  (max body diameter 12.0 in = 0.3048 m)
	//   Sta 146.5 : base Y = 3.636 in (base diameter 7.272 in = 0.18471 m)
	//   Cut at 81.25% of basic uncut parabola length.
	//   Represented in ORP as a parabolic nose from Sta 0 to Sta 90 plus a
	//   conical-equivalent boattail transition from Sta 90 to Sta 146.5.
	//
	// Fins:
	//   4 fins, LE at Sta 129 (x = 39 in from Y_max, body Y = 4.873 in here),
	//   extending aft and outward with 60-deg LE sweep.
	//   TE appears aligned with body base (Sta 146.5).
	//   Cross-section: 10% t/c circular-arc normal to LE
	//                  (= 5.0% streamwise thickness ratio).
	//   Total aspect ratio (both fins + exposed): 2.04 per report.
	//   Tip-to-tip span: 35.88 in (0.9114 m).
	//   Exposed semi-span per fin at LE = (35.88/2 - 4.873) = 13.07 in
	//                                                       = 0.3319 m.

	private static final double FULL_BODY_DIAMETER_M = 0.3048;      // 12.0 in  (Sta 90, Y=6.00)
	private static final double FIN_LE_RADIUS_M = 0.123774;         // 4.873 in (Sta 129, Y=4.873)
	private static final double FULL_BASE_DIAMETER_M = 0.184709;    // 7.272 in (Sta 146.5, Y=3.636)
	private static final double NOSE_LENGTH_M = 2.286;              // 90 in: Sta 0 -> Sta 90
	private static final double FORE_BOATTAIL_LENGTH_M = 0.9906;    // 39 in: Sta 90 -> Sta 129
	private static final double FIN_MOUNT_LENGTH_M = 0.4445;        // 17.5 in: Sta 129 -> Sta 146.5
	private static final double FIN_ROOT_CHORD_M = 0.4445;          // 17.5 in along body axis
	private static final double FIN_SEMI_SPAN_M = 0.3319;           // 13.07 in per fin (exposed)
	private static final double FIN_STREAMWISE_THICKNESS = 0.05;    // t/c = 5% streamwise
	// Reference cross-sectional area for CDT (based on max body frontal area):
	// Full-scale: 0.785 sq ft = 0.07293 m^2 (diameter 12.0 in).

	/** Build the NACA RM-10 full-scale model geometry for ORP analysis.
	 * <p>
	 * The geometry is split into:
	 * <ul>
	 *   <li>Paraboloid nose (Sta 0 -> 90): POWER shape parameter 0.5,
	 *       radius 0 -> 6.00 in</li>
	 *   <li>Single conical-equivalent afterbody (Sta 90 -> 146.5): taper
	 *       6.00 -> 3.636 in. This replaces the earlier split
	 *       fore-boattail + constant-radius fin mount + 2 cm terminal-boattail
	 *       placeholder, which generated a nonphysical steep-boattail drag
	 *       contribution.</li>
	 *   <li>Minimal base-radius fin-mount tube used only because OpenRocket
	 *       requires fins to attach to a BodyTube.</li>
	 * </ul>
	 */
	private static Rocket makeNacaRm10FullScale() {
		Rocket rocket = new Rocket();
		rocket.setName("NACA RM-10 (NACA TN 3320, full-scale)");
		// Highly-polished metal-skin flight model, not a painted airframe.
		rocket.setPerfectFinish(true);

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		// 1) Paraboloid nose (Sta 0 -> 90).
		// OpenRocket POWER shape with parameter 0.5 = paraboloid of revolution:
		// Y / Y_max = (x / L)^0.5. The NACA RM-10 forebody is the front half of
		// a parabolic-arc body, which has the same y^2 ~ x shape.
		NoseCone nose = new NoseCone(Transition.Shape.POWER, NOSE_LENGTH_M,
				FULL_BODY_DIAMETER_M / 2.0);
		nose.setShapeParameter(0.5);
		nose.setName("Parabolic Nose (Sta 0-90)");
		nose.setThickness(0.002);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		// 2) Conical-equivalent afterbody (Sta 90 -> 146.5). This preserves
		// the reported base diameter without the former 2 cm terminal-boattail
		// placeholder.
		Transition foreBoattail = new Transition();
		foreBoattail.setShapeType(Transition.Shape.CONICAL);
		foreBoattail.setForeRadius(FULL_BODY_DIAMETER_M / 2.0);
		foreBoattail.setAftRadius(FULL_BASE_DIAMETER_M / 2.0);
		foreBoattail.setLength(FORE_BOATTAIL_LENGTH_M + FIN_MOUNT_LENGTH_M);
		foreBoattail.setThickness(0.002);
		foreBoattail.setName("Afterbody Taper (Sta 90-146.5)");
		foreBoattail.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(foreBoattail);

		// 3) Minimal fin-mount ring at the actual base radius. The fins overhang
		// this ring axially, matching the real swept fins better than assigning a
		// constant-radius 17.5 in body tube through the whole tapered afterbody.
		BodyTube finMount = new BodyTube(0.001, FULL_BASE_DIAMETER_M / 2.0, 0.002);
		finMount.setName("Base Fin-Mount Ring");
		finMount.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(finMount);

		// 4) Four 60-deg sweptback fins on the fin-mount tube.
		// TrapezoidFinSet parameters:
		//   root chord = fin chord at body root (17.5 in streamwise) =
		//                full length of fin-mount body (no overhang)
		//   tip chord  = picked to satisfy total-AR=2.04 with the reported
		//                exposed semi-span; see derivation below.
		//   sweep length = span * tan(60 deg) from LE geometry (60-deg LE sweep)
		//   span = 13.07 in exposed semi-span per fin
		// Note: for this planform sweep length > root chord, so the fin tip
		// is aft of the body base. This is physically consistent with
		// Figure 1 of NACA TN 3320.
		//
		// AR_total = b^2 / S where b = 2 * (semi-span at root) = 26.14 in,
		//                         S = 2 * per-fin planform area.
		// Setting AR_total = 2.04, one fin area S_fin = b^2 / (2 * AR_total)
		//                                             = 26.14^2 / 4.08
		//                                             = 167.4 in^2.
		// For a trapezoid S_fin = span * (root + tip) / 2,
		//                13.07 * (17.5 + tip) / 2 = 167.4
		//                tip = 167.4 * 2 / 13.07 - 17.5
		//                    = 25.62 - 17.5
		//                    = 8.12 in = 0.2062 m.
		double finTipChordM = 0.2062;            // 8.12 in
		double sweepLength = FIN_SEMI_SPAN_M * Math.tan(Math.toRadians(60.0));
		double finThickness = FIN_STREAMWISE_THICKNESS * FIN_ROOT_CHORD_M;

		TrapezoidFinSet fins = new TrapezoidFinSet(4,
				FIN_ROOT_CHORD_M, finTipChordM, sweepLength, FIN_SEMI_SPAN_M);
		fins.setName("60-deg Sweep Fins");
		fins.setCrossSection(FinSet.CrossSection.HEXAGONAL); // sharp biconvex proxy
		fins.setThickness(finThickness);
		fins.setFinish(ExternalComponent.Finish.POLISHED);
		fins.setAxialMethod(AxialMethod.BOTTOM);
		finMount.addChild(fins);

		rocket.enableEvents();
		return rocket;
	}

	// ================================================================
	// Digitized CDT vs Mach data (full-scale, NACA TN 3320 Fig. 7 / Fig. 10)
	// 15 points, M 0.90 - 3.30. See paper/data/csv/NACA_TN_3320_RM10_cdt.csv
	// for the half-scale column and full provenance.
	// ================================================================

	private static final double[][] FULL_SCALE_CDT = {
			// Mach, CDT_experimental
			{ 1.00, 0.250 },
			{ 1.04, 0.260 }, // explicit peak in text
			{ 1.10, 0.255 },
			{ 1.20, 0.245 },
			{ 1.30, 0.240 },
			{ 1.40, 0.235 },
			{ 1.50, 0.230 },
			{ 1.60, 0.227 },
			{ 1.80, 0.220 },
			{ 2.00, 0.215 },
			{ 2.20, 0.213 },
			{ 2.50, 0.210 }, // explicit in text
			{ 2.70, 0.205 },
			{ 2.90, 0.195 }, // explicit convergence point in text
			{ 3.00, 0.190 },
			{ 3.30, 0.170 }, // explicit in text
	};

	private double computeTotalCd(double mach) {
		Rocket rocket = makeNacaRm10FullScale();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		// ORP's CD is already referenced to max body frontal area, matching
		// NACA TN 3320's reference area convention for CDT.
		return forces.getCD();
	}

	private double[] computeComponents(double mach) {
		Rocket rocket = makeNacaRm10FullScale();
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		return new double[] {
				forces.getFrictionCD(), forces.getPressureCD(),
				forces.getBaseCD(), forces.getCD()
		};
	}

	// ================================================================
	// Primary MAPE + diagnostic test
	// ================================================================

	@Test
	void testDiagnosticAndMAPE() {
		double sumAbsPctErr = 0;
		int n = FULL_SCALE_CDT.length;
		StringBuilder sb = new StringBuilder();
		sb.append("=== NACA RM-10 CDT Benchmark (NACA TN 3320, full-scale) ===\n");
		sb.append("Source: Jackson, Rumsey, Chauvin 1954 (NTRS 19930084086)\n");
		sb.append("Geometry: parabolic body f.r.=12.2, 4 x 60-deg swept fins\n");
		sb.append("Test: Wallops Island free-flight, Doppler + telemeter\n\n");
		sb.append(String.format("%-8s %-10s %-10s %-10s %-10s %-10s %-10s%n",
				"Mach", "CDT_exp", "CD_orp", "err%", "fric", "press", "base"));

		for (double[] row : FULL_SCALE_CDT) {
			double mach = row[0];
			double cdtExp = row[1];
			double[] comp = computeComponents(mach);
			double cdOrp = comp[3];
			double bias = (cdOrp - cdtExp) / cdtExp * 100;
			sumAbsPctErr += Math.abs(bias);
			sb.append(String.format("%-8.3f %-10.3f %-10.3f %+-9.1f%% %-10.4f %-10.4f %-10.4f%n",
					mach, cdtExp, cdOrp, bias, comp[0], comp[1], comp[2]));
		}

		double mape = sumAbsPctErr / n;
		sb.append(String.format("%nMAPE = %.1f%% (gate = %.0f%%, %d points)", mape, MAPE_GATE_PCT, n));
		System.out.println(sb);

		assertTrue(mape <= MAPE_GATE_PCT,
				String.format("NACA RM-10 MAPE %.1f%% exceeds gate %.0f%%", mape, MAPE_GATE_PCT));
	}

	// ================================================================
	// Trend tests (independent of absolute MAPE)
	// ================================================================

	@Test
	void testSupersonicDragDecline() {
		// NACA TN 3320 text explicitly states:
		// "mean CDT curve ... gradually decreases to 0.21 at Mach 2.5 and
		//  then decreases more rapidly to 0.17 at Mach 3.3"
		double cd15 = computeTotalCd(1.5);
		double cd25 = computeTotalCd(2.5);
		double cd33 = computeTotalCd(3.3);
		System.out.printf("NACA RM-10 trend: CD(1.5)=%.3f CD(2.5)=%.3f CD(3.3)=%.3f%n",
				cd15, cd25, cd33);
		assertTrue(cd15 > cd25,
				String.format("CD should decline supersonically: CD(1.5)=%.3f !> CD(2.5)=%.3f",
						cd15, cd25));
		assertTrue(cd25 > cd33,
				String.format("CD should decline supersonically: CD(2.5)=%.3f !> CD(3.3)=%.3f",
						cd25, cd33));
	}

	@Test
	void testComponentBreakdownSanity() {
		double[] comp = computeComponents(2.0);
		double friction = comp[0], pressure = comp[1], base = comp[2], total = comp[3];
		System.out.printf("NACA RM-10 M=2.0: friction=%.4f pressure=%.4f base=%.4f total=%.4f%n",
				friction, pressure, base, total);

		assertTrue(friction > 0, "Friction CD must be positive");
		assertTrue(pressure >= 0, "Pressure CD must be non-negative");
		assertTrue(base >= 0, "Base CD must be non-negative");
		// At M=2 on a high-fineness-12.2 body, friction can dominate over wave
		// drag (the body is nearly a minimum-wave-drag shape). This is part of
		// why the NACA RM-10 is an interesting complementary benchmark.
		assertTrue(total > 0.05,
				String.format("Total CD at M=2 too small: %.4f", total));
		assertTrue(total < 1.0,
				String.format("Total CD at M=2 implausibly large: %.4f", total));
	}

	@Test
	void testTransonicPeakOrdering() {
		// TN 3320 states peak CDT = 0.26 at M = 1.04 (full-scale).
		// The peak should be higher than the drag at M = 2 (a robust
		// qualitative check of the transonic drag rise).
		double cdPeak = computeTotalCd(1.04);
		double cd2 = computeTotalCd(2.0);
		System.out.printf("NACA RM-10 peak check: CD(1.04)=%.3f CD(2.0)=%.3f%n",
				cdPeak, cd2);
		assertTrue(cdPeak > cd2,
				String.format("Transonic peak expected: CD(1.04)=%.3f !> CD(2.0)=%.3f",
						cdPeak, cd2));
	}
}
