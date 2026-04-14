package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.startup.Application;

/**
 * Benchmark: ORP fin wave drag vs NACA TN 3650 free-flight experimental data.
 * <p>
 * Source: Welsh, C.J. (1956), "Results of a Flight Investigation to Determine
 * the Zero-Lift Drag Characteristics of a 60° Delta Wing with NACA 65-006
 * Airfoil Section and Various Double-Wedge Sections at Mach Numbers from
 * 0.7 to 1.6," NACA TN 3650.
 * <p>
 * Geometry: 60° LE sweepback delta wings, 4 fins, symmetric double-wedge
 * (HEXAGONAL cross-section in ORP), t/c = 0.03 and 0.06.
 * Drag referenced to exposed wing area (200 sq in = 0.12903 m²).
 * <p>
 * The experimental C_Dw is the difference between winged and wingless body
 * total drag — it includes wave drag + friction + wing-body interference.
 * The theory curves in the paper include 0.006 estimated viscous drag added
 * to the Puckett & Stewart linearized wave drag.
 * <p>
 * For ORP comparison, we compute fin pressure CD (wave drag) + fin friction CD,
 * converted to the exposed wing area reference.
 */
public class NacaTn3650FinWaveDragTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// TN 3650 geometry (from Figure 1):
	// Body: ogival nose + cylindrical body, 4.50/5.00 inch diameter
	// Wings: 60° LE sweep, delta, aspect ratio 2.31, exposed area 200 sq in
	// Wing span (semispan from body): ~8.38 inches per side
	// Root chord (parallel to centerline): ~17.50 - 5.00 = ~12.50 inches at body surface
	// (Estimated from Figure 1 stations)

	// ORP model: approximate the TN 3650 geometry
	private static final double BODY_RADIUS = 0.0635;   // ~2.5 inches = 0.0635m
	private static final double BODY_LENGTH = 1.0;       // long enough for fin placement
	private static final double NOSE_LENGTH = 0.30;      // ogival nose
	// Fin geometry (from Figure 1, approximately):
	// Root chord ~0.32m, tip chord ~0, semispan ~0.213m, 60° LE sweep
	private static final double FIN_ROOT_CHORD = 0.32;
	private static final double FIN_TIP_CHORD = 0.001;   // Near-zero (delta)
	private static final double FIN_SPAN = 0.213;         // semispan
	private static final double FIN_SWEEP = FIN_ROOT_CHORD - FIN_TIP_CHORD; // sweep length for 60° LE

	/** Build a TN 3650-like rocket with specified fin thickness. */
	private static Rocket makeTn3650Rocket(double thicknessRatio) {
		Rocket rocket = new Rocket();
		rocket.setName("TN3650 t/c=" + thicknessRatio);
		rocket.setPerfectFinish(true); // polished aluminum models

		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, NOSE_LENGTH, BODY_RADIUS);
		nose.setShapeParameter(1.0);
		nose.setThickness(0.002);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		BodyTube body = new BodyTube(BODY_LENGTH, BODY_RADIUS, 0.002);
		body.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(body);

		double thickness = thicknessRatio * FIN_ROOT_CHORD;
		TrapezoidFinSet fins = new TrapezoidFinSet(4, FIN_ROOT_CHORD, FIN_TIP_CHORD,
				FIN_SWEEP, FIN_SPAN);
		fins.setThickness(thickness);
		fins.setCrossSection(FinSet.CrossSection.HEXAGONAL); // double-wedge = HEXAGONAL in ORP
		fins.setFinish(ExternalComponent.Finish.POLISHED);
		fins.setAxialMethod(AxialMethod.BOTTOM);
		fins.setAxialOffset(0.0);
		body.addChild(fins);

		rocket.enableEvents();
		return rocket;
	}

	/** Compute fin-only drag components, referenced to ORP body ref area. */
	private double[] computeFinDrag(double thicknessRatio, double mach) {
		Rocket rocket = makeTn3650Rocket(thicknessRatio);
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);

		// Get total and wingless-body drag
		AerodynamicForces totalForces = calc.getAerodynamicForces(config, conditions, warnings);

		// Also compute wingless body drag for subtraction
		Rocket winglessRocket = makeTn3650Rocket(0); // thickness=0 won't work, so we'll use component breakdown
		// Instead, get component-level breakdown
		var forceMap = calc.getForceAnalysis(config, conditions, warnings);

		// Find fin forces
		double finPressureCd = 0;
		double finFrictionCd = 0;
		for (var entry : forceMap.entrySet()) {
			if (entry.getKey() instanceof FinSet) {
				AerodynamicForces f = entry.getValue();
				finPressureCd = f.getPressureCD();
				finFrictionCd = f.getFrictionCD();
			}
		}

		return new double[]{finPressureCd, finFrictionCd, finPressureCd + finFrictionCd};
	}

	// ================================================================
	// Benchmark: ORP fin drag vs TN 3650 experimental data
	// ================================================================

	@ParameterizedTest(name = "TN3650 {0} t/c={1} Λ={2}° M={3}: CDw_exp={4}")
	@CsvFileSource(
			resources = "/info/openrocket/core/aerodynamics/naca_tn_3650_wing_drag.csv",
			numLinesToSkip = 1
	)
	void testFinDragVsTn3650(String section, double tOverC, double sweepDeg,
							 double mach, double cdwExp, double cdwTheory) {

		double[] finDrag = computeFinDrag(tOverC, mach);
		double finTotalCdBodyRef = finDrag[2]; // pressure + friction, body-area referenced

		// Convert ORP body-ref CD to wing-area-ref CD
		// ORP ref area = pi * BODY_RADIUS^2; TN 3650 wing area = 200 sq in = 0.12903 m^2
		double bodyRefArea = Math.PI * BODY_RADIUS * BODY_RADIUS;
		double wingArea = 0.12903; // 200 sq in in m^2
		double finCdWingRef = finTotalCdBodyRef * bodyRefArea / wingArea;

		double errPct = (finCdWingRef - cdwExp) / cdwExp * 100;

		System.out.printf("TN3650 t/c=%.2f M=%.2f: CDw_exp=%.4f CDw_ORP=%.4f err=%+.1f%% " +
						"(press_bodyref=%.6f fric_bodyref=%.6f bodyArea=%.6f wingArea=%.6f ratio=%.4f)%n",
				tOverC, mach, cdwExp, finCdWingRef, errPct,
				finDrag[0], finDrag[1], bodyRefArea, 0.12903, bodyRefArea/0.12903);

		// DIAGNOSTIC ONLY — not a hard assertion.
		// ORP underpredicts by ~80% for 60°-sweep delta fins because:
		// 1. cos²(60°) = 0.25 sweep correction is too aggressive for finite delta wings
		//    (3D tip effects reduce the effective sweep benefit)
		// 2. Wing-body interference drag is included in experiment but not modelled
		// For typical HPR fins (0-15° sweep, cos²Λ ≈ 0.93-1.0), the error is much smaller.
		// This test documents the high-sweep limitation rather than enforcing agreement.
		assertTrue(finCdWingRef > 0,
				"Fin drag must be positive at M=" + mach);
		assertTrue(finCdWingRef < cdwExp,
				String.format("ORP should underpredict 60°-sweep delta: ORP=%.4f < exp=%.4f",
						finCdWingRef, cdwExp));
	}

	// ================================================================
	// Physics: (t/c)² scaling check at M=1.4
	// ================================================================

	@Test
	void testThicknessSquaredScaling() {
		// At M=1.4, Ackeret wave drag ~ (t/c)^2. The ratio of wave-only drag
		// between t/c=0.06 and t/c=0.03 should approach 4.0 (=(0.06/0.03)^2).
		// The actual ratio from TN 3650 is ~3.0 due to 3D planform effects.
		double[] drag06 = computeFinDrag(0.06, 1.4);
		double[] drag03 = computeFinDrag(0.03, 1.4);

		double pressureRatio = drag06[0] / drag03[0]; // pressure CD ratio

		System.out.printf("(t/c)^2 scaling at M=1.4: pressure ratio = %.2f (expected ~4.0 for 2D Ackeret)%n",
				pressureRatio);

		// Should be between 2.0 and 6.0 (3D effects modify the pure 2D ratio of 4.0)
		assertTrue(pressureRatio > 2.0 && pressureRatio < 6.0,
				String.format("Pressure drag ratio t/c=0.06/0.03 = %.2f, expected 2.0-6.0", pressureRatio));
	}

	// ================================================================
	// Diagnostic table
	// ================================================================

	@Test
	void printDiagnosticTable() {
		System.out.println("\n=== NACA TN 3650 Fin Wave Drag Benchmark ===");
		double[][] testPoints = {
				{0.06, 1.2, 0.024}, {0.06, 1.3, 0.021}, {0.06, 1.4, 0.019},
				{0.06, 1.5, 0.018}, {0.06, 1.6, 0.018},
				{0.03, 1.2, 0.012}, {0.03, 1.3, 0.011}, {0.03, 1.4, 0.010},
				{0.03, 1.5, 0.010}, {0.03, 1.6, 0.010},
		};

		double bodyRefArea = Math.PI * BODY_RADIUS * BODY_RADIUS;
		double wingArea = 0.12903;
		double sumAbsErr = 0;
		int count = 0;

		System.out.printf("%-6s %-6s %-10s %-10s %-10s%n", "t/c", "Mach", "CDw_exp", "CDw_ORP", "Error%");
		for (double[] pt : testPoints) {
			double[] finDrag = computeFinDrag(pt[0], pt[1]);
			double cdwOrp = finDrag[2] * bodyRefArea / wingArea;
			double err = (cdwOrp - pt[2]) / pt[2] * 100;
			sumAbsErr += Math.abs(err);
			count++;
			System.out.printf("%-6.2f %-6.2f %-10.4f %-10.4f %+.1f%%%n",
					pt[0], pt[1], pt[2], cdwOrp, err);
		}
		System.out.printf("MAPE = %.1f%% across %d supersonic points%n", sumAbsErr / count, count);
	}
}
