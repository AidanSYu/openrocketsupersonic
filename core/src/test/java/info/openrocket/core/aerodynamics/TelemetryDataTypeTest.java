package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

/**
 * Tests for Phase 12 telemetry FlightDataType constants.
 * Verifies all new data types are properly defined, have unique symbols/names,
 * appear in ALL_TYPES, and have non-empty localized names.
 */
public class TelemetryDataTypeTest extends BaseTestCase {

	// ---- 12a: Drag breakdown types (already existed, verify accessible) ----

	@Test
	public void testFrictionDragCoeffType() {
		assertNotNull(FlightDataType.TYPE_FRICTION_DRAG_COEFF);
		assertSame(UnitGroup.UNITS_COEFFICIENT, FlightDataType.TYPE_FRICTION_DRAG_COEFF.getUnitGroup());
		assertEquals("Cdf", FlightDataType.TYPE_FRICTION_DRAG_COEFF.getSymbol());
		assertFalse(FlightDataType.TYPE_FRICTION_DRAG_COEFF.getName().isBlank(),
				"Localization string must be non-empty");
	}

	@Test
	public void testPressureDragCoeffType() {
		assertNotNull(FlightDataType.TYPE_PRESSURE_DRAG_COEFF);
		assertSame(UnitGroup.UNITS_COEFFICIENT, FlightDataType.TYPE_PRESSURE_DRAG_COEFF.getUnitGroup());
		assertEquals("Cdp", FlightDataType.TYPE_PRESSURE_DRAG_COEFF.getSymbol());
		assertFalse(FlightDataType.TYPE_PRESSURE_DRAG_COEFF.getName().isBlank());
	}

	@Test
	public void testBaseDragCoeffType() {
		assertNotNull(FlightDataType.TYPE_BASE_DRAG_COEFF);
		assertSame(UnitGroup.UNITS_COEFFICIENT, FlightDataType.TYPE_BASE_DRAG_COEFF.getUnitGroup());
		assertEquals("Cdb", FlightDataType.TYPE_BASE_DRAG_COEFF.getSymbol());
		assertFalse(FlightDataType.TYPE_BASE_DRAG_COEFF.getName().isBlank());
	}

	@Test
	public void testFinLiftEffectivenessType() {
		assertNotNull(FlightDataType.TYPE_FIN_LIFT_EFFECTIVENESS);
		assertSame(UnitGroup.UNITS_COEFFICIENT, FlightDataType.TYPE_FIN_LIFT_EFFECTIVENESS.getUnitGroup());
		assertEquals("\u03b7f", FlightDataType.TYPE_FIN_LIFT_EFFECTIVENESS.getSymbol());
		assertFalse(FlightDataType.TYPE_FIN_LIFT_EFFECTIVENESS.getName().isBlank());
	}

	// ---- 12b: Dynamic stability & thermo-structural types ----

	@Test
	public void testFlutterMarginType() {
		assertNotNull(FlightDataType.TYPE_FLUTTER_MARGIN);
		assertSame(UnitGroup.UNITS_COEFFICIENT, FlightDataType.TYPE_FLUTTER_MARGIN.getUnitGroup());
		assertEquals("FM", FlightDataType.TYPE_FLUTTER_MARGIN.getSymbol());
		assertFalse(FlightDataType.TYPE_FLUTTER_MARGIN.getName().isBlank());
	}

	@Test
	public void testMagnusSideForceType() {
		assertNotNull(FlightDataType.TYPE_MAGNUS_SIDE_FORCE);
		// Phase 11a: Magnus side force slope is a dimensionless coefficient (Cy_pa)
		assertSame(UnitGroup.UNITS_COEFFICIENT, FlightDataType.TYPE_MAGNUS_SIDE_FORCE.getUnitGroup());
		assertEquals("Cy_pa", FlightDataType.TYPE_MAGNUS_SIDE_FORCE.getSymbol());
		assertFalse(FlightDataType.TYPE_MAGNUS_SIDE_FORCE.getName().isBlank());
	}

