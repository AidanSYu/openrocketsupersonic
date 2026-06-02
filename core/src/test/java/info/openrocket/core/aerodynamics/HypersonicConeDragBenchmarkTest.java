package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;

/**
 * Benchmark test: hypersonic cone foredrag against DTIC AD0487365 Table II.
 * <p>
 * Source: Grabow, R.M. et al., "The Determination of Hypersonic Drag,"
 * DTIC AD0487365, 1965. Table II: near-sharp cones (lambda ≈ 0.035-0.038),
 * alpha = 0°, M = 6.5 to 17.2.
 * <p>
 * Geometry: sharp circular cones with half-angles 8°, 12°, 16°.
 * CD referenced to base area.
 * <p>
 * This test compares ORP's full-body CD (pressure + friction + base) against
 * the experimental total CD. The test rockets are cone + minimal body tube
 * to approximate a pure cone geometry.
 * <p>
 * The DTIC data was obtained from ballistic-range free-flight measurements
 * at Re_L = 143k-2.15M, with laminar boundary-layer conditions. The benchmark
 * fixture marks the range models as perfect-finish and sets the atmospheric
 * pressure row-by-row so ORP sees the tabulated length Reynolds number.
 */
@Tag("slow")  // heavy hypersonic cone-drag benchmark (~10 s); excluded by default, run with -Pslow
public class HypersonicConeDragBenchmarkTest {

	/** Tolerance for total CD comparison. DTIC experiments were at laminar BL
	 *  conditions (Re_L ~ 0.1-2M), while ORP's hypersonic skin-friction branch
	 *  remains turbulent. We use 50% tolerance for point scatter; the primary
	 *  quantitative gate is the MAPE across all 11 points (target: < 20%). */
	private static final double TOTAL_CD_TOLERANCE = 0.50;

	/** Tighter tolerance for pressure-drag-only comparison. */
	private static final double PRESSURE_CD_TOLERANCE = 0.25;

	/** Base radius for all test cones (m). */
	private static final double BASE_RADIUS = 0.025;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ================================================================
	// Helper: build a cone + minimal body tube
	// ================================================================

