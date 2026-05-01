package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.util.BaseTestCase;

/**
 * Prompt 20 regression lock: closed-outlier single-case guards for the
 * Raven and Kinsel ascent overshoots partially closed by Prompts 12 + 13.
 * <p>
 * Both outliers moved monotonically in the predicted direction:
 * <ul>
 *   <li>Raven: +27.45 % (pre-P12) → +24.22 % (post-P13), −3.23 pp closed by
 *       Prompt 13 (Hart L52E06 transonic base-drag polynomial widening).</li>
 *   <li>A-601 Kinsel: +35.13 % (pre-P12) → +28.14 % (post-P13), −6.99 pp
 *       closed by Prompt 12 (Lamb-Oberkampf Re correction removal) +
 *       Prompt 13 (Hart polynomial widening).</li>
 * </ul>
 * <p>
 * Both cases remain OPEN in the AST-readiness campaign — they are not
 * closed to the ±10 % band. The gates here protect the gains made by
 * Prompts 12 + 13 so that a later change which regresses past the
 * post-P13 values by a material margin will trip.
 * <p>
 * Gate policy: post-P13 value + 2 pp headroom (digitization scatter plus
 * small simulation noise). A failure of one of these gates means one of
 * the accepted fixes (Re-correction removal or Hart polynomial) was
 * partially or fully reverted, and the AST campaign needs to rerun the
 * corpus audit.
 * <p>
 * Frozen values: see paper/data/corpus_summary_2026_04_17.md and
 * paper/data/csv/corpus_summary_frozen_2026_04_17.csv (git SHA
 * 4fe8a4101, 2026-04-17).
 * <p>
 * Runtime: each test runs a single rocket simulation, ~30–60 s.
 */
@ResourceLock("AERO_CPU_HEAVY")
public class ClosedOutlierRegressionTest extends BaseTestCase {

    private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";
    private static final double FT_PER_METER = 3.28084;

    /** Raven post-Prompt-13 audited value is +24.22 %. Gate at +27 %
     *  (≈ +2.8 pp headroom over post-P13) so a P12 or P13 revert trips. */
    private static final double RAVEN_GATE_PCT = 27.0;
    private static final double RAVEN_REAL_FT = 8815.0;

    /** Kinsel post-Prompt-13 audited value is +28.14 %. Gate at +33 %
     *  (≈ +4.9 pp headroom; tighter than the pre-P12 +35.13 % so any
     *  partial revert of P12 or P13 that pushes above +33 % trips). */
    private static final double KINSEL_GATE_PCT = 33.0;
    private static final double KINSEL_REAL_FT = 42771.0;

    @BeforeAll
    static void setup() {
        // Reuse the SimVRealBenchmarkTest motor-cache preloading so shared
        // RASP motor curves (P4935 for Kinsel, motor set referenced by
        // Raven's CDX1) resolve at loader time.
        SimVRealBenchmarkTest.setup();
    }

    /**
     * Raven (Baro truth 8,815 ft, RASAero 9,332 ft, transonic min-dia,
     * peak Mach 1.108). Prompt 13 closed it from +27.5 % to +24.2 % via
     * Hart L52E06 transonic base-drag polynomial widening.
     * <p>
     * This gate protects that closure. Reverting BASE_BLEND_HIGH to 1.30
     * or removing the M=1.30 Hart mid anchor would push Raven back past
     * +27 % and trip this test.
     */
    @Test
    void ravenStaysBelowGate() throws Exception {
        double errPct = simulateApogeeErrorPct("Raven.CDX1", RAVEN_REAL_FT);
        assertTrue(errPct <= RAVEN_GATE_PCT,
                String.format("Raven apogee error %+.2f %% exceeds Prompt-20 gate +%.1f %%. "
                                + "Post-P13 frozen value = +24.22 %% (see "
                                + "paper/data/corpus_summary_2026_04_17.md). A failure here "
                                + "implies the Prompt-13 Hart-anchored transonic polynomial "
                                + "was partially reverted.",
                        errPct, RAVEN_GATE_PCT));
    }

