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
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.logging.SimulationAbort;
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
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.util.BaseTestCase;

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
 *
 * <p>ResourceLock: prevents concurrent execution with SupersonicBaselineTest, whose
 * testDCdDMachBounded is a 7-minute CPU hog that causes these long-running flight
 * simulations to time out when they run simultaneously.
 */
@ResourceLock("AERO_CPU_HEAVY")
public class SimVRealBenchmarkTest extends BaseTestCase {

    /** Path to SimVReal CDX1 files, relative to project root */
    private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";

    /** Path to the RASP motor file that ships with the SimVReal corpus */
    private static final String[] RASP_ENG_PATHS = {
            "simvreal/rasp.eng",
            "c:/Code/OpenRocket Plus/simvreal/rasp.eng",
    };
    static final int BENCHMARK_RANDOM_SEED = 0x51A7EA;

    @BeforeAll
    static void setup() {
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
        int abnormalEnds = 0;

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
                sim.getOptions().setMaxSimulationTime(2400);  // allow long descents from high apogee
                sim.getOptions().setRandomSeed(BENCHMARK_RANDOM_SEED);

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
                String terminalNote = describeTerminalState(sim, data);
                if (!terminalNote.isEmpty()) {
                    abnormalEnds++;
                }

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

                System.out.printf("%-30s %8.0f %8.0f %8.0f %+7.1f%% %+7.1f%% %+7.1f%%  %-4s M%.2f%s%n",
                        vc.rocketName, vc.realAltitudeFt, vc.rasAeroAltitudeFt,
                        orpApogeeFt, rasError, orpError, deltaVsRas, status, maxMach,
                        terminalNote.isEmpty() ? "" : " " + terminalNote);

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
        if (abnormalEnds > 0) {
            System.out.printf("  Abnormal endings: %d  (SIM_ABORT or max-time without ground hit)%n", abnormalEnds);
        }
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

        // ==================== Prompt 20 Headline Gates ====================
        // Regression-locks the Prompt 19 frozen audited corpus headline metrics
        // (paper/data/corpus_summary_2026_04_17.md, git SHA 4fe8a41):
        //   avg |error| = 6.84 %, within ±10 % = 83.3 %, within ±5 % = 62.5 %,
        //   abnormal endings = 0 (all 24 terminal note NORMAL)
        // Gates below are set at modest headroom from the frozen values so that
        // any future change that regresses the corpus materially will trip.
        //
        // These gates protect the aggregate AST-readiness story. Individual
        // outlier closure is regression-locked separately in
        //   ClosedOutlierRegressionTest (Raven, Kinsel).
        //
        // Do NOT relax these gates without re-running the full corpus and
        // updating corpus_summary_2026_04_17.md with the new frozen numbers.
        double withinTen = 100.0 * orpUnder10 / total;
        double withinFive = 100.0 * orpUnder5 / total;

        assertEquals(0, abnormalEnds,
                "SimVReal corpus abnormal endings must stay at 0 (Prompt 19 frozen). "
                        + "Any SIM_ABORT or MAXTIME without ground hit breaks the AST stability claim.");
        assertTrue(avgOrpError <= 7.5,
                String.format("SimVReal corpus avg |error| = %.2f%% exceeds Prompt-20 gate 7.5%% "
                        + "(Prompt 19 frozen value 6.84%%). See paper/data/corpus_summary_2026_04_17.md.",
                        avgOrpError));
        assertTrue(withinTen >= 80.0,
                String.format("SimVReal within ±10%% = %.1f%% fell below gate 80%% "
                        + "(Prompt 19 frozen value 83.3%%).", withinTen));
        assertTrue(withinFive >= 58.0,
                String.format("SimVReal within ±5%% = %.1f%% fell below gate 58%% "
                        + "(Prompt 19 frozen value 62.5%%).", withinFive));
    }

    private static String describeTerminalState(Simulation sim, FlightData data) {
        FlightEvent abortEvent = findFirstEvent(data, FlightEvent.Type.SIM_ABORT);
        if (abortEvent != null && abortEvent.getData() instanceof SimulationAbort abort) {
            return String.format("ABORT:%s@%.1fs", abort.getCause().name(), abortEvent.getTime());
        }

        double maxTime = sim.getOptions().getMaxSimulationTime();
        double tolerance = Math.max(1.0e-6, sim.getOptions().getTimeStep());
        for (int branchIndex = 0; branchIndex < data.getBranchCount(); branchIndex++) {
            FlightEvent simulationEnd = data.getBranch(branchIndex).getLastEvent(FlightEvent.Type.SIMULATION_END);
            if (simulationEnd == null) {
                continue;
            }

            boolean groundHit = data.getBranch(branchIndex).getLastEvent(FlightEvent.Type.GROUND_HIT) != null;
            if (!groundHit && simulationEnd.getTime() >= maxTime - tolerance) {
                return String.format("MAXTIME@%.0fs", simulationEnd.getTime());
            }
        }
        return "";
    }

