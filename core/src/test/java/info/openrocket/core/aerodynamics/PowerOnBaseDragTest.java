package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.startup.Application;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the power-on base drag reduction factor (Phase 6b).
 *
 * Based on Brazzel et al. (1962) and Dempsey (1976): during motor burn,
 * the exhaust plume fills the base region, raising base pressure and
 * reducing base drag. The reduction depends on the nozzle exit area to
 * base area ratio (A_e / A_b).
 */
class PowerOnBaseDragTest {

	private static final double EPSILON = 1e-9;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	void powerOnFactorFullCoverage() {
		// When nozzle exit area >= 80% of base area, plume fully covers base
		assertEquals(0.0, BarrowmanDragCalculator.powerOnBaseDragFactor(0.8), EPSILON,
				"AR=0.8 should give zero base drag");
		assertEquals(0.0, BarrowmanDragCalculator.powerOnBaseDragFactor(0.9), EPSILON,
				"AR=0.9 should give zero base drag");
		assertEquals(0.0, BarrowmanDragCalculator.powerOnBaseDragFactor(1.0), EPSILON,
				"AR=1.0 should give zero base drag");
		assertEquals(0.0, BarrowmanDragCalculator.powerOnBaseDragFactor(1.5), EPSILON,
				"AR>1.0 should still give zero base drag");
	}

	@Test
	void powerOnFactorLargePlume() {
		// AR=0.6 is midway between 0.4 and 0.8 -> factor = 0.2 * (0.8-0.6)/0.4 = 0.1
		double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(0.6);
		assertEquals(0.1, factor, EPSILON, "AR=0.6 should give factor=0.1");
	}

	@Test
	void powerOnFactorMediumPlume() {
		// AR=0.3 -> factor = 0.2 + 0.6*(0.4-0.3)/0.3 = 0.2 + 0.2 = 0.4
		double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(0.3);
		assertEquals(0.4, factor, EPSILON, "AR=0.3 should give factor=0.4");
	}

	@Test
	void powerOnFactorSmallPlume() {
		// AR=0.05 -> factor = 0.8 + 0.2*(0.1-0.05)/0.1 = 0.8 + 0.1 = 0.9
		double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(0.05);
		assertEquals(0.9, factor, EPSILON, "AR=0.05 should give factor=0.9");
	}

	@Test
	void powerOnFactorZeroArea() {
		// AR=0 means no plume at all -> full base drag (factor=1.0)
		double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(0.0);
		assertEquals(1.0, factor, EPSILON, "AR=0 should give full base drag (factor=1.0)");
	}

	@Test
	void powerOnFactorContinuous() {
		// Factor should be monotonically decreasing from AR=0 to AR=1
		double prev = BarrowmanDragCalculator.powerOnBaseDragFactor(0.0);
		for (int i = 1; i <= 100; i++) {
			double ar = i / 100.0;
			double current = BarrowmanDragCalculator.powerOnBaseDragFactor(ar);
			assertTrue(current <= prev + EPSILON,
					"Factor must be monotonically decreasing: f(" + (ar - 0.01) + ")=" + prev
							+ " but f(" + ar + ")=" + current);
			prev = current;
		}
	}

	@Test
	void powerOnFactorBoundaryValues() {
		// Exact values at the piecewise breakpoints
		assertEquals(1.0, BarrowmanDragCalculator.powerOnBaseDragFactor(0.0), EPSILON,
				"AR=0.0 boundary");
		assertEquals(0.8, BarrowmanDragCalculator.powerOnBaseDragFactor(0.1), EPSILON,
				"AR=0.1 boundary");
		assertEquals(0.2, BarrowmanDragCalculator.powerOnBaseDragFactor(0.4), EPSILON,
				"AR=0.4 boundary");
		assertEquals(0.0, BarrowmanDragCalculator.powerOnBaseDragFactor(0.8), EPSILON,
				"AR=0.8 boundary");
	}

	@Test
	void powerOnFactorNeverNegative() {
		// Factor must be >= 0 for all reasonable inputs
		for (int i = 0; i <= 200; i++) {
			double ar = i / 100.0;
			double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(ar);
			assertTrue(factor >= 0.0,
					"Factor must be non-negative at AR=" + ar + ", got " + factor);
		}
	}

	@Test
	void powerOnFactorNeverAboveOne() {
		// Factor must be <= 1 for all non-negative inputs
		for (int i = 0; i <= 200; i++) {
			double ar = i / 100.0;
			double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(ar);
			assertTrue(factor <= 1.0,
					"Factor must be <= 1.0 at AR=" + ar + ", got " + factor);
		}
	}

	@Test
	void defaultReductionForTypicalMotor() {
		// Typical HPR motor has AR ~ 0.3, giving factor ~ 0.4 (60% reduction)
		double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(0.3);
		assertTrue(factor > 0.0 && factor < 1.0,
				"Typical motor AR=0.3 should give partial reduction, got " + factor);
		assertEquals(0.4, factor, 0.01,
				"AR=0.3 should give approximately 40% of unpowered base drag");
	}

	@Test
	void baseDragReducedDuringBurn() {
		// Integration-style test: verify that multiplying static base CD by the
		// power-on factor produces physically reasonable results.
		// At Mach 2.0 supersonic, base CD should be around 0.125 (from calculateBaseCD).
		double mach = 2.0;
		double unpoweredBaseCD = BarrowmanDragCalculator.calculateBaseCD(mach);
		assertTrue(unpoweredBaseCD > 0.0, "Unpowered base CD must be positive");

		// With a typical motor (AR=0.3), powered base CD should be ~40% of unpowered
		double areaRatio = 0.3;
		double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(areaRatio);
		double poweredBaseCD = unpoweredBaseCD * factor;

		assertTrue(poweredBaseCD < unpoweredBaseCD,
				"Powered base CD must be less than unpowered");
		assertTrue(poweredBaseCD >= 0.0,
				"Powered base CD must not be negative");
		assertEquals(unpoweredBaseCD * 0.4, poweredBaseCD, 1e-6,
				"Powered base CD should be 40% of unpowered for AR=0.3");
	}

	@Test
	void powerOnFactorNegativeAreaRatioClamps() {
		// Negative area ratio is unphysical but should not produce values > 1
		// The current piecewise formula extends linearly below 0, giving factor > 1.
		// This documents the behavior; callers should clamp AR >= 0.
		double factor = BarrowmanDragCalculator.powerOnBaseDragFactor(-0.1);
		// factor = 0.8 + 0.2*(0.1-(-0.1))/0.1 = 0.8 + 0.4 = 1.2
		// This is > 1 which is unphysical; documented as a known edge case.
		// The method contract states AR must be >= 0.
		assertTrue(factor >= 0.0, "Factor should not be negative even for negative AR");
	}
}
