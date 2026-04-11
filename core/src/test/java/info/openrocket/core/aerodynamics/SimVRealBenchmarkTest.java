package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.masscalc.RigidBody;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.PodSet;
import info.openrocket.core.rocketcomponent.RailButton;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.startup.OpenRocketCore;

/**
 * SimVReal Benchmark: Loads CDX1 files from SimVReal/RasAero Sims,
 * runs ORP simulations, and compares apogee predictions against
 * real flight data and RASAero II predictions.
 *
 * The three metrics:
 *   1. ORP predicted apogee (our model)
 *   2. RASAero II predicted apogee (the target)
 *   3. Real flight apogee (ground truth)
 *
 * Goal: close the gap between ORP and RASAero, using real flight data
 * as tiebreaker when they disagree.
 */
public class SimVRealBenchmarkTest {

    /** Path to SimVReal CDX1 files, relative to project root */
    private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";

    /** Path to the RASP motor file that ships with the SimVReal corpus */
    private static final String[] RASP_ENG_PATHS = {
            "simvreal/rasp.eng",
            "c:/Code/OpenRocket Plus/simvreal/rasp.eng",
    };

    @BeforeAll
    static void setup() {
        OpenRocketCore.initialize();
        // Preload the RASAero motor database that SimVReal CDX1 files reference by name.
        // Without this, ~5 rockets (Proteus6, Kinsel, etc.) fail with hasMotors=false
        // because the default OpenRocket motor DB doesn't contain these curves.
        for (String path : RASP_ENG_PATHS) {
            loadMotorsIntoRASAeroCache(path);
        }
    }

    private static void loadMotorsIntoRASAeroCache(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        try (InputStream stream = new FileInputStream(file)) {
            RASPMotorLoader loader = new RASPMotorLoader();
            List<ThrustCurveMotor.Builder> builders = loader.load(stream, "rasp.eng");
            int loaded = 0, skipped = 0;
            for (ThrustCurveMotor.Builder builder : builders) {
                try {
                    ThrustCurveMotor motor = builder.build();
                    RASAeroMotorsLoader.addMotorToCache(motor);
                    loaded++;
                } catch (Exception e) {
                    skipped++;
                }
            }
            System.out.println("Preloaded " + loaded + " motors from " + path
                    + " (skipped " + skipped + ")");
        } catch (Exception e) {
            System.out.println("Could not load " + path + ": " + e.getMessage());
        }
    }

    /**
     * A single validation case: CDX1 file, real altitude, RASAero prediction.
     */
    static class ValidationCase {
        final String cdx1File;
        final String rocketName;
        final double realAltitudeFt;
        final double rasAeroAltitudeFt;
        final String dataSource; // GPS, Baro, Optical, Accel
        final boolean isMultiStage;

        ValidationCase(String cdx1File, String rocketName, double realAltitudeFt,
                       double rasAeroAltitudeFt, String dataSource, boolean isMultiStage) {
            this.cdx1File = cdx1File;
            this.rocketName = rocketName;
            this.realAltitudeFt = realAltitudeFt;
            this.rasAeroAltitudeFt = rasAeroAltitudeFt;
            this.dataSource = dataSource;
            this.isMultiStage = isMultiStage;
        }

        double rasAeroErrorPct() {
            return 100.0 * (rasAeroAltitudeFt - realAltitudeFt) / realAltitudeFt;
        }
    }