	@Test
	public void testConingAngleType() {
		assertNotNull(FlightDataType.TYPE_CONING_ANGLE);
		assertSame(UnitGroup.UNITS_ANGLE, FlightDataType.TYPE_CONING_ANGLE.getUnitGroup());
		assertEquals("\u03b1c", FlightDataType.TYPE_CONING_ANGLE.getSymbol());
		assertFalse(FlightDataType.TYPE_CONING_ANGLE.getName().isBlank());
	}

	@Test
	public void testRollRateAlreadyExists() {
		// TYPE_ROLL_RATE should already exist from baseline
		assertNotNull(FlightDataType.TYPE_ROLL_RATE);
		assertFalse(FlightDataType.TYPE_ROLL_RATE.getName().isBlank());
	}

	@Test
	public void testNaturalPitchFrequencyType() {
		assertNotNull(FlightDataType.TYPE_NATURAL_PITCH_FREQUENCY);
		assertSame(UnitGroup.UNITS_FREQUENCY, FlightDataType.TYPE_NATURAL_PITCH_FREQUENCY.getUnitGroup());
		assertEquals("fn", FlightDataType.TYPE_NATURAL_PITCH_FREQUENCY.getSymbol());
		assertFalse(FlightDataType.TYPE_NATURAL_PITCH_FREQUENCY.getName().isBlank());
	}

	@Test
	public void testStagnationTemperatureType() {
		assertNotNull(FlightDataType.TYPE_STAGNATION_TEMPERATURE);
		assertSame(UnitGroup.UNITS_TEMPERATURE, FlightDataType.TYPE_STAGNATION_TEMPERATURE.getUnitGroup());
		assertEquals("T0", FlightDataType.TYPE_STAGNATION_TEMPERATURE.getSymbol());
		assertFalse(FlightDataType.TYPE_STAGNATION_TEMPERATURE.getName().isBlank());
	}

	@Test
	public void testPitchDampingDerivativeType() {
		assertNotNull(FlightDataType.TYPE_PITCH_DAMPING_DERIVATIVE);
		assertSame(UnitGroup.UNITS_COEFFICIENT, FlightDataType.TYPE_PITCH_DAMPING_DERIVATIVE.getUnitGroup());
		assertEquals("Cmq", FlightDataType.TYPE_PITCH_DAMPING_DERIVATIVE.getSymbol());
		assertFalse(FlightDataType.TYPE_PITCH_DAMPING_DERIVATIVE.getName().isBlank());
	}

	// ---- Cross-cutting: ALL_TYPES and uniqueness ----

	@Test
	public void testAllNewTypesInAllTypesArray() {
		List<FlightDataType> allTypes = Arrays.asList(FlightDataType.ALL_TYPES);

		assertTrue(allTypes.contains(FlightDataType.TYPE_FRICTION_DRAG_COEFF), "FRICTION_DRAG_COEFF missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_PRESSURE_DRAG_COEFF), "PRESSURE_DRAG_COEFF missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_BASE_DRAG_COEFF), "BASE_DRAG_COEFF missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_FIN_LIFT_EFFECTIVENESS), "FIN_LIFT_EFFECTIVENESS missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_FLUTTER_MARGIN), "FLUTTER_MARGIN missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_MAGNUS_SIDE_FORCE), "MAGNUS_SIDE_FORCE missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_CONING_ANGLE), "CONING_ANGLE missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_NATURAL_PITCH_FREQUENCY), "NATURAL_PITCH_FREQUENCY missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_STAGNATION_TEMPERATURE), "STAGNATION_TEMPERATURE missing from ALL_TYPES");
		assertTrue(allTypes.contains(FlightDataType.TYPE_PITCH_DAMPING_DERIVATIVE), "PITCH_DAMPING_DERIVATIVE missing from ALL_TYPES");
	}

	@Test
	public void testAllTypesHaveUniqueSymbols() {
		Set<String> symbols = new HashSet<>();
		for (FlightDataType type : FlightDataType.ALL_TYPES) {
			assertTrue(symbols.add(type.getSymbol()),
					"Duplicate symbol found: " + type.getSymbol() + " (" + type.getName() + ")");
		}
	}
}
