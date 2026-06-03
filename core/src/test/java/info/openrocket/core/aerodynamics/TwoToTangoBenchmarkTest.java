package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
import info.openrocket.core.startup.OpenRocketCore;

/**
 * TwoToTango Benchmark: benchmarks ORP against the RASAero II simulations
 * from the prs2026/TwoToTango GitHub repository.
 *
 * These are real 2-stage rockets designed to fly 20k–135k ft.  No actual
 * flight data is available yet; the target is to match the RASAero II
 * predicted apogees.  All four variants share the same 1.5" sustainer
 * geometry (4x double-wedge fins, conical nose) and differ in booster size,
 * motor choice, and target altitude.
 *
 * RASAero II reference results (from CDX1 SimulationList):
 *   twototango            J495AP + L1000   48,341 ft   M ~1.73
 *   twototangostraightcoupler  J360 + L416SW  59,872 ft   M ~1.72
 *   waltz                 J495AP + H550    20,932 ft   M ~1.54
 *   twototangomeme        K478  + N3114RR 135,443 ft   M ~4.36  (aspirational)
 */
// Shares the JVM-global RASAeroMotorsLoader cache; must not mutate it concurrently
// with SimVRealBenchmarkTest et al., hence the shared exclusive resource lock.
@ResourceLock("AERO_CPU_HEAVY")
public class TwoToTangoBenchmarkTest {

    /** Subdirectory under the project root where the CDX1 files were saved */
    private static final String TTT_DIR = "simvreal/TwoToTango";

    /**
     * Motor files to pre-load.
     * The SimVReal rasp.eng covers Aerotech/CTI motors (L1000, H550, etc.).
     * The TwoToTango-specific stub file covers Polaris/Walter motors that are
     * not in any standard public database (J495AP, J360, L416SW, K478, N3114RR).
     * Stub impulse is estimated from motor class midpoint — see ttt_motors.eng header.
     */
    private static final String[] RASP_ENG_PATHS = {
            "simvreal/rasp.eng",
            "c:/Code/OpenRocket Plus/simvreal/rasp.eng",
            "simvreal/TwoToTango/ttt_motors.eng",
            "c:/Code/OpenRocket Plus/simvreal/TwoToTango/ttt_motors.eng",
    };