    /**
     * Build the validation dataset from Rogers' published flight comparison data
     * and the CDX1 file comments.
     */
    static List<ValidationCase> getValidationCases() {
        List<ValidationCase> cases = new ArrayList<>();

        // === Single-stage rockets (simpler, fewer motor matching issues) ===

        cases.add(new ValidationCase("Byrum.CDX1", "Byrum",
                5732, 5281, "Baro", false));

        cases.add(new ValidationCase("CancerDescending.CDX1", "Cancer Descending",
                6188, 6328, "Baro", false));

        cases.add(new ValidationCase("EZI65-1.CDX1", "EZI-65 J450ST",
                3965, 4214, "Baro", false));

        cases.add(new ValidationCase("Gibb.CDX1", "Gibb",
                3913, 4205, "Baro", false));

        cases.add(new ValidationCase("IonDrive.CDX1", "Ion Drive",
                8027, 8642, "Baro", false));

        cases.add(new ValidationCase("Raven.CDX1", "Raven",
                8815, 9332, "Baro", false));

        cases.add(new ValidationCase("Thunder&Lightning.CDX1", "Thunder & Lightning",
                3577, 3989, "Baro", false));

        cases.add(new ValidationCase("Blister.CDX1", "Blister",
                9026, 8301, "Baro", false));

        cases.add(new ValidationCase("Rabia.CDX1", "Rabia",
                12745, 12197, "Baro", false));

        cases.add(new ValidationCase("Rabia-ShortFinCan.CDX1", "Rabia Short Fin Can",
                10584, 10225, "Baro", false));

        cases.add(new ValidationCase("Torrent.CDX1", "Torrent",
                12807, 13717, "Baro", false));

        cases.add(new ValidationCase("CalIsp1.CDX1", "Caliber Isp 04 Team 3",
                3964, 3876, "Baro", false));

        cases.add(new ValidationCase("CalIsp2.CDX1", "Caliber Isp 04 Team 1",
                3837, 3948, "Baro", false));

        cases.add(new ValidationCase("CalIsp3.CDX1", "Caliber Isp 04 Team 2",
                3710, 3876, "Baro", false));

        cases.add(new ValidationCase("CalIsp4.CDX1", "Caliber Isp 05 Columbia",
                5085, 4847, "Baro", false));

        cases.add(new ValidationCase("CalIsp5.CDX1", "Caliber Isp 05 Discovery",
                4930, 4836, "Baro", false));

        // === Higher altitude / supersonic single-stage ===

        cases.add(new ValidationCase("L500Roc.CDX1", "Kline-Rogers L500",
                24771, 26509, "Optical", false));

        cases.add(new ValidationCase("DontDebateThisN5800MinDia.CDX1", "Don't Debate This",
                56573, 61982, "Baro", false));

        // === Multi-stage rockets ===

        cases.add(new ValidationCase("Qu8k.CDX1", "Qu8k",
                121478, 119684, "Accel", false));  // single Q18000

        cases.add(new ValidationCase("Proteus6.CDX1", "Proteus 6",
                85067, 81499, "Accel", false));  // single P9381

        cases.add(new ValidationCase("Full Metal Jacket1.CDX1", "Full Metal Jacket BALLS 005",
                37981, 38772, "Optical", false));  // single O10000

        cases.add(new ValidationCase("Full Metal Jacket2.CDX1", "Full Metal Jacket Black Rock 6",
                30038, 32548, "Optical", false));  // single O10000

        cases.add(new ValidationCase("Kinsel_P4935_A-601_Rocket.CDX1", "A-601 Kinsel",
                42771, 41098, "GPS", false));  // single P4935

        cases.add(new ValidationCase("AeroPac104KStageOne&Two-2.CDX1", "AeroPac 104K",
                104659, 113786, "GPS", true));  // 2-stage

        return cases;
    }

    /**
     * Find the SimVReal directory by walking up from the test class location.
     */
    private static File findSimVRealDir() {
        // Try relative to working directory (project root)
        File dir = new File(SIMVREAL_DIR);
        if (dir.exists()) return dir;

        // Try walking up from current directory
        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            File candidate = current.resolve(SIMVREAL_DIR).toFile();
            if (candidate.exists()) return candidate;
            current = current.getParent();
            if (current == null) break;
        }

