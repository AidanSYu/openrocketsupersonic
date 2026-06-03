package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.logging.SimulationAbort;
import info.openrocket.core.util.BaseTestCase;

/**
 * Prompt 16: Damping-heuristic sensitivity study.
 *
 * Tests the impact of the two damping heuristics in BarrowmanStabilityCalculator on
 * full-flight trajectory outcomes (apogee and peak AoA):
 *
 *   1. DAMPING_MULTIPLIER (default 3.0): global multiplier on the legacy damping moment
 *   2. TRANSONIC_CMQ_PEAK (default 2.5): transonic Cmq augmentation Gaussian peak
 *
 * Runs the worst outliers + one healthy case through a matrix of damping parameter values
 * to quantify sensitivity. The production defaults must be restored after each run.
 */
@ResourceLock("AERO_CPU_HEAVY")
public class DampingHeuristicSensitivityTest extends BaseTestCase {

    private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";
    private static final double FT_PER_METER = 3.28084;

    /** Save original values so we can restore them after each test. */
    private static final double ORIG_DAMPING_MULTIPLIER = BarrowmanStabilityCalculator.DAMPING_MULTIPLIER;
    private static final double ORIG_TRANSONIC_CMQ_PEAK = BarrowmanStabilityCalculator.TRANSONIC_CMQ_PEAK;
    private static final double ORIG_TRANSONIC_CMQ_SIGMA = BarrowmanStabilityCalculator.TRANSONIC_CMQ_SIGMA;

    /**
     * Cases for the sensitivity study.
     * Worst outliers: Raven (transonic, +27.5%), Kinsel (supersonic, +35.1%),
     *                 EZI-65 (subsonic, +16.1%), Thunder & Lightning (subsonic, +17.4%)
     * Healthy: CalIsp1 (<5% error)
     */
    private static final String[][] SENSITIVITY_CASES = {
            // { cdx1File, rocketName, realAltitudeFt }
            {"Raven.CDX1", "Raven", "8815"},
            {"Kinsel_P4935_A-601_Rocket.CDX1", "A-601 Kinsel", "42771"},
            {"EZI65-1.CDX1", "EZI-65", "3965"},
            {"Thunder&Lightning.CDX1", "Thunder & Lightning", "3577"},
            {"CalIsp1.CDX1", "CalIsp1 (healthy)", "3964"},
    };

    /** Damping multiplier values to test. */
    private static final double[] DAMPING_MULT_VALUES = {1.0, 2.0, 3.0, 5.0};

    /** Transonic Cmq peak values to test. */
    private static final double[] CMQ_PEAK_VALUES = {0.0, 1.0, 2.5, 5.0};

