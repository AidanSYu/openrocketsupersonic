package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.DatabaseMotorFinder;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test for CDX1 imports from the simvreal/RasAero Sims folder.
 * Imports real rockets with known flight data and checks that geometry,
 * mass, CG, motors, and launch conditions are correctly parsed.
 *
 * Reference data extracted from CDX1 file comments (real flights):
 *
 * EZI65-1:  J450ST(AMW),  10.06 lb, CG=59",  alt=2750ft, actual=3965ft, RASAero=4214ft
 * Torrent:  M1850GG(AMW), 29.13 lb, CG=66.5", alt=2400ft, actual=12807ft, RASAero=13717ft
 * Qu8k:     Q18000(DEAP-EX), 314.5lb, CG=110", alt=3750ft, actual=121478ft, RASAero=119684ft
 * FMJ1:     O10000(Hist), 70 lb, CG=86", alt=3933ft, actual=37981ft, RASAero=38772ft
 */
public class CDX1ImportDiagnosticTest extends BaseTestCase {

    private static final double INCH_TO_METER = 1.0 / 39.37;
    private static final double FOOT_TO_METER = 1.0 / 3.28084;
    private static final double LB_TO_KG = 1.0 / 2.20462262;

    /**
     * Holds expected values extracted directly from a CDX1 file
     */
    private static class CDX1Expected {
        String name;
        // Nose
        double noseLengthIn;
        double noseDiameterIn;
        String noseShape;
        // Body
        double bodyLengthIn;
        double bodyDiameterIn;
        // Fins (on body tube or fin can)
        int finCount;
        double finChordIn;
        double finSpanIn;
        double finThicknessIn;
        // Simulation
        String motorDesignation;
        double launchWtLb;
        double cgIn;
        // Launch site
        double altitudeFt;
        double tempF;
        double rodLengthFt;
        // Reference results
        double rasAeroAltFt;
        double rasAeroMaxVelFtS;
        double actualAltFt;
    }

    private OpenRocketDocument importCDX1(String filePath) throws IOException, RocketLoadException {
        RASAeroLoader loader = new RASAeroLoader();
        File file = new File(filePath);
        assertTrue(file.exists(), "CDX1 file not found: " + filePath);

        OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
        DocumentLoadingContext context = new DocumentLoadingContext();
        context.setOpenRocketDocument(doc);
        context.setMotorFinder(new DatabaseMotorFinder());

        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            loader.loadFromStream(context, stream, file.getName().replace(".CDX1", ""));
        }

        // Print warnings
        WarningSet warnings = loader.getWarnings();
        if (!warnings.isEmpty()) {
            System.out.println("=== WARNINGS for " + file.getName() + " ===");
            for (Warning w : warnings) {
                System.out.println("  WARNING: " + w.toString());
            }
        }

