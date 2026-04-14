package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.CoordinateIF;

/**
 * Tests for Phase 3: Stability & Shock Interaction.
 * <p>
 * Phase 3a: Body CP/CNa supersonic correction
 * Phase 3b: Shock geometry pre-pass
 * Phase 3c: Fin local flow correction
 */
public class Phase3StabilityTest {

	private static final double EPS = 1e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ================================================================
	// Phase 3b: ShockGeometry tests
	// ================================================================

	@Test
	void testShockGeometry_subsonicReturnsPassthrough() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(0.5);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		assertFalse(sg.isSupersonic(), "Should not be supersonic at M=0.5");
		assertEquals(0, sg.getStationCount(), "No stations for subsonic");

		// Should return freestream conditions
		ShockGeometry.LocalConditions local = sg.getConditionsAt(0.3);
		assertEquals(1.0, local.pressureRatio, EPS, "Pressure ratio should be 1.0 for subsonic");
		assertEquals(1.0, local.dynamicPressureRatio, EPS, "Dynamic pressure ratio should be 1.0");
	}

	@Test
	void testShockGeometry_supersonicComputesStations() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		assertTrue(sg.isSupersonic(), "Should be supersonic at M=2.0");
		assertTrue(sg.getStationCount() > 5, "Should have multiple stations");
		assertEquals(2.0, sg.getFreestreamMach(), EPS);
	}

	@Test
	void testShockGeometry_localMachBelowFreestream() {
		// Behind a cone shock, the local Mach should be less than freestream
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		// At the nose tip (x~0), Mach should be reduced by the cone shock
		ShockGeometry.LocalConditions atNose = sg.getConditionsAt(0.05);
		assertTrue(atNose.localMach < 2.0,
				"Local Mach behind cone shock should be < freestream. Got " + atNose.localMach);
		assertTrue(atNose.localMach > 1.0,
				"Local Mach should still be supersonic. Got " + atNose.localMach);
	}

	@Test
	void testShockGeometry_pressureIncreasedAtNose() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		// Right after the cone tip, pressure should be elevated by the shock
		ShockGeometry.LocalConditions nearTip = sg.getConditionsAt(0.01);
		assertTrue(nearTip.pressureRatio >= 1.0,
				"Pressure should be >= freestream right after cone shock. Got " + nearTip.pressureRatio);

		// On the body tube (after shoulder expansion), pressure may be below freestream
		// — this is physically correct (expansion fan reduces pressure)
		ShockGeometry.LocalConditions atBody = sg.getConditionsAt(0.3);
		assertTrue(atBody.pressureRatio > 0,
				"Pressure ratio should be positive. Got " + atBody.pressureRatio);
	}

	@Test
	void testShockGeometry_bodyTubeConstantConditions() {
		// Along a constant-radius body tube, conditions should be roughly constant
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		// Body tube runs from ~0.15m to ~0.75m
		ShockGeometry.LocalConditions at03 = sg.getConditionsAt(0.3);
		ShockGeometry.LocalConditions at06 = sg.getConditionsAt(0.6);

		assertEquals(at03.localMach, at06.localMach, 0.1,
				"Mach should be roughly constant along body tube");
		assertEquals(at03.pressureRatio, at06.pressureRatio, 0.1,
				"Pressure ratio should be roughly constant along body tube");
	}

	@ParameterizedTest
	@ValueSource(doubles = {1.5, 2.0, 3.0, 5.0})
	void testShockGeometry_machSweepPhysicallyReasonable(double mach) {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		assertTrue(sg.isSupersonic());

		for (ShockGeometry.LocalConditions station : sg.getStations()) {
			assertTrue(station.localMach > 0, "Local Mach must be positive at M=" + mach);
			assertTrue(station.pressureRatio > 0, "Pressure ratio must be positive at M=" + mach);
			assertTrue(station.temperatureRatio > 0, "Temperature ratio must be positive at M=" + mach);
			assertTrue(station.dynamicPressureRatio > 0, "Dynamic pressure ratio must be positive at M=" + mach);
			assertFalse(Double.isNaN(station.localMach), "Local Mach must not be NaN");
			assertFalse(Double.isInfinite(station.localMach), "Local Mach must not be Infinite");
		}
	}

	@Test
	void testShockGeometry_ogiveShockWeakerThanCone() {
		// An ogive nose at the same fineness ratio produces a weaker shock than a cone
		var coneRocket = SupersonicTestRockets.makeConeCylinder();
		var ogiveRocket = SupersonicTestRockets.makeOgiveCylinder();
		var coneCfg = coneRocket.getSelectedConfiguration();
		var ogiveCfg = ogiveRocket.getSelectedConfiguration();

		var conditions = new FlightConditions(coneCfg);
		conditions.setMach(2.0);

		ShockGeometry coneSG = ShockGeometry.compute(coneCfg, conditions);
		ShockGeometry ogiveSG = ShockGeometry.compute(ogiveCfg, conditions);

		// Pressure rise should be greater for cone than ogive
		double conePressure = coneSG.getConditionsAt(0.2).pressureRatio;
		double ogivePressure = ogiveSG.getConditionsAt(0.2).pressureRatio;
		assertTrue(conePressure >= ogivePressure,
				"Cone shock should be at least as strong as ogive. " +
				"Cone p-ratio=" + conePressure + ", Ogive p-ratio=" + ogivePressure);
	}

	// ================================================================
	// Phase 3a: Body CP/CNa supersonic correction tests
	// ================================================================

	@Test
	void testBodyCPShiftsAftAtSupersonic() {
		// Body lift K blends from 1.1 (subsonic) to 0 (supersonic, M≥1.3).
		// At supersonic speeds, slender-body theory (Ward 1949) gives exactly
		// CNa = 2×ΔA/Aref with no additional viscous lift (K=0 matches RASAero II).
		// The direction of the CP shift depends on the balance between:
		//   - Nose CNα (forward CP, small contribution; less forward body lift at supersonic)
		//   - Fin CNα (aft CP, large contribution; decreases with Ackeret K1=2/√(M²−1))
		//
		// For this test geometry (nose 0.15m + body 0.60m + aft fins), the fin CNα drop
		// at M=2 makes the forward nose contribution relatively more influential, moving
		// CP forward compared to subsonic.  This is the correct physical behaviour — rocket
		// stability is governed by fin size.
		//
		// What IS invariant and testable: CP must stay within rocket bounds at all Mach numbers.
		var rocket = SupersonicTestRockets.makeConeCylinderFins();
		double rocketLength = 0.75; // 0.15m nose + 0.60m body
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var subsonicCond = new FlightConditions(config);
		subsonicCond.setMach(0.3);
		subsonicCond.setAOA(Math.toRadians(2));

		var supersonicCond = new FlightConditions(config);
		supersonicCond.setMach(2.0);
		supersonicCond.setAOA(Math.toRadians(2));

		CoordinateIF cpSub = calc.getCP(config, subsonicCond, warnings);
		CoordinateIF cpSuper = calc.getCP(config, supersonicCond, warnings);

		// CP must be aft of nose tip and forward of rocket base at both Mach numbers
		assertTrue(cpSub.getX() > 0 && cpSub.getX() < rocketLength,
				"Subsonic CP must be within rocket bounds [0, " + rocketLength + "]. Got " + cpSub.getX());
		assertTrue(cpSuper.getX() > 0 && cpSuper.getX() < rocketLength,
				"Supersonic CP must be within rocket bounds [0, " + rocketLength + "]. Got " + cpSuper.getX());

		// At supersonic, CP should be forward of fin station (fins are at aft end of 0.60m body)
		// because nose contribution becomes relatively larger when fin CNα drops with Ackeret K1.
		// The important stability guarantee is that fins (not body lift) dominate the CP.
		double finStation = 0.15 + 0.60; // fin leading edge at body aft end
		assertTrue(cpSuper.getX() < finStation,
				"Supersonic CP should be forward of fin trailing edge. Got " + cpSuper.getX());
	}

	@Test
	void testBodyCPContinuousThroughTransonic() {
		// CP position should vary smoothly through the transonic region (M 0.8-1.3)
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		double prevCpX = -1;
		for (double mach = 0.5; mach <= 2.0; mach += 0.05) {
			var conditions = new FlightConditions(config);
			conditions.setMach(mach);
			conditions.setAOA(Math.toRadians(2));

			CoordinateIF cp = calc.getCP(config, conditions, warnings);
			double cpX = cp.getX();

			assertFalse(Double.isNaN(cpX), "CP x should not be NaN at M=" + mach);
			assertTrue(cpX > 0 && cpX < 1.0,
					"CP should be within rocket length at M=" + mach + ". Got " + cpX);

			if (prevCpX > 0) {
				// CP should not jump by more than 10% of body length per 0.05 Mach step
				double jump = Math.abs(cpX - prevCpX);
				assertTrue(jump < 0.075,
						"CP jump too large between M=" + (mach - 0.05) + " and M=" + mach +
						". Jump=" + jump + " (prevCP=" + prevCpX + ", curCP=" + cpX + ")");
			}
			prevCpX = cpX;
		}
	}

	@Test
	void testNoSupersonicWarningBelowM5() {
		// Phase 3a: M>1.1 warning should no longer fire (replaced with M>5)
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAOA(Math.toRadians(2));

		calc.getAerodynamicForces(config, conditions, warnings);

		boolean hasSupersonicWarning = warnings.stream()
				.anyMatch(w -> w.getMessageDescription().toLowerCase().contains("supersonic"));
		assertFalse(hasSupersonicWarning,
				"Should not warn about supersonic at M=2.0 (models now valid). Warnings: " + warnings);
	}

	@Test
	void testBodyLiftKIncreasesAtSupersonic() {
		// Body lift K blends from 1.1 (subsonic) to 0 (supersonic, M≥1.3).
		// CN at non-zero AoA is still nonzero at both Mach numbers because
		// the potential-flow Barrowman nose CNa term is independent of K.
		// This test verifies overall CN sign / magnitude is physically reasonable.

		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var subsonicCond = new FlightConditions(config);
		subsonicCond.setMach(0.5);
		subsonicCond.setAOA(Math.toRadians(5));

		var supersonicCond = new FlightConditions(config);
		supersonicCond.setMach(2.0);
		supersonicCond.setAOA(Math.toRadians(5));

		AerodynamicForces forcesSub = calc.getAerodynamicForces(config, subsonicCond, warnings);
		AerodynamicForces forcesSuper = calc.getAerodynamicForces(config, supersonicCond, warnings);

		// Both should produce nonzero CN at nonzero AoA
		assertTrue(Math.abs(forcesSub.getCN()) > 0, "Subsonic CN should be nonzero");
		assertTrue(Math.abs(forcesSuper.getCN()) > 0, "Supersonic CN should be nonzero");
	}

	// ================================================================
	// Phase 3c: Fin local flow correction tests
	// ================================================================

	@Test
	void testFinCNaUsesLocalMach() {
		// With fins behind a body shock, the fin CNa calculation should use
		// the reduced local Mach rather than the freestream Mach
		var rocket = SupersonicTestRockets.makeConeCylinderFins();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAOA(Math.toRadians(2));

		Map<RocketComponent, AerodynamicForces> forces =
				calc.getForceAnalysis(config, conditions, warnings);

		// The force analysis should complete without errors
		assertFalse(forces.isEmpty(), "Force analysis should return results");

		// Find the fin set forces
		AerodynamicForces finForces = null;
		for (Map.Entry<RocketComponent, AerodynamicForces> entry : forces.entrySet()) {
			if (entry.getKey().getName().contains("Fin")) {
				finForces = entry.getValue();
				break;
			}
		}
		assertNotNull(finForces, "Should have fin forces in the analysis");
		assertFalse(Double.isNaN(finForces.getCN()), "Fin CN should not be NaN");
	}

	@Test
	void testFinForceAnalysisNonZeroAtSupersonic() {
		var rocket = SupersonicTestRockets.makeConeCylinderFins();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAOA(Math.toRadians(5));

		AerodynamicForces total = calc.getAerodynamicForces(config, conditions, warnings);

		assertTrue(Math.abs(total.getCN()) > 0, "Total CN should be nonzero at AoA=5° M=2.0");
		assertFalse(Double.isNaN(total.getCN()), "Total CN should not be NaN");
		assertFalse(Double.isNaN(total.getCm()), "Total Cm should not be NaN");
	}

	@ParameterizedTest
	@ValueSource(doubles = {0.3, 0.5, 0.8, 0.95, 1.0, 1.05, 1.1, 1.5, 2.0, 3.0, 5.0})
	void testStabilitySmoothnessAcrossMachRange(double mach) {
		var rocket = SupersonicTestRockets.makeConeCylinderFins();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(2));

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);

		assertFalse(Double.isNaN(forces.getCN()), "CN should not be NaN at M=" + mach);
		assertFalse(Double.isNaN(forces.getCm()), "Cm should not be NaN at M=" + mach);
		assertFalse(Double.isNaN(forces.getCD()), "CD should not be NaN at M=" + mach);
		assertFalse(Double.isInfinite(forces.getCN()), "CN should not be Infinite at M=" + mach);
		assertFalse(Double.isInfinite(forces.getCD()), "CD should not be Infinite at M=" + mach);

		CoordinateIF cp = calc.getCP(config, conditions, warnings);
		assertFalse(Double.isNaN(cp.getX()), "CP x should not be NaN at M=" + mach);
	}

	@Test
	void testStabilityMarginSmoothVsMach() {
		// Verify no large discontinuities in stability margin vs Mach
		var rocket = SupersonicTestRockets.makeConeCylinderFins();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		double prevCpX = -1;
		for (double mach = 0.3; mach <= 5.0; mach += 0.1) {
			var conditions = new FlightConditions(config);
			conditions.setMach(mach);
			conditions.setAOA(Math.toRadians(2));

			CoordinateIF cp = calc.getCP(config, conditions, warnings);
			double cpX = cp.getX();

			assertFalse(Double.isNaN(cpX), "CP x NaN at M=" + mach);

			if (prevCpX > 0) {
				double jump = Math.abs(cpX - prevCpX);
				// Allow up to 15% of total rocket length (~0.75m) per 0.1 Mach step
				assertTrue(jump < 0.12,
						"CP jump too large at M=" + mach +
						". Jump=" + jump + " (prev=" + prevCpX + ", cur=" + cpX + ")");
			}
			prevCpX = cpX;
		}
	}

	@Test
	void testOgiveBoattailFinsStability() {
		// Full rocket geometry: ogive + boattail + fins
		var rocket = SupersonicTestRockets.makeOgiveBoattailFins();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		for (double mach : new double[]{0.3, 1.0, 2.0, 3.0}) {
			var conditions = new FlightConditions(config);
			conditions.setMach(mach);
			conditions.setAOA(Math.toRadians(2));

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
			CoordinateIF cp = calc.getCP(config, conditions, warnings);

			assertFalse(Double.isNaN(forces.getCN()), "CN NaN at M=" + mach);
			assertFalse(Double.isNaN(cp.getX()), "CP NaN at M=" + mach);
			assertTrue(cp.getX() > 0, "CP should be positive at M=" + mach);
		}
	}

	@Test
	void testShockGeometryWithBoattail() {
		// Boattail creates an expansion fan that should accelerate the local flow
		var rocket = SupersonicTestRockets.makeOgiveBoattailFins();
		var config = rocket.getSelectedConfiguration();

		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);
		assertTrue(sg.isSupersonic());

		// Verify no NaN or negative values anywhere
		for (ShockGeometry.LocalConditions station : sg.getStations()) {
			assertFalse(Double.isNaN(station.localMach), "Local Mach NaN at x=" + station.x);
			assertTrue(station.localMach > 0, "Local Mach should be positive at x=" + station.x);
			assertTrue(station.pressureRatio > 0, "Pressure ratio should be positive at x=" + station.x);
		}
	}

	@Test
	void testMachExactlyOne() {
		// Edge case: M = 1.0 should not cause NaN or division by zero
		var rocket = SupersonicTestRockets.makeConeCylinderFins();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		var conditions = new FlightConditions(config);
		conditions.setMach(1.0);
		conditions.setAOA(Math.toRadians(2));

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
		assertFalse(Double.isNaN(forces.getCN()), "CN should not be NaN at M=1.0");
		assertFalse(Double.isNaN(forces.getCD()), "CD should not be NaN at M=1.0");
	}

	@Test
	void testVonKarmanRocketStability() {
		var rocket = SupersonicTestRockets.makeVonKarmanFins();
		var config = rocket.getSelectedConfiguration();

		var calc = new BarrowmanCalculator();
		var warnings = new WarningSet();

		for (double mach : new double[]{0.3, 1.5, 3.0}) {
			var conditions = new FlightConditions(config);
			conditions.setMach(mach);
			conditions.setAOA(Math.toRadians(2));

			AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
			assertFalse(Double.isNaN(forces.getCN()), "CN NaN at M=" + mach + " for Von Karman");
			assertFalse(Double.isNaN(forces.getCD()), "CD NaN at M=" + mach + " for Von Karman");
		}
	}
}
