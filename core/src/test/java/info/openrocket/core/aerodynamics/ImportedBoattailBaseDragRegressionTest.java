package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.CoordinateIF;

/**
 * Regression coverage for imported RASAero boattails.
 *
 * <p>Several of the remaining SimVReal outliers are rockets whose imported
 * geometry ends in a boattail with no downstream tail tube. That aft exposed
 * area must contribute a positive base-drag term; if it collapses to zero, the
 * rocket will retain too much coast energy and overshoot apogee.
 */
public class ImportedBoattailBaseDragRegressionTest extends BaseTestCase {

	private static final double BASE_DRAG_EPS = 1.0e-6;

	@Test
	void testQu8kImportedBoattailHasPositiveBaseDrag() throws Exception {
		assertImportedBoattailHasPositiveBaseDrag("Qu8k.CDX1", "Qu8k");
	}

	@Test
	void testProteus6ImportedBoattailHasPositiveBaseDrag() throws Exception {
		assertImportedBoattailHasPositiveBaseDrag("Proteus6.CDX1", "Proteus6");
	}

	private void assertImportedBoattailHasPositiveBaseDrag(String cdx1File, String rocketName) throws Exception {
		File cdx1 = findSimVRealFile(cdx1File);
		assertTrue(cdx1.exists(), "CDX1 file not found: " + cdx1.getAbsolutePath());

		GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
		OpenRocketDocument doc = loader.load();
		Rocket rocket = doc.getRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		Transition boattail = null;
		for (RocketComponent component : rocket) {
			if (component instanceof Transition t && "Boattail".equals(t.getName())) {
				boattail = t;
			}
		}

		assertNotNull(boattail, rocketName + " did not import a boattail transition");

		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(2.0);
		conditions.setAOA(0.0);

		BarrowmanCalculator calculator = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();
		var forceMap = calculator.getForceAnalysis(config, conditions, warnings);
		var boattailForces = forceMap.get(boattail);

		assertNotNull(boattailForces, rocketName + " boattail missing from force analysis");
		assertTrue(boattail.getAftRadius() > 0.0,
				rocketName + " boattail aft radius must be positive to expose a base area");
		assertTrue(boattail.getNextSymmetricComponent() == null,
				rocketName + " boattail should terminate the symmetric body chain");
		String downstreamSummary = describeAbuttingCoaxialComponents(config, boattail);
		assertTrue(boattailForces.getBaseCD() > BASE_DRAG_EPS,
				String.format("%s boattail has exposed aft area but zero base drag (baseCD=%.6f, pressureCD=%.6f)%s",
						rocketName, boattailForces.getBaseCD(), boattailForces.getPressureCD(), downstreamSummary));
	}

	private File findSimVRealFile(String cdx1File) {
		Path current = Paths.get(System.getProperty("user.dir"));
		for (int i = 0; i < 5 && current != null; i++) {
			File candidate = current.resolve("simvreal").resolve("RasAero Sims").resolve(cdx1File).toFile();
			if (candidate.exists()) {
				return candidate;
			}
			current = current.getParent();
		}
		return new File("simvreal/RasAero Sims/" + cdx1File);
	}

	private String describeAbuttingCoaxialComponents(FlightConfiguration config, Transition boattail) {
		InstanceMap imap = config.getActiveInstances();
		ArrayList<InstanceContext> boattailContexts = imap.get(boattail);
		if (boattailContexts == null || boattailContexts.isEmpty()) {
			return " (no active boattail instances)";
		}

		CoordinateIF boattailLoc = boattailContexts.get(0).getLocation();
		double aftX = boattailLoc.getX() + boattail.getLength();
		double y = boattailLoc.getY();
		double z = boattailLoc.getZ();

		final double xTol = 1.0e-4;
		final double axisTol = 1.0e-4;
		List<String> matches = new ArrayList<>();
		for (var entry : imap.entrySet()) {
			RocketComponent component = entry.getKey();
			if (!(component instanceof SymmetricComponent symmetric) || component == boattail) {
				continue;
			}
			for (InstanceContext ctx : entry.getValue()) {
				CoordinateIF loc = ctx.getLocation();
				if (Math.abs(loc.getX() - aftX) <= xTol
						&& Math.abs(loc.getY() - y) <= axisTol
						&& Math.abs(loc.getZ() - z) <= axisTol) {
					matches.add(String.format("%s[%s] foreR=%.6f x=%.6f",
							component.getName(),
							component.getClass().getSimpleName(),
							symmetric.getForeRadius(),
							loc.getX()));
				}
			}
		}

		if (matches.isEmpty()) {
			return " (no abutting coaxial components found)";
		}
		return " (abutting coaxial components: " + String.join(", ", matches) + ")";
	}
}
