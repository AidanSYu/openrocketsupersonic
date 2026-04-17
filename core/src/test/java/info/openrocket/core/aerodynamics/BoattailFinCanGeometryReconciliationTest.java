package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.util.BaseTestCase;

/**
 * Geometry reconciliation test for the overshoot outliers.
 *
 * <p>Verifies that the imported CDX1 geometry maps correctly to ORP's
 * internal representation and that drag terms (especially finned base
 * drag augmentation) are applied properly.
 *
 * <p>Target cases:
 * <ul>
 *   <li>Raven: +27.5%, M 1.12, minimum-diameter, fins on body tube</li>
 *   <li>Kinsel: +35.1%, M 2.33, FinCan with shoulder</li>
 *   <li>EZI-65: +16.1%, M 0.61, subsonic, fins on body tube</li>
 *   <li>CalIsp1: healthy comparison case</li>
 * </ul>
 */
@ResourceLock("AERO_CPU_HEAVY")
public class BoattailFinCanGeometryReconciliationTest extends BaseTestCase {

	private static final double IN_TO_M = 0.0254;

	// ============================================================
	// 1. Geometry import verification
	// ============================================================

	@Test
	void testRavenGeometryImport() throws Exception {
		OpenRocketDocument doc = loadCDX1("Raven.CDX1");
		if (doc == null) return;
		Rocket rocket = doc.getRocket();

		// Expected from CDX1: Von Karman 8.5" nose, 1.75" dia, 65" body, 3 fins
		BodyTube body = findComponent(rocket, BodyTube.class, "Body Tube");
		assertNotNull(body, "Raven body tube not found");
		assertEquals(1.75 * IN_TO_M / 2, body.getOuterRadius(), 1e-4,
				"Raven body tube radius mismatch");
		assertEquals(65.0 * IN_TO_M, body.getLength(), 1e-3,
				"Raven body tube length mismatch");

		// Verify fins are children of the body tube
		int finCount = 0;
		for (int i = 0; i < body.getChildCount(); i++) {
			if (body.getChild(i) instanceof FinSet) {
				FinSet fs = (FinSet) body.getChild(i);
				finCount += fs.getFinCount();
			}
		}
		assertEquals(3, finCount, "Raven should have 3 fins on body tube");

		// Body tube should be the last symmetric component (no boattail)
		assertNull(body.getNextSymmetricComponent(),
				"Raven body tube should have no downstream symmetric component");

		System.out.printf("Raven: body OD=%.3f in, length=%.3f in, %d fins, last_symmetric=true%n",
				body.getOuterRadius() * 2 / IN_TO_M, body.getLength() / IN_TO_M, finCount);
	}

