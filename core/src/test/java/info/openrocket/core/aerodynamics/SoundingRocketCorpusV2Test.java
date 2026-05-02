package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.database.motor.MotorDatabase;
import info.openrocket.core.database.motor.ThrustCurveMotorSetDatabase;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.startup.Application;

/**
 * RFD v2.0 sounding-rocket corpus seed: load the Super Loki Dart and Arcas .ork
 * models built from AFCRL-TR-73-0412 and DTIC AD-235341, run a single simulation
 * for each, and emit predicted apogee for v2.0 corpus expansion.
 *
 * <p>Source canonical apogees (median of available source flights):
 * <ul>
 *   <li>Super Loki Robin Dart: 13 source flights at Wallops, range 318k-394k ft,
 *       median ~348k ft (AFCRL-TR-73-0412 Table 8.2).</li>
 *   <li>Arcas Performance Flight 7 (extended-motor final config): 249,000 ft,
 *       peak Mach 3.6 (DTIC AD-235341 p.16-17).</li>
 * </ul>
 *
 * <p>Output: {@code paper/data/csv/v2_orp_runs_2026_05_02.csv}.
 *
 * <p>This test does not enforce a hard accuracy gate; it produces publication
 * evidence for the v2.0 corpus expansion. A hard regression gate is appropriate
 * after the .ork models are matured (per-flight launch conditions, time-resolved
 * Iyy, ablative mass loss).
 */
@ResourceLock("AERO_CPU_HEAVY")
public class SoundingRocketCorpusV2Test {

    private static final String ORK_DIR_REL = "paper/data/ork/sounding_rockets";

    @BeforeAll
    static void setup() {
        // Bind an empty MotorDatabase. The .ork files carry their motors inline
        // (RocketComponentSaver embeds the thrust curve as <motor> XML), so the
        // database is queried only as a fallback that we never need.
        Module applicationModule = new ServicesForTesting();
        Module pluginModule = new PluginModule();
        Module dbOverrides = new AbstractModule() {
            @Override
            protected void configure() {
                bind(MotorDatabase.class).toInstance(new ThrustCurveMotorSetDatabase());
            }
        };
        Injector injector = Guice.createInjector(
                Modules.override(applicationModule).with(dbOverrides), pluginModule);
        Application.setInjector(injector);
    }

    @Test
    void testV2SoundingRocketsAgainstSourceApogee() throws Exception {
        File orkDir = findDir(ORK_DIR_REL);
        assertNotNull(orkDir, "could not locate " + ORK_DIR_REL);
        assertTrue(orkDir.isDirectory(), "not a directory: " + orkDir);

        List<Row> rows = new ArrayList<>();
        rows.add(simulate(new File(orkDir, "super_loki_dart.ork"),
                "Super Loki Robin Dart",
                348_000.0,
                "AFCRL-TR-73-0412 Table 8.2 (median of 13 Wallops flights, 318k-394k ft)"));
        rows.add(simulate(new File(orkDir, "arcas.ork"),
                "Arcas Performance Flight 7",
                249_000.0,
                "DTIC AD-235341 p.16-17 (extended-motor final config, peak Mach 3.6)"));

        File reportRoot = findReportsRoot();
        File csvDir = new File(reportRoot, "paper/data/csv");
        assertTrue(csvDir.exists() || csvDir.mkdirs(), "could not create " + csvDir);
        File csv = new File(csvDir, "v2_orp_runs_2026_05_02.csv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(csv))) {
            w.write("vehicle,source_doc,apogee_real_ft,apogee_orp_ft,err_pct,note\n");
            for (Row r : rows) {
                w.write(r.toCsvRow() + "\n");
            }
        }
        System.out.println("V2 corpus ORP runs CSV: " + csv.getAbsolutePath());
        for (Row r : rows) {
            System.out.printf(Locale.US, "  %s: real=%.0f ft, orp=%.0f ft, err=%+.2f%%%n",
                    r.vehicle, r.apogeeRealFt, r.apogeeOrpFt, r.errPct());
        }

        // Soft sanity: apogee must be non-NaN and positive (not a load failure).
        for (Row r : rows) {
            assertTrue(Double.isFinite(r.apogeeOrpFt) && r.apogeeOrpFt > 0,
                    r.vehicle + " produced non-finite or non-positive apogee: " + r.apogeeOrpFt);
        }
    }