    @BeforeAll
    static void setup() {
        // Clear the JVM-global RASAero motor cache so this class binds only its own
        // rasp.eng curves (first-match lookup over a no-dedup static List).
        RASAeroMotorsLoader.clearAllMotors();
        // Preload RASAero motors
        for (String path : new String[]{"simvreal/rasp.eng", "c:/Code/OpenRocket Plus/simvreal/rasp.eng"}) {
            File file = new File(path);
            if (!file.exists()) continue;
            try (InputStream stream = new FileInputStream(file)) {
                RASPMotorLoader loader = new RASPMotorLoader();
                List<ThrustCurveMotor.Builder> builders = loader.load(stream, "rasp.eng");
                for (ThrustCurveMotor.Builder builder : builders) {
                    try {
                        RASAeroMotorsLoader.addMotorToCache(builder.build());
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }

    @AfterAll
    static void restoreDefaults() {
        BarrowmanStabilityCalculator.DAMPING_MULTIPLIER = ORIG_DAMPING_MULTIPLIER;
        BarrowmanStabilityCalculator.TRANSONIC_CMQ_PEAK = ORIG_TRANSONIC_CMQ_PEAK;
        BarrowmanStabilityCalculator.TRANSONIC_CMQ_SIGMA = ORIG_TRANSONIC_CMQ_SIGMA;
    }

    /**
     * Main sensitivity study: sweep DAMPING_MULTIPLIER across values while holding
     * TRANSONIC_CMQ_PEAK at its default, and vice versa.
     *
     * Output: console table + structured data for the memo.
     */
    @Test
    void testDampingMultiplierSensitivity() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) {
            System.out.println("SKIP: SimVReal directory not found.");
            return;
        }

        System.out.println();
        System.out.println("=".repeat(130));
        System.out.println("DAMPING MULTIPLIER SENSITIVITY STUDY");
        System.out.println("Holding TRANSONIC_CMQ_PEAK = " + ORIG_TRANSONIC_CMQ_PEAK + " (default)");
        System.out.println("=".repeat(130));
        System.out.printf(Locale.US, "%-25s %6s %10s %10s %10s %10s %10s%n",
                "Rocket", "DampM", "Apogee(ft)", "Err(%)", "PeakAoA", "MaxMach", "Terminal");
        System.out.println("-".repeat(130));

        List<String> csvRows = new ArrayList<>();
        csvRows.add("rocket,damping_mult,cmq_peak,apogee_ft,err_pct,peak_aoa_deg,max_mach,terminal");

        for (String[] caseSpec : SENSITIVITY_CASES) {
            String cdx1File = caseSpec[0];
            String rocketName = caseSpec[1];
            double realAlt = Double.parseDouble(caseSpec[2]);

            for (double dampMult : DAMPING_MULT_VALUES) {
                try {
                    BarrowmanStabilityCalculator.DAMPING_MULTIPLIER = dampMult;
                    BarrowmanStabilityCalculator.TRANSONIC_CMQ_PEAK = ORIG_TRANSONIC_CMQ_PEAK;
                    BarrowmanStabilityCalculator.TRANSONIC_CMQ_SIGMA = ORIG_TRANSONIC_CMQ_SIGMA;

                    SimResult result = runCase(simvrealDir, cdx1File, rocketName);
                    double errPct = 100.0 * (result.apogeeFt - realAlt) / realAlt;

                    System.out.printf(Locale.US, "%-25s %6.1f %10.0f %+9.1f%% %10.2f %10.3f %10s%n",
                            rocketName, dampMult, result.apogeeFt, errPct,
                            result.peakAoaDeg, result.maxMach, result.terminal);

                    csvRows.add(String.format(Locale.US, "%s,%.1f,%.1f,%.0f,%.4f,%.4f,%.4f,%s",
                            rocketName, dampMult, ORIG_TRANSONIC_CMQ_PEAK,
                            result.apogeeFt, errPct, result.peakAoaDeg, result.maxMach, result.terminal));
                } catch (Exception e) {
                    System.out.printf(Locale.US, "%-25s %6.1f  ERROR: %s%n", rocketName, dampMult, e.getMessage());
                } finally {
                    restoreDefaults();
                }
            }
            System.out.println();
        }
        System.out.println("=".repeat(130));

        // Write CSV
        File outDir = new File("build/reports/damping-sensitivity");
        outDir.mkdirs();
        File csv = new File(outDir, "damping_multiplier_sweep.csv");
        java.io.FileWriter fw = new java.io.FileWriter(csv);
        for (String row : csvRows) { fw.write(row + "\n"); }
        fw.close();
        System.out.println("CSV: " + csv.getPath());
    }

    @Test
    void testTransonicCmqSensitivity() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) {
            System.out.println("SKIP: SimVReal directory not found.");
            return;
        }

        System.out.println();
        System.out.println("=".repeat(130));
        System.out.println("TRANSONIC CMQ PEAK SENSITIVITY STUDY");
        System.out.println("Holding DAMPING_MULTIPLIER = " + ORIG_DAMPING_MULTIPLIER + " (default)");
        System.out.println("=".repeat(130));
        System.out.printf(Locale.US, "%-25s %6s %10s %10s %10s %10s %10s%n",
                "Rocket", "CmqPk", "Apogee(ft)", "Err(%)", "PeakAoA", "MaxMach", "Terminal");
        System.out.println("-".repeat(130));

        List<String> csvRows = new ArrayList<>();
        csvRows.add("rocket,damping_mult,cmq_peak,apogee_ft,err_pct,peak_aoa_deg,max_mach,terminal");

        for (String[] caseSpec : SENSITIVITY_CASES) {
            String cdx1File = caseSpec[0];
            String rocketName = caseSpec[1];
            double realAlt = Double.parseDouble(caseSpec[2]);

            for (double cmqPeak : CMQ_PEAK_VALUES) {
                try {
                    BarrowmanStabilityCalculator.DAMPING_MULTIPLIER = ORIG_DAMPING_MULTIPLIER;
                    BarrowmanStabilityCalculator.TRANSONIC_CMQ_PEAK = cmqPeak;
                    BarrowmanStabilityCalculator.TRANSONIC_CMQ_SIGMA = ORIG_TRANSONIC_CMQ_SIGMA;

                    SimResult result = runCase(simvrealDir, cdx1File, rocketName);
                    double errPct = 100.0 * (result.apogeeFt - realAlt) / realAlt;

                    System.out.printf(Locale.US, "%-25s %6.1f %10.0f %+9.1f%% %10.2f %10.3f %10s%n",
                            rocketName, cmqPeak, result.apogeeFt, errPct,
                            result.peakAoaDeg, result.maxMach, result.terminal);

                    csvRows.add(String.format(Locale.US, "%s,%.1f,%.1f,%.0f,%.4f,%.4f,%.4f,%s",
                            rocketName, ORIG_DAMPING_MULTIPLIER, cmqPeak,
                            result.apogeeFt, errPct, result.peakAoaDeg, result.maxMach, result.terminal));
                } catch (Exception e) {
                    System.out.printf(Locale.US, "%-25s %6.1f  ERROR: %s%n", rocketName, cmqPeak, e.getMessage());
                } finally {
                    restoreDefaults();
                }
            }
            System.out.println();
        }
        System.out.println("=".repeat(130));

        File outDir = new File("build/reports/damping-sensitivity");
        outDir.mkdirs();
        File csv = new File(outDir, "transonic_cmq_sweep.csv");
        java.io.FileWriter fw = new java.io.FileWriter(csv);
        for (String row : csvRows) { fw.write(row + "\n"); }
        fw.close();
        System.out.println("CSV: " + csv.getPath());
    }