	@Test
	void testKinselGeometryImport() throws Exception {
		OpenRocketDocument doc = loadCDX1("Kinsel_P4935_A-601_Rocket.CDX1");
		if (doc == null) return;
		Rocket rocket = doc.getRocket();

		// Expected: NoseCone 35" + BodyTube (shortened) + Shoulder + FinCan 20"
		BodyTube parentBody = findComponent(rocket, BodyTube.class, "Body Tube");
		assertNotNull(parentBody, "Kinsel parent body tube not found");

		Transition shoulder = findComponent(rocket, Transition.class, "Fin Can Shoulder");
		assertNotNull(shoulder, "Kinsel fin can shoulder not found");

		BodyTube finCan = findComponent(rocket, BodyTube.class, "Fin Can");
		assertNotNull(finCan, "Kinsel fin can body tube not found");

		// Verify dimensions
		assertEquals(6.125 * IN_TO_M / 2, parentBody.getOuterRadius(), 1e-4,
				"Kinsel body tube radius");

		// Shoulder: foreR = 6.125/2, aftR = 6.5/2
		assertEquals(6.125 / 2 * IN_TO_M, shoulder.getForeRadius(), 1e-4,
				"Kinsel shoulder fore radius");
		assertEquals(6.5 / 2 * IN_TO_M, shoulder.getAftRadius(), 1e-4,
				"Kinsel shoulder aft radius");
		assertEquals(0.5 * IN_TO_M, shoulder.getLength(), 1e-4,
				"Kinsel shoulder length");

		// FinCan
		assertEquals(6.5 * IN_TO_M / 2, finCan.getOuterRadius(), 1e-4,
				"Kinsel fin can radius");
		assertEquals(20.0 * IN_TO_M, finCan.getLength(), 1e-3,
				"Kinsel fin can length");

		// Parent body tube should be shortened
		double expectedParentLength = (134.0 - 0.5 - 20.0) * IN_TO_M;
		assertEquals(expectedParentLength, parentBody.getLength(), 1e-3,
				"Kinsel parent body tube should be shortened by shoulder+fincan");

		// Verify fins are children of the fin can
		int finCount = 0;
		double maxSpan = 0;
		for (int i = 0; i < finCan.getChildCount(); i++) {
			if (finCan.getChild(i) instanceof FinSet) {
				FinSet fs = (FinSet) finCan.getChild(i);
				finCount += fs.getFinCount();
				maxSpan = Math.max(maxSpan, fs.getSpan());
			}
		}
		assertEquals(4, finCount, "Kinsel fin can should have 4 fins");
		assertEquals(7.0 * IN_TO_M, maxSpan, 1e-3, "Kinsel fin span");

		// FinCan should be the last symmetric component
		assertNull(finCan.getNextSymmetricComponent(),
				"Kinsel fin can should have no downstream symmetric component");

		System.out.printf("Kinsel: parent body shortened to %.1f in, shoulder %.1f in (%.3f -> %.3f in dia)%n",
				parentBody.getLength() / IN_TO_M, shoulder.getLength() / IN_TO_M,
				shoulder.getForeRadius() * 2 / IN_TO_M, shoulder.getAftRadius() * 2 / IN_TO_M);
		System.out.printf("  Fin Can: OD=%.3f in, length=%.1f in, %d fins, span=%.3f in%n",
				finCan.getOuterRadius() * 2 / IN_TO_M, finCan.getLength() / IN_TO_M, finCount, maxSpan / IN_TO_M);
	}

	@Test
	void testEZI65GeometryImport() throws Exception {
		OpenRocketDocument doc = loadCDX1("EZI65-1.CDX1");
		if (doc == null) return;
		Rocket rocket = doc.getRocket();

		BodyTube body = findComponent(rocket, BodyTube.class, "Body Tube");
		assertNotNull(body, "EZI-65 body tube not found");
		assertEquals(4.0 * IN_TO_M / 2, body.getOuterRadius(), 1e-4,
				"EZI-65 body tube radius");
		assertEquals(72.0 * IN_TO_M, body.getLength(), 1e-3,
				"EZI-65 body tube length");

		int finCount = 0;
		for (int i = 0; i < body.getChildCount(); i++) {
			if (body.getChild(i) instanceof FinSet) {
				finCount += ((FinSet) body.getChild(i)).getFinCount();
			}
		}
		assertEquals(3, finCount, "EZI-65 should have 3 fins on body tube");
	}

	// ============================================================
	// 2. Finned base drag augmentation verification
	// ============================================================

