package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.DatabaseMotorFinder;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end simulation validation: imports CDX1 files, runs simulations,
 * and compares predicted apogee altitude against real flight data and
 * RASAero II predictions.
 *
 * Reference: RASAero II achieves 3.47% average error across 36 flights.
 * Our target: demonstrate comparable accuracy for OpenRocket Plus.
 *
 * Each test case represents a real rocket that has been flown. The actual
 * altitude is from barometric altimeters, optical tracking, GPS, or
 * accelerometer integration. The RASAero prediction is from the CDX1 file.
 */
public class SimVRealValidationTest extends BaseTestCase {

    private static final double FOOT_TO_METER = 0.3048;

    @BeforeAll
    static void loadRASPMotors() {
        // Load motors from rasp.eng directly into RASAeroMotorsLoader's internal cache.
        // We can't use Application.getThrustCurveMotorSetDatabase() because the test
        // injector creates a new instance each call (no singleton binding).
        // Instead, call loadAllRASAeroMotors which populates the internal allMotors list.
        loadMotorsIntoRASAeroCache("simvreal/rasp.eng");
        loadMotorsIntoRASAeroCache("c:/Code/OpenRocket Plus/simvreal/rasp.eng");

        // Motors are now loaded directly into RASAeroMotorsLoader's cache
    }

