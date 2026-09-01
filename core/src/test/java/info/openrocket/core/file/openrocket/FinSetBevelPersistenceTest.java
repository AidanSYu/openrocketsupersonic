package info.openrocket.core.file.openrocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.aerodynamics.BarrowmanDragCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.StorageOptions;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.file.rasaero.export.FinDTO;
import info.openrocket.core.logging.ErrorSet;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FreeformFinSet;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.util.BaseTestCase;

/**
 * The leading-edge bevel length decides whether a fin edge is aerodynamically sharp or a
 * blunt chamfer, and so directly changes supersonic drag. It is imported from RASAero's
 * {@code FX1}, which means it has to survive every path a component takes afterwards --
 * saving, reloading, undo/redo, and conversion to a freeform fin set -- or a rocket
 * silently flies differently after a save or an edit.
 */
public class FinSetBevelPersistenceTest extends BaseTestCase {

	private static final double IN = 0.0254;
	private static final double BEVEL = 0.125 * IN;

	@TempDir
	Path tempDir;

	private static TrapezoidFinSet finsOf(Rocket rocket) {
		Iterator<RocketComponent> it = rocket.iterator(true);
		while (it.hasNext()) {
			RocketComponent c = it.next();
			if (c instanceof TrapezoidFinSet) {
				return (TrapezoidFinSet) c;
			}
		}
		fail("test rocket has no trapezoidal fin set");
		return null;
	}

	/**
	 * A minimal body tube with one swept, hexagonal fin set -- no motor mount.
	 * <p>
	 * Deliberately not one of the {@code TestRockets} fixtures: those carry motor
	 * configurations, and reloading a saved rocket that references a motor resolves it
	 * through a {@code MotorDatabase}, which the core test harness does not bind. The fin
	 * geometry is a chamfered plate fin (0.25 in stock, 0.125 in bevel = 45 deg edge) with
	 * enough sweep that the leading edge is still supersonic at M = 2.5.
	 *
	 * @param bevel leading-edge bevel length in metres, or NaN to leave it unspecified
	 */
	private static Rocket bevelledRocket(double bevel) {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		BodyTube tube = new BodyTube(1.0, 0.05, 0.002);
		stage.addChild(tube);

		TrapezoidFinSet fins = new TrapezoidFinSet();
		fins.setFinCount(4);
		fins.setRootChord(17 * IN);
		fins.setTipChord(6 * IN);
		fins.setHeight(7 * IN);
		fins.setSweep(9.5 * IN);
		fins.setThickness(0.25 * IN);
		fins.setCrossSection(FinSet.CrossSection.HEXAGONAL);
		fins.setLeadingEdgeBevelLength(bevel);
		tube.addChild(fins);

		// Without this the rocket's flight configuration never refreshes, so it keeps the
		// default reference length instead of the body tube's diameter and every coefficient
		// comes out scaled by the wrong reference area.
		rocket.enableEvents();

		return rocket;
	}

	private static Rocket bevelledRocket() {
		return bevelledRocket(BEVEL);
	}

	private static Rocket sharpRocket() {
		return bevelledRocket(Double.NaN);
	}

	@Test
	public void bevelSurvivesSaveAndReload() {
		Rocket reloaded = saveAndLoad(bevelledRocket()).getRocket();
		assertEquals(BEVEL, finsOf(reloaded).getLeadingEdgeBevelLength(), 1e-12,
				"leading-edge bevel must survive an .ork round trip");
	}

	@Test
	public void unknownBevelWritesNothingAndStaysUnknown() {
		// Fins that never stated a bevel must produce exactly the file they always did, so
		// existing rockets are untouched and older readers see no unfamiliar element.
		Rocket rocket = sharpRocket();
		assertTrue(Double.isNaN(finsOf(rocket).getLeadingEdgeBevelLength()),
				"test premise: this fin set states no bevel");

		assertFalse(saveToString(rocket).contains("leadingedgebevellength"),
				"no bevel element may be written when the length is unknown");

		Rocket reloaded = saveAndLoad(rocket).getRocket();
		assertTrue(Double.isNaN(finsOf(reloaded).getLeadingEdgeBevelLength()),
				"unknown must reload as unknown, not as zero");
	}

