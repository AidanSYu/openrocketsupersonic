package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Tests for Phase 9e: Plume-Induced Flow Separation Model.
 *
 * Validates plume diameter estimation, blockage ratio, separation length,
 * fin overlap fraction, fin effectiveness knockdown, and activation conditions.
 */
public class PlumeInducedSeparationTest {

	private static final double EPSILON = 1e-6;

	// Typical motor: 54mm diameter, base diameter 76mm (3" airframe)
	private static final double MOTOR_DIAMETER = 0.054; // m
	private static final double BASE_DIAMETER = 0.076;  // m
	private static final double FIN_SPAN = 0.08;        // m
	private static final double SEA_LEVEL_PRESSURE = 101325.0; // Pa

	@Test
	@DisplayName("Sea level: no plume effect (p_exit approx p_ambient)")
	void testSeaLevel() {
		double exitPressure = PlumeModel.estimateExitPressure();
		double ambientPressure = SEA_LEVEL_PRESSURE;

		assertFalse(PlumeModel.isPlumeActive(true, exitPressure, ambientPressure),
				"Plume should not be active at sea level (ratio ~1)");

		double plumeDiam = PlumeModel.computePlumeDiameter(
				PlumeModel.estimateExitDiameter(MOTOR_DIAMETER),
				exitPressure, ambientPressure);
		// At sea level, exit pressure approximately equals ambient
		double exitDiam = PlumeModel.estimateExitDiameter(MOTOR_DIAMETER);
		assertEquals(exitDiam, plumeDiam, exitDiam * 0.01,
				"At sea level plume diameter should approximately equal exit diameter");
	}

	@Test
	@DisplayName("High altitude (30km): plume diameter 2-3x base diameter")
	void testHighAltitude() {
		double exitPressure = PlumeModel.estimateExitPressure(); // ~101 kPa
		double ambientPressure = 1200.0; // ~30km altitude, ~1.2 kPa

		assertTrue(PlumeModel.isPlumeActive(true, exitPressure, ambientPressure),
				"Plume should be active at 30km altitude");

		double exitDiam = PlumeModel.estimateExitDiameter(MOTOR_DIAMETER);
		double plumeDiam = PlumeModel.computePlumeDiameter(exitDiam, exitPressure, ambientPressure);

		// At 30km, pressure ratio ~84, sqrt(84) * 0.8 * exitDiam ~ 7.3 * exitDiam
		assertTrue(plumeDiam > BASE_DIAMETER,
				"Plume should exceed base diameter at 30km");
		assertTrue(plumeDiam > 2 * BASE_DIAMETER,
				"Plume should be > 2x base at 30km, got " + plumeDiam / BASE_DIAMETER + "x");
	}

	@Test
	@DisplayName("Fin effectiveness reduction at altitude")
	void testFinEffectivenessReduction() {
		double exitPressure = PlumeModel.estimateExitPressure();
		double ambientPressure = 3000.0; // ~25km

		double exitDiam = PlumeModel.estimateExitDiameter(MOTOR_DIAMETER);
		double plumeDiam = PlumeModel.computePlumeDiameter(exitDiam, exitPressure, ambientPressure);

		double overlap = PlumeModel.computeFinOverlapFraction(plumeDiam, BASE_DIAMETER, FIN_SPAN);
		double etaPlume = PlumeModel.computeFinEffectiveness(overlap);

		assertTrue(etaPlume < 1.0, "Fin effectiveness should be reduced at altitude");
		assertTrue(etaPlume >= 0.1, "Fin effectiveness should not go below minimum (0.1)");
	}

	@Test
	@DisplayName("Effect deactivates at burnout")
	void testBurnoutDeactivation() {
		double exitPressure = PlumeModel.estimateExitPressure();
		double ambientPressure = 1200.0; // High altitude

		// During burn
		assertTrue(PlumeModel.isPlumeActive(true, exitPressure, ambientPressure));

		// After burnout
		assertFalse(PlumeModel.isPlumeActive(false, exitPressure, ambientPressure),
				"Plume should not be active after burnout");
	}

