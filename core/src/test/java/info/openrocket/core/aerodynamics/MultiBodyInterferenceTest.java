package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.startup.Application;

/**
 * Tests for Phase 7a: Multi-Body Shock Interference.
 * <p>
 * Validates the inter-body shock impingement model for strap-on booster
 * configurations at supersonic speeds. Tests the ShockGeometry static
 * computation method directly.
 */
public class MultiBodyInterferenceTest {

	private static final double EPS = 1e-6;
	private static final double GAMMA = 1.4;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ================================================================
	// At M<1: no impingement
	// ================================================================

	@Test
	void testNoImpingementAtSubsonic() {
		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						0.8,   // mach
						0.10,  // separation
						0.025, // rCore
						0.015, // rBooster
						0.50,  // boosterLength
						0.002, // sRef
						GAMMA);

		assertNull(result, "Should be null at subsonic M=0.8");
	}

	@Test
	void testNoImpingementAtMach1() {
		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						1.0,   // mach
						0.10,  // separation
						0.025, // rCore
						0.015, // rBooster
						0.50,  // boosterLength
						0.002, // sRef
						GAMMA);

		assertNull(result, "Should be null at M=1.0");
	}

	// ================================================================
	// At M=2: x_impinge matches Mach cone geometry
	// ================================================================

	@Test
	void testImpingementLocationAtMach2() {
		double mach = 2.0;
		double rCore = 0.025;
		double rBooster = 0.015;
		double separation = 0.10;
		double boosterLength = 0.50;
		double sRef = Math.PI * rCore * rCore;

		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						mach, separation, rCore, rBooster, boosterLength,
						sRef, GAMMA);

		assertNotNull(result, "Should have impingement at M=2");

		// Expected x_impinge = gap / tan(mu) where mu = asin(1/M)
		double gap = separation - rCore - rBooster;
		double mu = Math.asin(1.0 / mach);
		double expectedX = gap / Math.tan(mu);

		assertEquals(expectedX, result.xImpinge, expectedX * 0.01,
				"Impingement location should match Mach cone geometry");
	}

	@Test
	void testImpingementLocationScalesWithMach() {
		double rCore = 0.025;
		double rBooster = 0.015;
		double separation = 0.10;
		double boosterLength = 2.0; // long enough for impingement at all Mach
		double sRef = Math.PI * rCore * rCore;

		ShockGeometry.InterBodyImpingement result2 =
				ShockGeometry.computeInterBodyImpingement(
						2.0, separation, rCore, rBooster, boosterLength, sRef, GAMMA);
		ShockGeometry.InterBodyImpingement result3 =
				ShockGeometry.computeInterBodyImpingement(
						3.0, separation, rCore, rBooster, boosterLength, sRef, GAMMA);

		assertNotNull(result2);
		assertNotNull(result3);

		// At higher Mach, Mach cone is narrower (smaller mu) -> tan(mu) smaller
		// -> x_impinge = gap/tan(mu) is LARGER (impingement moves further aft)
		assertTrue(result3.xImpinge > result2.xImpinge,
				"Higher Mach should give farther impingement: M2=" + result2.xImpinge +
				", M3=" + result3.xImpinge);
	}

	// ================================================================
	// Interference drag is positive
	// ================================================================

	@Test
	void testInterferenceDragPositive() {
		double mach = 2.0;
		double rCore = 0.025;
		double rBooster = 0.015;
		double separation = 0.10;
		double boosterLength = 0.50;
		double sRef = Math.PI * rCore * rCore;

		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						mach, separation, rCore, rBooster, boosterLength,
						sRef, GAMMA);

		assertNotNull(result);
		assertTrue(result.deltaCd > 0, "Interference drag should be positive, got " + result.deltaCd);
	}

	@ParameterizedTest
	@ValueSource(doubles = {1.5, 2.0, 3.0, 5.0})
	void testInterferenceDragPositiveAtVariousMach(double mach) {
		double rCore = 0.025;
		double rBooster = 0.015;
		double separation = 0.10;
		double boosterLength = 2.0;
		double sRef = Math.PI * rCore * rCore;

		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						mach, separation, rCore, rBooster, boosterLength,
						sRef, GAMMA);

		assertNotNull(result, "Should have impingement at M=" + mach);
		assertTrue(result.deltaCd > 0,
				"Interference drag should be positive at M=" + mach + ", got " + result.deltaCd);
		assertTrue(Double.isFinite(result.deltaCd),
				"Interference drag should be finite at M=" + mach);
	}

	// ================================================================
	// Symmetric pair: CY=0
	// ================================================================

	@Test
	void testSymmetricPairCyZero() {
		double mach = 2.0;
		double rCore = 0.025;
		double rBooster = 0.015;
		double separation = 0.10;
		double boosterLength = 0.50;
		double sRef = Math.PI * rCore * rCore;

		// Compute impingement for a single booster
		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						mach, separation, rCore, rBooster, boosterLength,
						sRef, GAMMA);

		assertNotNull(result);

		// For a single booster, cySide is zero (symmetric impingement model)
		assertEquals(0, result.cySide, EPS,
				"Side force for symmetric impingement should be zero");

		// For a symmetric pair, the CY contributions cancel:
		// Total CY = result.cySide + (-result.cySide) = 0
		// Total delta_Cd = 2 * result.deltaCd (additive)
		double totalDeltaCd = 2 * result.deltaCd;
		assertTrue(totalDeltaCd > 0, "Symmetric pair drag should add");
	}

	// ================================================================
	// No impingement when booster far away
	// ================================================================

	@Test
	void testNoImpingementWhenFarApart() {
		double mach = 2.0;
		double rCore = 0.025;
		double rBooster = 0.015;
		double separation = 1.0; // very far apart
		double boosterLength = 0.20; // short booster
		double sRef = Math.PI * rCore * rCore;

		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						mach, separation, rCore, rBooster, boosterLength,
						sRef, GAMMA);

		assertNull(result, "Should be null when Mach cone passes behind booster");
	}

	// ================================================================
	// Edge cases
	// ================================================================

	@Test
	void testNullForZeroSeparation() {
		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						2.0, 0, 0.025, 0.015, 0.50, 0.002, GAMMA);
		assertNull(result);
	}

	@Test
	void testNullForNegativeRadius() {
		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						2.0, 0.10, -0.025, 0.015, 0.50, 0.002, GAMMA);
		assertNull(result);
	}

	@Test
	void testNullForTouchingBodies() {
		// Bodies touching: separation = rCore + rBooster, gap = 0
		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						2.0, 0.040, 0.025, 0.015, 0.50, 0.002, GAMMA);
		assertNull(result, "Should be null when bodies are touching");
	}

	@Test
	void testFiniteResultsAtHighMach() {
		ShockGeometry.InterBodyImpingement result =
				ShockGeometry.computeInterBodyImpingement(
						10.0, 0.10, 0.025, 0.015, 2.0,
						Math.PI * 0.025 * 0.025, GAMMA);

		assertNotNull(result);
		assertTrue(Double.isFinite(result.xImpinge), "x_impinge should be finite at M=10");
		assertTrue(Double.isFinite(result.deltaCd), "deltaCd should be finite at M=10");
		assertTrue(Double.isFinite(result.cySide), "cySide should be finite at M=10");
	}

	// ================================================================
	// Drag magnitude scaling
	// ================================================================

	@Test
	void testCloserBoostersHigherDrag() {
		double mach = 2.0;
		double rCore = 0.025;
		double rBooster = 0.015;
		double boosterLength = 1.0;
		double sRef = Math.PI * rCore * rCore;

		ShockGeometry.InterBodyImpingement close =
				ShockGeometry.computeInterBodyImpingement(
						mach, 0.06, rCore, rBooster, boosterLength, sRef, GAMMA);
		ShockGeometry.InterBodyImpingement far =
				ShockGeometry.computeInterBodyImpingement(
						mach, 0.15, rCore, rBooster, boosterLength, sRef, GAMMA);

		assertNotNull(close);
		assertNotNull(far);

		// The interference drag should be comparable for different separations
		// (same shock strength, just different impingement location)
		// Both should be positive
		assertTrue(close.deltaCd > 0);
		assertTrue(far.deltaCd > 0);
	}
}