    private static FlightEvent findFirstEvent(FlightData data, FlightEvent.Type type) {
        for (int branchIndex = 0; branchIndex < data.getBranchCount(); branchIndex++) {
            FlightEvent event = data.getBranch(branchIndex).getFirstEvent(type);
            if (event != null) {
                return event;
            }
        }
        return null;
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
                "Kinsel_P4935_A-601_Rocket.CDX1",
                "Proteus6.CDX1",
                "Full Metal Jacket1.CDX1",
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

    /**
     * MESOS 293K flight validation.
     *
     * Real flight (GPS):  293,488 ft AGL
     * RASAero II (smooth): 289,789 ft  (-1.26%)
     * Max real velocity:    4,047 ft/s  (Mach 4.18)
     *
     * 2-stage rocket: O4374 (KIP) booster + M787 (KIP) sustainer.
     * Custom motors must be pre-loaded from simvreal/Docs/Mesos/ .eng files.
     */
    @Test
    void testMesosFlight() throws Exception {
        final double REAL_APOGEE_FT    = 293_488.0;   // GPS AGL
        final double RASAERO_APOGEE_FT = 289_789.0;   // RASAero II smooth paint postflight
        final double REAL_MAX_VEL_FPS  =   4_047.0;   // accelerometer + GPS
        final double FT_TO_M = 0.3048;

        // ---- locate MESOS directory ----
        String[] mesosDirCandidates = {
                "simvreal/Docs/Mesos",
                "c:/Code/OpenRocket Plus/simvreal/Docs/Mesos",
        };
        File mesosDir = null;
        for (String p : mesosDirCandidates) {
            File d = new File(p);
            if (d.exists()) { mesosDir = d; break; }
        }
        if (mesosDir == null) {
            System.out.println("SKIP testMesosFlight: simvreal/Docs/Mesos directory not found.");
            return;
        }

        // ---- pre-load custom KIP motors ----
        String[] motorFiles = { "M787_Expanded_Nozzle_Sea_Level.eng", "O4374_Sea_Level.eng" };
        for (String mf : motorFiles) {
            File engFile = new File(mesosDir, mf);
            if (!engFile.exists()) {
                System.out.println("SKIP testMesosFlight: motor file not found: " + mf);
                return;
            }
            try (InputStream is = new FileInputStream(engFile)) {
                RASPMotorLoader loader = new RASPMotorLoader();
                List<ThrustCurveMotor.Builder> builders = loader.load(is, mf);
                int loaded = 0;
                for (ThrustCurveMotor.Builder b : builders) {
                    try {
                        RASAeroMotorsLoader.addMotorToCache(b.build());
                        loaded++;
                    } catch (Exception ignored) {}
                }
                System.out.println("  Loaded " + loaded + " motor(s) from " + mf);
            } catch (Exception e) {
                System.out.println("SKIP testMesosFlight: failed to load " + mf + ": " + e.getMessage());
                return;
            }
        }

        // ---- load CDX1 ----
        File cdx1 = new File(mesosDir, "MESOS 293K Flight.CDX1");
        assertTrue(cdx1.exists(), "MESOS CDX1 not found at " + cdx1.getAbsolutePath());

        GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
        OpenRocketDocument doc = loader.load();
        Rocket rocket = doc.getRocket();
        FlightConfiguration config = rocket.getSelectedConfiguration();

        // ---- print imported geometry ----
        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println("MESOS 293K — CDX1 Import Geometry");
        System.out.println("=".repeat(90));
        System.out.printf("  Stages in config: %d%n", config.getStageCount());
        System.out.printf("  Has motors:       %b%n", config.hasMotors());
        final double INM = 0.0254;
        for (RocketComponent comp : rocket) {
            double lenIn  = comp.getLength() / INM;
            double posIn  = comp.getPosition().getX() / INM;
            System.out.printf("  %-35s  len=%7.3f in  pos=%7.3f in%n",
                    comp.getClass().getSimpleName() + " [" + comp.getName() + "]", lenIn, posIn);
        }

        // ---- run simulation ----
        List<Simulation> sims = doc.getSimulations();
        assertFalse(sims.isEmpty(), "No simulations found in MESOS CDX1");
        Simulation sim = sims.get(0);

        // Print launch conditions as imported
        var opts = sim.getOptions();
        System.out.println();
        System.out.printf("  Launch alt:    %.0f m (%.0f ft)%n",
                opts.getLaunchAltitude(), opts.getLaunchAltitude() / FT_TO_M);
        System.out.printf("  Launch temp:   %.1f K  (%.1f °F)%n",
                opts.getLaunchTemperature(), (opts.getLaunchTemperature() - 273.15) * 9.0/5.0 + 32);
        System.out.printf("  Rod angle:     %.2f°%n", Math.toDegrees(opts.getLaunchRodAngle()));
        System.out.printf("  Rod length:    %.2f m%n", opts.getLaunchRodLength());

        opts.setTimeStep(0.05);
        opts.setMaximumStepAngle(Math.toRadians(3));
        opts.setRandomSeed(BENCHMARK_RANDOM_SEED);

        Thread simThread = new Thread(() -> {
            try { sim.simulate(); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        simThread.start();
        simThread.join(900_000);  // 15 min timeout (high-altitude 2-stage; ~6 min under heavy test-suite GC load)
        if (simThread.isAlive()) {
            simThread.interrupt();
            fail("MESOS simulation timed out after 900s");
        }

        FlightData data = sim.getSimulatedData();
        assertNotNull(data, "No flight data produced");
        assertTrue(data.getBranchCount() > 0, "No flight branches in data");

        double orpApogeeFt  = data.getMaxAltitude() * 3.28084;
        double orpMaxVelFps = data.getMaxVelocity() * 3.28084;
        double orpMaxMach   = data.getMaxMachNumber();

        double rasError = 100.0 * (RASAERO_APOGEE_FT - REAL_APOGEE_FT)  / REAL_APOGEE_FT;
        double orpError = 100.0 * (orpApogeeFt        - REAL_APOGEE_FT)  / REAL_APOGEE_FT;
        double deltaVsRas = 100.0 * (orpApogeeFt      - RASAERO_APOGEE_FT) / RASAERO_APOGEE_FT;
        double velError = 100.0 * (orpMaxVelFps        - REAL_MAX_VEL_FPS)  / REAL_MAX_VEL_FPS;

        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println("MESOS 293K — Simulation Results");
        System.out.println("=".repeat(90));
        System.out.printf("  %-25s  %10s  %10s  %10s%n", "", "Real", "RASAero II", "ORP");
        System.out.printf("  %-25s  %10.0f  %10.0f  %10.0f ft%n",
                "Apogee (AGL):", REAL_APOGEE_FT, RASAERO_APOGEE_FT, orpApogeeFt);
        System.out.printf("  %-25s  %10.0f  %10s  %10.0f ft/s%n",
                "Max velocity:", REAL_MAX_VEL_FPS, "4095", orpMaxVelFps);
        System.out.printf("  %-25s  %10.2f  %10s  %10.2f%n",
                "Max Mach:", 4.18, "4.23", orpMaxMach);
        System.out.println();
        System.out.printf("  Apogee errors:   RASAero=%+.2f%%  ORP=%+.2f%%  ORP vs RASAero=%+.2f%%%n",
                rasError, orpError, deltaVsRas);
        System.out.printf("  Velocity error:  ORP=%+.2f%%%n", velError);
        System.out.println();

        // ---- per-branch event log (diagnose staging / TUMBLE issues) ----
        System.out.printf("  Branches: %d  |  Total flight time: %.1fs%n",
                data.getBranchCount(), data.getFlightTime());
        for (int bi = 0; bi < data.getBranchCount(); bi++) {
            var branch = data.getBranch(bi);
            List<Double> times = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_TIME);
            List<Double> alts  = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_ALTITUDE);
            List<Double> cps   = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_CP_LOCATION);
            List<Double> cgs   = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_CG_LOCATION);
            List<Double> machs = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_MACH_NUMBER);
            double branchMaxAlt = (alts  != null && !alts.isEmpty())
                    ? alts.stream().mapToDouble(Double::doubleValue).max().orElse(0) * 3.28084 : 0;
            double tStart = (times != null && !times.isEmpty()) ? times.get(0) : Double.NaN;
            double tEnd   = (times != null && !times.isEmpty()) ? times.get(times.size()-1) : Double.NaN;
            System.out.printf("  Branch[%d] t=%.2f..%.2fs  maxAlt=%.0f ft%n",
                    bi, tStart, tEnd, branchMaxAlt);
            // Print key flight events
            for (var evt : branch.getEvents()) {
                System.out.printf("    [t=%.3fs] %s%n", evt.getTime(), evt.getType());
            }
            // Print CP & CG: every 5s normally, every 0.5s during M787 burn (t=15-21s)
            if (times != null && cps != null && cgs != null && machs != null) {
                System.out.printf("    %-8s %-8s %-10s %-10s %-8s%n",
                        "t(s)", "Mach", "CG(in)", "CP(in)", "margin(in)");
                double prevPrintT = -999;
                for (int i = 0; i < times.size(); i++) {
                    double t  = times.get(i);
                    // Fine resolution during sustainer burn, coarse otherwise
                    double interval = (t >= 14.5 && t <= 22.0) ? 0.4 : 4.9;
                    if (t - prevPrintT < interval) continue;
                    prevPrintT = t;
                    double cp   = (cps.size()   > i) ? cps.get(i)   : Double.NaN;
                    double cg   = (cgs.size()   > i) ? cgs.get(i)   : Double.NaN;
                    double mach = (machs.size() > i) ? machs.get(i) : Double.NaN;
                    double cpIn = cp / 0.0254;
                    double cgIn = cg / 0.0254;
                    System.out.printf("    %-8.2f %-8.3f %-10.1f %-10.1f %-8.1f%n",
                            t, mach, cgIn, cpIn, cpIn - cgIn);
                }
            }
        }

