package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests that the Phase 12 telemetry data types are populated during simulation.
 * Runs a simple Estes Alpha III simulation and checks that the new FlightDataType
 * channels contain data.
 */
public class DataPipelineTest extends BaseTestCase {

	private static FlightDataBranch branch;

	@BeforeAll
	public static void runSimulation() throws Exception {
		// BaseTestCase.setUp() handles Guice init via @BeforeAll
		BaseTestCase.setUp();

		Rocket rocket = TestRockets.makeEstesAlphaIII();
		Simulation simulation = new Simulation(rocket);
		simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		simulation.getOptions().setISAAtmosphere(true);
		simulation.getOptions().setTimeStep(0.05);
		simulation.getOptions().setRandomSeed(0xC0FFEE);

		simulation.simulate();

		branch = simulation.getSimulatedData().getBranch(0);
		assertNotNull(branch, "Simulation must produce a flight data branch");
	}

	@Test
	public void testFrictionDragCoeffPopulated() {
		List<Double> data = branch.get(FlightDataType.TYPE_FRICTION_DRAG_COEFF);
		assertNotNull(data, "Friction drag coefficient data should be present");
		assertFalse(data.isEmpty(), "Friction drag coefficient data should not be empty");
		assertTrue(hasFiniteValue(data), "Friction drag data should contain at least one finite value");
	}

	@Test
	public void testPressureDragCoeffPopulated() {
		List<Double> data = branch.get(FlightDataType.TYPE_PRESSURE_DRAG_COEFF);
		assertNotNull(data, "Pressure drag coefficient data should be present");
		assertFalse(data.isEmpty(), "Pressure drag coefficient data should not be empty");
		assertTrue(hasFiniteValue(data), "Pressure drag data should contain at least one finite value");
	}

	@Test
	public void testBaseDragCoeffPopulated() {
		List<Double> data = branch.get(FlightDataType.TYPE_BASE_DRAG_COEFF);
		assertNotNull(data, "Base drag coefficient data should be present");
		assertFalse(data.isEmpty(), "Base drag coefficient data should not be empty");
		assertTrue(hasFiniteValue(data), "Base drag data should contain at least one finite value");
	}

	@Test
	public void testStagnationTemperaturePopulated() {
		List<Double> data = branch.get(FlightDataType.TYPE_STAGNATION_TEMPERATURE);
		assertNotNull(data, "Stagnation temperature data should be present");
		assertFalse(data.isEmpty(), "Stagnation temperature data should not be empty");
		assertTrue(hasFiniteValue(data), "Stagnation temperature should contain at least one finite value");
	}

	@Test
	public void testStagnationTemperaturePhysicallyReasonable() {
		List<Double> data = branch.get(FlightDataType.TYPE_STAGNATION_TEMPERATURE);
		List<Double> airTemp = branch.get(FlightDataType.TYPE_AIR_TEMPERATURE);
		assertNotNull(data);
		assertNotNull(airTemp);

		for (int i = 0; i < data.size(); i++) {
			double t0 = data.get(i);
			double t = airTemp.get(i);
			if (Double.isFinite(t0) && Double.isFinite(t)) {
				// Stagnation temperature must be >= static temperature
				assertTrue(t0 >= t - 0.01,
						"Stagnation temperature " + t0 + " must be >= air temperature " + t);
			}
		}
	}

	@Test
	public void testPitchDampingMomentCoeffPopulated() {
		// This existed before Phase 12 but verify it's populated
		List<Double> data = branch.get(FlightDataType.TYPE_PITCH_DAMPING_MOMENT_COEFF);
		assertNotNull(data, "Pitch damping moment coefficient data should be present");
		assertFalse(data.isEmpty(), "Pitch damping moment coefficient data should not be empty");
	}

	@Test
	public void testPitchDampingDerivativePopulated() {
		List<Double> data = branch.get(FlightDataType.TYPE_PITCH_DAMPING_DERIVATIVE);
		assertNotNull(data, "Pitch damping derivative data should be present");
		assertFalse(data.isEmpty(), "Pitch damping derivative data should not be empty");
	}

	@Test
	public void testNaturalPitchFrequencyPopulated() {
		List<Double> data = branch.get(FlightDataType.TYPE_NATURAL_PITCH_FREQUENCY);
		assertNotNull(data, "Natural pitch frequency data should be present");
		assertFalse(data.isEmpty(), "Natural pitch frequency data should not be empty");
		assertTrue(hasFiniteValue(data), "Natural pitch frequency should contain at least one finite value");
	}

	/**
	 * Returns true if the list contains at least one finite (non-NaN, non-Inf) value.
	 */
	private static boolean hasFiniteValue(List<Double> data) {
		for (Double v : data) {
			if (v != null && Double.isFinite(v)) {
				return true;
			}
		}
		return false;
	}
}