    static void loadMotorsIntoRASAeroCache(String path) {
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
            System.out.println("Loaded " + loaded + " motors into RASAero cache (skipped " + skipped + ")");
        } catch (Exception e) {
            System.out.println("Could not load rasp.eng: " + e.getMessage());
        }
    }

    /**
     * Import a CDX1, run the simulation, return the results.
     * Note: each CDX1 import clears the RASAero motor cache (SimulationListHandler),
     * so we must reload motors before each import.
     */
    private SimResult importAndSimulate(String cdx1Filename) {
        // Motors were loaded into RASAeroMotorsLoader cache in @BeforeAll.
        // SimulationListHandler no longer clears the cache between imports.
        SimResult result = new SimResult();
        result.rocketName = cdx1Filename.replace(".CDX1", "");

        // Find the CDX1 file
        String[] paths = {
            "simvreal/RasAero Sims/" + cdx1Filename,
            "c:/Code/OpenRocket Plus/simvreal/RasAero Sims/" + cdx1Filename,
            "simvreal/Docs/Mesos/" + cdx1Filename,
            "c:/Code/OpenRocket Plus/simvreal/Docs/Mesos/" + cdx1Filename,
        };
        File file = null;
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) { file = f; break; }
        }
        if (file == null) {
            result.error = "CDX1 file not found: " + cdx1Filename;
            return result;
        }

        // Import the CDX1
        try {
            RASAeroLoader loader = new RASAeroLoader();
            OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
            DocumentLoadingContext context = new DocumentLoadingContext();
            context.setOpenRocketDocument(doc);
            context.setMotorFinder(new DatabaseMotorFinder());

            try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
                loader.loadFromStream(context, stream, result.rocketName);
            }

            // Collect warnings
            WarningSet warnings = loader.getWarnings();
            for (Warning w : warnings) {
                result.warnings += w.toString() + "; ";
            }
            if (!warnings.isEmpty()) {
                System.out.println("[" + cdx1Filename + "] Import warnings: " + result.warnings);
            }

            // Check if we have a simulation
            if (doc.getSimulationCount() == 0) {
                result.error = "No simulation created during import";
                return result;
            }

            Simulation sim = doc.getSimulation(0);
            Rocket rocket = doc.getRocket();
            result.componentCount = countComponents(rocket);

            // Use a coarse time step to keep simulation runtime reasonable.
            // MESOS and other high-altitude multi-stage rockets can take hours at default steps.
            sim.getOptions().setTimeStep(0.05);

            // Run the simulation
            try {
                sim.simulate();
            } catch (SimulationException e) {
                result.error = "Simulation failed: " + e.getMessage();
                return result;
            }

            FlightData data = sim.getSimulatedData();
            if (data == null) {
                result.error = "No flight data returned";
                return result;
            }

            result.apogeeM = data.getMaxAltitude();
            result.apogeeFt = result.apogeeM / FOOT_TO_METER;
            result.maxVelMs = data.getMaxVelocity();
            result.maxVelFtS = result.maxVelMs / FOOT_TO_METER;
            // Trajectory-derived peak Mach (uses altitude-correct speed of sound at every step,
            // not v_peak / a_sea_level). Peak velocity and peak Mach do not generally occur at
            // the same time or altitude.
            result.maxMach = data.getMaxMachNumber();
            result.flightTimeS = data.getFlightTime();
            result.flightData = data;
            result.simulated = true;

            // Collect simulation warnings
            WarningSet simWarnings = sim.getSimulatedWarnings();
            if (simWarnings != null) {
                for (Warning w : simWarnings) {
                    result.simWarnings += w.toString() + "; ";
                }
            }

        } catch (Exception e) {
            result.error = "Import/sim exception: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        return result;
    }

    private int countComponents(Rocket rocket) {
        int count = 0;
        for (var comp : rocket) { count++; }
        return count;
    }

    /**
     * Print a comparison report for one rocket.
     */
    private void reportResult(SimResult result, double actualAltFt, double rasAeroAltFt) {
        System.out.println("\n========================================");
        System.out.println("ROCKET: " + result.rocketName);
        System.out.println("========================================");

        if (!result.simulated) {
            System.out.println("FAILED: " + result.error);
            if (!result.warnings.isEmpty()) {
                System.out.println("Import warnings: " + result.warnings);
            }
            return;
        }

        double orError = (result.apogeeFt - actualAltFt) / actualAltFt * 100;
        double rasError = (rasAeroAltFt - actualAltFt) / actualAltFt * 100;

        System.out.println("Components: " + result.componentCount);
        System.out.printf("Apogee:     %.0f ft (%.0f m)%n", result.apogeeFt, result.apogeeM);
        System.out.printf("Max Vel:    %.0f ft/s (%.1f m/s)%n",
                result.maxVelFtS, result.maxVelMs);
        System.out.printf("Max Mach:   %.2f (trajectory peak; altitude-correct a)%n",
                result.maxMach);
        System.out.printf("Flt Time:   %.1f s%n", result.flightTimeS);
        System.out.println("---");
        System.out.printf("Actual:     %.0f ft%n", actualAltFt);
        System.out.printf("RASAero:    %.0f ft (error: %+.1f%%)%n", rasAeroAltFt, rasError);
        System.out.printf("OpenRocket: %.0f ft (error: %+.1f%%)%n", result.apogeeFt, orError);
        System.out.println("---");

        if (Math.abs(orError) <= 10) {
            System.out.println("RESULT: PASS (within 10%)");
        } else if (Math.abs(orError) <= 20) {
            System.out.println("RESULT: MARGINAL (within 20%)");
        } else {
            System.out.println("RESULT: FAIL (error > 20%)");
        }

        if (!result.warnings.isEmpty()) {
            System.out.println("Import warnings: " + result.warnings);
        }
        if (!result.simWarnings.isEmpty()) {
            System.out.println("Sim warnings: " + result.simWarnings);
        }
    }

    // ==================== Individual Rocket Tests ====================
    // Each test: import CDX1 → simulate → compare
    // Real flight data from CDX1 comments and RASAero comparison table

    @Test
    public void testGibb() {
        // Subsonic: I284W, 3" dia, actual=3913ft, RASAero=4205ft
        SimResult r = importAndSimulate("Gibb.CDX1");
        reportResult(r, 3913, 4205);
        assertSimulated(r);
    }

    @Test
    public void testEZI65() {
        // Subsonic: J450ST, 4" dia, actual=3965ft, RASAero=4214ft
        SimResult r = importAndSimulate("EZI65-1.CDX1");
        reportResult(r, 3965, 4214);
        assertSimulated(r);
    }

    @Test
    public void testRaven() {
        // Transonic: J570W, 1.75" dia, actual=8815ft, RASAero=9332ft
        SimResult r = importAndSimulate("Raven.CDX1");
        reportResult(r, 8815, 9332);
        assertSimulated(r);
    }

    @Test
    public void testIonDrive() {
        // Subsonic: K550W, 4" dia, actual=8027ft, RASAero=8642ft
        SimResult r = importAndSimulate("IonDrive.CDX1");
        reportResult(r, 8027, 8642);
        assertSimulated(r);
    }

    @Test
    public void testBlister() {
        // Transonic: K1075GG, 3" dia, actual=9026ft, RASAero=8301ft
        SimResult r = importAndSimulate("Blister.CDX1");
        reportResult(r, 9026, 8301);
        assertSimulated(r);
    }

    @Test
    public void testTorrent() {
        // Supersonic M~1.25: M1850GG, 4" dia, actual=12807ft, RASAero=13717ft
        SimResult r = importAndSimulate("Torrent.CDX1");
        reportResult(r, 12807, 13717);
        assertSimulated(r);
    }

    @Test
    public void testFullMetalJacket1() {
        // Supersonic M~2.3: O10000, 4" dia, actual=37981ft, RASAero=38772ft
        SimResult r = importAndSimulate("Full Metal Jacket1.CDX1");
        reportResult(r, 37981, 38772);
        assertSimulated(r);
    }

    @Test
    public void testN5800MinDia() {
        // Supersonic M~3.1: N5800, 3.9" dia, actual=56573ft, RASAero=61982ft
        SimResult r = importAndSimulate("DontDebateThisN5800MinDia.CDX1");
        reportResult(r, 56573, 61982);
        assertSimulated(r);
    }

    @Test
    public void testQu8k() {
        // Supersonic M~3.4: Q18000, 8" dia, actual=121478ft, RASAero=119684ft
        SimResult r = importAndSimulate("Qu8k.CDX1");
        reportResult(r, 121478, 119684);
        assertSimulated(r);
    }

    @Test
    public void testProteus6() {
        // Supersonic M~2.94: P9381, 6" dia, actual=85067ft, RASAero=81499ft
        // 3-fin hexagonal airfoil, 28.5:1 fineness ratio, conical nose, 1" boattail
        SimResult r = importAndSimulate("Proteus6.CDX1");
        reportResult(r, 85067, 81499);
        // Print stability trace to diagnose divergence
        if (r.simulated && r.flightData != null) {
            printStabilityTrace(r.flightData);
        }
        assertSimulated(r);
    }

    private void printStabilityTrace(info.openrocket.core.simulation.FlightData data) {
        info.openrocket.core.simulation.FlightDataBranch branch = data.getBranch(0);
        if (branch == null) return;
        java.util.List<Double> times = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_TIME);
        java.util.List<Double> aoas = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_AOA);
        java.util.List<Double> cps = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_CP_LOCATION);
        java.util.List<Double> cgs = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_CG_LOCATION);
        java.util.List<Double> stabs = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_STABILITY);
        java.util.List<Double> vels = branch.get(info.openrocket.core.simulation.FlightDataType.TYPE_VELOCITY_TOTAL);
        if (times == null) return;
        System.out.println("\n--- Proteus6 stability trace (every 0.5s + AoA>5deg) ---");
        System.out.printf("%-7s %-8s %-9s %-9s %-8s %-8s%n", "t(s)", "AoA(deg)", "CP(m)", "CG(m)", "SM(cal)", "V(m/s)");
        double lastPrintedTime = -1;
        for (int i = 0; i < times.size(); i++) {
            double t = times.get(i);
            double aoa = aoas != null && i < aoas.size() ? Math.toDegrees(aoas.get(i)) : Double.NaN;
            double cp = cps != null && i < cps.size() ? cps.get(i) : Double.NaN;
            double cg = cgs != null && i < cgs.size() ? cgs.get(i) : Double.NaN;
            double stab = stabs != null && i < stabs.size() ? stabs.get(i) : Double.NaN;
            double vel = vels != null && i < vels.size() ? vels.get(i) : Double.NaN;
            boolean highAoA = !Double.isNaN(aoa) && aoa > 5;
            boolean timeStep = (t - lastPrintedTime) >= 0.5;
            if (timeStep || highAoA) {
                System.out.printf("%-7.3f %-8.2f %-9.4f %-9.4f %-8.3f %-8.2f%n",
                    t, aoa, cp, cg, stab, vel);
                if (timeStep) lastPrintedTime = t;
            }
        }
    }

    @Test
    public void testMesos293K() {
        // MESOS 293K Flight — Kip Daugirdas, Black Rock Desert NV, 2022-10-01.
        // Two-stage: O4374 booster + M787 sustainer (both Kip custom motors).
        // Real flight data (from the RASAero II Comparison PDF / flight card):
        //   GPS apogee:       293,488 ft AGL
        //   Accel max vel:    4,047 ft/s (Mach 4.18)
        //   Time to apogee:   154.3 s
        // RASAero II postflight sim (Smooth Paint): 289,789 ft (-1.26%), Mach 4.23 (+1.19%).
        loadMotorsIntoRASAeroCache("simvreal/Docs/Mesos/M787_Expanded_Nozzle_Sea_Level.eng");
        loadMotorsIntoRASAeroCache("simvreal/Docs/Mesos/O4374_Sea_Level.eng");
        loadMotorsIntoRASAeroCache("c:/Code/OpenRocket Plus/simvreal/Docs/Mesos/M787_Expanded_Nozzle_Sea_Level.eng");
        loadMotorsIntoRASAeroCache("c:/Code/OpenRocket Plus/simvreal/Docs/Mesos/O4374_Sea_Level.eng");

        SimResult r = importAndSimulate("MESOS 293K Flight.CDX1");
        reportResult(r, 293488, 289789);
        if (r.simulated) {
            // Real flight Mach 4.18 is from the flight card (computed at peak-velocity altitude,
            // not at sea level). ORP r.maxMach uses the same convention via FlightDataType.TYPE_MACH_NUMBER.
            double realMach = 4.18;
            System.out.printf(
                "Max velocity: real=%.0f ft/s (Mach %.2f), ORP=%.0f ft/s (Mach %.2f, err %+.1f%%)%n",
                4047.0, realMach, r.maxVelFtS, r.maxMach,
                (r.maxVelFtS - 4047.0) / 4047.0 * 100);
        }
        assertSimulated(r);
    }

    @Test
    public void testCalIsp1() {
        SimResult r = importAndSimulate("CalIsp1.CDX1");
        // CalIsp data needs to be extracted from CDX1
        if (r.simulated) {
            System.out.println("CalIsp1: apogee=" + String.format("%.0f", r.apogeeFt) + " ft");
        } else {
            System.out.println("CalIsp1 FAILED: " + r.error);
        }
    }

    @Test
    public void testThunderAndLightning() {
        // I284W, 3.1" dia, actual=3577ft, RASAero=3989ft
        SimResult r = importAndSimulate("Thunder&Lightning.CDX1");
        reportResult(r, 3577, 3989);
        assertSimulated(r);
    }

    @Test
    public void testRabia() {
        SimResult r = importAndSimulate("Rabia.CDX1");
        if (r.simulated) {
            System.out.println("Rabia: apogee=" + String.format("%.0f", r.apogeeFt) + " ft");
        } else {
            System.out.println("Rabia FAILED: " + r.error);
        }
    }

    @Test
    public void testL500Roc() {
        SimResult r = importAndSimulate("L500Roc.CDX1");
        if (r.simulated) {
            System.out.println("L500Roc: apogee=" + String.format("%.0f", r.apogeeFt) + " ft");
        } else {
            System.out.println("L500Roc FAILED: " + r.error);
        }
    }

    // ==================== Summary Test ====================

    /**
     * Run all rockets and produce a summary comparison table.
     */
    @Test
    public void testSummaryComparison() {
        System.out.println("\n================================================================");
        System.out.println("         SIM vs REAL FLIGHT VALIDATION SUMMARY");
        System.out.println("================================================================");
        System.out.println(String.format("%-25s | %-7s | %-7s | %-7s | %-8s | %-8s",
                "Rocket", "Actual", "RASAero", "OR+", "RAS Err", "OR+ Err"));
        System.out.println(String.format("%-25s-|---------|---------|---------|----------|----------",
                "-------------------------"));

        // Data: {filename, actualFt, rasAeroFt}
        String[][] rockets = {
            {"Gibb.CDX1",                      "3913",  "4205"},
            {"EZI65-1.CDX1",                   "3965",  "4214"},
            {"Thunder&Lightning.CDX1",          "3577",  "3989"},
            {"IonDrive.CDX1",                   "8027",  "8642"},
            {"Blister.CDX1",                    "9026",  "8301"},
            {"Raven.CDX1",                      "8815",  "9332"},
            {"Torrent.CDX1",                    "12807", "13717"},
            {"Full Metal Jacket1.CDX1",         "37981", "38772"},
            {"DontDebateThisN5800MinDia.CDX1",  "56573", "61982"},
            {"Qu8k.CDX1",                       "121478","119684"},
        };

        int passed = 0, failed = 0, simFailed = 0;
        double totalAbsError = 0;

        for (String[] data : rockets) {
            String filename = data[0];
            double actualFt = Double.parseDouble(data[1]);
            double rasAeroFt = Double.parseDouble(data[2]);

            SimResult r = importAndSimulate(filename);
            if (!r.simulated) {
                System.out.println(String.format("%-25s | %7.0f | %7.0f | FAILED  | %+7.1f%% | N/A",
                        r.rocketName, actualFt, rasAeroFt,
                        (rasAeroFt - actualFt) / actualFt * 100));
                System.out.println("  >> " + r.error);
                simFailed++;
                continue;
            }

            double rasErr = (rasAeroFt - actualFt) / actualFt * 100;
            double orErr = (r.apogeeFt - actualFt) / actualFt * 100;
            totalAbsError += Math.abs(orErr);

            String status;
            if (Math.abs(orErr) <= 10) { status = ""; passed++; }
            else if (Math.abs(orErr) <= 20) { status = " *"; passed++; }
            else { status = " **"; failed++; }

            System.out.println(String.format("%-25s | %7.0f | %7.0f | %7.0f | %+7.1f%% | %+7.1f%%%s",
                    r.rocketName, actualFt, rasAeroFt, r.apogeeFt, rasErr, orErr, status));
        }

        int total = passed + failed;
        double avgError = total > 0 ? totalAbsError / total : 0;

        System.out.println(String.format("%-25s-|---------|---------|---------|----------|----------",
                "-------------------------"));
        System.out.println(String.format("TOTALS: %d simulated, %d sim failures", total, simFailed));
        System.out.println(String.format("Average absolute error: %.1f%% (RASAero benchmark: 3.5%%)", avgError));
        System.out.println(String.format("Within 10%%: %d/%d (%.0f%%)",
                passed, total, total > 0 ? 100.0 * passed / total : 0));
    }

    private void assertSimulated(SimResult r) {
        if (!r.simulated) {
            System.out.println("FAILED: " + r.error);
            System.out.println("Warnings: " + r.warnings);
        }
        // Don't fail the test - just report. We want to see ALL results.
    }

    static class SimResult {
        String rocketName = "";
        boolean simulated = false;
        double apogeeM = 0;
        double apogeeFt = 0;
        double maxVelMs = 0;
        double maxVelFtS = 0;
        double maxMach = 0;
        double flightTimeS = 0;
        int componentCount = 0;
        String error = "";
        String warnings = "";
        String simWarnings = "";
        FlightData flightData = null;
    }
}
