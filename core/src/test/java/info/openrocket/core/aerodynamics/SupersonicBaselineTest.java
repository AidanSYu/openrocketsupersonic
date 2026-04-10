package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.CoordinateIF;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * Regression test suite for aerodynamic coefficients across the full Mach range.
 * <p>
 * These tests capture the current OpenRocket outputs as baselines. When supersonic
 * models are upgraded, the baselines should be updated to reflect the new (better)
 * values. Until then, they guard against unintentional regressions.
 * <p>
 * Tolerance is intentionally loose (5-10%) to allow minor refactoring without
 * breaking tests, while still catching major regressions.
 */
public class SupersonicBaselineTest {

	private static final double TOLERANCE_CD = 0.10;    // 10% relative tolerance for drag
	private static final double TOLERANCE_CNA = 0.10;   // 10% relative tolerance for CNa
	private static final double TOLERANCE_CP = 0.005;    // 5mm absolute tolerance for CP position
	private static final double AOA_RAD = Math.toRadians(2.0); // 2 degree AoA for stability tests

	private static Injector injector;

	@BeforeAll
	public static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ==================== Cone-Cylinder Baselines ====================

	/**
	 * Total CD vs Mach for cone-cylinder (no fins).
	 * Baseline values captured from current OpenRocket Barrowman calculator.
	 * These will be replaced with RASAero/analytical targets as phases complete.
	 */
	@ParameterizedTest(name = "Cone-Cylinder CD at M{0}")
	@CsvSource({
			"0.3,  0.303597",
			"0.5,  0.358370",
			"0.9,  0.482754",
			"1.1,  0.696012",
			"1.5,  0.450111",
			"2.0,  0.361089",
			"3.0,  0.266294",
			"5.0,  0.187796",
	})
	public void testConeCylinderCd(double mach, double expectedCD) {
		double cd = computeTotalCD(SupersonicTestRockets.makeConeCylinder(), mach);
		assertRelativeEquals("Cone-Cylinder CD at M" + mach, expectedCD, cd, TOLERANCE_CD);
	}

	@ParameterizedTest(name = "Ogive-Cylinder CD at M{0}")
	@CsvSource({
			"0.3,  0.310111",
			"0.5,  0.366172",
			"0.9,  0.481411",
			"1.1,  0.544110",
			"1.5,  0.352605",
			"2.0,  0.332594",
			"3.0,  0.268111",
			"5.0,  0.198488",
	})
	public void testOgiveCylinderCd(double mach, double expectedCD) {
		double cd = computeTotalCD(SupersonicTestRockets.makeOgiveCylinder(), mach);
		assertRelativeEquals("Ogive-Cylinder CD at M" + mach, expectedCD, cd, TOLERANCE_CD);
	}

	@ParameterizedTest(name = "Cone-Cylinder-Fins CD at M{0}")
	@CsvSource({
			"0.3,  0.545927",
			"0.5,  0.660039",
			"0.9,  0.772317",
			"1.1,  1.007036",
			"1.5,  0.765996",
			"2.0,  0.684093",
			"3.0,  0.592231",
			"5.0,  0.512342",
	})
	public void testConeCylinderFinsCd(double mach, double expectedCD) {
		double cd = computeTotalCD(SupersonicTestRockets.makeConeCylinderFins(), mach);
		assertRelativeEquals("Cone-Cylinder-Fins CD at M" + mach, expectedCD, cd, TOLERANCE_CD);
	}

	@ParameterizedTest(name = "Ogive-Boattail-Fins CD at M{0}")
	@CsvSource({
			"0.3,  0.450690",
			"0.5,  0.508586",
			"0.9,  0.588382",
			"1.1,  0.679698",
			"1.5,  0.561085",
			"2.0,  0.577892",
			"3.0,  0.540661",
			"5.0,  0.478152",
	})
	public void testOgiveBoattailFinsCd(double mach, double expectedCD) {
		double cd = computeTotalCD(SupersonicTestRockets.makeOgiveBoattailFins(), mach);
		assertRelativeEquals("Ogive-Boattail-Fins CD at M" + mach, expectedCD, cd, TOLERANCE_CD);
	}