	@Test
	@DisplayName("Blockage ratio scales with pressure ratio")
	void testBlockageRatioScaling() {
		double exitDiam = PlumeModel.estimateExitDiameter(MOTOR_DIAMETER);

		double plume1 = PlumeModel.computePlumeDiameter(exitDiam, 101325, 10000);
		double plume2 = PlumeModel.computePlumeDiameter(exitDiam, 101325, 2500);

		double blockage1 = PlumeModel.computeBlockageRatio(plume1, BASE_DIAMETER);
		double blockage2 = PlumeModel.computeBlockageRatio(plume2, BASE_DIAMETER);

		assertTrue(blockage2 > blockage1,
				"Higher altitude (lower ambient) should give larger blockage ratio");
	}

	@Test
	@DisplayName("Separation length increases with blockage")
	void testSeparationLength() {
		double sepLen1 = PlumeModel.computeSeparationLength(BASE_DIAMETER, 2.0);
		double sepLen2 = PlumeModel.computeSeparationLength(BASE_DIAMETER, 4.0);

		assertTrue(sepLen1 > 0, "Separation length should be positive");
		assertTrue(sepLen2 > sepLen1, "Higher blockage should give longer separation");

		// No separation when blockage <= 1
		assertEquals(0, PlumeModel.computeSeparationLength(BASE_DIAMETER, 1.0));
		assertEquals(0, PlumeModel.computeSeparationLength(BASE_DIAMETER, 0.5));
	}

	@Test
	@DisplayName("Fin overlap fraction in [0, 1]")
	void testFinOverlapFraction() {
		// Plume smaller than body — no overlap
		assertEquals(0, PlumeModel.computeFinOverlapFraction(
				BASE_DIAMETER * 0.5, BASE_DIAMETER, FIN_SPAN));

		// Plume just at body edge — no overlap
		assertEquals(0, PlumeModel.computeFinOverlapFraction(
				BASE_DIAMETER, BASE_DIAMETER, FIN_SPAN), EPSILON);

		// Plume extends halfway through fin span
		double plumeRadius = BASE_DIAMETER / 2.0 + FIN_SPAN / 2.0;
		double plumeDiam = 2.0 * plumeRadius;
		double overlap = PlumeModel.computeFinOverlapFraction(plumeDiam, BASE_DIAMETER, FIN_SPAN);
		assertEquals(0.5, overlap, 0.01, "Half-span plume should give ~50% overlap");

		// Very large plume — fully immersed but capped at 1.0
		overlap = PlumeModel.computeFinOverlapFraction(
				BASE_DIAMETER * 10, BASE_DIAMETER, FIN_SPAN);
		assertTrue(overlap <= 1.0, "Overlap should be capped at 1.0");
	}

	@Test
	@DisplayName("Fin effectiveness minimum is 0.1")
	void testFinEffectivenessMinimum() {
		// Fully immersed
		double eta = PlumeModel.computeFinEffectiveness(1.0);
		assertEquals(0.1, eta, EPSILON, "Fully immersed fin should have minimum effectiveness 0.1");

		// No overlap
		eta = PlumeModel.computeFinEffectiveness(0.0);
		assertEquals(1.0, eta, EPSILON, "No overlap should give full effectiveness");
	}

	@Test
	@DisplayName("No NaN or Inf at boundary conditions")
	void testBoundaryConditions() {
		// Zero exit diameter
		assertEquals(0, PlumeModel.computePlumeDiameter(0, 101325, 101325));

		// Zero ambient pressure
		assertFalse(PlumeModel.isPlumeActive(true, 101325, 0));

		// Zero base diameter
		assertEquals(0, PlumeModel.computeBlockageRatio(0.1, 0));

		// Negative pressure
		assertFalse(PlumeModel.isPlumeActive(true, 101325, -100));

		// Zero fin span
		assertEquals(0, PlumeModel.computeFinOverlapFraction(0.2, 0.1, 0));

		// Friction reduction edge cases
		assertEquals(1.0, PlumeModel.computeFrictionReduction(0, 1.0));
		assertEquals(1.0, PlumeModel.computeFrictionReduction(-1, 1.0));
		assertTrue(PlumeModel.computeFrictionReduction(0.5, 1.0) >= 0.5,
				"Friction reduction should be capped");
	}

	@Test
	@DisplayName("Pressure ratio threshold activates correctly")
	void testPressureRatioThreshold() {
		// Just below threshold
		assertFalse(PlumeModel.isPlumeActive(true, 101325, 101325 / 2.9));

		// At threshold
		assertTrue(PlumeModel.isPlumeActive(true, 101325, 101325 / 3.1));

		// Well above threshold
		assertTrue(PlumeModel.isPlumeActive(true, 101325, 101325 / 10.0));
	}
}
