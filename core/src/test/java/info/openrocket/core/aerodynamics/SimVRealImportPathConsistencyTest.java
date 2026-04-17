package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.DatabaseMotorFinder;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.file.rasaero.importt.RASAeroLoader;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.util.BaseTestCase;

/**
 * SimVReal readiness depends on the batch benchmark being reproducible.
 * These checks guard against loader-path and cross-run state drift for Qu8k,
 * which is sensitive enough to expose hidden inconsistency.
 */
public class SimVRealImportPathConsistencyTest extends BaseTestCase {

	private static final double FT_PER_M = 3.28084;

	@BeforeAll
	static void loadMotors() {
		loadMotorsIntoRasaeroCache("simvreal/rasp.eng");
		loadMotorsIntoRasaeroCache("c:/Code/OpenRocket Plus/simvreal/rasp.eng");
	}

	@Test
	void testQu8kGeneralLoaderMatchesDirectRasaeroLoader() throws Exception {
		double viaGeneralLoader = simulateFeet(loadWithGeneralLoader("Qu8k.CDX1"));
		double viaDirectLoader = simulateFeet(loadDirectRasaero("Qu8k.CDX1"));

		assertEquals(viaDirectLoader, viaGeneralLoader, viaDirectLoader * 0.01,
				String.format("Qu8k apogee differs by import path: general=%.0f ft direct=%.0f ft",
						viaGeneralLoader, viaDirectLoader));
	}

	@Test
	void testQu8kFreshRunMatchesSequentialRun() throws Exception {
		double freshQu8k = simulateFeet(loadWithGeneralLoader("Qu8k.CDX1"));

		// Reproduce the benchmark neighborhood: Don't Debate This runs immediately before Qu8k.
		simulateFeet(loadWithGeneralLoader("DontDebateThisN5800MinDia.CDX1"));
		double qu8kAfterPriorRun = simulateFeet(loadWithGeneralLoader("Qu8k.CDX1"));

		assertEquals(freshQu8k, qu8kAfterPriorRun, freshQu8k * 0.01,
				String.format("Qu8k apogee drifted after a prior batch run: fresh=%.0f ft afterPrior=%.0f ft",
						freshQu8k, qu8kAfterPriorRun));
	}

	@Test
	void testQu8kExplicitMaximumAngleMatchesImportedDefault() throws Exception {
		double importedDefault = simulateFeet(loadDirectRasaero("Qu8k.CDX1"), false);
		double explicitThreeDegrees = simulateFeet(loadDirectRasaero("Qu8k.CDX1"), true);

		assertEquals(importedDefault, explicitThreeDegrees, importedDefault * 0.01,
				String.format("Qu8k apogee drifted when explicitly setting max angle step: default=%.0f ft explicit=%.0f ft",
						importedDefault, explicitThreeDegrees));
	}

	private static void loadMotorsIntoRasaeroCache(String path) {
		File file = new File(path);
		if (!file.exists()) {
			return;
		}
		try (InputStream stream = new FileInputStream(file)) {
			RASPMotorLoader loader = new RASPMotorLoader();
			List<ThrustCurveMotor.Builder> builders = loader.load(stream, "rasp.eng");
			for (ThrustCurveMotor.Builder builder : builders) {
				try {
					RASAeroMotorsLoader.addMotorToCache(builder.build());
				} catch (Exception ignored) {
					// Ignore duplicate/invalid cache entries; we only need the usable curves loaded.
				}
			}
		} catch (Exception ignored) {
			// If a local rasp.eng is unavailable, the tests below will fail with a clearer message.
		}
	}

	private OpenRocketDocument loadWithGeneralLoader(String cdx1File) throws Exception {
		File cdx1 = findSimvrealFile(cdx1File);
		assertTrue(cdx1.exists(), "CDX1 file not found: " + cdx1.getAbsolutePath());
		return new GeneralRocketLoader(cdx1).load();
	}

	private OpenRocketDocument loadDirectRasaero(String cdx1File) throws Exception {
		File cdx1 = findSimvrealFile(cdx1File);
		assertTrue(cdx1.exists(), "CDX1 file not found: " + cdx1.getAbsolutePath());

		OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
		DocumentLoadingContext context = new DocumentLoadingContext();
		context.setOpenRocketDocument(doc);
		context.setMotorFinder(new DatabaseMotorFinder());

		RASAeroLoader loader = new RASAeroLoader();
		try (InputStream stream = new BufferedInputStream(new FileInputStream(cdx1))) {
			loader.loadFromStream(context, stream, cdx1File.replace(".CDX1", ""));
		}
		return doc;
	}

	private double simulateFeet(OpenRocketDocument doc) throws Exception {
		return simulateFeet(doc, true);
	}

	private double simulateFeet(OpenRocketDocument doc, boolean setMaximumAngle) throws Exception {
		Simulation sim = doc.getSimulation(0);
		sim.getOptions().setTimeStep(0.05);
		if (setMaximumAngle) {
			sim.getOptions().setMaximumStepAngle(Math.toRadians(3.0));
		}
		sim.simulate();

		FlightData data = sim.getSimulatedData();
		return data.getMaxAltitude() * FT_PER_M;
	}

	private File findSimvrealFile(String cdx1File) {
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
}