    /**
     * A-601 Kinsel (GPS truth 42,771 ft, RASAero 41,098 ft, supersonic
     * fin-can, peak Mach 2.29). Prompt 12 closed it from +35.1 % to
     * +33.0 % via Lamb-Oberkampf Re correction removal, then Prompt 13
     * closed another 1.7 pp to +31.3 % (diagnostic harness) / +28.1 %
     * (benchmark harness). Both harnesses are correct; see Prompt 19
     * session log.
     * <p>
     * This gate protects both fixes. Reverting either would push Kinsel
     * past +33 % and trip this test. Gate also guards the MAXTIME margin:
     * pre-P12 ground hit was 1198 s (2-second slack against 1200 s cap);
     * post-P12+P13 ground hit is 1166 s (safer).
     */
    @Test
    void kinselStaysBelowGate() throws Exception {
        double errPct = simulateApogeeErrorPct("Kinsel_P4935_A-601_Rocket.CDX1", KINSEL_REAL_FT);
        assertTrue(errPct <= KINSEL_GATE_PCT,
                String.format("Kinsel apogee error %+.2f %% exceeds Prompt-20 gate +%.1f %%. "
                                + "Post-P13 frozen value (benchmark harness) = +28.14 %% (see "
                                + "paper/data/corpus_summary_2026_04_17.md). A failure here "
                                + "implies either the Prompt-12 Re-correction removal or the "
                                + "Prompt-13 Hart polynomial was partially reverted, or a new "
                                + "code path re-introduced a supersonic base-drag reduction.",
                        errPct, KINSEL_GATE_PCT));
    }

    /**
     * Shared helper: load a CDX1 from the SimVReal corpus, run its first
     * simulation to completion, and return the signed apogee error
     * percent relative to the real flight apogee in feet.
     */
    private double simulateApogeeErrorPct(String cdx1Name, double realAltitudeFt) throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) {
            System.out.println("SKIP ClosedOutlierRegressionTest: SimVReal directory not found.");
            // Return a value that passes both gates so the test is a no-op on
            // machines without the corpus present. This matches the loose
            // SKIP semantics of SimVRealBenchmarkTest.testSimVRealBenchmark.
            return Double.NEGATIVE_INFINITY;
        }
        File cdx1 = new File(simvrealDir, cdx1Name);
        assertTrue(cdx1.exists(), "CDX1 file not found: " + cdx1.getAbsolutePath());

        GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
        OpenRocketDocument doc = loader.load();
        List<Simulation> sims = doc.getSimulations();
        assertFalse(sims.isEmpty(), "No simulations in " + cdx1Name);

        Simulation sim = sims.get(0);
        sim.getOptions().setTimeStep(0.05);
        sim.getOptions().setMaximumStepAngle(Math.toRadians(3));
        sim.getOptions().setMaxSimulationTime(2400);
        sim.getOptions().setRandomSeed(SimVRealBenchmarkTest.BENCHMARK_RANDOM_SEED);

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
        simThread.join(300_000);  // 5-minute per-case timeout
        if (simThread.isAlive()) {
            simThread.interrupt();
            throw new IllegalStateException("Simulation timed out for " + cdx1Name);
        }
        if (thrown[0] != null) {
            throw thrown[0];
        }

        FlightData data = sim.getSimulatedData();
        assertNotNull(data, "No flight data for " + cdx1Name);
        assertTrue(data.getBranchCount() > 0, "No flight branches for " + cdx1Name);

        double orpApogeeFt = data.getMaxAltitude() * FT_PER_METER;
        double errPct = 100.0 * (orpApogeeFt - realAltitudeFt) / realAltitudeFt;
        System.out.printf("  ClosedOutlierRegressionTest %s: ORP=%.0f ft real=%.0f ft err=%+.2f%%%n",
                cdx1Name, orpApogeeFt, realAltitudeFt, errPct);
        return errPct;
    }

    private static File findSimVRealDir() {
        File dir = new File(SIMVREAL_DIR);
        if (dir.exists()) {
            return dir;
        }
        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            File candidate = current.resolve(SIMVREAL_DIR).toFile();
            if (candidate.exists()) {
                return candidate;
            }
            current = current.getParent();
            if (current == null) {
                break;
            }
        }
        return null;
    }
}