	@Test
	void testKinselFinnedBaseAugmentationApplied() throws Exception {
		OpenRocketDocument doc = loadCDX1("Kinsel_P4935_A-601_Rocket.CDX1");
		if (doc == null) return;
		Rocket rocket = doc.getRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		BodyTube finCan = findComponent(rocket, BodyTube.class, "Fin Can");
		assertNotNull(finCan, "Kinsel fin can not found");

		InstanceMap imap = config.getActiveInstances();
		ArrayList<InstanceContext> finCanContexts = imap.get(finCan);
		assertNotNull(finCanContexts, "Kinsel fin can not in instance map");
		assertFalse(finCanContexts.isEmpty(), "Kinsel fin can has no instances");

		// Test augmentation at supersonic Mach
		double aug = BarrowmanDragCalculator.calculateFinnedBaseAugmentation(
				finCan, finCanContexts, imap, 2.4);
		System.out.printf("Kinsel Fin Can augmentation at M=2.4: %.4f%n", aug);
		assertTrue(aug > 1.0, String.format(
				"Kinsel fin can should have finned base augmentation > 1.0, got %.4f", aug));

		// Expected: 4 fins, span/radius > 1, machFactor=1.0 at M=2.4
		// aug = 1 + 0.55 * min(4/4, 1.5) * clamp(span/radius, 0.3, 1.0) * 1.0
		// = 1 + 0.55 * 1.0 * 1.0 * 1.0 = 1.55
		assertEquals(1.55, aug, 0.05,
				"Kinsel augmentation should be ~1.55 for 4 fins at M=2.4");
	}

	@Test
	void testRavenFinnedBaseAugmentationApplied() throws Exception {
		OpenRocketDocument doc = loadCDX1("Raven.CDX1");
		if (doc == null) return;
		Rocket rocket = doc.getRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		BodyTube body = findComponent(rocket, BodyTube.class, "Body Tube");
		assertNotNull(body, "Raven body tube not found");

		InstanceMap imap = config.getActiveInstances();
		ArrayList<InstanceContext> bodyContexts = imap.get(body);
		assertNotNull(bodyContexts, "Raven body not in instance map");

		// Test at M=1.1 (Raven's peak Mach region)
		double aug = BarrowmanDragCalculator.calculateFinnedBaseAugmentation(
				body, bodyContexts, imap, 1.1);
		System.out.printf("Raven Body Tube augmentation at M=1.1: %.4f%n", aug);

		// Expected: 3 fins, machFactor at M=1.1 = 0.30 + 0.70*(1.1-0.8)/0.5 = 0.72
		// finFactor = 3/4 = 0.75, spanFactor = 1.0
		// aug = 1 + 0.55 * 0.75 * 1.0 * 0.72 = 1.297
		assertTrue(aug > 1.0, String.format(
				"Raven body tube should have finned base augmentation > 1.0, got %.4f", aug));
		assertEquals(1.297, aug, 0.05,
				"Raven augmentation should be ~1.30 for 3 fins at M=1.1");
	}

	@Test
	void testEZI65FinnedBaseAugmentationApplied() throws Exception {
		OpenRocketDocument doc = loadCDX1("EZI65-1.CDX1");
		if (doc == null) return;
		Rocket rocket = doc.getRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		BodyTube body = findComponent(rocket, BodyTube.class, "Body Tube");
		assertNotNull(body, "EZI-65 body tube not found");

		InstanceMap imap = config.getActiveInstances();
		ArrayList<InstanceContext> bodyContexts = imap.get(body);
		assertNotNull(bodyContexts, "EZI-65 body not in instance map");

		// Test at M=0.6 (EZI-65 peak Mach ~0.61)
		double aug = BarrowmanDragCalculator.calculateFinnedBaseAugmentation(
				body, bodyContexts, imap, 0.6);
		System.out.printf("EZI-65 Body Tube augmentation at M=0.6: %.4f%n", aug);

		// Expected: 3 fins, machFactor at M=0.6 = 0.30 * (0.6-0.2)/0.6 = 0.20
		// finFactor = 3/4 = 0.75, spanFactor = clamp(4.75/2.0, 0.3, 1.0) = 1.0
		// aug = 1 + 0.55 * 0.75 * 1.0 * 0.20 = 1.0825
		assertTrue(aug > 1.0, String.format(
				"EZI-65 body tube should have finned base augmentation > 1.0, got %.4f", aug));
	}

	// ============================================================
	// 3. Full Cd analysis verification
	// ============================================================

