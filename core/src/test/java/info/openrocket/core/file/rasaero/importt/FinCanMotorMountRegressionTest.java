package info.openrocket.core.file.rasaero.importt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.DatabaseMotorFinder;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.BaseTestCase;

/**
 * Regression for the RASAero fin-can import topology.
 *
 * <p>The importer shortens the parent body tube and inserts the fin-can sleeve
 * as a sibling to preserve a linear outer aerodynamic chain. The sustainer motor
 * must therefore be attached to the aft sleeve, not the shortened parent tube,
 * or the motor moves forward by {@code shoulderLength + finCanLength} and the
 * imported CG back-calculation becomes wrong.</p>
 */
public class FinCanMotorMountRegressionTest extends BaseTestCase {

    private static final double IN_TO_M = 0.0254;

    @BeforeAll
    static void preloadMotors() {
        {
            File file = SimVRealTestFiles.find("simvreal/rasp.eng");
            if (file == null) {
                return;
            }

            try (InputStream stream = new FileInputStream(file)) {
                RASPMotorLoader loader = new RASPMotorLoader();
                List<ThrustCurveMotor.Builder> builders = loader.load(stream, "rasp.eng");
                for (ThrustCurveMotor.Builder builder : builders) {
                    try {
                        RASAeroMotorsLoader.addMotorToCache(builder.build());
                    } catch (Exception ignored) {
                        // Keep loading the rest of the motor file.
                    }
                }
            } catch (Exception ignored) {
                // The benchmark tests already cover motor-cache loading failures.
            }
        }
    }

    @Test
    void qu8kUsesFinCanAsMotorMountToPreserveOriginalAftStation() throws Exception {
        OpenRocketDocument doc = importCdx1("Qu8k.CDX1");
        Rocket rocket = doc.getRocket();
        Simulation sim = doc.getSimulation(0);
        FlightConfigurationId fcid = sim.getFlightConfigurationId();

        BodyTube mainBody = null;
        BodyTube finCan = null;
        BodyTube motorMountBody = null;

        for (RocketComponent component : rocket) {
            if (!(component instanceof BodyTube bodyTube)) {
                continue;
            }

            if ("Body Tube".equals(bodyTube.getName())) {
                mainBody = bodyTube;
            } else if ("Fin Can".equals(bodyTube.getName())) {
                finCan = bodyTube;
            }

            MotorConfiguration config = bodyTube.getMotorConfig(fcid);
            if (config != null && !config.isEmpty() && config.getMotor() != null) {
                motorMountBody = bodyTube;
            }
        }

        assertNotNull(mainBody, "Qu8k main body tube was not imported");
        assertNotNull(finCan, "Qu8k fin can was not imported");
        assertNotNull(motorMountBody, "No imported motor mount found for Qu8k");
        assertSame(finCan, motorMountBody,
                "Fin-can import should anchor the motor to the aft sleeve, not the shortened parent tube");

        // Qu8k CDX1 body tube = 124 in at x=42 in. The fin-can rewrite shortens the
        // parent to 99.25 in and inserts a 24.75 in sleeve aft of it. The motor front
        // must still reference the original 166 in aft station, which only happens when
        // the imported motor mount is the fin can.
        assertEquals(99.25 * IN_TO_M, mainBody.getLength(), 1e-5,
                "Qu8k parent body tube should be shortened by the fin-can sleeve length");

        ThrustCurveMotor motor = (ThrustCurveMotor) finCan.getMotorConfig(fcid).getMotor();
        double actualMotorFrontX = finCan.getComponentLocations()[0].getX() + finCan.getMotorPosition(fcid).getX();
        double expectedMotorFrontX = 166.0 * IN_TO_M - motor.getLength();
        double shortenedParentFrontX = mainBody.getComponentLocations()[0].getX()
                + mainBody.getLength() - motor.getLength();

        assertEquals(expectedMotorFrontX, actualMotorFrontX, 1e-5,
                "Imported motor front should stay tied to the original full-length body aft station");
        assertTrue(Math.abs(actualMotorFrontX - shortenedParentFrontX) > 20.0 * IN_TO_M,
                "Regression guard failed: fin-can and shortened-parent motor positions are no longer distinguishable");
    }

    private OpenRocketDocument importCdx1(String filename) throws Exception {
        File file = SimVRealTestFiles.findCdx1(filename);
        assertNotNull(file, "CDX1 file not found: " + filename);

        OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
        DocumentLoadingContext context = new DocumentLoadingContext();
        context.setOpenRocketDocument(doc);
        context.setMotorFinder(new DatabaseMotorFinder());

        RASAeroLoader loader = new RASAeroLoader();
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            loader.loadFromStream(context, stream, filename.replace(".CDX1", ""));
        }
        return doc;
    }
}