    /**
     * Combined 2D sweep: test the interaction between the two heuristics.
     * Uses a reduced matrix to keep runtime acceptable.
     */
    @Test
    void testCombinedSensitivity() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) {
            System.out.println("SKIP: SimVReal directory not found.");
            return;
        }

        // Use only the worst outlier (Raven) and healthy (CalIsp1) for the 2D sweep
        String[][] reducedCases = {
                {"Raven.CDX1", "Raven", "8815"},
                {"CalIsp1.CDX1", "CalIsp1 (healthy)", "3964"},
        };
        double[] dampValues = {1.0, 3.0, 5.0};
        double[] cmqValues = {0.0, 2.5, 5.0};

        System.out.println();
        System.out.println("=".repeat(140));
        System.out.println("COMBINED 2D DAMPING SENSITIVITY");
        System.out.println("=".repeat(140));
        System.out.printf(Locale.US, "%-20s %6s %6s %10s %10s %10s %10s %10s%n",
                "Rocket", "DampM", "CmqPk", "Apogee(ft)", "Err(%)", "PeakAoA", "MaxMach", "Terminal");
        System.out.println("-".repeat(140));

        List<String> csvRows = new ArrayList<>();
        csvRows.add("rocket,damping_mult,cmq_peak,apogee_ft,err_pct,peak_aoa_deg,max_mach,terminal");

        for (String[] caseSpec : reducedCases) {
            String cdx1File = caseSpec[0];
            String rocketName = caseSpec[1];
            double realAlt = Double.parseDouble(caseSpec[2]);

            for (double dampMult : dampValues) {
                for (double cmqPeak : cmqValues) {
                    try {
                        BarrowmanStabilityCalculator.DAMPING_MULTIPLIER = dampMult;
                        BarrowmanStabilityCalculator.TRANSONIC_CMQ_PEAK = cmqPeak;

                        SimResult result = runCase(simvrealDir, cdx1File, rocketName);
                        double errPct = 100.0 * (result.apogeeFt - realAlt) / realAlt;

                        System.out.printf(Locale.US, "%-20s %6.1f %6.1f %10.0f %+9.1f%% %10.2f %10.3f %10s%n",
                                rocketName, dampMult, cmqPeak, result.apogeeFt, errPct,
                                result.peakAoaDeg, result.maxMach, result.terminal);

                        csvRows.add(String.format(Locale.US, "%s,%.1f,%.1f,%.0f,%.4f,%.4f,%.4f,%s",
                                rocketName, dampMult, cmqPeak,
                                result.apogeeFt, errPct, result.peakAoaDeg, result.maxMach, result.terminal));
                    } catch (Exception e) {
                        System.out.printf(Locale.US, "%-20s %6.1f %6.1f  ERROR: %s%n",
                                rocketName, dampMult, cmqPeak, e.getMessage());
                    } finally {
                        restoreDefaults();
                    }
                }
            }
            System.out.println();
        }
        System.out.println("=".repeat(140));

        File outDir = new File("build/reports/damping-sensitivity");
        outDir.mkdirs();
        File csv = new File(outDir, "combined_2d_sweep.csv");
        java.io.FileWriter fw = new java.io.FileWriter(csv);
        for (String row : csvRows) { fw.write(row + "\n"); }
        fw.close();
        System.out.println("CSV: " + csv.getPath());
    }

    // ---- helpers ----

    private static SimResult runCase(File simvrealDir, String cdx1File, String rocketName) throws Exception {
        File cdx1 = new File(simvrealDir, cdx1File);
        assertTrue(cdx1.exists(), cdx1File + " not found");

        GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
        OpenRocketDocument doc = loader.load();
        List<Simulation> sims = doc.getSimulations();
        assertFalse(sims.isEmpty(), "No simulations in " + cdx1File);

        Simulation sim = sims.get(0);
        sim.getOptions().setTimeStep(0.05);
        sim.getOptions().setMaximumStepAngle(Math.toRadians(3));
        sim.getOptions().setMaxSimulationTime(2400);

        final RuntimeException[] thrown = new RuntimeException[1];
        Thread simThread = new Thread(() -> {
            try {
                sim.simulate();
            } catch (RuntimeException e) {
                thrown[0] = e;
            } catch (Exception e) {
                thrown[0] = new RuntimeException(e);
            }
        });
        simThread.start();
        simThread.join(120_000);
        if (simThread.isAlive()) {
            simThread.interrupt();
            return new SimResult(0, 0, 0, "TIMEOUT");
        }
        if (thrown[0] != null) {
            throw thrown[0];
        }

        FlightData data = sim.getSimulatedData();
        if (data == null || data.getBranchCount() == 0) {
            return new SimResult(0, 0, 0, "NO_DATA");
        }

        double apogeeFt = data.getMaxAltitude() * FT_PER_METER;
        double maxMach = data.getMaxMachNumber();

        // Extract peak AoA during ascent only (before apogee)
        FlightDataBranch branch = data.getBranch(0);
        double peakAoaDeg = 0;
        List<Double> aoas = branch.get(FlightDataType.TYPE_AOA);
        List<Double> alts = branch.get(FlightDataType.TYPE_ALTITUDE);
        // Find apogee index
        int apogeeIdx = 0;
        if (alts != null) {
            double maxAlt = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < alts.size(); i++) {
                if (alts.get(i) != null && alts.get(i) > maxAlt) {
                    maxAlt = alts.get(i);
                    apogeeIdx = i;
                }
            }
        }
        if (aoas != null) {
            int limit = apogeeIdx > 0 ? apogeeIdx : aoas.size();
            for (int i = 0; i < limit; i++) {
                Double aoa = aoas.get(i);
                if (aoa != null && !Double.isNaN(aoa)) {
                    double deg = Math.toDegrees(aoa);
                    if (deg > peakAoaDeg) peakAoaDeg = deg;
                }
            }
        }

        String terminal = describeTerminal(sim, data);

        return new SimResult(apogeeFt, peakAoaDeg, maxMach, terminal);
    }

    private static String describeTerminal(Simulation sim, FlightData data) {
        for (int bi = 0; bi < data.getBranchCount(); bi++) {
            FlightEvent abort = data.getBranch(bi).getFirstEvent(FlightEvent.Type.SIM_ABORT);
            if (abort != null && abort.getData() instanceof SimulationAbort sa) {
                return "ABORT:" + sa.getCause().name();
            }
        }
        double maxTime = sim.getOptions().getMaxSimulationTime();
        double tol = Math.max(1e-6, sim.getOptions().getTimeStep());
        for (int bi = 0; bi < data.getBranchCount(); bi++) {
            FlightEvent end = data.getBranch(bi).getLastEvent(FlightEvent.Type.SIMULATION_END);
            boolean groundHit = data.getBranch(bi).getLastEvent(FlightEvent.Type.GROUND_HIT) != null;
            if (end != null && !groundHit && end.getTime() >= maxTime - tol) {
                return "MAXTIME";
            }
        }
        return "NORMAL";
    }

    private static File findSimVRealDir() {
        File dir = new File(SIMVREAL_DIR);
        if (dir.exists()) return dir;
        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            File candidate = current.resolve(SIMVREAL_DIR).toFile();
            if (candidate.exists()) return candidate;
            current = current.getParent();
            if (current == null) break;
        }
        return null;
    }

    private static record SimResult(double apogeeFt, double peakAoaDeg, double maxMach, String terminal) {}
}