        return null;
    }

    /**
     * Main benchmark test: loads each CDX1, simulates, and prints the 3-column comparison.
     */
    @Test
    void testSimVRealBenchmark() throws InterruptedException {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) {
            System.out.println("SKIP: SimVReal directory not found. Set working directory to project root.");
            return;
        }

        List<ValidationCase> cases = getValidationCases();
        List<Double> orpErrors = new ArrayList<>();
        List<Double> rasErrors = new ArrayList<>();
        int failed = 0;
        int skipped = 0;

        System.out.println();
        System.out.println("=".repeat(120));
        System.out.println("SimVReal Benchmark: ORP vs RASAero II vs Real Flight Data");
        System.out.println("=".repeat(120));
        System.out.printf("%-30s %8s %8s %8s %8s %8s %8s  %s%n",
                "Rocket", "Real", "RASAero", "ORP", "RAS Err", "ORP Err", "Delta", "Status");
        System.out.println("-".repeat(120));

        for (ValidationCase vc : cases) {
            File cdx1 = new File(simvrealDir, vc.cdx1File);
            if (!cdx1.exists()) {
                System.out.printf("%-30s  SKIP: CDX1 file not found%n", vc.rocketName);
                skipped++;
                continue;
            }

            try {
                GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
                OpenRocketDocument doc = loader.load();

                List<Simulation> sims = doc.getSimulations();
                if (sims.isEmpty()) {
                    System.out.printf("%-30s  SKIP: No simulations in CDX1%n", vc.rocketName);
                    skipped++;
                    continue;
                }

                // Print launch conditions for first few rockets (diagnostic)
                Simulation sim = sims.get(0);
                if (cases.indexOf(vc) < 3) {
                    var opts = sim.getOptions();
                    System.out.printf("  [%s] alt=%.0fm, temp=%.1fK, rod=%.1f°, rodLen=%.2fm, wind=%.1fm/s%n",
                            vc.rocketName,
                            opts.getLaunchAltitude(),
                            opts.getLaunchTemperature(),
                            Math.toDegrees(opts.getLaunchRodAngle()),
                            opts.getLaunchRodLength(),
                            opts.getAverageWindModel().getAverage());
                }

                // Run the first simulation with a timeout
                sim.getOptions().setTimeStep(0.05);  // reasonable timestep
                sim.getOptions().setMaximumStepAngle(Math.toRadians(3));

                // Run in a thread with timeout to prevent hanging
                Thread simThread = new Thread(() -> {
                    try {
                        sim.simulate();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                simThread.start();
                simThread.join(120_000);  // 2 minute timeout per sim
                if (simThread.isAlive()) {
                    simThread.interrupt();
                    System.out.printf("%-30s  TIMEOUT: Simulation took > 120s%n", vc.rocketName);
                    failed++;
                    continue;
                }

                FlightData data = sim.getSimulatedData();
                if (data == null) {
                    System.out.printf("%-30s  FAIL: No flight data produced%n", vc.rocketName);
                    failed++;
                    continue;
                }

                // Debug: check for warnings, errors, and motor presence
                if (data.getBranchCount() == 0) {
                    System.out.printf("%-30s  SKIP: No flight data branches%n", vc.rocketName);
                    skipped++;
                    continue;
                }

                // Check if rocket has motors
                var rocket = doc.getRocket();
                var config = rocket.getSelectedConfiguration();
                boolean hasMotors = config.hasMotors();

                // ORP apogee in feet (FlightData returns meters)
                double orpApogeeFt = data.getMaxAltitude() * 3.28084;
                double maxMach = data.getMaxMachNumber();

                // If zero altitude, print debug info
                if (orpApogeeFt < 1) {
                    System.out.printf("%-30s  DEBUG: maxAlt=%.1fm, branches=%d, hasMotors=%b, flightTime=%.1fs, simWarnings=%s%n",
                            vc.rocketName, data.getMaxAltitude(), data.getBranchCount(),
                            hasMotors, data.getFlightTime(),
                            sim.getSimulatedWarnings() != null ? sim.getSimulatedWarnings().toString() : "none");
                    failed++;
                    continue;
                }

                double rasError = vc.rasAeroErrorPct();
                double orpError = 100.0 * (orpApogeeFt - vc.realAltitudeFt) / vc.realAltitudeFt;
                double deltaVsRas = 100.0 * (orpApogeeFt - vc.rasAeroAltitudeFt) / vc.rasAeroAltitudeFt;

                orpErrors.add(Math.abs(orpError));
                rasErrors.add(Math.abs(rasError));

                String status;
                if (Math.abs(orpError) <= 10.0) {
                    status = "OK";
                } else if (Math.abs(orpError) <= 20.0) {
                    status = "WARN";
                } else {
                    status = "POOR";
                    failed++;
                }

                System.out.printf("%-30s %8.0f %8.0f %8.0f %+7.1f%% %+7.1f%% %+7.1f%%  %-4s M%.2f%n",
                        vc.rocketName, vc.realAltitudeFt, vc.rasAeroAltitudeFt,
                        orpApogeeFt, rasError, orpError, deltaVsRas, status, maxMach);

            } catch (Exception e) {
                System.out.printf("%-30s  ERROR: %s%n", vc.rocketName, e.getMessage());
                failed++;
            }
        }

        // Summary statistics
        System.out.println("-".repeat(120));
        System.out.println();

        double avgOrpError = orpErrors.stream().mapToDouble(d -> d).average().orElse(0);
        double avgRasError = rasErrors.stream().mapToDouble(d -> d).average().orElse(0);
        long orpUnder10 = orpErrors.stream().filter(e -> e < 10).count();
        long orpUnder5 = orpErrors.stream().filter(e -> e < 5).count();
        long rasUnder10 = rasErrors.stream().filter(e -> e < 10).count();
        long rasUnder5 = rasErrors.stream().filter(e -> e < 5).count();
        int total = orpErrors.size();

        System.out.println("SUMMARY:");
        System.out.printf("  Tested: %d rockets  |  Skipped: %d  |  Errors: %d%n",
                total, skipped, failed);
        System.out.println();
        System.out.printf("  %-20s  %12s  %12s%n", "", "RASAero II", "ORP");
        System.out.printf("  %-20s  %11.2f%%  %11.2f%%%n", "Avg |Error|:", avgRasError, avgOrpError);
        System.out.printf("  %-20s  %10.1f%%   %10.1f%%%n", "Within ±10%:",
                100.0 * rasUnder10 / total, 100.0 * orpUnder10 / total);
        System.out.printf("  %-20s  %10.1f%%   %10.1f%%%n", "Within ±5%:",
                100.0 * rasUnder5 / total, 100.0 * orpUnder5 / total);
        System.out.println();
        System.out.println("TARGET: Match RASAero II accuracy (avg ~3.5%, 80% within ±10%)");
        System.out.println("=".repeat(120));
    }

    /**
     * Diagnostic test: imports the EZI-65 CDX1 file and prints the imported
     * rocket geometry, mass, and Cd breakdown to verify import correctness.
     *
     * Expected CDX1 geometry:
     *   - Nose cone: Tangent Ogive, 13" long, 4" diameter
     *   - Body tube: 72" long, 4" diameter
     *   - 3 fins: Rounded airfoil, 7" chord, 4.75" span, 1.25" sweep, 4.5" tip chord, 0.25" thickness
     *   - Rail guides: 0.625" diameter
     *   - Motor: J450ST (AMW), 10.06 lb launch weight
     */
    @Test
    void testEZI65Import() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) {
            System.out.println("SKIP: SimVReal directory not found.");
            return;
        }

        File cdx1File = new File(simvrealDir, "EZI65-1.CDX1");
        assertTrue(cdx1File.exists(), "EZI65-1.CDX1 not found at " + cdx1File.getAbsolutePath());

        // --- Load the CDX1 file ---
        GeneralRocketLoader loader = new GeneralRocketLoader(cdx1File);
        OpenRocketDocument doc = loader.load();
        Rocket rocket = doc.getRocket();
        FlightConfiguration config = rocket.getSelectedConfiguration();

        // Conversion: inches to meters
        final double IN_TO_M = 0.0254;
        final double LB_TO_KG = 0.453592;

        // Expected values from CDX1 (in inches/lbs)
        final double EXPECTED_NOSE_LENGTH_IN = 13.0;
        final double EXPECTED_NOSE_DIAMETER_IN = 4.0;
        final double EXPECTED_BODY_LENGTH_IN = 72.0;
        final double EXPECTED_BODY_DIAMETER_IN = 4.0;
        final int EXPECTED_FIN_COUNT = 3;
        final double EXPECTED_ROOT_CHORD_IN = 7.0;
        final double EXPECTED_TIP_CHORD_IN = 4.5;
        final double EXPECTED_SPAN_IN = 4.75;
        final double EXPECTED_SWEEP_IN = 1.25;
        final double EXPECTED_THICKNESS_IN = 0.25;
        final double EXPECTED_RAIL_GUIDE_DIAMETER_IN = 0.625;
        final double EXPECTED_LAUNCH_WEIGHT_LB = 10.06;

        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println("EZI-65 CDX1 Import Diagnostic");
        System.out.println("=".repeat(90));
        System.out.println();

        // --- Walk through all components and print geometry ---
        System.out.println("COMPONENT TREE:");
        System.out.println("-".repeat(90));
        System.out.printf("%-30s %-12s %10s %10s %12s%n",
                "Component", "Type", "Length(in)", "Dia(in)", "Position(in)");
        System.out.println("-".repeat(90));

        NoseCone importedNose = null;
        BodyTube importedBody = null;
        TrapezoidFinSet importedFins = null;
        RailButton importedRailButton = null;

        for (RocketComponent comp : rocket) {
            double lengthIn = comp.getLength() / IN_TO_M;
            double posIn = comp.getPosition().getX() / IN_TO_M;
            String type = comp.getClass().getSimpleName();

            if (comp instanceof NoseCone nc) {
                importedNose = nc;
                double diaIn = nc.getBaseRadius() * 2.0 / IN_TO_M;
                System.out.printf("%-30s %-12s %10.3f %10.3f %12.3f  shape=%s%n",
                        comp.getName(), type, lengthIn, diaIn, posIn, nc.getShapeType());
            } else if (comp instanceof BodyTube bt) {
                importedBody = bt;
                double diaIn = bt.getOuterRadius() * 2.0 / IN_TO_M;
                System.out.printf("%-30s %-12s %10.3f %10.3f %12.3f%n",
                        comp.getName(), type, lengthIn, diaIn, posIn);
            } else if (comp instanceof TrapezoidFinSet tfs) {
                importedFins = tfs;
                System.out.printf("%-30s %-12s %10.3f %10s %12.3f  (fins - details below)%n",
                        comp.getName(), type, lengthIn, "-", posIn);
            } else if (comp instanceof RailButton rb) {
                importedRailButton = rb;
                double outerDiaIn = rb.getOuterDiameter() / IN_TO_M;
                System.out.printf("%-30s %-12s %10.3f %10.3f %12.3f%n",
                        comp.getName(), type, lengthIn, outerDiaIn, posIn);
            } else {
                System.out.printf("%-30s %-12s %10.3f %10s %12.3f%n",
                        comp.getName(), type, lengthIn, "-", posIn);
            }
        }

        System.out.println();

        // --- Fin details ---
        System.out.println("FIN DETAILS:");
        System.out.println("-".repeat(90));
        if (importedFins != null) {
            double rootIn = importedFins.getRootChord() / IN_TO_M;
            double tipIn = importedFins.getTipChord() / IN_TO_M;
            double spanIn = importedFins.getSpan() / IN_TO_M;
            double sweepIn = importedFins.getSweep() / IN_TO_M;
            double thickIn = importedFins.getThickness() / IN_TO_M;
            int count = importedFins.getFinCount();
            String crossSection = importedFins.getCrossSection().toString();

            System.out.printf("  Count:         %d          (expected: %d)%n", count, EXPECTED_FIN_COUNT);
            System.out.printf("  Root chord:    %.3f in    (expected: %.3f in)  delta=%.4f in%n",
                    rootIn, EXPECTED_ROOT_CHORD_IN, rootIn - EXPECTED_ROOT_CHORD_IN);
            System.out.printf("  Tip chord:     %.3f in    (expected: %.3f in)  delta=%.4f in%n",
                    tipIn, EXPECTED_TIP_CHORD_IN, tipIn - EXPECTED_TIP_CHORD_IN);
            System.out.printf("  Span:          %.3f in    (expected: %.3f in)  delta=%.4f in%n",
                    spanIn, EXPECTED_SPAN_IN, spanIn - EXPECTED_SPAN_IN);
            System.out.printf("  Sweep:         %.3f in    (expected: %.3f in)  delta=%.4f in%n",
                    sweepIn, EXPECTED_SWEEP_IN, sweepIn - EXPECTED_SWEEP_IN);
            System.out.printf("  Thickness:     %.3f in    (expected: %.3f in)  delta=%.4f in%n",
                    thickIn, EXPECTED_THICKNESS_IN, thickIn - EXPECTED_THICKNESS_IN);
            System.out.printf("  Cross-section: %s%n", crossSection);
        } else {
            System.out.println("  WARNING: No TrapezoidFinSet found in imported rocket!");
        }
        System.out.println();

        // --- Nose cone verification ---
        System.out.println("NOSE CONE VERIFICATION:");
        System.out.println("-".repeat(90));
        if (importedNose != null) {
            double noseLen = importedNose.getLength() / IN_TO_M;
            double noseDia = importedNose.getBaseRadius() * 2.0 / IN_TO_M;
            String shape = importedNose.getShapeType().toString();
            System.out.printf("  Shape:    %s%n", shape);
            System.out.printf("  Length:   %.3f in  (expected: %.3f in)  delta=%.4f in%n",
                    noseLen, EXPECTED_NOSE_LENGTH_IN, noseLen - EXPECTED_NOSE_LENGTH_IN);
            System.out.printf("  Diameter: %.3f in  (expected: %.3f in)  delta=%.4f in%n",
                    noseDia, EXPECTED_NOSE_DIAMETER_IN, noseDia - EXPECTED_NOSE_DIAMETER_IN);
        } else {
            System.out.println("  WARNING: No NoseCone found!");
        }
        System.out.println();

        // --- Body tube verification ---
        System.out.println("BODY TUBE VERIFICATION:");
        System.out.println("-".repeat(90));
        if (importedBody != null) {
            double bodyLen = importedBody.getLength() / IN_TO_M;
            double bodyDia = importedBody.getOuterRadius() * 2.0 / IN_TO_M;
            System.out.printf("  Length:   %.3f in  (expected: %.3f in)  delta=%.4f in%n",
                    bodyLen, EXPECTED_BODY_LENGTH_IN, bodyLen - EXPECTED_BODY_LENGTH_IN);
            System.out.printf("  Diameter: %.3f in  (expected: %.3f in)  delta=%.4f in%n",
                    bodyDia, EXPECTED_BODY_DIAMETER_IN, bodyDia - EXPECTED_BODY_DIAMETER_IN);
        } else {
            System.out.println("  WARNING: No BodyTube found!");
        }
        System.out.println();

        // --- Rail guide verification ---
        System.out.println("RAIL GUIDE VERIFICATION:");
        System.out.println("-".repeat(90));
        if (importedRailButton != null) {
            double rgDia = importedRailButton.getOuterDiameter() / IN_TO_M;
            System.out.printf("  Outer diameter: %.3f in  (expected: %.3f in)  delta=%.4f in%n",
                    rgDia, EXPECTED_RAIL_GUIDE_DIAMETER_IN, rgDia - EXPECTED_RAIL_GUIDE_DIAMETER_IN);
        } else {
            System.out.println("  WARNING: No RailButton found in imported rocket!");
        }
        System.out.println();

        // --- Mass with motor ---
        System.out.println("MASS VERIFICATION:");
        System.out.println("-".repeat(90));
        RigidBody launchMass = MassCalculator.calculateLaunch(config);
        double launchMassKg = launchMass.getMass();
        double launchMassLb = launchMassKg / LB_TO_KG;
        double cgIn = launchMass.getCenterOfMass().getX() / IN_TO_M;
        System.out.printf("  Launch mass:  %.3f kg = %.3f lb  (expected: %.3f lb)  delta=%.3f lb%n",
                launchMassKg, launchMassLb, EXPECTED_LAUNCH_WEIGHT_LB,
                launchMassLb - EXPECTED_LAUNCH_WEIGHT_LB);
        System.out.printf("  Launch CG:    %.3f in from nose%n", cgIn);
        System.out.printf("  Has motors:   %b%n", config.hasMotors());
        System.out.println();

        // --- Cd breakdown at M=0.5 and M=0.8 ---
        System.out.println("Cd BREAKDOWN (zero AoA, sea level):");
        System.out.println("-".repeat(90));
        System.out.printf("%-12s %10s %10s %10s %10s%n",
                "Mach", "Cd_total", "Cd_fric", "Cd_press", "Cd_base");
        System.out.println("-".repeat(90));

        BarrowmanCalculator calc = new BarrowmanCalculator();
        AtmosphericConditions atm = new AtmosphericConditions();  // sea-level defaults
        double refArea = config.getReferenceArea();
        double refLength = config.getReferenceLength();

        for (double mach : new double[]{0.5, 0.8}) {
            FlightConditions conditions = new FlightConditions(config);
            conditions.setMach(mach);
            conditions.setAOA(0.0);
            conditions.setAtmosphericConditions(atm);

            WarningSet warnings = new WarningSet();
            AerodynamicForces totalForces = calc.getAerodynamicForces(config, conditions, warnings);

            // Also get the component-level breakdown
            Map<RocketComponent, AerodynamicForces> forceMap =
                    calc.getForceAnalysis(config, conditions, warnings);

            // Total Cd from the aggregate
            double cdTotal = totalForces.getCD();
            double cdFriction = totalForces.getFrictionCD();
            double cdPressure = totalForces.getPressureCD();
            double cdBase = totalForces.getBaseCD();

            System.out.printf("%-12.1f %10.4f %10.4f %10.4f %10.4f%n",
                    mach, cdTotal, cdFriction, cdPressure, cdBase);

            // Per-component breakdown
            for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
                RocketComponent comp = entry.getKey();
                AerodynamicForces f = entry.getValue();
                if (comp instanceof Rocket) continue;  // skip the total entry
                double cCd = f.getCD();
                if (Double.isNaN(cCd) || cCd == 0.0) continue;
                System.out.printf("  %-28s  Cd=%.5f  fric=%.5f  press=%.5f  base=%.5f%n",
                        comp.getName(),
                        cCd,
                        Double.isNaN(f.getFrictionCD()) ? 0.0 : f.getFrictionCD(),
                        Double.isNaN(f.getPressureCD()) ? 0.0 : f.getPressureCD(),
                        Double.isNaN(f.getBaseCD()) ? 0.0 : f.getBaseCD());
            }

            if (!warnings.isEmpty()) {
                System.out.println("  Warnings: " + warnings);
            }
            System.out.println();
        }

        System.out.println("=".repeat(90));
        System.out.println("EZI-65 Import Diagnostic Complete");
        System.out.println("=".repeat(90));
    }

    /**
     * Diagnostic: Compare Cd across Mach sweep for EZI-65, Raven, and CalIsp1
     * to identify where drag diverges between accurate and inaccurate rockets.
     */
    @Test
    void testCdMachSweep() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) { System.out.println("SKIP"); return; }

        String[] rockets = {"EZI65-1.CDX1", "Raven.CDX1", "CalIsp1.CDX1"};
        double[] machs = {0.3, 0.5, 0.7, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.2, 1.5};

        System.out.println();
        System.out.println("Cd vs Mach Sweep Comparison");
        System.out.println("=".repeat(100));
        System.out.printf("%-8s", "Mach");
        for (String r : rockets) System.out.printf("  %15s", r.replace(".CDX1", ""));
        System.out.println();
        System.out.println("-".repeat(100));

        // Load all rockets
        FlightConfiguration[] configs = new FlightConfiguration[rockets.length];
        for (int i = 0; i < rockets.length; i++) {
            File cdx1 = new File(simvrealDir, rockets[i]);
            GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
            OpenRocketDocument doc = loader.load();
            configs[i] = doc.getRocket().getSelectedConfiguration();
        }

        BarrowmanCalculator calc = new BarrowmanCalculator();
        AtmosphericConditions atm = new AtmosphericConditions();

        for (double mach : machs) {
            System.out.printf("M=%-6.2f", mach);
            for (int i = 0; i < configs.length; i++) {
                FlightConditions conditions = new FlightConditions(configs[i]);
                conditions.setMach(mach);
                conditions.setAOA(0.0);
                conditions.setAtmosphericConditions(atm);
                WarningSet ws = new WarningSet();
                AerodynamicForces forces = calc.getAerodynamicForces(configs[i], conditions, ws);
                System.out.printf("  Cd=%5.3f B=%5.3f", forces.getCD(), forces.getBaseCD());
            }
            System.out.println();
        }
        System.out.println("=".repeat(100));
    }

    /**
     * Diagnostic: import IonDrive.CDX1 and dump the boattail Transition's
     * actual fore/aft radii and computed cone angle. IonDrive is the only
     * subsonic outlier with drag TOO HIGH, so its boattail geometry is a
     * suspect: TransitionHandler calls setForeRadiusAutomatic(true), and the
     * boattail Transition sits inside a PodSet attached to the body tube.
     *
     * The auto-fore-radius path walks up via getPreviousSymmetricComponent();
     * if that resolves to the body tube it's correct, if it resolves to
     * DEFAULT_RADIUS (no previous component found) the boattail foreRadius is
     * wrong and the boattail correction is lost → overdrag → undershoot.
     */
    @Test
    void testIonDriveBoattailGeometry() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) { System.out.println("SKIP"); return; }

        File cdx1 = new File(simvrealDir, "IonDrive.CDX1");
        if (!cdx1.exists()) {
            System.out.println("SKIP: IonDrive.CDX1 not found");
            return;
        }

        GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
        OpenRocketDocument doc = loader.load();
        Rocket rocket = doc.getRocket();

        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println("IonDrive Boattail Geometry Audit");
        System.out.println("=".repeat(90));

        final double IN_TO_M = 0.0254;

        // Find the parent body tube radius for reference
        double bodyTubeRadiusM = Double.NaN;
        for (RocketComponent comp : rocket) {
            if (comp instanceof BodyTube bt && !"Boattail phantom tube".equals(bt.getName())) {
                bodyTubeRadiusM = bt.getOuterRadius();
                break;
            }
        }
        System.out.printf("Body tube outer radius: %.4f m (%.3f in)%n",
                bodyTubeRadiusM, bodyTubeRadiusM / IN_TO_M);

        // Walk the tree looking for Transition components (the boattail)
        int boattailCount = 0;
        boolean foreRadiusCorrect = true;
        for (RocketComponent comp : rocket) {
            if (!(comp instanceof Transition t)) continue;
            if (comp instanceof NoseCone) continue;
            boattailCount++;

            double foreR = t.getForeRadius();
            double aftR = t.getAftRadius();
            double length = t.getLength();
            boolean foreAuto = t.isForeRadiusAutomatic();
            RocketComponent parent = t.getParent();
            String parentType = parent == null ? "null" : parent.getClass().getSimpleName();

            double angleDeg = Math.toDegrees(Math.atan2(foreR - aftR, length));

            System.out.println();
            System.out.printf("Transition: %s%n", t.getName());
            System.out.printf("  parent           = %s%n", parentType);
            System.out.printf("  foreRadiusAuto   = %b%n", foreAuto);
            System.out.printf("  foreRadius       = %.4f m (%.3f in)%n", foreR, foreR / IN_TO_M);
            System.out.printf("  aftRadius        = %.4f m (%.3f in)%n", aftR, aftR / IN_TO_M);
            System.out.printf("  length           = %.4f m (%.3f in)%n", length, length / IN_TO_M);
            System.out.printf("  cone half-angle  = %.1f deg%n", angleDeg);

            if (!Double.isNaN(bodyTubeRadiusM)
                    && Math.abs(foreR - bodyTubeRadiusM) > 1e-4) {
                System.out.printf("  *** MISMATCH: foreRadius should be %.4f m (%.3f in); delta=%.4f m%n",
                        bodyTubeRadiusM, bodyTubeRadiusM / IN_TO_M, foreR - bodyTubeRadiusM);
                foreRadiusCorrect = false;
            }

            // Walk previous symmetric component (Transition is a SymmetricComponent)
            SymmetricComponent prev = t.getPreviousSymmetricComponent();
            System.out.printf("  previousSymmetric = %s%n",
                    prev == null ? "null" : prev.getClass().getSimpleName() + " r=" + prev.getAftRadius());
        }

        System.out.println();
        if (boattailCount == 0) {
            System.out.println("*** FAIL: No boattail Transition found in imported IonDrive");
        } else if (!foreRadiusCorrect) {
            System.out.println("*** BUG CONFIRMED: boattail foreRadius != body tube radius");
            System.out.println("    Fix candidate: BoattailHandler should set setForeRadiusAutomatic(false)");
            System.out.println("    and explicitly set foreRadius to the body tube radius.");
        } else {
            System.out.println("Boattail foreRadius matches body tube. Bug is elsewhere.");
        }
        System.out.println("=".repeat(90));
    }

    /**
     * Diagnostic: dump per-component Cd(M) breakdown for the priority rockets
     * to CSV so we can compare against RASAero II and bisect where drag
     * diverges. Uses the existing AeroCoeffientExporter.
     *
     * Output: build/reports/simvreal-component-cd-<rocket>.csv
     */
    @Test
    void testComponentCdSweepPriorityRockets() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) { System.out.println("SKIP"); return; }

        String[] rockets = {
                "EZI65-1.CDX1",
                "IonDrive.CDX1",
                "Raven.CDX1",
                "Torrent.CDX1",
                "DontDebateThisN5800MinDia.CDX1",
                "Qu8k.CDX1",
                "CalIsp1.CDX1",
                "CalIsp4.CDX1",
                "L500Roc.CDX1",
        };

        File outDir = new File("build/reports");
        outDir.mkdirs();

        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println("Component Cd(M) sweep for priority SimVReal rockets");
        System.out.println("=".repeat(90));

        for (String rocketFile : rockets) {
            File cdx1 = new File(simvrealDir, rocketFile);
            if (!cdx1.exists()) {
                System.out.println("SKIP (not found): " + rocketFile);
                continue;
            }

            GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
            OpenRocketDocument doc = loader.load();
            Rocket rocket = doc.getRocket();

            List<AeroCoeffientExporter.ComponentAeroData> data =
                    AeroCoeffientExporter.machSweep(rocket, 0.3, 3.0, 0.1, 0.0);

            String shortName = rocketFile.replace(".CDX1", "");
            File csvOut = new File(outDir, "simvreal-component-cd-" + shortName + ".csv");
            try (BufferedWriter w = new BufferedWriter(new FileWriter(csvOut))) {
                AeroCoeffientExporter.writeCsv(data, w);
            }
            System.out.println("Wrote " + csvOut.getPath() + " (" + data.size() + " rows)");

            // Also print a condensed summary of total Cd vs Mach
            System.out.printf("  %-30s %6s %6s %6s %6s %6s %6s %6s%n",
                    "Cd(M)", "M0.3", "M0.5", "M0.8", "M1.0", "M1.2", "M1.5", "M2.0");
            double[] samplePoints = {0.3, 0.5, 0.8, 1.0, 1.2, 1.5, 2.0};
            Map<Double, Double> totals = AeroCoeffientExporter.getTotalCdVsMach(data);
            System.out.printf("  %-30s", shortName);
            for (double m : samplePoints) {
                Double cd = totals.get(Math.round(m * 1000.0) / 1000.0);
                if (cd == null) System.out.printf(" %6s", "-");
                else System.out.printf(" %6.3f", cd);
            }
            System.out.println();
        }
        System.out.println("=".repeat(90));
    }
}