	@ParameterizedTest(name = "VonKarman-Fins CD at M{0}")
	@CsvSource({
			"0.3,  0.328014",
			"0.5,  0.401862",
			"0.9,  0.659554",
			"1.1,  0.729573",
			"1.5,  0.627805",
			"2.0,  0.548577",
			"3.0,  0.457403",
			"5.0,  0.384010",
	})
	public void testVonKarmanFinsCd(double mach, double expectedCD) {
		double cd = computeTotalCD(SupersonicTestRockets.makeVonKarmanFins(), mach);
		assertRelativeEquals("VonKarman-Fins CD at M" + mach, expectedCD, cd, TOLERANCE_CD);
	}

	// ==================== Stability Baselines ====================

	@ParameterizedTest(name = "Cone-Cylinder-Fins CNa at M{0}")
	@CsvSource({
			"0.3,  8.471678",
			"1.0,  8.378323",
			"1.5,  6.541390",
			"2.0,  5.159139",
			"3.0,  4.204980",
			"5.0,  3.591265",
	})
	public void testConeCylinderFinsCNa(double mach, double expectedCNa) {
		double cna = computeTotalCNa(SupersonicTestRockets.makeConeCylinderFins(), mach);
		assertRelativeEquals("Cone-Cylinder-Fins CNa at M" + mach, expectedCNa, cna, TOLERANCE_CNA);
	}

	// Phase 3a: CP baselines updated for supersonic body CP aft-shift correction.
	// CP moves aft at M>1.3 due to crossflow effects (Allen & Perkins).
	// Phase 6: PNK fin-body interference and Jorgensen crossflow correction shifted
	// CP values; baselines updated to reflect new model outputs.
	@ParameterizedTest(name = "Ogive-Boattail-Fins CP at M{0}")
	@CsvSource({
			"0.3,  0.443436",
			"1.0,  0.450844",
			"1.5,  0.390712",
			"2.0,  0.297561",
			"3.0,  0.189893",
			"5.0,  0.096662",
	})
	public void testOgiveBoattailFinsCPx(double mach, double expectedCPx) {
		double cpx = computeTotalCPx(SupersonicTestRockets.makeOgiveBoattailFins(), mach);
		assertEquals(expectedCPx, cpx, TOLERANCE_CP,
				String.format("Ogive-Boattail-Fins CP at M%.1f", mach));
	}

	// ==================== Sanity Checks ====================

	/**
	 * Verify that CD is non-negative at all Mach numbers.
	 */
	@Test
	public void testCdNonNegativeAllGeometries() {
		Rocket[] rockets = {
			SupersonicTestRockets.makeConeCylinder(),
			SupersonicTestRockets.makeOgiveCylinder(),
			SupersonicTestRockets.makeConeCylinderFins(),
			SupersonicTestRockets.makeOgiveBoattailFins(),
			SupersonicTestRockets.makeVonKarmanFins(),
		};
		double[] machs = { 0.3, 0.5, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.5, 2.0, 3.0, 5.0 };

		for (Rocket rocket : rockets) {
			for (double mach : machs) {
				double cd = computeTotalCD(rocket, mach);
				assertTrue(cd >= 0,
						String.format("%s has negative CD (%.6f) at M%.2f", rocket.getName(), cd, mach));
				assertFalse(Double.isNaN(cd),
						String.format("%s has NaN CD at M%.2f", rocket.getName(), mach));
				assertFalse(Double.isInfinite(cd),
						String.format("%s has infinite CD at M%.2f", rocket.getName(), mach));
			}
		}
	}

	/**
	 * Verify CD increases through the transonic regime (drag rise).
	 * CD at M=1.1 should be greater than CD at M=0.5 for all geometries.
	 */
	@Test
	public void testTransonicDragRise() {
		Rocket[] rockets = {
			SupersonicTestRockets.makeConeCylinder(),
			SupersonicTestRockets.makeOgiveCylinder(),
			SupersonicTestRockets.makeConeCylinderFins(),
			SupersonicTestRockets.makeOgiveBoattailFins(),
			SupersonicTestRockets.makeVonKarmanFins(),
		};

		for (Rocket rocket : rockets) {
			double cdSubsonic = computeTotalCD(rocket, 0.5);
			double cdTransonic = computeTotalCD(rocket, 1.1);
			assertTrue(cdTransonic > cdSubsonic,
					String.format("%s: CD at M1.1 (%.6f) should exceed CD at M0.5 (%.6f)",
							rocket.getName(), cdTransonic, cdSubsonic));
		}
	}

