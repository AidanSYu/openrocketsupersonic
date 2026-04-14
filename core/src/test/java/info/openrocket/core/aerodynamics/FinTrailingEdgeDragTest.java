package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for fin trailing-edge base drag (Phase 6j).
 *
 * Only fins with blunt trailing edges (SQUARE cross-section) generate significant
 * wake drag.  AIRFOIL, ROUNDED, and HEXAGONAL fins are assumed to have near-sharp
 * trailing edges (Cd_te ≈ 0); applying the Hoerner blunt-TE formula to them added
 * a systematic 0.010–0.025 Cd overdrag across subsonic Mach and was the
 * second-largest source of benchmark overdrag on multi-fin clusters.
 *
 * <p>The trailing-edge drag is tested through the public
 * {@link FinSetCalc#calculateComponentBaseCD} method, which includes both
 * the original cross-section base drag and the TE base drag contribution.
 */
public class FinTrailingEdgeDragTest {

	private static final double EPSILON = 1e-8;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	/**
	 * Helper: build a rocket with fins of the given cross-section and thickness,
	 * create FlightConditions at the given Mach, and return the component base drag
	 * from calculateComponentBaseCD (which includes TE base drag).
	 */
	private double computeBaseCD(FinSet.CrossSection cs, double thickness, double mach) {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		TrapezoidFinSet fins = (TrapezoidFinSet) rocket.getChild(0).getChild(1).getChild(0);
		fins.setCrossSection(cs);
		fins.setThickness(thickness);

		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);

		FinSetCalc calc = new FinSetCalc(fins);
		WarningSet warnings = new WarningSet();
		// Use a fixed baseCD so the original base drag contribution is constant
		return calc.calculateComponentBaseCD(conditions, 0.5, warnings);
	}

	@Test
	public void squareFinHasHigherTEDragThanAirfoil() {
		// At M=2, SQUARE fins should have significant base drag; AIRFOIL fins have none.
		// AIRFOIL fins have near-sharp TEs — their TE base drag is zero by design to
		// avoid systematic overdrag on subsonic benchmark rockets.
		double squareBaseCD = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 2.0);
		double airfoilBaseCD = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 2.0);

		assertTrue(squareBaseCD > 0, "Square fin base CD should be positive at M=2");
		assertEquals(0.0, airfoilBaseCD, EPSILON,
				"Airfoil fin base CD should be exactly zero (sharp TE, no base drag)");
	}

	@Test
	public void squareTEDragPositiveAtSupersonic() {
		// SQUARE fins have blunt TEs and generate positive base drag at all Mach numbers.
		double squareBaseCD = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 2.0);
		assertTrue(squareBaseCD > 0,
				"Square fin base CD should be positive at M=2 due to TE drag");
		assertTrue(squareBaseCD > 1e-8, "TE drag should be non-negligible");
		assertTrue(squareBaseCD < 1.0, "TE drag should be bounded (physical sanity check)");
	}

	@Test
	public void teDragIncreasesWithThickness() {
		// Use SQUARE to test thickness dependence (AIRFOIL has zero TE drag).
		double cdThin = computeBaseCD(FinSet.CrossSection.SQUARE, 0.002, 2.0);
		double cdThick = computeBaseCD(FinSet.CrossSection.SQUARE, 0.006, 2.0);

		assertTrue(cdThin > 0, "Thin SQUARE fin TE drag should be positive");
		assertTrue(cdThick > cdThin,
				"Thicker SQUARE fin TE drag (" + cdThick + ") should exceed thin fin TE drag (" + cdThin + ")");
	}

	@Test
	public void teDragBlendsSmoothlyTransonic() {
		// Evaluate base CD at several Mach numbers across the transonic blend region.
		// Use SQUARE to isolate TE drag behavior (it's the only cross-section with TE drag).
		double cd089 = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 0.89);
		double cd09 = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 0.9);
		double cd10 = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 1.0);
		double cd11 = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 1.1);
		double cd12 = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 1.2);
		double cd121 = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 1.21);

		// All should be positive
		assertTrue(cd09 > 0, "SQUARE TE drag at M=0.9 should be positive");
		assertTrue(cd10 > 0, "SQUARE TE drag at M=1.0 should be positive");
		assertTrue(cd11 > 0, "SQUARE TE drag at M=1.1 should be positive");
		assertTrue(cd12 > 0, "SQUARE TE drag at M=1.2 should be positive");

		// No wild jumps between adjacent points (ratio < 2)
		assertTrue(cd10 / cd09 < 2.0 && cd09 / cd10 < 2.0,
				"TE drag should not jump wildly between M=0.9 (" + cd09 + ") and M=1.0 (" + cd10 + ")");
		assertTrue(cd11 / cd10 < 2.0 && cd10 / cd11 < 2.0,
				"TE drag should not jump wildly between M=1.0 (" + cd10 + ") and M=1.1 (" + cd11 + ")");
		assertTrue(cd12 / cd11 < 2.0 && cd11 / cd12 < 2.0,
				"TE drag should not jump wildly between M=1.1 (" + cd11 + ") and M=1.2 (" + cd12 + ")");

		// Continuity at blend boundaries
		assertTrue(Math.abs(cd09 - cd089) / cd09 < 0.05,
				"TE drag should be continuous at M=0.9 boundary");
		assertTrue(Math.abs(cd12 - cd121) / cd12 < 0.05,
				"TE drag should be continuous at M=1.2 boundary");
	}

	@Test
	public void teDragAtSubsonic() {
		// SQUARE at subsonic should have positive TE drag (Hoerner turbulent wake).
		double cdSub = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 0.5);
		assertTrue(cdSub > 0, "SQUARE TE drag should be positive for fins at M=0.5");

		// TE drag peaks in the transonic region (where the base pressure is lowest).
		// Verify transonic peak exceeds both subsonic and high-supersonic values.
		double cdTransonic = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 1.05);
		assertTrue(cdTransonic > cdSub,
				"Transonic SQUARE TE drag (" + cdTransonic + ") should exceed subsonic (" + cdSub + ")");

		double cdHighSuper = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 3.0);
		assertTrue(cdTransonic > cdHighSuper,
				"Transonic SQUARE TE drag (" + cdTransonic + ") should exceed high-supersonic (" + cdHighSuper + ")");
	}

	@Test
	public void airfoilAndRoundedHaveZeroTEDrag() {
		// AIRFOIL, ROUNDED, and HEXAGONAL fins have near-sharp TEs → zero base drag.
		// This is correct: applying Hoerner blunt-TE to them was a systematic overdrag source.
		for (double mach : new double[]{0.5, 0.9, 1.0, 1.5, 2.0, 3.0}) {
			double cdAirfoil = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, mach);
			double cdRounded = computeBaseCD(FinSet.CrossSection.ROUNDED, 0.003, mach);
			double cdHex = computeBaseCD(FinSet.CrossSection.HEXAGONAL, 0.003, mach);

			assertEquals(0.0, cdAirfoil, EPSILON,
					"AIRFOIL fin TE drag should be 0 at M=" + mach);
			// ROUNDED gets baseCD/2 from cross-section drag (not TE), but TE portion is 0
			// Check that the TE-only contribution is zero by checking total is only from cross-section term:
			// calculateComponentBaseCD passes baseCD=0.5, so ROUNDED = 0.5/2 * span*thick/refArea
			// We only assert AIRFOIL and HEXAGONAL since ROUNDED has non-zero cross-section base drag.
			assertEquals(0.0, cdHex, EPSILON,
					"HEXAGONAL fin TE drag should be 0 at M=" + mach);
		}
	}

	@Test
	public void teDragZeroForZeroThickness() {
		// Zero thickness should produce zero base drag (both original and TE)
		double cdSquare = computeBaseCD(FinSet.CrossSection.SQUARE, 0.0, 2.0);
		double cdAirfoil = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.0, 2.0);

		assertEquals(0.0, cdSquare, EPSILON, "Base CD should be zero for zero-thickness SQUARE fins");
		assertEquals(0.0, cdAirfoil, EPSILON, "Base CD should be zero for zero-thickness AIRFOIL fins");
	}
}
