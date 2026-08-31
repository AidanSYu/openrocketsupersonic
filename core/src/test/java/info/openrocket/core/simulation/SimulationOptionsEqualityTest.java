package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import info.openrocket.core.util.BaseTestCase;

/**
 * Guards the reflexivity of {@link SimulationOptions#equals} against the
 * optional (NaN-defaulted) nozzle-geometry fields.
 * <p>
 * The nozzle exit diameters use {@link Double#NaN} to mean "not specified", and
 * only the RASAero importer ever sets them. Comparing them with an IEEE-754
 * equality (where {@code NaN != NaN}) makes a freshly-constructed options object
 * unequal to its own clone, which leaves every simulation permanently
 * {@code OUTDATED} and makes {@code copyConditionsFrom} overwrite the random
 * seed on every call -- the seed drives the launch-perturbation model, so that
 * silently changes simulation results.
 */
public class SimulationOptionsEqualityTest extends BaseTestCase {

	@Test
	public void testDefaultOptionsEqualItsClone() {
		SimulationOptions options = new SimulationOptions();
		assertEquals(options, options.clone(),
				"default options must equal their own clone (unset nozzle diameters are NaN)");
	}

	@Test
	public void testDefaultOptionsEqualThemselves() {
		SimulationOptions options = new SimulationOptions();
		assertEquals(options, options);
	}

	@Test
	public void testOptionsWithNozzleGeometryEqualItsClone() {
		SimulationOptions options = new SimulationOptions();
		for (int stage = 0; stage < 3; stage++) {
			options.setNozzleExitDiameterForStage(stage, 0.05 + 0.01 * stage);
		}
		assertEquals(options, options.clone());
	}

	@Test
	public void testSetNozzleDiameterStillBreaksEquality() {
		SimulationOptions unset = new SimulationOptions();
		SimulationOptions set = unset.clone();
		set.setNozzleExitDiameterForStage(0, 0.05);
		assertNotEquals(unset, set,
				"a specified nozzle diameter must still compare unequal to an unspecified one");
	}

	@Test
	public void testCopyConditionsFromUnchangedPreservesRandomSeed() {
		SimulationOptions source = new SimulationOptions();
		SimulationOptions target = source.clone();
		int originalSeed = target.getRandomSeed();

		// Give the source a different seed but leave everything else identical.
		source.setRandomSeed(originalSeed + 12345);
		target.copyConditionsFrom(source);

		assertEquals(originalSeed, target.getRandomSeed(),
				"randomSeed must only be copied when some other condition actually changed");
	}
}