    @BeforeAll
    static void setup() {
        OpenRocketCore.initialize();
        // Clear the JVM-global RASAero motor cache so this class binds only its own
        // curves and does not leave its estimated-impulse stub motors (J495AP, etc.)
        // in the shared static List for a later class (e.g. SimVRealBenchmarkTest) to
        // bind by first-match. See RASAeroMotorsLoader.allMotors.
        RASAeroMotorsLoader.clearAllMotors();
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
            int loaded = 0;
            for (ThrustCurveMotor.Builder builder : builders) {
                try {
                    ThrustCurveMotor motor = builder.build();
                    RASAeroMotorsLoader.addMotorToCache(motor);
                    loaded++;
                } catch (Exception ignored) {}
            }
            System.out.println("Preloaded " + loaded + " motors from " + path);
        } catch (Exception e) {
            System.out.println("Could not load " + path + ": " + e.getMessage());
        }
    }

    /** One benchmark entry: CDX1 filename, human name, and RASAero predicted apogee (ft). */
    record BenchmarkCase(String file, String name, double rasAeroFt, double rasAeroMaxVelocityFps) {}

    static List<BenchmarkCase> getCases() {
        List<BenchmarkCase> cases = new ArrayList<>();
        // RASAero apogee and max velocity taken directly from CDX1 SimulationList
        cases.add(new BenchmarkCase("twototango.CDX1",
                "Two to Tango (J495AP+L1000)",
                48_341.38, 3504.394));
        cases.add(new BenchmarkCase("twototangostraightcoupler.CDX1",
                "Straight Coupler (J360+L416SW)",
                59_871.80, 3343.818));
        cases.add(new BenchmarkCase("waltz.CDX1",
                "Waltz (J495AP+H550)",
                20_931.97, 2847.533));
        cases.add(new BenchmarkCase("twototangomeme.CDX1",
                "Meme (K478+N3114RR)",
                135_442.80, 4906.483));
        return cases;
    }

    private static File findTTTDir() {
        File dir = new File(TTT_DIR);
        if (dir.exists()) return dir;

        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            File candidate = current.resolve(TTT_DIR).toFile();
            if (candidate.exists()) return candidate;
            current = current.getParent();
            if (current == null) break;
        }
        return null;
    }

    @Test
    void testTwoToTangoBenchmark() throws InterruptedException {
        File tttDir = findTTTDir();
        if (tttDir == null) {
            System.out.println("SKIP: TwoToTango CDX1 directory not found. Run from project root.");
            return;
        }

        List<BenchmarkCase> cases = getCases();
        List<Double> deltas = new ArrayList<>();
        int failed = 0, skipped = 0;

        final int COL = 42;
        System.out.println();
        System.out.println("=".repeat(115));
        System.out.println("TwoToTango Benchmark: ORP vs RASAero II (no real flight data available)");
        System.out.println("Source: https://github.com/prs2026/TwoToTango");
        System.out.println("=".repeat(115));
        System.out.printf("%-" + COL + "s %9s %9s %9s %9s  %s%n",
                "Rocket", "RASAero", "ORP", "Delta", "MaxMach", "Motors");
        System.out.println("-".repeat(115));

        for (BenchmarkCase bc : cases) {
            File cdx1 = new File(tttDir, bc.file());
            if (!cdx1.exists()) {
                System.out.printf("%-" + COL + "s  SKIP: file not found%n", bc.name());
                skipped++;
                continue;
            }

            try {
                GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
                OpenRocketDocument doc = loader.load();

                List<Simulation> sims = doc.getSimulations();
                if (sims.isEmpty()) {
                    System.out.printf("%-" + COL + "s  SKIP: no simulations in CDX1%n", bc.name());
                    skipped++;
                    continue;
                }

                Simulation sim = sims.get(0);
                sim.getOptions().setTimeStep(0.05);
                sim.getOptions().setMaximumStepAngle(Math.toRadians(3));

                // Describe motor configuration
                String motors = "";

                Thread simThread = new Thread(() -> {
                    try { sim.simulate(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
                simThread.start();
                simThread.join(180_000); // 3 min timeout — meme variant is large
                if (simThread.isAlive()) {
                    simThread.interrupt();
                    System.out.printf("%-" + COL + "s  TIMEOUT%n", bc.name());
                    failed++;
                    continue;
                }

                FlightData data = sim.getSimulatedData();
                if (data == null || data.getBranchCount() == 0 || data.getMaxAltitude() < 1) {
                    boolean hasMotors = doc.getRocket().getSelectedConfiguration().hasMotors();
                    System.out.printf("%-" + COL + "s  FAIL: no flight data (hasMotors=%b)%n",
                            bc.name(), hasMotors);
                    failed++;
                    continue;
                }

                double orpFt    = data.getMaxAltitude() * 3.28084;
                double maxMach  = data.getMaxMachNumber();
                double deltaPct = 100.0 * (orpFt - bc.rasAeroFt()) / bc.rasAeroFt();
                deltas.add(Math.abs(deltaPct));

                // RASAero Mach estimate from max velocity at ~sea level speed of sound (1116 fps)
                double rasMach = bc.rasAeroMaxVelocityFps() / 1116.0;

                String status;
                if (Math.abs(deltaPct) <= 10.0)      status = "GOOD";
                else if (Math.abs(deltaPct) <= 20.0) status = "WARN";
                else                                  status = "POOR";

                System.out.printf("%-" + COL + "s %9.0f %9.0f %+8.1f%% %5.2f/%-4.2f  %-4s %s%n",
                        bc.name(),
                        bc.rasAeroFt(), orpFt,
                        deltaPct,
                        maxMach, rasMach,
                        status, motors != null ? motors : "");

            } catch (Exception e) {
                System.out.printf("%-" + COL + "s  ERROR: %s%n", bc.name(), e.getMessage());
                failed++;
            }
        }

        System.out.println("-".repeat(115));

        if (!deltas.isEmpty()) {
            double avgDelta = deltas.stream().mapToDouble(d -> d).average().orElse(0);
            long within10 = deltas.stream().filter(d -> d <= 10.0).count();
            int total = deltas.size();

            System.out.println();
            System.out.println("SUMMARY (ORP vs RASAero II — no real flight data baseline):");
            System.out.printf("  Tested: %d  |  Skipped: %d  |  Errors/Timeouts: %d%n",
                    total, skipped, failed);
            System.out.printf("  Avg |ORP - RASAero| delta: %.2f%%%n", avgDelta);
            System.out.printf("  Within 10%%: %d / %d (%.0f%%)%n",
                    within10, total, 100.0 * within10 / total);
            System.out.println();
            System.out.println("  NOTE: Mach column shows ORP/RASAero-estimated peak Mach.");
            System.out.println("        RASAero Mach estimated from max velocity / 1116 fps (sea-level SoS).");
            System.out.println("  NOTE: No real flight data available yet. If flight data is published,");
            System.out.println("        add it to ValidationCase and migrate to SimVRealBenchmarkTest.");
        }
        System.out.println("=".repeat(115));

        // Soft assertion: don't fail the test suite, just report
        // (no real flight data to gate against — this is a tracking test)
    }
}