    private static Row simulate(File ork, String vehicle, double apogeeRealFt, String sourceDoc)
            throws Exception {
        if (!ork.exists()) {
            return new Row(vehicle, sourceDoc, apogeeRealFt, Double.NaN,
                    "ORK FILE MISSING: " + ork.getAbsolutePath());
        }
        try {
            GeneralRocketLoader loader = new GeneralRocketLoader(ork);
            OpenRocketDocument doc = loader.load();

            // Force-activate the default flight configuration. The .ork builder
            // declares motorconfiguration with stage activations, but the
            // selected configuration on Rocket may default to the empty
            // configuration; explicitly select the first non-empty config so
            // hasMotors() returns true.
            info.openrocket.core.rocketcomponent.Rocket rocket = doc.getRocket();
            for (info.openrocket.core.rocketcomponent.FlightConfigurationId fcid : rocket.getIds()) {
                if (fcid != null && rocket.getFlightConfiguration(fcid).hasMotors()) {
                    rocket.setSelectedConfiguration(fcid);
                    break;
                }
            }
            // Ensure all stages are active in the selected configuration.
            rocket.getSelectedConfiguration().setAllStages();

            List<Simulation> sims = doc.getSimulations();
            Simulation sim;
            if (sims.isEmpty()) {
                sim = new Simulation(doc, rocket);
                doc.addSimulation(sim);
            } else {
                sim = sims.get(0);
            }
            // Bind the simulation to the configured selection, in case the
            // newly-created Simulation defaulted to the empty configuration.
            sim.setFlightConfigurationId(rocket.getSelectedConfiguration().getFlightConfigurationID());
            sim.getOptions().setTimeStep(0.05);
            sim.getOptions().setMaximumStepAngle(Math.toRadians(3));
            sim.getOptions().setMaxSimulationTime(2400);
            sim.getOptions().setRandomSeed(0x51A7EA);

            // Run with a generous timeout (sounding rockets descend slowly from 100+ km).
            final Throwable[] simErr = new Throwable[1];
            Thread t = new Thread(() -> {
                try {
                    sim.simulate();
                } catch (Throwable th) {
                    simErr[0] = th;
                }
            });
            t.start();
            t.join(180_000);
            if (t.isAlive()) {
                t.interrupt();
                return new Row(vehicle, sourceDoc, apogeeRealFt, Double.NaN,
                        "TIMEOUT after 180s");
            }
            if (simErr[0] != null) {
                return new Row(vehicle, sourceDoc, apogeeRealFt, Double.NaN,
                        "SIM ERROR: " + simErr[0].getMessage());
            }
            FlightData data = sim.getSimulatedData();
            if (data == null || data.getBranchCount() == 0) {
                return new Row(vehicle, sourceDoc, apogeeRealFt, Double.NaN,
                        "NO FLIGHT DATA");
            }
            double apogeeFt = data.getMaxAltitude() * 3.28084;
            String diagnostic = "";
            if (apogeeFt < 1.0) {
                boolean hasMotors = doc.getRocket().getSelectedConfiguration().hasMotors();
                String warns = sim.getSimulatedWarnings() != null
                        ? sim.getSimulatedWarnings().toString().replace("\n", " | ")
                        : "(none)";
                diagnostic = String.format(Locale.US,
                        "ZERO APOGEE: maxAlt=%.3fm, branches=%d, hasMotors=%b, flightTime=%.1fs, warnings=%s",
                        data.getMaxAltitude(), data.getBranchCount(),
                        hasMotors, data.getFlightTime(),
                        warns.length() > 200 ? warns.substring(0, 200) + "..." : warns);
            }
            return new Row(vehicle, sourceDoc, apogeeRealFt, apogeeFt, diagnostic);
        } catch (Throwable th) {
            return new Row(vehicle, sourceDoc, apogeeRealFt, Double.NaN,
                    "LOAD ERROR: " + th.getClass().getSimpleName() + ": " + th.getMessage());
        }
    }

    private static File findDir(String relativePath) {
        File dir = new File(relativePath);
        if (dir.exists()) return dir;
        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && current != null; i++) {
            File candidate = current.resolve(relativePath).toFile();
            if (candidate.exists()) return candidate;
            current = current.getParent();
        }
        return null;
    }

    private static File findReportsRoot() {
        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && current != null; i++) {
            File csvSibling = current.resolve("paper/data/csv").toFile();
            File thesisSibling = current.resolve("paper/Thesis").toFile();
            if (csvSibling.exists() && thesisSibling.exists()) return current.toFile();
            current = current.getParent();
        }
        return new File(System.getProperty("user.dir"));
    }

    private static final class Row {
        final String vehicle;
        final String sourceDoc;
        final double apogeeRealFt;
        final double apogeeOrpFt;
        final String note;

        Row(String vehicle, String sourceDoc, double apogeeRealFt, double apogeeOrpFt, String note) {
            this.vehicle = vehicle;
            this.sourceDoc = sourceDoc;
            this.apogeeRealFt = apogeeRealFt;
            this.apogeeOrpFt = apogeeOrpFt;
            this.note = note;
        }

        double errPct() {
            if (!Double.isFinite(apogeeOrpFt) || apogeeRealFt <= 0) return Double.NaN;
            return 100.0 * (apogeeOrpFt - apogeeRealFt) / apogeeRealFt;
        }

        String toCsvRow() {
            return String.format(Locale.US, "%s,%s,%.0f,%s,%s,%s",
                    csvQuote(vehicle),
                    csvQuote(sourceDoc),
                    apogeeRealFt,
                    Double.isFinite(apogeeOrpFt) ? String.format(Locale.US, "%.0f", apogeeOrpFt) : "",
                    Double.isFinite(errPct()) ? String.format(Locale.US, "%.2f", errPct()) : "",
                    csvQuote(note));
        }

        private static String csvQuote(String s) {
            if (s == null) return "";
            String esc = s.replace("\"", "\"\"");
            return "\"" + esc + "\"";
        }
    }
}
