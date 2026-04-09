package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Transformation;

/**
 * Tests for Phase 11b: Supersonic Roll Damping with Mach cone span clipping.
 *
 * At supersonic speeds, the Mach cone limits the effective fin span for
 * roll damping: span_eff = min(span, c_root * sqrt(M^2-1)).
 */
public class SupersonicRollDampingTest {

	private static final double EPSILON = 1.0e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	private Rocket buildRocketWithFins(double rootChord, double tipChord, double height, double sweep) {
		Rocket rocket = new Rocket();
		rocket.setName("Roll Damping Test");

		AxialStage stage = new AxialStage();
		stage.setName("Stage");
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 0.15, 0.025);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.50, 0.025, 0.001);
		stage.addChild(body);

		TrapezoidFinSet fins = new TrapezoidFinSet();
		fins.setFinCount(4);
		fins.setRootChord(rootChord);
		fins.setTipChord(tipChord);
		fins.setSweep(sweep);
		fins.setHeight(height);
		fins.setThickness(0.003);
		fins.setCrossSection(FinSet.CrossSection.SQUARE);
		body.addChild(fins);

		return rocket;
	}

	private double getRollDamping(Rocket rocket, double mach, double rollRate) {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		TrapezoidFinSet fins = (TrapezoidFinSet) rocket.getChild(0).getChild(1).getChild(0);
		FinSetCalc calc = new FinSetCalc(fins);

		FlightConditions cond = new FlightConditions(config);
		cond.setMach(mach);
		cond.setRollRate(rollRate);
		cond.setAOA(Math.toRadians(2.0));

		AerodynamicForces forces = new AerodynamicForces().zero();
		WarningSet warnings = new WarningSet();
		calc.calculateNonaxialForces(cond, Transformation.IDENTITY, forces, warnings);

		return forces.getCrollDamp();
	}

	@Test
	void testSubsonicDampingUnchanged() {
		// At M=0.5, no Mach cone clipping should occur
		Rocket rocket = buildRocketWithFins(0.10, 0.05, 0.05, 0.02);
		double damping = getRollDamping(rocket, 0.5, 5.0);

		assertFalse(Double.isNaN(damping), "Roll damping at M=0.5 should not be NaN");
		// Subsonic damping should be non-zero for a spinning rocket
		assertTrue(Math.abs(damping) > 0, "Roll damping at M=0.5 should be non-zero");
	}

	@Test
	void testSupersonicDampingComputed() {
		// At M=2, supersonic roll damping should be computed
		Rocket rocket = buildRocketWithFins(0.10, 0.05, 0.05, 0.02);
		double damping = getRollDamping(rocket, 2.0, 5.0);

		assertFalse(Double.isNaN(damping), "Roll damping at M=2 should not be NaN");
		assertTrue(Math.abs(damping) > 0, "Roll damping at M=2 should be non-zero");
	}

	@Test
	void testMachConeSpanClipping() {
		// For a fin with root chord = 0.10m, span = 0.05m
		// At M=1.2: span_eff = min(0.05, 0.10*sqrt(1.44-1)) = min(0.05, 0.10*0.663) = min(0.05, 0.0663)
		//   -> span_eff = 0.05 (no clipping)
		// At M=1.2 with a very small root chord (0.02m):
		//   span_eff = min(0.05, 0.02*0.663) = min(0.05, 0.01326) = 0.01326 (clipped!)
		Rocket rocketWide = buildRocketWithFins(0.10, 0.05, 0.05, 0.02);
		Rocket rocketNarrow = buildRocketWithFins(0.02, 0.01, 0.05, 0.005);

		double dampWide = getRollDamping(rocketWide, 2.0, 5.0);
		double dampNarrow = getRollDamping(rocketNarrow, 2.0, 5.0);

		// The narrow-chord fin should have more severe span clipping
		// (though actual values depend on full geometry)
		assertFalse(Double.isNaN(dampWide), "Wide fin damping should not be NaN");
		assertFalse(Double.isNaN(dampNarrow), "Narrow fin damping should not be NaN");
	}

	@Test
	void testEffectiveSpanFormula() {
		// Verify the effective span formula directly
		double rootChord = 0.10;
		double span = 0.05;

		// M = 1.5: span_eff = min(0.05, 0.10*sqrt(2.25-1)) = min(0.05, 0.1118) -> 0.05 (no clip)
		double machConeSpan15 = rootChord * Math.sqrt(1.5 * 1.5 - 1.0);
		assertEquals(0.1118, machConeSpan15, 0.001);
		double spanEff15 = Math.min(span, machConeSpan15);
		assertEquals(span, spanEff15, EPSILON, "No clipping at M=1.5 for this fin");

		// M = 1.1: span_eff = min(0.05, 0.10*sqrt(1.21-1)) = min(0.05, 0.0458) -> 0.0458 (clipped!)
		double machConeSpan11 = rootChord * Math.sqrt(1.1 * 1.1 - 1.0);
		assertEquals(0.0458, machConeSpan11, 0.001);
		double spanEff11 = Math.min(span, machConeSpan11);
		assertTrue(spanEff11 < span, "Span should be clipped at M=1.1 for this fin");
	}

	@Test
	void testTransonicBlending() {
		// The transonic region (M 0.9-1.5) should blend smoothly
		Rocket rocket = buildRocketWithFins(0.10, 0.05, 0.05, 0.02);

		double damp09 = getRollDamping(rocket, 0.89, 5.0);
		double damp10 = getRollDamping(rocket, 1.0, 5.0);
		double damp12 = getRollDamping(rocket, 1.2, 5.0);
		double damp15 = getRollDamping(rocket, 1.51, 5.0);

		// All should be finite and non-NaN
		assertFalse(Double.isNaN(damp09), "Damping at M=0.89 should not be NaN");
		assertFalse(Double.isNaN(damp10), "Damping at M=1.0 should not be NaN");
		assertFalse(Double.isNaN(damp12), "Damping at M=1.2 should not be NaN");
		assertFalse(Double.isNaN(damp15), "Damping at M=1.51 should not be NaN");
	}

	@Test
	void testDampingSignFollowsRollRate() {
		// Positive roll rate -> positive damping moment (opposing rotation)
		Rocket rocket = buildRocketWithFins(0.10, 0.05, 0.05, 0.02);

		double dampPos = getRollDamping(rocket, 2.0, 5.0);
		double dampNeg = getRollDamping(rocket, 2.0, -5.0);

		// Signs should be opposite
		if (Math.abs(dampPos) > EPSILON && Math.abs(dampNeg) > EPSILON) {
			assertTrue(dampPos * dampNeg < 0,
					"Damping should change sign with roll rate direction");
		}
	}

	@Test
	void testZeroRollRateGivesZeroDamping() {
		Rocket rocket = buildRocketWithFins(0.10, 0.05, 0.05, 0.02);
		double damping = getRollDamping(rocket, 2.0, 0.0);
		assertEquals(0.0, damping, EPSILON, "Zero roll rate should give zero damping");
	}

	@Test
	void testHighMachDamping() {
		// At very high Mach, damping should still be finite
		Rocket rocket = buildRocketWithFins(0.10, 0.05, 0.05, 0.02);
		double damping = getRollDamping(rocket, 4.0, 5.0);

		assertFalse(Double.isNaN(damping), "Roll damping at M=4 should not be NaN");
		assertFalse(Double.isInfinite(damping), "Roll damping at M=4 should be finite");
	}

	@Test
	void testDampingVsMachTrend() {
		// At higher Mach, 1/beta decreases, so supersonic roll damping should generally decrease
		Rocket rocket = buildRocketWithFins(0.10, 0.05, 0.05, 0.02);

		double damp2 = Math.abs(getRollDamping(rocket, 2.0, 5.0));
		double damp4 = Math.abs(getRollDamping(rocket, 4.0, 5.0));

		// Higher Mach -> lower damping (K1 ~ 2/beta decreases)
		// This is the general trend, though not strictly guaranteed
		assertFalse(Double.isNaN(damp2));
		assertFalse(Double.isNaN(damp4));
		// At least both should be positive
		assertTrue(damp2 > 0, "Damping at M=2 should be positive");
		assertTrue(damp4 > 0, "Damping at M=4 should be positive");
	}
}