	/**
	 * Verify that CP is aft of nose tip for finned rockets (positive stability).
	 */
	@Test
	public void testCpPositionSanity() {
		Rocket[] rockets = {
			SupersonicTestRockets.makeConeCylinderFins(),
			SupersonicTestRockets.makeOgiveBoattailFins(),
			SupersonicTestRockets.makeVonKarmanFins(),
		};
		double[] machs = { 0.3, 1.0, 1.5, 2.0, 3.0, 5.0 };

		for (Rocket rocket : rockets) {
			for (double mach : machs) {
				double cpx = computeTotalCPx(rocket, mach);
				assertTrue(cpx > 0,
						String.format("%s: CP (%.4f) should be aft of nose tip at M%.1f",
								rocket.getName(), cpx, mach));
				assertFalse(Double.isNaN(cpx),
						String.format("%s has NaN CP at M%.1f", rocket.getName(), mach));
			}
		}
	}

	/**
	 * Verify that ogive produces less wave drag than cone at supersonic speeds
	 * (for the same fineness ratio / dimensions).
	 * Compares total CD with a small tolerance to account for the slightly larger
	 * ogive wetted area which adds friction drag.
	 */
	@Test
	public void testOgiveLessDragThanCone() {
		double[] supersonicMachs = { 1.5, 2.0, 3.0 };

		for (double mach : supersonicMachs) {
			double coneCd = computeTotalCD(SupersonicTestRockets.makeConeCylinder(), mach);
			double ogiveCd = computeTotalCD(SupersonicTestRockets.makeOgiveCylinder(), mach);
			double tolerance = 0.01; // small tolerance for wetted area difference
			assertTrue(ogiveCd <= coneCd + tolerance,
					String.format("Ogive CD (%.6f) should be <= Cone CD (%.6f) + %.3f at M%.1f",
							ogiveCd, coneCd, tolerance, mach));
		}
	}

	/**
	 * Verify CD is smooth through the transonic region — no huge jumps
	 * between adjacent Mach points.
	 */
	@Test
	public void testTransonicSmoothness() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		double[] machs = { 0.8, 0.85, 0.9, 0.95, 1.0, 1.05, 1.1, 1.15, 1.2, 1.3, 1.5 };