        return doc;
    }

    private void diagnoseRocket(OpenRocketDocument doc, CDX1Expected expected) {
        Rocket rocket = doc.getRocket();
        assertNotNull(rocket, "Rocket is null after import");

        System.out.println("\n=== ROCKET: " + expected.name + " ===");
        System.out.println("Stages: " + rocket.getChildCount());

        // Walk component tree
        AxialStage sustainer = rocket.getStage(0);
        assertNotNull(sustainer, "No sustainer stage");
        System.out.println("Sustainer children: " + sustainer.getChildCount());

        for (int i = 0; i < sustainer.getChildCount(); i++) {
            RocketComponent comp = sustainer.getChild(i);
            System.out.println("  [" + i + "] " + comp.getClass().getSimpleName() + " \"" + comp.getName() + "\"");
            printComponentDetails(comp, "    ");
            for (int j = 0; j < comp.getChildCount(); j++) {
                RocketComponent child = comp.getChild(j);
                System.out.println("    [" + j + "] " + child.getClass().getSimpleName() + " \"" + child.getName() + "\"");
                printComponentDetails(child, "      ");
                for (int k = 0; k < child.getChildCount(); k++) {
                    RocketComponent grandchild = child.getChild(k);
                    System.out.println("      [" + k + "] " + grandchild.getClass().getSimpleName() + " \"" + grandchild.getName() + "\"");
                    printComponentDetails(grandchild, "        ");
                }
            }
        }

        // Check nose cone
        RocketComponent firstComp = sustainer.getChild(0);
        assertTrue(firstComp instanceof NoseCone, "First component should be NoseCone, got " + firstComp.getClass().getSimpleName());
        NoseCone nose = (NoseCone) firstComp;

        double expectedNoseLengthM = expected.noseLengthIn * INCH_TO_METER;
        double expectedNoseRadiusM = expected.noseDiameterIn / 2.0 * INCH_TO_METER;
        System.out.println("\nNose cone verification:");
        System.out.println("  Length: expected=" + String.format("%.4f", expectedNoseLengthM) + "m, got=" + String.format("%.4f", nose.getLength()) + "m");
        System.out.println("  Radius: expected=" + String.format("%.4f", expectedNoseRadiusM) + "m, got=" + String.format("%.4f", nose.getBaseRadius()) + "m");
        assertEquals(expectedNoseLengthM, nose.getLength(), 0.001, "Nose length mismatch");
        assertEquals(expectedNoseRadiusM, nose.getBaseRadius(), 0.001, "Nose radius mismatch");

        // Check body tube
        RocketComponent secondComp = sustainer.getChild(1);
        assertTrue(secondComp instanceof BodyTube, "Second component should be BodyTube, got " + secondComp.getClass().getSimpleName());
        BodyTube body = (BodyTube) secondComp;

        double expectedBodyLengthM = expected.bodyLengthIn * INCH_TO_METER;
        System.out.println("\nBody tube verification:");
        System.out.println("  Length: expected=" + String.format("%.4f", expectedBodyLengthM) + "m, got=" + String.format("%.4f", body.getLength()) + "m");
        assertEquals(expectedBodyLengthM, body.getLength(), 0.001, "Body length mismatch");

        // Check mass override
        System.out.println("\nMass override verification:");
        System.out.println("  Stage mass overridden: " + sustainer.isMassOverridden());
        if (sustainer.isMassOverridden()) {
            double expectedMassKg = expected.launchWtLb * LB_TO_KG;
            double overrideMassKg = sustainer.getOverrideMass();
            System.out.println("  CDX1 launch wt: " + expected.launchWtLb + " lb = " + String.format("%.3f", expectedMassKg) + " kg");
            System.out.println("  Stage override mass (without motor): " + String.format("%.3f", overrideMassKg) + " kg");
            // The override should be launch weight minus motor weight
        }

        // Check CG override
        System.out.println("\nCG override verification:");
        System.out.println("  Stage CG overridden: " + sustainer.isCGOverridden());
        if (sustainer.isCGOverridden()) {
            double cgM = sustainer.getOverrideCGX();
            double expectedCgM = expected.cgIn * INCH_TO_METER;
            System.out.println("  CDX1 CG: " + expected.cgIn + " in = " + String.format("%.4f", expectedCgM) + " m from nose");
            System.out.println("  Stage override CG: " + String.format("%.4f", cgM) + " m");
        }

        // Check simulations and motors
        System.out.println("\nSimulation verification:");
        System.out.println("  Number of simulations: " + doc.getSimulationCount());
        if (doc.getSimulationCount() > 0) {
            Simulation sim = doc.getSimulation(0);
            SimulationOptions opts = sim.getOptions();
            System.out.println("  Launch altitude: " + String.format("%.1f", opts.getLaunchAltitude()) + " m (expected: " + String.format("%.1f", expected.altitudeFt * FOOT_TO_METER) + " m)");
            System.out.println("  Launch rod length: " + String.format("%.4f", opts.getLaunchRodLength()) + " m (expected: " + String.format("%.4f", expected.rodLengthFt * FOOT_TO_METER) + " m)");
            System.out.println("  Launch temperature: " + String.format("%.1f", opts.getLaunchTemperature()) + " K (expected: " + String.format("%.1f", (expected.tempF + 459.67) * 5.0 / 9.0) + " K)");

            // Check motor
            FlightConfigurationId fcid = sim.getFlightConfigurationId();
            boolean motorFound = false;
            for (RocketComponent comp : rocket) {
                if (comp instanceof MotorMount) {
                    MotorMount mount = (MotorMount) comp;
                    MotorConfiguration config = mount.getMotorConfig(fcid);
                    if (config != null && config.getMotor() != null) {
                        System.out.println("  Motor found: " + config.getMotor().getDesignation());
                        System.out.println("  Motor launch mass: " + String.format("%.3f", config.getMotor().getLaunchMass()) + " kg");
                        motorFound = true;
                    }
                }
            }
            if (!motorFound) {
                System.out.println("  *** NO MOTOR FOUND *** (expected: " + expected.motorDesignation + ")");
                System.out.println("  This is likely why the simulation fails!");
            }
        } else {
            System.out.println("  *** NO SIMULATIONS CREATED ***");
        }

        // Print reference data for comparison
        System.out.println("\nReference flight data:");
        System.out.println("  Actual flight altitude: " + expected.actualAltFt + " ft");
        System.out.println("  RASAero prediction: " + expected.rasAeroAltFt + " ft");
        System.out.println("  RASAero max velocity: " + expected.rasAeroMaxVelFtS + " ft/s");
    }

    private void printComponentDetails(RocketComponent comp, String indent) {
        if (comp instanceof NoseCone) {
            NoseCone nc = (NoseCone) comp;
            System.out.println(indent + "shape=" + nc.getShapeType() + " length=" + String.format("%.4f", nc.getLength()) +
                    "m radius=" + String.format("%.4f", nc.getBaseRadius()) + "m");
        } else if (comp instanceof BodyTube) {
            BodyTube bt = (BodyTube) comp;
            System.out.println(indent + "length=" + String.format("%.4f", bt.getLength()) +
                    "m radius=" + String.format("%.4f", bt.getOuterRadius()) + "m");
        } else if (comp instanceof TrapezoidFinSet) {
            TrapezoidFinSet fs = (TrapezoidFinSet) comp;
            System.out.println(indent + "count=" + fs.getFinCount() + " rootChord=" + String.format("%.4f", fs.getRootChord()) +
                    "m span=" + String.format("%.4f", fs.getHeight()) + "m thickness=" + String.format("%.5f", fs.getThickness()) + "m");
        } else if (comp instanceof Transition) {
            Transition t = (Transition) comp;
            System.out.println(indent + "length=" + String.format("%.4f", t.getLength()) +
                    "m foreR=" + String.format("%.4f", t.getForeRadius()) + "m aftR=" + String.format("%.4f", t.getAftRadius()) + "m");
        } else if (comp instanceof PodSet) {
            PodSet ps = (PodSet) comp;
            System.out.println(indent + "instances=" + ps.getInstanceCount() + " name=" + ps.getName());
        }
    }

    @Test
    public void testEZI65Import() throws Exception {
        CDX1Expected exp = new CDX1Expected();
        exp.name = "EZI65-1";
        exp.noseLengthIn = 13; exp.noseDiameterIn = 4; exp.noseShape = "Tangent Ogive";
        exp.bodyLengthIn = 72; exp.bodyDiameterIn = 4;
        exp.finCount = 3; exp.finChordIn = 7; exp.finSpanIn = 4.75; exp.finThicknessIn = 0.25;
        exp.motorDesignation = "J450ST";
        exp.launchWtLb = 10.06; exp.cgIn = 59;
        exp.altitudeFt = 2750; exp.tempF = 70; exp.rodLengthFt = 10;
        exp.rasAeroAltFt = 4214.14; exp.rasAeroMaxVelFtS = 675.96;
        exp.actualAltFt = 3965;

        String path = findSimvrealPath("EZI65-1.CDX1");
        OpenRocketDocument doc = importCDX1(path);
        diagnoseRocket(doc, exp);
    }

    @Test
    public void testTorrentImport() throws Exception {
        CDX1Expected exp = new CDX1Expected();
        exp.name = "Torrent";
        exp.noseLengthIn = 20; exp.noseDiameterIn = 4; exp.noseShape = "Conical";
        // Body tube is shortened by finCan(7") + shoulder(0.05") = 7.05": 89.5 - 7.05 = 82.45"
        exp.bodyLengthIn = 82.45; exp.bodyDiameterIn = 4;
        exp.finCount = 3; exp.finChordIn = 8.25; exp.finSpanIn = 5; exp.finThicknessIn = 0.125;
        exp.motorDesignation = "M1850GG";
        exp.launchWtLb = 29.13; exp.cgIn = 66.5;
        exp.altitudeFt = 2400; exp.tempF = 80; exp.rodLengthFt = 10;
        exp.rasAeroAltFt = 13716.88; exp.rasAeroMaxVelFtS = 1404.34;
        exp.actualAltFt = 12807;

        String path = findSimvrealPath("Torrent.CDX1");
        OpenRocketDocument doc = importCDX1(path);
        diagnoseRocket(doc, exp);
    }

    @Test
    public void testQu8kImport() throws Exception {
        CDX1Expected exp = new CDX1Expected();
        exp.name = "Qu8k";
        exp.noseLengthIn = 42; exp.noseDiameterIn = 8; exp.noseShape = "Conical";
        // Body tube is shortened by finCan(24") + shoulder(0.75") = 24.75": 124 - 24.75 = 99.25"
        exp.bodyLengthIn = 99.25; exp.bodyDiameterIn = 8;
        exp.finCount = 4; exp.finChordIn = 22; exp.finSpanIn = 7.5; exp.finThicknessIn = 0.25;
        exp.motorDesignation = "Q18000";
        exp.launchWtLb = 314.5; exp.cgIn = 110;
        exp.altitudeFt = 3750; exp.tempF = 80; exp.rodLengthFt = 16;
        exp.rasAeroAltFt = 119683.9; exp.rasAeroMaxVelFtS = 3752.96;
        exp.actualAltFt = 121478;

        String path = findSimvrealPath("Qu8k.CDX1");
        OpenRocketDocument doc = importCDX1(path);
        diagnoseRocket(doc, exp);
    }

    @Test
    public void testFullMetalJacket1Import() throws Exception {
        CDX1Expected exp = new CDX1Expected();
        exp.name = "Full Metal Jacket 1";
        exp.noseLengthIn = 16; exp.noseDiameterIn = 4; exp.noseShape = "Tangent Ogive";
        // Body tube is shortened by finCan(14.5") + shoulder(0.2415") = 14.7415": 116.3125 - 14.7415 = 101.571"
        exp.bodyLengthIn = 101.571; exp.bodyDiameterIn = 4;
        exp.finCount = 3; exp.finChordIn = 12.5; exp.finSpanIn = 4.4; exp.finThicknessIn = 0.125;
        exp.motorDesignation = "O10000";
        exp.launchWtLb = 70; exp.cgIn = 86;
        exp.altitudeFt = 3933; exp.tempF = 80; exp.rodLengthFt = 12;
        exp.rasAeroAltFt = 38771.61; exp.rasAeroMaxVelFtS = 2566.52;
        exp.actualAltFt = 37981;

        String path = findSimvrealPath("Full Metal Jacket1.CDX1");
        OpenRocketDocument doc = importCDX1(path);
        diagnoseRocket(doc, exp);
    }

    @Test
    public void testIonDriveImport() throws Exception {
        CDX1Expected exp = new CDX1Expected();
        exp.name = "Ion Drive";
        exp.noseLengthIn = 19; exp.noseDiameterIn = 4; exp.noseShape = "Tangent Ogive";
        exp.bodyLengthIn = 49.5; exp.bodyDiameterIn = 4;
        exp.finCount = 3; exp.finChordIn = 9.5; exp.finSpanIn = 4.375; exp.finThicknessIn = 0.093;
        exp.motorDesignation = "K550W";
        exp.launchWtLb = 11.37; exp.cgIn = 39.5;
        exp.altitudeFt = 2750; exp.tempF = 100; exp.rodLengthFt = 10;
        exp.rasAeroAltFt = 8642.34; exp.rasAeroMaxVelFtS = 926.79;
        exp.actualAltFt = 8027;

        String path = findSimvrealPath("IonDrive.CDX1");
        OpenRocketDocument doc = importCDX1(path);
        diagnoseRocket(doc, exp);
    }

    @Test
    public void testProteus6Import() throws Exception {
        CDX1Expected exp = new CDX1Expected();
        exp.name = "Proteus6";
        exp.noseLengthIn = 28.5; exp.noseDiameterIn = 6; exp.noseShape = "Conical";
        exp.bodyLengthIn = 141.5; exp.bodyDiameterIn = 6;
        exp.finCount = 3; exp.finChordIn = 10.1; exp.finSpanIn = 9; exp.finThicknessIn = 0.1875;
        exp.motorDesignation = "P9381";
        exp.launchWtLb = 186.7; exp.cgIn = 111;
        exp.altitudeFt = 3933; exp.tempF = 80; exp.rodLengthFt = 1.5;
        exp.rasAeroAltFt = 81499; exp.rasAeroMaxVelFtS = 3063;
        exp.actualAltFt = 85067;

        String path = findSimvrealPath("Proteus6.CDX1");
        OpenRocketDocument doc = importCDX1(path);
        diagnoseRocket(doc, exp);
    }

    @Test
    public void testMesosDragBreakdown() throws Exception {
        // Load Kip custom motors
        SimVRealValidationTest.loadMotorsIntoRASAeroCache("simvreal/Docs/Mesos/M787_Expanded_Nozzle_Sea_Level.eng");
        SimVRealValidationTest.loadMotorsIntoRASAeroCache("simvreal/Docs/Mesos/O4374_Sea_Level.eng");
        SimVRealValidationTest.loadMotorsIntoRASAeroCache("c:/Code/OpenRocket Plus/simvreal/Docs/Mesos/M787_Expanded_Nozzle_Sea_Level.eng");
        SimVRealValidationTest.loadMotorsIntoRASAeroCache("c:/Code/OpenRocket Plus/simvreal/Docs/Mesos/O4374_Sea_Level.eng");

        String path = "c:/Code/OpenRocket Plus/simvreal/Docs/Mesos/MESOS 293K Flight.CDX1";
        OpenRocketDocument doc = importCDX1(path);
        Rocket rocket = doc.getRocket();

        // Print component tree
        System.out.println("\n=== MESOS Component Tree ===");
        for (int s = 0; s < rocket.getChildCount(); s++) {
            AxialStage stage = (AxialStage) rocket.getChild(s);
            System.out.println("Stage[" + s + "] " + stage.getName());
            printTree(stage, "  ");
        }

        // Aero sweep at key Mach numbers
        double[] machs = {0.05, 0.1, 0.3, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0};
        info.openrocket.core.aerodynamics.BarrowmanCalculator calc =
                new info.openrocket.core.aerodynamics.BarrowmanCalculator();

        System.out.println("\n=== MESOS CD Sweep ===");
        System.out.printf("%-6s  %-8s  %-8s  %-8s%n", "Mach", "CD_total", "CN", "Cm");
        System.out.println("-------  --------  --------  --------");
        for (double mach : machs) {
            info.openrocket.core.rocketcomponent.FlightConfiguration config =
                    rocket.getSelectedConfiguration();
            info.openrocket.core.aerodynamics.FlightConditions conditions =
                    new info.openrocket.core.aerodynamics.FlightConditions(config);
            conditions.setMach(mach);
            conditions.setAOA(Math.toRadians(0.0));
            info.openrocket.core.logging.WarningSet warnings = new info.openrocket.core.logging.WarningSet();

            info.openrocket.core.aerodynamics.AerodynamicForces total =
                    calc.getAerodynamicForces(config, conditions, warnings);
            System.out.printf("%-6.2f  %-8.4f  %-8.4f  %-8.4f%n",
                    mach, total.getCD(), total.getCN(), total.getCm());
        }

        // Per-component breakdown at M=0.05 and M=2.0
        for (double mach : new double[]{0.05, 0.5, 2.0}) {
            info.openrocket.core.rocketcomponent.FlightConfiguration config =
                    rocket.getSelectedConfiguration();
            info.openrocket.core.aerodynamics.FlightConditions conditions =
                    new info.openrocket.core.aerodynamics.FlightConditions(config);
            conditions.setMach(mach);
            conditions.setAOA(Math.toRadians(2.0));
            info.openrocket.core.logging.WarningSet warnings = new info.openrocket.core.logging.WarningSet();

            java.util.Map<info.openrocket.core.rocketcomponent.RocketComponent,
                    info.openrocket.core.aerodynamics.AerodynamicForces> forceMap =
                    calc.getForceAnalysis(config, conditions, warnings);

            System.out.println("\n--- Per-component at M=" + mach + " ---");
            System.out.printf("%-35s  %-8s  %-8s  %-8s  %-8s%n",
                    "Component", "CD", "CDf", "CDp", "CNa");
            for (var entry : forceMap.entrySet()) {
                info.openrocket.core.aerodynamics.AerodynamicForces f = entry.getValue();
                if (f != null && (f.getCD() > 0.001 || Math.abs(f.getCN()) > 0.001)) {
                    System.out.printf("%-35s  %-8.4f  %-8.4f  %-8.4f  %-8.4f%n",
                            entry.getKey().getName(),
                            f.getCD(), f.getFrictionCD(), f.getPressureCD(), f.getCN());
                }
            }
        }
    }

    private void printTree(info.openrocket.core.rocketcomponent.RocketComponent comp, String indent) {
        for (int i = 0; i < comp.getChildCount(); i++) {
            info.openrocket.core.rocketcomponent.RocketComponent child = comp.getChild(i);
            String detail = "";
            if (child instanceof BodyTube bt) {
                detail = String.format(" L=%.3fm R=%.3fm", bt.getLength(), bt.getOuterRadius());
            } else if (child instanceof info.openrocket.core.rocketcomponent.Transition t) {
                detail = String.format(" L=%.3fm foreR=%.3fm aftR=%.3fm", t.getLength(), t.getForeRadius(), t.getAftRadius());
            } else if (child instanceof info.openrocket.core.rocketcomponent.NoseCone nc) {
                detail = String.format(" L=%.3fm R=%.3fm", nc.getLength(), nc.getBaseRadius());
            } else if (child instanceof info.openrocket.core.rocketcomponent.TrapezoidFinSet fs) {
                detail = String.format(" n=%d chord=%.3fm span=%.3fm", fs.getFinCount(), fs.getRootChord(), fs.getHeight());
            }
            System.out.println(indent + child.getClass().getSimpleName() + " \"" + child.getName() + "\"" + detail);
            printTree(child, indent + "  ");
        }
    }

    /**
     * Find CDX1 files in the simvreal directory.
     * Searches relative to the project root.
     */
    private String findSimvrealPath(String filename) {
        // Try relative to working directory (project root)
        String[] candidates = {
            "simvreal/RasAero Sims/" + filename,
            "../simvreal/RasAero Sims/" + filename,
            "../../simvreal/RasAero Sims/" + filename,
        };
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        }
        // Try absolute path
        File absolute = new File("c:/Code/OpenRocket Plus/simvreal/RasAero Sims/" + filename);
        if (absolute.exists()) {
            return absolute.getAbsolutePath();
        }
        fail("Could not find CDX1 file: " + filename);
        return null;
    }
}