	@Test
	public void savedFileCarriesTheBevelElement() {
		String xml = saveToString(bevelledRocket());
		assertTrue(xml.contains("<leadingedgebevellength>"),
				"a known bevel must appear in the saved file");
	}

	@Test
	public void supersonicDragIsUnchangedByTheRoundTrip() {
		// The point of persisting the field: the aerodynamics must not move. This is the
		// assertion a user would actually notice failing -- same rocket, saved and reopened,
		// different drag.
		double before = pressureCD(finsOf(bevelledRocket()), 2.5);
		double after = pressureCD(finsOf(saveAndLoad(bevelledRocket()).getRocket()), 2.5);
		assertEquals(before, after, 1e-12,
				"fin pressure drag must be identical after a save/reload cycle");

		// Guard against the assertion above passing trivially because the bevel does nothing.
		double sharp = pressureCD(finsOf(sharpRocket()), 2.5);
		assertTrue(before > sharp, "test premise: the bevel must actually change drag");
	}

	@Test
	public void bevelSurvivesConversionToFreeform() {
		// FreeformFinSet.convertFinSet goes through copyFrom, which is also the undo/redo
		// path -- and the RASAero importer converts any fin mounted on a transition, so a
		// drop here would lose the bevel during import itself.
		Rocket rocket = bevelledRocket();
		FreeformFinSet freeform = FreeformFinSet.convertFinSet(finsOf(rocket));
		assertEquals(BEVEL, freeform.getLeadingEdgeBevelLength(), 1e-12,
				"converting to a freeform fin set must keep the leading-edge bevel");
	}

	@Test
	public void bevelIsExportedBackToRASAero() throws Exception {
		FinDTO dto = new FinDTO(finsOf(bevelledRocket()), new WarningSet(), new ErrorSet());
		// Tolerance reflects RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH being the
		// rounded 39.37 rather than 1/0.0254; every length the exporter writes shares that
		// ~2e-6 relative error, so pinning it tighter here would be testing the constant.
		assertEquals(0.125, dto.getFX1(), 1e-5,
				"FX1 must be written back in inches so a RASAero round trip preserves the bevel");

		FinDTO plain = new FinDTO(finsOf(sharpRocket()), new WarningSet(), new ErrorSet());
		assertEquals(0.0, plain.getFX1(), 1e-12,
				"an unspecified bevel must still export as RASAero's 0.0");
	}

	private static double pressureCD(TrapezoidFinSet fins, double mach) {
		Rocket rocket = (Rocket) fins.getRoot();
		FlightConditions cond = new FlightConditions(rocket.getSelectedConfiguration());
		cond.setMach(mach);
		return new FinSetCalc(fins).calculatePressureCD(cond,
				BarrowmanDragCalculator.calculateStagnationCD(mach),
				BarrowmanDragCalculator.calculateBaseCD(mach),
				new WarningSet());
	}

	private static String saveToString(Rocket rocket) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			new OpenRocketSaver().save(out, OpenRocketDocumentFactory.createDocumentFromRocket(rocket),
					new StorageOptions(), new WarningSet(), new ErrorSet());
		} catch (IOException e) {
			fail("Failed to save test rocket: " + e.getMessage());
		}
		return out.toString(StandardCharsets.UTF_8);
	}

	private OpenRocketDocument saveAndLoad(Rocket rocket) {
		File file = tempDir.resolve("fin-bevel.ork").toFile();
		try (OutputStream out = new FileOutputStream(file)) {
			new OpenRocketSaver().save(out, OpenRocketDocumentFactory.createDocumentFromRocket(rocket),
					new StorageOptions(), new WarningSet(), new ErrorSet());
		} catch (IOException e) {
			fail("Failed to save test rocket: " + e.getMessage());
		}

		try {
			return new GeneralRocketLoader(file).load();
		} catch (RocketLoadException e) {
			fail("Failed to load test rocket: " + e.getMessage());
		}
		return null;
	}
}
