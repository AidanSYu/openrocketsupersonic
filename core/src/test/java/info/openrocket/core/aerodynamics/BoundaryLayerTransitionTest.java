package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.Application;

/**
 * Phase 8c: Tests for boundary layer transition mapping.
 * Validates Michel criterion with compressibility correction and
 * the laminar fraction calculation used to reduce skin friction.
 */
class BoundaryLayerTransitionTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	void transitionReynoldsDecreaseWithMach() {
		// Higher Mach promotes earlier transition (lower Re_tr)
		double reTrLow = BarrowmanDragCalculator.transitionReynoldsNumber(0.5);
		double reTrHigh = BarrowmanDragCalculator.transitionReynoldsNumber(3.0);
		assertTrue(reTrHigh < reTrLow,
				"Transition Re at M=3 (" + reTrHigh + ") should be less than at M=0.5 (" + reTrLow + ")");
	}

	@Test
	void transitionReynoldsAtZeroMach() {
		// At M=0, the compressibility correction vanishes: Re_tr = 3e6
		double reTr = BarrowmanDragCalculator.transitionReynoldsNumber(0.0);
		assertEquals(3.0e6, reTr, 1.0, "Transition Re at M=0 should be 3e6");
	}

	@Test
	void laminarFractionIsOneForShortRocket() {
		// A very short rocket at low speed should be fully laminar
		// x_tr = Re_tr * nu / V = 3e6 * 1.5e-5 / 10 = 4.5 m
		// totalLength = 0.1 m => fLam = min(4.5/0.1, 1) = 1.0
		double fLam = BarrowmanDragCalculator.laminarFraction(0.0, 0.1, 10.0, 1.5e-5);
		assertEquals(1.0, fLam, 1e-10, "Very short rocket should be fully laminar");
	}

	@Test
	void laminarFractionIsZeroForVeryLong() {
		// A very long rocket at high velocity: transition happens very early
		// x_tr = 3e6 * 1.5e-5 / 1000 = 0.045 m; totalLength = 100 m
		// fLam = 0.045 / 100 = 0.00045
		double fLam = BarrowmanDragCalculator.laminarFraction(0.0, 100.0, 1000.0, 1.5e-5);
		assertTrue(fLam < 0.001,
				"Very long rocket at high velocity should be nearly fully turbulent, got " + fLam);
	}

	@Test
	void laminarFractionDecreasesWithVelocity() {
		// Higher velocity means shorter laminar run (higher local Re)
		double fLamSlow = BarrowmanDragCalculator.laminarFraction(0.5, 1.0, 50.0, 1.5e-5);
		double fLamFast = BarrowmanDragCalculator.laminarFraction(0.5, 1.0, 500.0, 1.5e-5);
		assertTrue(fLamFast < fLamSlow,
				"Laminar fraction at V=500 (" + fLamFast + ") should be less than at V=50 (" + fLamSlow + ")");
	}

	@Test
	void laminarFractionBounded() {
		// Test across a range of conditions: fraction must always be in [0, 1]
		double[] machs = { 0.0, 0.5, 1.0, 2.0, 5.0, 10.0 };
		double[] lengths = { 0.05, 0.5, 2.0, 10.0 };
		double[] velocities = { 1.0, 100.0, 1000.0 };
		double nu = 1.5e-5;

		for (double m : machs) {
			for (double L : lengths) {
				for (double V : velocities) {
					double fLam = BarrowmanDragCalculator.laminarFraction(m, L, V, nu);
					assertTrue(fLam >= 0.0 && fLam <= 1.0,
							String.format("laminarFraction(M=%.1f, L=%.2f, V=%.0f) = %.6f out of [0,1]",
									m, L, V, fLam));
				}
			}
		}
	}

	@Test
	void transitionFactorReducesCf() {
		// When laminar fraction > 0, the transition factor should be < 1
		double fLam = BarrowmanDragCalculator.laminarFraction(0.5, 1.0, 100.0, 1.5e-5);
		assertTrue(fLam > 0, "Should have some laminar region");
		double transitionFactor = 1.0 - 0.6 * fLam;
		assertTrue(transitionFactor < 1.0, "Transition factor should reduce Cf");
		assertTrue(transitionFactor >= 0.4, "Transition factor should not go below 0.4 (fully laminar limit)");
	}

	@Test
	void laminarFractionZeroForInvalidInputs() {
		// Zero or negative inputs should return 0 (fully turbulent, safe default)
		assertEquals(0.0, BarrowmanDragCalculator.laminarFraction(1.0, 0.0, 100.0, 1.5e-5));
		assertEquals(0.0, BarrowmanDragCalculator.laminarFraction(1.0, -1.0, 100.0, 1.5e-5));
		assertEquals(0.0, BarrowmanDragCalculator.laminarFraction(1.0, 1.0, 0.0, 1.5e-5));
		assertEquals(0.0, BarrowmanDragCalculator.laminarFraction(1.0, 1.0, -10.0, 1.5e-5));
		assertEquals(0.0, BarrowmanDragCalculator.laminarFraction(1.0, 1.0, 100.0, 0.0));
		assertEquals(0.0, BarrowmanDragCalculator.laminarFraction(1.0, 1.0, 100.0, -1e-5));
	}

	@Test
	void transitionReynoldsPositiveForAllMach() {
		// Re_tr must always be positive (physical requirement)
		for (double m = 0; m <= 10.0; m += 0.5) {
			double reTr = BarrowmanDragCalculator.transitionReynoldsNumber(m);
			assertTrue(reTr > 0,
					"Transition Re must be positive at M=" + m + ", got " + reTr);
		}
	}

	@Test
	void laminarFractionDecreasesWithMach() {
		// At same conditions, higher Mach should give smaller laminar fraction
		// because Re_tr decreases with Mach
		double fLamSubsonic = BarrowmanDragCalculator.laminarFraction(0.3, 1.0, 100.0, 1.5e-5);
		double fLamSupersonic = BarrowmanDragCalculator.laminarFraction(3.0, 1.0, 100.0, 1.5e-5);
		assertTrue(fLamSupersonic < fLamSubsonic,
				"Laminar fraction at M=3 (" + fLamSupersonic + ") should be less than at M=0.3 (" + fLamSubsonic + ")");
	}

	@Test
	void smoothFinishTransitionCfIsLaminarBelowTransition() {
		double re = 2.0e5;
		double reTr = 3.0e6;
		double expected = 1.328 / Math.sqrt(re);
		double actual = BarrowmanDragCalculator.smoothFinishTransitionCf(re, reTr);
		assertEquals(expected, actual, 1e-12,
				"Smooth-finish Cf should reduce to Blasius when Re is below transition");
	}

	@Test
	void smoothFinishTransitionCfDropsAsTransitionMovesAft() {
		double re = 2.0e6;
		double earlyTransition = BarrowmanDragCalculator.smoothFinishTransitionCf(re, 5.0e5);
		double lateTransition = BarrowmanDragCalculator.smoothFinishTransitionCf(re, 1.5e6);
		assertTrue(lateTransition < earlyTransition,
				"Later transition should reduce smooth-finish Cf: early=" + earlyTransition + " late=" + lateTransition);
	}

	@Test
	void forceTurbulentBLBypassesLaminarBranchForPerfectFinishRocket() throws Exception {
		// On a perfect-finish rocket at subsonic Mach with Re below transition,
		// the default (forceTurbulentBL=false) path uses the Blasius laminar
		// branch of smoothFinishTransitionCf. Setting forceTurbulentBL=true
		// must skip that laminar branch and produce a materially higher Cf
		// consistent with the fully-turbulent rough-plate formula used for
		// non-perfect-finish rockets. This mirrors the RASAero II behavior
		// triggered by CDX1 Turbulence=True.
		Rocket rocket = SupersonicTestRockets.makeAgardB();
		assertTrue(rocket.isPerfectFinish(), "AGARD calibration model should be perfect finish");
		FlightConfiguration config = rocket.getSelectedConfiguration();

		BarrowmanDragCalculator dragCalculator = new BarrowmanDragCalculator();
		Method cfMethod = BarrowmanDragCalculator.class
				.getDeclaredMethod("calculateFrictionCoefficient",
						FlightConfiguration.class, double.class, double.class, double.class, boolean.class);
		cfMethod.setAccessible(true);

		// Low-Re subsonic regime where laminar branch would otherwise apply:
		// Re = 1e5 is well below transitionReynoldsNumber(M=0.3) ~= 2.96e6.
		double mach = 0.3;
		double re = 1.0e5;
		double T_e = 288.15;

		double cfDefault = (double) cfMethod.invoke(dragCalculator, config, mach, re, T_e, false);
		double cfForced = (double) cfMethod.invoke(dragCalculator, config, mach, re, T_e, true);

		assertTrue(cfForced > cfDefault,
				"forceTurbulentBL=true should yield higher Cf than default laminar path "
						+ "(cfDefault=" + cfDefault + ", cfForced=" + cfForced + ")");
		// The forced-turbulent Cf at Re=1e5 should match the rough-plate branch
		// of incompressibleCf(Re, false), which returns 1.48e-2 below Re=1e4 or
		// Schlichting above. At Re=1e5 the Schlichting formula gives ~0.0073.
		assertTrue(cfForced > 5.0e-3,
				"Forced-turbulent Cf at Re=1e5 should exceed 5e-3 (Schlichting-turbulent), got " + cfForced);
		// And the default laminar-dominated Cf should sit below ~5e-3 at the
		// same Re (Blasius: 1.328/sqrt(1e5) ~= 4.2e-3, damped slightly by the
		// subsonic compressibility factor).
		assertTrue(cfDefault < 5.0e-3,
				"Default smooth-finish Cf at Re=1e5 should be laminar-like (<5e-3), got " + cfDefault);
	}

	@Test
	void forceTurbulentBLHasNoEffectWhenAlreadyFullyTurbulent() throws Exception {
		// For a non-perfect-finish rocket, the default path already takes the
		// rough-plate branch of incompressibleCf. Setting forceTurbulentBL=true
		// therefore must not change the Cf result — it is a no-op in this
		// regime. This protects non-CDX1 simulations from any regression.
		Rocket rocket = SupersonicTestRockets.makeAgardB();
		rocket.setPerfectFinish(false);
		FlightConfiguration config = rocket.getSelectedConfiguration();

		BarrowmanDragCalculator dragCalculator = new BarrowmanDragCalculator();
		Method cfMethod = BarrowmanDragCalculator.class
				.getDeclaredMethod("calculateFrictionCoefficient",
						FlightConfiguration.class, double.class, double.class, double.class, boolean.class);
		cfMethod.setAccessible(true);

		// Sweep a few (M, Re) points covering sub-, trans-, and supersonic regimes.
		double T_e = 288.15;
		double[][] points = {
				{ 0.3, 1.0e5 },
				{ 0.5, 5.0e5 },
				{ 0.95, 3.0e6 },
				{ 1.5, 1.0e7 },
				{ 3.0, 5.0e7 }
		};
		for (double[] p : points) {
			double m = p[0];
			double re = p[1];
			double cfOff = (double) cfMethod.invoke(dragCalculator, config, m, re, T_e, false);
			double cfOn = (double) cfMethod.invoke(dragCalculator, config, m, re, T_e, true);
			assertEquals(cfOff, cfOn, 1e-12,
					String.format("forceTurbulentBL should not change Cf for non-perfect-finish rocket "
							+ "at M=%.2f, Re=%.1e (off=%.6e, on=%.6e)", m, re, cfOff, cfOn));
		}
	}

	@Test
	void agardPerfectFinishUsesPhysicallyReasonableReynoldsAndFriction() throws Exception {
		Rocket rocket = SupersonicTestRockets.makeAgardB();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(0.2);
		conditions.setAOA(0.0);
		conditions.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325));

		BarrowmanDragCalculator dragCalculator = new BarrowmanDragCalculator();
		Method reMethod = BarrowmanDragCalculator.class
				.getDeclaredMethod("calculateReynoldsNumber", FlightConfiguration.class, FlightConditions.class);
		reMethod.setAccessible(true);
		Method cfMethod = BarrowmanDragCalculator.class
				.getDeclaredMethod("calculateFrictionCoefficient",
						FlightConfiguration.class, double.class, double.class, double.class, boolean.class);
		cfMethod.setAccessible(true);
		Method roughnessMethod = BarrowmanDragCalculator.class
				.getDeclaredMethod("calculateRoughnessCorrection", double.class);
		roughnessMethod.setAccessible(true);

		double re = (double) reMethod.invoke(dragCalculator, config, conditions);
		double cf = (double) cfMethod.invoke(dragCalculator, config, 0.2, re,
				conditions.getAtmosphericConditions().getTemperature(), false);
		double roughnessCorrection = (double) roughnessMethod.invoke(dragCalculator, 0.2);
		double roughnessLimit = 0.032
				* Math.pow(ExternalComponent.Finish.POLISHED.getRoughnessSize() / config.getLengthAerodynamic(), 0.2)
				* roughnessCorrection;

		BarrowmanCalculator calculator = new BarrowmanCalculator();
		AerodynamicForces total = calculator.getAerodynamicForces(config, conditions, new WarningSet());

		assertTrue(rocket.isPerfectFinish(), "AGARD calibration model should be marked as perfect finish");
		assertTrue(re > 1.0e6, "AGARD benchmark Reynolds number should be in the wind-tunnel regime, got " + re);
		assertTrue(cf < roughnessLimit,
				"Smooth-plate Cf should stay below the legacy roughness floor for a polished calibration model");
		assertTrue(total.getFrictionCD() < 0.30,
				"Perfect-finish AGARD friction drag should not blow up into the rough-wall regime, got "
						+ total.getFrictionCD());
	}
}