		double prevCd = computeTotalCD(rocket, machs[0]);
		for (int i = 1; i < machs.length; i++) {
			double cd = computeTotalCD(rocket, machs[i]);
			double jump = Math.abs(cd - prevCd);
			// Allow up to 100% change between adjacent 0.05 Mach steps in transonic,
			// but flag truly discontinuous jumps
			assertTrue(jump < prevCd * 2.0 + 0.1,
					String.format("CD jump too large between M%.2f (%.6f) and M%.2f (%.6f), delta=%.6f",
							machs[i - 1], prevCd, machs[i], cd, jump));
			prevCd = cd;
		}
	}

	/**
	 * CSV export smoke test — verify we can generate output without errors.
	 */
	@Test
	public void testCsvExportRuns() throws Exception {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		List<AeroCoeffientExporter.ComponentAeroData> data =
				AeroCoeffientExporter.machSweep(rocket, 0.3, 5.0, 0.5, AOA_RAD);

		assertFalse(data.isEmpty(), "Mach sweep produced no data");

		StringWriter writer = new StringWriter();
		AeroCoeffientExporter.writeCsv(data, writer);
		String csv = writer.toString();

		assertTrue(csv.contains("Mach,"), "CSV should have header");
		assertTrue(csv.contains("Conical Nose"), "CSV should contain nose cone data");
		assertTrue(csv.contains("4 Fin Set"), "CSV should contain fin data");

		// Print to console for manual inspection during development
		System.out.println("\n=== Cone-Cylinder-Fins Mach Sweep (totals) ===");
		System.out.println("Mach     FrictionCD  PressureCD  BaseCD    TotalCD   CNa       CP_x");
		for (AeroCoeffientExporter.ComponentAeroData d : AeroCoeffientExporter.filterTotalOnly(data)) {
			System.out.printf("%.1f      %.6f  %.6f  %.6f  %.6f  %.6f  %.6f%n",
					d.mach, d.frictionCD, d.pressureCD, d.baseCD, d.totalCD, d.cna, d.cpX);
		}
	}

	// ==================== Phase 2 Validation Gate ====================

	/**
	 * Validation gate: no Cd discontinuities across regime transitions.
	 * <p>
	 * Verify that dCd/dM is bounded everywhere from M 0.3-5.0.
	 * A bounded derivative means no discontinuous jumps at transonic boundaries.
	 * The maximum allowable |dCd/dM| is set generously (5.0) to allow the
	 * steep transonic drag rise while catching true discontinuities.
	 * <p>
	 * Split into per-rocket tests so JUnit 5 can execute them in parallel.
	 */
	@Test
	public void testDCdDMachBounded_ConeCylinder() {
		assertDCdDMachBounded(SupersonicTestRockets.makeConeCylinder());
	}

	@Test
	public void testDCdDMachBounded_OgiveCylinder() {
		assertDCdDMachBounded(SupersonicTestRockets.makeOgiveCylinder());
	}

	@Test
	public void testDCdDMachBounded_ConeCylinderFins() {
		assertDCdDMachBounded(SupersonicTestRockets.makeConeCylinderFins());
	}

	@Test
	public void testDCdDMachBounded_OgiveBoattailFins() {
		assertDCdDMachBounded(SupersonicTestRockets.makeOgiveBoattailFins());
	}

	@Test
	public void testDCdDMachBounded_VonKarmanFins() {
		assertDCdDMachBounded(SupersonicTestRockets.makeVonKarmanFins());
	}

	private void assertDCdDMachBounded(Rocket rocket) {
		double step = 0.02;
		double maxAllowedSlope = 5.0; // max |dCd/dM| per unit Mach

		double prevCd = computeTotalCD(rocket, 0.3);
		for (double m = 0.3 + step; m <= 5.0 + 0.001; m += step) {
			double cd = computeTotalCD(rocket, m);
			double slope = Math.abs(cd - prevCd) / step;
			assertTrue(slope < maxAllowedSlope,
					String.format("%s: |dCd/dM| = %.2f at M=%.2f exceeds limit %.1f (Cd went %.6f -> %.6f)",
							rocket.getName(), slope, m, maxAllowedSlope, prevCd, cd));
			prevCd = cd;
		}
	}

	/**
	 * Validation gate: Cd should decrease after the transonic peak.
	 * For all geometries, Cd at M=3.0 should be less than Cd at M=1.1.
	 */
	@Test
	public void testSupersonicCdDecay() {
		Rocket[] rockets = {
			SupersonicTestRockets.makeConeCylinder(),
			SupersonicTestRockets.makeOgiveCylinder(),
			SupersonicTestRockets.makeConeCylinderFins(),
			SupersonicTestRockets.makeOgiveBoattailFins(),
			SupersonicTestRockets.makeVonKarmanFins(),
		};

		for (Rocket rocket : rockets) {
			double cdPeak = computeTotalCD(rocket, 1.1);
			double cdM3 = computeTotalCD(rocket, 3.0);
			assertTrue(cdM3 < cdPeak,
					String.format("%s: Cd at M3 (%.6f) should be < Cd at M1.1 (%.6f)",
							rocket.getName(), cdM3, cdPeak));
		}
	}

	/**
	 * Validation gate: ogive produces less wave drag than cone at same dimensions.
	 * At very high Mach (M5), the ogive's larger wetted area adds friction drag
	 * that can offset its wave drag advantage, so use a generous tolerance.
	 */
	@Test
	public void testOgiveLessDragThanConeHighMach() {
		double cdCone = computeTotalCD(SupersonicTestRockets.makeConeCylinder(), 5.0);
		double cdOgive = computeTotalCD(SupersonicTestRockets.makeOgiveCylinder(), 5.0);
		assertTrue(cdOgive <= cdCone + 0.02,
				String.format("Ogive CD (%.6f) should be <= Cone CD (%.6f) + 0.02 at M5", cdOgive, cdCone));
	}

	// ==================== Baseline Capture Utility ====================

	/**
	 * Not a test — utility to capture current baselines for all geometries.
	 * Run this manually to generate baseline values for the parameterized tests above.
	 */
	@Test
	public void captureBaselines() {
		System.out.println("\n============================================");
		System.out.println("  BASELINE CAPTURE — Current OpenRocket Output");
		System.out.println("============================================\n");

		captureRocketBaseline("Cone-Cylinder", SupersonicTestRockets.makeConeCylinder());
		captureRocketBaseline("Ogive-Cylinder", SupersonicTestRockets.makeOgiveCylinder());
		captureRocketBaseline("Cone-Cylinder-Fins", SupersonicTestRockets.makeConeCylinderFins());
		captureRocketBaseline("Ogive-Boattail-Fins", SupersonicTestRockets.makeOgiveBoattailFins());
		captureRocketBaseline("VonKarman-Fins", SupersonicTestRockets.makeVonKarmanFins());
	}

	private void captureRocketBaseline(String name, Rocket rocket) {
		double[] machs = { 0.3, 0.5, 0.8, 0.9, 1.0, 1.1, 1.5, 2.0, 3.0, 5.0 };

		System.out.printf("--- %s ---%n", name);
		System.out.printf("%-6s  %-10s  %-10s  %-10s  %-10s  %-10s  %-10s%n",
				"Mach", "FrictionCD", "PressureCD", "BaseCD", "TotalCD", "CNa", "CP_x");

		for (double mach : machs) {
			FlightConfiguration config = rocket.getSelectedConfiguration();
			BarrowmanCalculator calc = new BarrowmanCalculator();
			FlightConditions conditions = new FlightConditions(config);
			conditions.setMach(mach);
			conditions.setAOA(AOA_RAD);
			WarningSet warnings = new WarningSet();

			AerodynamicForces total = calc.getAerodynamicForces(config, conditions, warnings);
			Map<RocketComponent, AerodynamicForces> forceMap = calc.getForceAnalysis(config, conditions, warnings);
			AerodynamicForces rocketForces = forceMap.get(rocket);

			double frictionCD = rocketForces != null ? nanToZero(rocketForces.getFrictionCD()) : 0;
			double pressureCD = rocketForces != null ? nanToZero(rocketForces.getPressureCD()) : 0;
			double baseCD = rocketForces != null ? nanToZero(rocketForces.getBaseCD()) : 0;
			double totalCD = nanToZero(total.getCD());
			double cna = 0;
			double cpX = 0;
			CoordinateIF cp = total.getCP();
			if (cp != null) {
				cna = cp.getWeight();
				cpX = cp.getX();
			}

			System.out.printf("%-6.1f  %-10.6f  %-10.6f  %-10.6f  %-10.6f  %-10.6f  %-10.6f%n",
					mach, frictionCD, pressureCD, baseCD, totalCD, cna, cpX);
		}
		System.out.println();
	}

	// ==================== Helpers ====================

	private double computeTotalCD(Rocket rocket, double mach) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		WarningSet warnings = new WarningSet();

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
		return forces.getCD();
	}

	private double computeTotalCNa(Rocket rocket, double mach) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(AOA_RAD);
		WarningSet warnings = new WarningSet();

		CoordinateIF cp = calc.getCP(config, conditions, warnings);
		return cp != null ? cp.getWeight() : 0;
	}

	private double computeTotalCPx(Rocket rocket, double mach) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(AOA_RAD);
		WarningSet warnings = new WarningSet();

		CoordinateIF cp = calc.getCP(config, conditions, warnings);
		return cp != null ? cp.getX() : Double.NaN;
	}

	private static void assertRelativeEquals(String message, double expected, double actual, double relativeTolerance) {
		double absTolerance = Math.abs(expected) * relativeTolerance + 1e-6; // add small absolute floor
		assertEquals(expected, actual, absTolerance,
				String.format("%s: expected=%.6f actual=%.6f (tolerance=%.1f%%)",
						message, expected, actual, relativeTolerance * 100));
	}

	private static double nanToZero(double v) {
		return Double.isNaN(v) ? 0.0 : v;
	}
}
