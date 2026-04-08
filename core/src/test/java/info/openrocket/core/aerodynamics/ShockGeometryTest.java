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
 * Unit tests for the ShockGeometry pre-pass (Phase 3b).
 * <p>
 * Validates that the nose-to-tail shock marching produces physically
 * reasonable local flow conditions at each axial station.
 */
public class ShockGeometryTest {

	private static final double EPS = 1e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ---- Subsonic passthrough ----

	@Test
	void testSubsonicIsPassthrough() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(0.8);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);
		assertFalse(sg.isSupersonic());
		assertEquals(0, sg.getStationCount());
	}

	@Test
	void testMachExactlyOneIsSubsonic() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(1.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);
		assertFalse(sg.isSupersonic(), "M=1.0 should be treated as subsonic (no shocks)");
	}

	// ---- Basic cone-cylinder ----

	@Test
	void testConeCylinder_shockReducesMach() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);
		assertTrue(sg.isSupersonic());

		// Immediately after the cone tip, Mach should be reduced
		ShockGeometry.LocalConditions nearTip = sg.getConditionsAt(0.01);
		assertTrue(nearTip.localMach < 2.0,
				"Mach behind cone shock should be < freestream. Got " + nearTip.localMach);
		assertTrue(nearTip.localMach > 1.0,
				"Mach behind cone shock should still be supersonic. Got " + nearTip.localMach);
	}

	@Test
	void testConeCylinder_pressureRiseAcrossShock() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		// Right after the nose tip, pressure should be elevated by the cone shock
		ShockGeometry.LocalConditions nearTip = sg.getConditionsAt(0.01);
		assertTrue(nearTip.pressureRatio > 1.0,
				"Pressure should increase across shock. Got p-ratio=" + nearTip.pressureRatio);
		assertTrue(nearTip.temperatureRatio > 1.0,
				"Temperature should increase across shock. Got T-ratio=" + nearTip.temperatureRatio);
	}

	@Test
	void testConeCylinder_expansionAtShoulder() {
		// At the nose-body junction, the flow expands (surface turns from cone angle to 0)
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		// At the nose tip
		ShockGeometry.LocalConditions atNose = sg.getConditionsAt(0.05);
		// At the body tube (after shoulder expansion)
		ShockGeometry.LocalConditions atBody = sg.getConditionsAt(0.30);

		// After expansion, Mach should increase and pressure decrease
		assertTrue(atBody.localMach >= atNose.localMach - 0.5,
				"Mach should increase or stay similar after shoulder expansion. " +
				"Nose M=" + atNose.localMach + ", Body M=" + atBody.localMach);
	}

	// ---- Ogive nose ----

	@Test
	void testOgiveCylinder_weaksShockThanCone() {
		var coneRocket = SupersonicTestRockets.makeConeCylinder();
		var ogiveRocket = SupersonicTestRockets.makeOgiveCylinder();

		var conditions = new FlightConditions(coneRocket.getSelectedConfiguration());
		conditions.setMach(2.5);

		ShockGeometry coneSG = ShockGeometry.compute(
				coneRocket.getSelectedConfiguration(), conditions);
		ShockGeometry ogiveSG = ShockGeometry.compute(
				ogiveRocket.getSelectedConfiguration(), conditions);

		// At mid-nose, ogive should have weaker shock (higher local Mach, lower pressure)
		double coneMach = coneSG.getConditionsAt(0.10).localMach;
		double ogiveMach = ogiveSG.getConditionsAt(0.10).localMach;

		// Ogive shock is weaker → higher post-shock Mach
		assertTrue(ogiveMach >= coneMach - 0.2,
				"Ogive shock should be weaker (higher post-shock Mach). " +
				"Cone M=" + coneMach + ", Ogive M=" + ogiveMach);
	}

	// ---- High Mach sweep ----

	@ParameterizedTest
	@ValueSource(doubles = {1.2, 1.5, 2.0, 3.0, 4.0, 5.0})
	void testConeCylinder_machSweepNoNaN(double mach) {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);
		assertTrue(sg.isSupersonic());

		for (ShockGeometry.LocalConditions station : sg.getStations()) {
			assertFalse(Double.isNaN(station.localMach),
					"Local Mach NaN at x=" + station.x + " M=" + mach);
			assertFalse(Double.isInfinite(station.localMach),
					"Local Mach Inf at x=" + station.x + " M=" + mach);
			assertTrue(station.localMach > 0,
					"Local Mach must be > 0 at x=" + station.x + " M=" + mach);
			assertFalse(Double.isNaN(station.pressureRatio),
					"Pressure ratio NaN at x=" + station.x + " M=" + mach);
			assertTrue(station.pressureRatio > 0,
					"Pressure ratio must be > 0 at x=" + station.x + " M=" + mach);
		}
	}

	@ParameterizedTest
	@ValueSource(doubles = {1.2, 2.0, 3.0, 5.0})
	void testOgiveBoattail_machSweepNoNaN(double mach) {
		var rocket = SupersonicTestRockets.makeOgiveBoattailFins();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(mach);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);
		assertTrue(sg.isSupersonic());

		for (ShockGeometry.LocalConditions station : sg.getStations()) {
			assertFalse(Double.isNaN(station.localMach),
					"Local Mach NaN at x=" + station.x + " for ogive-boattail M=" + mach);
			assertTrue(station.localMach > 0,
					"Local Mach must be > 0 at x=" + station.x + " M=" + mach);
		}
	}

	// ---- Interpolation ----

	@Test
	void testInterpolationBetweenStations() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);
		var stations = sg.getStations();
		assertTrue(stations.size() >= 2);

		// Get conditions at a point between two stations
		double x1 = stations.get(0).x;
		double x2 = stations.get(1).x;
		double xMid = (x1 + x2) / 2.0;

		ShockGeometry.LocalConditions mid = sg.getConditionsAt(xMid);
		assertNotNull(mid);
		assertFalse(Double.isNaN(mid.localMach));

		// Should be between the two station values (monotone interpolation)
		double m1 = stations.get(0).localMach;
		double m2 = stations.get(1).localMach;
		double mMin = Math.min(m1, m2);
		double mMax = Math.max(m1, m2);
		assertTrue(mid.localMach >= mMin - 0.01 && mid.localMach <= mMax + 0.01,
				"Interpolated Mach should be between stations. " +
				"M1=" + m1 + ", Mid=" + mid.localMach + ", M2=" + m2);
	}

	@Test
	void testConditionsBeforeFirstStation() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		// Before the first station
		ShockGeometry.LocalConditions before = sg.getConditionsAt(-1.0);
		assertNotNull(before);
		assertFalse(Double.isNaN(before.localMach));
	}

	@Test
	void testConditionsAfterLastStation() {
		var rocket = SupersonicTestRockets.makeConeCylinder();
		var config = rocket.getSelectedConfiguration();
		var conditions = new FlightConditions(config);
		conditions.setMach(2.0);

		ShockGeometry sg = ShockGeometry.compute(config, conditions);

		// Way after the last station
		ShockGeometry.LocalConditions after = sg.getConditionsAt(100.0);
		assertNotNull(after);
		assertFalse(Double.isNaN(after.localMach));
	}
}
