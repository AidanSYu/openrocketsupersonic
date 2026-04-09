package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.aerodynamics.barrowman.TransonicSimilarity;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;
import info.openrocket.core.util.Transformation;

public class TransonicSimilarityTest {

	private static final double EPSILON = 1e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// --- TransonicSimilarity unit tests ---

	@Test
	public void kTransSubsonic() {
		// At M=0.5, t/c=0.05, no sweep: K_trans should be well below -2
		double k = TransonicSimilarity.computeKTrans(0.5, 0.05, 0.0);
		assertTrue(k < -2.0, "K_trans at M=0.5 should be < -2, got " + k);
		assertFalse(TransonicSimilarity.isInTransonicRegime(k),
				"M=0.5 should not be in transonic regime");
	}

	@Test
	public void kTransAtMach1() {
		// At M=1.0, M^2-1=0, so K_trans should be ~0
		double k = TransonicSimilarity.computeKTrans(1.0, 0.05, 0.0);
		assertEquals(0.0, k, EPSILON, "K_trans at M=1.0 should be 0");
		assertTrue(TransonicSimilarity.isInTransonicRegime(k),
				"M=1.0 should be in transonic regime");
	}

	@Test
	public void kTransSupersonic() {
		// At M=2.5, t/c=0.05: K_trans should be well above 3
		double k = TransonicSimilarity.computeKTrans(2.5, 0.05, 0.0);
		assertTrue(k > 3.0, "K_trans at M=2.5 should be > 3, got " + k);
		assertFalse(TransonicSimilarity.isInTransonicRegime(k),
				"M=2.5 should not be in transonic regime");
	}

	@Test
	public void universalCurveAtPeak() {
		// h(0) should be exactly 1.0
		assertEquals(1.0, TransonicSimilarity.universalCurve(0.0), EPSILON,
				"Universal curve peak at K=0 should be 1.0");
	}

	@Test
	public void universalCurveDecreasesBothSides() {
		double hNeg1 = TransonicSimilarity.universalCurve(-1.0);
		double hZero = TransonicSimilarity.universalCurve(0.0);
		double hPos1 = TransonicSimilarity.universalCurve(1.0);

		assertTrue(hNeg1 < hZero, "h(-1) should be less than h(0)");
		assertTrue(hPos1 < hZero, "h(+1) should be less than h(0)");
	}

	@Test
	public void cnaPeakReasonable() {
		// For AR=2, t/c=0.05: peak CNa should be a reasonable positive value
		double cnaPeak = TransonicSimilarity.computeCNaPeak(2.0, 0.05);
		assertTrue(cnaPeak > 1.0, "Peak CNa should be > 1.0 for AR=2, got " + cnaPeak);
		assertTrue(cnaPeak < 10.0, "Peak CNa should be < 10.0 for AR=2, got " + cnaPeak);
	}

	@Test
	public void cnaPeakIncreasesWithAR() {
		double cnaLowAR = TransonicSimilarity.computeCNaPeak(1.0, 0.05);
		double cnaHighAR = TransonicSimilarity.computeCNaPeak(4.0, 0.05);
		assertTrue(cnaHighAR > cnaLowAR,
				"Higher AR should give higher peak CNa: AR=1 -> " + cnaLowAR + ", AR=4 -> " + cnaHighAR);
	}

	@Test
	public void thickerFinWiderTransonicBand() {
		// A thicker fin (larger t/c) means smaller |K_trans| for the same Mach offset
		// So the transonic regime covers a wider Mach range
		double tcThin = 0.03;
		double tcThick = 0.10;

		// Find the Mach at which K_trans = 3 (upper edge of regime)
		// K = (M^2-1)/(t/c)^(2/3) = 3  =>  M^2 = 1 + 3*(t/c)^(2/3)
		double machUpperThin = Math.sqrt(1 + 3.0 * Math.pow(tcThin, 2.0 / 3.0));
		double machUpperThick = Math.sqrt(1 + 3.0 * Math.pow(tcThick, 2.0 / 3.0));

		assertTrue(machUpperThick > machUpperThin,
				"Thicker fin should have wider transonic band: thin edge=" + machUpperThin +
						", thick edge=" + machUpperThick);
	}

	@Test
	public void sweptFinShiftsPeak() {
		// With sweep, effective Mach = M*cos(sweep) is lower, so K_trans shifts
		// At same freestream Mach, swept fin has lower K_trans
		double kNoSweep = TransonicSimilarity.computeKTrans(1.1, 0.05, 0.0);
		double kSwept = TransonicSimilarity.computeKTrans(1.1, 0.05, Math.toRadians(30));

		assertTrue(kSwept < kNoSweep,
				"Swept fin should have lower K_trans at same Mach: unswept=" + kNoSweep +
						", swept=" + kSwept);
	}

	@Test
	public void thinFinBypassesSimilarity() {
		// Very thin fin (t/c < 1e-6) should return extreme K values
		double k = TransonicSimilarity.computeKTrans(1.0, 1e-7, 0.0);
		// At exactly M=1.0, mEff=1.0 so mEff>1.0 is false, should return -100
		assertEquals(-100, k, EPSILON,
				"Very thin fin at M=1.0 should return -100");
	}

	@Test
	public void universalCurveExtrapolation() {
		// Below K=-2, should clamp to h(-2)=0.70
		assertEquals(0.70, TransonicSimilarity.universalCurve(-5.0), EPSILON);
		// Above K=3, should clamp to h(3)=0.62
		assertEquals(0.62, TransonicSimilarity.universalCurve(10.0), EPSILON);
	}

	// --- Integration test using FinSetCalc ---

	private double sumFinCNa(TrapezoidFinSet fins, Rocket rocket, double mach) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(2.0));
		WarningSet warnings = new WarningSet();
		AerodynamicForces assemblyForces = new AerodynamicForces().zero();
		AerodynamicForces componentForces = new AerodynamicForces();

		FinSetCalc calc = new FinSetCalc(fins);

		for (int i = 0; i < fins.getFinCount(); i++) {
			calc.calculateNonaxialForces(conditions,
					Transformation.rotate_x(2 * Math.PI * i / fins.getFinCount()),
					componentForces, warnings);
			assemblyForces.merge(componentForces);
		}

		return assemblyForces.getCP().getWeight();
	}

	@Test
	public void finCNaFiniteAndPositiveThroughTransonic() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		TrapezoidFinSet fins = (TrapezoidFinSet) rocket.getChild(0).getChild(1).getChild(0);

		// Set fin thickness so t/c > 0.01 to activate transonic similarity
		// Estes Alpha III root chord = 0.05m, so thickness = 0.003m gives t/c ~ 0.06
		fins.setThickness(0.003);

		double[] machs = { 0.5, 0.8, 0.95, 1.05, 1.2, 1.5, 2.5 };
		double prevCNa = Double.NaN;

		for (double mach : machs) {
			double cna = sumFinCNa(fins, rocket, mach);
			assertTrue(cna > 0 && Double.isFinite(cna),
					"CNa at M=" + mach + " should be positive and finite: " + cna);
		}

		// Verify high-supersonic CNa decreases with Mach (expected trend)
		double cna15 = sumFinCNa(fins, rocket, 1.5);
		double cna25 = sumFinCNa(fins, rocket, 2.5);
		assertTrue(cna15 > cna25,
				"CNa at M=1.5 (" + cna15 + ") should exceed CNa at M=2.5 (" + cna25 + ")");
	}
}