        // Warn on simulated warnings
        var warnings = sim.getSimulatedWarnings();
        if (warnings != null && !warnings.isEmpty()) {
            System.out.println("  Simulation warnings:");
            warnings.forEach(w -> System.out.println("    " + w));
        }
        System.out.println("=".repeat(90));

        // --- assertions ---
        assertTrue(orpApogeeFt > 240_000,
                String.format("ORP apogee %.0f ft is still too low for MESOS (expected ~290K ft, major staging/import regression likely)",
                        orpApogeeFt));
        assertTrue(Math.abs(velError) < 5.0,
                String.format("ORP max velocity %.0f ft/s differs too much from flight %.0f ft/s (err=%+.2f%%)",
                        orpMaxVelFps, REAL_MAX_VEL_FPS, velError));
        System.out.printf("  RESULT: ORP error vs real = %+.2f%%  (RASAero = %+.2f%%)%n", orpError, rasError);
    }

    /**
     * Trajectory trace test for Kline-Rogers L500 (supersonic single stage).
     *
     * Real:    24,771 ft    (optical)
     * RASAero: 26,509 ft  (+7.0%)  MaxVel=2229 ft/s (Mach ~2.0)
     * ORP:     ~31,900 ft (+28.8%)
     *
     * Prints a per-timestep trace of the L500 flight so we can see whether
     * the over-prediction is due to over-thrust, under-drag, under-mass, or coning.
     */
    @Test
    void testL500TrajectoryTrace() throws Exception {
        final double FT_TO_M = 0.3048;
        final double REAL_APOGEE_FT    = 24_771.0;
        final double RASAERO_APOGEE_FT = 26_509.0;
        final double RASAERO_MAXVEL_FPS = 2_229.373;

        // Locate CDX1
        File cdx1 = null;
        for (String base : new String[]{SIMVREAL_DIR, "c:/Code/OpenRocket Plus/" + SIMVREAL_DIR}) {
            File f = new File(base, "L500Roc.CDX1");
            if (f.exists()) { cdx1 = f; break; }
        }
        assertNotNull(cdx1, "L500Roc.CDX1 not found");

        GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
        OpenRocketDocument doc = loader.load();
        Rocket rocket = doc.getRocket();
        FlightConfiguration config = rocket.getSelectedConfiguration();

        System.out.println();
        System.out.println("=".repeat(100));
        System.out.println("L500 Trajectory Trace — Kline-Rogers USXRL-89");
        System.out.println("=".repeat(100));
        System.out.printf("  Stages: %d  hasMotors: %b%n", config.getStageCount(), config.hasMotors());
        final double INM = 0.0254;
        for (RocketComponent comp : rocket) {
            double lenIn = comp.getLength() / INM;
            double posIn = comp.getPosition().getX() / INM;
            System.out.printf("  %-35s  len=%7.3f in  pos=%7.3f in%n",
                    comp.getClass().getSimpleName() + " [" + comp.getName() + "]", lenIn, posIn);
        }

        // Print initial mass (dry + wet)
        RigidBody launchBody = MassCalculator.calculateLaunch(config);
        System.out.printf("  Launch mass: %.4f kg (%.3f lb)%n",
                launchBody.getMass(), launchBody.getMass() * 2.20462);
        RigidBody structureBody = MassCalculator.calculateStructure(config);
        System.out.printf("  Structure mass: %.4f kg (%.3f lb)%n",
                structureBody.getMass(), structureBody.getMass() * 2.20462);
        double propellantMass = launchBody.getMass() - structureBody.getMass();
        System.out.printf("  Propellant mass (launch-structure): %.4f kg (%.3f lb)%n",
                propellantMass, propellantMass * 2.20462);

        List<Simulation> sims = doc.getSimulations();
        assertFalse(sims.isEmpty(), "No sims in L500 CDX1");
        Simulation sim = sims.get(0);
        var opts = sim.getOptions();
        System.out.printf("  Launch alt: %.1f m (%.0f ft)  temp=%.1f K  rodAngle=%.2f deg%n",
                opts.getLaunchAltitude(), opts.getLaunchAltitude()/FT_TO_M,
                opts.getLaunchTemperature(), Math.toDegrees(opts.getLaunchRodAngle()));
        opts.setTimeStep(0.05);

        Thread simThread = new Thread(() -> {
            try { sim.simulate(); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        simThread.start();
        simThread.join(600_000);
        if (simThread.isAlive()) { simThread.interrupt(); fail("L500 sim timeout"); }

        FlightData data = sim.getSimulatedData();
        assertNotNull(data);
        assertTrue(data.getBranchCount() > 0);

        double orpApogeeFt = data.getMaxAltitude() * 3.28084;
        double orpMaxVelFps = data.getMaxVelocity() * 3.28084;
        double orpMaxMach = data.getMaxMachNumber();
        System.out.println();
        System.out.printf("  Real apogee:    %8.0f ft%n", REAL_APOGEE_FT);
        System.out.printf("  RASAero apogee: %8.0f ft   RASAero MaxVel: %.0f ft/s (~M2.0)%n",
                RASAERO_APOGEE_FT, RASAERO_MAXVEL_FPS);
        System.out.printf("  ORP apogee:     %8.0f ft   ORP MaxVel:     %.0f ft/s  (Max Mach %.3f)%n",
                orpApogeeFt, orpMaxVelFps, orpMaxMach);
        System.out.printf("  Velocity delta vs RASAero: %+.1f ft/s (%+.1f%%)%n",
                orpMaxVelFps - RASAERO_MAXVEL_FPS,
                100.0 * (orpMaxVelFps - RASAERO_MAXVEL_FPS) / RASAERO_MAXVEL_FPS);

        var branch = data.getBranch(0);
        List<Double> times = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_TIME);
        List<Double> alts  = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_ALTITUDE);
        List<Double> vels  = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_VELOCITY_TOTAL);
        List<Double> vzs   = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_VELOCITY_Z);
        List<Double> machs = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_MACH_NUMBER);
        List<Double> cds   = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_DRAG_COEFF);
        List<Double> drags = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_DRAG_FORCE);
        List<Double> thrusts = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_THRUST_FORCE);
        List<Double> masses = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_MASS);
        List<Double> gravs = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_GRAVITY);
        List<Double> aoas  = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_AOA);
        List<Double> pitchRates = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_PITCH_RATE);

        if (times == null) { System.out.println("  NO TRAJECTORY DATA"); return; }

        // Find burnout (thrust goes to zero) and apogee index
        int burnoutIdx = -1;
        for (int i = 1; i < times.size(); i++) {
            double th = (thrusts != null && i < thrusts.size()) ? thrusts.get(i) : 0;
            double thPrev = (thrusts != null && i-1 < thrusts.size()) ? thrusts.get(i-1) : 0;
            if (thPrev > 1.0 && th < 0.5) { burnoutIdx = i; break; }
        }
        int apogeeIdx = 0;
        double maxAlt = -1e9;
        for (int i = 0; i < alts.size(); i++) {
            if (alts.get(i) > maxAlt) { maxAlt = alts.get(i); apogeeIdx = i; }
        }

        // Print trace
        System.out.println();
        System.out.printf("  %-7s %-9s %-9s %-7s %-7s %-8s %-8s %-9s %-7s %-7s %-7s%n",
                "t(s)", "alt(ft)", "V(ft/s)", "Mach", "Cd", "Fdrag(N)", "Thr(N)", "m(kg)", "g(m/s2)", "AoA(d)", "pitR(d/s)");
        double prevPrintT = -999;
        for (int i = 0; i < times.size(); i++) {
            double t = times.get(i);
            boolean inBurn = (burnoutIdx < 0) || (i <= burnoutIdx);
            double interval = inBurn ? 0.1 : 0.5;
            if (t - prevPrintT < interval - 1e-6 && i != apogeeIdx && i != burnoutIdx) continue;
            prevPrintT = t;
            double alt = alts.get(i);
            double v = (vels != null && i < vels.size()) ? vels.get(i) : Double.NaN;
            double mach = (machs != null && i < machs.size()) ? machs.get(i) : Double.NaN;
            double cd = (cds != null && i < cds.size()) ? cds.get(i) : Double.NaN;
            double fd = (drags != null && i < drags.size()) ? drags.get(i) : Double.NaN;
            double th = (thrusts != null && i < thrusts.size()) ? thrusts.get(i) : Double.NaN;
            double m = (masses != null && i < masses.size()) ? masses.get(i) : Double.NaN;
            double g = (gravs != null && i < gravs.size()) ? gravs.get(i) : Double.NaN;
            double aoa = (aoas != null && i < aoas.size()) ? Math.toDegrees(aoas.get(i)) : Double.NaN;
            double pr = (pitchRates != null && i < pitchRates.size()) ? Math.toDegrees(pitchRates.get(i)) : Double.NaN;
            String marker = "";
            if (i == burnoutIdx) marker = " <-- BURNOUT";
            if (i == apogeeIdx) marker = " <-- APOGEE";
            System.out.printf("  %-7.2f %-9.0f %-9.0f %-7.3f %-7.3f %-8.1f %-8.1f %-9.4f %-7.3f %-7.2f %-7.2f%s%n",
                    t, alt*3.28084, v*3.28084, mach, cd, fd, th, m, g, aoa, pr, marker);
        }

        // Compute key metrics
        System.out.println();
        System.out.println("-".repeat(100));
        if (burnoutIdx > 0) {
            double tBO = times.get(burnoutIdx);
            double altBO = alts.get(burnoutIdx);
            double vBO = vels.get(burnoutIdx);
            double massBO = masses.get(burnoutIdx);
            double machBO = machs.get(burnoutIdx);
            double keBO = 0.5 * massBO * vBO * vBO;

            // Find peak vel and its index (usually just before burnout)
            double peakV = 0; int peakVIdx = 0;
            for (int i = 0; i < vels.size(); i++) {
                if (vels.get(i) > peakV) { peakV = vels.get(i); peakVIdx = i; }
            }

            System.out.printf("  Burnout:     t=%.2fs  alt=%.0f ft  V=%.1f m/s (%.0f ft/s)  M=%.3f  m=%.4f kg  KE=%.0f J%n",
                    tBO, altBO*3.28084, vBO, vBO*3.28084, machBO, massBO, keBO);
            System.out.printf("  Peak vel:    t=%.2fs  V=%.1f m/s (%.0f ft/s)  M=%.3f  alt=%.0f ft%n",
                    times.get(peakVIdx), peakV, peakV*3.28084, machs.get(peakVIdx), alts.get(peakVIdx)*3.28084);

            double tApogee = times.get(apogeeIdx);
            double altApogee = alts.get(apogeeIdx);
            double coastTime = tApogee - tBO;
            double dAlt = altApogee - altBO;
            double avgG = 9.81;
            if (gravs != null) {
                double sg = 0; int ng = 0;
                for (int i = burnoutIdx; i <= apogeeIdx && i < gravs.size(); i++) {
                    sg += gravs.get(i); ng++;
                }
                if (ng > 0) avgG = sg/ng;
            }
            double gravityPE = massBO * avgG * dAlt;  // PE gained
            double dragEnergy = keBO - gravityPE;      // KE - PE = drag energy dissipated
            // Drag impulse estimate
            double dragImpulse = 0;
            if (drags != null) {
                for (int i = burnoutIdx; i < apogeeIdx && i+1 < times.size() && i+1 < drags.size(); i++) {
                    dragImpulse += 0.5 * (drags.get(i) + drags.get(i+1)) * (times.get(i+1) - times.get(i));
                }
            }

            System.out.printf("  Apogee:      t=%.2fs  alt=%.0f ft  dAlt(coast)=%.0f ft  coastTime=%.2fs%n",
                    tApogee, altApogee*3.28084, dAlt*3.28084, coastTime);
            System.out.printf("  KE@burnout:  %10.0f J%n", keBO);
            System.out.printf("  PE gain:     %10.0f J  (= m*g*dh, avgG=%.3f)%n", gravityPE, avgG);
            System.out.printf("  Drag energy: %10.0f J  (KE-PE = drag dissipated during coast)%n", dragEnergy);
            System.out.printf("  Drag frac:   %.1f%% of KE@burnout lost to drag (rest to gravity PE)%n",
                    100.0 * dragEnergy / keBO);
            System.out.printf("  Drag impulse (coast): %.1f N*s%n", dragImpulse);
            System.out.printf("  Gravity impulse (coast): %.1f N*s%n", massBO * avgG * coastTime);

            // Thrust / impulse integration over burn phase
            double totalImpulse = 0;
            double peakThrust = 0;
            for (int i = 1; i <= burnoutIdx && i < times.size() && i < thrusts.size(); i++) {
                double dt = times.get(i) - times.get(i-1);
                totalImpulse += 0.5 * (thrusts.get(i) + thrusts.get(i-1)) * dt;
                if (thrusts.get(i) > peakThrust) peakThrust = thrusts.get(i);
            }
            System.out.printf("  Total impulse (thrust integrated): %.1f N*s  (L500 rasp.eng ~3269 N*s)%n",
                    totalImpulse);
            System.out.printf("  Peak thrust: %.1f N%n", peakThrust);

            // Average Cd during coast
            if (cds != null) {
                double sumCd = 0; int nCd = 0;
                for (int i = burnoutIdx; i <= apogeeIdx && i < cds.size(); i++) {
                    if (cds.get(i) > 0 && cds.get(i) < 5) { sumCd += cds.get(i); nCd++; }
                }
                System.out.printf("  Avg Cd during coast: %.4f  (n=%d)%n",
                        nCd > 0 ? sumCd/nCd : Double.NaN, nCd);
            }
        }
        System.out.println("=".repeat(100));
    }

    /**
     * Kinsel A-601 trajectory trace — diagnose +54.6% apogee overshoot and MAXTIME@1200s.
     *
     * Real (GPS):    42,771 ft
     * RASAero:       41,098 ft  (-3.9%)
     * ORP:           66,136 ft  (+54.6%)  MAXTIME@1200s
     *
     * CDX1 parity flags: ModifiedBarrowman=True, Turbulence=True, SustainerNozzle=3.09
     */
    @Test
    void testKinselTrajectoryTrace() throws Exception {
        final double REAL_APOGEE_FT = 42_771.0;
        final double RASAERO_APOGEE_FT = 41_098.0;

        File cdx1 = null;
        for (String base : new String[]{SIMVREAL_DIR, "c:/Code/OpenRocket Plus/" + SIMVREAL_DIR}) {
            File f = new File(base, "Kinsel_P4935_A-601_Rocket.CDX1");
            if (f.exists()) { cdx1 = f; break; }
        }
        assertNotNull(cdx1, "Kinsel CDX1 not found");

        GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
        OpenRocketDocument doc = loader.load();
        Rocket rocket = doc.getRocket();
        FlightConfiguration config = rocket.getSelectedConfiguration();

        System.out.println();
        System.out.println("=".repeat(110));
        System.out.println("Kinsel A-601 Trajectory Trace — P4935 (LR-EX)");
        System.out.println("=".repeat(110));

        // Geometry dump
        final double INM = 0.0254;
        System.out.printf("  Stages: %d  hasMotors: %b%n", config.getStageCount(), config.hasMotors());
        for (RocketComponent comp : rocket) {
            double lenIn = comp.getLength() / INM;
            double posIn = comp.getPosition().getX() / INM;
            String extra = "";
            if (comp instanceof SymmetricComponent sc) {
                double foreR = sc.getForeRadius() / INM;
                double aftR = sc.getAftRadius() / INM;
                extra = String.format("  foreR=%.3f aftR=%.3f in", foreR, aftR);
            }
            if (comp instanceof TrapezoidFinSet tfs) {
                extra = String.format("  count=%d rootChord=%.3f span=%.3f in",
                        tfs.getFinCount(), tfs.getRootChord() / INM, tfs.getSpan() / INM);
            }
            System.out.printf("  %-35s  len=%7.3f in  pos=%7.3f in%s%n",
                    comp.getClass().getSimpleName() + " [" + comp.getName() + "]", lenIn, posIn, extra);
        }

        // Mass
        RigidBody launchBody = MassCalculator.calculateLaunch(config);
        RigidBody structureBody = MassCalculator.calculateStructure(config);
        double propMass = launchBody.getMass() - structureBody.getMass();
        System.out.printf("  Launch mass:     %.3f kg (%.3f lb)  [CDX1: 154.46 lb]%n",
                launchBody.getMass(), launchBody.getMass() * 2.20462);
        System.out.printf("  Structure mass:  %.3f kg (%.3f lb)%n",
                structureBody.getMass(), structureBody.getMass() * 2.20462);
        System.out.printf("  Propellant mass: %.3f kg (%.3f lb)%n",
                propMass, propMass * 2.20462);
        System.out.printf("  Launch CG:       %.3f in from nose%n",
                launchBody.getCenterOfMass().getX() / INM);

        // Reference area
        System.out.printf("  Ref area: %.6f m^2  (= dia %.3f in)%n",
                config.getReferenceArea(),
                2.0 * Math.sqrt(config.getReferenceArea() / Math.PI) / INM);
        System.out.printf("  Nozzle exit dia (CDX1): 3.09 in  => nozzle area ratio = %.3f%n",
                Math.pow(3.09 / 6.125, 2));

        // Cd sweep at key Mach numbers
        System.out.println();
        System.out.println("  Cd BREAKDOWN (zero AoA, sea level):");
        System.out.printf("  %-6s %8s %8s %8s %8s%n", "Mach", "Cd_total", "Cd_fric", "Cd_press", "Cd_base");
        BarrowmanCalculator calc = new BarrowmanCalculator();
        AtmosphericConditions atm = new AtmosphericConditions();
        for (double mach : new double[]{0.5, 0.8, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0}) {
            FlightConditions fc = new FlightConditions(config);
            fc.setMach(mach);
            fc.setAOA(0.0);
            fc.setAtmosphericConditions(atm);
            WarningSet w = new WarningSet();
            AerodynamicForces forces = calc.getAerodynamicForces(config, fc, w);
            System.out.printf("  %-6.1f %8.4f %8.4f %8.4f %8.4f%n",
                    mach, forces.getCD(), forces.getFrictionCD(), forces.getPressureCD(), forces.getBaseCD());
        }

        // Simulate
        List<Simulation> sims = doc.getSimulations();
        assertFalse(sims.isEmpty(), "No sims in Kinsel CDX1");
        Simulation sim = sims.get(0);
        var opts = sim.getOptions();
        System.out.printf("%n  Launch alt: %.1f m (%.0f ft)  temp=%.1f K%n",
                opts.getLaunchAltitude(), opts.getLaunchAltitude() / 0.3048,
                opts.getLaunchTemperature());
        opts.setTimeStep(0.05);

        Thread simThread = new Thread(() -> {
            try { sim.simulate(); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        simThread.start();
        simThread.join(300_000);  // 5 min timeout for this long sim
        if (simThread.isAlive()) { simThread.interrupt(); fail("Kinsel sim timeout at 300s"); }

        FlightData data = sim.getSimulatedData();
        assertNotNull(data);
        assertTrue(data.getBranchCount() > 0);

        double orpApogeeFt = data.getMaxAltitude() * 3.28084;
        double orpMaxMach = data.getMaxMachNumber();
        System.out.printf("%n  Real apogee:    %8.0f ft%n", REAL_APOGEE_FT);
        System.out.printf("  RASAero apogee: %8.0f ft  (%+.1f%%)%n",
                RASAERO_APOGEE_FT, 100.0 * (RASAERO_APOGEE_FT - REAL_APOGEE_FT) / REAL_APOGEE_FT);
        System.out.printf("  ORP apogee:     %8.0f ft  (%+.1f%%)  Max Mach %.3f%n",
                orpApogeeFt, 100.0 * (orpApogeeFt - REAL_APOGEE_FT) / REAL_APOGEE_FT, orpMaxMach);

        // Terminal state
        String termNote = describeTerminalState(sim, data);
        System.out.printf("  Terminal state: %s%n", termNote.isEmpty() ? "NORMAL" : termNote);

        // Trajectory trace (burn phase + coast)
        var branch = data.getBranch(0);
        List<Double> times = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_TIME);
        List<Double> alts  = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_ALTITUDE);
        List<Double> vels  = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_VELOCITY_TOTAL);
        List<Double> machs = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_MACH_NUMBER);
        List<Double> cds   = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_DRAG_COEFF);
        List<Double> drags = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_DRAG_FORCE);
        List<Double> thrusts = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_THRUST_FORCE);
        List<Double> masses = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_MASS);

        if (times == null) { System.out.println("  NO TRAJECTORY DATA"); return; }

        // Find burnout and apogee
        int burnoutIdx = -1;
        for (int i = 1; i < times.size(); i++) {
            double th = (thrusts != null && i < thrusts.size()) ? thrusts.get(i) : 0;
            double thPrev = (thrusts != null && i-1 < thrusts.size()) ? thrusts.get(i-1) : 0;
            if (thPrev > 1.0 && th < 0.5) { burnoutIdx = i; break; }
        }
        int apogeeIdx = 0;
        double maxAlt = -1e9;
        for (int i = 0; i < alts.size(); i++) {
            if (alts.get(i) > maxAlt) { maxAlt = alts.get(i); apogeeIdx = i; }
        }

        System.out.println();
        System.out.printf("  %-7s %-9s %-9s %-7s %-7s %-8s %-8s %-9s%n",
                "t(s)", "alt(ft)", "V(ft/s)", "Mach", "Cd", "Fdrag(N)", "Thr(N)", "m(kg)");
        double prevPrintT = -999;
        for (int i = 0; i < times.size(); i++) {
            double t = times.get(i);
            boolean inBurn = (burnoutIdx < 0) || (i <= burnoutIdx);
            double interval = inBurn ? 0.2 : 1.0;
            if (i > apogeeIdx + 10) interval = 5.0;  // sparse during descent
            if (t - prevPrintT < interval - 1e-6 && i != apogeeIdx && i != burnoutIdx) continue;
            if (t > 200 && i != apogeeIdx) continue;  // don't flood descent trace
            prevPrintT = t;
            double alt = alts.get(i);
            double v = (vels != null && i < vels.size()) ? vels.get(i) : Double.NaN;
            double mach = (machs != null && i < machs.size()) ? machs.get(i) : Double.NaN;
            double cd = (cds != null && i < cds.size()) ? cds.get(i) : Double.NaN;
            double fd = (drags != null && i < drags.size()) ? drags.get(i) : Double.NaN;
            double th = (thrusts != null && i < thrusts.size()) ? thrusts.get(i) : Double.NaN;
            double m = (masses != null && i < masses.size()) ? masses.get(i) : Double.NaN;
            String marker = "";
            if (i == burnoutIdx) marker = " <-- BURNOUT";
            if (i == apogeeIdx) marker = " <-- APOGEE";
            System.out.printf("  %-7.2f %-9.0f %-9.0f %-7.3f %-7.3f %-8.1f %-8.1f %-9.4f%s%n",
                    t, alt * 3.28084, v * 3.28084, mach, cd, fd, th, m, marker);
        }

        // Key metrics
        if (burnoutIdx > 0) {
            double tBO = times.get(burnoutIdx);
            double altBO = alts.get(burnoutIdx);
            double vBO = vels.get(burnoutIdx);
            double massBO = masses.get(burnoutIdx);
            double machBO = machs.get(burnoutIdx);
            double keBO = 0.5 * massBO * vBO * vBO;
            double tApogee = times.get(apogeeIdx);
            double altApogee = alts.get(apogeeIdx);

            System.out.println();
            System.out.printf("  Burnout: t=%.2fs  alt=%.0f ft  V=%.0f ft/s  M=%.3f  m=%.3f kg%n",
                    tBO, altBO * 3.28084, vBO * 3.28084, machBO, massBO);
            System.out.printf("  Apogee:  t=%.2fs  alt=%.0f ft  (coast dAlt=%.0f ft, coast=%.1fs)%n",
                    tApogee, altApogee * 3.28084,
                    (altApogee - altBO) * 3.28084, tApogee - tBO);
            System.out.printf("  KE@burnout: %.0f J%n", keBO);

            // Avg Cd during coast
            if (cds != null) {
                double sumCd = 0; int nCd = 0;
                for (int i = burnoutIdx; i <= apogeeIdx && i < cds.size(); i++) {
                    if (cds.get(i) > 0 && cds.get(i) < 5) { sumCd += cds.get(i); nCd++; }
                }
                System.out.printf("  Avg Cd coast: %.4f  (n=%d)%n", nCd > 0 ? sumCd/nCd : Double.NaN, nCd);
            }
        }

        System.out.printf("%n  DIAGNOSIS: ORP overshoots by +%.1f%%. ",
                100.0 * (orpApogeeFt - REAL_APOGEE_FT) / REAL_APOGEE_FT);
        System.out.println("Check: coast drag level, nozzle parity, mass, ModifiedBarrowman effect");
        System.out.println("=".repeat(110));
    }
}