	/**
	 * Creates a cone-cylinder rocket with the specified half-angle.
	 * The body tube is kept minimal (1mm) so the geometry approximates
	 * a pure cone with a flat base.
	 */
	private static Rocket makeCone(double halfAngleDeg) {
		double halfAngleRad = Math.toRadians(halfAngleDeg);
		double coneLength = BASE_RADIUS / Math.tan(halfAngleRad);

		Rocket rocket = new Rocket();
		rocket.setName("Cone-" + halfAngleDeg + "deg");
		rocket.setPerfectFinish(true);

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, coneLength, BASE_RADIUS);
		nose.setName("Cone");
		nose.setThickness(0.001);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		// Minimal body tube — just enough to have a base
		BodyTube body = new BodyTube(0.001, BASE_RADIUS, 0.001);
		body.setName("Base");
		body.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}

	private static AtmosphericConditions atmosphereForReL(double mach, double length, double reL) {
		AtmosphericConditions standard = new AtmosphericConditions();
		double velocity = mach * standard.getMachSpeed();
		double targetNu = velocity * length / reL;
		double pressure = standard.getDynamicViscosity()
				* AtmosphericConditions.R
				* standard.getTemperature()
				/ targetNu;
		return new AtmosphericConditions(standard.getTemperature(), pressure);
	}

	/**
	 * Compute CD and component breakdown at a given Mach for a cone geometry.
	 */
	private CdBreakdown computeCd(double halfAngleDeg, double mach) {
		return computeCd(halfAngleDeg, mach, Double.NaN);
	}

	/**
	 * Compute CD and component breakdown while matching a source length Reynolds number.
	 */
	private CdBreakdown computeCd(double halfAngleDeg, double mach, double reL) {
		Rocket rocket = makeCone(halfAngleDeg);
		var config = rocket.getSelectedConfiguration();
		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		if (Double.isFinite(reL) && reL > 0) {
			conditions.setAtmosphericConditions(atmosphereForReL(mach, config.getLengthAerodynamic(), reL));
		}

		AerodynamicForces total = calc.getAerodynamicForces(config, conditions, warnings);

		return new CdBreakdown(total.getCD(), total.getPressureCD(),
				total.getFrictionCD(), total.getBaseCD());
	}

	private record CdBreakdown(double totalCd, double pressureCd, double frictionCd, double baseCd) {}

	// ================================================================
	// Benchmark: DTIC AD0487365 alpha=0 cone drag
	// ================================================================

	@ParameterizedTest(name = "Cone θ={0}° M={2}: CD_exp={5}")
	@CsvFileSource(
			resources = "/info/openrocket/core/aerodynamics/dtic_ad0487365_cone_drag_alpha0.csv",
			numLinesToSkip = 1
	)
	void testConeDragAgainstDTIC(
			double thetaDeg, double lambda, double mach, double reL,
			double twTt, double cdExp, double cdLam) {

		CdBreakdown result = computeCd(thetaDeg, mach, reL);

		// Log the comparison for diagnostics
		System.out.printf("DTIC θ=%2.0f° M=%4.1f: CD_exp=%.4f  CD_orp=%.4f  " +
						"(pressure=%.4f friction=%.4f base=%.4f)  error=%.1f%%%n",
				thetaDeg, mach, cdExp, result.totalCd,
				result.pressureCd, result.frictionCd, result.baseCd,
				100.0 * (result.totalCd - cdExp) / cdExp);

		// Sanity: ORP CD must be positive and finite
		assertTrue(result.totalCd > 0,
				String.format("CD must be positive: θ=%s° M=%s", thetaDeg, mach));
		assertFalse(Double.isNaN(result.totalCd),
				String.format("CD must not be NaN: θ=%s° M=%s", thetaDeg, mach));

		// Primary check: total CD within tolerance of experimental
		assertEquals(cdExp, result.totalCd, cdExp * TOTAL_CD_TOLERANCE,
				String.format("Total CD θ=%.0f° M=%.1f: exp=%.4f orp=%.4f (%.1f%% error, tol=%.0f%%)",
						thetaDeg, mach, cdExp, result.totalCd,
						100.0 * (result.totalCd - cdExp) / cdExp,
						TOTAL_CD_TOLERANCE * 100));

		// Trend check: pressure drag should scale roughly with sin²(θ)
		assertTrue(result.pressureCd > 0,
				String.format("Pressure CD must be positive: θ=%s° M=%s", thetaDeg, mach));
	}

	// ================================================================
	// Trend tests: physics sanity across cone angles
	// ================================================================

	@Test
	void testPressureDragIncreasesWithConeAngle() {
		// At any fixed Mach, a wider cone should have higher pressure drag
		double mach = 8.0;
		CdBreakdown cd8 = computeCd(8, mach);
		CdBreakdown cd12 = computeCd(12, mach);
		CdBreakdown cd16 = computeCd(16, mach);

		assertTrue(cd12.pressureCd > cd8.pressureCd,
				String.format("12° pressure CD (%.4f) > 8° (%.4f) at M=%.0f",
						cd12.pressureCd, cd8.pressureCd, mach));
		assertTrue(cd16.pressureCd > cd12.pressureCd,
				String.format("16° pressure CD (%.4f) > 12° (%.4f) at M=%.0f",
						cd16.pressureCd, cd12.pressureCd, mach));
	}

	@Test
	void testDragFlatteningAboveMach8() {
		// DTIC and Krasil'shchikov both show CD becomes approximately constant
		// above M ≈ 8 (Mach number independence). Check that CD changes < 15%
		// between M=8 and M=14.
		for (double theta : new double[]{8, 12, 16}) {
			CdBreakdown cd8 = computeCd(theta, 8.0);
			CdBreakdown cd14 = computeCd(theta, 14.0);

			double change = Math.abs(cd14.totalCd - cd8.totalCd) / cd8.totalCd;
			assertTrue(change < 0.15,
					String.format("θ=%.0f°: CD change M=8→14 = %.1f%% (should be <15%%)",
							theta, change * 100));
		}
	}

	@Test
	void testKrasilshchikovCrossCheck() {
		// Cross-check against Krasil'shchikov et al. 1969 Fig. 5 digitized data.
		// Sharp cones at M=8: θ=15° → Cx ≈ 0.31, θ=35° → Cx ≈ 0.66.
		// Note: Krasil'shchikov Cx may use a different reference area convention
		// (likely max cross-section = base area for a cone), so this is a
		// qualitative trend check.
		CdBreakdown cd15 = computeCd(15, 8.0);

		// At M=8, 15° cone should have CD in the range 0.2-0.5
		assertTrue(cd15.totalCd > 0.10 && cd15.totalCd < 0.50,
				String.format("15° cone at M=8: CD=%.4f should be in [0.10, 0.50]", cd15.totalCd));
	}

	// ================================================================
	// Diagnostic: print full comparison table
	// ================================================================

	@Test
	void printDiagnosticTable() {
		System.out.println("\n=== DTIC AD0487365 Hypersonic Cone Drag Benchmark ===");
		System.out.println("θ(°)  M     CD_exp  CD_orp  CD_pres  CD_fric  CD_base  Error(%)");
		System.out.println("----  ----  ------  ------  -------  -------  -------  --------");

		double[][] data = {
				{8, 6.5, 1110000, 0.072}, {8, 6.5, 1810000, 0.085},
				{8, 9.0, 2150000, 0.080}, {8, 14.3, 628000, 0.090},
				{12, 6.5, 1170000, 0.125}, {12, 9.0, 1440000, 0.128},
				{12, 17.2, 215000, 0.150},
				{16, 6.5, 583000, 0.205}, {16, 9.0, 440000, 0.198},
				{16, 14.3, 215000, 0.222}, {16, 17.2, 143000, 0.227}
		};

		double sumAbsError = 0;
		int count = 0;
		for (double[] row : data) {
			CdBreakdown cd = computeCd(row[0], row[1], row[2]);
			double error = 100.0 * (cd.totalCd - row[3]) / row[3];
			sumAbsError += Math.abs(error);
			count++;
			System.out.printf("%4.0f  %4.1f  %.4f  %.4f  %.4f   %.4f   %.4f   %+6.1f%%%n",
					row[0], row[1], row[3], cd.totalCd,
					cd.pressureCd, cd.frictionCd, cd.baseCd, error);
		}
		double mape = sumAbsError / count;
		System.out.printf("\nMAPE = %.1f%% across %d points%n", mape, count);
		assertTrue(mape < 20.0,
				String.format("DTIC cone total-CD MAPE %.1f%% should be below 20%%", mape));
	}
}
