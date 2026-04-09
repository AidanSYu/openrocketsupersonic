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
 * Fins with blunt trailing edges (SQUARE cross-section) generate significant
 * wake drag at supersonic speeds.  These tests verify the Mach-dependent
 * trailing-edge base drag model in {@link FinSetCalc}.
 *
 * <p>The trailing-edge drag is tested through the public
 * {@link FinSetCalc#calculateComponentBaseCD} method, which includes both
 * the original cross-section base drag and the new TE base drag contribution.
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
		// At M=2, SQUARE fins should have much more base drag than AIRFOIL fins.
		// AIRFOIL has zero original base drag, so its entire contribution comes from TE drag.
		// SQUARE has both original base drag + TE drag.
		double squareBaseCD = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 2.0);
		double airfoilBaseCD = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 2.0);

		assertTrue(squareBaseCD > 0, "Square fin base CD should be positive at M=2");
		assertTrue(airfoilBaseCD > 0, "Airfoil fin base CD should be positive at M=2 (TE drag contribution)");
		// SQUARE uses full thickness for TE; AIRFOIL uses 5%.  Square should be much larger.
		assertTrue(squareBaseCD > 10 * airfoilBaseCD,
				"Square fin base CD (" + squareBaseCD + ") should be >> airfoil base CD (" + airfoilBaseCD + ")");
	}

	@Test
	public void teDragPositiveAtSupersonic() {
		// AIRFOIL fins had zero base drag before Phase 6j. Now they should have
		// a small positive TE drag.
		double airfoilBaseCD = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 2.0);
		assertTrue(airfoilBaseCD > 0,
				"Airfoil fin base CD should be positive at M=2 due to TE drag");
		// Sanity bounds
		assertTrue(airfoilBaseCD > 1e-8, "TE drag should be non-negligible");
		assertTrue(airfoilBaseCD < 0.1, "TE drag should be bounded");
	}

	@Test
	public void teDragIncreasesWithThickness() {
		// Use AIRFOIL to isolate TE drag (no original base drag contribution)
		double cdThin = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.002, 2.0);
		double cdThick = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.006, 2.0);

		assertTrue(cdThin > 0, "Thin fin TE drag should be positive");
		assertTrue(cdThick > cdThin,
				"Thicker fin TE drag (" + cdThick + ") should exceed thin fin TE drag (" + cdThin + ")");
	}

	@Test
	public void teDragBlendsSmoothlyTransonic() {
		// Evaluate base CD at several Mach numbers across the transonic blend region.
		// Use AIRFOIL to isolate TE drag component (original base drag is zero).
		double cd089 = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 0.89);
		double cd09 = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 0.9);
		double cd10 = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 1.0);
		double cd11 = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 1.1);
		double cd12 = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 1.2);
		double cd121 = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 1.21);

		// All should be positive
		assertTrue(cd09 > 0, "TE drag at M=0.9 should be positive");
		assertTrue(cd10 > 0, "TE drag at M=1.0 should be positive");
		assertTrue(cd11 > 0, "TE drag at M=1.1 should be positive");
		assertTrue(cd12 > 0, "TE drag at M=1.2 should be positive");

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
		// AIRFOIL at subsonic should have small positive TE drag (Hoerner wake model)
		double cdSub = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 0.5);
		assertTrue(cdSub > 0, "TE drag should be positive for fins at M=0.5");

		// TE drag peaks in the transonic region (where the base pressure is lowest).
		// At high supersonic Mach, the 1/sqrt(beta) factor decreases it.
		// Verify transonic peak exceeds both subsonic and high-supersonic values.
		double cdTransonic = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 1.05);
		assertTrue(cdTransonic > cdSub,
				"Transonic TE drag (" + cdTransonic + ") should exceed subsonic (" + cdSub + ")");

		double cdHighSuper = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 3.0);
		assertTrue(cdTransonic > cdHighSuper,
				"Transonic TE drag (" + cdTransonic + ") should exceed high-supersonic (" + cdHighSuper + ")");
	}

	@Test
	public void airfoilTEDragIsSmall() {
		// AIRFOIL fins use t_te = 0.05 * thickness (5% of max thickness)
		// Their TE drag should be much smaller than SQUARE fin total base drag
		double cdAirfoil = computeBaseCD(FinSet.CrossSection.AIRFOIL, 0.003, 2.0);
		double cdSquare = computeBaseCD(FinSet.CrossSection.SQUARE, 0.003, 2.0);

		assertTrue(cdAirfoil > 0, "Airfoil TE drag should be positive");
		assertTrue(cdAirfoil < cdSquare * 0.1,
				"Airfoil base CD (" + cdAirfoil + ") should be < 10% of square base CD (" + cdSquare + ")");
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