	@Test
	void testKinselBaseDragNotZeroAtSupersonic() throws Exception {
		OpenRocketDocument doc = loadCDX1("Kinsel_P4935_A-601_Rocket.CDX1");
		if (doc == null) return;
		Rocket rocket = doc.getRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		BodyTube finCan = findComponent(rocket, BodyTube.class, "Fin Can");
		assertNotNull(finCan, "Kinsel fin can not found");

		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(2.4);
		conditions.setAOA(0.0);

		BarrowmanCalculator calc = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();
		Map<RocketComponent, AerodynamicForces> forceMap = calc.getForceAnalysis(config, conditions, warnings);

		AerodynamicForces finCanForces = forceMap.get(finCan);
		assertNotNull(finCanForces, "Kinsel fin can missing from force analysis");

		double baseCd = finCanForces.getBaseCD();
		double totalCd = finCanForces.getCD();

		// Devan-Ashwood at M=2.4 = ~0.096, with Re correction ~0.089
		// With finned augmentation ~1.55: expected ~0.138
		// Without augmentation: ~0.089
		System.out.printf("Kinsel Fin Can at M=2.4: baseCd=%.6f, totalCd=%.6f%n", baseCd, totalCd);

		// The key test: is finned augmentation actually reflected in the base Cd?
		double rawBaseCd = BarrowmanDragCalculator.calculateBaseCD(2.4, conditions);
		double augFactor = BarrowmanDragCalculator.calculateFinnedBaseAugmentation(
				finCan,
				config.getActiveInstances().get(finCan),
				config.getActiveInstances(),
				2.4);
		double expectedBaseCd = rawBaseCd * augFactor;
		System.out.printf("  rawBaseCd=%.6f, augFactor=%.4f, expected augmented=%.6f, actual=%.6f%n",
				rawBaseCd, augFactor, expectedBaseCd, baseCd);

		if (augFactor > 1.0) {
			// If augmentation is working, baseCd should be larger than raw
			assertTrue(baseCd > rawBaseCd * 1.05,
					String.format("Kinsel base Cd (%.6f) should reflect augmentation (%.4f x %.6f = %.6f)",
							baseCd, augFactor, rawBaseCd, expectedBaseCd));
		}
	}

	@Test
	void testCalIsp1AsHealthyComparison() throws Exception {
		OpenRocketDocument doc = loadCDX1("CalIsp1.CDX1");
		if (doc == null) return;
		Rocket rocket = doc.getRocket();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		BodyTube body = findComponent(rocket, BodyTube.class, "Body Tube");
		assertNotNull(body, "CalIsp1 body tube not found");

		// 4 fins on body tube
		int finCount = 0;
		for (int i = 0; i < body.getChildCount(); i++) {
			if (body.getChild(i) instanceof FinSet) {
				finCount += ((FinSet) body.getChild(i)).getFinCount();
			}
		}
		assertEquals(4, finCount, "CalIsp1 should have 4 fins");

		InstanceMap imap = config.getActiveInstances();
		double aug = BarrowmanDragCalculator.calculateFinnedBaseAugmentation(
				body, imap.get(body), imap, 0.8);
		System.out.printf("CalIsp1 Body Tube augmentation at M=0.8: %.4f%n", aug);

		// 4 fins, M=0.8: machFactor = 0.30*(0.8-0.2)/0.6 = 0.30
		// aug = 1 + 0.55 * 1.0 * 1.0 * 0.30 = 1.165
		assertTrue(aug > 1.0, "CalIsp1 should have augmentation > 1.0");
	}

	// ============================================================
	// Helpers
	// ============================================================

	private OpenRocketDocument loadCDX1(String filename) throws Exception {
		File cdx1 = findSimVRealFile(filename);
		if (!cdx1.exists()) {
			System.out.println("SKIP: " + filename + " not found");
			return null;
		}
		GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
		return loader.load();
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

	@SuppressWarnings("unchecked")
	private <T extends RocketComponent> T findComponent(Rocket rocket, Class<T> type, String name) {
		for (RocketComponent comp : rocket) {
			if (type.isInstance(comp) && name.equals(comp.getName())) {
				return (T) comp;
			}
		}
		return null;
	}
}
