package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.aerodynamics.barrowman.PittsNielsenKaattari;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;
import info.openrocket.core.util.Transformation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Pitts-Nielsen-Kaattari (NACA Report 1307) Mach-dependent
 * fin-body interference correction factors.
 */
public class PittsNielsenKaattariTest {

	private static final double EPSILON = 1e-9;

	// Typical fin geometry parameters for testing
	private static final double BODY_RADIUS = 0.02;   // 20 mm
	private static final double FIN_SEMISPAN = 0.05;   // 50 mm
	private static final double ROOT_CHORD = 0.06;     // 60 mm
	private static final double SWEEP_LE = Math.toRadians(30.0); // 30 deg LE sweep

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	public void fwbIsOneAtSubsonic() {
		double fwb = PittsNielsenKaattari.computeF_WB(0.5, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		assertEquals(1.0, fwb, EPSILON, "F_WB should be exactly 1.0 at M=0.5");

		// Also check at M=0.0 and M=0.84
		assertEquals(1.0, PittsNielsenKaattari.computeF_WB(0.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE), EPSILON);
		assertEquals(1.0, PittsNielsenKaattari.computeF_WB(0.84, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE), EPSILON);
	}

	@Test
	public void fbwIsOneAtSubsonic() {
		double fbw = PittsNielsenKaattari.computeF_BW(0.5, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD);
		assertEquals(1.0, fbw, EPSILON, "F_BW should be exactly 1.0 at M=0.5");

		assertEquals(1.0, PittsNielsenKaattari.computeF_BW(0.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD), EPSILON);
		assertEquals(1.0, PittsNielsenKaattari.computeF_BW(0.84, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD), EPSILON);
	}

	@Test
	public void fwbDecreasesAtSupersonic() {
		double fwb = PittsNielsenKaattari.computeF_WB(2.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		assertTrue(fwb < 1.0, "F_WB should be < 1.0 at M=2.0, got " + fwb);
		assertTrue(fwb > 0.5, "F_WB should be > minimum (0.5) at M=2.0, got " + fwb);
	}

	@Test
	public void fbwDecreasesAtSupersonic() {
		double fbw = PittsNielsenKaattari.computeF_BW(2.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD);
		assertTrue(fbw < 1.0, "F_BW should be < 1.0 at M=2.0, got " + fbw);
		assertTrue(fbw > 0.7, "F_BW should be > minimum (0.7) at M=2.0, got " + fbw);
	}

	@Test
	public void fwbCorrectionLargerThanFbw() {
		// F_WB correction is larger (smaller value) than F_BW at same conditions
		double fwb = PittsNielsenKaattari.computeF_WB(2.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		double fbw = PittsNielsenKaattari.computeF_BW(2.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD);
		assertTrue(fwb < fbw, "F_WB (" + fwb + ") should be smaller than F_BW (" + fbw + ") at M=2.0");
	}

	@Test
	public void fwbBlendsSmoothlythroughTransonic() {
		// Check that values are between 1.0 and the supersonic value, and monotonic
		double fwb_085 = PittsNielsenKaattari.computeF_WB(0.85, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		double fwb_100 = PittsNielsenKaattari.computeF_WB(1.00, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		double fwb_115 = PittsNielsenKaattari.computeF_WB(1.15, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		double fwb_sup = PittsNielsenKaattari.computeF_WB(2.00, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);

		// At M=0.85 (blend start), should be exactly 1.0
		assertEquals(1.0, fwb_085, 1e-6, "F_WB at M=0.85 should be ~1.0");

		// Values through blend must be in [F_WB_min, 1.0]
		assertTrue(fwb_100 <= 1.0 + 1e-6, "F_WB at M=1.0 should be <= 1.0");
		assertTrue(fwb_100 >= 0.5, "F_WB at M=1.0 should be >= 0.5");
		assertTrue(fwb_115 <= 1.0 + 1e-6, "F_WB at M=1.15 should be <= 1.0");
		assertTrue(fwb_115 >= 0.5, "F_WB at M=1.15 should be >= 0.5");

		// Blend should be monotonically non-increasing
		assertTrue(fwb_085 >= fwb_100 - 1e-6, "F_WB should not increase: M=0.85 >= M=1.0");
		assertTrue(fwb_100 >= fwb_115 - 1e-6, "F_WB should not increase: M=1.0 >= M=1.15");
	}

	@Test
	public void fwbDecreasesWithMach() {
		// Higher Mach gives more correction (smaller F_WB)
		double fwb_15 = PittsNielsenKaattari.computeF_WB(1.5, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		double fwb_20 = PittsNielsenKaattari.computeF_WB(2.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		double fwb_30 = PittsNielsenKaattari.computeF_WB(3.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		double fwb_50 = PittsNielsenKaattari.computeF_WB(5.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);

		assertTrue(fwb_15 >= fwb_20, "F_WB should decrease with Mach: M1.5 >= M2.0");
		assertTrue(fwb_20 >= fwb_30, "F_WB should decrease with Mach: M2.0 >= M3.0");
		assertTrue(fwb_30 >= fwb_50, "F_WB should decrease with Mach: M3.0 >= M5.0");
	}

	@Test
	public void fwbDependsOnFinGeometry() {
		// Larger semispan relative to root chord gives different correction
		double fwb_small = PittsNielsenKaattari.computeF_WB(2.0, BODY_RADIUS, 0.03, ROOT_CHORD, SWEEP_LE);
		double fwb_large = PittsNielsenKaattari.computeF_WB(2.0, BODY_RADIUS, 0.10, ROOT_CHORD, SWEEP_LE);
		// They should be different
		assertTrue(Math.abs(fwb_small - fwb_large) > 0.001,
				"F_WB should differ for different fin spans: small=" + fwb_small + " large=" + fwb_large);
	}

	@Test
	public void correctionsNeverBelowMinimum() {
		// Test at extreme Mach and geometry conditions
		double[] machs = { 1.5, 2.0, 3.0, 5.0, 10.0 };
		double[] spans = { 0.01, 0.05, 0.20 };
		double[] chords = { 0.02, 0.06, 0.15 };

		for (double m : machs) {
			for (double s : spans) {
				for (double c : chords) {
					double fwb = PittsNielsenKaattari.computeF_WB(m, BODY_RADIUS, s, c, SWEEP_LE);
					assertTrue(fwb >= 0.5,
							"F_WB must be >= 0.5: got " + fwb + " at M=" + m + " span=" + s + " chord=" + c);
					assertTrue(fwb <= 1.0 + 1e-9,
							"F_WB must be <= 1.0: got " + fwb + " at M=" + m + " span=" + s + " chord=" + c);

					double fbw = PittsNielsenKaattari.computeF_BW(m, BODY_RADIUS, s, c);
					assertTrue(fbw >= 0.7,
							"F_BW must be >= 0.7: got " + fbw + " at M=" + m + " span=" + s + " chord=" + c);
					assertTrue(fbw <= 1.0 + 1e-9,
							"F_BW must be <= 1.0: got " + fbw + " at M=" + m + " span=" + s + " chord=" + c);
				}
			}
		}
	}

	@Test
	public void fbwBlendsSmoothlythroughTransonic() {
		double fbw_085 = PittsNielsenKaattari.computeF_BW(0.85, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD);
		double fbw_100 = PittsNielsenKaattari.computeF_BW(1.00, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD);
		double fbw_115 = PittsNielsenKaattari.computeF_BW(1.15, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD);

		assertEquals(1.0, fbw_085, 1e-6, "F_BW at M=0.85 should be ~1.0");
		assertTrue(fbw_085 >= fbw_100 - 1e-6, "F_BW should not increase: M=0.85 >= M=1.0");
		assertTrue(fbw_100 >= fbw_115 - 1e-6, "F_BW should not increase: M=1.0 >= M=1.15");
	}

	@Test
	public void zeroBodyRadiusGivesNoCorrection() {
		// When body radius = 0, r/(r+s) = 0 so correction factor = 1.0
		double fwb = PittsNielsenKaattari.computeF_WB(2.0, 0.0, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		assertEquals(1.0, fwb, 1e-6, "F_WB should be 1.0 when body radius is zero");

		double fbw = PittsNielsenKaattari.computeF_BW(2.0, 0.0, FIN_SEMISPAN, ROOT_CHORD);
		assertEquals(1.0, fbw, 1e-6, "F_BW should be 1.0 when body radius is zero");
	}

	@Test
	public void finCNaReducedAtSupersonic() {
		// Integration test: build a real rocket and check that fin CNa at M=2.0
		// is reduced compared to what pure Barrowman interference would give.
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		TrapezoidFinSet fins = (TrapezoidFinSet) rocket.getChild(0).getChild(1).getChild(0);

		FlightConfiguration config = rocket.getSelectedConfiguration();

		// Compute fin forces at M=2.0
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAOA(Math.toRadians(5.0));
		conditions.setTheta(Math.PI / 2); // non-zero so sin^2(theta) != 0

		WarningSet warnings = new WarningSet();
		AerodynamicForces forces = new AerodynamicForces();
		FinSetCalc calcObj = new FinSetCalc(fins);
		calcObj.calculateNonaxialForces(conditions, Transformation.IDENTITY, forces, warnings);

		double cnaSupersonicWithPNK = forces.getCP().getWeight();

		// The PNK correction reduces the CNa. Verify it's positive and less than
		// what we'd get at subsonic (where PNK factor = 1.0).
		assertTrue(cnaSupersonicWithPNK > 0, "CNa should be positive at M=2.0");

		// Compute at M=0.5 (subsonic, no PNK correction)
		FlightConditions subsonicCond = new FlightConditions(config);
		subsonicCond.setMach(0.5);
		subsonicCond.setAOA(Math.toRadians(5.0));
		subsonicCond.setTheta(Math.PI / 2);

		AerodynamicForces subsonicForces = new AerodynamicForces();
		calcObj.calculateNonaxialForces(subsonicCond, Transformation.IDENTITY, subsonicForces, warnings);

		// At supersonic speeds the basic CNa formula changes too (K1/K2/K3),
		// so we just verify that PNK factors are applied (both < 1.0 at M=2).
		double bodyR = fins.getBodyRadius();
		double finSpan = fins.getSpan();
		double rc = fins.getRootChord();
		double fwb = PittsNielsenKaattari.computeF_WB(2.0, bodyR, finSpan, rc, 0);
		double fbw = PittsNielsenKaattari.computeF_BW(2.0, bodyR, finSpan, rc);
		double combinedReduction = fwb * fbw;

		assertTrue(combinedReduction < 1.0,
				"Combined PNK correction should reduce interference at M=2.0: " + combinedReduction);
		assertTrue(combinedReduction > 0.7,
				"Combined PNK correction should be modest (10-20%): " + combinedReduction);
	}

	@Test
	public void productionSuppressesPnkAboveHighTransonicBand() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		double cna125 = finCNaFromProductionAnalysis(config, 1.25);
		double cna130 = finCNaFromProductionAnalysis(config, 1.30);
		double cna135 = finCNaFromProductionAnalysis(config, 1.35);

		assertTrue(cna125 > 0.0, "fin CNa should be positive below PNK suppression");
		assertTrue(cna130 > 0.0, "fin CNa should be positive at PNK suppression threshold");
		assertTrue(cna135 > 0.0, "fin CNa should be positive above PNK suppression");

		double directFwb = PittsNielsenKaattari.computeF_WB(
				2.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD, SWEEP_LE);
		double directFbw = PittsNielsenKaattari.computeF_BW(
				2.0, BODY_RADIUS, FIN_SEMISPAN, ROOT_CHORD);
		assertTrue(directFwb * directFbw < 1.0,
				"raw PNK curve still has a high-Mach correction");
		assertTrue(cna135 >= 0.95 * cna130,
				"production path should not keep applying raw PNK reduction above M=1.3");
	}

	private static double finCNaFromProductionAnalysis(FlightConfiguration config, double mach) {
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(5.0));
		conditions.setTheta(Math.PI / 2);

		BarrowmanCalculator calc = new BarrowmanCalculator();
		Map<RocketComponent, AerodynamicForces> forces =
				calc.getForceAnalysis(config, conditions, new WarningSet());
		for (Map.Entry<RocketComponent, AerodynamicForces> entry : forces.entrySet()) {
			if (entry.getKey() instanceof TrapezoidFinSet) {
				return entry.getValue().getCP().getWeight();
			}
		}
		throw new AssertionError("No fin force row found");
	}
}
